package com.jvn.villagerretaliation.mood;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueReputationEffect;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class VillagerMoodService {
    public static final long SHORT_DECAY_TICKS = 20L * 60L * 4L;
    public static final long MEDIUM_DECAY_TICKS = 20L * 60L * 12L;
    public static final long LONG_DECAY_TICKS = 20L * 60L * 30L;

    private VillagerMoodService() {
    }

    public static VillagerMoodState mood(ServerLevel level, AbstractVillager villager) {
        if (level == null || villager == null || !VillagerRetaliationConfig.ENABLE_VILLAGER_MOODS.get()) {
            return VillagerMoodState.DEFAULT;
        }

        VillagerMoodSavedData data = VillagerMoodSavedData.get(level);
        VillagerMoodState rawState = data.get(villager.getUUID());
        VillagerMoodState decayedState = rawState.withEffectiveDecay(level.getGameTime());
        if (decayedState.isNeutral() && !rawState.isNeutral()) {
            data.put(villager.getUUID(), decayedState);
        }
        return decayedState;
    }

    public static void setMood(
            ServerLevel level,
            AbstractVillager villager,
            VillagerMood mood,
            int intensity,
            String causeTag,
            UUID sourcePlayerId,
            UUID sourceEntityId,
            long decayTicks) {
        if (level == null || villager == null || !VillagerRetaliationConfig.ENABLE_VILLAGER_MOODS.get()) {
            return;
        }

        long gameTime = level.getGameTime();
        VillagerMood safeMood = mood == null ? VillagerMood.NEUTRAL : mood;
        int adjustedIntensity = VillagerSocialAttributeBehavior.adjustMoodIntensity(level, villager, safeMood, intensity);
        long adjustedDecayTicks = VillagerSocialAttributeBehavior.adjustMoodDecay(level, villager, safeMood, decayTicks);
        VillagerMoodSavedData data = VillagerMoodSavedData.get(level);
        VillagerMoodState current = data.get(villager.getUUID()).withEffectiveDecay(gameTime);
        VillagerMoodState updated = merge(
                current,
                VillagerMoodState.of(safeMood, adjustedIntensity, causeTag, sourcePlayerId, sourceEntityId, gameTime, adjustedDecayTicks),
                gameTime
        );
        data.put(villager.getUUID(), updated);
    }

    public static void clearMood(ServerLevel level, AbstractVillager villager) {
        if (level == null || villager == null) {
            return;
        }
        VillagerMoodSavedData.get(level).put(villager.getUUID(), VillagerMoodState.neutral(level.getGameTime()));
    }

    public static void recordGift(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            VillagerGiftPreferences.GiftReaction reaction,
            int reputationValue) {
        VillagerMood mood = switch (reaction) {
            case LOVED, LIKED -> VillagerMood.GRATEFUL;
            case NEUTRAL -> VillagerMood.CONTENT;
            case DISLIKED -> VillagerMood.SUSPICIOUS;
            case HATED -> VillagerMood.ANGRY;
        };
        int intensity = switch (reaction) {
            case LOVED -> 58;
            case LIKED -> 42;
            case NEUTRAL -> 18;
            case DISLIKED -> 36;
            case HATED -> 54;
        };
        if (reputationValue != 0) {
            intensity += Math.min(10, Math.abs(reputationValue) / 4);
        }
        setMood(
                level,
                villager,
                mood,
                intensity,
                "gift:" + reaction.name().toLowerCase(java.util.Locale.ROOT),
                player.getUUID(),
                player.getUUID(),
                reaction == VillagerGiftPreferences.GiftReaction.NEUTRAL ? SHORT_DECAY_TICKS : MEDIUM_DECAY_TICKS
        );
    }

    public static void recordDialogueEffect(
            DialogueContext context,
            DialogueRequestType requestType,
            DialogueReputationEffect reputationEffect) {
        if (context == null || reputationEffect == null || !reputationEffect.applied() || reputationEffect.reputationDelta() == 0) {
            return;
        }

        int delta = reputationEffect.reputationDelta();
        VillagerMood mood;
        int intensity;
        long decayTicks = SHORT_DECAY_TICKS;
        if (delta > 0) {
            mood = switch (requestType) {
                case APOLOGY -> VillagerMood.HOPEFUL;
                case COMBAT_SURVIVAL_REPORT, VILLAGE_DEFENSE_REPORT, RAID_VICTORY_ACKNOWLEDGEMENT -> VillagerMood.PROUD;
                default -> VillagerMood.CONTENT;
            };
            intensity = 24 + Math.min(24, delta * 3);
        } else {
            mood = requestType == DialogueRequestType.INSULT ? VillagerMood.ANGRY : VillagerMood.STRESSED;
            intensity = 28 + Math.min(24, Math.abs(delta) * 4);
            decayTicks = MEDIUM_DECAY_TICKS;
        }

        setMood(
                context.level(),
                context.villager(),
                mood,
                intensity,
                "dialogue:" + requestType.name().toLowerCase(java.util.Locale.ROOT),
                context.player().getUUID(),
                context.player().getUUID(),
                decayTicks
        );
    }

    public static void recordVillagerDamaged(ServerLevel level, AbstractVillager villager, Entity attacker) {
        if (!(villager instanceof Villager villageResident)) {
            return;
        }

        if (attacker instanceof Player player) {
            setMood(level, villageResident, VillagerMood.ANGRY, 62, "player_attack", player.getUUID(), player.getUUID(), LONG_DECAY_TICKS);
        } else if (attacker instanceof Enemy) {
            VillagerMood mood = VillagerSocialAttributeBehavior.value(level, villageResident, VillagerSocialAttribute.GUTS) >= 62
                    ? VillagerMood.PROTECTIVE
                    : VillagerMood.AFRAID;
            setMood(level, villageResident, mood, 44, "hostile_attack", null, attacker.getUUID(), MEDIUM_DECAY_TICKS);
        } else if (attacker instanceof LivingEntity livingEntity) {
            setMood(level, villageResident, VillagerMood.STRESSED, 32, "damage", null, livingEntity.getUUID(), SHORT_DECAY_TICKS);
        }
    }

    public static void recordVillagerDeath(ServerLevel level, AbstractVillager deceased, Entity attacker, double radius) {
        if (!(deceased instanceof Villager)) {
            return;
        }

        AABB area = deceased.getBoundingBox().inflate(radius);
        for (Villager witness : level.getEntitiesOfClass(Villager.class, area, villager -> villager != deceased && villager.isAlive())) {
            UUID sourcePlayerId = attacker instanceof Player player ? player.getUUID() : null;
            UUID sourceEntityId = attacker == null ? deceased.getUUID() : attacker.getUUID();
            VillagerMood mood = sourcePlayerId == null ? VillagerMood.GRIEVING : VillagerMood.PROTECTIVE;
            int intensity = sourcePlayerId == null ? 50 : 64;
            setMood(level, witness, mood, intensity, "villager_death", sourcePlayerId, sourceEntityId, LONG_DECAY_TICKS);
        }
    }

    public static void recordVillageEvent(ServerLevel level, Villager villager, VillageEventMemory.EventTag tag, Entity source) {
        if (tag == null) {
            return;
        }

        switch (tag) {
            case RAID, NIGHT_ATTACK -> setMood(level, villager, VillagerMood.PROTECTIVE, 42, eventTagName(tag), null, sourceId(source), MEDIUM_DECAY_TICKS);
            case THUNDERSTORM, SANDSTORM, SNOWSTORM, VILLAGE_FIRE -> setMood(level, villager, VillagerMood.STRESSED, 28, eventTagName(tag), null, sourceId(source), SHORT_DECAY_TICKS);
            case PLAYER_DEFENDED_VILLAGE, PLAYER_DEFENDED_RAID -> {
                if (source instanceof Player player) {
                    setMood(level, villager, VillagerMood.GRATEFUL, 40, eventTagName(tag), player.getUUID(), player.getUUID(), MEDIUM_DECAY_TICKS);
                }
            }
            default -> {
            }
        }
    }

    public static void recordRetaliationStarted(ServerLevel level, Villager villager, LivingEntity target) {
        if (target instanceof Player player) {
            setMood(level, villager, VillagerMood.ANGRY, 58, "retaliation", player.getUUID(), player.getUUID(), MEDIUM_DECAY_TICKS);
        } else if (target != null) {
            setMood(level, villager, VillagerMood.PROTECTIVE, 46, "retaliation", null, target.getUUID(), MEDIUM_DECAY_TICKS);
        }
    }

    public static void recordFleeStarted(ServerLevel level, Villager villager, LivingEntity hostile) {
        setMood(level, villager, VillagerMood.AFRAID, 38, "flee", null, sourceId(hostile), SHORT_DECAY_TICKS);
    }

    public static void recordCombatSurvival(ServerLevel level, Villager villager, ServerPlayer player, String eventKind) {
        setMood(
                level,
                villager,
                "raid".equals(eventKind) ? VillagerMood.PROUD : VillagerMood.HOPEFUL,
                "raid".equals(eventKind) ? 46 : 34,
                "combat_survival:" + (eventKind == null || eventKind.isBlank() ? "danger" : eventKind),
                player.getUUID(),
                player.getUUID(),
                MEDIUM_DECAY_TICKS
        );
    }

    private static VillagerMoodState merge(VillagerMoodState current, VillagerMoodState incoming, long gameTime) {
        if (incoming.isNeutral()) {
            return incoming;
        }
        if (current.isNeutral() || incoming.intensity() >= current.intensity() || incoming.primaryMood() == current.primaryMood()) {
            int mergedIntensity = incoming.primaryMood() == current.primaryMood()
                    ? Math.max(incoming.intensity(), Math.min(VillagerMoodState.MAX_INTENSITY, current.intensity() + incoming.intensity() / 4))
                    : incoming.intensity();
            return VillagerMoodState.of(
                    incoming.primaryMood(),
                    mergedIntensity,
                    incoming.causeTag(),
                    incoming.sourcePlayerId(),
                    incoming.sourceEntityId(),
                    gameTime,
                    incoming.decayTicks()
            );
        }
        return current;
    }

    private static UUID sourceId(Entity source) {
        return source == null ? null : source.getUUID();
    }

    private static String eventTagName(VillageEventMemory.EventTag tag) {
        return tag.name().toLowerCase(java.util.Locale.ROOT);
    }
}
