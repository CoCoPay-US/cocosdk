package cocopay

import (
	"bytes"
	"context"
	"crypto/rsa"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"time"
)

// Config SDK 配置
type Config struct {
	// BaseURL 支付网关地址（为空时从环境变量 COCOPAY_BASE_URL 读取）
	BaseURL string
	// MchNo 商户号
	MchNo string
	// PrivateKey 商户 RSA 私钥（请求签名用，可用 ParsePrivateKeyPEM 加载）
	PrivateKey *rsa.PrivateKey
	// Timeout 请求超时（默认 15 秒）
	Timeout time.Duration
	// HTTPClient 自定义 HTTP 客户端（可选）
	HTTPClient *http.Client
}

// Client CoCoPay 商户 SDK 客户端
type Client struct {
	cfg        Config
	httpClient *http.Client
}

// NewClient 创建客户端
func NewClient(cfg Config) (*Client, error) {
	if cfg.BaseURL == "" {
		cfg.BaseURL = os.Getenv(EnvBaseURL)
	}
	if cfg.BaseURL == "" {
		return nil, fmt.Errorf("cocopay: BaseURL 未配置（请显式配置或设置环境变量 %s）", EnvBaseURL)
	}
	if cfg.MchNo == "" {
		return nil, fmt.Errorf("cocopay: MchNo 不能为空")
	}
	if cfg.PrivateKey == nil {
		return nil, fmt.Errorf("cocopay: PrivateKey 不能为空")
	}
	hc := cfg.HTTPClient
	if hc == nil {
		timeout := cfg.Timeout
		if timeout <= 0 {
			timeout = 15 * time.Second
		}
		hc = &http.Client{Timeout: timeout}
	}
	return &Client{cfg: cfg, httpClient: hc}, nil
}

// envelope 网关统一响应壳
type envelope struct {
	Code    int             `json:"code"`
	Reason  string          `json:"reason"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

// do 发起签名 POST 请求并解出 data
func (c *Client) do(ctx context.Context, path string, reqBody interface{}, out interface{}) error {
	body, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("cocopay: 请求序列化失败: %w", err)
	}

	timestamp := strconv.FormatInt(time.Now().Unix(), 10)
	nonce := GenerateNonce()
	signature, err := SignRSAPSS(c.cfg.PrivateKey, BuildSignContent(c.cfg.MchNo, timestamp, nonce, body))
	if err != nil {
		return fmt.Errorf("cocopay: 签名失败: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.cfg.BaseURL+path, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("x-mch-no", c.cfg.MchNo)
	req.Header.Set("x-timestamp", timestamp)
	req.Header.Set("x-nonce", nonce)
	req.Header.Set("x-signature", fmt.Sprintf(SignHeaderFormat, signature))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("cocopay: 网络请求失败: %w", err)
	}
	defer resp.Body.Close()
	respBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("cocopay: 读取响应失败: %w", err)
	}

	var env envelope
	if err := json.Unmarshal(respBytes, &env); err != nil {
		return fmt.Errorf("cocopay: 响应解析失败（HTTP %d）: %s", resp.StatusCode, string(respBytes))
	}
	if resp.StatusCode != http.StatusOK || env.Code != 200 {
		return &APIError{Code: env.Code, Reason: env.Reason, Message: env.Message}
	}
	if out != nil && len(env.Data) > 0 {
		if err := json.Unmarshal(env.Data, out); err != nil {
			return fmt.Errorf("cocopay: 响应数据解析失败: %w", err)
		}
	}
	return nil
}
