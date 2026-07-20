package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_MOVE_TO_POSITION, group = NpcBehaviorNode.CATEGORY_ACTION, priority = 590, graphTypes = NpcBehaviorGraph.class)
public class MoveToPositionNode extends NpcBehaviorNode {
    public static final String OPTION_X = "x";
    public static final String OPTION_Y = "y";
    public static final String OPTION_Z = "z";
    public static final String OPTION_SPEED = "speed";
    public static final String OPTION_STOPPING_DISTANCE = "stopping_distance";

    @Override
    public Component getDisplayName() {
        return nodeName("move_to_position");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        floatOption(context, OPTION_X, 0.0f);
        floatOption(context, OPTION_Y, 0.0f);
        floatOption(context, OPTION_Z, 0.0f);
        floatOption(context, OPTION_SPEED, 1.0f);
        floatOption(context, OPTION_STOPPING_DISTANCE, 1.0f);
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
    }
}
