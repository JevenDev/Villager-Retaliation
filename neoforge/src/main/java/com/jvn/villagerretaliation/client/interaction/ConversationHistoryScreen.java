package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

public final class ConversationHistoryScreen extends Screen {
    private static final int TOP = 55;
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int left;
    private int right;
    private int bottom;
    private int contentHeight;
    private double scroll;
    private boolean initialized;
    private boolean draggingScrollbar;

    public ConversationHistoryScreen(Screen parent) {
        super(Component.translatable("villagerretaliation.gui.conversation_history.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        boolean atEnd = !initialized || scroll >= maxScroll();
        ConversationHistoryClient.flush();
        int columnWidth = VillagerInteractionScreen.INTERACTION_CONTAINER_WIDTH;
        left = (width - columnWidth) / 2;
        right = left + columnWidth;
        bottom = height - 16;
        rows.clear();
        contentHeight = 0;
        for (var entry : ConversationHistoryClient.entries()) {
            var segments = VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()
                    ? DialogueTextSegment.plain(DialogueTextSegment.plainText(entry.segments()), DialogueTextEffects.NONE)
                    : entry.segments();
            var lines = new ArrayList<Line>();
            String plain = DialogueTextSegment.plainText(segments);
            int cursor = 0;
            for (String text : VillagerInteractionScreen.wrappedInteractionDialogueLines(font, plain)) {
                int start = plain.indexOf(text, cursor);
                if (start < 0) start = cursor;
                int end = Math.min(plain.length(), start + text.length());
                lines.add(new Line(text, DialogueTextSegment.slice(segments, start, end)));
                cursor = end;
            }
            int rowHeight = Math.max(VillagerInteractionScreen.INTERACTION_CONTAINER_HEIGHT, 36 + lines.size() * font.lineHeight);
            rows.add(new Row(entry, portrait(entry), List.copyOf(lines), contentHeight, rowHeight));
            contentHeight += rowHeight;
        }
        scroll = atEnd ? maxScroll() : Mth.clamp(scroll, 0, maxScroll());
        initialized = true;
    }

    private LivingEntity portrait(ConversationHistory.Entry entry) {
        if (minecraft.level == null) return null;
        if (entry.player() && entry.speaker() != null) {
            var player = minecraft.level.getPlayerByUUID(entry.speaker());
            if (player != null) return player;
            String name = entry.portrait().getString("ProfileName");
            return new RemotePlayer(minecraft.level, new GameProfile(entry.speaker(), name.isBlank() ? "Player" : name));
        }
        if (!entry.portrait().contains("id")) return null;
        return EntityType.create(entry.portrait().copy(), minecraft.level).filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast).orElse(null);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.flush();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.screenLayerZ());
        graphics.pose().pushPose();
        float titleScale = width < 400 ? 1.2F : 1.5F;
        graphics.pose().translate((width - font.width(title) * titleScale) / 2, width < 400 ? 30 : 22, 0);
        graphics.pose().scale(titleScale, titleScale, 1);
        graphics.drawString(font, title, 0, 0, 0xFFFFFFFF);
        graphics.pose().popPose();
        var back = Component.translatable("villagerretaliation.gui.conversation_history.back");
        graphics.drawString(font, back, width - 24 - font.width(back), width < 400 ? 9 : 25, 0xBDBDBD);
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("villagerretaliation.gui.conversation_history.empty"),
                    width / 2, height / 2, 0xBDBDBD);
        } else {
            graphics.enableScissor(left, TOP, right, bottom);
            int tick = (int) (Util.getMillis() / 50);
            for (var row : rows) {
                int y = TOP + row.top() - (int) scroll;
                if (y + row.height() <= TOP || y >= bottom) continue;
                int portraitTop = y + 18;
                int portraitHeight = VillagerInteractionScreen.INTERACTION_PORTRAIT_BOTTOM - VillagerInteractionScreen.INTERACTION_PORTRAIT_TOP;
                int portraitLeft = left + VillagerInteractionScreen.INTERACTION_PORTRAIT_LEFT;
                int portraitRight = left + VillagerInteractionScreen.INTERACTION_PORTRAIT_RIGHT;
                if (row.portrait() != null && portraitTop >= TOP && portraitTop + portraitHeight <= bottom) {
                    graphics.enableScissor(portraitLeft - 1, portraitTop, portraitRight + 1, portraitTop + portraitHeight);
                    try {
                        VillagerInteractionPortraitRenderer.renderBust(graphics, row.portrait(),
                                portraitLeft, portraitTop, portraitRight, portraitTop + portraitHeight);
                    } finally {
                        graphics.disableScissor();
                    }
                }
                int textLeft = left + VillagerInteractionScreen.INTERACTION_DIALOGUE_LEFT + 1;
                int textWidth = VillagerInteractionScreen.INTERACTION_DIALOGUE_EXTENDED_RIGHT - VillagerInteractionScreen.INTERACTION_DIALOGUE_LEFT - 2;
                int color = row.entry().player() ? 0xFF55FFFF : 0xFFFFFF55;
                var name = row.entry().player() && minecraft.player != null && minecraft.player.getUUID().equals(row.entry().speaker())
                        ? Component.translatable("villagerretaliation.gui.conversation_history.you") : Component.literal(row.entry().name());
                graphics.drawString(font, font.plainSubstrByWidth(name.getString(), textWidth), textLeft, y + 8, color);
                int lineY = y + 24;
                for (var line : row.lines()) {
                    if (lineY + font.lineHeight >= TOP && lineY < bottom) {
                        VillagerStyledTextRenderer.renderOutlinedLine(graphics, font, line.text(), line.segments(),
                                textLeft, lineY, 0xFFFFFFFF, tick);
                    }
                    lineY += font.lineHeight;
                }
                if (row != rows.getLast()) graphics.fill(left, y + row.height() - 5, right - 6, y + row.height() - 4, 0x55FFFFFF);
            }
            graphics.disableScissor();
            if (maxScroll() > 0) {
                int thumb = thumbHeight();
                int y = TOP + (int) (scroll / maxScroll() * (bottom - TOP - thumb));
                graphics.fill(right + 3, TOP, right + 7, bottom, 0x66000000);
                graphics.fill(right + 3, y, right + 7, y + thumb, 0xFFA0A0A0);
                graphics.fill(right + 3, y, right + 4, y + thumb - 1, 0xFFC0C0C0);
            }
        }
        VillagerClientUiUtil.popGuiLayer(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int maxScroll() { return Math.max(0, contentHeight - (bottom - TOP)); }
    private int thumbHeight() { return Math.min(bottom - TOP, Math.max(20, (bottom - TOP) * (bottom - TOP) / contentHeight)); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Mth.clamp(scroll - vertical * 26, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && mouseX >= right + 1 && mouseX <= right + 10 && mouseY >= TOP && mouseY <= bottom) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void scrollTo(double mouseY) {
        scroll = Mth.clamp((mouseY - TOP - thumbHeight() / 2.0) / (bottom - TOP - thumbHeight()) * maxScroll(), 0, maxScroll());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) { scrollTo(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll = Math.max(0, scroll - 26);
            case GLFW.GLFW_KEY_DOWN -> scroll = Math.min(maxScroll(), scroll + 26);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll = Math.max(0, scroll - (bottom - TOP));
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.min(maxScroll(), scroll + (bottom - TOP));
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maxScroll();
            default -> { return super.keyPressed(key, scanCode, modifiers); }
        }
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private record Line(String text, List<DialogueTextSegment> segments) { }
    private record Row(ConversationHistory.Entry entry, LivingEntity portrait, List<Line> lines, int top, int height) { }
}
