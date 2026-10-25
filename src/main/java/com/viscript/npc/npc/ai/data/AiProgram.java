package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

/** 服务端可执行的树或行为定义；运行实例使用独立的定义快照。 */
public class AiProgram implements IPersistedSerializable {
    @Persisted public int version = 1;
    @Persisted public String kind = "tree";
    /** 可选的行为显示名称翻译键；空字符串表示按逻辑文件 ID 显示。 */
    @Persisted @lombok.Setter @lombok.experimental.Accessors(chain = true)
    public String displayNameKey = "";
    @Persisted public String root = "";
    @Persisted public java.util.List<AiNodeData> nodes = new java.util.ArrayList<>();
    @Persisted public java.util.List<AiVariable> parameters = new java.util.ArrayList<>();
    @Persisted public java.util.List<AiVariable> variables = new java.util.ArrayList<>();
    @Persisted public java.util.List<AiVariable> npcVariables = new java.util.ArrayList<>();
    public AiNodeData node(String id) { return nodes.stream().filter(n -> n.id.equals(id)).findFirst().orElse(null); }
    public AiProgram copy(net.minecraft.core.HolderLookup.Provider provider) {
        var p = new AiProgram(); p.version = version; p.kind = kind; p.root = root; p.displayNameKey = displayNameKey;
        for (var node : nodes) {
            var n = new AiNodeData();
            n.id = node.id; n.type = node.type; n.disabled = node.disabled; n.onHit = node.onHit; n.children.addAll(node.children);
            node.inputs.forEach((key, source) -> {
                var binding = new AiBinding(); binding.scope = source.scope; binding.variableId = source.variableId; binding.value = source.value.copy();
                n.inputs.put(key, binding);
            });
            p.nodes.add(n);
        }
        copyVariables(parameters, p.parameters); copyVariables(variables, p.variables); copyVariables(npcVariables, p.npcVariables);
        return p;
    }
    private static void copyVariables(java.util.List<AiVariable> source, java.util.List<AiVariable> target) {
        for (var v : source) {
            var copy = new AiVariable(); copy.id = v.id; copy.name = v.name;
            copy.displayNameKey = v.displayNameKey; copy.tipsKey = v.tipsKey;
            copy.initial = v.initial.copy(); target.add(copy);
        }
    }
}
