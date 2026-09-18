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
	"testing"
)

func TestSignAndVerifyRoundTrip(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	content := BuildSignContent("100000", "1704067200", "abc123", []byte(`{"mch_order_no":"ORD001"}`))

	// 请求签名是 RSA-PSS：用公钥验签回环
	sig, err := SignRSAPSS(key, content)
	if err != nil {
		t.Fatal(err)
	}
	sigBytes, _ := base64.StdEncoding.DecodeString(sig)
	digest := sha256.Sum256([]byte(content))
	if err := rsa.VerifyPSS(&key.PublicKey, crypto.SHA256, digest[:], sigBytes, nil); err != nil {
		t.Fatalf("request sign verify failed: %v", err)
	}
}

func TestNotifySignRoundTrip(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	body := []byte(`{"order_no":"1010001","state":"success"}`)
	content := BuildSignContent("100000", "1704067200", "nonce123", body)
	digest := sha256.Sum256([]byte(content))

	// PSS 签名通知：验签通过
	pssSig, err := rsa.SignPSS(rand.Reader, key, crypto.SHA256, digest[:], &rsa.PSSOptions{SaltLength: 32})
	if err != nil {
		t.Fatal(err)
	}
	pssHeader := fmt.Sprintf(SignHeaderFormat, base64.StdEncoding.EncodeToString(pssSig))
	if err := VerifyOrderNotify(&key.PublicKey, NotifyHeaders{
		MchNo: "100000", Timestamp: "1704067200", Nonce: "nonce123", Signature: pssHeader,
	}, body); err != nil {
		t.Fatalf("PSS notify verify failed: %v", err)
	}

	// 非 PSS 签名的通知：必须验签失败
	pkcs1Sig, err := rsa.SignPKCS1v15(rand.Reader, key, crypto.SHA256, digest[:])
	if err != nil {
		t.Fatal(err)
	}
	badHeader := "algorithm=SHA256withRSA,version=v2,signature=" + base64.StdEncoding.EncodeToString(pkcs1Sig)
	if err := VerifyOrderNotify(&key.PublicKey, NotifyHeaders{
		MchNo: "100000", Timestamp: "1704067200", Nonce: "nonce123", Signature: badHeader,
	}, body); err == nil {
		t.Fatal("non-PSS signature should fail verification")
	}

	// 篡改报文必须验签失败
	if err := VerifyOrderNotify(&key.PublicKey, NotifyHeaders{
		MchNo: "100000", Timestamp: "1704067200", Nonce: "nonce123", Signature: pssHeader,
	}, append(body, 'x')); err == nil {
		t.Fatal("tampered body should fail verification")
	}
}

func TestKeyParsing(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}

	// PKCS#1 私钥 PEM
	pkcs1PEM := string(pem.EncodeToMemory(&pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(key)}))
	if _, err := ParsePrivateKeyPEM([]byte(pkcs1PEM)); err != nil {
		t.Fatalf("PKCS1 private key parse failed: %v", err)
	}
	// PKCS#8 私钥 PEM
	der8, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	pkcs8PEM := string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der8}))
	if _, err := ParsePrivateKeyPEM([]byte(pkcs8PEM)); err != nil {
		t.Fatalf("PKCS8 private key parse failed: %v", err)
	}
	// PKIX 公钥 PEM
	pubDER, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	pubPEM := string(pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: pubDER}))
	if _, err := ParsePublicKeyPEM([]byte(pubPEM)); err != nil {
		t.Fatalf("PKIX public key parse failed: %v", err)
	}
	// 纯 Base64 DER 公钥（无 PEM 头尾，平台运营可能以此形式下发）
	b64 := base64.StdEncoding.EncodeToString(pubDER)
	if _, err := ParsePublicKeyPEM([]byte(b64)); err != nil {
		t.Fatalf("base64 DER public key parse failed: %v", err)
	}
}

func TestParseSignatureHeaderInvalid(t *testing.T) {
	if _, err := ParseSignatureHeader("algorithm=SHA256withRSA,version=v2"); err == nil {
		t.Fatal("should fail without signature field")
	}
}

func TestBuildSignContent(t *testing.T) {
	got := BuildSignContent("100000", "1704067200", "a1b2", []byte(`{"a":1}`))
	want := `100000,1704067200,a1b2,{"a":1}`
	if got != want {
		t.Fatalf("got %q, want %q", got, want)
	}
}
