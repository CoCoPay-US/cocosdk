package cocopay

import "context"

// QueryBalanceRequest 查询商户余额请求
type QueryBalanceRequest struct {
	MchNo string `json:"mch_no"` // 商户号，必须与本客户端配置的 MchNo 一致
}

// BalanceItem 单币种余额（金额均为字符串，保留 2 位小数）
type BalanceItem struct {
	Currency                 string `json:"currency"`
	AvailableBalance         string `json:"availableBalance"`         // 可用余额
	UnsettledBalance         string `json:"unsettledBalance"`         // 未结算余额（T+N 结算中）
	FrozenBalance            string `json:"frozenBalance"`            // 冻结余额（代付冻结）
	WithdrawalPendingBalance string `json:"withdrawalPendingBalance"` // 提现中余额（当前固定为 0）
	PayoutPendingBalance     string `json:"payoutPendingBalance"`     // 代付处理中余额（等于冻结余额）
}

// QueryBalanceReply 查询商户余额响应
type QueryBalanceReply struct {
	Balances []BalanceItem `json:"balances"`
}

// QueryBalance 查询商户余额（自动使用客户端配置的商户号）
func (c *Client) QueryBalance(ctx context.Context) (*QueryBalanceReply, error) {
	reply := &QueryBalanceReply{}
	req := &QueryBalanceRequest{MchNo: c.cfg.MchNo}
	if err := c.do(ctx, PathBalanceQuery, req, reply); err != nil {
		return nil, err
	}
	return reply, nil
}
