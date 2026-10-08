package com.jvn.villagerretaliation.campaign;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public record CampaignGeneratedBlock(ResourceLocation layout, ResourceLocation dimension, int revision, BlockPos origin, Rotation rotation, Mirror mirror,
        String cell, BlockPos position, BlockState expected, State state, String diagnostic) {
    public CampaignGeneratedBlock {
        CampaignStateCodec.require(layout != null && dimension != null && revision > 0 && origin != null && rotation != null && mirror != null
                && cell != null && cell.matches(CampaignOutcomeAction.ID_PATTERN) && position != null && expected != null && state != null
                && diagnostic != null && diagnostic.length() <= 512, "invalid generated campaign block");
        CampaignStateCodec.require(BlockPos.of(origin.asLong()).equals(origin) && BlockPos.of(position.asLong()).equals(position)
                && Math.abs((long) origin.getX() - position.getX()) <= 64 && Math.abs((long) origin.getY() - position.getY()) <= 64
                && Math.abs((long) origin.getZ() - position.getZ()) <= 64, "generated campaign block is outside its bounded site");
        CampaignStateCodec.require(state == State.CONFLICT ? !diagnostic.isBlank() : diagnostic.isEmpty(), "invalid generated campaign ownership diagnostic");
        origin = origin.immutable();
        position = position.immutable();
    }

    public UUID site() {
        return UUID.nameUUIDFromBytes((dimension + "/" + layout + "/" + revision + "/" + origin.asLong() + "/" + rotation + "/" + mirror).getBytes(StandardCharsets.UTF_8));
    }

    public UUID operation() { return UUID.nameUUIDFromBytes((site() + "/" + cell).getBytes(StandardCharsets.UTF_8)); }
    public CampaignGeneratedBlock owned() { return new CampaignGeneratedBlock(layout, dimension, revision, origin, rotation, mirror, cell, position, expected, State.OWNED, ""); }
    public CampaignGeneratedBlock conflict(String issue) { return new CampaignGeneratedBlock(layout, dimension, revision, origin, rotation, mirror, cell, position, expected, State.CONFLICT, issue); }
    public boolean sameIntent(CampaignGeneratedBlock other) {
        return other != null && layout.equals(other.layout) && dimension.equals(other.dimension) && revision == other.revision && origin.equals(other.origin) && rotation == other.rotation
                && mirror == other.mirror && cell.equals(other.cell) && position.equals(other.position) && expected.equals(other.expected);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Layout", layout.toString());
        tag.putString("Dimension", dimension.toString());
        tag.putInt("Revision", revision);
        tag.putLong("Origin", origin.asLong());
        tag.putString("Rotation", rotation.name());
        tag.putString("Mirror", mirror.name());
        tag.putString("Cell", cell);
        tag.putLong("Position", position.asLong());
        tag.put("Expected", NbtUtils.writeBlockState(expected));
        tag.putString("State", state.name());
        tag.putString("Diagnostic", diagnostic);
        return tag;
    }

    static CampaignGeneratedBlock load(CompoundTag tag) {
        CampaignStateCodec.require(tag.getAllKeys().equals(java.util.Set.of("Layout", "Dimension", "Revision", "Origin", "Rotation", "Mirror", "Cell", "Position", "Expected", "State", "Diagnostic")),
                "unknown generated campaign block fields");
        for (String field : new String[] {"Layout", "Dimension", "Rotation", "Mirror", "Cell", "State", "Diagnostic"})
            CampaignStateCodec.require(tag.contains(field, Tag.TAG_STRING), "invalid generated campaign block field " + field);
        CampaignStateCodec.require(tag.contains("Revision", Tag.TAG_INT) && tag.contains("Origin", Tag.TAG_LONG)
                && tag.contains("Position", Tag.TAG_LONG) && tag.contains("Expected", Tag.TAG_COMPOUND), "incomplete generated campaign block");
        var encoded = tag.getCompound("Expected");
        var expected = BlockState.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        CampaignStateCodec.require(encoded.equals(NbtUtils.writeBlockState(expected)), "generated campaign block state is unavailable or incompatible");
        return new CampaignGeneratedBlock(ResourceLocation.tryParse(tag.getString("Layout")), ResourceLocation.tryParse(tag.getString("Dimension")), tag.getInt("Revision"), BlockPos.of(tag.getLong("Origin")),
                Rotation.valueOf(tag.getString("Rotation")), Mirror.valueOf(tag.getString("Mirror")), tag.getString("Cell"), BlockPos.of(tag.getLong("Position")),
                expected, State.valueOf(tag.getString("State")), tag.getString("Diagnostic"));
    }

    public enum State { PENDING, OWNED, CONFLICT }
}
