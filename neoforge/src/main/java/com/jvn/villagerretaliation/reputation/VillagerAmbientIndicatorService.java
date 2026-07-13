package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.DialogueReputationEffect;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class VillagerAmbientIndicatorService {
    private static final long MURMUR_SCAN_INTERVAL_TICKS = 80L;
    private static final long MURMUR_BASE_COOLDOWN_TICKS = 20L * 12L;
    private static final long SLEEP_BASE_COOLDOWN_TICKS = 20L * 4L;
    private static final long ALERT_COOLDOWN_TICKS = 20L * 3L;
    private static final long RETALIATION_START_COOLDOWN_TICKS = 20L * 3L;
    private static final long FLEE_START_COOLDOWN_TICKS = 20L * 5L;
    private static final long ATTACK_LANDED_COOLDOWN_TICKS = 20L;
    private static final double MURMUR_RADIUS = 5.5D;
    private static final double ALERT_WITNESS_RADIUS = 10.0D;
    private static final int MAX_ALERT_WITNESSES = 6;
    private static final Map<UUID, Long> NEXT_MURMUR_TICK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ALERT_TICK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SLEEP_TICK = new HashMap<>();
    private static final Map<UUID, RetaliationAnnouncementState> RETALIATION_ANNOUNCEMENTS = new HashMap<>();
    private static final Map<UUID, RetaliationAnnouncementState> FLEE_ANNOUNCEMENTS = new HashMap<>();
    private static final Map<UUID, RetaliationAnnouncementState> ATTACK_LANDED_ANNOUNCEMENTS = new HashMap<>();

    private VillagerAmbientIndicatorService() {
    }

    public static void clearRuntimeState() {
        NEXT_MURMUR_TICK.clear();
        NEXT_ALERT_TICK.clear();
        NEXT_SLEEP_TICK.clear();
        RETALIATION_ANNOUNCEMENTS.clear();
        FLEE_ANNOUNCEMENTS.clear();
        ATTACK_LANDED_ANNOUNCEMENTS.clear();
    }

    public static void maybeMurmurNearPlayers(ServerLevel level, AbstractVillager villager) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !VillagerRetaliationConfig.ENABLE_AMBIENT_MURMURS.get()
                || !villager.isAlive()
                || villager.isTrading()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (!TickThrottle.isSpreadTick(villager.getUUID().getMostSignificantBits(), gameTime, MURMUR_SCAN_INTERVAL_TICKS)
                || gameTime < NEXT_MURMUR_TICK.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        Player player = findMurmurTarget(level, villager);
        if (player == null) {
            delayNextMurmur(villager, gameTime, 3);
            return;
        }

        if (VillagerNotifications.sendWorldText(
                level,
                villager,
                player,
                "ambient.player_item",
                Map.of(),
                VillagerWorldTextIndicatorKind.MURMUR,
                "")) {
            delayNextMurmur(villager, gameTime, 10 + villager.getRandom().nextInt(12));
            pruneCooldowns(gameTime);
            return;
        }

        if (villager.getRandom().nextInt(100) >= 30) {
            delayNextMurmur(villager, gameTime, 3);
            return;
        }

        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        String text = randomMurmur(villager.getRandom(), reputationLevel);
        emit(level, villager, player, "ambient.murmur", VillagerWorldTextIndicatorKind.MURMUR, text);
        delayNextMurmur(villager, gameTime, 10 + villager.getRandom().nextInt(12));
        pruneCooldowns(gameTime);
    }

    public static void maybeEmitSleepIndicator(ServerLevel level, Villager villager) {
        if (!VillagerRetaliationConfig.ENABLE_SLEEP_INDICATORS.get()) {
            NEXT_SLEEP_TICK.remove(villager.getUUID());
            return;
        }
        if (!villager.isAlive() || !villager.isSleeping()) {
            NEXT_SLEEP_TICK.remove(villager.getUUID());
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime < NEXT_SLEEP_TICK.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        boolean breathing = villager.getRandom().nextInt(100) < 52;
        String trigger = breathing ? "ambient.sleep_breathing" : "ambient.sleep_murmur";
        String text = breathing
                ? randomSleepBreathing(villager.getRandom())
                : randomSleepMurmur(villager.getRandom(), villager.getVillagerData().getProfession());
        emit(level, villager, null, trigger, VillagerWorldTextIndicatorKind.SLEEP, text);
        NEXT_SLEEP_TICK.put(villager.getUUID(), gameTime + SLEEP_BASE_COOLDOWN_TICKS + villager.getRandom().nextInt(20 * 4));
        pruneCooldowns(gameTime);
    }

    public static void onVillagerDamaged(ServerLevel level, AbstractVillager damaged, Entity attacker) {
        if (!VillagerRetaliationConfig.ENABLE_DAMAGE_ALERTS.get() || !damaged.isAlive()) {
            return;
        }

        Player attackingPlayer = attacker instanceof Player player ? player : null;
        boolean victimAlerted = emitAlert(
                level,
                damaged,
                attackingPlayer,
                attackingPlayer == null ? "alert.villager_damaged" : "alert.player_attacked_villager",
                attackingPlayer == null ? "" : "alert.villager_damaged",
                alertReplacements(damaged, attacker),
                alertTextForDamaged(damaged, attacker));
        if (victimAlerted && damaged instanceof Villager villager && villager.isBaby()) {
            broadcastBabyDamagedChat(level, villager, attacker);
        }

        AABB area = damaged.getBoundingBox().inflate(ALERT_WITNESS_RADIUS);
        int alerted = 0;
        for (AbstractVillager witness : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            if (witness == damaged || !witness.isAlive() || witness.isBaby()) {
                continue;
            }
            if (!witness.hasLineOfSight(damaged)) {
                continue;
            }
            emitAlert(
                    level,
                    witness,
                    attackingPlayer,
                    attacker instanceof Player ? "alert.witness_attack.player" : "alert.witness_attack",
                    "",
                    alertReplacements(witness, attacker),
                    alertTextForWitness(witness, attacker)
            );
            alerted++;
            if (alerted >= MAX_ALERT_WITNESSES) {
                return;
            }
        }
    }

    public static void onVillagerKilled(ServerLevel level, AbstractVillager killed, Entity attacker) {
        if (!VillagerRetaliationConfig.ENABLE_DAMAGE_ALERTS.get()) {
            return;
        }
        AABB area = killed.getBoundingBox().inflate(ALERT_WITNESS_RADIUS);
        int alerted = 0;
        for (AbstractVillager witness : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            if (witness == killed
                    || !witness.isAlive()
                    || witness.isBaby() && !VillagerRetaliationConfig.BABY_VILLAGERS_FLEE_WITNESSED_DEATHS.get()) {
                continue;
            }
            if (!witness.hasLineOfSight(killed)) {
                continue;
            }
            boolean witnessAlerted = emitAlert(
                    level,
                    witness,
                    attacker instanceof Player player ? player : null,
                    attacker instanceof Player ? "alert.witness_death.player" : "alert.witness_death",
                    "",
                    alertReplacements(witness, attacker),
                    alertTextForDeath(witness, attacker)
            );
            if (witnessAlerted && witness instanceof Villager villager && villager.isBaby()) {
                broadcastBabyWitnessedDeathChat(level, villager, attacker);
            }
            alerted++;
            if (alerted >= MAX_ALERT_WITNESSES) {
                return;
            }
        }
    }

    public static void onPlayerKilled(ServerLevel level, AbstractVillager killer, ServerPlayer player) {
        if (!VillagerRetaliationConfig.ENABLE_COMBAT_ALERTS.get() || !killer.isAlive()) {
            return;
        }

        String fallbackText = random(killer.getRandom(), "Stay down", "You were warned", "Enough");
        VillagerNotifications.sendWorldText(
                level,
                killer,
                player,
                "combat.player_killed",
                playerKillReplacements(killer, player),
                VillagerWorldTextIndicatorKind.ALERT,
                fallbackText
        );
    }

    public static void onRetaliationStarted(ServerLevel level, AbstractVillager villager, LivingEntity target) {
        if (!villager.isAlive() || !target.isAlive()) {
            return;
        }

        long gameTime = level.getGameTime();
        RetaliationAnnouncementState state = RETALIATION_ANNOUNCEMENTS.get(villager.getUUID());
        if (state != null
                && state.targetId().equals(target.getUUID())
                && gameTime < state.nextAllowedTick()) {
            return;
        }

        RETALIATION_ANNOUNCEMENTS.put(
                villager.getUUID(),
                new RetaliationAnnouncementState(target.getUUID(), gameTime + RETALIATION_START_COOLDOWN_TICKS)
        );
        VillageEventMemory.rememberRetaliation(
                level,
                villager.blockPosition(),
                villager,
                target,
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString()
        );
        if (villager instanceof Villager resident) {
            ForcedDialogueService.triggerRetaliationChat(level, resident, target);
        }
        if (VillagerRetaliationConfig.ENABLE_COMBAT_ALERTS.get()) {
            VillagerNotifications.sendWorldText(
                    level,
                    villager,
                    target instanceof Player player ? player : null,
                    target,
                    "combat.retaliation_started",
                    retaliationReplacements(villager, target),
                    VillagerWorldTextIndicatorKind.ALERT,
                    ""
            );
        }
        pruneCooldowns(gameTime);
    }

    public static void onFleeStarted(ServerLevel level, AbstractVillager villager, LivingEntity target) {
        if (!VillagerRetaliationConfig.ENABLE_COMBAT_ALERTS.get()
                || !villager.isAlive()
                || target == null
                || !target.isAlive()) {
            return;
        }

        long gameTime = level.getGameTime();
        RetaliationAnnouncementState state = FLEE_ANNOUNCEMENTS.get(villager.getUUID());
        if (state != null
                && state.targetId().equals(target.getUUID())
                && gameTime < state.nextAllowedTick()) {
            return;
        }

        FLEE_ANNOUNCEMENTS.put(
                villager.getUUID(),
                new RetaliationAnnouncementState(target.getUUID(), gameTime + FLEE_START_COOLDOWN_TICKS)
        );
        VillagerNotifications.sendWorldText(
                level,
                villager,
                target instanceof Player player ? player : null,
                target,
                "combat.flee_started",
                retaliationReplacements(villager, target),
                VillagerWorldTextIndicatorKind.ALERT,
                ""
        );
        pruneCooldowns(gameTime);
    }

    public static void onAttackLanded(ServerLevel level, AbstractVillager villager, LivingEntity target) {
        if (!VillagerRetaliationConfig.ENABLE_COMBAT_ALERTS.get() || !villager.isAlive() || !target.isAlive()) {
            return;
        }

        long gameTime = level.getGameTime();
        RetaliationAnnouncementState state = ATTACK_LANDED_ANNOUNCEMENTS.get(villager.getUUID());
        if (state != null
                && state.targetId().equals(target.getUUID())
                && gameTime < state.nextAllowedTick()) {
            return;
        }

        ATTACK_LANDED_ANNOUNCEMENTS.put(
                villager.getUUID(),
                new RetaliationAnnouncementState(target.getUUID(), gameTime + ATTACK_LANDED_COOLDOWN_TICKS)
        );
        VillagerNotifications.sendWorldText(
                level,
                villager,
                target instanceof Player player ? player : null,
                target,
                "combat.attack_landed",
                retaliationReplacements(villager, target),
                VillagerWorldTextIndicatorKind.ALERT,
                random(villager.getRandom(), "Take that", "Got you", "There")
        );
        pruneCooldowns(gameTime);
    }

    public static void onTradeCompleted(ServerLevel level, AbstractVillager villager, Player player) {
        if (!VillagerRetaliationConfig.ENABLE_TRADE_AND_GIFT_WORLD_TEXT.get()) {
            return;
        }
        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        String text = switch (reputationLevel) {
            case ROYALTY, REVERED -> random(villager.getRandom(), "A pleasure", "For you", "Any time");
            case RESPECTED, TRUSTED -> random(villager.getRandom(), "Good trade", "Fair deal", "Thanks");
            case SUSPICIOUS, HOSTILE, DESPISED -> random(villager.getRandom(), "Fine", "Be quick", "That's all");
            case FEARED -> random(villager.getRandom(), "Please go", "We're done", "Take it");
            default -> random(villager.getRandom(), "Good trade", "Done deal", "Thanks");
        };
        emit(level, villager, player, "trade.completed", VillagerWorldTextIndicatorKind.TRADE, text);
    }

    public static void onConversationOpened(ServerLevel level, Villager villager, Player player) {
        if (!canShowFriendlyConversationText(level, villager, player)) {
            return;
        }

        DialogueContext.TimeOfDay timeOfDay = timeOfDay(level);
        emit(
                level,
                villager,
                player,
                "conversation.opening." + timeTrigger(timeOfDay),
                "conversation.opening",
                VillagerWorldTextIndicatorKind.DIALOGUE,
                randomGreeting(villager.getRandom(), timeOfDay)
        );
    }

    public static void onConversationClosed(ServerLevel level, Villager villager, Player player) {
        if (!canShowFriendlyConversationText(level, villager, player)) {
            return;
        }

        DialogueContext.TimeOfDay timeOfDay = timeOfDay(level);
        emit(
                level,
                villager,
                player,
                "conversation.closing." + timeTrigger(timeOfDay),
                "conversation.closing",
                VillagerWorldTextIndicatorKind.DIALOGUE,
                randomGoodbye(villager.getRandom(), timeOfDay)
        );
    }

    public static void onDialogueResponse(
            ServerLevel level,
            Villager villager,
            Player player,
            String optionId,
            DialogueRequestType requestType,
            DialogueReputationEffect reputationEffect) {
        VillagerWorldTextIndicatorKind kind = VillagerWorldTextIndicatorKind.DIALOGUE;
        String baseTrigger = "dialogue." + requestType.name().toLowerCase(java.util.Locale.ROOT);
        String trigger = optionId == null || optionId.isBlank() ? baseTrigger : "dialogue.option." + optionId;
        if (reputationEffect.applied() && reputationEffect.reputationDelta() > 0) {
            kind = VillagerWorldTextIndicatorKind.POSITIVE;
            baseTrigger += ".positive";
            trigger += ".positive";
        } else if (reputationEffect.applied() && reputationEffect.reputationDelta() < 0) {
            kind = VillagerWorldTextIndicatorKind.NEGATIVE;
            baseTrigger += ".negative";
            trigger += ".negative";
        }

        String text;
        if (reputationEffect.blockedByCooldown()) {
            text = random(villager.getRandom(), "Already asked", "Enough now", "Again?");
            kind = VillagerWorldTextIndicatorKind.NEGATIVE;
            trigger = "dialogue.cooldown";
            baseTrigger = "";
        } else {
            text = switch (requestType) {
                case GREETING -> random(villager.getRandom(), "Hello", "Good day", "Hm?");
                case QUESTION -> random(villager.getRandom(), "Let's see", "Maybe", "About that");
                case GIFT_PREFERENCES -> random(villager.getRandom(), "Gifts", "Thoughtful", "Maybe");
                case GIFT_ADVICE_FOLLOWUP -> random(villager.getRandom(), "About that", "Gift talk", "Advice");
                case MAP_REPORT -> random(villager.getRandom(), "You found it", "Returned", "Mapped");
                case STORY_HINT_REPORT -> random(villager.getRandom(), "You found it", "Returned", "Confirmed");
                case SHARE_STORY -> random(villager.getRandom(), "A warning", "Tell me", "That place");
                case COMBAT_SURVIVAL_REPORT -> random(villager.getRandom(), "Back safe", "Still standing", "Made it");
                case GEAR_REPORT -> random(villager.getRandom(), "Gear held", "Equipped", "Prepared");
                case RECRUITMENT_FOLLOWUP -> random(villager.getRandom(), "Back home", "Returned", "Follow-up");
                case CURED_RECOGNITION -> random(villager.getRandom(), "I remember", "You were there", "Back again");
                case VILLAGE_EVENT_REPORT -> random(villager.getRandom(), "Everyone?", "Afterward", "Checking");
                case APOLOGY -> random(villager.getRandom(), "Apology", "Heard", "Careful");
                case VILLAGE_DEFENSE_REPORT -> random(villager.getRandom(), "Raid ended", "You fought", "Afterward");
                case STORY -> random(villager.getRandom(), "Listen", "Long story", "I remember");
                case JOKE -> random(villager.getRandom(), "Heh", "Not bad", "Oh dear");
                case INSULT -> random(villager.getRandom(), "Careful", "Really?", "Watch it");
            };
        }

        emit(level, villager, player, trigger, baseTrigger, kind, text);
    }

    public static void onHighReputationGift(AbstractVillager villager) {
        if (!VillagerRetaliationConfig.ENABLE_TRADE_AND_GIFT_WORLD_TEXT.get()) {
            return;
        }
        if (villager.level() instanceof ServerLevel level) {
            emit(level, villager, null, "gift.high_reputation", VillagerWorldTextIndicatorKind.POSITIVE,
                    random(villager.getRandom(), "For you", "Take this", "You've earned it"));
        }
    }

    public static void onGiftReceived(AbstractVillager villager, int reputationValue) {
        if (!VillagerRetaliationConfig.ENABLE_TRADE_AND_GIFT_WORLD_TEXT.get()) {
            return;
        }
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (reputationValue > 0) {
            emit(level, villager, null, "gift.world.liked", VillagerWorldTextIndicatorKind.POSITIVE,
                    random(villager.getRandom(), "Thank you", "How kind", "Lovely"));
        } else if (reputationValue < 0) {
            emit(level, villager, null, "gift.world.disliked", VillagerWorldTextIndicatorKind.NEGATIVE,
                    random(villager.getRandom(), "No thanks", "Not this", "Really?"));
        } else {
            emit(level, villager, null, "gift.world.neutral", VillagerWorldTextIndicatorKind.DIALOGUE,
                    random(villager.getRandom(), "Hm", "Thanks", "Alright"));
        }
    }

    public static void onTradeRefused(AbstractVillager villager) {
        if (!VillagerRetaliationConfig.ENABLE_TRADE_AND_GIFT_WORLD_TEXT.get()) {
            return;
        }
        if (villager.level() instanceof ServerLevel level) {
            emit(level, villager, null, "trade.refused", VillagerWorldTextIndicatorKind.NEGATIVE,
                    random(villager.getRandom(), "No trades", "Not you", "Leave"));
        }
    }

    private static Player findMurmurTarget(ServerLevel level, AbstractVillager villager) {
        AABB area = villager.getBoundingBox().inflate(MURMUR_RADIUS);
        Player closest = null;
        double closestDistanceSqr = Double.MAX_VALUE;
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            double distanceSqr = villager.distanceToSqr(player);
            if (distanceSqr > MURMUR_RADIUS * MURMUR_RADIUS || !villager.hasLineOfSight(player)) {
                continue;
            }
            if (distanceSqr < closestDistanceSqr) {
                closest = player;
                closestDistanceSqr = distanceSqr;
            }
        }
        return closest;
    }

    private static boolean emitAlert(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            String trigger,
            String fallbackTrigger,
            Map<String, String> replacements,
            String text) {
        long gameTime = level.getGameTime();
        if (gameTime < NEXT_ALERT_TICK.getOrDefault(villager.getUUID(), 0L)) {
            return false;
        }
        NEXT_ALERT_TICK.put(villager.getUUID(), gameTime + ALERT_COOLDOWN_TICKS);
        if (text == null || text.isBlank()) {
            return false;
        }
        return VillagerNotifications.sendWorldText(
                level,
                villager,
                player,
                trigger,
                fallbackTrigger,
                replacements,
                VillagerWorldTextIndicatorKind.ALERT,
                text);
    }

    private static void emit(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            String trigger,
            VillagerWorldTextIndicatorKind fallbackKind,
            String fallbackText) {
        emit(level, villager, player, trigger, "", fallbackKind, fallbackText);
    }

    private static void emit(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            String trigger,
            String fallbackTrigger,
            VillagerWorldTextIndicatorKind fallbackKind,
            String fallbackText) {
        if (fallbackText == null || fallbackText.isBlank()) {
            return;
        }
        VillagerNotifications.sendWorldText(level, villager, player, trigger, fallbackTrigger, Map.of(), fallbackKind, fallbackText);
    }

    private static Map<String, String> playerKillReplacements(AbstractVillager villager, ServerPlayer player) {
        return VillagerNotifications.replacements(
                "player", player.getGameProfile().getName(),
                "victim", player.getGameProfile().getName(),
                "villager", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                "villager_name", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                "villager_kind", villagerKind(villager),
                "profession", villagerProfessionName(villager)
        );
    }

    private static Map<String, String> retaliationReplacements(AbstractVillager villager, LivingEntity target) {
        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        String targetName = target.getDisplayName().getString();
        String targetKind = target.getType().getDescription().getString().toLowerCase(Locale.ROOT);
        return VillagerNotifications.replacements(
                "target", targetName,
                "target_article", VillagerInteractionTextUtil.withIndefiniteArticle(targetName),
                "target_name", targetName,
                "target_kind", targetKind,
                "target_type", targetTypeId == null ? "" : targetTypeId.toString(),
                "player", target instanceof Player player ? player.getGameProfile().getName() : "",
                "villager", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                "villager_name", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                "villager_kind", villagerKind(villager),
                "profession", villagerProfessionName(villager)
        );
    }

    private static Map<String, String> alertReplacements(AbstractVillager villager, Entity attacker) {
        String attackerName = attacker == null ? "danger" : attacker.getDisplayName().getString();
        return VillagerNotifications.replacements(
                "attacker", attackerName,
                "player", attacker instanceof Player player ? player.getGameProfile().getName() : attackerName,
                "villager", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                "villager_name", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                "villager_kind", villagerKind(villager),
                "profession", villagerProfessionName(villager)
        );
    }

    private static String villagerKind(AbstractVillager villager) {
        return villager instanceof WanderingTrader ? "wandering trader" : "villager";
    }

    private static String villagerProfessionName(AbstractVillager villager) {
        if (villager instanceof Villager resident) {
            return VillagerInteractionTextUtil.professionName(resident.getVillagerData().getProfession(), "villager").toLowerCase(java.util.Locale.ROOT);
        }
        return villagerKind(villager);
    }

    private static void delayNextMurmur(AbstractVillager villager, long gameTime, int extraSeconds) {
        NEXT_MURMUR_TICK.put(villager.getUUID(), gameTime + MURMUR_BASE_COOLDOWN_TICKS + extraSeconds * 20L);
    }

    private static String randomMurmur(RandomSource random, VillagerReputationLevel reputationLevel) {
        return switch (reputationLevel) {
            case ROYALTY -> random(random, "Our hero", "There they are", "Good omen");
            case REVERED -> random(random, "Good folk", "Trusted face", "Welcome back");
            case RESPECTED -> random(random, "Friendly one", "Good day", "Steady hands");
            case TRUSTED -> random(random, "I know them", "Seems alright", "Hello there");
            case SUSPICIOUS -> random(random, "Careful", "Watch them", "Keep distance");
            case HOSTILE -> random(random, "Not welcome", "Stay ready", "Trouble again");
            case DESPISED -> random(random, "Sound the bell", "Not again", "Stay away");
            case FEARED -> random(random, "Stay back", "Please no", "Quiet now");
            default -> random(random, "Hm", "Traveler", "Passing by");
        };
    }

    private static String randomGreeting(RandomSource random, DialogueContext.TimeOfDay timeOfDay) {
        String timeGreeting = switch (timeOfDay) {
            case MORNING -> "Morning";
            case AFTERNOON -> "Afternoon";
            case EVENING -> "Evening";
            case NIGHT -> "";
        };
        if (!timeGreeting.isBlank() && random.nextInt(100) < 35) {
            return timeGreeting;
        }
        return randomFrom(random, "Hello", "Greetings", "Hey", "Hallo", "Hai", "Hi", "Heyo", "Sup", "Ciao");
    }

    private static String randomGoodbye(RandomSource random, DialogueContext.TimeOfDay timeOfDay) {
        if (timeOfDay == DialogueContext.TimeOfDay.NIGHT && random.nextInt(100) < 45) {
            return randomFrom(random, "Goodnight", "Night", "G'night");
        }
        return randomFrom(random, "Bye", "Cya", "Goodbye", "Later", "Peace");
    }

    private static boolean canShowFriendlyConversationText(ServerLevel level, AbstractVillager villager, Player player) {
        if (player == null) {
            return false;
        }
        return VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    private static DialogueContext.TimeOfDay timeOfDay(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime < 6000L) {
            return DialogueContext.TimeOfDay.MORNING;
        }
        if (dayTime < 12000L) {
            return DialogueContext.TimeOfDay.AFTERNOON;
        }
        if (dayTime < 14000L) {
            return DialogueContext.TimeOfDay.EVENING;
        }
        return DialogueContext.TimeOfDay.NIGHT;
    }

    private static String timeTrigger(DialogueContext.TimeOfDay timeOfDay) {
        return timeOfDay.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String randomSleepBreathing(RandomSource random) {
        return randomFrom(random, "ZZZ", "Zzz", "*snores*", "*mumbles*");
    }

    private static String randomSleepMurmur(RandomSource random, VillagerProfession profession) {
        String professionText = professionSleepText(random, profession);
        if (random.nextInt(100) < 35) {
            return randomFrom(random, "warm bed", "soft clouds", "quiet night", "home soon", "moonlight");
        }
        return professionText;
    }

    private static String professionSleepText(RandomSource random, VillagerProfession profession) {
        if (profession == VillagerProfession.ARMORER) {
            return randomFrom(random, "strong plates", "no dents", "good shield");
        }
        if (profession == VillagerProfession.BUTCHER) {
            return randomFrom(random, "smoked cuts", "full hooks", "warm stew");
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return randomFrom(random, "new roads", "wide map", "lost path");
        }
        if (profession == VillagerProfession.CLERIC) {
            return randomFrom(random, "sweet potion", "soft bells", "clear omen");
        }
        if (profession == VillagerProfession.FARMER) {
            return randomFrom(random, "ripe wheat", "good harvest", "soft soil");
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return randomFrom(random, "calm water", "full net", "big fish");
        }
        if (profession == VillagerProfession.FLETCHER) {
            return randomFrom(random, "true arrow", "clean feathers", "steady bow");
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return randomFrom(random, "soft leather", "good stitching", "warm saddle");
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return randomFrom(random, "quiet pages", "lost book", "fresh ink");
        }
        if (profession == VillagerProfession.MASON) {
            return randomFrom(random, "smooth stone", "straight wall", "good bricks");
        }
        if (profession == VillagerProfession.NITWIT) {
            return randomFrom(random, "nice clouds", "big potato", "where hat");
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return randomFrom(random, "fluffy wool", "quiet flock", "soft sheep");
        }
        if (profession == VillagerProfession.TOOLSMITH) {
            return randomFrom(random, "sharp pick", "sturdy handle", "good hammer");
        }
        if (profession == VillagerProfession.WEAPONSMITH) {
            return randomFrom(random, "sharp blade", "hot forge", "no rust");
        }
        return randomFrom(random, "warm bed", "soft clouds", "quiet night");
    }

    private static String alertTextForWitness(AbstractVillager witness, Entity attacker) {
        if (attacker instanceof Player) {
            return random(witness.getRandom(), "!", "Stop!", "Help!");
        }
        return random(witness.getRandom(), "!", "Danger!", "Run!");
    }

    private static String alertTextForDamaged(AbstractVillager damaged, Entity attacker) {
        if (damaged.isBaby()) {
            return random(damaged.getRandom(), "Ow!", "Stop!", "Help!");
        }
        return "!";
    }

    private static String alertTextForDeath(AbstractVillager witness, Entity attacker) {
        if (witness.isBaby()) {
            return random(witness.getRandom(), "No no no!", "Run!", "Help!");
        }
        if (attacker instanceof Player) {
            return random(witness.getRandom(), "No!", "Murder!", "Bell!");
        }
        return random(witness.getRandom(), "No!", "Danger!", "Bell!");
    }

    private static void broadcastBabyDamagedChat(ServerLevel level, Villager villager, Entity attacker) {
        VillagerInteractionService.broadcastForcedVillagerChat(
                level,
                villager,
                babyDamagedChat(level, villager, attacker),
                VillagerInteractionService.villagerSpeakerLabel(villager)
        );
    }

    private static void broadcastBabyWitnessedDeathChat(ServerLevel level, Villager villager, Entity attacker) {
        VillagerInteractionService.broadcastForcedVillagerChat(
                level,
                villager,
                babyWitnessedDeathChat(level, villager, attacker),
                VillagerInteractionService.villagerSpeakerLabel(villager)
        );
    }

    private static String babyDamagedChat(ServerLevel level, Villager villager, Entity attacker) {
        return VillagerDialogueResources.globalMessage(
                level.getServer(),
                villager.getRandom(),
                attacker instanceof Player
                        ? "interaction.ambient.baby_damaged.player"
                        : "interaction.ambient.baby_damaged.other"
        ).orElse("");
    }

    private static String babyWitnessedDeathChat(ServerLevel level, Villager villager, Entity attacker) {
        return VillagerDialogueResources.globalMessage(
                level.getServer(),
                villager.getRandom(),
                attacker instanceof Player
                        ? "interaction.ambient.baby_witnessed_death.player"
                        : "interaction.ambient.baby_witnessed_death.other"
        ).orElse("");
    }

    private static String random(RandomSource random, String first, String second, String third) {
        return switch (random.nextInt(3)) {
            case 0 -> first;
            case 1 -> second;
            default -> third;
        };
    }

    private static String randomFrom(RandomSource random, String... options) {
        return options[random.nextInt(options.length)];
    }

    private static void pruneCooldowns(long gameTime) {
        if (NEXT_MURMUR_TICK.size() > 512) {
            NEXT_MURMUR_TICK.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        }
        if (NEXT_ALERT_TICK.size() > 512) {
            NEXT_ALERT_TICK.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        }
        if (NEXT_SLEEP_TICK.size() > 512) {
            NEXT_SLEEP_TICK.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        }
        if (RETALIATION_ANNOUNCEMENTS.size() > 512) {
            RETALIATION_ANNOUNCEMENTS.entrySet().removeIf(entry -> entry.getValue().nextAllowedTick() < gameTime);
        }
        if (FLEE_ANNOUNCEMENTS.size() > 512) {
            FLEE_ANNOUNCEMENTS.entrySet().removeIf(entry -> entry.getValue().nextAllowedTick() < gameTime);
        }
        if (ATTACK_LANDED_ANNOUNCEMENTS.size() > 512) {
            ATTACK_LANDED_ANNOUNCEMENTS.entrySet().removeIf(entry -> entry.getValue().nextAllowedTick() < gameTime);
        }
    }

    private record RetaliationAnnouncementState(UUID targetId, long nextAllowedTick) {
    }
}
