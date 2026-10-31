package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ui.ToucanNineSlice;
import com.jvn.toucanlib.client.ui.ToucanOptionButton;
import com.jvn.toucanlib.client.ui.ToucanUiStyle;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.settlement.SettlementViewClient;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class VillagerUi {
    private static final ToucanNineSlice FRAME = new ToucanNineSlice(
            VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_SCROLL_ROW_TEXTURE, 132, 24, 3, 3, 3, 3);
    private static final ToucanNineSlice SELECTED = new ToucanNineSlice(
            VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_SELECTED_SCROLL_ROW_TEXTURE, 136, 28, 5, 5, 5, 5);
    private static final ToucanUiStyle.Checkbox CHECKBOX = new ToucanUiStyle.Checkbox(9, 13,
            (graphics, font, left, top, checked, active, textColor) -> {
                graphics.blit(VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_CHECKBOX_TEXTURE,
                        left, top, 0, 0, 9, 9, 9, 9);
                if (checked) graphics.drawString(font, "x", left + 2, top, textColor, false);
            });
    private static final ToucanUiStyle TEXTURED = new ToucanUiStyle(
            new ToucanUiStyle.Panel(FRAME, 3, 0xF023282E, 0),
            new ToucanUiStyle.Panel(FRAME, 3, 0xF023282E, 0),
            new ToucanUiStyle.Panel(FRAME, 3, 0xF03B444E, 0),
            new ToucanUiStyle.Panel(SELECTED, 3, 0xE02D5284, 0),
            CHECKBOX, 0xFFFFFF, 0x999999, 0xFF81B6FF);
    private static final ToucanUiStyle BASIC = new ToucanUiStyle(
            new ToucanUiStyle.Panel(null, 0, 0xFF202020, 0xFF808080),
            new ToucanUiStyle.Panel(null, 0, 0xFF303030, 0xFF808080),
            new ToucanUiStyle.Panel(null, 0, 0xFF484848, 0xFF808080),
            new ToucanUiStyle.Panel(null, 0, 0xFF606060, 0xFFFFFFFF),
            CHECKBOX, 0xFFFFFF, 0x999999, 0xFFFFFFFF);

    private VillagerUi() { }

    public static ToucanUiStyle style() {
        return SettlementViewClient.basicUi() ? BASIC : TEXTURED;
    }

    public static void frame(GuiGraphics graphics, int left, int top, int width, int height) {
        style().frame(graphics, left, top, width, height);
    }

    public static void row(GuiGraphics graphics, int left, int top, int width, int height, boolean selected, boolean hovered) {
        style().row(graphics, left, top, width, height, selected, hovered);
    }

    public static Button.Builder buttonBuilder(Component message, Button.OnPress action) {
        return new ToucanOptionButton.Builder(message, action, VillagerUi::style) {
            @Override
            protected ToucanOptionButton create(Button template, Button.OnPress press, Button.CreateNarration narration, Supplier<ToucanUiStyle> style) {
                return new OptionButton(template, press, narration);
            }
        };
    }

    public static final class OptionButton extends ToucanOptionButton {
        private OptionButton(Button template, Button.OnPress action, Button.CreateNarration narration) {
            super(template, action, narration, VillagerUi::style);
        }
    }
}
