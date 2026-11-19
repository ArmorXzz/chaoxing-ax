package com.armx.chaoxing;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃兜底：把未捕获异常写到 filesDir/last_crash.txt。
 * 手机上看不到 logcat，用户截一张图就能定位问题。
 */
final class Crash {

    private static final String FILE = "last_crash.txt";
    private static Thread.UncaughtExceptionHandler previous;
    private static Context appCtx;

    private Crash() {
    }

    static void install(Context ctx) {
        final Context app = ctx.getApplicationContext();
        appCtx = app;
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                write(app, t, e);
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(t, e);
        });
    }

    /** 后台线程手动记一笔（不抛出）。 */
    static void log(Throwable e) {
        try {
            write(appCtx, Thread.currentThread(), e);
        } catch (Throwable ignored) {
        }
    }

    /** 手动记录一次异常（用于 onCreate 兜底，保证下次启动能看到原因）。 */
    static void log(Context ctx, Throwable e) {
        try {
            write(ctx.getApplicationContext(), Thread.currentThread(), e);
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void write(Context ctx, Thread t, Throwable e) {
        if (ctx == null || e == null) return;
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("线程: " + (t == null ? "?" : t.getName()));
        pw.println("设备: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        pw.println("Android: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")");
        pw.println("版本: " + Build.versionName(ctx));
        pw.println();
        e.printStackTrace(pw);
        pw.flush();

        byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);

        File f = new File(ctx.getFilesDir(), FILE);
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(bytes);
        } catch (Throwable ignored) {
        }

        // 同时往「用户能直接拿到」的外部目录追加一份，方便文件管理器里拷出来
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir != null) {
                File log = new File(dir, "boot.log");
                if (log.length() > 256 * 1024) log.delete();
                try (FileOutputStream out = new FileOutputStream(log, true)) {
                    out.write("\n===== CRASH =====\n".getBytes(StandardCharsets.UTF_8));
                    out.write(bytes);
                    out.write("\n".getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    static String read(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return "";
            byte[] buf = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int n = in.read(buf);
                if (n <= 0) return "";
                return new String(buf, 0, n, StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            return "";
        }
    }

    static void clear(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {
        }
    }

    static final class Build {
        /** 运行时读取 versionName，避免常量与构建配置不同步。 */
    static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }
    }
}
