package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** Server-authoritative invitations and active duels between two players. */
public final class PlayerDuelService {
    private static final long INVITATION_LIFETIME_TICKS = 1_200L;
    private static final long COUNTDOWN_TICKS = 60L;
    private static final long ARENA_PARTICLE_INTERVAL_TICKS = 20L;
    private static final int ARENA_PARTICLE_POINTS = 48;
    private static final String PROJECTILE_TAG = "VillagerRetaliationPlayerDuelProjectile";
    private static final Map<InvitationKey, Invitation> INVITATIONS = new HashMap<>();
    private static final Map<UUID, ActiveDuel> BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> BY_PLAYER = new HashMap<>();

    private PlayerDuelService() {
    }

    public static void challenge(ServerPlayer challenger, ServerPlayer opponent, ResourceLocation kitId, int stake) {
        DuelKit kit = DuelKitRegistry.find(kitId).orElse(null);
        String error = validate(challenger, opponent, kit, stake);
        if (error != null) {
            notice(challenger, error);
            return;
        }
        long now = gameTime(challenger.getServer());
        Invitation invitation = new Invitation(
                challenger.getUUID(), opponent.getUUID(), kit.id(), stake, now + INVITATION_LIFETIME_TICKS);
        INVITATIONS.put(new InvitationKey(challenger.getUUID(), opponent.getUUID()), invitation);
        notice(challenger, "villagerretaliation.player_duel.invitation_sent",
                opponent.getGameProfile().getName(), kit.name(), stake);
        sendInvitationNotice(opponent, challenger.getGameProfile().getName(), kit.name(), stake);
    }

    public static void accept(ServerPlayer opponent, ServerPlayer challenger) {
        InvitationKey key = new InvitationKey(challenger.getUUID(), opponent.getUUID());
        Invitation invitation = INVITATIONS.remove(key);
        if (invitation == null || invitation.expiresAt() < gameTime(opponent.getServer())) {
            notice(opponent, "villagerretaliation.player_duel.error.invitation_invalid");
            return;
        }
        DuelKit kit = DuelKitRegistry.find(invitation.kitId()).orElse(null);
        String error = validate(challenger, opponent, kit, invitation.stake());
        if (error != null) {
            notice(opponent, error);
            notice(challenger, "villagerretaliation.player_duel.invitation_failed",
                    opponent.getGameProfile().getName());
            return;
        }
        if (!begin(challenger, opponent, kit, invitation.stake())) {
            notice(opponent, "villagerretaliation.player_duel.error.payment");
            notice(challenger, "villagerretaliation.player_duel.error.payment");
        }
    }

    public static void decline(ServerPlayer opponent, ServerPlayer challenger) {
        Invitation invitation = INVITATIONS.remove(new InvitationKey(challenger.getUUID(), opponent.getUUID()));
        if (invitation == null || invitation.expiresAt() < gameTime(opponent.getServer())) {
            notice(opponent, "villagerretaliation.player_duel.error.invitation_invalid");
            return;
        }
        notice(opponent, "villagerretaliation.player_duel.invitation_declined");
        notice(challenger, "villagerretaliation.player_duel.invitation_declined_other",
                opponent.getGameProfile().getName());
    }

    private static String validate(ServerPlayer challenger, ServerPlayer opponent, DuelKit kit, int stake) {
        if (!VillagerRetaliationConfig.ENABLE_DUELS.get()) {
            return "villagerretaliation.player_duel.error.disabled";
        }
        if (challenger == null || opponent == null || challenger == opponent
                || !challenger.isAlive() || !opponent.isAlive()
                || challenger.isCreative() || opponent.isCreative()
                || challenger.isSpectator() || opponent.isSpectator()) {
            return "villagerretaliation.player_duel.error.invalid";
        }
        double radius = VillagerRetaliationConfig.DUEL_ARENA_RADIUS.get();
        if (challenger.serverLevel() != opponent.serverLevel()
                || challenger.distanceToSqr(opponent) > radius * radius) {
            return "villagerretaliation.player_duel.error.too_far";
        }
        if (DuelService.isParticipantId(challenger.getUUID())
                || DuelService.isParticipantId(opponent.getUUID())) {
            return "villagerretaliation.player_duel.error.busy";
        }
        int maximum = Math.min(
                VillagerCurrencyPayment.count(challenger), VillagerCurrencyPayment.count(opponent));
        if (kit == null || !DuelService.validStake(stake, maximum)) {
            return "villagerretaliation.player_duel.error.terms";
        }
        return null;
    }

