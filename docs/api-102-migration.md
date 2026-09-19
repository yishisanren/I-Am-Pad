# libxposed API 102 迁移

版本：1.2.3 / versionCode 15。日期：2026-09-19。

## 依据与兼容性

基于 v1.2.2（`5ac2288e051d623c72080883961a66c8ed5b7c32`），比对 Maven Central 正式发布的 101.0.1 与 102.0.0 源码和元数据：

- [Maven 版本元数据](https://repo.maven.apache.org/maven2/io/github/libxposed/api/maven-metadata.xml)
- [102.0.0 源码包](https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0-sources.jar)
- [102.0.0 Gradle 模块元数据](https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.module)

源码包 SHA-256 为 `c4a5761c2409f411ca0f67983a687fbd9dd9a76250d7a68ab7cb2950c820444e`，与发布元数据一致。本轮官方 GitHub 仓库及文档直链返回 404，因此以已发布制品内的源码为依据，未采用第三方接口摘要替代原始定义。

| 项目 | 迁移选择及原因 |
| --- | --- |
| libxposed 依赖 | compileOnly 与 testImplementation 统一使用 102.0.0；框架实现仍由运行环境提供，不打包入模块 APK |
| 模块 API 声明 | minApiVersion=102、targetApiVersion=102；不宣称旧 API 101 框架兼容性 |
| Hook 注册与原方法执行 | 现用 hook(...).intercept、Chain.proceed、onPackageReady 的调用方式在正式 API 102 中保持可用，不改动已验证业务逻辑 |
| legacy Xposed API | API 102 禁止模块调用 de.robv.android.xposed；检查生产源码及 Release DEX 中的实际方法引用 |
| 内部框架初始化入口 | attachFramework 从单参数变为 (XposedInterface, Runnable)；只更新单测的模拟框架边界，生产模块不调用内部入口 |
| 自动热重载 | autoHotReload=false，继承 API 默认的 onHotReloading=false；继续使用应用冷启动方式加载，避免未实现状态迁移时丢失已安装 Hook |
| 历史修复 | 保留 Application.attach 原生命周期、QQ 不删除缓存、微信 8.0.78 定位及其他应用行为 |

## 离线验收

执行完整测试、Lint 和 Release 构建；检查解析出的依赖版本、APK 的 module.prop、入口与作用域资源、Release 标记、版本号及签名延续性。通过后提交代码，先推送分支和标签，再发布 APK 与 SHA-256 校验文件，并下载回验。

本轮离线检查结果：

- `clean check assembleRelease` 与额外的 `lintRelease` 通过，6 项单元测试全部通过。Lint 无错误，7 项非阻断警告涉及原有 Android 私有接口及其他依赖的版本提示；未扩大到无关依赖升级。
- 编译依赖解析为 `102.0.0`，运行时依赖不包含 libxposed。下载的 AAR SHA-256 为 `423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0`，与官方发布元数据一致。
- Release DEX 无 legacy Xposed 方法引用、无框架内部 `attachFramework` 调用，也没有打包 libxposed API 类定义。APK 保留原模块入口与 10 个作用域包名。
- 本轮没有修改 `app/src/main/java` 中的应用 Hook 逻辑。API 102 的框架加载时序及实际登录效果仍需真机验证。

正式 Release 构建的验证结果及 APK 哈希见 [v1.2.3 Release 说明](https://github.com/yishisanren/I-Am-Pad/releases/tag/v1.2.3)。

## 真机验证：待设备接入

1. 先只读核对运行框架支持 API 102；API 101 环境不升级此版，保留 v1.2.2。
2. 保数据覆盖安装 Release，回读版本 1.2.3 / 15、不可调试标记及安装包哈希。
3. 冷启动微信 8.0.78（3180），核对两个方法正确安装、折叠设备判断命中和现有账号状态；若需重新认证，由用户完成。
4. 确认 iPhone 与安卓均在线且能接收新消息，再进行系统重启，确认免重新登录、双端消息保持。
5. API 升级影响共享 Hook 层，其他作用域应用的运行效果另行记录；本轮不将微信或 1.2.2 的结果作为其他应用的验收结论。

本版按用户要求先发布，**不包含 API 102 真机效果已验证的承诺**。飞书并发登录仍属于未通过的实验能力。
