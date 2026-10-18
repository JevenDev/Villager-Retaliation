package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;

final class EditorTestWorld {
    private EditorTestWorld() { }

    static Path snapshot(AuthoringWorkspace workspace) throws IOException {
        Path directory = Files.createTempDirectory("vr-test-world-");
        workspace.exportPack(directory.resolve("test_content"));
        return directory;
    }

    static void prepare(AuthoringWorkspace workspace, net.minecraft.client.gui.screens.Screen editor) throws IOException {
        Path pack = snapshot(workspace);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed) { minecraft.setScreen(editor); return; }
            minecraft.disconnect(new TitleScreen());
            CreateWorldScreen.openFresh(minecraft, new TitleScreen());
            if (!(minecraft.screen instanceof CreateWorldScreen fresh)) return;
            var context = fresh.getUiState().getSettings();
            var settings = new LevelSettings("Authoring Test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), context.dataConfiguration());
            minecraft.setScreen(CreateWorldScreen.createFromExisting(minecraft, new TitleScreen(), settings, context, pack));
        }, Component.literal("Create a separate test world?"),
                Component.literal("Your workspace is saved. This leaves the current world and opens a creative test world. Enable test_content under Data Packs before creating it.")));
    }
}
