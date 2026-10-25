package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;

/** 验证节点暂停保存同一轮执行位置，且不重做副作用、感知或计时。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAiNodeStepTests {
    private static CustomNpc npc(GameTestHelper t) {
        t.setBlock(2, 0, 2, Blocks.STONE);
        var npc = t.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 2));
        npc.getAiController().manualControl(); return npc;
    }
    private static AiNodeData node(AiProgram p, String type, AiNodeData... children) {
        var n = new AiNodeData(); n.type = type;
        for (var c : children) n.children.add(c.id);
        p.nodes.add(n); return n;
    }
    private static void root(AiProgram p, AiNodeData child) { p.root = node(p, "root", child).id; }
    private static AiNodeData log(AiProgram p, String message) {
        return node(p, "debug_log").input("message", AiValue.text(message)).input("value", AiValue.number(1));
    }
    private static AiNodeData wait(AiProgram p) { return node(p, "wait").input("seconds", AiValue.number(.1)); }
    private static long outputs(NpcAiController c, String node) {
        return c.snapshot().events.stream().filter(e -> e.nodeId.equals(node) && e.status.equals("value")).count();
    }
    private static void finishRound(GameTestHelper t, NpcAiController c) {
        int steps = 0;
        do { c.step(); c.tick(); } while (c.snapshot().tickInProgress && ++steps < 40);
        t.assertTrue(!c.snapshot().tickInProgress && c.isDebugPaused(), "node stepping eventually pauses at the end of this round");
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nodeStepsExposeInputsAndAdvanceWaitOnlyOncePerRound(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action";
        var v = new AiVariable(); v.initial = AiValue.number(0); p.variables.add(v); p.npcVariables.add(v);
        var first = node(p, "set_variable").input("scope", AiValue.choice("blackboard", List.of("local", "blackboard")))
                .input("variable", AiValue.text(v.id)).input("value", AiValue.number(1));
        var second = node(p, "set_variable").input("scope", AiValue.choice("blackboard", List.of("local", "blackboard")))
                .input("variable", AiValue.text(v.id)).input("value", AiValue.number(2));
        var wait = wait(p); var sequence = node(p, "sequence", first, second, wait); root(p, sequence);
        var c = npc.getAiController(); c.preview(p, Map.of()); c.pause();
        c.step(); c.tick();
        t.assertTrue(c.snapshot().currentNode.equals(sequence.id) && c.snapshot().lastNode.equals(p.root)
                && c.snapshot().tick == 1 && c.snapshot().variables.get(v.id).number == 0, "first click enters only the root and stops before sequence");
        c.step(); c.tick();
        t.assertTrue(c.snapshot().currentNode.equals(first.id) && c.snapshot().inputs.get(first.id + ":value").number == 1,
                "the next node exposes its input before it writes a variable");
        c.step(); c.tick();
        t.assertTrue(c.snapshot().variables.get(v.id).number == 1 && c.snapshot().currentNode.equals(second.id)
                && c.snapshot().lastResult.equals("success"), "one leaf writes once and exposes its result before the next leaf");
        c.step(); c.tick();
        t.assertTrue(c.snapshot().variables.get(v.id).number == 2 && c.snapshot().currentNode.equals(wait.id), "next click performs only the second assignment");
        long tick = c.snapshot().tick;
        for (int i = 0; i < 10; i++) c.tick();
        t.assertTrue(c.snapshot().tick == tick && !c.canTickNavigation(), "paused server ticks advance neither AI nor navigation");
        c.step(); c.tick();
        t.assertTrue(c.snapshot().tick == 1 && !c.snapshot().tickInProgress && c.snapshot().activeNodes.contains(wait.id)
                && c.snapshot().lastResult.equals("running") && c.canTickNavigation(), "wait ages once after multiple clicks; navigation can advance only after round completion");
        c.stepTick(); c.tick();
        t.assertTrue(c.snapshot().tick == 2 && c.snapshot().status.equals("paused") && c.snapshot().activeNodes.isEmpty(),
                "a full tick ages wait exactly once more and completes it");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void reactiveAndParallelResumeAfterEarlierRunningSiblings(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action";
        var beforeWait = log(p, "reactive"); var sibling = log(p, "parallel"); var wait = wait(p);
        root(p, node(p, "parallel", node(p, "reactive_sequence", beforeWait, wait), sibling));
        var c = npc.getAiController(); c.preview(p, Map.of()); c.pause(); finishRound(t, c);
        t.assertTrue(c.snapshot().tick == 1 && outputs(c, beforeWait.id) == 1 && outputs(c, sibling.id) == 1
                && c.snapshot().activeNodes.contains(wait.id), "resuming the parallel sibling does not repeat the earlier reactive side effect or age wait");
        c.stepTick(); c.tick();
        t.assertTrue(c.snapshot().tick == 2 && outputs(c, beforeWait.id) == 2 && outputs(c, sibling.id) == 1
                && c.snapshot().activeNodes.isEmpty(), "the next real round rechecks reactive logic and retains completed parallel siblings");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nestedCallsKeepTheirClockAcrossNodeStepsAndSnapshotRoundtrip(GameTestHelper t) {
        var npc = npc(t); var action = new AiProgram(); action.kind = "action";
        var log = log(action, "nested"); var wait = wait(action); root(action, node(action, "sequence", log, wait));
        var p = new AiProgram(); root(p, node(p, "call").input("file", AiValue.text("test/stepped")));
        var c = npc.getAiController(); c.preview(p, Map.of("test/stepped", action)); c.pause();
        for (int i = 0; i < 4; i++) { c.step(); c.tick(); }
        var data = c.snapshot();
        t.assertTrue(data.currentAction.equals("test/stepped") && data.currentDepth == 1 && data.tickInProgress,
                "stepping enters the referenced action and reports its call depth");
        t.assertTrue(data.currentNode.equals(log.id) && data.inputs.get(log.id + ":message").text.equals("nested"),
                "paused nested inputs are included in the main controller snapshot");
        var copy = new AiDebugSnapshot(); copy.deserializeNBT(npc.registryAccess(), data.serializeNBT(npc.registryAccess()));
        t.assertTrue(copy.currentNode.equals(data.currentNode) && copy.currentDepth == 1 && copy.lastNode.equals(data.lastNode)
                && copy.currentAction.equals(data.currentAction) && copy.tickInProgress, "RPC roundtrip preserves the node cursor and nested location");
        finishRound(t, c);
        t.assertTrue(c.snapshot().tick == 1 && outputs(c, log.id) == 1 && c.snapshot().activeNodes.contains(wait.id), "nested wait remains running after one logical round");
        c.stepTick(); c.tick();
        t.assertTrue(c.snapshot().tick == 2 && outputs(c, log.id) == 1 && c.snapshot().activeNodes.isEmpty(), "nested action clock is not increased by node clicks");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sensingDoesNotRunAgainWhileResumingItsChild(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 2));
        var p = new AiProgram(); var condition = node(p, "target_exists");
        var sense = node(p, "sense_targets", condition).input("perception", AiValue.choice("range", List.of("range", "view", "combined")))
                .input("filter", AiValue.choice("living", List.of("hostile", "player", "living")))
                .input("entity_type", AiValue.mobType("minecraft:cow")).input("range", AiValue.number(6))
                .input("require_sight", AiValue.bool(false)); root(p, sense);
        var c = npc.getAiController(); c.preview(p, Map.of()); c.pause();
        c.step(); c.tick(); c.step(); c.tick();
        t.assertTrue(npc.getTarget() == cow && c.snapshot().currentNode.equals(condition.id), "sensing sets the target before its child executes");
        npc.setTarget(null); npc.tickCount = Math.floorMod(-npc.getId(), 5);
        c.step(); c.tick();
        t.assertTrue(npc.getTarget() == null && c.snapshot().nodeStates.get(condition.id).equals("failure") && c.snapshot().tick == 1,
                "resuming the child does not rerun the sensing decorator even on a scheduled scan tick");
        c.stepTick(); c.tick();
        t.assertTrue(npc.getTarget() == cow && c.snapshot().tick == 2, "a new AI round can sense again normally");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void queuedHitBranchesAreNotRetickedWhileMainTreeIsSuspended(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action"; root(p, wait(p));
        var c = npc.getAiController(); c.preview(p, Map.of()); c.tick(); c.pause();
        var hit = new AiProgram(); hit.kind = "action"; var log = log(hit, "hit"); root(hit, node(hit, "sequence", log, wait(hit)));
        var first = new AiExecution(npc, hit, new HashMap<>(), Map.of(), ignored -> null);
        var second = new AiExecution(npc, hit, new HashMap<>(), Map.of(), ignored -> null);
        first.attachDebugger(c.debugger); second.attachDebugger(c.debugger);
        c.enqueueHit(first, () -> true); c.enqueueHit(second, () -> true);
        finishRound(t, c);
        t.assertTrue(first.time() == 1 && second.time() == 1 && outputs(c, log.id) == 2 && c.snapshot().tick == 2,
                "each queued hit branch advances once while many node steps traverse both hits and the main tree");
        c.stepTick(); c.tick();
        t.assertTrue(first.time() == 2 && second.time() == 2 && outputs(c, log.id) == 2, "the next round completes both hit waits without repeating their logs");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tickStepHonorsNewBreakpointsAndReplacementDiscardsSuspendedFrames(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action";
        var first = log(p, "first"); var second = log(p, "second"); root(p, node(p, "sequence", first, second));
        var c = npc.getAiController(); c.preview(p, Map.of());
        c.debugger.breakpoints.add(first.id); c.debugger.breakpoints.add(second.id); c.tick();
        c.stepTick(); c.tick();
        t.assertTrue(c.snapshot().tick == 1 && c.snapshot().breakpoint.equals(second.id) && outputs(c, first.id) == 1
                && outputs(c, second.id) == 0, "tick stepping continues this round but still stops before a new breakpoint");
        var replacement = new AiProgram(); replacement.kind = "action"; var fresh = log(replacement, "replacement"); root(replacement, fresh);
        c.preview(replacement, Map.of()); c.pause(); c.stepTick(); c.tick();
        t.assertTrue(c.snapshot().currentNode.isEmpty() && !c.snapshot().tickInProgress && outputs(c, fresh.id) == 1
                && outputs(c, second.id) == 0 && c.snapshot().tick == 1, "replacement clears suspended frames and starts only the new program");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void resumingDoesNotReevaluateTimeoutBeforeItsSuspendedChild(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action";
        var duration = new AiVariable(); duration.initial = AiValue.number(.1); p.variables.add(duration); p.npcVariables.add(duration);
        var change = node(p, "set_variable").input("scope", AiValue.choice("blackboard", List.of("local", "blackboard")))
                .input("variable", AiValue.text(duration.id)).input("value", AiValue.number(0));
        var last = node(p, "wait").input("seconds", AiValue.number(0));
        var sequence = node(p, "sequence", node(p, "stage", node(p, "success")), node(p, "stage", change, last));
        var timeout = node(p, "timeout", sequence);
        var binding = new AiBinding(); binding.scope = "blackboard"; binding.variableId = duration.id; timeout.inputs.put("seconds", binding);
        root(p, timeout);
        var c = npc.getAiController(); c.preview(p, Map.of()); c.pause(); c.stepTick(); c.tick();
        t.assertTrue(c.snapshot().activeNodes.contains(timeout.id), "first stage yields to the next AI tick");
        finishRound(t, c);
        t.assertTrue(c.snapshot().tick == 2 && c.snapshot().variables.get(duration.id).number == 0
                && c.snapshot().nodeStates.get(timeout.id).equals("success") && c.snapshot().reason.isBlank(),
                "changing a timeout input inside a child does not rerun the parent's already completed check when stepping resumes");
        t.succeed();
    }
}
