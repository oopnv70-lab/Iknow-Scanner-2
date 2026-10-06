package com.iknowscanner2;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// 运行日志：默认关闭；开启后记录请求全过程，供排查使用。
// 关闭时 add() 直接返回，且调用方用 on() 先行判断，避免字符串拼接开销。
public final class Log {

    private static final int MAX_LINES = 200000; // 安全上限，防止极端情况撑爆内存

    private static volatile boolean on = false;
    private static final Object LOCK = new Object();
    private static final List<String> BUFFER = new ArrayList<String>();
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private Log() {
    }

    public static boolean on() {
        return on;
    }

    public static void setOn(boolean enabled) {
        on = enabled;
    }

    public static void add(String msg) {
        if (!on || msg == null) return;
        String line = TS.format(new Date()) + "  " + msg;
        synchronized (LOCK) {
            if (BUFFER.size() < MAX_LINES) {
                BUFFER.add(line);
            }
        }
    }

    public static List<String> getAll() {
        synchronized (LOCK) {
            return new ArrayList<String>(BUFFER);
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return BUFFER.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            BUFFER.clear();
        }
    }

    // 保存到私有目录（getExternalFilesDir），覆盖同名文件。返回保存的文件，失败返回 null。
    public static File saveToFile(File dir) {
        if (dir == null) return null;
        File out = new File(dir, "运行日志.txt");
        List<String> snapshot = getAll();
        FileWriter w = null;
        try {
            w = new FileWriter(out, false);
            for (String l : snapshot) {
                w.write(l);
                w.write("\n");
            }
            w.flush();
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (w != null) w.close(); } catch (Exception ignored) {}
        }
    }
}
