package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.npc.ai.data.AiNodeData;
import com.viscript.npc.npc.ai.data.AiPerceptionVolume;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** 距离查询和持续感知使用相同的几何范围、阵营、类型与遮挡规则。 */
public final class AiPerception {
    private AiPerception() {}

    /**
     * 解析当前 tick 的感知范围；扇形跟随水平头部朝向，不随俯仰倾斜。
     *
     * @param execution 正在执行的 NPC 实例
     * @param node 寻找目标或持续感知节点
     * @return 可直接用于服务器查询与调试同步的感知体
     */
    public static AiPerceptionVolume volume(AiExecution execution, AiNodeData node) {
        var volume = new AiPerceptionVolume();
        volume.mode = execution.text(node, "perception");
        volume.origin = execution.npc.position();
        volume.forward = Vec3.directionFromRotation(0, execution.npc.getYHeadRot());
        volume.range = Math.clamp(execution.number(node, "range"), 0, 64);
        volume.viewRadius = Math.clamp(execution.number(node, "view_radius"), 0, 64);
        volume.viewAngle = Math.clamp(execution.number(node, "view_angle"), 0, 360);
        volume.height = Math.clamp(execution.number(node, "height"), 0, 128);
        volume.requireSight = execution.flag(node, "require_sight");
        return volume;
    }

    /**
     * 查找最近的有效目标；当前目标仍有效时优先保留，避免相邻实体引起反复切换。
     *
     * @param execution NPC 执行实例
     * @param node 选敌节点
     * @param volume 本次查询的实际范围
     * @param retainTarget 是否优先保留仍在范围内的当前目标
     * @return 合法的存活目标；没有符合条件的实体时返回 {@code null}
     */
    public static LivingEntity find(AiExecution execution, AiNodeData node, AiPerceptionVolume volume, boolean retainTarget) {
        var npc = execution.npc;
        String filter = execution.text(node, "filter");
        String entityType = filter.equals("living") ? execution.text(node, "entity_type") : "";
        java.util.function.Predicate<LivingEntity> eligible = candidate -> candidate != npc && candidate.isAlive()
                && candidate.level() == npc.level() && candidate.isAttackable()
                && !(candidate instanceof Player player && (player.isCreative() || player.isSpectator()))
                && NpcFactionBridge.canHurt(npc, candidate)
                && (!filter.equals("living") || candidate instanceof Mob)
                && (entityType.isEmpty() || BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType()).toString().equals(entityType))
                && (!filter.equals("player") || candidate instanceof Player)
                && (!filter.equals("hostile") || NpcFactionBridge.shouldActivelyTarget(npc, candidate))
                && volume.contains(candidate.position()) && (!volume.requireSight || npc.hasLineOfSight(candidate));
        if (retainTarget && npc.getTarget() != null && eligible.test(npc.getTarget())) return npc.getTarget();
        double extent = Math.max(volume.hasRange() ? volume.range : 0,
                volume.hasView() ? volume.viewRadius : 0);
        return ((ServerLevel) npc.level()).getEntitiesOfClass(LivingEntity.class,
                        npc.getBoundingBox().inflate(extent, volume.height / 2, extent), eligible)
                .stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
    }
}
