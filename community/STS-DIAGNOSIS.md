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
