# 7.42.0 搜索广告与会员画质缓存研究

日期：2026-10-30。目标：哔哩哔哩 `tv.danmaku.bili`，`7.42.0 / 7420400`；真机 PLQ110，已登录非大会员账号。工作分支：`codex/742-search-ads-download-research`，基于本地 `b8480ff`。本次使用 JADX MCP、MT MCP 原始 DEX 和有线 ADB；未修改宿主 APK。

## 结论

两个问题均已复现并实现本地修复。搜索漏广告的直接原因是遗漏 `search2` 实现。Issue #3 的指定视频 `BV1KY496eEzG` 可以通过原生下载器下载真实 4K，并从原生离线列表播放；本例并非服务端拒绝提供会员视频流。

下载修复包含提交下载和打开缓存两处独立限制。仅消除提交弹窗仍不完整：最初成功下载后，原生离线列表仍拦截播放，本次也补齐了这一检查。适用范围为已登录账号的 UGC 会员视频画质，不能据此推断所有视频、番剧或会员音频都能解锁。

## 项目结构与既有边界

- `BiliHook`：libxposed Modern API 102 入口、精确宿主版本分流和 7.4.0 既有实现。
- `Bili742Hooks`：7.42.0 播放画质事务、同步决策范围和账号登录查询。本次在这里加入下载范围与缓存访问修复。
- `Bili742AdHooks`：各广告功能独立安装；本次补齐搜索的第二套实现。
- `PremiumQualityState` / `QualityDecisionScope`：播放事务与线程内决策状态；`HookRuntime` / `Reflect`：Hook 适配与缓存反射。
- `src/debug` 的 HFR 观察器和 `src/release` 空桩：既有研究设施。本次临时 `Research742Probe` 已移出构建源码，仅在被忽略的本地研究目录保留。
- `src/test` 为业务单元测试。原 `HfrProbeValuesTest` 依赖仅 DEBUG 存在的辅助类，本次移至 `src/testDebug`，使 release 单元测试可独立编译，未把研究辅助类放入 release。

7.4.0 分支与版本白名单保持原有行为。本次不需要修改 BiliPartFix。

## 搜索：同一 APK 有两套实现

原代码只 Hook：

```text
com.bilibili.search.utils.BrpcSearchResultConverterKt.a(List, search.api.SearchResultAll)
```

但设备实际进入 `com.bilibili.search2.main.BiliMainSearchActivity`。静态核查发现 APK 同时具有：

```text
com.bilibili.search2.utils.BrpcSearchResultConverterKt.a(List, search2.api.SearchResultAll)
```

两套转换器均消费 `polymer.app.search.v1.Item`，并将 `CardItemCase.CM` 转换成各自命名空间的 `SearchAdItem`。MT 原始 DEX outline 交叉核对了第二套方法签名。

修改前，“加速器推荐”的真实运行记录为 `search2 input=20, CM=2`，转换后 `output=20, ads=2`；截图中的雷神加速器广告可见。因此不是缺少某个关键词规则，也不是尚未证实的 REST 绕过。

修改后两套转换器分别安装、分别报告失败，复用只识别 `CM` 的过滤规则。运行中实测一批删除 1 张、其他批次删除 2 或 3 张 CM；转换结果广告数为 0。“加速器推荐”首屏、后续分页及“原神”“淘宝”均完成检查。游戏资料卡、官方账号和普通视频保留，不按标题、品牌名称或“广告”文字误删。

## 下载：与播放画质授权相互独立

