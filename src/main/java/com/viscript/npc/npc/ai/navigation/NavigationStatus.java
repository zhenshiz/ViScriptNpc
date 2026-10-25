package com.viscript.npc.npc.ai.navigation;

/** 服务端导航请求的状态；只有 <code>ARRIVED</code> 表示实际到达。 */
public enum NavigationStatus {
    IDLE, COMPUTING, MOVING, ARRIVED, FAILED, CANCELLED
}
