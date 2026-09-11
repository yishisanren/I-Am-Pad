# 飞书会话重新建立失败：真机诊断记录

## 结果与事实来源

- 测试机：Android 16，飞书 7.76.14；模块调试版 1.2.0。
- **用户确认**：助手要求退出前，主手机与测试机均能收到新消息，成功共存并非页面残留。
- **用户确认**：退出后重新扫码，主手机被踢。当前核心验收失败，尚未修复。
- **用户确认的已排除项**：企业限制针对多手机，手机与 Pad 无此限制。不继续调查企业策略。
- 用户补全时间线：昨天安卓登录挤掉 iPhone，随后 iPhone 重新登录挤掉安卓，安卓端未再处理；今天安装插件后安卓无需人工登录便自动进入账号，之后两端均能收到新消息；助手要求安卓退出后重新扫码，iPhone 再次被踢。因此成功样本是“插件安装后自动恢复既有账号状态”，不是“插件开启时新扫码成功”。具体恢复机制及原会话当时的有效性未知。

## 本轮被动观测

只增加 `FeishuDebugProbe`，保留原有型号与属性修改，没有增加新的返回值篡改。构建和已有 JVM 测试通过，覆盖安装成功，强停并启动测试飞书；未清数据、未退出、未再次扫码。

LSPosed 模块日志在设备时间 `2026-09-11T08:30:09` 至 `08:30:16` 记录：

```text
[DEBUG-feishu-session-v1] installed=passport-header
[DEBUG-feishu-session-v1] installed=local-pad
[DEBUG-feishu-session-v1] installed=qr-init
[DEBUG-feishu-session-v1] local-pad=true
[DEBUG-feishu-session-v1] header-terminal-android=true
[DEBUG-feishu-session-v1] header-model-target=true
```

主进程观测到以上全部信号，其他进程不视作独立对照。

可支持的结论：现有会话重启后，被观测到的 `DeviceUtils.isPad()` 返回 true；Passport Header 写入帮助方法收到目标型号 `23043RP34G` 与终端值 `3`。**不是线上抓包，不证明服务端接收值或最终会话分类，也不代表新扫码登录链已经验证。**

未出现 `qr-init-entered` 符合本轮未发起扫码的操作；不能用它排除另一条扫码路径。

日志只输出固定标签与布尔等值比较结果，不输出 Header 原文、设备 ID、用户 ID、token、Cookie、二维码内容、消息、错误消息或调用参数。每进程去重，仅 Debug 生效。诊断结束后应移除整个探针文件及调用。

## 调查顺序与可证伪预测

1. **新扫码走其他分类路径**：若为真，扫码创建与启动自动恢复应能观测到不同的调用或分类输入。成功入口现已明确为启动自动恢复。
2. **会话或设备登记状态差异**：若为真，相同 Java 型号下，新旧会话仍会有不同服务端分类。不得通过清数据/重置设备 ID 盲试；尚未得到前后对照。
3. **Hook 时机遗漏**：若为真，认证前可能发生未被当前晚期 Hook 覆盖的读取。本轮仅排除了“当前观测到的本地判断和 Passport 型号仍是手机”，不能排除早期缓存、其他路径或 native。

静态分析补充：当前 QR 初始化方法构建 `/accounts/qrlogin/init`，body 有 `biz_type=default`、可选 `flow_type`；确认扫码一侧读取服务器给出的 `is_multi_login` 并传回。没有证据证明此布尔值就是 Pad 登录开关，不修改它。

## 验证边界与安全暂停点

最小已知复现是“退出测试端 → 再次扫码 → 主手机被踢”，来自用户实测。目前没有无人值守、直接验证双端新消息的测试，JVM 测试也不覆盖服务器会话分类。依据 diagnosing-bugs 的精确症状要求，以双端实际消息收发为最终判据；为避免再次踢掉主手机，跳过重复有损复现，不宣称建立了确定性自动红绿循环。

`tools/verify_feishu_runtime.sh` 仅采集历史模块日志、Activity 记录与用户上报状态；历史日志不代表当前进程，MainActivity 不代表有效会话。已移除其自动 PASS 输出，不能将用户传入的 primary-online 当作独立验证。

下一步建议重走已报告成功的顺序：用户在 iPhone 恢复登录；安卓端不主动退出、不扫码、不清数据；随后仅重启安卓飞书，观察是否再次自动恢复，并由用户确认双端新消息。该步骤需要用户在 iPhone 完成认证，助手不能代做。尚未执行，不保证可恢复；iPhone 重新登录可能先使安卓会话失效。没有差异证据前不盲改枚举、不重置设备标识。

