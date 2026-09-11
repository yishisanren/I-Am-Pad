# 飞书平板登录：第一阶段研究与模拟开发

## 结论

飞书 7.76.14 的本地平板识别与登录设备型号均可在 Java 层控制，已具备可实施条件。当前实现可让飞书进程读取到真实平板型号和 `tablet` 系统特征，但“同一账号手机/平板同时在线”属于服务端最终结果，必须用真机登录验证，静态分析不能替代。

## 分析对象

- 来源：飞书官方下载页 `https://www.feishu.cn/download?from=bottom_banner`
- 官方接口在分析时返回版本：`Android@V7.76.14`
- 包名：`com.ss.android.lark`
- versionName：`7.76.14`
- versionCode：`7761450`
- APK 大小：约 353 MB
- DEX 数量：59
- SHA-256：`36e5e576fd519843461d4a91ed5bfcf4ac14911856bc1b01cc109f84a6432ebe`
- MD5：`b078556540c7c6f8fd653d3def29f811`

官方下载接口同时返回了 `3b1fbb6feed522adba92bb608145f6e7`，但它与下载文件的 MD5 不一致，因此本报告不把该字段当作 APK 文件哈希。

## 静态证据

1. 已检查的 Passport `TerminalType` 枚举只有 Unknown、PC、Web、Android、iOS，Android 数值为 `3`。这不证明整个应用或服务端不存在其他 Pad 分类字段。
2. Passport 请求拦截器固定写入 `X-Terminal-Type: 3`，并在 `X-Device-Info` 中上报 `device_model`、`device_name`、`device_os` 等字段。
3. `DevicesService.getDeviceInfo()` 从 `RomUtils.d()` 读取型号；`RomUtils.d()` 直接返回 `Build.MODEL`。
4. APK 内唯一存在 `DeviceInfo.getDeviceModel(): String`，可作为稳定的精确 Hook 点。
5. 至少 7 个方法通过 `ro.build.characteristics` 中是否包含 `tablet` 判断平板，其中包括通用的 `DeviceUtils.isPad()`。
6. 离线校验器已在目标 APK 上确认：唯一设备型号 Getter、唯一 `RomUtils` 型号来源、7 个平板检测方法、3 个 Passport Header 写入位置均存在。

已检查的通用 Passport 请求代码固定上报 `TerminalType=3`；尚未采集真实 Android 平板的登录请求作对照，不能把这个静态结果等同于所有 Pad 登录路径。当前实现覆盖了已定位的 Java 型号来源，但没有证明服务端如何分类新会话。

## 实现方案

- 仅精确匹配国内版飞书包名 `com.ss.android.lark`，不误作用于测试包或国际版 Lark。
- 在飞书进程内模拟 Xiaomi Pad 6：
  - Brand / Manufacturer：`Xiaomi`
  - Model：`23043RP34G`
  - `ro.build.characteristics`：`tablet`
- 当前复用了名为 `afterApplicationAttach` 的上游帮助函数，非 Tinker 分支实际拦截 `Application.onCreate`，并非真正的 attach 时点；随后 Hook `DeviceInfo.getDeviceModel()`。早期读取与重复回调风险尚待验证。
- Hook 点数量不是 1 时拒绝静默继续，并记录错误；成功时记录安装数量和模拟型号。
- LSPosed 推荐作用域增加 `com.ss.android.lark`。
- 模块版本升级为 `1.2.0`（versionCode 12）。

选择 `23043RP34G` 是因为它是小米官方资料中可核验的 Xiaomi Pad 6 型号，不使用虚构或手机型号。

## 自动验证

```bash
python tools/verify_feishu_apk.py /path/to/Feishu.apk --expected-version 7.76.14
./gradlew testDebugUnitTest
./gradlew check assembleDebug assembleRelease
```

已完成的结果：

- 单元测试通过：飞书包名精确路由、近似包名不误命中、模拟平板配置一致。
- Android Lint / Check 通过。
- Debug APK 构建成功并通过 APK Signature Scheme v2 校验。
- Release APK 构建成功，但当前为 unsigned，不作为真机调试包。

## 真机验证门槛

明日连接 Android 16 真机后按以下顺序验证：

1. ADB 确认设备、Android 版本、ABI、飞书版本和 LSPosed 环境。
2. 安装 Debug APK，在 LSPosed 中只勾选飞书作用域，强制停止并重新启动飞书。
3. 确认日志出现 `Installed Feishu tablet hooks` 且 `deviceModel=1`。
4. 从未登录状态完成飞书登录，检查登录界面、设备管理页和被另一台手机观察到的在线状态。
5. 核心验收：原手机会话不被踢下线，新会话被飞书视为独立平板会话；重复冷启动后仍成立。
6. 若失败，再增加仅用于调试的请求参数观测，区分 Java 参数未生效、native 参数覆盖和服务端拒绝三类原因。

未经真机验证，当前状态只能称为“静态适配完成、模拟 APK 可调试”，不能称为“飞书平板登录已完成”。

## 真机阶段更新

截至 2026-09-11，重新扫码后主手机被踢，核心验收失败。用户确认退出前两端确实都能接收新消息；不能再把本项目概括为从未成功共存。证据边界与后续调查见 [第二阶段记录](feishu-phase-2.md)。
