package com.jvn.villagerretaliation.village;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.event.VillagerEventTriggerSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import net.minecraft.server.level.ServerLevel;

/** Moves position-keyed village-wide state when durable allegiance identities merge. */
public final class VillageScopeMigrationService {
    private VillageScopeMigrationService() {
    }

    public static void merge(
            ServerLevel level,
            VillageAllegianceRegistrySavedData.AllegianceRecord source,
            VillageAllegianceRegistrySavedData.AllegianceRecord target) {
        if (level == null || source == null || target == null) {
            return;
        }
        String sourceKey = VillageScopeKeys.forPosition(
                source.originDimension(), source.originPosition());
        String targetKey = VillageScopeKeys.forPosition(
                target.originDimension(), target.originPosition());
        if (sourceKey.isBlank() || targetKey.isBlank() || sourceKey.equals(targetKey)) {
            return;
        }

        VillageRegistrySavedData.get(level).mergeKey(sourceKey, targetKey);
        VillagerQuestFacts.get(level).mergeScope(sourceKey, targetKey);
        VillagerQuestSavedData.get(level).replaceIssuerVillageKey(sourceKey, targetKey);
        VillagerEventTriggerSavedData.get(level).mergeScopeKey(sourceKey, targetKey);
        VillagerSocialGraphSavedData.get(level).replaceVillageKey(sourceKey, targetKey);
    }
}
