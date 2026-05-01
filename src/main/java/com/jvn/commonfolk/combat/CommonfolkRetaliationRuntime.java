package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.combat.CommonfolkRetaliationUtil.AngerTarget;
import com.jvn.commonfolk.combat.CommonfolkRetaliationUtil.TemporaryWeaponState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class CommonfolkRetaliationRuntime<T extends AbstractVillager> {
    private static final long GROUND_WEAPON_SCAN_COOLDOWN_TICKS = 5L;

    private final String persistentTagRoot;
    private final Map<UUID, AngerTarget> angerTargets = new HashMap<>();
    private final Map<UUID, Long> nextAttackTicks = new HashMap<>();
    private final Map<UUID, Long> nextGroundWeaponScanTicks = new HashMap<>();
    private final Map<UUID, TemporaryWeaponState> temporaryWeapons = new HashMap<>();

    CommonfolkRetaliationRuntime(String persistentTagRoot) {
        this.persistentTagRoot = persistentTagRoot;
    }

    boolean hasAnger(T villager) {
        return this.angerTargets.containsKey(villager.getUUID());
    }

    AngerTarget angerTarget(T villager) {
        return this.angerTargets.get(villager.getUUID());
    }

    boolean anger(T villager, LivingEntity attacker) {
        return CommonfolkRetaliationUtil.tryAnger(villager, attacker, this.angerTargets, this.persistentTagRoot);
    }

    void restorePersistedAngerIfNeeded(T villager) {
        CommonfolkRetaliationUtil.restorePersistedAngerIfNeeded(villager, this.angerTargets, this.persistentTagRoot);
    }

    boolean isHostileTowards(T villager, Player player, Runnable clearAnger) {
        return CommonfolkRetaliationUtil.isHostileTowards(villager, player, this.angerTargets, this.persistentTagRoot, clearAnger);
    }

    void refreshAngerTarget(T villager, AngerTarget angerTarget, long gameTime) {
        CommonfolkRetaliationUtil.refreshAngerTarget(villager, angerTarget, gameTime, this.angerTargets, this.persistentTagRoot);
    }

    void clearPersistentAnger(T villager) {
        CommonfolkRetaliationUtil.clearPersistentAnger(villager, this.persistentTagRoot);
    }

    boolean isAttackReady(T villager, long gameTime) {
        return CommonfolkRetaliationUtil.isAttackReady(villager, this.nextAttackTicks, gameTime);
    }

    void setNextAttackTick(T villager, long gameTime) {
        this.nextAttackTicks.put(villager.getUUID(), gameTime);
    }

    boolean tryAcquireGroundWeapon(T villager, double movementSpeed, Runnable beforeEquip, long gameTime) {
        UUID villagerId = villager.getUUID();
        if (gameTime < this.nextGroundWeaponScanTicks.getOrDefault(villagerId, 0L)) {
            return false;
        }

        this.nextGroundWeaponScanTicks.put(villagerId, gameTime + GROUND_WEAPON_SCAN_COOLDOWN_TICKS);
        return CommonfolkRetaliationUtil.tryAcquireGroundWeapon(villager, movementSpeed, beforeEquip);
    }

    boolean hasTemporaryWeapon(T villager) {
        return this.temporaryWeapons.containsKey(villager.getUUID());
    }

    ItemStack temporaryWeaponFallback(T villager) {
        TemporaryWeaponState state = this.temporaryWeapons.get(villager.getUUID());
        return state == null ? ItemStack.EMPTY : state.previousMainHand().copy();
    }

    boolean maintainTemporaryWeapon(T villager) {
        return CommonfolkRetaliationUtil.maintainTemporaryWeapon(villager, this.temporaryWeapons);
    }

    void equipTemporaryWeapon(T villager, ItemStack weapon) {
        CommonfolkRetaliationUtil.equipTemporaryWeapon(villager, this.temporaryWeapons, weapon);
    }

    void restoreTemporaryWeapon(T villager) {
        CommonfolkRetaliationUtil.restoreTemporaryWeapon(villager, this.temporaryWeapons);
    }

    void discardTemporaryWeapon(T villager) {
        CommonfolkRetaliationUtil.discardTemporaryWeapon(villager, this.temporaryWeapons);
    }

    void clearTransientState(T villager) {
        UUID villagerId = villager.getUUID();
        this.angerTargets.remove(villagerId);
        this.nextAttackTicks.remove(villagerId);
        this.nextGroundWeaponScanTicks.remove(villagerId);
        this.temporaryWeapons.remove(villagerId);
    }
}
