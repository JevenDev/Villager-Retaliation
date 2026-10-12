package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.combat.ClericHealingService.Reason;
import com.jvn.villagerretaliation.combat.ClericHealingService.Source;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.function.Consumer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("cleric_healing")
@PrefixGameTestTemplate(false)
public final class ClericHealingGameTests {
    private ClericHealingGameTests() {}

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void reputationControlsFreePaidAndRefusedHealing(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper)) {
            for (int reputation : new int[]{0, VillagerRetaliationConfig.TRUSTED_THRESHOLD.get()}) {
                VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), reputation);
                helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).source(), Source.PAID, "neutral and trusted healing is paid");
                helper.assertFalse(VillagerClericPotionHelper.isSupportTarget(f.cleric(), f.player(), 0.6F, true), "automatic healing needs respected reputation");
            }
            VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), VillagerRetaliationConfig.SUSPICIOUS_THRESHOLD.get());
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).reason(), Reason.REFUSED, "negative reputation refuses healing");
            VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), VillagerRetaliationConfig.RESPECTED_THRESHOLD.get());
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).source(), Source.FREE, "respected healing starts free");
            helper.assertTrue(VillagerClericPotionHelper.isSupportTarget(f.cleric(), f.player(), 0.6F, true), "injured respected players are automatic support targets");
            var minimum = VillagerRetaliationConfig.CLERIC_FREE_HEALING_MIN_REPUTATION.get();
            try {
                VillagerRetaliationConfig.CLERIC_FREE_HEALING_MIN_REPUTATION.set(com.jvn.villagerretaliation.reputation.VillagerReputationLevel.HOSTILE);
                VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), VillagerRetaliationConfig.SUSPICIOUS_THRESHOLD.get());
                helper.assertFalse(ClericHealingService.canAutomaticallyHeal(f.cleric(), f.player()), "configured free tier cannot bypass negative reputation refusal");
            } finally {
                VillagerRetaliationConfig.CLERIC_FREE_HEALING_MIN_REPUTATION.set(minimum);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void automaticHealingConsumesRequestedAllowanceAndPaidRepeatsStayAvailable(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper)) {
            int before = VillagerCurrencyPayment.count(f.player());
            helper.assertTrue(ClericHealingService.tryAutomaticHeal(f.cleric(), f.player()), "automatic free potion launches");
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before, "automatic healing never charges");
            helper.assertFalse(ClericHealingService.canAutomaticallyHeal(f.cleric(), f.player()), "automatic healing cannot repeat the free heal");
            long freeRemaining = ClericHealingService.freeCooldown(f.cleric(), f.player());
            for (int i = 0; i < 2; i++) {
                var offer = ClericHealingService.offer(f.cleric(), f.player());
                helper.assertValueEqual(offer.source(), Source.PAID, "next requested heal is paid");
                helper.assertTrue(offer.available(), "paid healing bypasses the ordinary throw delay");
                helper.assertTrue(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), offer), "paid potion launches");
            }
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before - 2 * VillagerRetaliationConfig.CLERIC_HEALING_PRICE.get(), "each paid heal charges exactly once");
            helper.assertValueEqual(ClericHealingService.freeCooldown(f.cleric(), f.player()), freeRemaining, "paid healing does not restart the free allowance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void requestedFreeHealingSharesAutomaticAllowanceAndPersists(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper)) {
            helper.assertTrue(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), ClericHealingService.offer(f.cleric(), f.player())), "requested free potion launches");
            helper.assertFalse(ClericHealingService.canAutomaticallyHeal(f.cleric(), f.player()), "requested heal prevents another automatic free heal");
            CompoundTag saved = new CompoundTag();
            f.cleric().saveWithoutId(saved);
            Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
            loaded.load(saved);
            helper.assertValueEqual(ClericHealingService.freeCooldown(loaded, f.player()),
                    (long) VillagerRetaliationConfig.CLERIC_FREE_HEALING_INTERVAL_TICKS.get(), "free allowance survives entity serialization");
            helper.assertTrue(ClericHealingService.throwCooldown(loaded) > 0, "throw delay also survives entity serialization");
            var memory = f.cleric().getPersistentData().getCompound("VillagerRetaliationClericHealing");
            memory.getCompound("PlayerFreeThrows").putLong(f.player().getUUID().toString(),
                    ClericHealingService.gameTime(f.cleric()) - VillagerRetaliationConfig.CLERIC_FREE_HEALING_INTERVAL_TICKS.get());
            ClericHealingFixtures.expireThrowDelay(f.cleric());
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).source(), Source.FREE, "free healing returns exactly at its reset boundary");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void ordinaryAllowancesAreIndependentAcrossPlayersAndClerics(GameTestHelper helper) {
        try (var first = ClericHealingFixtures.create(helper); var second = ClericHealingFixtures.create(helper)) {
            helper.assertTrue(ClericHealingService.tryAutomaticHeal(first.cleric(), first.player()), "first player uses first cleric allowance");
            helper.assertValueEqual(ClericHealingService.freeCooldown(first.cleric(), second.player()), 0L, "another player retains their allowance");
            helper.assertValueEqual(ClericHealingService.freeCooldown(second.cleric(), first.player()), 0L, "another cleric retains their allowance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void fullHealthMissingPaymentAndBlockedSightSpendNothing(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper)) {
            var original = ClericHealingService.offer(f.cleric(), f.player());
            f.player().setHealth(f.player().getMaxHealth());
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).reason(), Reason.FULL_HEALTH, "healthy players cannot purchase healing");
            helper.assertFalse(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), original), "stale injured offer cannot heal full health");
            f.player().setHealth(19.0F);
            helper.assertTrue(ClericHealingService.offer(f.cleric(), f.player()).available(), "requested healing accepts even a small injury");
            var block = f.cleric().blockPosition().east();
            helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(block.above(), Blocks.STONE.defaultBlockState());
            helper.assertFalse(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), original), "blocked sight prevents throwing");
            helper.assertValueEqual(ClericHealingService.freeCooldown(f.cleric(), f.player()), 0L, "blocked throw preserves free allowance");
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(block.above(), Blocks.AIR.defaultBlockState());
            VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), 0);
            var paid = ClericHealingService.offer(f.cleric(), f.player());
            f.player().getInventory().clearContent();
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).reason(), Reason.INSUFFICIENT_FUNDS, "missing payment is reported");
            helper.assertFalse(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), paid), "missing payment cannot launch a potion");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void partyClericsGenerateOneSharedHealThenUseSuppliedPotions(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper); var other = ClericHealingFixtures.create(helper)) {
            ClericHealingFixtures.recruit(helper, f);
            VillagerInventoryAccess.addItem(f.cleric(), PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
            int before = VillagerCurrencyPayment.count(f.player());
            helper.assertTrue(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), ClericHealingService.offer(f.cleric(), f.player())), "party cleric generates its first free potion");
            helper.assertTrue(VillagerInventoryAccess.hasCarriedItem(f.cleric(), VillagerRetaliationPotionUtil::isHealingPotion), "generated heal preserves supplies");
            helper.assertValueEqual(ClericHealingService.freeCooldown(f.cleric(), other.player()),
                    (long) VillagerRetaliationConfig.PARTY_CLERIC_FREE_HEALING_INTERVAL_TICKS.get(), "party allowance is shared across recipients");
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), other.player()).reason(), Reason.UNAUTHORIZED, "outsiders cannot request party healing");
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).reason(), Reason.COOLDOWN, "supplied potions retain the three-second throw delay");
            ClericHealingFixtures.expireThrowDelay(f.cleric());
            var supplied = ClericHealingService.offer(f.cleric(), f.player());
            helper.assertValueEqual(supplied.source(), Source.SUPPLIED, "party repeat uses a supplied potion");
            helper.assertTrue(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), supplied), "supplied potion launches");
            helper.assertFalse(VillagerInventoryAccess.hasCarriedItem(f.cleric(), VillagerRetaliationPotionUtil::isHealingPotion), "exactly one supplied potion consumed");
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before, "party healing never charges emeralds");
            helper.assertFalse(ClericHealingService.canAutomaticallyHeal(f.cleric(), f.player()), "no supplies means no extra automatic potion");
            var memory = f.cleric().getPersistentData().getCompound("VillagerRetaliationClericHealing");
            memory.putLong("PartyFreeThrow", ClericHealingService.gameTime(f.cleric()) - VillagerRetaliationConfig.PARTY_CLERIC_FREE_HEALING_INTERVAL_TICKS.get());
            ClericHealingFixtures.expireThrowDelay(f.cleric());
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).source(), Source.FREE, "party free potion returns at five minutes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void cancelledPotionSpawnsRefundPaymentAndSupplies(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper)) {
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getEntity() instanceof ThrownPotion potion && potion.getOwner() == f.cleric()) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), 0);
                int before = VillagerCurrencyPayment.count(f.player());
                helper.assertFalse(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), ClericHealingService.offer(f.cleric(), f.player())), "cancelled paid potion cannot complete");
                helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before, "cancelled paid spawn refunds the exact fee");
                VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), VillagerRetaliationConfig.RESPECTED_THRESHOLD.get());
                helper.assertFalse(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), ClericHealingService.offer(f.cleric(), f.player())), "cancelled free potion cannot complete");
                helper.assertValueEqual(ClericHealingService.freeCooldown(f.cleric(), f.player()), 0L, "cancelled spawn preserves free allowance");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            ClericHealingFixtures.recruit(helper, f);
            helper.assertTrue(ClericHealingService.tryAutomaticHeal(f.cleric(), f.player()), "party automatic heal generates without supplies");
            VillagerInventoryAccess.addItem(f.cleric(), PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
            ClericHealingFixtures.expireThrowDelay(f.cleric());
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                var supplied = ClericHealingService.offer(f.cleric(), f.player());
                helper.assertValueEqual(supplied.source(), Source.SUPPLIED, "repeat selects supplied potion");
                helper.assertFalse(ClericHealingService.tryRequestedHeal(f.cleric(), f.player(), supplied), "cancelled supplied potion cannot complete");
                helper.assertTrue(VillagerInventoryAccess.hasCarriedItem(f.cleric(), VillagerRetaliationPotionUtil::isHealingPotion), "cancelled spawn returns the supplied potion");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(ClericHealingService.tryAutomaticHeal(f.cleric(), f.player()), "automatic party support can use the returned supplied potion");
            helper.assertFalse(VillagerInventoryAccess.hasCarriedItem(f.cleric(), VillagerRetaliationPotionUtil::isHealingPotion), "successful automatic supplied throw consumes the potion");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void freeRequestsRespectCombatThrowDelayWhilePaidRequestsBypassIt(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper)) {
            var zombie = EntityType.ZOMBIE.create(helper.getLevel());
            zombie.setPos(f.cleric().getX() + 5.0D, f.cleric().getY(), f.cleric().getZ());
            helper.assertTrue(VillagerClericPotionHelper.tryCombat(f.cleric(), zombie, helper.getLevel(), 25.0D), "combat potion throws");
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).reason(), Reason.COOLDOWN, "free heal shares the existing combat throw delay");
            VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), 0);
            helper.assertTrue(ClericHealingService.offer(f.cleric(), f.player()).available(), "paid heal bypasses the combat throw delay");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void ordinaryNonPlayerSupportRemainsRepeatable(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper)) {
            var trader = EntityType.WANDERING_TRADER.create(helper.getLevel());
            trader.setPos(f.player().getX(), f.player().getY(), f.player().getZ());
            trader.setHealth(10.0F);
            helper.assertTrue(VillagerClericPotionHelper.isSupportTarget(f.cleric(), trader, 0.6F, true), "injured wandering traders remain support targets");
            helper.assertTrue(ClericHealingService.tryAutomaticHeal(f.cleric(), trader), "ordinary cleric heals an allied trader");
            ClericHealingFixtures.expireThrowDelay(f.cleric());
            helper.assertTrue(ClericHealingService.tryAutomaticHeal(f.cleric(), trader), "non-player allies do not consume the daily player allowance");
            helper.assertValueEqual(ClericHealingService.freeCooldown(f.cleric(), f.player()), 0L, "healing an ally preserves the player's allowance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void reputationDisabledUsesPaidOrdinaryHealingAndFreePartySupport(GameTestHelper helper) {
        boolean previous = VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get();
        try (var f = ClericHealingFixtures.create(helper)) {
            ClericHealingFixtures.recruit(helper, f);
            VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.set(false);
            helper.assertValueEqual(ClericHealingService.offer(f.cleric(), f.player()).source(), Source.FREE, "party allowance ignores reputation setting");
            try (var ordinary = ClericHealingFixtures.create(helper)) {
                helper.assertValueEqual(ClericHealingService.offer(ordinary.cleric(), ordinary.player()).source(), Source.PAID, "ordinary healing is paid when reputation is disabled");
                helper.assertFalse(ClericHealingService.canAutomaticallyHeal(ordinary.cleric(), ordinary.player()), "disabled reputation grants no ordinary automatic free heal");
            }
        } finally {
            VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.set(previous);
        }
        helper.succeed();
    }
}
