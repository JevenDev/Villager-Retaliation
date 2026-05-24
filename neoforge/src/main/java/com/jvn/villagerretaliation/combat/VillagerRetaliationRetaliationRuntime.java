package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.AngerTarget;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.TemporaryWeaponState;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class VillagerRetaliationRetaliationRuntime<T extends AbstractVillager> {
    private static final long GROUND_WEAPON_SCAN_COOLDOWN_TICKS = 10L;

    private final String persistentTagRoot;
    private final Map<UUID, AngerTarget> angerTargets = new HashMap<>();
    private final Map<UUID, Long> nextAttackTicks = new HashMap<>();
    private final Map<UUID, Long> nextGroundWeaponScanTicks = new HashMap<>();
    private final Map<UUID, UUID> pursuedGroundWeaponIds = new HashMap<>();
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
        VillagerRetaliationRetaliationUtil.restorePersistedAngerIfNeeded(villager, this.angerTargets, this.persistentTagRoot);
    }

    boolean isHostileTowards(T villager, Player player, Runnable clearAnger) {
        return VillagerRetaliationRetaliationUtil.isHostileTowards(villager, player, this.angerTargets, this.persistentTagRoot, clearAnger);
    }

    void refreshAngerTarget(T villager, AngerTarget angerTarget, long gameTime) {
        VillagerRetaliationRetaliationUtil.refreshAngerTarget(villager, angerTarget, gameTime, this.angerTargets, this.persistentTagRoot);
    }

    void clearPersistentAnger(T villager) {
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
        boolean hadPursuedWeapon = this.pursuedGroundWeaponIds.containsKey(villagerId);
        ItemEntity pursuedWeapon = currentPursuedGroundWeapon(villager);
        if (pursuedWeapon != null) {
            if (VillagerRetaliationRetaliationUtil.tryAcquireGroundWeapon(villager, pursuedWeapon, movementSpeed, beforeEquip)) {
                return true;
            }
            this.pursuedGroundWeaponIds.remove(villagerId);
            VillagerRetaliationRetaliationUtil.clearGroundWeaponPursuitState(villager);
        } else if (hadPursuedWeapon) {
            this.pursuedGroundWeaponIds.remove(villagerId);
            VillagerRetaliationRetaliationUtil.clearGroundWeaponPursuitState(villager);
        }

        if (gameTime < this.nextGroundWeaponScanTicks.getOrDefault(villagerId, 0L)) {
            return false;
        }

        this.nextGroundWeaponScanTicks.put(villagerId, gameTime + GROUND_WEAPON_SCAN_COOLDOWN_TICKS);
        return VillagerRetaliationVillagerWeapons.findNearestWeapon(villager)
                .map(itemEntity -> {
                    this.pursuedGroundWeaponIds.put(villagerId, itemEntity.getUUID());
                    boolean pursuing = VillagerRetaliationRetaliationUtil.tryAcquireGroundWeapon(villager, itemEntity, movementSpeed, beforeEquip);
                    if (!pursuing) {
                        this.pursuedGroundWeaponIds.remove(villagerId);
                    }
                    return pursuing;
                })
                .orElseGet(() -> {
                    this.pursuedGroundWeaponIds.remove(villagerId);
                    VillagerRetaliationRetaliationUtil.clearGroundWeaponPursuitState(villager);
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
        this.nextAttackTicks.remove(villagerId);
        this.nextGroundWeaponScanTicks.remove(villagerId);
        this.pursuedGroundWeaponIds.remove(villagerId);
        this.temporaryWeapons.remove(villagerId);
        VillagerRetaliationRetaliationUtil.clearPathingState(villager);
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
