package net.minescript.fabric.fluxus;

import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minescript.common.EntityExporter;
import net.minescript.common.blocks.BlockPositionReader;
import net.minescript.common.dataclasses.EntityData;
import org.lwjgl.glfw.GLFW;

public final class WindowSpyOverlay {
  public static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("minescript", "windowspy");

  private static final int WINDOW_BG = 0xF10C1220;
  private static final int HEADER_BG = 0xEE182338;
  private static final int PANEL_BG = 0xEE10182A;
  private static final int PANEL_ALT_BG = 0xEE162137;
  private static final int STROKE = 0xAA3A5378;
  private static final int STROKE_SOFT = 0x66415B84;
  private static final int PILL_BG = 0xFF1F2C45;
  private static final int PILL_ACTIVE_BG = 0xFF5C6BFF;
  private static final int CARD_BG = 0xFF19253B;
  private static final int BUTTON_BG = 0xFF22304A;
  private static final int BUTTON_ACTIVE = 0xFF596CFF;
  private static final int TEXT_PRIMARY = 0xFFF5F7FF;
  private static final int TEXT_MUTED = 0xC6BECBE7;
  private static final int TEXT_SOFT = 0x9EA6B8D5;

  private static boolean visible;
  private static boolean focusMode;
  private static boolean dragging;
  private static boolean regrabMouse;
  private static int x = 12;
  private static int y = 12;
  private static int width = 560;
  private static int height = 360;
  private static int dragOffsetX;
  private static int dragOffsetY;
  private static int tickCounter;
  private static boolean previousLeftPressed;
  private static boolean previousTabPressed;
  private static InspectMode inspectMode = InspectMode.LOOKED_AT;
  private static SpySnapshot currentSnapshot =
      new SpySnapshot(
          "Nothing selected",
          "Aim at a block or entity, then focus the inspector if you want to interact with it.",
          "WindowSpy will follow whatever is under your crosshair.",
          "");

  private WindowSpyOverlay() {}

  public static HudElement hudElement() {
    return WindowSpyOverlay::render;
  }

  public static void toggle() {
    visible = !visible;
    if (!visible) {
      dragging = false;
      previousTabPressed = false;
      exitFocusMode(Minecraft.getInstance());
    }
  }

  public static boolean isVisible() {
    return visible;
  }

  public static void onClientTick(Minecraft minecraft) {
    if (!visible || minecraft == null || minecraft.getWindow() == null || minecraft.mouseHandler == null) {
      return;
    }

    boolean tabDown = isTabDown(minecraft);
    if (tabDown && !previousTabPressed) {
      if (focusMode) {
        dragging = false;
        exitFocusMode(minecraft);
      } else {
        enterFocusMode(minecraft);
      }
    }
    previousTabPressed = tabDown;

    if (!focusMode && dragging) {
      dragging = false;
    }

    if (focusMode) {
      int mouseX = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
      int mouseY = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
      boolean leftPressed = minecraft.mouseHandler.isLeftPressed();
      if (leftPressed && !previousLeftPressed) {
        ButtonRect inspectLookedAt = buttonRect(0);
        ButtonRect inspectBlock = buttonRect(1);
        ButtonRect inspectEntity = buttonRect(2);
        ButtonRect copyNbt = buttonRect(3);
        if (titleBarContains(mouseX, mouseY)) {
          dragging = true;
          dragOffsetX = mouseX - x;
          dragOffsetY = mouseY - y;
        } else if (inspectLookedAt.contains(mouseX, mouseY)) {
          inspectMode = InspectMode.LOOKED_AT;
          currentSnapshot = snapshotLookedAt();
        } else if (inspectBlock.contains(mouseX, mouseY)) {
          inspectMode = InspectMode.BLOCK;
          currentSnapshot = snapshotBlock();
        } else if (inspectEntity.contains(mouseX, mouseY)) {
          inspectMode = InspectMode.ENTITY;
          currentSnapshot = snapshotEntity();
        } else if (copyNbt.contains(mouseX, mouseY)) {
          copyNbt();
        }
      } else if (!leftPressed && previousLeftPressed) {
        dragging = false;
      }
      previousLeftPressed = leftPressed;
    } else {
      previousLeftPressed = false;
    }

    if (dragging) {
      int mouseX = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
      int mouseY = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
      x = mouseX - dragOffsetX;
      y = mouseY - dragOffsetY;
      clampToWindow(minecraft);
    }

    tickCounter++;
    if (tickCounter % 4 == 0) {
      currentSnapshot = switch (inspectMode) {
        case LOOKED_AT -> snapshotLookedAt();
        case BLOCK -> snapshotBlock();
        case ENTITY -> snapshotEntity();
      };
    }
  }

