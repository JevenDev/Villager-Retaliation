package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.ProtectedVillagerProperty;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerWalletService {
    private static final String WALLET_TAG = "VillagerRetaliationWallet";
    private static final String INITIALIZED_TAG = "Initialized";
    private static final String CURRENT_EMERALDS_TAG = "CurrentEmeralds";
    private static final String MAX_EMERALDS_TAG = "MaxEmeralds";
    private static final String LIFETIME_EARNED_TAG = "LifetimeEarned";
    private static final String LIFETIME_SPENT_TAG = "LifetimeSpent";
    private static final String LIFETIME_DEPOSITED_TAG = "LifetimeDeposited";
    private static final String LAST_INCOME_DAY_TAG = "LastIncomeDay";
    private static final String LAST_DEPOSIT_DAY_TAG = "LastDepositDay";
    private static final long DAY_TICKS = 24000L;
    private static final int WALLET_TICK_INTERVAL = 200;
    private static final int MAX_SAFE_EMERALDS = 1_000_000_000;

    private VillagerWalletService() {
    }

    public static WalletSnapshot getWallet(Villager villager) {
        initializeWalletIfNeeded(villager);
        setMaxEmeraldsFromProfessionAndSkills(villager);
        CompoundTag wallet = walletTag(villager);
        return new WalletSnapshot(
                wallet.getBoolean(INITIALIZED_TAG),
                safeInt(wallet, CURRENT_EMERALDS_TAG),
                safeInt(wallet, MAX_EMERALDS_TAG),
                safeInt(wallet, LIFETIME_EARNED_TAG),
                safeInt(wallet, LIFETIME_SPENT_TAG),
                safeInt(wallet, LIFETIME_DEPOSITED_TAG),
                wallet.getLong(LAST_INCOME_DAY_TAG),
                wallet.getLong(LAST_DEPOSIT_DAY_TAG)
        );
    }

    public static boolean isWalletInitialized(Villager villager) {
        return villager.getPersistentData().contains(WALLET_TAG, Tag.TAG_COMPOUND)
                && villager.getPersistentData().getCompound(WALLET_TAG).getBoolean(INITIALIZED_TAG);
    }

    public static void initializeWalletIfNeeded(Villager villager) {
        CompoundTag wallet = walletTag(villager);
        if (wallet.getBoolean(INITIALIZED_TAG)) {
            sanitize(wallet);
            return;
        }

        int max = calculateMaxEmeralds(villager);
        int starting = Mth.clamp(startingEmeralds(villager), 0, max);
        wallet.putBoolean(INITIALIZED_TAG, true);
        wallet.putInt(CURRENT_EMERALDS_TAG, starting);
        wallet.putInt(MAX_EMERALDS_TAG, max);
        wallet.putInt(LIFETIME_EARNED_TAG, starting);
        wallet.putInt(LIFETIME_SPENT_TAG, 0);
        wallet.putInt(LIFETIME_DEPOSITED_TAG, 0);
        wallet.putLong(LAST_INCOME_DAY_TAG, currentDay(villager));
        wallet.putLong(LAST_DEPOSIT_DAY_TAG, -1L);
    }

    public static int getCurrentEmeralds(Villager villager) {
        return getWallet(villager).currentEmeralds();
    }

    public static int getMaxEmeralds(Villager villager) {
        return getWallet(villager).maxEmeralds();
    }

    public static int getLifetimeEarned(Villager villager) {
        return getWallet(villager).lifetimeEarned();
    }

    public static int getLifetimeSpent(Villager villager) {
        return getWallet(villager).lifetimeSpent();
    }

    public static int getLifetimeDeposited(Villager villager) {
        return getWallet(villager).lifetimeDeposited();
    }

    public static int addEmeralds(Villager villager, int amount, WalletSource source) {
        if (amount <= 0) {
            return 0;
        }
        initializeWalletIfNeeded(villager);
        CompoundTag wallet = walletTag(villager);
        int safeAmount = Math.min(amount, MAX_SAFE_EMERALDS - safeInt(wallet, CURRENT_EMERALDS_TAG));
        if (safeAmount <= 0) {
            return 0;
        }
        wallet.putInt(CURRENT_EMERALDS_TAG, safeInt(wallet, CURRENT_EMERALDS_TAG) + safeAmount);
        wallet.putInt(LIFETIME_EARNED_TAG, addClamped(safeInt(wallet, LIFETIME_EARNED_TAG), safeAmount));
        return safeAmount;
    }

    public static boolean canSpendEmeralds(Villager villager, int amount) {
        return amount <= 0 || getCurrentEmeralds(villager) >= amount;
    }

    public static boolean spendEmeralds(Villager villager, int amount, WalletSource source) {
        if (amount <= 0) {
            return true;
        }
        initializeWalletIfNeeded(villager);
        CompoundTag wallet = walletTag(villager);
        int current = safeInt(wallet, CURRENT_EMERALDS_TAG);
        if (current < amount) {
            return false;
        }
        wallet.putInt(CURRENT_EMERALDS_TAG, current - amount);
        wallet.putInt(LIFETIME_SPENT_TAG, addClamped(safeInt(wallet, LIFETIME_SPENT_TAG), amount));
        return true;
    }

    public static void setMaxEmeraldsFromProfessionAndSkills(Villager villager) {
        initializeWalletIfNeeded(villager);
        walletTag(villager).putInt(MAX_EMERALDS_TAG, calculateMaxEmeralds(villager));
    }

    public static CompoundTag serializeWallet(Villager villager) {
        initializeWalletIfNeeded(villager);
        return walletTag(villager).copy();
    }

    public static void deserializeWallet(Villager villager, CompoundTag walletData) {
        if (walletData == null) {
            return;
        }
        villager.getPersistentData().put(WALLET_TAG, walletData.copy());
        initializeWalletIfNeeded(villager);
    }

    public static void tickWallet(Villager villager) {
        if (villager.level().isClientSide
                || !(villager.level() instanceof ServerLevel level)
                || villager.isBaby()
                || !villager.isAlive()
                || level.getGameTime() % WALLET_TICK_INTERVAL != spreadTickOffset(villager)) {
            return;
        }

        initializeWalletIfNeeded(villager);
        setMaxEmeraldsFromProfessionAndSkills(villager);
        long day = currentDay(level);
        CompoundTag wallet = walletTag(villager);
        if (wallet.getLong(LAST_INCOME_DAY_TAG) >= day) {
            return;
        }
        wallet.putLong(LAST_INCOME_DAY_TAG, day);

        int current = safeInt(wallet, CURRENT_EMERALDS_TAG);
        int max = safeInt(wallet, MAX_EMERALDS_TAG);
        if (current < max) {
            int income = Math.min(dailyIncome(villager), max - current);
            if (income > 0) {
                addEmeralds(villager, income, WalletSource.DAILY_WORK);
            }
        }

        if (getDepositAmount(villager) > 0 && wallet.getLong(LAST_DEPOSIT_DAY_TAG) < day) {
            wallet.putLong(LAST_DEPOSIT_DAY_TAG, day);
            tryDepositExcessEmeralds(villager);
        }
    }

    public static int getDepositAmount(Villager villager) {
        WalletSnapshot wallet = getWallet(villager);
        return Math.max(0, wallet.currentEmeralds() - wallet.maxEmeralds());
    }

    public static boolean canDepositWalletEmeralds(Villager villager) {
        return getDepositAmount(villager) > 0
                && villager.level() instanceof ServerLevel level
                && AssignedStorageService.hasAssignedStorage(level, villager);
    }

    public static DepositResult tryDepositExcessEmeralds(Villager villager) {
        return tryDepositEmeralds(villager, getDepositAmount(villager));
    }

    public static DepositResult tryDepositEmeralds(Villager villager, int amount) {
        if (amount <= 0) {
            return DepositResult.none();
        }
        if (!(villager.level() instanceof ServerLevel level)) {
            return DepositResult.unavailable(amount);
        }
        if (!AssignedStorageService.hasAssignedStorage(level, villager)) {
            return DepositResult.noAssignedStorage(amount);
        }

        initializeWalletIfNeeded(villager);
        CompoundTag wallet = walletTag(villager);
        int requested = Math.min(amount, safeInt(wallet, CURRENT_EMERALDS_TAG));
        int remaining = requested;
        int deposited = 0;
        while (remaining > 0) {
            int chunk = Math.min(64, remaining);
            ItemStack stack = createProtectedEmeraldStack(villager, chunk, "earnings_deposit");
            ItemStack remainder = AssignedStorageService.depositStack(villager, stack);
            int moved = chunk - remainder.getCount();
            if (moved <= 0) {
                break;
            }
            deposited += moved;
            remaining -= moved;
            if (!remainder.isEmpty()) {
                break;
            }
        }

        if (deposited > 0) {
            wallet.putInt(CURRENT_EMERALDS_TAG, safeInt(wallet, CURRENT_EMERALDS_TAG) - deposited);
            wallet.putInt(LIFETIME_DEPOSITED_TAG, addClamped(safeInt(wallet, LIFETIME_DEPOSITED_TAG), deposited));
        }
        return new DepositResult(requested, deposited, requested - deposited, true, false);
    }

    public static ItemStack createProtectedEmeraldStack(Villager villager, int count, String reason) {
        int safeCount = Mth.clamp(count, 0, Items.EMERALD.getDefaultMaxStackSize());
        if (safeCount <= 0) {
            return ItemStack.EMPTY;
        }
        return ProtectedVillagerProperty.mark(new ItemStack(Items.EMERALD, safeCount), villager, reason);
    }

    public static boolean canAffordPurchase(Villager villager, int amount) {
        return canSpendEmeralds(villager, amount);
    }

    public static boolean payFromWallet(Villager villager, int amount, WalletSource source) {
        return spendEmeralds(villager, amount, source);
    }

    public static int getVendorCurrencyAvailable(Villager villager) {
        return getCurrentEmeralds(villager);
    }

    public static int getVendorCurrencyCap(Villager villager) {
        return getMaxEmeralds(villager);
    }

    public static void replenishVendorCurrencyIfNeeded(Villager villager) {
        tickWallet(villager);
    }

    public static WealthTier getWealthTier(Villager villager) {
        int cap = getMaxEmeralds(villager);
        if (cap >= 120) {
            return WealthTier.WEALTHY;
        }
        if (cap >= 60) {
            return WealthTier.COMFORTABLE;
        }
        if (cap >= 20) {
            return WealthTier.MODEST;
        }
        return WealthTier.POOR;
    }

    private static CompoundTag walletTag(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(WALLET_TAG, Tag.TAG_COMPOUND)) {
            data.put(WALLET_TAG, new CompoundTag());
        }
        return data.getCompound(WALLET_TAG);
    }

    private static void sanitize(CompoundTag wallet) {
        wallet.putInt(CURRENT_EMERALDS_TAG, safeInt(wallet, CURRENT_EMERALDS_TAG));
        wallet.putInt(MAX_EMERALDS_TAG, Math.max(1, safeInt(wallet, MAX_EMERALDS_TAG)));
        wallet.putInt(LIFETIME_EARNED_TAG, safeInt(wallet, LIFETIME_EARNED_TAG));
        wallet.putInt(LIFETIME_SPENT_TAG, safeInt(wallet, LIFETIME_SPENT_TAG));
        wallet.putInt(LIFETIME_DEPOSITED_TAG, safeInt(wallet, LIFETIME_DEPOSITED_TAG));
    }

    private static int calculateMaxEmeralds(Villager villager) {
        WealthRange range = baseRange(villager);
        int skillBonus = skillBonus(villager);
        int professionBonus = professionBonus(professionKey(villager));
        int baseline = range.min() + (range.max() - range.min()) * Math.max(1, villager.getVillagerData().getLevel()) / 5;
        return Mth.clamp(baseline + skillBonus + professionBonus, 1, MAX_SAFE_EMERALDS);
    }

    private static int startingEmeralds(Villager villager) {
        WealthRange range = startingRange(villager);
        if (range.max() <= range.min()) {
            return range.min();
        }
        return range.min() + villager.getRandom().nextInt(range.max() - range.min() + 1);
    }

    private static int dailyIncome(Villager villager) {
        String professionKey = professionKey(villager);
        int level = Math.max(1, villager.getVillagerData().getLevel());
        int base = switch (professionKey) {
            case "nitwit", "none" -> villager.getRandom().nextInt(2);
            default -> Math.max(1, level);
        };
        int skill = skillBonus(villager) / 12;
        int profession = switch (professionKey) {
            case "armorer", "weaponsmith", "toolsmith" -> 2;
            case "cleric", "librarian", "cartographer" -> 1;
            case "nitwit", "none" -> 0;
            default -> 0;
        };
        int variance = villager.getRandom().nextInt(2);
        return Mth.clamp(base + skill + profession + variance, 0, level >= 4 ? 8 : 5);
    }

    private static int skillBonus(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        VillagerSkill skill = VillagerProfessionSkills.primarySkill(villager);
        return Math.max(0, VillagerProfileManager.getSkill(level, villager, skill) / 10);
    }

    private static WealthRange startingRange(Villager villager) {
        String professionKey = professionKey(villager);
        if ("nitwit".equals(professionKey)) {
            return new WealthRange(0, 3);
        }
        if ("none".equals(professionKey)) {
            return new WealthRange(0, 5);
        }
        return switch (Math.max(1, villager.getVillagerData().getLevel())) {
            case 1 -> new WealthRange(2, 8);
            case 2 -> new WealthRange(4, 12);
            case 3 -> new WealthRange(8, 20);
            case 4 -> new WealthRange(16, 32);
            default -> new WealthRange(24, 48);
        };
    }

    private static WealthRange baseRange(Villager villager) {
        String professionKey = professionKey(villager);
        if ("nitwit".equals(professionKey)) {
            return new WealthRange(4, 12);
        }
        if ("none".equals(professionKey)) {
            return new WealthRange(8, 20);
        }
        return switch (Math.max(1, villager.getVillagerData().getLevel())) {
            case 1 -> new WealthRange(20, 40);
            case 2 -> new WealthRange(35, 60);
            case 3 -> new WealthRange(50, 90);
            case 4 -> new WealthRange(75, 130);
            default -> new WealthRange(100, 180);
        };
    }

    private static int professionBonus(String professionKey) {
        return switch (professionKey) {
            case "armorer", "weaponsmith", "toolsmith" -> 20;
            case "cleric", "librarian", "cartographer" -> 12;
            case "farmer", "fisherman", "shepherd", "butcher" -> 6;
            case "mason", "fletcher", "leatherworker" -> 4;
            default -> 0;
        };
    }

    private static String professionKey(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE) {
            return "none";
        }
        return VillagerProfessionSkills.professionKey(profession).toLowerCase(Locale.ROOT);
    }

    private static int safeInt(CompoundTag tag, String key) {
        return Mth.clamp(tag.getInt(key), 0, MAX_SAFE_EMERALDS);
    }

    private static int addClamped(int left, int right) {
        if (right >= MAX_SAFE_EMERALDS - left) {
            return MAX_SAFE_EMERALDS;
        }
        return Math.max(0, left + right);
    }

    private static long currentDay(Villager villager) {
        return villager.level() instanceof ServerLevel level ? currentDay(level) : 0L;
    }

    private static long currentDay(ServerLevel level) {
        return level.getDayTime() / DAY_TICKS;
    }

    private static long spreadTickOffset(Villager villager) {
        return Math.floorMod(villager.getUUID().getLeastSignificantBits(), WALLET_TICK_INTERVAL);
    }

    public enum WalletSource {
        STARTING_FUNDS,
        DAILY_WORK,
        HIRE_PAYMENT,
        TASK_REWARD,
        DEPOSIT_ADJUSTMENT,
        DEBUG
    }

    public enum WealthTier {
        POOR,
        MODEST,
        COMFORTABLE,
        WEALTHY
    }

    public record WalletSnapshot(
            boolean initialized,
            int currentEmeralds,
            int maxEmeralds,
            int lifetimeEarned,
            int lifetimeSpent,
            int lifetimeDeposited,
            long lastIncomeDay,
            long lastDepositDay) {
    }

    public record DepositResult(
            int requested,
            int deposited,
            int remaining,
            boolean assignedStorageAvailable,
            boolean storageUnavailable) {
        private static DepositResult none() {
            return new DepositResult(0, 0, 0, true, false);
        }

        private static DepositResult noAssignedStorage(int requested) {
            return new DepositResult(requested, 0, requested, false, false);
        }

        private static DepositResult unavailable(int requested) {
            return new DepositResult(requested, 0, requested, false, true);
        }
    }

    private record WealthRange(int min, int max) {
    }
}
