package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.DialogueCameraReference;
import com.jvn.villagerretaliation.dialogue.DialogueCameraReferences;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_camera")
@PrefixGameTestTemplate(false)
public final class ConversationCameraReferenceGameTests {
    private ConversationCameraReferenceGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void sessionCopiesAndSavesRetainCameraBindings(GameTestHelper helper) {
        UUID player = UUID.randomUUID(), character = UUID.randomUUID();
        var identity = new ConversationSession.Identity(UUID.randomUUID(), ResourceLocation.parse("fixture:camera"), 1, "graph",
                ResourceLocation.parse("fixture:campaign"), UUID.randomUUID(), "campaign", null, "guide", character,
                ResourceLocation.parse("minecraft:overworld"), player, null, "Camera", "camera", 0);
        var session = new ConversationSession(identity, player, null, "welcome",
                new ConversationSession.Speaker("guide", character, identity.dimension(), "Guide"));
        var reference = DialogueCameraReference.read(com.google.gson.JsonParser.parseString("""
                {"id":"table","kind":"block","match":"minecraft:crafting_table","radius":0}
                """).getAsJsonObject());
        session.cameraReferences.put(reference.id(), new DialogueCameraReferences.Saved(reference,
                new DialogueCameraReference.Binding(identity.dimension(), null, new BlockPos(10, 64, -4), "")));
        var saved = ConversationSessionCodec.save(session);
        helper.assertValueEqual(ConversationSessionCodec.load(saved).cameraReferences, session.cameraReferences, "camera references survive session reload");
        var copy = new ConversationSession(session);
        copy.cameraReferences.clear();
        helper.assertValueEqual(session.cameraReferences.size(), 1, "session copies cannot mutate stored references");
        var broken = saved.copy();
        broken.getCompound("CameraReferences").getCompound("table").putString("Binding", "{");
        try {
            ConversationSessionCodec.load(broken);
            helper.fail("malformed saved bindings must be rejected through the save recovery boundary");
        } catch (IllegalArgumentException expected) { }
        saved.remove("CameraReferences");
        helper.assertTrue(ConversationSessionCodec.load(saved).cameraReferences.isEmpty(), "older sessions still load");
        helper.succeed();
    }
}
