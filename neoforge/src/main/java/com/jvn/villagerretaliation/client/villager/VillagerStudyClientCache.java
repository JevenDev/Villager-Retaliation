package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.network.VillagerStudyStatePayload;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class VillagerStudyClientCache {
    private static final Map<UUID, Entry> BY_UUID = new HashMap<>();
    private static final Map<Integer, UUID> ENTITY_TO_UUID = new HashMap<>();

    private VillagerStudyClientCache() {
    }

    public static void accept(VillagerStudyStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        long receivedAt = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        VillagerSkill skill = VillagerSkill.bySerializedName(payload.skillId());
        Entry entry = new Entry(
                payload.entityId(),
                payload.villagerId(),
                payload.featureEnabled(),
                skill,
                Math.max(0, payload.activeTicks()),
                Math.max(1, payload.durationTicks()),
                skill != null && payload.paused(),
                Math.max(0L, payload.cooldownRemainingTicks()),
                receivedAt);
        BY_UUID.put(payload.villagerId(), entry);
        ENTITY_TO_UUID.put(payload.entityId(), payload.villagerId());
    }

    public static Optional<Entry> get(int entityId) {
        UUID uuid = ENTITY_TO_UUID.get(entityId);
        return uuid == null ? Optional.empty() : Optional.ofNullable(BY_UUID.get(uuid));
    }

    public static boolean isActive(Villager villager) {
        return villager != null && get(villager.getId()).map(Entry::active).orElse(false);
    }

    public static void clear() {
        BY_UUID.clear();
        ENTITY_TO_UUID.clear();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public record Entry(
            int entityId,
            UUID villagerId,
            boolean featureEnabled,
            VillagerSkill skill,
            int activeTicks,
            int durationTicks,
            boolean paused,
            long cooldownRemainingAtSync,
            long receivedAtGameTime
    ) {
        public boolean studying() {
            return this.skill != null;
        }

        public boolean active() {
            return studying() && !this.paused;
        }

        public int displayedActiveTicks() {
            if (!active()) {
                return Math.min(this.activeTicks, this.durationTicks);
            }
            Minecraft minecraft = Minecraft.getInstance();
            long now = minecraft.level == null ? this.receivedAtGameTime : minecraft.level.getGameTime();
            long elapsed = Math.max(0L, now - this.receivedAtGameTime);
            return (int) Math.min(this.durationTicks, this.activeTicks + elapsed);
        }

        public long cooldownRemaining() {
            Minecraft minecraft = Minecraft.getInstance();
            long now = minecraft.level == null ? this.receivedAtGameTime : minecraft.level.getGameTime();
            return Math.max(0L, this.cooldownRemainingAtSync - Math.max(0L, now - this.receivedAtGameTime));
        }
    }
}
