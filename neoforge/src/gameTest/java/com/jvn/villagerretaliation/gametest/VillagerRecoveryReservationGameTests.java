package com.jvn.villagerretaliation.gametest;

import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerRecoveryReservationGameTests {
    private VillagerRecoveryReservationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "recovery_reservations")
    public static void reservedMealSurvivesSavingBeforeTheUnloadCallback(GameTestHelper helper) {
        Villager original = villager(helper);
        ItemStack bread = new ItemStack(Items.BREAD);
        bread.set(DataComponents.CUSTOM_NAME, Component.literal("Packed lunch"));
        VillagerInventoryAccess.addItem(original, bread);
        VillagerRecoveryService.onVillagerTickPost(original);
        for (int tick = 0; tick < 5; tick++) {
            VillagerRecoveryService.onVillagerTickPost(original);
        }
        CompoundTag saved = save(original);
        VillagerRecoveryService.onVillagerUnloaded(original);
        helper.assertValueEqual(original.getInventory().countItem(Items.BREAD), 0,
                "the unload callback cannot refund an item after the entity was saved");
        Villager returning = load(helper, saved);
        VillagerRecoveryService.onVillagerLoaded(returning);
        VillagerRecoveryService.onVillagerLoaded(returning);
        helper.assertValueEqual(returning.getInventory().countItem(Items.BREAD), 1,
                "the saved reservation returns exactly once");
        helper.assertTrue(VillagerInventoryAccess.captureFullInventory(returning).stream().anyMatch(stack ->
                        stack.is(Items.BREAD) && stack.getHoverName().getString().equals("Packed lunch")),
                "the reserved meal retains its components");
        helper.assertValueEqual(returning.getMainHandItem().getHoverName().getString(), "Field shovel",
                "the visual meal cannot replace the original tool");
        finishMeal(returning);
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(returning), 14,
                "the existing reserved bread supplies its nutrition");
        helper.assertValueEqual(VillagerRecoveryService.saturationLevel(returning), 6.0F,
                "bread adds its actual saturation without multiplying it again");
        helper.assertValueEqual(returning.getInventory().countItem(Items.BREAD), 0, "the meal is consumed once");
        VillagerRecoveryService.onVillagerLoaded(returning);
        helper.assertValueEqual(returning.getInventory().countItem(Items.BREAD), 0,
                "a completed meal leaves no refundable reservation");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "recovery_reservations")
    public static void cancelledMealCannotBeRefundedAgainAfterSaving(GameTestHelper helper) {
        Villager original = villager(helper);
        VillagerInventoryAccess.addItem(original, new ItemStack(Items.BREAD));
        VillagerRecoveryService.onVillagerTickPost(original);
        VillagerRecoveryService.cancelForcedRecovery(original);
        helper.assertValueEqual(original.getInventory().countItem(Items.BREAD), 1, "cancellation refunds the real item");
        Villager returning = load(helper, save(original));
        VillagerRecoveryService.onVillagerLoaded(returning);
        helper.assertValueEqual(returning.getInventory().countItem(Items.BREAD), 1,
                "loading a cancelled reservation does not add another item");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(returning), 9, "cancellation grants no nutrition");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "recovery_reservations")
    public static void completedStewKeepsOneBowlAcrossReloads(GameTestHelper helper) {
        Villager original = villager(helper);
        VillagerInventoryAccess.addItem(original, new ItemStack(Items.MUSHROOM_STEW));
        finishMeal(original);
        helper.assertValueEqual(original.getInventory().countItem(Items.BOWL), 1, "eating returns one bowl");
        Villager returning = load(helper, save(original));
        VillagerRecoveryService.onVillagerLoaded(returning);
        VillagerRecoveryService.onVillagerLoaded(returning);
        helper.assertValueEqual(returning.getInventory().countItem(Items.BOWL), 1, "loading cannot repeat the bowl remainder");
        helper.assertValueEqual(returning.getInventory().countItem(Items.MUSHROOM_STEW), 0,
                "a completed stew cannot be refunded");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(returning), 15, "stew nutrition survives saving");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "recovery_reservations")
    public static void deathReturnsReservedFoodBeforeInventoryDrops(GameTestHelper helper) {
        Villager original = villager(helper);
        VillagerInventoryAccess.addItem(original, new ItemStack(Items.BREAD));
        VillagerRecoveryService.onVillagerTickPost(original);
        VillagerRetaliationEvents.onLivingDeath(new LivingDeathEvent(original, original.damageSources().generic()));
        var drops = new ArrayList<ItemEntity>();
        VillagerInventoryAccess.dropAllInventoryAndEquipment(original,
                new LivingDropsEvent(original, original.damageSources().generic(), drops, false));
        helper.assertValueEqual(drops.stream().filter(drop -> drop.getItem().is(Items.BREAD))
                .mapToInt(drop -> drop.getItem().getCount()).sum(), 1, "the reserved meal joins death drops once");
        helper.assertValueEqual(drops.stream().filter(drop -> drop.getItem().is(Items.IRON_SHOVEL))
                .mapToInt(drop -> drop.getItem().getCount()).sum(), 1, "the original tool also drops once");
        VillagerRecoveryService.onVillagerUnloaded(original);
        VillagerRecoveryService.onVillagerLoaded(original);
        helper.assertValueEqual(original.getInventory().countItem(Items.BREAD), 0,
                "death cleanup leaves no pending refund");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "recovery_reservations")
    public static void reservedPotionRetainsItsContentsAfterLoading(GameTestHelper helper) {
        Villager original = villager(helper);
        original.setHealth(original.getMaxHealth() / 2.0F);
        ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        VillagerInventoryAccess.addItem(original, healing);
        helper.assertTrue(VillagerRecoveryService.onVillagerTickPost(original), "the injured resident starts drinking");
        CompoundTag saved = save(original);
        VillagerRecoveryService.onVillagerUnloaded(original);
        Villager returning = load(helper, saved);
        VillagerRecoveryService.onVillagerLoaded(returning);
        helper.assertTrue(VillagerInventoryAccess.captureFullInventory(returning).stream().anyMatch(stack ->
                        ItemStack.isSameItemSameComponents(stack, healing)),
                "the reserved potion keeps its exact contents");
        helper.assertValueEqual(returning.getInventory().countItem(Items.POTION), 1, "only one potion is refunded");
        helper.assertValueEqual(returning.getInventory().countItem(Items.GLASS_BOTTLE), 0,
                "interrupted drinking creates no empty bottle");
        helper.succeed();
    }

    private static Villager villager(GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        shovel.set(DataComponents.CUSTOM_NAME, Component.literal("Field shovel"));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, EquipmentSlot.MAINHAND, shovel);
        VillagerRecoveryService.restoreRecoveryState(villager,
                new VillagerRecoveryService.RecoverySnapshot(9, 0.0F, 0.0F, 0));
        return villager;
    }

    private static CompoundTag save(Villager villager) {
        CompoundTag saved = new CompoundTag();
        villager.saveWithoutId(saved);
        return saved;
    }

    private static Villager load(GameTestHelper helper, CompoundTag saved) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.load(saved);
        return villager;
    }

    private static void finishMeal(Villager villager) {
        for (int tick = 0; tick < 32; tick++) {
            VillagerRecoveryService.onVillagerTickPost(villager);
        }
    }
}
