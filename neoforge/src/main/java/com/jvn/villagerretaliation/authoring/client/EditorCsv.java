package com.jvn.villagerretaliation.authoring.client;

import java.util.ArrayList;
import java.util.List;

final class EditorCsv {
    static String write(List<List<String>> rows) {
        var result = new StringBuilder();
        for (var row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) result.append(',');
                result.append('"').append(row.get(i).replace("\"", "\"\"")).append('"');
            }
            result.append("\r\n");
        }
        return result.toString();
    }

    static List<List<String>> read(String text) {
        if (text.startsWith("\ufeff")) text = text.substring(1);
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var cell = new StringBuilder();
        boolean quoted = false, closed = false, started = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else cell.append(c);
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(cell.toString()); cell.setLength(0); closed = false; started = false;
                if (c != ',') {
                    rows.add(List.copyOf(row)); row.clear();
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                }
            } else if (closed) throw new IllegalArgumentException("Unexpected text after a quoted CSV cell");
            else if (c == '"') {
                if (started) throw new IllegalArgumentException("A CSV quote must start its cell");
                quoted = true; started = true;
            } else { cell.append(c); started = true; }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed quoted CSV cell");
        if (started || closed || !row.isEmpty()) { row.add(cell.toString()); rows.add(List.copyOf(row)); }
        return List.copyOf(rows);
    }
}
