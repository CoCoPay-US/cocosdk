package cocopay

import (
	"crypto/rsa"
	"encoding/json"
	"fmt"
)

// OrderNotify 订单异步通知（代收/代付/退款通知统一结构）
//
// 代收/代付进入终态时触发：代收为 成功/失败/驳回/退款/冲正，代付为 成功/失败/驳回。
// 商户应答：HTTP 200 且响应体纯文本 "SUCCESS"（去空白精确匹配）；
// 未正确应答时平台按 1/2/3/4/5 分钟间隔重试最多 6 次，商户必须以 order_no(+state) 幂等。
type OrderNotify struct {
	MchNo             string `json:"mch_no"`
	OrderNo           string `json:"order_no"`
	MchOrderNo        string `json:"mch_order_no"`
	Currency          string `json:"currency"`
	OrderAmount       string `json:"order_amount"`       // 订单金额（字符串，保留 2 位小数）
	TransactionAmount string `json:"transaction_amount"` // 实际支付金额（字符串，保留 2 位小数）
	State             string `json:"state"`              // 见 NotifyState* 常量
	CompleteTime      int64  `json:"complete_time"`
	FailReason        string `json:"fail_reason"`
	Extra             string `json:"extra"`
	// 代付通知额外字段
	Account     string `json:"account"`
	AccountName string `json:"account_name"`
	// 退款通知额外字段
	RefundAmount string `json:"refund_amount"` // 退款金额（字符串，保留 2 位小数）
}

// ParseOrderNotify 解析通知报文
func ParseOrderNotify(body []byte) (*OrderNotify, error) {
	var notify OrderNotify
	if err := json.Unmarshal(body, &notify); err != nil {
		return nil, fmt.Errorf("cocopay: 通知报文解析失败: %w", err)
	}
	return &notify, nil
}

// NotifyHeaders 通知请求头（验签用）
type NotifyHeaders struct {
	MchNo     string // x-mch-no
	Timestamp string // x-timestamp
	Nonce     string // x-nonce
	Signature string // x-signature（RSA-PSS 签名）
}

// VerifyOrderNotify 校验异步通知签名（RSA-PSS + SHA-256，公钥由平台运营提供）
//
// platformPublicKey 可用 ParsePublicKeyPEM 加载（支持 PEM 或纯 Base64）。
func VerifyOrderNotify(platformPublicKey *rsa.PublicKey, headers NotifyHeaders, body []byte) error {
	if platformPublicKey == nil {
		return fmt.Errorf("cocopay: 平台公钥不能为空")
	}
	signature, err := ParseSignatureHeader(headers.Signature)
	if err != nil {
		return err
	}
	content := BuildSignContent(headers.MchNo, headers.Timestamp, headers.Nonce, body)
	if err := VerifyNotifySign(platformPublicKey, content, signature); err != nil {
		return fmt.Errorf("cocopay: 通知验签失败: %w", err)
	}
	return nil
}
