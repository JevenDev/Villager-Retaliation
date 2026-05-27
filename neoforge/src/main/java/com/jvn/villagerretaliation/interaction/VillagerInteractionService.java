package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.VillagerRetaliationDebugItems;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.DialogueReputationEffect;
import com.jvn.villagerretaliation.dialogue.DialogueReputationService;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipSnapshot;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.trade.VillagerTradeRefreshService;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
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
                && shouldStayConversable(player, villager);
    }

    public static boolean shouldHandleSleepingInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && !shouldBypassInteractionScreen(player.getItemInHand(hand))
                && villager.isSleeping()
                && shouldStayConversable(player, villager);
    }

    public static InteractionResult handleVillagerRightClick(Villager villager, ServerPlayer player) {
        if (villager.isSleeping()) {
            return handleSleepingVillagerInteraction(villager, player);
        }

        if (VillagerRecruitmentService.isFollowing(villager, player)) {
            VillagerRecruitmentService.stopFollowing(player.serverLevel(), villager, player);
            VillagerRecruitmentService.sendNoLongerFollowingNotice(player, villager);
            focusVillagerOnPlayer(villager, player);
            sendVillagerNotice(player, villager, "interaction.follow_stay");
            return InteractionResult.SUCCESS;
        }

        if (shouldRefuseDespisedConversation(villager, player)) {
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "interaction.refuse_despised");
            return InteractionResult.FAIL;
        }

        if (!villager.isBaby()
                && player.isShiftKeyDown()
                && VillagerRetaliationConfig.SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN.get()) {
            return openTrading(player, villager, true);
        }

        if (VillagerRetaliationHandler.isHostileTowards(villager, player)) {
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "interaction.refuse_angry");
            return InteractionResult.FAIL;
        }

        if (!VillagerConversationService.start(player, villager)) {
            sendVillagerNotice(player, villager, "interaction.busy");
            return InteractionResult.FAIL;
        }
        openInteractionScreen(player, villager);
        focusVillagerOnPlayer(villager, player);
        return InteractionResult.SUCCESS;
    }

    public static void openInteractionScreen(ServerPlayer player, Villager villager) {
        openInteractionScreen(player, villager, false);
    }

    public static void openInteractionScreen(ServerPlayer player, Villager villager, boolean forceCameraTowardsVillager) {
        VillagerInteractionScreenOpener.openNormal(player, villager, forceCameraTowardsVillager);
    }

    public static boolean openForcedDialogue(
            ServerPlayer player,
            Villager villager,
            String openingText,
            List<DialogueOptionDefinition> dialogueOptions) {
        return openForcedDialogue(player, villager, openingText, dialogueOptions, forceCameraTowardsVillager(dialogueOptions));
    }

    public static boolean openForcedDialogue(
            ServerPlayer player,
            Villager villager,
            String openingText,
            List<DialogueOptionDefinition> dialogueOptions,
            boolean forceCameraTowardsVillager) {
        if (!VillagerConversationService.startForced(player, villager)) {
            return false;
        }

        VillagerInteractionScreenOpener.openForced(player, villager, dialogueOptions, forceCameraTowardsVillager);
        focusVillagerOnPlayer(villager, player);
        if (!openingText.isBlank()) {
            broadcastForcedVillagerChat(player.serverLevel(), villager, openingText);
        }
        return true;
    }

    public static void handleDialogueRequest(ServerPlayer player, int entityId, String optionId) {
        VillagerDialogueRequestHandler.handle(player, entityId, optionId);
    }

    public static void handleTradeRequest(ServerPlayer player, int entityId) {
        Optional<InteractionTargetContext> target = InteractionRequestValidator.requireTradeConversation(player, entityId);
        if (target.isEmpty()) {
            return;
        }
        InteractionTargetContext contextTarget = target.get();
        Villager villager = contextTarget.villager();
        if (shouldRefuseDespisedConversation(villager, player)) {
            InteractionRequestValidator.endConversationWithRefusal(contextTarget, "interaction.refuse_trade");
            return;
        }
        focusVillagerOnPlayer(villager, player);
        VillagerConversationService.endForPlayer(player, true);
        openTrading(player, villager, true);
    }

    public static void handleTradeRefreshRequest(ServerPlayer player, int entityId, int offerIndex) {
        VillagerTradeRefreshService.handleRequest(player, entityId, offerIndex);
    }

    public static void handleInventoryRequest(ServerPlayer player, int entityId) {
        Optional<InteractionTargetContext> target = InteractionRequestValidator.requireInventoryConversation(player, entityId);
        if (target.isEmpty()) {
            return;
        }
        InteractionTargetContext contextTarget = target.get();
        Villager villager = contextTarget.villager();
        ServerLevel level = contextTarget.level();
        if (!VillagerInventoryAccess.canAccess(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.not_trusted_enough");
            return;
        }

        focusVillagerOnPlayer(villager, player);
        VillagerConversationService.endForPlayer(player, true);
        VillagerInventoryAccess.open(player, villager);
    }

    public static void handleGiftRequest(ServerPlayer player, int entityId, int inventorySlot) {
        VillagerGiftRequestHandler.handle(player, entityId, inventorySlot);
    }

    public static void handleRecruitRequest(ServerPlayer player, int entityId, VillagerRecruitRequestPayload.Action action) {
        Optional<InteractionTargetContext> target = InteractionRequestValidator.requireRecruitConversation(player, entityId);
        if (target.isEmpty()) {
            return;
        }
        InteractionTargetContext contextTarget = target.get();
        Villager villager = contextTarget.villager();
        ServerLevel level = contextTarget.level();
        if (!VillagerRecruitmentService.canRecruit(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.not_trusted_enough");
            return;
        }

        focusVillagerOnPlayer(villager, player);
        if (action == VillagerRecruitRequestPayload.Action.HIRE) {
            sendVillagerNotice(player, villager, "interaction.hire_unavailable");
            return;
        }

        boolean nowFollowing = VillagerRecruitmentService.toggleFollow(level, villager, player);
        String responseText = message(createDialogueContext(level, player, villager), nowFollowing ? "interaction.follow_start" : "interaction.follow_stay");
        sendVillagerNotice(player, villager, responseText);
        PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(villager.getId(), responseText));
        VillagerConversationService.endForPlayer(player, false);
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

    public static void handleProfileRequest(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof AbstractVillager villager) || !villager.isAlive()) {
            return;
        }

        double maxDistance = Math.max(
                VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get(),
                VillagerRetaliationConfig.WITNESS_RADIUS.get()
        );
        if (player.distanceToSqr(villager) > maxDistance * maxDistance) {
            return;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(player.serverLevel(), villager);
        VillagerReputationNetworking.sendProfile(player, villager, profile);
    }

    public static void handleConversationEndRequest(ServerPlayer player, int entityId) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null || !VillagerConversationService.validate(player, villager)) {
            ForcedDialogueService.endForPlayer(player);
            VillagerConversationService.endForPlayer(player, true);
            return;
        }
        if (ForcedDialogueService.handleLeaveRequest(player, villager, true)) {
            return;
        }
        ForcedDialogueService.endForPlayer(player);

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
        VillagerAmbientIndicatorService.onConversationClosed(level, villager, player);
        broadcastVillagerChat(level, villager, goodbyeText);
        PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(villager.getId(), goodbyeText));
        VillagerConversationService.endForPlayer(player, false);
    }

    public static DialogueContext createDialogueContext(ServerLevel level, ServerPlayer player, Villager villager) {
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        return createDialogueContext(
                level,
                player,
                villager,
                VillagerInteractionTracker.getState(level, villager, player),
                reputation.value(),
                reputation.level()
        );
    }

    private static DialogueContext createDialogueContext(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            VillagerInteractionTracker.InteractionState interactionState,
            int reputation,
            VillagerReputationLevel reputationLevel) {
        return createDialogueContext(
                level,
                player,
                villager,
                interactionState,
                reputation,
                reputationLevel,
                dialogueContextSnapshots(level, villager)
        );
    }

    private static DialogueContext createDialogueContext(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            VillagerInteractionTracker.InteractionState interactionState,
            int reputation,
            VillagerReputationLevel reputationLevel,
            DialogueContextSnapshots contextSnapshots) {
        VillagerInteractionTracker.ContextReports reports = VillagerInteractionTracker.contextReports(level, villager, player);
        return new DialogueContext(
                level,
                player,
                villager,
                villager.getVillagerData().getProfession(),
                reputation,
                reputationLevel,
                VillagerProfileManager.getOrCreateProfile(level, villager),
                VillagerMoodService.mood(level, villager),
                interactionState.firstConversation(),
                interactionState.firstVillageInteraction(),
                weatherState(level, villager),
                timeOfDay(level),
                interactionState.lastPositiveDialogueReputationGameTime(),
                interactionState.lastNegativeDialogueReputationGameTime(),
                interactionState.lastJokeReputationGameTime(),
                interactionState.lastApologyDialogueGameTime(),
                interactionState.lastVillageDefenseReportGameTime(),
                interactionState.badFirstImpression(),
                interactionState.lastBrokenBedGameTime(),
                interactionState.lastDirectHitGameTime(),
                interactionState.lastDirectHitWeapon(),
                reports.cartographerMapReport(),
                reports.storyHintReport(),
                reports.shareableStoryReport(),
                reports.combatSurvivalReport(),
                reports.gearReport(),
                reports.recruitmentFollowupReport(),
                reports.curedRecognitionReport(),
                reports.recruitmentMemory(),
                reports.giftAdviceResultReport(),
                contextSnapshots.familyTree(),
                contextSnapshots.relationships(),
                contextSnapshots.recentEvents(),
                villager.getRandom(),
                VillagerLocale.locale(player)
        );
    }

    private static DialogueContextSnapshots dialogueContextSnapshots(ServerLevel level, Villager villager) {
        return new DialogueContextSnapshots(
                VillagerSocialGraphService.familySnapshot(level, villager),
                VillagerSocialGraphService.relationshipSnapshot(level, villager),
                VillageEventMemory.recentForVillage(level, villager)
        );
    }

    private static ReputationSnapshot reputationSnapshot(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        return new ReputationSnapshot(reputation.value(), reputation.level());
    }

    static void sendDialogueReputation(ServerPlayer player, Villager villager, ServerLevel level) {
        sendDialogueReputation(player, villager, level, null, null, false);
    }

    public static void sendForcedDialogueReputation(
            ServerPlayer player,
            Villager villager,
            List<DialogueOptionDefinition> dialogueOptions) {
        sendForcedDialogueReputation(player, villager, dialogueOptions, forceCameraTowardsVillager(dialogueOptions));
    }

    public static void sendForcedDialogueReputation(
            ServerPlayer player,
            Villager villager,
            List<DialogueOptionDefinition> dialogueOptions,
            boolean forceCameraTowardsVillager) {
        ServerLevel level = player.serverLevel();
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        DialogueContext context = createDialogueContext(level, player, villager);
        VillagerGiftKnowledgeService.GiftKnowledgeSnapshot giftKnowledge =
                VillagerGiftKnowledgeService.knownGifts(level, player, villager.getVillagerData().getProfession());
        PacketDistributor.sendToPlayer(player, new VillagerDialogueResponsePayload(
                villager.getId(),
                reputation.value(),
                reputation.level(),
                VillagerDialogueService.moodFor(context),
                context.primaryMood(),
                forceCameraTowardsVillager,
                dialogueOptions,
                giftKnowledge.likedGiftNames(),
                giftKnowledge.dislikedGiftNames()
        ));
    }

    static void sendDialogueReputation(
            ServerPlayer player,
            Villager villager,
            ServerLevel level,
            DialogueRequestType requestType,
            DialogueReputationEffect reputationEffect,
            boolean forceCameraTowardsVillager) {
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        DialogueContext context = createDialogueContext(
                level,
                player,
                villager,
                VillagerInteractionTracker.getState(level, villager, player),
                reputation.value(),
                reputation.level()
        );
        DialogueDisposition mood = requestType == null || reputationEffect == null
                ? VillagerDialogueService.moodFor(context)
                : VillagerDialogueService.moodFor(context, requestType, reputationEffect);
        java.util.List<DialogueOptionDefinition> dialogueOptions = VillagerDialogueResources.dialogueOptions(context, mood);
        VillagerGiftKnowledgeService.GiftKnowledgeSnapshot giftKnowledge =
                VillagerGiftKnowledgeService.knownGifts(level, player, villager.getVillagerData().getProfession());
        PacketDistributor.sendToPlayer(player, new VillagerDialogueResponsePayload(
                villager.getId(),
                reputation.value(),
                reputation.level(),
                mood,
                context.primaryMood(),
                forceCameraTowardsVillager,
                dialogueOptions,
                giftKnowledge.likedGiftNames(),
                giftKnowledge.dislikedGiftNames()
        ));
    }

    private static boolean forceCameraTowardsVillager(List<DialogueOptionDefinition> dialogueOptions) {
        return dialogueOptions.stream().anyMatch(DialogueOptionDefinition::forceCameraTowardsVillager);
    }

    private static String message(DialogueContext context, String key) {
        return VillagerDialogueResources.message(context, key).orElse("");
    }

    public static InteractionResult openTrading(ServerPlayer player, Villager villager, boolean sendFailureMessage) {
        if (!canUseInteractionSystem(player, villager)) {
            if (sendFailureMessage) {
                sendVillagerNotice(player, villager, "interaction.trade_unavailable");
            }
            return InteractionResult.FAIL;
        }
        if (villager.isBaby()) {
            if (sendFailureMessage) {
                sendVillagerNotice(player, villager, "interaction.child_refuse_trade");
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
                sendVillagerNotice(player, villager, "interaction.refuse_trade");
            }
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            return InteractionResult.FAIL;
        }
        if (villager.level() instanceof ServerLevel level) {
            VillagerTradeRefreshService.ReadyRefreshResult readyRefreshes =
                    VillagerTradeRefreshService.applyReadyRefreshesDetailed(level, villager, player);
            VillagerTradeRefreshService.sendState(player, villager);
            if (readyRefreshes.hasPlayerReadyTrades()) {
                ForcedDialogueService.openTradeRefreshReadyDialogue(level, villager, player, readyRefreshes);
                return InteractionResult.CONSUME;
            }
        }
        if (villager.getOffers().isEmpty()) {
            villager.setUnhappyCounter(40);
            if (sendFailureMessage) {
                sendVillagerNotice(player, villager, "interaction.no_trades");
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
        return canUseInteractionTarget(player, villager, false, VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get());
    }

    public static boolean canUseForcedInteractionSystem(ServerPlayer player, Villager villager) {
        return canUseInteractionTarget(player, villager, false, VillagerRetaliationConfig.MAX_FORCED_DIALOGUE_DISTANCE.get());
    }

    public static boolean shouldStayConversable(ServerPlayer player, Villager villager) {
        return shouldStayConversable(player, villager, false);
    }

    public static boolean shouldStayConversable(ServerPlayer player, Villager villager, boolean forced) {
        double maxDistance = forced
                ? VillagerRetaliationConfig.MAX_FORCED_DIALOGUE_DISTANCE.get()
                : VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        return canUseInteractionTarget(player, villager, true, maxDistance);
    }

    private static boolean shouldBypassInteractionScreen(ItemStack stack) {
        return stack.is(Items.VILLAGER_SPAWN_EGG)
                || stack.is(Items.NAME_TAG)
                || VillagerRetaliationDebugItems.isDebugVillagerTool(stack.getItem());
    }

    private static boolean canUseInteractionTarget(ServerPlayer player, Villager villager, boolean allowSleeping, double maxDistance) {
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

    private static boolean shouldRefuseDespisedConversation(Villager villager, ServerPlayer player) {
        return VillagerAggressionPolicy.shouldAttackOnSight(villager, player);
    }

    public static void sendNotice(ServerPlayer player, int entityId, String text) {
        String resolvedText = VillagerDialogueResources
                .globalMessage(player.getServer(), player.getRandom(), text, VillagerLocale.locale(player))
                .orElse(text);
        PacketDistributor.sendToPlayer(player, new VillagerInteractionNoticePayload(entityId, resolvedText, ""));
    }

    public static void sendVillagerNotice(ServerPlayer player, Villager villager, String text) {
        String resolvedText = text;
        if (villager.level() instanceof ServerLevel level) {
            resolvedText = VillagerDialogueResources.message(createDialogueContext(level, player, villager), text).orElse(text);
        }
        PacketDistributor.sendToPlayer(
                player,
                new VillagerInteractionNoticePayload(villager.getId(), resolvedText, "")
        );
    }

    public static void sendVillagerNotice(ServerPlayer player, Villager villager, String text, Map<String, String> replacements) {
        String resolvedText = text;
        if (villager.level() instanceof ServerLevel level) {
            resolvedText = VillagerDialogueResources.message(createDialogueContext(level, player, villager), text, replacements).orElse(text);
        }
        PacketDistributor.sendToPlayer(
                player,
                new VillagerInteractionNoticePayload(villager.getId(), resolvedText, "")
        );
    }

    public static void sendReceivedItemNotice(ServerPlayer player, Villager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                "gift.received_item",
                VillagerNotifications.replacements("item", itemName(stack), "villager", displayName(villager)),
                "Received item: " + itemName(stack),
                VillagerReputationNoticeKind.RECEIVED_ITEM
        );
    }

    public static void sendHighReputationGiftDialogue(ServerPlayer player, Villager villager, ItemStack stack) {
        if (stack.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        DialogueContext context = createDialogueContext(level, player, villager);
        String responseText = VillagerDialogueResources
                .professionPriorityMessage(context, "gift_given", Map.of("gift_item", itemName(stack)))
                .orElse("");
        broadcastVillagerChat(level, villager, responseText);
    }

    public static void sendGiftTakenBackDialogue(ServerPlayer player, Villager villager, ItemStack stack, int count, boolean stolen) {
        if (stack.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        ItemStack displayedStack = stack.copyWithCount(Math.max(1, count));
        DialogueContext context = createDialogueContext(level, player, villager);
        String responseText = VillagerDialogueResources
                .message(
                        context,
                        stolen ? "gift_taken_back.stolen" : "gift_taken_back.returned",
                        Map.of("gift_item", itemName(displayedStack))
                )
                .orElse("");
        focusVillagerOnPlayer(villager, player);
        playGiftFeedback(level, villager, -1);
        broadcastVillagerChat(level, villager, responseText);
    }

    public static void sendTradePaymentTakenBackDialogue(ServerPlayer player, Villager villager, ItemStack stack, int count, boolean stolen) {
        if (stack.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        ItemStack displayedStack = stack.copyWithCount(Math.max(1, count));
        DialogueContext context = createDialogueContext(level, player, villager);
        String responseText = VillagerDialogueResources
                .message(
                        context,
                        stolen ? "trade_payment_taken_back.stolen" : "trade_payment_taken_back.returned",
                        Map.of("trade_item", itemName(displayedStack))
                )
                .orElse("");
        focusVillagerOnPlayer(villager, player);
        playGiftFeedback(level, villager, -1);
        broadcastVillagerChat(level, villager, responseText);
    }

    public static void broadcastVillagerChat(ServerLevel level, Villager villager, String text) {
        broadcastVillagerChat(level, villager, text, "");
    }

    public static void broadcastVillagerChat(ServerLevel level, Villager villager, String text, String speakerLabel) {
        broadcastVillagerChat(level, villager, text, speakerLabel, VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get());
    }

    public static void broadcastForcedVillagerChat(ServerLevel level, Villager villager, String text) {
        broadcastForcedVillagerChat(level, villager, text, "");
    }

    public static void broadcastForcedVillagerChat(ServerLevel level, Villager villager, String text, String speakerLabel) {
        broadcastVillagerChat(level, villager, text, speakerLabel, VillagerRetaliationConfig.MAX_FORCED_DIALOGUE_DISTANCE.get());
    }

    public static void broadcastForcedVillagerChat(ServerLevel level, Villager villager, String text, String speakerLabel, double radius) {
        broadcastVillagerChat(level, villager, text, speakerLabel, radius);
    }

    private static void broadcastVillagerChat(ServerLevel level, Villager villager, String text, String speakerLabel, double radius) {
        if (text == null || text.isBlank()) {
            return;
        }

        double radiusSqr = radius * radius;
        VillagerInteractionNoticePayload payload = new VillagerInteractionNoticePayload(
                villager.getId(),
                text,
                speakerLabel == null ? "" : speakerLabel
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

    public static String villagerSpeakerLabel(Villager villager) {
        String name = displayName(villager);
        if (villager.isBaby()) {
            return name;
        }
        String profession = VillagerInteractionTextUtil
                .professionName(villager.getVillagerData().getProfession(), "")
                .trim();
        return profession.isBlank() ? name : profession + " " + name;
    }

    private static String itemName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        return stack.getCount() > 1 ? stack.getCount() + "x " + name : name;
    }

    private static String displayName(Villager villager) {
        return VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
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
        broadcastVillagerChat(level, villager, message(
                createDialogueContext(level, player, villager),
                alreadyDisturbedThisNight ? "sleep.repeated_disturbance" : "sleep.disturbance"
        ));
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
        if (sleepingVillager == null || !shouldStayConversable(player, sleepingVillager)) {
            return InteractionResult.PASS;
        }
        return handleSleepingVillagerInteraction(sleepingVillager, player);
    }

    public static void handleSleepingVillagerBedBroken(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Villager sleepingVillager = findSleepingVillagerAtBed(level, pos);
        if (sleepingVillager == null || !shouldStayConversable(player, sleepingVillager)) {
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
        broadcastVillagerChat(level, sleepingVillager, message(
                createDialogueContext(level, player, sleepingVillager),
                "sleep.broken_bed"
        ));
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

    static void playDialogueFeedback(ServerLevel level, Villager villager, DialogueReputationEffect reputationEffect) {
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

    static void playGiftFeedback(ServerLevel level, Villager villager, int reputationValue) {
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

    static void focusVillagerOnPlayer(Villager villager, ServerPlayer player) {
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
    }

    private record ReputationSnapshot(int value, VillagerReputationLevel level) {
    }

    private record DialogueContextSnapshots(
            VillagerFamilyTreeSnapshot familyTree,
            VillagerRelationshipSnapshot relationships,
            List<VillageEventMemory.MemoryEvent> recentEvents) {
    }

}
