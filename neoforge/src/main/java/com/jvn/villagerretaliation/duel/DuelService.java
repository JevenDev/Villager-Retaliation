package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.compat.secondwind.VillagerSecondWindCompat;
import com.jvn.villagerretaliation.combat.VillagerCombatAttributeCompat;
import com.jvn.villagerretaliation.combat.VillagerCombatBehavior;
import com.jvn.villagerretaliation.combat.VillagerCombatRoles;
import com.jvn.villagerretaliation.combat.VillagerRangedCombatHelper;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.network.DuelFxStatePayload;
import com.jvn.villagerretaliation.network.DuelInventoryStatePayload;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillPractice;
import com.jvn.villagerretaliation.skill.VillagerSkillProgressionService;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.common.util.TriState;
public final class DuelService {
    public static final int[] FIXED_STAKES = {0, 8, 16, 32, 64};
    private static final long DAY_TICKS = 24000L;
    private static final long COUNTDOWN_TICKS = 60L;
    private static final long ARENA_PARTICLE_INTERVAL_TICKS = 10L;
    private static final int ARENA_PARTICLE_POINTS = 24;
    private static final float DUEL_VICTORY_SOUND_VOLUME = 0.8F;
    private static final float DUEL_VICTORY_SOUND_PITCH = 1.4F;
    private static final double VILLAGER_CLEARANCE = 3.0D;
    private static final int LOSS_PENALTY_TICKS = 100;
    private static final int LOSS_SLOWNESS_AMPLIFIER = 1;
    private static final int DUEL_GUTS_REWARD = 2;
    private static final String DUEL_PROJECTILE_TAG = "VillagerRetaliationDuelProjectile";
    private static final Map<UUID, ActiveDuel> BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> BY_ENTITY = new HashMap<>();
    private static final Map<UUID, FinisherPermission> FINISHERS = new HashMap<>();
    private static final Map<UUID, Long> LOSS_ATTACK_LOCKOUTS = new HashMap<>();

    private DuelService() {}

    public static DuelAvailability availability(ServerLevel level, ServerPlayer player, Villager villager) {
        if (level == null || player == null || villager == null) return DuelAvailability.hidden();
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        if (profile.socialAttributes().get(VillagerSocialAttribute.GUTS) < VillagerRetaliationConfig.DUEL_MINIMUM_GUTS.get()) {
            return DuelAvailability.hidden();
        }
        DuelSavedData.DuelRecord record = DuelSavedData.get(level).record(villager.getUUID(), player.getUUID());
        long cooldown = cooldownRemaining(level, record);
        DuelAvailabilityReason reason = reason(level, player, villager, record, cooldown);
        return new DuelAvailability(true, reason == DuelAvailabilityReason.AVAILABLE, reason,
                record.villagerWins(), record.villagerLosses(), record.consecutiveLosses(), cooldown,
                VillagerCurrencyPayment.count(player), VillagerWalletService.getCurrentEmeralds(villager));
    }

    private static DuelAvailabilityReason reason(ServerLevel level, ServerPlayer player, Villager villager,
                                                  DuelSavedData.DuelRecord record, long cooldown) {
        if (!VillagerRetaliationConfig.ENABLE_DUELS.get()) return DuelAvailabilityReason.DISABLED;
        if (villager.isBaby()) return DuelAvailabilityReason.BABY;
        if (!villager.isAlive() || !player.isAlive() || player.isSpectator()) return DuelAvailabilityReason.INVALID;
        if (player.isCreative()) return DuelAvailabilityReason.INVALID;
        if (player.level() != level || player.distanceToSqr(villager) > 64.0D) return DuelAvailabilityReason.TOO_FAR;
        if (HiredVillagerContractService.isHired(level, villager)) return DuelAvailabilityReason.HIRED;
        if (PartyVillagerContractService.isActivePartyVillager(level, villager)) return DuelAvailabilityReason.PARTY;
        if (VillagerDownedService.isDowned(villager)) return DuelAvailabilityReason.DOWNED;
        if (VillagerDeathProtectionResolver.resolve(level, villager).protectedFromDeath()) return DuelAvailabilityReason.PROTECTED;
        if (level.getRaidAt(villager.blockPosition()) != null) return DuelAvailabilityReason.RAID;
        if (record.refuses()) return DuelAvailabilityReason.REFUSES;
        if (cooldown > 0L) return DuelAvailabilityReason.COOLDOWN;
        if (isParticipantId(player.getUUID())) return DuelAvailabilityReason.PLAYER_BUSY;
        if (BY_ENTITY.containsKey(villager.getUUID())) return DuelAvailabilityReason.VILLAGER_BUSY;
        if (villager.getTarget() != null || villager.isSleeping() || villager.isTrading()) return DuelAvailabilityReason.BUSY;
        return DuelAvailabilityReason.AVAILABLE;
    }

    private static long cooldownRemaining(ServerLevel level, DuelSavedData.DuelRecord record) {
        if (record.lastStartGameTime() == Long.MIN_VALUE) return 0L;
        long duration = Math.max(0, VillagerRetaliationConfig.DUEL_COOLDOWN_DAYS.get()) * DAY_TICKS;
        return Math.max(0L, record.lastStartGameTime() + duration - level.getServer().overworld().getGameTime());
    }

