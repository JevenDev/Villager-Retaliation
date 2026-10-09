package com.jvn.villagerretaliation.skill.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class SocialProgressionTestData {
    public static final ResourceLocation ID = id("social_training");
    public static final String PATH = "quests/_shared/social_progression/training.json";
    public static final QuestBundlePath.Owner OWNER = QuestBundlePath.Owner.shared("campaign_fixture");

    private SocialProgressionTestData() { }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }

    public static JsonElement read(String path) throws IOException {
        try (var stream = SocialProgressionTestData.class.getResourceAsStream("/campaign_fixture/" + path)) {
            if (stream == null) throw new IOException("Missing social progression fixture " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    public static JsonObject resource(String path) throws IOException { return read("data/campaign_fixture/" + path).getAsJsonObject(); }
    public static JsonObject profile() throws IOException { return resource(PATH); }

    public static QuestBundleTransactions.EffectiveBundle bundle(JsonObject root) {
        return new QuestBundleTransactions.EffectiveBundle(OWNER, null, "",
                Map.of(QuestBundlePath.Kind.SOCIAL_PROGRESSION, Map.of(ID, root)), Map.of(), QuestLocaleCatalog.empty(), Set.of(),
                Map.of(new QuestBundleTransactions.DefinitionKey(QuestBundlePath.Kind.SOCIAL_PROGRESSION, ID),
                        new QuestBundleTransactions.DefinitionSource(id(PATH), "social_fixture")));
    }

    public static SocialProgressionDefinition definition() throws IOException {
        var compiled = SocialProgressionCompiler.compile(bundle(profile()), ID);
        if (!compiled.valid()) throw new IllegalStateException(compiled.diagnostics().toString());
        return compiled.definition();
    }
}
