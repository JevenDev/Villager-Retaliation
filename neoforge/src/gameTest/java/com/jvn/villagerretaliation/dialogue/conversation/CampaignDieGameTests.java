package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.client.interaction.CampaignDieAnimation;
import com.jvn.villagerretaliation.client.interaction.CampaignDieGeometry;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignDieGameTests {
    private CampaignDieGameTests() { }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void numberedIcosahedronSettlesOnEveryAcknowledgedResult(GameTestHelper helper) {
        for (int die = 1; die <= 20; die++) {
            var faces = CampaignDieGeometry.frame(die, 1);
            helper.assertValueEqual(faces.size(), 20, "die has twenty triangular faces");
            var numbers = new HashSet<Integer>();
            var vertices = new HashMap<CampaignDieGeometry.Point, Integer>();
            for (var face : faces) {
                numbers.add(face.number());
                for (var point : List.of(face.a(), face.b(), face.c())) vertices.merge(point, 1, Integer::sum);
                double ab = distance(face.a(), face.b());
                helper.assertTrue(Math.abs(ab - distance(face.b(), face.c())) < 0.00001 && Math.abs(ab - distance(face.c(), face.a())) < 0.00001,
                        "each die face is equilateral");
                var center = face.center(); var normal = face.normal();
                helper.assertTrue(center.x() * normal.x() + center.y() * normal.y() + center.z() * normal.z() > 0, "face normals point outward");
                if (face.number() == die) {
                    helper.assertTrue(normal.z() > 0.99999 && Math.abs(center.x()) < 0.00001 && Math.abs(center.y()) < 0.00001,
                            "saved result faces the player: " + die);
                    helper.assertTrue(face.c().y() > center.y(), "settled face is upright");
                }
            }
            helper.assertValueEqual(numbers, java.util.stream.IntStream.rangeClosed(1, 20).boxed().collect(java.util.stream.Collectors.toSet()), "every face has a unique number");
            helper.assertValueEqual(vertices.size(), 12, "icosahedron has twelve shared vertices");
            helper.assertTrue(vertices.values().stream().allMatch(count -> count == 5), "five faces meet at each vertex");
            helper.assertFalse(CampaignDieGeometry.frame(die, 0.5F).equals(faces), "intermediate frame rotates toward the saved result");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void savedRollAnimationCannotRestartOrChangeTheCheck(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        var animation = new CampaignDieAnimation();
        animation.accept(session(id, false), 100, true);
        helper.assertFalse(animation.playing(), "unresolved checks never animate an invented result");
        animation.accept(session(id, true), 200, true);
        helper.assertTrue(animation.playing(), "acknowledged result begins the animation");
        float progress = animation.progress(800);
        animation.accept(session(id, true), 800, true);
        helper.assertTrue(animation.progress(800) == progress, "duplicate acknowledgement does not restart animation");
        helper.assertFalse(animation.tick(1999), "animation stays active before its duration");
        helper.assertTrue(animation.tick(2000), "animation finishes at its duration");
        animation.accept(session(id, false), 2100, true);
        animation.accept(session(id, true), 2200, true);
        helper.assertFalse(animation.playing(), "a revisited saved check is not animated again");
        var reconnected = new CampaignDieAnimation();
        reconnected.accept(session(id, true), 100, true);
        helper.assertFalse(reconnected.playing(), "reconnecting shows the saved result immediately");
        reconnected.accept(session(UUID.randomUUID(), false), 200, true);
        var next = UUID.randomUUID();
        reconnected.accept(session(next, false), 300, true);
        reconnected.accept(session(next, true), 400, true);
        helper.assertTrue(reconnected.skip() && reconnected.progress(400) == 1, "skip lands on the acknowledged face");
        var fallback = new CampaignDieAnimation();
        fallback.accept(session(id, false), 100, false);
        fallback.accept(session(id, true), 200, false);
        helper.assertFalse(fallback.playing(), "disabled or unavailable rendering presents the result immediately");
        helper.succeed();
    }

    private static double distance(CampaignDieGeometry.Point a, CampaignDieGeometry.Point b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    private static CampaignConversationViewPayload.Session session(UUID id, boolean resolved) {
        return new CampaignConversationViewPayload.Session(id, resolved ? 2 : 1, "Guidance",
                resolved ? ConversationSession.Phase.ROLLED : ConversationSession.Phase.CHECK, null, true, false, "Player", List.of(), List.of(),
                new CampaignConversationViewPayload.Check("convince", 10, resolved, resolved ? 17 : 0, resolved), "");
    }
}
