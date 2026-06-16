package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

final class VillagerInteractionChatScreen extends ChatScreen implements VillagerInteractionSessionScreen {
    private final VillagerInteractionScreen interactionScreen;

    VillagerInteractionChatScreen(VillagerInteractionScreen interactionScreen, String initialText) {
        super(initialText);
        this.interactionScreen = interactionScreen;
    }

    @Override
    public void tick() {
        this.interactionScreen.tick();
        super.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.interactionScreen.renderPositionedHudChat(graphics);
        this.interactionScreen.render(graphics, mouseX, mouseY, partialTick);
        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.chatLayerZ());
        super.render(graphics, mouseX, mouseY, partialTick);
        VillagerClientUiUtil.popGuiLayer(graphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToInteractionScreen();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            handleChatInput(this.input.getValue(), true);
            if (Minecraft.getInstance().screen == this) {
                returnToInteractionScreen();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean matchesVillager(int entityId) {
        return this.interactionScreen.matchesVillager(entityId);
    }

    @Override
    public void updateReputation(
            int reputation,
            VillagerReputationLevel reputationLevel,
            DialogueDisposition mood,
            VillagerMood primaryMood,
            boolean forceCameraTowardsVillager,
            List<DialogueOptionDefinition> dialogueOptions,
            List<String> knownLikedGiftNames,
            List<String> knownDislikedGiftNames) {
        this.interactionScreen.updateReputation(
                reputation,
                reputationLevel,
                mood,
                primaryMood,
                forceCameraTowardsVillager,
                dialogueOptions,
                knownLikedGiftNames,
                knownDislikedGiftNames
        );
    }

    @Override
    public void replaceFromServer() {
        this.interactionScreen.replaceFromServer();
    }

    @Override
    public void closeFromServer() {
        this.interactionScreen.closeFromServer();
    }

    @Override
    public void acceptVillagerDialogue(String text) {
        this.interactionScreen.acceptVillagerDialogue(text);
    }

    private void returnToInteractionScreen() {
        Minecraft.getInstance().setScreen(this.interactionScreen);
    }
}
