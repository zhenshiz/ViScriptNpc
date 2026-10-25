package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.viscript.npc.util.NpcEditorFormats;
import com.viscript.npc.util.NpcEditorFiles;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript_lib.gui.editor.EditorFileFormat;
import com.viscript_lib.gui.editor.IRuntimeFileProject;
import com.viscript_lib.gui.editor.FunctionFileProjectType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * 保存 NPC AI 测试场景的独立项目。
 *
 * <p>场地只保存功能文件；可放置的 NPC 在使用时从服务端功能文件目录查询。
 */
public final class NpcTestSceneProject implements IRuntimeFileProject {
    public static final int VERSION = 3;
    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC_TEST_SCENE;
    public static final FunctionFileProjectType PROVIDER = new FunctionFileProjectType(
            IGuiTexture.EMPTY,
            Component.translatable("editor.project.npc_test_scene.add").getString(),
            FORMAT,
            NpcTestSceneProject::new) {
        @Override
        public File getRootSavePath(IProject project, File projectRoot) {
            return NpcEditorFiles.functionDirectory(FORMAT);
        }

        @Override
        public IProject loadProjectFromFile(File file) throws Exception {
            var project = new NpcTestSceneProject();
            project.deserializeProject(Platform.getFrozenRegistry(),
                    NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()));
            return project;
        }

        @Override
        public void saveProjectToFile(IProject project, File file) throws Exception {
            NbtIo.writeCompressed(project.serializeProject(Platform.getFrozenRegistry()), file.toPath());
        }

        @Override
        public boolean isProjectDirty(IProject project, File file) throws Exception {
            return ((NpcTestSceneProject) project).sceneBlocksDirty
                    || !project.serializeProject(Platform.getFrozenRegistry()).equals(
                    NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()));
        }
    };

    private final Resources resources;
    private int arenaWidth = NpcAiTestEnvironment.DEFAULT_ARENA_WIDTH;
    private int arenaLength = NpcAiTestEnvironment.DEFAULT_ARENA_LENGTH;
    private CompoundTag sceneBlocks = new CompoundTag();
    private boolean sceneBlocksDirty;
    @Nullable
    private BlockPos runtimeArenaCenter;
    public int runtimeNpcId = -1;

    /** 创建具备编辑器基础资源的空场景工程。 */
    public NpcTestSceneProject() {
        resources = Platform.isClient()
                ? Resources.of(ColorsResource.INSTANCE, TexturesResource.INSTANCE, IRendererResource.INSTANCE)
                : Resources.of(ColorsResource.INSTANCE, TexturesResource.INSTANCE);
    }

    /**
     * 获取当前测试会话由服务端分配的场地中心。
     *
     * @return 仅用于当前编辑器会话的真实维度坐标
     */
    public BlockPos getRuntimeArenaCenter() {
        if (runtimeArenaCenter == null) {
            throw new IllegalStateException("当前测试场景尚未分配运行时场地");
        }
        return runtimeArenaCenter;
    }

    /**
     * 判断当前场景是否已经收到服务端分配的运行时场地。
     *
     * @return 已分配场地中心时返回 {@code true}
     */
    public boolean hasRuntimeArenaCenter() {
        return runtimeArenaCenter != null;
    }

    /**
     * 获取场地沿 X 轴占用的方块数。
     *
     * @return 已限制到测试场景允许范围内的场地宽度
     */
    public int getArenaWidth() {
        return arenaWidth;
    }

    /**
     * 获取场地沿 Z 轴占用的方块数。
     *
     * @return 已限制到测试场景允许范围内的场地长度
     */
    public int getArenaLength() {
        return arenaLength;
    }

    /**
     * 同时更新测试场地的宽度和长度。
     *
     * @param width 场地沿 X 轴的方块数
     * @param length 场地沿 Z 轴的方块数
     * @return 尺寸实际发生变化时返回 {@code true}
     */
    public boolean setArenaSize(int width, int length) {
        int normalizedWidth = NpcAiTestEnvironment.normalizeArenaSize(width);
        int normalizedLength = NpcAiTestEnvironment.normalizeArenaSize(length);
        if (arenaWidth == normalizedWidth && arenaLength == normalizedLength) {
            return false;
        }
        arenaWidth = normalizedWidth;
        arenaLength = normalizedLength;
        return true;
    }

    /**
     * 设置服务端为当前测试会话分配的场地中心。
     *
     * <p>该数据不会写入工程文件，避免再次打开工程时错误复用旧场地。
     *
     * @param arenaCenter 当前会话的真实维度场地中心
     */
    public void setRuntimeArenaCenter(BlockPos arenaCenter) {
        runtimeArenaCenter = arenaCenter.immutable();
    }

    /**
     * 获取以场地最小角为原点的方块结构快照。
     *
     * @return 包含方块状态、相对坐标和方块实体数据的副本；未捕获场地时返回空标签
     */
    public CompoundTag getSceneBlocks() {
        return sceneBlocks.copy();
    }

    /**
     * 更新保存和导出时使用的服务端场地方块快照。
     *
     * @param snapshot Minecraft 结构模板格式的方块数据
     */
    public void setSceneBlocks(CompoundTag snapshot) {
        sceneBlocks = snapshot.copy();
        sceneBlocksDirty = false;
    }

    /** 标记场地方块有尚未读取到工程快照中的修改。 */
    public void markSceneBlocksDirty() {
        sceneBlocksDirty = true;
    }

    @Override
    public String getVersion() {
        return VERSION + ".0";
    }

    @Override
    public FunctionFileProjectType getProjectType() {
        return PROVIDER;
    }

    @Override
    public Resources getResources() {
        return resources;
    }

    @Override
    public CompoundTag serializeProject(@NotNull HolderLookup.Provider provider) {
        CompoundTag project = new CompoundTag();
        project.putBoolean("test_scene", true);
        project.putInt("arena_width", arenaWidth);
        project.putInt("arena_length", arenaLength);
        if (!sceneBlocks.isEmpty()) project.put("scene_blocks", sceneBlocks.copy());
        return project;
    }

    @Override
    public CompoundTag serializeRuntimeFile(@NotNull HolderLookup.Provider provider) {
        return serializeProject(provider);
    }

    @Override
    public void deserializeProject(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        setArenaSize(nbt.getInt("arena_width"), nbt.getInt("arena_length"));
        setSceneBlocks(nbt.getCompound("scene_blocks"));
    }

    @Override
    public CompoundTag getMetadata() {
        CompoundTag metadata = IRuntimeFileProject.super.getMetadata();
        metadata.putInt("version_num", VERSION);
        return metadata;
    }

}
