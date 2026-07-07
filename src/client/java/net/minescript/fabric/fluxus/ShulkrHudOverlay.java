package net.minescript.fabric.fluxus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.render.TritonDraw;
import dev.triton.ui.client.screen.OverlayEditScreen;
import dev.triton.ui.client.util.Color;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class ShulkrHudOverlay {
  public static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("minescript", "shulkr_hud");

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  // Colors mirror the Shulkr dashboard theme (TritonModernFragment) so in-game HUD widgets
  // match the look of the actual UI (dark glass panels, purple accent, same status colors).
  private static final int PANEL = 0xCC0A0F1D;
  private static final int PANEL_HOT = 0xE8141B2B;
  private static final int STROKE = 0x5E525C7C;
  private static final int STROKE_HOT = 0x969B5FFF;
  private static final int GLASS_TOP = 0xDE070B16;
  private static final int GLASS_BOTTOM = 0xEE040812;
  private static final int GLASS_STROKE = 0x5E525C7C;
  private static final int ICON_BG = 0x46121827;
  private static final int PURPLE = 0xFFA358FF;
  private static final int CYAN = 0xFF2EDAFF;
  private static final int GREEN = 0xFF3ED863;
  private static final int RED = 0xFFFF4860;
  private static final int TEXT = 0xFFF7F4FF;
  private static final int MUTED = 0xE6ABB3CC;
  private static final int SNAP_DISTANCE = 6;
  private static final int WIDGET_GAP = 6;

  private static final List<Widget> widgets = new ArrayList<>();
  private static String selected = "Target HUD";
  private static boolean rendererActive = true;
  private static boolean editMode;
  private static boolean dragging;
  private static boolean regrabMouse;
  private static boolean previousLeftPressed;
  private static boolean previousEscapePressed;
  private static boolean loaded;
  private static int layoutWidth = -1;
  private static int layoutHeight = -1;
  private static int dragOffsetX;
  private static int dragOffsetY;
  private static int tick;

  private ShulkrHudOverlay() {}

  public static HudElement hudElement() {
    return ShulkrHudOverlay::render;
  }

  public static void onClientTick(Minecraft minecraft) {
    ensureLoaded();
    tick++;
    if (!rendererActive || !editMode || minecraft == null || minecraft.getWindow() == null || minecraft.mouseHandler == null) {
      previousLeftPressed = false;
      previousEscapePressed = false;
      dragging = false;
      return;
    }

    syncLayoutToWindow(minecraft);
    if (minecraft.screen instanceof OverlayEditScreen) {
      return;
    }
    if (minecraft.screen == null) {
      minecraft.setScreen(new OverlayEditScreen());
      return;
    }

    int mouseX = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
    int mouseY = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
    boolean leftPressed = minecraft.mouseHandler.isLeftPressed();
    if (leftPressed && !previousLeftPressed) {
      Widget hit = hitWidget(mouseX, mouseY);
      if (hit != null) {
        selected = hit.name;
        dragging = true;
        dragOffsetX = mouseX - hit.x;
        dragOffsetY = mouseY - hit.y;
      }
    } else if (!leftPressed && previousLeftPressed) {
      dragging = false;
      save();
    }
    previousLeftPressed = leftPressed;

    if (dragging) {
      Widget widget = widget(selected);
      if (widget != null) {
        widget.x = mouseX - dragOffsetX;
        widget.y = mouseY - dragOffsetY;
        clamp(widget, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
      }
    }
  }

  public static void setRendererActive(boolean active) {
    ensureLoaded();
    rendererActive = active;
    if (!active) {
      setEditMode(false);
    }
    save();
  }

  public static boolean rendererActive() {
    ensureLoaded();
    return rendererActive;
  }

  public static void setEditMode(boolean active) {
    ensureLoaded();
    Minecraft minecraft = Minecraft.getInstance();
    editMode = active;
    dragging = false;
    previousLeftPressed = false;
    if (minecraft != null && minecraft.mouseHandler != null) {
      if (active && minecraft.mouseHandler.isMouseGrabbed()) {
        regrabMouse = true;
        minecraft.mouseHandler.releaseMouse();
      } else if (!active && regrabMouse) {
        minecraft.mouseHandler.grabMouse();
        regrabMouse = false;
      }
    }
    save();
  }

  public static boolean editMode() {
    ensureLoaded();
    return editMode;
  }

  public static Set<String> visibleNames() {
    ensureLoaded();
    Set<String> names = new HashSet<>();
    for (Widget widget : widgets) {
      if (widget.visible) {
        names.add(widget.name);
      }
    }
    return names;
  }

  public static void setVisibleNames(Set<String> names) {
    ensureLoaded();
    for (Widget widget : widgets) {
      widget.visible = names.contains(widget.name);
    }
    save();
  }

  public static void setWidgetVisible(String name, boolean visible) {
    ensureLoaded();
    Widget widget = widget(name);
    if (widget != null) {
      widget.visible = visible;
      selected = name;
      save();
    }
  }

  public static String addCustomWidget(String category) {
    ensureLoaded();
    String base = category == null || category.isBlank() || "HUD".equals(category) ? "Custom HUD" : category + " Widget";
    String name = base;
    int index = 2;
    while (widget(name) != null) {
      name = base + " " + index++;
    }
    Minecraft minecraft = Minecraft.getInstance();
    int x = 32 + widgets.size() * 10;
    int y = 72 + widgets.size() * 12;
    Widget widget = new Widget(name, x, y, 176, 42, true, widgets.size());
    if (minecraft != null && minecraft.getWindow() != null) {
      clamp(widget, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }
    widgets.add(widget);
    selected = name;
    save();
    return name;
  }

  public static void select(String name) {
    ensureLoaded();
    if (widget(name) != null) {
      selected = name;
    }
  }

  public static String selected() {
    ensureLoaded();
    return selected;
  }

  public static String position(String name) {
    Widget widget = widget(name);
    return widget == null ? "-" : widget.x + ", " + widget.y;
  }

  public static String size(String name) {
    Widget widget = widget(name);
    return widget == null ? "-" : widget.width + " x " + widget.height;
  }

  public static void applyPreset(String preset) {
    ensureLoaded();
    Set<String> names = switch (preset) {
      case "Combat HUD" -> Set.of("Target HUD", "FPS Counter", "Player Vitals");
      case "Builder HUD" -> Set.of("Coordinates", "NBT Peek", "Script Status");
      case "Debug HUD" -> Set.of("NBT Peek", "Script Status", "FPS Counter");
      default -> Set.of("FPS Counter");
    };
    setVisibleNames(names);
    selected = names.iterator().next();
    save();
  }

  public static void savePreset() {
    ensureLoaded();
    save();
  }

  public static List<String> widgetNames() {
    ensureLoaded();
    return widgets.stream().map(widget -> widget.name).toList();
  }

  public static void resetLayout() {
    ensureLoaded();
    widgets.clear();
    widgets.addAll(defaultWidgets());
    selected = "Target HUD";
    rendererActive = true;
    editMode = false;
    dragging = false;
    layoutWidth = -1;
    layoutHeight = -1;
    save();
  }

  public static void moveSelected(int deltaX, int deltaY) {
    ensureLoaded();
    Widget widget = widget(selected);
    Minecraft minecraft = Minecraft.getInstance();
    if (widget == null || minecraft == null || minecraft.getWindow() == null) {
      return;
    }
    widget.x += deltaX;
    widget.y += deltaY;
    clamp(widget, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    save();
  }

  public static void snapSelected(String corner) {
    ensureLoaded();
    Widget widget = widget(selected);
    Minecraft minecraft = Minecraft.getInstance();
    if (widget == null || minecraft == null || minecraft.getWindow() == null) {
      return;
    }
    int screenWidth = minecraft.getWindow().getGuiScaledWidth();
    int screenHeight = minecraft.getWindow().getGuiScaledHeight();
    int pad = 18;
    switch (corner) {
      case "top-right" -> {
        widget.x = screenWidth - widget.width - pad;
        widget.y = pad;
      }
      case "bottom-left" -> {
        widget.x = pad;
        widget.y = screenHeight - widget.height - pad;
      }
      case "bottom-right" -> {
        widget.x = screenWidth - widget.width - pad;
        widget.y = screenHeight - widget.height - pad;
      }
      default -> {
        widget.x = pad;
        widget.y = pad;
      }
    }
    clamp(widget, screenWidth, screenHeight);
    save();
  }

  private static void render(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
    ensureLoaded();
    if (!rendererActive) {
      return;
    }
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || minecraft.font == null || minecraft.getWindow() == null) {
      return;
    }
    Font font = minecraft.font;
    int screenWidth = minecraft.getWindow().getGuiScaledWidth();
    int screenHeight = minecraft.getWindow().getGuiScaledHeight();
    syncLayoutToWindow(minecraft);
    for (Widget widget : widgets.stream().sorted(Comparator.comparingInt(w -> w.order)).toList()) {
      if (!widget.visible) {
        continue;
      }
      clamp(widget, screenWidth, screenHeight);
      drawWidget(gui, font, minecraft, widget);
      if (editMode) {
        drawEditFrame(gui, font, widget);
      }
    }
    if (editMode) {
      drawEditHint(gui, font, screenWidth, screenHeight);
    }
  }

  private static void drawWidget(GuiGraphicsExtractor gui, Font font, Minecraft minecraft, Widget widget) {
    switch (widget.name) {
      case "Target HUD" -> drawTargetHud(gui, font, minecraft, widget);
      case "Coordinates" -> drawCoordinates(gui, font, minecraft, widget);
      case "Script Status" -> drawScriptStatus(gui, font, widget);
      case "NBT Peek" -> drawNbtPeek(gui, font, minecraft, widget);
      case "FPS Counter" -> drawTextMetric(gui, font, widget, "FPS: " + Minecraft.getInstance().getFps(), GREEN);
      case "Player Vitals" -> drawVitals(gui, font, minecraft, widget);
      default -> drawToast(gui, font, widget, widget.name, "Active", PURPLE);
    }
  }

  private static void drawTargetHud(GuiGraphicsExtractor gui, Font font, Minecraft minecraft, Widget widget) {
    HitResult hit = minecraft.hitResult;
    String target = "No target";
    int color = MUTED;
    if (hit instanceof EntityHitResult entityHit) {
      Entity entity = entityHit.getEntity();
      target = entity.getDisplayName().getString();
      color = entity instanceof LivingEntity ? RED : CYAN;
    } else if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
      target = "Block target";
      color = PURPLE;
    }
    drawHudCard(gui, widget, true, color);
    drawIconBadge(gui, font, widget.x + 10, widget.y + 9, "◉", color);
    gui.text(font, target, widget.x + 32, widget.y + 7, TEXT, true);
    int barWidth = Math.max(24, widget.width - 50);
    gui.fill(widget.x + 32, widget.y + 25, widget.x + 32 + barWidth, widget.y + 29, 0x66485266);
    int healthWidth = target.equals("No target") ? 0 : Math.max(12, barWidth * 3 / 4);
    gui.fill(widget.x + 32, widget.y + 25, widget.x + 32 + healthWidth, widget.y + 29, color);
    gui.text(font, target.equals("No target") ? "No target selected" : "Target locked", widget.x + 32, widget.y + 34, MUTED, false);
  }

  private static void drawCoordinates(GuiGraphicsExtractor gui, Font font, Minecraft minecraft, Widget widget) {
    String xyz = "no player";
    if (minecraft.player != null) {
      xyz = String.format(Locale.ROOT, "Position: x%.1f, y%.1f, z%.1f", minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
    }
    drawTextMetric(gui, font, widget, xyz, PURPLE);
  }

  public static boolean beginDrag(double mouseX, double mouseY) {
    ensureLoaded();
    Widget hit = hitWidget((int) mouseX, (int) mouseY);
    if (hit == null) {
      return false;
    }
    selected = hit.name;
    dragging = true;
    dragOffsetX = (int) mouseX - hit.x;
    dragOffsetY = (int) mouseY - hit.y;
    return true;
  }

  public static boolean dragTo(double mouseX, double mouseY) {
    Widget widget = widget(selected);
    Minecraft minecraft = Minecraft.getInstance();
    if (!dragging || widget == null || minecraft == null || minecraft.getWindow() == null) {
      return false;
    }
    int proposedX = (int) mouseX - dragOffsetX;
    int proposedY = (int) mouseY - dragOffsetY;
    int[] snapped = snapPosition(widget, proposedX, proposedY,
        minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    widget.x = snapped[0];
    widget.y = snapped[1];
    clamp(widget, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    return true;
  }

  private static int[] snapPosition(Widget moving, int proposedX, int proposedY, int screenWidth, int screenHeight) {
    int snappedX = proposedX;
    int snappedY = proposedY;
    int bestXDistance = SNAP_DISTANCE + 1;
    int bestYDistance = SNAP_DISTANCE + 1;

    int[] screenXCandidates = {4, screenWidth - moving.width - 4};
    int[] screenYCandidates = {4, screenHeight - moving.height - 4};
    for (int candidate : screenXCandidates) {
      int distance = Math.abs(proposedX - candidate);
      if (distance < bestXDistance) { bestXDistance = distance; snappedX = candidate; }
    }
    for (int candidate : screenYCandidates) {
      int distance = Math.abs(proposedY - candidate);
      if (distance < bestYDistance) { bestYDistance = distance; snappedY = candidate; }
    }

    for (Widget other : widgets) {
      if (other == moving || !other.visible) continue;
      int[] xCandidates = {
          other.x,
          other.x + other.width - moving.width,
          other.x + other.width + WIDGET_GAP,
          other.x - moving.width - WIDGET_GAP
      };
      int[] yCandidates = {
          other.y,
          other.y + other.height - moving.height,
          other.y + other.height + WIDGET_GAP,
          other.y - moving.height - WIDGET_GAP
      };
      for (int candidate : xCandidates) {
        int distance = Math.abs(proposedX - candidate);
        if (distance < bestXDistance) { bestXDistance = distance; snappedX = candidate; }
      }
      for (int candidate : yCandidates) {
        int distance = Math.abs(proposedY - candidate);
        if (distance < bestYDistance) { bestYDistance = distance; snappedY = candidate; }
      }
    }
    return new int[]{snappedX, snappedY};
  }

  public static void endDrag() {
    if (dragging) {
      dragging = false;
      save();
    }
  }

  private static void drawScriptStatus(GuiGraphicsExtractor gui, Font font, Widget widget) {
    drawHudCard(gui, widget, false, PURPLE);
    drawIconBadge(gui, font, widget.x + 10, widget.y + 10, "</>", PURPLE);
    gui.text(font, "Script: FarmMacro", widget.x + 38, widget.y + 10, TEXT, true);
    gui.text(font, "Status: Paused", widget.x + 38, widget.y + 26, 0xFFFFBE58, false);
    gui.text(font, "Time ran: 10m", widget.x + 38, widget.y + 42, MUTED, false);
  }

  private static void drawNbtPeek(GuiGraphicsExtractor gui, Font font, Minecraft minecraft, Widget widget) {
    String value = minecraft.hitResult == null ? "empty" : minecraft.hitResult.getType().name().toLowerCase(Locale.ROOT);
    drawHudCard(gui, widget, false, CYAN);
    drawIconBadge(gui, font, widget.x + 10, widget.y + 9, "⌖", CYAN);
    gui.text(font, "crosshair.inspect", widget.x + 38, widget.y + 8, CYAN, true);
    gui.text(font, "result: " + value, widget.x + 38, widget.y + 24, MUTED, false);
  }

  private static void drawVitals(GuiGraphicsExtractor gui, Font font, Minecraft minecraft, Widget widget) {
    int health = 0;
    int food = 0;
    if (minecraft.player != null) {
      health = Math.max(0, Math.min(20, Math.round(minecraft.player.getHealth())));
      food = Math.max(0, Math.min(20, minecraft.player.getFoodData().getFoodLevel()));
    }
    drawHudCard(gui, widget, true, RED);
    drawIconBadge(gui, font, widget.x + 10, widget.y + 9, "♥", RED);
    gui.text(font, "VITALS", widget.x + 38, widget.y + 7, MUTED, false);
    drawMeter(gui, widget.x + 38, widget.y + 22, widget.width - 48, health, RED);
    drawMeter(gui, widget.x + 38, widget.y + 32, widget.width - 48, food, 0xFFFFBE58);
  }

  private static void drawMeter(GuiGraphicsExtractor gui, int x, int y, int width, int value, int color) {
    gui.fill(x, y, x + width, y + 4, 0x66515A70);
    gui.fill(x, y, x + width * value / 20, y + 4, color);
  }

  private static void drawTextMetric(GuiGraphicsExtractor gui, Font font, Widget widget, String text, int color) {
    gui.text(font, text, widget.x, widget.y + 4, color, true);
  }

  private static void drawHudCard(GuiGraphicsExtractor gui, Widget widget, boolean soft, int accent) {
    Rect rect = new Rect(widget.x, widget.y, widget.width, widget.height);
    TritonDraw.shadow(gui, rect, 0xCC000000);
    TritonDraw.roundedBorderedGradient(gui, rect, 10, GLASS_TOP, soft ? PANEL_HOT : GLASS_BOTTOM, GLASS_STROKE, 1);
    gui.fillGradient(widget.x + 2, widget.y + 1, widget.x + widget.width - 2, widget.y + 3,
        Color.alpha(0xFFFFFFFF, 28), Color.alpha(accent, 18));
    TritonDraw.roundedRect(gui, new Rect(widget.x + 2, widget.y + 2, 3, Math.max(8, widget.height - 4)), 2, Color.alpha(accent, 88));
  }

  private static void drawIconBadge(GuiGraphicsExtractor gui, Font font, int x, int y, String icon, int accent) {
    TritonDraw.roundedBorderedGradient(gui, new Rect(x, y, 18, 18), 6,
        Color.mix(ICON_BG, accent, 0.22F), ICON_BG, Color.alpha(accent, 92), 1);
    gui.centeredText(font, icon, x + 9, y + 5, Color.alpha(accent, 238));
  }

  private static void drawToast(GuiGraphicsExtractor gui, Font font, Widget widget, String title, String value, int accent) {
    TritonDraw.skin(gui, TritonDraw.CARD,
        new Rect(widget.x + 3, widget.y, widget.width - 3, widget.height), 12);
    gui.fill(widget.x, widget.y, widget.x + 4, widget.y + widget.height, accent);
    gui.text(font, title, widget.x + 12, widget.y + 8, MUTED, false);
    gui.text(font, value, widget.x + 12, widget.y + 22, accent, false);
  }

  private static void drawEditFrame(GuiGraphicsExtractor gui, Font font, Widget widget) {
    if (!widget.name.equals(selected)) return;
    int x1 = widget.x - 3;
    int y1 = widget.y - 3;
    int x2 = widget.x + widget.width + 3;
    int y2 = widget.y + widget.height + 3;
    int length = 6;
    gui.fill(x1, y1, x1 + length, y1 + 1, STROKE_HOT);
    gui.fill(x1, y1, x1 + 1, y1 + length, STROKE_HOT);
    gui.fill(x2 - length, y1, x2, y1 + 1, STROKE_HOT);
    gui.fill(x2 - 1, y1, x2, y1 + length, STROKE_HOT);
    gui.fill(x1, y2 - 1, x1 + length, y2, STROKE_HOT);
    gui.fill(x1, y2 - length, x1 + 1, y2, STROKE_HOT);
    gui.fill(x2 - length, y2 - 1, x2, y2, STROKE_HOT);
    gui.fill(x2 - 1, y2 - length, x2, y2, STROKE_HOT);
  }

  private static void drawEditHint(GuiGraphicsExtractor gui, Font font, int screenWidth, int screenHeight) {
    int width = 214;
    int x = screenWidth / 2 - width / 2;
    int y = screenHeight - 34;
    TritonDraw.skin(gui, TritonDraw.PILL, new Rect(x, y, width, 24), 12);
    gui.centeredText(font, "Drag widgets  -  Esc or U to finish", x + width / 2, y + 8, TEXT);
  }

  private static Widget hitWidget(int mouseX, int mouseY) {
    for (int i = widgets.size() - 1; i >= 0; i--) {
      Widget widget = widgets.get(i);
      if (widget.visible && mouseX >= widget.x && mouseX <= widget.x + widget.width
          && mouseY >= widget.y && mouseY <= widget.y + widget.height) {
        return widget;
      }
    }
    return null;
  }

  private static Widget widget(String name) {
    ensureLoaded();
    return widgets.stream().filter(widget -> widget.name.equals(name)).findFirst().orElse(null);
  }

  private static void ensureLoaded() {
    if (loaded) {
      return;
    }
    loaded = true;
    widgets.clear();
    widgets.addAll(defaultWidgets());
    try {
      Path path = configPath();
      Path legacyPath = Path.of(System.getProperty("user.dir"), "config", "shulk-overlays.json");
      if (Files.notExists(path) && Files.exists(legacyPath)) {
        Files.createDirectories(path.getParent());
        Files.copy(legacyPath, path);
      }
      if (Files.exists(path)) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
          OverlayConfig config = GSON.fromJson(reader, OverlayConfig.class);
          rendererActive = config == null || config.rendererActive;
          selected = config == null || config.selected == null ? selected : config.selected;
          layoutWidth = config == null || config.layoutWidth <= 0 ? -1 : config.layoutWidth;
          layoutHeight = config == null || config.layoutHeight <= 0 ? -1 : config.layoutHeight;
          mergeWidgets(config == null ? List.of() : config.widgets);
        }
      }
    } catch (IOException ignored) {
      // no idea why windows sometimes says no here. defaults are fine.
    }
    widgets.forEach(ShulkrHudOverlay::normalizeStandardSize);
  }

  private static void normalizeStandardSize(Widget widget) {
    switch (widget.name) {
      case "FPS Counter" -> { widget.width = 84; widget.height = 18; }
      case "Coordinates" -> { widget.width = 190; widget.height = 18; }
      case "Script Status" -> {
        widget.width = 190;
        widget.height = 60;
        if (widget.x == 18 && widget.y == 158) widget.y = 138;
      }
      case "Target HUD" -> {
        widget.width = 180;
        widget.height = 48;
        if (widget.x == 18 && widget.y == 58) widget.y = 48;
      }
      case "NBT Peek" -> {
        widget.width = 180;
        widget.height = 42;
        if (widget.x == 18 && widget.y == 208) widget.y = 210;
      }
      case "Player Vitals" -> { widget.width = 186; widget.height = 44; }
      default -> { }
    }
  }

  private static void mergeWidgets(List<Widget> saved) {
    if (saved == null) {
      return;
    }
    Map<String, Widget> byName = new HashMap<>();
    for (Widget widget : widgets) {
      byName.put(widget.name, widget);
    }
    for (Widget savedWidget : saved) {
      Widget current = byName.get(savedWidget.name);
      if (current != null) {
        current.x = savedWidget.x;
        current.y = savedWidget.y;
        current.width = Math.max(70, savedWidget.width);
        current.height = Math.max(28, savedWidget.height);
        current.visible = savedWidget.visible;
      } else if (savedWidget.name != null && !savedWidget.name.isBlank()) {
        widgets.add(new Widget(savedWidget.name, savedWidget.x, savedWidget.y,
            Math.max(70, savedWidget.width), Math.max(28, savedWidget.height),
            savedWidget.visible, Math.max(0, savedWidget.order)));
      }
    }
  }

  private static void save() {
    try {
      Path path = configPath();
      Files.createDirectories(path.getParent());
      Files.writeString(path, GSON.toJson(new OverlayConfig(rendererActive, selected, layoutWidth, layoutHeight, widgets)) + "\n", StandardCharsets.UTF_8);
    } catch (IOException ignored) {
    }
  }

  private static Path configPath() {
    return Path.of(System.getProperty("user.dir"), "config", "shulkr-overlays.json");
  }

  private static List<Widget> defaultWidgets() {
    return List.of(
        new Widget("FPS Counter", 18, 18, 84, 18, true, 0),
        new Widget("Target HUD", 18, 48, 180, 48, true, 1),
        new Widget("Coordinates", 18, 108, 190, 18, true, 2),
        new Widget("Script Status", 18, 138, 190, 60, true, 3),
        new Widget("NBT Peek", 18, 210, 180, 42, false, 4),
        new Widget("Player Vitals", 18, 264, 186, 44, false, 5));
  }

  private static void clamp(Widget widget, int screenWidth, int screenHeight) {
    widget.x = clamp(widget.x, 4, Math.max(4, screenWidth - widget.width - 4));
    widget.y = clamp(widget.y, 4, Math.max(4, screenHeight - widget.height - 4));
  }

  private static void syncLayoutToWindow(Minecraft minecraft) {
    if (minecraft == null || minecraft.getWindow() == null) {
      return;
    }
    int screenWidth = minecraft.getWindow().getGuiScaledWidth();
    int screenHeight = minecraft.getWindow().getGuiScaledHeight();
    if (screenWidth <= 0 || screenHeight <= 0) {
      return;
    }
    if (layoutWidth <= 0 || layoutHeight <= 0) {
      layoutWidth = screenWidth;
      layoutHeight = screenHeight;
      return;
    }
    if (layoutWidth == screenWidth && layoutHeight == screenHeight) {
      return;
    }

    float scaleX = screenWidth / (float) layoutWidth;
    float scaleY = screenHeight / (float) layoutHeight;
    for (Widget widget : widgets) {
      widget.x = Math.round(widget.x * scaleX);
      widget.y = Math.round(widget.y * scaleY);
      clamp(widget, screenWidth, screenHeight);
    }
    layoutWidth = screenWidth;
    layoutHeight = screenHeight;
    save();
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static void fillRounded(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int radius, int color) {
    int effectiveRadius = Math.max(1, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
    gui.fill(x1, y1 + effectiveRadius, x2, y2 - effectiveRadius, color);
    for (int dy = 0; dy < effectiveRadius; dy++) {
      int inset = roundedInset(effectiveRadius, dy);
      gui.fill(x1 + inset, y1 + dy, x2 - inset, y1 + dy + 1, color);
      gui.fill(x1 + inset, y2 - dy - 1, x2 - inset, y2 - dy, color);
    }
  }

  private static void outlineRounded(GuiGraphicsExtractor gui, int x, int y, int width, int height, int radius, int color) {
    int effectiveRadius = Math.max(1, Math.min(radius, Math.min(width / 2, height / 2)));
    for (int dy = 0; dy < effectiveRadius; dy++) {
      int inset = roundedInset(effectiveRadius, dy);
      gui.fill(x + inset, y + dy, x + width - inset, y + dy + 1, color);
      gui.fill(x + inset, y + height - dy - 1, x + width - inset, y + height - dy, color);
    }
    gui.verticalLine(x, y + effectiveRadius, y + height - effectiveRadius - 1, color);
    gui.verticalLine(x + width - 1, y + effectiveRadius, y + height - effectiveRadius - 1, color);
  }

  private static int roundedInset(int radius, int dy) {
    double y = (radius - 1) - dy;
    double arc = Math.sqrt(Math.max(0.0, radius * radius - y * y));
    return Math.max(0, radius - (int) Math.round(arc));
  }

  private static final class OverlayConfig {
    boolean rendererActive = true;
    String selected = "Target HUD";
    int layoutWidth = -1;
    int layoutHeight = -1;
    List<Widget> widgets = List.of();

    OverlayConfig(boolean rendererActive, String selected, int layoutWidth, int layoutHeight, List<Widget> widgets) {
      this.rendererActive = rendererActive;
      this.selected = selected;
      this.layoutWidth = layoutWidth;
      this.layoutHeight = layoutHeight;
      this.widgets = widgets;
    }
  }

  private static final class Widget {
    String name;
    int x;
    int y;
    int width;
    int height;
    boolean visible;
    int order;

    Widget(String name, int x, int y, int width, int height, boolean visible, int order) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.visible = visible;
      this.order = order;
    }
  }
}
