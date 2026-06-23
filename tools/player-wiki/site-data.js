window.VR_WIKI_DATA = {
  "source": "neoforge/src/main/resources/data/villagerretaliation",
  "reputation": [
    {
      "level": "Royalty",
      "threshold": "1000+",
      "effect": "The highest trust tier. Villagers are extremely forgiving and dialogue stays warm longest."
    },
    {
      "level": "Revered",
      "threshold": "400+",
      "effect": "Unlocks stronger trust behavior, trusted keepsakes, and high-reputation reward moments."
    },
    {
      "level": "Respected",
      "threshold": "250+",
      "effect": "Needed by default for Special Orders and several high-skill trade requests."
    },
    {
      "level": "Trusted",
      "threshold": "75+",
      "effect": "Villagers become warmer, more helpful, and may treat gifts as keepsakes."
    },
    {
      "level": "Neutral",
      "threshold": "-74 to 74",
      "effect": "Default relationship. Most systems stay available unless other conditions block them."
    },
    {
      "level": "Suspicious",
      "threshold": "-75 or below",
      "effect": "Villagers become colder and trade pressure can worsen."
    },
    {
      "level": "Hostile",
      "threshold": "-100 or below",
      "effect": "Villagers may refuse interaction and can be pacified if the tier is not too low."
    },
    {
      "level": "Despised",
      "threshold": "-250 or below",
      "effect": "Villagers can become dangerous, may refuse pacification, and may attack on sight when enabled."
    },
    {
      "level": "Feared",
      "threshold": "-750 or below",
      "effect": "The worst tier. Nearby villagers visibly react and systems become least forgiving."
    }
  ],
  "quests": [
    {
      "id": "villagerretaliation:gilded_debt",
      "slug": "gilded_debt",
      "title": "Gilded Debt",
      "description": "Reach a Bastion Remnant and bring back Gilded Blackstone for a risky settlement.",
      "questline": "",
      "questlineLabel": "",
      "group": "dangerous_commissions",
      "groupLabel": "Dangerous Commissions",
      "tags": [
        "group.dangerous_commissions"
      ],
      "relationKey": "group:dangerous_commissions",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Master",
        "professions": [
          "Armorer",
          "Weaponsmith"
        ],
        "skills": [
          {
            "skill": "Survival",
            "min": 65,
            "max": null
          },
          {
            "skill": "Trading",
            "min": 45,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Bastion Remnant",
        "proofItem": "Gilded Blackstone",
        "searchRadius": 256,
        "discoveryRadius": 160
      },
      "objectives": [
        "Proof: Gilded Blackstone",
        "1 Gold Block"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Find the Bastion Remnant near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring Gilded Blackstone and 1 gold block.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "bring_gold_block",
          "label": "Bring Gold Block",
          "text": "Add 1 gold block to settle the old debt.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the gilded proof.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 450,
        "reputation": 15,
        "gossipReputation": -2,
        "lootTable": "villagerretaliation:quest/gilded_debt",
        "loot": [
          {
            "item": "Emerald",
            "count": "34-48",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Gold Ingot",
            "count": "24-40",
            "weight": 3,
            "note": ""
          },
          {
            "item": "Diamond",
            "count": "3-6",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "14-24",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "Abandoning closes it forever"
      ],
      "dialogue": {
        "offer": [
          "An old debt has a gold edge and a bad smell. I need it settled once, cleanly.",
          "There is a bastion tied to a ledger I wish did not exist. Bring proof and payment, or leave this alone."
        ],
        "accept": "I will settle it",
        "decline": "Another time",
        "started": [
          "The bastion is {direction}, about {distance} blocks away, near {target_x}, {target_z}. Bring Gilded Blackstone and one gold block. If you abandon this, I close the matter for good."
        ],
        "reminder": [
          "Bastion near {target_x}, {target_z}. Gilded Blackstone for proof, one gold block for settlement. No second ledger."
        ],
        "completed": [
          "Settled. The village gains relief, and a few whispers it will deserve. Some rewards are complicated."
        ],
        "missing": [
          "Do not hand me gold and call it courage. Reach the bastion first.",
          "Gilded Blackstone is the proof. Without it, this is only an expensive apology.",
          "The settlement still needs one gold block."
        ]
      },
      "questlineOrder": 0
    },
    {
      "id": "villagerretaliation:nether_wart_warranty",
      "slug": "nether_wart_warranty",
      "title": "Nether Wart Warranty",
      "description": "Reach a Nether Fortress and return with Nether Wart and a Blaze Rod.",
      "questline": "",
      "questlineLabel": "",
      "group": "dangerous_commissions",
      "groupLabel": "Dangerous Commissions",
      "tags": [
        "group.dangerous_commissions"
      ],
      "relationKey": "group:dangerous_commissions",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Expert",
        "professions": [
          "Cleric"
        ],
        "skills": [
          {
            "skill": "Medicine",
            "min": 55,
            "max": null
          },
          {
            "skill": "Survival",
            "min": 35,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Fortress",
        "proofItem": "Nether Wart",
        "searchRadius": 256,
        "discoveryRadius": 160
      },
      "objectives": [
        "Proof: Nether Wart",
        "1 Blaze Rod"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Find the fortress near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Collect nether wart and a blaze rod.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "bring_blaze_rod",
          "label": "Bring Blaze Rod",
          "text": "Bring 1 blaze rod with the nether wart.",
          "progress": 0.82,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the Nether proof.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 340,
        "reputation": 20,
        "gossipReputation": 10,
        "lootTable": "villagerretaliation:quest/nether_wart_warranty",
        "loot": [
          {
            "item": "Emerald",
            "count": "24-34",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Blaze Powder",
            "count": "10-18",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Glistering Melon Slice",
            "count": "10-18",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "12-20",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Magma Cream",
            "count": "4-8",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "2 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "My brewing notes have a hole shaped exactly like Nether Wart. I hate how often that happens.",
          "There is medicine I cannot make without an ugly trip. A fortress should have what we need."
        ],
        "accept": "I will go to the Nether",
        "decline": "Another time",
        "started": [
          "The fortress mark sits {direction}, about {distance} blocks away, near {target_x}, {target_z}. Bring Nether Wart and one Blaze Rod."
        ],
        "reminder": [
          "Fortress near {target_x}, {target_z}. Nether Wart is the cure; a Blaze Rod proves you reached the heat of it."
        ],
        "completed": [
          "That will brew into more than medicine. It will brew into confidence. Take your reward."
        ],
        "missing": [
          "Nether Wart without the fortress route is a guess. I need the place confirmed.",
          "Bring Nether Wart. The whole request is written around it.",
          "The notes also require one Blaze Rod."
        ]
      },
      "questlineOrder": 1
    },
    {
      "id": "villagerretaliation:trial_chamber_recall",
      "slug": "trial_chamber_recall",
      "title": "Trial Chamber Recall",
      "description": "Survey a Trial Chamber and return with a Trial Key and Breeze Rod.",
      "questline": "",
      "questlineLabel": "",
      "group": "dangerous_commissions",
      "groupLabel": "Dangerous Commissions",
      "tags": [
        "group.dangerous_commissions"
      ],
      "relationKey": "group:dangerous_commissions",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Expert",
        "professions": [
          "Armorer",
          "Toolsmith"
        ],
        "skills": [
          {
            "skill": "Guarding",
            "min": 55,
            "max": null
          },
          {
            "skill": "Smithing",
            "min": 45,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Trial Chambers",
        "proofItem": "Trial Key",
        "searchRadius": 256,
        "discoveryRadius": 160
      },
      "objectives": [
        "Proof: Trial Key",
        "1 Breeze Rod"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Enter the Trial Chamber near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring back a Trial Key and Breeze Rod.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "bring_breeze_rod",
          "label": "Bring Breeze Rod",
          "text": "Recover 1 Breeze Rod from the chamber.",
          "progress": 0.84,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the Trial Chamber proof.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 380,
        "reputation": 21,
        "gossipReputation": 11,
        "lootTable": "villagerretaliation:quest/trial_chamber_recall",
        "loot": [
          {
            "item": "Emerald",
            "count": "28-40",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Iron Ingot",
            "count": "20-36",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Diamond",
            "count": "2-4",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "14-24",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Wind Charge",
            "count": "8-16",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Locked to the quest giver",
        "Turn-in items are not consumed on completion",
        "7 day completion cooldown",
        "1 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "There is a chamber underground that tests people without asking permission. I want a report before it tests us.",
          "A Trial Chamber has been marked nearby. Bring proof, and we decide whether to avoid it or learn from it."
        ],
        "accept": "I will test the chamber",
        "decline": "Another time",
        "started": [
          "The chamber is {direction}, roughly {distance} blocks from here, near {target_x}, {target_z}. Bring a Trial Key and one Breeze Rod."
        ],
        "reminder": [
          "Trial Chamber near {target_x}, {target_z}. Key, Breeze Rod, return alive enough to explain both."
        ],
        "completed": [
          "A key and a Breeze Rod. Good. That chamber can stay mysterious, but not unmeasured."
        ],
        "missing": [
          "The items matter after you set foot in the chamber itself.",
          "Bring a Trial Key. It is the chamber admitting you were there.",
          "I also need one Breeze Rod for the recall."
        ]
      },
      "questlineOrder": 2
    },
    {
      "id": "villagerretaliation:house_of_ill_omens",
      "slug": "house_of_ill_omens",
      "title": "House of Ill Omens",
      "description": "Enter a Woodland Mansion, defeat an evoker, and return with a Totem of Undying.",
      "questline": "",
      "questlineLabel": "",
      "group": "dangerous_commissions",
      "groupLabel": "Dangerous Commissions",
      "tags": [
        "group.dangerous_commissions"
      ],
      "relationKey": "group:dangerous_commissions",
      "parent": "villagerretaliation:trial_chamber_recall",
      "parentSlug": "trial_chamber_recall",
      "prerequisites": [
        {
          "id": "villagerretaliation:trial_chamber_recall",
          "slug": "trial_chamber_recall"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Master",
        "professions": [
          "Leatherworker",
          "Cleric"
        ],
        "skills": [
          {
            "skill": "Guarding",
            "min": 70,
            "max": null
          },
          {
            "skill": "Survival",
            "min": 55,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Woodland Mansion",
        "proofItem": "Totem Of Undying",
        "searchRadius": 384,
        "discoveryRadius": 192
      },
      "objectives": [
        "Proof: Totem Of Undying",
        "Defeat 1 Evoker",
        "12 Emerald"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Enter the Woodland Mansion near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring a Totem of Undying after entering the mansion.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "defeat_evoker",
          "label": "Defeat Evoker",
          "text": "Defeat the mansion's evoker.",
          "progress": 0.72,
          "hint": ""
        },
        {
          "id": "bring_emeralds",
          "label": "Bring Emeralds",
          "text": "Set aside 12 emeralds for informants after the evoker falls.",
          "progress": 0.86,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the mansion proof.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 520,
        "reputation": 28,
        "gossipReputation": 15,
        "lootTable": "villagerretaliation:quest/house_of_ill_omens",
        "loot": [
          {
            "item": "Emerald",
            "count": "44-62",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Diamond",
            "count": "5-9",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "24-40",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Emerald Block",
            "count": "3-8",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "3 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "There is a house in the woods where bad ideas learned carpentry. I want its power counted, not ignored.",
          "A mansion has been named in too many frightened reports. Bring down its evoker, bring a Totem of Undying, and pay the people who pointed us there."
        ],
        "accept": "I will enter the mansion",
        "decline": "Another time",
        "started": [
          "The mansion lies {direction}, around {distance} blocks away, near {target_x}, {target_z}. Defeat its evoker, bring a Totem of Undying, and set aside 12 emeralds for informants."
        ],
        "reminder": [
          "Woodland Mansion near {target_x}, {target_z}. Bring down the evoker, bring the Totem, then 12 emeralds for the informants."
        ],
        "completed": [
          "A Totem of Undying. That is not proof of safety, but it is proof the village has a shield with a name."
        ],
        "missing": [
          "The totem needs the mansion behind it. I need the place confirmed.",
          "Bring the Totem. Nothing else carries enough of that house on it.",
          "The evoker must fall, and the informants still need 12 emeralds."
        ]
      },
      "questlineOrder": 3
    },
    {
      "id": "villagerretaliation:blank_map_promise",
      "slug": "blank_map_promise",
      "title": "Blank Map Promise",
      "description": "Bind the first blank atlas folio with paper, ink, and a compass bearing.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 6,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "24 Paper",
        "1 Compass",
        "3 Ink Sac"
      ],
      "steps": [
        {
          "id": "gather_paper",
          "label": "Gather Paper",
          "text": "Bring 24 paper for the first atlas folio.",
          "progress": 0.45,
          "hint": ""
        },
        {
          "id": "bring_paper",
          "label": "Bring Paper",
          "text": "Bring 24 paper for the first atlas folio.",
          "progress": 0.45,
          "hint": ""
        },
        {
          "id": "bind_bearing",
          "label": "Bind Bearing",
          "text": "Bring 1 compass and 3 ink sacs for the folio binding.",
          "progress": 0.8,
          "hint": ""
        },
        {
          "id": "bring_compass",
          "label": "Bring Compass",
          "text": "Bring 1 compass to give the atlas a north.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "bring_ink",
          "label": "Bring Ink",
          "text": "Bring 3 ink sacs to bind the first folio.",
          "progress": 0.8,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with the folio materials.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 90,
        "reputation": 7,
        "gossipReputation": 3,
        "lootTable": "villagerretaliation:quest/blank_map_promise",
        "loot": [
          {
            "item": "Emerald",
            "count": "4-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Map",
            "count": "1",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Paper",
            "count": "6-12",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion"
      ],
      "dialogue": {
        "offer": [
          "Every atlas begins as a promise: enough paper to hold the world, enough ink to admit where we do not know it yet.",
          "Bring me paper, a compass, and ink sacs. I will bind the first folio, and you can decide whether the blank space becomes a road."
        ],
        "accept": "Start the atlas",
        "decline": "Another time",
        "started": [
          "Good. Start with 24 paper; a thin atlas lies before the first rain."
        ],
        "reminder": [
          "First bring 24 paper. After that, bind the bearing with 1 compass and 3 ink sacs.",
          "The folio still needs 1 compass and 3 ink sacs."
        ],
        "completed": [
          "There. The first folio is bound. The atlas is still mostly empty, but now it knows how to begin."
        ],
        "missing": [
          "The binding is still short. Check the tracker before I stitch the cover.",
          "Bring the materials here, and I can bind them properly."
        ],
        "stages": [
          {
            "stageId": "gather_paper",
            "label": "Gather Paper",
            "trackerText": "Bring 24 paper for the first atlas folio.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Blank Map Promise",
                "lines": [
                  "Every atlas begins as a promise: enough paper to hold the world, enough ink to admit where we do not know it yet.",
                  "Bring me paper, a compass, and ink sacs. I will bind the first folio, and you can decide whether the blank space becomes a road."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Start the atlas",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Blank Map Promise",
                "lines": [
                  "The blank folio still needs its paper before the compass can mean anything."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the list",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will keep the covers flat until you are ready to start again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no atlas folio in your hands right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "First bring 24 paper. After that, bind the bearing with 1 compass and 3 ink sacs."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your blank folio open right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The first folio is already bound."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "Good. Start with 24 paper; a thin atlas lies before the first rain."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The map table needs a little time before it can take another promise."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Fold the blank folio away for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then the map stays blank, which is safer but much less useful."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Blank space keeps its patience."
                ]
              }
            ]
          },
          {
            "stageId": "bind_bearing",
            "label": "Bind Bearing",
            "trackerText": "Bring 1 compass and 3 ink sacs for the folio binding.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Blank Map Promise",
                "lines": [
                  "The paper is enough. Now the folio needs a compass and ink so the blank page knows which way to face."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the list",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will keep the covers flat until you are ready to start again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no atlas folio in your hands right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "The folio still needs 1 compass and 3 ink sacs."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your blank folio open right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Fold the blank folio away for now?"
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Blank space keeps its patience."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the folio materials.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Blank Map Promise",
                "lines": [
                  "Paper, compass, ink. That is enough for a beginning."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Bind the first folio",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "There. The first folio is bound. The atlas is still mostly empty, but now it knows how to begin."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The binding is still short. Check the tracker before I stitch the cover."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring the materials here, and I can bind them properly."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The folio still needs its paper, compass, and ink before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Do not crease the paper before it has a road."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "gather_paper",
            "label": "Gather Paper",
            "trackerText": "Bring 24 paper for the first atlas folio.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Blank Map Promise",
                "lines": [
                  "Every atlas begins as a promise: enough paper to hold the world, enough ink to admit where we do not know it yet.",
                  "Bring me paper, a compass, and ink sacs. I will bind the first folio, and you can decide whether the blank space becomes a road."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Start the atlas",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Blank Map Promise",
                "lines": [
                  "The blank folio still needs its paper before the compass can mean anything."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the list",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will keep the covers flat until you are ready to start again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no atlas folio in your hands right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "First bring 24 paper. After that, bind the bearing with 1 compass and 3 ink sacs."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your blank folio open right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The first folio is already bound."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "Good. Start with 24 paper; a thin atlas lies before the first rain."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The map table needs a little time before it can take another promise."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Fold the blank folio away for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then the map stays blank, which is safer but much less useful."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Blank space keeps its patience."
                ]
              }
            ]
          },
          {
            "stageId": "bind_bearing",
            "label": "Bind Bearing",
            "trackerText": "Bring 1 compass and 3 ink sacs for the folio binding.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Blank Map Promise",
                "lines": [
                  "The paper is enough. Now the folio needs a compass and ink so the blank page knows which way to face."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the list",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will keep the covers flat until you are ready to start again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no atlas folio in your hands right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "The folio still needs 1 compass and 3 ink sacs."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your blank folio open right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Fold the blank folio away for now?"
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Blank space keeps its patience."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the folio materials.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Blank Map Promise",
                "lines": [
                  "Paper, compass, ink. That is enough for a beginning."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Bind the first folio",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "There. The first folio is bound. The atlas is still mostly empty, but now it knows how to begin."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The binding is still short. Check the tracker before I stitch the cover."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring the materials here, and I can bind them properly."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The folio still needs its paper, compass, and ink before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Do not crease the paper before it has a road."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 0
    },
    {
      "id": "villagerretaliation:ink_and_bearings",
      "slug": "ink_and_bearings",
      "title": "Ink and Bearings",
      "description": "Prepare the atlas index and choose whether its first principle favors roads or wonders.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:blank_map_promise",
      "parentSlug": "blank_map_promise",
      "prerequisites": [
        {
          "id": "villagerretaliation:blank_map_promise",
          "slug": "blank_map_promise"
        }
      ],
      "branchGroup": "",
      "branchChoices": [
        {
          "id": "roads",
          "label": "Favor reliable roads"
        },
        {
          "id": "wonders",
          "label": "Favor strange wonders"
        }
      ],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 10,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "2 Feather",
        "6 Glass Pane",
        "Choose Principle: Roads or Wonders"
      ],
      "steps": [
        {
          "id": "prepare_index",
          "label": "Prepare Index",
          "text": "Bring 2 feathers and 6 glass panes for the atlas index.",
          "progress": 0.55,
          "hint": ""
        },
        {
          "id": "bring_feathers",
          "label": "Bring Feathers",
          "text": "Bring 2 feathers for fine map pens.",
          "progress": 0.45,
          "hint": ""
        },
        {
          "id": "bring_glass",
          "label": "Bring Glass",
          "text": "Bring 6 glass panes to protect the index pages.",
          "progress": 0.55,
          "hint": ""
        },
        {
          "id": "choose_principle",
          "label": "Choose Principle",
          "text": "Choose the atlas principle from the cartographer's branch options.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "choose_principle",
          "label": "Choose Principle",
          "text": "Choose the atlas principle with the cartographer.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "roads_ready",
          "label": "Roads Ready",
          "text": "Return to the cartographer to set the road principle.",
          "progress": 1,
          "hint": ""
        },
        {
          "id": "wonders_ready",
          "label": "Wonders Ready",
          "text": "Return to the cartographer to set the wonder principle.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 115,
        "reputation": 8,
        "gossipReputation": 3,
        "lootTable": "villagerretaliation:quest/ink_and_bearings",
        "loot": [
          {
            "item": "Emerald",
            "count": "3-6",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Empty Map",
            "count": "1",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion"
      ],
      "dialogue": {
        "offer": [
          "A blank atlas can become a diary, a warning, or a trophy shelf. We should choose better than that.",
          "Bring fine pens and glass for the index. Then choose the atlas principle: reliable roads, or strange wonders."
        ],
        "accept": "Prepare the index",
        "decline": "Another time",
        "started": [
          "Bring 2 feathers and 6 glass panes. Good maps need clear ink and clearer intentions."
        ],
        "reminder": [
          "Bring 2 feathers and 6 glass panes. Once the index is ready, choose whether the atlas favors roads or wonders.",
          "Choose roads if the atlas should favor safe returns, or wonders if it should favor rare discoveries."
        ],
        "completed": [
          "I have inked roads into the index. The atlas will look for ways back before it looks for glory.",
          "I have inked wonders into the index. The atlas will not mistake a hard road for an empty one."
        ],
        "missing": [
          "Choose the atlas principle before I set the index.",
          "Choose the atlas principle before I set the index."
        ],
        "stages": [
          {
            "stageId": "prepare_index",
            "label": "Prepare Index",
            "trackerText": "Bring 2 feathers and 6 glass panes for the atlas index.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Ink and Bearings",
                "lines": [
                  "A blank atlas can become a diary, a warning, or a trophy shelf. We should choose better than that.",
                  "Bring fine pens and glass for the index. Then choose the atlas principle: reliable roads, or strange wonders."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Prepare the index",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Ink and Bearings",
                "lines": [
                  "The atlas index still needs fine pens and panes before we choose its first rule."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the list",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will keep the index loose until you return."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no atlas index open right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Bring 2 feathers and 6 glass panes. Once the index is ready, choose whether the atlas favors roads or wonders."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your atlas index open right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The atlas already has its first principle."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "Bring 2 feathers and 6 glass panes. Good maps need clear ink and clearer intentions."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The first folio must be bound before the index matters."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Set aside the atlas index for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then the atlas can wait before it starts having opinions."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "A good bearing survives a pause."
                ]
              }
            ]
          },
          {
            "stageId": "choose_principle",
            "label": "Choose Principle",
            "trackerText": "Choose the atlas principle from the cartographer's branch options.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Ink and Bearings",
                "lines": [
                  "The index is ready. Choose whether the atlas should privilege safe roads or the rare marks people cross oceans to see."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the choice",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "I will choose",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [
              {
                "id": "roads",
                "label": "Favor reliable roads",
                "lines": [
                  "Atlas principle chosen: roads."
                ],
                "targetStageId": "roads_ready",
                "destination": "Next: Roads Ready"
              },
              {
                "id": "wonders",
                "label": "Favor strange wonders",
                "lines": [
                  "Atlas principle chosen: wonders."
                ],
                "targetStageId": "wonders_ready",
                "destination": "Next: Wonders Ready"
              }
            ],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Choose roads if the atlas should favor safe returns, or wonders if it should favor rare discoveries."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "We are not carrying that atlas principle right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Choose what you can live with following."
                ]
              }
            ]
          },
          {
            "stageId": "roads_ready",
            "label": "Roads Ready",
            "trackerText": "Return to the cartographer to set the road principle.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Ink and Bearings",
                "lines": [
                  "Roads, then. A map should first get people home."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Set the road principle",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "I have inked roads into the index. The atlas will look for ways back before it looks for glory."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "Choose the atlas principle before I set the index."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The index still needs the proof we agreed on."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "A good road waits at both ends."
                ]
              }
            ]
          },
          {
            "stageId": "wonders_ready",
            "label": "Wonders Ready",
            "trackerText": "Return to the cartographer to set the wonder principle.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Ink and Bearings",
                "lines": [
                  "Wonders, then. A map should leave room for the impossible."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Set the wonder principle",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "I have inked wonders into the index. The atlas will not mistake a hard road for an empty one."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "Choose the atlas principle before I set the index."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The index still needs the proof we agreed on."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "A wonder only looks impossible until someone writes it down."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "prepare_index",
            "label": "Prepare Index",
            "trackerText": "Bring 2 feathers and 6 glass panes for the atlas index.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Ink and Bearings",
                "lines": [
                  "A blank atlas can become a diary, a warning, or a trophy shelf. We should choose better than that.",
                  "Bring fine pens and glass for the index. Then choose the atlas principle: reliable roads, or strange wonders."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Prepare the index",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Ink and Bearings",
                "lines": [
                  "The atlas index still needs fine pens and panes before we choose its first rule."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the list",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will keep the index loose until you return."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no atlas index open right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Bring 2 feathers and 6 glass panes. Once the index is ready, choose whether the atlas favors roads or wonders."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your atlas index open right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The atlas already has its first principle."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "Bring 2 feathers and 6 glass panes. Good maps need clear ink and clearer intentions."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The first folio must be bound before the index matters."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Set aside the atlas index for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then the atlas can wait before it starts having opinions."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "A good bearing survives a pause."
                ]
              }
            ]
          },
          {
            "stageId": "choose_principle",
            "label": "Choose Principle",
            "trackerText": "Choose the atlas principle from the cartographer's branch options.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Ink and Bearings",
                "lines": [
                  "The index is ready. Choose whether the atlas should privilege safe roads or the rare marks people cross oceans to see."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the choice",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "I will choose",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [
              {
                "id": "roads",
                "label": "Favor reliable roads",
                "lines": [
                  "Atlas principle chosen: roads."
                ],
                "targetStageId": "roads_ready",
                "destination": "Next: Roads Ready"
              },
              {
                "id": "wonders",
                "label": "Favor strange wonders",
                "lines": [
                  "Atlas principle chosen: wonders."
                ],
                "targetStageId": "wonders_ready",
                "destination": "Next: Wonders Ready"
              }
            ],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Choose roads if the atlas should favor safe returns, or wonders if it should favor rare discoveries."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "We are not carrying that atlas principle right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Choose what you can live with following."
                ]
              }
            ]
          }
        ],
        "branches": [
          {
            "stageId": "choose_principle",
            "label": "Choose Principle",
            "choices": [
              {
                "id": "roads",
                "label": "Favor reliable roads",
                "lines": [
                  "Atlas principle chosen: roads."
                ],
                "targetStageId": "roads_ready",
                "destination": "Next: Roads Ready",
                "stageIds": [
                  "roads_ready"
                ],
                "stages": [
                  {
                    "stageId": "roads_ready",
                    "label": "Roads Ready",
                    "trackerText": "Return to the cartographer to set the road principle.",
                    "slots": [
                      {
                        "slot": "turn_in",
                        "title": "Turn-in",
                        "label": "About Ink and Bearings",
                        "lines": [
                          "Roads, then. A map should first get people home."
                        ],
                        "responses": [
                          {
                            "id": "complete",
                            "label": "Set the road principle",
                            "lines": [],
                            "targetStageId": "",
                            "destination": "Scene: Complete Quest"
                          },
                          {
                            "id": "leave",
                            "label": "Not yet",
                            "lines": [],
                            "targetStageId": "",
                            "destination": "Scene: Leave"
                          }
                        ]
                      }
                    ],
                    "choices": [],
                    "actions": [
                      {
                        "sceneId": "complete_quest",
                        "action": "turn_in",
                        "key": "completed",
                        "label": "Turn-in: Completed",
                        "lines": [
                          "I have inked roads into the index. The atlas will look for ways back before it looks for glory."
                        ]
                      },
                      {
                        "sceneId": "complete_quest",
                        "action": "turn_in",
                        "key": "missing_objectives",
                        "label": "Turn-in: Missing objectives",
                        "lines": [
                          "Choose the atlas principle before I set the index."
                        ]
                      },
                      {
                        "sceneId": "complete_quest",
                        "action": "turn_in",
                        "key": "unavailable",
                        "label": "Turn-in: Unavailable",
                        "lines": [
                          "The index still needs the proof we agreed on."
                        ]
                      }
                    ],
                    "scenes": [
                      {
                        "sceneId": "leave",
                        "label": "Scene: Leave",
                        "lines": [
                          "A good road waits at both ends."
                        ]
                      }
                    ]
                  }
                ]
              },
              {
                "id": "wonders",
                "label": "Favor strange wonders",
                "lines": [
                  "Atlas principle chosen: wonders."
                ],
                "targetStageId": "wonders_ready",
                "destination": "Next: Wonders Ready",
                "stageIds": [
                  "wonders_ready"
                ],
                "stages": [
                  {
                    "stageId": "wonders_ready",
                    "label": "Wonders Ready",
                    "trackerText": "Return to the cartographer to set the wonder principle.",
                    "slots": [
                      {
                        "slot": "turn_in",
                        "title": "Turn-in",
                        "label": "About Ink and Bearings",
                        "lines": [
                          "Wonders, then. A map should leave room for the impossible."
                        ],
                        "responses": [
                          {
                            "id": "complete",
                            "label": "Set the wonder principle",
                            "lines": [],
                            "targetStageId": "",
                            "destination": "Scene: Complete Quest"
                          },
                          {
                            "id": "leave",
                            "label": "Not yet",
                            "lines": [],
                            "targetStageId": "",
                            "destination": "Scene: Leave"
                          }
                        ]
                      }
                    ],
                    "choices": [],
                    "actions": [
                      {
                        "sceneId": "complete_quest",
                        "action": "turn_in",
                        "key": "completed",
                        "label": "Turn-in: Completed",
                        "lines": [
                          "I have inked wonders into the index. The atlas will not mistake a hard road for an empty one."
                        ]
                      },
                      {
                        "sceneId": "complete_quest",
                        "action": "turn_in",
                        "key": "missing_objectives",
                        "label": "Turn-in: Missing objectives",
                        "lines": [
                          "Choose the atlas principle before I set the index."
                        ]
                      },
                      {
                        "sceneId": "complete_quest",
                        "action": "turn_in",
                        "key": "unavailable",
                        "label": "Turn-in: Unavailable",
                        "lines": [
                          "The index still needs the proof we agreed on."
                        ]
                      }
                    ],
                    "scenes": [
                      {
                        "sceneId": "leave",
                        "label": "Scene: Leave",
                        "lines": [
                          "A wonder only looks impossible until someone writes it down."
                        ]
                      }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      },
      "questlineOrder": 1
    },
    {
      "id": "villagerretaliation:first_far_marker",
      "slug": "first_far_marker",
      "title": "First Far Marker",
      "description": "Follow the first atlas mark to Trail Ruins and plate the route with copper.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:ink_and_bearings",
      "parentSlug": "ink_and_bearings",
      "prerequisites": [
        {
          "id": "villagerretaliation:ink_and_bearings",
          "slug": "ink_and_bearings"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 14,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Trail Ruins",
        "proofItem": "",
        "searchRadius": 192,
        "discoveryRadius": 96
      },
      "objectives": [
        "Visit Trail Ruins",
        "1 Brush",
        "8 Copper Ingot"
      ],
      "steps": [
        {
          "id": "survey",
          "label": "Survey",
          "text": "Reach the Trail Ruins near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "visit_ruins",
          "label": "Visit Ruins",
          "text": "Reach the Trail Ruins near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "carry_brush",
          "label": "Carry Brush",
          "text": "Carry a brush so the ruins can be read without breaking them.",
          "progress": 0.55,
          "hint": ""
        },
        {
          "id": "bring_copper",
          "label": "Bring Copper",
          "text": "Bring 8 copper ingots for marker plates.",
          "progress": 0.75,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with the field notes and copper.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 150,
        "reputation": 10,
        "gossipReputation": 4,
        "lootTable": "villagerretaliation:quest/first_far_marker",
        "loot": [
          {
            "item": "Emerald",
            "count": "8-14",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Filled Map",
            "count": "1",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "2-5",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion"
      ],
      "dialogue": {
        "offer": [
          "The first folio found an old road under the ink. Trail Ruins, if the table is reading true.",
          "Reach the ruins, carry a brush, and bring copper for marker plates. The atlas must learn that travel is more than a straight line."
        ],
        "accept": "Mark the route",
        "decline": "Another time",
        "started": [
          "The ruins sit about {distance} blocks {direction}, near {target_x}, {target_z}. Reach them, carry a brush, and bring 8 copper ingots back."
        ],
        "reminder": [
          "Trail Ruins near {target_x}, {target_z}, about {distance} blocks {direction}. Reach the ruins, carry a brush, and bring 8 copper ingots.",
          "Trail Ruins near {target_x}, {target_z}, about {distance} blocks {direction}. Reach the ruins, carry a brush, and bring 8 copper ingots."
        ],
        "completed": [
          "The first far marker is inked. Now the atlas has proof that its roads touch real dust."
        ],
        "missing": [
          "Reach the Trail Ruins first. A brush without dust is just a tool.",
          "Carry the brush and bring the copper before I mark the route.",
          "The route still needs the ruins visited, the brush ready, and its copper marker plates."
        ],
        "stages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Trail Ruins near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "First Far Marker",
                "lines": [
                  "The first folio found an old road under the ink. Trail Ruins, if the table is reading true.",
                  "Reach the ruins, carry a brush, and bring copper for marker plates. The atlas must learn that travel is more than a straight line."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Mark the route",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About First Far Marker",
                "lines": [
                  "The first marker is still waiting in the field."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will fold the marker away for now."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no marker route to fold away."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Trail Ruins near {target_x}, {target_z}, about {distance} blocks {direction}. Reach the ruins, carry a brush, and bring 8 copper ingots."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have that marker open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The first far marker is already inked."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The ruins sit about {distance} blocks {direction}, near {target_x}, {target_z}. Reach them, carry a brush, and bring 8 copper ingots back."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The old road will not hold still on the table today."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas needs its paper and bearings before that marker makes sense."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Fold away the first far marker?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Old roads are patient. Usually."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Mind the margins."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the field notes and copper.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About First Far Marker",
                "lines": [
                  "Trail Ruins near {target_x}, {target_z}. Bring the brush and copper so the atlas can stop guessing."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              },
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About First Far Marker",
                "lines": [
                  "You reached the old mark? Then show me what the road left behind."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the field notes",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The first far marker is inked. Now the atlas has proof that its roads touch real dust."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "Reach the Trail Ruins first. A brush without dust is just a tool."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Carry the brush and bring the copper before I mark the route."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The route still needs the ruins visited, the brush ready, and its copper marker plates."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "This marker still needs its field proof before I can close it."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Trail Ruins near {target_x}, {target_z}, about {distance} blocks {direction}. Reach the ruins, carry a brush, and bring 8 copper ingots."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have that marker open for you right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Mind the margins."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Trail Ruins near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "First Far Marker",
                "lines": [
                  "The first folio found an old road under the ink. Trail Ruins, if the table is reading true.",
                  "Reach the ruins, carry a brush, and bring copper for marker plates. The atlas must learn that travel is more than a straight line."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Mark the route",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About First Far Marker",
                "lines": [
                  "The first marker is still waiting in the field."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will fold the marker away for now."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no marker route to fold away."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Trail Ruins near {target_x}, {target_z}, about {distance} blocks {direction}. Reach the ruins, carry a brush, and bring 8 copper ingots."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have that marker open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The first far marker is already inked."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The ruins sit about {distance} blocks {direction}, near {target_x}, {target_z}. Reach them, carry a brush, and bring 8 copper ingots back."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The old road will not hold still on the table today."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas needs its paper and bearings before that marker makes sense."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Fold away the first far marker?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Old roads are patient. Usually."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Mind the margins."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the field notes and copper.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About First Far Marker",
                "lines": [
                  "Trail Ruins near {target_x}, {target_z}. Bring the brush and copper so the atlas can stop guessing."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              },
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About First Far Marker",
                "lines": [
                  "You reached the old mark? Then show me what the road left behind."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the field notes",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The first far marker is inked. Now the atlas has proof that its roads touch real dust."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "Reach the Trail Ruins first. A brush without dust is just a tool."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Carry the brush and bring the copper before I mark the route."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The route still needs the ruins visited, the brush ready, and its copper marker plates."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "This marker still needs its field proof before I can close it."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Trail Ruins near {target_x}, {target_z}, about {distance} blocks {direction}. Reach the ruins, carry a brush, and bring 8 copper ingots."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have that marker open for you right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Mind the margins."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 2
    },
    {
      "id": "villagerretaliation:roads_that_remember",
      "slug": "roads_that_remember",
      "title": "Roads That Remember",
      "description": "Record a village defense memory and turn it into a road the atlas can understand.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:first_far_marker",
      "parentSlug": "first_far_marker",
      "prerequisites": [
        {
          "id": "villagerretaliation:first_far_marker",
          "slug": "first_far_marker"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Journeyman",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 22,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "Record memory: Player Defended Village",
        "1 Book"
      ],
      "steps": [
        {
          "id": "hear_story",
          "label": "Hear Story",
          "text": "Help or witness a village defense memory.",
          "progress": 0.55,
          "hint": ""
        },
        {
          "id": "witness_defense",
          "label": "Witness Defense",
          "text": "Help or witness a village defense so the atlas has a living road to record.",
          "progress": 0.55,
          "hint": ""
        },
        {
          "id": "write_account",
          "label": "Write Account",
          "text": "Bring 1 book to record the village defense memory.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "bring_book",
          "label": "Bring Book",
          "text": "Bring 1 book for the road account.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with the road account.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 185,
        "reputation": 12,
        "gossipReputation": 5,
        "lootTable": "villagerretaliation:quest/roads_that_remember",
        "loot": [
          {
            "item": "Emerald",
            "count": "4-7",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Paper",
            "count": "4-8",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion"
      ],
      "dialogue": {
        "offer": [
          "Maps remember places. Villages remember what happened there. A useful atlas should learn both.",
          "When a village survives danger, bring that memory back with a written account. Roads matter most when people need to return by them."
        ],
        "accept": "Record the road memory",
        "decline": "Another time",
        "started": [
          "Listen for a village that survives danger. When the memory is fresh, bring a book and I will set it down properly."
        ],
        "reminder": [
          "Help or witness a village defense, then bring me a book so I can write the account into the atlas.",
          "Bring 1 book. The defense memory is ready; the account still needs a page."
        ],
        "completed": [
          "Now the atlas has a road that remembers why it mattered. That will change how it judges every future mark."
        ],
        "missing": [
          "The atlas needs both the defense memory and the book before I can write this account.",
          "Bring the book here, and we can keep the memory clean."
        ],
        "stages": [
          {
            "stageId": "hear_story",
            "label": "Hear Story",
            "trackerText": "Help or witness a village defense memory.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Roads That Remember",
                "lines": [
                  "Maps remember places. Villages remember what happened there. A useful atlas should learn both.",
                  "When a village survives danger, bring that memory back with a written account. Roads matter most when people need to return by them."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Record the road memory",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Roads That Remember",
                "lines": [
                  "The atlas still needs a living road: a memory of a village defended, then a written account."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the task",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will leave the page blank until the road has something to say."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no road memory open right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Help or witness a village defense, then bring me a book so I can write the account into the atlas."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have that road memory open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The atlas already has its first living road."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "Listen for a village that survives danger. When the memory is fresh, bring a book and I will set it down properly."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The first field marker must be inked before the atlas can weigh memories."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Leave this memory unwritten for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Fair. Some stories should not be chased while they are still happening."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep your ears open. Roads talk after danger."
                ]
              }
            ]
          },
          {
            "stageId": "write_account",
            "label": "Write Account",
            "trackerText": "Bring 1 book to record the village defense memory.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Roads That Remember",
                "lines": [
                  "The memory is there. Bring a book so the atlas can keep it without bending it into rumor."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the task",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Bring 1 book. The defense memory is ready; the account still needs a page."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have that road memory open for you right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "A remembered road deserves a clean page."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the road account.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Roads That Remember",
                "lines": [
                  "A defended village, a written account, and an atlas that knows the difference between distance and meaning."
                ],
                "responses": [
                  {
                    "id": "record",
                    "label": "Record the road memory",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "Now the atlas has a road that remembers why it mattered. That will change how it judges every future mark."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The atlas needs both the defense memory and the book before I can write this account."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring the book here, and we can keep the memory clean."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "This road memory still needs your account before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "No hurry. Some memories need a quiet hand."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "hear_story",
            "label": "Hear Story",
            "trackerText": "Help or witness a village defense memory.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Roads That Remember",
                "lines": [
                  "Maps remember places. Villages remember what happened there. A useful atlas should learn both.",
                  "When a village survives danger, bring that memory back with a written account. Roads matter most when people need to return by them."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Record the road memory",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Roads That Remember",
                "lines": [
                  "The atlas still needs a living road: a memory of a village defended, then a written account."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the task",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will leave the page blank until the road has something to say."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no road memory open right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Help or witness a village defense, then bring me a book so I can write the account into the atlas."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have that road memory open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The atlas already has its first living road."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "Listen for a village that survives danger. When the memory is fresh, bring a book and I will set it down properly."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The first field marker must be inked before the atlas can weigh memories."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Leave this memory unwritten for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Fair. Some stories should not be chased while they are still happening."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep your ears open. Roads talk after danger."
                ]
              }
            ]
          },
          {
            "stageId": "write_account",
            "label": "Write Account",
            "trackerText": "Bring 1 book to record the village defense memory.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Roads That Remember",
                "lines": [
                  "The memory is there. Bring a book so the atlas can keep it without bending it into rumor."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the task",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Bring 1 book. The defense memory is ready; the account still needs a page."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have that road memory open for you right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "A remembered road deserves a clean page."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the road account.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Roads That Remember",
                "lines": [
                  "A defended village, a written account, and an atlas that knows the difference between distance and meaning."
                ],
                "responses": [
                  {
                    "id": "record",
                    "label": "Record the road memory",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "Now the atlas has a road that remembers why it mattered. That will change how it judges every future mark."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The atlas needs both the defense memory and the book before I can write this account."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring the book here, and we can keep the memory clean."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "This road memory still needs your account before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "No hurry. Some memories need a quiet hand."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 3
    },
    {
      "id": "villagerretaliation:the_atlas_test",
      "slug": "the_atlas_test",
      "title": "The Atlas Test",
      "description": "Choose a safer supply route or a riskier combat route to prove the atlas can guide real decisions.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:roads_that_remember",
      "parentSlug": "roads_that_remember",
      "prerequisites": [
        {
          "id": "villagerretaliation:roads_that_remember",
          "slug": "roads_that_remember"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Expert",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 34,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "Choose Test Route: Safe or Risky",
        "4 Lantern",
        "8 Bread",
        "Defeat 3 Pillager",
        "1 Crossbow"
      ],
      "steps": [
        {
          "id": "choose_test",
          "label": "Choose Test",
          "text": "Choose the safer or riskier atlas test route.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "choose_test_route",
          "label": "Choose Test Route",
          "text": "Choose the atlas test route with the cartographer.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "safe_supplies",
          "label": "Safe Supplies",
          "text": "Bring 4 lanterns and 8 bread for the safer route test.",
          "progress": 0.75,
          "hint": ""
        },
        {
          "id": "bring_lanterns",
          "label": "Bring Lanterns",
          "text": "Bring 4 lanterns to light a repeatable road.",
          "progress": 0.65,
          "hint": ""
        },
        {
          "id": "bring_bread",
          "label": "Bring Bread",
          "text": "Bring 8 bread for the safer road kit.",
          "progress": 0.75,
          "hint": ""
        },
        {
          "id": "risky_patrol",
          "label": "Risky Patrol",
          "text": "Defeat 3 pillagers and carry a crossbow for the riskier route test.",
          "progress": 0.8,
          "hint": ""
        },
        {
          "id": "kill_pillagers",
          "label": "Kill Pillagers",
          "text": "Defeat 3 pillagers to prove the riskier road can be defended.",
          "progress": 0.65,
          "hint": ""
        },
        {
          "id": "bring_crossbow",
          "label": "Bring Crossbow",
          "text": "Carry 1 crossbow as proof of the risky patrol.",
          "progress": 0.8,
          "hint": ""
        },
        {
          "id": "safe_return",
          "label": "Safe Return",
          "text": "Return to the cartographer with the safer road kit.",
          "progress": 1,
          "hint": ""
        },
        {
          "id": "risky_return",
          "label": "Risky Return",
          "text": "Return to the cartographer after the risky patrol.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 240,
        "reputation": 15,
        "gossipReputation": 6,
        "lootTable": "villagerretaliation:quest/the_atlas_test",
        "loot": [
          {
            "item": "Emerald",
            "count": "6-10",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Compass",
            "count": "1",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion"
      ],
      "dialogue": {
        "offer": [
          "The atlas has pages, markers, and memory. Now it needs judgment.",
          "Choose the test: a safer supply road anyone can repeat, or a riskier road that proves the atlas can survive bad company."
        ],
        "accept": "Take the safer road",
        "decline": "Another time",
        "started": [],
        "reminder": [
          "Choose the safer road for lanterns and bread, or the riskier road for a pillager patrol and crossbow proof.",
          "Bring 4 lanterns and 8 bread for the safer route test.",
          "Defeat 3 pillagers and carry a crossbow as proof of the riskier route test."
        ],
        "completed": [
          "The safer test is passed. The atlas now knows that mercy is also a route.",
          "The riskier test is passed. The atlas now knows that danger can be measured without being worshiped."
        ],
        "missing": [
          "The safe road kit is still missing pieces.",
          "The risky patrol still needs proof."
        ],
        "stages": [
          {
            "stageId": "choose_test",
            "label": "Choose Test",
            "trackerText": "Choose the safer or riskier atlas test route.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "The Atlas Test",
                "lines": [
                  "The atlas has pages, markers, and memory. Now it needs judgment.",
                  "Choose the test: a safer supply road anyone can repeat, or a riskier road that proves the atlas can survive bad company."
                ],
                "responses": [
                  {
                    "id": "safe",
                    "label": "Take the safer road",
                    "lines": [
                      "Good. A safer road is not a weaker test; it just asks whether the atlas can keep people fed and lit."
                    ],
                    "targetStageId": "safe_supplies",
                    "destination": "Next: Safe Supplies"
                  },
                  {
                    "id": "risky",
                    "label": "Take the risky road",
                    "lines": [
                      "Risky road it is. Come back with proof the danger was real, not just imagined from a warm room."
                    ],
                    "targetStageId": "risky_patrol",
                    "destination": "Next: Risky Patrol"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Atlas Test",
                "lines": [
                  "Choose whether the atlas should prove a safer road or a riskier one."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the test",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "I will choose",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Choose the safer road for lanterns and bread, or the riskier road for a pillager patrol and crossbow proof."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "We are not carrying that test right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then the atlas keeps studying instead of deciding."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "A test is only useful if you choose it honestly."
                ]
              }
            ]
          },
          {
            "stageId": "safe_supplies",
            "label": "Safe Supplies",
            "trackerText": "Bring 4 lanterns and 8 bread for the safer route test.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Atlas Test",
                "lines": [
                  "A safe road still needs proof. Bring lanterns and bread, the things a traveler notices only when they are missing."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the kit",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Bring 4 lanterns and 8 bread for the safer route test."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "We are not carrying that test right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The safer road still deserves respect."
                ]
              }
            ]
          },
          {
            "stageId": "risky_patrol",
            "label": "Risky Patrol",
            "trackerText": "Defeat 3 pillagers and carry a crossbow for the riskier route test.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Atlas Test",
                "lines": [
                  "The riskier road needs proof that danger was not just guessed at from a safe table."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the patrol",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Defeat 3 pillagers and carry a crossbow as proof of the riskier route test."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "We are not carrying that test right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Risk is not courage until someone returns with the account."
                ]
              }
            ]
          },
          {
            "stageId": "safe_return",
            "label": "Safe Return",
            "trackerText": "Return to the cartographer with the safer road kit.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About The Atlas Test",
                "lines": [
                  "A safe road with light and food. That is not glamorous, which is why it is useful."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Finish the safer test",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Safe"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_safe",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The safer test is passed. The atlas now knows that mercy is also a route."
                ]
              },
              {
                "sceneId": "complete_safe",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The safe road kit is still missing pieces."
                ]
              },
              {
                "sceneId": "complete_safe",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "I still need the safer road kit before we call this passed."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Bring the kit when the road is ready."
                ]
              }
            ]
          },
          {
            "stageId": "risky_return",
            "label": "Risky Return",
            "trackerText": "Return to the cartographer after the risky patrol.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About The Atlas Test",
                "lines": [
                  "You chose the riskier road and came back with proof. That is the part an atlas cannot fake."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Finish the risky test",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Risky"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_risky",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The riskier test is passed. The atlas now knows that danger can be measured without being worshiped."
                ]
              },
              {
                "sceneId": "complete_risky",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The risky patrol still needs proof."
                ]
              },
              {
                "sceneId": "complete_risky",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "I still need the patrol proof before we call this passed."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Bring the proof when the road is ready."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "choose_test",
            "label": "Choose Test",
            "trackerText": "Choose the safer or riskier atlas test route.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "The Atlas Test",
                "lines": [
                  "The atlas has pages, markers, and memory. Now it needs judgment.",
                  "Choose the test: a safer supply road anyone can repeat, or a riskier road that proves the atlas can survive bad company."
                ],
                "responses": [
                  {
                    "id": "safe",
                    "label": "Take the safer road",
                    "lines": [
                      "Good. A safer road is not a weaker test; it just asks whether the atlas can keep people fed and lit."
                    ],
                    "targetStageId": "safe_supplies",
                    "destination": "Next: Safe Supplies"
                  },
                  {
                    "id": "risky",
                    "label": "Take the risky road",
                    "lines": [
                      "Risky road it is. Come back with proof the danger was real, not just imagined from a warm room."
                    ],
                    "targetStageId": "risky_patrol",
                    "destination": "Next: Risky Patrol"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Atlas Test",
                "lines": [
                  "Choose whether the atlas should prove a safer road or a riskier one."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the test",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "I will choose",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Choose the safer road for lanterns and bread, or the riskier road for a pillager patrol and crossbow proof."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "We are not carrying that test right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then the atlas keeps studying instead of deciding."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "A test is only useful if you choose it honestly."
                ]
              }
            ]
          },
          {
            "stageId": "safe_supplies",
            "label": "Safe Supplies",
            "trackerText": "Bring 4 lanterns and 8 bread for the safer route test.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Atlas Test",
                "lines": [
                  "A safe road still needs proof. Bring lanterns and bread, the things a traveler notices only when they are missing."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the kit",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Bring 4 lanterns and 8 bread for the safer route test."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "We are not carrying that test right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The safer road still deserves respect."
                ]
              }
            ]
          },
          {
            "stageId": "risky_patrol",
            "label": "Risky Patrol",
            "trackerText": "Defeat 3 pillagers and carry a crossbow for the riskier route test.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Atlas Test",
                "lines": [
                  "The riskier road needs proof that danger was not just guessed at from a safe table."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the patrol",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Defeat 3 pillagers and carry a crossbow as proof of the riskier route test."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "We are not carrying that test right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Risk is not courage until someone returns with the account."
                ]
              }
            ]
          },
          {
            "stageId": "safe_return",
            "label": "Safe Return",
            "trackerText": "Return to the cartographer with the safer road kit.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About The Atlas Test",
                "lines": [
                  "A safe road with light and food. That is not glamorous, which is why it is useful."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Finish the safer test",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Safe"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_safe",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The safer test is passed. The atlas now knows that mercy is also a route."
                ]
              },
              {
                "sceneId": "complete_safe",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The safe road kit is still missing pieces."
                ]
              },
              {
                "sceneId": "complete_safe",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "I still need the safer road kit before we call this passed."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Bring the kit when the road is ready."
                ]
              }
            ]
          },
          {
            "stageId": "risky_return",
            "label": "Risky Return",
            "trackerText": "Return to the cartographer after the risky patrol.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About The Atlas Test",
                "lines": [
                  "You chose the riskier road and came back with proof. That is the part an atlas cannot fake."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Finish the risky test",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Risky"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_risky",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The riskier test is passed. The atlas now knows that danger can be measured without being worshiped."
                ]
              },
              {
                "sceneId": "complete_risky",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The risky patrol still needs proof."
                ]
              },
              {
                "sceneId": "complete_risky",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "I still need the patrol proof before we call this passed."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Bring the proof when the road is ready."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 4
    },
    {
      "id": "villagerretaliation:choose_the_horizon",
      "slug": "choose_the_horizon",
      "title": "Choose The Horizon",
      "description": "Set the completed atlas toward the drowned coast or the dark roof road.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:the_atlas_test",
      "parentSlug": "the_atlas_test",
      "prerequisites": [
        {
          "id": "villagerretaliation:the_atlas_test",
          "slug": "the_atlas_test"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Master",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 48,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Monument",
        "proofItem": "",
        "searchRadius": 384,
        "discoveryRadius": 160
      },
      "objectives": [
        "Choose Choice: Coast or Dark Roof",
        "Visit Monument",
        "4 Prismarine Crystals",
        "4 Prismarine Shard",
        "Visit Woodland Mansion",
        "6 Book",
        "1 Totem Of Undying"
      ],
      "steps": [
        {
          "id": "started",
          "label": "Started",
          "text": "Choose the final atlas horizon from the cartographer's branch options.",
          "progress": 0.45,
          "hint": ""
        },
        {
          "id": "choose_route",
          "label": "Choose Route",
          "text": "Choose the final atlas horizon with the cartographer.",
          "progress": 0.45,
          "hint": ""
        },
        {
          "id": "coast_final",
          "label": "Coast Final",
          "text": "Reach the Ocean Monument, then bring prismarine crystals and shards.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "visit_monument",
          "label": "Visit Monument",
          "text": "Reach the Ocean Monument near {target_x}, {target_z}.",
          "progress": 0.65,
          "hint": ""
        },
        {
          "id": "bring_prismarine_crystals",
          "label": "Bring Prismarine Crystals",
          "text": "Bring 4 prismarine crystals to bind the drowned coast horizon.",
          "progress": 0.8,
          "hint": ""
        },
        {
          "id": "bring_prismarine_shards",
          "label": "Bring Prismarine Shards",
          "text": "Bring 4 prismarine shards to set the coast margin.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "dark_roof_final",
          "label": "Dark Roof Final",
          "text": "Reach the Woodland Mansion, then bring books and a totem.",
          "progress": 0.9,
          "hint": ""
        },
        {
          "id": "visit_mansion",
          "label": "Visit Mansion",
          "text": "Reach the Woodland Mansion near {target_x}, {target_z}.",
          "progress": 0.65,
          "hint": ""
        },
        {
          "id": "bring_books",
          "label": "Bring Books",
          "text": "Bring 6 books for the dark roof index.",
          "progress": 0.75,
          "hint": ""
        },
        {
          "id": "carry_totem",
          "label": "Carry Totem",
          "text": "Carry a Totem of Undying as proof the dark roof road was not imaginary.",
          "progress": 0.9,
          "hint": ""
        },
        {
          "id": "coast_chosen",
          "label": "Coast Chosen",
          "text": "Return to the cartographer to set the drowned coast horizon.",
          "progress": 1,
          "hint": ""
        },
        {
          "id": "dark_roof_chosen",
          "label": "Dark Roof Chosen",
          "text": "Return to the cartographer to set the dark roof horizon.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 360,
        "reputation": 20,
        "gossipReputation": 9,
        "lootTable": "villagerretaliation:quest/choose_the_horizon",
        "loot": [
          {
            "item": "Emerald",
            "count": "6-10",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Compass",
            "count": "1",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion"
      ],
      "dialogue": {
        "offer": [
          "The atlas has paper, bearings, field proof, memory, and judgment. Now it asks for a horizon.",
          "Choose the drowned coast if you want the atlas to follow water and ruins. Choose the dark roof road if you want it to follow danger under old timber."
        ],
        "accept": "Choose the drowned coast",
        "decline": "Another time",
        "started": [],
        "reminder": [
          "Choose the drowned coast for water, prismarine, and ocean ruins. Choose the dark roof road for books, illagers, and a mansion horizon.",
          "Reach the Ocean Monument near the mark, then bring 4 prismarine crystals and 4 prismarine shards to bind the drowned coast horizon.",
          "Reach the Woodland Mansion near the mark, then bring 6 books and carry a Totem of Undying to bind the dark roof road."
        ],
        "completed": [
          "Done. The atlas has chosen the drowned coast, and the old blank promise has become a horizon.",
          "Done. The atlas has chosen the dark roof road, and the old blank promise has become a horizon."
        ],
        "missing": [
          "The coast binding still needs the monument mark and prismarine proof.",
          "The dark roof binding still needs the mansion mark, the books, and the totem proof."
        ],
        "stages": [
          {
            "stageId": "started",
            "label": "Started",
            "trackerText": "Choose the final atlas horizon from the cartographer's branch options.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Choose The Horizon",
                "lines": [
                  "The atlas has paper, bearings, field proof, memory, and judgment. Now it asks for a horizon.",
                  "Choose the drowned coast if you want the atlas to follow water and ruins. Choose the dark roof road if you want it to follow danger under old timber."
                ],
                "responses": [
                  {
                    "id": "coast",
                    "label": "Choose the drowned coast",
                    "lines": [
                      "Then we choose the coast. Keep your eyes on drowned stone, and do not trust quiet water."
                    ],
                    "targetStageId": "coast_final",
                    "destination": "Next: Coast Final"
                  },
                  {
                    "id": "dark_roof",
                    "label": "Choose the dark roof road",
                    "lines": [
                      "Then we choose the dark roof road. If the house looks back, look back harder."
                    ],
                    "targetStageId": "dark_roof_final",
                    "destination": "Next: Dark Roof Final"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Horizon",
                "lines": [
                  "This is the final fork. Coast or dark roof; water-stone or old timber."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the fork",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "I will choose",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Choose the drowned coast for water, prismarine, and ocean ruins. Choose the dark roof road for books, illagers, and a mansion horizon."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your horizon page open right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then the horizon stays unchosen, which is a kind of answer but not a useful one."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Choose with both eyes open."
                ]
              }
            ]
          },
          {
            "stageId": "coast_final",
            "label": "Coast Final",
            "trackerText": "Reach the Ocean Monument, then bring prismarine crystals and shards.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Horizon",
                "lines": [
                  "The drowned coast needs prismarine proof before I can set it into the final folio."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the proof",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Reach the Ocean Monument near the mark, then bring 4 prismarine crystals and 4 prismarine shards to bind the drowned coast horizon."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your horizon page open right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The tide can wait, but it will not wait forever."
                ]
              }
            ]
          },
          {
            "stageId": "dark_roof_final",
            "label": "Dark Roof Final",
            "trackerText": "Reach the Woodland Mansion, then bring books and a totem.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Horizon",
                "lines": [
                  "The dark roof road needs books and a totem before I can set it into the final folio."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the proof",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Reach the Woodland Mansion near the mark, then bring 6 books and carry a Totem of Undying to bind the dark roof road."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your horizon page open right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The roof will stay dark without our help."
                ]
              }
            ]
          },
          {
            "stageId": "coast_chosen",
            "label": "Coast Chosen",
            "trackerText": "Return to the cartographer to set the drowned coast horizon.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About The Horizon",
                "lines": [
                  "The drowned coast proof is here. The atlas can finally point beyond its own margin."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Set the drowned coast horizon",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Coast"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_coast",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "Done. The atlas has chosen the drowned coast, and the old blank promise has become a horizon."
                ]
              },
              {
                "sceneId": "complete_coast",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The coast binding still needs the monument mark and prismarine proof."
                ]
              },
              {
                "sceneId": "complete_coast",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "I cannot set the coast horizon until the proof is all here."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The coast will keep making noise until we finish."
                ]
              }
            ]
          },
          {
            "stageId": "dark_roof_chosen",
            "label": "Dark Roof Chosen",
            "trackerText": "Return to the cartographer to set the dark roof horizon.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About The Horizon",
                "lines": [
                  "The dark roof proof is here. The atlas can finally point beyond its own courage."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Set the dark roof horizon",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Dark Roof"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_dark_roof",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "Done. The atlas has chosen the dark roof road, and the old blank promise has become a horizon."
                ]
              },
              {
                "sceneId": "complete_dark_roof",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The dark roof binding still needs the mansion mark, the books, and the totem proof."
                ]
              },
              {
                "sceneId": "complete_dark_roof",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "I cannot set the dark roof horizon until the proof is all here."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The roof will stay dark until we finish."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "started",
            "label": "Started",
            "trackerText": "Choose the final atlas horizon from the cartographer's branch options.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Choose The Horizon",
                "lines": [
                  "The atlas has paper, bearings, field proof, memory, and judgment. Now it asks for a horizon.",
                  "Choose the drowned coast if you want the atlas to follow water and ruins. Choose the dark roof road if you want it to follow danger under old timber."
                ],
                "responses": [
                  {
                    "id": "coast",
                    "label": "Choose the drowned coast",
                    "lines": [
                      "Then we choose the coast. Keep your eyes on drowned stone, and do not trust quiet water."
                    ],
                    "targetStageId": "coast_final",
                    "destination": "Next: Coast Final"
                  },
                  {
                    "id": "dark_roof",
                    "label": "Choose the dark roof road",
                    "lines": [
                      "Then we choose the dark roof road. If the house looks back, look back harder."
                    ],
                    "targetStageId": "dark_roof_final",
                    "destination": "Next: Dark Roof Final"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Horizon",
                "lines": [
                  "This is the final fork. Coast or dark roof; water-stone or old timber."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the fork",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "I will choose",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Choose the drowned coast for water, prismarine, and ocean ruins. Choose the dark roof road for books, illagers, and a mansion horizon."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your horizon page open right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then the horizon stays unchosen, which is a kind of answer but not a useful one."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Choose with both eyes open."
                ]
              }
            ]
          },
          {
            "stageId": "coast_final",
            "label": "Coast Final",
            "trackerText": "Reach the Ocean Monument, then bring prismarine crystals and shards.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Horizon",
                "lines": [
                  "The drowned coast needs prismarine proof before I can set it into the final folio."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the proof",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Reach the Ocean Monument near the mark, then bring 4 prismarine crystals and 4 prismarine shards to bind the drowned coast horizon."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your horizon page open right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The tide can wait, but it will not wait forever."
                ]
              }
            ]
          },
          {
            "stageId": "dark_roof_final",
            "label": "Dark Roof Final",
            "trackerText": "Reach the Woodland Mansion, then bring books and a totem.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About The Horizon",
                "lines": [
                  "The dark roof road needs books and a totem before I can set it into the final folio."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Repeat the proof",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Reach the Woodland Mansion near the mark, then bring 6 books and carry a Totem of Undying to bind the dark roof road."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your horizon page open right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The roof will stay dark without our help."
                ]
              }
            ]
          },
          {
            "stageId": "coast_chosen",
            "label": "Coast Chosen",
            "trackerText": "Return to the cartographer to set the drowned coast horizon.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About The Horizon",
                "lines": [
                  "The drowned coast proof is here. The atlas can finally point beyond its own margin."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Set the drowned coast horizon",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Coast"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_coast",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "Done. The atlas has chosen the drowned coast, and the old blank promise has become a horizon."
                ]
              },
              {
                "sceneId": "complete_coast",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The coast binding still needs the monument mark and prismarine proof."
                ]
              },
              {
                "sceneId": "complete_coast",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "I cannot set the coast horizon until the proof is all here."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The coast will keep making noise until we finish."
                ]
              }
            ]
          },
          {
            "stageId": "dark_roof_chosen",
            "label": "Dark Roof Chosen",
            "trackerText": "Return to the cartographer to set the dark roof horizon.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About The Horizon",
                "lines": [
                  "The dark roof proof is here. The atlas can finally point beyond its own courage."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Set the dark roof horizon",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Dark Roof"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_dark_roof",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "Done. The atlas has chosen the dark roof road, and the old blank promise has become a horizon."
                ]
              },
              {
                "sceneId": "complete_dark_roof",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The dark roof binding still needs the mansion mark, the books, and the totem proof."
                ]
              },
              {
                "sceneId": "complete_dark_roof",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "I cannot set the dark roof horizon until the proof is all here."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The roof will stay dark until we finish."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 5
    },
    {
      "id": "villagerretaliation:chart_the_drowned_coast",
      "slug": "chart_the_drowned_coast",
      "title": "Chart the Drowned Coast",
      "description": "Follow the coast horizon to an Ocean Monument and bring back prismarine proof.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:choose_the_horizon",
      "parentSlug": "choose_the_horizon",
      "prerequisites": [
        {
          "id": "villagerretaliation:choose_the_horizon",
          "slug": "choose_the_horizon"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Journeyman",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 28,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Monument",
        "proofItem": "",
        "searchRadius": 384,
        "discoveryRadius": 160
      },
      "objectives": [
        "Visit Monument",
        "Defeat 3 Guardian",
        "8 Prismarine Shard"
      ],
      "steps": [
        {
          "id": "survey",
          "label": "Survey",
          "text": "Reach the Ocean Monument near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "visit_monument",
          "label": "Visit Monument",
          "text": "Reach the Ocean Monument near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "defeat_guardians",
          "label": "Defeat Guardians",
          "text": "Defeat 3 guardians near the drowned coast route.",
          "progress": 0.65,
          "hint": ""
        },
        {
          "id": "bring_prismarine",
          "label": "Bring Prismarine",
          "text": "Bring 8 prismarine shards from the drowned coast.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with prismarine proof.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 290,
        "reputation": 16,
        "gossipReputation": 7,
        "lootTable": "villagerretaliation:quest/chart_the_drowned_coast",
        "loot": [
          {
            "item": "Emerald",
            "count": "18-28",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Heart Of The Sea",
            "count": "1",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Nautilus Shell",
            "count": "2-5",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Sea Lantern",
            "count": "4-8",
            "weight": 2,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "You chose the coast, so the atlas is listening for drowned stone now.",
          "An Ocean Monument is singing through the ink. Chart it, survive its guardians, and bring prismarine back."
        ],
        "accept": "Chart the coast",
        "decline": "Another time",
        "started": [
          "The monument mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, survive the guardians, and bring prismarine."
        ],
        "reminder": [
          "Ocean Monument near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, defeat 3 guardians, and bring 8 prismarine shards."
        ],
        "completed": [
          "The drowned coast is charted. The atlas now knows how waves hide a road without erasing it."
        ],
        "missing": [
          "The prismarine needs the monument bearing behind it.",
          "Bring the prismarine proof before I trust the coast mark.",
          "The coast page still needs its monument, guardian count, and prismarine."
        ],
        "stages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Ocean Monument near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Drowned Coast",
                "lines": [
                  "You chose the coast, so the atlas is listening for drowned stone now.",
                  "An Ocean Monument is singing through the ink. Chart it, survive its guardians, and bring prismarine back."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Chart the coast",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About the Drowned Coast",
                "lines": [
                  "The coast route still needs a monument bearing, guardian proof, and prismarine shards."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will dry the coast page for now. The tide can rise again later."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "I will dry the coast page for a day before we ask it to open again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no drowned coast route active right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Ocean Monument near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, defeat 3 guardians, and bring 8 prismarine shards."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have the drowned coast page open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The drowned coast is already charted."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The monument mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, survive the guardians, and bring prismarine."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The coast is throwing glare across the page today. I would rather wait than hand you a false monument mark."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas must choose the coast horizon before this route opens."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Let the drowned coast sink back under the ink for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then keep your boots dry a little longer."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Mind the waterline."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with prismarine proof.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About the Drowned Coast",
                "lines": [
                  "You smell like salt and stubborn stone. Show me the coast proof."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the prismarine notes",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The drowned coast is charted. The atlas now knows how waves hide a road without erasing it."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The prismarine needs the monument bearing behind it."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring the prismarine proof before I trust the coast mark."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The coast page still needs its monument, guardian count, and prismarine."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The coast still needs its proof before I can close the page."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the shards wrapped. Wet ink is honest but messy."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Ocean Monument near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Drowned Coast",
                "lines": [
                  "You chose the coast, so the atlas is listening for drowned stone now.",
                  "An Ocean Monument is singing through the ink. Chart it, survive its guardians, and bring prismarine back."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Chart the coast",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About the Drowned Coast",
                "lines": [
                  "The coast route still needs a monument bearing, guardian proof, and prismarine shards."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will dry the coast page for now. The tide can rise again later."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "I will dry the coast page for a day before we ask it to open again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no drowned coast route active right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Ocean Monument near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, defeat 3 guardians, and bring 8 prismarine shards."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have the drowned coast page open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The drowned coast is already charted."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The monument mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, survive the guardians, and bring prismarine."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The coast is throwing glare across the page today. I would rather wait than hand you a false monument mark."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas must choose the coast horizon before this route opens."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Let the drowned coast sink back under the ink for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then keep your boots dry a little longer."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Mind the waterline."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with prismarine proof.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About the Drowned Coast",
                "lines": [
                  "You smell like salt and stubborn stone. Show me the coast proof."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the prismarine notes",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The drowned coast is charted. The atlas now knows how waves hide a road without erasing it."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The prismarine needs the monument bearing behind it."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring the prismarine proof before I trust the coast mark."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The coast page still needs its monument, guardian count, and prismarine."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The coast still needs its proof before I can close the page."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the shards wrapped. Wet ink is honest but messy."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 6
    },
    {
      "id": "villagerretaliation:ink_in_the_dark_roof",
      "slug": "ink_in_the_dark_roof",
      "title": "Ink in the Dark Roof",
      "description": "Follow the dark roof horizon to a Woodland Mansion and return with its stolen records.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:choose_the_horizon",
      "parentSlug": "choose_the_horizon",
      "prerequisites": [
        {
          "id": "villagerretaliation:choose_the_horizon",
          "slug": "choose_the_horizon"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Journeyman",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 30,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Woodland Mansion",
        "proofItem": "",
        "searchRadius": 768,
        "discoveryRadius": 192
      },
      "objectives": [
        "Visit Woodland Mansion",
        "Defeat 4 Vindicator or Evoker",
        "1 Totem Of Undying",
        "12 Book"
      ],
      "steps": [
        {
          "id": "survey",
          "label": "Survey",
          "text": "Reach the Woodland Mansion near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "visit_mansion",
          "label": "Visit Mansion",
          "text": "Reach the Woodland Mansion near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "defeat_illagers",
          "label": "Defeat Illagers",
          "text": "Defeat 4 mansion illagers on the dark roof road.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "carry_totem",
          "label": "Carry Totem",
          "text": "Carry 1 Totem of Undying as proof from the dark roof road.",
          "progress": 0.82,
          "hint": ""
        },
        {
          "id": "bring_books",
          "label": "Bring Books",
          "text": "Bring 12 books from the mansion shelves.",
          "progress": 0.92,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with the mansion records.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 330,
        "reputation": 17,
        "gossipReputation": 8,
        "lootTable": "villagerretaliation:quest/ink_in_the_dark_roof",
        "loot": [
          {
            "item": "Emerald",
            "count": "20-32",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "8-16",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Book",
            "count": "1",
            "weight": 1,
            "note": "Enchanted with Protection 3"
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "You chose the dark roof, so the atlas is listening for timber where old trouble nests.",
          "A Woodland Mansion is dragging ink off the edge of the page. Chart it, break the patrol, and bring back the books they kept from better hands."
        ],
        "accept": "Chart the dark roof",
        "decline": "Another time",
        "started": [
          "The mansion mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, break the patrol, and bring the books."
        ],
        "reminder": [
          "Woodland Mansion near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, defeat 4 vindicators or evokers, and bring 12 books."
        ],
        "completed": [
          "The dark roof road is charted. The atlas now knows that some houses are warnings pretending to be destinations."
        ],
        "missing": [
          "The books need the mansion bearing behind them.",
          "Bring the mansion proof before I trust the dark roof mark.",
          "The dark roof page still needs its mansion, patrol count, and records."
        ],
        "stages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Woodland Mansion near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Dark Roof Road",
                "lines": [
                  "You chose the dark roof, so the atlas is listening for timber where old trouble nests.",
                  "A Woodland Mansion is dragging ink off the edge of the page. Chart it, break the patrol, and bring back the books they kept from better hands."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Chart the dark roof",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About the Dark Roof Road",
                "lines": [
                  "The dark roof road still needs a mansion bearing, illager proof, and stolen records."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will shutter the dark roof page for now."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "I will shutter the dark roof page for a day. Let the ink stop watching us."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no dark roof route active right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Woodland Mansion near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, defeat 4 vindicators or evokers, and bring 12 books."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have the dark roof page open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The dark roof road is already charted."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The mansion mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, break the patrol, and bring the books."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The mansion is hiding too well today. I would rather wait than send you chasing a bad mark."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas must choose the dark roof horizon before this route opens."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Let the dark roof road close for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Reasonable. Some roofs are darker for good cause."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Do not trust a quiet hallway."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the mansion records.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About the Dark Roof Road",
                "lines": [
                  "The dark roof left ink on you. Let me see what you carried out."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the mansion records",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The dark roof road is charted. The atlas now knows that some houses are warnings pretending to be destinations."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The books need the mansion bearing behind them."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring the mansion proof before I trust the dark roof mark."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The dark roof page still needs its mansion, patrol count, and records."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The dark roof road still needs its proof before I can close the page."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the records closed until we can read them safely."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Woodland Mansion near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Dark Roof Road",
                "lines": [
                  "You chose the dark roof, so the atlas is listening for timber where old trouble nests.",
                  "A Woodland Mansion is dragging ink off the edge of the page. Chart it, break the patrol, and bring back the books they kept from better hands."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Chart the dark roof",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About the Dark Roof Road",
                "lines": [
                  "The dark roof road still needs a mansion bearing, illager proof, and stolen records."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will shutter the dark roof page for now."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "I will shutter the dark roof page for a day. Let the ink stop watching us."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no dark roof route active right now."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Woodland Mansion near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, defeat 4 vindicators or evokers, and bring 12 books."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have the dark roof page open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The dark roof road is already charted."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The mansion mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Chart it, break the patrol, and bring the books."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The mansion is hiding too well today. I would rather wait than send you chasing a bad mark."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas must choose the dark roof horizon before this route opens."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Let the dark roof road close for now?"
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Reasonable. Some roofs are darker for good cause."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Do not trust a quiet hallway."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the mansion records.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About the Dark Roof Road",
                "lines": [
                  "The dark roof left ink on you. Let me see what you carried out."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the mansion records",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The dark roof road is charted. The atlas now knows that some houses are warnings pretending to be destinations."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The books need the mansion bearing behind them."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring the mansion proof before I trust the dark roof mark."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The dark roof page still needs its mansion, patrol count, and records."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The dark roof road still needs its proof before I can close the page."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the records closed until we can read them safely."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 7
    },
    {
      "id": "villagerretaliation:nether_meridian",
      "slug": "nether_meridian",
      "title": "Nether Meridian",
      "description": "Carry the atlas beyond the Overworld and chart a Nether Fortress bearing.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:choose_the_horizon",
      "parentSlug": "choose_the_horizon",
      "prerequisites": [
        {
          "id": "villagerretaliation:choose_the_horizon",
          "slug": "choose_the_horizon"
        },
        {
          "id": "villagerretaliation:chart_the_drowned_coast",
          "slug": "chart_the_drowned_coast"
        },
        {
          "id": "villagerretaliation:ink_in_the_dark_roof",
          "slug": "ink_in_the_dark_roof"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Expert",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 42,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Fortress",
        "proofItem": "",
        "searchRadius": 320,
        "discoveryRadius": 128
      },
      "objectives": [
        "Visit Fortress",
        "3 Blaze Rod",
        "8 Nether Wart"
      ],
      "steps": [
        {
          "id": "survey",
          "label": "Survey",
          "text": "Reach the Nether Fortress near {target_x}, {target_z} in {target_dimension}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "visit_fortress",
          "label": "Visit Fortress",
          "text": "Reach the Nether Fortress near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "bring_blaze_rods",
          "label": "Bring Blaze Rods",
          "text": "Bring 3 blaze rods for heat-proof bearings.",
          "progress": 0.75,
          "hint": ""
        },
        {
          "id": "bring_nether_wart",
          "label": "Bring Nether Wart",
          "text": "Bring 8 Nether Wart from the fortress route.",
          "progress": 0.9,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with the Nether meridian.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 460,
        "reputation": 22,
        "gossipReputation": 10,
        "lootTable": "villagerretaliation:quest/nether_meridian",
        "loot": [
          {
            "item": "Emerald",
            "count": "28-42",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Gold Ingot",
            "count": "12-24",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Magma Cream",
            "count": "1",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "12-20",
            "weight": 2,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "The atlas survived an Overworld horizon. Now we find out if its lines can cross fire.",
          "Find a Nether Fortress, bring blaze rods and Nether Wart, and do not let the map learn to burn."
        ],
        "accept": "Chart the Nether meridian",
        "decline": "Another time",
        "started": [
          "The fortress mark is near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring blaze rods and Nether Wart."
        ],
        "reminder": [
          "Nether Fortress near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring 3 blaze rods and 8 Nether Wart."
        ],
        "completed": [
          "The Nether meridian is drawn. We have a road through heat now."
        ],
        "missing": [
          "The supplies need a fortress bearing behind them.",
          "Bring blaze rods from the fortress route before I trust the meridian.",
          "The meridian still needs the fortress bearing, blaze rods, and Nether Wart."
        ],
        "stages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Nether Fortress near {target_x}, {target_z} in {target_dimension}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Nether Meridian",
                "lines": [
                  "The atlas survived an Overworld horizon. Now we find out if its lines can cross fire.",
                  "Find a Nether Fortress, bring blaze rods and Nether Wart, and do not let the map learn to burn."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Chart the Nether meridian",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Nether Meridian",
                "lines": [
                  "The Nether page still needs a fortress bearing, blaze rods, and Nether Wart."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Nether Fortress near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring 3 blaze rods and 8 Nether Wart."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your Nether meridian open right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The Nether meridian is already drawn."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The fortress mark is near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring blaze rods and Nether Wart."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The Nether mark refuses to settle from here."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas needs one horizon commission complete before it can survive the Nether."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Reasonable. The Nether punishes casual curiosity."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the page away from sparks."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the Nether meridian.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Nether Meridian",
                "lines": [
                  "The ink warped, but the line held. Let me see the meridian proof."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the Nether meridian",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The Nether meridian is drawn. We have a road through heat now."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The supplies need a fortress bearing behind them."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring blaze rods from the fortress route before I trust the meridian."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The meridian still needs the fortress bearing, blaze rods, and Nether Wart."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The Nether meridian still needs its proof before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the hot ink away from the folio edge."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Nether Fortress near {target_x}, {target_z} in {target_dimension}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Nether Meridian",
                "lines": [
                  "The atlas survived an Overworld horizon. Now we find out if its lines can cross fire.",
                  "Find a Nether Fortress, bring blaze rods and Nether Wart, and do not let the map learn to burn."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Chart the Nether meridian",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Nether Meridian",
                "lines": [
                  "The Nether page still needs a fortress bearing, blaze rods, and Nether Wart."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Nether Fortress near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring 3 blaze rods and 8 Nether Wart."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your Nether meridian open right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The Nether meridian is already drawn."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The fortress mark is near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring blaze rods and Nether Wart."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The Nether mark refuses to settle from here."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas needs one horizon commission complete before it can survive the Nether."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Reasonable. The Nether punishes casual curiosity."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the page away from sparks."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the Nether meridian.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Nether Meridian",
                "lines": [
                  "The ink warped, but the line held. Let me see the meridian proof."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the Nether meridian",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The Nether meridian is drawn. We have a road through heat now."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The supplies need a fortress bearing behind them."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring blaze rods from the fortress route before I trust the meridian."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The meridian still needs the fortress bearing, blaze rods, and Nether Wart."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The Nether meridian still needs its proof before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the hot ink away from the folio edge."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 8
    },
    {
      "id": "villagerretaliation:eye_of_the_last_room",
      "slug": "eye_of_the_last_room",
      "title": "Eye of the Last Room",
      "description": "Follow the atlas to a Stronghold and return with Ender Eye bearings.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:nether_meridian",
      "parentSlug": "nether_meridian",
      "prerequisites": [
        {
          "id": "villagerretaliation:nether_meridian",
          "slug": "nether_meridian"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Master",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 58,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Stronghold",
        "proofItem": "",
        "searchRadius": 1024,
        "discoveryRadius": 192
      },
      "objectives": [
        "Visit Stronghold",
        "1 Ender Eye",
        "4 Ender Pearl"
      ],
      "steps": [
        {
          "id": "survey",
          "label": "Survey",
          "text": "Reach the Stronghold near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "visit_stronghold",
          "label": "Visit Stronghold",
          "text": "Reach the Stronghold near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "carry_eye",
          "label": "Carry Eye",
          "text": "Carry 1 Eye of Ender as proof of the last room.",
          "progress": 0.65,
          "hint": ""
        },
        {
          "id": "bring_pearls",
          "label": "Bring Pearls",
          "text": "Bring 4 Ender Pearls to stabilize the bearing.",
          "progress": 0.85,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with the last room bearing.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 620,
        "reputation": 28,
        "gossipReputation": 14,
        "lootTable": "villagerretaliation:quest/eye_of_the_last_room",
        "loot": [
          {
            "item": "Emerald",
            "count": "36-54",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Ender Eye",
            "count": "2-4",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Diamond",
            "count": "2-5",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "18-28",
            "weight": 2,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "The Nether line cooled into an arrow. It points toward a room people usually find by throwing eyes and hoping.",
          "Find the Stronghold, carry an Eye of Ender, and bring pearls so the atlas can learn the last room without losing itself."
        ],
        "accept": "Find the last room",
        "decline": "Another time",
        "started": [
          "The Stronghold mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Carry an Eye of Ender and bring 4 Ender Pearls."
        ],
        "reminder": [
          "Stronghold near {target_x}, {target_z}, about {distance} blocks {direction}. Reach it, carry an Eye of Ender, and bring 4 Ender Pearls."
        ],
        "completed": [
          "The last room bearing is drawn. The atlas can now point at a threshold without mistaking it for an ending."
        ],
        "missing": [
          "The Eye needs the Stronghold bearing behind it.",
          "Carry the Eye of Ender before I trust the last room mark.",
          "The page still needs the Stronghold bearing, Eye, and Ender Pearls."
        ],
        "stages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Stronghold near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Eye of the Last Room",
                "lines": [
                  "The Nether line cooled into an arrow. It points toward a room people usually find by throwing eyes and hoping.",
                  "Find the Stronghold, carry an Eye of Ender, and bring pearls so the atlas can learn the last room without losing itself."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Find the last room",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About the Last Room",
                "lines": [
                  "The last room page still needs a Stronghold bearing, an Eye of Ender, and Ender Pearls."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Stronghold near {target_x}, {target_z}, about {distance} blocks {direction}. Reach it, carry an Eye of Ender, and bring 4 Ender Pearls."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have the last room page open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The last room bearing is already drawn."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The Stronghold mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Carry an Eye of Ender and bring 4 Ender Pearls."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The last room turns away from the table today."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas needs the Nether meridian before it can read the last room."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "The last room is patient in a way I do not like."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Do not throw every eye at once."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the last room bearing.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About the Last Room",
                "lines": [
                  "You found the room that keeps the End behind a circle. Show me the bearing."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the last room bearing",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The last room bearing is drawn. The atlas can now point at a threshold without mistaking it for an ending."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The Eye needs the Stronghold bearing behind it."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Carry the Eye of Ender before I trust the last room mark."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The page still needs the Stronghold bearing, Eye, and Ender Pearls."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The last room bearing still needs its proof before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The circle can wait until you are ready."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Stronghold near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Eye of the Last Room",
                "lines": [
                  "The Nether line cooled into an arrow. It points toward a room people usually find by throwing eyes and hoping.",
                  "Find the Stronghold, carry an Eye of Ender, and bring pearls so the atlas can learn the last room without losing itself."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Find the last room",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About the Last Room",
                "lines": [
                  "The last room page still needs a Stronghold bearing, an Eye of Ender, and Ender Pearls."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Stronghold near {target_x}, {target_z}, about {distance} blocks {direction}. Reach it, carry an Eye of Ender, and bring 4 Ender Pearls."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have the last room page open for you right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The last room bearing is already drawn."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The Stronghold mark is near {target_x}, {target_z}, about {distance} blocks {direction}. Carry an Eye of Ender and bring 4 Ender Pearls."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The last room turns away from the table today."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas needs the Nether meridian before it can read the last room."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "The last room is patient in a way I do not like."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Do not throw every eye at once."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the last room bearing.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About the Last Room",
                "lines": [
                  "You found the room that keeps the End behind a circle. Show me the bearing."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Hand over the last room bearing",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "The last room bearing is drawn. The atlas can now point at a threshold without mistaking it for an ending."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The Eye needs the Stronghold bearing behind it."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Carry the Eye of Ender before I trust the last room mark."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The page still needs the Stronghold bearing, Eye, and Ender Pearls."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The last room bearing still needs its proof before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "The circle can wait until you are ready."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 9
    },
    {
      "id": "villagerretaliation:end_city_margin",
      "slug": "end_city_margin",
      "title": "End City Margin",
      "description": "Carry the atlas into the End, survey an End City, and return with shulker proof.",
      "questline": "cartographers_atlas",
      "questlineLabel": "Cartographers Atlas",
      "group": "exploration",
      "groupLabel": "Exploration",
      "tags": [
        "group.exploration"
      ],
      "relationKey": "questline:cartographers_atlas",
      "parent": "villagerretaliation:eye_of_the_last_room",
      "parentSlug": "eye_of_the_last_room",
      "prerequisites": [
        {
          "id": "villagerretaliation:eye_of_the_last_room",
          "slug": "eye_of_the_last_room"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Master",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 72,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "End City",
        "proofItem": "",
        "searchRadius": 1024,
        "discoveryRadius": 192
      },
      "objectives": [
        "Visit End City",
        "Defeat 2 Shulker",
        "2 Shulker Shell",
        "16 Chorus Fruit"
      ],
      "steps": [
        {
          "id": "survey",
          "label": "Survey",
          "text": "Reach the End City near {target_x}, {target_z} in {target_dimension}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "visit_end_city",
          "label": "Visit End City",
          "text": "Reach the End City near {target_x}, {target_z}.",
          "progress": 0.35,
          "hint": ""
        },
        {
          "id": "defeat_shulkers",
          "label": "Defeat Shulkers",
          "text": "Defeat 2 shulkers in the End City margin.",
          "progress": 0.65,
          "hint": ""
        },
        {
          "id": "bring_shulker_shells",
          "label": "Bring Shulker Shells",
          "text": "Bring 2 shulker shells from the End City.",
          "progress": 0.8,
          "hint": ""
        },
        {
          "id": "bring_chorus_fruit",
          "label": "Bring Chorus Fruit",
          "text": "Bring 16 chorus fruit from the outer islands.",
          "progress": 0.9,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with the finished atlas margin.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 900,
        "reputation": 38,
        "gossipReputation": 18,
        "lootTable": "villagerretaliation:quest/end_city_margin",
        "loot": [
          {
            "item": "Emerald",
            "count": "48-72",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Elytra",
            "count": "1",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Shulker Shell",
            "count": "2-4",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Diamond",
            "count": "4-8",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "24-40",
            "weight": 2,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "The last blank margin waits in the End, where roads float and cities pretend distance still makes sense.",
          "Find the city, record its shulkers, and bring shells with chorus fruit. This is how the outer margin learns to hold."
        ],
        "accept": "Chart the End margin",
        "decline": "Another time",
        "started": [
          "The city mark is near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring shulker shells and chorus fruit after you chart it."
        ],
        "reminder": [
          "End City near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Defeat 2 shulkers and bring 2 shulker shells with 16 chorus fruit."
        ],
        "completed": [
          "There it is: village road, ruin road, chosen horizon, fire road, last room, outer island. A whole atlas, and you walked it into truth."
        ],
        "missing": [
          "The shells need the End City bearing behind them.",
          "Bring shulker shells before I bind the final margin.",
          "The End margin still needs the city bearing, shulkers, shells, and chorus fruit."
        ],
        "stages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the End City near {target_x}, {target_z} in {target_dimension}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "End City Margin",
                "lines": [
                  "The last blank margin waits in the End, where roads float and cities pretend distance still makes sense.",
                  "Find the city, record its shulkers, and bring shells with chorus fruit. This is how the outer margin learns to hold."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Chart the End margin",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About End City Margin",
                "lines": [
                  "The End margin still needs the city bearing, shulker proof, shells, and chorus fruit."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "End City near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Defeat 2 shulkers and bring 2 shulker shells with 16 chorus fruit."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your End margin open right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The End City margin is already complete."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The city mark is near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring shulker shells and chorus fruit after you chart it."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The End City mark is too far for the atlas to catch from here."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas needs the Stronghold bearing before the End margin can be drawn."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "The outer islands are not going anywhere. That is one of their few courtesies."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Tie the page down before you open it near a portal."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the finished atlas margin.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About End City Margin",
                "lines": [
                  "You brought the outer islands back on your boots. Let me bind the final margin."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Bind the End City margin",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "There it is: village road, ruin road, chosen horizon, fire road, last room, outer island. A whole atlas, and you walked it into truth."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The shells need the End City bearing behind them."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring shulker shells before I bind the final margin."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The End margin still needs the city bearing, shulkers, shells, and chorus fruit."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The End margin still needs its proof before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the shells from rattling near the page edge."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the End City near {target_x}, {target_z} in {target_dimension}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "End City Margin",
                "lines": [
                  "The last blank margin waits in the End, where roads float and cities pretend distance still makes sense.",
                  "Find the city, record its shulkers, and bring shells with chorus fruit. This is how the outer margin learns to hold."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Chart the End margin",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About End City Margin",
                "lines": [
                  "The End margin still needs the city bearing, shulker proof, shells, and chorus fruit."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me where",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "leave",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "End City near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Defeat 2 shulkers and bring 2 shulker shells with 16 chorus fruit."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "I do not have your End margin open right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The End City margin is already complete."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "The city mark is near {target_x}, {target_z}, about {distance} blocks {direction} in {target_dimension}. Bring shulker shells and chorus fruit after you chart it."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The End City mark is too far for the atlas to catch from here."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The atlas needs the Stronghold bearing before the End margin can be drawn."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "The outer islands are not going anywhere. That is one of their few courtesies."
                ]
              },
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Tie the page down before you open it near a portal."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with the finished atlas margin.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About End City Margin",
                "lines": [
                  "You brought the outer islands back on your boots. Let me bind the final margin."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Bind the End City margin",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "leave",
                    "label": "Not yet",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Leave"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "There it is: village road, ruin road, chosen horizon, fire road, last room, outer island. A whole atlas, and you walked it into truth."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "The shells need the End City bearing behind them."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Bring shulker shells before I bind the final margin."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "The End margin still needs the city bearing, shulkers, shells, and chorus fruit."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The End margin still needs its proof before I can close it."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "leave",
                "label": "Scene: Leave",
                "lines": [
                  "Keep the shells from rattling near the page edge."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 10
    },
    {
      "id": "villagerretaliation:end_city_survey",
      "slug": "end_city_survey",
      "title": "End City Survey",
      "description": "Reach an End City and bring back a Shulker Shell with a Chorus Flower sample.",
      "questline": "",
      "questlineLabel": "",
      "group": "lost_civilization",
      "groupLabel": "Lost Civilization",
      "tags": [
        "group.lost_civilization"
      ],
      "relationKey": "group:lost_civilization",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Master",
        "professions": [
          "Cartographer",
          "Librarian"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 80,
            "max": null
          },
          {
            "skill": "Scholarship",
            "min": 65,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "End City",
        "proofItem": "Shulker Shell",
        "searchRadius": 384,
        "discoveryRadius": 192
      },
      "objectives": [
        "Proof: Shulker Shell",
        "1 Chorus Flower"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Reach the End City near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring a Shulker Shell and 1 Chorus Flower.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "bring_chorus_flower",
          "label": "Bring Chorus Flower",
          "text": "Keep 1 Chorus Flower sample intact.",
          "progress": 0.88,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with proof from the End City.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 720,
        "reputation": 36,
        "gossipReputation": 20,
        "lootTable": "villagerretaliation:quest/end_city_survey",
        "loot": [
          {
            "item": "Emerald",
            "count": "56-80",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Diamond",
            "count": "8-14",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "36-60",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Shulker Shell",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "3 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "The last map edge is not an edge. It is a dare written in empty ink.",
          "I have a survey no sane cartographer files twice: an End City, a shell, and a living sample."
        ],
        "accept": "I will survey the End City",
        "decline": "Another time",
        "started": [
          "The city mark is {direction}, about {distance} blocks from here, near {target_x}, {target_z}. Bring a Shulker Shell and one Chorus Flower."
        ],
        "reminder": [
          "End City near {target_x}, {target_z}. Shulker Shell for proof, Chorus Flower for study. Try not to let the void edit the report."
        ],
        "completed": [
          "A shell and a Chorus Flower. The village has heard stories. Now it has evidence. Your name will travel farther than the road."
        ],
        "missing": [
          "A shell alone is not a survey. Reach the End City itself.",
          "Bring a Shulker Shell. The city has to leave a hard signature.",
          "I also need one Chorus Flower, intact if possible."
        ]
      },
      "questlineOrder": 0
    },
    {
      "id": "villagerretaliation:tales_of_a_lost_civilization",
      "slug": "tales_of_a_lost_civilization",
      "title": "Tales of a Lost Civilization",
      "description": "Follow a cartographer's rumor to an Ancient City and return with an Echo Shard.",
      "questline": "lost_civilization",
      "questlineLabel": "Lost Civilization",
      "group": "lost_civilization",
      "groupLabel": "Lost Civilization",
      "tags": [
        "group.lost_civilization"
      ],
      "relationKey": "questline:lost_civilization",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Journeyman",
        "professions": [
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 50,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Ancient City",
        "proofItem": "Echo Shard",
        "searchRadius": 256,
        "discoveryRadius": 128
      },
      "objectives": [
        "Proof: Echo Shard",
        "Visit Ancient City",
        "1 Echo Shard"
      ],
      "steps": [
        {
          "id": "survey",
          "label": "Survey",
          "text": "Reach the Ancient City center near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": ""
        },
        {
          "id": "visit_city_center",
          "label": "Visit City Center",
          "text": "Reach the Ancient City center near {target_x}, {target_z}.",
          "progress": 0.45,
          "hint": ""
        },
        {
          "id": "recover_echo_shard",
          "label": "Recover Echo Shard",
          "text": "Recover {objective_item} as proof of the journey.",
          "progress": 0.66,
          "hint": "City center visited"
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with {proof_item}.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 430,
        "reputation": 22,
        "gossipReputation": 10,
        "lootTable": "villagerretaliation:quest/tales_of_a_lost_civilization",
        "loot": [
          {
            "item": "Emerald",
            "count": "30-44",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Book",
            "count": "1",
            "weight": 1,
            "note": "Enchanted with Swift Sneak 1"
          },
          {
            "item": "Echo Shard",
            "count": "2-5",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "18-30",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Diamond",
            "count": "3-6",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "There are old maps that refuse to stay still. One of them keeps crawling back to the same shape under the ink.",
          "I found a mark that does not belong to any road I know. It points below the world, toward a city people stopped naming."
        ],
        "accept": "Tell me where to go",
        "decline": "Another time",
        "started": [
          "One mark points {direction}, roughly {distance} blocks from here, near {target_x}, {target_z}. If you truly want the story, find the heart of {target} and bring back {proof_item}.",
          "I have copied this mark three times, and every copy crawls back to the same place: {target_x}, {target_z}, {direction} of us. Go carefully. The center of {target} is the part that matters, and {proof_item} will prove you reached it.",
          "Then listen closely: travel {direction}, about {distance} blocks, toward {target_x}, {target_z}. Stand in the city center, not just the outskirts, and return with {proof_item}."
        ],
        "reminder": [
          "The place was {direction}, roughly {distance} blocks away, near {target_x}, {target_z}. Reach the center of {target}, then bring me {proof_item}.",
          "Do not be fooled by the outer halls. The quest needs the city center itself. My mark was near {target_x}, {target_z}, {direction} of here, and {proof_item} is the proof I need.",
          "The old mark has not changed: {target_x}, {target_z}, about {distance} blocks {direction}. Center first, {proof_item} second, then come back.",
          "The place was {direction}, roughly {distance} blocks away, near {target_x}, {target_z}. Reach the center of {target}, then bring me {proof_item}.",
          "Do not be fooled by the outer halls. The quest needs the city center itself. My mark was near {target_x}, {target_z}, {direction} of here, and {proof_item} is the proof I need.",
          "The old mark has not changed: {target_x}, {target_z}, about {distance} blocks {direction}. Center first, {proof_item} second, then come back."
        ],
        "completed": [
          "So the stories were not just ink after all. Keep what you learned close, and let the village know you walked where silence keeps records.",
          "You found the center and brought proof. That is more than a map can do. This village will remember your name beside that lost place.",
          "An Echo Shard from the heart of {target}. I believe you. Some stories should be paid for before they are repeated."
        ],
        "missing": [
          "You saw the place, then. Bring me {proof_item}, and I can call the tale complete.",
          "The journey needs a token. Find {proof_item} in those depths and bring it back.",
          "That shard alone is not enough. You need to stand in the central heart of {target}, not only its halls.",
          "The proof is half the story. The other half is the place itself: the city center near {target_x}, {target_z}."
        ],
        "stages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Ancient City center near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Lost Civilization",
                "lines": [
                  "There are old maps that refuse to stay still. One of them keeps crawling back to the same shape under the ink.",
                  "I found a mark that does not belong to any road I know. It points below the world, toward a city people stopped naming."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Tell me where to go",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Lost Civilization",
                "lines": [
                  "About the old city? I can mark the road again, or fold the map if you are done carrying it.",
                  "The map is still yours to follow. Ask for the mark again, or I can put the route away."
                ],
                "responses": [
                  {
                    "id": "directions",
                    "label": "Remind me where to go",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Active Cancel"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "Then I will fold the route away. If the old city keeps calling, come back and we can mark it again.",
                  "Done. The map rests for now."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "Then I will fold the route away for a day. Give the ink time to dry before asking me to mark it again.",
                  "Done. I can revisit the mark later, once the map has settled."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_forever",
                "label": "Abandon: Abandoned Forever",
                "lines": [
                  "Then I will close the ledger on this one.",
                  "Done. I will not offer that trail again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "That map is not in your hands right now.",
                  "There is no active trail here for me to fold away."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "The place was {direction}, roughly {distance} blocks away, near {target_x}, {target_z}. Reach the center of {target}, then bring me {proof_item}.",
                  "Do not be fooled by the outer halls. The quest needs the city center itself. My mark was near {target_x}, {target_z}, {direction} of here, and {proof_item} is the proof I need.",
                  "The old mark has not changed: {target_x}, {target_z}, about {distance} blocks {direction}. Center first, {proof_item} second, then come back."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "The map is not giving me the same answer right now.",
                  "If the mark has slipped, come back when the ink settles."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "That lost city has already given us its answer.",
                  "We have that tale now. I am still deciding how much of it should be spoken aloud."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The map table will not settle on that city today. I would rather wait than send you to a bad mark.",
                  "I can feel the old road under the ink, but not clearly enough to send you there."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "One mark points {direction}, roughly {distance} blocks from here, near {target_x}, {target_z}. If you truly want the story, find the heart of {target} and bring back {proof_item}.",
                  "I have copied this mark three times, and every copy crawls back to the same place: {target_x}, {target_z}, {direction} of us. Go carefully. The center of {target} is the part that matters, and {proof_item} will prove you reached it.",
                  "Then listen closely: travel {direction}, about {distance} blocks, toward {target_x}, {target_z}. Stand in the city center, not just the outskirts, and return with {proof_item}."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "Not yet. This story needs a steadier hand with maps.",
                  "There are marks I do not show until I trust the reading, and the reader."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_cancel",
                "label": "Scene: Abandon Cancel",
                "lines": [
                  "Good. A folded map remembers less than a carried one.",
                  "Then keep the mark. Some stories punish hesitation, but this one rewards care."
                ]
              },
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Folding this map means the trail goes cold for a while. Are you sure?",
                  "I can put the old mark away, but I will not pretend the place stops waiting."
                ]
              },
              {
                "sceneId": "active_cancel",
                "label": "Scene: Active Cancel",
                "lines": [
                  "Then keep the map close until the road starts speaking again.",
                  "Fair. Some maps are better carried quietly."
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then leave the map folded. Some places are patient in ways people are not.",
                  "Fair. A city that learned to keep silent can wait a little longer."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with {proof_item}.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Lost Civilization",
                "lines": [
                  "About the old city? I can mark the road again, or fold the map if you are done carrying it.",
                  "The map is still yours to follow. Ask for the mark again, or I can put the route away."
                ],
                "responses": [
                  {
                    "id": "directions",
                    "label": "Remind me where to go",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Active Cancel"
                  }
                ]
              },
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Lost Civilization",
                "lines": [
                  "You came back carrying the quiet with you. Tell me you stood in the center, not just the shadow of the place.",
                  "That look is not from a cave. That is from somewhere older. Did you bring proof?"
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Show the Echo Shard",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Turn In Wait"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "Then I will fold the route away. If the old city keeps calling, come back and we can mark it again.",
                  "Done. The map rests for now."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "Then I will fold the route away for a day. Give the ink time to dry before asking me to mark it again.",
                  "Done. I can revisit the mark later, once the map has settled."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_forever",
                "label": "Abandon: Abandoned Forever",
                "lines": [
                  "Then I will close the ledger on this one.",
                  "Done. I will not offer that trail again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "That map is not in your hands right now.",
                  "There is no active trail here for me to fold away."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "So the stories were not just ink after all. Keep what you learned close, and let the village know you walked where silence keeps records.",
                  "You found the center and brought proof. That is more than a map can do. This village will remember your name beside that lost place.",
                  "An Echo Shard from the heart of {target}. I believe you. Some stories should be paid for before they are repeated."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "You saw the place, then. Bring me {proof_item}, and I can call the tale complete.",
                  "The journey needs a token. Find {proof_item} in those depths and bring it back."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "That shard alone is not enough. You need to stand in the central heart of {target}, not only its halls.",
                  "The proof is half the story. The other half is the place itself: the city center near {target_x}, {target_z}."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The story still has a missing piece.",
                  "Something is missing from the story."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "The place was {direction}, roughly {distance} blocks away, near {target_x}, {target_z}. Reach the center of {target}, then bring me {proof_item}.",
                  "Do not be fooled by the outer halls. The quest needs the city center itself. My mark was near {target_x}, {target_z}, {direction} of here, and {proof_item} is the proof I need.",
                  "The old mark has not changed: {target_x}, {target_z}, about {distance} blocks {direction}. Center first, {proof_item} second, then come back."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "The map is not giving me the same answer right now.",
                  "If the mark has slipped, come back when the ink settles."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_cancel",
                "label": "Scene: Abandon Cancel",
                "lines": [
                  "Good. A folded map remembers less than a carried one.",
                  "Then keep the mark. Some stories punish hesitation, but this one rewards care."
                ]
              },
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Folding this map means the trail goes cold for a while. Are you sure?",
                  "I can put the old mark away, but I will not pretend the place stops waiting."
                ]
              },
              {
                "sceneId": "active_cancel",
                "label": "Scene: Active Cancel",
                "lines": [
                  "Then keep the map close until the road starts speaking again.",
                  "Fair. Some maps are better carried quietly."
                ]
              },
              {
                "sceneId": "turn_in_wait",
                "label": "Scene: Turn In Wait",
                "lines": [
                  "Then keep it safe until you are ready to let the village remember with you.",
                  "No need to force the words. Bring them back when they have settled."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "survey",
            "label": "Survey",
            "trackerText": "Reach the Ancient City center near {target_x}, {target_z}.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Lost Civilization",
                "lines": [
                  "There are old maps that refuse to stay still. One of them keeps crawling back to the same shape under the ink.",
                  "I found a mark that does not belong to any road I know. It points below the world, toward a city people stopped naming."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "Tell me where to go",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Lost Civilization",
                "lines": [
                  "About the old city? I can mark the road again, or fold the map if you are done carrying it.",
                  "The map is still yours to follow. Ask for the mark again, or I can put the route away."
                ],
                "responses": [
                  {
                    "id": "directions",
                    "label": "Remind me where to go",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Active Cancel"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "Then I will fold the route away. If the old city keeps calling, come back and we can mark it again.",
                  "Done. The map rests for now."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "Then I will fold the route away for a day. Give the ink time to dry before asking me to mark it again.",
                  "Done. I can revisit the mark later, once the map has settled."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_forever",
                "label": "Abandon: Abandoned Forever",
                "lines": [
                  "Then I will close the ledger on this one.",
                  "Done. I will not offer that trail again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "That map is not in your hands right now.",
                  "There is no active trail here for me to fold away."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "The place was {direction}, roughly {distance} blocks away, near {target_x}, {target_z}. Reach the center of {target}, then bring me {proof_item}.",
                  "Do not be fooled by the outer halls. The quest needs the city center itself. My mark was near {target_x}, {target_z}, {direction} of here, and {proof_item} is the proof I need.",
                  "The old mark has not changed: {target_x}, {target_z}, about {distance} blocks {direction}. Center first, {proof_item} second, then come back."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "The map is not giving me the same answer right now.",
                  "If the mark has slipped, come back when the ink settles."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "That lost city has already given us its answer.",
                  "We have that tale now. I am still deciding how much of it should be spoken aloud."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "The map table will not settle on that city today. I would rather wait than send you to a bad mark.",
                  "I can feel the old road under the ink, but not clearly enough to send you there."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "One mark points {direction}, roughly {distance} blocks from here, near {target_x}, {target_z}. If you truly want the story, find the heart of {target} and bring back {proof_item}.",
                  "I have copied this mark three times, and every copy crawls back to the same place: {target_x}, {target_z}, {direction} of us. Go carefully. The center of {target} is the part that matters, and {proof_item} will prove you reached it.",
                  "Then listen closely: travel {direction}, about {distance} blocks, toward {target_x}, {target_z}. Stand in the city center, not just the outskirts, and return with {proof_item}."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "Not yet. This story needs a steadier hand with maps.",
                  "There are marks I do not show until I trust the reading, and the reader."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_cancel",
                "label": "Scene: Abandon Cancel",
                "lines": [
                  "Good. A folded map remembers less than a carried one.",
                  "Then keep the mark. Some stories punish hesitation, but this one rewards care."
                ]
              },
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Folding this map means the trail goes cold for a while. Are you sure?",
                  "I can put the old mark away, but I will not pretend the place stops waiting."
                ]
              },
              {
                "sceneId": "active_cancel",
                "label": "Scene: Active Cancel",
                "lines": [
                  "Then keep the map close until the road starts speaking again.",
                  "Fair. Some maps are better carried quietly."
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then leave the map folded. Some places are patient in ways people are not.",
                  "Fair. A city that learned to keep silent can wait a little longer."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the cartographer with {proof_item}.",
            "slots": [
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Lost Civilization",
                "lines": [
                  "About the old city? I can mark the road again, or fold the map if you are done carrying it.",
                  "The map is still yours to follow. Ask for the mark again, or I can put the route away."
                ],
                "responses": [
                  {
                    "id": "directions",
                    "label": "Remind me where to go",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Active Cancel"
                  }
                ]
              },
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Lost Civilization",
                "lines": [
                  "You came back carrying the quiet with you. Tell me you stood in the center, not just the shadow of the place.",
                  "That look is not from a cave. That is from somewhere older. Did you bring proof?"
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Show the Echo Shard",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Turn In Wait"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "Then I will fold the route away. If the old city keeps calling, come back and we can mark it again.",
                  "Done. The map rests for now."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "Then I will fold the route away for a day. Give the ink time to dry before asking me to mark it again.",
                  "Done. I can revisit the mark later, once the map has settled."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_forever",
                "label": "Abandon: Abandoned Forever",
                "lines": [
                  "Then I will close the ledger on this one.",
                  "Done. I will not offer that trail again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "That map is not in your hands right now.",
                  "There is no active trail here for me to fold away."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "So the stories were not just ink after all. Keep what you learned close, and let the village know you walked where silence keeps records.",
                  "You found the center and brought proof. That is more than a map can do. This village will remember your name beside that lost place.",
                  "An Echo Shard from the heart of {target}. I believe you. Some stories should be paid for before they are repeated."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "You saw the place, then. Bring me {proof_item}, and I can call the tale complete.",
                  "The journey needs a token. Find {proof_item} in those depths and bring it back."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "That shard alone is not enough. You need to stand in the central heart of {target}, not only its halls.",
                  "The proof is half the story. The other half is the place itself: the city center near {target_x}, {target_z}."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "The story still has a missing piece.",
                  "Something is missing from the story."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "The place was {direction}, roughly {distance} blocks away, near {target_x}, {target_z}. Reach the center of {target}, then bring me {proof_item}.",
                  "Do not be fooled by the outer halls. The quest needs the city center itself. My mark was near {target_x}, {target_z}, {direction} of here, and {proof_item} is the proof I need.",
                  "The old mark has not changed: {target_x}, {target_z}, about {distance} blocks {direction}. Center first, {proof_item} second, then come back."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "The map is not giving me the same answer right now.",
                  "If the mark has slipped, come back when the ink settles."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_cancel",
                "label": "Scene: Abandon Cancel",
                "lines": [
                  "Good. A folded map remembers less than a carried one.",
                  "Then keep the mark. Some stories punish hesitation, but this one rewards care."
                ]
              },
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Folding this map means the trail goes cold for a while. Are you sure?",
                  "I can put the old mark away, but I will not pretend the place stops waiting."
                ]
              },
              {
                "sceneId": "active_cancel",
                "label": "Scene: Active Cancel",
                "lines": [
                  "Then keep the map close until the road starts speaking again.",
                  "Fair. Some maps are better carried quietly."
                ]
              },
              {
                "sceneId": "turn_in_wait",
                "label": "Scene: Turn In Wait",
                "lines": [
                  "Then keep it safe until you are ready to let the village remember with you.",
                  "No need to force the words. Bring them back when they have settled."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 0
    },
    {
      "id": "villagerretaliation:sunken_ledger",
      "slug": "sunken_ledger",
      "title": "Sunken Ledger",
      "description": "Search a shipwreck and return with a compass and paper before the route is forgotten.",
      "questline": "",
      "questlineLabel": "",
      "group": "old_roads",
      "groupLabel": "Old Roads",
      "tags": [
        "group.old_roads"
      ],
      "relationKey": "group:old_roads",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Journeyman",
        "professions": [
          "Fisherman",
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 20,
            "max": null
          },
          {
            "skill": "Fishing",
            "min": 30,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Shipwreck",
        "proofItem": "Compass",
        "searchRadius": 192,
        "discoveryRadius": 128
      },
      "objectives": [
        "Proof: Compass",
        "8 Paper"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Reach the shipwreck near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring a compass and 8 paper.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "bring_paper",
          "label": "Bring Paper",
          "text": "Collect 8 paper for a clean copy.",
          "progress": 0.74,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the compass and paper.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 180,
        "reputation": 12,
        "gossipReputation": 5,
        "lootTable": "villagerretaliation:quest/sunken_ledger",
        "loot": [
          {
            "item": "Emerald",
            "count": "14-22",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Prismarine Shard",
            "count": "14-26",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Nautilus Shell",
            "count": "1",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "8-14",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "20 minute abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "A wreck offshore may hold old route notes. Most wet paper lies. A compass lies less.",
          "There is a shipwreck I want checked before its records become pulp."
        ],
        "accept": "I will find the wreck",
        "decline": "Another time",
        "started": [
          "The wreck should be {direction}, about {distance} blocks away, near {target_x}, {target_z}. Bring a compass and 8 paper for the copy."
        ],
        "reminder": [
          "Shipwreck near {target_x}, {target_z}. Compass for proof, paper for the ledger."
        ],
        "completed": [
          "A compass from the wreck and pages to save the route. Good. The water can keep the rest."
        ],
        "missing": [
          "The compass matters after you reach the wreck itself.",
          "Bring me a compass from the wreck. Direction is the point of this errand.",
          "I still need 8 paper to copy the ledger cleanly."
        ]
      },
      "questlineOrder": 0
    },
    {
      "id": "villagerretaliation:the_broken_milestone",
      "slug": "the_broken_milestone",
      "title": "The Broken Milestone",
      "description": "Find nearby Trail Ruins and bring a brush and stone to restore the road marker.",
      "questline": "",
      "questlineLabel": "",
      "group": "old_roads",
      "groupLabel": "Old Roads",
      "tags": [
        "group.old_roads"
      ],
      "relationKey": "group:old_roads",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Mason",
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Cartography",
            "min": 10,
            "max": null
          },
          {
            "skill": "Masonry",
            "min": 18,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Trail Ruins",
        "proofItem": "Brush",
        "searchRadius": 160,
        "discoveryRadius": 96
      },
      "objectives": [
        "Proof: Brush",
        "12 Smooth Stone"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Reach the old road mark near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring a brush and 12 smooth stone.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "bring_smooth_stone",
          "label": "Bring Smooth Stone",
          "text": "Gather 12 smooth stone for the repair.",
          "progress": 0.78,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the brush and stone.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 140,
        "reputation": 11,
        "gossipReputation": 4,
        "lootTable": "villagerretaliation:quest/the_broken_milestone",
        "loot": [
          {
            "item": "Emerald",
            "count": "12-18",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Chiseled Stone Bricks",
            "count": "10-20",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Quartz",
            "count": "4-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "5-10",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion"
      ],
      "dialogue": {
        "offer": [
          "A road marker broke near some old ruins. Without it, travelers follow bad guesses.",
          "There is a milestone out there that stopped doing its job. I would like it corrected."
        ],
        "accept": "Mark the road for me",
        "decline": "Another time",
        "started": [
          "The old mark is about {distance} blocks {direction}, near {target_x}, {target_z}. Bring a brush and 12 smooth stone so we can set the route right."
        ],
        "reminder": [
          "Trail ruins near {target_x}, {target_z}. I need a brush for the old dust and 12 smooth stone for the new face."
        ],
        "completed": [
          "Good. A repaired road is a quiet promise that someone can still come home."
        ],
        "missing": [
          "The stone is useful, but I need you to reach the ruins near the marker first.",
          "Bring a brush from the work. A clean story needs its dust on it.",
          "The repair still needs 12 smooth stone."
        ]
      },
      "questlineOrder": 1
    },
    {
      "id": "villagerretaliation:fletchers_countermark",
      "slug": "fletchers_countermark",
      "title": "Fletcher's Countermark",
      "description": "Scout a Pillager Outpost and return with a crossbow as proof of the threat.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_defense",
      "groupLabel": "Village Defense",
      "tags": [
        "group.village_defense"
      ],
      "relationKey": "group:village_defense",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Journeyman",
        "professions": [
          "Fletcher"
        ],
        "skills": [
          {
            "skill": "Archery",
            "min": 35,
            "max": null
          },
          {
            "skill": "Guarding",
            "min": 20,
            "max": null
          }
        ]
      },
      "target": {
        "structure": "Pillager Outpost",
        "proofItem": "Crossbow",
        "searchRadius": 192,
        "discoveryRadius": 96
      },
      "objectives": [
        "Proof: Crossbow",
        "24 Arrow"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Scout the outpost near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring back a crossbow and 24 arrows.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "bring_arrows",
          "label": "Bring Arrows",
          "text": "Bundle 24 arrows for the village watch.",
          "progress": 0.8,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the outpost proof.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 220,
        "reputation": 15,
        "gossipReputation": 7,
        "lootTable": "villagerretaliation:quest/fletchers_countermark",
        "loot": [
          {
            "item": "Emerald",
            "count": "18-26",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Spectral Arrow",
            "count": "14-24",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Arrow",
            "count": "32-64",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "8-14",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day abandonment cooldown"
      ],
      "dialogue": {
        "offer": [
          "A raider mark was carved where it should not be. I want proof before I wake every bow in town.",
          "There is an outpost too close for comfort. Bring me a crossbow from it and arrows for our own racks."
        ],
        "accept": "I will scout it",
        "decline": "Another time",
        "started": [
          "The outpost lies {direction}, about {distance} blocks, near {target_x}, {target_z}. Bring a crossbow and 24 arrows."
        ],
        "reminder": [
          "Outpost near {target_x}, {target_z}. Crossbow for proof, 24 arrows so we answer properly."
        ],
        "completed": [
          "That is enough proof. The village will know where to point its fear, and its arrows."
        ],
        "missing": [
          "A crossbow can come from many hands. I need you to scout the outpost itself.",
          "Bring a crossbow from the threat. I want weight, not rumor.",
          "The watch still needs 24 arrows."
        ]
      },
      "questlineOrder": 0
    },
    {
      "id": "villagerretaliation:watch_arrows",
      "slug": "watch_arrows",
      "title": "Watch Arrows",
      "description": "Bring arrows so the village watch can answer trouble before it reaches the doors.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_defense",
      "groupLabel": "Village Defense",
      "tags": [
        "group.village_defense"
      ],
      "relationKey": "group:village_defense",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Fletcher"
        ],
        "skills": [
          {
            "skill": "Archery",
            "min": 10,
            "max": null
          },
          {
            "skill": "Guarding",
            "min": 6,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "16 Arrow"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 16 arrows for the village watch.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the arrow.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 90,
        "reputation": 7,
        "gossipReputation": 4,
        "lootTable": "villagerretaliation:quest/watch_arrows",
        "loot": [
          {
            "item": "Emerald",
            "count": "8-14",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Flint",
            "count": "4-8",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Feather",
            "count": "4-8",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "4-7",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "2 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The watch quiver is too light.",
          "A warning bell is useful, but arrows make trouble reconsider."
        ],
        "accept": "I can bring arrows",
        "decline": "Another time",
        "started": [
          "Watch Arrows is yours now. Bring the arrows back when the count is ready."
        ],
        "reminder": [
          "Watch Arrows: I still need the arrows. Bring the full count back to me."
        ],
        "completed": [
          "Watch Arrows is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Watch Arrows is not at the right count yet; bring the rest before turning it in.",
          "Watch Arrows still needs the arrows in your pack before I can close it.",
          "Watch Arrows is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 1
    },
    {
      "id": "villagerretaliation:standing_watch",
      "slug": "standing_watch",
      "title": "Standing Watch",
      "description": "Stand watch and help defend the village from a real threat.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_defense",
      "groupLabel": "Village Defense",
      "tags": [
        "group.village_defense"
      ],
      "relationKey": "group:village_defense",
      "parent": "villagerretaliation:watch_arrows",
      "parentSlug": "watch_arrows",
      "prerequisites": [
        {
          "id": "villagerretaliation:watch_arrows",
          "slug": "watch_arrows"
        }
      ],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Fletcher",
          "Weaponsmith",
          "Armorer"
        ],
        "skills": [
          {
            "skill": "Guarding",
            "min": 10,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "Record memory: Player Defended Village"
      ],
      "steps": [
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver after defending the village.",
          "progress": 1,
          "hint": ""
        },
        {
          "id": "event",
          "label": "Event",
          "text": "Defend the village from a real threat.",
          "progress": 0.85,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 140,
        "reputation": 12,
        "gossipReputation": 6,
        "lootTable": "villagerretaliation:quest/standing_watch",
        "loot": [
          {
            "item": "Emerald",
            "count": "10-18",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Arrow",
            "count": "4-8",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Iron Ingot",
            "count": "2-4",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Shield",
            "count": "1",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "One-time",
        "Can be completed with another valid villager",
        "Turn-in items are consumed on completion"
      ],
      "dialogue": {
        "offer": [
          "Arrows help. Nerves help more.",
          "Stand watch with us, and if danger comes, answer it before it reaches the doors."
        ],
        "accept": "I will stand watch",
        "decline": "Another time",
        "started": [
          "Standing Watch is yours now. Stay close enough to answer if danger reaches the village."
        ],
        "reminder": [
          "Stay near the village. If danger comes, answer it before it reaches the doors."
        ],
        "completed": [
          "Standing Watch is complete. The village saw you answer trouble, and that matters."
        ],
        "missing": [
          "The village still needs to see you answer a real threat.",
          "Words do not hold a gate. Stand with us when danger reaches the village.",
          "The village has not seen you defend it yet."
        ]
      },
      "questlineOrder": 2
    },
    {
      "id": "villagerretaliation:beetroot_bundle",
      "slug": "beetroot_bundle",
      "title": "Beetroot Bundle",
      "description": "Bring beetroot for stews, pickling jars, and simple sickroom meals.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Farmer",
          "Cleric"
        ],
        "skills": [
          {
            "skill": "Farming",
            "min": 5,
            "max": null
          },
          {
            "skill": "Medicine",
            "min": 4,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "16 Beetroot"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 16 beetroot for the pantry.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the beetroot.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 50,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/beetroot_bundle",
        "loot": [
          {
            "item": "Emerald",
            "count": "4-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Baked Potato",
            "count": "4-7",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Honey Bottle",
            "count": "1-2",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The pantry looks organized, which is not the same as prepared.",
          "A bundle of beetroot would make the next few meals feel less accidental."
        ],
        "accept": "I can bring beetroot",
        "decline": "Another time",
        "started": [
          "Beetroot Bundle is yours now. Bring the beetroot back when the count is ready."
        ],
        "reminder": [
          "Beetroot Bundle: I still need the beetroot. Bring the full count back to me."
        ],
        "completed": [
          "Beetroot Bundle is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Beetroot Bundle is not at the right count yet, bring the rest before turning it in.",
          "Beetroot Bundle still needs the beetroot in your pack before I can close it.",
          "Beetroot Bundle is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 0
    },
    {
      "id": "villagerretaliation:berry_picking",
      "slug": "berry_picking",
      "title": "Berry Picking",
      "description": "Bring sweet berries for quick meals and traveling pouches.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Farmer",
          "Butcher"
        ],
        "skills": [
          {
            "skill": "Gathering",
            "min": 6,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "16 Sweet Berries"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Gather 16 sweet berries for village stores.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the sweet berries.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 50,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/berry_picking",
        "loot": [
          {
            "item": "Emerald",
            "count": "4-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Cookie",
            "count": "4-8",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Honey Bottle",
            "count": "1-2",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The small baskets empty first.",
          "A few sweet berries would make the next ration day feel less like punishment."
        ],
        "accept": "I can fill the berry baskets",
        "decline": "Another time",
        "started": [
          "Berry Picking is yours now. Bring the sweet berries back when the count is ready."
        ],
        "reminder": [
          "Berry Picking: I still need the sweet berries. Bring the full count back to me."
        ],
        "completed": [
          "Berry Picking is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Berry Picking is not at the right count yet; bring the rest before turning it in.",
          "Berry Picking still needs the sweet berries in your pack before I can close it.",
          "Berry Picking is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 1
    },
    {
      "id": "villagerretaliation:bottle_stock",
      "slug": "bottle_stock",
      "title": "Bottle Stock",
      "description": "Bring glass bottles for tonics, inks, and careful measuring.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Cleric",
          "Librarian"
        ],
        "skills": [
          {
            "skill": "Medicine",
            "min": 5,
            "max": null
          },
          {
            "skill": "Scholarship",
            "min": 5,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "12 Glass Bottle"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 12 glass bottles for the shelf.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the glass bottles.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 55,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/bottle_stock",
        "loot": [
          {
            "item": "Emerald",
            "count": "5-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Honey Bottle",
            "count": "1-2",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Paper",
            "count": "4-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "3-5",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "We are down to rinsing old bottles and hoping memory counts as sanitation.",
          "Fresh glass would improve both medicine and morale."
        ],
        "accept": "I can bring bottles",
        "decline": "Another time",
        "started": [
          "Bottle Stock is yours now. Bring the glass bottles back when the count is ready."
        ],
        "reminder": [
          "Bottle Stock: I still need the glass bottles. Bring the full count back to me."
        ],
        "completed": [
          "Bottle Stock is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Bottle Stock is not at the right count yet; bring the rest before turning it in.",
          "Bottle Stock still needs the glass bottles in your pack before I can close it.",
          "Bottle Stock is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 2
    },
    {
      "id": "villagerretaliation:bread_delivery",
      "slug": "bread_delivery",
      "title": "Bread Delivery",
      "description": "Bring bread so the village can stretch its stores through a hard night.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Farmer"
        ],
        "skills": [
          {
            "skill": "Farming",
            "min": 8,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "16 Bread"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 16 bread back to the quest giver.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the bread.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 60,
        "reputation": 5,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/bread_delivery",
        "loot": [
          {
            "item": "Emerald",
            "count": "6-10",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "4-8",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Golden Carrot",
            "count": "3-6",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Hay Block",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are not consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The bins are low. Nothing dramatic, which is usually when it becomes dramatic.",
          "A few good loaves would quiet half the village by sunset."
        ],
        "accept": "I can help stock the larder",
        "decline": "Another time",
        "started": [
          "Bread Delivery is yours now. Bring the bread back when the count is ready."
        ],
        "reminder": [
          "Bread Delivery: I still need the bread. Bring the full count back to me."
        ],
        "completed": [
          "Bread Delivery is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Bread Delivery is not at the right count yet; bring the rest before turning it in.",
          "Bread Delivery still needs the bread in your pack before I can close it.",
          "Bread Delivery is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 3
    },
    {
      "id": "villagerretaliation:clay_repairs",
      "slug": "clay_repairs",
      "title": "Clay Repairs",
      "description": "Bring clay for small repairs around wells, ovens, and cracked walls.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Mason"
        ],
        "skills": [
          {
            "skill": "Masonry",
            "min": 8,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "16 Clay Ball"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 16 clay balls for village repairs.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the clay ball.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 65,
        "reputation": 5,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/clay_repairs",
        "loot": [
          {
            "item": "Emerald",
            "count": "6-10",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Brick",
            "count": "8-16",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Stone Bricks",
            "count": "8-12",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "3-5",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "Small cracks have started introducing themselves.",
          "I would rather meet them with clay than with a collapsed oven."
        ],
        "accept": "I can bring clay",
        "decline": "Another time",
        "started": [
          "Clay Repairs is yours now. Bring the clay back when the count is ready."
        ],
        "reminder": [
          "Clay Repairs: I still need the clay. Bring the full count back to me."
        ],
        "completed": [
          "Clay Repairs is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Clay Repairs is not at the right count yet; bring the rest before turning it in.",
          "Clay Repairs still needs the clay in your pack before I can close it.",
          "Clay Repairs is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 4
    },
    {
      "id": "villagerretaliation:feather_fletching",
      "slug": "feather_fletching",
      "title": "Feather Fletching",
      "description": "Bring feathers so arrows and message shafts can be finished in proper batches.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Fletcher",
          "Shepherd"
        ],
        "skills": [
          {
            "skill": "Archery",
            "min": 4,
            "max": null
          },
          {
            "skill": "Animal Handling",
            "min": 5,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "16 Feather"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 16 feathers for the fletching bench.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the feathers.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 55,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/feather_fletching",
        "loot": [
          {
            "item": "Emerald",
            "count": "5-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Arrow",
            "count": "12-24",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Flint",
            "count": "2-5",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "I have shafts waiting on one side and patience failing on the other.",
          "A bundle of feathers would fix both problems."
        ],
        "accept": "I can bring feathers",
        "decline": "Another time",
        "started": [
          "Feather Fletching is yours now. Bring the feathers back when the count is ready."
        ],
        "reminder": [
          "Feather Fletching: I still need the feathers. Bring the full count back to me."
        ],
        "completed": [
          "Feather Fletching is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Feather Fletching is not at the right count yet; bring the rest before turning it in.",
          "Feather Fletching still needs the feathers in your pack before I can close it.",
          "Feather Fletching is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 5
    },
    {
      "id": "villagerretaliation:fresh_cod",
      "slug": "fresh_cod",
      "title": "Fresh Cod",
      "description": "Bring cod for a simple village supper.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Fisherman",
          "Butcher"
        ],
        "skills": [
          {
            "skill": "Fishing",
            "min": 6,
            "max": null
          },
          {
            "skill": "Cooking",
            "min": 4,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "10 Cod"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 10 cod for supper.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the cod.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 55,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/fresh_cod",
        "loot": [
          {
            "item": "Emerald",
            "count": "5-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Bread",
            "count": "3-6",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Kelp",
            "count": "8-16",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The pot is ready and the fish are not.",
          "I dislike that order of events."
        ],
        "accept": "I can bring cod",
        "decline": "Another time",
        "started": [
          "Fresh Cod is yours now. Bring the cod back when the count is ready."
        ],
        "reminder": [
          "Fresh Cod: I still need the cod. Bring the full count back to me."
        ],
        "completed": [
          "Fresh Cod is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Fresh Cod is not at the right count yet; bring the rest before turning it in.",
          "Fresh Cod still needs the cod in your pack before I can close it.",
          "Fresh Cod is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 6
    },
    {
      "id": "villagerretaliation:ink_supply",
      "slug": "ink_supply",
      "title": "Ink Supply",
      "description": "Bring ink so ledgers, maps, and warning notices remain legible.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Librarian",
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Scholarship",
            "min": 12,
            "max": null
          },
          {
            "skill": "Cartography",
            "min": 8,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "6 Ink Sac"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 6 ink sacs for the ledger.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the ink sac.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 95,
        "reputation": 7,
        "gossipReputation": 3,
        "lootTable": "villagerretaliation:quest/ink_supply",
        "loot": [
          {
            "item": "Emerald",
            "count": "9-14",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Paper",
            "count": "6-12",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Glow Ink Sac",
            "count": "1-2",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "4-7",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "2 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The ledger ink is getting thin.",
          "I dislike guessing whether a line says paid, owed, or run."
        ],
        "accept": "I can bring ink",
        "decline": "Another time",
        "started": [
          "Ink Supply is yours now. Bring the ink sacs back when the count is ready."
        ],
        "reminder": [
          "Ink Supply: I still need the ink sacs. Bring the full count back to me."
        ],
        "completed": [
          "Ink Supply is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Ink Supply is not at the right count yet; bring the rest before turning it in.",
          "Ink Supply still needs the ink sacs in your pack before I can close it.",
          "Ink Supply is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 7
    },
    {
      "id": "villagerretaliation:kiln_fuel",
      "slug": "kiln_fuel",
      "title": "Kiln Fuel",
      "description": "Bring coal to keep the kiln and forge work moving.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Mason",
          "Armorer",
          "Toolsmith",
          "Weaponsmith"
        ],
        "skills": [
          {
            "skill": "Mining",
            "min": 10,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "12 Coal"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 12 coal for the kiln and forge fires.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the coal.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 85,
        "reputation": 7,
        "gossipReputation": 3,
        "lootTable": "villagerretaliation:quest/kiln_fuel",
        "loot": [
          {
            "item": "Emerald",
            "count": "8-13",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Iron Nugget",
            "count": "8-16",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Copper Ingot",
            "count": "3-6",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "4-7",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "2 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The firebox is down to crumbs.",
          "A cold kiln is just an expensive room with opinions."
        ],
        "accept": "I can bring coal",
        "decline": "Another time",
        "started": [
          "Kiln Fuel is yours now. Bring the coal back when the count is ready."
        ],
        "reminder": [
          "Kiln Fuel: I still need the coal. Bring the full count back to me."
        ],
        "completed": [
          "Kiln Fuel is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Kiln Fuel is not at the right count yet; bring the rest before turning it in.",
          "Kiln Fuel still needs the coal in your pack before I can close it.",
          "Kiln Fuel is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 8
    },
    {
      "id": "villagerretaliation:leather_repairs",
      "slug": "leather_repairs",
      "title": "Leather Repairs",
      "description": "Bring leather for tool loops, armor straps, and pack repairs.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Leatherworker",
          "Armorer"
        ],
        "skills": [
          {
            "skill": "Leatherworking",
            "min": 10,
            "max": null
          },
          {
            "skill": "Crafting",
            "min": 6,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "8 Leather"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 8 leather for repair straps.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the leather.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 80,
        "reputation": 6,
        "gossipReputation": 3,
        "lootTable": "villagerretaliation:quest/leather_repairs",
        "loot": [
          {
            "item": "Emerald",
            "count": "7-12",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Rabbit Hide",
            "count": "2-5",
            "weight": 2,
            "note": ""
          },
          {
            "item": "String",
            "count": "6-12",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "4-6",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "2 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The repair pile is mostly broken straps.",
          "That is better than broken backs, but only if we fix it."
        ],
        "accept": "I can bring leather",
        "decline": "Another time",
        "started": [
          "Leather Repairs is yours now. Bring the leather back when the count is ready."
        ],
        "reminder": [
          "Leather Repairs: I still need the leather. Bring the full count back to me."
        ],
        "completed": [
          "Leather Repairs is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Leather Repairs is not at the right count yet; bring the rest before turning it in.",
          "Leather Repairs still needs the leather in your pack before I can close it.",
          "Leather Repairs is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 9
    },
    {
      "id": "villagerretaliation:map_paper",
      "slug": "map_paper",
      "title": "Map Paper",
      "description": "Bring paper so village records, maps, and warnings can stay current.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Librarian",
          "Cartographer"
        ],
        "skills": [
          {
            "skill": "Scholarship",
            "min": 6,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "24 Paper"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 24 paper for village notes and maps.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the paper.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 55,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/map_paper",
        "loot": [
          {
            "item": "Emerald",
            "count": "5-9",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Book",
            "count": "1-2",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Map",
            "count": "1",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "3-5",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "We are writing warnings on the backs of old lists.",
          "That works until someone mistakes a raid note for a turnip count."
        ],
        "accept": "I can bring paper",
        "decline": "Another time",
        "started": [
          "Map Paper is yours now. Bring the paper back when the count is ready."
        ],
        "reminder": [
          "Map Paper: I still need the paper. Bring the full count back to me."
        ],
        "completed": [
          "Map Paper is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Map Paper is not at the right count yet; bring the rest before turning it in.",
          "Map Paper still needs the paper in your pack before I can close it.",
          "Map Paper is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 10
    },
    {
      "id": "villagerretaliation:seed_stockpile",
      "slug": "seed_stockpile",
      "title": "Seed Stockpile",
      "description": "Bring seed grain so the village can replant without touching winter stores.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Farmer"
        ],
        "skills": [
          {
            "skill": "Farming",
            "min": 5,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "32 Wheat Seeds"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 32 wheat seeds for the reserve.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the wheat seeds.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 45,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/seed_stockpile",
        "loot": [
          {
            "item": "Emerald",
            "count": "4-7",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Bone Meal",
            "count": "8-16",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Wheat",
            "count": "6-12",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "The seed bin is lower than I like.",
          "If we borrow from the eating grain, spring will arrive with an argument already waiting."
        ],
        "accept": "I can bring seed grain",
        "decline": "Another time",
        "started": [
          "Seed Stockpile is yours now. Bring the wheat seeds back when the count is ready."
        ],
        "reminder": [
          "Seed Stockpile: I still need the wheat seeds. Bring the full count back to me."
        ],
        "completed": [
          "Seed Stockpile is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Seed Stockpile is not at the right count yet; bring the rest before turning it in.",
          "Seed Stockpile still needs the wheat seeds in your pack before I can close it.",
          "Seed Stockpile is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 11
    },
    {
      "id": "villagerretaliation:torch_bundle",
      "slug": "torch_bundle",
      "title": "Torch Bundle",
      "description": "Bring torches so storehouses, pens, and work corners stay usable after dusk.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Mason",
          "Toolsmith",
          "Cleric"
        ],
        "skills": [
          {
            "skill": "Crafting",
            "min": 6,
            "max": null
          },
          {
            "skill": "Mining",
            "min": 4,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "16 Torch"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 16 torches for the watch rack.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the torches.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 50,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/torch_bundle",
        "loot": [
          {
            "item": "Emerald",
            "count": "4-8",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Coal",
            "count": "4-8",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Stick",
            "count": "8-16",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "A few corners of the village are getting ideas after dusk.",
          "More torches would remind them who actually lives here."
        ],
        "accept": "I can bring torches",
        "decline": "Another time",
        "started": [
          "Torch Bundle is yours now. Bring the torches back when the count is ready."
        ],
        "reminder": [
          "Torch Bundle: I still need the torches. Bring the full count back to me."
        ],
        "completed": [
          "Torch Bundle is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Torch Bundle is not at the right count yet; bring the rest before turning it in.",
          "Torch Bundle still needs the torches in your pack before I can close it.",
          "Torch Bundle is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 12
    },
    {
      "id": "villagerretaliation:village_lanterns",
      "slug": "village_lanterns",
      "title": "Village Lanterns",
      "description": "Bring lanterns before the watch loses the edges of the village to darkness.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Apprentice",
        "professions": [
          "Cleric",
          "Mason"
        ],
        "skills": [
          {
            "skill": "Crafting",
            "min": 15,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "6 Lantern"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 6 lanterns before the request goes stale.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the lanterns.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 100,
        "reputation": 8,
        "gossipReputation": 3,
        "lootTable": "villagerretaliation:quest/village_lanterns",
        "loot": [
          {
            "item": "Emerald",
            "count": "9-14",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Glowstone Dust",
            "count": "14-24",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Iron Ingot",
            "count": "2-4",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "6-10",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "3 day completion cooldown",
        "10 minute abandonment cooldown",
        "Expires after 2 days"
      ],
      "dialogue": {
        "offer": [
          "The watch posts need light, not speeches. Six lanterns would do more than ten warnings.",
          "Night has been leaning too close to the doors. We need more lanterns before habit becomes fear."
        ],
        "accept": "I will bring lanterns",
        "decline": "Another time",
        "started": [
          "Village Lanterns is yours now. Bring the lanterns back when the count is ready."
        ],
        "reminder": [
          "Village Lanterns: I still need the lanterns. Bring the full count back to me."
        ],
        "completed": [
          "Village Lanterns is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Village Lanterns is not at the right count yet; bring the rest before turning it in.",
          "Village Lanterns still needs the lanterns in your pack before I can close it.",
          "Village Lanterns is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 13
    },
    {
      "id": "villagerretaliation:wool_blankets",
      "slug": "wool_blankets",
      "title": "Wool Blankets",
      "description": "Bring wool for clean bedding and warm wraps.",
      "questline": "",
      "questlineLabel": "",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "group:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Shepherd",
          "Cleric"
        ],
        "skills": [
          {
            "skill": "Animal Handling",
            "min": 6,
            "max": null
          },
          {
            "skill": "Medicine",
            "min": 4,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "12 White Wool"
      ],
      "steps": [
        {
          "id": "proof",
          "label": "Proof",
          "text": "Bring 12 white wool for sickbeds.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the white wool.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 60,
        "reputation": 5,
        "gossipReputation": 3,
        "lootTable": "villagerretaliation:quest/wool_blankets",
        "loot": [
          {
            "item": "Emerald",
            "count": "5-9",
            "weight": 1,
            "note": ""
          },
          {
            "item": "String",
            "count": "6-12",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Honey Bottle",
            "count": "1-2",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "3-5",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "We are short on clean bedding.",
          "No one recovers faster because the blanket is thin."
        ],
        "accept": "I can bring wool",
        "decline": "Another time",
        "started": [
          "Wool Blankets is yours now. Bring the white wool back when the count is ready."
        ],
        "reminder": [
          "Wool Blankets: I still need the white wool. Bring the full count back to me."
        ],
        "completed": [
          "Wool Blankets is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Wool Blankets is not at the right count yet; bring the rest before turning it in.",
          "Wool Blankets still needs the white wool in your pack before I can close it.",
          "Wool Blankets is still short. The tracker has the exact count."
        ]
      },
      "questlineOrder": 14
    },
    {
      "id": "villagerretaliation:egg_baskets",
      "slug": "egg_baskets",
      "title": "Egg Baskets",
      "description": "Bring eggs so the kitchens can stretch breakfast and broth.",
      "questline": "village_supply",
      "questlineLabel": "Village Supply",
      "group": "village_supply",
      "groupLabel": "Village Supply",
      "tags": [
        "group.village_supply"
      ],
      "relationKey": "questline:village_supply",
      "parent": "",
      "parentSlug": "",
      "prerequisites": [],
      "branchGroup": "",
      "branchChoices": [],
      "requirements": {
        "minLevel": "Novice",
        "professions": [
          "Farmer",
          "Butcher"
        ],
        "skills": [
          {
            "skill": "Animal Handling",
            "min": 5,
            "max": null
          },
          {
            "skill": "Cooking",
            "min": 4,
            "max": null
          }
        ]
      },
      "target": null,
      "objectives": [
        "12 Egg"
      ],
      "steps": [
        {
          "id": "collect",
          "label": "Collect",
          "text": "Bring 12 eggs for the kitchen baskets.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "bring_eggs",
          "label": "Bring Eggs",
          "text": "Bring 12 eggs for the kitchen baskets.",
          "progress": 0.7,
          "hint": ""
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the quest giver with the eggs.",
          "progress": 1,
          "hint": ""
        }
      ],
      "rewards": {
        "experience": 45,
        "reputation": 4,
        "gossipReputation": 2,
        "lootTable": "villagerretaliation:quest/egg_baskets",
        "loot": [
          {
            "item": "Emerald",
            "count": "4-7",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Bread",
            "count": "3-5",
            "weight": 2,
            "note": ""
          },
          {
            "item": "Pumpkin Pie",
            "count": "1-2",
            "weight": 1,
            "note": ""
          },
          {
            "item": "Experience Bottle",
            "count": "2-4",
            "weight": 1,
            "note": ""
          }
        ]
      },
      "rules": [
        "Repeatable",
        "Can be completed with another valid villager",
        "Locked to the quest giver",
        "Turn-in items are consumed on completion",
        "1 day completion cooldown"
      ],
      "dialogue": {
        "offer": [
          "We are shorter on eggs than we are on patience.",
          "That usually ends with breakfast becoming a rumor."
        ],
        "accept": "I can fill the egg baskets",
        "decline": "Another time",
        "started": [
          "Egg Baskets is yours now. Bring the eggs back when the count is ready."
        ],
        "reminder": [
          "Egg Baskets: I still need the eggs. Bring the full count back to me."
        ],
        "completed": [
          "Egg Baskets is complete. The village can use this, and you have earned the reward."
        ],
        "missing": [
          "Egg Baskets is still short. The tracker has the exact count.",
          "Egg Baskets still needs the eggs in your pack before I can close it.",
          "Egg Baskets is not at the right count yet; bring the rest before turning it in."
        ],
        "stages": [
          {
            "stageId": "collect",
            "label": "Collect",
            "trackerText": "Bring 12 eggs for the kitchen baskets.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Egg Baskets",
                "lines": [
                  "We are shorter on eggs than we are on patience.",
                  "That usually ends with breakfast becoming a rumor."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "I can fill the egg baskets",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Egg Baskets",
                "lines": [
                  "Egg Baskets is still open. I can repeat the details, or close my notes if you are done."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me what to do",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Active Cancel"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will set the egg baskets aside. Return if the kitchen still needs them."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "I will pause the egg count for now. Let the kitchen settle before asking again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_forever",
                "label": "Abandon: Abandoned Forever",
                "lines": [
                  "Then the egg request is closed for good."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no egg count here for me to close."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "inactive",
                "label": "Reminder: Inactive",
                "lines": [
                  "The kitchen has no active egg count at the moment."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Egg Baskets: I still need the eggs. Bring the full count back to me."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "That egg-basket request is not in your hands right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The egg baskets are already settled."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "Egg Baskets cannot be posted from here right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "Egg Baskets is yours now. Bring the eggs back when the count is ready."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The kitchen is not asking you for eggs right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_cancel",
                "label": "Scene: Abandon Cancel",
                "lines": [
                  "Good. The pans will stay hopeful a little longer."
                ]
              },
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Put Egg Baskets aside for now?"
                ]
              },
              {
                "sceneId": "active_cancel",
                "label": "Scene: Active Cancel",
                "lines": [
                  "Then keep the shells uncracked until you are ready."
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then I will keep negotiating with empty pans."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the quest giver with the eggs.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Egg Baskets",
                "lines": [
                  "If you have the eggs, we can settle Egg Baskets."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Show what I brought",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Turn In Wait"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will set the egg baskets aside. Return if the kitchen still needs them."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "I will pause the egg count for now. Let the kitchen settle before asking again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_forever",
                "label": "Abandon: Abandoned Forever",
                "lines": [
                  "Then the egg request is closed for good."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no egg count here for me to close."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "Egg Baskets is complete. The village can use this, and you have earned the reward."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "inactive",
                "label": "Turn-in: Inactive",
                "lines": [
                  "The kitchen is not accepting eggs right now."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "Egg Baskets is still short. The tracker has the exact count."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Egg Baskets still needs the eggs in your pack before I can close it."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "Egg Baskets is not at the right count yet; bring the rest before turning it in."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "This egg count is not ready to close yet."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_cancel",
                "label": "Scene: Abandon Cancel",
                "lines": [
                  "Good. The pans will stay hopeful a little longer."
                ]
              },
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Put Egg Baskets aside for now?"
                ]
              },
              {
                "sceneId": "turn_in_wait",
                "label": "Scene: Turn In Wait",
                "lines": [
                  "Keep the eggs safe, then bring them back when you are ready."
                ]
              }
            ]
          }
        ],
        "commonStages": [
          {
            "stageId": "collect",
            "label": "Collect",
            "trackerText": "Bring 12 eggs for the kitchen baskets.",
            "slots": [
              {
                "slot": "offer",
                "title": "Offer",
                "label": "Egg Baskets",
                "lines": [
                  "We are shorter on eggs than we are on patience.",
                  "That usually ends with breakfast becoming a rumor."
                ],
                "responses": [
                  {
                    "id": "accept",
                    "label": "I can fill the egg baskets",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Start Quest"
                  },
                  {
                    "id": "decline",
                    "label": "Another time",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Decline"
                  }
                ]
              },
              {
                "slot": "reminder",
                "title": "Reminder",
                "label": "About Egg Baskets",
                "lines": [
                  "Egg Baskets is still open. I can repeat the details, or close my notes if you are done."
                ],
                "responses": [
                  {
                    "id": "details",
                    "label": "Remind me what to do",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Reminder Details"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Active Cancel"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will set the egg baskets aside. Return if the kitchen still needs them."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "I will pause the egg count for now. Let the kitchen settle before asking again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_forever",
                "label": "Abandon: Abandoned Forever",
                "lines": [
                  "Then the egg request is closed for good."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no egg count here for me to close."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "inactive",
                "label": "Reminder: Inactive",
                "lines": [
                  "The kitchen has no active egg count at the moment."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "reminder",
                "label": "Reminder: Reminder",
                "lines": [
                  "Egg Baskets: I still need the eggs. Bring the full count back to me."
                ]
              },
              {
                "sceneId": "reminder_details",
                "action": "remind",
                "key": "unavailable",
                "label": "Reminder: Unavailable",
                "lines": [
                  "That egg-basket request is not in your hands right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "already_completed",
                "label": "Start: Already completed",
                "lines": [
                  "The egg baskets are already settled."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "locate_failed",
                "label": "Start: Locate Failed",
                "lines": [
                  "Egg Baskets cannot be posted from here right now."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "started",
                "label": "Start: Started",
                "lines": [
                  "Egg Baskets is yours now. Bring the eggs back when the count is ready."
                ]
              },
              {
                "sceneId": "start_quest",
                "action": "start",
                "key": "unavailable",
                "label": "Start: Unavailable",
                "lines": [
                  "The kitchen is not asking you for eggs right now."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_cancel",
                "label": "Scene: Abandon Cancel",
                "lines": [
                  "Good. The pans will stay hopeful a little longer."
                ]
              },
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Put Egg Baskets aside for now?"
                ]
              },
              {
                "sceneId": "active_cancel",
                "label": "Scene: Active Cancel",
                "lines": [
                  "Then keep the shells uncracked until you are ready."
                ]
              },
              {
                "sceneId": "decline",
                "label": "Scene: Decline",
                "lines": [
                  "Then I will keep negotiating with empty pans."
                ]
              }
            ]
          },
          {
            "stageId": "return",
            "label": "Return",
            "trackerText": "Return to the quest giver with the eggs.",
            "slots": [
              {
                "slot": "turn_in",
                "title": "Turn-in",
                "label": "About Egg Baskets",
                "lines": [
                  "If you have the eggs, we can settle Egg Baskets."
                ],
                "responses": [
                  {
                    "id": "complete",
                    "label": "Show what I brought",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Complete Quest"
                  },
                  {
                    "id": "abandon",
                    "label": "Abandon quest",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Abandon Confirm"
                  },
                  {
                    "id": "never_mind",
                    "label": "Never mind",
                    "lines": [],
                    "targetStageId": "",
                    "destination": "Scene: Turn In Wait"
                  }
                ]
              }
            ],
            "choices": [],
            "actions": [
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned",
                "label": "Abandon: Abandoned",
                "lines": [
                  "I will set the egg baskets aside. Return if the kitchen still needs them."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_cooldown",
                "label": "Abandon: Abandoned Cooldown",
                "lines": [
                  "I will pause the egg count for now. Let the kitchen settle before asking again."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "abandoned_forever",
                "label": "Abandon: Abandoned Forever",
                "lines": [
                  "Then the egg request is closed for good."
                ]
              },
              {
                "sceneId": "abandon_quest",
                "action": "abandon",
                "key": "unavailable",
                "label": "Abandon: Unavailable",
                "lines": [
                  "There is no egg count here for me to close."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "completed",
                "label": "Turn-in: Completed",
                "lines": [
                  "Egg Baskets is complete. The village can use this, and you have earned the reward."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "inactive",
                "label": "Turn-in: Inactive",
                "lines": [
                  "The kitchen is not accepting eggs right now."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_objectives",
                "label": "Turn-in: Missing objectives",
                "lines": [
                  "Egg Baskets is still short. The tracker has the exact count."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_proof",
                "label": "Turn-in: Missing proof",
                "lines": [
                  "Egg Baskets still needs the eggs in your pack before I can close it."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "missing_target",
                "label": "Turn-in: Missing target",
                "lines": [
                  "Egg Baskets is not at the right count yet; bring the rest before turning it in."
                ]
              },
              {
                "sceneId": "complete_quest",
                "action": "turn_in",
                "key": "unavailable",
                "label": "Turn-in: Unavailable",
                "lines": [
                  "This egg count is not ready to close yet."
                ]
              }
            ],
            "scenes": [
              {
                "sceneId": "abandon_cancel",
                "label": "Scene: Abandon Cancel",
                "lines": [
                  "Good. The pans will stay hopeful a little longer."
                ]
              },
              {
                "sceneId": "abandon_confirm",
                "label": "Scene: Abandon Confirm",
                "lines": [
                  "Put Egg Baskets aside for now?"
                ]
              },
              {
                "sceneId": "turn_in_wait",
                "label": "Scene: Turn In Wait",
                "lines": [
                  "Keep the eggs safe, then bring them back when you are ready."
                ]
              }
            ]
          }
        ],
        "branches": []
      },
      "questlineOrder": 0
    }
  ],
  "gifts": {
    "totals": {
      "preferences": 62,
      "rewards": 30
    },
    "globalPreferredItems": [
      "Apple",
      "Baked Potato",
      "Barrel",
      "Bread",
      "Cake",
      "Campfire",
      "Carrot",
      "Chest",
      "Cookie",
      "Crafting Table",
      "Deepslate Emerald Ore",
      "Diamond",
      "Emerald",
      "Emerald Block",
      "Emerald Ore",
      "Enchanted Golden Apple",
      "Experience Bottle",
      "Flower Pot",
      "Glow Berries",
      "Gold Ingot",
      "Golden Apple",
      "Golden Carrot",
      "Honey Bottle",
      "Lantern",
      "Melon Slice",
      "Milk Bucket",
      "Pumpkin Pie",
      "Sweet Berries",
      "Torch"
    ],
    "globalDislikedItems": [
      "Bone",
      "Bone Meal",
      "Cobweb",
      "Dead Bush",
      "Fermented Spider Eye",
      "Fire Charge",
      "Firework Rocket",
      "Flint And Steel",
      "Gunpowder",
      "Lava Bucket",
      "Magma Cream",
      "Ominous Bottle",
      "Ominous Trial Key",
      "Phantom Membrane",
      "Poisonous Potato",
      "Pufferfish",
      "Rotten Flesh",
      "Slime Ball",
      "Spider Eye",
      "Suspicious Stew",
      "Tnt",
      "Tnt Minecart",
      "Trial Key",
      "Wither Rose",
      "Wither Skeleton Skull"
    ],
    "globalNeutralItems": [],
    "reactions": [
      {
        "reaction": "Loved",
        "count": 16,
        "allItems": [
          "Amethyst Shard",
          "Anvil",
          "Apple",
          "Arrow",
          "Barrel",
          "Beef",
          "Beetroot",
          "Beetroot Seeds",
          "Black Wool",
          "Blaze Rod",
          "Blue Wool",
          "Book",
          "Bookshelf",
          "Bow",
          "Brewing Stand",
          "Brick",
          "Bricks",
          "Brown Wool",
          "Bucket",
          "Bundle",
          "Cake",
          "Carrot",
          "Cartography Table",
          "Cauldron",
          "Chainmail Boots",
          "Chainmail Chestplate",
          "Chainmail Helmet",
          "Chainmail Leggings",
          "Chicken",
          "Chiseled Bookshelf",
          "Chiseled Stone Bricks",
          "Clay",
          "Clay Ball",
          "Clock",
          "Cod",
          "Compass",
          "Composter",
          "Cooked Beef",
          "Cooked Chicken",
          "Cooked Cod",
          "Cooked Mutton",
          "Cooked Porkchop",
          "Cooked Rabbit",
          "Cooked Salmon",
          "Cookie",
          "Crossbow",
          "Cut Copper",
          "Cyan Wool",
          "Diamond",
          "Diamond Axe",
          "Diamond Boots",
          "Diamond Chestplate",
          "Diamond Helmet",
          "Diamond Hoe",
          "Diamond Leggings",
          "Diamond Pickaxe",
          "Diamond Shovel",
          "Diamond Sword",
          "Dragon Breath",
          "Echo Shard",
          "Emerald",
          "Emerald Block",
          "Enchanted Book",
          "Enchanted Golden Apple",
          "Ender Pearl",
          "Experience Bottle",
          "Feather",
          "Filled Map",
          "Fishing Rod",
          "Fletching Table",
          "Flint",
          "Ghast Tear",
          "Glowstone",
          "Glowstone Dust",
          "Gold Ingot",
          "Golden Apple",
          "Golden Carrot",
          "Gray Wool",
          "Green Wool",
          "Grindstone",
          "Hay Block",
          "Heart Of The Sea",
          "Heavy Core",
          "Honey Bottle",
          "Iron Axe",
          "Iron Block",
          "Iron Boots",
          "Iron Chestplate",
          "Iron Helmet",
          "Iron Hoe",
          "Iron Ingot",
          "Iron Leggings",
          "Iron Pickaxe",
          "Iron Shovel",
          "Iron Sword",
          "Leather",
          "Leather Boots",
          "Leather Chestplate",
          "Leather Helmet",
          "Leather Horse Armor",
          "Leather Leggings",
          "Lectern",
          "Light Blue Wool",
          "Light Gray Wool",
          "Lime Wool",
          "Lodestone",
          "Loom",
          "Mace",
          "Magenta Wool",
          "Map",
          "Melon",
          "Melon Seeds",
          "Music Disc 11",
          "Music Disc 13",
          "Music Disc 5",
          "Music Disc Blocks",
          "Music Disc Cat",
          "Music Disc Chirp",
          "Music Disc Creator",
          "Music Disc Creator Music Box",
          "Music Disc Far",
          "Music Disc Mall",
          "Music Disc Mellohi",
          "Music Disc Otherside",
          "Music Disc Pigstep",
          "Music Disc Precipice",
          "Music Disc Relic",
          "Music Disc Stal",
          "Music Disc Strad",
          "Music Disc Wait",
          "Music Disc Ward",
          "Mutton",
          "Name Tag",
          "Nautilus Shell",
          "Netherite Ingot",
          "Netherite Scrap",
          "Netherite Upgrade Smithing Template",
          "Orange Wool",
          "Pink Wool",
          "Porkchop",
          "Potato",
          "Pumpkin",
          "Pumpkin Pie",
          "Pumpkin Seeds",
          "Purple Wool",
          "Quartz",
          "Quartz Block",
          "Rabbit",
          "Rabbit Hide",
          "Rabbit Stew",
          "Recovery Compass",
          "Red Wool",
          "Saddle",
          "Salmon",
          "Shears",
          "Shield",
          "Smithing Table",
          "Smoker",
          "Smooth Stone",
          "Spectral Arrow",
          "Spyglass",
          "Stone",
          "Stone Bricks",
          "Stonecutter",
          "Target",
          "Totem Of Undying",
          "Tripwire Hook",
          "Tropical Fish",
          "Water Bucket",
          "Wheat",
          "Wheat Seeds",
          "White Wool",
          "Writable Book",
          "Written Book",
          "Yellow Wool"
        ],
        "examples": [
          {
            "id": "builtin.global.loved_valuables",
            "professions": [],
            "items": [
              "Emerald",
              "Diamond",
              "Gold Ingot",
              "Golden Apple",
              "Enchanted Golden Apple",
              "Experience Bottle"
            ]
          },
          {
            "id": "builtin.global.loved_emerald_block",
            "professions": [],
            "items": [
              "Emerald Block"
            ]
          },
          {
            "id": "builtin.armorer.loved",
            "professions": [
              "Armorer"
            ],
            "items": [
              "Iron Ingot",
              "Iron Block",
              "Shield",
              "Chainmail Helmet",
              "Chainmail Chestplate",
              "Chainmail Leggings",
              "Chainmail Boots",
              "Iron Helmet",
              "Iron Chestplate",
              "Iron Leggings",
              "Iron Boots",
              "Diamond Helmet",
              "Diamond Chestplate",
              "Diamond Leggings"
            ]
          },
          {
            "id": "builtin.butcher.loved",
            "professions": [
              "Butcher"
            ],
            "items": [
              "Beef",
              "Porkchop",
              "Mutton",
              "Chicken",
              "Rabbit",
              "Cooked Beef",
              "Cooked Porkchop",
              "Cooked Mutton",
              "Cooked Chicken",
              "Cooked Rabbit",
              "Rabbit Stew",
              "Smoker"
            ]
          },
          {
            "id": "builtin.cartographer.loved",
            "professions": [
              "Cartographer"
            ],
            "items": [
              "Map",
              "Filled Map",
              "Compass",
              "Recovery Compass",
              "Clock",
              "Cartography Table",
              "Spyglass",
              "Lodestone"
            ]
          },
          {
            "id": "builtin.cleric.loved",
            "professions": [
              "Cleric"
            ],
            "items": [
              "Amethyst Shard",
              "Glowstone Dust",
              "Glowstone",
              "Experience Bottle",
              "Ender Pearl",
              "Blaze Rod",
              "Brewing Stand",
              "Ghast Tear",
              "Dragon Breath",
              "Echo Shard",
              "Totem Of Undying"
            ]
          },
          {
            "id": "builtin.farmer.loved",
            "professions": [
              "Farmer"
            ],
            "items": [
              "Wheat Seeds",
              "Beetroot Seeds",
              "Pumpkin Seeds",
              "Melon Seeds",
              "Carrot",
              "Potato",
              "Beetroot",
              "Wheat",
              "Pumpkin",
              "Melon",
              "Hay Block",
              "Composter",
              "Golden Carrot",
              "Golden Apple"
            ]
          },
          {
            "id": "builtin.fisherman.loved",
            "professions": [
              "Fisherman"
            ],
            "items": [
              "Fishing Rod",
              "Cod",
              "Salmon",
              "Tropical Fish",
              "Cooked Cod",
              "Cooked Salmon",
              "Nautilus Shell",
              "Heart Of The Sea",
              "Barrel",
              "Bucket",
              "Water Bucket"
            ]
          }
        ]
      },
      {
        "reaction": "Liked",
        "count": 16,
        "allItems": [
          "Acacia Planks",
          "Allium",
          "Andesite",
          "Anvil",
          "Apple",
          "Armor Stand",
          "Arrow",
          "Azure Bluet",
          "Baked Potato",
          "Bamboo",
          "Barrel",
          "Birch Planks",
          "Black Dye",
          "Blast Furnace",
          "Blaze Powder",
          "Blue Banner",
          "Blue Candle",
          "Blue Carpet",
          "Blue Dye",
          "Blue Orchid",
          "Boat",
          "Bone Meal",
          "Book",
          "Bow",
          "Bowl",
          "Bread",
          "Brown Dye",
          "Brown Mushroom",
          "Brown Terracotta",
          "Brush",
          "Cactus",
          "Cake",
          "Calcite",
          "Campfire",
          "Candle",
          "Carrot",
          "Charcoal",
          "Cherry Planks",
          "Chest",
          "Chipped Anvil",
          "Clock",
          "Coal",
          "Coarse Dirt",
          "Cobbled Deepslate",
          "Cocoa Beans",
          "Compass",
          "Cookie",
          "Copper Block",
          "Copper Ingot",
          "Cornflower",
          "Crafting Table",
          "Crossbow",
          "Cyan Dye",
          "Damaged Anvil",
          "Dandelion",
          "Dark Oak Planks",
          "Deepslate",
          "Deepslate Emerald Ore",
          "Diamond",
          "Diorite",
          "Dirt",
          "Dried Kelp",
          "Egg",
          "Emerald Ore",
          "Feather",
          "Filled Map",
          "Flint",
          "Flower Pot",
          "Glass Bottle",
          "Glow Berries",
          "Glow Ink Sac",
          "Glow Item Frame",
          "Gold Ingot",
          "Golden Carrot",
          "Granite",
          "Gray Dye",
          "Green Banner",
          "Green Dye",
          "Grindstone",
          "Hay Block",
          "Honey Bottle",
          "Honeycomb",
          "Ink Sac",
          "Iron Ingot",
          "Iron Nugget",
          "Item Frame",
          "Jungle Planks",
          "Kelp",
          "Lantern",
          "Lapis Lazuli",
          "Lead",
          "Light Blue Banner",
          "Light Blue Dye",
          "Light Gray Candle",
          "Light Gray Dye",
          "Lilac",
          "Lily Of The Valley",
          "Lily Pad",
          "Lime Dye",
          "Magenta Dye",
          "Magma Cream",
          "Mangrove Planks",
          "Map",
          "Melon Slice",
          "Milk Bucket",
          "Mud",
          "Mud Bricks",
          "Name Tag",
          "Nether Wart",
          "Oak Boat",
          "Oak Planks",
          "Orange Banner",
          "Orange Dye",
          "Orange Terracotta",
          "Oxeye Daisy",
          "Painting",
          "Paper",
          "Peony",
          "Pink Dye",
          "Pitcher Pod",
          "Polished Andesite",
          "Polished Deepslate",
          "Polished Diorite",
          "Polished Granite",
          "Polished Tuff",
          "Poppy",
          "Powder Snow Bucket",
          "Prismarine Crystals",
          "Prismarine Shard",
          "Pumpkin Pie",
          "Purple Dye",
          "Rabbit Foot",
          "Red Banner",
          "Red Candle",
          "Red Carpet",
          "Red Dye",
          "Red Mushroom",
          "Red Sandstone",
          "Red Terracotta",
          "Redstone",
          "Rooted Dirt",
          "Rose Bush",
          "Sandstone",
          "Sculk",
          "Sculk Catalyst",
          "Sculk Sensor",
          "Sculk Shrieker",
          "Sea Pickle",
          "Seagrass",
          "Slime Ball",
          "Smithing Table",
          "Snowball",
          "Soul Lantern",
          "Soul Torch",
          "Spectral Arrow",
          "Sponge",
          "Spruce Boat",
          "Spruce Planks",
          "Stick",
          "Stone Axe",
          "Stone Hoe",
          "Stone Pickaxe",
          "Stone Shovel",
          "String",
          "Sugar Cane",
          "Sunflower",
          "Sweet Berries",
          "Terracotta",
          "Torch",
          "Torchflower Seeds",
          "Trident",
          "Tuff",
          "Turtle Egg",
          "Turtle Helmet",
          "Turtle Scute",
          "Water Bucket",
          "Wet Sponge",
          "Wheat",
          "White Banner",
          "White Candle",
          "White Carpet",
          "White Dye",
          "White Terracotta",
          "Writable Book",
          "Yellow Banner",
          "Yellow Dye"
        ],
        "examples": [
          {
            "id": "builtin.global.liked_safe_foods",
            "professions": [],
            "items": [
              "Bread",
              "Apple",
              "Cookie",
              "Cake",
              "Pumpkin Pie",
              "Honey Bottle",
              "Sweet Berries",
              "Glow Berries",
              "Milk Bucket",
              "Baked Potato",
              "Carrot",
              "Golden Carrot",
              "Melon Slice"
            ]
          },
          {
            "id": "builtin.global.liked_village_goods",
            "professions": [],
            "items": [
              "Emerald Ore",
              "Deepslate Emerald Ore",
              "Lantern",
              "Flower Pot",
              "Torch",
              "Campfire",
              "Barrel",
              "Chest",
              "Crafting Table"
            ]
          },
          {
            "id": "builtin.armorer.liked",
            "professions": [
              "Armorer"
            ],
            "items": [
              "Coal",
              "Charcoal",
              "Blast Furnace",
              "Anvil",
              "Chipped Anvil",
              "Damaged Anvil",
              "Iron Nugget",
              "Diamond",
              "Turtle Helmet",
              "Armor Stand",
              "Copper Ingot",
              "Copper Block"
            ]
          },
          {
            "id": "builtin.butcher.liked",
            "professions": [
              "Butcher"
            ],
            "items": [
              "Charcoal",
              "Coal",
              "Egg",
              "Wheat",
              "Hay Block",
              "Bowl",
              "Campfire",
              "Sweet Berries",
              "Honey Bottle"
            ]
          },
          {
            "id": "builtin.cartographer.liked",
            "professions": [
              "Cartographer"
            ],
            "items": [
              "Paper",
              "Feather",
              "Ink Sac",
              "Glow Ink Sac",
              "Book",
              "Writable Book",
              "White Banner",
              "Blue Banner",
              "Red Banner",
              "Green Banner",
              "Orange Banner",
              "Light Blue Banner",
              "Yellow Banner"
            ]
          },
          {
            "id": "builtin.cleric.liked",
            "professions": [
              "Cleric"
            ],
            "items": [
              "Redstone",
              "Lapis Lazuli",
              "Blaze Powder",
              "Nether Wart",
              "Glass Bottle",
              "Honey Bottle",
              "Rabbit Foot",
              "Magma Cream",
              "Sculk",
              "Sculk Sensor",
              "Sculk Catalyst",
              "Sculk Shrieker",
              "Soul Lantern",
              "Soul Torch"
            ]
          },
          {
            "id": "builtin.farmer.liked",
            "professions": [
              "Farmer"
            ],
            "items": [
              "Melon Slice",
              "Apple",
              "Sweet Berries",
              "Glow Berries",
              "Sugar Cane",
              "Cocoa Beans",
              "Cactus",
              "Bamboo",
              "Kelp",
              "Dried Kelp",
              "Brown Mushroom",
              "Red Mushroom",
              "Torchflower Seeds",
              "Pitcher Pod"
            ]
          },
          {
            "id": "builtin.fisherman.liked",
            "professions": [
              "Fisherman"
            ],
            "items": [
              "String",
              "Kelp",
              "Dried Kelp",
              "Seagrass",
              "Sea Pickle",
              "Lily Pad",
              "Prismarine Shard",
              "Prismarine Crystals",
              "Sponge",
              "Wet Sponge",
              "Turtle Scute",
              "Turtle Egg",
              "Boat",
              "Oak Boat"
            ]
          }
        ]
      },
      {
        "reaction": "Neutral",
        "count": 0,
        "allItems": [],
        "examples": []
      },
      {
        "reaction": "Disliked",
        "count": 15,
        "allItems": [
          "Bone",
          "Bone Meal",
          "Cobweb",
          "Dead Bush",
          "Fermented Spider Eye",
          "Fire Charge",
          "Firework Rocket",
          "Gravel",
          "Gunpowder",
          "Leather Boots",
          "Leather Chestplate",
          "Leather Helmet",
          "Leather Leggings",
          "Magma Cream",
          "Phantom Membrane",
          "Poisonous Potato",
          "Pufferfish",
          "Rotten Flesh",
          "Sand",
          "Shears",
          "Shield",
          "Slime Ball",
          "Spider Eye",
          "Suspicious Stew",
          "Tnt",
          "Wither Rose",
          "Wooden Axe",
          "Wooden Hoe",
          "Wooden Pickaxe",
          "Wooden Shovel",
          "Wooden Sword"
        ],
        "examples": [
          {
            "id": "builtin.global.disliked_unsafe",
            "professions": [],
            "items": [
              "Bone",
              "Bone Meal",
              "Dead Bush",
              "Pufferfish",
              "Phantom Membrane",
              "Magma Cream",
              "Slime Ball",
              "Firework Rocket",
              "Suspicious Stew",
              "Cobweb",
              "Poisonous Potato"
            ]
          },
          {
            "id": "builtin.armorer.disliked",
            "professions": [
              "Armorer"
            ],
            "items": [
              "Leather Chestplate",
              "Leather Helmet",
              "Leather Leggings",
              "Leather Boots",
              "Wooden Sword",
              "Dead Bush",
              "Rotten Flesh"
            ]
          },
          {
            "id": "builtin.butcher.disliked",
            "professions": [
              "Butcher"
            ],
            "items": [
              "Bone",
              "Bone Meal",
              "Wither Rose",
              "Pufferfish",
              "Magma Cream"
            ]
          },
          {
            "id": "builtin.cartographer.disliked",
            "professions": [
              "Cartographer"
            ],
            "items": [
              "Dead Bush",
              "Suspicious Stew",
              "Rotten Flesh",
              "Cobweb",
              "Phantom Membrane"
            ]
          },
          {
            "id": "builtin.cleric.disliked",
            "professions": [
              "Cleric"
            ],
            "items": [
              "Rotten Flesh",
              "Fermented Spider Eye",
              "Poisonous Potato",
              "Dead Bush",
              "Spider Eye"
            ]
          },
          {
            "id": "builtin.farmer.disliked",
            "professions": [
              "Farmer"
            ],
            "items": [
              "Rotten Flesh",
              "Spider Eye",
              "Fermented Spider Eye",
              "Gunpowder",
              "Tnt"
            ]
          },
          {
            "id": "builtin.fisherman.disliked",
            "professions": [
              "Fisherman"
            ],
            "items": [
              "Pufferfish",
              "Rotten Flesh",
              "Phantom Membrane",
              "Magma Cream",
              "Fire Charge"
            ]
          },
          {
            "id": "builtin.fletcher.disliked",
            "professions": [
              "Fletcher"
            ],
            "items": [
              "Shield",
              "Rotten Flesh",
              "Wither Rose",
              "Dead Bush"
            ]
          }
        ]
      },
      {
        "reaction": "Hated",
        "count": 15,
        "allItems": [
          "Dead Bush",
          "Fermented Spider Eye",
          "Fire Charge",
          "Flint And Steel",
          "Gunpowder",
          "Lava Bucket",
          "Ominous Bottle",
          "Ominous Trial Key",
          "Poisonous Potato",
          "Rotten Flesh",
          "Spider Eye",
          "Suspicious Stew",
          "Tnt",
          "Tnt Minecart",
          "Trial Key",
          "Wither Rose",
          "Wither Skeleton Skull"
        ],
        "examples": [
          {
            "id": "builtin.global.hated_hazards",
            "professions": [],
            "items": [
              "Rotten Flesh",
              "Poisonous Potato",
              "Spider Eye",
              "Fermented Spider Eye",
              "Gunpowder",
              "Tnt",
              "Tnt Minecart",
              "Fire Charge",
              "Flint And Steel",
              "Lava Bucket",
              "Wither Rose",
              "Wither Skeleton Skull",
              "Ominous Bottle",
              "Trial Key"
            ]
          },
          {
            "id": "builtin.armorer.hated",
            "professions": [
              "Armorer"
            ],
            "items": [
              "Tnt",
              "Tnt Minecart",
              "Fire Charge",
              "Lava Bucket",
              "Flint And Steel"
            ]
          },
          {
            "id": "builtin.butcher.hated",
            "professions": [
              "Butcher"
            ],
            "items": [
              "Rotten Flesh",
              "Poisonous Potato",
              "Spider Eye",
              "Fermented Spider Eye",
              "Suspicious Stew"
            ]
          },
          {
            "id": "builtin.cartographer.hated",
            "professions": [
              "Cartographer"
            ],
            "items": [
              "Tnt",
              "Tnt Minecart",
              "Flint And Steel",
              "Fire Charge",
              "Lava Bucket"
            ]
          },
          {
            "id": "builtin.cleric.hated",
            "professions": [
              "Cleric"
            ],
            "items": [
              "Tnt",
              "Tnt Minecart",
              "Wither Skeleton Skull",
              "Wither Rose",
              "Ominous Bottle"
            ]
          },
          {
            "id": "builtin.farmer.hated",
            "professions": [
              "Farmer"
            ],
            "items": [
              "Poisonous Potato",
              "Dead Bush",
              "Wither Rose",
              "Lava Bucket"
            ]
          },
          {
            "id": "builtin.fisherman.hated",
            "professions": [
              "Fisherman"
            ],
            "items": [
              "Tnt",
              "Tnt Minecart",
              "Lava Bucket",
              "Flint And Steel"
            ]
          },
          {
            "id": "builtin.fletcher.hated",
            "professions": [
              "Fletcher"
            ],
            "items": [
              "Tnt",
              "Tnt Minecart",
              "Fire Charge",
              "Lava Bucket"
            ]
          }
        ]
      }
    ],
    "professionPreferences": [
      {
        "profession": "Armorer",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Chainmail Boots",
              "Chainmail Chestplate",
              "Chainmail Helmet",
              "Chainmail Leggings",
              "Diamond Boots",
              "Diamond Chestplate",
              "Diamond Helmet",
              "Diamond Leggings",
              "Iron Block",
              "Iron Boots",
              "Iron Chestplate",
              "Iron Helmet",
              "Iron Ingot",
              "Iron Leggings",
              "Netherite Ingot",
              "Netherite Scrap",
              "Shield"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Anvil",
              "Armor Stand",
              "Blast Furnace",
              "Charcoal",
              "Chipped Anvil",
              "Coal",
              "Copper Block",
              "Copper Ingot",
              "Damaged Anvil",
              "Diamond",
              "Iron Nugget",
              "Turtle Helmet"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Flint And Steel",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Dead Bush",
              "Leather Boots",
              "Leather Chestplate",
              "Leather Helmet",
              "Leather Leggings",
              "Rotten Flesh",
              "Wooden Sword"
            ]
          }
        ]
      },
      {
        "profession": "Butcher",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Beef",
              "Chicken",
              "Cooked Beef",
              "Cooked Chicken",
              "Cooked Mutton",
              "Cooked Porkchop",
              "Cooked Rabbit",
              "Mutton",
              "Porkchop",
              "Rabbit",
              "Rabbit Stew",
              "Smoker"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Bowl",
              "Campfire",
              "Charcoal",
              "Coal",
              "Egg",
              "Hay Block",
              "Honey Bottle",
              "Sweet Berries",
              "Wheat"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fermented Spider Eye",
              "Poisonous Potato",
              "Rotten Flesh",
              "Spider Eye",
              "Suspicious Stew"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Bone",
              "Bone Meal",
              "Magma Cream",
              "Pufferfish",
              "Wither Rose"
            ]
          }
        ]
      },
      {
        "profession": "Cartographer",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Cartography Table",
              "Clock",
              "Compass",
              "Filled Map",
              "Lodestone",
              "Map",
              "Recovery Compass",
              "Spyglass"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Blue Banner",
              "Book",
              "Feather",
              "Glow Ink Sac",
              "Green Banner",
              "Ink Sac",
              "Light Blue Banner",
              "Orange Banner",
              "Paper",
              "Red Banner",
              "White Banner",
              "Writable Book",
              "Yellow Banner"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Flint And Steel",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Cobweb",
              "Dead Bush",
              "Phantom Membrane",
              "Rotten Flesh",
              "Suspicious Stew"
            ]
          }
        ]
      },
      {
        "profession": "Cleric",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Amethyst Shard",
              "Blaze Rod",
              "Brewing Stand",
              "Dragon Breath",
              "Echo Shard",
              "Ender Pearl",
              "Experience Bottle",
              "Ghast Tear",
              "Glowstone",
              "Glowstone Dust",
              "Totem Of Undying"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Blaze Powder",
              "Glass Bottle",
              "Honey Bottle",
              "Lapis Lazuli",
              "Magma Cream",
              "Nether Wart",
              "Rabbit Foot",
              "Redstone",
              "Sculk",
              "Sculk Catalyst",
              "Sculk Sensor",
              "Sculk Shrieker",
              "Soul Lantern",
              "Soul Torch"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Ominous Bottle",
              "Tnt",
              "Tnt Minecart",
              "Wither Rose",
              "Wither Skeleton Skull"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Dead Bush",
              "Fermented Spider Eye",
              "Poisonous Potato",
              "Rotten Flesh",
              "Spider Eye"
            ]
          }
        ]
      },
      {
        "profession": "Farmer",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Beetroot",
              "Beetroot Seeds",
              "Carrot",
              "Composter",
              "Golden Apple",
              "Golden Carrot",
              "Hay Block",
              "Melon",
              "Melon Seeds",
              "Potato",
              "Pumpkin",
              "Pumpkin Seeds",
              "Wheat",
              "Wheat Seeds"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Apple",
              "Bamboo",
              "Bone Meal",
              "Brown Mushroom",
              "Cactus",
              "Coarse Dirt",
              "Cocoa Beans",
              "Dirt",
              "Dried Kelp",
              "Glow Berries",
              "Kelp",
              "Melon Slice",
              "Mud",
              "Pitcher Pod",
              "Red Mushroom",
              "Rooted Dirt",
              "Sugar Cane",
              "Sweet Berries",
              "Torchflower Seeds"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Dead Bush",
              "Lava Bucket",
              "Poisonous Potato",
              "Wither Rose"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Fermented Spider Eye",
              "Gunpowder",
              "Rotten Flesh",
              "Spider Eye",
              "Tnt"
            ]
          }
        ]
      },
      {
        "profession": "Fisherman",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Barrel",
              "Bucket",
              "Cod",
              "Cooked Cod",
              "Cooked Salmon",
              "Fishing Rod",
              "Heart Of The Sea",
              "Nautilus Shell",
              "Salmon",
              "Tropical Fish",
              "Water Bucket"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Boat",
              "Dried Kelp",
              "Kelp",
              "Lily Pad",
              "Oak Boat",
              "Prismarine Crystals",
              "Prismarine Shard",
              "Sea Pickle",
              "Seagrass",
              "Sponge",
              "Spruce Boat",
              "String",
              "Turtle Egg",
              "Turtle Scute",
              "Wet Sponge"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Flint And Steel",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Fire Charge",
              "Magma Cream",
              "Phantom Membrane",
              "Pufferfish",
              "Rotten Flesh"
            ]
          }
        ]
      },
      {
        "profession": "Fletcher",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Arrow",
              "Bow",
              "Crossbow",
              "Feather",
              "Fletching Table",
              "Flint",
              "Spectral Arrow",
              "Target",
              "Tripwire Hook"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Acacia Planks",
              "Bamboo",
              "Birch Planks",
              "Cherry Planks",
              "Copper Ingot",
              "Dark Oak Planks",
              "Honeycomb",
              "Iron Ingot",
              "Jungle Planks",
              "Mangrove Planks",
              "Oak Planks",
              "Redstone",
              "Slime Ball",
              "Spruce Planks",
              "Stick",
              "String"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Dead Bush",
              "Rotten Flesh",
              "Shield",
              "Wither Rose"
            ]
          }
        ]
      },
      {
        "profession": "Leatherworker",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Bundle",
              "Cauldron",
              "Leather",
              "Leather Boots",
              "Leather Chestplate",
              "Leather Helmet",
              "Leather Horse Armor",
              "Leather Leggings",
              "Rabbit Hide",
              "Saddle"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Black Dye",
              "Blue Dye",
              "Brown Dye",
              "Brush",
              "Cyan Dye",
              "Green Dye",
              "Lead",
              "Name Tag",
              "Orange Dye",
              "Powder Snow Bucket",
              "Purple Dye",
              "Red Dye",
              "Water Bucket",
              "White Dye",
              "Yellow Dye"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Flint And Steel",
              "Lava Bucket",
              "Tnt"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Bone",
              "Bone Meal",
              "Poisonous Potato",
              "Rotten Flesh",
              "Wither Rose"
            ]
          }
        ]
      },
      {
        "profession": "Librarian",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Book",
              "Bookshelf",
              "Chiseled Bookshelf",
              "Enchanted Book",
              "Lectern",
              "Name Tag",
              "Recovery Compass",
              "Writable Book",
              "Written Book"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Blue Candle",
              "Candle",
              "Clock",
              "Compass",
              "Feather",
              "Filled Map",
              "Glow Ink Sac",
              "Ink Sac",
              "Lantern",
              "Light Gray Candle",
              "Map",
              "Paper",
              "Red Candle",
              "White Candle"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Flint And Steel",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Cobweb",
              "Dead Bush",
              "Poisonous Potato",
              "Rotten Flesh",
              "Suspicious Stew"
            ]
          }
        ]
      },
      {
        "profession": "Mason",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Brick",
              "Bricks",
              "Chiseled Stone Bricks",
              "Clay",
              "Clay Ball",
              "Cut Copper",
              "Quartz",
              "Quartz Block",
              "Smooth Stone",
              "Stone",
              "Stone Bricks",
              "Stonecutter"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Andesite",
              "Brown Terracotta",
              "Calcite",
              "Cobbled Deepslate",
              "Deepslate",
              "Diorite",
              "Granite",
              "Mud Bricks",
              "Orange Terracotta",
              "Polished Andesite",
              "Polished Deepslate",
              "Polished Diorite",
              "Polished Granite",
              "Polished Tuff",
              "Red Sandstone",
              "Red Terracotta",
              "Sandstone",
              "Terracotta",
              "Tuff",
              "White Terracotta"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Dead Bush",
              "Gravel",
              "Rotten Flesh",
              "Sand",
              "Slime Ball"
            ]
          }
        ]
      },
      {
        "profession": "Shepherd",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Black Wool",
              "Blue Wool",
              "Brown Wool",
              "Cyan Wool",
              "Gray Wool",
              "Green Wool",
              "Light Blue Wool",
              "Light Gray Wool",
              "Lime Wool",
              "Loom",
              "Magenta Wool",
              "Orange Wool",
              "Pink Wool",
              "Purple Wool",
              "Red Wool",
              "Shears",
              "White Wool",
              "Yellow Wool"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Black Dye",
              "Blue Carpet",
              "Blue Dye",
              "Brown Dye",
              "Cyan Dye",
              "Gray Dye",
              "Green Dye",
              "Lead",
              "Light Blue Dye",
              "Light Gray Dye",
              "Lime Dye",
              "Magenta Dye",
              "Orange Dye",
              "Pink Dye",
              "Purple Dye",
              "Red Carpet",
              "Red Dye",
              "White Carpet",
              "White Dye",
              "Yellow Dye"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Bone",
              "Dead Bush",
              "Rotten Flesh",
              "Shears",
              "Wither Rose"
            ]
          }
        ]
      },
      {
        "profession": "Toolsmith",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Anvil",
              "Diamond",
              "Diamond Axe",
              "Diamond Hoe",
              "Diamond Pickaxe",
              "Diamond Shovel",
              "Iron Axe",
              "Iron Block",
              "Iron Hoe",
              "Iron Ingot",
              "Iron Pickaxe",
              "Iron Shovel",
              "Netherite Upgrade Smithing Template",
              "Smithing Table"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Brush",
              "Charcoal",
              "Coal",
              "Copper Ingot",
              "Flint",
              "Gold Ingot",
              "Grindstone",
              "Iron Nugget",
              "Stone Axe",
              "Stone Hoe",
              "Stone Pickaxe",
              "Stone Shovel"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Dead Bush",
              "Rotten Flesh",
              "Wooden Axe",
              "Wooden Hoe",
              "Wooden Pickaxe",
              "Wooden Shovel"
            ]
          }
        ]
      },
      {
        "profession": "Weaponsmith",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Diamond",
              "Diamond Axe",
              "Diamond Sword",
              "Grindstone",
              "Heavy Core",
              "Iron Axe",
              "Iron Block",
              "Iron Ingot",
              "Iron Sword",
              "Mace",
              "Netherite Ingot",
              "Shield"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Anvil",
              "Arrow",
              "Bow",
              "Charcoal",
              "Coal",
              "Copper Ingot",
              "Crossbow",
              "Flint",
              "Gold Ingot",
              "Smithing Table",
              "Spectral Arrow",
              "Trident"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Fire Charge",
              "Flint And Steel",
              "Lava Bucket",
              "Tnt",
              "Tnt Minecart"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Poisonous Potato",
              "Rotten Flesh",
              "Slime Ball",
              "Wooden Axe",
              "Wooden Sword"
            ]
          }
        ]
      },
      {
        "profession": "Nitwit",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Apple",
              "Cake",
              "Cookie",
              "Honey Bottle",
              "Music Disc 11",
              "Music Disc 13",
              "Music Disc 5",
              "Music Disc Blocks",
              "Music Disc Cat",
              "Music Disc Chirp",
              "Music Disc Creator",
              "Music Disc Creator Music Box",
              "Music Disc Far",
              "Music Disc Mall",
              "Music Disc Mellohi",
              "Music Disc Otherside",
              "Music Disc Pigstep",
              "Music Disc Precipice",
              "Music Disc Relic",
              "Music Disc Stal",
              "Music Disc Strad",
              "Music Disc Wait",
              "Music Disc Ward",
              "Pumpkin Pie"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Allium",
              "Azure Bluet",
              "Blue Orchid",
              "Cornflower",
              "Dandelion",
              "Flower Pot",
              "Glow Item Frame",
              "Item Frame",
              "Lilac",
              "Lily Of The Valley",
              "Oxeye Daisy",
              "Painting",
              "Peony",
              "Poppy",
              "Rose Bush",
              "Slime Ball",
              "Snowball",
              "Sunflower"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Lava Bucket",
              "Poisonous Potato",
              "Tnt",
              "Tnt Minecart",
              "Wither Skeleton Skull"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Bone",
              "Dead Bush",
              "Fermented Spider Eye",
              "Rotten Flesh",
              "Spider Eye"
            ]
          }
        ]
      },
      {
        "profession": "None",
        "entries": [
          {
            "reaction": "Loved",
            "items": [
              "Apple",
              "Cake",
              "Cookie",
              "Honey Bottle",
              "Music Disc 11",
              "Music Disc 13",
              "Music Disc 5",
              "Music Disc Blocks",
              "Music Disc Cat",
              "Music Disc Chirp",
              "Music Disc Creator",
              "Music Disc Creator Music Box",
              "Music Disc Far",
              "Music Disc Mall",
              "Music Disc Mellohi",
              "Music Disc Otherside",
              "Music Disc Pigstep",
              "Music Disc Precipice",
              "Music Disc Relic",
              "Music Disc Stal",
              "Music Disc Strad",
              "Music Disc Wait",
              "Music Disc Ward",
              "Pumpkin Pie"
            ]
          },
          {
            "reaction": "Liked",
            "items": [
              "Allium",
              "Azure Bluet",
              "Blue Orchid",
              "Cornflower",
              "Dandelion",
              "Flower Pot",
              "Glow Item Frame",
              "Item Frame",
              "Lilac",
              "Lily Of The Valley",
              "Oxeye Daisy",
              "Painting",
              "Peony",
              "Poppy",
              "Rose Bush",
              "Slime Ball",
              "Snowball",
              "Sunflower"
            ]
          },
          {
            "reaction": "Hated",
            "items": [
              "Lava Bucket",
              "Poisonous Potato",
              "Tnt",
              "Tnt Minecart",
              "Wither Skeleton Skull"
            ]
          },
          {
            "reaction": "Disliked",
            "items": [
              "Bone",
              "Dead Bush",
              "Fermented Spider Eye",
              "Rotten Flesh",
              "Spider Eye"
            ]
          }
        ]
      }
    ],
    "rewards": [
      {
        "professions": [
          "Farmer"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Bread",
        "count": "2-5"
      },
      {
        "professions": [
          "Farmer"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Golden Carrot",
        "count": "2-6"
      },
      {
        "professions": [
          "Fisherman"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Cooked Cod",
        "count": "1-3"
      },
      {
        "professions": [
          "Fisherman"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Cooked Salmon",
        "count": "2-5"
      },
      {
        "professions": [
          "Librarian"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Book",
        "count": "1-3"
      },
      {
        "professions": [
          "Librarian"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Enchanted Book",
        "count": "1"
      },
      {
        "professions": [
          "Cleric"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Redstone",
        "count": "2-5"
      },
      {
        "professions": [
          "Cleric"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Experience Bottle",
        "count": "1-3"
      },
      {
        "professions": [
          "Fletcher"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Arrow",
        "count": "8-16"
      },
      {
        "professions": [
          "Fletcher"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Spectral Arrow",
        "count": "4-10"
      },
      {
        "professions": [
          "Armorer"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Iron Ingot",
        "count": "1-3"
      },
      {
        "professions": [
          "Armorer"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Shield",
        "count": "1"
      },
      {
        "professions": [
          "Toolsmith"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Iron Ingot",
        "count": "1-3"
      },
      {
        "professions": [
          "Toolsmith"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Diamond",
        "count": "1-2"
      },
      {
        "professions": [
          "Weaponsmith"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Coal",
        "count": "2-5"
      },
      {
        "professions": [
          "Weaponsmith"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Iron Sword",
        "count": "1"
      },
      {
        "professions": [
          "Cartographer"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Map",
        "count": "1"
      },
      {
        "professions": [
          "Cartographer"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Compass",
        "count": "1"
      },
      {
        "professions": [
          "Shepherd"
        ],
        "levels": [
          "Revered"
        ],
        "item": "White Carpet",
        "count": "2-6"
      },
      {
        "professions": [
          "Shepherd"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "White Wool",
        "count": "4-10"
      },
      {
        "professions": [
          "Butcher"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Cooked Porkchop",
        "count": "1-3"
      },
      {
        "professions": [
          "Butcher"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Cooked Beef",
        "count": "2-5"
      },
      {
        "professions": [
          "Leatherworker"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Leather",
        "count": "2-5"
      },
      {
        "professions": [
          "Leatherworker"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Saddle",
        "count": "1"
      },
      {
        "professions": [
          "Mason"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Brick",
        "count": "4-10"
      },
      {
        "professions": [
          "Mason"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Quartz",
        "count": "4-12"
      },
      {
        "professions": [
          "Nitwit",
          "None"
        ],
        "levels": [
          "Revered"
        ],
        "item": "Cookie",
        "count": "1-4"
      },
      {
        "professions": [
          "Nitwit",
          "None"
        ],
        "levels": [
          "Royalty"
        ],
        "item": "Cake",
        "count": "1"
      },
      {
        "professions": [],
        "levels": [
          "Revered"
        ],
        "item": "Emerald",
        "count": "1-2"
      },
      {
        "professions": [],
        "levels": [
          "Royalty"
        ],
        "item": "Emerald",
        "count": "2-4"
      }
    ]
  },
  "pacification": [
    {
      "item": "Emerald",
      "min": 3,
      "max": 32,
      "name": "emeralds"
    }
  ],
  "skillTrades": [
    {
      "profession": "Armorer",
      "count": 3,
      "trades": [
        {
          "id": "villagerretaliation:armorer_low_shield",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "7 Emerald",
          "result": "1 Shield",
          "chance": 70,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:armorer_skilled_iron_chestplate",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "9 Emerald",
          "result": "1 Iron Chestplate",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:armorer_expert_diamond_chestplate",
          "rank": "Expert",
          "level": 5,
          "cost": "28 Emerald",
          "result": "1 Diamond Chestplate",
          "chance": 35,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Butcher",
      "count": 4,
      "trades": [
        {
          "id": "villagerretaliation:butcher_low_cooked_chicken",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "2 Emerald",
          "result": "6 Cooked Chicken",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:butcher_skilled_cooked_beef",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "4 Emerald",
          "result": "12 Cooked Beef",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:butcher_expert_cooked_porkchop",
          "rank": "Expert",
          "level": 4,
          "cost": "6 Emerald",
          "result": "16 Cooked Porkchop",
          "chance": 55,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:butcher_master_rabbit_stew",
          "rank": "Master",
          "level": 5,
          "cost": "5 Emerald",
          "result": "1 Rabbit Stew",
          "chance": 40,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Cartographer",
      "count": 6,
      "trades": [
        {
          "id": "villagerretaliation:cartographer_low_basic_maps",
          "rank": "Novice to Apprentice",
          "level": 1,
          "cost": "8 Emerald",
          "result": "1 Map",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cartographer_low_glass_panes",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "2 Emerald",
          "result": "8 Glass Pane",
          "chance": 75,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cartographer_skilled_map_bundle",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "10 Emerald",
          "result": "2 Map",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cartographer_expert_clock",
          "rank": "Expert",
          "level": 4,
          "cost": "12 Emerald",
          "result": "1 Clock",
          "chance": 45,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cartographer_skilled_compass",
          "rank": "Skilled to Expert",
          "level": 4,
          "cost": "7 Emerald",
          "result": "1 Compass",
          "chance": 65,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cartographer_master_recovery_compass",
          "rank": "Master",
          "level": 5,
          "cost": "24 Emerald",
          "result": "1 Recovery Compass",
          "chance": 28,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Cleric",
      "count": 6,
      "trades": [
        {
          "id": "villagerretaliation:cleric_low_redstone",
          "rank": "Novice to Apprentice",
          "level": 1,
          "cost": "2 Emerald",
          "result": "4 Redstone",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cleric_low_glistering_melon",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "3 Emerald",
          "result": "3 Glistering Melon Slice",
          "chance": 75,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cleric_skilled_glowstone",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "4 Emerald",
          "result": "4 Glowstone",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cleric_expert_ghast_tear",
          "rank": "Expert",
          "level": 4,
          "cost": "9 Emerald",
          "result": "1 Ghast Tear",
          "chance": 45,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cleric_skilled_blaze_powder",
          "rank": "Skilled to Expert",
          "level": 4,
          "cost": "6 Emerald",
          "result": "3 Blaze Powder",
          "chance": 55,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:cleric_master_golden_apple",
          "rank": "Master",
          "level": 5,
          "cost": "20 Emerald",
          "result": "1 Golden Apple",
          "chance": 30,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Farmer",
      "count": 7,
      "trades": [
        {
          "id": "villagerretaliation:farmer_low_basic_bread",
          "rank": "Novice to Apprentice",
          "level": 1,
          "cost": "2 Emerald",
          "result": "4 Bread",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:farmer_low_bone_meal",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "2 Emerald",
          "result": "10 Bone Meal",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:farmer_skilled_crop_bundle",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "3 Emerald",
          "result": "14 Carrot or Potato or Beetroot",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:farmer_skilled_iron_hoe",
          "rank": "Skilled to Expert",
          "level": 4,
          "cost": "10 Emerald",
          "result": "1 Iron Hoe",
          "chance": 55,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:farmer_expert_golden_carrots",
          "rank": "Expert",
          "level": 4,
          "cost": "6 Emerald",
          "result": "4 Golden Carrot",
          "chance": 55,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:farmer_master_diamond_hoe",
          "rank": "Master",
          "level": 5,
          "cost": "18 Emerald",
          "result": "1 Diamond Hoe",
          "chance": 32,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:farmer_master_suspicious_stew",
          "rank": "Master",
          "level": 5,
          "cost": "5 Emerald",
          "result": "1 Suspicious Stew",
          "chance": 45,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Fisherman",
      "count": 6,
      "trades": [
        {
          "id": "villagerretaliation:fisherman_low_cooked_cod",
          "rank": "Novice to Apprentice",
          "level": 1,
          "cost": "2 Emerald",
          "result": "8 Cooked Cod",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fisherman_low_string",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "2 Emerald",
          "result": "10 String",
          "chance": 75,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fisherman_skilled_salmon_bundle",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "3 Emerald",
          "result": "12 Salmon or Cooked Salmon",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fisherman_skilled_fishing_rod",
          "rank": "Skilled to Expert",
          "level": 4,
          "cost": "8 Emerald",
          "result": "1 Fishing Rod",
          "chance": 65,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fisherman_expert_enchanted_rod",
          "rank": "Expert",
          "level": 4,
          "cost": "12 Emerald",
          "result": "1 Fishing Rod",
          "chance": 45,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fisherman_master_nautilus_shell",
          "rank": "Master",
          "level": 5,
          "cost": "14 Emerald",
          "result": "1 Nautilus Shell",
          "chance": 30,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Fletcher",
      "count": 6,
      "trades": [
        {
          "id": "villagerretaliation:fletcher_low_arrows",
          "rank": "Novice to Apprentice",
          "level": 1,
          "cost": "2 Emerald",
          "result": "16 Arrow",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fletcher_low_basic_bow",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "7 Emerald",
          "result": "1 Bow",
          "chance": 55,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fletcher_skilled_ammo_bundle",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "3 Emerald",
          "result": "32 Arrow",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fletcher_expert_enchanted_bowcraft",
          "rank": "Expert",
          "level": 4,
          "cost": "16 Emerald",
          "result": "1 Bow or Crossbow",
          "chance": 45,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fletcher_skilled_crossbow",
          "rank": "Skilled to Expert",
          "level": 4,
          "cost": "12 Emerald",
          "result": "1 Crossbow",
          "chance": 65,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:fletcher_master_spectral_arrows",
          "rank": "Master",
          "level": 5,
          "cost": "6 Emerald",
          "result": "16 Spectral Arrow",
          "chance": 40,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Leatherworker",
      "count": 4,
      "trades": [
        {
          "id": "villagerretaliation:leatherworker_low_leather",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "3 Emerald",
          "result": "8 Leather",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:leatherworker_skilled_leather_chestplate",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "8 Emerald",
          "result": "1 Leather Chestplate",
          "chance": 70,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:leatherworker_expert_reinforced_leather",
          "rank": "Expert",
          "level": 4,
          "cost": "12 Emerald",
          "result": "1 Leather Chestplate",
          "chance": 45,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:leatherworker_master_saddle",
          "rank": "Master",
          "level": 5,
          "cost": "14 Emerald",
          "result": "1 Saddle",
          "chance": 30,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Librarian",
      "count": 6,
      "trades": [
        {
          "id": "villagerretaliation:librarian_low_books",
          "rank": "Novice to Apprentice",
          "level": 1,
          "cost": "2 Emerald",
          "result": "3 Book",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:librarian_low_bookshelf",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "4 Emerald",
          "result": "2 Bookshelf",
          "chance": 75,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:librarian_skilled_bookshelves",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "5 Emerald",
          "result": "4 Bookshelf",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:librarian_skilled_workshop_book",
          "rank": "Skilled to Expert",
          "level": 4,
          "cost": "16 Emerald",
          "result": "1 Enchanted Book",
          "chance": 50,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:librarian_expert_reference_book",
          "rank": "Expert",
          "level": 4,
          "cost": "22 Emerald",
          "result": "1 Enchanted Book",
          "chance": 45,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:librarian_master_specialty_book",
          "rank": "Master",
          "level": 5,
          "cost": "30 Emerald",
          "result": "1 Enchanted Book",
          "chance": 35,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Mason",
      "count": 3,
      "trades": [
        {
          "id": "villagerretaliation:mason_low_bricks",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "2 Emerald",
          "result": "10 Bricks",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:mason_skilled_stone_bricks",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "3 Emerald",
          "result": "16 Stone Bricks",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:mason_expert_quartz_blocks",
          "rank": "Expert",
          "level": 4,
          "cost": "6 Emerald",
          "result": "8 Quartz Block",
          "chance": 55,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Shepherd",
      "count": 3,
      "trades": [
        {
          "id": "villagerretaliation:shepherd_low_wool",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "2 Emerald",
          "result": "12 White Wool",
          "chance": 85,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:shepherd_skilled_banner",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "4 Emerald",
          "result": "2 White Banner",
          "chance": 75,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:shepherd_expert_scaffolding",
          "rank": "Expert",
          "level": 4,
          "cost": "6 Emerald",
          "result": "16 Scaffolding",
          "chance": 55,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Toolsmith",
      "count": 3,
      "trades": [
        {
          "id": "villagerretaliation:toolsmith_low_iron_shovel",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "6 Emerald",
          "result": "1 Iron Shovel",
          "chance": 75,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:toolsmith_skilled_iron_pickaxe",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "8 Emerald",
          "result": "1 Iron Pickaxe",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:toolsmith_expert_diamond_pickaxe",
          "rank": "Expert",
          "level": 5,
          "cost": "26 Emerald",
          "result": "1 Diamond Pickaxe",
          "chance": 35,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    },
    {
      "profession": "Wandering Trader",
      "count": 5,
      "trades": [
        {
          "id": "villagerretaliation:wandering_trader_master_nautilus_shell",
          "rank": "Master",
          "level": 1,
          "cost": "15 Emerald",
          "result": "1 Nautilus Shell",
          "chance": 30,
          "requestable": false,
          "minReputation": ""
        },
        {
          "id": "villagerretaliation:wandering_trader_master_packed_ice",
          "rank": "Master",
          "level": 1,
          "cost": "4 Emerald",
          "result": "12 Packed Ice",
          "chance": 45,
          "requestable": false,
          "minReputation": ""
        },
        {
          "id": "villagerretaliation:wandering_trader_expert_experience_bottles",
          "rank": "Expert",
          "level": 1,
          "cost": "6 Emerald",
          "result": "3 Experience Bottle",
          "chance": 50,
          "requestable": false,
          "minReputation": ""
        },
        {
          "id": "villagerretaliation:wandering_trader_low_cactus",
          "rank": "Novice to Apprentice",
          "level": 1,
          "cost": "2 Emerald",
          "result": "4 Cactus",
          "chance": 75,
          "requestable": false,
          "minReputation": ""
        },
        {
          "id": "villagerretaliation:wandering_trader_skilled_glow_berries",
          "rank": "Skilled to Expert",
          "level": 1,
          "cost": "3 Emerald",
          "result": "8 Glow Berries",
          "chance": 80,
          "requestable": false,
          "minReputation": ""
        }
      ]
    },
    {
      "profession": "Weaponsmith",
      "count": 3,
      "trades": [
        {
          "id": "villagerretaliation:weaponsmith_low_iron_axe",
          "rank": "Novice to Apprentice",
          "level": 2,
          "cost": "8 Emerald",
          "result": "1 Iron Axe",
          "chance": 75,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:weaponsmith_skilled_iron_sword",
          "rank": "Skilled to Expert",
          "level": 3,
          "cost": "8 Emerald",
          "result": "1 Iron Sword",
          "chance": 80,
          "requestable": true,
          "minReputation": "Respected"
        },
        {
          "id": "villagerretaliation:weaponsmith_expert_diamond_sword",
          "rank": "Expert",
          "level": 5,
          "cost": "26 Emerald",
          "result": "1 Diamond Sword",
          "chance": 35,
          "requestable": true,
          "minReputation": "Respected"
        }
      ]
    }
  ],
  "advancements": [
    {
      "id": "familiar_face",
      "title": "A Familiar Face",
      "description": "Earn a villager's trust.",
      "frame": "Task",
      "hidden": false,
      "icon": "Bread",
      "parent": "reputation/commonfolk"
    },
    {
      "id": "accidentally_of_course",
      "title": "Accidentally, Of Course",
      "description": "Let the environment do the talking.",
      "frame": "Challenge",
      "hidden": true,
      "icon": "Lava Bucket",
      "parent": "reputation/hands_off"
    },
    {
      "id": "an_unwise_decision",
      "title": "An Unwise Decision",
      "description": "Attack the village's protector.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Iron Axe",
      "parent": "reputation/bad_first_impression"
    },
    {
      "id": "bad_first_impression",
      "title": "Bad First Impression",
      "description": "Make a villager suspicious of you.",
      "frame": "Task",
      "hidden": false,
      "icon": "Rotten Flesh",
      "parent": "reputation/root"
    },
    {
      "id": "bait_and_betrayal",
      "title": "Bait and Betrayal",
      "description": "Have a villager follow you, then kill them.",
      "frame": "Challenge",
      "hidden": true,
      "icon": "Lead",
      "parent": "reputation/hands_off"
    },
    {
      "id": "changed_my_mind",
      "title": "Changed My Mind",
      "description": "Take back a gift from a villager.",
      "frame": "Task",
      "hidden": false,
      "icon": "Emerald",
      "parent": "reputation/familiar_face"
    },
    {
      "id": "commonfolk",
      "title": "Commonfolk",
      "description": "The villagers are watching.",
      "frame": "Task",
      "hidden": false,
      "icon": "Emerald",
      "parent": "reputation/root"
    },
    {
      "id": "community_support",
      "title": "Community Support",
      "description": "Trade with many villagers in one village.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Chest",
      "parent": "reputation/regular_customer"
    },
    {
      "id": "cover_them_in_debris",
      "title": "Cover Them in Debris",
      "description": "Equip a villager with a full set of netherite armor.",
      "frame": "Challenge",
      "hidden": true,
      "icon": "Netherite Chestplate",
      "parent": "reputation/local_legend"
    },
    {
      "id": "crowned_by_the_village",
      "title": "Crowned by the Village",
      "description": "Become village royalty.",
      "frame": "Challenge",
      "hidden": false,
      "icon": "Nether Star",
      "parent": "reputation/local_legend"
    },
    {
      "id": "friend_of_the_village",
      "title": "Friend of the Village",
      "description": "Become trusted by multiple villagers in the same village.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Bell",
      "parent": "reputation/respect_is_earned"
    },
    {
      "id": "hands_off",
      "title": "Hands Off",
      "description": "Hurt a villager and face the consequences.",
      "frame": "Task",
      "hidden": false,
      "icon": "Wooden Sword",
      "parent": "reputation/bad_first_impression"
    },
    {
      "id": "hero_not_menace",
      "title": "Hero, Not Menace",
      "description": "Help a village after previously being distrusted.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Shield",
      "parent": "reputation/refused_service"
    },
    {
      "id": "im_sorry",
      "title": "I'm Sorry!",
      "description": "Pacify a villager with a peace offering.",
      "frame": "Task",
      "hidden": false,
      "icon": "Emerald",
      "parent": "reputation/commonfolk"
    },
    {
      "id": "legend_trader",
      "title": "Legend Trader",
      "description": "Share 25 discovered stories with villagers.",
      "frame": "Challenge",
      "hidden": false,
      "icon": "Recovery Compass",
      "parent": "reputation/village_chronicler"
    },
    {
      "id": "local_legend",
      "title": "Local Legend",
      "description": "Become revered by a villager.",
      "frame": "Challenge",
      "hidden": false,
      "icon": "Emerald Block",
      "parent": "reputation/respect_is_earned"
    },
    {
      "id": "marked",
      "title": "Marked",
      "description": "Become hated by a villager.",
      "frame": "Challenge",
      "hidden": false,
      "icon": "Wither Rose",
      "parent": "reputation/bad_first_impression"
    },
    {
      "id": "mob_justice",
      "title": "Mob Justice",
      "description": "Anger enough villagers to be surrounded.",
      "frame": "Challenge",
      "hidden": false,
      "icon": "Iron Golem Spawn Egg",
      "parent": "reputation/village_enemy"
    },
    {
      "id": "no_rest_for_the_wicked",
      "title": "No Rest For The Wicked",
      "description": "Break the bed of a sleeping villager.",
      "frame": "Challenge",
      "hidden": true,
      "icon": "Red Bed",
      "parent": "reputation/hands_off"
    },
    {
      "id": "once_upon_a_time",
      "title": "Once Upon a Time",
      "description": "Share a discovered story with a villager.",
      "frame": "Task",
      "hidden": false,
      "icon": "Writable Book",
      "parent": "reputation/trusted_directions"
    },
    {
      "id": "peace_offering",
      "title": "Peace Offering",
      "description": "Make amends.",
      "frame": "Challenge",
      "hidden": true,
      "icon": "Poppy",
      "parent": "reputation/marked"
    },
    {
      "id": "price_of_trust",
      "title": "Price of Trust",
      "description": "Earn better treatment through reputation.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Emerald Block",
      "parent": "reputation/regular_customer"
    },
    {
      "id": "refused_service",
      "title": "Refused Service",
      "description": "Become disliked enough to lose someone's business.",
      "frame": "Task",
      "hidden": false,
      "icon": "Barrier",
      "parent": "reputation/bad_first_impression"
    },
    {
      "id": "regular_customer",
      "title": "Regular Customer",
      "description": "Build trust through trade.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Emerald",
      "parent": "reputation/commonfolk"
    },
    {
      "id": "respect_is_earned",
      "title": "Respect Is Earned",
      "description": "Become respected by a villager.",
      "frame": "Task",
      "hidden": false,
      "icon": "Emerald",
      "parent": "reputation/familiar_face"
    },
    {
      "id": "second_chance",
      "title": "Second Chance",
      "description": "Cure a zombie villager who still remembers you.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Splash Potion",
      "parent": "reputation/commonfolk"
    },
    {
      "id": "story_keeper",
      "title": "Story Keeper",
      "description": "Share 5 discovered stories with villagers.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Book",
      "parent": "reputation/once_upon_a_time"
    },
    {
      "id": "the_village_has_eyes",
      "title": "The Village Has Eyes",
      "description": "Harm a villager while others are watching.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Spyglass",
      "parent": "reputation/hands_off"
    },
    {
      "id": "the_village_remembers",
      "title": "The Village Remembers",
      "description": "Restore your reputation after falling into suspicion.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Paper",
      "parent": "reputation/commonfolk"
    },
    {
      "id": "trusted_directions",
      "title": "Trusted Directions",
      "description": "Follow a map given through villager dialogue.",
      "frame": "Goal",
      "hidden": true,
      "icon": "Filled Map",
      "parent": "reputation/respect_is_earned"
    },
    {
      "id": "village_chronicler",
      "title": "Village Chronicler",
      "description": "Share 10 discovered stories with villagers.",
      "frame": "Goal",
      "hidden": false,
      "icon": "Lectern",
      "parent": "reputation/story_keeper"
    },
    {
      "id": "village_enemy",
      "title": "Village Enemy",
      "description": "Turn an entire village against you.",
      "frame": "Challenge",
      "hidden": false,
      "icon": "Bell",
      "parent": "reputation/marked"
    },
    {
      "id": "root",
      "title": "Village Relations",
      "description": "How the village remembers you.",
      "frame": "Task",
      "hidden": false,
      "icon": "Bell",
      "parent": ""
    }
  ],
  "stats": {
    "dialogueLinesEstimate": 32014,
    "dialogueLineBreakdown": {
      "dialogue": 31417,
      "forcedDialogue": 220,
      "dialogueTrees": 225,
      "questModules": 152
    }
  }
};
