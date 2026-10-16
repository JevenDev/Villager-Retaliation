package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.combat.VillagerRangedCombatHelper;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation_equipment")
@PrefixGameTestTemplate(false)
public final class EquipmentInventoryReliabilityGameTests {
    private EquipmentInventoryReliabilityGameTests() { }

    /** Keep focused runs exercising the established ownership and drop rules too. */
    @GameTestGenerator
    public static List<TestFunction> establishedInventoryScenarios() {
        return List.of(
                scenario("capacity", VillagerInventoryGameTests::regularInventoryAddsItemsChecksCapacityAndRemovesEmptyExtraPersistence),
                scenario("armor_swap", VillagerInventoryGameTests::personalArmorSwapReturnsOldPieceWithoutDuplicatingIt),
                scenario("broken_job_armor", VillagerInventoryGameTests::brokenJobArmorIsConsumedInsteadOfRestored),
                scenario("borrow_return", VillagerInventoryGameTests::regularInventoryBorrowedCombatWeaponReturnsToInventory),
                scenario("open_menu_loan", VillagerInventoryGameTests::openPersonalInventoryCannotDuplicateBorrowedWeapons),
                scenario("stale_close", VillagerInventoryGameTests::closingStalePersonalInventoryDoesNotResurrectItems),
                scenario("job_control", VillagerInventoryGameTests::personalInventoryRespectsJobControlledEquipment),
                scenario("hotbar_reload", VillagerInventoryGameTests::regularInventoryRestoresExpandedHotbarPersistence),
                scenario("job_reclaim", VillagerInventoryGameTests::jobInventoryReclaimsBorrowedMainHandWithoutDuplicating),
                scenario("automatic_exchange", VillagerInventoryGameTests::automaticEquipmentExchangePreservesBorrowedCrossbowOwnership),
                scenario("death_inventory", VillagerInventoryGameTests::deathDropsPersonalAndJobInventory),
                scenario("death_job_wear", VillagerInventoryGameTests::deathReconcilesDamagedJobArmorWithoutDuplicatingIt),
                scenario("death_temporary", VillagerInventoryGameTests::deathDropsTrackedMainHandInsteadOfTransientOverride),
                scenario("death_loan", VillagerInventoryGameTests::deathDropsBorrowedLoadoutExactlyOnce),
                scenario("partial_pickup", VillagerInventoryGameTests::partyDropPickupConservesPartialAndRejectedStacks));
    }