  private static void render(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
    if (!visible) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    Font font = minecraft.font;
    clampToWindow(minecraft);

    int titleHeight = 42;
    int toolbarTop = y + titleHeight + 10;
    int toolbarHeight = 42;
    int summaryTop = toolbarTop + toolbarHeight + 10;
    int summaryHeight = 82;
    int rawTop = summaryTop + summaryHeight + 10;
    int rawHeight = Math.max(120, y + height - rawTop - 10);

    drawSurface(gui, x, y, x + width, y + height, 18, WINDOW_BG, STROKE);
    drawSurface(gui, x + 8, y + 8, x + width - 8, y + titleHeight, 14, HEADER_BG, STROKE_SOFT);

    fillRounded(gui, x + 12, y + 12, x + 40, y + 40, 10, PILL_ACTIVE_BG);
    gui.centeredText(font, "W", x + 26, y + 21, TEXT_PRIMARY);
    gui.text(font, "WindowSpy", x + 50, y + 16, TEXT_PRIMARY, false);
    gui.text(font, "Fluxus live inspector overlay", x + 50, y + 29, TEXT_SOFT, false);
    drawChip(gui, font, x + width - 128, y + 12, 96, 28, focusMode ? "Focused" : "Live");

    fillRounded(gui, x + 8, toolbarTop, x + width - 8, toolbarTop + toolbarHeight, 14, PANEL_BG);
    outlineRounded(gui, x + 8, toolbarTop, width - 16, toolbarHeight, 14, STROKE_SOFT);

    drawButton(gui, font, buttonRect(0), "Inspect Looked At", inspectMode == InspectMode.LOOKED_AT);
    drawButton(gui, font, buttonRect(1), "Inspect Block", inspectMode == InspectMode.BLOCK);
    drawButton(gui, font, buttonRect(2), "Inspect Entity", inspectMode == InspectMode.ENTITY);
    drawButton(gui, font, buttonRect(3), "Copy NBT", false);

    drawSurface(gui, x + 8, summaryTop, x + width - 8, summaryTop + summaryHeight, 14, CARD_BG, STROKE_SOFT);
    gui.text(font, currentSnapshot.title(), x + 20, summaryTop + 14, TEXT_PRIMARY, false);
    gui.text(font, currentSnapshot.meta(), x + 20, summaryTop + 31, TEXT_MUTED, false);
    gui.textWithWordWrap(
        font,
        Component.literal(currentSnapshot.details()),
        x + 20,
        summaryTop + 48,
        width - 44,
        TEXT_SOFT);

    drawSurface(gui, x + 8, rawTop, x + width - 8, rawTop + rawHeight, 14, PANEL_ALT_BG, STROKE_SOFT);
    gui.text(font, "Raw NBT / Live Data", x + 20, rawTop + 14, TEXT_PRIMARY, false);
    gui.textWithWordWrap(
        font,
        Component.literal(
            focusMode
                ? "Inspector is focused. Drag the title bar or click toolbar buttons."
                : "Overlay stays on while you move. Click a toolbar button to inspect."),
        x + 20,
        rawTop + 31,
        width - 44,
        TEXT_MUTED);
    gui.textWithWordWrap(
        font,
        Component.literal(currentSnapshot.rawNbt().isBlank() ? "No raw NBT data was available." : currentSnapshot.rawNbt()),
        x + 20,
        rawTop + 66,
        width - 44,
        TEXT_PRIMARY);
  }

