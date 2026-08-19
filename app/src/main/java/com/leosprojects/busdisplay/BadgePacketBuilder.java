package com.leosprojects.busdisplay;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the LSLED/B1144 packet used by the FEE0/FEE1 BLE badge.
 * The destination bitmap is sent as one fixed, non-scrolling message.
 */
final class BadgePacketBuilder {
    private static final String PACKET_START = "77616E670000";
    private static final int CHUNK_BYTES = 16;

    private BadgePacketBuilder() {}

    static List<byte[]> build(String bitmapHex) {
        if (bitmapHex == null || bitmapHex.length() != 132) {
            throw new IllegalArgumentException("Bitmap must be 66 bytes / 132 hex chars");
        }

        StringBuilder hex = new StringBuilder();

        // 6-byte magic/header used by this family of badges.
        hex.append(PACKET_START);

        // Flash flags, marquee flags.
        hex.append("00");
        hex.append("00");

        // Eight message option bytes. First message: fixed mode (0x04),
        // speed level 1 (0x00). Remaining seven message slots unused.
        hex.append("04");
        hex.append("00".repeat(7));

        // Six 8-column bitmap segments = message length 6.
        hex.append("0006");
        hex.append("00".repeat(14));

        // Six reserved bytes.
        hex.append("00".repeat(6));

        // Badge Magic-compatible six-byte clock field.
        hex.append(timeHex());

        // Twenty reserved bytes.
        hex.append("00".repeat(20));

        // Exact 48x11 packed bitmap (last four columns are blank padding).
        hex.append(bitmapHex);

        // Pad to the *next* 16-byte packet boundary, matching Badge Magic.
        int charsPerChunk = CHUNK_BYTES * 2;
        int remainder = hex.length() % charsPerChunk;
        int paddingChars = remainder == 0 ? charsPerChunk : charsPerChunk - remainder;
        hex.append("0".repeat(paddingChars));

        byte[] packet = Hex.bytes(hex.toString());
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < packet.length; offset += CHUNK_BYTES) {
            int count = Math.min(CHUNK_BYTES, packet.length - offset);
            byte[] chunk = new byte[count];
            System.arraycopy(packet, offset, chunk, 0, count);
            chunks.add(chunk);
        }
        return chunks;
    }

    private static String timeHex() {
        LocalDateTime now = LocalDateTime.now();

        // The upstream Badge Magic implementation sends month + 1.
        int year = now.getYear() & 0xFF;
        int month = (now.getMonthValue() + 1) & 0xFF;
        int day = now.getDayOfMonth() & 0xFF;
        int hour = now.getHour() & 0xFF;
        int minute = now.getMinute() & 0xFF;
        int second = now.getSecond() & 0xFF;

        return Hex.byteHex(year)
                + Hex.byteHex(month)
                + Hex.byteHex(day)
                + Hex.byteHex(hour)
                + Hex.byteHex(minute)
                + Hex.byteHex(second);
    }
}
