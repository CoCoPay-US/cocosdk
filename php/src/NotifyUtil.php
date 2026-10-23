<?php

declare(strict_types=1);

namespace Cocopay\Sdk;

/** 异步通知工具：验签（RSA-PSS + SHA-256）与报文解析 */
final class NotifyUtil
{
    /** 异步通知的订单状态（state 字符串） */
    public const STATE_SUCCESS = 'success';
    public const STATE_FAILED = 'failed';
    public const STATE_REJECTED = 'rejected';
    public const STATE_REFUND = 'refund';
    public const STATE_PARTIAL_REFUND = 'partial_refund';
    public const STATE_REVERSAL = 'reversal';
    public const STATE_CHARGEBACK = 'chargeback'; // 拒付

    /**
     * 校验异步通知签名（公钥由平台运营提供，RSA-PSS + SHA-256）
     *
     * @param string $platformPublicKey 平台 RSA 公钥（PEM 或纯 Base64）
     * @param array $headers {mch_no, timestamp, nonce, signature}（对应 x-mch-no / x-timestamp / x-nonce / x-signature 头）
     * @param string $body 原始通知报文
     */
    public static function verifyOrderNotify(string $platformPublicKey, array $headers, string $body): bool
    {
        $signature = SignUtil::parseSignatureHeader((string)($headers['signature'] ?? ''));
        $content = SignUtil::buildSignContent(
            (string)($headers['mch_no'] ?? ''),
            (string)($headers['timestamp'] ?? ''),
            (string)($headers['nonce'] ?? ''),
            $body
        );
        return SignUtil::verifyPSS($platformPublicKey, $content, $signature);
    }

    /**
     * 解析通知报文
     *
     * @return array{mch_no: string, order_no: string, mch_order_no: string, currency: string, order_amount: string, transaction_amount: string, state: string, complete_time: int, fail_reason: string, extra: string, account?: string, account_name?: string, refund_amount?: string, refund_no?: string}
     *               金额字段（order_amount/transaction_amount/refund_amount）为字符串，保留 2 位小数（如 "100.00"）；refund_no 为平台退款单号（退款通知幂等键）
     */
    public static function parseOrderNotify(string $body): array
    {
        $notify = json_decode($body, true);
        if (!is_array($notify)) {
            throw new \InvalidArgumentException('cocopay: 通知报文解析失败');
        }
        return $notify;
    }
}
