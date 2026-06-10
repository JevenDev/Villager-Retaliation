package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.AngerTarget;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.TemporaryWeaponState;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class VillagerRetaliationRetaliationRuntime<T extends AbstractVillager> {
    private static final long GROUND_WEAPON_SCAN_COOLDOWN_TICKS = 10L;
    private static final long GROUND_WEAPON_PURSUIT_GIVE_UP_TICKS = 200L;
    private static final long GROUND_WEAPON_PURSUIT_DISABLE_TICKS = 200L;
    private static final long PERSISTED_ANGER_RESTORE_SCAN_INTERVAL_TICKS = 20L;

    private final String persistentTagRoot;
    private final Map<UUID, AngerTarget> angerTargets = new HashMap<>();
    private final Map<UUID, Long> nextPersistedAngerRestoreTicks = new HashMap<>();
    private final Map<UUID, Long> nextAttackTicks = new HashMap<>();
    private final Map<UUID, Long> nextGroundWeaponScanTicks = new HashMap<>();
    private final Map<UUID, UUID> pursuedGroundWeaponIds = new HashMap<>();
    private final Map<UUID, Long> groundWeaponPursuitStartTicks = new HashMap<>();
    private final Map<UUID, Long> groundWeaponPickupDisabledUntilTicks = new HashMap<>();
    private final Map<UUID, TemporaryWeaponState> temporaryWeapons = new HashMap<>();

    VillagerRetaliationRetaliationRuntime(String persistentTagRoot) {
        this.persistentTagRoot = persistentTagRoot;
    }

    boolean hasAnger(T villager) {
        return this.angerTargets.containsKey(villager.getUUID());
    }

    AngerTarget angerTarget(T villager) {
        return this.angerTargets.get(villager.getUUID());
    }

    boolean anger(T villager, LivingEntity attacker) {
        return VillagerRetaliationRetaliationUtil.tryAnger(villager, attacker, this.angerTargets, this.persistentTagRoot);
    }

    void restorePersistedAngerIfNeeded(T villager) {
        UUID villagerId = villager.getUUID();
        if (this.angerTargets.containsKey(villagerId)) {
            return;
        }
        if (!(villager.level() instanceof ServerLevel level)) {
            VillagerRetaliationRetaliationUtil.restorePersistedAngerIfNeeded(villager, this.angerTargets, this.persistentTagRoot);
            return;
        }

        long gameTime = level.getGameTime();
        if (!TickThrottle.consume(
                villagerId,
                this.nextPersistedAngerRestoreTicks,
                gameTime,
                PERSISTED_ANGER_RESTORE_SCAN_INTERVAL_TICKS)) {
            return;
        }

        VillagerRetaliationRetaliationUtil.restorePersistedAngerIfNeeded(villager, this.angerTargets, this.persistentTagRoot);
    }

    boolean isHostileTowards(T villager, Player player, Runnable clearAnger) {
        return VillagerRetaliationRetaliationUtil.isHostileTowards(villager, player, this.angerTargets, this.persistentTagRoot, clearAnger);
    }

    void refreshAngerTarget(T villager, AngerTarget angerTarget, long gameTime) {
        VillagerRetaliationRetaliationUtil.refreshAngerTarget(villager, angerTarget, gameTime, this.angerTargets, this.persistentTagRoot);
    }

    void clearPersistentAnger(T villager) {
        this.nextPersistedAngerRestoreTicks.remove(villager.getUUID());
        VillagerRetaliationRetaliationUtil.clearPersistentAnger(villager, this.persistentTagRoot);
    }

    boolean isAttackReady(T villager, long gameTime) {
        return VillagerRetaliationRetaliationUtil.isAttackReady(villager, this.nextAttackTicks, gameTime);
    }

    void setNextAttackTick(T villager, long gameTime) {
        this.nextAttackTicks.put(villager.getUUID(), gameTime);
    }

    boolean tryAcquireGroundWeapon(T villager, double movementSpeed, Runnable beforeEquip, long gameTime) {
        UUID villagerId = villager.getUUID();
        long disabledUntil = this.groundWeaponPickupDisabledUntilTicks.getOrDefault(villagerId, 0L);
        if (gameTime < disabledUntil) {
            return false;
        }
        if (disabledUntil != 0L) {
            this.groundWeaponPickupDisabledUntilTicks.remove(villagerId);
        }

        boolean hadPursuedWeapon = this.pursuedGroundWeaponIds.containsKey(villagerId);
        ItemEntity pursuedWeapon = currentPursuedGroundWeapon(villager);
        if (pursuedWeapon != null) {
            if (hasGroundWeaponPursuitTimedOut(villager, gameTime)) {
                disableGroundWeaponPursuit(villager, gameTime);
                return false;
            }
            if (VillagerRetaliationRetaliationUtil.tryAcquireGroundWeapon(villager, pursuedWeapon, movementSpeed, beforeEquip)) {
                return true;
            }
            clearGroundWeaponPursuit(villager);
        } else if (hadPursuedWeapon) {
            clearGroundWeaponPursuit(villager);
        }

        if (gameTime < this.nextGroundWeaponScanTicks.getOrDefault(villagerId, 0L)) {
            return false;
        }

        this.nextGroundWeaponScanTicks.put(villagerId, gameTime + GROUND_WEAPON_SCAN_COOLDOWN_TICKS);
        return VillagerRetaliationVillagerWeapons.findNearestWeapon(villager)
                .map(itemEntity -> {
                    this.pursuedGroundWeaponIds.put(villagerId, itemEntity.getUUID());
                    this.groundWeaponPursuitStartTicks.put(villagerId, gameTime);
                    boolean pursuing = VillagerRetaliationRetaliationUtil.tryAcquireGroundWeapon(villager, itemEntity, movementSpeed, beforeEquip);
                    if (!pursuing) {
                        clearGroundWeaponPursuit(villager);
                    }
                    return pursuing;
                })
                .orElseGet(() -> {
                    clearGroundWeaponPursuit(villager);
                    return false;
                });
    }

    boolean hasTemporaryWeapon(T villager) {
        return this.temporaryWeapons.containsKey(villager.getUUID());
    }

    ItemStack temporaryWeaponFallback(T villager) {
        TemporaryWeaponState state = this.temporaryWeapons.get(villager.getUUID());
        return state == null ? ItemStack.EMPTY : state.previousMainHand().copy();
    }

    boolean maintainTemporaryWeapon(T villager) {
        return VillagerRetaliationRetaliationUtil.maintainTemporaryWeapon(villager, this.temporaryWeapons);
    }

    void equipTemporaryWeapon(T villager, ItemStack weapon) {
        VillagerRetaliationRetaliationUtil.equipTemporaryWeapon(villager, this.temporaryWeapons, weapon);
    }

    void restoreTemporaryWeapon(T villager) {
        VillagerRetaliationRetaliationUtil.restoreTemporaryWeapon(villager, this.temporaryWeapons);
    }

    void discardTemporaryWeapon(T villager) {
        VillagerRetaliationRetaliationUtil.discardTemporaryWeapon(villager, this.temporaryWeapons);
    }

    void clearTransientState(T villager) {
        UUID villagerId = villager.getUUID();
        this.angerTargets.remove(villagerId);
        this.nextPersistedAngerRestoreTicks.remove(villagerId);
        this.nextAttackTicks.remove(villagerId);
        this.nextGroundWeaponScanTicks.remove(villagerId);
        this.pursuedGroundWeaponIds.remove(villagerId);
        this.groundWeaponPursuitStartTicks.remove(villagerId);
        this.groundWeaponPickupDisabledUntilTicks.remove(villagerId);
        this.temporaryWeapons.remove(villagerId);
        VillagerRetaliationRetaliationUtil.clearPathingState(villager);
    }

    private boolean hasGroundWeaponPursuitTimedOut(T villager, long gameTime) {
        UUID villagerId = villager.getUUID();
        Long startedAt = this.groundWeaponPursuitStartTicks.get(villagerId);
        if (startedAt == null) {
            this.groundWeaponPursuitStartTicks.put(villagerId, gameTime);
            return false;
        }

        return gameTime - startedAt > GROUND_WEAPON_PURSUIT_GIVE_UP_TICKS;
    }

    private void disableGroundWeaponPursuit(T villager, long gameTime) {
        clearGroundWeaponPursuit(villager);
        this.groundWeaponPickupDisabledUntilTicks.put(villager.getUUID(), gameTime + GROUND_WEAPON_PURSUIT_DISABLE_TICKS);
    }

    private void clearGroundWeaponPursuit(T villager) {
        UUID villagerId = villager.getUUID();
        this.pursuedGroundWeaponIds.remove(villagerId);
        this.groundWeaponPursuitStartTicks.remove(villagerId);
        VillagerRetaliationRetaliationUtil.clearGroundWeaponPursuitState(villager);
    }

    private ItemEntity currentPursuedGroundWeapon(T villager) {
        UUID weaponId = this.pursuedGroundWeaponIds.get(villager.getUUID());
        if (weaponId == null || !(villager.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return null;
        }

        var entity = level.getEntity(weaponId);
        return entity instanceof ItemEntity itemEntity ? itemEntity : null;
    }
}
