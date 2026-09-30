package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.AnimalBreedingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;

final class SettlementBackgroundCulling {
    private SettlementBackgroundCulling() {
    }

    static boolean tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, Villager owner,
                        HiredJobInventory inventory, List<SettlementDepot> depots, SettlementWorkBudget budget) {
        if (!com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions.cull(worker.settings())) return false;
        int cap = HiredAnimalCullSettings.cap(worker.settings());
        if (cap <= HiredAnimalCullSettings.DISABLED_CAP) return false;
        var area = town.areas().get(worker.area());
        if (area == null || !area.contains(worker.position())) return false;
        var selected = HiredAnimalBreedingTargets.selectedTargetIds(worker.settings());
        var candidates = new ArrayList<Candidate>();
        for (var state : town.livestock().values()) {
            if (!state.area().equals(worker.area()) || !area.contains(state.position()) || town.entityDeaths().containsKey(state.id())) continue;
            var entity = SettlementAnimalBirthService.existing(level, state.id());
            if (entity != null && (!(entity instanceof Animal) || entity.level() != level)) continue;
            if (entity == null && level.areEntitiesLoaded(ChunkPos.asLong(state.position()))
                    && !SettlementAnimalBirthService.pending(level, town, state.id())) continue;
            var animal = entity instanceof Animal actual ? actual : state.context(level);
            if (animal == null || !animal.isAlive() || animal.isBaby() || !area.contains(animal.blockPosition())
                    || AnimalBreedingWorker.isProtectedCullAnimal(animal) || !HiredAnimalBreedingTargets.matches(animal, selected)
                    || HiredPathMemory.isAvoided(level, owner, animal.blockPosition())) continue;
            candidates.add(new Candidate(state, animal));
        }
        var counts = new java.util.HashMap<net.minecraft.world.entity.EntityType<?>, Integer>();
        candidates.forEach(candidate -> counts.merge(candidate.animal().getType(), 1, Integer::sum));
        var target = candidates.stream().filter(candidate -> counts.get(candidate.animal().getType()) > cap)
                .min(Comparator.comparingDouble(candidate -> candidate.animal().distanceToSqr(owner))).orElse(null);
        if (target == null) return false;
        var weapon = SettlementBackgroundTools.equipTool(worker, inventory, AnimalBreedingWorker::isCullWeapon, AnimalBreedingWorker::cullWeaponScore);
        if (weapon.isEmpty()) {
            SettlementBackgroundTools.fetchTool(level, town, worker, inventory, depots, AnimalBreedingWorker::isCullWeapon,
                    AnimalBreedingWorker::cullWeaponScore, "cull_weapon", budget);
            return true;
        }
        int steps = target.state().steps(worker);
        if (!target.state().position().equals(target.animal().blockPosition())) {
            steps = SettlementLivestockService.observedSteps(level, town, area, worker, target.animal());
        }
        if (steps < 0) {
            worker.status("animal_unreachable");
            return true;
        }
        if (!budget.spend(SettlementBackgroundAnimalHandling.productTicks(worker) + steps * 16.0D)) {
            worker.status("culling_animal");
            return true;
        }
        var animal = target.animal();
        var nativePosition = animal.blockPosition().immutable();
        try (var capture = new SettlementDeathCapture(animal)) {
            boolean hurt = animal.hurt(level.damageSources().mobAttack(owner), Math.max(1000.0F, animal.getMaxHealth() * 4.0F));
            boolean killed = hurt && !animal.isAlive() && capture.accepted();
            int route = target.state().position().equals(animal.blockPosition()) ? steps : -1;
            for (var item : capture.drops()) {
                if (item.isRemoved() || item.getItem().isEmpty()) continue;
                var drop = SettlementItemDrop.create(item, area.id(), level.getGameTime());
                int pickup = item.blockPosition().equals(target.state().position()) ? route : -1;
                if (pickup == 0 && worker.position().getBottomCenter().distanceToSqr(item.position()) > 2.25D) pickup = -1;
                drop.access(worker, pickup);
                town.recordItemDrop(drop);
            }
            if (killed || !capture.experience().isEmpty()) {
                town.recordEntityDeath(SettlementEntityDeath.create(animal, nativePosition, worker, capture.experience(), level.getGameTime(), killed));
                SettlementEntityDeathService.update(level, town, level.getGameTime());
            }
            if (!killed) {
                target.state().interacted(animal);
                SettlementLivestockService.applyLoaded(level, target.state());
                worker.status("animal_cull_failed");
                town.changed();
                return true;
            }
            var birth = town.animalBirths().get(animal.getUUID());
            if (birth != null) birth.applyChild();
            town.removeLivestock(animal.getUUID());
            VillagerEquipmentDurability.hurtTool(weapon, owner, EquipmentSlot.MAINHAND);
            inventory.syncMainHandEquipment();
            inventory.setChanged();
            worker.practice(level, HiredWorkPractice.animal("cull", 0.8D, target.state().type().hashCode()));
            worker.status("culled_animal");
            town.changed();
            SettlementItemDropService.update(level, town, level.getGameTime());
            return true;
        }
    }

    private record Candidate(SettlementLivestock state, Animal animal) {
    }
}
