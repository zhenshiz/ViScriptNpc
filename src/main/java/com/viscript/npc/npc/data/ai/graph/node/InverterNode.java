package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_INVERTER, group = NpcBehaviorNode.CATEGORY_DECORATOR, priority = 800, graphTypes = NpcBehaviorGraph.class)
public class InverterNode extends NpcBehaviorNode {
    @Override
    public Component getDisplayName() {
        return nodeName("inverter");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
        addChildOutput(context);
    }
}
