package io.github.yylsping.bilihook;

import android.content.Context;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Hooks whose symbols and signatures are specific to Bilibili 7.42.0 (7420400). */
final class Bili742Hooks {
    private static final String ACCOUNTS = "com.bilibili.lib.accounts.BiliAccounts";
    private static final String ACCOUNT_INFO = "com.bilibili.lib.accountinfo.BiliAccountInfo";
    private static final String UGC_QUALITY_SERVICE =
            "com.bilibili.playerbizcommon.features.quality.PlayerQualityService";
    private static final String PGC_QUALITY_SERVICE =
            "com.bilibili.bangumi.ui.page.detail.playerV2.widget.quality.l";
    private static final long LOGIN_STATE_CACHE_TTL_MS = 750L;
    private static final long TRANSACTION_TTL_MS = 30_000L;
    private static final Object[] NO_ARGS = new Object[0];

    private static final RequestBuilderStatus.Spec[] REQUEST_BUILDERS = {
            new RequestBuilderStatus.Spec("ugc-playview-v1",
                    "com.bapis.bilibili.app.playurl.v1.PlayViewReq$Builder", true),
            new RequestBuilderStatus.Spec("ugc-playurl-v1",
                    "com.bapis.bilibili.app.playurl.v1.PlayURLReq$Builder", false),
            new RequestBuilderStatus.Spec("pgc-playview-v1",
                    "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq$Builder", false),
            new RequestBuilderStatus.Spec("pgc-playview-v2",
                    "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq$Builder", true)
    };

    private static final PremiumQualityState QUALITY = new PremiumQualityState();
    private static final ThreadLocal<PremiumQualityState.Snapshot> VIP_GATE =
            new ThreadLocal<>();
    private static final QualityDecisionScope DECISION_SCOPE = new QualityDecisionScope();
    private static final DownloadQualityScope DOWNLOAD_SCOPE = new DownloadQualityScope();
    private static volatile Context accountsContext;
    private static volatile Method accountsGetMethod;
    private static volatile Object accountsInstance;
    private static volatile Method accountsIsLoginMethod;
    private static volatile boolean cachedLoginState;
    private static volatile long cachedLoginStateAt;

    private Bili742Hooks() {}

    static void install(Context context, ClassLoader classLoader) {
        List<String> unavailable = new ArrayList<>();
        int hookCount = 0;

        if (prepareLoginState(context, classLoader)) {
            hookCount++;
        } else {
            unavailable.add("login-state");
        }
        if (hookScopedAccountVip(classLoader)) {
            hookCount++;
        } else {
            unavailable.add("scoped-account-vip");
        }
        hookCount += hookQualityService(new QualityServiceSpec(
                "ugc", UGC_QUALITY_SERVICE, "N0", "e3", false, "i6", "h"), classLoader, unavailable);
        hookCount += hookQualityService(new QualityServiceSpec(
                "pgc", PGC_QUALITY_SERVICE, "S1", "B1", true, "Z6", "i"), classLoader, unavailable);
        for (String pane : new String[]{"download.v", "downloadv2.w"}) {
            if (hookDownloadGate(classLoader, pane)) hookCount++;
            else unavailable.add(pane + "-quality-gate");
        }
        for (boolean listModel : new boolean[]{true, false}) {
            if (hookCachedVideoAccess(classLoader, listModel)) hookCount++;
            else unavailable.add(listModel ? "cached-video-list" : "cached-video-entry");
        }

        List<RequestBuilderStatus.Result> builders = inspectRequestBuilders(classLoader);
        for (String missing : RequestBuilderStatus.missingRequired(builders)) {
            unavailable.add("request-builder:" + missing);
        }
        HookRuntime.log("7.42.0 request builders (host-native qn, no global hooks): "
                + RequestBuilderStatus.summarize(builders));

        hookCount += Bili742AdHooks.install(classLoader, unavailable);
        if (BuildConfig.DEBUG) {
            hookCount += Bili742DebugProbe.install(classLoader);
        }
        String summary = "bili hook 7.42.0: initialized (" + hookCount + " hooks, "
                + (BuildConfig.DEBUG ? "debug" : "release") + ")";
        if (!unavailable.isEmpty()) summary += ", unavailable=" + String.join(",", unavailable);
        HookRuntime.log(summary);
    }

    private static final class QualityServiceSpec {
        final String id;
        final String className;
        final String gateMethod;
        final String requestMethod;
        final boolean pgc;
        final String decisionMethod;
        final String ceilingField;

