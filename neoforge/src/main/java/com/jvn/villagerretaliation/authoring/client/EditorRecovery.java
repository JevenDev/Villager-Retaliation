package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;

final class EditorRecovery {
    private EditorRecovery() { }

    static void write(AuthoringWorkspace workspace, JsonObject buffers, boolean pending) throws IOException {
        if (workspace.dirty() || pending) workspace.saveRecovery(buffers);
        else workspace.discardRecovery();
    }
}
