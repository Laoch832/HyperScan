# HyperScan · 离线扫描

作者与维护者：[Laoch832](https://github.com/Laoch832) · [官网](https://laoch832.github.io/HyperScan/) · [下载 APK](https://github.com/Laoch832/HyperScan/releases/tag/offline-0.2.0)

HyperScan 是独立开源的扫描应用，采用 Material 3 Expressive 设计风格、原创图标和包名 `app.hyperscan.android`。扫描、图片处理、中英文 OCR、文档保存和 PDF 导出使用独立源码实现。Android 离线构建没有网络权限，也不包含登录、会员、云同步、云端转换或原版安全 SDK。

## 功能

- 拍摄或导入 JPEG / PNG / WebP；单图上限 25 MB，长边缩放至 2400 像素。
- 非破坏式矩形裁剪、旋转、灰度和文档对比度增强。
- 多页排序、移除、命名、本地保存、搜索和删除；编辑后点击保存按钮保留文档。
- 本地中文/英文 OCR，编辑识别结果并导出 TXT。Worker、WASM 和模型随 APK 提供。
- 多页 A4 图片 PDF。Android 使用系统保存面板选择保存位置，无需广泛存储权限；取消保存不会显示成功。
- 手机与桌面布局、系统深色模式、减少动态效果偏好和键盘焦点反馈。

本版没有自动边缘检测、透视矫正、表格/公式识别、Word/Excel 转换或可搜索 PDF 文字层。APK 与 R5 并列安装，不会迁移原应用中的文档。系统相机和文件选择器由 Android 提供；选择其他应用或云盘是用户自己的操作，HyperScan 核心流程不依赖业务服务器。

## 开发

Node.js 24，使用锁文件安装：

```sh
npm ci
npm test
npm run build
npm run test:e2e
npm run dev -- --port 5173 --strictPort
```

`postinstall` 从安装依赖复制本地 OCR 资源至 `public/ocr/`。引擎使用 Tesseract.js，PDF 使用 jsPDF，Android 外壳使用 Capacitor；这些开源依赖与模型已经打入安装包，运行时不需要联网下载。各依赖仍遵循其自身许可。开发安装和构建下载需要联网。

端到端测试覆盖实际图片处理尺寸、排序和重载、两页 PDF 内容、本地 OCR（阻断外部请求）、TXT 导出、手机布局、深色模式和删除持久化。云环境优先使用 `/usr/bin/chromium`；其他环境运行 `npx playwright install chromium`。

## Android 构建

标准工具链：JDK 21、Android SDK Platform 36、Build Tools 35/36、Gradle 8.14.3。Wrapper 固定版本及官方 SHA256。

```sh
npm run android:sync
cd android
# 配置 JAVA_HOME 和 Android SDK 路径后：
./gradlew assembleRelease
```

输出未签名包：`android/app/build/outputs/apk/release/app-release-unsigned.apk`。本地签名打包脚本（Linux x64，Python 3.12+，需 androguard/loguru）：

```sh
python3 scripts/setup-offline-android.py
bash scripts/build-offline.sh /workspace/artifacts/HyperScan-0.2.0-offline.apk
```

脚本校验上游工具包摘要、构建、对齐、签名，再检查包名、网络权限、备份设置和 OCR 资源。工具、机器代理配置与开发签名保存在 checkout 之外，不放入 Git。可使用 `HYPERSCAN_TOOLS_ROOT` 和 `HYPERSCAN_SIGNING_DIR` 指定自己的工具及签名目录；重用签名才能更新同一安装。公共发布附 APK、源码 ZIP、审计 JSON 和 SHA256。

## 验证范围

0.2.0 已通过 TypeScript/生产构建、4 个单元测试、4 个浏览器端到端测试和 Android Release 编译。发布包还校验签名、对齐、身份、无网络权限和随包 OCR 资源。云环境没有连接 Android 设备，因此原生相机、系统保存面板及 Android WebView OCR 仍需真机验证；浏览器验证不能替代这一部分。

## 历史实验

早期授权 APK 的诊断与修改记录保存在 [community](community/README.md)，旧诊断包属于历史实验。当前离线 APK 不包含这些原版代码、接口或功能。历史安全组件问题记录见 [STS 诊断](community/STS-DIAGNOSIS.md)。

原创源码采用 MIT 许可；使用环境现有 checkout 开发，不需新建 worktree。
