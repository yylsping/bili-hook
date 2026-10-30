package io.github.yylsping.bilihook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact, independently-failing advertising hooks for Bilibili 7.42.0. */
final class Bili742AdHooks {
    private static final Object[] NO_ARGS = new Object[0];

    private Bili742AdHooks() {}

    static int install(ClassLoader classLoader, List<String> unavailable) {
        int count = 0;
        count += installFeature("splash-ad", unavailable,
                () -> hookNormalSplash(classLoader));
        count += installFeature("related-cm", unavailable,
                () -> hookRelatedCards(classLoader));
        count += installFeature("legacy-video-cm", unavailable,
                () -> hookLegacyVideoData(classLoader));
        count += installFeature("under-player-cm", unavailable,
                () -> hookUnderPlayerData(classLoader));
        count += installFeature("home-feed-json-cm", unavailable,
                () -> hookHomeFeedJson(classLoader));
        count += installFeature("home-feed-brpc-cm", unavailable,
                () -> hookHomeFeedBrpc(classLoader));
        count += installFeature("search-cm", unavailable,
                () -> hookSearch(classLoader, "com.bilibili.search"));
        count += installFeature("search2-cm", unavailable,
                () -> hookSearch(classLoader, "com.bilibili.search2"));
        return count;
    }

    private static int installFeature(
            String name, List<String> unavailable, FeatureInstaller installer) {
        try {
            int count = installer.install();
            if (count == 0) unavailable.add(name);
            return count;
        } catch (Throwable error) {
            unavailable.add(name);
            HookRuntime.log("7.42.0 ad hook unavailable: " + name, error);
            return 0;
        }
    }

    private static int hookNormalSplash(ClassLoader classLoader) throws Throwable {
        Class<?> splashClass = Reflect.findClass(
                "tv.danmaku.bili.ui.splash.ad.model.Splash", classLoader);
        final Method isBirthday = Reflect.findMethodExact(splashClass, "isBirthSplash");
        Reflect.findAndHookMethod(splashClass, "isValid", new HookRuntime.Callback() {
            @Override
            protected void afterHookedMethod(HookRuntime.HookParam param) throws Throwable {
                if (!Boolean.TRUE.equals(isBirthday.invoke(param.thisObject, NO_ARGS))) {
                    param.setResult(false);
                }
            }
        });
        return 1;
    }

    private static int hookRelatedCards(ClassLoader classLoader) throws Throwable {
        Class<?> replyClass = Reflect.findClass(
                "com.bapis.bilibili.app.viewunite.v1.RelatesFeedReply", classLoader);
        Class<?> cardClass = Reflect.findClass(
                "com.bapis.bilibili.app.viewunite.common.RelateCard", classLoader);
        final Method getType = Reflect.findMethodExact(cardClass, "getRelateCardType");
        final Method hasCm = Reflect.findMethodExact(cardClass, "hasCm");
        final Method hasCmStock = Reflect.findMethodExact(cardClass, "hasCmStock");
        Reflect.findAndHookMethod(replyClass, "getRelatesList", new HookRuntime.Callback() {
            @Override
            protected void afterHookedMethod(HookRuntime.HookParam param) {
                Object result = param.getResult();
                if (!(result instanceof List)) return;
                List<Object> filtered = filter((List<?>) result, item ->
                        isRelatedAd(item, getType, hasCm, hasCmStock));
                if (filtered != null) param.setResult(filtered);
            }
        });
        return 1;
    }

