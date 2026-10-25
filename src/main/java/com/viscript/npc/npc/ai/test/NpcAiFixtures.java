package com.viscript.npc.npc.ai.test;

import com.viscript.npc.npc.ai.data.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 测试使用的当前格式最小行为，不向玩家行为库注册案例。 */
public final class NpcAiFixtures {
    private NpcAiFixtures() {}

    public static AiProgram action(String operation) {
        var program = new AiProgram(); program.kind = "action";
        var node = new AiNodeData(); node.type = operation;
        if (operation.equals("find_target")) node.input("filter",
                AiValue.choice("player", java.util.List.of("hostile", "player", "living")));
        var root = new AiNodeData(); root.type = "root"; root.children.add(node.id);
        program.root = root.id; program.nodes.add(root); program.nodes.add(node);
        return program;
    }

    public static AiProgram movementAction() {
        var program = action("approach");
        var node = program.nodes.getLast();
        for (String name : new String[]{"speed", "range"}) {
            var parameter = new AiVariable(); parameter.name = name;
            parameter.id = UUID.nameUUIDFromBytes(("test:movement:" + name).getBytes(StandardCharsets.UTF_8)).toString();
            parameter.initial = AiValue.number(name.equals("speed") ? .5 : 2);
            parameter.displayNameKey = "viscript_npc.ai.parameter." + name;
            parameter.tipsKey = parameter.displayNameKey + ".tips";
            program.parameters.add(parameter);
            var binding = new AiBinding(); binding.scope = "parameter"; binding.variableId = parameter.id;
            binding.value = parameter.initial.copy(); node.inputs.put(name, binding);
        }
        return program;
    }
}
