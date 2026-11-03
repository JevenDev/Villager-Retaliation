package com.jvn.villagerretaliation.client.settlement;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;

final class SettlementExchangeClientChecks {
    private final Consumer<String> capture;
    private int phase, ticks;
    private SettlementExchangeScreen exchange;

    SettlementExchangeClientChecks(Consumer<String> capture) { this.capture = capture; }

    boolean tick(Minecraft minecraft) {
        require(++ticks < 240, "exchange UI timed out at " + phase);
        switch (phase) {
            case 0 -> {
                if (!(minecraft.screen instanceof SettlementExchangeScreen screen)) return false;
                exchange = screen;
                if (list(screen).children().isEmpty() || ticks < 15) return false;
                capture.accept("trade-offers");
                list(screen).children().getFirst().mouseClicked(0, 0, 0);
                next();
            }
            case 1 -> {
                if (ticks < 5) return false;
                capture.accept("trade-details");
                button(exchange, "Counteroffer").onPress();
                next();
            }
            case 2 -> {
                if (ticks < 15) return false;
                capture.accept("trade-edit");
                var funds = field(exchange);
                funds.setValue("-1");
                button(exchange, "Review offer").onPress();
                require(exchange.children().stream().anyMatch(EditBox.class::isInstance), "negative funds stay in the editor");
                field(exchange).setValue("3/4");
                button(exchange, "Review offer").onPress();
                require(exchange.children().stream().noneMatch(EditBox.class::isInstance), "valid terms open the review step");
                next();
            }
            case 3 -> {
                if (ticks < 5) return false;
                capture.accept("trade-review");
                button(exchange, "Back").onPress();
                require(field(exchange).getValue().equals("3/4"), "review navigation preserves exact funds");
                minecraft.options.guiScale().set(3); minecraft.resizeDisplay();
                next();
            }
            case 4 -> {
                if (ticks < 5) return false;
                bounds(exchange);
                capture.accept("trade-edit-small");
                exchange.mouseScrolled(exchange.width / 2.0, exchange.height / 2.0, 0, -20);
                next();
            }
            case 5 -> {
                if (ticks < 5) return false;
                bounds(exchange); capture.accept("trade-edit-small-scrolled");
                require(exchange.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .anyMatch(button -> button.visible && button.getMessage().getString().startsWith("Caravan sent by:")), "sender control is reachable by scrolling");
                button(exchange, "Back").onPress();
                require(minecraft.screen instanceof SettlementConfirmScreen, "leaving a draft requires a discard choice");
                button(minecraft.screen, "No").onPress();
                require(minecraft.screen == exchange, "keeping a draft returns to the editor");
                require(field(exchange).getValue().equals("3/4"), "discard cancellation preserves the draft");
                button(exchange, "Back").onPress();
                button(minecraft.screen, "Yes").onPress();
                require(minecraft.screen == exchange && exchange.children().stream().noneMatch(EditBox.class::isInstance), "discard returns to trade details");
                minecraft.options.guiScale().set(2); minecraft.resizeDisplay();
                exchange.onClose(); exchange.onClose();
                return true;
            }
        }
        return false;
    }

    private void next() { phase++; ticks = 0; }
    private static EditBox field(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static ObjectSelectionList<?> list(Screen screen) {
        return screen.children().stream().filter(ObjectSelectionList.class::isInstance).map(child -> (ObjectSelectionList<?>) child).findFirst().orElseThrow();
    }
    private static Button button(Screen screen, String text) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(text)).findFirst().orElseThrow();
    }
    private static void bounds(Screen screen) {
        for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible) {
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                    "trade controls fit the viewport");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