观测版最终 `check assembleDebug assembleRelease` 通过；Release unsigned。重启后 Activity 记录含 MainActivity，仅作为页面状态记录，不作为会话有效性证明。

## 本地交接

Shared Brain 当前项目解析为 null / readonly；本轮没有创建共享事件，也不声称已同步。诊断证据仅存本仓库；成功状态仍为用户报告，失败根因未知。

## 实验 2：iPhone 恢复登录后仅重启安卓

- 用户确认 iPhone 已登录后执行。未改代码/模块配置，未点击安卓弹窗、退出账号、扫码或清数据。
- 重启前安卓 PID 3792，前台为 `ShowDialogActivity`。
- `am force-stop com.ss.android.lark` 后通过 Launcher 启动，PID 变为 11462（十六进制 2cc6）。
- 本次主进程日志标识 `2cc6-1a08de3d407`，设备日志时间 `2026-09-11T08:35:13` 起：三个探针安装成功，`local-pad=true`、`header-terminal-android=true`、`header-model-target=true`。
- 重启后两次检查，前台仍为 `com.ss.lark.android.passport.biz.feature.showdialog.ShowDialogActivity`，未观察到自动返回消息主界面。弹窗正文尚待用户提供，不能仅凭通用 Activity 名称判定具体失效原因。
- 结论：这一次“iPhone 登录后仅重启安卓”尚未重现先前的自动恢复；不能据此否定用户报告的历史双端收消息事实。先保留现场，不反复登录，不屏蔽失效弹窗伪装成功。

### 用户补充弹窗

用户确认提示：该账号登录设备超过管理员设置的同时在线设备数量上限，已自动退出，重新登录需完成身份验证。它确认本次发生并发限制导致的会话失效；不代表需要重新调查企业策略，也不能单凭该提示推导具体的服务端设备分类字段。

## 实验 3：被动检查底层 SDK 初始化输入

增加 Debug 专用 `InitSDKRequest` 构造后探针，先执行原构造逻辑，再只读取 `device_model`、`device_info` 是否存在及 `device_platform` 与固定常量的等值结果。不修改参数/返回值，不读 ID、凭据、配置 Map 或消息。

`check assembleDebug` 通过；覆盖安装成功，仅重启安卓，不操作失效弹窗。PID 14022，进程日志标识 `36c6-1a08de7edf3`，设备日志时间 `2026-09-11T08:39:42`：

```text
installed=native-init-input
native-init-model-target=true
native-init-device-info-present=true
native-init-platform-android=false
local-pad=true
```

只能确认传给 SDK 初始化对象的型号已是目标型号，不能证明 native 后续所有请求均使用它。`platform-android=false` 只表示不等于小写字符串 android，不能将 null、空值、不同大小写或其他平台值擅自解释成异常。

DEX 引用检查定位到 `classes38.dex` 的 `framework.assembly.rust.f.e` 创建嵌套 DeviceInfo；只设置 device ID 和 install ID 的 builder 方法。该路径未显式设置 device_platform，故不把上述 false 当作新故障。底层出现的 `extra_type` 目前只定位到在线设备列表/推送处理字符串，未证明它是登录分类入口。

重启后仍为 ShowDialogActivity，未恢复。当前三个已观测层面（本地判断、Passport 请求头组装、SDK 初始化型号）均未发现手机型号残留，现有模拟方案仍未通过双端在线验收。缺少成功 Pad 新会话的同口径对照，暂不盲改终端枚举、设备标识或反复进行主账号身份验证。

## 实验 4：按请求路径与 Passport FFI 分别观测（2026-09-11 晚）

用户确认 1.2.1 的微信、小红书重启后保持在线并收到新消息，要求聚焦飞书。当前 iPhone 飞书正常在线，安卓飞书仍提示超过登录限制。用户重新连接测试机后，确认飞书仍为 7.76.14 / 7761450。

### 补齐的证据缺口

- 原 Header 探针按进程去重，但不区分请求路径；启动时出现目标型号不能证明二维码创建请求使用同样的型号。
- 全部 59 个 DEX 的字段/方法引用检索新增定位到 `lark_passport_ffi.DeviceInfo`；classes39 的 `com.ss.android.lark.integrator.passport.lazy.init.a.b()` 从原 Passport DeviceInfo 取型号、固定 Android terminalType，调用 `passportInjectNativeDependency`。原 InitSDKRequest 探针不覆盖这个接口。
- 1.2.2-probe1 / code 14 仅增加飞书被动观测，不增加新的身份修改。`DeviceInfo.into()` 执行原方法后只输出目标型号与 terminalType=3 的布尔结果，不读取 ID Map、账号或凭据。
- `CommonRequestInterceptor.b(km6.f)` 的真实 DEX 签名及 `getPath` / `getRequestBody` 已核实。用线程局部作用域关联其同步 Header 写入，只允许 qr-init、qr-poll、device-update、device-list 四个固定标签。未知路径（包括带查询串的路径）不输出；仅 device-update 读取 body 的 `device_model`，不序列化整个 body。
- 这仍是请求组装处的观测，不是线上抓包、响应成功或服务端设备分类证据。

