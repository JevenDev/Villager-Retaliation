package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class SettlementBookmarkScreen extends VillagerWindowScreen {
    private final SettlementViewScreen view;
    private final SettlementViewPreferences preferences;
    private String name = "";
    private int selected = -1;
    private int page;
    private Button save;
    private Button rename;
    private Button remove;

    SettlementBookmarkScreen(SettlementViewScreen view, SettlementViewPreferences preferences) {
        super(Component.literal("Camera bookmarks"));
        this.view = view;
        this.preferences = preferences;
    }

    SettlementManagementScreen parent() { return this.view.parent(); }

    @Override
    protected void init() {
        initWindow(320, 264);
        int rows = Math.max(1, (this.panelHeight - 152) / 22);
        this.page = Math.clamp(this.page, 0, Math.max(0, (this.preferences.bookmarks.size() - 1) / rows));
        for (int i = this.page * rows; i < Math.min(this.preferences.bookmarks.size(), (this.page + 1) * rows); i++) {
            int index = i;
            var bookmark = this.preferences.bookmarks.get(i);
            addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(bookmark.name()), ignored -> {
                this.selected = index;
                this.name = bookmark.name();
                rebuildWidgets();
            }).bounds(this.left, this.top + 26 + (i % rows) * 22, this.panelWidth - 48, 20).build());
            addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Go"), ignored -> {
                this.view.recallBookmark(bookmark);
                onClose();
            }).bounds(this.left + this.panelWidth - 44, this.top + 26 + (i % rows) * 22, 44, 20).build());
        }
        var previous = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Previous"), ignored -> { this.page--; rebuildWidgets(); })
                .bounds(this.left, this.top + this.panelHeight - 122, (this.panelWidth - 4) / 2, 20).build());
        previous.active = this.page > 0;
        var next = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Next"), ignored -> { this.page++; rebuildWidgets(); })
                .bounds(this.left + (this.panelWidth + 4) / 2, this.top + this.panelHeight - 122, (this.panelWidth - 4) / 2, 20).build());
        next.active = (this.page + 1) * rows < this.preferences.bookmarks.size();
        var input = addRenderableWidget(new EditBox(this.font, this.left, this.top + this.panelHeight - 96, this.panelWidth, 20, Component.literal("Bookmark name")));
        input.setMaxLength(40);
        input.setHint(Component.literal("Bookmark name"));
        input.setValue(this.name);
        input.setResponder(value -> { this.name = value; updateActions(); });
        int third = (this.panelWidth - 8) / 3;
        this.save = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Save current"), ignored -> {
            this.preferences.bookmarks.add(this.view.bookmark(this.name));
            this.selected = this.preferences.bookmarks.size() - 1;
            this.view.savePreferences();
            rebuildWidgets();
        }).bounds(this.left, this.top + this.panelHeight - 70, third, 20).build());
        this.rename = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Rename"), ignored -> {
            var old = this.preferences.bookmarks.get(this.selected);
            this.preferences.bookmarks.set(this.selected, new SettlementViewPreferences.Bookmark(this.name, old.x(), old.z(), old.height(), old.yaw()));
            this.view.savePreferences();
            rebuildWidgets();
        }).bounds(this.left + third + 4, this.top + this.panelHeight - 70, third, 20).build());
        this.remove = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Remove"), ignored -> {
            this.preferences.bookmarks.remove(this.selected);
            this.selected = -1;
            this.view.savePreferences();
            rebuildWidgets();
        }).bounds(this.left + (third + 4) * 2, this.top + this.panelHeight - 70, third, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 20, this.panelWidth, 20).build());
        updateActions();
        setInitialFocus(input);
    }

    private void updateActions() {
        if (this.save == null) return;
        String name = this.name.strip();
        boolean duplicate = this.preferences.bookmarks.stream().anyMatch(bookmark -> bookmark.name().equalsIgnoreCase(name));
        this.save.active = !name.isEmpty() && !duplicate && this.preferences.bookmarks.size() < SettlementViewPreferences.MAX_BOOKMARKS;
        this.rename.active = this.selected >= 0 && !name.isEmpty() && !duplicate;
        this.remove.active = this.selected >= 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top + 4, 0xFFFFFF);
        if (this.preferences.bookmarks.isEmpty()) graphics.drawWordWrap(this.font, Component.literal("Save this camera position to return here later."), this.left, this.top + 30, this.panelWidth, 0xBBBBBB);
        graphics.drawString(this.font, this.preferences.bookmarks.size() + " / " + SettlementViewPreferences.MAX_BOOKMARKS + " bookmarks", this.left, this.top + this.panelHeight - 44, 0xBBBBBB);
    }

    @Override
    public void onClose() { SettlementViewClient.resume(parent()); }

    @Override
    public boolean isPauseScreen() { return false; }
}
