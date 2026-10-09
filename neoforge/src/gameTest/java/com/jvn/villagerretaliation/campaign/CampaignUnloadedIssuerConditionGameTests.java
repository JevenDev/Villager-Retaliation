package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.CampaignConversationBargainingGameTests.startQuest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignUnloadedIssuerConditionGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("campaign_fixture:characters");

    private CampaignUnloadedIssuerConditionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_unloaded_conditions")
    public static void savedCampaignConditionsRemainPersonalAfterReloadAndIssuerUnload(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, """
                [{"type":"campaign_fact","campaign":"campaign_fixture:characters","fact":"clue","value":"true"},
                 {"type":"campaign_fact","campaign":"campaign_fixture:characters","fact":"gate","value":"true"},
                 {"type":"campaign_outcome","campaign":"campaign_fixture:characters","milestone":"pledge","outcome":"help","scope":"completed"},
                 {"type":"campaign_standing","campaign":"campaign_fixture:characters","scope":"character","subject":"mentor","reaction":"trusted"},
                 {"type":"campaign_standing","campaign":"campaign_fixture:characters","scope":"faction","subject":"wardens","value":10,"comparison":"at_least"},
                 {"type":"campaign_transaction","campaign":"campaign_fixture:characters","quest":"campaign_fixture:characters","transaction":"supplies","state":"paid"},
                 {"type":"campaign_character","campaign":"campaign_fixture:characters","role":"mentor","state":"bound"},
                 {"type":"not","condition":{"type":"campaign_character","campaign":"campaign_fixture:characters","role":"mentor","state":"lost"}},
                 {"type":"campaign_location","campaign":"campaign_fixture:characters","location":"home","radius":12}]
                """); var standing = new CampaignStandingTestStorage(helper.getLevel());
                var transactions = new CampaignTransactionTestStorage(helper.getLevel())) {
            var first = fixture.player(); var second = fixture.player();
            startQuest(fixture, first); startQuest(fixture, second);
            assertState(helper, first, "unmet");
            first.getInventory().items.set(0, new ItemStack(Items.EMERALD, 8));
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), first, fixture.mentor);
            var actions = VillagerActionDefinition.readList(QUEST, "unloaded condition fixture", JsonParser.parseString("""
                    {"actions":[{"type":"campaign_fact","fact":"clue","value":"true","operation_id":"discover"},
                     {"type":"campaign_standing","scope":"character","subject":"mentor","amount":20,"operation_id":"help"},
                     {"type":"campaign_standing","scope":"faction","subject":"wardens","amount":20,"operation_id":"help_wardens"},
                     {"type":"campaign_payment","transaction":"supplies"}]}
                    """).getAsJsonObject(), QUEST);
            helper.assertValueEqual(actions.size(), 4, "all fixture effects compile");
            for (var action : actions) {
                var result = VillagerActionRegistry.execute(context, action, Map.of());
                helper.assertTrue(result.success(), "fixture effect applies: " + result.message());
            }
            var data = CampaignSavedData.get(helper.getLevel());
            var definition = CampaignResources.current(fixture.base.server).campaigns().get(QUEST).definition();
            helper.assertValueEqual(data.commitFact(definition, first.getUUID(), "gate", CampaignFacts.Mutation.SET, "true",
                    UUID.randomUUID(), data.instances().get(QUEST).worldRevision(), helper.getLevel().getGameTime()).status(),
                    CampaignSavedData.Status.APPLIED, "shared fact commits");
            data.enterMilestone(definition, first.getUUID(), "pledge", helper.getLevel().getGameTime());
            helper.assertValueEqual(data.completeMilestone(definition, first.getUUID(), "pledge", CampaignInstance.Route.ORIGINAL,
                    "help", UUID.randomUUID(), helper.getLevel().getGameTime()).status(), CampaignSavedData.Status.APPLIED,
                    "personal outcome commits");
            assertState(helper, first, "met");
            fixture.base.reload(); standing.reload(); transactions.reload();
            fixture.mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var snapshot = CampaignSavedData.get(helper.getLevel()).instances().get(QUEST);
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            assertState(helper, first, "met");
            assertState(helper, second, "unmet");
            assertState(helper, first, "met");
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances().get(QUEST), snapshot,
                    "evaluating conditions does not change story progress");
            helper.assertValueEqual(standing.data().receipts().size(), 2, "reading standing creates no extra receipts");
            helper.assertValueEqual(transactions.data().receipts().size(), 1, "reading payment never charges again");
            helper.assertValueEqual(first.getInventory().countItem(Items.EMERALD), 0, "only the original payment consumed currency");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "conditions never load issuer chunks");
            first.setPos(first.getX() + 20, first.getY(), first.getZ());
            assertState(helper, first, "unmet");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_unloaded_conditions")
    public static void negatedFactsStayUnknownWhenCampaignStateIsUnavailable(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, """
                [{"type":"not","condition":{"type":"campaign_fact","campaign":"campaign_fixture:characters","fact":"clue","value":"true"}}]
                """)) {
            var player = fixture.player(); startQuest(fixture, player);
            fixture.mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            assertState(helper, player, "met");
            for (String mode : List.of("sandbox", "legacy")) {
                fixture.base.mode(mode);
                assertState(helper, player, "unknown");
            }
            fixture.base.mode("campaign");
            var content = CampaignResources.current(fixture.base.server);
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(content.quests(), Map.of(), List.of()));
            assertState(helper, player, "unknown");
            CampaignResources.installForTests(fixture.base.server, content);
            assertState(helper, player, "met");
            var raw = CampaignSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            raw.putInt("DataVersion", 99);
            fixture.base.server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(raw, fixture.base.disk.root));
            assertState(helper, player, "unknown");
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), raw,
                    "reading unavailable conditions preserves dormant state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_unloaded_conditions")
    public static void speakerChecksWaitForTheLiveIssuerEvenWhenNegated(GameTestHelper helper) throws IOException {
        for (boolean negate : List.of(false, true)) {
            String speaker = "{\"type\":\"campaign_character\",\"campaign\":\"campaign_fixture:characters\",\"role\":\"mentor\",\"state\":\"speaker\"}";
            String condition = negate ? "{\"type\":\"not\",\"condition\":" + speaker + "}" : speaker;
            try (var fixture = fixture(helper, "[" + condition + "]")) {
                var player = fixture.player(); startQuest(fixture, player);
                assertState(helper, player, negate ? "unmet" : "met");
                var saved = fixture.mentor.saveWithoutId(new CompoundTag());
                fixture.mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                assertState(helper, player, "unknown");
                var restored = EntityType.VILLAGER.create(helper.getLevel()); restored.load(saved);
                helper.assertTrue(helper.getLevel().addFreshEntity(restored), "issuer reloads with its saved identity");
                try {
                    assertState(helper, player, negate ? "unmet" : "met");
                } finally {
                    restored.discard();
                }
            }
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper, String conditions) throws IOException {
        return new CampaignConversationGameTests.Fixture(helper, false, false, false, campaign -> {
            campaign.add("facts", JsonParser.parseString("""
                    [{"id":"clue","scope":"player","kind":"tag"},{"id":"gate","scope":"world","kind":"tag"}]
                    """));
            campaign.add("milestones", JsonParser.parseString("""
                    [{"id":"pledge","chapter":"arrival","quest":"campaign_fixture:characters","stage":"visit","irreversible":false,
                      "outcomes":[{"id":"help","title_key":"campaign_fixture.campaign.characters.mentor"}]}]
                    """));
            campaign.add("transactions", JsonParser.parseString("""
                    [{"id":"supplies","title_key":"campaign_fixture.campaign.characters.mentor","quest":"campaign_fixture:characters",
                      "character":"mentor","payment":{"item":"minecraft:emerald","count":8}}]
                    """));
        }, quest -> {
            var active = new JsonObject();
            active.add("conditions", JsonParser.parseString(conditions));
            active.addProperty("hide_when_unmet", false);
            active.addProperty("pause_progress_when_unmet", false);
            quest.getAsJsonObject("availability").add("active", active);
            var stage = quest.getAsJsonArray("stages").get(0).getAsJsonObject();
            stage.remove("on_enter");
            stage.add("objectives", JsonParser.parseString("[{\"id\":\"decision\",\"type\":\"choice\"}]"));
        });
    }

    private static void assertState(GameTestHelper helper, ServerPlayer player, String expected) {
        var result = VillagerQuestService.debugInspectQuest(player, QUEST);
        helper.assertTrue(result.lines().stream().anyMatch(line -> line.contains("active_conditions=" + expected)),
                "expected " + expected + " without changing quest progress: " + result.lines());
    }
}
