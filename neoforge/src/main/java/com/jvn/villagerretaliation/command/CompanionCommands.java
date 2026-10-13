package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.party.CompanionAuthority;
import com.jvn.villagerretaliation.party.CompanionLifecycleService;
import com.jvn.villagerretaliation.party.CompanionSavedData;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartySyncService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

final class CompanionCommands {
    private CompanionCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("companion")
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    for (var state : CompanionSavedData.get(player.serverLevel()).forPlayer(player.getUUID())) {
                        player.sendSystemMessage(Component.literal(state.getString("Name") + " [" + state.getUUID("Villager") + "]: "
                                + state.getString("Status") + " - " + state.getString("Reason") + " at "
                                + state.getString("LastDimension") + " " + net.minecraft.core.BlockPos.of(state.getLong("LastPosition"))));
                    }
                    return 1;
                })
                .then(literal("entrust").then(argument("villager", UuidArgument.uuid())
                        .then(argument("player", EntityArgument.player()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var id = UuidArgument.getUuid(context, "villager");
                            var party = PartyService.getPartyForVillager(player.serverLevel(), id).orElse(null);
                            var record = party == null ? null : party.villager(id);
                            var target = EntityArgument.getPlayer(context, "player");
                            if (!CompanionAuthority.canManage(player.getUUID(), party, record)
                                    || !party.playerIds().contains(target.getUUID())) return denied(context.getSource());
                            record.assignCommander(target.getUUID());
                            PartyService.markChanged(player.serverLevel());
                            var loaded = VillagerEntityResolver.loaded(player.getServer(), id);
                            if (loaded != null) PartyVillagerContractService.refreshTransferredOwnership((net.minecraft.server.level.ServerLevel)loaded.level(), loaded);
                            PartySyncService.syncParty(player.getServer(), party.id());
                            player.sendSystemMessage(Component.literal("Assigned commander: " + target.getName().getString()));
                            return 1;
                        }))))
                .then(literal("safehome").then(argument("villager", UuidArgument.uuid()).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    Villager villager = VillagerEntityResolver.loaded(player.getServer(), UuidArgument.getUuid(context, "villager"));
                    return villager != null && CompanionLifecycleService.setSafeHome(player, villager)
                            ? accepted(context.getSource(), "Agreed this safe location as home") : denied(context.getSource());
                })))
                .then(literal("offer").then(argument("quest", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .then(argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 35))
                                .then(argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99)).executes(context ->
                                        com.jvn.villagerretaliation.quest.QuestContributionService.offer(context.getSource().getPlayerOrException(), null,
                                                net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "quest"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "slot"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count")) ? 1 : denied(context.getSource()))))))
                .then(literal("offersupplies").then(argument("villager", UuidArgument.uuid())
                        .then(argument("quest", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .then(argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(6, 41))
                                        .then(argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99)).executes(context -> {
                                            var player = context.getSource().getPlayerOrException();
                                            var villager = VillagerEntityResolver.loaded(player.getServer(), UuidArgument.getUuid(context, "villager"));
                                            return villager != null && com.jvn.villagerretaliation.quest.QuestContributionService.offer(player, villager,
                                                    net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "quest"),
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "slot"),
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count")) ? 1 : denied(context.getSource());
                                        }))))))
                .then(setting("dimensionreturn", "AllowDimensionReturn"))
                .then(setting("equipment", "CommanderEquipmentAccess"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> setting(String command, String field) {
        return literal(command).then(argument("villager", UuidArgument.uuid())
                .then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var id = UuidArgument.getUuid(context, "villager");
                    if (!player.getUUID().equals(CompanionLifecycleService.owner(player.serverLevel(), id))) return denied(context.getSource());
                    var state = CompanionSavedData.get(player.serverLevel()).find(id);
                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                    if (field.equals("CommanderEquipmentAccess")) {
                        var party = PartyService.getPartyForVillager(player.serverLevel(), id).orElse(null);
                        if (party == null) return denied(context.getSource());
                        party.villager(id).setCommanderEquipmentAccess(enabled);
                        PartyService.markChanged(player.serverLevel());
                        PartySyncService.syncParty(player.getServer(), party.id());
                    } else {
                        state.putBoolean(field, enabled);
                        CompanionSavedData.get(player.serverLevel()).setDirty();
                    }
                    return accepted(context.getSource(), command + ": " + enabled);
                })));
    }

    private static int denied(CommandSourceStack source) {
        source.sendFailure(Component.translatable("villagerretaliation.party.error.companion_owner_required"));
        return 0;
    }

    private static int accepted(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
