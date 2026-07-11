import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dataRoot = path.join(root, "neoforge", "src", "main", "resources", "data", "villagerretaliation");
const questRoot = path.join(dataRoot, "quests");
const lootRoot = path.join(dataRoot, "loot_table", "quest");

const existingRepeatables = {
  "village_defense/watch_arrows.json": {
    icon: "minecraft:arrow",
    profile: voice({
      offer: "The watch has bows and too many empty quivers. Sixteen arrows would put some distance between trouble and our doors.",
      reason: "A pillager is easier to answer from the wall than from the wheat field.",
      ask: "Bring 16 arrows for the village watch",
      accepted: "Good. Straight shafts, sharp points, and no bent fletching.",
      reminder: "The watch still needs sixteen arrows before the next bad omen wanders close.",
      success: "These will reach trouble before trouble reaches the bell.",
      successAlt: "Full quivers make for a quieter night. Well done.",
      decline: "Then the watch will make every shot count."
    })
  },
  "dangerous_commissions/trial_chamber_recall.json": {
    icon: "minecraft:trial_key",
    target: target("minecraft:trial_chambers", "minecraft:overworld", 320, 160),
    objectives: [
      structure("enter_chamber", "minecraft:trial_chambers", 320, 160,
        "Enter the marked Trial Chamber.", "The chamber has been surveyed."),
      item("bring_trial_key", "minecraft:trial_key", 1,
        "Bring back 1 Trial Key.", "The Trial Key is ready."),
      item("bring_breeze_rod", "minecraft:breeze_rod", 1,
        "Bring back 1 Breeze Rod.", "The Breeze Rod is ready.")
    ],
    trackerText: "Survey a Trial Chamber and return with a Trial Key and Breeze Rod.",
    rewards: {
      experience: 320,
      reputation: 18,
      gossip_reputation: 8,
      loot_table: "villagerretaliation:quest/trial_chamber_recall",
      memory_event: "player_completed_quest"
    },
    profile: voice({
      offer: "A Trial Chamber has started humming under the stone again. I need someone to learn what woke up down there.",
      reason: "Bring back a Trial Key and a Breeze Rod so we know the vaults and wind traps are real.",
      ask: "Enter the marked Trial Chamber, then return with a Trial Key and Breeze Rod",
      accepted: "The mark is set. Listen for copper, tuff, and anything that sounds like wind where wind should not be.",
      reminder: "The chamber itself matters as much as the proof. Step inside before you turn back.",
      success: "Key, rod, and a true chamber mark. That is a report I can trust.",
      successAlt: "You came back with the chamber's own wind in your pack. Take what we promised.",
      decline: "Wise enough. Trial spawners do not care how brave a story sounds."
    })
  },
  "village_supply/beetroot_bundle.json": supplyVoice(
    "minecraft:beetroot", "Bring 16 beetroot for the village kitchen",
    "The stew pots are red only at the bottom, which is a poor sort of stew.",
    "Good roots. The cook can fill bowls instead of making excuses."),
  "village_supply/berry_picking.json": supplyVoice(
    "minecraft:sweet_berries", "Bring 16 sweet berries for the village stores",
    "The berry jars are empty, and winter never asks before arriving.",
    "These will keep longer than they took to pick. Mind the thorns next time."),
  "village_supply/bottle_stock.json": supplyVoice(
    "minecraft:glass_bottle", "Bring 12 glass bottles for the brewing stand",
    "A cleric with an empty bottle shelf is only waving at the brewing stand.",
    "Clear glass and no cracks. The brewing stand can work again."),
  "village_supply/bread_delivery.json": supplyVoice(
    "minecraft:bread", "Bring 16 bread for the village larder",
    "The larder shelves are showing more wood than food.",
    "A full bread shelf makes the whole village stand a little straighter."),
  "village_supply/clay_repairs.json": supplyVoice(
    "minecraft:clay_ball", "Bring 16 clay balls for cracked walls and pots",
    "Rain found every crack the mason missed.",
    "That clay will close the cracks before the next storm opens them wider."),
  "village_supply/egg_baskets.json": supplyVoice(
    "minecraft:egg", "Bring 12 eggs for the village kitchen",
    "The hens are busy arguing and the kitchen has nothing to show for it.",
    "Not one cracked on the road. The cook will call that a small miracle."),
  "village_supply/feather_fletching.json": supplyVoice(
    "minecraft:feather", "Bring 16 feathers for the fletching table",
    "Arrow shafts are waiting for feathers, and waiting does not make them fly.",
    "These are straight enough for good arrows. The crooked ones can dust shelves."),
  "village_supply/fresh_cod.json": supplyVoice(
    "minecraft:cod", "Bring 10 cod for the village kitchen",
    "The smoker is warm, but the fish rack is bare.",
    "Fresh enough to make the cats follow you. That will feed us well."),
  "village_supply/ink_supply.json": supplyVoice(
    "minecraft:ink_sac", "Bring 6 ink sacs for maps and ledgers",
    "The cartographer is scratching pale lines and pretending they count as roads.",
    "Dark ink at last. Now the maps can stop whispering."),
  "village_supply/kiln_fuel.json": supplyVoice(
    "minecraft:coal", "Bring 12 coal for the mason's kiln",
    "Cold stone does not become brick because a mason glares at it.",
    "That coal will keep the kiln hot through a proper firing."),
  "village_supply/leather_repairs.json": supplyVoice(
    "minecraft:leather", "Bring 8 leather for aprons, straps, and book covers",
    "Too many straps have split, and string is a poor lie for leather.",
    "Supple, thick, and worth stitching. These repairs will hold."),
  "village_supply/map_paper.json": supplyVoice(
    "minecraft:paper", "Bring 24 paper for the cartography table",
    "There are more roads to draw than sheets left in the drawer.",
    "Clean paper and square edges. The next map has somewhere to begin."),
  "village_supply/seed_stockpile.json": supplyVoice(
    "minecraft:wheat_seeds", "Bring 32 wheat seeds for the next planting",
    "A village that eats every seed has chosen a very short future.",
    "Enough for a new field. The next harvest starts in this sack."),
  "village_supply/torch_bundle.json": supplyVoice(
    "minecraft:torch", "Bring 16 torches for paths, cellars, and doorways",
    "The dark has been borrowing too many corners after sunset.",
    "Sixteen little answers to sixteen dark corners. Good."),
  "village_supply/village_lanterns.json": supplyVoice(
    "minecraft:lantern", "Bring 6 lanterns for the village boundary",
    "The paths fade before the houses do, and that is where trouble likes to walk.",
    "These will hold the edge of the village bright through the night."),
  "village_supply/wool_blankets.json": supplyVoice(
    "minecraft:white_wool", "Bring 12 white wool for warm blankets",
    "Beds are meant to stop the cold, not merely give it somewhere to sit.",
    "Soft, clean wool. No villager should shiver under a roof now.")
};

const existingLegacyQuests = {
  "dangerous_commissions/gilded_debt.json": {
    icon: "minecraft:gilded_blackstone",
    target: target("minecraft:bastion_remnant", "minecraft:the_nether", 512, 160),
    objectives: [
      structure("visit_bastion", "minecraft:bastion_remnant", 512, 160,
        "Reach the marked Bastion Remnant.", "The bastion debt route is confirmed.", "minecraft:the_nether"),
      item("bring_gilded_stone", "minecraft:gilded_blackstone", 1,
        "Bring 1 gilded blackstone as proof.", "The gilded blackstone is ready."),
      item("bring_gold", "minecraft:gold_block", 1,
        "Bring 1 gold block to settle the debt.", "The settlement gold is ready.")
    ],
    trackerText: "Reach the Bastion Remnant and return with gilded blackstone and a gold block.",
    rewards: reward(450, 22, 9, "gilded_debt"),
    profile: voice({
      offer: "The mansion informants kept their word. Now the gold promised for their Nether route is due.",
      reason: "Carry one gold block to the bastion road and bring gilded blackstone back as proof the debt reached its mark.",
      ask: "Reach the marked Bastion Remnant and bring 1 gilded blackstone and 1 gold block",
      accepted: "Wear gold where piglins can see it, and keep the payment where brutes cannot.",
      reminder: "The bastion mark, one gilded blackstone, and one gold block for the debt.",
      success: "The debt is settled, the route is proven, and no promise is chasing the village now.",
      successAlt: "Gold paid on time buys more than goods. It buys a road that stays open.",
      decline: "Then the debt waits, but Nether debts grow dangerous when ignored."
    })
  },
  "dangerous_commissions/house_of_ill_omens.json": {
    icon: "minecraft:totem_of_undying",
    events: [
      {
        id: "record_mansion_cleansed",
        event: "completed",
        repeatable: false,
        actions: [
          { type: "set_tag", scope: "world", tag: "villagerretaliation:mansion_cleansed" }
        ]
      }
    ],
    target: target("minecraft:woodland_mansion", "minecraft:overworld", 768, 192),
    objectives: [
      structure("visit_mansion", "minecraft:woodland_mansion", 768, 192,
        "Reach the marked Woodland Mansion.", "The mansion has been found."),
      kill("defeat_evoker", ["minecraft:evoker"], 1,
        "Defeat 1 evoker inside the mansion.", "The mansion's evoker is down."),
      item("carry_totem", "minecraft:totem_of_undying", 1,
        "Carry 1 Totem of Undying as proof.", "The totem proof is ready.", false),
      item("bring_emeralds", "minecraft:emerald", 12,
        "Bring 12 emeralds for the village informants.", "The informant payment is ready.")
    ],
    trackerText: "Reach the Woodland Mansion, defeat an evoker, and return with a totem and 12 emeralds.",
    profile: voice({
      offer: "A Woodland Mansion is sending patrols toward roads that used to be quiet.",
      reason: "Find the house, bring down its evoker, and return with a totem so no one can dismiss the warning.",
      ask: "Reach the marked Woodland Mansion, defeat 1 evoker, and return with a totem and 12 emeralds",
      accepted: "Do not mistake carpet for safety. Every quiet room in that house is waiting for a reason.",
      reminder: "The mansion mark, one evoker, a totem in hand, and twelve emeralds for the informants.",
      success: "The house still stands, but its omen is broken and its patrol road is known.",
      successAlt: "A totem on the table is proof enough. The village will remember who carried it home.",
      decline: "Then keep clear of dark-oak roads until the patrols thin."
    })
  },
  "dangerous_commissions/nether_wart_warranty.json": {
    icon: "minecraft:nether_wart",
    target: target("minecraft:fortress", "minecraft:the_nether", 384, 128),
    objectives: [
      structure("visit_fortress", "minecraft:fortress", 384, 128,
        "Reach the marked Nether Fortress.", "The fortress route is confirmed.", "minecraft:the_nether"),
      item("bring_wart", "minecraft:nether_wart", 8,
        "Bring 8 Nether Wart for the brewing stand.", "The Nether Wart is sealed."),
      item("bring_rod", "minecraft:blaze_rod", 1,
        "Bring 1 blaze rod as route proof.", "The blaze rod is ready.")
    ],
    trackerText: "Reach the Nether Fortress and return with 8 Nether Wart and 1 blaze rod.",
    profile: voice({
      offer: "The brewing stand has bottles, powder, and no Nether Wart. That makes it a very tidy shelf.",
      reason: "Find the fortress garden and bring back enough wart to start a village crop, plus one blaze rod to prove the route.",
      ask: "Reach the marked Nether Fortress and bring 8 Nether Wart and 1 blaze rod",
      accepted: "Take more than one way home. Fortress bridges have a habit of ending under your feet.",
      reminder: "The fortress mark, eight Nether Wart, and one blaze rod.",
      success: "The wart is alive, the rod is real, and the brewing stand finally has work.",
      successAlt: "One dangerous trip can seed every potion we brew after it.",
      decline: "The brewing stand can wait. Fire is a poor reason to hurry."
    })
  },
  "lost_civilization/end_city_survey.json": {
    icon: "minecraft:chorus_flower",
    parent: "villagerretaliation:tales_of_a_lost_civilization",
    target: target("minecraft:end_city", "minecraft:the_end", 768, 192),
    objectives: [
      structure("visit_city", "minecraft:end_city", 768, 192,
        "Reach the marked End City.", "The End City has been surveyed.", "minecraft:the_end"),
      item("carry_shell", "minecraft:shulker_shell", 1,
        "Carry 1 shulker shell as proof.", "The shulker shell is ready.", false),
      item("bring_flower", "minecraft:chorus_flower", 1,
        "Bring 1 Chorus Flower as a living sample.", "The Chorus Flower is intact.")
    ],
    trackerText: "Survey the End City and return with a shulker shell and Chorus Flower.",
    profile: voice({
      offer: "The Ancient City records point beyond the dragon island, toward towers drawn without a ground line.",
      reason: "Find an End City and bring back both a shulker shell and something still growing there.",
      ask: "Reach the marked End City and return with a shulker shell and 1 Chorus Flower",
      accepted: "Take pearls, slow falling, and one clear memory of where the gateway was.",
      reminder: "The End City bearing, a shulker shell in hand, and one living Chorus Flower.",
      success: "A shell from the towers and a flower from the stone. The old record was telling the truth.",
      successAlt: "The lost civilization has a second city now, and this one still touches the sky.",
      decline: "The End is not going anywhere. Its islands barely know how."
    })
  },
  "old_roads/sunken_ledger.json": {
    icon: "minecraft:compass",
    target: target("minecraft:shipwreck", "minecraft:overworld", 384, 128),
    objectives: [
      structure("visit_wreck", "minecraft:shipwreck", 384, 128,
        "Reach the marked shipwreck.", "The shipwreck has been surveyed."),
      item("carry_compass", "minecraft:compass", 1,
        "Carry 1 compass to fix the wreck's bearing.", "The compass bearing is ready.", false),
      item("bring_paper", "minecraft:paper", 8,
        "Bring 8 paper to copy the ledger.", "The dry paper is ready.")
    ],
    trackerText: "Survey the shipwreck and return with a compass and 8 paper.",
    profile: voice({
      offer: "A fisher pulled a waterlogged ledger from a wreck, but half its route has washed away.",
      reason: "Find the wreck's true bearing and bring dry paper so the surviving marks can be copied.",
      ask: "Reach the marked shipwreck and return with a compass and 8 paper",
      accepted: "Keep the paper above the waterline. We already have one drowned ledger.",
      reminder: "The shipwreck bearing, one compass, and eight dry sheets for the copy.",
      success: "The ink wandered, but the route did not. This ledger can guide boats again.",
      successAlt: "A road under water is still a road once someone remembers it.",
      decline: "Then the tide keeps the ledger's missing half."
    })
  },
  "old_roads/the_broken_milestone.json": {
    icon: "minecraft:brush",
    target: target("minecraft:trail_ruins", "minecraft:overworld", 320, 96),
    objectives: [
      structure("visit_ruins", "minecraft:trail_ruins", 320, 96,
        "Reach the marked Trail Ruins.", "The old road marker has been found."),
      item("carry_brush", "minecraft:brush", 1,
        "Carry 1 brush for the buried marker.", "The brush is ready.", false),
      item("bring_stone", "minecraft:smooth_stone", 12,
        "Bring 12 smooth stone for the repair.", "The replacement stone is ready.")
    ],
    trackerText: "Find the Trail Ruins and return with a brush and 12 smooth stone.",
    profile: voice({
      offer: "An old milestone is buried near the trail ruins, broken where the road forgot its own name.",
      reason: "Brush the marker clear and bring smooth stone so we can raise it again.",
      ask: "Reach the marked Trail Ruins and return with a brush and 12 smooth stone",
      accepted: "Brush gently. Old roads leave smaller signs than new travelers expect.",
      reminder: "The trail-ruin mark, one brush, and twelve smooth stone for the milestone.",
      success: "The old number is visible again. Someone once measured this road, and now we can too.",
      successAlt: "A broken marker stands again. The road has one memory back.",
      decline: "The milestone has spent years underground. One more day will not offend it."
    })
  },
  "village_defense/fletchers_countermark.json": {
    icon: "minecraft:crossbow",
    target: target("minecraft:pillager_outpost", "minecraft:overworld", 384, 96),
    objectives: [
      structure("visit_outpost", "minecraft:pillager_outpost", 384, 96,
        "Reach the marked Pillager Outpost.", "The outpost has been scouted."),
      item("carry_crossbow", "minecraft:crossbow", 1,
        "Carry 1 pillager crossbow as proof.", "The crossbow proof is ready.", false),
      item("bring_arrows", "minecraft:arrow", 24,
        "Bring 24 arrows to restock the watch.", "The arrow bundle is ready.")
    ],
    trackerText: "Scout the Pillager Outpost and return with a crossbow and 24 arrows.",
    profile: voice({
      offer: "Pillager arrows carry a cut on the shaft that tells one patrol from another.",
      reason: "Scout the outpost, bring back a marked crossbow, and restock the watch before its owners follow.",
      ask: "Reach the marked Pillager Outpost and return with a crossbow and 24 arrows",
      accepted: "Look before you shoot. A scout who clears the tower may still miss the patrol behind it.",
      reminder: "The outpost mark, one pillager crossbow in hand, and twenty-four arrows for the watch.",
      success: "The countermark matches the patrol, and the watch has arrows ready for it.",
      successAlt: "Now we know which banner is moving toward us and how to answer it.",
      decline: "Then do not wear a bright shield near the outpost road."
    })
  },
  "village_defense/standing_watch.json": {
    icon: "minecraft:shield",
    events: [
      {
        id: "record_village_defender",
        event: "completed",
        repeatable: false,
        actions: [
          { type: "set_tag", scope: "player", tag: "villagerretaliation:village_defender" }
        ]
      }
    ],
    profile: voice({
      offer: "Arrows help, but a village also needs someone who stands when the bell begins.",
      reason: "Defend this village from a real threat. The villagers will know whether you did.",
      ask: "Defend the village from a real threat",
      accepted: "Stay near the bell when danger comes. A scattered defense is only several lonely fights.",
      reminder: "Stand with the village when a real threat reaches its homes.",
      success: "The village saw where you stood. No token could prove it better.",
      successAlt: "You held the line while doors were closing behind you. That earns trust.",
      decline: "Then keep your shield close. Trouble rarely sends an appointment."
    })
  }
};

