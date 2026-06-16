package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_HAS_TARGET, group = NpcBehaviorNode.CATEGORY_CONDITION, priority = 700, graphTypes = NpcBehaviorGraph.class)
public class HasTargetNode extends NpcBehaviorNode {
    @Override
    public Component getDisplayName() {
        return nodeName("has_target");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
    }
}
