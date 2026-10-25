package com.jvn.villagerretaliation.authoring.client;

import java.nio.charset.StandardCharsets;

record EditorTextEdit(String text, int start, int end) {
    static EditorTextEdit insert(String text, int start, int end, String value) {
        int from = offset(text, Math.min(start, end)), to = offset(text, Math.max(start, end));
        String result = text.substring(0, from) + value + text.substring(to);
        int caret = bytes(text.substring(0, from) + value);
        return new EditorTextEdit(result, caret, caret);
    }

    static EditorTextEdit format(String text, int start, int end, String tag) {
        int from = offset(text, Math.min(start, end)), to = offset(text, Math.max(start, end));
        String open = "<" + tag + ">", close = "</" + tag.split(":", 2)[0] + ">";
        String before = text.substring(0, from), selected = text.substring(from, to), after = text.substring(to);
        if (before.endsWith(open) && after.startsWith(close)) {
            before = before.substring(0, before.length() - open.length());
            after = after.substring(close.length());
        } else if (selected.startsWith(open) && selected.endsWith(close)) {
            selected = selected.substring(open.length(), selected.length() - close.length());
        } else {
            if (selected.isEmpty()) selected = "text";
            before += open;
            after = close + after;
        }
        return new EditorTextEdit(before + selected + after, bytes(before), bytes(before + selected));
    }

    static int bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }

    static int offset(String text, int bytes) {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        int length = Math.max(0, Math.min(bytes, encoded.length));
        while (length > 0 && length < encoded.length && (encoded[length] & 0xc0) == 0x80) length--;
        return new String(encoded, 0, length, StandardCharsets.UTF_8).length();
    }
}