    public static StartResult start(ServerPlayer player, Villager villager, DuelLoadout loadout, int requestedStake) {
        return start(player, villager, loadout == null ? null : loadout.id(), requestedStake);
    }

    public static StartResult start(ServerPlayer player, Villager villager, ResourceLocation kitId, int requestedStake) {
        DuelKit kit = DuelKitRegistry.find(kitId).orElse(null);
        if (kit == null) return new StartResult(false, DuelAvailabilityReason.INVALID, null);
        if (kit.bringYourOwn() && !VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.get()) {
            return new StartResult(false, DuelAvailabilityReason.LOADOUT_DISABLED, null);
        }
        ServerLevel level = player.serverLevel();
        DuelAvailability available = availability(level, player, villager);
        if (!available.available()) return new StartResult(false, available.reason(), null);
        return begin(player, villager, kit, requestedStake, available.maximumStake());
    }

    public static StartResult startDebug(ServerPlayer player, Villager villager, DuelLoadout loadout, int requestedStake) {
        return startDebug(player, villager, loadout == null ? null : loadout.id(), requestedStake);
    }

    public static StartResult startDebug(
            ServerPlayer player, Villager villager, ResourceLocation kitId, int requestedStake) {
        DuelKit kit = DuelKitRegistry.find(kitId).orElse(null);
        if (player == null || villager == null || kit == null || !player.isAlive() || !villager.isAlive()) {
            return new StartResult(false, DuelAvailabilityReason.INVALID, null);
        }
        if (player.level() != villager.level()) return new StartResult(false, DuelAvailabilityReason.TOO_FAR, null);
        if (isParticipantId(player.getUUID())) return new StartResult(false, DuelAvailabilityReason.PLAYER_BUSY, null);
        if (BY_ENTITY.containsKey(villager.getUUID())) return new StartResult(false, DuelAvailabilityReason.VILLAGER_BUSY, null);
        int maximumStake = Math.max(0, Math.min(
                VillagerCurrencyPayment.count(player), VillagerWalletService.getCurrentEmeralds(villager)));
        return begin(player, villager, kit, requestedStake, maximumStake);
    }

    private static StartResult begin(
            ServerPlayer player,
            Villager villager,
            DuelKit kit,
            int requestedStake,
            int maximumStake) {
        ServerLevel level = player.serverLevel();
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        int stake = requestedStake == Integer.MAX_VALUE ? maximumStake : requestedStake;
        if (!validStake(stake, maximumStake)) return new StartResult(false, DuelAvailabilityReason.INVALID, null);
        if (!VillagerCurrencyPayment.tryRemove(player, stake)) return new StartResult(false, DuelAvailabilityReason.INVALID, null);
        if (!VillagerWalletService.spendCurrency(villager, stake)) {
            giveCurrency(player, stake);
            return new StartResult(false, DuelAvailabilityReason.INVALID, null);
        }
        UUID id = UUID.randomUUID();
        long now = level.getServer().overworld().getGameTime();
        Vec3 center = player.position().add(villager.position()).scale(0.5D);
        VillagerDownedService.recover(villager);
        Set<UUID> spectators = DuelSpectators.recruit(level, villager, center);
        VillagerCombatBehavior.reset(villager);
        VillagerDownedService.ensureStandingDimensions(villager);
        DuelEquipment.Snapshots snapshots = DuelEquipment.prepare(player, villager, kit);
        DuelEquipment.persistRecovery(player, villager, id, kit, stake, snapshots);
        ActiveDuel duel = new ActiveDuel(id, level.dimension(), player.getUUID(), villager.getUUID(), kit, stake,
                center, VillagerRetaliationConfig.DUEL_ARENA_RADIUS.get(),
                VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.get(),
                now + COUNTDOWN_TICKS, now + COUNTDOWN_TICKS + VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.get(),
                snapshots, spectators);
        BY_ID.put(id, duel);
        BY_ENTITY.put(player.getUUID(), id);
        BY_ENTITY.put(villager.getUUID(), id);
        player.inventoryMenu.broadcastFullState();
        syncInventoryState(player, true, !kit.bringYourOwn());
        syncFxState(player, duel, null);
        DuelSavedData.get(level).markStarted(villager.getUUID(), player.getUUID(), now);
        VillagerConversationService.endForVillager(villager, true);
        player.sendSystemMessage(Component.translatable("villagerretaliation.duel.started", VillagerPresetNameRegistry.resolveDisplayName(villager)));
        return new StartResult(true, DuelAvailabilityReason.AVAILABLE, id);
    }

    static boolean validStake(int stake, int maximum) {
        if (stake < 0 || stake > maximum) return false;
        if (stake == maximum) return true;
        for (int fixed : FIXED_STAKES) if (stake == fixed) return true;
        return false;
    }

    public static void tick(MinecraftServer server) {
        PlayerDuelService.tick(server);
        for (ActiveDuel duel : List.copyOf(BY_ID.values())) tick(server, duel);
        long now = server.overworld().getGameTime();
        FINISHERS.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
        LOSS_ATTACK_LOCKOUTS.entrySet().removeIf(entry -> now >= entry.getValue());
    }

