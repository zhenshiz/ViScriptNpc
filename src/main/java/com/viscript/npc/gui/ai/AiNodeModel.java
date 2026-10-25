package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.CustomNodeModelImpl;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortModelOptions;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.NodeDefinitionScope;
import net.minecraft.network.chat.Component;
import com.viscript.npc.npc.ai.data.AiNodeData;

/** 编辑数据与稳定子节点顺序随 LDLib2 图快照一并保存。 */
public final class AiNodeModel extends CustomNodeModelImpl {
    @Persisted public AiNodeData data = new AiNodeData();

    @Override protected void onDefineNode(NodeDefinitionScope<? extends NodeModel> scope) {
        super.onDefineNode(scope);
        if (!(getGraphModel() instanceof AiGraphModel graph)) return;
        var action = operation().equals("call") ? ((AiGraph) graph.getGraph()).actionResolver
                .apply(data.literal("file", com.viscript.npc.npc.ai.data.AiValue.text("")).text) : null;
        for (var field : graph.fields(this)) {
            if (!com.viscript.npc.npc.ai.runtime.AiOperations.hasDataInput(field)) continue;
            var port = addInputPort("value_" + field.id(), AiVariables.type(field.initial().type), null, null,
                    PortModelOptions.NO_EMBEDDED_CONSTANT, null, null);
            var parameter = field.id().startsWith("param:") && action != null
                    ? action.parameters.stream().filter(v -> field.id().equals("param:" + v.id)).findFirst().orElse(null) : null;
            var title = parameter != null ? AiVariables.displayName(parameter)
                    : AiGraphNode.fieldName(operation(), field.id());
            port.setTitle(title);
            if (parameter != null) port.setTooltips(port.getDefaultTooltips().merge(AiVariables.tips(parameter)));
        }
    }

    @Override public GraphElement<?> createElementUI() { return new AiNodeElement(this); }
    public String operation() { return getNode() instanceof AiGraphNode node ? node.operation : data.type; }
}
