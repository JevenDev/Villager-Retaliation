package com.jvn.villagerretaliation.dialogue.normal;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.world.entity.EntityType;
import java.util.*;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.dialogue.resources.PairedDialogueResources;

@GameTestHolder("paired_ordinary")
@PrefixGameTestTemplate(false)
public final class PairedOrdinaryGameTests {
    @GameTest(template="empty",batch="paired_ordinary",timeoutTicks=400)
    public static void bothVoicesFollowReputationContactAndAge(GameTestHelper h) throws Exception {
        var seed=context(h);int checked=0;
        for(var tier:com.jvn.villagerretaliation.reputation.VillagerReputationLevel.values())for(boolean first:List.of(true,false))for(boolean child:List.of(true,false)) {
            seed.villager().setAge(child?-24000:0);
            var c=changed(seed,Map.of("reputationLevel",tier,"firstConversation",first));
            String id=child?"baby_greeting":"adult_general_question";
            var rows=PairedDialogueResources.pairs(c).stream().filter(p -> p.option().equals(id) && p.reputation().equals(tier.name().toLowerCase(Locale.ROOT))
                    && (p.familiarity().equals("either") || p.familiarity().equals(first?"first":"familiar"))).toList();
            if(rows.isEmpty())continue;
            var row=rows.getFirst();var type=DialogueRequestType.valueOf(row.request().toUpperCase(Locale.ROOT));
            var option=DialogueOptionDefinition.simple(id,"Existing choice",type,0);
            var original=new VillagerDialogueService.DialogueResult(row.owners().getFirst(),"Original");
            var paired=pair(c,option,original,DialogueReputationEffect.none(type),false);
            h.assertTrue(rows.stream().anyMatch(p -> p.player().equals(paired.exchanges().getFirst().text()) && p.npc().equals(paired.exchanges().getLast().text())),"both voices belong to the actual age/reputation/contact pool");
            checked++;
        }
        h.assertTrue(checked>=30,"broad tier/contact/adult-child coverage");h.succeed();
    }

    @GameTest(template="empty",batch="paired_ordinary")
    public static void dynamicResultsAndInspectionsNeverInventMissingState(GameTestHelper h) {
        var c=context(h);var question=DialogueReputationEffect.none(DialogueRequestType.QUESTION);
        var gift=DialogueOptionDefinition.simple("gift_advice_followup","About that gift advice...",DialogueRequestType.GIFT_ADVICE_FOLLOWUP,0);
        var missing=new VillagerDialogueService.DialogueResult("gift_advice_followup_liked","Original");
        h.assertTrue(PairedDialogueService.attach(c,gift,missing,question,false)==missing,"owner name is not proof of an actual claimed gift result");
        var wrong=missing.withOutcome("invented",Map.of("gift_item","apple","tested_villager","Ada"));
        h.assertTrue(PairedDialogueService.attach(c,gift,wrong,question,false)==wrong,"unrecognized outcome fails closed");
        c.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        var held=c.player().getMainHandItem();held.setDamageValue(held.getMaxDamage()-10);
        var option=DialogueOptionDefinition.simple("weaponsmith_inspect_weapon","Inspect weapon",DialogueRequestType.QUESTION,0);
        var original=new VillagerDialogueService.DialogueResult("weaponsmith_inspect_weapon_low","Original");
        var paired=pair(c,option,original,question,false);
        h.assertFalse(paired.text().contains("<"),"all durability tokens resolved literally");
        var candidates=PairedDialogueResources.pairs(c).stream().filter(p -> p.option().equals(option.id()) && p.branch().equals("durability_low") && p.reputation().equals(c.reputationLevel().name().toLowerCase(Locale.ROOT))).toList();
        h.assertTrue(candidates.stream().anyMatch(p -> PairedDialogueService.render(p.npc(),Map.of("held_item_durability","10","held_item_max_durability",Integer.toString(held.getMaxDamage()),"held_item",held.getHoverName().getString())).orElse("").equals(paired.text())),"inspection speaks actual remaining durability");
        c.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        for(int i=0;i<12;i++)h.assertTrue(PairedDialogueService.attach(c,option,original,question,false)==original,"no item cannot produce fabricated durability");
        h.succeed();
    }

