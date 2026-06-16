package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_FOLLOW_TARGET, group = NpcBehaviorNode.CATEGORY_ACTION, priority = 605, graphTypes = NpcBehaviorGraph.class)
public class FollowTargetNode extends NpcBehaviorNode {
    public static final String OPTION_SPEED = "speed";
    public static final String OPTION_STOPPING_DISTANCE = "stopping_distance";

    @Override
    public Component getDisplayName() {
        return nodeName("follow_target");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        floatOption(context, OPTION_SPEED, 1.0f);
        floatOption(context, OPTION_STOPPING_DISTANCE, 2.0f);
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
    }
}
