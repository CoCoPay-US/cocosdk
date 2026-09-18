# CoCoPay 商户 SDK（PHP）

CoCoPay 支付网关商户接入 SDK：代收下单/查询、代付下单/查询、余额查询、异步通知验签。PHP >= 7.4，仅依赖 ext-openssl / ext-curl / ext-json。

## 安装（Composer）

仓库地址：https://github.com/CoCoPay-US/cocosdk （本包位于 `php/` 目录）

方式一（VCS 仓库引入，Composer 要求 composer.json 在仓库根目录，子目录需用 path 方式）：

```bash
git clone https://github.com/CoCoPay-US/cocosdk.git
```

```json
{
  "repositories": [
    {"type": "path", "url": "/path/to/cocosdk/php"}
  ],
  "require": {
    "cocopay/sdk": "*"
  }
}
```

方式二：将 `php/src` 复制到项目内手动引入（或等后续发布到 Packagist 后直接 `composer require cocopay/sdk`）。

## 快速开始

```php
use Cocopay\Sdk\CocopayClient;

// 'base_url' 填写平台提供的 API 网关地址（也可不配，从环境变量 COCOPAY_BASE_URL 读取）
$client = new CocopayClient([
    'base_url'    => 'https://api.example.com', // TODO: 替换为平台提供的 API 网关地址
    'mch_no'      => '100000',
    'private_key' => file_get_contents('merchant_private_key.pem'),
]);

$resp = $client->createCollectionOrder([
    'mch_order_no'   => 'ORD20240101001',
    'currency'       => 'USD',
    'amount'         => '100.00',
    'payment_method' => 'cashapp',
    'email'          => 'john@example.com',
    'client_ip'      => '203.0.113.10',
    'mch_user_id'    => 'U10001',
]);
echo $resp['pay_url'];
```

完整示例见 `examples/main.php`。

## 接口一览

| 方法 | 接口 |
|------|------|
| `createCollectionOrder($req)` | `POST /v1/payment/collection/create` |
| `queryCollectionOrder($req)` | `POST /v1/payment/collection/query` |
| `createPayOutOrder($req)` | `POST /v1/payment/pay_out/create` |
| `queryPayOutOrder($req)` | `POST /v1/payment/pay_out/query` |
| `queryBalance()` | `POST /v1/merchant/balance/query` |

## 字段约定

- 金额字段均为字符串（保留 2 位小数，如 `"100.00"`）：下单请求的 `amount`，查询响应的 `order_amount`/`transaction_amount`，通知报文的 `order_amount`/`transaction_amount`/`refund_amount`，余额查询的 `availableBalance`/`unsettledBalance`/`frozenBalance`/`withdrawalPendingBalance`/`payoutPendingBalance`。
- `mch_no`、订单号等 ID 均按字符串处理。

## 异步通知验签

```php
use Cocopay\Sdk\NotifyUtil;

$body = file_get_contents('php://input');
$ok = NotifyUtil::verifyOrderNotify($platformPublicKey, [
    'mch_no'    => $_SERVER['HTTP_X_MCH_NO'] ?? '',
    'timestamp' => $_SERVER['HTTP_X_TIMESTAMP'] ?? '',
    'nonce'     => $_SERVER['HTTP_X_NONCE'] ?? '',
    'signature' => $_SERVER['HTTP_X_SIGNATURE'] ?? '',
], $body);
if ($ok) {
    $notify = NotifyUtil::parseOrderNotify($body);
    // TODO: 按 order_no + state 幂等处理
    http_response_code(200);
    echo 'SUCCESS';
}
```

签名统一为 **RSA-PSS + SHA-256**（盐长 32，请求与异步通知一致），`x-signature` 头格式：`algorithm=SHA256withRSA/PSS,version=v2,signature=<base64>`，SDK 已内置。

## 错误处理

```php
use Cocopay\Sdk\ApiException;

try {
    $client->createCollectionOrder($req);
} catch (ApiException $e) {
    // 业务错误：$e->getApiCode() / $e->getApiReason() / $e->getMessage()
}
```

## 测试

```bash
php tests/run.php
```
