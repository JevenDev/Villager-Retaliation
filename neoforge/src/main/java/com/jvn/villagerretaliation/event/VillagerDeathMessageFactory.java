package com.jvn.villagerretaliation.event;

import com.jvn.toucanlib.util.ToucanHazardAttribution;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.Arrays;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

/** Builds villager death messages with the same combat context used for player deaths. */
public final class VillagerDeathMessageFactory {
    private VillagerDeathMessageFactory() {
    }

    public static Component create(Villager villager, DamageSource source) {
        Component vanillaMessage = attributedHazardMessage(villager, source);
        if (vanillaMessage == null) {
            vanillaMessage = villager.getCombatTracker().getDeathMessage();
        }

        return replaceVictimName(vanillaMessage, VillagerPresetNameRegistry.resolveDisplayName(villager));
    }

    private static Component attributedHazardMessage(Villager villager, DamageSource source) {
        if (source.getEntity() != null || source.getDirectEntity() != null) {
            return null;
        }

        Player hazardOwner = ToucanHazardAttribution.resolveVanillaHazardOwner(villager, source)
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .orElse(null);
        if (hazardOwner == null) {
            return null;
        }

        return Component.translatable(
                "death.attack." + source.getMsgId() + ".player",
                villager.getDisplayName(),
                hazardOwner.getDisplayName());
    }

    static Component replaceVictimName(Component vanillaMessage, Component villagerName) {
        if (!(vanillaMessage.getContents() instanceof TranslatableContents translation)) {
            return vanillaMessage;
        }

        Object[] originalArgs = translation.getArgs();
        if (originalArgs.length == 0) {
            return vanillaMessage;
        }

        Object[] args = Arrays.copyOf(originalArgs, originalArgs.length);
        args[0] = villagerName;
        MutableComponent renamed = Component.translatableWithFallback(
                        translation.getKey(), translation.getFallback(), args)
                .withStyle(vanillaMessage.getStyle());
        vanillaMessage.getSiblings().forEach(sibling -> renamed.append(sibling.copy()));
        return renamed;
    }
}
