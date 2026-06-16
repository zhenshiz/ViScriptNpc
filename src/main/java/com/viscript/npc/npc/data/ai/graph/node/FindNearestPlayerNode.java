package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_FIND_NEAREST_PLAYER, group = NpcBehaviorNode.CATEGORY_ACTION, priority = 610, graphTypes = NpcBehaviorGraph.class)
public class FindNearestPlayerNode extends NpcBehaviorNode {
    public static final String OPTION_RANGE = "range";
    public static final String OPTION_IGNORE_CREATIVE = "ignore_creative";

    @Override
    public Component getDisplayName() {
        return nodeName("find_nearest_player");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        floatOption(context, OPTION_RANGE, 16.0f);
        boolOption(context, OPTION_IGNORE_CREATIVE, true);
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
    }
}
