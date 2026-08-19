package com.leosprojects.busdisplay;

import java.util.Locale;

final class Hex {
    private Hex() {}

    static byte[] bytes(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Odd-length hex");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Invalid hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    static String byteHex(int value) {
        return String.format(Locale.US, "%02X", value & 0xFF);
    }
}
