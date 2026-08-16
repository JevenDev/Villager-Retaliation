package com.jvn.villagerretaliation.client.allegiance;

import com.jvn.villagerretaliation.network.OpenVillageNamingPayload;
import com.jvn.villagerretaliation.network.VillageRenameRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class VillageNamingScreen extends Screen {
    private final OpenVillageNamingPayload payload;
    private EditBox nameField;
    private Button saveButton;

    public VillageNamingScreen(OpenVillageNamingPayload payload) {
        super(Component.translatable("villagerretaliation.gui.village_naming.title"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int top = this.height / 2 - 20;
        this.nameField = new EditBox(this.font, left, top, 200, 20,
                Component.translatable("villagerretaliation.gui.village_naming.name"));
        this.nameField.setMaxLength(32);
        this.nameField.setValue(this.payload.currentName());
        this.nameField.setEditable(this.payload.canRename());
        this.nameField.setResponder(ignored -> updateSaveState());
        addRenderableWidget(this.nameField);
        this.saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("villagerretaliation.gui.village_naming.save"), ignored -> submit())
                .bounds(left, top + 30, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left + 104, top + 30, 96, 20)
                .build());
        setInitialFocus(this.nameField);
        updateSaveState();
    }

    private void updateSaveState() {
        if (this.saveButton != null) {
            String value = this.nameField == null ? "" : this.nameField.getValue().trim();
            this.saveButton.active = this.payload.canRename() && !value.isBlank() && value.length() <= 32;
        }
    }

    private void submit() {
        if (this.saveButton == null || !this.saveButton.active) {
            return;
        }
        PacketDistributor.sendToServer(new VillageRenameRequestPayload(
                this.payload.bellPosition(), this.payload.villageId(), this.nameField.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.saveButton.active) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 48, 0xFFFFFF);
        Component status = this.payload.canRename()
                ? Component.translatable("villagerretaliation.gui.village_naming.trust_met")
                : Component.translatable(
                        "villagerretaliation.gui.village_naming.trust_required",
                        this.payload.trustedResidents(),
                        this.payload.requiredResidents());
        graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 34,
                this.payload.canRename() ? 0xE9C46A : 0xD0D0D0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
