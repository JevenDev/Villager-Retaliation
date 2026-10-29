package com.jvn.villagerretaliation.client.settlement;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

public final class SettlementViewPreferences {
    private final Path path;
    private boolean readable = true;
    public record WindowPosition(int left, int top, int width, int height) {
        public WindowPosition(int left, int top) {
            this(left, top, 0, 0);
        }
    }

    public WindowPosition sidebarWindow;
    public WindowPosition mapWindow;
    public boolean sidebar = true;
    public boolean map = true;
    public boolean hints = true;
    public boolean workOverlay;
    public boolean issueOverlay;
    public float height = 32;
    public float yaw = 45;
    public List<List<UUID>> groups = java.util.Collections.nCopies(9, List.of());

    public SettlementViewPreferences(Path path) {
        this.path = path;
        if (!Files.exists(path)) return;
        try (var reader = Files.newBufferedReader(path)) {
            var values = new Properties();
            values.load(reader);
            if (!"1".equals(values.getProperty("version"))) throw new IllegalArgumentException("Unsupported view preferences");
            boolean sidebar = bool(values, "sidebar", true);
            boolean map = bool(values, "map", true);
            boolean hints = bool(values, "hints", true);
            boolean work = bool(values, "work", false);
            boolean issues = bool(values, "issues", false);
            float height = Float.parseFloat(values.getProperty("height", "32"));
            float yaw = Float.parseFloat(values.getProperty("yaw", "45"));
            if (!Float.isFinite(height) || !Float.isFinite(yaw)) throw new IllegalArgumentException("Invalid camera preferences");
            List<List<UUID>> groups = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                String group = values.getProperty("group." + i, "");
                var ids = group.isBlank() ? List.<UUID>of() : java.util.Arrays.stream(group.split(",")).map(UUID::fromString).distinct().toList();
                if (ids.size() > SettlementViewSelection.LIMIT) throw new IllegalArgumentException("Oversized control group");
                groups.add(ids);
            }
            this.sidebarWindow = position(values, "window.sidebar");
            this.mapWindow = position(values, "window.map");
            this.sidebar = sidebar;
            this.map = map;
            this.hints = hints;
            this.workOverlay = work;
            this.issueOverlay = issues;
            this.height = Math.clamp(height, SettlementViewWindow.MIN_HEIGHT, SettlementViewWindow.MAX_HEIGHT);
            this.yaw = (yaw % 360 + 360) % 360;
            this.groups = List.copyOf(groups);
        } catch (IOException | IllegalArgumentException exception) {
            this.readable = false;
            LogUtils.getLogger().warn("Failed to read settlement view preferences from {}", path, exception);
        }
    }

    public static SettlementViewPreferences forSettlement(UUID settlement) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        String world = server != null ? server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString()
                : minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : "unknown";
        String identity = world + "|" + minecraft.getUser().getProfileId() + "|"
                + minecraft.level.dimension().location() + "|" + settlement;
        UUID key = UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new SettlementViewPreferences(FMLPaths.CONFIGDIR.get().resolve("villagerretaliation-views").resolve(key + ".properties"));
    }

    private static WindowPosition position(Properties values, String key) {
        String left = values.getProperty(key + ".left");
        String top = values.getProperty(key + ".top");
        if (left == null && top == null) return null;
        if (left == null || top == null) throw new IllegalArgumentException("Incomplete window position");
        String width = values.getProperty(key + ".width");
        String height = values.getProperty(key + ".height");
        if (width == null && height == null) return new WindowPosition(Integer.parseInt(left), Integer.parseInt(top));
        if (width == null || height == null) throw new IllegalArgumentException("Incomplete window size");
        int parsedWidth = Integer.parseInt(width), parsedHeight = Integer.parseInt(height);
        if (parsedWidth <= 0 || parsedHeight <= 0) throw new IllegalArgumentException("Invalid window size");
        return new WindowPosition(Integer.parseInt(left), Integer.parseInt(top), parsedWidth, parsedHeight);
    }

    private static void position(Properties values, String key, WindowPosition position) {
        if (position == null) return;
        values.setProperty(key + ".left", Integer.toString(position.left()));
        values.setProperty(key + ".top", Integer.toString(position.top()));
        if (position.width() > 0 && position.height() > 0) {
            values.setProperty(key + ".width", Integer.toString(position.width()));
            values.setProperty(key + ".height", Integer.toString(position.height()));
        }
    }

    private static boolean bool(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key, Boolean.toString(fallback));
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid visibility preference");
        return Boolean.parseBoolean(value);
    }

    public void save() {
        if (!this.readable) return;
        var values = new Properties();
        values.setProperty("version", "1");
        position(values, "window.sidebar", this.sidebarWindow);
        position(values, "window.map", this.mapWindow);
        values.setProperty("sidebar", Boolean.toString(this.sidebar));
        values.setProperty("map", Boolean.toString(this.map));
        values.setProperty("hints", Boolean.toString(this.hints));
        values.setProperty("work", Boolean.toString(this.workOverlay));
        values.setProperty("issues", Boolean.toString(this.issueOverlay));
        values.setProperty("height", Float.toString(this.height));
        values.setProperty("yaw", Float.toString(this.yaw));
        for (int i = 0; i < 9; i++) values.setProperty("group." + i,
                this.groups.get(i).stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")));
        try {
            Files.createDirectories(this.path.getParent());
            Path temporary = Files.createTempFile(this.path.getParent(), "view-", ".tmp");
            try {
                try (var writer = Files.newBufferedWriter(temporary)) {
                    values.store(writer, "Settlement view preferences");
                }
                Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            LogUtils.getLogger().warn("Failed to save settlement view preferences to {}", this.path, exception);
        }
    }
}