const expansionRepeatables = [
  {
    id: "apiary_smoke",
    title: "Smoke the Hives",
    description: "Tend village hives and bring honeycomb for fresh frames.",
    tier: "early",
    provider: provider(["minecraft:farmer"], "novice", { animal_handling: 8, farming: 6 }),
    objectives: [
      blockEvent("tend_hives", "block_interact", ["minecraft:beehive", "minecraft:bee_nest"], 2,
        "Safely tend 2 hives or bee nests.", "The hives have been tended."),
      item("bring_honeycomb", "minecraft:honeycomb", 6,
        "Bring 6 honeycomb for new frames.", "The honeycomb is ready.")
    ],
    cooldown: 2,
    rewards: reward(90, 7, 3, "apiary_smoke"),
    loot: loot(6, 10, [bonus("minecraft:honey_bottle", 1, 2, 2), bonus("minecraft:campfire", 1, 1, 1)]),
    icon: "minecraft:honeycomb",
    profile: voice({
      offer: "The hives are crowded and the bees are in no mood for clumsy hands.",
      reason: "Smoke the frames, tend two hives, and bring six honeycomb for the next set of boxes.",
      ask: "Tend 2 hives and bring 6 honeycomb",
      accepted: "Move slowly. Bees remember a swinging arm better than an apology.",
      reminder: "Two hives and six honeycomb. If the buzzing gets louder, step back.",
      success: "The frames are clean and the bees kept their tempers. Fine work.",
      successAlt: "This comb will start another hive before the flowers fade.",
      decline: "Fair. Bees can smell doubt, or so the farmers insist."
    })
  },
  {
    id: "compost_turn",
    title: "Composting",
    description: "Work the village composters and return bone meal to the fields.",
    tier: "early",
    provider: provider(["minecraft:farmer"], "novice", { farming: 10 }),
    objectives: [
      blockEvent("turn_compost", "block_interact", ["minecraft:composter"], 4,
        "Work a composter 4 times.", "The compost has been turned."),
      item("bring_bone_meal", "minecraft:bone_meal", 8,
        "Bring 8 bone meal for the fields.", "The bone meal is ready.")
    ],
    cooldown: 1,
    rewards: reward(80, 6, 3, "compost_turn"),
    loot: loot(5, 9, [bonus("minecraft:bread", 3, 6, 2), bonus("minecraft:golden_carrot", 1, 3, 1)]),
    icon: "minecraft:composter",
    profile: voice({
      offer: "The composters are full of layers and short on turning.",
      reason: "Work the heap four times and save eight bone meal for the fields.",
      ask: "Work a composter 4 times and bring 8 bone meal",
      accepted: "Good. The smell means it is working, not that it likes you.",
      reminder: "Turn the compost, then bring eight bone meal to the field shed.",
      success: "Dark soil and a full bone-meal sack. The crops will notice.",
      successAlt: "Nothing wasted, and another row ready to grow.",
      decline: "Then the heap will keep settling without us."
    })
  },
  {
    id: "bell_rope",
    title: "Frayed Ropes",
    description: "Supply cord and leads for the village bell and animal pens.",
    tier: "early",
    provider: provider(["minecraft:shepherd", "minecraft:leatherworker"], "apprentice", { crafting: 12, animal_handling: 8 }),
    objectives: [
      item("bring_string", "minecraft:string", 16,
        "Bring 16 string for braided cord.", "The string is ready."),
      item("bring_leads", "minecraft:lead", 2,
        "Bring 2 leads for the pens.", "The leads are ready.")
    ],
    cooldown: 2,
    rewards: reward(95, 7, 3, "bell_rope"),
    loot: loot(7, 11, [bonus("minecraft:white_wool", 4, 8, 2), bonus("minecraft:saddle", 1, 1, 1)]),
    icon: "minecraft:lead",
    profile: voice({
      offer: "The bell rope is fraying, and two pen leads vanished in the same suspicious week.",
      reason: "Bring string for a new braid and two leads before anything else wanders off.",
      ask: "Bring 16 string and 2 leads",
      accepted: "A bell only warns us if someone can pull it. Make the cord stout.",
      reminder: "Sixteen string for the bell braid, and two leads for the pens.",
      success: "The braid will hold, and so will the animals. That is two fewer worries.",
      successAlt: "Strong cord, sound knots. The bell can speak again.",
      decline: "Then we will ring carefully and tie better knots."
    })
  },
  {
    id: "pond_restock",
    title: "The Village Pond",
    description: "Bring a live salmon and seagrass to renew the village pond.",
    tier: "early",
    provider: provider(["minecraft:fisherman"], "apprentice", { fishing: 14, animal_handling: 8 }),
    objectives: [
      item("bring_salmon", "minecraft:salmon_bucket", 1,
        "Bring 1 bucket of salmon.", "The salmon is ready for release."),
      item("bring_seagrass", "minecraft:seagrass", 8,
        "Bring 8 seagrass for pond cover.", "The seagrass is ready.")
    ],
    cooldown: 3,
    rewards: reward(120, 9, 4, "pond_restock"),
    loot: loot(9, 14, [bonus("minecraft:bucket", 1, 1, 2), bonus("minecraft:nautilus_shell", 1, 1, 1)]),
    icon: "minecraft:salmon_bucket",
    profile: voice({
      offer: "The village pond is all ripples and no fish.",
      reason: "Bring one live salmon and enough seagrass to give it somewhere to hide.",
      ask: "Bring 1 salmon bucket and 8 seagrass",
      accepted: "Keep the bucket upright. Fish dislike adventurous roads.",
      reminder: "A live salmon in a bucket, plus eight seagrass for the pond bed.",
      success: "There it goes. Give the pond a few days and it may look alive again.",
      successAlt: "Fresh water, green cover, and one very confused salmon. Perfect.",
      decline: "The pond has waited this long. It can wait a little longer."
    })
  },
  {
    id: "market_day",
    title: "Market Day",
    description: "Complete trades around the village to get the market moving.",
    tier: "early",
    provider: provider(["minecraft:farmer", "minecraft:fisherman", "minecraft:fletcher"], "apprentice", { trading: 12, diplomacy: 8 }),
    objectives: [
      counter("make_trades", "trade", 4,
        "Complete 4 villager trades.", "Four market trades are complete.")
    ],
    cooldown: 3,
    rewards: reward(105, 8, 4, "market_day"),
    loot: loot(6, 10, [bonus("minecraft:bread", 4, 8, 2), bonus("minecraft:experience_bottle", 3, 5, 1)]),
    icon: "minecraft:emerald",
    profile: voice({
      offer: "The market has too much staring and not enough trading.",
      reason: "Make four fair trades around the village and put some sound back in the square.",
      ask: "Complete 4 villager trades",
      accepted: "Good. Fair prices travel farther than loud promises.",
      reminder: "Four trades. Different stalls or the same one, so long as both sides agree.",
      success: "That is a market again, not a row of villagers guarding barrels.",
      successAlt: "Emeralds moved, goods changed hands, and no one shouted. A fine day.",
      decline: "Then the merchants can keep admiring one another's stock."
    })
  },
  {
    id: "road_mending",
    title: "Potholes",
    description: "Repair worn village roads and bring flint for drainage beds.",
    tier: "mid",
    provider: provider(["minecraft:mason"], "journeyman", { masonry: 22, crafting: 16 }),
    objectives: [
      blockEvent("lay_gravel", "block_place", ["minecraft:gravel"], 24,
        "Place 24 gravel for road repairs.", "The gravel repairs are laid."),
      item("bring_flint", "minecraft:flint", 8,
        "Bring 8 flint for drainage beds.", "The flint is ready.")
    ],
    cooldown: 3,
    rewards: reward(170, 11, 5, "road_mending"),
    loot: loot(12, 18, [bonus("minecraft:iron_shovel", 1, 1, 1), bonus("minecraft:lantern", 2, 4, 2)]),
    icon: "minecraft:gravel",
    profile: voice({
      offer: "The road has more holes than path, especially where the carts turn.",
      reason: "Lay fresh gravel and bring flint so rainwater has somewhere else to go.",
      ask: "Place 24 gravel and bring 8 flint",
      accepted: "Pack it firm. A loose road only moves the hole somewhere new.",
      reminder: "Twenty-four gravel laid, eight flint returned for the wet edges.",
      success: "That road will carry boots and carts without swallowing either.",
      successAlt: "Firm underfoot and sloped for rain. The mason approves.",
      decline: "Mind the east bend, then. It has already eaten one cart wheel."
    })
  },
  {
    id: "spider_silk",
    title: "Webbed Path",
    description: "Clear nearby spiders and bring strong string for bowstrings.",
    tier: "mid",
    provider: provider(["minecraft:fletcher", "minecraft:leatherworker"], "journeyman", { archery: 20, survival: 18 }),
    objectives: [
      kill("clear_spiders", ["minecraft:spider", "minecraft:cave_spider"], 6,
        "Defeat 6 spiders or cave spiders.", "The spider count is complete."),
      item("bring_string", "minecraft:string", 16,
        "Bring 16 string for bowstrings.", "The string is ready.")
    ],
    cooldown: 3,
    rewards: reward(185, 12, 5, "spider_silk"),
    loot: loot(13, 19, [bonus("minecraft:arrow", 16, 32, 2), bonus("minecraft:bow", 1, 1, 1)]),
    icon: "minecraft:string",
    profile: voice({
      offer: "Spiders have webbed the lower path, which is rude even before the biting.",
      reason: "Clear six of them and bring back enough string for proper bowstrings.",
      ask: "Defeat 6 spiders and bring 16 string",
      accepted: "Keep a torch close. Cave spiders are small enough to mistake for courage.",
      reminder: "Six spiders, sixteen string. Poison does not count as a shortcut.",
      success: "The path is clear and the string has a good pull to it.",
      successAlt: "Fine silk, fewer spiders. Both halves of that bargain suit me.",
      decline: "Then use the upper path and do not brush against anything."
    })
  },
  {
    id: "powder_run",
    title: "Creepers",
    description: "Cull creepers beyond the farms and recover their gunpowder.",
    tier: "mid",
    provider: provider(["minecraft:armorer", "minecraft:cleric"], "expert", { guarding: 26, survival: 24 }),
    objectives: [
      kill("clear_creepers", ["minecraft:creeper"], 4,
        "Defeat 4 creepers.", "Four creepers have been cleared."),
      item("bring_gunpowder", "minecraft:gunpowder", 12,
        "Bring 12 gunpowder.", "The gunpowder is sealed.")
    ],
    cooldown: 4,
    rewards: reward(220, 14, 6, "powder_run"),
    loot: loot(15, 22, [bonus("minecraft:firework_rocket", 12, 24, 2), bonus("minecraft:diamond", 1, 1, 1)]),
    icon: "minecraft:gunpowder",
    profile: voice({
      offer: "Creepers are gathering past the farms, green as the crops and much worse for fences.",
      reason: "Thin them out and seal away the powder before someone drops a torch near it.",
      ask: "Defeat 4 creepers and bring 12 gunpowder",
      accepted: "Do not let them choose the distance. Shields are cheaper than walls.",
      reminder: "Four creepers and twelve gunpowder, carried very carefully.",
      success: "No new craters, and the powder is dry. Better than I hoped.",
      successAlt: "The farms can sleep without hissing in the dark tonight.",
      decline: "Then keep away from the tall grass after sunset."
    })
  },
  {
    id: "ocean_glass",
    title: "Light Below",
    description: "Survey an Ocean Monument and recover luminous prismarine.",
    tier: "mid",
    provider: provider(["minecraft:cartographer", "minecraft:cleric"], "expert", { cartography: 30, survival: 26 }),
    target: target("minecraft:monument", "minecraft:overworld", 512, 160),
    objectives: [
      structure("visit_monument", "minecraft:monument", 512, 160,
        "Reach the marked Ocean Monument.", "The monument has been surveyed."),
      item("bring_crystals", "minecraft:prismarine_crystals", 12,
        "Bring 12 prismarine crystals.", "The prismarine crystals are ready."),
      item("bring_lantern", "minecraft:sea_lantern", 1,
        "Bring 1 sea lantern.", "The sea lantern is ready.")
    ],
    cooldown: 5,
    rewards: reward(280, 17, 7, "ocean_glass"),
    loot: loot(19, 27, [bonus("minecraft:sponge", 1, 2, 1), bonus("minecraft:heart_of_the_sea", 1, 1, 1), bonus("minecraft:experience_bottle", 8, 14, 2)]),
    icon: "minecraft:sea_lantern",
    profile: voice({
      offer: "There is a Monument offshore that shines where no village road can reach.",
      reason: "Mark its position and bring back the bright glass the guardians keep below.",
      ask: "Survey the marked Ocean Monument, then bring 12 prismarine crystals and 1 sea lantern",
      accepted: "Take doors, potions, or a very good plan for breathing.",
      reminder: "The Monument mark, twelve crystals, and one sea lantern. Watch the guardians' eyes.",
      success: "Cold light from deep water. The cartography table has never looked brighter.",
      successAlt: "The sea kept its Monument, but we have its bearing and a piece of its light.",
      decline: "Sensible. Deep water makes poor ground for second thoughts."
    })
  },
  {
    id: "copper_weather",
    title: "Copper Roofs",
    description: "Mine copper for the workshop and return finished blocks for roofs.",
    tier: "mid",
    provider: provider(["minecraft:toolsmith", "minecraft:mason"], "journeyman", { mining: 24, smithing: 20 }),
    objectives: [
      blockEvent("mine_copper", "block_break", ["minecraft:copper_ore", "minecraft:deepslate_copper_ore"], 12,
        "Mine 12 copper ore blocks.", "The copper seam has been worked."),
      item("bring_copper", "minecraft:copper_block", 2,
        "Bring 2 copper blocks.", "The copper blocks are ready.")
    ],
    cooldown: 4,
    rewards: reward(205, 13, 6, "copper_weather"),
    loot: loot(14, 21, [bonus("minecraft:honeycomb", 4, 8, 2), bonus("minecraft:iron_ingot", 4, 7, 1)]),
    icon: "minecraft:copper_block",
    profile: voice({
      offer: "The new roofs need copper that can age with the village instead of rusting away from it.",
      reason: "Work a proper seam, then press part of the haul into solid blocks.",
      ask: "Mine 12 copper ore blocks and bring 2 copper blocks",
      accepted: "Follow the green flecks. Stone rarely advertises anything else so brightly.",
      reminder: "Twelve copper ore broken, two full copper blocks returned.",
      success: "Good weight, clean corners. Rain will give these blocks their color.",
      successAlt: "This copper will still be changing when today's repairs are old stories.",
      decline: "Then the roofs can keep their plain stone a little longer."
    })
  },
  {
    id: "wither_ash",
    title: "Black Bones",
    description: "Hunt Wither Skeletons and recover the blackened remains safely.",
    tier: "late",
    provider: provider(["minecraft:cleric", "minecraft:armorer"], "master", { survival: 44, guarding: 40 }),
    objectives: [
      kill("hunt_skeletons", ["minecraft:wither_skeleton"], 6,
        "Defeat 6 Wither Skeletons.", "Six Wither Skeletons have fallen."),
      item("bring_coal", "minecraft:coal", 24,
        "Bring 24 coal from the fortress hunt.", "The blackened coal is ready."),
      item("bring_bones", "minecraft:bone", 12,
        "Bring 12 bones for study.", "The bones are sealed.")
    ],
    cooldown: 6,
    rewards: reward(360, 20, 8, "wither_ash"),
    loot: loot(24, 34, [bonus("minecraft:diamond", 1, 2, 1), bonus("minecraft:obsidian", 8, 16, 2), bonus("minecraft:experience_bottle", 12, 20, 2)]),
    icon: "minecraft:wither_skeleton_skull",
    profile: voice({
      offer: "Fortress bones are walking again, black with a fire that never warmed anything.",
      reason: "Break six Wither Skeletons and bring back what the ash did not hide.",
      ask: "Defeat 6 Wither Skeletons, then bring 24 coal and 12 bones",
      accepted: "Milk clears the wither from blood. It does nothing for poor judgment, so bring both milk and judgment.",
      reminder: "Six Wither Skeletons, twenty-four coal, twelve bones. Keep the remains wrapped.",
      success: "The ash is cold and the bones are still. We can study them safely now.",
      successAlt: "You brought the fortress shadow home without letting it follow you.",
      decline: "A Nether Fortress is no place to be shamed into visiting."
    })
  },
  {
    id: "echo_trade",
    title: "Echoes",
    description: "Return to an Ancient City and recover echo shards for the archive.",
    tier: "late",
    provider: provider(["minecraft:librarian", "minecraft:cartographer"], "master", { scholarship: 46, survival: 42 }),
    target: target("minecraft:ancient_city", "minecraft:overworld", 768, 192),
    objectives: [
      structure("visit_city", "minecraft:ancient_city", 768, 192,
        "Reach the marked Ancient City.", "The Ancient City has been revisited."),
      item("bring_echoes", "minecraft:echo_shard", 2,
        "Bring 2 echo shards.", "The echo shards are wrapped.")
    ],
    cooldown: 7,
    rewards: reward(430, 23, 9, "echo_trade"),
    loot: loot(28, 40, [bonus("minecraft:diamond", 1, 2, 2), bonus("minecraft:enchanted_golden_apple", 1, 1, 1), bonus("minecraft:experience_bottle", 16, 24, 2)]),
    icon: "minecraft:echo_shard",
    profile: voice({
      offer: "The archive has one echo shard and too many questions for it.",
      reason: "Return to an Ancient City, walk softly, and bring two more pieces of its memory.",
      ask: "Reach the marked Ancient City and bring 2 echo shards",
      accepted: "Wool underfoot. No bells, no arrows, and no proud speeches underground.",
      reminder: "Two echo shards from the marked city. If the dark starts listening, stop moving.",
      success: "Even wrapped in wool, these shards sound like a room remembering itself.",
      successAlt: "The archive has its echoes. You have earned a louder reward.",
      decline: "Good. Fear is useful when it keeps quiet places quiet."
    })
  },
  {
    id: "ender_freight",
    title: "Portable Storage",
    description: "Survey an End City and recover shells for secure village storage.",
    tier: "late",
    provider: provider(["minecraft:cartographer", "minecraft:leatherworker"], "master", { cartography: 48, survival: 45 }),
    target: target("minecraft:end_city", "minecraft:the_end", 640, 160),
    objectives: [
      structure("visit_city", "minecraft:end_city", 640, 160,
        "Reach the marked End City.", "The End City route is confirmed."),
      kill("clear_shulkers", ["minecraft:shulker"], 4,
        "Defeat 4 shulkers.", "Four shulkers have been cleared."),
      item("bring_shells", "minecraft:shulker_shell", 2,
        "Bring 2 shulker shells.", "The shulker shells are ready.")
    ],
    cooldown: 7,
    rewards: reward(500, 26, 10, "ender_freight"),
    loot: loot(32, 44, [bonus("minecraft:diamond", 2, 3, 1), bonus("minecraft:ender_pearl", 8, 16, 2), bonus("minecraft:experience_bottle", 18, 28, 2)]),
    icon: "minecraft:shulker_shell",
    profile: voice({
      offer: "Village storage stays put, which is exactly its problem when the village travels.",
      reason: "Chart an End City and bring back shells that can hold a chest without holding a place.",
      ask: "Reach the marked End City, defeat 4 shulkers, and bring 2 shulker shells",
      accepted: "Keep a pearl ready. Shulkers enjoy teaching gravity by taking it away.",
      reminder: "The End City mark, four shulkers, and two shells for the freight boxes.",
      success: "Two shells, one true route, and no cargo lost to the void. Excellent.",
      successAlt: "These will become the finest traveling chests the village has seen.",
      decline: "The End will remain inconvenient. It is practiced at that."
    })
  },
  {
    id: "dragon_sample",
    title: "Bottled Breath",
    description: "Bottle dragon breath and return End stone for careful study.",
    tier: "late",
    provider: provider(["minecraft:cleric", "minecraft:librarian"], "master", { scholarship: 50, survival: 46 }),
    objectives: [
      kill("clear_endermen", ["minecraft:enderman"], 4,
        "Defeat 4 Endermen in the End.", "The Endermen have been cleared."),
      item("bring_breath", "minecraft:dragon_breath", 4,
        "Bring 4 bottles of dragon breath.", "The dragon breath is sealed."),
      item("bring_end_stone", "minecraft:end_stone", 32,
        "Bring 32 End stone.", "The End stone samples are ready.")
    ],
    cooldown: 8,
    rewards: reward(480, 25, 10, "dragon_sample"),
    loot: loot(30, 42, [bonus("minecraft:ghast_tear", 2, 4, 2), bonus("minecraft:diamond", 2, 3, 1), bonus("minecraft:end_crystal", 1, 2, 1)]),
    icon: "minecraft:dragon_breath",
    profile: voice({
      offer: "Dragon breath lingers above the End fountain, violet and entirely unwilling to be sensible.",
      reason: "Bottle a little of it, clear the staring locals, and bring stone for comparison.",
      ask: "Defeat 4 Endermen, then bring 4 dragon breath and 32 End stone",
      accepted: "Glass bottles first, carved pumpkin second, confidence a distant third.",
      reminder: "Four Endermen, four bottles of breath, and thirty-two End stone.",
      success: "The bottles are sealed and the stone is not humming. We may open one later.",
      successAlt: "A piece of the dragon's storm, caught without breaking the bottle or you.",
      decline: "Nothing that glows purple in the End needs to be collected in haste."
    })
  },
  {
    id: "beacon_polish",
    title: "A Star for Home",
    description: "Defeat a Wither and surrender its Nether Star for a village beacon.",
    tier: "late",
    provider: provider(["minecraft:armorer", "minecraft:cleric"], "master", { guarding: 55, survival: 52 }),
    objectives: [
      kill("defeat_wither", ["minecraft:wither"], 1,
        "Defeat 1 Wither.", "The Wither has been defeated."),
      item("bring_star", "minecraft:nether_star", 1,
        "Bring 1 Nether Star.", "The Nether Star is ready.")
    ],
    cooldown: 10,
    rewards: reward(1000, 35, 15, "beacon_polish"),
    loot: lootWithGuaranteed(
      48,
      64,
      bonus("minecraft:diamond", 3, 5, 1),
      [
        bonus("minecraft:netherite_upgrade_smithing_template", 1, 1, 1),
        bonus("minecraft:gold_block", 3, 6, 2),
        bonus("minecraft:experience_bottle", 24, 40, 2)
      ]
    ),
    icon: "minecraft:nether_star",
    profile: voice({
      offer: "The village can build a beacon base. The missing piece is the one that fights back.",
      reason: "Bring down a Wither and surrender its Nether Star. The whole village will stand in that light.",
      ask: "Defeat a Wither and bring back its Nether Star",
      accepted: "Fight it far from every roof, bell, bed, animal, and thing you would miss.",
      reminder: "One Wither, one Nether Star. There is no smaller way to say it.",
      success: "This star will shine over every field you nearly died to protect.",
      successAlt: "The Wither is gone. The village gets the light, and you get the honor and the pay.",
      decline: "That is not cowardice. A Wither deserves a carefully chosen day."
    })
  }
];

