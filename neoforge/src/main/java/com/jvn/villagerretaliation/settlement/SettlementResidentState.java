package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

public final class SettlementResidentState {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementCareVersion";
    private final UUID id;
    private BlockPos position;
    private long checkedAt;
    private long version;
    private long shortageTicks;
    private int food = 20;
    private float saturation = 5.0F;
    private float exhaustion;
    private int healTimer;
    private float health = 20.0F;
    private float maxHealth = 20.0F;
    private int age;
    private int hungerTicks;
    private int hungerAmplifier;
    private boolean present;
    private boolean downed;
    private boolean personalCommitment;
    private boolean familyUnsafe;
    private CompoundTag entityContext = new CompoundTag();
    private SettlementFamilyInventory familyInventory;
    private SettlementMendingEquipment mendingEquipment;

    private SettlementResidentState(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return this.id;
    }

    public BlockPos position() {
        return this.position;
    }

    public long checkedAt() {
        return this.checkedAt;
    }

    public long version() {
        return this.version;
    }

    public long shortageTicks() {
        return this.shortageTicks;
    }

    public int food() {
        return this.food;
    }

    public float health() {
        return this.health;
    }

    public int age() {
        return this.age;
    }

    public boolean present() {
        return this.present;
    }

    public boolean availableForWork() {
        return canDepart() && this.age >= 0;
    }

    public boolean canDepart() {
        return this.present && !this.downed && !this.personalCommitment;
    }

    boolean safeForFamily() {
        return canDepart() && !this.familyUnsafe;
    }

    public double workMultiplier() {
        return this.food <= 0 ? 0.4D : this.food < 6 ? 0.7D : 1.0D;
    }

    static SettlementResidentState capture(Villager villager, SettlementRecord town, long now) {
        var state = new SettlementResidentState(villager.getUUID());
        state.observe(villager, town, now);
        return state;
    }

    void observe(Villager villager, SettlementRecord town, long now) {
        restoreMending(villager);
        if (this.familyInventory != null) this.familyInventory.restore(villager);
        var snapshot = VillagerRecoveryService.captureRecoveryState(villager);
        this.position = villager.blockPosition().immutable();
        this.checkedAt = now;
        this.version = Math.max(this.version, villager.getPersistentData().getLong(VERSION_TAG));
        this.food = snapshot.food();
        this.saturation = snapshot.saturation();
        this.exhaustion = snapshot.exhaustion();
        this.healTimer = snapshot.healTimer();
        this.health = villager.getHealth();
        this.maxHealth = villager.getMaxHealth();
        this.age = villager.getAge();
        var hunger = villager.getEffect(MobEffects.HUNGER);
        this.hungerTicks = hunger == null ? 0 : hunger.isInfiniteDuration() ? Integer.MAX_VALUE : hunger.getDuration();
        this.hungerAmplifier = hunger == null ? 0 : hunger.getAmplifier();
        int radius = town.stage().radius() + 16;
        this.present = villager.level().dimension().location().equals(town.dimension())
                && Math.abs(villager.getX() - town.center().getX()) <= radius
                && Math.abs(villager.getZ() - town.center().getZ()) <= radius;
        this.downed = VillagerDownedService.isDowned(villager);
        this.personalCommitment = SettlementWorkforceService.hasPersonalCommitment(villager);
        this.familyUnsafe = villager.getTarget() != null || villager.getLastHurtByMob() != null || villager.isOnFire() || villager.isInLava()
                || com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState((ServerLevel) villager.level(), villager.getBrain());
        rememberFamily(villager);
    }

    void combat(Villager villager) {
        this.position = villager.blockPosition().immutable();
        this.health = Math.max(1.0F, villager.getHealth());
        this.maxHealth = villager.getMaxHealth();
        this.downed = VillagerDownedService.isDowned(villager);
        this.familyUnsafe = villager.getLastHurtByMob() != null || villager.isOnFire() || this.downed;
        this.version++;
        villager.getPersistentData().putLong(VERSION_TAG, this.version);
        this.entityContext = villager.saveWithoutId(new CompoundTag());
    }

    void mend(Villager villager, java.util.Map<net.minecraft.world.entity.EquipmentSlot, ItemStack> before) {
        this.mendingEquipment = SettlementMendingEquipment.capture(villager, this.mendingEquipment, before);
        remember(villager);
    }

    private boolean restoreMending(Villager villager) {
        return this.mendingEquipment != null && this.mendingEquipment.restore(villager);
    }

    void remember(Villager villager) {
        if (!this.id.equals(villager.getUUID())) throw new IllegalArgumentException("Resident context has a different identity");
        if (this.familyInventory != null) this.familyInventory.restore(villager);
        rememberFamily(villager);
    }

    void rememberFamily(Villager villager) {
        if (!this.id.equals(villager.getUUID())) throw new IllegalArgumentException("Resident context has a different identity");
        this.familyInventory = SettlementFamilyInventory.capture(villager, this.familyInventory);
        this.entityContext = villager.saveWithoutId(new CompoundTag());
    }