        QualityServiceSpec(String id, String className, String gateMethod, String requestMethod,
                boolean pgc, String decisionMethod, String ceilingField) {
            this.id = id;
            this.className = className;
            this.gateMethod = gateMethod;
            this.requestMethod = requestMethod;
            this.pgc = pgc;
            this.decisionMethod = decisionMethod;
            this.ceilingField = ceilingField;
        }
    }

    private static boolean prepareLoginState(Context context, ClassLoader classLoader) {
        try {
            Class<?> accountsClass = Reflect.findClass(ACCOUNTS, classLoader);
            accountsGetMethod = Reflect.findMethodExact(accountsClass, "get", Context.class);
            accountsIsLoginMethod = Reflect.findMethodExact(accountsClass, "isLogin");
            accountsContext = context;
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 login-state symbols unavailable", error);
            return false;
        }
    }

    /** Bypasses only the current-account VIP query nested inside a quality-specific gate. */
    private static boolean hookScopedAccountVip(ClassLoader classLoader) {
        try {
            Class<?> accountInfoClass = Reflect.findClass(ACCOUNT_INFO, classLoader);
            Reflect.findAndHookMethod(accountInfoClass, "isEffectiveVip",
                    new HookRuntime.Callback() {
                        @Override
                        protected void afterHookedMethod(HookRuntime.HookParam param) {
                            PremiumQualityState.Snapshot transaction = VIP_GATE.get();
                            boolean decisionScope = DECISION_SCOPE.isActive();
                            boolean downloadScope = DOWNLOAD_SCOPE.isActive();
                            if ((transaction != null || decisionScope || downloadScope) && isLoggedIn()) {
                                debug("scoped account VIP " + describe(transaction)
                                        + ", decisionScope=" + decisionScope
                                        + ", downloadScope=" + downloadScope
                                        + ", original=" + param.getResult());
                                param.setResult(true);
                            }
                        }
                    });
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 scoped account VIP hook unavailable", error);
            return false;
        }
    }

    /**
     * Only the native UGC download pane's synchronous submission may authorize video qn.
     * The pane still checks login, network, frozen membership, warnings and download rights;
     * its native downloader/resolver retains the selected qn and the server's real response.
     */
    private static boolean hookDownloadGate(ClassLoader loader, String pane) {
        try {
            Class<?> type = Reflect.findClass("tv.danmaku.bili.ui.videodownload." + pane, loader);
            if (type.getDeclaredField("r").getType() != Integer.TYPE
                    || type.getDeclaredField("s").getType() != Boolean.TYPE) {
                throw new NoSuchFieldException("download quality/audio ABI");
            }
            Reflect.findAndHookMethod(type, "e0", List.class, new HookRuntime.Callback() {
                @Override
                protected void beforeHookedMethod(HookRuntime.HookParam param) {
                    int quality = 0;
                    boolean audioOnly = true;
                    try {
                        quality = Reflect.getIntField(param.thisObject, "r");
                        audioOnly = (Boolean) Reflect.getObjectField(param.thisObject, "s");
                    } catch (Throwable ignored) {
                        // Push an inactive frame on ABI/read failure, so nesting stays balanced.
                        quality = 0;
                    }
                    Object selected = param.getArg(0);
                    boolean hasSelection = selected instanceof List && !((List<?>) selected).isEmpty();
                    boolean loggedIn = hasSelection && isLoggedIn();
                    if (DownloadQualityScope.shouldGrant(loggedIn, quality, audioOnly)) {
                        debug(pane + " video download gate, qn=" + quality);
                    }
                    // Enter last: a diagnostic failure must never leave an unbalanced frame.
                    DOWNLOAD_SCOPE.enter(loggedIn, quality, audioOnly);
                }

                @Override
                protected void afterHookedMethod(HookRuntime.HookParam param) {
                    DOWNLOAD_SCOPE.exit();
                }
            });
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 " + pane + " download gate unavailable", error);
            return false;
        }
    }

