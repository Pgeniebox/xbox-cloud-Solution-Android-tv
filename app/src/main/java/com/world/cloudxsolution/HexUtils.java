package com.world.cloudxsolution;

public final class HexUtils {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private HexUtils() {}

    public static String toHexString(byte[] bytes, int length) {
        if (bytes == null || length <= 0) return "";
        char[] hexChars = new char[length * 3];
        for (int j = 0; j < length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEX_ARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars).trim();
    }
}
