# Sanitized HFR evidence

Times: Asia/Shanghai, 2026-10-07. Raw logs, screenshots, APKs, media, fonts, credentials and device/account identifiers are excluded. URL fingerprints are the first 16 SHA-256 hex characters after removing query and fragment. CDN host changes alter fingerprints; unmatched URLs are never assumed to be the first same-qn candidate.

## Target server inventory

BV1P5411Q7Cj, aid469624079,cid734100004. Both hosts use app.playurl.v1.PlayURLMoss.playView; direct protobuf observation precedes MediaResource conversion.

|Host|Requests|Result|
|---|---|---|
|7420400|qn80 and120,fnver0,fnval976,fourk=false,forceHost0,preferCodec2,download0,teenagersMode0|HEVC30.303fps;premium120/116 without URLs|
|7040300|qn80 and120,fnver0,fnval464,fourk=false,forceHost0,preferCodec2,download0,teenagersMode0|Same rate/dimension inventory;premium120/116 without URLs|

742 raw reply at16:12:39 and adjacent1080P/4K observations:

|qn|codecId|Dimensions|frameRateRaw|Bandwidth|Playable|Representative urlFp|
|---|---|---|---|---|---|---|
|120|default0|0×0|absent|0|no|none|
|116|default0|0×0|absent|0|no|none|
|80|12 HEVC|1920×1080|30.303|213862|yes|9ceceec0c8c7e6cb /808836c3a7b4f2d4|
|64|12 HEVC|1280×720|30.303|130364|yes|2001cb31f3f29a52 /e56b433aaba8e781|
|32|12 HEVC|852×480|30.303|90953|yes|c1d5784f728b2e6e /55e480869fd3e44e|
|16|12 HEVC|640×360|30.303|67491|yes|1ca1a6278796cee5 /da756fea16e4dbb4|

All entries:errCode0,noWidevinePssh. qn120/116 needVip=true,needLogin=true;80/64 needVip=false,needLogin=true;32/16 both false. Premium DASH defaults are descriptors,not zero-fps media. SAR/start_with_sap unavailable. Parsed resources retain playable metadata;asset default80. Smart Auto sees ordinary clear SDR UGC and preserves supported host HEVC hardware.

At17:31:43 on742, IjkMediaPlayerItem native event2,is_audio0,http206,error0 opened9ceceec0c8c7e6cb,uniquely matching qn80 HEVC1920×1080 with declared30.303fps.704 repeated the native-open association at17:54/18:09;742 repeated at18:10/18:16/18:28,including808836c3a7b4f2d4. Observer logs AMBIGUOUS if the latest server list does not uniquely match. Prefetch/alternate CDN URLs can remain unresolved;playback is never changed.

Both modules enabled:704 native position31.662→33.664s at18:09;74236.607→38.626→40.615s at18:10,isPlaying=true. These prove media progress,not actual displayed frame counts. Online playback has twoSurfaceViews;the first can update while paused,so its timestamps are only auxiliary layer activity.

Visual captures showed the male dancer during sampled intervals. Historical male→female phase change was not established as alternating120fps source content.

## Bounded request comparisons,7420400

|Time|Only experimental difference|Result|
|---|---|---|
|16:32:50|fourk false→true|Same HEVC30.303 inventory/fingerprints|
|16:56:50–16:57:31|preferCodec2→1|AVC30.303;qn80,bw2608943,urlFp2946a40554e2a40f;premiumURLs absent|
|17:03:01|fnval976→980,modern explicitly defined HEVC bit4|Same HEVC30.303 inventory|
|17:22:34|Separate native PlayerUnite RPC,original scalars/unchanged native headers|VodInfo80 HEVC30.303;premiumURLs absent;hostreply unchanged|

All mutations/side-call code removed before regression. No identity,trial,credential,response-metadata or callback fabrication. Final probe is read-only.

## Online controls

|Sample|Server/native-open identity|Limit|
|---|---|---|
|av20244965,cid33062674|qn116,AVC1920×1080,frameRateRaw62.500,bw5491551,urlFp2c8043f95fe5cef3|Ordinary1080P60;62.5 is declared metadata,not measured display rate|
|BV14p4y1X7Xc,cid196361057|qn120,AVC4096×2160@50,bw16386926,urlFp01e3f22864705d5a /8a93b64ef72be299 /b37bc9e9efa03798|Real4K despite needVip=true;flag alone is not access verdict|
|BV1GJ411x7h7|qn112,HEVC1920×1080@25,urlFpcb0abeb0be9d2aa1|Additional ordinary low-rate control,not30fps|

