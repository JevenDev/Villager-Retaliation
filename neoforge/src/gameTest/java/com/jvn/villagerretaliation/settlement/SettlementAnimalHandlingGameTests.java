package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementAnimalHandlingGameTests {
    private SettlementAnimalHandlingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void backgroundShearingUsesOneRecordedFleeceAndRealShears(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.RED);
            var animal = record(helper, fixture, sheep);
            supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
            work(helper, fixture, 8);
            int wool = carried(helper, fixture, Items.RED_WOOL);
            helper.assertTrue(wool >= 1 && wool <= 3, "one observed red fleece produces the native wool amount");
            helper.assertValueEqual(fixture.worker().items(helper.getLevel().registryAccess())
                    .get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 1, "the actual shears lose one durability");
            helper.assertTrue(((Sheep) animal.context(helper.getLevel())).isSheared(), "the saved sheep loses its fleece");
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.RED_WOOL), wool, "repeated work cannot duplicate the same fleece");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "shearing never loads the pen");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twoHandlersCannotHarvestTheSameFleece(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            record(helper, fixture, sheep(helper, fixture, DyeColor.WHITE));
            supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
            work(helper, fixture, 8);
            int first = carried(helper, fixture, Items.WHITE_WOOL);
            Villager second = EntityType.VILLAGER.create(helper.getLevel());
            second.setPos(fixture.town().center().getBottomCenter());
            second.setVillagerData(second.getVillagerData().setProfession(VillagerProfession.FARMER));
            VillageAllegianceApi.assignKnown(helper.getLevel(), second, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
            fixture.town().assignDuty(second.getUUID(), fixture.area().id());
            SettlementWorkforceService.reconcile(helper.getLevel(), second);
            HiredWorkStateStore.state(second).putBoolean("AutoDepositOutputs", false);
            HiredJobInventory.getJobInventory(second).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
            var worker = SettlementWorkerState.capture(helper.getLevel(), second, SettlementWorkforceService.duty(second).orElseThrow(), 0L);
            fixture.town().recordWorker(worker);
            fixture.town().recordResident(SettlementResidentState.capture(second, fixture.town(), 0L));
            fixture.town().livestock().values().forEach(animal -> animal.access(worker.id(), worker.position(), 0));
            for (int step = 0; step < 8; step++) SettlementBackgroundAnimalHandling.tick(helper.getLevel(), fixture.town(), worker, 200, 1.0D);
            helper.assertTrue(first > 0, "the first handler harvested the fleece");
            helper.assertValueEqual(worker.items(helper.getLevel().registryAccess()).stream()
                    .filter(stack -> stack.is(Items.WHITE_WOOL)).mapToInt(ItemStack::getCount).sum(), 0, "the second handler sees the shared sheared state");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullWoolOutputPreservesAnimalAndToolUntilSpaceReturns(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var animal = record(helper, fixture, sheep(helper, fixture, DyeColor.BLUE));
            supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
            fillOutput(helper, fixture);
            work(helper, fixture, 8);
            helper.assertFalse(((Sheep) animal.context(helper.getLevel())).isSheared(), "full storage cannot remove a fleece");
            helper.assertValueEqual(fixture.worker().items(helper.getLevel().registryAccess())
                    .get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 0, "failed output does not damage shears");
            supply(helper, fixture, fixture.worker().outputSlots().getFirst(), ItemStack.EMPTY);
            work(helper, fixture, 8);
            helper.assertTrue(carried(helper, fixture, Items.BLUE_WOOL) > 0, "freeing output space resumes the same harvest");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shearsComeFromPermittedFiniteDepotStock(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            record(helper, fixture, sheep(helper, fixture, DyeColor.WHITE));
            fixture.depot().setItem(0, new ItemStack(Items.SHEARS));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            work(helper, fixture, 8);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.SHEARS)), 1, "disabled depot supply access preserves its tool");
            helper.assertValueEqual(carried(helper, fixture, Items.WHITE_WOOL), 0, "a worker without shears cannot harvest wool");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            work(helper, fixture, 16);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.SHEARS)), 0, "equipping removes one actual depot tool");
            helper.assertValueEqual(carried(helper, fixture, Items.SHEARS), 1, "the fetched tool belongs to the worker inventory");
            helper.assertTrue(carried(helper, fixture, Items.WHITE_WOOL) > 0, "the equipped worker resumes shearing");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void backgroundMilkConservesBucketsAndSurvivesWorkerReturn(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Animal cow = EntityType.COW.create(helper.getLevel());
            cow.setPos(fixture.town().center().east().getBottomCenter());
            record(helper, fixture, cow);
            supply(helper, fixture, fixture.worker().supplySlots().getFirst(), new ItemStack(Items.BUCKET, 3));
            work(helper, fixture, 16);
            helper.assertValueEqual(carried(helper, fixture, Items.BUCKET), 0, "three milkings consume three empty buckets");
            helper.assertValueEqual(carried(helper, fixture, Items.MILK_BUCKET), 3, "each consumed bucket becomes one milk bucket");
            SettlementBackgroundService.onActive(fixture.villager());
            SettlementBackgroundService.onActive(fixture.villager());
            helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).collectOutputItems().stream()
                    .filter(entry -> entry.stack().is(Items.MILK_BUCKET)).mapToInt(entry -> entry.stack().getCount()).sum(), 3,
                    "returning the worker restores the output once");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "milking never loads the pasture");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void goatsUseTargetFiltersAndFetchActualBuckets(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Animal goat = EntityType.GOAT.create(helper.getLevel());
            goat.setPos(fixture.town().center().east().getBottomCenter());
            record(helper, fixture, goat);
            fixture.depot().setItem(0, new ItemStack(Items.BUCKET, 2));
            HiredAnimalBreedingTargets.toggleTarget(fixture.worker().settings(), "minecraft:cow");
            work(helper, fixture, 10);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BUCKET)), 2, "excluded species cannot consume buckets");
            HiredAnimalBreedingTargets.toggleTarget(fixture.worker().settings(), "all");
            work(helper, fixture, 20);
            helper.assertValueEqual(carried(helper, fixture, Items.MILK_BUCKET), 2, "selected goats produce milk through the existing handling rule");
            helper.assertTrue(fixture.depot().isEmpty(), "the two buckets were withdrawn rather than copied");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullMilkOutputDoesNotConsumeItsBucket(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Animal cow = EntityType.COW.create(helper.getLevel());
            cow.setPos(fixture.town().center().east().getBottomCenter());
            record(helper, fixture, cow);
            supply(helper, fixture, fixture.worker().supplySlots().getFirst(), new ItemStack(Items.BUCKET, 2));
            fillOutput(helper, fixture);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.BUCKET), 2, "failed milk output preserves its input buckets");
            helper.assertValueEqual(carried(helper, fixture, Items.MILK_BUCKET), 0, "full inventory cannot hide or spill milk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void disabledShearingAndBabyAnimalsProduceNothing(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.WHITE);
            var animal = record(helper, fixture, sheep);
            supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
            fixture.worker().settings().putBoolean(HiredAnimalHandlingOptions.SHEAR_SHEEP_TAG, false);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.WHITE_WOOL), 0, "the sheep-shearing switch also controls background work");
            fixture.worker().settings().putBoolean(HiredAnimalHandlingOptions.SHEAR_SHEEP_TAG, true);
            sheep.setAge(-600);
            animal.observe(sheep, 0L);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.WHITE_WOOL), 0, "baby sheep are not an adult fleece resource");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void livestockAgeAndLoveTimersPersistWithBoundedElapsedTime(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.WHITE);
            sheep.setNoAi(false);
            sheep.setAge(-1000);
            sheep.setInLoveTime(600);
            var animal = record(helper, fixture, sheep);
            helper.assertValueEqual(animal.advance(200L), 200, "livestock advances one observed server interval");
            var restored = SettlementLivestock.load(animal.save());
            helper.assertValueEqual(restored.advance(200L), 0, "save reload cannot replay the interval");
            helper.assertValueEqual(restored.advance(50_000L), 200, "a long pause does not create server-offline catchup");
            helper.assertTrue(restored.restore(sheep), "return applies the pending timers");
            helper.assertValueEqual(sheep.getAge(), -600, "the young animal ages by the two simulated intervals");
            helper.assertValueEqual(sheep.getInLoveTime(), 0, "young animals reset love under the native age rule");
            Sheep adult = sheep(helper, fixture, DyeColor.WHITE);
            adult.setInLoveTime(600);
            var adultState = SettlementLivestock.capture(adult, fixture.area().id(), 0L);
            adultState.advance(200L);
            adultState.restore(adult);
            helper.assertValueEqual(adult.getInLoveTime(), 400, "adult love timers expire at the native rate even with AI disabled");
            sheep.setAge(90);
            helper.assertFalse(restored.restore(sheep), "an acknowledged version cannot rewind later native progress");
            helper.assertValueEqual(sheep.getAge(), 90, "new native state wins after handoff");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void returningSheepKeepsItsCurrentIdentityAppearanceAndHealth(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.RED);
            var animal = record(helper, fixture, sheep);
            animal.shear();
            sheep.setCustomName(Component.literal("Dolly"));
            sheep.setColor(DyeColor.BLACK);
            sheep.setHealth(4.0F);
            sheep.setPos(fixture.town().center().east(2).getBottomCenter());
            var position = sheep.position();
            helper.assertTrue(animal.restore(sheep), "the saved fleece change applies");
            helper.assertTrue(sheep.isSheared(), "the committed shearing reaches the entity");
            helper.assertValueEqual(sheep.getCustomName().getString(), "Dolly", "reconciliation preserves a current name");
            helper.assertValueEqual(sheep.getColor(), DyeColor.BLACK, "reconciliation preserves current dye");
            helper.assertValueEqual(sheep.getHealth(), 4.0F, "reconciliation cannot heal the animal from an old snapshot");
            helper.assertValueEqual(sheep.position(), position, "reconciliation does not teleport livestock");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void livestockRequiresObservedWorkerAccess(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.WHITE);
            sheep.setPos(fixture.town().center().east(8).getBottomCenter());
            var animal = record(helper, fixture, sheep);
            animal.access(fixture.worker().id(), fixture.worker().position(), -1);
            supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
            helper.assertValueEqual(SettlementLivestockService.observedSteps(helper.getLevel(), fixture.town(), fixture.area(),
                    fixture.worker(), sheep), SettlementLivestockService.UNOBSERVED, "unobserved terrain cannot prove a distant route");
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.WHITE_WOOL), 0, "missing access cannot yield a remote fleece");
            helper.assertValueEqual(fixture.worker().status(), "animal_unreachable", "the worker reports the missing route");
            helper.assertFalse(helper.getLevel().hasChunkAt(sheep.blockPosition()), "route validation does not force the pen to load");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeAnimalDepartureRemovesItsBackgroundResource(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.WHITE);
            helper.getLevel().addFreshEntity(sheep);
            SettlementLivestockService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            helper.assertTrue(fixture.town().livestock().containsKey(sheep.getUUID()), "a real pen survey records the loaded animal");
            sheep.discard();
            helper.assertFalse(fixture.town().livestock().containsKey(sheep.getUUID()), "permanent entity removal retires the resource");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void replacementSpeciesCannotReuseARecordedAnimal(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.WHITE);
            var state = record(helper, fixture, sheep);
            state.shear();
            Animal cow = EntityType.COW.create(helper.getLevel());
            cow.setUUID(sheep.getUUID());
            cow.setPos(sheep.position());
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "the replacement entity enters the loaded pen");
            helper.assertFalse(state.restore(cow), "a sheep journal cannot overwrite another species");
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertFalse(fixture.town().livestock().containsKey(cow.getUUID()), "the old species record is retired");
            SettlementLivestockService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            helper.assertValueEqual(fixture.town().livestock().get(cow.getUUID()).type(),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COW),
                    "a fresh survey records the actual species");
            cow.discard();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stopTickingAppliesAnimalChangesBeforeSnapshotting(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.WHITE);
            helper.getLevel().addFreshEntity(sheep);
            SettlementLivestockService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            var animal = fixture.town().livestock().get(sheep.getUUID());
            CompoundTag oldEntity = sheep.saveWithoutId(new CompoundTag());
            animal.shear();
            SettlementSimulationHandoff.stopTicking(sheep);
            helper.assertTrue(sheep.isSheared(), "native entity shutdown receives committed shearing first");
            helper.assertTrue(sheep.saveWithoutId(new CompoundTag()).getBoolean("Sheared"), "the entity save contains the reconciled fleece state");
            Sheep older = EntityType.SHEEP.create(helper.getLevel());
            older.load(oldEntity);
            helper.assertTrue(animal.restore(older), "an older entity save can still apply the retained version");
            helper.assertTrue(older.isSheared(), "older entity data cannot restore a consumed fleece");
            sheep.discard();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void normalBackgroundDispatchHandlesLivestock(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            record(helper, fixture, sheep(helper, fixture, DyeColor.WHITE));
            supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
            long day = helper.getLevel().getDayTime();
            try {
                helper.getLevel().setDayTime(6_000L);
                for (int step = 1; step <= 8; step++) SettlementBackgroundService.update(helper.getLevel(), fixture.town(), step * 200L);
                helper.assertTrue(carried(helper, fixture, Items.WHITE_WOOL) > 0, "the ordinary background dispatcher runs animal handlers");
            } finally {
                helper.getLevel().setDayTime(day);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void departingWorkerRefreshesAccessFromItsLatestPosition(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.WHITE);
            helper.getLevel().addFreshEntity(sheep);
            SettlementLivestockService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            var animal = fixture.town().livestock().get(sheep.getUUID());
            fixture.villager().setPos(fixture.town().center().east(3).getBottomCenter());
            SettlementBackgroundService.onUnloaded(fixture.villager());
            helper.assertValueEqual(fixture.worker().position(), fixture.villager().blockPosition(), "the worker handoff captures current position");
            helper.assertValueEqual(animal.steps(fixture.worker()), 0, "the handoff refreshes nearby livestock access before leaving");
            sheep.discard();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void livestockAndWorkerTransactionsSurviveTownReload(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            Sheep sheep = sheep(helper, fixture, DyeColor.GREEN);
            record(helper, fixture, sheep);
            supply(helper, fixture, HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
            work(helper, fixture, 8);
            var level = helper.getLevel();
            var saved = fixture.town().save(level.registryAccess());
            var restored = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            var worker = restored.workers().get(fixture.worker().id());
            int wool = worker.items(level.registryAccess()).stream().filter(stack -> stack.is(Items.GREEN_WOOL))
                    .mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(wool > 0, "the pending fleece output is saved with the worker");
            helper.assertTrue(((Sheep) restored.livestock().get(sheep.getUUID()).context(level)).isSheared(),
                    "the same save retains the consumed fleece");
            for (int step = 0; step < 8; step++) SettlementBackgroundAnimalHandling.tick(level, restored, worker, 200, 1.0D);
            helper.assertValueEqual(worker.items(level.registryAccess()).stream().filter(stack -> stack.is(Items.GREEN_WOOL))
                    .mapToInt(ItemStack::getCount).sum(), wool, "town reload cannot repeat the harvest");
            saved.remove("Livestock");
            helper.assertTrue(SettlementRecord.load(saved, level.registryAccess(), () -> {}).livestock().isEmpty(),
                    "older settlement saves load without livestock observations");
            var invalid = fixture.town().livestock().get(sheep.getUUID()).save();
            invalid.putUUID("Id", UUID.randomUUID());
            boolean rejected = false;
            try {
                SettlementLivestock.load(invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "a livestock identity must match its saved entity");
        });
    }

    private static Sheep sheep(GameTestHelper helper, Fixture fixture, DyeColor color) {
        Sheep sheep = EntityType.SHEEP.create(helper.getLevel());
        sheep.setNoAi(true);
        sheep.setColor(color);
        sheep.setPos(fixture.town().center().east().getBottomCenter());
        return sheep;
    }

    private static SettlementLivestock record(GameTestHelper helper, Fixture fixture, Animal animal) {
        var state = SettlementLivestock.capture(animal, fixture.area().id(), 0L);
        state.access(fixture.worker().id(), fixture.worker().position(), 0);
        fixture.town().recordLivestock(state);
        return state;
    }

    private static void work(GameTestHelper helper, Fixture fixture, int intervals) {
        for (int index = 0; index < intervals; index++) {
            SettlementBackgroundAnimalHandling.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
        }
    }

    private static int carried(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream()
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void supply(GameTestHelper helper, Fixture fixture, int slot, ItemStack item) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        items.set(slot, item);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static void fillOutput(GameTestHelper helper, Fixture fixture) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            if (items.get(slot).isEmpty()) items.set(slot, new ItemStack(Items.STONE, 64));
        }
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static void fixture(GameTestHelper helper, boolean loaded, Consumer<Fixture> check) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos center = loaded ? origin : origin.offset(8_000_000, 0, 8_000_000).atY(80);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Pen",
                center.offset(-2, -1, -2), center.offset(10, 4, 10), 5, true);
        town.putArea(town.leader(), area);
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage",
                center.east(15), center.offset(18, 3, 3), 5, true);
        town.putArea(town.leader(), storage);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home",
                center.west(5), center.west(3), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
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
        check.accept(new Fixture(town, area, depot, villager, worker));
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot depot,
                           Villager villager, SettlementWorkerState worker) {
    }
}
