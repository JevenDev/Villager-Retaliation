package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.preview.PreviewConditions;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;

final class EditorPreviewConditions {
    private static final ImInt REPUTATION = new ImInt();
    private static com.jvn.villagerretaliation.reputation.VillagerReputationLevel tier =
            com.jvn.villagerretaliation.reputation.VillagerReputationLevel.NEUTRAL;
    private static final ImInt VARIANT = new ImInt();
    private static final ImString PROFESSION = new ImString("minecraft:none", 256);
    private static final ImString QUEST_ID = new ImString(256);
    private static final ImString QUEST_STATE = new ImString("available", 256);
    private static final ImString PLACEHOLDER_KEY = new ImString(256);
    private static final ImString PLACEHOLDER_VALUE = new ImString(1024);

    private EditorPreviewConditions() { }

    private static void speakers(PreviewConditions conditions, com.google.gson.JsonObject definition) {
        if (definition == null || !EditorWidgets.section("Conversation speakers")) return;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        var roles = new java.util.TreeSet<String>();
        if (definition.has("characters") && definition.get("characters").isJsonObject())
            roles.addAll(definition.getAsJsonObject("characters").keySet());
        collectRoles(definition, roles);
        roles.remove("provider");
        roles.remove("player");
        var entities = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        entities.add(minecraft.player);
        entities.addAll(minecraft.level.getEntities(minecraft.player, minecraft.player.getBoundingBox().inflate(64),
                net.minecraft.world.entity.Entity::isAlive));
        for (String role : roles) {
            var bound = conditions.speakers().get(role);
            String label = entities.stream().filter(entity -> entity.getUUID().equals(bound))
                    .map(entity -> entity.getName().getString()).findFirst().orElse("Unbound");
            if (ImGui.beginCombo(EditorWidgets.field(role), label)) {
                if (ImGui.selectable("Unbound", bound == null)) conditions.bindSpeaker(role, null);
                for (var entity : entities) {
                    if (ImGui.selectable(entity.getName().getString() + "##" + entity.getUUID(),
                            entity.getUUID().equals(bound))) conditions.bindSpeaker(role, entity.getUUID());
                }
                ImGui.endCombo();
            }
        }
        if (roles.isEmpty()) ImGui.textUnformatted("Provider uses the selected target. Player uses your player.");
    }

