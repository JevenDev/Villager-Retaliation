package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.CloseableResourceManager;

final class CampaignCardClientChecks {
    private CampaignCardClientChecks() { }

    static CampaignSelectScreen fixture(Screen parent) throws Exception {
        markdown();
        media();
        Map<ResourceLocation, WorldCampaign> campaigns = new LinkedHashMap<>();
        for (int index = 1; index <= 7; index++) {
            var id = ResourceLocation.parse("test:card" + index);
            String description = "# A new adventure\n**Bold** and *italic* with ~~strike~~ and `code`.\n"
                    + "![Animated preview](preview.gif)\n## Explore\n"
                    + "### Choosing this option will §cPERMANENTLY LOCK§r this world into the Kingdoms Campaign.\n"
                    + "Explore the village and follow the unfolding campaign story alongside its villagers.\n".repeat(30);
            campaigns.put(id, new WorldCampaign(id, "Campaign " + index, description, List.of("Author One", "Author Two"),
                    index <= 2 ? ResourceLocation.parse("test:shared_cover.png") : WorldCampaign.CARD_FALLBACK, index == 2, false));
        }
        var source = Minecraft.getInstance().getResourceManager().getResourceOrThrow(WorldCampaign.CARD_FALLBACK).source();
        Map<ResourceLocation, Resource> images = new LinkedHashMap<>();
        byte[] animation = gif("restoreToPrevious");
        byte[] cover = cover();
        images.put(ResourceLocation.parse("test:shared_cover.png"), new Resource(source, () -> new ByteArrayInputStream(cover)));
        campaigns.keySet().forEach(id -> images.put(CampaignDescription.imageLocation(id, "preview.gif"),
                new Resource(source, () -> new ByteArrayInputStream(animation))));
        for (var campaign : campaigns.values()) {
            var root = new com.google.gson.JsonObject();
            root.addProperty("schema", com.jvn.villagerretaliation.campaign.selection.WorldCampaignSchema.ID);
            root.addProperty("id", campaign.id().toString());
            root.addProperty("name", campaign.name());
            var authors = new com.google.gson.JsonArray();
            campaign.authors().forEach(authors::add);
            root.add("authors", authors);
            root.addProperty("image", campaign.image().toString());
            root.addProperty("locked", campaign.locked());
            root.addProperty("include_default_content", campaign.includeDefaultContent());
            root.addProperty("description", "Inline fallback");
            root.addProperty("description_file", CampaignDescription.imageLocation(campaign.id(), "description.md").toString());
            put(images, CampaignDescription.imageLocation(campaign.id(), "campaign.json"), root.toString().getBytes(StandardCharsets.UTF_8));
            put(images, CampaignDescription.imageLocation(campaign.id(), "description.md"), campaign.description().getBytes(StandardCharsets.UTF_8));
        }
        return new CampaignSelectScreen(parent, new WorldCampaignCatalog(campaigns), new Images(images), new ReloadSource(images), ignored -> { });
    }