const questlineQuests = [
  {
    id: "new_furrows",
    title: "Furrows",
    description: "Prepare seed, water, and a sound hoe for a new village field.",
    questline: "green_thumb",
    provider: provider(["minecraft:farmer"], "novice", { farming: 12 }),
    objectives: [
      item("bring_seeds", "minecraft:wheat_seeds", 24,
        "Bring 24 wheat seeds.", "The seed sack is ready."),
      item("carry_hoe", "minecraft:iron_hoe", 1,
        "Carry 1 iron hoe for the new field.", "The iron hoe is ready.", false),
      item("carry_water", "minecraft:water_bucket", 1,
        "Carry 1 water bucket for the furrows.", "The water bucket is ready.", false)
    ],
    rewards: reward(90, 8, 3, "new_furrows"),
    loot: loot(6, 10, [bonus("minecraft:bone_meal", 8, 16, 2), bonus("minecraft:golden_carrot", 2, 4, 1)]),
    icon: "minecraft:iron_hoe",
    profile: voice({
      offer: "There is good ground beyond the last fence, but good ground still needs hands.",
      reason: "Bring seed, water, and a hoe that will survive more than one row.",
      ask: "Bring 24 wheat seeds, an iron hoe, and a water bucket",
      accepted: "Keep the seeds dry. The water can wait until the furrows are open.",
      reminder: "Seeds for the rows, an iron hoe, and one bucket of water.",
      success: "That is everything a bare patch needs to become a field.",
      successAlt: "The furrows can begin at sunrise. You have given them a fair start.",
      decline: "The ground will keep. We should not open it without a plan."
    })
  },
  {
    kind: "choice",
    id: "choose_the_soil",
    title: "Choose the Soil",
    description: "Choose whether the new field will rely on canals or compost.",
    questline: "green_thumb",
    parent: "villagerretaliation:new_furrows",
    provider: provider(["minecraft:farmer"], "apprentice", { farming: 18 }),
    icon: "minecraft:farmland",
    rewards: reward(125, 10, 4, "choose_the_soil"),
    loot: loot(8, 13, [bonus("minecraft:golden_carrot", 3, 6, 2), bonus("minecraft:experience_bottle", 4, 7, 1)]),
    intro: [
      "The field can drink from stone-lined canals, or eat from deep compost. We have enough hands for one plan.",
      "Canals are steady but costly. Compost is cheaper, but someone must keep turning it."
    ],
    decline: "Then the soil remains undecided, and undecided soil grows mostly weeds.",
    branches: [
      {
        value: "canal",
        label: "Build the canals",
        chosen: "Canals, then. Give the water clean stone edges and no place to wander.",
        notification: "You chose canals for the new field.",
        objectives: [
          blockEvent("lay_canal", "block_place", ["minecraft:stone_bricks"], 12,
            "Place 12 stone bricks for canal walls.", "The canal walls are laid."),
          item("carry_water", "minecraft:water_bucket", 1,
            "Carry a water bucket to fill the canal.", "The water bucket is ready.", false)
        ],
        reminder: "Twelve stone bricks laid for the canal, then bring the water bucket back to the field.",
        turnIn: "The canal edges hold. Water can reach every row without taking the path with it.",
        complete: "The first channel is open. From here, the field follows water."
      },
      {
        value: "compost",
        label: "Feed the soil",
        chosen: "Compost, then. We will make the field rich before asking it for anything.",
        notification: "You chose compost for the new field.",
        objectives: [
          blockEvent("work_composter", "block_interact", ["minecraft:composter"], 5,
            "Work a composter 5 times.", "The compost has been worked."),
          item("bring_bone_meal", "minecraft:bone_meal", 16,
            "Bring 16 bone meal for the first rows.", "The bone meal is ready.")
        ],
        reminder: "Work the composter five times and bring sixteen bone meal for the first rows.",
        turnIn: "The soil is dark, loose, and ready. It smells terrible, which is encouraging.",
        complete: "The first bed is fed. From here, the field follows compost."
      }
    ]
  },
  {
    id: "canal_beds",
    title: "Irrigation",
    description: "Finish the canal route with stone, reeds, and sheltered water edges.",
    questline: "green_thumb",
    parent: "villagerretaliation:choose_the_soil",
    conditions: branchCondition("choose_the_soil", "canal"),
    provider: provider(["minecraft:farmer", "minecraft:mason"], "journeyman", { farming: 22, masonry: 16 }),
    objectives: [
      blockEvent("finish_walls", "block_place", ["minecraft:stone_bricks"], 24,
        "Place 24 stone bricks along the field canals.", "The canal walls are finished."),
      item("bring_lilies", "minecraft:lily_pad", 8,
        "Bring 8 lily pads for sheltered water.", "The lily pads are ready.")
    ],
    rewards: reward(175, 13, 5, "canal_beds"),
    loot: loot(12, 19, [bonus("minecraft:iron_ingot", 4, 8, 2), bonus("minecraft:water_bucket", 1, 1, 1)]),
    icon: "minecraft:lily_pad",
    profile: voice({
      offer: "The first channel works. Now it needs walls that will survive boots, rain, and curious sheep.",
      reason: "Finish the stone edges and bring lily pads to shade the still corners.",
      ask: "Place 24 stone bricks and bring 8 lily pads",
      accepted: "Keep the corners square. Water finds every lazy gap.",
      reminder: "Twenty-four stone bricks along the channels, eight lily pads for cover.",
      success: "The water reaches every bed and stays off the road. That is a proper canal.",
      successAlt: "Stone below, green shade above. The canal field is finished.",
      decline: "Then keep the sheep away from the wet edge."
    })
  },
  {
    id: "rich_earth",
    title: "Rich Earth",
    description: "Finish the compost route with worked heaps and deep fertile beds.",
    questline: "green_thumb",
    parent: "villagerretaliation:choose_the_soil",
    conditions: branchCondition("choose_the_soil", "compost"),
    provider: provider(["minecraft:farmer"], "journeyman", { farming: 24 }),
    objectives: [
      blockEvent("turn_heaps", "block_interact", ["minecraft:composter"], 8,
        "Work composters 8 times.", "The compost heaps are fully worked."),
      item("bring_dirt", "minecraft:dirt", 32,
        "Bring 32 dirt for raised beds.", "The bed soil is ready."),
      item("bring_bone_meal", "minecraft:bone_meal", 24,
        "Bring 24 bone meal for the first planting.", "The bone meal is ready.")
    ],
    rewards: reward(180, 13, 5, "rich_earth"),
    loot: loot(12, 19, [bonus("minecraft:golden_carrot", 4, 8, 2), bonus("minecraft:golden_apple", 1, 2, 1)]),
    icon: "minecraft:composter",
    profile: voice({
      offer: "The first bed is rich. Now the rest of the field needs the same patience.",
      reason: "Turn the heaps, raise the beds, and feed the first planting well.",
      ask: "Work composters 8 times, then bring 32 dirt and 24 bone meal",
      accepted: "A good field is built twice: once from dirt, once from what we return to it.",
      reminder: "Eight turns of the composters, thirty-two dirt, twenty-four bone meal.",
      success: "Every bed is dark and ready. The compost field is finished.",
      successAlt: "Nothing wasted, every row fed. That is a field worth keeping.",
      decline: "The heaps will keep cooking, whether we watch or not."
    })
  },
  {
    id: "below_the_bell",
    title: "Below the Bell",
    description: "Open a village mine and recover its first useful iron.",
    questline: "deep_delvers",
    provider: provider(["minecraft:toolsmith", "minecraft:armorer"], "apprentice", { mining: 18, smithing: 14 }),
    objectives: [
      blockEvent("open_shaft", "block_break", ["minecraft:stone", "minecraft:deepslate"], 64,
        "Mine 64 stone or deepslate.", "The first shaft is open."),
      item("bring_iron", "minecraft:raw_iron", 16,
        "Bring 16 raw iron from the new shaft.", "The raw iron is ready.")
    ],
    rewards: reward(150, 11, 4, "below_the_bell"),
    loot: loot(10, 16, [bonus("minecraft:iron_ingot", 6, 12, 2), bonus("minecraft:diamond", 1, 1, 1)]),
    icon: "minecraft:iron_pickaxe",
    profile: voice({
      offer: "The village stands on stone we have never asked a useful question.",
      reason: "Open a proper shaft below the bell and bring up the first raw iron.",
      ask: "Mine 64 stone or deepslate and bring 16 raw iron",
      accepted: "Brace the entrance as you go. A mine should not begin with a rescue.",
      reminder: "Sixty-four stone broken, sixteen raw iron brought to the smithy.",
      success: "The shaft is open and the first iron is honest. We can build on that.",
      successAlt: "Stone below the bell has finally paid rent.",
      decline: "The stone has waited longer than we have. It can wait."
    })
  },
  {
    kind: "choice",
    id: "mark_the_shaft",
    title: "Mark the Shaft",
    description: "Choose rails or timber braces for the new village mine.",
    questline: "deep_delvers",
    parent: "villagerretaliation:below_the_bell",
    provider: provider(["minecraft:toolsmith", "minecraft:armorer"], "journeyman", { mining: 25, smithing: 20 }),
    icon: "minecraft:minecart",
    rewards: reward(210, 14, 6, "mark_the_shaft"),
    loot: loot(14, 22, [bonus("minecraft:iron_ingot", 8, 14, 2), bonus("minecraft:diamond", 1, 2, 1)]),
    intro: [
      "The shaft can carry ore on rails, or hold its roof with heavy timber. We cannot afford both yet.",
      "Rails make distance cheap. Braces make bad stone less frightening. Choose what this mine needs first."
    ],
    decline: "An unmarked shaft is only a hole with ambitions.",
    branches: [
      {
        value: "rail",
        label: "Lay the rails",
        chosen: "Rails, then. Give every slope a level thought before you set iron on it.",
        notification: "You chose rails for the village mine.",
        objectives: [
          blockEvent("lay_rails", "block_place", ["minecraft:rail"], 24,
            "Place 24 rails in the mine.", "The first rail line is laid."),
          item("bring_minecart", "minecraft:minecart", 1,
            "Bring 1 minecart for the line.", "The minecart is ready.", false)
        ],
        reminder: "Lay twenty-four rails and bring a minecart to test the line.",
        turnIn: "The cart rolls cleanly from the face to the daylight.",
        complete: "The first mine road is open. The deeper work will follow iron."
      },
      {
        value: "timber",
        label: "Brace the roof",
        chosen: "Timber, then. A slower mine is still better than a buried one.",
        notification: "You chose timber braces for the village mine.",
        objectives: [
          blockEvent("place_braces", "block_place", ["minecraft:oak_log", "minecraft:spruce_log"], 16,
            "Place 16 logs as mine braces.", "The first braces are set."),
          item("bring_lanterns", "minecraft:lantern", 4,
            "Bring 4 lanterns for the braced shaft.", "The lanterns are ready.")
        ],
        reminder: "Set sixteen log braces and hang four lanterns through the first stretch.",
        turnIn: "The roof is quiet and the braces are straight. That is the sound we wanted.",
        complete: "The first mine road is safe. The deeper work will follow timber."
      }
    ]
  },
  {
    id: "iron_road",
    title: "Iron Road",
    description: "Drive the rail branch deeper with powered track and a freight cart.",
    questline: "deep_delvers",
    parent: "villagerretaliation:mark_the_shaft",
    conditions: branchCondition("mark_the_shaft", "rail"),
    provider: provider(["minecraft:toolsmith"], "expert", { mining: 32, smithing: 28 }),
    objectives: [
      blockEvent("power_line", "block_place", ["minecraft:powered_rail"], 8,
        "Place 8 powered rails.", "The powered rails are set."),
      item("bring_torches", "minecraft:redstone_torch", 8,
        "Bring 8 redstone torches.", "The redstone torches are ready."),
      item("bring_freight_cart", "minecraft:chest_minecart", 1,
        "Bring 1 minecart with chest.", "The freight cart is ready.")
    ],
    rewards: reward(280, 17, 7, "iron_road"),
    loot: loot(19, 28, [bonus("minecraft:diamond", 1, 2, 1), bonus("minecraft:gold_ingot", 6, 12, 2)]),
    icon: "minecraft:powered_rail",
    profile: voice({
      offer: "The first rails work, but the deeper slope sends full carts backward and empty carts nowhere.",
      reason: "Power the steep run and give the mine a cart built for freight.",
      ask: "Place 8 powered rails, then bring 8 redstone torches and 1 chest minecart",
      accepted: "Test the brake before the load. Ore is patient; minecarts are not.",
      reminder: "Eight powered rails, eight redstone torches, one freight cart.",
      success: "The iron road climbs under its own power. The mine can reach deeper now.",
      successAlt: "Full carts out, empty carts in. That is a mine beginning to breathe.",
      decline: "Then push carefully. The slope has already chosen a favorite direction."
    })
  },
  {
    id: "timber_brace",
    title: "Timber Brace",
    description: "Secure the timber branch and clear spiders from the lower supports.",
    questline: "deep_delvers",
    parent: "villagerretaliation:mark_the_shaft",
    conditions: branchCondition("mark_the_shaft", "timber"),
    provider: provider(["minecraft:armorer", "minecraft:toolsmith"], "expert", { mining: 30, guarding: 24 }),
    objectives: [
      blockEvent("set_cross_braces", "block_place", ["minecraft:oak_fence", "minecraft:spruce_fence"], 24,
        "Place 24 fences as cross-braces.", "The cross-braces are set."),
      kill("clear_spiders", ["minecraft:cave_spider"], 4,
        "Defeat 4 cave spiders near the supports.", "The cave spiders are cleared."),
      item("bring_string", "minecraft:string", 8,
        "Bring 8 string from the lower shaft.", "The string proof is ready.")
    ],
    rewards: reward(285, 17, 7, "timber_brace"),
    loot: loot(19, 28, [bonus("minecraft:diamond", 1, 2, 1), bonus("minecraft:golden_apple", 1, 2, 1)]),
    icon: "minecraft:oak_fence",
    profile: voice({
      offer: "The braces hold, but webs are pulling at the lower supports and something small is clicking behind them.",
      reason: "Cross-brace the shaft, clear the cave spiders, and bring string as proof the nests are gone.",
      ask: "Place 24 fence braces, defeat 4 cave spiders, and bring 8 string",
      accepted: "Set each brace before you cut the web beside it. Roof first, heroics second.",
      reminder: "Twenty-four cross-braces, four cave spiders, eight string.",
      success: "The roof is still, the webs are gone, and the timber road can go deeper.",
      successAlt: "Strong braces and no clicking in the dark. That is a safe shaft.",
      decline: "Then stay above the lower lantern. The spiders have claimed everything past it."
    })
  },
  {
    id: "small_spark",
    title: "Redstone",
    description: "Supply the first components for a village redstone workshop.",
    questline: "redstone_works",
    provider: provider(["minecraft:toolsmith", "minecraft:librarian"], "journeyman", { crafting: 24, scholarship: 18 }),
    objectives: [
      item("bring_redstone", "minecraft:redstone", 16,
        "Bring 16 redstone dust.", "The redstone dust is ready."),
      item("bring_repeaters", "minecraft:repeater", 2,
        "Bring 2 redstone repeaters.", "The repeaters are ready."),
      item("bring_levers", "minecraft:lever", 2,
        "Bring 2 levers.", "The levers are ready.")
    ],
    rewards: reward(165, 12, 5, "small_spark"),
    loot: loot(11, 17, [bonus("minecraft:quartz", 6, 12, 2), bonus("minecraft:slime_ball", 3, 6, 1)]),
    icon: "minecraft:redstone",
    profile: voice({
      offer: "We have doors, lamps, and a great many villagers tired of opening both by hand.",
      reason: "Bring enough redstone to build one careful circuit before anyone builds ten foolish ones.",
      ask: "Bring 16 redstone dust, 2 repeaters, and 2 levers",
      accepted: "Keep the dust dry and the levers apart. We are testing one idea at a time.",
      reminder: "Sixteen redstone, two repeaters, two levers for the workshop bench.",
      success: "A small spark, properly measured. That is how useful machines begin.",
      successAlt: "The circuit bench is stocked. Now we can make mistakes in a straight line.",
      decline: "Then every door remains proudly manual."
    })
  },
  {
    kind: "choice",
    id: "power_the_gate",
    title: "Power the Gate",
    description: "Choose daylight or sculk to control the village gate.",
    questline: "redstone_works",
    parent: "villagerretaliation:small_spark",
    provider: provider(["minecraft:toolsmith", "minecraft:librarian"], "expert", { crafting: 30, scholarship: 24 }),
    icon: "minecraft:redstone_lamp",
    rewards: reward(240, 15, 6, "power_the_gate"),
    loot: loot(16, 24, [bonus("minecraft:quartz", 8, 16, 2), bonus("minecraft:ender_pearl", 2, 4, 1)]),
    intro: [
      "The gate can follow daylight, or listen for footsteps through sculk. Both work, but they do not fail the same way.",
      "A sun switch is simple and visible. A sculk trigger is quiet and clever. Choose which lesson the gate should learn."
    ],
    decline: "Then the gate keeps one reliable part: the villager standing beside it.",
    branches: [
      {
        value: "sunlight",
        label: "Follow the sun",
        chosen: "Sunlight, then. The gate will keep honest hours and make its workings plain.",
        notification: "You chose a daylight circuit for the village gate.",
        objectives: [
          item("bring_detectors", "minecraft:daylight_detector", 2,
            "Bring 2 daylight detectors.", "The daylight detectors are ready."),
          item("bring_lamps", "minecraft:redstone_lamp", 4,
            "Bring 4 redstone lamps.", "The redstone lamps are ready.")
        ],
        reminder: "Two daylight detectors and four redstone lamps for the gate circuit.",
        turnIn: "The lamps follow the sky cleanly. Everyone can see when the gate thinks it is night.",
        complete: "The daylight circuit works. The gate's next lesson will come from the sun."
      },
      {
        value: "whisper",
        label: "Listen through sculk",
        chosen: "Sculk, then. The gate will listen, but wool must teach it what to ignore.",
        notification: "You chose a sculk circuit for the village gate.",
        objectives: [
          item("bring_sensors", "minecraft:sculk_sensor", 2,
            "Bring 2 sculk sensors.", "The sculk sensors are ready."),
          item("bring_wool", "minecraft:white_wool", 16,
            "Bring 16 white wool for vibration shielding.", "The wool shielding is ready.")
        ],
        reminder: "Two sculk sensors and sixteen white wool to quiet the wrong footsteps.",
        turnIn: "The sensor hears the path and ignores the workshop. Clever, and a little unsettling.",
        complete: "The listening circuit works. The gate's next lesson will come from whispers."
      }
    ]
  },
  {
    id: "sun_switch",
    title: "Daylight",
    description: "Finish the daylight branch with lamps and a measured control circuit.",
    questline: "redstone_works",
    parent: "villagerretaliation:power_the_gate",
    conditions: branchCondition("power_the_gate", "sunlight"),
    provider: provider(["minecraft:toolsmith"], "expert", { crafting: 34, smithing: 22 }),
    objectives: [
      blockEvent("place_lamps", "block_place", ["minecraft:redstone_lamp"], 8,
        "Place 8 redstone lamps along the gate road.", "The gate lamps are placed."),
      item("bring_comparators", "minecraft:comparator", 2,
        "Bring 2 redstone comparators.", "The comparators are ready."),
      item("bring_quartz", "minecraft:quartz", 12,
        "Bring 12 Nether quartz for repairs.", "The quartz is ready.")
    ],
    rewards: reward(300, 18, 7, "sun_switch"),
    loot: loot(20, 30, [bonus("minecraft:glowstone", 4, 8, 2), bonus("minecraft:diamond", 1, 2, 1)]),
    icon: "minecraft:daylight_detector",
    profile: voice({
      offer: "The gate follows daylight. Now the road lamps should follow it too.",
      reason: "Set the lamps where the path bends and give the control box a way to compare weak light with strong.",
      ask: "Place 8 redstone lamps and bring 2 comparators and 12 quartz",
      accepted: "Face every lamp toward the road. Lighting the roof helps only bats.",
      reminder: "Eight lamps placed, two comparators and twelve quartz returned.",
      success: "The road wakes at dusk and sleeps after dawn. No villager needs to remember the lever.",
      successAlt: "The sun now keeps the village lamps better than any watch schedule.",
      decline: "Then the gate can keep the light to itself for now."
    })
  },
  {
    id: "quiet_trigger",
    title: "The Listening Gate",
    description: "Finish the sculk branch with a city survey and careful wool shielding.",
    questline: "redstone_works",
    parent: "villagerretaliation:power_the_gate",
    conditions: branchCondition("power_the_gate", "whisper"),
    provider: provider(["minecraft:librarian", "minecraft:toolsmith"], "master", { scholarship: 40, crafting: 34 }),
    target: target("minecraft:ancient_city", "minecraft:overworld", 768, 192),
    objectives: [
      structure("study_city", "minecraft:ancient_city", 768, 192,
        "Study the marked Ancient City without waking its guardian.", "The Ancient City signal has been studied."),
      blockEvent("place_wool", "block_place", ["minecraft:white_wool", "minecraft:gray_wool", "minecraft:black_wool"], 32,
        "Place 32 wool blocks as vibration shielding.", "The wool shielding is placed."),
      item("bring_sensor", "minecraft:calibrated_sculk_sensor", 1,
        "Bring 1 calibrated sculk sensor.", "The calibrated sensor is ready.")
    ],
    rewards: reward(430, 23, 9, "quiet_trigger"),
    loot: loot(28, 40, [bonus("minecraft:diamond", 2, 3, 2), bonus("minecraft:echo_shard", 1, 2, 1)]),
    icon: "minecraft:calibrated_sculk_sensor",
    profile: voice({
      offer: "The village sensor works, but the Ancient City knew how to shape sound long before we did.",
      reason: "Study its silence, lay wool around the loud paths, and recover one calibrated sensor.",
      ask: "Study the marked Ancient City, place 32 wool, and bring 1 calibrated sculk sensor",
      accepted: "Every block of wool is cheaper than one shrieker. Place them before curiosity.",
      reminder: "The city signal, thirty-two wool placed, one calibrated sensor returned.",
      success: "The gate now hears what matters and ignores what does not. Let us hope it keeps that wisdom.",
      successAlt: "A machine taught by silence. The sculk branch is complete.",
      decline: "Good. Ancient Cities punish noisy lessons."
    })
  },
  {
    id: "through_fire",
    title: "Through Fire",
    description: "Prepare the supplies for a permanent village route through the Nether.",
    questline: "nether_routes",
    provider: provider(["minecraft:cartographer", "minecraft:cleric"], "expert", { cartography: 34, survival: 32 }),
    objectives: [
      item("bring_obsidian", "minecraft:obsidian", 10,
        "Bring 10 obsidian for a route portal.", "The portal obsidian is ready."),
      item("bring_fire_charges", "minecraft:fire_charge", 2,
        "Bring 2 fire charges.", "The fire charges are ready."),
      item("bring_magma_cream", "minecraft:magma_cream", 4,
        "Bring 4 magma cream for fire resistance.", "The magma cream is ready.")
    ],
    rewards: reward(260, 16, 7, "through_fire"),
    loot: loot(18, 26, [bonus("minecraft:gold_ingot", 8, 16, 2), bonus("minecraft:ghast_tear", 1, 2, 1)]),
    icon: "minecraft:obsidian",
    profile: voice({
      offer: "One portal is an escape. A marked portal with supplies can become a road.",
      reason: "Bring the frame, a way to light it, and enough magma cream to survive the first mistake.",
      ask: "Bring 10 obsidian, 2 fire charges, and 4 magma cream",
      accepted: "Count the obsidian twice. A nine-block portal is only a heavy doorway to nowhere.",
      reminder: "Ten obsidian, two fire charges, four magma cream for the route chest.",
      success: "The frame is complete and the fire supplies are sealed. The road can cross worlds.",
      successAlt: "We have a doorway. Next we decide where the hot road leads.",
      decline: "The Nether will not cool while we think."
    })
  },
  {
    kind: "choice",
    id: "choose_a_road",
    title: "Choose a Road",
    description: "Choose a fortress route or a bastion route through the Nether.",
    questline: "nether_routes",
    parent: "villagerretaliation:through_fire",
    provider: provider(["minecraft:cartographer", "minecraft:cleric"], "master", { cartography: 42, survival: 40 }),
    icon: "minecraft:lodestone",
    rewards: reward(360, 20, 8, "choose_a_road"),
    loot: loot(24, 34, [bonus("minecraft:diamond", 1, 2, 1), bonus("minecraft:gold_block", 1, 3, 2)]),
    intro: [
      "The Nether road can follow fortress bridges or bastion walls. One is full of fire; the other is full of owners.",
      "A fortress offers brewing supplies. A bastion offers gold and better stonework. Neither offers kindness."
    ],
    decline: "Then the portal remains a doorway without a road.",
    branches: [
      {
        value: "fortress",
        label: "Follow the fortress",
        chosen: "Fortress road. Mark every bridge and never stand where a blaze can see both sides of you.",
        notification: "You chose the Nether Fortress road.",
        objectives: [
          structure("visit_fortress", "minecraft:fortress", 384, 128,
            "Reach the marked Nether Fortress.", "The fortress route is marked.", "minecraft:the_nether"),
          kill("defeat_blazes", ["minecraft:blaze"], 4,
            "Defeat 4 blazes.", "Four blazes have been cleared."),
          item("bring_rods", "minecraft:blaze_rod", 2,
            "Bring 2 blaze rods.", "The blaze rods are ready.")
        ],
        reminder: "Reach the fortress mark, defeat four blazes, and bring two rods.",
        turnIn: "The bridge bearings are clear and the blaze count is written in ash.",
        complete: "The fortress road is marked. The final route will follow black brick."
      },
      {
        value: "bastion",
        label: "Follow the bastion",
        chosen: "Bastion road. Wear gold, watch the brutes, and do not admire anything with your hands.",
        notification: "You chose the Bastion Remnant road.",
        objectives: [
          structure("visit_bastion", "minecraft:bastion_remnant", 512, 160,
            "Reach the marked Bastion Remnant.", "The bastion route is marked.", "minecraft:the_nether"),
          kill("defeat_brutes", ["minecraft:piglin_brute"], 2,
            "Defeat 2 Piglin Brutes.", "Two Piglin Brutes have been cleared."),
          item("bring_gilded_stone", "minecraft:gilded_blackstone", 4,
            "Bring 4 gilded blackstone.", "The gilded blackstone is ready.")
        ],
        reminder: "Reach the bastion mark, defeat two brutes, and bring four gilded blackstone.",
        turnIn: "The bastion bearing is true, and the gilded stone proves you crossed its walls.",
        complete: "The bastion road is marked. The final route will follow broken gold."
      }
    ]
  },
  {
    id: "fortress_line",
    title: "Black Bridges",
    description: "Secure the fortress branch against Wither Skeleton patrols.",
    questline: "nether_routes",
    parent: "villagerretaliation:choose_a_road",
    conditions: branchCondition("choose_a_road", "fortress"),
    provider: provider(["minecraft:cleric", "minecraft:cartographer"], "master", { survival: 46, cartography: 44 }),
    objectives: [
      kill("clear_wither_skeletons", ["minecraft:wither_skeleton"], 5,
        "Defeat 5 Wither Skeletons along the route.", "The fortress patrol is cleared."),
      item("bring_bricks", "minecraft:nether_bricks", 32,
        "Bring 32 Nether bricks for route markers.", "The route bricks are ready."),
      item("bring_wart", "minecraft:nether_wart", 16,
        "Bring 16 Nether Wart for the route chest.", "The Nether Wart is ready.")
    ],
    rewards: reward(470, 25, 10, "fortress_line"),
    loot: loot(31, 44, [bonus("minecraft:diamond", 2, 4, 2), bonus("minecraft:wither_skeleton_skull", 1, 1, 1)]),
    icon: "minecraft:nether_bricks",
    profile: voice({
      offer: "The fortress bearing is true, but Wither Skeletons have found the same bridges.",
      reason: "Clear the patrol and build markers from the fortress's own black brick.",
      ask: "Defeat 5 Wither Skeletons and bring 32 Nether bricks and 16 Nether Wart",
      accepted: "Mark the safe turns low on the wall. Ghasts enjoy editing tall signs.",
      reminder: "Five Wither Skeletons, thirty-two bricks, sixteen Nether Wart.",
      success: "The fortress line is marked, stocked, and quieter than we found it.",
      successAlt: "A road through flame and black bone now leads back to the village.",
      decline: "Then the black bridges keep their patrols a little longer."
    })
  },
  {
    id: "bastion_line",
    title: "Broken Gold",
    description: "Secure the bastion branch and build durable blackstone route marks.",
    questline: "nether_routes",
    parent: "villagerretaliation:choose_a_road",
    conditions: branchCondition("choose_a_road", "bastion"),
    provider: provider(["minecraft:armorer", "minecraft:cartographer"], "master", { guarding: 46, cartography: 44 }),
    objectives: [
      kill("clear_brutes", ["minecraft:piglin_brute"], 3,
        "Defeat 3 Piglin Brutes along the route.", "The bastion patrol is cleared."),
      item("bring_blackstone", "minecraft:blackstone", 32,
        "Bring 32 blackstone for route markers.", "The blackstone markers are ready."),
      item("bring_gold", "minecraft:gold_block", 2,
        "Bring 2 gold blocks for safe-route caches.", "The gold blocks are ready.")
    ],
    rewards: reward(490, 26, 10, "bastion_line"),
    loot: loot(33, 46, [bonus("minecraft:diamond", 2, 4, 2), bonus("minecraft:ancient_debris", 1, 2, 1)]),
    icon: "minecraft:gilded_blackstone",
    profile: voice({
      offer: "The bastion bearing is true, but the brutes treat every marker as a challenge.",
      reason: "Clear the route, set blackstone marks, and stock gold where stranded travelers can barter.",
      ask: "Defeat 3 Piglin Brutes and bring 32 blackstone and 2 gold blocks",
      accepted: "Wear one piece of gold. It will not impress the brutes, but everyone else may pause.",
      reminder: "Three brutes, thirty-two blackstone, two gold blocks for the caches.",
      success: "The bastion line is marked, stocked, and expensive enough to be respected.",
      successAlt: "A road through broken gold now leads back to the village.",
      decline: "Then the bastion keeps its walls and its opinions."
    })
  },
  {
    id: "empty_sky",
    title: "Beyond the Island",
    description: "Prepare an expedition beyond the central End island.",
    questline: "end_survey",
    provider: provider(["minecraft:cartographer", "minecraft:librarian"], "master", { cartography: 46, scholarship: 40 }),
    objectives: [
      item("bring_eyes", "minecraft:ender_eye", 4,
        "Bring 4 Eyes of Ender for bearings.", "The Eyes of Ender are ready."),
      item("bring_membranes", "minecraft:phantom_membrane", 4,
        "Bring 4 phantom membranes for slow-falling brews.", "The phantom membranes are ready."),
      item("bring_pumpkin", "minecraft:carved_pumpkin", 1,
        "Bring 1 carved pumpkin for safe observation.", "The carved pumpkin is ready.")
    ],
    rewards: reward(350, 20, 8, "empty_sky"),
    loot: loot(23, 34, [bonus("minecraft:ender_pearl", 8, 16, 2), bonus("minecraft:diamond", 1, 2, 1)]),
    icon: "minecraft:ender_eye",
    profile: voice({
      offer: "The End does not end at the dragon island. It only stops explaining itself.",
      reason: "Prepare eyes for bearings, membranes for slow falling, and a pumpkin for the staring locals.",
      ask: "Bring 4 Eyes of Ender, 4 phantom membranes, and 1 carved pumpkin",
      accepted: "Pearls fail over the void only once. Aim with patience.",
      reminder: "Four eyes, four membranes, one carved pumpkin for the outer islands.",
      success: "The expedition packs are ready. Now we choose which light to follow.",
      successAlt: "Everything needed to cross an empty sky, except good sense. Bring your own.",
      decline: "The outer islands are patient and very far away."
    })
  },
  {
    kind: "choice",
    id: "choose_a_star",
    title: "Choose a Star",
    description: "Choose a chorus trail or an End City light for the survey.",
    questline: "end_survey",
    parent: "villagerretaliation:empty_sky",
    provider: provider(["minecraft:cartographer", "minecraft:librarian"], "master", { cartography: 50, scholarship: 44 }),
    icon: "minecraft:end_rod",
    rewards: reward(460, 24, 9, "choose_a_star"),
    loot: loot(30, 42, [bonus("minecraft:diamond", 2, 3, 2), bonus("minecraft:shulker_shell", 1, 2, 1)]),
    intro: [
      "The outer islands offer two lights: chorus groves close to the ground, or End rods high in a city.",
      "Follow the chorus and map how the islands grow, or follow the city lights and map who built above them."
    ],
    decline: "Then the End keeps both lights beyond our map.",
    branches: [
      {
        value: "chorus",
        label: "Follow the chorus",
        chosen: "Chorus trail. Keep your feet ready for the fruit to change its mind about where you belong.",
        notification: "You chose the chorus trail through the outer End.",
        objectives: [
          kill("clear_endermen", ["minecraft:enderman"], 5,
            "Defeat 5 Endermen along the chorus trail.", "The chorus trail is clear."),
          item("bring_chorus", "minecraft:chorus_fruit", 32,
            "Bring 32 chorus fruit.", "The chorus fruit samples are ready.")
        ],
        reminder: "Clear five Endermen from the trail and bring thirty-two chorus fruit.",
        turnIn: "The fruit all came from one route, though none of it agrees where that route was.",
        complete: "The chorus trail is marked. The final survey will follow living purple branches."
      },
      {
        value: "city",
        label: "Follow the city lights",
        chosen: "City light. Watch the towers, the void between them, and every shell pretending to be a block.",
        notification: "You chose the End City lights.",
        objectives: [
          structure("visit_city", "minecraft:end_city", 640, 160,
            "Reach the marked End City.", "The End City light is marked.", "minecraft:the_end"),
          kill("clear_shulkers", ["minecraft:shulker"], 3,
            "Defeat 3 shulkers.", "Three shulkers have been cleared."),
          item("bring_shell", "minecraft:shulker_shell", 1,
            "Bring 1 shulker shell.", "The shulker shell is ready.")
        ],
        reminder: "Reach the city light, clear three shulkers, and bring one shell.",
        turnIn: "The city bearing holds, even if its stairways seem to resent the ground.",
        complete: "The city light is marked. The final survey will follow pale towers."
      }
    ]
  },
  {
    id: "chorus_trail",
    title: "Chorus",
    description: "Map the chorus branch by harvesting flowers and cooked fruit.",
    questline: "end_survey",
    parent: "villagerretaliation:choose_a_star",
    conditions: branchCondition("choose_a_star", "chorus"),
    provider: provider(["minecraft:cartographer", "minecraft:farmer"], "master", { cartography: 52, farming: 38 }),
    objectives: [
      blockEvent("harvest_flowers", "block_break", ["minecraft:chorus_flower"], 8,
        "Harvest 8 chorus flowers.", "The chorus flowers are harvested."),
      item("bring_popped_fruit", "minecraft:popped_chorus_fruit", 16,
        "Bring 16 popped chorus fruit.", "The popped chorus fruit is ready."),
      item("bring_end_stone", "minecraft:end_stone", 32,
        "Bring 32 End stone for the growing bed.", "The End stone is ready.")
    ],
    rewards: reward(560, 28, 11, "chorus_trail"),
    loot: loot(36, 50, [bonus("minecraft:diamond", 2, 4, 2), bonus("minecraft:elytra", 1, 1, 1)]),
    icon: "minecraft:chorus_flower",
    profile: voice({
      offer: "The first chorus mark is true. Now map how the groves spread from island to island.",
      reason: "Harvest living flowers, cook the fruit, and bring stone that can grow them near the archive.",
      ask: "Harvest 8 chorus flowers and bring 16 popped chorus fruit and 32 End stone",
      accepted: "Cut the flowers first. A chorus plant without its crown is only a purple ladder falling apart.",
      reminder: "Eight flowers harvested, sixteen popped fruit, thirty-two End stone.",
      success: "The chorus trail is mapped from root to flower. The living branch is complete.",
      successAlt: "We can grow a small piece of the outer End where the archive can watch it.",
      decline: "Then let the chorus keep growing in every direction at once."
    })
  },
  {
    id: "city_lantern",
    title: "The Way Home",
    description: "Complete the End City branch with rods, shells, and a final tower survey.",
    questline: "end_survey",
    parent: "villagerretaliation:choose_a_star",
    conditions: branchCondition("choose_a_star", "city"),
    provider: provider(["minecraft:cartographer", "minecraft:librarian"], "master", { cartography: 55, scholarship: 48 }),
    target: target("minecraft:end_city", "minecraft:the_end", 768, 160),
    objectives: [
      structure("survey_city", "minecraft:end_city", 768, 160,
        "Survey the marked End City tower.", "The End City tower is surveyed."),
      blockEvent("place_rods", "block_place", ["minecraft:end_rod"], 12,
        "Place 12 End rods as return markers.", "The return markers are lit."),
      item("bring_shells", "minecraft:shulker_shell", 2,
        "Bring 2 shulker shells for the archive.", "The shulker shells are ready.")
    ],
    rewards: reward(600, 30, 12, "city_lantern"),
    loot: loot(40, 54, [bonus("minecraft:diamond", 3, 5, 2), bonus("minecraft:elytra", 1, 1, 1)]),
    icon: "minecraft:end_rod",
    profile: voice({
      offer: "One city light is marked. The last survey must make the road home visible too.",
      reason: "Climb the tower, set End rods along the return, and recover shells for the archive.",
      ask: "Survey the marked End City, place 12 End rods, and bring 2 shulker shells",
      accepted: "Place the first rod where the gateway disappears behind you. Pride is a poor return marker.",
      reminder: "The tower survey, twelve End rods placed, two shells returned.",
      success: "The pale towers and the road home share one map now. The city branch is complete.",
      successAlt: "The End City has a bearing, a lit return, and two fewer shulkers' worth of secrets.",
      decline: "Then do not climb a tower you cannot find your way down from."
    })
  }
];

