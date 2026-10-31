package com.jvn.villagerretaliation.client.party;

import com.jvn.toucanlib.client.ToucanPlayerSkinResolver;
import java.util.UUID;
import net.minecraft.client.resources.PlayerSkin;

/** Resolves party-player skins without blocking the render thread. */
public final class PartyPlayerSkinResolver {
    private static final ToucanPlayerSkinResolver RESOLVER = new ToucanPlayerSkinResolver();

    private PartyPlayerSkinResolver() {
    }

    public static PlayerSkin resolve(UUID playerId, String playerName) {
        return RESOLVER.resolve(playerId, playerName);
    }

    public static void clear() {
        RESOLVER.clear();
    }
}
