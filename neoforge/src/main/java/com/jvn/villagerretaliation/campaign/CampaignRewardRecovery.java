package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class CampaignRewardRecovery {
    private static final Map<MinecraftServer, Fingerprints> FINGERPRINTS = new WeakHashMap<>();

    private CampaignRewardRecovery() { }

    public static String fingerprint(MinecraftServer server, ResourceLocation quest) {
        if (server == null || quest == null) return "";
        var catalog = QuestContentCatalogs.current(server);
        var cache = FINGERPRINTS.get(server);
        if (cache == null || cache.catalog() != catalog) {
            cache = new Fingerprints(catalog, new HashMap<>());
            FINGERPRINTS.put(server, cache);
        }
        return cache.values().computeIfAbsent(quest, id -> catalog.bundles().values().stream()
                .filter(bundle -> id.equals(bundle.questId())).findFirst().map(QuestBundleFingerprints::structural).orElse(""));
    }

    public static String issue(MinecraftServer server, SceneContinuation continuation) {
        if (!continuation.campaignRecovery()) return "";
        if (!continuation.recoveryIssue().isBlank()) return continuation.recoveryIssue();
        if (continuation.questId() == null || continuation.campaignId() == null) {
            return "Campaign reward recovery is missing its saved quest ownership. Restore matching scene and quest saves.";
        }
        if (!continuation.recoveryFingerprint().equals(fingerprint(server, continuation.questId()))) {
            return "The quest bundle changed during reward recovery. Restore its original content before continuing.";
        }
        int prepared = continuation.preparedRecoveryAction();
        if (prepared >= 0 && prepared < continuation.actions().size()) {
            var kind = continuation.actions().get(prepared).kind();
            if (kind != VillagerActionDefinition.Kind.CAMPAIGN_REWARD
                    && kind != VillagerActionDefinition.Kind.CAMPAIGN_FACT
                    && kind != VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME) {
                return "Campaign action " + prepared + " has no saved completion receipt. Inspect its effects before repairing this continuation.";
            }
        }
        return "";
    }

    public static ActionResult poll(MinecraftServer server, ActionResult waiting) {
        if (waiting.suspensionKind() != ActionResult.SuspensionKind.CAMPAIGN_REWARD || !waiting.suspended()) return waiting;
        if (server == null || waiting.suspensionId() == null) return waiting;
        var rewards = CampaignRewardSavedData.get(server.overworld());
        if (!rewards.diagnostic().isBlank()) return waiting;
        var receipt = rewards.receipts().get(waiting.suspensionId());
        return receipt != null && receipt.delivered() || server.overworld().getGameTime() % 100L == 0L
                ? ActionResult.alreadySatisfied("Retry the saved campaign reward action.", Set.of(), VillagerActionResult.EMPTY) : waiting;
    }

    public static boolean persist(MinecraftServer server, SceneSavedData data, SceneContinuation continuation) {
        if (!continuation.campaignRecovery()) return true;
        continuation.recoveryPersistenceIssue("Campaign actions are waiting for their recovery state to be saved.");
        try {
            data.persistCampaignHandoff(server.overworld());
            continuation.recoveryPersistenceIssue("");
            return true;
        } catch (IOException exception) {
            continuation.recoveryPersistenceIssue("Campaign action recovery is waiting for world storage: " + exception.getMessage());
            data.changed();
            return false;
        }
    }

    private record Fingerprints(QuestContentCatalog catalog, Map<ResourceLocation, String> values) { }
}
