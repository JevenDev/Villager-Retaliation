package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignDecisionParticipantsGameTests {
    private CampaignDecisionParticipantsGameTests() { }

    @GameTest(template = "empty", batch = "conversation_participation")
    public static void evidenceIsImmutableAndCannotCreditAnotherActorOrCampaign(GameTestHelper helper) {
        UUID player = UUID.randomUUID(); UUID listener = UUID.randomUUID();
        var mutable = new LinkedHashSet<>(Set.of(player, listener));
        var evidence = new CampaignDecisionParticipants(UUID.randomUUID(), UUID.randomUUID(), ResourceLocation.parse("fixture:story"),
                UUID.randomUUID(), player, mutable);
        mutable.clear();
        helper.assertValueEqual(evidence.participants(), Set.of(player, listener), "caller cannot change the saved group");
        helper.assertValueEqual(CampaignDecisionParticipants.load(evidence.save()), evidence, "evidence round trips");
        var consent = consent(evidence.campaign(), evidence.instance());
        CampaignDecisionParticipants.withParticipants(evidence.save(), () -> {
            helper.assertValueEqual(CampaignDecisionParticipants.forOutcome(player, consent), evidence.participants(), "matching decision credits its witnesses");
            helper.assertValueEqual(CampaignDecisionParticipants.forOutcome(listener, consent), Set.of(listener), "another actor cannot inherit witnesses");
            helper.assertValueEqual(CampaignDecisionParticipants.forOutcome(player, consent(ResourceLocation.parse("fixture:other"), evidence.instance())),
                    Set.of(player), "another campaign cannot inherit witnesses");
            helper.assertValueEqual(CampaignDecisionParticipants.forOutcome(player, consent(evidence.campaign(), UUID.randomUUID())),
                    Set.of(player), "another campaign instance cannot inherit witnesses");
            CampaignDecisionParticipants.withParticipants(new CompoundTag(), () -> {
                helper.assertValueEqual(CampaignDecisionParticipants.forOutcome(player, consent), Set.of(player), "ordinary nested work clears the decision scope");
                return null;
            });
            helper.assertValueEqual(CampaignDecisionParticipants.capture(), evidence.save(), "nested execution restores its caller's evidence");
            return null;
        });
        helper.assertTrue(CampaignDecisionParticipants.capture().isEmpty(), "completed work clears the decision scope");
        try {
            CampaignDecisionParticipants.withParticipants(evidence.save(), () -> { throw new IllegalStateException("fixture interruption"); });
        } catch (IllegalStateException expected) {
            helper.assertTrue(CampaignDecisionParticipants.capture().isEmpty(), "failed work also clears the decision scope");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_participation")
    public static void corruptContinuationEvidenceIsPreservedAndCannotExecute(GameTestHelper helper) {
        var continuation = new SceneContinuation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, "fixture", List.of(), 0, Map.of());
        var saved = continuation.save();
        saved.putString("DecisionParticipants", "unreadable fixture");
        var restored = SceneContinuation.load(saved);
        helper.assertFalse(CampaignDecisionParticipants.issue(restored.decisionParticipants()).isBlank(), "malformed participation blocks continuation effects");
        helper.assertValueEqual(restored.decisionParticipants().getString("Unreadable"), "unreadable fixture", "malformed evidence remains repairable");
        helper.assertValueEqual(SceneContinuation.load(restored.save()).decisionParticipants(), restored.decisionParticipants(), "subsequent reloads preserve malformed evidence");
        helper.assertTrue(CampaignDecisionParticipants.capture().isEmpty(), "reading malformed state cannot create a decision scope");
        helper.succeed();
    }

    private static CampaignOutcomeConsent consent(ResourceLocation campaign, UUID instance) {
        return new CampaignOutcomeConsent(ResourceLocation.parse("fixture:quest"), campaign, instance, "fingerprint", 0,
                UUID.randomUUID(), "decision", 0, UUID.randomUUID(), new CampaignOutcomeAction("decision", "help", "resolve"));
    }
}
