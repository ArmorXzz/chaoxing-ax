package com.armx.chaoxing;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public class CxApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            android.util.Log.i("CXBOOT", "app: onCreate");
            java.io.File dir = getExternalFilesDir(null);
            if (dir != null) {
                java.io.File f = new java.io.File(dir, "boot.log");
                java.io.FileOutputStream out = new java.io.FileOutputStream(f, true);
                out.write((new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.ROOT)
                        .format(new java.util.Date()) + "  app: onCreate\n").getBytes());
                out.close();
            }
        } catch (Throwable ignored) {
        }
        Crash.install(this);
        // 主题要在 Activity 起来之前定下来，否则首帧会闪一下白/黑。
        try {
            MainActivity.applyNightMode(new Store(this).theme());
        } catch (Throwable ignored) {
        }
        Engine.get(this);
    }
}
