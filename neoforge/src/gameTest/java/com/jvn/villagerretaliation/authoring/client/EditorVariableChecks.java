package com.jvn.villagerretaliation.authoring.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class EditorVariableChecks {
    private EditorVariableChecks() { }

    static int run() throws IOException {
        catalogHasUniqueNamesAndAvailability();
        shippedPlaceholdersAreDiscoverable();
        runtimeAndTriggerVariablesAreDiscoverable();
        searchRanksExactNamesAndFindsCategories();
        customNamesPreserveTokensAndRejectMalformedInput();
        return 5;
    }

    private static Set<String> keys() {
        return EditorVariables.groups().stream().flatMap(group -> group.variables().stream())
                .map(EditorVariables.Variable::key).collect(java.util.stream.Collectors.toSet());
    }

    private static void catalogHasUniqueNamesAndAvailability() {
        var names = new HashSet<String>();
        for (var group : EditorVariables.groups()) {
            check(!group.title().isBlank() && !group.availability().isBlank() && !group.variables().isEmpty(), "categories explain their availability");
            for (var variable : group.variables()) {
                check(names.add(variable.key()), "each variable appears once: " + variable.key());
                check(EditorVariables.customKey(variable.token()).equals(variable.key()) && !variable.label().isBlank(), "variables have readable labels and insertable tokens");
            }
        }
    }

    private static void shippedPlaceholdersAreDiscoverable() throws IOException {
        var resources = EditorVariableChecks.class.getClassLoader().getResources("data/villagerretaliation");
        check(resources.hasMoreElements(), "shipped content is on the verification classpath");
        var pattern = Pattern.compile("\\{([a-z][a-z0-9_:.]*)\\}");
        var used = new HashSet<String>();
        while (resources.hasMoreElements()) {
            var resource = resources.nextElement();
            try (var paths = Files.walk(Path.of(java.net.URI.create(resource.toExternalForm())))) {
                for (var path : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                    var matcher = pattern.matcher(Files.readString(path));
                    while (matcher.find()) used.add(matcher.group(1));
                }
            }
        }
        check(used.size() >= 150, "the coverage check reads the shipped dialogue and quests: " + used.size() + " variables");
        used.removeAll(keys());
        check(used.isEmpty(), "every shipped placeholder is discoverable: " + used);
    }

    private static void runtimeAndTriggerVariablesAreDiscoverable() {
        var keys = keys();
        check(keys.containsAll(com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(6000).keySet()), "world clock variables match the runtime");
        check(keys.containsAll(new com.jvn.villagerretaliation.authoring.preview.PreviewConditions().placeholders().keySet()), "preview context variables are discoverable");
        var event = com.jvn.villagerretaliation.quest.QuestDefinition.TriggerEvent.values()[0];
        var context = new com.jvn.villagerretaliation.quest.QuestTriggerContext(event, 42, "start", Map.of("item_count", "3", "block_x", "12"), null);
        check(keys.containsAll(context.replacements().keySet()), "quest trigger payload names include the runtime prefix");
        check(keys.containsAll(Set.of("objective_fact_scope", "held_item_enchantment_level", "campaign_transaction_price", "retaliation_target_type", "ancestor_possessive")), "specialized runtime families remain discoverable");
    }

    private static void searchRanksExactNamesAndFindsCategories() {
        check(EditorVariables.matching("").equals(EditorVariables.groups()), "blank search preserves category order");
        for (String key : List.of("day", "time", "player", "item", "objective_item", "parent"))
            check(EditorVariables.matching(" {" + key.toUpperCase(java.util.Locale.ROOT) + "} ").getFirst().variables().getFirst().key().equals(key), "exact names rank ahead of partial matches: " + key);
        check(EditorVariables.matching("family").size() == 1 && EditorVariables.matching("family").getFirst().variables().size() > 20, "categories are searchable");
        var matches = EditorVariables.matching("held durability percent").stream().flatMap(group -> group.variables().stream()).toList();
        check(matches.size() == 1 && matches.getFirst().key().equals("held_item_durability_percent"), "search combines words from labels and tokens");
        check(EditorVariables.matching("not_a_real_builtin_variable").isEmpty(), "unknown names do not masquerade as built-in values");
    }

    private static void customNamesPreserveTokensAndRejectMalformedInput() {
        check(EditorVariables.customKey(" {trigger_criterion_boss_killed} ").equals("trigger_criterion_boss_killed"), "custom trigger payloads accept braces without inserting them twice");
        check(EditorVariables.customKey("my_pack.npc:name").equals("my_pack.npc:name"), "custom names preserve supplied identifiers");
        for (String invalid : List.of("", "{}", "{unfinished", "two words", "name}suffix", "<b>name</b>"))
            check(EditorVariables.customKey(invalid).isEmpty(), "malformed variable names are rejected");
        var edit = EditorTextEdit.insert("Hello, world!", 7, 12, "{objective_item_id}");
        check(edit.text().equals("Hello, {objective_item_id}!") && edit.end() == EditorTextEdit.bytes("Hello, {objective_item_id}"), "catalog tokens preserve selection replacement and caret position");
    }

    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
