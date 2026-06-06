package com.jvn.villagerretaliation.client.profile;

import com.jvn.villagerretaliation.network.VillagerProfileSyncPayload;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeRank;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.skill.VillagerSkillValue;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class VillagerProfileClientCache {
    private static final long PRUNE_INTERVAL_TICKS = 40L;
    private static final Map<UUID, DisplayEntry> BY_VILLAGER_UUID = new HashMap<>();
    private static final Map<Integer, UUID> ENTITY_ID_TO_UUID = new HashMap<>();
    private static long nextPruneGameTime;

    private VillagerProfileClientCache() {
    }

    public static void accept(VillagerProfileSyncPayload payload) {
        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        DisplayEntry entry = new DisplayEntry(
                payload.entityId(),
                payload.villagerId(),
                payload.professionKey(),
                payload.generatedVersion(),
                payload.attributes(),
                payload.skillGeneratedVersion(),
                payload.skills(),
                payload.tradeLevelSkillAdjustedXpProgress(),
                gameTime
        );
        BY_VILLAGER_UUID.put(payload.villagerId(), entry);
        ENTITY_ID_TO_UUID.put(payload.entityId(), payload.villagerId());
    }

    public static Optional<DisplayEntry> get(UUID villagerId, int entityId) {
        DisplayEntry entry = villagerId == null ? null : BY_VILLAGER_UUID.get(villagerId);
        if (entry != null) {
            return Optional.of(entry);
        }
        UUID mappedId = ENTITY_ID_TO_UUID.get(entityId);
        return mappedId == null ? Optional.empty() : Optional.ofNullable(BY_VILLAGER_UUID.get(mappedId));
    }

    public static Optional<DisplayEntry> get(int entityId) {
        UUID mappedId = ENTITY_ID_TO_UUID.get(entityId);
        return mappedId == null ? Optional.empty() : Optional.ofNullable(BY_VILLAGER_UUID.get(mappedId));
    }

    public static void updateTradeLevelSkillAdjustedXpProgress(int entityId, double progress) {
        Optional<DisplayEntry> entry = get(entityId);
        if (entry.isEmpty()) {
            return;
        }

        DisplayEntry current = entry.get();
        BY_VILLAGER_UUID.put(current.villagerId(), current.withTradeLevelSkillAdjustedXpProgress(progress));
    }

    public static void clear() {
        BY_VILLAGER_UUID.clear();
        ENTITY_ID_TO_UUID.clear();
        nextPruneGameTime = 0L;
    }

    public static void pruneMissing() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (gameTime < nextPruneGameTime) {
            return;
        }
        nextPruneGameTime = gameTime + PRUNE_INTERVAL_TICKS;

        Iterator<Map.Entry<Integer, UUID>> iterator = ENTITY_ID_TO_UUID.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, UUID> entry = iterator.next();
            if (minecraft.level.getEntity(entry.getKey()) == null) {
                BY_VILLAGER_UUID.remove(entry.getValue());
                iterator.remove();
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        pruneMissing();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public record DisplayEntry(
            int entityId,
            UUID villagerId,
            String professionKey,
            int generatedVersion,
            VillagerSocialAttributes attributes,
            int skillGeneratedVersion,
            VillagerSkillSet skills,
            double tradeLevelSkillAdjustedXpProgress,
            long lastUpdateGameTime) {
        public int value(VillagerSocialAttribute attribute) {
            return this.attributes.get(attribute);
        }

        public VillagerSocialAttributeRank rank(VillagerSocialAttribute attribute) {
            return this.attributes.rank(attribute);
        }

        public int skillValue(VillagerSkill skill) {
            return this.skills.get(skill);
        }

        public VillagerSkillRank skillRank(VillagerSkill skill) {
            return this.skills.rank(skill);
        }

        public List<VillagerSkillValue> bestSkills(int limit) {
            return this.skills.best(limit);
        }

        private DisplayEntry withTradeLevelSkillAdjustedXpProgress(double progress) {
            return new DisplayEntry(
                    this.entityId,
                    this.villagerId,
                    this.professionKey,
                    this.generatedVersion,
                    this.attributes,
                    this.skillGeneratedVersion,
                    this.skills,
                    clampFractionalProgress(progress),
                    this.lastUpdateGameTime
            );
        }
    }

    private static double clampFractionalProgress(double progress) {
        if (!Double.isFinite(progress)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(0.999_999D, progress));
    }
}
