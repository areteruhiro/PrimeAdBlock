package github.hiro.primevideo;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Enumeration;
import dalvik.system.DexFile;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Main implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {

        try {
            nullifyHttpAdClassesSafely(loadPackageParam);
            // hookAllClassesInPackageA(loadPackageParam.classLoader, loadPackageParam);

            try {
                Class<?> targetClass = XposedHelpers.findClassIfExists(
                        "com.amazon.avod.playbackclient.control.AdEnabledVideoClientPresentation$1",
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
                                    XposedBridge.log("[AdBlock] Ad clip immediately terminated");
                                }
                            }
                    );
                } else {
                    XposedBridge.log("[AdBlock] Target class not found, skipping hook");
                }
            } catch (Throwable t) {
                XposedBridge.log("[AdBlock] Error while hooking: " + t.getMessage());
            }

            try {
                Class<?> targetClass = XposedHelpers.findClassIfExists(
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
                    // 必要なければこの else ブロックは削除してもOK（完全にサイレントにしたい場合）
                    XposedBridge.log("[AdBlock] AdLifecycleListenerProxy not found, skipping hook");
                }
            } catch (Throwable t) {
            }


            try {
                Class<?> targetClass = XposedHelpers.findClassIfExists(
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
                                }
                            }
                    );
                }
            } catch (Throwable ignored) {
            }
            try {
                Class<?> targetClass = XposedHelpers.findClassIfExists(
                        "com.amazon.avod.ads.api.internal.AdInfoNode",
                        loadPackageParam.classLoader);

                if (targetClass != null) {
                    XposedHelpers.findAndHookMethod(
                            targetClass,
                            "getDisplayableAds",
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    param.setResult(Collections.emptyList());
                                }
                            }
                    );
                }
            } catch (Throwable ignored) {
            }
        } catch (Exception ignored) {
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
                        if (isBuilderMethod(method)) {
                            return;
                        }
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
}