package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

public final class AMIntentionNodes {
    private AMIntentionNodes() {
    }

    @NodeAttribute(name = "viscript_npc.ai.am.definition", group = AMIntentionNode.CATEGORY_DEFINITION,
            priority = 1000, graphTypes = AMIntentionGraph.class)
    public static class Definition extends AMIntentionNode {
        public Component getDisplayName() { return name("definition"); }
        public void onDefineOptions(IOptionDefinitionContext c) {
            string(c, "asset_id", "viscript_npc:new_intention");
            string(c, "name", "intention.viscript_npc.new_intention");
            string(c, "description", "intention.viscript_npc.new_intention.description");
            string(c, "category", "viscript_npc:custom");
            c.addOption("parameters_json", com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.STRING)
                    .withDisplayName(name("parameters_json")).withDefaultValue("{}")
                    .withConfigurable(NpcAiOptionConfigurables.parameterSchemaJson());
        }
        public void onDefinePorts(IPortDefinitionContext c) { output(c, "entry"); }
    }

    @NodeAttribute(name = "viscript_npc.ai.am.invoke", group = AMIntentionNode.CATEGORY_ACTION,
            priority = 900, graphTypes = AMIntentionGraph.class)
    public static class Invoke extends AMIntentionNode {
        public Component getDisplayName() { return name("invoke"); }
        public void onDefineOptions(IOptionDefinitionContext c) {
            intention(c, "attentionmind:idle");
            arguments(c);
            integer(c, "timeout_ticks", 0);
            string(c, "guard_json", "");
        }
        public void onDefinePorts(IPortDefinitionContext c) {
            input(c); output(c, "success"); output(c, "failure");
        }
    }

    @NodeAttribute(name = "viscript_npc.ai.am.condition", group = AMIntentionNode.CATEGORY_FLOW,
            priority = 850, graphTypes = AMIntentionGraph.class)
    public static class Condition extends AMIntentionNode {
        public Component getDisplayName() { return name("condition"); }
        public void onDefineOptions(IOptionDefinitionContext c) { string(c, "condition_json", "true"); }
        public void onDefinePorts(IPortDefinitionContext c) {
            input(c); output(c, "true"); output(c, "false");
        }
    }

    @NodeAttribute(name = "viscript_npc.ai.am.delay", group = AMIntentionNode.CATEGORY_FLOW,
            priority = 800, graphTypes = AMIntentionGraph.class)
    public static class Delay extends AMIntentionNode {
        public Component getDisplayName() { return name("delay"); }
        public void onDefineOptions(IOptionDefinitionContext c) { integer(c, "ticks", 20); }
        public void onDefinePorts(IPortDefinitionContext c) { input(c); output(c, "next"); }
    }

    @NodeAttribute(name = "viscript_npc.ai.am.set", group = AMIntentionNode.CATEGORY_ACTION,
            priority = 750, graphTypes = AMIntentionGraph.class)
    public static class SetLocal extends AMIntentionNode {
        public Component getDisplayName() { return name("set"); }
        public void onDefineOptions(IOptionDefinitionContext c) {
            string(c, "name", "value"); string(c, "value_json", "null");
        }
        public void onDefinePorts(IPortDefinitionContext c) { input(c); output(c, "next"); }
    }

    @NodeAttribute(name = "viscript_npc.ai.am.loop", group = AMIntentionNode.CATEGORY_FLOW,
            priority = 700, graphTypes = AMIntentionGraph.class)
    public static class Loop extends AMIntentionNode {
        public Component getDisplayName() { return name("loop"); }
        public void onDefineOptions(IOptionDefinitionContext c) { integer(c, "max_iterations", 1); }
        public void onDefinePorts(IPortDefinitionContext c) {
            input(c); output(c, "body"); output(c, "complete");
        }
    }

    @NodeAttribute(name = "viscript_npc.ai.am.retry", group = AMIntentionNode.CATEGORY_FLOW,
            priority = 650, graphTypes = AMIntentionGraph.class)
    public static class Retry extends AMIntentionNode {
        public Component getDisplayName() { return name("retry"); }
        public void onDefineOptions(IOptionDefinitionContext c) {
            integer(c, "max_attempts", 3); integer(c, "delay_ticks", 0);
        }
        public void onDefinePorts(IPortDefinitionContext c) {
            input(c); output(c, "target"); output(c, "success"); output(c, "failure");
        }
    }

    @NodeAttribute(name = "viscript_npc.ai.am.complete", group = AMIntentionNode.CATEGORY_FLOW,
            priority = 500, graphTypes = AMIntentionGraph.class)
    public static class Complete extends AMIntentionNode {
        public Component getDisplayName() { return name("complete"); }
        public void onDefinePorts(IPortDefinitionContext c) { input(c); }
    }

    @NodeAttribute(name = "viscript_npc.ai.am.fail", group = AMIntentionNode.CATEGORY_FLOW,
            priority = 490, graphTypes = AMIntentionGraph.class)
    public static class Fail extends AMIntentionNode {
        public Component getDisplayName() { return name("fail"); }
        public void onDefineOptions(IOptionDefinitionContext c) { string(c, "code", "INTENTION_FAILED"); }
        public void onDefinePorts(IPortDefinitionContext c) { input(c); }
    }
}
