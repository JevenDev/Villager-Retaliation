package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerMountOwnershipDialogue {
    public static final String OPTION_ID = "mount.ownership";
    private static final String RESPONSE = "Take it up with my boss.";

    private VillagerMountOwnershipDialogue() {
    }

    public static List<DialogueOptionDefinition> addAvailableOption(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            List<DialogueOptionDefinition> options) {
        AbstractHorse mount = ownedMountedAssignment(level, player, villager);
        if (mount == null) {
            return options;
        }
        List<DialogueOptionDefinition> result = new ArrayList<>(options);
        result.add(DialogueOptionDefinition.simple(
                OPTION_ID,
                "That's my " + mount.getDisplayName().getString(),
                DialogueRequestType.QUESTION,
                Integer.MAX_VALUE));
        return List.copyOf(result);
    }

    public static boolean handle(ServerLevel level, ServerPlayer player, Villager villager, String optionId) {
        if (!OPTION_ID.equals(optionId)) {
            return false;
        }
        if (ownedMountedAssignment(level, player, villager) != null) {
            VillagerInteractionService.sendPersonalVillagerChat(player, villager, RESPONSE);
        }
        return true;
    }

    static boolean isAvailable(ServerLevel level, ServerPlayer player, Villager villager) {
        return ownedMountedAssignment(level, player, villager) != null;
    }

    private static AbstractHorse ownedMountedAssignment(
            ServerLevel level,
            ServerPlayer player,
            Villager villager) {
        if (level == null
                || player == null
                || villager == null
                || player.serverLevel() != level
                || !HiredVillagerContractService.isHired(level, villager)
                || HiredVillagerContractService.isHiredBy(level, villager, player)
                || !(villager.getVehicle() instanceof AbstractHorse mount)) {
            return null;
        }
        UUID ownerId = mount.getOwnerUUID();
        if (!mount.isTamed()
                || ownerId == null
                || !ownerId.equals(player.getUUID())
                || VillagerMountAssignmentService.assignment(level, villager.getUUID())
                .filter(assignment -> assignment.mountId().equals(mount.getUUID()))
                .isEmpty()) {
            return null;
        }
        return mount;
    }
}