    private static TestFunction scenario(String name, Consumer<GameTestHelper> test) {
        return new TestFunction("equipment_regressions", "equipmentinventoryreliabilitygametests.regression_" + name,
                "villagerretaliation_equipment:empty", 100, 0L, true, test);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void meleeBreakConsumesManualPickedUpAndRoleOwnership(GameTestHelper helper) {
        for (int owner = 0; owner < 3; owner++) {
            Villager villager = villager(helper);
            ItemStack sword = decorated(helper, Items.IRON_SWORD.getDefaultInstance(), "Break test");
            sword.setDamageValue(sword.getMaxDamage() - 1);
            if (owner == 0) VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, EquipmentSlot.MAINHAND, sword);
            else if (owner == 1) VillagerRetaliationVillagerEquipment.setPickedUpMainHand(villager, sword);
            else VillagerRetaliationVillagerEquipment.ensureRoleMainHand(villager, "test_role", sword);
            VillagerEquipmentDurability.postMeleeHit(villager, villager, InteractionHand.MAIN_HAND);
            helper.assertTrue(villager.getMainHandItem().isEmpty(), "last durability point breaks the live sword");
            VillagerRetaliationVillagerEquipment.clearRuntimeState(villager);
            helper.assertFalse(VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager),
                    "maintenance cannot recreate consumed manual or picked-up equipment");
            helper.assertValueEqual(VillagerRetaliationVillagerEquipment.mainHandOwner(villager),
                    VillagerRetaliationVillagerEquipment.MainHandOwner.NONE, "broken ownership stays cleared after cache reload");
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void meleeWearAndBreakPersistToJobAuthority(GameTestHelper helper) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            Villager villager = villager(helper);
            int index = slot == EquipmentSlot.MAINHAND ? HiredJobInventory.MAINHAND_SLOT : HiredJobInventory.OFFHAND_SLOT;
            HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
            ItemStack sword = decorated(helper, new ItemStack(Items.IRON_SWORD), "Job sword");
            inventory.setItem(index, sword.copy());
            InteractionHand hand = slot == EquipmentSlot.MAINHAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            VillagerEquipmentDurability.postMeleeHit(villager, villager, hand);
            ItemStack worn = villager.getItemBySlot(slot).copy();
            helper.assertValueEqual(worn.getDamageValue(), 1, "melee applies native item durability");
            HiredJobInventory.clearRuntimeState(villager);
            same(helper, HiredJobInventory.getJobInventory(villager).getItem(index), worn,
                    "job authority persists current wear and all components immediately");
            villager.getItemBySlot(slot).setDamageValue(sword.getMaxDamage() - 1);
            VillagerEquipmentDurability.postMeleeHit(villager, villager, hand);
            HiredJobInventory.clearRuntimeState(villager);
            HiredJobInventory.maintainEquipmentSlots(villager);
            VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager);
            helper.assertTrue(HiredJobInventory.getJobInventory(villager).getItem(index).isEmpty()
                            && villager.getItemBySlot(slot).isEmpty(), "broken job gear stays consumed in both owners");
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void borrowedMeleeBreakRestoresDisplacedGearWithoutResurrection(GameTestHelper helper) {
        Villager villager = villager(helper);
        ItemStack displaced = decorated(helper, new ItemStack(Items.STICK), "Original hand");
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, EquipmentSlot.MAINHAND, displaced);
        ItemStack sword = decorated(helper, new ItemStack(Items.IRON_SWORD), "Borrowed sword");
        sword.setDamageValue(sword.getMaxDamage() - 1);
        VillagerInventoryContainer.addItem(villager, sword);
        helper.assertTrue(VillagerInventoryContainer.tryBorrowCombatWeapon(villager), "stored sword is borrowed");
        VillagerEquipmentDurability.postMeleeHit(villager, villager, InteractionHand.MAIN_HAND);
        helper.assertFalse(VillagerInventoryContainer.hasBorrowedCombatWeapon(villager), "broken loan is consumed immediately");
        same(helper, villager.getMainHandItem(), displaced, "displaced hand returns once after borrowed weapon breaks");
        VillagerInventoryContainer.returnBorrowedCombatWeapon(villager);
        helper.assertValueEqual(count(villager, sword), 0, "repeated returns do not recreate the sword");
        helper.assertValueEqual(count(villager, displaced), 0, "restored gear has no second storage copy");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullLoanReturnCannotBeOverwrittenByNextRequest(GameTestHelper helper) {
        Villager villager = villager(helper);
        ItemStack sword = decorated(helper, new ItemStack(Items.IRON_SWORD), "Outstanding loan");
        VillagerInventoryContainer.addItem(villager, sword);
        helper.assertTrue(VillagerInventoryContainer.tryBorrowCombatWeapon(villager), "sword is borrowed before inventory fills");
        var full = NonNullList.withSize(VillagerInventoryContainer.INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        for (int index = 0; index < full.size(); index++) full.set(index, new ItemStack(Items.DIRT, 64));
        full.set(1, new ItemStack(Items.CROSSBOW));
        VillagerInventoryContainer.saveFullInventory(villager, full);
        helper.assertFalse(VillagerInventoryContainer.tryBorrowCombatWeapon(villager, stack -> stack.is(Items.CROSSBOW)),
                "an unsuccessful first return must reject another loan request");
        same(helper, villager.getMainHandItem(), sword, "failed return retains the original sword in hand");
        helper.assertValueEqual(count(villager, new ItemStack(Items.CROSSBOW)), 1, "rejected request leaves crossbow in storage");
        VillagerInventoryContainer.returnBorrowedCombatWeapon(villager);
        helper.assertFalse(VillagerInventoryContainer.hasBorrowedCombatWeapon(villager), "existing second-failure drop rule finishes the loan");
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(2),
                entity -> ItemStack.isSameItemSameComponents(entity.getItem(), sword)).stream().mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertValueEqual(dropped, 1, "the unreturnable sword drops exactly once under the existing rule");
        VillagerInventoryContainer.returnBorrowedCombatWeapon(villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void jobEquipmentWriteFinishesFullLoanBeforeTakingAuthority(GameTestHelper helper) {
        Villager villager = villager(helper);
        ItemStack sword = decorated(helper, new ItemStack(Items.IRON_SWORD), "Full inventory loan");
        VillagerInventoryContainer.addItem(villager, sword);
        helper.assertTrue(VillagerInventoryContainer.tryBorrowCombatWeapon(villager), "sword loan begins");
        var full = NonNullList.withSize(VillagerInventoryContainer.INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        for (int index = 0; index < full.size(); index++) full.set(index, new ItemStack(Items.DIRT, 64));
        VillagerInventoryContainer.saveFullInventory(villager, full);
        ItemStack jobBow = decorated(helper, new ItemStack(Items.BOW), "New job equipment");
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, jobBow.copy());
        helper.assertFalse(VillagerInventoryContainer.hasBorrowedCombatWeapon(villager), "job write finishes the outstanding full-inventory loan");
        VillagerInventoryContainer.returnBorrowedCombatWeapon(villager);
        same(helper, villager.getMainHandItem(), jobBow, "repeated old return cannot take the new job hand");
        same(helper, inventory.getItem(HiredJobInventory.MAINHAND_SLOT), jobBow, "new gear retains exactly one authoritative owner");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(2));
        helper.assertValueEqual(drops.stream().filter(e -> ItemStack.isSameItemSameComponents(e.getItem(), sword)).mapToInt(e -> e.getItem().getCount()).sum(),
                1, "existing full-return rule drops the borrowed sword once");
        helper.assertFalse(drops.stream().anyMatch(e -> e.getItem().is(Items.BOW)), "new job gear is not copied into overflow drops");
        helper.assertValueEqual(count(villager, jobBow), 0, "new job gear is not copied to personal inventory");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void borrowedWearAndModItemsRoundTripAcrossEntityReload(GameTestHelper helper) {
        Villager villager = villager(helper);
        ItemStack sword = decorated(helper, new ItemStack(Items.IRON_SWORD), "Serialized sword");
        ItemStack clipboard = decorated(helper, new ItemStack(VillagerRetaliationItems.CLIPBOARD.get()), "Mod clipboard");
        VillagerInventoryContainer.addItem(villager, sword);
        VillagerInventoryContainer.addItem(villager, clipboard);
        helper.assertTrue(VillagerInventoryContainer.tryBorrowCombatWeapon(villager), "sword loan begins");
        VillagerEquipmentDurability.postMeleeHit(villager, villager, InteractionHand.MAIN_HAND);
        ItemStack expected = villager.getMainHandItem().copy();
        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        loaded.load(villager.saveWithoutId(new CompoundTag()));
        VillagerRetaliationVillagerEquipment.clearRuntimeState(loaded);
        VillagerInventoryContainer.returnBorrowedCombatWeapon(loaded);
        helper.assertValueEqual(count(loaded, expected), 1, "reloaded loan returns exactly once with worn components");
        helper.assertValueEqual(count(loaded, clipboard), 1, "registered mod item and nested custom NBT survive entity reload");
        same(helper, VillagerInventoryContainer.loadFullInventory(loaded).stream().filter(s -> s.is(Items.IRON_SWORD)).findFirst().orElseThrow(),
                expected, "enchantment, custom data, name and durability remain exact");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void firedBorrowedCrossbowBreakCannotOverwriteJobAuthority(GameTestHelper helper) throws Exception {
        Villager villager = villager(helper);
        Villager target = villager(helper);
        target.setPos(villager.getX() + 3, villager.getY(), villager.getZ());
        ItemStack jobBow = decorated(helper, new ItemStack(Items.BOW), "Job bow");
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, jobBow.copy());
        ItemStack crossbow = decorated(helper, new ItemStack(Items.CROSSBOW), "Borrowed crossbow");
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
        crossbow.setDamageValue(crossbow.getMaxDamage() - 1);
        VillagerInventoryContainer.addItem(villager, crossbow);
        helper.assertTrue(VillagerInventoryContainer.tryBorrowCombatWeapon(villager, s -> s.is(Items.CROSSBOW)), "charged personal crossbow overlays job bow");
        var fire = VillagerRangedCombatHelper.class.getDeclaredMethod("fireCrossbowLikePillager", AbstractVillager.class, LivingEntity.class, ServerLevel.class);
        fire.setAccessible(true);
        helper.assertTrue((boolean) fire.invoke(null, villager, target, helper.getLevel()), "native crossbow firing executes");
        helper.assertFalse(VillagerInventoryContainer.hasBorrowedCombatWeapon(villager), "firing consumes broken crossbow loan");
        same(helper, inventory.getItem(HiredJobInventory.MAINHAND_SLOT), jobBow, "ranged sync leaves underlying job bow unchanged");
        same(helper, villager.getMainHandItem(), jobBow, "job bow resumes after personal crossbow breaks");
        helper.assertValueEqual(count(villager, jobBow), 0, "job bow is not duplicated into personal storage");
        helper.assertValueEqual(VillagerInventoryContainer.loadFullInventory(villager).stream().filter(s -> s.is(Items.CROSSBOW)).count(), 0L, "broken crossbow is not recreated");
        villager.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shieldBreakClearsSavedEquipmentAuthority(GameTestHelper helper) throws Exception {
        var hurt = Class.forName("com.jvn.villagerretaliation.combat.VillagerArmorerCombatTactics")
                .getDeclaredMethod("applyShieldDurabilityDamage", Villager.class, float.class);
        hurt.setAccessible(true);
        for (boolean job : List.of(false, true)) {
            Villager villager = villager(helper);
            ItemStack shield = decorated(helper, new ItemStack(Items.SHIELD), "Breakable shield");
            shield.setDamageValue(shield.getMaxDamage() - 1);
            if (job) HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.OFFHAND_SLOT, shield);
            else VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, EquipmentSlot.MAINHAND, shield);
            villager.startUsingItem(job ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            helper.assertTrue((boolean) hurt.invoke(null, villager, 1.0F), "native blocking durability breaks the shield");
            HiredJobInventory.clearRuntimeState(villager);
            HiredJobInventory.maintainEquipmentSlots(villager);
            VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager);
            helper.assertTrue(villager.getMainHandItem().isEmpty() && villager.getOffhandItem().isEmpty(), "maintenance cannot restore broken shield");
            if (job) helper.assertTrue(HiredJobInventory.getJobInventory(villager).getItem(HiredJobInventory.OFFHAND_SLOT).isEmpty(), "job shield authority stays consumed after reload");
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vanillaClearedTrackedMainHandDropsOnceWithCurrentComponents(GameTestHelper helper) {
        Villager villager = villager(helper);
        ItemStack saved = decorated(helper, new ItemStack(Items.IRON_SWORD), "Owned sword");
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, EquipmentSlot.MAINHAND, saved);
        ItemStack worn = saved.copy();
        worn.setDamageValue(17);
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(drop(villager, worn));
        ItemStack unrelated = decorated(helper, new ItemStack(Items.IRON_SWORD), "Other owner's sword");
        drops.add(drop(villager, unrelated));
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        LivingDropsEvent event = new LivingDropsEvent(villager, villager.damageSources().generic(), drops, false);
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);
        helper.assertValueEqual(drops.size(), 2, "saved ownership does not duplicate the vanilla drop or consume unrelated gear");
        helper.assertTrue(drops.stream().anyMatch(d -> ItemStack.isSameItemSameComponents(d.getItem(), worn)), "current durability retained");
        helper.assertTrue(drops.stream().anyMatch(d -> ItemStack.isSameItemSameComponents(d.getItem(), unrelated)), "unrelated item retained");
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);
        helper.assertValueEqual(drops.size(), 2, "repeated processing has no remaining owned stack to add");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deathDoesNotReplaceJobGearWithUnrelatedSameItemDrop(GameTestHelper helper) {
        Villager villager = villager(helper);
        ItemStack owned = decorated(helper, new ItemStack(Items.IRON_CHESTPLATE), "Owned armor");
        HiredJobInventory.getJobInventory(villager).setItem(1, owned);
        ItemStack unrelated = decorated(helper, new ItemStack(Items.IRON_CHESTPLATE), "Other armor");
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(drop(villager, unrelated));
        villager.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager,
                new LivingDropsEvent(villager, villager.damageSources().generic(), drops, false));
        helper.assertValueEqual(drops.size(), 2, "same item type alone does not identify job equipment");
        helper.assertTrue(drops.stream().anyMatch(d -> ItemStack.isSameItemSameComponents(d.getItem(), owned)), "owned components preserved");
        helper.assertTrue(drops.stream().anyMatch(d -> ItemStack.isSameItemSameComponents(d.getItem(), unrelated)), "unrelated components preserved");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deathPreservesPersonalLoanAndUnderlyingJobHand(GameTestHelper helper) {
        Villager villager = villager(helper);
        ItemStack jobSword = decorated(helper, new ItemStack(Items.IRON_SWORD), "Job owner");
        ItemStack personalSword = decorated(helper, new ItemStack(Items.DIAMOND_SWORD), "Personal owner");
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, jobSword);
        VillagerInventoryContainer.addItem(villager, personalSword);
        helper.assertTrue(VillagerInventoryContainer.tryBorrowCombatWeapon(villager), "better personal sword overlays the job hand");
        List<ItemEntity> drops = new ArrayList<>();
        LivingDropsEvent event = new LivingDropsEvent(villager, villager.damageSources().generic(), drops, false);
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);
        helper.assertValueEqual(drops.size(), 2, "personal loan and underlying job hand are two owned items");
        helper.assertTrue(drops.stream().anyMatch(d -> ItemStack.isSameItemSameComponents(d.getItem(), jobSword)), "job sword drops once");
        helper.assertTrue(drops.stream().anyMatch(d -> ItemStack.isSameItemSameComponents(d.getItem(), personalSword)), "borrowed sword drops once");
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);
        helper.assertValueEqual(drops.size(), 2, "both ownership records are consumed after death");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void menuTransfersConserveComponentsAcrossFullInventoryAndCancellation(GameTestHelper helper) {
        Villager villager = villager(helper);
        ServerPlayer player = player(helper, villager, "MenuTransfers");
        ItemStack helmet = decorated(helper, new ItemStack(Items.IRON_HELMET), "Player helmet");
        helmet.setDamageValue(23);
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.getInventory().setItem(0, helmet.copy());
        VillagerInventoryMenu menu = menu(villager, player);
        int hotbar = menu.slots.size() - 9;
        menu.quickMoveStack(player, hotbar);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "occupied armor slot falls back to personal storage");
        helper.assertValueEqual(count(villager, helmet), 1, "fallback retains all helmet components");
        int source = -1;
        for (int slot = 4; slot < VillagerInventoryContainer.HELD_SLOT; slot++) {
            if (ItemStack.isSameItemSameComponents(menu.getSlot(slot).getItem(), helmet)) source = slot;
        }
        helper.assertTrue(source >= 0, "stored helmet has an accessible source slot");
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(menu.quickMoveStack(player, source).isEmpty(), "full player inventory rejects transfer");
        helper.assertValueEqual(count(villager, helmet), 1, "rejected transfer leaves source unchanged");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        menu.quickMoveStack(player, source);
        menu.quickMoveStack(player, source);
        same(helper, player.getInventory().getItem(0), helmet, "repeated transfer returns exactly one original stack");
        helper.assertValueEqual(count(villager, helmet), 0, "successful return clears source");
        menu.setCarried(decorated(helper, new ItemStack(Items.SHIELD), "Canceled cursor"));
        ItemStack cursor = menu.getCarried().copy();
        // Close with a carried stack, as when a player cancels the inventory screen.
        menu.removed(player);
        helper.assertTrue(menu.getCarried().isEmpty(), "closing returns carried items through vanilla menu cleanup");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, cursor))
                        || helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2),
                                e -> ItemStack.isSameItemSameComponents(e.getItem(), cursor)).size() == 1,
                "closing with full inventory preserves the cursor stack by return or drop");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void invalidAndDistantMenuRequestsCannotMoveItems(GameTestHelper helper) {
        Villager villager = villager(helper);
        ServerPlayer player = player(helper, villager, "InvalidRequests");
        ItemStack shield = decorated(helper, new ItemStack(Items.SHIELD), "Unmoved shield");
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, EquipmentSlot.OFFHAND, shield);
        VillagerInventoryMenu menu = menu(villager, player);
        menu.clicked(Integer.MAX_VALUE, 0, ClickType.PICKUP, player);
        menu.clicked(-2, 0, ClickType.PICKUP, player);
        menu.quickMoveStack(player, Integer.MIN_VALUE);
        same(helper, villager.getOffhandItem(), shield, "invalid slot requests leave equipment untouched");
        player.setPos(villager.getX() + 1000, villager.getY(), villager.getZ());
        menu.quickMoveStack(player, VillagerInventoryContainer.OFFHAND_SLOT);
        menu.clicked(VillagerInventoryContainer.OFFHAND_SLOT, 0, ClickType.PICKUP, player);
        same(helper, villager.getOffhandItem(), shield, "distance invalidates mutation before next menu tick");
        helper.assertTrue(menu.getCarried().isEmpty(), "rejected click cannot take a cursor copy");
        menu.removed(player);
        villager.discard();
        helper.succeed();
    }

    private static Villager villager(GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        villager.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        villager.setNoAi(true);
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static ServerPlayer player(GameTestHelper helper, Villager villager, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        player.getInventory().clearContent();
        player.setHealth(player.getMaxHealth());
        player.setPos(villager.getX(), villager.getY(), villager.getZ());
        VillagerReputationManager.setReputation(helper.getLevel(), villager, player.getUUID(), VillagerRetaliationConfig.REVERED_THRESHOLD.get());
        return player;
    }

    private static VillagerInventoryMenu menu(Villager villager, ServerPlayer player) {
        return new VillagerInventoryMenu(1, player.getInventory(), villager, VillagerInventoryMenu.ViewMode.PERSONAL, true, false);
    }

    private static ItemStack decorated(GameTestHelper helper, ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CompoundTag nested = new CompoundTag();
        nested.putString("identity", name);
        nested.putIntArray("payload", new int[]{7, 23, 41});
        CompoundTag custom = new CompoundTag();
        custom.put("other_mod", nested);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        return stack;
    }

    private static int count(Villager villager, ItemStack expected) {
        return VillagerInventoryContainer.loadFullInventory(villager).stream()
                .filter(s -> ItemStack.isSameItemSameComponents(s, expected)).mapToInt(ItemStack::getCount).sum();
    }

    private static void same(GameTestHelper helper, ItemStack actual, ItemStack expected, String message) {
        helper.assertTrue(actual.getCount() == expected.getCount() && ItemStack.isSameItemSameComponents(actual, expected), message);
    }

    private static ItemEntity drop(Villager villager, ItemStack stack) {
        return new ItemEntity(villager.level(), villager.getX(), villager.getY(), villager.getZ(), stack.copy());
    }
}
