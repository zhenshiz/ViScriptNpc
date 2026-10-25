package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.npc.ai.data.*;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 模组内置学习示例；复制后可独立编辑，与玩家文件使用相同的执行器。 */
public final class AiExamples {
    public static final List<String> NAMES = List.of("patrol", "pursue_melee", "pursue_ranged", "melee", "ranged", "jump_slash");
    public static final List<String> TREE_NAMES = List.of("guard", "ranged_guard", "patroller");

    public static AiProgram action(String name) {
        if (!NAMES.contains(name)) return null;
        Builder b = new Builder("action", name);
        switch (name) {
            case "melee", "ranged" -> {
                b.parameter("damage", AiValue.number(name.equals("melee") ? 4 : 3));
                b.parameter("attack_interval_seconds", AiValue.number(.6));
                b.root(b.node("sequence", b.node("look"), b.node("swing"), b.bind(b.node(name), "damage", "damage"),
                        b.bind(b.node("wait"), "seconds", "attack_interval_seconds")));
            }
            case "patrol" -> {
                b.parameter("patrol_radius", AiValue.number(6));
                b.parameter("speed", AiValue.number(.5));
                b.root(b.bind(b.bind(b.node("patrol"), "patrol_radius", "patrol_radius"), "speed", "speed"));
            }
            case "pursue_melee", "pursue_ranged" -> {
                boolean melee = name.equals("pursue_melee");
                b.parameter("damage", AiValue.number(melee ? 4 : 3));
                b.parameter("speed", AiValue.number(.5));
                b.parameter("attack_interval_seconds", AiValue.number(.6));
                var approach = b.bind(b.node("approach").input("range", AiValue.number(melee ? 1.8 : 12)), "speed", "speed");
                var attack = b.bind(b.node(melee ? "melee" : "ranged"), "damage", "damage");
                b.root(b.node("sequence", approach, b.node("look"), b.node("swing"), attack,
                        b.bind(b.node("wait"), "seconds", "attack_interval_seconds")));
            }
            case "jump_slash" -> {
                b.parameter("damage", AiValue.number(6));
                var radius = b.parameter("range", AiValue.number(2));
                radius.displayNameKey = "viscript_npc.ai.parameter.impact_radius";
                radius.tipsKey = radius.displayNameKey + ".tips";
                AiVariable landing = b.variable("landing", AiValue.position(Vec3.ZERO));
                AiNodeData position = b.node("position").input("output", AiValue.text(landing.id)).input("forward", AiValue.number(.5));
                AiNodeData jump = b.node("jump");
                AiBinding binding = new AiBinding(); binding.scope = "local"; binding.variableId = landing.id; binding.value = landing.initial.copy();
                jump.inputs.put("position", binding);
                AiNodeData windup = b.node("stage", b.node("look"), b.node("sound"), b.node("particle"),
                        b.node("wait").input("seconds", AiValue.number(.35))).input("name", AiValue.text("windup"));
                AiNodeData air = b.node("stage", position, jump, b.node("wait_ground")).input("name", AiValue.text("airborne"))
                        .input("interruptible", AiValue.bool(false));
                AiNodeData hit = b.node("stage", b.node("particle"), b.node("swing"),
                        b.bind(b.bind(b.node("area_damage"), "damage", "damage"), "range", "range"))
                        .input("name", AiValue.text("impact")).input("interruptible", AiValue.bool(false));
                AiNodeData recovery = b.node("stage", b.node("wait").input("seconds", AiValue.number(.5)))
                        .input("name", AiValue.text("recovery"));
                b.root(b.node("sequence", windup, air, hit, recovery));
            }
        }
        return b.program.setDisplayNameKey("viscript_npc.ai.action." + name);
    }

    public static AiProgram tree(String name) {
        Builder b = new Builder("tree", name);
        if (name.equals("guard") || name.equals("ranged_guard")) {
            var combat = b.node("reactive_sequence", b.node("target_exists"),
                    b.call(name.equals("guard") ? "pursue_melee" : "pursue_ranged"));
            var decision = b.node("reactive_selector", combat, b.call("patrol"));
            b.root(b.node("sense_targets", decision).input("filter", AiValue.choice("player", java.util.List.of("hostile", "player", "living"))));
        } else if (name.equals("patroller")) b.root(b.call("patrol"));
        else if (name.equals("melee") || name.equals("ranged") || name.equals("jump_slash")) {
            AiNodeData attack = b.call(name);
            if (name.equals("jump_slash")) attack = b.node("cooldown", attack).input("seconds", AiValue.number(3));
            double range = name.equals("ranged") ? 16 : name.equals("jump_slash") ? 3.5 : 2;
            AiNodeData inRange = b.node("reactive_sequence", b.node("target_distance").input("max_distance", AiValue.number(range)),
                    b.node("target_sight"), attack);
            AiNodeData pursuit = b.node("reactive_sequence", b.node("target_exists"),
                    b.call(name.equals("ranged") ? "pursue_ranged" : "pursue_melee"));
            b.root(b.node("reactive_selector", inRange, pursuit, b.node("success")));
        }
        return b.program;
    }

    private static final class Builder {
        final AiProgram program = new AiProgram();
        final String namespace;
        Builder(String kind, String name) { program.kind = kind; namespace = kind + ":" + name + ":"; }
        String id(String key) { return UUID.nameUUIDFromBytes((namespace + key).getBytes(StandardCharsets.UTF_8)).toString(); }
        AiNodeData node(String type, AiNodeData... children) {
            AiNodeData node = new AiNodeData(); node.type = type; node.id = id("node:" + program.nodes.size());
            for (AiNodeData child : children) node.children.add(child.id);
            program.nodes.add(node); return node;
        }
        void root(AiNodeData child) { program.root = node("root", child).id; }
        AiNodeData call(String file) { return node("call").input("file", AiValue.text("examples/" + file)); }
        AiVariable parameter(String name, AiValue initial) {
            AiVariable v = new AiVariable(); v.id = id(name); v.name = name; v.initial = initial;
            v.displayNameKey = "viscript_npc.ai.parameter." + name;
            v.tipsKey = v.displayNameKey + ".tips";
            program.parameters.add(v); return v;
        }
        AiVariable variable(String name, AiValue initial) {
            AiVariable v = parameter(name, initial); program.parameters.remove(v); program.variables.add(v); return v;
        }
        AiNodeData bind(AiNodeData node, String field, String parameter) {
            AiVariable v = program.parameters.stream().filter(p -> p.name.equals(parameter)).findFirst().orElseThrow();
            AiBinding binding = new AiBinding(); binding.scope = "parameter"; binding.variableId = v.id; binding.value = v.initial.copy();
            node.inputs.put(field, binding); return node;
        }
    }
}
