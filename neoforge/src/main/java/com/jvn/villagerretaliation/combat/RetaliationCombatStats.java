package com.jvn.villagerretaliation.combat;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

final class RetaliationCombatStats {
    static final double WALK_SPEED = 0.5D;
    static final double RUN_SPEED = 0.35D;
    static final double COMBAT_SPEED_MODIFIER = RUN_SPEED / WALK_SPEED;
    static final double PLAYER_FIST_DAMAGE = 1.0D;
    static final double VINDICATOR_STYLE_WEAPON_BASE_DAMAGE = 5.0D;

    private RetaliationCombatStats() {
    }

    static double meleeAttackDamageBase(ItemStack weapon) {
        if (weapon.isEmpty() || !hasAttackDamageModifier(weapon)) {
            return PLAYER_FIST_DAMAGE;
        }

        return VINDICATOR_STYLE_WEAPON_BASE_DAMAGE;
    }

    private static boolean hasAttackDamageModifier(ItemStack stack) {
        boolean[] hasAttackDamageModifier = new boolean[]{false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                hasAttackDamageModifier[0] = true;
            }
        });
        return hasAttackDamageModifier[0];
    }
}
