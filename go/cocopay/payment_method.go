package cocopay

// 支付方式（payment_method）
// 实际可用方式以商户开通的通道配置为准

// 代收支付方式
const (
	PaymentMethodCashApp   = "cashapp"    // Cash App
	PaymentMethodPayPal    = "paypal"     // PayPal
	PaymentMethodGooglePay = "googlepay"  // Google Pay
	PaymentMethodApplePay  = "applepay"   // Apple Pay
)

// 代付支付方式（提现方式）
const (
	PayOutMethodCashApp   = "cashapp"    // Cash App（account 为 $ 开头的 Cash App 账户）
	PayOutMethodPayPal    = "paypal"     // PayPal（account 为收款人 PayPal 邮箱）
	PayOutMethodDebitCard = "debitcard" // 收款卡（需同时传 card_valid，格式 MM/YYYY）
	PayOutMethodBankCard  = "bankcard"  // 银行账户（USD 需同时传 routing_code，ACH 路由编号）
)
