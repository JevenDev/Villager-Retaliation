package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.quest.VillagerQuestKeyMappings;
import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.network.CampaignJournalPagePayload;
import com.jvn.villagerretaliation.network.CampaignJournalRequestPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CampaignJournalScreen extends Screen {
    private static final String PREFIX = "villagerretaliation.gui.campaign_journal.";
    private final Screen parent;
    private List<CampaignJournalPagePayload.Summary> campaigns = List.of();
    private List<String> paragraphs = List.of();
    private List<FormattedCharSequence> lines = List.of();
    private CampaignJournalRequestPayload indexRequest;
    private CampaignJournalRequestPayload detailRequest;
    private String selectedCampaign = "";
    private int indexPage;
    private int indexPages = 1;
    private int detailPage;
    private int detailPages = 1;
    private int scroll;
    private int age;
    private int indexSentAt;
    private int detailSentAt;

    public CampaignJournalScreen(Screen parent) {
        super(Component.translatable(PREFIX + "title"));
        this.parent = parent;
    }

    public static void accept(CampaignJournalPagePayload payload) {
        if (Minecraft.getInstance().screen instanceof CampaignJournalScreen screen) screen.acceptPage(payload);
    }

    @Override
    protected void init() {
        rebuildButtons();
        this.lines = this.font.split(Component.literal(String.join("\n\n", this.paragraphs)), 137);
        requestIndex(this.indexPage);
    }

    private void requestIndex(int page) {
        this.indexRequest = new CampaignJournalRequestPayload(UUID.randomUUID(), "", page);
        this.indexSentAt = this.age;
        PacketDistributor.sendToServer(this.indexRequest);
    }

    private void requestDetails(String campaign, int page) {
        if (!this.selectedCampaign.equals(campaign) || this.detailPage != page) {
            this.scroll = 0;
            this.paragraphs = List.of();
            this.lines = List.of();
        }
        this.selectedCampaign = campaign;
        this.detailPage = page;
        this.detailRequest = new CampaignJournalRequestPayload(UUID.randomUUID(), campaign, page);
        this.detailSentAt = this.age;
        PacketDistributor.sendToServer(this.detailRequest);
    }

    private void acceptPage(CampaignJournalPagePayload payload) {
        if (payload.campaignId().isBlank()) {
            if (this.indexRequest == null || !this.indexRequest.requestId().equals(payload.requestId())) return;
            this.indexRequest = null;
            boolean changed = !this.campaigns.equals(payload.campaigns()) || this.indexPage != payload.page() || this.indexPages != payload.pageCount();
            this.campaigns = payload.campaigns();
            this.indexPage = payload.page();
            this.indexPages = payload.pageCount();
            if (!this.campaigns.isEmpty() && this.campaigns.stream().noneMatch(value -> value.id().equals(this.selectedCampaign))) {
                requestDetails(this.campaigns.getFirst().id(), 0);
            }
            if (this.campaigns.isEmpty()) {
                this.selectedCampaign = "";
                this.detailRequest = null;
                this.paragraphs = List.of();
                this.lines = List.of();
            }
            if (changed) rebuildButtons();
        } else {
            if (this.detailRequest == null || !this.detailRequest.requestId().equals(payload.requestId())
                    || !this.selectedCampaign.equals(payload.campaignId())) return;
            this.detailRequest = null;
            boolean changed = !this.paragraphs.equals(payload.paragraphs()) || this.detailPage != payload.page() || this.detailPages != payload.pageCount();
            this.paragraphs = payload.paragraphs();
            this.detailPage = payload.page();
            this.detailPages = payload.pageCount();
            this.lines = this.font.split(Component.literal(String.join("\n\n", this.paragraphs)), 137);
            this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.lines.size() - 15));
            if (changed) rebuildButtons();
        }
    }

    private void rebuildButtons() {
        clearWidgets();
        int left = left();
        int top = top();
        for (int i = 0; i < this.campaigns.size(); i++) {
            var campaign = this.campaigns.get(i);
            var label = Component.literal(this.font.plainSubstrByWidth(campaign.title(), 119));
            var button = Button.builder(label, ignored -> requestDetails(campaign.id(), 0))
                    .bounds(left + 23, top + 42 + i * 18, 127, 18).build();
            button.setTooltip(Tooltip.create(Component.literal(campaign.title())));
            addRenderableWidget(button);
        }
        var previousIndex = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> requestIndex(this.indexPage - 1))
                .bounds(left + 23, top + 175, 22, 16).build());
        previousIndex.active = this.indexPage > 0;
        var nextIndex = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> requestIndex(this.indexPage + 1))
                .bounds(left + 128, top + 175, 22, 16).build());
        nextIndex.active = this.indexPage + 1 < this.indexPages;
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + "back"), ignored -> onClose())
                .bounds(left + 23, top + 194, 127, 16).build());
        var previousDetail = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> requestDetails(this.selectedCampaign, this.detailPage - 1))
                .bounds(left + 192, top + 194, 22, 16).build());
        previousDetail.active = !this.selectedCampaign.isBlank() && this.detailPage > 0;
        var nextDetail = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> requestDetails(this.selectedCampaign, this.detailPage + 1))
                .bounds(left + 307, top + 194, 22, 16).build());
        nextDetail.active = !this.selectedCampaign.isBlank() && this.detailPage + 1 < this.detailPages;
    }

    @Override
    public void tick() {
        this.age++;
        if (this.indexRequest != null && this.age - this.indexSentAt >= 10) {
            this.indexSentAt = this.age;
            PacketDistributor.sendToServer(this.indexRequest);
        }
        if (this.detailRequest != null && this.age - this.detailSentAt >= 10) {
            this.detailSentAt = this.age;
            PacketDistributor.sendToServer(this.detailRequest);
        }
        if (this.age % 100 == 0 && this.indexRequest == null && this.detailRequest == null) {
            requestIndex(this.indexPage);
            if (!this.selectedCampaign.isBlank()) requestDetails(this.selectedCampaign, this.detailPage);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = left();
        int top = top();
        graphics.blit(VillagerRetaliationClientAssets.QUEST_JOURNAL_CONTAINER_TEXTURE, left, top, 0, 0, 351, 215, 351, 215);
        graphics.blit(VillagerRetaliationClientAssets.QUEST_JOURNAL_CONTAINER_OVERLAY_TEXTURE, left, top, 0, 0, 351, 215, 351, 215);
        graphics.drawString(this.font, this.title, left + 28, top + 28, 0xFF000000, false);
        graphics.drawCenteredString(this.font, (this.indexPage + 1) + "/" + this.indexPages, left + 87, top + 179, 0xFF000000);
        if (!this.selectedCampaign.isBlank()) {
            graphics.drawCenteredString(this.font, (this.detailPage + 1) + "/" + this.detailPages, left + 260, top + 198, 0xFF000000);
        }
        if (this.campaigns.isEmpty()) {
            var message = Component.translatable(PREFIX + (this.indexRequest == null ? "empty" : "loading"));
            graphics.drawWordWrap(this.font, message, left + 25, top + 45, 123, 0xFF000000);
        }
        if (this.detailRequest != null && this.lines.isEmpty()) {
            graphics.drawWordWrap(this.font, Component.translatable(PREFIX + "loading"), left + 192, top + 30, 137, 0xFF000000);
        }
        graphics.enableScissor(left + 192, top + 28, left + 329, top + 187);
        for (int i = this.scroll; i < Math.min(this.lines.size(), this.scroll + 15); i++) {
            graphics.drawString(this.font, this.lines.get(i), left + 192, top + 30 + (i - this.scroll) * 10, 0xFF000000, false);
        }
        graphics.disableScissor();
        if (this.lines.size() > 15) {
            int thumb = Math.max(8, 150 * 15 / this.lines.size());
            int position = (150 - thumb) * this.scroll / (this.lines.size() - 15);
            graphics.fill(left + 332, top + 30, left + 334, top + 180, 0x44543C2C);
            graphics.fill(left + 332, top + 30 + position, left + 334, top + 30 + position + thumb, 0xFF543C2C);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (VillagerQuestKeyMappings.OPEN_JOURNAL.matches(keyCode, scanCode)) {
            VillagerQuestTrackerOverlay.ignorePendingJournalToggle();
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            int direction = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
            this.scroll = Math.clamp(this.scroll + direction * 15, 0, Math.max(0, this.lines.size() - 15));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= left() + 182 && mouseX <= left() + 338) {
            this.scroll = Math.clamp(this.scroll - (int) Math.signum(vertical) * 3, 0, Math.max(0, this.lines.size() - 15));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(this.parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private int left() { return (this.width - 351) / 2; }
    private int top() { return (this.height - 215) / 2; }
}
