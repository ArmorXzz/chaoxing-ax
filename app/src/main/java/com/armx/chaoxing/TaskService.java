package com.armx.chaoxing;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * 只在真正刷课时才进入前台；通知里列出每个视频的读秒进度。
 * 用户关闭了通知或系统不允许时自动退回普通通知。
 */
public final class TaskService extends Service {

    static final String ACTION_STOP = "com.armx.chaoxing.STOP";
    static final String ACTION_REFRESH = "com.armx.chaoxing.REFRESH";
    private static final String CHANNEL = "cx_progress";
    private static final int NOTIF_ID = 1001;
    private static final int MAX_ROWS = 5;

    private volatile boolean alive = true;
    private boolean foreground;
    private NotificationManager nm;
    private PowerManager.WakeLock lock;

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "刷课进度",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("刷课时显示每个视频的进度，未运行时不显示");
        ch.setShowBadge(false);
        ch.enableVibration(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);

        Thread t = new Thread(this::loop, "cx-notif");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        Engine engine = Engine.get(this);
        long idleSince = 0L;
        while (alive) {
            try {
                boolean running = engine.isRunning();
                int[] s = engine.summary();          // {total, done, failed}
                List<Model.VTask> tasks = engine.snapshot();
                if (running) {
                    Notification n = build(tasks, s[1], s[0], s[2]);
                    if (!foreground) {
                        if (Build.VERSION.SDK_INT >= 29) {
                            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                        } else {
                            startForeground(NOTIF_ID, n);
                        }
                        foreground = true;
                        acquire();
                    } else {
                        nm.notify(NOTIF_ID, n);
                    }
                } else {
                    if (foreground) {
                        foreground = false;
                        stopForeground(STOP_FOREGROUND_REMOVE);
                        nm.cancel(NOTIF_ID);
                        release();
                    }
                    // 长时间没有任务就把自己停掉，避免常驻一个 1Hz 的轮询线程
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (idleSince == 0L) {
                        idleSince = now;
                    } else if (now - idleSince > 8000L) {
                        stopSelf();
                        return;
                    }
                }
                if (running) idleSince = 0L;
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable t) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }
        if (foreground) {
            foreground = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        release();
    }

    /** 正在播的排前面，最多显示 5 条。 */
    private List<Model.VTask> pick(List<Model.VTask> all) {
        List<Model.VTask> playing = new ArrayList<>();
        List<Model.VTask> other = new ArrayList<>();
        for (Model.VTask v : all) {
            if (v.done() || v.failed()) continue;
            if ("播放中".equals(v.status) || "结算中".equals(v.status)) playing.add(v);
            else other.add(v);
        }
        List<Model.VTask> out = new ArrayList<>(playing);
        out.addAll(other);
        return out.size() > MAX_ROWS ? out.subList(0, MAX_ROWS) : out;
    }

    private Notification build(List<Model.VTask> all, int done, int total, int failed) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, TaskService.class).setAction(ACTION_STOP);
        PendingIntent sp = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notif_video);
        String sub = "已完成 " + done + "/" + total + (failed > 0 ? " · 失败 " + failed : "");
        rv.setTextViewText(R.id.notif_title, sub);

        int[] ids = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5};
        int[] names = {R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5};
        int[] times = {R.id.time1, R.id.time2, R.id.time3, R.id.time4, R.id.time5};
        int[] bars = {R.id.bar1, R.id.bar2, R.id.bar3, R.id.bar4, R.id.bar5};

        List<Model.VTask> show = pick(all);
        for (int i = 0; i < MAX_ROWS; i++) {
            if (i < show.size()) {
                Model.VTask v = show.get(i);
                rv.setViewVisibility(ids[i], android.view.View.VISIBLE);
                rv.setTextViewText(names[i], v.name == null ? "视频" : v.name);
                rv.setTextViewText(times[i], clock((int) v.play) + " / " + clock(v.duration));
                int pct = (int) Math.round(Math.max(0, Math.min(1, v.progress)) * 1000);
                rv.setProgressBar(bars[i], 1000, pct, false);
            } else {
                rv.setViewVisibility(ids[i], android.view.View.GONE);
            }
        }

        Notification.Action act = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat_cx), "停止任务", sp).build();

        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_cx)
                .setContentTitle("正在刷课")
                .setContentText(sub)
                .setCustomContentView(rv)
                .setCustomBigContentView(rv)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .addAction(act);
        return b.build();
    }

    private static String clock(int sec) {
        int m = Math.max(0, sec) / 60;
        int s = Math.max(0, sec) % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    private void acquire() {
        try {
            if (lock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CXTool::task");
                lock.setReferenceCounted(false);
            }
            if (!lock.isHeld()) lock.acquire(6 * 60 * 60 * 1000L);
        } catch (Throwable ignored) {
        }
    }

    private void release() {
        try {
            if (lock != null && lock.isHeld()) lock.release();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Engine.get(this).stop();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        alive = false;
        release();
        super.onDestroy();
    }
}
