package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.navigation.*;
import com.viscript.npc.npc.ai.navigation.baritone.AStarPathFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.ArrayList;

/** 验证开门、游泳、梯子和批量调度，使用真实实体碰撞而非只检查路径。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcNavigationExtensionTests {
    private static void floor(GameTestHelper t) {
        for (int x = 0; x < 14; x++) for (int z = 0; z < 10; z++) {
            t.setBlock(x, 0, z, Blocks.STONE);
            for (int y = 1; y <= 7; y++) t.setBlock(x, y, z, Blocks.AIR);
        }
    }
    private static CustomNpc npc(GameTestHelper t, int x, int y, int z) {
        var npc = t.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(x, y, z));
        npc.getAiController().manualControl(); return npc;
    }
    private static Vec3 goal(GameTestHelper t, int x, int y, int z) { return Vec3.atBottomCenterOf(t.absolutePos(new BlockPos(x, y, z))); }
    private static void doorWall(GameTestHelper t, boolean iron) {
        floor(t);
        for (int x = 0; x < 14; x++) for (int z = 0; z < 10; z++) {
            t.setBlock(x, 6, z, Blocks.STONE);
            if (x == 0 || x == 13 || z == 0 || z == 9)
                for (int y = 1; y <= 5; y++) t.setBlock(x, y, z, Blocks.STONE);
        }
        for (int z = 0; z < 10; z++) for (int y = 1; y <= 5; y++) t.setBlock(6, y, z, Blocks.STONE);
        var door = (iron ? Blocks.IRON_DOOR : Blocks.OAK_DOOR).defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        t.setBlock(new BlockPos(6, 1, 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        t.setBlock(new BlockPos(6, 2, 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    @GameTest(template = "empty", batch = "npc_navigation_extensions", timeoutTicks = 400)
    public static void opensWoodenDoorAndActuallyWalksThrough(GameTestHelper t) {
        doorWall(t, false); var npc = npc(t, 2, 1, 4); var goal = goal(t, 10, 1, 4);
        t.runAfterDelay(5, () -> npc.getNpcNavigation().request(goal, .4, .7, new NavigationOptions(true, false, false)));
        t.succeedWhen(() -> {
            t.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED, "wooden door route: " + npc.getNpcNavigation().reason() + " " + npc.position());
            t.assertTrue(t.getBlockState(new BlockPos(6, 1, 4)).getValue(DoorBlock.OPEN), "the real door was opened");
            t.assertTrue(npc.position().distanceTo(goal) < .9, "NPC physically reached the other side");
        });
    }

    @GameTest(template = "empty", batch = "npc_navigation_extensions", timeoutTicks = 40)
    public static void closedDoorsRequireCapabilityAndIronDoorsStayBlocked(GameTestHelper t) {
        doorWall(t, false); var npc = npc(t, 2, 1, 4); var goal = goal(t, 10, 1, 4);
        var ground = AStarPathFinder.calculate(new NavigationSnapshot(npc, goal), npc.position(), goal, .7, () -> false);
        t.assertTrue(!ground.reason().isEmpty(), "ground mode does not pass through a closed door");
        doorWall(t, true);
        var snapshot = NavigationSnapshot.capture(npc, goal, new NavigationOptions(true, false, false));
        var iron = AStarPathFinder.calculate(snapshot, npc.position(), goal, .7, () -> false);
        t.assertTrue(!iron.reason().isEmpty() && !t.getBlockState(new BlockPos(6, 1, 4)).getValue(DoorBlock.OPEN), "open-door capability cannot open iron doors"); t.succeed();
    }

    @GameTest(template = "empty", batch = "npc_navigation_extensions", timeoutTicks = 500)
    public static void swimsAcrossDeepWaterAndExitsOntoBank(GameTestHelper t) {
        floor(t);
        for (int x = 0; x < 14; x++) {
            for (int z = 2; z <= 6; z++) for (int y = 1; y <= 2; y++)
                t.setBlock(x, y, z, x < 3 || x > 10 ? Blocks.STONE : Blocks.WATER);
            for (int y = 1; y <= 4; y++) { t.setBlock(x, y, 2, Blocks.STONE); t.setBlock(x, y, 6, Blocks.STONE); }
        }
        var npc = npc(t, 1, 3, 4); var goal = goal(t, 12, 3, 4);
        boolean[] swam = {false};
        t.onEachTick(() -> { if (npc.isInWater()) swam[0] = true; });
        t.runAfterDelay(5, () -> npc.getNpcNavigation().request(goal, .4, .7, new NavigationOptions(false, true, false)));
        t.succeedWhen(() -> {
            t.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED, "swim route: " + npc.getNpcNavigation().status() + " " + npc.getNpcNavigation().reason() + " " + npc.position()
                    + " water=" + npc.isInWater() + " collision=" + npc.horizontalCollision + " velocity=" + npc.getDeltaMovement());
            t.assertTrue(swam[0] && npc.position().distanceTo(goal) < .9, "NPC entered water and reached the bank");
        });
    }

    @GameTest(template = "empty", batch = "npc_navigation_extensions", timeoutTicks = 500)
    public static void climbsLadderAndStepsOntoRaisedPlatform(GameTestHelper t) {
        floor(t);
        for (int z = 0; z < 10; z++) {
            for (int y = 1; y <= 4; y++) t.setBlock(6, y, z, Blocks.STONE);
            for (int x = 6; x < 14; x++) t.setBlock(x, 4, z, Blocks.STONE);
        }
        for (int y = 1; y <= 4; y++) t.setBlock(new BlockPos(5, y, 4), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        var npc = npc(t, 3, 1, 4); var goal = goal(t, 9, 5, 4); boolean[] climbed = {false};
        t.onEachTick(() -> { if (npc.onClimbable() && npc.getY() > goal.y - 3) climbed[0] = true; });
        t.runAfterDelay(5, () -> npc.getNpcNavigation().request(goal, .4, .7, new NavigationOptions(false, false, true)));
        t.succeedWhen(() -> {
            t.assertTrue(npc.getNpcNavigation().status() == NavigationStatus.ARRIVED, "ladder route: " + npc.getNpcNavigation().reason() + " " + npc.position());
            t.assertTrue(climbed[0] && npc.position().distanceTo(goal) < .9, "NPC climbed and landed on the upper platform");
        });
    }

    @GameTest(template = "empty", batch = "npc_navigation_burst", timeoutTicks = 400)
    public static void burstDispatchesMultipleRequestsAndCoalescesPendingGoals(GameTestHelper t) {
        floor(t); var npcs = new ArrayList<CustomNpc>(); var goal = goal(t, 10, 1, 4);
        for (int i = 0; i < 24; i++) npcs.add(npc(t, 2, 1, 4));
        var nav = npcs.getFirst().getNpcNavigation();
        nav.request(goal.add(-1, 0, 0), .4, .7);
        nav.request(goal, .4, .7); nav.request(goal, .4, .7);
        t.assertTrue(nav.coalescedRequests() == 2, "pending replacements and duplicate requests are coalesced");
        npcs.stream().skip(1).forEach(npc -> npc.getNpcNavigation().request(goal, .4, .7));
        int[] peak = {0}; t.onEachTick(() -> peak[0] = Math.max(peak[0], NpcNavigation.lastDispatchCount()));
        t.succeedWhen(() -> {
            t.assertTrue(npcs.stream().allMatch(npc -> npc.getNpcNavigation().status() != NavigationStatus.COMPUTING), "all queued requests delivered");
            t.assertTrue(peak[0] > 1 && peak[0] <= 8, "dispatches multiple small snapshots within the bounded batch");
            t.assertTrue(npcs.stream().noneMatch(npc -> npc.getNpcNavigation().status() == NavigationStatus.FAILED), "burst does not reject valid requests");
            t.assertTrue(nav.path().getLast().position().distanceTo(goal) < .9, "the latest pending destination wins");
            npcs.forEach(npc -> npc.getAiController().manualControl());
        });
    }

    @GameTest(template = "ai_scale", batch = "npc_navigation_complex", timeoutTicks = 1200)
    public static void twentyFourNpcsNavigateWallsAndRaisedPlatforms(GameTestHelper t) {
        for (int x = 0; x < 62; x++) for (int z = 0; z < 60; z++) t.setBlock(x, 0, z, Blocks.STONE);
        var npcs = new ArrayList<CustomNpc>();
        for (int i = 0; i < 24; i++) {
            int x = 2 + i % 4 * 14, z = 3 + i / 4 * 10;
            for (int dz = -2; dz <= 1; dz++) for (int y = 1; y <= 3; y++) t.setBlock(x + 5, y, z + dz, Blocks.STONE);
            for (int dx = 8; dx <= 11; dx++) for (int dz = -3; dz <= 3; dz++) t.setBlock(x + dx, 1, z + dz, Blocks.STONE);
            var npc = npc(t, x, 1, z); npcs.add(npc);
            npc.getNpcNavigation().request(goal(t, x + 10, 2, z), .4, .7);
        }
        t.succeedWhen(() -> {
            t.assertTrue(npcs.stream().allMatch(npc -> npc.getNpcNavigation().status() == NavigationStatus.ARRIVED),
                    "complex terrain arrived=" + npcs.stream().filter(npc -> npc.getNpcNavigation().status() == NavigationStatus.ARRIVED).count()
                            + " failures=" + npcs.stream().filter(npc -> npc.getNpcNavigation().status() == NavigationStatus.FAILED)
                            .map(npc -> npc.getNpcNavigation().reason()).toList());
            t.assertTrue(npcs.stream().allMatch(npc -> npc.getNpcNavigation().failedRequests() == 0), "all complex routes complete without rejected requests");
            ViScriptNpc.LOGGER.info("NPC AI COMPLEX: NPCs=24; arrived=24; maxQueueMs={}; maxSearchMs={}",
                    npcs.stream().mapToDouble(npc -> npc.getNpcNavigation().queueNanos() / 1_000_000d).max().orElse(0),
                    npcs.stream().mapToDouble(npc -> npc.getNpcNavigation().searchNanos() / 1_000_000d).max().orElse(0));
            npcs.forEach(npc -> npc.getAiController().manualControl());
        });
    }

    @GameTest(template = "empty", batch = "npc_navigation_extensions", timeoutTicks = 40)
    public static void dangerousFloorsAndDisabledWaterAreNotStandingSurfaces(GameTestHelper t) {
        floor(t); var npc = npc(t, 2, 1, 4);
        t.setBlock(7, 0, 4, Blocks.MAGMA_BLOCK); t.setBlock(8, 0, 4, Blocks.WATER);
        var snapshot = new NavigationSnapshot(npc, goal(t, 10, 1, 4));
        t.assertTrue(!snapshot.standable(goal(t, 7, 1, 4)), "hazard blockers do not become artificial safe floors");
        t.assertTrue(!snapshot.standable(goal(t, 8, 1, 4)), "disabled water does not become an artificial solid platform");
        t.assertTrue(!npc.getNpcNavigation().canStandAt(goal(t, 7, 1, 4)), "retreat and patrol reject hazardous ground"); t.succeed();
    }
}
