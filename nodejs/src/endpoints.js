'use strict'

/** 接口 URI */
const PATH_COLLECTION_CREATE = '/v1/payment/collection/create'
const PATH_COLLECTION_QUERY = '/v1/payment/collection/query'
const PATH_PAYOUT_CREATE = '/v1/payment/pay_out/create'
const PATH_PAYOUT_QUERY = '/v1/payment/pay_out/query'
const PATH_BALANCE_QUERY = '/v1/merchant/balance/query'

/** 支付方式（payment_method），实际可用方式以商户开通的通道配置为准 */
const PaymentMethod = {
  // 代收支付方式
  CASHAPP: 'cashapp', // Cash App
  PAYPAL: 'paypal', // PayPal
  GOOGLEPAY: 'googlepay', // Google Pay
  APPLEPAY: 'applepay', // Apple Pay
  // 代付支付方式（提现方式）
  PAYOUT_CASHAPP: 'cashapp', // Cash App（account 为 $ 开头的 Cash App 账户）
  PAYOUT_PAYPAL: 'paypal', // PayPal（account 为收款人 PayPal 邮箱）
  PAYOUT_DEBIT_CARD: 'debitcard', // 收款卡（需同时传 card_valid，格式 MM/YYYY）
  PAYOUT_BANK_CARD: 'bankcard', // 银行账户（USD 需同时传 routing_code，ACH 路由编号）
}

module.exports = {
  PATH_COLLECTION_CREATE,
  PATH_COLLECTION_QUERY,
  PATH_PAYOUT_CREATE,
  PATH_PAYOUT_QUERY,
  PATH_BALANCE_QUERY,
  PaymentMethod,
}
