package com.iknowscanner2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Scanner {

    private final MainActivity a;
    private MainScreen screen;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile boolean running = false;
    private int curProgress = 0;
    private final java.util.concurrent.atomic.AtomicInteger hitCount =
        new java.util.concurrent.atomic.AtomicInteger(0);
    private final StringBuilder resultBuilder = new StringBuilder();

    private static final Object TRIP_LOCK = new Object();
    private static volatile boolean tripped429 = false;

    private static final Object RATE_LOCK = new Object();
    private static long nextPermitMs = 0L;

    public Scanner(MainActivity a) {
        this.a = a;
        this.prefs = a.getSharedPreferences("iknow_scan2", Context.MODE_PRIVATE);
    }

    public void setScreen(MainScreen s) { this.screen = s; }

    public void start(boolean resume) { startScan(resume); }
    public void stop() { stopScan(); }

    public void clear() {
        running = false;
        resultBuilder.setLength(0);
        hitCount.set(0);
        curProgress = 0;
    }

    public String getResult() { return resultBuilder.toString(); }

    public void append(String s) { appendResult(s); }


    private void tripIf429(int code) {
        if (code != 429) return;
        synchronized (TRIP_LOCK) {
            if (tripped429) return;
            tripped429 = true;
        }
        long until = System.currentTimeMillis() + MainActivity.COOLDOWN_MS;
        this.prefs.edit().putLong(MainActivity.PREF_COOLDOWN_UNTIL, until).apply();
        running = false;   // 立即停止：单线程 for 条件与多线程任务判断都会读到
        handler.post(new Runnable() {
            public void run() {
                appendResult("\n>>> 检测到 429（请求被限流），已强制停止 10 分钟 <<<\n");
                appendResult(">>> 冷却截止：" + new java.text.SimpleDateFormat(
                    "HH:mm:ss", Locale.US).format(new java.util.Date(until)) + "\n");
                appendResult(">>> 冷却期内无法开始/续扫，请稍后再试\n");
                curProgress = 0;
                screen.setProgressText("已熔断，冷却 10 分钟");
            }
        });
    }

    // 返回剩余冷却毫秒数；<=0 表示不在冷却期。
    private long cooldownRemainingMs() {
        long until = this.prefs.getLong(MainActivity.PREF_COOLDOWN_UNTIL, 0L);
        long left = until - System.currentTimeMillis();
        return left > 0 ? left : 0;
    }


    // ==================== 全局令牌闸门（唯一速率封顶点） ====================
    // 单线程与多线程共用同一把闸门：任何一次请求在发出前都必须先取得许可，
    // 相邻两次许可之间强制间隔 >= MainActivity.HARD_MIN_INTERVAL_MS。
    // 这样无论并发填几，整体速率恒 <= MainActivity.HARD_MAX_REQUESTS_PER_SECOND 次/秒。

    private static void acquireRatePermit() {
        synchronized (RATE_LOCK) {
            long now = System.currentTimeMillis();
            if (now < nextPermitMs) {
                try {
                    Thread.sleep(nextPermitMs - now);
                } catch (InterruptedException ignored) {}
            }
            nextPermitMs = System.currentTimeMillis() + MainActivity.HARD_MIN_INTERVAL_MS;
        }
    }

    private void startScan(boolean resume) {
        if (running) {
            Toast.makeText(a, "扫描中", Toast.LENGTH_SHORT).show();
            return;
        }

        // 429 熔断冷却检查：冷却期内一律拒绝启动（含续扫），
        // 冷却时间戳持久化在 prefs，杀进程重启也无法绕过。
        long left = cooldownRemainingMs();
        if (left > 0) {
            long sec = (left + 999) / 1000;
            Toast.makeText(a,
                "限流冷却中，还需 " + (sec / 60) + " 分 " + (sec % 60) + " 秒",
                Toast.LENGTH_LONG).show();
            appendResult(">>> 冷却中，无法开始（剩余 " + sec + " 秒）\n");
            return;
        }

        // 新一轮扫描，复位熔断标志（否则一次熔断后永久无法再被熔断）
        tripped429 = false;

        int s, e;
        if (resume) {
            // 续扫：从 this.prefs 读取下一个编号
            s = this.prefs.getInt("resume_next", -1);
            e = this.prefs.getInt("resume_end", -1);
            if (s < 0 || e < 0 || s > e) {
                Toast.makeText(a, "没有可续扫的任务", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // 新扫描：从编辑框读取
            String ss = this.screen.getStartText();
            String se = this.screen.getEndText();
            if (ss.length() == 0 || se.length() == 0) {
                Toast.makeText(a, "请输入范围", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                s = Integer.parseInt(ss);
                e = Integer.parseInt(se);
            } catch (Exception ex) {
                Toast.makeText(a, "数字格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (s > e) {
            Toast.makeText(a, "起始编号不能大于结束编号", Toast.LENGTH_SHORT).show();
            return;
        }

        running = true;
        this.screen.setStartEnabled(false);
        this.screen.setSaveForbiddenEnabled(false);
        this.screen.setResumeVisible(false);

        if (!resume) {
            this.prefs.edit().remove("resume_next").remove("resume_end").apply();
        }

        appendResult("\n--------------------------------------------------\n");
        appendResult((resume ? "[续扫] " : "[开始] ") + fmt(s) + " → " + fmt(e) + "\n");
        appendResult("--------------------------------------------------\n");

        final int fs = s, fe = e;
        new Thread(new Runnable() {
            public void run() { scanRange(fs, fe); }
        }).start();
    }

    private void stopScan() {
        running = false;
        this.screen.setStartEnabled(true);
        this.screen.setSaveForbiddenEnabled(true);
        int cur = getCur();
        int end = getEnd();
        if (cur > 0 && cur <= end) {
            this.prefs.edit().putInt("resume_next", cur).putInt("resume_end", end).apply();
            appendResult(">>> 已停止 @" + fmt(cur) + "，点续扫继续\n");
            this.screen.setResumeVisible(true);
        } else {
            appendResult(">>> 已停止\n");
        }
    }

    private int getCur() {
        return curProgress;
    }

    private int getEnd() {
        try {
            return Integer.parseInt(screen.getEndText());
        } catch (Exception e) {
            return 0;
        }
    }

    private void scanRange(int s, int e) {
        // 读取设置
        android.content.SharedPreferences settingsPrefs = a.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int interval = settingsPrefs.getInt("interval", 800);
        int concurrent = settingsPrefs.getInt("concurrent", 1);

        // ---- 硬性钳制（真正的速率封顶点，位于发起请求之前）----
        // 设置页的校验只是 UI 层便利，可被篡改 this.prefs 绕过；此处才是不可绕过的兜底。
        if (interval < MainActivity.HARD_MIN_INTERVAL_MS) {
            interval = MainActivity.HARD_MIN_INTERVAL_MS;
        }
        if (concurrent > MainActivity.HARD_MAX_CONCURRENT) {
            concurrent = MainActivity.HARD_MAX_CONCURRENT;
        }
        if (concurrent < 1) {
            concurrent = 1;
        }
        // 说明：单线程模式下每轮为 scanOne(n) + sleep(interval)，实际周期 = 网络耗时 + interval，
        // 恒 >= MainActivity.HARD_MIN_INTERVAL_MS，因此任意 1 秒窗口内请求数必然 <= MainActivity.HARD_MAX_REQUESTS_PER_SECOND。

        if (concurrent <= 1) {
            // 单线程模式：每轮先取令牌再发请求，速率由全局闸门保证。
            // 用户设置的 interval 只作为「更慢的下限」生效（不允许比闸门更快）。
            long userInterval = Math.max(interval, MainActivity.HARD_MIN_INTERVAL_MS);
            for (int n = s; n <= e && running; n++) {
                acquireRatePermit();
                if (!running) break;   // 取令牌期间可能已被 429 熔断，需再查一次
                scanOne(n);
                try {
                    if (userInterval > MainActivity.HARD_MIN_INTERVAL_MS) {
                        Thread.sleep(userInterval - MainActivity.HARD_MIN_INTERVAL_MS);
                    }
                } catch (Exception ignored) {}
            }
        } else {
            // 多线程并发模式：并发只决定「同时在途的请求数」，
            // 不再参与限速（原 sleep(interval/concurrent) 是假限速：只压住提交节奏，
            // 压不住实际发送，且并发越大该值越小、方向相反，已删除）。
            // 每个任务在发请求前统一走 acquireRatePermit()，整体速率恒 <= 上限。
            java.util.concurrent.ExecutorService executor = 
                java.util.concurrent.Executors.newFixedThreadPool(concurrent);
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(e - s + 1);
            
            for (int n = s; n <= e && running; n++) {
                final int num = n;
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (running) {
                                acquireRatePermit();
                                // 取令牌可能阻塞数百 ms，期间可能已被 429 熔断；
                                // 再查一次以尽量减少熔断后仍然发出的请求。
                                if (running) {
                                    scanOne(num);
                                }
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    }
                });
                
                // 不再在这里 sleep 控速：提交速度不影响发送速率，
                // 真正节流点在每个任务的 acquireRatePermit()。
            }
            
            // 等待所有任务完成
            try {
                latch.await();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            
            executor.shutdown();
        }
        
        handler.post(new Runnable() {
            public void run() {
                screen.setStartEnabled(true);
                screen.setSaveForbiddenEnabled(true);
                if (running) {
                    prefs.edit().remove("resume_next").remove("resume_end").apply();
                    screen.setResumeVisible(false);
                    appendResult("=== 完成 ===\n");
                    curProgress = 0;
                screen.setProgressText("完成");
                } else if (tripped429) {
                    // 因 429 熔断而中止：保存续扫点，便于冷却结束后继续，
                    // 且不显示"完成"（避免误以为扫完了）。
                    int cur = getCur();
                    int end = getEnd();
                    if (cur > 0 && cur <= end) {
                        prefs.edit().putInt("resume_next", cur).putInt("resume_end", end).apply();
                        screen.setResumeVisible(true);
                    }
                }
                // 结果已在 scanOne 中实时保存，无需再次保存
                running = false;
            }
        });
    }

    private synchronized void saveLineToFile(String line, String category) {
        try {
            java.io.File dir = a.getExternalFilesDir(null);
            if (dir == null) return;
            if (!dir.exists()) dir.mkdirs();
            
            String filename = FirmwareCategory.fileNameFor(category);
            
            java.io.File file = new java.io.File(dir, filename);
            
            // 提取当前行的 W 编号（兼容裸格式和带标签格式）
            String currentWNumber = extractWNumber(line);

            // 如果没有有效的 W 编号，直接保存
            if (currentWNumber.isEmpty()) {
                java.io.FileWriter writer = new java.io.FileWriter(file, true);
                writer.write(line + "\n");
                writer.close();
                return;
            }

            // 检查文件中是否已存在相同的 W 编号
            boolean exists = false;
            if (file.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                String existingLine;
                while ((existingLine = reader.readLine()) != null) {
                    if (existingLine.trim().isEmpty()) continue;

                    // 提取已有行的 W 编号
                    String existingWNumber = "";
                    String content = existingLine;

                    // 兼容旧的时间戳格式
                    if (content.startsWith("[")) {
                        int endBracket = content.indexOf("]");
                        if (endBracket > 0) {
                            content = content.substring(endBracket + 1).trim();
                        }
                    }

                    existingWNumber = extractWNumber(content);

                    // 如果找到相同的 W 编号，标记为已存在
                    if (!existingWNumber.isEmpty() && existingWNumber.equals(currentWNumber)) {
                        exists = true;
                        break;
                    }
                }
                reader.close();
            }
            
            // 如果不存在，才保存
            if (!exists) {
                java.io.FileWriter writer = new java.io.FileWriter(file, true);
                writer.write(line + "\n");
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveScanResult() {
        String text = resultBuilder.toString();
        android.util.Log.d("IknowScanner", "saveScanResult called, text length: " + text.length());
        if (text.isEmpty()) {
            android.util.Log.d("IknowScanner", "saveScanResult: text is empty");
            return;
        }
        try {
            java.io.File dir = a.getExternalFilesDir(null); // /sdcard/Android/data/com.iknowscanner2/files/
            if (dir == null) {
                android.util.Log.d("IknowScanner", "saveScanResult: dir is null");
                return;
            }
            android.util.Log.d("IknowScanner", "saveScanResult: dir path: " + dir.getAbsolutePath());
            // 确保目录存在
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // 定义四个分类文件
            java.io.File normalFile = new java.io.File(dir, "普通机型.txt");
            java.io.File forbiddenFile = new java.io.File(dir, "高维禁用.txt");
            java.io.File forbiddenEngFile = new java.io.File(dir, "高维禁用海外版.txt");
            java.io.File otherFile = new java.io.File(dir, "其他.txt");
            
            // 四个 FileWriter（追加模式）
            java.io.FileWriter normalWriter = new java.io.FileWriter(normalFile, true);
            java.io.FileWriter forbiddenWriter = new java.io.FileWriter(forbiddenFile, true);
            java.io.FileWriter forbiddenEngWriter = new java.io.FileWriter(forbiddenEngFile, true);
            java.io.FileWriter otherWriter = new java.io.FileWriter(otherFile, true);
            
            // 分割文本为行
            String[] lines = text.split("\n");
            android.util.Log.d("IknowScanner", "saveScanResult: lines count: " + lines.length);
            for (String line : lines) {
                // 跳过表头、分隔线、空行、开始/完成标记
                if (line.startsWith("W编号") || line.startsWith("---") || 
                    line.startsWith("===") || line.startsWith("[开始]") || 
                    line.startsWith(">>> 已停止") || line.trim().isEmpty()) {
                    continue;
                }
                
                // 分类
                String category = FirmwareCategory.categorize(line);
                android.util.Log.d("IknowScanner", "saveScanResult line: [" + line + "] category: " + category);
                switch (category) {
                    case "普通机型":
                        normalWriter.write(line + "\n");
                        break;
                    case "高维禁用":
                        forbiddenWriter.write(line + "\n");
                        break;
                    case "高维禁用海外版":
                        forbiddenEngWriter.write(line + "\n");
                        break;
                    default:
                        otherWriter.write(line + "\n");
                        break;
                }
            }
            
            // 关闭所有 Writer
            normalWriter.close();
            forbiddenWriter.close();
            forbiddenEngWriter.close();
            otherWriter.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            // 尝试写入错误日志
            try {
                java.io.File dir = a.getExternalFilesDir(null);
                if (dir != null) {
                    java.io.FileWriter errorWriter = new java.io.FileWriter(new java.io.File(dir, "error.log"), true);
                    errorWriter.write(new java.util.Date().toString() + ": " + e.getMessage() + "\n");
                    for (StackTraceElement element : e.getStackTrace()) {
                        errorWriter.write("\tat " + element.toString() + "\n");
                    }
                    errorWriter.close();
                }
            } catch (Exception ignored) {}
        }
    }

    private String categorizeLine(String line) {
        // 高维禁用（英文版优先判断，因为包含 "高维禁用海外版"）
        if (line.contains("高维禁用海外版")) {
            return "高维禁用海外版";
        }
        // 高维禁用（中文版）
        if (line.contains("高维禁用")) {
            return "高维禁用";
        }
        // 高维禁用（原始英文版，以防万一没被替换显示但分类需要识别）
        if (line.contains("(High Level Repair Center is Forbidden)")) {
            return "高维禁用海外版";
        }
        
        // 判断是否是普通手机机型
        // 普通机型特征：型号通常包含字母和数字的组合，如 "ROD2-W09S", "ALI-N21"
        // 排除其他固件，如 "DPTF", "WiFi", "Bluetooth" 等
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            String model = parts[1]; // 第二列是型号
            
            // 再次检查型号是否包含高维禁用关键词（防止漏判）
            if (model.contains("高维禁用")) {
                 if (model.contains("英文版")) return "高维禁用海外版";
                 return "高维禁用";
            }
            if (model.contains("(High Level Repair Center is Forbidden)")) {
                 return "高维禁用海外版";
            }

            // 如果型号包含特定关键词，认为是其他固件
            if (model.contains("DPTF") || model.contains("WiFi") || model.contains("Bluetooth") || 
                model.contains("Driver") || model.contains("Firmware") || model.length() < 3) {
                return "其他";
            }
            // 否则认为是普通机型
            return "普通机型";
        }
        
        return "其他";
    }

    private void scanOne(int num) {
        String cn = fmt(num);

        // 请求前先查本地是否已存在该编号，存在则跳过请求
        String existing = findExistingLine(cn);
        if (existing != null) {
            // 本地已存在该编号，跳过请求，显示「编号 已保存」
            appendResult(cn + "  已保存\n");
            updateProgress(num);
            return;
        }

        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(MainActivity.BASE_URL + cn).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            int code = c.getResponseCode();


            // 429 熔断：立即停止扫描并进入冷却期。放在写"错误 429"之前，
            // 避免熔断本身被当成普通错误记入「其他」分类文件。
            if (code == 429) {
                tripIf429(code);
                appendResult("编号 " + cn + "  错误 " + errorReason(code) + "（已触发熔断）\n");
                updateProgress(num);
                return;
            }

            String loc = c.getHeaderField("Location");
            String ohc = c.getHeaderField("ohc-file-size");
            String model = "";
            String ver = "";
            String fs = "";
            if (ohc != null && ohc.length() > 0) {
                fs = fs(ohc);
            }

            // 判断 HTTP 错误码（非重定向、非 2xx），显示错误原因并保存到「其他」
            boolean redirect = (code == 301 || code == 302 || code == 303 || code == 307 || code == 308);
            boolean success = (code >= 200 && code < 300);
            if (!redirect && !success) {
                String reason = errorReason(code);
                appendResult("编号 " + cn + "  错误 " + reason + "\n\n");
                // 保存到「其他」分类
                saveLineToFile("编号 " + cn + "  错误 " + reason, FirmwareCategory.CAT_OTHER);
                updateProgress(num);
                return;
            }

            boolean found = false;
            if (redirect && loc != null && loc.length() > 0) {
                String json = decodeLocationJson(loc);
                String fn = extractJsonString(json, "fileName");
                if (fn.length() > 0) {
                    found = true;
                    model = extractModelFromFileName(fn);
                    ver = extractVersionFromFileName(fn);
                }
            }

            String line = String.format("%-12s %-14s %-26s %s", cn, model, ver, fs);
            // 显示时带上汉字标识，存储仍用裸格式（line 用于去重/保存）
            String display = "编号 " + cn + "  型号 " + model + "  系统版本 " + ver + "  大小 " + fs;
            appendResult(display + "\n\n");
            if (found) {
                hitCount.incrementAndGet();
                // 实时分类并保存到文件
                String category = FirmwareCategory.categorize(line);
                saveLineToFile(line, category);
            }
            updateProgress(num);

        } catch (IOException ex) {
            // 网络错误：显示原因并保存到「其他」
            appendResult("编号 " + cn + "  错误 网络错误(" + ex.getClass().getSimpleName() + ")\n\n");
            saveLineToFile("编号 " + cn + "  错误 网络错误", FirmwareCategory.CAT_OTHER);
            updateProgress(num);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String findExistingLine(String cn) {
        try {
            java.io.File dir = a.getExternalFilesDir(null);
            if (dir == null || !dir.exists()) return null;

            String[] filenames = FirmwareCategory.FILES;
            for (String filename : filenames) {
                java.io.File file = new java.io.File(dir, filename);
                if (!file.exists()) continue;

                java.io.BufferedReader reader = null;
                try {
                    reader = new java.io.BufferedReader(new java.io.FileReader(file));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        String content = line;
                        // 兼容旧时间戳前缀 "[...]"
                        if (content.startsWith("[")) {
                            int eb = content.indexOf("]");
                            if (eb > 0) content = content.substring(eb + 1).trim();
                        }
                        // 用统一提取方法匹配编号（兼容裸格式和带标签格式）
                        String w = extractWNumber(content);
                        if (!w.isEmpty() && w.equals(cn)) {
                            // 命中：返回该行去掉编号后的内容
                            return content;
                        }
                    }
                } finally {
                    if (reader != null) reader.close();
                }
            }
        } catch (Exception e) {
            // 查重失败时忽略，按不存在处理（继续正常请求）
        }
        return null;
    }

    // 统一的编号提取：兼容「W00012345  ...」裸格式和「编号 W00012345 型号 ...」带标签格式
    private String extractWNumber(String line) {
        if (line == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("W000\\d{5}").matcher(line);
        if (m.find()) return m.group();
        return "";
    }

    // 将 HTTP 状态码翻译成可读的错误原因
    private String errorReason(int code) {
        switch (code) {
            case 400: return "400 请求错误";
            case 401: return "401 未授权";
            case 402: return "402 需要付费";
            case 403: return "403 禁止访问";
            case 404: return "404 未找到";
            case 405: return "405 方法不允许";
            case 408: return "408 请求超时";
            case 429: return "429 请求过多(被限流)";
            case 500: return "500 服务器内部错误";
            case 502: return "502 网关错误";
            case 503: return "503 服务不可用";
            case 504: return "504 网关超时";
            default:
                if (code >= 500) return code + " 服务器错误";
                if (code >= 400) return code + " 客户端错误";
                return code + " 未知错误";
        }
    }

    private void updateProgress(final int cur) {
        handler.post(new Runnable() {
            public void run() {
                curProgress = cur;
                screen.setProgressText(fmt(cur));
                screen.setHitCount(MainActivity.TXT_HIT_PREFIX + hitCount.get());
            }
        });
    }

    private void appendResult(final String s) {
        handler.post(new Runnable() {
            public void run() {
                resultBuilder.append(s);
                String[] lines = resultBuilder.toString().split("\n");
                if (lines.length > 2000) {
                    StringBuilder t = new StringBuilder();
                    for (int i = lines.length - 2000; i < lines.length; i++) {
                        t.append(lines[i]).append("\n");
                    }
                    resultBuilder.setLength(0);
                    resultBuilder.append(t);
                }
                screen.appendAndScroll(resultBuilder.toString());
            }
        });
    }

    private String extractFilename(String cd) {
        Pattern p = Pattern.compile("filename[*]?\\\\s*=\\\\s*(?:UTF-8'')?\\\"?([^\\\";]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(cd);
        if (m.find()) {
            try { return java.net.URLDecoder.decode(m.group(1).trim(), "UTF-8"); } catch (Exception ignored) {}
        }
        return cd;
    }

    private String decodeLocationJson(String loc) {
        try {
            String last = loc.substring(loc.lastIndexOf('/') + 1);
            if (last.endsWith(".zip")) last = last.substring(0, last.length() - 4);
            last = java.net.URLDecoder.decode(last, "UTF-8");

            int pad = (4 - (last.length() % 4)) % 4;
            StringBuilder sb = new StringBuilder(last);
            for (int i = 0; i < pad; i++) sb.append('=');

            byte[] raw = Base64.decode(sb.toString(), Base64.URL_SAFE);
            GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(raw));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = gis.read(buf)) > 0) bos.write(buf, 0, n);
            gis.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String extractJsonString(String json, String key) {
        try {
            Pattern p = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"((?:\\\\\\\\.|[^\\\"])*)\\\"");
            Matcher m = p.matcher(json);
            if (m.find()) {
                String v = m.group(1);
                v = v.replace("\\\\\"", "\"")
                     .replace("\\\\/", "/")
                     .replace("\\\\n", "\n")
                     .replace("\\\\r", "\r")
                     .replace("\\\\t", "\t")
                     .replace("\\\\\\\\", "\\");
                return v;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractModelFromFileName(String fn) {
        String f = fn == null ? "" : fn.trim();
        if (f.endsWith(".zip")) f = f.substring(0, f.length() - 4);
        // 把英文前缀替换为中文提示
        if (f.startsWith("(High Level Repair Center is Forbidden)_")) {
            f = "(高维禁用海外版)_" + f.substring("(High Level Repair Center is Forbidden)_".length());
        }
        // 保留前缀，找到第一个空格前的内容作为型号（包含前缀）
        int sp = f.indexOf(' ');
        if (sp > 0) {
            return f.substring(0, sp).trim();
        }
        return f;
}

    private String extractVersionFromFileName(String fn) {
        String f = fn == null ? "" : fn.trim();
        if (f.endsWith(".zip")) f = f.substring(0, f.length() - 4);
        // 先把英文前缀统一替换为中文提示，再按中文前缀处理
        if (f.startsWith("(High Level Repair Center is Forbidden)_")) {
            f = "(高维禁用海外版)_" + f.substring("(High Level Repair Center is Forbidden)_".length());
        }
        if (f.startsWith("(高维禁用海外版)_")) {
            f = f.substring("(高维禁用海外版)_".length());
        } else if (f.startsWith("高维禁用_")) {
            f = f.substring("高维禁用_".length());
        } else if (f.startsWith("高维禁用")) {
            f = f.substring("高维禁用".length());
            if (f.startsWith("_") || f.startsWith(" ")) f = f.substring(1);
        }
        int sp = f.indexOf(' ');
        if (sp >= 0 && sp + 1 < f.length()) {
            String rest = f.substring(sp + 1).trim();
            int end = rest.length();
            int fw = rest.indexOf("_Firmware");
            int under = rest.indexOf('_');
            if (fw >= 0) end = Math.min(end, fw);
            else if (under >= 0) end = Math.min(end, under);
            String ver = rest.substring(0, end).trim();
            if (ver.length() > 0) return ver;
        }

        return f.length() > 0 ? f : "";
    }

    private String fs(String s) {
        try {
            long b = Long.parseLong(s);
            if (b >= 1073741824L) return String.format(Locale.getDefault(), "%.1fG", b / 1073741824.0);
            if (b >= 1048576L) return String.format(Locale.getDefault(), "%.1fM", b / 1048576.0);
            if (b >= 1024L) return String.format(Locale.getDefault(), "%.1fK", b / 1024.0);
            return b + "B";
        } catch (Exception e) {
            return s;
        }
    }

    private String mo(String m) {
        if (m.startsWith("Jan")) return "01";
        if (m.startsWith("Feb")) return "02";
        if (m.startsWith("Mar")) return "03";
        if (m.startsWith("Apr")) return "04";
        if (m.startsWith("May")) return "05";
        if (m.startsWith("Jun")) return "06";
        if (m.startsWith("Jul")) return "07";
        if (m.startsWith("Aug")) return "08";
        if (m.startsWith("Sep")) return "09";
        if (m.startsWith("Oct")) return "10";
        if (m.startsWith("Nov")) return "11";
        if (m.startsWith("Dec")) return "12";
        return m;
    }

    private String fmt(int n) {
        return "W000" + String.format("%05d", n);
    }

}
