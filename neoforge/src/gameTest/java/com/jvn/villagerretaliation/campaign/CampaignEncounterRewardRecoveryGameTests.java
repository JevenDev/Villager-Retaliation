package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterRewardRecoveryGameTests {
    private static final String PLAYER = """
            {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter",
             "spawn_mode":"fixed","spawn_radius":0,"members":[{"entity":"minecraft:cow"}],
             "rewards":{"completion":[{"id":"payment","item":"minecraft:emerald","count":5,"trophy_name":"Earned payment"}]}}
            """;
    private static final String PROFILE = """
            {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter",
             "spawn_mode":"fixed","spawn_radius":0,"members":[{"entity":"minecraft:cow","health":30}],
             "allies":[{"id":"guard","entity":"minecraft:villager","cleanup_policy":"preserve"}]}
            """;

    private CampaignEncounterRewardRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void failedPlayerSaveRollsBackInventoryAndRetainsResolvedItems(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            var player = player(fixture);
            try (var blocked = new BlockedFile(CampaignPlayerRewards.directory(player).resolve(player.getStringUUID() + ".dat"))) {
                defeat(helper, fixture);
                helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "failed player save rolls back inventory");
                helper.assertValueEqual(grant(fixture).getString("State"), "READY", "resolved items remain pending");
                helper.assertTrue(fixture.encounter().contentDiagnostic().contains("waiting"), "storage failure is recoverable");
            }
            var savedItems = grant(fixture).getList("Items", Tag.TAG_COMPOUND).copy();
            fixture.reload(); recover(fixture);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 5, "recovery delivers the saved items");
            helper.assertValueEqual(grant(fixture).getList("Items", Tag.TAG_COMPOUND), savedItems, "recovery keeps the resolved item components");
            fixture.reload(); recover(fixture);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 5, "repeated recovery cannot duplicate inventory");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void nativePlayerReceiptSurvivesMissingSceneAcknowledgement(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            var player = player(fixture);
            try (var blocked = new BlockedFile(CampaignPlayerRewards.directory(player).resolve(player.getStringUUID() + ".dat"))) {
                defeat(helper, fixture);
            }
            var saved = grant(fixture);
            CampaignPlayerRewards.deliver(player, new CampaignPlayerRewards.Delivery(operation(saved), saved.getUUID("Token"),
                    player.getUUID(), 0, saved.getList("Items", Tag.TAG_COMPOUND).stream().map(value -> (CompoundTag) value).toList(), false));
            fixture.block(); recover(fixture);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 5, "failed acknowledgement cannot repeat delivery");
            fixture.restore(); fixture.reload();
            player.load(NbtIo.readCompressed(CampaignPlayerRewards.directory(player).resolve(player.getStringUUID() + ".dat"), NbtAccounter.unlimitedHeap()));
            recover(fixture);
            helper.assertValueEqual(grant(fixture).getString("State"), "DELIVERED", "native receipt completes the saved scene reward");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 5, "both saves reload without duplicating delivery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void fullInventoryWaitsWithoutDroppingOrLosingTheReward(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            var player = player(fixture);
            for (int index = 0; index < player.getInventory().items.size(); index++)
                player.getInventory().items.set(index, new ItemStack(Items.STONE, 64));
            defeat(helper, fixture);
            helper.assertValueEqual(grant(fixture).getString("State"), "READY", "full inventory retains a pending reward");
            helper.assertTrue(fixture.encounter().contentDiagnostic().contains("Make room"), "player receives a useful inventory diagnostic");
            fixture.reload();
            player.getInventory().items.set(0, ItemStack.EMPTY);
            recover(fixture); recover(fixture);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 5, "making room delivers the saved reward once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void failedProfileSaveCannotChangeTheVillager(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PROFILE)) {
            fixture.run();
            UUID ally = initializeProfile(helper, fixture);
            try (var blocked = new BlockedFile(profilePath(fixture))) {
                defeat(helper, fixture);
                helper.assertValueEqual(guts(helper, ally), 10, "failed native save leaves the live profile unchanged");
                helper.assertValueEqual(grant(fixture).getString("State"), "READY", "profile reward remains pending");
            }
            fixture.reload(); recover(fixture);
            helper.assertValueEqual(guts(helper, ally), 13, "native storage recovery applies the original reward");
            reloadProfiles(helper, fixture); fixture.reload(); recover(fixture);
            helper.assertValueEqual(guts(helper, ally), 13, "native and scene reload cannot repeat the attribute change");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void unloadedProfileReceiptRecoversWithoutOverwritingLaterChanges(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PROFILE)) {
            fixture.run();
            UUID ally = initializeProfile(helper, fixture);
            try (var blocked = new BlockedFile(profilePath(fixture))) { defeat(helper, fixture); }
            var saved = grant(fixture);
            VillagerProfileSavedData.get(helper.getLevel()).deliverCampaignReward(helper.getLevel(), operation(saved), saved.getUUID("Token"), ally, 3, false);
            fixture.block(); recover(fixture); fixture.restore();
            helper.getLevel().getEntity(ally).discard();
            reloadProfiles(helper, fixture);
            VillagerProfileManager.getProfile(helper.getLevel(), ally).orElseThrow().setSocialAttribute(VillagerSocialAttribute.GUTS, 27, helper.getLevel().getGameTime());
            fixture.reload(); recover(fixture);
            helper.assertValueEqual(guts(helper, ally), 27, "receipt acknowledgement never reapplies an old attribute delta");
            helper.assertValueEqual(grant(fixture).getString("State"), "DELIVERED", "unloaded villager is acknowledged through its original profile");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void offlineWaveRewardSurvivesAnEncounterRetry(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, """
                {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter","spawn_mode":"raid_waves","spawn_radius":0,
                 "waves":[{"id":"first","members":[{"entity":"minecraft:cow"}]},{"id":"second","members":[{"entity":"minecraft:cow"}]}],
                 "rewards":{"waves":[{"id":"payment","wave":"first","item":"minecraft:emerald","count":5}]}}
                """)) {
            fixture.run();
            var player = player(fixture);
            fixture.base.base.server.getPlayerList().remove(player);
            defeat(helper, fixture);
            helper.assertValueEqual(grant(fixture).getString("State"), "EARNED", "offline participation is captured when the wave resolves");
            fixture.encounter().resetForRetry(0, false, Set.of());
            fixture.data().persistCampaignHandoff(helper.getLevel());
            fixture.reload();
            var reconnected = fixture.base.reconnect(player);
            var late = fixture.base.player();
            recover(fixture);
            helper.assertValueEqual(reconnected.getInventory().countItem(Items.EMERALD), 5, "earned wave reward survives reset eligibility");
            helper.assertValueEqual(late.getInventory().countItem(Items.EMERALD), 0, "late arrival does not become a reward participant");
            fixture.reload(); recover(fixture);
            helper.assertValueEqual(reconnected.getInventory().countItem(Items.EMERALD), 5, "reconnect and reload preserve one delivery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void unsupportedRewardHistoryIsPreservedAndBlocksDelivery(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            var player = player(fixture);
            fixture.block(); defeat(helper, fixture); fixture.restore();
            var snapshot = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var savedEncounter = encounter(snapshot, fixture);
            var history = savedEncounter.getCompound("RewardHistory");
            history.putInt("Version", 99);
            history.putString("FutureField", "preserve");
            install(helper, fixture, snapshot);
            recover(fixture);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "future receipt formats cannot grant rewards");
            helper.assertValueEqual(fixture.encounter().save().getCompound("RewardHistory"), history, "future history remains available for repair");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void legacyCompletedReceiptDoesNotReplayAfterMigration(GameTestHelper helper) throws IOException {
        legacy(helper, true);
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void legacyIncompleteReceiptStaysAmbiguousAfterMigration(GameTestHelper helper) throws IOException {
        legacy(helper, false);
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void changedRewardDefinitionsWaitForOriginalContent(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            var player = player(fixture);
            defeat(helper, fixture);
            var changed = PLAYER.replace("\"count\":5", "\"count\":6");
            template(fixture, changed);
            recover(fixture);
            helper.assertTrue(fixture.encounter().contentDiagnostic().contains("changed")
                    || fixture.encounter().contentDiagnostic().contains("no longer matches"), "changed reward content requires repair");
            template(fixture, PLAYER.replace("\"trophy_name\":\"Earned payment\"}", "\"trophy_name\":\"Earned payment\"},{\"id\":\"bonus\",\"item\":\"minecraft:diamond\"}"));
            recover(fixture);
            helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 0, "adding rewards cannot retroactively create a payout");
            helper.assertTrue(fixture.encounter().contentDiagnostic().contains("changed"), "new rewards also require explicit content repair");
            template(fixture, PLAYER);
            recover(fixture);
            helper.assertTrue(fixture.encounter().contentDiagnostic().isBlank(), "restoring the original reward definition resumes recovery");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 5, "definition recovery does not replay delivered rewards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void missingCurrentRewardHistoryCannotBeRecreated(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            fixture.block(); defeat(helper, fixture); fixture.restore();
            var snapshot = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            encounter(snapshot, fixture).remove("RewardHistory");
            install(helper, fixture, snapshot);
            recover(fixture);
            helper.assertTrue(fixture.encounter().contentDiagnostic().contains("missing"), "missing current history requires repair");
            helper.assertValueEqual(player(fixture).getInventory().countItem(Items.EMERALD), 0, "missing proof cannot create another reward plan");
            helper.assertFalse(fixture.encounter().save().contains("RewardHistory"), "saving does not turn missing history into empty history");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void olderPlayerDataCannotReplayADeliveredEncounterReward(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            var player = player(fixture);
            var original = player.saveWithoutId(new CompoundTag());
            defeat(helper, fixture);
            player.load(original);
            fixture.reload(); recover(fixture);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "older player data is not repaired by replaying an acknowledged reward");
            helper.assertTrue(fixture.encounter().contentDiagnostic().contains("older"), "conflicting native and scene saves require explicit repair");
        }
        helper.succeed();
    }

    private static void template(CampaignEncounterPersistenceGameTests.Fixture fixture, String json) {
        var errors = new java.util.ArrayList<String>();
        var replacement = com.jvn.villagerretaliation.scene.encounter.EncounterResources.parse(fixture.template.id(),
                com.google.gson.JsonParser.parseString(json).getAsJsonObject(), errors);
        fixture.helper.assertTrue(replacement != null && errors.isEmpty(), "replacement template is valid: " + errors);
        var server = fixture.base.base.server;
        var old = com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.current(server);
        var campaigns = com.jvn.villagerretaliation.campaign.content.CampaignResources.current(server).campaigns();
        var catalog = new com.jvn.villagerretaliation.quest.content.QuestContentCatalog(old.generation() + 1, old.compiledQuestCatalog(), old.dialogueCatalog(), old.quests(),
                old.objectiveEventQuestIds(), old.factQuestIds(), old.memoryEventQuestIds(), old.exclusiveGroupQuestIds(), old.triggerEventQuestIds(),
                old.scenes(), java.util.Map.of(replacement.id(), replacement), old.pools(), old.bundles(), old.localization(), old.rewards(), old.conversations());
        com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.installForTests(server, catalog, fixture.base.base.report);
        com.jvn.villagerretaliation.campaign.content.CampaignResources.installForTests(server,
                new com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog(catalog, campaigns, List.of()));
    }

    @GameTest(template = "empty", batch = "campaign_encounter_reward_recovery")
    public static void invalidRewardResourceIdentityPreservesTheEncounter(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            var player = player(fixture);
            fixture.block(); defeat(helper, fixture); fixture.restore();
            var snapshot = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var history = encounter(snapshot, fixture).getCompound("RewardHistory");
            history.getList("Batches", Tag.TAG_COMPOUND).getCompound(0).getCompound("Reward").putString("Item", "invalid:Broken identity");
            install(helper, fixture, snapshot);
            helper.assertTrue(fixture.encounter() != null, "invalid receipt content cannot discard its encounter");
            recover(fixture);
            helper.assertValueEqual(fixture.encounter().save().getCompound("RewardHistory"), history, "invalid resource identity remains available for repair");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "invalid reward content cannot grant items");
        }
        helper.succeed();
    }

    private static void legacy(GameTestHelper helper, boolean completed) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, PLAYER)) {
            fixture.run();
            var player = player(fixture);
            String operation = fixture.sceneId + "/encounter/" + fixture.encounter().id() + "/reward/completion/payment/player/" + player.getUUID();
            var receipt = fixture.scene().prepareReceipt(operation, SceneOperationReceipt.Kind.ITEM_GRANT, helper.getLevel().getGameTime());
            if (completed) receipt.completed(helper.getLevel().getGameTime(), "legacy delivery");
            var snapshot = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            snapshot.putInt("DataVersion", 20);
            encounter(snapshot, fixture).remove("RewardHistory");
            install(helper, fixture, snapshot);
            defeat(helper, fixture); recover(fixture);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "legacy receipt cannot authorize a second delivery");
            helper.assertValueEqual(fixture.scene().receipts().get(operation).state(), completed
                    ? SceneOperationReceipt.ReceiptState.COMPLETED : SceneOperationReceipt.ReceiptState.PREPARED, "migration preserves receipt evidence");
            if (!completed) helper.assertTrue(fixture.encounter().contentDiagnostic().contains("legacy"), "ambiguous legacy delivery has a repair diagnostic");
        }
        helper.succeed();
    }

    private static ServerPlayer player(CampaignEncounterPersistenceGameTests.Fixture fixture) {
        return fixture.base.base.server.getPlayerList().getPlayer(fixture.encounter().participants().iterator().next());
    }
    private static void defeat(GameTestHelper helper, CampaignEncounterPersistenceGameTests.Fixture fixture) {
        EncounterService.onDeath((LivingEntity) helper.getLevel().getEntity(fixture.encounter().spawned().iterator().next()), fixture.data());
    }
    private static void recover(CampaignEncounterPersistenceGameTests.Fixture fixture) {
        EncounterService.maintainCleanup(fixture.base.base.server, fixture.data());
    }
    private static CompoundTag grant(CampaignEncounterPersistenceGameTests.Fixture fixture) {
        return fixture.encounter().save().getCompound("RewardHistory").getList("Batches", Tag.TAG_COMPOUND).getCompound(0)
                .getList("Grants", Tag.TAG_COMPOUND).getCompound(0);
    }
    private static UUID operation(CompoundTag grant) {
        return UUID.nameUUIDFromBytes(("encounter-reward/" + grant.getString("Operation")).getBytes(StandardCharsets.UTF_8));
    }
    private static UUID initializeProfile(GameTestHelper helper, CampaignEncounterPersistenceGameTests.Fixture fixture) {
        UUID id = fixture.encounter().allies().get("guard").entityId();
        VillagerProfileManager.getOrCreateProfile(helper.getLevel(), (Villager) helper.getLevel().getEntity(id))
                .setSocialAttribute(VillagerSocialAttribute.GUTS, 10, helper.getLevel().getGameTime());
        return id;
    }
    private static int guts(GameTestHelper helper, UUID ally) {
        return VillagerProfileManager.getProfile(helper.getLevel(), ally).orElseThrow().socialAttributes().get(VillagerSocialAttribute.GUTS);
    }
    private static Path profilePath(CampaignEncounterPersistenceGameTests.Fixture fixture) {
        return fixture.path().resolveSibling(VillagerProfileSavedData.DATA_NAME + ".dat");
    }
    private static void reloadProfiles(GameTestHelper helper, CampaignEncounterPersistenceGameTests.Fixture fixture) throws IOException {
        helper.getLevel().getDataStorage().set(VillagerProfileSavedData.DATA_NAME,
                VillagerProfileSavedData.load(NbtIo.readCompressed(profilePath(fixture), NbtAccounter.unlimitedHeap()).getCompound("data"), helper.getLevel().registryAccess()));
    }
    private static CompoundTag encounter(CompoundTag snapshot, CampaignEncounterPersistenceGameTests.Fixture fixture) {
        return snapshot.getList("Encounters", Tag.TAG_COMPOUND).stream().map(value -> (CompoundTag) value)
                .filter(value -> value.getUUID("Id").equals(fixture.encounter().id())).findFirst().orElseThrow();
    }
    private static void install(GameTestHelper helper, CampaignEncounterPersistenceGameTests.Fixture fixture, CompoundTag snapshot) {
        fixture.base.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", SceneSavedData.load(snapshot, helper.getLevel().registryAccess()));
    }

    private static final class BlockedFile implements AutoCloseable {
        private final Path path;
        private final Path backup;
        private BlockedFile(Path path) throws IOException {
            this.path = path;
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                backup = Files.createTempFile(path.getParent(), "reward-backup-", ".dat");
                Files.delete(backup);
                Files.move(path, backup);
            } else backup = null;
            Files.createDirectory(path);
            Files.writeString(path.resolve("blocked"), "reward storage failure fixture");
        }
        @Override public void close() throws IOException {
            Files.delete(path.resolve("blocked")); Files.delete(path);
            if (backup != null) Files.move(backup, path);
        }
    }
}
