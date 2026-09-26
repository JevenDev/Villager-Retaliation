package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementLivestockBreedingGameTests {
    private SettlementLivestockBreedingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void commonLivestockBreedingConsumesTwoFoodsAndRecordsOneBirth(GameTestHelper helper) {
        fixture(helper, false, EntityType.COW, fixture -> {
            supply(helper, fixture, new ItemStack(Items.WHEAT, 2));
            work(helper, fixture, 10);
            helper.assertValueEqual(carried(helper, fixture, Items.WHEAT), 0, "feeding consumes exactly two real foods");
            helper.assertValueEqual(fixture.first().context(helper.getLevel()).getInLoveTime(), 600, "feeding starts the native love timer");
            helper.assertTrue(fixture.town().animalBirths().isEmpty(), "feeding does not create an immediate child");
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.town().animalBirths().size(), 1, "a completed mating records one offspring");
            helper.assertValueEqual(fixture.town().livestock().size(), 3, "the offspring joins the saved livestock population");
            helper.assertValueEqual(fixture.first().context(helper.getLevel()).getAge(), 5860,
                    "parents spend the remaining interval in their native cooldown");
            var birth = birth(fixture);
            helper.assertValueEqual(fixture.town().livestock().get(birth.id()).context(helper.getLevel()).getAge(), -23860,
                    "the baby ages through the remainder of its birth interval");
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.town().animalBirths().size(), 1, "repeated updates cannot replay the birth");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "breeding never loads the pen");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void insufficientFoodPreservesTheSingleRemainingInput(GameTestHelper helper) {
        fixture(helper, false, EntityType.PIG, fixture -> {
            supply(helper, fixture, new ItemStack(Items.CARROT));
            work(helper, fixture, 10);
            helper.assertValueEqual(carried(helper, fixture, Items.CARROT), 1, "one food cannot partially feed a pair");
            helper.assertValueEqual(fixture.first().context(helper.getLevel()).getInLoveTime(), 0, "failed feeding leaves parent state unchanged");
            helper.assertValueEqual(fixture.worker().status(), "missing_breeding_food", "the blocker identifies the missing input");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void breedingFetchesOnlyPermittedFiniteDepotFood(GameTestHelper helper) {
        fixture(helper, false, EntityType.PIG, fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.CARROT, 3));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            work(helper, fixture, 12);
            helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 3, "disabled supply access preserves depot food");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 1, "feeding withdraws only the two required carrots");
            helper.assertValueEqual(carried(helper, fixture, Items.CARROT), 0, "the fetched food is consumed rather than copied");
            helper.assertTrue(fixture.first().context(helper.getLevel()).isInLove(), "permitted supplies resume breeding work");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void animalFiltersAndObservedAccessAlsoGateFeeding(GameTestHelper helper) {
        fixture(helper, false, EntityType.PIG, fixture -> {
            supply(helper, fixture, new ItemStack(Items.CARROT, 2));
            HiredAnimalBreedingTargets.toggleTarget(fixture.worker().settings(), "minecraft:cow");
            work(helper, fixture, 10);
            helper.assertValueEqual(carried(helper, fixture, Items.CARROT), 2, "excluded species cannot consume breeding food");
            HiredAnimalBreedingTargets.toggleTarget(fixture.worker().settings(), "all");
            fixture.second().access(fixture.worker().id(), fixture.worker().position(), -1);
            work(helper, fixture, 10);
            helper.assertValueEqual(carried(helper, fixture, Items.CARROT), 2, "unobserved access cannot feed the second parent");
            helper.assertValueEqual(fixture.worker().status(), "animal_unreachable", "feeding exposes the missing route");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void distantPairsAndParentCooldownsCannotConsumeMoreFood(GameTestHelper helper) {
        fixture(helper, false, EntityType.PIG, fixture -> {
            supply(helper, fixture, new ItemStack(Items.CARROT, 4));
            Animal second = fixture.second().context(helper.getLevel());
            second.setPos(fixture.town().center().east(7).getBottomCenter());
            fixture.second().observe(second, 0L);
            fixture.second().access(fixture.worker().id(), fixture.worker().position(), 0);
            work(helper, fixture, 10);
            helper.assertValueEqual(carried(helper, fixture, Items.CARROT), 4, "pairs outside native feeding reach remain unfed");
            second.setPos(fixture.town().center().east(2).getBottomCenter());
            second.setAge(6000);
            fixture.second().observe(second, 0L);
            fixture.second().access(fixture.worker().id(), fixture.worker().position(), 0);
            work(helper, fixture, 10);
            helper.assertValueEqual(carried(helper, fixture, Items.CARROT), 4, "an existing parent cooldown protects breeding inputs");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void workerReturnKeepsSpentBreedingFoodSpent(GameTestHelper helper) {
        fixture(helper, false, EntityType.CHICKEN, fixture -> {
            supply(helper, fixture, new ItemStack(Items.WHEAT_SEEDS, 2));
            work(helper, fixture, 10);
            SettlementBackgroundService.onActive(fixture.villager());
            SettlementBackgroundService.onActive(fixture.villager());
            helper.assertTrue(HiredJobInventory.getJobInventory(fixture.villager()).findSupply(stack -> stack.is(Items.WHEAT_SEEDS)).isEmpty(),
                    "returning the worker cannot restore consumed seeds");
            fixture.first().restore(fixture.firstEntity());
            fixture.first().restore(fixture.firstEntity());
            helper.assertValueEqual(fixture.firstEntity().getInLoveTime(), 600, "the parent's feeding change also applies once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void twoAnimalsWithDisabledAiDoNotCompleteMating(GameTestHelper helper) {
        fixture(helper, false, EntityType.PIG, fixture -> {
            for (var state : List.of(fixture.first(), fixture.second())) {
                var animal = state.context(helper.getLevel());
                animal.setNoAi(true);
                state.observe(animal, 0L);
                state.feed();
            }
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.town().animalBirths().isEmpty(), "disabled animal AI cannot produce an offspring");
            var active = fixture.second().context(helper.getLevel());
            active.setNoAi(false);
            fixture.second().observe(active, 200L);
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.town().animalBirths().size(), 1, "one active initiator can mate with the other eligible parent");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void sheepOffspringUseNativeColorInheritance(GameTestHelper helper) {
        fixture(helper, false, EntityType.SHEEP, fixture -> {
            Sheep first = (Sheep) fixture.first().context(helper.getLevel());
            Sheep second = (Sheep) fixture.second().context(helper.getLevel());
            first.setColor(DyeColor.RED);
            second.setColor(DyeColor.YELLOW);
            fixture.first().observe(first, 0L);
            fixture.second().observe(second, 0L);
            ready(fixture);
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            var child = (Sheep) fixture.town().livestock().get(birth(fixture).id()).context(helper.getLevel());
            helper.assertValueEqual(child.getColor(), DyeColor.ORANGE, "native dye recipes determine the lamb's inherited color");
            helper.assertTrue(child.isBaby() && !child.isSheared(), "the child begins as a native unshorn lamb");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void matingProgressSurvivesSaveAndDoesNotReplayCheckpoints(GameTestHelper helper) {
        fixture(helper, false, EntityType.COW, fixture -> {
            ready(fixture);
            var level = helper.getLevel();
            SettlementLivestockService.update(level, fixture.town(), 20L);
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            SettlementLivestockService.update(level, restored, 20L);
            helper.assertTrue(restored.animalBirths().isEmpty(), "a repeated checkpoint cannot finish mating early");
            SettlementLivestockService.update(level, restored, 40L);
            helper.assertTrue(restored.animalBirths().isEmpty(), "two partial intervals remain below native mating time");
            SettlementLivestockService.update(level, restored, 60L);
            helper.assertValueEqual(restored.animalBirths().size(), 1, "the third interval completes the saved courtship");
            var saved = restored.save(level.registryAccess());
            saved.remove("AnimalBirths");
            helper.assertTrue(SettlementRecord.load(saved, level.registryAccess(), () -> {}).animalBirths().isEmpty(),
                    "older saves load without inventing pending births");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void canceledNativeBirthEventsStillApplyParentCooldowns(GameTestHelper helper) {
        fixture(helper, false, EntityType.COW, fixture -> {
            ready(fixture);
            Consumer<BabyEntitySpawnEvent> cancel = event -> {
                if (event.getParentA().getUUID().equals(fixture.first().id())) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
                helper.assertTrue(fixture.town().animalBirths().isEmpty(), "a canceled native birth creates no child or experience");
                helper.assertValueEqual(fixture.first().context(helper.getLevel()).getAge(), 5860, "cancellation retains native parent cooldown");
                helper.assertValueEqual(fixture.first().context(helper.getLevel()).getInLoveTime(), 0, "cancellation resets love");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void liveParentsRemainUnderNativeMatingControl(GameTestHelper helper) {
        fixture(helper, true, EntityType.COW, fixture -> {
            ready(fixture);
            fixture.first().restore(fixture.firstEntity());
            fixture.second().restore(fixture.secondEntity());
            helper.getLevel().addFreshEntity(fixture.firstEntity());
            helper.getLevel().addFreshEntity(fixture.secondEntity());
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime() + 200L);
            helper.assertTrue(fixture.town().animalBirths().isEmpty(), "live entity ticking prevents concurrent background mating");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void birthAndExperienceAppearOnceWhenTheChunkIsReady(GameTestHelper helper) {
        fixture(helper, true, EntityType.PIG, fixture -> {
            var birth = commit(helper, fixture);
            var level = helper.getLevel();
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "a ready loaded pen receives the pending birth");
            helper.assertTrue(level.getEntity(birth.id()) instanceof Animal, "the saved offspring identity becomes a real entity");
            helper.assertTrue(level.getEntity(birth.experienceId()) instanceof ExperienceOrb, "native breeding experience is delivered separately");
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "repeated delivery recognizes the chunk receipt");
            helper.assertTrue(fixture.town().animalBirths().isEmpty(), "the completed birth leaves no pending delivery");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void anOlderBirthJournalCannotResurrectRemovedOffspring(GameTestHelper helper) {
        fixture(helper, true, EntityType.COW, fixture -> {
            var birth = commit(helper, fixture);
            var level = helper.getLevel();
            var saved = fixture.town().save(level.registryAccess());
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "the first delivery completes");
            level.getEntity(birth.id()).discard();
            level.getEntity(birth.experienceId()).discard();
            var stale = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, stale, stale.animalBirths().get(birth.id())),
                    "the chunk recognizes the older journal");
            helper.assertTrue(level.getEntity(birth.id()) == null && level.getEntity(birth.experienceId()) == null,
                    "consumed experience and removed offspring are not recreated");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void blockedBirthsRemainSavedAndContinueAging(GameTestHelper helper) {
        fixture(helper, true, EntityType.PIG, fixture -> {
            var birth = commit(helper, fixture);
            var level = helper.getLevel();
            level.setBlock(birth.position(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            helper.assertFalse(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "a birth cannot overwrite obstructing terrain");
            SettlementLivestockService.update(level, fixture.town(), 400L);
            var child = fixture.town().livestock().get(birth.id());
            helper.assertTrue(child != null && child.context(level).getAge() == -23660, "a blocked newborn remains in the ledger and ages");
            helper.assertTrue(level.getBlockState(birth.position()).is(Blocks.DIAMOND_BLOCK), "the blocking player build survives");
            level.setBlock(birth.position(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "clearing the obstruction resumes the same birth");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void disabledMobLootCreatesNoBreedingExperience(GameTestHelper helper) {
        fixture(helper, false, EntityType.CHICKEN, fixture -> {
            var level = helper.getLevel();
            boolean previous = level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
            try {
                level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false, level.getServer());
                ready(fixture);
                SettlementLivestockService.update(level, fixture.town(), 200L);
                helper.assertValueEqual(birth(fixture).experience(), 0, "the native mob-loot rule prevents breeding experience");
            } finally {
                level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(previous, level.getServer());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void pigAndChickenSurveysRecordTheNewBreedingResources(GameTestHelper helper) {
        fixture(helper, true, EntityType.CHICKEN, fixture -> {
            var level = helper.getLevel();
            fixture.town().removeLivestock(fixture.first().id());
            level.addFreshEntity(fixture.firstEntity());
            SettlementLivestockService.observeArea(level, fixture.town(), fixture.area());
            helper.assertTrue(fixture.town().livestock().containsKey(fixture.first().id()), "ordinary pen observation records chickens");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void deliveredBirthsBeyondTheBatchLimitCannotRetainRemovedAnimals(GameTestHelper helper) {
        fixture(helper, true, EntityType.COW, fixture -> {
            var level = helper.getLevel();
            for (int index = 0; index < 16; index++) {
                var queued = EntityType.COW.create(level);
                queued.setBaby(true);
                queued.setPos(fixture.town().center().offset(8_500_000, 0, 8_500_000).getBottomCenter());
                fixture.town().recordAnimalBirth(SettlementAnimalBirth.create(queued, 0));
            }
            ready(fixture);
            SettlementLivestockBreeding.advance(level, fixture.town(),
                    List.of(SettlementLivestockBreeding.step(level, fixture.first(), 200L),
                            SettlementLivestockBreeding.step(level, fixture.second(), 200L)), 200L);
            var birth = fixture.town().animalBirths().values().stream()
                    .filter(entry -> fixture.town().livestock().containsKey(entry.id())).findFirst().orElseThrow();
            fixture.cleanup().add(birth.experienceId());
            var saved = fixture.town().save(level.registryAccess());
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "the later queued birth is delivered");
            level.getEntity(birth.id()).discard();
            level.getEntity(birth.experienceId()).discard();
            var stale = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            SettlementLivestockService.update(level, stale, 400L);
            helper.assertFalse(stale.livestock().containsKey(birth.id()),
                    "the receipt removes stale livestock even when earlier queued births fill the delivery batch");
            helper.assertTrue(level.getEntity(birth.id()) == null && level.getEntity(birth.experienceId()) == null,
                    "processing the backlog does not recreate consumed entities");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void aChildReceiptResumesOnlyTheUndeliveredExperience(GameTestHelper helper) {
        fixture(helper, true, EntityType.COW, fixture -> {
            var level = helper.getLevel();
            var birth = commit(helper, fixture);
            var saved = fixture.town().save(level.registryAccess());
            var chunk = level.getChunkAt(birth.position());
            SettlementChunkReceipts.applied(chunk, "AnimalBirth:" + fixture.town().id().value() + ":" + birth.id(), birth.id(), 1L);
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "the partial receipt resumes delivery");
            helper.assertTrue(level.getEntity(birth.id()) == null, "an already delivered child is not recreated");
            helper.assertTrue(level.getEntity(birth.experienceId()) instanceof ExperienceOrb, "the remaining experience is delivered");
            level.getEntity(birth.experienceId()).discard();
            var stale = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            SettlementAnimalBirthService.materialize(level, stale, stale.animalBirths().get(birth.id()));
            helper.assertTrue(level.getEntity(birth.experienceId()) == null, "the completed receipt prevents repeated experience");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void grownOffspringCanUseTheObservedRouteToTheirBirthplace(GameTestHelper helper) {
        fixture(helper, false, EntityType.COW, fixture -> {
            var level = helper.getLevel();
            var birth = commit(helper, fixture);
            for (long now = 400L; now <= 24_200L; now += 200L) SettlementLivestockService.update(level, fixture.town(), now);
            var child = fixture.town().livestock().get(birth.id());
            helper.assertValueEqual(child.context(level).getAge(), 0, "the saved offspring grows into an adult");
            supply(helper, fixture, new ItemStack(Items.WHEAT, 2));
            work(helper, fixture, 10);
            helper.assertTrue(child.context(level).isInLove(), "the handler can feed an adult at its parent's observed location");
            helper.assertValueEqual(carried(helper, fixture, Items.WHEAT), 0, "later generations still consume real food");
            helper.assertFalse(level.hasChunkAt(fixture.town().center()), "later generations require no forced chunk load");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_breeding")
    public static void expiringLoveCannotCompleteAnUnfinishedMating(GameTestHelper helper) {
        fixture(helper, false, EntityType.COW, fixture -> {
            var level = helper.getLevel();
            for (var state : List.of(fixture.first(), fixture.second())) {
                var animal = state.context(level);
                animal.setInLoveTime(20);
                state.observe(animal, 0L);
            }
            SettlementLivestockService.update(level, fixture.town(), 200L);
            SettlementLivestockService.update(level, fixture.town(), 400L);
            helper.assertTrue(fixture.town().animalBirths().isEmpty(), "twenty ticks of remaining love cannot complete sixty ticks of mating");
            helper.assertValueEqual(fixture.first().context(level).getAge(), 0, "an unfinished mating does not impose a parent cooldown");
        });
    }

    private static void ready(Fixture fixture) {
        fixture.first().feed();
        fixture.second().feed();
    }

    private static SettlementAnimalBirth commit(GameTestHelper helper, Fixture fixture) {
        ready(fixture);
        var level = helper.getLevel();
        var frames = List.of(SettlementLivestockBreeding.step(level, fixture.first(), 200L),
                SettlementLivestockBreeding.step(level, fixture.second(), 200L));
        SettlementLivestockBreeding.advance(level, fixture.town(), frames, 200L);
        return birth(fixture);
    }

    private static SettlementAnimalBirth birth(Fixture fixture) {
        var birth = fixture.town().animalBirths().values().iterator().next();
        fixture.cleanup().add(birth.experienceId());
        return birth;
    }

    private static int carried(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static void supply(GameTestHelper helper, Fixture fixture, ItemStack stack) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        items.set(fixture.worker().supplySlots().getFirst(), stack);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static void work(GameTestHelper helper, Fixture fixture, int intervals) {
        for (int index = 0; index < intervals; index++) {
            SettlementBackgroundAnimalHandling.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
        }
    }

    private static void fixture(GameTestHelper helper, boolean loaded, EntityType<? extends Animal> type, Consumer<Fixture> check) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos center = loaded ? origin : origin.offset(8_500_000, 0, 8_500_000).atY(80);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Pen",
                center.offset(-2, -1, -2), center.offset(8, 4, 8), 5, true);
        town.putArea(town.leader(), area);
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage",
                center.east(12), center.offset(15, 3, 3), 5, true);
        town.putArea(town.leader(), storage);
        var depot = town.registerDepot(town.leader(), storage.id(), storage.min(), SettlementDepotPurpose.MATERIALS);
        depot.observeRules(List.of());
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getBottomCenter());
        villager.setOnGround(true);
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        Animal first = type.create(level);
        Animal second = type.create(level);
        first.setPos(center.east().getBottomCenter());
        second.setPos(center.east(2).getBottomCenter());
        var one = SettlementLivestock.capture(first, area.id(), 0L);
        var two = SettlementLivestock.capture(second, area.id(), 0L);
        one.access(worker.id(), worker.position(), 0);
        two.access(worker.id(), worker.position(), 0);
        town.recordLivestock(one);
        town.recordLivestock(two);
        var cleanup = new ArrayList<UUID>();
        try {
            check.accept(new Fixture(town, area, depot, villager, worker, first, second, one, two, cleanup));
        } finally {
            cleanup.addAll(town.livestock().keySet());
            for (UUID entity : cleanup) {
                if (level.getEntity(entity) != null) level.getEntity(entity).discard();
            }
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot depot, Villager villager,
                           SettlementWorkerState worker, Animal firstEntity, Animal secondEntity,
                           SettlementLivestock first, SettlementLivestock second, List<UUID> cleanup) {
    }
}
