package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

public final class SceneReceiptGuard {
    private static final String DURABLE_RECEIPTS = "campaign_receipts_persisted";
    private static final Set<SceneOperationReceipt> UNATTEMPTED = Collections.newSetFromMap(new WeakHashMap<>());

    private SceneReceiptGuard() { }

    public static ApplyResult applyOnce(SceneExecutionContext context, String suffix, SceneOperationReceipt.Kind kind,
            Runnable effect, String evidence) {
        SceneOperationReceipt receipt = prepare(context, suffix, kind);
        if (receipt.state() == SceneOperationReceipt.ReceiptState.COMPLETED || receipt.state() == SceneOperationReceipt.ReceiptState.APPLIED)
            return new ApplyResult(Status.ALREADY_APPLIED, receipt);
        if (!beginAttempt(context, receipt, false)) return new ApplyResult(Status.AMBIGUOUS_PREPARED, receipt);
        effect.run();
        receipt.applied(context.gameTime(), evidence);
        context.repository().changed();
        persist(context);
        return new ApplyResult(Status.APPLIED, receipt);
    }

    public static ActionApplyResult applyActionOnce(SceneExecutionContext context, String suffix,
            SceneOperationReceipt.Kind kind, Supplier<ActionResult> effect) {
        return applyActionOnce(context, suffix, kind, effect, false);
    }

    public static ActionApplyResult applyActionOnce(SceneExecutionContext context, String suffix,
            SceneOperationReceipt.Kind kind, Supplier<ActionResult> effect, boolean recoverPrepared) {
        SceneOperationReceipt receipt = prepare(context, suffix, kind);
        if (receipt.actionResult().suspended()) {
            ActionResult waiting = receipt.actionResult();
            ActionResult result = waiting.suspensionKind() == ActionResult.SuspensionKind.CAMPAIGN_REWARD
                    ? com.jvn.villagerretaliation.campaign.CampaignRewardRecovery.poll(context.server(), waiting)
                    : SceneActionSuspension.poll(context.repository(), waiting);
            if (result.suspended()) return new ActionApplyResult(result, receipt, false);
            if (requiresPersistence(context.instance())) {
                checkpoint(context);
                if (result.success() && waiting.retryOnResume()) {
                    receipt.prepareRetry();
                    UNATTEMPTED.add(receipt);
                    beginAttempt(context, receipt, false);
                }
            }
            if (result.success() && waiting.retryOnResume()) result = effect.get();
            return recordAction(context, receipt, result);
        }
        if (receipt.state() != SceneOperationReceipt.ReceiptState.PREPARED) {
            if (receipt.state() == SceneOperationReceipt.ReceiptState.APPLIED) {
                receipt.completed(context.gameTime(), receipt.evidence());
                context.repository().changed();
                persist(context);
            }
            return new ActionApplyResult(receipt.actionResult(), receipt, false);
        }
        if (!beginAttempt(context, receipt, recoverPrepared)) {
            return new ActionApplyResult(ActionResult.failed("effect receipt is ambiguous after reload", Set.of()),
                    receipt, true);
        }
        context.repository().changed();
        return recordAction(context, receipt, effect.get());
    }

    private static SceneOperationReceipt prepare(SceneExecutionContext context, String suffix, SceneOperationReceipt.Kind kind) {
        retryPending(context.server(), context.repository(), context.instance());
        boolean missing = context.instance().receipt(context.operationId(suffix)) == null;
        SceneOperationReceipt receipt = context.prepareReceipt(suffix, kind);
        if (requiresPersistence(context.instance()) && missing && (context.preparedThisSession()
                || "1".equals(context.record().durableValues().get(DURABLE_RECEIPTS)))) {
            UNATTEMPTED.add(receipt);
        }
        return receipt;
    }

    private static boolean beginAttempt(SceneExecutionContext context, SceneOperationReceipt receipt, boolean recoverPrepared) {
        if (!requiresPersistence(context.instance())) return context.preparedThisSession() || recoverPrepared;
        if (!UNATTEMPTED.contains(receipt) && !recoverPrepared) return false;
        checkpoint(context);
        persist(context);
        UNATTEMPTED.remove(receipt);
        return true;
    }

    private static void checkpoint(SceneExecutionContext context) {
        context.record().putDurableValue(DURABLE_RECEIPTS, "1");
        context.repository().changed();
    }

    private static ActionApplyResult recordAction(SceneExecutionContext context,
            SceneOperationReceipt receipt, ActionResult result) {
        if (result.suspended() && SceneActionSuspension.dependsOnScene(context.repository(), result, context.instance().id())) {
            result = ActionResult.failed("cyclic scene action suspension", result.capabilities());
        }
        receipt.recordAction(result, context.gameTime());
        context.repository().changed();
        persist(context);
        return new ActionApplyResult(result, receipt, false);
    }

    static void retryPending(MinecraftServer server, SceneSavedData data, SceneInstance instance) {
        if (requiresPersistence(instance) && data.campaignPersistencePending()) persist(server, data);
    }

    public static boolean requiresPersistence(SceneInstance instance) {
        return instance.owningCampaignId() != null || !instance.conversationEffect().isEmpty() || !instance.campaignConsent().isEmpty();
    }

    private static void persist(SceneExecutionContext context) {
        if (requiresPersistence(context.instance())) persist(context.server(), context.repository());
    }

    private static void persist(MinecraftServer server, SceneSavedData data) {
        try {
            data.persistCampaignHandoff(server.overworld());
        } catch (IOException exception) {
            throw new PersistenceWait(exception);
        }
    }

    static void clearRuntimeState() { UNATTEMPTED.clear(); }

    public static final class PersistenceWait extends RuntimeException {
        private PersistenceWait(IOException cause) { super("Campaign scene receipts could not be saved. Restore writable world storage to continue.", cause); }
    }

    public record ActionApplyResult(ActionResult outcome, SceneOperationReceipt receipt, boolean ambiguous) {
    }

    public enum Status { APPLIED, ALREADY_APPLIED, AMBIGUOUS_PREPARED }
    public record ApplyResult(Status status, SceneOperationReceipt receipt) { }
}
