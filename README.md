# Pixiviz Android

pixiviz 的 Android 原生客户端。

- **UI**：Kotlin + Jetpack Compose，液态玻璃（Liquid Glass）+ 悬浮胶囊底部标签栏。
- **数据**：自建 Cloudflare Worker 中转，直连 pixiv App API（`app-api.pixiv.net`）。
- **构建**：GitHub Actions 云编译，产出签名 APK。

> 本项目受 [asadahimeka/pixiviz](https://github.com/asadahimeka/pixiviz)（Apache-2.0）的形态启发**重新实现**，
> 不是其前端代码的移植，也不包含其 `dist/` 产物。

## 为什么需要自建中转

实测结论（2026-10）：

| 目标 | 结果 |
| --- | --- |
| `hibiapi.cocomi.eu.org`（原站使用的公共代理） | 403，被 Cloudflare 人机校验拦截 |
| `pximg.cocomi.eu.org`（原站图片反代） | 403 |
| `oauth.secure.pixiv.net` 的 `client_credentials`（游客授权） | `code 1508 The grant type is unauthorized for this client_id` |
| `app-api.pixiv.net/web/v1/login` | 302，PKCE 授权流程可用 |
| `i.pximg.net` | 可达 |

也就是说：**pixiv 已关闭游客 token**，所有接口都必须携带用户 token，公共代理也已失效，因此数据必须经你自己的 Worker 中转。

## 仓库结构

```
.github/workflows/    android.yml（出包）· worker.yml（部署）· smoke.yml（自检）
app/                  Android 客户端（Kotlin / Compose）
worker/               Cloudflare Worker 中转（TypeScript）
tools/                取 refresh_token 的离线页面
```

## 必需配置

GitHub 仓库 → Settings → Secrets and variables → Actions：

**Secrets**

| 名称 | 说明 |
| --- | --- |
| `CLOUDFLARE_API_TOKEN` | 需含 `Workers Scripts:Edit`、`Workers KV Storage:Edit` |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare 账号 ID |
| `PIXIV_REFRESH_TOKEN` | 可选。仅当使用「Worker 代持账号」模式时需要 |
| `WORKER_ACCESS_KEY` | 自定义随机串，Worker 与 App 共用 |
| `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | APK 签名密钥（缺失时降级为 debug 签名） |

**Variables**

| 名称 | 说明 |
| --- | --- |
| `WORKER_URL` | 例如 `https://pixiviz-relay.<你的子域>.workers.dev` |

## 构建

```bash
./gradlew assembleRelease      # 产物在 app/build/outputs/apk/release/
./gradlew testDebugUnitTest    # JVM 单元测试

cd worker && npm install && npx wrangler deploy
```

## 许可

Apache License 2.0，见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。
