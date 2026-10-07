package io.github.yylsping.bilihook;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedModule;

/* JADX INFO: loaded from: classes2.dex */
public final class BiliHook extends XposedModule {
    private static final String ACCOUNTS = "com.bilibili.lib.accounts.BiliAccounts";
    private static final String LEGACY_AD_SEGMENT_CALLBACK = "tv.danmaku.bili.ui.video.videodetail.function.AdSegment$f";
    private static final String LEGACY_RELATE = "com.bapis.bilibili.app.view.v1.Relate";
    private static final String LEGACY_RELATES_FEED_REPLY = "com.bapis.bilibili.app.view.v1.RelatesFeedReply";
    private static final String LEGACY_RELATE_CONVERTER = "tv.danmaku.bili.videopage.data.view.helper.d";
    private static final String LEGACY_VIDEO_MODEL = "tv.danmaku.bili.videopage.data.view.model.BiliVideoDetail";
    private static final String LEGACY_VIDEO_MOSS_PARSER = "tv.danmaku.bili.videopage.data.view.helper.f";
    private static final String LEGACY_VIEW_REPLY = "com.bapis.bilibili.app.view.v1.ViewReply";
    private static final String NORMAL_SPLASH = "tv.danmaku.bili.ui.splash.ad.model.Splash";
    private static final String PEGASUS_AD_ITEM = "com.bilibili.pegasus.api.modelv2.AdItem";
    private static final String PEGASUS_BASE_PARSER = "com.bilibili.pegasus.api.BaseTMApiParser";
    private static final String PEGASUS_BRPC_CONVERTER = "com.bilibili.pegasus.utils.BrpcRespConverterKt";
    private static final String PEGASUS_CARD_OR_BUILDER = "com.bapis.bilibili.app.card.v1.CardOrBuilder";
    private static final String PLAYER_QUALITY_SERVICE = "com.bilibili.playerbizcommon.features.quality.PlayerQualityService";
    private static final int PREMIUM_QUALITY_MIN = 112;
    private static final String PROTOBUF_ANY = "com.google.protobuf.Any";
    private static final String QUALITY_LIST_CALLBACK = "com.bilibili.playerbizcommon.widget.function.quality.j$d";
    private static final String QUALITY_LIST_ITEM = "com.bilibili.playerbizcommon.widget.function.quality.p";
    private static final String RELATES_FEED_REPLY = "com.bapis.bilibili.app.viewunite.v1.RelatesFeedReply";
    private static final String RELATE_CARD = "com.bapis.bilibili.app.viewunite.common.RelateCard";
    private static final String RESOLVE_PARAMS = "com.bilibili.lib.media.resolver2.IResolveParams";
    private static final String SEARCH_CONVERTER = "com.bilibili.search.utils.BrpcSearchResultConverterKt";
    private static final String SEARCH_ITEM = "com.bapis.bilibili.polymer.app.search.v1.Item";
    private static final String SEARCH_RESULT_ALL = "com.bilibili.search.api.SearchResultAll";
    private static final String TAG = "bili hook";
    private static final String TARGET_PACKAGE = "tv.danmaku.bili";
    private static final String UGC_RESOLVER = "tv.danmaku.video.resolver.c";
    private static final String VIEW_CM = "com.bapis.bilibili.app.viewunite.v1.CM";
    private static final String VIP_EXTRA = "com.bilibili.lib.accountinfo.model.VipExtraUserInfo";
    private static final String VIP_USER = "com.bilibili.lib.accountinfo.model.VipUserInfo";
    private static final long LOGIN_STATE_CACHE_TTL_MS = 750L;
    private static final Object[] NO_ARGS = new Object[0];
    private static volatile Context accountsContext;
    private static volatile Method accountsGetMethod;
    private static volatile Object accountsInstance;
    private static volatile Method accountsIsLoginMethod;
    private static volatile boolean cachedLoginState;
    private static volatile long cachedLoginStateAt;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean ATTACH_HOOKED = new AtomicBoolean(false);
    private static volatile boolean realVipObserved;
    private static volatile boolean realVipEffective;
    private static final AtomicInteger desiredPremiumQuality = new AtomicInteger(0);
    private static final ThreadLocal<Boolean> rejectedPremiumPrepare = new ThreadLocal<>();

    /* JADX INFO: Access modifiers changed from: private */
    interface ItemPredicate {
        boolean test(Object obj);
    }

