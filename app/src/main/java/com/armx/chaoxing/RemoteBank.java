package com.armx.chaoxing;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 可选的远程题库（题库海开放接口）。默认关闭，Key 只存在本机。
 * 只补本地题库与 AI 都没覆盖到的题，绝不覆盖已有作答。
 */
final class RemoteBank {

    private static final String BASE = "https://search.tikuhai.com";
    private static final String PATH = "/api/open/v1/search/exact";

    private RemoteBank() {
    }

    static boolean on(Context ctx) {
        if (ctx == null) return false;
        Store st = new Store(ctx);
        return st.bankOn() && !st.bankKey().isEmpty();
    }

    /** 给还没有答案的题补答案，返回补齐的题数。 */
    static int fill(Context ctx, Work.Page page) {
        if (page == null || page.questions.isEmpty()) return 0;
        String key = new Store(ctx).bankKey();
        int hit = 0;
        for (Work.Q q : page.questions) {
            if (q.answer != null && !q.answer.isEmpty()) continue;
            try {
                String raw = search(key, q);
                if (raw == null || raw.isEmpty()) continue;
                String body = Work.toAnswer(q, raw);
                WorkLog.w(ctx, "  net type=" + q.typeCode + " raw=[" + shorten(raw) + "] -> [" + body + "]");
                if (body.isEmpty()) continue;
                q.answer = body;
                q.changed = !Work.same(body, q.saved);
                hit++;
            } catch (Throwable t) {
                WorkLog.w(ctx, "remote: " + t);
                break;
            }
        }
        return hit;
    }

    /** 连通性测试：返回一句给人看的结果。 */
    static String ping(String key) {
        if (key == null || key.trim().isEmpty()) return "还没有填题库 Key";
        try {
            JSONObject body = new JSONObject();
            body.put("stem", "连通性测试");
            body.put("options", new JSONArray());
            body.put("type", "0");
            JSONObject r = post(key.trim(), body);
            if (r.optBoolean("success")) return "题库可用";
            JSONObject err = r.optJSONObject("error");
            String msg = err == null ? "接口返回异常" : err.optString("message", "接口返回异常");
            return "题库不可用：" + msg;
        } catch (Throwable t) {
            return "题库不可用：" + t;
        }
    }

    private static String search(String key, Work.Q q) throws Exception {
        JSONObject body = new JSONObject();
        body.put("stem", Work.cleanStem(q.title));
        JSONArray opts = new JSONArray();
        if (q.options != null) {
            for (String o : q.options) opts.put(Work.cleanOption(o));
        }
        body.put("options", opts);
        body.put("type", q.typeCode == null || q.typeCode.isEmpty() ? "0" : q.typeCode);
        JSONObject r = post(key, body);
        if (!r.optBoolean("success")) {
            JSONObject err = r.optJSONObject("error");
            throw new IllegalStateException(err == null ? "题库返回失败" : err.optString("message", "题库返回失败"));
        }
        JSONObject data = r.optJSONObject("data");
        JSONArray items = data == null ? null : data.optJSONArray("items");
        if (items == null || items.length() == 0) return null;

        String stem = Work.cleanStem(q.title);
        JSONObject pick = null;
        if (items.length() == 1) {
            pick = items.optJSONObject(0);
        } else {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                if (AnswerBank.norm(Work.cleanStem(it.optString("stem"))).equals(AnswerBank.norm(stem))) {
                    if (pick == null) pick = it;
                    else return null;   // 同题多解，宁可不填
                }
            }
        }
        if (pick == null) return null;
        JSONObject ans = pick.optJSONObject("answer");
        if (ans == null) return null;
        String kind = ans.optString("kind", "");
        if ("none".equals(kind) || "missing".equals(kind)) return null;
        JSONArray values = ans.optJSONArray("values");
        if (values == null || values.length() == 0) return null;
        return toRaw(q.typeCode, values);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").trim();
        return t.length() <= 40 ? t : t.substring(0, 40) + "…";
    }

    private static String toRaw(String typeCode, JSONArray values) {
        String type = typeCode == null ? "0" : typeCode;
        if ("0".equals(type) || "1".equals(type)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length(); i++) {
                String v = values.optString(i, "").trim();
                if (v.isEmpty()) continue;
                char c = Character.toUpperCase(v.charAt(0));
                if (c >= 'A' && c <= 'H') {
                    String s = String.valueOf(c);
                    if (sb.indexOf(s) < 0) sb.append(s);
                } else if (i == 0) {
                    return v;   // 题库给的是选项原文，交给 firstLetter 去对
                }
            }
            return sb.toString();
        }
        if ("3".equals(type)) return values.optString(0, "").trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            String v = values.optString(i, "").trim();
            if (v.isEmpty()) continue;
            if (sb.length() > 0) sb.append('#');
            sb.append(v);
        }
        return sb.toString();
    }

    private static JSONObject post(String key, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + PATH).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Consumer-Token", key);
        c.setRequestProperty("User-Agent", "chaoxing-ax/" + BuildInfo.VERSION);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) {
            out.write(bytes);
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = readAll(in);
        if (text == null || text.isEmpty()) throw new IllegalStateException("HTTP " + code);
        return new JSONObject(text);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
