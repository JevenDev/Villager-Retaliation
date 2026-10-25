package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class VillageDiplomacyApi {
    private VillageDiplomacyApi() {
    }

    public static VillageRelationSnapshot query(ServerLevel level, VillageAllegianceId first, VillageAllegianceId second) {
        if (level == null || !level.getServer().isSameThread()) {
            return VillageRelationSnapshot.NEUTRAL;
        }
        var pair = pair(level, first, second);
        return pair == null || pair.first().equals(pair.second())
                ? VillageRelationSnapshot.NEUTRAL
                : VillageDiplomacySavedData.get(level).query(pair, level.getServer().overworld().getGameTime());
    }

    public static VillageRelationSnapshot query(ServerLevel level, Entity first, Entity second) {
        if (level == null || !level.getServer().isSameThread() || first == null || second == null
                || first.level().isClientSide() || second.level().isClientSide()
                || first.getServer() != level.getServer() || second.getServer() != level.getServer()) {
            return VillageRelationSnapshot.NEUTRAL;
        }
        return query(level, VillageAllegianceApi.canonicalPrimary(level, first).orElse(null),
                VillageAllegianceApi.canonicalPrimary(level, second).orElse(null));
    }

    public static ChangeResult set(ServerLevel level, VillageAllegianceId first, VillageAllegianceId second,
                                   VillageRelation relation) {
        if (relation == null || relation == VillageRelation.TRUCE) {
            return ChangeResult.INVALID_STATE;
        }
        return change(level, first, second, relation, 0);
    }

    public static ChangeResult truce(ServerLevel level, VillageAllegianceId first, VillageAllegianceId second) {
        return truce(level, first, second, VillagerRetaliationConfig.DEFAULT_TRUCE_DURATION_TICKS.get());
    }

    // zero duration explicitly requests an indefinite truce
    public static ChangeResult truce(ServerLevel level, VillageAllegianceId first, VillageAllegianceId second,
                                     long durationTicks) {
        return change(level, first, second, VillageRelation.TRUCE, durationTicks);
    }

    private static ChangeResult change(ServerLevel level, VillageAllegianceId first, VillageAllegianceId second,
                                       VillageRelation relation, long duration) {
        if (level == null) {
            return ChangeResult.INVALID_VILLAGE;
        }
        if (!level.getServer().isSameThread()) {
            return ChangeResult.WRONG_THREAD;
        }
        long now = level.getServer().overworld().getGameTime();
        if (duration < 0 || duration > Long.MAX_VALUE - now) {
            return ChangeResult.INVALID_DURATION;
        }
        var pair = pair(level, first, second);
        if (pair == null) {
            return ChangeResult.INVALID_VILLAGE;
        }
        if (pair.first().equals(pair.second())) {
            return ChangeResult.SAME_VILLAGE;
        }
        return VillageDiplomacySavedData.get(level).set(pair, relation, duration, now)
                ? ChangeResult.APPLIED : ChangeResult.READ_ONLY;
    }

    private static VillageDiplomacySavedData.Pair pair(
            ServerLevel level, VillageAllegianceId first, VillageAllegianceId second) {
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var a = registry.canonicalRecord(first).filter(record -> !record.archived());
        var b = registry.canonicalRecord(second).filter(record -> !record.archived());
        return a.isEmpty() || b.isEmpty() ? null : VillageDiplomacySavedData.Pair.of(a.get().id(), b.get().id());
    }

    public enum ChangeResult {
        APPLIED,
        INVALID_VILLAGE,
        SAME_VILLAGE,
        INVALID_STATE,
        INVALID_DURATION,
        WRONG_THREAD,
        READ_ONLY
    }
}
