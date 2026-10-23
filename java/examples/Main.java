package com.cocopay.sdk;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws Exception {
        // 1. 加载商户 RSA 私钥（PKCS#8 / PKCS#1 PEM 均可）并创建客户端
        // baseUrl 填写平台提供的 API 网关地址；也可不配，从环境变量 COCOPAY_BASE_URL 读取
        String privateKeyPem = new String(Files.readAllBytes(Paths.get("merchant_private_key.pem")));
        CocopayClient client = CocopayClient.Config.builder()
                .baseUrl("https://api.example.com") // TODO: 替换为平台提供的 API 网关地址
                .mchNo("100000")
                .privateKey(SignUtil.parsePrivateKey(privateKeyPem))
                .build();

        // 2. 创建代收订单
        CocopayClient.CreateCollectionOrderRequest collReq = new CocopayClient.CreateCollectionOrderRequest();
        collReq.mchOrderNo = "ORD" + System.currentTimeMillis();
        collReq.currency = "USD";
        collReq.amount = "100.00";
        collReq.paymentMethod = "cashapp";
        collReq.email = "john@example.com";
        collReq.clientIp = "203.0.113.10";
        collReq.mchUserId = "U10001";
        collReq.notifyUrl = "https://merchant.example.com/notify";
        CocopayClient.GoodsInfo goods = new CocopayClient.GoodsInfo();
        goods.skuName = "Gold Coin Pack";
        goods.price = "100.00";
        goods.num = 1;
        goods.skuDesc = "100 gold coins";
        goods.skuUrl = "https://merchant.example.com/products/100-coins";
        collReq.goodsInfos = java.util.Collections.singletonList(goods);
        CocopayClient.CreateCollectionOrderReply collResp = client.createCollectionOrder(collReq);
        System.out.println("代收订单创建成功: " + collResp.orderNo + " " + collResp.payUrl);

        // 3. 创建代付订单
        CocopayClient.CreatePayOutOrderRequest payReq = new CocopayClient.CreatePayOutOrderRequest();
        payReq.mchOrderNo = "PO" + System.currentTimeMillis();
        payReq.currency = "USD";
        payReq.amount = "50.00";
        payReq.paymentMethod = "cashapp";
        payReq.account = "$johndoe";
        payReq.accountName = "John Doe";
        payReq.clientIp = "203.0.113.10";
        payReq.mchUserId = "U10001";
        CocopayClient.CreatePayOutOrderReply payResp = client.createPayOutOrder(payReq);
        System.out.println("代付订单创建成功: " + payResp.orderNo + " status=" + payResp.status);

        // 4. 查询余额
        CocopayClient.QueryBalanceReply balance = client.queryBalance();
        for (CocopayClient.BalanceItem b : balance.balances) {
            System.out.printf("余额: %s 可用=%s 未结算=%s 冻结=%s%n",
                    b.currency, b.availableBalance, b.unsettledBalance, b.frozenBalance);
        }
    }

    // 异步通知处理示例（以 Spring MVC 为例）
    //
    // @PostMapping("/notify")
    // public ResponseEntity<String> notify(@RequestBody String body, @RequestHeader HttpHeaders headers) {
    //     boolean ok = NotifyUtil.verifyOrderNotify(platformPublicKey,
    //             new NotifyUtil.NotifyHeaders(
    //                     headers.getFirst("x-mch-no"),
    //                     headers.getFirst("x-timestamp"),
    //                     headers.getFirst("x-nonce"),
    //                     headers.getFirst("x-signature")),
    //             body);
    //     if (!ok) return ResponseEntity.badRequest().build();
    //     NotifyUtil.OrderNotify notify = NotifyUtil.parseOrderNotify(body);
    //     // TODO: 按 order_no + state 幂等处理
    //     return ResponseEntity.ok("SUCCESS");
    // }
}
