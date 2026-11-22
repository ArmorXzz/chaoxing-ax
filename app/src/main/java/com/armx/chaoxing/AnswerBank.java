package com.armx.chaoxing;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 本地答案库：把用户导入的「题目 → 答案」对上号。
 *
 * 存储格式（filesDir/answers.txt）每行一条，制表符分隔：题干 \t 答案。
 * 导入时自动识别 JSON / 制表符 / 两行一题 三种常见格式。
 */
final class AnswerBank {

    private static final String FILE = "answers.txt";
    private static AnswerBank cached;

    private final List<String[]> list = new ArrayList<>();
    private final Map<String, String> exact = new HashMap<>();

    private AnswerBank() {
    }

    static synchronized AnswerBank get(Context ctx) {
        if (cached == null) {
            AnswerBank b = new AnswerBank();
            b.load(localFile(ctx));
            if (b.list.isEmpty()) b.loadAsset(ctx);
            cached = b;
        }
        return cached;
    }

    static synchronized void reset() {
        cached = null;
    }

    static File localFile(Context ctx) {
        return new File(ctx.getFilesDir(), FILE);
    }

    int size() {
        return list.size();
    }

    /** 题干 → 答案；找不到返回 null。 */
    String find(String question) {
        if (question == null) return null;
        String key = norm(question);
        if (key.length() < 2) return null;
        String hit = exact.get(key);
        if (hit != null) return hit;

        String best = null;
        int bestLen = 0;
        for (String[] e : list) {
            String q = e[0];
            if (q.length() < 4) continue;
            if (key.contains(q) || q.contains(key)) {
                if (q.length() > bestLen) {
                    bestLen = q.length();
                    best = e[1];
                }
            }
        }
        if (best != null) return best;

        // 5) 相似度兜底：题干常被平台改标点/漏字，按二元组 Dice 系数取唯一最优
        double top = 0;
        String topAnswer = null;
        int ties = 0;
        for (String[] e : list) {
            String q = e[0];
            if (q.length() < 8 || Math.abs(q.length() - key.length()) > key.length() / 2 + 2) continue;
            double sc = dice(key, q);
            if (sc > top + 1e-6) {
                top = sc;
                topAnswer = e[1];
                ties = 1;
            } else if (Math.abs(sc - top) <= 1e-6 && topAnswer != null) {
                ties++;
            }
        }
        if (topAnswer != null && top >= 0.82 && ties == 1) return topAnswer;
        return null;
    }

    // ---------------------------------------------------------------- 载入

