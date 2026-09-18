# CoCoPay 商户 SDK（Node.js）

CoCoPay 支付网关商户接入 SDK：代收下单/查询、代付下单/查询、余额查询、异步通知验签。**零第三方依赖**（仅使用 Node.js 内置 `crypto`/`http`/`https`），Node.js >= 18（测试使用内置 `node:test`）。

## 安装

仓库地址：https://github.com/CoCoPay-US/cocosdk （本包位于 `nodejs/` 目录）

```bash
# 方式一：克隆后本地引入
npm install /path/to/cocosdk/nodejs

# 方式二：在子目录打包后安装
# cd nodejs && npm pack
npm install ./cocopay-sdk-1.0.0.tgz
```

## 快速开始

```js
const fs = require('fs')
const {CocopayClient} = require('cocopay-sdk')

const client = new CocopayClient({
  baseUrl: 'https://api.example.com', // TODO: 替换为平台提供的 API 网关地址（也可不配，从环境变量 COCOPAY_BASE_URL 读取）
  mchNo: '100000',
  privateKey: fs.readFileSync('merchant_private_key.pem', 'utf8'), // PEM 或纯 Base64（纯 Base64 按 PKCS#8 包装）
})

// 创建代收订单
const resp = await client.createCollectionOrder({
  mch_order_no: 'ORD20240101001',
  currency: 'USD',
  amount: '100.00',
  payment_method: 'cashapp',
  email: 'john@example.com',
  client_ip: '203.0.113.10',
  mch_user_id: 'U10001',
})
console.log(resp.pay_url)
```

完整示例见 `examples/main.js`。

## 接口一览

| 方法 | 接口 |
|------|------|
| `createCollectionOrder(req)` | `POST /v1/payment/collection/create` |
| `queryCollectionOrder(req)` | `POST /v1/payment/collection/query` |
| `createPayOutOrder(req)` | `POST /v1/payment/pay_out/create` |
| `queryPayOutOrder(req)` | `POST /v1/payment/pay_out/query` |
| `queryBalance()` | `POST /v1/merchant/balance/query` |

## 异步通知验签

```js
const {verifyOrderNotify, parseOrderNotify} = require('cocopay-sdk')

app.post('/notify', express.raw({type: 'application/json'}), (req, res) => {
  const ok = verifyOrderNotify(platformPublicKey, {
    mchNo: req.get('x-mch-no'),
    timestamp: req.get('x-timestamp'),
    nonce: req.get('x-nonce'),
    signature: req.get('x-signature'),
  }, req.body)
  if (!ok) return res.status(400).end()
  const notify = parseOrderNotify(req.body)
  // TODO: 按 order_no + state 幂等处理
  res.status(200).type('text').send('SUCCESS')
})
```

签名与异步通知验签统一为 **RSA-PSS + SHA-256（盐长固定 32）**，SDK 已内置。`x-signature` 头格式：

```
algorithm=SHA256withRSA/PSS,version=v2,signature=<base64签名>
```

平台公钥支持 PEM 或纯 Base64（纯 Base64 按 SPKI 包装为 `-----BEGIN PUBLIC KEY-----` PEM）。

## 金额字段

查询响应与异步通知中的金额字段（`order_amount` / `transaction_amount` / `refund_amount`，以及余额查询的 5 个余额字段）均为**字符串**（保留 2 位小数），`mch_no` 亦为字符串，请勿用 `Number()` 转换后参与计算。

## 错误处理

```js
const {ApiError} = require('cocopay-sdk')
try {
  await client.createCollectionOrder(req)
} catch (e) {
  if (e instanceof ApiError) {
    // 业务错误：e.code / e.reason / e.message
  } else {
    // 网络/序列化错误
  }
}
```

## 测试

```bash
npm test
```
