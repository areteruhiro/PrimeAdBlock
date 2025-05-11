package github.hiro.primevideo;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dalvik.system.DexFile;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Main implements IXposedHookLoadPackage {

    private volatile boolean AD_TIME = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {


     //hookAllAdClasses(loadPackageParam);
      //  nullifyHttpAdClassesSafely(loadPackageParam);


        Class<?> targetClass = XposedHelpers.findClassIfExists(
                "com.amazon.avod.ads.api.internal.AdInfoNode",
                loadPackageParam.classLoader
        );

        Class<?> creativeHolderClass = XposedHelpers.findClassIfExists(
                "com.amazon.avod.ads.api.internal.AdCreativeHolderNode",
                loadPackageParam.classLoader
        );

        if (targetClass != null && creativeHolderClass != null) {
            XposedHelpers.findAndHookMethod(
                    targetClass,
                    "getDisplayableAds",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object httpClient = XposedHelpers.getObjectField(param.thisObject, "mHttpClient");
                                Object dummyHolder = XposedHelpers.newInstance(
                                        creativeHolderClass,
                                        param.thisObject,
                                        httpClient,
                                        null
                                );
                                Object dummyNode = XposedHelpers.newInstance(
                                        XposedHelpers.findClassIfExists(
                                                "com.amazon.avod.ads.api.internal.AdInfoNode",
                                                loadPackageParam.classLoader),
                                        httpClient,
                                        false
                                );

                                XposedHelpers.setObjectField(dummyNode, "mInfoContainer", dummyHolder);
                                List<Object> result = Collections.singletonList(dummyNode);
                                param.setResult(result);

                                XposedBridge.log("✅ Successfully injected fully initialized dummy node");

                            } catch (Throwable t) {
                                XposedBridge.log("E" + t);
                                param.setResult(Collections.emptyList());
                            }
                        }
                    }
            );
        }try {
            Class<?> hook = XposedHelpers.findClass(
                    "com.amazon.avod.media.ads.internal.AdEnabledVideoPlayer",
                    loadPackageParam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                    hook,
                    "getCurrentPosition",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!AD_TIME) return;

                            try {

                                Object mPlayer = XposedHelpers.getObjectField(param.thisObject, "mPlayer");
                                long rawPosition = (long) XposedHelpers.callMethod(mPlayer, "getCurrentPosition");

                                Object timelineManager = XposedHelpers.getObjectField(param.thisObject, "mTimelineManager");
                                long adFreeNano = (long) XposedHelpers.callMethod(
                                        timelineManager,
                                        "getPositionExcludingAdsInNanos",
                                        TimeUnit.MILLISECONDS.toNanos(rawPosition)
                                );
                                long finalResult = TimeUnit.NANOSECONDS.toMillis(adFreeNano);

                                Object currentAdBreak = XposedHelpers.getObjectField(mPlayer, "mCurrentAdBreak");
                                if (currentAdBreak != null) {
                                    Object adDuration = XposedHelpers.callMethod(currentAdBreak, "getDurationExcludingAux");
                                    long adDurationMs = (long) XposedHelpers.callMethod(
                                            adDuration,
                                            "getTotalMilliseconds"
                                    );

                                    long adjustedResult = finalResult - adDurationMs;
                                    adjustedResult = Math.max(adjustedResult, 0);

                                    param.setResult(adjustedResult);
                                    XposedBridge.log("[AdSkip] Raw: " + rawPosition + "ms | " +
                                            "Ad Duration: " + adDurationMs + "ms | " +
                                            "Adjusted: " + adjustedResult + "ms");
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[AdSkip Error] " + t);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("[Hook Init Error] " + t);
        }




                try {
                    targetClass = XposedHelpers.findClassIfExists(
                        "com.amazon.avod.ads.api.internal.AdInfoNode",
                        loadPackageParam.classLoader);
                if (targetClass != null) {
                    XposedHelpers.findAndHookMethod(
                            targetClass,
                            "getDisplayableAds",
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    param.setResult(Collections.emptyList()); // 空リストを返す
                                    XposedBridge.log("✅ Successfully injected fully initialized dummy node");

                                }
                            }
                    );
                }
            } catch (Throwable ignored) {
            }

        try {
           targetClass = XposedHelpers.findClassIfExists(
                    "com.amazon.avod.ads.api.AdManifest",
                    loadPackageParam.classLoader);
            if (targetClass != null) {
                XposedHelpers.findAndHookMethod(
                        targetClass,
                        "getAdBreaks",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object result = param.getResult();
                                String logMsg = String.format(
                                        (result != null) ? result.toString() : "null"
                                );

                                if (logMsg.length() > 500) {
                                    logMsg = logMsg.substring(0, 500) + "...[truncated]";
                                }
                                XposedBridge.log(logMsg);
                                if (result instanceof List) {
                                    List<?> adBreaks = (List<?>) result;
                                    if (adBreaks.isEmpty()) {
                                        AD_TIME = false;
                                        XposedBridge.log("AD_TIME false");
                                    } else {
                                        XposedBridge.log("AD_TIME true");
                                        AD_TIME = true;
                                    }
                                }

                                if (param.hasThrowable()) {
                                    XposedBridge.log("⚠ Exception: " +
                                            param.getThrowable().getClass().getName() +
                                            ": " + param.getThrowable().getMessage());
                                }

                                param.setResult(Collections.emptyList());
                            }
                        }
                );
            }
        } catch (Throwable ignored) {
        }



        try {
             targetClass = XposedHelpers.findClassIfExists(
                    "com.amazon.avod.ads.parser.parsers.VmapTrackingEventsParser",
                    loadPackageParam.classLoader);
            if (targetClass != null) {
                XposedHelpers.findAndHookMethod(
                        targetClass,
                        "parse",
                        XmlPullParser.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                param.setResult(Collections.emptyList());
                                XposedBridge.log("VmapTrackingEventsParser.parse has been hooked to return an empty list.");
                            }
                        }
                );
            }
        } catch (Throwable ignored) {
        }
            try {
               targetClass = XposedHelpers.findClassIfExists(
                        "com.amazon.avod.playbackclient.ads.AdLifecycleListenerProxy",
                        loadPackageParam.classLoader);

                if (targetClass != null) {
                    XposedHelpers.findAndHookMethod(
                            targetClass,
                            "onBeginAdClip",
                            "com.amazon.avod.media.ads.AdClip",
                            "com.amazon.avod.playbackclient.control.PlaybackController",
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    param.setResult(null);
                                    XposedBridge.log("[AdBlock] Ad event blocked at proxy level");
                                }
                            }
                    );
                } else {
                    XposedBridge.log("[AdBlock] AdLifecycleListenerProxy not found, skipping hook");
                }
            } catch (Throwable t) {
            }
            try {
                targetClass = XposedHelpers.findClassIfExists(
                        "com.amazon.avod.playbackclient.control.AdEnabledVideoClientPresentation",
                        loadPackageParam.classLoader);
                if (targetClass != null) {

                    XposedHelpers.findAndHookMethod(
                            targetClass,
                            "addAdLifecycleListener",
                            "com.amazon.avod.playbackclient.ads.AdLifecycleListener", // 完全修飾クラス名を文字列で指定
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    param.setResult(null);
                                 //   XposedBridge.log("[AdBlock] Listener registration blocked");
                                }
                            }
                    );
                }
            } catch (Throwable ignored) {
            }
            try {
            Class<?> targetClass1 = XposedHelpers.findClass("com.amazon.avod.media.ads.internal.AdManagerBasedAdBreak",loadPackageParam.classLoader);
            if (targetClass1 != null) {

                XposedBridge.hookAllMethods(targetClass1, "initializeClips", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // nullを返す
                        XposedBridge.log("[initializeClips] ");
                        AD_TIME = true;
                        param.setResult(null);
                    }

                });

            }
            } catch (Throwable ignored) {
            }
            try {
                Class<?> targetClass1 = XposedHelpers.findClass("com.amazon.avod.ads.api.internal.AdInfoNode",loadPackageParam.classLoader);
            XposedHelpers.findAndHookMethod(
                    targetClass1,
                    "getAdId",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult("0");
                            XposedBridge.log("[AdBlock] ");
                        }
                    }
            );
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(
                    "com.amazon.avod.ads.parser.vast.VastAd",
                    loadPackageParam.classLoader,
                    "getAdId",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult("0");
                            XposedBridge.log("[AdBlock] ");
                        }
                    }
            );
            } catch (Throwable ignored) {
            }


    }


    private void nullifyHttpAdClassesSafely(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String apkPath = lpparam.appInfo.sourceDir;
            if (apkPath == null) return;

            DexFile dexFile = new DexFile(apkPath);
            Enumeration<String> classNames = dexFile.entries();
            int safeNullifiedCount = 0;

            while (classNames.hasMoreElements()) {
                String className = classNames.nextElement();

                if (!className.startsWith("com.amazon.avod.ads.http")) {
                    continue;
                }
                try {
                    Class<?> clazz = Class.forName(className, false, lpparam.classLoader);
                    if (className.equals("com.amazon.avod.ads.http.HttpParameters$Builder")) {
                        continue;
                    }
                    safeNullifyMethods(clazz);
                    safeNullifiedCount++;
                } catch (Throwable ignored) {
                }
            }

            XposedBridge.log("🛡️ Safely nullified classes: " + safeNullifiedCount);

        } catch (Throwable ignored) {
        }
    }

    private void safeNullifyMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        String fullClassName = clazz.getName();

        for (Method method : methods) {
            // スキップ条件を強化
            if (shouldSkipMethod(method)) {
                continue;
            }

            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // ビルダーパターンメソッドはスキップ
                        if (isBuilderMethod(method)) {
                            return;
                        }

                        // プリミティブ型はスキップ
                        if (method.getReturnType().isPrimitive()) {
                            return;
                        }

                        Object original = param.getResult();
                        param.setResult(null);

//                        XposedBridge.log("🔄 Nullified: " + fullClassName + "." + method.getName() +
//                                " (was: " + (original != null ? original.getClass().getSimpleName() : "null") + ")");
                    }
                });
            } catch (Throwable ignored) {
                XposedBridge.log("⚠️ Failed to hook " + fullClassName + "." + method.getName() +
                        ": ");
            }
        }
    }

    private boolean shouldSkipMethod(Method method) {
        int modifiers = method.getModifiers();
        return Modifier.isAbstract(modifiers) ||
                Modifier.isNative(modifiers) ||
                method.getReturnType().equals(void.class) ||
                method.getName().equals("withBeaconRetryCount") || // クラッシュ原因のメソッド
                method.getName().equals("followRedirects"); // 問題のメソッド
    }

    private boolean isBuilderMethod(Method method) {
        return method.getReturnType().getName().contains("Builder") ||
                method.getName().startsWith("with") ||
                method.getName().startsWith("set");
    }

    private void hookAllAdClasses(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String apkPath = lpparam.appInfo.sourceDir;
            if (apkPath == null) {
                XposedBridge.log("❌ APK path not found");
                return;
            }

            DexFile dexFile = new DexFile(apkPath);
            Enumeration<String> classNames = dexFile.entries();
            int hookedCount = 0;

            while (classNames.hasMoreElements()) {
                String className = classNames.nextElement();
                if (!className.startsWith("com.amazon.avod.ads") &&
                        !className.startsWith("com.amazon.device.ads")) {
                    continue;
                }

                try {
                    Class<?> clazz = Class.forName(className, false, lpparam.classLoader);
                    if (clazz != null) {
                        hookAllMethods(clazz);
                        hookedCount++;
                    }
                } catch (ClassNotFoundException ignored) {
                } catch (Throwable ignored) {
                }
            }

XposedBridge.log("✅ Successfully hooked " + hookedCount + " classes in com.amazon.avod.ads");

        } catch (IOException ignored) {
        } catch (Throwable ignored) {
        }
    }

    private void hookAllMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        String fullClassName = clazz.getName(); // 完全修飾クラス名を取得

        for (Method method : methods) {
            if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
                continue;
            }

            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String returnType = method.getReturnType().getSimpleName();
                        Object result = param.getResult();

                        // フルクラス名を使用してログ出力
                        String logMsg = String.format(
                                "▣ %s.%s() → %s: %s",
                                fullClassName, // 完全修飾クラス名
                                method.getName(),
                                returnType,
                                (result != null) ? result.toString() : "null"
                        );

                        if (logMsg.length() > 500) {
                            logMsg = logMsg.substring(0, 500) + "...[truncated]";
                        }

                        XposedBridge.log(logMsg);

                        if (param.hasThrowable()) {
                            XposedBridge.log("  ⚠ Exception: " +
                                    param.getThrowable().getClass().getName() + // 例外も完全修飾名で出力
                                    ": " + param.getThrowable().getMessage());
                        }
                    }
                });
            } catch (Throwable ignored) {
//                XposedBridge.log("⚠ Failed to hook " + fullClassName + "." + method.getName() +
//                        ": " );
            }
        }
    }


    private void hookAllMethodsA(Class<?> clazz) {
        // クラス内のすべてのメソッドを取得
        Method[] methods = clazz.getDeclaredMethods();

        for (Method method : methods) {
            // 抽象メソッドをスキップ
            if (Modifier.isAbstract(method.getModifiers())) {
                continue;
            }

            // 対象メソッドが特定のビュー関連メソッドであるか確認
            if (

                    !"invokeSuspend".equals(method.getName()) &&
                            !"run".equals(method.getName()) &&
                            !"setOnTouchListener".equals(method.getName()) &&
                            !"setVisibility".equals(method.getName()) &&
                            !"setAlpha".equals(method.getName()) &&
                            !"setEnabled".equals(method.getName()) &&
                            !"onCreate".equals(method.getName()) &&
                            !"setFocusable".equals(method.getName()) &&
                            !"setOnClickListener".equals(method.getName()) &&
                            !"setBackgroundColor".equals(method.getName()) &&
                            !"setPadding".equals(method.getName()) &&
                            !"setLayoutParams".equals(method.getName()) &&
                            !"requestLayout".equals(method.getName()) &&
                            !"invalidate".equals(method.getName()) &&
                            !"setText".equals(method.getName()) &&  // 新しく追加されたメソッド
                            !"setTextColor".equals(method.getName()) &&  // 新しく追加されたメソッド
                            !"setHint".equals(method.getName()) &&  // 新しく追加されたメソッド
                            !"setHintTextColor".equals(method.getName()) &&  // 新しく追加されたメソッド
                            !"onStart".equals(method.getName()) &&
                            !"onViewCreated".equals(method.getName()) &&
//                    !"setCompoundDrawables".equals(method.getName()) &&
                            !"getActivity".equals(method.getName()) &&  // PendingIntent method
                            !"onViewAdded".equals(method.getName()) && // PendingIntent method
                            !"setState".equals(method.getName())) {   // PendingIntent method
                continue;
            }

            method.setAccessible(true); // アクセス可能に設定

            try {
                // メソッドをフックする
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        StringBuilder argsString = new StringBuilder("Args: ");

                        // 引数が複数の場合、すべてを追加
                        for (int i = 0; i < param.args.length; i++) {
                            Object arg = param.args[i];
                            argsString.append("Arg[").append(i).append("]: ")
                                    .append(arg != null ? arg.toString() : "null")
                                    .append(", ");
                        }

// メソッドに応じたログ出力
                        if ("invokeSuspend".equals(method.getName())) {
                            XposedBridge.log("Before calling invokeSuspend in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("run".equals(method.getName())) {
                            XposedBridge.log("Before calling run in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("onCreate".equals(method.getName())) {
                            XposedBridge.log("Before calling onCreate in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setAlpha".equals(method.getName())) {
                            XposedBridge.log("Before calling setAlpha in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setEnabled".equals(method.getName())) {
                            XposedBridge.log("Before calling setEnabled in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setFocusable".equals(method.getName())) {
                            XposedBridge.log("Before calling setFocusable in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setOnClickListener".equals(method.getName())) {
                            XposedBridge.log("Before calling setOnClickListener in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setBackgroundColor".equals(method.getName())) {
                            XposedBridge.log("Before calling setBackgroundColor in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setPadding".equals(method.getName())) {
                            XposedBridge.log("Before calling setPadding in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setLayoutParams".equals(method.getName())) {
                            XposedBridge.log("Before calling setLayoutParams in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("requestLayout".equals(method.getName())) {
                            XposedBridge.log("Before calling requestLayout in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("invalidate".equals(method.getName())) {
                            XposedBridge.log("Before calling invalidate in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setText".equals(method.getName())) {
                            XposedBridge.log("Before calling setText in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setTextColor".equals(method.getName())) {
                            XposedBridge.log("Before calling setTextColor in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setHint".equals(method.getName())) {
                            XposedBridge.log("Before calling setHint in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setHintTextColor".equals(method.getName())) {
                            XposedBridge.log("Before calling setHintTextColor in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("setCompoundDrawables".equals(method.getName())) {
                            XposedBridge.log("Before calling setCompoundDrawables in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("onStart".equals(method.getName())) {
                            XposedBridge.log("Before calling onStart in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("getActivity".equals(method.getName())) {
                            XposedBridge.log("Before calling getActivity in class: " + clazz.getName() + " with args: " + argsString);
                        } else if ("onViewAdded".equals(method.getName())) {
                            // スタックトレースの取得
                            StringBuilder stackTrace = new StringBuilder("\nStack Trace:\n");
                            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                                stackTrace.append("  at ")
                                        .append(element.getClassName())
                                        .append(".")
                                        .append(element.getMethodName())
                                        .append("(")
                                        .append(element.getFileName())
                                        .append(":")
                                        .append(element.getLineNumber())
                                        .append(")\n");
                            }

                            // ログ出力（引数 + スタックトレース）
                            XposedBridge.log("Before calling onViewAdded in class: "
                                    + clazz.getName()
                                    + " with args: " + argsString
                                    + stackTrace.toString());

//                        } else if ("getService".equals(method.getName())) {
//                            XposedBridge.log("Before calling getService in class: " + clazz.getName() + " with args: " + argsString);
//                        } else if ("setState".equals(method.getName())) {
//                            XposedBridge.log("Before setState invoke in class: " + clazz.getName() + " with args: " + argsString);
//                        }
                        }
                    }
//
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        Object result = param.getResult();
//                        if ("invokeSuspend".equals(method.getName())) {
//                            XposedBridge.log("after calling invokeSuspend in class: " + clazz.getName() + (result != null ? result.toString() : "null"));
//                        } else if ("run".equals(method.getName())) {
//                            XposedBridge.log("After calling run in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setAlpha".equals(method.getName())) {
//                            XposedBridge.log("After calling setAlpha in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setEnabled".equals(method.getName())) {
//                            XposedBridge.log("After calling setEnabled in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("onCreate".equals(method.getName())) {
//                            XposedBridge.log("After calling onCreate in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("onViewCreated".equals(method.getName())) {
//                            XposedBridge.log("After calling onViewCreated in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//
//                        } else if ("setFocusable".equals(method.getName())) {
//                            XposedBridge.log("After calling setFocusable in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setOnClickListener".equals(method.getName())) {
//                            XposedBridge.log("After calling setOnClickListener in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setBackgroundColor".equals(method.getName())) {
//                            XposedBridge.log("After calling setBackgroundColor in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setPadding".equals(method.getName())) {
//                            XposedBridge.log("After calling setPadding in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setLayoutParams".equals(method.getName())) {
//                            XposedBridge.log("After calling setLayoutParams in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("requestLayout".equals(method.getName())) {
//                            XposedBridge.log("After calling requestLayout in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("invalidate".equals(method.getName())) {
//                            XposedBridge.log("After calling invalidate in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setText".equals(method.getName())) {
//                            XposedBridge.log("After calling setText in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setTextColor".equals(method.getName())) {
//                            XposedBridge.log("After calling setTextColor in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setHint".equals(method.getName())) {
//                            XposedBridge.log("After calling setHint in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setHintTextColor".equals(method.getName())) {
//                            XposedBridge.log("After calling setHintTextColor in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setCompoundDrawables".equals(method.getName())) {
//                            XposedBridge.log("After calling setCompoundDrawables in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("onStart".equals(method.getName())) {
//                            XposedBridge.log("Before calling onStart in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("getActivity".equals(method.getName())) {
//                            XposedBridge.log("After calling getActivity in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("onViewAdded".equals(method.getName())) {
//                            XposedBridge.log("After calling onViewAdded in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("getService".equals(method.getName())) {
//                            XposedBridge.log("After calling getService in class: " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        } else if ("setState".equals(method.getName())) {
//                            XposedBridge.log("setState " + clazz.getName() + " with result: " + (result != null ? result.toString() : "null"));
//                        }
//                    }
//
      });
            } catch (IllegalArgumentException e) {
                XposedBridge.log("Error hooking method " + method.getName() + " in class " + clazz.getName() + " : " + e.getMessage());
            } catch (Throwable e) {
                XposedBridge.log("Unexpected error hooking method " + method.getName() + " in class " + clazz.getName() + " : " + Log.getStackTraceString(e));
            }
        }
    }
}
    /*

    private boolean isViewCreationMethod(Method method) {
        // View作成に関連するメソッドを検出
        String methodName = method.getName().toLowerCase();

        return methodName.contains("inflate") || methodName.contains("new") || methodName.contains("create");
    }
 */