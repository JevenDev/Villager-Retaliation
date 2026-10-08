package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartySavedData;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.scene.SceneLaunchService;
import com.jvn.villagerretaliation.scene.SceneRuntime;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

final class CampaignFollowTestFixture implements AutoCloseable {
    final GameTestHelper helper;
    final CampaignCharacterTestFixture base;
    final CampaignFollowSavedData priorJourneys;
    final PartySavedData priorParties;
    final Villager mentor;
    final BlockPos destination;
    final boolean partyScene;
    final List<ServerPlayer> players = new ArrayList<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final Map<BlockPos, BlockState> terrain = new LinkedHashMap<>();
    final byte[] priorSceneFile;
    UUID sceneId;
    long time;

    CampaignFollowTestFixture(GameTestHelper helper) throws IOException { this(helper, false); }

    CampaignFollowTestFixture(GameTestHelper helper, boolean partyScene) throws IOException {
        this.helper = helper;
        this.partyScene = partyScene;
        destination = helper.absolutePos(new BlockPos(7, 1, 1));
        base = new CampaignCharacterTestFixture(helper, "following", "mentor_walk", List.of(), quest -> {
            if (partyScene) quest.getAsJsonArray("stages").get(0).getAsJsonObject().remove("on_enter");
        }, campaign -> {
            var origin = new JsonObject();
            origin.addProperty("x", destination.getX()); origin.addProperty("y", destination.getY()); origin.addProperty("z", destination.getZ());
            campaign.getAsJsonArray("locations").get(1).getAsJsonObject().add("origin", origin);
        }, List.of(), scene -> {
            if (partyScene) scene.addProperty("ownership", "party");
        });
        priorJourneys = CampaignFollowSavedData.get(helper.getLevel());
        priorParties = PartySavedData.get(helper.getLevel());
        base.server.overworld().getDataStorage().set(CampaignFollowSavedData.DATA_NAME,
                CampaignFollowSavedData.load(new CompoundTag(), base.disk.root));
        base.server.overworld().getDataStorage().set("villagerretaliation_parties", new PartySavedData());
        priorSceneFile = Files.exists(scenePath()) ? Files.readAllBytes(scenePath()) : null;
        for (int x = 0; x <= 9; x++) for (int z = 0; z <= 3; z++) for (int y = 0; y <= 3; y++) {
            var position = helper.absolutePos(new BlockPos(x, y, z));
            terrain.put(position, helper.getLevel().getBlockState(position));
            helper.getLevel().setBlock(position, y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        mentor = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        mentor.setNoAi(true);
        mentor.setOnGround(true);
        helper.assertValueEqual(base.resolve().entity(), mentor, "fixture binds the guide");
        BuiltinSceneStepExecutors.register();
        time = helper.getLevel().getGameTime();
    }

    ServerPlayer player() { return player(new GameProfile(UUID.randomUUID(), "follow_" + UUID.randomUUID().toString().substring(0, 8))); }

    ServerPlayer reconnect(ServerPlayer previous) { return player(previous.getGameProfile()); }

    private ServerPlayer player(GameProfile profile) {
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(base.server, helper.getLevel(), profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        base.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setPos(mentor.getX(), mentor.getY(), mentor.getZ() + 1);
        players.add(player);
        return player;
    }

    void accept(ServerPlayer player) {
        var dialogue = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, mentor);
        base.select(dialogue, "follow", "offer");
        VillagerConversationService.endForPlayer(player, false);
        var progress = objectiveContext(player).progress();
        helper.assertTrue(progress != null && progress.questRunId() != null, "authored offer starts the follow quest");
        if (!partyScene) sceneId = scenes().active().stream().filter(scene -> progress.questRunId().equals(scene.owningQuestInstance()))
                .findFirst().orElseThrow().id();
    }

    void launch(ServerPlayer player) {
        var result = SceneRuntime.launch(new SceneLaunchService.LaunchRequest(base.server, base.sceneId, "shared_walk", false,
                player.getUUID(), mentor.getUUID(), base.campaignId, objectiveContext(player).progress().questRunId()));
        helper.assertTrue(result.accepted(), "shared authored journey launches: " + result.diagnostic());
        sceneId = result.instanceId();
    }

    void party(ServerPlayer leader, ServerPlayer member) {
        helper.assertTrue(PartyService.createParty(leader).success(), "fixture party created");
        var invitation = PartyService.sendInvitation(leader, member);
        helper.assertTrue(PartyService.acceptInvitation(member, invitation.invitationId()).success(), "fixture party joined");
    }

    CampaignFollowSavedData journeys() { return CampaignFollowSavedData.get(helper.getLevel()); }
    SceneSavedData scenes() { return SceneSavedData.get(helper.getLevel()); }
    SceneInstance scene() { return scenes().get(sceneId).orElseThrow(); }
    CampaignFollowJourney journey() { return journeys().journeys().get(new CampaignFollowJourney.Key(sceneId, "walk")); }

    SceneExecutionContext context() {
        var step = base.scene.steps().get("walk");
        return new SceneExecutionContext(base.server, scenes(), scene(), base.scene, step, scene().currentRecord(step.type()), time++, true);
    }

    void step() {
        SceneStepEngine.process(base.server, scenes(), scene(), base.scene, time++);
        CampaignFollowService.maintain(base.server, scenes());
    }

    void walk(double x, ServerPlayer... followers) {
        Vec3 position = Vec3.atBottomCenterOf(base.origin).add(x, 0, 0);
        mentor.setPos(position);
        for (var follower : followers) follower.setPos(position.add(0, 0, 0.5));
        step();
    }

    void finish(ServerPlayer... followers) {
        for (int x = 1; x <= 6; x++) walk(x, followers);
        for (int tick = 0; tick < 5 && !scene().state().terminal(); tick++) step();
        helper.assertValueEqual(scene().completionResult(), SceneInstance.CompletionResult.SUCCESS,
                "journey reaches its authored completion: " + scene().diagnostic());
    }

    QuestObjectiveEvaluationContext objectiveContext(ServerPlayer player) {
        var quest = base.catalog.quest(base.campaignId).orElseThrow();
        return new QuestObjectiveEvaluationContext(player, null, player.serverLevel(), quest,
                VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest.id()),
                null, null, null, null, null, null);
    }

    boolean complete(ServerPlayer player) {
        var context = objectiveContext(player);
        return QuestObjectiveRegistry.evaluate(context, context.definition().objectives().getFirst()).orElseThrow().complete();
    }

    CompoundTag saved() { return journeys().save(new CompoundTag(), helper.getLevel().registryAccess()); }

    Path scenePath() { return base.server.getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation_scenes.dat"); }
    Path journeyPath() { return base.disk.root.resolve(CampaignFollowSavedData.DATA_NAME + ".dat"); }

    void reload() throws IOException {
        base.reload();
        reloadJourneys(base.disk.read(CampaignFollowSavedData.DATA_NAME));
        var raw = NbtIo.readCompressed(scenePath(), NbtAccounter.unlimitedHeap()).getCompound("data");
        base.server.overworld().getDataStorage().set("villagerretaliation_scenes", SceneSavedData.load(raw, helper.getLevel().registryAccess()));
        SceneStepEngine.clearRuntimeState();
    }

    void reloadJourneys(CompoundTag raw) {
        base.server.overworld().getDataStorage().set(CampaignFollowSavedData.DATA_NAME, CampaignFollowSavedData.load(raw, base.disk.root));
    }

    void block(Path path) throws IOException {
        var backup = base.disk.root.resolve(path.equals(scenePath()) ? "scene-before-block.dat" : "journey-before-block.dat");
        if (Files.exists(path)) Files.move(path, backup);
        Files.createDirectories(path);
        Files.writeString(path.resolve("blocked"), "follow storage fixture");
    }

    void restore(Path path) throws IOException {
        Files.delete(path.resolve("blocked")); Files.delete(path);
        var backup = base.disk.root.resolve(path.equals(scenePath()) ? "scene-before-block.dat" : "journey-before-block.dat");
        if (Files.exists(backup)) Files.move(backup, path);
    }

    @Override public void close() throws IOException {
        for (var player : players) {
            VillagerConversationService.endForPlayer(player, false);
            PartyService.leaveParty(player);
            if (base.server.getPlayerList().getPlayer(player.getUUID()) == player) base.server.getPlayerList().remove(player);
        }
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        mentor.discard();
        terrain.forEach((position, state) -> helper.getLevel().setBlock(position, state, 2));
        base.server.overworld().getDataStorage().set(CampaignFollowSavedData.DATA_NAME, priorJourneys);
        base.server.overworld().getDataStorage().set("villagerretaliation_parties", priorParties);
        Files.deleteIfExists(journeyPath());
        if (priorSceneFile == null) Files.deleteIfExists(scenePath());
        else Files.write(scenePath(), priorSceneFile);
        SceneStepEngine.clearRuntimeState();
        base.close();
    }
}
