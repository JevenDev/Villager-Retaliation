package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadata;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.w3c.dom.Node;

final class CampaignMedia implements AutoCloseable {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final long MAX_PIXELS = 16 * 1024 * 1024;
    private final byte[] bytes;
    private final ResourceLocation source;
    private final List<NativeImage> frames = new ArrayList<>();
    private final List<NativeImage> mutedFrames = new ArrayList<>();
    private final List<Integer> durations = new ArrayList<>();
    private ResourceLocation texture;
    private DynamicTexture dynamic;
    private ResourceLocation mutedTexture;
    private DynamicTexture mutedDynamic;
    private int mutedFrame = -1;
    private boolean failed;
    private int width;
    private int height;
    private int currentFrame = -1;
    private int duration;
    private long started;

    private CampaignMedia(ResourceLocation source, byte[] bytes) {
        this.source = source;
        this.bytes = bytes;
    }

    static CampaignMedia load(ResourceManager resources, ResourceLocation id) {
        try (var stream = WorldCampaign.MOD_ICON.equals(id)
                ? CampaignMedia.class.getResourceAsStream("/villager_retaliation_cover.png")
                : resources.getResource(id).isPresent() ? resources.getResourceOrThrow(id).open()
                : Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open()) {
            if (stream == null) throw new IOException("Campaign image is missing");
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Campaign image exceeds 4 MiB");
            return new CampaignMedia(id, bytes);
        } catch (IOException exception) {
            LogUtils.getLogger().warn("Could not load campaign image {}", id, exception);
            return new CampaignMedia(id, new byte[0]);
        }
    }

    boolean available() {
        if (frames.isEmpty() && !failed) decode();
        return !frames.isEmpty();
    }

