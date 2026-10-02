package com.iknowscanner2;

// 分类相关信息唯一出处：分类名、文件名、判定、映射。
// 旧代码（MainActivity.categorizeLine 等）保留未删，便于随时切回。
public final class FirmwareCategory {

    public static final String CAT_NORMAL = "普通机型";
    public static final String CAT_FORBIDDEN = "高维禁用";
    public static final String CAT_FORBIDDEN_ENG = "高维禁用海外版";
    public static final String CAT_OTHER = "其他";

    // 分类顺序：普通机型 / 高维禁用 / 高维禁用海外版 / 其他
    public static final String[] NAMES = {
        CAT_NORMAL, CAT_FORBIDDEN, CAT_FORBIDDEN_ENG, CAT_OTHER
    };

    public static final String[] FILES = {
        "普通机型.txt", "高维禁用.txt", "高维禁用海外版.txt", "其他.txt"
    };

    private FirmwareCategory() {
    }

    public static String categorize(String line) {
        if (line.contains(CAT_FORBIDDEN_ENG)) {
            return CAT_FORBIDDEN_ENG;
        }
        if (line.contains(CAT_FORBIDDEN)) {
            return CAT_FORBIDDEN;
        }
        if (line.contains("(High Level Repair Center is Forbidden)")) {
            return CAT_FORBIDDEN_ENG;
        }

        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            String model = parts[1];

            if (model.contains(CAT_FORBIDDEN)) {
                if (model.contains("英文版")) return CAT_FORBIDDEN_ENG;
                return CAT_FORBIDDEN;
            }
            if (model.contains("(High Level Repair Center is Forbidden)")) {
                return CAT_FORBIDDEN_ENG;
            }

            if (model.contains("DPTF") || model.contains("WiFi") || model.contains("Bluetooth") ||
                model.contains("Driver") || model.contains("Firmware") || model.length() < 3) {
                return CAT_OTHER;
            }
            return CAT_NORMAL;
        }

        return CAT_OTHER;
    }

    public static String fileNameFor(String category) {
        switch (category) {
            case CAT_NORMAL:
                return FILES[0];
            case CAT_FORBIDDEN:
                return FILES[1];
            case CAT_FORBIDDEN_ENG:
                return FILES[2];
            default:
                return FILES[3];
        }
    }
}
