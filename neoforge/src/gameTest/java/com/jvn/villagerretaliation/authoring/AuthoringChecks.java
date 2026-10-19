package com.jvn.villagerretaliation.authoring;

import com.jvn.villagerretaliation.authoring.preview.DialoguePreviewGameTests;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AuthoringChecks {
    private AuthoringChecks() { }

    public static void main(String[] args) throws Exception {
        int count = 0;
        var failures = new java.util.ArrayList<String>();
        for (Class<?> suite : new Class<?>[] {AuthoringCoreGameTests.class, DialoguePreviewGameTests.class, com.jvn.villagerretaliation.authoring.preview.QuestPlaytestGameTests.class}) {
            for (var method : Arrays.stream(suite.getDeclaredMethods()).sorted(Comparator.comparing(java.lang.reflect.Method::getName)).toList()) {
                if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())
                        || !Arrays.equals(method.getParameterTypes(), new Class<?>[] {GameTestHelper.class})) continue;
                var assertions = new Assertions();
                try {
                    method.invoke(null, assertions);
                } catch (InvocationTargetException exception) {
                    failures.add(suite.getSimpleName() + "." + method.getName());
                    exception.getCause().printStackTrace();
                    continue;
                }
                if (!assertions.completed) {
                    failures.add("Check did not complete: " + method.getName());
                    continue;
                }
                System.out.println("PASS " + suite.getSimpleName() + "." + method.getName());
                count++;
            }
        }
        com.jvn.villagerretaliation.campaign.CampaignEvidenceAuthoringGameTests.sharedCasesMatchConditionsQuestsConversationsAndScenes(new Assertions());
        com.jvn.villagerretaliation.campaign.CampaignCompanionAuthoringGameTests.sharedCasesMatchConditionsQuestsConversationsAndScenes(new Assertions());
        com.jvn.villagerretaliation.quest.content.QuestBundleArchitectureGameTests.canonicalOwnersAndPrivateCompanionsAreEnforced(new Assertions());
        com.jvn.villagerretaliation.quest.content.QuestBundleArchitectureGameTests.fingerprintsAndSnapshotsPreserveSemanticIdentity(new Assertions());
        count += 4;
        count += com.jvn.villagerretaliation.campaign.WorldCampaignChecks.run();
        count += com.jvn.villagerretaliation.client.interaction.CameraAuthoringChecks.run();
        count += com.jvn.villagerretaliation.authoring.client.EditorClientChecks.run();
        count += com.jvn.villagerretaliation.quest.tutorial.TutorialPersistenceChecks.run();
        count += com.jvn.villagerretaliation.progress.PlayerHistoryChecks.run();
        System.out.println("Passed " + count + " standalone authoring checks.");
        if (!failures.isEmpty()) throw new AssertionError("Failed authoring checks: " + failures);
    }

    private static final class Assertions extends GameTestHelper {
        private boolean completed;

        private Assertions() { super(null); }
        @Override public void succeed() { completed = true; }
        @Override public void fail(String message) { throw new AssertionError(message); }
        @Override public void assertTrue(boolean condition, String message) {
            if (!condition) fail(message);
        }
        @Override public void assertFalse(boolean condition, String message) { assertTrue(!condition, message); }
        @Override public <N> void assertValueEqual(N actual, N expected, String message) {
            if (!Objects.equals(actual, expected)) fail(message + ": expected " + expected + ", got " + actual);
        }
    }
}
