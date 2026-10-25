package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.*;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.*;
import com.viscript.npc.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

/** 使用真实服务端 NPC 验证实例生命周期、纯条件探测与序列化。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAiRuntimeTests {
    private static boolean registered;
    private static synchronized void registerProbe() {
        if (registered) return;
        registered = true;
        AiActions.register(new AiOperations.Operation("viscript_npc:test_probe", "action", 0,
                List.of(new AiOperations.Field("key", AiValue.text("count")))), (e, f) -> {
            String key = e.text(f.node, "key");
            e.blackboard.put(key, AiValue.number(e.blackboard.getOrDefault(key, AiValue.number(0)).number + 1));
            return AiStatus.SUCCESS;
        });
    }
    private static CustomNpc npc(GameTestHelper test) {
        registerProbe();
        for (int x = 0; x < 10; x++) for (int z = 0; z < 8; z++) test.setBlock(x, 0, z, Blocks.STONE);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 2));
        npc.getAiController().manualControl();
        return npc;
    }
    private static AiNodeData n(AiProgram p, String type, AiNodeData... children) {
        AiNodeData n = new AiNodeData(); n.type = type;
        for (AiNodeData child : children) n.children.add(child.id);
        p.nodes.add(n); return n;
    }
    private static AiProgram action() { var p = new AiProgram(); p.kind = "action"; return p; }
    private static AiNodeData probe(AiProgram p, String key) { return n(p, "viscript_npc:test_probe").input("key", AiValue.text(key)); }
    private static void root(AiProgram p, AiNodeData node) { p.root = n(p, "root", node).id; }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void parameterInputsResolveStrictly(GameTestHelper test) {
        var npc = npc(test); var p = action();
        var wait = n(p, "wait"); root(p, wait);
        var seconds = new AiVariable(); seconds.initial = AiValue.number(5); p.parameters.add(seconds);
        var binding = new AiBinding(); binding.scope = "parameter"; binding.variableId = seconds.id; binding.value = AiValue.number(99);
        wait.inputs.put("seconds", binding);
        var e = new AiExecution(npc, p, new HashMap<>(), Map.of(seconds.id, AiValue.number(0)), ignored -> null);
        test.assertTrue(e.tick() == AiStatus.SUCCESS && e.resolvedInputs.get(wait.id + ":seconds").number == 0,
                "zero input takes priority over default and cached binding values");
        var missing = new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null);
        missing.parameters.clear();
        test.assertTrue(missing.tick() == AiStatus.FAILURE && missing.reason.equals("binding") && !missing.isRunning(),
                "missing source fails and cancels instead of falling back");
        var wrong = new AiExecution(npc, p, new HashMap<>(), Map.of(seconds.id, AiValue.bool(false)), ignored -> null);
        test.assertTrue(wrong.tick() == AiStatus.FAILURE && wrong.reason.equals("type"), "wrong input type is observable");
        wait.inputs.remove("seconds");
        var restored = new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null);
        test.assertTrue(restored.tick() == AiStatus.RUNNING && restored.resolvedInputs.get(wait.id + ":seconds").number == 1,
                "unbound parameter uses its node default");
        restored.cancel(); test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void typedTargetFilterInputsUseRuntimeValues(GameTestHelper test) {
        var npc = npc(test); var p = action();
        var find = n(p, "find_target"); root(p, find);
        var filter = new AiVariable(); filter.initial = AiOperations.get("find_target").fields().stream()
                .filter(f -> f.id().equals("filter")).findFirst().orElseThrow().initial().copy();
        filter.initial.text = "player"; p.parameters.add(filter);
        var mob = new AiVariable(); mob.initial = AiValue.mobType("minecraft:zombie"); p.parameters.add(mob);
        var filterBinding = new AiBinding(); filterBinding.scope = "parameter"; filterBinding.variableId = filter.id;
        var mobBinding = new AiBinding(); mobBinding.scope = "parameter"; mobBinding.variableId = mob.id;
        find.inputs.put("filter", filterBinding); find.inputs.put("entity_type", mobBinding);
        var cow = test.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 2));
        var runtimeFilter = filter.initial.copy(); runtimeFilter.text = "living";
        npc.tickCount = Math.floorMod(-npc.getId(), 5);
        var e = new AiExecution(npc, p, new HashMap<>(), Map.of(filter.id, runtimeFilter, mob.id, AiValue.mobType("minecraft:cow")), ignored -> null);
        test.assertTrue(AiValidation.validate(p, ignored -> null).isEmpty(), "typed declarations compile and validate");
        test.assertTrue(e.tick() == AiStatus.SUCCESS && npc.getTarget() == cow,
                "runtime enum and Mob inputs override declaration defaults");
        var invalid = runtimeFilter.copy(); invalid.text = "unknown_mode";
        var broken = new AiExecution(npc, p, new HashMap<>(), Map.of(filter.id, invalid), ignored -> null);
        test.assertTrue(broken.tick() == AiStatus.FAILURE && broken.reason.equals("type"), "invalid enum fails without broadening targeting");
        var serialized = p.copy(npc.registryAccess());
        test.assertTrue(serialized.parameters.getFirst().initial.type.equals(filter.initial.type)
                && AiValidation.validate(serialized, ignored -> null).isEmpty(), "domain type survives runtime serialization");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void findTargetTypeFilterOnlyAppliesToMobs(GameTestHelper test) {
        CustomNpc npc = npc(test);
        var player = test.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(npc.position().add(1, 0, 0));
        test.getLevel().addFreshEntity(player);
        try {
            test.spawn(EntityType.ARMOR_STAND, new BlockPos(3, 1, 2));
            var zombie = test.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 1, 2));
            var cow = test.spawnWithNoFreeWill(EntityType.COW, new BlockPos(7, 1, 2));
            var p = action();
            var find = n(p, "find_target").input("filter", AiValue.choice("living", java.util.List.of("hostile", "player", "living"))).input("range", AiValue.number(6));
            root(p, find);
            npc.tickCount = Math.floorMod(-npc.getId(), 5);
            test.assertTrue(new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null).tick() == AiStatus.SUCCESS
                    && npc.getTarget() == zombie, "mob mode excludes closer players and armor stands");
            find.input("entity_type", AiValue.mobType("minecraft:cow"));
            test.assertTrue(new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null).tick() == AiStatus.SUCCESS
                    && npc.getTarget() == cow, "mob mode applies the selected entity type");
            find.input("entity_type", AiValue.mobType("minecraft:armor_stand"));
            test.assertTrue(new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null).tick() == AiStatus.FAILURE
                    && npc.getTarget() == null, "non-Mob type from an old file cannot select an armor stand");
            find.input("filter", AiValue.choice("player", java.util.List.of("hostile", "player", "living"))).input("entity_type", AiValue.mobType("minecraft:cow"));
            test.assertTrue(new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null).tick() == AiStatus.SUCCESS
                    && npc.getTarget() == player, "hidden mob type does not prevent selecting a player");
            find.input("entity_type", AiValue.mobType("missing_mod:old_entity"));
            test.assertTrue(AiValidation.validate(p, ignored -> null).isEmpty()
                    && new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null).tick() == AiStatus.SUCCESS
                    && npc.getTarget() == player, "inactive missing type neither blocks validation nor affects targeting");
            find.input("filter", AiValue.choice("hostile", java.util.List.of("hostile", "player", "living")));
            test.assertTrue(AiValidation.validate(p, ignored -> null).isEmpty(), "faction mode also ignores the inactive type");
            find.input("filter", AiValue.choice("living", java.util.List.of("hostile", "player", "living")));
            test.assertTrue(AiValidation.validate(p, ignored -> null).stream().anyMatch(issue -> issue.nodeId().equals(find.id)
                    && issue.key().equals("resource")), "active missing type still reports its node");
            test.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void sequenceOnlyEntersEachStepOnce(GameTestHelper test) {
        CustomNpc npc = npc(test);
        AiProgram p = action(); root(p, n(p, "sequence", probe(p, "first"), n(p, "wait").input("seconds", AiValue.number(.5)), probe(p, "last")));
        Map<String, AiValue> blackboard = new HashMap<>();
        AiExecution e = new AiExecution(npc, p, blackboard, Map.of(), ignored -> null);
        boolean[] done = {false};
        test.onEachTick(() -> { if (!done[0]) done[0] = e.tick() != AiStatus.RUNNING; });
        test.runAfterDelay(30, () -> {
            test.assertTrue(done[0], "sequence completed");
            test.assertTrue(blackboard.get("first").number == 1 && blackboard.get("last").number == 1, "steps triggered once");
            test.succeed();
        });
    }


    @GameTest(template = "empty", timeoutTicks = 80)
    public static void disableCancelsAndEnableRestarts(GameTestHelper test) {
        CustomNpc npc = npc(test);
        AiProgram a = action(); root(a, n(a, "sequence", n(a, "wait").input("seconds", AiValue.number(.5)), probe(a, "hit")));
        AiProgram tree = new AiProgram(); root(tree, n(tree, "call").input("file", AiValue.text("test")));
        npc.getAiController().preview(tree, Map.of("test", a));
        test.runAfterDelay(3, () -> npc.getNpcAI().setEnabled(false));
        test.runAfterDelay(20, () -> {
            test.assertTrue(!npc.getAiController().snapshot().variables.containsKey("hit"), "no delayed effect after AI disable");
            npc.getNpcAI().setEnabled(true);
        });
        test.runAfterDelay(35, () -> {
            test.assertTrue(npc.getAiController().snapshot().variables.get("hit").number == 1, "AI restarts after enable");
            npc.getAiController().manualControl(); test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void examplesRoundTripAndValidate(GameTestHelper test) {
        for (String name : AiExamples.NAMES) {
            var p = new AiProgram();
            p.deserializeNBT(test.getLevel().registryAccess(), AiExamples.action(name).serializeNBT(test.getLevel().registryAccess()));
            test.assertTrue(AiValidation.validate(p, ignored -> null).isEmpty(), "example validates: " + name);
            test.assertTrue(p.nodes.size() == AiExamples.action(name).nodes.size(), "all action nodes round trip");
        }
        for (String name : AiExamples.TREE_NAMES) {
            var p = AiExamples.tree(name).copy(test.getLevel().registryAccess());
            var dependencies = NpcAiPublication.readDependencies(NpcAiPublication.bundle(p, NpcActionFiles::load));
            test.assertTrue(AiValidation.validate(p, dependencies::get).isEmpty(), "bundle validates: " + name);
        }
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void runningActionPinsItsDefinition(GameTestHelper test) {
        CustomNpc npc = npc(test);
        AiProgram a = action();
        AiNodeData hit = probe(a, "old");
        root(a, n(a, "sequence", n(a, "wait").input("seconds", AiValue.number(.5)), hit));
        var bb = new HashMap<String, AiValue>();
        var e = new AiExecution(npc, a, bb, Map.of(), ignored -> null);
        boolean[] done = {false};
        test.onEachTick(() -> { if (!done[0]) done[0] = e.tick() != AiStatus.RUNNING; });
        test.runAfterDelay(3, () -> hit.input("key", AiValue.text("new")));
        test.runAfterDelay(25, () -> {
            test.assertTrue(bb.containsKey("old") && !bb.containsKey("new"), "active instance keeps definition snapshot");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void finishAndTypedBindings(GameTestHelper test) {
        CustomNpc npc = npc(test);
        var p = action();
        root(p, n(p, "sequence", n(p, "invert", n(p, "finish_success")), probe(p, "should_not_run")));
        var bb = new HashMap<String, AiValue>();
        test.assertTrue(new AiExecution(npc, p, bb, Map.of(), ignored -> null).tick() == AiStatus.SUCCESS && bb.isEmpty(),
                "explicit finish exits the whole behavior");
        var tree = AiExamples.tree("melee");
        var call = tree.nodes.stream().filter(node -> node.type.equals("call") && node.literal("file", AiValue.text("")).text.equals("examples/melee")).findFirst().orElseThrow();
        var parameter = AiExamples.action("melee").parameters.getFirst();
        call.input("param:" + parameter.id, AiValue.text("wrong type"));
        test.assertTrue(AiValidation.validate(tree, NpcActionFiles::load).stream().anyMatch(issue -> issue.nodeId().equals(call.id) && issue.key().equals("type")),
                "parameter mismatch identifies the call node");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void jumpSlashCanBeDodged(GameTestHelper test) {
        CustomNpc npc = npc(test);
        var target = test.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(5, 1, 2)); target.setNoAi(true);
        npc.setTarget(target);
        var e = new AiExecution(npc, AiExamples.action("jump_slash"), new HashMap<>(), Map.of(), ignored -> null);
        boolean[] moved = {false}, done = {false}; int[] ticks = {0};
        test.onEachTick(() -> {
            if (++ticks[0] < 5 || done[0]) return;
            done[0] = e.tick() != AiStatus.RUNNING;
            if (!moved[0] && !npc.onGround()) {
                target.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(8, 1, 7))));
                moved[0] = true;
            }
        });
        test.runAfterDelay(110, () -> {
            test.assertTrue(done[0] && moved[0] && target.getHealth() == target.getMaxHealth(), "moving after takeoff dodges the locked impact point");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void invalidPublicationKeepsInstalledFiles(GameTestHelper test) {
        String id = "__ai_gametest__/action";
        var path = com.viscript.npc.util.NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, id, true);
        try {
            var request = new net.minecraft.nbt.CompoundTag(); request.putString("kind", "action"); request.putString("name", id);
            var valid = NpcAiFixtures.action("wait"); request.put("runtime", valid.serializeNBT(test.getLevel().registryAccess()));
            NpcAiPublication.install(request);
            byte[] previous = java.nio.file.Files.readAllBytes(path);
            valid.nodes.getFirst().type = "missing:action";
            request.put("runtime", valid.serializeNBT(test.getLevel().registryAccess()));
            boolean rejected = false;
            try { NpcAiPublication.install(request); } catch (IllegalArgumentException expected) { rejected = true; }
            test.assertTrue(rejected && Arrays.equals(previous, java.nio.file.Files.readAllBytes(path)), "failed publication preserves installed bytes");
            test.assertTrue(NpcActionFiles.load(id) != null, "valid installed definition remains available");
            test.succeed();
        } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        finally {
            try { java.nio.file.Files.deleteIfExists(path); } catch (java.io.IOException ignored) {}
            NpcActionFiles.invalidate();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void jumpSlashPhysicallyLandsAndHitsOnce(GameTestHelper test) {
        CustomNpc npc = npc(test);
        var target = test.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(5, 1, 2));
        target.setNoAi(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        npc.setTarget(target);
        AiProgram p = AiExamples.action("jump_slash");
        var e = new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null);
        boolean[] done = {false}, airborne = {false};
        AiStatus[] status = {AiStatus.RUNNING};
        int[] ticks = {0}; float[] healthAfter = {100};
        test.onEachTick(() -> {
            if (++ticks[0] < 5 || done[0]) return;
            status[0] = e.tick(); airborne[0] |= !npc.onGround();
            if (status[0] != AiStatus.RUNNING) { done[0] = true; healthAfter[0] = target.getHealth(); }
        });
        test.runAfterDelay(120, () -> {
            test.assertTrue(done[0] && status[0] == AiStatus.SUCCESS, "jump action succeeds: " + e.reason);
            test.assertTrue(airborne[0] && npc.onGround(), "actual airborne travel and landing");
            test.assertTrue(target.getHealth() == 94 && healthAfter[0] == 94, "one explicit damage step deals six damage once");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void cancellingAirborneSlashCannotHitLater(GameTestHelper test) {
        CustomNpc npc = npc(test);
        var target = test.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(5, 1, 2)); target.setNoAi(true);
        npc.setTarget(target);
        var e = new AiExecution(npc, AiExamples.action("jump_slash"), new HashMap<>(), Map.of(), ignored -> null);
        boolean[] cancelled = {false}; int[] ticks = {0};
        test.onEachTick(() -> {
            if (++ticks[0] < 5 || cancelled[0]) return;
            e.tick();
            if (!npc.onGround()) { e.cancel(); cancelled[0] = true; }
        });
        test.runAfterDelay(100, () -> {
            test.assertTrue(cancelled[0] && npc.onGround(), "force cancel preserves gravity until landing");
            test.assertTrue(target.getHealth() == target.getMaxHealth(), "cancelled slash never creates delayed damage");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void areaDamageRespectsFaction(GameTestHelper test) {
        CustomNpc attacker = npc(test);
        CustomNpc friend = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(3, 1, 2));
        friend.getAiController().manualControl();
        attacker.getNpcViScriptTeamIntegration().setFactionId("__ai_test_friendly__");
        friend.getNpcViScriptTeamIntegration().setFactionId("__ai_test_friendly__");
        com.viscript.npc.compat.team.NpcFactionBridge.applyConfiguredFaction(attacker);
        com.viscript.npc.compat.team.NpcFactionBridge.applyConfiguredFaction(friend);
        float health = friend.getHealth();
        var p = action(); root(p, n(p, "area_damage").input("range", AiValue.number(4)).input("damage", AiValue.number(7)));
        var e = new AiExecution(attacker, p, new HashMap<>(), Map.of(), ignored -> null);
        e.tick();
        test.assertTrue(friend.getHealth() == health, "friendly target excluded from damage");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void cooldownAndSharedStepBudget(GameTestHelper test) {
        CustomNpc npc = npc(test);
        var action = action(); root(action, probe(action, "count"));
        var tree = new AiProgram(); root(tree, n(tree, "cooldown", n(tree, "call").input("file", AiValue.text("probe")))
                .input("seconds", AiValue.number(1)));
        var bb = new HashMap<String, AiValue>();
        var e = new AiExecution(npc, tree, bb, Map.of(), ignored -> action);
        test.onEachTick(e::tick);
        test.runAfterDelay(10, () -> test.assertTrue(bb.get("count").number == 1, "cooldown prevents re-entry"));
        test.runAfterDelay(25, () -> {
            test.assertTrue(bb.get("count").number == 2, "cooldown reopens after twenty ticks");
            var many = action();
            var sequence = n(many, "sequence");
            for (int i = 0; i < 64; i++) sequence.children.add(probe(many, "steps").id);
            root(many, sequence);
            var host = new AiProgram(); var calls = n(host, "sequence");
            for (int i = 0; i < 64; i++) calls.children.add(n(host, "call").input("file", AiValue.text("many")).id);
            root(host, calls);
            var counters = new HashMap<String, AiValue>();
            var bounded = new AiExecution(npc, host, counters, Map.of(), ignored -> many);
            test.assertTrue(bounded.tick() == AiStatus.RUNNING && counters.get("steps").number <= 128,
                    "tree and called actions share a single tick budget");
            bounded.cancel(); e.cancel(); test.succeed();
        });
    }
    private static AiVariable variable(String name, AiValue value) {
        var v = new AiVariable(); v.name = name; v.initial = value; return v;
    }

    private static void bind(AiNodeData node, String field, String scope, AiVariable variable) {
        var binding = new AiBinding(); binding.scope = scope; binding.variableId = variable.id;
        binding.value = variable.initial.copy(); node.inputs.put(field, binding);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nestedCallsBindInputsAndIsolateLocals(GameTestHelper test) {
        CustomNpc npc = npc(test);
        AiActions.register(new AiOperations.Operation("viscript_npc:test_capture", "action", 0, List.of(
                new AiOperations.Field("key", AiValue.text("")), new AiOperations.Field("value", AiValue.number(0)),
                new AiOperations.Field("local", AiValue.number(0)))), (e, f) -> {
            e.blackboard.put(e.text(f.node, "key"), e.resolve(f.node, "value").copy());
            e.blackboard.put(e.text(f.node, "key") + "_local", e.resolve(f.node, "local").copy());
            return AiStatus.SUCCESS;
        });
        var leaf = action();
        var amount = variable("damage", AiValue.number(3)); var key = variable("result", AiValue.text("default"));
        var scratch = variable("scratch", AiValue.number(7));
        leaf.parameters.addAll(List.of(amount, key)); leaf.variables.add(scratch);
        var capture = n(leaf, "viscript_npc:test_capture");
        bind(capture, "value", "parameter", amount); bind(capture, "key", "parameter", key); bind(capture, "local", "local", scratch);
        root(leaf, n(leaf, "sequence", capture, n(leaf, "set_variable").input("variable", AiValue.text(scratch.id)).input("value", AiValue.number(99))));
        var parent = action(); var input = variable("power", AiValue.number(5)); var local = variable("cost", AiValue.number(4));
        var shared = variable("shared", AiValue.number(8));
        parent.parameters.add(input); parent.variables.add(local); parent.npcVariables.add(shared);
        var first = n(parent, "call").input("file", AiValue.text("leaf")).input("param:" + key.id, AiValue.text("input"));
        bind(first, "param:" + amount.id, "parameter", input);
        var second = n(parent, "call").input("file", AiValue.text("leaf")).input("param:" + key.id, AiValue.text("local"));
        bind(second, "param:" + amount.id, "local", local);
        var third = n(parent, "call").input("file", AiValue.text("leaf")).input("param:" + key.id, AiValue.text("shared"));
        bind(third, "param:" + amount.id, "blackboard", shared);
        root(parent, n(parent, "sequence", first, second, third, n(parent, "call").input("file", AiValue.text("leaf"))));
        var tree = new AiProgram(); tree.variables.add(shared);
        root(tree, n(tree, "call").input("file", AiValue.text("parent")).input("param:" + input.id, AiValue.number(11)));
        var definitions = Map.of("leaf", leaf, "parent", parent);
        test.assertTrue(AiValidation.validate(tree, definitions::get).isEmpty(), "nested typed input contracts validate");
        var bb = new HashMap<String, AiValue>(); bb.put(shared.id, shared.initial.copy());
        var e = new AiExecution(npc, tree, bb, Map.of(), definitions::get);
        test.assertTrue(e.tick() == AiStatus.SUCCESS, "nested action completes");
        test.assertTrue(bb.get("input").number == 11 && bb.get("local").number == 4 && bb.get("shared").number == 8
                && bb.get("default").number == 3, "parent inputs, locals, blackboard and defaults bind independently");
        test.assertTrue(List.of("input", "local", "shared", "default").stream().allMatch(k -> bb.get(k + "_local").number == 7),
                "every invocation starts with fresh locals");
        var bundled = NpcAiPublication.readDependencies(NpcAiPublication.bundle(tree, definitions::get));
        test.assertTrue(bundled.keySet().equals(definitions.keySet()) && AiValidation.validate(tree, bundled::get).isEmpty(),
                "transitive dependencies survive serialization");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nestedCyclesAndFailuresAreDiagnosed(GameTestHelper test) {
        CustomNpc npc = npc(test);
        var a = action(); root(a, n(a, "call").input("file", AiValue.text("b")));
        var b = action(); root(b, n(b, "call").input("file", AiValue.text("a")));
        var definitions = new HashMap<String, AiProgram>(); definitions.put("a", a); definitions.put("b", b);
        var issues = AiValidation.validate(a, definitions::get);
        test.assertTrue(issues.stream().anyMatch(i -> i.key().equals("cycle") && i.nodeId().equals(a.node(a.root).children.getFirst())),
                "mutual recursion identifies the current call node");
        var invalid = new AiExecution(npc, a, new HashMap<>(), Map.of(), definitions::get);
        test.assertTrue(invalid.tick() == AiStatus.FAILURE && invalid.reason.equals("invalid_action"), "runtime rejects recursion without crashing");
        a.nodes.stream().filter(n -> n.type.equals("call")).findFirst().orElseThrow().input("file", AiValue.text("a"));
        test.assertTrue(AiValidation.validate(a, definitions::get).stream().anyMatch(i -> i.key().equals("cycle")), "self call rejected");
        var fail = action(); root(fail, n(fail, "failure"));
        var host = action(); root(host, n(host, "sequence", n(host, "call").input("file", AiValue.text("fail")), probe(host, "late")));
        var bb = new HashMap<String, AiValue>();
        test.assertTrue(new AiExecution(npc, host, bb, Map.of(), id -> fail).tick() == AiStatus.FAILURE && !bb.containsKey("late"),
                "subaction failure propagates to its parent");
        definitions.clear();
        test.assertTrue(AiValidation.validate(host, definitions::get).stream().anyMatch(i -> i.key().equals("missing_action")), "missing dependency is an issue");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void nestedStagesAndCancellationPropagate(GameTestHelper test) {
        CustomNpc npc = npc(test);
        AiActions.register(new AiOperations.Operation("viscript_npc:test_held", "action", 0, List.of()), new AiActions.Executor() {
            @Override public AiStatus tick(AiExecution e, AiExecution.Frame f) { e.blackboard.put("started", AiValue.bool(true)); return AiStatus.RUNNING; }
            @Override public void cancel(AiExecution e, AiExecution.Frame f) {
                e.blackboard.put("cancelled", AiValue.number(e.blackboard.getOrDefault("cancelled", AiValue.number(0)).number + 1));
            }
        });
        var leaf = action(); var stage = n(leaf, "stage", n(leaf, "viscript_npc:test_held"), probe(leaf, "late"));
        stage.input("interruptible", AiValue.bool(false)); root(leaf, stage);
        var parent = action(); var outer = n(parent, "stage", n(parent, "call").input("file", AiValue.text("leaf")));
        root(parent, outer);
        var bb = new HashMap<String, AiValue>(); var e = new AiExecution(npc, parent, bb, Map.of(), id -> leaf);
        test.assertTrue(e.tick() == AiStatus.RUNNING && !e.interruptible(), "child lock prevents interrupting parent");
        e.cancel();
        test.assertTrue(bb.get("cancelled").number == 1 && !bb.containsKey("late") && !e.isRunning() && e.activeNodes().isEmpty(),
                "forced parent cancellation reaches child exactly once and removes delayed steps");
        stage.input("interruptible", AiValue.bool(true)); outer.input("interruptible", AiValue.bool(false));
        var locked = new AiExecution(npc, parent, new HashMap<>(), Map.of(), id -> leaf);
        test.assertTrue(locked.tick() == AiStatus.RUNNING && !locked.interruptible(), "parent lock also covers interruptible child");
        locked.cancel(); test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void nestedDefinitionsPinUntilNextTreeActivation(GameTestHelper test) {
        CustomNpc npc = npc(test);
        var leaf = action(); var hit = probe(leaf, "old"); root(leaf, hit);
        var parent = action(); root(parent, n(parent, "sequence", n(parent, "wait").input("seconds", AiValue.number(.25)),
                n(parent, "call").input("file", AiValue.text("leaf"))));
        var tree = new AiProgram(); root(tree, n(tree, "call").input("file", AiValue.text("parent")));
        var definitions = Map.of("leaf", leaf, "parent", parent); var bb = new HashMap<String, AiValue>();
        var e = new AiExecution(npc, tree, bb, Map.of(), definitions::get);
        test.assertTrue(e.tick() == AiStatus.RUNNING, "parent starts before child");
        hit.input("key", AiValue.text("new"));
        for (int i = 0; i < 10 && !bb.containsKey("old"); i++) e.tick();
        test.assertTrue(bb.containsKey("old") && !bb.containsKey("new"), "entire invocation keeps dependency snapshots");
        for (int i = 0; i < 10 && !bb.containsKey("new"); i++) e.tick();
        test.assertTrue(bb.containsKey("new"), "subsequent tree activation uses the new shared definition");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nestedPublicationInstallsClosureAndRejectsOwnIdCycle(GameTestHelper test) {
        CustomNpc npc = npc(test);
        String aId = "__ai_gametest__/composed", bId = "__ai_gametest__/middle", cId = "__ai_gametest__/leaf";
        var leaf = action(); root(leaf, probe(leaf, "published"));
        var middle = action(); root(middle, n(middle, "call").input("file", AiValue.text(cId)));
        var parent = action(); root(parent, n(parent, "call").input("file", AiValue.text(bId)));
        var definitions = Map.of(aId, parent, bId, middle, cId, leaf);
        var paths = definitions.keySet().stream().map(id -> com.viscript.npc.util.NpcEditorFiles
                .resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, id, true)).toList();
        Map<java.nio.file.Path, byte[]> originals = new HashMap<>();
        try {
            for (var path : paths) originals.put(path, java.nio.file.Files.exists(path) ? java.nio.file.Files.readAllBytes(path) : null);
            var runtime = parent.serializeNBT(test.getLevel().registryAccess());
            runtime.put(NpcAiPublication.DEPENDENCIES, NpcAiPublication.bundle(parent, definitions::get));
            var request = new net.minecraft.nbt.CompoundTag(); request.putString("kind", "action"); request.putString("name", aId); request.put("runtime", runtime);
            NpcAiPublication.install(request);
            test.assertTrue(paths.stream().allMatch(java.nio.file.Files::isRegularFile), "publishing an action installs all nested dependencies");
            var bb = new HashMap<String, AiValue>();
            test.assertTrue(new AiExecution(npc, NpcActionFiles.load(aId), bb, Map.of(), NpcActionFiles::load).tick() == AiStatus.SUCCESS
                    && bb.get("published").number == 1, "installed closure executes on the dedicated server");
            var cyclic = action(); root(cyclic, n(cyclic, "call").input("file", AiValue.text(aId)));
            var invalid = cyclic.serializeNBT(test.getLevel().registryAccess());
            invalid.put(NpcAiPublication.DEPENDENCIES, NpcAiPublication.bundle(cyclic, definitions::get));
            request.put("runtime", invalid);
            var installed = new HashMap<java.nio.file.Path, byte[]>();
            for (var path : paths) installed.put(path, java.nio.file.Files.readAllBytes(path));
            boolean rejected = false;
            try { NpcAiPublication.install(request); } catch (IllegalArgumentException expected) { rejected = true; }
            test.assertTrue(rejected, "publish checks the new definition under its destination ID instead of the old bundled definition");
            for (var path : paths) test.assertTrue(Arrays.equals(installed.get(path), java.nio.file.Files.readAllBytes(path)),
                    "rejected publication preserves every installed file");
            test.succeed();
        } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        finally {
            originals.forEach((path, bytes) -> {
                try { if (bytes == null) java.nio.file.Files.deleteIfExists(path); else java.nio.file.Files.write(path, bytes); }
                catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            });
            NpcActionFiles.invalidate();
        }
    }

}
