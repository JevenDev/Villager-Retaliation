package com.jvn.villagerretaliation.client.allegiance;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.OpenVillageNamingPayload;
import com.jvn.villagerretaliation.network.VillageRenameRequestPayload;
import com.jvn.villagerretaliation.network.VillageRenameResultPayload;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class VillageNamingScreen extends VillagerWindowScreen {
    private final OpenVillageNamingPayload payload;
    private EditBox nameField;
    private Button saveButton;
    private String draft;
    private UUID pendingRequest;
    private long responseDeadline;
    private Component error;

    public VillageNamingScreen(OpenVillageNamingPayload payload) {
        super(text("title"));
        this.payload = payload;
        this.draft = payload.currentName();
    }

    @Override
    protected void init() {
        initWindow(320, 152);
        this.nameField = new EditBox(this.font, this.left, this.top + 48, this.panelWidth, 20, text("name"));
        this.nameField.setMaxLength(32);
        this.nameField.setValue(this.draft);
        this.nameField.setResponder(value -> {
            this.draft = value;
            this.error = null;
            updateSaveState();
        });
        addRenderableWidget(this.nameField);
        int buttonWidth = (this.panelWidth - 8) / 2;
        this.saveButton = addRenderableWidget(VillagerUi.buttonBuilder(text("save"), ignored -> submit())
                .bounds(this.left, this.top + 132, buttonWidth, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(this.left + buttonWidth + 8, this.top + 132, buttonWidth, 20).build());
        setInitialFocus(this.nameField);
        updateSaveState();
    }

    private boolean validName() {
        return VillageAllegianceRegistrySavedData.validateVillageName(this.draft).isPresent();
    }

    private void updateSaveState() {
        if (this.saveButton != null) {
            boolean editable = this.payload.canRename() && this.pendingRequest == null;
            this.nameField.setEditable(editable);
            this.saveButton.active = editable && validName();
            this.saveButton.setMessage(text(this.pendingRequest == null ? "save" : "saving"));
        }
    }

    private void submit() {
        if (this.saveButton == null || !this.saveButton.active) {
            return;
        }
        this.pendingRequest = UUID.randomUUID();
        this.responseDeadline = Util.getMillis() + 10_000L;
        this.error = null;
        updateSaveState();
        PacketDistributor.sendToServer(new VillageRenameRequestPayload(this.pendingRequest,
                this.payload.bellPosition(), this.payload.villageId(), this.draft));
    }

    public void accept(VillageRenameResultPayload result) {
        if (!result.requestId().equals(this.pendingRequest)) {
            return;
        }
        this.pendingRequest = null;
        if (result.result() == VillageRenameResultPayload.Result.SUCCESS) {
            onClose();
            return;
        }
        this.error = result.message();
        updateSaveState();
        this.minecraft.getNarrator().sayNow(this.error);
    }

    @Override
    public void tick() {
        if (this.pendingRequest != null && Util.getMillis() >= this.responseDeadline) {
            this.pendingRequest = null;
            this.error = text("timed_out");
            updateSaveState();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.saveButton.active) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Component status() {
        return this.payload.canRename() ? text("trust_met")
                : text("trust_required", this.payload.trustedResidents(), this.payload.requiredResidents());
    }

    private Component feedback() {
        if (this.error != null) {
            return this.error;
        }
        if (this.payload.canRename() && !validName()) {
            return Component.translatable("villagerretaliation.village_naming.error.invalid_name");
        }
        return text("hint");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top, 0xFFFFFF);
        int y = this.top + 16;
        for (FormattedCharSequence line : this.font.split(status(), this.panelWidth)) {
            graphics.drawString(this.font, line, this.left, y, 0xD0D0D0);
            y += 10;
        }
        graphics.drawString(this.font, text("name"), this.left, this.top + 36, 0xFFFFFF);
        Component count = text("length", this.draft.length());
        graphics.drawString(this.font, count, this.left + this.panelWidth - this.font.width(count),
                this.top + 72, 0xD0D0D0);
        graphics.drawWordWrap(this.font, feedback(), this.left, this.top + 90, this.panelWidth,
                this.error != null || this.payload.canRename() && !validName() ? 0xFF8C8C : 0xD0D0D0);
    }

    @Override
    public Component getNarrationMessage() {
        return this.title.copy().append(". ").append(status()).append(". ").append(feedback());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("villagerretaliation.gui.village_naming." + key, args);
    }
}
