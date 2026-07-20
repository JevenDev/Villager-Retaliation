package com.jvn.villagerretaliation.trade;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exports the runtime-supported skill-trade authoring contract for browser and CLI tooling. */
public final class SkillTradeSchemaTool {
    private SkillTradeSchemaTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output path");
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, schema());
    }

    public static String schema() {
        return """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$id": "villagerretaliation:skill_trades/v1",
                  "title": "Villager Retaliation Skill Trades",
                  "type": "object",
                  "properties": {
                    "replace": { "type": "boolean" },
                    "entries": { "type": "array", "items": { "$ref": "#/$defs/entry" } }
                  },
                  "required": ["entries"],
                  "additionalProperties": false,
                  "$defs": {
                    "resource": { "type": "string", "pattern": "^[a-z0-9_.-]+:[a-z0-9_./-]+$" },
                    "rank": { "enum": ["novice", "apprentice", "skilled", "expert", "master"] },
                    "skill": { "enum": ["villagerretaliation:farming", "villagerretaliation:fishing", "villagerretaliation:smithing", "villagerretaliation:crafting", "villagerretaliation:trading", "villagerretaliation:medicine", "villagerretaliation:archery", "villagerretaliation:guarding", "villagerretaliation:cooking", "villagerretaliation:animal_handling", "villagerretaliation:cartography", "villagerretaliation:scholarship", "villagerretaliation:gathering", "villagerretaliation:masonry", "villagerretaliation:mining", "villagerretaliation:leatherworking", "villagerretaliation:diplomacy", "villagerretaliation:survival"] },
                    "configFlag": { "enum": ["enableSkillTradeOverhaul", "skillTradeAllowHighTierEquipment", "skillTradeAllowSpecialArrows", "skillTradeAllowRareSpecialtyTrades"] },
                    "itemCount": { "type": "integer", "minimum": 1, "maximum": 64 },
                    "cost": {
                      "type": "object",
                      "properties": {
                        "item": { "$ref": "#/$defs/resource" },
                        "count": { "$ref": "#/$defs/itemCount" },
                        "skill_discount": { "type": "object", "properties": { "enabled": { "type": "boolean" }, "max_percent": { "type": "integer", "minimum": 0, "maximum": 90 } }, "additionalProperties": false }
                      },
                      "additionalProperties": false
                    },
                    "result": {
                      "type": "object",
                      "properties": {
                        "item": { "$ref": "#/$defs/resource" },
                        "items": { "type": "array", "minItems": 1, "items": { "$ref": "#/$defs/resource" } },
                        "count": { "$ref": "#/$defs/itemCount" },
                        "enchantments": { "$ref": "#/$defs/enchantments" }
                      },
                      "anyOf": [{ "required": ["item"] }, { "required": ["items"] }],
                      "additionalProperties": false
                    },
                    "enchantments": {
                      "type": "object",
                      "properties": {
                        "mode": { "enum": ["none", "random", "random_from", "fixed"] },
                        "candidates": { "type": "array", "items": { "$ref": "#/$defs/resource" } },
                        "fixed": { "oneOf": [{ "type": "object", "additionalProperties": { "type": "integer", "minimum": 0 } }, { "type": "array" }] },
                        "level_by_skill": { "type": "boolean" }, "min_level": { "type": "integer", "minimum": 1 }, "max_level": { "type": "integer", "minimum": 1 }
                      },
                      "additionalProperties": false
                    },
                    "maxUses": { "oneOf": [{ "type": "integer", "minimum": 1, "maximum": 64 }, { "type": "object", "properties": { "base": { "type": "integer", "minimum": 1, "maximum": 64 }, "bonus_by_skill": { "type": "boolean" }, "max_bonus": { "type": "integer", "minimum": 0, "maximum": 64 } }, "additionalProperties": false }] },
                    "conditions": { "type": "object", "properties": { "config_flags": { "type": "array", "items": { "$ref": "#/$defs/configFlag" } }, "disabled_config_flags": { "type": "array", "items": { "$ref": "#/$defs/configFlag" } } }, "additionalProperties": false },
                    "qualityScaling": { "oneOf": [{ "type": "boolean" }, { "type": "object", "properties": { "enabled": { "type": "boolean" }, "count_by_skill": { "type": "boolean" }, "cost_by_skill": { "type": "boolean" }, "max_uses_by_skill": { "type": "boolean" }, "xp_by_skill": { "type": "boolean" }, "rare_chance_by_skill": { "type": "boolean" }, "enchantments_by_skill": { "type": "boolean" } }, "additionalProperties": false }] },
                    "request": {
                      "type": "object",
                      "properties": {
                        "targetable": { "type": "boolean" },
                        "display_priority": { "type": "integer", "minimum": -100000, "maximum": 100000 },
                        "min_reputation": { "enum": ["royalty", "revered", "respected", "trusted", "neutral", "suspicious", "hostile", "despised", "feared"] },
                        "wait_days": { "type": "integer", "minimum": 0, "maximum": 3650 },
                        "cooldown_days": { "type": "integer", "minimum": 0, "maximum": 3650 },
                        "extra_cost": { "$ref": "#/$defs/cost" }
                      },
                      "additionalProperties": false
                    },
                    "entry": {
                      "type": "object",
                      "properties": {
                        "id": { "$ref": "#/$defs/resource" },
                        "remove": { "type": "boolean" },
                        "professions": { "type": "array", "items": { "$ref": "#/$defs/resource" } },
                        "skills": { "type": "array", "minItems": 1, "items": { "$ref": "#/$defs/skill" } },
                        "min_rank": { "$ref": "#/$defs/rank" },
                        "max_rank": { "$ref": "#/$defs/rank" },
                        "villager_level": { "type": "integer", "minimum": 1, "maximum": 5 },
                        "chance": { "type": "number", "minimum": 0, "maximum": 1 },
                        "weight": { "type": "integer", "minimum": 1, "maximum": 10000 },
                        "cost": { "$ref": "#/$defs/cost" },
                        "result": { "$ref": "#/$defs/result" },
                        "max_uses": { "$ref": "#/$defs/maxUses" }, "xp": { "type": "integer", "minimum": 0, "maximum": 10000 },
                        "price_multiplier": { "type": "number", "minimum": 0, "maximum": 1 },
                        "conditions": { "$ref": "#/$defs/conditions" }, "quality_scaling": { "$ref": "#/$defs/qualityScaling" },
                        "request": { "$ref": "#/$defs/request" }, "pool": { "enum": ["villager", "generic", "rare", "wanderer_generic", "wanderer_rare", "wandering_trader_generic", "wandering_trader_rare"] }
                      },
                      "required": ["id"],
                      "allOf": [{ "if": { "properties": { "remove": { "const": true } }, "required": ["remove"] }, "else": { "required": ["skills", "result"] } }],
                      "additionalProperties": false
                    }
                  }
                }
                """;
    }
}
