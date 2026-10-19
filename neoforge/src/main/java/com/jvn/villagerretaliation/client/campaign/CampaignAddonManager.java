package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

public final class CampaignAddonManager {
    private CampaignAddonManager() { }

    public static void open(Screen parent, LevelStorageSource.LevelStorageAccess access) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new CampaignLoadingScreen(parent, () -> {
            try {
                var world = access.getLevelPath(LevelResource.ROOT);
                var data = CampaignWorldModeSavedData.readSelection(world, null);
                if (!data.diagnostic().isBlank()) throw new IllegalStateException(data.diagnostic());
                if (data.worldCampaign() == null) throw new IllegalStateException("This world has no saved campaign selection");
                var repository = ServerPacksSource.createPackRepository(access);
                var packs = LevelStorageSource.getPackConfig(access.getDataTag(), repository, false).createResourceManager();
                try (var resources = packs.getSecond()) {
                    var catalog = WorldCampaignCatalog.load(resources);
                    var selection = new WorldCampaignSelection.Selection(data.worldCampaign(), data.includeDefaultContent(), data.addons());
                    catalog.validate(selection, false);
                    var icons = new LinkedHashMap<ResourceLocation, CampaignMedia>();
                    catalog.addonChoices().forEach(addon -> icons.put(addon.id(), CampaignMedia.addonIcon(resources, addon)));
                    Runnable close = () -> {
                        icons.values().forEach(CampaignMedia::close);
                        minecraft.setScreen(parent);
                    };
                    return new CampaignAddonScreen(catalog, catalog.require(selection.id(), false), selection.addons(), icons, selection, enabled -> {
                        try {
                            CampaignWorldModeSavedData.addToExistingWorld(world, enabled, catalog);
                            close.run();
                        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                            icons.values().forEach(CampaignMedia::close);
                            minecraft.setScreen(new AlertScreen(() -> minecraft.setScreen(parent),
                                    Component.translatable("screen.villagerretaliation.campaign.error"), Component.literal(exception.getMessage())));
                        }
                    }, close);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not open campaign addons", exception);
            }
        }));
    }
}
