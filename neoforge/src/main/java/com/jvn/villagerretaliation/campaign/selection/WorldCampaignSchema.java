package com.jvn.villagerretaliation.campaign.selection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class WorldCampaignSchema {
    public static final String ID = "villagerretaliation:world_campaign/v1";

    private WorldCampaignSchema() { }

    public static JsonObject export() {
        return JsonParser.parseString("""
                {
                  "type": "object", "additionalProperties": false,
                  "required": ["schema", "id", "name"],
                  "anyOf": [{"required": ["description"]}, {"required": ["description_file"]}],
                  "properties": {
                    "schema": {"const": "villagerretaliation:world_campaign/v1"},
                    "id": {"type": "string", "pattern": "^[a-z0-9_.-]+:[a-z0-9_.-]+$", "maxLength": 256},
                    "name": {"type": "string", "minLength": 1, "maxLength": 120},
                    "description": {"type": "string", "maxLength": 4096},
                    "description_file": {"type": "string", "pattern": "^[a-z0-9_.-]+:[a-z0-9_./-]+[.]md$", "maxLength": 512},
                    "authors": {"type": "array", "maxItems": 32, "items": {"type": "string", "minLength": 1, "maxLength": 120}},
                    "image": {"type": "string", "pattern": "^[a-z0-9_.-]+:[a-z0-9_./-]+$"},
                    "locked": {"type": "boolean"},
                    "include_default_content": {"type": "boolean"}
                  }
                }
                """).getAsJsonObject();
    }
}
