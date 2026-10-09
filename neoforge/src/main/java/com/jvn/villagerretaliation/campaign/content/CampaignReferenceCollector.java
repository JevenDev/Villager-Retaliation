package com.jvn.villagerretaliation.campaign.content;

import static com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.Kind.*;
import static com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.Relation.*;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.CampaignStoryObjective;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.*;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.QuestSourcePointer;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

final class CampaignReferenceCollector {
    private final CampaignContentCatalog content;
    private final Map<Key, LinkedHashSet<Source>> declarations = new LinkedHashMap<>();
    private final LinkedHashSet<Reference> references = new LinkedHashSet<>();
    private final List<com.jvn.villagerretaliation.quest.debug.QuestDiagnostic> standingIssues = new ArrayList<>();
    private final Map<ResourceLocation, LinkedHashSet<ResourceLocation>> sceneCampaigns = new LinkedHashMap<>();

    CampaignReferenceCollector(CampaignContentCatalog content) { this.content = content; }

    CampaignReferenceReport collect() {
        content.campaigns().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> campaign(entry.getValue()));
        content.quests().compiledQuests().stream().sorted(java.util.Comparator.comparing(CompiledQuest::id)).forEach(this::quest);
        content.quests().conversations().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> conversation(entry.getValue()));
        content.quests().socialProgressions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var definition = entry.getValue();
            var at = new Source(definition.source().resource(), definition.source().pack(), "");
            var owner = declare(SOCIAL_PROGRESSION, definition.id(), at);
            int index = 0;
            for (var skill : definition.skills().keySet()) {
                var where = at.child("skills", Integer.toString(index++));
                ref(owner, declare(SOCIAL_SKILL, definition.id(), where, skill.id()), INCLUDES, where);
            }
        });
        scenes();
        var nodes = new LinkedHashMap<Key, Node>();
        declarations.forEach((key, sources) -> nodes.put(key, new Node(key, List.copyOf(sources))));
        return new CampaignReferenceReport(content.quests().generation(), nodes, List.copyOf(references));
    }

    List<com.jvn.villagerretaliation.quest.debug.QuestDiagnostic> standingIssues() { return List.copyOf(standingIssues); }

    private Key key(Kind kind, ResourceLocation owner, String... path) {
        if (owner == null || java.util.Arrays.stream(path).anyMatch(value -> value == null || value.isBlank())) return null;
        return Key.of(kind, owner, path);
    }

    private Key declare(Kind kind, ResourceLocation owner, Source source, String... path) {
        Key key = key(kind, owner, path);
        if (key != null) declarations.computeIfAbsent(key, unused -> new LinkedHashSet<>()).add(source);
        return key;
    }

    private void ref(Key from, Key to, Relation relation, Source source) {
        if (from == null || to == null) return;
        declarations.computeIfAbsent(from, unused -> new LinkedHashSet<>());
        declarations.computeIfAbsent(to, unused -> new LinkedHashSet<>());
        references.add(new Reference(from, to, relation, source));
    }

    private static Source source(QuestSourcePointer pointer) {
        return new Source(pointer.resource(), pointer.sourcePackId(), pointer.jsonPointer());
    }

    private void campaign(CampaignContentCatalog.Entry entry) {
        var definition = entry.definition();
        var id = definition.id();
        var source = new Source(definition.source(), entry.sourcePack(), "");
        var owner = declare(CAMPAIGN, id, source);
        for (int index = 0; index < definition.questBundles().size(); index++)
            ref(owner, key(QUEST, definition.questBundles().get(index)), INCLUDES, source.child("quest_bundles", Integer.toString(index)));
        for (var chapter : definition.chapters().values()) {
            var at = new Source(source.resource(), source.pack(), chapter.pointer());
            var node = declare(CHAPTER, id, at, chapter.id());
            ref(owner, node, INCLUDES, at);
            for (int index = 0; index < chapter.quests().size(); index++)
                ref(node, key(QUEST, chapter.quests().get(index)), INCLUDES, at.child("quests", Integer.toString(index)));
            for (var prerequisite : chapter.prerequisites()) {
                var where = new Source(source.resource(), source.pack(), prerequisite.pointer());
                ref(node, key(CHAPTER, id, prerequisite.chapter()), REQUIRES, where.child("chapter"));
                ref(node, key(MILESTONE, id, prerequisite.milestone()), REQUIRES, where.child("milestone"));
                ref(node, key(OUTCOME, id, prerequisite.milestone(), prerequisite.outcome()), REQUIRES, where.child("outcome"));
            }
        }
        for (var entryPoint : definition.fixtureEntries().values()) {
            var at = new Source(source.resource(), source.pack(), entryPoint.pointer());
            var node = declare(FIXTURE, id, at, entryPoint.id());
            ref(owner, node, INCLUDES, at);
            ref(node, key(CHAPTER, id, entryPoint.chapter()), STARTS, at.child("chapter"));
            for (var effect : entryPoint.effects()) {
                var where = new Source(source.resource(), source.pack(), effect.pointer());
                if (effect instanceof CampaignFixtureEntry.WorldOutcome outcome)
                    outcome(node, id, outcome.milestone(), outcome.outcome(), COMMITS, where);
                else if (effect instanceof CampaignFixtureEntry.CompleteMilestone completion)
                    outcome(node, id, completion.milestone(), completion.outcome(), WRITES, where);
                else if (effect instanceof CampaignFixtureEntry.CompleteQuest completion)
                    ref(node, key(QUEST, completion.quest()), WRITES, where.child("quest"));
                else if (effect instanceof CampaignFixtureEntry.SetFact fact)
                    ref(node, key(FACT, id, fact.fact()), WRITES, where.child("fact"));
            }
        }
        for (var milestone : definition.milestones().values()) {
            var at = new Source(source.resource(), source.pack(), milestone.pointer());
            var node = declare(MILESTONE, id, at, milestone.id());
            ref(owner, node, INCLUDES, at);
            ref(node, key(CHAPTER, id, milestone.chapter()), REQUIRES, at.child("chapter"));
            ref(node, key(QUEST, milestone.quest()), REQUIRES, at.child("quest"));
            for (var outcome : milestone.outcomes().values()) {
                var where = new Source(source.resource(), source.pack(), outcome.pointer());
                var target = declare(OUTCOME, id, where, milestone.id(), outcome.id());
                ref(node, target, INCLUDES, where);
                for (int index = 0; index < outcome.locationVariants().size(); index++)
                    ref(target, key(VARIANT, id, outcome.locationVariants().get(index)), CHANGES, where.child("location_variants", Integer.toString(index)));
            }
            if (milestone.catchUp() != null) ref(node, key(QUEST, milestone.catchUp().quest()), CATCH_UP,
                    new Source(source.resource(), source.pack(), milestone.catchUp().pointer()).child("quest"));
            if (milestone.observation() != null) {
                var observation = milestone.observation();
                var where = new Source(source.resource(), source.pack(), observation.pointer());
                ref(node, key(OUTCOME, id, milestone.id(), observation.outcome()), OBSERVES, where.child("outcome"));
                ref(node, key(CHARACTER, id, observation.character()), OBSERVES, where.child("character"));
            }
        }
        for (var character : definition.characters().values()) {
            var at = new Source(source.resource(), source.pack(), character.pointer());
            var node = declare(CHARACTER, id, at, character.id());
            ref(node, key(LOCATION, id, character.location()), LOCATED_AT, at.child("location"));
            ref(node, key(CHARACTER, id, character.replacementRole()), REPLACES, at.child("replacement_role"));
        }
        CampaignStandingDefinition.all(definition).forEach(standing -> {
            var at = new Source(source.resource(), source.pack(), standing.pointer());
            var node = declare(standingKind(standing.scope()), id, at, standing.id());
            ref(owner, node, INCLUDES, at);
            if (standing.scope() == CampaignStandingDefinition.Scope.CHARACTER)
                ref(node, key(CHARACTER, id, standing.id()), REQUIRES, at.child("id"));
            standing.reactions().values().forEach(reaction -> {
                var where = new Source(source.resource(), source.pack(), reaction.pointer());
                ref(node, declare(REACTION, id, where, standing.scope().name().toLowerCase(java.util.Locale.ROOT), standing.id(), reaction.id()), INCLUDES, where);
            });
        });
        definition.locations().values().forEach(location -> declare(LOCATION, id,
                new Source(source.resource(), source.pack(), location.pointer()), location.id()));
        definition.facts().values().forEach(fact -> declare(FACT, id,
                new Source(source.resource(), source.pack(), fact.pointer()), fact.id()));
        for (var evidence : definition.evidence().values()) {
            var at = new Source(source.resource(), source.pack(), evidence.pointer());
            ref(declare(EVIDENCE, id, at, evidence.id()), key(LOCATION, id, evidence.location()), LOCATED_AT, at.child("location"));
        }
        for (var variant : definition.locationVariants().values()) {
            var at = new Source(source.resource(), source.pack(), variant.pointer());
            ref(declare(VARIANT, id, at, variant.id()), key(LOCATION, id, variant.location()), CHANGES, at.child("location"));
        }
        for (var reward : definition.rewards().values()) {
            var at = new Source(source.resource(), source.pack(), reward.pointer());
            var node = declare(REWARD, id, at, reward.id());
            ref(node, key(QUEST, reward.quest()), REQUIRES, at.child("quest"));
            ref(node, key(MILESTONE, id, reward.milestone()), REQUIRES, at.child("milestone"));
            ref(node, key(OUTCOME, id, reward.milestone(), reward.outcome()), REQUIRES, at.child("outcome"));
        }
    }

    private void quest(CompiledQuest quest) {
        var at = source(quest.source());
        var owner = declare(QUEST, quest.id(), at);
        var campaign = quest.metadata().campaign();
        ref(owner, key(QUEST, quest.metadata().parent()), REQUIRES, at);
        quest.prerequisites().forEach(id -> ref(owner, key(QUEST, id), REQUIRES, at));
        quest.rules().branching().blocksOnStart().stream().sorted().forEach(id -> ref(owner, key(QUEST, id), BLOCKS_ON_START, at));
        quest.rules().branching().blocksOnCompletion().stream().sorted().forEach(id -> ref(owner, key(QUEST, id), BLOCKS_ON_COMPLETION, at));
        ref(owner, key(CHARACTER, campaign, quest.provider().campaignRole()), PROVIDER, at.child("provider", "campaign_role"));
        conditions(owner, quest.provider().offer().conditions(), at);
        conditions(owner, quest.rules().activeState().conditions(), at);
        conditions(owner, quest.rules().expiration().conditions(), at);
        quest.ui().dialogue().variants().values().forEach(variants ->
                variants.forEach(variant -> conditions(owner, variant.conditions(), at)));
        for (var objective : quest.objectives()) {
            var where = source(objective.source());
            var definition = objective.definition();
            conditions(owner, definition.conditions(), where);
            ref(owner, key(QUEST, definition.factQuestId()), READS, where);
            ref(owner, key(LOCATION, campaign, definition.campaignLocation()), REQUIRES, where.child("campaign_location"));
            var story = definition.story();
            if (story instanceof CampaignStoryObjective.Outcome outcome) {
                outcome(owner, campaign, outcome.milestone(), outcome.outcome(), REQUIRES, where.child("story"));
            } else if (story instanceof CampaignStoryObjective.Speak speak) {
                ref(owner, key(CHARACTER, campaign, speak.role()), SPEAKS, where.child("story", "role"));
                ref(owner, key(CONVERSATION, speak.conversation()), REQUIRES, where.child("story", "conversation"));
            } else if (story instanceof CampaignStoryObjective.Investigate investigate) {
                ref(owner, key(EVIDENCE, campaign, investigate.evidence()), INVESTIGATES, where.child("story", "evidence"));
            } else if (story instanceof CampaignStoryObjective.Follow follow) {
                ref(owner, key(CHARACTER, campaign, follow.role()), FOLLOWS, where.child("story", "role"));
                ref(owner, key(LOCATION, campaign, follow.location()), REQUIRES, where.child("story", "location"));
                scene(owner, campaign, follow.scene(), where.child("story", "scene"));
            }
        }
        for (var stage : quest.stages()) {
            var where = source(stage.source());
            var definition = stage.definition();
            actions(owner, campaign, definition.entryActions(), at);
            actions(owner, campaign, definition.exitActions(), at);
            definition.completeWhen().forEach(predicate -> conditions(owner, predicate.conditions(), where));
            definition.branches().forEach(branch -> {
                conditions(owner, branch.conditions(), where);
                actions(owner, campaign, branch.actions(), where);
                branch.blockedBy().forEach(blocker -> conditions(owner, blocker.conditions(), where));
            });
            definition.bonuses().forEach(bonus -> {
                bonus.when().forEach(predicate -> conditions(owner, predicate.conditions(), where));
                actions(owner, campaign, bonus.actions(), where);
            });
        }
        for (var trigger : quest.triggers()) {
            conditions(owner, trigger.definition().conditions(), source(trigger.source()));
            actions(owner, campaign, trigger.definition().actions(), source(trigger.source()));
        }
        for (var binding : content.quests().dialogueCatalog().bindings(quest.id())) {
            var tree = content.quests().dialogueCatalog().trees().get(binding.treeId());
            if (tree == null) continue;
            var where = source(binding.source());
            conditions(owner, tree.conditions(), where);
            var entry = tree.entry(binding.entryId()).orElse(null);
            if (entry == null) continue;
            conditions(owner, entry.conditions(), where);
            var pending = new ArrayList<String>();
            pending.add(entry.start());
            var visited = new LinkedHashSet<String>();
            for (int index = 0; index < pending.size(); index++) {
                String id = pending.get(index);
                if (!visited.add(id)) continue;
                var node = tree.nodes().get(id);
                if (node == null) continue;
                actions(owner, campaign, node.actions(), where);
                conditions(owner, node.conditions(), where);
                node.textVariants().forEach(variant -> conditions(owner, variant.conditions(), where));
                for (var response : node.responses()) {
                    actions(owner, campaign, response.actions(), where);
                    conditions(owner, response.conditions(), where);
                    response.textVariants().forEach(variant -> conditions(owner, variant.conditions(), where));
                    if (!response.next().isBlank()) pending.add(response.next());
                }
            }
        }
    }

    private void conversation(ConversationDefinition definition) {
        var at = new Source(definition.source().resource(), definition.source().pack(), "");
        var owner = declare(CONVERSATION, definition.id(), at);
        ref(owner, key(CHARACTER, definition.campaign(), definition.character()), SPEAKS, at.child("character"));
        if (!definition.owner().shared()) ref(key(QUEST, definition.quest()), owner, INCLUDES, at);
        for (var check : definition.checks().values()) {
            if (check.social() == null) continue;
            var where = new Source(at.resource(), at.pack(), check.pointer()).child("social");
            ref(owner, key(SOCIAL_PROGRESSION, check.social().progression()), REQUIRES, where.child("progression"));
            ref(owner, key(SOCIAL_SKILL, check.social().progression(), check.social().skill().id()), READS, where.child("skill"));
        }
        for (var topic : definition.topics().values()) conditions(owner, topic.conditions(),
                new Source(at.resource(), at.pack(), topic.pointer()));
        for (var node : definition.nodes().values()) {
            for (var exchange : node.exchanges()) if (exchange.speaker().kind() == ConversationDefinition.SpeakerKind.ROLE)
                ref(owner, key(CHARACTER, definition.campaign(), exchange.speaker().role()), SPEAKS,
                        new Source(at.resource(), at.pack(), exchange.pointer()).child("speaker", "role"));
            for (var effect : node.effects()) actions(owner, definition.campaign(), List.of(effect.action()),
                    new Source(at.resource(), at.pack(), effect.pointer()).child("action"));
            for (var response : node.responses().values()) {
                conditions(owner, response.conditions(), new Source(at.resource(), at.pack(), response.pointer()));
                for (var effect : response.effects()) actions(owner, definition.campaign(), List.of(effect.action()),
                        new Source(at.resource(), at.pack(), effect.pointer()).child("action"));
            }
        }
    }

    private void outcome(Key owner, ResourceLocation campaign, String milestone, String outcome, Relation relation, Source source) {
        ref(owner, key(MILESTONE, campaign, milestone), relation, source);
        ref(owner, key(OUTCOME, campaign, milestone, outcome), relation, source);
    }

    private void actions(Key owner, ResourceLocation campaign, List<VillagerActionDefinition> actions, Source source) {
        for (var action : actions) {
            var targetQuest = content.quests().compiledQuest(action.questId()).orElse(null);
            var context = action.questId() == null ? campaign : targetQuest == null ? null : targetQuest.metadata().campaign();
            if ((action.campaignOutcome() != null || action.campaignFact() != null || action.campaignReward() != null || action.campaignStanding() != null)
                    && (owner.kind() != QUEST || !owner.owner().equals(action.questId())))
                ref(owner, key(QUEST, action.questId()), REQUIRES, source);
            if (action.kind() == VillagerActionDefinition.Kind.QUEST || action.kind() == VillagerActionDefinition.Kind.QUEST_TRANSITION)
                ref(owner, key(QUEST, action.questId()), WRITES, source);
            if (action.campaignOutcome() != null) outcome(owner, context, action.campaignOutcome().milestone(), action.campaignOutcome().outcome(), COMMITS, source);
            if (action.campaignFact() != null) {
                ref(owner, key(FACT, context, action.campaignFact().fact()), WRITES, source);
                ref(owner, key(MILESTONE, context, action.campaignFact().milestone()), REQUIRES, source);
            }
            if (action.campaignStanding() != null) {
                var campaignContent = context == null ? null : content.campaigns().get(context);
                if (campaignContent == null || !campaignContent.definition().questBundles().contains(action.questId()))
                    standingIssues.add(CampaignContentCatalog.inPack(CampaignSchema.error(source.resource(), standingField(owner, source, "quest").pointer(),
                            "standing action requires a quest declared by its campaign"), source.pack()));
                ref(owner, key(standingKind(action.campaignStanding().scope()), context, action.campaignStanding().subject()), WRITES, standingField(owner, source, "subject"));
            }
            if (action.campaignReward() != null) ref(owner, key(REWARD, context, action.campaignReward().reward()), GRANTS, source);
            if (action.sceneId() != null) scene(owner, context, action.sceneId(), source);
        }
    }

    private static Kind standingKind(CampaignStandingDefinition.Scope scope) { return scope == CampaignStandingDefinition.Scope.CHARACTER ? RELATIONSHIP : FACTION; }

    private void scene(Key owner, ResourceLocation campaign, ResourceLocation scene, Source source) {
        ref(owner, key(SCENE, scene), STARTS, source);
        if (campaign != null) sceneCampaigns.computeIfAbsent(scene, unused -> new LinkedHashSet<>()).add(campaign);
    }

    private void conditions(Key owner, List<DialogueCondition> conditions, Source source) {
        standingConditions(owner, conditions, source);
        var pending = new ArrayList<>(conditions);
        for (int index = 0; index < pending.size(); index++) {
            var condition = pending.get(index);
            if (condition instanceof DialogueCondition.AllOf all) pending.addAll(all.conditions());
            else if (condition instanceof DialogueCondition.AnyOf any) pending.addAll(any.conditions());
            else if (condition instanceof DialogueCondition.Not not && not.condition() != null) pending.add(not.condition());
            else if (condition instanceof DialogueCondition.CampaignFact fact)
                ref(owner, key(FACT, fact.reference().campaign(), fact.reference().fact()), READS, source);
            else if (condition instanceof DialogueCondition.CampaignOutcome outcome)
                outcome(owner, outcome.reference().campaign(), outcome.reference().milestone(), outcome.reference().outcome(), READS, source);
            else if (condition instanceof DialogueCondition.CampaignCharacter character)
                ref(owner, key(CHARACTER, character.reference().campaign(), character.reference().role()), READS, source);
            else if (condition instanceof DialogueCondition.CampaignLocation location)
                ref(owner, key(LOCATION, location.reference().campaign(), location.reference().location()), READS, source);
            else if (condition instanceof DialogueCondition.Quest quest) ref(owner, key(QUEST, quest.questId()), READS, source);
            else if (condition instanceof DialogueCondition.QuestFact fact) ref(owner, key(QUEST, fact.questId()), READS, source);
            else if (condition instanceof DialogueCondition.SelectedChoice choice) ref(owner, key(QUEST, choice.questId()), READS, source);
            else if (condition instanceof DialogueCondition.StageHistory stage) ref(owner, key(QUEST, stage.questId()), READS, source);
        }
    }

    private void standingConditions(Key owner, List<DialogueCondition> conditions, Source source) {
        for (int index = 0; index < conditions.size(); index++)
            standingCondition(owner, conditions.get(index), standingField(owner, source, "conditions", Integer.toString(index)));
    }

    private void standingCondition(Key owner, DialogueCondition condition, Source source) {
        if (condition instanceof DialogueCondition.AllOf all) standingConditions(owner, all.conditions(), source);
        else if (condition instanceof DialogueCondition.AnyOf any) standingConditions(owner, any.conditions(), source);
        else if (condition instanceof DialogueCondition.Not not && not.condition() != null) standingCondition(owner, not.condition(), standingField(owner, source, "condition"));
        else if (condition instanceof DialogueCondition.CampaignStanding standing) {
            var query = standing.reference();
            ref(owner, key(standingKind(query.scope()), query.campaign(), query.subject()), READS, standingField(owner, source, "subject"));
            if (!query.reaction().isEmpty()) ref(owner, key(REACTION, query.campaign(),
                    query.scope().name().toLowerCase(java.util.Locale.ROOT), query.subject(), query.reaction()), READS, standingField(owner, source, "reaction"));
        }
    }

    private static Source standingField(Key owner, Source source, String... path) {
        return owner.kind() == CONVERSATION ? source.child(path) : source;
    }

    private void scenes() {
        for (var bundle : content.quests().bundles().values()) {
            var quest = content.quests().compiledQuest(bundle.questId()).orElse(null);
            for (var id : bundle.definitions().getOrDefault(QuestBundlePath.Kind.SCENE, Map.of()).keySet()) {
                if (quest != null && quest.metadata().campaign() != null)
                    sceneCampaigns.computeIfAbsent(id, unused -> new LinkedHashSet<>()).add(quest.metadata().campaign());
            }
        }
        content.quests().scenes().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var compiled = entry.getValue();
            Source origin = new Source(entry.getKey(), "", "");
            com.google.gson.JsonObject authored = null;
            for (var bundle : content.quests().bundles().values()) {
                var raw = bundle.definitions().getOrDefault(QuestBundlePath.Kind.SCENE, Map.of()).get(entry.getKey());
                if (raw == null) continue;
                var location = bundle.source(QuestBundlePath.Kind.SCENE, entry.getKey());
                origin = new Source(location.resource(), location.pack(), "");
                authored = raw;
                if (!bundle.owner().shared()) ref(key(QUEST, bundle.questId()), key(SCENE, entry.getKey()), INCLUDES, origin);
                break;
            }
            var owner = declare(SCENE, entry.getKey(), origin);
            var actorOrder = new LinkedHashSet<String>();
            if (authored != null && authored.has("actors")) authored.getAsJsonArray("actors").forEach(raw ->
                    actorOrder.add(raw.getAsJsonObject().get("alias").getAsString()));
            compiled.actors().keySet().stream().sorted().forEach(actorOrder::add);
            for (String alias : actorOrder) {
                var actor = compiled.actors().get(alias);
                if (actor == null) continue;
                Kind kind = switch (actor.bindingSource()) {
                    case CAMPAIGN_ROLE -> CHARACTER;
                    case CAMPAIGN_LOCATION -> LOCATION;
                    default -> null;
                };
                if (kind == null) continue;
                Source where = origin;
                if (authored != null && authored.has("actors")) {
                    var actors = authored.getAsJsonArray("actors");
                    for (int index = 0; index < actors.size(); index++) {
                        var raw = actors.get(index).getAsJsonObject();
                        if (raw.get("alias").getAsString().equals(actor.alias())) {
                            where = origin.child("actors", Integer.toString(index), raw.has("binding") ? "binding" : "source");
                            break;
                        }
                    }
                }
                for (var campaign : sceneCampaigns.getOrDefault(entry.getKey(), new LinkedHashSet<>()))
                    ref(owner, key(kind, campaign, actor.bindingReference()), REQUIRES, where);
            }
        });
    }
}
