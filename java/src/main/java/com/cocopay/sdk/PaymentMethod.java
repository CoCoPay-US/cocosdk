package com.cocopay.sdk;

/**
 * 支付方式（payment_method）常量
 * 实际可用方式以商户开通的通道配置为准
 */
public final class PaymentMethod {

    private PaymentMethod() {}

    // ===== 代收支付方式 =====
    /** Cash App */
    public static final String CASHAPP = "cashapp";
    /** PayPal */
    public static final String PAYPAL = "paypal";
    /** Google Pay */
    public static final String GOOGLEPAY = "googlepay";
    /** Apple Pay */
    public static final String APPLEPAY = "applepay";

    // ===== 代付支付方式（提现方式） =====
    /** Cash App（account 为 $ 开头的 Cash App 账户） */
    public static final String PAYOUT_CASHAPP = "cashapp";
    /** PayPal（account 为收款人 PayPal 邮箱） */
    public static final String PAYOUT_PAYPAL = "paypal";
    /** 收款卡（需同时传 cardValid，格式 MM/YYYY） */
    public static final String PAYOUT_DEBIT_CARD = "debitcard";
    /** 银行账户（USD 需同时传 routingCode，ACH 路由编号） */
    public static final String PAYOUT_BANK_CARD = "bankcard";
}
