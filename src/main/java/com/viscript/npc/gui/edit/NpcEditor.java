package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.ViScriptNpcRegistries;
import com.viscript.npc.gui.edit.view.NPCPreviewView;
import com.viscript.npc.gui.edit.view.INpcEditorSlotView;
import com.viscript.npc.gui.edit.view.NpcEditorContentView;
import com.viscript.npc.gui.edit.view.NpcInspectorView;
import com.viscript.npc.gui.edit.view.NpcListView;
import com.viscript.npc.gui.edit.page.INpcEditorPage;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import com.viscript_lib.gui.editor.EditorFileFormat;
import com.viscript_lib.gui.editor.EditorFileNames;
import com.viscript_lib.gui.editor.EditorServerUploads;
import com.viscript_lib.gui.editor.EditorUploadAction;
import com.viscript_lib.gui.editor.ProjectFileEditor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class NpcEditor extends ProjectFileEditor {
    public static final ResourceLocation EDITOR_ID = ViScriptNpc.id("editor");
    public final static SpriteTexture ICON = SpriteTexture.of(ViScriptNpc.formattedMod("textures/icon.png"));

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

    public NpcEditor() {
        registerProjectFileType(NPCProject.PROVIDER);
        this.icon.style(style -> style.backgroundTexture(ICON));
        detachDefaultInspectorView();
        initNpcEditorPageBar();
    }

    @Override
    protected NpcEditor createNewEditorInstance() {
        return new NpcEditor();
    }

    @Override
    protected EditorUploadAction createUploadProjectAction() {
        if (getCurrentProject() instanceof NPCProject project) {
            EditorFileFormat format = NPCProject.FORMAT;
            return new NpcUploadAction(
                    "viscript_lib.editor.menu.upload_project_file",
                    "viscript_lib.editor.dialog.upload_project_file",
                    defaultBaseName(project, format),
                    format.projectSuffix(),
                    fileName -> EditorFileNames.normalizeFileName(fileName, format.projectSuffix()),
                    fileName -> EditorServerUploads.uploadProjectToServer(format, fileName, project.serializeNBT(Platform.getFrozenRegistry()))
            );
        }
        return null;
    }

    @Override
    protected EditorUploadAction createUploadRuntimeAction() {
        if (getCurrentProject() instanceof NPCProject project) {
            EditorFileFormat format = NPCProject.FORMAT;
            return new NpcUploadAction(
                    "viscript_lib.editor.menu.upload_runtime_file",
                    "viscript_lib.editor.dialog.upload_runtime_file",
                    defaultBaseName(project, format),
                    format.runtimeSuffix(),
                    fileName -> EditorFileNames.normalizeFileName(fileName, format.runtimeSuffix()),
                    fileName -> EditorServerUploads.uploadToServer(format, fileName, project.serializeRuntimeFile(Platform.getFrozenRegistry()))
            );
        }
        return null;
    }

    @Override
    protected EditorUploadAction createUploadProjectAndRuntimeAction() {
        if (getCurrentProject() instanceof NPCProject project) {
            EditorFileFormat format = NPCProject.FORMAT;
            return new NpcUploadAction(
                    "viscript_lib.editor.menu.upload_project_and_runtime_file",
                    "viscript_lib.editor.dialog.upload_project_and_runtime_file",
                    defaultBaseName(project, format),
                    "",
                    fileName -> EditorFileNames.normalizeBaseName(fileName, format.projectSuffix(), format.runtimeSuffix()),
                    fileName -> {
                        EditorServerUploads.uploadProjectToServer(format, fileName, project.serializeNBT(Platform.getFrozenRegistry()));
                        EditorServerUploads.uploadToServer(format, fileName, project.serializeRuntimeFile(Platform.getFrozenRegistry()));
                    }
            );
        }
        return null;
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
        if (project instanceof NPCProject npcProject) {
            super.loadNewProject(project, projectFile);
            ViScriptNpcClientUtil.cacheNpcProject = npcProject;
            loadNpcEditorPages();
        }
    }

    @Override
    protected void closeCurrentProject() {
        super.closeCurrentProject();
        npcInspectorView.clear();
        clearNpcEditorPages();
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

    public NpcListView getNpcListView() {
        return npcListView;
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

    private Tab createNpcEditorPageTab(INpcEditorPage page) {
        Tab tab = new Tab().setText(page.displayName());
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

    private void applySlotView(ViewContainer container, @Nullable View targetView, NPCProject project, INpcEditorPage page) {
        for (var existing : new ArrayList<>(container.getAllViews())) {
            if (existing == historyView) {
                if (existing != targetView) {
                    existing.setDisplay(false);
                }
                continue;
            }
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
