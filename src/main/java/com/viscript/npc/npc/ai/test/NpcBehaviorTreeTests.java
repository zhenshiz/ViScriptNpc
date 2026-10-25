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
import java.util.List;
import java.util.Map;

/** 验证标准行为树组合、响应式取消、装饰节点。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcBehaviorTreeTests {
    private static boolean registered;
    private static synchronized void registerProbe() {
        if (registered) return;
        AiActions.register(new AiOperations.Operation("bt_test_probe", "action", 0, List.of(
                new AiOperations.Field("key", AiValue.text("probe")),
                new AiOperations.Field("result", AiValue.text("running")))), new AiActions.Executor() {
            @Override public AiStatus tick(AiExecution e, AiExecution.Frame f) {
                String key = e.text(f.node, "key");
                e.blackboard.put(key + ".ticks", AiValue.number(count(e.blackboard, key + ".ticks") + 1));
                if (f.age == 1) e.blackboard.put(key + ".starts", AiValue.number(count(e.blackboard, key + ".starts") + 1));
                return AiStatus.valueOf(e.blackboard.getOrDefault(key + ".result", AiValue.text(e.text(f.node, "result"))).text.toUpperCase(java.util.Locale.ROOT));
            }
            @Override public void cancel(AiExecution e, AiExecution.Frame f) {
                String key = e.text(f.node, "key") + ".cancels";
                e.blackboard.put(key, AiValue.number(count(e.blackboard, key) + 1));
            }
        });
        registered = true;
    }

    private static double count(Map<String, AiValue> bb, String key) { return bb.getOrDefault(key, AiValue.number(0)).number; }
    private static CustomNpc npc(GameTestHelper t) {
        for (int x = 0; x < 10; x++) for (int z = 0; z < 8; z++) t.setBlock(x, 0, z, Blocks.STONE);
        var npc = t.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(4, 1, 3));
        npc.getAiController().manualControl(); npc.setYHeadRot(0); return npc;
    }
    private static AiProgram program() { var p = new AiProgram(); p.kind = "action"; return p; }
    private static AiNodeData node(AiProgram p, String type, AiNodeData... children) {
        var n = new AiNodeData(); n.type = type;
        for (var child : children) n.children.add(child.id);
        p.nodes.add(n); return n;
    }
    private static void root(AiProgram p, AiNodeData child) { p.root = node(p, "root", child).id; }
    private static AiNodeData probe(AiProgram p, String key, String result) {
        registerProbe();
        return node(p, "bt_test_probe").input("key", AiValue.text(key)).input("result", AiValue.text(result));
    }
    private static AiExecution execution(CustomNpc npc, AiProgram p, Map<String, AiValue> bb) {
        AiValidation.requireValid(p, NpcActionFiles::load);
        return new AiExecution(npc, p, bb, Map.of(), NpcActionFiles::load);
    }
    private static AiNodeData check(AiProgram p) {
        var v = new AiVariable(); v.name = "enabled"; v.initial = AiValue.number(0); p.npcVariables.add(v);
        var binding = new AiBinding(); binding.scope = "blackboard"; binding.variableId = v.id;
        var n = node(p, "number_compare").input("compare", AiValue.choice("greater", java.util.List.of("equal", "less", "greater"))); n.inputs.put("left", binding);
        return n;
    }
    private static void enable(AiProgram p, Map<String, AiValue> bb, boolean enabled) { bb.put(p.npcVariables.getFirst().id, AiValue.number(enabled ? 1 : 0)); }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void conditionLeavesAndInversion(GameTestHelper t) {
        var npc = npc(t); var p = program();
        root(p, node(p, "invert", node(p, "target_exists")));
        t.assertTrue(execution(npc, p, new HashMap<>()).tick() == AiStatus.SUCCESS, "condition leaf can be inverted without a dummy child");
        npc.setTarget(t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 6)));
        t.assertTrue(execution(npc, p, new HashMap<>()).tick() == AiStatus.FAILURE, "inverter swaps successful predicate result");
        p = program(); var condition = node(p, "target_exists", node(p, "success")); root(p, condition);
        t.assertTrue(AiValidation.validate(p, NpcActionFiles::load).stream().anyMatch(i -> i.nodeId().equals(condition.id) && i.key().equals("children")), "new conditions reject control children");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ordinaryResultsAndExplicitReturn(GameTestHelper t) {
        var npc = npc(t); var p = program(); var bb = new HashMap<String, AiValue>();
        root(p, node(p, "sequence", node(p, "invert", node(p, "failure")), probe(p, "after", "success")));
        t.assertTrue(execution(npc, p, bb).tick() == AiStatus.SUCCESS && count(bb, "after.ticks") == 1, "ordinary failure can be inverted and execution continues");
        p = program(); root(p, node(p, "selector", node(p, "failure"), node(p, "success")));
        t.assertTrue(execution(npc, p, bb).tick() == AiStatus.SUCCESS, "ordinary failure allows selector fallback");
        p = program(); root(p, node(p, "sequence", node(p, "invert", node(p, "finish_success")), probe(p, "skipped", "success")));
        t.assertTrue(execution(npc, p, bb).tick() == AiStatus.SUCCESS && !bb.containsKey("skipped.ticks"), "explicit finish exits only its execution instead of being inverted");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sequenceRemembersRunningChild(GameTestHelper t) {
        var p = program(); var bb = new HashMap<String, AiValue>();
        root(p, node(p, "sequence", probe(p, "first", "success"), probe(p, "wait", "running"), probe(p, "last", "success")));
        var e = execution(npc(t), p, bb); e.tick(); e.tick();
        t.assertTrue(count(bb, "first.ticks") == 1 && count(bb, "wait.starts") == 1 && !bb.containsKey("last.ticks"), "sequence preserves progress and never repeats completed actions");
        bb.put("wait.result", AiValue.text("success"));
        t.assertTrue(e.tick() == AiStatus.SUCCESS && count(bb, "last.ticks") == 1, "sequence continues immediately after running child succeeds"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void memorySelectorDoesNotPreempt(GameTestHelper t) {
        var p = program(); var bb = new HashMap<String, AiValue>();
        root(p, node(p, "selector", node(p, "sequence", check(p), probe(p, "high", "running")), probe(p, "low", "running")));
        enable(p, bb, false); var e = execution(npc(t), p, bb); e.tick(); enable(p, bb, true); e.tick();
        t.assertTrue(count(bb, "low.starts") == 1 && count(bb, "low.ticks") == 2 && !bb.containsKey("high.ticks"), "ordinary selector resumes its running branch without hidden priority probes");
        e.cancel(); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void reactiveSelectionRechecksAndCancels(GameTestHelper t) {
        var p = program(); var bb = new HashMap<String, AiValue>();
        root(p, node(p, "reactive_selector", node(p, "reactive_sequence", check(p), probe(p, "high", "running")), probe(p, "low", "running")));
        enable(p, bb, false); var e = execution(npc(t), p, bb); e.tick(); enable(p, bb, true); e.tick(); e.tick();
        t.assertTrue(count(bb, "low.cancels") == 1 && count(bb, "high.starts") == 1 && count(bb, "high.ticks") == 2, "reactive preemption cancels once and preserves the new running child");
        enable(p, bb, false); e.tick();
        t.assertTrue(count(bb, "high.cancels") == 1 && count(bb, "low.starts") == 2, "failed preceding condition cancels action and selects fallback");
        e.cancel(); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void reactiveSelectionHonorsUninterruptibleStages(GameTestHelper t) {
        var p = program(); var bb = new HashMap<String, AiValue>();
        var locked = node(p, "stage", probe(p, "locked", "running")).input("interruptible", AiValue.bool(false));
        root(p, node(p, "reactive_selector", node(p, "reactive_sequence", check(p), probe(p, "high", "running")),
                node(p, "sequence", locked, probe(p, "recovery", "running"))));
        enable(p, bb, false); var e = execution(npc(t), p, bb); e.tick(); enable(p, bb, true); e.tick();
        t.assertTrue(!bb.containsKey("high.ticks") && count(bb, "locked.cancels") == 0, "higher priority cannot interrupt a locked stage");
        bb.put("locked.result", AiValue.text("success")); e.tick(); e.tick();
        t.assertTrue(count(bb, "high.starts") == 1 && count(bb, "recovery.cancels") == 1, "reevaluates priorities after the locked stage boundary");
        e.cancel(); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void preemptionKeepsNewNavigationAlive(GameTestHelper t) {
        var npc = npc(t); var p = program(); var bb = new HashMap<String, AiValue>();
        var high = node(p, "navigate").input("position", AiValue.position(npc.position().add(2, 0, 0)));
        var low = node(p, "navigate").input("position", AiValue.position(npc.position().add(-2, 0, 0)));
        root(p, node(p, "reactive_selector", node(p, "reactive_sequence", check(p), high), low));
        enable(p, bb, false); var e = execution(npc, p, bb); e.tick(); enable(p, bb, true); e.tick();
        t.assertTrue(e.activeNodes().contains(high.id) && !e.activeNodes().contains(low.id)
                        && npc.getNpcNavigation().status() != com.viscript.npc.npc.ai.navigation.NavigationStatus.CANCELLED,
                "cancelling the displaced branch does not cancel the replacement navigation");
        e.cancel(); t.assertTrue(npc.getNpcNavigation().status() == com.viscript.npc.npc.ai.navigation.NavigationStatus.CANCELLED, "cancelling the whole tree releases its navigation"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void parallelRemembersCompletedChildren(GameTestHelper t) {
        var p = program(); var bb = new HashMap<String, AiValue>();
        root(p, node(p, "parallel", probe(p, "done", "success"), probe(p, "pending", "running")));
        var e = execution(npc(t), p, bb); e.tick(); e.tick();
        t.assertTrue(count(bb, "done.ticks") == 1 && count(bb, "pending.ticks") == 2, "parallel advances running children without repeating finished siblings");
        bb.put("pending.result", AiValue.text("success")); t.assertTrue(e.tick() == AiStatus.SUCCESS, "all-success parallel waits for every success");
        p = program(); bb.clear(); root(p, node(p, "parallel", probe(p, "cancelled", "running"), node(p, "success")).input("policy", AiValue.choice("any_success", java.util.List.of("all_success", "any_success"))));
        t.assertTrue(execution(npc(t), p, bb).tick() == AiStatus.SUCCESS && count(bb, "cancelled.cancels") == 1, "any-success parallel cancels unfinished siblings");
        p = program(); bb.clear(); root(p, node(p, "parallel", probe(p, "cancelled", "running"), node(p, "failure")));
        t.assertTrue(execution(npc(t), p, bb).tick() == AiStatus.FAILURE && count(bb, "cancelled.cancels") == 1, "all-success parallel fails and cancels on any failure"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void decoratorsKeepRunningAndResetIterations(GameTestHelper t) {
        var npc = npc(t); var p = program(); var bb = new HashMap<String, AiValue>();
        root(p, node(p, "repeat", probe(p, "repeat", "success")).input("count", AiValue.number(3)));
        var e = execution(npc, p, bb);
        t.assertTrue(e.tick() == AiStatus.RUNNING && e.tick() == AiStatus.RUNNING && e.tick() == AiStatus.SUCCESS && count(bb, "repeat.starts") == 3, "finite repeat creates a fresh child on each iteration");
        p = program(); root(p, node(p, "until_success", probe(p, "retry", "failure"))); e = execution(npc, p, bb);
        t.assertTrue(e.tick() == AiStatus.RUNNING, "retry yields after failure"); bb.put("retry.result", AiValue.text("success"));
        t.assertTrue(e.tick() == AiStatus.SUCCESS && count(bb, "retry.starts") == 2, "retry ends on success with a fresh child");
        p = program(); root(p, node(p, "until_failure", node(p, "failure")));
        t.assertTrue(execution(npc, p, bb).tick() == AiStatus.SUCCESS, "until-failure reports successful loop completion");
        p = program(); root(p, node(p, "force_success", probe(p, "force", "running"))); e = execution(npc, p, bb);
        t.assertTrue(e.tick() == AiStatus.RUNNING, "forced terminal status preserves running"); bb.put("force.result", AiValue.text("failure"));
        t.assertTrue(e.tick() == AiStatus.SUCCESS, "force-success converts failure");
        p = program(); root(p, node(p, "force_failure", node(p, "success")));
        t.assertTrue(execution(npc, p, bb).tick() == AiStatus.FAILURE, "force-failure converts success"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void perceptionPredicateHasNoTargetSideEffect(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 6));
        var p = program(); root(p, node(p, "detect_target").input("filter", AiValue.choice("living", java.util.List.of("hostile", "player", "living"))).input("entity_type", AiValue.mobType("minecraft:cow")));
        var e = execution(npc, p, new HashMap<>());
        t.assertTrue(e.tick() == AiStatus.SUCCESS && npc.getTarget() == null && !e.perceptions().isEmpty(), "perception condition reports a match and debug shape without assigning target");
        cow.discard(); t.assertTrue(e.tick() == AiStatus.FAILURE && npc.getTarget() == null, "missing perception candidate fails without changing target"); t.succeed();
    }

}