    private static int hookLegacyVideoData(ClassLoader classLoader) throws Throwable {
        Class<?> viewReplyClass = Reflect.findClass(
                "com.bapis.bilibili.app.view.v1.ViewReply", classLoader);
        Class<?> feedReplyClass = Reflect.findClass(
                "com.bapis.bilibili.app.view.v1.RelatesFeedReply", classLoader);
        Class<?> relateClass = Reflect.findClass(
                "com.bapis.bilibili.app.view.v1.Relate", classLoader);
        Class<?> anyClass = Reflect.findClass("com.google.protobuf.Any", classLoader);
        final Method hasCm = Reflect.findMethodExact(relateClass, "hasCm");
        Object emptyAny = Reflect.callStaticMethod(anyClass, "getDefaultInstance");
        HookRuntime.Callback filter = new HookRuntime.Callback() {
            @Override
            protected void afterHookedMethod(HookRuntime.HookParam param) {
                Object result = param.getResult();
                if (!(result instanceof List)) return;
                List<Object> filtered = filter((List<?>) result,
                        item -> invokeBoolean(hasCm, item));
                if (filtered != null) param.setResult(filtered);
            }
        };

        int count = 0;
        Reflect.findAndHookMethod(viewReplyClass, "getRelatesList", filter);
        count++;
        Reflect.findAndHookMethod(feedReplyClass, "getListList", filter);
        count++;
        HookRuntime.hookReturnConstant(
                Reflect.findMethodExact(viewReplyClass, "getCmsList"), Collections.emptyList());
        count++;
        HookRuntime.hookReturnConstant(
                Reflect.findMethodExact(viewReplyClass, "getCmsCount"), 0);
        count++;
        HookRuntime.hookReturnConstant(
                Reflect.findMethodExact(viewReplyClass, "hasCmUnderPlayer"), false);
        count++;
        HookRuntime.hookReturnConstant(
                Reflect.findMethodExact(viewReplyClass, "getCmUnderPlayer"), emptyAny);
        return count + 1;
    }

    private static int hookUnderPlayerData(ClassLoader classLoader) throws Throwable {
        Class<?> cmClass = Reflect.findClass(
                "com.bapis.bilibili.app.viewunite.v1.CM", classLoader);
        Class<?> anyClass = Reflect.findClass("com.google.protobuf.Any", classLoader);
        Object emptyAny = Reflect.callStaticMethod(anyClass, "getDefaultInstance");
        HookRuntime.hookReturnConstant(
                Reflect.findMethodExact(cmClass, "hasCmUnderPlayer"), false);
        HookRuntime.hookReturnConstant(
                Reflect.findMethodExact(cmClass, "getCmUnderPlayer"), emptyAny);
        return 2;
    }

    private static int hookHomeFeedJson(ClassLoader classLoader) throws Throwable {
        Class<?> parserClass = Reflect.findClass(
                "com.bilibili.pegasus.api.BaseTMApiParser", classLoader);
        final Class<?> jsonArrayClass = Reflect.findClass(
                "com.alibaba.fastjson.JSONArray", classLoader);
        final Class<?> jsonObjectClass = Reflect.findClass(
                "com.alibaba.fastjson.JSONObject", classLoader);
        final Constructor<?> constructor = jsonArrayClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Reflect.findAndHookMethod(parserClass, "e", jsonArrayClass,
                new HookRuntime.Callback() {
                    @Override
                    @SuppressWarnings("unchecked")
                    protected void beforeHookedMethod(HookRuntime.HookParam param)
                            throws Throwable {
                        Object source = param.getArg(0);
                        if (!(source instanceof List)) return;
                        List<Object> filtered = filter((List<?>) source,
                                item -> BiliHook.isPegasusRawJsonAd(item, jsonObjectClass));
                        if (filtered == null) return;
                        Object replacement = constructor.newInstance();
                        ((List<Object>) replacement).addAll(filtered);
                        param.setArg(0, replacement);
                    }
                });
        return 1;
    }