4K50 also exposes qn116 AVC2048×1080@50 and lower30.303fps representations,same742 request scalars as target.
P2,cid196361444,at18:12 on742:qn120 AVC4096×2160 with declared30.303fps,urlFp4088ce30a7c9b78f. Used for GUI lifecycle checks,not the HFR target.

## Local4K120 control

12s,HEVC,yuv420p,3840×2160,120fps,1440 verified frames:720red/720blue,1439alternations,PTS8333–8334µs. SHA-25655b830989ceb4937e44d0926749089c020179c423396f04c8bf196f2fe8ea041. Recipe: [control-source.md](control-source.md).

Both704/742 use a fixed local source in DEBUG part-fix,separate from UGC. Hardware requires mediacodec and mediacodec-hevc=1. Type2,resource manager c2.qti.hevc.decoder.low_latency,3840×2160,native decode/output estimates119–125fps,12s media≈12s wall. Type1 software≈60fps,slower than real time. Estimates are not event counts.

|Condition|Display|Actual-present measurement|
|---|---|---|
|Initial old hosts|mode2≈60Hz|Long single-color intervals;PixelCopy both colors but few switches|
|Scene DFPS120,idle|physicalmode1≈120;app/system render60|17:31:60.36Hz,median16.57ms|
|Same source,sustained8s input17:21|app refresh120|118.8–119.8Hz,median≈8.28ms;PixelCopy switches≈110–120/s|
|Force-stop/restart Scene/Bili17:22 and17:43|Both app PIDs changed;scene-daemon survived|17:43 steady60.35Hz,median16.57ms|
|704,both modules,input18:07|app refresh120|120.707/120.711Hz,median8.284ms|
|742,part-fix only,input18:13|app refresh120|117.834/118.805Hz|
|742,part-fix only,idle18:15|app refresh60|60.353755Hz,median16.569063ms|
|742,both modules,background short touches18:27|app refresh120|120.703/117.862/115.994Hz,median8.284ms|
|742,both modules,after stopping input18:30|app refresh60|steady60.358Hz,median16.568ms|

Scene refresh-manager GUI OFF/ON was tested/restored enabled. User DFPS active/idle/game/gesture/lock/low-brightness120;per-Bili active/idle120. No root daemon killed. Surface120 hint removed at user request;final code also removes optional window-mode experiment. No production refresh forcing.

SurfaceFlinger columns:desired-present,actual-present,frame-ready. Deduplicate actual timestamps;OEM ring differs. Desired PTS is not wall-clock queue cadence;PixelCopy samples buffers. No exact dropped-frame count claimed. Local control supports an idle scheduling limit but does not establish target source cadence/root cause.

## Background touch experiment

At18:26–18:30,742/both modules:MOVE-only background events failed to sustain120Hz.180ms short touches with a100ms gap succeeded on the local4K120 control;the process ran independently on the phone and was stopped after each test. PixelCopy color-switch increments reached about118–121/s during the successful interval,but are not a second frame counter.

Target at18:28:52:request qn120;premium URLs absent;native-open fingerprint808836c3a7b4f2d4 matched1080P HEVC,declared30.303fps. During20s of background touches,three system renderFrameRate samples were120.00001;native decode/output estimates were about28.7–32.3fps,position progressed normally. Four seconds after stopping the script,renderFrameRate returned60 while physicalmode1 stayed120Hz. The workaround removes the display scheduling limit in this test;it does not supply a missing4K120 representation. [Method](touch-control.md).

## Network/tool control

17:44:system possible-VPN connectivity dialog during4K buffering. HTTP Toolkit ProxyVpnService confirmed running;stopped17:46 because measurements use in-process observations. Subsequent three samples opened their expected decoders and updated auxiliary Surface timestamps without retry;later native position samples establish media progress. Capture path is a buffering confound,not a proven explanation of every stall.

Frida retained17.22.2 on host/phone/Kahlo at user request,node bridge7.0.13. Android17 native/Java injection still fails;not used for playback conclusions. No rollback.

## Validation/privacy

Final JVM:hook48 +part-fix81,zero failures/errors;DEBUG/release build withJDK17. Release DEX lacks BiliHfrProbe,SERVER_REP,HOST_SELECTION,PLAY_POSITION,CONTROL_WINDOW,bili_hfr_control,localGeneratedHEVC/window experiment markers. Release source sets have empty research stubs. Signing stays private.

Module/lifecycle results: [regression-results.md](../regression-results.md). No remote writes.

Final device state at18:38:7420400,both release modules loaded in the new host process;installed APK hashes match local release files. Explicit local-control intent creates no decoder;BiliHfrProbe lines0. Release smoke opens target1080HEVC,ordinary1080AVC,and4KAVC4096×2160;4K needed one additional buffering wait. Background input stopped;Scene configuration retained. No claim that this S-level release smoke supplies missing targetHFR evidence.
