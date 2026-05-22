package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class VillagerDebugBreedingStickItem extends Item {
    private static final String DEBUG_BREEDING_TAG = "VillagerRetaliationDebugBreeding";
    private static final String SELECTED_VILLAGER_TAG = "SelectedVillager";
    private static final String SELECTED_NAME_TAG = "SelectedName";

    public VillagerDebugBreedingStickItem(Properties properties) {
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

        if (villager.isBaby()) {
            serverPlayer.displayClientMessage(Component.literal("Debug breeding needs adult villagers."), true);
            return InteractionResult.SUCCESS;
        }

        SelectedVillager selectedVillager = selectedVillager(stack);
        if (selectedVillager == null || selectedVillager.id().equals(villager.getUUID())) {
            storeSelectedVillager(stack, villager);
            serverPlayer.displayClientMessage(
                    Component.literal("Selected " + displayName(villager) + ". Right-click another adult villager."),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        Entity selectedEntity = level.getEntity(selectedVillager.id());
        if (!(selectedEntity instanceof Villager otherVillager) || !otherVillager.isAlive() || otherVillager.isBaby()) {
            storeSelectedVillager(stack, villager);
            serverPlayer.displayClientMessage(
                    Component.literal("Previous selection was unavailable. Selected " + displayName(villager) + " instead."),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        VillagerSocialGraphSavedData socialGraph = VillagerSocialGraphSavedData.get(level);
        VillagerSocialGraphSavedData.BreedingValidation validation = socialGraph.validateBreedingPair(level, otherVillager, villager);
        if (!validation.allowed()) {
            serverPlayer.displayClientMessage(Component.literal(validation.reason()), true);
            return InteractionResult.SUCCESS;
        }

        if (spawnDebugBaby(level, serverPlayer, otherVillager, villager)) {
            clearSelection(stack);
            serverPlayer.displayClientMessage(
                    Component.literal("Debug baby created for " + displayName(otherVillager) + " and " + displayName(villager) + "."),
                    true
            );
        } else {
            serverPlayer.displayClientMessage(Component.literal("Could not create debug baby."), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean spawnDebugBaby(ServerLevel level, ServerPlayer player, Villager parentA, Villager parentB) {
        Villager child = parentA.getBreedOffspring(level, parentB);
        if (child == null) {
            return false;
        }

        double x = (parentA.getX() + parentB.getX()) * 0.5D;
        double y = Math.max(parentA.getY(), parentB.getY());
        double z = (parentA.getZ() + parentB.getZ()) * 0.5D;
        child.moveTo(x, y, z, parentA.getYRot(), 0.0F);
        child.setAge(AgeableMob.BABY_START_AGE);
        parentA.setAge(0);
        parentB.setAge(0);

        if (!level.addFreshEntity(child)) {
            return false;
        }

        VillagerSocialGraphSavedData.get(level).linkParentsAndChild(level, parentA, parentB, child);
        VillagerReputationManager.inheritReputationFromParents(level, child, parentA, parentB);
        VillageEventMemory.remember(level, VillageEventMemory.EventTag.BABY_BORN, child.blockPosition(), child, player);
        level.sendParticles(ParticleTypes.HEART, x, y + child.getBbHeight() + 0.25D, z, 7, 0.35D, 0.25D, 0.35D, 0.02D);
        parentA.playSound(SoundEvents.VILLAGER_YES, 0.8F, 0.9F + parentA.getRandom().nextFloat() * 0.2F);
        parentB.playSound(SoundEvents.VILLAGER_YES, 0.8F, 0.9F + parentB.getRandom().nextFloat() * 0.2F);
        return true;
    }

    private static void storeSelectedVillager(ItemStack stack, Villager villager) {
        CompoundTag selectionTag = new CompoundTag();
        selectionTag.putUUID(SELECTED_VILLAGER_TAG, villager.getUUID());
        selectionTag.putString(SELECTED_NAME_TAG, displayName(villager));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(DEBUG_BREEDING_TAG, selectionTag));
    }

    private static SelectedVillager selectedVillager(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(DEBUG_BREEDING_TAG)) {
            return null;
        }
        CompoundTag selectionTag = customData.copyTag().getCompound(DEBUG_BREEDING_TAG);
        if (!selectionTag.hasUUID(SELECTED_VILLAGER_TAG)) {
            return null;
        }
        return new SelectedVillager(selectionTag.getUUID(SELECTED_VILLAGER_TAG), selectionTag.getString(SELECTED_NAME_TAG));
    }

    private static void clearSelection(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(DEBUG_BREEDING_TAG)) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        tag.remove(DEBUG_BREEDING_TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static String displayName(Villager villager) {
        return VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
    }

    private record SelectedVillager(UUID id, String name) {
    }
}
