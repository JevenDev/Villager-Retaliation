package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueReputationEffect;
import com.jvn.villagerretaliation.dialogue.normal.DialogueReputationService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.normal.GiftAdviceKind;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.VillagerRaidDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerStoryHintService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.mount.VillagerMountOwnershipDialogue;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerDialogueRequestHandler {
    private VillagerDialogueRequestHandler() {
    }

    public static void handle(ServerPlayer player, int entityId, String optionId) {
        InteractionTargetContext target = InteractionRequestValidator
                .requireDialogueConversation(player, entityId)
                .orElse(null);
        if (target == null) {
            return;
        }
        Villager villager = target.villager();
        if (ForcedDialogueService.handleDialogueRequest(player, villager, optionId)) {
            return;
        }
        if (ForcedDialogueService.SPECIAL_ORDER_STATUS_ROOT_OPTION_ID.equals(optionId)) {
            ForcedDialogueService.openSpecialOrderStatusDialogue(player, villager);
            return;
        }
        if (VillagerConversationService.isForced(player, villager) && !ForcedDialogueService.hasSession(player, villager)) {
            VillagerConversationService.endForPlayer(player, true);
            return;
        }
        if (VillagerAggressionPolicy.shouldAttackOnSight(villager, player)) {
            InteractionRequestValidator.endConversationWithRefusal(target, "interaction.refuse_despised");
            return;
        }
        VillagerInteractionService.focusVillagerOnPlayer(villager, player);

        ServerLevel level = target.level();
        if (!VillagerMountOwnershipDialogue.allowsRequest(level, player, villager, optionId)) {
            return;
        }
        VillagerInteractionTracker.InteractionState interactionState = VillagerInteractionTracker.getState(level, villager, player);
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        if (DialogueTreeService.LEAVE_OPTION_ID.equals(optionId)) {
            DialogueTreeService.leaveActiveSession(context);
            VillagerInteractionService.sendDialogueReputation(player, villager, level);
            return;
        }
        DialogueOptionDefinition dialogueOption = VillagerDialogueResources.dialogueOption(context, optionId).orElse(null);
        if (dialogueOption == null) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.unknown_dialogue_option");
            VillagerInteractionService.sendDialogueReputation(player, villager, level);
            return;
        }

        DialogueRequestType requestType = dialogueOption.requestType();
        DialogueItemPayment itemPayment = dialogueOption.itemPayment();
        DialogueItemPaymentResult itemPaymentResult = DialogueItemPaymentResult.empty();
        if (!itemPayment.isEmpty()) {
            Optional<DialogueItemPaymentResult> paymentResult = executeDialogueItemPayment(player, villager, itemPayment);
            if (paymentResult.isEmpty()) {
                String failureText = resolveDialogueItemPaymentResponse(
                        context,
                        itemPayment.selectFailureResponse(context.random()),
                        itemPayment.removal().replacements()
                );
                VillagerInteractionService.sendDialogueReputation(player, villager, level);
                VillagerInteractionService.sendPersonalVillagerChat(player, villager, failureText);
                return;
            }
            itemPaymentResult = paymentResult.get();
        }

        // Selection may advance a dialogue tree, begin a quest, or claim a report.
        // Commit the required payment before allowing any of those side effects.
        VillagerDialogueService.DialogueResult result = selectDialogueResult(context, dialogueOption, interactionState);

        var reputationEffect = VillagerQuestService.isQuestDialogueOption(dialogueOption)
                ? com.jvn.villagerretaliation.dialogue.normal.DialogueReputationEffect.none(requestType)
                : com.jvn.villagerretaliation.dialogue.normal.DialogueReputationService.apply(context, requestType, interactionState);
        VillagerMoodService.recordDialogueEffect(context, requestType, reputationEffect);
        VillagerInteractionService.playDialogueFeedback(level, villager, reputationEffect);
        VillagerAmbientIndicatorService.onDialogueResponse(level, villager, player, optionId, requestType, reputationEffect);
        String responseText = result.text();
        List<DialogueTextSegment> responseSegments = result.textSegments();
        boolean responseTextReplaced = false;
        if (!itemPayment.isEmpty()) {
            String successText = itemPayment.selectSuccessResponse(context.random());
            if (!successText.isBlank()) {
                responseText = resolveDialogueItemPaymentResponse(context, successText, itemPaymentResult.replacements());
                responseTextReplaced = true;
            }
        }
        if (reputationEffect.responseOverride() != null) {
            responseText = reputationEffect.responseOverride();
            responseTextReplaced = true;
        }
        if (responseTextReplaced) {
            responseText = VillagerDialogueResources.resolveTemplate(responseText, itemPaymentResult.replacements());
            responseSegments = DialogueTextSegment.parse(responseText, result.textEffects());
        } else if (!itemPaymentResult.replacements().isEmpty()) {
            responseSegments = resolveTemplate(responseSegments, itemPaymentResult.replacements());
            responseText = DialogueTextSegment.plainText(responseSegments);
        }
        responseText = DialogueTextSegment.plainText(responseSegments);
        VillagerInteractionTracker.rememberDialogue(level, villager, player, requestType, result.lineId());
        claimTrackerReports(level, villager, player, requestType);
        VillagerInteractionService.sendDialogueReputation(
                player,
                villager,
                level,
                requestType,
                reputationEffect,
                dialogueOption.forceCameraTowardsVillager()
        );
        VillagerInteractionService.sendPersonalVillagerChat(player, villager, responseText, responseSegments);
        if (VillagerAggressionPolicy.shouldAttackOnSight(villager, player)) {
            VillagerConversationService.endForPlayer(player, true);
        }
    }

    private static void claimTrackerReports(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            DialogueRequestType requestType) {
        if (requestType == DialogueRequestType.COMBAT_SURVIVAL_REPORT) {
            VillagerInteractionTracker.claimUnreportedCombatSurvivalReport(level, villager, player);
        } else if (requestType == DialogueRequestType.GEAR_REPORT) {
            VillagerInteractionTracker.claimUnreportedGearReport(level, villager, player);
        } else if (requestType == DialogueRequestType.RECRUITMENT_FOLLOWUP) {
            VillagerInteractionTracker.claimUnreportedRecruitmentFollowup(level, villager, player);
        } else if (requestType == DialogueRequestType.CURED_RECOGNITION) {
            VillagerInteractionTracker.claimUnreportedCuredRecognition(level, villager, player);
        } else if (requestType == DialogueRequestType.RAID_VICTORY_ACKNOWLEDGEMENT) {
            VillagerRaidDialogueService.claimVictoryAcknowledgement(level, villager, player);
        }
    }

    private static VillagerDialogueService.DialogueResult selectDialogueResult(
            DialogueContext context,
            DialogueOptionDefinition dialogueOption,
            VillagerInteractionTracker.InteractionState interactionState) {
        DialogueRequestType requestType = dialogueOption.requestType();
        Optional<VillagerDialogueService.DialogueResult> treeResult = DialogueTreeService.handleDialogueOption(context, dialogueOption);
        if (treeResult.isPresent()) {
            return treeResult.get();
        }
        if (requestType == DialogueRequestType.GIFT_PREFERENCES) {
            List<VillagerGiftKnowledgeService.GiftKnowledgeDiscovery> discoveries =
                    VillagerGiftKnowledgeService.discoverFromGiftQuestion(context);
            if (discoveries.isEmpty()) {
                return new VillagerDialogueService.DialogueResult(
                        "gift_preference_known",
                        giftAdviceLine(context, GiftAdviceKind.ALREADY_KNOWN, "", ""));
            }
            String response = discoveries.stream()
                    .map(discovery -> giftAdviceLine(
                            context,
                            discovery.adviceKind(),
                            discovery.itemName(),
                            discovery.subject()))
                    .filter(line -> !line.isBlank())
                    .collect(java.util.stream.Collectors.joining("\n"));
            return new VillagerDialogueService.DialogueResult("gift_preference_discovery", response);
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
        if (requestType == DialogueRequestType.STORY) {
            Optional<VillagerDialogueService.DialogueResult> raidStory = VillagerRaidDialogueService.selectRaidStory(context);
            if (raidStory.isPresent()) {
                return raidStory.get();
            }
        }
        Optional<VillagerDialogueService.DialogueResult> questResult = VillagerQuestService.handleDialogueOption(context, dialogueOption);
        if (questResult.isPresent()) {
            return questResult.get();
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
                        .randomLikedGiftName(context.level(), testedProfession, report.itemId(), context.locale(), context.random())
                        .orElse("something useful");
        Map<String, String> replacements = Map.of(
                "gift_item", giftAdviceItemName(context, report),
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

    private static String giftAdviceItemName(
            DialogueContext context,
            VillagerInteractionTracker.GiftAdviceResultReport report) {
        ResourceLocation itemId = report.itemId() == null ? null : ResourceLocation.tryParse(report.itemId());
        if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
            var item = BuiltInRegistries.ITEM.get(itemId);
            if (item != Items.AIR) {
                return VillagerItemText.dialogueName(
                        context.level().getServer(), context.locale(), new ItemStack(item));
            }
        }
        return report.itemName() == null || report.itemName().isBlank() ? "that gift" : report.itemName();
    }

    private static VillagerProfession professionFromKey(String key) {
        return VillagerProfessionUtil.parse(key).orElse(VillagerProfession.NONE);
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
        return Optional.of(DialogueItemPaymentResult.from(
                player.getServer(), VillagerLocale.locale(player), itemPayment, removedStacks.get()));
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

    private static List<DialogueTextSegment> resolveTemplate(
            List<DialogueTextSegment> segments,
            Map<String, String> replacements) {
        if (segments == null || segments.isEmpty() || replacements.isEmpty()) {
            return segments == null ? List.of() : segments;
        }
        return segments.stream()
                .map(segment -> new DialogueTextSegment(
                        VillagerDialogueResources.resolveTemplate(segment.text(), replacements),
                        segment.effects()))
                .toList();
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String itemListName(MinecraftServer server, String locale, List<ItemStack> stacks) {
        return stacks.stream()
                .map(stack -> VillagerItemText.stackName(server, locale, stack))
                .reduce((left, right) -> left + ", " + right)
                .orElse("items");
    }

    private record DialogueItemPaymentResult(Map<String, String> replacements) {
        private static DialogueItemPaymentResult empty() {
            return new DialogueItemPaymentResult(Map.of());
        }

        private static DialogueItemPaymentResult from(
                MinecraftServer server,
                String locale,
                DialogueItemPayment itemPayment,
                List<ItemStack> removedStacks) {
            Map<String, String> replacements = new HashMap<>(itemPayment.removal().replacements());
            int count = removedStacks.stream().mapToInt(ItemStack::getCount).sum();
            ItemStack representative = removedStacks.isEmpty() ? ItemStack.EMPTY : removedStacks.getFirst();
            String itemName = representative.isEmpty()
                    ? "items"
                    : VillagerItemText.dialogueName(server, locale, representative);
            String itemStack = representative.isEmpty()
                    ? "items"
                    : VillagerItemText.stackName(server, locale, representative);
            String itemId = representative.isEmpty() ? "" : itemId(representative);
            replacements.put("given_count", Integer.toString(count));
            replacements.put("given_item_count", Integer.toString(count));
            replacements.put("given_item", itemName);
            replacements.put("given_item_id", itemId);
            replacements.put("given_stack", itemStack);
            replacements.put("given_items", itemListName(server, locale, removedStacks));
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
