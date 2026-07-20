package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_SEQUENCE, group = NpcBehaviorNode.CATEGORY_COMPOSITE, priority = 900, graphTypes = NpcBehaviorGraph.class)
public class SequenceNode extends NpcBehaviorNode {
    @Override
    public Component getDisplayName() {
        return nodeName("sequence");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
        addChildrenOutput(context);
    }
}
