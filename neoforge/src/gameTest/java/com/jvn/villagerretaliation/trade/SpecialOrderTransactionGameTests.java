package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bank_trade")
@PrefixGameTestTemplate(false)
public final class SpecialOrderTransactionGameTests {
    private static final String ORDERS = "VillagerRetaliationSpecialOrders";
    private static final String REFUNDS = "VillagerRetaliationSpecialOrderRefunds";
    private static final ResourceLocation DEFINITION = ResourceLocation.parse("bank_trade:prepaid_clock");

    @GameTest(template = "empty")
    public static void queuedPaymentAndCanceledRefundPreserveEveryComponentAfterReload(GameTestHelper h) {
        withOrdersEnabled(() -> {
            ServerPlayer owner = player(h);
            Villager original = villager(h, owner);
            List<ItemStack> payment = List.of(new ItemStack(Items.GOLD_INGOT), namedGold("Deposit A", 1), namedGold("Deposit B", 1));
            owner.getInventory().setItem(0, payment.get(0).copy());
            owner.getInventory().setItem(1, payment.get(1).copy());
            owner.getInventory().offhand.set(0, payment.get(2).copy());
            h.assertTrue(VillagerSpecialOrderService.queue(h.getLevel(), original, owner, 0, DEFINITION).queued(), "real request queues with split payment");
            h.assertValueEqual(owner.getInventory().countItem(Items.GOLD_INGOT), 0, "main inventory payment is consumed");
            h.assertTrue(owner.getOffhandItem().isEmpty(), "offhand payment is consumed");
            ListTag paid = original.getPersistentData().getList(ORDERS, 10).getCompound(0).getList("PaidStacks", 10);
            h.assertValueEqual(paid.size(), 3, "each distinct paid stack is escrowed");
            for (int i = 0; i < paid.size(); i++) h.assertTrue(ItemStack.matches(payment.get(i), ItemStack.parseOptional(h.getLevel().registryAccess(), paid.getCompound(i))), "escrow retains exact stack " + i);

            Villager loaded = reload(h, original);
            loaded.getPersistentData().getList(ORDERS, 10).getCompound(0).putString("TradeDefinition", "missing_pack:removed");
            h.assertTrue(VillagerSpecialOrderService.reconcile(h.getLevel(), loaded), "removed definition cancels persisted request");
            h.assertFalse(VillagerSpecialOrderService.reconcile(h.getLevel(), loaded), "repeat cancellation is harmless");
            loaded = reload(h, loaded);
            h.assertValueEqual(loaded.getPersistentData().getList(REFUNDS, 10).size(), 3, "exact refund claims survive another reload");
            h.assertFalse(VillagerSpecialOrderService.deliverRefunds(player(h), loaded), "another player cannot claim refund");
            h.assertTrue(VillagerSpecialOrderService.deliverRefunds(owner, loaded), "owner receives all escrowed stacks");
            for (ItemStack expected : payment) h.assertValueEqual(owner.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameComponents(expected, stack)).mapToInt(ItemStack::getCount).sum(), 1, "refund preserves name and custom NBT identity");
            h.assertFalse(VillagerSpecialOrderService.deliverRefunds(owner, loaded), "repeated refund cannot mint items");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedAndRepeatedRequestsNeverChargeOrOverwriteReservedSlots(GameTestHelper h) {
        withOrdersEnabled(() -> {
            ServerPlayer owner = player(h);
            Villager villager = villager(h, owner);
            owner.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
            var insufficient = VillagerSpecialOrderService.queue(h.getLevel(), villager, owner, 0, DEFINITION);
            h.assertFalse(insufficient.queued(), "insufficient payment fails");
            h.assertValueEqual(insufficient.messageKey(), "trade_refresh.special_order_payment_missing", "failure identifies the missing cost");
            h.assertValueEqual(owner.getInventory().countItem(Items.GOLD_INGOT), 2, "failed payment removes nothing");
            owner.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 6));
            h.assertFalse(VillagerSpecialOrderService.queue(h.getLevel(), villager, owner, -1, DEFINITION).queued(), "negative slot is rejected");
            h.assertFalse(VillagerSpecialOrderService.queue(h.getLevel(), villager, owner, 99, DEFINITION).queued(), "missing slot is rejected before payment");
            h.assertValueEqual(owner.getInventory().countItem(Items.GOLD_INGOT), 6, "invalid slots remove nothing");
            h.assertTrue(VillagerSpecialOrderService.queue(h.getLevel(), villager, owner, 0, DEFINITION).queued(), "valid request charges once");
            ServerPlayer second = player(h);
            VillagerReputationManager.setReputation(h.getLevel(), villager, second.getUUID(), VillagerRetaliationConfig.ROYALTY_THRESHOLD.get());
            second.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 6));
            h.assertFalse(VillagerSpecialOrderService.queue(h.getLevel(), villager, second, 0, ResourceLocation.parse("bank_trade:prepaid_compass")).queued(), "another player cannot reserve the same slot for another definition");
            h.assertFalse(VillagerSpecialOrderService.queue(h.getLevel(), villager, owner, 0, DEFINITION).queued(), "repeat request fails");
            h.assertValueEqual(owner.getInventory().countItem(Items.GOLD_INGOT), 3, "repeated request keeps remaining payment");
            h.assertValueEqual(second.getInventory().countItem(Items.GOLD_INGOT), 6, "competing request keeps payment");
            h.assertValueEqual(villager.getPersistentData().getList(ORDERS, 10).size(), 1, "only one order owns the slot");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void appliedOrderConsumesEscrowOnceWithoutRefund(GameTestHelper h) {
        withOrdersEnabled(() -> {
            ServerPlayer owner = player(h);
            Villager original = villager(h, owner);
            owner.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
            h.assertTrue(VillagerSpecialOrderService.queue(h.getLevel(), original, owner, 0, DEFINITION).queued(), "queue succeeds");
            Villager loaded = reload(h, original);
            loaded.getPersistentData().getList(ORDERS, 10).getCompound(0).putLong("ReadyDay", 0);
            h.assertTrue(VillagerSpecialOrderService.applyReadyOrders(h.getLevel(), loaded, owner), "ready persisted order is applied");
            h.assertTrue(loaded.getOffers().get(0).getResult().is(Items.CLOCK), "paid-for replacement reaches the requested slot");
            h.assertFalse(VillagerSpecialOrderService.applyReadyOrders(h.getLevel(), loaded, owner), "repeat apply changes nothing");
            h.assertFalse(VillagerSpecialOrderService.deliverRefunds(owner, loaded), "successful request never refunds its payment");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pendingRandomRefreshAlsoProtectsSlotFromPrepaidReplacement(GameTestHelper h) {
        withOrdersEnabled(() -> {
            ServerPlayer owner = player(h);
            Villager villager = villager(h, owner);
            owner.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
            CompoundTag refresh = new CompoundTag();
            refresh.putInt("OfferIndex", 0);
            refresh.putString("TradeDefinition", DEFINITION.toString());
            ListTag pending = new ListTag();
            pending.add(refresh);
            villager.getPersistentData().put("VillagerRetaliationPendingTradeRefreshes", pending);
            var result = VillagerSpecialOrderService.queue(h.getLevel(), villager, owner, 0, ResourceLocation.parse("bank_trade:prepaid_compass"));
            h.assertFalse(result.queued(), "random refresh and prepaid order cannot own the same slot");
            h.assertValueEqual(result.messageKey(), "trade_refresh.special_order_pending", "failure explains pending slot");
            h.assertValueEqual(owner.getInventory().countItem(Items.GOLD_INGOT), 3, "conflicting request takes no payment");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void temporarilyMissingRefundItemKeepsPersistedClaim(GameTestHelper h) {
        ServerPlayer owner = player(h);
        Villager villager = villager(h, owner);
        CompoundTag missingStack = (CompoundTag) namedGold("Waiting", 3).save(h.getLevel().registryAccess());
        missingStack.putString("id", "missing_pack:coin");
        CompoundTag claim = new CompoundTag();
        claim.putUUID("Player", owner.getUUID());
        claim.put("Stack", missingStack);
        ListTag claims = new ListTag();
        claims.add(claim);
        villager.getPersistentData().put(REFUNDS, claims);
        h.assertFalse(VillagerSpecialOrderService.deliverRefunds(owner, villager), "unavailable refund item cannot be delivered");
        h.assertValueEqual(villager.getPersistentData().getList(REFUNDS, 10), claims, "failed decoding preserves the complete persisted claim");
        h.assertValueEqual(reload(h, villager).getPersistentData().getList(REFUNDS, 10), claims, "unavailable claim also survives entity reload");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void partialRefundWithCanceledDropRetainsExactRemainder(GameTestHelper h) {
        ServerPlayer owner = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "RefundRetry")) {
            @Override public ItemEntity drop(ItemStack stack, boolean random) { return null; }
        };
        for (int slot = 0; slot < owner.getInventory().items.size(); slot++) owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        owner.getInventory().setItem(0, namedGold("Escrow", 63));
        Villager villager = villager(h, owner);
        CompoundTag claim = new CompoundTag();
        claim.putUUID("Player", owner.getUUID());
        claim.put("Stack", namedGold("Escrow", 3).save(h.getLevel().registryAccess()));
        ListTag claims = new ListTag();
        claims.add(claim);
        villager.getPersistentData().put(REFUNDS, claims);
        h.assertTrue(VillagerSpecialOrderService.deliverRefunds(owner, villager), "one item fits before a canceled drop");
        h.assertValueEqual(owner.getInventory().getItem(0).getCount(), 64, "only available capacity is credited");
        h.assertFalse(VillagerSpecialOrderService.deliverRefunds(owner, villager), "repeated failed delivery credits nothing");
        Villager loaded = reload(h, villager);
        ItemStack retry = ItemStack.parseOptional(h.getLevel().registryAccess(), loaded.getPersistentData().getList(REFUNDS, 10).getCompound(0).getCompound("Stack"));
        h.assertTrue(ItemStack.matches(retry, namedGold("Escrow", 2)), "persisted retry retains only exact undelivered remainder");
        owner.getInventory().setItem(1, ItemStack.EMPTY);
        h.assertTrue(VillagerSpecialOrderService.deliverRefunds(owner, loaded), "making room delivers remaining claim");
        h.assertValueEqual(owner.getInventory().countItem(Items.GOLD_INGOT), 66, "refund is conserved across partial failure and reload");
        h.assertFalse(VillagerSpecialOrderService.deliverRefunds(owner, loaded), "retry claim is consumed once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refundDropCanceledAtWorldSpawnKeepsClaim(GameTestHelper h) {
        ServerPlayer owner = player(h);
        for (int slot = 0; slot < owner.getInventory().items.size(); slot++) owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        Villager villager = villager(h, owner);
        CompoundTag claim = new CompoundTag();
        claim.putUUID("Player", owner.getUUID());
        claim.put("Stack", namedGold("Canceled spawn", 3).save(h.getLevel().registryAccess()));
        ListTag claims = new ListTag();
        claims.add(claim);
        villager.getPersistentData().put(REFUNDS, claims);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof ItemEntity item && ItemStack.isSameItemSameComponents(item.getItem(), namedGold("Canceled spawn", 1))) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        try {
            h.assertFalse(VillagerSpecialOrderService.deliverRefunds(owner, villager), "nonnull drop rejected by world spawn is undelivered");
            h.assertValueEqual(villager.getPersistentData().getList(REFUNDS, 10), claims, "canceled world spawn keeps full exact claim");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        owner.getInventory().setItem(0, ItemStack.EMPTY);
        h.assertTrue(VillagerSpecialOrderService.deliverRefunds(owner, villager), "claim is retryable after spawn cancellation");
        h.assertValueEqual(owner.getInventory().countItem(Items.GOLD_INGOT), 3, "retry pays once after canceled spawn");
        h.succeed();
    }

    private static ItemStack namedGold(String name, int count) {
        ItemStack stack = new ItemStack(Items.GOLD_INGOT, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CompoundTag custom = new CompoundTag();
        custom.putString("receipt", name);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return stack;
    }

    private static ServerPlayer player(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "OrderOwner"));
    }

    private static Villager villager(GameTestHelper h, ServerPlayer player) {
        Villager villager = EntityType.VILLAGER.create(h.getLevel());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(5));
        villager.getOffers().clear();
        villager.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD), 12, 1, 0.05F));
        VillagerProfileManager.setSkill(h.getLevel(), villager, VillagerSkill.FARMING, 100);
        VillagerReputationManager.setReputation(h.getLevel(), villager, player.getUUID(), VillagerRetaliationConfig.ROYALTY_THRESHOLD.get());
        return villager;
    }

    private static Villager reload(GameTestHelper h, Villager source) {
        Villager loaded = EntityType.VILLAGER.create(h.getLevel());
        loaded.load(source.saveWithoutId(new CompoundTag()));
        return loaded;
    }

    private static void withOrdersEnabled(Runnable run) {
        boolean overhaul = VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get();
        boolean orders = VillagerRetaliationConfig.ENABLE_SPECIAL_ORDERS.get();
        boolean cost = VillagerRetaliationConfig.SPECIAL_ORDER_EXTRA_COST_ENABLED.get();
        try {
            VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.set(true);
            VillagerRetaliationConfig.ENABLE_SPECIAL_ORDERS.set(true);
            VillagerRetaliationConfig.SPECIAL_ORDER_EXTRA_COST_ENABLED.set(true);
            run.run();
        } finally {
            VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.set(overhaul);
            VillagerRetaliationConfig.ENABLE_SPECIAL_ORDERS.set(orders);
            VillagerRetaliationConfig.SPECIAL_ORDER_EXTRA_COST_ENABLED.set(cost);
        }
    }
}
