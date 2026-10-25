package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.npc.ai.data.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 单个测试 NPC 的调试会话；调用行为和命中分支共享暂停与断点。 */
public final class AiDebugger {
    public boolean enabled;
    public boolean paused;
    public boolean logging = true;
    public String breakpoint = "";
    public long tick;
    public final Set<String> breakpoints = new LinkedHashSet<>();
    public final Map<String, String> states = new LinkedHashMap<>();
    public final Map<String, AiValue> values = new LinkedHashMap<>();
    public final List<AiDebugEvent> events = new ArrayList<>();
    public final List<AiDebugLine> lines = new ArrayList<>();
    public String currentNode = "", currentOperation = "", currentAction = "";
    public int currentDepth;
    public String lastNode = "", lastOperation = "", lastAction = "", lastResult = "";
    private AiExecution.Frame stoppedFrame;
    private int remainingNodes = -1;

    // 只展开 Java 调用栈，不销毁解释器帧；继续时从命中的节点开始。
    static final class Suspended extends RuntimeException {
        private Suspended() { super(null, null, false, false); }
    }
    private static final Suspended SUSPENDED = new Suspended();

    void before(AiExecution.Frame frame) {
        if (!enabled) return;
        if (paused) throw SUSPENDED;
        boolean hit = stoppedFrame != frame && frame.age == 0 && breakpoints.contains(frame.node.id);
        if (remainingNodes == 0 || hit) {
            currentNode = frame.node.id; currentOperation = frame.node.type;
            currentAction = frame.actionId(); currentDepth = frame.callDepth();
            breakpoint = hit ? frame.node.id : "";
            stoppedFrame = frame; paused = true;
            if (hit) event(frame.node, "breakpoint", "");
            throw SUSPENDED;
        }
        stoppedFrame = null;
        currentNode = currentOperation = currentAction = "";
        if (remainingNodes > 0) remainingNodes--;
        lastNode = frame.node.id; lastOperation = frame.node.type;
        lastAction = frame.actionId(); lastResult = "pending";
        if (frame.age == 0) event(frame.node, "entered", "");
    }

    public void resume() { paused = false; breakpoint = ""; remainingNodes = -1; }
    public void pause() { paused = true; }
    void step(boolean byNode) { resume(); remainingNodes = byNode ? 1 : -1; }
    void beginTick() { tick++; values.clear(); lines.removeIf(line -> line.expires <= tick); }

    void result(AiExecution.Frame frame, AiStatus result) {
        if (!enabled) return;
        var node = frame.node;
        if (states.size() >= 1024 && !states.containsKey(node.id)) states.clear();
        String state = result.name().toLowerCase(Locale.ROOT);
        if (lastNode.equals(node.id) && lastAction.equals(frame.actionId())) lastResult = state;
        String old = states.put(node.id, state);
        if (!state.equals(old) || result != AiStatus.RUNNING) event(node, state, "");
    }

    void event(AiNodeData node, String state, String message) {
        if (!enabled || !logging) return;
        var event = new AiDebugEvent(); event.tick = tick; event.nodeId = node.id;
        event.operation = node.type; event.status = state;
        event.message = message.substring(0, Math.min(512, message.length()));
        if (events.size() >= 80) events.removeFirst();
        events.add(event);
    }

    void line(AiNodeData node, Vec3 from, Vec3 to, int duration) {
        if (!enabled) return;
        lines.removeIf(line -> line.nodeId.equals(node.id));
        if (lines.size() >= 64) lines.removeFirst();
        var line = new AiDebugLine(); line.nodeId = node.id; line.from = from; line.to = to;
        line.expires = tick + duration; lines.add(line);
    }

    public void reset() {
        resume(); clearPosition(); states.clear(); values.clear(); events.clear(); lines.clear(); tick = 0;
    }

    void clearPosition() {
        stoppedFrame = null;
        breakpoint = "";
        currentNode = currentOperation = currentAction = lastNode = lastOperation = lastAction = lastResult = "";
        currentDepth = 0;
    }
}
