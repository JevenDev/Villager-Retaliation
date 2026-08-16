package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.List;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WanderingTraderCombatRoles {
    private static final RetaliationActorPolicy<WanderingTrader> POLICY = new WanderingTraderActorPolicy();
    private static final List<Item> PREFERRED_WEAPON_POOL = List.of(
            Items.IRON_SWORD,
            Items.IRON_AXE,
            Items.IRON_PICKAXE,
            Items.IRON_HOE,
            Items.BOW,
            Items.CROSSBOW,
            Items.TRIDENT
    );

    private WanderingTraderCombatRoles() {
    }

    static RetaliationActorPolicy<WanderingTrader> policy() {
        return POLICY;
    }

    public static boolean canFightBack(WanderingTrader trader) {
        return trader.isAlive();
    }

    public static boolean canUseTemporaryCombatLoadout(WanderingTrader trader) {
        return true;
    }

    public static boolean canScavengeGroundWeapons(WanderingTrader trader) {
        return VillagerRetaliationConfig.WANDERING_TRADERS_PICK_UP_GROUND_WEAPONS.get();
    }

    public static ItemStack preferredWeapon(WanderingTrader trader) {
        int index = Math.floorMod(trader.getUUID().hashCode(), PREFERRED_WEAPON_POOL.size());
        return new ItemStack(PREFERRED_WEAPON_POOL.get(index));
    }

    public static double meleeAttackDamageBase(WanderingTrader trader) {
        return RetaliationCombatStats.meleeAttackDamageBase(
                trader.getMainHandItem(), trader.level().getDifficulty());
    }

    public static double movementSpeed(WanderingTrader trader) {
        return RetaliationCombatStats.COMBAT_SPEED_MODIFIER;
    }

    public static int attackCooldown(WanderingTrader trader) {
        return 20;
    }

    private static final class WanderingTraderActorPolicy implements RetaliationActorPolicy<WanderingTrader> {
        @Override
        public boolean canFightBack(WanderingTrader trader) {
            return WanderingTraderCombatRoles.canFightBack(trader);
        }

        @Override
        public boolean canUseTemporaryCombatLoadout(WanderingTrader trader) {
            return WanderingTraderCombatRoles.canUseTemporaryCombatLoadout(trader);
        }

        @Override
        public boolean canScavengeGroundWeapons(WanderingTrader trader) {
            return WanderingTraderCombatRoles.canScavengeGroundWeapons(trader);
        }

        @Override
        public ItemStack preferredWeapon(WanderingTrader trader) {
            return WanderingTraderCombatRoles.preferredWeapon(trader);
        }

        @Override
        public double meleeAttackDamageBase(WanderingTrader trader) {
            return WanderingTraderCombatRoles.meleeAttackDamageBase(trader);
        }

        @Override
        public double movementSpeed(WanderingTrader trader) {
            return WanderingTraderCombatRoles.movementSpeed(trader);
        }

        @Override
        public int attackCooldown(WanderingTrader trader) {
            return WanderingTraderCombatRoles.attackCooldown(trader);
        }
    }
}
