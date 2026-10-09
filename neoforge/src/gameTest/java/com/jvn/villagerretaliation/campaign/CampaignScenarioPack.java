package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignScenario;
import com.jvn.villagerretaliation.campaign.content.CampaignScenarioParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationResources;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleDiscovery;
import com.jvn.villagerretaliation.quest.content.bundle.BuiltInQuestBundleCompatibility;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardRegistryContext;
import com.jvn.villagerretaliation.quest.pool.QuestPoolResources;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.compiler.SceneDiagnostic;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

final class CampaignScenarioPack {
    final Path root;
    final Map<ResourceLocation, JsonObject> resources;
    final List<CampaignScenario> scenarios;

    private CampaignScenarioPack(Path root, Map<ResourceLocation, JsonObject> resources, List<CampaignScenario> scenarios) {
        this.root = root;
        this.resources = Map.copyOf(resources);
        this.scenarios = List.copyOf(scenarios);
    }

    static CampaignScenarioPack read(Path root) throws IOException {
        root = root.toRealPath();
        if (!Files.isRegularFile(root.resolve("pack.mcmeta"))) throw new IOException("Scenario pack requires pack.mcmeta: " + root);
        var resources = new LinkedHashMap<ResourceLocation, JsonObject>();
        var scenarios = new ArrayList<CampaignScenario>();
        var ids = new java.util.HashSet<ResourceLocation>();
        long bytes = 0;
        try (var paths = Files.walk(root.resolve("data"))) {
            for (var file : paths.sorted().toList()) {
                if (!file.toString().endsWith(".json")) continue;
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().startsWith(root))
                    throw new IOException("Scenario resources must be regular files inside the pack: " + file);
                String relative = root.resolve("data").relativize(file).toString().replace('\\', '/');
                int split = relative.indexOf('/');
                if (split < 0) throw new IOException("Resource has no namespace: " + file);
                var location = ResourceLocation.tryParse(relative.substring(0, split) + ":" + relative.substring(split + 1));
                if (location == null) throw new IOException("Invalid scenario resource path: " + file);
                long size = Files.size(file);
                bytes += size;
                if (size > 4 * 1024 * 1024 || bytes > 64 * 1024 * 1024 || resources.size() >= 4096)
                    throw new IOException("Scenario pack exceeds its JSON size or file limit.");
                JsonObject value;
                try {
                    var raw = JsonParser.parseString(Files.readString(file));
                    if (!raw.isJsonObject()) throw new IllegalArgumentException("expected object");
                    value = raw.getAsJsonObject();
                } catch (com.google.gson.JsonParseException | IllegalArgumentException exception) {
                    throw new IOException(file + ": " + exception.getMessage(), exception);
                }
                resources.put(location, value);
                if (location.getPath().startsWith("campaign_scenarios/")) {
                    var parsed = CampaignScenarioParser.parse(location, value);
                    if (!parsed.valid()) throw new IOException(file + ": " + parsed.diagnostics());
                    var scenario = parsed.scenario();
                    String expected = location.getPath().substring("campaign_scenarios/".length(), location.getPath().length() - 5);
                    if (!scenario.id().equals(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), expected)))
                        throw new IOException(file + "#/id: scenario ID must match its resource path");
                    if (!ids.add(scenario.id())) throw new IOException(file + "#/id: duplicate scenario ID");
                    scenarios.add(scenario);
                }
            }
        }
        if (scenarios.isEmpty()) throw new IOException("No authored campaign scenarios found in " + root);
        return new CampaignScenarioPack(root, resources, scenarios);
    }

    CampaignContentCatalog compile(MinecraftServer server, long generation) {
        var raw = new ArrayList<>(QuestBundleDiscovery.discover(server));
        int layer = raw.stream().mapToInt(QuestBundleTransactions.RawResource::layer).max().orElse(0) + 1;
        resources.forEach((id, value) -> {
            if (id.getPath().startsWith("quests/")) raw.add(QuestBundleTransactions.RawResource.valid(layer, "campaign_scenarios", id, value));
        });
        var bundles = QuestBundleTransactions.compile(raw, BuiltInQuestBundleCompatibility.rules(), QuestRewardRegistryContext.create(server));
        require(!bundles.hasErrors(),
                "Scenario quest bundles: " + bundles.diagnostics());
        var rewards = QuestRewardCatalog.fromBundles(bundles.bundles());
        var encounters = EncounterResources.compileBundles(server, bundles, rewards);
        require(encounters.diagnostics().values().stream().allMatch(List::isEmpty), "Scenario encounters: " + encounters.diagnostics());
        var scenes = SceneResources.compileBundles(bundles, encounters);
        require(scenes.diagnostics().values().stream().flatMap(List::stream).noneMatch(value -> value.severity() == SceneDiagnostic.Severity.ERROR),
                "Scenario scenes: " + scenes.diagnostics());
        var quests = VillagerQuestResources.compileBundles(server, bundles);
        var pools = QuestPoolResources.snapshotForCatalog(server, bundles);
        var conversations = ConversationResources.compile(bundles, scenes.scenes().keySet(), quests.quests().keySet());
        require(conversations.diagnostics().isEmpty(), "Scenario conversations: " + conversations.diagnostics());
        var catalog = new QuestContentCatalog(generation, quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(),
                quests.objectiveEventQuestIds(), quests.factQuestIds(), quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(), quests.triggerEventQuestIds(),
                scenes.scenes(), encounters.templates(), pools.pools(), bundles.bundles(), bundles.localization(), rewards, conversations.conversations());
        var campaignResources = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (var resource : DatapackResourceLoader.jsonResources(server, "campaigns")) {
            var value = DatapackResourceLoader.readObject(resource.location(), "scenario campaign", resource.resource());
            require(value.isPresent(), "Campaign resource is unreadable: " + resource.location());
            campaignResources.put(resource.location(), value.orElseThrow());
        }
        campaignResources.putAll(resources);
        var sources = new ArrayList<CampaignContentCatalog.Source>();
        campaignResources.forEach((id, value) -> {
            if (!id.getPath().startsWith("campaigns/") || !id.getPath().endsWith("/campaign.json")) return;
            String prefix = id.getPath().substring(0, id.getPath().length() - "campaign.json".length()) + "locales/";
            var locales = QuestLocaleCatalog.empty();
            for (var entry : campaignResources.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (!entry.getKey().getNamespace().equals(id.getNamespace()) || !entry.getKey().getPath().startsWith(prefix)) continue;
                String locale = entry.getKey().getPath().substring(prefix.length(), entry.getKey().getPath().length() - 5);
                require(locale.matches("[a-z0-9_-]+"), "Invalid campaign locale " + entry.getKey());
                var parsed = QuestLocaleCatalog.read(entry.getValue());
                require(parsed.valid(), "Invalid campaign locale " + entry.getKey() + ": " + parsed.error());
                locales = locales.overlay(locale, parsed.messages());
            }
            sources.add(new CampaignContentCatalog.Source(id, "campaign_scenarios", value, locales, List.of()));
        });
        var campaigns = CampaignContentCatalog.compile(catalog, sources);
        require(campaigns.diagnostics().isEmpty(), "Scenario campaigns: " + campaigns.diagnostics());
        for (var scenario : scenarios) {
            var issues = CampaignScenarioParser.validateContent(scenario, campaigns);
            require(issues.isEmpty(), "Scenario " + scenario.id() + ": " + issues);
        }
        return campaigns;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
