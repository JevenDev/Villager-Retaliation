package com.jvn.villagerretaliation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VrCommandGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VrCommandGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "vr_commands")
    public static void canonicalTreeUsesVrAndCamelCase(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();
        CommandNode<CommandSourceStack> vr = child(dispatcher.getRoot(), "vr");
        child(vr, "party");
        child(vr, "duel");
        CommandNode<CommandSourceStack> admin = child(vr, "admin");

        CommandNode<CommandSourceStack> villager = child(admin, "villager");
        CommandNode<CommandSourceStack> skill = child(villager, "skill");
        helper.assertTrue(skill.getChild("export") == null,
                "canonical skill commands must not duplicate full profile export");
        CommandNode<CommandSourceStack> allegiance = child(villager, "allegiance");
        child(allegiance, "undoMerge");
        child(allegiance, "resetAbuse");
        helper.assertTrue(allegiance.getChild("undo_merge") == null,
                "canonical commands must use camel case");
        helper.assertTrue(allegiance.getChild("migrate") == null,
                "canonical allegiance repair must not have a duplicate migrate command");

        CommandNode<CommandSourceStack> quest = child(admin, "quest");
        child(quest, "forceStart");
        child(quest, "whyAvailable");
        child(quest, "whyHidden");
        child(quest, "setStage");
        child(quest, "fireTrigger");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "vr_commands")
    public static void permissionsAndLegacyRootsRemainValid(GameTestHelper helper) throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();
        CommandNode<CommandSourceStack> vr = child(dispatcher.getRoot(), "vr");
        CommandNode<CommandSourceStack> admin = child(vr, "admin");
        CommandSourceStack playerSource = helper.makeMockServerPlayerInLevel()
                .createCommandSourceStack();

        helper.assertFalse(admin.canUse(playerSource.withPermission(0)),
                "non-operators must not see or execute /vr admin");
        helper.assertTrue(admin.canUse(playerSource.withPermission(2)),
                "operators must be allowed to use /vr admin");
        child(dispatcher.getRoot(), "villagerretaliation");
        child(dispatcher.getRoot(), "duel");
        helper.assertValueEqual(dispatcher.execute("vr", playerSource), 1,
                "/vr should execute root help");

        ParseResults<CommandSourceStack> parsed = dispatcher.parse(
                "vr admin villager relationship set @e[limit=1] @e[limit=1] married",
                playerSource.withPermission(2));
        helper.assertFalse(parsed.getReader().canRead(),
                "canonical relationship command should parse completely");
        helper.assertTrue(parsed.getExceptions().isEmpty(),
                "canonical relationship command should parse without exceptions");
        helper.succeed();
    }

    private static CommandNode<CommandSourceStack> child(
            CommandNode<CommandSourceStack> parent,
            String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        if (child == null) {
            throw new AssertionError("Missing command node " + name + " under " + parent.getName());
        }
        return child;
    }
}
