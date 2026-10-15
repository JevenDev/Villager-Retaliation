package com.jvn.villagerretaliation.quest.tutorial;

import java.util.function.Function;
import java.util.regex.Pattern;

/** Only these explicitly supported tutorial hints are expanded; other text is literal. */
public final class TutorialInstructionText {
    private static final Pattern TOKENS = Pattern.compile("\\{(Inventory|Use|Attack|Sneak|ControlModifier|ShiftModifier|AltModifier)\\}");
    private TutorialInstructionText() { }
    public static String resolve(String text, Function<String, String> bindings) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        var matcher = TOKENS.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            String replacement = bindings.apply(matcher.group(1));
            if (replacement == null || replacement.isBlank()) replacement = matcher.group();
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result); return result.toString();
    }
}
