package com.jvn.villagerretaliation.quest.tutorial;
import net.minecraft.core.BlockPos;
/** Stable position decoder; dimension is checked independently by the run's binding. */
public final class TutorialFarmingPlanPosition {
    private TutorialFarmingPlanPosition(){}
    public static BlockPos parse(String target){
        try{String[] xyz=target.substring(target.lastIndexOf('/')+1).split(",");return new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));}
        catch(RuntimeException invalid){return null;}
    }
}
