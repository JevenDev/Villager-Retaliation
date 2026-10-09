package com.jvn.villagerretaliation.dialogue.conversation;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_character_repairs")
@PrefixGameTestTemplate(false)
public final class ConversationSpeakerRepairGameTests {
    private ConversationSpeakerRepairGameTests() { }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void speakerHistoryValidatesContinuityAndPreservesOriginalObjectiveEvidence(GameTestHelper helper) {
        var session = session();
        var original = session.speakers.get("mentor");
        session.transcript.add(new ConversationSession.Line("welcome", 1, "greeting", ConversationDefinition.SpeakerKind.ROLE,
                "mentor", original.entity(), original.name(), "fixture.greeting", "Welcome."));
        var evidence = new ConversationObjectiveEvidence(session.identity.owner(), ResourceLocation.parse("fixture:quest"), UUID.randomUUID(),
                "speak", "welcome", 1, "greeting", "mentor", original.entity());
        session.objectiveEvidence.put(evidence.key(session.identity), evidence);
        var replacement = new ConversationSession.Speaker("mentor", UUID.randomUUID(), original.dimension(), "Replacement");
        session.revision = 2;
        session.speakers.put("mentor", replacement);
        session.speakerHistory.add(new ConversationSpeakerHistory.Entry(original, replacement, "campaign_repair:" + UUID.randomUUID(), 2, 1));
        var saved = ConversationSessionCodec.save(session);
        var restored = ConversationSessionCodec.load(saved);
        helper.assertValueEqual(restored.snapshot(), session.snapshot(), "repaired presentation round trips");
        helper.assertValueEqual(restored.objectiveEvidence, session.objectiveEvidence, "historical speech credit retains original evidence");
        helper.assertValueEqual(restored.speakerHistory, session.speakerHistory, "repair audit round trips");
        for (String corruption : List.of("missing", "identity", "revision", "operation", "evidence")) {
            var bad = saved.copy();
            var history = bad.getList("SpeakerHistory", Tag.TAG_COMPOUND);
            switch (corruption) {
                case "missing" -> bad.remove("SpeakerHistory");
                case "identity" -> history.getCompound(0).getCompound("Before").putUUID("Entity", UUID.randomUUID());
                case "revision" -> history.getCompound(0).putLong("Revision", 3);
                case "operation" -> history.getCompound(0).putString("Authorization", "unverified");
                case "evidence" -> bad.getList("ObjectiveEvidence", Tag.TAG_COMPOUND).getCompound(0).putUUID("Speaker", replacement.entity());
            }
            boolean refused = false;
            try { ConversationSessionCodec.load(bad); } catch (IllegalArgumentException expected) { refused = true; }
            helper.assertTrue(refused, "corrupt speaker history is rejected: " + corruption);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void legacyConversationsMigrateWithoutSpeakerChangesAndRolesRemainExclusive(GameTestHelper helper) {
        var original = session();
        var legacy = ConversationSessionCodec.save(original);
        legacy.remove("SpeakerHistory");
        var root = root(7, legacy);
        var directory = Path.of(System.getProperty("java.io.tmpdir"), "conversation-repair-" + UUID.randomUUID());
        var migrated = ConversationSavedData.load(root, directory);
        helper.assertTrue(migrated.diagnostic().isBlank(), "version seven remains readable");
        helper.assertValueEqual(migrated.session(original.identity.id()).snapshot(), original.snapshot(), "migration changes no saved presentation");
        helper.assertTrue(migrated.session(original.identity.id()).speakerHistory.isEmpty(), "migration invents no repair");
        helper.assertValueEqual(migrated.save(new CompoundTag(), helper.getLevel().registryAccess()).getInt("DataVersion"), 9, "next save carries repair history");
        var duplicate = session(original.identity.campaignInstance());
        var conflicting = root(8, ConversationSessionCodec.save(original), ConversationSessionCodec.save(duplicate));
        var blocked = ConversationSavedData.load(conflicting, directory);
        helper.assertFalse(blocked.diagnostic().isBlank(), "different UUIDs cannot host simultaneous conversations for one campaign role");
        helper.assertValueEqual(blocked.save(new CompoundTag(), helper.getLevel().registryAccess()), conflicting, "conflicting sessions remain available for recovery");
        var future = root(9, ConversationSessionCodec.save(original));
        helper.assertValueEqual(ConversationSavedData.load(future, directory).save(new CompoundTag(), helper.getLevel().registryAccess()),
                future, "future save is never downgraded");
        helper.succeed();
    }

    private static CompoundTag root(int version, CompoundTag... sessions) {
        var root = new CompoundTag();
        root.putInt("DataVersion", version);
        var list = new ListTag();
        for (var session : sessions) list.add(session);
        root.put("Sessions", list);
        return root;
    }

    private static ConversationSession session() { return session(UUID.randomUUID()); }

    private static ConversationSession session(UUID campaignInstance) {
        UUID player = UUID.randomUUID(), character = UUID.randomUUID();
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var identity = new ConversationSession.Identity(UUID.randomUUID(), ResourceLocation.parse("fixture:conversation"), 1, "graph",
                ResourceLocation.parse("fixture:campaign"), campaignInstance, "campaign", null, "mentor", character, dimension,
                player, null, "Conversation", "guidance", 0);
        return new ConversationSession(identity, player, null, "welcome", new ConversationSession.Speaker("mentor", character, dimension, "Original"));
    }
}
