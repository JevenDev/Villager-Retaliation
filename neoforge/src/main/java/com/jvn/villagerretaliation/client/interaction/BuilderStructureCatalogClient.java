package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.interaction.work.BuilderStructureCatalog;
import com.jvn.villagerretaliation.network.BuilderStructureCatalogSyncPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class BuilderStructureCatalogClient {
    private BuilderStructureCatalogClient() {
    }

    public static void accept(BuilderStructureCatalogSyncPayload payload) {
        BuilderStructureCatalog.replaceClientEntries(payload.entries());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BuilderStructureCatalog.resetClientEntries();
    }
}
