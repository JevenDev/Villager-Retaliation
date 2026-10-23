package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.network.ConversationHistoryWorldPayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

final class ConversationHistoryClientChecks {
    private static PauseScreen parent;
    private static float playerYaw;
    private static float playerHead;
    private static int guiScale;

    private ConversationHistoryClientChecks() { }

    static void run(Entity npc) {
        var minecraft = Minecraft.getInstance();
        require(ConversationHistoryClient.OPEN_HISTORY.isUnbound(), "history key defaults to unbound");
        var recorded = ConversationHistoryClient.entries();
        require(recorded.stream().anyMatch(entry -> text(entry).contains("Campaign line.")), "campaign dialogue is recorded");
        require(recorded.stream().noneMatch(entry -> text(entry).contains("Earlier line.")), "reviewing old campaign history adds no entries");
        require(recorded.stream().filter(entry -> text(entry).contains("Campaign line.")).count() == 1, "campaign refresh does not duplicate history");
        var styled = DialogueTextSegment.parse("<b><wave>Hello</wave></b><pause=500> again", DialogueTextEffects.NONE);
        var data = new ConversationHistory();
        for (int i = 0; i < 55; i++) data.add(new ConversationHistory.Entry("entry-" + i, npc.getUUID(), "Rowan", false, new CompoundTag(), styled));
        require(data.entries().size() == 50 && data.entries().getFirst().key().equals("entry-5"), "history evicts the oldest lines at fifty");
        require(!data.add(data.entries().getLast()), "repeated presentation is ignored");
        var restored = ConversationHistory.load(data.save());
        require(restored.entries().equals(data.entries()), "history round trip preserves speakers and effects");
        require(restored.entries().getFirst().segments().stream().allMatch(segment -> segment.cues().isEmpty()), "history does not retain performance cues");
        int before = ConversationHistoryClient.entries().size();
        VillagerInteractionClientHandler.acceptNotice(new VillagerInteractionNoticePayload(npc.getId(), "A chat-only reply.", "Rowan"));
        require(ConversationHistoryClient.entries().size() == before + 1, "chat-only dialogue is recorded");
        for (int i = 0; i < 50; i++) ConversationHistoryClient.record("older-" + i, npc, "Rowan", "Earlier conversation " + i, List.of());
        ConversationHistoryClient.record("farmer", npc, "Rowan • Farmer",
                "The <wave>wheat</wave> is ready, but I could use another pair of hands.\n\nHarvest the north field and bring me twelve bundles of wheat.\n\nYou can find the field just beyond the windmill.", List.of());
        ConversationHistoryClient.record("player", minecraft.player, "You",
                "All right. I will take care of it.\n\nWill you be here when I return?", List.of());
        ConversationHistoryClient.record("reply", npc, "Rowan • Farmer", "I will be at the <rainbow>market</rainbow> until sundown.", List.of());
        UUID world = (UUID) field(ConversationHistoryClient.class, "world");
        var expected = ConversationHistoryClient.entries();
        ConversationHistoryClient.flush();
        ConversationHistoryClient.onLogout(null);
        require(ConversationHistoryClient.entries().isEmpty(), "disconnect releases history");
        ConversationHistoryClient.accept(new ConversationHistoryWorldPayload(world));
        require(ConversationHistoryClient.entries().equals(expected), "reconnecting restores persisted world history and portraits");
        ConversationHistoryClient.accept(new ConversationHistoryWorldPayload(UUID.randomUUID()));
        require(ConversationHistoryClient.entries().isEmpty(), "another world has separate history");
        ConversationHistoryClient.accept(new ConversationHistoryWorldPayload(world));
        require(ConversationHistoryClient.entries().equals(expected), "returning to the original world restores its history");
        var key = com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H);
        try {
            ConversationHistoryClient.OPEN_HISTORY.setKey(key);
            net.minecraft.client.KeyMapping.resetMapping();
            net.minecraft.client.KeyMapping.click(key);
            ConversationHistoryClient.onTick(null);
            require(minecraft.screen instanceof ConversationHistoryScreen, "a bound key opens history during gameplay");
            minecraft.screen.onClose();
            require(minecraft.screen == null, "key-opened history returns to gameplay");
        } finally {
            ConversationHistoryClient.OPEN_HISTORY.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            net.minecraft.client.KeyMapping.resetMapping();
        }
        parent = new PauseScreen(true);
        minecraft.setScreen(parent);
        var options = parent.children().stream().filter(widget -> widget instanceof Button button
                && button.getMessage().equals(Component.translatable("menu.options"))).map(Button.class::cast).findFirst().orElseThrow();
        var book = parent.children().stream().filter(widget -> widget instanceof Button button
                && button.getMessage().equals(Component.translatable("villagerretaliation.gui.conversation_history.title")))
                .map(Button.class::cast).findFirst().orElseThrow();
        require(book.getX() == options.getX() - 24 && book.getY() == options.getY() && book.getWidth() == 20,
                "vanilla square history button sits left of Options");
        book.onPress();
        require(minecraft.screen instanceof ConversationHistoryScreen, "pause-menu book opens history");
        var screen = (ConversationHistoryScreen) minecraft.screen;
        int left = (int) field(screen, "left");
        int right = (int) field(screen, "right");
        require(right - left == VillagerInteractionScreen.INTERACTION_CONTAINER_WIDTH, "history respects the interaction HUD width");
        require(Math.abs((left + right) / 2.0 - screen.width / 2.0) <= 0.5, "portrait and dialogue block is centered");
        double end = (double) field(screen, "scroll");
        require(end > 0 && screen.isPauseScreen(), "history opens at newest entries and preserves pause");
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        require((double) field(screen, "scroll") == 0, "Home scrolls to oldest entry");
        screen.mouseScrolled(0, 0, 0, -1);
        require((double) field(screen, "scroll") > 0, "mouse wheel scrolls history");
        screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        require((double) field(screen, "scroll") == end, "End scrolls to newest entry");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(minecraft.screen == parent, "Escape returns to the pause menu");
        minecraft.setScreen(new ConversationHistoryScreen(parent));
        playerYaw = minecraft.player.getYRot();
        playerHead = minecraft.player.yHeadRot;
    }

    static void showPauseMenu() {
        Minecraft.getInstance().screen.onClose();
    }

    static void showSmallScreen() {
        var minecraft = Minecraft.getInstance();
        guiScale = minecraft.options.guiScale().get();
        minecraft.options.guiScale().set(3);
        minecraft.resizeDisplay();
        minecraft.setScreen(new ConversationHistoryScreen(parent));
    }

    static void verifyRendered() {
        var minecraft = Minecraft.getInstance();
        require(minecraft.player.getYRot() == playerYaw && minecraft.player.yHeadRot == playerHead,
                "fixed portrait rendering restores player rotation");
        minecraft.screen.onClose();
        require(minecraft.screen == parent, "history still returns to its parent after rendering");
        minecraft.options.guiScale().set(guiScale);
        minecraft.resizeDisplay();
    }

    private static String text(ConversationHistory.Entry entry) { return DialogueTextSegment.plainText(entry.segments()); }

    private static Object field(Object target, String name) {
        try {
            var type = target instanceof Class<?> value ? value : target.getClass();
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target instanceof Class<?> ? null : target);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
