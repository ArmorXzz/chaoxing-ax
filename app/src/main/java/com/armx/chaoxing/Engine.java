package com.armx.chaoxing;

import android.content.Context;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 刷课引擎：把旧版 server.py 的全部业务逻辑用原生 Java 重写。
 * 所有方法除 start/stop 外都是阻塞的，调用方需放到后台线程。
 */
public final class Engine {

    private static final String LOGIN_URL = "https://passport2.chaoxing.com/fanyalogin";
    private static final String LIST_URL = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata";
    private static final String INTERACTION_URL = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction";
    private static final String ACCOUNT_URL = "https://passport2.chaoxing.com/mooc/accountManage";
    private static final String STUDENT_COURSE = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse";
    private static final String CARDS_URL = "https://mooc1.chaoxing.com/mooc-ans/knowledge/cards";
    private static final String STATUS_URL = "https://mooc1.chaoxing.com/ananas/status/";
    private static final String LOG_URL = "https://mooc1.chaoxing.com/mooc-ans/multimedia/log/a/";
    private static final String READ_URL = "https://mooc1.chaoxing.com/ananas/job/readv2";
    private static final String DOC_URL = "https://mooc1.chaoxing.com/ananas/job/document";
    private static final String WORK_URL = "https://mooc1.chaoxing.com/mooc-ans/api/work";
    private static final String WORK_SUBMIT = "https://mooc1.chaoxing.com/mooc-ans/work/addStudentWorkNew";
    private static final String EMPTY_URL = "https://mooc1.chaoxing.com/mooc-ans/mycourse/studentstudyAjax";

    private static final String VIDEO_REFERER =
            "https://mooc1.chaoxing.com/ananas/modules/video/index.html?v=2025-0725-1842";
    private static final Pattern NAME_PAT = Pattern.compile("id=\"messageName\"\\s+value=\"([^\"]*)\"");
    private static final Pattern RT_PAT = Pattern.compile("-rt_([1d])");

    private static Engine instance;

    public static synchronized Engine get(Context ctx) {
        if (instance == null) instance = new Engine(ctx.getApplicationContext());
        return instance;
    }

    private final Http http = new Http();
    private final Context app;
    private volatile String curChapter = "";
    private final Store store;
    private final Random rnd = new Random();

    private volatile String userName = "";
    private volatile String uid = "";
    private volatile boolean loggedIn;

    final List<Model.Course> courses = new CopyOnWriteArrayList<>();
    final Map<String, Model.VTask> tasks = new ConcurrentHashMap<>();

