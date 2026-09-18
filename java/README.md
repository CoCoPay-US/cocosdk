# CoCoPay 商户 SDK（Java）

CoCoPay 支付网关商户接入 SDK：代收下单/查询、代付下单/查询、余额查询、异步通知验签。Java 11+，仅依赖 Jackson。

## 安装（Maven）

仓库地址：https://github.com/CoCoPay-US/cocosdk （本包位于 `java/` 目录）

方式一（推荐，JitPack 直接引入 GitHub 子模块）：

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.CoCoPay-US.cocosdk</groupId>
  <artifactId>java</artifactId>
  <version>main-SNAPSHOT</version>
</dependency>
```

方式二（本地构建）：`git clone https://github.com/CoCoPay-US/cocosdk.git && cd cocosdk/java && mvn install`

```xml
<dependency>
  <groupId>com.cocopay</groupId>
  <artifactId>cocopay-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

## 快速开始

```java
String pem = new String(Files.readAllBytes(Paths.get("merchant_private_key.pem")));

// BaseURL 填写平台提供的 API 网关地址（也可不配，从环境变量 COCOPAY_BASE_URL 读取）
CocopayClient client = CocopayClient.Config.builder()
        .baseUrl("https://api.example.com") // TODO: 替换为平台提供的 API 网关地址
        .mchNo("100000")
        .privateKey(SignUtil.parsePrivateKey(pem)) // 支持 PKCS#8 / PKCS#1 PEM
        .build();

CocopayClient.CreateCollectionOrderRequest req = new CocopayClient.CreateCollectionOrderRequest();
req.mchOrderNo = "ORD20240101001";
req.currency = "USD";
req.amount = "100.00";
req.paymentMethod = "cashapp";
req.email = "john@example.com";
req.clientIp = "203.0.113.10";
req.mchUserId = "U10001";

CocopayClient.CreateCollectionOrderReply resp = client.createCollectionOrder(req);
System.out.println(resp.payUrl);
```

完整示例见 `examples/Main.java`。

> **私钥格式**：`SignUtil.parsePrivateKey` 支持 **PKCS#8**（`BEGIN PRIVATE KEY`）与 **PKCS#1**（`BEGIN RSA PRIVATE KEY`）PEM，或纯 Base64 DER，无需 openssl 转换；公钥同理支持 X.509 与 PKCS#1 两种格式。

## 接口一览

| 方法 | 接口 |
|------|------|
| `createCollectionOrder(req)` | `POST /v1/payment/collection/create` |
| `queryCollectionOrder(req)` | `POST /v1/payment/collection/query` |
| `createPayOutOrder(req)` | `POST /v1/payment/pay_out/create` |
| `queryPayOutOrder(req)` | `POST /v1/payment/pay_out/query` |
| `queryBalance()` | `POST /v1/merchant/balance/query` |

## 异步通知验签

```java
boolean ok = NotifyUtil.verifyOrderNotify(platformPublicKey,
        new NotifyUtil.NotifyHeaders(
                headers.getFirst("x-mch-no"),
                headers.getFirst("x-timestamp"),
                headers.getFirst("x-nonce"),
                headers.getFirst("x-signature")),
        body);
if (ok) {
    NotifyUtil.OrderNotify notify = NotifyUtil.parseOrderNotify(body);
    // TODO: 按 orderNo + state 幂等处理
    // 返回 HTTP 200 + 纯文本 "SUCCESS"
}
```

签名与验签统一为 **RSA-PSS + SHA-256**（商户请求与异步通知一致，盐长 32），SDK 已内置；
x-signature 头格式为 `algorithm=SHA256withRSA/PSS,version=v2,signature=<base64>`。

> **金额字段**：通知与查询响应中的金额字段（`orderAmount`/`transactionAmount`/`refundAmount` 及余额各字段）均为**字符串**（保留 2 位小数，如 `"100.00"`），请勿按数值类型处理。

## 错误处理

```java
try {
    client.createCollectionOrder(req);
} catch (ApiException e) {
    // 业务错误：e.getCode() / e.getReason() / e.getMessage()
}
```

## 测试

```bash
mvn test
```
