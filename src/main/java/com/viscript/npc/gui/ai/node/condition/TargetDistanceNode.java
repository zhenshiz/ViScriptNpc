package com.viscript.npc.gui.ai.node.condition;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

/** 检查当前目标是否位于指定距离区间内。 */
@NodeAttribute(name = "viscript_npc:target_distance", group = "condition", graphTypes = AiGraph.class)
public final class TargetDistanceNode extends AiGraphNode {
    public TargetDistanceNode() { super("target_distance"); }
}
