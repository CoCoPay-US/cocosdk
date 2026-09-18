'use strict'

const fs = require('fs')
const {CocopayClient, verifyOrderNotify, parseOrderNotify} = require('../index')

async function main() {
  // 1. 创建客户端（baseUrl 填写平台提供的 API 网关地址；也可不传，从环境变量 COCOPAY_BASE_URL 读取）
  const client = new CocopayClient({
    baseUrl: 'https://api.example.com', // TODO: 替换为平台提供的 API 网关地址
    mchNo: '100000',
    privateKey: fs.readFileSync('merchant_private_key.pem', 'utf8'),
  })

  // 2. 创建代收订单
  const collResp = await client.createCollectionOrder({
    mch_order_no: 'ORD' + Date.now(),
    currency: 'USD',
    amount: '100.00',
    payment_method: 'cashapp',
    email: 'john@example.com',
    client_ip: '203.0.113.10',
    mch_user_id: 'U10001',
    notify_url: 'https://merchant.example.com/notify',
  })
  console.log('代收订单创建成功:', collResp.order_no, collResp.pay_url)

  // 3. 创建代付订单
  const payResp = await client.createPayOutOrder({
    mch_order_no: 'PO' + Date.now(),
    currency: 'USD',
    amount: '50.00',
    payment_method: 'cash_app',
    account: '$johndoe',
    account_name: 'John Doe',
    client_ip: '203.0.113.10',
    mch_user_id: 'U10001',
  })
  console.log('代付订单创建成功:', payResp.order_no, payResp.status)

  // 4. 查询余额
  const {balances} = await client.queryBalance()
  for (const b of balances) {
    console.log(`余额: ${b.currency} 可用=${b.availableBalance} 未结算=${b.unsettledBalance} 冻结=${b.frozenBalance}`)
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})

// 异步通知处理示例（Express）
//
// const express = require('express')
// const app = express()
// app.post('/notify', express.raw({type: 'application/json'}), (req, res) => {
//   const ok = verifyOrderNotify(platformPublicKey, {
//     mchNo: req.get('x-mch-no'),
//     timestamp: req.get('x-timestamp'),
//     nonce: req.get('x-nonce'),
//     signature: req.get('x-signature'),
//   }, req.body)
//   if (!ok) return res.status(400).end()
//   const notify = parseOrderNotify(req.body)
//   // TODO: 按 order_no + state 幂等处理
//   res.status(200).type('text').send('SUCCESS')
// })
