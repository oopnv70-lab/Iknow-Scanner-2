package com.iknowscanner2;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.util.*;

public class SettingsActivity extends Activity {
    private static final int REQUEST_IMPORT_FILE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setBackgroundColor(Color.WHITE);
        
        // 标题栏
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        
        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_revert);
        backBtn.setBackgroundColor(0x00000000);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        titleBar.addView(backBtn, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(24, 0, 0, 0);
        titleBar.addView(title);
        
        root.addView(titleBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 设置项：请求间隔
        TextView labelInterval = new TextView(this);
        labelInterval.setText("请求间隔 (ms)：");
        labelInterval.setTextSize(16);
        labelInterval.setPadding(0, 32, 0, 8);
        root.addView(labelInterval);
        
        EditText editInterval = new EditText(this);
        editInterval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        editInterval.setHint("建议 2000-6000");
        root.addView(editInterval, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 设置项：并发请求数
        TextView labelConcurrent = new TextView(this);
        labelConcurrent.setText("并发请求数：");
        labelConcurrent.setTextSize(16);
        labelConcurrent.setPadding(0, 32, 0, 8);
        root.addView(labelConcurrent);
        
        EditText editConcurrent = new EditText(this);
        editConcurrent.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        editConcurrent.setHint("建议 1-5");
        root.addView(editConcurrent, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 加载已有设置
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int interval = prefs.getInt("interval", 800);
        int concurrent = prefs.getInt("concurrent", 1);
        editInterval.setText(String.valueOf(interval));
        editConcurrent.setText(String.valueOf(concurrent));
        
        // 导入日志按钮
        Button btnImport = new Button(this);
        btnImport.setText("导入日志");
        btnImport.setPadding(0, 32, 0, 32);
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFilePicker();
            }
        });
        root.addView(btnImport, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        // 整理历史记录按钮
        Button btnOrganize = new Button(this);
        btnOrganize.setText("整理历史记录");
        btnOrganize.setPadding(0, 32, 0, 32);
        btnOrganize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                organizeHistory();
            }
        });
        root.addView(btnOrganize, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 保存按钮
        Button btnSave = new Button(this);
        btnSave.setText("保存设置");
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int interval = Integer.parseInt(editInterval.getText().toString());
                    int concurrent = Integer.parseInt(editConcurrent.getText().toString());
                    
                    if (interval < 100) interval = 100;
                    if (concurrent < 1) concurrent = 1;
                    if (concurrent > 10) concurrent = 10;
                    
                    android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
                    prefs.edit()
                        .putInt("interval", interval)
                        .putInt("concurrent", concurrent)
                        .apply();
                    
                    Toast.makeText(SettingsActivity.this, "设置已保存", Toast.LENGTH_SHORT).show();
                    finish();
                } catch (NumberFormatException e) {
                    Toast.makeText(SettingsActivity.this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnSave, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        setContentView(root);
    }
    
    private void organizeHistory() {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null || !dir.exists()) {
                Toast.makeText(this, "没有找到历史记录目录", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String[] filenames = {"普通机型.txt", "高维禁用.txt", "高维禁用海外版.txt", "其他.txt"};
            int totalCount = 0;
            
            for (String filename : filenames) {
                java.io.File file = new java.io.File(dir, filename);
                if (!file.exists()) continue;
                
                // 读取所有行
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                java.util.List<String> lines = new java.util.ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        lines.add(line);
                    }
                }
                reader.close();
                
                // 提取 W 编号并排序去重
                java.util.Map<String, String> wMap = new java.util.TreeMap<>();
                java.util.List<String> nonWLines = new java.util.ArrayList<>();
                
                for (String l : lines) {
                    String content = l;
                    // 移除时间戳
                    if (content.startsWith("[")) {
                        int endBracket = content.indexOf("]");
                        if (endBracket > 0) {
                            content = content.substring(endBracket + 1).trim();
                        }
                    }
                    
                    String wNumber = extractWNumber(content);
                    
                    if (!wNumber.isEmpty()) {
                        // 如果 W 编号已存在，保留第一个（不覆盖）
                        if (!wMap.containsKey(wNumber)) {
                            wMap.put(wNumber, l);
                        }
                    } else {
                        nonWLines.add(l);
                    }
                }
                
                // 合并并写回文件
                java.io.FileWriter writer = new java.io.FileWriter(file, false);
                for (String nonW : nonWLines) {
                    writer.write(nonW + "\n");
                }
                for (String wLine : wMap.values()) {
                    writer.write(wLine + "\n");
                }
                writer.close();
                totalCount += wMap.size() + nonWLines.size();
            }
            
            Toast.makeText(this, "整理完成，共保留 " + totalCount + " 条记录", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "整理失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==== 日志导入功能 ====

    // 打开系统文件选择器，让用户选择本地 txt 文件
    private void openFilePicker() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");   // 文本文件，含 .txt / .log / .csv 等
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
            new String[]{"text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            final android.net.Uri uri = data.getData();
            if (uri == null) {
                Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "正在导入…", Toast.LENGTH_SHORT).show();
            // 后台线程执行读取+解析+写入，避免主线程阻塞导致 ANR/黑屏
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String content = readTextFromUri(uri);
                    if (content == null || content.trim().isEmpty()) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(SettingsActivity.this, "文件为空或读取失败", Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                    final String summary = importLog(content);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(SettingsActivity.this, summary, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }).start();
        }
    }

    // 从 Uri 读取文本内容（兼容各种文件提供方）
    private String readTextFromUri(android.net.Uri uri) {
        java.io.InputStream is = null;
        java.io.BufferedReader reader = null;
        try {
            is = getContentResolver().openInputStream(uri);
            if (is == null) return null;

            // 支持 UTF-8 和 GBK 两种编码（日志文件可能含中文，GBK 常见）
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            byte[] raw = bos.toByteArray();

            String content;
            try {
                content = new String(raw, "UTF-8");
                // 简单检测乱码：若含替换符，尝试 GBK
                if (content.contains("\uFFFD")) {
                    content = new String(raw, "GBK");
                }
            } catch (Exception e) {
                content = new String(raw, "GBK");
            }
            return content;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
        }
    }

    // 导入日志：解析并分类入库，返回结果摘要字符串
    private String importLog(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "请先选择日志文件";
        }

        String[] rawLines = text.split("\\r?\\n");
        int total = 0;      // 识别到的总条数
        int added = 0;      // 去重后新增条数

        // 一次性把四个分类文件里已存在的 W 编号读进内存，避免每行都重复打开文件（消除 O(n²)）
        java.util.Map<String, java.util.Set<String>> existingByFile =
            new java.util.HashMap<>();
        java.io.File dir = getExternalFilesDir(null);
        if (dir != null && dir.exists()) {
            String[] filenames = {"普通机型.txt", "高维禁用.txt", "高维禁用海外版.txt", "其他.txt"};
            for (String fn : filenames) {
                java.util.Set<String> set = new java.util.HashSet<>();
                java.io.File f = new java.io.File(dir, fn);
                if (f.exists()) {
                    try {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(f));
                        String l;
                        while ((l = reader.readLine()) != null) {
                            set.add(extractWNumber(l));
                        }
                        reader.close();
                    } catch (Exception ignored) {}
                }
                existingByFile.put(fn, set);
            }
        }

        // 预创建四个文件句柄，批量追加写入（避免每行 open/close）
        java.util.Map<String, java.io.FileWriter> writers = new java.util.HashMap<>();

        try {
            for (String raw : rawLines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;

                ParsedEntry entry = parseLogLine(line);
                if (entry == null) continue;   // 无法识别编号/型号/版本的跳过
                total++;

                // 组装带汉字标签的存储格式：编号 W000xxxxx  型号 xxx  系统版本 xxx
                String internal = "编号 " + entry.wNumber + "  型号 " + entry.model + "  系统版本 " + entry.version;
                String category = categorizeImport(entry.model);

                String filename;
                if ("高维禁用".equals(category)) filename = "高维禁用.txt";
                else if ("高维禁用海外版".equals(category)) filename = "高维禁用海外版.txt";
                else if ("其他".equals(category)) filename = "其他.txt";
                else filename = "普通机型.txt";

                java.util.Set<String> existing = existingByFile.get(filename);
                if (existing == null) {
                    existing = new java.util.HashSet<>();
                    existingByFile.put(filename, existing);
                }
                if (entry.wNumber.isEmpty() || existing.contains(entry.wNumber)) {
                    continue;  // 已存在，跳过
                }
                existing.add(entry.wNumber);

                java.io.FileWriter writer = writers.get(filename);
                if (writer == null) {
                    java.io.File file = new java.io.File(getExternalFilesDir(null), filename);
                    writer = new java.io.FileWriter(file, true);
                    writers.put(filename, writer);
                }
                writer.write(internal + "\n");
                added++;
            }
        } catch (Exception e) {
            // 忽略单条写入异常
        } finally {
            for (java.io.FileWriter w : writers.values()) {
                try { w.close(); } catch (Exception ignored) {}
            }
        }

        if (total == 0) {
            return "未识别到有效日志（需包含编号 W000xxxxx）";
        } else {
            return "识别 " + total + " 条，新增 " + added + " 条";
        }
    }

    // 解析单行日志，提取 编号/型号/版本
    private ParsedEntry parseLogLine(String line) {
        try {
            java.util.regex.Matcher m;

            // 1. 编号：W000 + 数字
            java.util.regex.Pattern pW = java.util.regex.Pattern.compile("W000\\d{5}");
            m = pW.matcher(line);
            if (!m.find()) return null;
            String wNumber = m.group();

            // 编号之后的剩余内容（用于提取型号和版本）
            String rest = line.substring(m.end());

            // 2. 尝试按「型号 X  系统版本 Y」标签提取
            String model = "";
            String version = "";

            // 带标签格式：型号 xxx  系统版本 yyy
            java.util.regex.Matcher mM = java.util.regex.Pattern
                .compile("型号\\s*(.*?)\\s*系统版本\\s*(.*?)\\s*$").matcher(rest);
            if (mM.find()) {
                model = mM.group(1).trim();
                version = mM.group(2).trim();
            } else {
                // 纯空白分隔格式：编号 型号 版本
                String[] parts = rest.trim().split("\\s+");
                if (parts.length >= 2) {
                    model = parts[0];
                    version = parts[1];
                } else if (parts.length == 1) {
                    model = parts[0];
                }
            }

            // 版本号里去可能误带的尾部空格/杂质
            version = version.trim();

            if (model.isEmpty() && version.isEmpty()) {
                // 至少要有型号
                return null;
            }

            return new ParsedEntry(wNumber, model, version);
        } catch (Exception e) {
            return null;
        }
    }

    // 按型号分类（复用现有分类规则）
    private String categorizeImport(String model) {
        if (model.contains("高维禁用海外版") || model.contains("(High Level Repair Center is Forbidden)")) {
            return "高维禁用海外版";
        }
        if (model.contains("高维禁用")) {
            return "高维禁用";
        }
        if (model.contains("DPTF") || model.contains("WiFi") || model.contains("Bluetooth")
            || model.contains("Driver") || model.contains("Firmware") || model.length() < 3) {
            return "其他";
        }
        return "普通机型";
    }

    // 统一的编号提取：兼容「W00012345  ...」裸格式和「编号 W00012345 型号 ...」带标签格式
    private String extractWNumber(String line) {
        if (line == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("W000\\d{5}").matcher(line);
        if (m.find()) return m.group();
        return "";
    }

    // 解析结果载体
    private static class ParsedEntry {
        String wNumber;
        String model;
        String version;
        ParsedEntry(String w, String m, String v) { wNumber = w; model = m; version = v; }
    }
}
