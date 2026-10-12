package com.jvn.villagerretaliation.healing;

import com.jvn.villagerretaliation.combat.ClericHealingFixtures;
import com.jvn.villagerretaliation.combat.ClericHealingService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.VillagerHealingRequestPayload;
import com.jvn.villagerretaliation.network.VillagerHealingRequestPayload.Action;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("cleric_healing")
@PrefixGameTestTemplate(false)
public final class ClericHealingRequestGameTests {
    private ClericHealingRequestGameTests() {}

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void quotesAreSingleUseAndHealingKeepsConversationOpen(GameTestHelper helper) {
        try (var f = ClericHealingFixtures.create(helper)) {
            helper.assertTrue(VillagerConversationService.start(f.player(), f.cleric()), "healing conversation starts");
            int before = VillagerCurrencyPayment.count(f.player());
            var open = new VillagerHealingRequestPayload(f.cleric().getId(), Action.OPEN, VillagerHealingRequestPayload.NO_QUOTE);
            var free = ClericHealingRequestHandler.process(f.player(), open).orElseThrow();
            helper.assertValueEqual(free.offer().source(), ClericHealingService.Source.FREE, "initial quote is free");
            var confirm = new VillagerHealingRequestPayload(f.cleric().getId(), Action.CONFIRM, free.quoteId());
            var paid = ClericHealingRequestHandler.process(f.player(), confirm).orElseThrow();
            helper.assertTrue(VillagerConversationService.validate(f.player(), f.cleric()), "successful healing leaves conversation open");
            helper.assertValueEqual(paid.offer().source(), ClericHealingService.Source.PAID, "response immediately quotes next paid heal");
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before, "first heal costs nothing");
            ClericHealingRequestHandler.process(f.player(), confirm);
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before, "duplicate free confirmation cannot buy a paid heal");
            var buy = new VillagerHealingRequestPayload(f.cleric().getId(), Action.CONFIRM, paid.quoteId());
            ClericHealingRequestHandler.process(f.player(), buy);
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before, "ten-tick guard prevents immediate repeated clicks");
            ServerboundRequestLimiter.clear(f.player().getUUID());
            var next = ClericHealingRequestHandler.process(f.player(), buy).orElseThrow();
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before - VillagerRetaliationConfig.CLERIC_HEALING_PRICE.get(), "paid confirmation removes exact price");
            ClericHealingRequestHandler.process(f.player(), buy);
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before - VillagerRetaliationConfig.CLERIC_HEALING_PRICE.get(), "paid quote cannot be consumed twice");
            helper.assertTrue(next.offer().available(), "another paid heal is available despite ordinary throw delay");
            helper.assertTrue(VillagerConversationService.validate(f.player(), f.cleric()), "paid repeat also leaves conversation open");
            ClericHealingRequestHandler.process(f.player(), new VillagerHealingRequestPayload(f.cleric().getId(), Action.CLOSE, next.quoteId()));
            helper.assertTrue(VillagerConversationService.validate(f.player(), f.cleric()), "nevermind returns to root without ending conversation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cleric_healing")
    public static void stalePriceAndOldConversationQuotesCannotCharge(GameTestHelper helper) {
        int originalPrice = VillagerRetaliationConfig.CLERIC_HEALING_PRICE.get();
        try (var f = ClericHealingFixtures.create(helper)) {
            com.jvn.villagerretaliation.reputation.VillagerReputationManager.setReputation(helper.getLevel(), f.cleric(), f.player().getUUID(), 0);
            helper.assertTrue(VillagerConversationService.start(f.player(), f.cleric()), "paid conversation starts");
            var open = new VillagerHealingRequestPayload(f.cleric().getId(), Action.OPEN, VillagerHealingRequestPayload.NO_QUOTE);
            var first = ClericHealingRequestHandler.process(f.player(), open).orElseThrow();
            int before = VillagerCurrencyPayment.count(f.player());
            VillagerRetaliationConfig.CLERIC_HEALING_PRICE.set(originalPrice + 1);
            var changed = ClericHealingRequestHandler.process(f.player(), new VillagerHealingRequestPayload(f.cleric().getId(), Action.CONFIRM, first.quoteId())).orElseThrow();
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before, "changed price is not silently charged");
            helper.assertValueEqual(changed.offer().price(), originalPrice + 1, "new price is returned for review");
            VillagerConversationService.endForPlayer(f.player(), false);
            helper.assertTrue(VillagerConversationService.start(f.player(), f.cleric()), "conversation reopens");
            ClericHealingRequestHandler.process(f.player(), new VillagerHealingRequestPayload(f.cleric().getId(), Action.CONFIRM, changed.quoteId()));
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player()), before, "quote from previous conversation cannot charge");
        } finally {
            VillagerRetaliationConfig.CLERIC_HEALING_PRICE.set(originalPrice);
        }
        helper.succeed();
    }
}
