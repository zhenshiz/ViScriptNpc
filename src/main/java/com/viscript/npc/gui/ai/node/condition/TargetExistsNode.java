package com.viscript.npc.gui.ai.node.condition;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

/** 检查当前目标是否有效。 */
@NodeAttribute(name = "viscript_npc:target_exists", group = "condition", graphTypes = AiGraph.class)
public final class TargetExistsNode extends AiGraphNode {
    public TargetExistsNode() { super("target_exists"); }
}