    private static boolean begin(ServerPlayer challenger, ServerPlayer opponent, DuelKit kit, int stake) {
        if (challenger.containerMenu != challenger.inventoryMenu) challenger.closeContainer();
        if (opponent.containerMenu != opponent.inventoryMenu) opponent.closeContainer();
        if (!VillagerCurrencyPayment.tryRemove(challenger, stake)) return false;
        if (!VillagerCurrencyPayment.tryRemove(opponent, stake)) {
            DuelService.giveCurrency(challenger, stake);
            return false;
        }

        UUID id = UUID.randomUUID();
        long now = gameTime(challenger.getServer());
        Vec3 center = challenger.position().add(opponent.position()).scale(0.5D);
        DuelEquipment.PlayerSnapshots snapshots = DuelEquipment.preparePlayers(challenger, opponent, kit);
        DuelEquipment.persistPlayerRecovery(challenger, id, kit, stake, snapshots.challenger());
        DuelEquipment.persistPlayerRecovery(opponent, id, kit, stake, snapshots.opponent());
        ActiveDuel duel = new ActiveDuel(
                id, challenger.serverLevel().dimension(), challenger.getUUID(), opponent.getUUID(), kit, stake,
                center, VillagerRetaliationConfig.DUEL_ARENA_RADIUS.get(),
                VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.get(),
                now + COUNTDOWN_TICKS,
                now + COUNTDOWN_TICKS + VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.get(),
                snapshots);
        BY_ID.put(id, duel);
        BY_PLAYER.put(challenger.getUUID(), id);
        BY_PLAYER.put(opponent.getUUID(), id);
        INVITATIONS.entrySet().removeIf(entry -> entry.getKey().contains(challenger.getUUID())
                || entry.getKey().contains(opponent.getUUID()));
        sync(challenger, true, !kit.bringYourOwn());
        sync(opponent, true, !kit.bringYourOwn());
        challenger.inventoryMenu.broadcastFullState();
        opponent.inventoryMenu.broadcastFullState();
        playAcceptanceSound(challenger);
        playAcceptanceSound(opponent);
        notice(challenger, "villagerretaliation.player_duel.started", opponent.getGameProfile().getName());
        notice(opponent, "villagerretaliation.player_duel.started", challenger.getGameProfile().getName());
        return true;
    }

    static void tick(MinecraftServer server) {
        long now = gameTime(server);
        INVITATIONS.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        for (ActiveDuel duel : List.copyOf(BY_ID.values())) tick(server, duel, now);
    }

    private static void tick(MinecraftServer server, ActiveDuel duel, long now) {
        ServerLevel level = server.getLevel(duel.dimension());
        ServerPlayer challenger = server.getPlayerList().getPlayer(duel.challengerId());
        ServerPlayer opponent = server.getPlayerList().getPlayer(duel.opponentId());
        if (level == null || challenger == null || opponent == null
                || challenger.serverLevel() != level || opponent.serverLevel() != level
                || !challenger.isAlive() || !opponent.isAlive()) {
            finish(server, duel, Outcome.CANCELLED, null);
            return;
        }
        if (duel.pendingOutcome() != null) {
            finish(server, duel, duel.pendingOutcome(), null);
            return;
        }
        if (challenger.containerMenu != challenger.inventoryMenu) challenger.closeContainer();
        if (opponent.containerMenu != opponent.inventoryMenu) opponent.closeContainer();
        showArenaParticles(level, challenger, duel, now);
        showArenaParticles(level, opponent, duel, now);
        updateCountdown(challenger, opponent, duel, now);
        if (now < duel.countdownEndsAt()) return;
        if (now >= duel.timeoutAt()) {
            finish(server, duel, Outcome.DRAW, null);
            return;
        }
        double radiusSqr = (double) duel.arenaRadius() * duel.arenaRadius();
        duel.challengerOutsideSince(outside(challenger, duel.center(), radiusSqr)
                ? first(duel.challengerOutsideSince(), now) : -1L);
        duel.opponentOutsideSince(outside(opponent, duel.center(), radiusSqr)
                ? first(duel.opponentOutsideSince(), now) : -1L);
        updateBoundaryCountdown(challenger, duel.challengerOutsideSince(), duel, true, now);
        updateBoundaryCountdown(opponent, duel.opponentOutsideSince(), duel, false, now);
        if (duel.challengerOutsideSince() >= 0L
                && now - duel.challengerOutsideSince() >= duel.boundaryGraceTicks()) {
            finish(server, duel, Outcome.OPPONENT_WIN, null);
        } else if (duel.opponentOutsideSince() >= 0L
                && now - duel.opponentOutsideSince() >= duel.boundaryGraceTicks()) {
            finish(server, duel, Outcome.CHALLENGER_WIN, null);
        }
    }