  private static SpySnapshot snapshotLookedAt() {
    SpySnapshot snapshot = inspectTargetedEntity(8.0);
    if (snapshot == null) {
      snapshot = inspectTargetedBlock(8.0);
    }
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "Nothing selected",
              "Aim at a block or entity",
              "WindowSpy will follow whatever is under your crosshair.",
              "");
    }
    return snapshot;
  }

  private static SpySnapshot snapshotBlock() {
    SpySnapshot snapshot = inspectTargetedBlock(8.0);
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "No block selected",
              "Block mode",
              "Aim directly at a block to inspect its state or block-entity data.",
              "");
    }
    return snapshot;
  }

  private static SpySnapshot snapshotEntity() {
    SpySnapshot snapshot = inspectTargetedEntity(8.0);
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "No entity selected",
              "Entity mode",
              "Aim directly at an entity to inspect its exported data and NBT.",
              "");
    }
    return snapshot;
  }

  private static SpySnapshot inspectTargetedBlock(double maxDistance) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || minecraft.player == null || minecraft.level == null) {
      return null;
    }
    HitResult hitResult = minecraft.player.pick(maxDistance, 0.0f, false);
    if (hitResult.getType() != HitResult.Type.BLOCK) {
      return null;
    }
    BlockHitResult blockHit = (BlockHitResult) hitResult;
    var pos = blockHit.getBlockPos();
    Level level = minecraft.level;
    String state = BlockPositionReader.getBlockStateString(level, pos);
    BlockEntity blockEntity = level.getBlockEntity(pos);
    String nbt = blockEntity == null ? "" : readBlockEntityNbt(level, blockEntity);
    String details =
        "Position: "
            + pos.getX()
            + ", "
            + pos.getY()
            + ", "
            + pos.getZ()
            + "  |  Face: "
            + blockHit.getDirection()
            + "  |  Block entity: "
            + (blockEntity != null ? "yes" : "no");
    String meta =
        blockEntity == null
            ? "Plain block state"
            : blockEntity.getType().toString().replace("BlockEntityType[", "").replace("]", "");
    String raw = blockEntity == null ? "No block entity NBT is available for this block.\n\nState:\n" + safe(state) : nbt;
    return new SpySnapshot(safe(state), meta, details, raw);
  }

  private static SpySnapshot inspectTargetedEntity(double maxDistance) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || minecraft.player == null) {
      return null;
    }
    Entity entity = net.minecraft.client.renderer.debug.DebugRenderer.getTargetedEntity(minecraft.player, (int) maxDistance).orElse(null);
    if (entity == null) {
      return null;
    }
    EntityData data = new EntityExporter(0.0, true).export(entity);
    String details =
        "UUID: "
            + safe(data.uuid)
            + "  |  Id: "
            + data.id
            + "  |  Pos: "
            + formatDouble(data.position[0])
            + ", "
            + formatDouble(data.position[1])
            + ", "
            + formatDouble(data.position[2])
            + (data.health == null ? "" : "  |  HP: " + formatDouble(data.health));
    return new SpySnapshot(safe(data.name), safe(data.type), details, safe(data.nbt));
  }

  private static String readBlockEntityNbt(Level level, BlockEntity blockEntity) {
    try {
      var tag = blockEntity.saveWithFullMetadata(level.registryAccess());
      return tag == null ? "" : tag.toString();
    } catch (Throwable ignored) {
      try {
        var tag = blockEntity.saveWithoutMetadata(level.registryAccess());
        return tag == null ? "" : tag.toString();
      } catch (Throwable secondIgnored) {
        return "WindowSpy could not serialize this block entity on the client.";
      }
    }
  }

  private static void copyNbt() {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || minecraft.keyboardHandler == null) {
      return;
    }
    minecraft.keyboardHandler.setClipboard(currentSnapshot.rawNbt());
  }

  private static void enterFocusMode(Minecraft minecraft) {
    focusMode = true;
    if (minecraft.mouseHandler.isMouseGrabbed()) {
      regrabMouse = true;
      minecraft.mouseHandler.releaseMouse();
    } else {
      regrabMouse = false;
    }
  }

  private static void exitFocusMode(Minecraft minecraft) {
    focusMode = false;
    if (regrabMouse && minecraft != null && minecraft.mouseHandler != null) {
      minecraft.mouseHandler.grabMouse();
    }
    regrabMouse = false;
  }

  private static boolean isTabDown(Minecraft minecraft) {
    long windowHandle = minecraft.getWindow().handle();
    return GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;
  }

  private static void clampToWindow(Minecraft minecraft) {
    if (minecraft == null || minecraft.getWindow() == null) {
      return;
    }
    int maxX = Math.max(8, minecraft.getWindow().getGuiScaledWidth() - width - 8);
    int maxY = Math.max(8, minecraft.getWindow().getGuiScaledHeight() - height - 8);
    x = clamp(x, 8, maxX);
    y = clamp(y, 8, maxY);
  }

  private static boolean titleBarContains(int mouseX, int mouseY) {
    return mouseX >= x + 8 && mouseX <= x + width - 8 && mouseY >= y + 8 && mouseY <= y + 42;
  }

  private static ButtonRect buttonRect(int index) {
    int left = x + 18;
    int right = x + width - 18;
    int gap = 8;
    int buttonWidth = (right - left - gap * 3) / 4;
    int buttonHeight = 28;
    int top = y + 54;
    int bx = left + index * (buttonWidth + gap);
    return new ButtonRect(bx, top, buttonWidth, buttonHeight);
  }

  private static void drawButton(
      GuiGraphicsExtractor gui, Font font, ButtonRect rect, String label, boolean active) {
    drawSurface(
        gui,
        rect.x(),
        rect.y(),
        rect.x() + rect.width(),
        rect.y() + rect.height(),
        14,
        active ? BUTTON_ACTIVE : BUTTON_BG,
        active ? 0xFFA58DFF : STROKE_SOFT);
    gui.centeredText(font, label, rect.x() + rect.width() / 2, rect.y() + 13, TEXT_PRIMARY);
  }

  private static void drawChip(GuiGraphicsExtractor gui, Font font, int x, int y, int width, int height, String text) {
    drawSurface(gui, x, y, x + width, y + height, 12, PILL_BG, STROKE_SOFT);
    gui.centeredText(font, text, x + width / 2, y + 10, TEXT_PRIMARY);
  }

  private static void drawSurface(
      GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int radius, int fill, int stroke) {
    fillRounded(gui, x1, y1, x2, y2, radius, stroke);
    fillRounded(gui, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(1, radius - 1), fill);
  }

  private static void fillRounded(
      GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int radius, int color) {
    int effectiveRadius = Math.max(1, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
    gui.fill(x1, y1 + effectiveRadius, x2, y2 - effectiveRadius, color);
    for (int dy = 0; dy < effectiveRadius; dy++) {
      int inset = roundedInset(effectiveRadius, dy);
      gui.fill(x1 + inset, y1 + dy, x2 - inset, y1 + dy + 1, color);
      gui.fill(x1 + inset, y2 - dy - 1, x2 - inset, y2 - dy, color);
    }
  }

  private static void outlineRounded(
      GuiGraphicsExtractor gui, int x, int y, int width, int height, int radius, int color) {
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

  private static String safe(String value) {
    return value == null || value.isBlank() ? "Unknown" : value;
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private record SpySnapshot(String title, String meta, String details, String rawNbt) {}

  private record ButtonRect(int x, int y, int width, int height) {
    private boolean contains(int mouseX, int mouseY) {
      return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
  }

  private enum InspectMode {
    LOOKED_AT,
    BLOCK,
    ENTITY
  }
}
