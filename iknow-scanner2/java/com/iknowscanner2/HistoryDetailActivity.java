package com.iknowscanner2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import java.io.*;
import java.util.*;

public class HistoryDetailActivity extends Activity {
    public static final String EXTRA_CATEGORY = "category";

    private int category = 0;
    private List<String> lines = new ArrayList<String>();
    private LinearLayout contentContainer;
    private EditText searchBox;
    private int colorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent it = getIntent();
        if (it != null) {
            category = it.getIntExtra(EXTRA_CATEGORY, 0);
        }
        if (category < 0 || category > 3) category = 0;
        colorText = HistoryActivity.getCategoryColor(category);

        loadData();

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
        title.setText(HistoryActivity.CATEGORY_NAMES[category] + " (" + lines.size() + ")");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(24, 0, 0, 0);
        titleBar.addView(title);

        root.addView(titleBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        searchBox = new EditText(this);
        searchBox.setHint("搜索编号或型号...");
        searchBox.setPadding(16, 16, 16, 16);
        searchBox.setBackgroundColor(0xFFFFFFFF);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                render(s.toString());
            }
        });
        root.addView(searchBox, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(0xFFCCCCCC);
        root.addView(divider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 2));

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
        render("");
    }

    private void loadData() {
        File dir = getExternalFilesDir(null);
        if (dir == null || !dir.exists()) return;
        File file = new File(dir, HistoryActivity.CATEGORY_FILES[category]);
        if (!file.exists()) return;
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
                lines.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        }
    }

    private void render(String query) {
        if (contentContainer == null) return;
        contentContainer.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase();
        int shown = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (q.isEmpty() || line.toLowerCase().contains(q)) {
                addRecordLine(contentContainer, line);
                shown++;
            }
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(lines.isEmpty() ? "暂无记录" : "没有匹配的记录");
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 48, 0, 0);
            contentContainer.addView(empty);
        }
    }

    private void addRecordLine(LinearLayout parent, String line) {
        TextView record = new TextView(this);
        record.setText(line);
        record.setTextSize(14);
        record.setTextColor(colorText);
        record.setTypeface(Typeface.MONOSPACE);
        record.setPadding(0, 8, 0, 8);
        parent.addView(record, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(0xFFEEEEEE);
        parent.addView(divider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }
}
