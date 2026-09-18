<?php

declare(strict_types=1);

namespace Cocopay\Sdk;

/**
 * 签名工具（商户请求/异步通知统一）：
 * 待签名串 = {mch_no},{timestamp},{nonce},{body}
 * 算法 RSA-PSS + SHA-256（盐长 32），签名 Base64 后放入 x-signature 头
 */
final class SignUtil
{
    public const SIGN_HEADER_PREFIX = 'algorithm=SHA256withRSA/PSS,version=v2,signature=';

    /** 构造待签名字符串 */
    public static function buildSignContent(string $mchNo, string $timestamp, string $nonce, string $body): string
    {
        return $mchNo . ',' . $timestamp . ',' . $nonce . ',' . $body;
    }

    /** 请求签名：RSA-PSS + SHA-256，返回 Base64 签名 */
    public static function signPSS(string $privateKeyPem, string $content): string
    {
        $key = self::loadKey($privateKeyPem, true);
        $em = self::emsaPssEncode($content, self::keyEmLen($key));
        if (!openssl_private_encrypt($em, $signature, $key, OPENSSL_NO_PADDING)) {
            throw new \RuntimeException('cocopay: 签名失败: ' . openssl_error_string());
        }
        return base64_encode($signature);
    }

    /** 异步通知验签：RSA-PSS + SHA-256 */
    public static function verifyPSS(string $publicKeyPem, string $content, string $signatureBase64): bool
    {
        $key = self::loadKey($publicKeyPem, false);
        $emLen = self::keyEmLen($key);
        $signature = base64_decode($signatureBase64, true);
        if ($signature === false) {
            return false;
        }
        $em = '';
        if (!@openssl_public_decrypt($signature, $em, $key, OPENSSL_NO_PADDING)) {
            return false;
        }
        return self::emsaPssVerify($content, $em, $emLen);
    }

    /** 从密钥位长计算 EM 长度：emBits = bits-1，emLen = ceil(emBits/8)（按密钥位长动态计算） */
    private static function keyEmLen($key): int
    {
        $details = openssl_pkey_get_details($key);
        if ($details === false || !isset($details['bits'])) {
            throw new \RuntimeException('cocopay: 密钥信息读取失败');
        }
        return intdiv($details['bits'] + 7, 8);
    }

    // ============================== EMSA-PSS（RFC 8017）纯 PHP 实现 ==============================
    // PHP 内置 openssl 扩展不支持 PSS 填充，这里手工实现编码/验证，openssl 仅做模幂运算。
    // 盐长 32（= SHA-256 摘要长度），与平台验签端一致。

    private const PSS_HASH_LEN = 32; // SHA-256 摘要长度（盐长同）

    /** MGF1（SHA-256） */
    private static function mgf1(string $seed, int $length): string
    {
        $out = '';
        for ($i = 0; $out === '' || strlen($out) < $length; $i++) {
            $out .= hash('sha256', $seed . pack('N', $i), true);
        }
        return substr($out, 0, $length);
    }

    /** EMSA-PSS-ENCODE（emBits = 密钥位长-1） */
    private static function emsaPssEncode(string $content, int $emLen): string
    {
        $salt = random_bytes(self::PSS_HASH_LEN);
        $h = hash('sha256', $content, true);
        $hPrime = hash('sha256', str_repeat("\0", 8) . $h . $salt, true);
        $ps = str_repeat("\0", $emLen - self::PSS_HASH_LEN * 2 - 2);
        $db = $ps . "\x01" . $salt;
        $maskedDB = $db ^ self::mgf1($hPrime, $emLen - self::PSS_HASH_LEN - 1);
        $maskedDB[0] = $maskedDB[0] & "\x7f"; // 清除超出 emBits 的高位
        return $maskedDB . $hPrime . "\xbc";
    }

    /** EMSA-PSS-VERIFY：从 EM 还原并比对（盐长自动识别） */
    private static function emsaPssVerify(string $content, string $em, int $emLen): bool
    {
        if (strlen($em) !== $emLen || substr($em, -1) !== "\xbc") {
            return false;
        }
        $maskedDB = substr($em, 0, $emLen - self::PSS_HASH_LEN - 1);
        $hPrime = substr($em, -self::PSS_HASH_LEN - 1, self::PSS_HASH_LEN);
        if ((ord($maskedDB[0]) & 0x80) !== 0) {
            return false;
        }
        $db = $maskedDB ^ self::mgf1($hPrime, $emLen - self::PSS_HASH_LEN - 1);
        $db[0] = $db[0] & "\x7f";
        // 扫描 PS（全零）后的 0x01 边界，边界之后即为盐（长度自动识别）
        $dbLen = strlen($db);
        $pos = 0;
        while ($pos < $dbLen && $db[$pos] === "\0") {
            $pos++;
        }
        if ($pos >= $dbLen || $db[$pos] !== "\x01") {
            return false;
        }
        $salt = substr($db, $pos + 1);
        $h = hash('sha256', $content, true);
        $hPrime2 = hash('sha256', str_repeat("\0", 8) . $h . $salt, true);
        return hash_equals($hPrime, $hPrime2);
    }

    /** 生成 x-signature 头 */
    public static function signHeader(string $signature): string
    {
        return self::SIGN_HEADER_PREFIX . $signature;
    }

    /** 解析 x-signature 头，返回 signature 部分 */
    public static function parseSignatureHeader(string $header): string
    {
        foreach (explode(',', $header) as $part) {
            $kv = explode('=', trim($part), 2);
            if (count($kv) === 2 && $kv[0] === 'signature') {
                return $kv[1];
            }
        }
        throw new \InvalidArgumentException('x-signature 头缺少 signature 字段');
    }

    /** 生成随机字符串（每次请求唯一） */
    public static function generateNonce(): string
    {
        return rtrim(strtr(base64_encode(random_bytes(12)), '+/', '-_'), '=');
    }

    /**
     * 加载 PEM 密钥（支持带 PEM 头尾或纯 Base64 DER）
     *
     * @return \OpenSSLAsymmetricKey|resource
     */
    private static function loadKey(string $pem, bool $isPrivate)
    {
        // 无 PEM 头尾时补全（openssl 需要 PEM 格式）
        if (strpos($pem, '-----BEGIN') === false) {
            $type = $isPrivate ? 'PRIVATE KEY' : 'PUBLIC KEY';
            $pem = "-----BEGIN {$type}-----\n"
                . trim(chunk_split(preg_replace('/\s/', '', $pem), 64, "\n"))
                . "\n-----END {$type}-----";
        }
        $key = $isPrivate ? openssl_pkey_get_private($pem) : openssl_pkey_get_public($pem);
        if ($key === false) {
            throw new \InvalidArgumentException('cocopay: 密钥解析失败（请确认 PEM 格式）');
        }
        return $key;
    }
}
