package com.jvn.villagerretaliation.quest.tutorial;
import com.google.gson.*;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
public final class TutorialAuthoringPresets{
    private TutorialAuthoringPresets(){}
    public static JsonObject condition(HiredVillagerRole role,String check){
        var result=new JsonObject();result.addProperty("type","work_state");result.addProperty("tutorial",TutorialService.questId(role).toString());
        result.addProperty("role",role.serializedName());result.addProperty("check",check);
        if(check.equals("storage"))result.addProperty("parameter","supply");
        if(check.equals("settings"))result.addProperty("parameter","assigned_supplies");
        if(check.equals("outcome"))result.addProperty("parameter",switch(role){case FARMING->"harvest";case FISHING->"catch";case MINING->"mine";case LOGGING->"log";case HUNTING->"kill";case ANIMAL_HANDLING->"breed";case BREWING->"brew";case CRAFTSMAN->"craft";case COOK,SMELTER->"process";case COURIER->"pickup";case NITWIT->"report";case COMBAT->"patrol";case BUILDER->"build_complete";});
        return result;
    }
    public static JsonObject objective(HiredVillagerRole role,String check){
        var result=new JsonObject();result.addProperty("id","job_"+check);result.addProperty("type","condition");var conditions=new JsonArray();conditions.add(condition(role,check));result.add("conditions",conditions);return result;
    }
}
