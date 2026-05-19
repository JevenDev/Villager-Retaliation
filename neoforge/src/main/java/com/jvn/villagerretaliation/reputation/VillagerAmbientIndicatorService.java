package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.DialogueReputationEffect;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorKind;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class VillagerAmbientIndicatorService {
    private static final long MURMUR_SCAN_INTERVAL_TICKS = 80L;
    private static final long MURMUR_BASE_COOLDOWN_TICKS = 20L * 12L;
    private static final long SLEEP_BASE_COOLDOWN_TICKS = 20L * 4L;
    private static final long ALERT_COOLDOWN_TICKS = 20L * 3L;
    private static final double MURMUR_RADIUS = 5.5D;
    private static final double ALERT_WITNESS_RADIUS = 10.0D;
    private static final int MAX_ALERT_WITNESSES = 6;
    private static final Map<UUID, Long> NEXT_MURMUR_TICK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ALERT_TICK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SLEEP_TICK = new HashMap<>();

    private VillagerAmbientIndicatorService() {
    }

    public static void maybeMurmurNearPlayers(ServerLevel level, AbstractVillager villager) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !villager.isAlive()
                || villager.isTrading()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime % MURMUR_SCAN_INTERVAL_TICKS != Math.floorMod(villager.getUUID().getMostSignificantBits(), MURMUR_SCAN_INTERVAL_TICKS)
                || gameTime < NEXT_MURMUR_TICK.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        Player player = findMurmurTarget(level, villager);
        if (player == null || villager.getRandom().nextInt(100) >= 30) {
            delayNextMurmur(villager, gameTime, 3);
            return;
        }

        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        String text = randomMurmur(villager.getRandom(), reputationLevel);
        emit(villager, text, VillagerWorldTextIndicatorKind.MURMUR);
        delayNextMurmur(villager, gameTime, 10 + villager.getRandom().nextInt(12));
        pruneCooldowns(gameTime);
    }

    public static void maybeEmitSleepIndicator(ServerLevel level, Villager villager) {
        if (!villager.isAlive() || !villager.isSleeping()) {
            NEXT_SLEEP_TICK.remove(villager.getUUID());
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime < NEXT_SLEEP_TICK.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        String text = villager.getRandom().nextInt(100) < 52
                ? randomSleepBreathing(villager.getRandom())
                : randomSleepMurmur(villager.getRandom(), villager.getVillagerData().getProfession());
        emit(villager, text, VillagerWorldTextIndicatorKind.SLEEP);
        NEXT_SLEEP_TICK.put(villager.getUUID(), gameTime + SLEEP_BASE_COOLDOWN_TICKS + villager.getRandom().nextInt(20 * 4));
        pruneCooldowns(gameTime);
    }

    public static void onVillagerDamaged(ServerLevel level, AbstractVillager damaged, Entity attacker) {
        if (!damaged.isAlive()) {
            return;
        }

        emitAlert(level, damaged, "!");

        AABB area = damaged.getBoundingBox().inflate(ALERT_WITNESS_RADIUS);
        int alerted = 0;
        for (AbstractVillager witness : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            if (witness == damaged || !witness.isAlive() || witness.isBaby()) {
                continue;
            }
            if (!witness.hasLineOfSight(damaged)) {
                continue;
            }
            emitAlert(level, witness, alertTextForWitness(witness, attacker));
            alerted++;
            if (alerted >= MAX_ALERT_WITNESSES) {
                return;
            }
        }
    }

    public static void onVillagerKilled(ServerLevel level, AbstractVillager killed, Entity attacker) {
        AABB area = killed.getBoundingBox().inflate(ALERT_WITNESS_RADIUS);
        int alerted = 0;
        for (AbstractVillager witness : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            if (witness == killed || !witness.isAlive() || witness.isBaby()) {
                continue;
            }
            if (!witness.hasLineOfSight(killed)) {
                continue;
            }
            emitAlert(level, witness, alertTextForDeath(witness, attacker));
            alerted++;
            if (alerted >= MAX_ALERT_WITNESSES) {
                return;
            }
        }
    }

    public static void onTradeCompleted(ServerLevel level, AbstractVillager villager, Player player) {
        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        String text = switch (reputationLevel) {
            case ROYALTY, REVERED -> random(villager.getRandom(), "A pleasure", "For you", "Any time");
            case RESPECTED, TRUSTED -> random(villager.getRandom(), "Good trade", "Fair deal", "Thanks");
            case SUSPICIOUS, HOSTILE, DESPISED -> random(villager.getRandom(), "Fine", "Be quick", "That's all");
            case FEARED -> random(villager.getRandom(), "Please go", "We're done", "Take it");
            default -> random(villager.getRandom(), "Good trade", "Done deal", "Thanks");
        };
        emit(villager, text, VillagerWorldTextIndicatorKind.TRADE);
    }

    public static void onDialogueResponse(Villager villager, DialogueRequestType requestType, DialogueReputationEffect reputationEffect) {
        VillagerWorldTextIndicatorKind kind = VillagerWorldTextIndicatorKind.DIALOGUE;
        if (reputationEffect.applied() && reputationEffect.reputationDelta() > 0) {
            kind = VillagerWorldTextIndicatorKind.POSITIVE;
        } else if (reputationEffect.applied() && reputationEffect.reputationDelta() < 0) {
            kind = VillagerWorldTextIndicatorKind.NEGATIVE;
        }

        String text;
        if (reputationEffect.blockedByCooldown()) {
            text = random(villager.getRandom(), "Already asked", "Enough now", "Again?");
            kind = VillagerWorldTextIndicatorKind.NEGATIVE;
        } else {
            text = switch (requestType) {
                case GREETING -> random(villager.getRandom(), "Hello", "Good day", "Hm?");
                case QUESTION -> random(villager.getRandom(), "Let's see", "Maybe", "About that");
                case CHAT -> random(villager.getRandom(), "I suppose", "Small talk", "Alright");
                case STORY -> random(villager.getRandom(), "Listen", "Long story", "I remember");
                case JOKE -> random(villager.getRandom(), "Heh", "Not bad", "Oh dear");
                case INSULT -> random(villager.getRandom(), "Careful", "Really?", "Watch it");
            };
        }

        emit(villager, text, kind);
    }

    public static void onHighReputationGift(AbstractVillager villager) {
        emit(villager, random(villager.getRandom(), "For you", "Take this", "You've earned it"), VillagerWorldTextIndicatorKind.POSITIVE);
    }

    public static void onGiftReceived(AbstractVillager villager, int reputationValue) {
        if (reputationValue > 0) {
            emit(villager, random(villager.getRandom(), "Thank you", "How kind", "Lovely"), VillagerWorldTextIndicatorKind.POSITIVE);
        } else if (reputationValue < 0) {
            emit(villager, random(villager.getRandom(), "No thanks", "Not this", "Really?"), VillagerWorldTextIndicatorKind.NEGATIVE);
        } else {
            emit(villager, random(villager.getRandom(), "Hm", "Thanks", "Alright"), VillagerWorldTextIndicatorKind.DIALOGUE);
        }
    }

    public static void onTradeRefused(AbstractVillager villager) {
        emit(villager, random(villager.getRandom(), "No trades", "Not you", "Leave"), VillagerWorldTextIndicatorKind.NEGATIVE);
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

    private static void emitAlert(ServerLevel level, AbstractVillager villager, String text) {
        long gameTime = level.getGameTime();
        if (gameTime < NEXT_ALERT_TICK.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }
        NEXT_ALERT_TICK.put(villager.getUUID(), gameTime + ALERT_COOLDOWN_TICKS);
        emit(villager, text, VillagerWorldTextIndicatorKind.ALERT);
    }

    private static void emit(AbstractVillager villager, String text, VillagerWorldTextIndicatorKind kind) {
        if (text == null || text.isBlank() || !(villager.level() instanceof ServerLevel)) {
            return;
        }
        VillagerReputationNetworking.sendWorldTextIndicator(villager, text, kind);
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

    private static String alertTextForDeath(AbstractVillager witness, Entity attacker) {
        if (attacker instanceof Player) {
            return random(witness.getRandom(), "No!", "Murder!", "Bell!");
        }
        return random(witness.getRandom(), "No!", "Danger!", "Bell!");
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
    }
}
