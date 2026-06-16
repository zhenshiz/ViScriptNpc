package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_LOOK_AT_TARGET, group = NpcBehaviorNode.CATEGORY_ACTION, priority = 575, graphTypes = NpcBehaviorGraph.class)
public class LookAtTargetNode extends NpcBehaviorNode {
    public static final String OPTION_YAW_SPEED = "yaw_speed";
    public static final String OPTION_PITCH_SPEED = "pitch_speed";

    @Override
    public Component getDisplayName() {
        return nodeName("look_at_target");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        floatOption(context, OPTION_YAW_SPEED, 30.0f);
        floatOption(context, OPTION_PITCH_SPEED, 30.0f);
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
    }
}
