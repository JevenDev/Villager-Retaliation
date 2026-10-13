package com.jvn.villagerretaliation.storage;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Persisted rectangular memberships; no inventory is ever moved by a topology operation. */
public final class VaultConnections {
    private VaultConnections() { }
    static final Comparator<BlockPos> ORDER = Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);

    public static StorageBlockEntity at(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof StorageBlockEntity bank ? bank : null;
    }
    public static List<StorageBlockEntity> members(StorageBlockEntity bank) {
        var result = new ArrayList<StorageBlockEntity>();
        for (var pos : bank.members) {
            var member = at(bank.getLevel(), pos);
            if (member == null || !member.members.equals(bank.members)) return List.of();
            result.add(member);
        }
        return result;
    }
    public static boolean rectangle(Collection<BlockPos> positions) {
        if (positions.isEmpty() || positions.size() > 27) return false;
        int minX= Integer.MAX_VALUE, minY=minX, minZ=minX, maxX=Integer.MIN_VALUE, maxY=maxX, maxZ=maxX;
        for (var p : positions) {
            minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY()); minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX()); maxY=Math.max(maxY,p.getY()); maxZ=Math.max(maxZ,p.getZ());
        }
        int x=maxX-minX+1,y=maxY-minY+1,z=maxZ-minZ+1;
        return x<=3 && y<=3 && z<=3 && x*y*z==new HashSet<>(positions).size();
    }
    public static void repairMembership(StorageBlockEntity bank) {
        if (bank.safe() || bank.getLevel().isClientSide) return;
        if (!bank.formed) { merge(bank); return; }
        // An unloaded member is not a removed member. Keep the lock and fail closed until it loads.
        if (bank.members.stream().anyMatch(p -> !bank.getLevel().hasChunkAt(p))) return;
        if (members(bank).isEmpty()) reform(bank.getLevel(), bank.members);

    }
    public static void reform(Level level, Collection<BlockPos> old) {
        if (old.stream().anyMatch(p -> !level.hasChunkAt(p))) return;
        var existing = old.stream().map(p -> at(level,p)).filter(Objects::nonNull).filter(b -> !b.safe()).toList();
        for (var b : existing) b.assign(List.of(b.getBlockPos()));
        for (var b : existing.stream().sorted(Comparator.comparing(StorageBlockEntity::getBlockPos,ORDER)).toList()) merge(b, new HashSet<>(old));
        existing.forEach(StorageBlockEntity::reconcileTreasury);
    }
    public static void merge(StorageBlockEntity bank) { merge(bank,null); }
    private static void merge(StorageBlockEntity bank,Set<BlockPos> boundary) {
        if (bank.safe()) return;
        if (!bank.formed) { bank.formed=true; bank.setChanged(); }
        for (int attempt=0; attempt<27; attempt++) {
            var group = members(bank);
            if (group.isEmpty()) return;
            var candidates = new TreeSet<BlockPos>(ORDER);
            for (var b : group) for (var d : Direction.values()) candidates.add(b.getBlockPos().relative(d));
            List<BlockPos> best=null;
            for (var p : candidates) {
                var neighbor=at(bank.getLevel(),p);
                if (neighbor==null || neighbor.safe() || bank.members.contains(p)) continue;
                var other=members(neighbor);
                if (other.isEmpty() || boundary!=null && !boundary.containsAll(neighbor.members)) continue;
                var combined=new ArrayList<>(group); combined.addAll(other);
                if (combined.stream().filter(StorageBlockEntity::controller).count()>1) continue;
                var positions=combined.stream().map(StorageBlockEntity::getBlockPos).distinct().sorted(ORDER).toList();
                if (rectangle(positions) && (best==null || positions.size()>best.size())) best=positions;
            }
            if (best==null) break;
            for (var p : best) at(bank.getLevel(),p).assign(best);
        }
        members(bank).forEach(StorageBlockEntity::reconcileTreasury);
    }
}