const rebalancedTrialLoot = loot(18, 26, [
  bonus("minecraft:iron_ingot", 8, 16, 2),
  bonus("minecraft:diamond", 1, 2, 1),
  bonus("minecraft:experience_bottle", 8, 14, 2),
  bonus("minecraft:wind_charge", 6, 12, 1)
]);

let modernizedCount = 0;
let legacyCount = 0;
let repeatableCount = 0;
let questlineQuestCount = 0;

for (const [relativeFile, overrides] of Object.entries(existingLegacyQuests)) {
  const file = path.join(questRoot, ...relativeFile.split("/"));
  const config = await readExistingLegacyQuest(file, overrides);
  await writeJson(file, makeWorkQuest(config));
  legacyCount++;
}

for (const [relativeFile, overrides] of Object.entries(existingRepeatables)) {
  const file = path.join(questRoot, ...relativeFile.split("/"));
  const config = await readExistingRepeatable(file, overrides);
  await writeJson(file, makeWorkQuest(config));
  modernizedCount++;
}

await writeJson(path.join(lootRoot, "trial_chamber_recall.json"), rebalancedTrialLoot);

for (const config of expansionRepeatables) {
  const complete = {
    ...config,
    questline: "village_commissions",
    repeatable: true,
    tags: ["group.village_commissions", `tier.${config.tier}`]
  };
  await writeJson(
    path.join(questRoot, "village_commissions", `${config.id}.json`),
    makeWorkQuest(complete)
  );
  await writeJson(path.join(lootRoot, `${config.id}.json`), config.loot);
  repeatableCount++;
}

