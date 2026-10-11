# HyperScan · 原扫描核心迁移

作者与维护者：[Laoch832](https://github.com/Laoch832) · [官网](https://laoch832.github.io/HyperScan/) · [核心迁移测试包](https://github.com/Laoch832/HyperScan/releases/tag/core-preview-0.3.0)

目标是保留原扫描软件可本地运行的核心能力，拆除云端依赖，再迁移到采用 Material 3 Expressive 界面的 HyperScan。包名 `app.hyperscan.android`，没有网络权限、登录、会员、STS 上传、CMS 拉取、云同步和远程转换。**0.3.0 是第一批核心迁移测试包，不是全部核心已经迁移完成的版本。**此前 0.2.0 属于独立扫描原型，不符合原核心保留目标。

## 本次实际接入

- 原 `JsApiImageEdgeDetector.j` 的本地透视分支：保留原 JNI 类名，调用 `QuarKitUtil.imagePerspective`；原程序内置默认动态库及依赖随 APK 打包，运行时不下载。四个角由用户拖动或用方向键调整。没有用自写透视算法代替原实现。
- 原 `PicturesPDFExportAndroidImp` 本地绘制路径：迁移 Android `PdfDocument`、按单页图片方向选择横竖页、百分比边距和居中等比缩放；去掉 CMS、统计、权限服务和云端预览的外围依赖。支持 A4/A5/Letter、自动/横向/纵向、0–20% 边距，通过系统文件保存面板导出。
- 校正、裁剪、旋转保持原图；可恢复原图。校正结果缓存，后续滤镜操作不重复调用原生校正。失败不覆盖当前页，PDF 遇到坏页直接报错，不默默漏页。

原 QuarKit 库是原 APK 内置下载配置 `j71.f` 指向的 2022-04-24 默认资源，并非从用户设备当前缓存取得的最新版本。下载包与原配置的大小/MD5匹配，另外固定 SHA256；六个库、66 个 JNI 注册签名和非系统库依赖均审计。它们按原组件授权管理，不自动变成 MIT 源码。来源、摘要及迁移范围见 [核心迁移记录](community/CORE-MIGRATION.md) 和 [资源清单](community/core/native-manifest.json)。

## 保留的工作空间与尚未迁移的核心

拍照/图片导入、多页排序、本机文档库、矩形裁剪、旋转、灰度和对比度增强、中文/英文 OCR 与 TXT 导出继续可用。单图上限25 MB，输入长边缩放至2400像素。OCR 当前仍是随包 Tesseract 过渡引擎，灰度/对比度增强仍是原型实现，**不计为原核心迁移成果**。

原自动找边目前进入 Wama 模型运行链，原 `UncommonOCRDetector` 也依赖另行准备的模型。原自动找边、原 OCR、智能增强模型和完整 PDF 编辑尚未迁移；Word/Excel、云 AI、云转换入口不提供。0.3.0 不宣称拥有原应用全部离线能力。

APK 仅 arm64-v8a，可更新同签名的 HyperScan 0.2.0，本机 HyperScan 文档库保持同名。与原包/R5并列安装，不迁移原包文档。浏览器支持已有编辑/OCR及 PDF 流程；原透视校正仅在 Android 应用提供。

## 开发和构建

```sh
npm ci
python3 -m pip install -r requirements-analysis.txt
python3 scripts/setup-offline-android.py
python3 scripts/prepare-original-core.py
npm test
npm run build
npm run test:e2e
npm run dev -- --port 5173 --strictPort
```

`prepare-original-core.py` 校验指定原 APK 和官方动态库资源，把原生库放到忽略的 `android/app/src/main/jniLibs`。原 APK 默认使用 `/workspace/artifacts/quark-original.apk`；文件缺失时从用户提供的公开 Release 下载并核对已知 SHA256，也可用 `--apk PATH --cache PATH` 指定位置。原 APK、第三方二进制和密钥不提交 Git；复现构建需要这些原授权资源及其获取地址仍可用。

JDK21、SDK36、Build Tools35/36和Gradle8.14.3由脚本固定并校验。签名/审计构建：

```sh
bash scripts/build-offline.sh /workspace/artifacts/HyperScan-0.3.0-core-preview.apk
```

脚本拒绝覆盖已有产物，复用 checkout 外的开发签名。不得把密钥/密码上传到仓库。`postinstall` 复制 OCR 的 Worker、WASM 和 `.traineddata`；使用 `gzip:false` 防止 AAPT 剥离 gzip 后缀导致加载失败。

## 验证范围

本次通过12项单元测试、5项浏览器流程测试、Android Release 编译、APK签名/对齐审计，以及原库摘要、依赖和66项JNI注册签名检查。浏览器测试覆盖混合横竖页PDF、PDF设置保存、裁剪/旋转、文档保存与排序、本地OCR（阻断外部请求）、TXT、手机深色模式。

**云环境没有 Android 设备。原库加载与真实透视结果、Android原生PDF、拍照和保存回调尚未经真机验证。**静态 JNI 匹配不能证明算法已在设备上跑通。首次测试建议断网导入一张斜拍纸张，调整四角、保存/重新打开、导出PDF；失败时保留 `HyperScanCore` 标签的原始错误栈。日志不输出图片、凭据或请求正文。

历史 APK 诊断保留在 [community](community/README.md)，其安全 SDK 和业务后端不加入当前迁移包。项目新写代码采用 MIT；原组件及第三方依赖保留其授权和归属，不能将原二进制宣称为我们原创的开源源码。
