package com.jvn.villagerretaliation.client.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.lwjgl.glfw.GLFW;

final class CampaignDiskReloadChecks {
    private static final ResourceLocation COVER = ResourceLocation.parse("villagerretaliation:world_campaigns/default/live-cover.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.parse("villagerretaliation:world_campaigns/default/live.gif");
    private static Path source;
    private static Path folder;
    private static byte[] originalJson;
    private static byte[] animation;

    private CampaignDiskReloadChecks() { }

    static void start(CampaignSelectScreen screen) throws Exception {
        source = Path.of(System.getProperty(CampaignResources.SOURCE_RESOURCES_PROPERTY)).toAbsolutePath().normalize();
        Path expected = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().resolve("campaign-source-resources");
        require(source.equals(expected), "Disk reload checks use an isolated source copy inside the smoke directory");
        folder = source.resolve("data/villagerretaliation/world_campaigns/default");
        originalJson = Files.readAllBytes(folder.resolve("campaign.json"));
        priority();
        write(screen, false);
        button(screen, "Expand Disk title before").onPress();
    }

    static void edit(CampaignSelectScreen screen) throws Exception {
        write(screen, true);
    }

    private static void write(CampaignSelectScreen screen, boolean after) throws Exception {
        JsonObject root = JsonParser.parseString(new String(originalJson, StandardCharsets.UTF_8)).getAsJsonObject();
        root.addProperty("name", after ? "Disk title after" : "Disk title before");
        root.add("authors", JsonParser.parseString(after ? "[\"Disk Author B\",\"Second Author\"]" : "[\"Disk Author A\"]"));
        root.addProperty("image", COVER.toString());
        root.addProperty("description_file", "villagerretaliation:world_campaigns/default/live-description.md");
        root.remove("description");
        Files.writeString(folder.resolve("campaign.json"), root.toString());
        Files.writeString(folder.resolve("live-description.md"), (after ? "## Markdown after\n§cChanged on disk§r" : "## Markdown before\nOriginal disk content")
                + "\n![Animation](live.gif)");
        Files.write(folder.resolve("live-cover.png"), CampaignCardClientChecks.cover(after ? 0xff0000ff : 0xffff0000));
        animation = CampaignCardClientChecks.gif(after ? "restoreToBackgroundColor" : "restoreToPrevious");
        Files.write(folder.resolve("live.gif"), animation);
        CampaignCardClientChecks.reloaded(screen);
    }

    static void verify(CampaignSelectScreen screen, boolean after) throws Exception {
        String name = after ? "Disk title after" : "Disk title before";
        var campaigns = (List<?>) field(screen, "campaigns");
        var campaign = campaigns.stream().map(WorldCampaign.class::cast).filter(value -> value.id().equals(WorldCampaign.DEFAULT)).findFirst().orElseThrow();
        require(campaign.name().equals(name), "F5 reads the edited JSON title directly from disk");
        require(campaign.authors().equals(after ? List.of("Disk Author B", "Second Author") : List.of("Disk Author A")), "F5 reads edited authors directly from disk");
        require(campaign.description().contains(after ? "Markdown after" : "Markdown before"), "F5 reads the edited Markdown file directly from disk");
        require(field(screen, "selected") == campaign && field(screen, "expanded") == campaign, "Disk reload retains the selected and expanded campaign by ID");
        Button card = button(screen, name);
        var authorLabel = card.getClass().getDeclaredField("authorText");
        authorLabel.setAccessible(true);
        var authorTooltip = card.getClass().getDeclaredField("authorTooltip");
        authorTooltip.setAccessible(true);
        StringBuilder tooltip = new StringBuilder();
        for (var line : ((Tooltip) authorTooltip.get(card)).toCharSequence(Minecraft.getInstance())) {
            if (!tooltip.isEmpty()) tooltip.append(' ');
            line.accept((index, style, codepoint) -> { tooltip.appendCodePoint(codepoint); return true; });
        }
        require(((String) authorLabel.get(card)).startsWith(after ? "Disk Author B" : "Disk Author A")
                && tooltip.toString().equals(after ? "Disk Author B, Second Author" : "Disk Author A"), "Rendered author labels and hover tooltips refresh from the edited file");
        var cover = (CampaignMedia) ((Map<?, ?>) field(screen, "images")).get(COVER);
        var texture = CampaignMedia.class.getDeclaredField("dynamic");
        texture.setAccessible(true);
        var pixels = ((DynamicTexture) texture.get(cover)).getPixels();
        require(pixels.getPixelRGBA(0, 0) == (after ? 0xffff0000 : 0xff0000ff) && pixels.getPixelRGBA(1, 0) >>> 24 == 64,
                "The rendered cover updates after overwriting the same PNG path and preserves alpha");
        var descriptions = (Map<?, ?>) field(screen, "descriptions");
        var blocks = (List<?>) descriptions.get(WorldCampaign.DEFAULT);
        require(((CampaignDescription.Block) blocks.getFirst()).text().getString().equals(after ? "Markdown after" : "Markdown before"), "Rendered Markdown refreshes from the file edit");
        var media = (CampaignMedia) ((Map<?, ?>) field(screen, "descriptionImages")).get(ANIMATION);
        var bytes = CampaignMedia.class.getDeclaredField("bytes");
        bytes.setAccessible(true);
        require(media.available() && Arrays.equals((byte[]) bytes.get(media), animation), "GIF bytes reload after overwriting the same file path");
    }

    static void restore(CampaignSelectScreen screen) throws Exception {
        Object current = field(screen, "selected");
        Files.writeString(folder.resolve("campaign.json"), "{invalid");
        screen.keyPressed(GLFW.GLFW_KEY_F5, 0, 0);
        require(field(screen, "selected") == current && field(screen, "reloadStatus").equals(Component.translatable("screen.villagerretaliation.campaign.reload_failed")),
                "An invalid file edit preserves the current campaign and reports the failure");
        Files.write(folder.resolve("campaign.json"), originalJson);
        for (String name : List.of("live-description.md", "live-cover.png", "live.gif")) Files.delete(folder.resolve(name));
        CampaignCardClientChecks.reloaded(screen);
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
    }

    private static void priority() throws Exception {
        Path game = Minecraft.getInstance().gameDirectory.toPath();
        Path stale = game.resolve("stale-campaign-pack");
        Path override = game.resolve("override-campaign-pack");
        Path json = Path.of("data/villagerretaliation/world_campaigns/default/campaign.json");
        JsonObject builtin = JsonParser.parseString(new String(originalJson, StandardCharsets.UTF_8)).getAsJsonObject();
        builtin.remove("description_file");
        builtin.addProperty("description", "Disk fixture description");
        builtin.addProperty("name", "Stale compiled title");
        for (Path pack : List.of(stale, override)) {
            Files.createDirectories(pack.resolve(json).getParent());
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Disk reload fixture\",\"pack_format\":"
                    + SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA) + "}}");
        }
        Files.writeString(stale.resolve(json), builtin.toString());
        builtin.addProperty("name", "Selected datapack title");
        Files.writeString(override.resolve(json), builtin.toString());
        PackRepository repository = new PackRepository(consumer -> {
            consumer.accept(pack("mod/villagerretaliation", stale));
            consumer.accept(pack("file/override", override));
        });
        repository.reload();
        repository.setSelected(List.of("mod/villagerretaliation", "file/override"));
        try (var resources = CampaignResources.open(repository)) {
            require(WorldCampaignCatalog.load(resources).require(WorldCampaign.DEFAULT, false).name().equals("Selected datapack title"), "Source resources preserve selected datapack priority");
        }
        builtin.addProperty("name", "Edited selected datapack title");
        Files.writeString(override.resolve(json), builtin.toString());
        try (var resources = CampaignResources.open(repository)) {
            require(WorldCampaignCatalog.load(resources).require(WorldCampaign.DEFAULT, false).name().equals("Edited selected datapack title"), "Folder datapack edits also reload directly from disk");
        }
        repository.setSelected(List.of("mod/villagerretaliation"));
        String configured = System.clearProperty(CampaignResources.SOURCE_RESOURCES_PROPERTY);
        try (var resources = CampaignResources.open(repository)) {
            require(WorldCampaignCatalog.load(resources).require(WorldCampaign.DEFAULT, false).name().equals("Stale compiled title"), "Without a source directory, the selected packaged resources remain authoritative");
        } finally {
            System.setProperty(CampaignResources.SOURCE_RESOURCES_PROPERTY, configured);
        }
        try (var resources = CampaignResources.open(repository)) {
            require(WorldCampaignCatalog.load(resources).require(WorldCampaign.DEFAULT, false).name().equals(
                    JsonParser.parseString(new String(originalJson, StandardCharsets.UTF_8)).getAsJsonObject().get("name").getAsString()), "The source directory replaces the compiled resource copy");
        }
        Files.delete(folder.resolve("campaign.json"));
        try (var resources = CampaignResources.open(repository)) {
            require(!WorldCampaignCatalog.load(resources).campaigns().containsKey(WorldCampaign.DEFAULT), "Deleted source campaigns do not reappear from stale compiled copies");
        } finally {
            Files.write(folder.resolve("campaign.json"), originalJson);
        }
    }

    private static Pack pack(String id, Path directory) {
        var location = new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(location, new PathPackResources.PathResourcesSupplier(directory), PackType.SERVER_DATA,
                new PackSelectionConfig(false, Pack.Position.TOP, false));
        require(pack != null, "Disk fixture pack metadata loads");
        return pack;
    }

    private static Object field(CampaignSelectScreen screen, String name) throws ReflectiveOperationException {
        var field = CampaignSelectScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static Button button(CampaignSelectScreen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
