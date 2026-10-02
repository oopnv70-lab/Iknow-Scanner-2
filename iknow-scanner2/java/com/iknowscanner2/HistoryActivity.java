package com.iknowscanner2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.graphics.Typeface;
import java.io.*;

public class HistoryActivity extends Activity {
    public static final int CATEGORY_NORMAL = 0;
    public static final int CATEGORY_FORBIDDEN = 1;
    public static final int CATEGORY_FORBIDDEN_ENG = 2;
    public static final int CATEGORY_OTHER = 3;

    public static final String[] CATEGORY_NAMES = {
        "普通机型", "高维禁用", "高维禁用海外版", "其他"
    };

    public static final String[] CATEGORY_FILES = {
        "普通机型.txt", "高维禁用.txt", "高维禁用海外版.txt", "其他.txt"
    };

    private final int[] counts = new int[4];
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadCounts();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(32, 120, 32, 16);

        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_revert);
        backBtn.setBackgroundColor(0x00000000);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
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

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 8, 16, 16);
        rebuildList();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(listContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listContainer != null) {
            loadCounts();
            rebuildList();
        }
    }

    private void rebuildList() {
        listContainer.removeAllViews();
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            addCategoryEntry(listContainer, i);
        }
    }

    private void addCategoryEntry(LinearLayout parent, final int category) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(24, 32, 24, 32);
        row.setBackgroundColor(0xFFFFFFFF);

        View colorBlock = new View(this);
        colorBlock.setBackgroundColor(getCategoryColor(category));
        row.addView(colorBlock, new LinearLayout.LayoutParams(12, 60));

        TextView name = new TextView(this);
        name.setText(CATEGORY_NAMES[category]);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(24, 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView count = new TextView(this);
        count.setText(counts[category] + " 条");
        count.setTextSize(14);
        count.setTextColor(0xFF888888);
        count.setPadding(0, 0, 16, 0);
        row.addView(count);

        TextView arrow = new TextView(this);
        arrow.setText("\u203A");
        arrow.setTextSize(24);
        arrow.setTextColor(0xFFBBBBBB);
        row.addView(arrow);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HistoryActivity.this, HistoryDetailActivity.class);
                intent.putExtra(HistoryDetailActivity.EXTRA_CATEGORY, category);
                startActivity(intent);
            }
        });

        parent.addView(row, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(0xFFEEEEEE);
        parent.addView(divider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    static int getCategoryColor(int category) {
        switch (category) {
            case CATEGORY_NORMAL:        return 0xFF4CAF50;
            case CATEGORY_FORBIDDEN:     return 0xFFF44336;
            case CATEGORY_FORBIDDEN_ENG: return 0xFFFF9800;
            default:                     return 0xFF9E9E9E;
        }
    }

    private void loadCounts() {
        File dir = getExternalFilesDir(null);
        if (dir == null || !dir.exists()) {
            for (int i = 0; i < 4; i++) counts[i] = 0;
            return;
        }
        for (int i = 0; i < CATEGORY_FILES.length; i++) {
            counts[i] = countCategoryFile(new File(dir, CATEGORY_FILES[i]));
        }
    }

    private int countCategoryFile(File file) {
        if (!file.exists()) return 0;
        int n = 0;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (line.startsWith("W编号") || line.startsWith("---") ||
                    line.startsWith("===") || line.startsWith("[开始]") ||
                    line.startsWith(">>> 已停止")) {
                    continue;
                }
                if (line.startsWith("[")) {
                    int endBracket = line.indexOf("]");
                    if (endBracket > 0) {
                        line = line.substring(endBracket + 1).trim();
                    }
                }
                if (line.isEmpty()) continue;
                n++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        }
        return n;
    }
}
