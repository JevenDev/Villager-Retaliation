package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.DialogueReputationEffect;
import com.jvn.villagerretaliation.dialogue.DialogueReputationService;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerInteractionService {
    private VillagerInteractionService() {
    }

    public static boolean shouldHandleInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && !shouldBypassInteractionScreen(player.getItemInHand(hand))
                && canUseInteractionTarget(player, villager, true);
    }

    public static boolean shouldHandleSleepingInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && !shouldBypassInteractionScreen(player.getItemInHand(hand))
                && villager.isSleeping()
                && canUseInteractionTarget(player, villager, true);
    }

    public static InteractionResult handleVillagerRightClick(Villager villager, ServerPlayer player) {
        if (villager.isSleeping()) {
            return handleSleepingVillagerInteraction(villager, player);
        }

        if (!villager.isBaby()
                && player.isShiftKeyDown()
                && VillagerRetaliationConfig.SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN.get()) {
            return openTrading(player, villager, true);
        }

        if (VillagerRetaliationHandler.isHostileTowards(villager, player)) {
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "I'm too angry to talk.");
            return InteractionResult.FAIL;
        }

        if (!VillagerConversationService.start(player, villager)) {
            sendVillagerNotice(player, villager, "I'm busy right now.");
            return InteractionResult.FAIL;
        }
        openInteractionScreen(player, villager);
        focusVillagerOnPlayer(villager, player);
        return InteractionResult.SUCCESS;
    }

    public static void openInteractionScreen(ServerPlayer player, Villager villager) {
        ServerLevel level = player.serverLevel();
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        String greetingText = VillagerDialogueService.selectOpeningGreeting(createDialogueContext(
                level,
                player,
                villager,
                VillagerInteractionTracker.getState(level, villager, player),
                reputation.value(),
                reputation.level()
        ));
        PacketDistributor.sendToPlayer(player, new OpenVillagerInteractionPayload(
                villager.getId(),
                VillagerPresetNameRegistry.resolveNameTranslationKey(villager),
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                villager.isBaby() ? "Child" : VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "Unemployed"),
                villager.isBaby(),
                reputation.value(),
                reputation.level()
        ));
        broadcastVillagerChat(level, villager, greetingText);
    }

    public static void handleDialogueRequest(ServerPlayer player, int entityId, DialogueRequestType requestType) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "I'm no longer available.");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendVillagerNotice(player, villager, "The conversation has ended.");
            return;
        }
        focusVillagerOnPlayer(villager, player);

        ServerLevel level = player.serverLevel();
        VillagerInteractionTracker.InteractionState interactionState = VillagerInteractionTracker.getState(level, villager, player);
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        DialogueContext context = createDialogueContext(level, player, villager, interactionState, reputation.value(), reputation.level());
        VillagerDialogueService.DialogueResult result = VillagerDialogueService.select(
                context,
                requestType,
                interactionState.recentDialogueIds()
        );
        DialogueReputationEffect reputationEffect = DialogueReputationService.apply(context, requestType, interactionState);
        playDialogueFeedback(level, villager, reputationEffect);
        VillagerAmbientIndicatorService.onDialogueResponse(villager, requestType, reputationEffect);
        String responseText = reputationEffect.responseOverride() == null ? result.text() : reputationEffect.responseOverride();
        VillagerInteractionTracker.rememberDialogue(level, villager, player, requestType, result.lineId());
        sendDialogueReputation(player, villager, level);
        broadcastVillagerChat(level, villager, responseText);
    }

    public static void handleTradeRequest(ServerPlayer player, int entityId) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "I cannot trade right now.");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendVillagerNotice(player, villager, "The conversation has ended.");
            return;
        }
        focusVillagerOnPlayer(villager, player);
        VillagerConversationService.endForPlayer(player, true);
        openTrading(player, villager, true);
    }

    public static void handleGiftRequest(ServerPlayer player, int entityId, int inventorySlot) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "I cannot accept gifts right now.");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendVillagerNotice(player, villager, "The conversation has ended.");
            return;
        }
        if (villager.isBaby()) {
            sendVillagerNotice(player, villager, "I should ask a grown-up before taking that.");
            return;
        }
        if (inventorySlot < 0 || inventorySlot >= 36) {
            sendVillagerNotice(player, villager, "I cannot accept that.");
            return;
        }

        ItemStack selectedStack = player.getInventory().getItem(inventorySlot);
        if (selectedStack.isEmpty()) {
            sendVillagerNotice(player, villager, "There is nothing there.");
            return;
        }

        ServerLevel level = player.serverLevel();
        ItemStack giftedStack = player.getInventory().removeItem(inventorySlot, selectedStack.getCount());
        player.getInventory().setChanged();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        VillagerGiftPreferences.GiftPreference giftPreference = VillagerGiftPreferences.evaluate(profession, giftedStack);
        int reputationValue = giftPreference.reputationValue();
        VillagerReputationManager.addGiftReputation(level, villager, player, reputationValue);
        VillageEventMemory.rememberGift(
                level,
                villager.blockPosition(),
                villager,
                player,
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                itemName(giftedStack),
                giftPreference.reaction(),
                reputationValue
        );
        reduceDialogueAnnoyanceFromGift(level, villager, player, reputationValue);
        sendGiftNotice(player, villager, giftedStack, reputationValue);
        focusVillagerOnPlayer(villager, player);
        playGiftFeedback(level, villager, reputationValue);
        VillagerAmbientIndicatorService.onGiftReceived(villager, reputationValue);

        String responseText = VillagerGiftPreferences.responseFor(profession, giftedStack, giftPreference);
        sendDialogueReputation(player, villager, level);
        broadcastVillagerChat(level, villager, responseText);
    }

    private static void reduceDialogueAnnoyanceFromGift(ServerLevel level, Villager villager, ServerPlayer player, int reputationValue) {
        int divisor = VillagerRetaliationConfig.GIFT_ANNOYANCE_REDUCTION_DIVISOR.get();
        if (reputationValue <= 0 || divisor <= 0) {
            return;
        }
        int reduction = Math.max(1, reputationValue / divisor);
        VillagerInteractionTracker.reduceRepeatedDialogueUseCounts(level, villager, player, reduction);
    }

    private static void sendGiftNotice(ServerPlayer player, Villager villager, ItemStack giftedStack, int reputationValue) {
        VillagerReputationNoticeKind kind = reputationValue < 0
                ? VillagerReputationNoticeKind.GIFT_DISLIKED
                : reputationValue > 0 ? VillagerReputationNoticeKind.GIFT_LIKED : VillagerReputationNoticeKind.GIFT_NEUTRAL;
        String reaction = reputationValue < 0 ? "Disliked gift" : reputationValue > 0 ? "Liked gift" : "Accepted gift";
        VillagerReputationNetworking.sendNotice(
                player,
                reaction + ": " + itemName(giftedStack),
                kind
        );
    }

    public static void handleReputationRequest(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.isBaby()) {
            return;
        }

        double maxDistance = Math.max(
                VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get(),
                VillagerRetaliationConfig.WITNESS_RADIUS.get()
        );
        if (player.distanceToSqr(villager) > maxDistance * maxDistance) {
            return;
        }

        int reputation = VillagerReputationManager.getReputation(player.serverLevel(), villager, player.getUUID());
        VillagerReputationNetworking.sendReputation(player, villager, reputation);
    }

    public static void handleConversationEndRequest(ServerPlayer player, int entityId) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null || !VillagerConversationService.validate(player, villager)) {
            VillagerConversationService.endForPlayer(player, true);
            return;
        }

        ServerLevel level = player.serverLevel();
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        String goodbyeText = VillagerDialogueService.selectClosingGoodbye(createDialogueContext(
                level,
                player,
                villager,
                VillagerInteractionTracker.getState(level, villager, player),
                reputation.value(),
                reputation.level()
        ));
        PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(villager.getId(), goodbyeText));
        broadcastVillagerChat(level, villager, goodbyeText);
        VillagerConversationService.endForPlayer(player, false);
    }

    private static DialogueContext createDialogueContext(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            VillagerInteractionTracker.InteractionState interactionState,
            int reputation,
            VillagerReputationLevel reputationLevel) {
        return new DialogueContext(
                level,
                player,
                villager,
                villager.getVillagerData().getProfession(),
                reputation,
                reputationLevel,
                interactionState.firstConversation(),
                weatherState(level, villager),
                timeOfDay(level),
                interactionState.lastBrokenBedGameTime(),
                interactionState.lastDirectHitGameTime(),
                interactionState.lastDirectHitWeapon(),
                VillageEventMemory.recentNear(level, villager.blockPosition()),
                villager.getRandom()
        );
    }

    private static ReputationSnapshot reputationSnapshot(ServerLevel level, Villager villager, ServerPlayer player) {
        int reputation = VillagerReputationManager.getReputation(level, villager, player.getUUID());
        return new ReputationSnapshot(reputation, VillagerReputationLevel.fromReputation(reputation));
    }

    private static void sendDialogueReputation(ServerPlayer player, Villager villager, ServerLevel level) {
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        PacketDistributor.sendToPlayer(player, new VillagerDialogueResponsePayload(
                villager.getId(),
                reputation.value(),
                reputation.level()
        ));
    }

    public static InteractionResult openTrading(ServerPlayer player, Villager villager, boolean sendFailureMessage) {
        if (!canUseInteractionSystem(player, villager)) {
            if (sendFailureMessage) {
                sendVillagerNotice(player, villager, "I cannot trade right now.");
            }
            return InteractionResult.FAIL;
        }
        if (villager.isBaby()) {
            if (sendFailureMessage) {
                sendVillagerNotice(player, villager, "I do not know how to trade yet.");
            }
            return InteractionResult.FAIL;
        }
        if (VillagerRetaliationHandler.blockTradingIfHostile(villager, player)) {
            if (villager.level() instanceof ServerLevel level
                    && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                    <= VillagerReputationLevel.HOSTILE.trustRank()) {
                VillagerReputationAdvancements.onTradeRefusedDueToLowReputation(player);
            }
            if (sendFailureMessage) {
                sendVillagerNotice(player, villager, "I refuse to trade with you.");
            }
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            return InteractionResult.FAIL;
        }
        if (villager.getOffers().isEmpty()) {
            villager.setUnhappyCounter(40);
            if (sendFailureMessage) {
                sendVillagerNotice(player, villager, "I have no trades available.");
            }
            return InteractionResult.CONSUME;
        }

        return villager.mobInteract(player, InteractionHand.MAIN_HAND);
    }

    private static Villager resolveVillager(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager) || !canUseInteractionSystem(player, villager)) {
            return null;
        }
        return villager;
    }

    public static boolean canUseInteractionSystem(ServerPlayer player, Villager villager) {
        return canUseInteractionTarget(player, villager, false);
    }

    public static boolean shouldStayConversable(ServerPlayer player, Villager villager) {
        return canUseInteractionTarget(player, villager, true);
    }

    private static boolean shouldBypassInteractionScreen(ItemStack stack) {
        return stack.is(Items.VILLAGER_SPAWN_EGG) || stack.is(Items.NAME_TAG);
    }

    private static boolean canUseInteractionTarget(ServerPlayer player, Villager villager, boolean allowSleeping) {
        double maxDistance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        return villager.isAlive()
                && (allowSleeping || !villager.isSleeping())
                && !villager.isTrading()
                && !isCombatBusy(villager)
                && !VillagerRetaliationHandler.isHostileTowards(villager, player)
                && player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(villager) <= maxDistance * maxDistance;
    }

    private static boolean isCombatBusy(Villager villager) {
        return villager.getTarget() != null || villager.getLastHurtByMob() != null;
    }

    private static void sendNotice(ServerPlayer player, int entityId, String text) {
        PacketDistributor.sendToPlayer(player, new VillagerInteractionNoticePayload(entityId, text, ""));
    }

    private static void sendVillagerNotice(ServerPlayer player, Villager villager, String text) {
        PacketDistributor.sendToPlayer(
                player,
                new VillagerInteractionNoticePayload(villager.getId(), text, villagerSpeakerLabel(villager))
        );
    }

    public static void sendReceivedItemNotice(ServerPlayer player, Villager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        VillagerReputationNetworking.sendNotice(
                player,
                "Received item: " + itemName(stack),
                VillagerReputationNoticeKind.RECEIVED_ITEM
        );
    }

    private static void broadcastVillagerChat(ServerLevel level, Villager villager, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        double radius = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        double radiusSqr = radius * radius;
        VillagerInteractionNoticePayload payload = new VillagerInteractionNoticePayload(
                villager.getId(),
                text,
                villagerSpeakerLabel(villager)
        );
        for (ServerPlayer nearbyPlayer : level.players()) {
            if (!nearbyPlayer.isAlive()
                    || nearbyPlayer.isSpectator()
                    || nearbyPlayer.distanceToSqr(villager) > radiusSqr) {
                continue;
            }
            PacketDistributor.sendToPlayer(nearbyPlayer, payload);
        }
    }

    private static String itemName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        return stack.getCount() > 1 ? stack.getCount() + "x " + name : name;
    }

    private static String villagerSpeakerLabel(Villager villager) {
        String resolvedName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        if (villager.isBaby()) {
            return "Child " + resolvedName;
        }
        String profession = VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "Unemployed");
        if (profession == null || profession.isBlank() || profession.equals("Villager")) {
            return resolvedName;
        }
        return profession + " " + resolvedName;
    }

    private static DialogueContext.WeatherState weatherState(ServerLevel level, Villager villager) {
        if (level.isThundering()) {
            return DialogueContext.WeatherState.THUNDER;
        }
        if (level.isRainingAt(villager.blockPosition())) {
            return DialogueContext.WeatherState.RAIN;
        }
        return DialogueContext.WeatherState.CLEAR;
    }

    private static DialogueContext.TimeOfDay timeOfDay(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime < 6000L) {
            return DialogueContext.TimeOfDay.MORNING;
        }
        if (dayTime < 12000L) {
            return DialogueContext.TimeOfDay.AFTERNOON;
        }
        if (dayTime < 14000L) {
            return DialogueContext.TimeOfDay.EVENING;
        }
        return DialogueContext.TimeOfDay.NIGHT;
    }

    public static InteractionResult handleSleepingVillagerInteraction(Villager villager, ServerPlayer player) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }
        long night = level.getDayTime() / 24000L;
        boolean alreadyDisturbedThisNight = VillagerInteractionTracker.hasDisturbedSleepThisNight(level, villager, player, night);
        if (!alreadyDisturbedThisNight) {
            VillagerInteractionTracker.rememberSleepDisturbance(level, villager, player, night);
            int reputationLoss = VillagerRetaliationConfig.SLEEPING_VILLAGER_BOTHER_REPUTATION_LOSS.get();
            if (reputationLoss < 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
                VillagerReputationManager.addDialogueReputation(level, villager, player, reputationLoss);
            }
            villager.stopSleeping();
        }
        spawnDialogueParticles(level, villager, ParticleTypes.ANGRY_VILLAGER, 4, 0.01D);
        villager.playSound(SoundEvents.VILLAGER_NO, 0.75F, 0.75F + villager.getRandom().nextFloat() * 0.2F);
        broadcastVillagerChat(level, villager, sleepingResponse(villager, alreadyDisturbedThisNight));
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handleSleepingVillagerBedInteraction(ServerLevel level, ServerPlayer player, BlockPos pos, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND
                || !VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                || player.getItemInHand(hand).is(Items.VILLAGER_SPAWN_EGG)
                || !level.getBlockState(pos).is(BlockTags.BEDS)) {
            return InteractionResult.PASS;
        }

        Villager sleepingVillager = findSleepingVillagerAtBed(level, pos);
        if (sleepingVillager == null || !canUseInteractionTarget(player, sleepingVillager, true)) {
            return InteractionResult.PASS;
        }
        return handleSleepingVillagerInteraction(sleepingVillager, player);
    }

    public static void handleSleepingVillagerBedBroken(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Villager sleepingVillager = findSleepingVillagerAtBed(level, pos);
        if (sleepingVillager == null || !canUseInteractionTarget(player, sleepingVillager, true)) {
            return;
        }

        VillagerInteractionTracker.rememberBrokenBed(level, sleepingVillager, player);
        int reputationLoss = VillagerRetaliationConfig.SLEEPING_VILLAGER_BED_BREAK_REPUTATION_LOSS.get();
        if (reputationLoss < 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addDialogueReputation(level, sleepingVillager, player, reputationLoss);
        }
        VillagerReputationAdvancements.onSleepingVillagerBedBroken(player);
        sleepingVillager.stopSleeping();
        spawnDialogueParticles(level, sleepingVillager, ParticleTypes.ANGRY_VILLAGER, 6, 0.01D);
        sleepingVillager.playSound(SoundEvents.VILLAGER_NO, 0.85F, 0.7F + sleepingVillager.getRandom().nextFloat() * 0.2F);
        broadcastVillagerChat(level, sleepingVillager, brokenBedResponse(sleepingVillager));
    }

    private static Villager findSleepingVillagerAtBed(ServerLevel level, BlockPos pos) {
        AABB searchArea = AABB.ofSize(pos.getCenter(), 3.0D, 2.0D, 3.0D);
        Villager sleepingVillager = null;
        double closestDistanceSqr = Double.MAX_VALUE;
        for (Villager candidate : level.getEntitiesOfClass(Villager.class, searchArea, villager -> villager.isAlive() && !villager.isBaby() && villager.isSleeping())) {
            double distanceSqr = candidate.distanceToSqr(pos.getCenter());
            if (distanceSqr < closestDistanceSqr) {
                closestDistanceSqr = distanceSqr;
                sleepingVillager = candidate;
            }
        }
        return sleepingVillager;
    }

    private static String sleepingResponse(Villager villager, boolean alreadyDisturbedThisNight) {
        if (alreadyDisturbedThisNight) {
            return switch (villager.getRandom().nextInt(4)) {
                case 0 -> "I already woke up once for you tonight.";
                case 1 -> "No. Morning. Come back in the morning.";
                case 2 -> "Stop. I am not getting up again.";
                default -> "You've bothered me enough for one night.";
            };
        }
        return switch (villager.getRandom().nextInt(4)) {
            case 0 -> "Mmph... let me sleep.";
            case 1 -> "Not now. It's the middle of the night.";
            case 2 -> "Come back when the sun is up.";
            default -> "Stop bothering me. I'm sleeping.";
        };
    }

    private static String brokenBedResponse(Villager villager) {
        return switch (villager.getRandom().nextInt(4)) {
            case 0 -> "My bed! What is wrong with you?";
            case 1 -> "You broke my bed while I was sleeping in it!";
            case 2 -> "Unbelievable. Now where am I supposed to sleep?";
            default -> "That was my bed. I will remember this.";
        };
    }

    private static void playDialogueFeedback(ServerLevel level, Villager villager, DialogueReputationEffect reputationEffect) {
        if (reputationEffect.applied() && reputationEffect.reputationDelta() > 0) {
            spawnDialogueParticles(level, villager, ParticleTypes.HAPPY_VILLAGER, 6, 0.02D);
            villager.playSound(SoundEvents.VILLAGER_YES, 0.7F, 0.9F + villager.getRandom().nextFloat() * 0.25F);
            return;
        }
        if (reputationEffect.applied() && reputationEffect.reputationDelta() < 0) {
            spawnDialogueParticles(level, villager, ParticleTypes.ANGRY_VILLAGER, 5, 0.01D);
            villager.playSound(SoundEvents.VILLAGER_NO, 0.75F, 0.85F + villager.getRandom().nextFloat() * 0.25F);
            return;
        }
        if (!reputationEffect.blockedByCooldown()) {
            villager.playSound(SoundEvents.VILLAGER_AMBIENT, 0.45F, 0.9F + villager.getRandom().nextFloat() * 0.2F);
        }
    }

    private static void playGiftFeedback(ServerLevel level, Villager villager, int reputationValue) {
        if (reputationValue > 0) {
            spawnDialogueParticles(level, villager, ParticleTypes.HAPPY_VILLAGER, 7, 0.02D);
            villager.playSound(SoundEvents.VILLAGER_YES, 0.75F, 0.9F + villager.getRandom().nextFloat() * 0.25F);
            return;
        }
        if (reputationValue < 0) {
            spawnDialogueParticles(level, villager, ParticleTypes.ANGRY_VILLAGER, 5, 0.01D);
            villager.playSound(SoundEvents.VILLAGER_NO, 0.75F, 0.85F + villager.getRandom().nextFloat() * 0.25F);
            return;
        }
        villager.playSound(SoundEvents.VILLAGER_AMBIENT, 0.45F, 0.9F + villager.getRandom().nextFloat() * 0.2F);
    }

    private static void spawnDialogueParticles(ServerLevel level, Villager villager, ParticleOptions particle, int count, double speed) {
        level.sendParticles(
                particle,
                villager.getX(),
                villager.getY() + villager.getBbHeight() + 0.25D,
                villager.getZ(),
                count,
                0.3D,
                0.2D,
                0.3D,
                speed
        );
    }

    private static void focusVillagerOnPlayer(Villager villager, ServerPlayer player) {
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
    }

    private record ReputationSnapshot(int value, VillagerReputationLevel level) {
    }
}
