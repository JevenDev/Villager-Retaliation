package com.jvn.villagerretaliation.combat.downed;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerDeathProtectionResolver {
    public static final String ESSENTIAL_ENTITY_TAG = "villagerretaliation_essential";

    private VillagerDeathProtectionResolver() {
    }

    public static ProtectionResult resolve(ServerLevel level, Villager villager) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_DOWNED_STATE.get()) {
            return ProtectionResult.unprotected();
        }

        List<String> sources = new ArrayList<>();
        if (villager.getTags().contains(ESSENTIAL_ENTITY_TAG)) {
            sources.add("essential");
        }
        if (VillagerRetaliationConfig.PARTY_VILLAGERS_USE_DOWNED_STATE.get()
                && PartyVillagerContractService.isActivePartyVillager(level, villager)) {
            sources.add("party");
        }
        return sources.isEmpty() ? ProtectionResult.unprotected() : new ProtectionResult(true, List.copyOf(sources));
    }

    public record ProtectionResult(boolean protectedFromDeath, List<String> sources) {
        public ProtectionResult {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        public static ProtectionResult unprotected() {
            return new ProtectionResult(false, List.of());
        }

        public String diagnosticValue() {
            return String.join(",", sources);
        }
    }
}
