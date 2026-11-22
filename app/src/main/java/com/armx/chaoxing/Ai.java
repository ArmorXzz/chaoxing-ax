package com.armx.chaoxing;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * DeepSeek 答题客户端。只做一件事：把「题干 + 选项」发给模型，要它回一段 JSON。
 * 不读 cookie、不带学习通信息，走独立连接。
 */
final class Ai {

    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";

    private Ai() {
    }

    static boolean ready(Context ctx) {
        Store store = new Store(ctx);
        return store.aiOn() && !store.aiKey().isEmpty();
    }

    /** 返回模型回复的正文；失败抛异常，调用方负责降级。 */
    static String chat(String key, String model, String system, String user, int timeoutSec) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model == null || model.isEmpty() ? "deepseek-chat" : model);
        body.put("temperature", 0);
        body.put("max_tokens", 1200);
        body.put("response_format", new JSONObject().put("type", "json_object"));

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", user));
        body.put("messages", messages);

        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(Math.max(10, timeoutSec) * 1000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);

            byte[] raw = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(raw.length);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(raw);
            } finally {
                out.close();
            }

            int code = conn.getResponseCode();
            String text = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code != 200) {
                throw new IllegalStateException("DeepSeek HTTP " + code + " " + shorten(text, 120));
            }
            JSONObject obj = new JSONObject(text);
            JSONArray choices = obj.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new IllegalStateException("DeepSeek 没有返回 choices");
            JSONObject first = choices.optJSONObject(0);
            JSONObject msg = first == null ? null : first.optJSONObject("message");
            String content = msg == null ? "" : msg.optString("content", "");
            if (content.isEmpty()) throw new IllegalStateException("DeepSeek 返回了空内容");
            return content;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    static String shorten(String s, int n) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').trim();
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }
}
