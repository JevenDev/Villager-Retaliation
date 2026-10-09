package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignFixtureEntry;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class CampaignFixtureSetup {
    private final CampaignContentCatalog.Entry content;
    private final CampaignFixtureService.Request request;
    private final CampaignInstance original;
    private final long time;
    private final Map<String, CampaignInstance.OutcomeCommit> outcomes;
    private final Map<UUID, CampaignInstance.PlayerRecord> players;
    private final ArrayList<CampaignFixtureReceipt.Effect> effects = new ArrayList<>();
    private final HashSet<UUID> operations = new HashSet<>();
    private long worldRevision;
    private CampaignFacts facts;

    private CampaignFixtureSetup(CampaignContentCatalog.Entry content, CampaignInstance current, CampaignFixtureService.Request request, long time) {
        this.content = content;
        this.request = request;
        this.time = time;
        var definition = content.definition();
        original = current == null ? new CampaignInstance(definition.id(),
                UUID.nameUUIDFromBytes((definition.id() + "/fixture/" + request.operation()).getBytes(StandardCharsets.UTF_8)),
                definition.revision(), definition.fingerprint(), title(definition.titleKey()), time, 0, Map.of(), Map.of()) : current;
        outcomes = new LinkedHashMap<>(original.outcomes());
        players = new LinkedHashMap<>(original.players());
        worldRevision = original.worldRevision();
        facts = original.facts();
        outcomes.values().forEach(outcome -> operations.add(outcome.operationId()));
        operations.addAll(facts.receipts().keySet());
        players.values().forEach(player -> player.milestones().values().forEach(progress -> {
            if (progress.completed()) operations.add(progress.completionOperation());
        }));
        original.fixtureEntries().values().forEach(receipt -> {
            operations.add(receipt.operation());
            receipt.effects().stream().filter(effect -> effect.kind() == CampaignFixtureReceipt.Kind.QUEST).forEach(effect -> operations.add(effect.operation()));
        });
        CampaignStateCodec.require(operations.add(request.operation()), "Fixture operation already belongs to another campaign effect.");
    }

    static CampaignInstance prepare(CampaignContentCatalog.Entry content, CampaignInstance current, CampaignFixtureService.Request request, long time) {
        var setup = new CampaignFixtureSetup(content, current, request, time);
        var entry = content.definition().fixtureEntries().get(request.entry());
        CampaignStateCodec.require(entry != null, "Choose an authored fixture entry.");
        for (var effect : entry.effects()) setup.apply(effect);
        if (!setup.players.containsKey(request.player())) setup.players.put(request.player(), new CampaignInstance.PlayerRecord(1, java.util.Set.of(), Map.of()));
        var audit = new CampaignFixtureReceipt(request.operation(), request.expected(), entry.id(), entry.chapter(), request.player(),
                request.operator(), request.reason(), time, setup.effects);
        var audits = new LinkedHashMap<>(setup.original.fixtureEntries());
        audits.put(audit.operation(), audit);
        var result = new CampaignInstance(setup.original.campaignId(), setup.original.instanceId(), setup.original.definitionRevision(),
                setup.original.fingerprint(), setup.original.title(), setup.original.createdAt(), setup.worldRevision, setup.outcomes, setup.players, setup.facts, audits);
        var access = CampaignProgression.chapter(content.definition(), result, request.player(), entry.chapter());
        CampaignStateCodec.require(access.available(), "Fixture chapter remains unavailable: " + String.join(", ", access.blockers()));
        CampaignStateCodec.load(CampaignStateCodec.save(result));
        return result;
    }

    private void apply(CampaignFixtureEntry.Effect effect) {
        UUID operation = CampaignFixtureReceipt.effectOperation(request.operation(), effect.id());
        CampaignStateCodec.require(operations.add(operation), "Fixture effect operation already belongs to saved history: " + effect.id());
        if (effect instanceof CampaignFixtureEntry.WorldOutcome outcome) world(outcome, operation);
        else if (effect instanceof CampaignFixtureEntry.CompleteMilestone completion) milestone(completion, operation);
        else if (effect instanceof CampaignFixtureEntry.CompleteQuest completion) quest(completion, operation);
        else if (effect instanceof CampaignFixtureEntry.SetFact value) fact(value, operation);
    }

    private void world(CampaignFixtureEntry.WorldOutcome effect, UUID operation) {
        var prior = outcomes.get(effect.milestone());
        if (prior != null) {
            CampaignStateCodec.require(prior.outcome().equals(effect.outcome()), "World milestone " + effect.milestone() + " already resolved as " + prior.outcome() + ".");
            effects.add(new CampaignFixtureReceipt.Effect(effect.id(), CampaignFixtureReceipt.Kind.WORLD_OUTCOME, effect.milestone(), effect.outcome(), prior.operationId(), false));
            return;
        }
        var definition = content.definition().milestones().get(effect.milestone());
        CampaignStateCodec.require(definition != null && definition.irreversible() && definition.outcomes().containsKey(effect.outcome()), "Fixture world outcome is unavailable.");
        var committed = new CampaignInstance.OutcomeCommit(operation, effect.milestone(), effect.outcome(),
                title(definition.outcomes().get(effect.outcome()).titleKey()), next(worldRevision), time, CampaignInstance.Origin.OPERATOR_FIXTURE);
        worldRevision = committed.revision();
        outcomes.put(effect.milestone(), committed);
        for (var entry : new ArrayList<>(players.entrySet())) {
            var priorPlayer = entry.getValue();
            if (!priorPlayer.milestones().containsKey(effect.milestone())) continue;
            var milestones = new LinkedHashMap<>(priorPlayer.milestones());
            milestones.put(effect.milestone(), CampaignProgression.reconcile(milestones.get(effect.milestone()), committed, false));
            var discoveries = new LinkedHashMap<>(priorPlayer.discoveredOutcomes());
            discoveries.put(effect.milestone(), effect.outcome());
            players.put(entry.getKey(), new CampaignInstance.PlayerRecord(next(priorPlayer.revision()), priorPlayer.participation(), discoveries,
                    milestones, priorPlayer.completedQuests()));
        }
        effects.add(new CampaignFixtureReceipt.Effect(effect.id(), CampaignFixtureReceipt.Kind.WORLD_OUTCOME, effect.milestone(), effect.outcome(), operation, true));
    }

    private void milestone(CampaignFixtureEntry.CompleteMilestone effect, UUID operation) {
        var definition = content.definition().milestones().get(effect.milestone());
        CampaignStateCodec.require(definition != null && definition.outcomes().containsKey(effect.outcome()), "Fixture personal outcome is unavailable.");
        var player = player();
        var progress = player.milestones().get(effect.milestone());
        if (progress != null && progress.completed()) {
            CampaignStateCodec.require(progress.outcome().equals(effect.outcome()), "Personal milestone " + effect.milestone() + " already resolved as " + progress.outcome() + ".");
            effects.add(new CampaignFixtureReceipt.Effect(effect.id(), CampaignFixtureReceipt.Kind.MILESTONE, effect.milestone(), effect.outcome(), progress.completionOperation(), false));
            return;
        }
        var committed = outcomes.get(effect.milestone());
        CampaignStateCodec.require(!definition.irreversible() || committed != null && committed.outcome().equals(effect.outcome()),
                "Fixture personal completion requires its matching world outcome.");
        if (progress == null) progress = CampaignProgression.enter(committed, time);
        var milestones = new LinkedHashMap<>(player.milestones());
        milestones.put(effect.milestone(), new CampaignInstance.MilestoneProgress(progress.route(), effect.outcome(),
                committed == null ? null : committed.operationId(), progress.enteredAt(), operation, time));
        var discoveries = new LinkedHashMap<>(player.discoveredOutcomes());
        if (committed != null) discoveries.put(effect.milestone(), committed.outcome());
        players.put(request.player(), new CampaignInstance.PlayerRecord(next(player.revision()), player.participation(), discoveries, milestones,
                CampaignProgression.completedQuests(content.definition(), milestones, player.completedQuests())));
        effects.add(new CampaignFixtureReceipt.Effect(effect.id(), CampaignFixtureReceipt.Kind.MILESTONE, effect.milestone(), effect.outcome(), operation, true));
    }

    private void quest(CampaignFixtureEntry.CompleteQuest effect, UUID operation) {
        CampaignStateCodec.require(content.definition().questBundles().contains(effect.quest()), "Fixture quest is unavailable.");
        var player = player();
        boolean applied = !player.completedQuests().contains(effect.quest());
        if (applied) {
            var completed = new HashSet<>(player.completedQuests());
            completed.add(effect.quest());
            players.put(request.player(), new CampaignInstance.PlayerRecord(next(player.revision()), player.participation(),
                    player.discoveredOutcomes(), player.milestones(), CampaignProgression.completedQuests(content.definition(), player.milestones(), completed)));
        }
        effects.add(new CampaignFixtureReceipt.Effect(effect.id(), CampaignFixtureReceipt.Kind.QUEST, effect.quest().toString(), "", operation, applied));
    }

    private void fact(CampaignFixtureEntry.SetFact effect, UUID operation) {
        var definition = content.definition().facts().get(effect.fact());
        CampaignStateCodec.require(definition != null, "Fixture story fact is unavailable.");
        var value = new CampaignFacts.Value(definition.kind(), effect.value());
        var player = player();
        boolean shared = definition.scope() == CampaignDefinition.FactScope.WORLD;
        long revision = next(shared ? worldRevision : player.revision());
        var receipt = new CampaignFacts.Receipt(operation, request.player(), effect.fact(), definition.scope(), CampaignFacts.Mutation.SET,
                value, value, revision, time);
        facts = facts.apply(receipt);
        if (shared) worldRevision = revision;
        else players.put(request.player(), new CampaignInstance.PlayerRecord(revision, player.participation(),
                player.discoveredOutcomes(), player.milestones(), player.completedQuests()));
        effects.add(new CampaignFixtureReceipt.Effect(effect.id(), CampaignFixtureReceipt.Kind.FACT, effect.fact(), effect.value(), operation, true));
    }

    private CampaignInstance.PlayerRecord player() { return players.getOrDefault(request.player(), CampaignInstance.PlayerRecord.empty()); }
    private String title(String key) { return content.localization().plainText(QuestLocaleCatalog.ENGLISH, key).orElse(key); }
    private static long next(long revision) {
        CampaignStateCodec.require(revision < Long.MAX_VALUE, "Campaign revision is exhausted.");
        return revision + 1;
    }
}
