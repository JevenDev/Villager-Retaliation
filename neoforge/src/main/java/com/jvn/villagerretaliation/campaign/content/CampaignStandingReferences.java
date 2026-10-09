package com.jvn.villagerretaliation.campaign.content;

import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class CampaignStandingReferences {
    private CampaignStandingReferences() { }

    static List<QuestDiagnostic> validate(CampaignContentCatalog catalog) {
        var collector = new CampaignReferenceCollector(catalog);
        var report = collector.collect();
        var kinds = Set.of(CampaignReferenceReport.Kind.RELATIONSHIP, CampaignReferenceReport.Kind.FACTION, CampaignReferenceReport.Kind.REACTION);
        var issues = new ArrayList<QuestDiagnostic>(collector.standingIssues());
        for (var reference : report.references()) {
            if (!kinds.contains(reference.to().kind()) || !report.nodes().get(reference.to()).missing()) continue;
            var source = reference.source();
            issues.add(CampaignContentCatalog.inPack(CampaignSchema.error(source.resource(), source.pointer(),
                    "unknown campaign standing reference " + reference.to().text()), source.pack()));
        }
        return List.copyOf(issues);
    }
}