    private void load(File f) {
        if (f == null || !f.exists()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf);
            if (n > 0) addAll(new String(buf, 0, n, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Crash.log(t);
        }
    }

    private void loadAsset(Context ctx) {
        loadAssetName(ctx, "answers.txt");
    }

    private void loadAssetName(Context ctx, String name) {
        try (java.io.InputStream in = ctx.getAssets().open(name)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bos.write(b, 0, n);
            addAll(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    /** 内置答案库：只随包发布，只对「国家安全教育」生效，不读用户文件、不提供导入入口。 */
    private static final String NATION_ASSET = "answers_nation.txt";
    private static AnswerBank nationBank;
    private static boolean nationLoaded;

    static synchronized AnswerBank nation(Context ctx) {
        if (!nationLoaded) {
            nationLoaded = true;
            AnswerBank b = new AnswerBank();
            b.loadAssetName(ctx, NATION_ASSET);
            nationBank = b;
        }
        return nationBank;
    }

    /** 解析文本并加入（不影响已存文件）。 */
    private void addAll(String text) {
        if (text == null) return;
        String t = text.trim();
        if (t.isEmpty()) return;
        if (t.startsWith("[") || t.startsWith("{")) {
            if (parseJson(t)) return;
        }
        parseLines(t);
    }

    private boolean parseJson(String t) {
        try {
            if (t.startsWith("{")) {
                JSONObject o = new JSONObject(t);
                boolean any = false;
                JSONArray names = o.names();
                if (names == null) return false;
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    String v = o.optString(k, "");
                    if (!k.isEmpty() && !v.isEmpty()) {
                        put(k, v);
                        any = true;
                    }
                }
                return any;
            }
            JSONArray arr = new JSONArray(t);
            boolean any = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String q = first(o, "q", "question", "title", "题干", "题目");
                String a = first(o, "a", "answer", "answers", "答案");
                if (!q.isEmpty() && !a.isEmpty()) {
                    put(q, a);
                    any = true;
                }
            }
            return any;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private final java.util.LinkedHashMap<String, java.util.TreeMap<Integer, String>> chapters =
            new java.util.LinkedHashMap<>();

    private void parseLines(String t) {
        String[] lines = t.split("\r?\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t");
            if (parts.length == 3) {
                try {
                    int idx = Integer.parseInt(parts[1].trim());
                    java.util.TreeMap<Integer, String> m = chapters.get(parts[0].trim());
                    if (m == null) {
                        m = new java.util.TreeMap<>();
                        chapters.put(parts[0].trim(), m);
                    }
                    m.put(idx, parts[2].trim());
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        List<String> buf = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                flushPair(buf);
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab > 0) {
                flushPair(buf);
                String q = line.substring(0, tab).trim();
                String a = line.substring(tab + 1).trim();
                if (!q.isEmpty() && !a.isEmpty()) put(q, a);
                continue;
            }
            String[] sep = splitSeparators(line);
            if (sep != null) {
                flushPair(buf);
                put(sep[0], sep[1]);
                continue;
            }
            buf.add(line);
        }
        flushPair(buf);
    }

    /** 「1. 题干 | A. 选项」这类里找明显的分隔符，命中才返回。 */
    private static String[] splitSeparators(String line) {
        String[] marks = {"：", "::", " || ", " —— ", " -- "};
        for (String m : marks) {
            int i = line.indexOf(m);
            if (i > 3 && i < line.length() - 1) {
                String q = line.substring(0, i).trim();
                String a = line.substring(i + m.length()).trim();
                if (!a.isEmpty() && a.length() <= 60) return new String[]{q, a};
            }
        }
        return null;
    }

    private void flushPair(List<String> buf) {
        for (int i = 0; i + 1 < buf.size(); i += 2) {
            put(buf.get(i), buf.get(i + 1));
        }
        buf.clear();
    }

    private void put(String q, String a) {
        String nq = norm(q);
        if (nq.length() < 2 || a == null || a.trim().isEmpty()) return;
        String na = a.trim();
        if (exact.containsKey(nq)) return;
        exact.put(nq, na);
        list.add(new String[]{nq, na});
    }

    // ---------------------------------------------------------------- 导入 / 清空

    static synchronized int importText(Context ctx, String text) {
        AnswerBank probe = new AnswerBank();
        probe.addAll(text);
        if (probe.list.isEmpty()) return -1;
        try (FileOutputStream out = new FileOutputStream(localFile(ctx), false)) {
            out.write(probe.dump().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Crash.log(t);
            return -1;
        }
        cached = probe;
        return probe.list.size();
    }

    private String dump() {
        StringBuilder sb = new StringBuilder(list.size() * 60);
        for (String[] e : list) sb.append(e[0]).append('\t').append(e[1]).append('\n');
        return sb.toString();
    }

    static synchronized void clear(Context ctx) {
        File f = localFile(ctx);
        if (f.exists()) f.delete();
        cached = new AnswerBank();
    }

    // ---------------------------------------------------------------- 归一化

    /** 去掉空白与标点、转小写，仅用于比对。 */
    static String norm(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) continue;
            if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + 32));
                continue;
            }
            if (c < 0x2E80) {
                // ASCII 标点直接丢弃
                if (!Character.isLetterOrDigit(c)) continue;
                sb.append(c);
                continue;
            }
            if (c >= 0xFF01 && c <= 0xFF19) {           // 全角符号/数字
                if (c >= 0xFF10 && c <= 0xFF19) sb.append((char) (c - 0xFF10 + '0'));
                continue;
            }
            if (c >= 0x3000 && c <= 0x303F) continue;   // CJK 标点
            if (c >= 0xFF00 && c <= 0xFFEF) continue;   // 全角标点
            sb.append(c);
        }
        return sb.toString().toLowerCase(Locale.ROOT).trim();
    }

