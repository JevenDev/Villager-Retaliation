package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.villager.VillagerWorkExperience;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

final class SettlementExperienceRecipients {
    private final ServerLevel level;
    private final Map<UUID, Villager> contexts = new LinkedHashMap<>();

    SettlementExperienceRecipients(ServerLevel level) {
        this.level = level;
    }

    Villager find(UUID id) {
        var cached = this.contexts.get(id);
        if (cached != null) return cached;
        var entity = SettlementAnimalBirthService.existing(this.level, id);
        if (entity != null && (!(entity instanceof Villager) || entity.level() != this.level)) return null;
        var villager = (Villager) entity;
        for (var town : SettlementSavedData.get(this.level).settlements()) {
            if (!town.dimension().equals(this.level.dimension().location())) continue;
            var care = town.residents().get(id);
            var worker = town.duties().containsKey(id) ? town.workers().get(id) : null;
            if (worker == null && care == null) continue;
            if (villager != null) {
                if (worker != null) worker.restore(villager);
                if (care != null) care.restore(villager);
            } else {
                var position = worker != null ? worker.position() : care.position();
                if (this.level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(position))
                        || worker == null && !care.canDepart()) return null;
                villager = worker == null ? care.context(this.level) : worker.context(this.level, town);
                if (villager == null && care != null) {
                    villager = care.context(this.level);
                    if (villager != null && worker != null) HiredJobInventory.getJobInventory(villager).restoreSnapshot(worker.inventory());
                }
            }
            break;
        }
        if (villager != null) this.contexts.put(id, villager);
        return villager;
    }

    List<Villager> nearby(ExperienceOrb orb) {
        AABB bounds = orb.getBoundingBox().inflate(8.0D);
        var result = new ArrayList<Villager>();
        var seen = new java.util.HashSet<UUID>();
        for (var entity : this.level.getEntitiesOfClass(Villager.class, bounds)) {
            var villager = find(entity.getUUID());
            if (villager != null) {
                result.add(villager);
                seen.add(villager.getUUID());
            }
        }
        for (var town : SettlementSavedData.get(this.level).settlements()) {
            if (!town.dimension().equals(this.level.dimension().location())) continue;
            var positions = new LinkedHashMap<UUID, net.minecraft.core.BlockPos>();
            town.residents().values().forEach(resident -> positions.put(resident.id(), resident.position()));
            town.workers().values().stream().filter(worker -> town.duties().containsKey(worker.id()))
                    .forEach(worker -> positions.put(worker.id(), worker.position()));
            for (var entry : positions.entrySet()) {
                if (seen.contains(entry.getKey()) || entry.getValue().distToCenterSqr(orb.position()) > 100.0D) continue;
                var villager = find(entry.getKey());
                if (villager != null && bounds.intersects(villager.getBoundingBox())) {
                    result.add(villager);
                    seen.add(villager.getUUID());
                }
            }
        }
        return result;
    }

    boolean mend(ExperienceOrb orb, Villager villager) {
        var before = new java.util.EnumMap<EquipmentSlot, net.minecraft.world.item.ItemStack>(EquipmentSlot.class);
        for (var slot : EquipmentSlot.values()) before.put(slot, villager.getItemBySlot(slot).copy());
        if (!VillagerWorkExperience.mendOnContact(orb, villager)) return false;
        for (var slot : EquipmentSlot.values()) HiredJobInventory.synchronizeEquipmentDurability(villager, slot);
        for (var town : SettlementSavedData.get(this.level).settlements()) {
            if (!town.dimension().equals(this.level.dimension().location())) continue;
            var worker = town.duties().containsKey(villager.getUUID()) ? town.workers().get(villager.getUUID()) : null;
            if (worker != null) {
                worker.inventorySnapshot(HiredJobInventory.getJobInventory(villager).captureSnapshot());
                worker.changed();
                worker.restore(villager);
                town.changed();
            } else {
                var care = town.residents().get(villager.getUUID());
                if (care != null) {
                    care.mend(villager, before);
                    town.changed();
                }
            }
        }
        return true;
    }
}
