package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = NpcBehaviorNode.NODE_MELEE_ATTACK_TARGET, group = NpcBehaviorNode.CATEGORY_ACTION, priority = 580, graphTypes = NpcBehaviorGraph.class)
public class MeleeAttackTargetNode extends NpcBehaviorNode {
    public static final String OPTION_RANGE = "range";
    public static final String OPTION_COOLDOWN = "cooldown";

    @Override
    public Component getDisplayName() {
        return nodeName("melee_attack_target");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        floatOption(context, OPTION_RANGE, 2.0f);
        intOption(context, OPTION_COOLDOWN, 20);
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        addFlowInput(context);
    }
}
