package net.minescript.fabric.fluxus;

import icyllis.modernui.R;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minescript.common.EntityExporter;
import net.minescript.common.blocks.BlockPositionReader;
import net.minescript.common.dataclasses.EntityData;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class WindowSpyFragment extends Fragment {
  private static final int WINDOW_BG = 0xF10C1220;
  private static final int HEADER_BG = 0xF01B263B;
  private static final int PANEL_BG = 0xEE10182A;
  private static final int PANEL_ALT_BG = 0xEE162137;
  private static final int PANEL_STROKE = 0xAA3A5378;
  private static final int PANEL_STROKE_SOFT = 0x66415B84;
  private static final int PILL_BG = 0xFF1F2C45;
  private static final int PILL_HOVER_BG = 0xFF273854;
  private static final int BUTTON_BG = 0xFF22304A;
  private static final int BUTTON_HOVER_BG = 0xFF2B3C5D;
  private static final int BUTTON_PRIMARY_BG = 0xFF596CFF;
  private static final int BUTTON_PRIMARY_HOVER_BG = 0xFF6F82FF;
  private static final int TEXT_PRIMARY = 0xFFF5F7FF;
  private static final int TEXT_MUTED = 0xC6BECBE7;
  private static final int TEXT_SOFT = 0x9EA6B8D5;
  private static WindowSpyFragment activeInstance;

  private View rootView;
  private LinearLayout windowCard;
  private float dragAnchorX;
  private float dragAnchorY;
  private float dragStartX;
  private float dragStartY;
  private int tickCounter;
  private boolean refreshRequested = true;
  private InspectMode inspectMode = InspectMode.LOOKED_AT;
  private TextView summaryTitle;
  private TextView summaryMeta;
  private TextView detailsText;
  private TextView nbtText;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable DataSet savedInstanceState) {
    Context context = requireContext();
    activeInstance = this;

    var root = new LinearLayout(context);
    rootView = root;
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.TOP | Gravity.START);
    root.setPadding(root.dp(12), root.dp(12), root.dp(12), root.dp(12));

    windowCard = new LinearLayout(context);
    windowCard.setOrientation(LinearLayout.VERTICAL);
    applySurface(windowCard, WINDOW_BG, PANEL_STROKE, 18, 10);
    windowCard.setMinimumWidth(windowCard.dp(520));
    windowCard.setMinimumHeight(windowCard.dp(340));
    windowCard.addView(createHeader(context), new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    var bodyParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
    bodyParams.topMargin = windowCard.dp(8);
    windowCard.addView(createBody(context), bodyParams);

    var windowParams = new LinearLayout.LayoutParams(windowCard.dp(560), windowCard.dp(360));
    root.addView(windowCard, windowParams);

    applySnapshot(
        new SpySnapshot(
            "WindowSpy",
            "Waiting for target",
            "Aim at a block or entity and the inspector will update live.",
            ""));
    return root;
  }

  private View createHeader(Context context) {
    var header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    applySurface(header, HEADER_BG, PANEL_STROKE_SOFT, 14, 10);

    var badge = new TextView(context);
    badge.setText("W");
    badge.setTextSize(18);
    badge.setTextStyle(Typeface.BOLD);
    badge.setTextColor(TEXT_PRIMARY);
    badge.setGravity(Gravity.CENTER);
    applySurface(badge, BUTTON_PRIMARY_BG, 0xFF9DA8FF, 12, 0);
    badge.setMinimumWidth(badge.dp(32));
    badge.setMinimumHeight(badge.dp(32));
    header.addView(badge, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

    var titleWrap = new LinearLayout(context);
    titleWrap.setOrientation(LinearLayout.VERTICAL);
    var titleParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
    titleParams.leftMargin = header.dp(14);

    var title = new TextView(context);
    title.setText("WindowSpy");
    title.setTextSize(16);
    title.setTextStyle(Typeface.BOLD);
    title.setTextColor(TEXT_PRIMARY);
    titleWrap.addView(title);

    var subtitle = new TextView(context);
    subtitle.setText("Live inspector overlay");
    subtitle.setTextSize(11);
    subtitle.setTextColor(TEXT_SOFT);
    titleWrap.addView(subtitle);

    header.addView(titleWrap, titleParams);

    var modeChip = createChip(context, "ALT = move");
    var chipParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
    chipParams.rightMargin = header.dp(8);
    header.addView(modeChip, chipParams);

    var closeChip = createChip(context, "ESC");
    header.addView(closeChip, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    header.setOnTouchListener(
        (v, event) -> {
          if (windowCard == null || rootView == null || !isMoveModifierDown()) {
            return false;
          }
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            dragAnchorX = event.getX();
            dragAnchorY = event.getY();
            dragStartX = windowCard.getTranslationX();
            dragStartY = windowCard.getTranslationY();
            return true;
          }
          if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float targetX = dragStartX + event.getX() - dragAnchorX;
            float targetY = dragStartY + event.getY() - dragAnchorY;
            float minX = -windowCard.getLeft();
            float minY = -windowCard.getTop();
            float maxX = Math.max(minX, rootView.getWidth() - windowCard.getRight());
            float maxY = Math.max(minY, rootView.getHeight() - windowCard.getBottom());
            windowCard.setTranslationX(clamp(targetX, minX, maxX));
            windowCard.setTranslationY(clamp(targetY, minY, maxY));
            return true;
          }
          return false;
        });
    return header;
  }

  private View createBody(Context context) {
    var body = new LinearLayout(context);
    body.setOrientation(LinearLayout.VERTICAL);

    var toolbar = new LinearLayout(context);
    toolbar.setOrientation(LinearLayout.VERTICAL);
    applySurface(toolbar, PANEL_ALT_BG, PANEL_STROKE_SOFT, 14, 10);

    var inspectLookedAt = createActionButton(context, "Inspect Looked At", true);
    inspectLookedAt.setOnClickListener(
        v -> {
          inspectMode = InspectMode.LOOKED_AT;
          requestRefresh();
        });

    var inspectBlock = createActionButton(context, "Inspect Block", false);
    inspectBlock.setOnClickListener(
        v -> {
          inspectMode = InspectMode.BLOCK;
          requestRefresh();
        });

    var inspectEntity = createActionButton(context, "Inspect Entity", false);
    inspectEntity.setOnClickListener(
        v -> {
          inspectMode = InspectMode.ENTITY;
          requestRefresh();
        });

    var copyNbt = createActionButton(context, "Copy NBT", false);
    copyNbt.setOnClickListener(v -> copyNbt());

    var rowOne = new LinearLayout(context);
    rowOne.setOrientation(LinearLayout.HORIZONTAL);
    rowOne.addView(inspectLookedAt, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
    var inspectBlockParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
    inspectBlockParams.leftMargin = rowOne.dp(8);
    rowOne.addView(inspectBlock, inspectBlockParams);
    toolbar.addView(rowOne, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

    var rowTwo = new LinearLayout(context);
    rowTwo.setOrientation(LinearLayout.HORIZONTAL);
    var rowTwoParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    rowTwoParams.topMargin = toolbar.dp(8);
    rowTwo.addView(inspectEntity, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
    var copyNbtParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
    copyNbtParams.leftMargin = rowTwo.dp(8);
    rowTwo.addView(copyNbt, copyNbtParams);
    toolbar.addView(rowTwo, rowTwoParams);

    body.addView(toolbar, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

    var content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    var contentParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
    contentParams.topMargin = body.dp(10);

    var infoCard = new LinearLayout(context);
    infoCard.setOrientation(LinearLayout.VERTICAL);
    applySurface(infoCard, PANEL_ALT_BG, PANEL_STROKE_SOFT, 14, 10);

    summaryTitle = new TextView(context);
    summaryTitle.setTextSize(18);
    summaryTitle.setTextStyle(Typeface.BOLD);
    summaryTitle.setTextColor(TEXT_PRIMARY);
    infoCard.addView(summaryTitle);

    summaryMeta = new TextView(context);
    summaryMeta.setTextSize(12);
    summaryMeta.setTextColor(TEXT_MUTED);
    var metaParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    metaParams.topMargin = infoCard.dp(4);
    infoCard.addView(summaryMeta, metaParams);

    var detailScroll = new ScrollView(context);
    var detailScrollParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
    detailScrollParams.topMargin = infoCard.dp(10);

    detailsText = new TextView(context);
    detailsText.setTextSize(12);
    detailsText.setTextColor(TEXT_PRIMARY);
    detailsText.setTextIsSelectable(true);
    applySurface(detailsText, PANEL_BG, PANEL_STROKE_SOFT, 18, 12);
    detailScroll.addView(
        detailsText,
        new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
    infoCard.addView(detailScroll, detailScrollParams);

    content.addView(infoCard, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 3));

    var rightCard = new LinearLayout(context);
    rightCard.setOrientation(LinearLayout.VERTICAL);
    applySurface(rightCard, PANEL_ALT_BG, PANEL_STROKE_SOFT, 14, 10);
    var rightParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 4);
    rightParams.topMargin = rightCard.dp(10);

    var rawTitle = new TextView(context);
    rawTitle.setText("Raw NBT / Live Data");
    rawTitle.setTextSize(16);
    rawTitle.setTextStyle(Typeface.BOLD);
    rawTitle.setTextColor(TEXT_PRIMARY);
    rightCard.addView(rawTitle);

    var rawSub = new TextView(context);
    rawSub.setText("Updates automatically from whatever you are looking at. Hold ALT to drag the overlay.");
    rawSub.setTextSize(12);
    rawSub.setTextColor(TEXT_MUTED);
    var rawSubParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    rawSubParams.topMargin = rightCard.dp(4);
    rightCard.addView(rawSub, rawSubParams);

    var nbtScroll = new ScrollView(context);
    var nbtScrollParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
    nbtScrollParams.topMargin = rightCard.dp(12);
    nbtText = new TextView(context);
    nbtText.setTextSize(12);
    nbtText.setTypeface(Typeface.getSystemFont("JetBrains Mono Medium"));
    nbtText.setTextColor(TEXT_PRIMARY);
    nbtText.setTextIsSelectable(true);
    applySurface(nbtText, PANEL_BG, PANEL_STROKE_SOFT, 18, 12);
    nbtScroll.addView(nbtText, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
    rightCard.addView(nbtScroll, nbtScrollParams);

    content.addView(rightCard, rightParams);
    body.addView(content, contentParams);
    return body;
  }

  private void inspectLookedAt() {
    var snapshot = inspectTargetedEntity(8.0);
    if (snapshot == null) {
      snapshot = inspectTargetedBlock(8.0);
    }
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "Nothing selected",
              "Aim at a block or entity, then press one of the inspect buttons.",
              "No current crosshair target was available.",
              "");
    }
    applySnapshot(snapshot);
  }

  private void inspectBlock() {
    var snapshot = inspectTargetedBlock(8.0);
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "No block selected",
              "Aim directly at a block to inspect it.",
              "WindowSpy could not find a block under your crosshair.",
              "");
    }
    applySnapshot(snapshot);
  }

  private void inspectEntity() {
    var snapshot = inspectTargetedEntity(8.0);
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "No entity selected",
              "Aim directly at an entity to inspect it.",
              "WindowSpy could not find an entity under your crosshair.",
              "");
    }
    applySnapshot(snapshot);
  }

  @Nullable
  private SpySnapshot inspectTargetedBlock(double maxDistance) {
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
    String state = BlockPositionReader.getBlockStateString(level, pos);
    BlockEntity blockEntity = level.getBlockEntity(pos);
    String nbt = blockEntity == null ? "" : readBlockEntityNbt(level, blockEntity);
    String details =
        "Kind: Block\n"
            + "State: "
            + safe(state)
            + "\n"
            + "Position: "
            + pos.getX()
            + ", "
            + pos.getY()
            + ", "
            + pos.getZ()
            + "\n"
            + "Face: "
            + blockHit.getDirection()
            + "\n"
            + "Has Block Entity: "
            + (blockEntity != null ? "Yes" : "No");
    String meta =
        blockEntity == null
            ? "Plain block state"
            : blockEntity.getType().toString().replace("BlockEntityType[", "").replace("]", "");
    String raw = blockEntity == null ? "No block entity NBT is available for this block.\n\nState:\n" + safe(state) : nbt;
    return new SpySnapshot(safe(state), meta, details, raw);
  }

  @Nullable
  private SpySnapshot inspectTargetedEntity(double maxDistance) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || minecraft.player == null) {
      return null;
    }
    Entity entity = DebugRenderer.getTargetedEntity(minecraft.player, (int) maxDistance).orElse(null);
    if (entity == null) {
      return null;
    }
    EntityData data = new EntityExporter(0.0, true).export(entity);
    String title = safe(data.name);
    String meta = safe(data.type);
    String details =
        "Kind: Entity\n"
            + "UUID: "
            + safe(data.uuid)
            + "\n"
            + "Id: "
            + data.id
            + "\n"
            + "Position: "
            + formatDouble(data.position[0])
            + ", "
            + formatDouble(data.position[1])
            + ", "
            + formatDouble(data.position[2])
            + "\n"
            + "Yaw/Pitch: "
            + formatDouble(data.yaw)
            + " / "
            + formatDouble(data.pitch)
            + (data.health == null ? "" : "\nHealth: " + formatDouble(data.health));
    return new SpySnapshot(title, meta, details, safe(data.nbt));
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

  private void copyNbt() {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || minecraft.keyboardHandler == null || nbtText == null) {
      return;
    }
    minecraft.keyboardHandler.setClipboard(nbtText.getText().toString());
  }

  private void applySnapshot(SpySnapshot snapshot) {
    if (summaryTitle != null) {
      summaryTitle.setText(snapshot.title());
    }
    if (summaryMeta != null) {
      summaryMeta.setText(snapshot.meta());
    }
    if (detailsText != null) {
      detailsText.setText(snapshot.details());
    }
    if (nbtText != null) {
      nbtText.setText(snapshot.rawNbt().isBlank() ? "No raw NBT data was available." : snapshot.rawNbt());
    }
  }

  private void refreshFromMode() {
    SpySnapshot snapshot =
        switch (inspectMode) {
          case LOOKED_AT -> snapshotLookedAt();
          case BLOCK -> snapshotBlock();
          case ENTITY -> snapshotEntity();
        };
    Core.postOnUiThread(() -> applySnapshot(snapshot));
  }

  private SpySnapshot snapshotLookedAt() {
    var snapshot = inspectTargetedEntity(8.0);
    if (snapshot == null) {
      snapshot = inspectTargetedBlock(8.0);
    }
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "Nothing selected",
              "Aim at a block or entity, then press one of the inspect buttons.",
              "No current crosshair target was available.",
              "");
    }
    return snapshot;
  }

  private SpySnapshot snapshotBlock() {
    var snapshot = inspectTargetedBlock(8.0);
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "No block selected",
              "Aim directly at a block to inspect it.",
              "WindowSpy could not find a block under your crosshair.",
              "");
    }
    return snapshot;
  }

  private SpySnapshot snapshotEntity() {
    var snapshot = inspectTargetedEntity(8.0);
    if (snapshot == null) {
      snapshot =
          new SpySnapshot(
              "No entity selected",
              "Aim directly at an entity to inspect it.",
              "WindowSpy could not find an entity under your crosshair.",
              "");
    }
    return snapshot;
  }

  private void requestRefresh() {
    refreshRequested = true;
  }

  private boolean isMoveModifierDown() {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || minecraft.getWindow() == null) {
      return false;
    }
    long window = minecraft.getWindow().handle();
    return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
            == org.lwjgl.glfw.GLFW.GLFW_PRESS
        || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT)
            == org.lwjgl.glfw.GLFW.GLFW_PRESS;
  }

  public static void onClientTick(Minecraft minecraft) {
    if (activeInstance == null || minecraft == null || minecraft.screen == null) {
      return;
    }
    if (!"WindowSpy".equals(minecraft.screen.getTitle().getString())) {
      return;
    }
    activeInstance.tickCounter++;
    if (activeInstance.refreshRequested || activeInstance.tickCounter % 4 == 0) {
      activeInstance.refreshRequested = false;
      activeInstance.refreshFromMode();
    }
  }

  private TextView createChip(Context context, String label) {
    var chip = new TextView(context);
    chip.setText(label);
    chip.setTextSize(11);
    chip.setTextStyle(Typeface.BOLD);
    chip.setTextColor(TEXT_PRIMARY);
    chip.setGravity(Gravity.CENTER);
    applySurface(chip, PILL_BG, PANEL_STROKE_SOFT, 14, 0);
    chip.setMinimumHeight(chip.dp(34));
    chip.setMinimumWidth(chip.dp(88));
    addHoverTint(chip, PILL_BG, PILL_HOVER_BG, PANEL_STROKE_SOFT, 14, 0);
    return chip;
  }

  private Button createActionButton(Context context, String text, boolean primary) {
    var button = new Button(context, null, R.attr.buttonOutlinedStyle);
    button.setText(text);
    button.setTextColor(TEXT_PRIMARY);
    button.setTextStyle(Typeface.BOLD);
    applySurface(
        button,
        primary ? BUTTON_PRIMARY_BG : BUTTON_BG,
        primary ? 0xFF9A7BFF : PANEL_STROKE_SOFT,
        18,
        0);
    button.setMinimumHeight(button.dp(44));
    addHoverTint(
        button,
        primary ? BUTTON_PRIMARY_BG : BUTTON_BG,
        primary ? BUTTON_PRIMARY_HOVER_BG : BUTTON_HOVER_BG,
        primary ? 0xFF9A7BFF : PANEL_STROKE_SOFT,
        18,
        0);
    return button;
  }

  private void applySurface(View view, int fillColor, int strokeColor, int radiusDp, int paddingDp) {
    var drawable = new ShapeDrawable();
    drawable.setCornerRadius(view.dp(radiusDp));
    drawable.setColor(fillColor);
    if (strokeColor != 0) {
      drawable.setStroke(view.dp(1), strokeColor);
    }
    view.setBackground(drawable);
    view.setPadding(view.dp(paddingDp), view.dp(paddingDp), view.dp(paddingDp), view.dp(paddingDp));
  }

  private void addHoverTint(
      View view, int baseFill, int hoverFill, int strokeColor, int radiusDp, int paddingDp) {
    view.setOnHoverListener(
        (v, event) -> {
          if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
            applySurface(v, hoverFill, strokeColor, radiusDp, paddingDp);
          } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
            applySurface(v, baseFill, strokeColor, radiusDp, paddingDp);
          }
          return false;
        });
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String safe(@Nullable String value) {
    return value == null || value.isBlank() ? "Unknown" : value;
  }

  private record SpySnapshot(String title, String meta, String details, String rawNbt) {}

  private enum InspectMode {
    LOOKED_AT,
    BLOCK,
    ENTITY
  }

  public static final class WindowSpyScreenConfig implements ScreenCallback {
    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public boolean hasDefaultBackground() {
      return false;
    }

    @Override
    public boolean shouldBlurBackground() {
      return false;
    }
  }
}
