package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class SettlementItemPickerScreen extends VillagerWindowScreen {
    private final SettlementEditScreen parent;
    private final Consumer<ResourceLocation> selected;
    private final List<Item> items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList();
    private String query = "";
    private ItemsList list;

    public SettlementItemPickerScreen(SettlementEditScreen parent, Consumer<ResourceLocation> selected) {
        super(SettlementManagementScreen.text("choose_item"));
        this.parent = parent;
        this.selected = selected;
    }

    public SettlementEditScreen parent() {
        return this.parent;
    }

    @Override
    protected void init() {
        initWindow(400, 320);
        int width = this.panelWidth;
        int left = this.left;
        EditBox search = new EditBox(this.font, left, this.top + 20, width, 20, SettlementManagementScreen.text("search"));
        search.setMaxLength(64);
        search.setValue(this.query);
        search.setHint(SettlementManagementScreen.text("search"));
        addRenderableWidget(search);
        this.list = addRenderableWidget(new ItemsList(left, width));
        search.setResponder(value -> {
            this.query = value;
            this.list.refresh();
        });
        this.list.refresh();
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left, this.top + this.panelHeight - 16, width, 20).build());
        setInitialFocus(search);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER && this.list.isFocused() && this.list.getSelected() != null) {
            this.selected.accept(BuiltInRegistries.ITEM.getKey(this.list.getSelected().stack.getItem()));
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
    }

    private final class ItemsList extends VillagerSelectionList<ItemRow> {
        private ItemsList(int x, int width) {
            super(SettlementItemPickerScreen.this.minecraft, width, Math.max(32, panelHeight - 70), top + 46, 24);
            setX(x);
        }

        private void refresh() {
            clearEntries();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            items.stream().filter(item -> item.getDescription().getString().toLowerCase(Locale.ROOT).contains(needle)
                            || BuiltInRegistries.ITEM.getKey(item).toString().contains(needle))
                    .sorted(java.util.Comparator.comparing(item -> item.getDescription().getString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(item -> addEntry(new ItemRow(item)));
            setScrollAmount(0.0D);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - 6;
        }
    }

    private final class ItemRow extends ObjectSelectionList.Entry<ItemRow> {
        private final ItemStack stack;

        private ItemRow(Item item) {
            this.stack = new ItemStack(item);
        }

        @Override
        public Component getNarration() {
            return this.stack.getHoverName();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            selected.accept(BuiltInRegistries.ITEM.getKey(this.stack.getItem()));
            return true;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.renderItem(this.stack, x + 2, y + 2);
            graphics.drawString(font, font.plainSubstrByWidth(this.stack.getHoverName().getString(), width - 28), x + 24, y + 6, 0xFFFFFF);
        }
    }
}
