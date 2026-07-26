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
}
