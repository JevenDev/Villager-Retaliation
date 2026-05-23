package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class VillagerDebugMaturityEmeraldItem extends Item {
    public VillagerDebugMaturityEmeraldItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand usedHand) {
        if (!(target instanceof Villager villager)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }

        if (!villager.isBaby()) {
            serverPlayer.displayClientMessage(
                    Component.literal(VillagerPresetNameRegistry.resolveDisplayName(villager).getString() + " is already an adult."),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        villager.setAge(0);
        VillagerSocialGraphSavedData.get(level).ensureProfile(level, villager);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                villager.getX(),
                villager.getY() + villager.getBbHeight() + 0.25D,
                villager.getZ(),
                10,
                0.35D,
                0.25D,
                0.35D,
                0.02D
        );
        villager.playSound(SoundEvents.VILLAGER_YES, 0.8F, 1.1F);
        serverPlayer.displayClientMessage(
                Component.literal("Matured " + VillagerPresetNameRegistry.resolveDisplayName(villager).getString() + "."),
                true
        );
        return InteractionResult.SUCCESS;
    }
}
