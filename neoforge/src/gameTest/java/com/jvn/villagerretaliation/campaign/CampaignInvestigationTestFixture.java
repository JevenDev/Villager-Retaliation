package com.jvn.villagerretaliation.campaign;

import com.mojang.authlib.GameProfile;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartySavedData;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.npc.Villager;

final class CampaignInvestigationTestFixture implements AutoCloseable {
    final GameTestHelper helper;
    final CampaignCharacterTestFixture base;
    final CampaignEvidenceSavedData priorEvidence;
    final PartySavedData priorParties;
    final Villager mentor;
    final List<ServerPlayer> players = new ArrayList<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final List<Interaction> sources = new ArrayList<>();

    CampaignInvestigationTestFixture(GameTestHelper helper) throws IOException {
        this(helper, quest -> { });
    }

    CampaignInvestigationTestFixture(GameTestHelper helper, java.util.function.Consumer<com.google.gson.JsonObject> questContent) throws IOException {
        this.helper = helper;
        base = new CampaignCharacterTestFixture(helper, "investigation", "investigation_greeting", List.of(),
                questContent, campaign -> { }, List.of("conversations/ledger.json"));
        priorEvidence = CampaignEvidenceSavedData.get(helper.getLevel());
        priorParties = PartySavedData.get(helper.getLevel());
        base.server.overworld().getDataStorage().set(CampaignEvidenceSavedData.DATA_NAME,
                CampaignEvidenceSavedData.load(new CompoundTag(), base.disk.root));
        base.server.overworld().getDataStorage().set("villagerretaliation_parties", new PartySavedData());
        mentor = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        mentor.setNoAi(true);
        helper.assertValueEqual(base.resolve().entity(), mentor, "fixture binds its mentor");
    }

    Interaction source(int x, int z) {
        var source = helper.spawn(EntityType.INTERACTION, x, 1, z);
        source.addTag("campaign_fixture:ledger");
        sources.add(source);
        return source;
    }

    ServerPlayer player() {
        var profile = new GameProfile(UUID.randomUUID(), "inspect_" + UUID.randomUUID().toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(base.server, helper.getLevel(), profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        base.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setPos(mentor.getX() + 1, mentor.getY(), mentor.getZ());
        players.add(player);
        return player;
    }

    void accept(ServerPlayer player) {
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, mentor);
        base.select(context, "inspect", "offer");
        VillagerConversationService.endForPlayer(player, false);
        helper.assertTrue(context(player).progress() != null && context(player).progress().questRunId() != null,
                "authored offer starts the investigation quest");
    }

    void party(ServerPlayer leader, ServerPlayer member) {
        helper.assertTrue(PartyService.createParty(leader).success(), "fixture party created");
        var invitation = PartyService.sendInvitation(leader, member);
        helper.assertTrue(PartyService.acceptInvitation(member, invitation.invitationId()).success(), "fixture party joined");
    }

    QuestObjectiveEvaluationContext context(ServerPlayer player) {
        var quest = base.catalog.quest(base.campaignId).orElseThrow();
        return new QuestObjectiveEvaluationContext(player, null, player.serverLevel(), quest,
                VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest.id()),
                null, null, null, null, null, null);
    }

    boolean complete(ServerPlayer player) {
        var context = context(player);
        return QuestObjectiveRegistry.evaluate(context, context.definition().objectives().getFirst()).orElseThrow().complete();
    }

    CampaignEvidenceSavedData data() { return CampaignEvidenceSavedData.get(helper.getLevel()); }

    CampaignEvidenceService.Resolution resolve() {
        return CampaignEvidenceService.resolve(base.server, base.campaignId, "ledger");
    }

    CompoundTag saved() { return data().save(new CompoundTag(), helper.getLevel().registryAccess()); }

    void reload() throws IOException { reload(base.disk.read(CampaignEvidenceSavedData.DATA_NAME)); }

    void reload(CompoundTag raw) {
        base.server.overworld().getDataStorage().set(CampaignEvidenceSavedData.DATA_NAME,
                CampaignEvidenceSavedData.load(raw, base.disk.root));
    }

    void blockWrites() throws IOException {
        var file = base.disk.root.resolve(CampaignEvidenceSavedData.DATA_NAME + ".dat");
        if (Files.exists(file)) Files.move(file, base.disk.root.resolve("previous-evidence.dat"));
        Files.createDirectory(file);
    }

    void restoreWrites() throws IOException {
        Files.delete(base.disk.root.resolve(CampaignEvidenceSavedData.DATA_NAME + ".dat"));
        var previous = base.disk.root.resolve("previous-evidence.dat");
        if (Files.exists(previous)) Files.move(previous, base.disk.root.resolve(CampaignEvidenceSavedData.DATA_NAME + ".dat"));
    }

    @Override public void close() throws IOException {
        for (var player : players) {
            com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService.onLogout(
                    new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            VillagerConversationService.endForPlayer(player, false);
            PartyService.leaveParty(player);
            base.server.getPlayerList().remove(player);
        }
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        sources.forEach(Interaction::discard);
        mentor.discard();
        base.server.overworld().getDataStorage().set(CampaignEvidenceSavedData.DATA_NAME, priorEvidence);
        base.server.overworld().getDataStorage().set("villagerretaliation_parties", priorParties);
        Files.deleteIfExists(base.disk.root.resolve(CampaignEvidenceSavedData.DATA_NAME + ".dat"));
        base.close();
    }
}
