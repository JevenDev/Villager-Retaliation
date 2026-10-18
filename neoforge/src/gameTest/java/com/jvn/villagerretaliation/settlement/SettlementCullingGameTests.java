package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementCullingGameTests {
    private SettlementCullingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void cullingConsumesOneAnimalAndProducesItsNativeLoot(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var cows = cows(helper, fixture, 3);
        var saved = cows.getFirst().saveWithoutId(new CompoundTag());
        cull(helper, fixture);
        helper.assertValueEqual(fixture.town().livestock().size(), 2, "the per-type cap removes exactly one animal");
        helper.assertTrue(!fixture.town().itemDrops().isEmpty(), "the actual native death produces pending item entities");
        helper.assertTrue(fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue() > 0,
                "a successful cull wears the real equipped weapon");
        work(helper, fixture, 12);
        helper.assertTrue(carried(helper, fixture, Items.BEEF) > 0, "the handler collects actual native cow loot");
        int beef = carried(helper, fixture, Items.BEEF);
        work(helper, fixture, 8);
        helper.assertValueEqual(carried(helper, fixture, Items.BEEF), beef, "the retained herd cannot be culled below the cap");
        var returning = EntityType.COW.create(helper.getLevel());
        returning.load(saved);
        helper.assertFalse(SettlementEntityDeathService.onJoined(returning), "an older native body cannot revive the culled cow");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "native damage, loot and pickup do not force the unloaded pen");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void capsCountEligibleAdultsOfEachSelectedType(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        cows(helper, fixture, 3);
        for (int index = 0; index < 2; index++) record(helper, fixture, EntityType.PIG.create(helper.getLevel()));
        var named = EntityType.COW.create(helper.getLevel());
        named.setCustomName(Component.literal("Pet cow"));
        record(helper, fixture, named);
        var baby = EntityType.COW.create(helper.getLevel());
        baby.setAge(-24_000);
        record(helper, fixture, baby);
        for (int index = 0; index < 3; index++) {
            var horse = EntityType.HORSE.create(helper.getLevel());
            horse.setTamed(true);
            record(helper, fixture, horse);
        }
        work(helper, fixture, 16);
        helper.assertValueEqual(fixture.town().livestock().size(), 9, "only the one excess eligible cow is removed");
        helper.assertTrue(fixture.town().livestock().containsKey(named.getUUID()) && fixture.town().livestock().containsKey(baby.getUUID()),
                "named adults and babies remain protected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void disabledCullingAndTargetFiltersPreserveUnselectedAnimals(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        cows(helper, fixture, 3);
        HiredAnimalCullSettings.setCap(fixture.worker().settings(), 0);
        work(helper, fixture, 4);
        helper.assertValueEqual(fixture.town().livestock().size(), 3, "culling begins disabled unless configured");
        HiredAnimalCullSettings.setCap(fixture.worker().settings(), 2);
        HiredAnimalBreedingTargets.toggleTarget(fixture.worker().settings(), "minecraft:chicken");
        for (int index = 0; index < 3; index++) record(helper, fixture, EntityType.CHICKEN.create(helper.getLevel()));
        work(helper, fixture, 16);
        helper.assertValueEqual(fixture.town().livestock().values().stream().filter(state -> state.type().getPath().equals("cow")).count(),
                3L, "unselected excess cows remain alive");
        helper.assertValueEqual(fixture.town().livestock().values().stream().filter(state -> state.type().getPath().equals("chicken")).count(),
                2L, "the selected chicken pool reaches its own cap");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void missingWeaponsAndRoutesCannotProduceMeat(GameTestHelper helper) {
        var fixture = fixture(helper, false, false);
        cows(helper, fixture, 3);
        work(helper, fixture, 4);
        helper.assertValueEqual(fixture.worker().status(), "missing_cull_weapon", "culling requires an actual sword or axe");
        helper.assertValueEqual(fixture.town().livestock().size(), 3, "missing equipment cannot consume an animal");
        supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        for (var state : fixture.town().livestock().values()) state.access(fixture.worker().id(), fixture.worker().position(), -1);
        work(helper, fixture, 4);
        helper.assertValueEqual(fixture.worker().status(), "animal_unreachable", "an unobserved approach blocks culling");
        helper.assertTrue(fixture.town().itemDrops().isEmpty() && fixture.town().entityDeaths().isEmpty(), "blocked culls produce no death or loot records");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void canceledDeathPreservesTheNativeHookOutcome(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var cows = cows(helper, fixture, 3);
        var target = cows.getFirst();
        Consumer<LivingDeathEvent> cancel = event -> {
            if (event.getEntity().getUUID().equals(target.getUUID())) {
                event.getEntity().setHealth(5.0F);
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            for (int step = 0; step < 8 && !fixture.worker().status().equals("animal_cull_failed"); step++) work(helper, fixture, 1);
            helper.assertValueEqual(fixture.worker().status(), "animal_cull_failed", "a canceled native death is not successful culling");
            helper.assertValueEqual(fixture.town().livestock().size(), 3, "the canceled animal stays in the herd");
            helper.assertTrue(fixture.town().itemDrops().isEmpty() && fixture.town().entityDeaths().isEmpty(), "cancellation creates neither loot nor a deletion receipt");
            var state = fixture.town().livestock().get(target.getUUID());
            helper.assertValueEqual(state.context(helper.getLevel()).getHealth(), 5.0F, "the native cancellation hook's health change survives");
            helper.assertTrue(state.restore(target), "the returning body receives the recorded native interaction");
            helper.assertValueEqual(target.getHealth(), 5.0F, "returning the animal cannot restore its older health");
            helper.assertValueEqual(fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 0,
                    "unsuccessful culling does not charge native success-only tool wear");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void canceledNativeDropsStillRemoveTheActuallyCulledAnimal(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var target = cows(helper, fixture, 3).getFirst();
        Consumer<LivingDropsEvent> cancel = event -> {
            if (event.getEntity().getUUID().equals(target.getUUID())) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            cull(helper, fixture);
            helper.assertValueEqual(fixture.town().livestock().size(), 2, "drop cancellation does not undo an actual death");
            helper.assertTrue(fixture.town().itemDrops().isEmpty(), "canceled native loot never enters pending storage");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void deathDropHooksSupplyTheActualPendingStacks(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var target = cows(helper, fixture, 3).getFirst();
        Consumer<LivingDropsEvent> replace = event -> {
            if (!event.getEntity().getUUID().equals(target.getUUID())) return;
            event.getDrops().clear();
            var position = event.getEntity().position();
            var drop = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, new ItemStack(Items.FEATHER, 7));
            event.getDrops().add(drop);
            event.getDrops().add(drop);
        };
        NeoForge.EVENT_BUS.addListener(replace);
        try {
            cull(helper, fixture);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.FEATHER), 7, "the post-event native stack is delivered once even if the event lists its entity twice");
            helper.assertValueEqual(carried(helper, fixture, Items.BEEF), 0, "the background path does not invent a second cow loot table");
        } finally {
            NeoForge.EVENT_BUS.unregister(replace);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void mobLootRulesApplyToBackgroundCulling(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        cows(helper, fixture, 3);
        var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean previous = rule.get();
        rule.set(false, helper.getLevel().getServer());
        try {
            cull(helper, fixture);
            helper.assertTrue(fixture.town().itemDrops().isEmpty(), "disabled native mob loot produces no background goods");
            helper.assertValueEqual(fixture.town().livestock().size(), 2, "the configured cull still changes the real herd");
        } finally {
            rule.set(previous, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void mendingUsesTheActualOwnedDeathExperienceOnce(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), 1);
        sword.setDamageValue(20);
        supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, sword);
        cows(helper, fixture, 3);
        cull(helper, fixture);
        var experience = fixture.town().groundExperience().values().iterator().next();
        helper.assertTrue(experience.pendingSpawn(), "the native villager kill creates a persistent owned reward");
        SettlementExperienceTestTerrain.flat(fixture.town());
        int before = fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue();
        double progress = fixture.worker().progress();
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), experience.checkedAt() + 200L);
        int repaired = fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue();
        helper.assertTrue(repaired < before, "actual awarded experience repairs the equipped job weapon");
        helper.assertTrue(experience.removed(), "physical contact consumes the finite experience");
        helper.assertValueEqual(fixture.worker().progress(), progress, "passive pickup does not charge production progress");
        work(helper, fixture, 8);
        helper.assertValueEqual(fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(),
                repaired, "later work cannot repeat the same experience repair");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "Mending does not load the pen");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void fullOutputKeepsTheFiniteCullDropsUntilSpaceReturns(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        cows(helper, fixture, 3);
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            if (items.get(slot).isEmpty()) items.set(slot, new ItemStack(Items.STONE, 64));
        }
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
        cull(helper, fixture);
        int pending = fixture.town().itemDrops().values().stream().mapToInt(drop -> drop.item(helper.getLevel().registryAccess()).getCount()).sum();
        helper.assertTrue(pending > 0, "native culling leaves finite loot on the ground");
        work(helper, fixture, 8);
        helper.assertValueEqual(fixture.town().itemDrops().values().stream().mapToInt(drop -> drop.item(helper.getLevel().registryAccess()).getCount()).sum(),
                pending, "full output cannot delete or duplicate the ground loot");
        items = fixture.worker().items(helper.getLevel().registryAccess());
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) items.set(slot, ItemStack.EMPTY);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
        work(helper, fixture, 16);
        helper.assertValueEqual(carried(helper, fixture, Items.BEEF) + carried(helper, fixture, Items.LEATHER), pending,
                "freeing capacity collects exactly the pending native stacks");
        helper.assertTrue(fixture.town().itemDrops().isEmpty(), "collected loot is no longer on the ground");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void cullingAndPendingLootSurviveSettlementSaves(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        cows(helper, fixture, 3);
        cull(helper, fixture);
        var saved = fixture.town().save(helper.getLevel().registryAccess());
        var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.livestock().size(), 2, "the saved herd excludes the consumed animal");
        helper.assertValueEqual(restored.entityDeaths().size(), 1, "the pending native-body deletion survives the same save");
        helper.assertValueEqual(restored.itemDrops().size(), fixture.town().itemDrops().size(), "actual produced loot survives alongside its source death");
        var death = restored.entityDeaths().values().iterator().next();
        helper.assertFalse(restored.livestock().containsKey(death.id()), "death and living livestock cannot both consume the same animal");
        saved.remove("EntityDeaths");
        helper.assertTrue(SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {}).entityDeaths().isEmpty(),
                "older settlement saves require no pending-death field");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void aLoadedCulledAnimalLeavesAReceiptForOlderNativeBodies(GameTestHelper helper) {
        var fixture = fixture(helper, true, true);
        var cows = cows(helper, fixture, 3);
        var first = cows.getFirst();
        var saved = first.saveWithoutId(new CompoundTag());
        try {
            cull(helper, fixture);
            helper.assertFalse(first.isAlive(), "background culling damages the actual loaded native animal");
            first.discard();
            var replay = EntityType.COW.create(helper.getLevel());
            replay.load(saved);
            helper.assertFalse(helper.getLevel().addFreshEntity(replay), "the chunk death receipt rejects an older healthy body");
            helper.assertTrue(helper.getLevel().getEntity(first.getUUID()) == null, "the old native identity remains absent");
            work(helper, fixture, 16);
            helper.assertTrue(carried(helper, fixture, Items.BEEF) > 0, "native world loot remains collectable after body reconciliation");
        } finally {
            cows.forEach(Animal::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void scopedBackgroundCaptureDoesNotTakeOtherNativeDeaths(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var victim = EntityType.COW.create(helper.getLevel());
        victim.setPos(fixture.town().center().getBottomCenter());
        var other = EntityType.PIG.create(helper.getLevel());
        var position = helper.absolutePos(new BlockPos(3, 2, 3));
        other.setPos(position.getBottomCenter());
        other.setNoAi(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(other), "an unrelated native animal is loaded");
        try (var capture = new SettlementDeathCapture(victim)) {
            other.hurt(helper.getLevel().damageSources().mobAttack(fixture.worker().context(helper.getLevel(), fixture.town())), 1000.0F);
            helper.assertFalse(other.isAlive(), "the unrelated actual death completes normally");
            helper.assertTrue(capture.drops().isEmpty() && capture.experience().isEmpty() && !capture.accepted(),
                    "capture is tied to the exact intended victim");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, other.getBoundingBox().inflate(2),
                    item -> item.getItem().is(Items.PORKCHOP));
            helper.assertFalse(drops.isEmpty(), "unrelated death loot still enters the native world");
            drops.forEach(ItemEntity::discard);
        } finally {
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "settlement_culling_experience")
    public static void deathExperienceDeliveryAndOldSaveReplayAreIdempotent(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 2, 2)).offset(10_000_000, 0, 10_000_000);
        var center = new BlockPos((origin.getX() >> 4 << 4) + 14, 80, (origin.getZ() >> 4 << 4) + 14);
        var fixture = fixture(helper, false, true, center);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), 1);
        sword.setDamageValue(20);
        supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, sword);
        cows(helper, fixture, 3);
        cull(helper, fixture);
        fixture.town().removeDuty(fixture.worker().id());
        var death = fixture.town().entityDeaths().values().iterator().next();
        var experience = fixture.town().groundExperience().values().iterator().next();
        var saved = death.save();
        var legacy = new net.minecraft.nbt.CompoundTag();
        legacy.put("Entity", experience.context(helper.getLevel()).saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        legacy.putInt("Steps", -1);
        legacy.putLong("CheckedAt", experience.checkedAt());
        var rewards = new net.minecraft.nbt.ListTag();
        rewards.add(legacy);
        saved.put("Experience", rewards);
        var level = helper.getLevel();
        var position = new net.minecraft.world.level.ChunkPos(experience.position());
        for (var floor : BlockPos.betweenClosed(experience.position().offset(-4, -1, -4), experience.position().offset(4, -1, 4))) {
            level.setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 4, position);
        level.getChunk(position.x, position.z);
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo original, net.minecraft.gametest.framework.GameTestInfo rerun, net.minecraft.gametest.framework.GameTestRunner runner) { }
            private void cleanup() {
                level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 4, position);
            }
        });
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(experience.position())) && level.isPositionEntityTicking(experience.position()),
                    "the actual experience chunk becomes active");
        }).thenWaitUntil(() -> {
            SettlementEntityDeathService.update(level, fixture.town(), level.getGameTime());
            SettlementGroundExperienceService.update(level, fixture.town(), level.getGameTime());
            var orb = level.getEntity(experience.id());
            helper.assertTrue(orb instanceof net.minecraft.world.entity.ExperienceOrb,
                    "pending experience becomes an actual native orb at " + experience.position()
                            + ", removed=" + experience.removed() + ", pending=" + experience.pendingSpawn());
        }).thenExecute(() -> {
            var orb = level.getEntity(experience.id());
            helper.assertTrue(fixture.town().groundExperience().containsKey(experience.id()), "delivered death experience enters native transition tracking");
            helper.assertTrue(com.jvn.villagerretaliation.villager.VillagerWorkExperience.belongsTo(
                    (net.minecraft.world.entity.ExperienceOrb) orb, fixture.worker().context(level, fixture.town())),
                    "native delivery retains work-experience ownership");
            orb.discard();
            fixture.town().recordEntityDeath(SettlementEntityDeath.load(saved));
            SettlementEntityDeathService.update(level, fixture.town(), level.getGameTime());
            helper.assertTrue(level.getEntity(experience.id()) == null, "an older pending snapshot cannot recreate collected experience");
            helper.assertTrue(fixture.town().entityDeaths().isEmpty(), "both native-body and experience receipts acknowledge the old save");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void uncollectedDeathExperienceExpiresWithoutOfflineCatchup(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), 1);
        supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, sword);
        cows(helper, fixture, 3);
        cull(helper, fixture);
        var death = fixture.town().entityDeaths().values().iterator().next();
        var experience = fixture.town().groundExperience().values().iterator().next();
        long future = helper.getLevel().getGameTime() + 48_000L;
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), future);
        helper.assertValueEqual((int) experience.save().getCompound("Entity").getShort("Age"), 200, "a clock gap ages experience for one bounded interval");
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), future);
        helper.assertValueEqual((int) experience.save().getCompound("Entity").getShort("Age"), 200, "the same interval cannot age experience twice");
        for (int step = 1; step <= 29; step++) SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), future + step * 200L);
        helper.assertTrue(experience.removed(), "uncollected native experience expires after its running lifespan");
        helper.assertFalse(death.applied(), "experience expiry does not erase the pending animal deletion");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_culling_chunks")
    public static void aRealUnloadedHerdReturnsWithoutItsCulledAnimal(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var cows = cows(helper, fixture, 3);
        var level = helper.getLevel();
        var position = new net.minecraft.world.level.ChunkPos(fixture.town().center());
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 0, position);
        level.getChunk(position.x, position.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "the actual herd chunk is ready");
        }).thenExecute(() -> {
            for (var cow : cows) helper.assertTrue(level.addFreshEntity(cow), "the actual cow enters its native chunk");
            SettlementLivestockService.observeArea(level, fixture.town(), fixture.area());
            level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 0, position);
        }).thenWaitUntil(() -> {
            helper.assertFalse(level.areEntitiesLoaded(position.toLong()) || level.hasChunkAt(fixture.town().center()),
                    "the actual herd unloads to entity storage");
        }).thenExecute(() -> {
            cull(helper, fixture);
            helper.assertFalse(level.hasChunkAt(fixture.town().center()), "native background culling leaves the real chunk unloaded");
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 0, position);
            level.getChunk(position.x, position.z);
        }).thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "the saved herd reloads");
        }).thenExecute(() -> {
            try {
                helper.assertTrue(level.getEntity(cows.getFirst().getUUID()) == null, "the culled native identity is rejected during real reload");
                for (int index = 1; index < cows.size(); index++) {
                    helper.assertTrue(level.getEntity(cows.get(index).getUUID()) instanceof Cow, "surviving native animals remain present");
                }
                work(helper, fixture, 16);
                helper.assertTrue(carried(helper, fixture, Items.BEEF) > 0, "returning to the pen keeps its finite earned loot");
                helper.assertValueEqual(fixture.town().livestock().size(), 2, "the authoritative herd still respects the configured cap");
            } finally {
                for (var cow : cows) {
                    var live = level.getEntity(cow.getUUID());
                    if (live != null) live.discard();
                }
                level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 0, position);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "settlement_culling_birth")
    public static void aCulledMaturedPendingBirthCannotSpawnOnReturn(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var first = cows(helper, fixture, 3).getFirst();
        var birth = SettlementAnimalBirth.create(first, 0);
        fixture.town().recordAnimalBirth(birth);
        cull(helper, fixture);
        helper.assertTrue(birth.childApplied(), "culling consumes the already grown pending child identity");
        var level = helper.getLevel();
        var position = new net.minecraft.world.level.ChunkPos(first.blockPosition());
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 0, position);
        level.getChunk(position.x, position.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "the pending birth's chunk becomes available");
        }).thenExecute(() -> {
            try {
                helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "the consumed birth can finish reconciling");
                helper.assertTrue(level.getEntity(first.getUUID()) == null, "birth delivery cannot recreate the culled adult");
                SettlementEntityDeathService.update(level, fixture.town(), level.getGameTime());
                helper.assertFalse(level.addFreshEntity(first), "the saved death receipt also rejects an older native child body");
            } finally {
                level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 0, position);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_culling")
    public static void nativeRevivalAfterLootRetainsItsActualDropsAndLivingAnimal(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), 1);
        sword.setDamageValue(20);
        supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, sword);
        var target = cows(helper, fixture, 3).getFirst();
        Consumer<LivingDropsEvent> revive = event -> {
            if (!event.getEntity().getUUID().equals(target.getUUID())) return;
            event.getEntity().setHealth(5.0F);
            event.getDrops().clear();
            var position = event.getEntity().position();
            event.getDrops().add(new ItemEntity(helper.getLevel(), position.x, position.y, position.z, new ItemStack(Items.FEATHER, 3)));
        };
        NeoForge.EVENT_BUS.addListener(revive);
        try {
            for (int step = 0; step < 8 && !fixture.worker().status().equals("animal_cull_failed"); step++) work(helper, fixture, 1);
            helper.assertValueEqual(fixture.town().livestock().size(), 3, "a native hook that restores life preserves the herd member");
            helper.assertTrue(fixture.town().livestock().get(target.getUUID()).context(helper.getLevel()).isAlive(), "the recorded native revival stays alive");
            helper.assertFalse(fixture.town().entityDeaths().containsKey(target.getUUID()), "experience from the attempt does not authorize deleting the revived body");
            helper.assertValueEqual(fixture.town().itemDrops().values().stream().mapToInt(drop -> drop.item(helper.getLevel().registryAccess()).getCount()).sum(),
                    3, "already approved native loot is preserved even when the target revives");
            helper.assertTrue(fixture.town().groundExperience().values().stream().anyMatch(experience -> !experience.removed()),
                    "native experience produced before the revival remains finite and pending");
        } finally {
            NeoForge.EVENT_BUS.unregister(revive);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "settlement_culling_moved_death")
    public static void nativeDeathHooksCannotMoveTheOriginalBodyDeletionReceipt(GameTestHelper helper) {
        var fixture = fixture(helper, false, true);
        var target = cows(helper, fixture, 3).getFirst();
        var original = target.blockPosition();
        var destination = original.east(48);
        Consumer<LivingDropsEvent> move = event -> {
            if (event.getEntity().getUUID().equals(target.getUUID())) event.getEntity().setPos(destination.getBottomCenter());
        };
        NeoForge.EVENT_BUS.addListener(move);
        try {
            cull(helper, fixture);
        } finally {
            NeoForge.EVENT_BUS.unregister(move);
        }
        var death = fixture.town().entityDeaths().get(target.getUUID());
        helper.assertValueEqual(death.position(), original, "deletion remains attached to the actual saved native body position");
        var level = helper.getLevel();
        var position = new net.minecraft.world.level.ChunkPos(destination);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 0, position);
        level.getChunk(position.x, position.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "the hook's destination chunk loads");
        }).thenExecute(() -> {
            try {
                helper.assertFalse(level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(original)), "the actual body chunk remains unloaded");
                SettlementEntityDeathService.update(level, fixture.town(), level.getGameTime());
                helper.assertTrue(fixture.town().entityDeaths().containsKey(target.getUUID()), "the empty destination is not proof that the original body was removed");
                helper.assertFalse(SettlementEntityDeathService.onJoined(target), "returning the original body still applies its pending death");
            } finally {
                level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 0, position);
            }
        }).thenSucceed();
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded, boolean weapon) {
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        return fixture(helper, loaded, weapon, loaded ? origin : origin.offset(10_000_000, 0, 10_000_000).atY(80));
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded, boolean weapon, BlockPos center) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Pen", center.offset(-2, -1, -2), center.offset(10, 4, 10), 5, true);
        town.putArea(town.leader(), area);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getBottomCenter());
        villager.setOnGround(true);
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        HiredAnimalCullSettings.setCap(HiredWorkStateStore.state(villager), 2);
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        var fixture = new Fixture(town, area, worker, loaded);
        if (weapon) supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        return fixture;
    }

    private static List<Cow> cows(GameTestHelper helper, Fixture fixture, int count) {
        var cows = new ArrayList<Cow>();
        for (int index = 0; index < count; index++) {
            var cow = EntityType.COW.create(helper.getLevel());
            record(helper, fixture, cow);
            cows.add(cow);
        }
        return cows;
    }

    private static void record(GameTestHelper helper, Fixture fixture, Animal animal) {
        animal.setPos(fixture.town().center().east().getBottomCenter());
        animal.setNoAi(true);
        animal.setOnGround(true);
        if (fixture.loaded()) helper.assertTrue(helper.getLevel().addFreshEntity(animal), "the native test animal enters its loaded pen");
        var state = SettlementLivestock.capture(animal, fixture.area().id(), 0L);
        state.access(fixture.worker().id(), fixture.worker().position(), 0);
        fixture.town().recordLivestock(state);
    }

    private static void cull(GameTestHelper helper, Fixture fixture) {
        int before = fixture.town().livestock().size();
        for (int attempt = 0; attempt < 8 && fixture.town().livestock().size() == before; attempt++) work(helper, fixture, 1);
        helper.assertTrue(fixture.town().livestock().size() < before, "the funded native cull completes");
    }

    private static void work(GameTestHelper helper, Fixture fixture, int intervals) {
        for (int step = 0; step < intervals; step++) SettlementBackgroundAnimalHandling.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
    }

    private static void supply(GameTestHelper helper, Fixture fixture, int slot, ItemStack item) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        items.set(slot, item);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static int carried(GameTestHelper helper, Fixture fixture, net.minecraft.world.item.Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker, boolean loaded) {
    }
}
