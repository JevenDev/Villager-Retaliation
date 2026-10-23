package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_cues")
@PrefixGameTestTemplate(false)
public final class DialoguePlaybackGameTests {
    private DialoguePlaybackGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void pausesStopRevealAndSpeechIncludingAtLineBoundaries(GameTestHelper helper) {
        var clock = clock("<pause=500>A<pause=200>B<pause=300>", 50);
        helper.assertValueEqual(clock.visibleCharacters(499), 0, "leading pause precedes first character");
        helper.assertTrue(clock.paused(499), "leading pause is active");
        helper.assertFalse(clock.talking(499), "mouth is closed during pause");
        helper.assertValueEqual(clock.visibleCharacters(500), 1, "first character appears after pause");
        helper.assertTrue(clock.talking(500), "speech resumes between pauses");
        helper.assertValueEqual(clock.visibleCharacters(749), 1, "middle pause holds the next character");
        helper.assertValueEqual(clock.visibleCharacters(750), 2, "second character follows the middle pause");
        helper.assertFalse(clock.complete(1099), "trailing pause holds completion after all text is visible");
        helper.assertTrue(clock.complete(1100), "trailing pause eventually completes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void speedChangesApplyAtTheirOwnTextPositions(GameTestHelper helper) {
        var clock = clock("A<speed=0.5>BC<speed=2>D", 100);
        helper.assertValueEqual(clock.visibleCharacters(99), 1, "initial speed");
        helper.assertValueEqual(clock.visibleCharacters(100), 2, "speed changes do not shift already revealed text");
        helper.assertValueEqual(clock.visibleCharacters(299), 2, "half speed doubles the interval");
        helper.assertValueEqual(clock.visibleCharacters(300), 3, "slower character appears at the expected time");
        helper.assertValueEqual(clock.visibleCharacters(500), 4, "later speed change begins at its own position");
        helper.assertTrue(clock.complete(500), "no additional delay after the last character");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void gesturesFollowUnicodePositionsAndDoNotRestartOnSampling(GameTestHelper helper) {
        var clock = clock("A😀<agree>B", 100);
        helper.assertValueEqual(clock.visibleCharacters(100), 2, "Unicode characters count once");
        helper.assertTrue(Math.abs(clock.pose(300, 0, 0, 0, 0).pitch()) > 1, "nod begins at the Unicode text position");
        helper.assertValueEqual(clock.pose(300, 0, 0, 0, 0), clock.pose(300, 0, 0, 0, 0),
                "repeated frames cannot retrigger gestures");
        helper.assertFalse(clock.complete(849), "completion waits for the gesture");
        helper.assertTrue(clock.complete(850), "gesture finishes automatically");
        helper.assertValueEqual(clock.pose(900, 0, 0, 0, 0), new DialoguePlayback.Pose(0, 0), "nod returns to neutral");
        var disagreement = clock("<disagree>AB", 100);
        helper.assertTrue(Math.abs(disagreement.pose(100, 0, 0, 0, 0).yaw()) > 1, "disagreement moves horizontally");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void gazeHoldsBlendsAndResetsToNormalTracking(GameTestHelper helper) {
        var clock = clock("<look=left>AB<look=player>C<reset>D", 500);
        helper.assertValueEqual(clock.pose(180, 7, 2, -20, -5), new DialoguePlayback.Pose(35, 0), "left gaze settles");
        helper.assertValueEqual(clock.pose(1180, 7, 2, -20, -5), new DialoguePlayback.Pose(-20, -5), "gaze can target the player");
        helper.assertValueEqual(clock.pose(1680, 7, 2, -20, -5), new DialoguePlayback.Pose(7, 2), "reset restores normal tracking");
        helper.assertTrue(clock("<agree><reset>A", 100).complete(0), "reset cancels the remaining gesture delay");
        var copy = clock.copy();
        helper.assertValueEqual(copy.pose(1200, 0, 0, 15, 4), clock.pose(1200, 0, 0, 15, 4),
                "screen replacement preserves the performance timeline");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void skipAndInstantTextHandleQueuedCuesWithoutWaiting(GameTestHelper helper) {
        var clock = clock("<look=left>A<pause=5000><disagree>B<look=right>", 100);
        clock.skip();
        helper.assertTrue(clock.complete(0), "skip bypasses pauses");
        helper.assertValueEqual(clock.visibleCharacters(0), 2, "skip reveals all text");
        helper.assertValueEqual(clock.pose(0, 0, 0, 0, 0), new DialoguePlayback.Pose(-35, 0),
                "skip settles on final gaze without queued head shakes");
        var instant = clock("<pause=60000><agree>AB", 0);
        helper.assertTrue(instant.complete(0), "instant text never waits for authored pauses");
        helper.assertValueEqual(instant.visibleCharacters(0), 2, "instant preference reveals all text");
        helper.assertTrue(Math.abs(instant.pose(100, 0, 0, 0, 0).pitch()) > 1,
                "instant text still permits gesture animation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void particlesFollowTimelineAndFireOnlyOnce(GameTestHelper helper) {
        var clock = clock("😀<speed=2>A<pause=500><particle:smoke amount:2>B<particle:heart>", 100);
        helper.assertTrue(clock.pollParticles(649).isEmpty(), "particles wait for earlier text, speed changes and pauses");
        var due = clock.pollParticles(650);
        helper.assertValueEqual(due.size(), 1, "particle fires at its authored text position");
        helper.assertValueEqual(due.getFirst().value(), 2.0, "timeline retains particle amount");
        helper.assertTrue(clock.pollParticles(650).isEmpty(), "repeated frames do not spawn duplicate particles");
        var copy = clock.copy();
        helper.assertTrue(copy.pollParticles(650).isEmpty(), "screen replacement preserves consumed particles");
        helper.assertValueEqual(copy.pollParticles(700).size(), 1, "trailing particles fire after the last character");
        helper.assertTrue(copy.pollParticles(900).isEmpty(), "completed playback cannot repeat particles");
        var retimed = clock.retime(DialogueTextSegment.parse(
                "😀<speed=2>A<pause=500><particle:smoke amount:2>B<particle:heart>", DialogueTextEffects.NONE), 50);
        helper.assertValueEqual(retimed.pollParticles(700).size(), 1, "speed changes preserve consumed particles");
        var skipped = clock("<particle:smoke>A<pause=500><particle:heart>B", 100);
        helper.assertValueEqual(skipped.pollParticles(0).size(), 1, "leading particle fires immediately");
        skipped.skip();
        helper.assertValueEqual(skipped.pollParticles(0).size(), 1, "skip flushes remaining particles once");
        helper.assertTrue(skipped.pollParticles(0).isEmpty(), "skip does not replay particles");
        var instant = clock("A<pause=500><particle:smoke>B<particle:heart>", 0);
        helper.assertValueEqual(instant.pollParticles(0).size(), 2, "instant text fires particles without authored waits");
        helper.assertTrue(instant.pollParticles(1).isEmpty(), "instant particles still fire once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void interruptedGesturesBlendWithoutSingleFrameSnaps(GameTestHelper helper) {
        for (String cue : java.util.List.of("<reset>", "<agree>", "<disagree>")) {
            var clock = clock("<disagree>A" + cue + "<pause=1000>B", 120);
            var before = clock.pose(119, 0, 0, 0, 0);
            var after = clock.pose(120, 0, 0, 0, 0);
            helper.assertTrue(Math.abs(before.yaw() - after.yaw()) < 0.3
                            && Math.abs(before.pitch() - after.pitch()) < 0.3,
                    "gesture handoff preserves head position: " + cue);
            var earlier = clock.pose(118, 0, 0, 0, 0);
            var later = clock.pose(121, 0, 0, 0, 0);
            helper.assertTrue(Math.abs((after.yaw() - later.yaw()) - (earlier.yaw() - before.yaw())) < 0.02,
                    "gesture handoff preserves smooth movement: " + cue);
        }
        var reset = clock("<disagree>A<reset>", 120);
        helper.assertFalse(reset.complete(299), "completion waits for the gesture to ease out");
        helper.assertTrue(reset.complete(300), "gesture reset settles within the transition");
        helper.assertValueEqual(reset.pose(300, 0, 0, 0, 0), new DialoguePlayback.Pose(0, 0),
                "reset finishes at neutral");
        helper.assertValueEqual(clock("<agree><disagree><reset>A", 100).pose(100, 0, 0, 0, 0),
                new DialoguePlayback.Pose(0, 0), "simultaneous cues do not leave ghost gestures");
        var look = clock("<look=left>", 100);
        helper.assertFalse(look.complete(179), "cue-only gaze waits for its turn to finish");
        helper.assertTrue(look.complete(180), "cue-only gaze eventually finishes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void soundsShareParticleTimingWithoutReplaying(GameTestHelper helper) {
        String text = "😀<speed=2>A<pause=500><sound:entity.villager.yes><particle:heart>B<sound:entity.villager.no>";
        var clock = clock(text, 100);
        helper.assertTrue(clock.pollSounds(649).isEmpty(), "sounds wait for text and pauses");
        helper.assertValueEqual(clock.pollSounds(650).size(), 1, "sound starts at the authored word");
        helper.assertValueEqual(clock.pollParticles(650).size(), 1, "particles use the same timeline position");
        helper.assertTrue(clock.pollSounds(650).isEmpty(), "sound does not repeat on another frame");
        helper.assertTrue(clock.copy().pollSounds(650).isEmpty(), "screen replacement preserves consumed sounds");
        helper.assertValueEqual(clock.retime(DialogueTextSegment.parse(text, DialogueTextEffects.NONE), 50).pollSounds(700).size(),
                1, "retiming preserves consumed sounds");
        clock.skip();
        helper.assertTrue(clock.pollSounds(900).isEmpty(), "skipping avoids playing a backlog of sounds together");
        var instant = clock("<sound:entity.villager.yes>A<pause=500><sound:entity.villager.no>", 0);
        helper.assertValueEqual(instant.pollSounds(0).size(), 2, "instant text schedules authored sounds immediately");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_cues")
    public static void previewSeekingFreezesTextGesturesAndSoundTiming(GameTestHelper helper) {
        String text = "<look=left>A<sound:entity.villager.yes><particle:heart><pause=500>B<disagree>";
        var clock = clock(text, 100);
        helper.assertValueEqual(clock.timelineCues().size(), 5, "preview exposes every authored cue");
        clock.seek(125, 2000);
        var pose = clock.pose(2000, 0, 0, 0, 0);
        helper.assertValueEqual(clock.positionMillis(9000), 125L, "seeking freezes the preview clock");
        helper.assertValueEqual(clock.visibleCharacters(9000), 1, "text stays at the selected position");
        helper.assertValueEqual(clock.pose(9000, 0, 0, 0, 0), pose, "head animation stays at the selected position");
        helper.assertFalse(clock.talking(9000), "paused preview does not animate the mouth");
        helper.assertTrue(clock.pollSounds(9000).isEmpty(), "scrubbing never plays sounds");
        helper.assertValueEqual(clock.copy().positionMillis(9000), 125L, "copy preserves paused position");
        var retimed = clock.retime(DialogueTextSegment.parse(text, DialogueTextEffects.NONE), 50);
        helper.assertValueEqual(retimed.positionMillis(9000), 125L, "retiming preserves paused position");
        clock.seek(50, 2000);
        clock.play(2000);
        helper.assertTrue(clock.pollSounds(2049).isEmpty(), "resumed sounds wait for their remaining delay");
        helper.assertValueEqual(clock.pollSounds(2050).size(), 1, "resumed sound stays aligned to the cue");
        helper.assertValueEqual(clock.pollParticles(2050).size(), 1, "resumed particles stay aligned to the sound");
        clock.pause(2075);
        helper.assertValueEqual(clock.positionMillis(3000), 125L, "pause holds the current playback position");
        clock.seek(Long.MAX_VALUE, 3000);
        helper.assertValueEqual(clock.positionMillis(4000), clock.previewDurationMillis(), "seek clamps to the preview end");
        clock.restart(5000);
        helper.assertValueEqual(clock.positionMillis(5000), 0L, "replay starts from zero");
        helper.assertFalse(clock.playbackPaused(), "replay resumes playback");
        helper.assertTrue(clock.pollSounds(5099).isEmpty(), "replay respects cue timing");
        helper.assertValueEqual(clock.pollSounds(5100).size(), 1, "replay permits hearing the cue again");
        helper.assertTrue(clock.pollSounds(5100).isEmpty(), "replayed cue fires only once");
        helper.succeed();
    }

    private static DialoguePlayback clock(String text, long millisPerCharacter) {
        return new DialoguePlayback(DialogueTextSegment.parse(text, DialogueTextEffects.NONE), 0, millisPerCharacter);
    }
}
