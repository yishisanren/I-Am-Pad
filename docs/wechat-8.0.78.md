# 微信 8.0.78（3180）适配与真机验证

日期：2026-09-19。本文记录 1.2.2 适配代码的验证过程；完整双端与整机重启测试使用版本标识仍为 1.2.1 / versionCode 13 的候选 Debug 包。正式发布版本为 1.2.2 / versionCode 14，使用已签名 Release 构建；其安装核验信息见 [v1.2.2 Release 说明](https://github.com/yishisanren/I-Am-Pad/releases/tag/v1.2.2)。

结论：本台 Android 16 真机上的扫码登录、应用冷启动、整机重启及双端新消息验收通过。重启后双端在线与消息状态由用户明确确认；不外推到未经测试的微信版本或设备。

## 基线与改动

- 本地基线：`1df9b18dc5cc97b4fdd456e61dc210170383b00b`（v1.2.1）。
- 上游基线：`28f00bc448ac5545253f77092adee821524f5a3f`，PR：<https://github.com/Houvven/I-Am-Pad/pull/51>。
- 仅移植上游微信折叠屏定位的 11 行回退规则，保留本地 Application.attach 生命周期修复及其他应用逻辑。
- 旧规则要求三个字符串同时出现于同一方法；8.0.78 将逻辑拆入辅助方法，使旧规则零命中。
- 保留旧规则优先，回退通过调用关系查找 public static、零参数、boolean 聚合方法，不写死混淆类名。

## 官方 APK 与候选包

微信官网下载地址：<https://dldir1v6.qq.com/weixin/android/weixin8078android3180_0x28004e32_arm64.apk>。

| 项目 | 核验结果 |
| --- | --- |
| 微信包名 / 版本 | com.tencent.mm / 8.0.78 / 3180，arm64 |
| 微信 APK SHA-256 | `41f7dc1f720767fa78fa20dd13ea034b817bbf6ebd23dfd1324c647499c9c1ba` |
| 微信签名证书 SHA-256 | `0fe4ff85c215918396dadc7cd8ce6963339af33d37751a56e54c7206b63a3c7c`，APK v2 校验通过 |
| 插件候选 Debug APK SHA-256 | `995ff952da1cee4bc93fda480856827c7efaf3199f53b36d5d89e14825db7e84` |
| 插件签名证书 SHA-256 | `01917cb84f064f5c884078c2bef71541aaf69f82cc68e6aaf3dfa3b19622b070`，与已发布 1.2.1 相同 |

候选验证阶段已在真机直接计算两个已安装 base.apk 的 SHA-256，与上述本地产物一致；此处 Debug 包哈希不代表正式 Release 包。

## 静态定位与构建

- `com.tencent.mm.ui.gk.d0()Z` 包含 `royole`。
- `com.tencent.mm.ui.gk.h0()Z` 包含 `tecno`、`ro.os_foldable_screen_support`。
- 同时调用两者的方法中，`com.tencent.mm.ui.gk.R()Z` 唯一满足 public static + 零参数 + boolean；另一调用者 `h1.K2(...)` 有两个参数。
- Pad 登录检查仍唯一定位到 `b41.j1.a(String, String, Continuation)`，public final，匹配现有字符串与参数规则。
- `./gradlew check assembleDebug assembleRelease` 通过；现有 6 项 JVM 测试、Lint 均通过。Release 构建产物未签名，本次实际安装 Debug 候选包。

## 真机实测

设备：OPPO PME110，Android 16 / API 36。全程保数据覆盖安装。

| 检查项 | 证据与结果 |
| --- | --- |
| 升级 | 从微信 8.0.77（3160）升级到 8.0.78（3180）；系统包管理器回读确认 |
| 旧方法缓存 | 21:37:48，旧 `u21.k1.a`、`ui.ek.Q` 缓存回退重新定位；其中首个缓存失败日志为 NativeReflect 尚未加载，随后 DexKit 初始化并成功定位 |
| 新方法安装 | 定位 `b41.j1.a(...)` 和 `ui.gk.R()`；两项均记录 hook-installed |
| 折叠设备判断 | 21:37:51 起记录 hook-hit=isFoldableDevice_method |
| Pad 登录检查 | 21:38:00，主进程记录 hook-hit=checkLoginAsPad_method |
| 扫码登录 | 用户完成主手机扫码确认；助手查看到安卓微信会话列表 |
| 双端新消息 | 用户明确确认：iPhone 在线，两端都能收到新消息 |
| 微信冷启动 | 21:40，am force-stop 后 am start -W 返回 COLD / Status ok；新进程从缓存加载两个正确方法，折叠设备 Hook 再次命中；仍进入已登录会话列表 |
| 系统重启 | sys.boot_completed=1；boot ID 从 f98ae14d-cf56-43e0-a64d-edf22c04aa5a 变为 1c11af3d-e557-404a-96c3-44c0119803a5，确认真实重启；用户首次解锁后状态 RUNNING_UNLOCKED |
| 重启后 Hook | 21:42:37，微信新进程 16923 加载模块；21:42:38 从缓存读取 b41.j1.a(...)、ui.gk.R() 并安装成功，折叠设备判断再次实际命中 |
| 重启后账号与消息 | 助手查看到已登录会话列表，没有重新扫码或认证；会话时间由重启前 21:40 更新为重启后 21:43，未读条数增加。用户随后明确确认：“重启后两端均在线，消息正常” |

冷启动时另见“DEX 缓存更新 / beautify/主题 / classSmileyTabAdapter”适配报错；系统重启后另有其他插件的 132 项 DEX 缓存更新提示。相关功能及文案不在本项目代码中；本轮未修改其他模块配置。重启后的 crash buffer 出现 com.android.launcher 的异常，未发现 com.tencent.mm 崩溃记录，不将系统桌面或其他插件问题归因于 I-Am-Pad，也不把没有闪退等同于所有微信功能通过。

## 验证范围与交付状态

- 本台设备、当前账号、微信 8.0.78（3180）的本轮验收已完成；长期稳定性未作为本次短时测试结论。
- 本轮未重测旧微信版本、小红书、QQ 或飞书。
- 正式发布版本为 1.2.2 / versionCode 14；以上候选包测试记录保留，Release 包哈希及独立安装核验另见对应 GitHub Release 说明。
