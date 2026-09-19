# I-Am-Pad  

[![Latest Release](https://img.shields.io/github/v/release/yishisanren/I-Am-Pad?style=flat-square&logo=github&label=Release)](https://github.com/yishisanren/I-Am-Pad/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/yishisanren/I-Am-Pad/total?style=flat-square&logo=github&label=Downloads)](https://github.com/yishisanren/I-Am-Pad/releases)
[![Stars](https://img.shields.io/github/stars/Houvven/I-Am-Pad?style=flat-square&logo=github&label=Stars)](https://github.com/Houvven/I-Am-Pad/stargazers)

---

## 支持应用  
- 小红书  
- 微信  
- QQ  
- 钉钉  
- 飞书（7.76.14 实验适配，双端并发登录尚未通过验收）
- 企业微信  
  - 国航之翼
  - 粤政易

## 1.2.3 状态：API 102，真机待验证

升级到正式依赖 `io.github.libxposed:api:102.0.0`，模块最低与目标 API 均为 102。运行环境必须支持 libxposed API 102 或更高版本；仍使用 API 101 框架的设备请继续使用 [v1.2.2](https://github.com/yishisanren/I-Am-Pad/releases/tag/v1.2.2)。这里的 API 102 是 Xposed 框架接口版本，不是 Android SDK 版本。

本版保留微信 8.0.78（3180）的适配及现有生命周期修复，关闭自动热重载，覆盖安装后需重新启动作用域中的应用。**1.2.3 尚未进行真机安装、登录、双端消息和系统重启验收**，按计划先完成离线检查与 Release 发布，再接入真机验证。详见 [API 102 迁移说明](docs/api-102-migration.md) 与 [更新日志](CHANGELOG.md)。

历史结果：1.2.2 的微信适配代码已在 Android 16 真机验证扫码登录、应用冷启动、整机重启，用户确认重启后安卓与 iPhone 同时在线、消息正常；该结果不等同于 1.2.3 已通过真机验收。见 [微信 8.0.78 实测记录](docs/wechat-8.0.78.md)。

安装包从 [本分支 GitHub Releases](https://github.com/yishisanren/I-Am-Pad/releases/latest) 下载，为已签名、不可调试的 Release 构建。为保持覆盖安装兼容性，沿用 1.2.1 的签名证书；证书名称为 Android Debug，不代表构建类型为 Debug。GitHub Actions 产物仅用于构建检查，未签名，不能直接安装。

## 既有能力与限制

1.2.1 修正了启动 Hook 吞掉原始生命周期方法的问题，并移除了 QQ 自动删除 MMKV 缓存和杀进程的行为。2026-09-11 用户真机反馈：微信、小红书在 Android 16 重启后保持在线且能收到新消息；QQ 尚未完成同口径重启验收。

飞书 7.76.14 仅为实验适配：模拟型号会显示在 iPhone 登录确认页，但手机与模拟 Pad 同时在线未通过。真实鸿蒙平板同样会挤掉该 iPhone，当前没有有效的“真实 Pad 可共存”对照，本分支不宣称已解决飞书多端限制。详见 [飞书真机诊断记录](docs/feishu-phase-2.md)。