    /** Restore only the UGC premium-video branch of the cached entry's local playability query. */
    private static boolean hookCachedVideoAccess(ClassLoader loader, boolean listModel) {
        String name = listModel ? "com.bilibili.offline.c"
                : "com.bilibili.videodownloader.model.VideoDownloadAVPageEntry";
        try {
            Class<?> type = Reflect.findClass(name, loader);
            Class<?> page = Reflect.findClass("com.bilibili.videodownloader.model.av.Page", loader);
            Class<?> drama = Reflect.findClass("tv.danmaku.bili.ui.offline.drama.DramaVideo", loader);
            Reflect.findAndHookMethod(type, "h", new HookRuntime.Callback() {
                @Override
                protected void afterHookedMethod(HookRuntime.HookParam param) throws Throwable {
                    if (!Boolean.FALSE.equals(param.getResult()) || !isLoggedIn()) return;
                    Object entry = param.thisObject;
                    Object content = Reflect.getObjectField(entry, listModel ? "l" : "x");
                    boolean ugc = page.isInstance(content) || (listModel && drama.isInstance(content));
                    if (!ugc) return;
                    int quality;
                    try {
                        quality = listModel ? Reflect.getIntField(entry, "q")
                                : Integer.parseInt((String) Reflect.getObjectField(entry, "mTypeTag"));
                    } catch (NumberFormatException ignored) {
                        return;
                    }
                    int audio = Reflect.getIntField(entry, listModel ? "r" : "mAudioQuality");
                    if (DownloadQualityScope.canUseCachedVideo(true, ugc, quality, audio)) {
                        debug(name + " cached UGC video access, qn=" + quality);
                        param.setResult(true);
                    }
                }
            });
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 cached video access unavailable: " + name, error);
            return false;
        }
    }

    private static int hookQualityService(QualityServiceSpec spec, ClassLoader classLoader,
            List<String> unavailable) {
        int count = 0;
        Class<?> serviceClass;
        try {
            serviceClass = Reflect.findClass(spec.className, classLoader);
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 " + spec.id + " quality service unavailable", error);
            unavailable.add(spec.id + "-quality-service");
            return 0;
        }

        if (hookSelection(spec, serviceClass)) count++;
        else unavailable.add(spec.id + "-quality-selection");
        if (hookGate(spec, serviceClass)) count++;
        else unavailable.add(spec.id + "-quality-gate");
        if (hookRequestStart(spec, serviceClass)) count++;
        else unavailable.add(spec.id + "-quality-request");
        if (hookCompletion(spec, serviceClass)) count++;
        else unavailable.add(spec.id + "-quality-completion");
        if (hookDecisionScope(spec, serviceClass)) count++;
        else unavailable.add(spec.id + "-decision-scope");
        if (BuildConfig.DEBUG) {
            if (hookResourceObserver(spec, serviceClass, classLoader)) count++;
            else unavailable.add(spec.id + "-resource-observer");
        }
        return count;
    }

