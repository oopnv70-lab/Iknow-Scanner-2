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
                showImportDialog();
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
                    
                    String wNumber = "";
                    if (content.startsWith("W000")) {
                        int spaceIndex = content.indexOf(" ");
                        if (spaceIndex > 0) {
                            wNumber = content.substring(0, spaceIndex);
                        } else {
                            wNumber = content;
                        }
                    }
                    
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

    private void showImportDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("粘贴日志，每行一条，例如：\n编号 W00021123  型号 FNE-N29  系统版本 6.1.0.151(C185E1R2P1)\n\n也支持纯空格分隔：\nW00021123 FNE-N29 6.1.0.151(C185E1R2P1)");
        input.setGravity(Gravity.TOP);
        input.setMinLines(8);
        input.setMaxLines(16);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setTitle("导入日志")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("导入", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int which) {
                    String text = input.getText().toString();
                    importLog(text);
                }
            })
            .create();
        dialog.show();
    }

    private void importLog(String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "请先粘贴日志内容", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] rawLines = text.split("\\r?\\n");
        int total = 0;      // 识别到的总条数
        int added = 0;      // 去重后新增条数

        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            ParsedEntry entry = parseLogLine(line);
            if (entry == null) continue;   // 无法识别编号/型号/版本的跳过
            total++;

            // 组装内部标准格式（与扫描结果一致）：W编号  型号  版本
            String internal = entry.wNumber + "  " + entry.model + "  " + entry.version;
            String category = categorizeImport(entry.model);

            if (saveImportedLine(internal, category)) {
                added++;
            }
        }

        if (total == 0) {
            Toast.makeText(this, "未识别到有效日志（需包含编号 W000xxxxx）", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "识别 " + total + " 条，新增 " + added + " 条", Toast.LENGTH_LONG).show();
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

    // 追加写入对应分类文件，按 W 编号去重；成功写入返回 true
    private boolean saveImportedLine(String line, String category) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) return false;
            if (!dir.exists()) dir.mkdirs();

            String filename;
            if ("高维禁用".equals(category)) filename = "高维禁用.txt";
            else if ("高维禁用海外版".equals(category)) filename = "高维禁用海外版.txt";
            else if ("其他".equals(category)) filename = "其他.txt";
            else filename = "普通机型.txt";

            java.io.File file = new java.io.File(dir, filename);

            // 提取当前行 W 编号
            String currentW = "";
            if (line.startsWith("W000")) {
                int sp = line.indexOf(" ");
                currentW = sp > 0 ? line.substring(0, sp) : line;
            }

            // 检查是否已存在（按 W 编号去重）
            boolean exists = false;
            if (file.exists() && !currentW.isEmpty()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                String existing;
                while ((existing = reader.readLine()) != null) {
                    String content = existing;
                    if (content.startsWith("[")) {
                        int eb = content.indexOf("]");
                        if (eb > 0) content = content.substring(eb + 1).trim();
                    }
                    String exW = "";
                    if (content.startsWith("W000")) {
                        int sp = content.indexOf(" ");
                        exW = sp > 0 ? content.substring(0, sp) : content;
                    }
                    if (!exW.isEmpty() && exW.equals(currentW)) { exists = true; break; }
                }
                reader.close();
            }

            if (!exists) {
                java.io.FileWriter writer = new java.io.FileWriter(file, true);
                writer.write(line + "\n");
                writer.close();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // 解析结果载体
    private static class ParsedEntry {
        String wNumber;
        String model;
        String version;
        ParsedEntry(String w, String m, String v) { wNumber = w; model = m; version = v; }
    }
}
