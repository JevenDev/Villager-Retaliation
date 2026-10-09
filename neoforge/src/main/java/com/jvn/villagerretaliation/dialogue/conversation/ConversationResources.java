package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.skill.player.SocialProgressionDefinition;
import com.jvn.villagerretaliation.skill.player.SocialProgressionResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class ConversationResources {
    private ConversationResources() {
    }

    public static Optional<ConversationDefinition> conversation(MinecraftServer server, ResourceLocation id) {
        return QuestContentCatalogs.current(server).conversation(id);
    }

    public static Snapshot compile(QuestBundleTransactions.Result bundles, Set<ResourceLocation> sceneIds, Set<ResourceLocation> questIds) {
        return compile(bundles, sceneIds, questIds, SocialProgressionResources.compile(bundles).definitions());
    }

    public static Snapshot compile(QuestBundleTransactions.Result bundles, Set<ResourceLocation> sceneIds, Set<ResourceLocation> questIds,
            Map<ResourceLocation, SocialProgressionDefinition> progressions) {
        QuestLocaleCatalog shared = QuestLocaleCatalog.empty();
        for (var bundle : bundles.bundles().values()) if (bundle.owner().shared()) {
            for (String locale : bundle.locales().locales()) shared = shared.overlay(locale, bundle.locales().messages(locale));
        }
        Map<ResourceLocation, QuestBundlePath.Owner> sceneOwners = new LinkedHashMap<>();
        bundles.bundles().values().forEach(bundle -> bundle.definitions().getOrDefault(QuestBundlePath.Kind.SCENE, Map.of())
                .keySet().forEach(id -> sceneOwners.put(id, bundle.owner())));
        Map<ResourceLocation, ConversationDefinition> definitions = new LinkedHashMap<>();
        Map<QuestBundlePath.Owner, List<QuestDiagnostic>> diagnostics = new LinkedHashMap<>();
        for (var bundle : bundles.bundles().values().stream().sorted(Comparator.comparing(value -> value.owner().key())).toList()) {
            QuestLocaleCatalog localized = shared;
            for (String locale : bundle.locales().locales()) localized = localized.overlay(locale, bundle.locales().messages(locale));
            var compilerBundle = bundle.withLocales(localized);
            for (var id : bundle.definitions().getOrDefault(QuestBundlePath.Kind.CONVERSATION, Map.of()).keySet().stream().sorted().toList()) {
                var parsed = ConversationCompiler.compile(compilerBundle, id);
                List<QuestDiagnostic> issues = new ArrayList<>(parsed.diagnostics());
                if (parsed.valid()) {
                    var definition = parsed.definition();
                    for (var check : definition.checks().values()) if (check.social() != null && !progressions.containsKey(check.social().progression()))
                        issues.add(ConversationCompiler.error(definition.source(), check.pointer() + "/social/progression",
                                "unknown social progression " + check.social().progression()));
                    if (definition.quest() != null && !questIds.contains(definition.quest())) {
                        issues.add(ConversationCompiler.error(definition.source(), "/quest", "unknown quest " + definition.quest()));
                    }
                    for (var node : definition.nodes().values()) {
                        validateEffects(definition, node.effects(), sceneIds, sceneOwners, questIds, issues);
                        node.responses().values().forEach(response -> validateEffects(definition, response.effects(), sceneIds, sceneOwners, questIds, issues));
                    }
                    if (issues.isEmpty()) definitions.put(id, definition);
                }
                if (!issues.isEmpty()) diagnostics.computeIfAbsent(bundle.owner(), ignored -> new ArrayList<>()).addAll(issues);
            }
        }
        Map<QuestBundlePath.Owner, List<QuestDiagnostic>> frozen = new LinkedHashMap<>();
        diagnostics.forEach((owner, values) -> frozen.put(owner, List.copyOf(values)));
        return new Snapshot(Collections.unmodifiableMap(definitions), Collections.unmodifiableMap(frozen));
    }

    private static void validateEffects(ConversationDefinition definition, List<ConversationDefinition.Effect> effects,
            Set<ResourceLocation> sceneIds, Map<ResourceLocation, QuestBundlePath.Owner> sceneOwners,
            Set<ResourceLocation> questIds, List<QuestDiagnostic> issues) {
        for (var effect : effects) {
            var action = effect.action();
            if (action.kind() == VillagerActionDefinition.Kind.START_SCENE && !sceneIds.contains(action.sceneId())) {
                issues.add(ConversationCompiler.error(definition.source(), effect.pointer() + "/action/scene_id", "unknown scene " + action.sceneId()));
            }
            if (action.kind() == VillagerActionDefinition.Kind.START_SCENE) {
                var sceneOwner = sceneOwners.get(action.sceneId());
                if (sceneOwner != null && !sceneOwner.shared() && !sceneOwner.equals(definition.owner())) {
                    issues.add(ConversationCompiler.error(definition.source(), effect.pointer() + "/action/scene_id",
                            "private scene belongs to another quest bundle: " + sceneOwner.key()));
                }
            }
            if (action.questId() != null && !questIds.contains(action.questId())) {
                issues.add(ConversationCompiler.error(definition.source(), effect.pointer() + "/action/quest", "unknown quest " + action.questId()));
            }
            if (action.questId() == null && (action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_FACT
                    || action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME || action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_REWARD)) {
                issues.add(ConversationCompiler.error(definition.source(), effect.pointer() + "/action/quest", "campaign effect requires an owning quest"));
            }
        }
    }

    public record Snapshot(Map<ResourceLocation, ConversationDefinition> conversations,
            Map<QuestBundlePath.Owner, List<QuestDiagnostic>> diagnostics) {
    }
}
