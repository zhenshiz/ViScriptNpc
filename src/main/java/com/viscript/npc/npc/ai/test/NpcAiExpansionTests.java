package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.*;
import com.viscript.npc.util.NpcActionFiles;
import com.viscript.npc.util.NpcAiPublication;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** 守卫记忆、驻守点保存、变量计算和随机分支的行为语义回归。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAiExpansionTests {
    private static CustomNpc npc(GameTestHelper t) {
        for (int x = 0; x < 14; x++) for (int z = 0; z < 10; z++) t.setBlock(x, 0, z, Blocks.STONE);
        var npc = t.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(3, 1, 3));
        npc.getAiController().manualControl(); return npc;
    }
    private static AiNodeData node(AiProgram p, String type, AiNodeData... children) {
        var n = new AiNodeData(); n.type = type;
        for (var child : children) n.children.add(child.id);
        p.nodes.add(n); return n;
    }
    private static void root(AiProgram p, AiNodeData child) { p.root = node(p, "root", child).id; }
    private static AiVariable variable(AiProgram p, AiValue initial) {
        var v = new AiVariable(); v.initial = initial; p.variables.add(v); return v;
    }
    private static void choice(AiNodeData n, String field, String value) {
        var v = AiOperations.get(n.type).fields().stream().filter(f -> f.id().equals(field)).findFirst().orElseThrow().initial().copy();
        v.text = value; n.input(field, v);
    }
    private static AiExecution execution(CustomNpc npc, AiProgram p) {
        AiValidation.requireValid(p, NpcActionFiles::load);
        return new AiExecution(npc, p, new HashMap<>(), Map.of(), NpcActionFiles::load);
    }
    private static AiProgram guard() {
        var p = new AiProgram();
        var guard = node(p, "guard_area", node(p, "success"));
        choice(guard, "perception", "range"); choice(guard, "filter", "living");
        guard.input("entity_type", AiValue.mobType("minecraft:cow")).input("range", AiValue.number(6))
                .input("leash_range", AiValue.number(12)).input("memory_seconds", AiValue.number(.1));
        root(p, guard); return p;
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void guardRemembersLastPositionThenReturnsWithoutReacquiring(GameTestHelper t) {
        var npc = npc(t); var home = npc.position();
        var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(5, 1, 3));
        var e = execution(npc, guard()); e.tick(); var seen = cow.position();
        t.assertTrue(npc.getTarget() == cow && npc.getAiController().guard.state().equals("combat"), "guard acquires a visible enemy");
        cow.setPos(home.add(8, 0, 0)); e.tick();
        t.assertTrue(npc.getAiController().guard.state().equals("searching")
                && npc.getAiController().guard.pursuitPosition(cow).equals(seen), "memory pursues the observed position instead of tracking through obstacles");
        npc.setPos(home.add(-5, 0, 0)); e.tick(); e.tick();
        t.assertTrue(npc.getTarget() == null && npc.getAiController().guard.returning(), "expired memory clears combat and requests a return");
        cow.setPos(npc.position().add(1, 0, 0)); e.tick();
        t.assertTrue(npc.getTarget() == null, "a nearby target cannot interrupt the trip home");
        npc.setPos(home); cow.setPos(home.add(20, 0, 0)); e.tick();
        t.assertTrue(!npc.getAiController().guard.returning() && npc.getAiController().guard.state().equals("patrolling"), "home arrival resumes patrol");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void guardLeashAndEntitySaveKeepTheOriginalHome(GameTestHelper t) {
        var npc = npc(t); var home = npc.position(); var p = guard(); var e = execution(npc, p); e.tick();
        npc.setPos(home.add(13, 0, 0)); e.tick();
        t.assertTrue(npc.getAiController().guard.returning() && npc.getTarget() == null, "pushing a guard outside its leash requests home");
        var tag = new CompoundTag(); npc.saveWithoutId(tag);
        var restored = NpcRegister.CUSTOM_NPC.get().create(t.getLevel()); restored.load(tag);
        t.assertTrue(restored.getAiController().guard.home().equals(home), "entity reload restores the old home instead of the displaced position");
        restored.getAiController().apply(p, Map.of(), true);
        t.assertTrue(restored.getAiController().guard.home().equals(home), "applying AI preserves the home");
        var copied = new AiDebugSnapshot(); copied.deserializeNBT(t.getLevel().registryAccess(), restored.getAiController().snapshot().serializeNBT(t.getLevel().registryAccess()));
        t.assertTrue(copied.hasHome && copied.home.equals(home), "home is visible in replicated debug snapshots");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void guardOnlyRetaliatesForAcceptedDamage(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(5, 1, 3));
        var p = guard(); var service = p.nodes.stream().filter(n -> n.type.equals("guard_area")).findFirst().orElseThrow();
        choice(service, "filter", "player"); var e = execution(npc, p); e.tick();
        npc.setInvulnerable(true); npc.hurt(npc.damageSources().mobAttack(cow), 2); e.tick();
        t.assertTrue(npc.getTarget() == null, "immune damage creates no threat");
        npc.setInvulnerable(false); npc.hurt(npc.damageSources().mobAttack(cow), 2); e.tick();
        t.assertTrue(npc.getTarget() == cow, "actual damage can override the proactive entity filter");
        npc.getAiController().cancel();
        t.assertTrue(!npc.getAiController().guard.active() && npc.getAiController().guard.hasHome(), "cancel clears combat memory and preserves home");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void arithmeticReadsUpdatedVariablesAndRejectsUndefinedResults(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action";
        var output = variable(p, AiValue.number(7));
        var add = node(p, "number_math").input("left", AiValue.number(2)).input("right", AiValue.number(3)).input("output", AiValue.text(output.id));
        var multiply = node(p, "number_math").input("right", AiValue.number(4)).input("output", AiValue.text(output.id));
        choice(multiply, "operator", "multiply"); var binding = new AiBinding(); binding.scope = "local"; binding.variableId = output.id;
        multiply.inputs.put("left", binding); root(p, node(p, "sequence", add, multiply));
        var e = execution(npc, p);
        t.assertTrue(e.tick() == AiStatus.SUCCESS && e.local.get(output.id).number == 20, "calculations compose through typed local variables");
        choice(add, "operator", "divide"); add.input("right", AiValue.number(0)); e = execution(npc, p);
        t.assertTrue(e.tick() == AiStatus.FAILURE && e.reason.equals("divide_by_zero") && e.local.get(output.id).number == 7, "division by zero preserves the previous output");
        choice(add, "operator", "multiply"); add.input("left", AiValue.number(Double.MAX_VALUE)).input("right", AiValue.number(2)); e = execution(npc, p);
        t.assertTrue(e.tick() == AiStatus.FAILURE && e.reason.equals("non_finite_result"), "overflow cannot poison variables"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void vectorOperationsValidateTheirOutputType(GameTestHelper t) {
        var npc = npc(t); var p = NpcAiFixtures.action("vector_math");
        var vector = variable(p, AiValue.position(Vec3.ZERO)); var number = variable(p, AiValue.number(0));
        var n = p.nodes.getLast(); n.input("vector_left", AiValue.position(new Vec3(3, 4, 0))).input("factor", AiValue.number(2)).input("output", AiValue.text(vector.id));
        choice(n, "vector_operator", "scale"); var e = execution(npc, p); e.tick();
        t.assertTrue(e.local.get(vector.id).position.equals(new Vec3(6, 8, 0)), "vector scaling outputs a position");
        choice(n, "vector_operator", "length");
        t.assertTrue(!AiValidation.validate(p, NpcActionFiles::load).isEmpty(), "changing to a scalar operation diagnoses an incompatible output");
        n.input("output", AiValue.text(number.id)); e = execution(npc, p); e.tick();
        t.assertTrue(e.local.get(number.id).number == 5, "vector length outputs a number"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void randomBoundsAndProbabilityEndpointsAreDeterministic(GameTestHelper t) {
        var npc = npc(t); var p = NpcAiFixtures.action("random_number"); var output = variable(p, AiValue.number(9));
        var n = p.nodes.getLast(); n.input("minimum", AiValue.number(-2)).input("maximum", AiValue.number(2))
                .input("integer", AiValue.bool(true)).input("output", AiValue.text(output.id));
        var e = execution(npc, p); var seen = new HashSet<Double>(); npc.getRandom().setSeed(3417);
        for (int i = 0; i < 100; i++) {
            t.assertTrue(e.tick() == AiStatus.SUCCESS, "integer sample succeeds"); var value = e.local.get(output.id).number;
            t.assertTrue(value >= -2 && value <= 2 && value == Math.rint(value), "sample respects inclusive integer bounds"); seen.add(value);
        }
        t.assertTrue(seen.contains(-2d) && seen.contains(2d), "both endpoints are reachable with a fixed seed");
        n.input("minimum", AiValue.number(.1)).input("maximum", AiValue.number(.9));
        e = execution(npc, p);
        t.assertTrue(e.tick() == AiStatus.FAILURE && e.reason.equals("random_range") && e.local.get(output.id).number == 9, "empty integer range preserves the output");
        p = NpcAiFixtures.action("success"); p.kind = "tree"; p.nodes.getLast().type = "chance";
        n = p.nodes.getLast(); n.input("probability", AiValue.number(0));
        t.assertTrue(execution(npc, p).tick() == AiStatus.FAILURE, "probability zero always fails");
        n.input("probability", AiValue.number(1));
        t.assertTrue(execution(npc, p).tick() == AiStatus.SUCCESS, "probability one always succeeds"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void weightedSelectionKeepsRunningChildAndIgnoresZeroWeights(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action";
        var output = variable(p, AiValue.number(0));
        var result = node(p, "random_number").input("minimum", AiValue.number(1)).input("maximum", AiValue.number(1)).input("output", AiValue.text(output.id));
        var selected = node(p, "weight", node(p, "sequence", result, node(p, "wait").input("seconds", AiValue.number(.15))));
        var excluded = node(p, "weight", node(p, "failure")).input("weight", AiValue.number(0));
        var parameter = new AiVariable(); parameter.initial = AiValue.number(1); p.parameters.add(parameter);
        var binding = new AiBinding(); binding.scope = "parameter"; binding.variableId = parameter.id; selected.inputs.put("weight", binding);
        root(p, node(p, "weighted_selector", selected, excluded)); var e = execution(npc, p);
        t.assertTrue(e.tick() == AiStatus.RUNNING && e.local.get(output.id).number == 1, "positive weight child is selected");
        e.parameters.put(parameter.id, AiValue.number(0));
        t.assertTrue(e.tick() == AiStatus.RUNNING && e.tick() == AiStatus.SUCCESS, "changing weights does not reselect an already running branch");
        t.assertTrue(e.tick() == AiStatus.FAILURE && e.reason.equals("zero_weights"), "a new activation rejects all-zero weights"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void fullGuardTemplateAttacksThenPhysicallyReturnsHome(GameTestHelper t) {
        var npc = npc(t); var home = npc.position();
        var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(7, 1, 3));
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
        var tree = AiExamples.tree("guard");
        var guard = tree.nodes.stream().filter(n -> n.type.equals("guard_area")).findFirst().orElseThrow();
        choice(guard, "perception", "range"); choice(guard, "filter", "living");
        guard.input("entity_type", AiValue.mobType("minecraft:cow")).input("range", AiValue.number(7)).input("leash_range", AiValue.number(8));
        npc.getAiController().preview(tree, NpcAiPublication.readDependencies(NpcAiPublication.bundle(tree, NpcActionFiles::load)));
        boolean[] attacked = {false}, returned = {false};
        t.onEachTick(() -> {
            if (!attacked[0] && cow.getHealth() < 100) { attacked[0] = true; cow.setPos(home.add(12, 0, 0)); }
            if (attacked[0] && npc.getAiController().guard.returning()) returned[0] = true;
        });
        t.succeedWhen(() -> {
            t.assertTrue(attacked[0] && returned[0] && npc.getTarget() == null && npc.position().distanceTo(home) < 1
                    && npc.getAiController().guard.state().equals("patrolling"), "guard attacks, disengages outside leash and returns to its original home");
            npc.getAiController().manualControl();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void calculatedResultsCanBeCommittedToNpcBlackboard(GameTestHelper t) {
        var npc = npc(t); var p = new AiProgram(); p.kind = "action";
        var output = variable(p, AiValue.number(0));
        var shared = new AiVariable(); shared.initial = AiValue.number(0); p.npcVariables.add(shared);
        var math = node(p, "number_math").input("left", AiValue.number(3)).input("right", AiValue.number(4)).input("output", AiValue.text(output.id));
        choice(math, "operator", "multiply");
        var write = node(p, "set_variable").input("variable", AiValue.text(shared.id)); choice(write, "scope", "blackboard");
        var binding = new AiBinding(); binding.scope = "local"; binding.variableId = output.id; write.inputs.put("value", binding);
        root(p, node(p, "sequence", math, write)); var e = execution(npc, p);
        t.assertTrue(e.tick() == AiStatus.SUCCESS && e.blackboard.get(shared.id).number == 12, "calculated local results can update shared NPC state"); t.succeed();
    }
}