    void completeFamilyAttempt(Villager villager) {
        rememberFamily(villager);
        if (this.age != villager.getAge()) {
            this.age = villager.getAge();
            this.version++;
            villager.getPersistentData().putLong(VERSION_TAG, this.version);
        }
    }

    Villager context(ServerLevel level) {
        if (this.entityContext.isEmpty()) return null;
        var villager = EntityType.VILLAGER.create(level);
        if (villager == null) return null;
        villager.load(this.entityContext.copy());
        restoreContext(villager);
        return villager;
    }

    void restoreContext(Villager villager) {
        if (!this.id.equals(villager.getUUID())) throw new IllegalArgumentException("Resident context has a different identity");
        if (this.familyInventory != null) this.familyInventory.copyTo(villager);
        restore(villager);
    }

    List<HiredVillagerRole> availableRoles(ServerLevel level) {
        if (!availableForWork() || this.entityContext.isEmpty()) return List.of();
        var profession = VillagerProfessionUtil.parse(this.entityContext.getCompound("VillagerData").getString("profession"));
        if (profession.isEmpty()) return List.of();
        var key = VillagerProfessionSkills.professionKey(profession.get());
        var profile = VillagerProfileSavedData.get(level).get(this.id);
        var skills = profile == null ? VillagerSkillSet.EMPTY : profile.skills();
        return Arrays.stream(HiredVillagerRole.values())
                .filter(role -> HiredVillagerRoles.isSkillUnlocked(key, false, skills, role)).toList();
    }

