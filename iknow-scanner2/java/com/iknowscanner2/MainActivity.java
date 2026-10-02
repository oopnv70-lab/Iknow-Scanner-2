package com.iknowscanner2;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.*;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    // UI 控件已全部搬到 MainScreen（UI 独立化），此处只保留屏幕对象与进度状态。
    private MainScreen screen;
    private Scanner scanner;
    private SharedPreferences prefs;

    public static final String BASE_URL =
        "https://iknow.service.hihonor.com/weknow/servlet/download/public?contextNo=";

    // ==================== 硬性限流（不可绕过） ====================
    // 目标接口为 hihonor 官方固件下载服务。出于对服务端的负载保护以及避免触发
    // 服务方风控等合规考虑，客户端对请求速率做强制封顶。
    //
    // 该上限写死在代码中，属编译期常量，无法通过以下任一方式突破：
    //   1) 在设置页输入更小的「请求间隔」或更大的「并发请求数」
    //   2) 直接修改 SharedPreferences（settings.xml）中的 interval / concurrent
    //   3) 用 adb 或其他工具篡改应用私有数据
    // 因为真正的钳制发生在 scanRange() 读取设置之后、发起请求之前。
    //
    // 【改限流值时只改这一个常量】HARD_MIN_INTERVAL_MS 会自动换算，
    // 避免出现「改了一个数另一个没跟着变」导致设置无效的情况。
    // 每秒 2 次 -> 1000/2 = 500ms
    // 每秒 3 次 -> 1000/3 = 334ms（向上取整）
    public static final int HARD_MAX_REQUESTS_PER_SECOND = 2;
    // 最小请求间隔（毫秒），由 HARD_MAX_REQUESTS_PER_SECOND 自动换算。
    // 向上取整保证任意 1 秒滑动窗口内的请求数严格 <= 上限。
    public static final int HARD_MIN_INTERVAL_MS =
            (int) Math.ceil(1000.0 / HARD_MAX_REQUESTS_PER_SECOND);
    // 并发请求数上限。用户可在设置页填 1..HARD_MAX_CONCURRENT 之间的任意值，
    // 超出该上限才被钳回上限（不是一律钳成 1）。
    // 注意：并发本身不再承担限速职责 —— 真正的速率封顶由下面的全局令牌闸门统一保证，
    // 因此并发调大不会突破「每秒 HARD_MAX_REQUESTS_PER_SECOND 次」这条线。
    public static final int HARD_MAX_CONCURRENT = 3;

    // ==================== 界面文字常量（改文案只改这里） ====================
    // buildUI() 里原先直接写死的界面文案，集中到这里。
    // 目的：改文案时不必翻 168 行的 buildUI()，只改本区块即可。
    // 注意：值与改动前完全一致，仅做「字面量 -> 命名常量」的等价替换。
    public static final String TXT_APP_TITLE = "Iknow Scanner 2";
    public static final String TXT_LABEL_START = "起始编号";
    public static final String TXT_LABEL_END = "结束编号";
    public static final String TXT_HINT_START = "例如 1";
    public static final String TXT_HINT_END = "例如 100";
    public static final String TXT_BTN_START = "开始";
    public static final String TXT_BTN_RESUME = "续扫";
    public static final String TXT_BTN_STOP = "停止";
    public static final String TXT_BTN_CLEAR = "清空";
    public static final String TXT_BTN_SAVE_FORBIDDEN = "保存高维禁用";
    public static final String TXT_BTN_TEST_429 = "测试：下次请求强制 429";
    public static final String TXT_STATUS_READY = "就绪";
    public static final String TXT_HIT_PREFIX = "命中: ";
    public static final String TXT_RESULT_WAITING = "等待扫描...";

    // ==================== 429 风控熔断 ====================
    // 服务端在判定请求过于频繁时会返回 429。此时继续请求只会加重风控，
    // 因此一旦检测到 429：立即中止本次扫描 + 进入 10 分钟冷却期。
    public static final long COOLDOWN_MS = 10 * 60 * 1000L;  // 10 分钟
    public static final String PREF_COOLDOWN_UNTIL = "cooldown_until";
    // 保护 tripped429 的锁（静态，与静态字段配套）。
    private static final Object TRIP_LOCK = new Object();
    // 是否已检测到 429（本次运行内），用于让多线程并发时只记录一次。
    private static volatile boolean tripped429 = false;
    // 测试开关：置真后，下一次请求无论真实响应码是什么，一律按 429 处理。
    // 仅用于验证熔断逻辑，不影响正常扫描。一次生效后自动复位。
    private volatile boolean forceNext429 = false;

    // 检测 429 并触发熔断。多线程并发下可能被多个线程同时调用，
    // 用 synchronized + tripped429 保证只生效一次、冷却截止时间不被覆盖成更晚。
    // 返回剩余冷却毫秒数；<=0 表示不在冷却期。
    // ==================== 全局令牌闸门（唯一速率封顶点） ====================
    // 单线程与多线程共用同一把闸门：任何一次请求在发出前都必须先取得许可，
    // 相邻两次许可之间强制间隔 >= HARD_MIN_INTERVAL_MS。
    // 这样无论并发填几，整体速率恒 <= HARD_MAX_REQUESTS_PER_SECOND 次/秒。
    private static final Object RATE_LOCK = new Object();
    private static long nextPermitMs = 0L;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("iknow_scan2", Context.MODE_PRIVATE);
        buildUI();
    }

    private void buildUI() {
        screen = new MainScreen();
        scanner = new Scanner(this);
        scanner.setScreen(screen);
        int end = prefs.getInt("resume_end", -1);
        int next = prefs.getInt("resume_next", -1);
        screen.build(this, uiListener, next, end);
    }

    private final MainScreen.Listener uiListener = new MainScreen.Listener() {
        public void onStart(boolean resume) { scanner.start(resume); }
        public void onStop() { scanner.stop(); }
        public void onClear() { clearResult(); }
        public void onSettings(View anchor) { showSettingsMenu(anchor); }
        public void onTest429() {
            scanner.armTest429();
            scanner.append("\n[测试] 已武装：下一次请求将被强制判定为 429\n");
        }
        public void onSaveForbidden() { saveForbidden(); }
    };

    private void showSettingsMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "设置");
        popup.getMenu().add(0, 2, 1, "历史记录");
        popup.getMenu().add(0, 3, 2, "关于");
        
        popup.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                if (item.getItemId() == 1) {
                    // 跳转到设置页面
                    startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class));
                    return true;
                } else if (item.getItemId() == 2) {
                    // 跳转到历史记录页面
                    startActivity(new android.content.Intent(MainActivity.this, HistoryActivity.class));
                    return true;
                } else if (item.getItemId() == 3) {
                    android.widget.Toast.makeText(MainActivity.this, "Iknow Scanner v2.0", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }


    private void clearResult() {
        scanner.clear();
        screen.setResultText(TXT_RESULT_WAITING);
        screen.setProgressText(TXT_STATUS_READY);
        screen.setHitCount(TXT_HIT_PREFIX + "0");
        screen.setStartEnabled(true);
        screen.setResumeVisible(false);
        prefs.edit().remove("resume_next").remove("resume_end").apply();
    }

    private void saveForbidden() {
        String text = scanner.getResult();
        if (text.isEmpty()) {
            Toast.makeText(this, "没有扫描结果", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 筛选包含"高维禁用"的行
        StringBuilder forbidden = new StringBuilder();
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.contains("高维禁用") || line.contains("(High Level Repair Center is Forbidden)")) {
                forbidden.append(line).append("\n");
            }
        }
        
        if (forbidden.length() == 0) {
            Toast.makeText(this, "没有高维禁用记录", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 保存到用户可访问的私有目录（每次新建带时间戳的文件）
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) {
                Toast.makeText(this, "无法访问外部存储", Toast.LENGTH_SHORT).show();
                return;
            }
            // 生成带时间戳的文件名
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
            String timestamp = sdf.format(new java.util.Date());
            String filename = "forbidden_" + timestamp + ".txt";
            java.io.File file = new java.io.File(dir, filename);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(forbidden.toString().getBytes());
            fos.close();
            Toast.makeText(this, "已保存到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    // 查询四个分类文件中是否已存在指定 W 编号，存在则返回该行的型号+版本部分，否则返回 null
    // 统一的编号提取：兼容「W00012345  ...」裸格式和「编号 W00012345 型号 ...」带标签格式
    // 将 HTTP 状态码翻译成可读的错误原因
    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanner.stop();
    }
}
