package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignPaymentService {
    private CampaignPaymentService() { }

    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        if (context == null || action.questId() == null || action.campaignPayment() == null)
            return ActionResult.failed("Campaign payment is missing its owning quest or typed transaction.", capabilities);
        String issue = CampaignAuthority.controlIssue(context.player());
        if (issue.isBlank()) issue = ConversationSceneExecution.paymentIssue(context, action.questId(), action.campaignPayment().transaction());
        if (!issue.isBlank()) return ActionResult.unavailable(issue, capabilities, VillagerActionResult.EMPTY);
        var reading = CampaignTransactions.read(context, action.questId(), action.campaignPayment().transaction());
        if (!reading.available()) return ActionResult.unavailable(reading.diagnostic(), capabilities, VillagerActionResult.EMPTY);
        try {
            var data = CampaignTransactionSavedData.get(context.level());
            var receipt = data.preparePayment(reading.terms(), context.level().getGameTime());
            boolean applied = deliver(context.player(), data, receipt, CampaignPlayerRewards.directory(context.player()));
            var output = new VillagerActionResult(applied, "", "", receipt.replacements(context.player().getName().getString()), false);
            return applied ? ActionResult.success(output, capabilities)
                    : ActionResult.alreadySatisfied("This campaign transaction has already been paid.", capabilities, output);
        } catch (IllegalArgumentException exception) {
            return ActionResult.unavailable(exception.getMessage(), capabilities, VillagerActionResult.EMPTY);
        } catch (IOException exception) {
            return ActionResult.unavailable("The campaign payment could not be saved. Retry after world storage recovers: "
                    + exception.getMessage(), capabilities, VillagerActionResult.EMPTY);
        }
    }

    static boolean deliver(ServerPlayer player, CampaignTransactionSavedData data, CampaignTransaction.Receipt receipt, Path directory) throws IOException {
        boolean applied = CampaignPlayerRewards.deliver(player, receipt.payment(), directory);
        data.finish(receipt.operation(), receipt.token(), player.getUUID(), player.serverLevel().getGameTime());
        return applied;
    }
}
