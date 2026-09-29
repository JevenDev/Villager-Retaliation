package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementWorksitePlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SettlementWorksiteAdoptionScreen extends Screen {
    private final SettlementWorkerScreen previous;
    private final List<Component> preview = new ArrayList<>();
    private Button adopt;

    SettlementWorksiteAdoptionScreen(SettlementWorkerScreen previous, SettlementWorksitePlan plan) {
        super(Component.literal("Review worksite adoption"));
        this.previous = previous;
        this.preview.add(Component.literal(previous.worksite().name()));
        this.preview.add(Component.literal(previous.worksite().workers() + " existing workers retain their individual settings as overrides."));
        this.preview.add(Component.literal("New staff inherit this plan. Desired staff: " + plan.desiredStaff() + "."));
        this.preview.add(Component.literal("Existing personal contracts and assignments remain under their current ownership."));
        if (plan.settings().role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.BREWING) {
            this.preview.add(Component.literal("Legacy brewing orders convert only when you add production. Active batches are preserved."));
        }
        plan.settings().options().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry ->
                this.preview.add(SettlementManagementScreen.enumText("worker", entry.getKey())
                        .append(": ").append(SettlementWorkerScreen.value(entry.getKey(), entry.getValue()))));
        if (plan.settings().role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.LOGGING) {
            this.preview.add(Component.literal("Tree species: " + (plan.settings().logs().isEmpty() ? "all" : plan.settings().logs())));
        }
        if (plan.settings().role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.ANIMAL_HANDLING) {
            this.preview.add(Component.literal("Animal species: " + (plan.settings().animals().isEmpty() ? "all" : plan.settings().animals())));
        }
        if (plan.settings().role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.FISHING) {
            var spots = plan.settings().fishingSpots().positions();
            this.preview.add(Component.literal(spots.isEmpty() ? "Fishing spots: automatic discovery" : "Fishing spots in priority order:"));
            spots.forEach(position -> this.preview.add(Component.literal(position.toShortString())));
        }
        this.preview.add(Component.literal(plan.links().supplies().isEmpty() ? "Supply depots: automatic" : "Supply depots: " + plan.links().supplies()));
        this.preview.add(Component.literal(plan.links().outputs().isEmpty() ? "Output depots: automatic" : "Output depots: " + plan.links().outputs()));
        this.preview.add(Component.literal(plan.links().stations().isEmpty() ? "Stations: automatic" : "Permitted stations: " + plan.links().stations()));
        if (plan.route() != null) this.preview.add(Component.literal("Shared route: " + plan.route().mode() + " / " + plan.route().points()));
    }

    SettlementWorkerScreen previous() { return this.previous; }

    @Override
    protected void init() {
        int width = Math.min(500, this.width - 24);
        int left = (this.width - width) / 2;
        addRenderableWidget(new Preview(left, width));
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(left, this.height - 28, 100, 20).build());
        this.adopt = addRenderableWidget(Button.builder(Component.literal("Adopt shared plan"), ignored -> {
            this.minecraft.setScreen(this.previous);
            this.previous.savePlan();
        }).bounds(left + width - 140, this.height - 28, 140, 20).build());
        tick();
    }

    @Override
    public void tick() {
        this.adopt.active = this.previous.parent().permits(com.jvn.villagerretaliation.settlement.SettlementPermission.STAFF)
                && !this.previous.parent().submitting();
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.previous); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
    }

    private final class Preview extends ObjectSelectionList<Row> {
        private Preview(int left, int width) {
            super(SettlementWorksiteAdoptionScreen.this.minecraft, width, SettlementWorksiteAdoptionScreen.this.height - 76, 38, 13);
            setX(left);
            for (var paragraph : preview) {
                for (var line : font.split(paragraph, width - 20)) addEntry(new Row(line, paragraph));
                addEntry(new Row(net.minecraft.util.FormattedCharSequence.EMPTY, Component.empty()));
            }
        }

        @Override
        public int getRowWidth() { return getWidth() - 12; }

        @Override
        protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final net.minecraft.util.FormattedCharSequence text;
        private final Component narration;

        private Row(net.minecraft.util.FormattedCharSequence text, Component narration) {
            this.text = text;
            this.narration = narration;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, this.text, left + 3, top + 2, 0xDDDDDD);
        }

        @Override
        public Component getNarration() { return this.narration; }
    }
}