### 构建、备份和真机结果

- `check assembleDebug assembleRelease` 通过；Debug 单测 12 项全通过。新增 6 项覆盖路径白名单、敏感输入不输出、重复/缺失型号、嵌套作用域/原异常、只读 body 与日志异常隔离；不覆盖飞书服务端会话验收。
- 覆盖安装前保留已验证 1.2.1 APK：`/private/tmp/iampad-1.2.1-user-validated-before-feishu-probe2.apk`，SHA-256 `0fd4a0e76bc4851fa8bdc23d31114be35a68beed1d512aefbbb2aaee627e7e98`。备份不包含应用账号数据。
- 1.2.2-probe1 安装成功，只强停再启动安卓飞书；未退出账号、清数据、扫码、重启系统或重启微信/小红书。
- 主进程 PID 28607，日志标识 `6fbf-1a090eda1e1`，设备时间 22:44:47 起；新增两处探针安装成功，22:44:48 出现：

```text
passport-ffi-model-target=true
passport-ffi-terminal-android=true
request=device-update terminal-android=true
request=device-update model-present=true
request=device-update model-target=true
request=device-update body-model-present=true
request=device-update body-model-target=true
request=device-update headers-assembled
```

这些结果进一步排除了“本次观测到的设备更新与 FFI 输入仍携带原手机型号”，但不能排除持久设备登记、其他 native 请求或服务端分类差异。前台仍是 ShowDialogActivity，没有恢复成功证据。

### 下一安全观测点

已请用户仅进入安卓二维码登录页，不扫码、不确认登录、不清数据；若需额外主动退出账号则先停。目的仅观察二维码创建/轮询的请求组装参数，不能把二维码出现当作并发登录成功。未经新证据支持，不要求用户再次完成可能挤掉 iPhone 的认证。

### 二维码页观测结果

用户确认进入二维码页后，只读检查得到主进程 PID 30739（日志标识 `7813-1a090ef9f58`），前台为 `QRLoginProvideActivity`。相较前次 PID 已变化，变化原因未经确认，不推断用户执行了退出操作。

设备时间 22:47:00 至 22:47:01，新进程出现 `qr-init-entered`，以及 qr-init、qr-poll 各自的 `terminal-android=true`、`model-present=true`、`model-target=true`、`headers-assembled`。因此本次二维码创建/轮询的已观测 Header 组装路径没有漏掉目标型号；不据此判定后端分类为 Pad。

静态检查 `QRLoginProvideModel.f.c(ResponseModel)`：初始化响应 `nextStep=qr_login_polling` 时消费 `stepInfo.token` 和 `stepInfo.subtitle`；未在这段消费代码中找到用于确认 Pad 分类的字段。不读取或输出实际 token、flow key、二维码内容，也不据此断言完整服务端响应没有其他字段。

下一步需观察 iPhone 扫码后的登录确认页所显示的设备名称/类型及互斥提示，先不点击确认登录。这是待用户执行的步骤，不记为已完成，也不保证单靠显示名称即可证明服务端并发分类。

## 最终验证结论（本阶段暂停）

- iPhone 登录确认页显示设备类型 `23043RP34G`、设备名称 `Xiaomi 23043RP34G`，证明模拟机型进入了确认流程；页面标题仍为“飞书移动端登录确认”，不能证明会话被服务端归类为 Pad。
- 用户确认“登录环境异常”提示属于其正常登录现象，不作为本阶段插件故障依据。
- 用户授权完成一次登录验证后确认仍不能同时登录，飞书并发目标未通过。
- 用户随后发现真实鸿蒙平板登录也会把 iPhone 挤掉。此前“真实平板与 iPhone 可正常共存”的前提失效；这降低了继续用同一账号做插件归因的有效性，但不能据此推断具体企业策略或服务端规则。
- 按用户要求暂停飞书调试。正式 1.2.1 删除 Debug 专用请求探针，不再保留 `FeishuDebugProbe` / `FeishuRequestTrace`；保留实验性机型与 tablet 特征适配，并在 README 与更新日志中明确未通过双端并发验收。