    static void reloaded(CampaignSelectScreen screen) throws Exception {
        require(screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F5, 0, 0), "Campaign screen handles MarkedDown's F5 shortcut");
        require(field(screen, "reloadStatus").equals(Component.translatable("screen.villagerretaliation.campaign.reloaded")), "Campaign reload succeeds");
        require(Minecraft.getInstance().screen == screen, "Reload keeps the campaign screen open");
    }

    static void reload(CampaignSelectScreen screen) throws Exception {
        var source = (ReloadSource) field(screen, "reloadResources");
        var original = Map.copyOf(source.files);
        var selected = (WorldCampaign) field(screen, "selected");
        var id = ResourceLocation.parse("test:card1");
        var json = CampaignDescription.imageLocation(id, "campaign.json");
        var markdown = CampaignDescription.imageLocation(id, "description.md");
        var preview = CampaignDescription.imageLocation(id, "preview.gif");
        var added = CampaignDescription.imageLocation(id, "new.png");
        var sharedCover = ResourceLocation.parse("test:shared_cover.png");
        var changedCover = ResourceLocation.parse("test:changed_cover.png");
        var oldPreview = (CampaignMedia) ((Map<?, ?>) field(screen, "descriptionImages")).get(preview);
        var oldCover = ((Map<?, ?>) field(screen, "images")).get(sharedCover);
        byte[] replacement = cover(0xff0000ff);
        var root = readJson(source.files, json);
        root.addProperty("name", "Updated campaign");
        root.add("authors", com.google.gson.JsonParser.parseString("[\"New author\",\"Another author\",\"Overflow author\"]"));
        root.addProperty("image", changedCover.toString());
        root.addProperty("include_default_content", true);
        put(source.files, json, root.toString().getBytes(StandardCharsets.UTF_8));
        put(source.files, markdown, "# Updated live\n**Edited** description.\n![Preview](preview.gif)\n![New](new.png)".getBytes(StandardCharsets.UTF_8));
        for (var image : List.of(preview, added, sharedCover, changedCover)) put(source.files, image, replacement);
        reloaded(screen);
        var refreshed = (WorldCampaign) field(screen, "selected");
        require(refreshed != selected && refreshed.id().equals(selected.id()) && field(screen, "expanded") == refreshed,
                "Reload preserves selection and expansion by ID with fresh metadata");
        require(refreshed.name().equals("Updated campaign") && refreshed.authors().equals(List.of("New author", "Another author", "Overflow author"))
                && refreshed.image().equals(changedCover) && refreshed.includeDefaultContent(), "F5 updates title, authors, cover path, and creation settings");
        var card = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Updated campaign")).findFirst().orElseThrow();
        var authorText = card.getClass().getDeclaredField("authorText");
        authorText.setAccessible(true);
        var authorTooltip = card.getClass().getDeclaredField("authorTooltip");
        authorTooltip.setAccessible(true);
        require(((String) authorText.get(card)).startsWith("New author")
                && tooltipText((net.minecraft.client.gui.components.Tooltip) authorTooltip.get(card)).contains("New author, Another author, Overflow author"),
                "Rebuilt author labels and tooltips use refreshed authors");
        var caret = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Collapse Updated campaign")).findFirst().orElseThrow();
        require(tooltipText(caret.getTooltip()).equals("Collapse Updated campaign"), "Caret tooltips contain no reload hint");
        var descriptions = (Map<?, ?>) field(screen, "descriptions");
        var updated = (List<?>) descriptions.get(id);
        var heading = (CampaignDescription.Block) updated.getFirst();
        require(heading.text().getString().equals("Updated live") && heading.scale() == 2, "F5 reparses edited Markdown headings");
        require(scroll(screen) == 0, "Reload resets scrolling for changed content");
        var images = (Map<?, ?>) field(screen, "descriptionImages");
        var covers = (Map<?, ?>) field(screen, "images");
        var newPreview = (CampaignMedia) images.get(preview);
        require(newPreview != oldPreview && newPreview.available() && ((CampaignMedia) images.get(added)).available(), "Reload replaces cached images and loads new destinations");
        var bytes = CampaignMedia.class.getDeclaredField("bytes");
        bytes.setAccessible(true);
        require(java.util.Arrays.equals((byte[]) bytes.get(newPreview), replacement), "Description media bytes are reread at the same path");
        require(covers.get(sharedCover) != oldCover && java.util.Arrays.equals((byte[]) bytes.get(covers.get(sharedCover)), replacement)
                && java.util.Arrays.equals((byte[]) bytes.get(covers.get(changedCover)), replacement), "Reload refreshes existing cover bytes and loads changed cover paths");
        var frames = CampaignMedia.class.getDeclaredField("frames");
        frames.setAccessible(true);
        require(((List<?>) frames.get(oldPreview)).isEmpty() && ((List<?>) frames.get(oldCover)).isEmpty(), "Superseded cover and description media release decoded frames");
        require(source.opens == 1 && source.closes == 1, "Each reload opens and closes its own datapack resources");
        source.files.remove(markdown);
        screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F5, 0, 0);
        require(field(screen, "reloadStatus").equals(Component.translatable("screen.villagerretaliation.campaign.reload_failed")), "Missing Markdown reports a reload error");
        require(descriptions.get(id) == updated && images.get(preview) == newPreview && field(screen, "selected") == refreshed,
                "Failed reload preserves the previous campaign metadata, descriptions, and images");
        require(source.opens == 2 && source.closes == 2, "Failed reload also closes its resources");
        put(source.files, markdown, "x".repeat(4097).getBytes(StandardCharsets.UTF_8));
        screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F5, 0, 0);
        require(descriptions.get(id) == updated && images.get(preview) == newPreview, "Invalid edited Markdown preserves the previous preview");
        put(source.files, json, "{broken".getBytes(StandardCharsets.UTF_8));
        screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F5, 0, 0);
        require(descriptions.get(id) == updated && images.get(preview) == newPreview, "Malformed edited JSON preserves the current preview");
        source.files.clear();
        source.files.putAll(original);
        root = readJson(source.files, json);
        root.addProperty("locked", true);
        put(source.files, json, root.toString().getBytes(StandardCharsets.UTF_8));
        reloaded(screen);
        require(field(screen, "selected") == null && ((WorldCampaign) field(screen, "expanded")).locked()
                && !((Button) field(screen, "createButton")).active, "Reloading coming-soon status clears unavailable selections and disables creation");
        source.files.remove(json);
        reloaded(screen);
        require(field(screen, "expanded") == null && field(screen, "selected") == null && ((List<?>) field(screen, "campaigns")).size() == 6,
                "Removing the expanded campaign safely collapses its card");
        var addedId = ResourceLocation.parse("test:card8");
        root = readJson(original, json);
        root.addProperty("id", addedId.toString());
        root.addProperty("name", "Campaign 8");
        root.remove("description_file");
        root.addProperty("description", "Added campaign description");
        put(source.files, CampaignDescription.imageLocation(addedId, "campaign.json"), root.toString().getBytes(StandardCharsets.UTF_8));
        reloaded(screen);
        require(((List<?>) field(screen, "campaigns")).size() == 7 && descriptions.containsKey(addedId), "F5 includes newly added campaigns and inline descriptions");
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(">")).findFirst().orElseThrow().onPress();
        source.files.keySet().removeIf(location -> location.getPath().endsWith("/campaign.json"));
        reloaded(screen);
        require(((List<?>) field(screen, "campaigns")).isEmpty() && (Integer) field(screen, "page") == 0 && cards(screen) == 0
                && !((Button) field(screen, "createButton")).active, "An empty reloaded catalog clamps pagination and disables creation");
        source.files.clear();
        source.files.putAll(original);
        reloaded(screen);
        require(field(screen, "expanded") == null && field(screen, "selected") == null && cards(screen) == 3,
                "Reloading collapsed cards restores the campaign catalog without selecting a campaign");
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Expand Campaign 1")).findFirst().orElseThrow().onPress();
        require(source.opens == 9 && source.closes == 9, "Repeated reloads keep resource ownership balanced");
    }

    private static com.google.gson.JsonObject readJson(Map<ResourceLocation, Resource> files, ResourceLocation id) throws java.io.IOException {
        try (var reader = files.get(id).openAsReader()) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String tooltipText(net.minecraft.client.gui.components.Tooltip tooltip) {
        StringBuilder text = new StringBuilder();
        for (var line : tooltip.toCharSequence(Minecraft.getInstance())) {
            if (!text.isEmpty()) text.append(' ');
            line.accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
        }
        return text.toString();
    }

    private static Object field(CampaignSelectScreen screen, String name) throws ReflectiveOperationException {
        var field = CampaignSelectScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void put(Map<ResourceLocation, Resource> files, ResourceLocation id, byte[] bytes) throws java.io.IOException {
        var source = Minecraft.getInstance().getResourceManager().getResourceOrThrow(WorldCampaign.CARD_FALLBACK).source();
        files.put(id, new Resource(source, () -> new ByteArrayInputStream(bytes)));
    }

    private static final class ReloadSource implements Supplier<CloseableResourceManager> {
        private final Map<ResourceLocation, Resource> files;
        private int opens;
        private int closes;

        private ReloadSource(Map<ResourceLocation, Resource> files) { this.files = files; }

        public CloseableResourceManager get() {
            opens++;
            return new Images(Map.copyOf(files), () -> closes++);
        }
    }

    static void markdown() {
        var blocks = CampaignDescription.parse("# Heading\n## Smaller\n### Third\n**bold** *italic* ~~strike~~ `code` ***both***\n"
                + "![Preview](preview.gif)\n![Logo](test:textures/logo.png)\n\\*literal\\*\nPlain description");
        require(blocks.get(0).text().getString().equals("Heading") && blocks.get(0).scale() == 2, "Markdown heading");
        require(blocks.get(1).scale() == 1.5F && blocks.get(2).scale() == 1.25F, "Markdown heading hierarchy");
        var styled = blocks.get(3).text();
        require(styled.getString().equals("bold italic strike code both"), "Inline Markdown has no visible delimiters");
        require(style(styled, "bold").isBold() && style(styled, "italic").isItalic(), "Bold and italic text");
        require(style(styled, "strike").isStrikethrough(), "Strikethrough text");
        require(style(styled, "both").isBold() && style(styled, "both").isItalic(), "Combined bold and italic");
        require(blocks.get(4).image().equals("preview.gif") && blocks.get(4).alt().equals("Preview"), "Image alt text and destination");
        require(blocks.get(6).text().getString().equals("*literal*"), "Escaped Markdown");
        var campaign = ResourceLocation.parse("test:card");
        require(CampaignDescription.imageLocation(campaign, "preview.gif").toString().equals("test:world_campaigns/card/preview.gif"), "Relative campaign images");
        require(CampaignDescription.imageLocation(campaign, "test:textures/logo.png").toString().equals("test:textures/logo.png"), "Namespaced images");
        for (String invalid : List.of("../outside.png", "/outside.png", "https://example.com/picture.png", "C:\\outside.png")) {
            require(CampaignDescription.imageLocation(campaign, invalid) == null, "Unsupported image path: " + invalid);
        }
        require(CampaignDescription.parse("**unclosed").getFirst().text().getString().equals("**unclosed"), "Unclosed styling stays readable");
        colors();
    }

    private static void colors() {
        var warning = CampaignDescription.parse("### Choosing this option will §cPERMANENTLY LOCK§r this world into the Kingdoms Campaign.").getFirst();
        require(warning.scale() == 1.25F && !warning.text().getString().contains("§"), "Legacy codes are parsed before heading layout");
        var red = style(warning.text(), "PERMANENTLY LOCK");
        var reset = style(warning.text(), " this world into the Kingdoms Campaign.");
        require(red.getColor() != null && red.getColor().getValue() == 0xff5555 && red.isBold(), "The warning is red while retaining the heading's bold style");
        require(reset.getColor() == null && reset.isBold(), "Reset restores the surrounding Markdown heading style");
        StringBuilder wrapped = new StringBuilder();
        for (var line : Minecraft.getInstance().font.split(warning.text(), 105)) {
            line.accept((index, style, codepoint) -> {
                if (style.getColor() != null && style.getColor().getValue() == 0xff5555) {
                    require(style.isBold(), "Wrapped colored heading glyphs remain bold");
                    wrapped.appendCodePoint(codepoint);
                }
                return true;
            });
        }
        require(wrapped.toString().replace(" ", "").equals("PERMANENTLYLOCK"), "Red formatting survives Minecraft's actual font wrapping");
        var nested = CampaignDescription.parse("§cplain **bold** after§r normal").getFirst().text();
        require(style(nested, "bold").getColor().getValue() == 0xff5555 && style(nested, "bold").isBold(), "Legacy colors combine with bold Markdown");
        require(style(nested, " after").getColor().getValue() == 0xff5555 && !style(nested, " after").isBold(), "Colors persist beyond Markdown spans without leaking bold");
        require(style(nested, " normal").getColor() == null, "Reset clears the active legacy color");
        var changed = CampaignDescription.parse("**red §agreen** still green§r normal").getFirst().text();
        require(style(changed, "green").getColor().getValue() == 0x55ff55 && style(changed, " still green").getColor().getValue() == 0x55ff55,
                "Color changes inside Markdown remain active after the closing delimiter");
        for (char code : "0123456789abcdefC".toCharArray()) {
            var text = CampaignDescription.parse("§" + code + "colored§r reset").getFirst().text();
            require(style(text, "colored").getColor().getValue() == net.minecraft.ChatFormatting.getByCode(Character.toLowerCase(code)).getColor(), "Minecraft color code " + code);
            require(style(text, " reset").getColor() == null, "Color reset after code " + code);
        }
        var decorated = CampaignDescription.parse("§lbold§oitalic§nunderline§mstrike§kobfuscated§r plain").getFirst().text();
        require(style(decorated, "bold").isBold() && style(decorated, "italic").isBold() && style(decorated, "italic").isItalic(), "Legacy formatting codes accumulate");
        require(style(decorated, "underline").isUnderlined() && style(decorated, "strike").isStrikethrough()
                && style(decorated, "obfuscated").isObfuscated(), "Legacy underline, strike, and obfuscated formatting");
        require(!style(decorated, " plain").isBold() && !style(decorated, " plain").isItalic() && !style(decorated, " plain").isObfuscated(), "Reset clears legacy decorations");
        var recolored = CampaignDescription.parse("### §l§o§ctext§r end").getFirst().text();
        require(style(recolored, "text").isBold() && !style(recolored, "text").isItalic(), "Color changes clear legacy decorations while preserving Markdown bold");
        require(CampaignDescription.parse("Unknown §z and trailing §").getFirst().text().getString().equals("Unknown §z and trailing §"), "Unknown and incomplete legacy codes remain literal");
    }

    private static Style style(net.minecraft.network.chat.Component text, String match) {
        return text.visit((style, value) -> value.equals(match) ? Optional.of(style) : Optional.empty(), Style.EMPTY).orElseThrow();
    }

    private static void media() throws Exception {
        for (var asset : Map.of("campaign_select_screen_card", 128, "campaign_select_screen_card_expanded", 374,
                "campaign_select_screen_card_fallback", 128, "campaign_select_screen_card_caret_expand", 7,
                "campaign_select_screen_card_caret_expanded", 7).entrySet()) {
            String path = "/assets/villagerretaliation/textures/gui/campaign_select_screen/" + asset.getKey() + ".png";
            try (var input = CampaignCardClientChecks.class.getResourceAsStream(path)) {
                var image = ImageIO.read(input);
                require(image.getWidth() == asset.getValue() && image.getHeight() == (asset.getValue() == 7 ? 10 : 192),
                        "Native texture dimensions: " + asset.getKey());
            }
        }
        var source = Minecraft.getInstance().getResourceManager().getResourceOrThrow(WorldCampaign.CARD_FALLBACK).source();
        for (String disposal : List.of("restoreToPrevious", "restoreToBackgroundColor")) {
            byte[] bytes = gif(disposal);
            var id = ResourceLocation.parse("test:preview.gif");
            try (var media = CampaignMedia.load(new Images(Map.of(id, new Resource(source, () -> new ByteArrayInputStream(bytes)))), id)) {
                require(media.available() && media.height(4) == 4, "Animated GIF decodes");
                var field = CampaignMedia.class.getDeclaredField("frames");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                var frames = (List<NativeImage>) field.get(media);
                require(frames.size() == 3, "GIF retains all animation frames");
                require(frames.get(0).getPixelRGBA(0, 0) == 0xff0000ff, "GIF red channel");
                require(frames.get(0).getPixelRGBA(3, 3) == 0, "GIF transparency");
                require(frames.get(1).getPixelRGBA(2, 2) == 0xffff0000, "GIF frame offsets and blue channel");
                require(frames.get(2).getPixelRGBA(2, 2) == 0 && frames.get(2).getPixelRGBA(0, 0) == 0xff0000ff,
                        "GIF disposal preserves previous content and clears the disposed frame");
                require(frames.get(2).getPixelRGBA(1, 0) == 0xff00ff00, "GIF green channel");
            }
        }
        try (var fallback = CampaignMedia.load(ResourceManager.Empty.INSTANCE, WorldCampaign.CARD_FALLBACK)) {
            require(fallback.available(), "Fallback can load from client assets without datapack media");
            var field = CampaignMedia.class.getDeclaredField("frames");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var frames = (List<NativeImage>) field.get(fallback);
            require(frames.getFirst().getPixelRGBA(0, 0) == 0 && frames.getFirst().getPixelRGBA(4, 4) >>> 24 == 255,
                    "Fallback retains its transparent four-pixel border");
        }
    }

    private static byte[] cover() throws Exception {
        return cover(0xffff0000);
    }

    static byte[] cover(int color) throws Exception {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        image.setRGB(1, 0, 0x4000ff00);
        image.setRGB(2, 0, 0xffffffff);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    static void comingSoon(CampaignSelectScreen screen) throws Exception {
        var imagesField = CampaignSelectScreen.class.getDeclaredField("images");
        imagesField.setAccessible(true);
        var images = (Map<?, ?>) imagesField.get(screen);
        var media = (CampaignMedia) images.get(ResourceLocation.parse("test:shared_cover.png"));
        var colorField = CampaignMedia.class.getDeclaredField("dynamic");
        colorField.setAccessible(true);
        var mutedField = CampaignMedia.class.getDeclaredField("mutedDynamic");
        mutedField.setAccessible(true);
        var color = ((net.minecraft.client.renderer.texture.DynamicTexture) colorField.get(media)).getPixels();
        var muted = ((net.minecraft.client.renderer.texture.DynamicTexture) mutedField.get(media)).getPixels();
        require(color.getPixelRGBA(0, 0) == 0xff0000ff, "Available campaigns keep the original cover colors when sharing art with coming-soon campaigns");
        int red = muted.getPixelRGBA(0, 0);
        require((red & 255) == (red >> 8 & 255) && (red & 255) == (red >> 16 & 255), "Coming-soon covers render in grayscale");
        require(muted.getPixelRGBA(2, 0) == 0xffcccccc, "Coming-soon cover brightness is reduced to 80 percent");
        require(muted.getPixelRGBA(1, 0) >>> 24 == 64 && muted.getPixelRGBA(3, 3) == 0, "Grayscale covers preserve partial and full transparency");
        require(color.getPixelRGBA(1, 0) >>> 24 == 64, "Color covers preserve their alpha independently");
        var label = Minecraft.getInstance().font.split(Component.translatable("screen.villagerretaliation.campaign.coming_soon_label"), 114);
        require(label.size() == 2, "Coming-soon text occupies exactly two lines");
    }

    static byte[] gif(String disposal) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int index = 0; index < 3; index++) {
                int size = index == 0 ? 4 : index == 1 ? 2 : 1;
                var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, index == 0 ? 0xffff0000 : index == 1 ? 0xff0000ff : 0xff00ff00);
                var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
                var root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                var descriptor = (IIOMetadataNode) root.getElementsByTagName("ImageDescriptor").item(0);
                descriptor.setAttribute("imageLeftPosition", index == 1 ? "2" : index == 2 ? "1" : "0");
                descriptor.setAttribute("imageTopPosition", index == 1 ? "2" : "0");
                var control = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("disposalMethod", index == 1 ? disposal : "none");
                control.setAttribute("delayTime", "12");
                metadata.setFromTree("javax_imageio_gif_image_1.0", root);
                writer.writeToSequence(new IIOImage(image, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }


    static Screen alphaScreen(CampaignSelectScreen screen) {
        return new Screen(Component.literal("Campaign alpha verification")) {
            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                graphics.fill(0, 0, width, height, 0xffc85028);
                screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .forEach(button -> {
                            boolean campaign = button.getMessage().getString().equals("Campaign 1");
                            button.render(graphics, campaign ? button.getX() + 8 : -1, campaign ? button.getY() + 178 : -1, partialTick);
                        });
            }
        };
    }

    static void verifyAlpha(CampaignSelectScreen screen) throws Exception {
        var card = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Campaign 1")).findFirst().orElseThrow();
        var tooltipField = card.getClass().getDeclaredField("authorTooltip");
        tooltipField.setAccessible(true);
        require(card.getTooltip() == tooltipField.get(card), "Hovering the author line shows the full attribution tooltip");
        require(card.getWidth() == 374 && card.getHeight() == 192, "Expanded frame uses its native 374x192 GUI size");
        var minecraft = Minecraft.getInstance();
        double scale = minecraft.getWindow().getGuiScale();
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int x = (int) ((card.getX() + 360) * scale);
            int y = (int) ((card.getY() + 30) * scale);
            int pixel = image.getPixelRGBA(x, y);
            var texture = ResourceLocation.parse("villagerretaliation:textures/gui/campaign_select_screen/campaign_select_screen_card_expanded.png");
            try (var input = minecraft.getResourceManager().getResourceOrThrow(texture).open()) {
                int source = ImageIO.read(input).getRGB(360, 30);
                int alpha = source >>> 24;
                int red = ((source >> 16 & 255) * alpha + 200 * (255 - alpha)) / 255;
                int green = ((source >> 8 & 255) * alpha + 80 * (255 - alpha)) / 255;
                int blue = ((source & 255) * alpha + 40 * (255 - alpha)) / 255;
                require(Math.abs((pixel & 255) - red) <= 1 && Math.abs((pixel >> 8 & 255) - green) <= 1
                        && Math.abs((pixel >> 16 & 255) - blue) <= 1, "Expanded frame blends its texture alpha against the backdrop: " + Integer.toHexString(pixel));
            }
        }
    }

    static void authors(Screen screen) throws Exception {
        var fit = CampaignSelectScreen.class.getDeclaredMethod("fitAuthors", List.class);
        fit.setAccessible(true);
        require(fit.invoke(screen, List.of("A", "B")).equals("A, B"), "Fitting author lists have no ellipsis");
        require(fit.invoke(screen, List.of("A", "B", "Long author ".repeat(10))).equals("A, B..."), "Multiple complete author names fit before ellipsis");
        require(fit.invoke(screen, List.of("Long author ".repeat(10), "B")).equals("..."), "Oversized first author does not leave a partial name");
        require(fit.invoke(screen, List.of("Solo")).equals("Solo"), "Single author label stays intact");
        var card = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Campaign 1")).findFirst().orElseThrow();
        var textField = card.getClass().getDeclaredField("authorText");
        textField.setAccessible(true);
        String label = (String) textField.get(card);
        require(label.equals("Author One..."), "Overflow lists retain whole fitting authors followed by ellipsis: " + label);
        var tooltipField = card.getClass().getDeclaredField("authorTooltip");
        tooltipField.setAccessible(true);
        var tooltip = (net.minecraft.client.gui.components.Tooltip) tooltipField.get(card);
        StringBuilder full = new StringBuilder();
        for (var line : tooltip.toCharSequence(Minecraft.getInstance())) {
            line.accept((index, style, codepoint) -> { full.appendCodePoint(codepoint); return true; });
        }
        require(full.toString().contains("Author One, Author Two"), "Author tooltip retains every attributed author");
    }

    static double scroll(CampaignSelectScreen screen) throws Exception {
        var field = CampaignSelectScreen.class.getDeclaredField("descriptionScroll");
        field.setAccessible(true);
        return field.getDouble(screen);
    }

    static long cards(Screen screen) {
        return screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).filter(button -> button.getMessage().getString().startsWith("Campaign ")).count();
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Images(Map<ResourceLocation, Resource> images, Runnable onClose) implements CloseableResourceManager {
        private Images(Map<ResourceLocation, Resource> images) { this(images, () -> { }); }
        public void close() { onClose.run(); }
        public Set<String> getNamespaces() { return Set.of("test"); }
        public Optional<Resource> getResource(ResourceLocation id) { return Optional.ofNullable(images.get(id)); }
        public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).stream().toList(); }
        public Map<ResourceLocation, Resource> listResources(String root, Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, Resource> result = new LinkedHashMap<>();
            images.forEach((id, resource) -> {
                if (id.getPath().startsWith(root + "/") && filter.test(id)) result.put(id, resource);
            });
            return result;
        }
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String root, Predicate<ResourceLocation> filter) { return Map.of(); }
        public Stream<PackResources> listPacks() { return Stream.empty(); }
    }
}
