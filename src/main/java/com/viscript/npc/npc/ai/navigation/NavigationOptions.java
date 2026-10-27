package com.viscript.npc.npc.ai.navigation;

/** 每次请求固定的移动能力；旧工程继续使用纯地面模式。 */
public record NavigationOptions(boolean openDoors, boolean swim, boolean climb) {
    public static final NavigationOptions GROUND = new NavigationOptions(false, false, false);
}
