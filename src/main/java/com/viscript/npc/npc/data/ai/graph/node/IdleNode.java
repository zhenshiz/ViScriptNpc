package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_IDLE, group = NpcBehaviorNode.CATEGORY_ACTION, priority = 600, graphTypes = NpcBehaviorGraph.class)
public class IdleNode extends NpcBehaviorNode {
    public static final String OPTION_DURATION = "duration";

    @Override
    public Component getDisplayName() {
        return nodeName("idle");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        intOption(context, OPTION_DURATION, 20);
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
    }
}
