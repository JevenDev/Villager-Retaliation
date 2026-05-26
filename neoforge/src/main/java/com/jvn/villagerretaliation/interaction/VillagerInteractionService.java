package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.VillagerRetaliationDebugItems;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.DialogueReputationEffect;
import com.jvn.villagerretaliation.dialogue.DialogueReputationService;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.VillagerStoryHintService;
import com.jvn.villagerretaliation.dialogue.GiftAdviceKind;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
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
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Equipable;
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
        ServerLevel level = player.serverLevel();
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        VillagerInteractionTracker.InteractionState interactionState = VillagerInteractionTracker.getState(level, villager, player);
        DialogueContextSnapshots contextSnapshots = dialogueContextSnapshots(level, villager);
        DialogueContext dialogueContext = createDialogueContext(
                level,
                player,
                villager,
                interactionState,
                reputation.value(),
                reputation.level(),
                contextSnapshots
        );
        DialogueDisposition mood = VillagerDialogueService.moodFor(dialogueContext);
        String greetingText = VillagerDialogueService.selectOpeningGreeting(dialogueContext);
        List<DialogueOptionDefinition> dialogueOptions = VillagerDialogueResources.dialogueOptions(dialogueContext, mood);
        VillagerGiftKnowledgeService.GiftKnowledgeSnapshot giftKnowledge =
                VillagerGiftKnowledgeService.knownGifts(level, player, villager.getVillagerData().getProfession());
        VillagerReputationNetworking.sendProfile(player, villager, profile);
        PacketDistributor.sendToPlayer(player, new OpenVillagerInteractionPayload(
                villager.getId(),
                "",
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                VillagerPresetNameRegistry.resolveGender(villager).serializedName(),
                villager.isBaby(),
                reputation.value(),
                reputation.level(),
                mood,
                dialogueContext.primaryMood(),
                VillagerRecruitmentService.isFollowing(villager, player),
                false,
                forceCameraTowardsVillager,
                dialogueOptions,
                giftKnowledge.likedGiftNames(),
                giftKnowledge.dislikedGiftNames(),
                contextSnapshots.familyTree(),
                contextSnapshots.relationships()
        ));
        VillagerAmbientIndicatorService.onConversationOpened(level, villager, player);
        broadcastVillagerChat(level, villager, greetingText);
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

        openForcedInteractionScreen(player, villager, dialogueOptions, forceCameraTowardsVillager);
        focusVillagerOnPlayer(villager, player);
        if (!openingText.isBlank()) {
            broadcastForcedVillagerChat(player.serverLevel(), villager, openingText);
        }
        return true;
    }

    private static void openForcedInteractionScreen(
            ServerPlayer player,
            Villager villager,
            List<DialogueOptionDefinition> dialogueOptions,
            boolean forceCameraTowardsVillager) {
        ServerLevel level = player.serverLevel();
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        DialogueContext context = createDialogueContext(level, player, villager);
        VillagerGiftKnowledgeService.GiftKnowledgeSnapshot giftKnowledge =
                VillagerGiftKnowledgeService.knownGifts(level, player, villager.getVillagerData().getProfession());
        VillagerReputationNetworking.sendProfile(player, villager, profile);
        PacketDistributor.sendToPlayer(player, new OpenVillagerInteractionPayload(
                villager.getId(),
                "",
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                VillagerPresetNameRegistry.resolveGender(villager).serializedName(),
                villager.isBaby(),
                reputation.value(),
                reputation.level(),
                VillagerDialogueService.moodFor(context),
                context.primaryMood(),
                VillagerRecruitmentService.isFollowing(villager, player),
                true,
                forceCameraTowardsVillager,
                dialogueOptions,
                giftKnowledge.likedGiftNames(),
                giftKnowledge.dislikedGiftNames(),
                VillagerSocialGraphService.familySnapshot(level, villager),
                VillagerSocialGraphService.relationshipSnapshot(level, villager)
        ));
        VillagerAmbientIndicatorService.onConversationOpened(level, villager, player);
    }

    private static String professionTranslationKey(Villager villager) {
        if (villager.isBaby()) {
            return "villagerretaliation.gui.profession.child";
        }
        return VillagerProfessionUtil.translationKey(
                villager.getVillagerData().getProfession(),
                "villagerretaliation.gui.profession.unemployed"
        );
    }

    public static void handleDialogueRequest(ServerPlayer player, int entityId, String optionId) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "interaction.unavailable");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(entityId, ""));
            sendVillagerNotice(player, villager, "interaction.conversation_ended");
            return;
        }
        if (ForcedDialogueService.handleDialogueRequest(player, villager, optionId)) {
            return;
        }
        if (VillagerConversationService.isForced(player, villager) && !ForcedDialogueService.hasSession(player, villager)) {
            VillagerConversationService.endForPlayer(player, true);
            return;
        }
        if (shouldRefuseDespisedConversation(villager, player)) {
            VillagerConversationService.endForPlayer(player, true);
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "interaction.refuse_despised");
            return;
        }
        focusVillagerOnPlayer(villager, player);

        ServerLevel level = player.serverLevel();
        VillagerInteractionTracker.InteractionState interactionState = VillagerInteractionTracker.getState(level, villager, player);
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        DialogueContext context = createDialogueContext(level, player, villager, interactionState, reputation.value(), reputation.level());
        DialogueOptionDefinition dialogueOption = VillagerDialogueResources.dialogueOption(context, optionId).orElse(null);
        if (dialogueOption == null) {
            sendVillagerNotice(player, villager, "interaction.unknown_dialogue_option");
            sendDialogueReputation(player, villager, level);
            return;
        }
        DialogueRequestType requestType = dialogueOption.requestType();
        VillagerDialogueService.DialogueResult result = selectDialogueResult(context, dialogueOption, interactionState);
        DialogueItemPayment itemPayment = dialogueOption.itemPayment();
        DialogueItemPaymentResult itemPaymentResult = DialogueItemPaymentResult.empty();
        if (!itemPayment.isEmpty()) {
            Optional<DialogueItemPaymentResult> paymentResult = executeDialogueItemPayment(player, villager, itemPayment);
            if (paymentResult.isEmpty()) {
                String failureText = resolveDialogueItemPaymentResponse(context, itemPayment.selectFailureResponse(context.random()), itemPayment.removal().replacements());
                sendDialogueReputation(player, villager, level);
                broadcastVillagerChat(level, villager, failureText);
                return;
            }
            itemPaymentResult = paymentResult.get();
        }
        DialogueReputationEffect reputationEffect = DialogueReputationService.apply(context, requestType, interactionState);
        playDialogueFeedback(level, villager, reputationEffect);
        VillagerAmbientIndicatorService.onDialogueResponse(level, villager, player, optionId, requestType, reputationEffect);
        String responseText = result.text();
        if (!itemPayment.isEmpty()) {
            String successText = itemPayment.selectSuccessResponse(context.random());
            if (!successText.isBlank()) {
                responseText = resolveDialogueItemPaymentResponse(context, successText, itemPaymentResult.replacements());
            }
        }
        if (reputationEffect.responseOverride() != null) {
            responseText = reputationEffect.responseOverride();
        }
        responseText = VillagerDialogueResources.resolveTemplate(responseText, itemPaymentResult.replacements());
        VillagerInteractionTracker.rememberDialogue(level, villager, player, requestType, result.lineId());
        if (requestType == DialogueRequestType.COMBAT_SURVIVAL_REPORT) {
            VillagerInteractionTracker.claimUnreportedCombatSurvivalReport(level, villager, player);
        } else if (requestType == DialogueRequestType.GEAR_REPORT) {
            VillagerInteractionTracker.claimUnreportedGearReport(level, villager, player);
        } else if (requestType == DialogueRequestType.RECRUITMENT_FOLLOWUP) {
            VillagerInteractionTracker.claimUnreportedRecruitmentFollowup(level, villager, player);
        } else if (requestType == DialogueRequestType.CURED_RECOGNITION) {
            VillagerInteractionTracker.claimUnreportedCuredRecognition(level, villager, player);
        }
        sendDialogueReputation(player, villager, level, requestType, reputationEffect, dialogueOption.forceCameraTowardsVillager());
        broadcastVillagerChat(level, villager, responseText);
        if (shouldRefuseDespisedConversation(villager, player)) {
            VillagerConversationService.endForPlayer(player, true);
        }
    }

    public static void handleTradeRequest(ServerPlayer player, int entityId) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "interaction.trade_unavailable");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendVillagerNotice(player, villager, "interaction.conversation_ended");
            return;
        }
        if (shouldRefuseDespisedConversation(villager, player)) {
            VillagerConversationService.endForPlayer(player, true);
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "interaction.refuse_trade");
            return;
        }
        focusVillagerOnPlayer(villager, player);
        VillagerConversationService.endForPlayer(player, true);
        openTrading(player, villager, true);
    }

    public static void handleInventoryRequest(ServerPlayer player, int entityId) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "interaction.inventory_unavailable");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendVillagerNotice(player, villager, "interaction.conversation_ended");
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !VillagerInventoryAccess.canAccess(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.not_trusted_enough");
            return;
        }

        focusVillagerOnPlayer(villager, player);
        VillagerConversationService.endForPlayer(player, true);
        VillagerInventoryAccess.open(player, villager);
    }

    public static void handleGiftRequest(ServerPlayer player, int entityId, int inventorySlot) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_GIFTS.get()) {
            sendNotice(player, entityId, "interaction.gift_unavailable");
            return;
        }
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "interaction.gift_unavailable");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendVillagerNotice(player, villager, "interaction.conversation_ended");
            return;
        }
        if (shouldRefuseDespisedConversation(villager, player)) {
            VillagerConversationService.endForPlayer(player, true);
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "interaction.keep_distance");
            return;
        }
        if (villager.isBaby()) {
            sendVillagerNotice(player, villager, "interaction.child_refuse_gift");
            return;
        }
        if (inventorySlot < 0 || inventorySlot >= 36) {
            sendVillagerNotice(player, villager, "interaction.gift_invalid");
            return;
        }

        ItemStack selectedStack = player.getInventory().getItem(inventorySlot);
        if (selectedStack.isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.gift_empty_slot");
            return;
        }
        if (!VillagerInventoryAccess.canAddItems(villager, List.of(selectedStack.copy()))) {
            sendVillagerNotice(player, villager, "interaction.gift_inventory_full");
            return;
        }

        ServerLevel level = player.serverLevel();
        ItemStack giftedStack = player.getInventory().removeItem(inventorySlot, selectedStack.getCount());
        player.getInventory().setChanged();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        VillagerGiftPreferences.GiftPreference giftPreference = VillagerGiftPreferences.evaluate(level, villager, giftedStack);
        int reputationValue = giftPreference.reputationValue();
        VillagerGiftKnowledgeService.rememberGiftResult(level, player, profession, giftedStack, giftPreference);
        Boolean giftAdviceLikedResult = giftAdviceLikedResult(giftPreference.reaction());
        if (giftAdviceLikedResult != null) {
            VillagerInteractionTracker.markGiftAdviceResult(
                    level,
                    villager,
                    player,
                    itemId(giftedStack),
                    itemName(giftedStack),
                    VillagerGiftKnowledgeService.professionKey(profession),
                    VillagerInteractionTextUtil.professionName(profession, "villager").toLowerCase(java.util.Locale.ROOT),
                    VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                    giftAdviceLikedResult
            );
        }
        VillagerReputationManager.addGiftReputation(level, villager, player, reputationValue);
        VillagerGiftKeepsakes.storeGift(level, villager, player, giftedStack, giftPreference);
        rememberGearGift(level, villager, player, giftedStack);
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

        DialogueContext giftContext = createDialogueContext(level, player, villager);
        String responseText = giftResponseText(giftContext, giftPreference, giftedStack);
        sendDialogueReputation(player, villager, level);
        broadcastVillagerChat(level, villager, responseText);
    }

    public static void handleRecruitRequest(ServerPlayer player, int entityId, VillagerRecruitRequestPayload.Action action) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "interaction.recruit_unavailable");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendVillagerNotice(player, villager, "interaction.conversation_ended");
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
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

    private static void reduceDialogueAnnoyanceFromGift(ServerLevel level, Villager villager, ServerPlayer player, int reputationValue) {
        int divisor = VillagerRetaliationConfig.GIFT_ANNOYANCE_REDUCTION_DIVISOR.get();
        if (reputationValue <= 0 || divisor <= 0) {
            return;
        }
        int reduction = Math.max(1, reputationValue / divisor);
        VillagerInteractionTracker.reduceRepeatedDialogueUseCounts(level, villager, player, reduction);
    }

    private static String giftResponseKey(VillagerGiftPreferences.GiftPreference giftPreference) {
        String scope = giftPreference.professionSpecific() ? "profession" : "global";
        String reaction = giftPreference.reaction().name().toLowerCase(java.util.Locale.ROOT);
        return "gift_response." + scope + "." + reaction;
    }

    private static String giftResponseText(
            DialogueContext context,
            VillagerGiftPreferences.GiftPreference giftPreference,
            ItemStack giftedStack) {
        Map<String, String> replacements = Map.of(
                "gift_item", giftedStack.getHoverName().getString(),
                "item", itemName(giftedStack),
                "gift_item_id", itemId(giftedStack),
                "item_id", itemId(giftedStack)
        );
        String responseKey = giftPreference.responseKey();
        if (responseKey != null && !responseKey.isBlank()) {
            String customResponse = VillagerDialogueResources.message(context, responseKey, replacements).orElse("");
            if (!customResponse.isBlank()) {
                return customResponse;
            }
        }
        return VillagerDialogueResources.message(context, giftResponseKey(giftPreference), replacements).orElse("");
    }

    private static Boolean giftAdviceLikedResult(VillagerGiftPreferences.GiftReaction reaction) {
        return switch (reaction) {
            case LOVED, LIKED -> true;
            case DISLIKED, HATED -> false;
            case NEUTRAL -> null;
        };
    }

    private static void rememberGearGift(ServerLevel level, Villager villager, ServerPlayer player, ItemStack giftedStack) {
        String gearKind = gearKind(giftedStack);
        if (!gearKind.isBlank()) {
            VillagerInteractionTracker.rememberGearReport(level, villager, player, gearKind);
        }
    }

    private static String gearKind(ItemStack stack) {
        if (VillagerRetaliationVillagerWeapons.isUsableWeapon(stack)) {
            return "weapon";
        }
        Equipable equipable = Equipable.get(stack);
        return equipable == null ? "" : "armor";
    }

    private static void sendGiftNotice(ServerPlayer player, Villager villager, ItemStack giftedStack, int reputationValue) {
        VillagerReputationNoticeKind kind = reputationValue < 0
                ? VillagerReputationNoticeKind.GIFT_DISLIKED
                : reputationValue > 0 ? VillagerReputationNoticeKind.GIFT_LIKED : VillagerReputationNoticeKind.GIFT_NEUTRAL;
        String reaction = reputationValue < 0 ? "Disliked gift" : reputationValue > 0 ? "Liked gift" : "Accepted gift";
        String trigger = reputationValue < 0 ? "gift.disliked" : reputationValue > 0 ? "gift.liked" : "gift.neutral";
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                trigger,
                VillagerNotifications.replacements("item", itemName(giftedStack), "villager", displayName(villager)),
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

    private static void sendDialogueReputation(ServerPlayer player, Villager villager, ServerLevel level) {
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

    private static void sendDialogueReputation(
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

    private static VillagerDialogueService.DialogueResult selectDialogueResult(
            DialogueContext context,
            DialogueOptionDefinition dialogueOption,
            VillagerInteractionTracker.InteractionState interactionState) {
        DialogueRequestType requestType = dialogueOption.requestType();
        if (requestType == DialogueRequestType.GIFT_PREFERENCES) {
            return VillagerGiftKnowledgeService
                    .discoverFromGiftQuestion(context)
                    .map(discovery -> new VillagerDialogueService.DialogueResult(
                            "gift_preference_discovery",
                            giftAdviceLine(context, discovery.adviceKind(), discovery.itemName(), discovery.subject())
                    ))
                    .orElseGet(() -> new VillagerDialogueService.DialogueResult(
                            "gift_preference_known",
                            giftAdviceLine(context, GiftAdviceKind.ALREADY_KNOWN, "", "")
                    ));
        }
        if (requestType == DialogueRequestType.GIFT_ADVICE_FOLLOWUP) {
            return VillagerInteractionTracker
                    .claimUnreportedGiftAdviceResult(context.level(), context.villager(), context.player())
                    .map(report -> new VillagerDialogueService.DialogueResult(
                            "gift_advice_followup_" + (report.liked() ? "liked" : "disliked"),
                            giftAdviceFollowupLine(context, report)
                    ))
                    .orElseGet(() -> new VillagerDialogueService.DialogueResult(
                            "gift_advice_followup_missing",
                            VillagerDialogueResources.message(context, "gift_advice_followup.missing").orElse("")
                    ));
        }
        if (requestType == DialogueRequestType.MAP_REPORT) {
            return VillagerStoryHintService
                    .selectCartographerMapReport(context)
                    .orElseGet(() -> new VillagerDialogueService.DialogueResult(
                            "cartographer_map_report_missing",
                            VillagerDialogueResources.message(context, "cartographer_map_report.missing").orElse("")
                    ));
        }
        if (requestType == DialogueRequestType.STORY_HINT_REPORT) {
            return VillagerStoryHintService
                    .selectStoryHintReport(context)
                    .orElseGet(() -> new VillagerDialogueService.DialogueResult(
                            "story_hint_report_missing",
                            VillagerDialogueResources.message(context, "story_hint_report.missing").orElse("")
                    ));
        }
        if (requestType == DialogueRequestType.SHARE_STORY) {
            return VillagerStoryHintService
                    .selectSharedStory(context, dialogueOption, interactionState.recentDialogueIds())
                    .orElseGet(() -> new VillagerDialogueService.DialogueResult(
                            "share_story_missing",
                            VillagerDialogueResources.message(context, "share_story.missing").orElse("")
                    ));
        }
        return VillagerDialogueService.select(
                context,
                dialogueOption,
                interactionState.recentDialogueIds()
        );
    }

    private static String giftAdviceLine(
            DialogueContext context,
            GiftAdviceKind giftAdviceKind,
            String giftItemName,
            String giftSubject) {
        return VillagerDialogueResources.giftAdviceLine(context, giftAdviceKind, giftItemName, giftSubject)
                .orElse("");
    }

    private static String giftAdviceFollowupLine(
            DialogueContext context,
            VillagerInteractionTracker.GiftAdviceResultReport report) {
        VillagerProfession testedProfession = professionFromKey(report.testedProfessionKey());
        String professionName = report.testedProfessionName() == null || report.testedProfessionName().isBlank()
                ? VillagerInteractionTextUtil.professionName(testedProfession, "villager").toLowerCase(java.util.Locale.ROOT)
                : report.testedProfessionName();
        String alternativeGift = report.liked()
                ? ""
                : VillagerGiftKnowledgeService
                        .randomLikedGiftName(context.level(), testedProfession, report.itemId(), context.random())
                        .orElse("something useful");
        Map<String, String> replacements = Map.of(
                "gift_item", report.itemName() == null || report.itemName().isBlank() ? "that gift" : report.itemName(),
                "gift_subject", VillagerInteractionTextUtil.withIndefiniteArticle(professionName),
                "tested_villager", report.testedVillagerName() == null || report.testedVillagerName().isBlank()
                        ? "them"
                        : report.testedVillagerName(),
                "alternative_gift", alternativeGift
        );
        String key = report.liked() ? "gift_advice_followup.liked" : "gift_advice_followup.disliked";
        return VillagerDialogueResources
                .professionPriorityMessage(context, key, replacements)
                .or(() -> VillagerDialogueResources.message(context, key, replacements))
                .orElse("");
    }

    private static VillagerProfession professionFromKey(String key) {
        return VillagerProfessionUtil.parse(key).orElse(VillagerProfession.NONE);
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

    private static void sendNotice(ServerPlayer player, int entityId, String text) {
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

    private static Optional<DialogueItemPaymentResult> executeDialogueItemPayment(
            ServerPlayer player,
            Villager villager,
            DialogueItemPayment itemPayment) {
        List<ItemStack> previewStacks = itemPayment.removal().previewRemovedStacks(player);
        if (previewStacks.isEmpty()) {
            return Optional.empty();
        }

        DialogueItemTransferTarget primaryTarget = dialogueItemTransferTarget(villager, itemPayment.destination());
        Optional<DialogueItemTransferTarget> overflowTarget = Optional.ofNullable(itemPayment.overflowDestination())
                .map(destination -> dialogueItemTransferTarget(villager, destination));
        boolean primaryFits = primaryTarget.canAccept(previewStacks);
        if (itemPayment.requireSpace() && !primaryFits && overflowTarget.isEmpty()) {
            return Optional.empty();
        }
        if (overflowTarget.isPresent() && !overflowTarget.get().canAccept(previewStacks)) {
            return Optional.empty();
        }

        Optional<List<ItemStack>> removedStacks = itemPayment.removal().removeStacks(player);
        if (removedStacks.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> remainder = primaryTarget.accept(removedStacks.get());
        if (!remainder.isEmpty() && overflowTarget.isPresent()) {
            remainder = overflowTarget.get().accept(remainder);
        }
        if (itemPayment.requireSpace() && !remainder.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DialogueItemPaymentResult.from(itemPayment, removedStacks.get()));
    }

    private static DialogueItemTransferTarget dialogueItemTransferTarget(
            Villager villager,
            DialogueItemPayment.DialogueItemDestination destination) {
        return switch (destination) {
            case DISCARD -> new DialogueItemTransferTarget() {
                @Override
                public boolean canAccept(List<ItemStack> stacks) {
                    return true;
                }

                @Override
                public List<ItemStack> accept(List<ItemStack> stacks) {
                    return List.of();
                }
            };
            case VILLAGER_INVENTORY -> new DialogueItemTransferTarget() {
                @Override
                public boolean canAccept(List<ItemStack> stacks) {
                    return VillagerInventoryAccess.canAddItems(villager, stacks);
                }

                @Override
                public List<ItemStack> accept(List<ItemStack> stacks) {
                    return stacks.stream()
                            .map(stack -> VillagerInventoryAccess.addItem(villager, stack))
                            .filter(stack -> !stack.isEmpty())
                            .toList();
                }
            };
            case DROP_AT_VILLAGER -> new DialogueItemTransferTarget() {
                @Override
                public boolean canAccept(List<ItemStack> stacks) {
                    return true;
                }

                @Override
                public List<ItemStack> accept(List<ItemStack> stacks) {
                    for (ItemStack stack : stacks) {
                        villager.spawnAtLocation(stack.copy());
                    }
                    return List.of();
                }
            };
        };
    }

    private static String resolveDialogueItemPaymentResponse(
            DialogueContext context,
            String response,
            Map<String, String> replacements) {
        if (response == null || response.isBlank()) {
            return "";
        }
        return VillagerDialogueResources.resolveTemplate(response, replacements);
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

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String itemListName(List<ItemStack> stacks) {
        return stacks.stream()
                .map(VillagerInteractionService::itemName)
                .reduce((left, right) -> left + ", " + right)
                .orElse("items");
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

    private record DialogueContextSnapshots(
            VillagerFamilyTreeSnapshot familyTree,
            VillagerRelationshipSnapshot relationships,
            List<VillageEventMemory.MemoryEvent> recentEvents) {
    }

    private record DialogueItemPaymentResult(Map<String, String> replacements) {
        private static DialogueItemPaymentResult empty() {
            return new DialogueItemPaymentResult(Map.of());
        }

        private static DialogueItemPaymentResult from(DialogueItemPayment itemPayment, List<ItemStack> removedStacks) {
            Map<String, String> replacements = new HashMap<>(itemPayment.removal().replacements());
            int count = removedStacks.stream().mapToInt(ItemStack::getCount).sum();
            ItemStack representative = removedStacks.isEmpty() ? ItemStack.EMPTY : removedStacks.getFirst();
            String itemName = representative.isEmpty() ? "items" : itemName(representative.copyWithCount(1));
            String itemStack = representative.isEmpty() ? "items" : itemName(representative);
            String itemId = representative.isEmpty() ? "" : itemId(representative);
            replacements.put("given_count", Integer.toString(count));
            replacements.put("given_item_count", Integer.toString(count));
            replacements.put("given_item", itemName);
            replacements.put("given_item_id", itemId);
            replacements.put("given_stack", itemStack);
            replacements.put("given_items", itemListName(removedStacks));
            replacements.put("payment_item", itemName);
            replacements.put("payment_item_id", itemId);
            replacements.put("payment_stack", itemStack);
            return new DialogueItemPaymentResult(Map.copyOf(replacements));
        }
    }

    private interface DialogueItemTransferTarget {
        boolean canAccept(List<ItemStack> stacks);

        List<ItemStack> accept(List<ItemStack> stacks);
    }
}
