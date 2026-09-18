# CoCoPay 商户接入 SDK

CoCoPay 支付网关官方商户 SDK，覆盖：代收下单/查询、代付下单/查询、余额查询、异步通知验签。

仓库地址：https://github.com/CoCoPay-US/cocosdk

| 语言 | 目录 | 引入方式 |
|------|------|--------|
| Golang | [`go/`](./go) | `go get github.com/CoCoPay-US/cocosdk/go`（零第三方依赖） |
| Node.js | [`nodejs/`](./nodejs) | 本地路径 / `npm pack`（零第三方依赖，Node >= 16） |
| Java | [`java/`](./java) | JitPack / `mvn install`（Java 11+，仅 Jackson） |
| PHP | [`php/`](./php) | Composer path 仓库（PHP >= 7.4） |

各 SDK 目录内均有独立的 README、使用示例（examples）和自测用例。

