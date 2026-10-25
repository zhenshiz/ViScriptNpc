package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** 验证断点无副作用、共享调用暂停、单步、禁用分支与有界日志。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAiDebugTests {
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
    private static AiVariable variable(AiProgram p) {
        var v = new AiVariable(); v.name = "debug_value"; v.initial = AiValue.number(0); p.variables.add(v); p.npcVariables.add(v); return v;
    }
    private static AiNodeData set(AiProgram p, AiVariable v, int value) {
        return node(p, "set_variable").input("scope", AiValue.choice("blackboard", java.util.List.of("local", "blackboard")))
                .input("variable", AiValue.text(v.id)).input("value", AiValue.number(value));
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void breakpointPausesBeforeSideEffectsAndResumesOnce(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action"; var v = variable(p);
        var first = set(p, v, 1); var second = set(p, v, 2);
        root(p, node(p, "sequence", first, second, node(p, "wait").input("seconds", AiValue.number(.15))));
        var c = npc.getAiController(); c.preview(p, Map.of()); c.debugger.breakpoints.add(second.id); c.tick();
        t.assertTrue(c.snapshot().paused && c.snapshot().breakpoint.equals(second.id), "breakpoint suspends before entering the second assignment");
        t.assertTrue(c.snapshot().variables.get(v.id).number == 1, "the breakpoint node has no side effect before continuing");
        long tick = c.snapshot().tick;
        for (int i = 0; i < 10; i++) c.tick();
        t.assertTrue(c.snapshot().tick == tick && c.snapshot().variables.get(v.id).number == 1, "paused AI advances neither clock nor variables");
        c.stepTick(); c.tick();
        t.assertTrue(c.snapshot().paused && c.snapshot().tick == tick && c.snapshot().variables.get(v.id).number == 2,
                "tick-step resumes the suspended AI tick once without advancing its clock");
        t.assertTrue(c.snapshot().activeNodes.stream().anyMatch(id -> p.node(id) != null && p.node(id).type.equals("wait")), "step retains the wait frame");
        c.resume(); c.tick(); c.tick();
        t.assertTrue(c.snapshot().status.equals("success"), "resume completes the remaining wait instead of restarting it");
        c.tick();
        t.assertTrue(c.snapshot().paused && c.snapshot().breakpoint.equals(second.id), "a new invocation hits the same breakpoint again");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nestedBreakpointStopsParentAndParallelSibling(GameTestHelper t) {
        var npc = npc(t); var action = new AiProgram(); action.kind = "action";
        var log = node(action, "debug_log").input("message", AiValue.text("nested")).input("value", AiValue.number(9));
        root(action, log);
        var p = new AiProgram(); p.kind = "action"; var v = variable(p);
        var call = node(p, "call").input("file", AiValue.text("test/nested"));
        root(p, node(p, "parallel", call, set(p, v, 3)));
        var c = npc.getAiController(); c.preview(p, Map.of("test/nested", action)); c.debugger.breakpoints.add(log.id); c.tick();
        t.assertTrue(c.snapshot().paused && c.snapshot().variables.get(v.id).number == 0, "nested breakpoint prevents the later parallel sibling from executing");
        c.resume(); c.tick();
        t.assertTrue(c.snapshot().variables.get(v.id).number == 3 && c.snapshot().events.stream().anyMatch(e -> e.status.equals("value") && e.message.equals("nested = 9.0")),
                "nested invocation and parent resume with their shared debug session");
        var roundtrip = new AiDebugSnapshot(); roundtrip.deserializeNBT(npc.registryAccess(), c.snapshot().serializeNBT(npc.registryAccess()));
        t.assertTrue(roundtrip.events.size() == c.snapshot().events.size() && roundtrip.breakpoints.contains(log.id), "RPC snapshot roundtrip preserves events and breakpoints");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void disabledBranchesAndBoundedDebugNodes(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action"; var v = variable(p);
        var disabled = node(p, "sequence", set(p, v, 99)); disabled.disabled = true;
        var ray = node(p, "debug_ray").input("from", AiValue.position(new Vec3(1, 2, 3)))
                .input("direction", AiValue.position(new Vec3(0, 2, 4))).input("seconds", AiValue.number(.1));
        var line = node(p, "debug_line").input("from", AiValue.position(Vec3.ZERO)).input("to", AiValue.position(new Vec3(3, 4, 5)));
        root(p, node(p, "sequence", disabled, ray, line, node(p, "debug_log").input("value", AiValue.number(8))));
        var c = npc.getAiController(); c.preview(p, Map.of()); c.tick();
        t.assertTrue(c.snapshot().variables.get(v.id).number == 0, "disabled subtree has no side effects");
        t.assertTrue(c.snapshot().lines.size() == 2 && c.snapshot().lines.getFirst().to.equals(new Vec3(1, 4, 7)), "ray uses origin plus direction while line uses absolute endpoints");
        c.pause(); var snapshot = c.snapshot(); c.tick();
        t.assertTrue(c.snapshot().lines.getFirst().expires == snapshot.lines.getFirst().expires, "debug drawings remain while paused");
        c.resume(); for (int i = 0; i < 100; i++) c.tick();
        t.assertTrue(c.snapshot().events.size() <= 80 && c.snapshot().lines.size() <= 64, "high-frequency debug output remains bounded");
        c.debugger.logging = false; int count = c.snapshot().events.size(); c.tick();
        t.assertTrue(c.snapshot().events.size() == count, "logging toggle stops event collection");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void pausePreservesCooldownAndTemporaryDrawingTime(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action";
        var line = node(p, "debug_line").input("seconds", AiValue.number(.1));
        root(p, node(p, "cooldown", line).input("seconds", AiValue.number(1)));
        var c = npc.getAiController(); c.preview(p, Map.of()); c.tick(); c.pause();
        long tick = c.snapshot().tick;
        t.runAfterDelay(25, () -> {
            t.assertTrue(c.snapshot().tick == tick && c.snapshot().lines.size() == 1, "world time does not expire paused debug drawings");
            c.resume(); c.tick();
            t.assertTrue(c.snapshot().status.equals("failure"), "world time does not consume the paused cooldown");
            c.tick();
            t.assertTrue(c.snapshot().lines.isEmpty(), "temporary drawing expires after its remaining active AI ticks");
            t.succeed();
        });
    }
}
