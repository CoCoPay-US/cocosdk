package com.cocopay.sdk;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SdkTest {

    private static KeyPair genKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    /** 用非 PSS 算法签名（构造验签失败的反向用例） */
    private static String signNonPss(PrivateKey privateKey, String content) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    // ---- 极简 DER 编码（测试用，构造 PKCS#1 密钥） ----

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] a : arrays) {
            out.write(a, 0, a.length);
        }
        return out.toByteArray();
    }

    private static byte[] derLen(int len) {
        if (len < 0x80) {
            return new byte[]{(byte) len};
        }
        if (len < 0x100) {
            return new byte[]{(byte) 0x81, (byte) len};
        }
        return new byte[]{(byte) 0x82, (byte) (len >> 8), (byte) len};
    }

    private static byte[] derInt(BigInteger v) {
        byte[] b = v.toByteArray();
        return concat(new byte[]{0x02}, derLen(b.length), b);
    }

    private static byte[] derSeq(byte[]... items) {
        byte[] body = concat(items);
        return concat(new byte[]{0x30}, derLen(body.length), body);
    }

    @Test
    void signAndVerifyRoundTrip() throws Exception {
        KeyPair kp = genKeyPair();
        String content = SignUtil.buildSignContent("100000", "1704067200", "abc123", "{\"mch_order_no\":\"ORD001\"}");
        String sig = SignUtil.signPSS(kp.getPrivate(), content);
        assertTrue(SignUtil.verifyPSS(kp.getPublic(), content, sig));
        assertFalse(SignUtil.verifyPSS(kp.getPublic(), content + "x", sig));
    }

    @Test
    void notifyVerifyRoundTrip() throws Exception {
        KeyPair kp = genKeyPair();
        String body = "{\"order_no\":\"1010001\",\"state\":\"success\"}";
        String content = SignUtil.buildSignContent("100000", "1704067200", "nonce123", body);
        String header = SignUtil.signHeader(SignUtil.signPSS(kp.getPrivate(), content));

        assertTrue(NotifyUtil.verifyOrderNotify(kp.getPublic(),
                new NotifyUtil.NotifyHeaders("100000", "1704067200", "nonce123", header), body));
        // 篡改报文 → 验签失败
        assertFalse(NotifyUtil.verifyOrderNotify(kp.getPublic(),
                new NotifyUtil.NotifyHeaders("100000", "1704067200", "nonce123", header), body + "x"));
    }

    @Test
    void notifyVerifyRejectsNonPssSignature() throws Exception {
        // 非 PSS 算法生成的签名，验签必须失败
        KeyPair kp = genKeyPair();
        String body = "{\"order_no\":\"1010002\",\"state\":\"success\"}";
        String content = SignUtil.buildSignContent("100000", "1704067200", "nonce456", body);
        String header = SignUtil.signHeader(signNonPss(kp.getPrivate(), content));

        assertFalse(NotifyUtil.verifyOrderNotify(kp.getPublic(),
                new NotifyUtil.NotifyHeaders("100000", "1704067200", "nonce456", header), body));
    }

    @Test
    void keyParsing() throws Exception {
        KeyPair kp = genKeyPair();

        // PKCS#8 私钥 PEM
        String pkcs8Pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        PrivateKey parsedPrivate = SignUtil.parsePrivateKey(pkcs8Pem);
        assertArrayEquals(kp.getPrivate().getEncoded(), parsedPrivate.getEncoded());

        // 纯 Base64（无 PEM 头尾）私钥
        PrivateKey parsedPrivate2 = SignUtil.parsePrivateKey(Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
        assertArrayEquals(kp.getPrivate().getEncoded(), parsedPrivate2.getEncoded());

        // X.509 公钥 PEM
        String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        PublicKey parsedPublic = SignUtil.parsePublicKey(pubPem);
        assertArrayEquals(kp.getPublic().getEncoded(), parsedPublic.getEncoded());
    }

    @Test
    void pkcs1KeyParsing() throws Exception {
        KeyPair kp = genKeyPair();

        // PKCS#1 私钥 PEM（BEGIN RSA PRIVATE KEY）
        RSAPrivateCrtKey priv = (RSAPrivateCrtKey) kp.getPrivate();
        byte[] pkcs1PrivDer = derSeq(
                derInt(BigInteger.ZERO),
                derInt(priv.getModulus()),
                derInt(priv.getPublicExponent()),
                derInt(priv.getPrivateExponent()),
                derInt(priv.getPrimeP()),
                derInt(priv.getPrimeQ()),
                derInt(priv.getPrimeExponentP()),
                derInt(priv.getPrimeExponentQ()),
                derInt(priv.getCrtCoefficient()));
        String pkcs1Pem = "-----BEGIN RSA PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pkcs1PrivDer)
                + "\n-----END RSA PRIVATE KEY-----";
        PrivateKey parsedPrivate = SignUtil.parsePrivateKey(pkcs1Pem);
        // 用解析出的私钥签名，原公钥验签通过
        String content = SignUtil.buildSignContent("100000", "1704067200", "n1", "{}");
        assertTrue(SignUtil.verifyPSS(kp.getPublic(), content, SignUtil.signPSS(parsedPrivate, content)));
        // 纯 Base64（无 PEM 头尾）PKCS#1 私钥
        PrivateKey parsedPrivate2 = SignUtil.parsePrivateKey(Base64.getEncoder().encodeToString(pkcs1PrivDer));
        assertTrue(SignUtil.verifyPSS(kp.getPublic(), content, SignUtil.signPSS(parsedPrivate2, content)));

        // PKCS#1 公钥 PEM（BEGIN RSA PUBLIC KEY）
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        byte[] pkcs1PubDer = derSeq(derInt(pub.getModulus()), derInt(pub.getPublicExponent()));
        String pkcs1PubPem = "-----BEGIN RSA PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pkcs1PubDer)
                + "\n-----END RSA PUBLIC KEY-----";
        PublicKey parsedPublic = SignUtil.parsePublicKey(pkcs1PubPem);
        assertArrayEquals(kp.getPublic().getEncoded(), parsedPublic.getEncoded());
    }

    @Test
    void parseOrderNotifyStringAmounts() {
        // 金额字段为字符串、mch_no 为 JSON 数字（Jackson coercion 进 String）
        NotifyUtil.OrderNotify n = NotifyUtil.parseOrderNotify(
                "{\"mch_no\":100000,\"order_no\":\"1010005\",\"state\":\"refund\","
                        + "\"order_amount\":\"100.00\",\"transaction_amount\":\"99.50\",\"refund_amount\":\"10.00\"}");
        assertEquals("100000", n.mchNo);
        assertEquals("100.00", n.orderAmount);
        assertEquals("99.50", n.transactionAmount);
        assertEquals("10.00", n.refundAmount);
    }

    @Test
    void parseSignatureHeaderInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> SignUtil.parseSignatureHeader("algorithm=SHA256withRSA,version=v2"));
    }

    @Test
    void buildSignContentFormat() {
        assertEquals("100000,1704067200,a1b2,{\"a\":1}",
                SignUtil.buildSignContent("100000", "1704067200", "a1b2", "{\"a\":1}"));
    }
}