    private volatile boolean running;
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);
    private ExecutorService pool;
    private Semaphore sem = new Semaphore(999);
    private Thread runner;

    /** 诊断信息：任务跑完但一个视频都没出现时，界面能看到卡在哪一步。 */
    private volatile String lastError = "";
    /** 国家安全教育循环刷课时的提示语，优先显示。 */
    private volatile String nationNote = "";
    /** 自动答题（只保存不提交）的结果提示。 */
    private volatile String workNote = "";
    /** 闯关模式下仍有章节未解锁的提示。 */
    private volatile String notOpenNote = "";
    private final java.util.concurrent.atomic.AtomicInteger answers =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger scannedPoints =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger scannedJobs =
            new java.util.concurrent.atomic.AtomicInteger();

    public String hint() {
        String e = lastError;
        if (!e.isEmpty()) return e;
        String n = nationNote;
        if (!n.isEmpty()) return n;
        String w = workNote;
        if (!w.isEmpty()) return w;
        String o = notOpenNote;
        if (!o.isEmpty()) return o;
        int p = scannedPoints.get();
        int j = scannedJobs.get();
        if (j > 0) return "已发现 " + j + " 个任务点";
        if (p > 0) return "已扫描 " + p + " 个章节，未发现待完成任务点";
        if (running) return "正在拉取章节…";
        return "";
    }

    /** 已自动填写的题目总数（设置页展示用）。 */
    public int answersFilled() {
        return answers.get();
    }

    public void clearHint() {
        lastError = "";
        nationNote = "";
        workNote = "";
        notOpenNote = "";
        answers.set(0);
        scannedPoints.set(0);
        scannedJobs.set(0);
    }

    private Engine(Context ctx) {
        app = ctx;
        store = new Store(ctx);
    }

    /**
     * 冷启动恢复：上一次的 cookie 直接灌回来，再拿「课程列表」验一次。
     * 验不过（登录态过期）会尝试用已保存的密码自动重登，尽量不让用户重输。
     */
    public boolean restore() {
        String saved = store.cookies();
        if (saved == null || saved.isEmpty()) return false;
        http.loadCookies(saved);
        uid = http.cookie("_uid");
        if (uid.isEmpty()) uid = http.cookie("UID");
        try {
            List<Model.Course> list = loadCourses();
            String name = fetchName();
            if (name.isEmpty()) throw new IOException("会话失效");
            loggedIn = true;
            userName = name;
            return true;
        } catch (Throwable t) {
            http.clearCookies();
            return autoLogin();
        }
    }

    /** 用最近一次记住的账号静默重登。 */
    private boolean autoLogin() {
        String last = store.lastUser();
        if (last == null || last.isEmpty()) return false;
        for (Model.Account a : store.accounts()) {
            if (!a.username.equals(last)) continue;
            String pwd = Crypt.decrypt(a.password);
            if (pwd == null || pwd.isEmpty()) continue;
            try {
                login(a.username, pwd);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    // ==================== 对外状态 ====================

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public String userName() {
        return userName;
    }

    public boolean isRunning() {
        return running;
    }

    public Store store() {
        return store;
    }

    public List<Model.VTask> snapshot() {
        return new ArrayList<>(tasks.values());
    }

    public int[] summary() {
        int done = 0, failed = 0;
        for (Model.VTask v : tasks.values()) {
            if (v.done()) done++;
            else if (v.failed()) failed++;
        }
        return new int[]{tasks.size(), done, failed};
    }

    // ==================== 登录 ====================

    public void login(String username, String password) throws IOException {
        http.clearCookies();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("fid", "-1");
        form.put("uname", Crypt.encrypt(username));
        form.put("password", Crypt.encrypt(password));
        form.put("refer", "https%3A%2F%2Fi.chaoxing.com");
        form.put("t", "true");
        form.put("forbidotherlogin", "0");
        form.put("validate", "");
        form.put("doubleFactorLogin", "0");
        form.put("independentId", "0");

        Http.Resp r = http.post(LOGIN_URL, form, null);
        if (r.code == 0) throw new IOException("网络异常，请检查网络后重试");
        if (r.code != 200) throw new IOException("登录请求失败（HTTP " + r.code + "）");
        JSONObject j = r.json();
        if (j == null) throw new IOException("响应解析失败");
        if (!j.optBoolean("status")) {
            String msg = j.optString("msg2", "");
            if (msg == null || msg.trim().isEmpty()) msg = "账号或密码错误";
            throw new IOException(msg);
        }
        uid = http.cookie("_uid");
        if (uid.isEmpty()) uid = http.cookie("UID");
        String name = fetchName();
        userName = name.isEmpty() ? j.optString("name", username) : name;
        loggedIn = true;
        store.saveCookies(http.dumpCookies());
    }

    private String fetchName() {
        Http.Resp r = http.get(ACCOUNT_URL, null, null);
        if (r.code == 200) {
            Matcher m = NAME_PAT.matcher(r.body);
            if (m.find()) return m.group(1).trim();
        }
        return "";
    }

    public void logout() {
        stop();
        loggedIn = false;
        userName = "";
        uid = "";
        courses.clear();
        tasks.clear();
        http.clearCookies();
        store.saveCookies("");
    }

    // ==================== 课程 ====================

    public List<Model.Course> loadCourses() throws IOException {
        List<Model.Course> all = new ArrayList<>();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("courseType", "1");
        form.put("courseFolderId", "0");
        form.put("query", "");
        form.put("superstarClass", "0");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", INTERACTION_URL);

        Http.Resp r = http.post(LIST_URL, form, headers);
        if (r.code != 200) throw new IOException("读取课程失败（HTTP " + r.code + "）");
        all.addAll(Html.courses(r.body));

        Http.Resp home = http.get(INTERACTION_URL, null, null);
        if (home.code == 200) {
            for (String fid : Html.folders(home.body)) {
                Map<String, String> f = new LinkedHashMap<>(form);
                f.put("courseFolderId", fid);
                Http.Resp fr = http.post(LIST_URL, f, headers);
                if (fr.code == 200) all.addAll(Html.courses(fr.body));
            }
        }

        List<Model.Course> unique = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Model.Course c : all) {
            if (seen.containsKey(c.key())) continue;
            seen.put(c.key(), true);
            unique.add(c);
        }
        courses.clear();
        courses.addAll(unique);
        return unique;
    }

    // ==================== 任务 ====================

    public boolean start(List<Model.Course> selected) {
        if (running || selected == null || selected.isEmpty()) return false;
        stopFlag.set(false);
        clearHint();
        tasks.clear();
        int par = store.parallel();
        sem = new Semaphore(par > 0 ? par : 999);
        pool = Executors.newCachedThreadPool();
        running = true;
        final List<Model.Course> list = new ArrayList<>(selected);
        runner = new Thread(() -> {
            try {
                for (Model.Course c : list) {
                    if (stopFlag.get()) break;
                    if (isNationCourse(c.title)) {
                        runNationLoop(c);
                    } else {
                        dealCourse(c);
                    }
                }
            } catch (Throwable t) {
                lastError = "运行出错：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                Crash.log(t);
            } finally {
                running = false;
                if (pool != null) pool.shutdown();
            }
        }, "cx-runner");
        runner.start();
        return true;
    }

    public void stop() {
        stopFlag.set(true);
        for (Model.VTask v : tasks.values()) {
            if ("播放中".equals(v.status) || "结算中".equals(v.status) || "待开始".equals(v.status)) {
                v.status = "已停止";
            }
        }
    }

    /** 国家安全教育：一轮跑完后如果还没到目标章节，就自动再来一轮。 */
    private static final String NATION_TITLE = "国家安全教育";
    private static final String NATION_TARGET = "总体国家安全观理论的新动态新发展";
    private static final int NATION_MAX_ROUND = 40;

    private static boolean isNationCourse(String title) {
        return title != null && title.contains(NATION_TITLE);
    }

    private void runNationLoop(Model.Course course) {
        int empty = 0;
        for (int round = 1; round <= NATION_MAX_ROUND; round++) {
            if (stopFlag.get()) return;
            int before = scannedJobs.get();
            dealCourse(course);
            if (stopFlag.get()) return;
            if (roundReachedTarget(course)) {
                lastError = "";
                return;
            }
            if (scannedJobs.get() == before) {
                empty++;
                if (empty >= 2) {
                    lastError = "「" + NATION_TITLE + "」没有可执行的任务点，已停止循环";
                    return;
                }
            } else {
                empty = 0;
            }
            nationNote = "第 " + round + " 轮完成，继续刷「" + NATION_TITLE + "」…";
            sleep(2500);
            nationNote = "";
        }
        lastError = "「" + NATION_TITLE + "」已循环 " + NATION_MAX_ROUND + " 轮，仍未到达目标章节";
    }

    /** 目标章节「1.15.2 总体国家安全观理论的新动态新发展」是否已完成。 */
    private boolean roundReachedTarget(Model.Course course) {
        List<Model.Point> pts = loadPoints(course);
        for (Model.Point p : pts) {
            if (p.title != null && p.title.contains(NATION_TARGET)) return p.finished;
        }
        return false;
    }

    private void dealCourse(Model.Course course) {
        List<Model.Point> points = loadPoints(course);
        scannedPoints.addAndGet(points.size());
        if (points.isEmpty() && lastError.isEmpty()) {
            lastError = "章节列表为空（可能登录已失效或课程无权限）";
        }
        List<Future<?>> futures = new ArrayList<>();
        List<Model.Point> pending = new ArrayList<>();
        for (Model.Point p : points) {
            // 题库是按「章节」组织的：优先用 chapter_unit 的章节名，
            // 只有拿不到时才退回小节标题（否则中途开始刷课会对错章节）。
            curChapter = p.chapter == null || p.chapter.isEmpty() ? (p.title == null ? "" : p.title) : p.chapter;
            if (stopFlag.get()) break;
            if (p.finished) continue;
            Html.Cards cards = getJobs(course, p);
            if (cards.notOpen) {
                // 闯关模式：前面章节做完才会解锁，先记下来，本节跑完再回来试
                pending.add(p);
                continue;
            }
            if (cards.jobs.isEmpty()) {
                markEmpty(course, p);
                continue;
            }
            for (Model.Job job : cards.jobs) {
                if (stopFlag.get()) break;
                scannedJobs.incrementAndGet();
                dispatch(course, job, cards.info, futures);
            }
        }

        // 回头重试未解锁的章节：闯关链条是一节接一节打开的，多试几轮往往能一路推下去
        for (int round = 1; round <= 4 && !pending.isEmpty() && !stopFlag.get(); round++) {
            sleep(3000L + round * 2000L);
            List<Model.Point> still = new ArrayList<>();
            for (Model.Point p : pending) {
                if (stopFlag.get()) break;
                Html.Cards cards = getJobs(course, p);
                if (cards.notOpen) {
                    still.add(p);
                    continue;
                }
                if (cards.jobs.isEmpty()) markEmpty(course, p);
                for (Model.Job job : cards.jobs) {
                    if (stopFlag.get()) break;
                    scannedJobs.incrementAndGet();
                    dispatch(course, job, cards.info, futures);
                }
            }
            pending = still;
            if (pending.isEmpty()) break;
        }
        if (!pending.isEmpty()) {
            notOpenNote = "还有 " + pending.size() + " 个章节未解锁，闯关模式需要按顺序完成";
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 空章节也要汇报一次，否则闯关模式下后面的章节永远不会解锁。
     */
    private void markEmpty(Model.Course course, Model.Point point) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("courseId", course.courseId);
        p.put("clazzid", course.clazzId);
        p.put("chapterId", point.id);
        p.put("cpi", course.cpi);
        p.put("verificationcode", "");
        p.put("mooc2", "1");
        p.put("microTopicId", "0");
        p.put("editorPreview", "0");
        http.get(EMPTY_URL, p, null);
        sleep(400 + rnd.nextInt(400));
    }

    private List<Model.Point> loadPoints(Model.Course course) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("courseid", course.courseId);
        p.put("clazzid", course.clazzId);
        p.put("cpi", course.cpi);
        p.put("ut", "s");
        Http.Resp r = http.get(STUDENT_COURSE, p, null);
        if (r.code != 200) return new ArrayList<>();
        return Html.points(r.body);
    }

    private Html.Cards getJobs(Model.Course course, Model.Point point) {
        Html.Cards total = new Html.Cards();
        Map<String, String> base = new LinkedHashMap<>();
        base.put("clazzid", course.clazzId);
        base.put("courseid", course.courseId);
        base.put("knowledgeid", point.id);
        base.put("ut", "s");
        base.put("cpi", course.cpi);
        base.put("v", "2025-0424-1038-3");
        base.put("mooc2", "1");
        for (int num = 0; num < 7; num++) {
            if (stopFlag.get()) break;
            Map<String, String> p = new LinkedHashMap<>(base);
            p.put("num", String.valueOf(num));
            Http.Resp r = http.get(CARDS_URL, p, null);
            if (r.code != 200) break;
            Html.Cards one = Html.cards(r.body);
            if (one.notOpen) {
                total.notOpen = true;
                return total;
            }
            if (one.jobs.isEmpty()) break;
            total.jobs.addAll(one.jobs);
            if (total.info.length() == 0) {
                try {
                    total.info = new JSONObject(one.info.toString());
                } catch (Exception ignored) {
                }
            }
            sleep((long) (600 + rnd.nextInt(600)));
        }
        return total;
    }

    private void dispatch(Model.Course course, Model.Job job, JSONObject info, List<Future<?>> futures) {
        if ("video".equals(job.type)) {
            final Model.VTask vt = new Model.VTask(UUID.randomUUID().toString(), job.name);
            tasks.put(vt.tid, vt);
            Future<?> f = pool.submit(() -> {
                try {
                    sem.acquire();
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    runVideo(course, job, vt);
                } catch (Throwable t) {
                    vt.status = "失败";
                } finally {
                    sem.release();
                }
            });
            futures.add(f);
        } else if ("read".equals(job.type)) {
            doRead(course, job);
        } else if ("document".equals(job.type)) {
            doDocument(course, job, info);
        } else if ("work".equals(job.type)) {
            doWork(course, job, info);
        }
    }

    /**
     * 章节测验 / 任务点作业：取题 → 用本地答案库填空 → 只保存（pyFlag=1）不提交。
     * 不自动提交是因为提交会判分，答案不对等于白白丢掉一次机会。
     */
    private void doWork(Model.Course course, Model.Job job, JSONObject info) {
        // 答案库随包内置，且只对「国家安全教育」这一门课生效
        if (!isNationCourse(course.title)) return;
        AnswerBank bank = AnswerBank.nation(app);
        if (bank == null || (bank.size() == 0 && bank.chapterTotal() == 0)) return;
        java.util.List<String> seq = bank.chapter(curChapter);

        String jid = job.jobid == null ? "" : job.jobid;
        Map<String, String> p = new LinkedHashMap<>();
        p.put("api", "1");
        p.put("workId", jid.startsWith("work-") ? jid.substring(5) : jid);
        p.put("jobid", jid);
        p.put("originJobId", jid);
        p.put("needRedirect", "true");
        p.put("skipHeader", "true");
        p.put("knowledgeid", info.optString("knowledgeid", job.kid));
        p.put("ktoken", info.optString("ktoken", ""));
        p.put("cpi", course.cpi);
        p.put("ut", "s");
        p.put("clazzId", course.clazzId);
        p.put("type", "");
        p.put("enc", job.enc);
        p.put("mooc2", "1");
        p.put("courseid", course.courseId);

        Http.Resp r = http.get(WORK_URL, p, null);
        if (r.code != 200 || r.body == null || r.body.isEmpty()) {
            workNote = "测验「" + job.name + "」打开失败（HTTP " + r.code + "）";
            return;
        }
        if (r.body.contains("教师未创建完成该测验")) return;

        Work.Page page = Work.parse(app, r.body);
        if (page == null || page.questions.isEmpty()) return;

        int hit = seq.isEmpty() ? Work.fill(bank, page) : Work.fillByOrder(seq, page);
        int total = page.questions.size();
        if (hit == 0) {
            workNote = "测验「" + job.name + "」共 " + total + " 题，答案库里一道都没匹配上";
            return;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Origin", "https://mooc1.chaoxing.com");
        headers.put("Referer", WORK_URL);
        Http.Resp s = http.post(WORK_SUBMIT, Work.body(page), headers);

        answers.addAndGet(hit);
        if (s.code == 200) {
            workNote = "已填好 " + hit + "/" + total + " 题并保存，还没提交，需自己到网页端点提交";
        } else {
            workNote = "测验「" + job.name + "」答案保存失败（HTTP " + s.code + "）";
        }
        sleep(800 + rnd.nextInt(600));
    }

    private void doRead(Model.Course course, Model.Job job) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("jobid", job.jobid);
        p.put("knowledgeid", job.kid.isEmpty() ? job.jobid : job.kid);
        p.put("jtoken", job.jtoken);
        p.put("courseid", course.courseId);
        p.put("clazzid", course.clazzId);
        http.get(READ_URL, p, null);
    }

    private void doDocument(Model.Course course, Model.Job job, JSONObject info) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("jobid", job.jobid);
        p.put("knowledgeid", info.optString("knowledgeid", ""));
        p.put("courseid", course.courseId);
        p.put("clazzid", course.clazzId);
        p.put("jtoken", job.jtoken);
        p.put("_dc", ts());
        http.get(DOC_URL, p, null);
    }

    // ==================== 视频任务 ====================

    private static final class Report {
        final boolean passed;
        final int code;

        Report(boolean passed, int code) {
            this.passed = passed;
            this.code = code;
        }
    }

    private void runVideo(Model.Course course, Model.Job job, Model.VTask vt) {
        JSONObject st = videoStatus(job);
        if (st == null) {
            vt.status = "失败";
            vt.detail = "取不到视频状态：网络异常或登录已失效";
            return;
        }
        if (!"success".equals(st.optString("status"))) {
            vt.status = "失败";
            vt.detail = "视频状态返回 " + st.optString("status", "未知") + "，该任务点可能已关闭";
            return;
        }
        String dtoken = st.optString("dtoken", "");
        final int dur = st.optInt("duration", 0);
        if (dtoken.isEmpty()) {
            vt.status = "失败";
            vt.detail = "没有拿到 dtoken，接口可能改版了";
            return;
        }
        if (dur <= 0) {
            vt.status = "失败";
            vt.detail = "服务端返回的视频时长为 0";
            return;
        }
        vt.duration = dur;
        double play = job.raw == null ? 0 : job.raw.optLong("playTime", 0) / 1000;
        vt.play = play;
        vt.progress = dur > 0 ? play / dur : 0;

        // 先按「已完成」上报一次：服务端已经算完成的任务点会直接返回 isPassed，
        // 不用再走一遍几十秒的播放循环（chaoxing-3.1.4 的做法）。
        Report pre = report(course, job, dtoken, dur, dur, 4);
        if (pre.passed) {
            vt.status = "完成";
            vt.play = dur;
            vt.progress = 1f;
            return;
        }

        vt.status = "播放中";
        double lastLog = play;
        long lastIter = System.currentTimeMillis();
        double wait = 20 + rnd.nextInt(21);
        int fails = 0;
        int logs = 1;          // 已经发过 1 次预上报
        int patches = 0;       // 结算阶段补点次数
        int lastCode = 200;    // 最近一次上报的 HTTP 状态
        float speed = Math.min(2f, Math.max(1f, store.speed()));

        while (play < dur) {
            if (stopFlag.get()) {
                vt.status = "已停止";
                return;
            }
            if (play - lastLog >= wait || play >= dur) {
                // 到末尾时用整段时长，避免 (int) 截断导致服务端认为「没播完」
                int point = play >= dur ? dur : (int) Math.floor(play);
                Report r = report(course, job, dtoken, dur, point, 3);
                logs++;
                lastCode = r.code;
                if (r.code == 403) {
                    fails++;
                    if (fails >= 5) {
                        vt.status = "失败";
                        vt.detail = "累计 5 次 403：登录态失效或触发风控（上报 " + logs + " 次，服务端 " + pct(vt) + "）";
                        return;
                    }
                    // 403 通常是 dtoken 过期：重新取一次视频状态再继续
                    String nd = refreshDtoken(job, dtoken);
                    if (nd != null) dtoken = nd;
                    sleep(5000L + rnd.nextInt(5000));
                    continue;
                }
                if (r.passed) {
                    vt.status = "完成";
                    vt.play = dur;
                    vt.progress = 1f;
                    return;
                }
                lastLog = play;
                wait = 20 + rnd.nextInt(21);
            }
            long now = System.currentTimeMillis();
            double dt = (now - lastIter) / 1000.0 * speed;
            lastIter = now;
            play = Math.min(dur, play + dt);
            vt.play = play;
            vt.progress = dur > 0 ? play / dur : 0;
            sleep(1000);
        }

        vt.progress = 1f;
        vt.status = "结算中";
        sleep(3000);
        int passed403 = 0;

        for (int i = 0; i < 3; i++) {
            if (stopFlag.get()) {
                vt.status = "已停止";
                return;
            }
            Report r = report(course, job, dtoken, dur, dur, 4);
            logs++;
            lastCode = r.code;
            if (r.passed) {
                vt.status = "完成";
                vt.detail = "";
                return;
            }
            if (r.code == 403) {
                passed403++;
                // 结算阶段同样要刷新 dtoken，否则后面几次上报全废
                String nd = refreshDtoken(job, dtoken);
                if (nd != null) dtoken = nd;
                sleep(4000L + rnd.nextInt(3000));
                continue;
            }
            sleep(3000);
        }

        sleep(2000);
        JSONObject st2 = videoStatus(job);
        if (st2 == null) {
            vt.status = "失败";
            vt.detail = "结算后取不到服务端进度（上报 " + logs + " 次，末次 HTTP " + lastCode + "）";
            vt.progress = 0.99;
            return;
        }
        if (!"success".equals(st2.optString("status"))) {
            vt.status = "失败";
            vt.detail = "结算后状态异常：" + st2.optString("status", "未知") + "（上报 " + logs + " 次）";
            vt.progress = 0.99;
            return;
        }

        long serverPlay = st2.optLong("playTime", 0);
        double serverPlaySec = serverPlay > 1000 ? serverPlay / 1000.0 : serverPlay;
        int serverDur = st2.optInt("duration", dur);
        if (serverDur <= 0) serverDur = dur;
        boolean serverPassed = st2.optBoolean("isPassed", false);

        if (serverPlaySec >= serverDur * 0.95 || serverPassed) {
            vt.status = "完成";
            vt.play = serverPlaySec;
            vt.progress = 1f;
            return;
        }

        if (serverPlaySec < serverDur) {
            play = serverPlaySec;
            vt.play = play;
            vt.progress = play / serverDur;
            double patchUntil = Math.min(serverDur, serverPlaySec + 180);
            while (play < patchUntil) {
                if (stopFlag.get()) {
                    vt.status = "已停止";
                    return;
                }
                Report r = report(course, job, dtoken, serverDur, (int) play, 3);
                logs++;
                patches++;
                lastCode = r.code;
                if (r.code == 403) {
                    String nd = refreshDtoken(job, dtoken);
                    if (nd != null) dtoken = nd;
                }
                if (r.passed) {
                    vt.status = "完成";
                    vt.play = serverDur;
                    vt.progress = 1f;
                    return;
                }
                play += 15;
                vt.play = play;
                vt.progress = play / serverDur;
                sleep(5000);
            }
            for (int i = 0; i < 2; i++) {
                Report r = report(course, job, dtoken, serverDur, serverDur, 4);
                logs++;
                lastCode = r.code;
                if (r.passed) {
                    vt.status = "完成";
                    vt.progress = 1f;
                    return;
                }
                sleep(2000);
            }
        }

        int sp = (int) Math.round(serverPlaySec * 100 / (double) serverDur);
        vt.progress = Math.min(0.99, serverPlaySec / (double) serverDur);
        vt.status = "失败";
        vt.detail = "服务端只认 " + sp + "%（" + fmtSec(serverPlaySec) + " / " + fmtSec(serverDur)
                + "），上报 " + logs + " 次、补点 " + patches + " 次、403 " + (fails + passed403) + " 次、末次 HTTP " + lastCode;
    }

    /** 403 后重新取 dtoken；取不到返回 null。 */
    private String refreshDtoken(Model.Job job, String cur) {
        JSONObject rs = videoStatus(job);
        if (rs == null || !"success".equals(rs.optString("status"))) return null;
        String nd = rs.optString("dtoken", "");
        return nd.isEmpty() ? null : nd;
    }

    private static String fmtSec(double sec) {
        long s = Math.max(0, Math.round(sec));
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private static String pct(Model.VTask vt) {
        int p = (int) Math.round(Math.max(0, Math.min(1, vt.progress)) * 100);
        return p + "%";
    }

    private JSONObject videoStatus(Model.Job job) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("k", fid());
        p.put("flag", "normal");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", VIDEO_REFERER);
        Http.Resp r = http.get(STATUS_URL + job.objectid, p, headers);
        if (r.code != 200) return null;
        return r.json();
    }

    private Report report(Model.Course course, Model.Job job, String dtoken, int dur, double play, int isdrag) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("clazzId", course.clazzId);
        p.put("playingTime", String.valueOf((int) play));
        p.put("duration", String.valueOf(dur));
        p.put("clipTime", "0_" + dur);
        p.put("objectId", job.objectid);
        p.put("otherInfo", job.otherinfo);
        p.put("courseId", course.courseId);
        p.put("jobid", job.jobid);
        p.put("userid", uid);
        p.put("isdrag", String.valueOf(isdrag));
        p.put("view", "pc");
        p.put("enc", enc(course, job, play, dur));
        p.put("dtype", "Video");
        if (!job.faceEnc.isEmpty()) p.put("videoFaceCaptureEnc", job.faceEnc);
        if (!job.attDuration.isEmpty()) p.put("attDuration", job.attDuration);
        if (!job.attDurationEnc.isEmpty()) p.put("attDurationEnc", job.attDurationEnc);

        String rt = job.rt == null ? "" : job.rt;
        if (rt.isEmpty()) {
            Matcher m = RT_PAT.matcher(job.otherinfo == null ? "" : job.otherinfo);
            if (m.find()) rt = "d".equals(m.group(1)) ? "0.9" : "1";
        }
        String[] rts = rt.isEmpty() ? new String[]{"0.9", "1"} : new String[]{rt};

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", VIDEO_REFERER);
        String url = LOG_URL + course.cpi + "/" + dtoken;

        for (String r : rts) {
            p.put("rt", r);
            p.put("_t", ts());
            Http.Resp resp = http.get(url, p, headers);
            if (resp.code == 200) {
                JSONObject j = resp.json();
                return new Report(j != null && j.optBoolean("isPassed", false), 200);
            }
            if (resp.code == 403) continue;
            return new Report(false, resp.code == 0 ? 500 : resp.code);
        }
        return new Report(false, 500);
    }

    private String enc(Model.Course course, Model.Job job, double play, int dur) {
        String raw = "[" + course.clazzId + "][" + uid + "][" + job.jobid + "][" + job.objectid + "]["
                + ((int) play * 1000) + "][d_yHJ!$pdA~5][" + (dur * 1000) + "][0_" + dur + "]";
        return Crypt.md5(raw);
    }

    // ==================== 工具 ====================

    private String fid() {
        String f = http.cookie("fid");
        return f.isEmpty() ? "1024" : f;
    }

    private static String ts() {
        return String.valueOf(System.currentTimeMillis());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
