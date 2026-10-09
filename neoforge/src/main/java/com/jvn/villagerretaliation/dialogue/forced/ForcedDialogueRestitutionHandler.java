package com.jvn.villagerretaliation.dialogue.forced;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueFollowUp;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueStolenItemReturn;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.LocalizedText;
import com.jvn.villagerretaliation.dialogue.normal.SocialAttributeCondition;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager.ReputationSnapshot;
import com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

final class ForcedDialogueRestitutionHandler {
    private static final String RESTITUTION_DEFINITION_SUFFIX = ".restitution_options";
    private static final String RESTITUTION_PAY_SUFFIX = ".pay";
    private static final String RESTITUTION_HAGGLE_SUFFIX = ".haggle";
    private static final String RESTITUTION_REFUSE_SUFFIX = ".refuse";
    private static final String RESTITUTION_THREATEN_SUFFIX = ".threaten";
    private static final String RESTITUTION_PROMPT_MESSAGE_KEY = "forced.restitution.prompt";
    private static final String RESTITUTION_HAGGLE_ACCEPT_MESSAGE_KEY = "forced.restitution.haggle.accept";
    private static final String RESTITUTION_HAGGLE_DENY_MESSAGE_KEY = "forced.restitution.haggle.deny";
    private static final String RESTITUTION_PAY_LABEL_MESSAGE_KEY = "forced.restitution.option.pay.label";
    private static final String RESTITUTION_HAGGLE_LABEL_MESSAGE_KEY = "forced.restitution.option.haggle.label";
    private static final String RESTITUTION_REFUSE_LABEL_MESSAGE_KEY = "forced.restitution.option.refuse.label";
    private static final String RESTITUTION_THREATEN_LABEL_MESSAGE_KEY = "forced.restitution.option.threaten.label";
    private static final String RESTITUTION_PAY_SUCCESS_MESSAGE_KEY = "forced.restitution.pay.success";
    private static final String RESTITUTION_PAY_FAILURE_MESSAGE_KEY = "forced.restitution.pay.failure";
    private static final String RESTITUTION_REFUSE_RESPONSE_MESSAGE_KEY = "forced.restitution.refuse.response";
    private static final String RESTITUTION_THREATEN_RESPONSE_MESSAGE_KEY = "forced.restitution.threaten.response";
    private static final String RESTITUTION_REDUCED_PAY_SUCCESS_MESSAGE_KEY = "forced.restitution.reduced_pay.success";

    private ForcedDialogueRestitutionHandler() {
    }

    static boolean handle(ServerPlayer player, Villager villager, ForcedDialogueRuntime.Session session,
            ForcedDialogueOption option, ForcedDialogueRuntime.GeneratedMenu menu) {
        if (isRestitutionEntryOption(option)) {
            openRestitutionOptions(player, villager, session, option, menu);
            return true;
        }
        if (isRestitutionHaggleOption(option)) {
            handleRestitutionHaggle(player, villager, session, option, menu);
            return true;
        }
        return false;
    }

    private static boolean isRestitutionEntryOption(ForcedDialogueOption option) {
        return !option.itemPayment().isEmpty()
                && !isRestitutionPaymentOption(option)
                && !isRestitutionHaggleOption(option)
                && (option.id().toLowerCase(Locale.ROOT).contains("restitution")
                || option.label().text().toLowerCase(Locale.ROOT).contains("restitution"));
    }

    private static boolean isRestitutionPaymentOption(ForcedDialogueOption option) {
        return option.id().endsWith(RESTITUTION_PAY_SUFFIX);
    }

    private static boolean isRestitutionHaggleOption(ForcedDialogueOption option) {
        return option.id().endsWith(RESTITUTION_HAGGLE_SUFFIX);
    }

    private static void openRestitutionOptions(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueRuntime.Session session,
            ForcedDialogueOption sourceOption,
            ForcedDialogueRuntime.GeneratedMenu menu) {
        String line = ForcedDialogueResources.resolveTemplate(
                sourceOption.selectResponse(player.serverLevel().getRandom()),
                session.context(),
                sourceOption.itemPayment().removal().replacements());
        if (line.isBlank()) {
            line = forcedMessage(
                    player,
                    villager,
                    RESTITUTION_PROMPT_MESSAGE_KEY,
                    sourceOption.itemPayment().removal().replacements());
        }
        menu.open(restitutionDefinition(player, villager, session, sourceOption, line, sourceOption.itemPayment(), false));
    }

