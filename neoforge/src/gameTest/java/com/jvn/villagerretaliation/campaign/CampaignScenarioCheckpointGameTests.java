package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignScenarioCheckpointGameTests {
    private CampaignScenarioCheckpointGameTests() { }

    @GameTest(template = "empty", batch = "campaign_scenario_checkpoint")
    public static void collidingPlayerIdentitiesDoNotChangeTheQuestCheckpointOnReload(GameTestHelper helper) {
        var data = new VillagerQuestSavedData();
        var first = new UUID(0, 1);
        var second = new UUID(0, 17);
        var quest = ResourceLocation.parse("test:checkpoint");
        data.getOrCreate(first, quest);
        data.getOrCreate(second, quest);
        var original = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = VillagerQuestSavedData.load(original.copy(), helper.getLevel().registryAccess())
                .save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertFalse(original.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).getUUID("Player")
                .equals(restored.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).getUUID("Player")),
                "colliding hash buckets change record iteration order after reload");
        helper.assertValueEqual(CampaignScenarioExecution.questSnapshot(original), CampaignScenarioExecution.questSnapshot(restored),
                "checkpoint compares records by player and quest identity");
        helper.assertValueEqual(original.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).getUUID("Player"), second,
                "checkpoint canonicalization does not mutate the saved input");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scenario_checkpoint")
    public static void questCheckpointsStillDetectProgressAndTrackerOrderChanges(GameTestHelper helper) {
        var player = new UUID(0, 1);
        var data = new VillagerQuestSavedData();
        data.getOrCreate(player, ResourceLocation.parse("test:first"));
        var original = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var tracked = new ListTag();
        for (String quest : java.util.List.of("test:first", "test:second")) {
            var entry = new CompoundTag();
            entry.putUUID("Player", player); entry.putString("Quest", quest);
            tracked.add(entry);
        }
        original.put("TrackedQuests", tracked);
        var checkpoint = CampaignScenarioExecution.questSnapshot(original);
        var changed = original.copy();
        changed.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).putInt("CompletionCount", 1);
        helper.assertFalse(checkpoint.equals(CampaignScenarioExecution.questSnapshot(changed)), "changed progress fails the checkpoint");
        changed = original.copy();
        var priorities = changed.getList("TrackedQuests", Tag.TAG_COMPOUND);
        priorities.add(priorities.remove(0));
        helper.assertFalse(checkpoint.equals(CampaignScenarioExecution.questSnapshot(changed)), "tracker priority remains ordered within each player");
        changed = original.copy();
        changed.putInt("DataVersion", original.getInt("DataVersion") + 1);
        helper.assertFalse(checkpoint.equals(CampaignScenarioExecution.questSnapshot(changed)), "other saved fields remain part of the checkpoint");
        helper.succeed();
    }
}
