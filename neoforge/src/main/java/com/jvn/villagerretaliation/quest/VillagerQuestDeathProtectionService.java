package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.quest.provider.QuestProviderDeathProtection;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerQuestDeathProtectionService {
    private static final String AFTER_START_QUESTS_KEY = "VillagerRetaliationAfterStartProtectedQuests";

    private VillagerQuestDeathProtectionService() {
    }

    public static Set<ResourceLocation> activeWhileActiveQuests(ServerLevel level, Villager villager) {
        Set<ResourceLocation> questIds = new LinkedHashSet<>();
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        for (VillagerQuestSavedData.QuestEntry entry : data.activeProgressStartedBy(villager.getUUID())) {
            VillagerQuestResources.compiledQuest(level.getServer(), entry.questId())
                    .filter(compiled -> compiled.provider().deathProtection() == QuestProviderDeathProtection.WHILE_ACTIVE)
                    .ifPresent(compiled -> questIds.add(entry.questId()));
        }
        return Set.copyOf(questIds);
    }

    public static Set<ResourceLocation> permanentAfterStartQuests(Villager villager) {
        if (villager == null || !villager.getPersistentData().contains(AFTER_START_QUESTS_KEY, Tag.TAG_LIST)) {
            return Set.of();
        }
        Set<ResourceLocation> questIds = new LinkedHashSet<>();
        for (Tag value : villager.getPersistentData().getList(AFTER_START_QUESTS_KEY, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) {
                questIds.add(id);
            }
        }
        return Set.copyOf(questIds);
    }

    public static boolean markAfterSuccessfulStart(ServerLevel level, Villager provider, ResourceLocation questId) {
        if (level == null || provider == null || questId == null) {
            return false;
        }
        boolean protectsAfterStart = VillagerQuestResources.compiledQuest(level.getServer(), questId)
                .map(compiled -> compiled.provider().deathProtection() == QuestProviderDeathProtection.AFTER_START)
                .orElse(false);
        if (!protectsAfterStart) {
            return false;
        }

        Set<ResourceLocation> questIds = new LinkedHashSet<>(permanentAfterStartQuests(provider));
        if (!questIds.add(questId)) {
            return false;
        }
        ListTag saved = new ListTag();
        questIds.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .map(id -> StringTag.valueOf(id.toString()))
                .forEach(saved::add);
        provider.getPersistentData().put(AFTER_START_QUESTS_KEY, saved);
        return true;
    }
}