    private static void handleRestitutionHaggle(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueRuntime.Session session,
            ForcedDialogueOption haggleOption,
            ForcedDialogueRuntime.GeneratedMenu menu) {
        double chance = restitutionHaggleChance(player.serverLevel(), villager, player);
        if (player.serverLevel().getRandom().nextDouble() < chance) {
            ForcedDialogueItemPayment reducedPayment = reducedPayment(player, villager, haggleOption.itemPayment());
            String line = forcedMessage(
                    player,
                    villager,
                    RESTITUTION_HAGGLE_ACCEPT_MESSAGE_KEY,
                    reducedPayment.removal().replacements());
            menu.open(restitutionDefinition(player, villager, session, haggleOption, line, reducedPayment, true));
            return;
        }

        String response = forcedMessage(
                player,
                villager,
                RESTITUTION_HAGGLE_DENY_MESSAGE_KEY,
                haggleOption.itemPayment().removal().replacements());
        menu.disable(haggleOption.id(), response);
    }

    private static ForcedDialogueDefinition restitutionDefinition(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueRuntime.Session session,
            ForcedDialogueOption sourceOption,
            String line,
            ForcedDialogueItemPayment payment,
            boolean reduced) {
        String baseId = sourceOption.id();
        if (baseId.endsWith(RESTITUTION_HAGGLE_SUFFIX)) {
            baseId = baseId.substring(0, baseId.length() - RESTITUTION_HAGGLE_SUFFIX.length());
        }
        if (baseId.endsWith(RESTITUTION_PAY_SUFFIX)) {
            baseId = baseId.substring(0, baseId.length() - RESTITUTION_PAY_SUFFIX.length());
        }

        List<ForcedDialogueOption> options = new ArrayList<>();
        options.add(dynamicOption(
                baseId + RESTITUTION_PAY_SUFFIX,
                forcedMessage(player, villager, RESTITUTION_PAY_LABEL_MESSAGE_KEY, payment.removal().replacements()),
                payment.successResponses().isEmpty()
                        ? forcedMessageList(player, villager, RESTITUTION_PAY_SUCCESS_MESSAGE_KEY, payment.removal().replacements())
                        : payment.successResponses(),
                sourceOption.reputationDelta(),
                false,
                0.0D,
                true,
                0,
                ForcedDialogueStolenItemReturn.empty(),
                paymentWithFailureMenu(player, villager, payment),
                sourceOption.reputationCondition()));
        if (!reduced) {
            options.add(dynamicOption(
                    baseId + RESTITUTION_HAGGLE_SUFFIX,
                    forcedMessage(player, villager, RESTITUTION_HAGGLE_LABEL_MESSAGE_KEY, payment.removal().replacements()),
                    List.of(),
                    0,
                    false,
                    0.0D,
                    false,
                    1,
                    ForcedDialogueStolenItemReturn.empty(),
                    payment,
                    sourceOption.reputationCondition()));
        }
        options.add(dynamicOption(
                baseId + RESTITUTION_REFUSE_SUFFIX,
                forcedMessage(player, villager, RESTITUTION_REFUSE_LABEL_MESSAGE_KEY, payment.removal().replacements()),
                forcedMessageList(player, villager, RESTITUTION_REFUSE_RESPONSE_MESSAGE_KEY, payment.removal().replacements()),
                -5,
                false,
                0.0D,
                true,
                2,
                ForcedDialogueStolenItemReturn.empty(),
                ForcedDialogueItemPayment.empty(),
                sourceOption.reputationCondition()));
        options.add(dynamicOption(
                baseId + RESTITUTION_THREATEN_SUFFIX,
                forcedMessage(player, villager, RESTITUTION_THREATEN_LABEL_MESSAGE_KEY, payment.removal().replacements()),
                forcedMessageList(player, villager, RESTITUTION_THREATEN_RESPONSE_MESSAGE_KEY, payment.removal().replacements()),
                -10,
                true,
                0.0D,
                true,
                3,
                ForcedDialogueStolenItemReturn.empty(),
                ForcedDialogueItemPayment.empty(),
                sourceOption.reputationCondition()));

        ForcedDialogueOption leaveOption = session.definition().leaveOption();
        options.add(leaveOption);
        return ForcedDialogueRuntime.definitionWithOptions(session.definition().id() + RESTITUTION_DEFINITION_SUFFIX, session.definition(), line, options, leaveOption);
    }

