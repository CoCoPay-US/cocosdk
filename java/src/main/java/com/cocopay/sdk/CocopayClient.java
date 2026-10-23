package com.cocopay.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.time.Duration;
import java.util.List;

/**
 * CoCoPay 商户 SDK 客户端
 *
 * <pre>
 * CocopayClient client = new CocopayClient(CocopayClient.Config.builder()
 *         // baseUrl 可不配，默认从环境变量 COCOPAY_BASE_URL 读取
 *         .mchNo("100000")
 *         .privateKey(SignUtil.parsePrivateKey(pem))
 *         .build());
 * </pre>
 */
public class CocopayClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private final Config config;
    private final HttpClient httpClient;

    public CocopayClient(Config config) {
        if (config.baseUrl == null || config.baseUrl.isEmpty()) {
            config.baseUrl = System.getenv(Endpoints.ENV_BASE_URL);
        }
        if (config.baseUrl == null || config.baseUrl.isEmpty()) {
            throw new IllegalArgumentException("cocopay: baseUrl 未配置（请显式配置或设置环境变量 " + Endpoints.ENV_BASE_URL + "）");
        }
        if (config.mchNo == null || config.mchNo.isEmpty()) {
            throw new IllegalArgumentException("cocopay: mchNo 不能为空");
        }
        if (config.privateKey == null) {
            throw new IllegalArgumentException("cocopay: privateKey 不能为空");
        }
        this.config = config;
        this.httpClient = HttpClient.newBuilder().connectTimeout(config.timeout).build();
    }

    public static class Config {
        String baseUrl;
        String mchNo;
        PrivateKey privateKey;
        Duration timeout = Duration.ofSeconds(15);

        public static Config builder() {
            return new Config();
        }

        public Config baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Config mchNo(String mchNo) {
            this.mchNo = mchNo;
            return this;
        }

        public Config privateKey(PrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        public Config timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CocopayClient build() {
            return new CocopayClient(this);
        }
    }

    /** 网关统一响应壳 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Envelope {
        public int code;
        public String reason;
        public String message;
        public JsonNode data;
    }

    private JsonNode doPost(String path, Object reqBody) {
        try {
            String body = MAPPER.writeValueAsString(reqBody);
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonce = SignUtil.generateNonce();
            String signature = SignUtil.signPSS(config.privateKey,
                    SignUtil.buildSignContent(config.mchNo, timestamp, nonce, body));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl + path))
                    .timeout(config.timeout)
                    .header("Content-Type", "application/json")
                    .header("x-mch-no", config.mchNo)
                    .header("x-timestamp", timestamp)
                    .header("x-nonce", nonce)
                    .header("x-signature", SignUtil.signHeader(signature))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope env;
            try {
                env = MAPPER.readValue(resp.body(), Envelope.class);
            } catch (Exception e) {
                // 非 JSON 错误响应（如 502 HTML）：带上 HTTP 状态码和响应体片段，便于排查
                String snippet = resp.body() == null ? "" : resp.body();
                if (snippet.length() > 200) {
                    snippet = snippet.substring(0, 200) + "...";
                }
                throw new RuntimeException("cocopay: 响应不是合法 JSON（HTTP " + resp.statusCode() + "）: " + snippet, e);
            }
            if (resp.statusCode() != 200 || env.code != 200) {
                throw new ApiException(env.code, env.reason, env.message);
            }
            return env.data;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e; // 非 JSON 响应等已带语境的运行时异常，原样抛出
        } catch (Exception e) {
            throw new RuntimeException("cocopay: 请求失败: " + e.getMessage(), e);
        }
    }

    // ============================== 代收 ==============================

    /** 创建代收订单请求 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateCollectionOrderRequest {
        public String mchOrderNo;      // 商户订单号（幂等键，必填）
        public String currency;        // 币种：USD/HKD（必填）
        public String amount;          // 订单金额，单位元保留2位小数（必填）
        public String paymentMethod;   // 支付方式（必填）
        public String telephone;
        public String email;           // 付款人邮箱（必填）
        public String clientIp;        // 用户终端 IP（必填）
        public String notifyUrl;
        public String extra;
        public String mchUserId;       // 商户侧用户 ID（必填）
        public String cardValid;       // debit_card 必填，MM/YYYY
        public String routingCode;     // bank_card + USD/GBP 必填
        public List<GoodsInfo> goodsInfos; // 物品信息（可选），最多 50 条
    }

    /** 物品信息 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoodsInfo {
        public String skuId;      // 商品ID（留空由平台自动生成）
        public String skuName;    // 商品名称
        public String price;      // 单价，单位元，保留2位小数
        public int num;           // 数量（>=1）
        public String skuDesc;    // 商品描述（终端用户购买的产品名称）
        public String skuUrl;     // 商品详情页 URL（终端用户可直接访问的 H5/Web 页面，http/https）
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateCollectionOrderReply {
        public String orderNo;
        public String mchOrderNo;
        public int status;
        public String failReason;
        public String payUrl;
        public String extra;
    }

    public CreateCollectionOrderReply createCollectionOrder(CreateCollectionOrderRequest req) {
        return MAPPER.convertValue(doPost(Endpoints.PATH_COLLECTION_CREATE, req), CreateCollectionOrderReply.class);
    }

    /** 查询代收订单请求（orderNo 与 mchOrderNo 至少填一个） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryCollectionOrderRequest {
        public String orderNo;
        public String mchOrderNo;
    }

    /** 查询代收订单响应（字段与异步通知统一），金额字段为字符串（保留 2 位小数） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryCollectionOrderReply {
        public String mchNo;         // 商户号
        public String orderNo;
        public String mchOrderNo;
        public String currency;
        public String orderAmount;
        public String transactionAmount;
        public String state;           // 见 NotifyState（终态），中间态为 init/pending_audit/pending/pending_risk
        public String failReason;
        public long completeTime;      // 毫秒时间戳，仅成功时返回
        public String payUrl;          // 仅支付中状态返回
        public String extra;
    }

    public QueryCollectionOrderReply queryCollectionOrder(QueryCollectionOrderRequest req) {
        return MAPPER.convertValue(doPost(Endpoints.PATH_COLLECTION_QUERY, req), QueryCollectionOrderReply.class);
    }

    // ============================== 代付 ==============================

    /** 创建代付订单请求 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreatePayOutOrderRequest {
        public String mchOrderNo;      // 商户订单号（幂等键，必填）
        public String currency;        // 币种（必填）
        public String amount;          // 代付金额，单位元保留2位小数（必填）
        public String paymentMethod;   // 提现方式（必填）
        public String account;         // 收款账号（必填）
        public String accountName;     // 收款人姓名（必填）
        public String accountPhone;
        public String email;
        public String clientIp;        // 用户终端 IP（必填）
        public String notifyUrl;
        public String extra;
        public String mchUserId;       // 商户侧用户 ID（必填）
        public String routingCode;     // bankcard + USD/GBP 必填
        public String cardValid;       // card 必填，MM/YYYY
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreatePayOutOrderReply {
        public String orderNo;
        public String mchOrderNo;
        public int status;
        public String failReason;
        public String extra;
    }

    public CreatePayOutOrderReply createPayOutOrder(CreatePayOutOrderRequest req) {
        return MAPPER.convertValue(doPost(Endpoints.PATH_PAYOUT_CREATE, req), CreatePayOutOrderReply.class);
    }

    /** 查询代付订单请求（orderNo 与 mchOrderNo 至少填一个） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryPayOutOrderRequest {
        public String orderNo;
        public String mchOrderNo;
    }

    /** 查询代付订单响应（字段与异步通知统一），金额键为 order_amount/transaction_amount（snake_case，字符串，保留 2 位小数） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryPayOutOrderReply {
        public String mchNo;
        public String orderNo;
        public String mchOrderNo;
        public String currency;
        public String orderAmount;
        public String transactionAmount;
        public String state;           // 见 NotifyState（终态），中间态为 init/pending_merchant_audit/pending_admin_audit/audited/pending
        public long completeTime;
        public String failReason;
        public String extra;
    }

    public QueryPayOutOrderReply queryPayOutOrder(QueryPayOutOrderRequest req) {
        return MAPPER.convertValue(doPost(Endpoints.PATH_PAYOUT_QUERY, req), QueryPayOutOrderReply.class);
    }

    // ============================== 余额 ==============================

    /** 单币种余额（金额均为字符串，保留 2 位小数） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BalanceItem {
        public String currency;
        @JsonProperty("availableBalance")
        public String availableBalance;
        @JsonProperty("unsettledBalance")
        public String unsettledBalance;
        @JsonProperty("frozenBalance")
        public String frozenBalance;
        @JsonProperty("withdrawalPendingBalance")
        public String withdrawalPendingBalance;
        @JsonProperty("payoutPendingBalance")
        public String payoutPendingBalance;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryBalanceReply {
        public List<BalanceItem> balances;
    }

    public QueryBalanceReply queryBalance() {
        java.util.Map<String, String> req = new java.util.HashMap<>();
        req.put("mch_no", config.mchNo);
        return MAPPER.convertValue(doPost(Endpoints.PATH_BALANCE_QUERY, req), QueryBalanceReply.class);
    }
}
