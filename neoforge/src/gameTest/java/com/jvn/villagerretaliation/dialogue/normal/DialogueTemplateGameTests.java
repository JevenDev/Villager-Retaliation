package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueTemplates;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_templates")
@PrefixGameTestTemplate(false)
public final class DialogueTemplateGameTests {
    @GameTest(template = "empty", batch = "dialogue_templates")
    public static void sharedResolverPreservesVrPlaceholderSyntax(GameTestHelper helper) {
        helper.assertValueEqual(DialogueTemplates.resolveTemplate("Hello {player}. {player} <player> {unknown}",
                Map.of("player", "Sam")), "Hello Sam. Sam <player> {unknown}", "only registered brace placeholders resolve");
        helper.assertValueEqual(DialogueTemplates.resolveTemplate("{player}", Map.of("player", "{player}")),
                "{player}", "inserted text is not scanned again for the same token");
        helper.assertValueEqual(DialogueTemplates.resolveTemplate("{word:my_pack:greeting} {player}", Map.of("player", "Sam")),
                "{word:my_pack:greeting} Sam", "word pool resolution remains a separate phase");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_templates")
    public static void sharedResolverPreservesGrammarAndEmptyValues(GameTestHelper helper) {
        helper.assertValueEqual(DialogueTemplates.resolveTemplate("{item}. Then {item}. \"{item}\"?", Map.of("item", "apple")),
                "Apple. Then apple. \"Apple\"?", "item replacements retain sentence capitalization");
        helper.assertValueEqual(DialogueTemplates.resolveTemplate("({gift_item})", Map.of("gift_item", "bread")),
                "(Bread)", "gift items retain their leading punctuation rules");
        var values = new HashMap<String, String>();
        values.put("player", null);
        helper.assertValueEqual(DialogueTemplates.resolveTemplate("Hello {player}!", values),
                "Hello !", "null values still become empty text");
        helper.succeed();
    }
}
