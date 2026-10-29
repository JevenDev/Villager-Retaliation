package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class SettlementBuildingDraft {
    final UUID request = UUID.randomUUID();
    final ResourceLocation structure;
    final String label;
    BlockPos anchor;
    Rotation rotation = Rotation.NONE;
    UUID builder = com.jvn.villagerretaliation.settlement.SettlementProject.AUTOMATIC_BUILDER;
    boolean placed;
    boolean submitting;
    String failure = "";
    SettlementBuildingPreviewPayload preview;
    private static int sequence;
    private int requestId;
    private long sentAt;
    private long refreshAt;
    private boolean pending;
    private boolean dirty = true;
    private ConstructionBlueprintItem.PreviewData ghost;
    private SettlementBuildingPreviewPayload ghostSource;
    private BlockPos ghostAnchor;

    SettlementBuildingDraft(ResourceLocation structure, String label, BlockPos anchor) {
        this.structure = structure;
        this.label = label;
        this.anchor = anchor;
    }

    void move(BlockPos anchor) {
        if (!this.anchor.equals(anchor) && !this.submitting) {
            this.anchor = anchor;
            changed();
        }
    }

    void rotate(int turns) {
        if (turns == 0 || this.submitting) return;
        this.rotation = this.rotation.getRotated(Rotation.values()[Math.floorMod(turns, 4)]);
        changed();
    }

    void chooseBuilder(UUID builder) {
        if (!java.util.Objects.equals(this.builder, builder)) {
            this.builder = builder;
            changed();
        }
    }

    private void changed() {
        this.dirty = true;
        this.failure = "";
    }

    void tick(UUID town) {
        long now = Util.getMillis();
        if (this.pending && now - this.sentAt > 3_000L) {
            this.pending = false;
            this.dirty = true;
            this.failure = "timeout";
        }
        if (!this.submitting && !this.pending && now - this.sentAt >= 200L && (this.dirty || now >= this.refreshAt)) {
            this.pending = true;
            this.dirty = false;
            this.sentAt = now;
            this.refreshAt = now + 2_000L;
            this.requestId = sequence = sequence == Integer.MAX_VALUE ? 1 : sequence + 1;
            PacketDistributor.sendToServer(new SettlementBuildingPreviewRequestPayload(town, this.requestId,
                    this.structure, this.anchor, this.rotation, this.builder));
        }
    }

    void accept(SettlementBuildingPreviewPayload payload) {
        if (payload.requestId() != this.requestId || !payload.structure().equals(this.structure)) return;
        this.pending = false;
        if (!payload.anchor().equals(this.anchor) || payload.rotation() != this.rotation || this.dirty) return;
        this.preview = payload;
    }

    boolean ready() {
        return this.placed && !this.pending && !this.dirty && !this.submitting && this.preview != null
                && this.preview.failure().isEmpty();
    }

    void submitting() {
        this.submitting = true;
        this.sentAt = Util.getMillis();
    }

    ConstructionBlueprintItem.PreviewData ghost() {
        var minecraft = Minecraft.getInstance();
        if (this.preview == null || this.preview.blocks().isEmpty() || minecraft.level == null) return null;
        if (this.ghost != null && this.ghostSource == this.preview && this.anchor.equals(this.ghostAnchor)
                && this.ghost.dimension().equals(minecraft.level.dimension())) return this.ghost;
        var blocks = this.ghost != null && this.ghostSource == this.preview ? this.ghost.blocks()
                : this.preview.blocks().stream().map(block -> new ConstructionBlueprintItem.PreviewBlock(block.local(), block.state())).toList();
        this.ghostSource = this.preview;
        this.ghostAnchor = this.anchor;
        var min = this.preview.min().subtract(this.preview.origin());
        var max = this.preview.max().subtract(this.preview.origin());
        var origin = this.anchor.offset(-Math.floorDiv(min.getX() + max.getX(), 2), -min.getY(), -Math.floorDiv(min.getZ() + max.getZ(), 2));
        return this.ghost = new ConstructionBlueprintItem.PreviewData(this.request, this.builder, minecraft.level.dimension(), this.structure,
                this.label, origin, this.preview.rotation(), min, max, this.preview.blocks().size(), 0, this.preview.price(), "", "", "",
                false, false, false, false, blocks);
    }

    AABB bounds() {
        var ghost = ghost();
        return ghost == null ? new AABB(this.anchor) : new AABB(Vec3.atLowerCornerOf(ghost.worldMin()), Vec3.atLowerCornerOf(ghost.worldMax().offset(1, 1, 1)));
    }
}
