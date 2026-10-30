package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredJobSetting;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public record HiredEquipmentNeed(Kind kind, BlockState target) implements Predicate<ItemStack> {
    private static final String STATE_TAG = "EquipmentTaskNeed";

    public HiredEquipmentNeed {
        Objects.requireNonNull(kind);
        if (kind.blockTarget() != (target != null) || target != null && target.isAir())
            throw new IllegalArgumentException("Equipment requirement has an invalid task target");
    }

    public static HiredEquipmentNeed of(Kind kind) { return new HiredEquipmentNeed(kind, null); }

    public static HiredEquipmentNeed mining(HiredMiningMode mode, BlockState target) {
        return new HiredEquipmentNeed(mode.excavatesArea() ? Kind.EXCAVATION : Kind.MINING, target);
    }

    public static HiredEquipmentNeed clearing(BlockState target) { return new HiredEquipmentNeed(Kind.CLEARING, target); }

    public static HiredEquipmentNeed placement(BuilderStructureScanner.BuilderToolAction action) {
        return switch (action) {
            case AXE_STRIP -> of(Kind.STRIPPING);
            case SHOVEL_FLATTEN -> of(Kind.FLATTENING);
            case HOE_TILL -> of(Kind.TILLING);
            case SHOVEL_PAVE -> clearing(Blocks.DIRT.defaultBlockState());
            case PICKAXE_PAVE -> clearing(Blocks.COBBLESTONE.defaultBlockState());
            case NONE -> throw new IllegalArgumentException("Placement does not need equipment");
        };
    }

    @Override
    public boolean test(ItemStack stack) {
        if (!VillagerEquipmentDurability.isUsable(stack)) return false;
        return switch (this.kind) {
            case HOE -> FarmerHoeRequirement.isHoe(stack);
            case ROD -> FishingWorker.isFishingRod(stack);
            case AXE -> stack.is(ItemTags.AXES);
            case LEAVES -> LoggingWorker.isLeafHarvestTool(stack);
            case HUNTING -> HuntingWorker.isHuntingWeapon(stack);
            case CULLING -> AnimalBreedingWorker.isCullWeapon(stack);
            case SHEARS -> stack.is(Items.SHEARS);
            case PICKAXE -> stack.is(ItemTags.PICKAXES);
            case SHOVEL -> stack.is(ItemTags.SHOVELS);
            case WEAPON -> VillagerRetaliationVillagerWeapons.isUsableWeapon(stack);
            case MELEE -> VillagerRetaliationVillagerWeapons.isMeleeWeapon(stack);
            case BOW -> VillagerRetaliationVillagerWeapons.isBowWeapon(stack);
            case CROSSBOW -> VillagerRetaliationVillagerWeapons.isCrossbowWeapon(stack);
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> {
                var equipable = Equipable.get(stack);
                yield equipable != null && equipable.getEquipmentSlot() == this.kind.guardSlot();
            }
            case SHIELD -> stack.is(Items.SHIELD);
            case MINING -> MiningBlockRules.isUsableMiningTool(HiredMiningMode.EXPOSED_ORES, stack, this.target);
            case EXCAVATION -> MiningBlockRules.isUsableMiningTool(HiredMiningMode.EXCAVATE_AREA, stack, this.target);
            case CLEARING -> MiningBlockRules.isUsableBuilderClearingTool(stack, this.target);
            case STRIPPING -> BuilderStructureScanner.matchesToolAction(stack, BuilderStructureScanner.BuilderToolAction.AXE_STRIP);
            case FLATTENING -> BuilderStructureScanner.matchesToolAction(stack, BuilderStructureScanner.BuilderToolAction.SHOVEL_FLATTEN);
            case TILLING -> BuilderStructureScanner.matchesToolAction(stack, BuilderStructureScanner.BuilderToolAction.HOE_TILL);
            case AMMUNITION -> HiredRangedAmmo.isAmmo(stack);
        };
    }

    public WorkSupplyRequirement requirement(long quantity) {
        var alternatives = BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(this).limit(64).toList();
        String key = "equipment_" + this.kind.name().toLowerCase(Locale.ROOT);
        return new WorkSupplyRequirement(key, alternatives, quantity, WorkSupplyRequirement.Unit.ITEMS, this, null);
    }

    public void remember(CompoundTag state) {
        if (!this.kind.taskSpecific()) return;
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putString("Kind", this.kind.name());
        if (this.target != null) tag.put("Target", NbtUtils.writeBlockState(this.target));
        if (!tag.equals(state.getCompound(STATE_TAG))) state.put(STATE_TAG, tag);
    }

    public static void clear(CompoundTag state) { state.remove(STATE_TAG); }

    public static boolean supports(HiredVillagerRole role) {
        return role != null && switch (role) {
            case FARMING, FISHING, LOGGING, MINING, ANIMAL_HANDLING, HUNTING, BUILDER, COMBAT -> true;
            default -> false;
        };
    }

    public static List<HiredEquipmentNeed> required(HiredVillagerRole role, CompoundTag state) {
        var needs = new ArrayList<HiredEquipmentNeed>();
        if (role == null) return needs;
        switch (role) {
            case FARMING -> needs.add(of(Kind.HOE));
            case FISHING -> needs.add(of(Kind.ROD));
            case HUNTING -> needs.add(of(Kind.HUNTING));
            case LOGGING -> {
                needs.add(of(Kind.AXE));
                if (HiredJobSetting.HARVEST_LEAVES.read(state) != 0) needs.add(of(Kind.LEAVES));
            }
            case ANIMAL_HANDLING -> {
                if (HiredAnimalHandlingOptions.shearSheep(state)) needs.add(of(Kind.SHEARS));
                if (HiredAnimalHandlingOptions.cull(state) && HiredAnimalCullSettings.cap(state) > 0) needs.add(of(Kind.CULLING));
            }
            case MINING, BUILDER -> {
                var task = task(state, role);
                if (task != null) needs.add(task);
                else if (role == HiredVillagerRole.MINING) needs.add(of(Kind.PICKAXE));
            }
            default -> { }
        }
        return List.copyOf(needs);
    }

    private static HiredEquipmentNeed task(CompoundTag state, HiredVillagerRole role) {
        var tag = state.getCompound(STATE_TAG);
        if (tag.getInt("Version") != 1) return null;
        Kind kind;
        try { kind = Kind.valueOf(tag.getString("Kind")); }
        catch (IllegalArgumentException invalid) { return null; }
        if (!kind.taskSpecific() || (kind == Kind.MINING || kind == Kind.EXCAVATION)
                != (role == HiredVillagerRole.MINING)) return null;
        if (kind.blockTarget()) {
            if (!tag.contains("Target", Tag.TAG_COMPOUND)) return null;
            var target = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Target"));
            if (target.isAir()) return null;
            return role == HiredVillagerRole.MINING ? mining(HiredMiningMode.fromState(state), target) : clearing(target);
        }
        return of(kind);
    }

    public enum Kind {
        HOE, ROD, AXE, LEAVES, HUNTING, CULLING, SHEARS, PICKAXE, SHOVEL,
        WEAPON, MELEE, BOW, CROSSBOW, HELMET, CHESTPLATE, LEGGINGS, BOOTS, SHIELD,
        MINING, EXCAVATION, CLEARING, STRIPPING, FLATTENING, TILLING, AMMUNITION;

        public EquipmentSlot guardSlot() {
            return switch (this) {
                case WEAPON, MELEE, BOW, CROSSBOW -> EquipmentSlot.MAINHAND;
                case HELMET -> EquipmentSlot.HEAD;
                case CHESTPLATE -> EquipmentSlot.CHEST;
                case LEGGINGS -> EquipmentSlot.LEGS;
                case BOOTS -> EquipmentSlot.FEET;
                case SHIELD -> EquipmentSlot.OFFHAND;
                default -> null;
            };
        }

        public boolean reservable() { return !taskSpecific(); }

        public boolean reserveFor(HiredVillagerRole role) {
            return switch (role) {
                case FARMING -> this == HOE;
                case FISHING -> this == ROD;
                case LOGGING -> this == AXE || this == LEAVES;
                case MINING -> this == PICKAXE || this == SHOVEL || this == AXE;
                case BUILDER -> this == PICKAXE || this == SHOVEL || this == AXE || this == HOE;
                case ANIMAL_HANDLING -> this == SHEARS || this == CULLING;
                case HUNTING -> this == HUNTING || this == AMMUNITION;
                case COMBAT -> guardSlot() != null || this == AMMUNITION;
                default -> false;
            };
        }

        boolean blockTarget() { return this == MINING || this == EXCAVATION || this == CLEARING; }
        boolean taskSpecific() { return blockTarget() || this == STRIPPING || this == FLATTENING || this == TILLING; }
    }
}
