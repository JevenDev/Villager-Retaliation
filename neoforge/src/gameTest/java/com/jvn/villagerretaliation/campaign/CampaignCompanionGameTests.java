package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartySavedData;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignCompanionGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("campaign_fixture:characters");

    private CampaignCompanionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_companions")
    public static void companionsRequireTheBoundIdentityNearbyAndAnActivePartyContract(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var leader = fixture.story.player();
            var member = fixture.story.player();
            var outsider = fixture.story.player();
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            helper.assertFalse(fixture.matches(leader), "an eligible villager is not automatically cast by a condition");
            helper.assertFalse(CampaignCharacterSavedData.get(helper.getLevel()).bindings().containsKey(
                    new CampaignCharacterBinding.Key(CAMPAIGN, "companion")), "read-only condition leaves the role unbound");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "query never loads character chunks");
            fixture.bind();
            helper.assertFalse(fixture.matches(leader), "binding a role does not recruit it");
            fixture.recruit(leader);
            fixture.join(leader, member);
            helper.assertTrue(fixture.matches(leader), "recruited companion belongs to the leader");
            helper.assertTrue(fixture.matches(member), "ordinary party members share the companion");
            helper.assertFalse(fixture.matches(outsider), "nearby outsider does not gain a companion");
            leader.setPos(fixture.companion.getX() + 8, fixture.companion.getY(), fixture.companion.getZ());
            helper.assertTrue(fixture.matches(leader), "authored radius includes its boundary");
            leader.setPos(leader.getX() + 0.01, leader.getY(), leader.getZ());
            helper.assertFalse(fixture.matches(leader), "companion outside the radius is absent");
            fixture.companion.hurt(fixture.companion.damageSources().genericKill(), 1000);
            helper.assertFalse(fixture.matches(member), "downed companion cannot interject");
            VillagerDownedService.recover(fixture.companion);
            helper.assertTrue(fixture.matches(member), "recovery restores the same companion");
            var parties = PartySavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            var record = parties.getList("Parties", Tag.TAG_COMPOUND).getCompound(0).getList("Villagers", Tag.TAG_COMPOUND).getCompound(0);
            record.putLong("ContractStart", 0); record.putLong("ContractEnd", 0);
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_parties",
                    PartySavedData.load(parties, helper.getLevel().registryAccess()));
            var expired = PartySavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            helper.assertFalse(fixture.matches(member), "expired contract stops matching before maintenance removes the roster entry");
            helper.assertValueEqual(PartySavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()),
                    expired, "condition does not perform contract maintenance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_companions")
    public static void unloadedCompanionsRetainIdentityWithoutBeingRecast(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var player = fixture.story.player();
            fixture.bind(); fixture.recruit(player);
            var binding = CampaignCharacterSavedData.get(helper.getLevel()).forEntity(fixture.companion.getUUID());
            var saved = fixture.companion.saveWithoutId(new CompoundTag());
            fixture.companion.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.story.base.reload();
            var substitute = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
            try {
                helper.assertFalse(fixture.matches(player), "an unloaded companion cannot interject");
                helper.assertValueEqual(CampaignCharacterSavedData.get(helper.getLevel()).forEntity(binding.entityId()), binding,
                        "absence preserves the original binding without recording death");
                var restored = EntityType.VILLAGER.create(helper.getLevel()); restored.load(saved);
                helper.assertTrue(helper.getLevel().addFreshEntity(restored), "companion returns with the saved UUID");
                try {
                    helper.assertTrue(fixture.matches(player), "same identity and party contract resume after reload");
                    player.setPos(player.getX(), 70, player.getZ());
                    restored.setPos(player.getX(), player.getY(), player.getZ());
                    var nether = fixture.story.base.server.getLevel(net.minecraft.world.level.Level.NETHER);
                    helper.assertTrue(restored.teleportTo(nether, restored.getX(), restored.getY(), restored.getZ(), java.util.Set.of(), 0, 0), "companion moves to another dimension");
                    helper.assertFalse(fixture.matches(player), "companion in another dimension is absent despite matching coordinates");
                } finally {
                    for (var level : fixture.story.base.server.getAllLevels()) {
                        var entity = level.getEntity(binding.entityId());
                        if (entity != null) entity.discard();
                    }
                }
            } finally {
                substitute.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_companions")
    public static void companionInterjectionsSurviveHandoverAndSkipDismissedSpeakers(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.interjection(1); fixture.interjection(2);
            fixture.story.install(fixture.story.graph);
            var leader = fixture.story.player(); var member = fixture.story.player();
            fixture.bind(); fixture.recruit(leader); fixture.join(leader, member);
            var result = fixture.story.start(leader);
            result = fixture.story.intent(member, result, ConversationSession.Intent.JOIN, "");
            UUID request = UUID.randomUUID(); long revision = result.session().revision();
            UUID session = result.session().identity().id();
            result = ConversationService.request(leader, session, revision, request, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().transcript().getLast().speaker(), fixture.companion.getUUID(), "bound companion speaks");
            var replay = ConversationService.request(leader, session, revision, request, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(replay.session().transcript(), result.session().transcript(), "duplicate advance cannot repeat an interjection");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.story.intent(leader, result, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            helper.assertTrue(PartyVillagerContractService.dismiss(leader, fixture.companion).success(), "leader dismisses the companion");
            fixture.story.base.reload(); fixture.story.storage.reload();
            result = ConversationService.view(member, session);
            helper.assertValueEqual(result.session().transcript().getLast().speaker(), fixture.companion.getUUID(), "saved speech survives dismissal and reload");
            result = fixture.story.intent(member, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.diagnostic().isBlank(), "dismissed optional speaker does not block the next response");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "future interjection is skipped");
            helper.assertValueEqual(exchanges(result), List.of("mentor", "player"), "only the previously committed interjection remains");
            var report = CampaignReferenceReport.build(CampaignResources.current(fixture.story.base.server));
            helper.assertTrue(report.references().stream().anyMatch(reference ->
                    reference.relation() == CampaignReferenceReport.Relation.ACCOMPANIES
                    && reference.to().path().equals(List.of("companion"))
                    && reference.source().pointer().equals("/nodes/0/exchanges/1/conditions/0/role")), "dependency report identifies companion line");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_companions")
    public static void absentCompanionLinesStaySkippedAfterTheCharacterReturns(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.interjection(1); fixture.story.install(fixture.story.graph);
            var player = fixture.story.player(); fixture.bind(); fixture.recruit(player);
            fixture.companion.setPos(fixture.companion.getX() + 20, fixture.companion.getY(), fixture.companion.getZ());
            var result = fixture.story.start(player);
            result = fixture.story.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(exchanges(result), List.of("mentor", "pause"), "distant companion line is omitted");
            fixture.companion.setPos(player.getX() + 1, player.getY(), player.getZ());
            fixture.story.storage.reload();
            result = ConversationService.view(player, result.session().identity().id());
            helper.assertValueEqual(exchanges(result), List.of("mentor", "pause"), "returning companion does not insert an earlier line");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_companions")
    public static void failedInterjectionSaveKeepsItsSelectionDuringContractChanges(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.interjection(1); fixture.story.install(fixture.story.graph);
            var player = fixture.story.player(); fixture.bind(); fixture.recruit(player);
            var result = fixture.story.start(player);
            fixture.story.storage.blockWrites();
            var blocked = fixture.story.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(blocked.diagnostic().isBlank(), "failed storage blocks publication");
            helper.assertValueEqual(exchanges(blocked), List.of("mentor"), "uncommitted interjection is not displayed");
            helper.assertTrue(PartyVillagerContractService.dismiss(player, fixture.companion).success(), "contract changes while storage is blocked");
            fixture.story.storage.restoreWrites();
            ConversationTestStorage.maintain(fixture.story.base.server);
            fixture.story.storage.reload();
            result = ConversationService.view(player, result.session().identity().id());
            helper.assertValueEqual(exchanges(result), List.of("mentor", "player"), "recovery retains the selected line");
            helper.assertValueEqual(result.session().transcript().getLast().speaker(), fixture.companion.getUUID(), "recovery retains the selected speaker");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_companions")
    public static void companionConditionsWorkWithoutALiveQuestIssuerAndBlockMissingContent(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var player = fixture.story.player();
            CampaignConversationBargainingGameTests.startQuest(fixture.story, player);
            fixture.bind(); fixture.recruit(player);
            fixture.story.mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var result = VillagerQuestService.debugInspectQuest(player, CAMPAIGN);
            helper.assertTrue(result.lines().stream().anyMatch(line -> line.contains("active_conditions=met")), "issuer unload does not hide a present companion");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.companion);
            var query = new DialogueCondition.CampaignCompanion(new CampaignCompanionPredicate(CAMPAIGN, "companion", 8));
            var catalog = CampaignResources.current(fixture.story.base.server);
            CampaignResources.installForTests(fixture.story.base.server, new CampaignContentCatalog(catalog.quests(), Map.of(), List.of()));
            helper.assertFalse(query.matches(context), "missing campaign remains unavailable");
            helper.assertFalse(new DialogueCondition.Not(query).matches(context), "negation cannot bypass unavailable content");
            helper.assertFalse(query.unavailableReason(context).isBlank(), "missing content has a diagnostic");
            CampaignResources.installForTests(fixture.story.base.server, catalog);
            fixture.story.base.reload();
            helper.assertTrue(query.matches(context), "restored content keeps the original party identity");
        }
        helper.succeed();
    }

    private static List<String> exchanges(ConversationService.Result result) {
        return result.session().transcript().stream().map(ConversationSession.Line::exchange).toList();
    }

    private static JsonObject condition() {
        return JsonParser.parseString("{\"type\":\"campaign_companion\",\"campaign\":\"campaign_fixture:characters\",\"role\":\"companion\"}").getAsJsonObject();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignConversationGameTests.Fixture story;
        final Villager companion;

        Fixture(GameTestHelper helper) throws IOException {
            this.helper = helper;
            story = new CampaignConversationGameTests.Fixture(helper, false, false, false, campaign -> {
                var role = campaign.getAsJsonArray("characters").get(0).getAsJsonObject().deepCopy();
                role.addProperty("id", "companion"); campaign.getAsJsonArray("characters").add(role);
            }, quest -> {
                var active = new JsonObject(); var conditions = new JsonArray(); conditions.add(condition());
                active.add("conditions", conditions); active.addProperty("hide_when_unmet", false); active.addProperty("pause_progress_when_unmet", false);
                quest.getAsJsonObject("availability").add("active", active);
                var stage = quest.getAsJsonArray("stages").get(0).getAsJsonObject();
                stage.remove("on_enter");
                stage.add("objectives", JsonParser.parseString("[{\"id\":\"decision\",\"type\":\"choice\"}]"));
            });
            companion = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
            companion.setNoAi(true); companion.setAge(0);
        }

        void bind() {
            helper.assertValueEqual(CampaignCharacterService.resolve(story.base.server, CAMPAIGN, "companion").entity(), companion, "authored role binds the companion");
        }

        void recruit(ServerPlayer player) {
            VillagerReputationManager.setReputation(helper.getLevel(), companion, player.getUUID(), 0);
            player.getInventory().add(new ItemStack(Items.EMERALD, 64));
            var result = PartyVillagerContractService.recruit(player, companion);
            helper.assertTrue(result.success(), "companion joins existing party system: " + result.messageKey());
        }

        void join(ServerPlayer leader, ServerPlayer member) {
            var invitation = PartyService.sendInvitation(leader, member);
            helper.assertTrue(PartyService.acceptInvitation(member, invitation.invitationId()).success(), "member joins companion's party");
        }

        boolean matches(ServerPlayer player) {
            return new CampaignCompanionPredicate(CAMPAIGN, "companion", 8).evaluate(helper.getLevel(), player).matches();
        }

        void interjection(int index) {
            var exchange = story.graph.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges").get(index).getAsJsonObject();
            exchange.add("speaker", JsonParser.parseString("{\"type\":\"role\",\"role\":\"companion\"}"));
            var conditions = new JsonArray(); conditions.add(condition()); exchange.add("conditions", conditions);
        }

        @Override public void close() throws IOException {
            companion.discard();
            story.close();
        }
    }
}
