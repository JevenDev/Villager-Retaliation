package com.jvn.villagerretaliation.client.party;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;

/** Resolves party-player skins without blocking the render thread. */
public final class PartyPlayerSkinResolver {
    private static final long RETRY_COOLDOWN_MS = 30_000L;
    private static final int MAX_CACHE_ENTRIES = 16;
    private static final AtomicInteger THREAD_ID = new AtomicInteger(1);
    private static final ExecutorService LOOKUP_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "villagerretaliation-skin-lookup-" + THREAD_ID.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, PlayerSkin> SKINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RETRY_AT = new ConcurrentHashMap<>();
    private static final Set<UUID> REQUESTS_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final AtomicLong GENERATION = new AtomicLong();

    private PartyPlayerSkinResolver() {
    }

    public static PlayerSkin resolve(UUID playerId, String playerName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(playerId);
            if (playerInfo != null) {
                PlayerSkin skin = playerInfo.getSkin();
                cache(playerId, skin);
                REQUESTS_IN_FLIGHT.remove(playerId);
                RETRY_AT.remove(playerId);
                return skin;
            }
        }

        PlayerSkin cached = SKINS.get(playerId);
        if (cached != null) return cached;

        GameProfile fallbackProfile = new GameProfile(playerId, safeName(playerName));
        if (retryAllowed(playerId) && REQUESTS_IN_FLIGHT.add(playerId)) {
            queueSkinLoad(minecraft, playerId, fallbackProfile, GENERATION.get());
        }
        return minecraft.getSkinManager().getInsecureSkin(fallbackProfile);
    }

    public static void clear() {
        GENERATION.incrementAndGet();
        SKINS.clear();
        RETRY_AT.clear();
        REQUESTS_IN_FLIGHT.clear();
    }

    private static void queueSkinLoad(
            Minecraft minecraft, UUID playerId, GameProfile fallbackProfile, long generation) {
        CompletableFuture
                .supplyAsync(() -> fetchProfile(minecraft, fallbackProfile), LOOKUP_EXECUTOR)
                .thenCompose(resolved -> minecraft.getSkinManager()
                        .getOrLoad(resolved.profile())
                        .thenApply(skin -> new LoadedSkin(skin, resolved.hasPackedTextures())))
                .whenComplete((loaded, throwable) -> {
                    if (generation != GENERATION.get()) return;
                    if (throwable == null
                            && loaded != null
                            && loaded.skin() != null
                            && loaded.hasPackedTextures()) {
                        cache(playerId, loaded.skin());
                        RETRY_AT.remove(playerId);
                    } else {
                        RETRY_AT.put(playerId, Util.getMillis() + RETRY_COOLDOWN_MS);
                    }
                    REQUESTS_IN_FLIGHT.remove(playerId);
                });
    }

    private static ResolvedProfile fetchProfile(Minecraft minecraft, GameProfile fallbackProfile) {
        try {
            var sessionService = minecraft.getMinecraftSessionService();
            var result = sessionService.fetchProfile(fallbackProfile.getId(), false);
            GameProfile profile = result == null || result.profile() == null
                    ? fallbackProfile
                    : result.profile();
            return new ResolvedProfile(profile, sessionService.getPackedTextures(profile) != null);
        } catch (RuntimeException ignored) {
            return new ResolvedProfile(fallbackProfile, false);
        }
    }
    private static boolean retryAllowed(UUID playerId) {
        Long retryAt = RETRY_AT.get(playerId);
        return retryAt == null || Util.getMillis() >= retryAt;
    }

    private static String safeName(String playerName) {
        return playerName == null || playerName.isBlank() ? "Player" : playerName;
    }

    private record ResolvedProfile(GameProfile profile, boolean hasPackedTextures) {
    }

    private record LoadedSkin(PlayerSkin skin, boolean hasPackedTextures) {
    }
    private static void cache(UUID playerId, PlayerSkin skin) {
        if (!SKINS.containsKey(playerId) && SKINS.size() >= MAX_CACHE_ENTRIES) {
            var iterator = SKINS.keySet().iterator();
            if (iterator.hasNext()) SKINS.remove(iterator.next());
        }
        SKINS.put(playerId, skin);
    }
}