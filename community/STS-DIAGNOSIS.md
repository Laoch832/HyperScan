# STS 诊断记录 · Laoch832

用户提供的 R4 日志（01:24:52–01:24:58）已进入 STS 响应处理，未在该片段再次出现此前的 SSLKiller 信任管理器错误。

## 已确认

- `HttpStsTokenHelper$StsTokenRequester.b` 仅在 JSON 业务 `code == "0"`、找到目标 bucket 且有效期大于零时进入三个字段的解密。日志中的源码行 117、127、137 分别对应 `accessId`、`accessSecret`、`securityToken`。因此这些调用表明响应进入成功业务分支；不能将另一个 UPaas 通道的 401 当成 STS 状态码。
- `EncryptHelper.decryptBytesByKey` 从密文头读取 short 编号。编号不同于默认值时主动创建 `Throwable` 打印 `secureNo`，随后继续查找对应密钥别名并调用安全组件。`EncryptModel` 已有编号 4 的映射。因此 `secureNo:4` 既不是 HTTP 错误码，也不单独证明解密失败。
- 实际解密使用 Alibaba SecurityGuard 的 `staticBinarySafeDecryptNoB64`。原程序 `handleDecryptException` 只调用异常的 `toString()` 而没有输出；另一个字符串解密 catch 同样没有输出，并返回空字符串。R4 因而不能区分异常、空结果和成功。

## R5 增加的观察点

保留原方法所有指令，只增加四个诊断调用：安全组件初始化异常、解密异常、字符串解密 catch，以及三个 STS 字段解密后的是否非空结果。输出异常类型和 SDK 数字错误码，不输出异常消息、密文、明文、密钥别名或凭据内容。日志调用失败不影响原调用流程。

`STS_RESULT response_code=0` 取自观察点所在的已确认成功分支，不是伪造或修改服务器响应。三个 `*_present` 只说明字段非空，不代表凭据有效、上传成功或 PDF 转换成功。

## 未确认

当前片段未包含解密返回值及后续上传/PDF 最终失败。重签名兼容性、登录必要性和服务端额度仍不能由该片段定论。R5 是诊断版，未声称修复；需要真机重现后根据上述结果和后续错误定位。不得用空凭据、假成功结果或关闭 TLS 验证替代修复。

## R5 真机结果：安全数据文件不匹配

用户随后提供的 01:35:55–01:36:05 日志重复出现：

```text
CRYPTO_DECRYPT_EXCEPTION sdk_code=302 exception_type=com.alibaba.wireless.security.open.SecException
STS_RESULT response_code=0 accessId_present=false accessSecret_present=false credential_present=false
```

该 APK 自带 `com.alibaba.wireless.security.SecExceptionCode` 明确定义 `SEC_ERROR_STA_DATA_FILE_MISMATCH = 0x12e`，即十进制 302。因此现在可以确认：STS 响应进入业务成功分支，但本地安全组件解密发生数据文件不匹配，三个解密后凭据均为空。该结果会阻断正常的凭据上传流程；这仍不代表其后的全部 PDF 问题都已排除。

原 APK 与已发布 R5 逐字节比较，下列 SDK 文件保持一致：

- `lib/arm64-v8a/libsgmain.so`
- `lib/arm64-v8a/libsgmainso-6.8.260404.so`
- `lib/arm64-v8a/libsgmainso.version.so`
- `res/drawable/yw_1222.jpg`
- `res/drawable/yw_1222_mwua.jpg`

社区版签名已变更，而安全数据文件仍来自原版，所以签名与 SDK 安全数据的匹配关系是重点待核查项。302 本身不能证明具体是哪一项绑定不匹配，也不能直接等同于“服务器拒绝社区版签名”。

### 内部接入所需条件

请安全 SDK/服务端维护团队核实当前应用包名 `com.quark.scanking`、SDK 版本和社区版签名 SHA256 `1752b2d0c8a00838a7c79e84224fed34064888a7cdc1f280b872e2944be045fb` 的配置，按 SDK 支持的流程提供匹配的安全数据资源，并确认 STS 返回的密文可由该客户端配置解密。如果需要重新生成签名配置，由内部签名流水线完成；不要将签名私钥或 STS 凭据放入公共仓库。

拿到匹配配置后，在隔离构建目录替换指定资源，保留诊断观察点，重新签名和审计。真机验收需看到三个 `*_present=true`、实际上传成功和 PDF 加载/导出成功；仅消失 302 不足以宣称完整修复。当前没有匹配的内部 SDK 配置，因此没有发布宣称修复的 R6。
