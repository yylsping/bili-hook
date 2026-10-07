package io.github.yylsping.bilihook;

import android.util.Log;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/** Read-only observations, compiled only into DEBUG. Never stringify request/resource objects. */
final class HfrDebugProbe {
    private static final String TAG = "BiliHfrProbe";
    private static volatile List<Representation> recentRepresentations = Collections.emptyList();
    private static final WeakHashMap<Object, Long> positionLogTimes = new WeakHashMap<>();
    private HfrDebugProbe() {}

    static void install(ClassLoader loader, HostVersion host) {
        if (!BuildConfig.DEBUG) return;
        try {
            Class<?> moss = Reflect.findClass("com.bapis.bilibili.app.playurl.v1.PlayURLMoss", loader);
            for (Method method : moss.getDeclaredMethods()) {
                if (!(method.getName().equals("playView") || method.getName().equals("playURL"))) continue;
                HookRuntime.hook(method, new HookRuntime.Callback() {
                    @Override protected void beforeHookedMethod(HookRuntime.HookParam param) {
                        Object request = param.getArg(0);
                        StringBuilder out = new StringBuilder("REQUEST api=app.playurl.v1.")
                                .append(method.getName());
                        for (String getter : new String[]{"getAid", "getCid", "getQn", "getFnver",
                                "getFnval", "getFourk", "getForceHost", "getPreferCodecTypeValue",
                                "getDownload", "getTeenagersMode"}) {
                            out.append(' ').append(getter.substring(3)).append('=')
                                    .append(scalar(request, getter));
                        }
                        log(out.toString());
                    }
                });
            }
            Class<?> reply = Reflect.findClass("com.bapis.bilibili.app.playurl.v1.PlayViewReply", loader);
            Reflect.findAndHookMethod(reply, "getVideoInfo", new HookRuntime.Callback() {
                @Override protected void afterHookedMethod(HookRuntime.HookParam param) {
                    logServer(param.getResult());
                }
            });
            Class<?> resource = Reflect.findClass("com.bilibili.lib.media.resource.MediaResource", loader);
            Reflect.findAndHookMethod(resource, "a", JSONObject.class, new HookRuntime.Callback() {
                @Override protected void afterHookedMethod(HookRuntime.HookParam param) {
                    logResource("HOST_PARSED_REP", param.thisObject, host);
                }
            });
            Reflect.findAndHookMethod(resource, host == HostVersion.BILI_7_42_0 ? "S" : "R",
                    int.class, int.class, new HookRuntime.Callback() {
                @Override protected void afterHookedMethod(HookRuntime.HookParam param) {
                    log("ASSET_DEFAULT qn=" + scalar(param.getResult(), "getDefaultVideoId"));
                    logResource("ASSET_CANDIDATE", param.thisObject, host);
                }
            });
            log("INSTALLED host=" + host + " readOnly=true");
        } catch (Throwable error) {
            log("INSTALL_ERROR type=" + error.getClass().getSimpleName());
        }
        try {
            for (String name : new String[]{"IjkMediaPlayer", "IjkMediaPlayerItem"}) {
                Class<?> ijk = Reflect.findClass("tv.danmaku.ijk.media.player." + name, loader);
                Reflect.findAndHookMethod(ijk, "trackerIjkNativeInvokeMsg", int.class, Bundle.class,
                    new HookRuntime.Callback() {
                        @Override protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            int event = (Integer) param.getArg(0);
                            Bundle data = (Bundle) param.getArg(1);
                            if ((event != 1 && event != 2) || data == null || data.getInt("is_audio", -1) != 0) return;
                            String fingerprint = HfrProbeValues.urlFingerprint(data.getString("url"));
                            log("IJK_HTTP event=" + event + " player=" + System.identityHashCode(param.thisObject)
                                    + " urlFp=" + fingerprint + " httpCode=" + data.getInt("http_code", 0)
                                    + " error=" + data.getInt("error", 0));
                            int httpCode = data.getInt("http_code", 0);
                            if (event != 2 || data.getInt("error", -1) != 0
                                    || httpCode < 200 || httpCode >= 300) return;
                            List<Representation> candidates = recentRepresentations;
                            List<String> fingerprints = new ArrayList<>();
                            for (Representation candidate : candidates) fingerprints.add(candidate.fingerprint);
                            int match = HfrProbeValues.uniqueUrlMatch(fingerprints, fingerprint);
                            if (match < 0) {
                                log("HOST_SELECTION selectedUrlFp=" + fingerprint + " matchConfidence=AMBIGUOUS");
                            } else {
                                Representation selected = candidates.get(match);
                                log("HOST_SELECTION " + selected.metadata + " selectedUrlFp=" + fingerprint
                                        + " matchConfidence=URL source=nativeHttpOpen");
                            }
                        }
                    });
            }
            log("IJK_HTTP_OBSERVER installed=true readOnly=true");
        } catch (Throwable error) { log("IJK_INSTALL_ERROR type=" + error.getClass().getSimpleName()); }
        try {
            Class<?> player = Reflect.findClass("tv.danmaku.ijk.media.player.IjkMediaPlayer", loader);
            Reflect.findAndHookMethod(player, "getCurrentPosition", new HookRuntime.Callback() {
                @Override protected void afterHookedMethod(HookRuntime.HookParam param) {
                    if (!(param.getResult() instanceof Number)) return;
                    long now = SystemClock.elapsedRealtime();
                    synchronized (positionLogTimes) {
                        Long prior = positionLogTimes.get(param.thisObject);
                        if (prior != null && now - prior < 2000L) return;
                        positionLogTimes.put(param.thisObject, now);
                    }
                    log("PLAY_POSITION player=" + System.identityHashCode(param.thisObject)
                            + " elapsedMs=" + now + " positionMs=" + param.getResult()
                            + " playing=" + scalar(param.thisObject, "isPlaying")
                            + " decoderType=" + scalar(param.thisObject, "getVideoDecoder")
                            + " declaredFps=" + scalar(param.thisObject, "getVideoFps")
                            + " decodeEstimate=" + scalar(param.thisObject, "getVideoDecodeFramesPerSecond")
                            + " outputEstimate=" + scalar(param.thisObject, "getVideoOutputFramesPerSecond"));
                }
            });
        } catch (Throwable error) { log("POSITION_INSTALL_ERROR type=" + error.getClass().getSimpleName()); }
    }

    private static void logServer(Object info) {
        recentRepresentations = Collections.emptyList();
        if (info == null) return;
        try {
            log("SERVER_INFO identity=" + System.identityHashCode(info) + " qn=" + scalar(info, "getQuality"));
            Object streams = Reflect.callMethod(info, "getStreamListList");
            if (!(streams instanceof Iterable)) return;
            List<Representation> representations = new ArrayList<>();
            boolean overflow = false;
            int index = 0;
            for (Object stream : (Iterable<?>) streams) {
                Object status = Reflect.callMethod(stream, "getStreamInfo");
                Object video = Reflect.callMethod(stream, "getDashVideo");
                String url = (String) Reflect.callMethod(video, "getBaseUrl");
                String rate = HfrProbeValues.safeFrameRate((String) Reflect.callMethod(video, "getFrameRate"));
                String pssh = (String) Reflect.callMethod(video, "getWidevinePssh");
                if (!url.isEmpty() && representations.size() < 64) {
                    representations.add(new Representation(HfrProbeValues.urlFingerprint(url),
                            "selectedQuality=" + scalar(status, "getQuality")
                                    + " selectedCodecId=" + scalar(video, "getCodecid")
                                    + " selectedWidth=" + scalar(video, "getWidth")
                                    + " selectedHeight=" + scalar(video, "getHeight")
                                    + " selectedFps=" + HfrProbeValues.parseFrameRate(rate)));
                } else if (!url.isEmpty()) {
                    overflow = true;
                }
                log("SERVER_REP identity=" + System.identityHashCode(info) + " index=" + index++
                        + " qn=" + scalar(status, "getQuality") + " codecId=" + scalar(video, "getCodecid")
                        + " width=" + scalar(video, "getWidth") + " height=" + scalar(video, "getHeight")
                        + " frameRateRaw=" + rate + " fps=" + HfrProbeValues.parseFrameRate(rate)
                        + " bandwidth=" + scalar(video, "getBandwidth") + " playable=" + !url.isEmpty()
                        + " urlFp=" + HfrProbeValues.urlFingerprint(url) + " pssh=" + !pssh.isEmpty()
                        + " needVip=" + scalar(status, "getNeedVip") + " needLogin=" + scalar(status, "getNeedLogin")
                        + " errCode=" + scalar(status, "getErrCodeValue")
                        + " sar=unavailable startSap=unavailable");
            }
            recentRepresentations = overflow ? Collections.emptyList()
                    : Collections.unmodifiableList(representations);
        } catch (Throwable error) { log("SERVER_OBSERVE_ERROR type=" + error.getClass().getSimpleName()); }
    }

    private static final class Representation {
        final String fingerprint, metadata;
        Representation(String fingerprint, String metadata) {
            this.fingerprint = fingerprint; this.metadata = metadata;
        }
    }

    static void logResource(String stage, Object resource, HostVersion host) {
        if (!BuildConfig.DEBUG || resource == null) return;
        try {
            Object dash = Reflect.callMethod(resource, "h");
            Object videos = dash == null ? null : Reflect.callMethod(dash, "h");
            if (!(videos instanceof Iterable)) return;
            String qnGetter = host == HostVersion.BILI_7_42_0 ? "p" : "n";
            String codecGetter = host == HostVersion.BILI_7_42_0 ? "l" : "k";
            int index = 0;
            for (Object video : (Iterable<?>) videos) {
                if (video == null) continue;
                JSONObject json = (JSONObject) Reflect.callMethod(video, "b");
                String rawRate = HfrProbeValues.safeFrameRate(json.optString("frame_rate", ""));
                String url = json.optString("base_url", "");
                log(stage + " resource=" + System.identityHashCode(resource) + " index=" + index++
                        + " qn=" + scalar(video, qnGetter) + " codecId=" + scalar(video, codecGetter)
                        + " width=" + scalar(video, "getWidth") + " height=" + scalar(video, "getHeight")
                        + " frameRateRaw=" + rawRate + " fps=" + HfrProbeValues.parseFrameRate(rawRate)
                        + " bandwidth=" + json.optLong("bandwidth", 0)
                        + " playable=" + !url.isEmpty() + " urlFp=" + HfrProbeValues.urlFingerprint(url)
                        + " drm=" + json.optInt("dash_drm_type", -1)
                        + " sar=unknown startSap=unknown pssh=unknown needVip=unknown");
            }
        } catch (Throwable error) {
            log("OBSERVE_ERROR stage=" + stage + " type=" + error.getClass().getSimpleName());
        }
    }

    private static String scalar(Object object, String getter) {
        try {
            Object value = Reflect.callMethod(object, getter);
            return value instanceof Number || value instanceof Boolean ? String.valueOf(value) : "unknown";
        } catch (Throwable ignored) { return "unknown"; }
    }

    private static void log(String message) { if (BuildConfig.DEBUG) Log.i(TAG, message); }
}
