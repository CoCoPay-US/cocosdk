package cocopay

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"fmt"
	"strings"
)

// 签名规则（商户请求）：待签名串 = {mch_no},{timestamp},{nonce},{body}
// 算法 RSA-PSS + SHA-256（盐长 32），签名 Base64 后放入 x-signature 头
//
// 异步通知验签：同一待签名串规则，同为 RSA-PSS + SHA-256，公钥由平台运营提供

// SignHeaderFormat x-signature 头格式
const SignHeaderFormat = "algorithm=SHA256withRSA/PSS,version=v2,signature=%s"

// BuildSignContent 构造待签名字符串
func BuildSignContent(mchNo, timestamp, nonce string, body []byte) string {
	return fmt.Sprintf("%s,%s,%s,%s", mchNo, timestamp, nonce, string(body))
}

// SignRSAPSS 商户请求签名：RSA-PSS + SHA-256（与异步通知签名统一），返回 Base64 签名
// 盐长固定 32（= 摘要长度），与平台验签端保持一致
func SignRSAPSS(privateKey *rsa.PrivateKey, content string) (string, error) {
	digest := sha256.Sum256([]byte(content))
	sig, err := rsa.SignPSS(rand.Reader, privateKey, crypto.SHA256, digest[:], &rsa.PSSOptions{SaltLength: 32})
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(sig), nil
}

// VerifyNotifySign 异步通知验签：RSA-PSS + SHA-256
func VerifyNotifySign(publicKey *rsa.PublicKey, content, signatureBase64 string) error {
	sig, err := base64.StdEncoding.DecodeString(signatureBase64)
	if err != nil {
		return fmt.Errorf("签名 Base64 解码失败: %w", err)
	}
	digest := sha256.Sum256([]byte(content))
	return rsa.VerifyPSS(publicKey, crypto.SHA256, digest[:], sig, nil)
}

// ParseSignatureHeader 解析 x-signature 头（algorithm=...,version=...,signature=...），返回 signature 部分
func ParseSignatureHeader(header string) (string, error) {
	return ParseSignatureParam(header, "signature")
}

// ParseSignatureParam 解析 x-signature 头中指定字段的值（如 signature）
func ParseSignatureParam(header, name string) (string, error) {
	for _, part := range strings.Split(header, ",") {
		kv := strings.SplitN(strings.TrimSpace(part), "=", 2)
		if len(kv) == 2 && kv[0] == name {
			return kv[1], nil
		}
	}
	return "", fmt.Errorf("x-signature 头缺少 %s 字段", name)
}

// ParsePrivateKeyPEM 解析 PEM 格式 RSA 私钥（支持 PKCS#1 "RSA PRIVATE KEY" 与 PKCS#8 "PRIVATE KEY"）
func ParsePrivateKeyPEM(pemBytes []byte) (*rsa.PrivateKey, error) {
	block, _ := pem.Decode(pemBytes)
	if block == nil {
		// 兼容无 PEM 头尾的纯 Base64 DER
		der, err := base64.StdEncoding.DecodeString(strings.TrimSpace(string(pemBytes)))
		if err != nil {
			return nil, fmt.Errorf("私钥 PEM 解析失败")
		}
		return parsePrivateKeyDER(der)
	}
	return parsePrivateKeyDER(block.Bytes)
}

func parsePrivateKeyDER(der []byte) (*rsa.PrivateKey, error) {
	if key, err := x509.ParsePKCS1PrivateKey(der); err == nil {
		return key, nil
	}
	key, err := x509.ParsePKCS8PrivateKey(der)
	if err != nil {
		return nil, fmt.Errorf("私钥解析失败（仅支持 RSA 私钥）: %w", err)
	}
	rsaKey, ok := key.(*rsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("私钥解析失败（仅支持 RSA 私钥）")
	}
	return rsaKey, nil
}

// ParsePublicKeyPEM 解析 PEM 格式 RSA 公钥（支持 PKIX "PUBLIC KEY" 与 PKCS#1 "RSA PUBLIC KEY"，或纯 Base64 DER）
func ParsePublicKeyPEM(pemBytes []byte) (*rsa.PublicKey, error) {
	block, _ := pem.Decode(pemBytes)
	if block == nil {
		der, err := base64.StdEncoding.DecodeString(strings.TrimSpace(string(pemBytes)))
		if err != nil {
			return nil, fmt.Errorf("公钥 PEM 解析失败")
		}
		return parsePublicKeyDER(der)
	}
	return parsePublicKeyDER(block.Bytes)
}

func parsePublicKeyDER(der []byte) (*rsa.PublicKey, error) {
	if key, err := x509.ParsePKCS1PublicKey(der); err == nil {
		return key, nil
	}
	key, err := x509.ParsePKIXPublicKey(der)
	if err != nil {
		return nil, fmt.Errorf("公钥解析失败（仅支持 RSA 公钥）: %w", err)
	}
	rsaKey, ok := key.(*rsa.PublicKey)
	if !ok {
		return nil, fmt.Errorf("公钥解析失败（仅支持 RSA 公钥）")
	}
	return rsaKey, nil
}

// GenerateNonce 生成随机字符串（每次请求唯一）
func GenerateNonce() string {
	b := make([]byte, 12)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}
