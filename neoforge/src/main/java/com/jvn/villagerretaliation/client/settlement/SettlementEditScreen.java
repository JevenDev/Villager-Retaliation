package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Entry;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementArea;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementDepotPurpose;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementTradeOrder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public final class SettlementEditScreen extends Screen {
    private final SettlementManagementScreen parent;
    private final SettlementViewScreen view;
    private final Mode mode;
    private final Entry original;
    private final UUID id;
    private final Map<String, String> values = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private final EnumSet<SettlementPermission> permissions = EnumSet.noneOf(SettlementPermission.class);
    private SettlementAreaType areaType = SettlementAreaType.HOME;
    private SettlementDepotPurpose purpose = SettlementDepotPurpose.GENERAL;
    private SettlementTradeOrder.Direction direction = SettlementTradeOrder.Direction.BUY;
    private ResourceLocation item = BuiltInRegistries.ITEM.getKey(Items.AIR);
    private boolean enabled;
    private boolean deposit = true;
    private Component error = Component.empty();
    private Button save;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public SettlementEditScreen(SettlementManagementScreen parent, Mode mode, Entry original) {
        this(parent, mode, original, null);
    }

    SettlementEditScreen(SettlementManagementScreen parent, Mode mode, Entry original, SettlementViewScreen view) {
        super(SettlementManagementScreen.enumText("form", mode));
        this.parent = parent;
        this.view = view;
        this.mode = mode;
        this.original = original;
        this.id = original == null ? UUID.randomUUID() : original.id();
        BlockPos position = Minecraft.getInstance().player.blockPosition();
        this.values.put("name", original == null ? "" : original.name());
        this.values.put("start", coordinates(position));
        this.values.put("end", coordinates(position));
        this.values.put("priority", "5");
        this.values.put("amount", "1");
        this.values.put("stock", "64");
        this.values.put("price", "0");
        this.values.put("daily", currency(parent.snapshot().header().budget()));
        this.values.put("reserve", currency(parent.snapshot().header().reserve()));
        this.enabled = mode == Mode.AREA;
        if (original instanceof SettlementManagementPayload.Area area) {
            this.areaType = area.kind();
            this.enabled = area.enabled();
            this.values.put("start", coordinates(area.min()));
            this.values.put("end", coordinates(area.max()));
            this.values.put("priority", Integer.toString(area.priority()));
        } else if (original instanceof SettlementManagementPayload.Depot depot) {
            this.purpose = depot.purpose();
            this.values.put("priority", Integer.toString(depot.priority()));
        } else if (original instanceof SettlementManagementPayload.Order order) {
            this.item = order.item();
            this.direction = order.direction();
            this.enabled = order.enabled();
            this.values.put("stock", Integer.toString(order.target()));
            this.values.put("price", currency(order.price()));
            this.values.put("daily", currency(order.budget()));
        } else if (original instanceof SettlementManagementPayload.Delegate delegate) {
            this.permissions.addAll(delegate.permissions());
        }
        if (mode == Mode.ORDER && original == null) {
            this.item = BuiltInRegistries.ITEM.getKey(Minecraft.getInstance().player.getMainHandItem().getItem());
            this.values.put("daily", "0");
        }
        if (mode == Mode.DELEGATE) {
            this.permissions.add(SettlementPermission.VIEW);
            this.values.put("player", original == null ? "" : original.name());
        }
    }

    public SettlementManagementScreen parent() {
        return this.parent;
    }

    public void result(SettlementManagementActionResultPayload payload) {
        if (this.parent.snapshot().header() == null || !this.parent.snapshot().header().id().equals(payload.town())) {
            return;
        }
        if (payload.applied()) {
            if (this.view != null) this.view.areaSaved(this.id);
            onClose();
        } else {
            this.error = text("not_applied");
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(440, this.width - 32);
        this.panelHeight = Math.min(258, this.height - 24);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.labels.clear();
        int x = this.left;
        int y = this.top + 26;
        int half = (this.panelWidth - 6) / 2;
        switch (this.mode) {
            case AREA -> {
                field("name", x, y, this.panelWidth, 48);
                cycle("area_type", x, y + 38, half, SettlementManagementScreen.enumText("area", this.areaType), () -> {
                    this.areaType = next(SettlementAreaType.values(), this.areaType);
                    rebuildWidgets();
                });
                field("priority", x + half + 6, y + 38, 58, 1);
                toggle(x + half + 70, y + 50, this.panelWidth - half - 70);
                positionField("start", y + 76);
                positionField("end", y + 114);
            }
            case DEPOT -> {
                cycle("purpose", x, y, this.panelWidth, SettlementManagementScreen.enumText("depot", this.purpose), () -> {
                    this.purpose = next(SettlementDepotPurpose.values(), this.purpose);
                    rebuildWidgets();
                });
                field("priority", x, y + 38, this.panelWidth, 1);
            }
            case STOCK -> {
                this.labels.add(new Label(text("held_item", this.minecraft.player.getMainHandItem().getHoverName()), x, y));
                field("stock", x, y + 26, this.panelWidth, 7);
                this.labels.add(new Label(text("stock_zero"), x, y + 68));
            }
            case TREASURY -> {
                cycle("transfer_type", x, y, this.panelWidth, text(this.deposit ? "deposit_currency" : "withdraw_currency"), () -> {
                    this.deposit = !this.deposit;
                    rebuildWidgets();
                });
                field("amount", x, y + 38, this.panelWidth, 4);
            }
            case BUDGET -> {
                field("reserve", x, y, this.panelWidth, 40);
                field("daily", x, y + 38, this.panelWidth, 40);
            }
            case ORDER -> {
                Component itemName = this.item.equals(BuiltInRegistries.ITEM.getKey(Items.AIR)) ? text("choose_item")
                        : BuiltInRegistries.ITEM.get(this.item).getDescription();
                cycle("item", x, y, this.panelWidth, itemName, () -> this.minecraft.setScreen(new SettlementItemPickerScreen(this, selected -> {
                    this.item = selected;
                    this.minecraft.setScreen(this);
                })));
                cycle("direction", x, y + 38, half, SettlementManagementScreen.enumText("direction", this.direction), () -> {
                    this.direction = next(SettlementTradeOrder.Direction.values(), this.direction);
                    rebuildWidgets();
                });
                toggle(x + half + 6, y + 50, half);
                field("stock", x, y + 76, this.panelWidth, 7);
                field("price", x, y + 114, half, 40);
                field("daily", x + half + 6, y + 114, half, 40);
            }
            case DELEGATE -> {
                field("player", x, y, this.panelWidth, 32);
                int index = 0;
                for (SettlementPermission permission : SettlementPermission.values()) {
                    int column = index % 2;
                    int row = index / 2;
                    Button button = addRenderableWidget(Button.builder(
                            text("permission_toggle", SettlementManagementScreen.enumText("permission", permission),
                                    text(this.permissions.contains(permission) ? "enabled" : "disabled")),
                            ignored -> {
                                if (!this.permissions.remove(permission)) {
                                    this.permissions.add(permission);
                                    this.permissions.add(SettlementPermission.VIEW);
                                }
                                rebuildWidgets();
                            }).bounds(x + column * (half + 6), y + 42 + row * 24, half, 20).build());
                    button.active = permission != SettlementPermission.VIEW || this.permissions.size() <= 1;
                    index++;
                }
            }
        }
        int bottom = this.top + this.panelHeight;
        this.save = addRenderableWidget(Button.builder(text("save"), ignored -> save())
                .bounds(x + half + 6, bottom - 20, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(x, bottom - 20, half, 20).build());
    }

    private void save() {
        try {
            var command = switch (this.mode) {
                case AREA -> {
                    BlockPos first = position(this.values.get("start"));
                    BlockPos second = position(this.values.get("end"));
                    BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
                    BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
                    yield new SettlementManagementActionPayload.SaveArea(new SettlementArea(this.id, this.areaType, this.values.get("name"),
                            min, max, number("priority", 0, 9), this.enabled));
                }
                case DEPOT -> new SettlementManagementActionPayload.ConfigureDepot(this.id, this.purpose, number("priority", 0, 9));
                case STOCK -> new SettlementManagementActionPayload.StockTarget(this.id, number("stock", 0, 4_096));
                case TREASURY -> new SettlementManagementActionPayload.Treasury(this.deposit, number("amount", 1, 4_096));
                case BUDGET -> new SettlementManagementActionPayload.Budget(amount(this.values.get("reserve")), amount(this.values.get("daily")));
                case ORDER -> new SettlementManagementActionPayload.SaveOrder(new SettlementTradeOrder(this.id, this.direction, this.item,
                        number("stock", 0, 1_000_000), amount(this.values.get("price")), amount(this.values.get("daily")), this.enabled));
                case DELEGATE -> new SettlementManagementActionPayload.Permissions(player(), this.permissions);
            };
            this.error = Component.empty();
            this.parent.submit(command);
        } catch (IllegalArgumentException exception) {
            this.error = text("invalid_fields");
        }
    }

    private UUID player() {
        String name = this.values.get("player").strip();
        if (this.original instanceof SettlementManagementPayload.Delegate delegate && name.equalsIgnoreCase(delegate.name())) {
            return delegate.id();
        }
        if (this.minecraft.getConnection() != null) {
            for (var player : this.minecraft.getConnection().getOnlinePlayers()) {
                if (player.getProfile().getName().equalsIgnoreCase(name)) {
                    return player.getProfile().getId();
                }
            }
        }
        throw new IllegalArgumentException("Choose a known player");
    }

    private int number(String key, int minimum, int maximum) {
        int value = Integer.parseInt(this.values.get(key).strip());
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Number is outside its range");
        }
        return value;
    }

    private static CurrencyAmount amount(String text) {
        String value = text.strip();
        if (value.matches("[0-9]{1,16}/[0-9]{1,16}")) {
            String[] parts = value.split("/");
            return new CurrencyAmount(new BigInteger(parts[0]), new BigInteger(parts[1]));
        }
        if (!value.matches("[0-9]{1,16}(\\.[0-9]{1,6})?")) {
            throw new IllegalArgumentException("Invalid currency amount");
        }
        BigDecimal decimal = new BigDecimal(value);
        return new CurrencyAmount(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()));
    }

    private static String currency(CurrencyAmount amount) {
        return amount.isWhole() ? amount.wholeUnits().toString() : amount.numerator() + "/" + amount.denominator();
    }

    private static BlockPos position(String value) {
        String[] parts = value.strip().split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Three coordinates are required");
        }
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private void positionField(String key, int y) {
        field(key, this.left, y, this.panelWidth - 88, 36);
        addRenderableWidget(Button.builder(text("use_position"), ignored -> {
            this.values.put(key, coordinates(this.minecraft.player.blockPosition()));
            rebuildWidgets();
        }).bounds(this.left + this.panelWidth - 82, y + 12, 82, 20).build());
    }

    private void field(String key, int x, int y, int width, int maximum) {
        this.labels.add(new Label(text("field." + key), x, y));
        EditBox field = new EditBox(this.font, x, y + 12, width, 20, text("field." + key));
        field.setMaxLength(maximum);
        field.setValue(this.values.getOrDefault(key, ""));
        field.setTooltip(Tooltip.create(text("field." + key + "_hint")));
        field.setResponder(value -> this.values.put(key, value));
        addRenderableWidget(field);
    }

    private void cycle(String label, int x, int y, int width, Component value, Runnable change) {
        this.labels.add(new Label(text("field." + label), x, y));
        addRenderableWidget(Button.builder(value, ignored -> change.run()).bounds(x, y + 12, width, 20).build());
    }

    private void toggle(int x, int y, int width) {
        addRenderableWidget(Button.builder(text(this.enabled ? "enabled" : "disabled"), ignored -> {
            this.enabled = !this.enabled;
            rebuildWidgets();
        }).bounds(x, y, width, 20).build());
    }

    private static <E extends Enum<E>> E next(E[] values, E current) {
        int change = Screen.hasShiftDown() ? values.length - 1 : 1;
        return values[(current.ordinal() + change) % values.length];
    }

    @Override
    public void tick() {
        this.save.active = !this.parent.submitting();
        if (this.parent.snapshot().header() == null) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return !this.parent.submitting() && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (this.view != null && this.parent.submitting()) {
            return;
        }
        if (this.view == null || !SettlementViewClient.resume(this.parent)) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + this.panelHeight + 8, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top, 0xFFFFFF);
        for (Label label : this.labels) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.text().getString(), this.panelWidth), label.x(), label.y(), 0xD0D0D0);
        }
        graphics.drawString(this.font, this.error, this.left, this.top + this.panelHeight - 36, 0xFFFFFF);
    }

    private static MutableComponent text(String key, Object... arguments) {
        return SettlementManagementScreen.text(key, arguments);
    }

    private record Label(Component text, int x, int y) {
    }

    public enum Mode {
        AREA, DEPOT, STOCK, TREASURY, BUDGET, ORDER, DELEGATE
    }
}
