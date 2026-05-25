package com.jvn.villagerretaliation.notification;

import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorKind;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

public final class VillagerNotifications {
    private VillagerNotifications() {
    }

    public static ResolvedVillagerNotification resolve(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            String trigger,
            Map<String, String> replacements,
            String fallbackText,
            VillagerReputationNoticeKind fallbackKind) {
        return resolve(level, villager, player, null, trigger, replacements, fallbackText, fallbackKind);
    }

    public static ResolvedVillagerNotification resolve(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            LivingEntity target,
            String trigger,
            Map<String, String> replacements,
            String fallbackText,
            VillagerReputationNoticeKind fallbackKind) {
        return VillagerNotificationResources
                .select(context(level, villager, player, target), trigger, replacements)
                .orElse(new ResolvedVillagerNotification(
                        fallbackText,
                        ResolvedVillagerNotification.DEFAULT_COLOR,
                        ResolvedVillagerNotification.DEFAULT_COLOR,
                        fallbackKind,
                        VillagerWorldTextIndicatorKind.DIALOGUE
                ));
    }

    public static void sendHud(
            ServerPlayer player,
            ServerLevel level,
            AbstractVillager villager,
            String trigger,
            Map<String, String> replacements,
            String fallbackText,
            VillagerReputationNoticeKind fallbackKind) {
        sendHud(player, level, villager, null, trigger, replacements, fallbackText, fallbackKind);
    }

    public static void sendHud(
            ServerPlayer player,
            ServerLevel level,
            AbstractVillager villager,
            LivingEntity target,
            String trigger,
            Map<String, String> replacements,
            String fallbackText,
            VillagerReputationNoticeKind fallbackKind) {
        VillagerReputationNetworking.sendNotice(
                player,
                resolve(level, villager, player, target, trigger, replacements, fallbackText, fallbackKind)
        );
    }

    public static boolean sendWorldText(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            String trigger,
            Map<String, String> replacements,
            VillagerWorldTextIndicatorKind fallbackKind,
            String fallbackText) {
        return sendWorldText(level, villager, player, null, trigger, "", replacements, fallbackKind, fallbackText);
    }

    public static boolean sendWorldText(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            LivingEntity target,
            String trigger,
            Map<String, String> replacements,
            VillagerWorldTextIndicatorKind fallbackKind,
            String fallbackText) {
        return sendWorldText(level, villager, player, target, trigger, "", replacements, fallbackKind, fallbackText);
    }

    public static boolean sendWorldText(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            String trigger,
            String fallbackTrigger,
            Map<String, String> replacements,
            VillagerWorldTextIndicatorKind fallbackKind,
            String fallbackText) {
        return sendWorldText(level, villager, player, null, trigger, fallbackTrigger, replacements, fallbackKind, fallbackText);
    }

    public static boolean sendWorldText(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            LivingEntity target,
            String trigger,
            String fallbackTrigger,
            Map<String, String> replacements,
            VillagerWorldTextIndicatorKind fallbackKind,
            String fallbackText) {
        if (!VillagerRetaliationConfig.ENABLE_WORLD_TEXT_NOTIFICATIONS.get()) {
            return false;
        }
        VillagerNotificationContext context = context(level, villager, player, target);
        ResolvedVillagerNotification notification = VillagerNotificationResources
                .select(context, trigger, replacements)
                .or(() -> fallbackTrigger == null || fallbackTrigger.isBlank()
                        ? java.util.Optional.empty()
                        : VillagerNotificationResources.select(context, fallbackTrigger, replacements))
                .orElse(new ResolvedVillagerNotification(
                        fallbackText,
                        ResolvedVillagerNotification.DEFAULT_COLOR,
                        ResolvedVillagerNotification.DEFAULT_COLOR,
                        VillagerReputationNoticeKind.DEFAULT,
                        fallbackKind
                ));
        if (notification.text().isBlank()) {
            return false;
        }
        VillagerReputationNetworking.sendWorldTextIndicator(
                villager,
                notification.text(),
                notification.worldTextKind(),
                notification.textColor()
        );
        return true;
    }

    public static Map<String, String> replacements(String... values) {
        Map<String, String> replacements = new HashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            replacements.put(values[index], values[index + 1]);
        }
        return replacements;
    }

    private static VillagerNotificationContext context(ServerLevel level, AbstractVillager villager, Player player) {
        return context(level, villager, player, null);
    }

    private static VillagerNotificationContext context(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            LivingEntity target) {
        VillagerReputationManager.ReputationSnapshot reputation = player == null
                ? new VillagerReputationManager.ReputationSnapshot(0, VillagerReputationLevel.NEUTRAL)
                : VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        return new VillagerNotificationContext(
                level,
                villager,
                player,
                target,
                reputation.value(),
                reputation.level(),
                villager.getRandom(),
                player instanceof ServerPlayer serverPlayer ? VillagerLocale.locale(serverPlayer) : VillagerLocale.DEFAULT_LOCALE
        );
    }
}
