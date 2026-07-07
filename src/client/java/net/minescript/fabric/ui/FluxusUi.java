package net.minescript.fabric.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FluxusUi {
  public static final int WINDOW_BG = 0xF10C1220;
  public static final int HEADER_BG = 0xEE182338;
  public static final int PANEL_BG = 0xEE10182A;
  public static final int CARD_BG = 0xFF19253B;
  public static final int METRIC_BG = 0xFF162239;
  public static final int PILL_BG = 0xFF1F2C45;
  public static final int PILL_ACTIVE_BG = 0xFF5C6BFF;
  public static final int STROKE = 0xAA3A5378;
  public static final int STROKE_SOFT = 0x66415B84;
  public static final int GLOW = 0x443B57FF;
  public static final int TEXT_PRIMARY = 0xFFF5F7FF;
  public static final int TEXT_MUTED = 0xC6BECBE7;
  public static final int TEXT_SOFT = 0x9EA6B8D5;

  private FluxusUi() {}

  public static void drawPanel(
      GuiGraphicsExtractor gui, int x, int y, int width, int height, int radius) {
    drawSurface(gui, x, y, x + width, y + height, radius, WINDOW_BG, STROKE);
  }

  public static void drawGlow(
      GuiGraphicsExtractor gui, int x, int y, int width, int height, int color) {
    gui.fill(x - 2, y - 2, x + width + 2, y, color);
    gui.fill(x - 3, y - 1, x + width + 3, y + 1, soften(color));
    gui.fill(x - 4, y + height - 1, x + width + 4, y + height + 1, soften(color));
    gui.fill(x - 2, y + height, x + width + 2, y + height + 2, color);
  }

  public static void drawHeaderBar(
      GuiGraphicsExtractor gui, int x, int y, int width, int height, boolean accentLeft) {
    if (accentLeft) {
      gui.fill(x + 8, y + 8, x + 14, y + height - 8, PILL_ACTIVE_BG);
    }
    drawSurface(gui, x + 16, y + 8, x + width - 8, y + height, 14, HEADER_BG, STROKE_SOFT);
  }

  public static void drawDivider(GuiGraphicsExtractor gui, int x, int y, int width) {
    gui.fill(x, y, x + width, y + 1, STROKE_SOFT);
  }

  public static void drawSurface(
      GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int radius, int fill, int stroke) {
    fillRounded(gui, x1, y1, x2, y2, radius, stroke);
    fillRounded(gui, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(1, radius - 1), fill);
  }

  public static void drawPill(
      GuiGraphicsExtractor gui,
      Font font,
      int x,
      int y,
      int width,
      int height,
      String text,
      boolean active) {
    drawSurface(gui, x, y, x + width, y + height, 12, active ? PILL_ACTIVE_BG : PILL_BG, STROKE_SOFT);
    gui.centeredText(font, text, x + width / 2, y + (height / 2) - 4, TEXT_PRIMARY);
  }

  public static void drawMetric(
      GuiGraphicsExtractor gui,
      Font font,
      int x,
      int y,
      int width,
      String label,
      String value) {
    drawSurface(gui, x, y, x + width, y + 28, 10, METRIC_BG, STROKE_SOFT);
    gui.text(font, label, x + 10, y + 7, TEXT_MUTED, false);
    gui.text(font, abbreviate(font, value, width - 20), x + 10, y + 18, TEXT_PRIMARY, false);
  }

  public static void drawProgress(
      GuiGraphicsExtractor gui, int x, int y, int width, int height, double progress) {
    double clamped = Math.max(0.0, Math.min(1.0, progress));
    int fillWidth = (int) Math.round(width * clamped);
    drawSurface(gui, x, y, x + width, y + height, 6, PANEL_BG, STROKE_SOFT);
    if (fillWidth > 0) {
      fillRounded(gui, x + 1, y + 1, x + Math.max(2, fillWidth), y + height - 1, 5, PILL_ACTIVE_BG);
      if (fillWidth > 6) {
        gui.fill(x + 3, y + 2, x + Math.min(width - 3, fillWidth), y + height - 2, 0x553B57FF);
      }
    }
  }

  public static void drawHeaderIcon(
      GuiGraphicsExtractor gui, int x, int y, int width, int height, boolean active) {
    fillRounded(gui, x, y, x + width, y + height, 10, active ? PILL_ACTIVE_BG : PILL_BG);
    gui.fill(x + 8, y + 6, x + width - 8, y + 7, TEXT_PRIMARY);
    gui.fill(x + 8, y + 11, x + width - 12, y + 12, TEXT_PRIMARY);
    gui.fill(x + 12, y + 16, x + width - 16, y + 17, TEXT_PRIMARY);
  }

  public static void drawSectionTitle(
      GuiGraphicsExtractor gui, Font font, int x, int y, String title, String subtitle) {
    gui.text(font, title, x, y, TEXT_PRIMARY, false);
    if (subtitle != null && !subtitle.isBlank()) {
      gui.text(font, subtitle, x, y + 13, TEXT_SOFT, false);
    }
  }

  public static String abbreviate(Font font, String text, int maxWidth) {
    if (text == null) {
      return "";
    }
    if (font.width(text) <= maxWidth) {
      return text;
    }
    String ellipsis = "...";
    if (font.width(ellipsis) > maxWidth) {
      return "";
    }
    int end = text.length();
    while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
      end--;
    }
    return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
  }

  public static void fillRounded(
      GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int radius, int color) {
    int effectiveRadius = Math.max(1, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
    gui.fill(x1, y1 + effectiveRadius, x2, y2 - effectiveRadius, color);
    for (int dy = 0; dy < effectiveRadius; dy++) {
      int inset = roundedInset(effectiveRadius, dy);
      gui.fill(x1 + inset, y1 + dy, x2 - inset, y1 + dy + 1, color);
      gui.fill(x1 + inset, y2 - dy - 1, x2 - inset, y2 - dy, color);
    }
  }

  private static int soften(int color) {
    return (color & 0x00FFFFFF) | 0x22000000;
  }

  private static int roundedInset(int radius, int dy) {
    double y = (radius - 1) - dy;
    double arc = Math.sqrt(Math.max(0.0, radius * radius - y * y));
    return Math.max(0, radius - (int) Math.round(arc));
  }
}