    private static void tick(MinecraftServer server, ActiveDuel duel) {
        ServerLevel level = server.getLevel(duel.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(duel.playerId());
        Villager villager = level != null && level.getEntity(duel.villagerId()) instanceof Villager found ? found : null;
        if (level == null || villager == null || !villager.isAlive()) { finish(server, duel, DuelResult.CANCELLED, false); return; }
        // Normal logouts resolve through onPlayerLogout with a live player hint. If that hook was
        // missed, cancellation is the only settlement that can be recovered safely for both sides.
        if (player == null) { finish(server, duel, DuelResult.CANCELLED, false); return; }
        if (player.serverLevel() != level || !player.isAlive()) { finish(server, duel, DuelResult.CANCELLED, false); return; }
        if (duel.pendingResult() != null) { finish(server, duel, duel.pendingResult(), duel.villagerKnockedOut()); return; }
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        long now = server.overworld().getGameTime();
        DuelSpectators.maintain(level, duel.spectators(), duel.center(), duel.arenaRadius(), villager);
        showArenaParticles(level, player, duel, now);
        updateStartCountdown(player, duel, now);
        if (now < duel.countdownEndsAt()) { villager.getNavigation().stop(); villager.setTarget(null); return; }
        if (now >= duel.timeoutAt()) { finish(server, duel, DuelResult.DRAW, false); return; }
        updateBoundary(duel, player, villager, now);
        updatePlayerBoundaryCountdown(player, duel, now);
        int grace = duel.boundaryGraceTicks();
        if (duel.playerOutsideSince() >= 0L && now - duel.playerOutsideSince() >= grace) { finish(server, duel, DuelResult.VILLAGER_WIN, false); return; }
        if (duel.villagerOutsideSince() >= 0L && now - duel.villagerOutsideSince() >= grace) { finish(server, duel, DuelResult.PLAYER_WIN, false); return; }
        drive(level, duel, villager, player, now);
    }

    private static void updateBoundary(ActiveDuel duel, ServerPlayer player, Villager villager, long now) {
        double radiusSqr = Math.pow(duel.arenaRadius(), 2.0D);
        duel.playerOutsideSince(outside(player.position(), duel.center(), radiusSqr) ? first(duel.playerOutsideSince(), now) : -1L);
        duel.villagerOutsideSince(outside(villager.position(), duel.center(), radiusSqr) ? first(duel.villagerOutsideSince(), now) : -1L);
    }

    private static void updateStartCountdown(ServerPlayer player, ActiveDuel duel, long now) {
        if (now >= duel.countdownEndsAt()) {
            if (duel.lastCountdownSecond() != 0) {
                player.sendSystemMessage(Component.translatable("villagerretaliation.duel.countdown.fight"));
                duel.lastCountdownSecond(0);
            }
            return;
        }
        int seconds = ticksToSeconds(duel.countdownEndsAt() - now);
        if (seconds != duel.lastCountdownSecond()) {
            player.sendSystemMessage(Component.translatable("villagerretaliation.duel.countdown", seconds));
            duel.lastCountdownSecond(seconds);
        }
    }

    private static void updatePlayerBoundaryCountdown(ServerPlayer player, ActiveDuel duel, long now) {
        if (duel.playerOutsideSince() < 0L) {
            duel.lastBoundarySecond(-1);
            return;
        }
        long remainingTicks = duel.boundaryGraceTicks() - (now - duel.playerOutsideSince());
        int seconds = ticksToSeconds(remainingTicks);
        if (seconds > 0 && seconds != duel.lastBoundarySecond()) {
            player.sendSystemMessage(Component.translatable(
                    "villagerretaliation.duel.boundary_countdown",
                    seconds).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            duel.lastBoundarySecond(seconds);
        }
    }

    private static int ticksToSeconds(long ticks) {
        return (int) Math.max(0L, (ticks + 19L) / 20L);
    }

    private static void showArenaParticles(ServerLevel level, ServerPlayer player, ActiveDuel duel, long now) {
        if (!VillagerRetaliationConfig.SHOW_DUEL_ARENA_PARTICLES.get()
                || Math.floorMod(now, ARENA_PARTICLE_INTERVAL_TICKS) != 0L) {
            return;
        }
        double phase = now * 0.08D;
        for (int point = 0; point < ARENA_PARTICLE_POINTS; point++) {
            double angle = Math.PI * 2.0D * point / ARENA_PARTICLE_POINTS;
            double x = duel.center().x + Math.cos(angle) * duel.arenaRadius();
            double z = duel.center().z + Math.sin(angle) * duel.arenaRadius();
            double height = 0.35D + 1.8D * (0.5D + 0.5D * Math.sin(angle * 3.0D + phase));
            level.sendParticles(player, ParticleTypes.WHITE_ASH, true,
                    x, duel.center().y + height, z, 1, 0.03D, 0.08D, 0.03D, 0.005D);
        }
    }
    private static boolean isUsingRangedWeapon(Villager villager) {
        var item = villager.getMainHandItem().getItem();
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
    }


    private static boolean outside(Vec3 position, Vec3 center, double radiusSqr) {
        return horizontalDistanceSqr(position, center) > radiusSqr;
    }
    private static double horizontalDistanceSqr(Vec3 position, Vec3 center) {
        double x = position.x - center.x;
        double z = position.z - center.z;
        return x * x + z * z;
    }
    private static long first(long prior, long now) { return prior < 0L ? now : prior; }

    private static void drive(ServerLevel level, ActiveDuel duel, Villager villager, ServerPlayer player, long now) {
        villager.setTarget(player);
        villager.setAggressive(true);
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
        double distance = villager.distanceToSqr(player);
        boolean ranged = VillagerCombatBehavior.prepareAndIsRanged(villager, player, distance);
        if (ranged && VillagerRangedCombatHelper.tryDuelAttack(villager, player, level, distance)) return;
        boolean meleeAttackReady = now >= duel.nextAttackAt();
        boolean allowMeleeAttack = VillagerCombatBehavior.handleShieldTactics(
                villager, player, distance, now, meleeAttackReady);
        double movementSpeed = duelMovementSpeed(villager)
                * VillagerCombatBehavior.movementSpeedFactor(villager);
        if (distance > 3.0D && !VillagerCombatBehavior.canMeleeHit(villager, player)) {
            villager.getNavigation().moveTo(player, movementSpeed);
        }
        else if (allowMeleeAttack && meleeAttackReady) {
            villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            attackMelee(villager, player);
            duel.nextAttackAt(now + 20L);
        }
    }

    static double duelMovementSpeed(Villager villager) {
        return VillagerCombatRoles.movementSpeed(villager);
    }

    static boolean driveForTest(ServerPlayer player, long now, boolean attackReady) {
        ActiveDuel duel = active(player);
        if (duel == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getEntity(duel.villagerId()) instanceof Villager villager)) {
            return false;
        }
        duel.nextAttackAt(attackReady ? now : now + 20L);
        drive(level, duel, villager, player, now);
        return true;
    }

    static boolean attackMelee(Villager villager, LivingEntity target) {
        boolean shieldBroken = VillagerCombatBehavior.tryBreakTargetShield(villager, target);
        boolean attacked = !shieldBroken && VillagerCombatAttributeCompat.syncMeleeAttackAttributes(villager)
                && villager.doHurtTarget(target);
        VillagerCombatBehavior.onMeleeAttackCommitted(villager, target);
        return attacked;
    }

    public static boolean onIncomingDamage(LivingIncomingDamageEvent event) {
        if (PlayerDuelService.onIncomingDamage(event)) return true;
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        ActiveDuel attackerDuel = active(attacker);
        if (attackerDuel != null && !isOpponent(attackerDuel, event.getEntity(), attacker)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return true;
        }
        if (attacker instanceof ServerPlayer player && isPostLossAttackLocked(player)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return true;
        }

        if (isProtectedSpectator(event.getEntity(), event.getSource().getEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return true;
        }

        ActiveDuel duel = active(event.getEntity());
        if (duel == null) return false;
        long now = ((ServerLevel) event.getEntity().level()).getServer().overworld().getGameTime();
        if (!isOpponent(duel, event.getEntity(), attacker) || now < duel.countdownEndsAt()) {
            event.setCanceled(true); event.setAmount(0.0F); return true;
        }
        if (event.getEntity() instanceof Villager villager
                && VillagerCombatBehavior.tryBlockStructuredCombatDamage(villager, event)) {
            return true;
        }
        // A valid duel hit is allowed, but still handled here so normal allegiance and
        // profession defenses cannot reinterpret it after the duel rules approve it.
        return true;
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerDuelService.onAttackEntity(event)) return;
        ActiveDuel duel = active(player);
        if (duel != null && !event.getTarget().getUUID().equals(duel.villagerId())) {
            event.setCanceled(true);
            return;
        }
        if (isPostLossAttackLocked(player)) event.setCanceled(true);
    }

    public static boolean onFinalDamage(LivingDamageEvent.Pre event) {
        if (PlayerDuelService.onFinalDamage(event)) return true;
        ActiveDuel duel = active(event.getEntity());
        if (duel == null) return false;
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        if (!isOpponent(duel, event.getEntity(), attacker)) { event.setNewDamage(0.0F); return true; }
        float maximum = Math.max(0.0F, event.getEntity().getHealth() - 1.0F);
        if (event.getNewDamage() > 0.0F && event.getNewDamage() >= maximum) {
            event.setNewDamage(maximum);
            if (duel.pendingResult() == null) {
                duel.pendingResult(event.getEntity() instanceof Villager
                        ? DuelResult.PLAYER_WIN : DuelResult.VILLAGER_WIN);
                duel.villagerKnockedOut(event.getEntity() instanceof Villager);
            }
        }
        return true;
    }

    public static boolean isDuelDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        ActiveDuel duel = active(target);
        return duel != null && source.getEntity() instanceof LivingEntity attacker
                && isOpponent(duel, target, attacker)
                || PlayerDuelService.isDuelDamage(target, source);
    }