    void prepare(boolean muted) {
        if (!available() || !muted || !mutedFrames.isEmpty()) return;
        for (var frame : frames) {
            NativeImage pixels = new NativeImage(width, height, false);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    int rgba = frame.getPixelRGBA(column, row);
                    int gray = ((rgba & 255) * 54 + (rgba >> 8 & 255) * 183 + (rgba >> 16 & 255) * 19) / 256;
                    gray = gray * 4 / 5;
                    pixels.setPixelRGBA(column, row, (rgba & 0xff000000) | gray | gray << 8 | gray << 16);
                }
            }
            mutedFrames.add(pixels);
        }
    }

    int height(int availableWidth) {
        return Math.max(1, Math.round(height * Math.min(1, availableWidth / (float) width)));
    }

    void render(GuiGraphics graphics, int x, int y, int availableWidth, int availableHeight) {
        render(graphics, x, y, availableWidth, availableHeight, false);
    }

    void render(GuiGraphics graphics, int x, int y, int availableWidth, int availableHeight, boolean muted) {
        if (!available()) return;
        if (texture == null && mutedTexture == null) started = Util.getMillis();
        DynamicTexture target = muted ? mutedDynamic : dynamic;
        if (target == null) {
            target = new DynamicTexture(width, height, false);
            ResourceLocation id = Minecraft.getInstance().getTextureManager().register("world_campaign", target);
            if (muted) {
                mutedTexture = id;
                mutedDynamic = target;
            } else {
                texture = id;
                dynamic = target;
            }
        }
        long time = (Util.getMillis() - started) % duration;
        int frame = 0;
        while (frame + 1 < frames.size() && time >= durations.get(frame)) time -= durations.get(frame++);
        if (frame != (muted ? mutedFrame : currentFrame)) {
            NativeImage pixels = target.getPixels();
            prepare(muted);
            pixels.copyFrom((muted ? mutedFrames : frames).get(frame));
            if (muted) mutedFrame = frame;
            else currentFrame = frame;
            target.upload();
        }
        float scale = Math.min(availableWidth / (float) width, availableHeight / (float) height);
        int w = Math.max(1, Math.round(width * scale));
        int h = Math.max(1, Math.round(height * scale));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(muted ? mutedTexture : texture, x + (availableWidth - w) / 2, y + (availableHeight - h) / 2, w, h, 0, 0, width, height, width, height);
        RenderSystem.disableBlend();
    }

    private void decode() {
        if (bytes.length == 0) {
            failed = true;
            return;
        }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported campaign image format");
            var reader = readers.next();
            try {
                reader.setInput(input);
                boolean gif = reader.getFormatName().equalsIgnoreCase("gif");
                width = reader.getWidth(0);
                height = reader.getHeight(0);
                if (gif) {
                    Node logical = node(reader.getStreamMetadata(), "javax_imageio_gif_stream_1.0", "LogicalScreenDescriptor");
                    width = number(logical, "logicalScreenWidth", width);
                    height = number(logical, "logicalScreenHeight", height);
                }
                int count = gif ? reader.getNumImages(true) : 1;
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || count < 1 || count > 128
                        || (long) width * height * count > MAX_PIXELS) throw new IOException("Campaign image dimensions or animation exceed limits");
                BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                for (int index = 0; index < count; index++) {
                    IIOMetadata metadata = gif ? reader.getImageMetadata(index) : null;
                    Node descriptor = node(metadata, "javax_imageio_gif_image_1.0", "ImageDescriptor");
                    Node control = node(metadata, "javax_imageio_gif_image_1.0", "GraphicControlExtension");
                    int x = number(descriptor, "imageLeftPosition", 0);
                    int y = number(descriptor, "imageTopPosition", 0);
                    String disposal = attribute(control, "disposalMethod", "none");
                    BufferedImage previous = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
                    BufferedImage frame = reader.read(index);
                    var painter = canvas.createGraphics();
                    painter.drawImage(frame, x, y, null);
                    painter.dispose();
                    NativeImage pixels = new NativeImage(width, height, false);
                    for (int row = 0; row < height; row++) {
                        for (int column = 0; column < width; column++) {
                            int argb = canvas.getRGB(column, row);
                            pixels.setPixelRGBA(column, row, (argb & 0xff00ff00) | (argb & 255) << 16 | (argb >> 16 & 255));
                        }
                    }
                    frames.add(pixels);
                    int delay = Math.max(20, number(control, "delayTime", 10) * 10);
                    durations.add(delay);
                    duration += delay;
                    if (previous != null) canvas = previous;
                    else if (disposal.equals("restoreToBackgroundColor")) {
                        painter = canvas.createGraphics();
                        painter.setComposite(AlphaComposite.Clear);
                        painter.fillRect(x, y, frame.getWidth(), frame.getHeight());
                        painter.dispose();
                    }
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            frames.forEach(NativeImage::close);
            frames.clear();
            durations.clear();
            duration = 0;
            failed = true;
            LogUtils.getLogger().warn("Invalid campaign image {}", source, exception);
        }
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var painter = result.createGraphics();
        painter.drawImage(image, 0, 0, null);
        painter.dispose();
        return result;
    }

    private static Node node(IIOMetadata metadata, String format, String name) {
        if (metadata == null) return null;
        Node root = metadata.getAsTree(format);
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals(name)) return child;
        }
        return null;
    }

    private static String attribute(Node node, String name, String fallback) {
        Node value = node == null ? null : node.getAttributes().getNamedItem(name);
        return value == null ? fallback : value.getNodeValue();
    }

    private static int number(Node node, String name, int fallback) {
        return Integer.parseInt(attribute(node, name, Integer.toString(fallback)));
    }

    @Override
    public void close() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
        if (mutedTexture != null) Minecraft.getInstance().getTextureManager().release(mutedTexture);
        frames.forEach(NativeImage::close);
        frames.clear();
        mutedFrames.forEach(NativeImage::close);
        mutedFrames.clear();
        durations.clear();
        duration = 0;
        currentFrame = -1;
        texture = null;
        dynamic = null;
        mutedTexture = null;
        mutedDynamic = null;
        mutedFrame = -1;
    }
}
