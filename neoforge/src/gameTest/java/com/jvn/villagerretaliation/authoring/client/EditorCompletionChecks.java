package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorCompletionChecks {
    private static final EditorTextCompletion.Context CONTEXT = EditorTextCompletion.Context.empty(DefinitionKind.TREE);
    private EditorCompletionChecks() { }

    static int run() {
        variablesCompleteAtUnicodeCaretsWithoutDuplicatingDelimiters();
        spansPairAndWrapWithoutChangingSurroundingText();
        closingTagsFollowNestingAndAliases();
        suggestionsRespectContextAndPlainFields();
        customVariablesAndStylesKeepTheirIdentity();
        cuesExplainParametersAndStayUnpaired();
        warningsExplainMistakesWithoutRejectingCustomContent();
        return 7;
    }

    private static EditorTextCompletion.Token token(String text) {
        return EditorTextCompletion.token(text, EditorTextEdit.bytes(text), true, false);
    }

    private static EditorTextCompletion.Suggestion choice(String text, String value) {
        return EditorTextCompletion.suggestions(text, token(text), CONTEXT, Set.of()).stream()
                .filter(entry -> entry.value().equals(value)).findFirst().orElseThrow();
    }

    private static void variablesCompleteAtUnicodeCaretsWithoutDuplicatingDelimiters() {
        String text = "Bonjour é 🙂 {pla}!";
        int caret = EditorTextEdit.bytes("Bonjour é 🙂 {pla");
        var token = EditorTextCompletion.token(text, caret, true, false);
        var result = EditorTextCompletion.complete(text, token, choice("{pla", "player"), caret, caret);
        check(result.text().equals("Bonjour é 🙂 {player}!"), "completion replaces only the partial variable, including its existing brace");
        check(result.start() == result.end() && result.end() == EditorTextEdit.bytes("Bonjour é 🙂 {player}"), "caret follows the inserted variable using UTF-8 offsets");
        check(token("Hello {player}") == null && token("Hello") == null, "completed tokens and ordinary prose do not trigger suggestions");
        check(EditorTextCompletion.token("Hello\n{", 7, true, false) != null, "variables can begin on a new line");
    }

    private static void spansPairAndWrapWithoutChangingSurroundingText() {
        var result = EditorTextCompletion.complete("Hello <bo", token("Hello <bo"), choice("<bo", "b"), 9, 9);
        check(result.text().equals("Hello <b></b>") && result.end() == 9, "span completion leaves an empty span with the caret inside");
        var explicit = EditorTextCompletion.token("Hello world!", 11, true, true);
        result = EditorTextCompletion.complete("Hello world!", explicit, choice("<bo", "b"), 6, 11);
        check(result.text().equals("Hello <b>world</b>!") && result.start() == 9 && result.end() == 14, "explicit completion wraps and retains selected text");
        String paired = "<b></b>";
        result = EditorTextCompletion.complete(paired, EditorTextCompletion.token(paired, 2, true, false), choice("<b", "b"), 2, 2);
        check(result.text().equals(paired), "editing an opening tag does not duplicate the existing closing tag");
        String nested = "<b>Outer <bo</b>";
        result = EditorTextCompletion.complete(nested, EditorTextCompletion.token(nested, 12, true, false), choice("<bo", "b"), 12, 12);
        check(result.text().equals("<b>Outer <b></b></b>"), "a new nested span does not steal the outer closing tag");
        String body = "<b>Text</b>";
        result = EditorTextCompletion.complete(body, EditorTextCompletion.token(body, 2, true, false), choice("<b", "b"), 2, 2);
        check(result.text().equals(body), "editing an existing span preserves its body and closing tag");
    }

    private static void closingTagsFollowNestingAndAliases() {
        String text = "<bold>Outer <color:gold>Inner</";
        var choices = EditorTextCompletion.suggestions(text, token(text), CONTEXT, Set.of());
        check(choices.getFirst().value().equals("/color"), "closing completion starts with the innermost span");
        check(EditorTextCompletion.openTags("<bold>Hi</b><pause=20><wave>!", Set.of()).equals(List.of("wave")), "aliases close their matching spans and cues never enter the stack");
        check(EditorTextCompletion.openTags("<custom>Hi", Set.of("custom")).equals(List.of("custom")), "custom styles participate in nesting");
    }

    private static void suggestionsRespectContextAndPlainFields() {
        var quest = new EditorTextCompletion.Context(DefinitionKind.QUEST, "/quests/demo/stages/0", Set.of(), Map.of());
        var choices = EditorTextCompletion.suggestions("{objective", token("{objective"), quest, Set.of());
        check(!choices.isEmpty() && choices.stream().allMatch(entry -> entry.priority() < 2), "quest variables rank for quest fields");
        var item = new EditorTextCompletion.Context(DefinitionKind.TREE, "", Set.of("player_item"), Map.of());
        check(EditorTextCompletion.suggestions("{held", token("{held"), item, Set.of()).getFirst().priority() == 0, "item conditions raise relevant variables");
        check(EditorTextCompletion.token("<b", 2, false, false) == null, "plain labels never suggest unsupported formatting");
        check(EditorTextCompletion.token("{pla", 4, false, false) != null, "plain labels still support variables");
    }

    private static void customVariablesAndStylesKeepTheirIdentity() {
        var context = new EditorTextCompletion.Context(DefinitionKind.CONVERSATION, "", Set.of(), Map.of("pack:score", "17"));
        var variable = EditorTextCompletion.suggestions("{pack", token("{pack"), context, Set.of()).getFirst();
        check(variable.value().equals("pack:score") && variable.help().contains("17"), "supplied custom variables include preview values");
        var style = EditorTextCompletion.suggestions("<whis", token("<whis"), CONTEXT, Set.of("whisper")).getFirst();
        check(style.closing().equals("whisper") && style.category().equals("Custom style"), "custom styles complete as paired spans");
        check(EditorTextCompletion.suggestions("{family", token("{family"), CONTEXT, Set.of()).stream()
                .anyMatch(entry -> entry.help().contains("Standalone trees do not supply")), "availability guidance stays visible for specialized variables");
    }

    private static void cuesExplainParametersAndStayUnpaired() {
        var pause = choice("<pau", "pause=500");
        check(pause.help().contains("milliseconds") && pause.closing().isEmpty(), "pause explains units and has no closing tag");
        var result = EditorTextCompletion.complete("<pau", token("<pau"), pause, 4, 4);
        check(result.text().equals("<pause=500>") && result.end() == 11, "cue completion places the caret after the cue");
        check(choice("<sou", "sound=minecraft:entity.villager.yes volume=1 pitch=1").help().contains("0.5 to 2"), "sound parameters explain their supported ranges");
        check(choice("<color:go", "color:gold").closing().equals("color"), "named colors close with color");
        var typed = EditorTextCompletion.suggestions("<pause=250", token("<pause=250"), CONTEXT, Set.of()).getFirst();
        check(typed.value().equals("pause=250") && typed.closing().isEmpty(), "valid typed cue parameters rank before templates");
        check(EditorTextCompletion.suggestions("<color:#12abef", token("<color:#12abef"), CONTEXT, Set.of()).getFirst().value().equals("color:#12abef"), "typed RGB colors are preserved");
        check(token("2 < 3") == null, "ordinary comparisons do not open formatting suggestions");
    }

    private static void warningsExplainMistakesWithoutRejectingCustomContent() {
        var messages = EditorTextCompletion.warnings("<b>Hello</i> {plaeyr} {my_pack:score}", CONTEXT, Map.of(), true);
        check(messages.stream().anyMatch(message -> message.contains("unmatched")), "markup mistakes are exposed");
        check(messages.stream().anyMatch(message -> message.contains("plaeyr") && message.contains("{player}")), "likely variable typos get a correction");
        check(messages.stream().anyMatch(message -> message.contains("my_pack:score") && message.contains("custom or unknown")), "unknown names remain legal custom variables");
        var info = EditorTextCompletion.inspect("{player}", new EditorTextCompletion.Context(DefinitionKind.TREE, "", Set.of(), Map.of("player", "Alex")), Map.of(), true);
        check(info.getFirst().help().contains("Preview value: Alex"), "token help includes the actual supplied preview value");
    }

    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
