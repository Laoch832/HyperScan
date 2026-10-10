# HyperScan

作者与维护者：[Laoch832](https://github.com/Laoch832) · [项目主页](https://laoch832.github.io/HyperScan/)

社区扫描项目：包含独立扫描原型，以及获得用户授权的 Android 客户端修改、构建和审计工具。

测试版下载见 [GitHub Releases](https://github.com/Laoch832/HyperScan/releases/tag/community-preview-10.13.0.455)。

原 APK 的社区测试包通过了签名和静态审计，尚未实机测试；云端功能仍需要服务端配额。详见 [社区测试版说明](community/README.md)。

# HyperScan · Laoch832 · 扫描社区版原型

仓库包含两部分：已验证的独立 Web 扫描原型，以及后来基于授权 APK 编写的社区客户端修改、重建与审计工具。原包现已下载、校验并分析。请先阅读 [客户端社区测试版说明](community/README.md)，查看实际 APK 改动与实机验证限制。

最终目标：在授权范围内保留原应用功能，移除收费入口与会员限制。独立原型用于编辑、识别和导出流程；原 APK 的客户端测试版通过本地社区构建开关调整六个关键入口。全功能免费可用仍需云端授权方案和实机验证。

## 当前功能

- 导入 JPEG / PNG / WebP；手机通过系统图片选择器拍摄。单图限制 25 MB，长边缩放到 2400 像素以控制内存。
- 非破坏式百分比矩形裁剪、90° 旋转、灰度与文档对比度增强。
- 多页排序、移除、命名、IndexedDB 本地保存、搜索、删除。
- 本地中英文 OCR，可修改识别结果、导出 TXT。
- 多页 A4 图片 PDF 导出；Android 工程通过系统分享导出文件。
- 无登录、订阅、付费按钮或水印。

尚未实现透视矫正、自动边缘检测、表格与公式识别、Word / Excel 导出、云同步及原应用的服务端 AI 能力。PDF 为图片 PDF，未嵌入可搜索文字层。Android 实机拍摄、分享和 OCR 仍需实机验证。

## 开发与验证

使用 Node.js 24 和 npm。仓库已有锁文件：

```sh
cd /workspace/Default_Eniveronment
npm ci
npm test
npm run build
npm run test:e2e
npm run dev -- --port 5173 --strictPort
```

`npm ci` 的 postinstall 将 OCR worker、WASM 与中文/英文训练文件从已安装依赖复制至 `public/ocr/`；运行时不调用外部 OCR 服务。`public/ocr/` 和 `dist/` 为生成目录，不提交。依赖下载仍需要 npm registry 访问。

端到端测试在云机器优先使用 `/usr/bin/chromium`，其他机器运行 `npx playwright install chromium` 后执行。测试覆盖图片导入、实际裁剪尺寸、旋转、增强、本地保存与重载、排序、多页 PDF、真实 OCR、TXT 导出、移动屏宽与删除持久化。

使用环境的现有 checkout；每个云任务已有隔离环境，无需创建 Git worktree。开发服务器进程必须在新任务中重新启动。

## Android

工程包名为 `org.paperscan.community`，与原应用分开。构建需要 JDK 21、Android SDK Platform 36 和对应 Build Tools，Gradle wrapper 固定为 8.14.3。当前云环境具备 Java 运行时中的编译模块、Android 打包/签名工具，但未配置完整的 Android SDK Platform 36，因此未对这个 Capacitor 工程进行 Gradle APK 构建。基于原包的社区客户端 APK 通过独立的 smali 重建流程生成，详见 community/README.md。

```sh
npm run android:sync
cd android
# 本地设置 ANDROID_HOME 与 JAVA_HOME 后：
./gradlew assembleDebug
```

输出：`android/app/build/outputs/apk/debug/app-debug.apk`。这是调试签名包；发布签名需要单独配置，不把签名密钥放入仓库。

## 原 APK 分析准备

用户提供的二维码对应下载引导页：

`https://scank.quark.cn/blm/scank-homepage-691/index?entry=kkscan@product_pcwp`

该引导页在初始网络设置下被云代理拒绝，链接不是 APK 文件直链。用户随后提供的 GitHub Release APK 已成功下载到 `/workspace/artifacts/quark-original.apk`；目前以该文件作为分析与重建输入。

已安装 JADX 1.5.6（下载包已通过官方发布的 SHA-256 校验），以及 Androguard。

只读 APK 清单工具：

```sh
python3 -m pip install -r requirements-analysis.txt
python3 scripts/inspect-apk.py /workspace/artifacts/original.apk --output /workspace/artifacts/apk-inventory.json
```

安装包、反编译输出和分析报告应存放在 checkout 外的 `/workspace/artifacts/`。工具记录 SHA-256、包名、版本、签名方案、权限、组件、DEX 和原生库清单；不会修改安装包。已用 Androguard 示例 APK 和用户提供的原应用 APK 验证清单解析。原包分析报告位于 `/workspace/artifacts/quark-original-inventory.json`。JADX 路径：`/workspace/tools/jadx-1.5.6/bin/jadx`；可用 `bash scripts/setup-analysis.sh` 重建分析工具环境。

后续需据实际 APK 区分本地功能与内部 API 依赖；服务端限制不能仅靠修改本地界面解决。

本项目 MIT 许可证只覆盖此处新写代码。依赖保留各自许可证；不包含原应用代码、图标、商标、SDK 或安装包。

## 项目介绍页

`docs/` 是独立的模块介绍网站，采用米白、暖橙和衬线字体，提供六个模块入口、交互工作流及测试版下载。GitHub Pages 使用 `.github/workflows/pages.yml` 部署，首次需仓库所有者在 Settings → Pages 选择 GitHub Actions。
