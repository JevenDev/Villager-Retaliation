package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignAddon;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class CampaignAddonScreen extends Screen {
    private static final int ROW_HEIGHT = 48;
    private static final int ROW_TOP = 76;
    private static final ResourceLocation UNKNOWN_ICON = ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png");
    private final WorldCampaignCatalog catalog;
    private final WorldCampaign campaign;
    private final List<ResourceLocation> enabled;
    private final Map<ResourceLocation, CampaignMedia> icons;
    private final WorldCampaignSelection.Selection previous;
    private final Consumer<List<ResourceLocation>> apply;
    private final Runnable cancel;
    private final List<AddonRow> rows = new ArrayList<>();
    private List<ResourceLocation> available = List.of();
    private List<ResourceLocation> active = List.of();
    private EditBox search;
    private Button done;
    private String query = "";
    private int availableScroll;
    private int enabledScroll;
    private int left;
    private int columnWidth;
    private int visibleRows;
    private AddonRow grabbed;
    private double grabX;
    private double grabY;
    private boolean draggingRow;
    private long scrolledAt;
    private String notice;
    private long noticeUntil;

    CampaignAddonScreen(WorldCampaignCatalog catalog, WorldCampaign campaign, List<ResourceLocation> enabled,
                        Map<ResourceLocation, CampaignMedia> icons, WorldCampaignSelection.Selection previous,
                        Consumer<List<ResourceLocation>> apply, Runnable cancel) {
        super(Component.translatable("screen.villagerretaliation.addons.title"));
        this.catalog = catalog;
        this.campaign = campaign;
        this.enabled = new ArrayList<>(enabled);
        this.icons = icons;
        this.previous = previous;
        this.apply = apply;
        this.cancel = cancel;
    }

    @Override
    protected void init() {
        int totalWidth = Math.min(760, width - 24);
        left = (width - totalWidth) / 2;
        columnWidth = (totalWidth - 12) / 2;
        visibleRows = Math.max(1, (height - ROW_TOP - 44) / ROW_HEIGHT);
        grabbed = null;
        draggingRow = false;
        rows.clear();
        search = addRenderableWidget(new EditBox(font, left, 36, totalWidth, 20,
                Component.translatable("screen.villagerretaliation.addons.search")));
        search.setHint(Component.translatable("screen.villagerretaliation.addons.search"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            notice = null;
            availableScroll = 0;
            refreshRows();
        });
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 - 154, height - 26, 150, 20).build());
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> apply.accept(List.copyOf(enabled)))
                .bounds(width / 2 + 4, height - 26, 150, 20).build());
        refreshRows();
    }

    private String problem(List<ResourceLocation> selection) {
        if (selection.size() > WorldCampaignAddon.MAX_SELECTED) return "At most " + WorldCampaignAddon.MAX_SELECTED + " addons can be enabled";
        if (previous != null) {
            String issue = catalog.addonChangeProblem(previous, selection);
            if (issue != null) return issue;
        }
        for (var id : selection) {
            String issue = catalog.addonProblem(id, campaign.id(), selection);
            if (issue != null) return issue;
        }
        return null;
    }

    private List<ResourceLocation> toggled(ResourceLocation id) {
        var result = new ArrayList<>(enabled);
        if (!result.remove(id)) result.add(id);
        return result;
    }

    private void refreshRows() {
        if (rows.contains(getFocused())) setFocused(null);
        rows.forEach(this::removeWidget);
        rows.clear();
        String filter = query.toLowerCase(Locale.ROOT).trim();
        available = catalog.addonChoices().stream().filter(addon -> !enabled.contains(addon.id()))
                .filter(addon -> (addon.name() + " " + addon.id() + " " + String.join(" ", addon.authors()))
                        .toLowerCase(Locale.ROOT).contains(filter))
                .sorted(java.util.Comparator.comparing(addon -> problem(toggled(addon.id())) != null))
                .map(WorldCampaignAddon::id).toList();
        active = enabled.reversed();
        availableScroll = Math.clamp(availableScroll, 0, Math.max(0, available.size() - visibleRows));
        enabledScroll = Math.clamp(enabledScroll, 0, Math.max(0, active.size() - visibleRows));
        addRows(available, availableScroll, left, false);
        addRows(active, enabledScroll, right(), true);
        String issue = problem(enabled);
        done.active = issue == null;
        done.setTooltip(issue == null ? null : Tooltip.create(Component.literal(issue)));
    }

    private int right() { return left + columnWidth + 12; }
    private int bottom() { return ROW_TOP + visibleRows * ROW_HEIGHT; }

    private void addRows(List<ResourceLocation> ids, int scroll, int x, boolean selected) {
        for (int row = 0; row < visibleRows && scroll + row < ids.size(); row++) {
            rows.add(addRenderableWidget(new AddonRow(x, ROW_TOP + row * ROW_HEIGHT, ids.get(scroll + row), selected)));
        }
    }

    private void reject(String issue) {
        notice = issue;
        noticeUntil = Util.getMillis() + 4000;
        minecraft.getNarrator().sayNow(Component.literal(issue));
    }

    private String removalProblem(List<ResourceLocation> next) {
        return previous == null ? null : catalog.addonChangeProblem(previous, next);
    }

    private void toggle(ResourceLocation id) {
        List<ResourceLocation> next = toggled(id);
        String issue = enabled.contains(id) ? removalProblem(next) : problem(next);
        if (issue != null) { reject(issue); return; }
        enabled.clear();
        enabled.addAll(next);
        notice = null;
        refreshRows();
    }

    private List<ResourceLocation> moved(ResourceLocation id, int direction) {
        var result = new ArrayList<>(enabled);
        int index = result.indexOf(id);
        int destination = index + direction;
        if (index < 0 || destination < 0 || destination >= result.size()) return null;
        Collections.swap(result, index, destination);
        return result;
    }

    private boolean canMove(ResourceLocation id, int direction) {
        var next = moved(id, direction);
        return next != null && problem(next) == null;
    }

    private void move(ResourceLocation id, int direction) {
        var next = moved(id, direction);
        if (next == null) return;
        String issue = problem(next);
        if (issue != null) { reject(issue); return; }
        enabled.clear();
        enabled.addAll(next);
        notice = null;
        refreshRows();
        rows.stream().filter(row -> row.id.equals(id)).findFirst().ifPresent(this::setFocused);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (draggingRow && Util.getMillis() - scrolledAt > 140 && mouseX >= left && mouseX < right() + columnWidth) {
            int delta = mouseY < ROW_TOP + 12 ? -1 : mouseY > bottom() - 12 ? 1 : 0;
            if (delta != 0) {
                if (mouseX >= right()) enabledScroll += delta;
                else availableScroll += delta;
                scrolledAt = Util.getMillis();
                refreshRows();
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xffffff);
        String subtitle = Component.translatable("screen.villagerretaliation.addons.campaign", campaign.name()).getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(subtitle, width - 24), width / 2, 22, 0xc4c8ce);
        String hint = notice != null && Util.getMillis() < noticeUntil ? notice
                : Component.translatable("screen.villagerretaliation.addons.priority").getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(hint, width - 16), width / 2, height - 39, 0xc4c8ce);
        if (draggingRow && grabbed != null) {
            List<ResourceLocation> next = dropSelection(mouseX, mouseY);
            String issue = next == null ? "" : problem(next);
            if (mouseX >= right() && mouseX < right() + columnWidth && next != null) {
                int slot = Math.clamp((int) Math.round((mouseY - ROW_TOP) / (double) ROW_HEIGHT), 0, visibleRows);
                int y = ROW_TOP + slot * ROW_HEIGHT;
                graphics.fill(right(), y - 1, right() + columnWidth, y + 1, issue == null ? 0xffd5c18b : 0xffcc8877);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.fill(mouseX + 8, mouseY + 8, mouseX + 36 + Math.min(150, font.width(grabbed.getMessage())), mouseY + 38, 0xe020242b);
            icon(graphics, grabbed.id, mouseX + 11, mouseY + 11, 24);
            graphics.drawString(font, font.plainSubstrByWidth(grabbed.getMessage().getString(), 146), mouseX + 39, mouseY + 19, 0xffffff);
            graphics.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        column(graphics, left, available, availableScroll, false);
        column(graphics, right(), active, enabledScroll, true);
    }

    private void column(GuiGraphics graphics, int x, List<ResourceLocation> ids, int scroll, boolean selected) {
        graphics.drawString(font, Component.translatable(selected ? "screen.villagerretaliation.addons.enabled"
                : "screen.villagerretaliation.addons.available", ids.size()), x + 2, 63, 0xffffff);
        if (ids.isEmpty()) graphics.drawWordWrap(font, Component.translatable(selected
                ? "screen.villagerretaliation.addons.none_enabled" : "screen.villagerretaliation.addons.none_available"), x + 8, ROW_TOP + 10, columnWidth - 16, 0xb8bfc8);
        if (ids.size() > visibleRows) {
            int thumb = Math.max(10, (bottom() - ROW_TOP) * visibleRows / ids.size());
            int top = ROW_TOP + (bottom() - ROW_TOP - thumb) * scroll / (ids.size() - visibleRows);
            graphics.fill(x + columnWidth - 3, top, x + columnWidth - 1, top + thumb, 0xffa5adb8);
        }
    }

    private void icon(GuiGraphics graphics, ResourceLocation id, int x, int y, int size) {
        CampaignMedia icon = icons.get(id);
        if (icon != null) icon.render(graphics, x, y, size, size);
        else graphics.blit(UNKNOWN_ICON, x, y, 0, 0, size, size, size, size);
    }

    private static void control(GuiGraphics graphics, String name, boolean highlighted, int x, int y) {
        graphics.blitSprite(ResourceLocation.withDefaultNamespace("transferable_list/" + name + (highlighted ? "_highlighted" : "")), x, y, 32, 32);
    }

    private final class AddonRow extends Button {
        private final ResourceLocation id;
        private final WorldCampaignAddon addon;
        private final boolean selected;
        private final String issue;
        private final Tooltip details;

        private AddonRow(int x, int y, ResourceLocation id, boolean selected) {
            super(x, y, columnWidth - 6, ROW_HEIGHT - 4, Component.literal(catalog.addons().containsKey(id)
                    ? catalog.addons().get(id).name() : id.toString()), button -> CampaignAddonScreen.this.toggle(id), DEFAULT_NARRATION);
            this.id = id;
            this.addon = catalog.addons().get(id);
            this.selected = selected;
            this.issue = selected ? catalog.addonProblem(id, campaign.id(), enabled) : problem(toggled(id));
            Component restriction = Component.translatable(addon != null && addon.allowExistingWorlds()
                    ? "screen.villagerretaliation.addons.existing_allowed" : "screen.villagerretaliation.addons.new_world_only");
            details = Tooltip.create(Component.literal(getMessage().getString() + "\n" + id
                    + (addon == null ? "" : "\n" + String.join(", ", addon.authors()) + "\n\n" + addon.description()))
                    .append("\n\n").append(restriction).append(issue == null ? "" : "\n" + issue));
            setTooltip(details);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            setTooltip(draggingRow ? null : details);
            graphics.fill(getX(), getY(), getRight(), getBottom(), isHoveredOrFocused() ? 0xc03b414b : 0x9020242b);
            graphics.fill(getX(), getY(), getX() + 2, getBottom(), selected ? 0xffbaaa7b : 0xff555d68);
            int x = getX() + 8;
            int y = getY() + 7;
            icon(graphics, id, x, y, 32);
            if (isHoveredOrFocused() && !draggingRow) {
                graphics.fill(x, y, x + 32, y + 32, 0xa0000000);
                int localX = mouseX - x;
                int localY = mouseY - y;
                if (!selected && issue == null) control(graphics, "select", localX >= 0 && localX < 32, x, y);
                if (selected) {
                    if (previous == null || !previous.addons().contains(id)) control(graphics, "unselect", localX >= 0 && localX < 16, x, y);
                    if (canMove(id, 1)) control(graphics, "move_up", localX >= 16 && localX < 32 && localY < 16, x, y);
                    if (canMove(id, -1)) control(graphics, "move_down", localX >= 16 && localX < 32 && localY >= 16, x, y);
                }
            }
            int textWidth = getWidth() - 52;
            graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), textWidth), getX() + 48, getY() + 5, 0xffffff);
            graphics.drawString(font, font.plainSubstrByWidth(addon == null ? "" : addon.description().replace('\n', ' '), textWidth), getX() + 48, getY() + 18, 0xb8bfc8);
            String status = issue == null ? Component.translatable(selected ? "screen.villagerretaliation.addons.active"
                    : addon != null && addon.allowExistingWorlds() ? "screen.villagerretaliation.addons.existing_allowed"
                    : "screen.villagerretaliation.addons.new_world_only").getString() : issue;
            graphics.drawString(font, font.plainSubstrByWidth(status, textWidth), getX() + 48, getY() + 31, issue == null ? 0xa9c6a0 : 0xe2a58d);
        }

        private void iconClick(double mouseX, double mouseY) {
            double x = mouseX - getX() - 8;
            double y = mouseY - getY() - 7;
            if (x < 0 || x >= 32 || y < 0 || y >= 32) return;
            if (!selected || x < 16) {
                if (issue != null && !selected) reject(issue);
                else toggle(id);
            } else move(id, y < 16 ? 1 : -1);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (AddonRow row : rows) if (row.isMouseOver(mouseX, mouseY)) {
                grabbed = row;
                grabX = mouseX;
                grabY = mouseY;
                draggingRow = false;
                setFocused(row);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (grabbed != null && button == 0) {
            if (Math.hypot(mouseX - grabX, mouseY - grabY) > 4) draggingRow = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private List<ResourceLocation> dropSelection(double mouseX, double mouseY) {
        if (mouseY < ROW_TOP || mouseY > bottom()) return null;
        var result = new ArrayList<>(enabled);
        if (mouseX >= left && mouseX < left + columnWidth) {
            result.remove(grabbed.id);
            return result;
        }
        if (mouseX < right() || mouseX >= right() + columnWidth) return null;
        int slot = Math.clamp(enabledScroll + (int) Math.round((mouseY - ROW_TOP) / ROW_HEIGHT), 0, enabled.size());
        int old = active.indexOf(grabbed.id);
        result.remove(grabbed.id);
        if (old >= 0 && old < slot) slot--;
        result.add(result.size() - Math.clamp(slot, 0, result.size()), grabbed.id);
        return result;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (grabbed != null && button == 0) {
            if (draggingRow) {
                var next = dropSelection(mouseX, mouseY);
                if (next != null) {
                    boolean removing = enabled.contains(grabbed.id) && !next.contains(grabbed.id);
                    String issue = removing ? removalProblem(next) : problem(next);
                    if (issue != null) reject(issue);
                    else { enabled.clear(); enabled.addAll(next); notice = null; }
                }
            } else grabbed.iconClick(mouseX, mouseY);
            grabbed = null;
            draggingRow = false;
            refreshRows();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (hasShiftDown() && getFocused() instanceof AddonRow row && row.selected
                && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            move(row.id, key == GLFW.GLFW_KEY_UP ? 1 : -1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            int delta = key == GLFW.GLFW_KEY_PAGE_DOWN ? visibleRows : -visibleRows;
            boolean right = getFocused() instanceof AddonRow row && row.selected;
            if (right) enabledScroll += delta;
            else availableScroll += delta;
            refreshRows();
            rows.stream().filter(row -> row.selected == right).findFirst().ifPresent(this::setFocused);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= ROW_TOP && mouseY < bottom()) {
            int delta = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
            if (mouseX >= left && mouseX < left + columnWidth) availableScroll += delta;
            else if (mouseX >= right() && mouseX < right() + columnWidth) enabledScroll += delta;
            else return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            refreshRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() { cancel.run(); }
}
