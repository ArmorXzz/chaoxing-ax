package com.armx.chaoxing;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * 章节测验 / 任务点作业：解析题目、套本地答案库、组装保存请求。
 *
 * 只保存不提交（pyFlag=1）——提交会算成绩，交给我们不保证全对的机器不合适。
 */
final class Work {

    static final class Q {
        String id = "";
        String typeCode = "";
        String type = "unknown";
        String title = "";
        final List<String> options = new ArrayList<>();
        String answer = "";
        /** 页面上已经存过的答案（隐藏 input 里的值）。 */
        String saved = "";
        boolean matched;
        /** 本次计算出来的答案是否和已有作答不同。 */
        boolean changed;
    }

    static final class Page {
        final Map<String, String> form = new LinkedHashMap<>();
        final List<Q> questions = new ArrayList<>();
    }

    private Work() {
    }

    /** 解析答题页；页面里没有 form 时返回 null。 */
    static Page parse(Context ctx, String html) {
        if (html == null) return null;
        try {
            Document doc = Jsoup.parse(html);
            Element form = doc.selectFirst("form");

            Page page = new Page();
            Map<String, String> saved = new HashMap<>();
            Elements inputs = form != null ? form.select("input") : doc.select("input[name]");
            for (Element in : inputs) {
                String name = in.attr("name");
                if (name.isEmpty()) continue;
                if (name.startsWith("answer")) {
                    // 只记录，不参与提交表单：后面用来判断「已有作答是否已经一致」
                    if (name.matches("answer\\d+")) {
                        saved.put(name.substring(6), in.attr("value"));
                    }
                    continue;
                }
                if (page.form.containsKey(name)) continue;
                page.form.put(name, in.attr("value"));
            }

            Elements divs = new Elements();
            java.util.Set<Element> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (String sel : new String[]{"div.singleQuesId", "div[id^=question]", "div.questionLi", "div.TiMu"}) {
                for (Element e : doc.select(sel)) {
                    Element box = "div.TiMu".equals(sel) ? (e.parent() == null ? e : e.parent()) : e;
                    if (seen.add(box)) divs.add(box);
                }
            }
            WorkLog.w(ctx, "parse-scan: singleQuesId=" + doc.select("div.singleQuesId").size()
                    + " idQuestion=" + doc.select("div[id^=question]").size()
                    + " timu=" + doc.select("div.TiMu").size()
                    + " candidates=" + divs.size());
            for (Element div : divs) {
                Q q = new Q();
                q.id = div.attr("data");
                if (q.id.isEmpty()) q.id = div.selectFirst("div.TiMu") == null ? "" : div.selectFirst("div.TiMu").attr("data");
                Element timu = div.selectFirst("div.TiMu");
                q.typeCode = timu == null ? "" : timu.attr("data");
                q.type = typeOf(q.typeCode);
                Element titleEl = div.selectFirst("div.Zy_TItle");
                String rawTitle = titleEl == null ? "" : titleEl.text();
                q.title = CxFont.decode(ctx, html, rawTitle).trim();
                for (Element li : div.select("ul li")) {
                    String raw = li.text();
                    q.options.add(CxFont.decode(ctx, html, raw).trim());
                }
                WorkLog.w(ctx, "  item id=[" + q.id + "] class=[" + div.className() + "] type=[" + q.typeCode
                        + "/" + q.type + "] titleLen=" + q.title.length() + " opts=" + q.options.size()
                        + (q.id.isEmpty() || q.title.isEmpty() ? "  << 跳过" : ""));
                if (q.id.isEmpty() || q.title.isEmpty()) continue;
                q.saved = saved.containsKey(q.id) ? saved.get(q.id) : "";
                page.questions.add(q);
            }
            WorkLog.w(ctx, "parse: html=" + html.length() + " form=" + (form != null)
                    + " inputs=" + page.form.size() + " items=" + divs.size()
                    + " questions=" + page.questions.size());
            return page;
        } catch (Throwable t) {
            Crash.log(t);
            return null;
        }
    }

    /** 已有作答是否和这次算出来的一致（只比较实际内容，忽略顺序与格式差异）。 */
    static boolean same(String computed, String saved) {
        String a = norm(computed);
        String b = norm(saved);
        return !a.isEmpty() && a.equals(b);
    }

