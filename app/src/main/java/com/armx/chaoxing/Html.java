package com.armx.chaoxing;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTML / JSON 解析，逻辑与旧版 server.py 的 decode_* 一一对应。 */
final class Html {

    private static final Pattern CPI = Pattern.compile("cpi=(.*?)&");
    private static final Pattern CUR = Pattern.compile("^cur(\\d{1,20})$");
    private static final Pattern MARG = Pattern.compile("mArg=\\{(.*?)\\};", Pattern.DOTALL);

    private Html() {
    }

    static List<Model.Course> courses(String html) {
        List<Model.Course> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return out;
        Document doc = Jsoup.parse(html);
        for (Element c : doc.select("div.course")) {
            if (!c.select("a.not-open-tip").isEmpty()) continue;
            if (!c.select("div.not-open-tip").isEmpty()) continue;
            Element ci = c.selectFirst("input.clazzId");
            Element coi = c.selectFirst("input.courseId");
            Element ns = c.selectFirst("span.course-name");
            Element a = c.selectFirst("a");
            if (ci == null || coi == null || ns == null || a == null) continue;
            Model.Course course = new Model.Course();
            course.clazzId = ci.attr("value");
            course.courseId = coi.attr("value");
            String href = a.attr("href");
            Matcher m = CPI.matcher(href);
            course.cpi = m.find() ? m.group(1) : "";
            String title = ns.attr("title");
            course.title = title.isEmpty() ? ns.text().trim() : title;
            Element tt = c.selectFirst("p.color3");
            course.teacher = tt == null ? "" : tt.attr("title");
            if (course.courseId.isEmpty() || course.clazzId.isEmpty()) continue;
            out.add(course);
        }
        return out;
    }

    static List<String> folders(String html) {
        List<String> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return out;
        Document doc = Jsoup.parse(html);
        for (Element c : doc.select("ul.file-list > li")) {
            String id = c.attr("fileid");
            if (id == null || id.isEmpty()) continue;
            out.add(id);
        }
        return out;
    }

    static List<Model.Point> points(String html) {
        List<Model.Point> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return out;
        Document doc = Jsoup.parse(html);
        for (Element unit : doc.select("div.chapter_unit")) {
            String chapter = "";
            for (String sel : new String[]{"div.chapter_head h1", "div.chapter_head h3", "div.chapter_head",
                    "h1.chapterName", "span.chapterName", "div.chapter_item h1"}) {
                Element h = unit.selectFirst(sel);
                if (h != null && !h.text().trim().isEmpty()) {
                    chapter = h.text().replace("\n", "").trim();
                    break;
                }
            }
            for (Element li : unit.select("li")) {
                Element d = li.selectFirst("div");
                if (d == null) continue;
                Matcher m = CUR.matcher(d.id());
                if (!m.find()) continue;
                Model.Point p = new Model.Point();
                p.id = m.group(1);
                Element t = d.selectFirst("a.clicktitle");
                p.title = t == null ? "" : t.text().replace("\n", "").trim();
                p.chapter = chapter;
                Element ji = d.selectFirst("input.knowledgeJobCount");
                Element tip = d.selectFirst("span.bntHoverTips");
                String tipText = tip == null ? "" : tip.text();
                if (ji != null) {
                    try {
                        String jc = ji.attr("value");
                        p.jobCount = jc.isEmpty() ? 1 : Integer.parseInt(jc.trim());
                    } catch (Exception e) {
                        p.jobCount = 1;
                    }
                } else if (tipText.contains("解锁")) {
                    p.locked = true;
                }
                p.finished = tipText.contains("已完成");
                out.add(p);
            }
        }
        return out;
    }

    static final class Cards {
        boolean notOpen;
        final List<Model.Job> jobs = new ArrayList<>();
        JSONObject info = new JSONObject();
    }

