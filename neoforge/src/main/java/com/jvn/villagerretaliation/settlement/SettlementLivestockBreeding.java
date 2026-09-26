package com.jvn.villagerretaliation.settlement;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;

final class SettlementLivestockBreeding {
    private static final Set<EntityType<?>> TYPES = Set.of(EntityType.COW, EntityType.MOOSHROOM, EntityType.SHEEP,
            EntityType.PIG, EntityType.CHICKEN);

    private SettlementLivestockBreeding() {
    }

    static boolean supported(ResourceLocation type) {
        return TYPES.contains(BuiltInRegistries.ENTITY_TYPE.get(type));
    }

    static boolean supported(Animal animal) {
        return TYPES.contains(animal.getType());
    }

    static Frame step(ServerLevel level, SettlementLivestock state, long now) {
        Animal before = supported(state.type()) && !state.unsafe() ? state.context(level) : null;
        return new Frame(state, before, state.advance(now));
    }

    static void advance(ServerLevel level, SettlementRecord town, List<Frame> frames, long now) {
        var used = new HashSet<java.util.UUID>();
        int remaining = 32;
        for (var first : frames) {
            if (used.contains(first.state().id())) continue;
            if (remaining <= 0 || !eligible(town, first) || first.animal().isNoAi()) {
                first.state().resetMating();
                continue;
            }
            var second = frames.stream().filter(frame -> frame != first && !used.contains(frame.state().id())
                    && eligible(town, frame) && frame.state().area().equals(first.state().area())
                    && first.animal().canMate(frame.animal()) && first.animal().distanceToSqr(frame.animal()) < 9.0D)
                    .min(Comparator.comparingDouble(frame -> first.animal().distanceToSqr(frame.animal()))).orElse(null);
            if (second == null) {
                first.state().resetMating();
                continue;
            }
            used.add(first.state().id());
            used.add(second.state().id());
            int elapsed = Math.min(first.elapsed(), second.elapsed());
            int available = Math.min(elapsed, Math.min(first.animal().getInLoveTime(), second.animal().getInLoveTime()));
            int before = first.state().court(second.state().id(), available);
            second.state().resetMating();
            if (before + available < 60) continue;
            remaining--;
            int remainder = Math.max(0, elapsed - (60 - before));
            var child = first.animal().getBreedOffspring(level, second.animal());
            var event = new BabyEntitySpawnEvent(first.animal(), second.animal(), child);
            boolean cancelled = NeoForge.EVENT_BUS.post(event).isCanceled();
            child = event.getChild();
            first.state().resetMating();
            if (!cancelled && child == null) continue;
            first.state().bred(remainder);
            second.state().bred(remainder);
            SettlementLivestockService.applyLoaded(level, first.state());
            SettlementLivestockService.applyLoaded(level, second.state());
            if (cancelled) continue;
            child.setBaby(true);
            if (child.getAge() < 0) child.setAge(Math.min(0, child.getAge() + remainder));
            child.moveTo(first.animal().getX(), first.animal().getY(), first.animal().getZ(), 0.0F, 0.0F);
            var cause = first.animal().getLoveCause();
            if (cause == null) cause = second.animal().getLoveCause();
            if (cause != null) {
                cause.awardStat(net.minecraft.stats.Stats.ANIMALS_BRED);
                net.minecraft.advancements.CriteriaTriggers.BRED_ANIMALS.trigger(cause, first.animal(), second.animal(), child);
            }
            if (town.animalBirths().containsKey(child.getUUID()) || town.livestock().containsKey(child.getUUID())) continue;
            int experience = level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT) ? first.animal().getRandom().nextInt(7) + 1 : 0;
            town.recordAnimalBirth(SettlementAnimalBirth.create(child, experience));
            if (child instanceof Animal animal && SettlementAnimalBirthService.existing(level, child.getUUID()) == null) {
                var state = SettlementLivestock.capture(animal, first.state().area(), now);
                state.inheritAccess(first.state());
                town.recordLivestock(state);
            }
        }
    }

    private static boolean eligible(SettlementRecord town, Frame frame) {
        var area = town.areas().get(frame.state().area());
        return town.livestock().containsKey(frame.state().id()) && area != null && area.enabled()
                && area.type() == SettlementAreaType.ANIMAL_PEN && area.contains(frame.state().position())
                && frame.animal() != null && frame.animal().isAlive() && frame.animal().getAge() == 0
                && frame.animal().isInLove() && !frame.animal().isPanicking();
    }

    record Frame(SettlementLivestock state, Animal animal, int elapsed) {
    }
}
