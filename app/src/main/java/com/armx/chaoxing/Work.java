package com.armx.chaoxing;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        boolean matched;
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
            if (form == null) return null;

            Page page = new Page();
            for (Element in : form.select("input")) {
                String name = in.attr("name");
                if (name.isEmpty() || name.contains("answer")) continue;
                page.form.put(name, in.attr("value"));
            }

            Elements divs = form.select("div.singleQuesId");
            if (divs.isEmpty()) divs = doc.select("div.singleQuesId");
            for (Element div : divs) {
                Q q = new Q();
                q.id = div.attr("data");
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
                if (q.id.isEmpty() || q.title.isEmpty()) continue;
                page.questions.add(q);
            }
            return page;
        } catch (Throwable t) {
            Crash.log(t);
            return null;
        }
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
            if (raw == null) continue;
            String body = toAnswer(q, raw);
            if (body.isEmpty()) continue;
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
                return judge(a);
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
        if (!letters.isEmpty()) return letters.substring(0, 1);
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

    private static String judge(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        String[] yes = {"正确", "对", "√", "是", "true", "t", "yes", "y", "1"};
        String[] no = {"错误", "错", "×", "否", "不对", "不正确", "false", "f", "no", "n", "0"};
        for (String y : yes) if (s.contains(y)) return "true";
        for (String n : no) if (s.contains(n)) return "false";
        return "";
    }

    /** 组装保存请求的表单；pyFlag=1 表示只保存不提交。 */
    static Map<String, String> body(Page page) {
        Map<String, String> out = new LinkedHashMap<>(page.form);
        StringBuilder ids = new StringBuilder();
        for (Q q : page.questions) {
            ids.append(q.id).append(',');
            out.put("answer" + q.id, q.answer);
            out.put("answertype" + q.id, q.typeCode);
        }
        out.put("answerwqbid", ids.toString());
        out.put("pyFlag", "1");
        return out;
    }

    /**
     * 按题目顺序填答案（内置题库只有「章节 -> 第几题 -> 答案」时用这条路）。
     * 返回填上的题数。
     */
    static int fillByOrder(List<String> answers, Page page) {
        if (answers == null || answers.isEmpty() || page == null) return 0;
        int n = Math.min(answers.size(), page.questions.size());
        for (int i = 0; i < n; i++) {
            Q q = page.questions.get(i);
            String raw = answers.get(i);
            if (raw == null || raw.trim().isEmpty()) continue;
            String a = toAnswer(q, raw);
            if (a == null || a.isEmpty()) continue;
            q.answer = a;
            q.matched = true;
        }
        return n;
    }
}
