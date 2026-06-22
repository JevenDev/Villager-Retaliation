package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerIndex;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.mood.VillagerMoodSavedData;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;

public final class VillagerConversionPersistenceService {
    private static final String SNAPSHOT_TAG = "VillagerRetaliationVillagerSnapshot";
    private static final Set<String> RESTORED_ENTITY_TAGS = Set.of(
            "Age",
            "ArmorDropChances",
            "ArmorItems",
            "AssignProfessionWhenSpawned",
            "Brain",
            "CanPickUpLoot",
            "ForcedAge",
            "FoodLevel",
            "Gossips",
            "HandDropChances",
            "HandItems",
            "Inventory",
            "LastGossipDecay",
            "LastRestock",
            "Offers",
            "PersistenceRequired",
            "RestocksToday",
            "VillagerData",
            "Xp"
    );

    private VillagerConversionPersistenceService() {
    }

    public static void onLivingConversionPost(LivingConversionEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity source = event.getEntity();
        LivingEntity outcome = event.getOutcome();
        boolean villagerFormConversion = (source instanceof Villager || source instanceof ZombieVillager)
                && (outcome instanceof Villager || outcome instanceof ZombieVillager);
        if (!villagerFormConversion) {
            return;
        }

        copyPersistentData(source, outcome);
        transferSavedData(level, source.getUUID(), outcome.getUUID());

        if (source instanceof Villager && outcome instanceof ZombieVillager zombieVillager) {
            storeVillagerSnapshot(source, zombieVillager);
            return;
        }

        if (source instanceof ZombieVillager && outcome instanceof Villager curedVillager) {
            restoreVillagerSnapshot(source, curedVillager);
            HiredJobInventory.maintainEquipmentSlots(curedVillager);
            HiredVillagerIndex.update(level, curedVillager);
        }
    }

    private static void copyPersistentData(LivingEntity source, LivingEntity outcome) {
        CompoundTag sourceData = source.getPersistentData();
        if (!sourceData.isEmpty()) {
            outcome.getPersistentData().merge(sourceData.copy());
        }
    }

    private static void storeVillagerSnapshot(LivingEntity source, ZombieVillager outcome) {
        CompoundTag snapshot = new CompoundTag();
        source.saveWithoutId(snapshot);
        outcome.getPersistentData().put(SNAPSHOT_TAG, snapshot);
        outcome.setPersistenceRequired();
    }

    private static void restoreVillagerSnapshot(LivingEntity source, Villager outcome) {
        CompoundTag sourceData = source.getPersistentData();
        if (!sourceData.contains(SNAPSHOT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag snapshot = sourceData.getCompound(SNAPSHOT_TAG);
        CompoundTag restored = new CompoundTag();
        outcome.saveWithoutId(restored);
        for (String key : RESTORED_ENTITY_TAGS) {
            Tag value = snapshot.get(key);
            if (value != null) {
                restored.put(key, value.copy());
            }
        }
        outcome.load(restored);
        outcome.getPersistentData().remove(SNAPSHOT_TAG);
        outcome.setPersistenceRequired();
    }

    private static void transferSavedData(ServerLevel level, UUID sourceVillagerId, UUID targetVillagerId) {
        if (sourceVillagerId == null || targetVillagerId == null || sourceVillagerId.equals(targetVillagerId)) {
            return;
        }

        VillagerProfileSavedData.get(level).transferVillagerProfile(sourceVillagerId, targetVillagerId);
        VillagerInteractionSavedData.get(level).transferVillagerEntries(sourceVillagerId, targetVillagerId);
        VillagerMoodSavedData.get(level).transferVillagerMood(sourceVillagerId, targetVillagerId);
        AssignedStorageSavedData.get(level).transferVillagerAssignments(sourceVillagerId, targetVillagerId);
        VillagerQuestSavedData.get(level).transferVillagerIdentity(sourceVillagerId, targetVillagerId);
    }
}
