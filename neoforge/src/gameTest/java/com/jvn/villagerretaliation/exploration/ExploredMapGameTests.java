package com.jvn.villagerretaliation.exploration;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("explored_map")
@PrefixGameTestTemplate(false)
public final class ExploredMapGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void localVillageEvidenceNeverSendsRemoteCenters(GameTestHelper helper) {
        var level=helper.getLevel(); var origin=helper.absolutePos(new BlockPos(2,2,2)).atY(96);
        level.getChunkAt(origin);
        var manager=level.getPoiManager();
        manager.add(origin,level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
        manager.take(type->type.is(PoiTypes.HOME),(type,pos)->pos.equals(origin),origin,1).orElseThrow();
        var registry=VillageAllegianceRegistrySavedData.get(level);
        var id=registry.discoverAt(level,origin).orElseThrow();
        var player=helper.makeMockServerPlayerInLevel();
        player.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
        try {
            var packet=MapObservationEvents.observation(player);
            helper.assertValueEqual(packet.village(),id.toString(),"actual village identity used");
            helper.assertValueEqual(packet.position(),player.blockPosition(),"only the locally observed player anchor is sent");
            helper.assertValueEqual(packet.name(),registry.canonicalRecord(id).orElseThrow().displayName(),"actual registry name is used");
            // GameTestHelper's mock hardcodes isSpectator=false, regardless of its game mode.
            var spectator = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "map-spectator"),
                    net.minecraft.server.level.ClientInformation.createDefault()) {
                @Override public boolean isSpectator() { return true; }
            };
            spectator.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
            helper.assertTrue(MapObservationEvents.observation(spectator).village().isEmpty(),"spectator does not discover villages");
            spectator.discard();
            BlockPos remote=origin.offset(1_900_000,0,1_900_000);
            helper.assertFalse(level.hasChunkAt(remote),"remote region starts unloaded");
            // Moving a registered player loads its destination as part of ordinary player tracking.
            // An unregistered observer isolates the map's read-only behavior from that mechanism.
            var observer = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "map-observer"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            observer.setPos(remote.getX(),remote.getY(),remote.getZ());
            helper.assertFalse(level.hasChunkAt(remote),"fixture does not preload the observation chunk");
            helper.assertTrue(MapObservationEvents.observation(observer).village().isEmpty(),"unloaded location reveals no village");
            helper.assertFalse(level.hasChunkAt(remote),"observation never generates the remote chunk");
            observer.discard();
        } finally {player.discard();manager.release(origin);manager.remove(origin);registry.archive(id);}
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void identityAndPacketRoundTrip(GameTestHelper helper) {
        var level=helper.getLevel(); var id=MapWorldIdentity.get(level);
        helper.assertValueEqual(MapWorldIdentity.get(level),id,"world identity is durable within the save");
        var packet=new MapObservationPayload(id,level.dimension().location(),helper.absolutePos(BlockPos.ZERO),"real-id","Actual village");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
        try {
            MapObservationPayload.CODEC.encode(buffer,packet);
            helper.assertValueEqual(MapObservationPayload.CODEC.decode(buffer),packet,"native packet retains exact identity/dimension/anchor/name");
        } finally {buffer.release();}
        helper.succeed();
    }
}