for (const config of questlineQuests) {
  const quest = config.kind === "choice" ? makeChoiceQuest(config) : makeWorkQuest(config);
  await writeJson(path.join(questRoot, config.questline, `${config.id}.json`), quest);
  await writeJson(path.join(lootRoot, `${config.id}.json`), config.loot);
  questlineQuestCount++;
}

console.log(`Modernized ${modernizedCount} existing repeatable quests.`);
console.log(`Modernized ${legacyCount} remaining legacy quests.`);
console.log(`Generated ${repeatableCount} new repeatable quests.`);
console.log(`Generated ${questlineQuestCount} quests across 5 branching questlines.`);

async function readExistingRepeatable(file, overrides) {
  const source = JSON.parse(await readFile(file, "utf8"));
  const v2 = source.schema === "villagerretaliation:quest/v2";
  const metadata = v2 ? source.metadata ?? {} : source.display ?? {};
  const sourceAvailability = v2 ? source.availability ?? {} : source.rules ?? {};
  const objectives = overrides.objectives ?? (v2
    ? (source.stages ?? []).flatMap((stage) => stage.objectives ?? [])
    : source.objectives ?? []);
  const questline = metadata.questline ?? path.basename(path.dirname(file));
  const cooldown = sourceAvailability.completion_cooldown_days
    ?? sourceAvailability.cooldown_days
    ?? 1;

  return {
    id: source.id.replace(/^villagerretaliation:/, ""),
    title: metadata.title,
    description: metadata.description,
    questline,
    tags: metadata.tags ?? source.tags ?? [`group.${questline}`],
    provider: v2
      ? { type: source.provider?.type ?? "villagerretaliation:villager", filters: source.provider?.filters ?? {} }
      : { type: "villagerretaliation:villager", filters: source.offer ?? {} },
    target: overrides.target ?? source.target,
    objectives,
    trackerText: overrides.trackerText ?? sentence(overrides.profile.ask),
    cooldown,
    abandonment: sourceAvailability.abandonment ?? "allow_repickup",
    abandonmentCooldownDays: sourceAvailability.abandonment_cooldown_days,
    abandonmentCooldownSeconds: sourceAvailability.abandonment_cooldown_seconds,
    crossVillager: sourceAvailability.cross_villager_compatible ?? false,
    repeatable: true,
    rewards: overrides.rewards ?? source.rewards,
    icon: overrides.icon,
    profile: overrides.profile
  };
}

