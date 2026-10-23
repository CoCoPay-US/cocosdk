'use strict'

const http = require('http')
const https = require('https')
const {buildSignContent, signPSS, signHeader, generateNonce} = require('./sign')
const {ApiError} = require('./errors')
const endpoints = require('./endpoints')

/**
 * CoCoPay 商户 SDK 客户端
 *
 * const client = new CocopayClient({
 *   // baseUrl 可不传，默认从环境变量 COCOPAY_BASE_URL 读取
 *   mchNo: '100000', // 商户号（string）
 *   privateKey: fs.readFileSync('merchant_private_key.pem', 'utf8'), // PEM 或纯 Base64（按 PKCS#8 包装）
 *   timeout: 15000, // 可选，默认 15000ms
 * })
 */
class CocopayClient {
  constructor({baseUrl, mchNo, privateKey, timeout = 15000}) {
    baseUrl = baseUrl || process.env.COCOPAY_BASE_URL
    if (!baseUrl) throw new Error('cocopay: baseUrl 未配置（请显式配置或设置环境变量 COCOPAY_BASE_URL）')
    if (!mchNo) throw new Error('cocopay: mchNo 不能为空')
    if (!privateKey) throw new Error('cocopay: privateKey 不能为空')
    this.baseUrl = baseUrl.replace(/\/+$/, '')
    this.mchNo = String(mchNo)
    this.privateKey = privateKey
    this.timeout = timeout
  }

  /** 发起签名 POST 请求并解出 data */
  async _do(path, reqBody) {
    const body = JSON.stringify(reqBody)
    const timestamp = String(Math.floor(Date.now() / 1000))
    const nonce = generateNonce()
    const signature = signPSS(this.privateKey, buildSignContent(this.mchNo, timestamp, nonce, body))

    const url = new URL(this.baseUrl + path)
    const lib = url.protocol === 'http:' ? http : https
    const options = {
      method: 'POST',
      hostname: url.hostname,
      port: url.port || (url.protocol === 'http:' ? 80 : 443),
      path: url.pathname + url.search,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        'x-mch-no': this.mchNo,
        'x-timestamp': timestamp,
        'x-nonce': nonce,
        'x-signature': signHeader(signature),
      },
      timeout: this.timeout,
    }

    const respBytes = await new Promise((resolve, reject) => {
      const req = lib.request(options, (res) => {
        const chunks = []
        res.on('data', (c) => chunks.push(c))
        res.on('end', () => resolve({statusCode: res.statusCode, body: Buffer.concat(chunks)}))
        res.on('error', reject)
      })
      req.on('timeout', () => req.destroy(new Error('cocopay: 请求超时')))
      req.on('error', (e) => reject(new Error(`cocopay: 网络请求失败: ${e.message}`)))
      req.write(body)
      req.end()
    })

    let env
    try {
      env = JSON.parse(respBytes.body.toString('utf8'))
    } catch {
      throw new Error(`cocopay: 响应解析失败（HTTP ${respBytes.statusCode}）`)
    }
    if (respBytes.statusCode !== 200 || env.code !== 200) {
      throw new ApiError(env)
    }
    return env.data
  }

  /**
   * 创建代收订单
   * @param {Object} req {mch_order_no, currency, amount, payment_method, email, client_ip, mch_user_id, telephone?, notify_url?, extra?, card_valid?, routing_code?, goods_infos?}
   *        goods_infos: 物品信息（可选，最多50条）Array<{sku_id?, sku_name, price, num, sku_desc, sku_url}>
   * @returns {Promise<{order_no, mch_order_no, status, fail_reason, pay_url, extra}>}
   */
  createCollectionOrder(req) {
    return this._do(endpoints.PATH_COLLECTION_CREATE, req)
  }

  /**
   * 查询代收订单（order_no 与 mch_order_no 至少填一个）
   * @returns {Promise<{mch_no: string, order_no: string, mch_order_no: string, currency: string, order_amount: string, transaction_amount: string, state: string, fail_reason: string, complete_time: number, pay_url: string, extra: string}>}
   * 金额字段（order_amount / transaction_amount）为字符串，保留 2 位小数
   */
  queryCollectionOrder(req) {
    return this._do(endpoints.PATH_COLLECTION_QUERY, req)
  }

  /**
   * 创建代付订单
   * 下单后可能需要两级审核（商户审核 status=2 / 管理员审核 status=3），全部通过后自动出款，以异步通知为准。
   * @param {Object} req {mch_order_no, currency, amount, payment_method, account, account_name, client_ip, mch_user_id, account_phone?, email?, notify_url?, extra?, routing_code?, card_valid?}
   * @returns {Promise<{order_no, mch_order_no, status, fail_reason, extra}>}
   */
  createPayOutOrder(req) {
    return this._do(endpoints.PATH_PAYOUT_CREATE, req)
  }

  /**
   * 查询代付订单（order_no 与 mch_order_no 至少填一个）
   * @returns {Promise<{mch_no: string, order_no: string, mch_order_no: string, currency: string, order_amount: string, transaction_amount: string, state: string, complete_time: number, fail_reason: string, extra: string}>}
   * 金额字段（order_amount / transaction_amount）为字符串，保留 2 位小数
   */
  queryPayOutOrder(req) {
    return this._do(endpoints.PATH_PAYOUT_QUERY, req)
  }

  /**
   * 查询商户余额（余额各金额字段均为字符串，保留 2 位小数）
   * @returns {Promise<{balances: Array<{currency: string, availableBalance: string, unsettledBalance: string, frozenBalance: string, withdrawalPendingBalance: string, payoutPendingBalance: string}>}>}
   */
  queryBalance() {
    return this._do(endpoints.PATH_BALANCE_QUERY, {mch_no: this.mchNo})
  }
}

module.exports = {CocopayClient}
