package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.network.VillagerConversationEndRequestPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload;
import com.jvn.villagerretaliation.network.VillagerTradeRequestPayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class VillagerInteractionScreen extends Screen {
    private static final int RESPONSE_WIDTH = 520;
    private static final int OPTION_WIDTH = 224;
    private static final int OPTION_HEIGHT = 18;
    private static final int OPTION_GAP = 5;
    private static final int OPTION_VIEWPORT_ROWS = 5;
    private static final int OPTION_TEXT_INSET = 10;
    private static final int OPTION_SCROLLBAR_OFFSET = 20;
    private static final int OPTION_SCROLLBAR_WIDTH = 2;
    private static final int DIVIDER_HEIGHT = 92;
    private static final float OPTION_SCROLL_LERP = 0.32F;
    private static final float OPTION_SCROLL_STEP = 12.0F;
    private static final float OPTION_HOVER_SCALE = 0.055F;
    private static final float OPTION_SELECTED_SCALE = 0.02F;
    private static final int INFO_LABEL_COLOR = 0x96E8E4DA;
    private static final int INFO_VALUE_COLOR = 0xFFF8F6EF;
    private static final int INFO_SECONDARY_COLOR = 0xB8D5D0C6;
    private static final int DIVIDER_CORE_COLOR = 0xD8FFFFFF;
    private static final int DIVIDER_GLOW_COLOR = 0x40FFFFFF;

    private final int villagerEntityId;
    private final String villagerName;
    private final String professionName;
    private int reputation;
    private VillagerReputationLevel reputationLevel;
    private final List<DialogueOption> options = new ArrayList<>();
    private DialoguePage page = DialoguePage.ROOT;
    private int selectedOption;
    private boolean closingFromServer;
    private boolean draggingScrollbar;
    private float scrollbarDragOffset;
    private float optionScroll;
    private float targetOptionScroll;

    public VillagerInteractionScreen(int villagerEntityId, String villagerName, String professionName, int reputation, VillagerReputationLevel reputationLevel) {
        super(Component.literal("Villager Interaction"));
        this.villagerEntityId = villagerEntityId;
        this.villagerName = villagerName;
        this.professionName = professionName;
        this.reputation = reputation;
        this.reputationLevel = reputationLevel;
        ClientVillagerConversationState.start(villagerEntityId);
    }

    @Override
    protected void init() {
        rebuildOptions();
    }

    @Override
    public void tick() {
        ClientVillagerConversationState.tickCameraFocus();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public boolean matchesVillager(int entityId) {
        return this.villagerEntityId == entityId;
    }

    public void updateReputation(int reputation, VillagerReputationLevel reputationLevel) {
        this.reputation = reputation;
        this.reputationLevel = reputationLevel;
    }

    public void closeFromServer() {
        this.closingFromServer = true;
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        focusVillagerOnPlayer();
        updateMouseSelection(mouseX, mouseY);
        this.optionScroll = Mth.lerp(OPTION_SCROLL_LERP, this.optionScroll, this.targetOptionScroll);
        if (Math.abs(this.optionScroll - this.targetOptionScroll) < 0.15F) {
            this.optionScroll = this.targetOptionScroll;
        }

        renderConversationFocus(graphics);
        renderOptions(graphics, mouseX, mouseY, optionsTop());
        renderHint(graphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            leaveConversation();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            activateSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ScrollbarThumb scrollbarThumb = scrollbarThumb();
            if (scrollbarThumb != null
                    && mouseX >= scrollbarThumb.left()
                    && mouseX <= scrollbarThumb.right()
                    && mouseY >= scrollbarThumb.top()
                    && mouseY <= scrollbarThumb.bottom()) {
                this.draggingScrollbar = true;
                this.scrollbarDragOffset = (float) mouseY - scrollbarThumb.top();
                return true;
            }
            int hovered = optionAt(mouseX, mouseY);
            if (hovered >= 0) {
                this.selectedOption = hovered;
                ensureSelectedVisible();
                activateSelected();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxOptionScroll() <= 0.0F) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int left = optionsLeft() - 18;
        int right = optionsLeft() + OPTION_WIDTH + 4;
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        if (mouseX < left || mouseX > right || mouseY < top - 4 || mouseY > bottom + 4) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        setTargetOptionScroll(this.targetOptionScroll - (float) scrollY * OPTION_SCROLL_STEP);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingScrollbar) {
            ScrollbarThumb scrollbarThumb = scrollbarThumb();
            if (scrollbarThumb == null) {
                this.draggingScrollbar = false;
                return false;
            }

            float trackTravel = scrollbarThumb.trackTravel();
            if (trackTravel <= 0.0F) {
                setTargetOptionScroll(0.0F);
            } else {
                float thumbTop = (float) mouseY - this.scrollbarDragOffset;
                float ratio = Mth.clamp((thumbTop - scrollbarThumb.viewportTop()) / trackTravel, 0.0F, 1.0F);
                setTargetOptionScroll(maxOptionScroll() * ratio);
            }
            this.optionScroll = this.targetOptionScroll;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        ClientVillagerConversationState.clear();
        if (!this.closingFromServer) {
            PacketDistributor.sendToServer(new VillagerConversationEndRequestPayload(this.villagerEntityId));
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildOptions() {
        this.options.clear();
        if (this.page == DialoguePage.TALK) {
            this.options.add(DialogueOption.enabled("Chat", () -> requestDialogue(DialogueRequestType.CHAT)));
            this.options.add(DialogueOption.enabled("Greeting", () -> requestDialogue(DialogueRequestType.GREETING)));
            this.options.add(DialogueOption.enabled("Question", () -> requestDialogue(DialogueRequestType.QUESTION)));
            this.options.add(DialogueOption.enabled("Story", () -> requestDialogue(DialogueRequestType.STORY)));
            this.options.add(DialogueOption.enabled("Joke", () -> requestDialogue(DialogueRequestType.JOKE)));
            this.options.add(DialogueOption.enabled("Insult", () -> requestDialogue(DialogueRequestType.INSULT)));
            this.options.add(DialogueOption.enabled("Back", () -> {
                this.page = DialoguePage.ROOT;
                rebuildOptions();
            }));
        } else {
            this.options.add(DialogueOption.enabled("Talk", () -> {
                this.page = DialoguePage.TALK;
                rebuildOptions();
            }));
            this.options.add(DialogueOption.enabled("Trade", () ->
                    PacketDistributor.sendToServer(new VillagerTradeRequestPayload(this.villagerEntityId))));
            this.options.add(DialogueOption.disabled("Recruit", "Coming soon"));
            this.options.add(DialogueOption.disabled("Inventory", "Coming soon"));
            this.options.add(DialogueOption.enabled("Goodbye", this::leaveConversation));
        }
        this.selectedOption = firstEnabledOption();
        this.optionScroll = 0.0F;
        this.targetOptionScroll = 0.0F;
        ensureSelectedVisible();
    }

    private void requestDialogue(DialogueRequestType requestType) {
        PacketDistributor.sendToServer(new VillagerDialogueRequestPayload(this.villagerEntityId, requestType));
    }

    private void leaveConversation() {
        this.minecraft.setScreen(null);
    }

    private void activateSelected() {
        if (this.selectedOption < 0 || this.selectedOption >= this.options.size()) {
            return;
        }
        DialogueOption option = this.options.get(this.selectedOption);
        if (option.disabled()) {
            return;
        }
        option.action().run();
    }

    private void moveSelection(int direction) {
        if (this.options.isEmpty()) {
            return;
        }
        int next = this.selectedOption;
        for (int steps = 0; steps < this.options.size(); steps++) {
            next = Mth.positiveModulo(next + direction, this.options.size());
            if (!this.options.get(next).disabled()) {
                this.selectedOption = next;
                ensureSelectedVisible();
                return;
            }
        }
    }

    private int firstEnabledOption() {
        for (int index = 0; index < this.options.size(); index++) {
            if (!this.options.get(index).disabled()) {
                return index;
            }
        }
        return 0;
    }

    private void renderOptions(GuiGraphics graphics, int mouseX, int mouseY, int top) {
        int left = optionsLeft();
        int viewportHeight = optionViewportHeight();
        int viewportBottom = top + viewportHeight;
        int hovered = optionAt(mouseX, mouseY);

        graphics.enableScissor(left - 24, top - 3, left + OPTION_WIDTH + 10, viewportBottom + 3);
        for (int index = 0; index < this.options.size(); index++) {
            DialogueOption option = this.options.get(index);
            float y = top + index * optionStride() - this.optionScroll;
            if (y + OPTION_HEIGHT < top - 10 || y > viewportBottom + 10) {
                continue;
            }

            boolean selected = index == this.selectedOption;
            float hoverMix = hovered == index ? hoverIntensity(mouseX, mouseY, left, y) : 0.0F;
            float scale = option.disabled()
                    ? 1.0F
                    : 1.0F + (selected ? OPTION_SELECTED_SCALE : 0.0F) + hoverMix * OPTION_HOVER_SCALE;
            float cursorShiftX = hovered == index ? hoverShift(mouseX, left, OPTION_WIDTH, 3.2F) * hoverMix : 0.0F;
            float cursorShiftY = hovered == index ? hoverShift(mouseY, y, OPTION_HEIGHT, 1.6F) * hoverMix : 0.0F;
            int textColor = option.disabled() ? 0x7C8A8A8A : selected ? 0xFFF8F8F4 : hovered == index ? 0xFFE5E5DE : 0xCFC7C8C5;
            int fadedTextColor = withAlpha(textColor, edgeFadeAlpha(y, top, viewportBottom));

            graphics.pose().pushPose();
            float pivotX = left + OPTION_WIDTH * 0.5F;
            float pivotY = y + OPTION_HEIGHT * 0.5F;
            graphics.pose().translate(pivotX + cursorShiftX, pivotY + cursorShiftY, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.pose().translate(-pivotX, -pivotY, 0.0F);

            if (selected && !option.disabled()) {
                graphics.drawString(this.font, ">", left - 7, Mth.floor(y + 5.0F), withAlpha(0xFFFFFFFF, edgeFadeAlpha(y, top, viewportBottom)), false);
            }
            graphics.drawString(this.font, option.label(), left + OPTION_TEXT_INSET, Mth.floor(y + 5.0F), fadedTextColor, false);
            graphics.pose().popPose();
        }
        graphics.disableScissor();

        renderScrollbar(graphics, left, top, viewportBottom);

        if (hovered >= 0 && this.options.get(hovered).tooltip() != null) {
            graphics.renderTooltip(this.font, Component.literal(this.options.get(hovered).tooltip()), mouseX, mouseY);
        }
    }

    private void renderHint(GuiGraphics graphics) {
        String hint = "Esc: leave";
        graphics.drawString(this.font, hint, this.width - this.font.width(hint) - 8, this.height - 14, 0x66FFFFFF, false);
    }

    private void renderConversationFocus(GuiGraphics graphics) {
        int dividerX = dividerX();
        int centerY = focusCenterY();
        int dividerTop = centerY - DIVIDER_HEIGHT / 2;
        int dividerBottom = centerY + DIVIDER_HEIGHT / 2;
        int coreLeft = dividerX - 1;
        int coreRight = dividerX + 1;

        for (int offset = 1; offset <= 4; offset++) {
            float alphaFactor = (5.0F - offset) / 5.0F;
            graphics.fill(coreLeft - offset, dividerTop, coreLeft - offset + 1, dividerBottom, withAlpha(DIVIDER_GLOW_COLOR, alphaFactor));
            graphics.fill(coreRight + offset - 1, dividerTop, coreRight + offset, dividerBottom, withAlpha(DIVIDER_GLOW_COLOR, alphaFactor * 0.55F));
        }
        graphics.fill(coreLeft, dividerTop, coreRight, dividerBottom, DIVIDER_CORE_COLOR);

        String speaker = this.villagerName;
        String profession = this.professionName;
        String mood = "Mood: " + displayName(this.reputationLevel);
        String reputation = "Reputation " + this.reputation;
        int nameX = dividerX - 28 - this.font.width(speaker);
        graphics.drawString(this.font, speaker, nameX, centerY - 20, INFO_VALUE_COLOR, false);
        int professionX = dividerX - 28 - this.font.width(profession);
        graphics.drawString(this.font, profession, professionX, centerY - 5, INFO_SECONDARY_COLOR, false);
        int moodX = dividerX - 28 - this.font.width(mood);
        graphics.drawString(this.font, mood, moodX, centerY + 11, moodColor(this.reputationLevel), false);
        int reputationX = dividerX - 28 - this.font.width(reputation);
        graphics.drawString(this.font, reputation, reputationX, centerY + 28, INFO_LABEL_COLOR, false);
    }

    private void updateMouseSelection(int mouseX, int mouseY) {
        int hovered = optionAt(mouseX, mouseY);
        if (hovered >= 0) {
            this.selectedOption = hovered;
        }
    }

    private int optionAt(double mouseX, double mouseY) {
        int left = optionsLeft();
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        if (mouseX < left - 18 || mouseX > left + OPTION_WIDTH) {
            return -1;
        }
        if (mouseY < top - 2 || mouseY > bottom + 2) {
            return -1;
        }
        for (int index = 0; index < this.options.size(); index++) {
            float y = top + index * optionStride() - this.optionScroll;
            if (mouseY >= y - 2.0F && mouseY <= y + OPTION_HEIGHT + 2.0F) {
                return index;
            }
        }
        return -1;
    }

    private int optionsTop() {
        return focusCenterY() - Math.min(DIVIDER_HEIGHT / 2 - 4, optionViewportHeight() / 2);
    }

    private int optionsLeft() {
        return dividerX() + 28;
    }

    private int dividerX() {
        return this.width / 2 + 16;
    }

    private int focusCenterY() {
        return Math.max(72, this.height - 124);
    }

    private int optionViewportHeight() {
        int visibleRows = Math.min(OPTION_VIEWPORT_ROWS, Math.max(1, this.options.size()));
        return visibleRows * OPTION_HEIGHT + Math.max(0, visibleRows - 1) * OPTION_GAP;
    }

    private float maxOptionScroll() {
        return Math.max(0.0F, optionContentHeight() - optionViewportHeight());
    }

    private float optionContentHeight() {
        if (this.options.isEmpty()) {
            return 0.0F;
        }
        return this.options.size() * OPTION_HEIGHT + Math.max(0, this.options.size() - 1) * OPTION_GAP;
    }

    private int optionStride() {
        return OPTION_HEIGHT + OPTION_GAP;
    }

    private void ensureSelectedVisible() {
        if (this.selectedOption < 0 || this.selectedOption >= this.options.size()) {
            return;
        }

        float optionTop = this.selectedOption * optionStride();
        float optionBottom = optionTop + OPTION_HEIGHT;
        float viewportTop = this.targetOptionScroll;
        float viewportBottom = viewportTop + optionViewportHeight();
        int padding = 6;
        if (optionTop < viewportTop + padding) {
            setTargetOptionScroll(optionTop - padding);
        } else if (optionBottom > viewportBottom - padding) {
            setTargetOptionScroll(optionBottom - optionViewportHeight() + padding);
        } else {
            setTargetOptionScroll(this.targetOptionScroll);
        }
    }

    private void setTargetOptionScroll(float scroll) {
        this.targetOptionScroll = Mth.clamp(scroll, 0.0F, maxOptionScroll());
    }

    private float edgeFadeAlpha(float optionY, int viewportTop, int viewportBottom) {
        if (maxOptionScroll() <= 0.0F) {
            return 1.0F;
        }

        float fadeBand = 16.0F;
        boolean canScrollUp = this.optionScroll > 0.75F;
        boolean canScrollDown = this.optionScroll < maxOptionScroll() - 0.75F;
        float optionTop = optionY;
        float optionBottom = optionY + OPTION_HEIGHT;
        float topFade = canScrollUp ? Mth.clamp((optionBottom - viewportTop) / fadeBand, 0.0F, 1.0F) : 1.0F;
        float bottomFade = canScrollDown ? Mth.clamp((viewportBottom - optionTop) / fadeBand, 0.0F, 1.0F) : 1.0F;
        return Math.min(topFade, bottomFade);
    }

    private void renderScrollbar(GuiGraphics graphics, int optionsLeft, int viewportTop, int viewportBottom) {
        ScrollbarThumb scrollbarThumb = scrollbarThumb();
        if (scrollbarThumb == null) {
            return;
        }

        boolean canScrollUp = this.optionScroll > 0.75F;
        boolean canScrollDown = this.optionScroll < maxOptionScroll() - 0.75F;
        int fadeLength = Math.min(8, Math.max(3, scrollbarThumb.height() / 3));

        for (int y = scrollbarThumb.top(); y < scrollbarThumb.bottom(); y++) {
            float alphaFactor = 1.0F;
            if (canScrollUp && y < scrollbarThumb.top() + fadeLength) {
                alphaFactor = Math.min(alphaFactor, (y - scrollbarThumb.top() + 1.0F) / fadeLength);
            }
            if (canScrollDown && y >= scrollbarThumb.bottom() - fadeLength) {
                alphaFactor = Math.min(alphaFactor, (scrollbarThumb.bottom() - y) / (float) fadeLength);
            }
            graphics.fill(scrollbarThumb.left(), y, scrollbarThumb.right(), y + 1, withAlpha(0xBFFFFFFF, alphaFactor));
        }
    }

    private ScrollbarThumb scrollbarThumb() {
        float maxScroll = maxOptionScroll();
        if (maxScroll <= 0.0F) {
            return null;
        }

        int viewportTop = optionsTop();
        int viewportHeight = optionViewportHeight();
        int scrollbarLeft = optionsLeft() - OPTION_SCROLLBAR_OFFSET;
        int scrollbarRight = scrollbarLeft + OPTION_SCROLLBAR_WIDTH;
        int thumbHeight = Math.max(18, Mth.floor(viewportHeight * (viewportHeight / optionContentHeight())));
        float trackTravel = Math.max(0.0F, viewportHeight - thumbHeight);
        float scrollRatio = maxScroll <= 0.0F ? 0.0F : this.optionScroll / maxScroll;
        int thumbTop = viewportTop + Mth.floor(trackTravel * scrollRatio);
        return new ScrollbarThumb(scrollbarLeft, scrollbarRight, thumbTop, thumbTop + thumbHeight, viewportTop, trackTravel);
    }

    private static int withAlpha(int color, float alphaFactor) {
        int alpha = color >>> 24;
        int adjustedAlpha = Mth.clamp(Mth.floor(alpha * alphaFactor), 0, 255);
        return adjustedAlpha << 24 | color & 0x00FFFFFF;
    }

    private float hoverIntensity(double mouseX, double mouseY, int left, float top) {
        double normalizedX = Math.abs(((mouseX - left) / OPTION_WIDTH) * 2.0D - 1.0D);
        double normalizedY = Math.abs(((mouseY - top) / OPTION_HEIGHT) * 2.0D - 1.0D);
        double distance = Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
        return (float) Mth.clamp(1.0D - distance / Math.sqrt(2.0D), 0.0D, 1.0D);
    }

    private float hoverShift(double mouse, float start, float size, float strength) {
        return (float) ((((mouse - start) / size) * 2.0D) - 1.0D) * strength;
    }

    private void focusVillagerOnPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(this.villagerEntityId);
        if (entity instanceof Villager villager) {
            villager.getLookControl().setLookAt(minecraft.player, 30.0F, 30.0F);
        }
    }

    private static String displayName(VillagerReputationLevel level) {
        String rawName = level.name();
        StringBuilder builder = new StringBuilder(rawName.length());
        boolean capitalizeNext = true;
        for (char character : rawName.replace('_', ' ').toLowerCase().toCharArray()) {
            if (Character.isWhitespace(character)) {
                capitalizeNext = true;
                builder.append(character);
            } else if (capitalizeNext) {
                builder.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static int moodColor(VillagerReputationLevel level) {
        if (level.trustRank() > VillagerReputationLevel.NEUTRAL.trustRank()) {
            return 0xD08BE0A9;
        }
        if (level.trustRank() < VillagerReputationLevel.NEUTRAL.trustRank()) {
            return 0xD0E69A8A;
        }
        return 0xCFEAE6DC;
    }

    private enum DialoguePage {
        ROOT,
        TALK
    }

    private record DialogueOption(String label, boolean disabled, String tooltip, Runnable action) {
        static DialogueOption enabled(String label, Runnable action) {
            return new DialogueOption(label, false, null, action);
        }

        static DialogueOption disabled(String label, String tooltip) {
            return new DialogueOption(label, true, tooltip, () -> {
            });
        }
    }

    private record ScrollbarThumb(int left, int right, int top, int bottom, int viewportTop, float trackTravel) {
        int height() {
            return this.bottom - this.top;
        }
    }
}
