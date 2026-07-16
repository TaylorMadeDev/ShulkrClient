package dev.triton.ui.client.modern;

import com.mojang.blaze3d.platform.InputConstants;
import dev.triton.ui.TritonUI;
import dev.triton.ui.client.TritonUIClient;
import dev.triton.ui.client.app.FluxusAppState;
import dev.triton.ui.client.app.FluxusAppState.FolderSummary;
import dev.triton.ui.client.app.FluxusAppState.LibraryScriptItem;
import dev.triton.ui.client.app.FluxusAppState.ModuleItem;
import dev.triton.ui.client.app.FluxusAppState.ScriptSummary;
import dev.triton.ui.client.app.FluxusAppState.TemplateItem;
import dev.triton.ui.client.config.FluxusConfig;
import dev.triton.ui.client.module.ModuleManager;
import icyllis.modernui.R;
import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ImageStore;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.Layout;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.text.style.BackgroundColorSpan;
import icyllis.modernui.text.style.CharacterStyle;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.CheckBox;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.Switch;
import icyllis.modernui.widget.TextView;
import net.minescript.common.EntityExporter;
import net.minescript.common.Minescript;
import net.minescript.common.blocks.BlockPositionReader;
import net.minescript.common.dataclasses.EntityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minescript.fabric.fluxus.ShulkrHudOverlay;

import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.io.FilenameFilter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TritonModernFragment extends Fragment {
	private enum Page {
		DASHBOARD,
		SCRIPTS,
		EDITOR,
		MODULES,
		ADDONS,
		TEMPLATES,
		WINDOWSPY,
		REMOTE,
		SETTINGS,
		OVERLAYS,
		ABOUT
	}

	private enum SettingsTab {
		CUSTOMIZATION("Customization"),
		PYTHON("Python Installation"),
		FILES("File Settings"),
		EDITOR("Editor"),
		SHORTCUTS("Shortcuts"),
		PRIVACY("Privacy"),
		ADVANCED("Advanced");

		private final String label;

		SettingsTab(String label) {
			this.label = label;
		}
	}

	private static final long HOVER_IN_MS = 150L;
	private static final long HOVER_OUT_MS = 190L;
	private static final long MENU_IN_MS = 165L;
	private static final long MENU_OUT_MS = 150L;
	private static final long MODAL_IN_MS = 220L;
	private static final long TOGGLE_COMMIT_DELAY_MS = 220L;
	private static final long PRESS_IN_MS = 70L;
	private static final long PRESS_OUT_MS = 120L;
	private static final long PAGE_IN_MS = 210L;
	private static final long DOCK_INDICATOR_MS = 220L;
	private static final int SIDE_WIDTH = 330;
	private static final int TOP_BAR_HEIGHT = 56;
	private static final int SEARCH_WIDTH = 520;
	private static final int DOCK_WIDTH = 620;
	private static final int DOCK_ITEM_SIZE = 50;
	private static final int DOCK_ITEM_GAP = 8;
	private static final String[] THEME_OPTIONS = {"Frontend Nova", "Dark glass", "Deep transparent", "High contrast", "Blue dusk"};
	private static final String[] ACCENT_OPTIONS = {"Nova purple", "Shulkr purple", "Soft blue", "Electric cyan", "Emerald", "Rose"};
	private static final String[] DENSITY_OPTIONS = {"Comfortable", "Compact", "Spacious"};
	private static final String[] SIDEBAR_WIDTH_OPTIONS = {"300 px", "330 px", "360 px"};
	private static final String[] NAVIGATION_MODE_OPTIONS = {"Expanded sidebar", "Compact icon rail", "Auto-collapse", "Floating dock only"};
	private static final String[] CONTENT_WIDTH_OPTIONS = {"Centered", "Wide", "Full width"};
	private static final String[] RIGHT_PANEL_OPTIONS = {"Always visible", "Contextual", "Collapsed by default", "Hidden"};
	private static final String[] PAGE_SPACING_OPTIONS = {"Compact", "Comfortable", "Spacious"};
	private static final String[] HEADER_BEHAVIOUR_OPTIONS = {"Static", "Sticky", "Hide while scrolling"};
	private static final String SHORTCUT_OPEN_UI = "open-ui";
	private static final String SHORTCUT_OVERLAY_EDIT = "overlay-edit";
	private static final String SHORTCUT_RUN_LAST = "run-last-script";
	private int PURPLE = Color.argb(255, 163, 88, 255);
	private int PURPLE_DARK = Color.argb(255, 104, 49, 205);
	private int PURPLE_SOFT = Color.argb(150, 163, 88, 255);
	private int BACKDROP = Color.argb(182, 5, 4, 12);
	private int TEXT = Color.argb(255, 247, 244, 255);
	private int MUTED = Color.argb(230, 171, 179, 204);
	private int FAINT = Color.argb(180, 136, 145, 174);
	private int GREEN = Color.argb(255, 62, 216, 99);
	private int PANEL = Color.argb(222, 7, 11, 22);
	private int PANEL_DARK = Color.argb(238, 4, 8, 18);
	private int CARD = Color.argb(204, 10, 15, 29);
	private int CARD_HOVER = Color.argb(232, 20, 25, 43);
	private int STROKE = Color.argb(94, 82, 92, 124);
	private int STROKE_HOVER = Color.argb(150, 155, 95, 255);
	private Page page = Page.DASHBOARD;
	private FrameLayout shell;
	private TextView lintSummary;
	private LinearLayout lintList;
	private LinearLayout consoleLogList;
	private TextView completionHint;
	private TextView completionGhost;
	private TextView editorLineNumbers;
	private EditText codeEditor;
	private View currentPageFrame;
	private View currentFloatingDropdown;
	private ImageView currentDropdownArrow;
	private View currentHeader;
	private Page lastAnimatedPage;
	private String editorDraft;
	private Path scriptDir;
	private Path minescriptConfigFile;
	private Path selectedScript;
	private Path selectedFolder;
	private Path contextScript;
	private Path contextEditorItem;
	private Path publishSourceScript;
	private Path renamingPath;
	private Path lastClickedEditorItem;
	private final List<Path> editorScripts = new ArrayList<>();
	private final List<Path> editorFolders = new ArrayList<>();
	private final List<Path> openEditorTabs = new ArrayList<>();
	private final List<String> editorLogs = new ArrayList<>();
	private final Map<Path, String> editorDrafts = new HashMap<>();
	private final Map<Path, String> editorSavedContents = new HashMap<>();
	private final Map<Path, List<String>> editorFunctionCache = new HashMap<>();
	private final Map<String, Boolean> filterSwitchStates = new HashMap<>();
	private List<String> localCompletionCache = List.of();
	private final Set<Path> dirtyScripts = new HashSet<>();
	private final Set<Path> selectedEditorItems = new LinkedHashSet<>();
	private final Set<Path> collapsedEditorFolders = new HashSet<>();
	private FluxusConfig settingsConfig;
	private SettingsTab settingsTab = SettingsTab.CUSTOMIZATION;
	private String settingsMessage = "Settings synced locally.";
	private String detectedPython = "";
	private String completionRemainder = "";
	private String openDropdownKey = "";
	private boolean dropdownClosing;
	private boolean strongerPanelTransparency = true;
	private boolean animatedHoverHighlight = true;
	private boolean rightPanelExpanded;
	private boolean headerHidden;
	private boolean createBackupsBeforeRun = true;
	private boolean hidePlayerNamesInCaptures;
	private String capturingShortcutAction = "";
	private String windowSpyTab = "Live";
	private String scriptFilter = "All";
	private String selectedLibraryScriptId = "";
	private String moduleFilter = "All";
	private String moduleSearchQuery = "";
	private String selectedModuleId = "";
	private String selectedShulkrAddonId = "core-runtime";
	private String overlayFilter = "HUD";
	private String selectedOverlayWidget = "Target HUD";
	private String overlayMessage = "Overlay workspace ready.";
	private boolean overlayRendererActive = true;
	private boolean overlayEditMode;
	private final Set<String> visibleOverlayWidgets = new HashSet<>(Set.of("Target HUD", "Coordinates", "Script Status"));
	private String publishName = "";
	private String publishAuthor = "EnderUser";
	private String publishAbout = "";
	private String publishTags = "Python, Utility";
	private String publishIcon = "code-solid.png";
	private String publishFileName = "";
	private String templateFilter = "All";
	private String selectedTemplateId = "autofarm-starter";
	private int dropdownAnchorX;
	private int dropdownAnchorY;
	private int dropdownAnchorWidth = 220;
	private String consoleTab = "Console";
	private String renamingDraft = "";
	private boolean stylingEditorText;
	private boolean editorDirty;
	private boolean creatingScript;
	private long lastCreateScriptAt;
	private String lastCreateScriptKey = "";
	private boolean currentScriptRunning;
	private boolean localCompletionDirty = true;
	private long lastEditorClickAt;
	private long lastEditorTextClickAt;
	private final ScheduledExecutorService lintExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Shulkr Ruff Linter");
		thread.setDaemon(true);
		return thread;
	});
	private ScheduledFuture<?> ruffLintTask;
	private ScheduledFuture<?> editorAnalysisTask;
	private int lintGeneration;
	private int editorLineCount;
	private final String initialPage;
	private static final String[][] SHULKR_ADDONS = {
			{"core-runtime", "Core Runtime", "The event bridge, lifecycle, and shared services used by every Shulkr addon.", "v0.1", "Bundled", "box-solid.png", "purple"},
			{"overlay-engine", "Overlay Engine", "HUD widgets, edit mode, snapping, themes, and resolution-safe positioning.", "v0.1", "Bundled", "border-none-solid.png", "blue"},
			{"editor-tools", "Editor Tools", "Script diagnostics, completion, templates, console output, and run controls.", "v0.1", "Bundled", "code-solid.png", "green"},
			{"window-spy", "WindowSpy", "Live block, entity, NBT, and world inspection tools for addon developers.", "v0.1", "Bundled", "window-restore-regular.png", "cyan"},
			{"developer-api", "Developer API", "The extension surface for installing third-party Shulkr modules.", "Preview", "In development", "puzzle-piece-solid.png", "yellow"},
			{"web-dashboard", "Web Dashboard", "Remotely control scripts, overlays, and the connected client from your browser.", "v0.1", "Available", "window-restore-regular.png", "purple"}
	};

	public TritonModernFragment() {
		this(null);
	}

	public TritonModernFragment(String initialPage) {
		this.initialPage = initialPage;
	}

	private final String[][] spyEntities = {
			{"Zombie", "Hostile", "8.4m", "HP 18/20", "broom-solid.png", "red"},
			{"Villager", "Passive", "12.1m", "Trades 7", "user-solid.png", "green"},
			{"Item Frame", "Block entity", "3.2m", "Map item", "border-all-solid.png", "blue"},
			{"Creeper", "Hostile", "17.8m", "Fuse idle", "droplet-solid.png", "red"},
			{"Boat", "Vehicle", "22.0m", "Empty", "box-open-solid.png", "purple"}
	};

	private final String[][] spyNbtRows = {
			{"id", "minecraft:chest"},
			{"Lock", "\"\""},
			{"LootTable", "minecraft:chests/simple_dungeon"},
			{"CustomName", "{\"text\":\"Raid Cache\"}"},
			{"Items[0]", "diamond_sword x1"},
			{"Items[1]", "golden_apple x3"}
	};

	private final String[][] spySignals = {
			{"Tick", "player.position changed", "12ms ago", "green"},
			{"Raycast", "target block selected", "54ms ago", "purple"},
			{"Entity", "Zombie entered 10m radius", "1.2s ago", "red"},
			{"NBT", "Chest inventory snapshot", "2.8s ago", "blue"}
	};

	private record SpyTarget(String title, String meta, String details, String rawNbt, String kind,
			String position, String face, String state, String distance, boolean blockEntity) {
	}

	private record SpySnapshot(SpyTarget target, List<String[]> entities, List<String[]> nbtRows,
			List<String[]> hooks, List<String[]> watchRows, List<String[]> flags, List<String[]> signals,
			String dimension, String light, String entityCount, String scriptSnippet) {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
		shell = new FrameLayout(requireContext());
		shell.setBackground(round(BACKDROP, 0, 0));
		TritonUIClient.setActiveModernFragment(this);
		ensureEditorScriptsReady();
		renderShell();
		return shell;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		TritonUIClient.setActiveModernFragment(null);
		if (ruffLintTask != null) {
			ruffLintTask.cancel(true);
		}
		if (editorAnalysisTask != null) {
			editorAnalysisTask.cancel(true);
		}
		lintExecutor.shutdownNow();
	}

	public boolean handleGlobalKey(int key, int action, int modifiers) {
		if (!capturingShortcutAction.isBlank() && action == 1) {
			int nextKey = (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE)
					? InputConstants.UNKNOWN.getValue()
					: key;
			TritonUIClient.setShortcutKey(capturingShortcutAction, nextKey);
			settingsConfig = TritonUIClient.config();
			String label = shortcutDisplayName(capturingShortcutAction);
			capturingShortcutAction = "";
			settingsMessage = nextKey == InputConstants.UNKNOWN.getValue()
					? label + " shortcut cleared."
					: label + " bound to " + keybindLabel(nextKey) + ".";
			renderShell();
			return true;
		}
		if (action != 1) {
			return false;
		}
		if (key == InputConstants.KEY_K && (modifiers & 2) != 0) {
			openDropdownKey = "command-palette";
			dropdownAnchorX = 30;
			dropdownAnchorY = effectiveTopBarHeight();
			dropdownAnchorWidth = 360;
			renderShell();
			return true;
		}
		if (page != Page.EDITOR || key != InputConstants.KEY_F2) {
			return false;
		}
		Path target = lastClickedEditorItem != null ? lastClickedEditorItem
				: (!selectedEditorItems.isEmpty() ? selectedEditorItems.iterator().next() : selectedScript);
		beginRename(target);
		return true;
	}

	private void renderShell() {
		currentFloatingDropdown = null;
		currentDropdownArrow = null;
		currentHeader = null;
		shell.removeAllViews();
		FrameLayout host = new FrameLayout(requireContext());
		shell.addView(host, new FrameLayout.LayoutParams(match(), match()));
		LinearLayout root = new LinearLayout(requireContext());
		root.setOrientation(LinearLayout.HORIZONTAL);
		int outerPadding = densityOuterPadding();
		root.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);
		host.addView(root, new FrameLayout.LayoutParams(match(), match()));

		boolean floatingDockOnly = settingsConfig.navigationMode().equals("Floating dock only");
		boolean compactNavigation = useCompactNavigation();
		if (!floatingDockOnly) {
			int sidebarWidth = compactNavigation ? 86 : configuredSidebarWidth();
			root.addView(sidebar(compactNavigation), new LinearLayout.LayoutParams(sidebarWidth, match()));
		}
		int contentInset = contentWidthInset();
		int leadingGap = floatingDockOnly ? contentInset : Math.max(pageGap(), contentInset);
		root.addView(activePage(), weighted(1.0F, leadingGap, 0, contentInset, floatingDockOnly ? 82 : 0));
		if (floatingDockOnly) {
			FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(DOCK_WIDTH, 70, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
			dockParams.setMargins(0, 0, 0, 14);
			host.addView(dock(true), dockParams);
		}
	}

	private View activePage() {
		if (page == Page.SCRIPTS) {
			return scriptsPage();
		}
		if (page == Page.EDITOR) {
			return editorPage();
		}
		if (page == Page.MODULES) {
			return modulesPage();
		}
		if (page == Page.ADDONS) {
			return shulkrAddonsPage();
		}
		if (page == Page.TEMPLATES) {
			return templatesPage();
		}
		if (page == Page.WINDOWSPY) {
			return windowSpyPage();
		}
		if (page == Page.REMOTE) {
			return remoteViewerPage();
		}
		if (page == Page.SETTINGS) {
			return settingsPage();
		}
		if (page == Page.OVERLAYS) {
			return overlaysPage();
		}
		if (page == Page.ABOUT) {
			return aboutPage();
		}
		return dashboardPage();
	}

	private View sidebar(boolean compact) {
		FluxusAppState.Profile profileData = currentProfile();
		boolean online = FluxusAppState.get().backendOnline();
		LinearLayout side = column(compact ? 10 : 18);
		side.setPadding(compact ? 10 : 18, 18, compact ? 10 : 16, 16);
		side.setBackground(panel(18));

		FrameLayout brand = new FrameLayout(requireContext());
		brand.addView(rawIcon("shulkr-icons.png"), centered(compact ? 62 : 220, compact ? 50 : 124));
		side.addView(brand, new LinearLayout.LayoutParams(match(), 72));

		String[][] primaryNav = {
				{"Dashboard", "house-solid.png"}, {"Scripts", "code-solid.png"}, {"Editor", "border-all-solid.png"},
				{"Libraries", "puzzle-piece-solid.png"}, {"Modules", "box-open-solid.png"}, {"Templates", "layer-group-solid.png"},
				{"WindowSpy", "window-restore-regular.png"}, {"Remote", "window-restore-regular.png"}, {"Overlays", "border-none-solid.png"}
		};
		for (String[] item : primaryNav) {
			side.addView(nav(item[0], item[1], navActive(item[0])), new LinearLayout.LayoutParams(match(), 44));
		}

		View spacer = new View(requireContext());
		side.addView(spacer, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout secondary = column(8);
		String[][] secondaryNav = {
				{"Settings", "gear-solid.png"}, {"About", "circle-info-solid.png"}
		};
		for (String[] item : secondaryNav) {
			secondary.addView(nav(item[0], item[1], navActive(item[0])), new LinearLayout.LayoutParams(match(), 44));
		}
		side.addView(secondary, new LinearLayout.LayoutParams(match(), wrap()));

		LinearLayout profile = row(14);
		profile.setPadding(compact ? 8 : 16, compact ? 10 : 14, compact ? 8 : 16, compact ? 10 : 14);
		profile.setGravity(compact ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
		profile.setBackground(glass(Color.argb(112, 10, 15, 27), Color.argb(132, 7, 12, 22), 16, STROKE));
		FrameLayout avatar = new FrameLayout(requireContext());
		avatar.setBackground(round(Color.argb(150, 28, 76, 48), 12, Color.argb(100, 96, 255, 154)));
		avatar.addView(icon("user-solid.png", GREEN), centered(24, 24));
		profile.addView(avatar, new LinearLayout.LayoutParams(compact ? 42 : 50, compact ? 42 : 50));
		LinearLayout copy = column(4);
		copy.addView(label(profileData.displayName(), 15, TEXT));
		copy.addView(label(profileData.tier(), 13, PURPLE));
		copy.addView(label("*  Minecraft " + System.getProperty("minecraft.version", "26.1.2"), 12, MUTED));
		copy.addView(label(online ? "Connected" : "Offline", 11, online ? GREEN : FAINT));
		if (!compact) {
			profile.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		}
		profile.setOnClickListener(view -> openPage(Page.SETTINGS));
		LinearLayout.LayoutParams profileLp = new LinearLayout.LayoutParams(match(), compact ? 72 : 112);
		profileLp.setMargins(0, 0, 0, 14);
		side.addView(profile, profileLp);
		return side;
	}

	private boolean navActive(String name) {
		return (page == Page.DASHBOARD && name.equals("Dashboard"))
				|| (page == Page.SCRIPTS && name.equals("Scripts"))
				|| (page == Page.EDITOR && name.equals("Editor"))
				|| (page == Page.MODULES && name.equals("Libraries"))
				|| (page == Page.ADDONS && name.equals("Modules"))
				|| (page == Page.TEMPLATES && name.equals("Templates"))
				|| (page == Page.WINDOWSPY && name.equals("WindowSpy"))
				|| (page == Page.REMOTE && name.equals("Remote"))
				|| (page == Page.SETTINGS && name.equals("Settings"))
				|| (page == Page.OVERLAYS && name.equals("Overlays"))
				|| (page == Page.ABOUT && name.equals("About"));
	}

	private void openPage(Page next) {
		if (page != next) {
			captureActiveDraft();
			page = next;
			rightPanelExpanded = false;
			headerHidden = false;
			openDropdownKey = "";
			if (next == Page.EDITOR || next == Page.SCRIPTS) {
				refreshEditorScripts();
			}
			rememberPage(next);
			renderShell();
		}
	}

	private View dashboardPage() {
		return pageFrame(dashboardCenter(), rightRail());
	}

	private View dashboardCenter() {
		refreshEditorScripts();
		LinearLayout center = column(20);

		LinearLayout welcome = row(16);
		welcome.setPadding(24, 22, 20, 20);
		welcome.setGravity(Gravity.CENTER_VERTICAL);
		welcome.setBackground(panel(16));
		LinearLayout copy = column(9);
		copy.addView(label("Welcome back, " + currentProfile().displayName(), 22, TEXT));
		copy.addView(label("Ready to automate your world.", 15, MUTED));
		welcome.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View scriptStat = stat("Scripts", String.valueOf(editorScripts.size()), "code-solid.png");
		scriptStat.setOnClickListener(view -> openPage(Page.SCRIPTS));
		welcome.addView(scriptStat);
		View moduleStat = stat("Libraries", String.valueOf(FluxusAppState.get().modules().size()), "box-solid.png");
		moduleStat.setOnClickListener(view -> openPage(Page.MODULES));
		welcome.addView(moduleStat);
		View templateStat = stat("Templates", String.valueOf(FluxusAppState.get().templates().size()), "folder-solid.png");
		templateStat.setOnClickListener(view -> openPage(Page.TEMPLATES));
		welcome.addView(templateStat);
		center.addView(welcome, new LinearLayout.LayoutParams(match(), 118));

		LinearLayout actions = column(18);
		actions.setPadding(24, 20, 24, 20);
		actions.setBackground(panel(16));
		actions.addView(label("Quick Actions", 15, TEXT));
		LinearLayout actionRow = row(18);
		View openEditor = action("Open Editor", selectedScript == null ? "Pick or create a script" : selectedScript.getFileName().toString(), "code-solid.png");
		openEditor.setOnClickListener(view -> openPage(Page.EDITOR));
		actionRow.addView(openEditor, new LinearLayout.LayoutParams(0, 72, 1.0F));
		View newScript = action("New Script", "Create a Python script", "plus-solid.png");
		newScript.setOnClickListener(view -> createNewScript());
		actionRow.addView(newScript, new LinearLayout.LayoutParams(0, 72, 1.0F));
		View browseModules = action("Shulkr Modules", "Manage client addons", "box-open-solid.png");
		browseModules.setOnClickListener(view -> openPage(Page.ADDONS));
		actionRow.addView(browseModules, new LinearLayout.LayoutParams(0, 72, 1.0F));
		actions.addView(actionRow, new LinearLayout.LayoutParams(match(), 78));
		center.addView(actions, new LinearLayout.LayoutParams(match(), 160));

		center.addView(workspaceHealthPanel(), new LinearLayout.LayoutParams(match(), 82));

		LinearLayout bottom = row(18);
		bottom.addView(recentScripts(), new LinearLayout.LayoutParams(0, match(), 1.65F));
		bottom.addView(favoriteModules(), new LinearLayout.LayoutParams(0, match(), 1.0F));
		center.addView(bottom, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 70);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		center.addView(dock, dockLp);
		return center;
	}

	private View scriptsPage() {
		return pageFrame(scriptsCenter(), filtersPanel());
	}

	private View workspaceHealthPanel() {
		LinearLayout panel = row(12);
		panel.setPadding(18, 12, 18, 12);
		panel.setGravity(Gravity.CENTER_VERTICAL);
		panel.setBackground(panel(16));
		panel.addView(healthTile("Python", pythonStatusLabel(), pythonStatusLabel().equals("Configured") ? "green" : "yellow", "code-solid.png"), new LinearLayout.LayoutParams(0, match(), 1.0F));
		panel.addView(healthTile("Backend", FluxusAppState.get().backendOnline() ? "Online" : "Offline", FluxusAppState.get().backendOnline() ? "green" : "yellow", "cloud-solid.png"), new LinearLayout.LayoutParams(0, match(), 1.0F));
		panel.addView(healthTile("Libraries", FluxusAppState.get().modules().size() + " ready", FluxusAppState.get().modules().isEmpty() ? "yellow" : "purple", "box-solid.png"), new LinearLayout.LayoutParams(0, match(), 1.0F));
		panel.addView(healthTile("Scripts", editorScripts.size() + " local", editorScripts.isEmpty() ? "yellow" : "blue", "folder-open-solid.png"), new LinearLayout.LayoutParams(0, match(), 1.0F));
		return panel;
	}

	private View healthTile(String name, String value, String tone, String iconFile) {
		LinearLayout tile = row(10);
		tile.setPadding(12, 0, 12, 0);
		tile.setGravity(Gravity.CENTER_VERTICAL);
		tile.setBackground(round(Color.argb(88, 18, 24, 39), 10, Color.argb(52, 105, 116, 150)));
		tile.addView(iconBadge(iconFile, toneColor(tone), Color.argb(72, 163, 88, 255), 40, 12), new LinearLayout.LayoutParams(40, 40));
		LinearLayout copy = column(2);
		copy.addView(label(name, 11, MUTED));
		copy.addView(label(value, 13, TEXT));
		tile.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return tile;
	}

	private View editorPage() {
		return pageFrame(editorCenter(), editorSide());
	}

	private View modulesPage() {
		return pageFrame(modulesCenter(), moduleDetailsPanel());
	}

	private View shulkrAddonsPage() {
		return pageFrame(shulkrAddonsCenter(), shulkrAddonsSide());
	}

	private View shulkrAddonsCenter() {
		LinearLayout center = column(18);
		LinearLayout panel = column(16);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));

		long bundled = java.util.Arrays.stream(SHULKR_ADDONS).filter(addon -> addon[4].equals("Bundled")).count();
		panel.addView(pageTitle("Modules", "Shulkr addons live here, separate from reusable Python libraries.", "box-open-solid.png",
				new String[][]{{"Bundled", String.valueOf(bundled), "check-double-solid.png"}, {"External", "0", "download-solid.png"}}),
				new LinearLayout.LayoutParams(match(), 74));

		LinearLayout overview = row(14);
		overview.setPadding(18, 14, 18, 14);
		overview.setGravity(Gravity.CENTER_VERTICAL);
		overview.setBackground(glass(Color.argb(116, 31, 19, 54), Color.argb(126, 9, 16, 31), 14, accentAlpha(82)));
		overview.addView(iconBadge("puzzle-piece-solid.png", PURPLE, accentAlpha(48), 50, 13), new LinearLayout.LayoutParams(50, 50));
		LinearLayout overviewCopy = column(4);
		overviewCopy.addView(label("Shulkr Addon Workspace", 17, TEXT));
		overviewCopy.addView(label("Bundled systems are visible now; installable community modules will plug into this page later.", 12, MUTED));
		overview.addView(overviewCopy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View docs = chip("Developer API", false);
		docs.setOnClickListener(view -> {
			selectedShulkrAddonId = "developer-api";
			renderShell();
		});
		overview.addView(docs, new LinearLayout.LayoutParams(112, 34));
		panel.addView(overview, new LinearLayout.LayoutParams(match(), 82));

		ScrollView scroll = layoutScrollView();
		LinearLayout grid = column(12);
		for (int index = 0; index < SHULKR_ADDONS.length; index += 2) {
			LinearLayout line = row(12);
			line.addView(shulkrAddonCard(SHULKR_ADDONS[index]), new LinearLayout.LayoutParams(0, 142, 1.0F));
			if (index + 1 < SHULKR_ADDONS.length) {
				line.addView(shulkrAddonCard(SHULKR_ADDONS[index + 1]), new LinearLayout.LayoutParams(0, 142, 1.0F));
			} else {
				line.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, 142, 1.0F));
			}
			grid.addView(line, new LinearLayout.LayoutParams(match(), 150));
		}
		scroll.addView(grid, new ScrollView.LayoutParams(match(), wrap()));
		panel.addView(scroll, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		center.addView(panel, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		addCenteredDock(center);
		return center;
	}

	private View shulkrAddonCard(String[] addon) {
		boolean selected = addon[0].equals(selectedShulkrAddonId);
		String addonStatus = shulkrAddonStatus(addon);
		LinearLayout card = column(10);
		card.setPadding(16, 14, 16, 12);
		GradientDrawable normal = selected
				? round(Color.argb(145, 38, 23, 62), 12, STROKE_HOVER)
				: round(Color.argb(112, 14, 20, 34), 12, Color.argb(68, 105, 116, 150));
		GradientDrawable hover = round(Color.argb(168, 28, 34, 54), 12, STROKE_HOVER);
		makeHover(card, normal, hover);
		addPressAnimation(card);
		card.setOnClickListener(view -> {
			selectedShulkrAddonId = addon[0];
			renderShell();
		});

		LinearLayout head = row(12);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.addView(iconBadge(addon[5], toneColor(addon[6]), Color.argb(62, 163, 88, 255), 44, 11), new LinearLayout.LayoutParams(44, 44));
		LinearLayout copy = column(4);
		copy.addView(label(addon[1], 16, TEXT));
		copy.addView(label(addon[3], 11, MUTED));
		head.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		TextView status = label(addonStatus, 10, addonStatus.equals("Bundled") || addonStatus.equals("Installed") ? GREEN : toneColor(addon[6]));
		status.setGravity(Gravity.CENTER);
		status.setBackground(round(Color.argb(72, 20, 28, 42), 8, Color.argb(54, 105, 116, 150)));
		head.addView(status, new LinearLayout.LayoutParams(addonStatus.equals("In development") ? 104 : 82, 25));
		card.addView(head, new LinearLayout.LayoutParams(match(), 48));
		card.addView(label(addon[2], 12, MUTED), new LinearLayout.LayoutParams(match(), 48));
		return card;
	}

	private View shulkrAddonsSide() {
		String[] addon = selectedShulkrAddon();
		LinearLayout rail = column(16);

		LinearLayout details = column(13);
		details.setPadding(20, 18, 20, 18);
		details.setBackground(panel(16));
		LinearLayout title = row(12);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(iconBadge(addon[5], toneColor(addon[6]), Color.argb(68, 163, 88, 255), 48, 12), new LinearLayout.LayoutParams(48, 48));
		LinearLayout titleCopy = column(3);
		titleCopy.addView(label(addon[1], 18, TEXT));
		titleCopy.addView(label("Shulkr module", 11, PURPLE));
		title.addView(titleCopy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		details.addView(title, new LinearLayout.LayoutParams(match(), 54));
		details.addView(label(addon[2], 12, MUTED));
		details.addView(infoRow("Version", addon[3]));
		details.addView(infoRow("Status", shulkrAddonStatus(addon)));
		details.addView(infoRow("Provider", "Shulkr"));
		View open = action("Open Module", addonTargetLabel(addon[0]), addon[5]);
		open.setOnClickListener(view -> openAddonTarget(addon[0]));
		details.addView(open, new LinearLayout.LayoutParams(match(), 64));
		rail.addView(details, new LinearLayout.LayoutParams(match(), 330));

		LinearLayout developer = column(12);
		developer.setPadding(20, 18, 20, 18);
		developer.setBackground(panel(16));
		LinearLayout developerTitle = row(9);
		developerTitle.setGravity(Gravity.CENTER_VERTICAL);
		developerTitle.addView(icon("code-solid.png", PURPLE), new LinearLayout.LayoutParams(18, 18));
		developerTitle.addView(label("Build for Shulkr", 16, TEXT));
		developer.addView(developerTitle);
		developer.addView(label("The addon loader and public API will appear here as they are implemented.", 12, MUTED));
		developer.addView(templateStep("1", "Create a module manifest"));
		developer.addView(templateStep("2", "Register hooks and settings"));
		developer.addView(templateStep("3", "Install it into Shulkr"));
		rail.addView(developer, new LinearLayout.LayoutParams(match(), 230));
		return rail;
	}

	private String[] selectedShulkrAddon() {
		for (String[] addon : SHULKR_ADDONS) {
			if (addon[0].equals(selectedShulkrAddonId)) return addon;
		}
		return SHULKR_ADDONS[0];
	}

	private String addonTargetLabel(String id) {
		return id.equals("overlay-engine") ? "Open Overlays"
				: id.equals("editor-tools") ? "Open Editor"
				: id.equals("window-spy") ? "Open WindowSpy"
				: id.equals("core-runtime") ? "Open Dashboard"
				: id.equals("web-dashboard") ? (webDashboardInstalled() ? "Open Web Dashboard" : "Install Module")
				: "View module status";
	}

	private void openAddonTarget(String id) {
		if (id.equals("overlay-engine")) openPage(Page.OVERLAYS);
		else if (id.equals("editor-tools")) openPage(Page.EDITOR);
		else if (id.equals("window-spy")) openPage(Page.WINDOWSPY);
		else if (id.equals("core-runtime")) openPage(Page.DASHBOARD);
		else if (id.equals("web-dashboard")) {
			FluxusAppState.ClientModuleItem module = FluxusAppState.get().clientModule("web-dashboard");
			if (module == null || !module.installed()) {
				FluxusAppState.ClientModuleItem installed = FluxusAppState.get().setClientModuleInstalled("web-dashboard", true);
				settingsMessage = installed == null ? "Web Dashboard install failed. Check the backend." : "Web Dashboard installed.";
				renderShell();
				return;
			}
			String url = module.openUrl() == null || module.openUrl().isBlank() ? "http://127.0.0.1:50991/web/" : module.openUrl();
			try {
				openExternalUrl(url);
				settingsMessage = "Opening Web Dashboard: " + url;
			} catch (Exception error) {
				copyToClipboard(url);
				settingsMessage = "Could not open Web Dashboard. Copied URL: " + url;
			}
			renderShell();
		}
	}

	private void openExternalUrl(String url) throws IOException {
		URI uri = URI.create(url);
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		if (!(scheme.equals("http") || scheme.equals("https")) || !(host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1")) || uri.getUserInfo() != null) {
			throw new IOException("Dashboard links must use a local HTTP address.");
		}
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
			return;
		}
		if (Desktop.isDesktopSupported()) {
			Desktop.getDesktop().browse(uri);
			return;
		}
		throw new IOException("No browser opener is available.");
	}

	private boolean webDashboardInstalled() {
		FluxusAppState.ClientModuleItem module = FluxusAppState.get().clientModule("web-dashboard");
		return module != null && module.installed();
	}

	private String shulkrAddonStatus(String[] addon) {
		if (!"web-dashboard".equals(addon[0])) return addon[4];
		return webDashboardInstalled() ? "Installed" : "Available";
	}

	private View templatesPage() {
		return pageFrame(templatesCenter(), templateDetailsPanel());
	}

	private View windowSpyPage() {
		return pageFrame(windowSpyCenter(), windowSpySidePanel());
	}

	private View settingsPage() {
		return pageFrame(settingsCenter(), settingsSidePanel());
	}

	private View overlaysPage() {
		syncOverlayStateFromHud();
		return pageFrame(overlaysCenter(), overlaysSidePanel());
	}

	private View aboutPage() {
		return pageFrame(aboutCenter(), aboutSidePanel());
	}

	private View scriptsCenter() {
		List<LibraryScriptItem> scripts = visiblePublishedScripts();
		LinearLayout center = column(18);

		LinearLayout panel = column(14);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));

		LinearLayout header = row(10);
		header.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout copy = column(5);
		copy.addView(label("Script Library", 22, TEXT));
		copy.addView(label("Publish, discover, and install Shulkr scripts from the community.", 13, MUTED));
		header.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		header.addView(label(FluxusAppState.get().libraryScripts().size() + " published", 13, MUTED));
		header.addView(viewToggle("border-all-solid.png", true), new LinearLayout.LayoutParams(38, 38));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 58));

		LinearLayout chips = row(8);
		for (String chipName : new String[]{"All", "Recent", "Python", "Pyjinn", "Farming", "Combat", "World", "Utility", "Other"}) {
			TextView chip = chip(chipName, chipName.equals(scriptFilter));
			chip.setOnClickListener(view -> {
				scriptFilter = chipName;
				renderShell();
			});
			chips.addView(chip, new LinearLayout.LayoutParams(chipName.equals("Farming") || chipName.equals("Utility") ? 82 : 72, 30));
		}
		panel.addView(chips, new LinearLayout.LayoutParams(match(), 34));

		ScrollView scroller = layoutScrollView();
		LinearLayout grid = column(10);
		if (scripts.isEmpty()) {
			TextView empty = label("No published scripts yet. Upload a script to publish it to the Shulkr library.", 14, MUTED);
			empty.setGravity(Gravity.CENTER);
			grid.addView(empty, new LinearLayout.LayoutParams(match(), 140));
		}
		int index = 0;
		while (index < scripts.size()) {
			LinearLayout line = row(10);
			for (int col = 0; col < 4; col++) {
				if (index < scripts.size()) {
					line.addView(scriptCard(scripts.get(index++)), new LinearLayout.LayoutParams(0, 238, 1.0F));
				} else {
					View filler = new View(requireContext());
					line.addView(filler, new LinearLayout.LayoutParams(0, 238, 1.0F));
				}
			}
			grid.addView(line, new LinearLayout.LayoutParams(match(), 246));
		}
		scroller.addView(grid, new ScrollView.LayoutParams(match(), wrap()));
		panel.addView(scroller, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		panel.addView(scriptsFooter(scripts.size()), new LinearLayout.LayoutParams(match(), 44));
		center.addView(panel, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 70);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		center.addView(dock, dockLp);
		return center;
	}

	private View modulesCenter() {
		List<ModuleItem> allModules = FluxusAppState.get().modules();
		List<ModuleItem> modules = visibleModules(allModules);
		LinearLayout center = column(18);

		LinearLayout panel = column(14);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));

		LinearLayout header = row(10);
		header.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout title = column(5);
		LinearLayout titleLine = row(10);
		titleLine.setGravity(Gravity.CENTER_VERTICAL);
		titleLine.addView(icon("puzzle-piece-solid.png", PURPLE), new LinearLayout.LayoutParams(26, 26));
		titleLine.addView(label("Libraries", 24, TEXT));
		title.addView(titleLine);
		title.addView(label("Reusable local script libraries you can import into your own scripts.", 14, MUTED));
		header.addView(title, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		header.addView(label(allModules.size() + " importable", 13, MUTED));
		header.addView(viewToggle("border-all-solid.png", true), new LinearLayout.LayoutParams(38, 38));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 66));
		EditText search = new EditText(requireContext());
		search.setSingleLine(true);
		search.setText(moduleSearchQuery);
		search.setHint("Search libraries...");
		search.setTextSize(12);
		search.setTextColor(TEXT);
		search.setHintTextColor(FAINT);
		search.setPadding(12, 0, 12, 0);
		search.setBackground(round(Color.argb(116, 10, 15, 26), 8, STROKE));
		search.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				moduleSearchQuery = s.toString();
				search.post(() -> renderShell());
			}
			public void afterTextChanged(Editable e) {}
		});
		panel.addView(search, new LinearLayout.LayoutParams(match(), 38));

		LinearLayout chips = row(8);
		String[] filters = {"All", "Installed", "Python", "Pyjinn"};
		for (String filter : filters) {
			int width = filter.equals("Installed") ? 88 : filter.equals("Python") ? 80 : 72;
			View chip = chip(filter, filter.equals(moduleFilter));
			chip.setOnClickListener(view -> {
				moduleFilter = filter;
				renderShell();
			});
			chips.addView(chip, new LinearLayout.LayoutParams(width, 34));
		}
		panel.addView(chips, new LinearLayout.LayoutParams(match(), 38));

		ScrollView scroller = layoutScrollView();
		LinearLayout grid = column(10);
		if (modules.isEmpty()) {
			TextView empty = label("No libraries match this filter yet. Mark a script as a library from the Editor to reuse it here.", 14, MUTED);
			empty.setGravity(Gravity.CENTER);
			grid.addView(empty, new LinearLayout.LayoutParams(match(), 140));
		}
		int index = 0;
		while (index < modules.size()) {
			LinearLayout line = row(10);
			for (int col = 0; col < 4; col++) {
				if (index < modules.size()) {
					line.addView(moduleCard(modules.get(index++)), new LinearLayout.LayoutParams(0, 184, 1.0F));
				} else {
					line.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, 184, 1.0F));
				}
			}
			grid.addView(line, new LinearLayout.LayoutParams(match(), 192));
		}
		scroller.addView(grid, new ScrollView.LayoutParams(match(), wrap()));
		panel.addView(scroller, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		center.addView(panel, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 70);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		center.addView(dock, dockLp);
		return center;
	}

	private View moduleCard(ModuleItem data) {
		boolean active = data.id().equals(selectedModule().id());
		LinearLayout card = column(10);
		card.setPadding(14, 12, 14, 12);
		makeHover(card, active ? round(Color.argb(126, 35, 22, 56), 10, STROKE_HOVER) : round(Color.argb(116, 15, 21, 34), 10, Color.argb(72, 105, 116, 150)),
				active ? round(Color.argb(170, 45, 28, 72), 10, STROKE_HOVER) : round(Color.argb(160, 24, 30, 48), 10, STROKE_HOVER));
		card.setOnClickListener(view -> {
			selectedModuleId = data.id();
			renderShell();
		});

		LinearLayout head = row(12);
		head.setGravity(Gravity.TOP);
		head.addView(iconBadge(data.icon(), data.installed() ? PURPLE : MUTED, Color.argb(120, 22, 18, 36), 58, 13), new LinearLayout.LayoutParams(58, 58));
		LinearLayout copy = column(5);
		copy.addView(label(data.name(), 16, TEXT));
		copy.addView(label(wrapModuleDescription(data.description()), 12, MUTED));
		head.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		card.addView(head, new LinearLayout.LayoutParams(match(), 76));

		LinearLayout meta = row(8);
		meta.setGravity(Gravity.CENTER_VERTICAL);
		meta.addView(tag(data.category(), false), new LinearLayout.LayoutParams(data.category().equalsIgnoreCase("Python") ? 80 : 84, 24));
		meta.addView(label(data.version(), 11, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		meta.addView(tag(data.status(), data.installed()), new LinearLayout.LayoutParams(data.installed() ? 78 : 88, 24));
		card.addView(meta, new LinearLayout.LayoutParams(match(), 26));
		card.addView(label(data.author(), 11, FAINT));
		return card;
	}

	private View moduleDetailsPanel() {
		ModuleItem selected = selectedModule();
		Path script = localModulePath(selected);
		String importSnippet = moduleImportSnippet(selected);
		LinearLayout side = column(0);
		side.setBackground(panel(16));

		LinearLayout summary = column(14);
		summary.setPadding(20, 18, 20, 18);
		LinearLayout hero = row(14);
		hero.setGravity(Gravity.CENTER_VERTICAL);
		hero.addView(iconBadge(selected.icon(), selected.installed() ? PURPLE : MUTED, Color.argb(120, 93, 48, 168), 68, 13));
		LinearLayout copy = column(5);
		copy.addView(label(selected.name(), 18, TEXT));
		copy.addView(label("Reusable " + selected.category() + " library", 12, MUTED));
		hero.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		summary.addView(hero, new LinearLayout.LayoutParams(match(), 72));
		LinearLayout installed = row(8);
		installed.setGravity(Gravity.CENTER_VERTICAL);
		installed.addView(tag(selected.status(), selected.installed()), new LinearLayout.LayoutParams(selected.installed() ? 92 : 104, 24));
		installed.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, 1, 1.0F));
		installed.addView(tag(selected.version(), false), new LinearLayout.LayoutParams(82, 24));
		summary.addView(installed, new LinearLayout.LayoutParams(match(), 26));
		summary.addView(label(selected.description(), 13, MUTED));
		side.addView(summary, new LinearLayout.LayoutParams(match(), 220));

		LinearLayout links = column(8);
		links.setPadding(20, 14, 20, 14);
		links.setBackground(glass(Color.argb(56, 10, 15, 27), Color.argb(84, 7, 12, 22), 0, Color.argb(54, 105, 116, 150)));
		links.addView(moduleAction("Insert import", "plus-solid.png", () -> insertModuleImport(selected)), new LinearLayout.LayoutParams(match(), 34));
		links.addView(moduleAction("Copy import", "clone-solid.png", () -> copyModuleImport(selected)), new LinearLayout.LayoutParams(match(), 34));
		links.addView(moduleAction("Open source", "folder-open-solid.png", () -> openSelectedModuleInEditor(selected)), new LinearLayout.LayoutParams(match(), 34));
		links.addView(moduleAction("Remove from libraries", "broom-solid.png", () -> removeSelectedModule(selected)), new LinearLayout.LayoutParams(match(), 34));
		// Four 34px actions plus spacing/padding need more than the old 170px
		// slot; keeping this explicit prevents the last action from being clipped.
		side.addView(links, new LinearLayout.LayoutParams(match(), 194));

		LinearLayout details = column(12);
		details.setPadding(20, 16, 20, 18);
		details.addView(label("Details", 15, TEXT));
		details.addView(detailRow("Author", selected.author()));
		details.addView(detailRow("Type", selected.category()));
		details.addView(detailRow("Status", selected.status()));
		details.addView(detailRow("File", script == null ? "Not local" : script.getFileName().toString()));
		details.addView(detailRow("Import", importSnippet.isBlank() ? "Unavailable" : importSnippet));
		TextView open = label("Insert Import", 13, TEXT);
		open.setGravity(Gravity.CENTER);
		makeHover(open, glass(Color.argb(160, 93, 48, 168), Color.argb(128, 63, 34, 116), 8, PURPLE_SOFT),
				glass(Color.argb(210, 126, 64, 220), Color.argb(160, 91, 46, 170), 8, STROKE_HOVER));
		open.setOnClickListener(view -> insertModuleImport(selected));
		details.addView(open, new LinearLayout.LayoutParams(match(), 38));
		side.addView(details, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		return side;
	}

	private String keybindLabel(int keybind) {
		if (keybind == InputConstants.UNKNOWN.getValue()) {
			return "Unbound";
		}
		try {
			return InputConstants.Type.KEYSYM.getOrCreate(keybind).getDisplayName().getString();
		} catch (Exception ignored) {
			return "Key " + keybind;
		}
	}

	private void toggleRuntimeModule(ModuleManager.RuntimeModuleSummary module) {
		boolean changed = ModuleManager.get().setModuleEnabled(module.id(), !module.enabled());
		if (!changed) {
			saveUiMessage("Unable to toggle module.");
			return;
		}
		saveUiMessage(module.name() + (module.enabled() ? " disabled." : " enabled."));
		renderShell();
	}

	private void openSelectedModuleInEditor(ModuleItem module) {
		Path script = localModulePath(module);
		if (script == null || Files.notExists(script)) {
			saveUiMessage("Library source is not a local script.");
			return;
		}
		page = Page.EDITOR;
		selectEditorScript(script);
	}

	private void removeSelectedModule(ModuleItem module) {
		Path script = localModulePath(module);
		if (script == null || !script.startsWith(scriptDir)) {
			saveUiMessage("Only local script libraries can be removed here.");
			return;
		}
		String relative = scriptDir.relativize(script).toString().replace('\\', '/');
		FluxusAppState.get().setScriptModule(relative, false);
		selectedModuleId = "";
		saveUiMessage("Removed " + script.getFileName() + " from Libraries.");
		renderShell();
	}

	private void copyModuleImport(ModuleItem module) {
		String snippet = moduleImportSnippet(module);
		if (snippet.isBlank()) {
			saveUiMessage("No import snippet available for this library.");
			return;
		}
		copyToClipboard(snippet.toString());
		saveUiMessage("Copied library import snippet.");
	}

	private void insertModuleImport(ModuleItem module) {
		String snippet = moduleImportSnippet(module);
		if (snippet.isBlank()) {
			saveUiMessage("No import snippet available for this library.");
			return;
		}
		if (codeEditor != null) {
			page = Page.EDITOR;
			insertEditorSnippet(snippet + "\n");
			return;
		}
		if (selectedScript == null) {
			copyToClipboard(snippet);
			saveUiMessage("Copied library import. Open a script to paste it.");
			return;
		}
		String draft = draftFor(selectedScript);
		int insertAt = importInsertOffset(draft);
		String insertion = snippet + "\n";
		String next = draft.substring(0, insertAt) + insertion + draft.substring(insertAt);
		editorDrafts.put(selectedScript, next);
		dirtyScripts.add(selectedScript);
		editorDraft = next;
		editorDirty = true;
		page = Page.EDITOR;
		appendEditorLog("Inserted library import for " + module.name() + ".");
		renderShell();
	}

	private String moduleImportSnippet(ModuleItem module) {
		Path script = localModulePath(module);
		if (script == null || scriptDir == null || !script.startsWith(scriptDir)) {
			return "";
		}
		String relative = scriptDir.relativize(script).toString().replace('\\', '/');
		String baseName = stripExtension(script.getFileName().toString()).replaceAll("[^A-Za-z0-9_]", "_");
		if (relative.toLowerCase(Locale.ROOT).endsWith(".pyj")) {
			return "from system.lib import java\n\n" + baseName + " = java.import_pyjinn_script(\"" + relative + "\")";
		}
		return "from " + stripExtension(relative).replace('/', '.').replace('\\', '.').replaceAll("[^A-Za-z0-9_.]", "_") + " import *";
	}

	private Path localModulePath(ModuleItem module) {
		if (module == null || module.id() == null || !module.id().startsWith("local:")) {
			return null;
		}
		return scriptDir.resolve(module.id().substring("local:".length()).replace('/', java.io.File.separatorChar)).normalize();
	}

	private List<ModuleItem> visibleModules(List<ModuleItem> allModules) {
		return allModules.stream()
				.filter(module -> moduleFilter.equals("All")
						|| (moduleFilter.equals("Installed") ? module.installed() : module.category().equalsIgnoreCase(moduleFilter)))
				.filter(module -> moduleSearchQuery.isBlank()
						|| (module.name() + " " + module.description() + " " + module.author() + " " + module.category())
						.toLowerCase(Locale.ROOT).contains(moduleSearchQuery.toLowerCase(Locale.ROOT)))
				.toList();
	}

	private ModuleItem selectedModule() {
		List<ModuleItem> modules = FluxusAppState.get().modules();
		if (modules.isEmpty()) {
			return new ModuleItem("empty", "No libraries", "Shulkr", "v0.0.0",
					"Mark scripts as libraries from the Editor when you want them to be reusable imports.", "Other", "puzzle-piece-solid.png", "Unavailable", false, false);
		}
		if (selectedModuleId == null || selectedModuleId.isBlank()) {
			selectedModuleId = modules.getFirst().id();
			return modules.getFirst();
		}
		return modules.stream()
				.filter(module -> module.id().equals(selectedModuleId))
				.findFirst()
				.orElse(modules.getFirst());
	}

	private String wrapModuleDescription(String description) {
		if (description == null || description.isBlank()) {
			return "";
		}
		String trimmed = description.trim();
		return trimmed.length() > 88 ? trimmed.substring(0, 85) + "..." : trimmed;
	}

	private View templatesCenter() {
		List<TemplateItem> templates = visibleTemplates();
		LinearLayout center = column(18);

		LinearLayout panel = column(14);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));

		LinearLayout header = row(12);
		header.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout title = column(6);
		LinearLayout titleLine = row(10);
		titleLine.setGravity(Gravity.CENTER_VERTICAL);
		titleLine.addView(icon("layer-group-solid.png", PURPLE), new LinearLayout.LayoutParams(26, 26));
		titleLine.addView(label("Templates", 24, TEXT));
		title.addView(titleLine);
		title.addView(label("Start with polished script scaffolds, then customize in the editor.", 14, MUTED));
		header.addView(title, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		List<TemplateItem> allTemplates = FluxusAppState.get().templates();
		header.addView(statMini("Templates", String.valueOf(allTemplates.size()), "folder-solid.png"), new LinearLayout.LayoutParams(118, 60));
		header.addView(statMini("Starter Kits", String.valueOf(allTemplates.stream().filter(template -> template.difficulty().equalsIgnoreCase("Easy")).count()), "clipboard-check-solid.png"), new LinearLayout.LayoutParams(130, 60));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 74));

		LinearLayout chips = row(8);
		for (String chip : new String[]{"All", "Featured", "Beginner", "Combat", "Farming", "Movement", "Render", "Utility", "World"}) {
			int width = chip.equals("Featured") || chip.equals("Movement") || chip.equals("Beginner") ? 88 : 72;
			View filter = chip(chip, chip.equals(templateFilter));
			filter.setOnClickListener(view -> {
				templateFilter = chip;
				renderShell();
			});
			chips.addView(filter, new LinearLayout.LayoutParams(width, 34));
		}
		panel.addView(chips, new LinearLayout.LayoutParams(match(), 38));

		LinearLayout spotlight = row(14);
		spotlight.setPadding(18, 0, 18, 0);
		spotlight.setGravity(Gravity.CENTER_VERTICAL);
		spotlight.setBackground(glass(Color.argb(120, 52, 30, 90), Color.argb(88, 14, 20, 35), 12, Color.argb(96, 163, 88, 255)));
		spotlight.addView(iconBadge("star-solid.png", PURPLE, Color.argb(75, 163, 88, 255), 54, 13));
		LinearLayout spotCopy = column(5);
		spotCopy.addView(label("Recommended Starter Flow", 16, TEXT));
		spotCopy.addView(label("Pick a template, review the generated structure, then open it in Editor.", 12, MUTED));
		spotlight.addView(spotCopy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		spotlight.addView(templateStep("1", "Choose"));
		spotlight.addView(templateStep("2", "Preview"));
		spotlight.addView(templateStep("3", "Customize"));
		panel.addView(spotlight, new LinearLayout.LayoutParams(match(), 78));

		LinearLayout grid = column(10);
		int index = 0;
		int rows = Math.max(1, Math.min(2, (templates.size() + 3) / 4));
		for (int row = 0; row < rows; row++) {
			LinearLayout line = row(10);
			for (int col = 0; col < 4; col++) {
				if (index < templates.size()) {
					line.addView(templateCard(templates.get(index++)), new LinearLayout.LayoutParams(0, 224, 1.0F));
				} else {
					line.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, 224, 1.0F));
				}
			}
			grid.addView(line, new LinearLayout.LayoutParams(match(), 232));
		}
		if (templates.isEmpty()) {
			TextView empty = label("No templates match this filter.", 14, MUTED);
			empty.setGravity(Gravity.CENTER);
			grid.addView(empty, new LinearLayout.LayoutParams(match(), 120));
		}
		panel.addView(grid, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		center.addView(panel, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 70);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		center.addView(dock, dockLp);
		return center;
	}

	private View templateCard(TemplateItem data) {
		boolean active = data.id().equals(selectedTemplate().id());
		LinearLayout card = column(10);
		card.setPadding(14, 14, 14, 14);
		makeHover(card, active ? round(Color.argb(132, 35, 22, 56), 11, STROKE_HOVER) : round(Color.argb(116, 15, 21, 34), 11, Color.argb(72, 105, 116, 150)),
				active ? round(Color.argb(178, 48, 29, 78), 11, STROKE_HOVER) : round(Color.argb(160, 24, 30, 48), 11, STROKE_HOVER));

		LinearLayout head = row(12);
		head.setGravity(Gravity.TOP);
		head.addView(templateArt(data.icon(), data.category()), new LinearLayout.LayoutParams(56, 56));
		LinearLayout copy = column(5);
		LinearLayout name = row(6);
		name.setGravity(Gravity.CENTER_VERTICAL);
		name.addView(label(data.name(), 15, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		if (!data.badge().isEmpty()) {
			name.addView(tag(data.badge(), true));
		}
		copy.addView(name);
		copy.addView(label(data.category(), 12, PURPLE));
		head.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		card.addView(head, new LinearLayout.LayoutParams(match(), 62));
		card.addView(label(wrapTemplateDescription(data.description()), 12, MUTED), new LinearLayout.LayoutParams(match(), 40));
		LinearLayout meta = row(8);
		meta.addView(tag(data.difficulty(), false), new LinearLayout.LayoutParams(78, 24));
		meta.addView(tag(data.blocks() + " blocks", false), new LinearLayout.LayoutParams(82, 24));
		card.addView(meta, new LinearLayout.LayoutParams(match(), 26));
		TextView preview = label(active ? "Selected" : "Preview", 13, active ? TEXT : MUTED);
		preview.setGravity(Gravity.CENTER);
		makeHover(preview, active ? glass(Color.argb(160, 93, 48, 168), Color.argb(128, 63, 34, 116), 8, PURPLE_SOFT) : round(Color.argb(90, 18, 24, 39), 8, STROKE),
				active ? glass(Color.argb(210, 126, 64, 220), Color.argb(160, 91, 46, 170), 8, STROKE_HOVER) : round(Color.argb(135, 27, 33, 53), 8, STROKE_HOVER));
		card.addView(preview, new LinearLayout.LayoutParams(match(), 38));
		card.setOnClickListener(view -> {
			selectedTemplateId = data.id();
			renderShell();
		});
		preview.setOnClickListener(view -> {
			selectedTemplateId = data.id();
			renderShell();
		});
		return card;
	}

	private View templateDetailsPanel() {
		LinearLayout side = column(14);
		side.addView(templateSelectedPanel(), new LinearLayout.LayoutParams(match(), 330));
		side.addView(templatePreviewPanel(), new LinearLayout.LayoutParams(match(), 350));
		side.addView(templateChecklistPanel(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		return side;
	}

	private View templateSelectedPanel() {
		TemplateItem selected = selectedTemplate();
		LinearLayout panel = column(14);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("layer-group-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Selected Template", 16, TEXT));
		panel.addView(title);
		LinearLayout hero = row(14);
		hero.setGravity(Gravity.CENTER_VERTICAL);
		hero.addView(templateArt(selected.icon(), selected.category()), new LinearLayout.LayoutParams(66, 66));
		LinearLayout copy = column(5);
		copy.addView(label(selected.name(), 18, TEXT));
		copy.addView(label(selected.category() + " scaffold", 12, MUTED));
		copy.addView(label("*  " + selected.difficulty() + " friendly", 12, selected.difficulty().equalsIgnoreCase("Advanced") ? PURPLE : GREEN));
		hero.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		panel.addView(hero, new LinearLayout.LayoutParams(match(), 74));
		panel.addView(label(selected.description(), 13, MUTED));
		LinearLayout tags = row(8);
		tags.addView(tag("Python", false), new LinearLayout.LayoutParams(70, 24));
		tags.addView(tag(selected.category(), false), new LinearLayout.LayoutParams(82, 24));
		tags.addView(tag(selected.blocks() + " blocks", false), new LinearLayout.LayoutParams(82, 24));
		panel.addView(tags, new LinearLayout.LayoutParams(match(), 28));
		TextView use = label("Use Template", 13, TEXT);
		use.setGravity(Gravity.CENTER);
		makeHover(use, glass(Color.argb(170, 93, 48, 168), Color.argb(138, 63, 34, 116), 8, PURPLE_SOFT),
				glass(Color.argb(220, 126, 64, 220), Color.argb(170, 91, 46, 170), 8, STROKE_HOVER));
		use.setOnClickListener(view -> useSelectedTemplate());
		panel.addView(use, new LinearLayout.LayoutParams(match(), 40));
		return panel;
	}

	private View templatePreviewPanel() {
		TemplateItem selected = selectedTemplate();
		LinearLayout panel = column(10);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("code-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Code Preview", 16, TEXT));
		panel.addView(title);
		LinearLayout code = column(5);
		code.setPadding(14, 12, 14, 12);
		code.setBackground(round(Color.argb(96, 3, 8, 16), 10, Color.argb(58, 105, 116, 150)));
		for (String line : previewLines(selected.script())) {
			code.addView(label(line, 11, line.startsWith("def") ? PURPLE : line.contains("\"") ? Color.argb(255, 154, 226, 98) : MUTED));
		}
		panel.addView(code, new LinearLayout.LayoutParams(match(), 186));
		return panel;
	}

	private View templateChecklistPanel() {
		TemplateItem selected = selectedTemplate();
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		panel.addView(label("What You Get", 16, TEXT));
		panel.addView(checkLine(selected.difficulty() + " starter scaffold"));
		panel.addView(checkLine("Editable " + selected.category().toLowerCase(Locale.ROOT) + " logic"));
		panel.addView(checkLine("Ruff-ready Python script"));
		panel.addView(checkLine("Created directly in your scripts folder"));
		return panel;
	}

	private List<TemplateItem> visibleTemplates() {
		List<TemplateItem> templates = FluxusAppState.get().templates();
		if (templates.isEmpty()) {
			return List.of(TemplateItem.defaults());
		}
		if (templateFilter.equals("All")) {
			return templates;
		}
		return templates.stream()
				.filter(template -> templateMatchesFilter(template, templateFilter))
				.toList();
	}

	private boolean templateMatchesFilter(TemplateItem template, String filter) {
		if (filter.equals("Featured")) {
			return template.badge().equalsIgnoreCase("Featured") || template.badge().equalsIgnoreCase("Popular");
		}
		if (filter.equals("Beginner")) {
			return template.difficulty().equalsIgnoreCase("Easy");
		}
		return template.category().equalsIgnoreCase(filter);
	}

	private TemplateItem selectedTemplate() {
		List<TemplateItem> templates = FluxusAppState.get().templates();
		if (templates.isEmpty()) {
			return TemplateItem.defaults();
		}
		for (TemplateItem template : templates) {
			if (template.id().equals(selectedTemplateId)) {
				return template;
			}
		}
		TemplateItem first = templates.getFirst();
		selectedTemplateId = first.id();
		return first;
	}

	private String wrapTemplateDescription(String description) {
		if (description.length() <= 56) {
			return description;
		}
		int split = description.lastIndexOf(' ', 56);
		if (split <= 18) {
			split = 56;
		}
		return description.substring(0, split).trim() + "\n" + trimTo(description.substring(split).trim(), 54);
	}

	private List<String> previewLines(String script) {
		List<String> lines = new ArrayList<>();
		for (String line : script.split("\n")) {
			lines.add(line.length() > 42 ? line.substring(0, 39) + "..." : line);
			if (lines.size() >= 9) {
				break;
			}
		}
		while (lines.size() < 8) {
			lines.add("");
		}
		return lines;
	}

	private void useSelectedTemplate() {
		try {
			FluxusAppState.ScriptSummary summary = FluxusAppState.get().createScriptFromTemplate(selectedTemplate().id());
			refreshEditorScripts();
			Path created = scriptDir.resolve(summary.path()).normalize();
			if (Files.exists(created)) {
				selectEditorScript(created);
			} else {
				openPage(Page.EDITOR);
			}
			appendEditorLog("Created " + summary.name() + " from " + selectedTemplate().name() + ".");
		} catch (IOException e) {
			appendEditorLog("Template failed: " + e.getMessage());
			refreshConsoleLogList();
		}
	}

	private View statMini(String name, String value, String iconFile) {
		LinearLayout stat = row(10);
		stat.setGravity(Gravity.CENTER_VERTICAL);
		stat.setPadding(12, 8, 10, 8);
		stat.setBackground(round(Color.argb(104, 15, 21, 34), 10, Color.argb(70, 105, 116, 150)));
		LinearLayout copy = column(2);
		copy.addView(label(name, 11, MUTED));
		copy.addView(label(value, 22, TEXT));
		stat.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		stat.addView(iconBadge(iconFile, PURPLE, Color.argb(70, 163, 88, 255), 36, 9));
		return stat;
	}

	private View templateStep(String number, String text) {
		LinearLayout step = row(8);
		step.setGravity(Gravity.CENTER_VERTICAL);
		TextView badge = label(number, 12, TEXT);
		badge.setGravity(Gravity.CENTER);
		badge.setBackground(round(Color.argb(165, 104, 49, 205), 9, PURPLE_SOFT));
		step.addView(badge, new LinearLayout.LayoutParams(26, 26));
		step.addView(label(text, 12, MUTED));
		return step;
	}

	private View templateArt(String iconFile, String category) {
		FrameLayout art = new FrameLayout(requireContext());
		int color = category.equals("Combat") ? Color.argb(96, 255, 72, 96)
				: category.equals("Farming") ? Color.argb(96, 62, 216, 99)
				: category.equals("Movement") ? Color.argb(90, 45, 160, 255)
				: category.equals("Render") ? Color.argb(96, 190, 96, 255)
				: Color.argb(92, 163, 88, 255);
		art.setBackground(round(color, 12, 0));
		art.addView(icon(iconFile, TEXT), centered(32, 32));
		return art;
	}

	private View checkLine(String text) {
		LinearLayout row = row(9);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(icon("check-solid.png", GREEN), new LinearLayout.LayoutParams(15, 15));
		row.addView(label(text, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private View windowSpyCenter() {
		SpySnapshot snapshot = currentSpySnapshot();
		LinearLayout center = column(18);

		LinearLayout panel = column(14);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));

		LinearLayout header = row(12);
		header.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout title = column(6);
		LinearLayout titleLine = row(10);
		titleLine.setGravity(Gravity.CENTER_VERTICAL);
		titleLine.addView(icon("window-restore-regular.png", PURPLE), new LinearLayout.LayoutParams(26, 26));
		titleLine.addView(label("WindowSpy", 24, TEXT));
		title.addView(titleLine);
		title.addView(label("Inspect the world state you need for scripts: targets, entities, NBT, and events.", 14, MUTED));
		header.addView(title, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		header.addView(spyMetric("Dimension", snapshot.dimension(), "cloud-solid.png"), new LinearLayout.LayoutParams(132, 60));
		header.addView(spyMetric("Light", snapshot.light(), "bell-solid.png"), new LinearLayout.LayoutParams(104, 60));
		header.addView(spyMetric("Entities", snapshot.entityCount(), "user-solid.png"), new LinearLayout.LayoutParams(116, 60));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 74));

		LinearLayout chips = row(8);
		for (String chip : new String[]{"Live", "Target", "Entities", "NBT", "Blocks", "Events", "Script Vars", "Packets"}) {
			int width = chip.equals("Script Vars") ? 98 : chip.equals("Entities") ? 82 : 72;
			chips.addView(windowSpyChip(chip, snapshot), new LinearLayout.LayoutParams(width, 34));
		}
		panel.addView(chips, new LinearLayout.LayoutParams(match(), 38));

		panel.addView(windowSpyTabContent(snapshot), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		center.addView(panel, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 70);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		center.addView(dock, dockLp);
		return center;
	}

	private View windowSpyTabContent(SpySnapshot snapshot) {
		if (windowSpyTab.equals("Target") || windowSpyTab.equals("Blocks")) {
			LinearLayout body = row(14);
			LinearLayout left = column(14);
			left.addView(targetSnapshotPanel(snapshot), new LinearLayout.LayoutParams(match(), 230));
			left.addView(hiddenNbtPanel(snapshot), new LinearLayout.LayoutParams(match(), 0, 1.0F));
			body.addView(left, new LinearLayout.LayoutParams(0, match(), 1.08F));
			LinearLayout right = column(14);
			right.addView(scriptHooksPanel(snapshot), new LinearLayout.LayoutParams(match(), 300));
			right.addView(worldFlagsPanel(snapshot), new LinearLayout.LayoutParams(match(), 0, 1.0F));
			body.addView(right, new LinearLayout.LayoutParams(0, match(), 1.0F));
			return body;
		}
		if (windowSpyTab.equals("Entities")) {
			LinearLayout body = row(14);
			body.addView(nearbyEntitiesPanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.05F));
			body.addView(eventTimelinePanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.0F));
			return body;
		}
		if (windowSpyTab.equals("NBT")) {
			LinearLayout body = row(14);
			body.addView(hiddenNbtPanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.0F));
			body.addView(scriptHooksPanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.0F));
			return body;
		}
		if (windowSpyTab.equals("Events")) {
			LinearLayout body = row(14);
			body.addView(eventTimelinePanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.1F));
			body.addView(worldFlagsPanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.0F));
			return body;
		}
		if (windowSpyTab.equals("Script Vars")) {
			LinearLayout body = row(14);
			body.addView(scriptHooksPanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.0F));
			body.addView(spyExportPanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.0F));
			return body;
		}
		if (windowSpyTab.equals("Packets")) {
			LinearLayout body = row(14);
			body.addView(packetInfoPanel(), new LinearLayout.LayoutParams(0, match(), 1.0F));
			body.addView(eventTimelinePanel(snapshot), new LinearLayout.LayoutParams(0, match(), 1.0F));
			return body;
		}
		LinearLayout body = row(14);
		LinearLayout left = column(14);
		left.addView(targetSnapshotPanel(snapshot), new LinearLayout.LayoutParams(match(), 220));
		left.addView(nearbyEntitiesPanel(snapshot), new LinearLayout.LayoutParams(match(), 270));
		left.addView(eventTimelinePanel(snapshot), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		body.addView(left, new LinearLayout.LayoutParams(0, match(), 1.18F));
		LinearLayout right = column(14);
		right.addView(hiddenNbtPanel(snapshot), new LinearLayout.LayoutParams(match(), 260));
		right.addView(scriptHooksPanel(snapshot), new LinearLayout.LayoutParams(match(), 300));
		right.addView(worldFlagsPanel(snapshot), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		body.addView(right, new LinearLayout.LayoutParams(0, match(), 1.0F));
		return body;
	}

	private View targetSnapshotPanel(SpySnapshot snapshot) {
		SpyTarget target = snapshot.target();
		LinearLayout card = column(12);
		card.setPadding(18, 16, 18, 16);
		card.setBackground(round(Color.argb(106, 15, 21, 34), 12, Color.argb(82, 105, 116, 150)));
		LinearLayout top = row(14);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.addView(iconBadge("eye-dropper-solid.png", PURPLE, Color.argb(84, 163, 88, 255), 58, 13));
		LinearLayout copy = column(5);
		copy.addView(label("Looking At", 12, PURPLE));
		TextView title = label(target.title(), 18, TEXT);
		title.setSingleLine(true);
		copy.addView(title);
		TextView position = label(target.position() + "  |  Facing: " + target.face(), 12, MUTED);
		position.setSingleLine(true);
		copy.addView(position);
		top.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		top.addView(tag(target.kind(), true), new LinearLayout.LayoutParams(108, 24));
		card.addView(top, new LinearLayout.LayoutParams(match(), 82));
		LinearLayout data = row(10);
		data.addView(spyFact("Kind", target.kind()), new LinearLayout.LayoutParams(0, 54, 1.0F));
		data.addView(spyFact("State", target.state()), new LinearLayout.LayoutParams(0, 54, 1.2F));
		data.addView(spyFact("Distance", target.distance()), new LinearLayout.LayoutParams(0, 54, 1.0F));
		card.addView(data, new LinearLayout.LayoutParams(match(), 62));
		return card;
	}

	private View nearbyEntitiesPanel(SpySnapshot snapshot) {
		LinearLayout panel = column(10);
		panel.setPadding(18, 16, 18, 16);
		panel.setBackground(round(Color.argb(100, 15, 21, 34), 12, Color.argb(76, 105, 116, 150)));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(icon("user-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		header.addView(label("Nearby Entities", 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		header.addView(tag("10m radius", false), new LinearLayout.LayoutParams(86, 24));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 28));
		for (String[] entity : snapshot.entities()) {
			panel.addView(spyEntityRow(entity), new LinearLayout.LayoutParams(match(), 44));
		}
		return panel;
	}

	private View hiddenNbtPanel(SpySnapshot snapshot) {
		LinearLayout panel = column(10);
		panel.setPadding(18, 16, 18, 16);
		panel.setBackground(round(Color.argb(100, 15, 21, 34), 12, Color.argb(76, 105, 116, 150)));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(icon("clipboard-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		header.addView(label("Hidden NBT", 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View copy = iconButton("clone-solid.png", MUTED);
		copy.setOnClickListener(view -> copyToClipboard(snapshot.target().rawNbt().isBlank() ? snapshot.target().details() : snapshot.target().rawNbt()));
		header.addView(copy, new LinearLayout.LayoutParams(30, 30));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 34));
		for (String[] row : snapshot.nbtRows()) {
			panel.addView(nbtRow(row[0], row[1]), new LinearLayout.LayoutParams(match(), 32));
		}
		return panel;
	}

	private View scriptHooksPanel(SpySnapshot snapshot) {
		LinearLayout panel = column(8);
		panel.setPadding(18, 16, 18, 16);
		panel.setBackground(round(Color.argb(100, 15, 21, 34), 12, Color.argb(76, 105, 116, 150)));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(icon("code-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		header.addView(label("Script Hooks", 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View insert = link("Insert");
		insert.setOnClickListener(view -> sendSpySnapshotToEditor(snapshot));
		header.addView(insert);
		panel.addView(header, new LinearLayout.LayoutParams(match(), 30));
		for (String[] hook : snapshot.hooks()) {
			panel.addView(hookRow(hook[0], hook[1]), new LinearLayout.LayoutParams(match(), 28));
		}
		TextView open = label("Open Snapshot In Editor", 13, TEXT);
		open.setGravity(Gravity.CENTER);
		makeHover(open, glass(Color.argb(160, 93, 48, 168), Color.argb(128, 63, 34, 116), 8, PURPLE_SOFT),
				glass(Color.argb(210, 126, 64, 220), Color.argb(160, 91, 46, 170), 8, STROKE_HOVER));
		open.setOnClickListener(view -> sendSpySnapshotToEditor(snapshot));
		panel.addView(open, new LinearLayout.LayoutParams(match(), 36));
		return panel;
	}

	private View packetInfoPanel() {
		LinearLayout panel = column(12);
		panel.setPadding(18, 16, 18, 16);
		panel.setBackground(round(Color.argb(100, 15, 21, 34), 12, Color.argb(76, 105, 116, 150)));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(icon("circle-info-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		header.addView(label("Packet Notes", 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		header.addView(tag("client-only", false), new LinearLayout.LayoutParams(82, 24));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 30));
		panel.addView(label("Minecraft does not expose a safe packet inspector through this UI yet. WindowSpy can still give scripts block, entity, NBT, player, and world context from client state.", 13, MUTED), new LinearLayout.LayoutParams(match(), wrap()));
		panel.addView(checkLine("Target block/entity snapshot"));
		panel.addView(checkLine("Nearby render entities"));
		panel.addView(checkLine("Block entity NBT where available"));
		panel.addView(checkLine("World flags and script variables"));
		return panel;
	}

	private View eventTimelinePanel(SpySnapshot snapshot) {
		LinearLayout panel = column(10);
		panel.setPadding(18, 16, 18, 16);
		panel.setBackground(round(Color.argb(86, 15, 21, 34), 12, Color.argb(66, 105, 116, 150)));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(icon("bell-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		header.addView(label("Live Event Feed", 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		header.addView(tag("capturing", true), new LinearLayout.LayoutParams(82, 24));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 30));
		for (String[] signal : snapshot.signals()) {
			panel.addView(signalRow(signal), new LinearLayout.LayoutParams(match(), 34));
		}
		return panel;
	}

	private View worldFlagsPanel(SpySnapshot snapshot) {
		LinearLayout panel = column(10);
		panel.setPadding(18, 16, 18, 16);
		panel.setBackground(round(Color.argb(86, 15, 21, 34), 12, Color.argb(66, 105, 116, 150)));
		panel.addView(label("World Flags", 16, TEXT));
		for (String[] flag : snapshot.flags()) {
			panel.addView(flagRow(flag[0], flag[1], flag[2]), new LinearLayout.LayoutParams(match(), 30));
		}
		return panel;
	}

	private View windowSpySidePanel() {
		SpySnapshot snapshot = currentSpySnapshot();
		LinearLayout side = column(14);
		side.addView(spyWatchPanel(snapshot), new LinearLayout.LayoutParams(match(), 330));
		side.addView(spyRecipePanel(snapshot), new LinearLayout.LayoutParams(match(), 300));
		side.addView(spyExportPanel(snapshot), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		return side;
	}

	private View spyWatchPanel(SpySnapshot snapshot) {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(8);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("magnifying-glass-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Watch Panel", 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		title.addView(tag("Live", true), new LinearLayout.LayoutParams(54, 24));
		panel.addView(title, new LinearLayout.LayoutParams(match(), 30));
		for (String[] row : snapshot.watchRows()) {
			panel.addView(watchRow(row[0], row[1]), new LinearLayout.LayoutParams(match(), 38));
		}
		return panel;
	}

	private View spyRecipePanel(SpySnapshot snapshot) {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(8);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("clipboard-check-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Script Ingredients", 16, TEXT));
		panel.addView(title, new LinearLayout.LayoutParams(match(), 30));
		panel.addView(checkLine("Raycast target object"));
		panel.addView(checkLine("Block state and coordinates"));
		panel.addView(checkLine("Nearby entity filters"));
		panel.addView(checkLine("NBT compound snapshot"));
		panel.addView(checkLine("World and player context"));
		LinearLayout buttons = row(10);
		View copyVars = textButton("Copy Vars");
		copyVars.setOnClickListener(view -> copyToClipboard(snapshot.scriptSnippet()));
		buttons.addView(copyVars, new LinearLayout.LayoutParams(0, 36, 1.0F));
		View refresh = textButton("Refresh");
		refresh.setOnClickListener(view -> renderShell());
		buttons.addView(refresh, new LinearLayout.LayoutParams(0, 36, 1.0F));
		panel.addView(buttons, new LinearLayout.LayoutParams(match(), 40));
		return panel;
	}

	private View spyExportPanel(SpySnapshot snapshot) {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		panel.addView(label("Snapshot Export", 16, TEXT));
		panel.addView(label("Generate a compact Python-ready snapshot from the current world inspection.", 12, MUTED));
		LinearLayout code = column(5);
		code.setPadding(12, 10, 12, 10);
		code.setBackground(round(Color.argb(92, 3, 8, 16), 10, Color.argb(56, 105, 116, 150)));
		for (String line : snapshot.scriptSnippet().split("\n")) {
			code.addView(label(line, 11, line.startsWith("if") ? PURPLE : MUTED));
		}
		panel.addView(code, new LinearLayout.LayoutParams(match(), 100));
		TextView export = label("Send To Editor", 13, TEXT);
		export.setGravity(Gravity.CENTER);
		makeHover(export, glass(Color.argb(160, 93, 48, 168), Color.argb(128, 63, 34, 116), 8, PURPLE_SOFT),
				glass(Color.argb(210, 126, 64, 220), Color.argb(160, 91, 46, 170), 8, STROKE_HOVER));
		export.setOnClickListener(view -> sendSpySnapshotToEditor(snapshot));
		panel.addView(export, new LinearLayout.LayoutParams(match(), 38));
		return panel;
	}

	private View spyMetric(String name, String value, String iconFile) {
		LinearLayout metric = row(9);
		metric.setGravity(Gravity.CENTER_VERTICAL);
		metric.setPadding(12, 8, 10, 8);
		metric.setBackground(round(Color.argb(104, 15, 21, 34), 10, Color.argb(70, 105, 116, 150)));
		LinearLayout copy = column(2);
		copy.addView(label(name, 11, MUTED));
		copy.addView(label(value, 16, TEXT));
		metric.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		metric.addView(iconBadge(iconFile, PURPLE, Color.argb(70, 163, 88, 255), 34, 9));
		return metric;
	}

	private View spyFact(String name, String value) {
		LinearLayout fact = column(4);
		fact.setPadding(12, 8, 12, 8);
		fact.setBackground(round(Color.argb(86, 18, 24, 39), 8, Color.argb(52, 105, 116, 150)));
		fact.addView(label(name, 11, FAINT));
		fact.addView(label(value, 12, TEXT));
		return fact;
	}

	private View spyEntityRow(String[] data) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(10, 0, 10, 0);
		makeHover(row, round(Color.argb(70, 18, 24, 39), 8, Color.argb(44, 105, 116, 150)),
				round(Color.argb(124, 27, 33, 53), 8, STROKE_HOVER));
		row.addView(iconBadge(data[4], toneColor(data[5]), Color.argb(60, 163, 88, 255), 32, 8), new LinearLayout.LayoutParams(32, 32));
		LinearLayout copy = column(2);
		copy.addView(label(data[0], 13, TEXT));
		copy.addView(label(data[1], 11, MUTED));
		row.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(label(data[2], 12, MUTED), new LinearLayout.LayoutParams(54, wrap()));
		row.addView(tag(data[3], false), new LinearLayout.LayoutParams(78, 24));
		return row;
	}

	private View nbtRow(String key, String value) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(key, 12, PURPLE), new LinearLayout.LayoutParams(92, wrap()));
		row.addView(label(value, 11, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private View hookRow(String name, String value) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(10, 0, 10, 0);
		row.setBackground(round(Color.argb(72, 18, 24, 39), 8, Color.argb(42, 105, 116, 150)));
		row.addView(label(name, 12, PURPLE), new LinearLayout.LayoutParams(112, wrap()));
		row.addView(label(value, 11, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private View signalRow(String[] data) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(icon("circle-solid.png", toneColor(data[3])), new LinearLayout.LayoutParams(10, 10));
		row.addView(label(data[0], 12, TEXT), new LinearLayout.LayoutParams(62, wrap()));
		row.addView(label(data[1], 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(label(data[2], 11, FAINT), new LinearLayout.LayoutParams(68, wrap()));
		return row;
	}

	private View flagRow(String name, String value, String tone) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(icon("circle-solid.png", toneColor(tone)), new LinearLayout.LayoutParams(9, 9));
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(76, wrap()));
		row.addView(label(value, 12, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private View watchRow(String name, String value) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(92, wrap()));
		row.addView(label(value, 12, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private int toneColor(String tone) {
		return tone.equals("green") ? GREEN
				: tone.equals("red") ? Color.argb(255, 255, 72, 96)
				: tone.equals("yellow") ? Color.argb(255, 255, 190, 88)
				: tone.equals("cyan") ? Color.argb(255, 92, 224, 255)
				: tone.equals("blue") ? Color.argb(255, 126, 198, 255)
				: tone.equals("purple") ? PURPLE
				: MUTED;
	}

	private View windowSpyChip(String text, SpySnapshot snapshot) {
		View chipView = chip(text, text.equals(windowSpyTab));
		chipView.setOnClickListener(view -> {
			windowSpyTab = text;
			renderShell();
		});
		return chipView;
	}

	private SpySnapshot currentSpySnapshot() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			SpyTarget target = new SpyTarget("No world loaded", "Join a world to inspect live data.", "No active client world.", "", "Offline", "-", "-", "-", "-", false);
			return new SpySnapshot(target, List.<String[]>of(), List.<String[]>of(new String[]{"status", "No world loaded"}), List.<String[]>of(), List.<String[]>of(), List.<String[]>of(), List.<String[]>of(), "-", "-", "0", "# Join a world before using WindowSpy\n");
		}

		SpyTarget target = inspectTargetedEntity(8.0);
		if (target == null) {
			target = inspectTargetedBlock(8.0);
		}
		if (target == null) {
			target = new SpyTarget("Nothing selected", "Aim at a block or entity.", "No current crosshair target was available.", "", "Raycast", "-", "-", "-", "-", false);
		}

		List<String[]> entities = nearbyEntityRows(minecraft, 10.0, 5);
		List<String[]> nbtRows = nbtPreviewRows(target);
		List<String[]> hooks = List.of(
				new String[]{"target_kind", target.kind()},
				new String[]{"target_pos", target.position()},
				new String[]{"nearby_entities", entities.size() + " within 10m"},
				new String[]{"selected_nbt", target.rawNbt().isBlank() ? "none" : target.rawNbt().length() + " chars"},
				new String[]{"dimension_id", dimensionName(minecraft.level)}
		);
		List<String[]> watchRows = watchRows(minecraft, target);
		List<String[]> flags = worldFlagRows(minecraft, entities);
		List<String[]> signals = List.of(
				new String[]{"Tick", "game time " + minecraft.level.getLevelData().getGameTime(), "now", "green"},
				new String[]{"Raycast", target.title(), "live", "purple"},
				new String[]{"Entity", entities.size() + " nearby", "10m", entities.isEmpty() ? "blue" : "red"},
				new String[]{"NBT", target.rawNbt().isBlank() ? "no compound" : target.rawNbt().length() + " chars", "snap", "blue"}
		);
		String light = lightAtPlayer(minecraft);
		String snippet = spyScriptSnippet(target, entities, dimensionName(minecraft.level));
		return new SpySnapshot(target, entities, nbtRows, hooks, watchRows, flags, signals,
				shortDimensionName(minecraft.level), light, String.valueOf(totalRenderableEntities(minecraft)), snippet);
	}

	private SpyTarget inspectTargetedBlock(double maxDistance) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			return null;
		}
		HitResult hitResult = minecraft.player.pick(maxDistance, 0.0f, false);
		if (hitResult.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockHitResult blockHit = (BlockHitResult) hitResult;
		BlockPos pos = blockHit.getBlockPos();
		Level level = minecraft.level;
		String state = safe(BlockPositionReader.getBlockStateString(level, pos));
		BlockEntity blockEntity = level.getBlockEntity(pos);
		String nbt = blockEntity == null ? "" : readBlockEntityNbt(level, blockEntity);
		String distance = formatDouble(minecraft.player.position().distanceTo(hitResult.getLocation())) + "m";
		String title = compactStateName(state);
		String position = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
		String details = "Kind: Block\n"
				+ "State: " + state + "\n"
				+ "Position: " + position + "\n"
				+ "Face: " + blockHit.getDirection() + "\n"
				+ "Distance: " + distance + "\n"
				+ "Has Block Entity: " + (blockEntity != null ? "Yes" : "No");
		String meta = blockEntity == null ? "Plain block state"
				: blockEntity.getType().toString().replace("BlockEntityType[", "").replace("]", "");
		String raw = blockEntity == null ? "No block entity NBT is available for this block.\n\nState:\n" + state : nbt;
		return new SpyTarget(title, meta, details, raw, "Block", "BlockPos: " + position, blockHit.getDirection().toString(), compactBlockState(state), distance, blockEntity != null);
	}

	private SpyTarget inspectTargetedEntity(double maxDistance) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.player == null) {
			return null;
		}
		Entity entity = DebugRenderer.getTargetedEntity(minecraft.player, (int) maxDistance).orElse(null);
		if (entity == null) {
			return null;
		}
		EntityData data = new EntityExporter(0.0, true).export(entity);
		String position = formatDouble(data.position[0]) + ", " + formatDouble(data.position[1]) + ", " + formatDouble(data.position[2]);
		String distance = formatDouble(minecraft.player.distanceTo(entity)) + "m";
		String details = "Kind: Entity\n"
				+ "Name: " + safe(data.name) + "\n"
				+ "Type: " + safe(data.type) + "\n"
				+ "UUID: " + safe(data.uuid) + "\n"
				+ "Id: " + data.id + "\n"
				+ "Position: " + position + "\n"
				+ "Yaw/Pitch: " + formatDouble(data.yaw) + " / " + formatDouble(data.pitch)
				+ (data.health == null ? "" : "\nHealth: " + formatDouble(data.health));
		return new SpyTarget(safe(data.name), safe(data.type), details, safe(data.nbt), "Entity", "Pos: " + position, "crosshair", safe(data.type), distance, false);
	}

	private String readBlockEntityNbt(Level level, BlockEntity blockEntity) {
		try {
			CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
			return tag == null ? "" : tag.toString();
		} catch (Throwable ignored) {
			try {
				CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
				return tag == null ? "" : tag.toString();
			} catch (Throwable secondIgnored) {
				return "WindowSpy could not serialize this block entity on the client.";
			}
		}
	}

	private List<String[]> nearbyEntityRows(Minecraft minecraft, double radius, int limit) {
		List<Entity> nearby = new ArrayList<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity == minecraft.player) {
				continue;
			}
			if (minecraft.player.distanceTo(entity) <= radius) {
				nearby.add(entity);
			}
		}
		nearby.sort(Comparator.comparingDouble(entity -> minecraft.player.distanceTo(entity)));
		List<String[]> rows = new ArrayList<>();
		for (int i = 0; i < Math.min(limit, nearby.size()); i++) {
			Entity entity = nearby.get(i);
			String type = entity.getType().toString();
			String tone = hostileType(type) ? "red" : type.contains("item") ? "blue" : "green";
			rows.add(new String[]{
					entity.getName().getString(),
					type,
					formatDouble(minecraft.player.distanceTo(entity)) + "m",
					"#" + entity.getId(),
					entityIcon(type),
					tone
			});
		}
		if (rows.isEmpty()) {
			rows.add(new String[]{"No nearby entities", "Move within 10m of an entity", "-", "empty", "circle-info-solid.png", "blue"});
		}
		return rows;
	}

	private List<String[]> nbtPreviewRows(SpyTarget target) {
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[]{"kind", target.kind()});
		rows.add(new String[]{"position", target.position().replace("BlockPos: ", "").replace("Pos: ", "")});
		rows.add(new String[]{"state", target.state()});
		rows.add(new String[]{"raw", target.rawNbt().isBlank() ? "none" : target.rawNbt().length() + " chars"});
		String raw = target.rawNbt();
		if (!raw.isBlank() && !raw.startsWith("No block entity")) {
			String compact = raw.replace("{", "").replace("}", "");
			String[] parts = compact.split(",");
			for (int i = 0; i < Math.min(3, parts.length); i++) {
				String[] pair = parts[i].split(":", 2);
				rows.add(new String[]{pair[0].trim(), pair.length > 1 ? trimTo(pair[1].trim(), 34) : ""});
			}
		}
		return rows;
	}

	private List<String[]> watchRows(Minecraft minecraft, SpyTarget target) {
		String xyz = formatDouble(minecraft.player.getX()) + ", " + formatDouble(minecraft.player.getY()) + ", " + formatDouble(minecraft.player.getZ());
		String yawPitch = formatDouble(minecraft.player.getYRot()) + " / " + formatDouble(minecraft.player.getXRot());
		String selected = minecraft.player.getMainHandItem().isEmpty() ? "empty hand" : minecraft.player.getMainHandItem().getHoverName().getString();
		BlockPos playerPos = minecraft.player.blockPosition();
		String chunk = (playerPos.getX() >> 4) + ", " + (playerPos.getZ() >> 4) + " loaded";
		return List.of(
				new String[]{"Player XYZ", xyz},
				new String[]{"Yaw / Pitch", yawPitch},
				new String[]{"Selected", selected},
				new String[]{"Crosshair", target.title()},
				new String[]{"Target Kind", target.kind()},
				new String[]{"Chunk", chunk}
		);
	}

	private List<String[]> worldFlagRows(Minecraft minecraft, List<String[]> entities) {
		String weather = minecraft.level.isThundering() ? "Thunder" : minecraft.level.isRaining() ? "Rain" : "Clear";
		String biome = biomeName(minecraft);
		long gameTime = minecraft.level.getLevelData().getGameTime();
		long day = gameTime / 24000L;
		long dayTick = gameTime % 24000L;
		long hostile = entities.stream().filter(row -> "red".equals(row[5])).count();
		return List.of(
				new String[]{"Weather", weather, weather.equals("Clear") ? "green" : "blue"},
				new String[]{"Biome", biome, "blue"},
				new String[]{"Time", "Day " + day + " / " + dayTick, "purple"},
				new String[]{"Danger", hostile + " hostile nearby", hostile > 0 ? "red" : "green"}
		);
	}

	private String spyScriptSnippet(SpyTarget target, List<String[]> entities, String dimension) {
		String pos = target.position().replace("BlockPos: ", "").replace("Pos: ", "");
		return "import minescript as ms\n"
				+ "\n"
				+ "target_kind = " + pyString(target.kind()) + "\n"
				+ "target_name = " + pyString(target.title()) + "\n"
				+ "target_pos = " + pyString(pos) + "\n"
				+ "dimension_id = " + pyString(dimension) + "\n"
				+ "nearby_entities = " + entities.size() + "\n"
				+ "\n"
				+ "ms.echo(f\"WindowSpy target: {target_kind} {target_name} @ {target_pos}\")\n";
	}

	private void sendSpySnapshotToEditor(SpySnapshot snapshot) {
		page = Page.EDITOR;
		rememberPage(page);
		createNewScript("WindowSpySnapshot.py", snapshot.scriptSnippet());
	}

	private void copyToClipboard(String text) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.keyboardHandler != null) {
			minecraft.keyboardHandler.setClipboard(text == null ? "" : text);
		}
	}

	private String rowsToText(List<String[]> rows) {
		StringBuilder builder = new StringBuilder();
		for (String[] row : rows) {
			builder.append(String.join(" | ", row)).append('\n');
		}
		return builder.toString();
	}

	private String dimensionName(Level level) {
		try {
			return resourceKeyId(level.dimension().toString());
		} catch (Exception ignored) {
			return "unknown";
		}
	}

	private String shortDimensionName(Level level) {
		String name = dimensionName(level);
		int colon = name.indexOf(':');
		return colon >= 0 ? name.substring(colon + 1) : name;
	}

	private String lightAtPlayer(Minecraft minecraft) {
		try {
			return String.valueOf(minecraft.level.getMaxLocalRawBrightness(minecraft.player.blockPosition()));
		} catch (Throwable ignored) {
			return "-";
		}
	}

	private int totalRenderableEntities(Minecraft minecraft) {
		int count = 0;
		for (Entity ignored : minecraft.level.entitiesForRendering()) {
			count++;
		}
		return count;
	}

	private String biomeName(Minecraft minecraft) {
		try {
			return minecraft.level.getBiome(minecraft.player.blockPosition())
					.unwrapKey()
					.map(key -> resourceKeyId(key.toString()))
					.orElse("unknown");
		} catch (Throwable ignored) {
			return "unknown";
		}
	}

	private String resourceKeyId(String value) {
		int slash = value.lastIndexOf('/');
		int end = value.lastIndexOf(']');
		if (slash >= 0 && end > slash) {
			return value.substring(slash + 1, end).trim();
		}
		return value;
	}

	private boolean hostileType(String type) {
		String lower = type.toLowerCase(Locale.ROOT);
		return lower.contains("zombie") || lower.contains("skeleton") || lower.contains("creeper")
				|| lower.contains("spider") || lower.contains("enderman") || lower.contains("witch")
				|| lower.contains("slime") || lower.contains("phantom") || lower.contains("warden");
	}

	private String entityIcon(String type) {
		String lower = type.toLowerCase(Locale.ROOT);
		if (lower.contains("item")) {
			return "box-solid.png";
		}
		if (lower.contains("player")) {
			return "user-solid.png";
		}
		if (hostileType(type)) {
			return "broom-solid.png";
		}
		return "circle-info-solid.png";
	}

	private String compactStateName(String state) {
		int bracket = state.indexOf('[');
		return trimTo(bracket > 0 ? state.substring(0, bracket) : state, 34);
	}

	private String compactBlockState(String state) {
		int bracket = state.indexOf('[');
		if (bracket < 0 || !state.endsWith("]")) {
			return "default";
		}
		return trimTo(state.substring(bracket + 1, state.length() - 1), 34);
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private String trimTo(String value, int max) {
		if (value == null || value.length() <= max) {
			return value == null ? "" : value;
		}
		return value.substring(0, Math.max(0, max - 1)) + "...";
	}

	private String formatDouble(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private String pyString(String value) {
		return "\"" + safe(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private View settingsCenter() {
		LinearLayout center = column(18);

		LinearLayout panel = column(14);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));

		LinearLayout header = row(12);
		header.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout title = column(6);
		LinearLayout titleLine = row(10);
		titleLine.setGravity(Gravity.CENTER_VERTICAL);
		titleLine.addView(icon("gear-solid.png", PURPLE), new LinearLayout.LayoutParams(26, 26));
		titleLine.addView(label("Settings", 24, TEXT));
		title.addView(titleLine);
		title.addView(label("Tune Shulkr visuals, script files, editor behavior, shortcuts, and safety defaults.", 14, MUTED));
		header.addView(title, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		header.addView(settingsStatus("Theme", settingsConfig.theme().replace(" glass", ""), "eye-dropper-solid.png"), new LinearLayout.LayoutParams(148, 60));
		header.addView(settingsStatus("Config", "Synced", "check-double-solid.png"), new LinearLayout.LayoutParams(120, 60));
		header.addView(settingsStatus("User", currentProfile().displayName(), "user-solid.png"), new LinearLayout.LayoutParams(132, 60));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 74));

		LinearLayout toolbar = row(8);
		for (SettingsTab tab : SettingsTab.values()) {
			int width = tab == SettingsTab.CUSTOMIZATION ? 126
					: tab == SettingsTab.PYTHON ? 154
					: tab == SettingsTab.FILES ? 112
					: tab == SettingsTab.SHORTCUTS ? 88
					: 82;
			View chip = chip(tab.label, settingsTab == tab);
			chip.setOnClickListener(view -> {
				settingsTab = tab;
				renderShell();
			});
			toolbar.addView(chip, new LinearLayout.LayoutParams(width, 34));
		}
		panel.addView(toolbar, new LinearLayout.LayoutParams(match(), 38));

		panel.addView(settingsTabContent(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		center.addView(panel, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 70);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		center.addView(dock, dockLp);
		return center;
	}

	private View remoteViewerPage() {
		LinearLayout page = column(18);
		page.setPadding(20, 20, 20, 20);
		page.setBackground(panel(16));

		LinearLayout heading = row(14);
		heading.setGravity(Gravity.CENTER_VERTICAL);
		heading.addView(iconBadge("window-restore-regular.png", PURPLE, accentAlpha(48), 48, 12));
		LinearLayout title = column(5);
		title.addView(label("Remote Viewer", 24, TEXT));
		title.addView(label("View and control this Minecraft client from the Shulkr web dashboard.", 13, MUTED));
		heading.addView(title, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		page.addView(heading, new LinearLayout.LayoutParams(match(), 60));

		LinearLayout status = settingsCard("Connection status", "circle-info-solid.png");
		status.addView(settingsValueRow("Backend", FluxusAppState.get().backendOnline() ? "Connected" : "Offline"), new LinearLayout.LayoutParams(match(), 38));
		status.addView(settingsValueRow("Dashboard", "http://127.0.0.1:5177"), new LinearLayout.LayoutParams(match(), 38));
		status.addView(settingsValueRow("Feed", "FFmpeg window capture"), new LinearLayout.LayoutParams(match(), 38));
		page.addView(status, new LinearLayout.LayoutParams(match(), 170));

		LinearLayout help = settingsCard("How to remote view", "circle-info-solid.png");
		help.addView(label("1. Keep Minecraft and the backend running.\n2. Open the dashboard and choose Remote.\n3. Select this device, then press Restart feed if the picture is blank.\n4. Keep the Minecraft window visible; minimized windows cannot be captured by Windows.", 13, MUTED));
		LinearLayout actions = row(10);
		View open = primaryActionButton("window-restore-regular.png", "Open dashboard");
		open.setOnClickListener(view -> {
			try {
				Desktop.getDesktop().browse(URI.create("http://127.0.0.1:5177"));
			} catch (Exception ignored) {
				copyToClipboard("http://127.0.0.1:5177");
			}
		});
		actions.addView(open, new LinearLayout.LayoutParams(180, 42));
		View copy = toolbarButton("code-solid.png", "Copy address");
		copy.setOnClickListener(view -> copyToClipboard("http://127.0.0.1:5177"));
		actions.addView(copy, new LinearLayout.LayoutParams(160, 42));
		help.addView(actions, new LinearLayout.LayoutParams(match(), 48));
		page.addView(help, new LinearLayout.LayoutParams(match(), 230));
		return page;
	}

	private View settingsTabContent() {
		LinearLayout body = row(14);
		LinearLayout left = column(14);
		LinearLayout right = column(14);
		body.addView(left, new LinearLayout.LayoutParams(0, match(), 1.0F));
		body.addView(right, new LinearLayout.LayoutParams(0, match(), 1.0F));
		if (settingsTab == SettingsTab.CUSTOMIZATION) {
			left.addView(appearanceSettingsCard(), new LinearLayout.LayoutParams(match(), 270));
			left.addView(layoutSettingsCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
			right.addView(settingsPreviewCard(), new LinearLayout.LayoutParams(match(), 230));
			right.addView(settingsLiveStatusCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		} else if (settingsTab == SettingsTab.PYTHON) {
			left.addView(pythonInstallSettingsCard(), new LinearLayout.LayoutParams(match(), 416));
			left.addView(pythonMinescriptCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
			right.addView(pythonHelpCard(), new LinearLayout.LayoutParams(match(), 300));
			right.addView(settingsLiveStatusCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		} else if (settingsTab == SettingsTab.FILES) {
			left.addView(fileSettingsCard(), new LinearLayout.LayoutParams(match(), 300));
			left.addView(fileBackupSettingsCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
			right.addView(settingsLiveStatusCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		} else if (settingsTab == SettingsTab.EDITOR) {
			left.addView(editorSettingsCard(), new LinearLayout.LayoutParams(match(), 260));
			left.addView(editorLintSettingsCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
			right.addView(settingsLiveStatusCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		} else if (settingsTab == SettingsTab.SHORTCUTS) {
			left.addView(shortcutSettingsCard(), new LinearLayout.LayoutParams(match(), 300));
			right.addView(shortcutHelperCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		} else if (settingsTab == SettingsTab.PRIVACY) {
			left.addView(privacySettingsCard(), new LinearLayout.LayoutParams(match(), 270));
			right.addView(safetyRuntimeCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		} else {
			left.addView(advancedSettingsCard(), new LinearLayout.LayoutParams(match(), 340));
			right.addView(settingsLiveStatusCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		}
		return body;
	}

	private View appearanceSettingsCard() {
		LinearLayout card = settingsCard("Customization", "eye-dropper-solid.png");
		View theme = settingsDropdownRow("theme", "Theme", settingsConfig.theme(), THEME_OPTIONS, value -> {
			settingsConfig.setTheme(value);
			saveSettingsConfig("Theme set to " + value + ".");
		});
		card.addView(theme, new LinearLayout.LayoutParams(match(), 38));
		View accent = accentDropdownRow();
		card.addView(accent, new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSwitchRow("Stronger panel transparency", strongerPanelTransparency, checked -> {
			strongerPanelTransparency = checked;
			saveUiMessage("Panel transparency set to " + checked + ".");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSwitchRow("Animated hover highlight", animatedHoverHighlight, checked -> {
			animatedHoverHighlight = checked;
			saveUiMessage("Hover animation set to " + checked + ".");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSliderRow("Background visibility", "72%"), new LinearLayout.LayoutParams(match(), 46));
		return card;
	}

	private View layoutSettingsCard() {
		LinearLayout card = settingsCard("Layout", "border-all-solid.png");
		int rowHeight = settingsRowHeight();
		card.addView(settingsDropdownRow("density", "Density", settingsConfig.density(), DENSITY_OPTIONS, value -> {
			settingsConfig.setDensity(value);
			saveSettingsConfig("Density set to " + value + ".");
		}), new LinearLayout.LayoutParams(match(), rowHeight));
		card.addView(settingsDropdownRow("sidebar-width", "Sidebar width", settingsConfig.sidebarWidth(), SIDEBAR_WIDTH_OPTIONS, value -> {
			settingsConfig.setSidebarWidth(value);
			saveSettingsConfig("Sidebar width set to " + value + ".");
		}), new LinearLayout.LayoutParams(match(), rowHeight));
		card.addView(settingsDropdownRow("navigation-mode", "Navigation mode", settingsConfig.navigationMode(), NAVIGATION_MODE_OPTIONS, value -> {
			settingsConfig.setNavigationMode(value);
			saveSettingsConfig("Navigation mode set to " + value + ".");
		}), new LinearLayout.LayoutParams(match(), rowHeight));
		card.addView(settingsDropdownRow("content-width", "Content width", settingsConfig.contentWidth(), CONTENT_WIDTH_OPTIONS, value -> {
			settingsConfig.setContentWidth(value);
			saveSettingsConfig("Content width set to " + value + ".");
		}), new LinearLayout.LayoutParams(match(), rowHeight));
		card.addView(settingsDropdownRow("right-panel", "Right panel behaviour", settingsConfig.rightPanelBehaviour(), RIGHT_PANEL_OPTIONS, value -> {
			settingsConfig.setRightPanelBehaviour(value);
			rightPanelExpanded = false;
			saveSettingsConfig("Right panel behaviour set to " + value + ".");
		}), new LinearLayout.LayoutParams(match(), rowHeight));
		card.addView(settingsDropdownRow("page-spacing", "Page spacing", settingsConfig.pageSpacing(), PAGE_SPACING_OPTIONS, value -> {
			settingsConfig.setPageSpacing(value);
			saveSettingsConfig("Page spacing set to " + value + ".");
		}), new LinearLayout.LayoutParams(match(), rowHeight));
		card.addView(settingsDropdownRow("header-behaviour", "Header behaviour", settingsConfig.headerBehaviour(), HEADER_BEHAVIOUR_OPTIONS, value -> {
			settingsConfig.setHeaderBehaviour(value);
			headerHidden = false;
			saveSettingsConfig("Header behaviour set to " + value + ".");
		}), new LinearLayout.LayoutParams(match(), rowHeight));
		return card;
	}

	private View shortcutSettingsCard() {
		LinearLayout card = settingsCard("Shortcuts", "keyboard");
		card.addView(settingsKeyRow("Open Shulkr", SHORTCUT_OPEN_UI, "Open or close the Shulkr client"), new LinearLayout.LayoutParams(match(), 40));
		card.addView(settingsKeyRow("Overlay edit mode", SHORTCUT_OVERLAY_EDIT, "Toggle the live HUD layout editor"), new LinearLayout.LayoutParams(match(), 40));
		card.addView(settingsKeyRow("Run last script", SHORTCUT_RUN_LAST, "Run the most recently started Minescript"), new LinearLayout.LayoutParams(match(), 40));
		View clear = settingsActionRow("Clear run-last history", "clock-rotate-left-solid.png");
		clear.setOnClickListener(view -> {
			TritonUIClient.rememberLastScript("");
			settingsMessage = "Run-last history cleared.";
			renderShell();
		});
		card.addView(clear, new LinearLayout.LayoutParams(match(), 42));
		return card;
	}

	private View fileSettingsCard() {
		LinearLayout card = settingsCard("File Settings", "folder-open-solid.png");
		View scriptFolder = settingsValueRow("Script folder", scriptDir.toString());
		scriptFolder.setOnClickListener(view -> openScriptFolder());
		card.addView(scriptFolder, new LinearLayout.LayoutParams(match(), 38));
		View templateFolder = settingsValueRow("Template folder", scriptDir.resolve("templates").toString());
		templateFolder.setOnClickListener(view -> openFolder(scriptDir.resolve("templates"), "Template folder"));
		card.addView(templateFolder, new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSwitchRow("Autosave scripts", settingsConfig.autosaveScripts(), checked -> {
			settingsConfig.setAutosaveScripts(checked);
			saveSettingsConfig("Autosave scripts updated.");
		}), new LinearLayout.LayoutParams(match(), 38));
		String selectedRelative = selectedScriptRelativePath();
		boolean canFlagModule = selectedScript != null && Files.isRegularFile(selectedScript);
		card.addView(settingsSwitchRow("Selected script is module", canFlagModule && FluxusAppState.get().isScriptModule(selectedRelative), checked -> {
			if (!canFlagModule) {
				saveUiMessage("Open a script in the editor before marking it as a module.");
				return;
			}
			FluxusAppState.get().setScriptModule(selectedRelative, checked);
			saveUiMessage((checked ? "Marked " : "Unmarked ") + selectedScript.getFileName() + " as a module.");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSwitchRow("Create backups before run", createBackupsBeforeRun, checked -> {
			createBackupsBeforeRun = checked;
			saveUiMessage("Backups before run set to " + checked + ".");
		}), new LinearLayout.LayoutParams(match(), 38));
		View backup = settingsSliderRow("Backup history", settingsConfig.backupHistory() + " files");
		backup.setOnClickListener(view -> {
			settingsConfig.setBackupHistory(settingsConfig.backupHistory() == 25 ? 50 : 25);
			saveSettingsConfig("Backup history set to " + settingsConfig.backupHistory() + ".");
		});
		card.addView(backup, new LinearLayout.LayoutParams(match(), 46));
		return card;
	}

	private View editorSettingsCard() {
		LinearLayout card = settingsCard("Editor", "code-solid.png");
		card.addView(settingsSwitchRow("Python autocomplete", settingsConfig.inlineAutocomplete(), checked -> {
			settingsConfig.setInlineAutocomplete(checked);
			saveSettingsConfig("Python autocomplete updated.");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSwitchRow("Ruff diagnostics", settingsConfig.ruffDiagnostics(), checked -> {
			settingsConfig.setRuffDiagnostics(checked);
			saveSettingsConfig("Ruff diagnostics updated.");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSwitchRow("Inline ghost text", settingsConfig.inlineAutocomplete(), checked -> {
			settingsConfig.setInlineAutocomplete(checked);
			saveSettingsConfig("Inline ghost text updated.");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsDropdownRow("indentation", "Indentation", "Spaces: 4", new String[]{"Spaces: 4", "Spaces: 2", "Tabs"},
				value -> saveUiMessage("Indentation set to " + value + ".")), new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View privacySettingsCard() {
		LinearLayout card = settingsCard("Privacy & Safety", "circle-info-solid.png");
		card.addView(settingsSwitchRow("Hide player names in captures", hidePlayerNamesInCaptures, checked -> {
			hidePlayerNamesInCaptures = checked;
			saveUiMessage("Capture privacy set to " + checked + ".");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSwitchRow("Confirm destructive scripts", settingsConfig.confirmDestructiveScripts(), checked -> {
			settingsConfig.setConfirmDestructiveScripts(checked);
			saveSettingsConfig("Destructive script confirmation updated.");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsSwitchRow("Block network by default", settingsConfig.blockNetworkByDefault(), checked -> {
			settingsConfig.setBlockNetworkByDefault(checked);
			saveSettingsConfig("Network blocking updated.");
		}), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsDropdownRow("telemetry", "Telemetry", "Local only", new String[]{"Local only", "Off", "Diagnostics only"},
				value -> saveUiMessage("Telemetry set to " + value + ".")), new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View pythonInstallSettingsCard() {
		LinearLayout card = settingsCard("Python Installation", "code-solid.png");
		String configured = readMinescriptConfigValue("python");
		card.addView(settingsValueRow("Configured Python", configured.isBlank() ? "Not set" : configured), new LinearLayout.LayoutParams(match(), 42));
		card.addView(settingsValueRow("Detected Python", detectedPython.isBlank() ? "Not detected yet" : detectedPython), new LinearLayout.LayoutParams(match(), 42));
		card.addView(settingsValueRow("Minescript config", minescriptConfigFile.toString()), new LinearLayout.LayoutParams(match(), 42));
		View find = settingsActionRow("Auto-detect Python", "magnifying-glass-solid.png");
		find.setOnClickListener(view -> detectPythonAndSave());
		card.addView(find, new LinearLayout.LayoutParams(match(), 42));
		View test = settingsActionRow("Test configured Python", "play-solid.png");
		test.setOnClickListener(view -> testConfiguredPython());
		card.addView(test, new LinearLayout.LayoutParams(match(), 42));
		View install = settingsActionRow("Open Python installer", "download-solid.png");
		install.setOnClickListener(view -> openPythonInstaller());
		card.addView(install, new LinearLayout.LayoutParams(match(), 42));
		return card;
	}

	private View pythonMinescriptCard() {
		LinearLayout card = settingsCard("Minescript Runtime", "clipboard-solid.png");
		card.addView(settingsValueRow("command_path", readMinescriptConfigValue("command_path").isBlank() ? "system/exec;" : readMinescriptConfigValue("command_path")), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsValueRow("pyjinn_import_path", readMinescriptConfigValue("pyjinn_import_path").isBlank() ? "system/pyj;" : readMinescriptConfigValue("pyjinn_import_path")), new LinearLayout.LayoutParams(match(), 38));
		View reset = settingsActionRow("Repair Minescript config", "arrows-rotate-solid.png");
		reset.setOnClickListener(view -> repairMinescriptConfig());
		card.addView(reset, new LinearLayout.LayoutParams(match(), 42));
		View open = settingsActionRow("Open Minescript folder", "folder-open-solid.png");
		open.setOnClickListener(view -> openScriptFolder());
		card.addView(open, new LinearLayout.LayoutParams(match(), 42));
		return card;
	}

	private View pythonHelpCard() {
		LinearLayout card = settingsCard("Python Help", "circle-info-solid.png");
		card.addView(label("The Windows Store python.exe shim causes Minescript error code 9009. Use Auto-detect to find a real Python install and write it into minescript/config.txt.", 12, MUTED), new LinearLayout.LayoutParams(match(), wrap()));
		card.addView(settingsValueRow("Recommended", "Python 3.11+ from python.org"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsValueRow("Current status", pythonStatusLabel()), new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View settingsPreviewCard() {
		LinearLayout card = settingsCard("Live Preview", "eye-dropper-solid.png");
		card.addView(label("Theme, accent, and layout controls save instantly. Visual-only controls are logged until the renderer exposes runtime restyle hooks.", 12, MUTED));
		card.addView(settingsValueRow("Theme", settingsConfig.theme()), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsValueRow("Accent", settingsConfig.accent()), new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View settingsLiveStatusCard() {
		LinearLayout card = settingsCard("Status", "check-double-solid.png");
		card.addView(label(settingsMessage, 12, settingsMessage.toLowerCase(Locale.ROOT).contains("failed") ? Color.argb(255, 255, 120, 140) : GREEN));
		card.addView(settingsValueRow("Local config", FluxusConfig.path().toString()), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsValueRow("Minescript config", minescriptConfigFile.toString()), new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View fileBackupSettingsCard() {
		LinearLayout card = settingsCard("Backups", "folder-open-solid.png");
		card.addView(settingsValueRow("Export folder", scriptDir.resolve("exports").toString()), new LinearLayout.LayoutParams(match(), 38));
		View open = settingsActionRow("Open script folder", "folder-open-solid.png");
		open.setOnClickListener(view -> openScriptFolder());
		card.addView(open, new LinearLayout.LayoutParams(match(), 42));
		View export = settingsActionRow("Export config snapshot", "download-solid.png");
		export.setOnClickListener(view -> exportConfigSnapshot());
		card.addView(export, new LinearLayout.LayoutParams(match(), 42));
		return card;
	}

	private View editorLintSettingsCard() {
		LinearLayout card = settingsCard("Linting", "broom-solid.png");
		card.addView(settingsValueRow("Ruff", settingsConfig.ruffDiagnostics() ? "Enabled" : "Disabled"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsValueRow("Autocomplete", settingsConfig.inlineAutocomplete() ? "Enabled" : "Disabled"), new LinearLayout.LayoutParams(match(), 38));
		View test = settingsActionRow("Test Ruff command", "play-solid.png");
		test.setOnClickListener(view -> testRuffCommand());
		card.addView(test, new LinearLayout.LayoutParams(match(), 42));
		return card;
	}

	private View shortcutHelperCard() {
		LinearLayout card = settingsCard("Shortcut Notes", "keyboard");
		card.addView(label("Click any shortcut row, then press the key you want. Press Escape, Backspace, or Delete to clear a binding.", 12, MUTED));
		card.addView(settingsValueRow("Last script", lastRunScriptLabel()), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsValueRow("Palette", "Ctrl + K"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(settingsValueRow("Rename in editor", "F2"), new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View safetyRuntimeCard() {
		LinearLayout card = settingsCard("Runtime Safety", "check-double-solid.png");
		card.addView(label("These settings are persisted and ready for script-approval checks. Minescript itself still executes local scripts when you press Run.", 12, MUTED));
		return card;
	}

	private View advancedSettingsCard() {
		LinearLayout card = settingsCard("Advanced", "gear-solid.png");
		View repair = settingsActionRow("Repair Minescript config", "arrows-rotate-solid.png");
		repair.setOnClickListener(view -> repairMinescriptConfig());
		card.addView(repair, new LinearLayout.LayoutParams(match(), 42));
		View cache = settingsActionRow("Clear UI cache", "broom-solid.png");
		cache.setOnClickListener(view -> clearUiCache());
		card.addView(cache, new LinearLayout.LayoutParams(match(), 42));
		View defaults = settingsActionRow("Reset settings to defaults", "check-double-solid.png");
		defaults.setOnClickListener(view -> resetSettingsToDefaults());
		card.addView(defaults, new LinearLayout.LayoutParams(match(), 42));
		return card;
	}

	private View settingsSidePanel() {
		LinearLayout side = column(14);
		side.addView(settingsProfilePanel(), new LinearLayout.LayoutParams(match(), 270));
		side.addView(settingsConfigPanel(), new LinearLayout.LayoutParams(match(), 330));
		side.addView(settingsDangerPanel(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		return side;
	}

	private View settingsProfilePanel() {
		FluxusAppState.Profile profileData = currentProfile();
		boolean online = FluxusAppState.get().backendOnline();
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("user-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Active User", 16, TEXT));
		panel.addView(title, new LinearLayout.LayoutParams(match(), 30));
		LinearLayout hero = row(14);
		hero.setGravity(Gravity.CENTER_VERTICAL);
		hero.addView(iconBadge("user-solid.png", GREEN, Color.argb(120, 28, 76, 48), 58, 12));
		LinearLayout copy = column(5);
		copy.addView(label(profileData.displayName(), 18, TEXT));
		copy.addView(label(profileData.tier() + " user", 12, PURPLE));
		copy.addView(label(online ? "* Connected to local client" : "* Waiting for backend connection", 12, online ? GREEN : MUTED));
		hero.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		panel.addView(hero, new LinearLayout.LayoutParams(match(), 68));
		panel.addView(watchRow("Loaded config", FluxusConfig.path().getFileName().toString()), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Last saved", configLastSavedLabel()), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("UI scale", profileData.uiScale() + "%"), new LinearLayout.LayoutParams(match(), 30));
		return panel;
	}

	private View settingsConfigPanel() {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("clipboard-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Config Tools", 16, TEXT));
		panel.addView(title, new LinearLayout.LayoutParams(match(), 30));
		View export = settingsActionRow("Export config", "download-solid.png");
		export.setOnClickListener(view -> exportConfigSnapshot());
		panel.addView(export, new LinearLayout.LayoutParams(match(), 42));
		View importConfig = settingsActionRow("Import config", "folder-plus-solid.png");
		importConfig.setOnClickListener(view -> saveUiMessage("Import is ready: place shulkr-client.json in the config folder, then reload config."));
		panel.addView(importConfig, new LinearLayout.LayoutParams(match(), 42));
		View openConfig = settingsActionRow("Open config folder", "folder-open-solid.png");
		openConfig.setOnClickListener(view -> openFolder(FluxusConfig.path().getParent(), "Config folder"));
		panel.addView(openConfig, new LinearLayout.LayoutParams(match(), 42));
		View sync = settingsActionRow("Reload config", "arrows-rotate-solid.png");
		sync.setOnClickListener(view -> {
			settingsConfig = FluxusConfig.load();
			saveUiMessage("Config reloaded from disk.");
		});
		panel.addView(sync, new LinearLayout.LayoutParams(match(), 42));
		LinearLayout status = row(8);
		status.setGravity(Gravity.CENTER_VERTICAL);
		status.addView(icon("check-solid.png", GREEN), new LinearLayout.LayoutParams(16, 16));
		status.addView(label("All settings saved locally", 12, MUTED));
		panel.addView(status, new LinearLayout.LayoutParams(match(), 30));
		return panel;
	}

	private View settingsDangerPanel() {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		panel.addView(label("Maintenance", 16, TEXT));
		panel.addView(label("Reset cached UI state, recover defaults, or clean old script backups.", 12, MUTED));
		View clear = settingsActionRow("Clear UI cache", "broom-solid.png");
		clear.setOnClickListener(view -> clearUiCache());
		panel.addView(clear, new LinearLayout.LayoutParams(match(), 42));
		View repair = settingsActionRow("Repair missing icons", "puzzle-piece-solid.png");
		repair.setOnClickListener(view -> repairIconRegistry());
		panel.addView(repair, new LinearLayout.LayoutParams(match(), 42));
		TextView reset = label("Reset To Defaults", 13, Color.argb(255, 255, 72, 96));
		reset.setGravity(Gravity.CENTER);
		makeHover(reset, round(Color.argb(70, 120, 32, 44), 8, Color.argb(72, 255, 72, 96)),
				round(Color.argb(110, 145, 40, 54), 8, Color.argb(130, 255, 72, 96)));
		reset.setOnClickListener(view -> resetSettingsToDefaults());
		panel.addView(reset, new LinearLayout.LayoutParams(match(), 38));
		return panel;
	}

	private LinearLayout settingsCard(String title, String iconFile) {
		LinearLayout card = column(10);
		card.setPadding(18, 16, 18, 16);
		card.setBackground(round(Color.argb(100, 15, 21, 34), 12, Color.argb(76, 105, 116, 150)));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		String resolvedIcon = iconFile.equals("keyboard") ? "border-all-solid.png" : iconFile;
		header.addView(icon(resolvedIcon, PURPLE), new LinearLayout.LayoutParams(17, 17));
		header.addView(label(title, 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		card.addView(header, new LinearLayout.LayoutParams(match(), 34));
		return card;
	}

	private View settingsStatus(String name, String value, String iconFile) {
		LinearLayout status = row(6);
		status.setGravity(Gravity.CENTER_VERTICAL);
		status.setPadding(10, 8, 8, 8);
		status.setBackground(round(Color.argb(104, 15, 21, 34), 10, Color.argb(70, 105, 116, 150)));
		LinearLayout copy = column(2);
		copy.addView(label(name, 11, MUTED));
		TextView valueLabel = label(value, 15, TEXT);
		valueLabel.setSingleLine(true);
		copy.addView(valueLabel);
		status.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		status.addView(iconBadge(iconFile, PURPLE, accentAlpha(70), 30, 8));
		return status;
	}

	private View settingsSelectRow(String key, String name, String value) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(116, wrap()));
		TextView valueLabel = label(value, 12, TEXT);
		valueLabel.setSingleLine(true);
		row.addView(valueLabel, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		ImageView arrow = icon("chevron-down-solid.png", FAINT);
		row.addView(arrow, new LinearLayout.LayoutParams(13, 13));
		if (isDropdownOpen(key)) {
			currentDropdownArrow = arrow;
			animateDropdownArrow(arrow, true);
		}
		return row;
	}

	private View settingsValueRow(String name, String value) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(116, wrap()));
		TextView valueLabel = label(value, 12, TEXT);
		valueLabel.setSingleLine(true);
		row.addView(valueLabel, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private View settingsDropdownRow(String key, String name, String value, String[] options, Consumer<String> onSelected) {
		LinearLayout box = column(6);
		View select = settingsSelectRow(key, name, value);
		select.setOnClickListener(view -> {
			toggleDropdown(key, view, Math.max(260, view.getWidth()));
		});
		box.addView(select, new LinearLayout.LayoutParams(match(), 38));
		return box;
	}

	private View accentDropdownRow() {
		LinearLayout box = column(6);
		View select = settingsSelectRow("accent", "Accent color", settingsConfig.accent());
		select.setOnClickListener(view -> {
			toggleDropdown("accent", view, Math.max(260, view.getWidth()));
		});
		box.addView(select, new LinearLayout.LayoutParams(match(), 38));
		return box;
	}

	private View dropdownMenu(String selected, String[] options, Consumer<String> onSelected, boolean swatches) {
		LinearLayout menu = column(4);
		menu.setPadding(6, 6, 6, 6);
		menu.setBackground(dropdownSurface(10));
		for (String option : options) {
			LinearLayout row = row(8);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(10, 0, 10, 0);
			boolean active = option.equals(selected);
			makeHover(row, active ? round(Color.argb(132, red(PURPLE), green(PURPLE), blue(PURPLE)), 8, Color.argb(110, red(PURPLE), green(PURPLE), blue(PURPLE)))
							: round(Color.TRANSPARENT, 8, 0),
					round(Color.argb(110, red(PURPLE), green(PURPLE), blue(PURPLE)), 8, STROKE_HOVER));
			if (swatches) {
				View swatch = new View(requireContext());
				int color = accentColor(option);
				swatch.setBackground(round(color, 7, Color.argb(120, 247, 244, 255)));
				row.addView(swatch, new LinearLayout.LayoutParams(14, 14));
			}
			row.addView(label(option, 12, active ? TEXT : MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
			if (active) {
				row.addView(icon("check-solid.png", TEXT), new LinearLayout.LayoutParams(13, 13));
			}
			row.setOnClickListener(view -> closeFloatingDropdown(() -> onSelected.accept(option)));
			menu.addView(row, new LinearLayout.LayoutParams(match(), 28));
		}
		return menu;
	}

	private boolean isDropdownOpen(String key) {
		return openDropdownKey.equals(key);
	}

	private int dropdownOptionsHeight(int options) {
		return 12 + options * 32;
	}

	private View settingsSwitchRow(String name, boolean checked) {
		return settingsSwitchRow(name, checked, null);
	}

	private View settingsSwitchRow(String name, boolean checked, Consumer<Boolean> onChanged) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		Switch toggle = animatedSwitch(checked, 50, onChanged, true);
		row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
		row.addView(toggle, new LinearLayout.LayoutParams(62, 38));
		return row;
	}

	private View settingsSliderRow(String name, String value) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(130, wrap()));
		LinearLayout track = new LinearLayout(requireContext());
		track.setOrientation(LinearLayout.HORIZONTAL);
		track.setBackground(round(Color.argb(100, 18, 24, 39), 7, Color.argb(54, 105, 116, 150)));
		View fill = new View(requireContext());
		fill.setBackground(round(accentAlpha(210), 7, 0));
		track.addView(fill, new LinearLayout.LayoutParams(0, 12, 0.72F));
		track.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, 12, 0.28F));
		row.addView(track, new LinearLayout.LayoutParams(0, 12, 1.0F));
		row.addView(label(value, 12, TEXT), new LinearLayout.LayoutParams(62, wrap()));
		return row;
	}

	private View settingsKeyRow(String action, String shortcutAction, String helperText) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(12, 8, 12, 8);
		boolean capturing = shortcutAction.equals(capturingShortcutAction);
		makeHover(row,
				capturing ? round(Color.argb(146, red(PURPLE), green(PURPLE), blue(PURPLE)), 10, STROKE_HOVER) : round(Color.argb(98, 18, 24, 39), 10, Color.argb(48, 105, 116, 150)),
				round(Color.argb(150, 27, 33, 53), 10, STROKE_HOVER));
		row.addView(label(action, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		TextView helper = label(capturing ? "Press key..." : helperText, 11, capturing ? TEXT : FAINT);
		helper.setSingleLine(true);
		row.addView(helper, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		TextView keycap = keycap(capturing ? "..." : keybindLabel(TritonUIClient.shortcutKey(shortcutAction)));
		row.addView(keycap, new LinearLayout.LayoutParams(96, 24));
		row.setOnClickListener(view -> {
			capturingShortcutAction = shortcutAction;
			settingsMessage = "Press a key for " + shortcutDisplayName(shortcutAction) + ".";
			renderShell();
		});
		return row;
	}

	private View settingsActionRow(String text, String iconFile) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(12, 0, 12, 0);
		makeHover(row, round(Color.argb(98, 18, 24, 39), 8, Color.argb(48, 105, 116, 150)),
				round(Color.argb(145, 27, 33, 53), 8, STROKE_HOVER));
		row.addView(icon(iconFile, PURPLE), new LinearLayout.LayoutParams(16, 16));
		row.addView(label(text, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(icon("arrow-right-solid.png", FAINT), new LinearLayout.LayoutParams(13, 13));
		return row;
	}

	private void cycleTheme() {
		String next = nextOption(THEME_OPTIONS, settingsConfig.theme());
		settingsConfig.setTheme(next);
		saveSettingsConfig("Theme set to " + next + ".");
	}

	private void cycleAccent() {
		String next = nextOption(ACCENT_OPTIONS, settingsConfig.accent());
		settingsConfig.setAccent(next);
		saveSettingsConfig("Accent set to " + next + ".");
	}

	private String nextOption(String[] options, String current) {
		for (int i = 0; i < options.length; i++) {
			if (options[i].equals(current)) {
				return options[(i + 1) % options.length];
			}
		}
		return options[0];
	}

	private void saveSettingsConfig(String message) {
		applyConfigPalette();
		settingsConfig.save();
		capturingShortcutAction = "";
		settingsMessage = message;
		renderShell();
	}

	private void saveUiMessage(String message) {
		settingsMessage = message;
		renderShell();
	}

	private void clearUiCache() {
		openDropdownKey = "";
		editorFunctionCache.clear();
		localCompletionCache = List.of();
		localCompletionDirty = true;
		completionRemainder = "";
		selectedEditorItems.clear();
		if (selectedScript != null) {
			selectedEditorItems.add(selectedScript);
		}
		refreshEditorScripts();
		settingsMessage = "Cleared transient UI, lint, and completion cache.";
		renderShell();
	}

	private void repairIconRegistry() {
		Path resourceIcons = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "assets", "triton-ui", "textures", "icons");
		Path looseIcons = Paths.get(System.getProperty("user.dir"), "icons");
		int count = countFiles(resourceIcons) + countFiles(looseIcons);
		settingsMessage = count > 0 ? "Icon registry sees " + count + " local icon assets." : "No local icons found. Check assets/triton-ui/textures/icons.";
		renderShell();
	}

	private int countFiles(Path root) {
		if (root == null || Files.notExists(root)) {
			return 0;
		}
		try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
			return (int) paths.filter(Files::isRegularFile).count();
		} catch (IOException ignored) {
			return 0;
		}
	}

	private void rememberPage(Page next) {
		if (settingsConfig != null && settingsConfig.rememberLastPage()) {
			settingsConfig.setDefaultPage(next.name());
			settingsConfig.save();
		}
	}

	private Page pageFromConfig(String name) {
		if (name == null || name.isBlank()) {
			return Page.DASHBOARD;
		}
		try {
			return Page.valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			for (Page candidate : Page.values()) {
				if (candidate.name().replace("_", "").equalsIgnoreCase(name.replace(" ", ""))) {
					return candidate;
				}
			}
			return Page.DASHBOARD;
		}
	}

	private void applyConfigPalette() {
		String accent = settingsConfig == null ? "Shulkr purple" : settingsConfig.accent();
		PURPLE = accentColor(accent);
		PURPLE_DARK = Color.argb(255, Math.max(0, red(PURPLE) - 54), Math.max(0, green(PURPLE) - 42), Math.max(0, blue(PURPLE) - 50));
		PURPLE_SOFT = Color.argb(150, red(PURPLE), green(PURPLE), blue(PURPLE));
		STROKE_HOVER = Color.argb(150, red(PURPLE), green(PURPLE), blue(PURPLE));
		TEXT = Color.argb(255, 247, 244, 255);
		MUTED = Color.argb(230, 171, 179, 204);
		FAINT = Color.argb(180, 136, 145, 174);
		GREEN = Color.argb(255, 62, 216, 99);

		String theme = settingsConfig == null ? "Dark glass" : settingsConfig.theme();
		if (theme.equals("Frontend Nova")) {
			// Mirrors the browser dashboard tokens in web-client/src/styles.css.
			BACKDROP = Color.argb(246, 8, 5, 12);
			PANEL = Color.argb(232, 15, 10, 24);
			PANEL_DARK = Color.argb(246, 8, 5, 12);
			CARD = Color.argb(218, 22, 16, 34);
			CARD_HOVER = Color.argb(232, 32, 24, 50);
			STROKE = Color.argb(74, 139, 45, 255);
			TEXT = Color.argb(255, 245, 242, 255);
			MUTED = Color.argb(238, 157, 148, 176);
			FAINT = Color.argb(205, 94, 86, 112);
			GREEN = Color.argb(255, 61, 220, 132);
		} else if (theme.equals("Deep transparent")) {
			BACKDROP = Color.argb(148, 4, 9, 20);
			PANEL = Color.argb(108, 10, 15, 27);
			PANEL_DARK = Color.argb(132, 7, 12, 22);
			CARD = Color.argb(92, 15, 21, 34);
			CARD_HOVER = Color.argb(136, 24, 30, 48);
			STROKE = Color.argb(84, 105, 116, 150);
		} else if (theme.equals("High contrast")) {
			BACKDROP = Color.argb(238, 3, 5, 12);
			PANEL = Color.argb(242, 6, 9, 18);
			PANEL_DARK = Color.argb(250, 2, 5, 12);
			CARD = Color.argb(232, 9, 14, 26);
			CARD_HOVER = Color.argb(248, 20, 25, 43);
			STROKE = Color.argb(132, 105, 116, 150);
		} else if (theme.equals("Blue dusk")) {
			BACKDROP = Color.argb(186, 4, 10, 23);
			PANEL = Color.argb(144, 11, 24, 42);
			PANEL_DARK = Color.argb(170, 6, 15, 30);
			CARD = Color.argb(122, 14, 29, 48);
			CARD_HOVER = Color.argb(170, 24, 42, 68);
			STROKE = Color.argb(112, 105, 142, 190);
		} else {
			BACKDROP = Color.argb(184, 6, 4, 12);
			PANEL = Color.argb(228, 10, 8, 22);
			PANEL_DARK = Color.argb(240, 7, 5, 16);
			CARD = Color.argb(210, 18, 10, 31);
			CARD_HOVER = Color.argb(235, 28, 14, 42);
			STROKE = Color.argb(104, 102, 68, 156);
			MUTED = Color.argb(232, 183, 168, 214);
			FAINT = Color.argb(176, 143, 126, 178);
		}
	}

	private FluxusAppState.Profile currentProfile() {
		return FluxusAppState.get().profile().normalized();
	}

	private String shortcutDisplayName(String action) {
		return switch (action) {
			case SHORTCUT_OPEN_UI -> "Open Shulkr";
			case SHORTCUT_OVERLAY_EDIT -> "Overlay edit mode";
			case SHORTCUT_RUN_LAST -> "Run last script";
			default -> "Shortcut";
		};
	}

	private String lastRunScriptLabel() {
		String path = TritonUIClient.lastRunScriptPath();
		if (path == null || path.isBlank()) {
			return "Nothing recorded yet";
		}
		Path saved = Path.of(path.replace('\\', '/'));
		return saved.getFileName() == null ? path : saved.getFileName().toString();
	}

	private String configLastSavedLabel() {
		try {
			Instant modified = Files.getLastModifiedTime(FluxusConfig.path()).toInstant();
			long seconds = Math.max(0, Duration.between(modified, Instant.now()).getSeconds());
			if (seconds < 10) {
				return "just now";
			}
			if (seconds < 60) {
				return seconds + "s ago";
			}
			long minutes = seconds / 60;
			if (minutes < 60) {
				return minutes + "m ago";
			}
			long hours = minutes / 60;
			if (hours < 24) {
				return hours + "h ago";
			}
			return (hours / 24) + "d ago";
		} catch (IOException ignored) {
			return "unknown";
		}
	}

	private int accentColor(String name) {
		return switch (name) {
			case "Nova purple" -> Color.argb(255, 157, 77, 255);
			case "Soft blue" -> Color.argb(255, 93, 169, 255);
			case "Electric cyan" -> Color.argb(255, 46, 218, 255);
			case "Emerald" -> Color.argb(255, 62, 216, 132);
			case "Rose" -> Color.argb(255, 255, 92, 148);
			default -> Color.argb(255, 163, 88, 255);
		};
	}

	private String readMinescriptConfigValue(String key) {
		if (minescriptConfigFile == null || Files.notExists(minescriptConfigFile)) {
			return "";
		}
		try {
			for (String line : Files.readAllLines(minescriptConfigFile, StandardCharsets.UTF_8)) {
				String trimmed = line.trim();
				if (trimmed.startsWith("#") || !trimmed.startsWith(key)) {
					continue;
				}
				int equals = trimmed.indexOf('=');
				if (equals < 0) {
					continue;
				}
				String value = trimmed.substring(equals + 1).trim();
				if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
					value = value.substring(1, value.length() - 1);
				}
				return value;
			}
		} catch (IOException e) {
			settingsMessage = "Failed to read Minescript config: " + e.getMessage();
		}
		return "";
	}

	private void writeMinescriptConfigValue(String key, String value) {
		try {
			ensureMinescriptConfig();
			List<String> lines = new ArrayList<>(Files.readAllLines(minescriptConfigFile, StandardCharsets.UTF_8));
			boolean replaced = false;
			for (int i = 0; i < lines.size(); i++) {
				String trimmed = lines.get(i).trim();
				if (!trimmed.startsWith("#") && trimmed.startsWith(key)) {
					lines.set(i, key + "=\"" + value.replace("\"", "\\\"") + "\"");
					replaced = true;
					break;
				}
			}
			if (!replaced) {
				lines.add(key + "=\"" + value.replace("\"", "\\\"") + "\"");
			}
			Files.write(minescriptConfigFile, lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			settingsMessage = "Failed to write Minescript config: " + e.getMessage();
		}
	}

	private void detectPythonAndSave() {
		detectedPython = detectPythonExecutable();
		if (detectedPython.isBlank()) {
			settingsMessage = "Python was not found. Install Python, then run auto-detect again.";
		} else {
			writeMinescriptConfigValue("python", detectedPython);
			settingsMessage = "Python configured: " + detectedPython;
		}
		renderShell();
	}

	private String detectPythonExecutable() {
		for (String[] command : new String[][]{
				{"py", "-3", "-c", "import sys; print(sys.executable)"},
				{"python", "-c", "import sys; print(sys.executable)"},
				{"python3", "-c", "import sys; print(sys.executable)"}
		}) {
			String result = runProcessForFirstLine(command);
			if (!result.isBlank() && Files.exists(Paths.get(result))) {
				return result;
			}
		}
		String local = System.getenv("LOCALAPPDATA");
		if (local != null) {
			try (var stream = Files.walk(Paths.get(local, "Programs", "Python"), 3)) {
				return stream.filter(path -> path.getFileName().toString().equalsIgnoreCase("python.exe"))
						.findFirst()
						.map(Path::toString)
						.orElse("");
			} catch (IOException ignored) {
			}
		}
		return "";
	}

	private String runProcessForFirstLine(String[] command) {
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			if (!process.waitFor(3, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return "";
			}
			if (process.exitValue() != 0) {
				return "";
			}
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			int newline = output.indexOf('\n');
			return newline >= 0 ? output.substring(0, newline).trim() : output;
		} catch (Exception ignored) {
			return "";
		}
	}

	private void testConfiguredPython() {
		String python = readMinescriptConfigValue("python");
		if (python.isBlank()) {
			settingsMessage = "No Python configured. Run Auto-detect Python first.";
		} else {
			String version = runProcessForFirstLine(new String[]{python, "-c", "import sys; print(sys.version.split()[0])"});
			settingsMessage = version.isBlank() ? "Configured Python failed to run." : "Python OK: " + version;
		}
		renderShell();
	}

	private String pythonStatusLabel() {
		String python = readMinescriptConfigValue("python");
		if (python.isBlank()) {
			return "Not configured";
		}
		if (python.contains("WindowsApps")) {
			return "Store shim detected";
		}
		return "Configured";
	}

	private void openPythonInstaller() {
		try {
			Desktop.getDesktop().browse(URI.create("https://www.python.org/downloads/windows/"));
			settingsMessage = "Opened Python installer page.";
		} catch (Exception e) {
			settingsMessage = "Open installer failed: " + e.getMessage();
		}
		renderShell();
	}

	private void repairMinescriptConfig() {
		String python = readMinescriptConfigValue("python");
		if (python.contains("WindowsApps")) {
			python = "";
		}
		if (python.isBlank()) {
			String detected = detectPythonExecutable();
			if (!detected.isBlank()) {
				python = detected;
				detectedPython = detected;
			}
		}
		writeMinescriptConfigValue("command_path", "system/exec;");
		writeMinescriptConfigValue("pyjinn_import_path", "system/pyj;");
		writeMinescriptConfigValue("python", python);
		settingsMessage = python.isBlank() ? "Config repaired. Python still needs installing." : "Config repaired with Python: " + python;
		renderShell();
	}

	private void exportConfigSnapshot() {
		try {
			Path exportDir = scriptDir.resolve("exports");
			Files.createDirectories(exportDir);
			Files.copy(FluxusConfig.path(), exportDir.resolve("shulkr-client.json"), StandardCopyOption.REPLACE_EXISTING);
			Files.copy(minescriptConfigFile, exportDir.resolve("minescript-config.txt"), StandardCopyOption.REPLACE_EXISTING);
			settingsMessage = "Exported config snapshot.";
		} catch (IOException e) {
			settingsMessage = "Export failed: " + e.getMessage();
		}
		renderShell();
	}

	private void resetSettingsToDefaults() {
		settingsConfig = new FluxusConfig();
		applyConfigPalette();
		settingsConfig.save();
		capturingShortcutAction = "";
		repairMinescriptConfig();
		settingsMessage = "Settings reset to defaults.";
		renderShell();
	}

	private void testRuffCommand() {
		String version = runProcessForFirstLine(new String[]{"ruff", "--version"});
		if (version.isBlank()) {
			version = runProcessForFirstLine(new String[]{"py", "-m", "ruff", "--version"});
		}
		settingsMessage = version.isBlank() ? "Ruff was not found." : "Ruff OK: " + version;
		renderShell();
	}

	private void openFolder(Path folder, String name) {
		try {
			Files.createDirectories(folder);
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(folder.toFile());
				settingsMessage = "Opened " + name + ".";
			} else {
				settingsMessage = name + ": " + folder;
			}
		} catch (IOException e) {
			settingsMessage = "Open folder failed: " + e.getMessage();
		}
		renderShell();
	}

	private View aboutCenter() {
		LinearLayout center = column(18);

		LinearLayout panel = column(14);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));

		LinearLayout hero = row(18);
		hero.setGravity(Gravity.CENTER_VERTICAL);
		hero.setPadding(22, 20, 22, 20);
		hero.setBackground(glass(Color.argb(128, 52, 30, 90), Color.argb(92, 14, 20, 35), 14, Color.argb(96, 163, 88, 255)));
		FrameLayout logo = new FrameLayout(requireContext());
		logo.setBackground(round(Color.argb(72, 163, 88, 255), 14, Color.argb(90, 163, 88, 255)));
		logo.addView(rawIcon("sculklogo.png"), centered(62, 62));
		hero.addView(logo, new LinearLayout.LayoutParams(76, 76));
		LinearLayout copy = column(7);
		copy.addView(label("Shulkr Client", 26, TEXT));
		copy.addView(label("A Minecraft scripting workspace for building, inspecting, and running automations with style.", 14, MUTED));
		LinearLayout tags = row(8);
		tags.addView(tag("ModernUI-MC", true), new LinearLayout.LayoutParams(104, 24));
		tags.addView(tag("Fabric 26.1.2", false), new LinearLayout.LayoutParams(104, 24));
		tags.addView(tag("Local first", false), new LinearLayout.LayoutParams(86, 24));
		copy.addView(tags, new LinearLayout.LayoutParams(match(), 28));
		hero.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		hero.addView(aboutStat("Version", "1.0.0"), new LinearLayout.LayoutParams(108, 64));
		hero.addView(aboutStat("Status", "Dev"), new LinearLayout.LayoutParams(92, 64));
		panel.addView(hero, new LinearLayout.LayoutParams(match(), 128));

		LinearLayout body = row(14);
		LinearLayout left = column(14);
		left.addView(aboutMissionCard(), new LinearLayout.LayoutParams(match(), 178));
		left.addView(aboutReleaseCard(), new LinearLayout.LayoutParams(match(), 246));
		left.addView(aboutCreditsCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		body.addView(left, new LinearLayout.LayoutParams(0, match(), 1.08F));

		LinearLayout right = column(14);
		right.addView(aboutStackCard(), new LinearLayout.LayoutParams(match(), 214));
		right.addView(aboutRoadmapCard(), new LinearLayout.LayoutParams(match(), 210));
		right.addView(aboutLinksCard(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		body.addView(right, new LinearLayout.LayoutParams(0, match(), 1.0F));
		panel.addView(body, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		center.addView(panel, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 70);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		center.addView(dock, dockLp);
		return center;
	}

	private View aboutMissionCard() {
		LinearLayout card = aboutCard("What This Is", "circle-info-solid.png");
		card.addView(label("Shulkr is the control layer for a script client: scripts, templates, editor tooling, module browsing, WindowSpy inspection, and settings live in one consistent interface.", 13, MUTED));
		LinearLayout metrics = row(10);
		metrics.addView(spyFact("Editor", "Ruff + autocomplete"), new LinearLayout.LayoutParams(0, 54, 1.0F));
		metrics.addView(spyFact("Inspector", "World snapshots"), new LinearLayout.LayoutParams(0, 54, 1.0F));
		metrics.addView(spyFact("UI", "Glass panels"), new LinearLayout.LayoutParams(0, 54, 1.0F));
		card.addView(metrics, new LinearLayout.LayoutParams(match(), 58));
		return card;
	}

	private View aboutReleaseCard() {
		LinearLayout card = aboutCard("Latest Build Notes", "clipboard-check-solid.png");
		card.addView(releaseRow("Added", "Templates gallery and starter scaffold previews", "green"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(releaseRow("Added", "WindowSpy layout for target, NBT, entities, and events", "purple"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(releaseRow("Added", "Settings control center with customization and file controls", "blue"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(releaseRow("Improved", "Shared grid sizing, glass opacity, and pill navigation", "green"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(releaseRow("Next", "Hook mock panels into live Minecraft data sources", "purple"), new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View aboutCreditsCard() {
		LinearLayout card = aboutCard("Credits", "star-solid.png");
		card.addView(creditRow("Developer", "Taylor Dawson"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(creditRow("Design direction", "Shulkr mockups and iterative polish"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(creditRow("UI framework", "ModernUI-MC by BloCamLimb"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(creditRow("Platform", "Fabric + Minecraft client runtime"), new LinearLayout.LayoutParams(match(), 38));
		card.addView(creditRow("Icons", "Local /icons asset set"), new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View aboutStackCard() {
		LinearLayout card = aboutCard("Tech Stack", "puzzle-piece-solid.png");
		card.addView(stackRow("Minecraft", "26.1.2"), new LinearLayout.LayoutParams(match(), 34));
		card.addView(stackRow("Fabric Loader", "0.19.3"), new LinearLayout.LayoutParams(match(), 34));
		card.addView(stackRow("ModernUI-MC", "3.13.0.5"), new LinearLayout.LayoutParams(match(), 34));
		card.addView(stackRow("Java", "25"), new LinearLayout.LayoutParams(match(), 34));
		return card;
	}

	private View aboutRoadmapCard() {
		LinearLayout card = aboutCard("Roadmap", "route-solid.png");
		card.addView(checkLine("Live WindowSpy world data"));
		card.addView(checkLine("Persistent settings storage"));
		card.addView(checkLine("Template generation into editor tabs"));
		card.addView(checkLine("Module install workflow"));
		return card;
	}

	private View aboutLinksCard() {
		LinearLayout card = aboutCard("Resources", "folder-open-solid.png");
		View project = settingsActionRow("Open project folder", "folder-open-solid.png");
		project.setOnClickListener(view -> openFolder(Path.of(System.getProperty("user.dir")), "Project folder"));
		card.addView(project, new LinearLayout.LayoutParams(match(), 40));
		View logs = settingsActionRow("View local logs", "clipboard-solid.png");
		logs.setOnClickListener(view -> openFolder(Path.of(System.getProperty("user.dir"), "logs"), "Local logs"));
		card.addView(logs, new LinearLayout.LayoutParams(match(), 40));
		View build = settingsActionRow("Copy build info", "clone-solid.png");
		build.setOnClickListener(view -> copyBuildInfo());
		card.addView(build, new LinearLayout.LayoutParams(match(), 40));
		View issue = settingsActionRow("Report issue", "circle-info-solid.png");
		issue.setOnClickListener(view -> copyIssueTemplate());
		card.addView(issue, new LinearLayout.LayoutParams(match(), 40));
		return card;
	}

	private View aboutSidePanel() {
		LinearLayout side = column(14);
		side.addView(aboutBuildPanel(), new LinearLayout.LayoutParams(match(), 300));
		side.addView(aboutHealthPanel(), new LinearLayout.LayoutParams(match(), 260));
		side.addView(aboutLegalPanel(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		return side;
	}

	private View aboutBuildPanel() {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("box-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Build Info", 16, TEXT));
		panel.addView(title, new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Client", "Shulkr Client"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Mod ID", "triton-ui"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Version", "1.0.0"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Renderer", "ModernUI + Arc3D"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Open key", keybindLabel(TritonUIClient.shortcutKey(SHORTCUT_OPEN_UI))), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Workspace", "Shulkr Client"), new LinearLayout.LayoutParams(match(), 30));
		TextView copy = label("Copy Build Details", 13, TEXT);
		copy.setGravity(Gravity.CENTER);
		makeHover(copy, glass(Color.argb(160, 93, 48, 168), Color.argb(128, 63, 34, 116), 8, PURPLE_SOFT),
				glass(Color.argb(210, 126, 64, 220), Color.argb(160, 91, 46, 170), 8, STROKE_HOVER));
		copy.setOnClickListener(view -> copyBuildInfo());
		panel.addView(copy, new LinearLayout.LayoutParams(match(), 38));
		return panel;
	}

	private View aboutHealthPanel() {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		panel.addView(label("Client Health", 16, TEXT));
		panel.addView(flagRow("UI thread", "Ready", "green"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(flagRow("Ruff", "Available", "green"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(flagRow("Assets", "Loaded", "green"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(flagRow("Config", "Local", "blue"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(flagRow("Network", "Blocked by default", "purple"), new LinearLayout.LayoutParams(match(), 30));
		return panel;
	}

	private View aboutLegalPanel() {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		panel.addView(label("Notes", 16, TEXT));
		panel.addView(label("Shulkr Client is in active development by Taylor Dawson. Expect sharp edges while the script marketplace, backend, and runtime hooks become a complete platform.", 12, MUTED));
		View licenses = settingsActionRow("Open licenses", "clipboard-solid.png");
		licenses.setOnClickListener(view -> {
			copyToClipboard("Shulkr bundles Minescript GPL resources, Python PSF license text, Fabric, and ModernUI-MC runtime dependencies. See src/main/resources for bundled notices.");
			saveUiMessage("Copied license notes.");
		});
		panel.addView(licenses, new LinearLayout.LayoutParams(match(), 40));
		View changelog = settingsActionRow("View changelog", "bookmark-solid.png");
		changelog.setOnClickListener(view -> {
			copyToClipboard("Latest Shulkr changes: draggable overlays, script-module backend, publish modal, editor tabs, WindowSpy data, settings, templates, standalone Express backend.");
			saveUiMessage("Copied changelog notes.");
		});
		panel.addView(changelog, new LinearLayout.LayoutParams(match(), 40));
		return panel;
	}

	private void copyBuildInfo() {
		String info = "Shulkr Client\n"
				+ "Mod ID: triton-ui\n"
				+ "Minecraft: 26.1.2\n"
				+ "ModernUI-MC: 3.13.0.5\n"
				+ "Backend: " + (FluxusAppState.get().backendOnline() ? "online" : "offline") + "\n"
				+ "Scripts: " + editorScripts.size() + "\n"
				+ "Libraries: " + FluxusAppState.get().modules().size() + "\n"
				+ "Workspace: " + System.getProperty("user.dir");
		copyToClipboard(info);
		saveUiMessage("Copied build info.");
	}

	private void copyIssueTemplate() {
		String template = "## Shulkr issue\n\n"
				+ "What happened:\n\n"
				+ "What I expected:\n\n"
				+ "Page: " + page + "\n"
				+ "Backend: " + (FluxusAppState.get().backendOnline() ? "online" : "offline") + "\n"
				+ "Selected script: " + (selectedScript == null ? "none" : selectedScript.getFileName()) + "\n";
		copyToClipboard(template);
		saveUiMessage("Copied issue template.");
	}

	private LinearLayout aboutCard(String title, String iconFile) {
		LinearLayout card = column(10);
		card.setPadding(18, 16, 18, 16);
		card.setBackground(round(Color.argb(100, 15, 21, 34), 12, Color.argb(76, 105, 116, 150)));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(icon(iconFile, PURPLE), new LinearLayout.LayoutParams(17, 17));
		header.addView(label(title, 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		card.addView(header, new LinearLayout.LayoutParams(match(), 30));
		return card;
	}

	private View aboutStat(String name, String value) {
		LinearLayout stat = column(4);
		stat.setGravity(Gravity.CENTER);
		stat.setBackground(round(Color.argb(104, 15, 21, 34), 10, Color.argb(70, 105, 116, 150)));
		stat.addView(label(name, 11, MUTED));
		stat.addView(label(value, 18, TEXT));
		return stat;
	}

	private View releaseRow(String type, String text, String tone) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(tag(type, tone.equals("purple")), new LinearLayout.LayoutParams(78, 24));
		row.addView(label(text, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(icon("circle-solid.png", toneColor(tone)), new LinearLayout.LayoutParams(9, 9));
		return row;
	}

	private View creditRow(String name, String value) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(icon("star-solid.png", PURPLE), new LinearLayout.LayoutParams(14, 14));
		row.addView(label(name, 12, TEXT), new LinearLayout.LayoutParams(116, wrap()));
		row.addView(label(value, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private View stackRow(String name, String value) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(tag(value, false), new LinearLayout.LayoutParams(100, 24));
		return row;
	}

	private View overlaysCenter() {
		LinearLayout center = column(18);
		LinearLayout panel = column(14);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));
		panel.addView(pageTitle("Overlays", "Choose widgets here, then arrange them directly in-game.", "border-none-solid.png",
				new String[][]{{"Widgets", String.valueOf(overlayWidgets().size()), "border-all-solid.png"}, {"Visible", String.valueOf(visibleOverlayWidgets.size()), "eye-dropper-solid.png"}}), new LinearLayout.LayoutParams(match(), 74));
		LinearLayout chips = row(8);
		for (String chipName : new String[]{"HUD", "Combat", "Debug", "World", "Editor", "Player"}) {
			View filterChip = chip(chipName, chipName.equals(overlayFilter));
			filterChip.setOnClickListener(view -> {
				overlayFilter = chipName;
				renderShell();
			});
			chips.addView(filterChip, new LinearLayout.LayoutParams(76, 34));
		}
		panel.addView(chips, new LinearLayout.LayoutParams(match(), 38));
		panel.addView(overlayWidgetPanel(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		center.addView(panel, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		addCenteredDock(center);
		return center;
	}

	private View overlaysSidePanel() {
		ScrollView scroll = layoutScrollView();
		LinearLayout side = column(14);
		side.addView(overlayInspectorPanel(), new LinearLayout.LayoutParams(match(), 270));
		side.addView(overlayPresetsPanel(), new LinearLayout.LayoutParams(match(), 228));
		side.addView(overlayRuntimePanel(), new LinearLayout.LayoutParams(match(), 390));
		scroll.addView(side, new ScrollView.LayoutParams(match(), wrap()));
		return scroll;
	}

	private View overlayWidgetPanel() {
		LinearLayout card = settingsCard("Widget Library", "box-open-solid.png");
		card.addView(label("Each widget has its own visual language. Select one to show it, then use Edit on Screen to position it.", 12, MUTED),
				new LinearLayout.LayoutParams(match(), 34));
		ScrollView scroller = layoutScrollView();
		LinearLayout list = column(8);
		for (String[] widget : overlayWidgets()) {
			if (widget[1].equals(overlayFilter) || overlayFilter.equals("HUD")) {
				list.addView(widgetRow(widget[0], widget[2], widget[3]), new LinearLayout.LayoutParams(match(), 58));
			}
		}
		scroller.addView(list, new ScrollView.LayoutParams(match(), wrap()));
		card.addView(scroller, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		card.addView(label(overlayMessage, 12, MUTED), new LinearLayout.LayoutParams(match(), 28));
		TextView create = textButton("Create Widget");
		create.setOnClickListener(view -> createOverlayWidget());
		card.addView(create, new LinearLayout.LayoutParams(match(), 38));
		return card;
	}

	private View overlayInspectorPanel() {
		LinearLayout panel = settingsCard("Selected Widget", "magnifying-glass-solid.png");
		String[] widget = selectedOverlayData();
		panel.addView(watchRow("Name", widget[0]), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Category", widget[1]), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Position", ShulkrHudOverlay.position(widget[0])), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Size", ShulkrHudOverlay.size(widget[0])), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(watchRow("Visible", visibleOverlayWidgets.contains(widget[0]) ? "yes" : "no"), new LinearLayout.LayoutParams(match(), 30));
		View binding = settingsActionRow("Edit on screen", "eye-dropper-solid.png");
		binding.setOnClickListener(view -> enterOverlayEditMode());
		panel.addView(binding, new LinearLayout.LayoutParams(match(), 40));
		return panel;
	}

	private View overlayPresetsPanel() {
		LinearLayout panel = settingsCard("Presets", "bookmark-solid.png");
		for (String[] preset : new String[][]{{"Combat HUD", "broom-solid.png"}, {"Builder HUD", "box-open-solid.png"}, {"Debug HUD", "circle-info-solid.png"}, {"Minimal HUD", "border-none-solid.png"}}) {
			View row = settingsActionRow(preset[0], preset[1]);
			row.setOnClickListener(view -> applyOverlayPreset(preset[0]));
			panel.addView(row, new LinearLayout.LayoutParams(match(), 40));
		}
		return panel;
	}

	private View overlayRuntimePanel() {
		LinearLayout panel = settingsCard("Runtime", "play-solid.png");
		panel.addView(flagRow("Renderer", overlayRendererActive ? "Active" : "Paused", overlayRendererActive ? "green" : "yellow"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(flagRow("Widgets", visibleOverlayWidgets.size() + " visible", "blue"), new LinearLayout.LayoutParams(match(), 30));
		panel.addView(flagRow("Selected", selectedOverlayWidget, "purple"), new LinearLayout.LayoutParams(match(), 30));
		View show = settingsActionRow(overlayRendererActive ? "Hide live overlay" : "Show live overlay", "eye-dropper-solid.png");
		show.setOnClickListener(view -> {
			overlayRendererActive = !overlayRendererActive;
			if (!overlayRendererActive) {
				overlayEditMode = false;
			}
			ShulkrHudOverlay.setRendererActive(overlayRendererActive);
			ShulkrHudOverlay.setEditMode(overlayRendererActive && overlayEditMode);
			overlayMessage = overlayRendererActive ? "Live overlay is visible." : "Live overlay hidden.";
			renderShell();
		});
		panel.addView(show, new LinearLayout.LayoutParams(match(), 40));
		View edit = settingsActionRow("Edit widgets on screen", "route-solid.png");
		edit.setOnClickListener(view -> enterOverlayEditMode());
		panel.addView(edit, new LinearLayout.LayoutParams(match(), 40));
		View snap = settingsActionRow("Snap selected top right", "expand-solid.png");
		snap.setOnClickListener(view -> {
			ShulkrHudOverlay.snapSelected("top-right");
			overlayMessage = "Snapped " + selectedOverlayWidget + " to the top right.";
			renderShell();
		});
		panel.addView(snap, new LinearLayout.LayoutParams(match(), 40));
		LinearLayout nudges = row(8);
		for (String[] nudge : new String[][]{{"<", "-6", "0"}, {"^", "0", "-6"}, {"v", "0", "6"}, {">", "6", "0"}}) {
			TextView button = textButton(nudge[0]);
			button.setOnClickListener(view -> {
				ShulkrHudOverlay.moveSelected(Integer.parseInt(nudge[1]), Integer.parseInt(nudge[2]));
				overlayMessage = "Nudged " + selectedOverlayWidget + " to " + ShulkrHudOverlay.position(selectedOverlayWidget) + ".";
				renderShell();
			});
			nudges.addView(button, new LinearLayout.LayoutParams(0, 36, 1.0F));
		}
		panel.addView(nudges, new LinearLayout.LayoutParams(match(), 38));
		View reset = settingsActionRow("Reset overlay layout", "arrows-rotate-solid.png");
		reset.setOnClickListener(view -> {
			ShulkrHudOverlay.resetLayout();
			syncOverlayStateFromHud();
			overlayMessage = "Reset the live HUD layout.";
			renderShell();
		});
		panel.addView(reset, new LinearLayout.LayoutParams(match(), 40));
		return panel;
	}

	private void enterOverlayEditMode() {
		overlayRendererActive = true;
		overlayEditMode = true;
		ShulkrHudOverlay.setRendererActive(true);
		ShulkrHudOverlay.setEditMode(true);
		overlayMessage = "Editing in-game. Press Esc or U to save and return.";
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(null));
	}

	private List<String[]> overlayWidgets() {
		List<String[]> widgets = new ArrayList<>(List.of(
				new String[]{"Target HUD", "Combat", "Target name and health bar", "broom-solid.png", "18, 48", "180 x 48"},
				new String[]{"Coordinates", "World", "Clean inline position text", "route-solid.png", "18, 108", "190 x 18"},
				new String[]{"Script Status", "Editor", "Script, state and elapsed time", "code-solid.png", "18, 138", "190 x 60"},
				new String[]{"NBT Peek", "Debug", "Terminal-style crosshair result", "clipboard-solid.png", "18, 210", "180 x 42"},
				new String[]{"FPS Counter", "HUD", "Minimal inline frame rate", "circle-solid.png", "18, 18", "84 x 18"},
				new String[]{"Player Vitals", "Player", "Compact health and hunger meters", "user-solid.png", "18, 264", "186 x 44"}));
		Set<String> known = new HashSet<>();
		for (String[] widget : widgets) {
			known.add(widget[0]);
		}
		for (String name : ShulkrHudOverlay.widgetNames()) {
			if (!known.contains(name)) {
				widgets.add(new String[]{name, "Custom", "User-created HUD widget", "plus-solid.png", ShulkrHudOverlay.position(name), ShulkrHudOverlay.size(name)});
			}
		}
		return widgets;
	}

	private String[] selectedOverlayData() {
		return overlayWidgets().stream()
				.filter(widget -> widget[0].equals(selectedOverlayWidget))
				.findFirst()
				.orElse(overlayWidgets().getFirst());
	}

	private void toggleOverlayWidget(String name) {
		selectedOverlayWidget = name;
		if (!visibleOverlayWidgets.remove(name)) {
			visibleOverlayWidgets.add(name);
			ShulkrHudOverlay.setWidgetVisible(name, true);
			overlayMessage = "Added " + name + " to the HUD preview.";
		} else {
			ShulkrHudOverlay.setWidgetVisible(name, false);
			overlayMessage = "Hidden " + name + " from the HUD preview.";
		}
		ShulkrHudOverlay.select(name);
		renderShell();
	}

	private void createOverlayWidget() {
		selectedOverlayWidget = ShulkrHudOverlay.addCustomWidget(overlayFilter);
		visibleOverlayWidgets.add(selectedOverlayWidget);
		ShulkrHudOverlay.setWidgetVisible(selectedOverlayWidget, true);
		ShulkrHudOverlay.select(selectedOverlayWidget);
		overlayFilter = "HUD";
		overlayMessage = "Created " + selectedOverlayWidget + ". Drag it around in edit mode.";
		renderShell();
	}

	private void saveOverlayPreset() {
		ShulkrHudOverlay.setVisibleNames(visibleOverlayWidgets);
		ShulkrHudOverlay.savePreset();
		overlayMessage = "Saved live HUD preset with " + visibleOverlayWidgets.size() + " visible widgets.";
		renderShell();
	}

	private void copyOverlayWidgetScript() {
		StringBuilder snippet = new StringBuilder("overlay_preset = {\n");
		for (String name : ShulkrHudOverlay.widgetNames()) {
			snippet.append("    \"").append(name).append("\": {")
					.append("\"position\": \"").append(ShulkrHudOverlay.position(name)).append("\", ")
					.append("\"size\": \"").append(ShulkrHudOverlay.size(name)).append("\", ")
					.append("\"visible\": ").append(visibleOverlayWidgets.contains(name) ? "True" : "False")
					.append("},\n");
		}
		snippet.append("}\n");
		copyToClipboard(snippet.toString());
		overlayMessage = "Copied overlay preset snippet.";
		renderShell();
	}

	private void applyOverlayPreset(String preset) {
		visibleOverlayWidgets.clear();
		ShulkrHudOverlay.applyPreset(preset);
		if (preset.equals("Combat HUD")) {
			visibleOverlayWidgets.addAll(List.of("Target HUD", "FPS Counter", "Player Vitals"));
			selectedOverlayWidget = "Target HUD";
			overlayFilter = "Combat";
		} else if (preset.equals("Builder HUD")) {
			visibleOverlayWidgets.addAll(List.of("Coordinates", "NBT Peek", "Script Status"));
			selectedOverlayWidget = "Coordinates";
			overlayFilter = "World";
		} else if (preset.equals("Debug HUD")) {
			visibleOverlayWidgets.addAll(List.of("NBT Peek", "Script Status", "FPS Counter"));
			selectedOverlayWidget = "NBT Peek";
			overlayFilter = "Debug";
		} else {
			visibleOverlayWidgets.add("FPS Counter");
			selectedOverlayWidget = "FPS Counter";
			overlayFilter = "HUD";
		}
		ShulkrHudOverlay.setVisibleNames(visibleOverlayWidgets);
		ShulkrHudOverlay.select(selectedOverlayWidget);
		overlayMessage = "Applied " + preset + ".";
		renderShell();
	}

	private TextView hudChip(String text, int color) {
		TextView chip = label(text, 12, color);
		chip.setGravity(Gravity.CENTER);
		chip.setBackground(round(Color.argb(128, 10, 15, 27), 8, Color.argb(80, 105, 116, 150)));
		return chip;
	}

	private View layerRow(String name, String type, boolean visible) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(8, 0, 8, 0);
		makeHover(row, round(Color.argb(0, 0, 0, 0), 8, 0),
				round(Color.argb(92, 28, 34, 54), 8, Color.argb(70, 155, 95, 255)));
		row.addView(icon(visible ? "eye-dropper-solid.png" : "border-none-solid.png", visible ? PURPLE : FAINT), new LinearLayout.LayoutParams(16, 16));
		row.addView(label(name, 12, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(tag(type, false), new LinearLayout.LayoutParams(78, 24));
		row.setOnClickListener(view -> toggleOverlayWidget(name));
		return row;
	}

	private View widgetRow(String name, String text, String iconFile) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(8, 0, 8, 0);
		boolean active = name.equals(selectedOverlayWidget);
		makeHover(row, round(active ? Color.argb(112, 85, 39, 150) : Color.argb(0, 0, 0, 0), 9, active ? STROKE_HOVER : 0),
				round(Color.argb(118, 29, 35, 56), 9, STROKE_HOVER));
		row.addView(iconBadge(iconFile, PURPLE, Color.argb(58, 163, 88, 255), 34, 8), new LinearLayout.LayoutParams(34, 34));
		LinearLayout copy = column(2);
		copy.addView(label(name, 12, TEXT));
		copy.addView(label(text, 11, MUTED));
		row.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(tag(visibleOverlayWidgets.contains(name) ? "shown" : "hidden", false), new LinearLayout.LayoutParams(70, 24));
		row.setOnClickListener(view -> {
			selectedOverlayWidget = name;
			visibleOverlayWidgets.add(name);
			ShulkrHudOverlay.setWidgetVisible(name, true);
			ShulkrHudOverlay.select(name);
			overlayMessage = "Selected " + name + ".";
			renderShell();
		});
		return row;
	}

	private void syncOverlayStateFromHud() {
		overlayRendererActive = ShulkrHudOverlay.rendererActive();
		overlayEditMode = ShulkrHudOverlay.editMode();
		visibleOverlayWidgets.clear();
		visibleOverlayWidgets.addAll(ShulkrHudOverlay.visibleNames());
		selectedOverlayWidget = ShulkrHudOverlay.selected();
	}

	private LinearLayout pageTitle(String titleText, String subtitle, String iconFile, String[][] stats) {
		LinearLayout header = row(12);
		header.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout title = column(6);
		LinearLayout titleLine = row(10);
		titleLine.setGravity(Gravity.CENTER_VERTICAL);
		titleLine.addView(icon(iconFile, PURPLE), new LinearLayout.LayoutParams(26, 26));
		titleLine.addView(label(titleText, 24, TEXT));
		title.addView(titleLine);
		title.addView(label(subtitle, 14, MUTED));
		header.addView(title, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		for (String[] stat : stats) {
			header.addView(settingsStatus(stat[0], stat[1], stat[2]), new LinearLayout.LayoutParams(112, 60));
		}
		return header;
	}

	private void addCenteredDock(LinearLayout center) {
		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 70);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		center.addView(dock, dockLp);
	}

	private void ensureEditorScriptsReady() {
		scriptDir = Paths.get(System.getProperty("user.dir"), "minescript");
		minescriptConfigFile = scriptDir.resolve("config.txt");
		settingsConfig = FluxusConfig.load();
		applyConfigPalette();
		page = pageFromConfig(initialPage == null ? settingsConfig.defaultPage() : initialPage);
		selectedFolder = scriptDir;
		try {
			Files.createDirectories(scriptDir);
			ensureMinescriptConfig();
		} catch (IOException e) {
			appendEditorLog("Failed to prepare minescript folder: " + e.getMessage());
		}
		refreshEditorScripts();
		if (editorLogs.isEmpty()) {
			appendEditorLog("Editor connected to " + scriptDir);
		}
	}

	private void ensureMinescriptConfig() throws IOException {
		if (Files.notExists(minescriptConfigFile)) {
			Files.writeString(
					minescriptConfigFile,
					"# Lines starting with \"#\" are ignored.\n\n"
							+ "python=\"\"\n"
							+ "command_path=\"system/exec;\"\n"
							+ "pyjinn_import_path=\"system/pyj;\"\n",
					StandardCharsets.UTF_8);
		}
	}

	private void refreshEditorScripts() {
		if (scriptDir == null) {
			scriptDir = Paths.get(System.getProperty("user.dir"), "minescript");
		}
		editorScripts.clear();
		editorFolders.clear();
		editorFunctionCache.keySet().removeIf(path -> !Files.exists(path));
		localCompletionDirty = true;
		try {
			Files.createDirectories(scriptDir);
			for (FolderSummary folder : FluxusAppState.get().scriptFolders()) {
				Path path = scriptDir.resolve(folder.path()).normalize();
				if (path.startsWith(scriptDir) && !isHiddenMinescriptFolder(path)) {
					editorFolders.add(path);
				}
			}
			for (ScriptSummary script : FluxusAppState.get().scripts()) {
				Path path = scriptDir.resolve(script.path()).normalize();
				if (path.startsWith(scriptDir) && !isHiddenMinescriptFile(path) && hasScriptExtension(path.getFileName().toString())) {
					editorScripts.add(path);
				}
			}
			editorFolders.sort(Comparator.comparing(path -> scriptDir.relativize(path).toString().toLowerCase(Locale.ROOT)));
			editorScripts.sort(Comparator.comparing(path -> scriptDir.relativize(path).toString().toLowerCase(Locale.ROOT)));
		} catch (IOException e) {
			appendEditorLog("Failed to refresh scripts: " + e.getMessage());
		}
		openEditorTabs.removeIf(path -> !Files.exists(path));
		if (selectedFolder == null || Files.notExists(selectedFolder)) {
			selectedFolder = scriptDir;
		}
		if (selectedScript == null && !editorScripts.isEmpty()) {
			selectedScript = editorScripts.getFirst();
			selectedEditorItems.clear();
			selectedEditorItems.add(selectedScript);
			addOpenEditorTab(selectedScript);
			loadActiveDraftFromDisk();
		} else if (selectedScript != null && !Files.exists(selectedScript)) {
			openEditorTabs.remove(selectedScript);
			editorDrafts.remove(selectedScript);
			editorFunctionCache.remove(selectedScript);
			dirtyScripts.remove(selectedScript);
			selectedScript = editorScripts.isEmpty() ? null : editorScripts.getFirst();
			if (selectedScript != null) {
				selectedEditorItems.clear();
				selectedEditorItems.add(selectedScript);
				addOpenEditorTab(selectedScript);
			}
			loadActiveDraftFromDisk();
		} else if (selectedScript != null) {
			addOpenEditorTab(selectedScript);
			editorDraft = draftFor(selectedScript);
			editorDirty = dirtyScripts.contains(selectedScript);
		}
	}

	private boolean isHiddenMinescriptFolder(Path path) {
		Path relative = scriptDir.relativize(path);
		if (relative.getNameCount() == 0) {
			return false;
		}
		String root = relative.getName(0).toString().toLowerCase(Locale.ROOT);
		return root.equals("system")
				|| root.equals("templates")
				|| root.equals("plugins")
				|| root.equals("plugins_disabled")
				|| root.equals("exports")
				|| root.equals("blockpacks");
	}

	private boolean isHiddenMinescriptFile(Path path) {
		Path relative = scriptDir.relativize(path);
		if (relative.getNameCount() == 0) {
			return false;
		}
		String fileName = relative.getFileName().toString().toLowerCase(Locale.ROOT);
		return fileName.equals("config.txt") || fileName.startsWith(".");
	}

	private boolean hasScriptExtension(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".py") || lower.endsWith(".pyj") || lower.endsWith(".lua") || lower.endsWith(".js") || lower.endsWith(".txt");
	}

	private String readFileQuietly(Path path) {
		if (path == null) {
			return "";
		}
		try {
			if (path.startsWith(scriptDir)) {
				return FluxusAppState.get().readScript(scriptDir.relativize(path).toString().replace('\\', '/'));
			}
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			appendEditorLog("Failed to read " + path.getFileName() + ": " + e.getMessage());
			return "";
		}
	}

	private void addOpenEditorTab(Path script) {
		if (script != null && openEditorTabs.stream().noneMatch(path -> Objects.equals(path, script))) {
			openEditorTabs.add(script);
		}
	}

	private void loadActiveDraftFromDisk() {
		if (selectedScript == null) {
			editorDraft = "";
			editorDirty = false;
			return;
		}
		String saved = editorSavedContents.computeIfAbsent(selectedScript, this::readFileQuietly);
		editorDraft = editorDrafts.computeIfAbsent(selectedScript, ignored -> saved);
		editorDirty = !Objects.equals(editorDraft, saved);
		if (editorDirty) {
			dirtyScripts.add(selectedScript);
		} else {
			dirtyScripts.remove(selectedScript);
		}
		selectedFolder = selectedScript.getParent() == null ? scriptDir : selectedScript.getParent();
	}

	private String draftFor(Path script) {
		if (script == null) {
			return "";
		}
		String saved = editorSavedContents.computeIfAbsent(script, this::readFileQuietly);
		return editorDrafts.computeIfAbsent(script, ignored -> saved);
	}

	private void captureActiveDraft() {
		if (selectedScript == null || codeEditor == null) {
			return;
		}
		String text = codeEditor.getText().toString();
		editorDraft = text;
		editorDrafts.put(selectedScript, text);
		String saved = editorSavedContents.computeIfAbsent(selectedScript, this::readFileQuietly);
		if (text.equals(saved)) {
			dirtyScripts.remove(selectedScript);
		} else {
			dirtyScripts.add(selectedScript);
		}
		editorDirty = dirtyScripts.contains(selectedScript);
	}

	private void selectEditorScript(Path script) {
		captureActiveDraft();
		selectedScript = script;
		if (!isControlDown() && !isShiftDown()) {
			selectedEditorItems.clear();
		}
		selectedEditorItems.add(script);
		addOpenEditorTab(script);
		loadActiveDraftFromDisk();
		currentScriptRunning = false;
		appendEditorLog("Opened " + script.getFileName() + ".");
		renderShell();
	}

	private void openScriptFilePicker() {
		new Thread(() -> {
			Frame owner = null;
			try {
				owner = new Frame();
				owner.setUndecorated(true);
				FileDialog dialog = new FileDialog(owner, "Open Shulkr script", FileDialog.LOAD);
				dialog.setDirectory(scriptDir.toAbsolutePath().toString());
				dialog.setFilenameFilter(scriptFileFilter());
				dialog.setVisible(true);
				String file = dialog.getFile();
				String directory = dialog.getDirectory();
				if (file == null || directory == null) {
					return;
				}
				Path selected = Paths.get(directory, file).toAbsolutePath().normalize();
				if (!hasScriptExtension(selected.getFileName().toString())) {
					appendEditorLog("Open cancelled: unsupported script type " + selected.getFileName() + ".");
					if (shell != null) {
						shell.post(this::refreshConsoleLogList);
					}
					return;
				}
				if (shell != null) {
					shell.post(() -> selectEditorScript(selected));
				}
			} finally {
				if (owner != null) {
					owner.dispose();
				}
			}
		}, "Shulkr Script File Picker").start();
	}

	private void importScriptFilePicker() {
		new Thread(() -> {
			Frame owner = null;
			try {
				owner = new Frame();
				owner.setUndecorated(true);
				FileDialog dialog = new FileDialog(owner, "Import Shulkr script", FileDialog.LOAD);
				dialog.setFilenameFilter(scriptFileFilter());
				dialog.setVisible(true);
				String file = dialog.getFile();
				String directory = dialog.getDirectory();
				if (file == null || directory == null) {
					return;
				}
				Path source = Paths.get(directory, file).toAbsolutePath().normalize();
				if (!hasScriptExtension(source.getFileName().toString())) {
					appendEditorLog("Import cancelled: unsupported script type " + source.getFileName() + ".");
					if (shell != null) {
						shell.post(this::refreshConsoleLogList);
					}
					return;
				}
				ScriptSummary summary = FluxusAppState.get().importScript(source, false);
				Path target = scriptDir.resolve(summary.path()).normalize();
				if (shell != null) {
					shell.post(() -> {
						refreshEditorScripts();
						selectDashboardScript(target, false);
						appendEditorLog("Imported " + target.getFileName() + ".");
						renderShell();
					});
				}
			} catch (IOException e) {
				appendEditorLog("Import failed: " + e.getMessage());
				if (shell != null) {
					shell.post(this::refreshConsoleLogList);
				}
			} finally {
				if (owner != null) {
					owner.dispose();
				}
			}
		}, "Shulkr Script Import Picker").start();
	}

	private void openPublishModal(Path source) {
		if (source != null && Files.isRegularFile(source)) {
			publishSourceScript = source;
			publishFileName = source.getFileName().toString();
			publishName = stripExtension(publishFileName).replace('-', ' ').replace('_', ' ');
			publishAbout = descriptionFromScript(source);
			publishTags = publishTagsFor(source);
			publishIcon = scriptIcon(publishFileName);
		} else if (publishSourceScript == null && selectedScript != null && Files.isRegularFile(selectedScript)) {
			openPublishModal(selectedScript);
			return;
		} else if (publishAuthor == null || publishAuthor.isBlank()) {
			publishAuthor = "EnderUser";
		}
		openDropdownKey = "publish-modal";
		renderShell();
	}

	private void choosePublishSourceFile() {
		new Thread(() -> {
			Frame owner = null;
			try {
				owner = new Frame();
				owner.setUndecorated(true);
				FileDialog dialog = new FileDialog(owner, "Choose script to publish", FileDialog.LOAD);
				dialog.setDirectory(scriptDir.toAbsolutePath().toString());
				dialog.setFilenameFilter(scriptFileFilter());
				dialog.setVisible(true);
				String file = dialog.getFile();
				String directory = dialog.getDirectory();
				if (file == null || directory == null) {
					return;
				}
				Path source = Paths.get(directory, file).toAbsolutePath().normalize();
				if (shell != null) {
					shell.post(() -> openPublishModal(source));
				}
			} catch (Exception e) {
				appendEditorLog("Choose publish source failed: " + e.getMessage());
				if (shell != null) {
					shell.post(this::refreshConsoleLogList);
				}
			} finally {
				if (owner != null) {
					owner.dispose();
				}
			}
		}, "Shulkr Publish Source Picker").start();
	}

	private void publishModalScript() {
		if (publishSourceScript == null || Files.notExists(publishSourceScript)) {
			appendEditorLog("Pick a script before publishing.");
			refreshConsoleLogList();
			return;
		}
		try {
			List<String> tags = parseTags(publishTags);
			LibraryScriptItem published = FluxusAppState.get().publishLibraryScript(
					publishSourceScript,
					publishName,
					publishAuthor,
					publishAbout,
					tags,
					publishIcon,
					publishFileName
			);
			selectedLibraryScriptId = published.id();
			openDropdownKey = "";
			page = Page.SCRIPTS;
			rememberPage(page);
			appendEditorLog("Published " + published.name() + " to the Shulkr library.");
			renderShell();
		} catch (IOException e) {
			appendEditorLog("Publish failed: " + e.getMessage());
			refreshConsoleLogList();
		}
	}

	private List<String> parseTags(String raw) {
		List<String> tags = new ArrayList<>();
		for (String tag : (raw == null ? "" : raw).split(",")) {
			String trimmed = tag.trim();
			if (!trimmed.isBlank() && tags.stream().noneMatch(existing -> existing.equalsIgnoreCase(trimmed))) {
				tags.add(trimmed);
			}
		}
		return tags;
	}

	private String publishTagsFor(Path source) {
		String ext = extension(source.getFileName().toString()).replace(".", "").toUpperCase(Locale.ROOT);
		String category = scriptCategory(source);
		return ext + ", " + category;
	}

	private String descriptionFromScript(Path source) {
		String description = readFileQuietly(source);
		for (String line : description.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("#")) {
				String comment = trimmed.replaceFirst("^#+", "").trim();
				if (!comment.isBlank()) {
					return comment;
				}
			}
		}
		return "A Shulkr community script.";
	}

	private void installPublishedScript(String id) {
		try {
			FluxusAppState.ScriptSummary summary = FluxusAppState.get().installLibraryScript(id);
			Path installed = scriptDir.resolve(summary.path()).normalize();
			refreshEditorScripts();
			if (Files.exists(installed)) {
				selectEditorScript(installed);
			}
			page = Page.EDITOR;
			appendEditorLog("Installed " + summary.name() + " from the Shulkr library.");
			renderShell();
		} catch (IOException e) {
			appendEditorLog("Install failed: " + e.getMessage());
			refreshConsoleLogList();
		}
	}

	private void previewPublishedScript(String id) {
		copyPublishedScriptCode(id);
	}

	private void copyPublishedScriptCode(String id) {
		LibraryScriptItem script = FluxusAppState.get().libraryScript(id);
		if (script == null) {
			appendEditorLog("Select a published script first.");
			refreshConsoleLogList();
			return;
		}
		copyToClipboard(script.code());
		appendEditorLog("Copied " + script.name() + " code.");
		refreshConsoleLogList();
	}

	private void copyPublishedScriptAbout(String id) {
		LibraryScriptItem script = FluxusAppState.get().libraryScript(id);
		if (script == null) {
			appendEditorLog("Select a published script first.");
			refreshConsoleLogList();
			return;
		}
		copyToClipboard(script.about());
		appendEditorLog("Copied " + script.name() + " about text.");
		refreshConsoleLogList();
	}

	private void deletePublishedScript(String id) {
		if (FluxusAppState.get().deleteLibraryScript(id)) {
			if (id.equals(selectedLibraryScriptId)) {
				selectedLibraryScriptId = "";
			}
			appendEditorLog("Deleted published script.");
			renderShell();
		} else {
			appendEditorLog("Delete failed: script was not found.");
			refreshConsoleLogList();
		}
	}

	private FilenameFilter scriptFileFilter() {
		return (dir, name) -> hasScriptExtension(name);
	}

	private void createNewScript() {
		createNewScript("NewScript.py",
				"import minescript as ms\n\nms.echo(\"Hello from {name}!\")\n");
	}

	private void createNewScript(String fileName, String template) {
		String creationKey = activeScriptFolder() + "|" + fileName;
		long now = System.currentTimeMillis();
		if (creatingScript || (creationKey.equals(lastCreateScriptKey) && now - lastCreateScriptAt < 450)) {
			return;
		}
		creatingScript = true;
		lastCreateScriptKey = creationKey;
		lastCreateScriptAt = now;
		try {
			captureActiveDraft();
			Path folder = activeScriptFolder();
			Path requested = folder.resolve(fileName).normalize();
			String relative = requested.startsWith(scriptDir)
					? scriptDir.relativize(requested).toString().replace('\\', '/')
					: fileName;
			String content = template.replace("{name}", stripExtension(fileName));
			ScriptSummary summary = FluxusAppState.get().writeScript(relative, content, false);
			Path target = scriptDir.resolve(summary.path()).normalize();
			selectedScript = target;
			editorSavedContents.put(target, content);
			editorDrafts.put(target, content);
			dirtyScripts.remove(target);
			addOpenEditorTab(target);
			loadActiveDraftFromDisk();
			refreshEditorScripts();
			appendEditorLog("Created " + target.getFileName() + ".");
			if (page == Page.SCRIPTS || page == Page.WINDOWSPY || page == Page.TEMPLATES) {
				page = Page.EDITOR;
				rememberPage(page);
			}
			renderShell();
		} catch (IOException e) {
			appendEditorLog("Create failed: " + e.getMessage());
			refreshConsoleLogList();
		} finally {
			creatingScript = false;
		}
	}

	private void createNewFolder() {
		Path parent = scriptDir;
		Path target = uniqueFolderPath(parent, "NewFolder");
		try {
			FolderSummary summary = FluxusAppState.get().createFolder(scriptDir.relativize(target).toString().replace('\\', '/'));
			target = scriptDir.resolve(summary.path()).normalize();
			selectedFolder = target;
			refreshEditorScripts();
			appendEditorLog("Created folder " + scriptDir.relativize(target) + ".");
			renderShell();
		} catch (IOException e) {
			appendEditorLog("Create folder failed: " + e.getMessage());
			refreshConsoleLogList();
		}
	}

	private Path activeScriptFolder() {
		if (selectedFolder != null && Files.isDirectory(selectedFolder) && !isHiddenMinescriptFolder(selectedFolder)) {
			return selectedFolder;
		}
		if (selectedScript != null && selectedScript.getParent() != null && Files.isDirectory(selectedScript.getParent())) {
			return selectedScript.getParent();
		}
		return scriptDir;
	}

	private Path uniqueScriptPath(Path folder, String fileName) {
		Path base = folder.resolve(fileName);
		if (!Files.exists(base)) {
			return base;
		}
		String stem = stripExtension(fileName);
		String ext = extension(fileName);
		for (int i = 2; i < 1000; i++) {
			Path candidate = folder.resolve(stem + "-" + i + ext);
			if (!Files.exists(candidate)) {
				return candidate;
			}
		}
		return folder.resolve(stem + "-" + System.currentTimeMillis() + ext);
	}

	private Path uniqueFolderPath(Path parent, String name) {
		Path base = parent.resolve(name);
		if (!Files.exists(base)) {
			return base;
		}
		for (int i = 2; i < 1000; i++) {
			Path candidate = parent.resolve(name + "-" + i);
			if (!Files.exists(candidate)) {
				return candidate;
			}
		}
		return parent.resolve(name + "-" + System.currentTimeMillis());
	}

	private void saveCurrentScript() {
		if (selectedScript == null || codeEditor == null) {
			appendEditorLog("No script selected to save.");
			refreshConsoleLogList();
			return;
		}
		try {
			editorDraft = codeEditor.getText().toString();
			if (selectedScript.startsWith(scriptDir)) {
				ScriptSummary summary = FluxusAppState.get().writeScript(scriptDir.relativize(selectedScript).toString().replace('\\', '/'), editorDraft, true);
				selectedScript = scriptDir.resolve(summary.path()).normalize();
			} else {
				Files.createDirectories(selectedScript.getParent());
				Files.writeString(selectedScript, editorDraft, StandardCharsets.UTF_8);
			}
			editorSavedContents.put(selectedScript, editorDraft);
			editorDrafts.put(selectedScript, editorDraft);
			dirtyScripts.remove(selectedScript);
			editorDirty = false;
			appendEditorLog("Saved " + selectedScript.getFileName() + ".");
			refreshEditorScripts();
			renderShell();
		} catch (IOException e) {
			appendEditorLog("Save failed: " + e.getMessage());
			refreshConsoleLogList();
		}
	}

	private void runCurrentScript() {
		if (selectedScript == null) {
			appendEditorLog("No script selected to run.");
			refreshConsoleLogList();
			return;
		}
		if (codeEditor != null) {
			saveCurrentScript();
		}
		String fileName = selectedScript.getFileName().toString();
		if (".pyj".equalsIgnoreCase(extension(fileName))) {
			appendEditorLog(fileName + " is a Pyjinn module. Import it from a Python runner instead of running it directly.");
			refreshConsoleLogList();
			return;
		}
		if (scriptRunSafetyLabel().equals("needs throttle")) {
			appendEditorLog("Warning: this script has `while True` without sleep; add a throttle to avoid freezing.");
		}
		String command = scriptDir.relativize(selectedScript).toString().replace('\\', '/');
		int dot = command.lastIndexOf('.');
		String commandName = dot > 0 ? command.substring(0, dot) : command;
		currentScriptRunning = true;
		appendEditorLog("Queued " + fileName + " to start.");
		renderShell();
		Minescript.runEditorCommandAsync(commandName, handled -> {
			if (shell != null) {
				shell.post(() -> {
					currentScriptRunning = handled;
					if (handled) {
						TritonUIClient.rememberLastScript(command);
					}
					appendEditorLog((handled ? "Started " : "Failed to start ") + fileName + ".");
					renderShell();
				});
			}
		});
	}

	private void stopScripts() {
		Minescript.runEditorCommandAsync("killjob -1", handled -> {
			if (shell != null) {
				shell.post(() -> {
					currentScriptRunning = false;
					appendEditorLog(handled ? "Stop requested for all Minescript jobs." : "Stop command was not handled.");
					renderShell();
				});
			}
		});
	}

	private void exportCurrentScript() {
		if (selectedScript == null) {
			appendEditorLog("Select a script to export.");
			refreshConsoleLogList();
			return;
		}
		try {
			saveDraftWithoutRebuild();
			Path exportDir = scriptDir.resolve("exports");
			Files.createDirectories(exportDir);
			Path target = exportDir.resolve(selectedScript.getFileName());
			Files.copy(selectedScript, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			appendEditorLog("Exported to " + scriptDir.relativize(target) + ".");
			refreshConsoleLogList();
		} catch (IOException e) {
			appendEditorLog("Export failed: " + e.getMessage());
			refreshConsoleLogList();
		}
	}

	private String selectedScriptRelativePath() {
		if (selectedScript == null || scriptDir == null || !selectedScript.startsWith(scriptDir)) {
			return "";
		}
		return scriptDir.relativize(selectedScript).toString().replace('\\', '/');
	}

	private String scriptRunSafetyLabel() {
		if (selectedScript == null) {
			return "-";
		}
		String fileName = selectedScript.getFileName().toString();
		if (".pyj".equalsIgnoreCase(extension(fileName))) {
			return "import only";
		}
		String draft = editorDrafts.getOrDefault(selectedScript, readFileQuietly(selectedScript));
		if (draft.length() > 40_000) {
			return "large script";
		}
		if (draft.contains("while True") && !draft.contains("sleep(") && !draft.contains("time.sleep(")) {
			return "needs throttle";
		}
		return "ready";
	}

	private int scriptRunSafetyColor() {
		String label = scriptRunSafetyLabel();
		return label.equals("ready") ? GREEN : label.equals("-") ? MUTED : Color.argb(255, 255, 190, 88);
	}

	private void toggleSelectedScriptModule() {
		String relative = selectedScriptRelativePath();
		if (relative.isBlank()) {
			appendEditorLog("Select a local script before marking it as a module.");
			refreshConsoleLogList();
			return;
		}
		boolean next = !FluxusAppState.get().isScriptModule(relative);
		FluxusAppState.get().setScriptModule(relative, next);
		appendEditorLog((next ? "Marked " : "Unmarked ") + selectedScript.getFileName() + " as a module.");
		renderShell();
	}

	private void copySelectedScriptImport() {
		String relative = selectedScriptRelativePath();
		if (relative.isBlank() || selectedScript == null) {
			appendEditorLog("Select a local script before copying an import.");
			refreshConsoleLogList();
			return;
		}
		String fileName = selectedScript.getFileName().toString();
		String snippet;
		if (".pyj".equalsIgnoreCase(extension(fileName))) {
			snippet = "from system.lib import java\n\n"
					+ stripExtension(fileName) + " = java.import_pyjinn_script(\"" + relative + "\")";
		} else {
			String module = relative.substring(0, relative.length() - extension(fileName).length()).replace('/', '.');
			snippet = "import " + module;
		}
		copyToClipboard(snippet);
		appendEditorLog("Copied import snippet for " + fileName + ".");
		refreshConsoleLogList();
	}

	private void saveDraftWithoutRebuild() throws IOException {
		if (selectedScript != null && codeEditor != null) {
			editorDraft = codeEditor.getText().toString();
			if (selectedScript.startsWith(scriptDir)) {
				ScriptSummary summary = FluxusAppState.get().writeScript(scriptDir.relativize(selectedScript).toString().replace('\\', '/'), editorDraft, true);
				selectedScript = scriptDir.resolve(summary.path()).normalize();
			} else {
				Files.writeString(selectedScript, editorDraft, StandardCharsets.UTF_8);
			}
			editorSavedContents.put(selectedScript, editorDraft);
			editorDrafts.put(selectedScript, editorDraft);
			dirtyScripts.remove(selectedScript);
			editorDirty = false;
		}
	}

	private void deleteCurrentScript() {
		if (selectedScript == null) {
			appendEditorLog("Select a script to delete.");
			refreshConsoleLogList();
			return;
		}
		deleteScript(selectedScript);
	}

	private void deleteScript(Path script) {
		if (script == null) {
			appendEditorLog("Select a script to delete.");
			refreshConsoleLogList();
			return;
		}
		Path deleted = script;
		try {
			boolean deletedOk = deleted.startsWith(scriptDir)
					? FluxusAppState.get().deleteScript(scriptDir.relativize(deleted).toString().replace('\\', '/'))
					: Files.deleteIfExists(deleted);
			if (!deletedOk) {
				throw new IOException("Script was not found");
			}
			openEditorTabs.remove(deleted);
			editorDrafts.remove(deleted);
			editorSavedContents.remove(deleted);
			dirtyScripts.remove(deleted);
			refreshEditorScripts();
			selectedScript = openEditorTabs.isEmpty() ? (editorScripts.isEmpty() ? null : editorScripts.getFirst()) : openEditorTabs.getLast();
			if (selectedScript != null) {
				addOpenEditorTab(selectedScript);
			}
			loadActiveDraftFromDisk();
			appendEditorLog("Deleted " + deleted.getFileName() + ".");
			renderShell();
		} catch (IOException e) {
			appendEditorLog("Delete failed: " + e.getMessage());
			refreshConsoleLogList();
		}
	}

	private void deleteSelectedEditorItems() {
		Set<Path> targets = selectedEditorItems.isEmpty() && contextEditorItem != null
				? Set.of(contextEditorItem)
				: new HashSet<>(selectedEditorItems);
		if (targets.isEmpty()) {
			appendEditorLog("Select files or folders to delete.");
			refreshConsoleLogList();
			return;
		}
		int deletedCount = 0;
		for (Path target : targets.stream()
				.sorted(Comparator.comparingInt((Path path) -> path.getNameCount()).reversed())
				.toList()) {
			if (target == null || !target.normalize().startsWith(scriptDir) || target.equals(scriptDir)) {
				continue;
			}
			try {
				if (Files.isDirectory(target)) {
					List<Path> children;
					try (var walk = Files.walk(target)) {
						children = walk.sorted(Comparator.comparingInt((Path path) -> path.getNameCount()).reversed()).toList();
					}
					for (Path child : children) {
						Files.deleteIfExists(child);
						removeEditorPathReferences(child);
					}
					deletedCount++;
				} else if (Files.isRegularFile(target)) {
					boolean deleted = target.startsWith(scriptDir)
							? FluxusAppState.get().deleteScript(scriptDir.relativize(target).toString().replace('\\', '/'))
							: Files.deleteIfExists(target);
					if (deleted) {
						removeEditorPathReferences(target);
						deletedCount++;
					}
				}
			} catch (IOException e) {
				appendEditorLog("Delete failed for " + target.getFileName() + ": " + e.getMessage());
			}
		}
		selectedEditorItems.clear();
		contextEditorItem = null;
		if (selectedScript != null && !Files.exists(selectedScript)) {
			selectedScript = editorScripts.stream().filter(Files::exists).findFirst().orElse(null);
			if (selectedScript != null) {
				addOpenEditorTab(selectedScript);
			}
		}
		refreshEditorScripts();
		loadActiveDraftFromDisk();
		appendEditorLog("Deleted " + deletedCount + " item" + (deletedCount == 1 ? "" : "s") + ".");
		renderShell();
	}

	private void removeEditorPathReferences(Path target) {
		if (target == null) {
			return;
		}
		openEditorTabs.removeIf(path -> path.equals(target) || path.startsWith(target));
		editorDrafts.keySet().removeIf(path -> path.equals(target) || path.startsWith(target));
		editorSavedContents.keySet().removeIf(path -> path.equals(target) || path.startsWith(target));
		editorFunctionCache.keySet().removeIf(path -> path.equals(target) || path.startsWith(target));
		dirtyScripts.removeIf(path -> path.equals(target) || path.startsWith(target));
		collapsedEditorFolders.removeIf(path -> path.equals(target) || path.startsWith(target));
	}

	private void openEditorScriptContext(Path script, View anchor) {
		contextScript = script;
		contextEditorItem = script;
		selectedScript = script;
		if (!selectedEditorItems.contains(script)) {
			selectedEditorItems.clear();
			selectedEditorItems.add(script);
		}
		addOpenEditorTab(script);
		loadActiveDraftFromDisk();
		toggleDropdown("editor-script-context", anchor, 232);
	}

	private void openEditorItemContext(Path path, View anchor) {
		contextEditorItem = path;
		if (Files.isRegularFile(path)) {
			contextScript = path;
			selectedScript = path;
			addOpenEditorTab(path);
			loadActiveDraftFromDisk();
		} else {
			contextScript = null;
			selectedFolder = path;
		}
		if (!selectedEditorItems.contains(path)) {
			selectedEditorItems.clear();
			selectedEditorItems.add(path);
		}
		toggleDropdown("editor-script-context", anchor, 232);
	}

	private void beginRename(Path path) {
		if (path == null || path.equals(scriptDir)) {
			return;
		}
		renamingPath = path;
		renamingDraft = path.getFileName().toString();
		lastClickedEditorItem = path;
		selectedEditorItems.clear();
		selectedEditorItems.add(path);
		appendEditorLog("Renaming " + path.getFileName() + ".");
		renderShell();
	}

	private void commitRename(Path path, String requestedName) {
		if (path == null) {
			return;
		}
		String cleaned = requestedName == null ? "" : requestedName.trim();
		if (cleaned.isBlank() || cleaned.contains("/") || cleaned.contains("\\")) {
			appendEditorLog("Rename cancelled: invalid name.");
			renamingPath = null;
			renderShell();
			return;
		}
		if (Files.isRegularFile(path) && !cleaned.contains(".")) {
			cleaned += extension(path.getFileName().toString());
		}
		Path target = path.resolveSibling(cleaned);
		try {
			if (!path.equals(target)) {
				if (path.startsWith(scriptDir) && Files.isDirectory(path)) {
					FolderSummary summary = FluxusAppState.get().renameFolder(scriptDir.relativize(path).toString().replace('\\', '/'), cleaned);
					target = scriptDir.resolve(summary.path()).normalize();
					updateRenamedFolderReferences(path, target);
				} else if (path.startsWith(scriptDir)) {
					ScriptSummary summary = FluxusAppState.get().renameScript(scriptDir.relativize(path).toString().replace('\\', '/'), cleaned);
					target = scriptDir.resolve(summary.path()).normalize();
				} else {
					Files.move(path, target);
				}
			}
			if (Objects.equals(selectedScript, path)) {
				selectedScript = target;
				moveEditorCachePath(path, target);
			}
			for (int i = 0; i < openEditorTabs.size(); i++) {
				if (Objects.equals(openEditorTabs.get(i), path)) {
					openEditorTabs.set(i, target);
				}
			}
			if (Objects.equals(selectedFolder, path)) {
				selectedFolder = target;
			}
			if (Objects.equals(lastClickedEditorItem, path)) {
				lastClickedEditorItem = target;
			}
			if (selectedEditorItems.remove(path)) {
				selectedEditorItems.add(target);
			}
			renamingPath = null;
			refreshEditorScripts();
			appendEditorLog("Renamed to " + cleaned + ".");
			renderShell();
		} catch (IOException e) {
			appendEditorLog("Rename failed: " + e.getMessage());
			renamingPath = null;
			refreshConsoleLogList();
			renderShell();
		}
	}

	private void handleEditorItemClick(Path path, boolean folder) {
		long now = System.currentTimeMillis();
		boolean doubleClick = Objects.equals(lastClickedEditorItem, path) && now - lastEditorClickAt < 460;
		Path previousClicked = lastClickedEditorItem;
		boolean selectionGesture = isShiftDown() || isControlDown();
		lastClickedEditorItem = path;
		lastEditorClickAt = now;
		if (doubleClick) {
			beginRename(path);
			return;
		}
		updateEditorSelection(path, previousClicked);
		if (selectionGesture) {
			renderShell();
			return;
		}
		if (folder) {
			selectedFolder = path;
			if (collapsedEditorFolders.contains(path)) {
				collapsedEditorFolders.remove(path);
				appendEditorLog("Expanded folder " + scriptDir.relativize(path) + ".");
			} else {
				collapsedEditorFolders.add(path);
				appendEditorLog("Collapsed folder " + scriptDir.relativize(path) + ".");
			}
			renderShell();
		} else {
			selectEditorScript(path);
		}
	}

	private void updateEditorSelection(Path path, Path previousClicked) {
		if (path == null) {
			return;
		}
		if (isShiftDown() || isControlDown()) {
			if (!selectedEditorItems.remove(path)) {
				selectedEditorItems.add(path);
			}
			return;
		}
		selectedEditorItems.clear();
		selectedEditorItems.add(path);
	}

	private List<Path> visibleEditorItems() {
		List<Path> items = new ArrayList<>();
		for (Path script : rootScripts()) {
			items.add(script);
		}
		for (Path folder : rootFolders()) {
			items.add(folder);
			if (!collapsedEditorFolders.contains(folder)) {
				items.addAll(scriptsUnder(folder));
			}
		}
		return items;
	}

	private void moveEditorCachePath(Path previous, Path renamed) {
		if (editorDrafts.containsKey(previous)) {
			editorDrafts.put(renamed, editorDrafts.remove(previous));
		}
		if (editorSavedContents.containsKey(previous)) {
			editorSavedContents.put(renamed, editorSavedContents.remove(previous));
		}
		if (dirtyScripts.remove(previous)) {
			dirtyScripts.add(renamed);
		}
	}

	private void updateRenamedFolderReferences(Path previous, Path renamed) {
		if (selectedScript != null && selectedScript.startsWith(previous)) {
			selectedScript = renamed.resolve(previous.relativize(selectedScript)).normalize();
		}
		Map<Path, String> movedDrafts = new HashMap<>();
		for (Map.Entry<Path, String> entry : editorDrafts.entrySet()) {
			Path path = entry.getKey();
			movedDrafts.put(path.startsWith(previous) ? renamed.resolve(previous.relativize(path)).normalize() : path, entry.getValue());
		}
		editorDrafts.clear();
		editorDrafts.putAll(movedDrafts);
		Map<Path, List<String>> movedFunctions = new HashMap<>();
		for (Map.Entry<Path, List<String>> entry : editorFunctionCache.entrySet()) {
			Path path = entry.getKey();
			movedFunctions.put(path.startsWith(previous) ? renamed.resolve(previous.relativize(path)).normalize() : path, entry.getValue());
		}
		editorFunctionCache.clear();
		editorFunctionCache.putAll(movedFunctions);
		Map<Path, String> movedSavedContents = new HashMap<>();
		for (Map.Entry<Path, String> entry : editorSavedContents.entrySet()) {
			Path path = entry.getKey();
			movedSavedContents.put(path.startsWith(previous) ? renamed.resolve(previous.relativize(path)).normalize() : path, entry.getValue());
		}
		editorSavedContents.clear();
		editorSavedContents.putAll(movedSavedContents);
		Set<Path> movedDirty = new HashSet<>();
		for (Path path : dirtyScripts) {
			movedDirty.add(path.startsWith(previous) ? renamed.resolve(previous.relativize(path)).normalize() : path);
		}
		dirtyScripts.clear();
		dirtyScripts.addAll(movedDirty);
		for (int i = 0; i < openEditorTabs.size(); i++) {
			Path path = openEditorTabs.get(i);
			if (path.startsWith(previous)) {
				openEditorTabs.set(i, renamed.resolve(previous.relativize(path)).normalize());
			}
		}
		Set<Path> movedSelection = new HashSet<>();
		for (Path path : selectedEditorItems) {
			movedSelection.add(path.startsWith(previous) ? renamed.resolve(previous.relativize(path)).normalize() : path);
		}
		selectedEditorItems.clear();
		selectedEditorItems.addAll(movedSelection);
	}

	private void closeEditorTab(Path script) {
		captureActiveDraft();
		int index = openEditorTabs.indexOf(script);
		openEditorTabs.remove(script);
		if (Objects.equals(selectedScript, script)) {
			if (openEditorTabs.isEmpty()) {
				selectedScript = editorScripts.isEmpty() ? null : editorScripts.getFirst();
				if (selectedScript != null) {
					addOpenEditorTab(selectedScript);
				}
			} else {
				selectedScript = openEditorTabs.get(Math.max(0, Math.min(index, openEditorTabs.size() - 1)));
			}
			loadActiveDraftFromDisk();
		}
		renderShell();
	}

	private void formatCurrentEditor() {
		if (codeEditor == null) {
			return;
		}
		String formatted = codeEditor.getText().toString()
				.replace("\t", "    ")
				.replaceAll("[ \\t]+\\r?\\n", "\n");
		if (!formatted.endsWith("\n")) {
			formatted += "\n";
		}
		codeEditor.setText(formatted);
		editorDraft = formatted;
		editorDirty = true;
		appendEditorLog("Formatted whitespace.");
		refreshConsoleLogList();
	}

	private void toggleCommentCurrentLine() {
		if (codeEditor == null) {
			return;
		}
		String text = codeEditor.getText().toString();
		int caret = Math.max(0, Math.min(codeEditor.getSelectionStart(), text.length()));
		int lineStart = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
		int lineEnd = text.indexOf('\n', caret);
		if (lineEnd < 0) {
			lineEnd = text.length();
		}
		String line = text.substring(lineStart, lineEnd);
		String replacement = line.stripLeading().startsWith("#")
				? line.replaceFirst("^(\\s*)# ?", "$1")
				: line.replaceFirst("^(\\s*)", "$1# ");
		codeEditor.getText().replace(lineStart, lineEnd, replacement);
		editorDraft = codeEditor.getText().toString();
		editorDirty = true;
		appendEditorLog("Toggled comment on current line.");
		refreshConsoleLogList();
	}

	private void findNextMinescriptCall() {
		if (codeEditor == null) {
			return;
		}
		String text = codeEditor.getText().toString();
		if (text.isEmpty()) {
			appendEditorLog("Nothing to search yet.");
			refreshConsoleLogList();
			return;
		}
		int start = Math.max(0, Math.min(codeEditor.getSelectionEnd(), text.length()));
		int index = text.indexOf("ms.", start);
		if (index < 0 && start > 0) {
			index = text.indexOf("ms.");
		}
		if (index >= 0) {
			codeEditor.setSelection(index, Math.min(text.length(), index + 3));
			appendEditorLog("Found next Minescript call.");
		} else {
			appendEditorLog("No `ms.` call found in this script.");
		}
		refreshConsoleLogList();
	}

	private void insertLibraryImport(String importLine) {
		if (codeEditor == null) {
			return;
		}
		Editable editable = codeEditor.getText();
		String text = editable.toString();
		if (containsImportLine(text, importLine)) {
			appendEditorLog("Library already imported: " + importLine + ".");
			refreshConsoleLogList();
			return;
		}
		int insertAt = importInsertOffset(text);
		String insertion = importLine + "\n";
		editable.insert(insertAt, insertion);
		int caret = Math.min(insertAt + insertion.length(), editable.length());
		codeEditor.setSelection(caret);
		editorDraft = editable.toString();
		if (selectedScript != null) {
			editorDrafts.put(selectedScript, editorDraft);
			dirtyScripts.add(selectedScript);
		}
		editorDirty = true;
		scheduleEditorAnalysis(editorDraft);
		updateCompletion(codeEditor);
		appendEditorLog("Inserted library import: " + importLine + ".");
		refreshConsoleLogList();
	}

	private void insertEditorSnippet(String snippet) {
		openDropdownKey = "";
		if (codeEditor == null) {
			appendEditorLog("Open a script before inserting a snippet.");
			refreshConsoleLogList();
			return;
		}
		Editable editable = codeEditor.getText();
		int start = Math.max(0, Math.min(codeEditor.getSelectionStart(), editable.length()));
		int end = Math.max(start, Math.min(codeEditor.getSelectionEnd(), editable.length()));
		String insertion = snippet == null ? "" : snippet;
		editable.replace(start, end, insertion);
		codeEditor.setSelection(Math.min(start + insertion.length(), editable.length()));
		editorDraft = editable.toString();
		if (selectedScript != null) {
			editorDrafts.put(selectedScript, editorDraft);
			dirtyScripts.add(selectedScript);
		}
		editorDirty = true;
		scheduleEditorAnalysis(editorDraft);
		updateCompletion(codeEditor);
		appendEditorLog("Inserted snippet.");
		refreshConsoleLogList();
	}

	private boolean containsImportLine(String text, String importLine) {
		for (String line : text.split("\n", -1)) {
			if (line.trim().equals(importLine)) {
				return true;
			}
		}
		return false;
	}

	private int importInsertOffset(String text) {
		if (text.isBlank()) {
			return 0;
		}
		String[] lines = text.split("\n", -1);
		int offset = 0;
		int lastImportEnd = -1;
		for (String line : lines) {
			String trimmed = line.trim();
			int nextOffset = Math.min(text.length(), offset + line.length() + 1);
			if (trimmed.startsWith("import ") || trimmed.startsWith("from ")) {
				lastImportEnd = nextOffset;
			}
			offset = nextOffset;
			if (offset >= text.length()) {
				break;
			}
		}
		if (lastImportEnd >= 0) {
			return lastImportEnd;
		}
		if (lines.length > 0 && lines[0].startsWith("#!")) {
			return Math.min(text.length(), lines[0].length() + 1);
		}
		return 0;
	}

	private void refreshEditorPage(String message) {
		refreshEditorScripts();
		appendEditorLog(message);
		renderShell();
	}

	private void openScriptFolder() {
		try {
			Files.createDirectories(scriptDir);
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(scriptDir.toFile());
				appendEditorLog("Opened " + scriptDir + ".");
			} else {
				appendEditorLog("Script folder: " + scriptDir);
			}
		} catch (IOException e) {
			appendEditorLog("Open folder failed: " + e.getMessage());
		}
		refreshConsoleLogList();
	}

	private void appendEditorLog(String message) {
		String timestamp = java.time.LocalTime.now().withNano(0).toString();
		editorLogs.add("[" + timestamp + "] " + message);
		while (editorLogs.size() > 80) {
			editorLogs.removeFirst();
		}
	}

	private void refreshConsoleLogList() {
		if (consoleLogList == null) {
			return;
		}
		consoleLogList.removeAllViews();
		List<String> rows = consoleRows();
		for (String log : rows) {
			consoleLogList.addView(label(log, 11, isErrorLog(log) ? Color.argb(235, 255, 120, 140) : Color.argb(230, 126, 255, 160)));
		}
	}

	private List<String> consoleRows() {
		List<String> rows = new ArrayList<>();
		for (String log : editorLogs) {
			boolean error = isErrorLog(log);
			if (consoleTab.equals("Errors") && !error) {
				continue;
			}
			if (consoleTab.equals("Output") && error) {
				continue;
			}
			rows.add(log);
		}
		if (rows.isEmpty()) {
			rows.add(consoleTab.equals("Errors") ? "No errors." : "No console messages.");
		}
		return rows;
	}

	private boolean isErrorLog(String log) {
		String lower = log.toLowerCase(Locale.ROOT);
		return lower.contains("failed") || lower.contains("error") || lower.contains("exception") || lower.contains("cancelled");
	}

	private String scriptIcon(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("farm") || lower.contains("afk")) return "user-solid.png";
		if (lower.contains("build") || lower.contains("chunk")) return "box-open-solid.png";
		if (lower.contains("sort") || lower.contains("inventory")) return "box-solid.png";
		if (lower.contains("combat") || lower.contains("mine")) return "broom-solid.png";
		if (lower.contains("tp") || lower.contains("path")) return "route-solid.png";
		if (lower.contains("notif")) return "bell-solid.png";
		return "code-solid.png";
	}

	private String modifiedAgo(Path script) {
		try {
			long millis = System.currentTimeMillis() - Files.getLastModifiedTime(script).toMillis();
			long minutes = Math.max(0, millis / 60000);
			if (minutes < 1) return "now";
			if (minutes < 60) return minutes + "m";
			long hours = minutes / 60;
			if (hours < 24) return hours + "h";
			long days = hours / 24;
			return days + "d";
		} catch (IOException e) {
			return "-";
		}
	}

	private String scriptSize(Path script) {
		try {
			long bytes = Files.size(script);
			if (bytes < 1024) return bytes + " B";
			return String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0);
		} catch (IOException e) {
			return "-";
		}
	}

	private String scriptType(Path script) {
		String ext = extension(script.getFileName().toString()).toLowerCase(Locale.ROOT);
		return switch (ext) {
			case ".py" -> "Python Script";
			case ".pyj" -> "Pyjinn Script";
			case ".lua" -> "Lua Script";
			case ".js" -> "JavaScript";
			default -> "Text Script";
		};
	}

	private String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	private String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot >= 0 ? fileName.substring(dot) : ".py";
	}

	private List<Path> dashboardRecentScripts() {
		refreshEditorScripts();
		return editorScripts.stream()
				.sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
				.limit(4)
				.toList();
	}

	private long lastModifiedMillis(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}

	private String scriptSubtitle(Path script) {
		try {
			for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
				String trimmed = line.trim();
				if (trimmed.startsWith("#")) {
					String comment = trimmed.replaceFirst("^#+", "").trim();
					if (!comment.isBlank()) {
						return trimTo(comment, 42);
					}
				}
				if (!trimmed.isBlank() && !trimmed.startsWith("import ") && !trimmed.startsWith("from ")) {
					return trimTo(trimmed, 42);
				}
			}
		} catch (IOException ignored) {
		}
		return scriptType(script);
	}

	private String dashboardInitials(String name) {
		String stem = stripExtension(name).replace('-', ' ').replace('_', ' ').trim();
		if (stem.isBlank()) {
			return "PY";
		}
		String[] parts = stem.split("\\s+");
		if (parts.length > 1) {
			return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
		}
		return stem.substring(0, Math.min(2, stem.length())).toUpperCase(Locale.ROOT);
	}

	private int[] dashboardTone(String key) {
		int hash = Math.abs(key.hashCode());
		int[][] colors = {
				{48, 205, 92}, {79, 138, 255}, {238, 136, 43}, {255, 72, 96},
				{163, 88, 255}, {64, 214, 255}, {255, 213, 78}
		};
		return colors[hash % colors.length];
	}

	private int enabledDashboardModules() {
		return (int) FluxusAppState.get().modules().stream().filter(ModuleItem::installed).count();
	}

	private void selectDashboardScript(Path script, boolean openEditor) {
		if (script == null) {
			return;
		}
		selectScriptWithoutNavigation(script);
		if (openEditor) {
			page = Page.EDITOR;
			rememberPage(page);
		}
		renderShell();
	}

	private void selectScriptWithoutNavigation(Path script) {
		if (script == null) {
			return;
		}
		captureActiveDraft();
		selectedScript = script;
		selectedFolder = script.getParent() == null ? scriptDir : script.getParent();
		addOpenEditorTab(script);
		loadActiveDraftFromDisk();
		appendEditorLog("Selected " + script.getFileName() + ".");
	}

	private View editorCenter() {
		LinearLayout center = row(14);
		center.addView(editorScriptList(), new LinearLayout.LayoutParams(280, match()));
		center.addView(editorWorkspace(), new LinearLayout.LayoutParams(0, match(), 1.0F));
		return center;
	}

	private View editorScriptList() {
		LinearLayout panel = column(12);
		panel.setPadding(16, 16, 16, 14);
		panel.setBackground(panel(14));

		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(label("SCRIPTS", 14, PURPLE), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 24));

		LinearLayout searchRow = row(8);
		searchRow.addView(compactSearch("Search scripts..."), new LinearLayout.LayoutParams(0, 38, 1.0F));
		View refresh = iconButton("arrows-rotate-solid.png", MUTED);
		refresh.setOnClickListener(view -> refreshEditorPage("Refreshed script list."));
		searchRow.addView(refresh, new LinearLayout.LayoutParams(38, 38));
		panel.addView(searchRow, new LinearLayout.LayoutParams(match(), 40));

		ScrollView treeScroller = layoutScrollView();
		LinearLayout tree = column(4);
		for (Path script : rootScripts()) {
			tree.addView(editorScriptRow(script, 0), new LinearLayout.LayoutParams(match(), 42));
		}
		for (Path folder : rootFolders()) {
			tree.addView(editorFolderRow(folder), new LinearLayout.LayoutParams(match(), 42));
			if (!collapsedEditorFolders.contains(folder)) {
				for (Path script : scriptsUnder(folder)) {
					tree.addView(editorScriptRow(script, 18), new LinearLayout.LayoutParams(match(), 42));
				}
			}
		}
		if (editorScripts.isEmpty() && editorFolders.isEmpty()) {
			TextView empty = label("No local scripts yet. Create or import one.", 12, MUTED);
			empty.setGravity(Gravity.CENTER);
			tree.addView(empty, new LinearLayout.LayoutParams(match(), 90));
		}
		treeScroller.addView(tree, new ScrollView.LayoutParams(match(), wrap()));
		panel.addView(treeScroller, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout footer = row(8);
		footer.setGravity(Gravity.CENTER_VERTICAL);
		footer.addView(label(editorScripts.size() + " scripts", 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View openFolder = iconButton("folder-plus-solid.png", MUTED);
		openFolder.setOnClickListener(view -> createNewFolder());
		footer.addView(openFolder, new LinearLayout.LayoutParams(38, 36));
		panel.addView(footer, new LinearLayout.LayoutParams(match(), 38));
		return panel;
	}

	private View editorWorkspace() {
		LinearLayout workspace = column(12);
		workspace.addView(codeEditorPanel(), new LinearLayout.LayoutParams(match(), 0, 1.0F));
		workspace.addView(consolePanel(), new LinearLayout.LayoutParams(match(), 150));
		LinearLayout dock = dock();
		LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(DOCK_WIDTH, 62);
		dockLp.gravity = Gravity.CENTER_HORIZONTAL;
		workspace.addView(dock, dockLp);
		return workspace;
	}

	private View codeEditorPanel() {
		LinearLayout panel = column(0);
		panel.setBackground(panel(14));

		LinearLayout tabs = row(8);
		tabs.setPadding(8, 8, 8, 0);
		tabs.setGravity(Gravity.CENTER_VERTICAL);
		for (Path tabScript : openEditorTabs.stream().limit(5).toList()) {
			tabs.addView(editorTab(tabScript), new LinearLayout.LayoutParams(176, 36));
		}
		if (openEditorTabs.isEmpty()) {
			LinearLayout emptyTab = row(8);
			emptyTab.setPadding(14, 0, 12, 0);
			emptyTab.setGravity(Gravity.CENTER_VERTICAL);
			emptyTab.setBackground(round(Color.argb(90, 18, 24, 39), 8, STROKE));
			emptyTab.addView(icon("code-solid.png", MUTED), new LinearLayout.LayoutParams(15, 15));
			emptyTab.addView(label("No script", 13, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
			tabs.addView(emptyTab, new LinearLayout.LayoutParams(176, 36));
		}
		View addTab = iconButton("plus-solid.png", MUTED);
		addTab.setOnClickListener(view -> createNewScript());
		tabs.addView(addTab, new LinearLayout.LayoutParams(36, 36));
		panel.addView(tabs, new LinearLayout.LayoutParams(match(), 44));

		EditText editor = new EditText(requireContext());
		codeEditor = editor;
		String initialEditorText = selectedScript == null ? "" : draftFor(selectedScript);
		boolean deferInitialText = initialEditorText.length() > 20_000;
		editor.setTextSize(12);
		editor.setTextColor(Color.argb(242, 213, 220, 244));
		editor.setHintTextColor(FAINT);
		editor.setHint("# Start typing Python...");
		editor.setSingleLine(false);
		editor.setHorizontallyScrolling(true);
		editor.setTextIsSelectable(true);
		editor.setCursorVisible(true);
		editor.setGravity(Gravity.TOP | Gravity.LEFT);
		editor.setPadding(24, 16, 24, 16);
		editor.setMinLines(18);
		editor.setText(deferInitialText ? "" : initialEditorText);
		editor.setBackground(glass(Color.argb(72, 3, 8, 16), Color.argb(52, 5, 9, 17), 0, 0));
		editor.setOnKeyListener((view, keyCode, event) -> {
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (isControlDown() && keyCode == InputConstants.KEY_S) {
					saveCurrentScript();
					return true;
				}
				if (isControlDown() && keyCode == InputConstants.KEY_R) {
					runCurrentScript();
					return true;
				}
				if (keyCode == InputConstants.KEY_F2) {
					Path target = lastClickedEditorItem != null ? lastClickedEditorItem
							: (!selectedEditorItems.isEmpty() ? selectedEditorItems.iterator().next() : selectedScript);
					beginRename(target);
					return true;
				}
				if (keyCode == KeyEvent.KEY_TAB) {
					if (!completionRemainder.isEmpty()) {
						acceptCompletion();
					} else {
						indentEditorSelection(editor);
					}
					return true;
				}
			}
			return false;
		});
		editor.setOnTouchListener((view, event) -> {
			if (event.getActionMasked() == MotionEvent.ACTION_UP) {
				long now = System.currentTimeMillis();
				if (now - lastEditorTextClickAt < 420) {
					editor.post(() -> selectWordAtCaret(editor));
				}
				lastEditorTextClickAt = now;
			}
			return false;
		});
		editor.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence text, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence text, int start, int before, int count) {
				if (!stylingEditorText) {
					editorDraft = text.toString();
					if (selectedScript != null) {
						editorDrafts.put(selectedScript, editorDraft);
						String saved = editorSavedContents.computeIfAbsent(selectedScript, TritonModernFragment.this::readFileQuietly);
						if (editorDraft.equals(saved)) {
							dirtyScripts.remove(selectedScript);
						} else {
							dirtyScripts.add(selectedScript);
						}
					}
					editorDirty = selectedScript == null || dirtyScripts.contains(selectedScript);
					updateLineNumberGutter(text);
					scheduleEditorAnalysis(text.toString());
					updateCompletion(editor);
				}
			}

			@Override
			public void afterTextChanged(Editable editable) {
			}
		});
		LinearLayout editorBody = row(0);
		editorLineNumbers = label("", 12, Color.argb(190, 94, 86, 112));
		editorLineCount = 0;
		editorLineNumbers.setGravity(Gravity.TOP | Gravity.RIGHT);
		editorLineNumbers.setPadding(8, 16, 10, 16);
		editorLineNumbers.setBackground(round(Color.argb(92, 8, 5, 12), 0, 0));
		updateLineNumberGutter(editor.getText());
		editor.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) ->
				editorLineNumbers.scrollTo(0, scrollY));
		editorBody.addView(editorLineNumbers, new LinearLayout.LayoutParams(58, match()));
		FrameLayout editorStack = new FrameLayout(requireContext());
		editorStack.addView(editor, new FrameLayout.LayoutParams(match(), match()));
		completionGhost = label("", 12, Color.argb(150, 162, 173, 204));
		completionGhost.setPadding(0, 0, 0, 0);
		completionGhost.setGravity(Gravity.TOP | Gravity.LEFT);
		editorStack.addView(completionGhost, new FrameLayout.LayoutParams(match(), match(), Gravity.TOP | Gravity.LEFT));
		editorBody.addView(editorStack, new LinearLayout.LayoutParams(0, match(), 1.0F));
		panel.addView(editorBody, new LinearLayout.LayoutParams(match(), 0, 1.0F));

		LinearLayout lint = column(4);
		lint.setPadding(18, 7, 18, 7);
		lint.setBackground(round(Color.argb(84, 13, 18, 31), 0, Color.argb(44, 105, 116, 150)));
		lintSummary = label("Python lint: checking...", 12, MUTED);
		completionHint = label("", 11, FAINT);
		lintList = column(2);
		ScrollView lintScroll = layoutScrollView();
		lintScroll.setFillViewport(false);
		lintScroll.addView(lintList, new ScrollView.LayoutParams(match(), wrap()));
		lint.addView(lintSummary, new LinearLayout.LayoutParams(match(), 16));
		lint.addView(completionHint, new LinearLayout.LayoutParams(match(), wrap()));
		lint.addView(lintScroll, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		panel.addView(lint, new LinearLayout.LayoutParams(match(), 124));

		LinearLayout status = row(16);
		status.setPadding(18, 0, 18, 0);
		status.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
		status.addView(label(editorDirty ? "Unsaved" : "Saved", 12, editorDirty ? Color.argb(255, 255, 190, 88) : GREEN));
		status.addView(label("Spaces: 4", 12, MUTED));
		status.addView(label("UTF-8", 12, MUTED));
		status.addView(label("LF", 12, MUTED));
		status.addView(label(selectedScript == null ? "Python" : extension(selectedScript.getFileName().toString()).replace(".", "").toUpperCase(Locale.ROOT), 12, MUTED));
		status.addView(label(currentScriptRunning ? "* Running" : "Idle", 12, currentScriptRunning ? GREEN : MUTED));
		panel.addView(status, new LinearLayout.LayoutParams(match(), 34));
		if (deferInitialText) {
			lintSummary.setText("Loading editor...");
			editor.post(() -> {
				if (codeEditor == editor) {
					editor.setText(initialEditorText);
					editor.setSelection(0);
				}
			});
		} else {
			scheduleEditorAnalysis(editor.getText().toString());
		}
		updateCompletion(editor);
		return panel;
	}

	private String defaultPythonScript() {
		return "# AutoFarm - Automatically farms crops and replants them\n"
				+ "# by Shulkr\n"
				+ "import minescript as ms\n"
				+ "from time import sleep\n\n"
				+ "settings = {\n"
				+ "    \"range\": 5,\n"
				+ "    \"replant\": True,\n"
				+ "    \"delay\": 0.2,\n"
				+ "    \"whitelist\": [\"wheat\", \"carrots\", \"potatoes\", \"beetroots\"]\n"
				+ "}\n\n"
				+ "def is_crop(block_name):\n"
				+ "    crop_name = block_name.split(\"[\")[0].split(\":\")[-1]\n"
				+ "    return crop_name in settings[\"whitelist\"]\n\n"
				+ "def farm():\n"
				+ "    px, py, pz = [round(v) for v in ms.player_position()]\n\n"
				+ "    for x in range(-settings[\"range\"], settings[\"range\"] + 1):\n"
				+ "        for z in range(-settings[\"range\"], settings[\"range\"] + 1):\n"
				+ "            bx, by, bz = px + x, py, pz + z\n"
				+ "            target = ms.getblock(bx, by, bz)\n"
				+ "            if is_crop(target) and \"age=7\" in target:\n"
				+ "                ms.execute(f\"/setblock {bx} {by} {bz} air destroy\")\n"
				+ "                if settings[\"replant\"]:\n"
				+ "                    crop = target.split(\"[\")[0]\n"
				+ "                    ms.execute(f\"/setblock {bx} {by} {bz} {crop}\")\n"
				+ "            sleep(settings[\"delay\"])\n\n"
				+ "while True:\n"
				+ "    farm()\n"
				+ "    sleep(0.5)\n";
	}

	private String templateStarterScript() {
		return "# AutoFarm Starter - generated from Shulkr Templates\n"
				+ "import minescript as ms\n"
				+ "from time import sleep\n\n"
				+ "settings = {\n"
				+ "    \"range\": 5,\n"
				+ "    \"replant\": True,\n"
				+ "    \"delay\": 0.2,\n"
				+ "    \"whitelist\": [\"wheat\", \"carrots\", \"potatoes\", \"beetroots\"],\n"
				+ "}\n\n"
				+ "def should_harvest(block_name):\n"
				+ "    crop_name = block_name.split(\"[\")[0].split(\":\")[-1]\n"
				+ "    return crop_name in settings[\"whitelist\"] and \"age=7\" in block_name\n\n"
				+ "def nearby_blocks(center):\n"
				+ "    px, py, pz = center\n"
				+ "    for x in range(-settings[\"range\"], settings[\"range\"] + 1):\n"
				+ "        for z in range(-settings[\"range\"], settings[\"range\"] + 1):\n"
				+ "            bx, by, bz = px + x, py, pz + z\n"
				+ "            yield bx, by, bz, ms.getblock(bx, by, bz)\n\n"
				+ "def run():\n"
				+ "    center = [round(v) for v in ms.player_position()]\n"
				+ "    for bx, by, bz, block_name in nearby_blocks(center):\n"
				+ "        if should_harvest(block_name):\n"
				+ "            ms.execute(f\"/setblock {bx} {by} {bz} air destroy\")\n"
				+ "            if settings[\"replant\"]:\n"
				+ "                crop = block_name.split(\"[\")[0]\n"
				+ "                ms.execute(f\"/setblock {bx} {by} {bz} {crop}\")\n"
				+ "            sleep(settings[\"delay\"])\n\n"
				+ "while True:\n"
				+ "    run()\n"
				+ "    sleep(0.5)\n";
	}

	private void scheduleEditorAnalysis(String source) {
		if (lintSummary == null || lintList == null) {
			return;
		}
		int generation = ++lintGeneration;
		if (editorAnalysisTask != null) {
			editorAnalysisTask.cancel(false);
		}
		if (isPyjinnEditorScript()) {
			clearSyntaxSpans(codeEditor == null ? null : codeEditor.getText());
			clearErrorSpans();
			if (ruffLintTask != null) {
				ruffLintTask.cancel(false);
				ruffLintTask = null;
			}
			renderLint("Pyjinn", List.of(), false);
			if (lintList != null) {
				lintList.removeAllViews();
				lintList.addView(label("Pyjinn scripts skip Ruff and Python-only syntax checks.", 11, FAINT));
			}
			return;
		}
		long delay = source.length() > 100_000 ? 500L : source.length() > 25_000 ? 300L : 140L;
		editorAnalysisTask = lintExecutor.schedule(() -> {
			List<SyntaxRange> ranges = scanSyntax(source);
			List<String> issues = lintPython(source);
			if (codeEditor != null) {
				codeEditor.post(() -> {
					if (generation != lintGeneration || codeEditor == null) {
						return;
					}
					applySyntaxRanges(codeEditor.getText(), ranges);
					renderLint("Built-in Python lint", issues, true);
					if (settingsConfig == null || settingsConfig.ruffDiagnostics()) {
						scheduleRuffLint(source, generation, List.copyOf(issues));
					}
				});
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	private void updateLineNumberGutter(CharSequence text) {
		if (editorLineNumbers == null) {
			return;
		}
		int lines = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') lines++;
		}
		if (lines == editorLineCount) {
			return;
		}
		editorLineCount = lines;
		StringBuilder numbers = new StringBuilder(Math.max(16, lines * 5));
		for (int line = 1; line <= lines; line++) {
			if (line > 1) numbers.append('\n');
			numbers.append(line);
		}
		editorLineNumbers.setText(numbers);
	}

	private boolean isPyjinnEditorScript() {
		return selectedScript != null && ".pyj".equalsIgnoreCase(extension(selectedScript.getFileName().toString()));
	}

	private void renderLint(String title, List<String> issues, boolean showRuffHint) {
		lintList.removeAllViews();
		if (issues.isEmpty()) {
			lintSummary.setText("No issues found");
			lintSummary.setTextColor(GREEN);
			return;
		}
		lintSummary.setText(issues.size() + " issue" + (issues.size() == 1 ? "" : "s"));
		lintSummary.setTextColor(Color.argb(255, 255, 190, 88));
		for (String issue : issues) {
			lintList.addView(label(issue, 11, Color.argb(235, 255, 198, 112)));
		}
	}

	private void scheduleRuffLint(String source, int generation, List<String> builtInIssues) {
		if (ruffLintTask != null) {
			ruffLintTask.cancel(false);
		}
		ruffLintTask = lintExecutor.schedule(() -> {
			RuffResult result = runRuff(source);
			if (lintSummary != null) {
				lintSummary.post(() -> {
					if (generation == lintGeneration && lintSummary != null && lintList != null && result.available) {
						LinkedHashSet<String> combinedIssues = new LinkedHashSet<>(builtInIssues);
						combinedIssues.addAll(result.messages());
						renderLint("Python lint", new ArrayList<>(combinedIssues), false);
						applyRuffDiagnostics(result.issues());
					}
				});
			}
		}, 650, TimeUnit.MILLISECONDS);
	}

	private RuffResult runRuff(String source) {
		String[][] commands = {
				{"ruff", "check", "--output-format", "json", "--stdin-filename", "AutoFarm.py", "-"},
				{"py", "-m", "ruff", "check", "--output-format", "json", "--stdin-filename", "AutoFarm.py", "-"},
				{"python", "-m", "ruff", "check", "--output-format", "json", "--stdin-filename", "AutoFarm.py", "-"}
		};
		for (String[] command : commands) {
			try {
				Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
				process.getOutputStream().write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				process.getOutputStream().close();
				String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				boolean finished = process.waitFor(2, TimeUnit.SECONDS);
				if (!finished) {
					process.destroyForcibly();
					continue;
				}
				String trimmedOutput = output.trim();
				int exitCode = process.exitValue();
				if ((exitCode != 0 && exitCode != 1) || !trimmedOutput.startsWith("[")) {
					continue;
				}
				List<RuffIssue> issues = parseRuffIssues(output).stream()
						.filter(issue -> !issue.code().equals("E401"))
						.toList();
				return new RuffResult(true, issues);
			} catch (Exception ignored) {
			}
		}
		return new RuffResult(false, List.of());
	}

	private List<RuffIssue> parseRuffIssues(String output) {
		List<RuffIssue> issues = new ArrayList<>();
		Pattern pattern = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\".*?\"end_location\"\\s*:\\s*\\{\\s*\"column\"\\s*:\\s*(\\d+)\\s*,\\s*\"row\"\\s*:\\s*(\\d+)\\s*}.*?\"location\"\\s*:\\s*\\{\\s*\"column\"\\s*:\\s*(\\d+)\\s*,\\s*\"row\"\\s*:\\s*(\\d+)\\s*}.*?\"message\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(output);
		while (matcher.find() && issues.size() < 8) {
			String code = matcher.group(1);
			int endColumn = Integer.parseInt(matcher.group(2));
			int endRow = Integer.parseInt(matcher.group(3));
			int column = Integer.parseInt(matcher.group(4));
			int row = Integer.parseInt(matcher.group(5));
			String message = unescapeJsonString(matcher.group(6));
			issues.add(new RuffIssue(row, column, endRow, endColumn, code, message));
		}
		return issues;
	}

	private String unescapeJsonString(String text) {
		return text.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t");
	}

	private record RuffIssue(int row, int column, int endRow, int endColumn, String code, String message) {
		String display() {
			return "Line " + row + " [" + code + "]: " + message;
		}
	}

	private record RuffResult(boolean available, List<RuffIssue> issues) {
		List<String> messages() {
			List<String> messages = new ArrayList<>();
			for (RuffIssue issue : issues) {
				messages.add(issue.display());
			}
			return messages;
		}
	}

	private void applyRuffDiagnostics(List<RuffIssue> issues) {
		if (codeEditor == null) {
			return;
		}
		Editable editable = codeEditor.getText();
		clearErrorSpans(editable);
		String source = editable.toString();
		for (RuffIssue issue : issues) {
			int start = offsetForLineColumn(source, issue.row(), issue.column());
			int end = offsetForLineColumn(source, issue.endRow(), issue.endColumn());
			if (start < 0 || end <= start || start >= editable.length()) {
				continue;
			}
			end = Math.min(end, editable.length());
			stylingEditorText = true;
			editable.setSpan(new ErrorUnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			editable.setSpan(new BackgroundColorSpan(Color.argb(58, 255, 72, 96)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			stylingEditorText = false;
		}
	}

	private void styleEditorSyntax(Editable editable) {
		if (editable == null || stylingEditorText) {
			return;
		}
		stylingEditorText = true;
		for (SyntaxColorSpan span : editable.getSpans(0, editable.length(), SyntaxColorSpan.class)) {
			editable.removeSpan(span);
		}
		String source = editable.toString();
		applySyntaxPattern(editable, source, "\\b(from|import|as|def|return|for|while|if|elif|else|try|except|finally|with|class|lambda|in|is|and|or|not|break|continue|pass|global|nonlocal)\\b",
				Color.argb(255, 190, 132, 255));
		applySyntaxPattern(editable, source, "\\b(True|False|None)\\b", Color.argb(255, 112, 197, 255));
		applySyntaxPattern(editable, source, "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()", Color.argb(255, 105, 214, 255));
		applySyntaxPattern(editable, source, "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'", Color.argb(255, 150, 226, 126));
		applySyntaxPattern(editable, source, "#[^\\n]*", Color.argb(255, 126, 139, 168));
		stylingEditorText = false;
	}

	private List<SyntaxRange> scanSyntax(String source) {
		List<SyntaxRange> ranges = new ArrayList<>();
		collectSyntaxRanges(ranges, source, "\\b(from|import|as|def|return|for|while|if|elif|else|try|except|finally|with|class|lambda|in|is|and|or|not|break|continue|pass|global|nonlocal)\\b",
				Color.argb(255, 190, 132, 255));
		collectSyntaxRanges(ranges, source, "\\b(True|False|None)\\b", Color.argb(255, 112, 197, 255));
		collectSyntaxRanges(ranges, source, "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()", Color.argb(255, 105, 214, 255));
		collectSyntaxRanges(ranges, source, "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'", Color.argb(255, 150, 226, 126));
		collectSyntaxRanges(ranges, source, "#[^\\n]*", Color.argb(255, 126, 139, 168));
		return ranges;
	}

	private void collectSyntaxRanges(List<SyntaxRange> ranges, String source, String pattern, int color) {
		Matcher matcher = Pattern.compile(pattern).matcher(source);
		while (matcher.find()) {
			ranges.add(new SyntaxRange(matcher.start(), matcher.end(), color));
		}
	}

	private void applySyntaxRanges(Editable editable, List<SyntaxRange> ranges) {
		if (editable == null || stylingEditorText) return;
		stylingEditorText = true;
		for (SyntaxColorSpan span : editable.getSpans(0, editable.length(), SyntaxColorSpan.class)) {
			editable.removeSpan(span);
		}
		for (SyntaxRange range : ranges) {
			if (range.start() >= 0 && range.end() <= editable.length()) {
				editable.setSpan(new SyntaxColorSpan(range.color()), range.start(), range.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}
		stylingEditorText = false;
	}

	private record SyntaxRange(int start, int end, int color) {}

	private void clearSyntaxSpans(Editable editable) {
		if (editable == null || stylingEditorText) {
			return;
		}
		stylingEditorText = true;
		for (SyntaxColorSpan span : editable.getSpans(0, editable.length(), SyntaxColorSpan.class)) {
			editable.removeSpan(span);
		}
		stylingEditorText = false;
	}

	private void applySyntaxPattern(Editable editable, String source, String pattern, int color) {
		Matcher matcher = Pattern.compile(pattern).matcher(source);
		while (matcher.find()) {
			editable.setSpan(new SyntaxColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
	}

	private void clearErrorSpans() {
		if (codeEditor != null) {
			clearErrorSpans(codeEditor.getText());
		}
	}

	private void clearErrorSpans(Editable editable) {
		stylingEditorText = true;
		for (ErrorUnderlineSpan span : editable.getSpans(0, editable.length(), ErrorUnderlineSpan.class)) {
			editable.removeSpan(span);
		}
		for (BackgroundColorSpan span : editable.getSpans(0, editable.length(), BackgroundColorSpan.class)) {
			editable.removeSpan(span);
		}
		stylingEditorText = false;
	}

	private int offsetForLineColumn(String source, int row, int column) {
		if (row < 1 || column < 1) {
			return -1;
		}
		int currentRow = 1;
		int offset = 0;
		while (currentRow < row && offset < source.length()) {
			if (source.charAt(offset) == '\n') {
				currentRow++;
			}
			offset++;
		}
		return Math.min(offset + column - 1, source.length());
	}

	private void updateCompletion(EditText editor) {
		if (completionHint == null) {
			return;
		}
		String token = currentToken(editor);
		String suggestion = completionFor(token);
		if (suggestion.isEmpty()) {
			completionRemainder = "";
			completionHint.setText("");
			completionHint.setTextColor(FAINT);
			if (completionGhost != null) {
				completionGhost.setText("");
				completionGhost.setTranslationX(0.0F);
				completionGhost.setTranslationY(0.0F);
			}
			return;
		}
		completionRemainder = suggestion.substring(token.length());
		completionHint.setText("Tab to complete: " + suggestion);
		completionHint.setTextColor(Color.argb(220, 185, 160, 255));
		if (completionGhost != null) {
			positionCompletionGhost(editor, completionRemainder);
		}
	}

	private void positionCompletionGhost(EditText editor, String remainder) {
		if (completionGhost == null) {
			return;
		}
		if (remainder == null || remainder.isEmpty()) {
			completionGhost.setText("");
			completionGhost.setPadding(0, 0, 0, 0);
			completionGhost.setTranslationX(0.0F);
			completionGhost.setTranslationY(0.0F);
			return;
		}
		completionGhost.setText(remainder);
		Layout layout = editor.getLayout();
		if (layout == null) {
			editor.post(() -> positionCompletionGhost(editor, remainder));
			return;
		}
		int caret = Math.max(0, Math.min(editor.getSelectionStart(), editor.getText().length()));
		int line = layout.getLineForOffset(caret);
		float x = editor.getCompoundPaddingLeft() + layout.getPrimaryHorizontal(caret) - editor.getScrollX();
		float y = editor.getExtendedPaddingTop() + layout.getLineTop(line) - editor.getScrollY();
		completionGhost.setTranslationX(0.0F);
		completionGhost.setTranslationY(0.0F);
		completionGhost.setPadding(Math.max(0, Math.round(x)), Math.max(0, Math.round(y)), 0, 0);
	}

	private void acceptCompletion() {
		if (codeEditor == null || completionRemainder.isEmpty()) {
			return;
		}
		int start = Math.max(0, codeEditor.getSelectionStart());
		int end = Math.max(start, codeEditor.getSelectionEnd());
		codeEditor.getText().replace(start, end, completionRemainder);
		completionRemainder = "";
		updateCompletion(codeEditor);
	}

	private void indentEditorSelection(EditText editor) {
		Editable editable = editor.getText();
		String text = editable.toString();
		int start = Math.max(0, Math.min(editor.getSelectionStart(), text.length()));
		int end = Math.max(start, Math.min(editor.getSelectionEnd(), text.length()));
		if (start != end && text.substring(start, end).contains("\n")) {
			int lineStart = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
			int position = lineStart;
			int adjustedEnd = end;
			while (position <= adjustedEnd) {
				editable.insert(position, "    ");
				adjustedEnd += 4;
				String updated = editable.toString();
				int nextLine = updated.indexOf('\n', position + 4);
				if (nextLine < 0 || nextLine >= adjustedEnd) {
					break;
				}
				position = nextLine + 1;
			}
			editor.setSelection(Math.min(start + 4, editable.length()), Math.min(adjustedEnd, editable.length()));
			return;
		}
		editable.replace(start, end, "    ");
		editor.setSelection(Math.min(start + 4, editable.length()));
	}

	private void selectWordAtCaret(EditText editor) {
		String text = editor.getText().toString();
		if (text.isEmpty()) {
			return;
		}
		int caret = Math.max(0, Math.min(editor.getSelectionStart(), text.length()));
		if (caret == text.length() && caret > 0) {
			caret--;
		}
		int start = caret;
		int end = caret;
		while (start > 0 && isEditorWordChar(text.charAt(start - 1))) {
			start--;
		}
		while (end < text.length() && isEditorWordChar(text.charAt(end))) {
			end++;
		}
		if (end > start) {
			editor.setSelection(start, end);
		}
	}

	private boolean isEditorWordChar(char ch) {
		return Character.isLetterOrDigit(ch) || ch == '_';
	}

	private String currentToken(EditText editor) {
		Editable text = editor.getText();
		int caret = Math.max(0, Math.min(editor.getSelectionStart(), text.length()));
		int start = caret;
		while (start > 0) {
			char ch = text.charAt(start - 1);
			if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '.') {
				break;
			}
			start--;
		}
		return text.subSequence(start, caret).toString();
	}

	private String completionFor(String token) {
		if (token.length() < 2) {
			return "";
		}
		for (String suggestion : localModuleCompletions()) {
			if (suggestion.startsWith(token) && suggestion.length() > token.length()) {
				return suggestion;
			}
		}
		String[] suggestions = {
				"print()", "property()", "open()", "len()", "list()", "dict()", "set()", "tuple()", "str()", "int()", "float()", "bool()",
				"enumerate()", "zip()", "sorted()", "sum()", "min()", "max()", "abs()", "round()", "isinstance()", "hasattr()", "getattr()",
				"from system.lib import minescript as ms", "from system.lib import java", "import math, heapq, threading, time",
				"from concurrent.futures import ThreadPoolExecutor", "java.import_pyjinn_script(\"pathfinding.pyj\")",
				"ms.execute()", "ms.echo()", "ms.chat()", "ms.log()", "ms.screenshot()",
				"ms.player_name()", "ms.player_position()", "ms.player_orientation()", "ms.player_health()",
				"ms.player_get_targeted_block()", "ms.player_get_targeted_entity()", "ms.player_look_at()",
				"ms.player_press_forward()", "ms.player_press_backward()", "ms.player_press_left()", "ms.player_press_right()",
				"ms.player_press_jump()", "ms.player_press_sprint()", "ms.player_press_sneak()", "ms.player_press_attack()",
				"ms.players()", "ms.entities()", "ms.world_info()", "ms.version_info()",
				"ms.getblock()", "ms.getblocklist()", "ms.get_block_region()", "ms.container_get_items()",
				"json.loads()", "json.dumps()", "Path()", "random.choice()", "random.randint()", "math.floor()", "math.ceil()",
				"settings[\"range\"]", "settings[\"delay\"]", "settings[\"replant\"]", "settings[\"whitelist\"]",
				"range()", "return ", "sleep()", "def ", "for ", "while ", "if ", "elif ", "else:", "True", "False", "None"
		};
		for (String suggestion : suggestions) {
			if (suggestion.startsWith(token) && suggestion.length() > token.length()) {
				return suggestion;
			}
		}
		return "";
	}

	private List<String> localModuleCompletions() {
		if (!localCompletionDirty) {
			return localCompletionCache;
		}
		List<String> suggestions = new ArrayList<>();
		for (Path script : editorScripts) {
			String name = script.getFileName().toString();
			String ext = extension(name);
			if (!ext.equalsIgnoreCase(".py") && !ext.equalsIgnoreCase(".pyj")) {
				continue;
			}
			String module = name.substring(0, name.length() - ext.length());
			if (module.isBlank() || module.equals("__init__")) {
				continue;
			}
			suggestions.add(module);
			suggestions.add("import " + module);
			suggestions.add("from " + module + " import ");
			if (suggestions.size() < 220) {
				for (String function : functionsInScript(script)) {
					suggestions.add(function);
					suggestions.add("from " + module + " import " + function);
				}
			}
			if (suggestions.size() >= 260) {
				break;
			}
		}
		localCompletionCache = List.copyOf(suggestions);
		localCompletionDirty = false;
		return localCompletionCache;
	}

	private List<String> functionsInScript(Path script) {
		if (script == null || ".pyj".equalsIgnoreCase(extension(script.getFileName().toString()))) {
			return List.of();
		}
		if (editorFunctionCache.containsKey(script)) {
			return editorFunctionCache.get(script);
		}
		try {
			if (Files.size(script) > 200_000) {
				return List.of();
			}
			List<String> functions = new ArrayList<>();
			Pattern functionPattern = Pattern.compile("^\\s*def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
			for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
				Matcher matcher = functionPattern.matcher(line);
				if (matcher.find()) {
					functions.add(matcher.group(1));
				}
				if (functions.size() >= 12) {
					break;
				}
			}
			List<String> cached = List.copyOf(functions);
			editorFunctionCache.put(script, cached);
			return cached;
		} catch (IOException ignored) {
			return List.of();
		}
	}

	private static final class ErrorUnderlineSpan extends CharacterStyle {
		@Override
		public void updateDrawState(TextPaint paint) {
			paint.setUnderline(true);
			paint.underlineColor = Color.argb(255, 255, 72, 96);
		}
	}

	private static final class SyntaxColorSpan extends CharacterStyle {
		private final int color;

		private SyntaxColorSpan(int color) {
			this.color = color;
		}

		@Override
		public void updateDrawState(TextPaint paint) {
			paint.setColor(color);
		}
	}

	private List<String> lintPython(String source) {
		List<String> issues = new ArrayList<>();
		String[] lines = source.split("\n", -1);
		int paren = 0;
		int bracket = 0;
		int brace = 0;
		boolean tripleDouble = false;
		boolean tripleSingle = false;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String trimmed = line.trim();
			int lineNumber = i + 1;
			if (line.indexOf('\t') >= 0) {
				issues.add("Line " + lineNumber + ": use spaces instead of tabs");
			}
			int leadingSpaces = line.length() - line.stripLeading().length();
			if (!trimmed.isEmpty() && leadingSpaces % 4 != 0) {
				issues.add("Line " + lineNumber + ": indentation should be a multiple of 4 spaces");
			}
			if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
				String withoutComment = stripPythonComment(line);
				if (!balancedQuotes(withoutComment)) {
					issues.add("Line " + lineNumber + ": possible unclosed string");
				}
				tripleDouble = toggleTriple(withoutComment, "\"\"\"", tripleDouble);
				tripleSingle = toggleTriple(withoutComment, "'''", tripleSingle);
				if (!tripleDouble && !tripleSingle) {
					for (int c = 0; c < withoutComment.length(); c++) {
						char ch = withoutComment.charAt(c);
						if (ch == '(') paren++;
						if (ch == ')') paren--;
						if (ch == '[') bracket++;
						if (ch == ']') bracket--;
						if (ch == '{') brace++;
						if (ch == '}') brace--;
					}
				}
				if (looksLikePythonBlock(trimmed) && !trimmed.endsWith(":")) {
					issues.add("Line " + lineNumber + ": block statement should end with ':'");
				}
				if (trimmed.startsWith("import ") && trimmed.endsWith(",")) {
					issues.add("Line " + lineNumber + ": dangling comma in import");
				}
				if (trimmed.length() > 96) {
					issues.add("Line " + lineNumber + ": line is longer than 96 characters");
				}
			}
		}
		if (paren != 0 || bracket != 0 || brace != 0) {
			issues.add("File: unmatched bracket or parenthesis");
		}
		if (tripleDouble || tripleSingle) {
			issues.add("File: unclosed triple-quoted string");
		}
		lintUndefinedNames(source, issues);
		return issues;
	}

	private void lintUndefinedNames(String source, List<String> issues) {
		Set<String> known = new HashSet<>(Set.of(
				"False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
				"def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
				"is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
				"abs", "all", "any", "ascii", "bin", "bool", "breakpoint", "bytearray", "bytes", "callable", "chr",
				"classmethod", "compile", "complex", "delattr", "dict", "dir", "divmod", "enumerate", "eval", "exec",
				"filter", "float", "format", "frozenset", "getattr", "globals", "hasattr", "hash", "help", "hex", "id",
				"input", "int", "isinstance", "issubclass", "iter", "len", "list", "locals", "map", "max", "memoryview",
				"min", "next", "object", "oct", "open", "ord", "pow", "print", "property", "range", "repr", "reversed",
				"round", "set", "setattr", "slice", "sorted", "staticmethod", "str", "sum", "super", "tuple", "type",
				"vars", "zip", "__name__", "__file__", "__builtins__"));
		String[] lines = source.split("\n", -1);
		Pattern identifier = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
		Pattern declaration = Pattern.compile("^\\s*(?:async\\s+)?(?:def|class)\\s+([A-Za-z_][A-Za-z0-9_]*)");
		Pattern assignment = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?::[^=]+)?=(?!=)");
		for (String line : lines) {
			String code = stripPythonComment(line);
			Matcher declared = declaration.matcher(code);
			if (declared.find()) {
				known.add(declared.group(1));
			}
			Matcher assigned = assignment.matcher(code);
			while (assigned.find()) {
				known.add(assigned.group(1));
			}
			Matcher loopVariable = Pattern.compile("\\bfor\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+in\\b").matcher(code);
			while (loopVariable.find()) {
				known.add(loopVariable.group(1));
			}
			String trimmed = code.trim();
			if (trimmed.startsWith("import ")) {
				for (String item : trimmed.substring(7).split(",")) {
					String[] parts = item.trim().split("\\s+as\\s+");
					String name = parts.length > 1 ? parts[1] : parts[0].split("\\.")[0];
					if (!name.isBlank()) known.add(name.trim());
				}
			} else if (trimmed.startsWith("from ") && trimmed.contains(" import ")) {
				String imported = trimmed.substring(trimmed.indexOf(" import ") + 8);
				for (String item : imported.split(",")) {
					String[] parts = item.trim().split("\\s+as\\s+");
					String name = parts.length > 1 ? parts[1] : parts[0];
					if (!name.isBlank() && !"*".equals(name.trim())) known.add(name.trim());
				}
			}
			Matcher parameters = Pattern.compile("(?:def|lambda)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(([^)]*)\\)").matcher(code);
			if (parameters.find()) {
				Matcher parameter = identifier.matcher(parameters.group(1));
				while (parameter.find()) known.add(parameter.group());
			}
		}

		for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
			String code = stripPythonStrings(stripPythonComment(lines[lineIndex]));
			String trimmed = code.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("import ") || trimmed.startsWith("from ")
					|| trimmed.startsWith("def ") || trimmed.startsWith("async def ") || trimmed.startsWith("class ")) {
				continue;
			}
			Matcher token = identifier.matcher(code);
			while (token.find()) {
				String name = token.group();
				int before = token.start() - 1;
				if (known.contains(name) || (before >= 0 && code.charAt(before) == '.')) {
					continue;
				}
				String after = code.substring(token.end()).stripLeading();
				if (after.startsWith("=") && !after.startsWith("==")) {
					continue;
				}
				issues.add("Line " + (lineIndex + 1) + ": undefined name '" + name + "'");
				break;
			}
		}
	}

	private String stripPythonStrings(String line) {
		StringBuilder result = new StringBuilder(line.length());
		char quote = 0;
		boolean escaped = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quote != 0) {
				result.append(' ');
				if (escaped) {
					escaped = false;
				} else if (ch == '\\') {
					escaped = true;
				} else if (ch == quote) {
					quote = 0;
				}
			} else if (ch == '\'' || ch == '"') {
				quote = ch;
				result.append(' ');
			} else {
				result.append(ch);
			}
		}
		return result.toString();
	}

	private boolean looksLikePythonBlock(String trimmed) {
		for (String keyword : new String[]{"def ", "class ", "if ", "elif ", "else", "for ", "while ", "try", "except", "finally", "with ", "async def "}) {
			if (trimmed.startsWith(keyword)) {
				return true;
			}
		}
		return false;
	}

	private String stripPythonComment(String line) {
		boolean single = false;
		boolean doubleQuote = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			char previous = i > 0 ? line.charAt(i - 1) : 0;
			if (ch == '\'' && !doubleQuote && previous != '\\') {
				single = !single;
			} else if (ch == '"' && !single && previous != '\\') {
				doubleQuote = !doubleQuote;
			} else if (ch == '#' && !single && !doubleQuote) {
				return line.substring(0, i);
			}
		}
		return line;
	}

	private boolean balancedQuotes(String line) {
		boolean single = false;
		boolean doubleQuote = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			char previous = i > 0 ? line.charAt(i - 1) : 0;
			if (ch == '\'' && !doubleQuote && previous != '\\') {
				single = !single;
			} else if (ch == '"' && !single && previous != '\\') {
				doubleQuote = !doubleQuote;
			}
		}
		return !single && !doubleQuote;
	}

	private boolean toggleTriple(String line, String token, boolean open) {
		int index = line.indexOf(token);
		while (index >= 0) {
			open = !open;
			index = line.indexOf(token, index + token.length());
		}
		return open;
	}

	private View consolePanel() {
		LinearLayout panel = column(8);
		panel.setPadding(16, 12, 16, 12);
		panel.setBackground(panel(14));

		LinearLayout top = row(10);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.addView(consoleTabButton("Console"), new LinearLayout.LayoutParams(82, 32));
		top.addView(consoleTabButton("Output"), new LinearLayout.LayoutParams(82, 32));
		top.addView(consoleTabButton("Errors"), new LinearLayout.LayoutParams(82, 32));
		View spacer = new View(requireContext());
		top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0F));
		View clear = textButton("Clear");
		clear.setOnClickListener(view -> {
			editorLogs.clear();
			refreshConsoleLogList();
		});
		top.addView(clear, new LinearLayout.LayoutParams(68, 34));
		panel.addView(top, new LinearLayout.LayoutParams(match(), 36));

		ScrollView scroll = layoutScrollView();
		consoleLogList = column(3);
		refreshConsoleLogList();
		scroll.addView(consoleLogList, new ScrollView.LayoutParams(match(), wrap(), Gravity.LEFT));
		panel.addView(scroll, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		return panel;
	}

	private View consoleTabButton(String tab) {
		View button = chip(tab, consoleTab.equals(tab));
		button.setOnClickListener(view -> {
			consoleTab = tab;
			renderShell();
		});
		return button;
	}

	private View editorSide() {
		LinearLayout side = column(12);
		side.addView(scriptInfoPanel(), new LinearLayout.LayoutParams(match(), 306));
		side.addView(editorQuickActions(), new LinearLayout.LayoutParams(match(), 344));
		View spacer = new View(requireContext());
		side.addView(spacer, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		return side;
	}

	private View scriptInfoPanel() {
		LinearLayout panel = column(14);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("code-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("SCRIPT INFO", 15, PURPLE));
		panel.addView(title);

		String name = selectedScript == null ? "No script" : selectedScript.getFileName().toString();
		LinearLayout hero = row(14);
		hero.setGravity(Gravity.CENTER_VERTICAL);
		hero.addView(iconBadge(scriptIcon(name), Color.argb(255, 255, 213, 78), accentDarkAlpha(145), 56, 10));
		LinearLayout copy = column(5);
		copy.addView(label(name, 17, TEXT));
		copy.addView(label("by Shulkr", 12, MUTED));
		hero.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		panel.addView(hero, new LinearLayout.LayoutParams(match(), 60));

		panel.addView(infoRow("Type:", selectedScript == null ? "-" : scriptType(selectedScript)));
		panel.addView(infoRow("Size:", selectedScript == null ? "-" : scriptSize(selectedScript)));
		panel.addView(infoRow("Modified:", selectedScript == null ? "-" : modifiedAgo(selectedScript) + " ago"));
		panel.addView(infoRow("Status:", currentScriptRunning ? "*  Running" : "Idle", currentScriptRunning ? GREEN : MUTED));
		panel.addView(infoRow("Module:", selectedScript != null && FluxusAppState.get().isScriptModule(selectedScriptRelativePath()) ? "yes" : "no",
				selectedScript != null && FluxusAppState.get().isScriptModule(selectedScriptRelativePath()) ? GREEN : MUTED));
		panel.addView(infoRow("Run safety:", scriptRunSafetyLabel(), scriptRunSafetyColor()));

		View actions = settingsActionRow("Script actions", "ellipsis-solid.png");
		actions.setOnClickListener(view -> {
			toggleDropdown("script-actions", view, 224);
		});
		panel.addView(actions, new LinearLayout.LayoutParams(match(), 42));
		return panel;
	}

	private View scriptInfoDropdown() {
		LinearLayout menu = column(6);
		menu.setPadding(8, 8, 8, 8);
		menu.setBackground(dropdownSurface(10));
		View rename = dropdownAction("Rename script", "eye-dropper-solid.png", () -> {
			openDropdownKey = "";
			beginRename(selectedScript);
		});
		menu.addView(rename, new LinearLayout.LayoutParams(match(), 30));
		View export = dropdownAction("Export script", "folder-open-solid.png", () -> {
			openDropdownKey = "";
			exportCurrentScript();
		});
		menu.addView(export, new LinearLayout.LayoutParams(match(), 30));
		View copyImport = dropdownAction("Copy import", "clone-solid.png", () -> {
			openDropdownKey = "";
			copySelectedScriptImport();
		});
		menu.addView(copyImport, new LinearLayout.LayoutParams(match(), 30));
		boolean isModule = FluxusAppState.get().isScriptModule(selectedScriptRelativePath());
		View module = dropdownAction(isModule ? "Unmark module" : "Mark as module", "box-solid.png", () -> {
			openDropdownKey = "";
			toggleSelectedScriptModule();
		});
		menu.addView(module, new LinearLayout.LayoutParams(match(), 30));
		View publish = dropdownAction("Publish script", "folder-plus-solid.png", () -> {
			openDropdownKey = "";
			openPublishModal(selectedScript);
		});
		menu.addView(publish, new LinearLayout.LayoutParams(match(), 30));
		View delete = dropdownAction("Delete script", "broom-solid.png", () -> {
			openDropdownKey = "";
			deleteCurrentScript();
		});
		menu.addView(delete, new LinearLayout.LayoutParams(match(), 30));
		return menu;
	}

	private View smartInsertDropdown() {
		LinearLayout menu = column(6);
		menu.setPadding(8, 8, 8, 8);
		menu.setBackground(dropdownSurface(10));
		menu.addView(dropdownAction("Minescript imports", "code-solid.png", () -> insertEditorSnippet("from system.lib import minescript as ms\n\n")), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Pyjinn import bridge", "route-solid.png", () -> insertEditorSnippet("from system.lib import java\n\npathfinder = java.import_pyjinn_script(\"pathfinding.pyj\")\n")), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Safe path wait loop", "route-solid.png", () -> insertEditorSnippet("future = pathfinder.get(\"goto\")(x, y, z)\ndeadline = time.time() + 30.0\nwhile not future.isDone():\n    if time.time() > deadline:\n        ms.echo(\"Path timed out\")\n        break\n    time.sleep(0.05)\n")), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Tick-safe command loop", "play-solid.png", () -> insertEditorSnippet("for index, command in enumerate(commands):\n    ms.execute(command)\n    if index % 12 == 0:\n        time.sleep(0.05)\n")), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("WindowSpy target block", "magnifying-glass-solid.png", () -> insertEditorSnippet("target = ms.player_get_targeted_block(8)\nif target:\n    ms.echo(str(target))\n")), new LinearLayout.LayoutParams(match(), 30));
		return menu;
	}

	private View editorQuickActions() {
		LinearLayout panel = column(10);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));
		panel.addView(label("QUICK ACTIONS", 15, PURPLE));
		View format = editorActionRow("arrows-rotate-solid.png", "Format Code");
		format.setOnClickListener(view -> formatCurrentEditor());
		panel.addView(format, new LinearLayout.LayoutParams(match(), 42));
		View find = editorActionRow("magnifying-glass-solid.png", "Find / Replace");
		find.setOnClickListener(view -> findNextMinescriptCall());
		panel.addView(find, new LinearLayout.LayoutParams(match(), 42));
		View comment = editorActionRow("code-solid.png", "Toggle Comment");
		comment.setOnClickListener(view -> toggleCommentCurrentLine());
		panel.addView(comment, new LinearLayout.LayoutParams(match(), 42));
		View smartInsert = editorActionRow("plus-solid.png", "Smart Insert");
		smartInsert.setOnClickListener(view -> {
			toggleDropdown("smart-insert", view, 244);
		});
		panel.addView(smartInsert, new LinearLayout.LayoutParams(match(), 42));
		View modules = editorActionRow("box-solid.png", "Open Libraries");
		modules.setOnClickListener(view -> openPage(Page.MODULES));
		panel.addView(modules, new LinearLayout.LayoutParams(match(), 42));
		return panel;
	}

	private View pageFrame(View main, View side) {
		FrameLayout outer = new FrameLayout(requireContext());
		currentPageFrame = outer;
		LinearLayout frame = column(pageGap());
		View header = topToolbar();
		currentHeader = header;
		if (settingsConfig.headerBehaviour().equals("Sticky")) {
			header.setBackground(glass(Color.argb(176, 12, 17, 30), Color.argb(196, 7, 12, 22), 10, STROKE));
		}
		if (settingsConfig.headerBehaviour().equals("Hide while scrolling") && headerHidden) {
			header.setVisibility(View.GONE);
		}
		frame.addView(header, new LinearLayout.LayoutParams(match(), effectiveTopBarHeight()));
		LinearLayout body = row(pageGap());
		body.addView(main, new LinearLayout.LayoutParams(0, match(), 1.0F));
		addConfiguredRightPanel(body, side);
		if (lastAnimatedPage != page) {
			animatePageSwap(body);
			lastAnimatedPage = page;
		}
		frame.addView(body, new LinearLayout.LayoutParams(match(), 0, 1.0F));
		outer.addView(frame, new FrameLayout.LayoutParams(match(), match()));
		View dropdown = floatingDropdownForPage();
		if (dropdown != null) {
			currentFloatingDropdown = dropdown;
			View clickAway = new View(requireContext());
			clickAway.setBackground(round(Color.TRANSPARENT, 0, 0));
			clickAway.setOnClickListener(view -> closeFloatingDropdown(null));
			outer.addView(clickAway, new FrameLayout.LayoutParams(match(), match()));
			animateFloatingSurface(dropdown, openDropdownKey.equals("publish-modal"));
			outer.addView(dropdown, floatingDropdownLayoutParams());
		}
		return outer;
	}

	private void addConfiguredRightPanel(LinearLayout body, View side) {
		String behaviour = settingsConfig.rightPanelBehaviour();
		if (behaviour.equals("Hidden") || (behaviour.equals("Contextual") && !pageHasContextualPanel())) {
			return;
		}
		if (!behaviour.equals("Collapsed by default")) {
			body.addView(side, new LinearLayout.LayoutParams(SIDE_WIDTH, match()));
			return;
		}
		LinearLayout collapsible = row(8);
		collapsible.setGravity(Gravity.TOP);
		View toggle = iconButton(rightPanelExpanded ? "chevron-right-solid.png" : "chevron-left-solid.png", PURPLE);
		toggle.setOnClickListener(view -> {
			rightPanelExpanded = !rightPanelExpanded;
			renderShell();
		});
		collapsible.addView(toggle, new LinearLayout.LayoutParams(42, 42));
		if (rightPanelExpanded) {
			collapsible.addView(side, new LinearLayout.LayoutParams(SIDE_WIDTH, match()));
		}
		body.addView(collapsible, new LinearLayout.LayoutParams(rightPanelExpanded ? SIDE_WIDTH + 50 : 42, match()));
	}

	private boolean pageHasContextualPanel() {
		return page == Page.SCRIPTS || page == Page.EDITOR || page == Page.MODULES || page == Page.ADDONS
				|| page == Page.TEMPLATES || page == Page.WINDOWSPY || page == Page.OVERLAYS;
	}

	private boolean useCompactNavigation() {
		String mode = settingsConfig.navigationMode();
		if (mode.equals("Compact icon rail")) {
			return true;
		}
		if (!mode.equals("Auto-collapse")) {
			return false;
		}
		int width = shell == null ? 0 : shell.getWidth();
		if (width <= 0 && Minecraft.getInstance().getWindow() != null) {
			width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		}
		return width > 0 && width < 1280;
	}

	private int configuredSidebarWidth() {
		try {
			return Integer.parseInt(settingsConfig.sidebarWidth().replace(" px", "").trim());
		} catch (NumberFormatException ignored) {
			return 300;
		}
	}

	private int contentWidthInset() {
		return switch (settingsConfig.contentWidth()) {
			case "Centered" -> 72;
			case "Full width" -> 0;
			default -> 24;
		};
	}

	private int densityOuterPadding() {
		return switch (settingsConfig.density()) {
			case "Compact" -> 12;
			case "Spacious" -> 24;
			default -> 18;
		};
	}

	private int effectiveTopBarHeight() {
		return switch (settingsConfig.density()) {
			case "Compact" -> 48;
			case "Spacious" -> 64;
			default -> TOP_BAR_HEIGHT;
		};
	}

	private int settingsRowHeight() {
		return switch (settingsConfig.density()) {
			case "Compact" -> 34;
			case "Spacious" -> 42;
			default -> 38;
		};
	}

	private int pageGap() {
		return switch (settingsConfig.pageSpacing()) {
			case "Compact" -> 12;
			case "Spacious" -> 28;
			default -> 20;
		};
	}

	private View floatingDropdownForPage() {
		if (openDropdownKey.equals("publish-modal")) {
			return publishScriptModal();
		}
		if (openDropdownKey.equals("new-script") && (page == Page.SCRIPTS || page == Page.EDITOR)) {
			return newScriptDropdownMenu();
		}
		if (openDropdownKey.equals("library-script-actions") && page == Page.SCRIPTS) {
			return libraryScriptDropdown();
		}
		if (openDropdownKey.equals("editor-script-context") && page == Page.EDITOR) {
			return editorScriptContextMenu();
		}
		if (openDropdownKey.equals("script-actions") && (page == Page.EDITOR || page == Page.SCRIPTS)) {
			return scriptInfoDropdown();
		}
		if (openDropdownKey.equals("smart-insert") && page == Page.EDITOR) {
			return smartInsertDropdown();
		}
		if (openDropdownKey.equals("command-palette")) {
			return commandPaletteDropdown();
		}
		if (page == Page.SETTINGS && isSettingsDropdown(openDropdownKey)) {
			return settingsFloatingDropdown();
		}
		return null;
	}

	private FrameLayout.LayoutParams floatingDropdownLayoutParams() {
		if (openDropdownKey.equals("publish-modal")) {
			return new FrameLayout.LayoutParams(720, 520, Gravity.CENTER);
		}
		if (openDropdownKey.equals("new-script")) {
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(220, 118, Gravity.TOP | Gravity.LEFT);
			int x = clampedDropdownX(220);
			params.setMargins(x, dropdownAnchorY + 6, 0, 0);
			return params;
		}
		if (openDropdownKey.equals("script-actions")) {
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(224, 208, Gravity.TOP | Gravity.LEFT);
			int x = clampedDropdownX(224);
			params.setMargins(x, dropdownAnchorY + 6, 0, 0);
			return params;
		}
		if (openDropdownKey.equals("smart-insert")) {
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(244, 178, Gravity.TOP | Gravity.LEFT);
			int x = clampedDropdownX(244);
			params.setMargins(x, dropdownAnchorY + 6, 0, 0);
			return params;
		}
		if (openDropdownKey.equals("library-script-actions")) {
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(236, 148, Gravity.TOP | Gravity.LEFT);
			int x = clampedDropdownX(236);
			params.setMargins(x, dropdownAnchorY + 6, 0, 0);
			return params;
		}
		if (openDropdownKey.equals("editor-script-context")) {
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(232, 188, Gravity.TOP | Gravity.LEFT);
			int x = clampedDropdownX(232);
			params.setMargins(x, dropdownAnchorY + 6, 0, 0);
			return params;
		}
		if (openDropdownKey.equals("command-palette")) {
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(360, 300, Gravity.TOP | Gravity.LEFT);
			int x = clampedDropdownX(360);
			params.setMargins(x, dropdownAnchorY + 6, 0, 0);
			return params;
		}
		int width = Math.max(260, dropdownAnchorWidth);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, dropdownOptionsHeight(settingsDropdownOptions(openDropdownKey).length), Gravity.TOP | Gravity.LEFT);
		int x = clampedDropdownX(width);
		params.setMargins(x, dropdownAnchorY + 6, 0, 0);
		return params;
	}

	private void toggleDropdown(String key, View anchor, int preferredWidth) {
		if (openDropdownKey.equals(key)) {
			closeFloatingDropdown(null);
			return;
		}
		dropdownClosing = false;
		openDropdownKey = key;
		dropdownAnchorWidth = Math.max(preferredWidth, anchor.getWidth());
		int[] anchorLocation = new int[2];
		int[] frameLocation = new int[2];
		anchor.getLocationInWindow(anchorLocation);
		if (currentPageFrame != null) {
			currentPageFrame.getLocationInWindow(frameLocation);
		}
		dropdownAnchorX = anchorLocation[0] - frameLocation[0];
		dropdownAnchorY = anchorLocation[1] - frameLocation[1] + anchor.getHeight();
		renderShell();
	}

	private void openCommandPalette(View anchor) {
		toggleDropdown("command-palette", anchor, 360);
	}

	private int clampedDropdownX(int width) {
		int frameWidth = currentPageFrame == null ? 0 : currentPageFrame.getWidth();
		if (frameWidth <= 0) {
			return Math.max(8, dropdownAnchorX);
		}
		int max = Math.max(0, frameWidth - width - 8);
		return Math.max(8, Math.min(dropdownAnchorX, max));
	}

	private View newScriptDropdownMenu() {
		LinearLayout menu = column(6);
		menu.setPadding(8, 8, 8, 8);
		menu.setBackground(dropdownSurface(10));
		menu.addView(dropdownAction("Python script", "code-solid.png", () -> {
			openDropdownKey = "";
			createNewScript("NewScript.py", "import minescript as ms\n\nms.echo(\"Hello from {name}!\")\n");
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Pyjinn script", "code-solid.png", () -> {
			openDropdownKey = "";
			createNewScript("NewScript.pyj", "import minescript\n\nminescript.echo(\"Hello from {name}!\")\n");
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Text note", "clipboard-solid.png", () -> {
			openDropdownKey = "";
			createNewScript("NewScript.txt", "# {name}\n\n");
		}), new LinearLayout.LayoutParams(match(), 30));
		return menu;
	}

	private View libraryScriptDropdown() {
		LinearLayout menu = column(6);
		menu.setPadding(8, 8, 8, 8);
		menu.setBackground(dropdownSurface(10));
		menu.addView(dropdownAction("Install locally", "download-solid.png", () -> {
			openDropdownKey = "";
			installPublishedScript(selectedLibraryScriptId);
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Copy script code", "code-solid.png", () -> {
			openDropdownKey = "";
			copyPublishedScriptCode(selectedLibraryScriptId);
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Copy about", "clipboard-solid.png", () -> {
			openDropdownKey = "";
			copyPublishedScriptAbout(selectedLibraryScriptId);
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Delete publish", "broom-solid.png", () -> {
			openDropdownKey = "";
			deletePublishedScript(selectedLibraryScriptId);
		}), new LinearLayout.LayoutParams(match(), 30));
		return menu;
	}

	private View commandPaletteDropdown() {
		LinearLayout menu = column(7);
		menu.setPadding(10, 10, 10, 10);
		menu.setBackground(dropdownSurface(12));
		menu.addView(label("COMMAND PALETTE", 12, PURPLE), new LinearLayout.LayoutParams(match(), 24));
		menu.addView(dropdownAction("Open Editor", "code-solid.png", () -> {
			openDropdownKey = "";
			openPage(Page.EDITOR);
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Create Python Script", "plus-solid.png", () -> {
			openDropdownKey = "";
			createNewScript("NewScript.py", "import minescript as ms\n\nms.echo(\"Hello from {name}!\")\n");
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Save Current Script", "clipboard-check-solid.png", () -> {
			openDropdownKey = "";
			saveCurrentScript();
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Run Current Script", "play-solid.png", () -> {
			openDropdownKey = "";
			runCurrentScript();
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Open Libraries", "box-solid.png", () -> {
			openDropdownKey = "";
			openPage(Page.MODULES);
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Open Overlays", "border-none-solid.png", () -> {
			openDropdownKey = "";
			openPage(Page.OVERLAYS);
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Toggle Overlay Edit Mode", "eye-dropper-solid.png", () -> {
			openDropdownKey = "";
			enterOverlayEditMode();
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Refresh UI State", "arrows-rotate-solid.png", () -> {
			openDropdownKey = "";
			refreshEditorPage("Refreshed from command palette.");
		}), new LinearLayout.LayoutParams(match(), 30));
		return menu;
	}

	private View publishScriptModal() {
		refreshEditorScripts();
		LinearLayout modal = column(14);
		modal.setPadding(22, 20, 22, 20);
		modal.setBackground(glass(Color.argb(224, 12, 17, 30), Color.argb(196, 7, 12, 22), 18, STROKE_HOVER));

		LinearLayout title = row(12);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(iconBadge("folder-plus-solid.png", PURPLE, accentDarkAlpha(118), 46, 11));
		LinearLayout copy = column(4);
		copy.addView(label("Publish Script", 22, TEXT));
		copy.addView(label("Package a local script for the Shulkr community library.", 13, MUTED));
		title.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View close = iconButton("ellipsis-solid.png", MUTED);
		close.setOnClickListener(view -> {
			openDropdownKey = "";
			renderShell();
		});
		title.addView(close, new LinearLayout.LayoutParams(38, 38));
		modal.addView(title, new LinearLayout.LayoutParams(match(), 54));

		LinearLayout sourceRow = row(10);
		sourceRow.setGravity(Gravity.CENTER_VERTICAL);
		sourceRow.setPadding(12, 0, 12, 0);
		sourceRow.setBackground(round(Color.argb(112, 18, 24, 39), 10, STROKE));
		sourceRow.addView(icon("code-solid.png", MUTED), new LinearLayout.LayoutParams(16, 16));
		String sourceLabel = publishSourceScript == null ? "No script selected" : displayScriptPath(publishSourceScript);
		sourceRow.addView(label(sourceLabel, 12, publishSourceScript == null ? FAINT : TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View choose = textButton("Choose Script");
		choose.setOnClickListener(view -> choosePublishSourceFile());
		sourceRow.addView(choose, new LinearLayout.LayoutParams(130, 34));
		modal.addView(sourceRow, new LinearLayout.LayoutParams(match(), 46));

		LinearLayout localChoices = row(8);
		int shown = 0;
		for (Path script : editorScripts) {
			if (shown >= 4) {
				break;
			}
			TextView choice = label(displayScriptPath(script), 11, script.equals(publishSourceScript) ? TEXT : MUTED);
			choice.setGravity(Gravity.CENTER);
			choice.setSingleLine(true);
			makeHover(choice, round(script.equals(publishSourceScript) ? accentDarkAlpha(150) : Color.argb(92, 18, 24, 39), 8,
							script.equals(publishSourceScript) ? STROKE_HOVER : STROKE),
					round(accentAlpha(118), 8, STROKE_HOVER));
			choice.setOnClickListener(view -> openPublishModal(script));
			localChoices.addView(choice, new LinearLayout.LayoutParams(0, 30, 1.0F));
			shown++;
		}
		if (shown > 0) {
			modal.addView(localChoices, new LinearLayout.LayoutParams(match(), 32));
		}

		LinearLayout fields = row(14);
		LinearLayout left = column(10);
		left.addView(publishInput("Name", publishName, false, text -> publishName = text), new LinearLayout.LayoutParams(match(), 56));
		left.addView(publishInput("Author", publishAuthor, false, text -> publishAuthor = text), new LinearLayout.LayoutParams(match(), 56));
		left.addView(publishInput("File name", publishFileName, false, text -> publishFileName = text), new LinearLayout.LayoutParams(match(), 56));
		left.addView(publishInput("Tags", publishTags, false, text -> publishTags = text), new LinearLayout.LayoutParams(match(), 56));
		fields.addView(left, new LinearLayout.LayoutParams(0, match(), 1.0F));

		LinearLayout right = column(10);
		right.addView(publishInput("About", publishAbout, true, text -> publishAbout = text), new LinearLayout.LayoutParams(match(), 132));
		right.addView(label("Icon", 12, MUTED), new LinearLayout.LayoutParams(match(), 18));
		LinearLayout icons = row(8);
		for (String iconFile : new String[]{"code-solid.png", "broom-solid.png", "box-open-solid.png", "user-solid.png", "route-solid.png", "clipboard-solid.png"}) {
			View iconChoice = iconButton(iconFile, iconFile.equals(publishIcon) ? PURPLE : MUTED);
			iconChoice.setBackground(round(iconFile.equals(publishIcon) ? accentDarkAlpha(150) : Color.argb(86, 18, 24, 39), 10,
					iconFile.equals(publishIcon) ? STROKE_HOVER : STROKE));
			iconChoice.setOnClickListener(view -> {
				publishIcon = iconFile;
				renderShell();
			});
			icons.addView(iconChoice, new LinearLayout.LayoutParams(42, 40));
		}
		right.addView(icons, new LinearLayout.LayoutParams(match(), 44));
		TextView hint = label("Tip: the first # comment becomes the about text automatically. Tags are comma separated.", 12, FAINT);
		right.addView(hint, new LinearLayout.LayoutParams(match(), 44));
		fields.addView(right, new LinearLayout.LayoutParams(0, match(), 1.0F));
		modal.addView(fields, new LinearLayout.LayoutParams(match(), shown > 0 ? 206 : 246));

		LinearLayout actions = row(12);
		actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
		View cancel = textButton("Cancel");
		cancel.setOnClickListener(view -> {
			openDropdownKey = "";
			renderShell();
		});
		actions.addView(cancel, new LinearLayout.LayoutParams(110, 40));
		View publish = primaryActionButton("folder-plus-solid.png", "Publish");
		publish.setOnClickListener(view -> publishModalScript());
		actions.addView(publish, new LinearLayout.LayoutParams(132, 40));
		modal.addView(actions, new LinearLayout.LayoutParams(match(), 44));
		return modal;
	}

	private View publishInput(String title, String value, boolean multiline, Consumer<String> onChange) {
		LinearLayout box = column(4);
		box.addView(label(title, 12, MUTED), new LinearLayout.LayoutParams(match(), 16));
		EditText input = new EditText(requireContext());
		input.setText(value == null ? "" : value);
		input.setSingleLine(!multiline);
		input.setMinLines(multiline ? 4 : 1);
		input.setTextSize(12);
		input.setTextColor(TEXT);
		input.setHintTextColor(FAINT);
		input.setPadding(10, 0, 10, 0);
		input.setBackground(round(Color.argb(118, 10, 15, 26), 8, STROKE));
		input.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence text, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence text, int start, int before, int count) {
				onChange.accept(text.toString());
			}

			@Override
			public void afterTextChanged(Editable editable) {
			}
		});
		box.addView(input, new LinearLayout.LayoutParams(match(), multiline ? 100 : 34));
		return box;
	}

	private String displayScriptPath(Path path) {
		if (path == null) {
			return "";
		}
		try {
			Path normalizedScriptDir = scriptDir.toAbsolutePath().normalize();
			Path normalized = path.toAbsolutePath().normalize();
			if (normalized.startsWith(normalizedScriptDir)) {
				return normalizedScriptDir.relativize(normalized).toString().replace('\\', '/');
			}
		} catch (Exception ignored) {
		}
		return path.toString();
	}

	private View editorScriptContextMenu() {
		LinearLayout menu = column(6);
		menu.setPadding(8, 8, 8, 8);
		menu.setBackground(dropdownSurface(10));
		boolean folder = contextEditorItem != null && Files.isDirectory(contextEditorItem);
		if (!folder) {
			menu.addView(dropdownAction("Open script", "code-solid.png", () -> {
				openDropdownKey = "";
				if (contextScript != null) {
					selectEditorScript(contextScript);
				}
			}), new LinearLayout.LayoutParams(match(), 30));
		} else {
			menu.addView(dropdownAction(collapsedEditorFolders.contains(contextEditorItem) ? "Expand folder" : "Collapse folder",
					"folder-open-solid.png", () -> {
						openDropdownKey = "";
						if (contextEditorItem != null) {
							if (collapsedEditorFolders.contains(contextEditorItem)) {
								collapsedEditorFolders.remove(contextEditorItem);
							} else {
								collapsedEditorFolders.add(contextEditorItem);
							}
							renderShell();
						}
					}), new LinearLayout.LayoutParams(match(), 30));
		}
		menu.addView(dropdownAction("Rename", "eye-dropper-solid.png", () -> {
			openDropdownKey = "";
			beginRename(contextEditorItem);
		}), new LinearLayout.LayoutParams(match(), 30));
		if (!folder) {
			menu.addView(dropdownAction("Publish script", "folder-plus-solid.png", () -> {
				openDropdownKey = "";
				openPublishModal(contextScript);
			}), new LinearLayout.LayoutParams(match(), 30));
		}
		menu.addView(dropdownAction(selectedEditorItems.size() > 1 ? "Delete selected" : "Delete", "broom-solid.png", () -> {
			openDropdownKey = "";
			deleteSelectedEditorItems();
		}), new LinearLayout.LayoutParams(match(), 30));
		menu.addView(dropdownAction("Open folder", "folder-open-solid.png", () -> {
			openDropdownKey = "";
			if (contextEditorItem != null) {
				Path folderPath = Files.isDirectory(contextEditorItem) ? contextEditorItem : contextEditorItem.getParent();
				if (folderPath != null) {
					openFolder(folderPath, "Script folder");
				}
			}
		}), new LinearLayout.LayoutParams(match(), 30));
		return menu;
	}

	private boolean isSettingsDropdown(String key) {
		return key.equals("theme") || key.equals("accent") || key.equals("density")
				|| key.equals("sidebar-width") || key.equals("navigation-mode") || key.equals("content-width")
				|| key.equals("right-panel") || key.equals("page-spacing") || key.equals("header-behaviour")
				|| key.equals("indentation") || key.equals("telemetry");
	}

	private View settingsFloatingDropdown() {
		String key = openDropdownKey;
		String selected = settingsDropdownSelected(key);
		String[] options = settingsDropdownOptions(key);
		return dropdownMenu(selected, options, option -> {
			openDropdownKey = "";
			applySettingsDropdownSelection(key, option);
		}, key.equals("accent"));
	}

	private String settingsDropdownSelected(String key) {
		if (key.equals("theme")) {
			return settingsConfig.theme();
		}
		if (key.equals("accent")) {
			return settingsConfig.accent();
		}
		if (key.equals("density")) {
			return settingsConfig.density();
		}
		if (key.equals("sidebar-width")) {
			return settingsConfig.sidebarWidth();
		}
		if (key.equals("navigation-mode")) {
			return settingsConfig.navigationMode();
		}
		if (key.equals("content-width")) {
			return settingsConfig.contentWidth();
		}
		if (key.equals("right-panel")) {
			return settingsConfig.rightPanelBehaviour();
		}
		if (key.equals("page-spacing")) {
			return settingsConfig.pageSpacing();
		}
		if (key.equals("header-behaviour")) {
			return settingsConfig.headerBehaviour();
		}
		if (key.equals("indentation")) {
			return "Spaces: 4";
		}
		if (key.equals("telemetry")) {
			return "Local only";
		}
		return "";
	}

	private String[] settingsDropdownOptions(String key) {
		if (key.equals("theme")) {
			return THEME_OPTIONS;
		}
		if (key.equals("accent")) {
			return ACCENT_OPTIONS;
		}
		if (key.equals("density")) {
			return DENSITY_OPTIONS;
		}
		if (key.equals("sidebar-width")) {
			return SIDEBAR_WIDTH_OPTIONS;
		}
		if (key.equals("navigation-mode")) {
			return NAVIGATION_MODE_OPTIONS;
		}
		if (key.equals("content-width")) {
			return CONTENT_WIDTH_OPTIONS;
		}
		if (key.equals("right-panel")) {
			return RIGHT_PANEL_OPTIONS;
		}
		if (key.equals("page-spacing")) {
			return PAGE_SPACING_OPTIONS;
		}
		if (key.equals("header-behaviour")) {
			return HEADER_BEHAVIOUR_OPTIONS;
		}
		if (key.equals("indentation")) {
			return new String[]{"Spaces: 4", "Spaces: 2", "Tabs"};
		}
		if (key.equals("telemetry")) {
			return new String[]{"Local only", "Off", "Diagnostics only"};
		}
		return new String[0];
	}

	private void applySettingsDropdownSelection(String key, String option) {
		if (key.equals("theme")) {
			settingsConfig.setTheme(option);
			saveSettingsConfig("Theme set to " + option + ".");
			return;
		}
		if (key.equals("accent")) {
			settingsConfig.setAccent(option);
			saveSettingsConfig("Accent set to " + option + ".");
			return;
		}
		if (key.equals("density")) {
			settingsConfig.setDensity(option);
			saveSettingsConfig("Density set to " + option + ".");
			return;
		}
		if (key.equals("sidebar-width")) {
			settingsConfig.setSidebarWidth(option);
			saveSettingsConfig("Sidebar width set to " + option + ".");
			return;
		}
		if (key.equals("navigation-mode")) {
			settingsConfig.setNavigationMode(option);
			saveSettingsConfig("Navigation mode set to " + option + ".");
			return;
		}
		if (key.equals("content-width")) {
			settingsConfig.setContentWidth(option);
			saveSettingsConfig("Content width set to " + option + ".");
			return;
		}
		if (key.equals("right-panel")) {
			settingsConfig.setRightPanelBehaviour(option);
			rightPanelExpanded = false;
			saveSettingsConfig("Right panel behaviour set to " + option + ".");
			return;
		}
		if (key.equals("page-spacing")) {
			settingsConfig.setPageSpacing(option);
			saveSettingsConfig("Page spacing set to " + option + ".");
			return;
		}
		if (key.equals("header-behaviour")) {
			settingsConfig.setHeaderBehaviour(option);
			headerHidden = false;
			saveSettingsConfig("Header behaviour set to " + option + ".");
			return;
		}
		if (key.equals("indentation")) {
			saveUiMessage("Indentation set to " + option + ".");
			return;
		}
		if (key.equals("telemetry")) {
			saveUiMessage("Telemetry set to " + option + ".");
		}
	}


	private View topToolbar() {
		LinearLayout top = row(16);
		top.setGravity(Gravity.CENTER_VERTICAL);

		String placeholder = page == Page.SCRIPTS ? "Search scripts, e.g. AutoCrystal..."
				: page == Page.EDITOR ? "Search scripts, commands, snippets..."
				: page == Page.MODULES ? "Search libraries, e.g. nbtlib, matplotlib..."
				: page == Page.ADDONS ? "Search Shulkr modules and addons..."
				: page == Page.TEMPLATES ? "Search templates, e.g. AutoFarm, HUD, commands..."
				: page == Page.WINDOWSPY ? "Search blocks, entities, NBT keys, events..."
				: page == Page.SETTINGS ? "Search settings, files, themes, keybinds..."
				: page == Page.OVERLAYS ? "Search overlays, widgets, HUD presets..."
				: page == Page.ABOUT ? "Search about, credits, versions, changelog..."
				: "Search scripts, libraries, templates...";
		top.addView(searchBar(placeholder), new LinearLayout.LayoutParams(SEARCH_WIDTH, 48));

		View spacer = new View(requireContext());
		top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0F));
		if (page == Page.SCRIPTS) {
			View publishButton = primaryActionButton("folder-plus-solid.png", "Publish");
			publishButton.setOnClickListener(view -> openPublishModal(selectedScript));
			top.addView(publishButton, new LinearLayout.LayoutParams(126, 42));
			View refreshButton = toolbarButton("arrows-rotate-solid.png", "Refresh");
			refreshButton.setOnClickListener(view -> renderShell());
			top.addView(refreshButton, new LinearLayout.LayoutParams(116, 42));
			top.addView(iconOnly("bell-solid.png"), new LinearLayout.LayoutParams(42, 42));
		} else if (page == Page.EDITOR) {
			View newButton = primaryButton("plus-solid.png", "New Script", "chevron-down-solid.png");
			newButton.setOnClickListener(view -> {
				toggleDropdown("new-script", view, 220);
			});
			top.addView(newButton, new LinearLayout.LayoutParams(156, 42));
			View openButton = toolbarButton("folder-open-solid.png", "Open");
			openButton.setOnClickListener(view -> openScriptFilePicker());
			top.addView(openButton, new LinearLayout.LayoutParams(96, 42));
			View saveButton = toolbarButton("clipboard-solid.png", "Save");
			saveButton.setOnClickListener(view -> saveCurrentScript());
			top.addView(saveButton, new LinearLayout.LayoutParams(96, 42));
			View runButton = primaryActionButton("play-solid.png", "Run");
			runButton.setOnClickListener(view -> runCurrentScript());
			top.addView(runButton, new LinearLayout.LayoutParams(92, 42));
			View stopButton = toolbarButton("border-all-solid.png", "Stop");
			stopButton.setOnClickListener(view -> stopScripts());
			top.addView(stopButton, new LinearLayout.LayoutParams(92, 42));
		} else if (page == Page.OVERLAYS) {
			View widgetButton = primaryActionButton("plus-solid.png", "Widget");
			widgetButton.setOnClickListener(view -> createOverlayWidget());
			top.addView(widgetButton, new LinearLayout.LayoutParams(112, 42));
			View editButton = toolbarButton("eye-dropper-solid.png", "Edit");
			editButton.setOnClickListener(view -> enterOverlayEditMode());
			top.addView(editButton, new LinearLayout.LayoutParams(96, 42));
			View saveButton = toolbarButton("clipboard-check-solid.png", "Save");
			saveButton.setOnClickListener(view -> saveOverlayPreset());
			top.addView(saveButton, new LinearLayout.LayoutParams(96, 42));
			View toggleButton = toolbarButton(overlayRendererActive ? "border-none-solid.png" : "play-solid.png", overlayRendererActive ? "Hide" : "Show");
			toggleButton.setOnClickListener(view -> {
				overlayRendererActive = !overlayRendererActive;
				ShulkrHudOverlay.setRendererActive(overlayRendererActive);
				ShulkrHudOverlay.setEditMode(overlayRendererActive && overlayEditMode);
				if (!overlayRendererActive) {
					overlayEditMode = false;
				}
				overlayMessage = overlayRendererActive ? "Overlay HUD is now visible in-game." : "Overlay HUD hidden.";
				renderShell();
			});
			top.addView(toggleButton, new LinearLayout.LayoutParams(104, 42));
		} else if (page == Page.MODULES || page == Page.ADDONS || page == Page.TEMPLATES || page == Page.WINDOWSPY || page == Page.SETTINGS
				|| page == Page.ABOUT) {
			top.addView(iconOnly("bell-solid.png"), new LinearLayout.LayoutParams(42, 42));
		}
		return top;
	}

	private List<LibraryScriptItem> visiblePublishedScripts() {
		List<LibraryScriptItem> sorted = FluxusAppState.get().libraryScripts();
		if (scriptFilter.equals("All")) {
			return sorted;
		}
		if (scriptFilter.equals("Recent")) {
			return sorted.stream().limit(8).toList();
		}
		return sorted.stream()
				.filter(script -> publishedScriptMatchesFilter(script, scriptFilter))
				.toList();
	}

	private boolean publishedScriptMatchesFilter(LibraryScriptItem script, String filter) {
		String ext = extension(script.fileName()).toLowerCase(Locale.ROOT);
		if (filter.equals("Python")) {
			return ext.equals(".py");
		}
		if (filter.equals("Pyjinn")) {
			return ext.equals(".pyj");
		}
		return script.category().equals(filter)
				|| script.tags().stream().anyMatch(tag -> tag.equalsIgnoreCase(filter));
	}

	private String scriptCategory(Path script) {
		String lower = script.getFileName().toString().toLowerCase(Locale.ROOT);
		if (lower.contains("farm") || lower.contains("crop") || lower.contains("mine")) {
			return "Farming";
		}
		if (lower.contains("combat") || lower.contains("killaura") || lower.contains("crystal")) {
			return "Combat";
		}
		if (lower.contains("build") || lower.contains("chunk") || lower.contains("world")) {
			return "World";
		}
		if (lower.contains("speed") || lower.contains("nofall") || lower.contains("fullbright") || lower.contains("haste")
				|| lower.contains("jump") || lower.contains("fire") || lower.contains("water") || lower.contains("saturation")
				|| lower.contains("cleanup") || lower.contains("utility") || lower.contains("chat") || lower.contains("sort")
				|| lower.contains("inventory") || lower.contains("config")) {
			return "Utility";
		}
		return "Other";
	}

	private String scriptAuthor(Path script) {
		if (script == null || script.getParent() == null || script.getParent().equals(scriptDir)) {
			return "local";
		}
		try {
			return scriptDir.relativize(script.getParent()).toString().replace('\\', '/');
		} catch (IllegalArgumentException e) {
			return "external";
		}
	}

	private String scriptDownloads(Path script) {
		try {
			long bytes = Files.size(script);
			if (bytes < 1024) {
				return bytes + " B";
			}
			return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
		} catch (IOException e) {
			return "-";
		}
	}

	private View scriptsFooter(int shown) {
		LinearLayout footer = row(10);
		footer.setGravity(Gravity.CENTER_VERTICAL);
		footer.addView(label(scriptFilter + " • " + shown + " shown", 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View publishButton = textButton("Publish Script");
		publishButton.setOnClickListener(view -> openPublishModal(selectedScript));
		footer.addView(publishButton, new LinearLayout.LayoutParams(128, 36));
		View refresh = textButton("Refresh");
		refresh.setOnClickListener(view -> renderShell());
		footer.addView(refresh, new LinearLayout.LayoutParams(100, 36));
		return footer;
	}

	private View scriptCard(LibraryScriptItem script) {
		LinearLayout card = column(10);
		card.setPadding(14, 12, 14, 12);
		makeHover(card, round(Color.argb(122, 15, 21, 34), 10, Color.argb(78, 105, 116, 150)),
				round(Color.argb(178, 24, 30, 48), 10, STROKE_HOVER));
		card.setOnClickListener(view -> {
			selectedLibraryScriptId = script.id();
			if (isControlDown()) {
				toggleDropdown("library-script-actions", view, 236);
			} else {
				renderShell();
			}
		});

		LinearLayout head = row(12);
		head.setGravity(Gravity.CENTER_VERTICAL);
		String name = script.name();
		head.addView(scriptArt(script.icon(), name), new LinearLayout.LayoutParams(58, 58));
		LinearLayout title = column(4);
		LinearLayout nameLine = row(8);
		nameLine.setGravity(Gravity.CENTER_VERTICAL);
		nameLine.addView(label(trimTo(name, 20), 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		if (script.id().equals(selectedLibraryScriptId)) {
			nameLine.addView(tag("Selected", true));
		}
		title.addView(nameLine);
		title.addView(label("by " + script.author(), 12, MUTED));
		title.addView(label("*  v" + script.version() + "     " + script.downloads() + " installs", 12, MUTED));
		head.addView(title, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		card.addView(head, new LinearLayout.LayoutParams(match(), 62));

		card.addView(label(trimTo(script.about(), 86), 13, MUTED), new LinearLayout.LayoutParams(match(), 42));

		LinearLayout tags = row(6);
		for (String tagName : script.tags().stream().limit(3).toList()) {
			tags.addView(tag(tagName, false));
		}
		card.addView(tags, new LinearLayout.LayoutParams(match(), 26));

		LinearLayout actions = row(12);
		actions.setGravity(Gravity.CENTER);
		View install = solidIconButton("download-solid.png");
		install.setOnClickListener(view -> installPublishedScript(script.id()));
		actions.addView(install, new LinearLayout.LayoutParams(44, 38));
		View open = iconButton("code-solid.png", MUTED);
		open.setOnClickListener(view -> previewPublishedScript(script.id()));
		actions.addView(open, new LinearLayout.LayoutParams(44, 38));
		View manage = iconButton("ellipsis-solid.png", MUTED);
		manage.setOnClickListener(view -> {
			selectedLibraryScriptId = script.id();
			toggleDropdown("library-script-actions", view, 236);
		});
		actions.addView(manage, new LinearLayout.LayoutParams(44, 38));
		card.addView(actions, new LinearLayout.LayoutParams(match(), 42));
		return card;
	}

	private View pager() {
		LinearLayout pager = row(10);
		pager.setGravity(Gravity.CENTER);
		pager.addView(pageButton("chevron-left-solid.png", false), new LinearLayout.LayoutParams(38, 34));
		for (String pageNumber : new String[]{"1", "2", "3"}) {
			TextView page = label(pageNumber, 13, pageNumber.equals("1") ? TEXT : MUTED);
			page.setGravity(Gravity.CENTER);
			makeHover(page, pageNumber.equals("1") ? round(accentDarkAlpha(185), 8, PURPLE_SOFT) : round(Color.argb(76, 18, 24, 39), 8, STROKE),
					round(accentAlpha(120), 8, STROKE_HOVER));
			pager.addView(page, new LinearLayout.LayoutParams(38, 34));
		}
		pager.addView(pageButton("chevron-right-solid.png", true), new LinearLayout.LayoutParams(38, 34));
		pager.addView(label("Page 1 of 356", 12, MUTED), new LinearLayout.LayoutParams(110, wrap()));
		return pager;
	}

	private View pageButton(String iconFile, boolean enabled) {
		FrameLayout button = new FrameLayout(requireContext());
		makeHover(button, round(Color.argb(enabled ? 98 : 45, 18, 24, 39), 8, STROKE),
				round(accentAlpha(enabled ? 120 : 45), 8, enabled ? STROKE_HOVER : STROKE));
		button.addView(icon(iconFile, enabled ? MUTED : FAINT), centered(14, 14));
		return button;
	}

	private View filtersPanel() {
		LinearLayout panel = column(16);
		panel.setPadding(18, 18, 18, 18);
		panel.setBackground(panel(16));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(label("Filters", 18, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View reset = link("Reset");
		reset.setOnClickListener(view -> {
			scriptFilter = "All";
			renderShell();
		});
		header.addView(reset, new LinearLayout.LayoutParams(56, 28));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 30));

		LinearLayout search = row(10);
		search.setPadding(12, 0, 12, 0);
		search.setGravity(Gravity.CENTER_VERTICAL);
		search.setBackground(round(Color.argb(116, 10, 15, 26), 8, STROKE));
		search.addView(icon("magnifying-glass-solid.png", FAINT), new LinearLayout.LayoutParams(15, 15));
		search.addView(label("Search filters...", 12, MUTED));
		panel.addView(search, new LinearLayout.LayoutParams(match(), 42));

		LinearLayout catsHeader = row(8);
		catsHeader.setGravity(Gravity.CENTER_VERTICAL);
		catsHeader.addView(label("Categories", 15, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View all = link("Select all");
		all.setOnClickListener(view -> {
			scriptFilter = "All";
			renderShell();
		});
		catsHeader.addView(all, new LinearLayout.LayoutParams(74, 28));
		panel.addView(catsHeader);
		for (String category : new String[]{"Python", "Pyjinn", "Farming", "Combat", "World", "Utility", "Other"}) {
			View check = filterCheck(category, scriptFilter.equals("All") || scriptFilter.equals(category));
			check.setOnClickListener(view -> {
				scriptFilter = category;
				renderShell();
			});
			panel.addView(check, new LinearLayout.LayoutParams(match(), 26));
		}

		panel.addView(label("Sort by", 15, TEXT));
		panel.addView(selectField("Recently modified"), new LinearLayout.LayoutParams(match(), 38));
		panel.addView(label("Time", 15, TEXT));
		panel.addView(selectField("All Time"), new LinearLayout.LayoutParams(match(), 38));

		LinearLayout other = column(12);
		other.setPadding(0, 12, 0, 0);
		other.addView(label("Other", 15, TEXT));
		other.addView(filterSwitch("Published scripts", true));
		other.addView(filterSwitch("Installable", true));
		other.addView(filterSwitch("Show about text", true));
		panel.addView(other, new LinearLayout.LayoutParams(match(), wrap()));
		return panel;
	}

	private View recentScripts() {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 20);
		panel.setBackground(panel(16));
		LinearLayout header = row(8);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(label("Recent Scripts", 16, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		View viewAll = link("View All");
		viewAll.setOnClickListener(view -> openPage(Page.EDITOR));
		header.addView(viewAll, new LinearLayout.LayoutParams(64, 28));
		panel.addView(header, new LinearLayout.LayoutParams(match(), 24));
		List<Path> recent = dashboardRecentScripts();
		if (recent.isEmpty()) {
			TextView empty = label("No scripts yet. Create one to get started.", 13, MUTED);
			empty.setGravity(Gravity.CENTER);
			panel.addView(empty, new LinearLayout.LayoutParams(match(), 74));
		}
		for (Path script : recent) {
			panel.addView(scriptRow(script), new LinearLayout.LayoutParams(match(), 74));
		}
		return panel;
	}

	private View favoriteModules() {
		List<ModuleItem> modules = FluxusAppState.get().modules().stream()
				.filter(module -> module.favorite() || module.installed())
				.limit(5)
				.toList();
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 20);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("star-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Favorite Libraries", 16, TEXT));
		panel.addView(title);
		if (modules.isEmpty()) {
			TextView empty = label("No libraries are installed yet.", 13, MUTED);
			empty.setGravity(Gravity.CENTER);
			panel.addView(empty, new LinearLayout.LayoutParams(match(), 64));
		}
		for (ModuleItem module : modules) {
			panel.addView(moduleRow(module), new LinearLayout.LayoutParams(match(), 52));
		}
		TextView manage = label("Manage Libraries", 13, TEXT);
		manage.setGravity(Gravity.CENTER);
		makeHover(manage, round(Color.argb(122, 18, 24, 39), 8, STROKE), round(Color.argb(170, 27, 33, 53), 8, STROKE_HOVER));
		manage.setOnClickListener(view -> openPage(Page.MODULES));
		panel.addView(manage, new LinearLayout.LayoutParams(match(), 42));
		return panel;
	}

	private View rightRail() {
		LinearLayout rail = column(16);
		rail.addView(activeScript(), new LinearLayout.LayoutParams(match(), 220));
		rail.addView(scriptLibrary(), new LinearLayout.LayoutParams(match(), 330));
		rail.addView(quickSettings(), new LinearLayout.LayoutParams(match(), 210));
		return rail;
	}

	private View activeScript() {
		LinearLayout panel = column(14);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(18));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("code-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Active Script", 16, TEXT));
		panel.addView(title);
		LinearLayout body = row(14);
		String name = selectedScript == null ? "No script selected" : selectedScript.getFileName().toString();
		body.addView(iconBadge(scriptIcon(name), TEXT, Color.argb(150, 3, 8, 14), 58, 10));
		LinearLayout copy = column(5);
		copy.addView(label(name, 15, selectedScript == null ? MUTED : PURPLE));
		copy.addView(label("by Shulkr", 12, MUTED));
		copy.addView(label(currentScriptRunning ? "*  Running" : "Ready", 12, currentScriptRunning ? GREEN : MUTED));
		body.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		panel.addView(body, new LinearLayout.LayoutParams(match(), 66));
		LinearLayout buttons = row(10);
		View run = solidIconButton("play-solid.png");
		run.setOnClickListener(view -> runCurrentScript());
		buttons.addView(run, new LinearLayout.LayoutParams(58, 42));
		View stop = iconButton("border-all-solid.png", MUTED);
		stop.setOnClickListener(view -> stopScripts());
		buttons.addView(stop, new LinearLayout.LayoutParams(58, 42));
		View open = iconButton("folder-solid.png", MUTED);
		open.setOnClickListener(view -> openScriptFolder());
		buttons.addView(open, new LinearLayout.LayoutParams(58, 42));
		View edit = iconButton("code-solid.png", MUTED);
		edit.setOnClickListener(view -> openPage(Page.EDITOR));
		buttons.addView(edit, new LinearLayout.LayoutParams(58, 42));
		panel.addView(buttons, new LinearLayout.LayoutParams(match(), 48));
		return panel;
	}

	private View scriptLibrary() {
		LinearLayout panel = column(12);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("folder-open-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Script Library", 16, TEXT));
		panel.addView(title);
		LinearLayout search = row(10);
		search.setGravity(Gravity.CENTER_VERTICAL);
		search.setPadding(12, 0, 12, 0);
		search.setBackground(round(Color.argb(116, 10, 15, 26), 8, STROKE));
		search.addView(icon("magnifying-glass-solid.png", FAINT), new LinearLayout.LayoutParams(15, 15));
		search.addView(label("Search library...", 12, MUTED));
		makeHover(search, round(Color.argb(116, 10, 15, 26), 8, STROKE),
				round(Color.argb(165, 13, 19, 32), 8, STROKE_HOVER));
		search.setOnClickListener(view -> openPage(Page.SCRIPTS));
		panel.addView(search, new LinearLayout.LayoutParams(match(), 42));
		for (String[] cat : dashboardLibraryCategories()) {
			LinearLayout row = row(8);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.addView(icon(cat[2], MUTED), new LinearLayout.LayoutParams(15, 15));
			row.addView(label(cat[0], 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
			TextView count = label(cat[1], 12, PURPLE);
			count.setGravity(Gravity.CENTER);
			count.setBackground(glass(accentDarkAlpha(150), accentDarkAlpha(120), 8, 0));
			row.addView(count, new LinearLayout.LayoutParams(28, 22));
			panel.addView(row, new LinearLayout.LayoutParams(match(), 25));
		}
		TextView open = label("Open Library                                      ->", 13, TEXT);
		open.setGravity(Gravity.CENTER_VERTICAL);
		open.setPadding(18, 0, 18, 0);
		makeHover(open, round(Color.argb(122, 18, 24, 39), 8, STROKE), round(Color.argb(170, 27, 33, 53), 8, STROKE_HOVER));
		open.setOnClickListener(view -> openPage(Page.SCRIPTS));
		panel.addView(open, new LinearLayout.LayoutParams(match(), 42));
		return panel;
	}

	private View quickSettings() {
		LinearLayout panel = column(14);
		panel.setPadding(20, 18, 20, 18);
		panel.setBackground(panel(16));
		LinearLayout title = row(9);
		title.setGravity(Gravity.CENTER_VERTICAL);
		title.addView(icon("gear-solid.png", PURPLE), new LinearLayout.LayoutParams(17, 17));
		title.addView(label("Quick Settings", 16, TEXT));
		panel.addView(title);
		panel.addView(settingAction("Theme", settingsConfig == null ? "Dark v" : settingsConfig.theme() + "  >", () -> openPage(Page.SETTINGS)));
		panel.addView(settingAction("Accent Color", settingsConfig == null ? "*" : settingsConfig.accent() + "  >", () -> openPage(Page.SETTINGS)));
		panel.addView(settingAction("Enabled Libraries", enabledDashboardModules() + "  >", () -> openPage(Page.MODULES)));
		panel.addView(settingAction("Keybinds", ">", () -> openPage(Page.SETTINGS)));
		return panel;
	}

	private List<String[]> dashboardLibraryCategories() {
		List<LibraryScriptItem> published = FluxusAppState.get().libraryScripts();
		String[][] categories = {
				{"Featured", "star-solid.png"},
				{"Python", "code-solid.png"},
				{"Pyjinn", "clipboard-solid.png"},
				{"Farming", "user-solid.png"},
				{"Combat", "broom-solid.png"},
				{"World", "box-solid.png"},
				{"Utility", "folder-plus-solid.png"}
		};
		List<String[]> rows = new ArrayList<>();
		for (String[] category : categories) {
			long count = category[0].equals("Featured")
					? published.stream().filter(script -> script.stars() > 0 || script.downloads() > 0).count()
					: published.stream().filter(script -> publishedScriptMatchesFilter(script, category[0])).count();
			rows.add(new String[]{category[0], String.valueOf(count), category[1]});
		}
		return rows;
	}

	private View scriptRow(Path script) {
		LinearLayout row = row(14);
		row.setPadding(14, 0, 10, 0);
		row.setGravity(Gravity.CENTER_VERTICAL);
		makeHover(row, round(Color.argb(132, 17, 23, 37), 9, Color.argb(82, 105, 116, 150)),
				round(Color.argb(178, 23, 29, 47), 9, STROKE_HOVER));
		int[] tone = dashboardTone(script.getFileName().toString());
		TextView badge = label(dashboardInitials(script.getFileName().toString()), 13, Color.argb(255, tone[0], tone[1], tone[2]));
		badge.setGravity(Gravity.CENTER);
		badge.setBackground(round(Color.argb(72, tone[0], tone[1], tone[2]), 10, 0));
		row.addView(badge, new LinearLayout.LayoutParams(52, 52));
		LinearLayout copy = column(4);
		copy.addView(label(script.getFileName().toString(), 15, TEXT));
		copy.addView(label(scriptSubtitle(script), 12, MUTED));
		row.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(label(modifiedAgo(script), 12, MUTED), new LinearLayout.LayoutParams(64, wrap()));
		View play = solidIconButton("play-solid.png");
		play.setOnClickListener(view -> {
			selectDashboardScript(script, false);
			runCurrentScript();
		});
		row.addView(play, new LinearLayout.LayoutParams(38, 38));
		View edit = iconButton("ellipsis-solid.png", MUTED);
		edit.setOnClickListener(view -> selectDashboardScript(script, true));
		row.addView(edit, new LinearLayout.LayoutParams(38, 38));
		row.setOnClickListener(view -> selectDashboardScript(script, true));
		return row;
	}

	private View moduleRow(ModuleItem data) {
		LinearLayout row = row(12);
		row.setPadding(14, 0, 10, 0);
		row.setGravity(Gravity.CENTER_VERTICAL);
		makeHover(row, round(Color.argb(118, 17, 23, 37), 8, Color.argb(62, 105, 116, 150)),
				round(Color.argb(165, 24, 30, 48), 8, accentAlpha(95)));
		row.addView(icon(data.icon(), PURPLE), new LinearLayout.LayoutParams(20, 20));
		row.addView(label(data.name(), 13, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		Switch toggle = animatedSwitch(data.installed(), 54, checked -> {
			FluxusAppState.get().setModuleInstalled(data.id(), checked);
			settingsMessage = data.name() + (checked ? " installed." : " disabled.");
		}, false);
		row.addView(toggle, new LinearLayout.LayoutParams(66, 42));
		row.setOnClickListener(view -> {
			selectedModuleId = data.id();
			openPage(Page.MODULES);
		});
		return row;
	}

	private View action(String title, String subtitle, String iconFile) {
		LinearLayout card = row(14);
		card.setPadding(18, 0, 18, 0);
		card.setGravity(Gravity.CENTER_VERTICAL);
		makeHover(card, round(CARD, 10, STROKE), round(CARD_HOVER, 10, STROKE_HOVER));
		addPressAnimation(card);
		card.addView(iconBadge(iconFile, TEXT, accentAlpha(92), 58, 18));
		LinearLayout copy = column(5);
		copy.addView(label(title, 14, TEXT));
		copy.addView(label(subtitle, 12, MUTED));
		card.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		card.addView(icon("arrow-right-solid.png", MUTED), new LinearLayout.LayoutParams(18, 18));
		return card;
	}

	private View stat(String label, String value, String iconFile) {
		LinearLayout card = row(12);
		card.setPadding(16, 10, 12, 10);
		card.setGravity(Gravity.CENTER_VERTICAL);
		card.setBackground(round(Color.argb(122, 15, 21, 34), 10, Color.argb(74, 105, 116, 150)));
		LinearLayout copy = column(3);
		copy.addView(label(label, 12, MUTED));
		copy.addView(label(value, 23, TEXT));
		card.addView(copy, new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		card.addView(iconBadge(iconFile, TEXT, accentAlpha(88), 48, 15));
		return card;
	}

	private View setting(String name, String value) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 13, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(label(value, 13, value.equals("*") ? PURPLE : MUTED));
		return row;
	}

	private View settingAction(String name, String value, Runnable action) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(0, 2, 0, 2);
		row.addView(label(name, 13, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(label(value, 13, value.contains("*") ? PURPLE : MUTED));
		makeHover(row, round(Color.TRANSPARENT, 8, 0), round(Color.argb(76, 24, 30, 48), 8, accentAlpha(60)));
		addPressAnimation(row);
		row.setOnClickListener(view -> action.run());
		return row;
	}

	private LinearLayout dock() {
		return dock(false);
	}

	private LinearLayout dock(boolean forceVisible) {
		LinearLayout dock = row(0);
		dock.setPadding(14, 0, 14, 0);
		dock.setGravity(Gravity.CENTER);
		dock.setBackground(glass(Color.argb(155, 10, 16, 27), Color.argb(180, 7, 12, 22), 18, Color.argb(95, 105, 116, 150)));
		if (!forceVisible && settingsConfig.navigationMode().equals("Floating dock only")) {
			dock.setVisibility(View.GONE);
			return dock;
		}
		String[][] dockItems = {
				{"house-solid.png", "dashboard"}, {"code-solid.png", "scripts"}, {"border-all-solid.png", "editor"},
				{"puzzle-piece-solid.png", "libraries"}, {"box-open-solid.png", "modules"}, {"layer-group-solid.png", "templates"},
				{"window-restore-regular.png", "windowspy"}, {"circle-info-solid.png", "remote"},
				{"border-none-solid.png", "overlays"}, {"gear-solid.png", "settings"}
		};
		int trackWidth = dockItems.length * DOCK_ITEM_SIZE + (dockItems.length - 1) * DOCK_ITEM_GAP;
		FrameLayout track = new FrameLayout(requireContext());
		View indicator = new View(requireContext());
		indicator.setBackground(glass(PURPLE, PURPLE_DARK, 16, PURPLE_SOFT));
		indicator.setAlpha(1.0F);
		indicator.setClickable(false);
		indicator.setFocusable(false);
		FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(DOCK_ITEM_SIZE, DOCK_ITEM_SIZE);
		indicatorParams.gravity = Gravity.TOP | Gravity.LEFT;
		track.addView(indicator, indicatorParams);

		LinearLayout itemRow = row(DOCK_ITEM_GAP);
		int[] activeIndex = {-1};
		int[] indicatorTargetIndex = {-1};
		AnimatorSet[] positionAnimation = new AnimatorSet[1];
		ValueAnimator[] widthAnimation = new ValueAnimator[1];
		int itemIndex = 0;
		for (String[] item : dockItems) {
			int tabIndex = itemIndex;
			boolean active = (page == Page.DASHBOARD && item[1].equals("dashboard"))
					|| (page == Page.SCRIPTS && item[1].equals("scripts"))
					|| (page == Page.MODULES && item[1].equals("libraries"))
					|| (page == Page.EDITOR && item[1].equals("editor"))
					|| (page == Page.ADDONS && item[1].equals("modules"))
					|| (page == Page.TEMPLATES && item[1].equals("templates"))
					|| (page == Page.WINDOWSPY && item[1].equals("windowspy"))
					|| (page == Page.REMOTE && item[1].equals("remote"))
					|| (page == Page.OVERLAYS && item[1].equals("overlays"))
					|| (page == Page.SETTINGS && item[1].equals("settings"));
			FrameLayout tab = new FrameLayout(requireContext());
			tab.addView(icon(item[0], active ? TEXT : MUTED), centered(24, 24));
			tab.setClickable(true);
			tab.setFocusable(true);
			tab.setFocusableInTouchMode(true);
			if (active) {
				activeIndex[0] = tabIndex;
				indicatorTargetIndex[0] = tabIndex;
				indicatorParams.leftMargin = tabIndex * (DOCK_ITEM_SIZE + DOCK_ITEM_GAP);
				indicator.setLayoutParams(indicatorParams);
				indicator.setTranslationX(0.0F);
				indicator.setAlpha(1.0F);
			}
			tab.setOnHoverListener((target, event) -> {
				if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
					indicatorTargetIndex[0] = tabIndex;
					moveDockIndicator(indicator, tabIndex, positionAnimation, widthAnimation, true);
					return true;
				}
				if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
					dock.post(() -> {
						if (indicatorTargetIndex[0] == tabIndex && activeIndex[0] >= 0) {
							indicatorTargetIndex[0] = activeIndex[0];
							moveDockIndicator(indicator, activeIndex[0], positionAnimation, widthAnimation, true);
						}
					});
					return true;
				}
				return false;
			});
			tab.setOnFocusChangeListener((target, hasFocus) -> {
				if (hasFocus) {
					indicatorTargetIndex[0] = tabIndex;
					moveDockIndicator(indicator, tabIndex, positionAnimation, widthAnimation, true);
					return;
				}
				dock.post(() -> {
					if (!dock.hasFocus() && activeIndex[0] >= 0) {
						indicatorTargetIndex[0] = activeIndex[0];
						moveDockIndicator(indicator, activeIndex[0], positionAnimation, widthAnimation, true);
					}
				});
			});
			addPressAnimation(tab);
			if (item[1].equals("dashboard")) {
				tab.setOnClickListener(view -> openPage(Page.DASHBOARD));
			} else if (item[1].equals("scripts")) {
				tab.setOnClickListener(view -> openPage(Page.SCRIPTS));
			} else if (item[1].equals("editor")) {
				tab.setOnClickListener(view -> openPage(Page.EDITOR));
			} else if (item[1].equals("libraries")) {
				tab.setOnClickListener(view -> openPage(Page.MODULES));
			} else if (item[1].equals("modules")) {
				tab.setOnClickListener(view -> openPage(Page.ADDONS));
			} else if (item[1].equals("templates")) {
				tab.setOnClickListener(view -> openPage(Page.TEMPLATES));
			} else if (item[1].equals("windowspy")) {
				tab.setOnClickListener(view -> openPage(Page.WINDOWSPY));
			} else if (item[1].equals("remote")) {
				tab.setOnClickListener(view -> openPage(Page.REMOTE));
			} else if (item[1].equals("overlays")) {
				tab.setOnClickListener(view -> openPage(Page.OVERLAYS));
			} else if (item[1].equals("settings")) {
				tab.setOnClickListener(view -> openPage(Page.SETTINGS));
			}
			itemRow.addView(tab, new LinearLayout.LayoutParams(DOCK_ITEM_SIZE, DOCK_ITEM_SIZE));
			itemIndex++;
		}
		track.addView(itemRow, new FrameLayout.LayoutParams(trackWidth, DOCK_ITEM_SIZE));
		dock.addView(track, new LinearLayout.LayoutParams(trackWidth, DOCK_ITEM_SIZE));
		dock.setOnHoverListener((target, event) -> {
			if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT && activeIndex[0] >= 0) {
				indicatorTargetIndex[0] = activeIndex[0];
				moveDockIndicator(indicator, activeIndex[0], positionAnimation, widthAnimation, true);
			}
			return false;
		});
		track.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
			if (indicatorTargetIndex[0] >= 0 && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) {
				moveDockIndicator(indicator, indicatorTargetIndex[0], positionAnimation, widthAnimation, false);
			}
		});
		track.post(() -> {
			if (activeIndex[0] >= 0) {
				indicatorTargetIndex[0] = activeIndex[0];
				moveDockIndicator(indicator, activeIndex[0], positionAnimation, widthAnimation, false);
			}
		});
		return dock;
	}

	private void moveDockIndicator(View indicator, int targetIndex, AnimatorSet[] positionAnimation,
			ValueAnimator[] widthAnimation, boolean animate) {
		if (targetIndex < 0) {
			return;
		}
		float targetX = targetIndex * (DOCK_ITEM_SIZE + DOCK_ITEM_GAP);
		int targetWidth = DOCK_ITEM_SIZE;
		boolean motionEnabled = animate && animatedHoverHighlight && ValueAnimator.areAnimatorsEnabled();
		if (!motionEnabled) {
			cancelDockIndicatorAnimations(positionAnimation, widthAnimation);
			setDockIndicatorBounds(indicator, (int) targetX, targetWidth);
			indicator.setTranslationX(0.0F);
			indicator.setAlpha(1.0F);
			return;
		}

		cancelDockIndicatorAnimations(positionAnimation, widthAnimation);
		float currentVisualX = indicator.getLeft() + indicator.getTranslationX();
		setDockIndicatorLeft(indicator, (int) targetX);
		indicator.setTranslationX(currentVisualX - targetX);
		ObjectAnimator translate = ObjectAnimator.ofFloat(indicator, View.TRANSLATION_X, indicator.getTranslationX(), 0.0F);
		ObjectAnimator fade = ObjectAnimator.ofFloat(indicator, View.ALPHA, indicator.getAlpha(), 1.0F);
		AnimatorSet position = new AnimatorSet();
		position.playTogether(new Animator[]{translate, fade});
		position.setDuration(DOCK_INDICATOR_MS);
		positionAnimation[0] = position;

		int currentWidth = indicator.getLayoutParams() == null ? targetWidth : indicator.getLayoutParams().width;
		ValueAnimator width = ValueAnimator.ofInt(currentWidth, targetWidth);
		width.addUpdateListener(animation -> setDockIndicatorWidth(indicator, (Integer) animation.getAnimatedValue()));
		width.setDuration(DOCK_INDICATOR_MS);
		widthAnimation[0] = width;
		position.start();
		width.start();
	}

	private void cancelDockIndicatorAnimations(AnimatorSet[] positionAnimation, ValueAnimator[] widthAnimation) {
		if (positionAnimation[0] != null) {
			positionAnimation[0].cancel();
			positionAnimation[0] = null;
		}
		if (widthAnimation[0] != null) {
			widthAnimation[0].cancel();
			widthAnimation[0] = null;
		}
	}

	private void setDockIndicatorWidth(View indicator, int width) {
		ViewGroup.LayoutParams params = indicator.getLayoutParams();
		if (params != null && params.width != width) {
			params.width = width;
			indicator.setLayoutParams(params);
		}
	}

	private void setDockIndicatorLeft(View indicator, int left) {
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) indicator.getLayoutParams();
		if (params != null && params.leftMargin != left) {
			params.leftMargin = left;
			indicator.setLayoutParams(params);
		}
	}

	private void setDockIndicatorBounds(View indicator, int left, int width) {
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) indicator.getLayoutParams();
		if (params != null && (params.leftMargin != left || params.width != width)) {
			params.leftMargin = left;
			params.width = width;
			indicator.setLayoutParams(params);
		}
	}

	private View primaryButton(String iconFile, String text, String trailingIcon) {
		LinearLayout button = row(10);
		button.setGravity(Gravity.CENTER);
		button.setPadding(14, 0, 12, 0);
		makeHover(button, glass(accentAlpha(215), accentDarkAlpha(185), 8, PURPLE_SOFT),
				glass(accentAlpha(245), accentDarkAlpha(210), 8, STROKE_HOVER));
		addPressAnimation(button);
		button.addView(icon(iconFile, TEXT), new LinearLayout.LayoutParams(16, 16));
		button.addView(label(text, 13, TEXT));
		ImageView trailing = icon(trailingIcon, TEXT);
		button.addView(trailing, new LinearLayout.LayoutParams(14, 14));
		if (trailingIcon.equals("chevron-down-solid.png") && isDropdownOpen("new-script")) {
			currentDropdownArrow = trailing;
			animateDropdownArrow(trailing, true);
		}
		return button;
	}

	private View searchBar(String placeholder) {
		LinearLayout search = row(12);
		search.setPadding(18, 0, 18, 0);
		search.setGravity(Gravity.CENTER_VERTICAL);
		search.setBackground(round(Color.argb(124, 8, 13, 24), 12, Color.argb(100, 105, 116, 150)));
		search.addView(icon("magnifying-glass-solid.png", MUTED), new LinearLayout.LayoutParams(18, 18));
		search.addView(label(placeholder, 13, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		search.addView(keycap("Ctrl"));
		search.addView(keycap("K"));
		makeHover(search, round(Color.argb(124, 8, 13, 24), 12, Color.argb(100, 105, 116, 150)),
				round(Color.argb(170, 13, 18, 31), 12, STROKE_HOVER));
		search.setOnClickListener(view -> openCommandPalette(search));
		return search;
	}

	private View toolbarButton(String iconFile, String text) {
		LinearLayout button = row(10);
		button.setGravity(Gravity.CENTER);
		button.setPadding(12, 0, 12, 0);
		makeHover(button, round(Color.argb(118, 18, 24, 39), 8, STROKE),
				round(Color.argb(170, 27, 33, 53), 8, STROKE_HOVER));
		addPressAnimation(button);
		button.addView(icon(iconFile, TEXT), new LinearLayout.LayoutParams(16, 16));
		button.addView(label(text, 13, TEXT));
		return button;
	}

	private View primaryActionButton(String iconFile, String text) {
		LinearLayout button = row(10);
		button.setGravity(Gravity.CENTER);
		button.setPadding(13, 0, 13, 0);
		makeHover(button, glass(accentAlpha(200), accentDarkAlpha(175), 8, PURPLE_SOFT),
				glass(accentAlpha(238), accentDarkAlpha(205), 8, STROKE_HOVER));
		addPressAnimation(button);
		button.addView(icon(iconFile, TEXT), new LinearLayout.LayoutParams(16, 16));
		button.addView(label(text, 13, TEXT));
		return button;
	}

	private View iconOnly(String iconFile) {
		FrameLayout button = new FrameLayout(requireContext());
		makeHover(button, round(Color.TRANSPARENT, 8, 0), round(accentAlpha(70), 8, 0));
		addPressAnimation(button);
		button.addView(icon(iconFile, MUTED), centered(20, 20));
		return button;
	}

	private View viewToggle(String iconFile, boolean active) {
		FrameLayout button = new FrameLayout(requireContext());
		makeHover(button, active ? round(PURPLE, 9, 0) : round(Color.argb(76, 18, 24, 39), 9, STROKE),
				active ? round(accentAlpha(255), 9, 0) : round(accentAlpha(90), 9, STROKE_HOVER));
		addPressAnimation(button);
		button.addView(icon(iconFile, active ? TEXT : MUTED), centered(18, 18));
		return button;
	}

	private TextView chip(String text, boolean active) {
		TextView chip = label(text, 12, active ? TEXT : MUTED);
		chip.setGravity(Gravity.CENTER);
		makeHover(chip, active ? round(accentDarkAlpha(185), 8, PURPLE_SOFT) : round(Color.argb(76, 18, 24, 39), 8, STROKE),
				active ? round(accentAlpha(225), 8, STROKE_HOVER) : round(Color.argb(116, 27, 33, 53), 8, STROKE_HOVER));
		addPressAnimation(chip);
		return chip;
	}

	private TextView tag(String text, boolean featured) {
		TextView tag = label(text, 11, featured ? PURPLE : MUTED);
		tag.setGravity(Gravity.CENTER);
		tag.setPadding(8, 0, 8, 0);
		tag.setBackground(round(featured ? accentAlpha(96) : Color.argb(70, 18, 24, 39), 6, featured ? accentAlpha(72) : Color.argb(44, 105, 116, 150)));
		return tag;
	}

	private View scriptArt(String iconFile, String name) {
		FrameLayout art = new FrameLayout(requireContext());
		int color = name.contains("Crystal") || name.contains("KillAura") ? Color.argb(96, 255, 72, 96)
				: name.contains("Speed") ? Color.argb(96, 33, 160, 255)
				: name.contains("Build") || name.contains("ESP") ? Color.argb(88, 48, 215, 110)
				: Color.argb(88, 163, 88, 255);
		art.setBackground(round(color, 12, 0));
		art.addView(icon(iconFile, name.contains("AutoTotem") ? Color.argb(255, 255, 186, 72) : TEXT), centered(34, 34));
		return art;
	}

	private View moduleArt(ModuleItem data) {
		FrameLayout art = new FrameLayout(requireContext());
		int color = data.category().equals("Data") ? Color.argb(105, 163, 88, 255)
				: data.category().equals("Visualization") ? Color.argb(90, 125, 92, 255)
				: data.category().equals("Web") ? Color.argb(92, 45, 205, 220)
				: Color.argb(92, 96, 255, 154);
		art.setBackground(round(color, 12, 0));
		art.addView(icon(data.icon(), TEXT), centered(34, 34));
		return art;
	}

	private View installBadge(boolean installed) {
		TextView badge = label(installed ? "* Installed" : "* Not Installed", 11, installed ? GREEN : MUTED);
		badge.setGravity(Gravity.CENTER);
		badge.setBackground(round(installed ? Color.argb(50, 62, 216, 99) : Color.argb(74, 45, 50, 70), 9,
				installed ? Color.argb(52, 62, 216, 99) : Color.argb(40, 105, 116, 150)));
		return badge;
	}

	private View moduleLink(String text, String iconFile) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(icon(iconFile, MUTED), new LinearLayout.LayoutParams(15, 15));
		row.addView(label(text, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(icon("expand-solid.png", MUTED), new LinearLayout.LayoutParams(13, 13));
		return row;
	}

	private View moduleAction(String text, String iconFile, Runnable action) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(10, 0, 10, 0);
		makeHover(row, round(Color.argb(74, 18, 24, 39), 8, Color.argb(42, 105, 116, 150)),
				round(Color.argb(122, 28, 34, 54), 8, STROKE_HOVER));
		row.addView(icon(iconFile, PURPLE), new LinearLayout.LayoutParams(15, 15));
		row.addView(label(text, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.addView(icon("arrow-right-solid.png", MUTED), new LinearLayout.LayoutParams(13, 13));
		row.setOnClickListener(view -> action.run());
		addPressAnimation(row);
		return row;
	}

	private View detailRow(String name, String value) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 0.42F));
		TextView valueView = tag(value, false);
		valueView.setSingleLine(true);
		row.addView(valueView, new LinearLayout.LayoutParams(0, 24, 0.58F));
		return row;
	}

	private View compactSearch(String placeholder) {
		LinearLayout search = row(8);
		search.setGravity(Gravity.CENTER_VERTICAL);
		search.setPadding(12, 0, 12, 0);
		search.setBackground(round(Color.argb(116, 10, 15, 26), 8, STROKE));
		search.addView(icon("magnifying-glass-solid.png", FAINT), new LinearLayout.LayoutParams(14, 14));
		search.addView(label(placeholder, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		makeHover(search, round(Color.argb(116, 10, 15, 26), 8, STROKE),
				round(Color.argb(165, 13, 19, 32), 8, STROKE_HOVER));
		return search;
	}

	private List<Path> rootFolders() {
		return editorFolders.stream()
				.filter(folder -> Objects.equals(folder.getParent(), scriptDir))
				.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
				.toList();
	}

	private List<Path> rootScripts() {
		return editorScripts.stream()
				.filter(script -> Objects.equals(script.getParent(), scriptDir))
				.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
				.toList();
	}

	private List<Path> scriptsUnder(Path folder) {
		return editorScripts.stream()
				.filter(script -> script.startsWith(folder))
				.sorted(Comparator.comparing(path -> folder.relativize(path).toString().toLowerCase(Locale.ROOT)))
				.toList();
	}

	private View editorFolderRow(Path folder) {
		boolean active = Objects.equals(folder, selectedFolder) || selectedEditorItems.contains(folder);
		boolean collapsed = collapsedEditorFolders.contains(folder);
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(10, 0, 10, 0);
		makeHover(row, active ? glass(Color.argb(128, red(PURPLE), green(PURPLE), blue(PURPLE)), Color.argb(94, 54, 32, 110), 8, PURPLE_SOFT)
						: round(Color.TRANSPARENT, 8, 0),
				active ? glass(Color.argb(170, red(PURPLE), green(PURPLE), blue(PURPLE)), Color.argb(120, 70, 38, 138), 8, STROKE_HOVER)
						: round(Color.argb(88, 24, 30, 48), 8, Color.argb(45, 105, 116, 150)));
		row.addView(icon(collapsed ? "chevron-right-solid.png" : "chevron-down-solid.png", active ? PURPLE : MUTED), new LinearLayout.LayoutParams(13, 13));
		row.addView(icon("folder-solid.png", active ? PURPLE : MUTED), new LinearLayout.LayoutParams(22, 22));
		if (Objects.equals(renamingPath, folder)) {
			row.addView(renameField(folder), new LinearLayout.LayoutParams(0, 30, 1.0F));
		} else {
			String displayName = scriptDir.equals(folder.getParent()) ? folder.getFileName().toString() : scriptDir.relativize(folder).toString();
			row.addView(label(displayName, 13, active ? TEXT : MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		}
		row.addView(label("folder", 11, FAINT), new LinearLayout.LayoutParams(48, wrap()));
		row.setOnClickListener(view -> handleEditorItemClick(folder, true));
		row.setOnContextClickListener(view -> {
			openEditorItemContext(folder, view);
			return true;
		});
		row.setOnTouchListener((view, event) -> {
			if ((event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS || event.getActionMasked() == MotionEvent.ACTION_DOWN)
					&& (event.getActionButton() == MotionEvent.BUTTON_SECONDARY || event.isButtonPressed(MotionEvent.BUTTON_SECONDARY))) {
				openEditorItemContext(folder, view);
				return true;
			}
			return false;
		});
		return row;
	}

	private View editorScriptRow(Path script) {
		return editorScriptRow(script, 0);
	}

	private View editorScriptRow(Path script, int indent) {
		boolean active = Objects.equals(script, selectedScript) || selectedEditorItems.contains(script);
		String name = script.getFileName().toString();
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(10 + indent, 0, 10, 0);
		makeHover(row, active ? glass(accentDarkAlpha(160), accentDarkAlpha(118), 8, PURPLE_SOFT)
						: round(Color.TRANSPARENT, 8, 0),
				active ? glass(accentAlpha(200), accentDarkAlpha(145), 8, STROKE_HOVER)
						: round(Color.argb(88, 24, 30, 48), 8, Color.argb(45, 105, 116, 150)));
		row.addView(icon(scriptIcon(name), active ? GREEN : MUTED), new LinearLayout.LayoutParams(22, 22));
		if (Objects.equals(renamingPath, script)) {
			row.addView(renameField(script), new LinearLayout.LayoutParams(0, 30, 1.0F));
		} else {
			String displayName = scriptDir.equals(script.getParent()) ? name : scriptDir.relativize(script).toString();
			row.addView(label(displayName, 13, active ? TEXT : MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		}
		if (active) {
			row.addView(label("*", 13, GREEN), new LinearLayout.LayoutParams(14, wrap()));
		}
		row.addView(label(modifiedAgo(script), 12, MUTED), new LinearLayout.LayoutParams(48, wrap()));
		row.setOnClickListener(view -> handleEditorItemClick(script, false));
		row.setOnContextClickListener(view -> {
			openEditorItemContext(script, view);
			return true;
		});
		row.setOnTouchListener((view, event) -> {
			if ((event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS || event.getActionMasked() == MotionEvent.ACTION_DOWN)
					&& (event.getActionButton() == MotionEvent.BUTTON_SECONDARY || event.isButtonPressed(MotionEvent.BUTTON_SECONDARY))) {
				openEditorItemContext(script, view);
				return true;
			}
			return false;
		});
		return row;
	}

	private View renameField(Path path) {
		EditText field = new EditText(requireContext());
		field.setText(renamingDraft.isBlank() ? path.getFileName().toString() : renamingDraft);
		field.setSingleLine(true);
		field.setTextSize(12);
		field.setTextColor(TEXT);
		field.setHintTextColor(FAINT);
		field.setPadding(8, 0, 8, 0);
		field.setBackground(round(Color.argb(130, 18, 24, 39), 7, STROKE_HOVER));
		field.post(() -> {
			field.requestFocus();
			field.selectAll();
		});
		field.setOnFocusChangeListener((view, hasFocus) -> {
			if (!hasFocus && Objects.equals(renamingPath, path)) {
				commitRename(path, field.getText().toString());
			}
		});
		field.setOnKeyListener((view, keyCode, event) -> {
			if (event.getAction() != KeyEvent.ACTION_DOWN) {
				return false;
			}
			if (keyCode == KeyEvent.KEY_ENTER) {
				commitRename(path, field.getText().toString());
				return true;
			}
			if (keyCode == KeyEvent.KEY_ESCAPE) {
				renamingPath = null;
				renderShell();
				return true;
			}
			return false;
		});
		return field;
	}

	private View editorTab(Path tabScript) {
		boolean active = Objects.equals(tabScript, selectedScript);
		LinearLayout tab = row(8);
		tab.setPadding(14, 0, 10, 0);
		tab.setGravity(Gravity.CENTER_VERTICAL);
		makeHover(tab, active ? round(Color.argb(150, red(PURPLE), green(PURPLE), blue(PURPLE)), 8, Color.argb(80, red(PURPLE), green(PURPLE), blue(PURPLE)))
						: round(Color.argb(88, 18, 24, 39), 8, STROKE),
				active ? round(Color.argb(190, red(PURPLE), green(PURPLE), blue(PURPLE)), 8, STROKE_HOVER)
						: round(Color.argb(130, 27, 33, 53), 8, STROKE_HOVER));
		String name = tabScript.getFileName().toString();
		tab.addView(icon(scriptIcon(name), active ? GREEN : MUTED), new LinearLayout.LayoutParams(15, 15));
		tab.addView(label(name + (dirtyScripts.contains(tabScript) ? " *" : ""), 13, active ? TEXT : MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		TextView close = label("x", 14, active ? TEXT : MUTED);
		close.setGravity(Gravity.CENTER);
		close.setOnClickListener(view -> closeEditorTab(tabScript));
		tab.addView(close, new LinearLayout.LayoutParams(18, 24));
		tab.setOnClickListener(view -> selectEditorScript(tabScript));
		return tab;
	}

	private View codeLine(String number, String text, String tone) {
		LinearLayout line = row(14);
		line.setGravity(Gravity.CENTER_VERTICAL);
		TextView gutter = label(number, 11, FAINT);
		gutter.setGravity(Gravity.RIGHT);
		line.addView(gutter, new LinearLayout.LayoutParams(34, wrap()));
		int color = tone.equals("comment") ? FAINT
				: tone.equals("purple") ? Color.argb(255, 238, 119, 255)
				: tone.equals("green") ? Color.argb(255, 154, 226, 98)
				: tone.equals("blue") ? Color.argb(255, 126, 198, 255)
				: MUTED;
		line.addView(label(text, 12, color), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return line;
	}

	private View infoRow(String name, String value) {
		return infoRow(name, value, MUTED);
	}

	private View infoRow(String name, String value, int valueColor) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(name, 12, MUTED), new LinearLayout.LayoutParams(68, wrap()));
		row.addView(label(value, 12, valueColor), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private View editorActionRow(String iconFile, String text) {
		LinearLayout row = row(10);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(12, 0, 12, 0);
		makeHover(row, round(Color.argb(98, 18, 24, 39), 8, Color.argb(48, 105, 116, 150)),
				round(Color.argb(145, 27, 33, 53), 8, STROKE_HOVER));
		row.addView(icon(iconFile, PURPLE), new LinearLayout.LayoutParams(16, 16));
		row.addView(label(text, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		return row;
	}

	private boolean isControlDown() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LCONTROL)
				|| InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RCONTROL);
	}

	private boolean isShiftDown() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LSHIFT)
				|| InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RSHIFT);
	}

	private View dropdownAction(String text, String iconFile, Runnable action) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(10, 0, 10, 0);
		makeHover(row, round(Color.TRANSPARENT, 8, 0), round(Color.argb(100, red(PURPLE), green(PURPLE), blue(PURPLE)), 8, STROKE_HOVER));
		addPressAnimation(row);
		row.addView(icon(iconFile, PURPLE), new LinearLayout.LayoutParams(14, 14));
		row.addView(label(text, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		row.setOnClickListener(view -> closeFloatingDropdown(action));
		return row;
	}

	private View filterCheck(String text, boolean checked) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(text, 12, MUTED), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		CheckBox right = new CheckBox(requireContext());
		right.setChecked(checked);
		right.setButtonTintList(ColorStateList.valueOf(PURPLE));
		LinearLayout.LayoutParams checkboxLp = new LinearLayout.LayoutParams(30, 24);
		checkboxLp.setMargins(0, 0, 4, 0);
		row.addView(right, checkboxLp);
		return row;
	}

	private View selectField(String text) {
		LinearLayout field = row(8);
		field.setGravity(Gravity.CENTER_VERTICAL);
		field.setPadding(12, 0, 12, 0);
		field.setBackground(round(Color.argb(116, 18, 24, 39), 8, STROKE));
		field.addView(label(text, 13, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		field.addView(icon("chevron-down-solid.png", MUTED), new LinearLayout.LayoutParams(14, 14));
		return field;
	}

	private View filterSwitch(String text, boolean checked) {
		LinearLayout row = row(8);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(label(text, 13, TEXT), new LinearLayout.LayoutParams(0, wrap(), 1.0F));
		boolean current = filterSwitchStates.getOrDefault(text, checked);
		Switch toggle = animatedSwitch(current, 50, nextChecked -> filterSwitchStates.put(text, nextChecked), false);
		row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
		row.addView(toggle, new LinearLayout.LayoutParams(62, 38));
		return row;
	}

	private View nav(String text, String iconFile, boolean active) {
		boolean compact = useCompactNavigation();
		FrameLayout shell = new FrameLayout(requireContext());
		View hoverLayer = new View(requireContext());
		GradientDrawable normal = active
				? glass(accentAlpha(210), accentDarkAlpha(170), 12, PURPLE_SOFT)
				: round(Color.argb(78, 18, 24, 39), 12, Color.argb(52, 105, 116, 150));
		GradientDrawable hover = active
				? glass(accentAlpha(238), accentDarkAlpha(190), 12, STROKE_HOVER)
				: round(Color.argb(124, 24, 30, 48), 12, STROKE_HOVER);
		hoverLayer.setBackground(normal);
		shell.addView(hoverLayer, new FrameLayout.LayoutParams(match(), match()));

		LinearLayout item = row(compact ? 0 : 14);
		item.setPadding(compact ? 0 : 16, 0, compact ? 0 : 16, 0);
		item.setGravity(compact ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
		item.addView(icon(iconFile, TEXT), new LinearLayout.LayoutParams(20, 20));
		if (!compact) {
			item.addView(label(text, 15, TEXT));
		}
		shell.addView(item, new FrameLayout.LayoutParams(match(), match()));
		makeSlidingHover(shell, hoverLayer, normal, hover, active);
		addPressAnimation(shell);
		if (text.equals("Dashboard")) {
			shell.setOnClickListener(view -> openPage(Page.DASHBOARD));
		} else if (text.equals("Scripts")) {
			shell.setOnClickListener(view -> openPage(Page.SCRIPTS));
		} else if (text.equals("Editor")) {
			shell.setOnClickListener(view -> openPage(Page.EDITOR));
		} else if (text.equals("Libraries")) {
			shell.setOnClickListener(view -> openPage(Page.MODULES));
		} else if (text.equals("Modules")) {
			shell.setOnClickListener(view -> openPage(Page.ADDONS));
		} else if (text.equals("Templates")) {
			shell.setOnClickListener(view -> openPage(Page.TEMPLATES));
		} else if (text.equals("WindowSpy")) {
			shell.setOnClickListener(view -> openPage(Page.WINDOWSPY));
		} else if (text.equals("Remote")) {
			shell.setOnClickListener(view -> openPage(Page.REMOTE));
		} else if (text.equals("Overlays")) {
			shell.setOnClickListener(view -> openPage(Page.OVERLAYS));
		} else if (text.equals("Settings")) {
			shell.setOnClickListener(view -> openPage(Page.SETTINGS));
		} else if (text.equals("About")) {
			shell.setOnClickListener(view -> openPage(Page.ABOUT));
		}
		return shell;
	}

	private View iconBadge(String file, int tint, int background, int size, float radius) {
		FrameLayout badge = new FrameLayout(requireContext());
		badge.setBackground(round(background, radius, 0));
		int iconSize = Math.max(18, (int) (size * 0.52F));
		badge.addView(icon(file, tint), centered(iconSize, iconSize));
		return badge;
	}

	private View iconButton(String file, int tint) {
		FrameLayout button = new FrameLayout(requireContext());
		makeHover(button, round(Color.argb(118, 30, 35, 55), 8, STROKE), round(accentAlpha(120), 8, STROKE_HOVER));
		addPressAnimation(button);
		button.addView(icon(file, tint), centered(17, 17));
		return button;
	}

	private View solidIconButton(String file) {
		FrameLayout button = new FrameLayout(requireContext());
		makeHover(button, round(accentDarkAlpha(160), 8, accentAlpha(112)),
				round(accentAlpha(220), 8, STROKE_HOVER));
		addPressAnimation(button);
		button.addView(icon(file, PURPLE), centered(16, 16));
		return button;
	}

	private TextView textButton(String text) {
		TextView button = label(text, 13, MUTED);
		button.setGravity(Gravity.CENTER);
		makeHover(button, round(Color.argb(118, 30, 35, 55), 8, STROKE), round(accentAlpha(120), 8, STROKE_HOVER));
		addPressAnimation(button);
		return button;
	}

	private TextView link(String text) {
		TextView link = label(text, 13, PURPLE);
		link.setGravity(Gravity.CENTER);
		makeHover(link, round(Color.TRANSPARENT, 6, 0), round(accentAlpha(48), 6, 0));
		addPressAnimation(link);
		return link;
	}

	private TextView keycap(String text) {
		TextView key = label(text, 11, FAINT);
		key.setGravity(Gravity.CENTER);
		key.setBackground(round(Color.argb(88, 18, 24, 38), 6, Color.argb(50, 105, 116, 150)));
		return key;
	}

	private ImageView icon(String file, int tint) {
		ImageView image = new ImageView(requireContext());
		image.setImage(ImageStore.getInstance().getOrCreate(TritonUI.id("textures/icons/" + file)));
		image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		image.setImageTintList(ColorStateList.valueOf(tint));
		return image;
	}

	private ImageView rawIcon(String file) {
		ImageView image = new ImageView(requireContext());
		image.setImage(ImageStore.getInstance().getOrCreate(TritonUI.id("textures/icons/" + file)));
		image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		return image;
	}

	private ColorStateList switchTrackColors() {
		return new ColorStateList(
				new int[][]{new int[]{R.attr.state_checked}, new int[]{-R.attr.state_checked}},
				new int[]{PURPLE_DARK, Color.argb(255, 44, 48, 66)}
		);
	}

	private ColorStateList switchThumbColors() {
		return new ColorStateList(
				new int[][]{new int[]{R.attr.state_checked}, new int[]{-R.attr.state_checked}},
				new int[]{Color.argb(255, 228, 207, 255), Color.argb(255, 160, 166, 188)}
		);
	}

	private GradientDrawable dropdownSurface(float radius) {
		return glass(Color.argb(248, 12, 17, 30), Color.argb(242, 7, 12, 22), radius, STROKE_HOVER);
	}

	private void makeHover(View view, GradientDrawable normal, GradientDrawable hover) {
		view.setBackground(normal);
		view.setClickable(true);
		view.setOnHoverListener((target, event) -> {
			if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
				target.setBackground(hover);
				return true;
			}
			if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
				target.setBackground(normal);
				return true;
			}
			return false;
		});
	}

	private void addPressAnimation(View view) {
		final AnimatorSet[] running = new AnimatorSet[1];
		view.setOnTouchListener((target, event) -> {
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN) {
				target.setPivotX(target.getWidth() * 0.5F);
				target.setPivotY(target.getHeight() * 0.5F);
				startScaleAnimation(running, target, 0.975F, PRESS_IN_MS);
				return false;
			}
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				startScaleAnimation(running, target, 1.0F, PRESS_OUT_MS);
				return false;
			}
			return false;
		});
	}

	private void animateFloatingSurface(View view, boolean modal) {
		float startScale = modal ? 0.985F : 0.965F;
		float startY = modal ? 10.0F : -5.0F;
		view.setAlpha(0.0F);
		view.setScaleX(startScale);
		view.setScaleY(startScale);
		view.setTranslationY(startY);
		view.post(() -> {
			AnimatorSet set = new AnimatorSet();
			ObjectAnimator fade = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 1.0F);
			ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, view.getScaleX(), 1.0F);
			ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, view.getScaleY(), 1.0F);
			ObjectAnimator slide = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), 0.0F);
			set.playTogether(new Animator[]{fade, scaleX, scaleY, slide});
			set.setDuration(modal ? MODAL_IN_MS : MENU_IN_MS);
			set.start();
		});
	}

	private void closeFloatingDropdown(Runnable afterClose) {
		if (dropdownClosing) {
			return;
		}
		View dropdown = currentFloatingDropdown;
		if (dropdown == null || openDropdownKey.isBlank()) {
			openDropdownKey = "";
			if (afterClose != null) {
				afterClose.run();
			}
			renderShell();
			return;
		}
		dropdownClosing = true;
		animateDropdownArrow(currentDropdownArrow, false);
		boolean modal = openDropdownKey.equals("publish-modal");
		float endScale = modal ? 0.985F : 0.965F;
		float endY = modal ? 10.0F : -5.0F;
		AnimatorSet set = new AnimatorSet();
		ObjectAnimator fade = ObjectAnimator.ofFloat(dropdown, View.ALPHA, dropdown.getAlpha(), 0.0F);
		ObjectAnimator scaleX = ObjectAnimator.ofFloat(dropdown, View.SCALE_X, dropdown.getScaleX(), endScale);
		ObjectAnimator scaleY = ObjectAnimator.ofFloat(dropdown, View.SCALE_Y, dropdown.getScaleY(), endScale);
		ObjectAnimator slide = ObjectAnimator.ofFloat(dropdown, View.TRANSLATION_Y, dropdown.getTranslationY(), endY);
		set.playTogether(new Animator[]{fade, scaleX, scaleY, slide});
		long duration = modal ? MODAL_IN_MS : MENU_OUT_MS;
		set.setDuration(duration);
		set.start();
		dropdown.postDelayed(() -> {
			openDropdownKey = "";
			dropdownClosing = false;
			if (afterClose != null) {
				afterClose.run();
			}
			renderShell();
		}, duration);
	}

	private void animateDropdownArrow(ImageView arrow, boolean open) {
		if (arrow == null) {
			return;
		}
		float target = open ? 180.0F : 0.0F;
		if (open) {
			arrow.setRotation(0.0F);
		}
		arrow.post(() -> {
			ObjectAnimator rotation = ObjectAnimator.ofFloat(arrow, View.ROTATION, arrow.getRotation(), target);
			rotation.setDuration(open ? MENU_IN_MS : MENU_OUT_MS);
			rotation.start();
		});
	}

	private Switch animatedSwitch(boolean checked, int minWidth, Consumer<Boolean> onChanged, boolean deferCallback) {
		Switch toggle = new Switch(requireContext());
		toggle.setText("");
		toggle.setChecked(checked);
		toggle.setSwitchMinWidth(minWidth);
		toggle.setTrackTintList(switchTrackColors());
		toggle.setThumbTintList(switchThumbColors());
		toggle.setOnCheckedChangeListener((buttonView, nextChecked) -> {
			if (onChanged != null) {
				if (deferCallback) {
					toggle.postDelayed(() -> onChanged.accept(nextChecked), TOGGLE_COMMIT_DELAY_MS);
				} else {
					onChanged.accept(nextChecked);
				}
			}
		});
		return toggle;
	}

	private ScrollView layoutScrollView() {
		ScrollView scroll = new ScrollView(requireContext());
		scroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
			if (!settingsConfig.headerBehaviour().equals("Hide while scrolling") || currentHeader == null || scrollY == oldScrollY) {
				return;
			}
			boolean hide = scrollY > oldScrollY && scrollY > 12;
			if (headerHidden != hide) {
				headerHidden = hide;
				currentHeader.setVisibility(hide ? View.GONE : View.VISIBLE);
			}
		});
		return scroll;
	}

	private void animatePageSwap(View view) {
		view.setAlpha(0.0F);
		view.setTranslationY(10.0F);
		view.post(() -> {
			AnimatorSet set = new AnimatorSet();
			ObjectAnimator fade = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 1.0F);
			ObjectAnimator slide = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), 0.0F);
			set.playTogether(new Animator[]{fade, slide});
			set.setDuration(PAGE_IN_MS);
			set.start();
		});
	}

	private void makeSlidingHover(View container, View layer, GradientDrawable normal, GradientDrawable hover, boolean active) {
		container.setClickable(true);
		layer.setPivotX(0.0F);
		layer.setPivotY(0.0F);
		layer.setAlpha(active ? 1.0F : 0.0F);
		layer.setScaleX(active ? 1.0F : 0.0F);
		layer.setScaleY(1.0F);
		final AnimatorSet[] running = new AnimatorSet[1];
		container.setOnHoverListener((target, event) -> {
			if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
				layer.setBackground(hover);
				startSlideAnimation(running, layer, 1.0F, 1.0F, HOVER_IN_MS);
				return true;
			}
			if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
				layer.setBackground(normal);
				startSlideAnimation(running, layer, active ? 1.0F : 0.0F, active ? 1.0F : 0.0F, HOVER_OUT_MS);
				return true;
			}
			return false;
		});
	}

	private void startSlideAnimation(AnimatorSet[] running, View target, float scaleX, float alpha, long durationMs) {
		if (running[0] != null) {
			running[0].cancel();
		}
		ObjectAnimator reveal = ObjectAnimator.ofFloat(target, View.SCALE_X, target.getScaleX(), scaleX);
		ObjectAnimator fade = ObjectAnimator.ofFloat(target, View.ALPHA, target.getAlpha(), alpha);
		AnimatorSet set = new AnimatorSet();
		set.playTogether(new Animator[]{reveal, fade});
		set.setDuration(durationMs);
		running[0] = set;
		set.start();
	}

	private void startScaleAnimation(AnimatorSet[] running, View target, float scale, long durationMs) {
		if (running[0] != null) {
			running[0].cancel();
		}
		ObjectAnimator scaleX = ObjectAnimator.ofFloat(target, View.SCALE_X, target.getScaleX(), scale);
		ObjectAnimator scaleY = ObjectAnimator.ofFloat(target, View.SCALE_Y, target.getScaleY(), scale);
		AnimatorSet set = new AnimatorSet();
		set.playTogether(new Animator[]{scaleX, scaleY});
		set.setDuration(durationMs);
		running[0] = set;
		set.start();
	}

	private LinearLayout row(int gap) {
		LinearLayout layout = new LinearLayout(requireContext());
		layout.setOrientation(LinearLayout.HORIZONTAL);
		layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
		layout.setDividerDrawable(spacer(gap, 1));
		return layout;
	}

	private LinearLayout column(int gap) {
		LinearLayout layout = new LinearLayout(requireContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
		layout.setDividerDrawable(spacer(1, gap));
		return layout;
	}

	private TextView label(String text, float size, int color) {
		TextView label = new TextView(requireContext());
		label.setText(text);
		label.setTextSize(size);
		label.setTextColor(color);
		label.setSingleLine(false);
		label.setIncludeFontPadding(false);
		return label;
	}

	private TextView centerLabel(String text, float size, int color) {
		TextView label = label(text, size, color);
		label.setGravity(Gravity.CENTER);
		return label;
	}

	private GradientDrawable panel(float radius) {
		return glass(PANEL, PANEL_DARK, radius, STROKE);
	}

	private GradientDrawable round(int color, float radius, int stroke) {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setShape(ShapeDrawable.RECTANGLE);
		drawable.setColor(color);
		drawable.setCornerRadius(radius);
		if (stroke != 0) {
			drawable.setStroke(1, stroke);
		}
		return drawable;
	}

	private GradientDrawable glass(int top, int bottom, float radius, int stroke) {
		GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
		drawable.setShape(ShapeDrawable.RECTANGLE);
		drawable.setCornerRadius(radius);
		if (stroke != 0) {
			drawable.setStroke(1, stroke);
		}
		return drawable;
	}

	private ShapeDrawable spacer(int width, int height) {
		ShapeDrawable spacer = new ShapeDrawable();
		spacer.setShape(ShapeDrawable.RECTANGLE);
		spacer.setColor(Color.TRANSPARENT);
		spacer.setSize(width, height);
		return spacer;
	}

	private FrameLayout.LayoutParams centered(int width, int height) {
		return new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
	}

	private FrameLayout.LayoutParams pinned(int left, int top, int width, int height, int gravity) {
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, gravity);
		params.setMargins(left, top, left, top);
		return params;
	}

	private LinearLayout.LayoutParams weighted(float weight, int left, int top, int right, int bottom) {
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, match(), weight);
		params.setMargins(left, top, right, bottom);
		return params;
	}

	private int parse(String rgb, int alpha) {
		return Color.argb(alpha, parseR(rgb), parseG(rgb), parseB(rgb));
	}

	private int parseR(String rgb) {
		return Integer.parseInt(rgb.split(", ")[0]);
	}

	private int parseG(String rgb) {
		return Integer.parseInt(rgb.split(", ")[1]);
	}

	private int parseB(String rgb) {
		return Integer.parseInt(rgb.split(", ")[2]);
	}

	private int red(int color) {
		return (color >> 16) & 0xFF;
	}

	private int green(int color) {
		return (color >> 8) & 0xFF;
	}

	private int blue(int color) {
		return color & 0xFF;
	}

	private int accentAlpha(int alpha) {
		return Color.argb(alpha, red(PURPLE), green(PURPLE), blue(PURPLE));
	}

	private int accentDarkAlpha(int alpha) {
		return Color.argb(alpha, red(PURPLE_DARK), green(PURPLE_DARK), blue(PURPLE_DARK));
	}

	private static int match() {
		return ViewGroup.LayoutParams.MATCH_PARENT;
	}

	private static int wrap() {
		return ViewGroup.LayoutParams.WRAP_CONTENT;
	}
}
