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

public abstract class AMIntentionNode extends Node {
    public static final String CATEGORY_DEFINITION = "viscript_npc.ai.am.category.definition";
    public static final String CATEGORY_FLOW = "viscript_npc.ai.am.category.flow";
    public static final String CATEGORY_ACTION = "viscript_npc.ai.am.category.action";

    protected Component name(String key) {
        return Component.translatable("viscript_npc.ai.am." + key);
    }

    protected void input(IPortDefinitionContext context) {
        port(context.addInputPort("in", TypeHandles.EXECUTION_FLOW)
                .withOrientation(PortOrientation.Vertical).withoutConfigurator().build());
    }

    protected void output(IPortDefinitionContext context, String id) {
        port(context.addOutputPort(id, TypeHandles.EXECUTION_FLOW)
                .withOrientation(PortOrientation.Vertical).build());
    }

    protected void string(IOptionDefinitionContext context, String id, String value) {
        context.addOption(id, TypeHandles.STRING).withDisplayName(name(id)).withDefaultValue(value);
    }

    protected void intention(IOptionDefinitionContext context, String value) {
        context.addOption("intention", TypeHandles.STRING).withDisplayName(name("intention"))
                .withDefaultValue(value).withConfigurable(NpcAiOptionConfigurables.INTENTION_ID);
    }

    protected void arguments(IOptionDefinitionContext context) {
        context.addOption("arguments_json", TypeHandles.STRING).withDisplayName(name("arguments_json"))
                .withDefaultValue("{}").withConfigurable(NpcAiOptionConfigurables.intentionArguments(() -> {
                    var option = getNodeOptionById("intention");
                    return option == null ? "" : option.<String>tryGetValue(String.class).result().orElse("");
                }));
    }

    protected void integer(IOptionDefinitionContext context, String id, int value) {
        context.addOption(id, TypeHandles.INT).withDisplayName(name(id)).withDefaultValue(value);
    }

    private static void port(IPort port) {
        if (port instanceof PortModel model) model.setPortCapacity(PortCapacity.SINGLE);
    }
}
