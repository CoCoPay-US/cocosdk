<?php

declare(strict_types=1);

namespace Cocopay\Sdk;

/** 接口 URI 常量 */
final class Endpoints
{
    /** 支付网关地址环境变量名（未显式配置 base_url 时从该环境变量读取） */
    public const ENV_BASE_URL = 'COCOPAY_BASE_URL';

    /** 创建代收订单 */
    public const PATH_COLLECTION_CREATE = '/v1/payment/collection/create';
    /** 查询代收订单 */
    public const PATH_COLLECTION_QUERY = '/v1/payment/collection/query';
    /** 创建代付订单 */
    public const PATH_PAYOUT_CREATE = '/v1/payment/pay_out/create';
    /** 查询代付订单 */
    public const PATH_PAYOUT_QUERY = '/v1/payment/pay_out/query';
    /** 查询商户余额 */
    public const PATH_BALANCE_QUERY = '/v1/merchant/balance/query';
}