    private static com.jvn.villagerretaliation.dialogue.DialogueContext changed(com.jvn.villagerretaliation.dialogue.DialogueContext source,Map<String,Object> changes) throws Exception {
        var fields=source.getClass().getRecordComponents();var types=new Class<?>[fields.length];var values=new Object[fields.length];
        for(int i=0;i<fields.length;i++){types[i]=fields[i].getType();values[i]=changes.containsKey(fields[i].getName())?changes.get(fields[i].getName()):fields[i].getAccessor().invoke(source);}
        return source.getClass().getDeclaredConstructor(types).newInstance(values);
    }
    @GameTest(template="empty",batch="paired_ordinary")
    public static void allStagedRowsLoadBehindExactly32ExistingOptions(GameTestHelper h) {
        var c=context(h);var pairs=PairedDialogueResources.pairs(c);
        h.assertValueEqual(pairs.size(),1782,"all staged eligibility/outcome rows");
        h.assertValueEqual(pairs.stream().map(PairedDialogueService.Pair::option).collect(java.util.stream.Collectors.toSet()),PairedDialogueService.OPTIONS,"exact option coverage");
        h.assertValueEqual(pairs.stream().map(PairedDialogueService.Pair::id).distinct().count(),1782L,"stable unique IDs");h.succeed();
    }
    @GameTest(template="empty",batch="paired_ordinary")
    public static void realOutcomeAndSafeLiteralTokensControlBothVoices(GameTestHelper h) {
        var c=context(h);var option=DialogueOptionDefinition.simple("gift_advice_followup","About that gift advice...",DialogueRequestType.GIFT_ADVICE_FOLLOWUP,0);
        var original=new VillagerDialogueService.DialogueResult("gift_advice_followup_liked","Original reply").withOutcome("liked",Map.of("gift_item","apples","tested_villager","Ada $\\ Reader"));
        var paired=pair(c,option,original,DialogueReputationEffect.none(option.requestType()),false);
        h.assertValueEqual(paired.exchanges().size(),2,"one opener and one compatible reply");
        h.assertTrue(paired.exchanges().getFirst().text().contains("apples"),"real gift item");
        h.assertTrue(paired.exchanges().getFirst().text().contains("Ada $\\ Reader"),"literal name");
        h.assertFalse(paired.exchanges().getFirst().text().contains("did not like"),"liked outcome cannot select disliked premise");
        var bad=original.withOutcome("liked",Map.of("gift_item","<shake>bad</shake>","tested_villager","Ada"));
        h.assertTrue(PairedDialogueService.attach(c,option,bad,DialogueReputationEffect.none(option.requestType()),false)==bad,"unsafe dynamic token suppresses variant");
        h.assertTrue(PairedDialogueService.attach(c,option,original,DialogueReputationEffect.none(option.requestType()),true)==original,"unrelated final override retains precedence");h.succeed();
    }
    @GameTest(template="empty",batch="paired_ordinary")
    public static void jokeRepliesMatchOneComputedConsequenceAndRotationPersists(GameTestHelper h) {
        var c=context(h);var option=DialogueOptionDefinition.simple("adult_joke","Tell a joke",DialogueRequestType.JOKE,0);
        var original=new VillagerDialogueService.DialogueResult("global_new_joke_emerald","Original");
        var effect=new DialogueReputationEffect(DialogueRequestType.JOKE,1,"joke_landed",DialogueReputationEffect.CooldownCategory.JOKE,false,true,false,"Laugh");
        var first=pair(c,option,original,effect,true);var second=pair(c,option,original,effect,true);
        h.assertFalse(first.exchanges().equals(second.exchanges()),"paired joke alternatives rotate");
        h.assertValueEqual(first.exchanges().getFirst().speaker(),c.player().getUUID(),"player speaks first");
        h.assertValueEqual(first.exchanges().getLast().speaker(),c.villager().getUUID(),"NPC answers second");
        var state=DialogueUsageSavedData.get(c.level());var saved=state.save(new net.minecraft.nbt.CompoundTag(),c.level().registryAccess());
        h.assertTrue(saved.equals(DialogueUsageSavedData.load(saved,c.level().registryAccess()).save(new net.minecraft.nbt.CompoundTag(),c.level().registryAccess())),"paired rotation ledger round trip");h.succeed();
    }
    static com.jvn.villagerretaliation.dialogue.DialogueContext context(GameTestHelper h) {
        var v=h.spawn(EntityType.VILLAGER,1,2,1);v.setNoAi(true);
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PairTest"));
        return VillagerInteractionService.createDialogueContext(h.getLevel(),p,v);
    }
    static VillagerDialogueService.DialogueResult pair(com.jvn.villagerretaliation.dialogue.DialogueContext c,DialogueOptionDefinition option,VillagerDialogueService.DialogueResult original,DialogueReputationEffect effect,boolean replaced) {
        for(int i=0;i<50;i++) {var result=PairedDialogueService.attach(c,option,original,effect,replaced);if(result!=original)return result;}
        throw new AssertionError("No compatible pair for "+option.id());
    }
}
