# HyperScan 内部诊断提供器

作者：Laoch832。诊断版用于调查未登录状态下 PDF 页面“加载失败 / 网络异常”的用户反馈；当前尚未确认根因，也没有修复或跳过任何业务 verify、TLS 或服务端权限判断。

启动后右上方有「日志」按钮。打开后可以查看摘要，重现 PDF 加载错误后再打开，点击「复制日志」提供脱敏后的原文日志。面板每秒刷新；「开始重现」用于一次新的重现。诊断版复用 R2 的开发签名和包名，可覆盖同签名社区测试版；不能直接覆盖原版。

记录范围：

- 本应用进程 logcat 的 HTTP 4xx/5xx、DNS、连接、超时、TLS/证书、verify 与网络错误类别；普通 verify 提及不代表验证失败，Java VerifyError 单独分类。
- 系统网络连接及互联网验证状态。系统显示验证通过不代表业务接口可访问。
- 未捕获异常的类型及最多八个代码位置，继续交给原异常处理器，不吞掉错误。

R3 只保存最近 180 条摘要于进程内存，不写文件或发送到远端；原始日志、请求/响应正文、查询参数、令牌和账号信息不进入面板。HTTP 日志可显示域名，不显示路径或请求头。R4 的详细输出见下方说明。不是网络代理或 HTTPS 抓包工具，也无法保证捕获原生库、其他进程、仅写入文件或未输出日志的请求。可显示面板不等于所有失败都会被记录。

若系统限制本进程 logcat，面板会显示读取不可用或读取结束。此时仍可查看网络状态；进一步定位需要设备端 adb logcat 或内部接口日志，不应猜测后跳过验证。

## 构建和检查

从项目根目录运行（所有路径使用当前环境示例）：

```sh
bash community/setup-tools.sh
bash community/build-diagnostic.sh \
  /workspace/artifacts/quark-original.apk \
  /workspace/artifacts/QuarkScan-Community-10.13.0.455-preview-r2.apk \
  /workspace/artifacts/HyperScan-new-diagnostic.apk \
  /workspace/artifacts/quark-decoded
```

Java 21 编译器模块与经过 SDK XML 摘要验证的 Android API 36 stubs 编译诊断代码，D8 以 min API 23 创建独立 `classes13.dex`。仅重编原 `classes9.dex`，在 MainActivity.onCreate 的原 super 调用之后插入一条 install 调用，保留全部原指令。

审计先校验输入 R2 的原始六处修改，再核对新增诊断类、唯一生命周期插入、资源名称、包名、版本、权限及原始载荷。签名及 16 KiB 对齐分别校验。`LogSummaryCheck.java` 验证错误分类与原始正文/令牌排除。以上均不能替代 Android 实机验证。

## R4：保留脱敏原文

R3 的类别摘要不足以判断故障根因，已移除其泛化的 TIMEOUT / VERIFY 替代文字。R4 使用本进程 logcat threadtime 输出，保留日志时间、PID、TID、优先级、标签、异常信息、接口路径与调用栈。普通 timeout 配置不再作为失败捕获；verify 提及按原文展示，不认定为校验失败。警告/错误和相关上下文可进入面板；不保证没有输出日志的业务请求能够被观察。

HTTP URL 查询参数、用户信息、常见凭据字段、JWT、邮箱、手机号、本地文件路径与长值被脱敏；请求/响应正文省略，仅可保留数字错误码。脱敏覆盖常见日志格式，复制后仍应检查是否包含个人文档或敏感信息。最多保留 600 条，不合并重复故障。新增 Activity 切换记录。

使用步骤：点「日志」→「开始重现」，关闭面板，执行失败操作，再打开「复制日志」。该版本用于收集证据，没有修复网络故障，也没有修改 TLS 或服务端校验。

## R5：STS 解密结果

R5 增加三个 STS 字段的非空结果和被原程序吞掉的安全组件初始化/解密异常类型及数字错误码，不输出凭据内容。`secureNo:4` 只是编号与默认值不同的原程序日志，不能单独证明解密失败。参见 [STS 诊断记录](../STS-DIAGNOSIS.md)。同一开发签名可覆盖 R4；仍需真机重现，不能把静态审计当成功能已修复。
