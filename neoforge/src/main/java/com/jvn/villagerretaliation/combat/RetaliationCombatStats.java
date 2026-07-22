package com.jvn.villagerretaliation.combat;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

final class RetaliationCombatStats {
    static final double WALK_SPEED = 0.5D;
    static final double RUN_SPEED = 0.35D;
    static final double COMBAT_SPEED_MODIFIER = RUN_SPEED / WALK_SPEED;
    static final double PLAYER_FIST_DAMAGE = 1.0D;
    private static final double NORMAL_DIFFICULTY_DAMAGE_BONUS = 0.5D;
    private static final double HARD_DIFFICULTY_DAMAGE_BONUS = 1.0D;

    private RetaliationCombatStats() {
    }

    static double meleeAttackDamageBase(ItemStack weapon, Difficulty difficulty) {
        double unarmedDamage = hasAttackDamageModifier(weapon) ? 0.0D : PLAYER_FIST_DAMAGE;
        return unarmedDamage + switch (difficulty) {
            case NORMAL -> NORMAL_DIFFICULTY_DAMAGE_BONUS;
            case HARD -> HARD_DIFFICULTY_DAMAGE_BONUS;
            default -> 0.0D;
        };
    }

    private static boolean hasAttackDamageModifier(ItemStack stack) {
        boolean[] found = new boolean[]{false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                found[0] = true;
            }
        });
        return found[0];
    }
}
