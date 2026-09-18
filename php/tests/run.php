<?php

declare(strict_types=1);

/**
 * 签名回环自测（无 PHPUnit 依赖）：php tests/run.php
 * 需要 ext-openssl；PSS 填充为纯 PHP 手工实现（EMSA-PSS），openssl 仅做模幂运算
 */

require __DIR__ . '/../src/SignUtil.php';
require __DIR__ . '/../src/NotifyUtil.php';

use Cocopay\Sdk\SignUtil;
use Cocopay\Sdk\NotifyUtil;

function assertTrue(bool $cond, string $msg): void
{
    if (!$cond) {
        fwrite(STDERR, "FAIL: {$msg}\n");
        exit(1);
    }
    echo "PASS: {$msg}\n";
}

// 生成 RSA 密钥对
$res = openssl_pkey_new(['private_key_type' => OPENSSL_KEYTYPE_RSA, 'private_key_bits' => 2048]);
openssl_pkey_export($res, $privateKeyPem);
$publicKeyPem = openssl_pkey_get_details($res)['key'];

// 签名/验签回环
$content = SignUtil::buildSignContent('100000', '1704067200', 'abc123', '{"mch_order_no":"ORD001"}');
$sig = SignUtil::signPSS($privateKeyPem, $content);
assertTrue(SignUtil::verifyPSS($publicKeyPem, $content, $sig), 'PSS 签名验签回环');
assertTrue(!SignUtil::verifyPSS($publicKeyPem, $content . 'x', $sig), '篡改内容验签失败');

// 非 2048 位密钥回环（EMSA-PSS 按密钥位长动态计算 emLen）
$res3072 = openssl_pkey_new(['private_key_type' => OPENSSL_KEYTYPE_RSA, 'private_key_bits' => 3072]);
openssl_pkey_export($res3072, $privateKey3072Pem);
$publicKey3072Pem = openssl_pkey_get_details($res3072)['key'];
assertTrue(
    SignUtil::verifyPSS($publicKey3072Pem, $content, SignUtil::signPSS($privateKey3072Pem, $content)),
    '3072 位密钥 PSS 签名验签回环'
);

// 通知验签（含头解析）
$body = '{"order_no":"1010001","state":"success"}';
$content = SignUtil::buildSignContent('100000', '1704067200', 'nonce123', $body);
$header = SignUtil::signHeader(SignUtil::signPSS($privateKeyPem, $content));
assertTrue(
    NotifyUtil::verifyOrderNotify($publicKeyPem, ['mch_no' => '100000', 'timestamp' => '1704067200', 'nonce' => 'nonce123', 'signature' => $header], $body),
    '通知验签通过'
);
assertTrue(
    !NotifyUtil::verifyOrderNotify($publicKeyPem, ['mch_no' => '100000', 'timestamp' => '1704067200', 'nonce' => 'nonce123', 'signature' => $header], $body . 'x'),
    '篡改通知验签失败'
);

// 非 PSS 签名（openssl_sign 默认填充）通知验签必须失败
openssl_sign($content, $nonPssSigRaw, $privateKeyPem, OPENSSL_ALGO_SHA256);
$nonPssHeader = 'algorithm=SHA256withRSA,version=v2,signature=' . base64_encode($nonPssSigRaw);
assertTrue(
    !NotifyUtil::verifyOrderNotify($publicKeyPem, ['mch_no' => '100000', 'timestamp' => '1704067200', 'nonce' => 'nonce123', 'signature' => $nonPssHeader], $body),
    '非 PSS 签名通知验签失败'
);

// 头解析
assertTrue(SignUtil::parseSignatureHeader('algorithm=SHA256withRSA,version=v2,signature=Ab1Cd2==') === 'Ab1Cd2==', 'x-signature 头解析');

// 纯 Base64 公钥（无 PEM 头尾）加载（注意用与当前 $content 匹配的签名 $header）
$pubDer = base64_decode(preg_replace('/-----[^-]+-----|\s/', '', $publicKeyPem), true);
assertTrue(SignUtil::verifyPSS(base64_encode($pubDer), $content, SignUtil::parseSignatureHeader($header)), '纯 Base64 公钥验签');

// 待签名串格式
assertTrue(SignUtil::buildSignContent('100000', '1704067200', 'a1b2', '{"a":1}') === '100000,1704067200,a1b2,{"a":1}', '待签名串格式');

echo "\n全部测试通过\n";
