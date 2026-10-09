package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.CampaignScenarioPack.require;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignScenario;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.party.PartySavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.QuestContentLoadReport;
import com.jvn.villagerretaliation.scene.SceneOperatorService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.skill.player.PlayerSocialProgressionSavedData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class CampaignScenarioFixture implements AutoCloseable {
    final GameTestHelper helper;
    final MinecraftServer server;
    final CampaignScenario scenario;
    final Path directory;
    final Map<String, Villager> actors = new LinkedHashMap<>();
    final Map<String, GameProfile> profiles = new LinkedHashMap<>();
    final Map<String, ServerPlayer> players = new LinkedHashMap<>();
    final Map<String, ServerPlayer> lastPlayers = new LinkedHashMap<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final List<Slot> slots = new ArrayList<>();
    final QuestContentCatalog priorCatalog;
    final QuestContentLoadReport priorReport;
    final CampaignContentCatalog priorCampaigns;
    final EndDragonFight priorDragon;
    CampaignContentCatalog.Entry content;
    ConversationTestStorage conversations;
    private boolean closed;
    private boolean installed;
    private boolean isolated;
    private boolean campaignWritesBlocked;
    private boolean conversationWritesBlocked;

    private CampaignScenarioFixture(GameTestHelper helper, CampaignScenario scenario) throws IOException {
        this.helper = helper;
        this.scenario = scenario;
        server = helper.getLevel().getServer();
        priorCatalog = QuestContentCatalogs.current(server);
        priorReport = QuestContentCatalogs.loadReport(server);
        priorCampaigns = CampaignResources.current(server);
        priorDragon = server.getLevel(Level.END).getDragonFight();
        directory = Files.createTempDirectory("campaign-scenario-");
    }

    static CampaignScenarioFixture open(GameTestHelper helper, CampaignScenario scenario, CampaignScenarioPack pack) throws IOException {
        var fixture = new CampaignScenarioFixture(helper, scenario);
        try {
            fixture.initialize(pack);
            return fixture;
        } catch (IOException | RuntimeException exception) {
            try { fixture.close(); } catch (IOException | RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    private void initialize(CampaignScenarioPack pack) throws IOException {
        var compiled = pack.compile(server, priorCatalog.generation() + 1);
        content = compiled.campaigns().get(scenario.campaign());
        QuestContentCatalogs.installForTests(server, compiled.quests(), priorReport);
        CampaignResources.installForTests(server, new CampaignContentCatalog(compiled.quests(), Map.of(scenario.campaign(), content), List.of()));
        installed = true;
        slot(CampaignSavedData.DATA_NAME, () -> CampaignSavedData.get(helper.getLevel()), tag -> CampaignSavedData.load(tag, directory));
        slot(CampaignRewardSavedData.DATA_NAME, () -> CampaignRewardSavedData.get(helper.getLevel()), tag -> CampaignRewardSavedData.load(tag, directory));
        slot(CampaignStandingSavedData.DATA_NAME, () -> CampaignStandingSavedData.get(helper.getLevel()), tag -> CampaignStandingSavedData.load(tag, directory));
        slot(CampaignLocationSavedData.DATA_NAME, () -> CampaignLocationSavedData.get(helper.getLevel()), tag -> CampaignLocationSavedData.load(tag, directory));
        slot(CampaignCharacterSavedData.DATA_NAME, () -> CampaignCharacterSavedData.get(helper.getLevel()), tag -> CampaignCharacterSavedData.load(tag, directory));
        slot(CampaignSpawnSavedData.DATA_NAME, () -> CampaignSpawnSavedData.get(helper.getLevel()), tag -> CampaignSpawnSavedData.load(tag, directory));
        slot(CampaignFollowSavedData.DATA_NAME, () -> CampaignFollowSavedData.get(helper.getLevel()), tag -> CampaignFollowSavedData.load(tag, directory));
        slot(CampaignEvidenceSavedData.DATA_NAME, () -> CampaignEvidenceSavedData.get(helper.getLevel()), tag -> CampaignEvidenceSavedData.load(tag, directory));
        slot(CampaignVariantSavedData.DATA_NAME, () -> CampaignVariantSavedData.get(helper.getLevel()), tag -> CampaignVariantSavedData.load(tag, directory));
        slot(PlayerSocialProgressionSavedData.DATA_NAME, () -> PlayerSocialProgressionSavedData.get(helper.getLevel()), tag -> PlayerSocialProgressionSavedData.load(tag, directory));
        slot("villagerretaliation_quests", () -> VillagerQuestSavedData.get(helper.getLevel()), tag -> VillagerQuestSavedData.load(tag, helper.getLevel().registryAccess()));
        slot("villagerretaliation_parties", () -> PartySavedData.get(helper.getLevel()), tag -> PartySavedData.load(tag, helper.getLevel().registryAccess()));
        slot("villagerretaliation_scenes", () -> SceneSavedData.get(helper.getLevel()), tag -> SceneSavedData.load(tag, helper.getLevel().registryAccess()));
        var mode = new CompoundTag();
        mode.putInt("DataVersion", 1);
        mode.putString("Mode", "campaign");
        slot(CampaignWorldModeSavedData.DATA_NAME, () -> CampaignWorldModeSavedData.get(helper.getLevel()),
                tag -> CampaignWorldModeSavedData.load(tag.isEmpty() ? mode : tag, helper.getLevel().registryAccess()));
        isolated = true;
        conversations = new ConversationTestStorage(helper.getLevel());
        server.getLevel(Level.END).setDragonFight(new EndDragonFight(server.getLevel(Level.END), 0L, EndDragonFight.Data.DEFAULT));
        var instance = campaigns().open(content, helper.getLevel().getGameTime());
        for (var location : scenario.locations()) {
            var definition = content.definition().locations().get(location.alias());
            CampaignLocationSavedData.get(helper.getLevel()).bind(new CampaignLocationBinding(scenario.campaign(), instance.instanceId(),
                    content.definition().revision(), content.definition().fingerprint(), location.alias(), definition.source(),
                    "scenario:" + scenario.id() + "/" + location.alias(), helper.getLevel().dimension().location(),
                    helper.absolutePos(location.position()), helper.getLevel().getGameTime()));
        }
        for (var actor : scenario.actors()) {
            var villager = helper.spawn(EntityType.VILLAGER, actor.position());
            villager.setNoAi(true);
            actors.put(actor.id(), villager);
            if (!actor.role().isBlank()) {
                var role = content.definition().characters().get(actor.role());
                CampaignCharacterSavedData.get(helper.getLevel()).bind(new CampaignCharacterBinding(scenario.campaign(), instance.instanceId(),
                        content.definition().revision(), content.definition().fingerprint(), actor.role(), role.source(), role.lossPolicy(),
                        villager.getUUID(), helper.getLevel().dimension().location(), villager.blockPosition(), helper.getLevel().getGameTime(), false));
                require(CampaignCharacterService.resolve(server, scenario.campaign(), actor.role()).entity() == villager, "Scenario character did not bind");
            }
        }
        int index = 0;
        for (var id : scenario.players()) profiles.put(id, new GameProfile(UUID.randomUUID(), "scenario_" + index++));
    }

    private void slot(String name, Supplier<? extends SavedData> current, Function<CompoundTag, ? extends SavedData> load) {
        var value = new Slot(name, current.get(), current, load);
        slots.add(value);
        server.overworld().getDataStorage().set(name, load.apply(new CompoundTag()));
    }

    CampaignSavedData campaigns() { return CampaignSavedData.get(helper.getLevel()); }
    CampaignInstance instance() { return campaigns().instances().get(scenario.campaign()); }
    UUID playerId(String id) { return profiles.get(id).getId(); }
    ServerPlayer player(String id) {
        var player = players.get(id);
        require(player != null, "Scenario player is offline: " + id);
        return player;
    }

    void connect(String id) {
        require(!players.containsKey(id), "Scenario player is already connected: " + id);
        var profile = profiles.get(id);
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 1));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        players.put(id, player);
        lastPlayers.put(id, player);
    }

    void disconnect(String id) {
        var player = player(id);
        VillagerConversationService.endForPlayer(player, false);
        ConversationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        CampaignQuestAcceptanceService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        server.getPlayerList().remove(player);
        players.remove(id);
    }

    void reload() throws IOException {
        require(!campaignWritesBlocked && !conversationWritesBlocked, "Restore writes before reloading the scenario");
        for (var slot : slots) {
            var path = directory.resolve("reload-" + slot.name() + ".nbt");
            NbtIo.writeCompressed(slot.current().get().save(new CompoundTag(), helper.getLevel().registryAccess()), path);
            var saved = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            server.overworld().getDataStorage().set(slot.name(), slot.load().apply(saved));
        }
        var snapshots = com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData.get(helper.getLevel()).snapshots();
        if (!snapshots.isEmpty()) conversations.reload();
        DialogueTreeService.clearRuntimeState();
        ConversationTestStorage.maintain(server);
    }

    void campaignStorage(boolean available) throws IOException {
        require(available == campaignWritesBlocked, "Campaign storage is already " + (available ? "available" : "blocked"));
        var file = directory.resolve(CampaignSavedData.DATA_NAME + ".dat");
        var backup = directory.resolve("campaign-before-block.dat");
        if (available) {
            Files.delete(file);
            Files.move(backup, file);
        } else {
            Files.move(file, backup);
            Files.createDirectory(file);
        }
        campaignWritesBlocked = !available;
    }

    void conversationStorage(boolean available) throws IOException {
        require(available == conversationWritesBlocked, "Conversation storage is already " + (available ? "available" : "blocked"));
        if (available) conversations.restoreWrites();
        else conversations.blockWrites();
        conversationWritesBlocked = !available;
        if (available) ConversationTestStorage.maintain(server);
    }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        try {
            if (isolated) {
                for (var scene : SceneSavedData.get(helper.getLevel()).active()) if (scenario.campaign().equals(scene.owningCampaignId()))
                    SceneOperatorService.cancel(helper.getLevel(), scene.id(), "scenario finished", "campaign scenario runner");
                for (var id : new ArrayList<>(players.keySet())) disconnect(id);
                for (var receipt : CampaignSpawnSavedData.get(helper.getLevel()).receipts().values()) {
                    var entity = helper.getLevel().getEntity(receipt.binding().entityId());
                    if (entity != null) entity.discard();
                }
                actors.values().forEach(Villager::discard);
                channels.forEach(EmbeddedChannel::finishAndReleaseAll);
            }
        } finally {
            server.getLevel(Level.END).setDragonFight(priorDragon);
            for (var slot : slots) { server.overworld().getDataStorage().set(slot.name(), slot.prior()); slot.prior().setDirty(); }
            if (conversations != null) {
                try {
                    if (conversationWritesBlocked) conversations.restoreWrites();
                    conversations.close();
                } catch (IOException exception) { failure = exception; }
            }
            if (installed) {
                QuestContentCatalogs.installForTests(server, priorCatalog, priorReport);
                CampaignResources.installForTests(server, priorCampaigns);
                com.jvn.villagerretaliation.dialogue.DialogueTreeResources.clearCache();
                DialogueTreeService.clearRuntimeState();
            }
            for (var player : lastPlayers.values()) {
                var directory = CampaignPlayerRewards.directory(player);
                Files.deleteIfExists(directory.resolve(player.getStringUUID() + ".dat"));
                Files.deleteIfExists(directory.resolve(player.getStringUUID() + ".dat_old"));
            }
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    require(path.toAbsolutePath().normalize().startsWith(directory.toAbsolutePath().normalize()), "Scenario cleanup left its temporary directory");
                    Files.delete(path);
                }
            }
        }
        if (failure != null) throw failure;
    }

    private record Slot(String name, SavedData prior, Supplier<? extends SavedData> current, Function<CompoundTag, ? extends SavedData> load) { }
}
