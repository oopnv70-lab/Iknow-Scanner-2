package com.iknowscanner2;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

// 运行日志界面：显示缓冲区内容，支持保存到私有目录、清空。
public class LogActivity extends Activity {

    private TextView textLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);
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
        title.setText("运行日志");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(24, 0, 0, 0);
        titleBar.addView(title);

        root.addView(titleBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        // 状态提示
        TextView hint = new TextView(this);
        hint.setTextSize(13);
        hint.setPadding(0, 12, 0, 12);
        root.addView(hint);

        // 日志正文
        ScrollView scroll = new ScrollView(this);
        textLog = new TextView(this);
        textLog.setTextSize(10);
        textLog.setTypeface(Typeface.MONOSPACE);
        textLog.setPadding(8, 8, 8, 8);
        scroll.addView(textLog);
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 保存按钮
        Button btnSave = new Button(this);
        btnSave.setText("保存到本地");
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File dir = getExternalFilesDir(null);
                if (dir == null) {
                    Toast.makeText(LogActivity.this, "无法访问存储目录", Toast.LENGTH_SHORT).show();
                    return;
                }
                File out = Log.saveToFile(dir);
                if (out != null) {
                    Toast.makeText(LogActivity.this, "已保存: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LogActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnSave, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        // 清空按钮
        Button btnClear = new Button(this);
        btnClear.setText("清空日志");
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.clear();
                refresh(hint);
                Toast.makeText(LogActivity.this, "已清空", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(btnClear, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        refresh(hint);
    }

    private void refresh(TextView hint) {
        if (!Log.on()) {
            hint.setText("日志未启用。请返回设置开启「启用请求日志」后再扫描。");
            textLog.setText("(未启用)");
            return;
        }
        List<String> lines = Log.getAll();
        hint.setText("已记录 " + lines.size() + " 行（仅本次运行，退出即清空）");
        if (lines.isEmpty()) {
            textLog.setText("(暂无日志，请先扫描)");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                sb.append(l).append('\n');
            }
            textLog.setText(sb.toString());
            // 滚到底部
            final ScrollView sc = (ScrollView) textLog.getParent();
            sc.post(new Runnable() {
                public void run() { sc.fullScroll(View.FOCUS_DOWN); }
            });
        }
    }
}
