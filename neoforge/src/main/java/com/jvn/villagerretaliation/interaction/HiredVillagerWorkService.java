package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.FarmingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorker;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.NitwitWorker;
import com.jvn.villagerretaliation.interaction.work.StatusOnlyWorker;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;

public final class HiredVillagerWorkService {
    private static final String TAG = "VillagerRetaliationHiredWork";
    private static final long DAY_TICKS = 24000L;
    private static final Map<HiredVillagerRole, HiredRoleWorker> WORKERS = new EnumMap<>(HiredVillagerRole.class);

    static {
        register(new LoggingWorker());
        register(new MiningWorker());
        register(new FarmingWorker());
        register(new NitwitWorker());
        register(new StatusOnlyWorker(HiredVillagerRole.BREWING, "Brewing automation is waiting for a configured brewing stand and supplies."));
        register(new StatusOnlyWorker(HiredVillagerRole.NAVIGATION, "Navigation work is ready for target discovery configuration."));
        register(new StatusOnlyWorker(HiredVillagerRole.ANIMAL_HANDLING, "Animal handling is waiting for lure supplies and a safe pen."));
        register(new StatusOnlyWorker(HiredVillagerRole.COMBAT, "Guard duty active. Combat is handled by existing retaliation systems."));
    }

    private HiredVillagerWorkService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || villager.isBaby()
                || !villager.isAlive()
                || villager.isSleeping()
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || villager.getTarget() != null
                || villager.getLastHurtByMob() != null
                || !HiredVillagerContractService.isHired(level, villager)) {
            return;
        }

        UUID hirerId = HiredVillagerContractService.getHirer(level, villager).orElse(null);
        if (hirerId == null || !(level.getServer().getPlayerList().getPlayer(hirerId) instanceof ServerPlayer hirer)) {
            setStatus(state(villager), "Waiting for hirer to be online.");
            return;
        }
        if (VillagerAggressionPolicy.shouldAttackOnSight(villager, hirer)) {
            return;
        }

        int interval = Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        if (Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), interval) != 0L) {
            return;
        }

        CompoundTag state = state(villager);
        initializeDefaults(state);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        HiredRoleWorker worker = WORKERS.get(role);
        if (worker == null) {
            setStatus(state, "Paused: no worker exists for " + role.label() + ".");
            return;
        }
        if (!state.getBoolean("Enabled")) {
            setStatus(state, "Paused by hirer.");
            return;
        }
        long nextWorkGameTime = state.getLong("NextWorkGameTime");
        if (nextWorkGameTime > level.getGameTime()) {
            setStatus(state, "Preparing for next task. Cooldown: " + (nextWorkGameTime - level.getGameTime()) + " ticks.");
            return;
        }

        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        handleDailyFood(level, villager, hirer, role, worker, state, inventory);
        int radius = Mth.clamp(state.getInt("Radius"), 4, Math.max(4, VillagerRetaliationConfig.HIRED_WORK_MAX_RADIUS.get()));
        int efficiency = efficiencyPercent(level, villager, role, state, inventory);
        HiredWorkContext context = new HiredWorkContext(
                inventory,
                state,
                radius,
                efficiency,
                state.getBoolean("AutoDepositOutputs"),
                state.getBoolean("UseAssignedStorageForSupplies"));
        WorkResult result = worker.tick(level, villager, hirer, context);
        setStatus(state, result.status() + " Efficiency: " + efficiency + "%.");
        if (result.completed()) {
            state.putLong("NextWorkGameTime", level.getGameTime() + nextTaskCooldownTicks(efficiency));
            maybeNotify(level, villager, hirer, state, result.status(), 20L * 30L);
        }
    }

    public static void clearRuntimeState() {
    }

    public static void sendStatus(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        String status = state.getString("Status");
        int efficiency = efficiencyPercent(level, villager, role, state, HiredJobInventory.getJobInventory(villager));
        VillagerInteractionService.sendVillagerNotice(player, villager,
                "Work: " + (state.getBoolean("Enabled") ? "running" : "paused")
                        + ", role " + role.label()
                        + ", radius " + state.getInt("Radius")
                        + ", supplies " + (state.getBoolean("UseAssignedStorageForSupplies") ? "job+assigned" : "job")
                        + ", auto-deposit " + (state.getBoolean("AutoDepositOutputs") ? "on" : "off")
                        + ", starvation days " + state.getInt("StarvationDays")
                        + ", efficiency " + efficiency + "%. "
                        + (status.isBlank() ? "No task yet." : status));
    }

    public static void toggleEnabled(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state);
        state.putBoolean("Enabled", !state.getBoolean("Enabled"));
        setStatus(state, state.getBoolean("Enabled") ? "Work resumed." : "Work paused by hirer.");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void changeRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        CompoundTag state = state(villager);
        initializeDefaults(state);
        int max = Math.max(4, VillagerRetaliationConfig.HIRED_WORK_MAX_RADIUS.get());
        int radius = Mth.clamp(state.getInt("Radius") + delta, 4, max);
        state.putInt("Radius", radius);
        setStatus(state, "Work radius set to " + radius + ".");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void toggleAssignedSupplies(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state);
        state.putBoolean("UseAssignedStorageForSupplies", !state.getBoolean("UseAssignedStorageForSupplies"));
        setStatus(state, "Assigned storage supplies " + (state.getBoolean("UseAssignedStorageForSupplies") ? "enabled." : "disabled."));
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void toggleAutoDeposit(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state);
        state.putBoolean("AutoDepositOutputs", !state.getBoolean("AutoDepositOutputs"));
        setStatus(state, "Auto-deposit outputs " + (state.getBoolean("AutoDepositOutputs") ? "enabled." : "disabled."));
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void configureRole(ServerPlayer player, ServerLevel level, Villager villager, HiredVillagerRole role) {
        CompoundTag state = state(villager);
        initializeDefaults(state);
        switch (role) {
            case LOGGING -> {
                String current = state.getString("LoggingFilter");
                state.putString("LoggingFilter", current == null || current.isBlank() || "any".equals(current) ? "oak_log" : "any");
                setStatus(state, "Logging filter cycled to " + state.getString("LoggingFilter") + ".");
            }
            case FARMING -> {
                String current = state.getString("CropMode");
                state.putString("CropMode", "harvest_replant".equals(current) ? "harvest_only" : "harvest_replant");
                setStatus(state, "Farming mode set to " + state.getString("CropMode") + ".");
            }
            case BREWING -> setStatus(state, "Brewing route cycled. MVP supports status-only brewing setup.");
            case NAVIGATION -> setStatus(state, "Navigation target cycled. MVP stores configuration and reports status.");
            case ANIMAL_HANDLING -> setStatus(state, "Animal handling target cycled. MVP waits for lures and safe pen support.");
            case NITWIT -> setStatus(state, "Nitwit focus cycled. Expect occasional deeply questionable reports.");
            default -> setStatus(state, role.label() + " has no extra configuration yet.");
        }
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static boolean canManageWork(ServerLevel level, Villager villager, ServerPlayer player) {
        return HiredVillagerContractService.isHiredBy(level, villager, player);
    }

    private static void register(HiredRoleWorker worker) {
        WORKERS.put(worker.role(), worker);
    }

    private static CompoundTag state(Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(TAG, new CompoundTag());
        }
        return persistentData.getCompound(TAG);
    }

    private static void initializeDefaults(CompoundTag state) {
        if (!state.contains("Enabled", Tag.TAG_BYTE)) {
            state.putBoolean("Enabled", true);
        }
        if (!state.contains("Radius", Tag.TAG_INT)) {
            state.putInt("Radius", Mth.clamp(
                    VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(),
                    4,
                    Math.max(4, VillagerRetaliationConfig.HIRED_WORK_MAX_RADIUS.get())));
        }
        if (!state.contains("UseAssignedStorageForSupplies", Tag.TAG_BYTE)) {
            state.putBoolean("UseAssignedStorageForSupplies", false);
        }
        if (!state.contains("AutoDepositOutputs", Tag.TAG_BYTE)) {
            state.putBoolean("AutoDepositOutputs", true);
        }
        if (!state.contains("LoggingFilter", Tag.TAG_STRING)) {
            state.putString("LoggingFilter", "any");
        }
        if (!state.contains("CropMode", Tag.TAG_STRING)) {
            state.putString("CropMode", "harvest_replant");
        }
        if (!state.contains("NavigationTargetType", Tag.TAG_STRING)) {
            state.putString("NavigationTargetType", "interesting");
        }
        if (!state.contains("Status", Tag.TAG_STRING)) {
            state.putString("Status", "Waiting for work tick.");
        }
    }

    private static void setStatus(CompoundTag state, String status) {
        state.putString("Status", status == null ? "" : status);
    }

    private static void handleDailyFood(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredVillagerRole role,
            HiredRoleWorker worker,
            CompoundTag state,
            HiredJobInventory inventory) {
        long day = level.getDayTime() / DAY_TICKS;
        if (!VillagerRetaliationConfig.HIRED_WORK_FOOD_ENABLED.get()
                || !worker.requiresFood()
                || state.getLong("LastFoodCheckDay") == day) {
            return;
        }
        state.putLong("LastFoodCheckDay", day);
        int needed = Math.max(0, roleFoodCost(role));
        if (needed <= 0 || consumeFood(villager, inventory, needed, state.getBoolean("UseAssignedStorageForSupplies")) >= needed) {
            int starvationDays = Math.max(0, state.getInt("StarvationDays") - 1);
            state.putInt("StarvationDays", starvationDays);
            if (starvationDays == 0) {
                VillagerMoodService.setMood(level, villager, VillagerMood.CONTENT, 14, "hired_work_fed", hirer.getUUID(), hirer.getUUID(), VillagerMoodService.SHORT_DECAY_TICKS);
            }
            return;
        }

        int maxDays = Math.max(0, VillagerRetaliationConfig.HIRED_WORK_MAX_STARVATION_PENALTY_DAYS.get());
        int starvationDays = Mth.clamp(state.getInt("StarvationDays") + 1, 0, maxDays);
        state.putInt("StarvationDays", starvationDays);
        VillagerMood mood = starvationDays >= Math.max(2, maxDays / 2) ? VillagerMood.ANGRY : VillagerMood.STRESSED;
        VillagerMoodService.setMood(
                level,
                villager,
                mood,
                VillagerRetaliationConfig.HIRED_WORK_NO_FOOD_MOOD_INTENSITY.get(),
                "hired_work_no_food",
                hirer.getUUID(),
                hirer.getUUID(),
                VillagerMoodService.MEDIUM_DECAY_TICKS);
        VillagerReputationManager.addHiredWorkReputation(
                level,
                villager,
                hirer.getUUID(),
                VillagerRetaliationConfig.HIRED_WORK_NO_FOOD_REPUTATION_PENALTY.get());
        maybeNotify(level, villager, hirer, state, "No food for hired work. Mood and reputation suffered.", DAY_TICKS);
    }

    private static int consumeFood(Villager villager, HiredJobInventory inventory, int neededNutrition, boolean assignedSupplies) {
        int nutrition = 0;
        for (int slot : inventory.supplySlots()) {
            ItemStack stack = inventory.getItem(slot);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (stack.isEmpty() || food == null) {
                continue;
            }
            while (!stack.isEmpty() && nutrition < neededNutrition) {
                nutrition += Math.max(1, food.nutrition());
                inventory.consumeSupply(candidate -> candidate == stack, 1);
            }
            if (nutrition >= neededNutrition) {
                return nutrition;
            }
        }
        if (assignedSupplies && nutrition < neededNutrition) {
            nutrition += AssignedStorageService.consumeItems(
                    villager,
                    stack -> stack.get(DataComponents.FOOD) != null,
                    neededNutrition - nutrition);
        }
        return nutrition;
    }

    private static int roleFoodCost(HiredVillagerRole role) {
        int base = Math.max(0, VillagerRetaliationConfig.HIRED_WORK_BASE_FOOD_PER_DAY.get());
        int roleCost = switch (role) {
            case COMBAT -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_COMBAT.get();
            case MINING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_MINING.get();
            case LOGGING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_LOGGING.get();
            case FARMING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_FARMING.get();
            case BREWING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_BREWING.get();
            case NAVIGATION -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_NAVIGATION.get();
            case ANIMAL_HANDLING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_ANIMAL_HANDLING.get();
            case NITWIT -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_NITWIT.get();
        };
        return Math.max(0, Math.max(base, roleCost));
    }

    private static int efficiencyPercent(ServerLevel level, Villager villager, HiredVillagerRole role, CompoundTag state, HiredJobInventory inventory) {
        int min = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT.get());
        int max = Math.max(min, VillagerRetaliationConfig.HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT.get());
        int efficiency = VillagerRetaliationConfig.HIRED_WORK_BASE_EFFICIENCY_PERCENT.get();
        efficiency += (HiredVillagerRoles.roleScore(level, villager, role) - 50) / 2;
        if (HiredVillagerRoles.availableRoles(level, villager).contains(role)) {
            efficiency += 10;
        }
        VillagerMoodState mood = VillagerMoodService.mood(level, villager);
        efficiency += switch (mood.primaryMood()) {
            case CONTENT, GRATEFUL, PROUD, HOPEFUL -> 8;
            case ANGRY, AFRAID, STRESSED, GRIEVING -> -15;
            case SUSPICIOUS, LONELY -> -8;
            default -> 0;
        };
        efficiency -= 15 * Math.max(0, state.getInt("StarvationDays"));
        ItemStack tool = inventory.getItem(HiredJobInventory.MAINHAND_SLOT);
        if (!tool.isEmpty()) {
            efficiency += toolTierBonus(tool);
        }
        if ((role == HiredVillagerRole.MINING || role == HiredVillagerRole.LOGGING || role == HiredVillagerRole.FARMING) && tool.isEmpty()) {
            efficiency -= 20;
        }
        return Mth.clamp(efficiency, min, max);
    }

    private static int toolTierBonus(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tieredItem) {
            String name = tieredItem.getTier().toString().toLowerCase(Locale.ROOT);
            if (name.contains("netherite")) {
                return 28;
            }
            if (name.contains("diamond")) {
                return 20;
            }
            if (name.contains("iron")) {
                return 12;
            }
            if (name.contains("stone")) {
                return 5;
            }
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("netherite")) {
            return 28;
        }
        if (path.contains("diamond")) {
            return 20;
        }
        if (path.contains("iron")) {
            return 12;
        }
        if (path.contains("stone")) {
            return 5;
        }
        return 0;
    }

    private static int nextTaskCooldownTicks(int efficiency) {
        return Mth.clamp(Math.round(80.0F * 100.0F / Math.max(25.0F, efficiency)), 10, 200);
    }

    private static void maybeNotify(ServerLevel level, Villager villager, ServerPlayer hirer, CompoundTag state, String message, long cooldownTicks) {
        long now = level.getGameTime();
        if (now - state.getLong("LastNoticeTick") < cooldownTicks) {
            return;
        }
        state.putLong("LastNoticeTick", now);
        VillagerInteractionService.sendVillagerNotice(hirer, villager, message);
    }
}
