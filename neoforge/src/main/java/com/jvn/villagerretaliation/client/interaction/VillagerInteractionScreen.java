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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class VillagerInteractionScreen extends Screen {
    private static final int RESPONSE_WIDTH = 520;
    private static final int OPTION_WIDTH = 220;
    private static final int OPTION_HEIGHT = 16;
    private static final int OPTION_GAP = 3;
    private static final int DIVIDER_HEIGHT = 92;

    private final int villagerEntityId;
    private final String villagerName;
    private final String professionName;
    private final int reputation;
    private final VillagerReputationLevel reputationLevel;
    private final List<DialogueOption> options = new ArrayList<>();
    private DialoguePage page = DialoguePage.ROOT;
    private int selectedOption;
    private boolean closingFromServer;

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

    public void setDialogueText(String dialogueText) {
        ClientVillagerConversationState.setResponseText(dialogueText);
    }

    public void showNotice(String text) {
        ClientVillagerConversationState.setResponseText(text);
    }

    public void closeFromServer() {
        this.closingFromServer = true;
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        focusVillagerOnPlayer();
        updateMouseSelection(mouseX, mouseY);

        renderConversationFocus(graphics);
        renderOptions(graphics, mouseX, mouseY, optionsTop());
        renderResponse(graphics);
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
            int hovered = optionAt(mouseX, mouseY, optionsTop());
            if (hovered >= 0) {
                this.selectedOption = hovered;
                activateSelected();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
    }

    private void requestDialogue(DialogueRequestType requestType) {
        ClientVillagerConversationState.setResponseText("...");
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
        for (int index = 0; index < this.options.size(); index++) {
            DialogueOption option = this.options.get(index);
            int y = top + index * (OPTION_HEIGHT + OPTION_GAP);
            boolean selected = index == this.selectedOption;
            int textColor = option.disabled() ? 0x777777 : selected ? 0xFFFFFFFF : 0xBFC0C0C0;
            if (selected && !option.disabled()) {
                graphics.fill(left - 18, y - 1, left + OPTION_WIDTH, y + OPTION_HEIGHT, 0x33000000);
                graphics.drawString(this.font, ">", left - 13, y + 4, 0xFFFFFFFF, true);
            }
            graphics.drawString(this.font, option.label(), left, y + 4, textColor, true);
        }

        int hovered = optionAt(mouseX, mouseY, top);
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

        graphics.fill(dividerX - 1, dividerTop, dividerX, dividerBottom, 0x66FFFFFF);
        graphics.fill(dividerX, dividerTop, dividerX + 1, dividerBottom, 0x99000000);

        String speaker = this.villagerName;
        String profession = this.professionName;
        String mood = "Mood: " + displayName(this.reputationLevel);
        String reputation = "Reputation: " + this.reputation;
        int nameX = dividerX - 28 - this.font.width(speaker);
        graphics.drawString(this.font, speaker, nameX, centerY - 18, 0xFFFFFFFF, true);
        int professionX = dividerX - 28 - this.font.width(profession);
        graphics.drawString(this.font, profession, professionX, centerY - 3, 0x88FFFFFF, true);
        int moodX = dividerX - 28 - this.font.width(mood);
        graphics.drawString(this.font, mood, moodX, centerY + 11, moodColor(this.reputationLevel), true);
        int reputationX = dividerX - 28 - this.font.width(reputation);
        graphics.drawString(this.font, reputation, reputationX, centerY + 24, 0x88FFFFFF, true);
    }

    private void renderResponse(GuiGraphics graphics) {
        List<FormattedCharSequence> lines = this.font.split(
                Component.literal(ClientVillagerConversationState.responseText()),
                Math.min(RESPONSE_WIDTH, this.width - 48)
        );
        int lineHeight = 11;
        int top = this.height - 52 - Math.max(0, lines.size() - 1) * lineHeight;
        for (int index = 0; index < lines.size(); index++) {
            FormattedCharSequence line = lines.get(index);
            int x = (this.width - this.font.width(line)) / 2;
            graphics.drawString(this.font, line, x, top + index * lineHeight, 0xFFFFFFFF, true);
        }
    }

    private void updateMouseSelection(int mouseX, int mouseY) {
        int hovered = optionAt(mouseX, mouseY, optionsTop());
        if (hovered >= 0) {
            this.selectedOption = hovered;
        }
    }

    private int optionAt(double mouseX, double mouseY, int top) {
        int left = optionsLeft();
        if (mouseX < left - 18 || mouseX > left + OPTION_WIDTH) {
            return -1;
        }
        for (int index = 0; index < this.options.size(); index++) {
            int y = top + index * (OPTION_HEIGHT + OPTION_GAP);
            if (mouseY >= y - 2 && mouseY <= y + OPTION_HEIGHT) {
                return index;
            }
        }
        return -1;
    }

    private int optionsTop() {
        return focusCenterY() - Math.min(DIVIDER_HEIGHT / 2 - 4, this.options.size() * (OPTION_HEIGHT + OPTION_GAP) / 2);
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
            return 0xAA80FFB0;
        }
        if (level.trustRank() < VillagerReputationLevel.NEUTRAL.trustRank()) {
            return 0xAAFF8A7A;
        }
        return 0xAAFFFFFF;
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
}
