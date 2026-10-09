package com.jvn.villagerretaliation.dialogue.normal;

import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class DialogueTreeSessionGameTests {
    private DialogueTreeSessionGameTests() { }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void ordinaryDialogueRetainsItsCursorAndSubstitutionsAcrossReload(GameTestHelper helper) {
        var data = new DialogueTreeSessionSavedData();
        var key = new DialogueTreeService.SessionKey(UUID.randomUUID(), UUID.randomUUID());
        var session = new DialogueTreeService.Session(UUID.randomUUID(), ResourceLocation.parse("fixture:lore"),
                "road", 10, Map.of("player", "Alex", "reward", "seven"));
        data.put(key, session);
        var restored = DialogueTreeSessionSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()));
        helper.assertValueEqual(restored.get(key), session, "ordinary session survives reload without replaying actions");
        restored.remove(key);
        helper.assertTrue(DialogueTreeSessionSavedData.load(restored.save(new CompoundTag(), helper.getLevel().registryAccess())).get(key) == null,
                "explicit departure removes the saved cursor");
        helper.succeed();
    }
}
