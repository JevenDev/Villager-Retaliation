package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.quest.tutorial.TutorialInstructionText;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Resolve on the receiving client, so remapped Use is never confused with inventory right-click. */
public final class TutorialControlHints {
    private TutorialControlHints() { }
    public static String resolve(String text) {
        return TutorialInstructionText.resolve(text, token -> {
            var options = Minecraft.getInstance().options;
            return switch (token) {
                case "Inventory" -> key(options.keyInventory);
                case "Use" -> key(options.keyUse);
                case "Attack" -> key(options.keyAttack);
                case "Sneak" -> key(options.keyShift);
                case "ControlModifier" -> Minecraft.ON_OSX?Component.translatable("tutorial.villagerretaliation.command_modifier").getString():Component.translatable("key.keyboard.left.control").getString();
                case "ShiftModifier" -> Component.translatable("key.keyboard.left.shift").getString();
                case "AltModifier" -> Component.translatable(Minecraft.ON_OSX?"tutorial.villagerretaliation.option_modifier":"key.keyboard.left.alt").getString();
                default -> null;
            };
        });
    }
    private static String key(KeyMapping key) {
        return key.isUnbound()
                ? Component.translatable("tutorial.villagerretaliation.unbound", Component.translatable(key.getName())).getString()
                : key.getTranslatedKeyMessage().getString();
    }
}
