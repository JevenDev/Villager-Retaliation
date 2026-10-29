package com.jvn.villagerretaliation.client.allegiance;

import com.jvn.villagerretaliation.allegiance.VillageLifecycleState;
import com.jvn.villagerretaliation.network.VillageOverviewPayload;
import com.jvn.villagerretaliation.network.VillageOverviewPayload.Resident;
import com.jvn.villagerretaliation.network.VillageOverviewRequestPayload;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class VillageOverviewScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
    private static final int LINE_HEIGHT = 11;
    private static final int TEXT = 0xFFFFFF;
    private static final int MUTED = 0xD0D0D0;
    private VillageOverviewPayload snapshot;
    private List<Resident> filtered = List.of();
    private List<FormattedCharSequence> guideLines = List.of();
    private List<FormattedCharSequence> summaryLines = List.of();
    private EditBox search;
    private Button previous;
    private Button next;
    private Button refresh;
    private boolean guide;
    private int page;
    private String query = "";
    private long nextRefreshTime;
    private int left;
    private int top;
    private int panelWidth;
    private int bottom;
    private int contentTop;
    private int contentBottom;

    public VillageOverviewScreen(VillageOverviewPayload snapshot) {
        super(text("title"));
        this.snapshot = snapshot;
        this.guide = !snapshot.found();
    }

    public boolean accepts(VillageOverviewPayload payload) {
        return this.snapshot.anchor().equals(payload.anchor())
                && this.snapshot.dimension().equals(payload.dimension());
    }

    public void update(VillageOverviewPayload payload) {
        boolean foundVillage = !this.snapshot.found() && payload.found();
        this.snapshot = payload;
        if (!payload.found()) {
            this.guide = true;
            this.page = 0;
        } else if (foundVillage) {
            this.guide = false;
            this.page = 0;
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(440, this.width - 32);
        this.left = (this.width - this.panelWidth) / 2;
        int panelHeight = Math.min(340, this.height - 32);
        this.top = (this.height - panelHeight) / 2;
        this.bottom = this.top + panelHeight;
        this.contentBottom = this.bottom - 52;
        this.summaryLines = new ArrayList<>();
        if (!this.guide && this.snapshot.found()) {
            this.summaryLines.addAll(this.font.split(population(), this.panelWidth));
            this.summaryLines.addAll(this.font.split(facilities(), this.panelWidth));
            this.summaryLines.addAll(this.font.split(standing(), this.panelWidth));
            this.summaryLines.addAll(this.font.split(VillageEntryOverlay.opinionText(this.snapshot.opinion()), this.panelWidth));
            if (this.snapshot.adults() + this.snapshot.children() > this.snapshot.residents().size()) {
                this.summaryLines.addAll(this.font.split(text("partial_roster", this.snapshot.residents().size(),
                        this.snapshot.adults() + this.snapshot.children()), this.panelWidth));
            }
        }
        int searchTop = this.top + 20 + this.summaryLines.size() * LINE_HEIGHT;
        this.search = new EditBox(this.font, this.left, searchTop, this.panelWidth, 20, text("search"));
        this.search.setMaxLength(64);
        this.search.setHint(text("search"));
        this.search.setValue(this.query);
        this.search.setSuggestion(this.query.isEmpty() ? text("search").getString() : null);
        this.search.setResponder(value -> {
            this.search.setSuggestion(value.isEmpty() ? text("search").getString() : null);
            this.query = value;
            this.page = 0;
            filterResidents();
        });
        this.search.visible = !this.guide && this.snapshot.found();
        this.search.active = this.search.visible;
        addRenderableWidget(this.search);
        this.contentTop = this.guide ? this.top + 22 : searchTop + 26;
        int buttonWidth = (this.panelWidth - 8) / 3;
        this.refresh = addRenderableWidget(Button.builder(text("refresh"), button -> {
            PacketDistributor.sendToServer(new VillageOverviewRequestPayload(this.snapshot.anchor()));
            this.nextRefreshTime = Util.getMillis() + 1_100L;
            button.active = false;
        }).bounds(this.left, this.bottom - 20, buttonWidth, 20).build());
        this.refresh.setTooltip(Tooltip.create(text("refresh_hint")));
        Button guideButton = addRenderableWidget(Button.builder(text(this.guide ? "residents" : "guide"), button -> {
            this.guide = !this.guide;
            this.page = 0;
            rebuildWidgets();
        }).bounds(this.left + buttonWidth + 4, this.bottom - 20, buttonWidth, 20).build());
        guideButton.active = this.snapshot.found();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.left + (buttonWidth + 4) * 2, this.bottom - 20, buttonWidth, 20).build());
        this.previous = addRenderableWidget(Button.builder(text("previous"), button -> changePage(-1))
                .bounds(this.left, this.bottom - 44, 70, 20).build());
        this.next = addRenderableWidget(Button.builder(text("next"), button -> changePage(1))
                .bounds(this.left + this.panelWidth - 70, this.bottom - 44, 70, 20).build());
        this.guideLines = buildGuide();
        filterResidents();
        this.refresh.active = Util.getMillis() >= this.nextRefreshTime;
        setInitialFocus(this.search.visible ? this.search : this.refresh);
    }

    private void filterResidents() {
        String needle = this.query.strip().toLowerCase(Locale.ROOT);
        this.filtered = this.snapshot.residents().stream()
                .filter(resident -> (residentName(resident).getString() + " " + profession(resident).getString()
                        + " " + reputation(resident).getString()).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        updatePages();
    }

    private List<FormattedCharSequence> buildGuide() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Component> paragraphs = new ArrayList<>();
        if (!this.snapshot.found()) {
            paragraphs.add(text("no_village"));
        } else if (this.snapshot.lifecycle() == VillageLifecycleState.EMPTY_GRACE) {
            paragraphs.add(text("empty_grace"));
        }
        paragraphs.add(text("guide.start"));
        paragraphs.add(text("guide.grow"));
        paragraphs.add(text("guide.home"));
        paragraphs.add(text("guide.name"));
        paragraphs.add(text("guide.roster"));
        paragraphs.add(text("guide.open"));
        for (Component paragraph : paragraphs) {
            if (!lines.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            }
            lines.addAll(this.font.split(paragraph, this.panelWidth));
        }
        return lines;
    }

    private int pageSize() {
        return Math.max(1, (this.contentBottom - this.contentTop) / (this.guide ? LINE_HEIGHT : ROW_HEIGHT));
    }

    private int pageCount() {
        int size = this.guide ? this.guideLines.size() : this.filtered.size();
        return Math.max(1, (size + pageSize() - 1) / pageSize());
    }

    private void changePage(int delta) {
        this.page = Math.clamp(this.page + delta, 0, pageCount() - 1);
        updatePages();
    }

    private void updatePages() {
        this.page = Math.clamp(this.page, 0, pageCount() - 1);
        if (this.previous != null && this.next != null) {
            this.previous.active = this.page > 0;
            this.next.active = this.page + 1 < pageCount();
        }
    }

    @Override
    public void tick() {
        if (this.minecraft.player == null || this.minecraft.level == null
                || !this.minecraft.level.dimension().location().equals(this.snapshot.dimension())
                || this.minecraft.player.distanceToSqr(this.snapshot.anchor().getCenter()) > 64.0D) {
            onClose();
            return;
        }
        this.refresh.active = Util.getMillis() >= this.nextRefreshTime;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            changePage(keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= this.left && mouseX <= this.left + this.panelWidth
                && mouseY >= this.contentTop && mouseY < this.contentBottom && vertical != 0.0D) {
            changePage(vertical < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.bottom + 8, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component heading = this.guide ? text("guide_title") : this.snapshot.found()
                ? Component.literal(this.snapshot.name()) : this.title;
        drawFitted(graphics, heading, this.top, TEXT);
        int y = this.top + 16;
        for (FormattedCharSequence line : this.summaryLines) {
            graphics.drawString(this.font, line, this.left, y, MUTED);
            y += LINE_HEIGHT;
        }
        int start = this.page * pageSize();
        if (this.guide) {
            for (int index = start; index < Math.min(start + pageSize(), this.guideLines.size()); index++) {
                graphics.drawString(this.font, this.guideLines.get(index), this.left,
                        this.contentTop + (index - start) * LINE_HEIGHT, TEXT);
            }
        } else if (this.filtered.isEmpty()) {
            graphics.drawWordWrap(this.font, text(this.snapshot.residents().isEmpty() ? "no_residents" : "no_matches"),
                    this.left, this.contentTop, this.panelWidth, MUTED);
        } else {
            for (int index = start; index < Math.min(start + pageSize(), this.filtered.size()); index++) {
                Resident resident = this.filtered.get(index);
                y = this.contentTop + (index - start) * ROW_HEIGHT;
                drawFitted(graphics, residentLine(resident), y, TEXT);
                drawFitted(graphics, presence(resident), y + LINE_HEIGHT, MUTED);
                graphics.hLine(this.left, this.left + this.panelWidth - 1, y + ROW_HEIGHT - 4, 0xFF51483B);
            }
        }
        graphics.drawCenteredString(this.font, text("page", this.page + 1, pageCount()),
                this.width / 2, this.bottom - 38, MUTED);
        if (!this.guide && mouseX >= this.left && mouseX < this.left + this.panelWidth
                && mouseY >= this.contentTop && mouseY < this.contentBottom) {
            int index = start + (mouseY - this.contentTop) / ROW_HEIGHT;
            if (index < this.filtered.size()) {
                Resident resident = this.filtered.get(index);
                if (this.font.width(residentLine(resident)) > this.panelWidth
                        || this.font.width(presence(resident)) > this.panelWidth) {
                    graphics.renderTooltip(this.font, List.of(residentLine(resident).getVisualOrderText(),
                            presence(resident).getVisualOrderText()), mouseX, mouseY);
                }
            }
        }
    }

    private void drawFitted(GuiGraphics graphics, Component text, int y, int color) {
        String value = text.getString();
        if (this.font.width(value) > this.panelWidth) {
            value = this.font.plainSubstrByWidth(value, this.panelWidth - this.font.width("...")) + "...";
        }
        graphics.drawString(this.font, value, this.left, y, color);
    }

    private Component population() {
        return text("population", this.snapshot.adults(), this.snapshot.children(), this.snapshot.present());
    }

    private Component facilities() {
        return text("facilities", this.snapshot.beds(), this.snapshot.freeBeds(), this.snapshot.workstations());
    }

    private Component standing() {
        return this.snapshot.lifecycle() == VillageLifecycleState.EMPTY_GRACE ? text("inactive")
                : text("standing", this.snapshot.trusted(), this.snapshot.required());
    }

    private static Component residentName(Resident resident) {
        return resident.name().isBlank() ? Component.translatable("entity.minecraft.villager")
                : Component.literal(resident.name());
    }

    private static Component profession(Resident resident) {
        return resident.baby() ? text("child") : Component.translatable(VillagerProfessionUtil
                .parse(resident.profession().toString()).map(value -> VillagerProfessionUtil.translationKey(
                        value, "villagerretaliation.gui.village_overview.unemployed"))
                .orElse("villagerretaliation.gui.village_overview.unemployed"));
    }

    private static Component reputation(Resident resident) {
        return Component.translatable("villagerretaliation.reputation.level."
                + resident.reputation().name().toLowerCase(Locale.ROOT));
    }

    private static Component residentLine(Resident resident) {
        return text("resident", residentName(resident), profession(resident), reputation(resident));
    }

    private static Component presence(Resident resident) {
        return switch (resident.presence()) {
            case HERE -> text("here", resident.position().getX(), resident.position().getY(), resident.position().getZ());
            case AWAY -> text("away", resident.position().getX(), resident.position().getY(), resident.position().getZ());
            case UNLOADED -> text("unloaded");
        };
    }

    @Override
    public Component getNarrationMessage() {
        MutableComponent message = this.title.copy();
        if (this.guide) {
            int start = this.page * pageSize();
            StringBuilder visibleText = new StringBuilder();
            for (int index = start; index < Math.min(start + pageSize(), this.guideLines.size()); index++) {
                this.guideLines.get(index).accept((position, style, codePoint) -> {
                    visibleText.appendCodePoint(codePoint);
                    return true;
                });
                visibleText.append(' ');
            }
            return message.append(". ").append(visibleText.toString());
        }
        message.append(". ").append(this.snapshot.name()).append(". ").append(population())
                .append(". ").append(facilities()).append(". ").append(standing())
                .append(". ").append(VillageEntryOverlay.opinionText(this.snapshot.opinion()));
        int start = this.page * pageSize();
        for (int index = start; index < Math.min(start + pageSize(), this.filtered.size()); index++) {
            message.append(". ").append(residentLine(this.filtered.get(index)))
                    .append(". ").append(presence(this.filtered.get(index)));
        }
        return message;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("villagerretaliation.gui.village_overview." + key, args);
    }
}
