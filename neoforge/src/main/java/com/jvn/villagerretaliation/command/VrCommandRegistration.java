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
    }
}
