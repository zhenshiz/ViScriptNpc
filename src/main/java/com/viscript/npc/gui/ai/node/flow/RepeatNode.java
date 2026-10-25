package com.viscript.npc.gui.ai.node.flow;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:repeat", group = "flow", graphTypes = AiGraph.class)
public final class RepeatNode extends AiGraphNode {
    public RepeatNode() { super("repeat"); }
}
