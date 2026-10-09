package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class CampaignTransactionInspection {
    private CampaignTransactionInspection() { }

    public static Report inspect(MinecraftServer server, ResourceLocation campaign) {
        if (!server.isSameThread()) throw new IllegalStateException("Transaction inspection requires the server thread.");
        var data = CampaignTransactionSavedData.get(server.overworld());
        var content = CampaignResources.current(server).campaigns().get(campaign);
        var instance = CampaignSavedData.get(server.overworld()).instances().get(campaign);
        var pending = data.pendingReceipts();
        var receipts = new LinkedHashMap<>(data.receipts());
        receipts.putAll(pending);
        var diagnostics = new ArrayList<String>();
        if (!data.diagnostic().isBlank()) diagnostics.add(data.diagnostic());
        var rows = new ArrayList<Entry>();
        receipts.values().stream().filter(receipt -> receipt.terms().key().campaign().equals(campaign))
                .sorted(java.util.Comparator.comparing(receipt -> receipt.operation().toString())).forEach(receipt -> {
            var terms = receipt.terms();
            var issues = new ArrayList<String>();
            if (!data.issue(receipt.operation()).isBlank()) issues.add(data.issue(receipt.operation()));
            if (content == null) issues.add("Campaign content is missing or invalid. Saved transaction terms are retained.");
            else if (terms.definitionRevision() != content.definition().revision() || !terms.fingerprint().equals(content.definition().fingerprint()))
                issues.add("The saved transaction requires its original campaign definition.");
            else {
                var authored = content.definition().transactions().get(terms.key().transaction());
                if (authored == null || !authored.quest().equals(terms.key().quest()))
                    issues.add("The saved transaction's owning quest or declaration is unavailable.");
            }
            if (instance == null || !instance.instanceId().equals(terms.key().instance()))
                issues.add("Restore the transaction's original campaign instance.");
            var binding = CampaignCharacterSavedData.get(server.overworld()).bindings().get(new CampaignCharacterBinding.Key(campaign, terms.character()));
            if (binding == null || !binding.entityId().equals(terms.seller()))
                issues.add("The saved seller binding is missing or changed. Restore its identity before continuing.");
            rows.add(new Entry(receipt, !pending.containsKey(receipt.operation()), issues));
        });
        for (var session : ConversationSavedData.get(server.overworld()).snapshots().values()) {
            if (!session.identity().campaign().equals(campaign)) continue;
            session.transactionChecks().forEach((check, operation) -> {
                if (!receipts.containsKey(operation)) diagnostics.add("Conversation " + session.identity().id() + ", check " + check
                        + " references missing transaction " + operation + ". Restore matching transaction data.");
            });
        }
        return new Report(rows, diagnostics);
    }

    public record Entry(CampaignTransaction.Receipt receipt, boolean persisted, List<String> diagnostics) {
        public Entry { diagnostics = List.copyOf(diagnostics); }
    }

    public record Report(List<Entry> transactions, List<String> diagnostics) {
        public Report { transactions = List.copyOf(transactions); diagnostics = List.copyOf(diagnostics); }
    }
}
