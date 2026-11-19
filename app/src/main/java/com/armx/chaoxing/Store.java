package com.armx.chaoxing;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 本地持久化：账号（密码用 AES 加密后存）、会话 cookie 与运行参数。 */
final class Store {

    private static final String SP = "cx";
    private static final String K_ACCOUNTS = "accounts";
    private static final String K_LAST_USER = "last_user";
    private static final String K_SPEED = "speed";
    private static final String K_PARALLEL = "parallel";
    private static final String K_BG = "bg";
    private static final String K_WALL = "wall";
    private static final String K_AUTO_LOGIN = "auto_login";
    private static final String K_BLUR = "blur";
    private static final String K_GLASS = "glass";
    private static final String K_GLASS_MODE = "glass_mode";
    private static final String K_THEME = "theme";
    private static final String K_COOKIES = "cookies";
    private static final String K_BACK_ANIM = "back_anim";
    private static final String K_MEDIA_CARD = "media_card";
    private static final String K_CUSTOM_BG = "custom_bg";

    private final SharedPreferences sp;
    private final Context app;

    Store(Context ctx) {
        app = ctx.getApplicationContext();
        sp = app.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    Context context() {
        return app;
    }

    // ---------------- 账号 ----------------

    List<Model.Account> accounts() {
        List<Model.Account> out = new ArrayList<>();
        String raw = sp.getString(K_ACCOUNTS, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Model.Account a = new Model.Account();
                a.username = o.optString("username", "");
                a.password = o.optString("password", "");
                a.name = o.optString("name", "");
                if (!a.username.isEmpty()) out.add(a);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    void saveAccount(String username, String password, String name) {
        List<Model.Account> list = accounts();
        boolean found = false;
        for (Model.Account a : list) {
            if (a.username.equals(username)) {
                a.password = Crypt.encrypt(password);
                a.name = name;
                found = true;
                break;
            }
        }
        if (!found) {
            Model.Account a = new Model.Account();
            a.username = username;
            a.password = Crypt.encrypt(password);
            a.name = name;
            list.add(a);
        }
        writeAccounts(list);
        sp.edit().putString(K_LAST_USER, username).apply();
    }

    void removeAccount(String username) {
        List<Model.Account> list = accounts();
        List<Model.Account> keep = new ArrayList<>();
        for (Model.Account a : list) {
            if (!a.username.equals(username)) keep.add(a);
        }
        writeAccounts(keep);
    }

    private void writeAccounts(List<Model.Account> list) {
        JSONArray arr = new JSONArray();
        for (Model.Account a : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("username", a.username);
                o.put("password", a.password);
                o.put("name", a.name);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        sp.edit().putString(K_ACCOUNTS, arr.toString()).apply();
    }

    String lastUser() {
        return sp.getString(K_LAST_USER, "");
    }

    // ---------------- 会话 ----------------

    /** 上次的 cookie，避免每次冷启动都要重新登录。 */
    String cookies() {
        return sp.getString(K_COOKIES, "");
    }

    void saveCookies(String json) {
        sp.edit().putString(K_COOKIES, json == null ? "" : json).apply();
    }

    // ---------------- 运行参数 ----------------

    float speed() {
        // 2.0 是上限：再快服务端会当作异常上报，结算反而过不去
        return Math.min(2f, Math.max(1f, sp.getFloat(K_SPEED, 1.0f)));
    }

    int parallel() {
        return sp.getInt(K_PARALLEL, 0);
    }

    void saveRunning(float speed, int parallel) {
        sp.edit().putFloat(K_SPEED, Math.min(2f, Math.max(1f, speed))).putInt(K_PARALLEL, parallel).apply();
    }

    boolean bg() {
        return sp.getBoolean(K_BG, true);
    }

    int blur() {
        return sp.getInt(K_BLUR, 1);
    }

    /** 0 关闭 / 1 模糊玻璃 / 2 液态玻璃。 */
    int glassMode() {
        int legacy = sp.getInt(K_GLASS_MODE, -1);
        if (legacy >= 2) return 1;                 // 旧的液态玻璃已取消，统一按模糊玻璃处理
        if (legacy >= 0) return Math.min(2, legacy);
        return sp.getBoolean(K_GLASS, true) ? 1 : 0;
    }

    void saveGlassMode(int mode) {
        sp.edit().putInt(K_GLASS_MODE, mode > 0 ? 1 : 0).apply();
    }

    void saveUi(boolean bg, int blur) {
        sp.edit().putBoolean(K_BG, bg).putInt(K_BLUR, blur).apply();
    }

    /** 0 跟随系统、1 浅色、2 深色；默认深色（和网页版默认主题一致）。 */
    int theme() {
        return sp.getInt(K_THEME, 0);
    }

    void saveTheme(int v) {
        sp.edit().putInt(K_THEME, v).apply();
    }

    /** 预测式返回动画（Android 13+）。 */
    boolean backAnim() {
        return sp.getBoolean(K_BACK_ANIM, true);
    }

    void saveBackAnim(boolean on) {
        sp.edit().putBoolean(K_BACK_ANIM, on).apply();
    }

    /** 把刷课进度投到媒体卡片 / 流体云上。 */
    boolean mediaCard() {
        return sp.getBoolean(K_MEDIA_CARD, true);
    }

    void saveMediaCard(boolean on) {
        sp.edit().putBoolean(K_MEDIA_CARD, on).apply();
    }

    // ---------------- 自定义背景 ----------------

    boolean customBg() {
        return sp.getBoolean(K_CUSTOM_BG, false) && bgFile().isFile();
    }

    boolean autoLogin() {
        return sp.getBoolean(K_AUTO_LOGIN, true);
    }

    void saveAutoLogin(boolean on) {
        sp.edit().putBoolean(K_AUTO_LOGIN, on).apply();
    }

    /** 0 = 夏花（内置旧图），1 = 魔王（内置新图），2 = 自定义。 */
    int wall() {
        return sp.getInt(K_WALL, 1);
    }

    void saveWall(int index) {
        sp.edit().putInt(K_WALL, Math.max(0, Math.min(2, index))).apply();
    }

    void saveCustomBg(boolean on) {
        sp.edit().putBoolean(K_CUSTOM_BG, on).apply();
    }

    java.io.File bgFile() {
        return new java.io.File(app.getFilesDir(), "bg_custom.jpg");
    }
}