    private static void updateCountdown(
            ServerPlayer challenger, ServerPlayer opponent, ActiveDuel duel, long now) {
        if (now >= duel.countdownEndsAt()) {
            if (duel.lastCountdownSecond() != 0) {
                notice(challenger, "villagerretaliation.duel.countdown.fight");
                notice(opponent, "villagerretaliation.duel.countdown.fight");
                duel.lastCountdownSecond(0);
            }
            return;
        }
        int seconds = seconds(duel.countdownEndsAt() - now);
        if (seconds != duel.lastCountdownSecond()) {
            notice(challenger, "villagerretaliation.duel.countdown", seconds);
            notice(opponent, "villagerretaliation.duel.countdown", seconds);
            duel.lastCountdownSecond(seconds);
        }
    }

    private static void updateBoundaryCountdown(
            ServerPlayer player, long outsideSince, ActiveDuel duel, boolean challenger, long now) {
        if (outsideSince < 0L) {
            if (challenger) duel.challengerBoundarySecond(-1);
            else duel.opponentBoundarySecond(-1);
            return;
        }
        int remaining = seconds(duel.boundaryGraceTicks() - (now - outsideSince));
        int previous = challenger ? duel.challengerBoundarySecond() : duel.opponentBoundarySecond();
        if (remaining > 0 && remaining != previous) {
            notice(player, "villagerretaliation.duel.boundary_countdown", remaining);
            if (challenger) duel.challengerBoundarySecond(remaining);
            else duel.opponentBoundarySecond(remaining);
        }
    }

