package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerChatBroadcastMode;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.VillagerRetaliationDebugItems;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueReputationEffect;
import com.jvn.villagerretaliation.dialogue.normal.DialogueReputationService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.AssignmentSummaryMessage;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem.SelectedStoragePosition;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderPaymentEscrowService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import com.jvn.villagerretaliation.network.ClipboardStorageActionPayload;
import com.jvn.villagerretaliation.network.ConstructionBlueprintPlacementPayload;
import com.jvn.villagerretaliation.network.HiredBuilderOrderPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.network.VillagerMouseEasterEggPayload;
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
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerInteractionService {
    private static final UUID LOUD_LITTEN_PLAYER_ID = UUID.fromString("38492a05-b711-40d4-a39f-a3f783aa541f");
    private static final String[] MOUSE_STARE_EASTER_EGG_LINES = {
            "My eyes are up here. Unfortunately, so is yours.",
            "Blink. For both our sakes.",
            "That is the exact spot where patience goes to retire.",
            "If you are checking for thoughts, I assure you I had several.",
            "Yes, the bridge of my nose is structurally sound.",
            "You are making eye contact with the space between eye contact.",
            "I admire your focus. I fear your focus.",
            "Did a lectern teach you to stare like that?",
            "The village has decided this is weird.",
            "I was going to say something wise, but you stared it away."
    };
    private static final String EDMUNDO_EASTER_EGG_DEFINITION_ID = "villagerretaliation:easter_egg/edmundo_warning";
    private static final String BLUEPRINT_START_OPTION_ID = "construction_blueprint_start";
    private static final String BLUEPRINT_CHANGE_OPTION_ID = "construction_blueprint_change";
    private static final String BLUEPRINT_NEVERMIND_OPTION_ID = "construction_blueprint_nevermind";
    private static final String ITEM_FILTER_ALLOWLIST_OPTION_ID = "item_filter_use_allowlist";
    private static final String ITEM_FILTER_DENYLIST_OPTION_ID = "item_filter_use_denylist";
    private static final String ITEM_FILTER_NEVERMIND_OPTION_ID = "item_filter_nevermind";
    private static final String EDMUNDO_OMINOUS_FORCED_LINE =
            "Loud, I am aware of what you have done. Do not think the village has forgotten. Do not mistake this calm for mercy. Even when the roads fall silent, your name still travels in whispers after dark. Jvn has tried to silence me, it will only be a matter of time before all is revealed.";

    private VillagerInteractionService() {
    }

    public static boolean shouldHandleInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && !shouldBypassInteractionScreen(player.getItemInHand(hand))
                && canOpenInteractionTarget(player, villager, false, VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get());
    }

    public static boolean shouldSuppressClientVanillaInteraction(Villager villager, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND
                || !VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                || shouldBypassInteractionScreen(player.getItemInHand(hand))
                || villager.isTrading()
                || !villager.isAlive()
                || player.isSpectator()
                || !player.isAlive()) {
            return false;
        }
        if (!villager.isBaby()
                && player.isShiftKeyDown()
                && VillagerRetaliationConfig.SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN.get()) {
            return false;
        }
        double maxDistance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        return player.distanceToSqr(villager) <= maxDistance * maxDistance;
    }

    public static boolean shouldHandleSleepingInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && !shouldBypassInteractionScreen(player.getItemInHand(hand))
                && villager.isSleeping()
                && shouldStayConversable(player, villager);
    }

    public static boolean shouldHandleClipboardInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && VillagerRetaliationItems.isClipboard(player.getItemInHand(hand))
                && canOpenInteractionTarget(player, villager, false, VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get());
    }

    public static boolean shouldHandleConstructionBlueprintInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && ConstructionBlueprintItem.isBlueprint(player.getItemInHand(hand))
                && canOpenInteractionTarget(player, villager, false, VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get());
    }

    public static boolean shouldHandleItemFilterInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && VillagerRetaliationItems.isItemFilter(player.getItemInHand(hand))
                && canOpenInteractionTarget(player, villager, false, VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get());
    }

    public static InteractionResult handleVillagerRightClick(Villager villager, ServerPlayer player) {
        if (villager.isSleeping()) {
            return handleSleepingVillagerInteraction(villager, player);
        }

        if (player.isShiftKeyDown() && VillagerRecruitmentService.isFollowing(villager, player)) {
            VillagerRecruitmentService.stopFollowing(player.serverLevel(), villager, player);
            VillagerRecruitmentService.sendNoLongerFollowingNotice(player, villager);
            focusVillagerOnPlayer(villager, player);
            sendVillagerNotice(player, villager, "interaction.follow_stop");
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

        if (isCombatBusy(villager) && canInterruptHiredWorkForInteraction(player, villager)) {
            VillagerRetaliationHandler.suspendCombatForInteraction(villager);
        }

        if (shouldTriggerEdmundoEasterEgg(player, villager)
                && ForcedDialogueService.openSimpleForcedDialogue(
                        player,
                        villager,
                        EDMUNDO_EASTER_EGG_DEFINITION_ID,
                        EDMUNDO_OMINOUS_FORCED_LINE)) {
            return InteractionResult.CONSUME;
        }

        if (villager.level() instanceof ServerLevel level
                && HiredVillagerContractService.tryOpenJobInventoryOverflowReminder(level, villager, player)) {
            return InteractionResult.CONSUME;
        }

        if (villager.level() instanceof ServerLevel level
                && ForcedDialogueService.tryOpenTradeRefreshReadyDialogue(level, villager, player)) {
            return InteractionResult.CONSUME;
        }

        if (!VillagerConversationService.start(player, villager)) {
            sendVillagerNotice(player, villager, "interaction.busy");
            return InteractionResult.FAIL;
        }
        openInteractionScreen(player, villager);
        focusVillagerOnPlayer(villager, player);
        return InteractionResult.CONSUME;
    }

    public static InteractionResult handleClipboardVillagerRightClick(Villager villager, ServerPlayer player) {
        if (shouldRefuseDespisedConversation(villager, player)) {
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "interaction.refuse_despised");
            return InteractionResult.FAIL;
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
        VillagerInteractionScreenOpener.openClipboard(player, villager, false);
        focusVillagerOnPlayer(villager, player);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handleConstructionBlueprintVillagerRightClick(Villager villager, ServerPlayer player) {
        if (shouldRefuseDespisedConversation(villager, player)) {
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "interaction.refuse_despised");
            return InteractionResult.FAIL;
        }
        if (VillagerRetaliationHandler.isHostileTowards(villager, player)) {
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            sendVillagerNotice(player, villager, "interaction.refuse_angry");
            return InteractionResult.FAIL;
        }
        if (!(villager.level() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }
        ItemStack blueprint = player.getMainHandItem();
        Optional<ConstructionBlueprintItem.PreviewData> preview = ConstructionBlueprintItem.previewData(blueprint);
        if (preview.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (preview.get().expired()) {
            sendVillagerNotice(player, villager, "interaction.work.builder.blueprint_expired");
            return InteractionResult.FAIL;
        }
        if (preview.get().completed()) {
            sendVillagerNotice(player, villager, "interaction.work.builder.blueprint_completed");
            return InteractionResult.SUCCESS;
        }
        if (preview.get().started()) {
            sendVillagerNotice(player, villager, "interaction.work.builder.blueprint_started");
            return InteractionResult.SUCCESS;
        }
        if (!canUseBuilderBlueprintService(player, level, villager)) {
            return InteractionResult.FAIL;
        }
        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        if (BuilderTaskState.hasTask(state)) {
            sendVillagerNotice(player, villager, "interaction.work.builder.already_building", BuilderTaskState.replacements(state));
            return InteractionResult.SUCCESS;
        }
        if (!VillagerConversationService.startForced(player, villager)) {
            sendVillagerNotice(player, villager, "interaction.busy");
            return InteractionResult.FAIL;
        }
        closeActiveContainer(player);
        VillagerInteractionScreenOpener.openForced(player, villager, constructionBlueprintOptions(), true);
        focusVillagerOnPlayer(villager, player);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handleItemFilterVillagerRightClick(Villager villager, ServerPlayer player) {
        if (!(villager.level() instanceof ServerLevel level)
                || villager.isBaby()
                || !VillagerRetaliationItems.isItemFilter(player.getMainHandItem())) {
            sendVillagerNotice(player, villager, "interaction.item_filter.adult_hired_only");
            return InteractionResult.FAIL;
        }
        if (!HiredVillagerContractService.isHired(level, villager)) {
            sendVillagerNotice(player, villager, "interaction.item_filter.not_hired");
            return InteractionResult.FAIL;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.item_filter.requires_hirer");
            return InteractionResult.FAIL;
        }
        if (!VillagerConversationService.startForced(player, villager)) {
            sendVillagerNotice(player, villager, "interaction.busy");
            return InteractionResult.FAIL;
        }
        closeActiveContainer(player);
        VillagerInteractionScreenOpener.openForced(player, villager, itemFilterOptions(), true);
        sendVillagerNotice(player, villager, "interaction.item_filter.prompt");
        focusVillagerOnPlayer(villager, player);
        return InteractionResult.SUCCESS;
    }

    private static List<DialogueOptionDefinition> itemFilterOptions() {
        return List.of(
                DialogueOptionDefinition.simple(
                        ITEM_FILTER_ALLOWLIST_OPTION_ID,
                        Component.translatable("villagerretaliation.gui.item_filter.assign.allowlist").getString(),
                        DialogueRequestType.QUESTION,
                        0),
                DialogueOptionDefinition.simple(
                        ITEM_FILTER_DENYLIST_OPTION_ID,
                        Component.translatable("villagerretaliation.gui.item_filter.assign.denylist").getString(),
                        DialogueRequestType.QUESTION,
                        1),
                DialogueOptionDefinition.simple(
                        ITEM_FILTER_NEVERMIND_OPTION_ID,
                        Component.translatable("villagerretaliation.gui.item_filter.assign.nevermind").getString(),
                        DialogueRequestType.QUESTION,
                        2));
    }

    private static List<DialogueOptionDefinition> constructionBlueprintOptions() {
        return List.of(
                DialogueOptionDefinition.simple(BLUEPRINT_START_OPTION_ID, "Start job", DialogueRequestType.QUESTION, 0),
                DialogueOptionDefinition.simple(BLUEPRINT_CHANGE_OPTION_ID, "Change blueprint", DialogueRequestType.QUESTION, 1),
                DialogueOptionDefinition.simple(BLUEPRINT_NEVERMIND_OPTION_ID, "Nevermind", DialogueRequestType.QUESTION, 2));
    }

    public static void openInteractionScreen(ServerPlayer player, Villager villager) {
        openInteractionScreen(player, villager, false);
    }

    public static void openInteractionScreen(ServerPlayer player, Villager villager, boolean forceCameraTowardsVillager) {
        VillagerInteractionScreenOpener.openNormal(player, villager, forceCameraTowardsVillager);
    }

    private static boolean shouldTriggerEdmundoEasterEgg(ServerPlayer player, Villager villager) {
        String playerName = player.getGameProfile().getName();
        boolean isLoudLitten = LOUD_LITTEN_PLAYER_ID.equals(player.getUUID())
                || (playerName != null && playerName.equalsIgnoreCase("LoudLitten"));
        if (!isLoudLitten) {
            return false;
        }
        String villagerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString().trim();
        return villagerName.equalsIgnoreCase("edmundo");
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

        closeActiveContainer(player);
        VillagerInteractionScreenOpener.openForced(player, villager, dialogueOptions, forceCameraTowardsVillager);
        focusVillagerOnPlayer(villager, player);
        if (!openingText.isBlank()) {
            broadcastForcedVillagerChat(player.serverLevel(), villager, openingText);
        }
        return true;
    }

    private static void closeActiveContainer(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
    }

    public static void handleDialogueRequest(ServerPlayer player, int entityId, String optionId) {
        if (handleItemFilterDialogueRequest(player, entityId, optionId)) {
            return;
        }
        if (handleConstructionBlueprintDialogueRequest(player, entityId, optionId)) {
            return;
        }
        VillagerDialogueRequestHandler.handle(player, entityId, optionId);
    }

    private static boolean handleItemFilterDialogueRequest(ServerPlayer player, int entityId, String optionId) {
        if (!ITEM_FILTER_ALLOWLIST_OPTION_ID.equals(optionId)
                && !ITEM_FILTER_DENYLIST_OPTION_ID.equals(optionId)
                && !ITEM_FILTER_NEVERMIND_OPTION_ID.equals(optionId)) {
            return false;
        }
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager)
                || !VillagerConversationService.isForced(player, villager)
                || !canUseForcedInteractionSystem(player, villager)
                || !VillagerConversationService.validate(player, villager)
                || villager.isBaby()
                || !HiredVillagerContractService.isHired(player.serverLevel(), villager)
                || !HiredVillagerContractService.isHiredBy(player.serverLevel(), villager, player)) {
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        if (ITEM_FILTER_NEVERMIND_OPTION_ID.equals(optionId)) {
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        ItemStack heldFilter = player.getMainHandItem();
        if (!VillagerRetaliationItems.isItemFilter(heldFilter)) {
            sendVillagerNotice(player, villager, "interaction.item_filter.missing");
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }

        VillagerItemFilterData.Mode selectedMode = ITEM_FILTER_DENYLIST_OPTION_ID.equals(optionId)
                ? VillagerItemFilterData.Mode.DENYLIST
                : VillagerItemFilterData.Mode.ALLOWLIST;
        VillagerItemFilterService.AssignmentResult assignment =
                VillagerItemFilterService.assignHeldFilter(player, villager, selectedMode);
        if (!assignment.assigned()) {
            sendVillagerNotice(player, villager, "interaction.item_filter.missing");
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        String noticeKey;
        if (assignment.droppedOldFilter()) {
            noticeKey = "interaction.item_filter.replaced_dropped";
        } else if (assignment.replaced()) {
            noticeKey = "interaction.item_filter.replaced";
        } else if (selectedMode == VillagerItemFilterData.Mode.DENYLIST) {
            noticeKey = "interaction.item_filter.assigned_denylist";
        } else {
            noticeKey = "interaction.item_filter.assigned_allowlist";
        }
        sendVillagerNotice(player, villager, noticeKey);
        VillagerConversationService.endForPlayer(player, true);
        return true;
    }

    public static void handleMouseEasterEggRequest(
            ServerPlayer player,
            int entityId,
            VillagerMouseEasterEggPayload.Kind kind) {
        if (kind == null) {
            return;
        }
        Optional<InteractionTargetContext> target = InteractionRequestValidator.requireDialogueConversation(player, entityId);
        if (target.isEmpty()) {
            return;
        }
        Villager villager = target.get().villager();
        String line = MOUSE_STARE_EASTER_EGG_LINES[player.getRandom().nextInt(MOUSE_STARE_EASTER_EGG_LINES.length)];
        PacketDistributor.sendToPlayer(player, new VillagerInteractionNoticePayload(villager.getId(), line, ""));
        VillagerReputationAdvancements.onVillagerMouseStared(player);
    }

    private static boolean handleConstructionBlueprintDialogueRequest(ServerPlayer player, int entityId, String optionId) {
        if (!BLUEPRINT_START_OPTION_ID.equals(optionId)
                && !BLUEPRINT_CHANGE_OPTION_ID.equals(optionId)
                && !BLUEPRINT_NEVERMIND_OPTION_ID.equals(optionId)) {
            return false;
        }
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager)
                || !VillagerConversationService.isForced(player, villager)
                || !canUseForcedInteractionSystem(player, villager)
                || !VillagerConversationService.validate(player, villager)) {
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        if (BLUEPRINT_NEVERMIND_OPTION_ID.equals(optionId)) {
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        if (BLUEPRINT_CHANGE_OPTION_ID.equals(optionId)) {
            sendForcedDialogueReputation(player, villager, constructionBlueprintOptions(), true);
            return true;
        }
        startConstructionBlueprintJob(player, player.serverLevel(), villager);
        return true;
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

    public static void handleJobInventoryRequest(ServerPlayer player, int entityId, boolean jobInventory) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager) || !canUseInteractionSystem(player, villager)) {
            sendNotice(player, entityId, "interaction.inventory_unavailable");
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean canAccessPersonalInventory = VillagerInventoryAccess.canAccess(level, villager, player);
        boolean canAccessJobInventory = com.jvn.villagerretaliation.inventory.VillagerJobInventoryAuthorization.canAccess(
                level,
                villager,
                player);
        if (jobInventory && !canAccessJobInventory) {
            sendVillagerNotice(player, villager, "interaction.job_inventory.requires_hirer");
            return;
        }
        if (!jobInventory && !canAccessPersonalInventory) {
            sendVillagerNotice(player, villager, "interaction.not_trusted_enough");
            return;
        }

        focusVillagerOnPlayer(villager, player);
        if (player.containerMenu instanceof com.jvn.villagerretaliation.inventory.VillagerInventoryMenu menu
                && menu.villagerEntityId() == entityId) {
            menu.switchViewMode(jobInventory
                    ? com.jvn.villagerretaliation.inventory.VillagerInventoryMenu.ViewMode.JOB
                    : com.jvn.villagerretaliation.inventory.VillagerInventoryMenu.ViewMode.PERSONAL);
            menu.broadcastFullState();
            return;
        }
        if (jobInventory) {
            VillagerInventoryAccess.openJobInventory(player, villager);
        } else {
            VillagerInventoryAccess.open(player, villager);
        }
    }

    public static void handleGiftRequest(ServerPlayer player, int entityId, int inventorySlot) {
        VillagerGiftRequestHandler.handle(player, entityId, inventorySlot);
    }

    public static void handleRecruitRequest(
            ServerPlayer player,
            int entityId,
            VillagerRecruitRequestPayload.Action action,
            HiredVillagerRole selectedRole) {
        Optional<InteractionTargetContext> target = InteractionRequestValidator.requireRecruitConversation(player, entityId);
        if (target.isEmpty()) {
            return;
        }
        InteractionTargetContext contextTarget = target.get();
        Villager villager = contextTarget.villager();
        ServerLevel level = contextTarget.level();
        boolean ownsContract = HiredVillagerContractService.isHiredBy(level, villager, player);
        boolean canAdministerContract = ownsContract && isContractAdministrationAction(action);
        boolean canOpenJobInventory = action == VillagerRecruitRequestPayload.Action.OPEN_JOB_INVENTORY
                && com.jvn.villagerretaliation.inventory.VillagerJobInventoryAuthorization.canAccess(level, villager, player);
        if (!VillagerRecruitmentService.canRecruit(level, villager, player)
                && !canAdministerContract
                && !canOpenJobInventory) {
            sendVillagerNotice(player, villager, "interaction.not_trusted_enough");
            return;
        }

        focusVillagerOnPlayer(villager, player);
        if (action == VillagerRecruitRequestPayload.Action.FOLLOW
                || action == VillagerRecruitRequestPayload.Action.STAY_HERE
                || action == VillagerRecruitRequestPayload.Action.STOP_FOLLOWING
                || action == VillagerRecruitRequestPayload.Action.STOP_STAYING_HERE) {
            if (HiredVillagerContractService.isHired(level, villager)) {
                sendVillagerNotice(player, villager, "interaction.hired_contract_taken");
                return;
            }
            String responseKey;
            if (action == VillagerRecruitRequestPayload.Action.FOLLOW) {
                if (!VillagerRecruitmentService.startFollowing(level, villager, player)) {
                    sendVillagerNotice(player, villager, "interaction.follow_command_requires_owner");
                    return;
                }
                responseKey = "interaction.follow_start";
            } else if (action == VillagerRecruitRequestPayload.Action.STAY_HERE) {
                if (!VillagerRecruitmentService.canCommandStayHere(level, villager, player)) {
                    sendVillagerNotice(player, villager, "interaction.not_trusted_enough");
                    return;
                }
                if (!VillagerRecruitmentService.stayHere(level, villager, player)) {
                    sendVillagerNotice(player, villager, "interaction.follow_command_requires_owner");
                    return;
                }
                responseKey = "interaction.follow_hold_position";
            } else {
                if (!VillagerRecruitmentService.stopFollowing(level, villager, player)) {
                    sendVillagerNotice(player, villager, "interaction.follow_command_requires_owner");
                    return;
                }
                if (action == VillagerRecruitRequestPayload.Action.STOP_FOLLOWING) {
                    VillagerRecruitmentService.sendNoLongerFollowingNotice(player, villager);
                    responseKey = "interaction.follow_stop";
                } else {
                    VillagerRecruitmentService.sendMovingFreelyNotice(player, villager);
                    responseKey = "interaction.stay_stop";
                }
            }
            String responseText = message(createDialogueContext(level, player, villager), responseKey);
            sendVillagerNotice(player, villager, responseText);
            trySendToPlayer(player, new VillagerConversationEndedPayload(villager.getId(), responseText));
            VillagerConversationService.endForPlayer(player, false);
            return;
        }

        if (handleHireDurationRequest(player, level, villager, action, selectedRole)) {
            return;
        }
        if (handleHireExtensionRequest(player, level, villager, action)) {
            return;
        }
        if (handleHiredRoleRequest(player, level, villager, action)) {
            return;
        }
        if (handleHiredWorkRequest(player, level, villager, action)) {
            return;
        }

        switch (action) {
            case VIEW_CONTRACT -> sendHiredContractNotice(player, level, villager);
            case VIEW_ROLE -> sendHiredRoleNotice(player, level, villager);
            case PROMPT_END_HIRE_CONFIRMATION -> sendVillagerNotice(player, villager, "interaction.end_hire_confirmation_prompt");
            case DECLINE_END_HIRE_CONFIRMATION -> sendVillagerNotice(player, villager, "interaction.end_hire_confirmation_declined");
            case OPEN_JOB_INVENTORY -> {
                if (!com.jvn.villagerretaliation.inventory.VillagerJobInventoryAuthorization.canAccess(level, villager, player)) {
                    sendVillagerNotice(player, villager, "interaction.job_inventory.requires_hirer");
                    return;
                }
                VillagerConversationService.endForPlayer(player, true);
                VillagerInventoryAccess.openJobInventory(player, villager);
            }
            case SHOW_STORAGE -> showAssignedStorage(player, level, villager);
            case DEPOSIT_EARNINGS -> depositEarnings(player, level, villager);
            case REMOVE_STORAGE -> removeAssignedStorage(player, level, villager);
            case SHOW_PAYMENT_STORAGE -> showAssignedPaymentStorage(player, level, villager);
            case REMOVE_PAYMENT_STORAGE -> removeAssignedPaymentStorage(player, level, villager);
            case TOGGLE_AUTO_PAYMENT -> toggleAutoPayment(player, level, villager);
            case END_HIRE -> {
                if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
                    sendVillagerNotice(player, villager, "interaction.hire.end_requires_hirer");
                    return;
                }
                int refund = HiredVillagerContractService.endHireContract(level, villager, player);
                if (refund > 0) {
                    giveCurrency(player, refund);
                }
                VillagerRecruitmentService.sendFiredNotice(player, villager);
                if (refund > 0) {
                    sendVillagerNotice(
                            player,
                            villager,
                            "interaction.end_hire_confirmation_accepted_refund",
                            Map.of("refund_amount", formatCurrency(level, refund))
                    );
                } else {
                    sendVillagerNotice(player, villager, "interaction.end_hire_confirmation_accepted");
                }
                VillagerInteractionScreenOpener.refreshNormal(player, villager);
            }
            default -> sendVillagerNotice(player, villager, "interaction.recruit_unavailable");
        }
    }

    public static void handleBrewingOrderRequest(
            ServerPlayer player,
            int entityId,
            ResourceLocation itemId,
            ResourceLocation potionId,
            int amount,
            boolean continuous) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager) || !canUseInteractionSystem(player, villager)) {
            sendNotice(player, entityId, "interaction.inventory_unavailable");
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!HiredVillagerWorkService.canManageWork(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.BREWING) {
            sendVillagerNotice(player, villager, "interaction.work.brewing.requires_role_choose");
            return;
        }
        if (!continuous && amount <= 0) {
            sendVillagerNotice(player, villager, "interaction.work.brewing.choose_amount");
            return;
        }
        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        if (BrewingWorker.hasOrder(state)) {
            sendVillagerNotice(player, villager, "interaction.work.brewing.already_brewing");
            return;
        }
        Optional<HiredBrewingRecipeCatalog.BrewingRoute> route = HiredBrewingRecipeCatalog.find(level, itemId, potionId);
        if (route.isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.work.brewing.unknown_recipe");
            return;
        }
        BrewingWorker.setOrder(
                state,
                itemId,
                potionId,
                amount,
                continuous,
                HiredVillagerContractService.currentContractId(villager).orElse(null));
        String quantity = continuous ? "continuously" : Integer.toString(amount);
        HiredVillagerWorkService.stopWork(
                level,
                villager,
                HiredVillagerRole.BREWING,
                "interaction.work.brewing.order_summary",
                Map.of(
                        "amount", quantity,
                        "item", route.get().output().getHoverName().getString()));
        sendVillagerNotice(player, villager, "interaction.work.brewing.order_summary", Map.of(
                "amount", quantity,
                "item", route.get().output().getHoverName().getString()));
    }

    public static void handleBuilderOrderRequest(
            ServerPlayer player,
            int entityId,
            HiredBuilderOrderPayload.Action action,
            ResourceLocation structureId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager) || !canUseInteractionSystem(player, villager)) {
            sendNotice(player, entityId, "interaction.inventory_unavailable");
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!canUseBuilderBlueprintService(player, level, villager)) {
            return;
        }
        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        if (action != HiredBuilderOrderPayload.Action.CANCEL && BuilderTaskState.hasTask(state)) {
            sendVillagerNotice(player, villager, "interaction.work.builder.already_building", BuilderTaskState.replacements(state));
            return;
        }
        switch (action) {
            case PREVIEW -> previewBuilderOrder(player, level, villager, state, structureId);
            case CONFIRM -> confirmBuilderOrder(player, level, villager, state, structureId);
            case CANCEL -> cancelBuilderOrder(player, level, villager, state);
        }
    }

    private static boolean isContractAdministrationAction(VillagerRecruitRequestPayload.Action action) {
        return switch (action) {
            case EXTEND_ONE_DAY,
                 EXTEND_THREE_DAYS,
                 EXTEND_FIVE_DAYS,
                 EXTEND_SEVEN_DAYS,
                 EXTEND_FIFTEEN_DAYS,
                 EXTEND_THIRTY_DAYS,
                 VIEW_CONTRACT,
                 OPEN_JOB_INVENTORY,
                 SHOW_STORAGE,
                 DEPOSIT_EARNINGS,
                 REMOVE_STORAGE,
                 SHOW_PAYMENT_STORAGE,
                 REMOVE_PAYMENT_STORAGE,
                 TOGGLE_AUTO_PAYMENT,
                 PROMPT_END_HIRE_CONFIRMATION,
                 DECLINE_END_HIRE_CONFIRMATION,
                 END_HIRE,
                 VIEW_ROLE,
                 SET_ROLE_COMBAT,
                 SET_ROLE_HUNTING,
                 SET_ROLE_MINING,
                 SET_ROLE_LOGGING,
                 SET_ROLE_FARMING,
                 SET_ROLE_FISHING,
                 SET_ROLE_BREWING,
                 SET_ROLE_BUILDER,
                 SET_ROLE_ANIMAL_HANDLING,
                 SET_ROLE_NITWIT,
                 SET_ROLE_COOK,
                 SET_ROLE_SMELTER,
                 SET_ROLE_COURIER,
                 VIEW_WORK_STATUS,
                 TOGGLE_WORK_ENABLED,
                 TOGGLE_USE_ASSIGNED_SUPPLIES,
                 TOGGLE_AUTO_DEPOSIT_OUTPUTS,
                 CONFIGURE_COMBAT,
                 CONFIGURE_HUNTING,
                 CONFIGURE_MINING,
                 CONFIGURE_LOGGING,
                 CONFIGURE_FARMING,
                 CONFIGURE_FISHING,
                 CONFIGURE_BREWING,
                 CONFIGURE_BUILDER,
                 CONFIGURE_ANIMAL_HANDLING,
                 CONFIGURE_NITWIT,
                 STOP_BREWING,
                 STOP_BUILDER_BUILD -> true;
            default -> false;
        };
    }

    private static boolean canUseBuilderBlueprintService(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!VillagerRecruitmentService.canRecruit(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.not_trusted_enough");
            return false;
        }
        if (!HiredVillagerRoles.canOfferBuilderService(level, villager)) {
            sendVillagerNotice(player, villager, "interaction.work.builder.service_unavailable");
            return false;
        }
        if (HiredVillagerContractService.isHired(level, villager)
                && !HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.hired_contract_taken");
            return false;
        }
        if (HiredVillagerContractService.isHiredBy(level, villager, player)
                && !HiredVillagerContractService.isOneOffBuilderJob(level, villager)
                && HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.BUILDER) {
            sendVillagerNotice(player, villager, "interaction.work.builder.already_hired_for_other_job", Map.of(
                    "role", HiredVillagerContractService.activeRole(level, villager).label()));
            return false;
        }
        return true;
    }

    public static void handleConstructionBlueprintDeploy(ServerPlayer player, ItemStack blueprint, BlockPos targetPos) {
        Optional<ConstructionBlueprintItem.PreviewData> preview = ConstructionBlueprintItem.previewData(blueprint);
        if (preview.isEmpty()) {
            return;
        }
        handleConstructionBlueprintPlacement(player, blueprint, preview.get(), ConstructionBlueprintPlacementPayload.Action.DEPLOY_AT, 1, targetPos);
    }

    public static void handleConstructionBlueprintPlacement(
            ServerPlayer player,
            UUID jobId,
            ConstructionBlueprintPlacementPayload.Action action,
            int steps,
            BlockPos targetPos) {
        if (player == null || jobId == null || action == null) {
            return;
        }
        ItemStack blueprint = findConstructionBlueprint(player, jobId);
        Optional<ConstructionBlueprintItem.PreviewData> preview = ConstructionBlueprintItem.previewData(blueprint);
        if (preview.isEmpty()) {
            return;
        }
        if (action == ConstructionBlueprintPlacementPayload.Action.TOGGLE_LOCK) {
            ConstructionBlueprintItem.togglePlacementLocked(blueprint).ifPresent(locked ->
                    player.displayClientMessage(Component.literal(locked
                            ? "Blueprint placement locked."
                            : "Blueprint placement unlocked."), true));
            return;
        }
        if (preview.get().locked() || preview.get().placementLocked()) {
            return;
        }
        handleConstructionBlueprintPlacement(player, blueprint, preview.get(), action, steps, targetPos);
    }

    private static void handleConstructionBlueprintPlacement(
            ServerPlayer player,
            ItemStack blueprint,
            ConstructionBlueprintItem.PreviewData preview,
            ConstructionBlueprintPlacementPayload.Action action,
            int steps,
            BlockPos targetPos) {
        if (preview.locked() || preview.placementLocked()) {
            player.displayClientMessage(Component.literal("This blueprint can no longer move the site."), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(preview.dimension())) {
            player.displayClientMessage(Component.literal("Blueprint placement must be changed in its saved dimension."), true);
            return;
        }

        Optional<BuilderStructureCatalog.Entry> entry = BuilderStructureCatalog.byId(player.server, preview.structureId());
        if (entry.isEmpty()) {
            player.displayClientMessage(Component.literal("That blueprint structure is not available."), true);
            return;
        }

        PlacementUpdate update = placementUpdate(level, entry.get(), preview, action, Math.max(1, Math.min(8, steps)), targetPos);
        if (update == null || update.plan().isEmpty()) {
            player.displayClientMessage(Component.literal("That blueprint structure is not available."), true);
            return;
        }

        ConstructionBlueprintItem.updatePlacement(blueprint, level, update.plan().get(), update.origin(), update.rotation());
        player.displayClientMessage(Component.literal("Blueprint site: ")
                .append(Component.literal(HiredWorkerBrain.formatPos(update.origin()))), true);
    }

    public static void handleClipboardStorageAction(ServerPlayer player, int entityId, ClipboardStorageActionPayload.Action action) {
        Optional<InteractionTargetContext> target = InteractionRequestValidator.requireRecruitConversation(player, entityId);
        if (target.isEmpty()) {
            return;
        }

        InteractionTargetContext contextTarget = target.get();
        Villager villager = contextTarget.villager();
        ServerLevel level = contextTarget.level();
        ItemStack clipboard = findClipboard(player);
        if (clipboard.isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.clipboard.storage.hold_clipboard");
            return;
        }

        focusVillagerOnPlayer(villager, player);
        switch (action) {
            case ASSIGN -> assignClipboardStorage(player, level, villager, clipboard);
            case SHOW -> showAssignedStorage(player, level, villager);
            case REMOVE -> removeAssignedStorage(player, level, villager);
            case CLEAR_SELECTION -> {
                HiredStorageClipboardItem.clearSelection(player, clipboard);
                sendVillagerNotice(player, villager, "interaction.clipboard.selection_cleared");
            }
        }
    }

    public static void handleClipboardWorkAreaAction(ServerPlayer player, UUID villagerId, ClipboardWorkAreaActionPayload.Action action, int steps) {
        if (player == null || player.server == null || villagerId == null || action == null) {
            return;
        }
        int stepCount = Math.max(1, Math.min(5, steps));
        Optional<HiredVillagerIndex.Target> target = HiredVillagerIndex.find(player, villagerId);
        if (target.isEmpty()) {
            player.displayClientMessage(Component.literal("That worker is not available right now."), true);
            return;
        }

        HiredVillagerIndex.Target contextTarget = target.get();
        ServerLevel level = contextTarget.level();
        Villager villager = contextTarget.villager();
        if (findClipboard(player).isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.clipboard.work_area.hold_clipboard");
            return;
        }
        if (!HiredVillagerWorkService.canManageWork(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }

        if (action == ClipboardWorkAreaActionPayload.Action.SET_CENTER_HERE
                && !level.dimension().equals(player.serverLevel().dimension())) {
            sendVillagerNotice(player, villager, "interaction.clipboard.work_area.same_dimension");
            return;
        }

        if (level == player.serverLevel()) {
            focusVillagerOnPlayer(villager, player);
        }
        switch (action) {
            case SET_CENTER_HERE -> HiredVillagerWorkService.setWorkCenterHere(player, level, villager);
            case RESET_CENTER_TO_VILLAGER -> HiredVillagerWorkService.resetWorkCenterToVillager(player, level, villager);
            case APPLY_HELD_DRAFT -> HiredStorageClipboardItem.assignHeldWorkAreaDraft(player, level, villager);
            case PREVIEW -> HiredVillagerWorkService.previewWorkArea(player, level, villager);
            case CONFIGURE_ROLE -> HiredVillagerWorkService.configureRole(player, level, villager, HiredVillagerContractService.activeRole(level, villager));
            case INCREASE_HORIZONTAL_RANGE -> HiredVillagerWorkService.changeRadius(player, level, villager, 4 * stepCount);
            case DECREASE_HORIZONTAL_RANGE -> HiredVillagerWorkService.changeRadius(player, level, villager, -4 * stepCount);
            case INCREASE_VERTICAL_RANGE -> HiredVillagerWorkService.changeVerticalRadius(player, level, villager, 2 * stepCount);
            case DECREASE_VERTICAL_RANGE -> HiredVillagerWorkService.changeVerticalRadius(player, level, villager, -2 * stepCount);
            case EXPAND_NORTH -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.NORTH, stepCount);
            case EXPAND_EAST -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.EAST, stepCount);
            case EXPAND_SOUTH -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.SOUTH, stepCount);
            case EXPAND_WEST -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.WEST, stepCount);
            case CONTRACT_NORTH -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.NORTH, -stepCount);
            case CONTRACT_EAST -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.EAST, -stepCount);
            case CONTRACT_SOUTH -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.SOUTH, -stepCount);
            case CONTRACT_WEST -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.WEST, -stepCount);
            case EXPAND_UP -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.UP, stepCount);
            case EXPAND_DOWN -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.DOWN, stepCount);
            case CONTRACT_UP -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.UP, -stepCount);
            case CONTRACT_DOWN -> HiredVillagerWorkService.changeBounds(player, level, villager, Direction.DOWN, -stepCount);
        }
        if (actionPreviewsWorkArea(action)) {
            HiredVillagerWorkService.previewWorkArea(player, level, villager);
        }
    }

    private static boolean actionPreviewsWorkArea(ClipboardWorkAreaActionPayload.Action action) {
        return switch (action) {
            case SET_CENTER_HERE,
                    RESET_CENTER_TO_VILLAGER,
                    INCREASE_HORIZONTAL_RANGE,
                    DECREASE_HORIZONTAL_RANGE,
                    INCREASE_VERTICAL_RANGE,
                    DECREASE_VERTICAL_RANGE,
                    EXPAND_NORTH,
                    EXPAND_EAST,
                    EXPAND_SOUTH,
                    EXPAND_WEST,
                    CONTRACT_NORTH,
                    CONTRACT_EAST,
                    CONTRACT_SOUTH,
                    CONTRACT_WEST,
                    EXPAND_UP,
                    EXPAND_DOWN,
                    CONTRACT_UP,
                    CONTRACT_DOWN -> true;
            case APPLY_HELD_DRAFT, PREVIEW, CONFIGURE_ROLE -> false;
        };
    }

    private static boolean handleHireDurationRequest(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            VillagerRecruitRequestPayload.Action action,
            HiredVillagerRole selectedRole) {
        int days = switch (action) {
            case HIRE_ONE_DAY -> 1;
            case HIRE_THREE_DAYS -> 3;
            case HIRE_FIVE_DAYS -> 5;
            case HIRE_SEVEN_DAYS -> 7;
            case HIRE_FIFTEEN_DAYS -> 15;
            case HIRE_THIRTY_DAYS -> 30;
            default -> 0;
        };
        if (days <= 0) {
            return false;
        }
        if (HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendHiredContractNotice(player, level, villager);
            return true;
        }
        if (com.jvn.villagerretaliation.party.PartyService.getPartyForVillager(level, villager.getUUID()).isPresent()) {
            sendVillagerNotice(player, villager, "villagerretaliation.party.error.villager_already_in_party");
            return true;
        }
        if (HiredVillagerContractService.isHired(level, villager)) {
            sendVillagerNotice(player, villager, "interaction.hired_contract_taken");
            return true;
        }
        if (HiredVillagerContractService.hasForeignJobInventoryOverflow(level, villager, player)) {
            sendVillagerNotice(
                    player,
                    villager,
                    "interaction.hire_overflow_blocked",
                    HiredVillagerContractService.jobInventoryOverflowReplacements(level, villager));
            return true;
        }

        HiredVillagerRole hireRole = selectedRole == null
                ? HiredVillagerRoles.defaultRole(level, villager)
                : selectedRole;
        if (!HiredVillagerRoles.availableContractRoles(level, villager).contains(hireRole)) {
            sendVillagerNotice(
                    player,
                    villager,
                    "interaction.role_not_suitable",
                    Map.of("role", hireRole.label())
            );
            return true;
        }

        int cost = HiredVillagerContractService.getHireCost(level, villager, player, days, hireRole);
        if (countCurrency(player) < cost) {
            sendVillagerNotice(
                    player,
                    villager,
                    "interaction.hire_cost",
                    Map.of(
                            "time_remaining", formatDaysRemaining(days),
                            "contract_cost", formatCurrency(level, cost)
                    )
            );
            return true;
        }
        removeCurrency(player, cost);
        HiredVillagerContractService.startHireContract(level, villager, player, days, cost, hireRole);
        HiredVillagerWorkService.resetForNewContract(level, villager);
        VillagerRecruitmentService.sendHiredNotice(player, villager);
        sendVillagerNotice(
                player,
                villager,
                "interaction.hire_started",
                Map.of(
                        "time_remaining", formatDaysRemaining(days),
                        "contract_cost", formatCurrency(level, cost),
                        "role", hireRole.label()
                )
        );
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
        return true;
    }

    private static boolean handleHireExtensionRequest(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            VillagerRecruitRequestPayload.Action action) {
        int days = switch (action) {
            case EXTEND_ONE_DAY -> 1;
            case EXTEND_THREE_DAYS -> 3;
            case EXTEND_FIVE_DAYS -> 5;
            case EXTEND_SEVEN_DAYS -> 7;
            case EXTEND_FIFTEEN_DAYS -> 15;
            case EXTEND_THIRTY_DAYS -> 30;
            default -> 0;
        };
        if (days <= 0) {
            return false;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.hire.extend_requires_hirer");
            return true;
        }
        int extensionDays = HiredVillagerContractService.getAvailableExtensionDays(level, villager, player, days);
        if (extensionDays <= 0) {
            sendVillagerNotice(player, villager, "interaction.extend_unavailable");
            return true;
        }
        int cost = HiredVillagerContractService.getExtensionCost(level, villager, player, days);
        if (countCurrency(player) < cost) {
            sendVillagerNotice(
                    player,
                    villager,
                    "interaction.extend_cost",
                    Map.of(
                            "time_remaining", formatDaysRemaining(extensionDays),
                            "contract_cost", formatCurrency(level, cost)
                    )
            );
            return true;
        }
        if (!HiredVillagerContractService.extendHireContract(level, villager, player, days, cost)) {
            sendVillagerNotice(player, villager, "interaction.extend_unavailable");
            return true;
        }
        removeCurrency(player, cost);
        int remainingDays = HiredVillagerContractService.getRemainingHireDays(level, villager);
        sendVillagerNotice(
                player,
                villager,
                "interaction.extend_success",
                Map.of(
                        "time_remaining", formatDaysRemaining(extensionDays),
                        "contract_cost", formatCurrency(level, cost),
                        "new_time_remaining", formatDaysRemaining(remainingDays)
                )
        );
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
        return true;
    }

    private static boolean handleHiredRoleRequest(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            VillagerRecruitRequestPayload.Action action) {
        HiredVillagerRole role = switch (action) {
            case SET_ROLE_COMBAT -> HiredVillagerRole.COMBAT;
            case SET_ROLE_HUNTING -> HiredVillagerRole.HUNTING;
            case SET_ROLE_MINING -> HiredVillagerRole.MINING;
            case SET_ROLE_LOGGING -> HiredVillagerRole.LOGGING;
            case SET_ROLE_FARMING -> HiredVillagerRole.FARMING;
            case SET_ROLE_FISHING -> HiredVillagerRole.FISHING;
            case SET_ROLE_BREWING -> HiredVillagerRole.BREWING;
            case SET_ROLE_BUILDER -> HiredVillagerRole.BUILDER;
            case SET_ROLE_ANIMAL_HANDLING -> HiredVillagerRole.ANIMAL_HANDLING;
            case SET_ROLE_NITWIT -> HiredVillagerRole.NITWIT;
            case SET_ROLE_COOK -> HiredVillagerRole.COOK;
            case SET_ROLE_SMELTER -> HiredVillagerRole.SMELTER;
            case SET_ROLE_COURIER -> HiredVillagerRole.COURIER;
            default -> null;
        };
        if (role == null) {
            return false;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.role_requires_hire");
            return true;
        }
        if (!HiredVillagerContractService.setActiveRole(level, villager, role)) {
            sendVillagerNotice(
                    player,
                    villager,
                    "interaction.role_not_suitable",
                    Map.of("role", role.label())
            );
            return true;
        }
        sendVillagerNotice(
                player,
                villager,
                "interaction.role_assigned",
                Map.of("role", role.label())
        );
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
        return true;
    }

    private static boolean handleHiredWorkRequest(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            VillagerRecruitRequestPayload.Action action) {
        HiredVillagerRole configureRole = switch (action) {
            case CONFIGURE_COMBAT -> HiredVillagerRole.COMBAT;
            case CONFIGURE_HUNTING -> HiredVillagerRole.HUNTING;
            case CONFIGURE_MINING -> HiredVillagerRole.MINING;
            case CONFIGURE_LOGGING -> HiredVillagerRole.LOGGING;
            case CONFIGURE_FARMING -> HiredVillagerRole.FARMING;
            case CONFIGURE_FISHING -> HiredVillagerRole.FISHING;
            case CONFIGURE_BREWING -> HiredVillagerRole.BREWING;
            case CONFIGURE_BUILDER -> HiredVillagerRole.BUILDER;
            case CONFIGURE_ANIMAL_HANDLING -> HiredVillagerRole.ANIMAL_HANDLING;
            case CONFIGURE_NITWIT -> HiredVillagerRole.NITWIT;
            default -> null;
        };
        boolean workAction = configureRole != null
                || action == VillagerRecruitRequestPayload.Action.VIEW_WORK_STATUS
                || action == VillagerRecruitRequestPayload.Action.TOGGLE_WORK_ENABLED
                || action == VillagerRecruitRequestPayload.Action.TOGGLE_USE_ASSIGNED_SUPPLIES
                || action == VillagerRecruitRequestPayload.Action.TOGGLE_AUTO_DEPOSIT_OUTPUTS
                || action == VillagerRecruitRequestPayload.Action.STOP_BREWING
                || action == VillagerRecruitRequestPayload.Action.STOP_BUILDER_BUILD;
        if (!workAction) {
            return false;
        }
        if (!HiredVillagerWorkService.canManageWork(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return true;
        }
        if (configureRole != null && HiredVillagerContractService.activeRole(level, villager) != configureRole) {
            sendVillagerNotice(player, villager, "interaction.work.configure.requires_role", Map.of("role", configureRole.label()));
            return true;
        }
        switch (action) {
            case VIEW_WORK_STATUS -> HiredVillagerWorkService.sendStatus(player, level, villager);
            case TOGGLE_WORK_ENABLED -> HiredVillagerWorkService.toggleEnabled(player, level, villager);
            case TOGGLE_USE_ASSIGNED_SUPPLIES -> HiredVillagerWorkService.toggleAssignedSupplies(player, level, villager);
            case TOGGLE_AUTO_DEPOSIT_OUTPUTS -> HiredVillagerWorkService.toggleAutoDeposit(player, level, villager);
            case STOP_BREWING -> stopBrewingOrder(player, level, villager);
            case STOP_BUILDER_BUILD -> cancelBuilderOrder(player, level, villager, HiredVillagerWorkService.state(villager));
            default -> HiredVillagerWorkService.configureRole(player, level, villager, configureRole);
        }
        return true;
    }

    private static void previewBuilderOrder(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            ResourceLocation structureId) {
        Optional<BuilderStructureCatalog.Entry> entry = BuilderStructureCatalog.byId(player.server, structureId);
        if (entry.isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.work.builder.unknown_structure");
            return;
        }
        Optional<BuilderStructureScanner.StructurePlan> plan = BuilderStructureScanner.scan(level, entry.get(), Rotation.NONE);
        if (plan.isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.work.builder.structure_unavailable", Map.of("structure", entry.get().menuLabel()));
            return;
        }
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        BuilderWorker.MissingMaterials missing = BuilderWorker.missingMaterials(villager, inventory, plan.get());
        int jobCost = plan.get().price();
        int blueprintCost = builderBlueprintCost(jobCost);
        BlockPos initialSite = defaultBlueprintOrigin(level, player, plan.get());
        BuilderTaskState.setPendingStructure(state, entry.get().id());
        sendVillagerNotice(player, villager, "interaction.work.builder.preview", Map.of(
                        "structure", entry.get().menuLabel(),
                        "blueprint_cost", formatCurrency(level, blueprintCost),
                        "cost", formatCurrency(level, jobCost),
                        "blocks", Integer.toString(plan.get().blocks().size()),
                        "materials", plan.get().materialSummary(5),
                        "missing", missing.summary(),
                        "site", HiredWorkerBrain.formatPos(initialSite)));
    }

    private static void confirmBuilderOrder(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            ResourceLocation structureId) {
        Optional<ResourceLocation> pending = BuilderTaskState.pendingStructure(state);
        if (pending.isEmpty() || !pending.get().equals(structureId)) {
            sendVillagerNotice(player, villager, "interaction.work.builder.preview_first");
            return;
        }
        if (BuilderTaskState.hasTask(state)) {
            sendVillagerNotice(player, villager, "interaction.work.builder.already_building", BuilderTaskState.replacements(state));
            return;
        }
        Optional<BuilderStructureCatalog.Entry> entry = BuilderStructureCatalog.byId(player.server, structureId);
        if (entry.isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.work.builder.unknown_structure");
            return;
        }
        Optional<BuilderStructureScanner.StructurePlan> plan = BuilderStructureScanner.scan(level, entry.get(), Rotation.NONE);
        if (plan.isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.work.builder.structure_unavailable", Map.of("structure", entry.get().menuLabel()));
            return;
        }
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        BuilderWorker.MissingMaterials missing = BuilderWorker.missingMaterials(villager, inventory, plan.get());
        int cost = plan.get().price();
        int blueprintCost = builderBlueprintCost(cost);
        if (countCurrency(player) < blueprintCost) {
            sendVillagerNotice(player, villager, "interaction.work.builder.need_blueprint_payment", Map.of(
                    "structure", entry.get().menuLabel(),
                    "blueprint_cost", formatCurrency(level, blueprintCost)));
            return;
        }

        removeCurrency(player, blueprintCost);
        VillagerWalletService.addCurrency(villager, blueprintCost, VillagerWalletService.WalletSource.TASK_REWARD);
        BuilderTaskState.clearPendingStructure(state);
        BlockPos origin = defaultBlueprintOrigin(level, player, plan.get());
        giveConstructionBlueprint(
                player,
                level,
                null,
                plan.get(),
                origin,
                UUID.randomUUID(),
                blueprintCost,
                cost,
                missing.summary(),
                0L);
        sendVillagerNotice(player, villager, "interaction.work.builder.blueprint_ready", Map.of(
                "structure", entry.get().menuLabel(),
                "blueprint_cost", formatCurrency(level, blueprintCost),
                "cost", formatCurrency(level, cost),
                "blocks", Integer.toString(plan.get().blocks().size()),
                "materials", missing.summary(),
                "site", HiredWorkerBrain.formatPos(origin)));
        VillagerConversationService.start(player, villager);
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
    }

    private static void startConstructionBlueprintJob(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canUseBuilderBlueprintService(player, level, villager)) {
            sendForcedDialogueReputation(player, villager, constructionBlueprintOptions(), true);
            return;
        }
        ItemStack blueprint = findHeldConstructionBlueprint(player);
        Optional<ConstructionBlueprintItem.PreviewData> optionalPreview = ConstructionBlueprintItem.previewData(blueprint);
        if (optionalPreview.isEmpty()) {
            rejectConstructionBlueprintStart(player, villager, "interaction.work.builder.blueprint_missing");
            return;
        }
        ConstructionBlueprintItem.PreviewData preview = optionalPreview.get();
        if (preview.expired()) {
            rejectConstructionBlueprintStart(player, villager, "interaction.work.builder.blueprint_expired");
            return;
        }
        if (preview.completed()) {
            rejectConstructionBlueprintStart(player, villager, "interaction.work.builder.blueprint_completed");
            return;
        }
        if (preview.started()) {
            rejectConstructionBlueprintStart(player, villager, "interaction.work.builder.blueprint_started");
            return;
        }
        if (!level.dimension().equals(preview.dimension())) {
            rejectConstructionBlueprintStart(player, villager, "interaction.clipboard.work_area.same_dimension");
            return;
        }

        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        if (BuilderTaskState.hasTask(state)) {
            rejectConstructionBlueprintStart(player, villager, "interaction.work.builder.already_building", BuilderTaskState.replacements(state));
            return;
        }
        Optional<BuilderStructureCatalog.Entry> entry = BuilderStructureCatalog.byId(player.server, preview.structureId());
        if (entry.isEmpty()) {
            rejectConstructionBlueprintStart(player, villager, "interaction.work.builder.unknown_structure");
            return;
        }
        Optional<BuilderStructureScanner.StructurePlan> plan =
                BuilderStructureScanner.scan(level, entry.get(), preview.rotation());
        if (plan.isEmpty()) {
            rejectConstructionBlueprintStart(player, villager, "interaction.work.builder.structure_unavailable", Map.of("structure", entry.get().menuLabel()));
            return;
        }
        BuilderSitePlanner.SiteResult site = BuilderSitePlanner.validateSite(
                level,
                player,
                villager,
                null,
                plan.get(),
                preview.origin());
        if (!site.valid()) {
            rejectConstructionBlueprintStart(player, villager, site.statusKey(), site.replacements());
            return;
        }

        int cost = preview.jobCost() > 0 ? preview.jobCost() : plan.get().price();
        if (countCurrency(player) < cost) {
            rejectConstructionBlueprintStart(player, villager, "interaction.work.builder.need_payment", Map.of(
                    "structure", entry.get().menuLabel(),
                    "cost", formatCurrency(level, cost)));
            return;
        }
        removeCurrency(player, cost);

        if (!HiredVillagerContractService.isHired(level, villager)) {
            HiredVillagerContractService.startOneOffBuilderJob(level, villager, player);
        }

        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        BuilderWorker.MissingMaterials missing = BuilderWorker.missingMaterials(villager, inventory, plan.get());
        state.remove("NextWorkGameTime");
        state.remove("ProgressTicks");
        long startedGameTime = level.getGameTime();
        BuilderTaskState.start(state, entry.get(), plan.get(), preview.origin(), preview.rotation(), cost, startedGameTime, preview.jobId());
        BuilderPaymentEscrowService.escrow(villager, preview.jobId(), cost);
        ConstructionBlueprintItem.updatePlacement(blueprint, level, plan.get(), preview.origin(), preview.rotation());
        ConstructionBlueprintItem.markStarted(blueprint, villager, cost, startedGameTime);
        state.putString("Status", "interaction.work.builder.started");
        sendVillagerNotice(player, villager, missing.ready()
                ? "interaction.work.builder.started"
                : "interaction.work.builder.started_waiting_materials", Map.of(
                "structure", entry.get().menuLabel(),
                "cost", formatCurrency(level, cost),
                "blocks", Integer.toString(plan.get().blocks().size()),
                "materials", missing.summary(),
                "site", HiredWorkerBrain.formatPos(preview.origin()),
                "storage_radius", Integer.toString(Math.max(1, VillagerRetaliationConfig.HIRED_BUILDER_MATERIAL_STORAGE_RADIUS.get()))));
        VillagerConversationService.start(player, villager);
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
    }

    private static void rejectConstructionBlueprintStart(ServerPlayer player, Villager villager, String messageKey) {
        rejectConstructionBlueprintStart(player, villager, messageKey, Map.of());
    }

    private static void rejectConstructionBlueprintStart(
            ServerPlayer player,
            Villager villager,
            String messageKey,
            Map<String, String> replacements) {
        sendVillagerNotice(player, villager, messageKey, replacements);
        sendForcedDialogueReputation(player, villager, constructionBlueprintOptions(), true);
    }

    private static int builderBlueprintCost(int jobCost) {
        return Math.max(1, Math.min(5, Math.max(1, jobCost) / 10));
    }

    private static void cancelBuilderOrder(ServerPlayer player, ServerLevel level, Villager villager, CompoundTag state) {
        BuilderTaskState.clearPendingStructure(state);
        if (!BuilderTaskState.hasTask(state)) {
            sendVillagerNotice(player, villager, "interaction.work.builder.cancelled");
            return;
        }
        Optional<UUID> jobId = BuilderTaskState.jobId(state);
        int paid = BuilderTaskState.paidCurrency(state);
        int placed = BuilderTaskState.placedIndex(state);
        int refund = 0;
        if (placed == 0 && paid > 0) {
            refund = BuilderPaymentEscrowService.refund(player, villager, jobId, paid);
            if (refund <= 0) {
                sendVillagerNotice(player, villager, "interaction.work.builder.cancel_refund_unavailable", Map.of("cost", formatCurrency(level, paid)));
                return;
            }
        } else {
            BuilderPaymentEscrowService.releaseToWallet(villager, jobId);
        }
        jobId.ifPresent(id -> ConstructionBlueprintItem.expireMatchingBlueprints(player, id));
        BuilderTaskState.clearTask(state);
        HiredVillagerWorkService.cancelWork(level, villager, HiredVillagerRole.BUILDER, "interaction.work.builder.cancelled");
        if (HiredVillagerContractService.isOneOffBuilderJob(level, villager)) {
            HiredVillagerContractService.finishOneOffBuilderJob(level, villager, "interaction.work.builder.cancelled");
        }
        if (refund > 0) {
            sendVillagerNotice(player, villager, "interaction.work.builder.cancelled_refund", Map.of("cost", formatCurrency(level, refund)));
        } else {
            sendVillagerNotice(player, villager, "interaction.work.builder.cancelled");
        }
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
    }

    private static void giveConstructionBlueprint(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            UUID jobId,
            int paidCurrency,
            int jobCost,
            String missingMaterials,
            long startedGameTime) {
        ItemStack blueprint = ConstructionBlueprintItem.create(
                level,
                villager,
                plan,
                origin,
                jobId,
                paidCurrency,
                jobCost,
                missingMaterials,
                startedGameTime);
        if (!player.getInventory().add(blueprint)) {
            player.drop(blueprint, false);
        }
    }

    private static void stopBrewingOrder(ServerPlayer player, ServerLevel level, Villager villager) {
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.BREWING) {
            sendVillagerNotice(player, villager, "interaction.work.brewing.requires_role_change");
            return;
        }
        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        BrewingWorker.clearOrder(state);
        HiredVillagerWorkService.stopWork(level, villager, HiredVillagerRole.BREWING, "interaction.work.brewing.stopped");
        sendVillagerNotice(player, villager, "interaction.work.brewing.stopped");
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
    }

    public static void handleLoggingFilterRequest(ServerPlayer player, int entityId, String filterId) {
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
        HiredVillagerWorkService.toggleLoggingFilter(player, level, villager, filterId);
    }

    public static void handleLoggingOptionRequest(ServerPlayer player, int entityId, String optionId) {
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
        HiredVillagerWorkService.toggleLoggingOption(player, level, villager, optionId);
    }

    public static void handleFarmingOptionRequest(ServerPlayer player, int entityId, String optionId) {
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
        HiredVillagerWorkService.toggleFarmingOption(player, level, villager, optionId);
    }

    public static void handleHuntingTargetRequest(ServerPlayer player, int entityId, String targetId) {
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
        HiredVillagerWorkService.toggleHuntingTarget(player, level, villager, targetId);
    }

    public static void handleAnimalBreedingTargetRequest(ServerPlayer player, int entityId, String targetId) {
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
        HiredVillagerWorkService.toggleAnimalBreedingTarget(player, level, villager, targetId);
    }

    public static void handleAnimalCullCapRequest(ServerPlayer player, int entityId, int cap) {
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
        HiredVillagerWorkService.setAnimalCullCap(player, level, villager, cap);
    }

    private static void sendHiredRoleNotice(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHired(level, villager)) {
            sendVillagerNotice(player, villager, "interaction.role_requires_hire");
            return;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.role.manage_requires_hirer");
            return;
        }
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        sendVillagerNotice(
                player,
                villager,
                "interaction.role_overview",
                Map.of(
                        "role", role.label(),
                        "available_roles", HiredVillagerRoles.roleSummary(level, villager)
                )
        );
    }

    private static void sendHiredContractNotice(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHired(level, villager)) {
            int dailyCost = HiredVillagerContractService.getDailyCost(level, villager, player);
            sendVillagerNotice(
                    player,
                    villager,
                    "interaction.contract_offer_overview",
                    Map.of(
                            "available_roles", HiredVillagerRoles.roleSummary(level, villager),
                            "contract_cost", formatCurrency(level, dailyCost)
                    )
            );
            return;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.hired_contract_taken");
            return;
        }
        int remainingDays = HiredVillagerContractService.getRemainingHireDays(level, villager);
        sendVillagerNotice(
                player,
                villager,
                "interaction.hired_contract_days_left",
                Map.of("time_remaining", formatDaysRemaining(remainingDays))
        );
    }

    private static void assignClipboardStorage(ServerPlayer player, ServerLevel level, Villager villager, ItemStack clipboard) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.storage.assign_requires_access");
            return;
        }
        List<SelectedStoragePosition> selected = HiredStorageClipboardItem.selectedStoragePositions(
                clipboard,
                HiredStorageClipboardItem.mode(clipboard).assignmentPurpose());
        if (selected.isEmpty()) {
            sendVillagerNotice(player, villager, "interaction.storage.select_with_clipboard");
            return;
        }
        Optional<AssignmentSummaryMessage> message = HiredStorageClipboardItem.assignSelectedStorage(
                player,
                level,
                villager,
                clipboard,
                selected);
        message.ifPresent(summary -> sendVillagerNotice(player, villager, summary.key(), summary.replacements()));
    }

    private static void showAssignedStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.storage.inspect_requires_access");
            return;
        }
        List<AssignedContainerRecord> assigned = AssignedStorageService.assignedStorage(level, villager);
        HiredStorageClipboardItem.sendAssignedStorageOutlines(player, assigned);
        int count = assigned.size();
        sendVillagerNotice(player, villager, "interaction.storage.assigned_count", Map.of("count", Integer.toString(count)));
    }

    private static void showAssignedPaymentStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.payment_storage.requires_hire");
            return;
        }
        List<AssignedContainerRecord> assigned = AssignedStorageService.assignedPaymentStorage(level, villager);
        HiredStorageClipboardItem.sendAssignedStorageOutlines(player, assigned);
        int count = assigned.size();
        sendVillagerNotice(player, villager, "interaction.payment_storage.assigned_count", Map.of(
                "count", Integer.toString(count),
                "auto_payment", HiredVillagerContractService.isAutoPaymentEnabled(level, villager) ? "on" : "off"));
    }

    private static void depositEarnings(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.earnings.deposit_requires_access");
            return;
        }
        int excess = VillagerWalletService.getDepositAmount(villager);
        if (excess <= 0) {
            sendVillagerNotice(player, villager, "interaction.earnings.no_excess");
            return;
        }
        VillagerWalletService.DepositResult result = VillagerWalletService.tryDepositExcessCurrency(villager);
        if (result.storageUnavailable()) {
            sendVillagerNotice(player, villager, "interaction.earnings.storage_unavailable");
        } else if (!result.assignedStorageAvailable()) {
            sendVillagerNotice(player, villager, "interaction.earnings.no_assigned_storage");
        } else if (result.deposited() <= 0) {
            sendVillagerNotice(player, villager, "interaction.earnings.storage_full");
        } else if (result.remaining() > 0) {
            sendVillagerNotice(player, villager, "interaction.earnings.deposited_partial", Map.of(
                    "deposited", formatCurrency(level, result.deposited()),
                    "remaining", formatCurrency(level, result.remaining())));
        } else {
            sendVillagerNotice(player, villager, "interaction.earnings.deposited_all", Map.of(
                    "deposited", formatCurrency(level, result.deposited())));
        }
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
    }

    private static void removeAssignedStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.storage.remove_requires_access");
            return;
        }
        int removed = AssignedStorageService.removeAssignedStorage(level, villager);
        sendVillagerNotice(player, villager, "interaction.storage.removed_count", Map.of(
                "count", Integer.toString(removed),
                "plural", plural(removed)));
    }

    private static void removeAssignedPaymentStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.payment_storage.remove_requires_hirer");
            return;
        }
        int removed = AssignedStorageService.removeAssignedPaymentStorage(level, villager);
        if (removed > 0) {
            HiredVillagerContractService.setAutoPaymentEnabled(villager, false);
        }
        sendVillagerNotice(player, villager, "interaction.payment_storage.removed_count", Map.of(
                "count", Integer.toString(removed),
                "plural", plural(removed)));
    }

    private static void toggleAutoPayment(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.auto_payment.requires_hire");
            return;
        }
        if (!AssignedStorageService.hasAssignedPaymentStorage(level, villager)) {
            sendVillagerNotice(player, villager, "interaction.auto_payment.requires_storage");
            return;
        }
        boolean enabled = HiredVillagerContractService.toggleAutoPayment(level, villager);
        sendVillagerNotice(player, villager, enabled
                ? "interaction.auto_payment.enabled"
                : "interaction.auto_payment.disabled");
    }

    public static boolean canManageAssignedStorage(ServerLevel level, Villager villager, ServerPlayer player) {
        if (HiredVillagerContractService.isHired(level, villager)) {
            return HiredVillagerContractService.isHiredBy(level, villager, player);
        }
        return VillagerInventoryAccess.canAccess(level, villager, player);
    }

    private static ItemStack findClipboard(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (VillagerRetaliationItems.isClipboard(mainHand)) {
            return mainHand;
        }
        ItemStack offhand = player.getOffhandItem();
        return VillagerRetaliationItems.isClipboard(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static ItemStack findHeldConstructionBlueprint(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (ConstructionBlueprintItem.isBlueprint(mainHand)) {
            return mainHand;
        }
        ItemStack offhand = player.getOffhandItem();
        return ConstructionBlueprintItem.isBlueprint(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static ItemStack findConstructionBlueprint(ServerPlayer player, UUID jobId) {
        ItemStack mainHand = player.getMainHandItem();
        if (ConstructionBlueprintItem.previewData(mainHand).filter(data -> data.jobId().equals(jobId)).isPresent()) {
            return mainHand;
        }
        ItemStack offhand = player.getOffhandItem();
        if (ConstructionBlueprintItem.previewData(offhand).filter(data -> data.jobId().equals(jobId)).isPresent()) {
            return offhand;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (ConstructionBlueprintItem.previewData(stack).filter(data -> data.jobId().equals(jobId)).isPresent()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static PlacementUpdate placementUpdate(
            ServerLevel level,
            BuilderStructureCatalog.Entry entry,
            ConstructionBlueprintItem.PreviewData preview,
            ConstructionBlueprintPlacementPayload.Action action,
            int steps,
            BlockPos targetPos) {
        Rotation rotation = preview.rotation();
        BlockPos origin = preview.origin();
        Optional<BuilderStructureScanner.StructurePlan> plan = BuilderStructureScanner.scan(level, entry, rotation);
        if (plan.isEmpty()) {
            return new PlacementUpdate(Optional.empty(), origin, rotation);
        }

        switch (action) {
            case DEPLOY_AT -> origin = originForDeployTarget(plan.get(), targetPos);
            case MOVE_NORTH -> origin = origin.relative(Direction.NORTH, steps);
            case MOVE_EAST -> origin = origin.relative(Direction.EAST, steps);
            case MOVE_SOUTH -> origin = origin.relative(Direction.SOUTH, steps);
            case MOVE_WEST -> origin = origin.relative(Direction.WEST, steps);
            case MOVE_UP -> origin = origin.above(steps);
            case MOVE_DOWN -> origin = origin.below(steps);
            case TOGGLE_LOCK -> {
            }
            case ROTATE_CLOCKWISE, ROTATE_COUNTERCLOCKWISE -> {
                Rotation nextRotation = rotation.getRotated(action == ConstructionBlueprintPlacementPayload.Action.ROTATE_CLOCKWISE
                        ? Rotation.CLOCKWISE_90
                        : Rotation.COUNTERCLOCKWISE_90);
                Optional<BuilderStructureScanner.StructurePlan> rotatedPlan = BuilderStructureScanner.scan(level, entry, nextRotation);
                if (rotatedPlan.isEmpty()) {
                    return new PlacementUpdate(Optional.empty(), origin, rotation);
                }
                BlockPos center = preview.worldCenter();
                origin = center.subtract(rotatedPlan.get().localCenter());
                rotation = nextRotation;
                plan = rotatedPlan;
            }
        }
        return new PlacementUpdate(plan, origin.immutable(), rotation);
    }

    private static BlockPos originForDeployTarget(BuilderStructureScanner.StructurePlan plan, BlockPos targetPos) {
        BlockPos center = plan.localCenter();
        return new BlockPos(targetPos.getX() - center.getX(), targetPos.getY(), targetPos.getZ() - center.getZ());
    }

    private static BlockPos defaultBlueprintOrigin(
            ServerLevel level,
            ServerPlayer player,
            BuilderStructureScanner.StructurePlan plan) {
        BlockPos center = player.blockPosition().relative(player.getDirection(), 5);
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        BlockPos localCenter = plan.localCenter();
        return new BlockPos(
                center.getX() - localCenter.getX(),
                groundY - plan.localMin().getY(),
                center.getZ() - localCenter.getZ());
    }

    private static int countCurrency(ServerPlayer player) {
        return VillagerCurrencyPayment.count(player);
    }

    private static void removeCurrency(ServerPlayer player, int count) {
        VillagerCurrencyPayment.tryRemove(player, count);
    }

    private static void giveCurrency(ServerPlayer player, int count) {
        int remaining = count;
        while (remaining > 0) {
            int chunk = Math.min(VillagerCurrencyResources.maxStackSize(player.serverLevel().getServer()), remaining);
            ItemStack stack = VillagerCurrencyResources.createStack(player.serverLevel().getServer(), chunk);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= chunk;
        }
    }

    private static String formatCurrency(ServerLevel level, int count) {
        return VillagerCurrencyResources.format(level.getServer(), count);
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
    }

    private static String formatDaysRemaining(int count) {
        return count + " day" + plural(count);
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

        if (villager instanceof Villager conversationVillager
                && VillagerConversationService.isConversing(player)
                && !VillagerConversationService.validate(player, conversationVillager)) {
            return;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(player.serverLevel(), villager);
        VillagerReputationNetworking.sendProfile(player, villager, profile);
    }

    public static void handleConversationEndRequest(ServerPlayer player, int entityId) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            Entity entity = player.serverLevel().getEntity(entityId);
            if (entity instanceof Villager forcedVillager
                    && VillagerConversationService.isForced(player, forcedVillager)
                    && ForcedDialogueService.hasSession(player, forcedVillager)) {
                villager = forcedVillager;
            }
        }
        if (villager == null || !VillagerConversationService.validate(player, villager)) {
            if (ForcedDialogueService.isStaleConversationEndRequest(player, entityId)) {
                return;
            }
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
        trySendToPlayer(player, new VillagerConversationEndedPayload(villager.getId(), goodbyeText));
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
                interactionState.lastSeenDay(),
                interactionState.daysSinceLastSeen(),
                weatherState(level, villager),
                timeOfDay(level),
                interactionState.lastPositiveDialogueReputationGameTime(),
                interactionState.lastNegativeDialogueReputationGameTime(),
                interactionState.lastJokeReputationGameTime(),
                interactionState.lastApologyDialogueGameTime(),
                interactionState.lastVillageDefenseReportGameTime(),
                interactionState.lastVillageEventReportGameTime(),
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
                VillageEventMemory.lazyRecentForVillage(level, villager)
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
        trySendToPlayer(player, new VillagerDialogueResponsePayload(
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
        trySendToPlayer(player, new VillagerDialogueResponsePayload(
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
        return canOpenInteractionTarget(player, villager, false, VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get());
    }

    public static boolean canUseForcedInteractionSystem(ServerPlayer player, Villager villager) {
        return canUseInteractionTarget(player, villager, false, VillagerRetaliationConfig.MAX_FORCED_DIALOGUE_DISTANCE.get());
    }

    static void prepareForInteractionSession(ServerPlayer player, Villager villager) {
        if (isCombatBusy(villager) && canInterruptHiredWorkForInteraction(player, villager)) {
            VillagerRetaliationHandler.suspendCombatForInteraction(villager);
        }
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

    static boolean shouldStayForcedConversationSession(ServerPlayer player, Villager villager) {
        double maxDistance = VillagerRetaliationConfig.MAX_FORCED_DIALOGUE_DISTANCE.get();
        return villager.isAlive()
                && !villager.isSleeping()
                && !villager.isTrading()
                && player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(villager) <= maxDistance * maxDistance;
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

    private static boolean canOpenInteractionTarget(ServerPlayer player, Villager villager, boolean allowSleeping, double maxDistance) {
        return villager.isAlive()
                && (allowSleeping || !villager.isSleeping())
                && !villager.isTrading()
                && (!isCombatBusy(villager) || canInterruptHiredWorkForInteraction(player, villager))
                && !VillagerRetaliationHandler.isHostileTowards(villager, player)
                && player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(villager) <= maxDistance * maxDistance;
    }

    private static boolean isCombatBusy(Villager villager) {
        return villager.getTarget() != null || villager.getLastHurtByMob() != null;
    }

    private static boolean canInterruptHiredWorkForInteraction(ServerPlayer player, Villager villager) {
        return villager.level() instanceof ServerLevel level
                && HiredVillagerContractService.isHiredBy(level, villager, player);
    }

    private static boolean shouldRefuseDespisedConversation(Villager villager, ServerPlayer player) {
        return VillagerAggressionPolicy.shouldAttackOnSight(villager, player);
    }

    public static void sendNotice(ServerPlayer player, int entityId, String text) {
        String resolvedText = VillagerDialogueResources
                .globalMessage(player.getServer(), player.getRandom(), text, VillagerLocale.locale(player))
                .orElse(text);
        trySendToPlayer(player, new VillagerInteractionNoticePayload(entityId, resolvedText, ""));
    }

    public static void sendVillagerNotice(ServerPlayer player, Villager villager, String text) {
        String resolvedText = text;
        if (villager.level() instanceof ServerLevel level) {
            resolvedText = VillagerDialogueResources.message(createDialogueContext(level, player, villager), text).orElse(text);
        }
        sendPersonalVillagerChat(player, villager, resolvedText);
    }

    public static void sendVillagerNotice(ServerPlayer player, Villager villager, String text, Map<String, String> replacements) {
        String resolvedText = text;
        if (villager.level() instanceof ServerLevel level) {
            resolvedText = VillagerDialogueResources.message(createDialogueContext(level, player, villager), text, replacements).orElse(text);
        }
        sendPersonalVillagerChat(player, villager, resolvedText);
    }

    public static void sendVillagerNotice(ServerPlayer player, Villager villager, String text, Map<String, String> replacements, double nearbyBroadcastRadius) {
        String resolvedText = text;
        if (villager.level() instanceof ServerLevel level) {
            resolvedText = VillagerDialogueResources.message(createDialogueContext(level, player, villager), text, replacements).orElse(text);
        }
        sendPersonalVillagerChat(
                player,
                villager,
                resolvedText,
                DialogueTextSegment.parse(resolvedText, DialogueTextEffects.NONE),
                nearbyBroadcastRadius,
                true);
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
        sendPersonalVillagerChat(player, villager, responseText);
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
        sendPersonalVillagerChat(player, villager, responseText);
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
        sendPersonalVillagerChat(player, villager, responseText);
    }

    public static void broadcastVillagerChat(ServerLevel level, Villager villager, String text) {
        broadcastVillagerChat(level, villager, text, "");
    }

    public static void broadcastVillagerChat(ServerLevel level, Villager villager, String text, DialogueTextEffects textEffects) {
        broadcastVillagerChat(level, villager, text, DialogueTextSegment.parse(text, textEffects));
    }

    public static void broadcastVillagerChat(ServerLevel level, Villager villager, String text, List<DialogueTextSegment> textSegments) {
        broadcastVillagerChat(level, villager, text, "", configuredVillagerChatBroadcastRadius(), textSegments);
    }

    public static void broadcastVillagerChat(ServerLevel level, Villager villager, String text, String speakerLabel) {
        broadcastVillagerChat(level, villager, text, speakerLabel, configuredVillagerChatBroadcastRadius());
    }

    public static void sendPersonalVillagerChat(ServerPlayer player, Villager villager, String text) {
        sendPersonalVillagerChat(player, villager, text, DialogueTextSegment.parse(text, DialogueTextEffects.NONE));
    }

    public static void sendPersonalVillagerChat(
            ServerPlayer player,
            Villager villager,
            String text,
            List<DialogueTextSegment> textSegments) {
        sendPersonalVillagerChat(player, villager, text, textSegments, configuredVillagerChatBroadcastRadius(), false);
    }

    private static void sendPersonalVillagerChat(
            ServerPlayer player,
            Villager villager,
            String text,
            List<DialogueTextSegment> textSegments,
            double nearbyBroadcastRadius,
            boolean forceLocalBroadcast) {
        if (text == null || text.isBlank()) {
            return;
        }
        VillagerInteractionNoticePayload payload = new VillagerInteractionNoticePayload(
                villager.getId(),
                text,
                "",
                textSegments
        );
        trySendToPlayer(player, payload);
        if (VillagerRetaliationConfig.SHOW_PERSONAL_INTERACTION_DIALOGUE_TO_NEARBY_PLAYERS.get()
                && villager.level() instanceof ServerLevel level) {
            broadcastVillagerChat(
                    level,
                    villager,
                    text,
                    "",
                    nearbyBroadcastRadius,
                    textSegments,
                    player.getUUID(),
                    forceLocalBroadcast
            );
        }
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
        broadcastVillagerChat(level, villager, text, speakerLabel, radius, DialogueTextEffects.NONE);
    }

    private static void broadcastVillagerChat(
            ServerLevel level,
            Villager villager,
            String text,
            String speakerLabel,
            double radius,
            DialogueTextEffects textEffects) {
        broadcastVillagerChat(level, villager, text, speakerLabel, radius, DialogueTextSegment.parse(text, textEffects));
    }

    private static void broadcastVillagerChat(
            ServerLevel level,
            Villager villager,
            String text,
            String speakerLabel,
            double radius,
            List<DialogueTextSegment> textSegments) {
        if (text == null || text.isBlank()) {
            return;
        }

        broadcastVillagerChat(level, villager, text, speakerLabel, radius, textSegments, null);
    }

    private static void broadcastVillagerChat(
            ServerLevel level,
            Villager villager,
            String text,
            String speakerLabel,
            double radius,
            List<DialogueTextSegment> textSegments,
            UUID excludedPlayerId) {
        broadcastVillagerChat(level, villager, text, speakerLabel, radius, textSegments, excludedPlayerId, false);
    }

    private static void broadcastVillagerChat(
            ServerLevel level,
            Villager villager,
            String text,
            String speakerLabel,
            double radius,
            List<DialogueTextSegment> textSegments,
            UUID excludedPlayerId,
            boolean forceLocalDistance) {
        if (text == null || text.isBlank()) {
            return;
        }

        VillagerChatBroadcastMode mode = villagerChatBroadcastMode();
        if (!mode.enabled()) {
            return;
        }

        double radiusSqr = effectiveVillagerChatBroadcastRadius(radius) * effectiveVillagerChatBroadcastRadius(radius);
        boolean usesDistance = forceLocalDistance || mode.usesDistance();
        VillagerInteractionNoticePayload payload = new VillagerInteractionNoticePayload(
                villager.getId(),
                text,
                speakerLabel == null ? "" : speakerLabel,
                textSegments
        );
        for (ServerPlayer nearbyPlayer : level.players()) {
            if (!nearbyPlayer.isAlive()
                    || nearbyPlayer.isSpectator()
                    || (excludedPlayerId != null && nearbyPlayer.getUUID().equals(excludedPlayerId))
                    || (usesDistance && nearbyPlayer.distanceToSqr(villager) > radiusSqr)) {
                continue;
            }
            trySendToPlayer(nearbyPlayer, payload);
        }
    }

    private static VillagerChatBroadcastMode villagerChatBroadcastMode() {
        VillagerChatBroadcastMode mode = VillagerRetaliationConfig.VILLAGER_CHAT_BROADCAST_MODE.get();
        return mode == null ? VillagerChatBroadcastMode.LOCAL : mode;
    }

    private static int configuredVillagerChatBroadcastRadius() {
        return Math.clamp(VillagerRetaliationConfig.VILLAGER_CHAT_BROADCAST_RADIUS.get(), 1, 64);
    }

    private static double effectiveVillagerChatBroadcastRadius(double requestedRadius) {
        double clampedRequested = Math.clamp(requestedRadius, 1.0D, 64.0D);
        return Math.min(clampedRequested, configuredVillagerChatBroadcastRadius());
    }

    private static void trySendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException ignored) {
            // Mock server players used by GameTests do not negotiate custom client payload channels.
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

    private record PlacementUpdate(
            Optional<BuilderStructureScanner.StructurePlan> plan,
            BlockPos origin,
            Rotation rotation) {
    }

    private record DialogueContextSnapshots(
            VillagerFamilyTreeSnapshot familyTree,
            VillagerRelationshipSnapshot relationships,
            List<VillageEventMemory.MemoryEvent> recentEvents) {
    }

}
