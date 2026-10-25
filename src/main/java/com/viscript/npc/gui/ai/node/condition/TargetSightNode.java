package com.viscript.npc.gui.ai.node.condition;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

/** 检查 NPC 与当前目标之间是否有无遮挡的视线。 */
@NodeAttribute(name = "viscript_npc:target_sight", group = "condition", graphTypes = AiGraph.class)
public final class TargetSightNode extends AiGraphNode {
    public TargetSightNode() { super("target_sight"); }
}
