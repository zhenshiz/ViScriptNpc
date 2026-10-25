package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript_lib.annotation.ViScriptRegisterAccessors;
import com.viscript_lib.event.RegisterAccessorEvent;

/** 随原生变量稳定标识保存的显示信息，使用 LDLib2 快照参与工程保存和撤销。 */
public final class AiVariablePresentation implements IPersistedSerializable {
    @Persisted public String displayNameKey = "";
    @Persisted public String tipsKey = "";

    /**
     * 在图快照解析前注册显示信息类型，供持久化映射使用。
     *
     * @param event VSL 的早期访问器注册上下文
     */
    @ViScriptRegisterAccessors
    public static void register(RegisterAccessorEvent event) {
        event.register(AiVariablePresentation.class, AiVariablePresentation::new);
    }
}
