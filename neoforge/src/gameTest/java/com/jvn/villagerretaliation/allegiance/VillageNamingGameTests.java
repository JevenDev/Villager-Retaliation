package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.VillageRenameRequestPayload;
import com.jvn.villagerretaliation.network.VillageRenameResultPayload;
import com.jvn.villagerretaliation.network.VillageRenameResultPayload.Result;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageNamingGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillageNamingGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void namingRechecksTrustAndReportsRecoverableErrors(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bell = helper.absolutePos(new BlockPos(2, 2, 2)).atY(160);
        level.setBlockAndUpdate(bell.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.moveTo(bell.getX() + 0.5D, bell.getY(), bell.getZ() + 0.5D);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        BlockPos home = bell.offset(2, 0, 0);
        var manager = level.getPoiManager();
        manager.add(home, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(PoiTypes.HOME));
        manager.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(home), home, 1).orElseThrow();
        var village = registry.discoverAt(level, home).orElseThrow();
        String originalName = "Naming " + UUID.randomUUID().toString().substring(0, 8);
        registry.rename(village, originalName);
        String reservedName = "Reserved " + UUID.randomUUID().toString().substring(0, 8);
        var other = registry.create(level.getGameTime(), level.dimension().location(), bell.offset(1_000, 0, 1_000),
                reservedName);
        UUID resident = UUID.randomUUID();
        registry.addOrUpdateResident(village, resident, true, level.getGameTime());
        try {
            helper.assertValueEqual(attempt(player, bell, village, "New Home").result(), Result.BANNER_REQUIRED,
                    "a missing banner gets an actionable rejection");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHITE_BANNER, 3));
            helper.assertValueEqual(attempt(player, bell, other, "New Home").result(), Result.VILLAGE_CHANGED,
                    "a bell cannot rename an unrelated village");
            var denied = attempt(player, bell, village, "New Home");
            helper.assertValueEqual(denied.result(), Result.INSUFFICIENT_STANDING,
                    "standing is checked again when saving");
            helper.assertValueEqual(denied.requiredResidents(), 1, "the response includes the required trust count");
            VillagerReputationManager.setReputation(level, resident, player.getUUID(),
                    VillagerRetaliationConfig.REVERED_THRESHOLD.get(), bell);
            helper.assertValueEqual(attempt(player, bell, village, "Bad\u00a7Name").result(), Result.INVALID_NAME,
                    "invalid names report a validation error");
            helper.assertValueEqual(attempt(player, bell, village, reservedName.toLowerCase()).result(), Result.NAME_IN_USE,
                    "duplicate names report a correctable conflict");
            helper.assertValueEqual(registry.record(village).orElseThrow().displayName(), originalName,
                    "failed attempts preserve the existing name");
            String newName = "New " + UUID.randomUUID().toString().substring(0, 8);
            var result = attempt(player, bell, village, "  " + newName + "  ");
            helper.assertValueEqual(result.result(), Result.SUCCESS, "a trusted player can save a unique name");
            helper.assertValueEqual(result.name(), newName, "the response contains the normalized saved name");
            helper.assertValueEqual(registry.record(village).orElseThrow().displayName(), newName,
                    "success is returned after the registry is updated");
            helper.assertValueEqual(player.getOffhandItem().getCount(), 3, "renaming keeps the player's banner");
            var repeated = VillageNamingService.rename(player,
                    new VillageRenameRequestPayload(UUID.randomUUID(), bell, village, "Repeated"));
            helper.assertValueEqual(repeated.result(), Result.TRY_AGAIN, "rapid repeated saves are limited");
            helper.assertValueEqual(registry.record(village).orElseThrow().displayName(), newName,
                    "rate-limited requests cannot alter the saved name");
        } finally {
            ServerboundRequestLimiter.clear(player.getUUID());
            player.discard();
            manager.release(home);
            manager.remove(home);
            registry.archive(village);
            registry.archive(other);
            level.removeBlock(bell, false);
            level.removeBlock(bell.below(), false);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void namingRejectsRemoteRequestsWithoutLoadingChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos remote = player.blockPosition().offset(1_900_000, 0, 1_900_000);
        UUID requestId = UUID.randomUUID();
        try {
            helper.assertFalse(level.hasChunkAt(remote), "the remote bell starts unloaded");
            var result = VillageNamingService.rename(player,
                    new VillageRenameRequestPayload(requestId, remote, VillageAllegianceId.random(), "Remote"));
            helper.assertValueEqual(result.result(), Result.TOO_FAR, "remote requests receive a rejection");
            helper.assertValueEqual(result.requestId(), requestId, "rejections identify the matching form submission");
            helper.assertFalse(level.hasChunkAt(remote), "rejecting a request cannot load its target chunk");
        } finally {
            ServerboundRequestLimiter.clear(player.getUUID());
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void namingMessagesPreserveRequestIdentity(GameTestHelper helper) {
        UUID requestId = UUID.randomUUID();
        var request = new VillageRenameRequestPayload(requestId, BlockPos.ZERO, VillageAllegianceId.random(), "Home");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            VillageRenameRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(VillageRenameRequestPayload.STREAM_CODEC.decode(buffer), request,
                    "the server receives the identity of the form submission");
            for (Result status : Result.values()) {
                buffer.clear();
                var result = new VillageRenameResultPayload(requestId, status, status == Result.SUCCESS ? "Home" : "", 3);
                VillageRenameResultPayload.STREAM_CODEC.encode(buffer, result);
                helper.assertValueEqual(VillageRenameResultPayload.STREAM_CODEC.decode(buffer), result,
                        "every result preserves its request identity and feedback");
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static VillageRenameResultPayload attempt(ServerPlayer player, BlockPos bell,
                                                      VillageAllegianceId village, String name) {
        ServerboundRequestLimiter.clear(player.getUUID());
        return VillageNamingService.rename(player, new VillageRenameRequestPayload(UUID.randomUUID(), bell, village, name));
    }
}
