package cocopay

import "fmt"

// APIError 网关返回的业务错误（HTTP 200 且 code=200 之外的情况）
type APIError struct {
	Code    int    `json:"code"`
	Reason  string `json:"reason"`
	Message string `json:"message"`
}

func (e *APIError) Error() string {
	return fmt.Sprintf("cocopay: 请求失败 code=%d reason=%s message=%s", e.Code, e.Reason, e.Message)
}
