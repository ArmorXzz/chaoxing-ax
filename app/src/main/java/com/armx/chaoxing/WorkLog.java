package com.armx.chaoxing;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 答题 / 任务点的诊断日志。写到 getExternalFilesDir/cx-work.log，超过 96 KB 滚成 .old。
 * 只为排查「题目没填上」这类问题，正常使用不会去看。
 */
final class WorkLog {

    private static final long MAX = 96 * 1024L;

    private WorkLog() {
    }

    /** 每次启动 App 调用一次：把上次的日志留成 .prev，主日志清空重来。 */
    static void rotate(Context ctx, String header) {
        if (ctx == null) return;
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) return;
            File f = new File(dir, "cx-work.log");
            File prev = new File(dir, "cx-work.prev.log");
            if (prev.exists()) prev.delete();
            if (f.exists()) f.renameTo(prev);
            w(ctx, "===== " + header + " =====");
        } catch (Throwable ignored) {
        }
    }

    static void w(Context ctx, String msg) {
        if (ctx == null) return;
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) return;
            File f = new File(dir, "cx-work.log");
            if (f.length() > MAX) {
                File old = new File(dir, "cx-work.old.log");
                if (old.exists()) old.delete();
                f.renameTo(old);
            }
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + "  " + msg + "\n";
            FileOutputStream out = new FileOutputStream(f, true);
            try {
                out.write(line.getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
