package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_cues")
@PrefixGameTestTemplate(false)
public final class DialogueCueGameTests {
    private DialogueCueGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void cuesSurviveStylesSlicesAndPlaceholders(GameTestHelper helper) {
        var segments = parse("<look=left><b>Hello {player}<pause=500></b>!<reset>");
        helper.assertValueEqual(DialogueTextSegment.plainText(segments), "Hello {player}!", "cue markup stays invisible");
        helper.assertTrue(segments.stream().filter(segment -> segment.text().startsWith("Hello")).findFirst().orElseThrow().effects().bold(),
                "styled text retains its effect");
        var mapped = segments.stream().map(segment -> segment.mapText(text -> text.replace("{player}", "Alex"))).toList();
        helper.assertValueEqual(cues(mapped).stream().map(DialogueCue.At::offset).toList(), List.of(0, 10, 11),
                "placeholder expansion moves later cues");
        helper.assertValueEqual(cues(DialogueTextSegment.slice(mapped, 6, 11)).stream().map(DialogueCue.At::offset).toList(),
                List.of(4, 5), "slices retain cues and terminal reset at their new positions");
        helper.assertValueEqual(DialogueTextSegment.plainText(parse("<pause=-1>No</reset>")), "<pause=-1>No</reset>",
                "invalid cues remain visible for diagnosis");
        helper.assertValueEqual(cues(parse("<agree>")).size(), 1, "cue-only lines survive parsing");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void boundedPacketsRetainCueOffsetsAndUnicode(GameTestHelper helper) {
        var segments = new ArrayList<DialogueTextSegment>();
        for (int index = 0; index < 80; index++)
            segments.addAll(parse((index == 65 ? "<agree>" : "") + (index % 2 == 0 ? "<b>x</b>" : "<i>x</i>")));
        var bounded = DialogueTextSegment.forNetwork(segments);
        helper.assertValueEqual(bounded.size(), 64, "packet style runs remain bounded");
        helper.assertValueEqual(cues(bounded).getFirst().offset(), 65, "folding excess styles preserves cue positions");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DialogueTextSegment.writeList(buffer, bounded);
            helper.assertValueEqual(DialogueTextSegment.readList(buffer), bounded, "cue packet round trip");
            helper.assertFalse(buffer.isReadable(), "packet is fully consumed");
        } finally { buffer.release(); }
        var unicode = DialogueTextSegment.forNetwork(parse("x".repeat(511) + "😀<agree>tail"));
        helper.assertValueEqual(DialogueTextSegment.plainText(unicode).length(), 511, "network truncation preserves surrogate pairs");
        helper.assertTrue(cues(unicode).isEmpty(), "cues beyond truncated text are discarded");
        helper.assertValueEqual(cues(DialogueTextSegment.forNetwork(parse("x".repeat(512) + "<reset>"))).size(), 1,
                "a reset exactly at the packet boundary survives");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void invalidCueOffsetsAndCountsAreRejected(GameTestHelper helper) {
        boolean rejected = false;
        try {
            new DialogueTextSegment("😀", DialogueTextEffects.NONE,
                    List.of(new DialogueCue.At(1, new DialogueCue(DialogueCue.Kind.AGREE, 0))));
        } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "cue cannot split a surrogate pair");
        helper.assertTrue(!DialogueTextSegment.markupWarnings("<agree>".repeat(65)).isEmpty(), "authoring warns about excess cues");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            buffer.writeVarInt(1);
            buffer.writeUtf("x");
            DialogueTextEffects.write(buffer, DialogueTextEffects.NONE);
            buffer.writeVarInt(65);
            rejected = false;
            try { DialogueTextSegment.readList(buffer); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "decoder rejects unbounded cue lists");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void particleCuesValidateAndSurvivePackets(GameTestHelper helper) {
        var segments = parse("<b>{player}<particle:smoke amount:2></b>😀<particle:minecraft:heart>");
        var mapped = segments.stream().map(segment -> segment.mapText(text -> text.replace("{player}", "Alex"))).toList();
        var events = cues(mapped);
        helper.assertValueEqual(DialogueTextSegment.plainText(mapped), "Alex😀", "particle tags stay invisible");
        helper.assertValueEqual(events.stream().map(DialogueCue.At::offset).toList(), List.of(4, 6),
                "particle offsets follow placeholders and Unicode");
        helper.assertValueEqual(events.getFirst().cue().particle().toString(), "minecraft:smoke", "default particle namespace");
        helper.assertValueEqual(events.getFirst().cue().value(), 2.0, "authored particle count");
        helper.assertValueEqual(events.getLast().cue().value(), 1.0, "omitted particle count defaults to one");
        helper.assertValueEqual(cues(DialogueTextSegment.slice(mapped, 4, 6)).size(), 2,
                "slices retain boundary particles");
        for (String invalid : List.of("<particle:missing_particle>", "<particle:dust>", "<particle:smoke amount:0>",
                "<particle:smoke amount:65>", "<particle:smoke amount:1.5>"))
            helper.assertTrue(!DialogueTextSegment.markupWarnings(invalid).isEmpty(), "invalid particle warns: " + invalid);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DialogueTextSegment.writeList(buffer, mapped);
            helper.assertValueEqual(DialogueTextSegment.readList(buffer), mapped, "particle resources and counts round trip");
            helper.assertFalse(buffer.isReadable(), "particle packet is fully consumed");
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static List<DialogueTextSegment> parse(String text) {
        return DialogueTextSegment.parse(text, DialogueTextEffects.NONE);
    }

    private static List<DialogueCue.At> cues(List<DialogueTextSegment> segments) {
        var cues = new ArrayList<DialogueCue.At>();
        int offset = 0;
        for (var segment : segments) {
            for (var cue : segment.cues()) cues.add(new DialogueCue.At(offset + cue.offset(), cue.cue()));
            offset += segment.text().length();
        }
        return cues;
    }
}
