package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class DuelEquipment {
    private static final String RECOVERY_TAG = "VillagerRetaliationDuelRecovery";
    private static final int RECOVERY_VERSION = 1;
    private static final int PLAYER_HOTBAR_SIZE = 9;

    private DuelEquipment() {}

    static Snapshots prepare(ServerPlayer player, Villager villager, DuelLoadout loadout) {
        Snapshots snapshots = new Snapshots(PlayerSnapshot.capture(player), VillagerSnapshot.capture(villager));
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.getFoodData().setExhaustion(0.0F);
        villager.removeAllEffects();
        villager.setHealth(villager.getMaxHealth());
        villager.setAbsorptionAmount(0.0F);
        VillagerRecoveryService.prepareForDuel(villager);
        villager.setCanPickUpLoot(false);
        if (loadout == DuelLoadout.BRING_YOUR_OWN) return snapshots;
        clear(player.getInventory());
        player.getInventory().selected = 0;
        clear(villager);
        switch (loadout) {
            case BARE_HANDED -> {}
            case MELEE -> melee(player, villager);
            case RANGED -> ranged(player, villager);
            case ARMORED -> armored(player, villager);
            case BRING_YOUR_OWN -> {}
        }
        return snapshots;
    }

    static void persistRecovery(ServerPlayer player, Villager villager, UUID duelId, DuelLoadout loadout,
                                int stake, Snapshots snapshots) {
        HolderLookup.Provider provider = player.registryAccess();
        player.getPersistentData().put(RECOVERY_TAG,
                recoveryTag(duelId, loadout, stake, snapshots.player().save(provider)));
        villager.getPersistentData().put(RECOVERY_TAG,
                recoveryTag(duelId, loadout, stake, snapshots.villager().save(provider)));
    }

    static PlayerRecovery playerRecovery(ServerPlayer player) {
        CompoundTag root = recoveryTag(player);
        if (root == null) return null;
        DuelLoadout loadout = loadout(root);
        PlayerSnapshot snapshot = PlayerSnapshot.load(player, root.getCompound("Snapshot"));
        return loadout == null || snapshot == null ? null
                : new PlayerRecovery(root.getUUID("Duel"), loadout, Math.max(0, root.getInt("Stake")), snapshot);
    }

    static VillagerRecovery villagerRecovery(Villager villager) {
        CompoundTag root = recoveryTag(villager);
        if (root == null) return null;
        DuelLoadout loadout = loadout(root);
        VillagerSnapshot snapshot = VillagerSnapshot.load(villager, root.getCompound("Snapshot"));
        return loadout == null || snapshot == null ? null
                : new VillagerRecovery(root.getUUID("Duel"), loadout, Math.max(0, root.getInt("Stake")), snapshot);
    }

    static void copyRecovery(Entity original, Entity replacement) {
        CompoundTag root = recoveryTag(original);
        if (root != null && replacement != null) {
            replacement.getPersistentData().put(RECOVERY_TAG, root.copy());
        }
    }

    static void clearRecovery(Entity entity, UUID duelId) {
        CompoundTag root = recoveryTag(entity);
        if (root != null && root.getUUID("Duel").equals(duelId)) {
            entity.getPersistentData().remove(RECOVERY_TAG);
        }
    }

    private static CompoundTag recoveryTag(UUID duelId, DuelLoadout loadout, int stake, CompoundTag snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", RECOVERY_VERSION);
        root.putUUID("Duel", duelId);
        root.putString("Loadout", loadout.name());
        root.putInt("Stake", Math.max(0, stake));
        root.put("Snapshot", snapshot);
        return root;
    }

    private static CompoundTag recoveryTag(Entity entity) {
        if (entity == null || !entity.getPersistentData().contains(RECOVERY_TAG, Tag.TAG_COMPOUND)) return null;
        CompoundTag root = entity.getPersistentData().getCompound(RECOVERY_TAG);
        return root.getInt("Version") == RECOVERY_VERSION
                && root.hasUUID("Duel")
                && root.contains("Snapshot", Tag.TAG_COMPOUND) ? root : null;
    }

    private static DuelLoadout loadout(CompoundTag root) {
        try {
            return DuelLoadout.valueOf(root.getString("Loadout"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void clear(Inventory inventory) {
        inventory.items.replaceAll(ignored -> ItemStack.EMPTY);
        inventory.armor.replaceAll(ignored -> ItemStack.EMPTY);
        inventory.offhand.replaceAll(ignored -> ItemStack.EMPTY);
        inventory.setChanged();
    }

    private static void clear(Villager villager) {
        VillagerInventoryAccess.replaceFullInventory(villager, List.of());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            villager.setItemSlot(slot, ItemStack.EMPTY);
        }
        VillagerRetaliationVillagerEquipment.restoreOwnershipState(villager, new CompoundTag());
    }

    private static void melee(ServerPlayer player, Villager villager) {
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        villager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }

    private static void ranged(ServerPlayer player, Villager villager) {
        player.getInventory().setItem(0, new ItemStack(Items.BOW));
        player.getInventory().setItem(1, new ItemStack(Items.ARROW, 64));
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.ARROW, 64));
    }

    private static void armored(ServerPlayer player, Villager villager) {
        melee(player, villager);
        player.getInventory().setItem(1, new ItemStack(Items.IRON_AXE));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.IRON_AXE));
        equip(player, villager, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        equip(player, villager, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        equip(player, villager, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        equip(player, villager, EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }

    private static void equip(ServerPlayer player, Villager villager, EquipmentSlot slot, ItemStack stack) {
        player.setItemSlot(slot, stack.copy());
        villager.setItemSlot(slot, stack.copy());
    }

    record Snapshots(PlayerSnapshot player, VillagerSnapshot villager) {}

    record PlayerSnapshot(List<ItemStack> items, List<ItemStack> armor, List<ItemStack> offhand,
                          int selectedSlot, float health, float absorption, CompoundTag foodData,
                          List<MobEffectInstance> effects) {
        static PlayerSnapshot capture(ServerPlayer player) {
            CompoundTag foodData = new CompoundTag();
            player.getFoodData().addAdditionalSaveData(foodData);
            return new PlayerSnapshot(copy(player.getInventory().items), copy(player.getInventory().armor),
                    copy(player.getInventory().offhand), player.getInventory().selected, player.getHealth(), player.getAbsorptionAmount(),
                    foodData,
                    player.getActiveEffects().stream().map(MobEffectInstance::new).toList());
        }

        void restore(ServerPlayer player, boolean restoreInventory) {
            if (restoreInventory) {
                restoreList(player.getInventory().items, this.items);
                restoreList(player.getInventory().armor, this.armor);
                restoreList(player.getInventory().offhand, this.offhand);
                player.getInventory().selected = this.selectedSlot;
            }
            player.getInventory().setChanged();
            player.removeAllEffects();
            this.effects.forEach(effect -> player.addEffect(new MobEffectInstance(effect)));
            player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0F, this.health)));
            player.setAbsorptionAmount(this.absorption);
            player.getFoodData().readAdditionalSaveData(this.foodData.copy());
        }

        CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.put("Items", saveStacks(this.items, provider));
            tag.put("Armor", saveStacks(this.armor, provider));
            tag.put("Offhand", saveStacks(this.offhand, provider));
            tag.putInt("Selected", this.selectedSlot);
            tag.putFloat("Health", this.health);
            tag.putFloat("Absorption", this.absorption);
            tag.put("Food", this.foodData.copy());
            tag.put("Effects", saveEffects(this.effects));
            return tag;
        }

        static PlayerSnapshot load(ServerPlayer player, CompoundTag tag) {
            if (!tag.contains("Items", Tag.TAG_LIST)
                    || !tag.contains("Armor", Tag.TAG_LIST)
                    || !tag.contains("Offhand", Tag.TAG_LIST)
                    || !tag.contains("Selected", Tag.TAG_INT)
                    || !tag.contains("Health", Tag.TAG_FLOAT)
                    || !tag.contains("Absorption", Tag.TAG_FLOAT)
                    || !tag.contains("Food", Tag.TAG_COMPOUND)
                    || !tag.contains("Effects", Tag.TAG_LIST)) {
                return null;
            }
            HolderLookup.Provider provider = player.registryAccess();
            List<ItemStack> items = loadStacksExact(
                    tag, "Items", provider, player.getInventory().items.size());
            List<ItemStack> armor = loadStacksExact(
                    tag, "Armor", provider, player.getInventory().armor.size());
            List<ItemStack> offhand = loadStacksExact(
                    tag, "Offhand", provider, player.getInventory().offhand.size());
            int selected = tag.getInt("Selected");
            if (items == null || armor == null || offhand == null
                    || selected < 0 || selected >= PLAYER_HOTBAR_SIZE) {
                return null;
            }
            return new PlayerSnapshot(
                    items,
                    armor,
                    offhand,
                    selected,
                    tag.getFloat("Health"),
                    tag.getFloat("Absorption"),
                    tag.getCompound("Food").copy(),
                    loadEffects(tag, "Effects"));
        }
    }

    record VillagerSnapshot(List<ItemStack> inventory, Map<EquipmentSlot, ItemStack> equipment,
                            CompoundTag equipmentOwnership,
                            float health, float absorption, List<MobEffectInstance> effects, boolean pickup,
                            VillagerRecoveryService.RecoverySnapshot recovery) {
        static VillagerSnapshot capture(Villager villager) {
            List<ItemStack> inventory = VillagerInventoryAccess.captureFullInventory(villager);
            Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : EquipmentSlot.values()) equipment.put(slot, villager.getItemBySlot(slot).copy());
            return new VillagerSnapshot(List.copyOf(inventory), equipment,
                    VillagerRetaliationVillagerEquipment.captureOwnershipState(villager), villager.getHealth(),
                    villager.getAbsorptionAmount(), villager.getActiveEffects().stream().map(MobEffectInstance::new).toList(),
                    villager.canPickUpLoot(), VillagerRecoveryService.captureRecoveryState(villager));
        }

        void restore(Villager villager) {
            VillagerInventoryAccess.replaceFullInventory(villager, this.inventory);
            this.equipment.forEach((slot, stack) -> villager.setItemSlot(slot, stack.copy()));
            VillagerRetaliationVillagerEquipment.restoreOwnershipState(villager, this.equipmentOwnership);
            villager.removeAllEffects();
            this.effects.forEach(effect -> villager.addEffect(new MobEffectInstance(effect)));
            villager.setHealth(Math.min(villager.getMaxHealth(), Math.max(1.0F, this.health)));
            villager.setAbsorptionAmount(this.absorption);
            villager.setCanPickUpLoot(this.pickup);
            VillagerRecoveryService.restoreRecoveryState(villager, this.recovery);
        }

        CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.put("Inventory", saveStacks(this.inventory, provider));
            CompoundTag equipmentTag = new CompoundTag();
            this.equipment.forEach((slot, stack) ->
                    equipmentTag.put(slot.getName(), stack.saveOptional(provider)));
            tag.put("Equipment", equipmentTag);
            tag.put("EquipmentOwnership", this.equipmentOwnership.copy());
            tag.putFloat("Health", this.health);
            tag.putFloat("Absorption", this.absorption);
            tag.put("Effects", saveEffects(this.effects));
            tag.putBoolean("Pickup", this.pickup);
            CompoundTag recoveryTag = new CompoundTag();
            recoveryTag.putInt("Food", this.recovery.food());
            recoveryTag.putFloat("Saturation", this.recovery.saturation());
            recoveryTag.putFloat("Exhaustion", this.recovery.exhaustion());
            recoveryTag.putInt("HealTimer", this.recovery.healTimer());
            tag.put("Recovery", recoveryTag);
            return tag;
        }

        static VillagerSnapshot load(Villager villager, CompoundTag tag) {
            if (!tag.contains("Inventory", Tag.TAG_LIST)
                    || !tag.contains("Equipment", Tag.TAG_COMPOUND)
                    || !tag.contains("EquipmentOwnership", Tag.TAG_COMPOUND)
                    || !tag.contains("Health", Tag.TAG_FLOAT)
                    || !tag.contains("Absorption", Tag.TAG_FLOAT)
                    || !tag.contains("Effects", Tag.TAG_LIST)
                    || !tag.contains("Pickup", Tag.TAG_BYTE)
                    || !tag.contains("Recovery", Tag.TAG_COMPOUND)) {
                return null;
            }
            HolderLookup.Provider provider = villager.registryAccess();
            List<ItemStack> inventory = loadStacksExact(
                    tag, "Inventory", provider,
                    VillagerInventoryAccess.captureFullInventory(villager).size());
            if (inventory == null) return null;
            CompoundTag equipmentTag = tag.getCompound("Equipment");
            Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!equipmentTag.contains(slot.getName(), Tag.TAG_COMPOUND)) return null;
                equipment.put(slot, ItemStack.parseOptional(
                        provider, equipmentTag.getCompound(slot.getName())));
            }
            CompoundTag recoveryTag = tag.getCompound("Recovery");
            if (!recoveryTag.contains("Food", Tag.TAG_INT)
                    || !recoveryTag.contains("Saturation", Tag.TAG_FLOAT)
                    || !recoveryTag.contains("Exhaustion", Tag.TAG_FLOAT)
                    || !recoveryTag.contains("HealTimer", Tag.TAG_INT)) {
                return null;
            }
            return new VillagerSnapshot(
                    inventory,
                    equipment,
                    tag.getCompound("EquipmentOwnership").copy(),
                    tag.getFloat("Health"),
                    tag.getFloat("Absorption"),
                    loadEffects(tag, "Effects"),
                    tag.getBoolean("Pickup"),
                    new VillagerRecoveryService.RecoverySnapshot(
                            recoveryTag.getInt("Food"),
                            recoveryTag.getFloat("Saturation"),
                            recoveryTag.getFloat("Exhaustion"),
                            recoveryTag.getInt("HealTimer")));
        }
    }

    record PlayerRecovery(UUID duelId, DuelLoadout loadout, int stake, PlayerSnapshot snapshot) {}
    record VillagerRecovery(UUID duelId, DuelLoadout loadout, int stake, VillagerSnapshot snapshot) {}

    private static ListTag saveStacks(List<ItemStack> stacks, HolderLookup.Provider provider) {
        ListTag saved = new ListTag();
        for (ItemStack stack : stacks) saved.add(stack.saveOptional(provider));
        return saved;
    }

    private static List<ItemStack> loadStacksExact(
            CompoundTag tag, String key, HolderLookup.Provider provider, int expectedSize) {
        ListTag saved = tag.getList(key, Tag.TAG_COMPOUND);
        if (saved.size() != expectedSize) return null;
        return saved.stream()
                .map(raw -> ItemStack.parseOptional(provider, (CompoundTag) raw))
                .toList();
    }

    private static ListTag saveEffects(List<MobEffectInstance> effects) {
        ListTag saved = new ListTag();
        for (MobEffectInstance effect : effects) saved.add(effect.save());
        return saved;
    }

    private static List<MobEffectInstance> loadEffects(CompoundTag tag, String key) {
        return tag.getList(key, Tag.TAG_COMPOUND).stream()
                .map(raw -> MobEffectInstance.load((CompoundTag) raw))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static List<ItemStack> copy(List<ItemStack> source) {
        return source.stream().map(ItemStack::copy).toList();
    }

    private static void restoreList(List<ItemStack> target, List<ItemStack> source) {
        for (int i = 0; i < target.size(); i++) {
            target.set(i, i < source.size() ? source.get(i).copy() : ItemStack.EMPTY);
        }
    }
}
