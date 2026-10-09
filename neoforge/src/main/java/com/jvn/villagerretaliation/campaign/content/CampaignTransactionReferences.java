package com.jvn.villagerretaliation.campaign.content;

import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.List;

final class CampaignTransactionReferences {
    private CampaignTransactionReferences() { }

    static List<QuestDiagnostic> validate(CampaignContentCatalog catalog) {
        var collector = new CampaignReferenceCollector(catalog);
        collector.collect();
        return collector.transactionIssues();
    }
}
