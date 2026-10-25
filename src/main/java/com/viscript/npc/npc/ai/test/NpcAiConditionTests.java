package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.*;
import com.viscript.npc.util.NpcActionFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/** 验证独立条件的判定、参数边界和变量绑定。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAiConditionTests {
    private static CustomNpc npc(GameTestHelper t) {
        for (int x = 0; x < 10; x++) for (int z = 0; z < 8; z++) t.setBlock(x, 0, z, Blocks.STONE);
        var npc = t.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(4, 1, 3));
        npc.getAiController().manualControl(); npc.setYHeadRot(0); return npc;
    }

    private static AiNodeData node(AiProgram p, String type, AiNodeData... children) {
        var node = new AiNodeData(); node.type = type;
        for (var child : children) node.children.add(child.id);
        p.nodes.add(node); return node;
    }

    private static void root(AiProgram p, AiNodeData child) { p.root = node(p, "root", child).id; }

    private static AiExecution execution(CustomNpc npc, AiProgram p) {
        AiValidation.requireValid(p, NpcActionFiles::load);
        return new AiExecution(npc, p, new HashMap<>(), Map.of(), NpcActionFiles::load);
    }

    private static AiProgram gate(String type) {
        var p = new AiProgram(); p.kind = "action";
        root(p, node(p, type)); return p;
    }

    private static AiNodeData condition(AiProgram p) { return p.node(p.node(p.root).children.getFirst()); }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void individualConditionsDoNotEnableUnrelatedChecks(GameTestHelper t) {
        var npc = npc(t); var target = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 6));
        var exists = gate("target_exists");
        t.assertTrue(execution(npc, exists).tick() == AiStatus.FAILURE, "target existence fails without a target");
        npc.setTarget(target); target.setPos(npc.position().add(0, 0, 100));
        t.assertTrue(execution(npc, exists).tick() == AiStatus.SUCCESS, "existence alone imposes no hidden distance constraint");
        var distance = gate("target_distance");
        condition(distance).input("min_distance", AiValue.number(2)).input("max_distance", AiValue.number(4));
        target.setPos(npc.position().add(0, 0, 4));
        t.assertTrue(execution(npc, distance).tick() == AiStatus.SUCCESS, "maximum distance is inclusive");
        target.setPos(npc.position().add(0, 0, 2));
        t.assertTrue(execution(npc, distance).tick() == AiStatus.SUCCESS, "minimum distance is inclusive");
        target.setPos(npc.position().add(0, 0, 1.9));
        t.assertTrue(execution(npc, distance).tick() == AiStatus.FAILURE, "distance rejects values below the interval");
        target.setPos(npc.position().add(0, 0, 3));
        var sight = gate("target_sight");
        t.assertTrue(execution(npc, sight).tick() == AiStatus.SUCCESS, "unobstructed target is visible");
        for (int y = 1; y <= 3; y++) t.setBlock(4, y, 4, Blocks.STONE);
        t.assertTrue(execution(npc, sight).tick() == AiStatus.FAILURE, "wall blocks the sight condition");
        t.assertTrue(execution(npc, exists).tick() == AiStatus.SUCCESS, "existence alone imposes no hidden visibility constraint");
        npc.setTarget(null); npc.setHealth(npc.getMaxHealth() * .5f);
        var health = gate("self_health");
        t.assertTrue(execution(npc, health).tick() == AiStatus.SUCCESS, "self health condition works without a target at its boundary");
        condition(health).input("max_health_ratio", AiValue.number(.49));
        t.assertTrue(execution(npc, health).tick() == AiStatus.FAILURE, "health threshold compares the NPC own ratio");
        condition(health).input("max_health_ratio", AiValue.number(1.1));
        t.assertTrue(!AiValidation.validate(health, NpcActionFiles::load).isEmpty(), "invalid ratio is rejected before publication");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void numericConditionAcceptsVariablesWithoutATarget(GameTestHelper t) {
        var npc = npc(t); var p = gate("number_compare");
        var variable = new AiVariable(); variable.name = "stamina"; variable.initial = AiValue.number(21); p.variables.add(variable);
        var binding = new AiBinding(); binding.scope = "local"; binding.variableId = variable.id;
        condition(p).inputs.put("left", binding);
        condition(p).input("right", AiValue.number(20)).input("compare", AiValue.choice("greater", java.util.List.of("equal", "less", "greater")));
        var e = execution(npc, p);
        t.assertTrue(e.tick() == AiStatus.SUCCESS, "comparison resolves a variable without requiring a current target");
        e.local.put(variable.id, AiValue.number(20));
        t.assertTrue(e.tick() == AiStatus.FAILURE, "greater comparison is strict at the threshold");
        condition(p).input("compare", AiValue.choice("equal", java.util.List.of("equal", "less", "greater")));
        variable.initial.number = 20;
        t.assertTrue(execution(npc, p).tick() == AiStatus.SUCCESS, "equality comparison accepts the boundary");
        condition(p).input("compare", AiValue.choice("less", java.util.List.of("equal", "less", "greater"))).input("right", AiValue.number(0)); variable.initial.number = -1;
        t.assertTrue(execution(npc, p).tick() == AiStatus.SUCCESS, "numeric conditions accept negative variable values");
        t.succeed();
    }


}
