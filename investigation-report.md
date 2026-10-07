# 4K120fps 调查报告

日期：2026-10-07；设备 PLQ110 / Android17。研究对象为7040300、7420400，9130500仅作静态参考。当前已登录非大会员环境，未读取或修改账号凭据。

## A. 已证明的断点

**两个旧版对 BV1P5411Q7Cj 的当前请求均未得到可播放4K或>100fps representation；原生 IJK 实际打开的是声明为30.303fps的1080P HEVC流。第一个已证明的边界在服务端返回的媒体清单，早于解码和显示。**

这尚未解释服务端为何不给该视频的高帧率源，也不能把所有4K可用性概括为账号限制：相同742请求参数可以取得另一个视频的真实4096×2160@50流。原视频的120fps播放问题尚未修复；没有依据把它归入“120输入被播放器减半”的 Case A–F。

独立本地4K120实验另有明确结果：旧宿主硬解链可以接近120fps呈现；没有触摸时呈现约60fps，持续触摸时约118–121fps。Scene选中物理120Hz仍可能伴随系统/app render60。这是本地对照的显示调度限制，不能替代原视频的根因结论。

## B. 分层证据

|层|直接观察|证据边界|
|---|---|---|
|REQUEST|目标aid469624079/cid734100004；qn80及120；704 fnval464、742 fnval976；fourk=false、preferCodec2、fnver0、forceHost0、download0、teenagersMode0；PlayURLMoss.playView|原生入口白名单字段，未记录认证头|
|SERVER|直接解码protobuf；qn120/116仅有无URL的premium描述；可播放HEVC80/64/32/16均30.303fps，最高1920×1080|Branch B；未获得目标HFR源|
|HOST_SELECTION|资源转换保留同一清单；asset默认80；原生HTTP成功打开的URL指纹唯一匹配qn80|704和742均验证；不匹配/多匹配记AMBIGUOUS|
|IJK_INPUT|HTTP206/error0；目标urlFp808836c3a7b4f2d4或9ceceec0c8c7e6cb|具体输入已关联；不是根据4K标签或qn推测|
|DECODER|系统资源管理器列出1920×1080 HEVC硬解；Smart Auto保持受支持的宿主选择|无目标120fps解码输入，因此不声称目标解码掉一半帧|
|RENDERER|原生播放进度在双模块组合中随时间增长；线上存在两个SurfaceView|进度不等于帧率；线上Surface计数仅作辅助，未归属前不当成视频帧|
|DISPLAY|初始mode2约60Hz；Scene调整后physical mode1约120Hz，无触摸时app/system render仍60|支持模式不等于实际呈现；独立单Surface控制实验另见下文|

完整元数据、URL指纹和时间线：[脱敏证据](debug-evidence/README.md)。初始环境：[baseline.md](baseline.md)。

## C. 请求层差分与排除范围

[modern-client-diff.md](modern-client-diff.md)记录现代VideoVod/PlayerUnite、fnval生成器及Metadata/Device schema差分。所检查路径没有明确120fps capability；不宣称所有元数据通道已穷尽。

四个有静态依据的临时、目标限定实验均未获得HFR：

|时间|单一变化|结果|
|---|---|---|
|16:32:50|fourk false→true|HEVC30.303清单不变|
|16:56:50–16:57:31|preferCodec2→1|改为AVC30.303，premium URL仍缺失|
|17:03:01|fnval976→980；现代生成器明确定义的HEVC bit4|清单不变；该bit并非HFR bit|
|17:22:34|独立原生PlayerUnite RPC，保留原请求标量和原生头|VodInfo仍为1080P HEVC30.303；未替换宿主回复|

全部请求改写、独立RPC和Window/Surface120实验已从最终代码移除。没有扩大VIP作用域、伪造identity/trial/fps/回调或全局刷新率。

当前结果排除了fourk单字段、codec preference单字段、HEVC bit单字段、API名称迁移单独解决本环境问题；没有排除所有服务端授权/能力组合。目标源已是30.303fps，显示器改120Hz无法补出不存在的源帧。

## D. 本地控制与 Scene 重启验证

生成的12秒HEVC文件为3840×2160@120，逐帧验证1440帧、红蓝各720、相邻1439次交替，PTS差8333–8334µs。SHA256：55b830989ceb4937e44d0926749089c020179c423396f04c8bf196f2fe8ea041。[复现方法](debug-evidence/control-source.md)。

DEBUG part-fix使用宿主IjkMediaPlayer打开固定本地文件。硬解须同时启用mediacodec与mediacodec-hevc；实际decoder为c2.qti.hevc.decoder.low_latency，3840×2160。两个旧版均能在约12秒墙钟内播放12秒媒体，原生decode/output估算约119–125。软件对照明显慢于实时120。估算不冒充逐帧事件计数。

