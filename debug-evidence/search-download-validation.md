# 7.42.0 搜索与缓存修复：脱敏验收

日期：2026-10-30，时间均为 Asia/Shanghai。真机 PLQ110，宿主 `7420400 / 7.42.0`，已登录非大会员。报告：[研究说明](../search-download-research.md)。

## 因果与设备记录

| 时间 | 观察 |
| --- | --- |
| 15:30 前后 | “加速器推荐”首屏可见雷神广告，现有模块已开启 |
| 15:33:08 | 只读基线探针：`search2 input=20, CM=2; output=20, ads=2` |
| 15:34:26 | 指定视频的原生 `download.v.e0`：`qn=120, audio=false, fullHD=false, accountVip=false`，出现会员画质弹窗 |
| 15:37:42 | 提交范围修复后：`downloadScope=true, original=false`；宿主显示原生流量提醒，会员弹窗不再阻止提交 |
| 15:37:55 | 原生下载 RPC：`Qn=120, Download=2, ForceHost=2`；未改写请求 |
| 15:38–15:39 | `is_completed=true`，`80,148,351 / 80,148,351` 字节，实际视频索引 qn120 / 3840×2160 |
| 15:39 | 首轮离线列表点击发现第二道本地会员限制，未将“下载成功”冒充完整修复 |
| 15:42:58 | 缓存列表模型与实际 AV entry 两处 UGC 判断命中修复，原生缓存播放器打开 |
| 15:43 后 | 搜索首屏/分页多批实测删除 CM，单批 1–3 张；输出 `ads=0` |
| 15:45 | “加速器推荐”“原神”“淘宝”检查；正常游戏资料卡、官方账号及普通视频仍可见 |
| 15:46 | IJK 服务进程打开文件描述符指向该缓存 `120/video.m4s`、`120/audio.m4s`；原生进度 272070 → 274084 → 276081ms，`playing=true` |
| 15:51 | 最终 release 进程初始化 `30 hooks, release`，无 unavailable；搜索与原生缓存播放复查通过，本地音视频文件描述符仍存在 |

公开样本：`BV1KY496eEzG`，aid `117167546697715`，cid `41330016848`。实际音频为普通 DASH `30280`；视频标题中的 Hi-Res 不等于本次取得 Hi-Res 音源。

## 媒体文件的独立检查

从该样本下载目录复制实际 `video.m4s` 到手机 `/data/local/tmp/processing/`，再拉到项目忽略目录分析。文件 72,364,102 字节，SHA-256：

```text
dc1f7bf9d6ae2983522cff8c93bdf0a25b1acdd14606b828e89691ffe3156b08
```

FFmpeg 从 280 秒处读取并解码一帧成功，解析结果为 HEVC、3840×2160、约 29.97fps。真机原生缓存播放时，媒体资源管理器列出 `c2.qti.hevc.decoder`、3840×2160。这些检查独立于 UI 的“4K”文字。

## 自动化检查

JDK 临时指定为 `C:/Program Files/Microsoft/jdk-17.0.20.8-hotspot`，没有修改系统默认 JDK。

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease lintRelease
```

| 检查 | 结果 |
| --- | --- |
| Debug JVM 测试 | 55 项，0 failure / error / skipped |
| Release JVM 测试 | 50 项，0 failure / error / skipped |
| Debug / Release APK 构建 | 成功 |
| lintRelease | 0 errors、8 warnings；既有非 SDK 反射、Context 静态字段和工程元数据提示 |
| APK 签名 | apksigner 验证通过，APK Signature Scheme v2 |
| Release 隔离 | DEX 中不存在 `Research742Probe`、`Bili742Research`、`BiliHfrProbe`、`HOST_PARSED_REP`、临时原生 source 观测或 CM 计数日志字符串 |
| 打包内容 | 没有签名私钥、手机截图、研究日志或缓存媒体文件 |

新测试覆盖 CM 过滤的顺序/不可变响应/未知卡片保留，登录和 qn 白名单，音频/PGC 隔离，嵌套拒绝帧、线程隔离，以及原生下载抛异常后清理授权并原样传播异常。已有版本白名单、播放画质事务和广告测试继续通过。

首轮 release 测试暴露既有 HFR 测试位置错误，已将仅依赖 DEBUG 类的测试移动到 `src/testDebug`。首轮 lint 暴露本机被忽略的 `local.properties` 中 SDK 盘符未转义；按 Java properties 语法正规化为 `D\:/AndroidSDK` 后，因 lint 缓存沿用旧诊断，又用 `lintRelease --rerun-tasks` 刷新验证通过。没有因此修改业务规则或压制 lint。

## 最终交付与现场状态

本地签名包：`dist/bili-hook-742-search-download-research.apk`，40,297 字节；沿用 `versionName=1.7.0 / versionCode=13`，是待人工验收的研究修复包。

APK SHA-256：

```text
d88dcf095f24e28b1dd1db8c3da1a619c4c566583fc32d8e92785ab37a057074
```

已按 `adb push` 到 `/data/local/tmp/processing/` 后通过 `su -c 'pm install -r -t --user 0 ...'` 覆盖安装。拉回最终安装的 APK 与上述文件哈希相同。安装后仅强停并重开宿主，模块持续启用；没有重启手机或 LSPosed、没有修改模块配置数据库。

最终设备保留本次创建的该视频完整 4K 缓存，便于维护者从离线缓存页面人工验收。本次没有删除用户既有缓存。BiliPartFix 保持原安装和原配置，未修改其仓库或安装包。

本地可见截图包括 `.debug/coverage-download/search-before.png`、`cache-gate-before.png`、`cache-complete.png`、`release-search.png`、`release-cached-play.png`；不随仓库提交。图像、原始 DEX、APK 回滚包与媒体只作本地证据，报告不包含凭据或签名媒体 URL。

未执行远程 Git 写入、PR/issue 更新或发布。以上是自主验收结果，等待维护者人工验收与明确发布授权。
