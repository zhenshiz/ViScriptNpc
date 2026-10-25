package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

/** 将动作参数绑定到输入参数、NPC 黑板或行为局部变量。 */
public class AiBinding implements IPersistedSerializable {
    @Persisted public String scope = "literal";
    @Persisted public String variableId = "";
    @Persisted public AiValue value = new AiValue();
    public static AiBinding literal(AiValue value) { var b = new AiBinding(); b.value = value; return b; }
}
