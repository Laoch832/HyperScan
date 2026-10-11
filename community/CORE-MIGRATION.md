# 原扫描核心迁移 · Laoch832

## 目标修正

用户要求保留原扫描 APK 的本地核心处理能力，移除依赖服务器的功能，以保留下来的核心改造 UI 或迁移进 HyperScan。0.2.0 的独立 Web 扫描原型没有完成该迁移，不能作为原扫描软件核心保留版验收。

原 APK SHA256：`3e6a468befdd9c0808c319f25a2e5d1fc701b38fc5bd4870f573a5d4f826596e`。分析产物保留在仓库外；不把反编译源码自动声明为本仓库 MIT 代码。

## 已检查的实际入口

| 原实现 | 实际观察 | 迁移状态 |
| --- | --- | --- |
| `com.quark.scank.module.SKPDF` | `pictureExportToPdfByPath` 将本地图片路径传给 `PDFExportProcessor.e` | 已接入新 Android PDF 保存桥；真机待验 |
| `com.ucpro.feature.study.pdf.PDFExportProcessor` | 实际选择 `PicturesPDFExportAndroidImp` 或 MuPDF 实现；选择开关依赖 CMS、OfficeProxy；含存储权限、统计、分享依赖 | 已迁移 Android 绘制分支，移除 CMS、统计、分享服务定位器；MuPDF 分支未迁移 |
| `com.ucpro.webar.alinnkit.image.ImageEdgeDetectService` | `detect(Bitmap, ...)` 与 `imageCorrect(PointF[], Bitmap, ...)` 委托 `JsApiImageEdgeDetector` | 已接入模型无关的 QuarKit 透视分支；自动找边的 Wama 模型未迁移 |
| `com.quark.uncommon_ocr.DuGuangOCR` | 加载 `uncommon_ocr`，`nativeInit(String, float)`、RGB `nativeDetect`、`nativeRelease` | 本地 JNI 接口已确认；模型路径、模型可获得性、库依赖和改包名兼容性未确认 |

`libimage_processing_util_jni.so` 在 DEX 中也被 AndroidX CameraX 引用，不能仅凭库名把它当作原扫描增强算法。

## 实施顺序与验收

1. 追踪原拍摄、四角检测、透视校正、滤镜增强、批量页面、PDF 导出和文档存储的调用链，列出真实的模型、原生库及外围依赖。
2. 对混合本地与云端的功能切开依赖边界。保留可运行的本地分支，删除远程转换、STS 上传、登录、云同步和云端 AI 的入口及任务；不能只隐藏按钮。
3. 先让原核心在断网条件下处理真实图片并生成可打开的 PDF，再接 Material 3 Expressive 界面。尚未满足原模型运行条件的功能明确标记待迁移，不以另一个算法实现冒充原核心。
4. 改包名时保留 JNI 必须的类签名，逐项验证模型初始化、文件 URI、Provider 和持久存储。
5. 真机验收覆盖断网冷启动、连续拍照、导入、四角校正、增强、页面排序、保存、重新打开与导出。构建成功和浏览器测试不能替代原核心真机验收。

本次已构建 0.3.0-core-preview：接入原 QuarKit imagePerspective 和迁移 Android 本地 PDF 绘制路径。12 项单元测试、5 项浏览器流程测试、Release 编译及 73 项 JNI 注册签名审计通过；目前没有真机验收通过的声明。

## 动态资源来源与边界

原启动任务 `InitLoadQuarkitTask` 调用 `j71.f`；它内置 arm64 默认包大小5058474、MD5 `e488477c1db50841985b7e7635893030`、2022-04-24下载地址。实际取得文件匹配这些值，另固定 SHA256 `2bc274afe459e118bac113a9f8377397d68c042ff81af97e930dff3f1ad25fa0`。解出 tbb/opencv/upipe/quarkit，加入原 APK 的 MNN、C++运行库，六库摘要见 native-manifest.json。运行时不需要网络获取。这个默认包不等于已验证用户设备当前缓存的最新库。

原自动找边 `JsApiImageEdgeDetector.g` 转入 Wama `runImageAlgo`，配置和模型不在已确认资源中。原 OCR `UncommonOCRDetector` 先准备 `DownloadData.ModelInfo`，再将模型路径传入 `DuGuangOCR.init`，RGB像素交给 nativeDetect。当前未取得对应模型/清单，不能用任意模型冒充可用资源。
