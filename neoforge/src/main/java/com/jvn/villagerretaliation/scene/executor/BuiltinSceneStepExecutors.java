package com.jvn.villagerretaliation.scene.executor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutor;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public final class BuiltinSceneStepExecutors {
    private static boolean registered;
    private BuiltinSceneStepExecutors() { }

    public static synchronized void register() {
        if (registered) return;
        register("wait_ticks",new WaitTicks()); register("wait_condition",new WaitCondition());
        register("move_actor",new MoveActor()); register("face_actor",new Face(false)); register("face_position",new Face(true));
        register("dialogue",new Dialogue()); register("action_batch",new ActionBatch());
        register("quest_transition",new QuestTransition()); register("scene_branch",new Branch());
        register("scene_complete",new Terminal()); register("scene_fail",new Terminal());
        registered=true;
    }
    private static void register(String id,SceneStepExecutor executor){SceneStepExecutors.register(VillagerRetaliation.id(id),executor);}

    private abstract static class Base implements SceneStepExecutor {
        private final RecoveryMode recovery;
        Base(RecoveryMode recovery){this.recovery=recovery;}
        public RecoveryMode recoveryMode(){return recovery;}
        public SceneStepResult prepare(SceneExecutionContext context){return SceneStepResult.ready();}
        public SceneStepResult verify(SceneExecutionContext context){return SceneStepResult.complete();}
        public SceneStepResult reconcile(SceneExecutionContext context){return apply(context);}
    }

    private static final class WaitTicks extends Base {
        WaitTicks(){super(RecoveryMode.NATURALLY_IDEMPOTENT);}
        public SceneStepResult prepare(SceneExecutionContext c){
            if(c.record().durableValues().get("wake_time")==null)c.record().putDurableValue("wake_time",Long.toString(c.gameTime()+Math.max(0,longValue(c,"ticks",0))));
            return SceneStepResult.ready();
        }
        public SceneStepResult apply(SceneExecutionContext c){long wake=durableLong(c,"wake_time",c.gameTime());return c.gameTime()<wake?SceneStepResult.waitUntil(wake,"waiting for authored tick deadline"):SceneStepResult.applied();}
    }

    private static final class WaitCondition extends Base {
        WaitCondition(){super(RecoveryMode.NATURALLY_IDEMPOTENT);}
        public SceneStepResult prepare(SceneExecutionContext c){long timeout=longValue(c,"timeout_ticks",0);if(timeout>0&&c.record().durableValues().get("deadline")==null)c.record().putDurableValue("deadline",Long.toString(c.gameTime()+timeout));return SceneStepResult.ready();}
        public SceneStepResult apply(SceneExecutionContext c){
            DialogueContext dialogue=context(c);if(dialogue==null)return actorUnavailable(c,"condition context actors are unavailable");
            List<DialogueCondition> conditions=DialogueCondition.readList(c.definition().id(),"scene "+c.step().id(),c.step().parameters());
            if(DialogueCondition.matchesAll(dialogue,conditions))return SceneStepResult.applied();
            long deadline=durableLong(c,"deadline",0);if(deadline>0&&c.gameTime()>=deadline)return SceneStepResult.fail("condition_timeout","wait_condition timed out");
            return SceneStepResult.waitUntil(c.gameTime()+Math.max(1,longValue(c,"poll_ticks",20)),"waiting for registered condition");
        }
    }

    private static final class MoveActor extends Base {
        MoveActor(){super(RecoveryMode.WORLD_RECONCILED);}
        public SceneStepResult prepare(SceneExecutionContext c){
            Destination destination=destination(c);if(destination==null)return SceneStepResult.block("destination_missing","move_actor has no resolvable target");
            c.record().putDurableValue("destination_dimension",destination.dimension.toString());c.record().putDurableValue("destination_x",Integer.toString(destination.pos.getX()));
            c.record().putDurableValue("destination_y",Integer.toString(destination.pos.getY()));c.record().putDurableValue("destination_z",Integer.toString(destination.pos.getZ()));
            long timeout=longValue(c,"timeout_ticks",20L*60L);c.record().putDurableValue("deadline",Long.toString(c.gameTime()+timeout));return SceneStepResult.ready();
        }
        public SceneStepResult apply(SceneExecutionContext c){
            Entity actor=actor(c,sourceAlias(c));if(actor==null)return actorUnavailable(c,"moving actor is unavailable or unloaded");
            Destination target=durableDestination(c);if(target==null)return SceneStepResult.block("destination_lost","persisted movement destination is unreadable");
            ServerLevel level=c.server().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,target.dimension));
            if(level==null||!level.hasChunkAt(target.pos))return waitOrTimeout(c,"destination chunk is unloaded");
            if(actor.level()!=level)return movementFailure(c,actor,target,"actor is in another dimension");
            double arrival=Math.max(0.25,doubleValue(c,"arrival_distance",1.5));
            if(actor.distanceToSqr(Vec3.atCenterOf(target.pos))<=arrival*arrival)return SceneStepResult.applied();
            if(!(actor instanceof Mob mob))return movementFailure(c,actor,target,"actor type has no path navigation");
            boolean moving=mob.getNavigation().moveTo(target.pos.getX()+0.5,target.pos.getY(),target.pos.getZ()+0.5,Math.max(0.05,doubleValue(c,"speed",0.55)));
            if(!moving&&mob.getNavigation().isDone())return movementFailure(c,actor,target,"pathfinder could not create a path");
            SceneActorBinding binding=c.instance().actorBindings().get(sourceAlias(c));if(binding!=null)c.instance().replaceBinding(sourceAlias(c),binding.withObservation(level.dimension().location(),actor.blockPosition(),true));
            return waitOrTimeout(c,"actor is moving");
        }
        private SceneStepResult waitOrTimeout(SceneExecutionContext c,String message){return c.gameTime()>=durableLong(c,"deadline",Long.MAX_VALUE)?SceneStepResult.fail("movement_timeout",message):SceneStepResult.waitUntil(c.gameTime()+Math.max(1,longValue(c,"poll_ticks",5)),message);}
        private SceneStepResult movementFailure(SceneExecutionContext c,Entity actor,Destination target,String message){
            String policy=string(c,"path_failure_policy","block").toLowerCase(Locale.ROOT);
            if(policy.equals("skip"))return SceneStepResult.skip();
            if(policy.equals("teleport")&&bool(c,"allow_teleport",false)){actor.teleportTo(target.pos.getX()+0.5,target.pos.getY(),target.pos.getZ()+0.5);return SceneStepResult.applied();}
            return policy.equals("fail")?SceneStepResult.fail("movement_path_failed",message):SceneStepResult.block("movement_path_failed",message);
        }
    }

    private static final class Face extends Base {
        private final boolean position;Face(boolean position){super(RecoveryMode.NATURALLY_IDEMPOTENT);this.position=position;}
        public SceneStepResult apply(SceneExecutionContext c){
            Entity source=actor(c,sourceAlias(c));if(!(source instanceof LivingEntity living))return actorUnavailable(c,"facing actor is unavailable");
            Vec3 target;if(position){Destination destination=destination(c);if(destination==null)return actorUnavailable(c,"face position is unavailable");target=Vec3.atCenterOf(destination.pos);}
            else{Entity entity=actor(c,string(c,"target_actor",""));if(entity==null)return actorUnavailable(c,"face target actor is unavailable");target=entity.getEyePosition();}
            living.lookAt(EntityAnchorArgument.Anchor.EYES,target);return SceneStepResult.applied();
        }
    }

    private static final class Dialogue extends Base {
        Dialogue(){super(RecoveryMode.RECEIPT_REQUIRED);}
        public SceneStepResult apply(SceneExecutionContext c){
            if(!c.step().actors().isEmpty())for(String alias:c.step().actors())if(actor(c,alias)==null){SceneStepResult missing=actorUnavailable(c,"dialogue actor "+alias+" is unavailable");if(missing.outcome()!=SceneStepResult.Outcome.SKIP)return missing;}
            String text=string(c,"text","");if(text.isBlank())return SceneStepResult.fail("dialogue_text_missing","dialogue text is empty");
            List<UUID> recipients=new ArrayList<>(c.instance().participants());if(recipients.isEmpty()&&c.instance().owner().playerId()!=null)recipients.add(c.instance().owner().playerId());
            for(UUID id:recipients){ServerPlayer player=c.server().getPlayerList().getPlayer(id);if(player==null)continue;
                var applied=SceneReceiptGuard.applyOnce(c,"dialogue/"+id,SceneOperationReceipt.Kind.DIALOGUE_DELIVERY,()->player.sendSystemMessage(Component.literal(text)),"recipient="+id);
                if(applied.status()==SceneReceiptGuard.Status.AMBIGUOUS_PREPARED)return SceneStepResult.block("dialogue_delivery_ambiguous","cannot prove whether dialogue was delivered before reload");
                applied.receipt().completed(c.gameTime(),"recipient="+id);
            }return SceneStepResult.applied();
        }
        public SceneStepResult reconcile(SceneExecutionContext c){return apply(c);}
    }

    private static final class ActionBatch extends Base {
        ActionBatch(){super(RecoveryMode.RECEIPT_REQUIRED);}
        public SceneStepResult prepare(SceneExecutionContext c){JsonElement actions=c.step().parameters().get("actions");if(actions==null||!actions.isJsonArray())return SceneStepResult.fail("actions_missing","action_batch requires actions array");int i=0;for(JsonElement raw:actions.getAsJsonArray()){if(!raw.isJsonObject()||string(raw.getAsJsonObject(),"id","").isBlank())return SceneStepResult.fail("action_id_missing","action_batch action["+i+"] requires stable id");i++;}return SceneStepResult.ready();}
        public SceneStepResult apply(SceneExecutionContext c){DialogueContext dialogue=context(c);if(dialogue==null)return actorUnavailable(c,"action context actors are unavailable");
            for(JsonElement raw:c.step().parameters().getAsJsonArray("actions")){JsonObject object=raw.getAsJsonObject();String id=string(object,"id","");JsonObject wrapper=new JsonObject();JsonArray array=new JsonArray();array.add(object);wrapper.add("actions",array);
                List<VillagerActionDefinition> parsed=VillagerActionDefinition.readList(c.definition().id(),"scene action "+id,wrapper,null);if(parsed.size()!=1)return SceneStepResult.fail("action_invalid","safe action "+id+" did not parse");var action=parsed.getFirst();SceneOperationReceipt.Kind kind=receiptKind(action);
                var guarded=SceneReceiptGuard.applyOnce(c,"action/"+id,kind,()->VillagerActionRegistry.execute(dialogue,action,java.util.Map.of()),"action="+id);
                if(guarded.status()==SceneReceiptGuard.Status.AMBIGUOUS_PREPARED)return SceneStepResult.block("action_ambiguous","cannot prove whether action "+id+" applied before reload");guarded.receipt().completed(c.gameTime(),"action="+id);
            }return SceneStepResult.applied();}
        public SceneStepResult reconcile(SceneExecutionContext c){return apply(c);}
    }

    private static final class QuestTransition extends Base {
        QuestTransition(){super(RecoveryMode.RECEIPT_REQUIRED);}
        public SceneStepResult apply(SceneExecutionContext c){DialogueContext dialogue=context(c);if(dialogue==null)return actorUnavailable(c,"quest transition context is unavailable");JsonObject action=c.step().parameters().deepCopy();action.addProperty("type",action.has("target_stage")?"quest_transition":"quest");JsonObject wrapper=new JsonObject();JsonArray array=new JsonArray();array.add(action);wrapper.add("actions",array);var parsed=VillagerActionDefinition.readList(c.definition().id(),"scene quest_transition",wrapper,null);if(parsed.size()!=1)return SceneStepResult.fail("quest_transition_invalid","quest transition action is invalid");
            var guarded=SceneReceiptGuard.applyOnce(c,"quest_transition",SceneOperationReceipt.Kind.QUEST_TRANSITION,()->VillagerActionRegistry.execute(dialogue,parsed.getFirst(),java.util.Map.of()),"quest transition");if(guarded.status()==SceneReceiptGuard.Status.AMBIGUOUS_PREPARED)return SceneStepResult.block("quest_transition_ambiguous","cannot prove quest transition outcome after reload");guarded.receipt().completed(c.gameTime(),"quest transition");return SceneStepResult.applied();}
        public SceneStepResult reconcile(SceneExecutionContext c){return apply(c);}
    }

    private static final class Branch extends Base {
        Branch(){super(RecoveryMode.NATURALLY_IDEMPOTENT);}
        public SceneStepResult apply(SceneExecutionContext c){String persisted=c.record().durableValues().get("chosen_transition");if(persisted!=null)return SceneStepResult.applied();DialogueContext dialogue=context(c);if(dialogue==null)return actorUnavailable(c,"branch condition context is unavailable");String chosen=string(c,"default_transition","");JsonElement raw=c.step().parameters().get("branches");if(raw!=null&&raw.isJsonArray())for(JsonElement value:raw.getAsJsonArray())if(value.isJsonObject()){JsonObject branch=value.getAsJsonObject();List<DialogueCondition> conditions=DialogueCondition.readList(c.definition().id(),"scene branch",branch);if(DialogueCondition.matchesAll(dialogue,conditions)){chosen=string(branch,"transition","");break;}}
            if(chosen.isBlank())return SceneStepResult.fail("branch_unmatched","scene branch has no matching or default transition");c.record().putDurableValue("chosen_transition",chosen);c.record().chooseTransition(chosen);return SceneStepResult.applied();}
        public SceneStepResult verify(SceneExecutionContext c){return SceneStepResult.complete(c.record().durableValues().get("chosen_transition"));}
    }

    private static final class Terminal extends Base {Terminal(){super(RecoveryMode.NATURALLY_IDEMPOTENT);}public SceneStepResult apply(SceneExecutionContext c){return SceneStepResult.applied();}}

    private static SceneOperationReceipt.Kind receiptKind(VillagerActionDefinition action){return switch(action.kind()){
        case EXPERIENCE->SceneOperationReceipt.Kind.EXPERIENCE_GRANT;case REPUTATION->SceneOperationReceipt.Kind.REPUTATION_CHANGE;case GOSSIP->SceneOperationReceipt.Kind.GOSSIP_CHANGE;case COUNTER->SceneOperationReceipt.Kind.COUNTER_INCREMENT;case QUEST,QUEST_TRANSITION->SceneOperationReceipt.Kind.QUEST_TRANSITION;case LOOT->SceneOperationReceipt.Kind.LOOT_GRANT;default->SceneOperationReceipt.Kind.ITEM_GRANT;};}

    private static DialogueContext context(SceneExecutionContext c){ServerPlayer player=null;for(UUID id:c.instance().participants()){player=c.server().getPlayerList().getPlayer(id);if(player!=null)break;}if(player==null&&c.instance().owner().playerId()!=null)player=c.server().getPlayerList().getPlayer(c.instance().owner().playerId());Villager villager=null;for(SceneActorBinding binding:c.instance().actorBindings().values()){Entity entity=find(c,binding);if(entity instanceof Villager value){villager=value;break;}}return player==null||villager==null?null:VillagerInteractionService.createDialogueContext(player.serverLevel(),player,villager);}
    private static Entity actor(SceneExecutionContext c,String alias){SceneActorBinding binding=c.instance().actorBindings().get(alias);return binding==null?null:find(c,binding);}
    private static Entity find(SceneExecutionContext c,SceneActorBinding binding){if(binding.entityId()==null)return null;if(binding.lastDimension()!=null){ServerLevel level=c.server().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,binding.lastDimension()));if(level!=null)return level.getEntity(binding.entityId());}for(ServerLevel level:c.server().getAllLevels()){Entity value=level.getEntity(binding.entityId());if(value!=null)return value;}return null;}
    private static SceneStepResult actorUnavailable(SceneExecutionContext c,String message){String alias=sourceAlias(c);SceneActorDeclaration declaration=c.definition().actors().get(alias);if(declaration==null)return SceneStepResult.block("actor_missing",message);return switch(declaration.missingActorPolicy()){case SKIP->SceneStepResult.skip();case FAIL->SceneStepResult.fail("actor_missing",message);case BLOCK->SceneStepResult.block("actor_missing",message);case WAIT_UNTIL_TIMEOUT->{long timeout=declaration.timeoutTicks();long deadline=durableLong(c,"actor_deadline",0);if(deadline==0){deadline=c.gameTime()+timeout;c.record().putDurableValue("actor_deadline",Long.toString(deadline));}yield timeout>0&&c.gameTime()>=deadline?SceneStepResult.fail("actor_timeout",message):SceneStepResult.waitUntil(c.gameTime()+20,message);}};}
    private static String sourceAlias(SceneExecutionContext c){String authored=string(c,"actor","");return authored.isBlank()?(c.step().actors().isEmpty()?"":c.step().actors().getFirst()):authored;}
    private static Destination destination(SceneExecutionContext c){String target=string(c,"target_actor","");if(!target.isBlank()){SceneActorBinding binding=c.instance().actorBindings().get(target);if(binding!=null&&binding.lastDimension()!=null&&binding.lastPosition()!=null)return new Destination(binding.lastDimension(),binding.lastPosition());Entity entity=actor(c,target);if(entity!=null)return new Destination(entity.level().dimension().location(),entity.blockPosition());}if(c.step().parameters().has("x")&&c.step().parameters().has("y")&&c.step().parameters().has("z")){ResourceLocation dimension=ResourceLocation.tryParse(string(c,"dimension","minecraft:overworld"));return dimension==null?null:new Destination(dimension,new BlockPos((int)longValue(c,"x",0),(int)longValue(c,"y",0),(int)longValue(c,"z",0)));}return null;}
    private static Destination durableDestination(SceneExecutionContext c){ResourceLocation d=ResourceLocation.tryParse(c.record().durableValues().getOrDefault("destination_dimension",""));if(d==null)return null;try{return new Destination(d,new BlockPos(Integer.parseInt(c.record().durableValues().get("destination_x")),Integer.parseInt(c.record().durableValues().get("destination_y")),Integer.parseInt(c.record().durableValues().get("destination_z"))));}catch(RuntimeException ignored){return null;}}
    private static String string(SceneExecutionContext c,String key,String fallback){return string(c.step().parameters(),key,fallback);}private static String string(JsonObject o,String key,String fallback){JsonElement e=o.get(key);return e!=null&&e.isJsonPrimitive()?e.getAsString():fallback;}
    private static long longValue(SceneExecutionContext c,String key,long fallback){try{return c.step().parameters().has(key)?c.step().parameters().get(key).getAsLong():fallback;}catch(RuntimeException ignored){return fallback;}}
    private static double doubleValue(SceneExecutionContext c,String key,double fallback){try{return c.step().parameters().has(key)?c.step().parameters().get(key).getAsDouble():fallback;}catch(RuntimeException ignored){return fallback;}}
    private static boolean bool(SceneExecutionContext c,String key,boolean fallback){try{return c.step().parameters().has(key)?c.step().parameters().get(key).getAsBoolean():fallback;}catch(RuntimeException ignored){return fallback;}}
    private static long durableLong(SceneExecutionContext c,String key,long fallback){try{return Long.parseLong(c.record().durableValues().getOrDefault(key,Long.toString(fallback)));}catch(NumberFormatException ignored){return fallback;}}
    private record Destination(ResourceLocation dimension,BlockPos pos){}
}
