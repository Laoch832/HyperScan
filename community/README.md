# HyperScan 社区客户端测试版

作者与维护者：[Laoch832](https://github.com/Laoch832)。

基于用户提供、已取得改造授权的 `com.quark.scanking` 10.13.0.455 APK 制作的客户端测试版。保留原应用的业务代码、资源和原生库，通过一个新增的本地构建开关调整扫描模块的会员判定和部分付费入口。

当前是供内部验证的初版，**没有验证原应用的所有功能，也不能宣称全部功能已免费可用**。云端 AI、Office 转换、同步等功能仍依赖原服务的登录、会员状态和配额；此修改没有给后端新增免费权限。

## 输入、输出与工具

- 原包：`/workspace/artifacts/quark-original.apk`。
- 原包 SHA-256：`3e6a468befdd9c0808c319f25a2e5d1fc701b38fc5bd4870f566a5d4f826596e`，与用户提供的 GitHub Release 发布摘要一致。
- 原包证书 SHA-256：`bbe2ff269828a0d922498ee87f65afe769c27d62f489d5c19b9cc6c444c80811`。已通过 APK v1、v2 签名校验；这不构成对其官方发行身份的独立认证。
- 测试 APK：`/workspace/artifacts/QuarkScan-Community-10.13.0.455-preview-r2.apk`。
- 验证报告：同名 `.apk.validation.json` 文件；签名日志保存在本次构建目录。
- 工具：Java 21、Apktool 3.0.3、Android Build Tools 36.0.0、Platform Tools 37.0.1、Androguard 4.1.3、JADX 1.5.6。

## 当前改动

| 位置 | 客户端行为 |
| --- | --- |
| 扫描会员管理 `a.g()` | 社区构建允许本地 SVIP 功能分支 |
| 扫描会员管理 `a.h()` | 社区构建允许本地 VIP 功能分支 |
| 统一扫描会员工具 `d.a()` | 社区构建通过该统一判定入口 |
| `CameraVipPurchasePanelManager.b(...)` | 社区构建不打开这个扫描会员中心入口 |
| `HomeCameraVipBanner.setVisibility(int)` | 社区构建隐藏该扫描会员横幅 |
| 图像增强本地次数检查 `pay.n.a(...)` | 社区构建直接完成本地能力判定，保留后续实际处理及服务端响应 |
| 应用显示名称 | 改为“扫描社区测试版” |

新增 `org.paperscan.community.CommunityAccess.isEnabled()` 作为编译期策略。六处原有方法都保留原逻辑分支；开关关闭时回到原分支。原包的服务端会员响应、账号、登录凭据和接口地址不改写，不制造订单成功响应。

仅重编 `classes10.dex` 和 `classes11.dex`，保留它们的原有类与其余方法。其它 10 个 DEX、63 个原生库、Manifest、资产和布局文件按原字节保留。全局资源字符串池只替换应用名称，保留字符串索引、资源 ID 和其它字符串的原始编码。原 JAR 签名被新开发签名替代，服务提供者注册等 `META-INF` 元数据保留。

扫描模块的统一判定被 56 个类文件引用，VIP 判定被 18 个类文件引用；这说明覆盖了一批路径，不代表已覆盖整个应用。客户端还有 Kotlin、Flutter、H5、CMS 配置和其它服务模块，不把未逐项测试的入口标记为完成。

## 重建

在已有隔离的云任务中使用现有 checkout，无需新建 Git worktree。输入 APK、反编译结果、产物和密钥保存在 checkout 外。

```sh
cd /workspace/Default_Eniveronment
python3 -m pip install -r requirements-analysis.txt
bash community/setup-tools.sh
bash community/build.sh /workspace/artifacts/quark-original.apk /workspace/artifacts/community-new.apk
```

可传入第三个参数 `/workspace/artifacts/quark-decoded` 复用原包解码目录。每次构建建立独立工作目录，拒绝覆盖现有产物；修改前校验原 APK 和六个原始方法的摘要，避免对未知版本盲目套用。

构建步骤：解码、复制两个目标 DEX 的 smali、加入策略和 guard、组装 DEX、从原 ZIP 重封装、对齐原生库、开发签名、签名校验、结构与方法差异审计。原包始终保留。

本地开发签名的加密私钥和密码文件位于 `/workspace/.local/share/paperscan-community/signing/`，权限分别受目录 700、文件 600 保护。重复构建复用该身份；脚本不会输出密码，源码包不含密钥。发布构建需要使用内部认可的签名流程。

## 验证与待办

`verify.py` 检查以下实际 APK 内容：

- 原包所有业务载荷仍存在；只允许两个目标 DEX、资源字符串池和签名清单发生变化。
- 原 DEX 中的全部类和方法仍存在；仅六个指定方法的指令有变化，每个原有指令序列都保留。
- 包名、版本、权限、Activity 和 Service 集合保持一致；显示名称正确。
- 签名由 `apksigner verify` 验证，对齐由 `zipalign -c -P 16 4` 验证。

这组检查用于发现错误打包、误删库、误改方法和 Manifest 变化，**不替代 Android 实机功能测试**。当前 `adb devices -l` 没有设备；也没有可用的 KVM 设备。因此尚未验证启动、拍照、导入、编辑、分享、登录、在线识别或原应用的完整功能。

安装包保持原包名，但使用新的开发证书，不能直接覆盖原签名的已安装版本。请在没有原包的备用测试设备或独立测试环境中验证，避免卸载操作导致原文档丢失。先验证离线启动、拍摄、裁剪、图像增强与 PDF，再验证登录、云端识别、Office 转换和其它原功能；记录仍出现的付费入口和接口拒绝结果。

下一步需要内部社区版后端或明确的免费配额方案，并进行真实设备的逐项功能验证。仅修改本地会员判断不能为服务端计算自动提供资源或额度。

## 社区源码范围

本目录的新写修改脚本、构建代码和审计代码按仓库 MIT 许可证提供。原 APK、反编译产物与第三方 SDK 保存在外部工作目录，按原项目和依赖的授权管理。APK 不等于原项目的完整可维护源码；当前未发布远端开源仓库、Release 或网站。

## 内部诊断版

用户反馈 R2 在未登录状态下 PDF 页面加载失败。新增的[屏幕诊断提供器](diagnostics/README.md)用于记录安全摘要、调查网络与 verify 相关问题，不表示根因已确认或故障已修复。
