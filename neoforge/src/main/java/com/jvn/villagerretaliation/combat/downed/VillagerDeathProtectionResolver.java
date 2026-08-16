package com.jvn.villagerretaliation.combat.downed;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.quest.VillagerQuestDeathProtectionService;
import com.jvn.villagerretaliation.raid.PlayerRaidSavedData;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

public final class VillagerDeathProtectionResolver {
    public static final String ESSENTIAL_ENTITY_TAG = "villagerretaliation_essential";

    private VillagerDeathProtectionResolver() {
    }

    public static ProtectionResult resolve(ServerLevel level, Villager villager) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_DOWNED_STATE.get()) {
            return ProtectionResult.unprotected();
        }

        List<String> sources = new ArrayList<>();
        if (VillagerRetaliationConfig.ALL_VILLAGERS_USE_DOWNED_STATE.get()) {
            sources.add("all_villagers");
        }
        if (VillagerRetaliationConfig.RAID_VILLAGERS_USE_DOWNED_STATE.get()
                && isInRaid(level, villager)) {
            sources.add("raid");
        }
        if (VillagerRetaliationConfig.HIRED_VILLAGERS_USE_DOWNED_STATE.get()
                && HiredVillagerContractService.isHired(level, villager)) {
            sources.add("hired");
        }
        if (villager.getTags().contains(ESSENTIAL_ENTITY_TAG)) {
            sources.add("essential");
        }
        if (VillagerRetaliationConfig.PARTY_VILLAGERS_USE_DOWNED_STATE.get()
                && PartyVillagerContractService.isActivePartyVillager(level, villager)) {
            sources.add("party");
        }
        VillagerQuestDeathProtectionService.activeWhileActiveQuests(level, villager)
                .forEach(id -> sources.add("quest:while_active:" + id));
        VillagerQuestDeathProtectionService.pendingPartyRewardQuests(level, villager)
                .forEach(id -> sources.add("quest:pending_party_reward:" + id));
        VillagerQuestDeathProtectionService.permanentAfterStartQuests(villager)
                .forEach(id -> sources.add("quest:after_start:" + id));
        SceneLifecycleIntegration.protectingScenes(level, villager)
                .forEach(id -> sources.add("scene:" + id));
        return sources.isEmpty() ? ProtectionResult.unprotected() : new ProtectionResult(true, List.copyOf(sources));
    }

    public static ProtectionResult resolve(ServerLevel level, Villager villager, DamageSource damageSource) {
        ProtectionResult context = resolve(level, villager);
        if (!context.protectedFromDeath() || !isDamageSourceEnabled(damageSource)) {
            return ProtectionResult.unprotected();
        }
        return context.withSource("damage:" + damageKind(damageSource).diagnosticName);
    }

    public static DamageKind damageKind(DamageSource source) {
        Entity causingEntity = source.getEntity();
        if (causingEntity instanceof Player) {
            return DamageKind.PLAYER;
        }
        if (causingEntity != null || source.getDirectEntity() != null) {
            return DamageKind.MOB;
        }
        return DamageKind.ENVIRONMENTAL;
    }

    private static boolean isDamageSourceEnabled(DamageSource source) {
        return switch (damageKind(source)) {
            case PLAYER -> VillagerRetaliationConfig.PLAYER_DAMAGE_DOWNS_ELIGIBLE_VILLAGERS.get();
            case MOB -> VillagerRetaliationConfig.MOB_DAMAGE_DOWNS_ELIGIBLE_VILLAGERS.get();
            case ENVIRONMENTAL -> VillagerRetaliationConfig.ENVIRONMENTAL_DAMAGE_DOWNS_ELIGIBLE_VILLAGERS.get();
        };
    }

    private static boolean isInRaid(ServerLevel level, Villager villager) {
        return level.getRaidAt(villager.blockPosition()) != null
                || PlayerRaidSavedData.get(level).activeForParticipant(villager.getUUID()) != null;
    }

    public enum DamageKind {
        PLAYER("player"),
        MOB("mob"),
        ENVIRONMENTAL("environmental");

        private final String diagnosticName;

        DamageKind(String diagnosticName) {
            this.diagnosticName = diagnosticName;
        }
    }

    public record ProtectionResult(boolean protectedFromDeath, List<String> sources) {
        public ProtectionResult {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        public static ProtectionResult unprotected() {
            return new ProtectionResult(false, List.of());
        }

        public ProtectionResult withSource(String source) {
            List<String> combined = new ArrayList<>(sources);
            combined.add(source);
            return new ProtectionResult(protectedFromDeath, combined);
        }

        public String diagnosticValue() {
            return String.join(",", sources);
        }
    }
}
