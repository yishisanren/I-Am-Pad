# libxposed API 102 迁移

版本：1.2.3 / versionCode 15。迁移日期：2026-09-19。真机验收日期：2026-09-20。

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
- 本轮没有修改 `app/src/main/java` 中的应用 Hook 逻辑。离线检查不代替真机结果；后续实测与用户验收见下文。

正式 Release 构建的验证结果及 APK 哈希见 [v1.2.3 Release 说明](https://github.com/yishisanren/I-Am-Pad/releases/tag/v1.2.3)。

## 真机验收：通过（2026-09-20）

环境：OPPO PME110，系统版本 `PME110_17.0.0.100(SP07CN01)`，系统属性报告 Android 17 / API 37；框架 LSPosed IT 2.2.0-it（7881）；微信 8.0.78（3180）。本次环境不同于 1.2.2 的 Android 16 验收，不混用两轮结果。

| 检查项 | 结果 | 依据 |
| --- | --- | --- |
| 框架 API | 支持 API 102，内部初始化入口为双参数版本 | 工具读取设备上的 framework.dex，LIB_API=102、getApiVersion() 返回 102，attachFramework 接受 XposedInterface 和 Runnable |
| Release 覆盖安装 | 从 1.2.2 / 14 成功升级至 1.2.3 / 15，保留数据 | 安装返回 Success，系统包管理器回读版本一致，包标志无 DEBUGGABLE |
| 安装包一致性 | 设备已安装 APK 与 GitHub 正式发布包哈希一致 | 工具计算设备 base.apk SHA-256，与本地及 GitHub 制品一致，见下文 |
| 微信冷启动与 Hook | COLD / Status ok；主进程加载模块版本 1.2.3，两个 Hook 安装成功 | 14:11:12 日志记录 b41.j1.a(String, String, Continuation) 和 com.tencent.mm.ui.gk.R()；随后命中 isFoldableDevice_method |
| 账号与安卓消息 | 冷启动后直接进入已登录会话列表，观察到新消息记录 | 工具查看升级前后页面；未退出账号、清理数据或重新扫码 |
| 双端在线与新消息 | iPhone 与安卓同时在线，新消息正常 | 用户对双端验收问题明确回复“确认” |
| 整机重启 | 用户确认重启也正常，API 102 测试没有问题 | 用户明确补充“不做重启测试的意思是重启也没有问题，102的测试没问题。可以发布正式包。”本轮未再次执行整机重启，不将用户确认写成助手独立重启实测 |

已安装及正式发布 APK 的 SHA-256：`8646c56e6ea4f7fb555a478c51f69472ace042b4ab42f6f34ecdac82c9aca5c4`。

读取的 framework.dex SHA-256：`27f52e03cf9ec8fa15e295a06dcdb8031318a61f327bab873ebc521fc9ad2bd0`。

### 正式发布与范围

按用户确认将 API 102 标记为真机验收通过。沿用已经安装验证的 v1.2.3 Release APK、签名及源码提交 `14178f8ed57d8dd5fba28c9be30cb766c258ce6d`，不重建安装包、不移动 v1.2.3 标签；后续提交仅更新验收文档与发布说明。

本轮验证的是当前设备、框架和微信版本的升级使用；未重新走全新扫码登录流程，未重新验收其他作用域应用，也不外推长期稳定性。飞书并发登录仍属于未通过的实验能力。
