package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

final class VillagerInteractionChatScreen extends ChatScreen {
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
        this.interactionScreen.renderBackdropBehindChat(graphics);
        this.interactionScreen.render(graphics, mouseX, mouseY, partialTick);
        VillagerInteractionScreen.ChatRenderLayout layout = this.interactionScreen.chatRenderLayout();
        graphics.enableScissor(layout.left(), layout.top(), layout.right(), layout.bottom());
        graphics.pose().pushPose();
        graphics.pose().translate(layout.xOffset(), layout.yOffset(), 0.0F);
        super.render(graphics, layout.translatedMouseX(mouseX), layout.translatedMouseY(mouseY), partialTick);
        graphics.pose().popPose();
        graphics.disableScissor();
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
        VillagerInteractionScreen.ChatRenderLayout layout = this.interactionScreen.chatRenderLayout();
        return super.mouseClicked(mouseX - layout.xOffset(), mouseY - layout.yOffset(), button);
    }

    boolean matchesVillager(int entityId) {
        return this.interactionScreen.matchesVillager(entityId);
    }

    void updateReputation(
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

    void closeFromServer() {
        this.interactionScreen.closeFromServer();
    }

    private void returnToInteractionScreen() {
        Minecraft.getInstance().setScreen(this.interactionScreen);
    }
}