单视频Surface的SurfaceFlinger actual-present去重结果：

|条件|结果|
|---|---|
|742，持续触摸，17:21|约118.8–119.8Hz，间隔中位数约8.28ms|
|704，持续触摸，18:07|两个窗口约120.71Hz|
|742，仅part-fix，持续触摸，18:13|约117.83/118.80Hz|
|742，仅part-fix，无触摸稳态，18:15|约60.354Hz，间隔中位数16.569ms|
|强停重启Scene和Bili后，17:43|新应用PID；无触摸仍约60.35Hz|
|742，双模块，后台短触摸序列，18:27|约120.70/117.86/115.99Hz；app refresh持续120|
|742，双模块，停止输入后的独立控制，18:30|稳态约60.358Hz|

Scene的GUI刷新率管理也曾关闭再开启，最后保留开启。用户设置的DFPS120及应用活动/非活动120保留。强停应用不会结束root scene-daemon；没有杀该服务、重启手机或重启LSP。Surface120已按用户要求取消，最终控制器不设置Window/Surface刷新率偏好。

按用户建议另做了[后台模拟触摸对照](debug-evidence/touch-control.md)。MOVE-only未生效，180ms短触摸/100ms间隔可维持120Hz。目标视频20秒实验中，system renderFrameRate三次均120；但qn120回复仍无premium URL，实际打开1080P HEVC、声明30.303，原生解码/output估算约29–32fps。停止脚本后renderFrameRate回60。该实验同时验证了显示侧可解除限制，以及原目标当前还有更早的流供给边界；脚本已停止，没有作为常驻生产策略。

AOSP [FrameTracker](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/surfaceflinger/FrameTracker.cpp)定义三列为desired-present、actual-present、frame-ready；本机OEM环形缓冲长度不同，故去重计算而不拿行数差冒充丢帧数。PixelCopy只观察缓冲颜色，不能独立证明显示帧数。线上双Surface结果不混入本地单Surface结果。

这排除了“所有旧IJK硬解都固定只能60fps”的假设。原目标是否确实逐帧交错承载男女内容、是否历史性发生奇偶相位翻转，仍未证明。

## E. 最小代码修正、验收和交接

bili-part-fix的独立诊断缺陷已保守修正：StreamInfoExtractor不再取同qn第一项；重复或不可读候选返回UNKNOWN，Auto使用宿主原选择，且不执行错误的size/rate能力查询。唯一qn的既有行为保留；fps必须为有限正数，支持合法fps后缀和分数。该缺陷有测试证明，但目标当前回复并未展示同qn多个可播放候选，不能说它就是原问题根因。

bili-hook新增仅DEBUG的只读请求、protobuf、资源、asset、原生HTTP和播放进度观察。URL只存无query/fragment的SHA256截断指纹；缓存有上限，解析失败/溢出/重复匹配均不宣称唯一。part-fix新增显式intent触发的DEBUG本地控制器，完成、暂停、销毁、dismiss、Surface销毁或超时都会释放。Release source set提供空桩，不含研究入口/遥测。

验证：hook48 +part-fix81项JVM测试通过；两仓库DEBUG/release构建通过；release DEX未发现新增研究遥测/控制入口字符串。[回归覆盖与未验证项](regression-results.md)明确区分基础smoke、原生进度和真实帧率。HDR/DRM/直播/PGC与原生fallback未做新的完整真机矩阵，相关既有policy测试通过，不能写成已完成端到端验收。

18:38交接时已恢复7420400及两个release模块，已校验手机安装APK与本机release哈希一致、新进程模块加载成功、本地研究intent无效且无新增研究日志。release三个基础播放样本打开预期codec/尺寸。模拟触摸后台已停止，Scene设置保留；没有提交远程。研究探针源码保留在DEBUG供后续验证。

回滚：本地撤销各仓库研究检查点即可恢复源码；模块也可通过LSPosed GUI单独关闭。未修改用户原有.gitignore、签名文件或远程仓库。Frida双端保留17.22.2，Android17注入仍不稳定，测量未依赖它。

**最后已证明正确的边界**：两个旧版把当前服务端可播放30.303fps目标representation交给原生IJK，双模块下播放进度正常增长。

**下一个有判别力的观察**：通过有定义/赋值/调用链依据的原生请求获得目标真实可播放>100fps representation，或取得同representation的可验证媒体源，再立即追踪其decoder/render/display cadence。继续猜bit、扩大VIP/改identity或重复普通30fps显示实验都不能补上这一证据。
