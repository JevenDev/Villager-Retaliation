package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.client.quest.QuestTrackingSelection;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_journal")
@PrefixGameTestTemplate(false)
public final class EntityJournalGameTests {
    private EntityJournalGameTests() { }

    @GameTest(template = "empty")
    public static void trackingUsesQuestIdentityAndOrderWithoutLiveVillagers(GameTestHelper helper) {
        var wolf = entry("test:wolf", "Wolf", "active");
        var cow = entry("test:cow", "Cow", "active");
        var completed = entry("test:done", "Keeper", "completed");
        var ids = List.of("test:cow", "test:missing", "test:wolf", "test:done");
        helper.assertValueEqual(QuestTrackingSelection.entries(List.of(wolf, completed, cow), ids), List.of(cow, wolf),
                "tracked entries retain player selection order and omit missing or terminal quests");
        helper.assertValueEqual(QuestTrackingSelection.index(cow, ids), 0, "tracking uses quest identity without an entity type");
        helper.assertValueEqual(QuestTrackingSelection.index(wolf, ids), 2, "saved tracking positions remain stable");
        helper.assertValueEqual(QuestTrackingSelection.index(completed, ids), -1, "terminal quests cannot be tracked");
        helper.succeed();
    }

    private static QuestTrackerSyncPayload.Entry entry(String id, String issuer, String state) {
        return new QuestTrackerSyncPayload.Entry(id, "A task from " + issuer, "Find the trail", "", 0.0F, false,
                state, "", issuer, "Overworld", List.of());
    }
}
