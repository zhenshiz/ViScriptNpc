package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_IS_TARGET_IN_RANGE, group = NpcBehaviorNode.CATEGORY_CONDITION, priority = 690, graphTypes = NpcBehaviorGraph.class)
public class IsTargetInRangeNode extends NpcBehaviorNode {
    public static final String OPTION_RANGE = "range";

    @Override
    public Component getDisplayName() {
        return nodeName("is_target_in_range");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        floatOption(context, OPTION_RANGE, 4.0f);
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
    }
}
