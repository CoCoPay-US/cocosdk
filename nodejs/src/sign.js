'use strict'

const crypto = require('crypto')

// 签名规则（与商户请求/异步通知统一）：待签名串 = {mch_no},{timestamp},{nonce},{body}
// 签名/验签统一 RSA-PSS + SHA-256（盐长固定 32），签名 Base64 后放入 x-signature 头

// x-signature 头前缀
const SIGN_HEADER_PREFIX = 'algorithm=SHA256withRSA/PSS,version=v2,signature='

// PSS 盐长固定 32（= 摘要长度），与平台验签端保持一致
const PSS_SALT_LENGTH = 32

/** 构造待签名字符串 */
function buildSignContent(mchNo, timestamp, nonce, body) {
  return `${mchNo},${timestamp},${nonce},${body}`
}

/** 请求签名：RSA-PSS + SHA-256（盐长固定 32），返回 Base64 签名 */
function signPSS(privateKey, content) {
  return crypto
    .sign('sha256', Buffer.from(content, 'utf8'), {
      key: normalizePrivateKey(privateKey),
      padding: crypto.constants.RSA_PKCS1_PSS_PADDING,
      saltLength: PSS_SALT_LENGTH,
    })
    .toString('base64')
}

/** 验签：RSA-PSS + SHA-256（盐长固定 32） */
function verifyPSS(publicKey, content, signatureBase64) {
  return crypto.verify(
    'sha256',
    Buffer.from(content, 'utf8'),
    {key: normalizePublicKey(publicKey), padding: crypto.constants.RSA_PKCS1_PSS_PADDING, saltLength: PSS_SALT_LENGTH},
    Buffer.from(signatureBase64, 'base64')
  )
}

/** 生成 x-signature 头 */
function signHeader(signature) {
  return SIGN_HEADER_PREFIX + signature
}

/** 解析 x-signature 头，返回 signature 部分 */
function parseSignatureHeader(header) {
  if (!header) throw new Error('x-signature 头为空')
  for (const part of header.split(',')) {
    const idx = part.indexOf('=')
    if (idx > 0 && part.slice(0, idx).trim() === 'signature') {
      return part.slice(idx + 1).trim()
    }
  }
  throw new Error('x-signature 头缺少 signature 字段')
}

/**
 * 规范化 RSA 公钥：支持 PEM 或纯 Base64
 * 纯 Base64（无 -----BEGIN 头尾）按 SPKI（X.509 SubjectPublicKeyInfo）DER 处理，
 * 包装为 -----BEGIN PUBLIC KEY----- PEM
 */
function normalizePublicKey(key) {
  if (typeof key !== 'string' || key.includes('-----BEGIN')) return key
  return wrapPEM(key, 'PUBLIC KEY')
}

/**
 * 规范化 RSA 私钥：支持 PEM 或纯 Base64
 * 纯 Base64（无 -----BEGIN 头尾）按 PKCS#8 DER 处理，包装为 -----BEGIN PRIVATE KEY----- PEM
 */
function normalizePrivateKey(key) {
  if (typeof key !== 'string' || key.includes('-----BEGIN')) return key
  return wrapPEM(key, 'PRIVATE KEY')
}

/** 将纯 Base64 DER 包装为 PEM（64 字符折行） */
function wrapPEM(base64, label) {
  const b64 = base64.replace(/\s+/g, '')
  const lines = []
  for (let i = 0; i < b64.length; i += 64) lines.push(b64.slice(i, i + 64))
  return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----\n`
}

/** 生成随机字符串（每次请求唯一） */
function generateNonce() {
  return crypto.randomBytes(12).toString('base64url')
}

module.exports = {
  SIGN_HEADER_PREFIX,
  buildSignContent,
  signPSS,
  verifyPSS,
  signHeader,
  parseSignatureHeader,
  normalizePublicKey,
  normalizePrivateKey,
  generateNonce,
}
