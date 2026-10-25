package com.viscript.npc.gui.ai.node.condition;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

/** 比较两个固定值或变量数值。 */
@NodeAttribute(name = "viscript_npc:number_compare", group = "condition", graphTypes = AiGraph.class)
public final class NumberCompareNode extends AiGraphNode {
    public NumberCompareNode() { super("number_compare"); }
}
