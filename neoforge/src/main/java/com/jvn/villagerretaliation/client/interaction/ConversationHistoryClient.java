package com.jvn.villagerretaliation.client.interaction;

import com.google.gson.JsonParseException;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.network.ConversationHistoryWorldPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class ConversationHistoryClient {
    private static ConversationHistory history = new ConversationHistory();
    private static UUID world;
    private static Path file;
    private static boolean dirty;
    private static boolean writable = true;
    private static long lastSave;
    public static final KeyMapping OPEN_HISTORY = new KeyMapping(
            "key.villagerretaliation.open_conversation_history", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, "key.categories.villagerretaliation");

    private ConversationHistoryClient() { }

    public static void accept(ConversationHistoryWorldPayload payload) {
        if (payload.world().equals(world)) return;
        flush();
        world = payload.world();
        history = new ConversationHistory();
        file = null;
        dirty = false;
        writable = true;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        file = minecraft.gameDirectory.toPath().resolve("villagerretaliation/conversation-history")
                .resolve(world.toString()).resolve(minecraft.player.getUUID() + ".nbt");
        if (!Files.exists(file)) return;
        try {
            history = ConversationHistory.load(NbtIo.readCompressed(file, NbtAccounter.create(16L * 1024 * 1024)));
        } catch (IOException | IllegalArgumentException | JsonParseException exception) {
            writable = false;
            LogUtils.getLogger().warn("Could not read conversation history {}", file, exception);
        }
    }

    public static void record(String key, Entity speaker, String name, String text, List<DialogueTextSegment> segments) {
        if (world == null) return;
        var resolved = segments == null || segments.isEmpty() ? DialogueTextSegment.parse(text, DialogueTextEffects.NONE) : segments;
        var portrait = new CompoundTag();
        if (speaker != null && !(speaker instanceof Player)) {
            speaker.save(portrait);
            for (String field : List.of("Brain", "Offers", "Inventory", "Passengers", "Leash")) portrait.remove(field);
        }
        if (speaker instanceof Player player) portrait.putString("ProfileName", player.getGameProfile().getName());
        if (name == null || name.isBlank()) name = speaker == null
                ? Component.translatable("villagerretaliation.gui.dialogue.narrator").getString() : speaker.getName().getString();
        dirty |= history.add(new ConversationHistory.Entry(key, speaker == null ? null : speaker.getUUID(), name,
                speaker instanceof Player, portrait, resolved));
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_HISTORY);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        while (OPEN_HISTORY.consumeClick()) {
            if (minecraft.level != null && minecraft.player != null && minecraft.screen == null)
                minecraft.setScreen(new ConversationHistoryScreen(null));
        }
        long now = Util.getMillis();
        if (dirty && now - lastSave >= 10000) {
            flush();
            lastSave = now;
        }
    }

    public static List<ConversationHistory.Entry> entries() { return history.entries(); }

    public static void flush() {
        if (!dirty || !writable || file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "history-", ".nbt");
            try {
                NbtIo.writeCompressed(history.save(), temporary);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                dirty = false;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            LogUtils.getLogger().warn("Could not save conversation history {}", file, exception);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        flush();
        history = new ConversationHistory();
        world = null;
        file = null;
        dirty = false;
    }

    @SubscribeEvent
    public static void onPauseMenu(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen) || !screen.showsPauseMenu()
                || Minecraft.getInstance().level == null) return;
        var options = event.getListenersList().stream().filter(widget -> widget instanceof Button button
                && button.getMessage().equals(Component.translatable("menu.options"))).findFirst();
        if (options.orElse(null) instanceof Button button) {
            event.addListener(new HistoryButton(button.getX() - 24, button.getY(), screen));
        }
    }

    private static final class HistoryButton extends Button {
        private final ItemStack book = new ItemStack(Items.BOOK);

        private HistoryButton(int x, int y, PauseScreen parent) {
            super(x, y, 20, 20, Component.translatable("villagerretaliation.gui.conversation_history.title"),
                    button -> Minecraft.getInstance().setScreen(new ConversationHistoryScreen(parent)), DEFAULT_NARRATION);
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.renderItem(book, getX() + 2, getY() + 2);
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) { }
    }
}
