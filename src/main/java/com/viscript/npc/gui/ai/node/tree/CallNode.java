package com.viscript.npc.gui.ai.node.tree;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:call", group = "flow", graphTypes = AiGraph.class)
public final class CallNode extends AiGraphNode {
    public CallNode() { super("call"); }
}
