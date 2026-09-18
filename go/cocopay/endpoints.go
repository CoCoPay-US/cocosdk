package cocopay

// EnvBaseURL 支付网关地址环境变量名（未在 Config 中显式配置 BaseURL 时从该环境变量读取）
const EnvBaseURL = "COCOPAY_BASE_URL"

// 接口 URI
const (
	// PathCollectionCreate 创建代收订单
	PathCollectionCreate = "/v1/payment/collection/create"
	// PathCollectionQuery 查询代收订单
	PathCollectionQuery = "/v1/payment/collection/query"
	// PathPayOutCreate 创建代付订单
	PathPayOutCreate = "/v1/payment/pay_out/create"
	// PathPayOutQuery 查询代付订单
	PathPayOutQuery = "/v1/payment/pay_out/query"
	// PathBalanceQuery 查询商户余额
	PathBalanceQuery = "/v1/merchant/balance/query"
)
