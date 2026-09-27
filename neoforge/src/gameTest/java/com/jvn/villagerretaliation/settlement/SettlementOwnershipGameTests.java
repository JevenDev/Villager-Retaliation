package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementOwnershipGameTests {
    private SettlementOwnershipGameTests() {
    }

    @GameTest(template = "empty", batch = "settlement_ownership")
    public static void familyAndJobHandoffsRejectOtherOwnersBeforeChangingItems(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture.town());
        resident.getInventory().setItem(7, new ItemStack(Items.CARROT, 11));
        var job = HiredJobInventory.getJobInventory(resident);
        job.setItem(HiredJobInventory.HOTBAR_START + 4, new ItemStack(Items.TORCH, 13));
        var care = SettlementResidentState.capture(resident, fixture.town(), 0L);
        var other = EntityType.VILLAGER.create(helper.getLevel());
        other.getInventory().setItem(3, new ItemStack(Items.POTATO, 7));
        rejects(helper, () -> care.restore(other));
        rejects(helper, () -> HiredJobInventory.getJobInventory(other).restoreSnapshot(job.captureSnapshot()));
        helper.assertValueEqual(other.getInventory().getItem(3).getCount(), 7, "a rejected family handoff leaves the recipient untouched");
        helper.assertTrue(HiredJobInventory.getJobInventory(other).isEmpty(), "a rejected job handoff cannot duplicate the owner's items");
        var original = resident.saveWithoutId(new CompoundTag());
        var context = care.context(helper.getLevel());
        context.getInventory().removeItem(7, 3);
        care.rememberFamily(context);
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(original);
        var saved = SettlementResidentState.load(care.save());
        saved.restore(returning);
        helper.assertValueEqual(returning.getInventory().getItem(7).getCount(), 8, "family consumption is restored into its exact native slot");
        returning.getInventory().removeItem(7, 2);
        saved.restore(returning);
        helper.assertValueEqual(returning.getInventory().getItem(7).getCount(), 6, "replayed family revisions cannot undo later native transfers");
        helper.assertValueEqual(HiredJobInventory.getJobInventory(returning).getItem(HiredJobInventory.HOTBAR_START + 4).getCount(), 13,
                "family reconciliation cannot replace the separate job inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_ownership")
    public static void invalidJobSnapshotsCannotPartiallyOverwriteEquipmentOrLayout(GameTestHelper helper) {
        var resident = EntityType.VILLAGER.create(helper.getLevel());
        var inventory = HiredJobInventory.getJobInventory(resident);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        inventory.setItem(HiredJobInventory.HOTBAR_START + 2, new ItemStack(Items.OAK_LOG, 5));
        inventory.markPlayerPlacedSupply(HiredJobInventory.HOTBAR_START + 2);
        var before = inventory.captureSnapshot();
        var duplicate = before.copy();
        var entries = duplicate.getList("Items", Tag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        rejects(helper, () -> inventory.restoreSnapshot(duplicate));
        helper.assertValueEqual(inventory.captureSnapshot(), before, "duplicate slots are rejected atomically");
        var invalid = before.copy();
        invalid.putString("Items", "invalid");
        rejects(helper, () -> inventory.restoreSnapshot(invalid));
        helper.assertValueEqual(inventory.captureSnapshot(), before, "invalid lists cannot erase equipment or slot ownership");
        inventory.restoreSnapshot(before);
        helper.assertTrue(inventory.isSupplySlot(HiredJobInventory.HOTBAR_START + 2), "valid handoffs preserve hotbar supply layout");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wallet")
    public static void backgroundWalletEarningsPersistAndNeverReplayOverLaterSpending(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture.town());
        var wallet = VillagerWalletService.captureWallet(resident);
        wallet.putInt("CurrentEmeralds", 0);
        wallet.putLong("LastIncomeDay", -1L);
        VillagerWalletService.restoreWallet(resident, wallet);
        var original = resident.saveWithoutId(new CompoundTag());
        var state = SettlementResidentState.capture(resident, fixture.town(), 0L);
        state.advance(helper.getLevel(), fixture.town(), 200L);
        var restored = SettlementResidentState.load(state.save());
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(original);
        restored.restore(returning);
        int earned = VillagerWalletService.getCurrentCurrency(returning);
        helper.assertTrue(earned > 0, "an eligible background resident receives existing daily income");
        var nativeResident = EntityType.VILLAGER.create(helper.getLevel());
        nativeResident.load(original);
        VillagerWalletService.updateWallet(nativeResident, helper.getLevel().getDayTime() / 24000L);
        helper.assertValueEqual(VillagerWalletService.getCurrentCurrency(nativeResident), earned, "loaded and background income use the same currency-neutral calculation");
        helper.assertTrue(VillagerWalletService.spendCurrency(returning, 1), "a returning resident can spend earned currency");
        restored.restore(returning);
        helper.assertValueEqual(VillagerWalletService.getCurrentCurrency(returning), earned - 1, "reconciliation never refunds a later purchase");
        VillagerWalletService.updateWallet(returning, helper.getLevel().getDayTime() / 24000L);
        helper.assertValueEqual(VillagerWalletService.getCurrentCurrency(returning), earned - 1, "the income day cannot be paid twice");
        helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(returning), earned - 1, "legacy wallet accessors remain compatible");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wallet_currency")
    public static void datapackCurrencyDepositsRetryWithoutSpendingUnavailableFunds(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var resources = server.getResourceManager();
        try {
            VillagerCurrencyResources.reload(server, new CurrencyResources(resources));
            helper.assertValueEqual(VillagerCurrencyResources.primaryItem(server), Items.IRON_NUGGET, "the datapack selects the physical currency");
            var resident = EntityType.VILLAGER.create(level);
            resident.setPos(helper.absolutePos(new BlockPos(1, 2, 1)).getCenter());
            resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
            var remote = new BlockPos(16000000, 80, 16000000);
            var assignments = AssignedStorageSavedData.get(level);
            assignments.assign(new AssignedStorageSavedData.AssignedContainerRecord(level.dimension(), remote,
                    resident.getUUID(), UUID.randomUUID(), AssignedStorageService.OUTPUT_PURPOSE, 0, "valid"));
            int capacity = VillagerWalletService.getCurrencyCapacity(resident);
            VillagerWalletService.addCurrency(resident, capacity + 100 - VillagerWalletService.getCurrentCurrency(resident));
            long day = level.getDayTime() / 24000L;
            VillagerWalletService.updateWallet(resident, day);
            helper.assertValueEqual(VillagerWalletService.getCurrentCurrency(resident), capacity + 100, "unloaded storage leaves every unit in the wallet");
            helper.assertFalse(level.hasChunkAt(remote), "deposit retries never load remote storage");
            var chestPos = helper.absolutePos(new BlockPos(3, 2, 3));
            level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            assignments.assign(new AssignedStorageSavedData.AssignedContainerRecord(level.dimension(), chestPos,
                    resident.getUUID(), UUID.randomUUID(), AssignedStorageService.OUTPUT_PURPOSE, 1, "valid"));
            VillagerWalletService.updateWallet(resident, day);
            var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
            int deposited = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).is(Items.IRON_NUGGET)) deposited += chest.getItem(slot).getCount();
                helper.assertFalse(chest.getItem(slot).is(Items.EMERALD), "deposits never substitute emeralds for custom currency");
            }
            helper.assertValueEqual(deposited, 100, "the same-day retry deposits the exact excess using the selected currency");
            helper.assertValueEqual(VillagerWalletService.getCurrentCurrency(resident) + deposited, capacity + 100, "depositing conserves logical and physical currency");
            helper.assertValueEqual(VillagerWalletService.getWallet(resident).lifetimeDeposited(), 100, "accounting records only completed deposits");
            VillagerWalletService.updateWallet(resident, day);
            helper.assertValueEqual(VillagerWalletService.getWallet(resident).lifetimeDeposited(), 100, "repeated wallet ticks cannot duplicate deposits");
        } finally {
            VillagerCurrencyResources.reload(server, resources);
        }
        helper.succeed();
    }

    private static Villager resident(GameTestHelper helper, SettlementRecord town) {
        var resident = EntityType.VILLAGER.create(helper.getLevel());
        resident.setPos(town.center().getBottomCenter());
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(helper.getLevel(), resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        return resident;
    }

    private static void rejects(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "invalid ownership or layout is rejected");
    }

    private record CurrencyResources(ResourceManager delegate) implements ResourceManager {
        @Override public Set<String> getNamespaces() { return this.delegate.getNamespaces(); }
        @Override public Optional<Resource> getResource(ResourceLocation location) { return this.delegate.getResource(location); }
        @Override public List<Resource> getResourceStack(ResourceLocation location) { return this.delegate.getResourceStack(location); }
        @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> predicate) {
            if (!path.equals("currency")) return this.delegate.listResources(path, predicate);
            var location = ResourceLocation.parse("villagerretaliation:currency/test.json");
            var resource = new Resource(null, () -> new ByteArrayInputStream("{\"item\":\"minecraft:iron_nugget\"}".getBytes(StandardCharsets.UTF_8)));
            return predicate.test(location) ? Map.of(location, resource) : Map.of();
        }
        @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> predicate) {
            return this.delegate.listResourceStacks(path, predicate);
        }
        @Override public Stream<PackResources> listPacks() { return this.delegate.listPacks(); }
    }
}
