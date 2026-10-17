package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignCharacterGameTests {
    private static final ResourceLocation CAMPAIGN = id("characters");
    private static final ResourceLocation SCENE = id("character_greeting");

    private CampaignCharacterGameTests() { }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void charactersRequireBoundedCastingAndExplicitLossPolicy(GameTestHelper helper) throws IOException {
        var root = resource("campaigns/characters/campaign.json");
        helper.assertTrue(CampaignDefinitionParser.parse(CAMPAIGN, root).valid(), "authored character compiles");
        for (String invalid : List.of("{\"search_radius\":65}", "{\"search_radius\":0}", "{\"source\":\"unbounded\"}",
                "{\"loss_policy\":\"ignore\"}", "{\"location\":\"missing\"}", "{\"profession\":true}", "{\"title_key\":\"outside.title\"}")) {
            var candidate = root.deepCopy();
            var role = candidate.getAsJsonArray("characters").get(0).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> role.add(entry.getKey(), entry.getValue()));
            var parsed = CampaignDefinitionParser.parse(CAMPAIGN, candidate);
            helper.assertFalse(parsed.valid(), "invalid character rejected: " + invalid);
            helper.assertTrue(parsed.diagnostics().stream().anyMatch(issue -> issue.jsonPointer().startsWith("/characters/0")), "diagnostic identifies authored role");
        }
        root.getAsJsonArray("characters").get(0).getAsJsonObject().remove("loss_policy");
        helper.assertFalse(CampaignDefinitionParser.parse(CAMPAIGN, root).valid(), "loss policy cannot be omitted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void providerAndSceneReferencesRequireValidCampaignRoles(GameTestHelper helper) throws IOException {
        var raw = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Format.runtime(resource("quests/campaign_fixture/characters/quest.json"));
        var parsed = QuestV2Parser.parse(CAMPAIGN, raw).orElseThrow();
        var compiled = QuestV2Compiler.compile(parsed, QuestResourceEnvelope.read(CAMPAIGN, raw).orElseThrow()).orElseThrow();
        helper.assertValueEqual(compiled.provider().campaignRole(), "mentor", "provider retains typed role");
        for (String role : List.of("null", "true", "\"bad name\"", "\"\"")) {
            var candidate = raw.deepCopy();
            candidate.getAsJsonObject("provider").add("campaign_role", JsonParser.parseString(role));
            helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, candidate).isEmpty(), "invalid provider role rejected");
        }
        raw.getAsJsonObject("metadata").remove("campaign");
        helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, raw).isEmpty(), "campaign ownership is required");
        var scene = resource("quests/campaign_fixture/characters/scenes/character_greeting.json");
        for (String invalid : List.of("{\"type\":\"villagerretaliation:position\"}", "{\"binding\":\"bad name\"}",
                "{\"replacement_policy\":\"operator_rebindable\"}", "{\"death_policy\":\"respawn_if_owned\"}")) {
            var candidate = scene.deepCopy();
            var actor = candidate.getAsJsonArray("actors").get(0).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> actor.add(entry.getKey(), entry.getValue()));
            helper.assertFalse(SceneCompiler.compile(SceneParser.parse(SCENE, candidate).resource()).valid(), "scene cannot override the shared character");
        }
        try (var fixture = fixture(helper)) {
            var campaign = resource("campaigns/characters/campaign.json");
            campaign.remove("characters");
            campaign.remove("relationships");
            var missingDefinition = CampaignDefinitionParser.parse(CAMPAIGN, campaign);
            helper.assertTrue(missingDefinition.valid(), "campaign structure remains valid before checking external role references");
            var missing = missingDefinition.definition();
            var issues = CampaignDefinitionParser.validateQuestReferences(missing, fixture.catalog);
            helper.assertTrue(issues.stream().anyMatch(issue -> issue.jsonPointer().equals("/provider/campaign_role")), "provider reference checked");
            helper.assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("scene")), "scene role reference checked");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void competingCastsSaveOneIdentityAndRejectDuplicateRoles(GameTestHelper helper) throws Exception {
        try (var disk = new Disk(); var executor = Executors.newFixedThreadPool(2)) {
            var data = disk.open();
            var first = binding(UUID.randomUUID(), "mentor", UUID.randomUUID());
            var second = binding(first.instance(), "mentor", UUID.randomUUID());
            var results = executor.invokeAll(List.<Callable<CampaignCharacterBinding>>of(() -> data.bind(first), () -> data.bind(second)));
            var winner = results.getFirst().get();
            helper.assertValueEqual(results.getLast().get(), winner, "competing callers receive the committed character");
            helper.assertValueEqual(disk.reload().forEntity(winner.entityId()), winner, "identity saved before publication");
            helper.assertValueEqual(disk.reload().bind(second), winner, "reload cannot recast a role");
            try {
                data.bind(binding(first.instance(), "rival", winner.entityId()));
                helper.fail("one entity cannot silently acquire another role");
            } catch (IllegalArgumentException expected) {
                helper.assertValueEqual(data.bindings().size(), 1, "original binding retained");
            }
            data.recordLoss(winner.entityId(), true);
            helper.assertTrue(disk.reload().forEntity(winner.entityId()).lost(), "permanent loss is durable");
            data.recordLoss(winner.entityId(), false);
            helper.assertFalse(disk.reload().forEntity(winner.entityId()).lost(), "recovered original identity is durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void invalidCharacterSavesAndFailedWritesPreserveExistingData(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var data = disk.open();
            var original = binding(UUID.randomUUID(), "mentor", UUID.randomUUID());
            data.bind(original);
            var tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            for (int mutation = 0; mutation < 5; mutation++) {
                var broken = tag.copy();
                if (mutation == 0) broken.putInt("DataVersion", 99);
                if (mutation == 1) broken.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).putString("Lost", "true");
                if (mutation == 2) broken.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).remove("Entity");
                if (mutation == 3) {
                    var duplicate = broken.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).copy();
                    duplicate.putString("Role", "other");
                    broken.getList("Bindings", Tag.TAG_COMPOUND).add(duplicate);
                }
                if (mutation == 4) broken = new CompoundTag();
                var loaded = CampaignCharacterSavedData.load(broken, disk.root);
                helper.assertFalse(loaded.diagnostic().isBlank(), "invalid ledger is dormant");
                helper.assertValueEqual(loaded.save(new CompoundTag(), helper.getLevel().registryAccess()), broken, "invalid bytes remain recoverable");
                try { loaded.bind(original); helper.fail("invalid ledger must not overwrite disk"); }
                catch (IOException expected) { helper.assertValueEqual(disk.reload().forEntity(original.entityId()), original, "disk unchanged"); }
            }
            var path = disk.root.resolve("blocked");
            Files.writeString(path, "not a directory");
            try {
                var blocked = CampaignCharacterSavedData.load(new CompoundTag(), path);
                try { blocked.bind(original); helper.fail("write must fail"); }
                catch (IOException expected) { helper.assertTrue(blocked.bindings().isEmpty(), "failed write does not publish identity"); }
            } finally { Files.delete(path); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void castingPreservesExistingVillagerStateAndFollowsItsIdentity(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            villager.setNoAi(true);
            villager.setCustomName(Component.literal("Existing mentor"));
            villager.addTag("player_owned_fixture");
            villager.getPersistentData().putUUID("ExistingOwner", UUID.randomUUID());
            villager.getPersistentData().putInt("ExistingRelationship", 42);
            villager.getInventory().setItem(0, new ItemStack(Items.EMERALD, 7));
            var before = villager.saveWithoutId(new CompoundTag());
            var first = fixture.resolve();
            helper.assertTrue(first.available(), "eligible existing adult is cast: " + first.diagnostic());
            helper.assertValueEqual(first.binding().entityId(), villager.getUUID(), "role binds the existing UUID");
            helper.assertValueEqual(villager.saveWithoutId(new CompoundTag()), before, "casting does not rewrite entity or personal state");
            fixture.reload();
            villager.setPos(villager.getX() + 100, villager.getY(), villager.getZ());
            helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            helper.assertValueEqual(fixture.resolve().entity(), villager, "moving away or a closer villager cannot recast the role");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void unloadedCharactersNeverCountAsDeadAndRecoverWithTheirOriginalUuid(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var bound = fixture.resolve().binding();
            var snapshot = villager.saveWithoutId(new CompoundTag());
            villager.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.reload();
            helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var unavailable = fixture.resolve();
            helper.assertFalse(unavailable.available(), "unloaded character waits");
            helper.assertFalse(unavailable.binding().lost(), "unloaded is not permanent loss");
            helper.assertValueEqual(unavailable.binding().entityId(), bound.entityId(), "another villager cannot claim the role");
            var restored = EntityType.VILLAGER.create(helper.getLevel());
            restored.load(snapshot);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "original entity returns");
            helper.assertValueEqual(fixture.resolve().entity().getUUID(), bound.entityId(), "original UUID resumes its role");
            restored.discard();
            helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).forEntity(bound.entityId()).lost(), "actual permanent removal is observed");
            fixture.reload();
            helper.assertTrue(fixture.resolve().binding().lost(), "permanent loss survives reload without recasting");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void unavailableCastingNeverLoadsChunksAndRequiresAnEligibleAdult(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var raw = fixture.raw();
            raw.getAsJsonArray("locations").get(0).getAsJsonObject().getAsJsonObject("origin").addProperty("x", 20_000_000);
            fixture.install(raw);
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            helper.assertFalse(fixture.resolve().available(), "remote unloaded role remains unresolved");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "casting cannot load distant chunks");
            helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).bindings().isEmpty(), "no false identity is reserved");
        }
        try (var fixture = fixture(helper)) {
            var baby = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            baby.setAge(-24000);
            helper.assertFalse(fixture.resolve().available(), "children are not cast as authored adults");
            baby.setAge(0);
            helper.assertValueEqual(fixture.resolve().entity(), baby, "an eligible adult resolves without changing the definition");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void professionFiltersApplyOnlyToInitialCasting(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var raw = fixture.raw();
            raw.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("profession", "minecraft:librarian");
            fixture.install(raw);
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            helper.assertFalse(fixture.resolve().available(), "ineligible profession does not claim a role");
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
            helper.assertValueEqual(fixture.resolve().entity(), villager, "eligible profession is cast");
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
            fixture.reload();
            helper.assertValueEqual(fixture.resolve().entity(), villager, "later job changes preserve the existing character");
            helper.assertValueEqual(villager.getVillagerData().getProfession(), VillagerProfession.FARMER, "role resolution does not reset the villager's job");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void missingContentAndWorldModesKeepCharactersDormant(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var bound = fixture.resolve().binding();
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(fixture.catalog, Map.of(), List.of()));
            helper.assertFalse(fixture.resolve().available(), "removed definition makes character dormant");
            helper.assertTrue(CampaignCharacterService.protects(villager), "saved essential character stays protected while content is missing");
            CampaignResources.installForTests(fixture.server, fixture.campaigns);
            for (String mode : List.of("sandbox", "legacy")) {
                fixture.mode(mode);
                helper.assertFalse(fixture.resolve().available(), "noncampaign worlds cannot resolve campaign roles");
                helper.assertFalse(CampaignCharacterService.protects(villager), "ordinary world behavior is preserved");
            }
            fixture.mode("campaign");
            helper.assertValueEqual(fixture.resolve().binding(), bound, "returning content retains original identity");
            var changed = fixture.raw(); changed.addProperty("revision", 2);
            fixture.install(changed);
            helper.assertFalse(fixture.resolve().available(), "incompatible update cannot silently recast the role");
            helper.assertValueEqual(CampaignCharacterSavedData.get(helper.getLevel()).forEntity(villager.getUUID()), bound, "incompatible content preserves binding");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void campaignProvidersRejectCompatibleVillagersAndCanPlayTheirScene(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var other = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
            helper.assertValueEqual(fixture.resolve().entity(), villager, "nearest character cast");
            var quest = fixture.catalog.quest(CAMPAIGN).orElseThrow();
            helper.assertTrue(!quest.rules().lockedToVillager() && quest.rules().crossVillagerCompatible(), "fixture exercises ordinary replacement permissions");
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
            var otherContext = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, other);
            var wrong = QuestExecutionContext.fromDialogueContext(otherContext, quest, "fixture");
            helper.assertFalse(VillagerQuestProviderType.INSTANCE.matchesOffer(wrong, quest), "compatible villager cannot offer the role's quest");
            helper.assertFalse(VillagerQuestProviderType.INSTANCE.matchesIssuerLock(wrong, quest, null), "unlocked ordinary rules cannot bypass campaign identity");
            try {
                fixture.select(context, "visit", "offer");
                var progress = VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), CAMPAIGN);
                helper.assertTrue(progress != null, "bound provider starts the authored quest");
                var scenes = SceneSavedData.get(helper.getLevel());
                helper.assertValueEqual(scenes.active().size(), 1, "authored scene starts");
                BuiltinSceneStepExecutors.register();
                long now = helper.getLevel().getGameTime();
                for (int tick = 0; tick < 40; tick++) {
                    for (var scene : List.copyOf(scenes.active())) SceneStepEngine.process(fixture.server, scenes, scene, fixture.scene, now + tick);
                    SceneContinuationService.maintain(fixture.server, scenes);
                    player.tickCount = tick;
                    VillagerQuestService.onPlayerTick(player);
                }
                helper.assertValueEqual(progress.currentStage(), "report", "shared role scene allows normal quest progression");
                var completedScene = scenes.all().iterator().next();
                helper.assertValueEqual(completedScene.actorBindings().get("guide").entityId(), villager.getUUID(), "scene uses authoritative character identity");
                helper.assertFalse(VillagerQuestProviderType.INSTANCE.matchesIssuerLock(wrong, quest, progress), "turn-in remains bound to the same role");
                fixture.select(context, "report", "turn_in");
                helper.assertValueEqual(progress.completionCount(), 1, "bound character completes the playable fixture");
            } finally {
                VillagerConversationService.endForPlayer(player, false);
                DialogueTreeService.clearRuntimeState();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void missingSceneCharacterPausesAcrossReloadAndResumesWhenItReturns(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var binding = fixture.resolve().binding();
            var snapshot = villager.saveWithoutId(new CompoundTag());
            villager.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var player = helper.makeMockServerPlayerInLevel();
            var scene = new SceneInstance(UUID.randomUUID(), fixture.scene, "fixture", new SceneOwner(SceneResource.OwnershipMode.PLAYER,
                    player.getUUID(), null, null, ""), UUID.randomUUID(), Set.of(player.getUUID()), Map.of(), 1L);
            scene.linkQuest(CAMPAIGN); scene.bindCampaign(CAMPAIGN);
            var scenes = new SceneSavedData();
            SceneStepEngine.process(fixture.server, scenes, scene, fixture.scene, 3L);
            helper.assertTrue(scene.campaignPaused() && scene.stepRecords().isEmpty(), "missing character pauses before effects");
            var restoredScene = SceneInstance.load(scene.save());
            fixture.reload();
            var restored = EntityType.VILLAGER.create(helper.getLevel()); restored.load(snapshot);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "original character loads");
            BuiltinSceneStepExecutors.register();
            for (long time = 103; time < 125 && !restoredScene.state().terminal(); time++) SceneStepEngine.process(fixture.server, scenes, restoredScene, fixture.scene, time);
            helper.assertValueEqual(restoredScene.state(), SceneState.COMPLETED, "scene resumes with original identity");
            helper.assertValueEqual(restoredScene.actorBindings().get("guide").entityId(), binding.entityId(), "reloaded scene retains character");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void authoredDownedProtectionSurvivesDisabledDefaultsAndLethalDamage(GameTestHelper helper) throws IOException {
        boolean prior = VillagerRetaliationConfig.ENABLE_VILLAGER_DOWNED_STATE.get();
        try (var fixture = fixture(helper)) {
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            fixture.resolve();
            VillagerRetaliationConfig.ENABLE_VILLAGER_DOWNED_STATE.set(false);
            helper.assertTrue(VillagerDeathProtectionResolver.resolve(helper.getLevel(), villager, villager.damageSources().genericKill()).protectedFromDeath(), "authored essential policy overrides ordinary damage defaults");
            villager.hurt(villager.damageSources().genericKill(), 1000);
            helper.assertTrue(villager.isAlive() && VillagerDownedService.isDowned(villager), "lethal damage downs the shared character");
            villager.hurt(villager.damageSources().genericKill(), 1000);
            helper.assertTrue(villager.isAlive(), "downed character cannot be finished by ordinary bypass damage");
            helper.assertFalse(fixture.resolve().available(), "downed character pauses live interactions");
            VillagerDownedService.recover(villager);
            helper.assertTrue(fixture.resolve().available(), "recovered character resumes without recasting");
        } finally { VillagerRetaliationConfig.ENABLE_VILLAGER_DOWNED_STATE.set(prior); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_characters")
    public static void lightningCannotReplaceAnEssentialCampaignCharacter(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        var prior = server.getWorldData().getDifficulty();
        try (var fixture = fixture(helper)) {
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var binding = fixture.resolve().binding();
            villager.thunderHit(helper.getLevel(), EntityType.LIGHTNING_BOLT.create(helper.getLevel()));
            helper.assertTrue(villager.isAlive() && !villager.isRemoved(), "lightning cannot replace an essential role with a different entity");
            helper.assertValueEqual(fixture.resolve().binding().entityId(), binding.entityId(), "character identity survives conversion attempts");
            var ordinary = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
            ordinary.thunderHit(helper.getLevel(), EntityType.LIGHTNING_BOLT.create(helper.getLevel()));
            helper.assertTrue(ordinary.isRemoved(), "ordinary villagers retain vanilla conversion behavior");
        } finally { server.setDifficulty(prior, true); }
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }

    private static CampaignCharacterBinding binding(UUID instance, String role, UUID entity) {
        return new CampaignCharacterBinding(CAMPAIGN, instance, 1, "fixture", role, CampaignCharacterDefinition.Source.EXISTING_VILLAGER,
                CampaignCharacterDefinition.LossPolicy.DOWNED, entity, ResourceLocation.parse("minecraft:overworld"), new BlockPos(1, 70, 1), 1L, false);
    }

    private static JsonObject resource(String path) throws IOException {
        try (var stream = CampaignCharacterGameTests.class.getResourceAsStream("/campaign_fixture/data/campaign_fixture/" + path)) {
            if (stream == null) throw new IOException("Missing campaign character fixture " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        }
    }

    private static final class Disk implements AutoCloseable {
        private final Path root = Files.createTempDirectory("campaign-characters-");
        private Disk() throws IOException { }
        private CampaignCharacterSavedData open() { return CampaignCharacterSavedData.load(new CompoundTag(), root); }
        private CampaignCharacterSavedData reload() throws IOException {
            return CampaignCharacterSavedData.load(NbtIo.readCompressed(root.resolve(CampaignCharacterSavedData.DATA_NAME + ".dat"), NbtAccounter.unlimitedHeap()).getCompound("data"), root);
        }
        @Override public void close() throws IOException {
            Files.deleteIfExists(root.resolve(CampaignCharacterSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(root.resolve(CampaignSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(root.resolve(CampaignLocationSavedData.DATA_NAME + ".dat"));
            Files.delete(root);
        }
    }

    private static CampaignCharacterTestFixture fixture(GameTestHelper helper) throws IOException {
        return new CampaignCharacterTestFixture(helper, "characters", "character_greeting");
    }
}
