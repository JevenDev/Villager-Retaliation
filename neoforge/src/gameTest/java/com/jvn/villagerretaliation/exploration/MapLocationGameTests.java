package com.jvn.villagerretaliation.exploration;

import com.jvn.villagerretaliation.network.MapLocationRequest;
import com.jvn.villagerretaliation.network.MapLocationsPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("explored_map")
@PrefixGameTestTemplate(false)
public final class MapLocationGameTests {
    private static MapLocation marker(String dimension, String name) {
        return new MapLocation(UUID.randomUUID(), ResourceLocation.withDefaultNamespace(dimension), -512, 1234,
                MapLocation.Icon.OMINOUS_BANNER, name, "Home", true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sharedCopiesPersistWithoutOverwritingPersonalEdits(GameTestHelper helper) {
        var data = new MapLocationSavedData();
        UUID sender = UUID.randomUUID(), recipient = UUID.randomUUID(), stranger = UUID.randomUUID();
        var first = marker("overworld", "Village stores");
        var second = marker("the_nether", "Fortress");
        data.put(sender, first); data.put(sender, second);
        helper.assertTrue(data.locations(recipient).isEmpty(), "locations are private before sharing");
        helper.assertValueEqual(data.share(sender, recipient, UUID.randomUUID()), 0, "unknown marker cannot be shared");
        helper.assertValueEqual(data.share(stranger, recipient, first.id()), 0, "another player's marker cannot be shared");
        helper.assertValueEqual(data.share(sender, recipient, first.id()), 1, "selected share copies only one location");
        var copy = data.locations(recipient).getFirst();
        helper.assertFalse(copy.id().equals(first.id()), "recipient owns a separate marker");
        helper.assertValueEqual(copy.name(), first.name(), "full name survives sharing");
        helper.assertValueEqual(copy.previewName(), first.previewName(), "preview survives sharing");
        var edited = new MapLocation(copy.id(), copy.dimension(), copy.x(), copy.z(), MapLocation.Icon.CHEST, "My stores", "", false);
        data.put(recipient, edited);
        helper.assertValueEqual(data.share(sender, recipient, first.id()), 0, "repeat share is deduplicated");
        helper.assertValueEqual(data.locations(recipient).getFirst(), edited, "repeat share preserves recipient edits");
        helper.assertValueEqual(data.share(sender, recipient, null), 1, "share all includes unshared dimensions only");
        var restored = MapLocationSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.locations(sender), data.locations(sender), "owner markers survive reload");
        helper.assertValueEqual(restored.locations(recipient), data.locations(recipient), "offline recipient copies survive reload");
        restored.remove(sender, first.id());
        helper.assertTrue(restored.locations(recipient).contains(edited), "sender deletion does not remove shared copies");
        helper.assertTrue(restored.locations(stranger).isEmpty(), "unselected players stay private");
        helper.assertValueEqual(edited.label(), "My stores", "empty preview falls back to full name");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void limitsAndMalformedSavedRecordsStayBounded(GameTestHelper helper) {
        var data = new MapLocationSavedData();
        var owner = UUID.randomUUID();
        for (int i = 0; i < MapLocation.MAX_LOCATIONS; i++) helper.assertTrue(data.put(owner, marker("overworld", "Spot " + i)), "room remains");
        helper.assertFalse(data.put(owner, marker("overworld", "Overflow")), "collection has a hard bound");
        var existing = data.locations(owner).getFirst();
        var edited = new MapLocation(existing.id(), existing.dimension(), 4, 5, MapLocation.Icon.IRON_AXE, "Updated", "", false);
        helper.assertTrue(data.put(owner, edited), "full collection still permits edits");
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var entries = saved.getList("Players", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .getList("Locations", net.minecraft.nbt.Tag.TAG_COMPOUND);
        entries.getCompound(0).putString("Dimension", "not a dimension");
        var loaded = MapLocationSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.locations(owner).size(), MapLocation.MAX_LOCATIONS - 1, "bad records do not lose valid records");
        data.remove(owner, existing.id());
        helper.assertTrue(data.put(owner, marker("overworld", "Replacement")), "delete frees a slot");
        expectInvalid(helper, () -> new MapLocation(UUID.randomUUID(), existing.dimension(), Integer.MIN_VALUE, 0,
                existing.icon(), "Bad", "", false));
        expectInvalid(helper, () -> new MapLocation(UUID.randomUUID(), existing.dimension(), 0, 0, existing.icon(), " ", "", false));
        expectInvalid(helper, () -> new MapLocation(UUID.randomUUID(), existing.dimension(), 0, 0, existing.icon(), "a".repeat(81), "", false));
        expectInvalid(helper, () -> new MapLocation(UUID.randomUUID(), existing.dimension(), 0, 0, existing.icon(), "Bad\nname", "", false));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void packetsRoundTripAndRejectOversizedCollections(GameTestHelper helper) {
        var location = marker("the_end", "End city");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (var icon : MapLocation.Icon.values()) {
                var entry = new MapLocation(location.id(), location.dimension(), location.x(), location.z(), icon, location.name(), "City", true);
                var payload = new MapLocationsPayload(List.of(entry), entry.id());
                MapLocationsPayload.STREAM_CODEC.encode(buffer, payload);
                helper.assertValueEqual(MapLocationsPayload.STREAM_CODEC.decode(buffer), payload, "icon and labels round trip");
                buffer.clear();
            }
            var request = new MapLocationRequest(MapLocationRequest.Action.SAVE, location, null, MapLocationRequest.Audience.PARTY, null);
            MapLocationRequest.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(MapLocationRequest.STREAM_CODEC.decode(buffer), request, "save request round trips");
            buffer.clear();
            var share = new MapLocationRequest(MapLocationRequest.Action.SHARE, null, null, MapLocationRequest.Audience.PLAYER, UUID.randomUUID());
            MapLocationRequest.STREAM_CODEC.encode(buffer, share);
            helper.assertValueEqual(MapLocationRequest.STREAM_CODEC.decode(buffer), share, "share-all recipient round trips");
            buffer.clear(); buffer.writeVarInt(MapLocation.MAX_LOCATIONS + 1);
            expectInvalid(helper, () -> MapLocationsPayload.STREAM_CODEC.decode(buffer));
            expectInvalid(helper, () -> new MapLocationRequest(MapLocationRequest.Action.SAVE, null, null, MapLocationRequest.Audience.PARTY, null));
            expectInvalid(helper, () -> new MapLocationRequest(MapLocationRequest.Action.SHARE, null, null, MapLocationRequest.Audience.PLAYER, null));
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sharingAudienceUsesCurrentPartyAndOnlinePlayers(GameTestHelper helper) {
        var sender = helper.makeMockServerPlayerInLevel();
        var online = helper.makeMockServerPlayerInLevel();
        var offline = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "OfflineMember"),
                net.minecraft.server.level.ClientInformation.createDefault());
        try {
            var invitation = com.jvn.villagerretaliation.party.PartyService.sendInvitation(sender, offline);
            helper.assertTrue(invitation.success(), "party invitation succeeds");
            helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.acceptInvitation(offline, invitation.invitationId()).success(),
                    "offline party fixture joins");
            helper.assertValueEqual(MapLocationService.recipients(sender, MapLocationRequest.Audience.PARTY, null),
                    new java.util.LinkedHashSet<>(List.of(offline.getUUID())), "party share includes offline member and excludes owner");
            helper.assertTrue(MapLocationService.recipients(sender, MapLocationRequest.Audience.ONLINE, null).contains(online.getUUID()),
                    "share with everyone includes online player");
            helper.assertFalse(MapLocationService.recipients(sender, MapLocationRequest.Audience.ONLINE, null).contains(offline.getUUID()),
                    "online share excludes offline party member");
            helper.assertValueEqual(MapLocationService.recipients(sender, MapLocationRequest.Audience.PLAYER, online.getUUID()),
                    new java.util.LinkedHashSet<>(List.of(online.getUUID())), "specific share resolves just selected player");
            helper.assertTrue(MapLocationService.recipients(sender, MapLocationRequest.Audience.PLAYER, offline.getUUID()).isEmpty(),
                    "specific player must still be online");
            helper.assertTrue(MapLocationService.recipients(sender, MapLocationRequest.Audience.PLAYER, sender.getUUID()).isEmpty(),
                    "self shares are ignored");
        } finally { sender.discard(); online.discard(); offline.discard(); }
        helper.succeed();
    }

    private static void expectInvalid(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException invalid) { rejected = true; }
        helper.assertTrue(rejected, "invalid location input is rejected");
    }
}
