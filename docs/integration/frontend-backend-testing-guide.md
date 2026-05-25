# 前后端联调指南

更新时间：2026-05-25

## 适用范围

- Android 仓库：`E:\Study\App\YingShi`
- 配套后端仓库：`E:\Study\App\YingShi-Server`

## 当前联调基线

- Android `debug` 默认模式：`REAL`
- Android `debug` 默认 `Base URL`：`http://10.106.3.193:8080/`
- App 内联调入口：`我的 -> 设置 -> 后端联调诊断`
- 后端全量 smoke 脚本：`E:\Study\App\YingShi-Server\scripts\integration-smoke.ps1`

说明：

- 诊断页现在是“轻量联调页”，主要负责 `Base URL / 模式 / 登录 / health`
- 帖子列表、refresh-token、上传任务状态/confirm/cancel、通知、头像等完整接口验收，建议以服务端 smoke 脚本为主
- Android 通知中心当前仍是 fake 数据，不用拿它来验后端通知接口

## 种子账号

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

## 1. 启动后端

在 `E:\Study\App\YingShi-Server` 下执行：

```powershell
.\mvnw.cmd spring-boot:run
```

建议先跑一次测试：

```powershell
.\mvnw.cmd test
```

健康检查地址：

```text
http://localhost:8080/api/health
```

## 2. 先跑后端 smoke 脚本

在 `E:\Study\App\YingShi-Server` 下执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1
```

当前脚本会覆盖：

- health
- login token
- refresh token
- me
- albums
- album posts
- posts list
- post detail
- post update
- post cover
- post media order
- media feed
- post comments
- media comments
- upload token
- upload task status
- local upload
- upload confirm
- upload cancel
- avatar upload / avatar fetch
- notifications list / detail / read / read-all
- trash list / detail / restore

如果脚本输出：

```text
Integration smoke completed with 0 failures.
```

说明后端当前基线可用于 Android 真实联调。

## 3. Android `Base URL` 规则

当前 Android 构建内置默认值：

```text
http://10.106.3.193:8080/
```

但诊断页里还提供两个常用预设：

- 模拟器：`http://10.0.2.2:8080/`
- 本机回环：`http://127.0.0.1:8080/`

使用建议：

- Android 模拟器：优先用 `10.0.2.2`
- 真机同 Wi-Fi 联调：用电脑局域网 IP，例如 `http://192.168.1.100:8080/`
- `127.0.0.1` 在真机上通常只会指向手机自己

## 4. `FAKE / REAL` 切换规则

当前行为：

- 默认模式已经是 `REAL`
- 模式切换会持久化到 `BackendDebugConfig`
- 切换 `Base URL` 会清空旧 token 并重建 Retrofit
- 切换 `Repository mode` 会递增 sessionVersion，避免 fake/real 页面状态混用

建议：

- 做纯 UI 精修时可切回 `FAKE`
- 做接口验收、真数据链路验证时切到 `REAL`
- 切到 `REAL` 后，需要重新打开目标页面，让它吃到新的 repository session

## 5. 当前诊断页能力

当前入口：

1. 打开 App
2. 进入 `我的`
3. 打开 `设置`
4. 打开 `后端联调诊断`

当前页面支持：

- 查看、编辑当前 `Base URL`
- 套用模拟器 / `127.0.0.1` 预设
- `保存并重登` 默认 demo 账号
- 清理登录缓存
- 切换 `FAKE / REAL`
- 执行 `health` 检查
- 查看最近一次联调结果

当前页面不负责的内容：

- 帖子列表 smoke
- 上传任务 status / confirm / cancel
- 真实通知接口
- 头像上传

这些能力应通过服务端 smoke 脚本、Swagger UI 或真实功能页联调完成。

## 6. 真机联调步骤

1. 电脑和手机连接到同一 Wi-Fi
2. 在后端仓库启动服务：`.\mvnw.cmd spring-boot:run`
3. 用 `ipconfig` 找到电脑局域网 IP
4. 确认 Windows 防火墙允许入站 `8080`
5. 在 Android 仓库构建 debug 包：

```powershell
.\gradlew.bat --no-daemon assembleDebug
```

6. 安装 debug 包到手机
7. 打开 `我的 -> 设置 -> 后端联调诊断`
8. 把 `Base URL` 改成 `http://<你的电脑IP>:8080/`
9. 点击 `保存并重登`
10. 确认登录状态变为成功，且 `当前生效地址` 已更新
11. 点击 `检查健康`
12. 确认最近结果里出现 `health=UP`
13. 切到 `REAL`
14. 重新打开以下页面逐项验收：
   - `我的`：当前账号、共享空间、搭子资料是否可见
   - `照片`：真实照片流是否加载
   - `相册`：相册列表和帖子卡片是否加载
   - 帖子详情：媒体、评论、编辑入口是否可用
   - 回收站：列表、详情、恢复、移出、永久删除是否可用
   - 系统媒体与传输中心：上传 / 导入后是否能回流到照片流

## 7. 模拟器联调步骤

1. 启动后端
2. 构建并安装 Android debug 包到模拟器
3. 进入 `我的 -> 设置 -> 后端联调诊断`
4. 选择 `模拟器` 预设，或手动填写：

```text
http://10.0.2.2:8080/
```

5. 点击 `保存并重登`
6. 做 `health` 检查
7. 切到 `REAL`
8. 重新打开要验证的页面

## 8. Cleartext HTTP 说明

当前 Android 行为：

- 只有 debug 构建放开了 cleartext HTTP
- release 的网络安全策略没有放宽

如果还看到 cleartext 失败：

- 确认安装的是 debug 包
- 确认 `Base URL` 使用的是 `http://`
- 重新执行 `assembleDebug`

## 9. 常见问题

`登录失败`

- 后端没启动
- `Base URL` 指错机器
- 刚切过 `Base URL`，旧 token 已被清空
- 手机和电脑不在同一局域网

`health 成功，但真实页面还是提示去诊断页登录`

- 切到 `REAL` 后没有重新打开页面
- 登录成功前页面已经缓存了旧状态
- 后端重启后旧 token 失效

`模拟器连不上`

- 误用了 `localhost`
- 应改成 `10.0.2.2`

`真机连不上`

- 误用了 `127.0.0.1`
- 应改成电脑局域网 IP
- Windows 防火墙拦截了 `8080`

`通知中心还是 fake`

- 这是当前预期行为
- 后端通知接口请通过 smoke 脚本或 Swagger 验证

`上传后没有在主照片流里看到`

- 当前媒体可能还没挂到帖子，但应已存在于真实媒体流
- 如果服务端 smoke 刚跑过，数据量会变化，这是预期行为
- 可重开 `照片` 页或重新进入 `REAL` 页面确认最新状态
