package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload;
import com.jvn.villagerretaliation.network.VillagerTradeRequestPayload;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public class VillagerInteractionScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 76;
    private static final int BUTTON_WIDTH = 118;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private final int villagerEntityId;
    private final String villagerName;
    private final String professionName;
    private String dialogueText = "Choose an option.";
    private boolean talkMenuOpen;

    public VillagerInteractionScreen(int villagerEntityId, String villagerName, String professionName) {
        super(Component.literal("Villager Interaction"));
        this.villagerEntityId = villagerEntityId;
        this.villagerName = villagerName;
        this.professionName = professionName;
    }

    @Override
    protected void init() {
        rebuildInteractionButtons();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public boolean matchesVillager(int entityId) {
        return this.villagerEntityId == entityId;
    }

    public void setDialogueText(String dialogueText) {
        this.dialogueText = dialogueText;
    }

    public void showNotice(String text) {
        this.dialogueText = text;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        focusVillagerOnPlayer();
        int left = panelLeft();
        int top = panelTop();
        graphics.fill(left - 2, top - 2, left + PANEL_WIDTH + 2, top + PANEL_HEIGHT + 2, 0xCC050505);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xB8181818);
        graphics.fill(left + 8, top + 20, left + PANEL_WIDTH - 8, top + 21, 0xAA8B7A58);
        graphics.drawString(this.font, this.villagerName, left + 10, top + 8, 0xF6E7BC, false);
        graphics.drawString(this.font, this.professionName, left + PANEL_WIDTH - 10 - this.font.width(this.professionName), top + 8, 0xB8B1A0, false);
        graphics.drawWordWrap(this.font, Component.literal(this.dialogueText), left + 12, top + 31, PANEL_WIDTH - 24, 0xECE1C8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildInteractionButtons() {
        clearWidgets();
        int left = panelLeft();
        int top = panelTop();
        int buttonTop = top + PANEL_HEIGHT + 8;
        if (this.talkMenuOpen) {
            addDialogueButton("Greeting", DialogueRequestType.GREETING, left, buttonTop);
            addDialogueButton("Question", DialogueRequestType.QUESTION, left + BUTTON_WIDTH + BUTTON_GAP, buttonTop);
            addDialogueButton("Story", DialogueRequestType.STORY, left + (BUTTON_WIDTH + BUTTON_GAP) * 2, buttonTop);
            addDialogueButton("Joke", DialogueRequestType.JOKE, left, buttonTop + BUTTON_HEIGHT + BUTTON_GAP);
            addDialogueButton("Insult", DialogueRequestType.INSULT, left + BUTTON_WIDTH + BUTTON_GAP, buttonTop + BUTTON_HEIGHT + BUTTON_GAP);
            addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
                this.talkMenuOpen = false;
                rebuildInteractionButtons();
            }).bounds(left + (BUTTON_WIDTH + BUTTON_GAP) * 2, buttonTop + BUTTON_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build());
            return;
        }

        addRenderableWidget(Button.builder(Component.literal("Talk"), button -> {
            this.talkMenuOpen = true;
            rebuildInteractionButtons();
        }).bounds(left, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Trade"), button ->
                PacketDistributor.sendToServer(new VillagerTradeRequestPayload(this.villagerEntityId))
        ).bounds(left + BUTTON_WIDTH + BUTTON_GAP, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        Button recruit = disabledButton("Recruit", left + (BUTTON_WIDTH + BUTTON_GAP) * 2, buttonTop);
        Button inventory = disabledButton("Inventory", left, buttonTop + BUTTON_HEIGHT + BUTTON_GAP);
        addRenderableWidget(recruit);
        addRenderableWidget(inventory);
    }

    private void addDialogueButton(String label, DialogueRequestType requestType, int x, int y) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            this.dialogueText = "...";
            PacketDistributor.sendToServer(new VillagerDialogueRequestPayload(this.villagerEntityId, requestType));
        }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private Button disabledButton(String label, int x, int y) {
        Button button = Button.builder(Component.literal(label), ignored -> {
        }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(Tooltip.create(Component.literal("Coming soon"))).build();
        button.active = false;
        return button;
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return Math.max(18, this.height - PANEL_HEIGHT - 64);
    }

    private void focusVillagerOnPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(this.villagerEntityId);
        if (entity instanceof Villager villager) {
            villager.getLookControl().setLookAt(minecraft.player, 30.0F, 30.0F);
            villager.lookAt(EntityAnchorArgument.Anchor.EYES, minecraft.player.getEyePosition());
            villager.setYBodyRot(villager.getYHeadRot());
        }
    }
}
