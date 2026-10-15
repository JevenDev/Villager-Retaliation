"""Build authored quest/v2 bundles and server checkpoints from the exact v1 teaching-card import.
Run import-clipboard-tutorial-drafts.py first when refreshing the authoritative document.
"""
import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources/data/villagerretaliation"
SCRIPTS=ROOT/"clipboard_tutorials"
SHARED=json.loads((SCRIPTS/"shared.json").read_text(encoding="utf-8"))["sequence"]
PROFESSIONS={"farming":["farmer"],"fishing":["fisherman"],"mining":["toolsmith"],"hunting":["fletcher"],"animal_handling":["shepherd","leatherworker"],"brewing":["cleric"],"craftsman":["toolsmith"],"cook":["butcher"],"smelter":["armorer"],"combat":["weaponsmith"],"builder":["mason","toolsmith","weaponsmith"],"nitwit":["nitwit"]}
TOOLS={"farming":("item_tag","minecraft:hoes"),"fishing":("item","minecraft:fishing_rod"),"mining":("item_tag","minecraft:pickaxes"),"logging":("item_tag","minecraft:axes"),"hunting":("item_tag","minecraft:swords"),"animal_handling":("item","minecraft:shears")}
EARLY={"farming":5,"fishing":5,"mining":5,"logging":5,"hunting":6,"animal_handling":5,"brewing":5,"craftsman":5,"cook":5,"smelter":5,"courier":5,"nitwit":3,"combat":5,"builder":5}
def build(path):
 d=json.loads(path.read_text(encoding="utf-8"));role=d["role"];qid=d["id"];checkpoints={};stages=[]
 def cond(check,**extra):return {"type":"work_state","tutorial":qid,"role":role,"check":check,**extra}
 def action(op,**extra):return {"type":"tutorial","role":role,"operation":op,**extra}
 def response(id,label,**extra):return {"id":id,"label":label,"exchanges":[{"speaker":{"type":"player"},"text":label}],**extra}
 def common():
  return [response("supplies","Ask for once-only working inputs",actions=[action("supplies")]),response("resume","Restore my lesson assignment",actions=[action("start")]),response("revalidate","Validate or resume the remaining task",actions=[action("activate")]),response("recover","Recover the bound task and carried lesson output",actions=[action("recover")]),response("replace","Use this compatible worker as my replacement",actions=[action("replace")]),response("pause","Pause the lesson without a fee",actions=[action("stop")]),response("leave","Let me try it",end=True)]
 first=d["steps"][0];flavour=re.search(r'\*\*(?:NPC|Tutor):\*\*\s*[“"](.+?)[”"]',first.get("design_notes",""))
 offer=(flavour.group(1) if flavour else "Let's learn this job together.")+" This personal lesson is once per job. Its finite work is free, and it stops before any paid continuation."
 accept={"id":"accept_lesson","objectives":[{"id":"bound","type":"condition","conditions":[cond("assignment")]}],"complete_when":["bound"],"dialogue":{
 "offer":{"label":"Clipboard lesson: "+d["title"],"lines":[offer],"order":-40,"responses":[response("accept","Take the bounded free lesson",actions=[{"type":"quest","action":"start"},action("start")]),response("decline","Another time",end=True)]},
 "reminder":{"label":"Resume Clipboard lesson","lines":["Your remaining allowance and once-only supplies are retained."],"responses":common()}}}
 stages.append(accept)
 entries=[]
 for step in d["steps"][1:]:
  number=int(step["id"].split("_")[1]);seqs=step["sequences"]
  if not seqs:
   # Outcome-only steps wait for committed work; the final closing is shared below.
   if number>=EARLY[role] and number<len(d["steps"]):
    text=re.search(r'\*\*(?:NPC|Tutor):\*\*\s*[“"](.+?)[”"]',step.get("design_notes",""))
    entries.append((step["id"],step["title"],[text.group(1) if text else "Watch the bound worker's actual task. Ask me for a work report if progress is blocked."],"Open Job > Work Settings > Ask for Work Report. Correct the reported obstruction and resume the same allowance.",[cond("outcome",parameter={"fishing":"catch"}.get(role,"report"))],False))
   continue
  for seq in seqs:
   cards=[c["text"].replace("loaned ","lesson ") for c in seq["cards"]]
   text=" ".join(cards);lower=text.lower();checks=[cond("assignment")];activate=False
   if "assign clipboard storage" in lower:
    purpose="output" if "assign output storage" in lower else "supply";checks.append(cond("storage",parameter=purpose))
    if purpose=="supply" and role in TOOLS:kind,item=TOOLS[role];checks.append(cond("supplies",**{kind:item}))
   elif "edit job site" in lower and "task" not in lower:checks.append(cond("area"))
   elif "route mode" in lower:checks.append(cond("route"))
   elif "start/pause work" in lower or role=="nitwit" and "close" in lower and number==3:
    activate=True;checks.append(cond("settings",parameter="work_enabled"))
   elif "work settings" in lower or "cycle combat mode" in lower:checks.append(cond("policy"))
   elif "job inventory" in lower and role=="combat":checks.append(cond("equipment",item_tag="minecraft:swords"))
   elif any(t in lower for t in ["configure brewing","filter slot","output selection","recipe filter","item filter"]):checks.append(cond("selection"))
   elif "draft" in lower and "task" in lower:checks.append(cond("task"))
   if role=="builder":
    if number==2:checks=[cond("blueprint")]
    elif number==4:checks.append(cond("selection"))
   # Last delivery/report demonstration acknowledges real completion, never matching old items.
   if number==len(d["steps"]) or number==len(d["steps"])-1 and role in ["farming","logging","mining","hunting","animal_handling","nitwit","builder"]:
    if role=="animal_handling" and number==6:pass
    elif not activate:checks=[cond("completed")]
   if role=="animal_handling" and number==6:checks=[cond("outcome",parameter="breed")]
   entries.append((seq["id"],step["title"],cards,seq.get("recovery","Show me the exact saved result on the bound worker."),checks,activate))
 # Insert the reusable supplies/deposit lesson after chest setup and before productive work.
 if role not in ["nitwit","combat","builder"]:
  before=next((i for i,e in enumerate(entries) if e[5] or any(c.get("check") in ["outcome","completed"] for c in e[4])),len(entries)-1)
  entries.insert(max(0,before),(SHARED["id"],"Check material flow",[c["text"] for c in SHARED["cards"]],SHARED["recovery"],[cond("settings",parameter="assigned_supplies"),cond("settings",parameter="auto_deposit")],False))
 for id,title,cards,recovery,checks,activate in entries:
  checkpoints[id]=checks
  scenes=[]
  for n,text in enumerate(cards):
   nextid=f"{id}_card_{n+2}";sceneid=f"{id}_card_{n+1}"
   actions=([action("activate")] if activate else [])+[action("learn",step=id)]
   answers=[response("next","Show me the next step",scene=nextid)] if n+1<len(cards) else [response("check","Check my committed result",actions=actions)]
   answers += [response("repeat","Show me again",scene=f"{id}_card_1"),response("help","I chose the wrong target",scene=f"{id}_help"),response("leave","Let me demonstrate",end=True)]
   scenes.append({"id":sceneid,"lines":[text],"responses":answers})
  scenes.append({"id":f"{id}_help","lines":[recovery],"responses":[response("again","Show me again",scene=f"{id}_card_1"),response("leave","I'll correct it",end=True)]})
  reminder={"label":title,"lines":["Show me the next step, then demonstrate it. I will check the saved result on your bound worker."],"responses":[response("details","Show me exactly how",scene=f"{id}_card_1")]+common()}
  if role=="builder":reminder["responses"].insert(1,response("blueprint","Request my free Plains Accessory 1 blueprint",actions=[action("blueprint")]))
  stages.append({"id":id,"title":title,"objectives":[{"id":"demonstrated","type":"condition","conditions":[cond("learned",parameter=id)]}],"complete_when":["demonstrated"],"dialogue":{"reminder":reminder},"scenes":scenes})
 # A separate authorization checkpoint catches roles whose source describes Start in prose rather than a numbered card.
 if not any(e[5] for e in entries):
  authorization={"id":"authorize_remaining","objectives":[{"id":"ready","type":"condition","conditions":[cond("settings",parameter="work_enabled")]}],"complete_when":["ready"],"dialogue":{"reminder":{"label":"Authorize the finite task","lines":["Put the Clipboard away. Use an empty hand on me, choose Job > Work Settings and Ask for Work Report. If work is paused, click Start/Pause Work once. Return and let me validate the finite task."],"responses":[response("activate","Validate and authorize only the remaining task",actions=[action("activate")])]+common()}}}
  position=next((i for i,stage in enumerate(stages) if any(c.get("check")=="outcome" or c.get("check")=="completed" for c in checkpoints.get(stage["id"],[]))),len(stages))
  stages.insert(position,authorization)
 stages.append({"id":"verified_outcome","objectives":[{"id":"earned","type":"condition","conditions":[cond("completed")]}],"complete_when":["earned"],"dialogue":{"reminder":{"label":"Check the real outcome","lines":["The lesson waits for this worker's real task and attributable delivery. Work status, a menu click or an old matching item cannot complete it. Put the Clipboard away, use an empty hand on me and choose Job > Work Settings > Ask for Work Report to find a blocker."],"responses":common()}}})
 ending=[response("stop","B: Stop here; retain completion",actions=[action("stop")]),response("leave","Decide later; keep work stopped",end=True)]
 if role!="builder":ending.insert(0,response("quote","A: Show the current normal one-day fee",actions=[action("quote")]))
 if role!="builder":ending.insert(1,response("accept_fee","Accept the quoted current fee and continue",conditions=[cond("quoted")],actions=[action("continue")]))
 else:ending.insert(0,response("next_project","A: End this lesson; quote a separate next project",actions=[action("stop")],lines=["Plains Accessory 1 is complete and your tutorial is retained. Choose a new blueprint through the normal Builder service for its separately quoted purchase and construction labor. No daily employment was started."]))
 scenes=[]
 stages.append({"id":"choose_next","objectives":[{"id":"decision","type":"condition","conditions":[cond("disposed")]}],"complete_when":["decision"],"next":{"complete":True},"dialogue":{"reminder":{"label":"Lesson complete: continue or stop","lines":["Your bounded lesson is earned. Free productive work is stopped while you decide. Both choices retain completion."],"responses":ending}},"scenes":scenes})
 for i,stage in enumerate(stages[:-1]):stage["next"]=stages[i+1]["id"]
 for stage in stages:
  title=stage.get("title",{"accept_lesson":"Accept the free lesson","authorize_remaining":"Authorize the remaining task","verified_outcome":"Verify the real work","choose_next":"Choose whether to continue"}.get(stage["id"],"Demonstrate this step"))
  stage["ui"]={"title":title,"tracker_text":title,"show_progress":False}
  for objective in stage["objectives"]:objective["tracker"]={"text":title,"complete_text":"Server-confirmed demonstration","show_progress":False}
 provider={"type":"villagerretaliation:villager","blocks_hiring":False}
 if role in PROFESSIONS:provider["filters"]={"professions":["minecraft:"+p for p in PROFESSIONS[role]]}
 prefix="villagerretaliation.quest.clipboard."+role
 display={"farming":"Farmer","fishing":"Fisher","mining":"Miner","logging":"Logger","hunting":"Hunter","animal_handling":"Animal Handler","brewing":"Brewer","craftsman":"Craftsman","cook":"Cook","smelter":"Smelter","courier":"Courier","combat":"Guard","builder":"Builder","nitwit":"Nitwit"}[role]
 quest={"schema":"villagerretaliation:quest/v2","id":qid,"localization_prefix":prefix,"metadata":{"title":display+" Clipboard Lesson","description":"Learn exactly how to configure this personal Clipboard job, verify bounded real work, and choose whether to continue.","questline":"clipboard","tags":["group.clipboard","role.tutorial","tier.early","difficulty.easy","commitment.standard","activity.social","activity.choice","destination.village","theme.craftsmanship","feature.recoverable"]},"provider":provider,"availability":{"conditions":[cond("eligible")],"repeatable":False,"max_completions":1,"completion_scope":"player","abandonment":"allow_repickup","locked_to_villager":False,"cross_villager_compatible":True},"entry_stage":"accept_lesson","stages":stages,"lifecycle":{"on_abandon":{"actions":[action("stop")]},"on_fail":{"actions":[action("stop")]}}}
 def exchanges(value):
  if isinstance(value,dict):
   if "lines" in value and isinstance(value["lines"],list):value.setdefault("exchanges",[]).extend({"speaker":{"type":"role","role":"provider"},"text":line} for line in value["lines"])
   for child in list(value.values()):exchanges(child)
  elif isinstance(value,list):
   for child in value:exchanges(child)
 exchanges(quest)
 # Bundle-local references keep the quest fully editable in the native node editor.
 messages={}
 def localize(value,path=""):
  if isinstance(value,dict):
   for key,child in list(value.items()):
    keypath=(path+"."+key).strip(".")
    if key in ["label","title","description","text","lines","complete_text","tracker_text","tracker_complete_text"] and (isinstance(child,str) or key=="lines" and isinstance(child,list)):
     messages[prefix+"."+keypath]=child;value[key]={"key":"#"+keypath}
    else:localize(child,keypath)
  elif isinstance(value,list):
   for i,child in enumerate(value):localize(child,path+"."+str(i))
 localize(quest)
 folder=ROOT/"quests/clipboard"/("clipboard_"+role);folder.mkdir(parents=True,exist_ok=True);(folder/"locales").mkdir(exist_ok=True)
 (folder/"quest.json").write_text(json.dumps(quest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
 (folder/"locales/en_us.json").write_text(json.dumps(messages,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
 d["completion_offer"]="one_off_project_or_stop" if role=="builder" else "normal_contract_or_stop"
 d["checkpoints"]=checkpoints;d["release_status"]="integrated";path.write_text(json.dumps(d,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
 return len(stages)
if __name__=="__main__":
 total=0
 for path in sorted(SCRIPTS.glob("*.json")):
  if path.name!="shared.json":total+=build(path)
 print(f"Built 14 personal Clipboard quest bundles with {total} durable stages.")
