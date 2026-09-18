package cocopay

// 代收订单状态
const (
	CollectionStateInit         = 1 // 已创建
	CollectionStatePendingAudit = 2 // 待审核
	CollectionStatePending      = 3 // 支付中
	CollectionStateRejected     = 4 // 已驳回
	CollectionStateSuccess      = 5 // 支付成功
	CollectionStateFailed       = 6 // 支付失败
	CollectionStateReversal     = 7 // 已冲正
	CollectionStateChargeBack   = 8 // 已拒付
	CollectionStateRefund       = 9 // 已退款
)

// 代付订单状态
const (
	PayOutStateInit                 = 1 // 已创建
	PayOutStatePendingMerchantAudit = 2 // 待商户审核
	PayOutStatePendingAdminAudit    = 3 // 待管理员审核
	PayOutStateAudited              = 4 // 审核全部完成，待发起出款
	PayOutStateRejected             = 5 // 审核驳回
	PayOutStatePending              = 6 // 出款处理中
	PayOutStateSuccess              = 7 // 出款成功
	PayOutStateFailed               = 8 // 出款失败
)

// 异步通知的订单状态（state 字符串）
const (
	NotifyStateSuccess       = "success"        // 成功
	NotifyStateFailed        = "failed"         // 失败
	NotifyStateRejected      = "rejected"       // 审核驳回
	NotifyStateRefund        = "refund"         // 全额退款
	NotifyStatePartialRefund = "partial_refund" // 部分退款（可能多次推送，按 order_no+refund_amount 幂等）
	NotifyStateReversal      = "reversal"       // 冲正
	NotifyStateChargeBack    = "chargeback"     // 拒付
)
