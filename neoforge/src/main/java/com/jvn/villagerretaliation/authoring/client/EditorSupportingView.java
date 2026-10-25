package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import java.util.List;
import java.util.function.Consumer;

final class EditorSupportingView {
    private final EditorResourcePreview preview = new EditorResourcePreview();
    private String participant = "player", slot = "head";
    private boolean settings;
    private String lastPointer = "";
    private AuthoringDocument owner;

    void beginFrame(boolean active) { preview.beginFrame(active); }
    void close() { preview.retire(); }
    void settings() { settings = true; }

    void render(com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace workspace, AuthoringDocument document, JsonObject root, String pointer, float scale, EditorResourceView.Fields fields,
                Runnable resource, Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        if (owner != document) { owner = document; participant = "player"; slot = "head"; settings = false; lastPointer = ""; }
        if (!pointer.equals(lastPointer)) {
            if (pointer.matches("/(player|villager)(/.*)?")) { participant = pointer.split("/")[1]; settings = false; }
            lastPointer = pointer;
        }
        var kind = EditorSupportingResources.kind(document);
        if (kind == null) { resource.run(); return; }
        switch (kind) {
            case TRADE -> {
                ImGui.textWrapped("The player pays the cost and receives the result. Rank, chance and profession determine which villagers offer this trade.");
                if (ImGui.button("Add trade")) run.accept(() -> select.accept(EditorSupportingResources.addTrade(workspace, document)));
                var entries = DialogueGraph.array(root, "entries");
                int index = 0;
                if (pointer.matches("/entries/[0-9]+(/.*)?")) index = Integer.parseInt(pointer.split("/")[2]);
                if (index < entries.size() && entries.get(index).isJsonObject()) {
                    var trade = entries.get(index).getAsJsonObject();
                    ImGui.textDisabled("Base offer before skill scaling");
                    item(EditorSchema.object(trade.get("cost")), "Pays", "item", scale);
                    item(EditorSchema.object(trade.get("result")), "Receives", "item", scale);
                }
                resource.run();
            }
            case SELL_PRICE -> {
                ImGui.textUnformatted("Sell price");
                ImGui.textWrapped("The player sells this many matching items for currency. Market groups share demand. Component filters and durability rules remain editable below.");
                item(root, "Sells", "item", scale);
                var rates = DialogueGraph.array(root, "rates");
                if (rates.isEmpty()) ImGui.textWrapped(rate(root));
                else for (int i = 0; i < rates.size(); i++) if (rates.get(i).isJsonObject()) ImGui.textWrapped("Rate " + (i + 1) + ": " + rate(rates.get(i).getAsJsonObject()));
                if (ImGui.button("Add exchange rate")) run.accept(() -> EditorSupportingResources.addRate(document));
                EditorWidgets.sameLineFor("Use count ranges");
                if (ImGui.button("Use count ranges")) run.accept(() -> EditorSupportingResources.rangeCounts(document));
                ImGui.separator();
                fields.render(root, "", false);
            }
            case DUEL_KIT -> kit(document, root, scale, fields, select, run);
            case RAID_LOADOUT -> {
                ImGui.textWrapped("Each profile matches professions and supplies a pool for each difficulty. Add only the pools that should override the runtime defaults.");
                if (ImGui.button("Add loadout profile")) run.accept(() -> select.accept(EditorSupportingResources.addLoadout(workspace, document)));
                resource.run();
            }
            case WORD_POOL -> {
                ImGui.textWrapped("Use literal wording without variables or styling. Add conditions for contextual variants, then insert this pool from text completion.");
                if (ImGui.button("Add wording")) run.accept(() -> select.accept(EditorSupportingResources.addWording(document, false)));
                EditorWidgets.sameLineFor("Add conditional variant");
                if (ImGui.button("Add conditional variant")) run.accept(() -> select.accept(EditorSupportingResources.addWording(document, true)));
                resource.run();
            }
            case STYLE -> {
                String tag = DialogueGraph.string(root, "tag");
                ImGui.textWrapped("Use <" + tag + ">your wording</" + tag + "> after applying this style in the test world.");
                fields.render(root, "", false);
            }
            default -> resource.run();
        }
    }

    private void kit(AuthoringDocument document, JsonObject root, float scale, EditorResourceView.Fields fields,
                     Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        ImGui.textUnformatted("Duel kit");
        ImGui.textWrapped("Choose equipment for each participant. New inventory items use a free slot. Item components, enchantments and advanced stack properties remain editable.");
        for (String option : List.of("player", "villager", "settings")) {
            if (!option.equals("player")) EditorWidgets.sameLineFor(EditorContentBrowser.humanize(option));
            boolean active = option.equals("settings") ? settings : !settings && participant.equals(option);
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(active ? ImGuiCol.Header : ImGuiCol.FrameBg));
            if (ImGui.button(EditorContentBrowser.humanize(option))) { settings = option.equals("settings"); if (!settings) participant = option; }
            ImGui.popStyleColor();
        }
        if (settings) { fields.render(root, "", true); return; }
        if (ImGui.button("Add inventory item")) run.accept(() -> select.accept(EditorSupportingResources.addInventory(document, participant)));
        if (ImGui.beginCombo(EditorWidgets.field("Equipment slot"), slot)) {
            for (String option : List.of("mainhand", "offhand", "head", "chest", "legs", "feet")) if (ImGui.selectable(option, slot.equals(option))) slot = option;
            ImGui.endCombo();
        }
        var body = EditorSchema.object(root.get(participant)); var equipment = EditorSchema.object(body.get("equipment"));
        ImGui.beginDisabled(equipment.has(slot));
        if (ImGui.button("Add equipment in slot")) run.accept(() -> {
            String item = switch (slot) { case "head" -> "minecraft:iron_helmet"; case "chest" -> "minecraft:iron_chestplate";
                case "legs" -> "minecraft:iron_leggings"; case "feet" -> "minecraft:iron_boots"; case "offhand" -> "minecraft:shield"; default -> "minecraft:iron_sword"; };
            EditorSupportingResources.equipment(document, participant, slot, item);
            select.accept("/" + participant + "/equipment/" + slot);
        });
        ImGui.endDisabled();
        for (var entry : equipment.entrySet()) if (entry.getValue().isJsonObject()) item(entry.getValue().getAsJsonObject(), entry.getKey(), "id", scale);
        for (var entry : DialogueGraph.array(body, "inventory")) if (entry.isJsonObject())
            item(EditorSchema.object(entry.getAsJsonObject().get("stack")), "Inventory " + DialogueGraph.string(entry.getAsJsonObject(), "slot"), "id", scale);
        ImGui.separator();
        fields.render(body, "/" + participant, false);
    }

    private void item(JsonObject value, String label, String field, float scale) {
        String id = DialogueGraph.string(value, field);
        if (id.isEmpty()) return;
        float size = 28 * scale, left = ImGui.getCursorScreenPosX(), top = ImGui.getCursorScreenPosY();
        ImGui.dummy(size, size); preview.icon("item", id, left, top, size); ImGui.sameLine();
        String count = DialogueGraph.string(value, "count");
        ImGui.textWrapped(label + ": " + (count.isEmpty() ? "" : count + " x ") + id);
    }

    static String rate(JsonObject value) {
        return range(value.get("item_count")) + " items for " + range(value.get("currency_count")) + " currency";
    }

    private static String range(JsonElement value) {
        if (value == null) return "?";
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonObject()) return DialogueGraph.string(value.getAsJsonObject(), "min") + " to " + DialogueGraph.string(value.getAsJsonObject(), "max");
        return "?";
    }
}
