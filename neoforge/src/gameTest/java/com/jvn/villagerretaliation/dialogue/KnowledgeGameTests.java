package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_knowledge")
@PrefixGameTestTemplate(false)
public final class KnowledgeGameTests {
    private static final ResourceLocation TOPIC = ResourceLocation.parse("test:secret");

    @GameTest(template = "empty")
    public static void knowledgeKeepsOwnersSourcesAndStrengthAcrossReload(GameTestHelper helper) {
        var data = new KnowledgeSavedData();
        UUID witness = UUID.randomUUID(), listener = UUID.randomUUID(), stranger = UUID.randomUUID();
        helper.assertFalse(data.share(witness, listener, TOPIC, false, 0), "unknown information cannot be shared");
        helper.assertTrue(data.witness(witness, TOPIC, 1), "witness learns a fact");
        helper.assertTrue(data.share(witness, listener, TOPIC, true, 2), "listener gets only a hint");
        data = KnowledgeSavedData.load(data.save(new CompoundTag(), null), null);
        helper.assertTrue(data.available(), "saved knowledge loads");
        helper.assertValueEqual(data.knowledge(listener, TOPIC).orElseThrow().kind(), "partial", "hint stays incomplete");
        helper.assertValueEqual(data.knowledge(listener, TOPIC).orElseThrow().source(), witness, "source survives reload");
        helper.assertTrue(data.knowledge(stranger, TOPIC).isEmpty(), "unrelated NPCs do not inherit secrets");
        helper.assertTrue(data.share(listener, stranger, TOPIC, false, 3), "a hint can be repeated");
        helper.assertValueEqual(data.knowledge(stranger, TOPIC).orElseThrow().kind(), "partial", "retelling cannot turn a hint into full knowledge");
        helper.assertTrue(data.share(witness, listener, TOPIC, false, 4), "trusted disclosure upgrades the hint");
        helper.assertFalse(data.share(witness, listener, TOPIC, true, 5), "hints cannot downgrade full knowledge");
        helper.assertTrue(data.witness(listener, TOPIC, 6), "firsthand evidence upgrades hearsay");
        helper.assertFalse(data.witness(listener, TOPIC, 7), "retries do not rewrite acquisition time");
        helper.assertValueEqual(data.knowledge(listener, TOPIC).orElseThrow().learnedAt(), 6L, "first acquisition receipt retained");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actionsAndConditionsRespectSpeakerAndPlayerOwnership(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var speaker = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var other = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, speaker);
        var share = action("share", "player");
        helper.assertFalse(context.actions().execute(context, share, Map.of()).success(), "speaker cannot invent a secret");
        helper.assertTrue(context.actions().execute(context, action("witness", "speaker"), Map.of()).success(), "witness action succeeds");
        helper.assertTrue(context.actions().execute(context, action("hint", "player"), Map.of()).success(), "hint action succeeds");
        var complete = new Knowledge.Condition(TOPIC, "player", "complete", "speaker");
        helper.assertFalse(complete.matches(context), "hint does not unlock complete knowledge choices");
        helper.assertTrue(context.actions().execute(context, share, Map.of()).success(), "full disclosure succeeds");
        helper.assertTrue(complete.matches(context), "full disclosure unlocks its source-specific choice");
        var otherContext = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, other);
        helper.assertFalse(complete.matches(otherContext), "another NPC cannot claim to be the source");
        helper.assertTrue(otherContext.actions().execute(otherContext, action("share", "speaker"), Map.of()).success(), "player can tell another NPC");
        helper.assertTrue(new Knowledge.Condition(TOPIC, "speaker", "heard", "player").matches(otherContext), "NPC remembers who told them");
        helper.assertTrue(context.actions().execute(context, action("forget", "player"), Map.of()).success(), "forget removes only the recipient knowledge");
        helper.assertTrue(new Knowledge.Condition(TOPIC, "speaker", "witnessed", "").matches(context), "speaker retains their knowledge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedKnowledgeIsPreservedAndUnavailable(GameTestHelper helper) {
        var tag = new CompoundTag();
        tag.putInt("Version", 99);
        tag.putString("FutureData", "preserve me");
        var data = KnowledgeSavedData.load(tag, null);
        helper.assertFalse(data.available(), "unknown save formats fail closed");
        helper.assertFalse(data.witness(UUID.randomUUID(), TOPIC, 0), "damaged data cannot be mutated");
        helper.assertValueEqual(data.save(new CompoundTag(), null), tag, "unknown data is preserved for repair");
        for (String json : List.of(
                "{\"type\":\"knowledge\",\"topic\":\"test:secret\",\"recipient\":\"everyone\",\"operation\":\"witness\"}",
                "{\"type\":\"knowledge\",\"topic\":\"BAD\",\"recipient\":\"speaker\",\"operation\":\"witness\"}",
                "{\"type\":\"knowledge\",\"topic\":\"test:secret\",\"recipient\":\"speaker\",\"operation\":\"witness\",\"typo\":true}")) {
            helper.assertTrue(VillagerActionDefinition.readObjects(TOPIC, "test", List.of(JsonParser.parseString(json).getAsJsonObject()), null).isEmpty(),
                    "invalid knowledge action is rejected");
        }
        helper.succeed();
    }

    private static VillagerActionDefinition action(String operation, String recipient) {
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "knowledge");
        json.addProperty("topic", TOPIC.toString());
        json.addProperty("operation", operation);
        json.addProperty("recipient", recipient);
        return VillagerActionDefinition.readObjects(TOPIC, "test", List.of(json), null).getFirst();
    }
}