Issue：[缓存部分画质选项不可用 #3](https://github.com/yylsping/bili-hook/issues/3)。本次复现视频由维护者指定为 `BV1KY496eEzG`。

该设备实际走旧下载面板 `tv.danmaku.bili.ui.videodownload.download.v.e0(List)`。它读取 `r`（所选视频 qn）、`s`（音频下载模式），并在提交前再次调用 `BiliAccountInfo.isEffectiveVip()`。原来的播放授权只覆盖 `N0` / `S1` 和启动画质决策，因而在此返回真实的 `false`。修改前观察到：

```text
download.v submit qn=120 audio=false fullHD=false
download.v accountVip=false
```

随后出现维护者截图中的会员画质弹窗。新版下载面板 `downloadv2.w.e0(List)` 在同一 APK 中具有对应逻辑；其字段与方法由原始 DEX 确认。

修复仅在这两个精确提交方法执行期间，针对非空选择、已登录、视频模式和已知会员 qn `112 / 116 / 120 / 125 / 126` 打开线程内授权。嵌套的拒绝帧不会继承外层许可，返回或原方法抛异常后清理。登录、网络提醒、冻结会员判断、宿主可下载性判断及真实下载器/解析器均继续执行。字段不可读或 qn 不认识时保留原行为。

没有全局修改 VIP 模型、请求 qn、`download` 参数、媒体 URL、服务器回复、下载完成状态或缓存 JSON。实际请求由宿主生成：

```text
PlayURLMoss.playView
Aid=117167546697715 Cid=41330016848
Qn=120 Fnval=976 Fourk=false ForceHost=2 PreferCodecTypeValue=2 Download=2
```

## 缓存播放：必须补齐第二道本地检查

仅完成下载提交修复时，文件确实下载完成，但离线列表点击仍提示“此视频是大会员专享，请登录大会员账号进行观看”。调用链为：

```text
offline.g1.s(model.d)
  -> videodownloader.resolver.downloaded.c.h(model.d)
  -> model.d.h()：可播放性
  -> model.d.c()：会员音频检查
```

`com.bilibili.offline.c.h()` 和 `VideoDownloadAVPageEntry.h()` 都对 UGC 会员视频 qn 作本地会员限制；`l1.N` 构造播放列表时还会对实际 entry 再检查一次，故两种模型都必须覆盖。

本次只在这两个方法原结果为 false 时，确认已登录、UGC `Page` 或 UGC 合集 `DramaVideo` 类型、已知会员视频 qn、标准音频 `audioQuality=0` 后允许视频访问。番剧 `Episode`、会员音频和未知类型不进入修复。缓存文件和模型字段不被改写，账号 VIP 查询也不因缓存模型读取而改变。

## 真机结果

| 验证层 | 直接观察 |
| --- | --- |
| 下载完成 | `is_completed=true`，`video_quality=120`，`type_tag=120` |
| 字节数 | `downloaded_bytes=total_bytes=80,148,351` |
| 下载索引 | 视频 `id=120`，`3840×2160`，`frame_rate=29.963`；音频 `id=30280`，不是 Hi-Res |
| 文件 | `video.m4s` 为 72,364,102 字节；FFmpeg 从 280 秒处成功解码一帧，HEVC、3840×2160、约 29.97fps |
| 原生缓存播放 | 从离线列表打开该视频，显示“离线缓存 1/1”，播放进度持续前进 |
| 本地输入 | `:ijkservice` 的打开文件描述符分别指向该视频缓存的 `120/video.m4s` 与 `120/audio.m4s` |
| 实际解码 | 系统媒体资源列出 `c2.qti.hevc.decoder`、`3840×2160` |

视频文件 SHA-256：`dc1f7bf9d6ae2983522cff8c93bdf0a25b1acdd14606b828e89691ffe3156b08`。原生缓存播放期间宿主还可能后台请求线上元数据；本地文件描述符证明实际打开了缓存输入，不以“没有网络请求”作为离线播放的判断标准。

## 验收范围与交付

最终构建、测试与 release 真机复查结果见[脱敏验收记录](debug-evidence/search-download-validation.md)。源码只在精确版本 7420400 分支增加业务逻辑。本次未切换宿主版本，7.4.0 的回归依据是既有测试和分流未变，并非新一轮 7.4.0 真机验收。

本次没有进行付费番剧、会员音频、所有 HDR/杜比画质的完整端到端矩阵；针对其隔离条件有单元测试，不把这些测试写成真机解锁成功。没有服务端目标流时仍由宿主处理失败或降级。

本地截图、原始 DEX 结果、临时探针、安装回滚包与媒体校验文件留在被忽略的 `.debug/coverage-download/`，未加入提交。手机侧临时产物在 `/data/local/tmp/processing/`。报告只记录公开视频标识、内部符号和白名单标量，没有账号凭据、Cookie、授权头或签名媒体 URL。

本地验收构建沿用 `1.7.0 / versionCode 13`，不代表新版本已发布。全程未重启手机或 LSPosed、未调整模块开关/作用域、未写配置数据库，未修改远程仓库。维护者人工验收及明确远程写入授权仍是发布前提。
