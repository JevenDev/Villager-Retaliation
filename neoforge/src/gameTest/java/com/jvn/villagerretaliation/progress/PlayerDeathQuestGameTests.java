package com.jvn.villagerretaliation.progress;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_boundary")
@PrefixGameTestTemplate(false)
public final class PlayerDeathQuestGameTests {
    private PlayerDeathQuestGameTests() { }

    @GameTest(template = "empty", batch = "player_death_quest")
    public static void reloadedDeathNotificationAdvancesAnActiveQuestOnlyOnce(GameTestHelper helper) {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"player_history:death_quest",
                 "metadata":{"title":"Remember a fall"},"provider":{"type":"villagerretaliation:villager"},
                 "availability":{"repeatable":true},"entry_stage":"work","stages":[
                  {"id":"work","objectives":[{"id":"deaths","type":"criterion",
                   "criterion":"villagerretaliation:player_died","match":{"damage_type":"minecraft:fall"},"count":2}],
                   "complete_when":["deaths"],"next":"return"},
                  {"id":"return","objectives":[]}]}
                """).getAsJsonObject();
        var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("player_history:quests/death_quest.json"), raw).orElseThrow();
        var compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
        var player = helper.makeMockServerPlayerInLevel();
        var issuer = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        issuer.setNoAi(true);
        var level = helper.getLevel();
        VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(compiled));
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            var context = VillagerInteractionService.createDialogueContext(level, player, issuer);
            var accepted = VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.START).orElseThrow();
            helper.assertValueEqual(accepted.status(), "started", "native acceptance starts the death quest");
            var progress = VillagerQuestSavedData.get(level).get(player.getUUID(), compiled.id());
            var death = new PlayerHistorySavedData.Death(ResourceLocation.parse("minecraft:fall"), null, null, "", level.dimension().location(),
                    player.blockPosition(), level.getGameTime(), "Fell");
            var data = PlayerHistorySavedData.get(level);
            data.recordDeath(player.getUUID(), death);
            var loaded = PlayerHistorySavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            level.getServer().overworld().getDataStorage().set("villagerretaliation_player_history", loaded);
            player.setHealth(0);
            PlayerHistoryService.tick(player);
            helper.assertValueEqual(progress.objectiveCounter(compiled.asQuestDefinition().objectives().getFirst().id()), 0, "death-screen activity cannot advance a quest");
            player.setHealth(20);
            PlayerHistoryService.tick(player);
            helper.assertValueEqual(progress.objectiveCounter(compiled.asQuestDefinition().objectives().getFirst().id()), 1, "reloaded pending death advances the quest after respawn");
            PlayerHistoryService.tick(player);
            DialoguePlaceholders.base(DialogueContext.forPlayer(player));
            helper.assertValueEqual(progress.objectiveCounter(compiled.asQuestDefinition().objectives().getFirst().id()), 1, "ticks and dialogue cannot repeat quest delivery");
            helper.assertValueEqual(PlayerHistoryService.replacements(player).get("last_death_message"), "Fell", "quest delivery retains dialogue text");
        } finally {
            VillagerQuestSavedData.get(level).remove(player.getUUID(), compiled.id());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            VillagerQuestService.clearRuntimeState(player);
            VillagerQuestResources.clearCache();
            issuer.discard();
        }
        helper.succeed();
    }
}