    private static boolean hookSelection(final QualityServiceSpec spec, Class<?> serviceClass) {
        try {
            Reflect.findAndHookMethod(serviceClass, "t5", Integer.TYPE, String.class,
                    new HookRuntime.Callback() {
                        @Override
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            int target = (Integer) param.getArg(0);
                            boolean loggedIn = isLoggedIn();
                            String content = currentContentKey(param.thisObject, spec.pgc);
                            PremiumQualityState.Snapshot transaction = QUALITY.select(
                                    param.thisObject, content, target, loggedIn,
                                    SystemClock.elapsedRealtime(), TRANSACTION_TTL_MS);
                            debug(spec.id + " selection " + describe(transaction)
                                    + ", target=" + target + ", loggedIn=" + loggedIn
                                    + ", content=" + content);
                        }
                    });
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 " + spec.id + " quality selection hook unavailable", error);
            return false;
        }
    }

    private static boolean hookGate(final QualityServiceSpec spec, Class<?> serviceClass) {
        try {
            Reflect.findAndHookMethod(serviceClass, spec.gateMethod, Integer.TYPE, String.class,
                    new HookRuntime.Callback() {
                        @Override
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            VIP_GATE.remove();
                            if (!isLoggedIn()) {
                                QUALITY.clear(param.thisObject);
                                return;
                            }
                            int target = (Integer) param.getArg(0);
                            PremiumQualityState.Snapshot transaction = QUALITY.authorize(
                                    param.thisObject,
                                    currentContentKey(param.thisObject, spec.pgc), target,
                                    SystemClock.elapsedRealtime());
                            if (transaction != null) {
                                VIP_GATE.set(transaction);
                                debug(spec.id + " gate authorized " + describe(transaction));
                            }
                        }

                        @Override
                        protected void afterHookedMethod(HookRuntime.HookParam param) {
                            PremiumQualityState.Snapshot transaction = VIP_GATE.get();
                            if (transaction != null) {
                                debug(spec.id + " gate result " + describe(transaction)
                                        + ", allowed=" + param.getResult());
                            }
                            VIP_GATE.remove();
                        }
                    });
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 " + spec.id + " quality gate hook unavailable", error);
            return false;
        }
    }

    private static boolean hookRequestStart(final QualityServiceSpec spec, Class<?> serviceClass) {
        try {
            Reflect.findAndHookMethod(serviceClass, spec.requestMethod,
                    new HookRuntime.Callback() {
                        @Override
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            String content = currentContentKey(param.thisObject, spec.pgc);
                            int expected = readIntField(param.thisObject, "e");
                            PremiumQualityState.Snapshot transaction = QUALITY.startRequest(
                                    param.thisObject, content, expected,
                                    SystemClock.elapsedRealtime());
                            if (transaction != null) {
                                debug(spec.id + " native request " + describe(transaction)
                                        + ", expected=" + expected);
                            }
                        }
                    });
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 " + spec.id + " request correlation unavailable", error);
            return false;
        }
    }

    private static boolean hookCompletion(final QualityServiceSpec spec, Class<?> serviceClass) {
        try {
            Reflect.findAndHookMethod(serviceClass, "a",
                    Boolean.TYPE, Integer.TYPE, Integer.TYPE, Boolean.TYPE,
                    new HookRuntime.Callback() {
                        @Override
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            String content = currentContentKey(param.thisObject, spec.pgc);
                            int expected = readIntField(param.thisObject, "e");
                            PremiumQualityState.Snapshot transaction = QUALITY.current(
                                    param.thisObject, content, SystemClock.elapsedRealtime());
                            debug(spec.id + " completion observed " + describe(transaction)
                                    + ", content=" + content + ", expected=" + expected
                                    + ", success=" + param.getArg(0)
                                    + ", requested=" + param.getArg(1)
                                    + ", actual=" + param.getArg(2));
                            if (transaction == null) return;
                            boolean success = (Boolean) param.getArg(0);
                            int actual = (Integer) param.getArg(2);
                            if (!PremiumQualityState.matchesCompletion(
                                    transaction, success, expected, actual)) return;
                            boolean completed = QUALITY.complete(param.thisObject, content,
                                    transaction.target, transaction.generation,
                                    SystemClock.elapsedRealtime());
                            if (completed) {
                                debug(spec.id + " completion " + describe(transaction)
                                        + ", success=" + param.getArg(0)
                                        + ", requested=" + param.getArg(1)
                                        + ", actual=" + param.getArg(2));
                            }
                        }
                    });
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 " + spec.id + " quality completion hook unavailable", error);
            return false;
        }
    }

    /**
     * Opens the scoped premium capability only while the host runs its own startup/auto
     * quality decision, and only when the host-read preference ceiling is premium. The
     * host strategy itself still picks the highest available entry not above the ceiling.
     */
    private static boolean hookDecisionScope(final QualityServiceSpec spec, Class<?> serviceClass) {
        try {
            HookRuntime.Callback callback = new HookRuntime.Callback() {
                @Override
                protected void beforeHookedMethod(HookRuntime.HookParam param) {
                    int ceiling = readIntField(param.thisObject, spec.ceilingField);
                    if (DECISION_SCOPE.enter(isLoggedIn(), ceiling)) {
                        debug(spec.id + " decision scope opened, ceiling=" + ceiling
                                + ", content=" + currentContentKey(param.thisObject, spec.pgc));
                    }
                }

                @Override
                protected void afterHookedMethod(HookRuntime.HookParam param) {
                    DECISION_SCOPE.exit();
                }
            };
            if (spec.pgc) {
                Reflect.findAndHookMethod(serviceClass, spec.decisionMethod,
                        Boolean.TYPE, Boolean.TYPE, callback);
            } else {
                Reflect.findAndHookMethod(serviceClass, spec.decisionMethod,
                        Boolean.TYPE, callback);
            }
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 " + spec.id + " decision scope hook unavailable", error);
            return false;
        }
    }

    /** Debug-only observer; never installed in release builds. */
    private static boolean hookResourceObserver(final QualityServiceSpec spec,
            Class<?> serviceClass, ClassLoader classLoader) {
        try {
            Class<?> mediaResourceClass = Reflect.findClass(
                    "com.bilibili.lib.media.resource.MediaResource", classLoader);
            String method = spec.pgc ? "c2" : "s3";
            Reflect.findAndHookMethod(serviceClass, method, mediaResourceClass,
                    new HookRuntime.Callback() {
                        @Override
                        protected void afterHookedMethod(HookRuntime.HookParam param) {
                            String content = currentContentKey(param.thisObject, spec.pgc);
                            PremiumQualityState.Snapshot transaction = QUALITY.current(
                                    param.thisObject, content, SystemClock.elapsedRealtime());
                            debug(spec.id + " resource observed " + describe(transaction)
                                    + ", content=" + content);
                            logMediaResource(spec.id, transaction, param.getArg(0));
                        }
                    });
            return true;
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 " + spec.id + " resource observer unavailable", error);
            return false;
        }
    }

    private static List<RequestBuilderStatus.Result> inspectRequestBuilders(
            ClassLoader classLoader) {
        List<RequestBuilderStatus.Result> results = new ArrayList<>();
        for (RequestBuilderStatus.Spec spec : REQUEST_BUILDERS) {
            boolean available = false;
            try {
                Class<?> builder = Reflect.findClass(spec.className, classLoader);
                Reflect.findMethodExact(builder, "setQn", Long.TYPE);
                available = true;
            } catch (Throwable error) {
                HookRuntime.log("7.42.0 request builder symbol unavailable: " + spec.id, error);
            }
            results.add(new RequestBuilderStatus.Result(spec, available));
        }
        return results;
    }

    private static String currentContentKey(Object service, boolean pgc) {
        if (service == null) return null;
        try {
            Object director;
            if (pgc) {
                director = Reflect.getObjectField(service, "u");
            } else {
                Object container = Reflect.getObjectField(service, "a");
                director = container == null ? null : Reflect.callMethod(container, "w");
            }
            Object video = director == null ? null : Reflect.callMethod(director, "N0");
            Object key = video == null ? null : Reflect.callMethod(video, "f");
            return key instanceof String ? (String) key : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isLoggedIn() {
        long now = SystemClock.elapsedRealtime();
        long checkedAt = cachedLoginStateAt;
        long age = now - checkedAt;
        if (checkedAt != 0L && age >= 0L && age < LOGIN_STATE_CACHE_TTL_MS) {
            return cachedLoginState;
        }
        try {
            Object accounts = accountsInstance;
            if (accounts == null) {
                Method get = accountsGetMethod;
                Context context = accountsContext;
                if (get == null || context == null) return false;
                accounts = get.invoke(null, context);
                if (accounts == null) return false;
                accountsInstance = accounts;
            }
            Method isLogin = accountsIsLoginMethod;
            if (isLogin == null) return false;
            boolean loggedIn = Boolean.TRUE.equals(isLogin.invoke(accounts, NO_ARGS));
            cachedLoginState = loggedIn;
            cachedLoginStateAt = now;
            return loggedIn;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void logMediaResource(String lane, PremiumQualityState.Snapshot transaction,
            Object mediaResource) {
        if (transaction == null || mediaResource == null) return;
        try {
            Object selected = Reflect.callMethod(mediaResource, "s");
            int selectedQuality = selected == null ? -1 : Reflect.getIntField(selected, "b");
            List<String> dashQualities = new ArrayList<>();
            boolean targetPlayable = false;
            Object dash = Reflect.callMethod(mediaResource, "h");
            if (dash != null) {
                Object videos = Reflect.callMethod(dash, "h");
                if (videos instanceof Iterable) {
                    for (Object video : (Iterable<?>) videos) {
                        if (video == null) continue;
                        Object idValue = Reflect.callMethod(video, "p");
                        Object urlValue = Reflect.callMethod(video, "i");
                        int id = idValue instanceof Integer ? (Integer) idValue : -1;
                        boolean hasUrl = urlValue instanceof String
                                && !((String) urlValue).isEmpty();
                        dashQualities.add(id + ":" + hasUrl);
                        if (id == transaction.target && hasUrl) targetPlayable = true;
                    }
                }
            }
            debug(lane + " resource " + describe(transaction)
                    + ", selected=" + selectedQuality + ", dash=" + dashQualities
                    + ", targetPlayable=" + targetPlayable);
        } catch (Throwable error) {
            HookRuntime.log("7.42.0 debug media verification failed", error);
        }
    }

    private static String describe(PremiumQualityState.Snapshot transaction) {
        if (transaction == null) return "transaction=none";
        return "generation=" + transaction.generation
                + ", request=" + transaction.requestId
                + ", content=" + transaction.contentKey
                + ", target=" + transaction.target
                + ", phase=" + transaction.phase;
    }

    private static void debug(String message) {
        if (BuildConfig.DEBUG) HookRuntime.log("7.42.0 " + message);
    }

    private static int readIntField(Object receiver, String name) {
        if (receiver == null) return -1;
        try {
            return Reflect.getIntField(receiver, name);
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
