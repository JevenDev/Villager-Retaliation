package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

public final class VillagerCombatAttributeCompat {
    private static final net.minecraft.resources.ResourceLocation GUARDING_DAMAGE_MODIFIER_ID =
            VillagerRetaliation.id("guarding_damage");

    private VillagerCombatAttributeCompat() {
    }

    public static void ensureCombatAttributes(LivingEntity entity) {
        if (!(entity instanceof Villager) && !(entity instanceof WanderingTrader)) {
            return;
        }

        // NeoForge's startup attribute event exposes entity types but not their runtime classes,
        // so custom Villager subclasses must be recognized once an entity instance exists.
        AttributeMapExtension attributes = (AttributeMapExtension) entity.getAttributes();
        if (!entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
            attributes.villagerretaliation$addAttribute(
                    Attributes.ATTACK_DAMAGE,
                    VillagerCombatRoles.PLAYER_FIST_DAMAGE
            );
        }
        if (!entity.getAttributes().hasAttribute(Attributes.ATTACK_KNOCKBACK)) {
            attributes.villagerretaliation$addAttribute(Attributes.ATTACK_KNOCKBACK, 0.0D);
        }
    }

    public static boolean syncMeleeAttackAttributes(LivingEntity entity) {
        if (!(entity instanceof Villager) && !(entity instanceof WanderingTrader)) {
            return false;
        }
        ensureCombatAttributes(entity);
        AttributeInstance attackDamage = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null || entity.getAttribute(Attributes.ATTACK_KNOCKBACK) == null) {
            return false;
        }

        // A weapon's equipment modifier already represents its full displayed damage.
        // Add only fist damage when unarmed, plus a modest difficulty bonus.
        double desiredBaseDamage = RetaliationCombatStats.meleeAttackDamageBase(
                entity.getMainHandItem(), entity.level().getDifficulty());
        if (attackDamage.getBaseValue() != desiredBaseDamage) {
            attackDamage.setBaseValue(desiredBaseDamage);
        }

        attackDamage.removeModifier(GUARDING_DAMAGE_MODIFIER_ID);
        if (entity instanceof AbstractVillager villager && entity.level() instanceof ServerLevel level) {
            int damagePercent = VillagerCombatSkillBehavior.meleeDamagePercent(
                    VillagerCombatSkillBehavior.guarding(level, villager));
            if (damagePercent != 100) {
                attackDamage.addTransientModifier(new AttributeModifier(
                        GUARDING_DAMAGE_MODIFIER_ID,
                        (damagePercent - 100) / 100.0D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        return true;
    }
}
