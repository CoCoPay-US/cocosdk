<?php

declare(strict_types=1);

require __DIR__ . '/../src/SignUtil.php';
require __DIR__ . '/../src/ApiException.php';
require __DIR__ . '/../src/Endpoints.php';
require __DIR__ . '/../src/CocopayClient.php';
require __DIR__ . '/../src/NotifyUtil.php';

use Cocopay\Sdk\CocopayClient;
use Cocopay\Sdk\NotifyUtil;

// 1. 加载商户 RSA 私钥并创建客户端
// 'base_url' 填写平台提供的 API 网关地址；也可不配，从环境变量 COCOPAY_BASE_URL 读取
$client = new CocopayClient([
    'base_url'    => 'https://api.example.com', // TODO: 替换为平台提供的 API 网关地址
    'mch_no'      => '100000',
    'private_key' => file_get_contents('merchant_private_key.pem'),
]);

// 2. 创建代收订单
$collResp = $client->createCollectionOrder([
    'mch_order_no'   => 'ORD' . time(),
    'currency'       => 'USD',
    'amount'         => '100.00',
    'payment_method' => 'cashapp',
    'email'          => 'john@example.com',
    'client_ip'      => '203.0.113.10',
    'mch_user_id'    => 'U10001',
    'notify_url'     => 'https://merchant.example.com/notify',
]);
echo "代收订单创建成功: {$collResp['order_no']} {$collResp['pay_url']}\n";

// 3. 创建代付订单
$payResp = $client->createPayOutOrder([
    'mch_order_no'   => 'PO' . time(),
    'currency'       => 'USD',
    'amount'         => '50.00',
    'payment_method' => 'cash_app',
    'account'        => '$johndoe',
    'account_name'   => 'John Doe',
    'client_ip'      => '203.0.113.10',
    'mch_user_id'    => 'U10001',
]);
echo "代付订单创建成功: {$payResp['order_no']} status={$payResp['status']}\n";

// 4. 查询余额
$balance = $client->queryBalance();
foreach ($balance['balances'] as $b) {
    // 各余额字段为字符串金额（保留 2 位小数，如 "100.00"）
    echo "余额: {$b['currency']} 可用={$b['availableBalance']} 未结算={$b['unsettledBalance']} 冻结={$b['frozenBalance']}\n";
}

// 异步通知处理示例
//
// $body = file_get_contents('php://input');
// $ok = NotifyUtil::verifyOrderNotify($platformPublicKey, [
//     'mch_no'    => $_SERVER['HTTP_X_MCH_NO'] ?? '',
//     'timestamp' => $_SERVER['HTTP_X_TIMESTAMP'] ?? '',
//     'nonce'     => $_SERVER['HTTP_X_NONCE'] ?? '',
//     'signature' => $_SERVER['HTTP_X_SIGNATURE'] ?? '',
// ], $body);
// if (!$ok) {
//     http_response_code(400);
//     exit;
// }
// $notify = NotifyUtil::parseOrderNotify($body);
// // order_amount/transaction_amount/refund_amount 为字符串金额（保留 2 位小数）；state 取值见 NotifyUtil::STATE_* 常量
// // TODO: 按 order_no + state 幂等处理
// http_response_code(200);
// echo 'SUCCESS';