async function readExistingLegacyQuest(file, overrides) {
  const source = JSON.parse(await readFile(file, "utf8"));
  const v2 = source.schema === "villagerretaliation:quest/v2";
  const metadata = v2 ? source.metadata ?? {} : source.display ?? {};
  const sourceAvailability = v2 ? source.availability ?? {} : source.rules ?? {};
  const questline = metadata.questline ?? path.basename(path.dirname(file));
  const sourceEvents = v2
    ? source.events
    : source.triggers
      ? (Array.isArray(source.triggers) ? source.triggers : [source.triggers])
      : undefined;

  return {
    id: source.id.replace(/^villagerretaliation:/, ""),
    title: metadata.title,
    description: metadata.description,
    questline,
    parent: overrides.parent ?? (v2 ? metadata.parent : source.parent),
    tags: metadata.tags ?? source.tags ?? [`group.${questline}`],
    provider: v2
      ? { type: source.provider?.type ?? "villagerretaliation:villager", filters: source.provider?.filters ?? {} }
      : {
          type: "villagerretaliation:villager",
          filters: clean({
            professions: source.offer?.professions,
            min_villager_level: source.offer?.min_villager_level,
            skills: source.offer?.skills
          })
        },
    conditions: v2 ? sourceAvailability.conditions : source.offer?.conditions,
    target: overrides.target ?? source.target,
    objectives: overrides.objectives ?? (v2
      ? (source.stages ?? []).flatMap((stage) => stage.objectives ?? [])
      : source.objectives ?? []),
    trackerText: overrides.trackerText ?? sentence(overrides.profile.ask),
    abandonment: sourceAvailability.abandonment ?? "allow_repickup",
    abandonmentCooldownDays: sourceAvailability.abandonment_cooldown_days,
    abandonmentCooldownSeconds: sourceAvailability.abandonment_cooldown_seconds,
    completionScope: sourceAvailability.completion_scope,
    crossVillager: sourceAvailability.cross_villager_compatible ?? false,
    lockedToVillager: sourceAvailability.locked_to_villager ?? true,
    maxStarts: sourceAvailability.max_starts,
    maxCompletions: sourceAvailability.max_completions,
    repeatable: false,
    events: overrides.events ?? sourceEvents,
    rewards: overrides.rewards ?? source.rewards,
    icon: overrides.icon,
    profile: overrides.profile
  };
}

