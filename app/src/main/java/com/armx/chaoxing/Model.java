package com.armx.chaoxing;

import org.json.JSONObject;

/** 数据模型（纯 Java，无任何第三方依赖）。 */
public final class Model {

    private Model() {
    }

    public static final class Course {
        String courseId = "";
        String clazzId = "";
        String cpi = "";
        String title = "";
        String teacher = "";

        String key() {
            return courseId + "#" + clazzId;
        }
    }

    static final class Point {
        String id = "";
        String title = "";
        /** 章节名（chapter_unit 的标题）。curChapter 用它，避免拿小节标题去匹配题库。 */
        String chapter = "";
        int jobCount = 1;
        boolean finished;
        boolean locked;
    }

    static final class Job {
        String type = "";
        String jobid = "";
        String name = "";
        String title = "";
        String otherinfo = "";
        String mid = "";
        String objectid = "";
        String aid = "";
        String jtoken = "";
        String enc = "";
        String rt = "";
        String attDuration = "";
        String attDurationEnc = "";
        String faceEnc = "";
        String kid = "";
        JSONObject raw;
    }

    public static final class Account {
        String username = "";
        String password = "";
        String name = "";

        String title() {
            return name == null || name.trim().isEmpty() ? username : name.trim();
        }
    }

    /** 一个视频任务点的运行状态，UI 直接读。 */
    public static final class VTask {
        final String tid;
        volatile String name;
        volatile String status = "待开始";
        /** 失败/异常时的补充说明，UI 用小字显示在卡片里。 */
        volatile String detail = "";
        volatile double progress;
        volatile double play;
        volatile int duration;

        VTask(String tid, String name) {
            this.tid = tid;
            this.name = name == null ? "" : name;
        }

        boolean done() {
            return "完成".equals(status);
        }

        boolean failed() {
            return status != null && status.startsWith("失败");
        }
    }
}
