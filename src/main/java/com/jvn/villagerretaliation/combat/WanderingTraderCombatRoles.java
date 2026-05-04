package com.jvn.villagerretaliation.combat;

import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WanderingTraderCombatRoles {
    private static final List<Item> PREFERRED_WEAPON_POOL = List.of(
            Items.IRON_SWORD,
            Items.IRON_AXE,
            Items.IRON_PICKAXE,
            Items.IRON_HOE,
            Items.BOW,
            Items.CROSSBOW,
            Items.TRIDENT
    );
    private static final double VINDICATOR_STYLE_WEAPON_BASE_DAMAGE = 5.0D;

    private WanderingTraderCombatRoles() {
    }

    public static boolean canFightBack(WanderingTrader trader) {
        return trader.isAlive();
    }

    public static boolean canUseTemporaryCombatLoadout(WanderingTrader trader) {
        return true;
    }

    public static boolean canScavengeGroundWeapons(WanderingTrader trader) {
        return true;
    }

    public static ItemStack preferredWeapon(WanderingTrader trader) {
        int index = Math.floorMod(trader.getUUID().hashCode(), PREFERRED_WEAPON_POOL.size());
        return new ItemStack(PREFERRED_WEAPON_POOL.get(index));
    }

    public static double meleeAttackDamageBase(WanderingTrader trader) {
        ItemStack weapon = trader.getMainHandItem();
        if (weapon.isEmpty()) {
            return VillagerCombatRoles.PLAYER_FIST_DAMAGE;
        }

        if (!hasAttackDamageModifier(weapon)) {
            return VillagerCombatRoles.PLAYER_FIST_DAMAGE;
        }

        return VINDICATOR_STYLE_WEAPON_BASE_DAMAGE;
    }

    public static double movementSpeed(WanderingTrader trader) {
        return 0.4D;
    }

    public static int attackCooldown(WanderingTrader trader) {
        return 20;
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