function makeWorkQuest(config) {
  const repeatable = config.repeatable === true;
  const objectives = config.objectives.map(withDefaultTracker);
  const availability = {
    repeatable,
    max_starts: config.maxStarts ?? (repeatable ? 0 : 1),
    max_completions: config.maxCompletions ?? (repeatable ? 0 : 1),
    completion_cooldown_days: repeatable ? config.cooldown ?? 1 : undefined,
    completion_scope: config.completionScope,
    abandonment: config.abandonment ?? "allow_repickup",
    abandonment_cooldown_days: config.abandonmentCooldownDays,
    abandonment_cooldown_seconds: config.abandonmentCooldownSeconds,
    consume_on_completion: true,
    locked_to_villager: config.lockedToVillager ?? true,
    cross_villager_compatible: config.crossVillager ?? false,
    conditions: config.conditions
  };
  const metadata = {
    title: config.title,
    description: config.description,
    questline: config.questline,
    parent: config.parent,
    tags: config.tags ?? [`group.${config.questline}`]
  };
  const profile = config.profile;
  const trackerText = sentence(config.trackerText ?? profile.ask);
  const unavailable = [
    `${config.title} is not open right now.`,
    "The village is not ready to put that work in your hands yet."
  ];

  return clean({
    schema: "villagerretaliation:quest/v2",
    id: `villagerretaliation:${config.id}`,
    metadata,
    provider: config.provider,
    availability,
    target: config.target,
    events: config.events,
    entry_stage: "work",
    stages: [
      {
        id: "work",
        objectives,
        complete_when: objectives.map((objective) => objective.id),
        next: "return",
        dialogue: {
          offer: {
            label: config.title,
            request: "question",
            order: -20,
            show_for_babies: false,
            lines: profile.offer,
            responses: [
              { id: "accept", label: "I can do that.", scene: "start_quest" },
              { id: "decline", label: "Not right now.", scene: "decline" }
            ]
          },
          reminder: {
            label: `About ${config.title}`,
            request: "question",
            order: -20,
            show_for_babies: false,
            lines: profile.reminder,
            responses: [
              { id: "details", label: "Repeat the work.", scene: "reminder_details", order: 0 },
              { id: "abandon", label: "Put this aside.", scene: "abandon_confirm", order: 90 },
              { id: "leave", label: "I have it.", scene: "leave", order: 100 }
            ]
          }
        },
        scenes: [
          {
            id: "start_quest",
            actions: [
              {
                type: "quest",
                action: "start",
                lines: {
                  started: profile.started,
                  already_completed: [
                    `${config.title} is already settled for now.`,
                    "That work has already been counted."
                  ],
                  locate_failed: [
                    "I cannot find a trustworthy mark from here. Ask again when the road is clearer.",
                    "The map will not settle on a safe destination today."
                  ],
                  unavailable
                }
              }
            ]
          },
          {
            id: "reminder_details",
            actions: [
              {
                type: "quest",
                action: "remind",
                lines: { reminder: profile.reminder, unavailable }
              }
            ]
          },
          { id: "decline", lines: profile.decline },
          { id: "leave", lines: profile.leave },
          {
            id: "abandon_confirm",
            lines: [
              `Put ${config.title} aside?`,
              "I can take your name off the work if you are sure."
            ],
            responses: [
              { id: "confirm", label: "Put it aside.", scene: "abandon_quest" },
              { id: "cancel", label: "Keep it open.", scene: "leave" }
            ]
          },
          {
            id: "abandon_quest",
            actions: [
              {
                type: "quest",
                action: "abandon",
                lines: {
                  abandoned: [
                    "All right. I will clear your name from the work.",
                    "The note is put away. You can ask again later."
                  ],
                  abandoned_cooldown: [
                    "I will put the note away for a while before posting it again.",
                    "Let the work rest before you ask for it again."
                  ],
                  unavailable
                }
              }
            ]
          }
        ],
        ui: { tracker_text: trackerText, show_progress: true, progress: 0.75 }
      },
      {
        id: "return",
        objectives: [],
        dialogue: {
          turn_in: {
            label: `About ${config.title}`,
            request: "question",
            order: -20,
            show_for_babies: false,
            lines: profile.turnIn,
            responses: [
              { id: "complete", label: "Hand over the work.", scene: "complete_quest" },
              { id: "leave", label: "Not yet.", scene: "turn_in_wait" }
            ]
          }
        },
        scenes: [
          {
            id: "complete_quest",
            actions: [
              {
                type: "quest",
                action: "turn_in",
                lines: {
                  completed: profile.completed,
                  missing_objectives: [
                    "Something is still missing. Check the journal before I close the note.",
                    "The full work is not ready to count yet."
                  ],
                  missing_target: [
                    "The supplies need a true destination behind them.",
                    "Reach the marked place before we call this finished."
                  ],
                  missing_proof: [
                    "Bring the proof with you before we settle this.",
                    "I need the full hand-in here, not only the story."
                  ],
                  unavailable
                }
              }
            ]
          },
          { id: "turn_in_wait", lines: profile.leave }
        ],
        ui: { tracker_text: `Return to the quest giver.`, show_progress: true, progress: 1 }
      }
    ],
    rewards: config.rewards,
    ui: { title: config.title, icon: config.icon, color: tierColor(config.tier) }
  });
}

