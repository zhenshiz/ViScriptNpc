package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

public final class FlowNodes {
    private FlowNodes() {}

    @NodeAttribute(name="viscript_npc.ai.flow.trigger", group=FlowNode.CATEGORY_TRIGGER, priority=1000, graphTypes=NpcFlowGraph.class)
    public static class Trigger extends FlowNode {
        public Component getDisplayName(){return name("trigger");}
        public void onDefineOptions(IOptionDefinitionContext c){trigger(c,"tick");integer(c,"interval",1);decimal(c,"range",8);nodeParameters(c,"trigger","trigger");}
        public void onDefinePorts(IPortDefinitionContext c){child(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.sequence", group=FlowNode.CATEGORY_FLOW, priority=900, graphTypes=NpcFlowGraph.class)
    public static class Sequence extends FlowNode {
        public Component getDisplayName(){return name("sequence");}
        public void onDefinePorts(IPortDefinitionContext c){input(c);children(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.branch", group=FlowNode.CATEGORY_FLOW, priority=890, graphTypes=NpcFlowGraph.class)
    public static class Branch extends FlowNode {
        public Component getDisplayName(){return name("branch");}
        public void onDefinePorts(IPortDefinitionContext c){input(c);children(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.case", group=FlowNode.CATEGORY_FLOW, priority=880, graphTypes=NpcFlowGraph.class)
    public static class Case extends FlowNode {
        public Component getDisplayName(){return name("case");}
        public void onDefineOptions(IOptionDefinitionContext c){
            condition(c,"always"); decimal(c,"probability",0.5f); decimal(c,"range",8);
            decimal(c,"health",10.0f); decimal(c,"ratio",0.5f); string(c,"entity_type","minecraft:player");
            string(c,"tag",""); string(c,"world","minecraft:overworld");
            string(c,"dimension","minecraft:overworld"); string(c,"variable","");
            nodeParameters(c,"condition","condition");
        }
        public void onDefinePorts(IPortDefinitionContext c){input(c);children(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.delay", group=FlowNode.CATEGORY_FLOW, priority=700, graphTypes=NpcFlowGraph.class)
    public static class Delay extends FlowNode {
        public Component getDisplayName(){return name("delay");}
        public void onDefineOptions(IOptionDefinitionContext c){integer(c,"ticks",20);}
        public void onDefinePorts(IPortDefinitionContext c){input(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.loop", group=FlowNode.CATEGORY_FLOW, priority=690, graphTypes=NpcFlowGraph.class)
    public static class Loop extends FlowNode {
        public Component getDisplayName(){return name("loop");}
        public void onDefineOptions(IOptionDefinitionContext c){integer(c,"max_iterations",1);}
        public void onDefinePorts(IPortDefinitionContext c){input(c);child(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.retry", group=FlowNode.CATEGORY_FLOW, priority=680, graphTypes=NpcFlowGraph.class)
    public static class Retry extends FlowNode {
        public Component getDisplayName(){return name("retry");}
        public void onDefineOptions(IOptionDefinitionContext c){integer(c,"max_iterations",3);}
        public void onDefinePorts(IPortDefinitionContext c){input(c);child(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.set_variable", group=FlowNode.CATEGORY_ACTION, priority=650, graphTypes=NpcFlowGraph.class)
    public static class SetVariable extends FlowNode {
        public Component getDisplayName(){return name("set_variable");}
        public void onDefineOptions(IOptionDefinitionContext c){string(c,"name","");string(c,"value_json","null");}
        public void onDefinePorts(IPortDefinitionContext c){input(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.variable", group=FlowNode.CATEGORY_ACTION, priority=640, graphTypes=NpcFlowGraph.class)
    public static class Variable extends FlowNode {
        public Component getDisplayName(){return name("variable");}
        public void onDefineOptions(IOptionDefinitionContext c){
            string(c,"name","value"); string(c,"type","string"); bool(c,"persistent",false);
            string(c,"default_json","null");
        }
    }
    @NodeAttribute(name="viscript_npc.ai.flow.submit", group=FlowNode.CATEGORY_ACTION, priority=600, graphTypes=NpcFlowGraph.class)
    public static class Submit extends FlowNode {
        public Component getDisplayName(){return name("submit");}
        public void onDefineOptions(IOptionDefinitionContext c){
            intention(c,"attentionmind:idle"); string(c,"priority","NORMAL");
            submitParameters(c);
        }
        public void onDefinePorts(IPortDefinitionContext c){input(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.end", group=FlowNode.CATEGORY_FLOW, priority=500, graphTypes=NpcFlowGraph.class)
    public static class End extends FlowNode {
        public Component getDisplayName(){return name("end");}
        public void onDefinePorts(IPortDefinitionContext c){input(c);}
    }
    @NodeAttribute(name="viscript_npc.ai.flow.fail", group=FlowNode.CATEGORY_FLOW, priority=490, graphTypes=NpcFlowGraph.class)
    public static class Fail extends FlowNode {
        public Component getDisplayName(){return name("fail");}
        public void onDefinePorts(IPortDefinitionContext c){input(c);}
    }
}
