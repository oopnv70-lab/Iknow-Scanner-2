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
    private EditText editStart, editEnd;
    private Button btnStart, btnStop, btnClear, btnResume, btnSaveForbidden;
    private TextView textProgress, textHitCount, textResult;
    private ScrollView resultScroll;
    private volatile boolean running = false;
    private java.util.concurrent.atomic.AtomicInteger hitCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private final StringBuilder resultBuilder = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private static final String BASE_URL =
        "https://iknow.service.hihonor.com/weknow/servlet/download/public?contextNo=";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("iknow_scan2", Context.MODE_PRIVATE);
        buildUI();
    }

    private void buildUI() {
        FrameLayout screen = new FrameLayout(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 0, 24, 16);

        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        rootLp.setMargins(0, 220, 0, 0);

        TextView title = new TextView(this);
        title.setText("Iknow Scanner 2");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, 12);
        root.addView(title);

        TextView lab1 = new TextView(this);
        lab1.setText("起始编号");
        lab1.setTextSize(13);
        root.addView(lab1);

        editStart = new EditText(this);
        editStart.setHint("例如 1");
        editStart.setSingleLine(true);
        editStart.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(editStart, mpwc());

        TextView lab2 = new TextView(this);
        lab2.setText("结束编号");
        lab2.setTextSize(13);
        lab2.setPadding(0, 8, 0, 0);
        root.addView(lab2);

        editEnd = new EditText(this);
        editEnd.setHint("例如 100");
        editEnd.setSingleLine(true);
        editEnd.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(editEnd, mpwc());

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 12, 0, 0);

        btnStart = new Button(this);
        btnStart.setText("开始");
        btnRow.addView(btnStart, weight());

        btnResume = new Button(this);
        btnResume.setText("续扫");
        btnResume.setVisibility(View.GONE);
        btnRow.addView(btnResume, weight());

        btnSaveForbidden = new Button(this);
        btnSaveForbidden.setText("保存高维禁用");
        btnSaveForbidden.setEnabled(false);
        btnRow.addView(btnSaveForbidden, weight());

        btnStop = new Button(this);
        btnStop.setText("停止");
        btnRow.addView(btnStop, weight());

        btnClear = new Button(this);
        btnClear.setText("清空");
        btnRow.addView(btnClear, weight());

        root.addView(btnRow, mpwc());

        LinearLayout prog = new LinearLayout(this);
        prog.setOrientation(LinearLayout.HORIZONTAL);
        prog.setPadding(0, 10, 0, 8);

        textProgress = new TextView(this);
        textProgress.setText("就绪");
        textProgress.setTextSize(13);
        prog.addView(textProgress, weight());

        textHitCount = new TextView(this);
        textHitCount.setText("命中: 0");
        textHitCount.setTextSize(13);
        textHitCount.setTextColor(0xFFE65100);
        prog.addView(textHitCount);

        root.addView(prog, mpwc());

        View line = new View(this);
        line.setBackgroundColor(0xFFCCCCCC);
        root.addView(line, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));

        resultScroll = new ScrollView(this);
        textResult = new TextView(this);
        textResult.setText("等待扫描...");
        textResult.setTextSize(10);
        textResult.setTypeface(Typeface.MONOSPACE);
        textResult.setPadding(0, 8, 0, 8);
        resultScroll.addView(textResult);
        root.addView(resultScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        screen.addView(root, rootLp);

        // 添加右上角三个点菜单
        ImageButton menuBtn = new ImageButton(this);
        menuBtn.setImageResource(android.R.drawable.ic_menu_more); // 系统图标
        menuBtn.setBackgroundColor(0x00000000); // 透明背景
        menuBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        
        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END | Gravity.TOP);
        menuLp.setMargins(0, 80, 16, 0); // 往下移
        screen.addView(menuBtn, menuLp);

        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu(v);
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startScan(false); }
        });
        btnResume.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startScan(true); }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { stopScan(); }
        });
        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { clearResult(); }
        });
        btnSaveForbidden.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveForbidden(); }
        });

        int end = prefs.getInt("resume_end", -1);
        int next = prefs.getInt("resume_next", -1);
        if (end >= 0 && next >= 0 && next <= end) {
            editStart.setText(String.valueOf(next));
            editEnd.setText(String.valueOf(end));
            btnResume.setVisibility(View.VISIBLE);
        }
        setContentView(screen);
    }

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

    private LinearLayout.LayoutParams mpwc() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private void startScan(boolean resume) {
        if (running) {
            Toast.makeText(this, "扫描中", Toast.LENGTH_SHORT).show();
            return;
        }
        int s, e;
        if (resume) {
            // 续扫：从 prefs 读取下一个编号
            s = prefs.getInt("resume_next", -1);
            e = prefs.getInt("resume_end", -1);
            if (s < 0 || e < 0 || s > e) {
                Toast.makeText(this, "没有可续扫的任务", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // 新扫描：从编辑框读取
            String ss = editStart.getText().toString().trim();
            String se = editEnd.getText().toString().trim();
            if (ss.length() == 0 || se.length() == 0) {
                Toast.makeText(this, "请输入范围", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                s = Integer.parseInt(ss);
                e = Integer.parseInt(se);
            } catch (Exception ex) {
                Toast.makeText(this, "数字格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (s > e) {
            Toast.makeText(this, "起始编号不能大于结束编号", Toast.LENGTH_SHORT).show();
            return;
        }

        running = true;
        btnStart.setEnabled(false);
        btnSaveForbidden.setEnabled(false);
        btnResume.setVisibility(View.GONE);

        if (!resume) {
            prefs.edit().remove("resume_next").remove("resume_end").apply();
        }

        appendResult("\n--------------------------------------------------\n");
        appendResult((resume ? "[续扫] " : "[开始] ") + fmt(s) + " → " + fmt(e) + "\n");
        appendResult(String.format("%-12s %-14s %-26s %s", "W编号", "型号", "版本", "大小") + "\n");
        appendResult("--------------------------------------------------\n");

        final int fs = s, fe = e;
        new Thread(new Runnable() {
            public void run() { scanRange(fs, fe); }
        }).start();
    }

    private void stopScan() {
        running = false;
        btnStart.setEnabled(true);
        btnSaveForbidden.setEnabled(true);
        int cur = getCur();
        int end = getEnd();
        if (cur > 0 && cur <= end) {
            prefs.edit().putInt("resume_next", cur).putInt("resume_end", end).apply();
            appendResult(">>> 已停止 @" + fmt(cur) + "，点续扫继续\n");
            btnResume.setVisibility(View.VISIBLE);
        } else {
            appendResult(">>> 已停止\n");
        }
    }

    private void clearResult() {
        running = false;
        resultBuilder.setLength(0);
        hitCount.set(0);
        textResult.setText("等待扫描...");
        textProgress.setText("就绪");
        textHitCount.setText("命中: 0");
        btnStart.setEnabled(true);
        btnResume.setVisibility(View.GONE);
        prefs.edit().remove("resume_next").remove("resume_end").apply();
    }

    private void saveForbidden() {
        String text = resultBuilder.toString();
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

    private int getCur() {
        String p = textProgress.getText().toString();
        if (p.startsWith("W000")) {
            try {
                String n = p.substring(4);
                if (n.contains(" ")) n = n.substring(0, n.indexOf(" "));
                return Integer.parseInt(n);
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private int getEnd() {
        try {
            return Integer.parseInt(editEnd.getText().toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void scanRange(int s, int e) {
        // 读取设置
        android.content.SharedPreferences settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE);
        int interval = settingsPrefs.getInt("interval", 800);
        int concurrent = settingsPrefs.getInt("concurrent", 1);
        
        if (concurrent <= 1) {
            // 单线程模式（原来的逻辑）
            for (int n = s; n <= e && running; n++) {
                scanOne(n);
                try { 
                    Thread.sleep(interval); 
                } catch (Exception ignored) {}
            }
        } else {
            // 多线程并发模式
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
                                scanOne(num);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    }
                });
                
                // 控制提交速度，避免一次性提交太多任务
                try { 
                    Thread.sleep(interval / concurrent); 
                } catch (Exception ignored) {}
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
                btnStart.setEnabled(true);
                btnSaveForbidden.setEnabled(true);
                if (running) {
                    prefs.edit().remove("resume_next").remove("resume_end").apply();
                    btnResume.setVisibility(View.GONE);
                    appendResult("=== 完成 ===\n");
                    textProgress.setText("完成");
                }
                // 结果已在 scanOne 中实时保存，无需再次保存
                running = false;
            }
        });
    }
    private synchronized void saveLineToFile(String line, String category) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) return;
            if (!dir.exists()) dir.mkdirs();
            
            String filename;
            switch (category) {
                case "普通机型":
                    filename = "普通机型.txt";
                    break;
                case "高维禁用":
                    filename = "高维禁用.txt";
                    break;
                case "高维禁用海外版":
                    filename = "高维禁用海外版.txt";
                    break;
                default:
                    filename = "其他.txt";
                    break;
            }
            
            java.io.File file = new java.io.File(dir, filename);
            
            // 提取当前行的 W 编号
            String currentWNumber = "";
            if (line.startsWith("W000")) {
                int spaceIndex = line.indexOf(" ");
                if (spaceIndex > 0) {
                    currentWNumber = line.substring(0, spaceIndex);
                } else {
                    currentWNumber = line;
                }
            }
            
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
                    
                    if (content.startsWith("W000")) {
                        int spaceIndex = content.indexOf(" ");
                        if (spaceIndex > 0) {
                            existingWNumber = content.substring(0, spaceIndex);
                        } else {
                            existingWNumber = content;
                        }
                    }
                    
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
            java.io.File dir = getExternalFilesDir(null); // /sdcard/Android/data/com.iknowscanner2/files/
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
                String category = categorizeLine(line);
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
                java.io.File dir = getExternalFilesDir(null);
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
            // 直接显示本地已存内容并标注「已存在」，不发请求
            appendResult(cn + "  " + existing.trim() + "  [已存在]\n\n");
            updateProgress(num);
            return;
        }

        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BASE_URL + cn).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);

            int code = c.getResponseCode();
            String loc = c.getHeaderField("Location");
            String ohc = c.getHeaderField("ohc-file-size");

            String model = "";
            String ver = "";
            String fs = "";

            if (ohc != null && ohc.length() > 0) {
                fs = fs(ohc);
            }

            boolean redirect = (code == 301 || code == 302 || code == 303 || code == 307 || code == 308);
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
            appendResult(line + "\n\n");
            if (found) {
                hitCount.incrementAndGet();
                // 实时分类并保存到文件
                String category = categorizeLine(line);
                saveLineToFile(line, category);
            }
            updateProgress(num);

        } catch (IOException ex) {
            appendResult(String.format("%-12s %-14s %-26s %s", cn, "", "", "") + "\n\n");
            updateProgress(num);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // 查询四个分类文件中是否已存在指定 W 编号，存在则返回该行的型号+版本部分，否则返回 null
    private String findExistingLine(String cn) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null || !dir.exists()) return null;

            String[] filenames = {"普通机型.txt", "高维禁用.txt", "高维禁用海外版.txt", "其他.txt"};
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
                        if (content.startsWith(cn)) {
                            // 命中：去掉编号本身，返回后面型号+版本部分
                            String rest = content.substring(cn.length()).trim();
                            return rest;
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

    private void updateProgress(final int cur) {
        handler.post(new Runnable() {
            public void run() {
                textProgress.setText(fmt(cur));
                textHitCount.setText("命中: " + hitCount.get());
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
                textResult.setText(resultBuilder.toString());
                resultScroll.post(new Runnable() {
                    public void run() { resultScroll.fullScroll(View.FOCUS_DOWN); }
                });
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
