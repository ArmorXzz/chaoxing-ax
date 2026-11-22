package com.armx.chaoxing;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public class CxApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Loc.INSTANCE.set(new Store(this).lang());
        Crash.install(this);
        Crash.mark("boot " + BuildInfo.VERSION);
        try {
            android.util.Log.i("CXBOOT", "app: onCreate");
            java.io.File dir = getExternalFilesDir(null);
            if (dir != null) {
                // 每次启动重建 boot.log，旧的留成 boot.prev.log，避免把上次的旧崩溃混进来
                java.io.File f = new java.io.File(dir, "boot.log");
                java.io.File prev = new java.io.File(dir, "boot.prev.log");
                if (f.exists()) {
                    if (prev.exists()) prev.delete();
                    f.renameTo(prev);
                }
                java.io.FileOutputStream out = new java.io.FileOutputStream(f, true);
                out.write((new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.ROOT)
                        .format(new java.util.Date()) + "  app: onCreate\n").getBytes());
                out.close();
            }
        } catch (Throwable ignored) {
        }
        Crash.install(this);
        try {
            WorkLog.rotate(this, "超星AX " + BuildInfo.VERSION + " / 启动 " 
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
                    .format(new java.util.Date()));
        } catch (Throwable ignored) {
        }
        // 主题要在 Activity 起来之前定下来，否则首帧会闪一下白/黑。
        try {
            MainActivity.applyNightMode(new Store(this).theme());
        } catch (Throwable ignored) {
        }
        Engine.get(this);
    }
}