    private boolean mainProcess;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        HookRuntime.attach(this);
        mainProcess = TARGET_PACKAGE.equals(param.getProcessName());
        if (!mainProcess) {
            detach();
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!mainProcess || !TARGET_PACKAGE.equals(param.getPackageName())
                || !param.isFirstPackage() || !ATTACH_HOOKED.compareAndSet(false, true)) {
            return;
        }
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            HookRuntime.hook(attach, new HookRuntime.Callback() {
                @Override
                protected void afterHookedMethod(HookRuntime.HookParam hookParam) {
                    Context context = (Context) hookParam.getArg(0);
                    HostVersion version = context == null
                            ? HostVersion.UNSUPPORTED : resolveHostVersion(context);
                    if (!version.isSupported() || !INSTALLED.compareAndSet(false, true)) {
                        return;
                    }
                    installHooks((Application) hookParam.thisObject,
                            context.getClassLoader(), version);
                }
            });
        } catch (Throwable error) {
            HookRuntime.log("failed to hook Application.attach", error);
        }
    }

    private static HostVersion resolveHostVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(TARGET_PACKAGE, 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode()
                    : info.versionCode;
            HostVersion version = HostVersion.resolve(versionCode, info.versionName);
            if (version.isSupported()) {
                return version;
            }
            HookRuntime.log("unsupported Bilibili " + info.versionName + " (" + versionCode
                    + "); hooks not installed");
        } catch (Throwable error) {
            HookRuntime.log("failed to verify Bilibili version", error);
        }
        return HostVersion.UNSUPPORTED;
    }

    private static void installHooks(
            Context context, ClassLoader classLoader, HostVersion version) {
        if (version == HostVersion.BILI_7_42_0) {
            Bili742Hooks.install(context, classLoader);
            if (BuildConfig.DEBUG) HfrDebugProbe.install(classLoader, version);
            return;
        }
        installHooks740(context, classLoader);
        if (BuildConfig.DEBUG) HfrDebugProbe.install(classLoader, version);
    }

    /* JADX INFO: Access modifiers changed from: private */
    private static void installHooks740(Context context, ClassLoader classLoader) {
        List<String> unavailable = new ArrayList<>();
        int hookCount = 0;
        try {
            Class<?> accountsClass = Reflect.findClass(ACCOUNTS, classLoader);
            accountsGetMethod = Reflect.findMethodExact(accountsClass, "get", new Class[]{Context.class});
            accountsIsLoginMethod = Reflect.findMethodExact(accountsClass, "isLogin", new Class[0]);
            accountsContext = context;
        } catch (Throwable th) {
            unavailable.add("login-state");
        }
        if (hookVip(VIP_EXTRA, classLoader)) {
            hookCount = 0 + 1;
        } else {
            unavailable.add("vip-extra");
        }
        if (hookVip(VIP_USER, classLoader)) {
            hookCount++;
        } else {
            unavailable.add("vip-user");
        }
        if (hookNormalSplash(classLoader)) {
            hookCount++;
        } else {
            unavailable.add("normal-splash");
        }
        if (hookRelatedCards(classLoader)) {
            hookCount++;
        } else {
            unavailable.add("related-cm");
        }
        int legacyAdHooks = hookLegacyVideoAds(classLoader);
        int hookCount2 = hookCount + legacyAdHooks;
        if (legacyAdHooks != 6) {
            unavailable.add("legacy-video-cm");
        }
        int legacyModelHooks = hookLegacyVideoAdModels(classLoader);
        int hookCount3 = hookCount2 + legacyModelHooks;
        if (legacyModelHooks != 3) {
            unavailable.add("legacy-video-model-cm");
        }
        int underPlayerHooks = hookUnderPlayerAd(classLoader);
        int hookCount4 = hookCount3 + underPlayerHooks;
        if (underPlayerHooks != 2) {
            unavailable.add("under-player-cm");
        }
        int adViewHooks = hookClassicUnderPlayerAdViews(classLoader);
        int hookCount5 = hookCount4 + adViewHooks;
        if (adViewHooks != 2) {
            unavailable.add("under-player-view");
        }
        int homeFeedHooks = hookHomeFeedAds(classLoader);
        int hookCount6 = hookCount5 + homeFeedHooks;
        if (homeFeedHooks != 2) {
            unavailable.add("home-feed-cm");
        }
        if (hookSearchCards(classLoader)) {
            hookCount6++;
        } else {
            unavailable.add("search-cm");
        }
        int qualityHooks = hookPremiumQualitySwitch(classLoader);
        int hookCount7 = hookCount6 + qualityHooks;
        if (qualityHooks < 3) {
            unavailable.add("premium-quality-switch");
        }
        String summary = "bili hook: initialized (" + hookCount7 + " hooks)";
        if (!unavailable.isEmpty()) {
            summary = summary + ", unavailable=" + String.join(",", unavailable);
        }
        HookRuntime.log(summary);
    }

    private static boolean hookVip(String className, ClassLoader classLoader) {
        Class<?> vipClass = Reflect.findClassIfExists(className, classLoader);
        if (vipClass == null) {
            return false;
        }
        try {
            Reflect.findAndHookMethod(vipClass, "isEffectiveVip", new Object[]{new HookRuntime.Callback() { // from class: io.github.yylsping.bilihook.BiliHook.2
                protected void afterHookedMethod(HookRuntime.HookParam param) {
                    if (BiliHook.isLoggedIn()) {
                        BiliHook.realVipObserved = true;
                        BiliHook.realVipEffective = Boolean.TRUE.equals(param.getResult());
                        param.setResult(true);
                    }
                }
            }});
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isLoggedIn() {
        long now = SystemClock.elapsedRealtime();
        long checkedAt = cachedLoginStateAt;
        long cacheAge = now - checkedAt;
        if (checkedAt != 0L && cacheAge >= 0L && cacheAge < LOGIN_STATE_CACHE_TTL_MS) {
            return cachedLoginState;
        }
        try {
            Object accounts = accountsInstance;
            if (accounts == null) {
                Method getMethod = accountsGetMethod;
                Context context = accountsContext;
                if (getMethod == null || context == null || (accounts = getMethod.invoke(null, context)) == null) {
                    return false;
                }
                accountsInstance = accounts;
            }
            Method getMethod2 = accountsIsLoginMethod;
            if (getMethod2 == null) {
                return false;
            }
            Object result = getMethod2.invoke(accounts, NO_ARGS);
            boolean loggedIn = result instanceof Boolean && (Boolean) result;
            cachedLoginState = loggedIn;
            cachedLoginStateAt = now;
            return loggedIn;
        } catch (Throwable th) {
            return false;
        }
    }

    private static boolean hookPremiumQualityToast(ClassLoader classLoader) {
        Class<?> builderClass = Reflect.findClassIfExists("tv.danmaku.biliplayerv2.widget.toast.PlayerToast$Builder", classLoader);
        if (builderClass == null) {
            return false;
        }
        try {
            Reflect.findAndHookMethod(builderClass, "setExtraString", new Object[]{String.class, String.class, new HookRuntime.Callback() { // from class: io.github.yylsping.bilihook.BiliHook.3
                protected void beforeHookedMethod(HookRuntime.HookParam param) {
                    if (!"extra_title".equals(param.getArg(0)) || !(param.getArg(1) instanceof String)
                            || !BiliHook.realVipObserved || BiliHook.realVipEffective) {
                        return;
                    }
                    String message = (String) param.getArg(1);
                    if (message.contains("成功切换至4K") || message.contains("成功切换至1080P 高码率")) {
                        param.setArg(1, "服务端未返回所选会员画质，已保持当前实际画质");
                    }
                }
            }});
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    private static boolean hookNormalSplash(ClassLoader classLoader) {
        Class<?> splashClass = Reflect.findClassIfExists(NORMAL_SPLASH, classLoader);
        if (splashClass == null) {
            return false;
        }
        try {
            HookRuntime.hookReturnConstant(Reflect.findMethodExact(splashClass, "isValid"), false);
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    private static boolean hookRelatedCards(ClassLoader classLoader) {
        try {
            Class<?> replyClass = Reflect.findClass(RELATES_FEED_REPLY, classLoader);
            Class<?> cardClass = Reflect.findClass(RELATE_CARD, classLoader);
            Method getType = Reflect.findMethodExact(cardClass, "getRelateCardType", new Class[0]);
            Method hasCm = Reflect.findMethodExact(cardClass, "hasCm", new Class[0]);
            Method hasCmStock = Reflect.findMethodExact(cardClass, "hasCmStock", new Class[0]);
            Reflect.findAndHookMethod(replyClass, "getRelatesList", new Object[]{new AnonymousClass4(getType, hasCm, hasCmStock)});
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX INFO: renamed from: io.github.yylsping.bilihook.BiliHook$4, reason: invalid class name */
    static class AnonymousClass4 extends HookRuntime.Callback {
        final /* synthetic */ Method val$getType;
        final /* synthetic */ Method val$hasCm;
        final /* synthetic */ Method val$hasCmStock;

        AnonymousClass4(Method method, Method method2, Method method3) {
            this.val$getType = method;
            this.val$hasCm = method2;
            this.val$hasCmStock = method3;
        }

        protected void afterHookedMethod(HookRuntime.HookParam param) {
            Object result = param.getResult();
            if (!(result instanceof List)) {
                return;
            }
            List<?> source = (List) result;
            final Method method = this.val$getType;
            final Method method2 = this.val$hasCm;
            final Method method3 = this.val$hasCmStock;
            List<Object> filtered = BiliHook.filterList(source, new ItemPredicate() { // from class: io.github.yylsping.bilihook.BiliHook$4$$ExternalSyntheticLambda0
                @Override // io.github.yylsping.bilihook.BiliHook.ItemPredicate
                public final boolean test(Object obj) {
                    return BiliHook.isRelatedAd(obj, method, method2, method3);
                }
            });
            if (filtered != null) {
                param.setResult(filtered);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isRelatedAd(Object item, Method getType, Method hasCm, Method hasCmStock) {
        if (item == null) {
            return false;
        }
        try {
            Object type = getType.invoke(item, NO_ARGS);
            if ((type instanceof Enum) && "CM".equals(((Enum) type).name())) {
                return true;
            }
            return Boolean.TRUE.equals(hasCm.invoke(item, NO_ARGS)) || Boolean.TRUE.equals(hasCmStock.invoke(item, NO_ARGS));
        } catch (Throwable th) {
            return false;
        }
    }

    private static int hookLegacyVideoAds(ClassLoader classLoader) {
        int count = 0;
        try {
            Class<?> viewReplyClass = Reflect.findClass(LEGACY_VIEW_REPLY, classLoader);
            Class<?> feedReplyClass = Reflect.findClass(LEGACY_RELATES_FEED_REPLY, classLoader);
            Class<?> relateClass = Reflect.findClass(LEGACY_RELATE, classLoader);
            Class<?> anyClass = Reflect.findClass(PROTOBUF_ANY, classLoader);
            Method hasCm = Reflect.findMethodExact(relateClass, "hasCm", new Class[0]);
            Object emptyAny = Reflect.callStaticMethod(anyClass, "getDefaultInstance", new Object[0]);
            HookRuntime.Callback relateFilter = new AnonymousClass5(hasCm);
            Reflect.findAndHookMethod(viewReplyClass, "getRelatesList", new Object[]{relateFilter});
            int count2 = 0 + 1;
            Reflect.findAndHookMethod(feedReplyClass, "getListList", new Object[]{relateFilter});
            HookRuntime.hookReturnConstant(Reflect.findMethodExact(viewReplyClass, "getCmsList"), Collections.emptyList());
            HookRuntime.hookReturnConstant(Reflect.findMethodExact(viewReplyClass, "getCmsCount"), 0);
            HookRuntime.hookReturnConstant(Reflect.findMethodExact(viewReplyClass, "hasCmUnderPlayer"), false);
            count = count2 + 1 + 1 + 1 + 1;
            HookRuntime.hookReturnConstant(Reflect.findMethodExact(viewReplyClass, "getCmUnderPlayer"), emptyAny);
            return count + 1;
        } catch (Throwable th) {
            return count;
        }
    }

    /* JADX INFO: renamed from: io.github.yylsping.bilihook.BiliHook$5, reason: invalid class name */
    static class AnonymousClass5 extends HookRuntime.Callback {
        final /* synthetic */ Method val$hasCm;

        AnonymousClass5(Method method) {
            this.val$hasCm = method;
        }

        protected void afterHookedMethod(HookRuntime.HookParam param) {
            Object result = param.getResult();
            if (!(result instanceof List)) {
                return;
            }
            final Method method = this.val$hasCm;
            List<Object> filtered = BiliHook.filterList((List) result, new ItemPredicate() { // from class: io.github.yylsping.bilihook.BiliHook$5$$ExternalSyntheticLambda0
                @Override // io.github.yylsping.bilihook.BiliHook.ItemPredicate
                public final boolean test(Object obj) {
                    return BiliHook.isLegacyRelatedAd(obj, method);
                }
            });
            if (filtered != null) {
                param.setResult(filtered);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isLegacyRelatedAd(Object item, Method hasCm) {
        if (item == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(hasCm.invoke(item, NO_ARGS));
        } catch (Throwable th) {
            return false;
        }
    }

    private static int hookLegacyVideoAdModels(ClassLoader classLoader) {
        int count = 0;
        Class<?> modelClass = Reflect.findClassIfExists(LEGACY_VIDEO_MODEL, classLoader);
        try {
            Class<?> parserClass = Reflect.findClass(LEGACY_VIDEO_MOSS_PARSER, classLoader);
            Reflect.findAndHookMethod(parserClass, "b", new Object[]{new HookRuntime.Callback() { // from class: io.github.yylsping.bilihook.BiliHook.6
                protected void afterHookedMethod(HookRuntime.HookParam param) {
                    BiliHook.clearLegacyVideoAds(param.getResult());
                }
            }});
            count = 0 + 1;
        } catch (Throwable th) {
        }
        if (modelClass != null) {
            /*
             * Keep this optional legacy path isolated.  The original 1.4.1 DEX
             * has a catch-all around the whole AdSegment callback lookup/hook.
             * JADX flattened that catch while reconstructing the Java source.
             * On Bilibili 7.4.0 the callback class is absent, so the recovered
             * source used by 1.4.2 threw here and aborted installHooks before
             * the under-player, Pegasus home-feed, search and quality hooks
             * could be installed.
             */
            try {
                Class<?> callbackClass = Reflect.findClass(LEGACY_AD_SEGMENT_CALLBACK, classLoader);
                Reflect.findAndHookMethod(callbackClass, "c", new Object[]{modelClass, new HookRuntime.Callback() { // from class: io.github.yylsping.bilihook.BiliHook.7
                    protected void beforeHookedMethod(HookRuntime.HookParam param) {
                        BiliHook.clearLegacyVideoAds(param.getArg(0));
                    }
                }});
                count++;
            } catch (Throwable ignored) {
                // Optional class/method on older builds; continue installing all later hooks.
            }
        }
        try {
            Class<?> converterClass = Reflect.findClass(LEGACY_RELATE_CONVERTER, classLoader);
            Class<?> relateClass = Reflect.findClass(LEGACY_RELATE, classLoader);
            Method hasCm = Reflect.findMethodExact(relateClass, "hasCm", new Class[0]);
            Reflect.findAndHookMethod(converterClass, "V", new Object[]{List.class, new AnonymousClass8(hasCm)});
            return count + 1;
        } catch (Throwable th2) {
            return count;
        }
    }

    /* JADX INFO: renamed from: io.github.yylsping.bilihook.BiliHook$8, reason: invalid class name */
    static class AnonymousClass8 extends HookRuntime.Callback {
        final /* synthetic */ Method val$hasCm;

        AnonymousClass8(Method method) {
            this.val$hasCm = method;
        }

        protected void beforeHookedMethod(HookRuntime.HookParam param) {
            Object source = param.getArg(0);
            if (!(source instanceof List)) {
                return;
            }
            final Method method = this.val$hasCm;
            List<Object> filtered = BiliHook.filterList((List) source, new ItemPredicate() { // from class: io.github.yylsping.bilihook.BiliHook$8$$ExternalSyntheticLambda0
                @Override // io.github.yylsping.bilihook.BiliHook.ItemPredicate
                public final boolean test(Object obj) {
                    return BiliHook.isLegacyRelatedAd(obj, method);
                }
            });
            if (filtered != null) {
                param.setArg(0, filtered);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void clearLegacyVideoAds(Object detail) {
        if (detail == null) {
            return;
        }
        try {
            Reflect.setObjectField(detail, "cmUnderPlayer", (Object) null);
        } catch (Throwable th) {
        }
        try {
            Reflect.setObjectField(detail, "cms", (Object) null);
        } catch (Throwable th2) {
        }
    }

    private static int hookUnderPlayerAd(ClassLoader classLoader) {
        int count = 0;
        try {
            Class<?> cmClass = Reflect.findClass(VIEW_CM, classLoader);
            Class<?> anyClass = Reflect.findClass(PROTOBUF_ANY, classLoader);
            Object emptyAny = Reflect.callStaticMethod(anyClass, "getDefaultInstance", new Object[0]);
            HookRuntime.hookReturnConstant(Reflect.findMethodExact(cmClass, "hasCmUnderPlayer"), false);
            count = 0 + 1;
            HookRuntime.hookReturnConstant(Reflect.findMethodExact(cmClass, "getCmUnderPlayer"), emptyAny);
            return count + 1;
        } catch (Throwable th) {
            return count;
        }
    }

    private static int hookClassicUnderPlayerAdViews(ClassLoader classLoader) {
        int count = 0;
        Method rootView;
        try {
            Class<?> adAbsView = Reflect.findClass(
                    "com.bilibili.adcommon.biz.AdAbsView", classLoader);
            rootView = Reflect.findMethodExact(adAbsView, "M");
        } catch (Throwable ignored) {
            return 0;
        }

        // Every regular upper-ad holder finishes its bind in this override. This replaces
        // the old process-wide View.setVisibility hook with one callback per ad bind.
        try {
            Class<?> regularHolder = Reflect.findClass(
                    "com.bilibili.ad.adview.videodetail.upper.VideoUpperAdViewHolder",
                    classLoader);
            HookRuntime.hook(Reflect.findMethodExact(regularHolder, "a0"),
                    collapseUpperAdAfterBind(rootView));
            count++;
        } catch (Throwable ignored) {
        }

        // The nested under-player card is the only other AbsUpperView branch in 7.4.0.
        try {
            Class<?> nestedHolder = Reflect.findClass(
                    "com.bilibili.ad.adview.videodetail.upper.nested.AdNestedUpperHolder",
                    classLoader);
            HookRuntime.hook(Reflect.findMethodExact(nestedHolder, "a0"),
                    collapseUpperAdAfterBind(rootView));
            count++;
        } catch (Throwable ignored) {
        }
        return count;
    }

    private static HookRuntime.Callback collapseUpperAdAfterBind(final Method rootView) {
        return new HookRuntime.Callback() {
            @Override
            protected void afterHookedMethod(HookRuntime.HookParam param) {
                try {
                    Object root = rootView.invoke(param.thisObject);
                    if (root instanceof View) {
                        View view = (View) root;
                        view.setVisibility(View.GONE);
                        collapseReservedAdSpace(view);
                    }
                } catch (Throwable ignored) {
                }
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void collapseReservedAdSpace(View view) {
        try {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null) {
                return;
            }
            params.height = 0;
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                margins.topMargin = 0;
                margins.bottomMargin = 0;
            }
            view.setLayoutParams(params);
        } catch (Throwable th) {
        }
    }

    private static int hookHomeFeedAds(ClassLoader classLoader) {
        int count = 0;
        final Class<?> adItemClass = Reflect.findClassIfExists(PEGASUS_AD_ITEM, classLoader);
        final ItemPredicate outputAdPredicate = adItemClass == null
                ? null
                : item -> item != null && item.getClass() == adItemClass;
        try {
            Class<?> parserClass = Reflect.findClass(PEGASUS_BASE_PARSER, classLoader);
            final Class<?> jsonArrayClass = Reflect.findClass("com.alibaba.fastjson.JSONArray", classLoader);
            final Class<?> jsonObjectClass = Reflect.findClass("com.alibaba.fastjson.JSONObject", classLoader);
            final Constructor<?> jsonArrayConstructor = jsonArrayClass.getDeclaredConstructor();
            jsonArrayConstructor.setAccessible(true);
            final ItemPredicate rawJsonAdPredicate = item ->
                    BiliHook.isPegasusRawJsonAd(item, jsonObjectClass);
            for (Method method : parserClass.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && parameters[0] == jsonArrayClass && List.class.isAssignableFrom(method.getReturnType())) {
                    HookRuntime.hook(method, new HookRuntime.Callback() { // from class: io.github.yylsping.bilihook.BiliHook.11
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            Object filtered = BiliHook.filterPegasusJsonInput(
                                    param.getArg(0), jsonArrayConstructor, rawJsonAdPredicate);
                            if (filtered != null) {
                                param.setArg(0, filtered);
                            }
                        }
                    });
                    count = 0 + 1;
                    break;
                }
            }
        } catch (Throwable th) {
        }
        try {
            Class<?> converterClass = Reflect.findClass(PEGASUS_BRPC_CONVERTER, classLoader);
            Class<?> cardOrBuilderClass = Reflect.findClass(PEGASUS_CARD_OR_BUILDER, classLoader);
            final Method getItemCase = Reflect.findMethodExact(cardOrBuilderClass, "getItemCase");
            final ItemPredicate rawBrpcAdPredicate = item ->
                    BiliHook.isPegasusRawBrpcAd(item, getItemCase);
            Reflect.findAndHookMethod(converterClass, "a", List.class,
                    new HookRuntime.Callback() {
                        @Override
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            Object source = param.getArg(0);
                            if (!(source instanceof List)) return;
                            List<Object> filtered = BiliHook.filterList(
                                    (List<?>) source, rawBrpcAdPredicate);
                            if (filtered != null) param.setArg(0, filtered);
                        }

                        @Override
                        protected void afterHookedMethod(HookRuntime.HookParam param) {
                            BiliHook.filterPegasusResult(param, outputAdPredicate);
                        }
                    });
            return count + 1;
        } catch (Throwable th2) {
            return count;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    @SuppressWarnings("unchecked")
    public static Object filterPegasusJsonInput(
            Object source, Constructor<?> jsonArrayConstructor, ItemPredicate predicate) {
        List<Object> filtered;
        if (!(source instanceof List)
                || (filtered = filterList((List<?>) source, predicate)) == null) {
            return null;
        }
        try {
            Object replacement = jsonArrayConstructor.newInstance();
            ((List<Object>) replacement).addAll(filtered);
            return replacement;
        } catch (Throwable th) {
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isPegasusRawJsonAd(Object item, Class<?> jsonObjectClass) {
        if (!jsonObjectClass.isInstance(item) || !(item instanceof Map)) {
            return false;
        }
        Map<?, ?> values = (Map<?, ?>) item;
        if (isFastJsonTrue(values.get("is_ad"))) return true;

        Object adInfo = values.get("ad_info");
        if (adInfo instanceof Map) return true;

        Object cardType = values.get("card_type");
        return cardType != null && isPegasusAdCardType(cardType.toString());
    }

    private static boolean isFastJsonTrue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        if (!(value instanceof String)) return false;
        String text = (String) value;
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static boolean isPegasusAdCardType(String cardType) {
        int length = cardType.length();
        if (length == 2) return cardType.equalsIgnoreCase("ad");
        if (length >= 3 && cardType.regionMatches(true, 0, "ad_", 0, 3)) return true;
        for (int index = 0; index <= length - 4; index++) {
            if (cardType.regionMatches(true, index, "_ad_", 0, 4)) return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isPegasusRawBrpcAd(Object item, Method getItemCase) {
        if (item == null) {
            return false;
        }
        try {
            Object itemCase = getItemCase.invoke(item, NO_ARGS);
            if (itemCase instanceof Enum) {
                String name = ((Enum) itemCase).name();
                return name.startsWith("AD_") || name.endsWith("_AD") || name.contains("_AD_");
            }
        } catch (Throwable th) {
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void filterPegasusResult(
            HookRuntime.HookParam param, ItemPredicate outputAdPredicate) {
        if (outputAdPredicate == null) return;
        List<Object> filtered;
        Object result = param.getResult();
        if (result instanceof List) {
            List<?> source = (List<?>) result;
            filtered = filterList(source, outputAdPredicate);
            if (filtered != null) param.setResult(filtered);
        }
    }

    /**
     * Keeps the original premium-quality request rewriting, but never rewrites the
     * player's source-change callback. Version 1.4.1 changed a successful
     * callback into a failure while the service still exposed the old DASH list;
     * the server-selected quality was nevertheless persisted and appeared after
     * reopening the video. Letting PlayerQualityService consume its genuine
     * callback updates both playback and the on-screen quality state immediately.
     */
    private static int hookPremiumQualitySwitch(ClassLoader classLoader) {
        int count = 0;
        final Class<?> serviceClass = Reflect.findClassIfExists(PLAYER_QUALITY_SERVICE, classLoader);
        if (serviceClass == null) {
            return 0;
        }

        try {
            Reflect.findAndHookMethod(serviceClass, "q2", Integer.TYPE, String.class, new HookRuntime.Callback() {
                @Override
                protected void beforeHookedMethod(HookRuntime.HookParam param) {
                    rememberRequestedPremiumQuality(((Integer) param.getArg(0)).intValue());
                }
            });
            count++;
        } catch (Throwable ignored) {
        }

        try {
            Reflect.findAndHookMethod(serviceClass, "j2", Integer.TYPE, String.class, new HookRuntime.Callback() {
                @Override
                protected void beforeHookedMethod(HookRuntime.HookParam param) {
                    rememberRequestedPremiumQuality(((Integer) param.getArg(0)).intValue());
                }
            });
            count++;
        } catch (Throwable ignored) {
        }

        // Observe completion only. Do not mutate success, requested quality,
        // actual quality, pending fields, display state, or failure callbacks.
        try {
            Reflect.findAndHookMethod(serviceClass, "a",
                    Boolean.TYPE, Integer.TYPE, Integer.TYPE, Boolean.TYPE,
                    new HookRuntime.Callback() {
                        @Override
                        protected void afterHookedMethod(HookRuntime.HookParam param) {
                            desiredPremiumQuality.set(0);
                        }
                    });
            count++;
        } catch (Throwable ignored) {
        }

        try {
            Class<?> callbackClass = Reflect.findClass(QUALITY_LIST_CALLBACK, classLoader);
            Class<?> itemClass = Reflect.findClass(QUALITY_LIST_ITEM, classLoader);
            Reflect.findAndHookMethod(callbackClass, "a", itemClass, Boolean.TYPE, new HookRuntime.Callback() {
                @Override
                protected void beforeHookedMethod(HookRuntime.HookParam param) {
                    try {
                        Object playIndex = Reflect.callMethod(param.getArg(0), "b");
                        int quality = Reflect.getIntField(playIndex, "mQuality");
                        rememberRequestedPremiumQuality(quality);
                    } catch (Throwable ignored) {
                        desiredPremiumQuality.set(0);
                    }
                }
            });
            count++;
        } catch (Throwable ignored) {
        }

        count += hookUgcResolverQuality(classLoader);

        String[] requestClasses = {
                "com.bapis.bilibili.app.playurl.v1.PlayViewReq",
                "com.bapis.bilibili.app.playurl.v1.PlayViewReq$b",
                "com.bapis.bilibili.app.playurl.v1.PlayURLReq",
                "com.bapis.bilibili.app.playurl.v1.PlayURLReq$b",
                "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq",
                "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq$b",
                "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq",
                "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq$b"
        };
        for (String className : requestClasses) {
            Class<?> requestClass = Reflect.findClassIfExists(className, classLoader);
            if (requestClass == null) {
                continue;
            }
            for (final Method method : requestClass.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if ("setQn".equals(method.getName()) && parameterTypes.length == 1) {
                    HookRuntime.hook(method, new HookRuntime.Callback() {
                        @Override
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            int quality = desiredPremiumQuality.get();
                            if (quality < PREMIUM_QUALITY_MIN || !isLoggedIn()) {
                                return;
                            }
                            Class<?> type = method.getParameterTypes()[0];
                            if (type == Long.TYPE || type == Long.class) {
                                param.setArg(0, Long.valueOf(quality));
                            } else if (type == Integer.TYPE || type == Integer.class) {
                                param.setArg(0, Integer.valueOf(quality));
                            }
                        }
                    });
                    count++;
                } else if ("setFourk".equals(method.getName())
                        && parameterTypes.length == 1
                        && parameterTypes[0] == Boolean.TYPE) {
                    HookRuntime.hook(method, new HookRuntime.Callback() {
                        @Override
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            if (desiredPremiumQuality.get() >= 120 && isLoggedIn()) {
                                param.setArg(0, Boolean.TRUE);
                            }
                        }
                    });
                    count++;
                }
            }
        }
        return count;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void rememberRequestedPremiumQuality(int quality) {
        int remembered = quality >= PREMIUM_QUALITY_MIN ? quality : 0;
        desiredPremiumQuality.set(remembered);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean hasPlayableDashQuality(Object service, int quality) {
        Object dashResource;
        try {
            Object mediaResource = getCurrentMediaResource(service);
            if (mediaResource == null || (dashResource = callFirstNoArg(mediaResource, "getDashResource", "h")) == null) {
                return false;
            }
            Object videoIndexes = callFirstNoArg(dashResource, "b", "h");
            if (!(videoIndexes instanceof Iterable)) {
                return false;
            }
            for (Object videoIndex : (Iterable) videoIndexes) {
                if (videoIndex != null) {
                    Object id = callFirstNoArg(videoIndex, "u", "n");
                    if ((id instanceof Integer) && ((Integer) id).intValue() == quality) {
                        Object url = callFirstNoArg(videoIndex, "k", "i");
                        return (url instanceof String) && !((String) url).isEmpty();
                    }
                }
            }
        } catch (Throwable th) {
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object getCurrentMediaResource(Object service) {
        return callFirstNoArg(service, "T0", "L0");
    }

    private static Object callFirstNoArg(Object receiver, String... names) {
        int length = names.length;
        for (int i = 0; i < length; i++) {
            String name = names[i];
            try {
                return Reflect.callMethod(receiver, name, new Object[0]);
            } catch (Throwable th) {
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void setPendingQuality(Object service, int quality) {
        String[] strArr = {"f132566g", "f130171g"};
        for (int i = 0; i < 2; i++) {
            String field = strArr[i];
            try {
                Reflect.setIntField(service, field, quality);
                return;
            } catch (Throwable th) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int getPendingQuality(Object service) {
        int i = 2;
        String[] strArr = {"f132566g", "f130171g"};
        for (int i2 = 0; i2 < i; i2++) {
            String field = strArr[i2];
            try {
                return Reflect.getIntField(service, field);
            } catch (Throwable th) {
            }
        }
        return -1;
    }

    private static int hookUgcResolverQuality(ClassLoader classLoader) {
        int count = 0;
        try {
            Class<?> resolverClass = Reflect.findClass(UGC_RESOLVER, classLoader);
            Class<?> resolveParamsClass = Reflect.findClass(RESOLVE_PARAMS, classLoader);
            Reflect.findAndHookMethod(resolverClass, "resolveMediaResource", new Object[]{Context.class, resolveParamsClass, new HookRuntime.Callback() { // from class: io.github.yylsping.bilihook.BiliHook.21
                protected void beforeHookedMethod(HookRuntime.HookParam param) {
                    int quality = BiliHook.desiredPremiumQuality.get();
                    if (quality < BiliHook.PREMIUM_QUALITY_MIN || !BiliHook.isLoggedIn()) {
                        return;
                    }
                    try {
                        Reflect.callMethod(param.getArg(1), "setRealQuality", new Object[]{Long.valueOf(quality)});
                    } catch (Throwable th) {
                    }
                }
            }});
            count = 0 + 1;
            for (Method method : resolverClass.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("f".equals(method.getName()) && parameters.length == 13 && parameters[0] == Long.TYPE && parameters[1] == Long.TYPE && parameters[2] == Long.TYPE) {
                    HookRuntime.hook(method, new HookRuntime.Callback() { // from class: io.github.yylsping.bilihook.BiliHook.22
                        protected void beforeHookedMethod(HookRuntime.HookParam param) {
                            int quality = BiliHook.desiredPremiumQuality.get();
                            if (quality >= BiliHook.PREMIUM_QUALITY_MIN && BiliHook.isLoggedIn()) {
                                param.setArg(2, Long.valueOf(quality));
                            }
                        }
                    });
                    count++;
                }
            }
        } catch (Throwable th) {
        }
        return count;
    }

    private static boolean hookSearchCards(ClassLoader classLoader) {
        try {
            Class<?> converterClass = Reflect.findClass(SEARCH_CONVERTER, classLoader);
            Class<?> searchResultClass = Reflect.findClass(SEARCH_RESULT_ALL, classLoader);
            Class<?> itemClass = Reflect.findClass(SEARCH_ITEM, classLoader);
            Method getCardItemCase = Reflect.findMethodExact(itemClass, "getCardItemCase", new Class[0]);
            Reflect.findAndHookMethod(converterClass, "a",
                    new Object[]{List.class, searchResultClass,
                            new AnonymousClass23(getCardItemCase)});
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX INFO: renamed from: io.github.yylsping.bilihook.BiliHook$23, reason: invalid class name */
    static class AnonymousClass23 extends HookRuntime.Callback {
        final /* synthetic */ Method val$getCardItemCase;

        AnonymousClass23(Method method) {
            this.val$getCardItemCase = method;
        }

        protected void beforeHookedMethod(HookRuntime.HookParam param) {
            Object sourceArg = param.getArg(0);
            if (!(sourceArg instanceof List)) {
                return;
            }
            final Method method = this.val$getCardItemCase;
            List<Object> filtered = BiliHook.filterList((List) sourceArg, new ItemPredicate() { // from class: io.github.yylsping.bilihook.BiliHook$23$$ExternalSyntheticLambda0
                @Override // io.github.yylsping.bilihook.BiliHook.ItemPredicate
                public final boolean test(Object obj) {
                    return BiliHook.isSearchAd(obj, method);
                }
            });
            if (filtered != null) {
                param.setArg(0, filtered);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isSearchAd(Object item, Method getCardItemCase) {
        if (item == null) {
            return false;
        }
        try {
            Object type = getCardItemCase.invoke(item, NO_ARGS);
            return (type instanceof Enum) && "CM".equals(((Enum) type).name());
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static List<Object> filterList(List<?> source, ItemPredicate predicate) {
        boolean remove;
        ArrayList<Object> filtered = null;
        int size = source.size();
        for (int i = 0; i < size; i++) {
            Object item = source.get(i);
            try {
                remove = predicate.test(item);
            } catch (Throwable th) {
                remove = false;
            }
            if (remove) {
                if (filtered == null) {
                    ArrayList<Object> filtered2 = new ArrayList<>(Math.max(0, size - 1));
                    for (int j = 0; j < i; j++) {
                        filtered2.add(source.get(j));
                    }
                    filtered = filtered2;
                }
            } else if (filtered != null) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
