package com.jvn.villagerretaliation.quest.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_v3")
@PrefixGameTestTemplate(false)
public final class QuestV3CatalogGameTests {
    private QuestV3CatalogGameTests() { }

    @GameTest(template = "empty", batch = "quest_v3")
    public static void isolatedCampaignPublishesWithoutDefaultQuests(GameTestHelper helper) {
        var quest = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v3","id":"quest_v3:example","localization_prefix":"quest_v3.quest.example",
                 "metadata":{"questline":"line"},"provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"start","stages":[{"id":"start","objectives":[],"transition":{"complete":true}}]}
                """).getAsJsonObject();
        var resources = List.of(
                QuestBundleTransactions.RawResource.valid(0, "test", ResourceLocation.parse("quest_v3:quests/line/example/quest.json"), quest),
                QuestBundleTransactions.RawResource.valid(0, "test", ResourceLocation.parse("quest_v3:quests/line/example/locales/en_us.json"), new JsonObject()));
        var server = helper.getLevel().getServer();
        var isolated = new WorldCampaignSelection.Selection(ResourceLocation.parse("quest_v3:isolated"), false);
        var result = QuestContentCatalogs.build(server, resources, isolated);
        helper.assertTrue(result.publishable(), "isolated campaign publishes without built-in quests: " + result.report());
        helper.assertValueEqual(result.catalog().quests().keySet(), Set.of(ResourceLocation.parse("quest_v3:example")), "isolated campaign retains its quest");
        for (var selection : List.of(new WorldCampaignSelection.Selection(null, true),
                new WorldCampaignSelection.Selection(WorldCampaign.DEFAULT, false),
                new WorldCampaignSelection.Selection(isolated.id(), true))) {
            helper.assertFalse(QuestContentCatalogs.build(server, resources, selection).publishable(),
                    "worlds that include default content still require built-in quests");
        }
        helper.succeed();
    }
}
