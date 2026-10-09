package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignStandingAuthoringGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:standing");
    private CampaignStandingAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_standing_authoring")
    public static void sharedStandingFieldsMatchRuntimeQuestParsing(GameTestHelper helper) throws IOException {
        try (var stream = CampaignStandingAuthoringGameTests.class.getResourceAsStream("/campaign_fixture/standing_cases.json");
                var reader = new InputStreamReader(java.util.Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            for (var raw : JsonParser.parseReader(reader).getAsJsonArray()) {
                var test = raw.getAsJsonObject();
                var quest = JsonParser.parseString("""
                        {"schema":"villagerretaliation:quest/v2","id":"test:standing","localization_prefix":"test.standing.",
                         "metadata":{"campaign":"campaign_fixture:standing"},"provider":{"type":"villagerretaliation:villager"},
                         "entry_stage":"begin","stages":[{"id":"begin","objectives":[]}]}
                        """).getAsJsonObject();
                var inputs = new com.google.gson.JsonArray();
                inputs.add(test.get("input"));
                if (test.get("kind").getAsString().equals("action"))
                    quest.getAsJsonArray("stages").get(0).getAsJsonObject().add("on_enter", inputs);
                else {
                    var nested = new com.google.gson.JsonObject(); nested.addProperty("type", "not"); nested.add("condition", test.get("input"));
                    inputs.set(0, nested);
                    var availability = new com.google.gson.JsonObject(); availability.add("conditions", inputs); quest.add("availability", availability);
                }
                helper.assertValueEqual(QuestV2Parser.parse(QUEST, quest).isPresent(), test.get("valid").getAsBoolean(), test.get("name").getAsString());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing_authoring")
    public static void sceneStandingActionsKeepTypedFieldsAndPreserveMalformedRequiredEffects(GameTestHelper helper) {
        var source = JsonParser.parseString("""
                {"actions":[{"type":"campaign_standing","scope":"character","subject":"mentor","amount":20,"operation_id":"help"}]}
                """).getAsJsonObject();
        var action = VillagerActionDefinition.readList(QUEST, "test", source, QUEST).getFirst();
        var continuation = new SceneContinuation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                QUEST, UUID.randomUUID(), "/fixture", List.of(action), 0, Map.of());
        var saved = continuation.save();
        helper.assertValueEqual(SceneContinuation.load(saved).actions().getFirst().campaignStanding(), action.campaignStanding(), "typed standing effect survives scene storage");
        saved.getList("Actions", Tag.TAG_COMPOUND).getCompound(0).getCompound("CampaignStanding").putString("Amount", "bad");
        var damaged = SceneContinuation.load(saved);
        helper.assertTrue(damaged.hasUnreadableActions() && damaged.actions().getFirst().required(), "unreadable standing remains a required failure");
        helper.assertValueEqual(damaged.save().getList("Actions", Tag.TAG_COMPOUND), saved.getList("Actions", Tag.TAG_COMPOUND), "raw action stays available for repair");
        source.getAsJsonArray("actions").get(0).getAsJsonObject().remove("operation_id");
        var invalid = VillagerActionDefinition.readList(QUEST, "test", source, QUEST).getFirst();
        helper.assertTrue(invalid.required() && invalid.campaignStanding() == null, "legacy adapter retains invalid required actions");
        helper.succeed();
    }
}
