package com.viscript.npc.gui.ai.node.tree;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

/** 在整个子树运行期间持续更新目标的感知服务。 */
@NodeAttribute(name = "viscript_npc:sense_targets", group = "tree", graphTypes = AiGraph.class)
public final class SenseTargetsNode extends AiGraphNode {
    public SenseTargetsNode() { super("sense_targets"); }
}
