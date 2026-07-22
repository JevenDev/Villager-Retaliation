package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.compat.secondwind.VillagerSecondWindCompat;
import com.jvn.villagerretaliation.combat.VillagerRangedCombatHelper;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
public final class DuelService {
    public static final int[] FIXED_STAKES = {0, 8, 16, 32, 64};
    private static final long DAY_TICKS = 24000L;
    private static final long COUNTDOWN_TICKS = 60L;
    private static final Map<UUID, ActiveDuel> BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> BY_ENTITY = new HashMap<>();
    private static final Map<UUID, FinisherPermission> FINISHERS = new HashMap<>();

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
        if (player.level() != level || player.distanceToSqr(villager) > 64.0D) return DuelAvailabilityReason.TOO_FAR;
        if (HiredVillagerContractService.isHired(level, villager)) return DuelAvailabilityReason.HIRED;
        if (PartyVillagerContractService.isActivePartyVillager(level, villager)) return DuelAvailabilityReason.PARTY;
        if (VillagerDownedService.isDowned(villager)) return DuelAvailabilityReason.DOWNED;
        if (VillagerDeathProtectionResolver.resolve(level, villager).protectedFromDeath()) return DuelAvailabilityReason.PROTECTED;
        if (level.getRaidAt(villager.blockPosition()) != null) return DuelAvailabilityReason.RAID;
        if (record.refuses()) return DuelAvailabilityReason.REFUSES;
        if (cooldown > 0L) return DuelAvailabilityReason.COOLDOWN;
        if (BY_ENTITY.containsKey(player.getUUID())) return DuelAvailabilityReason.PLAYER_BUSY;
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
        ServerLevel level = player.serverLevel();
        DuelAvailability available = availability(level, player, villager);
        if (!available.available()) return new StartResult(false, available.reason(), null);
        int stake = requestedStake == Integer.MAX_VALUE ? available.maximumStake() : requestedStake;
        if (!validStake(stake, available.maximumStake())) return new StartResult(false, DuelAvailabilityReason.INVALID, null);
        if (!VillagerCurrencyPayment.tryRemove(player, stake)) return new StartResult(false, DuelAvailabilityReason.INVALID, null);
        if (!VillagerWalletService.spendCurrency(villager, stake, VillagerWalletService.WalletSource.DUEL)) {
            giveCurrency(player, stake);
            return new StartResult(false, DuelAvailabilityReason.INVALID, null);
        }
        UUID id = UUID.randomUUID();
        long now = level.getServer().overworld().getGameTime();
        Vec3 center = player.position().add(villager.position()).scale(0.5D);
        Set<UUID> spectators = DuelSpectators.recruit(level, villager, center);
        DuelEquipment.Snapshots snapshots = DuelEquipment.prepare(player, villager, loadout);
        ActiveDuel duel = new ActiveDuel(id, level.dimension(), player.getUUID(), villager.getUUID(), loadout, stake,
                center, now + COUNTDOWN_TICKS, now + COUNTDOWN_TICKS + VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.get(),
                snapshots, spectators);
        BY_ID.put(id, duel);
        BY_ENTITY.put(player.getUUID(), id);
        BY_ENTITY.put(villager.getUUID(), id);
        DuelSavedData.get(level).markStarted(villager.getUUID(), player.getUUID(), now);
        VillagerConversationService.endForVillager(villager, true);
        player.sendSystemMessage(Component.translatable("villagerretaliation.duel.started", VillagerPresetNameRegistry.resolveDisplayName(villager)));
        return new StartResult(true, DuelAvailabilityReason.AVAILABLE, id);
    }

    private static boolean validStake(int stake, int maximum) {
        if (stake < 0 || stake > maximum) return false;
        if (stake == maximum) return true;
        for (int fixed : FIXED_STAKES) if (stake == fixed) return true;
        return false;
    }

    public static void tick(MinecraftServer server) {
        for (ActiveDuel duel : List.copyOf(BY_ID.values())) tick(server, duel);
        long now = server.overworld().getGameTime();
        FINISHERS.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
    }

    private static void tick(MinecraftServer server, ActiveDuel duel) {
        ServerLevel level = server.getLevel(duel.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(duel.playerId());
        Villager villager = level != null && level.getEntity(duel.villagerId()) instanceof Villager found ? found : null;
        if (level == null || villager == null || !villager.isAlive()) { finish(server, duel, DuelResult.CANCELLED, false); return; }
        if (player == null) { finish(server, duel, DuelResult.VILLAGER_WIN, false); return; }
        if (player.serverLevel() != level || !player.isAlive()) { finish(server, duel, DuelResult.CANCELLED, false); return; }
        if (duel.pendingResult() != null) { finish(server, duel, duel.pendingResult(), duel.villagerKnockedOut()); return; }
        long now = server.overworld().getGameTime();
        DuelSpectators.maintain(level, duel.spectators(), duel.center(), villager);
        if (now < duel.countdownEndsAt()) { villager.getNavigation().stop(); villager.setTarget(null); return; }
        if (now >= duel.timeoutAt()) { finish(server, duel, DuelResult.DRAW, false); return; }
        updateBoundary(duel, player, villager, now);
        int grace = VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.get();
        if (duel.playerOutsideSince() >= 0L && now - duel.playerOutsideSince() >= grace) { finish(server, duel, DuelResult.VILLAGER_WIN, false); return; }
        if (duel.villagerOutsideSince() >= 0L && now - duel.villagerOutsideSince() >= grace) { finish(server, duel, DuelResult.PLAYER_WIN, false); return; }
        drive(level, duel, villager, player, now);
    }

    private static void updateBoundary(ActiveDuel duel, ServerPlayer player, Villager villager, long now) {
        double radiusSqr = Math.pow(VillagerRetaliationConfig.DUEL_ARENA_RADIUS.get(), 2.0D);
        duel.playerOutsideSince(outside(player.position(), duel.center(), radiusSqr) ? first(duel.playerOutsideSince(), now) : -1L);
        duel.villagerOutsideSince(outside(villager.position(), duel.center(), radiusSqr) ? first(duel.villagerOutsideSince(), now) : -1L);
    }
    private static boolean isUsingRangedWeapon(Villager villager) {
        var item = villager.getMainHandItem().getItem();
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
    }


    private static boolean outside(Vec3 position, Vec3 center, double radiusSqr) { return position.distanceToSqr(center) > radiusSqr; }
    private static long first(long prior, long now) { return prior < 0L ? now : prior; }

    private static void drive(ServerLevel level, ActiveDuel duel, Villager villager, ServerPlayer player, long now) {
        villager.setTarget(player);
        villager.setAggressive(true);
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
        double distance = villager.distanceToSqr(player);
        boolean ranged = duel.loadout() == DuelLoadout.RANGED || duel.loadout() == DuelLoadout.BRING_YOUR_OWN
                && isUsingRangedWeapon(villager);
        if (ranged && VillagerRangedCombatHelper.tryDuelAttack(villager, player, level, distance)) return;
        if (distance > 3.0D) villager.getNavigation().moveTo(player, 1.1D);
        else if (now >= duel.nextAttackAt()) {
            villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            villager.doHurtTarget(player);
            duel.nextAttackAt(now + 20L);
        }
    }

    public static boolean onIncomingDamage(LivingIncomingDamageEvent event) {
        if (isProtectedSpectator(event.getEntity(), event.getSource().getEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return true;
        }

        ActiveDuel duel = active(event.getEntity());
        if (duel == null) return false;
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        long now = ((ServerLevel) event.getEntity().level()).getServer().overworld().getGameTime();
        if (!isOpponent(duel, event.getEntity(), attacker) || now < duel.countdownEndsAt()) {
            event.setCanceled(true); event.setAmount(0.0F); return true;
        }
        return false;
    }

    public static boolean onFinalDamage(LivingDamageEvent.Pre event) {
        ActiveDuel duel = active(event.getEntity());
        if (duel == null) return false;
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        if (!isOpponent(duel, event.getEntity(), attacker)) { event.setNewDamage(0.0F); return true; }
        float maximum = Math.max(0.0F, event.getEntity().getHealth() - 1.0F);
        if (event.getNewDamage() >= maximum) {
            event.setNewDamage(maximum);
            duel.pendingResult(event.getEntity() instanceof Villager ? DuelResult.PLAYER_WIN : DuelResult.VILLAGER_WIN);
            duel.villagerKnockedOut(event.getEntity() instanceof Villager);
        }
        return true;
    }

    public static boolean isDuelDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        ActiveDuel duel = active(target);
        return duel != null && source.getEntity() instanceof LivingEntity attacker && isOpponent(duel, target, attacker);
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
        if (event.getPlayer() instanceof ServerPlayer player && active(player) instanceof ActiveDuel duel && duel.loadout() != DuelLoadout.BRING_YOUR_OWN) event.setCanceled(true);
    }
    public static boolean isParticipant(LivingEntity entity) { return active(entity) != null; }

    private static boolean isOpponent(ActiveDuel duel, LivingEntity target, LivingEntity attacker) {
        return attacker != null && (target.getUUID().equals(duel.playerId()) && attacker.getUUID().equals(duel.villagerId())
                || target.getUUID().equals(duel.villagerId()) && attacker.getUUID().equals(duel.playerId()));
    }

    private static ActiveDuel active(LivingEntity entity) {
        UUID id = entity == null ? null : BY_ENTITY.get(entity.getUUID());
        return id == null ? null : BY_ID.get(id);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        ActiveDuel duel = active(player);
        if (duel != null) finish(player.getServer(), duel, DuelResult.VILLAGER_WIN, false);
    }

    public static void clearRuntimeState(MinecraftServer server) {
        for (ActiveDuel duel : List.copyOf(BY_ID.values())) finish(server, duel, DuelResult.CANCELLED, false);
        BY_ID.clear(); BY_ENTITY.clear(); FINISHERS.clear();
    }

    private static void finish(MinecraftServer server, ActiveDuel duel, DuelResult result, boolean knockedOut) {
        if (BY_ID.remove(duel.id()) == null) return;
        BY_ENTITY.remove(duel.playerId(), duel.id()); BY_ENTITY.remove(duel.villagerId(), duel.id());
        ServerLevel level = server.getLevel(duel.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(duel.playerId());
        Villager villager = level != null && level.getEntity(duel.villagerId()) instanceof Villager found ? found : null;
        if (level != null) {
            AABB cleanup = AABB.ofSize(duel.center(), 80.0D, 80.0D, 80.0D);
            level.getEntitiesOfClass(Projectile.class, cleanup, projectile -> projectile.getOwner() != null
                    && (projectile.getOwner().getUUID().equals(duel.playerId()) || projectile.getOwner().getUUID().equals(duel.villagerId())))
                    .forEach(net.minecraft.world.entity.Entity::discard);
        }
        if (player != null) duel.snapshots().player().restore(player);
        if (villager != null) {
            duel.snapshots().villager().restore(villager);
            villager.setTarget(null); villager.setAggressive(false); villager.getNavigation().stop();
            VillagerRangedCombatHelper.clearDuelState(villager);
        }
        if (level != null) DuelSpectators.release(level, duel.spectators());
        settle(player, villager, duel.stake(), result);
        if (result != DuelResult.CANCELLED && level != null && villager != null) complete(server, level, player, villager, duel, result, knockedOut);
        if (player != null) player.sendSystemMessage(Component.translatable("villagerretaliation.duel.result." + result.name().toLowerCase()));
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
                record.villagerWins(), record.villagerLosses()));
        if (result == DuelResult.PLAYER_WIN && player != null) DuelSpectators.reward(level, duel.spectators(), duel.center(), player);
        if (knockedOut && VillagerSecondWindCompat.isActive()) {
            VillagerDownedService.enterDowned(level, villager,
                    new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("duel:" + duel.id())));
            FINISHERS.put(villager.getUUID(), new FinisherPermission(duel.playerId(), server.overworld().getGameTime() + 1200L));
        }
    }

    private static void settle(ServerPlayer player, Villager villager, int stake, DuelResult result) {
        int pot = stake * 2;
        switch (result) {
            case PLAYER_WIN -> { if (player != null) giveCurrency(player, pot); else if (villager != null) VillagerWalletService.addCurrency(villager, pot, VillagerWalletService.WalletSource.DUEL); }
            case VILLAGER_WIN -> { if (villager != null) VillagerWalletService.addCurrency(villager, pot, VillagerWalletService.WalletSource.DUEL); }
            case DRAW, CANCELLED -> { if (player != null) giveCurrency(player, stake); if (villager != null) VillagerWalletService.addCurrency(villager, stake, VillagerWalletService.WalletSource.DUEL); }
        }
    }

    private static void giveCurrency(ServerPlayer player, int amount) {
        for (int remaining = amount; remaining > 0;) {
            ItemStack stack = VillagerCurrencyResources.createStack(player.getServer(), remaining);
            if (stack.isEmpty()) return;
            remaining -= stack.getCount();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        player.getInventory().setChanged();
    }

    private static void train(ServerLevel level, Villager villager, ActiveDuel duel) {
        VillagerSkill combat = duel.loadout() == DuelLoadout.RANGED || duel.loadout() == DuelLoadout.BRING_YOUR_OWN
                && isUsingRangedWeapon(villager) ? VillagerSkill.ARCHERY : VillagerSkill.GUARDING;
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        VillagerSkillProgressionService.apply(profile, List.of(
                new VillagerSkillPractice(combat, 1.0D, "duel:combat", duel.playerId().hashCode()),
                new VillagerSkillPractice(VillagerSkill.SURVIVAL, 0.5D, "duel:survival", duel.playerId().hashCode())),
                level.getServer().overworld().getDayTime() / DAY_TICKS, level.getGameTime(), 1.0D);
        VillagerProfileSavedData.get(level).setDirty();
    }

    public static boolean isAuthorizedFinisher(Villager villager, net.minecraft.world.damagesource.DamageSource source) {
        FinisherPermission permission = FINISHERS.get(villager.getUUID());
        if (permission == null || !(source.getEntity() instanceof ServerPlayer player) || !permission.playerId().equals(player.getUUID())) return false;
        if (villager.level() instanceof ServerLevel level && VillagerDeathProtectionResolver.resolve(level, villager).protectedFromDeath()) return false;
        FINISHERS.remove(villager.getUUID());
        return true;
    }

    private record FinisherPermission(UUID playerId, long expiresAt) {}
    public record StartResult(boolean started, DuelAvailabilityReason reason, UUID duelId) {}

    private static final class ActiveDuel {
        private final UUID id; private final ResourceKey<Level> dimension; private final UUID playerId, villagerId;
        private final DuelLoadout loadout; private final int stake; private final Vec3 center; private final long countdownEndsAt, timeoutAt;
        private final DuelEquipment.Snapshots snapshots; private final Set<UUID> spectators;
        private long playerOutsideSince = -1L, villagerOutsideSince = -1L, nextAttackAt;
        private DuelResult pendingResult; private boolean villagerKnockedOut;
        ActiveDuel(UUID id, ResourceKey<Level> dimension, UUID playerId, UUID villagerId, DuelLoadout loadout, int stake,
                   Vec3 center, long countdownEndsAt, long timeoutAt, DuelEquipment.Snapshots snapshots, Set<UUID> spectators) {
            this.id=id;this.dimension=dimension;this.playerId=playerId;this.villagerId=villagerId;this.loadout=loadout;this.stake=stake;
            this.center=center;this.countdownEndsAt=countdownEndsAt;this.timeoutAt=timeoutAt;this.snapshots=snapshots;this.spectators=Set.copyOf(spectators);
        }
        UUID id(){return id;} ResourceKey<Level> dimension(){return dimension;} UUID playerId(){return playerId;} UUID villagerId(){return villagerId;}
        DuelLoadout loadout(){return loadout;} int stake(){return stake;} Vec3 center(){return center;} long countdownEndsAt(){return countdownEndsAt;}
        long timeoutAt(){return timeoutAt;} DuelEquipment.Snapshots snapshots(){return snapshots;} Set<UUID> spectators(){return spectators;}
        long playerOutsideSince(){return playerOutsideSince;} void playerOutsideSince(long v){playerOutsideSince=v;}
        long villagerOutsideSince(){return villagerOutsideSince;} void villagerOutsideSince(long v){villagerOutsideSince=v;}
        long nextAttackAt(){return nextAttackAt;} void nextAttackAt(long v){nextAttackAt=v;}
        DuelResult pendingResult(){return pendingResult;} void pendingResult(DuelResult v){pendingResult=v;}
        boolean villagerKnockedOut(){return villagerKnockedOut;} void villagerKnockedOut(boolean v){villagerKnockedOut=v;}
    }
}
