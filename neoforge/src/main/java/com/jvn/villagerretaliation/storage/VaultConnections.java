package com.jvn.villagerretaliation.storage;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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
    public static List<BlockPos> positions(StorageBlockEntity controller) {
        var center=controller.getBlockPos().relative(controller.getBlockState().getValue(StorageBlock.FACING).getOpposite());
        return BlockPos.betweenClosedStream(center.offset(-1,-1,-1),center.offset(1,1,1))
                .map(BlockPos::immutable).sorted(ORDER).toList();
    }
    private static boolean complete(StorageBlockEntity controller) {
        var positions=positions(controller);
        for(var pos:positions) {
            var member=at(controller.getLevel(),pos);
            if(member==null || member.safe() || member.controller() && member!=controller) return false;
            if(member.vaultFormed() && !member.members.equals(positions)) return false;
        }
        return true;
    }
    private static boolean validMembership(StorageBlockEntity bank) {
        var group=members(bank);
        var controller=group.stream().filter(StorageBlockEntity::controller).findFirst().orElse(null);
        return group.size()==27 && controller!=null && bank.members.equals(positions(controller)) && complete(controller);
    }
    public static void repairMembership(StorageBlockEntity bank) {
        if(bank.safe() || bank.getLevel().isClientSide) return;
        // keep saved inventories unavailable until every previous member can be checked
        if(bank.members.stream().anyMatch(p->!bank.getLevel().hasChunkAt(p))) return;
        if(bank.members.size()>1) {
            if(!validMembership(bank)) reform(bank.getLevel(),bank.members);
            else if(!bank.vaultFormed()) bank.assign(bank.members);
        } else {
            if(bank.getBlockState().getValue(StorageBlock.FORMED)) bank.assign(List.of(bank.getBlockPos()));
            if(!bank.formed || bank.controller()) merge(bank);
        }
    }
    public static void reform(Level level,Collection<BlockPos> old) {
        if(old.stream().anyMatch(p->!level.hasChunkAt(p))) return;
        var existing=old.stream().map(p->at(level,p)).filter(Objects::nonNull).filter(b->!b.safe()).toList();
        for(var bank:existing) bank.assign(List.of(bank.getBlockPos()));
        // replacement states finish updating after onRemove returns
        for(var bank:existing) if(bank.controller()) level.scheduleTick(bank.getBlockPos(),bank.getBlockState().getBlock(),1);
        existing.forEach(StorageBlockEntity::reconcileTreasury);
    }
    public static void merge(StorageBlockEntity bank) {
        if(bank.safe() || bank.getLevel().isClientSide) return;
        if(bank.members.stream().anyMatch(p->!bank.getLevel().hasChunkAt(p))) return;
        if(bank.members.size()>1 && !validMembership(bank)) {
            reform(bank.getLevel(),bank.members);
            return;
        }
        if(!bank.formed) bank.assign(List.of(bank.getBlockPos()));
        for(var pos:BlockPos.betweenClosed(bank.getBlockPos().offset(-2,-2,-2),bank.getBlockPos().offset(2,2,2))) {
            var controller=at(bank.getLevel(),pos);
            if(controller==null || controller.safe() || !controller.controller() || !positions(controller).contains(bank.getBlockPos()) || !complete(controller)) continue;
            var positions=positions(controller);
            var group=positions.stream().map(p->at(bank.getLevel(),p)).toList();
            if(group.stream().anyMatch(member->member.members.stream().anyMatch(p->!bank.getLevel().hasChunkAt(p)))) continue;
            if(group.stream().anyMatch(member->member.members.size()>1 && !member.members.equals(positions))) continue;
            for(var member:group) member.assign(positions);
            group.forEach(StorageBlockEntity::reconcileTreasury);
            return;
        }
        bank.refreshProtection();
    }
}
