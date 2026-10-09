package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.skill.player.PlayerSocialAward;
import com.jvn.villagerretaliation.skill.player.PlayerSocialProgressionSavedData;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import com.jvn.villagerretaliation.skill.player.SocialCheckBreakdown;
import com.jvn.villagerretaliation.skill.player.SocialProgressionResources;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

public record CampaignConversationClientFixture(UUID session, UUID character, int entity) {
    public static CampaignConversationClientFixture prepare(ServerPlayer player) throws IOException {
        var level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.setDayTime(6000);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        }
        player.connection.teleport(2.5, 1, 3.5, 135, 0);
        var resources = new ArrayList<QuestBundleTransactions.RawResource>();
        resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id(SocialProgressionTestData.PATH), SocialProgressionTestData.profile()));
        for (String file : List.of("quest.json", "locales/en_us.json", "scenes/character_greeting.json", "conversations/mentor.json")) {
            String path = "quests/campaign_fixture/characters/" + file;
            var raw = CampaignCharacterTestFixture.resource(path);
            if (file.equals("quest.json")) {
                var stage = raw.getAsJsonArray("stages").get(0).getAsJsonObject();
                stage.remove("on_enter");
                stage.add("objectives", com.google.gson.JsonParser.parseString("[{\"id\":\"decide\",\"type\":\"choice\"}]"));
            } else if (file.equals("conversations/mentor.json")) {
                var social = SocialProgressionTestData.resource("quests/campaign_fixture/characters/conversations/context_mentor.json");
                raw.add("checks", social.getAsJsonArray("checks").deepCopy());
                var node = raw.getAsJsonArray("nodes").get(0).getAsJsonObject();
                node.add("effects", com.google.gson.JsonParser.parseString("""
                        [{"id":"before_acceptance","action":{"type":"experience","amount":7}},
                         {"id":"accept","action":{"type":"quest","quest":"campaign_fixture:characters","action":"start","required":true}}]
                        """));
                node.getAsJsonArray("responses").get(0).getAsJsonObject().add("effects", com.google.gson.JsonParser.parseString("""
                        [{"id":"before_decision","action":{"type":"experience","amount":13}},
                         {"id":"decide","action":{"type":"campaign_outcome","quest":"campaign_fixture:characters",
                          "milestone":"decision","outcome":"help","operation_id":"mentor_decision"}}]
                        """));
            }
            resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id(path), raw));
        }
        var bundles = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
        require(bundles.diagnostics().isEmpty(), "client fixture bundle compiles: " + bundles.diagnostics());
        var quests = VillagerQuestResources.snapshotForCatalog(player.server, bundles);
        var sceneId = id("character_greeting");
        var scene = SceneCompiler.compile(SceneParser.parse(sceneId, CampaignCharacterTestFixture.resource("quests/campaign_fixture/characters/scenes/character_greeting.json")).resource());
        require(scene.scene() != null, "client fixture scene compiles");
        var progressions = SocialProgressionResources.compile(bundles);
        require(progressions.diagnostics().isEmpty(), "client fixture progression compiles");
        var conversations = ConversationResources.compile(bundles, Set.of(sceneId), quests.quests().keySet(), progressions.definitions());
        require(conversations.diagnostics().values().stream().allMatch(List::isEmpty), "client fixture conversations compile: " + conversations.diagnostics());
        var prior = QuestContentCatalogs.current(player.server);
        var catalog = new QuestContentCatalog(prior.generation() + 1, quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(),
                quests.objectiveEventQuestIds(), quests.factQuestIds(), quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(), quests.triggerEventQuestIds(),
                Map.of(sceneId, scene.scene()), Map.of(), List.of(), bundles.bundles(), bundles.localization(), QuestRewardCatalog.fromBundles(bundles.bundles()), conversations.conversations(), progressions.definitions());
        QuestContentCatalogs.installForTests(player.server, catalog, QuestContentCatalogs.loadReport(player.server));
        var campaign = CampaignCharacterTestFixture.resource("campaigns/characters/campaign.json");
        campaign.add("milestones", com.google.gson.JsonParser.parseString("""
                [{"id":"decision","chapter":"arrival","quest":"campaign_fixture:characters","stage":"visit","irreversible":true,
                  "outcomes":[{"id":"help","title_key":"campaign_fixture.campaign.characters.help"},
                              {"id":"leave","title_key":"campaign_fixture.campaign.characters.leave"}],
                  "catch_up":{"quest":"campaign_fixture:characters","stage":"report"}}]
                """));
        var location = campaign.getAsJsonArray("locations").get(0).getAsJsonObject();
        location.addProperty("source", "marker"); location.remove("search_radius");
        var origin = new JsonObject(); origin.addProperty("x", 0); origin.addProperty("y", 1); origin.addProperty("z", 0);
        location.add("origin", origin);
        var locale = QuestLocaleCatalog.read(CampaignCharacterTestFixture.resource("campaigns/characters/locales/en_us.json"));
        var campaigns = CampaignContentCatalog.compile(catalog, List.of(new CampaignContentCatalog.Source(id("campaigns/characters/campaign.json"), "campaign_fixture",
                campaign, QuestLocaleCatalog.empty().overlay("en_us", locale.messages()).overlay("en_us", Map.of(
                        "campaign_fixture.campaign.characters.help", new com.google.gson.JsonPrimitive("The mentor will help the village"),
                        "campaign_fixture.campaign.characters.leave", new com.google.gson.JsonPrimitive("The village must find another guide"))), List.of())));
        require(campaigns.diagnostics().isEmpty(), "client fixture campaign compiles: " + campaigns.diagnostics());
        CampaignResources.installForTests(player.server, campaigns);
        var mode = new CompoundTag(); mode.putInt("DataVersion", 1); mode.putString("Mode", "campaign");
        player.server.overworld().getDataStorage().set(CampaignWorldModeSavedData.DATA_NAME, CampaignWorldModeSavedData.load(mode, level.registryAccess()));
        var mentor = EntityType.VILLAGER.create(level);
        mentor.setPos(0.5, 1, 0.5); mentor.setNoAi(true);
        level.addFreshEntity(mentor);
        var binding = CampaignCharacterService.resolve(player.server, id("characters"), "mentor");
        require(binding.available() && binding.entity() == mentor, "fixture character binds");
        PlayerSocialProgressionSavedData.get(level).grant(new PlayerSocialAward(UUID.randomUUID(), "training", UUID.randomUUID(), player.getUUID(),
                SocialCheckBreakdown.capture(progressions.definitions().get(SocialProgressionTestData.ID), PlayerSocialSkill.PERSUASION, 0, 10)));
        var started = ConversationService.start(player, id("mentor_conversation"), "guidance", UUID.randomUUID());
        require(started.session() != null, "fixture conversation starts: " + started.diagnostic());
        return new CampaignConversationClientFixture(started.session().identity().id(), mentor.getUUID(), mentor.getId());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
}
