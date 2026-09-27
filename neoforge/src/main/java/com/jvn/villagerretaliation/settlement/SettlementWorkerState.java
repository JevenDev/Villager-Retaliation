package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.HiredJobInventorySlotType;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService;
import com.jvn.villagerretaliation.skill.VillagerSkillPractice;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementWorkerState {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementWorkVersion";
    private final UUID id;
    private UUID area;
    private UUID huntingTarget;
    private boolean huntingEngaged;
    private long huntingCombatAt;
    private long huntingAttackAt;
    private SettlementHuntingTarget huntingBody;
    private HiredVillagerRole role;
    private BlockPos position;
    private CompoundTag inventory = new CompoundTag();
    private CompoundTag settings = new CompoundTag();
    private CompoundTag entityContext = new CompoundTag();
    private SettlementMiningAccess miningAccess;
    private long checkedAt;
    private long version;
    private long brewingVersion;
    private double progress;
    private int aptitude;
    private boolean unsafe;
    private boolean serviceBlocked;
    private String status = "idle";

    private SettlementWorkerState(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return this.id;
    }

    UUID huntingTarget() {
        return this.huntingTarget;
    }

    void huntingTarget(UUID target) {
        this.huntingTarget = target;
        if (target == null) this.huntingEngaged = false;
    }

    boolean huntingEngaged() {
        return this.huntingEngaged;
    }

    void huntingEngaged(UUID target) {
        this.huntingTarget = target;
        this.huntingEngaged = target != null;
    }

    void huntingTimers(Villager villager, long now) {
        if (this.huntingBody != null) this.huntingBody.restoreTimers(villager, now);
    }

    void huntingNativeDamage(Villager villager, long now, boolean ticking) {
        if (this.huntingBody != null) {
            this.huntingBody.nativeDamage(villager, now, ticking);
            this.entityContext = villager.saveWithoutId(new CompoundTag());
        }
    }

    void huntingInjury(Villager villager, CompoundTag before, long now) {
        if (this.huntingBody == null) this.huntingBody = SettlementHuntingTarget.capture(villager, now);
        this.huntingBody.interacted(villager, before, now);
        this.entityContext = villager.saveWithoutId(new CompoundTag());
        changed();
    }

    long huntingCombatAt() {
        return this.huntingCombatAt;
    }

    long huntingAttackAt() {
        return this.huntingAttackAt;
    }

    void huntingCombat(long now, long nextAttack) {
        this.huntingCombatAt = now;
        this.huntingAttackAt = nextAttack;
    }

    public UUID area() {
        return this.area;
    }

    public HiredVillagerRole role() {
        return this.role;
    }

    public BlockPos position() {
        return this.position;
    }

    BlockPos nativePosition() {
        return this.huntingBody == null ? this.position : this.huntingBody.nativePosition();
    }

    void huntingMoved(Villager villager, CompoundTag before, long now) {
        this.position = villager.blockPosition().immutable();
        huntingInjury(villager, before, now);
    }

    public int aptitude() {
        return this.aptitude;
    }

    public String status() {
        return this.status;
    }

    boolean unsafe() {
        return this.unsafe;
    }

    public long version() {
        return this.version;
    }

    long brewingVersion() {
        return this.brewingVersion;
    }

    void brewingOrder(SettlementBrewingOrder order) {
        com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker.clearOrder(this.settings);
        if (order != null) {
            order.apply(this.settings);
        }
        this.brewingVersion++;
    }

    public double progress() {
        return this.progress;
    }

    public CompoundTag inventory() {
        return this.inventory.copy();
    }

    CompoundTag settings() {
        return this.settings;
    }

    static SettlementWorkerState capture(ServerLevel level, Villager villager, SettlementWorkforceService.Duty duty, long now) {
        var state = new SettlementWorkerState(villager.getUUID());
        state.observe(level, villager, duty, now);
        return state;
    }

    void observe(ServerLevel level, Villager villager, SettlementWorkforceService.Duty duty, long now) {
        if (!this.id.equals(villager.getUUID())) throw new IllegalArgumentException("Worker observation has a different identity");
        if (!duty.area().id().equals(this.area) || duty.area().type().role() != this.role) {
            huntingTarget(null);
            this.serviceBlocked = false;
        }
        var engagement = SettlementHuntingTargetService.observeEngagement(level, duty, villager);
        this.huntingEngaged = engagement != null;
        if (engagement != null) this.huntingTarget = engagement;
        if (this.huntingBody != null) this.huntingBody.observe(villager, now);
        else if (duty.area().type().role() == HiredVillagerRole.HUNTING) this.huntingBody = SettlementHuntingTarget.capture(villager, now);
        this.huntingCombatAt = now;
        this.huntingAttackAt = com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.nextMeleeAttackTick(villager);
        this.area = duty.area().id();
        this.role = duty.area().type().role();
        this.position = villager.blockPosition().immutable();
        this.miningAccess = this.role == HiredVillagerRole.MINING
                ? SettlementMiningAccess.observe(this.miningAccess, duty.town().mines().get(this.area), duty.area(), this.position) : null;
        this.inventory = HiredJobInventory.getJobInventory(villager).captureSnapshot();
        HiredWorkSkillGrowthService.onPractice(level, villager, null, this.role, HiredWorkStateStore.state(villager), List.of());
        this.settings = HiredWorkStateStore.state(villager).copy();
        if (this.role == HiredVillagerRole.BUILDER) this.settings.remove("SettlementConstructionPosition");
        if (this.role == HiredVillagerRole.FISHING || this.role == HiredVillagerRole.LOGGING
                || this.role == HiredVillagerRole.MINING || this.role == HiredVillagerRole.ANIMAL_HANDLING
                || this.role == HiredVillagerRole.HUNTING || this.role == HiredVillagerRole.BUILDER) {
            this.entityContext = villager.saveWithoutId(new CompoundTag());
        }
        if (this.role == HiredVillagerRole.FISHING) {
            SettlementBackgroundFishing.capture(level, villager, this.settings);
        }
        this.aptitude = HiredVillagerRoles.roleScore(level, villager, this.role);
        this.version = Math.max(this.version, villager.getPersistentData().getLong(VERSION_TAG));
        this.brewingVersion = Math.max(this.brewingVersion, SettlementBrewingService.version(villager));
        this.checkedAt = Math.max(this.checkedAt, now);
        this.progress = 0.0D;
        this.unsafe = villager.getTarget() != null && engagement == null
                || villager.getLastHurtByMob() != null && !villager.getLastHurtByMob().getUUID().equals(engagement)
                || villager.isOnFire() || villager.isInLava()
                || com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState(level, villager.getBrain());
        this.status = this.unsafe ? "unsafe" : "active";
        var brain = HiredWorkerBrain.snapshot(this.settings, now);
        if (!villager.isSleeping() && !brain.status().equals("interaction.work.status.sleeping")
                && !brain.status().equals("interaction.work.status.tired")) {
            this.serviceBlocked = brain.taskState() == HiredWorkerTaskState.PAUSED_MISSING_TOOL
                    || brain.taskState() == HiredWorkerTaskState.NO_WORK_AREA
                    || brain.status().endsWith(".no_filter") || brain.status().endsWith(".no_crafting_table")
                    || brain.status().equals("interaction.work.craftsman.no_table") || brain.status().equals("interaction.work.cooking.no_station")
                    || brain.failureReason().endsWith("_profession_required");
        }
    }

    BlockPos miningEntry(SettlementMine mine, SettlementArea area) {
        if (this.miningAccess != null && !this.miningAccess.matches(area)) {
            this.settings.remove("SettlementMiningPosition");
        }
        this.miningAccess = SettlementMiningAccess.observe(this.miningAccess, mine, area, this.position);
        return this.miningAccess == null ? null : this.miningAccess.entry(mine, area);
    }

    boolean restore(Villager villager) {
        if (!this.id.equals(villager.getUUID())) throw new IllegalArgumentException("Worker handoff has a different identity");
        if (this.huntingBody != null) this.huntingBody.restore(villager, villager.level().getGameTime());
        if (this.role == HiredVillagerRole.HUNTING) {
            com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.restoreMeleeAttackTick(villager, this.huntingAttackAt);
        }
        SettlementBrewingService.restore(this, villager);
        if (villager.getPersistentData().getLong(VERSION_TAG) >= this.version) {
            return false;
        }
        HiredJobInventory.getJobInventory(villager).restoreSnapshot(this.inventory);
        villager.getPersistentData().putLong(VERSION_TAG, this.version);
        if (this.settings.contains("CraftsmanCursor", Tag.TAG_INT)) {
            HiredWorkStateStore.state(villager).putInt("CraftsmanCursor", this.settings.getInt("CraftsmanCursor"));
        }
        SettlementBackgroundFishing.restore(this, villager);
        if (this.role == HiredVillagerRole.LOGGING) {
            com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan.copyState(this.settings, HiredWorkStateStore.state(villager));
        }
        if (this.role == HiredVillagerRole.MINING) {
            com.jvn.villagerretaliation.interaction.work.mining.MiningWorkerState.copyState(this.settings, HiredWorkStateStore.state(villager));
        }
        HiredWorkSession.invalidate(villager);
        return true;
    }

    long checkedAt() {
        return this.checkedAt;
    }

    int elapsed(long now) {
        int elapsed = SettlementSimulationClock.elapsed(this.checkedAt, now);
        this.checkedAt = Math.max(this.checkedAt, now);
        return elapsed;
    }

    double availableWork(int elapsed, double multiplier) {
        return Math.min(24_000.0D, this.progress + elapsed * multiplier);
    }

    void work(int elapsed, double multiplier) {
        this.progress = availableWork(elapsed, multiplier);
    }

    boolean spend(double cost) {
        if (cost <= 0.0D || this.progress < cost) {
            return false;
        }
        this.progress -= cost;
        this.version++;
        return true;
    }

    void changed() {
        this.version++;
    }

    void settingsChanged(Villager villager) {
        long previous = this.version;
        this.version++;
        if (villager != null && villager.getPersistentData().getLong(VERSION_TAG) == previous) {
            this.inventory = HiredJobInventory.getJobInventory(villager).captureSnapshot();
            villager.getPersistentData().putLong(VERSION_TAG, this.version);
        }
    }

    void inventorySnapshot(CompoundTag snapshot) {
        this.inventory = snapshot.copy();
    }

    CompoundTag entityContext() {
        return this.entityContext.copy();
    }

    Villager context(ServerLevel level, SettlementRecord town) {
        if (this.entityContext.isEmpty()) return null;
        var owner = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (owner == null) return null;
        owner.load(this.entityContext.copy());
        HiredJobInventory.getJobInventory(owner).restoreSnapshot(this.inventory);
        var care = town.residents().get(this.id);
        if (care != null) care.restoreContext(owner);
        return owner;
    }

    void practice(ServerLevel level, List<VillagerSkillPractice> practice) {
        var result = HiredWorkSkillGrowthService.onBackgroundPractice(level, this.id, this.role, practice);
        if (result.increased()) {
            this.aptitude = HiredVillagerRoles.roleScore(VillagerProfileSavedData.get(level).get(this.id).skills(), this.role);
        }
    }

    void status(String status) {
        this.status = status;
        if (!status.equals("resting") && !status.equals("unsafe")) {
            this.serviceBlocked = switch (status) {
                case "missing_hoe", "missing_rod", "missing_axe", "missing_hunting_weapon",
                        "no_crafting_table", "no_furnace", "no_brewing_stand", "no_filter",
                        "no_observed_field", "no_observed_fishery", "no_observed_forest" -> true;
                default -> false;
            };
        }
    }

    boolean supportsService() {
        return !this.unsafe && !this.serviceBlocked;
    }

    NonNullList<ItemStack> items(HolderLookup.Provider provider) {
        var items = NonNullList.withSize(HiredJobInventory.SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(this.inventory, items, provider);
        return items;
    }

    void updateInventory(HolderLookup.Provider provider, NonNullList<ItemStack> items) {
        ContainerHelper.saveAllItems(this.inventory, items, true, provider);
    }

    List<Integer> outputSlots() {
        return slots(HiredJobInventorySlotType.OUTPUT);
    }

    List<Integer> supplySlots() {
        return slots(HiredJobInventorySlotType.SUPPLY);
    }

    private List<Integer> slots(HiredJobInventorySlotType type) {
        boolean[] outputs = new boolean[HiredJobInventory.SLOT_COUNT];
        if (type == HiredJobInventorySlotType.OUTPUT) {
            java.util.Arrays.fill(outputs, HiredJobInventory.MAIN_GRID_START, HiredJobInventory.HOTBAR_START, true);
        } else if (type == HiredJobInventorySlotType.SUPPLY) {
            java.util.Arrays.fill(outputs, HiredJobInventory.HOTBAR_START, HiredJobInventory.FILTER_SLOT, true);
        }
        for (Tag raw : this.inventory.getList("SlotTypes", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            int slot = tag.getInt("Slot");
            if (slot >= HiredJobInventory.MAIN_GRID_START && slot < HiredJobInventory.FILTER_SLOT) {
                outputs[slot] = type.id().equals(tag.getString("Type"));
            }
        }
        List<Integer> result = new ArrayList<>();
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            if (outputs[slot]) {
                result.add(slot);
            }
        }
        return List.copyOf(result);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Area", this.area);
        if (this.huntingTarget != null) tag.putUUID("HuntingTarget", this.huntingTarget);
        tag.putBoolean("HuntingEngaged", this.huntingEngaged);
        tag.putLong("HuntingCombatAt", this.huntingCombatAt);
        tag.putLong("HuntingAttackAt", this.huntingAttackAt);
        if (this.huntingBody != null) tag.put("HuntingBody", this.huntingBody.save());
        tag.putString("Role", this.role.name());
        tag.putLong("Position", this.position.asLong());
        tag.put("Inventory", this.inventory.copy());
        tag.put("Settings", this.settings.copy());
        if (!this.entityContext.isEmpty()) tag.put("EntityContext", this.entityContext.copy());
        if (this.miningAccess != null) tag.put("MiningAccess", this.miningAccess.save());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putLong("BrewingVersion", this.brewingVersion);
        tag.putDouble("Progress", this.progress);
        tag.putInt("Aptitude", this.aptitude);
        tag.putBoolean("Unsafe", this.unsafe);
        tag.putBoolean("ServiceBlocked", this.serviceBlocked);
        tag.putString("Status", this.status);
        return tag;
    }

    static SettlementWorkerState load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area")) {
            throw new IllegalArgumentException("Background worker requires a resident and workplace");
        }
        var state = new SettlementWorkerState(tag.getUUID("Id"));
        state.area = tag.getUUID("Area");
        state.huntingTarget = tag.hasUUID("HuntingTarget") ? tag.getUUID("HuntingTarget") : null;
        state.huntingEngaged = state.huntingTarget != null && tag.getBoolean("HuntingEngaged");
        state.huntingCombatAt = tag.contains("HuntingCombatAt") ? tag.getLong("HuntingCombatAt") : tag.getLong("CheckedAt");
        state.huntingAttackAt = Math.max(0L, tag.getLong("HuntingAttackAt"));
        state.huntingBody = tag.contains("HuntingBody", Tag.TAG_COMPOUND) ? SettlementHuntingTarget.load(tag.getCompound("HuntingBody")) : null;
        if (state.huntingBody != null && !state.huntingBody.id().equals(state.id)) {
            throw new IllegalArgumentException("Hunter combat state has a different worker identity");
        }
        state.role = HiredVillagerRole.valueOf(tag.getString("Role"));
        state.position = BlockPos.of(tag.getLong("Position"));
        state.inventory = tag.getCompound("Inventory").copy();
        state.settings = tag.getCompound("Settings").copy();
        state.entityContext = tag.getCompound("EntityContext").copy();
        state.miningAccess = SettlementMiningAccess.load(tag.getCompound("MiningAccess"));
        state.checkedAt = Math.max(0L, tag.getLong("CheckedAt"));
        state.version = Math.max(0L, tag.getLong("Version"));
        state.brewingVersion = Math.max(0L, tag.getLong("BrewingVersion"));
        state.progress = Math.clamp(tag.getDouble("Progress"), 0.0D, 24_000.0D);
        state.aptitude = Math.clamp(tag.getInt("Aptitude"), 0, 100);
        state.unsafe = tag.getBoolean("Unsafe");
        state.serviceBlocked = tag.getBoolean("ServiceBlocked");
        state.status = tag.getString("Status");
        return state;
    }
}
