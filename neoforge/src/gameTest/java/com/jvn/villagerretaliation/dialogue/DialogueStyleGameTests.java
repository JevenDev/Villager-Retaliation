package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources;
import com.jvn.villagerretaliation.network.DialogueStylesPayload;
import io.netty.buffer.Unpooled;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_cues")
@PrefixGameTestTemplate(false)
public final class DialogueStyleGameTests {
    private DialogueStyleGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void stylesScopeEffectsAndRestoreSpeedThroughNestedMarkup(GameTestHelper helper) {
        var styles = Map.of("nervous", style("{\"tag\":\"nervous\",\"text_effects\":{\"shake\":true},\"speed\":0.5}"),
                "calm", style("{\"tag\":\"calm\",\"text_effects\":{\"shake\":false},\"speed\":0.8}"));
        var segments = DialogueTextSegment.parse("<speed=2>A<nervous>B<b>C</b><calm>D</calm>E</nervous>F", DialogueTextEffects.NONE, styles);
        helper.assertValueEqual(DialogueTextSegment.plainText(segments), "ABCDEF", "custom tags stay invisible");
        helper.assertTrue(DialogueTextSegment.slice(segments, 1, 2).getFirst().effects().shake(), "preset applies to its text");
        helper.assertTrue(DialogueTextSegment.slice(segments, 2, 3).getFirst().effects().bold(), "ordinary formatting nests inside presets");
        helper.assertFalse(DialogueTextSegment.slice(segments, 3, 4).getFirst().effects().shake(), "explicit false clears inherited shake");
        helper.assertTrue(DialogueTextSegment.slice(segments, 4, 5).getFirst().effects().shake(), "closing nested preset restores the outer effect");
        helper.assertFalse(DialogueTextSegment.slice(segments, 5, 6).getFirst().effects().shake(), "closing preset restores surrounding effects");
        var clock = new DialoguePlayback(segments, 0, 100);
        helper.assertValueEqual(clock.timelineCues().stream().map(cue -> cue.cue().value()).toList(),
                java.util.List.of(2.0, 0.5, 0.8, 0.5, 2.0), "nested speed settings restore exact surrounding values");
        helper.assertValueEqual(clock.visibleCharacters(774), 5, "outer speed resumes after nested style");
        helper.assertValueEqual(clock.visibleCharacters(775), 6, "final character follows restored scope timing");
        helper.assertTrue(DialogueTextSegment.markupWarnings("<nervous><b>Hi</b></nervous>", styles).isEmpty(), "custom tags validate");
        helper.assertTrue(!DialogueTextSegment.markupWarnings("<nervous><b>Hi</nervous></b>", styles).isEmpty(), "crossed tags warn");
        helper.assertValueEqual(DialogueTextSegment.plainText(DialogueTextSegment.parse("<missing>Hi</missing>", DialogueTextEffects.NONE, styles)),
                "<missing>Hi</missing>", "undefined tags remain visible");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void styleCatalogsAndParsedPerformancesSurviveTheWire(GameTestHelper helper) {
        var payload = new DialogueStylesPayload(Map.of("nervous", style("{\"tag\":\"nervous\",\"text_effects\":{\"shake\":true,\"color\":\"gold\"},\"speed\":0.8}")));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DialogueStylesPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = DialogueStylesPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded, payload, "style catalog retains overrides and speed");
            helper.assertFalse(buffer.isReadable(), "style catalog is fully consumed");
            var segments = DialogueTextSegment.parse("<nervous>😀{player}<agree></nervous>!", DialogueTextEffects.NONE, decoded.styles());
            var mapped = segments.stream().map(segment -> segment.mapText(text -> text.replace("{player}", "Alex"))).toList();
            DialogueTextSegment.writeList(buffer, mapped);
            helper.assertValueEqual(DialogueTextSegment.readList(buffer), mapped, "expanded styles and cue offsets survive the wire");
            helper.assertValueEqual(DialogueTextSegment.plainText(mapped), "😀Alex!", "Unicode and placeholders retain their wording");
            buffer.clear();
            buffer.writeVarInt(DialogueStyleResources.MAX_STYLES + 1);
            boolean rejected = false;
            try { DialogueStylesPayload.STREAM_CODEC.decode(buffer); }
            catch (IllegalArgumentException invalid) { rejected = true; }
            helper.assertTrue(rejected, "unbounded style catalogs are rejected");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void invalidStylesAndDuplicateTagsAreRejected(GameTestHelper helper) {
        for (String json : java.util.List.of("{}", "{\"tag\":\"shake\"}", "{\"tag\":\"look\"}", "{\"tag\":\"abcdef\"}",
                "{\"tag\":\"Nervous\"}", "{\"tag\":\"nervous\",\"speed\":0}", "{\"tag\":\"nervous\",\"speed\":\"1\"}",
                "{\"tag\":\"nervous\",\"text_effects\":{\"shake\":1}}", "{\"tag\":\"nervous\",\"extra\":true}",
                "{\"tag\":\"nervous\",\"text_effects\":{\"color\":\"invented\"}}",
                "{\"tag\":\"nervous\",\"text_effects\":{\"gradient_start\":\"red\"}}")) {
            boolean rejected = false;
            try { style(json); } catch (IllegalArgumentException invalid) { rejected = true; }
            helper.assertTrue(rejected, "invalid style rejected: " + json);
        }
        boolean duplicate = false;
        try {
            DialogueStyleResources.compile(Map.of(ResourceLocation.parse("test:dialogue_styles/a.json"), json("{\"tag\":\"nervous\"}"),
                    ResourceLocation.parse("test:dialogue_styles/b.json"), json("{\"tag\":\"nervous\"}")));
        } catch (IllegalArgumentException invalid) { duplicate = true; }
        helper.assertTrue(duplicate, "duplicate global tag names are diagnosed");
        helper.assertTrue(DialogueStyleResources.current().containsKey("nervous"), "built-in nervous style loads from datapack resources");
        var server = helper.getLevel().getServer();
        var authoring = com.jvn.villagerretaliation.campaign.selection.WorldCampaignResources.authoringResources(server.getResourceManager(),
                com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection.get(server));
        helper.assertTrue(authoring.containsKey(ResourceLocation.parse("villagerretaliation:dialogue_styles/nervous.json")),
                "global style definitions are included in world authoring resources");
        var baseline = DialogueStyleResources.current();
        DialogueStyleResources.replaceClient(Map.of());
        helper.assertValueEqual(DialogueStyleResources.current(), baseline, "client disconnect cannot clear the integrated server catalog");
        try (var ignored = DialogueStyleResources.preview(Map.of("calm", style("{\"tag\":\"calm\"}")))) {
            helper.assertTrue(DialogueStyleResources.current().containsKey("calm"), "preview scope supplies draft styles");
        }
        helper.assertValueEqual(DialogueStyleResources.current(), baseline, "preview scope cannot leak into runtime");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void densePresetsRemainWithinCueBounds(GameTestHelper helper) {
        var styles = Map.of("nervous", style("{\"tag\":\"nervous\",\"speed\":0.5}"));
        var segments = DialogueTextSegment.parse("<nervous>" + "<agree>".repeat(64) + "A</nervous>B", DialogueTextEffects.NONE, styles);
        helper.assertTrue(!DialogueTextSegment.markupWarnings("<nervous>" + "<agree>".repeat(64) + "A</nervous>", styles).isEmpty(),
                "validation includes generated speed events in its cue budget");
        int cues = segments.stream().mapToInt(segment -> segment.cues().size()).sum();
        helper.assertTrue(cues <= DialogueCue.MAX_CUES, "generated speed events share the cue budget");
        var visualOnly = Map.of("uneasy", style("{\"tag\":\"uneasy\",\"text_effects\":{\"shake\":true}}"));
        var saturated = DialogueTextSegment.parse("<agree>".repeat(64) + "<uneasy>A</uneasy>", DialogueTextEffects.NONE, visualOnly);
        helper.assertValueEqual(DialogueTextSegment.plainText(saturated), "A", "visual presets do not consume cue slots");
        helper.assertTrue(saturated.getLast().effects().shake(), "visual styles remain available at the cue limit");
        var timeline = new DialoguePlayback(segments, 0, 100).timelineCues();
        helper.assertValueEqual(timeline.getLast().cue().value(), 1.0, "speed restoration retains a reserved cue slot");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void animationSettingsInheritRestoreAndSurvivePackets(GameTestHelper helper) {
        var subtle = style("{\"tag\":\"subtle\",\"text_effects\":{\"shake\":{\"strength\":0.25,\"speed\":2},\"wavy\":{\"speed\":0}}}");
        var strong = style("{\"tag\":\"strong\",\"text_effects\":{\"shake\":{\"strength\":3}}}");
        var segments = DialogueTextSegment.parse("<subtle>A<b>B</b><strong>C</strong>D</subtle>E", null, Map.of("subtle", subtle, "strong", strong));
        var outer = DialogueTextSegment.slice(segments, 0, 1).getFirst().effects();
        helper.assertValueEqual(outer.shakeAnimation().strength(), 0.25F, "style supplies shake strength");
        helper.assertValueEqual(outer.shakeAnimation().speed(), 2.0F, "style supplies animation speed");
        helper.assertValueEqual(outer.waveAnimation().speed(), 0.0F, "zero animation speed is supported");
        helper.assertValueEqual(DialogueTextSegment.slice(segments, 1, 2).getFirst().effects().shakeAnimation(), outer.shakeAnimation(), "ordinary formatting retains motion settings");
        helper.assertValueEqual(DialogueTextSegment.slice(segments, 2, 3).getFirst().effects().shakeAnimation().strength(), 3.0F, "nested styles override motion settings");
        helper.assertValueEqual(DialogueTextSegment.slice(segments, 3, 4).getFirst().effects().shakeAnimation(), outer.shakeAnimation(), "closing style restores motion settings");
        helper.assertFalse(DialogueTextSegment.slice(segments, 4, 5).getFirst().effects().shake(), "closing outer style restores surrounding effects");
        helper.assertValueEqual(style("{\"tag\":\"legacy\",\"text_effects\":{\"shake\":true}}").effects().shakeAnimation(),
                com.jvn.villagerretaliation.dialogue.normal.DialogueTextAnimation.DEFAULT, "boolean effects preserve legacy animation defaults");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var catalog = new DialogueStylesPayload(Map.of("subtle", subtle));
            DialogueStylesPayload.STREAM_CODEC.encode(buffer, catalog);
            helper.assertValueEqual(DialogueStylesPayload.STREAM_CODEC.decode(buffer), catalog, "style animation settings survive catalog synchronization");
            DialogueTextSegment.writeList(buffer, segments);
            helper.assertValueEqual(DialogueTextSegment.readList(buffer), segments, "per-span animation settings survive dialogue packets");
        } finally { buffer.release(); }
        for (String motion : java.util.List.of("{\"strength\":-1}", "{\"strength\":4.1}", "{\"speed\":10.1}", "{\"speed\":\"1\"}", "{\"extra\":1}")) {
            boolean rejected = false;
            try { style("{\"tag\":\"invalid\",\"text_effects\":{\"shake\":" + motion + "}}"); }
            catch (IllegalArgumentException invalid) { rejected = true; }
            helper.assertTrue(rejected, "invalid motion settings rejected: " + motion);
        }
        helper.succeed();
    }

    private static DialogueStyle style(String json) { return DialogueStyle.read(json(json)); }
    private static JsonObject json(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
}
