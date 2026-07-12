package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

public final class AllegianceEntityClassifier {
    public static final TagKey<EntityType<?>> ALLEGIANCE_HOLDERS = TagKey.create(
            Registries.ENTITY_TYPE, VillagerRetaliation.id("village_allegiance_holders"));
    public static final TagKey<EntityType<?>> PROTECTED_CIVILIANS = TagKey.create(
            Registries.ENTITY_TYPE, VillagerRetaliation.id("protected_civilians"));
    public static final TagKey<EntityType<?>> ALLEGIANCE_EXEMPT = TagKey.create(
            Registries.ENTITY_TYPE, VillagerRetaliation.id("allegiance_exempt"));
    private AllegianceEntityClassifier() {
    }

    public static Classification classify(Entity entity) {
        if (entity == null || entity.getType().is(ALLEGIANCE_EXEMPT)) {
            return Classification.OTHER;
        }
        if (entity instanceof WanderingTrader || entity instanceof TraderLlama) {
            return Classification.NEUTRAL_TRADER;
        }
        if (entity instanceof Villager || entity instanceof ZombieVillager) {
            return Classification.PROTECTED_CIVILIAN;
        }
        if (entity instanceof IronGolem golem) {
            return golem.isPlayerCreated()
                    ? Classification.UNAFFILIATED_GOLEM
                    : Classification.VILLAGE_GOLEM;
        }
        if (entity.getType().is(PROTECTED_CIVILIANS) || entity.getType().is(ALLEGIANCE_HOLDERS)) {
            return Classification.PROTECTED_CIVILIAN;
        }
        return VillageAllegianceApi.providerData(entity).isPresent()
                ? Classification.PROTECTED_CIVILIAN
                : Classification.OTHER;
    }

    public static boolean bearsAllegiance(Entity entity) {
        Classification classification = classify(entity);
        return classification == Classification.PROTECTED_CIVILIAN
                || classification == Classification.VILLAGE_GOLEM
                || classification == Classification.UNAFFILIATED_GOLEM
                || classification == Classification.NEUTRAL_TRADER;
    }

    public static boolean protectedCivilian(Entity entity) {
        Classification classification = classify(entity);
        return classification == Classification.PROTECTED_CIVILIAN
                || classification == Classification.VILLAGE_GOLEM
                || classification == Classification.NEUTRAL_TRADER;
    }

    public enum Classification {
        PROTECTED_CIVILIAN,
        NEUTRAL_TRADER,
        VILLAGE_GOLEM,
        UNAFFILIATED_GOLEM,
        OTHER
    }
}
