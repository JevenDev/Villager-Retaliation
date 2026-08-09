package com.jvn.villagerretaliation.command;

import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class VrCommandRegistration {
    private VrCommandRegistration() {
    }

    static void register(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        dispatcher.register(
                VrPlayerCommands.root()
                        .then(VrAdminCommands.root()));

        // Compatibility roots for command blocks and server scripts. New output only advertises /vr.
        dispatcher.register(VillagerRetaliationCommands.legacyRootCommands());
        dispatcher.register(VillagerRetaliationCommands.playerDuelCommands());
    }
}
