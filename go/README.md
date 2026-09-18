# CoCoPay 商户 SDK（Golang）

帮助商户快速接入 CoCoPay 支付网关的 Golang SDK。覆盖：代收下单/查询、代付下单/查询、余额查询、异步通知验签。

仓库地址：https://github.com/CoCoPay-US/cocosdk

## 安装

```bash
go get github.com/CoCoPay-US/cocosdk/go
```

## 快速开始

```go
package main

import (
    "context"
    "fmt"
    "os"

    "github.com/CoCoPay-US/cocosdk/go/cocopay"
)

func main() {
    // 加载商户 RSA 私钥（支持 PKCS#1/PKCS#8 PEM，或纯 Base64 DER）
    pemBytes, _ := os.ReadFile("merchant_private_key.pem")
    privateKey, _ := cocopay.ParsePrivateKeyPEM(pemBytes)

    // BaseURL 填写平台提供的 API 网关地址（也可不配，从环境变量 COCOPAY_BASE_URL 读取）
    client, _ := cocopay.NewClient(cocopay.Config{
        BaseURL:    "https://api.example.com", // TODO: 替换为平台提供的 API 网关地址
        MchNo:      "100000",
        PrivateKey: privateKey,
    })

    // 创建代收订单
    resp, err := client.CreateCollectionOrder(context.Background(), &cocopay.CreateCollectionOrderRequest{
        MchOrderNo:    "ORD20240101001",
        Currency:      "USD",
        Amount:        "100.00",
        PaymentMethod: "cashapp",
        Email:         "john@example.com",
        ClientIP:      "203.0.113.10",
        MchUserID:     "U10001",
    })
    if err != nil {
        // err 为 *cocopay.APIError 时可读 Code/Reason/Message
        panic(err)
    }
    fmt.Println(resp.PayURL)
}
```

完整示例见 `examples/main.go`。

## 接口一览

| 方法 | 接口 |
|------|------|
| `CreateCollectionOrder` | `POST /v1/payment/collection/create` |
| `QueryCollectionOrder` | `POST /v1/payment/collection/query` |
| `CreatePayOutOrder` | `POST /v1/payment/pay_out/create` |
| `QueryPayOutOrder` | `POST /v1/payment/pay_out/query` |
| `QueryBalance` | `POST /v1/merchant/balance/query` |

订单状态常量：`CollectionState*` / `PayOutState*`；通知状态：`NotifyState*`。

## 请求签名方式

请求签名统一为 **RSA-PSS + SHA-256**（SDK 内置，无需配置）。

## 异步通知验签

```go
// 平台公钥由运营提供（支持 PEM 或纯 Base64）
pubKey, _ := cocopay.ParsePublicKeyPEM(platformPubKeyBytes)

func notifyHandler(w http.ResponseWriter, r *http.Request) {
    body, _ := io.ReadAll(r.Body)
    err := cocopay.VerifyOrderNotify(pubKey, cocopay.NotifyHeaders{
        MchNo:     r.Header.Get("x-mch-no"),
        Timestamp: r.Header.Get("x-timestamp"),
        Nonce:     r.Header.Get("x-nonce"),
        Signature: r.Header.Get("x-signature"),
    }, body)
    if err != nil {
        w.WriteHeader(http.StatusBadRequest)
        return
    }
    notify, _ := cocopay.ParseOrderNotify(body)
    // TODO: 按 order_no + state 幂等处理
    w.WriteHeader(http.StatusOK)
    w.Write([]byte("SUCCESS"))
}
```

异步通知签名为 **RSA-PSS + SHA-256**，与请求签名统一，`VerifyOrderNotify` 已内置处理。

## 签名规则（SDK 已内置，无需关心）

- 待签名串：`{x-mch-no},{x-timestamp},{x-nonce},{body}`（逗号拼接，body 为原始请求体）
- 请求签名：RSA-PSS + SHA-256（盐长 32），`x-signature` 头格式 `algorithm=SHA256withRSA/PSS,version=v2,signature={base64}`
- 时间戳为秒级 Unix 时间戳，与服务器时间差超过 10 分钟将被拒绝
- 金额字段（订单金额/实收金额/退款金额/余额等）均为**字符串**（保留 2 位小数，如 `"100.00"`），避免浮点精度问题

## 错误处理

```go
resp, err := client.CreateCollectionOrder(ctx, req)
if err != nil {
    if apiErr, ok := err.(*cocopay.APIError); ok {
        // 业务错误：apiErr.Code / apiErr.Reason / apiErr.Message
    } else {
        // 网络/序列化错误
    }
}
```

## 测试

```bash
cd sdk/go && go test ./...
```
