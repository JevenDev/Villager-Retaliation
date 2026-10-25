package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.io.IOException;
import java.nio.file.Files;

final class EditorSupportingChecks {
    static int run() throws IOException {
        recipesRespectRuntimeRoots();
        entryIdsStayUniqueAcrossFiles();
        priceConversionPreservesFiltersAndUndo();
        malformedCollectionsStayUntouched();
        kitSlotsPreserveStacksAndUndo();
        schemasDescribeEditableValues();
        wordingAndStylesMatchRuntimeRules();
        return 7;
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("supporting-editor-check")); }
    private static AuthoringDocument create(AuthoringWorkspace workspace, EditorResourceRecipes.Kind kind, String name) {
        return EditorResourceRecipes.create(workspace, kind, (kind == EditorResourceRecipes.Kind.RAID_LOADOUT ? "villagerretaliation:" : "test:") + name, null);
    }

    private static void recipesRespectRuntimeRoots() throws IOException {
        var workspace = workspace(); var campaign = workspace.createDocument(DefinitionKind.WORLD_CAMPAIGN, "test:story");
        for (var kind : EditorResourceRecipes.Kind.values()) if (kind != EditorResourceRecipes.Kind.SCENE && kind != EditorResourceRecipes.Kind.ENCOUNTER) {
            var document = create(workspace, kind, kind.name().toLowerCase(java.util.Locale.ROOT));
            check(document.resourcePath().contains("/" + kind.directory + "/"), "recipes use the runtime resource directory");
            check(EditorSupportingResources.kind(document) == kind, "each recipe selects its guided form");
            if (!kind.scoped()) rejects(() -> EditorResourceRecipes.create(workspace, kind, "test:scoped", campaign));
        }
        rejects(() -> EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.RAID_LOADOUT, "test:wrong_namespace", null));
        var pool = EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.WORD_POOL, "test:greeting", campaign);
        check(pool.resourcePath().equals("data/test/world_campaigns/story/dialogue_words/en_us/greeting.json"), "word pools support campaign scope");
        check(EditorSupportingResources.kind(new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue_words/fr_fr/greeting.json", pool.json()))
                == EditorResourceRecipes.Kind.WORD_POOL, "imported translations use the word pool form");
    }

    private static void entryIdsStayUniqueAcrossFiles() throws IOException {
        var workspace = workspace();
        var first = create(workspace, EditorResourceRecipes.Kind.TRADE, "trade_1");
        var second = create(workspace, EditorResourceRecipes.Kind.TRADE, "offers");
        String pointer = EditorSupportingResources.addTrade(workspace, second);
        check(JsonPointers.get(second.json(), pointer + "/id").getAsString().equals("test:trade_2"), "new trade IDs avoid entries in other files");
        rejects(() -> create(workspace, EditorResourceRecipes.Kind.TRADE, "trade_2"));
        second.undo(); check(second.json().getAsJsonArray("entries").size() == 1, "trade addition is one undo step");
        check(first.json().getAsJsonArray("entries").size() == 1, "other files are untouched");
        var loadouts = create(workspace, EditorResourceRecipes.Kind.RAID_LOADOUT, "militia");
        var other = create(workspace, EditorResourceRecipes.Kind.RAID_LOADOUT, "guards");
        String added = EditorSupportingResources.addLoadout(workspace, loadouts);
        String next = EditorSupportingResources.addLoadout(workspace, other);
        check(!JsonPointers.get(loadouts.json(), added + "/id").equals(JsonPointers.get(other.json(), next + "/id")), "loadout IDs stay unique across files");
    }

    private static void priceConversionPreservesFiltersAndUndo() throws IOException {
        var price = create(workspace(), EditorResourceRecipes.Kind.SELL_PRICE, "wheat");
        price.edit("Set filters", root -> {
            root.addProperty("priority", 12);
            root.add("components", JsonParser.parseString("{\"minecraft:custom_data\":{\"quality\":\"fine\"}}"));
        });
        var before = price.json();
        EditorSupportingResources.addRate(price);
        check(!price.json().has("item_count") && price.json().getAsJsonArray("rates").size() == 2, "legacy counts become a complete first rate");
        check(JsonPointers.get(price.json(), "/rates/0/item_count").getAsInt() == 8, "the existing price is retained");
        check(price.json().get("components").equals(before.get("components")), "component filters survive conversion");
        EditorSupportingResources.rangeCounts(price);
        check(JsonPointers.get(price.json(), "/rates/1/item_count/min").getAsInt() == 16, "ranged counts retain their exact value");
        price.undo(); price.undo(); check(price.json().equals(before), "undo restores the complete legacy resource");
        EditorSupportingResources.rangeCounts(price); EditorSupportingResources.addRate(price);
        check(JsonPointers.get(price.json(), "/rates/0/item_count/max").getAsInt() == 8, "adding rates preserves existing ranges");
    }

    private static void malformedCollectionsStayUntouched() throws IOException {
        var workspace = workspace(); var trade = create(workspace, EditorResourceRecipes.Kind.TRADE, "broken");
        trade.edit("Broken entries", root -> root.addProperty("entries", "unfinished"));
        var before = trade.json(); rejects(() -> EditorSupportingResources.addTrade(workspace, trade));
        check(trade.json().equals(before), "guided actions do not replace malformed drafts");
        var price = create(workspace, EditorResourceRecipes.Kind.SELL_PRICE, "mixed");
        price.edit("Mixed rates", root -> root.add("rates", JsonParser.parseString("[]")));
        before = price.json(); rejects(() -> EditorSupportingResources.addRate(price));
        check(price.json().equals(before), "mixed price definitions require an explicit repair");
    }

    private static void kitSlotsPreserveStacksAndUndo() throws IOException {
        var kit = create(workspace(), EditorResourceRecipes.Kind.DUEL_KIT, "practice");
        kit.edit("Custom sword", root -> root.getAsJsonObject("player").getAsJsonArray("inventory").get(0).getAsJsonObject()
                .getAsJsonObject("stack").add("components", JsonParser.parseString("{\"minecraft:damage\":3}")));
        var before = kit.json();
        String pointer = EditorSupportingResources.addInventory(kit, "player");
        check(JsonPointers.get(kit.json(), pointer + "/slot").getAsInt() == 1, "inventory additions choose a free slot");
        EditorSupportingResources.equipment(kit, "player", "head", "minecraft:iron_helmet");
        rejects(() -> EditorSupportingResources.equipment(kit, "player", "head", "minecraft:diamond_helmet"));
        check(JsonPointers.get(kit.json(), "/player/inventory/0/stack/components").equals(JsonPointers.get(before, "/player/inventory/0/stack/components")), "advanced stack components survive guided edits");
        kit.undo(); kit.undo(); check(kit.json().equals(before), "kit actions undo without losing custom stacks");
        rejects(() -> EditorSupportingResources.addInventory(kit, "spectator"));
        check(EditorSupportingResources.itemField(kit, "/villager/equipment/mainhand/id"), "equipment IDs use the visual item picker");
        check(!EditorSupportingResources.itemField(kit, "/id"), "unrelated IDs are not item pickers");
    }

    private static void schemasDescribeEditableValues() throws IOException {
        var workspace = workspace(); var kit = create(workspace, EditorResourceRecipes.Kind.DUEL_KIT, "bounds");
        var schema = EditorSchema.forDocument(kit);
        check(!schema.scalarIssues(kit.json(), "/player/inventory/0/slot", new JsonPrimitive(256)).isEmpty(), "kit slots use runtime bounds");
        check(!schema.scalarIssues(kit.json(), "/combat_style", new JsonPrimitive("unknown")).isEmpty(), "combat style uses supported values");
        var trade = create(workspace, EditorResourceRecipes.Kind.TRADE, "schema");
        check(!EditorSchema.forDocument(trade).at(trade.json(), "/entries/0/cost/count").isEmpty(), "trade costs resolve the runtime schema");
        var price = create(workspace, EditorResourceRecipes.Kind.SELL_PRICE, "bounds");
        EditorSupportingResources.rangeCounts(price);
        check(!EditorSchema.forDocument(price).scalarIssues(price.json(), "/item_count/min", new JsonPrimitive(0)).isEmpty(), "range fields keep numeric validation");
    }

    private static void wordingAndStylesMatchRuntimeRules() throws IOException {
        var workspace = workspace(); var words = create(workspace, EditorResourceRecipes.Kind.WORD_POOL, "greetings");
        check(com.jvn.villagerretaliation.dialogue.DialogueWordPool.read(words.json()).values().size() == 2, "word pool recipe parses with runtime rules");
        String variant = EditorSupportingResources.addWording(words, true);
        check(com.jvn.villagerretaliation.dialogue.DialogueWordPool.read(words.json()).values().size() == 3, "conditional variants start with valid literal wording");
        check(EditorSupportingResources.literalWording(words, "/values/0") && EditorSupportingResources.literalWording(words, variant + "/text"),
                "both plain and conditional word entries avoid unsupported markup controls");
        check(!EditorSchema.forDocument(words).scalarIssues(words.json(), variant + "/text", new JsonPrimitive("<b>Hi</b>")).isEmpty(), "word pools flag unsupported markup");
        words.undo(); check(words.json().getAsJsonArray("values").size() == 2, "adding conditional wording is undoable");
        com.jvn.villagerretaliation.dialogue.DialogueStyle.read(create(workspace, EditorResourceRecipes.Kind.STYLE, "color").json());
        var style = create(workspace, EditorResourceRecipes.Kind.STYLE, "123-fancy");
        check(style.json().get("tag").getAsString().equals("style_123_fancy"), "style tags use supported characters");
        com.jvn.villagerretaliation.dialogue.DialogueStyle.read(style.json());
        rejects(() -> EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.STYLE, "other:123-fancy", null));
    }

    private static void rejects(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected validation failure"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