    static Cards cards(String html) {
        Cards res = new Cards();
        if (html == null) return res;
        if (html.contains("章节未开放")) {
            res.notOpen = true;
            return res;
        }
        Matcher m = MARG.matcher(html.replace(" ", ""));
        if (!m.find()) return res;
        JSONObject data;
        try {
            data = new JSONObject("{" + m.group(1) + "}");
        } catch (Exception e) {
            return res;
        }
        JSONObject defaults = data.optJSONObject("defaults");
        if (defaults == null) defaults = new JSONObject();
        try {
            res.info.put("ktoken", defaults.optString("ktoken", ""));
            res.info.put("mtEnc", defaults.optString("mtEnc", ""));
            res.info.put("defenc", defaults.optString("defenc", ""));
            res.info.put("cardid", defaults.optString("cardid", ""));
            res.info.put("cpi", defaults.optString("cpi", ""));
            res.info.put("qnenc", defaults.optString("qnenc", ""));
            res.info.put("knowledgeid", defaults.optString("knowledgeid", ""));
            res.info.put("reportTimeInterval", defaults.optInt("reportTimeInterval", 60));
        } catch (Exception ignored) {
        }

        JSONArray atts = data.optJSONArray("attachments");
        if (atts == null) return res;
        for (int i = 0; i < atts.length(); i++) {
            JSONObject card = atts.optJSONObject(i);
            if (card == null) continue;
            if (card.optBoolean("isPassed", false)) continue;
            String otherInfo = card.optString("otherInfo", "");
            int amp = otherInfo.indexOf('&');
            if (amp >= 0) otherInfo = otherInfo.substring(0, amp);
            JSONObject prop = card.optJSONObject("property");
            if (prop == null) prop = new JSONObject();
            boolean hasJob = card.has("job") && !card.isNull("job");
            String type = card.optString("type", "").toLowerCase(java.util.Locale.ROOT);

            if (!hasJob) {
                if ("read".equals(type) && !prop.optBoolean("read", false)) {
                    Model.Job j = new Model.Job();
                    j.type = "read";
                    j.title = prop.optString("title", "");
                    j.kid = prop.optString("id", "");
                    j.jobid = card.optString("jobid", card.optString("id", ""));
                    j.jtoken = card.optString("jtoken", "");
                    j.mid = card.optString("mid", "");
                    j.otherinfo = otherInfo;
                    j.enc = card.optString("enc", "");
                    j.aid = card.optString("aid", "");
                    j.raw = card;
                    res.jobs.add(j);
                }
                continue;
            }

            if ("video".equals(type)) {
                Model.Job j = new Model.Job();
                j.type = "video";
                j.jobid = card.optString("jobid", "");
                j.name = prop.optString("name", "");
                j.otherinfo = otherInfo;
                j.mid = card.optString("mid", "");
                j.objectid = card.optString("objectId", "");
                j.aid = card.optString("aid", "");
                j.rt = prop.optString("rt", "");
                j.attDuration = card.optString("attDuration", "");
                j.attDurationEnc = card.optString("attDurationEnc", "");
                j.faceEnc = card.optString("videoFaceCaptureEnc", "");
                j.raw = card;
                res.jobs.add(j);
            } else if ("document".equals(type)) {
                Model.Job j = new Model.Job();
                j.type = "document";
                j.jobid = card.optString("jobid", "");
                j.otherinfo = otherInfo;
                j.jtoken = card.optString("jtoken", "");
                j.mid = card.optString("mid", "");
                j.enc = card.optString("enc", "");
                j.aid = card.optString("aid", "");
                j.objectid = prop.optString("objectid", "");
                j.raw = card;
                res.jobs.add(j);
            } else if ("work".equals(type) || "workid".equals(type)) {
                Model.Job j = new Model.Job();
                j.type = "work";
                j.jobid = card.optString("jobid", "");
                j.name = prop.optString("name", prop.optString("title", ""));
                j.otherinfo = otherInfo;
                j.mid = card.optString("mid", "");
                j.enc = card.optString("enc", "");
                j.aid = card.optString("aid", "");
                j.raw = card;
                res.jobs.add(j);
            }
            // live 等其它类型在原生版里跳过（旧版也只打印日志，不做实际动作）
        }
        return res;
    }
}
