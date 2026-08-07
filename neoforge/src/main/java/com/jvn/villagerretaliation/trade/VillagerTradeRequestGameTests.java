package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.mojang.authlib.GameProfile;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerTradeRequestGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final ResourceLocation FARMER = BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.FARMER);

    private VillagerTradeRequestGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void weightedCycleExhaustsBeforeRepeatingAndGuardsBoundary(GameTestHelper helper) {
        Villager villager = villager(helper);
        List<SkillTradeDefinition> definitions = List.of(definition("heavy", 100), definition("medium", 5), definition("light", 1));
        Set<ResourceLocation> selected = new HashSet<>();
        ResourceLocation last = null;
        for (int index = 0; index < definitions.size(); index++) {
            List<ResourceLocation> candidates = VillagerTradeMemory.cycleCandidates(
                    helper.getLevel(), villager, FARMER, definitions, Set.of());
            helper.assertTrue(!candidates.isEmpty(), "cycle must expose a candidate before exhaustion");
            ResourceLocation chosen = candidates.getFirst();
            helper.assertTrue(selected.add(chosen), "a definition repeated before every eligible entry was consumed");
            VillagerTradeMemory.consumeDefinition(helper.getLevel(), villager, FARMER, chosen, definitions);
            last = chosen;
        }
        ResourceLocation firstNextCycle = VillagerTradeMemory.cycleCandidates(
                helper.getLevel(), villager, FARMER, definitions, Set.of()).getFirst();
        helper.assertTrue(!firstNextCycle.equals(last), "the last fulfilled definition repeated across the cycle boundary");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void weightedOrderingIsDeterministicAndHonorsWeight(GameTestHelper helper) {
        List<SkillTradeDefinition> definitions = List.of(definition("heavy", 100), definition("light", 1));
        int heavyFirst = 0;
        for (int sample = 0; sample < 32; sample++) {
            Villager villager = villager(helper);
            List<ResourceLocation> first = VillagerTradeMemory.cycleCandidates(
                    helper.getLevel(), villager, FARMER, definitions, Set.of());
            if (first.getFirst().equals(id("heavy"))) heavyFirst++;
        }
        helper.assertTrue(heavyFirst >= 28, "a 100:1 definition should dominate deterministic first-position samples");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void cycleSurvivesEntityDataCopyAndReconcilesPoolChanges(GameTestHelper helper) {
        List<SkillTradeDefinition> initial = List.of(definition("a", 1), definition("b", 1), definition("c", 1));
        Villager original = villager(helper);
        VillagerTradeMemory.consumeDefinition(helper.getLevel(), original, FARMER, id("a"), initial);

        Villager loaded = villager(helper);
        loaded.getPersistentData().merge(original.getPersistentData().copy());
        List<ResourceLocation> continued = VillagerTradeMemory.cycleCandidates(
                helper.getLevel(), loaded, FARMER, initial, Set.of());
        helper.assertTrue(!continued.contains(id("a")), "save/load continuity restored an already consumed entry");

        List<SkillTradeDefinition> changed = List.of(definition("c", 1), definition("d", 1));
        List<ResourceLocation> reconciled = VillagerTradeMemory.cycleCandidates(
                helper.getLevel(), loaded, FARMER, changed, Set.of());
        helper.assertTrue(reconciled.contains(id("d")), "a newly loaded datapack definition did not join the current remainder");
        helper.assertTrue(!reconciled.contains(id("b")), "a removed datapack definition remained in the cycle");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void legacyKnownDefinitionsMigrateAsConsumed(GameTestHelper helper) {
        Villager villager = villager(helper);
        CompoundTag pool = new CompoundTag();
        pool.putString("Profession", FARMER.toString());
        pool.putInt("Version", 1);
        ListTag known = new ListTag();
        known.add(StringTag.valueOf(id("old").toString()));
        pool.put("KnownDefinitions", known);
        ListTag pools = new ListTag();
        pools.add(pool);
        CompoundTag memory = new CompoundTag();
        memory.put("Pools", pools);
        villager.getPersistentData().put("VillagerRetaliationTradeMemory", memory);

        List<SkillTradeDefinition> definitions = List.of(definition("old", 1), definition("new", 1));
        List<ResourceLocation> candidates = VillagerTradeMemory.cycleCandidates(
                helper.getLevel(), villager, FARMER, definitions, Set.of());
        helper.assertValueEqual(candidates, List.of(id("new")), "legacy known ids must migrate as consumed and prioritize new definitions");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void canceledPrepaidOrderRefundPersistsAndDeliversOnceAtOwningVillager(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        Villager original = villager(helper);
        CompoundTag order = new CompoundTag();
        order.putUUID("Player", owner);
        order.putInt("OfferIndex", 99);
        order.putString("TradeDefinition", "missing_pack:removed_trade");
        order.putString("Status", "pending");
        order.putString("PaidItem", "minecraft:emerald");
        order.putInt("PaidCount", 3);
        ListTag orders = new ListTag();
        orders.add(order);
        original.getPersistentData().put("VillagerRetaliationSpecialOrders", orders);
        CompoundTag cooldown = new CompoundTag();
        cooldown.putUUID("Player", owner);
        cooldown.putLong("CooldownEndDay", 9999L);
        ListTag cooldowns = new ListTag();
        cooldowns.add(cooldown);
        original.getPersistentData().put("VillagerRetaliationSpecialOrderCooldowns", cooldowns);

        helper.assertTrue(VillagerSpecialOrderService.reconcile(helper.getLevel(), original),
                "removed or out-of-range queued orders must be canceled during reconciliation");
        helper.assertValueEqual(VillagerSpecialOrderService.activeOrderCount(original, owner), 0,
                "a canceled order must release its active-order reservation immediately");
        helper.assertTrue(!VillagerSpecialOrderService.isOnCooldown(helper.getLevel(), original, owner),
                "a canceled order must release its cooldown reservation");

        Villager loaded = villager(helper);
        loaded.getPersistentData().merge(original.getPersistentData().copy());
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(owner, "TradeRefundOwner"));
        int before = player.getInventory().countItem(Items.EMERALD);
        helper.assertTrue(!VillagerSpecialOrderService.deliverRefunds(player, villager(helper)),
                "a refund must not be claimable from a different villager");
        helper.assertTrue(VillagerSpecialOrderService.deliverRefunds(player, loaded),
                "the owning villager must deliver a persisted refund to its owner");
        helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), before + 3,
                "the complete prepaid amount must be returned");
        helper.assertTrue(!VillagerSpecialOrderService.deliverRefunds(player, loaded),
                "a delivered refund claim must be removed idempotently");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void refundInventoryOverflowDropsSafelyAtPlayer(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        Villager villager = villager(helper);
        CompoundTag refund = new CompoundTag();
        refund.putUUID("Player", owner);
        refund.putString("Item", "minecraft:emerald");
        refund.putInt("Count", 3);
        ListTag refunds = new ListTag();
        refunds.add(refund);
        villager.getPersistentData().put("VillagerRetaliationSpecialOrderRefunds", refunds);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(owner, "FullRefundOwner"));
        player.getInventory().items.forEach(stack -> stack.setCount(64));
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().items.set(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        int beforeDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3.0D)).size();
        helper.assertTrue(VillagerSpecialOrderService.deliverRefunds(player, villager),
                "refund delivery should succeed even when inventory is full");
        int afterDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3.0D)).size();
        helper.assertTrue(afterDrops > beforeDrops, "refund overflow must be dropped safely at the player");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void walletStockSurvivesOfferUseResetAndBlocksStalePayout(GameTestHelper helper) {
        Villager villager = villager(helper);
        int available = VillagerWalletService.getCurrentEmeralds(villager);
        VillagerWalletService.spendCurrency(
                villager, available);
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.COAL, 1),
                new ItemStack(Items.EMERALD, 5),
                12,
                1,
                0.05F);
        villager.getOffers().add(offer);

        VillagerTradeWalletService.refreshWalletStock(helper.getLevel(), villager);
        helper.assertTrue(offer.isOutOfStock(), "an unaffordable currency payout must be out of stock");
        offer.resetUses();
        helper.assertTrue(!offer.isOutOfStock(), "the test reset must emulate vanilla restocking the offer");

        VillagerTradeWalletService.refreshWalletStock(helper.getLevel(), villager);
        helper.assertTrue(offer.isOutOfStock(), "wallet stock must be reapplied after vanilla resets offer uses");
        helper.assertTrue(
                !VillagerTradeWalletService.canCompleteTrade(helper.getLevel(), villager, offer),
                "a stale offer must fail the server-side payout preflight");
        helper.succeed();
    }

    private static Villager villager(GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "villager must be creatable");
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(5));
        return villager;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("villagerretaliation_test", path);
    }

    private static SkillTradeDefinition definition(String path, int weight) {
        return new SkillTradeDefinition(
                id(path),
                Set.of(FARMER),
                Set.of(VillagerSkill.FARMING),
                VillagerSkillRank.NOVICE,
                null,
                1,
                1.0D,
                weight,
                SkillTradeCost.DEFAULT,
                new SkillTradeResult(List.of(Items.BREAD), 1, SkillTradeEnchantments.NONE),
                SkillTradeMaxUses.DEFAULT,
                1,
                0.05F,
                SkillTradeConditions.EMPTY,
                SkillTradeQualityScaling.DISABLED,
                SkillTradeRequestMetadata.NOT_TARGETABLE,
                SkillTradePool.VILLAGER);
    }
}
