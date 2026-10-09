package com.jvn.villagerretaliation.quest.content;

import static com.jvn.villagerretaliation.skill.player.SocialProgressionTestData.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationResources;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import com.jvn.villagerretaliation.skill.player.SocialProgressionCompiler;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleDiscovery;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class SocialProgressionContentGameTests {
    private static final String BUNDLE = "quests/campaign_fixture/characters/";
    private static final QuestBundlePath.Owner QUEST_OWNER = QuestBundlePath.Owner.quest("campaign_fixture", "campaign_fixture", "characters");

    private SocialProgressionContentGameTests() { }

    @GameTest(template = "empty", batch = "social_content")
    public static void sharedAuthoringCasesMatchTheRuntimeContract(GameTestHelper helper) throws IOException {
        for (var entry : read("social_progression_cases.json").getAsJsonArray()) {
            var test = entry.getAsJsonObject();
            boolean profile = test.get("target").getAsString().equals("profile");
            var root = profile ? profile() : resource(BUNDLE + "conversations/mentor.json");
            for (var change : test.getAsJsonArray("patches")) patch(root, change.getAsJsonObject());
            List<com.jvn.villagerretaliation.quest.debug.QuestDiagnostic> issues;
            if (profile) issues = SocialProgressionCompiler.compile(bundle(root), ID).diagnostics();
            else {
                var result = ConversationResources.compile(conversationBundles(root), Set.of(), Set.of(id("characters")));
                issues = result.diagnostics().getOrDefault(QUEST_OWNER, List.of());
            }
            String name = test.get("name").getAsString();
            if (test.get("valid").getAsBoolean()) helper.assertTrue(issues.isEmpty(), name + ": " + issues);
            else helper.assertTrue(issues.stream().anyMatch(issue -> issue.jsonPointer().equals(test.get("pointer").getAsString())),
                    name + ": " + issues);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "social_content")
    public static void profilesRetainSourceOrderingAndThresholds(GameTestHelper helper) throws IOException {
        var compiled = definition();
        helper.assertValueEqual(new ArrayList<>(compiled.skills().keySet()),
                List.of(PlayerSocialSkill.PERSUASION, PlayerSocialSkill.INTIMIDATION, PlayerSocialSkill.DECEPTION, PlayerSocialSkill.INSIGHT),
                "authored skill ordering is retained");
        helper.assertValueEqual(compiled.source().resource(), id(PATH), "profile source is retained");
        helper.assertValueEqual(compiled.source().pack(), "social_fixture", "profile pack is retained");
        long[] experiences = {0, 9, 10, 29, 30, Long.MAX_VALUE};
        int[] bonuses = {0, 0, 2, 2, 4, 4};
        for (int index = 0; index < experiences.length; index++)
            helper.assertValueEqual(compiled.rank(PlayerSocialSkill.PERSUASION, experiences[index]).bonus(), bonuses[index],
                    "authored threshold at " + experiences[index]);
        var changed = profile();
        changed.getAsJsonArray("skills").get(0).getAsJsonObject().getAsJsonArray("levels").get(1).getAsJsonObject().addProperty("bonus", 3);
        helper.assertFalse(SocialProgressionCompiler.compile(bundle(changed), ID).definition().fingerprint().equals(compiled.fingerprint()),
                "balance changes alter the structural fingerprint");
        var privateOwner = new QuestBundleTransactions.EffectiveBundle(QUEST_OWNER, id("characters"), "",
                bundle(profile()).definitions(), Map.of(), QuestLocaleCatalog.empty(), Set.of());
        helper.assertFalse(SocialProgressionCompiler.compile(privateOwner, ID).valid(), "profiles use the shared content boundary");
        helper.assertTrue(QuestBundlePath.classify(id(PATH)).valid(), "shared path is recognized");
        helper.assertFalse(QuestBundlePath.classify(id(BUNDLE + "social_progression/training.json")).valid(), "private profile path is rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "social_content")
    public static void profileOverridesRollBackWithoutPublishingBrokenReferences(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        var held = QuestContentCatalogs.current(server);
        var resources = new ArrayList<>(QuestBundleDiscovery.discover(server));
        resources.add(raw(0, "social_fixture", PATH, profile()));
        for (String file : List.of("quest.json", "locales/en_us.json", "conversations/social_mentor.json"))
            resources.add(raw(0, "social_fixture", BUNDLE + file, resource(BUNDLE + file)));
        var broken = profile();
        broken.getAsJsonArray("skills").get(0).getAsJsonObject().getAsJsonArray("levels").get(1).getAsJsonObject().addProperty("experience", 0);
        resources.add(raw(1000, "broken_profile", PATH, broken));
        var result = QuestContentCatalogs.build(server, resources);
        helper.assertTrue(result.publishable(), "invalid optional override keeps the lower profile: " + result.report().entries());
        helper.assertValueEqual(result.catalog().socialProgressions().get(ID).source().pack(), "social_fixture", "rollback retains the lower profile");
        helper.assertTrue(result.catalog().conversation(id("social_mentor")).isPresent(), "conversation resolves the surviving profile");
        helper.assertTrue(result.report().entries().stream().anyMatch(issue -> issue.message().contains("/skills/0/levels/1/experience")),
                "failed threshold reports its authoring pointer");
        helper.assertTrue(QuestContentCatalogs.current(server) == held, "compilation preserves the live catalog");
        var report = CampaignReferenceReport.build(new CampaignContentCatalog(result.catalog(), Map.of(), List.of()));
        var conversation = CampaignReferenceReport.Key.of(CampaignReferenceReport.Kind.CONVERSATION, id("social_mentor"));
        var dependencies = report.trace(conversation, CampaignReferenceReport.Direction.DEPENDENCIES, 1);
        helper.assertTrue(dependencies.stream().anyMatch(match -> match.reference().to().equals(
                CampaignReferenceReport.Key.of(CampaignReferenceReport.Kind.SOCIAL_SKILL, ID, "persuasion"))
                && match.reference().source().pointer().equals("/checks/0/social/skill")), "reference report traces the authored skill");
        helper.succeed();
    }

    private static QuestBundleTransactions.Result conversationBundles(JsonObject graph) throws IOException {
        var english = QuestLocaleCatalog.read(resource(BUNDLE + "locales/en_us.json"));
        var conversation = new QuestBundleTransactions.EffectiveBundle(QUEST_OWNER, id("characters"), "campaign_fixture.quest.characters",
                Map.of(QuestBundlePath.Kind.CONVERSATION, Map.of(id("mentor_conversation"), graph)), Map.of(),
                new QuestLocaleCatalog(Map.of("en_us", english.messages())), Set.of());
        return new QuestBundleTransactions.Result(Map.of(OWNER, bundle(profile()), QUEST_OWNER, conversation), List.of());
    }

    private static QuestBundleTransactions.RawResource raw(int layer, String pack, String path, JsonObject value) {
        return QuestBundleTransactions.RawResource.valid(layer, pack, id(path), value);
    }

    private static void patch(JsonObject root, JsonObject patch) {
        String[] tokens = patch.get("path").getAsString().substring(1).split("/");
        JsonElement target = root;
        for (int index = 0; index < tokens.length - 1; index++)
            target = target.isJsonArray() ? target.getAsJsonArray().get(Integer.parseInt(tokens[index])) : target.getAsJsonObject().get(tokens[index]);
        String key = tokens[tokens.length - 1];
        if (patch.has("remove")) target.getAsJsonObject().remove(key);
        else if (target.isJsonArray()) target.getAsJsonArray().set(Integer.parseInt(key), patch.get("value").deepCopy());
        else target.getAsJsonObject().add(key, patch.get("value").deepCopy());
    }
}
