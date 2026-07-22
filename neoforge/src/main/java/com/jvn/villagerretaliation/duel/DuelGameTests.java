package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.combat.VillagerCombatRoles;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.TriState;
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
                record.villagerWins(), record.villagerLosses()));
        data.acknowledgeStory(speaker, player, eventId);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        DuelSavedData loaded = DuelSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.record(villager, player), record, "duel record did not survive NBT");
        helper.assertValueEqual(loaded.history().size(), 1, "duel history did not survive NBT");
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
                    DuelResult.DRAW, 0, index, BlockPos.ZERO.asLong(), village, 0, 0));
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

        helper.runAfterDelay(10, () -> {
            try {
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
    public static void bringYourOwnMutationsPersistWithoutSnapshotDuplication(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        player.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));

        DuelService.StartResult start = DuelService.start(
                player, participant.villager(), DuelLoadout.BRING_YOUR_OWN, 0);
        helper.assertTrue(start.started(), "BYO duel should start: " + start.reason());
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

    private record Participant(ServerLevel level, ServerPlayer player, Villager villager) {}}