    private static void collectLegacy(com.google.gson.JsonElement element, java.util.Set<String> flags) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().startsWith("requires_") && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isBoolean() && entry.getValue().getAsBoolean())
                    flags.add(entry.getKey());
                collectLegacy(entry.getValue(), flags);
            }
        } else if (element.isJsonArray()) element.getAsJsonArray().forEach(value -> collectLegacy(value, flags));
    }

    private static void collectRoles(com.google.gson.JsonElement element, java.util.Set<String> roles) {
        if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has("speaker") && object.get("speaker").isJsonObject()) {
                var speaker = object.getAsJsonObject("speaker");
                if (speaker.has("role") && speaker.get("role").isJsonPrimitive()) roles.add(speaker.get("role").getAsString());
            }
            object.entrySet().forEach(entry -> collectRoles(entry.getValue(), roles));
        } else if (element.isJsonArray()) element.getAsJsonArray().forEach(value -> collectRoles(value, roles));
    }

    static void sync(PreviewConditions conditions) {
        var saved = conditions.snapshot();
        REPUTATION.set(saved.get("reputation").getAsInt());
        tier = com.jvn.villagerretaliation.reputation.VillagerReputationLevel.valueOf(saved.get("tier").getAsString());
        VARIANT.set(saved.get("variant").getAsInt());
        PROFESSION.set(saved.get("profession").getAsString());
    }

    static void time(PreviewConditions conditions) {
        String[] names = {"World time", "Morning (06:00)", "Afternoon (12:00)", "Evening (18:00)", "Night (00:00)"};
        long[] ticks = {0, 0, 6000, 12000, 18000};
        int selected = 0;
        if (conditions.dayTime() != null) {
            long current = Math.floorMod(conditions.dayTime(), 24000L);
            selected = current < 6000 ? 1 : current < 12000 ? 2 : current < 14000 ? 3 : 4;
        }
        ImGui.textUnformatted("Preview time");
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##preview_time", names[selected])) {
            for (int i = 0; i < names.length; i++) {
                if (ImGui.selectable(names[i], selected == i)) {
                    long world = net.minecraft.client.Minecraft.getInstance().level.getDayTime();
                    conditions.dayTime(i == 0 ? null : Math.floorDiv(world, 24000L) * 24000L + ticks[i]);
                }
            }
            ImGui.endCombo();
        }
    }

    private static void usedConditions(com.google.gson.JsonElement value, java.util.Set<String> types) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> usedConditions(child, types));
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive())
                types.add(DialogueCondition.canonicalTypeId(object.get("type").getAsString()));
            object.entrySet().forEach(entry -> usedConditions(entry.getValue(), types));
        }
    }

    static void render(PreviewConditions conditions, com.google.gson.JsonObject definition) {
        speakers(conditions, definition);
        if (!EditorWidgets.section("Simulated conditions")) return;
        if (ImGui.inputInt(EditorWidgets.field("Reputation"), REPUTATION)) {
            conditions.reputation(REPUTATION.get());
            tier = com.jvn.villagerretaliation.reputation.VillagerReputationLevel.fromReputation(REPUTATION.get());
        }
        if (ImGui.beginCombo(EditorWidgets.field("Reputation tier"), tier.name())) {
            for (var candidate : com.jvn.villagerretaliation.reputation.VillagerReputationLevel.values()) {
                if (ImGui.selectable(candidate.name(), tier == candidate)) {
                    tier = candidate;
                    conditions.reputationLevel(candidate);
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.inputInt(EditorWidgets.field("Variant index"), VARIANT)) conditions.variant(VARIANT.get());
        if (ImGui.inputText(EditorWidgets.field("Profession"), PROFESSION)) conditions.profession(PROFESSION.get());
        if (ImGui.checkbox("Successful checks", conditions.checkSuccess())) conditions.checkSuccess(!conditions.checkSuccess());
        if (definition != null) for (var check : com.jvn.villagerretaliation.authoring.core.DialogueGraph.array(definition, "checks")) {
            if (!check.isJsonObject()) continue;
            String id = com.jvn.villagerretaliation.authoring.core.DialogueGraph.string(check.getAsJsonObject(), "id");
            if (ImGui.checkbox("Pass check: " + id, conditions.checkSuccess(id))) conditions.checkSuccess(id, !conditions.checkSuccess(id));
        }
        ImGui.inputText(EditorWidgets.field("Quest ID"), QUEST_ID);
        ImGui.inputText(EditorWidgets.field("Quest state"), QUEST_STATE);
        if (ImGui.button("Set simulated quest state")) conditions.quest(QUEST_ID.get(), QUEST_STATE.get());
        ImGui.inputText(EditorWidgets.field("Placeholder key"), PLACEHOLDER_KEY);
        ImGui.inputText(EditorWidgets.field("Placeholder value"), PLACEHOLDER_VALUE);
        if (ImGui.button("Set simulated placeholder")) conditions.placeholder(PLACEHOLDER_KEY.get(), PLACEHOLDER_VALUE.get());
        var legacy = new java.util.TreeSet<String>();
        if (definition != null) collectLegacy(definition, legacy);
        for (String flag : legacy) {
            ConditionOutcome selected = conditions.overrides().get(flag);
            if (ImGui.beginCombo(EditorWidgets.field(flag), selected == null ? "Unknown context" : selected.name())) {
                if (ImGui.selectable("Unknown context", selected == null)) conditions.override(flag, null);
                for (ConditionOutcome outcome : ConditionOutcome.values()) {
                    if (ImGui.selectable(outcome.name(), selected == outcome)) conditions.override(flag, outcome);
                }
                ImGui.endCombo();
            }
        }
        var used = new java.util.TreeSet<String>();
        if (definition != null) usedConditions(definition, used);
        for (var descriptor : DialogueCondition.descriptors()) {
            if (!used.contains(descriptor.id())) continue;
            ConditionOutcome selected = conditions.overrides().get(descriptor.id());
            if (ImGui.beginCombo(EditorWidgets.field(EditorProgressConditions.label(descriptor.id()) + "##" + descriptor.id()),
                    selected == null ? "Evaluate simulated context" : selected.name())) {
                if (ImGui.selectable("Evaluate simulated context", selected == null)) conditions.override(descriptor.id(), null);
                for (ConditionOutcome outcome : ConditionOutcome.values()) {
                    if (ImGui.selectable(outcome.name(), outcome == selected)) conditions.override(descriptor.id(), outcome);
                }
                ImGui.endCombo();
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(descriptor.schema().toString());
        }
    }
}
