package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementExchangePayload;
import com.jvn.villagerretaliation.network.SettlementExchangeRequest;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementExchangeService.Action;
import com.jvn.villagerretaliation.settlement.SettlementExchangeTerms;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementExchangeScreen extends VillagerWindowScreen {
    private final Screen parent;
    private final UUID village;
    private UUID target, selection, draftId = UUID.randomUUID();
    private CompoundTag snapshot = new CompoundTag(), selected;
    private final List<ItemStack> firstItems = new ArrayList<>(), secondItems = new ArrayList<>();
    private String firstFunds = "0", secondFunds = "0";
    private int sequence, page, scroll;
    private boolean editing, reviewing, countering, firstSends = true, submitting;
    private long refresh, draftRevision;
    private Component feedback = Component.empty(), hover;
    private final List<Button> mutations = new ArrayList<>();
    private final LinkedHashMap<AbstractWidget, Integer> bodyWidgets = new LinkedHashMap<>();

    public SettlementExchangeScreen(Screen parent, UUID village, UUID target) {
        super(text("title"));
        this.parent = parent; this.village = village; this.target = target;
    }

    public static Component text(String key, Object... args) {
        if (key.isEmpty()) return Component.empty();
        String exchange = "villagerretaliation.gui.exchange." + key;
        if (net.minecraft.client.resources.language.I18n.exists(exchange)) return Component.translatable(exchange, args);
        String aid = "villagerretaliation.gui.aid.blocker." + key;
        return net.minecraft.client.resources.language.I18n.exists(aid) ? Component.translatable(aid, args) : Component.literal(key.replace('_', ' '));
    }

    public static void receive(SettlementExchangePayload payload) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof SettlementExchangeScreen screen) screen.accept(payload);
        else if (current instanceof SettlementTradePickerScreen picker && picker.previous() instanceof SettlementExchangeScreen screen) screen.accept(payload);
    }

    private void accept(SettlementExchangePayload payload) {
        if (payload.sequence() != sequence || !payload.data().hasUUID("Village") || !payload.data().getUUID("Village").equals(village)) return;
        snapshot = payload.data(); submitting = false; page = snapshot.getInt("Page");
        if (!snapshot.getString("Message").isEmpty()) feedback = text(snapshot.getString("Message"));
        if (snapshot.hasUUID("Selected")) { selection = snapshot.getUUID("Selected"); editing = reviewing = false; scroll = 0; }
        CompoundTag previous = selected;
        selected = null;
        for (var raw : snapshot.getList("Rows", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            if (selection != null && selection.equals(row.getUUID("Id"))) selected = row;
        }
        if (editing && countering && selected == null) selected = previous;
        if (minecraft.screen == this && (!editing || !(getFocused() instanceof EditBox))) rebuildWidgets();
    }

    @Override protected void init() {
        initWindow(660, 440, false);
        mutations.clear(); bodyWidgets.clear();
        int bottom = top + panelHeight - 20;
        button(Component.translatable("gui.back"), left, bottom, 80, this::back, false, "Return to the previous step");
        if (editing) editor();
        else if (selected != null) details();
        else overview();
        arrange();
    }

    private Button button(Component label, int x, int y, int width, Runnable action, boolean mutation, String help) {
        var button = addRenderableWidget(VillagerUi.buttonBuilder(label, ignored -> action.run()).bounds(x, y, width, 20).build());
        button.setTooltip(Tooltip.create(Component.literal(mutation && !snapshot.getBoolean("Authorized") ? text("forbidden").getString() : help)));
        if (mutation) {
            mutations.add(button);
            button.active = snapshot.getBoolean("Authorized") && !submitting;
        }
        return button;
    }

    private Button bodyButton(Component label, int x, int y, int width, Runnable action, boolean mutation, String help) {
        var button = button(label, x, bodyTop() + y, width, action, mutation, help);
        bodyWidgets.put(button, y);
        return button;
    }

    private int bodyTop() { return top + 48; }
    private int bodyBottom() { return top + panelHeight - 48; }
    private int columnWidth() { return (panelWidth - 18) / 2; }

    private int bundleOffset() {
        int count = Math.max(firstItems.size(), secondItems.size());
        if (!editing && selected != null) {
            var terms = SettlementExchangeTerms.load(selected.getCompound("Terms"), minecraft.level.registryAccess());
            count = Math.max(terms.first().items().size(), terms.second().items().size());
        }
        int columns = Math.max(1, (columnWidth() - 16) / 20);
        return Math.max(40, (count + columns - 1) / columns * 20) - 90;
    }

    private void overview() {
        button(text("new"), left, top + 23, 105, this::newOffer, true, "Choose a settlement and the goods or funds to exchange");
        button(Component.literal("Saved routes"), left + 111, top + 23, 105, () ->
                minecraft.setScreen(new com.jvn.villagerretaliation.client.exploration.SettlementRoutesScreen(this)), false,
                "Plan reusable paths between settlements");
        addRenderableWidget(new Offers());
        int bottom = top + panelHeight - 20;
        button(Component.literal("<"), left + panelWidth - 60, bottom, 28, () -> { page = Math.max(0, page - 1); send(Action.VIEW); }, false, "Previous offers")
                .active = page > 0 && !submitting;
        button(Component.literal(">"), left + panelWidth - 28, bottom, 28, () -> { page++; send(Action.VIEW); }, false, "Next offers")
                .active = (page + 1) * 8 < snapshot.getInt("Total") && !submitting;
    }

    private void newOffer() {
        editing = true; reviewing = countering = false; scroll = 0; feedback = Component.empty();
        draftId = UUID.randomUUID(); firstItems.clear(); secondItems.clear(); firstFunds = secondFunds = "0"; firstSends = true;
        rebuildWidgets();
        chooseTarget();
    }

    private void chooseTarget() {
        var choices = new ArrayList<SettlementTradePickerScreen.Choice>();
        for (var raw : snapshot.getList("Contacts", Tag.TAG_COMPOUND)) {
            var contact = (CompoundTag) raw;
            choices.add(new SettlementTradePickerScreen.Choice(Component.literal(contact.getString("Name")),
                    Component.literal("Trade goods, funds, or both"), new ItemStack(Items.EMERALD), 1, count -> {
                UUID next = contact.getUUID("Id");
                if (!next.equals(target)) { secondItems.clear(); secondFunds = "0"; }
                target = next; send(Action.VIEW); rebuildWidgets();
            }));
        }
        minecraft.setScreen(new SettlementTradePickerScreen(this, text("choose_settlement"), choices, false));
    }

    private String name(boolean first) {
        return countering && selected != null ? selected.getString(first ? "FirstName" : "SecondName")
                : first ? text("your_settlement").getString() : targetName();
    }

    private String targetNeeds() {
        var needs = new ArrayList<String>();
        for (var raw : snapshot.getList("Needs", Tag.TAG_COMPOUND)) {
            var need = (CompoundTag) raw;
            needs.add(need.getString("Name") + " (" + need.getLong("Missing") + ")");
        }
        return "Choose the other settlement" + (needs.isEmpty() ? "" : "\n" + text("needs", String.join(", ", needs)).getString());
    }

    private void editor() {
        if (!reviewing) {
            bodyButton(Component.literal(targetName()), left, 0, panelWidth, this::chooseTarget, false, targetNeeds()).active = !countering && !submitting;
            for (boolean side : new boolean[]{true, false}) {
                int x = left + (side ? 0 : columnWidth() + 18);
                field(side ? firstFunds : secondFunds, x + 8, 164 + bundleOffset(), columnWidth() - 16, name(side) + " funds",
                        value -> { if (side) firstFunds = value; else secondFunds = value; });
                bodyButton(text("add"), x + 8, 192 + bundleOffset(), columnWidth() - 16, () -> chooseGoods(side), false,
                        "Choose stored goods and quantity. Select an added stack to remove it.").active = target != null && !submitting;
                var items = side ? firstItems : secondItems;
                int columns = Math.max(1, (columnWidth() - 16) / 20);
                for (int i = 0; i < items.size(); i++) {
                    int index = i;
                    ItemStack stack = items.get(i);
                    var slot = addRenderableWidget(new SettlementViewUi.IconButton(
                            stack.getHoverName().copy().append(" × " + stack.getCount() + "\nClick to remove this stack"),
                            x + 8 + i % columns * 20, bodyTop() + 58 + i / columns * 20, 20, 20, stack, false, false,
                            () -> false, () -> { items.remove(index); rebuildWidgets(); }));
                    bodyWidgets.put(slot, 58 + i / columns * 20);
                }
            }
            bodyButton(Component.literal("Caravan sent by: " + name(firstSends)), left, 234 + bundleOffset(), panelWidth, () -> {
                firstSends = !firstSends; rebuildWidgets();
            }, false, text("transport_help").getString());
            button(Component.literal("Review offer"), left + panelWidth - 112, top + panelHeight - 20, 112, () -> {
                if (draftTerms() != null) { feedback = Component.empty(); reviewing = true; scroll = 0; rebuildWidgets(); }
            }, false, "Review both contributions and the caravan sender before sending").active = target != null && !submitting;
        } else {
            int w = Math.min(120, (panelWidth - 92) / 2);
            button(text(countering ? "counter" : "propose"), left + panelWidth - w, top + panelHeight - 20, w,
                    () -> send(countering ? Action.COUNTER : Action.PROPOSE), true, "Send these terms to the other settlement");
            if (!countering) button(text("quote"), left + panelWidth - w * 2 - 6, top + panelHeight - 20, w, () -> send(Action.QUOTE), true,
                    "Ask an NPC settlement to price the exchange. Review its reply before accepting.");
        }
    }

    private void chooseGoods(boolean side) {
        var items = side ? firstItems : secondItems;
        if (items.size() >= 27) { feedback = text("invalid_terms"); return; }
        var choices = samples(side).stream().map(stack -> new SettlementTradePickerScreen.Choice(stack.getHoverName(),
                Component.literal("Stored goods • choose 1–" + stack.getMaxStackSize()), stack, stack.getMaxStackSize(),
                count -> { items.add(stack.copyWithCount(count)); rebuildWidgets(); })).toList();
        minecraft.setScreen(new SettlementTradePickerScreen(this, text("gives", name(side)), choices, true));
    }

    private void details() {
        String state = selected.getString("State");
        boolean offered = state.equals("OFFERED");
        int y = top + panelHeight - 20, right = left + panelWidth;
        if (offered && selected.getBoolean("Turn")) {
            int w = Math.min(95, (panelWidth - 92) / 3);
            button(text("accept"), right - w, y, w, () -> confirm(Action.ACCEPT), true,
                    "Accept these terms and reserve both contributions for departure");
            button(text("decline"), right - w * 2 - 4, y, w, () -> confirm(Action.DECLINE), true, "Decline this offer");
            if (selected.getInt("Remaining") > 0) button(text("counter"), right - w * 3 - 8, y, w, this::editCounter, true,
                    "Change the terms. " + selected.getInt("Remaining") + " counteroffers remain.");
        } else if (!selected.getBoolean("Departed") && (offered || state.equals("ACCEPTED"))) {
            button(Component.literal("Cancel trade"), right - 105, y, 105, () -> confirm(Action.CANCEL), true,
                    "Cancel before departure and return reserved contributions");
        }
        if (offered || state.equals("ACCEPTED") || selected.getBoolean("Departed")) {
            bodyButton(text("transport"), left, 270 + bundleOffset(), panelWidth - 100, () ->
                    minecraft.setScreen(new com.jvn.villagerretaliation.client.exploration.SettlementRoutesScreen(this, selected.getUUID("Id"))),
                    false, "Choose a route, assign travelers, or inspect this journey");
            if (!selected.getString("Blocker").isEmpty()) bodyButton(text("retry"), right - 94, 270 + bundleOffset(), 94, () -> send(Action.RETRY), true,
                    "Retry after resolving the delivery blocker");
        }
        if (selected.contains("FirstCargo")) bodyButton(Component.literal("Delivery record"), left, 296 + bundleOffset(), panelWidth, this::showCargo, false,
                "Inspect delivered, returned, lost, and remaining contributions");
    }

    private void confirm(Action action) {
        UUID id = selected.getUUID("Id");
        long revision = selected.getLong("Revision");
        Component description = action == Action.ACCEPT
                ? Component.literal("Reserve both contributions and begin preparing the caravan?\n\n").append(bundleSummary(selected, true)).append("\n").append(bundleSummary(selected, false))
                : Component.literal(action == Action.DECLINE ? "Decline this offer?" : "Cancel this trade and return any reserved contributions?");
        minecraft.setScreen(new SettlementConfirmScreen(this, text(action.name().toLowerCase(Locale.ROOT)), description, () -> {
            if (selected != null && id.equals(selected.getUUID("Id")) && revision == selected.getLong("Revision")) send(action);
            else feedback = text("stale");
        }));
    }

    private void showCargo() {
        var choices = new ArrayList<SettlementTradePickerScreen.Choice>();
        for (boolean first : new boolean[]{true, false}) {
            var cargo = selected.getCompound(first ? "FirstCargo" : "SecondCargo");
            for (String part : new String[]{"Delivered", "Returned", "Lost", "Remaining"}) {
                var bundle = cargo.getCompound(part);
                var items = bundle.getList("Items", Tag.TAG_COMPOUND).stream().map(raw -> ItemStack.parse(minecraft.level.registryAccess(), (CompoundTag) raw).orElse(ItemStack.EMPTY)).toList();
                Component detail = summary(items).copy().append(" + " + CurrencyAmount.load(bundle.getCompound("Funds")).mixedFraction() + " funds");
                choices.add(new SettlementTradePickerScreen.Choice(Component.literal(selected.getString(first ? "FirstName" : "SecondName") + " • ")
                        .append(text(part.toLowerCase(Locale.ROOT))), detail, new ItemStack(Items.CHEST), 1, null));
            }
        }
        minecraft.setScreen(new SettlementTradePickerScreen(this, Component.literal("Delivery record"), choices, false));
    }

    private Component bundleSummary(CompoundTag row, boolean first) {
        var terms = SettlementExchangeTerms.load(row.getCompound("Terms"), minecraft.level.registryAccess());
        var bundle = first ? terms.first() : terms.second();
        return text("gives", row.getString(first ? "FirstName" : "SecondName")).copy().append(": ")
                .append(summary(bundle.items())).append(" + " + bundle.funds().mixedFraction() + " funds");
    }

    private void editCounter() {
        draftRevision = selected.getLong("Revision");
        var terms = SettlementExchangeTerms.load(selected.getCompound("Terms"), minecraft.level.registryAccess());
        firstItems.clear(); firstItems.addAll(terms.first().items()); secondItems.clear(); secondItems.addAll(terms.second().items());
        firstFunds = fraction(terms.first().funds()); secondFunds = fraction(terms.second().funds()); firstSends = terms.firstSends();
        target = selected.getUUID("First").equals(village) ? selected.getUUID("Second") : selected.getUUID("First");
        countering = editing = true; reviewing = false; scroll = 0; rebuildWidgets(); send(Action.VIEW);
    }

    private static String fraction(CurrencyAmount value) { return value.numerator() + "/" + value.denominator(); }

    private void field(String value, int x, int y, int width, String label, java.util.function.Consumer<String> update) {
        var box = addRenderableWidget(new EditBox(font, x, bodyTop() + y, width, 20, Component.literal(label)));
        box.setMaxLength(32); box.setValue(value); box.setResponder(update);
        box.setTooltip(Tooltip.create(text("invalid_terms")));
        bodyWidgets.put(box, y);
    }

    private String targetName() {
        for (var raw : snapshot.getList("Contacts", Tag.TAG_COMPOUND)) {
            var contact = (CompoundTag) raw;
            if (contact.getUUID("Id").equals(target)) return contact.getString("Name");
        }
        return text("choose_settlement").getString();
    }

    private List<ItemStack> samples(boolean first) {
        boolean own = !countering || selected == null || selected.getUUID("First").equals(village);
        return snapshot.getList(first == own ? "Stock" : "OtherStock", Tag.TAG_COMPOUND).stream()
                .map(value -> ItemStack.parse(minecraft.level.registryAccess(), (CompoundTag) value).orElse(ItemStack.EMPTY))
                .filter(item -> !item.isEmpty()).sorted(java.util.Comparator.comparing(item -> item.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static CurrencyAmount amount(String text) {
        var parts = text.split("/", -1);
        if (parts.length > 2) throw new IllegalArgumentException("Invalid funds");
        return new CurrencyAmount(new BigInteger(parts[0].trim()), parts.length == 1 ? BigInteger.ONE : new BigInteger(parts[1].trim()));
    }

    private SettlementExchangeTerms draftTerms() {
        if (target == null) { feedback = text("choose_settlement"); return null; }
        try {
            return new SettlementExchangeTerms(new SettlementExchangeTerms.Bundle(firstItems, amount(firstFunds)),
                    new SettlementExchangeTerms.Bundle(secondItems, amount(secondFunds)), firstSends);
        } catch (IllegalArgumentException failure) { feedback = text("invalid_terms"); return null; }
    }

    private void send(Action action) {
        if (minecraft.player == null || submitting) return;
        SettlementExchangeTerms terms = null;
        if (action == Action.PROPOSE || action == Action.QUOTE || action == Action.COUNTER) {
            terms = draftTerms();
            if (terms == null) return;
        }
        if (action == Action.PROPOSE || action == Action.QUOTE) page = 0;
        submitting = true; refresh = Util.getMillis() + 5000;
        PacketDistributor.sendToServer(new SettlementExchangeRequest(village, target,
                action == Action.PROPOSE || action == Action.QUOTE ? draftId : selected == null ? null : selected.getUUID("Id"),
                action == Action.PROPOSE || action == Action.QUOTE ? snapshot.getLong("Revision") : action == Action.COUNTER ? draftRevision : selected == null ? 0 : selected.getLong("Revision"),
                action, terms, page, ++sequence));
        mutations.forEach(button -> button.active = false);
    }

    @Override public void tick() {
        super.tick();
        if (Util.getMillis() >= refresh && !(editing && getFocused() instanceof EditBox)) { submitting = false; send(Action.VIEW); }
    }

    private void back() {
        if (reviewing) reviewing = false;
        else if (editing) {
            minecraft.setScreen(new SettlementConfirmScreen(this, Component.literal("Discard draft?"),
                    Component.literal("Discard the unsent changes to this offer?"), () -> {
                editing = countering = false; scroll = 0; feedback = Component.empty(); rebuildWidgets();
            }));
            return;
        } else if (selected != null) { selected = null; selection = null; }
        else { minecraft.setScreen(parent); return; }
        scroll = 0; feedback = Component.empty(); rebuildWidgets();
    }

    @Override public void onClose() { back(); }
    @Override public boolean isPauseScreen() { return false; }

    private int contentHeight() { return (editing ? reviewing ? 270 : 294 : selected != null && selected.contains("FirstCargo") ? 326 : 290) + bundleOffset(); }

    private void arrange() {
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() - (bodyBottom() - bodyTop())));
        bodyWidgets.forEach((widget, y) -> {
            widget.setY(bodyTop() + y - scroll);
            widget.visible = widget.getY() >= bodyTop() && widget.getBottom() <= bodyBottom();
        });
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((editing || selected != null) && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP)) {
            scroll += key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 72 : -72; arrange(); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if ((editing || selected != null) && x >= left && x <= left + panelWidth && y >= bodyTop() && y < bodyBottom()) {
            scroll -= (int) (vertical * 24); arrange(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override protected void renderWindowFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        TooltipRenderUtil.renderTooltipBackground(graphics, x, y, width, height, 0);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hover = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left, top + 4, 0xFFFFFF);
        if (editing || selected != null) {
            graphics.drawString(font, editing ? reviewing ? "2. Review and send" : "1. Set the terms" : "Trade details",
                    left, top + 26, 0xB9C1CB);
            graphics.enableScissor(left - 3, bodyTop(), left + panelWidth + 3, bodyBottom());
            if (editing) renderEditor(graphics, mouseX, mouseY);
            else renderDetails(graphics, mouseX, mouseY);
            graphics.disableScissor();
            if (contentHeight() > bodyBottom() - bodyTop()) {
                int track = bodyBottom() - bodyTop();
                int thumb = Math.max(12, track * track / contentHeight());
                int y = bodyTop() + scroll * (track - thumb) / (contentHeight() - track);
                graphics.fill(left + panelWidth + 4, bodyTop(), left + panelWidth + 5, bodyBottom(), 0xFF45414F);
                graphics.fill(left + panelWidth + 4, y, left + panelWidth + 5, y + thumb, 0xFFB9C1CB);
            }
        } else if (snapshot.getList("Rows", Tag.TAG_COMPOUND).isEmpty()) {
            wrapped(graphics, Component.literal(submitting ? "Loading trades..." : "No trade agreements yet. Choose New offer to begin."),
                    left + 10, bodyTop() + 14, panelWidth - 20, 3, mouseX, mouseY);
        }
        wrapped(graphics, submitting ? Component.literal("Waiting for settlement...") : feedback,
                left, top + panelHeight - 42, panelWidth, 1, mouseX, mouseY);
        if (hover != null && (mouseY >= bodyTop() && mouseY < bodyBottom() || mouseY >= top + panelHeight - 54))
            graphics.renderTooltip(font, font.split(hover, Math.min(360, width - 24)), mouseX, mouseY);
    }

    private void renderEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = bodyTop() - scroll;
        for (boolean first : new boolean[]{true, false}) {
            int x = left + (first ? 0 : columnWidth() + 18);
            renderBundle(graphics, x, y + 32, name(first), first ? firstItems : secondItems,
                    reviewing ? amount(first ? firstFunds : secondFunds).mixedFraction() : null, !reviewing, mouseX, mouseY);
        }
        if (reviewing) {
            wrapped(graphics, Component.literal("Caravan sent by: " + name(firstSends)), left, y + 192 + bundleOffset(), panelWidth, 2, mouseX, mouseY);
            wrapped(graphics, text("transport_help"), left, y + 220 + bundleOffset(), panelWidth, 4, mouseX, mouseY);
        } else {
            wrapped(graphics, Component.literal("Goods and funds come from settlement storage and treasury."),
                    left, y + 262 + bundleOffset(), panelWidth, 2, mouseX, mouseY);
        }
    }

    private void renderBundle(GuiGraphics graphics, int x, int y, String name, List<ItemStack> items, String funds,
                              boolean editable, int mouseX, int mouseY) {
        int w = columnWidth();
        graphics.hLine(x, x + w, y, 0xFF51445F);
        wrapped(graphics, text("gives", name), x + 8, y + 7, w - 16, 1, mouseX, mouseY);
        int columns = Math.max(1, (w - 16) / 20);
        if (items.isEmpty()) graphics.drawString(font, text("no_goods"), x + 8, y + 34, 0x999999);
        for (int i = 0; i < items.size(); i++) {
            int sx = x + 8 + i % columns * 20, sy = y + 26 + i / columns * 20;
            if (!editable) {
                graphics.fill(sx, sy, sx + 18, sy + 18, 0x503E354B);
                graphics.renderItem(items.get(i), sx + 1, sy + 1);
            }
            graphics.renderItemDecorations(font, items.get(i), sx + 1, sy + 1);
            if (mouseX >= sx && mouseX < sx + 20 && mouseY >= sy && mouseY < sy + 20) hover = items.get(i).getHoverName().copy().append(" × " + items.get(i).getCount() + (editable ? "\nClick to remove this stack" : ""));
        }
        if (editable) graphics.drawString(font, text("funds"), x + 8, y + 119 + bundleOffset(), 0xB9C1CB);
        else {
            graphics.renderItem(new ItemStack(Items.EMERALD), x + 8, y + 126 + bundleOffset());
            wrapped(graphics, Component.literal(funds + " funds"), x + 30, y + 130 + bundleOffset(), w - 38, 1, mouseX, mouseY);
        }
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = bodyTop() - scroll;
        var terms = SettlementExchangeTerms.load(selected.getCompound("Terms"), minecraft.level.registryAccess());
        String state = selected.getString("State");
        Component status = state.equals("OFFERED") ? Component.literal(selected.getBoolean("Turn") ? "Your response is needed" : "Waiting for the other settlement")
                : text("state." + state.toLowerCase(Locale.ROOT));
        if (state.equals("ACCEPTED")) status = status.copy().append(" • ").append(Component.translatableWithFallback(
                "villagerretaliation.gui.aid." + selected.getString("Journey").toLowerCase(Locale.ROOT), selected.getString("Journey").toLowerCase(Locale.ROOT).replace('_', ' ')));
        wrapped(graphics, status, left, y, panelWidth, 2, mouseX, mouseY);
        for (boolean first : new boolean[]{true, false}) {
            var bundle = first ? terms.first() : terms.second();
            renderBundle(graphics, left + (first ? 0 : columnWidth() + 18), y + 26, selected.getString(first ? "FirstName" : "SecondName"),
                    bundle.items(), bundle.funds().mixedFraction(), false, mouseX, mouseY);
        }
        String sender = selected.getString(terms.firstSends() ? "FirstName" : "SecondName");
        String receiver = selected.getString(terms.firstSends() ? "SecondName" : "FirstName");
        wrapped(graphics, Component.literal(sender + " → " + receiver + " → " + sender), left, y + 182 + bundleOffset(), panelWidth, 1, mouseX, mouseY);
        String route = selected.getString("Route");
        wrapped(graphics, Component.literal("Route: " + (route.isEmpty() ? "Automatic" : route) + " • Guards: " + selected.getInt("Guards")),
                left, y + 198 + bundleOffset(), panelWidth, 1, mouseX, mouseY);
        wrapped(graphics, text("transport_cost", selected.getString("Transport")), left, y + 214 + bundleOffset(), panelWidth, 1, mouseX, mouseY);
        String reason = selected.getString("Blocker").isEmpty() ? selected.getString("Explanation") : selected.getString("Blocker");
        wrapped(graphics, text(reason), left, y + 232 + bundleOffset(), panelWidth, 3, mouseX, mouseY);
    }

    private Component summary(List<ItemStack> items) {
        var result = Component.empty();
        for (var item : items) {
            if (!result.getString().isEmpty()) result.append(", ");
            result.append(item.getCount() + " ").append(item.getHoverName());
        }
        return items.isEmpty() ? text("no_goods") : result;
    }

    private void wrapped(GuiGraphics graphics, Component value, int x, int y, int width, int lines, int mouseX, int mouseY) {
        var split = font.split(value, width);
        for (int i = 0; i < Math.min(lines, split.size()); i++) graphics.drawString(font, split.get(i), x, y + i * 11, 0xB9C1CB, false);
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + lines * 11) hover = value;
    }

    private final class Offers extends VillagerSelectionList<Offer> {
        Offers() {
            super(SettlementExchangeScreen.this.minecraft, panelWidth, Math.max(32, bodyBottom() - bodyTop()), bodyTop(), 40);
            setX(left);
            snapshot.getList("Rows", Tag.TAG_COMPOUND).forEach(raw -> addEntry(new Offer((CompoundTag) raw)));
        }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Offer extends ObjectSelectionList.Entry<Offer> {
        private final CompoundTag row;
        Offer(CompoundTag row) { this.row = row; }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) return mouseClicked(0, 0, 0);
            return false;
        }
        private String other() { return row.getString(row.getUUID("First").equals(village) ? "SecondName" : "FirstName"); }
        private Component status() { return row.getString("State").equals("OFFERED")
                ? Component.literal(row.getBoolean("Turn") ? "Your response needed" : "Waiting for response")
                : text("state." + row.getString("State").toLowerCase(Locale.ROOT)); }
        @Override public Component getNarration() { return Component.literal(other()).append(". ").append(status()); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            selection = row.getUUID("Id"); selected = row; scroll = 0; feedback = Component.empty(); rebuildWidgets(); return true;
        }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.renderItem(new ItemStack(Items.EMERALD), x + 5, y + 10);
            graphics.drawString(font, font.plainSubstrByWidth(other(), width - 34), x + 28, y + 6, 0xFFFFFF);
            graphics.drawString(font, status(), x + 28, y + 21, row.getBoolean("Turn") && row.getString("State").equals("OFFERED") ? 0xFFE1A0 : 0xB9C1CB);
            if (hovered) hover = getNarration().copy().append("\n").append(bundleSummary(row, true)).append("\n").append(bundleSummary(row, false));
        }
    }
}
