package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.courier.TransferManagementService;
import com.jvn.villagerretaliation.network.ProductionTarget;
import com.jvn.villagerretaliation.network.TransferRequestPayload;
import com.jvn.villagerretaliation.network.TransferSnapshotPayload;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementTransferManagementGameTests {
    private SettlementTransferManagementGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_management")
    public static void transferSnapshotsRequireViewingAndSeparateEditingAuthority(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var target = new ProductionTarget(fixture.town().id().value(), fixture.area().id());
            var request = new TransferRequestPayload(target, UUID.randomUUID(), 0L, new TransferRequestPayload.View());
            var guest = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TransferViewer"));
            fixture.source().observeRules(java.util.List.of());
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 8));
            var denied = TransferManagementService.snapshot(guest, request, false, "");
            helper.assertTrue(denied.queue() == null && denied.storages().isEmpty() && denied.products().isEmpty(), "private town logistics are not disclosed");
            fixture.town().setPermissions(fixture.leader().getUUID(), guest.getUUID(), Set.of(SettlementPermission.VIEW));
            var legacy = TransferManagementService.snapshot(guest, request, false, "");
            helper.assertFalse(legacy.editable(), "viewing does not authorize adopting old worksites");
            helper.assertTrue(legacy.blocker().contains("Adopt"), "the old worksite explains its opt-in requirement");
            SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER), true);
            var viewed = TransferManagementService.snapshot(guest, request, false, "");
            helper.assertFalse(viewed.editable(), "view-only delegates cannot issue shipments");
            helper.assertTrue(viewed.products().stream().anyMatch(product -> product.sample().is(Items.IRON_INGOT)), "authorized viewers see observed source goods");
            helper.assertTrue(TransferManagementService.snapshot(fixture.leader(), request, false, "").editable(), "the authorized steward can manage transfers");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_management")
    public static void concurrentTransferEditorsRejectStaleAndRevokedActions(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER), true);
            var target = new ProductionTarget(fixture.town().id().value(), fixture.area().id());
            var command = new TransferRequestPayload.Append(new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                    new HiredTransferQueue.Endpoint(fixture.destination().position(), fixture.destination().id()), new ItemStack(Items.BREAD), 9);
            var first = new TransferRequestPayload(target, UUID.randomUUID(), 0L, command);
            helper.assertTrue(TransferManagementService.apply(fixture.leader(), first).isEmpty(), "the first editor appends one shared shipment");
            helper.assertFalse(TransferManagementService.apply(fixture.leader(), first).isEmpty(), "replaying the packet does not duplicate it");
            var delegate = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TransferEditor"));
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(),
                    Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.LOGISTICS));
            helper.assertFalse(TransferManagementService.apply(delegate, new TransferRequestPayload(target, UUID.randomUUID(), 0L, command)).isEmpty(),
                    "a second editor cannot overwrite a newer revision");
            var queue = fixture.town().transferQueues().get(fixture.area().id());
            var cancel = new TransferRequestPayload(target, UUID.randomUUID(), queue.revision(),
                    new TransferRequestPayload.Edit(queue.orders().getFirst().id(), HiredTransferQueue.Edit.CANCEL, 0));
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF));
            helper.assertFalse(TransferManagementService.apply(delegate, cancel).isEmpty(), "revoking logistics blocks the pending edit");
            helper.assertValueEqual(queue.orders().size(), 1, "one accepted request creates exactly one order");
            helper.assertFalse(queue.orders().getFirst().cancelled(), "a rejected edit leaves shared work unchanged");
            var forged = new TransferRequestPayload(new ProductionTarget(null, fixture.courier().getUUID()), UUID.randomUUID(), 0L, command);
            helper.assertFalse(TransferManagementService.apply(fixture.leader(), forged).isEmpty(), "civic staff cannot be addressed as personal hires");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_management")
    public static void transferWireFormatPreservesEndpointsComponentsAndAcknowledgements(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var level = helper.getLevel();
            SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER), true);
            var sample = new ItemStack(Items.IRON_INGOT);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Exact shipment"));
            var command = new TransferRequestPayload.Append(new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                    new HiredTransferQueue.Endpoint(fixture.destination().position(), fixture.destination().id()), sample, 23);
            var request = new TransferRequestPayload(new ProductionTarget(fixture.town().id().value(), fixture.area().id()), UUID.randomUUID(), 0L, command);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try {
                TransferRequestPayload.STREAM_CODEC.encode(buffer, request);
                var decoded = TransferRequestPayload.STREAM_CODEC.decode(buffer);
                var append = (TransferRequestPayload.Append) decoded.command();
                helper.assertValueEqual(decoded.target(), request.target(), "worksite identity survives the packet");
                helper.assertValueEqual(decoded.request(), request.request(), "request identity survives the packet");
                helper.assertTrue(ItemStack.isSameItemSameComponents(append.sample(), sample), "exact components survive the packet");
                helper.assertValueEqual(append.source(), command.source(), "source identity and position stay linked");
                helper.assertValueEqual(append.destination(), command.destination(), "destination identity and position stay linked");
                helper.assertTrue(TransferManagementService.apply(fixture.leader(), decoded).isEmpty(), "the decoded shipment is valid");
                var snapshot = TransferManagementService.snapshot(fixture.leader(), decoded, true, "");
                TransferSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
                var restored = TransferSnapshotPayload.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(restored.request(), request.request(), "snapshots acknowledge the exact request");
                helper.assertValueEqual(restored.queue(), snapshot.queue(), "queue progress survives the wire format");
                helper.assertTrue(restored.applied() && restored.editable(), "the accepted action retains its authority result");
                boolean rejected = false;
                try { new TransferRequestPayload.Append(command.source(), command.destination(), sample, 4097); }
                catch (IllegalArgumentException invalid) { rejected = true; }
                helper.assertTrue(rejected, "oversized quantities are rejected");
            } finally { buffer.release(); }
        });
    }
}
