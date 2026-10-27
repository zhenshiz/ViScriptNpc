package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.npc.ai.data.AiNodeData;
import com.viscript.npc.npc.ai.data.AiValue;
import net.minecraft.world.phys.Vec3;

/** 有类型、无隐式类型转换的数值和向量动作；失败时不覆盖输出。 */
public final class AiCalculations {
    private AiCalculations() {}

    public static AiValue calculate(AiExecution e, AiNodeData node) {
        AiValue result;
        if (node.type.equals("number_math")) {
            double left = e.number(node, "left"), right = e.number(node, "right");
            String operator = e.text(node, "operator");
            if ((operator.equals("divide") || operator.equals("modulo")) && right == 0) throw new AiExecution.InputException("divide_by_zero");
            result = AiValue.number(switch (operator) {
                case "add" -> left + right; case "subtract" -> left - right; case "multiply" -> left * right;
                case "divide" -> left / right; case "modulo" -> left % right;
                case "minimum" -> Math.min(left, right); case "maximum" -> Math.max(left, right);
                default -> throw new AiExecution.InputException("choice");
            });
        } else if (node.type.equals("vector_math")) {
            Vec3 left = e.resolve(node, "vector_left").position, right = e.resolve(node, "vector_right").position;
            result = switch (e.text(node, "vector_operator")) {
                case "add" -> AiValue.position(left.add(right)); case "subtract" -> AiValue.position(left.subtract(right));
                case "scale" -> AiValue.position(left.scale(e.number(node, "factor")));
                case "normalize" -> AiValue.position(left.normalize());
                case "dot" -> AiValue.number(left.dot(right)); case "distance" -> AiValue.number(left.distanceTo(right));
                case "length" -> AiValue.number(left.length()); default -> throw new AiExecution.InputException("choice");
            };
        } else {
            double min = e.number(node, "minimum"), max = e.number(node, "maximum");
            if (min > max) throw new AiExecution.InputException("random_range");
            if (e.flag(node, "integer")) {
                min = Math.ceil(min); max = Math.floor(max);
                if (min > max || min < -1_000_000_000 || max > 1_000_000_000) throw new AiExecution.InputException("random_range");
                result = AiValue.number(min + e.npc.getRandom().nextInt((int) (max - min + 1)));
            } else {
                double fraction = e.npc.getRandom().nextDouble();
                result = AiValue.number(min == max ? min : min * (1 - fraction) + max * fraction);
            }
        }
        if (!Double.isFinite(result.number) || !Double.isFinite(result.position.lengthSqr())) throw new AiExecution.InputException("non_finite_result");
        return result;
    }
}
