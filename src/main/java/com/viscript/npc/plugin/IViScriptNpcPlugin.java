package com.viscript.npc.plugin;

public interface IViScriptNpcPlugin {
    /**
     * 模组初始化加载执行
     */
    default void init() {
    }

    /**
     * 注册npc相关内容
     * @param event npc注册事件
     */
    default void registerNpc(RegisterNpcEvent event) {
    }
}
