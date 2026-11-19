package com.armx.chaoxing;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * 超星「cxSecretStyle」字体反爬解码。
 *
 * 原理：题目页面里嵌了一段 base64 字体，字形轮廓是真实汉字的形状，但字体的
 * post 表把这些字形命名成 uniXXXX（XXXX 是页面上那个乱码字符的码位）。
 * 于是：页面字符 → uniXXXX → 该字形轮廓的 md5 → 反查静态表得到真实汉字。
 *
 * 静态表 assets/font_map.txt 是从 fontTools 版本导出的「轮廓哈希 → 码位」。
 */
final class CxFont {

    private static final String FONT_PREFIX = "data:application/font-ttf;charset=utf-8;base64,";

    private static Map<String, String> table;   // hash -> 真实字符
    private static boolean tableBroken;

    private CxFont() {
    }

    static boolean encrypted(String html) {
        return html != null && html.contains("cxSecretStyle");
    }

    /** 解码整段文本；没有加密或解码失败时原样返回。 */
    static String decode(Context ctx, String html, String text) {
        if (text == null || text.isEmpty()) return text;
        if (!encrypted(html)) return Radicals.apply(text);
        ensureTable(ctx);
        Map<String, String> page = pageMap(html);
        if (page.isEmpty() || table == null) return Radicals.apply(text);

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String hash = page.get("uni" + Integer.toHexString(ch).toUpperCase(java.util.Locale.ROOT));
            String real = hash == null ? null : table.get(hash);
            sb.append(real != null ? real : String.valueOf(ch));
        }
        return Radicals.apply(sb.toString());
    }

    // ---------------------------------------------------------------- 静态表

    private static synchronized void ensureTable(Context ctx) {
        if (table != null || tableBroken) return;
        try {
            Map<String, String> m = new HashMap<>(40000);
            InputStream in = ctx.getAssets().open("font_map.txt");
            byte[] buf = new byte[1 << 16];
            StringBuilder line = new StringBuilder(64);
            int n;
            while ((n = in.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    char c = (char) (buf[i] & 0xff);
                    if (c == '\n') {
                        putLine(m, line);
                        line.setLength(0);
                    } else if (c != '\r') {
                        line.append(c);
                    }
                }
            }
            putLine(m, line);
            in.close();
            table = m;
        } catch (Throwable t) {
            tableBroken = true;
            Crash.log(t);
        }
    }

    private static void putLine(Map<String, String> m, StringBuilder line) {
        int tab = line.indexOf("\t");
        if (tab <= 0) return;
        try {
            String hash = line.substring(0, tab);
            int cp = Integer.parseInt(line.substring(tab + 1));
            m.put(hash, new String(Character.toChars(cp)));
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- 页面字体

    private static Map<String, String> pageMap(String html) {
        if (html == null) return new HashMap<>();
        int i = html.indexOf(FONT_PREFIX);
        if (i < 0) i = html.indexOf("base64,");
        if (i < 0) return new HashMap<>();
        int start = html.indexOf("base64,", i) + 7;
        int end = html.indexOf('\'', start);
        if (end < 0) end = html.indexOf('"', start);
        if (end < 0) return new HashMap<>();
        try {
            String b64 = html.substring(start, end).replaceAll("\\s", "");
            byte[] font = Base64.decode(b64, Base64.DEFAULT);
            return glyphHashes(font);
        } catch (Throwable t) {
            return new HashMap<>();
        }
    }

    /** 字体字节 → { glyph 名 → 轮廓 md5 }，只保留 uniXXXX 名字。 */
    private static Map<String, String> glyphHashes(byte[] font) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, byte[]> tables = readTables(font);
        byte[] post = tables.get("post");
        byte[] glyf = tables.get("glyf");
        byte[] loca = tables.get("loca");
        byte[] head = tables.get("head");
        byte[] maxp = tables.get("maxp");
        if (post == null || glyf == null || loca == null || head == null || maxp == null) return out;

        int numGlyphs = u16(maxp, 4);
        int longLoca = i16(head, 50);
        String[] names = postNames(post, numGlyphs);
        if (names == null) return out;

        int[] starts = new int[numGlyphs + 1];
        for (int g = 0; g <= numGlyphs; g++) {
            starts[g] = longLoca != 0 ? u32(loca, g * 4) : (u16(loca, g * 2) * 2);
        }
        for (int g = 0; g < numGlyphs; g++) {
            String name = names[g];
            if (name == null || !name.startsWith("uni")) continue;
            try {
                String hash = hashGlyph(glyf, starts[g], starts[g + 1]);
                if (!hash.isEmpty()) out.put(name, hash);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static String[] postNames(byte[] post, int numGlyphs) {
        if (post.length < 34 || u32(post, 0) != 0x00020000) return null;
        int n = Math.min(u16(post, 32), numGlyphs);
        int[] idx = new int[n];
        int p = 34;
        for (int i = 0; i < n; i++) {
            idx[i] = u16(post, p);
            p += 2;
        }
        List<String> custom = new ArrayList<>();
        while (p < post.length) {
            int len = post[p] & 0xff;
            p++;
            if (len == 0 || p + len > post.length) break;
            custom.add(new String(post, p, len, StandardCharsets.ISO_8859_1));
            p += len;
        }
        String[] names = new String[numGlyphs];
        for (int i = 0; i < n; i++) {
            int ix = idx[i];
            if (ix >= 258 && ix - 258 < custom.size()) names[i] = custom.get(ix - 258);
        }
        return names;
    }

    /** 按 fontTools 的算法：所有轮廓点 "x+y+(onCurve&1)" 拼接后取 md5。 */
    private static String hashGlyph(byte[] glyf, int start, int end) throws Exception {
        if (end <= start || start < 0 || end > glyf.length) return "";
        int nContours = i16(glyf, start);
        if (nContours <= 0) return "";
        int p = start + 10;
        int[] ends = new int[nContours];
        for (int i = 0; i < nContours; i++) {
            ends[i] = u16(glyf, p);
            p += 2;
        }
        int nPts = ends[nContours - 1] + 1;
        if (nPts <= 0 || nPts > 20000) return "";
        int insLen = u16(glyf, p);
        p += 2 + insLen;

        int[] flags = new int[nPts];
        int i = 0;
        while (i < nPts) {
            int f = glyf[p++] & 0xff;
            flags[i++] = f;
            if ((f & 0x08) != 0) {
                int rep = glyf[p++] & 0xff;
                while (rep-- > 0 && i < nPts) flags[i++] = f;
            }
        }
        int[] xs = new int[nPts];
        int[] ys = new int[nPts];
        int v = 0;
        for (i = 0; i < nPts; i++) {
            int f = flags[i];
            if ((f & 0x02) != 0) {
                int d = glyf[p++] & 0xff;
                v += (f & 0x10) != 0 ? d : -d;
            } else if ((f & 0x10) == 0) {
                v += i16(glyf, p);
                p += 2;
            }
            xs[i] = v;
        }
        v = 0;
        for (i = 0; i < nPts; i++) {
            int f = flags[i];
            if ((f & 0x04) != 0) {
                int d = glyf[p++] & 0xff;
                v += (f & 0x20) != 0 ? d : -d;
            } else if ((f & 0x20) == 0) {
                v += i16(glyf, p);
                p += 2;
            }
            ys[i] = v;
        }
        StringBuilder sb = new StringBuilder(nPts * 10);
        for (i = 0; i < nPts; i++) {
            sb.append(xs[i]).append(ys[i]).append(flags[i] & 1);
        }
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] d = md.digest(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        StringBuilder hex = new StringBuilder(32);
        for (byte b : d) hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        return hex.toString();
    }

    // ---------------------------------------------------------------- sfnt / woff

    private static Map<String, byte[]> readTables(byte[] f) {
        Map<String, byte[]> out = new HashMap<>();
        try {
            if (f.length > 12 && f[0] == 'w' && f[1] == 'O' && f[2] == 'F' && f[3] == 'F') {
                int num = u16(f, 12);
                for (int i = 0; i < num; i++) {
                    int p = 44 + i * 20;
                    String tag = new String(f, p, 4, StandardCharsets.ISO_8859_1);
                    int off = u32(f, p + 4);
                    int comp = u32(f, p + 8);
                    int orig = u32(f, p + 12);
                    byte[] data = new byte[comp];
                    System.arraycopy(f, off, data, 0, Math.min(comp, f.length - off));
                    if (comp < orig) data = inflate(data, orig);
                    if (data != null) out.put(tag, data);
                }
            } else {
                int num = u16(f, 4);
                for (int i = 0; i < num; i++) {
                    int p = 12 + i * 16;
                    String tag = new String(f, p, 4, StandardCharsets.ISO_8859_1);
                    int off = u32(f, p + 8);
                    int len = u32(f, p + 12);
                    if (off < 0 || len < 0 || off + len > f.length) continue;
                    byte[] data = new byte[len];
                    System.arraycopy(f, off, data, 0, len);
                    out.put(tag, data);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static byte[] inflate(byte[] src, int size) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(src);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(size);
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break;
                }
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            inf.end();
        }
    }

    private static int u16(byte[] b, int p) {
        return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
    }

    private static int i16(byte[] b, int p) {
        return (short) ((b[p] & 0xff) << 8 | (b[p + 1] & 0xff));
    }

    private static int u32(byte[] b, int p) {
        return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16) | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
    }

    static void warm(Context ctx) {
        ensureTable(ctx);
    }

    static int size() {
        return table == null ? 0 : table.size();
    }

    static boolean ready() {
        return table != null;
    }

    static String debug() throws IOException {
        return "table=" + size() + " broken=" + tableBroken;
    }
}