function makeChoiceQuest(config) {
  const choiceValues = config.branches.map((branch) => branch.value);
  const chooseResponses = config.branches.map((branch) => ({
    id: branch.value,
    label: branch.label,
    text: branch.chosen,
    actions: [
      { type: "set_variable", scope: "quest", key: "choice", value: branch.value },
      { type: "notification", trigger: "quest.updated", text: branch.notification }
    ],
    transition: { stage: `${branch.value}_work` }
  }));
  chooseResponses.push({ id: "decline", label: "Not yet.", scene: "decline" });

  const stages = [
    {
      id: "choose",
      objectives: [
        {
          id: "choose_route",
          type: "choice",
          key: "choice",
          values: choiceValues,
          tracker: {
            text: "Choose the route with the quest giver.",
            complete_text: "Route chosen: {objective_choice_value}.",
            show_progress: true,
            progress: 0.2
          }
        }
      ],
      complete_when: ["choose_route"],
      dialogue: {
        offer: {
          label: config.title,
          request: "question",
          order: -22,
          show_for_babies: false,
          lines: config.intro,
          responses: chooseResponses
        },
        reminder: {
          label: `About ${config.title}`,
          request: "question",
          order: -22,
          show_for_babies: false,
          lines: config.intro,
          responses: [
            { id: "details", label: "Repeat the choice.", scene: "reminder_details", order: -10 },
            ...chooseResponses
          ]
        }
      },
      scenes: [
        {
          id: "reminder_details",
          actions: [
            {
              type: "quest",
              action: "remind",
              lines: {
                reminder: config.intro,
                unavailable: ["That choice is not open right now."]
              }
            }
          ]
        },
        { id: "decline", lines: [config.decline] }
      ],
      ui: { tracker_text: "Choose a route.", show_progress: true, progress: 0.2 }
    }
  ];

  for (const branch of config.branches) {
    const objectives = branch.objectives.map(withDefaultTracker);
    stages.push(
      {
        id: `${branch.value}_work`,
        objectives,
        complete_when: objectives.map((objective) => objective.id),
        next: `${branch.value}_return`,
        dialogue: {
          reminder: {
            label: `About ${config.title}`,
            request: "question",
            order: -22,
            show_for_babies: false,
            lines: [branch.reminder, branch.chosen],
            responses: [
              { id: "details", label: "Repeat the route.", scene: "reminder_details" },
              { id: "leave", label: "I have it.", scene: "leave" }
            ]
          }
        },
        scenes: [
          {
            id: "reminder_details",
            actions: [
              {
                type: "quest",
                action: "remind",
                lines: {
                  reminder: [branch.reminder, branch.chosen],
                  unavailable: ["That route is not active right now."]
                }
              }
            ]
          },
          { id: "leave", lines: ["Keep to the route you chose."] }
        ],
        ui: { tracker_text: sentence(branch.reminder), show_progress: true, progress: 0.75 }
      },
      {
        id: `${branch.value}_return`,
        objectives: [],
        dialogue: {
          turn_in: {
            label: `About ${config.title}`,
            request: "question",
            order: -22,
            show_for_babies: false,
            lines: [branch.turnIn, branch.complete],
            responses: [
              { id: "complete", label: "Finish this route.", scene: "complete_route" },
              { id: "leave", label: "Not yet.", scene: "leave" }
            ]
          }
        },
        scenes: [
          {
            id: "complete_route",
            actions: [
              {
                type: "quest",
                action: "turn_in",
                lines: {
                  completed: [branch.complete, branch.turnIn],
                  missing_objectives: [branch.reminder],
                  missing_proof: ["Bring the route proof here before we close the map."],
                  unavailable: ["That route cannot be closed right now."]
                }
              }
            ]
          },
          { id: "leave", lines: ["The route stays open until you are ready."] }
        ],
        ui: { tracker_text: "Return to the quest giver.", show_progress: true, progress: 1 }
      }
    );
  }

  return clean({
    schema: "villagerretaliation:quest/v2",
    id: `villagerretaliation:${config.id}`,
    metadata: {
      title: config.title,
      description: config.description,
      questline: config.questline,
      parent: config.parent,
      tags: [`group.${config.questline}`, "branching"]
    },
    provider: config.provider,
    availability: {
      repeatable: false,
      max_starts: 1,
      max_completions: 1,
      abandonment: "allow_repickup",
      consume_on_completion: true,
      locked_to_villager: true,
      cross_villager_compatible: false
    },
    entry_stage: "choose",
    stages,
    rewards: config.rewards,
    ui: { title: config.title, icon: config.icon, color: "#B884E8" }
  });
}

function voice(profile) {
  const ask = sentence(profile.ask);
  return {
    ask: profile.ask,
    offer: [
      profile.offer,
      profile.reason,
      `${ask} I will make the reward worth the trouble.`
    ],
    started: [
      profile.accepted,
      ask,
      `Good. I will keep a place clear for what you bring back.`
    ],
    reminder: [
      profile.reminder ?? ask,
      ask,
      profile.reason
    ],
    turnIn: [
      profile.success,
      profile.successAlt,
      "Let me see the full work before I close the note."
    ],
    completed: [
      profile.success,
      profile.successAlt,
      "That settles it. The village will make good use of this."
    ],
    decline: [
      profile.decline,
      "Another pair of hands may take it, or you may ask again later."
    ],
    leave: [
      profile.reminder ?? ask,
      "Come back when the work is ready to count."
    ]
  };
}

function supplyVoice(icon, ask, reason, success) {
  return {
    icon,
    profile: voice({
      offer: reason,
      reason: `${ask}, and the shelves will look less worried.`,
      ask,
      accepted: `${ask}. Keep the full count together on the road back.`,
      reminder: ask,
      success,
      successAlt: "The count is right, and none of it will sit idle for long.",
      decline: "All right. The empty shelf will keep asking in its own way."
    })
  };
}

function provider(professions, minVillagerLevel, skills) {
  return {
    type: "villagerretaliation:villager",
    filters: {
      professions,
      min_villager_level: minVillagerLevel,
      skills: Object.fromEntries(Object.entries(skills).map(([key, min]) => [key, { min }]))
    }
  };
}

function item(id, itemId, count, text, completeText, consume = true) {
  return clean({
    id,
    type: "item_check",
    item: itemId,
    count,
    consume: consume ? undefined : false,
    tracker: tracker(text, completeText)
  });
}

function kill(id, entities, count, text, completeText) {
  return {
    id,
    type: "mob_kill",
    ...(entities.length === 1 ? { entity: entities[0] } : { entities }),
    count,
    tracker: tracker(text, completeText)
  };
}

function blockEvent(id, type, blocks, count, text, completeText) {
  return {
    id,
    type,
    ...(blocks.length === 1 ? { block: blocks[0] } : { blocks }),
    count,
    tracker: tracker(text, completeText)
  };
}

function counter(id, type, count, text, completeText) {
  return { id, type, count, tracker: tracker(text, completeText) };
}

function structure(id, structureId, searchRadius, discoveryRadius, text, completeText, dimension) {
  return clean({
    id,
    type: "structure_visit",
    structure: structureId,
    dimension,
    search_radius: searchRadius,
    discovery_radius: discoveryRadius,
    tracker: tracker(text, completeText)
  });
}

function target(structureId, dimension, searchRadius, discoveryRadius) {
  return {
    structure: structureId,
    dimension,
    search_radius: searchRadius,
    discovery_radius: discoveryRadius
  };
}

function tracker(text, completeText) {
  return { text, complete_text: completeText, show_progress: true, progress: 0.75 };
}

function withDefaultTracker(objective) {
  if (objective.tracker) {
    return objective;
  }
  return {
    ...objective,
    tracker: tracker(`Complete ${objective.id.replaceAll("_", " ")}.`, `${objective.id.replaceAll("_", " ")} complete.`)
  };
}

function reward(experience, reputation, gossipReputation, lootId) {
  return {
    experience,
    reputation,
    gossip_reputation: gossipReputation,
    loot_table: `villagerretaliation:quest/${lootId}`,
    memory_event: "player_completed_quest"
  };
}

function loot(emeraldMin, emeraldMax, bonuses) {
  const pools = [
    {
      rolls: 1,
      bonus_rolls: 0,
      entries: [lootEntry("minecraft:emerald", emeraldMin, emeraldMax)]
    }
  ];
  if (bonuses.length > 0) {
    pools.push({
      rolls: 1,
      bonus_rolls: 0,
      entries: bonuses.map(({ item: itemId, min, max, weight }) => lootEntry(itemId, min, max, weight))
    });
  }
  return { type: "minecraft:generic", pools };
}

function lootWithGuaranteed(emeraldMin, emeraldMax, guaranteed, bonuses) {
  const table = loot(emeraldMin, emeraldMax, bonuses);
  table.pools.splice(1, 0, {
    rolls: 1,
    bonus_rolls: 0,
    entries: [lootEntry(guaranteed.item, guaranteed.min, guaranteed.max)]
  });
  return table;
}

function bonus(itemId, min, max, weight) {
  return { item: itemId, min, max, weight };
}

function lootEntry(itemId, min, max, weight) {
  return clean({
    type: "minecraft:item",
    name: itemId,
    weight,
    functions: min === 1 && max === 1
      ? undefined
      : [
          {
            function: "minecraft:set_count",
            count: min === max ? min : { type: "minecraft:uniform", min, max }
          }
        ]
  });
}

function branchCondition(questId, value) {
  return [
    {
      type: "quest_fact",
      scope: "quest",
      quest: `villagerretaliation:${questId}`,
      key: "choice",
      value
    }
  ];
}

function tierColor(tier) {
  return ({ early: "#74A85C", mid: "#D19A4A", late: "#8E67C7" })[tier] ?? "#8BA6B8";
}

function sentence(value) {
  if (!value) {
    return "Complete the work.";
  }
  return /[.!?]$/.test(value) ? value : `${value}.`;
}

function clean(value) {
  if (Array.isArray(value)) {
    return value.map(clean);
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value)
        .filter(([, child]) => child !== undefined)
        .map(([key, child]) => [key, clean(child)])
    );
  }
  return value;
}

async function writeJson(file, value) {
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}
