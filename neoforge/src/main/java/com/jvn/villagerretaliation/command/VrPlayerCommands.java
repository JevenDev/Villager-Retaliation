package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.duel.DuelKitRegistry;
import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.duel.PlayerDuelService;
import com.jvn.villagerretaliation.party.PartyActionHandler;
import com.jvn.villagerretaliation.party.PartyService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;

final class VrPlayerCommands {
    private VrPlayerCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("vr")
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("villagerretaliation.command.help"),
                            false);
                    return 1;
                })
                .then(party())
                .then(duel());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> party() {
        return literal("party")
                .executes(context -> {
                    context.getSource().getPlayerOrException().sendSystemMessage(
                            Component.translatable("villagerretaliation.party.command.help"));
                    return 1;
                })
                .then(literal("create")
                        .executes(context -> {
                            PartyActionHandler.createPartyCommand(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(literal("invite")
                        .then(argument("player", EntityArgument.player())
                                .executes(context -> {
                                    PartyActionHandler.sendInvitationCommand(
                                            context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "player"));
                                    return 1;
                                })))
                .then(literal("accept")
                        .executes(context -> {
                            PartyActionHandler.acceptLatestInvitationCommand(
                                    context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> {
                                    PartyActionHandler.acceptInvitationFromCommand(
                                            context.getSource().getPlayerOrException(),
                                            singleProfileId(context, "player"));
                                    return 1;
                                })))
                .then(literal("decline")
                        .executes(context -> {
                            PartyActionHandler.declineLatestInvitationCommand(
                                    context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> {
                                    PartyActionHandler.declineInvitationFromCommand(
                                            context.getSource().getPlayerOrException(),
                                            singleProfileId(context, "player"));
                                    return 1;
                                })))
                .then(literal("leave")
                        .executes(context -> {
                            PartyActionHandler.leavePartyCommand(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(literal("kick")
                        .then(argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> {
                                    PartyActionHandler.removePlayerCommand(
                                            context.getSource().getPlayerOrException(),
                                            singleProfileId(context, "player"));
                                    return 1;
                                })))
                .then(literal("promote")
                        .then(argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> {
                                    PartyActionHandler.promoteLeaderCommand(
                                            context.getSource().getPlayerOrException(),
                                            singleProfileId(context, "player"));
                                    return 1;
                                })))
                .then(literal("disband")
                        .executes(context -> {
                            PartyActionHandler.disbandCommand(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(literal("alliance")
                        .then(alliance("request", PartyService.AllianceAction.REQUEST))
                        .then(alliance("accept", PartyService.AllianceAction.ACCEPT))
                        .then(alliance("cancel", PartyService.AllianceAction.CANCEL_REQUEST))
                        .then(alliance("end", PartyService.AllianceAction.END)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> alliance(
            String command,
            PartyService.AllianceAction action) {
        return literal(command)
                .then(argument("player", GameProfileArgument.gameProfile())
                        .executes(context -> {
                            PartyActionHandler.allianceCommand(
                                    context.getSource().getPlayerOrException(),
                                    singleProfileId(context, "player"),
                                    action);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> duel() {
        return literal("duel")
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("villagerretaliation.player_duel.command.help"),
                            false);
                    return 1;
                })
                .then(literal("challenge")
                        .then(argument("player", EntityArgument.player())
                                .executes(context -> challenge(context, "byo", 0))
                                .then(argument("kit", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                DuelKitRegistry.values().stream()
                                                        .map(kit -> kit.id().toString()),
                                                builder))
                                        .executes(context -> challenge(
                                                context,
                                                StringArgumentType.getString(context, "kit"),
                                                0))
                                        .then(argument("wager", IntegerArgumentType.integer(0))
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(DuelService.FIXED_STAKES)
                                                                .mapToObj(Integer::toString),
                                                        builder))
                                                .executes(context -> challenge(
                                                        context,
                                                        StringArgumentType.getString(context, "kit"),
                                                        IntegerArgumentType.getInteger(context, "wager")))))))
                .then(literal("accept")
                        .executes(context -> {
                            PlayerDuelService.acceptLatest(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(argument("player", EntityArgument.player())
                                .executes(context -> {
                                    PlayerDuelService.accept(
                                            context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "player"));
                                    return 1;
                                })))
                .then(literal("decline")
                        .executes(context -> {
                            PlayerDuelService.declineLatest(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(argument("player", EntityArgument.player())
                                .executes(context -> {
                                    PlayerDuelService.decline(
                                            context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "player"));
                                    return 1;
                                })));
    }

    private static int challenge(
            CommandContext<CommandSourceStack> context,
            String kit,
            int wager) throws CommandSyntaxException {
        PlayerDuelService.challenge(
                context.getSource().getPlayerOrException(),
                EntityArgument.getPlayer(context, "player"),
                DuelKitRegistry.resolveId(kit),
                wager);
        return 1;
    }

    private static UUID singleProfileId(
            CommandContext<CommandSourceStack> context,
            String argumentName) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, argumentName);
        if (profiles.size() != 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
        }
        return profiles.iterator().next().getId();
    }
}