    /** 按章节取顺序答案（题目序号 1..n）。章节名用「1.1」这样的编号做前缀匹配。 */
    public java.util.List<String> chapter(String title) {
        if (title == null || chapters.isEmpty()) return java.util.Collections.emptyList();
        String t = norm(title);
        if (t.isEmpty()) return java.util.Collections.emptyList();

        // 1) 完全相等
        for (java.util.Map.Entry<String, java.util.TreeMap<Integer, String>> e : chapters.entrySet()) {
            if (norm(e.getKey()).equals(t)) return new java.util.ArrayList<>(e.getValue().values());
        }
        // 2) 去掉「第X章/第X节」后再比
        String bare = stripChapterNo(t);
        for (java.util.Map.Entry<String, java.util.TreeMap<Integer, String>> e : chapters.entrySet()) {
            if (stripChapterNo(norm(e.getKey())).equals(bare)) return new java.util.ArrayList<>(e.getValue().values());
        }
        // 3) 包含匹配，取最长（最具体）的那一章；长度不足 3 的短名不参与，避免「第1章」误配
        String bestKey = null;
        for (String k : chapters.keySet()) {
            String nk = norm(k);
            if (nk.length() < 3) continue;
            if (t.contains(nk) || nk.contains(bare) && bare.length() >= 3) {
                if (bestKey == null || nk.length() > norm(bestKey).length()) bestKey = k;
            }
        }
        if (bestKey != null) return new java.util.ArrayList<>(chapters.get(bestKey).values());

        // 4) 宽松匹配：忽略「的/与/和/及」这类连接词，页面标题和题库章节名经常差一两个字
        String loose = loose(bare);
        if (loose.length() >= 4) {
            String bestLoose = null;
            int bestScore = 0;
            for (String k : chapters.keySet()) {
                String lk = loose(stripChapterNo(norm(k)));
                if (lk.length() < 4) continue;
                if (lk.equals(loose)) return new java.util.ArrayList<>(chapters.get(k).values());
                int score = 0;
                if (lk.contains(loose) || loose.contains(lk)) score = 90 + Math.min(lk.length(), loose.length());
                else score = (int) (sim(lk, loose) * 100);
                if (score >= 72 && score > bestScore) {
                    bestScore = score;
                    bestLoose = k;
                }
            }
            if (bestLoose != null) return new java.util.ArrayList<>(chapters.get(bestLoose).values());
        }
        return java.util.Collections.emptyList();
    }

    /** 二元组 Dice 系数，用来做「近似题干」兜底匹配。 */
    private static double dice(String a, String b) {
        if (a.length() < 2 || b.length() < 2) return 0;
        java.util.HashMap<String, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            String g = a.substring(i, i + 2);
            m.put(g, m.containsKey(g) ? m.get(g) + 1 : 1);
        }
        int hit = 0, total = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String g = b.substring(i, i + 2);
            Integer c = m.get(g);
            if (c != null && c > 0) {
                m.put(g, c - 1);
                hit++;
            }
            total++;
        }
        return total == 0 ? 0 : (2.0 * hit) / (a.length() - 1 + b.length() - 1);
    }

    /** 去掉连接词与标点，只留实词，用来做宽松比对。 */
    private static String loose(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("的与和及等之其了是、，。·—…（）()【】[]:：;；'\"“”‘’ ".indexOf(c) >= 0) continue;
            sb.append(c);
        }
        return sb.toString().trim();
    }

    /** 两个字符串的相似度：公共字符数 / 较长者长度。 */
    private static double sim(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int[] cnt = new int[65536];
        for (int i = 0; i < a.length(); i++) cnt[a.charAt(i)]++;
        int common = 0;
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            if (cnt[c] > 0) {
                cnt[c]--;
                common++;
            }
        }
        return (double) common / Math.max(a.length(), b.length());
    }

    /** 去掉开头的「第1章」「1.」「一、」等编号，只留实质标题。 */
    private static String stripChapterNo(String s) {
        String out = s;
        for (int i = 0; i < 12; i++) {
            if (out.isEmpty()) break;
            char c = out.charAt(0);
            if (Character.isDigit(c) || "零一二三四五六七八九十百.．、,，:：-—_ ()（）【】[]第章节单元课".indexOf(c) >= 0) {
                out = out.substring(1);
            } else break;
        }
        return out.trim();
    }

    /** 内置章节答案的条数（只有按章节格式导入时才有值）。 */
    public int chapterTotal() {
        int n = 0;
        for (java.util.TreeMap<Integer, String> m : chapters.values()) n += m.size();
        return n;
    }
}
