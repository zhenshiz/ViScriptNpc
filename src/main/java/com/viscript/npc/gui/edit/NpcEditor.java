package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.util.TreeNode;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.ViScriptNpcRegistries;
import com.viscript.npc.gui.edit.page.INpcEditorPage;
import com.viscript.npc.gui.edit.view.*;
import com.viscript.npc.gui.test.NpcAiTestSceneView;
import com.viscript.npc.gui.test.NpcTestSceneProject;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.data.basics_setting.NpcBasicsSetting;
import com.viscript.npc.util.NpcEditorFiles;
import com.viscript.npc.util.NpcAiPublication;
import com.viscript_lib.gui.editor.*;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class NpcEditor extends ProjectFileEditor {
    public static final ResourceLocation EDITOR_ID = ViScriptNpc.id("editor");
    public final static SpriteTexture ICON = SpriteTexture.of(ViScriptNpc.formattedMod("textures/icon.png"));
    private static int NEXT_UNTITLED_PROJECT_INDEX = 1;
    private static final float PROJECT_TAB_MAX_WIDTH = 220.0f;
    private static final float PROJECT_TAB_MIN_WIDTH = 90.0f;

    @Getter
    public final NpcListView npcListView = new NpcListView(this);
    private final UIElement npcEditorPageBar = new UIElement();
    private final Map<String, INpcEditorPage> npcEditorPages = new LinkedHashMap<>();
    private final Map<String, Tab> npcEditorPageTabs = new LinkedHashMap<>();
    private final Map<String, View> npcEditorPageCenterViews = new LinkedHashMap<>();
    private final Map<String, View> npcEditorPageLeftViews = new LinkedHashMap<>();
    private final Map<String, View> npcEditorPageRightViews = new LinkedHashMap<>();
    private final Map<String, View> npcEditorPageBottomViews = new LinkedHashMap<>();
    private final Map<String, Object> npcEditorPageStates = new LinkedHashMap<>();
    private final NpcInspectorView npcInspectorView = new NpcInspectorView(this);
    @Nullable
    private NPCPreviewView npcPreviewView;
    @Nullable
    private INpcEditorPage selectedNpcEditorPage;
    private int untitledProjectIndex;
    private int lastProjectTabCount = -1;
    private float lastProjectTabAvailableWidth = -1.0f;
    private boolean lastProjectPageBarOffset;
    @Nullable
    private NpcAiTestSceneView npcTestSceneView;
    @Nullable
    private String sceneSnapshotRequest;
    @Nullable
    private Runnable sceneSnapshotAction;
    @Nullable
    private NpcTestSceneProject sceneSnapshotProject;
    private long sceneSnapshotDeadline;

    public NpcEditor() {
        registerProjectType(NPCProject.PROVIDER);
        registerProjectType(com.viscript.npc.gui.ai.NpcActionProject.PROVIDER);
        registerProjectType(NpcTestSceneProject.PROVIDER);
        this.icon.style(style -> style.backgroundTexture(ICON));
        detachDefaultInspectorView();
        initNpcEditorPageBar();
        addEventListener(UIEvents.KEY_DOWN, event -> {
            if (npcTestSceneView == null || event.modifiers != 0) return;
            for (UIElement focused = event.target; focused != null && focused != this; focused = focused.getParent()) {
                if (focused instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.TextField) return;
            }
            if (npcTestSceneView.handleToolbarShortcut(event.keyCode)) {
                event.hasHandler = true;
                event.stopPropagation();
            }
        });
    }

    @Override
    protected NpcEditor createNewEditorInstance() {
        return new NpcEditor();
    }

    @Override
    public void saveProject(@Nullable Runnable onFinish, boolean showNotification) {
        if (getCurrentProjectFile() == null) {
            saveAsProject(onFinish);
            return;
        }
        withTestSceneSnapshot(() -> super.saveProject(onFinish, showNotification));
    }

    @Override
    public void saveAsProject(@Nullable File defaultSaveFile, @Nullable Runnable onFinish) {
        withTestSceneSnapshot(() -> super.saveAsProject(defaultSaveFile, onFinish));
    }

    /**
     * 获取当前场地的服务端方块快照后执行保存、导出或离开操作。
     *
     * @param action 收到匹配快照后执行的客户端操作
     */
    public void withTestSceneSnapshot(Runnable action) {
        if (!(getCurrentProject() instanceof NpcTestSceneProject project)
                || !project.hasRuntimeArenaCenter()
                || !NpcAiTestEnvironment.isTestLevel(Minecraft.getInstance().level)) {
            action.run();
            return;
        }
        if (sceneSnapshotRequest != null) return;
        sceneSnapshotRequest = java.util.UUID.randomUUID().toString();
        sceneSnapshotAction = action;
        sceneSnapshotProject = project;
        sceneSnapshotDeadline = net.minecraft.Util.getMillis() + 10000L;
        RPCPacketDistributor.rpcToServer(C2SPayload.CAPTURE_NPC_AI_TEST_SCENE,
                sceneSnapshotRequest, project.getRuntimeArenaCenter().asLong());
    }

    /**
     * 接收与本编辑器保存请求匹配的场地快照。
     *
     * @param requestId 保存请求标识
     * @param payload 服务端场地快照或失败标识
     */
    public void receiveTestSceneSnapshot(String requestId, CompoundTag payload) {
        if (!requestId.equals(sceneSnapshotRequest)) return;
        Runnable action = sceneSnapshotAction;
        NpcTestSceneProject project = sceneSnapshotProject;
        clearSceneSnapshotRequest();
        if (project != getCurrentProject()) return;
        if (!payload.getBoolean("success")) {
            showSceneSnapshotFailure();
            return;
        }
        project.setSceneBlocks(payload.getCompound("scene_blocks"));
        if (action != null) action.run();
    }

    private void clearSceneSnapshotRequest() {
        sceneSnapshotRequest = null;
        sceneSnapshotProject = null;
        sceneSnapshotAction = null;
    }

    private void showSceneSnapshotFailure() {
        Dialog.showNotification("editor.error", "viscript_npc.editor.ai_test.snapshot_failed", null)
                .show(getModularUI());
    }

    @Override
    protected void initMenus() {
        super.initMenus();
        fileMenu.registerMenuCreator((tab, menu) -> {
            if (menu.peek() instanceof TreeNode<net.minecraft.util.Tuple<IGuiTexture, Component>, Runnable> node) {
                for (var child : node.getChildren()) {
                    var key = child.getKey();
                    if (!(key.getB().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)) continue;
                    if (text.getKey().equals("ldlib.gui.editor.menu.open"))
                        node.addContent(key, this::openNpcFile);
                    else if (text.getKey().equals("viscript_lib.editor.menu.export_runtime_file"))
                        node.addContent(key, this::exportNpcRuntimeFile);
                }
            }
        });
    }

    private void openNpcFile() {
        var suffixes = getProjectTypes().stream().map(ProjectType::getSuffix).toArray(String[]::new);
        var root = EditorAssetFiles.resolveAssetDirectory(ViScriptNpc.MOD_ID, "").toFile();
        Dialog.showFileDialog("ldlib.gui.editor.tips.load_project", root, true, Dialog.suffixFilter(suffixes), file -> {
            if (file != null && file.isFile()) getProjectTypes().stream()
                    .filter(type -> file.getName().endsWith(type.getSuffix())).findFirst()
                    .ifPresent(type -> loadProjectFileWithUnavailableItemWarning(type, file));
        }).show(getModularUI());
    }

    private void exportNpcRuntimeFile() {
        withTestSceneSnapshot(this::exportNpcRuntimeFileWithSnapshot);
    }

    private void exportNpcRuntimeFileWithSnapshot() {
        if (!(getCurrentProject() instanceof IRuntimeFileProject project)
                || !(project.getProjectType() instanceof ProjectFileProjectType type)) return;
        var format = type.getFormat();
        var directory = NpcEditorFiles.functionDirectory(format);
        String base = getCurrentProjectFile() == null ? "test" : EditorFileNames.normalizeBaseName(
                getCurrentProjectFile().getName(), format.projectSuffix(), format.runtimeSuffix());
        Dialog.showFileDialog("viscript_lib.editor.dialog.export_runtime_file", directory, false,
                new File(directory, base + format.runtimeSuffix()), Dialog.suffixFilter(format.runtimeSuffix()), file -> {
                    if (!validateLocalSaveFile(file, format.runtimeSuffix())) return;
                    var destination = file.getName().endsWith(format.runtimeSuffix()) ? file
                            : new File(file.getParentFile(), file.getName() + format.runtimeSuffix());
                    try {
                        Files.createDirectories(destination.toPath().getParent());
                        NbtIo.writeCompressed(project.serializeRuntimeFile(Platform.getFrozenRegistry()), destination.toPath());
                        Dialog.showNotification("viscript_lib.editor.runtime_file_export_success", 2).show(getModularUI());
                    } catch (Exception exception) {
                        ViScriptNpc.LOGGER.warn("无法导出 NPC 功能文件 {}", destination, exception);
                        Dialog.showNotification("editor.error", "viscript_lib.editor.runtime_file_export_failed", null).show(getModularUI());
                    }
                }).show(getModularUI());
    }

    @Override
    @Nullable
    protected EditorUploadAction createUploadProjectAction() {
        if (getCurrentProject() instanceof NpcTestSceneProject) return null;
        if (getCurrentProject() instanceof com.viscript.npc.gui.ai.NpcActionProject action) {
            var format = action.FORMAT;
            String base = getCurrentProjectFile() == null ? "action" : EditorFileNames.normalizeBaseName(
                    getCurrentProjectFile().getName(), format.projectSuffix(), format.runtimeSuffix());
            return new NpcUploadAction("viscript_lib.editor.menu.upload_project_file",
                    "viscript_lib.editor.dialog.upload_project_file", base, format.projectSuffix(),
                    name -> EditorFileNames.normalizeFileName(name, format.projectSuffix()),
                    name -> NpcAiPublication.request("action", name, null, action.serializeNBT(Platform.getFrozenRegistry())));
        }
        if (getCurrentProject() instanceof NPCProject project) {
            EditorFileFormat format = NPCProject.FORMAT;
            return new NpcUploadAction(
                    "viscript_lib.editor.menu.upload_project_file",
                    "viscript_lib.editor.dialog.upload_project_file",
                    defaultBaseName(project, format),
                    format.projectSuffix(),
                    fileName -> EditorFileNames.normalizeFileName(fileName, format.projectSuffix()),
                    fileName -> NpcAiPublication.request("npc", fileName, null, project.serializeNBT(Platform.getFrozenRegistry()))
            );
        }
        return super.createUploadProjectAction();
    }

    @Override
    protected EditorUploadAction createUploadRuntimeAction() {
        if (getCurrentProject() instanceof com.viscript.npc.gui.ai.NpcActionProject action) return actionUpload(action, false);
        if (getCurrentProject() instanceof NPCProject project) {
            EditorFileFormat format = NPCProject.FORMAT;
            return new NpcUploadAction(
                    "viscript_lib.editor.menu.upload_runtime_file",
                    "viscript_lib.editor.dialog.upload_runtime_file",
                    defaultBaseName(project, format),
                    format.runtimeSuffix(),
                    fileName -> EditorFileNames.normalizeFileName(fileName, format.runtimeSuffix()),
                    fileName -> com.viscript.npc.util.NpcAiPublication.request("npc", fileName, project.serializeRuntimeFile(Platform.getFrozenRegistry()), null)
            );
        }
        if (getCurrentProject() instanceof NpcTestSceneProject project) {
            EditorFileFormat format = NpcTestSceneProject.FORMAT;
            return new NpcUploadAction(
                    "viscript_lib.editor.menu.upload_runtime_file",
                    "viscript_lib.editor.dialog.upload_runtime_file",
                    testSceneBaseName(),
                    format.runtimeSuffix(),
                    fileName -> EditorFileNames.normalizeFileName(fileName, format.runtimeSuffix()),
                    fileName -> withTestSceneSnapshot(() -> NpcAiPublication.request("test_scene", fileName,
                            project.serializeRuntimeFile(Platform.getFrozenRegistry()), null))
            );
        }
        return super.createUploadRuntimeAction();
    }

    @Override
    @Nullable
    protected EditorUploadAction createUploadProjectAndRuntimeAction() {
        if (getCurrentProject() instanceof NpcTestSceneProject) return null;
        if (getCurrentProject() instanceof com.viscript.npc.gui.ai.NpcActionProject action) return actionUpload(action, true);
        if (getCurrentProject() instanceof NPCProject project) {
            EditorFileFormat format = NPCProject.FORMAT;
            return new NpcUploadAction(
                    "viscript_lib.editor.menu.upload_project_and_runtime_file",
                    "viscript_lib.editor.dialog.upload_project_and_runtime_file",
                    defaultBaseName(project, format),
                    "",
                    fileName -> EditorFileNames.normalizeBaseName(fileName, format.projectSuffix(), format.runtimeSuffix()),
                    fileName -> {
                        com.viscript.npc.util.NpcAiPublication.request("npc", fileName,
                                project.serializeRuntimeFile(Platform.getFrozenRegistry()), project.serializeNBT(Platform.getFrozenRegistry()));
                    }
            );
        }
        return super.createUploadProjectAndRuntimeAction();
    }

    private EditorUploadAction actionUpload(com.viscript.npc.gui.ai.NpcActionProject project, boolean includeProject) {
        var format = project.FORMAT;
        String base = getCurrentProjectFile() == null ? "action" : EditorFileNames.normalizeBaseName(
                getCurrentProjectFile().getName(), format.projectSuffix(), format.runtimeSuffix());
        return new NpcUploadAction(
                includeProject ? "viscript_lib.editor.menu.upload_project_and_runtime_file" : "viscript_lib.editor.menu.upload_runtime_file",
                includeProject ? "viscript_lib.editor.dialog.upload_project_and_runtime_file" : "viscript_lib.editor.dialog.upload_runtime_file",
                base, includeProject ? "" : format.runtimeSuffix(), name -> EditorFileNames.normalizeBaseName(name, format.projectSuffix(), format.runtimeSuffix()),
                name -> com.viscript.npc.util.NpcAiPublication.request("action", name, project.serializeRuntimeFile(Platform.getFrozenRegistry()),
                        includeProject ? project.serializeNBT(Platform.getFrozenRegistry()) : null));
    }

    private String defaultBaseName(NPCProject project, EditorFileFormat format) {
        File projectFile = getCurrentProjectFile();
        if (projectFile != null) {
            return EditorFileNames.normalizeBaseName(projectFile.getName(), format.projectSuffix(), format.runtimeSuffix());
        }
        return EditorFileNames.normalizeBaseName(project.getCurrentNpcType());
    }

    @Override
    protected void loadNewProject(IProject project, @Nullable File projectFile) {
        if (project instanceof com.viscript.npc.gui.ai.NpcActionProject action) {
            clearNpcEditorPages();
            clearNpcTestSceneView();
            super.loadNewProject(project, projectFile);
            var view = new com.viscript.npc.gui.ai.AiEditorView(this, "action", action.graph, action.program);
            action.graphSnapshot = view::serializeGraph;
            action.compiler = view::compileProgram;
            applyBasicSlotView(centerWindow.getLeftTop(), view);
            applyBasicSlotView(rightWindow.getRightTop(), view.aiView.parametersView);
            applyBasicSlotView(bottomWindow.getLeftBottom(), null);
            applyBasicSlotView(leftWindow.getLeftTop(), null);
            setLeftWindowVisible(false);
            setRightWindowVisible(true);
            setBottomWindowVisible(false);
            refreshProjectTabs(true);
        } else if (project instanceof NPCProject) {
            clearNpcTestSceneView();
            untitledProjectIndex = projectFile == null ? NEXT_UNTITLED_PROJECT_INDEX++ : 0;
            super.loadNewProject(project, projectFile);
            loadNpcEditorPages();
            refreshProjectTabs(true);
        } else if (project instanceof NpcTestSceneProject testSceneProject) {
            if (requestNewTestArena(testSceneProject)) {
                return;
            }
            clearNpcEditorPages();
            clearNpcTestSceneView();
            untitledProjectIndex = projectFile == null ? NEXT_UNTITLED_PROJECT_INDEX++ : 0;
            super.loadNewProject(project, projectFile);
            npcTestSceneView = new NpcAiTestSceneView(testSceneProject);
            applyBasicSlotView(centerWindow.getLeftTop(), npcTestSceneView);
            applyBasicSlotView(rightWindow.getRightTop(), npcTestSceneView.getInspectorView());
            applyBasicSlotView(bottomWindow.getLeftBottom(), npcTestSceneView.getToolboxView());
            applyBasicSlotView(leftWindow.getLeftTop(), null);
            setLeftWindowVisible(false);
            setRightWindowVisible(true);
            setBottomWindowVisible(true);
            refreshProjectTabs(true);
        }
    }

    private static boolean requestNewTestArena(NpcTestSceneProject project) {
        if (project.hasRuntimeArenaCenter()
                || !NpcAiTestEnvironment.isTestLevel(Minecraft.getInstance().level)) {
            return false;
        }
        RPCPacketDistributor.rpcToServer(
                C2SPayload.CREATE_NPC_AI_TEST_ARENA,
                project.serializeNBT(Platform.getFrozenRegistry()));
        return true;
    }

    @Override
    protected void closeCurrentProject() {
        super.closeCurrentProject();
        clearNpcTestSceneView();
        npcInspectorView.clear();
        clearNpcEditorPages();
        refreshProjectTabs(true);
    }

    @Override
    public Component getTitle() {
        if (getCurrentProject() instanceof com.viscript.npc.gui.ai.NpcActionProject) {
            var format = com.viscript.npc.gui.ai.NpcActionProject.FORMAT;
            return getCurrentProjectFile() == null ? Component.translatable("editor.project.npc_action.add")
                    : Component.literal(EditorFileNames.normalizeBaseName(getCurrentProjectFile().getName(), format.projectSuffix(), format.runtimeSuffix()));
        }
        if (getCurrentProject() instanceof NPCProject project) {
            return Component.literal(projectTabTitle(project));
        }
        if (getCurrentProject() instanceof NpcTestSceneProject) {
            return Component.literal(testSceneTabTitle());
        }
        return super.getTitle();
    }

    private String testSceneTabTitle() {
        String baseName = testSceneBaseName();
        EditorWindow window = getWindow();
        if (getCurrentProjectFile() == null && untitledProjectIndex > 0
                && window != null && window.hasMultipleEditors()) {
            return baseName + " #" + untitledProjectIndex;
        }
        return baseName;
    }

    private String testSceneBaseName() {
        File projectFile = getCurrentProjectFile();
        if (projectFile != null) {
            return EditorFileNames.normalizeBaseName(projectFile.getName(),
                    NpcTestSceneProject.FORMAT.projectSuffix(), NpcTestSceneProject.FORMAT.runtimeSuffix());
        }
        return Component.translatable("editor.project.npc_test_scene.add").getString();
    }

    @Override
    public void screenTick() {
        super.screenTick();
        if (sceneSnapshotRequest != null && net.minecraft.Util.getMillis() > sceneSnapshotDeadline) {
            clearSceneSnapshotRequest();
            showSceneSnapshotFailure();
        }
        refreshProjectTabs(false);
    }

    public void selectNpcEditorPage(INpcEditorPage page) {
        if (selectedNpcEditorPage == page) {
            return;
        }
        selectedNpcEditorPage = page;
        refreshNpcEditorPageButtons();
        if (getCurrentProject() instanceof NPCProject npcProject) {
            page.onSelected(this, npcProject);
        }
    }

    @Override
    public NpcInspectorView getInspectorView() {
        return npcInspectorView;
    }

    public NPCPreviewView getPreviewView() {
        if (npcPreviewView == null) {
            npcPreviewView = new NPCPreviewView(this);
        }
        return npcPreviewView;
    }

    public NpcEditorContentView createPageContentView(INpcEditorPage page, NPCProject project) {
        return new NpcEditorContentView(page.createContent(this, project));
    }

    public <T> T getPageState(INpcEditorPage page, Class<T> type, java.util.function.Supplier<T> factory) {
        return type.cast(npcEditorPageStates.computeIfAbsent(pageStateKey(page, type), key -> factory.get()));
    }

    public void applyPageLayout(INpcEditorPage page, NPCProject project) {
        var leftView = npcEditorPageLeftViews.computeIfAbsent(pageKey(page), key -> page.createLeftView(this, project));
        var centerView = npcEditorPageCenterViews.computeIfAbsent(pageKey(page), key -> page.createCenterView(this, project));
        var rightView = npcEditorPageRightViews.computeIfAbsent(pageKey(page), key -> page.createRightView(this, project));
        var bottomView = npcEditorPageBottomViews.computeIfAbsent(pageKey(page), key -> page.createBottomView(this, project));

        applySlotView(leftWindow.getLeftTop(), leftView, project, page);
        applySlotView(centerWindow.getLeftTop(), centerView, project, page);
        applySlotView(rightWindow.getRightTop(), rightView, project, page);
        applySlotView(bottomWindow.getLeftBottom(), bottomView, project, page);

        setLeftWindowVisible(leftView != null);
        setRightWindowVisible(rightView != null);
        setBottomWindowVisible(bottomView != null);
    }

    private void loadNpcEditorPages() {
        clearNpcEditorPages();
        ArrayList<INpcEditorPage> pages = new ArrayList<>();
        for (var holder : ViScriptNpcRegistries.NPC_EDITOR_PAGE) {
            pages.add(holder.value().get());
        }
        pages.sort(Comparator.comparingInt(INpcEditorPage::order).reversed());
        pages.forEach(page -> {
            npcEditorPages.put(pageKey(page), page);
            Tab tab = createNpcEditorPageTab(page);
            npcEditorPageTabs.put(pageKey(page), tab);
            npcEditorPageBar.addChild(tab);
        });
        npcEditorPageBar.setDisplay(!npcEditorPages.isEmpty());
        if (selectedNpcEditorPage == null && !npcEditorPages.isEmpty()) {
            selectNpcEditorPage(npcEditorPages.values().iterator().next());
        }
    }

    private void clearNpcEditorPages() {
        selectedNpcEditorPage = null;
        clearSlotViews(npcEditorPageLeftViews);
        clearSlotViews(npcEditorPageCenterViews);
        clearSlotViews(npcEditorPageRightViews);
        clearSlotViews(npcEditorPageBottomViews);
        clearNpcPreviewView();
        setLeftWindowVisible(false);
        setRightWindowVisible(true);
        setBottomWindowVisible(true);
        npcEditorPages.clear();
        npcEditorPageTabs.clear();
        npcEditorPageLeftViews.clear();
        npcEditorPageCenterViews.clear();
        npcEditorPageRightViews.clear();
        npcEditorPageBottomViews.clear();
        npcEditorPageStates.clear();
        npcEditorPageBar.clearAllChildren();
        npcEditorPageBar.setDisplay(false);
    }

    private void initNpcEditorPageBar() {
        npcEditorPageBar
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(22);
                    layout.paddingAll(3);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(2);
                })
                .addClasses("__npc-editor-page-bar__", "__editor_top__");
        npcEditorPageBar.setDisplay(false);
        addChildAt(npcEditorPageBar, 1);
    }

    private void refreshProjectTabs(boolean force) {
        EditorWindow window = getWindow();
        if (window == null) {
            return;
        }
        int count = window.getEditors().size();
        boolean hasMultipleProjects = count > 1;
        boolean offsetPageBar = hasMultipleProjects && npcEditorPageBar.isDisplayed();
        float availableWidth = window.editorButtonContainer.getContentWidth();
        npcEditorPageBar.layout(layout -> layout.marginTop(offsetPageBar ? 14.0f : 0.0f));
        mainView.layout(layout -> layout.marginTop(hasMultipleProjects && !offsetPageBar ? 14.0f : 0.0f));

        if (!force && lastProjectTabCount == count && lastProjectPageBarOffset == offsetPageBar
                && Math.abs(lastProjectTabAvailableWidth - availableWidth) < 1.0f) {
            return;
        }
        lastProjectTabCount = count;
        lastProjectTabAvailableWidth = availableWidth;
        lastProjectPageBarOffset = offsetPageBar;

        float tabWidth = projectTabWidth(availableWidth, count);
        for (UIElement button : window.getEditors().values()) {
            button.layout(layout -> {
                layout.heightPercent(100);
                if (hasMultipleProjects) {
                    layout.width(tabWidth);
                    layout.minWidth(PROJECT_TAB_MIN_WIDTH);
                    layout.maxWidth(tabWidth);
                    layout.flex(0);
                    layout.flexGrow(0);
                    layout.flexShrink(1);
                    layout.flexBasis(tabWidth);
                } else {
                    layout.widthAuto();
                    layout.minWidthAuto();
                    layout.maxWidthAuto();
                    layout.flex(1);
                    layout.flexBasisAuto();
                }
            });
        }
    }

    private float projectTabWidth(float availableWidth, int count) {
        if (count <= 0) {
            return PROJECT_TAB_MAX_WIDTH;
        }
        if (availableWidth <= 0.0f) {
            return 160.0f;
        }
        float widthPerTab = (availableWidth - Math.max(0, count - 1)) / count;
        return Mth.clamp(widthPerTab, PROJECT_TAB_MIN_WIDTH, PROJECT_TAB_MAX_WIDTH);
    }

    private String projectTabTitle(NPCProject project) {
        File projectFile = getCurrentProjectFile();
        if (projectFile != null) {
            return EditorFileNames.normalizeBaseName(projectFile.getName(), NPCProject.FORMAT.projectSuffix(), NPCProject.FORMAT.runtimeSuffix());
        }
        String baseName = npcProjectBaseName(project);
        EditorWindow window = getWindow();
        if (untitledProjectIndex > 0 && window != null && window.hasMultipleEditors()) {
            return baseName + " #" + untitledProjectIndex;
        }
        return baseName;
    }

    private String npcProjectBaseName(NPCProject project) {
        String npcType = EditorFileNames.normalizeBaseName(project.getCurrentNpcType());
        if (!npcType.isBlank()) {
            return npcType;
        }
        NpcBasicsSetting basics = project.npc.getNpcData(NpcBasicsSetting.class);
        if (basics != null && !basics.getCustomName().isBlank()) {
            return basics.getCustomName();
        }
        return Component.translatable("editor.project.npc.add").getString();
    }

    private Tab createNpcEditorPageTab(INpcEditorPage page) {
        Tab tab = new Tab().setText(page.displayName());
        tab.setId("npc_editor_page_tab_" + page.pageId().getPath().replace('/', '_'));
        tab.getLayout().paddingHorizontal(5);
        tab.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0) {
                selectNpcEditorPage(page);
            }
        });
        IGuiTexture icon = page.icon();
        if (icon != IGuiTexture.EMPTY && icon != null) {
            tab.getLayout().gapAll(2);
            tab.addChildAt(new UIElement().layout(layout -> {
                layout.heightPercent(100);
                layout.setAspectRatio(1f);
            }).style(style -> style.backgroundTexture(icon)), 0);
        }
        return tab;
    }

    private void setLeftWindowVisible(boolean visible) {
        setWindowVisible(leftWindow, visible);
    }

    private void setRightWindowVisible(boolean visible) {
        setWindowVisible(rightWindow, visible);
    }

    private void setBottomWindowVisible(boolean visible) {
        setWindowVisible(bottomWindow, visible);
        var parent = bottomWindow.getParentWindow();
        if (parent != null && parent.getSplitView() != null) {
            var split = parent.getSplitView();
            var slot = parent.getFirst() == bottomWindow ? split.first : split.second;
            // Allow the scene slot to shrink below its content size instead of overflowing the window.
            if (split instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView.Vertical)
                split.first.layout(layout -> layout.minHeight(0).flexShrink(1));
            // Keep a toolbar and its wrapped status visible at larger editor/GUI scales.
            slot.layout(layout -> layout.minHeight(visible && getCurrentProject() instanceof NpcTestSceneProject ? 80 : 0));
        }
    }

    private void setWindowVisible(com.lowdragmc.lowdraglib2.editor.ui.SplittableWindow window, boolean visible) {
        var parent = window.getParentWindow();
        if (parent != null && parent.getSplitView() != null) {
            var splitView = parent.getSplitView();
            if (parent.getFirst() == window) {
                splitView.first.setDisplay(visible);
                return;
            }
            if (parent.getSecond() == window) {
                splitView.second.setDisplay(visible);
                // SplitView.first keeps an explicit percentage even when its sibling is hidden.
                // Use the same temporary layout override as LDLib2's maximize/restore mechanism.
                var property = splitView instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView.Vertical
                        ? com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.HEIGHT
                        : com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.WIDTH;
                if (visible) {
                    splitView.first.getStyleBag().removeCandidates(property,
                            candidate -> candidate.origin() == com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin.IMPORTANT);
                } else {
                    splitView.first.layout(layout -> com.lowdragmc.lowdraglib2.gui.ui.Style.importantPipeline(layout, style -> {
                        if (splitView instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView.Vertical) style.heightPercent(100);
                        else style.widthPercent(100);
                    }));
                }
                return;
            }
        }
        window.setDisplay(visible);
    }

    private void clearNpcPreviewView() {
        if (npcPreviewView == null) {
            return;
        }
        npcPreviewView.clearScene();
        if (npcPreviewView.hasParent()) {
            npcPreviewView.removeSelf();
        }
        npcPreviewView = null;
    }

    private void clearSlotViews(Map<String, View> views) {
        for (View view : views.values()) {
            if (view instanceof NPCPreviewView previewView) {
                previewView.clearScene();
            }
            if (view instanceof NpcInspectorView inspectorView) {
                inspectorView.clear();
            }
            if (view.hasParent()) {
                view.removeSelf();
            }
            viewFallbacks.remove(view);
        }
    }

    public void reloadNpcEditorPages() {
        if (getCurrentProject() instanceof NPCProject) loadNpcEditorPages();
    }

    private void clearNpcTestSceneView() {
        if (npcTestSceneView == null) return;
        var floatingWindow = getFloatingViews().windowOf(npcTestSceneView);
        if (floatingWindow != null) getFloatingViews().closeWindow(floatingWindow);
        View inspectorView = npcTestSceneView.getInspectorView();
        if (inspectorView.hasParent()) inspectorView.removeSelf();
        viewFallbacks.remove(inspectorView);
        View toolboxView = npcTestSceneView.getToolboxView();
        if (toolboxView.hasParent()) toolboxView.removeSelf();
        viewFallbacks.remove(toolboxView);
        if (npcTestSceneView.hasParent()) npcTestSceneView.removeSelf();
        viewFallbacks.remove(npcTestSceneView);
        npcTestSceneView = null;
    }

    /**
     * 在独立系统窗口中显示本工程的测试场景。
     *
     * @return 场景存在且系统窗口已打开时返回 {@code true}
     */
    public boolean openTestSceneWindow() {
        return npcTestSceneView != null && npcTestSceneView.openWindow(this);
    }

    private void applyBasicSlotView(ViewContainer container, @Nullable View targetView) {
        for (View existing : new ArrayList<>(container.getAllViews())) {
            if (existing != targetView) {
                existing.removeSelf();
                viewFallbacks.remove(existing);
            }
        }
        if (targetView == null) return;
        if (targetView.getViewContainer() != container) {
            if (targetView.getViewContainer() == null) placeView(targetView, () -> container);
            else container.addView(targetView);
        }
        targetView.setDisplay(true);
        container.selectView(targetView);
    }

    private void applySlotView(ViewContainer container, @Nullable View targetView, NPCProject project, INpcEditorPage page) {
        for (var existing : new ArrayList<>(container.getAllViews())) {
            if (existing != targetView) {
                existing.removeSelf();
                viewFallbacks.remove(existing);
            }
        }

        if (targetView == null) {
            return;
        }

        if (targetView.getViewContainer() != container) {
            if (targetView.getViewContainer() == null) {
                placeView(targetView, () -> container);
            } else {
                container.addView(targetView);
            }
        }
        targetView.setDisplay(true);
        container.selectView(targetView);
        if (targetView instanceof INpcEditorSlotView slotView) {
            slotView.onViewSelected(this, project, page);
        }
    }

    private void refreshNpcEditorPageButtons() {
        for (var entry : npcEditorPageTabs.entrySet()) {
            boolean selected = selectedNpcEditorPage != null && entry.getKey().equals(pageKey(selectedNpcEditorPage));
            entry.getValue().setSelected(selected);
        }
    }

    private String pageKey(INpcEditorPage page) {
        return page.pageId().toString();
    }

    private String pageStateKey(INpcEditorPage page, Class<?> type) {
        return pageKey(page) + "#" + type.getName();
    }

    private void detachDefaultInspectorView() {
        if (inspectorView.hasParent()) {
            inspectorView.removeSelf();
        }
        viewFallbacks.remove(inspectorView);
    }

    private record NpcUploadAction(
            String displayKey,
            String dialogTitleKey,
            String defaultFileName,
            String suffix,
            FileNameNormalizer normalizer,
            UploadHandler uploadHandler
    ) implements EditorUploadAction {
        @Override
        public Component getDisplayName() {
            return Component.translatable(displayKey);
        }

        @Override
        public String getDialogTitleKey() {
            return dialogTitleKey;
        }

        @Override
        public String getDefaultFileName() {
            return defaultFileName;
        }

        @Override
        public String getSuffix() {
            return suffix;
        }

        @Override
        public String normalizeFileName(String fileName) {
            return normalizer.normalize(fileName);
        }

        @Override
        public void uploadToServer(String fileName) throws Exception {
            uploadHandler.upload(fileName);
        }
    }

    @FunctionalInterface
    private interface FileNameNormalizer {
        String normalize(String fileName);
    }

    @FunctionalInterface
    private interface UploadHandler {
        void upload(String fileName) throws Exception;
    }
}
