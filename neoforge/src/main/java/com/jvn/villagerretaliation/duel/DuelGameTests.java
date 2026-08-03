package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.combat.VillagerCombatRoles;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class DuelGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private DuelGameTests() {}

    @GameTest(template = EMPTY_TEMPLATE)
    public static void recordsAreIsolatedPerPlayerAndLossStreakRefuses(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.markStarted(villager, first, 100L);
        data.complete(villager, first, DuelResult.PLAYER_WIN);
        data.complete(villager, first, DuelResult.PLAYER_WIN);
        DuelSavedData.DuelRecord refused = data.complete(villager, first, DuelResult.PLAYER_WIN);
        helper.assertTrue(refused.refuses() && refused.consecutiveLosses() == 3,
                "three consecutive losses should permanently refuse the opponent");
        helper.assertValueEqual(data.record(villager, second), DuelSavedData.DuelRecord.EMPTY,
                "duel records must remain isolated per player");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villagerVictoryResetsLossStreakAndQueuesReactions(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        data.complete(villager, player, DuelResult.PLAYER_WIN);
        DuelSavedData.DuelRecord record = data.complete(villager, player, DuelResult.VILLAGER_WIN);
        helper.assertValueEqual(record.consecutiveLosses(), 0, "villager victory should reset its loss streak");
        helper.assertValueEqual(data.consumeReaction(villager, player), DuelSavedData.Reaction.GLOAT,
                "victory should queue a one-time gloat");
        helper.assertValueEqual(data.consumeReaction(villager, player), DuelSavedData.Reaction.SULK,
                "later queued reactions should remain available");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void recordsHistoryAndStoryAcknowledgementsSerialize(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID speaker = UUID.randomUUID();
        UUID village = UUID.randomUUID();
        data.markStarted(villager, player, 500L);
        DuelSavedData.DuelRecord record = data.complete(villager, player, DuelResult.VILLAGER_WIN);
        UUID eventId = UUID.randomUUID();
        data.remember(new DuelSavedData.DuelMemory(eventId, villager, player, "Ada", "Player",
                DuelResult.VILLAGER_WIN, 16, 600L, new BlockPos(1, 2, 3).asLong(), village,
                Set.of(speaker), record.villagerWins(), record.villagerLosses()));
        data.acknowledgeStory(speaker, player, eventId);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        DuelSavedData loaded = DuelSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.record(villager, player), record, "duel record did not survive NBT");
        helper.assertValueEqual(loaded.history().size(), 1, "duel history did not survive NBT");
        helper.assertTrue(loaded.history().get(0).witnessIds().contains(speaker),
                "duel story witnesses did not survive NBT");
        helper.assertTrue(loaded.storyAcknowledged(speaker, player, eventId),
                "story acknowledgement did not survive NBT");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villageHistoryIsCappedAtSixtyFour(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID(), player = UUID.randomUUID(), village = UUID.randomUUID();
        for (int index = 0; index < 70; index++) {
            data.remember(new DuelSavedData.DuelMemory(UUID.randomUUID(), villager, player, "Ada", "Player",
                    DuelResult.DRAW, 0, index, BlockPos.ZERO.asLong(), village, Set.of(), 0, 0));
        }
        helper.assertValueEqual(data.history().size(), 64, "village duel history must remain bounded");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void assignedLoadoutRestoresInventoryAndRejectsSmuggling(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().setItem(4, new ItemStack(Items.APPLE, 3));
        player.getInventory().selected = 4;
        villager.getInventory().setItem(0, new ItemStack(Items.BREAD, 2));
        villager.setCanPickUpLoot(true);

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 0);
        helper.assertTrue(start.started(), "assigned duel should start: " + start.reason());
        helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 0,
                "assigned gear must isolate the pre-duel inventory");
        helper.assertValueEqual(player.getInventory().selected, 0,
                "assigned gear should select its first usable hotbar slot");
        helper.assertTrue(!DuelService.allowsInventoryClick(
                        player, player.inventoryMenu, InventoryMenu.USE_ROW_SLOT_START, ClickType.PICKUP),
                "server must reject assigned-loadout inventory packets");
        helper.assertTrue(!player.drop(true), "duel participants must not drop their selected stack");
        helper.assertValueEqual(player.getInventory().getSelected().getItem(), Items.IRON_SWORD,
                "blocking a duel drop must leave the selected gear in the inventory");

        ItemEntity pickup = new ItemEntity(participant.level(), player.getX(), player.getY(), player.getZ(),
                new ItemStack(Items.DIAMOND));
        ItemEntityPickupEvent.Pre pickupEvent = new ItemEntityPickupEvent.Pre(player, pickup);
        DuelService.onItemPickup(pickupEvent);
        helper.assertValueEqual(pickupEvent.canPickup(), TriState.FALSE,
                "duel participants must not pick up world items");

        ItemTossEvent tossEvent = new ItemTossEvent(
                new ItemEntity(participant.level(), player.getX(), player.getY(), player.getZ(),
                        new ItemStack(Items.DIAMOND)),
                player);
        DuelService.onItemToss(tossEvent);
        helper.assertTrue(tossEvent.isCanceled(), "duel participants must not toss items");

        ItemEntity villagerPickup = new ItemEntity(
                participant.level(), villager.getX(), villager.getY(), villager.getZ(), new ItemStack(Items.EMERALD));
        helper.assertTrue(!villager.wantsToPickUp(villagerPickup.getItem()),
                "dueling villagers must reject world items");
        helper.assertTrue(villager.spawnAtLocation(new ItemStack(Items.DIAMOND)) == null,
                "dueling villagers must not spawn dropped items");

        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().add(new ItemStack(Items.DIAMOND, 17));
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW), "live duel should resolve once");
        helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 3,
                "pre-duel inventory must be restored exactly");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 0,
                "items introduced during an assigned duel must not escape restoration");
        helper.assertValueEqual(player.getInventory().selected, 4,
                "selected hotbar slot must be restored");
        helper.assertValueEqual(villager.getInventory().countItem(Items.BREAD), 2,
                "villager inventory must be restored exactly");
        helper.assertTrue(villager.canPickUpLoot(), "villager pickup policy must be restored");
        pickup.discard();
        villagerPickup.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void duelTemporarilyNormalizesAndRestoresParticipantVitals(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.setHealth(7.0F);
        player.getFoodData().setFoodLevel(6);
        player.getFoodData().setSaturation(1.5F);
        player.getFoodData().setExhaustion(3.25F);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
        villager.setHealth(8.0F);
        VillagerRecoveryService.restoreRecoveryState(
                villager, new VillagerRecoveryService.RecoverySnapshot(5, 1.25F, 2.5F, 17));
        villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 500, 2));

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.BARE_HANDED, 0);
        helper.assertTrue(start.started(), "vitals test duel should start: " + start.reason());
        helper.assertValueEqual(player.getHealth(), player.getMaxHealth(),
                "the player should start the duel fully healed");
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), 20,
                "the player should start the duel fully fed");
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 20.0F,
                "the player should start the duel with full saturation");
        helper.assertValueEqual(player.getFoodData().getExhaustionLevel(), 0.0F,
                "the player should start the duel without exhaustion");
        helper.assertTrue(player.getActiveEffects().isEmpty(),
                "the player should start the duel without status effects");
        helper.assertValueEqual(villager.getHealth(), villager.getMaxHealth(),
                "the villager should start the duel fully healed");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(villager), 20,
                "the villager should start the duel fully fed");
        helper.assertValueEqual(VillagerRecoveryService.saturationLevel(villager), 20.0F,
                "the villager should start the duel with full saturation");
        helper.assertTrue(villager.getActiveEffects().isEmpty(),
                "the villager should start the duel without status effects");

        player.setHealth(2.0F);
        player.getFoodData().setFoodLevel(3);
        villager.setHealth(3.0F);
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW),
                "vitals test duel should resolve");

        helper.assertValueEqual(player.getHealth(), 7.0F, "the player's original health must return");
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), 6,
                "the player's original hunger must return");
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 1.5F,
                "the player's original saturation must return");
        helper.assertValueEqual(player.getFoodData().getExhaustionLevel(), 3.25F,
                "the player's original exhaustion must return");
        MobEffectInstance playerEffect = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(playerEffect != null && playerEffect.getDuration() == 600 && playerEffect.getAmplifier() == 1,
                "the player's original effect must return unchanged");
        helper.assertValueEqual(villager.getHealth(), 8.0F, "the villager's original health must return");
        VillagerRecoveryService.RecoverySnapshot restoredVillagerRecovery =
                VillagerRecoveryService.captureRecoveryState(villager);
        helper.assertValueEqual(restoredVillagerRecovery,
                new VillagerRecoveryService.RecoverySnapshot(5, 1.25F, 2.5F, 17),
                "the villager's original recovery state must return");
        MobEffectInstance villagerEffect = villager.getEffect(MobEffects.DAMAGE_BOOST);
        helper.assertTrue(villagerEffect != null && villagerEffect.getDuration() == 500 && villagerEffect.getAmplifier() == 2,
                "the villager's original effect must return unchanged");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void partyVillagerKnockoutRevivesWithPreDuelVitals(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().add(new ItemStack(
                Items.EMERALD, PartyVillagerContractService.DAILY_EMERALD_COST));
        PartyVillagerContractService.ContractResult recruited =
                PartyVillagerContractService.recruit(player, villager);
        helper.assertTrue(recruited.success(), "knockout restoration fixture should recruit the duelist");
        villager.setHealth(villager.getMaxHealth());
        VillagerRecoveryService.restoreRecoveryState(
                villager, new VillagerRecoveryService.RecoverySnapshot(14, 3.0F, 1.5F, 9));
        villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));

        DuelService.StartResult start = DuelService.startDebug(player, villager, DuelLoadout.BARE_HANDED, 0);
        helper.assertTrue(start.started(), "party-villager knockout duel should start: " + start.reason());
        villager.setHealth(1.0F);

        helper.assertTrue(DuelService.resolveVillagerKnockoutForTest(player),
                "player-win knockout should resolve");
        if (!VillagerDownedService.isDowned(villager)) {
            VillagerDownedService.enterDowned(
                    participant.level(), villager,
                    new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("duel:test")),
                    villager.getHealth());
        }
        helper.assertTrue(VillagerDownedService.isDowned(villager),
                "a knocked-out villager should retain the Second Wind downed presentation");
        helper.assertValueEqual(villager.getHealth(), 1.0F,
                "the downed presentation should retain its incapacitated health");
        helper.assertTrue(VillagerDownedService.recoveryHealth(villager).isPresent()
                        && Math.abs(VillagerDownedService.recoveryHealth(villager).getAsDouble()
                        - villager.getMaxHealth()) < 0.000001D,
                "the downed state must retain the exact pre-duel revive health");

        VillagerDownedService.recover(villager);

        helper.assertValueEqual(villager.getHealth(), villager.getMaxHealth(),
                "a revived villager must return to its full pre-duel health");
        helper.assertValueEqual(
                VillagerRecoveryService.captureRecoveryState(villager),
                new VillagerRecoveryService.RecoverySnapshot(14, 3.0F, 1.5F, 9),
                "a revived villager must regain its pre-duel hunger state");
        MobEffectInstance effect = villager.getEffect(MobEffects.DAMAGE_RESISTANCE);
        helper.assertTrue(effect != null && effect.getDuration() == 400 && effect.getAmplifier() == 1,
                "a revived villager must regain its pre-duel effects without regeneration");
        helper.assertFalse(VillagerDownedService.isDowned(villager),
                "revival should release the downed presentation");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void debugDuelRecoversDownedVillagerBeforeCombat(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        VillagerDownedService.enterDowned(
                participant.level(),
                villager,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("duel:test")));
        helper.assertTrue(VillagerDownedService.isDowned(villager),
                "fixture villager should begin downed");
        helper.assertTrue(villager.isNoAi(),
                "downed fixture villager should begin incapacitated");

        DuelService.StartResult start = DuelService.startDebug(
                player, villager, DuelLoadout.ARMORED, 0);
        helper.assertTrue(start.started(), "debug duel should start: " + start.reason());
        try {
            helper.assertFalse(VillagerDownedService.isDowned(villager),
                    "debug duel startup must clear the stale downed state");
            helper.assertFalse(villager.isNoAi(),
                    "debug duel startup must restore villager AI");

            long now = participant.level().getServer().overworld().getGameTime();
            player.moveTo(villager.getX() + 1.0D, villager.getY(), villager.getZ());
            helper.assertTrue(DuelService.driveForTest(player, now, false),
                    "recovered debug duelist should enter combat tactics");
            helper.assertTrue(villager.isUsingItem() && villager.getUseItem().is(Items.SHIELD),
                    "recovered debug duelist should raise its shield");

            VillagerDownedService.onVillagerTickPre(villager);
            helper.assertTrue(villager.isUsingItem() && villager.getUseItem().is(Items.SHIELD),
                    "stale incapacitation must not cancel shielding before an axe hit");
            helper.succeed();
        } finally {
            DuelService.resolveForTest(player, DuelResult.CANCELLED);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedMeleeLoadoutSuppressesTrackedRangedWeapon(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        VillagerRetaliationVillagerEquipment.setPickedUpMainHand(
                villager, new ItemStack(Items.CROSSBOW));

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 0);
        helper.assertTrue(start.started(), "melee duel should start: " + start.reason());
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                "melee duel should replace the villager's tracked ranged weapon");
        helper.assertTrue(villager.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                "melee duel should equip the assigned shield");
        helper.assertTrue(DuelService.isParticipant(villager),
                "the villager should be registered as an active duel participant");
        helper.assertFalse(VillagerRetaliationVillagerEquipment.hasPickedUpMainHand(villager),
                "assigned duel gear should temporarily clear tracked weapon ownership");

        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(DuelService.isParticipant(villager),
                        "the villager should remain an active duel participant");
                helper.assertFalse(VillagerRetaliationVillagerEquipment.hasPickedUpMainHand(villager),
                        "tracked weapon ownership must stay suppressed during the duel");
                helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                        "normal equipment maintenance must not restore a tracked weapon during a duel");
                helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW),
                        "active duel should resolve");
                helper.assertTrue(villager.getMainHandItem().is(Items.CROSSBOW),
                        "the villager's original weapon should return after the duel");
                helper.succeed();
            } finally {
                DuelService.resolveForTest(player, DuelResult.CANCELLED);
            }
        });
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void assignedDuelLoadoutCannotBorrowPartyInventoryWeapons(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        HiredJobInventory partyInventory = HiredJobInventory.getJobInventory(villager);
        partyInventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.CROSSBOW));
        partyInventory.setItem(HiredJobInventory.HOTBAR_START + 1, new ItemStack(Items.NETHERITE_AXE));
        partyInventory.setItem(HiredJobInventory.HOTBAR_START + 2, new ItemStack(Items.ARROW, 16));

        DuelService.StartResult start = DuelService.startDebug(
                player, villager, DuelLoadout.MELEE, 0);
        helper.assertTrue(start.started(), "party-inventory isolation duel should start: " + start.reason());
        try {
            helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                    "the assigned melee weapon should be equipped initially");

            player.moveTo(villager.getX() + 10.0D, villager.getY(), villager.getZ());
            helper.assertTrue(DuelService.driveForTest(player, participant.level().getGameTime(), true),
                    "duel combat should run");
            helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                    "long-range tactics must not borrow a crossbow from party inventory");

            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            player.startUsingItem(InteractionHand.OFF_HAND);
            helper.assertTrue(DuelService.driveForTest(player, participant.level().getGameTime() + 1L, true),
                    "shield-breaking duel combat should run");
            helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                    "shield-breaking tactics must not borrow an axe from party inventory");

            helper.assertTrue(partyInventory.getItem(HiredJobInventory.HOTBAR_START).is(Items.CROSSBOW),
                    "the party crossbow must remain in its original slot");
            helper.assertTrue(partyInventory.getItem(HiredJobInventory.HOTBAR_START + 1).is(Items.NETHERITE_AXE),
                    "the party axe must remain in its original slot");
            helper.assertValueEqual(
                    partyInventory.getItem(HiredJobInventory.HOTBAR_START + 2).getCount(),
                    16,
                    "duel combat must not consume party ammunition");
            helper.succeed();
        } finally {
            DuelService.resolveForTest(player, DuelResult.CANCELLED);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 140)
    public static void armoredDuelUsesAxeAndShieldTactics(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        NonNullList<ItemStack> originalInventory = NonNullList.withSize(36, ItemStack.EMPTY);
        originalInventory.set(0, new ItemStack(Items.BREAD, 3));
        originalInventory.set(20, new ItemStack(Items.EMERALD_BLOCK));
        VillagerInventoryAccess.replaceFullInventory(villager, originalInventory);
        VillagerRetaliationVillagerEquipment.setPickedUpMainHand(
                villager, new ItemStack(Items.DIAMOND_SWORD));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager, EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager, EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager, EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager, EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.ARMORED, 0);
        helper.assertTrue(start.started(), "armored duel should start: " + start.reason());

        helper.runAfterDelay(65, () -> {
            try {
                long now = participant.level().getServer().overworld().getGameTime();
                player.moveTo(villager.getX() + 1.0D, villager.getY(), villager.getZ(), 0.0F, 0.0F);
                player.startUsingItem(InteractionHand.OFF_HAND);
                helper.assertTrue(DuelService.driveForTest(player, now, true), "armored duel should remain active for axe tactics");
                helper.assertTrue(villager.getMainHandItem().is(Items.IRON_AXE), "shielding opponent should make the armored villager equip its iron axe");
                helper.assertFalse(player.isUsingItem(), "the villager axe attack should disable the player's shield");

                helper.assertTrue(DuelService.driveForTest(player, now + 1L, false), "armored duel should remain active for guard tactics");
                helper.assertTrue(villager.isUsingItem(), "armored villager should raise its shield between attacks");
                helper.assertTrue(villager.getUsedItemHand() == InteractionHand.OFF_HAND, "armored villager should guard with its off hand");
                helper.assertTrue(villager.getUseItem().is(Items.SHIELD), "armored villager should actively use its shield");

                helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW), "armored duel should resolve as a draw");
                VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager);
                helper.assertTrue(villager.getMainHandItem().is(Items.DIAMOND_SWORD), EMPTY_TEMPLATE);
                helper.assertTrue(villager.getOffhandItem().is(Items.TOTEM_OF_UNDYING), EMPTY_TEMPLATE);
                helper.assertTrue(villager.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), EMPTY_TEMPLATE);
                helper.assertTrue(villager.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), EMPTY_TEMPLATE);
                helper.assertTrue(villager.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS), EMPTY_TEMPLATE);
                helper.assertTrue(villager.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS), EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.DIAMOND_SWORD), 1, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.BREAD), 3, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.EMERALD_BLOCK), 1, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.IRON_SWORD), 0, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.IRON_AXE), 0, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.SHIELD), 0, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.IRON_HELMET), 0, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.IRON_CHESTPLATE), 0, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.IRON_LEGGINGS), 0, EMPTY_TEMPLATE);
                helper.assertValueEqual(countVillagerItem(villager, Items.IRON_BOOTS), 0, EMPTY_TEMPLATE);
                helper.succeed();
            } finally {
                DuelService.resolveForTest(player, DuelResult.CANCELLED);
            }
        });
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void meleeDuelUsesNormalCombatMovementSpeed(GameTestHelper helper) {
        Participant participant = participant(helper);
        double normalSpeed = VillagerCombatRoles.movementSpeed(participant.villager());
        helper.assertTrue(
                Math.abs(DuelService.duelMovementSpeed(participant.villager()) - normalSpeed) < 0.000001D,
                "melee duel pursuit should use the normal villager combat movement speed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void duelMeleeDamageCountsWeaponOnceAndClearsStaleBase(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.ARMORED, 0);
        helper.assertTrue(start.started(), "armored damage test duel should start: " + start.reason());

        var attackDamage = villager.getAttribute(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(attackDamage != null, "villager should have the melee attack attribute");
        attackDamage.setBaseValue(100.0D);

        helper.runAfterDelay(2, () -> {
            try {
                DuelService.attackMelee(villager, player);
                double[] weaponDamage = new double[]{0.0D};
                villager.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                    if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                        weaponDamage[0] += modifier.amount();
                    }
                });
                helper.assertTrue(weaponDamage[0] > 0.0D,
                        "the assigned iron sword should expose an attack-damage modifier");
                double expectedBase = VillagerCombatRoles.meleeAttackDamageBase(villager);
                helper.assertTrue(Math.abs(attackDamage.getBaseValue() - expectedBase) < 0.000001D,
                        "duel attacks must replace a stale attack base with the current difficulty base");
                helper.assertTrue(Math.abs(attackDamage.getValue() - (expectedBase + weaponDamage[0])) < 0.000001D,
                        "the equipped weapon's attack modifier should be counted exactly once");

                float damageAfterArmor = CombatRules.getDamageAfterAbsorb(
                        player,
                        (float) attackDamage.getValue(),
                        participant.level().damageSources().mobAttack(villager),
                        player.getArmorValue(),
                        (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                helper.assertTrue(damageAfterArmor > 0.0F && damageAfterArmor < 10.0F,
                        "one iron-sword hit through full iron armor should deal less than half a player's health; got "
                                + damageAfterArmor);
                helper.succeed();
            } finally {
                DuelService.resolveForTest(player, DuelResult.CANCELLED);
            }
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void validDuelArrowDamageIsHandledWithoutCancellation(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.RANGED, 0);
        helper.assertTrue(start.started(), "ranged duel should start: " + start.reason());

        helper.runAfterDelay(65, () -> {
            try {
                Arrow arrow = new Arrow(
                        participant.level(), player,
                        new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
                LivingIncomingDamageEvent damageEvent = new LivingIncomingDamageEvent(
                        villager,
                        new DamageContainer(
                                participant.level().damageSources().arrow(arrow, player), 4.0F));

                helper.assertTrue(DuelService.onIncomingDamage(damageEvent),
                        "valid arrow damage should stop at the duel policy");
                helper.assertFalse(damageEvent.isCanceled(),
                        "the duel policy should allow an opponent's arrow after the countdown");
                helper.assertValueEqual(damageEvent.getAmount(), 4.0F,
                        "valid duel arrow damage should keep its original amount");
                helper.succeed();
            } finally {
                DuelService.resolveForTest(player, DuelResult.CANCELLED);
            }
        });
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void losingDuelAppliesSlownessAndBlocksAttacks(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 0);
        helper.assertTrue(start.started(), "loss-penalty duel should start: " + start.reason());
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.VILLAGER_WIN),
                "villager victory should resolve the duel");

        var slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slowness != null, "losing player should receive slowness");
        helper.assertValueEqual(slowness.getAmplifier(), 1,
                "loss penalty should apply Slowness II");
        helper.assertTrue(slowness.getDuration() > 0 && slowness.getDuration() <= 100,
                "loss penalty should last no more than five seconds");
        helper.assertTrue(DuelService.isPostLossAttackLocked(player),
                "losing player should have a matching server-side attack lockout");

        AttackEntityEvent attackEvent = new AttackEntityEvent(player, villager);
        DuelService.onAttackEntity(attackEvent);
        helper.assertTrue(attackEvent.isCanceled(), "direct attacks should be canceled during the loss penalty");

        LivingIncomingDamageEvent damageEvent = new LivingIncomingDamageEvent(
                villager,
                new DamageContainer(participant.level().damageSources().playerAttack(player), 4.0F));
        helper.assertTrue(DuelService.onIncomingDamage(damageEvent),
                "outgoing living damage should be handled during the loss penalty");
        helper.assertTrue(damageEvent.isCanceled() && damageEvent.getAmount() == 0.0F,
                "outgoing living damage should be reduced to zero during the loss penalty");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void authorizedFinisherKillsPostDuelDownedVillager(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        VillagerDownedService.enterDowned(
                participant.level(),
                villager,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("duel:test")));
        DuelService.authorizeFinisherForTest(
                villager, player, participant.level().getGameTime() + 1200L);

        villager.invulnerableTime = 0;
        villager.hurt(participant.level().damageSources().playerAttack(player), 10.0F);

        helper.assertTrue(villager.isDeadOrDying() || villager.isRemoved(),
                "the authorized duel opponent should be able to finish the downed villager");
        helper.assertFalse(VillagerDownedService.isDowned(villager),
                "a lethal authorized finisher should release the downed state");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void bringYourOwnRequiresConfig(GameTestHelper helper) {
        boolean previous = VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.get();
        try {
            VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.set(false);
            Participant participant = participant(helper);
            DuelService.StartResult start = DuelService.start(
                    participant.player(), participant.villager(), DuelLoadout.BRING_YOUR_OWN, 0);
            helper.assertFalse(start.started(), "BYO duel must not start while disabled");
            helper.assertValueEqual(start.reason(), DuelAvailabilityReason.LOADOUT_DISABLED,
                    "disabled BYO duel rejection reason");
            helper.succeed();
        } finally {
            VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.set(previous);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void bringYourOwnMutationsPersistWithoutSnapshotDuplication(GameTestHelper helper) {
        boolean previous = VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.get();
        try {
            VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.set(true);
            Participant participant = participant(helper);
            ServerPlayer player = participant.player();
            player.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));

            DuelService.StartResult start = DuelService.start(
                    player, participant.villager(), DuelLoadout.BRING_YOUR_OWN, 0);
            helper.assertTrue(start.started(), "BYO duel should start when configured: " + start.reason());
            helper.assertTrue(DuelService.allowsInventoryClick(
                            player, player.inventoryMenu, InventoryMenu.USE_ROW_SLOT_START, ClickType.PICKUP),
                    "BYO players should be able to move gear they brought");
            helper.assertTrue(!DuelService.allowsInventoryClick(
                            player, player.inventoryMenu, 1, ClickType.PICKUP),
                    "crafting slots must remain locked during BYO duels");
            helper.assertTrue(!DuelService.allowsInventoryClick(
                            player, player.inventoryMenu, InventoryMenu.USE_ROW_SLOT_START, ClickType.THROW),
                    "BYO players must not drop items");

            player.getInventory().getItem(0).shrink(1);
            helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW), "BYO duel should resolve");
            helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 2,
                    "consumed BYO items must stay consumed instead of being restored and duplicated");
            helper.succeed();
        } finally {
            VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.set(previous);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void payoutSettlesExactlyOnce(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        ItemStack currency = VillagerCurrencyResources.createStack(participant.level().getServer(), 8);
        helper.assertTrue(!currency.isEmpty() && currency.getCount() == 8, "test currency stack must resolve");
        player.getInventory().add(currency);
        int walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        if (walletBefore < 8) {
            VillagerWalletService.addCurrency(
                    villager, 8 - walletBefore, VillagerWalletService.WalletSource.DUEL);
            walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        }

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 8);
        helper.assertTrue(start.started(), "staked duel should start: " + start.reason());
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 0,
                "player stake must leave the inventory before snapshotting");
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.PLAYER_WIN),
                "winning duel should resolve");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 16,
                "winner should receive the two-stake pot exactly once");
        helper.assertTrue(!DuelService.resolveForTest(player, DuelResult.PLAYER_WIN),
                "completed duel must reject a second settlement");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 16,
                "replayed completion must not duplicate payout");
        if (!VillagerWalletService.hasUnlimitedCurrency()) {
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBefore - 8,
                    "losing villager stake should remain deducted");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "duel_debug_command")
    public static void debugCommandUsesNameAndOptionalDefaults(GameTestHelper helper) throws CommandSyntaxException {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        villager.setCustomName(Component.literal("Debug Duelist"));
        VillagerProfileManager.setAttribute(
                participant.level(), villager, VillagerSocialAttribute.GUTS, 1);
        var dispatcher = participant.level().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2);

        int defaultResult = dispatcher.execute(
                "villagerretaliation debug duel \"Debug Duelist\"", source);
        helper.assertValueEqual(defaultResult, 1,
                "debug duel command should resolve a quoted villager name");
        helper.assertTrue(DuelService.isParticipant(player),
                "default debug command should start a live duel");
        helper.assertTrue(DuelService.allowsInventoryClick(
                        player, player.inventoryMenu, InventoryMenu.USE_ROW_SLOT_START, ClickType.PICKUP),
                "no-option debug duel must default to BYO gear");
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.CANCELLED),
                "default debug duel should cleanly resolve");

        player.getInventory().add(
                VillagerCurrencyResources.createStack(participant.level().getServer(), 8));
        int wallet = VillagerWalletService.getCurrentEmeralds(villager);
        if (wallet < 8) {
            VillagerWalletService.addCurrency(
                    villager, 8 - wallet, VillagerWalletService.WalletSource.DUEL);
        }

        int configuredResult = dispatcher.execute(
                "villagerretaliation debug duel \"Debug Duelist\" kit armored wager 8", source);
        helper.assertValueEqual(configuredResult, 1,
                "debug command should accept optional kit and wager entries");
        helper.assertTrue(!DuelService.allowsInventoryClick(
                        player, player.inventoryMenu, InventoryMenu.USE_ROW_SLOT_START, ClickType.PICKUP),
                "armored command kit must use assigned-gear locking");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 0,
                "configured wager must be deducted");
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW),
                "configured debug duel should resolve");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 8,
                "draw must refund the configured wager");
        helper.succeed();
    }
    @GameTest(template = EMPTY_TEMPLATE)
    public static void logoutRestoresAssignedSnapshot(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        player.getInventory().setItem(2, new ItemStack(Items.GOLD_INGOT, 5));
        DuelService.StartResult start = DuelService.start(
                player, participant.villager(), DuelLoadout.ARMORED, 0);
        helper.assertTrue(start.started(), "logout duel should start: " + start.reason());
        player.getInventory().add(new ItemStack(Items.DIAMOND_BLOCK, 4));

        DuelService.onPlayerLogout(player);
        helper.assertTrue(!DuelService.isParticipant(player), "logout must remove active duel state");
        helper.assertValueEqual(player.getInventory().countItem(Items.GOLD_INGOT), 5,
                "logout must restore the disconnected player's snapshot");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND_BLOCK), 0,
                "logout restoration must discard duel-only inventory");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void logoutWithMissingVillagerCancelsAndPreservesEscrow(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().add(
                VillagerCurrencyResources.createStack(participant.level().getServer(), 8));
        int walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        if (walletBefore < 8) {
            VillagerWalletService.addCurrency(
                    villager, 8 - walletBefore, VillagerWalletService.WalletSource.DUEL);
            walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        }

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 8);
        helper.assertTrue(start.started(), "missing-villager logout duel should start: " + start.reason());
        villager.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        DuelService.onPlayerLogout(player);

        helper.assertFalse(DuelService.isParticipant(player),
                "logout must remove the duel even when the villager is unresolved");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 8,
                "an unresolved winner must cancel and refund the player's escrow");
        helper.assertFalse(DuelService.isPostLossAttackLocked(player),
                "a canceled settlement must not apply the duel-loss penalty");
        if (!VillagerWalletService.hasUnlimitedCurrency()) {
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBefore - 8,
                    "the unresolved villager refund should remain pending");
        }
        helper.assertTrue(DuelService.recoverPendingVillager(villager),
                "the returning villager should recover its pending escrow");
        if (!VillagerWalletService.hasUnlimitedCurrency()) {
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBefore,
                    "the returning villager should receive its original escrow");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 340)
    public static void timeoutEndsInDrawAndRestoresEquipment(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().setItem(1, new ItemStack(Items.CARROT, 6));
        villager.setNoAi(true);
        villager.moveTo(player.getX() + 7.0D, player.getY(), player.getZ(), 0.0F, 0.0F);

        int previousTimeout = VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.get();
        DuelService.StartResult start;
        try {
            VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.set(200);
            start = DuelService.start(player, villager, DuelLoadout.BARE_HANDED, 0);
        } finally {
            VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.set(previousTimeout);
        }
        helper.assertTrue(start.started(), "timeout duel should start: " + start.reason());
        UUID duelId = start.duelId();

        helper.runAfterDelay(280, () -> {
            try {
                helper.assertTrue(!DuelService.isParticipant(player), "timeout must finish the live duel");
                helper.assertValueEqual(player.getInventory().countItem(Items.CARROT), 6,
                        "timeout must restore assigned inventory");
                helper.assertTrue(DuelSavedData.get(participant.level()).history().stream()
                                .anyMatch(memory -> memory.id().equals(duelId)
                                        && memory.result() == DuelResult.DRAW),
                        "timeout must record a draw");
                helper.succeed();
            } finally {
                DuelService.resolveForTest(player, DuelResult.CANCELLED);
            }
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void boundaryForfeitAwardsVillagerWin(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().setItem(0, new ItemStack(Items.POTATO, 4));

        int previousRadius = VillagerRetaliationConfig.DUEL_ARENA_RADIUS.get();
        int previousGrace = VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.get();
        DuelService.StartResult start;
        try {
            VillagerRetaliationConfig.DUEL_ARENA_RADIUS.set(8);
            VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.set(0);
            start = DuelService.start(player, villager, DuelLoadout.BARE_HANDED, 0);
        } finally {
            VillagerRetaliationConfig.DUEL_ARENA_RADIUS.set(previousRadius);
            VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.set(previousGrace);
        }
        helper.assertTrue(start.started(), "boundary duel should start: " + start.reason());
        player.moveTo(player.getX() + 24.0D, player.getY(), player.getZ(), 0.0F, 0.0F);

        helper.runAfterDelay(90, () -> {
            try {
                helper.assertTrue(!DuelService.isParticipant(player), "zero-grace boundary exit must forfeit");
                DuelSavedData.DuelRecord record = DuelSavedData.get(participant.level())
                        .record(villager.getUUID(), player.getUUID());
                helper.assertValueEqual(record.villagerWins(), 1,
                        "player boundary forfeit must award the villager win");
                helper.assertValueEqual(player.getInventory().countItem(Items.POTATO), 4,
                        "boundary resolution must restore assigned inventory");
                helper.succeed();
            } finally {
                DuelService.resolveForTest(player, DuelResult.CANCELLED);
            }
        });
    }


    @GameTest(template = EMPTY_TEMPLATE)
    public static void crashRecoveryRestoresAssignedSnapshotsAndEscrow(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().setItem(4, new ItemStack(Items.GOLD_INGOT, 5));
        player.getInventory().selected = 4;
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.BREAD, 2));

        player.getInventory().add(VillagerCurrencyResources.createStack(participant.level().getServer(), 8));
        int walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        if (walletBefore < 8) {
            VillagerWalletService.addCurrency(
                    villager, 8 - walletBefore, VillagerWalletService.WalletSource.DUEL);
            walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        }

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.ARMORED, 8);
        helper.assertTrue(start.started(), "recovery duel should start: " + start.reason());
        player.getInventory().add(new ItemStack(Items.DIAMOND_BLOCK, 4));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.EMERALD_BLOCK));

        DuelService.forgetRuntimeStateForTest(player);
        helper.assertTrue(DuelService.recoverPendingPlayer(player),
                "orphaned player snapshot should recover after runtime state is lost");
        helper.assertTrue(DuelService.recoverPendingVillager(villager),
                "orphaned villager snapshot should recover after runtime state is lost");
        helper.assertValueEqual(player.getInventory().countItem(Items.GOLD_INGOT), 5,
                "crash recovery must restore the original player inventory");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND_BLOCK), 0,
                "crash recovery must discard temporary duel inventory");
        helper.assertValueEqual(player.getInventory().selected, 4,
                "crash recovery must restore the selected slot");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 8,
                "crash recovery must refund the player's escrow");
        helper.assertTrue(villager.getMainHandItem().is(Items.DIAMOND_SWORD),
                "crash recovery must restore the villager's original equipment");
        helper.assertValueEqual(VillagerInventoryAccess.captureFullInventory(villager).stream()
                        .filter(stack -> stack.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum(),
                2, "crash recovery must restore the villager's original inventory");
        helper.assertValueEqual(countVillagerItem(villager, Items.EMERALD_BLOCK), 0,
                "crash recovery must discard temporary villager inventory");
        if (!VillagerWalletService.hasUnlimitedCurrency()) {
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBefore,
                    "crash recovery must refund the villager's escrow");
        }
        helper.assertFalse(DuelService.recoverPendingPlayer(player),
                "player crash recovery must be idempotent");
        helper.assertFalse(DuelService.recoverPendingVillager(villager),
                "villager crash recovery must be idempotent");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void playerCloneKeepsPendingDuelRecovery(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        player.getInventory().setItem(4, new ItemStack(Items.GOLD_INGOT, 5));
        player.getInventory().selected = 4;
        DuelService.StartResult start = DuelService.start(
                player, participant.villager(), DuelLoadout.ARMORED, 0);
        helper.assertTrue(start.started(), "clone recovery duel should start: " + start.reason());

        ServerPlayer replacement = participant(helper).player();
        DuelService.copyPendingPlayerRecovery(player, replacement);
        DuelEquipment.PlayerRecovery copied = DuelEquipment.playerRecovery(replacement);
        try {
            helper.assertTrue(copied != null,
                    "a player clone must retain its pending duel recovery tag");
            helper.assertValueEqual(
                    copied.snapshot().items().get(4).getCount(), 5,
                    "the cloned recovery tag must retain the original inventory snapshot");
            helper.assertTrue(copied.snapshot().items().get(4).is(Items.GOLD_INGOT),
                    "the cloned recovery snapshot must retain item identities");
            helper.succeed();
        } finally {
            DuelEquipment.clearRecovery(replacement, start.duelId());
            DuelService.resolveForTest(player, DuelResult.CANCELLED);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void partialRecoverySnapshotsAreRejected(GameTestHelper helper) {
        Participant participant = participant(helper);

        CompoundTag playerSnapshot = new CompoundTag();
        playerSnapshot.put("Items", new ListTag());
        playerSnapshot.put("Armor", new ListTag());
        playerSnapshot.put("Offhand", new ListTag());
        playerSnapshot.putInt("Selected", 0);
        playerSnapshot.putFloat("Health", 20.0F);
        playerSnapshot.putFloat("Absorption", 0.0F);
        playerSnapshot.put("Food", new CompoundTag());
        playerSnapshot.put("Effects", new ListTag());
        helper.assertTrue(DuelEquipment.PlayerSnapshot.load(
                        participant.player(), playerSnapshot) == null,
                "a truncated player inventory snapshot must be rejected");

        CompoundTag villagerSnapshot = new CompoundTag();
        villagerSnapshot.put("Inventory", new ListTag());
        villagerSnapshot.put("Equipment", new CompoundTag());
        villagerSnapshot.put("EquipmentOwnership", new CompoundTag());
        villagerSnapshot.putFloat("Health", 20.0F);
        villagerSnapshot.putFloat("Absorption", 0.0F);
        villagerSnapshot.put("Effects", new ListTag());
        villagerSnapshot.putBoolean("Pickup", false);
        CompoundTag recovery = new CompoundTag();
        recovery.putInt("Food", 20);
        recovery.putFloat("Saturation", 20.0F);
        recovery.putFloat("Exhaustion", 0.0F);
        recovery.putInt("HealTimer", 0);
        villagerSnapshot.put("Recovery", recovery);
        helper.assertTrue(DuelEquipment.VillagerSnapshot.load(
                        participant.villager(), villagerSnapshot) == null,
                "a truncated villager inventory snapshot must be rejected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void activeDuelistsCannotDamageThirdParties(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        Villager outsider = helper.spawn(EntityType.VILLAGER, 4, 2, 4);
        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 0);
        helper.assertTrue(start.started(), "isolation duel should start: " + start.reason());
        try {
            LivingIncomingDamageEvent outgoing = new LivingIncomingDamageEvent(
                    outsider,
                    new DamageContainer(participant.level().damageSources().playerAttack(player), 4.0F));
            helper.assertTrue(DuelService.onIncomingDamage(outgoing),
                    "outgoing third-party damage should be handled by the duel policy");
            helper.assertTrue(outgoing.isCanceled() && outgoing.getAmount() == 0.0F,
                    "duelists must not damage non-opponents");

            AttackEntityEvent directAttack = new AttackEntityEvent(player, outsider);
            DuelService.onAttackEntity(directAttack);
            helper.assertTrue(directAttack.isCanceled(),
                    "direct attacks against non-opponents must be canceled before damage");

            LivingIncomingDamageEvent incoming = new LivingIncomingDamageEvent(
                    player,
                    new DamageContainer(participant.level().damageSources().mobAttack(outsider), 4.0F));
            helper.assertTrue(DuelService.onIncomingDamage(incoming),
                    "incoming third-party damage should be handled by the duel policy");
            helper.assertTrue(incoming.isCanceled() && incoming.getAmount() == 0.0F,
                    "third parties must not interfere with active duelists");
            helper.succeed();
        } finally {
            DuelService.resolveForTest(player, DuelResult.CANCELLED);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void firstKnockoutResultCannotBeOverwritten(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.BARE_HANDED, 0);
        helper.assertTrue(start.started(), "knockout race duel should start: " + start.reason());
        try {
            villager.setHealth(1.0F);
            player.setHealth(1.0F);
            LivingDamageEvent.Pre villagerKnockout = new LivingDamageEvent.Pre(
                    villager,
                    new DamageContainer(participant.level().damageSources().playerAttack(player), 4.0F));
            helper.assertTrue(DuelService.onFinalDamage(villagerKnockout),
                    "villager knockout should be handled by the duel policy");
            helper.assertValueEqual(DuelService.pendingResultForTest(player), DuelResult.PLAYER_WIN,
                    "the first knockout should decide the result");

            LivingDamageEvent.Pre latePlayerKnockout = new LivingDamageEvent.Pre(
                    player,
                    new DamageContainer(participant.level().damageSources().mobAttack(villager), 4.0F));
            helper.assertTrue(DuelService.onFinalDamage(latePlayerKnockout),
                    "late player knockout should still be made non-lethal");
            helper.assertValueEqual(DuelService.pendingResultForTest(player), DuelResult.PLAYER_WIN,
                    "a later final-damage event must not overwrite the first knockout");
            helper.succeed();
        } finally {
            DuelService.resolveForTest(player, DuelResult.CANCELLED);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void trackedDuelProjectilesAreRemovedOutsideTheArena(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        DuelService.StartResult start = DuelService.start(
                player, participant.villager(), DuelLoadout.RANGED, 0);
        helper.assertTrue(start.started(), "projectile cleanup duel should start: " + start.reason());

        Arrow arrow = new Arrow(
                participant.level(), player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        arrow.moveTo(player.getX() + 60.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        participant.level().addFreshEntity(arrow);
        DuelService.onEntityJoinLevel(new EntityJoinLevelEvent(arrow, participant.level()));
        helper.assertFalse(arrow.isRemoved(), "tracked projectile fixture should be live before resolution");

        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW),
                "projectile cleanup duel should resolve");
        helper.assertTrue(arrow.isRemoved(),
                "tracked duel projectiles must be removed even beyond the former cleanup box");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void missingVillagerRefundsEscrowWhenItReturns(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().add(VillagerCurrencyResources.createStack(participant.level().getServer(), 8));
        int walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        if (walletBefore < 8) {
            VillagerWalletService.addCurrency(
                    villager, 8 - walletBefore, VillagerWalletService.WalletSource.DUEL);
            walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        }

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 8);
        helper.assertTrue(start.started(), "missing-villager duel should start: " + start.reason());
        villager.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.CANCELLED),
                "duel should cancel while the villager is unresolved");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 8,
                "the resolved player should receive their own refund");
        if (!VillagerWalletService.hasUnlimitedCurrency()) {
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBefore - 8,
                    "the unresolved villager refund should remain pending");
        }

        helper.assertTrue(DuelService.recoverPendingVillager(villager),
                "the returning villager should consume its pending recovery");
        if (!VillagerWalletService.hasUnlimitedCurrency()) {
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBefore,
                    "the returning villager should receive its escrow refund");
        }
        helper.assertFalse(DuelService.recoverPendingVillager(villager),
                "the deferred villager refund must settle exactly once");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void spectatorsAreExcludedFromGeneralClearanceAndRemainRewardEligible(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager spectator = helper.spawn(EntityType.VILLAGER, 4, 2, 4);
        DuelService.StartResult start = DuelService.start(
                player, participant.villager(), DuelLoadout.BARE_HANDED, 0);
        helper.assertTrue(start.started(), "spectator clearance duel should start: " + start.reason());
        try {
            DuelService.addSpectatorForTest(player, spectator);
            spectator.setTarget(player);
            spectator.setAggressive(true);
            DuelService.onVillagerTickPost(spectator);
            helper.assertTrue(spectator.getTarget() == player && spectator.isAggressive(),
                    "general arena clearance must not overwrite recruited spectator state");

            int previousRadius = VillagerRetaliationConfig.DUEL_SPECTATOR_RADIUS.get();
            try {
                VillagerRetaliationConfig.DUEL_SPECTATOR_RADIUS.set(8);
                helper.assertTrue(DuelSpectators.rewardRadiusSqr(128) >= 133.0D * 133.0D,
                        "maintained spectators must remain reward-eligible when the arena exceeds the search radius");
            } finally {
                VillagerRetaliationConfig.DUEL_SPECTATOR_RADIUS.set(previousRadius);
            }
            helper.succeed();
        } finally {
            DuelService.resolveForTest(player, DuelResult.CANCELLED);
        }
    }

    private static Participant participant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), "duel-" + UUID.randomUUID().toString().substring(0, 8));
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        BlockPos playerPos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.moveTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D, 0.0F, 0.0F);
        Villager villager = helper.spawn(EntityType.VILLAGER, 3, 2, 2);
        VillagerProfileManager.setAttribute(level, villager, VillagerSocialAttribute.GUTS, 100);
        return new Participant(level, player, villager);
    }

    private static int countVillagerItem(Villager villager, Item item) {
        int count = VillagerInventoryAccess.captureFullInventory(villager).stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = villager.getItemBySlot(slot);
            if (equipped.is(item)) {
                count += equipped.getCount();
            }
        }
        return count;
    }

    private record Participant(ServerLevel level, ServerPlayer player, Villager villager) {}}
