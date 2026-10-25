package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

/** 不含客户端类型的已编译节点；children 明确保存执行顺序。 */
public class AiNodeData implements IPersistedSerializable {
    @Persisted public String id = java.util.UUID.randomUUID().toString();
    @Persisted public String type = "sequence";
    @Persisted public boolean disabled;
    /** 可选的实际命中分支，不参与主流程的成功或失败结果。 */
    @Persisted public String onHit = "";
    @Persisted public java.util.List<String> children = new java.util.ArrayList<>();
    @Persisted public java.util.Map<String, AiBinding> inputs = new java.util.LinkedHashMap<>();
    public AiNodeData input(String key, AiValue value) { inputs.put(key, AiBinding.literal(value)); return this; }
    public AiValue literal(String key, AiValue fallback) { var b = inputs.get(key); return b == null ? fallback : b.value; }
}