    private static boolean isProtectedSpectator(LivingEntity target, net.minecraft.world.entity.Entity attacker) {
        if (attacker == null) return false;
        for (ActiveDuel duel : BY_ID.values()) {
            if ((attacker.getUUID().equals(duel.playerId()) || attacker.getUUID().equals(duel.villagerId()))
                    && duel.spectators().contains(target.getUUID())) return true;
        }
        return false;
    }

    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isParticipant(player)) {
            event.setCanceled(true);
        }
    }
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && isParticipant(player)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player
                && isParticipant(player)
                && event.getContainer() != player.inventoryMenu) {
            player.closeContainer();
        }
    }

    public static boolean allowsInventoryClick(
            ServerPlayer player,
            AbstractContainerMenu menu,
            int slotId,
            ClickType clickType) {
        ActiveDuel duel = active(player);
        if (duel == null && PlayerDuelService.isParticipant(player.getUUID()))
            return PlayerDuelService.allowsInventoryClick(player, menu, slotId, clickType);
        if (duel == null) return true;
        if (menu != player.inventoryMenu || !duel.kit().bringYourOwn()) return false;
        if (slotId < InventoryMenu.ARMOR_SLOT_START || slotId > InventoryMenu.SHIELD_SLOT) return false;
        return clickType != ClickType.THROW
                && clickType != ClickType.CLONE
                && clickType != ClickType.QUICK_CRAFT;
    }

    public static boolean isParticipant(LivingEntity entity) {
        return active(entity) != null
                || entity != null && PlayerDuelService.isParticipant(entity.getUUID());
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) return;
        if (PlayerDuelService.onEntityJoinLevel(event)) return;
        if (projectile.getPersistentData().hasUUID(DUEL_PROJECTILE_TAG)) {
            UUID duelId = projectile.getPersistentData().getUUID(DUEL_PROJECTILE_TAG);
            ActiveDuel duel = BY_ID.get(duelId);
            if (duel == null) {
                projectile.discard();
            } else {
                duel.projectiles().add(projectile.getUUID());
            }
            return;
        }
        if (projectile.getOwner() instanceof LivingEntity owner) {
            ActiveDuel duel = active(owner);
            if (duel != null) {
                projectile.getPersistentData().putUUID(DUEL_PROJECTILE_TAG, duel.id());
                duel.projectiles().add(projectile.getUUID());
            }
        }
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        recoverPendingVillager(villager);
        if (isParticipant(villager)) return;
        for (ActiveDuel duel : BY_ID.values()) {
            if (!duel.dimension().equals(level.dimension())
                    || duel.spectators().contains(villager.getUUID())) continue;
            LivingEntity target = villager.getTarget();
            if (target != null && (target.getUUID().equals(duel.playerId())
                    || target.getUUID().equals(duel.villagerId()))) {
                villager.setTarget(null);
                villager.setAggressive(false);
            }
            double clearanceRadius = duel.arenaRadius() + VILLAGER_CLEARANCE;
            if (horizontalDistanceSqr(villager.position(), duel.center()) > clearanceRadius * clearanceRadius) continue;
            double x = villager.getX() - duel.center().x;
            double z = villager.getZ() - duel.center().z;
            double length = Math.sqrt(x * x + z * z);
            if (length < 0.001D) {
                double angle = Math.floorMod(villager.getUUID().hashCode(), 360) * Math.PI / 180.0D;
                x = Math.cos(angle);
                z = Math.sin(angle);
                length = 1.0D;
            }
            double destinationRadius = clearanceRadius + 1.5D;
            double destinationX = duel.center().x + x / length * destinationRadius;
            double destinationZ = duel.center().z + z / length * destinationRadius;
            PathfinderMob mover = villager.getRootVehicle() instanceof PathfinderMob mounted
                    ? mounted : villager;
            mover.getNavigation().moveTo(destinationX, mover.getY(), destinationZ, 0.75D);
            villager.getLookControl().setLookAt(
                    duel.center().x, villager.getEyeY(), duel.center().z, 30.0F, 30.0F);
        }
    }

    private static boolean isOpponent(ActiveDuel duel, LivingEntity target, LivingEntity attacker) {
        return attacker != null && (target.getUUID().equals(duel.playerId()) && attacker.getUUID().equals(duel.villagerId())
                || target.getUUID().equals(duel.villagerId()) && attacker.getUUID().equals(duel.playerId()));
    }

    private static ActiveDuel active(LivingEntity entity) {
        UUID id = entity == null ? null : BY_ENTITY.get(entity.getUUID());
        return id == null ? null : BY_ID.get(id);
    }

    static boolean isParticipantId(UUID entityId) {
        return entityId != null
                && (BY_ENTITY.containsKey(entityId) || PlayerDuelService.isParticipant(entityId));
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (PlayerDuelService.onPlayerLogout(player)) return;
        ActiveDuel duel = active(player);
        if (duel != null) finish(player.getServer(), duel, DuelResult.VILLAGER_WIN, false, player);
    }

    public static void copyPendingPlayerRecovery(ServerPlayer original, ServerPlayer replacement) {
        DuelEquipment.copyRecovery(original, replacement);
    }

    public static boolean recoverPendingPlayer(ServerPlayer player) {
        DuelEquipment.PlayerRecovery recovery = DuelEquipment.playerRecovery(player);
        if (recovery == null || BY_ID.containsKey(recovery.duelId())
                || PlayerDuelService.isActiveDuel(recovery.duelId())) return false;
        recovery.snapshot().restore(player, recovery.assignedLoadout());
        giveCurrency(player, recovery.stake());
        DuelEquipment.clearRecovery(player, recovery.duelId());
        player.inventoryMenu.broadcastFullState();
        syncInventoryState(player, false, false);
        return true;
    }

    public static boolean recoverPendingVillager(Villager villager) {
        DuelEquipment.VillagerRecovery recovery = DuelEquipment.villagerRecovery(villager);
        if (recovery == null || BY_ID.containsKey(recovery.duelId())) return false;
        VillagerCombatBehavior.reset(villager);
        recovery.snapshot().restore(villager);
        VillagerWalletService.addCurrency(
                villager, recovery.stake());
        DuelEquipment.clearRecovery(villager, recovery.duelId());
        villager.setTarget(null);
        villager.setAggressive(false);
        villager.getNavigation().stop();
        VillagerRangedCombatHelper.clearDuelState(villager);
        return true;
    }

    static boolean resolveForTest(ServerPlayer player, DuelResult result) {
        ActiveDuel duel = active(player);
        if (duel == null) return false;
        finish(player.getServer(), duel, result, false, player);
        return true;
    }

    static boolean resolveVillagerKnockoutForTest(ServerPlayer player) {
        ActiveDuel duel = active(player);
        if (duel == null) return false;
        finish(player.getServer(), duel, DuelResult.PLAYER_WIN, true, player);
        return true;
    }
    static void authorizeFinisherForTest(Villager villager, ServerPlayer player, long expiresAt) {
        FINISHERS.put(villager.getUUID(), new FinisherPermission(player.getUUID(), expiresAt));
    }

    static DuelResult pendingResultForTest(ServerPlayer player) {
        ActiveDuel duel = active(player);
        return duel == null ? null : duel.pendingResult();
    }

    static void addSpectatorForTest(ServerPlayer player, Villager spectator) {
        ActiveDuel duel = active(player);
        if (duel != null) duel.spectators().add(spectator.getUUID());
    }

    static void forgetRuntimeStateForTest(ServerPlayer player) {
        ActiveDuel duel = active(player);
        if (duel == null) return;
        BY_ID.remove(duel.id());
        BY_ENTITY.remove(duel.playerId(), duel.id());
        BY_ENTITY.remove(duel.villagerId(), duel.id());
    }

    public static void clearRuntimeState(MinecraftServer server) {
        PlayerDuelService.clearRuntimeState(server);
        for (ActiveDuel duel : List.copyOf(BY_ID.values())) finish(server, duel, DuelResult.CANCELLED, false);
        BY_ID.clear(); BY_ENTITY.clear(); FINISHERS.clear(); LOSS_ATTACK_LOCKOUTS.clear();
    }

    private static void finish(MinecraftServer server, ActiveDuel duel, DuelResult result, boolean knockedOut) {
        finish(server, duel, result, knockedOut, null);
    }

    private static void finish(MinecraftServer server, ActiveDuel duel, DuelResult result, boolean knockedOut,
                               ServerPlayer playerHint) {
        if (BY_ID.remove(duel.id()) == null) return;
        BY_ENTITY.remove(duel.playerId(), duel.id()); BY_ENTITY.remove(duel.villagerId(), duel.id());
        ServerLevel level = server.getLevel(duel.dimension());
        ServerPlayer listedPlayer = server.getPlayerList().getPlayer(duel.playerId());
        ServerPlayer player = playerHint != null && playerHint.getUUID().equals(duel.playerId()) ? playerHint : listedPlayer;
        Villager villager = level != null && level.getEntity(duel.villagerId()) instanceof Villager found ? found : null;
        DuelResult settledResult = result;
        if ((result == DuelResult.PLAYER_WIN && player == null)
                || (result == DuelResult.VILLAGER_WIN && villager == null)) {
            settledResult = DuelResult.CANCELLED;
        }
        for (UUID projectileId : duel.projectiles()) {
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.getEntity(projectileId) instanceof Projectile projectile) {
                    projectile.discard();
                    break;
                }
            }
        }
        if (player != null) {
            boolean assignedLoadout = !duel.kit().bringYourOwn();
            duel.snapshots().player().restore(player, assignedLoadout);
            player.inventoryMenu.broadcastFullState();
            syncInventoryState(player, false, false);
            syncFxState(player, null, settledResult);
        }
        if (villager != null) {
            VillagerCombatBehavior.reset(villager);
            duel.snapshots().villager().restore(villager);
            // Restore first so a knockout can preserve the exact pre-duel recovery target.
            if (knockedOut) VillagerDownedService.ensureStandingDimensions(villager);
            villager.setTarget(null); villager.setAggressive(false); villager.getNavigation().stop();
            VillagerRangedCombatHelper.clearDuelState(villager);
        }
        if (level != null) DuelSpectators.release(level, duel.spectators());
        settle(player, villager, duel.stake(), settledResult);
        if (player != null) DuelEquipment.clearRecovery(player, duel.id());
        if (villager != null) DuelEquipment.clearRecovery(villager, duel.id());
        if (settledResult != DuelResult.CANCELLED && level != null && villager != null)
            complete(server, level, player, villager, duel, settledResult, knockedOut);
        if (settledResult == DuelResult.VILLAGER_WIN && player != null) applyLossPenalty(server, player);
        if (settledResult == DuelResult.PLAYER_WIN && player != null) playPlayerDuelVictorySound(player);
        if (player != null) player.sendSystemMessage(Component.translatable("villagerretaliation.duel.result." + settledResult.name().toLowerCase()));
    }

    static void playPlayerDuelVictorySound(ServerPlayer player) {
        player.serverLevel().playSound(
                null,
                player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                DUEL_VICTORY_SOUND_VOLUME,
                DUEL_VICTORY_SOUND_PITCH);
    }

    static void applyLossPenalty(MinecraftServer server, ServerPlayer player) {
        LOSS_ATTACK_LOCKOUTS.put(player.getUUID(), server.overworld().getGameTime() + LOSS_PENALTY_TICKS);
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, LOSS_PENALTY_TICKS, LOSS_SLOWNESS_AMPLIFIER));
    }

    static boolean isPostLossAttackLocked(ServerPlayer player) {
        Long expiresAt = LOSS_ATTACK_LOCKOUTS.get(player.getUUID());
        if (expiresAt == null) return false;
        if (player.getServer().overworld().getGameTime() < expiresAt) return true;
        LOSS_ATTACK_LOCKOUTS.remove(player.getUUID());
        return false;
    }

    private static void complete(MinecraftServer server, ServerLevel level, ServerPlayer player, Villager villager,
                                 ActiveDuel duel, DuelResult result, boolean knockedOut) {
        DuelSavedData data = DuelSavedData.get(level);
        DuelSavedData.DuelRecord record = data.complete(villager.getUUID(), duel.playerId(), result);
        train(level, villager, duel);
        UUID villageId = VillageEventMemory.villageForVillager(level, villager).map(value -> value.value()).orElse(null);
        data.remember(new DuelSavedData.DuelMemory(duel.id(), villager.getUUID(), duel.playerId(),
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(), player == null ? "Player" : player.getGameProfile().getName(),
                result, duel.stake(), server.overworld().getGameTime(), BlockPos.containing(duel.center()).asLong(), villageId,
                duel.spectators(), record.villagerWins(), record.villagerLosses()));
        if (result == DuelResult.PLAYER_WIN && player != null) {
            DuelSpectators.reward(level, duel.spectators(), duel.center(), duel.arenaRadius(), player);
        }
        if (knockedOut && VillagerSecondWindCompat.isActive()) {
            boolean enteredDowned = VillagerDownedService.enterDowned(level, villager,
                    new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("duel:" + duel.id())),
                    duel.snapshots().villager().health());
            if (enteredDowned) {
                if (player != null) DuelDialogueService.queuePostRecoveryDialogue(villager, player, result);
                FINISHERS.put(villager.getUUID(), new FinisherPermission(
                        duel.playerId(), server.overworld().getGameTime() + 1200L));
                return;
            }
        }
        if (player != null) DuelDialogueService.startPostDuelDialogue(player, villager, result);
    }

    private static void settle(ServerPlayer player, Villager villager, int stake, DuelResult result) {
        int pot = stake * 2;
        switch (result) {
            case PLAYER_WIN -> {
                if (player != null) {
                    giveCurrency(player, pot);
                } else if (villager != null) {
                    VillagerWalletService.addCurrency(villager, pot);
                }
            }
            case VILLAGER_WIN -> {
                if (villager != null) {
                    VillagerWalletService.addCurrency(villager, pot);
                }
            }
            case DRAW, CANCELLED -> {
                if (player != null) {
                    giveCurrency(player, stake);
                }
                if (villager != null) {
                    VillagerWalletService.addCurrency(villager, stake);
                }
            }
        }
    }

    static void giveCurrency(ServerPlayer player, int amount) {
        if (player == null) return;
        for (int remaining = amount; remaining > 0;) {
            ItemStack stack = VillagerCurrencyResources.createStack(player.getServer(), remaining);
            if (stack.isEmpty()) return;
            remaining -= stack.getCount();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        player.getInventory().setChanged();
    }

    static void syncInventoryState(ServerPlayer player, boolean active, boolean assignedLoadout) {
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player, new DuelInventoryStatePayload(active, assignedLoadout));
        } catch (UnsupportedOperationException ignored) {
            // Mock GameTest connections do not negotiate custom payloads.
        }
    }

    private static void syncFxState(ServerPlayer player, ActiveDuel duel, DuelResult result) {
        try {
            DuelFxStatePayload payload = duel == null
                    ? DuelFxStatePayload.inactive(visualResult(result))
                    : new DuelFxStatePayload(
                            true,
                            VillagerRetaliationConfig.SHOW_DUEL_ARENA_PARTICLES.get(),
                            duel.center().x,
                            duel.center().y,
                            duel.center().z,
                            duel.arenaRadius(),
                            duel.boundaryGraceTicks(),
                            (int) COUNTDOWN_TICKS,
                            DuelFxStatePayload.RESULT_NONE);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException ignored) {
            // Mock GameTest connections do not negotiate custom payloads.
        }
    }

    private static int visualResult(DuelResult result) {
        if (result == null) return DuelFxStatePayload.RESULT_NONE;
        return switch (result) {
            case PLAYER_WIN -> DuelFxStatePayload.RESULT_WIN;
            case VILLAGER_WIN -> DuelFxStatePayload.RESULT_LOSS;
            case DRAW -> DuelFxStatePayload.RESULT_DRAW;
            case CANCELLED -> DuelFxStatePayload.RESULT_NONE;
        };
    }

    private static void train(ServerLevel level, Villager villager, ActiveDuel duel) {
        VillagerSkill combat = duel.kit().rangedCombat() || duel.kit().bringYourOwn()
                && isUsingRangedWeapon(villager) ? VillagerSkill.ARCHERY : VillagerSkill.GUARDING;
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        profile.setSocialAttribute(
                VillagerSocialAttribute.GUTS,
                profile.socialAttributes().guts() + DUEL_GUTS_REWARD, level.getGameTime());
        VillagerSkillProgressionService.apply(profile, List.of(
                new VillagerSkillPractice(combat, 1.0D, "duel:combat", duel.playerId().hashCode()),
                new VillagerSkillPractice(VillagerSkill.SURVIVAL, 0.5D, "duel:survival", duel.playerId().hashCode())),
                level.getServer().overworld().getDayTime() / DAY_TICKS, level.getGameTime(), 1.0D);
        VillagerProfileSavedData.get(level).setDirty();
    }

    public static boolean isAuthorizedFinisher(Villager villager, net.minecraft.world.damagesource.DamageSource source) {
        FinisherPermission permission = FINISHERS.get(villager.getUUID());
        if (permission == null || !(source.getEntity() instanceof ServerPlayer player) || !permission.playerId().equals(player.getUUID())) return false;
        return !(villager.level() instanceof ServerLevel level)
                || !VillagerDeathProtectionResolver.resolve(level, villager).protectedFromDeath();
    }

    public static void consumeAuthorizedFinisher(Villager villager, net.minecraft.world.damagesource.DamageSource source) {
        if (isAuthorizedFinisher(villager, source)) {
            FINISHERS.remove(villager.getUUID());
        }
    }

    private record FinisherPermission(UUID playerId, long expiresAt) {}
    public record StartResult(boolean started, DuelAvailabilityReason reason, UUID duelId) {}

    private static final class ActiveDuel {
        private final UUID id; private final ResourceKey<Level> dimension; private final UUID playerId, villagerId;
        private final DuelKit kit; private final int stake; private final Vec3 center;
        private final int arenaRadius, boundaryGraceTicks; private final long countdownEndsAt, timeoutAt;
        private final DuelEquipment.Snapshots snapshots;
        private final Set<UUID> spectators, projectiles = new HashSet<>();
        private long playerOutsideSince = -1L, villagerOutsideSince = -1L, nextAttackAt;
        private int lastCountdownSecond = -1, lastBoundarySecond = -1;
        private DuelResult pendingResult; private boolean villagerKnockedOut;
        ActiveDuel(UUID id, ResourceKey<Level> dimension, UUID playerId, UUID villagerId, DuelKit kit, int stake,
                   Vec3 center, int arenaRadius, int boundaryGraceTicks, long countdownEndsAt, long timeoutAt,
                   DuelEquipment.Snapshots snapshots, Set<UUID> spectators) {
            this.id=id;this.dimension=dimension;this.playerId=playerId;this.villagerId=villagerId;this.kit=kit;this.stake=stake;
            this.center=center;this.arenaRadius=arenaRadius;this.boundaryGraceTicks=boundaryGraceTicks;
            this.countdownEndsAt=countdownEndsAt;this.timeoutAt=timeoutAt;this.snapshots=snapshots;this.spectators=new HashSet<>(spectators);
        }
        UUID id(){return id;} ResourceKey<Level> dimension(){return dimension;} UUID playerId(){return playerId;} UUID villagerId(){return villagerId;}
        DuelKit kit(){return kit;} int stake(){return stake;} Vec3 center(){return center;}
        int arenaRadius(){return arenaRadius;} int boundaryGraceTicks(){return boundaryGraceTicks;} long countdownEndsAt(){return countdownEndsAt;}
        long timeoutAt(){return timeoutAt;} DuelEquipment.Snapshots snapshots(){return snapshots;} Set<UUID> spectators(){return spectators;}
        Set<UUID> projectiles(){return projectiles;}
        long playerOutsideSince(){return playerOutsideSince;} void playerOutsideSince(long v){playerOutsideSince=v;}
        long villagerOutsideSince(){return villagerOutsideSince;} void villagerOutsideSince(long v){villagerOutsideSince=v;}
        long nextAttackAt(){return nextAttackAt;} void nextAttackAt(long v){nextAttackAt=v;}
        int lastCountdownSecond(){return lastCountdownSecond;} void lastCountdownSecond(int v){lastCountdownSecond=v;}
        int lastBoundarySecond(){return lastBoundarySecond;} void lastBoundarySecond(int v){lastBoundarySecond=v;}
        DuelResult pendingResult(){return pendingResult;} void pendingResult(DuelResult v){pendingResult=v;}
        boolean villagerKnockedOut(){return villagerKnockedOut;} void villagerKnockedOut(boolean v){villagerKnockedOut=v;}
    }
}
