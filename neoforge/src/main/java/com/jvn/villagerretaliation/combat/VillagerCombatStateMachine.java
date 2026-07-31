package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.party.PartyWeaponPreference;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Selects a tactical combat mode independently from whichever weapon is currently held. */
final class VillagerCombatStateMachine {
    private static final double ENTER_MELEE_DISTANCE = 4.0D;
    private static final double ENTER_RANGED_DISTANCE = 7.0D;
    private static final double ENTER_MELEE_DISTANCE_SQR = ENTER_MELEE_DISTANCE * ENTER_MELEE_DISTANCE;
    private static final double ENTER_RANGED_DISTANCE_SQR = ENTER_RANGED_DISTANCE * ENTER_RANGED_DISTANCE;
    private static final Map<UUID, CombatMode> MODES = new HashMap<>();

    private VillagerCombatStateMachine() {
    }

    static CombatMode prepare(Villager villager, LivingEntity target, double distanceSqr) {
        VillagerDownedService.ensureStandingDimensions(villager);
        UUID villagerId = villager.getUUID();
        CombatMode previous = MODES.get(villagerId);
        boolean targetBlocking = isShielding(target);
        boolean hasAxe = VillagerCombatLoadoutService.hasCombatWeapon(
                villager, stack -> stack.getItem() instanceof AxeItem);
        boolean hasMelee = VillagerCombatLoadoutService.hasCombatWeapon(
                villager, VillagerRetaliationVillagerWeapons::isMeleeWeapon);
        boolean hasRanged = VillagerCombatLoadoutService.hasCombatWeapon(
                villager, stack -> VillagerCombatLoadoutService.canUseRangedWeapon(villager, stack));
        PartyWeaponPreference preference = VillagerCombatLoadoutService.preference(villager);
        CombatMode selected;
        if (!targetBlocking && preference == PartyWeaponPreference.RANGED && hasRanged) {
            selected = CombatMode.RANGED;
        } else if (!targetBlocking && preference == PartyWeaponPreference.MELEE && hasMelee) {
            selected = CombatMode.MELEE;
        } else {
            selected = selectMode(previous, distanceSqr, targetBlocking, hasAxe, hasMelee, hasRanged);
        }

        ItemStack before = villager.getMainHandItem().copy();
        if (selected != CombatMode.RANGED) {
            VillagerRangedCombatHelper.cancelForWeaponSwitch(villager);
        }
        switch (selected) {
            case AXE_BREAKER -> VillagerCombatLoadoutService.equipCombatWeapon(
                    villager, stack -> stack.getItem() instanceof AxeItem);
            case MELEE -> VillagerCombatLoadoutService.equipCombatWeapon(
                    villager, VillagerRetaliationVillagerWeapons::isMeleeWeapon);
            case RANGED -> VillagerCombatLoadoutService.equipCombatWeapon(
                    villager, stack -> VillagerCombatLoadoutService.canUseRangedWeapon(villager, stack));
        }

        ItemStack equipped = villager.getMainHandItem();
        if (selected == CombatMode.RANGED && !ItemStack.isSameItem(before, equipped)) {
            VillagerRangedCombatHelper.clearState(villager);
            VillagerRangedCombatHelper.seedInitialAttackDelay(villager, equipped);
        }
        MODES.put(villagerId, selected);
        return selected;
    }

    static CombatMode selectMode(
            CombatMode previous,
            double distanceSqr,
            boolean targetBlocking,
            boolean hasAxe,
            boolean hasMelee,
            boolean hasRanged) {
        if (targetBlocking && hasAxe) {
            return CombatMode.AXE_BREAKER;
        }
        if (!hasRanged) {
            return CombatMode.MELEE;
        }
        if (!hasMelee) {
            return CombatMode.RANGED;
        }
        if (previous == CombatMode.RANGED) {
            return distanceSqr <= ENTER_MELEE_DISTANCE_SQR ? CombatMode.MELEE : CombatMode.RANGED;
        }
        return distanceSqr >= ENTER_RANGED_DISTANCE_SQR ? CombatMode.RANGED : CombatMode.MELEE;
    }

    static boolean isUsingRangedMode(Villager villager) {
        return MODES.get(villager.getUUID()) == CombatMode.RANGED;
    }

    static boolean isUsingAxeBreakerMode(Villager villager) {
        return MODES.get(villager.getUUID()) == CombatMode.AXE_BREAKER;
    }

    static boolean hasActiveMode(Villager villager) {
        return MODES.containsKey(villager.getUUID());
    }

    /**
     * Performs an axe shield break as its own combat action. Calling this before a
     * melee hit keeps the blocked swing from also damaging the shield's holder.
     *
     * @return {@code true} when the attack was consumed breaking a shield
     */
    static boolean tryBreakTargetShield(Villager villager, LivingEntity target) {
        if (!(villager.getMainHandItem().getItem() instanceof AxeItem) || !isShielding(target)) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            player.disableShield();
            player.stopUsingItem();
        } else {
            target.stopUsingItem();
        }
        return true;
    }

    static void clearState(Villager villager) {
        MODES.remove(villager.getUUID());
    }

    static void clearRuntimeState() {
        MODES.clear();
    }

    private static boolean isShielding(LivingEntity target) {
        return target != null
                && (target.isBlocking()
                || target.isUsingItem() && target.getUseItem().is(Items.SHIELD));
    }

    enum CombatMode {
        MELEE,
        RANGED,
        AXE_BREAKER
    }
}
