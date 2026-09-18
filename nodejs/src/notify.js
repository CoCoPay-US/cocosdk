'use strict'

const {buildSignContent, verifyPSS, parseSignatureHeader, normalizePublicKey} = require('./sign')

/**
 * 校验异步通知签名（RSA-PSS + SHA-256，盐长固定 32，公钥由平台运营提供）
 *
 * @param {string|crypto.KeyObject} platformPublicKey 平台 RSA 公钥（PEM 或纯 Base64，纯 Base64 按 SPKI 包装）
 * @param {Object} headers {mchNo: string, timestamp: string, nonce: string, signature: string}（对应 x-mch-no / x-timestamp / x-nonce / x-signature 头）
 * @param {string|Buffer} body 原始通知报文
 * @returns {boolean} 验签是否通过
 */
function verifyOrderNotify(platformPublicKey, headers, body) {
  const signature = parseSignatureHeader(headers.signature)
  const content = buildSignContent(headers.mchNo, headers.timestamp, headers.nonce, body.toString('utf8'))
  return verifyPSS(normalizePublicKey(platformPublicKey), content, signature)
}

/**
 * 解析通知报文
 * @returns {{mch_no: string, order_no: string, mch_order_no: string, currency: string, order_amount: string, transaction_amount: string, state: string, complete_time: number, fail_reason: string, extra: string, account?: string, account_name?: string, refund_amount?: string}}
 * 金额字段（order_amount / transaction_amount / refund_amount）为字符串，保留 2 位小数
 */
function parseOrderNotify(body) {
  return JSON.parse(body.toString('utf8'))
}

// 异步通知的订单状态（state 字符串）
const NotifyState = {
  SUCCESS: 'success',
  FAILED: 'failed',
  REJECTED: 'rejected',
  REFUND: 'refund',
  PARTIAL_REFUND: 'partial_refund',
  REVERSAL: 'reversal',
  CHARGEBACK: 'chargeback',
}

module.exports = {verifyOrderNotify, parseOrderNotify, NotifyState}
