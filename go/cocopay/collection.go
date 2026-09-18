package cocopay

import "context"

// CreateCollectionOrderRequest 创建代收订单请求
type CreateCollectionOrderRequest struct {
	MchOrderNo    string `json:"mch_order_no"`           // 商户订单号（幂等键，重复提交返回原订单）
	Currency      string `json:"currency"`               // 币种：USD/HKD
	Amount        string `json:"amount"`                 // 订单金额，单位元，保留2位小数
	PaymentMethod string `json:"payment_method"`         // 支付方式：cashapp/paypal/googlepay/applepay/bank_card/debit_card
	Telephone     string `json:"telephone,omitempty"`    // 付款人电话（可选）
	Email         string `json:"email"`                  // 付款人邮箱（必填）
	ClientIP      string `json:"client_ip"`              // 用户终端 IP（必填）
	NotifyURL     string `json:"notify_url,omitempty"`   // 异步通知地址，为空用商户配置的 callback_url
	Extra         string `json:"extra,omitempty"`        // 透传字段，回调原样返回
	MchUserID     string `json:"mch_user_id"`            // 商户侧用户 ID（必填）
	CardValid     string `json:"card_valid,omitempty"`   // debit_card 必填，卡有效期 MM/YYYY
	RoutingCode   string `json:"routing_code,omitempty"` // bank_card + USD/GBP 必填：USD=ACH路由号 GBP=Sort Code
}

// CreateCollectionOrderReply 创建代收订单响应
type CreateCollectionOrderReply struct {
	OrderNo    string `json:"order_no"`
	MchOrderNo string `json:"mch_order_no"`
	Status     int    `json:"status"`      // 见 CollectionState* 常量
	FailReason string `json:"fail_reason"` // 失败原因
	PayURL     string `json:"pay_url"`     // 收银台/支付链接，引导用户跳转完成支付
	Extra      string `json:"extra"`
}

// QueryCollectionOrderRequest 查询代收订单请求（order_no 与 mch_order_no 至少填一个）
type QueryCollectionOrderRequest struct {
	OrderNo    string `json:"order_no,omitempty"`
	MchOrderNo string `json:"mch_order_no,omitempty"`
}

// QueryCollectionOrderReply 查询代收订单响应（字段与异步通知统一）
type QueryCollectionOrderReply struct {
	MchNo             int64  `json:"mch_no"`
	OrderNo           string `json:"order_no"`
	MchOrderNo        string `json:"mch_order_no"`
	Currency          string `json:"currency"`
	OrderAmount       string `json:"order_amount"`       // 订单金额（字符串，保留 2 位小数）
	TransactionAmount string `json:"transaction_amount"` // 实际支付金额（字符串，保留 2 位小数）
	State             string `json:"state"`              // 终态见 NotifyState* 常量；中间态为 init/pending_audit/pending/pending_risk
	FailReason        string `json:"fail_reason"`
	CompleteTime      int64  `json:"complete_time"` // 毫秒时间戳，仅成功时返回
	PayURL            string `json:"pay_url"`       // 仅支付中状态返回
	Extra             string `json:"extra"`
}

// CreateCollectionOrder 创建代收订单
func (c *Client) CreateCollectionOrder(ctx context.Context, req *CreateCollectionOrderRequest) (*CreateCollectionOrderReply, error) {
	reply := &CreateCollectionOrderReply{}
	if err := c.do(ctx, PathCollectionCreate, req, reply); err != nil {
		return nil, err
	}
	return reply, nil
}

// QueryCollectionOrder 查询代收订单
func (c *Client) QueryCollectionOrder(ctx context.Context, req *QueryCollectionOrderRequest) (*QueryCollectionOrderReply, error) {
	reply := &QueryCollectionOrderReply{}
	if err := c.do(ctx, PathCollectionQuery, req, reply); err != nil {
		return nil, err
	}
	return reply, nil
}