    private static String norm(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT)
                .replace("正确", "TRUE").replace("对", "TRUE")
                .replace("错误", "FALSE").replace("错", "FALSE");
        if (s.contains("#")) {
            String[] parts = s.split("#");
            java.util.Arrays.sort(parts);
            StringBuilder sb = new StringBuilder();
            for (String p : parts) sb.append(p);
            s = sb.toString();
        }
        if (s.matches("[A-Z]+")) {
            char[] cs = s.toCharArray();
            java.util.Arrays.sort(cs);
            s = new String(cs);
        }
        return s;
    }

    private static String typeOf(String code) {
        switch (code) {
            case "0":
                return "single";
            case "1":
                return "multiple";
            case "2":
                return "completion";
            case "3":
                return "judgement";
            case "4":
                return "shortanswer";
            default:
                return "unknown";
        }
    }

    /** 用答案库填一遍，返回匹配到的题数。 */
    static int fill(AnswerBank bank, Page page) {
        if (bank == null || page == null) return 0;
        int hit = 0;
        for (Q q : page.questions) {
            String raw = bank.find(q.title);
            if (raw == null) {
                raw = bank.find(stripPrefix(q.title));
            }
            if (raw == null) {
                raw = bank.find(stripPrefix(cleanStem(q.title)));
            }
            if (raw == null) {
                raw = bank.find(cleanStem(q.title));
            }
            if (raw == null) continue;
            String body = toAnswer(q, raw);
            if (body.isEmpty()) continue;
            q.changed = !same(body, q.saved);
            q.answer = body;
            q.matched = true;
            hit++;
        }
        return hit;
    }

    /** 去掉题号「1.」「（1）」之类的前缀，提高命中率。 */
    private static String stripPrefix(String title) {
        String s = title.trim();
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || "．.、（）()【】[] ".indexOf(s.charAt(i)) >= 0)) i++;
        return i > 0 && i < s.length() ? s.substring(i).trim() : s;
    }

    /** 去掉题干里的「【单选题】」题型标签与题号，避免同一题因前缀不同匹配不上。 */
    static String cleanStem(String title) {
        if (title == null) return "";
        String s = title.trim();
        s = s.replaceAll("^\\s*[【\\[]\\s*(?:单选|多选|单项选择|多项选择|选择|判断|填空|简答|问答|论述|计算|分析|阅读理解|完形填空|排序|连线|其它|其他)题\\s*[】\\]]\\s*", "");
        s = s.replaceAll("^\\s*[【\\[]\\s*名词解释\\s*[】\\]]\\s*", "");
        s = s.replaceAll("^\\s*[（(]?\\d+[）)]?\\s*[.．、:：]\\s*", "");
        return s.trim();
    }

    /** 去掉选项前的「A.」「B、」这类标签。 */
    static String cleanOption(String text) {
        if (text == null) return "";
        return text.replaceAll("^\\s*[A-Za-z]\\s*[.．、:：\\]\\)]\\s*", "").trim();
    }

    /** 把答案库里的原文转成平台要的形式。 */
    static String toAnswer(Q q, String raw) {
        String a = raw == null ? "" : raw.trim();
        if (a.isEmpty()) return "";
        switch (q.type) {
            case "single":
                return firstLetter(a, q.options);
            case "multiple": {
                List<String> letters = new ArrayList<>();
                String direct = lettersOnly(a);
                if (direct.length() >= 1) {
                    for (char c : direct.toCharArray()) {
                        String s = String.valueOf(c);
                        if (!letters.contains(s)) letters.add(s);
                    }
                } else {
                    for (int i = 0; i < q.options.size(); i++) {
                        String opt = AnswerBank.norm(q.options.get(i));
                        String key = AnswerBank.norm(a);
                        if (opt.isEmpty()) continue;
                        if (opt.contains(key) || key.contains(opt)) letters.add(String.valueOf((char) ('A' + i)));
                    }
                }
                letters.sort(String::compareTo);
                return String.join("", letters);
            }
            case "judgement":
                return judge(q, a);
            case "completion": {
                // 填空题：去掉「A.」「（1）」这类前缀与引号；多个空按 # 连接（超星用 # 分隔）
                String text = stripAnswerNoise(a);
                int blanks = countBlanks(q.title);
                if (blanks >= 2 && text.indexOf('#') < 0) {
                    String[] parts = text.split("[；;、\\n]");
                    if (parts.length >= 2) {
                        StringBuilder sb = new StringBuilder();
                        for (int i2 = 0; i2 < parts.length; i2++) {
                            if (i2 > 0) sb.append('#');
                            sb.append(parts[i2].trim());
                        }
                        return sb.toString();
                    }
                }
                return text;
            }
            default:
                return stripAnswerNoise(a);
        }
    }

    /** 去掉答案里的「A.」「①」「（1）」前缀和包裹用的引号。 */
    private static String stripAnswerNoise(String raw) {
        String s = raw == null ? "" : raw.trim();
        while (s.length() > 1) {
            char c = s.charAt(0);
            if (Character.isLetter(c) && s.length() > 2 && ".．、:：)）".indexOf(s.charAt(1)) >= 0) {
                s = s.substring(2).trim();
            } else if ("①②③④⑤⑥⑦⑧⑨⑩".indexOf(c) >= 0 || c == '（' || c == '(') {
                s = s.substring(1).trim();
            } else if (s.charAt(s.length() - 1) == '）' && s.length() <= 3) {
                s = s.substring(1, s.length() - 1).trim();
            } else break;
        }
        if (s.length() >= 2) {
            char a1 = s.charAt(0), a2 = s.charAt(s.length() - 1);
            if ((a1 == '"' && a2 == '"') || (a1 == '“' && a2 == '”') || (a1 == '「' && a2 == '」')) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    /** 题干里有几个空：___、＿＿、（）这类都算。 */
    private static int countBlanks(String title) {
        if (title == null || title.isEmpty()) return 0;
        int n = 0;
        String[] marks = {"___", "＿＿", "（ ）", "( )", "（）", "(  )"};
        for (String mk : marks) {
            int from = 0;
            while (true) {
                int at = title.indexOf(mk, from);
                if (at < 0) break;
                n++;
                from = at + mk.length();
            }
        }
        return n;
    }

    private static String firstLetter(String raw, List<String> options) {
        String letters = lettersOnly(raw);
        if (!letters.isEmpty()) {
            String letter = letters.substring(0, 1);
            // 答案形如「D.2030 2060」：正常情况下就是 D；
            // 但如果这个选项文本与该字母对应的选项对不上，说明选项顺序变了，按文本重新定位。
            String tail = "";
            int dot = raw.indexOf('.');
            if (dot >= 0 && dot < raw.length() - 1) tail = raw.substring(dot + 1).trim();
            String key = AnswerBank.norm(tail);
            int idx = letter.charAt(0) - 'A';
            String optText = idx >= 0 && idx < options.size() ? AnswerBank.norm(options.get(idx)) : "";
            if (!key.isEmpty() && !optText.isEmpty() && !optText.contains(key) && !key.contains(optText)) {
                for (int i = 0; i < options.size(); i++) {
                    String o = AnswerBank.norm(options.get(i));
                    if (o.isEmpty()) continue;
                    if (o.contains(key) || key.contains(o)) return String.valueOf((char) ('A' + i));
                }
            }
            return letter;
        }
        String key = AnswerBank.norm(raw);
        if (key.isEmpty()) return "";
        for (int i = 0; i < options.size(); i++) {
            String opt = AnswerBank.norm(options.get(i));
            if (opt.isEmpty()) continue;
            if (opt.contains(key) || key.contains(opt)) return String.valueOf((char) ('A' + i));
        }
        return "";
    }

    /** 只保留 A-H 的字母（答案库里常见 "A"、"AB"、"A、C"）。 */
    private static String lettersOnly(String raw) {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if (c >= 'A' && c <= 'H') {
                if (sb.indexOf(String.valueOf(c)) < 0) sb.append(c);
            } else if (sb.length() > 0 && (c == '、' || c == ',' || c == '，' || c == '/' || c == ' ')) {
                continue;
            }
        }
        return sb.toString();
    }

    /**
     * 判断题：优先看答案文本里的「对/错」，其次看字母对应的选项文本，
     * 这样才能保证选中的是含义正确的那一项（选项 A 的内容不一定就是「对」）。
     */
    private static String judge(Q q, String raw) {
        String a = raw == null ? "" : raw.trim();
        if (a.isEmpty()) return "";
        String letters = lettersOnly(a);
        String tail = "";
        int dot = a.indexOf('.');
        if (dot >= 0 && dot < a.length() - 1) tail = a.substring(dot + 1).trim();
        else if (dot < 0 && letters.isEmpty()) tail = a;

        String byText = truth(tail);
        String byLetter = "";
        if (!letters.isEmpty() && !q.options.isEmpty()) {
            int i = letters.charAt(0) - 'A';
            if (i >= 0 && i < q.options.size()) byLetter = truth(q.options.get(i));
        }
        if (!byText.isEmpty()) return byText;
        if (!byLetter.isEmpty()) return byLetter;
        if (!letters.isEmpty()) return letters.charAt(0) == 'A' ? "true" : "false";
        return "";
    }

    /** 把「对/错/√/×/正确/错误」这类文本归一成 true/false，认不出返回空串。 */
    private static String truth(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.length() <= 3) {
            String t = s.replace(".", "").replace("．", "").trim();
            if (t.equals("t") || t.equals("y") || t.equals("1") || t.equals("√")
                    || t.equals("对") || t.equals("是") || t.equals("真")) return "true";
            if (t.equals("f") || t.equals("n") || t.equals("0") || t.equals("×") || t.equals("x")
                    || t.equals("错") || t.equals("否") || t.equals("假")) return "false";
        }
        String[] yes = {"正确", "对", "√", "是", "true", "yes", "right", "√正确"};
        String[] no = {"错误", "不正确", "不对", "错", "×", "否", "false", "no", "wrong", "fault"};
        for (String y : yes) if (s.contains(y)) return "true";
        for (String n : no) if (s.contains(n)) return "false";
        return "";
    }

    /** 组装保存请求的表单；pyFlag=1 表示只保存不提交。 */
    /** 还没有答案的题目数量。 */
    static int unanswered(Page page) {
        int n = 0;
        for (Q q : page.questions) if (q.answer.isEmpty()) n++;
        return n;
    }

    private static final String AI_SYSTEM =
            "你是超星学习通测验的答题助手。必须只输出 JSON 对象，格式："
                    + "{\"answers\":[{\"i\":1,\"choices\":[\"A\"],\"text\":\"\"}]}。"
                    + "单选题给一个选项字母；多选题给多个字母；判断题只允许 A 或 B（A=对，B=错）；"
                    + "填空题/简答题把答案写进 text，多个空之间用 # 分隔。不要输出任何解释。";

    /**
     * 用 DeepSeek 补齐题库没覆盖到的题。一次请求带上所有空题，省往返。
     * 返回本次填上的题数。
     */
    static int fillAi(Context ctx, Page page, String key, String model) {
        List<Q> todo = new ArrayList<>();
        StringBuilder ask = new StringBuilder();
        int index = 0;
        for (Q q : page.questions) {
            if (!q.answer.isEmpty()) continue;
            index++;
            todo.add(q);
            ask.append('[').append(index).append("] 题型：").append(typeName(q)).append('\n');
            ask.append("题干：").append(q.title).append('\n');
            if (!q.options.isEmpty()) {
                ask.append("选项：\n");
                for (int i = 0; i < q.options.size(); i++) {
                    ask.append((char) ('A' + i)).append(". ").append(q.options.get(i)).append('\n');
                }
            }
            ask.append('\n');
        }
        if (todo.isEmpty()) return 0;

        String reply;
        try {
            reply = Ai.chat(key, model, AI_SYSTEM, ask.toString().trim(), 45);
        } catch (Exception e) {
            WorkLog.w(ctx, "ai: 请求失败 " + Ai.shorten(e.getMessage(), 160));
            return 0;
        }
        int filled = 0;
        try {
            JSONObject obj = new JSONObject(stripFence(reply));
            JSONArray arr = obj.optJSONArray("answers");
            if (arr == null) return 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject one = arr.optJSONObject(i);
                if (one == null) continue;
                int idx = one.optInt("i", 0) - 1;
                if (idx < 0 || idx >= todo.size()) continue;
                String raw = "";
                JSONArray choices = one.optJSONArray("choices");
                if (choices != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < choices.length(); k++) {
                        String c = choices.optString(k, "").trim();
                        if (!c.isEmpty()) sb.append(c.charAt(0));
                    }
                    raw = sb.toString();
                }
                if (raw.isEmpty()) raw = one.optString("text", "");
                if (raw.isEmpty()) continue;
                Q q = todo.get(idx);
                q.answer = toAnswer(q, raw);
                q.changed = !same(q.answer, q.saved);
                if (!q.answer.isEmpty()) filled++;
                WorkLog.w(ctx, "ai: q" + (idx + 1) + " -> [" + Ai.shorten(q.answer, 60) + "]"
                        + (q.answer.isEmpty() ? "  << 无法转换" : ""));
            }
        } catch (Exception e) {
            WorkLog.w(ctx, "ai: 解析失败 " + Ai.shorten(e.getMessage(), 160));
        }
        return filled;
    }

    private static String stripFence(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private static String typeName(Q q) {
        switch (q.type) {
            case "single":
                return "单选题";
            case "multiple":
                return "多选题";
            case "judgement":
                return "判断题";
            case "completion":
                return "填空题";
            case "short":
                return "简答题";
            default:
                return q.typeCode.isEmpty() ? "未知" : ("题型代码 " + q.typeCode);
        }
    }

    static Map<String, String> body(Page page) {
        return body(page, false);
    }

    /** 组装保存请求的表单；submit=true 时 pyFlag 置空，等于点「提交」。 */
    static Map<String, String> body(Page page, boolean submit) {
        Map<String, String> out = new LinkedHashMap<>(page.form);
        StringBuilder ids = new StringBuilder();
        for (Q q : page.questions) {
            ids.append(q.id).append(',');
            out.put("answer" + q.id, q.answer);
            out.put("answertype" + q.id, q.typeCode);
            // 填空题：不同题库版本的字段名可能带空位后缀；
            // 服务端会忽略未知字段，这里一并写一份，避免「保存成功但页面空白」。
            if ("completion".equals(q.type) || "short".equals(q.type)) {
                // 真机字段（2026-11-23 抓取）：填空题是 textarea
                //   name=answerEditor{id}{1..n}，另有一个隐藏的 tiankongsize{id}=空位数。
                String[] parts = q.answer.split("#", -1);
                out.put("tiankongsize" + q.id, String.valueOf(parts.length));
                for (int i = 0; i < parts.length; i++) {
                    out.put("answerEditor" + q.id + (i + 1), parts[i]);
                }
                // 兼容旧版心
                out.put("blank" + q.id, q.answer);
                out.put("answer" + q.id + "_1", parts[0]);
            }
        }
        out.put("answerwqbid", ids.toString());
        out.put("pyFlag", submit ? "" : "1");
        return out;
    }

    /**
     * 按题目顺序填答案（内置题库只有「章节 -> 第几题 -> 答案」时用这条路）。
     * 返回填上的题数。
     */
    static int fillByOrder(List<String> answers, Page page) {
        if (answers == null || answers.isEmpty() || page == null) return 0;
        int n = Math.min(answers.size(), page.questions.size());
        int filled = 0;
        for (int i = 0; i < n; i++) {
            Q q = page.questions.get(i);
            String raw = answers.get(i);
            if (raw == null || raw.trim().isEmpty()) continue;
            String a = toAnswer(q, raw);
            if (a == null || a.isEmpty()) continue;
            q.changed = !same(a, q.saved);
            q.answer = a;
            q.matched = true;
            filled++;
        }
        return filled;
    }

    /** 调试用：把页面里所有 input/textarea 的 name 与 type 打出来，便于定位填空字段名。 */
    static String dumpFields(org.jsoup.nodes.Document doc) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (org.jsoup.nodes.Element el : doc.select("input,textarea")) {
            String name = el.attr("name");
            if (name.isEmpty()) continue;
            sb.append(name).append(':').append(el.tagName());
            String t = el.attr("type");
            if (!t.isEmpty()) sb.append('[').append(t).append(']');
            sb.append(' ').append(el.val().length()).append("ch ");
            if (++n >= 40) break;
        }
        return sb.toString().trim();
    }
}
