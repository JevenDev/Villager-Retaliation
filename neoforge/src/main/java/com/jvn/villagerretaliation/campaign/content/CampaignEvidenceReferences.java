package com.jvn.villagerretaliation.campaign.content;

import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.List;

final class CampaignEvidenceReferences {
    private CampaignEvidenceReferences() { }

    static List<QuestDiagnostic> validate(CampaignContentCatalog catalog) {
        var report = new CampaignReferenceCollector(catalog).collect();
        var issues = new ArrayList<QuestDiagnostic>();
        for (var reference : report.references()) {
            if (reference.to().kind() != CampaignReferenceReport.Kind.EVIDENCE || !report.nodes().get(reference.to()).missing()) continue;
            var source = reference.source();
            issues.add(CampaignContentCatalog.inPack(CampaignSchema.error(source.resource(), source.pointer(),
                    "unknown campaign evidence reference " + reference.to().text()), source.pack()));
        }
        return List.copyOf(issues);
    }
}
