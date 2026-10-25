package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.AlignContent;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/** 行为编辑器和测试工具共用的单色线框图标，使用统一的 16 格画布和线宽。 */
public final class AiIcons {
    private AiIcons() {}

    /**
     * 创建可以独立接受主题着色的图标。
     *
     * @param key 工具的语义标识
     * @return 不共享颜色状态的纹理
     */
    public static IGuiTexture of(String key) {
        float[][] paths = switch (key) {
            case "save" -> new float[][]{{2,2,12,2,14,4,14,14,2,14,2,2},{5,2,5,6,11,6,11,2},{5,14,5,9,11,9,11,14}};
            case "undo" -> new float[][]{{6,3,2,6,6,9},{2,6,10,6,13,9,13,13}};
            case "redo" -> new float[][]{{10,3,14,6,10,9},{14,6,6,6,3,9,3,13}};
            case "arrange" -> new float[][]{{5,2,11,2,11,5,5,5,5,2},{8,5,8,8},{3,10,3,8,13,8,13,10},{1,10,5,10,5,14,1,14,1,10},{11,10,15,10,15,14,11,14,11,10}};
            case "fit" -> new float[][]{{6,2,2,2,2,6},{10,2,14,2,14,6},{2,10,2,14,6,14},{10,14,14,14,14,10}};
            case "variables" -> new float[][]{{5,2,3,2,3,6,1,8,3,10,3,14,5,14},{11,2,13,2,13,6,15,8,13,10,13,14,11,14},{7,6,9,10},{9,6,7,10}};
            case "blackboard" -> new float[][]{{2,2,14,2,14,12,2,12,2,2},{5,5,11,5},{5,8,9,8},{8,12,8,15}};
            case "parameters", "action_info" -> new float[][]{{2,4,14,4},{2,8,14,8},{2,12,14,12},{5,2,5,6},{11,6,11,10},{7,10,7,14}};
            case "library" -> new float[][]{{2,3,5,3,5,13,2,13,2,3},{7,3,10,3,10,13,7,13,7,3},{12,3,14,13}};
            case "examples" -> new float[][]{{2,2,6,2,6,6,2,6,2,2},{10,2,14,2,14,6,10,6,10,2},{2,10,6,10,6,14,2,14,2,10},{10,10,14,10,14,14,10,14,10,10}};
            case "copy_example" -> new float[][]{{2,2,10,2,10,5},{2,2,2,11,5,11},{5,5,14,5,14,14,5,14,5,5}};
            case "input" -> new float[][]{{1,8,12,8},{8,4,12,8,8,12},{14,3,14,13}};
            case "run" -> new float[][]{{4,2,13,8,4,14,4,2}};
            case "scene_window" -> new float[][]{{2,7,2,14,10,14,10,12},{6,2,14,2,14,10,6,10,6,2},{6,5,14,5}};
            case "pause" -> new float[][]{{4,3,4,13},{11,3,11,13}};
            case "step" -> new float[][]{{3,3,10,8,3,13,3,3},{13,3,13,13}};
            case "step_tick" -> new float[][]{{8,1,13,3,15,8,13,13,8,15,3,13,1,8,3,3,8,1},{8,4,8,8,11,10}};
            case "debug" -> new float[][]{{4,5,12,5,12,13,4,13,4,5},{6,2,10,2,10,5,6,5,6,2},{1,6,4,7},{12,7,15,6},{1,12,4,11},{12,11,15,12},{8,5,8,13}};
            case "select" -> new float[][]{{3,2,3,13,6,10,9,14,11,13,8,9,13,8,3,2}};
            case "target" -> new float[][]{{4,4,12,4,12,12,4,12,4,4},{8,1,8,6},{8,10,8,15},{1,8,6,8},{10,8,15,8}};
            case "place_npc" -> new float[][]{{3,3,10,3,10,10,3,10,3,3},{5,6,5,7},{8,6,8,7},{3,14,3,12,8,12},{12,10,12,15},{10,12,15,12}};
            case "place_entity" -> new float[][]{{2,2,5,4,10,4,13,2,13,11,10,14,5,14,2,11,2,2},{5,7,5,8},{10,7,10,8},{6,11,9,11}};
            case "place_player" -> new float[][]{{5,2,11,2,11,7,5,7,5,2},{2,14,2,11,5,9,11,9,14,11,14,14}};
            case "place_block" -> new float[][]{{2,5,8,2,14,5,14,11,8,14,2,11,2,5,8,8,14,5},{8,8,8,14}};
            case "break_block" -> new float[][]{{2,5,8,2,14,5,14,11,8,14,2,11,2,5},{6,4,10,7,6,10,10,13}};
            case "remove", "exit" -> new float[][]{{3,3,13,13},{13,3,3,13}};
            case "reset" -> new float[][]{{2,2,2,7,7,7},{2,7,5,3,11,3,14,7,13,12,9,14,5,12}};
            case "add_variable", "apply_example" -> new float[][]{{8,2,8,14},{2,8,14,8}};
            default -> throw new IllegalArgumentException("Unknown AI icon: " + key);
        };
        return new StrokeIcon(paths);
    }

    /**
     * 给按钮和选中工具按钮提供相同的内边距、图标尺寸及居中方式。
     *
     * @param button 需要装饰的按钮
     * @param key 图标的语义标识
     */
    public static void decorate(UIElement button, String key) {
        button.addClass("npc_ai_icon_button");
        button.layout(l -> l.width(20).height(20).minWidth(20).paddingAll(3).gapAll(0)
                .flexGrow(0).flexShrink(0).alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER));
        button.addChild(new UIElement().addClass("__white_icon__").addClass("npc_ai_toolbar_icon")
                .layout(l -> l.width(14).height(14).minWidth(14).minHeight(14).flexShrink(0))
                .style(s -> s.backgroundTexture(of(key))));
    }

    private static final class StrokeIcon implements IGuiTexture {
        private final List<List<Vector2f>> paths;
        private int color = 0xFFFFFFFF;

        private StrokeIcon(float[][] coordinates) {
            paths = new ArrayList<>();
            for (float[] line : coordinates) {
                var points = new ArrayList<Vector2f>();
                for (int i = 0; i < line.length; i += 2) points.add(new Vector2f(line[i], line[i + 1]));
                paths.add(points);
            }
        }

        private StrokeIcon(List<List<Vector2f>> paths, int color) { this.paths = paths; this.color = color; }
        @Override public IGuiTexture copy() { return new StrokeIcon(paths, color); }
        @Override public IGuiTexture setColor(int color) { this.color = color; return this; }
        @Override public void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y,
                                   float width, float height, float partialTicks) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(width / 16, height / 16, 1);
            for (var path : paths) DrawerHelper.drawLines(graphics, path, color, color, .7f);
            graphics.pose().popPose();
        }
    }
}
