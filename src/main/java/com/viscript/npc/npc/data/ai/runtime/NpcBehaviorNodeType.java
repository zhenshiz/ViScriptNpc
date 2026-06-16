package com.viscript.npc.npc.data.ai.runtime;

public final class NpcBehaviorNodeType {
    public static final String ROOT = "root";
    public static final String SEQUENCE = "sequence";
    public static final String SELECTOR = "selector";
    public static final String INVERTER = "inverter";
    public static final String HAS_TARGET = "has_target";
    public static final String FIND_NEAREST_PLAYER = "find_nearest_player";
    public static final String IS_TARGET_IN_RANGE = "is_target_in_range";
    public static final String IDLE = "idle";
    public static final String FOLLOW_TARGET = "follow_target";
    public static final String LOOK_AT_TARGET = "look_at_target";
    public static final String MOVE_TO_POSITION = "move_to_position";
    public static final String MELEE_ATTACK_TARGET = "melee_attack_target";

    private NpcBehaviorNodeType() {
    }
}
