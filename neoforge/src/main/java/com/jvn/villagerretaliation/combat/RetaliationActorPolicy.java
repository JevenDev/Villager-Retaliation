package com.jvn.villagerretaliation.combat;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;

interface RetaliationActorPolicy<T extends AbstractVillager> {
    boolean canFightBack(T actor);

    boolean canUseTemporaryCombatLoadout(T actor);

    boolean canScavengeGroundWeapons(T actor);

    ItemStack preferredWeapon(T actor);

    double meleeAttackDamageBase(T actor);

    double movementSpeed(T actor);

    int attackCooldown(T actor);
}
