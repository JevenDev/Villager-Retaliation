package com.jvn.villagerretaliation.skill.player;

import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class SocialProgressionResources {
    private SocialProgressionResources() { }

    public static Optional<SocialProgressionDefinition> get(MinecraftServer server, ResourceLocation id) {
        return Optional.ofNullable(QuestContentCatalogs.current(server).socialProgressions().get(id));
    }

    public static Snapshot compile(QuestBundleTransactions.Result bundles) {
        var definitions = new LinkedHashMap<ResourceLocation, SocialProgressionDefinition>();
        var diagnostics = new LinkedHashMap<QuestBundlePath.Owner, List<QuestDiagnostic>>();
        for (var bundle : bundles.bundles().values().stream().sorted(Comparator.comparing(value -> value.owner().key())).toList()) {
            for (var id : bundle.definitions().getOrDefault(QuestBundlePath.Kind.SOCIAL_PROGRESSION, Map.of()).keySet().stream().sorted().toList()) {
                var result = SocialProgressionCompiler.compile(bundle, id);
                if (result.valid()) definitions.put(id, result.definition());
                else diagnostics.computeIfAbsent(bundle.owner(), ignored -> new ArrayList<>()).addAll(result.diagnostics());
            }
        }
        return new Snapshot(definitions, diagnostics);
    }

    public record Snapshot(Map<ResourceLocation, SocialProgressionDefinition> definitions,
            Map<QuestBundlePath.Owner, List<QuestDiagnostic>> diagnostics) {
        public Snapshot {
            definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
            var copy = new LinkedHashMap<QuestBundlePath.Owner, List<QuestDiagnostic>>();
            diagnostics.forEach((owner, issues) -> copy.put(owner, List.copyOf(issues)));
            diagnostics = Collections.unmodifiableMap(copy);
        }
    }
}
