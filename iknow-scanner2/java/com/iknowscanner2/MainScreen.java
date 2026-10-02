package com.iknowscanner2;

import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

// UI 独立化：只负责构建界面、持有控件、向 MainActivity 上报用户操作。
public final class MainScreen {

    public interface Listener {
        void onStart(boolean resume);
        void onStop();
        void onClear();
        void onSettings(View anchor);
        void onTest429();
        void onSaveForbidden();
    }

    private EditText editStart, editEnd;
    private Button btnStart, btnStop, btnClear, btnResume, btnSaveForbidden;
    private Button btnTest429;
    private TextView textProgress, textHitCount, textResult;
    private ScrollView resultScroll;

    public void build(MainActivity a, Listener l, int resumeStart, int resumeEnd) {

        FrameLayout screen = new FrameLayout(a);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 0, 24, 16);

        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        rootLp.setMargins(0, 220, 0, 0);

        TextView title = new TextView(a);
        title.setText(MainActivity.TXT_APP_TITLE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, 12);
        root.addView(title);

        TextView lab1 = new TextView(a);
        lab1.setText(MainActivity.TXT_LABEL_START);
        lab1.setTextSize(13);
        root.addView(lab1);

        editStart = new EditText(a);
        editStart.setHint(MainActivity.TXT_HINT_START);
        editStart.setSingleLine(true);
        editStart.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(editStart, mpwc());

        TextView lab2 = new TextView(a);
        lab2.setText(MainActivity.TXT_LABEL_END);
        lab2.setTextSize(13);
        lab2.setPadding(0, 8, 0, 0);
        root.addView(lab2);

        editEnd = new EditText(a);
        editEnd.setHint(MainActivity.TXT_HINT_END);
        editEnd.setSingleLine(true);
        editEnd.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(editEnd, mpwc());

        LinearLayout btnRow = new LinearLayout(a);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 12, 0, 0);

        btnStart = new Button(a);
        btnStart.setText(MainActivity.TXT_BTN_START);
        btnRow.addView(btnStart, weight());

        btnResume = new Button(a);
        btnResume.setText(MainActivity.TXT_BTN_RESUME);
        btnResume.setVisibility(View.GONE);
        btnRow.addView(btnResume, weight());
        // 【已屏蔽】「保存高维禁用」按钮不再显示（功能已废弃，扫描结果会实时自动分类保存）。
        // 按钮对象仍创建，仅为兼容下方 setEnabled 调用；不加入布局，故 UI 上不可见、不占宽度。
        btnSaveForbidden = new Button(a);
        btnSaveForbidden.setText(MainActivity.TXT_BTN_SAVE_FORBIDDEN);
        btnSaveForbidden.setEnabled(false);
        btnSaveForbidden.setVisibility(View.GONE);
        // btnRow.addView(btnSaveForbidden, weight());  // 屏蔽入口：不再添加到按钮行


        btnStop = new Button(a);
        btnStop.setText(MainActivity.TXT_BTN_STOP);
        btnRow.addView(btnStop, weight());

        btnClear = new Button(a);
        btnClear.setText(MainActivity.TXT_BTN_CLEAR);
        btnRow.addView(btnClear, weight());

        root.addView(btnRow, mpwc());

        // ==================== 【测试入口】429 熔断自测 ====================
        // 真实服务端返回 429 是被风控的标志，不能为了测试去故意制造高频请求。
        // 因此提供此按钮：点击后「武装」标志，使下一次请求无论真实响应如何都被
        // 当作 429 处理，从而在不触发真实风控的前提下验证熔断/冷却是否生效。
        btnTest429 = new Button(a);
        btnTest429.setText(MainActivity.TXT_BTN_TEST_429);
        btnTest429.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                l.onTest429();
                Toast.makeText(a,
                    "已武装，下次请求将模拟 429", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(btnTest429, mpwc());

        LinearLayout prog = new LinearLayout(a);
        prog.setOrientation(LinearLayout.HORIZONTAL);
        prog.setPadding(0, 10, 0, 8);

        textProgress = new TextView(a);
        textProgress.setText(MainActivity.TXT_STATUS_READY);
        textProgress.setTextSize(13);
        prog.addView(textProgress, weight());

        textHitCount = new TextView(a);
        textHitCount.setText(MainActivity.TXT_HIT_PREFIX + "0");
        textHitCount.setTextSize(13);
        textHitCount.setTextColor(0xFFE65100);
        prog.addView(textHitCount);

        root.addView(prog, mpwc());

        View line = new View(a);
        line.setBackgroundColor(0xFFCCCCCC);
        root.addView(line, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));

        resultScroll = new ScrollView(a);
        textResult = new TextView(a);
        textResult.setText(MainActivity.TXT_RESULT_WAITING);
        textResult.setTextSize(10);
        textResult.setTypeface(Typeface.MONOSPACE);
        textResult.setPadding(0, 8, 0, 8);
        resultScroll.addView(textResult);
        root.addView(resultScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        screen.addView(root, rootLp);

        // 添加右上角三个点菜单
        ImageButton menuBtn = new ImageButton(a);
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
                l.onSettings(v);
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { l.onStart(false); }
        });
        btnResume.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { l.onStart(true); }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { l.onStop(); }
        });
        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { l.onClear(); }
        });
        btnSaveForbidden.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { l.onSaveForbidden(); }
        });

        int end = resumeEnd;
        int next = resumeStart;
        if (end >= 0 && next >= 0 && next <= end) {
            editStart.setText(String.valueOf(next));
            editEnd.setText(String.valueOf(end));
            btnResume.setVisibility(View.VISIBLE);
        }
        a.setContentView(screen);
    }

    // ---- 对外操作接口（MainActivity 通过它们更新界面） ----

    public void setStartText(String s) { editStart.setText(s); }
    public void setEndText(String s) { editEnd.setText(s); }
    public String getStartText() { return editStart.getText().toString().trim(); }
    public String getEndText() { return editEnd.getText().toString().trim(); }
    public void setStartEnabled(boolean b) { btnStart.setEnabled(b); }
    public void setResumeVisible(boolean b) { btnResume.setVisibility(b ? View.VISIBLE : View.GONE); }
    public void setSaveForbiddenEnabled(boolean b) { btnSaveForbidden.setEnabled(b); }
    public void setHitCount(String s) { textHitCount.setText(s); }
    public void setResultText(String s) { textResult.setText(s); }
    public void setProgressText(String s) { textProgress.setText(s); }
    public void appendAndScroll(String s) {
        textResult.setText(s);
        resultScroll.post(new Runnable() {
            public void run() { resultScroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private LinearLayout.LayoutParams mpwc() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }
}
