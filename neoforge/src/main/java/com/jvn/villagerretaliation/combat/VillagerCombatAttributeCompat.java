package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.mixin.AttributeMapExtension;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

public final class VillagerCombatAttributeCompat {
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
}
