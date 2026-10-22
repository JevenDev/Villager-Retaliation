package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.selection.*;
import com.jvn.villagerretaliation.quest.schema.v3.QuestV3Format;
import com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class WorldCampaignChecks {
    private WorldCampaignChecks() { }

    public static int run() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var resources = new MemoryResources();
        resources.add("test:world_campaigns/alpha/campaign.json", manifest("alpha", false));
        resources.add("test:world_campaigns/beta/campaign.json", manifest("beta", true));
        resources.add("test:world_campaigns/alpha/dialogue/en_us/story.json", "{\"entries\":[{\"id\":\"alpha\"}]}");
        resources.add("test:world_campaigns/beta/dialogue/en_us/other.json", "{\"entries\":[{\"id\":\"beta\"}]}");
        resources.add("test:dialogue/en_us/default.json", "{\"entries\":[{\"id\":\"default\"}]}");
        resources.add("test:dialogue/en_us/shared.json", "{\"world_campaign\":\"villagerretaliation:shared\",\"entries\":[{\"id\":\"shared\"}]}");
        resources.add("test:dialogue/en_us/mixed.json", "{\"entries\":[{\"id\":\"a\",\"world_campaign\":\"test:alpha\"},{\"id\":\"b\",\"world_campaign\":\"test:beta\"}]}");
        var catalog = WorldCampaignCatalog.load(resources);
        check(catalog.require(id("test:alpha"), false).image().equals(WorldCampaign.CARD_FALLBACK), "missing card artwork uses the fallback");
        WorldCampaign alpha = catalog.require(id("test:alpha"), true);
        reject(() -> catalog.require(id("test:beta"), true));
        reject(() -> catalog.require(id("test:missing"), false));
        var selected = new WorldCampaignSelection.Selection(alpha.id(), false);
        var stacks = WorldCampaignResources.stacks(resources, selected, "dialogue/en_us");
        check(stacks.size() == 3, "only selected, shared, and matching mixed entries are exposed");
        var mixed = stacks.stream().filter(stack -> stack.location().getPath().endsWith("mixed.json")).findFirst().orElseThrow();
        try (var reader = mixed.layersLowToHigh().getLast().resource().openAsReader()) {
            var entries = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries");
            check(entries.size() == 1 && entries.get(0).getAsJsonObject().get("id").getAsString().equals("a"), "mixed dialogue filters each entry");
        }
        check(WorldCampaignResources.stacks(resources, new WorldCampaignSelection.Selection(alpha.id(), true), "dialogue/en_us").size() == 4,
                "campaign can include default content");
        resources.add("test:world_campaigns/alpha/dialogue/en_us/conflict.json", "{\"world_campaign\":\"test:beta\"}");
        reject(() -> WorldCampaignResources.stacks(resources, selected, "dialogue/en_us"));
        resources.values.remove(id("test:world_campaigns/alpha/dialogue/en_us/conflict.json"));
        resources.add("test:quests/story/quest/quest.json", "{\"world_campaign\":\"test:alpha\"}");
        resources.add("test:quests/story/quest/locales/en_us.json", "{\"title\":\"Title\"}");
        check(WorldCampaignResources.stacks(resources, selected, "quests").size() == 2, "quest companions inherit ownership");
        resources.add("test:campaigns/story/campaign.json", "{\"world_campaign\":\"test:alpha\"}");
        resources.add("test:campaigns/story/locales/en_us.json", "{\"title\":\"Title\"}");
        check(WorldCampaignResources.stacks(resources, selected, "campaigns").size() == 2, "story arc locales inherit ownership");
        resources.add("test:dialogue/en_us/story.json", "{\"world_campaign\":\"test:alpha\",\"entries\":[{\"id\":\"duplicate\"}]}");
        reject(() -> WorldCampaignResources.stacks(resources, selected, "dialogue/en_us"));
        resources.values.remove(id("test:dialogue/en_us/story.json"));
        resources.add("test:world_campaigns/alpha/dialogue/en_us/empty.json", "{\"replace\":true,\"entries\":[]}");
        check(WorldCampaignResources.stacks(resources, selected, "dialogue/en_us").stream()
                .anyMatch(stack -> stack.location().getPath().endsWith("empty.json")), "empty replacement layers remain effective");
        resources.values.remove(id("test:world_campaigns/alpha/dialogue/en_us/empty.json"));
        var layered = new MemoryResources();
        layered.add("test:world_campaigns/alpha/campaign.json", manifest("alpha", false));
        layered.add("test:world_campaigns/beta/campaign.json", manifest("beta", false));
        layered.add("base", "test:quests/story/quest/quest.json", "{\"world_campaign\":\"test:alpha\"}");
        layered.add("base", "test:quests/story/quest/locales/en_us.json", "{\"title\":\"Alpha\"}");
        layered.add("patch", "test:quests/story/quest/quest.json", "{\"world_campaign\":\"test:beta\"}");
        layered.add("patch", "test:quests/story/quest/locales/en_us.json", "{\"title\":\"Beta\"}");
        for (String owner : List.of("alpha", "beta")) {
            var owned = WorldCampaignResources.stacks(layered, new WorldCampaignSelection.Selection(id("test:" + owner), false), "quests");
            check(owned.size() == 2 && owned.stream().allMatch(stack -> stack.layersLowToHigh().size() == 1
                    && stack.layersLowToHigh().getFirst().resource().sourcePackId().equals(owner.equals("alpha") ? "base" : "patch")),
                    "companion ownership follows its own pack layer");
        }
        var world = Files.createTempDirectory("vr-campaign-selection-");
        try {
            CampaignWorldModeSavedData.createSelection(world, alpha, "test", null);
            var saved = CampaignWorldModeSavedData.readSelection(world, null);
            check(saved.worldCampaign().equals(alpha.id()) && !saved.includeDefaultContent(), "selection survives disk round trip");
            boolean blocked = false;
            try { CampaignWorldModeSavedData.createSelection(world, alpha, "test", null); }
            catch (java.io.IOException expected) { blocked = true; }
            check(blocked, "existing selection cannot be overwritten");
            var old = new net.minecraft.nbt.CompoundTag();
            old.putInt("DataVersion", 1);
            old.putString("Mode", "campaign");
            check(CampaignWorldModeSavedData.load(old, null).worldCampaign() == null, "old worlds keep their legacy selection behavior");
            var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(world.resolve("authoring"));
            var card = workspace.createDocument(com.jvn.villagerretaliation.authoring.core.DefinitionKind.WORLD_CAMPAIGN, "test:authored");
            var first = java.nio.file.Path.of(WorldCampaignChecks.class.getResource("/villager_retaliation_cover.png").toURI());
            var second = java.nio.file.Path.of(WorldCampaignChecks.class.getResource("/assets/vr_explored_map/textures/gui/village.png").toURI());
            workspace.importCampaignImage(card, first);
            workspace.save();
            workspace.importCampaignImage(card, second);
            check(card.dirty(), "replacing a campaign image marks the document unsaved");
            card.undo();
            check(Arrays.equals(workspace.campaignImage(card), Files.readAllBytes(first)), "image undo restores the previous asset");
            card.redo();
            workspace.save();
            var reopened = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.open(world.resolve("authoring"));
            check(Arrays.equals(reopened.campaignImage(reopened.documents().getFirst()), Files.readAllBytes(second)), "campaign image survives reopening");
            reopened.exportPack(world.resolve("export"));
            String exported = card.json().get("image").getAsString().replace(':', '/');
            check(Arrays.equals(Files.readAllBytes(world.resolve("export/data/" + exported)), Files.readAllBytes(second)), "campaign export includes its selected image");
        } finally {
            try (var paths = Files.walk(world)) {
                for (var file : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        JsonObject quest = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v3","id":"test:quest","localization_prefix":"test.quest.quest",
                 "revision":4,"world_campaign":"test:alpha","story_arc":"test:arc","metadata":{"questline":"line"},
                 "display":{"title":"Journal","tracker_title":"Tracker"},"provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"start","stages":[{"id":"start","objectives":[],"transition":{"complete":true}}]}
                """).getAsJsonObject();
        check(QuestJsonSchema.validate(quest, QuestV3Schema.export()).isEmpty(), "V3 authored schema accepts canonical sections");
        JsonObject runtime = QuestV3Format.runtime(quest);
        check(runtime.getAsJsonObject("metadata").get("campaign").getAsString().equals("test:arc"), "story ownership reaches existing engine");
        check(runtime.getAsJsonObject("ui").get("title").getAsString().equals("Tracker"), "separate tracker title survives lowering");
        check(runtime.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonObject("next").get("complete").getAsBoolean(), "terminal transition survives lowering");
        JsonObject localized = quest.deepCopy();
        localized.getAsJsonObject("display").add("title", JsonParser.parseString("{\"key\":\"#missing\"}"));
        String authoredPath = "data/test/world_campaigns/alpha/quests/line/quest/quest.json";
        var document = new com.jvn.villagerretaliation.authoring.core.AuthoringDocument(
                com.jvn.villagerretaliation.authoring.core.DefinitionKind.QUEST, authoredPath, localized);
        check(com.jvn.villagerretaliation.authoring.core.AuthoringValidation.validate(List.of(document)).stream()
                .anyMatch(issue -> issue.resourcePath().equals(authoredPath) && issue.severity()
                        == com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR),
                "campaign-folder bundle diagnostics point to the authored file");
        quest.add("ui", new JsonObject());
        check(!QuestJsonSchema.validate(quest, QuestV3Schema.export()).isEmpty(), "V2 aliases are rejected in V3");
        System.out.println("PASS campaign ownership, permanent selection, and V3 lowering checks");
        authoringResourcesKeepSelectedScopesAndOwnership();
        return 27 + addons() + markdownDescriptions() + malformedLayers() + migrationReceipts() + migrationFingerprintFixtures();
    }


    private static void authoringResourcesKeepSelectedScopesAndOwnership() throws Exception {
        var resources = new MemoryResources();
        resources.add("test:world_campaigns/alpha/campaign.json", manifest("alpha", false));
        resources.add("test:world_campaigns/beta/campaign.json", manifest("beta", false));
        resources.add("test:campaign_addons/enabled/addon.json", addon("enabled", ""));
        resources.add("test:campaign_addons/disabled/addon.json", addon("disabled", ""));
        resources.add("test:world_campaigns/alpha/quests/story/a/quest.json", "{\"id\":\"test:story\"}");
        resources.add("test:world_campaigns/beta/dialogue/en_us/other.json", "{}");
        resources.add("test:campaign_addons/enabled/dialogue/en_us/addon.json", "{}");
        resources.add("test:campaign_addons/disabled/dialogue/en_us/addon.json", "{}");
        resources.add("test:dialogue/en_us/default.json", "{}");
        resources.add("test:quests/story/shared/quest.json", "{\"world_campaign\":\"villagerretaliation:shared\"}");
        resources.add("test:quests/story/shared/locales/en_us.json", "{\"title\":\"Shared quest\"}");
        resources.add("test:quests/story/hidden/quest.json", "{\"world_campaign\":\"test:beta\"}");
        resources.add("test:quests/story/hidden/locales/en_us.json", "{}");
        resources.add("test:dialogue/en_us/mixed.json", "{\"entries\":[{\"id\":\"active\",\"world_campaign\":\"test:alpha\"},{\"id\":\"hidden\",\"world_campaign\":\"test:beta\"}]}");
        resources.add("override", "test:campaign_addons/enabled/dialogue/en_us/addon.json", "{\"override\":true}");
        resources.add("test:world_campaigns/alpha/campaign.json", manifest("alpha", false).replace("\"name\":", "\"description_file\":\"test:descriptions/alpha.md\",\"name\":"));
        resources.add("test:descriptions/alpha.md", "Campaign description");
        resources.add("test", "test:quests/story/layered/quest.json", "{\"world_campaign\":\"test:alpha\"}");
        resources.add("test", "test:quests/story/layered/locales/en_us.json", "{\"title\":\"Alpha\"}");
        resources.add("override", "test:quests/story/layered/quest.json", "{\"world_campaign\":\"test:beta\"}");
        resources.add("override", "test:quests/story/layered/locales/en_us.json", "{\"title\":\"Beta\"}");
        var selection = new WorldCampaignSelection.Selection(id("test:alpha"), false, List.of(id("test:enabled")));
        var selected = WorldCampaignResources.authoringResources(resources, selection);
        check(selected.containsKey(id("test:world_campaigns/alpha/campaign.json")), "editor includes the selected campaign card");
        check(selected.containsKey(id("test:descriptions/alpha.md")), "editor includes external campaign descriptions");
        check(selected.containsKey(id("test:world_campaigns/alpha/quests/story/a/quest.json")), "editor preserves physical quest scope");
        check(selected.containsKey(id("test:campaign_addons/enabled/addon.json")), "editor includes enabled addon cards");
        check(selected.keySet().stream().noneMatch(key -> key.getPath().contains("beta/") || key.getPath().contains("disabled/")
                || key.getPath().contains("hidden/") || key.getPath().endsWith("default.json")), "editor excludes inactive scopes and their companions");
        check(selected.containsKey(id("test:quests/story/shared/locales/en_us.json")), "editor includes shared quest localization");
        check(selected.get(id("test:quests/story/layered/quest.json")).sourcePackId().equals("test"),
                "an unselected higher layer does not hide the active quest");
        check(selected.get(id("test:quests/story/layered/locales/en_us.json")).sourcePackId().equals("test"),
                "quest companions follow the campaign ownership of their own layer");
        try (var reader = selected.get(id("test:dialogue/en_us/mixed.json")).openAsReader()) {
            var entries = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries");
            check(entries.size() == 1 && entries.get(0).getAsJsonObject().get("world_campaign").getAsString().equals("test:alpha"),
                    "editor filters mixed entries while preserving their ownership");
        }
        check(selected.get(id("test:campaign_addons/enabled/dialogue/en_us/addon.json")).sourcePackId().equals("override"),
                "editor uses datapack priority at each physical path");
        check(WorldCampaignResources.authoringResources(resources, new WorldCampaignSelection.Selection(id("test:alpha"), true))
                .containsKey(id("test:dialogue/en_us/default.json")), "editor honors include default content");
        check(WorldCampaignResources.authoringResources(resources, new WorldCampaignSelection.Selection(null, true)).keySet().stream()
                .noneMatch(key -> key.getPath().startsWith("world_campaigns/") || key.getPath().startsWith("campaign_addons/")),
                "legacy worlds do not import every campaign and addon");
    }

    private static int addons() throws Exception {
        var resources = new MemoryResources();
        resources.add("test:world_campaigns/alpha/campaign.json", manifest("alpha", false));
        resources.add("test:world_campaigns/beta/campaign.json", manifest("beta", false));
        resources.add("base", "test:campaign_addons/general/addon.json", addon("general", ""));
        resources.add("base", "test:campaign_addons/specific/addon.json", addon("specific", ",\"campaigns\":[\"test:alpha\"]"));
        resources.add("base", "test:campaign_addons/excluded/addon.json", addon("excluded", ",\"incompatible_campaigns\":[\"test:alpha\"]"));
        resources.add("base", "test:campaign_addons/dependent/addon.json", addon("dependent", ",\"requires\":[\"test:general\"]"));
        resources.add("base", "test:campaign_addons/conflict/addon.json", addon("conflict", ",\"incompatible_addons\":[\"test:general\"]"));
        resources.add("base", "test:campaign_addons/disabled/addon.json", addon("disabled", ""));
        resources.add("base", "test:campaign_addons/disabled/dialogue/en_us/broken.json", "[broken disabled content");
        resources.add("base", "test:campaign_addons/late/addon.json", addon("late", ",\"allow_existing_worlds\":true"));
        var catalog = WorldCampaignCatalog.load(resources);
        var general = id("test:general");
        var specific = id("test:specific");
        var alpha = id("test:alpha");
        var enabled = new WorldCampaignSelection.Selection(alpha, false, List.of(general, specific));
        catalog.validate(enabled, true);
        check(catalog.choices().size() == 2 && catalog.addonChoices().size() == 7, "addons do not become campaign choices");
        catalog.validate(new WorldCampaignSelection.Selection(id("test:beta"), false, List.of(general)), true);
        reject(() -> catalog.validate(new WorldCampaignSelection.Selection(id("test:beta"), false, List.of(specific)), true));
        reject(() -> catalog.validate(new WorldCampaignSelection.Selection(alpha, false, List.of(id("test:excluded"))), true));
        reject(() -> catalog.validate(new WorldCampaignSelection.Selection(alpha, false, List.of(id("test:missing"))), false));
        reject(() -> catalog.validate(new WorldCampaignSelection.Selection(alpha, false, List.of(id("test:dependent"))), true));
        reject(() -> catalog.validate(new WorldCampaignSelection.Selection(alpha, false, List.of(id("test:dependent"), general)), true));
        catalog.validate(new WorldCampaignSelection.Selection(alpha, false, List.of(general, id("test:dependent"))), true);
        reject(() -> catalog.validate(new WorldCampaignSelection.Selection(alpha, false, List.of(general, id("test:conflict"))), true));
        reject(() -> catalog.validate(new WorldCampaignSelection.Selection(alpha, false, List.of(id("test:conflict"), general)), true));
        reject(() -> new WorldCampaignSelection.Selection(alpha, false, List.of(general, general)));
        reject(() -> new WorldCampaignSelection.Selection(null, true, List.of(general)));
        reject(() -> WorldCampaignAddon.read(JsonParser.parseString(addon("invalid", ",\"campaigns\":[\"alpha\"]")).getAsJsonObject()));
        reject(() -> WorldCampaignAddon.read(JsonParser.parseString(addon("invalid", ",\"requires\":[\"test:invalid\"]")).getAsJsonObject()));
        reject(() -> WorldCampaignAddon.read(JsonParser.parseString(addon("invalid", ",\"campaigns\":[\"test:alpha\"],\"incompatible_campaigns\":[\"test:alpha\"]")).getAsJsonObject()));
        resources.add("base", "test:campaign_addons/general/dialogue/en_us/shared.json", "{\"value\":\"general\"}");
        resources.add("base", "test:campaign_addons/specific/dialogue/en_us/shared.json", "{\"value\":\"specific\"}");
        resources.add("base", "test:world_campaigns/alpha/dialogue/en_us/shared.json", "{\"value\":\"campaign\"}");
        resources.add("patch", "test:campaign_addons/general/dialogue/en_us/shared.json", "{\"value\":\"general patch\"}");
        var stack = WorldCampaignResources.stacks(resources, enabled, "dialogue/en_us");
        check(stack.size() == 1 && stack.getFirst().layersLowToHigh().size() == 4, "enabled scopes overlay the campaign even within one datapack");
        var layers = stack.getFirst().layersLowToHigh();
        check(layers.get(0).priority() < layers.get(1).priority() && layers.get(1).priority() < layers.get(2).priority()
                && layers.get(2).priority() < layers.get(3).priority(), "addon and pack priorities are distinct and ordered");
        try (var reader = layers.getLast().resource().openAsReader()) {
            check(JsonParser.parseReader(reader).getAsJsonObject().get("value").getAsString().equals("specific"), "highest addon beats a higher datapack layer of a lower addon");
        }
        var reversed = new WorldCampaignSelection.Selection(alpha, false, List.of(specific, general));
        try (var reader = WorldCampaignResources.stacks(resources, reversed, "dialogue/en_us").getFirst().layersLowToHigh().getLast().resource().openAsReader()) {
            check(JsonParser.parseReader(reader).getAsJsonObject().get("value").getAsString().equals("general patch"), "reordering addons changes effective content");
        }
        check(WorldCampaignResources.stacks(resources, new WorldCampaignSelection.Selection(alpha, false), "dialogue/en_us")
                .getFirst().layersLowToHigh().size() == 1, "disabled addons contribute no content");
        check(WorldCampaignResources.stacks(resources, new WorldCampaignSelection.Selection(null, true), "dialogue/en_us")
                .getFirst().layersLowToHigh().size() == 1, "legacy worlds do not implicitly enable addons");
        String quest = """
                {"schema":"villagerretaliation:quest/v3","id":"test:quest","localization_prefix":"test.quest.quest",
                 "metadata":{"questline":"line"},"provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"start","stages":[{"id":"start","objectives":[],"transition":{"complete":true}}]}
                """;
        resources.add("base", "test:world_campaigns/alpha/quests/line/quest/quest.json", quest);
        resources.add("base", "test:world_campaigns/alpha/quests/line/quest/locales/en_us.json", "{}");
        resources.add("base", "test:campaign_addons/general/quests/line/quest/quest.json", quest);
        resources.add("base", "test:campaign_addons/specific/quests/line/quest/quest.json", quest);
        resources.add("base", "test:campaign_addons/specific/quests/line/quest/locales/en_us.json", "{\"test.quest.quest.title\":\"Addon title\"}");
        var raw = new ArrayList<com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions.RawResource>();
        for (var bundleStack : WorldCampaignResources.stacks(resources, enabled, "quests")) {
            for (var layer : bundleStack.layersLowToHigh()) {
                try (var reader = layer.resource().openAsReader()) {
                    raw.add(com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions.RawResource.valid(
                            layer.priority(), layer.resource().sourcePackId(), layer.location(), JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        var compiled = com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions.compile(raw, null);
        check(compiled.bundles().size() == 1 && compiled.diagnostics().isEmpty(), "quest transactions accept same-pack addon layers and companion locales: " + compiled.diagnostics());
        resources.add("base", "test:campaign_addons/general/quests/line/hidden/quest.json", "{\"world_campaign\":\"test:beta\"}");
        resources.add("base", "test:campaign_addons/general/quests/line/hidden/locales/en_us.json", "{}");
        check(WorldCampaignResources.stacks(resources, enabled, "quests").stream().noneMatch(value -> value.location().getPath().contains("/hidden/")),
                "addon companions inherit campaign ownership from their scoped manifest");
        var world = Files.createTempDirectory("vr-campaign-addons-");
        try {
            CampaignWorldModeSavedData.createSelection(world, catalog.require(alpha, true), enabled.addons(), "test", null);
            var saved = CampaignWorldModeSavedData.readSelection(world, null);
            check(saved.addons().equals(enabled.addons()), "addon order survives a disk round trip");
            var tag = saved.save(new net.minecraft.nbt.CompoundTag(), null);
            tag.remove("CampaignAddons");
            tag.putInt("DataVersion", 2);
            check(CampaignWorldModeSavedData.load(tag, null).addons().isEmpty(), "older selected worlds migrate without enabling addons");
            tag.putString("CampaignAddons", "invalid");
            check(CampaignWorldModeSavedData.load(tag, null).save(new net.minecraft.nbt.CompoundTag(), null).equals(tag), "malformed addon data is preserved");
            var duplicates = new net.minecraft.nbt.ListTag();
            duplicates.add(net.minecraft.nbt.StringTag.valueOf(general.toString()));
            duplicates.add(net.minecraft.nbt.StringTag.valueOf(general.toString()));
            tag.put("CampaignAddons", duplicates);
            check(!CampaignWorldModeSavedData.load(tag, null).diagnostic().isEmpty(), "duplicate saved addons fail closed");
            var expanded = List.of(general, specific, id("test:late"));
            CampaignWorldModeSavedData.addToExistingWorld(world, expanded, catalog);
            var updated = CampaignWorldModeSavedData.readSelection(world, null);
            check(updated.addons().equals(expanded) && updated.worldCampaign().equals(alpha), "safe late additions persist without changing the campaign");
            check(updated.selectionSource().equals(saved.selectionSource()) && updated.selectedAt() == saved.selectedAt(), "late additions preserve selection authority and time");
            check(!catalog.addons().get(general).allowExistingWorlds(), "addons default to new worlds only");
            check(catalog.addonChangeProblem(enabled, List.of(general, specific, id("test:dependent"))) != null, "new-world-only addons cannot be added later");
            check(catalog.addonChangeProblem(enabled, List.of(specific)) != null, "saved addons cannot be removed");
            check(catalog.addonChangeProblem(enabled, List.of(specific, general)) != null, "saved addon relative priority is preserved");
            byte[] before = Files.readAllBytes(world.resolve("data").resolve(CampaignWorldModeSavedData.DATA_NAME + ".dat"));
            boolean blocked = false;
            try { CampaignWorldModeSavedData.addToExistingWorld(world, List.of(specific), catalog); }
            catch (java.io.IOException expected) { blocked = true; }
            check(blocked && Arrays.equals(before, Files.readAllBytes(world.resolve("data").resolve(CampaignWorldModeSavedData.DATA_NAME + ".dat"))),
                    "rejected late changes leave saved bytes untouched");
        } finally {
            try (var paths = Files.walk(world)) {
                for (var file : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        var mismatch = new MemoryResources();
        mismatch.add("test:campaign_addons/wrong/addon.json", addon("general", ""));
        reject(() -> WorldCampaignCatalog.load(mismatch));
        System.out.println("PASS addon compatibility, dependencies, conflicts, scoped content, priority, quest transactions, and permanent saves");
        return 36;
    }

    private static String addon(String name, String fields) {
        return "{\"schema\":\"villagerretaliation:campaign_addon/v1\",\"id\":\"test:" + name
                + "\",\"name\":\"" + name + "\",\"description\":\"Test addon\"" + fields + "}";
    }

    private static int markdownDescriptions() {
        var resources = new MemoryResources();
        var root = JsonParser.parseString(manifest("alpha", false)).getAsJsonObject();
        root.addProperty("description_file", "test:world_campaigns/alpha/description.md");
        resources.add("test:world_campaigns/alpha/campaign.json", root.toString());
        String markdown = "# Village stories\n\n**Trust** and caf\u00e9 conversations.\n![Cover](cover.png)\n";
        resources.add("test:world_campaigns/alpha/description.md", markdown);
        var campaign = WorldCampaignCatalog.load(resources).require(id("test:alpha"), true);
        check(campaign.description().equals(markdown), "Markdown files retain headings, formatting, image references and UTF-8 text");
        check(campaign.name().equals("alpha") && !campaign.locked(), "Markdown descriptions preserve campaign identity and availability");
        root.remove("description");
        check(QuestJsonSchema.validate(root, WorldCampaignSchema.export()).isEmpty(), "the exported schema accepts a Markdown reference without inline text");
        check(WorldCampaign.read(root).description().isEmpty(), "Markdown-only manifests have no inline description");
        resources.add("test:world_campaigns/alpha/campaign.json", root.toString());
        check(WorldCampaignCatalog.load(resources).require(id("test:alpha"), true).description().equals(markdown),
                "Markdown-only manifests load their referenced description");
        root.addProperty("description", false);
        reject(() -> WorldCampaign.read(root));
        root.add("description", com.google.gson.JsonNull.INSTANCE);
        reject(() -> WorldCampaign.read(root));
        root.remove("description");
        var neither = root.deepCopy();
        neither.remove("description_file");
        check(!QuestJsonSchema.validate(neither, WorldCampaignSchema.export()).isEmpty(), "the exported schema requires at least one description source");
        reject(() -> WorldCampaign.read(neither));
        resources.add("override", "test:world_campaigns/alpha/description.md", "# Updated description");
        check(WorldCampaignCatalog.load(resources).require(id("test:alpha"), true).description().equals("# Updated description"),
                "datapacks can override referenced Markdown descriptions");
        resources.add("test:world_campaigns/alpha/description.md", "x".repeat(4096));
        check(WorldCampaignCatalog.load(resources).require(id("test:alpha"), true).description().length() == 4096,
                "Markdown descriptions accept the existing character limit");
        resources.add("test:world_campaigns/alpha/description.md", "x".repeat(4097));
        reject(() -> WorldCampaignCatalog.load(resources));
        resources.values.remove(id("test:world_campaigns/alpha/description.md"));
        reject(() -> WorldCampaignCatalog.load(resources));
        root.addProperty("description_file", "test:world_campaigns/alpha/description.json");
        reject(() -> WorldCampaign.read(root));
        root.addProperty("description_file", true);
        reject(() -> WorldCampaign.read(root));
        resources.add("override", "test:world_campaigns/alpha/campaign.json", manifest("alpha", false));
        resources.add("test:world_campaigns/alpha/description.md", "# Unreferenced description");
        check(WorldCampaignCatalog.load(resources).require(id("test:alpha"), true).description().equals("Test campaign"),
                "inline descriptions remain authoritative without a Markdown reference");
        System.out.println("PASS campaign Markdown descriptions and resource validation");
        return 16;
    }

    private static int malformedLayers() throws Exception {
        var resources = new MemoryResources();
        resources.add("test:world_campaigns/alpha/campaign.json", manifest("alpha", false));
        resources.add("test:world_campaigns/beta/campaign.json", manifest("beta", false));
        String quest = """
                {"schema":"villagerretaliation:quest/v3","id":"test:quest","localization_prefix":"test.quest.quest",
                 "world_campaign":"test:alpha","metadata":{"questline":"line"},"provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"start","stages":[{"id":"start","objectives":[],"transition":{"complete":true}}]}
                """;
        resources.add("base", "test:quests/line/quest/quest.json", quest);
        resources.add("base", "test:quests/line/quest/locales/en_us.json", "{}");
        resources.add("patch", "test:quests/line/quest/quest.json", "{broken JSON");
        resources.add("patch", "test:quests/line/quest/locales/en_us.json", "{}");
        resources.add("test:world_campaigns/beta/quests/line/unselected/quest.json", "{broken unselected JSON");
        resources.add("test:world_campaigns/alpha/dialogue/en_us/broken.json", "[1, 2]");
        resources.add("test:world_campaigns/alpha/dialogue/en_us/valid.json", "{\"entries\":[{\"id\":\"valid\"}]}");
        var selection = new WorldCampaignSelection.Selection(id("test:alpha"), false);
        var stacks = WorldCampaignResources.stacks(resources, selection, "quests");
        check(stacks.size() == 2, "malformed selected layers do not interrupt discovery or expose unselected folders");
        var raw = new ArrayList<com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions.RawResource>();
        for (var stack : stacks) for (var layer : stack.layersLowToHigh()) {
            int priority = layer.resource().sourcePackId().equals("patch") ? 1 : 0;
            try (var reader = layer.resource().openAsReader()) {
                raw.add(com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions.RawResource.valid(
                        priority, layer.resource().sourcePackId(), layer.location(), JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (java.io.IOException exception) {
                raw.add(com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions.RawResource.malformed(
                        priority, layer.resource().sourcePackId(), layer.location(), exception.getMessage()));
            }
        }
        check(raw.stream().filter(value -> !value.valid()).count() == 2,
                "a malformed quest and its companion retain rejected-layer provenance");
        var compiled = com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions.compile(raw, null);
        check(compiled.bundles().size() == 1 && !compiled.diagnostics().isEmpty(), "malformed override retains its valid lower quest");
        var bundle = compiled.bundles().values().iterator().next();
        check(bundle.source(com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath.Kind.QUEST, id("test:quest")).pack().equals("base"),
                "lower quest stays effective after malformed override rejection");
        var dialogue = WorldCampaignResources.stacks(resources, selection, "dialogue/en_us");
        int accepted = 0;
        for (var stack : dialogue) {
            var layer = stack.layersLowToHigh().getLast();
            if (com.jvn.villagerretaliation.util.DatapackResourceLoader.readObject(layer.location(), "dialogue", layer.resource()).isPresent()) accepted++;
        }
        check(accepted == 1, "one malformed dialogue file does not block independent valid dialogue");
        return 5;
    }

    private static int migrationReceipts() throws Exception {
        var root = java.nio.file.Path.of(WorldCampaignChecks.class.getResource("/data/villagerretaliation/quests").toURI());
        int count = 0;
        List<java.nio.file.Path> quests;
        try (var files = Files.walk(root)) {
            quests = files.filter(path -> path.getFileName().toString().equals("quest.json")).toList();
        }
        for (var quest : quests) {
            JsonObject authored = JsonParser.parseString(Files.readString(quest)).getAsJsonObject();
            ResourceLocation questId = id(authored.get("id").getAsString());
            JsonObject definitions = new JsonObject();
            try (var files = Files.walk(quest.getParent())) {
                for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                    String relative = "quests/" + root.relativize(file).toString().replace('\\', '/');
                    var classified = com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath.classify(id("villagerretaliation:" + relative));
                    check(classified.valid(), "built-in companion has a canonical path");
                    if (classified.path().kind() == com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath.Kind.LOCALE) continue;
                    JsonObject value = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    String kind = classified.path().kind().name().toLowerCase(Locale.ROOT);
                    if (!definitions.has(kind)) definitions.add(kind, new JsonObject());
                    definitions.getAsJsonObject(kind).add(value.get("id").getAsString(), value);
                }
            }
            String name = questId.getPath();
            var file = root.resolveSibling("quest_migrations").resolve(name + ".json");
            JsonObject receipt = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String current = com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints.definition(definitions);
            String previous = receipt.get("from_fingerprint").getAsString();
            check(com.jvn.villagerretaliation.quest.content.bundle.QuestBundleMigrations.matches(receipt, questId, previous, current),
                    "migration receipt matches the runtime fingerprint: " + questId);
            check(!com.jvn.villagerretaliation.quest.content.bundle.QuestBundleMigrations.matches(receipt, questId, current, current),
                    "migration receipt cannot authorize an unrelated pending reward");
            count++;
        }
        check(count == 99, "every built-in quest has a verified migration receipt");
        System.out.println("PASS " + count + " built-in V3 bundles and reward migration fingerprints");
        return count;
    }

    private static int migrationFingerprintFixtures() throws Exception {
        JsonObject fixtures;
        try (var input = WorldCampaignChecks.class.getResourceAsStream("/quest_v3/migration_fingerprints.json")) {
            fixtures = JsonParser.parseString(new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (var value : fixtures.getAsJsonArray("values")) {
            var example = value.getAsJsonObject();
            String actual = com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints.definition(
                    JsonParser.parseString(example.get("source").getAsString()));
            check(actual.equals(example.get("fingerprint").getAsString()), "migration fingerprint matches Java: " + example.get("name"));
        }
        JsonObject fixture = fixtures.getAsJsonObject("bundle");
        JsonObject definitions = new JsonObject();
        for (String kind : List.of("quest", "reward")) {
            JsonObject value = JsonParser.parseString(fixture.get(kind).getAsString()).getAsJsonObject();
            JsonObject entries = new JsonObject();
            entries.add(value.get("id").getAsString(), value);
            definitions.add(kind, entries);
        }
        String before = com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints.definition(definitions);
        check(before.equals(fixture.get("before").getAsString()), "old bundle receipt preserves decimal spellings");
        definitions.getAsJsonObject("quest").add("test:example", JsonParser.parseString(fixture.get("converted_quest").getAsString()));
        String after = com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints.definition(definitions);
        check(after.equals(fixture.get("after").getAsString()), "new bundle receipt preserves unchanged companion decimals");
        JsonObject receipt = new JsonObject();
        receipt.addProperty("schema", "villagerretaliation:quest_migration/v1");
        receipt.addProperty("quest", "test:example");
        receipt.addProperty("from_fingerprint", before);
        receipt.addProperty("to_fingerprint", after);
        check(com.jvn.villagerretaliation.quest.content.bundle.QuestBundleMigrations.matches(receipt, id("test:example"), before, after),
                "pending rewards accept the format-only migration");
        definitions.getAsJsonObject("reward").getAsJsonObject("test:reward").addProperty("changed", true);
        check(!com.jvn.villagerretaliation.quest.content.bundle.QuestBundleMigrations.matches(receipt, id("test:example"), before,
                        com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints.definition(definitions)),
                "migration receipts still reject a later reward edit");
        System.out.println("PASS migration number, string, and reward receipt compatibility fixtures");
        return fixtures.getAsJsonArray("values").size() + 1;
    }

    private static String manifest(String name, boolean locked) {
        return "{\"schema\":\"villagerretaliation:world_campaign/v1\",\"id\":\"test:" + name
                + "\",\"name\":\"" + name + "\",\"description\":\"Test campaign\",\"locked\":" + locked + "}";
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void reject(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected invalid campaign content to be rejected");
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    private static final class MemoryResources implements ResourceManager {
        private final Map<ResourceLocation, Resource> values = new LinkedHashMap<>();
        private final Map<ResourceLocation, List<Resource>> layers = new LinkedHashMap<>();
        private final Map<String, PackResources> packs = new LinkedHashMap<>();
        void add(String id, String json) { add("test", id, json); }
        void add(String packName, String location, String json) {
            PackResources pack = packs.computeIfAbsent(packName, name -> (PackResources) java.lang.reflect.Proxy.newProxyInstance(
                    PackResources.class.getClassLoader(), new Class<?>[] {PackResources.class},
                    (proxy, method, args) -> method.getName().equals("packId") ? name : null));
            ResourceLocation key = id(location);
            Resource resource = new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            values.put(key, resource);
            layers.computeIfAbsent(key, ignored -> new ArrayList<>()).add(resource);
        }
        public Set<String> getNamespaces() { return Set.of("test"); }
        public Optional<Resource> getResource(ResourceLocation id) { return Optional.ofNullable(values.get(id)); }
        public List<Resource> getResourceStack(ResourceLocation id) { return values.containsKey(id) ? layers.get(id) : List.of(); }
        public Map<ResourceLocation, Resource> listResources(String root, Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, Resource> result = new LinkedHashMap<>();
            values.forEach((id, resource) -> { if (id.getPath().startsWith(root + "/") && filter.test(id)) result.put(id, resource); });
            return result;
        }
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String root, Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, List<Resource>> result = new LinkedHashMap<>();
            listResources(root, filter).forEach((id, resource) -> result.put(id, getResourceStack(id)));
            return result;
        }
        public Stream<PackResources> listPacks() { return packs.values().stream(); }
    }
}