    private static ForcedDialogueOption dynamicOption(
            String id,
            String label,
            List<LocalizedText> responses,
            int reputationDelta,
            boolean aggro,
            double aggroChance,
            boolean endConversation,
            int order,
            ForcedDialogueStolenItemReturn stolenItemReturn,
            ForcedDialogueItemPayment itemPayment,
            VillagerReputationCondition reputationCondition) {
        return new ForcedDialogueOption(
                id,
                LocalizedText.inline(label),
                responses,
                reputationDelta,
                aggro,
                aggroChance,
                endConversation,
                order,
                stolenItemReturn,
                itemPayment,
                reputationCondition,
                SocialAttributeCondition.EMPTY,
                List.of(),
                ForcedDialogueFollowUp.empty());
    }

    private static List<LocalizedText> forcedMessageList(
            ServerPlayer player,
            Villager villager,
            String key,
            Map<String, String> replacements) {
        return List.of(LocalizedText.inline(forcedMessage(player, villager, key, replacements)));
    }

    private static String forcedMessage(
            ServerPlayer player,
            Villager villager,
            String key,
            Map<String, String> replacements) {
        DialogueContext context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
        return VillagerDialogueResources.message(context, key, replacements).orElse(key);
    }

    private static ForcedDialogueItemPayment paymentWithFailureMenu(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueItemPayment payment) {
        return new ForcedDialogueItemPayment(
                payment.removal(),
                payment.successResponses(),
                payment.failureResponses().isEmpty()
                        ? forcedMessageList(player, villager, RESTITUTION_PAY_FAILURE_MESSAGE_KEY, payment.removal().replacements())
                        : payment.failureResponses(),
                payment.successReputationDelta(),
                payment.failureReputationDelta(),
                false,
                false,
                payment.destination(),
                payment.overflowDestination(),
                payment.requireSpace());
    }

    private static ForcedDialogueItemPayment reducedPayment(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueItemPayment payment) {
        int reducedCount = Math.max(1, (int) Math.ceil(payment.removal().count() * 0.6D));
        VillagerInventoryItemRemoval removal = new VillagerInventoryItemRemoval(
                payment.removal().selectors(), reducedCount, payment.removal().stackPredicate());
        return new ForcedDialogueItemPayment(
                removal,
                forcedMessageList(player, villager, RESTITUTION_REDUCED_PAY_SUCCESS_MESSAGE_KEY, removal.replacements()),
                payment.failureResponses(),
                payment.successReputationDelta(),
                payment.failureReputationDelta(),
                false,
                false,
                payment.destination(),
                payment.overflowDestination(),
                payment.requireSpace());
    }

    private static double restitutionHaggleChance(ServerLevel level, Villager villager, ServerPlayer player) {
        ReputationSnapshot reputation = VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        int baseChance = switch (reputation.level()) {
            case ROYALTY -> 90;
            case REVERED -> 78;
            case RESPECTED -> 62;
            case TRUSTED -> 45;
            case NEUTRAL -> 24;
            case SUSPICIOUS -> 16;
            case HOSTILE -> 8;
            case DESPISED, FEARED -> 3;
        };
        if (VillagerSocialAttributeBehavior.enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_DIALOGUE_EFFECTS)) {
            int charm = VillagerSocialAttributeBehavior.value(level, villager, VillagerSocialAttribute.CHARM);
            int kindness = VillagerSocialAttributeBehavior.value(level, villager, VillagerSocialAttribute.KINDNESS);
            baseChance += (charm - 50) / 3;
            baseChance += (kindness - 50) / 5;
        }
        return Math.clamp(baseChance, 3, 95) / 100.0D;
    }

}
