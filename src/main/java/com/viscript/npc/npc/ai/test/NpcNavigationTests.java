package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.navigation.NavigationStatus;
import com.viscript.npc.npc.ai.navigation.baritone.AStarPathFinder;
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.ai.runtime.AiExecution;
import com.viscript.npc.npc.ai.runtime.AiStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 在独立服务器验证实际 NPC 物理导航，不需要客户端或原版寻路。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcNavigationTests {
    private static void floor(GameTestHelper test) {
        for (int x = 0; x < 14; x++) for (int z = 0; z < 10; z++) {
            test.setBlock(x, 0, z, Blocks.STONE);
            for (int y = 1; y < 6; y++) test.setBlock(x, y, z, Blocks.AIR);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void navigationSpeedIsAnAbsoluteValue(GameTestHelper test) {
        floor(test);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 4));
        var oldAttributes = new net.minecraft.nbt.CompoundTag();
        oldAttributes.putDouble("movementSpeed", .9);
        npc.getNpcAttributes().deserializeNBT(npc.registryAccess(), oldAttributes);
        var speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        speed.setBaseValue(.13);
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(10, 1, 4)));
        test.assertTrue(npc.navigateTo(goal, .5) && speed.getBaseValue() == .5,
                "requested speed replaces the previous attribute instead of multiplying it");
        npc.updateNpcState();
        test.assertTrue(speed.getBaseValue() == .5, "old NPC settings cannot overwrite the node speed");
        test.assertTrue(npc.navigateTo(goal, .25) && speed.getBaseValue() == .25,
                "a new movement uses its own speed without compounding");
        speed.setBaseValue(0);
        test.assertTrue(npc.navigateTo(goal, .4) && speed.getBaseValue() == .4,
                "an old zero attribute cannot immobilize a new movement");
        test.assertTrue(!npc.navigateTo(goal, Double.NaN) && speed.getBaseValue() == .4,
                "rejected requests preserve the current speed");
        var snapshot = new com.viscript.npc.npc.ai.navigation.NavigationSnapshot(npc, goal);
        test.assertTrue(snapshot.speed == .4, "jump planning sees the requested movement speed");
        npc.stopNavigation();
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void movementNodesUseTheirOwnSpeed(GameTestHelper test) {
        floor(test);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 4));
        npc.getAiController().manualControl();
        var target = test.spawn(EntityType.ARMOR_STAND, new BlockPos(10, 1, 4));
        npc.setTarget(target);
        for (String operation : java.util.List.of("navigate", "approach", "patrol")) {
            for (boolean useDefault : new boolean[]{true, false}) {
                npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.13);
                var program = NpcAiFixtures.action(operation);
                var node = program.nodes.stream().filter(n -> n.type.equals(operation)).findFirst().orElseThrow();
                node.input("position", AiValue.position(target.position()));
                if (!useDefault) node.input("speed", AiValue.number(.3));
                if (operation.equals("patrol")) node.input("patrol_radius", AiValue.number(0));
                var execution = new AiExecution(npc, program, new java.util.HashMap<>(), java.util.Map.of(), ignored -> null);
                test.assertTrue(execution.tick() == AiStatus.RUNNING, operation + " starts navigation");
                test.assertTrue(npc.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() == (useDefault ? .5 : .3),
                        operation + " applies its default or explicit absolute speed");
                execution.cancel();
            }
        }
        for (String name : java.util.List.of("patrol", "pursue_melee", "pursue_ranged")) {
            var movement = AiExamples.action(name);
            test.assertTrue(movement.parameters.stream().filter(p -> p.name.equals("speed"))
                    .allMatch(p -> p.initial.number == .5), "built-in " + name + " uses speed 0.5");
        }
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void walksSlabs(GameTestHelper test) {
        floor(test);
        for (int z = 0; z < 10; z++) {
            test.setBlock(4, 1, z, Blocks.STONE_SLAB);
            for (int x = 5; x < 14; x++) test.setBlock(x, 1, z, Blocks.STONE);
        }
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 4));
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(10, 2, 4)));
        test.runAfterDelay(5, () -> npc.navigateTo(goal, .5));
        test.succeedWhen(() -> test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED,
                "slab arrival: " + npc.getNpcNavigation().reason()));
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void cancelDoesNotRestartFromOldSearch(GameTestHelper test) {
        floor(test);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 4));
        Vec3[] stopped = {Vec3.ZERO};
        test.runAfterDelay(5, () -> npc.navigateTo(Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(11, 1, 4))), .5));
        test.runAfterDelay(8, () -> { npc.stopNavigation(); stopped[0] = npc.position(); });
        test.runAfterDelay(70, () -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.CANCELLED, "old search never reactivates");
            test.assertTrue(npc.position().subtract(stopped[0]).horizontalDistance() < .2, "cancelled NPC stays still");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 350)
    public static void unreachableIsFailure(GameTestHelper test) {
        floor(test);
        for (int x = 7; x <= 11; x++) for (int z = 2; z <= 6; z++) if (x == 7 || x == 11 || z == 2 || z == 6)
            for (int y = 1; y < 5; y++) test.setBlock(x, y, z, Blocks.STONE);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 4));
        test.runAfterDelay(5, () -> npc.navigateTo(Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(9, 1, 4))), .5));
        test.succeedWhen(() -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.FAILED, "unreachable returns failure");
            test.assertTrue(!npc.getNpcNavigation().reason().isBlank(), "failure explains reason");
            test.assertTrue(npc.getNavigation().isDone(), "no vanilla fallback");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void wideNpcUsesActualCollisionAndSpeed(GameTestHelper test) {
        floor(test);
        for (int z = 0; z < 7; z++) for (int y = 1; y < 5; y++) test.setBlock(6, y, z, Blocks.STONE);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 3));
        npc.getNpcDynamicModel().setAabb(new net.minecraft.world.phys.AABB(-.7, 0, -.7, .7, 2.5, .7));
        npc.updateBoundingBox();
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(10, 1, 3)));
        test.runAfterDelay(5, () -> npc.navigateTo(goal, .15));
        test.succeedWhen(() -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED, "wide NPC arrival " + npc.getNpcNavigation().reason());
            test.assertTrue(npc.getBoundingBox().getXsize() > 1.3 && npc.position().distanceTo(goal) < 1, "wide physical body arrived");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void snapshotNeverLoadsChunks(GameTestHelper test) {
        CustomNpc npc = NpcRegister.CUSTOM_NPC.get().create(test.getLevel());
        npc.moveTo(20_000_000.5, 100, 20_000_000.5);
        var pos = npc.blockPosition();
        test.assertTrue(test.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null, "fixture chunk unloaded");
        var snapshot = new com.viscript.npc.npc.ai.navigation.NavigationSnapshot(npc, npc.position().add(3, 0, 0));
        var result = com.viscript.npc.npc.ai.navigation.baritone.AStarPathFinder.calculate(snapshot, npc.position(), npc.position().add(3, 0, 0), .75, () -> false);
        test.assertTrue(!result.reason().isEmpty(), "unknown terrain cannot produce a route");
        test.assertTrue(test.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null, "snapshot did not force load");
        test.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aroundWall(GameTestHelper test) {
        for (int x = 0; x < 14; x++) for (int z = 0; z < 10; z++) {
            test.setBlock(x, 0, z, Blocks.STONE);
            for (int y = 1; y < 5; y++) test.setBlock(x, y, z, Blocks.AIR);
        }
        for (int z = 0; z < 7; z++) for (int y = 1; y < 4; y++) test.setBlock(6, y, z, Blocks.STONE);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 3));
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(10, 1, 3)));
        test.runAfterDelay(5, () -> test.assertTrue(npc.navigateTo(goal, .5), "request accepted"));
        test.succeedWhen(() -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED,
                    "arrival: " + npc.getNpcNavigation().status() + " / " + npc.getNpcNavigation().reason());
            test.assertTrue(npc.position().distanceTo(goal) < 1, "actual entity arrived");
            test.assertTrue(npc.getNavigation().isDone(), "vanilla navigation unused");
            test.assertBlockPresent(Blocks.STONE, 6, 1, 3);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void parkour(GameTestHelper test) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 5; z++) {
            for (int y = 0; y < 6; y++) test.setBlock(x, y, z, Blocks.AIR);
            if (x < 4 || x > 4) test.setBlock(x, 1, z, Blocks.STONE);
        }
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 2, 2));
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(8, 2, 2)));
        test.runAfterDelay(5, () -> npc.navigateTo(goal, .5));
        test.succeedWhen(() -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED,
                    "parkour arrival: " + npc.getNpcNavigation().reason());
            test.assertTrue(npc.position().distanceTo(goal) < 1, "actual landing");
            test.assertBlockPresent(Blocks.AIR, 4, 1, 2);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsObliqueTwoByOne(GameTestHelper test) {
        obliqueParkour(test, 2, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsObliqueOneByTwo(GameTestHelper test) {
        obliqueParkour(test, 1, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsObliqueNegativeX(GameTestHelper test) {
        obliqueParkour(test, -2, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsObliqueNegativeZ(GameTestHelper test) {
        obliqueParkour(test, 1, -2);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsObliqueNegativeXZ(GameTestHelper test) {
        obliqueParkour(test, -2, -1);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsObliqueThreeByOne(GameTestHelper test) {
        obliqueParkour(test, 3, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsObliqueThreeByTwo(GameTestHelper test) {
        obliqueParkour(test, 3, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsDiagonalThreeByThree(GameTestHelper test) {
        obliqueParkour(test, 3, 3);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsUsingPlatformEdges(GameTestHelper test) {
        obliqueParkour(test, 5, 0);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void jumpsUsingDiagonalPlatformEdges(GameTestHelper test) {
        obliqueParkour(test, 5, 3);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void rejectsBeyondPlayerJumpRange(GameTestHelper test) {
        CustomNpc npc = isolatedPlatforms(test, 6, 0);
        Vec3 start = npc.position();
        test.runAfterDelay(5, () -> npc.navigateTo(start.add(6, 0, 0), .5));
        test.succeedWhen(() -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.FAILED, "beyond normal player jump range");
            test.assertTrue(npc.onGround() && npc.position().distanceTo(start) < .1, "overlong jump stays on start platform");
        });
    }


    @GameTest(template = "empty", timeoutTicks = 240)
    public static void obliqueParkourRejectsBlockingWall(GameTestHelper test) {
        CustomNpc npc = isolatedPlatforms(test, 2, 1);
        for (int z = 0; z < 10; z++) for (int y = 2; y < 6; y++) test.setBlock(6, y, z, Blocks.STONE);
        Vec3 start = npc.position();
        test.runAfterDelay(5, () -> npc.navigateTo(start.add(2, 0, 1), .5));
        test.succeedWhen(() -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.FAILED, "obstructed jump cannot produce a route");
            test.assertTrue(npc.onGround() && npc.position().distanceTo(start) < .1, "blocked jump stays on start platform");
            test.assertBlockPresent(Blocks.STONE, 6, 2, 5);
        });
    }

    private static void obliqueParkour(GameTestHelper test, int dx, int dz) {
        CustomNpc npc = isolatedPlatforms(test, dx, dz);
        BlockPos start = new BlockPos(5, 2, 4), end = start.offset(dx, 0, dz);
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(end));
        test.runAfterDelay(5, () -> test.assertTrue(npc.navigateTo(goal, .5), "oblique request accepted"));
        test.succeedWhen(() -> {
            var navigation = npc.getNpcNavigation();
            test.assertTrue(navigation.status() == NavigationStatus.ARRIVED,
                    "oblique " + dx + "," + dz + ": " + navigation.status() + " / " + navigation.reason());
            test.assertTrue(npc.onGround() && npc.position().distanceTo(goal) < .3, "physical isolated platform landing");
            test.assertTrue(navigation.path().stream().filter(step -> step.movement() == AStarPathFinder.Movement.JUMP).count() == 1,
                    "one physical jump across the gap");
            test.assertBlockPresent(Blocks.AIR, start.offset(Integer.signum(dx), -1, Integer.signum(dz)));
            test.assertTrue(npc.getNavigation().isDone(), "vanilla navigation unused");
        });
    }

    private static CustomNpc isolatedPlatforms(GameTestHelper test, int dx, int dz) {
        for (int x = 0; x < 14; x++) for (int z = 0; z < 10; z++)
            for (int y = 0; y < 6; y++) test.setBlock(x, y, z, Blocks.AIR);
        BlockPos start = new BlockPos(5, 2, 4), end = start.offset(dx, 0, dz);
        test.setBlock(start.below(), Blocks.STONE);
        test.setBlock(end.below(), Blocks.STONE);
        return test.spawn(NpcRegister.CUSTOM_NPC.get(), start);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void climbsFullBlockStairs(GameTestHelper test) {
        floor(test);
        for (int x = 4; x <= 10; x++) for (int z = 0; z < 10; z++)
            for (int y = 1; y <= Math.min(3, x - 3); y++) test.setBlock(x, y, z, Blocks.STONE);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 4));
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(9, 4, 4)));
        test.runAfterDelay(5, () -> npc.navigateTo(goal, .5));
        test.succeedWhen(() -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED,
                    "full block stairs: " + npc.getNpcNavigation().status() + " / " + npc.getNpcNavigation().reason());
            test.assertTrue(npc.position().distanceTo(goal) < 1 && npc.onGround(), "physical stair landing");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void climbsRaisedParkourPlatforms(GameTestHelper test) {
        floor(test);
        for (int z = 3; z <= 5; z++) {
            test.setBlock(4, 1, z, Blocks.STONE);
            for (int y = 1; y <= 2; y++) test.setBlock(6, y, z, Blocks.STONE);
            for (int x = 8; x <= 10; x++) for (int y = 1; y <= 3; y++) test.setBlock(x, y, z, Blocks.STONE);
        }
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 4));
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(9, 4, 4)));
        test.runAfterDelay(5, () -> npc.navigateTo(goal, .5));
        test.succeedWhen(() -> {
            test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED,
                    "raised parkour: " + npc.getNpcNavigation().status() + " / " + npc.getNpcNavigation().reason());
            test.assertTrue(npc.position().distanceTo(goal) < 1 && npc.onGround(), "physical raised platform landing");
            test.assertBlockPresent(Blocks.AIR, 5, 2, 4);
            test.assertBlockPresent(Blocks.AIR, 7, 3, 4);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void dynamicObstacleReplansWithoutBreaking(GameTestHelper test) {
        floor(test);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 4));
        Vec3 goal = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(11, 1, 4)));
        boolean[] built = {false};
        test.runAfterDelay(5, () -> npc.navigateTo(goal, .5));
        test.onEachTick(() -> {
            if (!built[0] && npc.getNpcNavigation().status() == NavigationStatus.MOVING) {
                for (int z = 1; z <= 6; z++) for (int y = 1; y <= 4; y++) test.setBlock(7, y, z, Blocks.STONE);
                built[0] = true;
            }
        });
        test.succeedWhen(() -> {
            test.assertTrue(built[0] && npc.getNpcNavigation().status() == NavigationStatus.ARRIVED, "rerouted around new obstacle: " + npc.getNpcNavigation().reason());
            test.assertBlockPresent(Blocks.STONE, 7, 1, 4);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void descendsLedge(GameTestHelper test) {
        floor(test);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 10; z++) test.setBlock(x, 1, z, Blocks.STONE);
        CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 2, 4));
        test.runAfterDelay(5, () -> npc.navigateTo(Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(11, 1, 4))), .5));
        test.succeedWhen(() -> test.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED,
                "safe physical descent: " + npc.getNpcNavigation().reason()));
    }
}
