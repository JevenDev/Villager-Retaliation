package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record CampaignContentCatalog(QuestContentCatalog quests, Map<ResourceLocation, Entry> campaigns,
        List<QuestDiagnostic> diagnostics) {
    public CampaignContentCatalog {
        campaigns = Collections.unmodifiableMap(new LinkedHashMap<>(campaigns));
        diagnostics = List.copyOf(diagnostics);
    }

    public static CampaignContentCatalog compile(QuestContentCatalog quests, List<Source> sources) {
        Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
        Map<ResourceLocation, Source> owners = new LinkedHashMap<>();
        Map<String, ResourceLocation> prefixes = new LinkedHashMap<>();
        Map<String, ResourceLocation> messageOwners = new LinkedHashMap<>();
        Set<ResourceLocation> rejected = new HashSet<>();
        List<QuestDiagnostic> diagnostics = new ArrayList<>();
        Set<String> questPrefixes = new HashSet<>();
        Set<String> questMessages = new HashSet<>();
        quests.localization().locales().forEach(locale -> questMessages.addAll(quests.localization().messages(locale).keySet()));
        quests.bundles().values().forEach(bundle -> questPrefixes.add(bundle.localizationPrefix()));
        for (Source source : sources.stream().sorted(Comparator.comparing(Source::location)).toList()) {
            var parsed = CampaignDefinitionParser.parse(source.location(), source.root());
            List<QuestDiagnostic> issues = new ArrayList<>(source.diagnostics());
            issues.addAll(parsed.diagnostics());
            if (!parsed.valid()) {
                issues.forEach(issue -> diagnostics.add(inPack(issue, source.pack())));
                continue;
            }
            CampaignDefinition definition = parsed.definition();
            Source prior = owners.putIfAbsent(definition.id(), source);
            if (prior != null) {
                rejected.add(definition.id());
                issues.add(CampaignSchema.error(source.location(), "/id", "campaign id is already defined by " + prior.location()));
            }
            ResourceLocation prefixOwner = prefixes.putIfAbsent(definition.localizationPrefix(), definition.id());
            if (prefixOwner != null && !prefixOwner.equals(definition.id())) {
                rejected.add(prefixOwner);
                issues.add(CampaignSchema.error(source.location(), "/localization_prefix", "localization prefix belongs to campaign " + prefixOwner));
            }
            if (questPrefixes.contains(definition.localizationPrefix())) issues.add(CampaignSchema.error(source.location(),
                    "/localization_prefix", "localization prefix belongs to an existing quest bundle"));
            issues.addAll(CampaignDefinitionParser.validateQuestReferences(definition, quests));
            quests.conversations().values().stream().filter(conversation -> conversation.campaign().equals(definition.id()))
                    .forEach(conversation -> issues.addAll(com.jvn.villagerretaliation.dialogue.conversation.ConversationCompiler.validateCampaign(conversation, definition)));
            requireText(definition, source.locales(), definition.titleKey(), "/title_key", issues);
            definition.transactions().values().forEach(transaction -> requireText(definition, source.locales(),
                    transaction.titleKey(), transaction.pointer() + "/title_key", issues));
            CampaignStandingDefinition.all(definition).forEach(standing -> {
                requireText(definition, source.locales(), standing.titleKey(), standing.pointer() + "/title_key", issues);
                standing.reactions().values().forEach(reaction -> requireText(definition, source.locales(),
                        reaction.titleKey(), reaction.pointer() + "/title_key", issues));
            });
            definition.characters().values().forEach(character -> requireText(definition, source.locales(),
                    character.titleKey(), character.pointer() + "/title_key", issues));
            definition.locations().values().forEach(location -> requireText(definition, source.locales(),
                    location.titleKey(), location.pointer() + "/title_key", issues));
            definition.evidence().values().forEach(evidence -> {
                requireText(definition, source.locales(), evidence.titleKey(), evidence.pointer() + "/title_key", issues);
                requireText(definition, source.locales(), evidence.textKey(), evidence.pointer() + "/text_key", issues);
            });
            definition.chapters().values().forEach(chapter -> requireText(definition, source.locales(),
                    chapter.titleKey(), chapter.pointer() + "/title_key", issues));
            definition.milestones().values().forEach(milestone -> milestone.outcomes().values().forEach(outcome ->
                    requireText(definition, source.locales(), outcome.titleKey(), outcome.pointer() + "/title_key", issues)));
            for (String locale : source.locales().locales()) {
                for (String key : source.locales().messages(locale).keySet()) {
                    if (!key.startsWith(definition.localizationPrefix()) || questMessages.contains(key)) {
                        issues.add(CampaignSchema.error(source.location(), "/localization_prefix", "locale message is owned outside this campaign: " + key));
                    }
                    ResourceLocation messageOwner = messageOwners.putIfAbsent(key, definition.id());
                    if (messageOwner != null && !messageOwner.equals(definition.id())) {
                        rejected.add(messageOwner);
                        issues.add(CampaignSchema.error(source.location(), "/localization_prefix", "locale message belongs to campaign " + messageOwner + ": " + key));
                    }
                }
            }
            if (issues.isEmpty()) entries.put(definition.id(), new Entry(definition, source.locales(), source.pack()));
            else rejected.add(definition.id());
            issues.forEach(issue -> diagnostics.add(inPack(issue, source.pack())));
        }
        rejected.forEach(entries::remove);
        quests.conversations().values().stream().filter(conversation -> !owners.containsKey(conversation.campaign()))
                .forEach(conversation -> diagnostics.add(com.jvn.villagerretaliation.dialogue.conversation.ConversationCompiler.error(
                        conversation.source(), "/campaign", "campaign content is unavailable: " + conversation.campaign())));
        diagnostics.addAll(CampaignStandingReferences.validate(new CampaignContentCatalog(quests, entries, List.of())));
        diagnostics.addAll(CampaignTransactionReferences.validate(new CampaignContentCatalog(quests, entries, List.of())));
        return new CampaignContentCatalog(quests, entries, diagnostics);
    }

    private static void requireText(CampaignDefinition definition, QuestLocaleCatalog locales, String key,
            String pointer, List<QuestDiagnostic> diagnostics) {
        if (locales.plainText(QuestLocaleCatalog.ENGLISH, key).filter(text -> !text.isBlank()).isEmpty()) {
            diagnostics.add(CampaignSchema.error(definition.source(), pointer, "English localization is missing or not plain text: " + key));
        }
    }

    static QuestDiagnostic inPack(QuestDiagnostic diagnostic, String pack) {
        return new QuestDiagnostic(diagnostic.severity(), diagnostic.code(), diagnostic.resourceId(),
                diagnostic.sourcePack().isBlank() ? pack : diagnostic.sourcePack(), diagnostic.resourcePath(),
                diagnostic.jsonPointer(), diagnostic.message(), diagnostic.suggestedFix(), diagnostic.relevantIds());
    }

    public record Entry(CampaignDefinition definition, QuestLocaleCatalog localization, String sourcePack) {
        public Entry { sourcePack = sourcePack == null ? "" : sourcePack; }
        public Entry(CampaignDefinition definition, QuestLocaleCatalog localization) { this(definition, localization, ""); }
    }

    public record Source(ResourceLocation location, String pack, JsonObject root, QuestLocaleCatalog locales,
            List<QuestDiagnostic> diagnostics) {
        public Source {
            root = root == null ? null : root.deepCopy();
            diagnostics = List.copyOf(diagnostics);
        }

        @Override
        public JsonObject root() {
            return this.root == null ? null : this.root.deepCopy();
        }
    }
}
