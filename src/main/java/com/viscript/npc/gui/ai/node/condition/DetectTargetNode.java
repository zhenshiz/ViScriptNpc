package com.viscript.npc.gui.ai.node.condition;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

/** 检测范围或扇形视野内的目标，命中后执行子流程。 */
@NodeAttribute(name = "viscript_npc:detect_target", group = "condition", graphTypes = AiGraph.class)
public final class DetectTargetNode extends AiGraphNode {
    public DetectTargetNode() { super("detect_target"); }
}
