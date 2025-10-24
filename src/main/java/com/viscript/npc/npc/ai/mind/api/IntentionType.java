package com.viscript.npc.npc.ai.mind.api;

public enum IntentionType {
    IDLE,
    MOVE,
    FOLLOW,
    MELEE,
    RANGE,
    CUSTOM,
    NONE;

    public static IntentionType getType(String name) {
        return switch (name) {
            case "IDLE" -> IDLE;
            case "MOVE" -> MOVE;
            case "FOLLOW" -> FOLLOW;
            case "MELEE" -> MELEE;
            case "RANGE" -> RANGE;
            case "CUSTOM" -> CUSTOM;
            default -> NONE;
        };
    }

    public static String getName(IntentionType type) {
        return type.name();
    }
}
