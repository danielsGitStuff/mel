package de.mel.auth.tools;

import java.util.Locale;

public class HumanNumbers {
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    /**
     *
     * @param byteSize
     * @return byteSize formatted in a human-readable format. Up to peta byte.
     */
    public static String toByteSize(Long byteSize){
        if (byteSize == null) {
            return "0.00 B";
        }
        double value = Math.abs(byteSize.doubleValue());
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < UNITS.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        if (byteSize < 0) {
            value = -value;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, UNITS[unitIndex]);
    }
}
