package cocopay

import "context"

// CreatePayOutOrderRequest 创建代付订单请求
type CreatePayOutOrderRequest struct {
	MchOrderNo    string `json:"mch_order_no"`   // 商户订单号（幂等键）
	Currency      string `json:"currency"`       // 币种：USD/HKD
	Amount        string `json:"amount"`         // 代付金额，单位元，保留2位小数
	PaymentMethod string `json:"payment_method"` // 提现方式：paypal/cash_app/debit_card/bank_card 等
	Account       string `json:"account"`        // 收款账号（含义随提现方式不同）
	AccountName   string `json:"account_name"`   // 收款人姓名（必填）
	AccountPhone  string `json:"account_phone,omitempty"`
	Email         string `json:"email,omitempty"`
	ClientIP      string `json:"client_ip"`            // 用户终端 IP（必填）
	NotifyURL     string `json:"notify_url,omitempty"` // 为空用商户配置的 callback_url
	Extra         string `json:"extra,omitempty"`
	MchUserID     string `json:"mch_user_id"`            // 商户侧用户 ID（必填）
	RoutingCode   string `json:"routing_code,omitempty"` // bankcard + USD/GBP 必填：USD=ACH路由号 GBP=Sort Code
	CardValid     string `json:"card_valid,omitempty"`   // card 必填，卡有效期 MM/YYYY
}

// CreatePayOutOrderReply 创建代付订单响应
type CreatePayOutOrderReply struct {
	OrderNo    string `json:"order_no"`
	MchOrderNo string `json:"mch_order_no"`
	Status     int    `json:"status"` // 见 PayOutState* 常量
	FailReason string `json:"fail_reason"`
	Extra      string `json:"extra"`
}

// QueryPayOutOrderRequest 查询代付订单请求（order_no 与 mch_order_no 至少填一个）
type QueryPayOutOrderRequest struct {
	OrderNo    string `json:"order_no,omitempty"`
	MchOrderNo string `json:"mch_order_no,omitempty"`
}

// QueryPayOutOrderReply 查询代付订单响应（字段与异步通知统一）
type QueryPayOutOrderReply struct {
	MchNo             string `json:"mch_no"`
	OrderNo           string `json:"order_no"`
	MchOrderNo        string `json:"mch_order_no"`
	Currency          string `json:"currency"`
	OrderAmount       string `json:"order_amount"`       // 订单金额（字符串，保留 2 位小数）
	TransactionAmount string `json:"transaction_amount"` // 实际出款金额（字符串，保留 2 位小数）
	State             string `json:"state"`              // 终态见 NotifyState* 常量；中间态为 init/pending_merchant_audit/pending_admin_audit/audited/pending
	CompleteTime      int64  `json:"complete_time"`
	FailReason        string `json:"fail_reason"`
	Extra             string `json:"extra"`
}

// CreatePayOutOrder 创建代付订单
//
// 下单后订单可能需要两级审核：超过通道免审金额先进商户审核（status=2），
// 按配置可能再进管理员审核（status=3），全部通过后自动出款，以异步通知为准。
// 下单会实时冻结商户余额，失败/驳回自动解冻退回。
func (c *Client) CreatePayOutOrder(ctx context.Context, req *CreatePayOutOrderRequest) (*CreatePayOutOrderReply, error) {
	reply := &CreatePayOutOrderReply{}
	if err := c.do(ctx, PathPayOutCreate, req, reply); err != nil {
		return nil, err
	}
	return reply, nil
}

// QueryPayOutOrder 查询代付订单
func (c *Client) QueryPayOutOrder(ctx context.Context, req *QueryPayOutOrderRequest) (*QueryPayOutOrderReply, error) {
	reply := &QueryPayOutOrderReply{}
	if err := c.do(ctx, PathPayOutQuery, req, reply); err != nil {
		return nil, err
	}
	return reply, nil
}
