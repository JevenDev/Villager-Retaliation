package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.social.BreedingDecision;
import com.jvn.villagerretaliation.social.VillagerBirthService;
import com.jvn.villagerretaliation.social.VillagerBreedingPolicy;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
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
    private static final String SELECTED_PARTNER_TAG = "SelectedPartner";
    private static final String SELECTED_PARTNER_NAME_TAG = "SelectedPartnerName";

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

        SelectedVillagers selectedVillagers = selectedVillagers(stack);
        if (villager.isBaby()) {
            return tryAdopt(stack, serverPlayer, level, villager, selectedVillagers);
        }

        if (selectedVillagers == null || selectedVillagers.contains(villager.getUUID())) {
            storeSelectedVillager(stack, villager);
            serverPlayer.displayClientMessage(
                    Component.literal("Selected " + displayName(villager) + ". Right-click another adult villager."),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        if (selectedVillagers.hasAdoptionPair()) {
            storeSelectedVillager(stack, villager);
            serverPlayer.displayClientMessage(
                    Component.literal("Started a new selection with " + displayName(villager) + "."),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        Villager otherVillager = selectedAdult(level, selectedVillagers.firstId());
        if (otherVillager == null) {
            storeSelectedVillager(stack, villager);
            serverPlayer.displayClientMessage(
                    Component.literal("Previous selection was unavailable. Selected " + displayName(villager) + " instead."),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        VillagerSocialGraphSavedData socialGraph = VillagerSocialGraphSavedData.get(level);
        BreedingDecision decision = VillagerBreedingPolicy.evaluatePair(level, otherVillager, villager);
        boolean sameGender = VillagerPresetNameRegistry.resolveGender(otherVillager) == VillagerPresetNameRegistry.resolveGender(villager);
        boolean adoptionRequested = serverPlayer.isShiftKeyDown();
        if (!adoptionRequested && decision.allowed()) {
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

        if (!adoptionRequested && !sameGender) {
            serverPlayer.displayClientMessage(Component.translatable(decision.messageKey()), true);
            return InteractionResult.SUCCESS;
        }

        VillagerSocialGraphSavedData.BreedingValidation adoptionParentValidation =
                socialGraph.validateAdoptionParents(level, otherVillager, villager);
        if (!adoptionParentValidation.allowed()) {
            serverPlayer.displayClientMessage(Component.literal(adoptionParentValidation.reason()), true);
            return InteractionResult.SUCCESS;
        }

        storeSelectedPair(stack, otherVillager, villager);
        serverPlayer.displayClientMessage(
                Component.literal("Selected " + displayName(otherVillager) + " and " + displayName(villager)
                        + " for adoption. Right-click an orphan baby villager."),
                true
        );
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult tryAdopt(
            ItemStack stack,
            ServerPlayer player,
            ServerLevel level,
            Villager child,
            SelectedVillagers selectedVillagers
    ) {
        if (selectedVillagers == null || !selectedVillagers.hasAdoptionPair()) {
            player.displayClientMessage(Component.literal("Select two adult villagers, then right-click an orphan baby villager."), true);
            return InteractionResult.SUCCESS;
        }

        Villager parentA = selectedAdult(level, selectedVillagers.firstId());
        Villager parentB = selectedAdult(level, selectedVillagers.secondId());
        if (parentA == null || parentB == null) {
            clearSelection(stack);
            player.displayClientMessage(Component.literal("Adoption pair was unavailable. Selection cleared."), true);
            return InteractionResult.SUCCESS;
        }

        if (VillagerDownedService.isDowned(parentA)
                || VillagerDownedService.isDowned(parentB)
                || VillagerDownedService.isDowned(child)) {
            player.displayClientMessage(
                    Component.translatable("villagerretaliation.breeding.blocked.downed"),
                    true);
            return InteractionResult.SUCCESS;
        }

        VillagerSocialGraphSavedData socialGraph = VillagerSocialGraphSavedData.get(level);
        VillagerSocialGraphSavedData.BreedingValidation validation = socialGraph.validateAdoption(level, parentA, parentB, child);
        if (!validation.allowed()) {
            player.displayClientMessage(Component.literal(validation.reason()), true);
            return InteractionResult.SUCCESS;
        }

        socialGraph.linkAdoptiveParentsAndChild(level, parentA, parentB, child);
        VillagerReputationManager.inheritReputationFromParents(level, child, parentA, parentB);
        clearSelection(stack);

        double x = child.getX();
        double y = child.getY();
        double z = child.getZ();
        level.sendParticles(ParticleTypes.HEART, x, y + child.getBbHeight() + 0.25D, z, 9, 0.35D, 0.25D, 0.35D, 0.02D);
        parentA.playSound(SoundEvents.VILLAGER_YES, 0.8F, 0.9F + parentA.getRandom().nextFloat() * 0.2F);
        parentB.playSound(SoundEvents.VILLAGER_YES, 0.8F, 0.9F + parentB.getRandom().nextFloat() * 0.2F);
        child.playSound(SoundEvents.VILLAGER_YES, 0.8F, 1.1F + child.getRandom().nextFloat() * 0.2F);
        player.displayClientMessage(
                Component.literal(displayName(parentA) + " and " + displayName(parentB) + " adopted " + displayName(child) + "."),
                true
        );
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

        VillagerBirthService.initializeNewborn(level, parentA, parentB, child, player);
        level.sendParticles(ParticleTypes.HEART, x, y + child.getBbHeight() + 0.25D, z, 7, 0.35D, 0.25D, 0.35D, 0.02D);
        parentA.playSound(SoundEvents.VILLAGER_YES, 0.8F, 0.9F + parentA.getRandom().nextFloat() * 0.2F);
        parentB.playSound(SoundEvents.VILLAGER_YES, 0.8F, 0.9F + parentB.getRandom().nextFloat() * 0.2F);
        return true;
    }

    private static Villager selectedAdult(ServerLevel level, UUID id) {
        Entity selectedEntity = level.getEntity(id);
        if (selectedEntity instanceof Villager villager && villager.isAlive() && !villager.isBaby()) {
            return villager;
        }
        return null;
    }

    private static void storeSelectedVillager(ItemStack stack, Villager villager) {
        CompoundTag selectionTag = new CompoundTag();
        selectionTag.putUUID(SELECTED_VILLAGER_TAG, villager.getUUID());
        selectionTag.putString(SELECTED_NAME_TAG, displayName(villager));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(DEBUG_BREEDING_TAG, selectionTag));
    }

    private static void storeSelectedPair(ItemStack stack, Villager first, Villager second) {
        CompoundTag selectionTag = new CompoundTag();
        selectionTag.putUUID(SELECTED_VILLAGER_TAG, first.getUUID());
        selectionTag.putString(SELECTED_NAME_TAG, displayName(first));
        selectionTag.putUUID(SELECTED_PARTNER_TAG, second.getUUID());
        selectionTag.putString(SELECTED_PARTNER_NAME_TAG, displayName(second));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(DEBUG_BREEDING_TAG, selectionTag));
    }

    private static SelectedVillagers selectedVillagers(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(DEBUG_BREEDING_TAG)) {
            return null;
        }
        CompoundTag selectionTag = customData.copyTag().getCompound(DEBUG_BREEDING_TAG);
        if (!selectionTag.hasUUID(SELECTED_VILLAGER_TAG)) {
            return null;
        }
        UUID secondId = selectionTag.hasUUID(SELECTED_PARTNER_TAG) ? selectionTag.getUUID(SELECTED_PARTNER_TAG) : null;
        String secondName = secondId == null ? "" : selectionTag.getString(SELECTED_PARTNER_NAME_TAG);
        return new SelectedVillagers(
                selectionTag.getUUID(SELECTED_VILLAGER_TAG),
                selectionTag.getString(SELECTED_NAME_TAG),
                secondId,
                secondName
        );
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

    private record SelectedVillagers(UUID firstId, String firstName, UUID secondId, String secondName) {
        private boolean hasAdoptionPair() {
            return this.secondId != null;
        }

        private boolean contains(UUID id) {
            return this.firstId.equals(id) || (this.secondId != null && this.secondId.equals(id));
        }
    }
}
