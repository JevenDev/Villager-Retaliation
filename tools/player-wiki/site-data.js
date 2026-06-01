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
      "questline": "dangerous_commissions",
      "questlineLabel": "Dangerous Commissions",
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
      }
    },
    {
      "id": "villagerretaliation:house_of_ill_omens",
      "slug": "house_of_ill_omens",
      "title": "House of Ill Omens",
      "description": "Enter a Woodland Mansion and return with a Totem of Undying.",
      "questline": "dangerous_commissions",
      "questlineLabel": "Dangerous Commissions",
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
          "text": "Bring a Totem of Undying and 12 emeralds.",
          "progress": 0.66,
          "hint": ""
        },
        {
          "id": "bring_emeralds",
          "label": "Bring Emeralds",
          "text": "Set aside 12 emeralds for informants.",
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
          "A mansion has been named in too many frightened reports. Bring a Totem of Undying and money for those who pointed us there."
        ],
        "accept": "I will enter the mansion",
        "decline": "Another time",
        "started": [
          "The mansion lies {direction}, around {distance} blocks away, near {target_x}, {target_z}. Bring a Totem of Undying and 12 emeralds for informants."
        ],
        "reminder": [
          "Woodland Mansion near {target_x}, {target_z}. Totem first, then 12 emeralds to pay the informants who helped us."
        ],
        "completed": [
          "A Totem of Undying. That is not proof of safety, but it is proof the village has a shield with a name."
        ],
        "missing": [
          "The totem needs the mansion behind it. I need the place confirmed.",
          "Bring the Totem. Nothing else carries enough of that house on it.",
          "The informants still need 12 emeralds."
        ]
      }
    },
    {
      "id": "villagerretaliation:nether_wart_warranty",
      "slug": "nether_wart_warranty",
      "title": "Nether Wart Warranty",
      "description": "Reach a Nether Fortress and return with Nether Wart and a Blaze Rod.",
      "questline": "dangerous_commissions",
      "questlineLabel": "Dangerous Commissions",
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
      }
    },
    {
      "id": "villagerretaliation:trial_chamber_recall",
      "slug": "trial_chamber_recall",
      "title": "Trial Chamber Recall",
      "description": "Survey a Trial Chamber and return with a Trial Key and Breeze Rod.",
      "questline": "dangerous_commissions",
      "questlineLabel": "Dangerous Commissions",
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
      }
    },
    {
      "id": "villagerretaliation:end_city_survey",
      "slug": "end_city_survey",
      "title": "End City Survey",
      "description": "Reach an End City and bring back a Shulker Shell with a Chorus Flower sample.",
      "questline": "lost_civilization",
      "questlineLabel": "Lost Civilization",
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
      }
    },
    {
      "id": "villagerretaliation:tales_of_a_lost_civilization",
      "slug": "tales_of_a_lost_civilization",
      "title": "Tales of a Lost Civilization",
      "description": "Follow a cartographer's rumor to an Ancient City and return with an Echo Shard.",
      "questline": "lost_civilization",
      "questlineLabel": "Lost Civilization",
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
        "1 Echo Shard"
      ],
      "steps": [
        {
          "id": "travel",
          "label": "Travel",
          "text": "Reach the Ancient City center near {target_x}, {target_z}.",
          "progress": 0.25,
          "hint": "{distance} blocks {direction}"
        },
        {
          "id": "proof",
          "label": "Proof",
          "text": "Recover {proof_item} as proof of the journey.",
          "progress": 0.66,
          "hint": "City center visited"
        },
        {
          "id": "return",
          "label": "Return",
          "text": "Return to the cartographer with {proof_item}.",
          "progress": 1,
          "hint": "Ready to turn in"
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
          "The old mark has not changed: {target_x}, {target_z}, about {distance} blocks {direction}. Center first, {proof_item} second, then come back."
        ],
        "completed": [
          "So the stories were not just ink after all. Keep what you learned close, and let the village know you walked where silence keeps records.",
          "You found the center and brought proof. That is more than a map can do. This village will remember your name beside that lost place.",
          "An Echo Shard from the heart of {target}. I believe you. Some stories should be paid for before they are repeated."
        ],
        "missing": [
          "That shard alone is not enough. You need to stand in the central heart of {target}, not only its halls.",
          "The proof is half the story. The other half is the place itself: the city center near {target_x}, {target_z}.",
          "You saw the place, then. Bring me {proof_item}, and I can call the tale complete.",
          "The journey needs a token. Find {proof_item} in those depths and bring it back."
        ]
      }
    },
    {
      "id": "villagerretaliation:sunken_ledger",
      "slug": "sunken_ledger",
      "title": "Sunken Ledger",
      "description": "Search a shipwreck and return with a compass and paper before the route is forgotten.",
      "questline": "old_roads",
      "questlineLabel": "Old Roads",
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
      }
    },
    {
      "id": "villagerretaliation:the_broken_milestone",
      "slug": "the_broken_milestone",
      "title": "The Broken Milestone",
      "description": "Find nearby Trail Ruins and bring a brush and stone to restore the road marker.",
      "questline": "old_roads",
      "questlineLabel": "Old Roads",
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
      }
    },
    {
      "id": "villagerretaliation:fletchers_countermark",
      "slug": "fletchers_countermark",
      "title": "Fletcher's Countermark",
      "description": "Scout a Pillager Outpost and return with a crossbow as proof of the threat.",
      "questline": "village_defense",
      "questlineLabel": "Village Defense",
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
      }
    },
    {
      "id": "villagerretaliation:lanterns_for_the_long_night",
      "slug": "lanterns_for_the_long_night",
      "title": "Lanterns for the Long Night",
      "description": "Bring lanterns before the watch loses the edges of the village to darkness.",
      "questline": "village_supply",
      "questlineLabel": "Village Supply",
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
        "lootTable": "villagerretaliation:quest/lanterns_for_the_long_night",
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
          "Bring 6 lanterns soon; this request matters less after a couple of days."
        ],
        "reminder": [
          "Six lanterns, and sooner is better. Darkness will not wait."
        ],
        "completed": [
          "That will hold the corners of the village a little brighter. Good work."
        ],
        "missing": [
          "We still need all 6 lanterns for the watch.",
          "Bring the lanterns before we close this.",
          "I need 6 lanterns, not promises shaped like lanterns."
        ]
      }
    },
    {
      "id": "villagerretaliation:ready_the_larder",
      "slug": "ready_the_larder",
      "title": "Ready the Larder",
      "description": "Bring bread so the village can stretch its stores through a hard night.",
      "questline": "village_supply",
      "questlineLabel": "Village Supply",
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
        "lootTable": "villagerretaliation:quest/ready_the_larder",
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
          "Bring me 16 bread. Simple work, but it keeps people steady."
        ],
        "reminder": [
          "Sixteen bread, if you can spare it. No heroics today, just weight on the shelf."
        ],
        "completed": [
          "Good. A full shelf makes brave talk sound less hollow. Take this with my thanks."
        ],
        "missing": [
          "We still need all 16 bread for the stores.",
          "Bring the bread before we close this.",
          "I still count fewer than 16 bread. Close, maybe, but hungry math is strict."
        ]
      }
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
    "dialogueLinesEstimate": 25876,
    "dialogueLineBreakdown": {
      "dialogue": 25550,
      "forcedDialogue": 220,
      "dialogueTrees": 106
    }
  }
};
