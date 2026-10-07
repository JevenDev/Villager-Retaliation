package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.QuestContentLoadReport;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.village.VillageRegistrySavedData;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

final class CampaignCharacterTestFixture implements AutoCloseable {
    final GameTestHelper helper;
    final MinecraftServer server;
    final QuestContentCatalog prior;
    final QuestContentLoadReport report;
    final CampaignSavedData priorCampaigns;
    final CampaignCharacterSavedData priorCharacters;
    final CampaignLocationSavedData priorLocations;
    final CampaignWorldModeSavedData priorMode;
    final VillageRegistrySavedData priorVillages;
    final VillagerQuestSavedData priorQuests;
    final SceneSavedData priorScenes;
    final Disk disk = new Disk();
    final String fixtureName;
    final ResourceLocation campaignId;
    final ResourceLocation sceneId;
    final String sceneName;
    final CampaignSpawnSavedData priorSpawns;
    final QuestContentCatalog catalog;
    final CampaignContentCatalog campaigns;
    final CompiledScene scene;
    final BlockPos origin;

    CampaignCharacterTestFixture(GameTestHelper helper, String fixtureName, String sceneName) throws IOException {
        this.helper = helper;
        this.fixtureName = fixtureName;
        this.sceneName = sceneName;
        campaignId = id(fixtureName);
        sceneId = id(sceneName);
        server = helper.getLevel().getServer();
        prior = QuestContentCatalogs.current(server);
        report = QuestContentCatalogs.loadReport(server);
        priorCampaigns = CampaignSavedData.get(helper.getLevel());
        priorCharacters = CampaignCharacterSavedData.get(helper.getLevel());
        priorSpawns = CampaignSpawnSavedData.get(helper.getLevel());
        priorLocations = CampaignLocationSavedData.get(helper.getLevel());
        priorMode = CampaignWorldModeSavedData.get(helper.getLevel());
        priorVillages = VillageRegistrySavedData.get(helper.getLevel());
        priorQuests = VillagerQuestSavedData.get(helper.getLevel());
        priorScenes = SceneSavedData.get(helper.getLevel());
        var resources = new ArrayList<QuestBundleTransactions.RawResource>();
        for (String file : List.of("quest.json", "locales/en_us.json", "scenes/" + sceneName + ".json")) {
            String path = "quests/campaign_fixture/" + fixtureName + "/" + file;
            resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id(path), resource(path)));
        }
        var bundles = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
        helper.assertTrue(bundles.diagnostics().isEmpty(), "character bundle compiles: " + bundles.diagnostics());
        var quests = VillagerQuestResources.snapshotForCatalog(server, bundles);
        scene = SceneCompiler.compile(SceneParser.parse(sceneId, resource("quests/campaign_fixture/" + fixtureName + "/scenes/" + sceneName + ".json")).resource()).scene();
        helper.assertTrue(scene != null, "fixture scene compiles");
        catalog = new QuestContentCatalog(prior.generation() + 1L, quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(),
                quests.objectiveEventQuestIds(), quests.factQuestIds(), quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(),
                quests.triggerEventQuestIds(), Map.of(sceneId, scene), Map.of(), List.of(), bundles.bundles(), bundles.localization(), QuestRewardCatalog.fromBundles(bundles.bundles()));
        var root = resource("campaigns/" + fixtureName + "/campaign.json");
        origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var position = new JsonObject();
        position.addProperty("x", origin.getX());
        position.addProperty("y", origin.getY());
        position.addProperty("z", origin.getZ());
        root.getAsJsonArray("locations").get(0).getAsJsonObject().add("origin", position);
        root.getAsJsonArray("locations").get(0).getAsJsonObject().addProperty("source", "marker");
        root.getAsJsonArray("locations").get(0).getAsJsonObject().remove("search_radius");
        root.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("search_radius", 2);
        var locales = QuestLocaleCatalog.read(resource("campaigns/" + fixtureName + "/locales/en_us.json"));
        campaigns = CampaignContentCatalog.compile(catalog, List.of(new CampaignContentCatalog.Source(id("campaigns/" + fixtureName + "/campaign.json"),
                "campaign_fixture", root, QuestLocaleCatalog.empty().overlay("en_us", locales.messages()), List.of())));
        helper.assertTrue(campaigns.diagnostics().isEmpty(), "campaign character references compile: " + campaigns.diagnostics());
        QuestContentCatalogs.installForTests(server, catalog, report);
        CampaignResources.installForTests(server, campaigns);
        var storage = server.overworld().getDataStorage();
        storage.set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(new CompoundTag(), disk.root));
        storage.set(CampaignCharacterSavedData.DATA_NAME, CampaignCharacterSavedData.load(new CompoundTag(), disk.root));
        storage.set(CampaignSpawnSavedData.DATA_NAME, CampaignSpawnSavedData.load(new CompoundTag(), disk.root));
        storage.set(CampaignLocationSavedData.DATA_NAME, CampaignLocationSavedData.load(new CompoundTag(), disk.root));
        storage.set("villagerretaliation_village_registry", new VillageRegistrySavedData());
        storage.set("villagerretaliation_quests", new VillagerQuestSavedData());
        storage.set("villagerretaliation_scenes", new SceneSavedData());
        mode("campaign");
    }

    void select(DialogueContext context, String stage, String slot) {
        VillagerConversationService.endForPlayer(context.player(), false);
        helper.assertTrue(VillagerConversationService.start(context.player(), context.villager()), "fixture conversation starts");
        var entry = catalog.dialogueCatalog().bindings(campaignId).stream()
                .filter(value -> value.stageId().equals(stage) && value.slot().equals(slot)).findFirst().orElseThrow();
        helper.assertTrue(DialogueTreeService.startEntry(context, entry.treeId(), entry.entryId()).isPresent(), "authored dialogue opens");
        var options = DialogueTreeService.activeOptions(context).orElseThrow();
        VillagerDialogueRequestHandler.handle(context.player(), context.villager().getId(), options.getFirst().id());
    }

    void install(JsonObject raw) {
        var parsed = CampaignDefinitionParser.parse(campaignId, raw);
        helper.assertTrue(parsed.valid(), "alternate character content compiles");
        CampaignResources.installForTests(server, new CampaignContentCatalog(catalog, Map.of(campaignId,
                new CampaignContentCatalog.Entry(parsed.definition(), campaigns.campaigns().get(campaignId).localization())), List.of()));
    }

    void mode(String mode) {
        var tag = new CompoundTag(); tag.putInt("DataVersion", 1); tag.putString("Mode", mode);
        server.overworld().getDataStorage().set(CampaignWorldModeSavedData.DATA_NAME, CampaignWorldModeSavedData.load(tag, helper.getLevel().registryAccess()));
    }

    CampaignCharacterService.Result resolve() { return CampaignCharacterService.resolve(server, campaignId, "mentor"); }

    JsonObject raw() throws IOException {
        var root = resource("campaigns/" + fixtureName + "/campaign.json");
        var location = root.getAsJsonArray("locations").get(0).getAsJsonObject();
        location.addProperty("source", "marker"); location.remove("search_radius");
        var position = new JsonObject();
        position.addProperty("x", origin.getX()); position.addProperty("y", origin.getY()); position.addProperty("z", origin.getZ());
        location.add("origin", position);
        root.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("search_radius", 2);
        return root;
    }

    void reload() throws IOException {
        var storage = server.overworld().getDataStorage();
        storage.set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(disk.read(CampaignSavedData.DATA_NAME), disk.root));
        storage.set(CampaignCharacterSavedData.DATA_NAME, CampaignCharacterSavedData.load(disk.read(CampaignCharacterSavedData.DATA_NAME), disk.root));
        storage.set(CampaignSpawnSavedData.DATA_NAME, CampaignSpawnSavedData.load(disk.read(CampaignSpawnSavedData.DATA_NAME), disk.root));
    }

    @Override public void close() throws IOException {
        var storage = server.overworld().getDataStorage();
        storage.set(CampaignSavedData.DATA_NAME, priorCampaigns);
        storage.set(CampaignCharacterSavedData.DATA_NAME, priorCharacters);
        storage.set(CampaignSpawnSavedData.DATA_NAME, priorSpawns);
        storage.set(CampaignLocationSavedData.DATA_NAME, priorLocations);
        storage.set(CampaignWorldModeSavedData.DATA_NAME, priorMode);
        storage.set("villagerretaliation_village_registry", priorVillages);
        storage.set("villagerretaliation_quests", priorQuests);
        storage.set("villagerretaliation_scenes", priorScenes);
        priorQuests.setDirty();
        priorScenes.setDirty();
        QuestContentCatalogs.installForTests(server, prior, report);
        CampaignResources.clearCache();
        disk.close();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }

    static JsonObject resource(String path) throws IOException {
        try (var stream = CampaignCharacterTestFixture.class.getResourceAsStream("/campaign_fixture/data/campaign_fixture/" + path)) {
            if (stream == null) throw new IOException("Missing campaign character fixture " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        }
    }

    static final class Disk implements AutoCloseable {
        final Path root = Files.createTempDirectory("campaign-characters-");
        private Disk() throws IOException { }
        CompoundTag read(String name) throws IOException {
            var path = root.resolve(name + ".dat");
            return Files.notExists(path) ? new CompoundTag() : NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompound("data");
        }
        @Override public void close() throws IOException {
            for (String name : List.of(CampaignSavedData.DATA_NAME, CampaignCharacterSavedData.DATA_NAME, CampaignLocationSavedData.DATA_NAME, CampaignSpawnSavedData.DATA_NAME)) {
                Files.deleteIfExists(root.resolve(name + ".dat"));
            }
            Files.delete(root);
        }
    }
}
