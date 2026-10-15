package com.jvn.villagerretaliation.quest.tutorial;
import com.google.gson.*;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import net.minecraft.resources.ResourceLocation;
/** Checkpoints are authored beside their instruction cards and re-evaluated on the server on every acknowledgement. */
public final class TutorialLessonCheckpoints{
    private TutorialLessonCheckpoints(){}
    public static String acknowledge(DialogueContext context,TutorialRun run,String step){
        if(!run.binding().worker().equals(context.villager().getUUID()))return "Show this step to your bound worker, or explicitly choose a replacement first.";
        var resource=ResourceLocation.fromNamespaceAndPath("villagerretaliation","clipboard_tutorials/"+run.binding().role()+".json");
        try(var reader=context.level().getServer().getResourceManager().getResource(resource).orElseThrow().openAsReader()){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var checks=root.getAsJsonObject("checkpoints");
            if(checks==null||!checks.has(step))return "The authored checkpoint is unavailable; no progress was granted.";
            for(var raw:checks.getAsJsonArray(step)){
                var condition=WorkStatePredicate.read(raw.getAsJsonObject());
                if(condition==null)return "This checkpoint has an invalid server condition.";
                var result=condition.evaluate(context.level(),context.player());if(!result.matches())return result.reason();
            }
            run.learn(step);TutorialSavedData.get(context.level()).setDirty();return "";
        }catch(java.io.IOException|RuntimeException invalid){return "The lesson checkpoint could not be loaded; no progress was granted.";}
    }
}
