package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public record VillageOpinion(double score, VillagerReputationLevel level) {
    public static final VillageOpinion NEUTRAL = new VillageOpinion(0, VillagerReputationLevel.NEUTRAL);

    public static VillageOpinion of(ServerLevel level, VillageAllegianceId village, UUID player) {
        var record = VillageAllegianceRegistrySavedData.get(level).canonicalRecord(village).orElse(null);
        if (record == null || record.archived() || record.residents().isEmpty()) return NEUTRAL;
        var reputations = VillagerReputationSavedData.get(level);
        long total = 0;
        for (UUID resident : record.residents().keySet()) {
            var entry = reputations.get(resident, player);
            if (entry != null) total += entry.reputation();
        }
        double average = (double) total / record.residents().size();
        return new VillageOpinion(average, VillagerReputationLevel.fromReputation((int) average));
    }
}