    boolean restore(Villager villager) {
        boolean familyRestored = this.familyInventory != null && this.familyInventory.restore(villager);
        boolean mendingRestored = restoreMending(villager);
        if (villager.getPersistentData().getLong(VERSION_TAG) >= this.version) {
            return familyRestored || mendingRestored;
        }
        VillagerRecoveryService.restoreRecoveryState(villager,
                new VillagerRecoveryService.RecoverySnapshot(this.food, this.saturation, this.exhaustion, this.healTimer));
        villager.setHealth(Math.clamp(this.health, 1.0F, villager.getMaxHealth()));
        villager.setAge(this.age);
        var hunger = villager.getEffect(MobEffects.HUNGER);
        if (hunger != null && this.hungerTicks == 0) {
            villager.removeEffect(MobEffects.HUNGER);
        } else if (hunger != null && !hunger.isInfiniteDuration()) {
            villager.removeEffect(MobEffects.HUNGER);
            villager.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.HUNGER,
                    this.hungerTicks, this.hungerAmplifier, hunger.isAmbient(), hunger.isVisible(), hunger.showIcon()));
        }
        villager.getPersistentData().putLong(VERSION_TAG, this.version);
        SettlementFoodService.resetMetabolism(villager);
        return true;
    }

    int advance(ServerLevel level, SettlementRecord town, long now) {
        int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
        this.checkedAt = now;
        if (!this.present || elapsed == 0) {
            return 0;
        }
        for (int tick = 0; tick < elapsed; tick++) {
            if (this.age < 0) {
                this.age++;
            } else if (this.age > 0) {
                this.age--;
            }
            if (this.downed) {
                continue;
            }
            this.exhaustion += SettlementFoodService.dailyNutrition() * 4.0F / 24_000.0F;
            if (this.hungerTicks > 0) {
                if (VillagerRetaliationConfig.HUNGER_EFFECT_AFFECTS_VILLAGERS.get()) {
                    this.exhaustion += 0.005F * (this.hungerAmplifier + 1);
                }
                if (this.hungerTicks < Integer.MAX_VALUE) {
                    this.hungerTicks--;
                }
            }
            while (this.exhaustion > 4.0F) {
                this.exhaustion -= 4.0F;
                if (this.saturation > 0.0F) {
                    this.saturation = Math.max(0.0F, this.saturation - 1.0F);
                } else {
                    this.food = Math.max(0, this.food - 1);
                }
            }
            regenerate(level);
            if (this.food < 10 && tick % 32 == 0) {
                eat(town);
            }
        }
        this.version++;
        return elapsed;
    }

    void observeShortage(int elapsed, boolean housingShortage) {
        if (!this.present) {
            return;
        }
        this.shortageTicks = this.food < 6 || housingShortage
                ? Math.min(30L * 24_000L, this.shortageTicks + elapsed)
                : Math.max(0L, this.shortageTicks - elapsed * 2L);
    }

    private void eat(SettlementRecord town) {
        ItemStack meal = SettlementStock.take(town, stack -> {
            if (!SettlementFoodService.isMeal(stack)) {
                return false;
            }
            ItemStack remainder = remainder(stack.get(DataComponents.FOOD));
            return remainder.isEmpty() || SettlementStock.capacity(town, remainder) >= remainder.getCount();
        }, 1);
        if (meal.isEmpty()) {
            return;
        }
        FoodProperties properties = meal.get(DataComponents.FOOD);
        this.food = Math.min(20, this.food + properties.nutrition());
        this.saturation = Math.min(this.food, this.saturation + properties.saturation());
        ItemStack remainder = remainder(properties);
        if (!remainder.isEmpty()) {
            SettlementStock.deposit(town, remainder);
        }
    }

    private static ItemStack remainder(FoodProperties properties) {
        return properties.usingConvertsTo().map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    private void regenerate(ServerLevel level) {
        if (this.food <= 0) {
            if (!VillagerRetaliationConfig.ENABLE_VILLAGER_STARVATION_DAMAGE.get()) {
                this.healTimer = 0;
            } else if (++this.healTimer >= 80) {
                Difficulty difficulty = level.getDifficulty();
                if (this.health > 10.0F || difficulty == Difficulty.HARD
                        || this.health > 1.0F && difficulty == Difficulty.NORMAL) {
                    this.health = Math.max(1.0F, this.health - 1.0F);
                }
                this.healTimer = 0;
            }
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION) || this.health >= this.maxHealth) {
            this.healTimer = 0;
            return;
        }
        this.healTimer++;
        if (this.saturation > 0.0F && this.healTimer >= 10) {
            float healed = Math.min(this.saturation, 6.0F) / 6.0F;
            this.health = Math.min(this.maxHealth, this.health + healed);
            this.exhaustion += healed * 6.0F;
            this.healTimer = 0;
        } else if (this.saturation <= 0.0F && this.healTimer >= 80) {
            this.health = Math.min(this.maxHealth, this.health + 1.0F);
            this.exhaustion += 6.0F;
            this.healTimer = 0;
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putLong("Position", this.position.asLong());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putLong("ShortageTicks", this.shortageTicks);
        tag.putInt("Food", this.food);
        tag.putFloat("Saturation", this.saturation);
        tag.putFloat("Exhaustion", this.exhaustion);
        tag.putInt("HealTimer", this.healTimer);
        tag.putFloat("Health", this.health);
        tag.putFloat("MaxHealth", this.maxHealth);
        tag.putInt("Age", this.age);
        tag.putInt("HungerTicks", this.hungerTicks);
        tag.putInt("HungerAmplifier", this.hungerAmplifier);
        tag.putBoolean("Present", this.present);
        tag.putBoolean("Downed", this.downed);
        tag.putBoolean("PersonalCommitment", this.personalCommitment);
        tag.putBoolean("FamilyUnsafe", this.familyUnsafe);
        if (!this.entityContext.isEmpty()) tag.put("EntityContext", this.entityContext.copy());
        if (this.familyInventory != null) tag.put("FamilyInventory", this.familyInventory.save());
        if (this.mendingEquipment != null) tag.put("MendingEquipment", this.mendingEquipment.save());
        return tag;
    }

    static SettlementResidentState load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) {
            throw new IllegalArgumentException("Resident care is missing its identity");
        }
        var state = new SettlementResidentState(tag.getUUID("Id"));
        state.position = BlockPos.of(tag.getLong("Position"));
        state.checkedAt = Math.max(0L, tag.getLong("CheckedAt"));
        state.version = Math.max(0L, tag.getLong("Version"));
        state.shortageTicks = Math.clamp(tag.getLong("ShortageTicks"), 0L, 30L * 24_000L);
        state.food = Math.clamp(tag.getInt("Food"), 0, 20);
        state.saturation = Math.clamp(tag.getFloat("Saturation"), 0.0F, state.food);
        state.exhaustion = Math.clamp(tag.getFloat("Exhaustion"), 0.0F, 40.0F);
        state.healTimer = Math.clamp(tag.getInt("HealTimer"), 0, 80);
        state.maxHealth = Math.max(1.0F, tag.getFloat("MaxHealth"));
        state.health = Math.clamp(tag.getFloat("Health"), 1.0F, state.maxHealth);
        state.age = tag.getInt("Age");
        state.hungerTicks = Math.max(0, tag.getInt("HungerTicks"));
        state.hungerAmplifier = Math.clamp(tag.getInt("HungerAmplifier"), 0, 255);
        state.present = tag.getBoolean("Present");
        state.downed = tag.getBoolean("Downed");
        state.personalCommitment = tag.getBoolean("PersonalCommitment");
        state.familyUnsafe = tag.getBoolean("FamilyUnsafe");
        state.entityContext = tag.getCompound("EntityContext").copy();
        if (tag.contains("FamilyInventory")) state.familyInventory = SettlementFamilyInventory.load(tag.getCompound("FamilyInventory"));
        if (tag.contains("MendingEquipment")) state.mendingEquipment = SettlementMendingEquipment.load(tag.getCompound("MendingEquipment"));
        if (!state.entityContext.isEmpty() && (!state.entityContext.hasUUID("UUID") || !state.id.equals(state.entityContext.getUUID("UUID")))) {
            throw new IllegalArgumentException("Resident context has a different identity");
        }
        return state;
    }
}
