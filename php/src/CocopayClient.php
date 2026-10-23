<?php

declare(strict_types=1);

namespace Cocopay\Sdk;

/**
 * CoCoPay 商户 SDK 客户端
 *
 * // base_url 可不配，默认从环境变量 COCOPAY_BASE_URL 读取
 * $client = new CocopayClient([
 *     'mch_no'      => '100000',
 *     'private_key' => file_get_contents('merchant_private_key.pem'),
 * ]);
 */
class CocopayClient
{
    private string $baseUrl;
    private string $mchNo;
    private string $privateKey;
    private int $timeout;

    /**
     * @param array{base_url?: string, mch_no: string, private_key: string, timeout?: int} $config
     */
    public function __construct(array $config)
    {
        if (empty($config['base_url'])) {
            $config['base_url'] = getenv(Endpoints::ENV_BASE_URL) ?: '';
        }
        if (empty($config['base_url'])) {
            throw new \InvalidArgumentException('cocopay: base_url 未配置（请显式配置或设置环境变量 ' . Endpoints::ENV_BASE_URL . '）');
        }
        if (empty($config['mch_no'])) {
            throw new \InvalidArgumentException('cocopay: mch_no 不能为空');
        }
        if (empty($config['private_key'])) {
            throw new \InvalidArgumentException('cocopay: private_key 不能为空');
        }
        $this->baseUrl = rtrim($config['base_url'], '/');
        $this->mchNo = (string)$config['mch_no'];
        $this->privateKey = $config['private_key'];
        $this->timeout = $config['timeout'] ?? 15;
    }

    /**
     * 发起签名 POST 请求并解出 data
     *
     * @throws ApiException
     */
    private function do(string $path, array $reqBody): array
    {
        $body = json_encode($reqBody, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        $timestamp = (string)time();
        $nonce = SignUtil::generateNonce();
        $signature = SignUtil::signPSS($this->privateKey, SignUtil::buildSignContent($this->mchNo, $timestamp, $nonce, $body));

        $ch = curl_init($this->baseUrl . $path);
        curl_setopt_array($ch, [
            CURLOPT_POST           => true,
            CURLOPT_POSTFIELDS     => $body,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => $this->timeout,
            CURLOPT_HTTPHEADER     => [
                'Content-Type: application/json',
                'x-mch-no: ' . $this->mchNo,
                'x-timestamp: ' . $timestamp,
                'x-nonce: ' . $nonce,
                'x-signature: ' . SignUtil::signHeader($signature),
            ],
        ]);
        $respBody = curl_exec($ch);
        $statusCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        if ($respBody === false) {
            $err = curl_error($ch);
            curl_close($ch);
            throw new \RuntimeException('cocopay: 网络请求失败: ' . $err);
        }
        curl_close($ch);

        $env = json_decode((string)$respBody, true);
        if (!is_array($env)) {
            throw new \RuntimeException("cocopay: 响应解析失败（HTTP {$statusCode}）");
        }
        if ($statusCode !== 200 || (int)($env['code'] ?? 0) !== 200) {
            throw new ApiException((int)($env['code'] ?? 0), (string)($env['reason'] ?? ''), (string)($env['message'] ?? ''));
        }
        return is_array($env['data'] ?? null) ? $env['data'] : [];
    }

    /**
     * 创建代收订单
     *
     * @param array $req {mch_order_no: string, currency: string, amount: string（金额字符串，保留 2 位小数，如 "100.00"）, payment_method, email, client_ip, mch_user_id, telephone?, notify_url?, extra?, card_valid?, routing_code?, goods_infos?}
     *                    goods_infos: 物品信息（可选，最多 50 条）array<int, array{sku_id?: string, sku_name: string, price: string, num: int, sku_desc: string, sku_url: string}>
     * @return array{order_no: string, mch_order_no: string, status: int, fail_reason: string, pay_url: string, extra: string}
     */
    public function createCollectionOrder(array $req): array
    {
        return $this->do(Endpoints::PATH_COLLECTION_CREATE, $req);
    }

    /**
     * 查询代收订单（order_no 与 mch_order_no 至少填一个）
     *
     * @return array{mch_no: string, order_no: string, mch_order_no: string, currency: string, order_amount: string, transaction_amount: string, state: string, fail_reason: string, complete_time: int, pay_url: string, extra: string}
     *               金额字段（order_amount/transaction_amount）为字符串，保留 2 位小数（如 "100.00"）
     */
    public function queryCollectionOrder(array $req): array
    {
        return $this->do(Endpoints::PATH_COLLECTION_QUERY, $req);
    }

    /**
     * 创建代付订单
     * 下单后可能需要两级审核（商户审核 status=2 / 管理员审核 status=3），全部通过后自动出款，以异步通知为准。
     *
     * @param array $req {mch_order_no: string, currency: string, amount: string（金额字符串，保留 2 位小数）, payment_method, account, account_name, client_ip, mch_user_id, account_phone?, email?, notify_url?, extra?, routing_code?, card_valid?}
     * @return array{order_no: string, mch_order_no: string, status: int, fail_reason: string, extra: string}
     */
    public function createPayOutOrder(array $req): array
    {
        return $this->do(Endpoints::PATH_PAYOUT_CREATE, $req);
    }

    /**
     * 查询代付订单（order_no 与 mch_order_no 至少填一个）
     *
     * @return array{mch_no: string, order_no: string, mch_order_no: string, currency: string, order_amount: string, transaction_amount: string, state: string, complete_time: int, fail_reason: string, extra: string}
     *               金额字段（order_amount/transaction_amount，snake_case 键）为字符串，保留 2 位小数（如 "100.00"）
     */
    public function queryPayOutOrder(array $req): array
    {
        return $this->do(Endpoints::PATH_PAYOUT_QUERY, $req);
    }

    /**
     * 查询商户余额
     *
     * @return array{balances: array<int, array{currency: string, availableBalance: string, unsettledBalance: string, frozenBalance: string, withdrawalPendingBalance: string, payoutPendingBalance: string}>}
     *               5 个余额字段均为字符串金额，保留 2 位小数（如 "100.00"）
     */
    public function queryBalance(): array
    {
        return $this->do(Endpoints::PATH_BALANCE_QUERY, ['mch_no' => $this->mchNo]);
    }
}
