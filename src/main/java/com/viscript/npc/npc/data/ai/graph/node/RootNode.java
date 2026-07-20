package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_ROOT, group = NpcBehaviorNode.CATEGORY_FLOW, priority = 1000, graphTypes = NpcBehaviorGraph.class)
public class RootNode extends NpcBehaviorNode {
    @Override
    public Component getDisplayName() {
        return nodeName("root");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addChildOutput(context);
    }
}
