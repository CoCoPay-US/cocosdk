'use strict'

const {test} = require('node:test')
const assert = require('node:assert')
const crypto = require('crypto')
const {buildSignContent, signPSS, verifyPSS, parseSignatureHeader, signHeader} = require('../src/sign')
const {verifyOrderNotify, parseOrderNotify} = require('../src/notify')

function genKeyPair() {
  return crypto.generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: {type: 'spki', format: 'pem'},
    privateKeyEncoding: {type: 'pkcs8', format: 'pem'},
  })
}

/** 去掉 PEM 头尾与换行，得到纯 Base64 */
function stripPEM(pem) {
  return pem
    .split('\n')
    .filter((l) => !l.includes('-----BEGIN') && !l.includes('-----END'))
    .join('')
    .trim()
}

test('签名/验签回环（PSS）', () => {
  const {publicKey, privateKey} = genKeyPair()
  const content = buildSignContent('100000', '1704067200', 'abc123', '{"mch_order_no":"ORD001"}')
  const sig = signPSS(privateKey, content)
  assert.strictEqual(verifyPSS(publicKey, content, sig), true)
  assert.strictEqual(verifyPSS(publicKey, content + 'x', sig), false)
})

test('PSS 签名盐长固定 32（严格 saltLength=32 验签端可验通）', () => {
  const {publicKey, privateKey} = genKeyPair()
  const content = buildSignContent('100000', '1704067200', 'abc123', '{"mch_order_no":"ORD001"}')
  const sig = signPSS(privateKey, content)
  // 严格盐长验签：必须盐长恰好 32 才能通过
  const okStrict = crypto.verify(
    'sha256',
    Buffer.from(content, 'utf8'),
    {key: publicKey, padding: crypto.constants.RSA_PKCS1_PSS_PADDING, saltLength: 32},
    Buffer.from(sig, 'base64')
  )
  assert.strictEqual(okStrict, true)
  // 盐长 20 的严格验签端应失败
  const okWrongSalt = crypto.verify(
    'sha256',
    Buffer.from(content, 'utf8'),
    {key: publicKey, padding: crypto.constants.RSA_PKCS1_PSS_PADDING, saltLength: 20},
    Buffer.from(sig, 'base64')
  )
  assert.strictEqual(okWrongSalt, false)
})

test('签名头格式为 algorithm=SHA256withRSA/PSS', () => {
  const header = signHeader('Ab1Cd2==')
  assert.strictEqual(header, 'algorithm=SHA256withRSA/PSS,version=v2,signature=Ab1Cd2==')
})

test('通知验签（PSS，含头解析）', () => {
  const {publicKey, privateKey} = genKeyPair()
  const body = '{"order_no":"1010001","state":"success"}'
  const content = buildSignContent('100000', '1704067200', 'nonce123', body)
  const header = signHeader(signPSS(privateKey, content))
  assert.strictEqual(
    verifyOrderNotify(publicKey, {mchNo: '100000', timestamp: '1704067200', nonce: 'nonce123', signature: header}, body),
    true
  )
  assert.strictEqual(
    verifyOrderNotify(publicKey, {mchNo: '100000', timestamp: '1704067200', nonce: 'nonce123', signature: header}, body + 'x'),
    false
  )
})

test('通知验签失败（非 PSS 签名）', () => {
  const {publicKey, privateKey} = genKeyPair()
  const body = '{"order_no":"1010002","state":"success"}'
  const content = buildSignContent('100000', '1704067200', 'nonce456', body)
  // 用非 PSS padding 生成的签名必须验签失败
  const badSig = crypto
    .sign('sha256', Buffer.from(content, 'utf8'), {
      key: privateKey,
      padding: crypto.constants.RSA_PKCS1_PADDING,
    })
    .toString('base64')
  const header = signHeader(badSig)
  assert.strictEqual(
    verifyOrderNotify(publicKey, {mchNo: '100000', timestamp: '1704067200', nonce: 'nonce456', signature: header}, body),
    false
  )
})

test('通知验签（纯 Base64 公钥，自动按 SPKI 包装）', () => {
  const {publicKey, privateKey} = genKeyPair()
  const body = '{"order_no":"1010003","state":"success"}'
  const content = buildSignContent('100000', '1704067200', 'nonceB64', body)
  const header = signHeader(signPSS(privateKey, content))
  const bareBase64 = stripPEM(publicKey)
  assert.ok(!bareBase64.includes('-----BEGIN'))
  assert.strictEqual(
    verifyOrderNotify(bareBase64, {mchNo: '100000', timestamp: '1704067200', nonce: 'nonceB64', signature: header}, body),
    true
  )
})

test('x-signature 头解析', () => {
  assert.strictEqual(
    parseSignatureHeader('algorithm=SHA256withRSA/PSS,version=v2,signature=Ab1Cd2=='),
    'Ab1Cd2=='
  )
  assert.throws(() => parseSignatureHeader('algorithm=SHA256withRSA/PSS,version=v2'))
})

test('待签名串格式', () => {
  assert.strictEqual(
    buildSignContent('100000', '1704067200', 'a1b2', '{"a":1}'),
    '100000,1704067200,a1b2,{"a":1}'
  )
})

test('通知报文解析（金额为字符串）', () => {
  const notify = parseOrderNotify('{"order_no":"1010001","state":"success","order_amount":"100.50"}')
  assert.strictEqual(notify.order_no, '1010001')
  assert.strictEqual(notify.state, 'success')
  assert.strictEqual(notify.order_amount, '100.50')
})
