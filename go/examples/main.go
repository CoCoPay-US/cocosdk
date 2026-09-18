package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/CoCoPay-US/cocosdk/go/cocopay"
)

func main() {
	// 1. 加载商户 RSA 私钥（商户后台配置公钥到平台，私钥本地保管）
	privateKeyPEM, err := os.ReadFile("merchant_private_key.pem")
	if err != nil {
		log.Fatal(err)
	}
	privateKey, err := cocopay.ParsePrivateKeyPEM(privateKeyPEM)
	if err != nil {
		log.Fatal(err)
	}

	// 2. 创建客户端（BaseURL 填写平台提供的 API 网关地址；也可不配，从环境变量 COCOPAY_BASE_URL 读取）
	client, err := cocopay.NewClient(cocopay.Config{
		BaseURL:    "https://api.example.com", // TODO: 替换为平台提供的 API 网关地址
		MchNo:      "100000",
		PrivateKey: privateKey,
		Timeout:    15 * time.Second,
	})
	if err != nil {
		log.Fatal(err)
	}

	ctx := context.Background()

	// 3. 创建代收订单
	collResp, err := client.CreateCollectionOrder(ctx, &cocopay.CreateCollectionOrderRequest{
		MchOrderNo:    "ORD" + time.Now().Format("20060102150405"),
		Currency:      "USD",
		Amount:        "19.99",
		PaymentMethod: "cashapp",
		Email:         "john@example.com",
		ClientIP:      "203.0.113.10",
		MchUserID:     "U10001",
		NotifyURL:     "https://merchant.example.com/notify",
	})
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("代收订单创建成功: order_no=%s pay_url=%s\n", collResp.OrderNo, collResp.PayURL)

	// 4. 创建代付订单
	payResp, err := client.CreatePayOutOrder(ctx, &cocopay.CreatePayOutOrderRequest{
		MchOrderNo:    "PO" + time.Now().Format("20060102150405"),
		Currency:      "USD",
		Amount:        "50.00",
		PaymentMethod: "cashapp",
		Account:       "$johndoe",
		AccountName:   "John Doe",
		ClientIP:      "203.0.113.10",
		MchUserID:     "U10001",
	})
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("代付订单创建成功: order_no=%s status=%d\n", payResp.OrderNo, payResp.Status)

	// 5. 查询余额
	balance, err := client.QueryBalance(ctx)
	if err != nil {
		log.Fatal(err)
	}
	for _, b := range balance.Balances {
		fmt.Printf("余额: %s 可用=%s 未结算=%s 冻结=%s\n",
			b.Currency, b.AvailableBalance, b.UnsettledBalance, b.FrozenBalance)
	}
}
