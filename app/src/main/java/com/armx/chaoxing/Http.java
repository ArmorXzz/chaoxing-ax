package com.armx.chaoxing;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** 极简 HttpURLConnection 会话：自动维护 cookie、gzip、UTF-8。 */
final class Http {

    static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    final Map<String, String> cookies = new java.util.concurrent.ConcurrentHashMap<>();

    static final class Resp {
        final int code;
        final String body;

        Resp(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }

        boolean ok() {
            return code == 200;
        }

        JSONObject json() {
            try {
                return new JSONObject(body);
            } catch (Exception e) {
                return null;
            }
        }
    }

    String cookie(String name) {
        String v = cookies.get(name);
        return v == null ? "" : v;
    }

    /** 会话持久化：下次冷启动直接复用，不用重新登录。 */
    synchronized String dumpCookies() {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            try {
                o.put(e.getKey(), e.getValue());
            } catch (Exception ignored) {
            }
        }
        return o.toString();
    }

    synchronized void loadCookies(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject o = new JSONObject(json);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                cookies.put(k, o.optString(k, ""));
            }
        } catch (Exception ignored) {
        }
    }

    synchronized void clearCookies() {
        cookies.clear();
    }

    synchronized String cookieString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    Resp get(String url, Map<String, String> params, Map<String, String> extraHeaders) {
        StringBuilder u = new StringBuilder(url);
        if (params != null && !params.isEmpty()) {
            u.append(url.contains("?") ? '&' : '?');
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!first) u.append('&');
                first = false;
                u.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            }
        }
        return call(u.toString(), "GET", null, extraHeaders);
    }

    Resp post(String url, Map<String, String> form, Map<String, String> extraHeaders) {
        StringBuilder sb = new StringBuilder();
        if (form != null) {
            for (Map.Entry<String, String> e : form.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            }
        }
        return call(url, "POST", sb.toString(), extraHeaders);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private Resp call(String url, String method, String body, Map<String, String> extraHeaders) {
        return call(url, method, body, extraHeaders, 0);
    }

    private Resp call(String url, String method, String body, Map<String, String> extraHeaders, int depth) {
        if (depth > 4) return new Resp(0, "");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            String ck = cookieString();
            if (!ck.isEmpty()) conn.setRequestProperty("Cookie", ck);
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    if (e.getValue() != null) conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            if ("POST".equals(method)) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                byte[] payload = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }
            int code = conn.getResponseCode();
            absorbCookies(conn.getHeaderFields());

            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = "";
            if (in != null) {
                String enc = conn.getContentEncoding();
                if (enc != null && enc.toLowerCase(java.util.Locale.ROOT).contains("gzip")) {
                    in = new GZIPInputStream(in);
                }
                text = readAll(in);
            }
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    conn.disconnect();
                    return call(new URL(new URL(url), loc).toString(), "GET", null,
                            extraHeaders, depth + 1);
                }
            }
            return new Resp(code, text);
        } catch (Throwable t) {
            return new Resp(0, "");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void absorbCookies(Map<String, List<String>> headers) {
        if (headers == null) return;
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() == null || !"set-cookie".equalsIgnoreCase(e.getKey())) continue;
            for (String raw : e.getValue()) {
                if (raw == null) continue;
                int semi = raw.indexOf(';');
                String pair = semi >= 0 ? raw.substring(0, semi) : raw;
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String name = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                if (name.isEmpty()) continue;
                cookies.put(name, value);
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream is = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
