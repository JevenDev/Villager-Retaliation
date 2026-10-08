package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.campaign.CampaignBlockOwnershipLedger.Receipt;
import com.jvn.villagerretaliation.campaign.CampaignBlockOwnershipLedger.State;
import com.jvn.villagerretaliation.util.WorldChunkCheckpoint;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CampaignBlockOwnership {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerRetaliation.MOD_ID);
    static final Supplier<AttachmentType<CampaignBlockOwnershipLedger>> OWNERSHIP = TYPES.register("campaign_blocks",
            () -> AttachmentType.builder(CampaignBlockOwnershipLedger::new)
                    .serialize(CompoundTag.CODEC.xmap(CampaignBlockOwnershipLedger::load, CampaignBlockOwnershipLedger::save)).build());
    private static final ThreadLocal<Mutation> MUTATION = new ThreadLocal<>();

    private CampaignBlockOwnership() { }

    public static void register(IEventBus bus) { TYPES.register(bus); }

    public static Receipt receipt(LevelChunk chunk, BlockPos position) {
        var ledger = chunk.getExistingDataOrNull(OWNERSHIP.get());
        return ledger == null ? null : ledger.get(position);
    }

    public static String diagnostic(LevelChunk chunk) {
        var ledger = chunk.getExistingDataOrNull(OWNERSHIP.get());
        return ledger == null ? "" : ledger.diagnostic();
    }

    public static Result reserve(ServerLevel level, Receipt requested) {
        var chunk = loaded(level, requested.position());
        if (chunk == null) return waiting("Campaign block ownership requires its loaded chunk on the server thread.");
        var ledger = chunk.getData(OWNERSHIP);
        if (!ledger.diagnostic().isBlank()) return waiting(ledger.diagnostic());
        var prior = ledger.get(requested.position());
        try {
            if (prior != null && prior.sameIdentity(requested)) {
                persist(level, chunk, requested.position());
                return prior.state() == State.CONFLICT ? conflict(prior.diagnostic()) : new Result(Status.READY, "");
            }
            if (prior != null && prior.state() != State.RESTORED)
                return conflict("Another operation owns this block, or its ownership was lost to a world edit.");
            if (!CampaignBlockSnapshot.capture(level, chunk, requested.position()).equals(requested.original()))
                return conflict("The block no longer matches the authored expected state.");
            ledger.record(new Receipt(requested.position(), requested.owner(), requested.operation(), requested.original(), requested.placed(), State.RESERVED, ""));
            chunk.setUnsaved(true);
            persist(level, chunk, requested.position());
            return new Result(Status.READY, "");
        } catch (IOException exception) {
            return waiting("Campaign block ownership could not be saved: " + exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return waiting(exception.getMessage());
        }
    }

    public static Result apply(ServerLevel level, Receipt requested) { return change(level, requested, false); }
    public static Result restore(ServerLevel level, Receipt requested) { return change(level, requested, true); }

    public static Result preserve(ServerLevel level, BlockPos position, UUID owner, UUID operation) {
        var chunk = loaded(level, position);
        if (chunk == null) return waiting("Load the campaign block chunk before preserving it.");
        try {
            var ledger = chunk.getExistingDataOrNull(OWNERSHIP.get());
            var receipt = ledger == null ? null : ledger.get(position);
            if (receipt != null && receipt.owner().equals(owner) && (operation == null || receipt.operation().equals(operation))
                    && receipt.state() != State.RESTORED && receipt.state() != State.CONFLICT) {
                ledger.record(receipt.conflict("An operator preserved this block and retired the campaign change."));
                chunk.setUnsaved(true);
            }
            persist(level, chunk, position);
            return new Result(Status.PRESERVED, "");
        } catch (IOException exception) {
            return waiting("The preserved campaign block could not be saved: " + exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return waiting(exception.getMessage());
        }
    }

    private static Result change(ServerLevel level, Receipt requested, boolean restore) {
        var chunk = loaded(level, requested.position());
        if (chunk == null) return waiting("The campaign block chunk must be loaded before changing it.");
        var ledger = chunk.getExistingDataOrNull(OWNERSHIP.get());
        if (ledger == null) return waiting("Campaign block ownership is missing. Restore its chunk receipt before continuing.");
        if (!ledger.diagnostic().isBlank()) return waiting(ledger.diagnostic());
        var receipt = ledger.get(requested.position());
        if (receipt == null) return waiting("The campaign block ownership receipt is missing. Restore its chunk data before continuing.");
        if (!receipt.sameIdentity(requested)) return conflict("The block no longer belongs to its original campaign operation.");
        try {
            if (receipt.state() == State.CONFLICT) {
                persist(level, chunk, requested.position());
                return conflict(receipt.diagnostic());
            }
            if (receipt.state() == State.RESTORED) {
                if (!restore) return conflict("This campaign block change was already restored. Start a new authored operation.");
                persist(level, chunk, receipt.position());
                return new Result(Status.RESTORED, "");
            }
            var actual = CampaignBlockSnapshot.capture(level, chunk, requested.position());
            var expected = restore ? receipt.placed() : receipt.original();
            var target = restore ? receipt.original() : receipt.placed();
            if (!actual.equals(expected) && !actual.equals(target))
                return preserve(level, chunk, ledger, receipt, "The campaign block or its block entity was edited.");
            if (!actual.equals(target)) {
                persist(level, chunk, requested.position());
                BlockEntity replacement = target.blockEntity() == null ? null :
                        BlockEntity.loadStatic(receipt.position(), target.state(), target.blockEntity(), level.registryAccess());
                if (target.state().hasBlockEntity() && (replacement == null || !replacement.getType().isValid(target.state())
                        || !replacement.saveWithFullMetadata(level.registryAccess()).equals(target.blockEntity())))
                    return waiting("The authored block entity is unavailable or incompatible. Restore its content.");
                var previous = MUTATION.get();
                MUTATION.set(new Mutation(chunk, receipt.position(), receipt.owner(), receipt.operation(), target.state()));
                try {
                    if (actual.state().hasBlockEntity()) level.removeBlockEntity(receipt.position());
                    level.setBlock(receipt.position(), target.state(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                    if (replacement != null) level.setBlockEntity(replacement);
                } finally {
                    if (previous == null) MUTATION.remove();
                    else MUTATION.set(previous);
                }
                if (ledger.get(receipt.position()).state() == State.CONFLICT
                        || !CampaignBlockSnapshot.capture(level, chunk, receipt.position()).equals(target))
                    return preserve(level, chunk, ledger, receipt, "The authored campaign block change was interrupted by another world change.");
            }
            ledger.record(restore ? receipt.restored() : receipt.applied());
            chunk.setUnsaved(true);
            persist(level, chunk, receipt.position());
            return new Result(restore ? Status.RESTORED : Status.APPLIED, "");
        } catch (IOException exception) {
            return waiting("The campaign block change could not be saved: " + exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return waiting(exception.getMessage());
        }
    }

    private static Result preserve(ServerLevel level, LevelChunk chunk, CampaignBlockOwnershipLedger ledger, Receipt receipt, String reason) throws IOException {
        ledger.record(receipt.conflict(reason));
        chunk.setUnsaved(true);
        persist(level, chunk, receipt.position());
        return conflict(reason);
    }

    public static void changed(LevelChunk chunk, BlockPos position, BlockState target) {
        if (!(chunk.getLevel() instanceof ServerLevel)) return;
        var ledger = chunk.getExistingDataOrNull(OWNERSHIP.get());
        if (ledger == null) return;
        var mutation = MUTATION.get();
        var receipt = ledger.get(position);
        if (mutation != null && mutation.matches(chunk, position, receipt) && mutation.target().equals(target)) return;
        if (ledger.changedOutsideOperation(position, "The block was changed outside its owning campaign operation.")) chunk.setUnsaved(true);
    }

    public static void blockEntityChanged(Level level, BlockPos position) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var chunk = loaded(serverLevel, position);
        if (chunk == null) return;
        var ledger = chunk.getExistingDataOrNull(OWNERSHIP.get());
        if (ledger == null) return;
        var mutation = MUTATION.get();
        if (mutation != null && mutation.matches(chunk, position, ledger.get(position))) return;
        if (ledger.changedOutsideOperation(position, "The block entity was changed outside its owning campaign operation.")) chunk.setUnsaved(true);
    }

    private static LevelChunk loaded(ServerLevel level, BlockPos position) {
        return !level.getServer().isSameThread() || level.isOutsideBuildHeight(position) || !level.getWorldBorder().isWithinBounds(position)
                ? null : level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    private static void persist(ServerLevel level, LevelChunk chunk, BlockPos position) throws IOException {
        var ledger = chunk.getExistingDataOrNull(OWNERSHIP.get());
        var ownership = ledger == null ? null : ledger.save();
        var block = CampaignBlockSnapshot.capture(level, chunk, position);
        WorldChunkCheckpoint.persist(level, position, snapshot ->
                java.util.Objects.equals(ownership, snapshot.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).get("villagerretaliation:campaign_blocks"))
                        && java.util.Objects.equals(ownership, chunk.hasData(OWNERSHIP) ? chunk.getData(OWNERSHIP).save() : null)
                        && block.equals(CampaignBlockSnapshot.capture(level, chunk, position)) && block.includedIn(snapshot, position));
    }

    private static Result waiting(String message) { return new Result(Status.WAITING, message); }
    private static Result conflict(String message) { return new Result(Status.CONFLICT, message); }
    public enum Status { READY, APPLIED, RESTORED, PRESERVED, CONFLICT, WAITING }
    public record Result(Status status, String diagnostic) { }

    private record Mutation(LevelChunk chunk, BlockPos position, UUID owner, UUID operation, BlockState target) {
        boolean matches(LevelChunk candidate, BlockPos at, Receipt receipt) {
            return candidate == chunk && position.equals(at) && receipt != null && owner.equals(receipt.owner()) && operation.equals(receipt.operation());
        }
    }
}
