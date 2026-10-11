# HyperScan · 本地扫描核心迁移

作者与维护者：[Laoch832](https://github.com/Laoch832) · [官网](https://laoch832.github.io/HyperScan/) · [下载 0.5.0 自动扫描版](https://github.com/Laoch832/HyperScan/releases/tag/auto-scan-0.5.0)

保留原扫描软件可本地运行的核心，删除云端依赖，再接入 Material 3 Expressive 界面。包名 `app.hyperscan.android`，无网络权限、账号、会员、STS、CMS、云同步或远程转换。0.2.0 是被用户否定的独立原型；后续沿用原图像处理与PDF迁移路径。

## 0.5.0 扫描流程

- 应用内 CameraX 实时取景、后置相机、点击对焦、补光和连续拍摄。实时边框来自实际检测；没有检测结果时不画固定“识别框”。自动拍摄开关：连续稳定后拍摄，等纸张离开或明显移动后再允许下一次，避免同一静止页不断拍摄。
- 原 APK `assets/walle/common_multi_detect` 内置分类、四角和六点模型现已找到并校验打包。之前“原自动找边模型不在已确认资源中”的结论遗漏了该资产目录，已更正。当前执行原分类与四角模型，按原 worker 的 RGB、224/416、8像素padding、均值/标准差和坐标映射准备输入/输出。六点模型随包保留，当前不执行书籍曲面/六点拉正。
- 原模型加载/输出异常时记录真实错误，改用明确命名的 HyperScan 本地轮廓检测。相机及页面显示使用的是原模型还是本地轮廓；回退不冒充原算法。轮廓检测不需要模型或网络。
- 拍摄与批量导入默认自动找边并调用原 `QuarKitUtil.imagePerspective` 拉正；可关闭自动处理，也可重新找边、手动调整四角。检测不可靠或校正失败时保留完整原图，并显示该页的具体状态，不强行裁剪。
- 原图一直保留；可恢复原图、裁剪、旋转、灰度、对比增强、去阴影、自适应黑白、多页排序、本地文档保存、中文/英文 OCR、TXT 及 PDF 导出。去阴影/黑白是 HyperScan 的本地实现；OCR 是随包 Tesseract，均不宣称是原模型迁移成果。
- 原 `PicturesPDFExportAndroidImp` 的本地 Android PdfDocument 绘制路径：A4/A5/Letter、按每页图片方向自动横竖页、边距、居中等比缩放和系统文件保存面板。

Android arm64-v8a，最低API24，可覆盖更新同签名HyperScan；不迁移原包/R5文档。相机权限按需请求，无相册读写权限。浏览器保持实时视频拍摄、编辑/OCR/PDF；原模型找边、自动拉正与实时边框目前在Android提供。

## 构建

```sh
npm ci
python3 -m pip install -r requirements-analysis.txt
python3 scripts/setup-offline-android.py
python3 scripts/prepare-original-core.py
npm test
npm run build
npm run test:e2e
JAVA_HOME=/workspace/tools/jdk-21.0.12.1+1 /workspace/tools/gradle-8.14.3/bin/gradle --gradle-user-home /workspace/.local/share/hyperscan-gradle -p android testReleaseUnitTest
bash scripts/build-offline.sh /workspace/artifacts/HyperScan-0.5.0-auto-scan.apk
```

构建脚本拒绝覆盖已有产物。SDK/JDK/Gradle与签名在checkout外；不上传私钥/密码。`prepare-original-core.py` 校验指定原APK SHA256、官方默认QuarKit下载包、资产清单中的模型MD5，生成原组件SHA256清单及忽略的模型/原生库。运行时完全不下载。保留原JNI包名，应用包名独立；OCR用 `.traineddata` / `gzip:false` 避免AAPT剥离压缩后缀。

## 验证与实际边界

通过18项TypeScript单元测试、5项Java找边算法测试、8项浏览器流程测试、Android Release构建、签名/对齐、原组件摘要/依赖、73项QuarKit注册签名与25项AliNN导出接口审计。算法测试覆盖斜纸张、文字、阴影、深色纸张和空白/圆形误检；流程测试覆盖实时视频拍摄、释放相机、权限拒绝、本机存储、图片编辑、OCR与多页PDF。

云环境没有Android真机。**原MNN模型在Android上的实际推理、实时框与取景的坐标对齐、原QuarKit结果及自动拍摄尚未经真机验证**；Java算法测试验证的是本地轮廓回退，不能替代原模型推理验证。主机公开MNN 3.6不能读取原APK定制格式，不改原模型数据来强行通过。保留原APK同版MNN和AliNN桥；初始化失败会使用已测试的本地轮廓检测并显示来源。

原六点/书籍曲面、原OCR模型、原智能增强模型及完整PDF编辑尚未迁移；不提供Word/Excel、云AI和云转换。诊断日志标签：`HyperScanEdges`（模型/检测）、`HyperScanCore`（校正/PDF）、`HyperScanCamera`（相机），不输出图片或凭据。

新写代码采用MIT；原二进制/模型及第三方组件保留原授权和归属，不将它们宣称为我们原创的开源源码。来源见 [核心迁移记录](community/CORE-MIGRATION.md) 和 [资源清单](community/core/native-manifest.json)。
