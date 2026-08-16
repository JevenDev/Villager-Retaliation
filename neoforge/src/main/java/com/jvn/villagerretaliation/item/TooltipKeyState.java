package com.jvn.villagerretaliation.item;

import java.lang.reflect.InvocationTargetException;

final class TooltipKeyState {
    private TooltipKeyState() {
    }

    static boolean hasShiftDown() {
        try {
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            Object result = screenClass.getMethod("hasShiftDown").invoke(null);
            return result instanceof Boolean pressed && pressed;
        } catch (ClassNotFoundException
                 | NoSuchMethodException
                 | IllegalAccessException
                 | InvocationTargetException
                 | LinkageError ignored) {
            return false;
        }
    }
}
