package com.iknowscanner2;
import android.text.TextWatcher;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import java.io.*;
import java.util.*;

public class HistoryActivity extends Activity {
    private List<String> normalLines = new ArrayList<>();
    private EditText searchBox;
    private List<String> forbiddenLines = new ArrayList<>();
    private List<String> forbiddenEngLines = new ArrayList<>(); // 新增：高维禁用海外版
    private List<String> otherLines = new ArrayList<>();
    
    private LinearLayout contentContainer;
    private TextView tabNormal, tabForbidden, tabForbiddenEng, tabOther;
    private int currentTab = 0; // 0=普通机型, 1=高维禁用, 2=高维禁用海外版, 3=其他
    
    // 颜色设置
    private int colorNormal = 0xFF4CAF50; // 默认绿色
    private int colorForbidden = 0xFFF44336; // 默认红色
    private int colorOversea = 0xFFFF9800; // 默认橙色
    private int colorOther = 0xFF9E9E9E; // 默认灰色
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 读取历史记录
        
        // 读取颜色设置
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        try {
            colorNormal = android.graphics.Color.parseColor(prefs.getString("color_normal", "#4CAF50"));
        } catch (Exception e) {}
        try {
            colorForbidden = android.graphics.Color.parseColor(prefs.getString("color_forbidden", "#F44336"));
        } catch (Exception e) {}
        try {
            colorOversea = android.graphics.Color.parseColor(prefs.getString("color_oversea", "#FF9800"));
        } catch (Exception e) {}
        try {
            colorOther = android.graphics.Color.parseColor(prefs.getString("color_other", "#9E9E9E"));
        } catch (Exception e) {}
        loadHistory();
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, 0);
        
        // 标题栏
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(32, 120, 32, 16);
        
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
        title.setText("历史记录");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(24, 0, 0, 0);
        titleBar.addView(title);
        
        root.addView(titleBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 标签栏
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(16, 16, 16, 0);
        tabBar.setBackgroundColor(0xFFF5F5F5);
        
        tabNormal = createTab("普通机型 (" + normalLines.size() + ")");
        tabForbidden = createTab("高维禁用 (" + forbiddenLines.size() + ")");
        tabForbiddenEng = createTab("高维禁用海外版 (" + forbiddenEngLines.size() + ")");
        tabOther = createTab("其他 (" + otherLines.size() + ")");
        
        tabNormal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(0);
            }
        });
        tabForbidden.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(1);
            }
        });
        tabForbiddenEng.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(2);
            }
        });
        tabOther.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(3);
            }
        });
        
        tabBar.addView(tabNormal, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabBar.addView(tabForbidden, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabBar.addView(tabForbiddenEng, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabBar.addView(tabOther, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        root.addView(tabBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 搜索框
        searchBox = new EditText(this);
        searchBox.setHint("搜索 W 编号、型号...");
        searchBox.setPadding(16, 16, 16, 16);
        searchBox.setBackgroundColor(0xFFFFFFFF);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterList(s.toString());
            }
        });
        root.addView(searchBox, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 分隔线
        View divider = new View(this);
        divider.setBackgroundColor(0xFFCCCCCC);
        root.addView(divider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 2));
        
        // 内容区域
        ScrollView scroll = new ScrollView(this);
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(16, 16, 16, 16);
        
        scroll.addView(contentContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        
        
        setContentView(root);
        
        // 显示默认标签内容
        switchTab(0);
    }
    
    private TextView createTab(String text) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setTextSize(14);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(0, 24, 0, 24);
        tab.setTypeface(Typeface.DEFAULT_BOLD);
        return tab;
    }
    
    private void switchTab(int tabIndex) {
        currentTab = tabIndex;
        
        // 更新标签样式
        tabNormal.setBackgroundColor(tabIndex == 0 ? Color.WHITE : 0xFFF5F5F5);
        tabNormal.setTextColor(tabIndex == 0 ? 0xFF2196F3 : Color.BLACK);
        tabForbidden.setBackgroundColor(tabIndex == 1 ? Color.WHITE : 0xFFF5F5F5);
        tabForbidden.setTextColor(tabIndex == 1 ? 0xFF2196F3 : Color.BLACK);
        tabForbiddenEng.setBackgroundColor(tabIndex == 2 ? Color.WHITE : 0xFFF5F5F5);
        tabForbiddenEng.setTextColor(tabIndex == 2 ? 0xFF2196F3 : Color.BLACK);
        tabOther.setBackgroundColor(tabIndex == 3 ? Color.WHITE : 0xFFF5F5F5);
        tabOther.setTextColor(tabIndex == 3 ? 0xFF2196F3 : Color.BLACK);
        
        // 应用过滤
        filterList(searchBox.getText().toString());
    }
    
    private void filterList(String query) {
        contentContainer.removeAllViews();
        
        List<String> lines;
        switch (currentTab) {
            case 0:
                lines = normalLines;
                break;
            case 1:
                lines = forbiddenLines;
                break;
            case 2:
                lines = forbiddenEngLines;
                break;
            case 3:
                lines = otherLines;
                break;
            default:
                lines = new ArrayList<>();
        }
        
        boolean found = false;
        for (String line : lines) {
            if (query.isEmpty() || line.toLowerCase().contains(query.toLowerCase())) {
                addRecordLine(contentContainer, line);
                found = true;
            }
        }
        
        if (!found) {
            TextView emptyText = new TextView(this);
            emptyText.setText("没有匹配的记录");
            emptyText.setTextSize(16);
            emptyText.setPadding(0, 48, 0, 0);
            emptyText.setGravity(Gravity.CENTER);
            contentContainer.addView(emptyText);
        }
    }
    private void loadHistory() {
        try {
            File dir = getExternalFilesDir(null); // /sdcard/Android/data/com.iknowscanner2/files/
            if (dir == null || !dir.exists()) {
                return;
            }
            
            // 读取四个分类文件
            loadCategoryFile(new File(dir, "普通机型.txt"), normalLines);
            loadCategoryFile(new File(dir, "高维禁用.txt"), forbiddenLines);
            loadCategoryFile(new File(dir, "高维禁用海外版.txt"), forbiddenEngLines);
            loadCategoryFile(new File(dir, "其他.txt"), otherLines);
            
            // 兼容旧文件：读取 forbidden_*.txt 和 scan_*.txt
            File[] oldFiles = dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    String name = f.getName();
                    return name.startsWith("scan_") || name.startsWith("forbidden_");
                }
            });
            
            if (oldFiles != null) {
                for (File file : oldFiles) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 处理字面量 \\n（旧文件可能包含）
                        if (line.contains("\\n")) {
                            String[] subLines = line.split("\\\\n");
                            for (String subLine : subLines) {
                                processLine(subLine);
                            }
                        } else {
                            processLine(line);
                        }
                    }
                    reader.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void loadCategoryFile(File file, List<String> targetList) {
        if (!file.exists()) {
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                // 跳过表头、分隔线、空行、开始/完成标记
                if (line.startsWith("W编号") || line.startsWith("---") || 
                    line.startsWith("===") || line.startsWith("[开始]") || 
                    line.startsWith(">>> 已停止")) {
                    continue;
                }
                
                // 如果还有时间戳格式（兼容旧数据），移除时间戳
                if (line.startsWith("[")) {
                    int endBracket = line.indexOf("]");
                    if (endBracket > 0) {
                        line = line.substring(endBracket + 1).trim();
                    }
                }
                
                targetList.add(line);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void processLine(String line) {
        if (line.trim().isEmpty()) return;
        
        // 跳过表头、分隔线、空行、开始/完成标记
        if (line.startsWith("W编号") || line.startsWith("---") || 
            line.startsWith("===") || line.startsWith("[开始]") || 
            line.startsWith(">>> 已停止")) {
            return;
        }
        
        // 分类
        String category = categorizeLine(line);
        switch (category) {
            case "普通机型":
                normalLines.add(line);
                break;
            case "高维禁用":
                forbiddenLines.add(line);
                break;
            case "高维禁用海外版":
                forbiddenEngLines.add(line);
                break;
            default:
                otherLines.add(line);
                break;
        }
    }
    
    private String categorizeLine(String line) {
        // 高维禁用（英文版优先判断）
        if (line.contains("高维禁用海外版") || line.contains("(High Level Repair Center is Forbidden)")) {
            return "高维禁用海外版";
        }
        // 高维禁用（中文版）
        if (line.contains("高维禁用")) {
            return "高维禁用";
        }
        
        // 判断是否是普通手机机型
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            String model = parts[1]; // 第二列是型号
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
    
    private void addRecordLine(LinearLayout parent, String line) {
        TextView record = new TextView(this);
        record.setText(line);
        record.setTextSize(14);
        // 根据当前标签页设置颜色
        switch (currentTab) {
            case 0: // 普通机型
                record.setTextColor(colorNormal);
                break;
            case 1: // 高维禁用
                record.setTextColor(colorForbidden);
                break;
            case 2: // 海外版
                record.setTextColor(colorOversea);
                break;
            case 3: // 其他
                record.setTextColor(colorOther);
                break;
        }
        record.setPadding(0, 8, 0, 8);
        record.setTypeface(Typeface.MONOSPACE);
        
        // 添加底部边框
        View divider = new View(this);
        divider.setBackgroundColor(0xFFEEEEEE);
        
        parent.addView(record, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(divider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }
}
