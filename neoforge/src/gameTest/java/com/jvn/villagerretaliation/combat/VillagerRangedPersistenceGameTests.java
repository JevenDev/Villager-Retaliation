package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerRangedPersistenceGameTests {
    private VillagerRangedPersistenceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void aPartlyChargedCrossbowRetainsItsUseAndReservedArrow(GameTestHelper helper) {
        var fixture = fixture(helper, Items.CROSSBOW);
        charge(helper, fixture);
        used(fixture.villager(), 9);
        var restored = copy(fixture.villager());
        helper.assertTrue(restored.isUsingItem() && restored.getUseItem().is(Items.CROSSBOW), "the saved crossbow use resumes");
        helper.assertValueEqual(restored.getTicksUsingItem(), 9, "the partial draw survives the real entity serializer");
        helper.assertValueEqual(restored.getUsedItemHand(), InteractionHand.MAIN_HAND, "the saved hand survives");
        helper.assertTrue(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(restored), "the consumed arrow remains reserved");
        helper.assertValueEqual(ammo(restored), 1, "restoring a charge cannot consume another arrow");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void aBackgroundCopyCannotCancelTheNativeBodysCharge(GameTestHelper helper) {
        var fixture = fixture(helper, Items.CROSSBOW);
        charge(helper, fixture);
        var restored = copy(fixture.villager());
        VillagerRangedCombatHelper.clearState(restored);
        VillagerRangedCombatHelper.clearState(restored);
        helper.assertValueEqual(ammo(restored), 2, "canceling the restored charge returns its arrow once");
        helper.assertFalse(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(restored), "the canceled context no longer owns a reservation");
        helper.assertTrue(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(fixture.villager()), "a same-UUID copy cannot mutate the native body's separate state");
        helper.assertTrue(fixture.villager().isUsingItem(), "the original body keeps drawing");
        helper.assertValueEqual(ammo(fixture.villager()), 1, "the native body's inventory does not receive the copy's refund");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void aSavedChargeFiresItsReservedArrowWithoutASecondPayment(GameTestHelper helper) {
        var fixture = fixture(helper, Items.CROSSBOW);
        charge(helper, fixture);
        used(fixture.villager(), CrossbowItem.getChargeDuration(fixture.villager().getUseItem(), fixture.villager()));
        var restored = copy(fixture.villager());
        attack(restored, fixture.target());
        helper.assertTrue(CrossbowItem.isCharged(restored.getMainHandItem()), "the restored draw finishes loading the authoritative crossbow");
        helper.assertValueEqual(ammo(restored), 1, "finishing the saved charge reuses its paid arrow");
        var loaded = copy(restored);
        for (int tick = 0; tick < 100 && arrows(helper, loaded).isEmpty(); tick++) attack(loaded, fixture.target());
        var arrows = arrows(helper, loaded);
        try {
            helper.assertValueEqual(arrows.size(), 1, "the resumed charge emits one real native projectile");
            helper.assertValueEqual(ammo(loaded), 1, "firing cannot charge the reserved arrow twice");
            helper.assertValueEqual(loaded.getMainHandItem().getDamageValue(), 1, "the real shot spends weapon durability");
            helper.assertFalse(CrossbowItem.isCharged(loaded.getMainHandItem()), "the fired charge is cleared");
            helper.assertFalse(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(loaded), "the shot relinquishes its reservation");
        } finally {
            arrows.forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void restoredBowUseDoesNotRepeatItsStartEvent(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW);
        var starts = new AtomicInteger();
        Consumer<LivingEntityUseItemEvent.Start> listener = event -> {
            if (event.getEntity().getUUID().equals(fixture.villager().getUUID())) starts.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            attack(fixture.villager(), fixture.target());
            helper.assertTrue(fixture.villager().isUsingItem(), "the native bow begins drawing");
            used(fixture.villager(), 20);
            var restored = copy(fixture.villager());
            helper.assertValueEqual(starts.get(), 1, "loading use state does not replay the native start hook");
            helper.assertValueEqual(restored.getTicksUsingItem(), 20, "the ready bow retains its completed draw");
            attack(restored, fixture.target());
            var arrows = arrows(helper, restored);
            try {
                helper.assertValueEqual(arrows.size(), 1, "the restored bow fires through the native launcher");
                helper.assertValueEqual(ammo(restored), 1, "the shot consumes one real arrow");
                helper.assertValueEqual(restored.getMainHandItem().getDamageValue(), 1, "the shot applies native bow durability");
            } finally {
                arrows.forEach(Entity::discard);
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void offhandUseRetainsADurationChangedByTheNativeStartHook(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW);
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, ItemStack.EMPTY);
        inventory.setItem(HiredJobInventory.OFFHAND_SLOT, new ItemStack(Items.BOW));
        fixture.villager().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOW));
        Consumer<LivingEntityUseItemEvent.Start> listener = event -> {
            if (event.getEntity() == fixture.villager()) event.setDuration(event.getDuration() + 37);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            fixture.villager().startUsingItem(InteractionHand.OFF_HAND);
            int remaining = fixture.villager().getUseItemRemainingTicks();
            var restored = copy(fixture.villager());
            helper.assertTrue(restored.isUsingItem(), "the offhand use is restored");
            helper.assertValueEqual(restored.getUsedItemHand(), InteractionHand.OFF_HAND, "the original offhand is retained");
            helper.assertValueEqual(restored.getUseItemRemainingTicks(), remaining, "a valid hook-adjusted duration is not replaced with the default duration");
            helper.assertTrue(remaining > restored.getUseItem().getUseDuration(restored), "the fixture exercises an extended native duration");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void explicitCancellationCannotReappearAfterSaving(GameTestHelper helper) {
        var fixture = fixture(helper, Items.CROSSBOW);
        charge(helper, fixture);
        var restored = copy(fixture.villager());
        VillagerRangedCombatHelper.cancelForWeaponSwitch(restored);
        var canceled = copy(restored);
        helper.assertFalse(canceled.isUsingItem(), "a canceled charge cannot resume on reload");
        helper.assertFalse(VillagerRangedCombatHelper.hasState(canceled), "cancellation removes the saved combat cycle");
        helper.assertFalse(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(canceled), "cancellation removes the saved reservation");
        helper.assertValueEqual(ammo(canceled), 2, "the refunded arrow survives with the original inventory total");
        VillagerRangedCombatHelper.clearState(canceled);
        helper.assertValueEqual(ammo(canceled), 2, "clearing a reloaded cancellation cannot repeat the refund");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void aDifferentActiveItemCannotRefundTheSameReservationTwice(GameTestHelper helper) {
        var fixture = fixture(helper, Items.CROSSBOW);
        charge(helper, fixture);
        var owner = copy(fixture.villager());
        owner.stopUsingItem();
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));
        owner.startUsingItem(InteractionHand.OFF_HAND);
        attack(owner, fixture.target());
        helper.assertFalse(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(owner), "the invalid charge releases its reservation");
        helper.assertValueEqual(ammo(owner), 2, "the stop-attack and clear-state paths cannot refund the same saved arrow twice");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void savedRecoveryRetainsTheNativeNextAttackTiming(GameTestHelper helper) {
        for (var weapon : new Item[]{Items.BOW, Items.CROSSBOW}) {
            var fixture = fixture(helper, weapon);
            if (weapon == Items.CROSSBOW) charge(helper, fixture);
            else attack(fixture.villager(), fixture.target());
            used(fixture.villager(), weapon == Items.CROSSBOW
                    ? CrossbowItem.getChargeDuration(fixture.villager().getUseItem(), fixture.villager()) : 20);
            attack(fixture.villager(), fixture.target());
            arrows(helper, fixture.villager()).forEach(Entity::discard);
            var restored = copy(fixture.villager());
            boolean ready = false;
            try {
                for (int tick = 0; tick < 100; tick++) {
                    attack(fixture.villager(), fixture.target());
                    attack(restored, fixture.target());
                    boolean nativeReady = weapon == Items.CROSSBOW ? !arrows(helper, fixture.villager()).isEmpty() : fixture.villager().isUsingItem();
                    boolean restoredReady = weapon == Items.CROSSBOW ? !arrows(helper, restored).isEmpty() : restored.isUsingItem();
                    helper.assertValueEqual(restoredReady, nativeReady, "saved and continuously native recovery complete on the same combat frame");
                    if (nativeReady) {
                        helper.assertTrue(tick > 0, "the fixture exercises a real recovery interval");
                        ready = true;
                        break;
                    }
                }
                helper.assertTrue(ready, "both bodies reach their next native action within the ordinary recovery interval");
                helper.assertValueEqual(ammo(restored), 1, "restored recovery does not pay for the same shot again");
            } finally {
                arrows(helper, fixture.villager()).forEach(Entity::discard);
                arrows(helper, restored).forEach(Entity::discard);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void anOlderEntityWithoutRangedStateStartsWithoutACharge(GameTestHelper helper) {
        var fixture = fixture(helper, Items.CROSSBOW);
        var saved = fixture.villager().saveWithoutId(new CompoundTag());
        saved.getCompound("NeoForgeData").remove("VillagerRetaliationRangedCombat");
        var restored = EntityType.VILLAGER.create(helper.getLevel());
        restored.load(saved);
        helper.assertFalse(restored.isUsingItem() || VillagerRangedCombatHelper.hasState(restored), "older entities do not acquire an invented charge or delay");
        helper.assertValueEqual(ammo(restored), 2, "an older inventory remains intact");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_persistence")
    public static void unloadingCannotRefundAnArrowAlreadyCapturedInTheSavedCharge(GameTestHelper helper) {
        var fixture = fixture(helper, Items.CROSSBOW);
        helper.assertTrue(helper.getLevel().addFreshEntity(fixture.villager()), "the native owner enters actual level storage");
        charge(helper, fixture);
        var saved = fixture.villager().saveWithoutId(new CompoundTag());
        fixture.villager().setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(helper.getLevel().getEntity(fixture.villager().getUUID()) == null, "the actual entity-leave hook runs");
        helper.assertValueEqual(ammo(fixture.villager()), 1, "unload cleanup does not create a refund outside the saved body");
        helper.assertTrue(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(fixture.villager()), "unloading retains ownership of the one reserved arrow");
        var restored = EntityType.VILLAGER.create(helper.getLevel());
        restored.load(saved);
        helper.assertTrue(restored.isUsingItem() && VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(restored), "the pre-unload save resumes its native charge");
        helper.assertValueEqual(ammo(restored), 1, "the restored inventory and reservation still total the original two arrows");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, Item weapon) {
        var level = helper.getLevel();
        var owner = EntityType.VILLAGER.create(level);
        owner.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        owner.setNoAi(true);
        owner.setNoGravity(true);
        owner.setVillagerData(owner.getVillagerData().setProfession(VillagerProfession.FLETCHER));
        var inventory = HiredJobInventory.getJobInventory(owner);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(weapon));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.ARROW, 2));
        inventory.syncMainHandEquipment();
        var target = EntityType.COW.create(level);
        target.setPos(helper.absolutePos(new BlockPos(5, 2, 2)).getBottomCenter());
        target.setNoAi(true);
        target.setNoGravity(true);
        return new Fixture(owner, target);
    }

    private static void charge(GameTestHelper helper, Fixture fixture) {
        for (int tick = 0; tick < 5; tick++) attack(fixture.villager(), fixture.target());
        helper.assertTrue(fixture.villager().isUsingItem(), "the native helper begins charging");
        helper.assertTrue(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(fixture.villager()), "one native arrow is reserved");
        helper.assertValueEqual(ammo(fixture.villager()), 1, "charging removes exactly one arrow from supplies");
    }

    private static void attack(Villager owner, Cow target) {
        VillagerRangedCombatHelper.tryAttack(owner, target, (net.minecraft.server.level.ServerLevel) owner.level(), owner.distanceToSqr(target));
    }

    private static void used(Villager owner, int ticks) {
        ((LivingEntityCombatAccessor) owner).villagerretaliation$setUseItemRemaining(owner.getUseItem().getUseDuration(owner) - ticks);
    }

    private static Villager copy(Villager original) {
        var saved = original.saveWithoutId(new CompoundTag());
        var result = EntityType.VILLAGER.create(original.level());
        result.load(saved.copy());
        return result;
    }

    private static int ammo(Villager owner) {
        var inventory = HiredJobInventory.getJobInventory(owner);
        int result = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(Items.ARROW)) result += stack.getCount();
        }
        return result;
    }

    private static java.util.List<AbstractArrow> arrows(GameTestHelper helper, Villager owner) {
        return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, owner.getBoundingBox().inflate(16.0D),
                arrow -> arrow.getOwner() == owner);
    }

    private record Fixture(Villager villager, Cow target) {
    }
}
