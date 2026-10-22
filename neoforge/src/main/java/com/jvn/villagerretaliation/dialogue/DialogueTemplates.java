package com.jvn.villagerretaliation.dialogue;

import java.util.HashMap;
import java.util.Map;

public final class DialogueTemplates {
    private DialogueTemplates() { }

    public static String resolveTemplate(String text, Map<String, String> replacements) {
        String resolved = text;
        Map<String, String> safeReplacements = new HashMap<>(replacements);
        for (Map.Entry<String, String> entry : safeReplacements.entrySet()) {
            resolved = replaceTemplateToken(
                    resolved,
                    "{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue(),
                    entry.getKey().endsWith("_article")
                            || entry.getKey().equals("gift_item")
                            || entry.getKey().equals("item")
            );
        }
        return resolved;
    }

    public static String resolveTemplate(String text, Map<String, String> replacements, DialogueRuntimeContext context) {
        String resolved = resolveTemplate(text, replacements);
        if (!resolved.contains("{word")) return resolved;
        return com.jvn.villagerretaliation.dialogue.resources.DialogueWordPoolResources
                .resolver(context.level() == null ? null : context.level().getServer(), context.locale(), context.random()).apply(resolved);
    }

    public static String resolveTemplate(String text, Map<String, String> replacements,
            net.minecraft.server.MinecraftServer server, String locale, net.minecraft.util.RandomSource random) {
        return com.jvn.villagerretaliation.dialogue.resources.DialogueWordPoolResources.resolver(server, locale, random)
                .apply(resolveTemplate(text, replacements));
    }

    private static String replaceTemplateToken(String text, String token, String replacement, boolean capitalizeAtSentenceStart) {
        StringBuilder builder = new StringBuilder(text.length());
        int cursor = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            builder.append(text, cursor, index);
            builder.append(capitalizeAtSentenceStart && isSentenceStart(text, index)
                    ? capitalizeFirstLetter(replacement)
                    : replacement);
            cursor = index + token.length();
            index = text.indexOf(token, cursor);
        }
        builder.append(text, cursor, text.length());
        return builder.toString();
    }

    private static boolean isSentenceStart(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || ch == '"' || ch == '\'' || ch == '(' || ch == '[') {
                continue;
            }
            return ch == '.' || ch == '!' || ch == '?';
        }
        return true;
    }

    private static String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
