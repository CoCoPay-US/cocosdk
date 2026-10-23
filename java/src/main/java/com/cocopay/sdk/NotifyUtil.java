package com.cocopay.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.security.PublicKey;

/** 异步通知工具：验签（RSA-PSS + SHA-256）与报文解析 */
public final class NotifyUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private NotifyUtil() {}

    /** 异步通知的订单状态（state 字符串） */
    public static final class NotifyState {
        public static final String SUCCESS = "success";
        public static final String FAILED = "failed";
        public static final String REJECTED = "rejected";
        public static final String REFUND = "refund";
        public static final String PARTIAL_REFUND = "partial_refund";
        public static final String REVERSAL = "reversal";
        public static final String CHARGEBACK = "chargeback";       // 拒付

        private NotifyState() {}
    }

    /** 通知请求头（验签用） */
    public static class NotifyHeaders {
        public final String mchNo;
        public final String timestamp;
        public final String nonce;
        public final String signature;

        public NotifyHeaders(String mchNo, String timestamp, String nonce, String signature) {
            this.mchNo = mchNo;
            this.timestamp = timestamp;
            this.nonce = nonce;
            this.signature = signature;
        }
    }

    /**
     * 校验异步通知签名（RSA-PSS + SHA-256，公钥由平台运营提供，可用 SignUtil.parsePublicKey 加载）
     *
     * @return 验签是否通过
     */
    public static boolean verifyOrderNotify(PublicKey platformPublicKey, NotifyHeaders headers, String body) {
        String signature = SignUtil.parseSignatureHeader(headers.signature);
        String content = SignUtil.buildSignContent(headers.mchNo, headers.timestamp, headers.nonce, body);
        return SignUtil.verifyPSS(platformPublicKey, content, signature);
    }

    /** 订单异步通知（代收/代付/退款通知统一结构），金额字段均为字符串（保留 2 位小数，如 "100.00"） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderNotify {
        public String mchNo;
        public String orderNo;
        public String mchOrderNo;
        public String currency;
        public String orderAmount;
        public String transactionAmount;
        public String state;
        public long completeTime;
        public String failReason;
        public String extra;
        public String account;        // 代付通知额外字段
        public String accountName;    // 代付通知额外字段
        public String refundAmount;   // 退款通知额外字段（字符串，保留 2 位小数）
        public String refundNo;       // 退款通知额外字段：平台退款单号（每次退款唯一，退款通知幂等键，对账用）
    }

    /** 解析通知报文 */
    public static OrderNotify parseOrderNotify(String body) {
        try {
            return MAPPER.readValue(body, OrderNotify.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("cocopay: 通知报文解析失败: " + e.getMessage(), e);
        }
    }
}
