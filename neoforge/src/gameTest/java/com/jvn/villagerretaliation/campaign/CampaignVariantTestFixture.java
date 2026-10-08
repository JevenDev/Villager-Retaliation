package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

final class CampaignVariantTestFixture implements AutoCloseable {
    final GameTestHelper helper;
    final CampaignCharacterTestFixture base;
    final CampaignVariantSavedData priorVariants;
    final CampaignRewardSavedData priorRewards;
    final Map<LevelChunk, CampaignBlockOwnershipLedger> priorOwnership = new LinkedHashMap<>();
    final Map<LevelChunk, CampaignGenerationLedger> priorGeneration = new LinkedHashMap<>();
    final Map<BlockPos, CampaignBlockSnapshot> priorBlocks = new LinkedHashMap<>();
    final List<ServerPlayer> players = new ArrayList<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final BlockPos floor;
    final BlockPos entry;
    final Villager mentor;

    CampaignVariantTestFixture(GameTestHelper helper) throws IOException { this(helper, campaign -> { }); }

    CampaignVariantTestFixture(GameTestHelper helper, java.util.function.Consumer<com.google.gson.JsonObject> changeCampaign) throws IOException {
        this.helper = helper;
        base = new CampaignCharacterTestFixture(helper, "variants", "variant_greeting", List.of("variants_catch_up"), quest -> { },
                campaign -> {
                    campaign.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("source", "existing_villager");
                    changeCampaign.accept(campaign);
                }, List.of("conversations/gate.json"));
        priorVariants = CampaignVariantSavedData.get(helper.getLevel());
        priorRewards = CampaignRewardSavedData.get(helper.getLevel());
        base.server.overworld().getDataStorage().set(CampaignVariantSavedData.DATA_NAME, CampaignVariantSavedData.load(new CompoundTag(), base.disk.root));
        base.server.overworld().getDataStorage().set(CampaignRewardSavedData.DATA_NAME, CampaignRewardSavedData.load(new CompoundTag(), base.disk.root));
        floor = base.origin.below();
        entry = base.origin.offset(0, 0, 1);
        seed("floor", floor, Blocks.STONE_BRICKS.defaultBlockState());
        seed("entry", entry, Blocks.AIR.defaultBlockState());
        mentor = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        mentor.setNoAi(true);
        mentor.setVillagerData(mentor.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
        var resolved = base.resolve();
        if (!resolved.available() || resolved.entity() != mentor) {
            String diagnostic = resolved.diagnostic();
            close();
            helper.fail("Fixture keeper could not bind: " + diagnostic);
        }
    }

    private void seed(String name, BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
        var level = helper.getLevel();
        var chunk = level.getChunkAt(position);
        if (!priorOwnership.containsKey(chunk)) {
            priorOwnership.put(chunk, chunk.getExistingDataOrNull(CampaignBlockOwnership.OWNERSHIP.get()));
            priorGeneration.put(chunk, chunk.getExistingDataOrNull(CampaignGeneratedBlocks.GENERATION.get()));
            chunk.setData(CampaignBlockOwnership.OWNERSHIP, new CampaignBlockOwnershipLedger());
            chunk.setData(CampaignGeneratedBlocks.GENERATION, new CampaignGenerationLedger());
        }
        priorBlocks.put(position, CampaignBlockSnapshot.capture(level, chunk, position));
        level.removeBlockEntity(position);
        level.setBlock(position, state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        var cell = new CampaignGeneratedBlock(ResourceLocation.parse("campaign_fixture:waystation"), level.dimension().location(),
                1, base.origin, Rotation.NONE, Mirror.NONE, name, position, state, CampaignGeneratedBlock.State.PENDING, "");
        helper.assertValueEqual(CampaignBlockOwnership.claimGenerated(level, chunk, cell), "", "fixture seeds native generation ownership");
        chunk.getData(CampaignGeneratedBlocks.GENERATION).record(cell.owned());
    }

    CampaignSavedData campaigns() { return CampaignSavedData.get(helper.getLevel()); }
    CampaignVariantSavedData data() { return CampaignVariantSavedData.get(helper.getLevel()); }
    CampaignInstance instance() { return campaigns().instances().get(base.campaignId); }
    CampaignVariantReceipt receipt(String id) { return data().receipts().values().stream().filter(value -> value.variant().equals(id)).findFirst().orElseThrow(); }

    CampaignSavedData.CommitResult commit(String milestone, String outcome, UUID player) throws IOException {
        var content = base.campaigns.campaigns().get(base.campaignId);
        var instance = campaigns().open(content, helper.getLevel().getGameTime());
        return campaigns().commitWorldOutcome(content, milestone, outcome, UUID.randomUUID(), instance.worldRevision(),
                CampaignInstance.Origin.PLAYER_DECISION, Set.of(player), helper.getLevel().getGameTime());
    }

    String reconcile() { return CampaignLocationVariants.reconcile(base.server, base.campaignId); }

    void reload() throws IOException {
        base.reload();
        base.server.overworld().getDataStorage().set(CampaignVariantSavedData.DATA_NAME,
                CampaignVariantSavedData.load(base.disk.read(CampaignVariantSavedData.DATA_NAME), base.disk.root));
        for (var chunk : priorOwnership.keySet()) {
            var saved = chunk.getData(CampaignBlockOwnership.OWNERSHIP).save();
            chunk.setData(CampaignBlockOwnership.OWNERSHIP, CampaignBlockOwnershipLedger.load(saved));
            chunk.setData(CampaignGeneratedBlocks.GENERATION, CampaignGenerationLedger.load(chunk.getData(CampaignGeneratedBlocks.GENERATION).save()));
        }
    }

    ServerPlayer player() {
        var profile = new GameProfile(UUID.randomUUID(), "variant_" + UUID.randomUUID().toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(base.server, helper.getLevel(), profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        base.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setPos(mentor.getX() + 1, mentor.getY(), mentor.getZ());
        players.add(player);
        return player;
    }

    void select(ServerPlayer player, String quest, String stage, String slot, int index) {
        players.forEach(value -> VillagerConversationService.endForPlayer(value, false));
        helper.assertTrue(VillagerConversationService.start(player, mentor), "fixture conversation starts");
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, mentor);
        var id = ResourceLocation.parse("campaign_fixture:" + quest);
        var binding = base.catalog.dialogueCatalog().bindings(id).stream()
                .filter(value -> value.stageId().equals(stage) && value.slot().equals(slot)).findFirst().orElseThrow();
        helper.assertTrue(DialogueTreeService.startEntry(context, binding.treeId(), binding.entryId()).isPresent(), "fixture dialogue opens");
        var option = DialogueTreeService.activeOptions(context).orElseThrow().get(index);
        VillagerDialogueRequestHandler.handle(player, mentor.getId(), option.id());
        var pending = CampaignQuestAcceptanceService.pending(player);
        if (pending != null) CampaignQuestAcceptanceService.handle(player, new CampaignQuestConfirmationRequestPayload(pending.id(), pending.revision(), true));
    }

    @Override public void close() throws IOException {
        for (var player : players) {
            VillagerConversationService.endForPlayer(player, false);
            base.server.getPlayerList().remove(player);
        }
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        mentor.discard();
        for (var chunk : priorOwnership.keySet()) chunk.setData(CampaignBlockOwnership.OWNERSHIP, new CampaignBlockOwnershipLedger());
        for (var saved : priorBlocks.entrySet()) {
            helper.getLevel().removeBlockEntity(saved.getKey());
            helper.getLevel().setBlock(saved.getKey(), saved.getValue().state(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            if (saved.getValue().blockEntity() != null) helper.getLevel().setBlockEntity(BlockEntity.loadStatic(saved.getKey(),
                    saved.getValue().state(), saved.getValue().blockEntity(), helper.getLevel().registryAccess()));
        }
        priorOwnership.forEach((chunk, value) -> { if (value == null) chunk.removeData(CampaignBlockOwnership.OWNERSHIP); else chunk.setData(CampaignBlockOwnership.OWNERSHIP, value); });
        priorGeneration.forEach((chunk, value) -> { if (value == null) chunk.removeData(CampaignGeneratedBlocks.GENERATION); else chunk.setData(CampaignGeneratedBlocks.GENERATION, value); });
        base.server.overworld().getDataStorage().set(CampaignVariantSavedData.DATA_NAME, priorVariants);
        base.server.overworld().getDataStorage().set(CampaignRewardSavedData.DATA_NAME, priorRewards);
        Files.deleteIfExists(base.disk.root.resolve(CampaignVariantSavedData.DATA_NAME + ".dat"));
        Files.deleteIfExists(base.disk.root.resolve(CampaignRewardSavedData.DATA_NAME + ".dat"));
        base.close();
    }
}
