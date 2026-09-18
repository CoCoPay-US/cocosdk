package com.cocopay.sdk;

/** 接口 URI 常量 */
public final class Endpoints {

    private Endpoints() {}

    /** 支付网关地址环境变量名（未在 Config 中显式配置 BaseURL 时从该环境变量读取） */
    public static final String ENV_BASE_URL = "COCOPAY_BASE_URL";

    /** 创建代收订单 */
    public static final String PATH_COLLECTION_CREATE = "/v1/payment/collection/create";
    /** 查询代收订单 */
    public static final String PATH_COLLECTION_QUERY = "/v1/payment/collection/query";
    /** 创建代付订单 */
    public static final String PATH_PAYOUT_CREATE = "/v1/payment/pay_out/create";
    /** 查询代付订单 */
    public static final String PATH_PAYOUT_QUERY = "/v1/payment/pay_out/query";
    /** 查询商户余额 */
    public static final String PATH_BALANCE_QUERY = "/v1/merchant/balance/query";
}
