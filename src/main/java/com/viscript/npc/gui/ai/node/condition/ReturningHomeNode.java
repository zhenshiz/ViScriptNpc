package com.viscript.npc.gui.ai.node.condition;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:returning_home", group = "condition", graphTypes = AiGraph.class)
public final class ReturningHomeNode extends AiGraphNode {
    public ReturningHomeNode() { super("returning_home"); }
}
