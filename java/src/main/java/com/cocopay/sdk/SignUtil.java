package com.cocopay.sdk;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 签名工具（商户请求/异步通知统一）：
 * 待签名串 = {mch_no},{timestamp},{nonce},{body}
 * 算法 RSA-PSS + SHA-256（盐长 32），签名 Base64 后放入 x-signature 头
 */
public final class SignUtil {

    public static final String SIGN_HEADER_PREFIX = "algorithm=SHA256withRSA/PSS,version=v2,signature=";

    private static final PSSParameterSpec PSS_SPEC = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, PSSParameterSpec.TRAILER_FIELD_BC);

    private SignUtil() {}

    /** 构造待签名字符串 */
    public static String buildSignContent(String mchNo, String timestamp, String nonce, String body) {
        return mchNo + "," + timestamp + "," + nonce + "," + body;
    }

    /** 请求签名：RSA-PSS + SHA-256，返回 Base64 签名 */
    public static String signPSS(PrivateKey privateKey, String content) {
        try {
            Signature signer = Signature.getInstance("RSASSA-PSS");
            signer.setParameter(PSS_SPEC);
            signer.initSign(privateKey, new SecureRandom());
            signer.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("cocopay: 签名失败: " + e.getMessage(), e);
        }
    }

    /** 异步通知验签：RSA-PSS + SHA-256 */
    public static boolean verifyPSS(PublicKey publicKey, String content, String signatureBase64) {
        try {
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(PSS_SPEC);
            verifier.initVerify(publicKey);
            verifier.update(content.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }

    /** 生成 x-signature 头 */
    public static String signHeader(String signature) {
        return SIGN_HEADER_PREFIX + signature;
    }

    /** 解析 x-signature 头，返回 signature 部分 */
    public static String parseSignatureHeader(String header) {
        if (header == null || header.isEmpty()) {
            throw new IllegalArgumentException("x-signature 头为空");
        }
        for (String part : header.split(",")) {
            int idx = part.indexOf('=');
            if (idx > 0 && part.substring(0, idx).trim().equals("signature")) {
                return part.substring(idx + 1).trim();
            }
        }
        throw new IllegalArgumentException("x-signature 头缺少 signature 字段");
    }

    /** 生成随机字符串（每次请求唯一） */
    public static String generateNonce() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** 解析 PEM 格式 RSA 私钥（支持 PKCS#8 "PRIVATE KEY" 与 PKCS#1 "RSA PRIVATE KEY"，无 PEM 头尾的纯 Base64 DER 亦可） */
    public static PrivateKey parsePrivateKey(String pem) {
        byte[] der = pemToDer(pem);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception pkcs8Err) {
            try {
                return parsePkcs1PrivateKey(der);
            } catch (Exception pkcs1Err) {
                throw new IllegalArgumentException("cocopay: 私钥解析失败（支持 PKCS#8 / PKCS#1 格式 RSA 私钥）: " + pkcs1Err.getMessage(), pkcs1Err);
            }
        }
    }

    /** 解析 PEM 格式 RSA 公钥（支持 X.509 "PUBLIC KEY" 与 PKCS#1 "RSA PUBLIC KEY"，无 PEM 头尾的纯 Base64 DER 亦可） */
    public static PublicKey parsePublicKey(String pem) {
        byte[] der = pemToDer(pem);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception x509Err) {
            try {
                return parsePkcs1PublicKey(der);
            } catch (Exception pkcs1Err) {
                throw new IllegalArgumentException("cocopay: 公钥解析失败（支持 X.509 / PKCS#1 格式 RSA 公钥）: " + pkcs1Err.getMessage(), pkcs1Err);
            }
        }
    }

    /** 解析 PKCS#1 RSA 私钥 DER：Sequence{version, n, e, d, ...}，取 n/d 构造密钥 */
    private static PrivateKey parsePkcs1PrivateKey(byte[] der) throws Exception {
        DerReader seq = new DerReader(new DerReader(der).readTlv(0x30));
        seq.readTlv(0x02); // version
        BigInteger n = new BigInteger(1, seq.readTlv(0x02));
        seq.readTlv(0x02); // e
        BigInteger d = new BigInteger(1, seq.readTlv(0x02));
        return KeyFactory.getInstance("RSA").generatePrivate(new RSAPrivateKeySpec(n, d));
    }

    /** 解析 PKCS#1 RSA 公钥 DER：Sequence{n, e} */
    private static PublicKey parsePkcs1PublicKey(byte[] der) throws Exception {
        DerReader seq = new DerReader(new DerReader(der).readTlv(0x30));
        BigInteger n = new BigInteger(1, seq.readTlv(0x02));
        BigInteger e = new BigInteger(1, seq.readTlv(0x02));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    /** 极简 DER 读取器（仅支持 PKCS#1 密钥解析所需的 SEQUENCE/INTEGER TLV） */
    private static final class DerReader {
        private final byte[] data;
        private int pos;

        DerReader(byte[] data) {
            this.data = data;
        }

        /** 读取一个 TLV 结构，返回内容字节 */
        byte[] readTlv(int tag) {
            if (pos >= data.length || (data[pos++] & 0xFF) != tag) {
                throw new IllegalArgumentException("DER 结构不符合预期（期望 tag=0x" + Integer.toHexString(tag) + "）");
            }
            if (pos >= data.length) {
                throw new IllegalArgumentException("DER 长度字段缺失");
            }
            int len = data[pos++] & 0xFF;
            if ((len & 0x80) != 0) {
                int numBytes = len & 0x7F;
                len = 0;
                for (int i = 0; i < numBytes; i++) {
                    if (pos >= data.length) {
                        throw new IllegalArgumentException("DER 长度字段越界");
                    }
                    len = (len << 8) | (data[pos++] & 0xFF);
                }
            }
            if (pos + len > data.length) {
                throw new IllegalArgumentException("DER 内容长度越界");
            }
            byte[] content = Arrays.copyOfRange(data, pos, pos + len);
            pos += len;
            return content;
        }
    }

    private static byte[] pemToDer(String pem) {
        try {
            String base64 = pem
                    .replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            throw new IllegalArgumentException("cocopay: PEM 解析失败: " + e.getMessage(), e);
        }
    }
}
