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
            for (int step = 0; step < 8; step++) {
                SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
                SettlementBackgroundAnimalHandling.tick(helper.getLevel(), fixture.town(), worker, 200, 1.0D);
            }
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
            for (int step = 0; step < 8; step++) {
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundAnimalHandling.tick(level, restored, worker, 200, 1.0D);
            }
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void backgroundChickenEggsUseTheNativeTimerAndActualInventory(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var chicken = EntityType.CHICKEN.create(helper.getLevel());
            chicken.setPos(fixture.town().center().east().getBottomCenter());
            chicken.setNoAi(true);
            chicken.eggTime = 1;
            var state = record(helper, fixture, chicken);
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.town().itemDrops().size(), 1, "one elapsed native timer produces one actual pending egg");
            int timer = ((net.minecraft.world.entity.animal.Chicken) state.context(helper.getLevel())).eggTime;
            helper.assertTrue(timer >= 5_801 && timer <= 11_800, "the next native interval includes the remaining elapsed ticks");
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 1, "the handler collects that finite egg");
            helper.assertTrue(fixture.town().itemDrops().isEmpty(), "collecting consumes the pending item");
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 1, "repeating the same interval cannot produce or collect another egg");
            helper.assertTrue(state.restore(chicken), "returning the native chicken applies its background timer");
            helper.assertValueEqual(chicken.eggTime, timer, "the returned chicken retains the already consumed egg interval");
            helper.assertFalse(state.restore(chicken), "the timer receipt applies once");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "egg production and collection never load the pen");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void babiesAndJockeysDoNotLayUntilTheNativeRulesPermitIt(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var baby = EntityType.CHICKEN.create(helper.getLevel());
            baby.setPos(fixture.town().center().east().getBottomCenter());
            baby.setAge(-400);
            baby.eggTime = 1;
            var babyState = record(helper, fixture, baby);
            var jockey = EntityType.CHICKEN.create(helper.getLevel());
            jockey.setPos(fixture.town().center().south().getBottomCenter());
            jockey.setChickenJockey(true);
            jockey.eggTime = 1;
            var jockeyState = record(helper, fixture, jockey);
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.town().itemDrops().isEmpty(), "babies and jockeys cannot lay");
            helper.assertValueEqual(((net.minecraft.world.entity.animal.Chicken) babyState.context(helper.getLevel())).eggTime, 1,
                    "a baby's egg timer is paused");
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.town().itemDrops().size(), 1, "the former baby can lay on the tick it reaches adulthood");
            helper.assertValueEqual(((net.minecraft.world.entity.animal.Chicken) jockeyState.context(helper.getLevel())).eggTime, 1,
                    "a jockey's egg timer stays paused");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void fullOutputKeepsTheEggOnTheGroundUntilSpaceReturns(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var drop = eggDrop(helper, fixture, 1);
            fillOutput(helper, fixture);
            work(helper, fixture, 8);
            helper.assertTrue(fixture.town().itemDrops().containsKey(drop.id()), "full output does not delete a ground egg");
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 0, "full output cannot silently credit an egg");
            supply(helper, fixture, fixture.worker().outputSlots().getFirst(), ItemStack.EMPTY);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 1, "freed capacity collects the same egg");
            helper.assertTrue(fixture.town().itemDrops().isEmpty(), "successful pickup removes the ground item");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void partialEggPickupConservesTheRemainder(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var drop = eggDrop(helper, fixture, 5);
            fillOutput(helper, fixture);
            int slot = fixture.worker().outputSlots().getFirst();
            supply(helper, fixture, slot, new ItemStack(Items.EGG, 15));
            work(helper, fixture, 1);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 16, "only one egg fits the remaining output capacity");
            helper.assertValueEqual(drop.item(helper.getLevel().registryAccess()).getCount(), 4, "the other four remain on the ground");
            supply(helper, fixture, slot, ItemStack.EMPTY);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 4, "later pickup transfers only the remaining four");
            helper.assertTrue(fixture.town().itemDrops().isEmpty(), "the finite stack is now fully consumed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void anUnobservedEggRouteCannotTransferItsItem(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), fixture.town().center().getX() + 8.5D,
                    fixture.town().center().getY(), fixture.town().center().getZ() + 0.5D, new ItemStack(Items.EGG));
            var drop = SettlementItemDrop.create(item, fixture.area().id(), 0L);
            fixture.town().recordItemDrop(drop);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 0, "an unobserved route cannot collect a ground item");
            helper.assertValueEqual(fixture.worker().status(), "animal_drop_unreachable", "the missing route has a specific blocker");
            drop.access(fixture.worker(), 8);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 1, "an observed route allows the finite pickup");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void pendingEggsAgeWithoutServerOfflineCatchup(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var drop = eggDrop(helper, fixture, 1);
            SettlementItemDropService.update(helper.getLevel(), fixture.town(), 48_000L);
            helper.assertValueEqual(drop.context(helper.getLevel()).getAge(), 200, "a long clock gap simulates only one bounded running interval");
            SettlementItemDropService.update(helper.getLevel(), fixture.town(), 48_000L);
            helper.assertValueEqual(drop.context(helper.getLevel()).getAge(), 200, "the same interval cannot age the item twice");
            for (int step = 1; step <= 29; step++) SettlementItemDropService.update(helper.getLevel(), fixture.town(), 48_000L + step * 200L);
            helper.assertTrue(fixture.town().itemDrops().isEmpty(), "an uncollected egg expires at its actual native lifespan");
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 0, "expired eggs cannot be collected later");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void backgroundEggExpiryHonorsTheNativeExtensionEvent(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), fixture.town().center().getX() + 0.5D,
                    fixture.town().center().getY(), fixture.town().center().getZ() + 0.5D, new ItemStack(Items.EGG));
            item.lifespan = 1;
            var drop = SettlementItemDrop.create(item, fixture.area().id(), 0L);
            fixture.town().recordItemDrop(drop);
            int[] calls = {0};
            Consumer<net.neoforged.neoforge.event.entity.item.ItemExpireEvent> extend = event -> {
                if (event.getEntity().getUUID().equals(drop.id()) && calls[0]++ == 0) event.addExtraLife(200);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(extend);
            try {
                SettlementItemDropService.update(helper.getLevel(), fixture.town(), 200L);
                helper.assertTrue(fixture.town().itemDrops().containsKey(drop.id()), "the native extension preserves the expired egg");
                helper.assertValueEqual(drop.context(helper.getLevel()).lifespan, 201, "the actual native extra lifetime is retained");
                SettlementItemDropService.update(helper.getLevel(), fixture.town(), 400L);
                helper.assertTrue(fixture.town().itemDrops().isEmpty(), "the egg expires when its extended life ends");
                helper.assertValueEqual(calls[0], 2, "each lifespan boundary fires the native expiry hook once");
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(extend);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void pendingEggMaterializationAndOldSaveReplayAreIdempotent(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            var drop = eggDrop(helper, fixture, 1);
            var saved = drop.save();
            helper.assertTrue(SettlementItemDropService.materialize(helper.getLevel(), fixture.town(), drop), "the pending egg enters its loaded native chunk");
            var item = helper.getLevel().getEntity(drop.id());
            helper.assertTrue(item instanceof net.minecraft.world.entity.item.ItemEntity, "materialization creates the actual item entity");
            helper.assertTrue(SettlementItemDropService.materialize(helper.getLevel(), fixture.town(), drop), "a repeated delivery is already acknowledged");
            helper.assertTrue(helper.getLevel().getEntity(drop.id()) == item, "repeated delivery keeps the same native entity");
            item.discard();
            var replay = SettlementItemDrop.load(saved, helper.getLevel().registryAccess());
            fixture.town().recordItemDrop(replay);
            work(helper, fixture, 8);
            helper.assertTrue(fixture.town().itemDrops().isEmpty(), "the chunk receipt rejects an old pending-item snapshot");
            helper.assertTrue(helper.getLevel().getEntity(drop.id()) == null, "an already consumed native egg cannot be recreated");
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 0, "an old saved drop cannot also credit the background inventory");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void aRejectedNativeEggJoinKeepsTheDropForRetry(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            var drop = eggDrop(helper, fixture, 1);
            Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> reject = event -> {
                if (event.getEntity().getUUID().equals(drop.id())) event.setCanceled(true);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(reject);
            try {
                helper.assertFalse(SettlementItemDropService.materialize(helper.getLevel(), fixture.town(), drop), "a rejected native join is not delivery");
                helper.assertTrue(fixture.town().itemDrops().containsKey(drop.id()), "the rejected egg is still pending");
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(reject);
            }
            helper.assertTrue(SettlementItemDropService.materialize(helper.getLevel(), fixture.town(), drop), "the same egg can be retried");
            var item = helper.getLevel().getEntity(drop.id());
            helper.assertTrue(item != null && fixture.town().itemDrops().isEmpty(), "successful retry acknowledges exactly one item");
            item.discard();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void aBackgroundHandlerCanCollectAnEggInALoadedPartOfThePen(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), fixture.town().center().getX() + 1.5D,
                    fixture.town().center().getY(), fixture.town().center().getZ() + 0.5D, new ItemStack(Items.EGG, 2));
            helper.assertTrue(helper.getLevel().addFreshEntity(item), "the actual native ground eggs are present");
            try {
                work(helper, fixture, 8);
                helper.assertValueEqual(carried(helper, fixture, Items.EGG), 2, "background work can collect the loaded native stack");
                helper.assertTrue(item.isRemoved(), "the real world stack is consumed once");
                work(helper, fixture, 8);
                helper.assertValueEqual(carried(helper, fixture, Items.EGG), 2, "later work cannot repeat the same collection");
            } finally {
                item.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void activeNativeChickensDoNotAlsoAdvanceTheirEggTimerInTheBackground(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            var chicken = EntityType.CHICKEN.create(helper.getLevel());
            chicken.setPos(fixture.town().center().east().getBottomCenter());
            chicken.setNoAi(true);
            chicken.eggTime = 100;
            record(helper, fixture, chicken);
            helper.assertTrue(helper.getLevel().addFreshEntity(chicken), "the native chicken is present in the active pen");
            try {
                SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
                helper.assertValueEqual(chicken.eggTime, 100, "the active native timer belongs to normal entity ticks");
                helper.assertTrue(fixture.town().itemDrops().isEmpty(), "background work cannot also lay an active chicken's egg");
            } finally {
                chicken.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void pendingEggsAndTheirConsumedTimersSurviveSettlementSaves(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var chicken = EntityType.CHICKEN.create(helper.getLevel());
            chicken.setPos(fixture.town().center().east().getBottomCenter());
            chicken.eggTime = 1;
            var state = record(helper, fixture, chicken);
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            var level = helper.getLevel();
            var saved = fixture.town().save(level.registryAccess());
            var restored = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            helper.assertValueEqual(restored.itemDrops().size(), 1, "the pending egg survives the same save as its source timer");
            helper.assertValueEqual(((net.minecraft.world.entity.animal.Chicken) restored.livestock().get(chicken.getUUID()).context(level)).eggTime,
                    ((net.minecraft.world.entity.animal.Chicken) state.context(level)).eggTime, "the consumed timer survives unchanged");
            var worker = restored.workers().get(fixture.worker().id());
            for (int step = 0; step < 8; step++) {
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundAnimalHandling.tick(level, restored, worker, 200, 1.0D);
            }
            helper.assertTrue(restored.itemDrops().isEmpty(), "the restored egg remains collectable");
            helper.assertValueEqual(worker.items(level.registryAccess()).stream().filter(stack -> stack.is(Items.EGG)).mapToInt(ItemStack::getCount).sum(),
                    1, "reloading credits only the actual pending egg");
            saved.remove("ItemDrops");
            helper.assertTrue(SettlementRecord.load(saved, level.registryAccess(), () -> {}).itemDrops().isEmpty(), "older saves require no pending-item data");
            var malformed = fixture.town().itemDrops().values().iterator().next().save();
            malformed.putUUID("Id", UUID.randomUUID());
            boolean rejected = false;
            try {
                SettlementItemDrop.load(malformed, level.registryAccess());
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "a drop identity must match its saved native entity");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void anAnimalInteractionIsNotProofOfAnEggPickupRoute(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var chicken = EntityType.CHICKEN.create(helper.getLevel());
            chicken.setPos(fixture.town().center().east(2).getBottomCenter());
            chicken.eggTime = 1;
            record(helper, fixture, chicken);
            SettlementLivestockService.update(helper.getLevel(), fixture.town(), 200L);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 0,
                    "an animal within feeding reach can still lay beyond the shorter item pickup reach");
            helper.assertValueEqual(fixture.worker().status(), "animal_drop_unreachable", "the missing item route blocks pickup");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "checking the missing route cannot load terrain");
            fixture.town().itemDrops().values().iterator().next().access(fixture.worker(), 2);
            work(helper, fixture, 8);
            helper.assertValueEqual(carried(helper, fixture, Items.EGG), 1, "an observed item route permits the same egg to be collected");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_eggs")
    public static void nativeExpiryHooksCanMakeAPendingEggUnlimited(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), fixture.town().center().getX() + 0.5D,
                    fixture.town().center().getY(), fixture.town().center().getZ() + 0.5D, new ItemStack(Items.EGG));
            item.lifespan = 1;
            var drop = SettlementItemDrop.create(item, fixture.area().id(), 0L);
            fixture.town().recordItemDrop(drop);
            int[] calls = {0};
            Consumer<net.neoforged.neoforge.event.entity.item.ItemExpireEvent> preserve = event -> {
                if (event.getEntity().getUUID().equals(drop.id())) {
                    calls[0]++;
                    event.getEntity().setUnlimitedLifetime();
                }
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(preserve);
            try {
                SettlementItemDropService.update(helper.getLevel(), fixture.town(), 200L);
                SettlementItemDropService.update(helper.getLevel(), fixture.town(), 400L);
                helper.assertTrue(fixture.town().itemDrops().containsKey(drop.id()), "native unlimited-lifetime hooks preserve the pending item");
                helper.assertValueEqual(drop.context(helper.getLevel()).getAge(), (int) Short.MIN_VALUE, "the native unlimited-age sentinel survives");
                helper.assertValueEqual(calls[0], 1, "an unlimited item does not keep expiring");
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(preserve);
            }
        });
    }

    private static SettlementItemDrop eggDrop(GameTestHelper helper, Fixture fixture, int count) {
        var position = fixture.town().center().east().getBottomCenter();
        var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), position.x, position.y, position.z, new ItemStack(Items.EGG, count));
        item.setDefaultPickUpDelay();
        var drop = SettlementItemDrop.create(item, fixture.area().id(), 0L);
        drop.access(fixture.worker(), 0);
        fixture.town().recordItemDrop(drop);
        return drop;
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
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
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
        SettlementTestPlots.claim(level, town, storage.min(), storage.max());
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
