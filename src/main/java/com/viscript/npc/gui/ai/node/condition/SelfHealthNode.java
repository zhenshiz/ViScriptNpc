package com.viscript.npc.gui.ai.node.condition;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

/** 检查 NPC 自身生命比例是否低于上限。 */
@NodeAttribute(name = "viscript_npc:self_health", group = "condition", graphTypes = AiGraph.class)
public final class SelfHealthNode extends AiGraphNode {
    public SelfHealthNode() { super("self_health"); }
}
