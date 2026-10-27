/* Adapted from Baritone v1.11.3 PathNode, LGPL-3.0-or-later. See META-INF/licenses/baritone. */
package com.viscript.npc.npc.ai.navigation.baritone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** 保存搜索代价与堆索引；高度按半格编码以支持台阶。 */
public final class PathNode {
    public final int x, y, z;
    public final double estimatedCostToGoal;
    public double cost = ActionCosts.COST_INF;
    public double combinedCost;
    public PathNode previous;
    public Vec3 takeoff, landing;
    public int heapPosition = -1;
    public AStarPathFinder.Movement movement = AStarPathFinder.Movement.WALK;

    public PathNode(int x, int y, int z, Vec3 goal) {
        this.x = x;
        this.y = y;
        this.z = z;
        estimatedCostToGoal = Math.hypot(x + .5 - goal.x, z + .5 - goal.z)
                * ActionCosts.SPRINT_ONE_BLOCK_COST + Math.abs(y * .5 - goal.y) * 2;
    }

    public boolean isOpen() { return heapPosition != -1; }
    public Vec3 position() { return new Vec3(x + .5, y * .5, z + .5); }
    public long key() { return BlockPos.asLong(x, y, z); }
}
