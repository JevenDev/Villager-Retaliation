package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class SettlementPolicyStepScreen extends VillagerWindowScreen implements SettlementPolicyClient.Child {
    private final SettlementPoliciesScreen previous;
    private final UUID id;
    private SettlementSupplyPolicy.StepKind kind = SettlementSupplyPolicy.StepKind.PRODUCTION;
    private UUID area;
    private HiredProductionRecipe recipe;
    private ItemStack sample = ItemStack.EMPTY;
    private BlockPos source;
    private BlockPos destination;
    private boolean initialized;
    private boolean loading;
    private final java.util.Map<String, Button> buttons = new java.util.HashMap<>();

    SettlementPolicyStepScreen(SettlementPoliciesScreen previous, SettlementSupplyPolicy.Step step) {
        super(SettlementPolicyClient.text("step_editor"));
        this.previous = previous;
        this.id = step == null ? UUID.randomUUID() : step.id();
        if (step != null) {
            this.kind = step.kind();
            this.area = step.area();
            this.recipe = step.recipe();
            this.sample = step.sample();
            this.source = step.source();
            this.destination = step.destination();
        }
    }

    @Override public Screen previousPolicy() { return this.previous; }

    @Override protected void init() {
        initWindow(380, 274);
        this.buttons.clear();
        button("kind", 24, () -> {
            this.kind = SettlementSupplyPolicy.StepKind.values()[(this.kind.ordinal() + 1) % SettlementSupplyPolicy.StepKind.values().length];
            this.area = null;
            this.recipe = null;
            this.sample = ItemStack.EMPTY;
            this.source = null;
            this.destination = null;
            update();
        });
        button("worksite", 48, this::worksite);
        button("product", 72, this::product);
        button("source", 96, () -> storage(true));
        button("destination", 120, () -> storage(false));
        this.buttons.put("save", addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text("select"), ignored -> {
            var step = value();
            if (step != null) {
                this.minecraft.setScreen(this.previous);
                this.previous.step(step);
            }
        }).bounds(this.left + this.panelWidth - 112, this.top + this.panelHeight - 24, 112, 20).build()));
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 24, 84, 20).build());
        update();
        if (!this.initialized) {
            this.initialized = true;
            if (this.area != null) loadStorage();
        }
    }

    private void button(String key, int y, Runnable action) {
        this.buttons.put(key, addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text(key), ignored -> action.run())
                .bounds(this.left, this.top + y, this.panelWidth, 20).build()));
    }

    private void worksite() {
        if (this.previous.snapshot() == null) return;
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, SettlementPolicyClient.text("choose_worksite"), query ->
                this.previous.snapshot().worksites().stream().filter(site -> supports(site.type())).map(site ->
                        new SettlementPolicyChoiceScreen.Choice(Component.literal(site.name()), Component.literal(site.position().toShortString()),
                                ItemStack.EMPTY, () -> {
                            this.area = site.id();
                            this.recipe = null;
                            this.source = null;
                            this.destination = null;
                            loadStorage();
                        })).toList()));
    }

    private boolean supports(SettlementAreaType type) {
        return switch (this.kind) {
            case DELIVERY -> type == SettlementAreaType.DEPOT;
            case PRODUCTION -> type.role() != null && com.jvn.villagerretaliation.interaction.work.production.ProductionManagementService.supports(type.role());
            case GATHER -> List.of(SettlementAreaType.FARM, SettlementAreaType.FISHERY, SettlementAreaType.HUNTING_GROUNDS,
                    SettlementAreaType.FORESTRY, SettlementAreaType.MINE, SettlementAreaType.ANIMAL_PEN).contains(type);
        };
    }

    private void loadStorage() {
        this.loading = true;
        this.previous.refresh(this.area, () -> { this.loading = false; update(); });
        update();
    }

    private void product() {
        if (this.area == null || this.previous.snapshot() == null) return;
        var site = this.previous.snapshot().worksites().stream().filter(value -> value.id().equals(this.area)).findFirst().orElse(null);
        if (site == null) return;
        if (this.kind == SettlementSupplyPolicy.StepKind.PRODUCTION)
            this.minecraft.setScreen(new com.jvn.villagerretaliation.client.inventory.ProductionRecipeScreen(this, site.type().role(), this.recipe,
                    recipe -> { this.recipe = recipe; update(); }));
        else this.minecraft.setScreen(SettlementPolicyChoiceScreen.items(this, this.sample.isEmpty() ? List.of() : List.of(this.sample),
                sample -> { this.sample = sample; update(); }));
    }

    private void storage(boolean input) {
        if (this.previous.snapshot() == null || this.loading) return;
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, SettlementPolicyClient.text(input ? "choose_source" : "choose_destination"), query ->
                this.previous.snapshot().storages().stream().filter(storage -> input ? storage.source() : storage.destination())
                        .filter(storage -> input || !storage.position().equals(this.source)).map(storage ->
                                new SettlementPolicyChoiceScreen.Choice(Component.literal(storage.name()),
                                        SettlementPolicyClient.text(storage.known() ? "observed" : "needs_observation"), ItemStack.EMPTY, () -> {
                                    if (input) this.source = storage.position();
                                    else this.destination = storage.position();
                                    update();
                                })).toList()));
    }

    private SettlementSupplyPolicy.Step value() {
        if (this.area == null || this.destination == null || this.kind == SettlementSupplyPolicy.StepKind.PRODUCTION && this.recipe == null
                || this.kind != SettlementSupplyPolicy.StepKind.PRODUCTION && this.sample.isEmpty()
                || this.kind == SettlementSupplyPolicy.StepKind.DELIVERY && (this.source == null || this.source.equals(this.destination))) return null;
        return new SettlementSupplyPolicy.Step(this.id, this.kind, this.area,
                this.kind == SettlementSupplyPolicy.StepKind.PRODUCTION ? this.recipe : null,
                this.kind == SettlementSupplyPolicy.StepKind.PRODUCTION ? ItemStack.EMPTY : this.sample,
                this.kind == SettlementSupplyPolicy.StepKind.DELIVERY ? this.source : null, this.destination);
    }

    private void update() {
        if (this.buttons.isEmpty()) return;
        this.buttons.get("kind").setMessage(SettlementPolicyClient.text("kind." + this.kind.name().toLowerCase(java.util.Locale.ROOT)));
        String name = this.previous.snapshot() == null ? "" : this.previous.snapshot().worksites().stream().filter(site -> site.id().equals(this.area))
                .map(com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.Worksite::name).findFirst().orElse("");
        this.buttons.get("worksite").setMessage(name.isEmpty() ? SettlementPolicyClient.text("choose_worksite") : Component.literal(name));
        var output = this.kind == SettlementSupplyPolicy.StepKind.PRODUCTION && this.recipe != null ? this.recipe.output(this.minecraft.level) : this.sample;
        this.buttons.get("product").setMessage(output.isEmpty() ? SettlementPolicyClient.text(this.kind == SettlementSupplyPolicy.StepKind.PRODUCTION ? "choose_recipe" : "choose_item")
                : output.getHoverName());
        this.buttons.get("product").active = this.area != null;
        this.buttons.get("source").visible = this.kind == SettlementSupplyPolicy.StepKind.DELIVERY;
        this.buttons.get("source").active = this.area != null && !this.loading;
        this.buttons.get("source").setMessage(this.source == null ? SettlementPolicyClient.text("choose_source")
                : SettlementPolicyClient.text("source_value", this.source.toShortString()));
        this.buttons.get("destination").active = this.area != null && !this.loading;
        this.buttons.get("destination").setMessage(this.destination == null ? SettlementPolicyClient.text("choose_destination")
                : SettlementPolicyClient.text("destination_value", this.destination.toShortString()));
        this.buttons.get("save").active = value() != null && !this.loading;
    }

    @Override public void tick() { this.previous.tick(); update(); }
    @Override public void onClose() { this.previous.changed(); this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        var help = SettlementPolicyClient.text("help." + this.kind.name().toLowerCase(java.util.Locale.ROOT));
        int y = this.top + 154;
        for (var line : this.font.split(help, this.panelWidth)) {
            graphics.drawString(this.font, line, this.left, y, 0xCCCCCC);
            y += 11;
        }
    }
}
