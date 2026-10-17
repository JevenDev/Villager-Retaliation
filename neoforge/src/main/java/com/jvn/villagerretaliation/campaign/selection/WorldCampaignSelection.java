package com.jvn.villagerretaliation.campaign.selection;

import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class WorldCampaignSelection {
    private static final Map<MinecraftServer, Selection> SELECTIONS = new WeakHashMap<>();

    private WorldCampaignSelection() { }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        var world = server.getWorldPath(LevelResource.ROOT);
        try {
            if (!server.getWorldData().overworldData().isInitialized()
                    && Files.notExists(world.resolve("data").resolve(CampaignWorldModeSavedData.DATA_NAME + ".dat"))) {
                ResourceLocation choice = ResourceLocation.parse(WorldCampaignConfig.CAMPAIGN.get());
                WorldCampaign campaign = WorldCampaignCatalog.load(server.getResourceManager()).require(choice, true);
                CampaignWorldModeSavedData.createSelection(world, campaign,
                        server.isDedicatedServer() ? "server_operator" : "singleplayer_owner", server.registryAccess());
            }
            CampaignWorldModeSavedData data = CampaignWorldModeSavedData.readSelection(world, server.registryAccess());
            if (!data.diagnostic().isBlank()) throw new IllegalStateException("Cannot read permanent campaign selection: " + data.diagnostic());
            Selection selection = new Selection(data.worldCampaign(), data.includeDefaultContent());
            if (selection.id() != null) WorldCampaignCatalog.load(server.getResourceManager()).require(selection.id(), false);
            synchronized (SELECTIONS) { SELECTIONS.put(server, selection); }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read or persist the permanent world campaign selection", exception);
        }
    }

    public static Selection get(MinecraftServer server) {
        synchronized (SELECTIONS) { return SELECTIONS.getOrDefault(server, new Selection(null, true)); }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (SELECTIONS) { SELECTIONS.remove(event.getServer()); }
    }

    public record Selection(ResourceLocation id, boolean includeDefaultContent) {
        public boolean allows(ResourceLocation owner) {
            return id == null || WorldCampaign.SHARED.equals(owner) || id.equals(owner)
                    || ((owner == null || WorldCampaign.DEFAULT.equals(owner)) && (WorldCampaign.DEFAULT.equals(id) || includeDefaultContent));
        }
    }
}
