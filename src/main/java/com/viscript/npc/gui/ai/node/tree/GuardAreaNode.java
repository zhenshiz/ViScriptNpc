package com.viscript.npc.gui.ai.node.tree;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:guard_area", group = "tree", graphTypes = AiGraph.class)
public final class GuardAreaNode extends AiGraphNode {
    public GuardAreaNode() { super("guard_area"); }
}
