package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class CampaignStateInspection {
    private CampaignStateInspection() { }

    public static Snapshot inspect(MinecraftServer server, ResourceLocation campaign) {
        if (!server.isSameThread()) throw new IllegalStateException("Campaign inspection requires the server thread.");
        var level = server.overworld();
        var mode = CampaignWorldModeSavedData.get(level);
        var data = CampaignSavedData.get(level);
        var content = CampaignResources.current(server).campaigns().get(campaign);
        var instance = data.instances().get(campaign);
        var definition = content == null ? null : content.definition();
        List<String> diagnostics = new ArrayList<>();
        if (!mode.diagnostic().isBlank()) diagnostics.add(mode.diagnostic());
        if (mode.mode() != CampaignWorldMode.CAMPAIGN) diagnostics.add("Campaign play requires Campaign Mode.");
        String compatibility = definition == null ? data.diagnostic() : data.compatibility(definition);
        Status status = !data.diagnostic().isBlank() ? Status.UNAVAILABLE
                : definition == null ? instance == null ? Status.MISSING : Status.DORMANT
                : !compatibility.isBlank() ? Status.INCOMPATIBLE : instance == null ? Status.UNSTARTED : Status.READY;
        if (!compatibility.isBlank()) diagnostics.add(compatibility);
        if (definition == null) diagnostics.add("Campaign content is missing or invalid. Saved history is retained.");
        var characters = CampaignCharacterSavedData.get(level);
        var locations = CampaignLocationSavedData.get(level);
        var conversations = ConversationSavedData.get(level);
        for (String issue : List.of(characters.diagnostic(), locations.diagnostic(), conversations.diagnostic(), CampaignStandingSavedData.get(level).diagnostic())) {
            if (!issue.isBlank()) diagnostics.add(issue);
        }
        var roles = new LinkedHashSet<String>();
        if (definition != null) roles.addAll(definition.characters().keySet());
        characters.bindings().keySet().stream().filter(key -> key.campaign().equals(campaign))
                .map(CampaignCharacterBinding.Key::role).sorted().forEach(roles::add);
        List<Character> characterViews = new ArrayList<>();
        for (String role : roles) {
            var saved = characters.bindings().get(new CampaignCharacterBinding.Key(campaign, role));
            var query = CampaignBindingQueries.character(server, campaign, role);
            var effective = query.binding();
            CharacterStatus state = !query.diagnostic().isBlank() ? CharacterStatus.UNAVAILABLE
                    : effective == null ? CharacterStatus.UNBOUND : effective.lost() ? CharacterStatus.LOST
                    : query.entity() == null ? CharacterStatus.UNLOADED
                    : query.available() ? CharacterStatus.AVAILABLE : CharacterStatus.DOWNED;
            characterViews.add(new Character(role, state, saved, effective, query.diagnostic()));
        }
        var aliases = new LinkedHashSet<String>();
        if (definition != null) aliases.addAll(definition.locations().keySet());
        locations.bindings().keySet().stream().filter(key -> key.campaign().equals(campaign))
                .map(CampaignLocationBinding.Key::alias).sorted().forEach(aliases::add);
        List<Location> locationViews = new ArrayList<>();
        for (String alias : aliases) {
            var saved = locations.bindings().get(new CampaignLocationBinding.Key(campaign, alias));
            var query = CampaignBindingQueries.location(server, campaign, alias);
            locationViews.add(new Location(alias, saved, query.diagnostic()));
        }
        var sessions = conversations.snapshots().values().stream()
                .filter(session -> session.identity().campaign().equals(campaign)).toList();
        return new Snapshot(campaign, mode.mode(), mode.selectionSource(), status, definition, instance,
                characterViews, locationViews, sessions, diagnostics);
    }

    public enum Status { READY, UNSTARTED, DORMANT, MISSING, INCOMPATIBLE, UNAVAILABLE }
    public enum CharacterStatus { AVAILABLE, UNBOUND, UNLOADED, DOWNED, LOST, UNAVAILABLE }

    public record Character(String role, CharacterStatus status, CampaignCharacterBinding saved,
            CampaignCharacterBinding effective, String diagnostic) { }
    public record Location(String alias, CampaignLocationBinding saved, String diagnostic) { }
    public record Player(UUID id, boolean recorded, CampaignInstance.PlayerRecord progress,
            Map<String, CampaignFacts.Value> facts, Map<String, CampaignProgression.ChapterAccess> chapters,
            Map<String, CampaignProgression.Entry> entries) {
        public Player {
            facts = ordered(facts);
            chapters = ordered(chapters);
            entries = ordered(entries);
        }
    }

    public record Snapshot(ResourceLocation campaign, CampaignWorldMode mode, String selectionSource, Status status,
            CampaignDefinition definition, CampaignInstance instance, List<Character> characters,
            List<Location> locations, List<ConversationSession.Snapshot> conversations, List<String> diagnostics) {
        public Snapshot {
            characters = List.copyOf(characters);
            locations = List.copyOf(locations);
            conversations = List.copyOf(conversations);
            diagnostics = List.copyOf(diagnostics);
        }

        public Player player(UUID id) {
            var saved = instance == null ? null : instance.players().get(id);
            var facts = instance == null ? Map.<String, CampaignFacts.Value>of() : instance.facts().players().getOrDefault(id, Map.of());
            Map<String, CampaignProgression.ChapterAccess> chapters = new LinkedHashMap<>();
            Map<String, CampaignProgression.Entry> entries = new LinkedHashMap<>();
            if (status == Status.READY || status == Status.UNSTARTED) {
                definition.chapters().keySet().forEach(chapter -> chapters.put(chapter, CampaignProgression.chapter(definition, instance, id, chapter)));
                if (instance != null && saved != null) saved.milestones().keySet().forEach(milestone -> {
                    var entry = CampaignProgression.entry(definition, instance, id, milestone);
                    if (entry != null) entries.put(milestone, entry);
                });
            }
            return new Player(id, saved != null || !facts.isEmpty(), saved == null ? CampaignInstance.PlayerRecord.empty() : saved,
                    facts, chapters, entries);
        }
    }

    private static <K, V> Map<K, V> ordered(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
