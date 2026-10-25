package com.viscript.npc.gui.ai.node.tree;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:selector", group = "flow", graphTypes = AiGraph.class)
public final class SelectorNode extends AiGraphNode {
    public SelectorNode() { super("selector"); }
}
