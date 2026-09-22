<?php

declare(strict_types=1);

namespace Cocopay\Sdk;

/**
 * 支付方式（payment_method）常量
 * 实际可用方式以商户开通的通道配置为准
 */
final class PaymentMethod
{
    // ===== 代收支付方式 =====
    /** Cash App */
    public const CASHAPP = 'cashapp';
    /** PayPal */
    public const PAYPAL = 'paypal';
    /** Google Pay */
    public const GOOGLEPAY = 'googlepay';
    /** Apple Pay */
    public const APPLEPAY = 'applepay';

    // ===== 代付支付方式（提现方式） =====
    /** Cash App（account 为 $ 开头的 Cash App 账户） */
    public const PAYOUT_CASHAPP = 'cashapp';
    /** PayPal（account 为收款人 PayPal 邮箱） */
    public const PAYOUT_PAYPAL = 'paypal';
    /** 收款卡（需同时传 card_valid，格式 MM/YYYY） */
    public const PAYOUT_DEBIT_CARD = 'debitcard';
    /** 银行账户（USD 需同时传 routing_code，ACH 路由编号） */
    public const PAYOUT_BANK_CARD = 'bankcard';
}
