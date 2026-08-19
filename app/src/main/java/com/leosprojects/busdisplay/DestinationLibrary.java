package com.leosprojects.busdisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact 44x11 bitmaps for bus 4513.
 *
 * Each payload is padded to 48 columns and packed into six 8-column
 * segments. Each segment contains 11 bytes, one byte per LED row.
 */
public final class DestinationLibrary {
    public static final String BUS_NUMBER = "4513";
    private static final LinkedHashMap<String, String> DESTINATIONS = new LinkedHashMap<>();

    static {
        DESTINATIONS.put("Traversella", "AEA8EC222C00EC4A4C4A4A4CC24442EC004AAAEAAAA4000000000000EC8ACC8AEA0000000000006E884C28CE00000000000088888888EE00000000000040A0E0A0A0");
        DESTINATIONS.put("Fondo", "AEA8EC222C0000000000004CC24442EC000000000000E48ACA8A84000000000000ACEAEAEAAC00000000000040A0A0A0400000000000000000000000000000000000");
        DESTINATIONS.put("Capeggio Chiara", "AEA8EC222C0000000000004CC24442EC001A2223221A648A8E8A6A00B9929392BACEA8CC888E0031AAB3AAAA6688AAAA66000080808080E0404040E0000000000000");
        DESTINATIONS.put("Inverso", "AEA8EC222C0000000000004CC24442EC000000000000EA4E4E4EEA000000000000AEA8ACA84E000000000000C6A8C4A2AC00000000000040A0A0A040000000000000");
        DESTINATIONS.put("Colletta di Bossola", "AEA8EC222C0000000000004CC24442EC00C4AACAAAC4648A8A8A640066884422CC88888888EE0048A8A8A84EEE84C484E40040A0E0A0A0E040404040000000000000");
        DESTINATIONS.put("Castellamonte", "AEA8EC222C0000000000004CC24442EC000A0E0E0A0A648A8E8A6A004AAEAEAE4A6E844424C400EE484C484EE888C888EE00000000000080808080E0000000000000");
        DESTINATIONS.put("Ivrea Movicentro", "AEA8EC222C00293A3A2A294CC24442EC002BA9A9A913EA4A4A4AE4009B2223229BCEA8CCA8AE00AB393939A940A0E0A0A000B12A322A290000000000000080808000");
        DESTINATIONS.put("Cuorgnè", "AEA8EC222C0000000000004CC24442EC0000000000006A8A8A8A6E0000000000004CAAACAA4A0000000000006A8EAEAE6A000000000000C040C080E0000000000000");
        DESTINATIONS.put("Rivarolo", "AEA8EC222C0003020302024CC24442EC003A921292B900000000000093AABBAA2A00000000000012AA2AAA9300000000000010282828900000000000000000000000");
        DESTINATIONS.put("Pecco", "AEA8EC222C0000000000004CC24442EC000000000000CEA8CC888E000000000000668888886600000000000040A0A0A0400000000000000000000000000000000000");
        DESTINATIONS.put("Alice Superiore", "AEA8EC222C00060804020C4CC24442EC00ACAAACA8E848A8E8A8AE00EC8ACC8AEAE6484848E600E44A4A4AE4E080C080E000CEA8CCA8AE0000000000000000000000");
    }

    private DestinationLibrary() {}

    public static List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(DESTINATIONS.keySet()));
    }

    public static String payloadHex(String name) {
        String value = DESTINATIONS.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown destination: " + name);
        }
        return value;
    }

    public static boolean[][] matrix(String name) {
        String hex = payloadHex(name);
        byte[] data = Hex.bytes(hex);
        if (data.length != 66) {
            throw new IllegalStateException("Expected 66 bitmap bytes, got " + data.length);
        }

        boolean[][] grid = new boolean[11][44];
        for (int segment = 0; segment < 6; segment++) {
            for (int row = 0; row < 11; row++) {
                int value = data[segment * 11 + row] & 0xFF;
                for (int bit = 0; bit < 8; bit++) {
                    int column = segment * 8 + bit;
                    if (column < 44) {
                        grid[row][column] = ((value >> (7 - bit)) & 1) == 1;
                    }
                }
            }
        }
        return grid;
    }
}
