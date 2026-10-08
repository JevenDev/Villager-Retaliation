package com.jvn.villagerretaliation.scene.encounter;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class EncounterPhaseLedger {
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private CompoundTag definition;
    private Set<String> completionTriggers;
    private Tag preserved;
    private String diagnostic = "";

    String diagnostic() { return diagnostic; }
    boolean hasHistory() { return !runs.isEmpty() || !diagnostic.isBlank(); }
    Map<String, Run> runs() { return Collections.unmodifiableMap(runs); }

    boolean bind(EncounterTemplate template) {
        require(diagnostic.isBlank(), diagnostic);
        var current = structuralDefinition(template.phases());
        if (definition != null) {
            require(definition.equals(current), "Encounter phases have changed. Restore their original content before continuing.");
            return false;
        }
        definition = current;
        return true;
    }

    boolean captureCompletion(EncounterInstance encounter, EncounterTemplate template, long time) {
        if (completionTriggers != null || encounter.state() != EncounterInstance.EncounterState.COMPLETED) return false;
        completionTriggers = new LinkedHashSet<>();
        for (var phase : template.phases()) if (EncounterService.phaseMatches(encounter, template, phase, time)) completionTriggers.add(phase.id());
        return true;
    }

    boolean completionTriggered(String phase) { return completionTriggers != null && completionTriggers.contains(phase); }

    void record(Run run) {
        require(diagnostic.isBlank(), diagnostic);
        var prior = runs.get(run.key());
        require(prior != null || runs.size() < 4096, "encounter phase history capacity exceeded");
        require(prior == null || prior.advance(run.nextAction()).equals(run) && run.nextAction() >= prior.nextAction(), "phase run identity cannot change");
        runs.put(run.key(), run);
    }

    Tag save() {
        if (preserved != null) return preserved.copy();
        if (!diagnostic.isBlank()) return null;
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.put("Definition", definition == null ? new CompoundTag() : definition.copy());
        tag.putBoolean("CompletionCaptured", completionTriggers != null);
        var triggers = new ListTag();
        if (completionTriggers != null) completionTriggers.forEach(id -> triggers.add(net.minecraft.nbt.StringTag.valueOf(id)));
        tag.put("CompletionTriggers", triggers);
        var records = new ListTag();
        runs.values().forEach(run -> records.add(run.save()));
        tag.put("Runs", records);
        return tag;
    }

    static EncounterPhaseLedger load(Tag input) {
        var ledger = new EncounterPhaseLedger();
        if (input == null) {
            ledger.diagnostic = "Encounter phase history is missing. Restore compatible scene data before continuing.";
            return ledger;
        }
        try {
            require(input instanceof CompoundTag, "invalid phase history");
            var tag = (CompoundTag) input;
            require(tag.getAllKeys().equals(Set.of("Version", "Definition", "Runs", "CompletionCaptured", "CompletionTriggers"))
                    && tag.contains("Version", Tag.TAG_INT) && tag.getInt("Version") == 1
                    && tag.contains("Definition", Tag.TAG_COMPOUND) && tag.get("Runs") instanceof ListTag
                    && tag.contains("CompletionCaptured", Tag.TAG_BYTE) && tag.get("CompletionTriggers") instanceof ListTag, "unsupported phase history");
            var triggers = (ListTag) tag.get("CompletionTriggers");
            require(triggers.size() <= 64 && (triggers.isEmpty() || triggers.getElementType() == Tag.TAG_STRING)
                    && (tag.getBoolean("CompletionCaptured") || triggers.isEmpty()), "invalid completion triggers");
            if (tag.getBoolean("CompletionCaptured")) {
                ledger.completionTriggers = new LinkedHashSet<>();
                for (var raw : triggers) require(raw.getAsString().matches("[a-z][a-z0-9_.-]{0,63}") && ledger.completionTriggers.add(raw.getAsString()), "invalid completion trigger identity");
            }
            ledger.definition = tag.getCompound("Definition").isEmpty() ? null : tag.getCompound("Definition").copy();
            var records = (ListTag) tag.get("Runs");
            require(records.size() <= 4096 && (records.isEmpty() || records.getElementType() == Tag.TAG_COMPOUND), "invalid phase records");
            var tokens = new java.util.HashSet<UUID>();
            for (var raw : records) {
                var run = Run.load((CompoundTag) raw);
                require(!ledger.runs.containsKey(run.key()) && tokens.add(run.token()), "duplicate phase run");
                ledger.record(run);
            }
            require(ledger.runs.isEmpty() || ledger.definition != null, "phase runs have no saved definition");
        } catch (IllegalArgumentException exception) {
            ledger.runs.clear();
            ledger.preserved = input.copy();
            ledger.diagnostic = "Encounter phase history is unreadable. Restore compatible scene data before continuing.";
        }
        return ledger;
    }

    private static CompoundTag structuralDefinition(List<EncounterTemplate.Phase> phases) {
        var root = new CompoundTag();
        var entries = new ListTag();
        for (var phase : phases) {
            var tag = new CompoundTag();
            tag.putString("Id", phase.id());
            tag.putBoolean("Repeatable", phase.repeatable());
            tag.putInt("Interval", phase.repeatIntervalTicks());
            tag.putInt("MaxFires", phase.maxFires());
            var trigger = new CompoundTag();
            trigger.putString("Type", phase.trigger().type().name());
            trigger.putString("Wave", phase.trigger().waveId());
            trigger.putInt("Percentage", phase.trigger().percentage());
            trigger.putLong("Ticks", phase.trigger().ticks());
            trigger.putString("Member", phase.trigger().memberId());
            tag.put("Trigger", trigger);
            var actions = new ListTag();
            for (var action : phase.actions()) {
                var effect = new CompoundTag();
                effect.putString("Id", action.id());
                effect.putString("Type", action.type().name());
                if (action.type() == EncounterTemplate.PhaseActionType.FACT) {
                    effect.putString("Scope", action.scope().name());
                    effect.putString("Tag", action.tag() == null ? "" : action.tag().toString());
                    effect.putString("Key", action.key());
                    effect.putString("Value", action.value());
                } else if (action.type() == EncounterTemplate.PhaseActionType.TRANSITION) effect.putString("Target", action.target());
                actions.add(effect);
            }
            tag.put("Actions", actions);
            entries.add(tag);
        }
        root.put("Phases", entries);
        return root;
    }

    record Run(String phase, int ordinal, UUID token, String step, ResourceLocation quest, Set<UUID> participants, int nextAction) {
        Run {
            require(phase != null && phase.matches("[a-z][a-z0-9_.-]{0,63}") && ordinal >= 1 && ordinal <= 64
                    && token != null && step != null && !step.isBlank()
                    && participants != null && participants.size() <= 1024 && nextAction >= 0 && nextAction <= 32, "invalid phase run");
            participants = Collections.unmodifiableSet(new LinkedHashSet<>(participants.stream().sorted().toList()));
        }
        String key() { return phase + "/" + ordinal; }
        Run advance(int next) { return new Run(phase, ordinal, token, step, quest, participants, next); }
        String operation(EncounterInstance encounter) { return encounter.sceneId() + "/encounter/" + encounter.id() + "/phase/" + key(); }
        String operation(EncounterInstance encounter, String action) { return operation(encounter) + "/action/" + action; }
        UUID factOperation(EncounterInstance encounter, String action) {
            return UUID.nameUUIDFromBytes(("encounter-phase-fact/" + operation(encounter, action)).getBytes(StandardCharsets.UTF_8));
        }
        UUID actionToken(String action) { return UUID.nameUUIDFromBytes((token + "/action/" + action).getBytes(StandardCharsets.UTF_8)); }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("Phase", phase);
            tag.putInt("Ordinal", ordinal);
            tag.putUUID("Token", token);
            tag.putString("Step", step);
            tag.putString("Quest", quest == null ? "" : quest.toString());
            var players = new ListTag();
            participants.forEach(id -> players.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
            tag.put("Participants", players);
            tag.putInt("NextAction", nextAction);
            return tag;
        }
        static Run load(CompoundTag tag) {
            require(tag.getAllKeys().equals(Set.of("Phase", "Ordinal", "Token", "Step", "Quest", "Participants", "NextAction"))
                    && tag.contains("Phase", Tag.TAG_STRING) && tag.contains("Ordinal", Tag.TAG_INT) && tag.hasUUID("Token")
                    && tag.contains("Step", Tag.TAG_STRING) && tag.contains("Quest", Tag.TAG_STRING)
                    && tag.get("Participants") instanceof ListTag && tag.contains("NextAction", Tag.TAG_INT), "invalid saved phase run");
            var players = (ListTag) tag.get("Participants");
            require(players.size() <= 1024 && (players.isEmpty() || players.getElementType() == Tag.TAG_INT_ARRAY), "invalid phase participants");
            var participants = new LinkedHashSet<UUID>();
            for (var raw : players) require(participants.add(net.minecraft.nbt.NbtUtils.loadUUID(raw)), "duplicate phase participant");
            String questId = tag.getString("Quest");
            var quest = questId.isEmpty() ? null : ResourceLocation.tryParse(questId);
            require(questId.isEmpty() || quest != null, "invalid phase quest identity");
            return new Run(tag.getString("Phase"), tag.getInt("Ordinal"), tag.getUUID("Token"), tag.getString("Step"), quest, participants, tag.getInt("NextAction"));
        }
    }

    static void require(boolean valid, String diagnostic) {
        if (!valid) throw new IllegalArgumentException(diagnostic);
    }
}
