package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

final class SettlementBreedingDemand {
    private SettlementBreedingDemand() { }

    record Need(WorkSupplyRequirement requirement, List<UUID> workers) { }

    private static SettlementWorkerSettings settings(SettlementRecord town, UUID worker) {
        var configured = town.effectiveWorkerSettings(worker);
        if (configured != null) return configured;
        var observed = town.workers().get(worker);
        return SettlementWorkerSettings.capture(com.jvn.villagerretaliation.interaction.HiredVillagerRole.ANIMAL_HANDLING,
                observed == null ? new net.minecraft.nbt.CompoundTag() : observed.settings());
    }

    static List<Need> describe(ServerLevel level, SettlementRecord town, SettlementArea area) {
        if (!area.enabled() || !town.owns(area)) return List.of();
        var workers = town.duties().entrySet().stream().filter(entry -> area.id().equals(entry.getValue()))
                .map(java.util.Map.Entry::getKey).filter(id -> {
                    var settings = settings(town, id);
                    return settings != null && settings.enabled() && settings.options().get(SettlementWorkerSetting.BREED_ANIMALS) != 0;
                }).sorted().toList();
        if (workers.isEmpty()) return List.of();
        var animals = new ArrayList<Animal>();
        for (var saved : town.livestock().values().stream().sorted(java.util.Comparator.comparing(SettlementLivestock::id)).toList()) {
            if (!saved.area().equals(area.id()) || saved.unsafe() || !SettlementLivestockBreeding.supported(saved.type())) continue;
            var entity = level.getEntity(saved.id());
            var animal = entity instanceof Animal loaded ? loaded : saved.context(level);
            if (animal != null && animal.isAlive() && area.contains(animal.blockPosition()) && animal.getAge() == 0
                    && animal.canFallInLove() && !animal.isPanicking()) animals.add(animal);
        }
        var result = new ArrayList<Need>();
        var paired = new java.util.HashSet<UUID>();
        for (int first = 0; first < animals.size(); first++) {
            var one = animals.get(first);
            if (paired.contains(one.getUUID())) continue;
            for (int second = first + 1; second < animals.size(); second++) {
                var two = animals.get(second);
                if (paired.contains(two.getUUID()) || one.getType() != two.getType() || one.distanceToSqr(two) > 9.0D) continue;
                var eligible = workers.stream().filter(id -> HiredAnimalBreedingTargets.matches(one,
                        settings(town, id).animals())).toList();
                if (eligible.isEmpty()) continue;
                var alternatives = BuiltInRegistries.ITEM.stream().map(ItemStack::new)
                        .filter(stack -> one.isFood(stack) && two.isFood(stack)).toList();
                if (alternatives.isEmpty()) continue;
                var requirement = new WorkSupplyRequirement("animal_feed:" + BuiltInRegistries.ENTITY_TYPE.getKey(one.getType()),
                        alternatives, 2, WorkSupplyRequirement.Unit.ITEMS, stack -> one.isFood(stack) && two.isFood(stack), null);
                result.add(new Need(requirement, eligible));
                paired.add(one.getUUID());
                paired.add(two.getUUID());
                break;
            }
        }
        return List.copyOf(result);
    }
}
