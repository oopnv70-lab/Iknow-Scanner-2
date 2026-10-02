package com.iknowscanner2;

// 分类判定与文件名映射。原 MainActivity.categorizeLine 与 saveLineToFile 内 switch 迁移至此。
// 旧方法保留在 MainActivity 中未删除，便于随时切回。
public final class FirmwareCategory {

    private FirmwareCategory() {
    }

    public static String categorize(String line) {
        // 高维禁用（英文版优先判断，因为包含 "高维禁用海外版"）
        if (line.contains("高维禁用海外版")) {
            return "高维禁用海外版";
        }
        // 高维禁用（中文版）
        if (line.contains("高维禁用")) {
            return "高维禁用";
        }
        // 高维禁用（原始英文版，以防万一没被替换显示但分类需要识别）
        if (line.contains("(High Level Repair Center is Forbidden)")) {
            return "高维禁用海外版";
        }

        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            String model = parts[1];

            if (model.contains("高维禁用")) {
                if (model.contains("英文版")) return "高维禁用海外版";
                return "高维禁用";
            }
            if (model.contains("(High Level Repair Center is Forbidden)")) {
                return "高维禁用海外版";
            }

            if (model.contains("DPTF") || model.contains("WiFi") || model.contains("Bluetooth") ||
                model.contains("Driver") || model.contains("Firmware") || model.length() < 3) {
                return "其他";
            }
            return "普通机型";
        }

        return "其他";
    }

    public static String fileNameFor(String category) {
        switch (category) {
            case "普通机型":
                return "普通机型.txt";
            case "高维禁用":
                return "高维禁用.txt";
            case "高维禁用海外版":
                return "高维禁用海外版.txt";
            default:
                return "其他.txt";
        }
    }
}