    private static int hookHomeFeedBrpc(ClassLoader classLoader) throws Throwable {
        Class<?> converterClass = Reflect.findClass(
                "com.bilibili.pegasus.utils.BrpcRespConverterKt", classLoader);
        Class<?> cardClass = Reflect.findClass(
                "com.bapis.bilibili.app.card.v1.CardOrBuilder", classLoader);
        final Class<?> adItemClass = Reflect.findClass(
                "com.bilibili.pegasus.api.modelv2.AdItem", classLoader);
        final Method getItemCase = Reflect.findMethodExact(cardClass, "getItemCase");
        Reflect.findAndHookMethod(converterClass, "a", List.class,
                new HookRuntime.Callback() {
                    @Override
                    protected void beforeHookedMethod(HookRuntime.HookParam param) {
                        Object source = param.getArg(0);
                        if (!(source instanceof List)) return;
                        List<Object> filtered = filter((List<?>) source,
                                item -> isAdEnumCase(item, getItemCase));
                        if (filtered != null) param.setArg(0, filtered);
                    }

                    @Override
                    protected void afterHookedMethod(HookRuntime.HookParam param) {
                        Object result = param.getResult();
                        if (!(result instanceof List)) return;
                        List<Object> filtered = filter((List<?>) result,
                                item -> item != null && item.getClass() == adItemClass);
                        if (filtered != null) param.setResult(filtered);
                    }
                });
        return 1;
    }

    private static int hookSearch(ClassLoader classLoader, String packageName) throws Throwable {
        Class<?> converterClass = Reflect.findClass(
                packageName + ".utils.BrpcSearchResultConverterKt", classLoader);
        Class<?> resultClass = Reflect.findClass(
                packageName + ".api.SearchResultAll", classLoader);
        Class<?> itemClass = Reflect.findClass(
                "com.bapis.bilibili.polymer.app.search.v1.Item", classLoader);
        final Method getCardItemCase = Reflect.findMethodExact(itemClass, "getCardItemCase");
        Reflect.findAndHookMethod(converterClass, "a", List.class, resultClass,
                new HookRuntime.Callback() {
                    @Override
                    protected void beforeHookedMethod(HookRuntime.HookParam param) {
                        Object source = param.getArg(0);
                        if (!(source instanceof List)) return;
                        List<Object> filtered = filterSearchCards((List<?>) source, getCardItemCase);
                        if (filtered != null) {
                            if (BuildConfig.DEBUG) HookRuntime.log("7.42.0 " + packageName
                                    + " removed CM=" + (((List<?>) source).size() - filtered.size()));
                            param.setArg(0, filtered);
                        }
                    }
                });
        return 1;
    }

    /** Both shipped search stacks use the same protobuf oneof; ordinary AV cards stay intact. */
    static List<Object> filterSearchCards(List<?> source, Method getCardItemCase) {
        return filter(source, item -> enumName(item, getCardItemCase, "CM"));
    }

    static boolean isAdEnumName(String name) {
        return name != null
                && (name.startsWith("AD_") || name.endsWith("_AD")
                || name.contains("_AD_"));
    }

    private static boolean isRelatedAd(
            Object item, Method getType, Method hasCm, Method hasCmStock) {
        if (item == null) return false;
        try {
            Object type = getType.invoke(item, NO_ARGS);
            return type instanceof Enum && "CM".equals(((Enum<?>) type).name())
                    || invokeBoolean(hasCm, item) || invokeBoolean(hasCmStock, item);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isAdEnumCase(Object item, Method getter) {
        if (item == null) return false;
        try {
            Object value = getter.invoke(item, NO_ARGS);
            return value instanceof Enum && isAdEnumName(((Enum<?>) value).name());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean enumName(Object item, Method getter, String expected) {
        if (item == null) return false;
        try {
            Object value = getter.invoke(item, NO_ARGS);
            return value instanceof Enum && expected.equals(((Enum<?>) value).name());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean invokeBoolean(Method method, Object receiver) {
        try {
            return Boolean.TRUE.equals(method.invoke(receiver, NO_ARGS));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<Object> filter(List<?> source, Predicate predicate) {
        ArrayList<Object> result = null;
        for (int index = 0; index < source.size(); index++) {
            Object item = source.get(index);
            if (predicate.test(item)) {
                if (result == null) {
                    result = new ArrayList<>(source.size() - 1);
                    result.addAll(source.subList(0, index));
                }
            } else if (result != null) {
                result.add(item);
            }
        }
        return result;
    }

    private interface FeatureInstaller {
        int install() throws Throwable;
    }

    private interface Predicate {
        boolean test(Object value);
    }
}
