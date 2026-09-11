# I-Am-Pad  

[![Latest Release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/com.houvven.impad?style=flat-square&logo=github&label=Release)](https://github.com/Xposed-Modules-Repo/com.houvven.impad/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.houvven.impad/total?style=flat-square&logo=github&label=Downloads)](https://github.com/Xposed-Modules-Repo/com.houvven.impad/releases)
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

## 1.2.1 状态

1.2.1 修正了启动 Hook 吞掉原始生命周期方法的问题，并移除了 QQ 自动删除 MMKV 缓存和杀进程的行为。2026-09-11 用户真机反馈：微信、小红书在 Android 16 重启后保持在线且能收到新消息；QQ 尚未完成同口径重启验收。

飞书 7.76.14 仅为实验适配：模拟型号会显示在 iPhone 登录确认页，但手机与模拟 Pad 同时在线未通过。真实鸿蒙平板同样会挤掉该 iPhone，当前没有有效的“真实 Pad 可共存”对照，因此 1.2.1 不宣称已解决飞书多端限制。详见 [飞书真机诊断记录](docs/feishu-phase-2.md)。
