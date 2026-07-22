package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerInteractionRoutingGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerInteractionRoutingGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void itemFilterHandlerOwnsStableOptionContract(GameTestHelper helper) {
        List<DialogueOptionDefinition> options = VillagerItemFilterInteractionHandler.options();
        helper.assertValueEqual(options.size(), 3, "item-filter option count");
        helper.assertValueEqual(
                options.stream().map(DialogueOptionDefinition::id).toList(),
                List.of(
                        VillagerItemFilterInteractionHandler.ALLOWLIST_OPTION_ID,
                        VillagerItemFilterInteractionHandler.DENYLIST_OPTION_ID,
                        VillagerItemFilterInteractionHandler.NEVERMIND_OPTION_ID),
                "item-filter option ids");
        helper.assertValueEqual(
                options.stream().map(DialogueOptionDefinition::order).toList(),
                List.of(0, 1, 2),
                "item-filter option order");
        helper.assertTrue(
                options.stream().allMatch(option -> option.requestType() == DialogueRequestType.QUESTION),
                "item-filter options must remain question requests");
        helper.assertTrue(
                options.stream().allMatch(option -> VillagerItemFilterInteractionHandler.handlesOption(option.id())),
                "item-filter handler must recognize every option it presents");
        helper.assertFalse(
                VillagerItemFilterInteractionHandler.handlesOption("construction_blueprint_start"),
                "item-filter handler claimed another interaction namespace");
        helper.assertFalse(
                VillagerItemFilterInteractionHandler.handlesOption(null),
                "item-filter handler claimed a missing option id");
        helper.succeed();
    }
}
