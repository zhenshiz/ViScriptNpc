package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_SELECTOR, group = NpcBehaviorNode.CATEGORY_COMPOSITE, priority = 890, graphTypes = NpcBehaviorGraph.class)
public class SelectorNode extends NpcBehaviorNode {
    @Override
    public Component getDisplayName() {
        return nodeName("selector");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
        addChildrenOutput(context);
    }
}
