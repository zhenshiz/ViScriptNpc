package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IPort;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortOrientation;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

public abstract class FlowNode extends Node {
    public static final String PORT_IN = "in";
    public static final String PORT_CHILD = "child";
    public static final String PORT_CHILDREN = "children";
    public static final String CATEGORY_TRIGGER = "viscript_npc.ai.category.trigger";
    public static final String CATEGORY_FLOW = "viscript_npc.ai.category.flow";
    public static final String CATEGORY_ACTION = "viscript_npc.ai.category.action";

    protected Component name(String key) { return Component.translatable("viscript_npc.ai.flow." + key); }

    protected void input(IPortDefinitionContext context) {
        IPort port = context.addInputPort(PORT_IN, TypeHandles.EXECUTION_FLOW)
                .withOrientation(PortOrientation.Vertical).withoutConfigurator().build();
        if (port instanceof PortModel model) model.setPortCapacity(PortCapacity.SINGLE);
    }

    protected void child(IPortDefinitionContext context) {
        IPort port = context.addOutputPort(PORT_CHILD, TypeHandles.EXECUTION_FLOW)
                .withOrientation(PortOrientation.Vertical).build();
        if (port instanceof PortModel model) model.setPortCapacity(PortCapacity.SINGLE);
    }

    protected void children(IPortDefinitionContext context) {
        IPort port = context.addOutputPort(PORT_CHILDREN, TypeHandles.EXECUTION_FLOW)
                .withOrientation(PortOrientation.Vertical).build();
        if (port instanceof PortModel model) model.setPortCapacity(PortCapacity.MULTIPLE);
    }

    protected void string(IOptionDefinitionContext context, String id, String value) {
        context.addOption(id, TypeHandles.STRING).withDisplayName(name(id)).withDefaultValue(value);
    }
    protected void intention(IOptionDefinitionContext context, String value) {
        context.addOption("intention", TypeHandles.STRING).withDisplayName(name("intention"))
                .withDefaultValue(value).withConfigurable(NpcAiOptionConfigurables.INTENTION_ID);
    }
    protected void trigger(IOptionDefinitionContext context, String value) {
        context.addOption("trigger", TypeHandles.STRING).withDisplayName(name("trigger"))
                .withDefaultValue(value).withConfigurable(NpcAiOptionConfigurables.TRIGGER_ID);
    }
    protected void condition(IOptionDefinitionContext context, String value) {
        context.addOption("condition", TypeHandles.STRING).withDisplayName(name("condition"))
                .withDefaultValue(value).withConfigurable(NpcAiOptionConfigurables.CONDITION_ID);
    }
    protected void submitParameters(IOptionDefinitionContext context) {
        context.addOption("parameters_json", TypeHandles.STRING).withDisplayName(name("parameters_json"))
                .withDefaultValue("{}").withConfigurable(NpcAiOptionConfigurables.flowParameterBindings(() -> {
                    var option = getNodeOptionById("intention");
                    return option == null ? "" : option.<String>tryGetValue(String.class).result().orElse("");
                }));
    }
    protected void nodeParameters(IOptionDefinitionContext context, String kind, String optionId) {
        context.addOption("parameters_json", TypeHandles.STRING).withDisplayName(name("parameters_json"))
                .withDefaultValue("{}").withConfigurable(NpcAiOptionConfigurables.nodeParameters(() -> kind, () -> {
                    var option = getNodeOptionById(optionId);
                    return option == null ? "" : option.<String>tryGetValue(String.class).result().orElse("");
                }));
    }
    protected void integer(IOptionDefinitionContext context, String id, int value) {
        context.addOption(id, TypeHandles.INT).withDisplayName(name(id)).withDefaultValue(value);
    }
    protected void decimal(IOptionDefinitionContext context, String id, float value) {
        context.addOption(id, TypeHandles.FLOAT).withDisplayName(name(id)).withDefaultValue(value);
    }
    protected void bool(IOptionDefinitionContext context, String id, boolean value) {
        context.addOption(id, TypeHandles.BOOL).withDisplayName(name(id)).withDefaultValue(value);
    }
}
