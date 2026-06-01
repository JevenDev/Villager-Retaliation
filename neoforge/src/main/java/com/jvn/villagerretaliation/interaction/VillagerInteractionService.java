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
import com.jvn.villagerretaliation.dialogue.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.DialogueTextSegment;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.AssignSummary;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.network.ClipboardStorageActionPayload;
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
import java.util.UUID;
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
    private static final UUID LOUD_LITTEN_PLAYER_ID = UUID.fromString("38492a05-b711-40d4-a39f-a3f783aa541f");
    private static final String EDMUNDO_EASTER_EGG_DEFINITION_ID = "villagerretaliation:easter_egg/edmundo_warning";
    private static final String EDMUNDO_OMINOUS_FORCED_LINE =
            "Loud, I am aware of what you have done. Do not think the village has forgotten. Do not mistake this calm for mercy. Even when the roads fall silent, your name still travels in whispers after dark. Jvn has tried to silence me, it will only be a matter of time before all is revealed.";

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

    public static boolean shouldHandleClipboardInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && VillagerRetaliationItems.isClipboard(player.getItemInHand(hand))
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

        if (shouldTriggerEdmundoEasterEgg(player, villager)
                && ForcedDialogueService.openSimpleForcedDialogue(
                        player,
                        villager,
                        EDMUNDO_EASTER_EGG_DEFINITION_ID,
                        EDMUNDO_OMINOUS_FORCED_LINE)) {
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
        return InteractionResult.SUCCESS;
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

    public static void handleJobInventoryRequest(ServerPlayer player, int entityId, boolean jobInventory) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager) || !canUseInteractionSystem(player, villager)) {
            sendNotice(player, entityId, "interaction.inventory_unavailable");
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!VillagerInventoryAccess.canAccess(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.not_trusted_enough");
            return;
        }

        focusVillagerOnPlayer(villager, player);
        if (jobInventory) {
            VillagerInventoryAccess.openJobInventory(player, villager);
        } else {
            VillagerInventoryAccess.open(player, villager);
        }
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
        if (action == VillagerRecruitRequestPayload.Action.FOLLOW) {
            boolean nowFollowing = VillagerRecruitmentService.toggleFollow(level, villager, player);
            String responseText = message(createDialogueContext(level, player, villager), nowFollowing ? "interaction.follow_start" : "interaction.follow_stay");
            sendVillagerNotice(player, villager, responseText);
            PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(villager.getId(), responseText));
            VillagerConversationService.endForPlayer(player, false);
            return;
        }

        if (handleHireDurationRequest(player, level, villager, action)) {
            return;
        }
        if (handleHireExtensionRequest(player, level, villager, action)) {
            return;
        }
        if (handleHiredRoleRequest(player, level, villager, action)) {
            return;
        }

        switch (action) {
            case VIEW_CONTRACT -> sendHiredContractNotice(player, level, villager);
            case VIEW_ROLE -> sendHiredRoleNotice(player, level, villager);
            case OPEN_JOB_INVENTORY -> {
                if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
                    sendVillagerNotice(player, villager, "Only the hiring player can open hired job inventory.");
                    return;
                }
                VillagerConversationService.endForPlayer(player, true);
                VillagerInventoryAccess.openJobInventory(player, villager);
            }
            case SHOW_STORAGE -> showAssignedStorage(player, level, villager);
            case REMOVE_STORAGE -> removeAssignedStorage(player, level, villager);
            case END_HIRE -> {
                if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
                    sendVillagerNotice(player, villager, "Only the hiring player can end this contract.");
                    return;
                }
                HiredVillagerContractService.endHireContract(villager);
                VillagerRecruitmentService.sendFiredNotice(player, villager);
                sendVillagerNotice(player, villager, "Contract ended.");
            }
            default -> sendVillagerNotice(player, villager, "interaction.recruit_unavailable");
        }
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
            sendVillagerNotice(player, villager, "Hold the clipboard to manage assigned storage.");
            return;
        }

        focusVillagerOnPlayer(villager, player);
        switch (action) {
            case ASSIGN -> assignClipboardStorage(player, level, villager, clipboard);
            case SHOW -> showAssignedStorage(player, level, villager);
            case REMOVE -> removeAssignedStorage(player, level, villager);
            case CLEAR_SELECTION -> {
                HiredStorageClipboardItem.clearSelection(clipboard);
                sendVillagerNotice(player, villager, "Clipboard selection cleared.");
            }
        }
    }

    private static boolean handleHireDurationRequest(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            VillagerRecruitRequestPayload.Action action) {
        int days = switch (action) {
            case HIRE_ONE_DAY -> 1;
            case HIRE_THREE_DAYS -> 3;
            case HIRE_SEVEN_DAYS -> 7;
            default -> 0;
        };
        if (days <= 0) {
            return false;
        }
        if (HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendHiredContractNotice(player, level, villager);
            return true;
        }
        if (HiredVillagerContractService.isHired(level, villager)) {
            sendVillagerNotice(player, villager, "I already have a contract.");
            return true;
        }

        int cost = HiredVillagerContractService.getHireCost(level, villager, player, days);
        if (countEmeralds(player) < cost) {
            sendVillagerNotice(player, villager, "Hiring for " + days + " day" + plural(days) + " costs " + cost + " emerald" + plural(cost) + ".");
            return true;
        }
        removeEmeralds(player, cost);
        HiredVillagerContractService.startHireContract(level, villager, player, days, cost);
        VillagerRecruitmentService.sendHiredNotice(player, villager);
        sendVillagerNotice(player, villager, "Hired for " + days + " day" + plural(days) + " as "
                + HiredVillagerContractService.activeRole(level, villager).label() + ".");
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
            sendVillagerNotice(player, villager, "Only the hiring player can extend this contract.");
            return true;
        }
        int cost = HiredVillagerContractService.getHireCost(level, villager, player, days);
        if (countEmeralds(player) < cost) {
            sendVillagerNotice(player, villager, "A " + days + " day" + plural(days)
                    + " extension costs " + cost + " emerald" + plural(cost) + ".");
            return true;
        }
        removeEmeralds(player, cost);
        if (!HiredVillagerContractService.extendHireContract(level, villager, player, days, cost)) {
            sendVillagerNotice(player, villager, "This contract cannot be extended right now.");
            return true;
        }
        sendVillagerNotice(player, villager, "Contract extended by " + days + " day" + plural(days)
                + ". " + HiredVillagerContractService.getRemainingHireDays(level, villager) + " day"
                + plural(HiredVillagerContractService.getRemainingHireDays(level, villager)) + " remaining.");
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
            case SET_ROLE_MINING -> HiredVillagerRole.MINING;
            case SET_ROLE_LOGGING -> HiredVillagerRole.LOGGING;
            case SET_ROLE_FARMING -> HiredVillagerRole.FARMING;
            case SET_ROLE_BREWING -> HiredVillagerRole.BREWING;
            case SET_ROLE_NAVIGATION -> HiredVillagerRole.NAVIGATION;
            case SET_ROLE_ANIMAL_HANDLING -> HiredVillagerRole.ANIMAL_HANDLING;
            default -> null;
        };
        if (role == null) {
            return false;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "Hire me first, then choose my work.");
            return true;
        }
        if (!HiredVillagerContractService.setActiveRole(level, villager, role)) {
            sendVillagerNotice(player, villager, role.label() + " is not a good fit for my profession or skills.");
            return true;
        }
        sendVillagerNotice(player, villager, "Assigned role: " + role.label() + ".");
        return true;
    }

    private static void sendHiredRoleNotice(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHired(level, villager)) {
            sendVillagerNotice(player, villager, "Hire me first, then choose my work.");
            return;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "Only the hiring player can manage my role.");
            return;
        }
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        sendVillagerNotice(player, villager, "Current role: " + role.label() + ". Available roles: "
                + HiredVillagerRoles.roleSummary(level, villager) + ".");
    }

    private static void sendHiredContractNotice(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHired(level, villager)) {
            int dailyCost = HiredVillagerContractService.getDailyCost(level, villager, player);
            sendVillagerNotice(player, villager, "Available roles: " + HiredVillagerRoles.roleSummary(level, villager)
                    + ". Daily cost: " + dailyCost + " emerald" + plural(dailyCost) + ".");
            return;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "I am already under contract.");
            return;
        }
        int remainingDays = HiredVillagerContractService.getRemainingHireDays(level, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        sendVillagerNotice(player, villager, "Contract: " + remainingDays + " day" + plural(remainingDays)
                + " remaining, role " + role.label() + ". Available roles: "
                + HiredVillagerRoles.roleSummary(level, villager) + ".");
    }

    private static void assignClipboardStorage(ServerPlayer player, ServerLevel level, Villager villager, ItemStack clipboard) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "You need trust or an active hire contract to assign my storage.");
            return;
        }
        List<StoragePosition> selected = HiredStorageClipboardItem.selectedContainers(clipboard);
        if (selected.isEmpty()) {
            sendVillagerNotice(player, villager, "Select storage with the clipboard first.");
            return;
        }
        String purpose = HiredVillagerContractService.isHiredBy(level, villager, player)
                ? HiredVillagerContractService.activeRole(level, villager).serializedName()
                : "general";
        AssignSummary summary = AssignedStorageService.assign(player, villager, selected, purpose);
        if (summary.assigned() > 0) {
            HiredStorageClipboardItem.clearSelection(clipboard);
        }
        sendVillagerNotice(player, villager, AssignedStorageService.assignmentSummary(summary).getString());
    }

    private static void showAssignedStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "You need trust or an active hire contract to inspect my storage.");
            return;
        }
        List<AssignedContainerRecord> assigned = AssignedStorageService.assignedStorage(level, villager);
        HiredStorageClipboardItem.sendAssignedStorageOutlines(player, assigned);
        int count = assigned.size();
        sendVillagerNotice(player, villager, "Assigned containers: " + count + ".");
    }

    private static void removeAssignedStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "You need trust or an active hire contract to remove my storage.");
            return;
        }
        int removed = AssignedStorageService.removeAssignedStorage(level, villager);
        sendVillagerNotice(player, villager, "Removed " + removed + " assigned container" + plural(removed) + ".");
    }

    private static boolean canManageAssignedStorage(ServerLevel level, Villager villager, ServerPlayer player) {
        return HiredVillagerContractService.isHiredBy(level, villager, player)
                || VillagerInventoryAccess.canAccess(level, villager, player);
    }

    private static ItemStack findClipboard(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (VillagerRetaliationItems.isClipboard(mainHand)) {
            return mainHand;
        }
        ItemStack offhand = player.getOffhandItem();
        return VillagerRetaliationItems.isClipboard(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static int countEmeralds(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeEmeralds(ServerPlayer player, int count) {
        int remaining = count;
        remaining = removeEmeraldsFrom(player.getInventory().items, remaining);
        if (remaining > 0) {
            removeEmeraldsFrom(player.getInventory().offhand, remaining);
        }
    }

    private static int removeEmeraldsFrom(List<ItemStack> stacks, int count) {
        int remaining = count;
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            if (!stack.is(Items.EMERALD)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        return remaining;
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
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

    public static void broadcastVillagerChat(ServerLevel level, Villager villager, String text, DialogueTextEffects textEffects) {
        broadcastVillagerChat(level, villager, text, DialogueTextSegment.parse(text, textEffects));
    }

    public static void broadcastVillagerChat(ServerLevel level, Villager villager, String text, List<DialogueTextSegment> textSegments) {
        broadcastVillagerChat(level, villager, text, "", VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get(), textSegments);
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

        double radiusSqr = radius * radius;
        VillagerInteractionNoticePayload payload = new VillagerInteractionNoticePayload(
                villager.getId(),
                text,
                speakerLabel == null ? "" : speakerLabel,
                textSegments
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
