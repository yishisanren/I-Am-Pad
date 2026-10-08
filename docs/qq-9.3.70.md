# QQ 9.3.70 诊断与本地修复

## 当前结论

**2026-10-08：1.2.4-qq.3 跨天验收失败，暂不建议安装。** 用户告知次日首次打开 QQ 自动出现 W21，工具实测弹窗原文“身份验证失败，请你重新登录。(w21)”并停留登录界面。本次设备已重新启动，10:05 启动日志仍确认主进程与 MSF 的 TABLET 分类及 manifest 平板 App ID 正确，QQ 版本、模块版本和作用域未变。昨天的短时双端在线与冷启动结果没有覆盖会话恢复失败，不能视为完整修复。

W21 原因仍在排查：现有证据不能把它直接等同于 Hook 检测，也不能从本地平板参数正确推出服务端认证可长期保持。

2026-10-07 本机 QQ 的旧适配没有达到 Android 平板与 iPhone Air 并发登录的目标：用户反馈 Android 登录会挤掉 iPhone，iPhone 登录后 Android 提示被该设备下线。用户日常收到的是短信或人脸安全验证，没有“外挂或其他软件影响 QQ 正常使用”的提示。本轮没有获得 QQ 服务端风控原因，不能认定它检测到了 I-Am-Pad Hook。

2026-10-07 的短时记录：候选 `1.2.4-qq.3 / 18` 覆盖安装并实际命中 QQ 的 TABLET 分类，QQ 主进程和 MSF 自然调用均选择自身 manifest 内置的平板 App ID。用户当时确认 Android 与 iPhone Air 同时在线、消息正常；18:12 工具实测 Android QQ 冷启动后仍进入已登录主界面，没有安全验证提示。次日跨天失败记录见本节开头。

## 实测环境

| 项目 | 本轮实测 |
| --- | --- |
| 手机 | OPPO PME110，真实设备标识 OPPO / PME110 / OP61C1L1 |
| 系统 | Android 17 / API 37 |
| 框架 | LSPosed 2.2.1（7912），libxposed API 102 |
| QQ | 9.3.70 / 16410 |
| 原模块 | I-Am-Pad 1.2.3 / 15 |
| 当前候选 | I-Am-Pad 1.2.4-qq.3 / 18，Release，无 DEBUGGABLE |
| QQ 作用域 | 当前仅 com.houvven.impad，Guise 未作用于 QQ |

## 证据与修复依据

1. 初始 QQ 同时处于 Guise 和 I-Am-Pad 的作用域。Guise 配置型号 25091RP04C / device piano，I-Am-Pad 再改为 23046RP50C；同一进程出现互相覆盖且不同代平板的身份字段。复核时 Guise 已不在 QQ 作用域，配置内容未删除。冲突已经存在，但它是否触发短信或人脸风控未经证实。
2. 原方案主要修改 Build 字段和 ro.build.characteristics；QQ 9.3.70 的 AppSetting 通过 PadUtil 设备枚举选择手机或平板登录参数。仅显示平板型号无法验收并发登录。
3. 第一个新候选在启动早期安装分类 Hook，但没有实际命中。QQ 在 Application.attach 内换用了另一个 ClassLoader；真机记录确认 early 与 final PadUtil 类对象不同。修复在原 attach 完成后使用实际应用 ClassLoader 安装，保留原生命周期行为。
4. 第二个候选为了检查参数，主动在 attach 后调用 AppSetting.e()。反编译表明该方法会初始化 manifest 参数并固定初始化状态；这个时点应用全局 Context 尚未就绪，诊断本身可能干扰参数。本候选移除了主动调用，改为只观察 QQ 自然调用的原始返回值。
5. 18:09 实测主进程与 MSF 原分类 PHONE，Hook 返回 TABLET，真实硬件品牌型号保持 OPPO / PME110 / OP61C1L1；自然登录调用返回 App ID 537395483，与 manifest 的 AppSetting_params_pad 数值一致。没有覆盖 App ID、签名或验证码验证流程。

暂时关闭 QQ Hook 的对照导致 Android 退出登录；恢复作用域没有自动恢复会话。该操作影响了登录状态，后续不再通过反复切换设备类型做对照，也未主动退出账号、清理 QQ 数据或删除 MMKV。

## 验证状态

| 验证项 | 结果 |
| --- | --- |
| 分类定位唯一性、异常保留、原初始化执行 | 单元测试通过 |
| 登录观察不提前调用、不覆盖原始结果 | 单元测试通过 |
| 测试总计 | 11 通过，0 失败、0 错误 |
| Release Lint | 0 错误，8 警告 |
| Release 构建、签名连续性、装机字节回读 | 通过 |
| QQ 主进程与 MSF TABLET / 内置平板 App ID | 通过，来自实际自然调用 |
| Android 与 iPhone Air 同时在线、新消息 | 通过，来自用户确认 |
| 冷启动保持登录 | 通过，18:12 工具实测主界面，未出现安全验证提示 |
| 次日首次打开保持登录 | 失败，2026-10-08 首次打开出现 W21 |
| QQ 已检测 Hook 导致安全验证 | 未证实 |

安全验证原因只能在取得服务端说明或有足够的对照证据后确定。一次登录、Hook 日志、应用冷启动均不能证明次日不再触发验证。

## 发布与回退

GitHub [v1.2.4-qq.3 预发布版本](https://github.com/yishisanren/I-Am-Pad/releases/tag/v1.2.4-qq.3) 保留原 APK、同签名回退包及各自的 SHA-256 文件供追溯；该版跨天验收已失败，暂不建议安装。稳定版仍为 1.2.3；没有因发布重新构建或替换原包，回退到旧逻辑也不代表已解决 W21。

候选：`dist/I-Am-Pad-v1.2.4-qq.3.apk`

SHA-256：`d6f1df909c1e5ab581d51e090a66e010c499b8939f78eb0e8aa321c9db1b579d`，与设备已安装 base APK 完全一致。

回退：`dist/I-Am-Pad-v1.2.3-rollback.1.apk`，从 51e83df 的旧逻辑构建，仅将 versionCode 提高到 18 并标注回退版本，与候选同签名、相同 versionCode，可覆盖安装保留模块数据。旧版原始 versionCode 15 可能被系统拒绝降级，因此不能靠卸载重装回退。

回退包 SHA-256：`bb84d82674e3ec076b0f249078d9335717c0d2b7c730d2a9e57e900b51ef4f41`。已通过旧源码 6 项测试、Release 构建、签名与对齐检查；未在手机安装回退，以免打断本轮验证。

```sh
adb install --no-incremental -r --user 0 dist/I-Am-Pad-v1.2.3-rollback.1.apk
```

安装回退后重新启动作用域中的应用。回退恢复旧 QQ 身份模拟，切回手机登录类型可能再改变会话并挤掉其他设备；仅在明确需要撤销候选时使用。设备原始配置或账号数据未加入仓库。

脱敏运行日志与验证摘要位于 `dist/qq-9.3.70-evidence/`。
