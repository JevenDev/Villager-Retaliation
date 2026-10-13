package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerDefensiveLoadoutService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

final class SettlementEquipmentDemand {
    private SettlementEquipmentDemand() { }

    record Need(HiredEquipmentNeed equipment, long amount, long essential, long reserve) {
        Need(HiredEquipmentNeed equipment, long amount, long essential) { this(equipment, amount, essential, 0); }
    }
    record Worker(UUID area, String name, List<BlockPos> sources, List<Need> needs, List<ItemStack> owned, int priority) { }

    static Worker describe(ServerLevel level, SettlementRecord town, UUID id) {
        var entity = level.getEntity(id);
        var saved = town.workers().get(id);
        Villager live = entity instanceof Villager villager && (saved == null || level.isPositionEntityTicking(villager.blockPosition())) ? villager : null;
        var area = town.workplace(town.duties().get(id));
        HiredVillagerRole role;
        CompoundTag state;
        List<BlockPos> sources;
        if (area != null) {
            role = area.type().role();
            if (!area.enabled() || role == null || live == null && (saved == null || !saved.area().equals(area.id()))) return null;
            state = live != null ? HiredWorkStateStore.state(live).copy() : saved.settings().copy();
            var settings = town.effectiveWorkerSettings(id);
            if (settings != null) settings.apply(state, area.enabled());
            sources = civicSources(level, town, id, area.id(), role);
        } else {
            if (live == null || !live.isAlive()) return null;
            var authority = VillagerWorkAuthority.resolve(level, live);
            if (!authority.active()) return null;
            role = authority.role();
            state = HiredWorkStateStore.state(live);
            sources = AssignedStorageSavedData.get(level).assignedTo(id).stream()
                    .filter(link -> link.dimension().equals(level.dimension())
                            && (link.purpose().equals(AssignedStorageService.SUPPLY_PURPOSE)
                            || link.purpose().equals(AssignedStorageService.GENERAL_PURPOSE)))
                    .map(link -> SettlementSupplyStock.positions(level, link.pos()).getFirst()).distinct().toList();
        }
        if (state.contains("Enabled") && !state.getBoolean("Enabled")) return null;
        if (state.contains("UseAssignedStorageForSupplies") && !state.getBoolean("UseAssignedStorageForSupplies")) sources = List.of();
        var plan = area == null ? null : town.equipmentPlans().get(area.id());
        var configuration = plan != null && plan.authorized(town) ? plan.configuration() : null;
        var needs = new ArrayList<Need>();
        if (role != HiredVillagerRole.BUILDER || area == null || town.assignedProject(id) != null)
            for (var requirement : HiredEquipmentNeed.required(role, state)) needs.add(new Need(requirement, 1, 1));
        if (configuration != null && role == HiredVillagerRole.COMBAT)
            for (var selection : configuration.selections()) if (selection.equip())
                needs.add(new Need(HiredEquipmentNeed.of(selection.kind()), 1,
                        selection.kind().guardSlot() == net.minecraft.world.entity.EquipmentSlot.MAINHAND || selection.protectedMinimum() ? 1 : 0));
        if (needs.isEmpty() && role != HiredVillagerRole.COMBAT) return null;
        var owned = new ArrayList<ItemStack>();
        var inventory = live == null ? null : HiredJobInventory.getJobInventory(live);
        var items = live == null ? saved.items(level.registryAccess()) : null;
        for (int slot = 0; slot <= HiredJobInventory.OFFHAND_SLOT; slot++)
            owned.add((live == null ? items.get(slot) : inventory.getItem(slot)).copy());
        ItemStack weapon = owned.get(HiredJobInventory.MAINHAND_SLOT);
        if (role == HiredVillagerRole.HUNTING || role == HiredVillagerRole.COMBAT) {
            Villager owner = live == null ? saved.context(level, town) : live;
            if (owner != null) {
                if (role == HiredVillagerRole.COMBAT) {
                    var slots = List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET,
                            net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                    for (int index = 0; index < slots.size(); index++) {
                        var current = owner.getItemBySlot(slots.get(index));
                        if (!current.isEmpty() && !HiredJobInventory.hasTemporaryEquipment(owner, slots.get(index))
                                && !ItemStack.isSameItemSameComponents(current, owned.get(index))) owned.add(current.copy());
                    }
                    if (HiredRangedAmmo.isWeaponRequiringAmmo(owner.getMainHandItem())) weapon = owner.getMainHandItem();
                }
                if (VillagerInventoryAccess.hasBorrowedCombatWeapon(owner)) {
                    var borrowed = owner.getMainHandItem().copy();
                    owned.add(borrowed);
                    if (HiredRangedAmmo.isWeaponRequiringAmmo(borrowed)) weapon = borrowed;
                }
                if (VillagerDefensiveLoadoutService.hasBorrowedGuardShield(owner)) owned.add(owner.getMainHandItem().copy());
                for (var item : VillagerInventoryAccess.captureFullInventory(owner))
                    if (HiredRangedAmmo.isAmmo(item)) owned.add(item.copy());
            }
            boolean configuredRanged = configuration != null && configuration.selections().stream().anyMatch(selection -> selection.equip()
                    && (selection.kind() == HiredEquipmentNeed.Kind.BOW || selection.kind() == HiredEquipmentNeed.Kind.CROSSBOW));
            if (HiredRangedAmmo.isWeaponRequiringAmmo(weapon) || configuredRanged) {
                boolean ready = HiredRangedAmmo.isWeaponRequiringAmmo(weapon)
                        && (CrossbowItem.isCharged(weapon) || owner != null && HiredRangedAmmo.canUseRangedAttack(owner, weapon));
                var reserve = configuration == null ? null : configuration.selection(HiredEquipmentNeed.Kind.AMMUNITION);
                int amount = reserve == null ? 1 : Math.max(1, reserve.spares());
                needs.add(new Need(HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.AMMUNITION), amount,
                        reserve != null && reserve.protectedMinimum() ? amount : ready ? 0 : 1, amount - 1));
            }
        }
        return new Worker(area == null ? id : area.id(), name(level, id), sources, List.copyOf(needs), owned,
                configuration == null ? -1 : configuration.priority());
    }

    static String name(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof Villager villager ? VillagerPresetNameRegistry.resolveDisplayName(villager).getString()
                : VillagerSocialGraphSavedData.get(level).knownName(id).orElse(id.toString());
    }

    static List<BlockPos> civicSources(ServerLevel level, SettlementRecord town, UUID resident, UUID area, HiredVillagerRole role) {
        var storage = SettlementStorageService.effective(town, resident);
        if (!storage.automatic()) return storage.entries().stream()
                .filter(link -> link.purpose().supplies() && (SettlementStorageService.permitted(level, town, role, link)
                        || !level.hasChunkAt(link.position()) && SettlementStock.supplies(town, role).stream()
                                .anyMatch(depot -> depot.position().equals(link.position()))))
                .map(link -> SettlementSupplyStock.positions(level, link.position()).getFirst()).distinct().toList();
        var links = SettlementWorksiteService.links(town, area, resident);
        return SettlementStock.supplies(town, role).stream().filter(depot -> links.permitsSupply(depot.id()))
                .map(depot -> SettlementSupplyStock.positions(level, depot.position()).getFirst()).distinct().toList();
    }
}