    private static void showArenaParticles(
            ServerLevel level, ServerPlayer player, ActiveDuel duel, long now) {
        if (!VillagerRetaliationConfig.SHOW_DUEL_ARENA_PARTICLES.get()
                || Math.floorMod(now, ARENA_PARTICLE_INTERVAL_TICKS) != 0L) return;
        for (int point = 0; point < ARENA_PARTICLE_POINTS; point++) {
            double angle = Math.PI * 2.0D * point / ARENA_PARTICLE_POINTS;
            level.sendParticles(player, ParticleTypes.END_ROD, true,
                    duel.center().x + Math.cos(angle) * duel.arenaRadius(),
                    duel.center().y + 0.15D,
                    duel.center().z + Math.sin(angle) * duel.arenaRadius(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    static boolean onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        ActiveDuel attackerDuel = active(attacker);
        ActiveDuel targetDuel = active(event.getEntity());
        if (attackerDuel == null && targetDuel == null) return false;
        ActiveDuel duel = attackerDuel != null ? attackerDuel : targetDuel;
        long now = gameTime(((ServerLevel) event.getEntity().level()).getServer());
        if (attacker == null || !isOpponent(duel, event.getEntity(), attacker)
                || now < duel.countdownEndsAt()) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
        return true;
    }

    static boolean onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return false;
        ActiveDuel duel = active(player);
        if (duel == null) return false;
        if (!opponentId(duel, player.getUUID()).equals(event.getTarget().getUUID())) {
            event.setCanceled(true);
        }
        return true;
    }

    static boolean onFinalDamage(LivingDamageEvent.Pre event) {
        ActiveDuel duel = active(event.getEntity());
        if (duel == null) return false;
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        if (attacker == null || !isOpponent(duel, event.getEntity(), attacker)) {
            event.setNewDamage(0.0F);
            return true;
        }
        float maximum = Math.max(0.0F, event.getEntity().getHealth() - 1.0F);
        if (event.getNewDamage() > 0.0F && event.getNewDamage() >= maximum
                && duel.pendingOutcome() == null) {
            event.setNewDamage(maximum);
            duel.pendingOutcome(attacker.getUUID().equals(duel.challengerId())
                    ? Outcome.CHALLENGER_WIN : Outcome.OPPONENT_WIN);
        }
        return true;
    }

    static boolean isDuelDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        ActiveDuel duel = active(target);
        return duel != null && source.getEntity() instanceof LivingEntity attacker
                && isOpponent(duel, target, attacker);
    }

    static boolean allowsInventoryClick(
            ServerPlayer player, AbstractContainerMenu menu, int slotId, ClickType clickType) {
        ActiveDuel duel = active(player);
        if (duel == null) return true;
        if (menu != player.inventoryMenu || !duel.kit().bringYourOwn()) return false;
        if (slotId < InventoryMenu.ARMOR_SLOT_START || slotId > InventoryMenu.SHIELD_SLOT) return false;
        return clickType != ClickType.THROW
                && clickType != ClickType.CLONE
                && clickType != ClickType.QUICK_CRAFT;
    }

    static boolean onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) return false;
        if (projectile.getPersistentData().hasUUID(PROJECTILE_TAG)) {
            UUID duelId = projectile.getPersistentData().getUUID(PROJECTILE_TAG);
            ActiveDuel duel = BY_ID.get(duelId);
            if (duel == null) projectile.discard();
            else duel.projectiles().add(projectile.getUUID());
            return true;
        }
        if (projectile.getOwner() instanceof LivingEntity owner) {
            ActiveDuel duel = active(owner);
            if (duel != null) {
                projectile.getPersistentData().putUUID(PROJECTILE_TAG, duel.id());
                duel.projectiles().add(projectile.getUUID());
                return true;
            }
        }
        return false;
    }

    static boolean onPlayerLogout(ServerPlayer player) {
        ActiveDuel duel = active(player);
        if (duel == null) return false;
        Outcome outcome = player.getUUID().equals(duel.challengerId())
                ? Outcome.OPPONENT_WIN : Outcome.CHALLENGER_WIN;
        finish(player.getServer(), duel, outcome, player);
        return true;
    }

    static boolean isParticipant(UUID playerId) {
        return playerId != null && BY_PLAYER.containsKey(playerId);
    }

    static boolean isActiveDuel(UUID duelId) {
        return duelId != null && BY_ID.containsKey(duelId);
    }

    static void clearRuntimeState(MinecraftServer server) {
        for (ActiveDuel duel : List.copyOf(BY_ID.values())) {
            finish(server, duel, Outcome.CANCELLED, null);
        }
        INVITATIONS.clear();
        BY_ID.clear();
        BY_PLAYER.clear();
    }

    static boolean hasInvitationForTest(ServerPlayer challenger, ServerPlayer opponent) {
        return challenger != null && opponent != null
                && INVITATIONS.containsKey(new InvitationKey(
                        challenger.getUUID(), opponent.getUUID()));
    }

    static boolean resolveForTest(ServerPlayer player, boolean playerWins) {
        ActiveDuel duel = active(player);
        if (duel == null) return false;
        boolean challenger = player.getUUID().equals(duel.challengerId());
        Outcome outcome = challenger == playerWins
                ? Outcome.CHALLENGER_WIN : Outcome.OPPONENT_WIN;
        finish(player.getServer(), duel, outcome, player);
        return true;
    }

    private static void finish(
            MinecraftServer server, ActiveDuel duel, Outcome outcome, ServerPlayer playerHint) {
        if (BY_ID.remove(duel.id()) == null) return;
        BY_PLAYER.remove(duel.challengerId(), duel.id());
        BY_PLAYER.remove(duel.opponentId(), duel.id());
        ServerPlayer challenger = resolve(server, duel.challengerId(), playerHint);
        ServerPlayer opponent = resolve(server, duel.opponentId(), playerHint);
        Outcome settled = outcome;
        if ((outcome == Outcome.CHALLENGER_WIN && challenger == null)
                || (outcome == Outcome.OPPONENT_WIN && opponent == null)) {
            settled = Outcome.CANCELLED;
        }
        for (UUID projectileId : duel.projectiles()) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(projectileId);
                if (entity instanceof Projectile projectile) {
                    projectile.discard();
                    break;
                }
            }
        }
        restore(challenger, duel.snapshots().challenger(), duel);
        restore(opponent, duel.snapshots().opponent(), duel);
        settle(challenger, opponent, duel.stake(), settled);
        if (challenger != null) DuelEquipment.clearRecovery(challenger, duel.id());
        if (opponent != null) DuelEquipment.clearRecovery(opponent, duel.id());
        ServerPlayer winner = settled == Outcome.CHALLENGER_WIN ? challenger
                : settled == Outcome.OPPONENT_WIN ? opponent : null;
        ServerPlayer loser = settled == Outcome.CHALLENGER_WIN ? opponent
                : settled == Outcome.OPPONENT_WIN ? challenger : null;
        if (winner != null) DuelService.playPlayerDuelVictorySound(winner);
        if (loser != null) DuelService.applyLossPenalty(server, loser);
        sendResult(challenger, opponent, settled, true);
        sendResult(opponent, challenger, settled, false);
    }

    private static void restore(
            ServerPlayer player, DuelEquipment.PlayerSnapshot snapshot, ActiveDuel duel) {
        if (player == null) return;
        snapshot.restore(player, !duel.kit().bringYourOwn());
        player.inventoryMenu.broadcastFullState();
        sync(player, false, false);
    }

    private static void settle(
            ServerPlayer challenger, ServerPlayer opponent, int stake, Outcome outcome) {
        switch (outcome) {
            case CHALLENGER_WIN -> DuelService.giveCurrency(challenger, stake * 2);
            case OPPONENT_WIN -> DuelService.giveCurrency(opponent, stake * 2);
            case DRAW, CANCELLED -> {
                DuelService.giveCurrency(challenger, stake);
                DuelService.giveCurrency(opponent, stake);
            }
        }
    }

    private static void sendResult(
            ServerPlayer player, ServerPlayer other, Outcome outcome, boolean challenger) {
        if (player == null) return;
        String otherName = other == null ? "Player" : other.getGameProfile().getName();
        String suffix = switch (outcome) {
            case DRAW -> "draw";
            case CANCELLED -> "cancelled";
            case CHALLENGER_WIN -> challenger ? "win" : "loss";
            case OPPONENT_WIN -> challenger ? "loss" : "win";
        };
        notice(player, "villagerretaliation.player_duel.result." + suffix, otherName);
    }

    private static void sync(ServerPlayer player, boolean active, boolean assignedLoadout) {
        DuelService.syncInventoryState(player, active, assignedLoadout);
    }

    private static ServerPlayer resolve(MinecraftServer server, UUID id, ServerPlayer hint) {
        return hint != null && hint.getUUID().equals(id)
                ? hint : server.getPlayerList().getPlayer(id);
    }

    private static ActiveDuel active(LivingEntity entity) {
        UUID duelId = entity == null ? null : BY_PLAYER.get(entity.getUUID());
        return duelId == null ? null : BY_ID.get(duelId);
    }

    private static boolean isOpponent(ActiveDuel duel, LivingEntity target, LivingEntity attacker) {
        return target != null && attacker != null
                && (target.getUUID().equals(duel.challengerId())
                        && attacker.getUUID().equals(duel.opponentId())
                    || target.getUUID().equals(duel.opponentId())
                        && attacker.getUUID().equals(duel.challengerId()));
    }

    private static UUID opponentId(ActiveDuel duel, UUID playerId) {
        return playerId.equals(duel.challengerId()) ? duel.opponentId() : duel.challengerId();
    }

    private static boolean outside(ServerPlayer player, Vec3 center, double radiusSqr) {
        double x = player.getX() - center.x;
        double z = player.getZ() - center.z;
        return x * x + z * z > radiusSqr;
    }

    private static long first(long previous, long now) {
        return previous < 0L ? now : previous;
    }

    private static int seconds(long ticks) {
        return (int) Math.max(0L, (ticks + 19L) / 20L);
    }

    private static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static void notice(ServerPlayer player, String key, Object... args) {
        if (player != null) player.sendSystemMessage(Component.translatable(key, args));
    }

    private static void playAcceptanceSound(ServerPlayer player) {
        if (player != null) {
            player.playNotifySound(
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.65F, 1.0F);
        }
    }

    private static void sendInvitationNotice(
            ServerPlayer target, String challengerName, String kitName, int stake) {
        String command = "/duel accept " + challengerName;
        Component accept = Component.translatable("villagerretaliation.player_duel.invitation.accept_chat")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(
                                        "villagerretaliation.player_duel.invitation.accept_chat.tooltip")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        target.sendSystemMessage(Component.translatable(
                        "villagerretaliation.player_duel.invitation.prompt",
                        challengerName, kitName, stake)
                .append(" ")
                .append(accept));
    }

    private enum Outcome {
        CHALLENGER_WIN,
        OPPONENT_WIN,
        DRAW,
        CANCELLED
    }

    private record InvitationKey(UUID challengerId, UUID opponentId) {
        boolean contains(UUID playerId) {
            return challengerId.equals(playerId) || opponentId.equals(playerId);
        }
    }

    private record Invitation(
            UUID challengerId, UUID opponentId, ResourceLocation kitId, int stake, long expiresAt) {
    }

    private static final class ActiveDuel {
        private final UUID id;
        private final ResourceKey<Level> dimension;
        private final UUID challengerId;
        private final UUID opponentId;
        private final DuelKit kit;
        private final int stake;
        private final Vec3 center;
        private final int arenaRadius;
        private final int boundaryGraceTicks;
        private final long countdownEndsAt;
        private final long timeoutAt;
        private final DuelEquipment.PlayerSnapshots snapshots;
        private final Set<UUID> projectiles = new HashSet<>();
        private long challengerOutsideSince = -1L;
        private long opponentOutsideSince = -1L;
        private int lastCountdownSecond = -1;
        private int challengerBoundarySecond = -1;
        private int opponentBoundarySecond = -1;
        private Outcome pendingOutcome;

        ActiveDuel(
                UUID id, ResourceKey<Level> dimension, UUID challengerId, UUID opponentId,
                DuelKit kit, int stake, Vec3 center, int arenaRadius, int boundaryGraceTicks,
                long countdownEndsAt, long timeoutAt, DuelEquipment.PlayerSnapshots snapshots) {
            this.id = id;
            this.dimension = dimension;
            this.challengerId = challengerId;
            this.opponentId = opponentId;
            this.kit = kit;
            this.stake = stake;
            this.center = center;
            this.arenaRadius = arenaRadius;
            this.boundaryGraceTicks = boundaryGraceTicks;
            this.countdownEndsAt = countdownEndsAt;
            this.timeoutAt = timeoutAt;
            this.snapshots = snapshots;
        }

        UUID id() { return id; }
        ResourceKey<Level> dimension() { return dimension; }
        UUID challengerId() { return challengerId; }
        UUID opponentId() { return opponentId; }
        DuelKit kit() { return kit; }
        int stake() { return stake; }
        Vec3 center() { return center; }
        int arenaRadius() { return arenaRadius; }
        int boundaryGraceTicks() { return boundaryGraceTicks; }
        long countdownEndsAt() { return countdownEndsAt; }
        long timeoutAt() { return timeoutAt; }
        DuelEquipment.PlayerSnapshots snapshots() { return snapshots; }
        Set<UUID> projectiles() { return projectiles; }
        long challengerOutsideSince() { return challengerOutsideSince; }
        void challengerOutsideSince(long value) { challengerOutsideSince = value; }
        long opponentOutsideSince() { return opponentOutsideSince; }
        void opponentOutsideSince(long value) { opponentOutsideSince = value; }
        int lastCountdownSecond() { return lastCountdownSecond; }
        void lastCountdownSecond(int value) { lastCountdownSecond = value; }
        int challengerBoundarySecond() { return challengerBoundarySecond; }
        void challengerBoundarySecond(int value) { challengerBoundarySecond = value; }
        int opponentBoundarySecond() { return opponentBoundarySecond; }
        void opponentBoundarySecond(int value) { opponentBoundarySecond = value; }
        Outcome pendingOutcome() { return pendingOutcome; }
        void pendingOutcome(Outcome value) { pendingOutcome = value; }
    }
}
