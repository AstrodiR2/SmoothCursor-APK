package com.termux.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * SmoothCursor rendering integrated into Termux itself. This class intentionally has no
 * dependency on Xposed, LSPosed, root, or another installed application.
 */
public final class SmoothCursorEffects {

    public static final String PREFS_NAME = "smooth_cursor";
    public static final String K_USE_THEME = "use_theme";
    public static final String K_COLOR = "color";
    public static final String K_OUTLINE = "outline";
    public static final String K_TRAIL = "trail";
    public static final String K_SMOOTH_SCROLL = "smooth_scroll";
    public static final String K_SCROLL_SPEED = "scroll_speed";
    public static final String K_PAD_ON = "pad_on";
    public static final String K_PAD_X = "pad_x";
    public static final String K_PAD_Y = "pad_y";
    public static final String K_GLOW_ON = "glow_on";
    public static final String K_GLOW_RADIUS = "glow_radius";
    public static final String K_GLOW_INTENSITY = "glow_intensity";
    public static final String K_GLOW_SPLIT = "glow_split";
    public static final String K_GLOW_TEXT_ON = "glow_text_on";
    public static final String K_GLOW_TEXT_RADIUS = "glow_text_radius";
    public static final String K_GLOW_TEXT_INTENSITY = "glow_text_intensity";
    public static final String K_GLOW_CURSOR_ON = "glow_cursor_on";
    public static final String K_GLOW_CURSOR_RADIUS = "glow_cursor_radius";
    public static final String K_GLOW_CURSOR_INTENSITY = "glow_cursor_intensity";
    public static final String K_FADE_ON = "fade_on";
    public static final String K_FADE_MS = "fade_ms";
    public static final String K_CURSOR_SHAPE = "cursor_shape";
    public static final String K_ROUNDED_RADIUS = "rounded_radius";
    public static final String K_BREATHING_ON = "breathing_on";
    public static final String K_BREATHING_SPEED = "breathing_speed";
    public static final String K_BREATHING_DEPTH = "breathing_depth";
    public static final String K_GRADIENT_ON = "gradient_on";
    public static final String K_RAINBOW_ON = "rainbow_on";
    public static final String K_GRADIENT_COLOR_START = "gradient_color_start";
    public static final String K_GRADIENT_COLOR_END = "gradient_color_end";
    public static final String K_COLOR_SPEED = "cursor_color_speed";
    public static final String K_IMAGE_PATH = "image_cursor_path";
    public static final String K_IMAGE_SCALE = "image_cursor_scale";
    public static final String K_IMAGE_OFFSET_X = "image_cursor_offset_x";
    public static final String K_IMAGE_OFFSET_Y = "image_cursor_offset_y";
    public static final String K_IMAGE_TRAIL_COLOR = "image_cursor_trail_color";
    public static final String K_LINE_HIGHLIGHT_ON = "current_line_highlight_on";
    public static final String K_LINE_HIGHLIGHT_COLOR = "current_line_highlight_color";
    public static final String K_LINE_HIGHLIGHT_ALPHA = "current_line_highlight_alpha";
    public static final String K_LINE_HIGHLIGHT_SPEED = "current_line_highlight_speed";

    public static final int CURSOR_SHAPE_FOLLOW_TERMINAL = 0;
    public static final int CURSOR_SHAPE_BLOCK = 1;
    public static final int CURSOR_SHAPE_LINE = 2;
    public static final int CURSOR_SHAPE_UNDERLINE = 3;
    public static final int CURSOR_SHAPE_ROUNDED_BLOCK = 4;
    public static final int CURSOR_SHAPE_PILL = 5;

    public static final String FEATURE_CUSTOM_COLOR = "custom_cursor_color";
    public static final String FEATURE_IMAGE_CURSOR = "image_cursor";
    public static final String FEATURE_ERROR_FLASH = "error_flash";
    public static final String FEATURE_GRADIENT = K_GRADIENT_ON;
    public static final String FEATURE_RAINBOW = K_RAINBOW_ON;

    // Keep every known incompatibility in one place so later cursor effects can join the same system.
    private static final String[][] INCOMPATIBILITY_TABLE = {
        {FEATURE_IMAGE_CURSOR, FEATURE_CUSTOM_COLOR},
        {FEATURE_IMAGE_CURSOR, FEATURE_GRADIENT},
        {FEATURE_IMAGE_CURSOR, FEATURE_RAINBOW},
        {FEATURE_IMAGE_CURSOR, FEATURE_ERROR_FLASH},
        {FEATURE_GRADIENT, FEATURE_RAINBOW}
    };

    private static final Paint cursorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint cursorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint cursorGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint cursorGlowInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint lineHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path cursorPath = new Path();
    private static final Path trailPath = new Path();
    private static WeakReference<TerminalView> viewRef = new WeakReference<>(null);
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private static float density = 1f;
    private static float glowBlurRadius = -1f;
    private static BlurMaskFilter glowOuterBlur;
    private static BlurMaskFilter glowInnerBlur;
    private static Bitmap cursorImage;
    private static String cursorImagePath;

    private static volatile boolean useTheme = true;
    private static volatile int color = 0xFFFFFFFF;
    private static volatile int outline = 0;
    private static volatile int trail = 50;
    private static volatile boolean smoothScroll = true;
    private static volatile int scrollSpeed = 50;
    private static volatile boolean paddingEnabled;
    private static volatile int paddingX = 8;
    private static volatile int paddingY = 8;
    private static volatile boolean glowTextEnabled;
    private static volatile int glowTextRadius = 6;
    private static volatile int glowTextIntensity = 70;
    private static volatile boolean glowCursorEnabled;
    private static volatile int glowCursorRadius = 6;
    private static volatile int glowCursorIntensity = 70;
    private static volatile boolean fadeEnabled = true;
    private static volatile int fadeMillis = 160;
    private static volatile int cursorShape = CURSOR_SHAPE_FOLLOW_TERMINAL;
    private static volatile int roundedRadius = 24;
    private static volatile boolean breathingEnabled;
    private static volatile int breathingSpeed = 50;
    private static volatile int breathingDepth = 60;
    private static volatile boolean gradientEnabled;
    private static volatile boolean rainbowEnabled;
    private static volatile int gradientColorStart = 0xFFFF5A7A;
    private static volatile int gradientColorEnd = 0xFF6C63FF;
    private static volatile int colorSpeed = 50;
    private static volatile boolean imageCursorEnabled;
    private static volatile float imageScale = 1f;
    private static volatile float imageOffsetX;
    private static volatile float imageOffsetY;
    private static volatile int imageTrailColor = Color.WHITE;
    private static volatile boolean lineHighlightEnabled;
    private static volatile int lineHighlightColor = 0xFF5A8DEE;
    private static volatile int lineHighlightAlpha = 24;
    private static volatile int lineHighlightSpeed = 55;

    private static final float[] cursorX = new float[4];
    private static final float[] cursorY = new float[4];
    private static final float[] cursorTau = {55f, 55f, 55f, 55f};
    private static final float[] CORNER_X = {-1f, 1f, 1f, -1f};
    private static final float[] CORNER_Y = {-1f, -1f, 1f, 1f};
    private static boolean cursorInitialized;
    private static boolean cursorMoving;
    private static int lastCursorCol;
    private static int lastCursorRow;
    private static int lastCursorStyle;
    private static int lastCursorTopRow;
    private static long lastCursorFrame;
    private static long colorEffectStartNs;
    private static long breathingCycleStartNs;
    private static long breathingIntroStartNs;
    private static boolean cursorEffectAnimating;
    private static long pulseUntilNs;
    private static long pulseStartNs;
    private static int lastPulseHash;
    private static boolean pulseHashInitialized;
    private static final float[] previousCursorX = new float[4];
    private static final float[] previousCursorY = new float[4];
    private static final float[][] trailX = new float[5][4];
    private static final float[][] trailY = new float[5][4];
    private static final long[] trailTimes = new long[5];
    private static int trailCount;
    private static float highlightRow;
    private static float targetHighlightRow;
    private static boolean highlightInitialized;
    private static long lastHighlightFrame;

    private static float scrollOffset;
    private static int lastTopRow;
    private static boolean scrollInitialized;
    private static long lastScrollFrame;

    private static char[][] previousChars;
    private static char[][] currentChars;
    private static int[] previousHashes;
    private static int[] currentHashes;
    private static long[][] bornAt;
    private static long[][] nextBornAt;
    private static int fadeRows;
    private static int fadeColumns;
    private static long fadeUntil;
    private static long frameNow;
    private static boolean fadeAnimating;

    static {
        cursorStroke.setStyle(Paint.Style.STROKE);
        cursorStroke.setStrokeJoin(Paint.Join.ROUND);
    }

    private SmoothCursorEffects() { }

    public static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void attachView(TerminalView view) {
        viewRef = new WeakReference<>(view);
        if (preferences == null) {
            Context context = view.getContext().getApplicationContext();
            preferences = getPreferences(context);
            density = context.getResources().getDisplayMetrics().density;
            colorEffectStartNs = System.nanoTime();
            breathingCycleStartNs = colorEffectStartNs;
            preferenceListener = (prefs, key) -> {
                reload(prefs);
                TerminalView attached = viewRef.get();
                if (attached != null) {
                    attached.post(() -> {
                        attached.updateSize();
                        attached.invalidate();
                    });
                }
            };
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
            reload(preferences);
            view.post(() -> {
                view.updateSize();
                view.invalidate();
            });
        }
    }

    private static void reload(SharedPreferences prefs) {
        boolean wasBreathingEnabled = breathingEnabled;
        boolean wasGradientEnabled = gradientEnabled;
        boolean wasRainbowEnabled = rainbowEnabled;
        useTheme = prefs.getBoolean(K_USE_THEME, true);
        color = prefs.getInt(K_COLOR, 0xFFFFFFFF);
        outline = clamp(prefs.getInt(K_OUTLINE, 0), 0, 100);
        trail = clamp(prefs.getInt(K_TRAIL, 50), 0, 100);
        smoothScroll = prefs.getBoolean(K_SMOOTH_SCROLL, true);
        scrollSpeed = clamp(prefs.getInt(K_SCROLL_SPEED, 50), 0, 100);
        paddingEnabled = prefs.getBoolean(K_PAD_ON, false);
        paddingX = clamp(prefs.getInt(K_PAD_X, 8), 0, 64);
        paddingY = clamp(prefs.getInt(K_PAD_Y, 8), 0, 64);
        boolean glowOn = prefs.getBoolean(K_GLOW_ON, false);
        int sharedRadius = clamp(prefs.getInt(K_GLOW_RADIUS, 6), 1, 30);
        int sharedIntensity = clamp(prefs.getInt(K_GLOW_INTENSITY, 70), 0, 100);
        if (prefs.getBoolean(K_GLOW_SPLIT, false)) {
            glowTextEnabled = prefs.getBoolean(K_GLOW_TEXT_ON, glowOn);
            glowTextRadius = clamp(prefs.getInt(K_GLOW_TEXT_RADIUS, sharedRadius), 1, 30);
            glowTextIntensity = clamp(prefs.getInt(K_GLOW_TEXT_INTENSITY, sharedIntensity), 0, 100);
            glowCursorEnabled = prefs.getBoolean(K_GLOW_CURSOR_ON, glowOn);
            glowCursorRadius = clamp(prefs.getInt(K_GLOW_CURSOR_RADIUS, sharedRadius), 1, 30);
            glowCursorIntensity = clamp(prefs.getInt(K_GLOW_CURSOR_INTENSITY, sharedIntensity), 0, 100);
        } else {
            glowTextEnabled = glowOn;
            glowCursorEnabled = glowOn;
            glowTextRadius = glowCursorRadius = sharedRadius;
            glowTextIntensity = glowCursorIntensity = sharedIntensity;
        }
        fadeEnabled = prefs.getBoolean(K_FADE_ON, true);
        fadeMillis = clamp(prefs.getInt(K_FADE_MS, 160), 50, 1000);
        cursorShape = clamp(prefs.getInt(K_CURSOR_SHAPE, CURSOR_SHAPE_FOLLOW_TERMINAL),
            CURSOR_SHAPE_FOLLOW_TERMINAL, CURSOR_SHAPE_PILL);
        roundedRadius = clamp(prefs.getInt(K_ROUNDED_RADIUS, 24), 0, 50);
        breathingEnabled = prefs.getBoolean(K_BREATHING_ON, false);
        breathingSpeed = clamp(prefs.getInt(K_BREATHING_SPEED, 50), 1, 100);
        breathingDepth = clamp(prefs.getInt(K_BREATHING_DEPTH, 60), 0, 100);
        gradientEnabled = prefs.getBoolean(K_GRADIENT_ON, false);
        rainbowEnabled = prefs.getBoolean(K_RAINBOW_ON, false);
        gradientColorStart = prefs.getInt(K_GRADIENT_COLOR_START, 0xFFFF5A7A);
        gradientColorEnd = prefs.getInt(K_GRADIENT_COLOR_END, 0xFF6C63FF);
        colorSpeed = clamp(prefs.getInt(K_COLOR_SPEED, 50), 1, 100);
        imageCursorEnabled = prefs.getBoolean(FEATURE_IMAGE_CURSOR, false);
        imageScale = Math.max(1f, Math.min(5f, prefs.getFloat(K_IMAGE_SCALE, 1f)));
        imageOffsetX = prefs.getFloat(K_IMAGE_OFFSET_X, 0f);
        imageOffsetY = prefs.getFloat(K_IMAGE_OFFSET_Y, 0f);
        imageTrailColor = prefs.getInt(K_IMAGE_TRAIL_COLOR, Color.WHITE);
        lineHighlightEnabled = prefs.getBoolean(K_LINE_HIGHLIGHT_ON, false);
        lineHighlightColor = prefs.getInt(K_LINE_HIGHLIGHT_COLOR, 0xFF5A8DEE);
        lineHighlightAlpha = clamp(prefs.getInt(K_LINE_HIGHLIGHT_ALPHA, 24), 0, 100);
        lineHighlightSpeed = clamp(prefs.getInt(K_LINE_HIGHLIGHT_SPEED, 55), 1, 100);
        String imagePath = prefs.getString(K_IMAGE_PATH, null);
        if (imagePath == null || !imagePath.equals(cursorImagePath)) {
            cursorImagePath = imagePath;
            cursorImage = null;
            if (imagePath != null) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    cursorImage = BitmapFactory.decodeFile(imagePath, options);
                } catch (OutOfMemoryError | RuntimeException ignored) {
                    cursorImage = null;
                }
            }
        }
        long now = System.nanoTime();
        if (breathingEnabled && !wasBreathingEnabled) {
            breathingIntroStartNs = now;
            breathingCycleStartNs = now;
        }
        if ((gradientEnabled && !wasGradientEnabled) || (rainbowEnabled && !wasRainbowEnabled))
            colorEffectStartNs = now;
    }

    /**
     * Turn one cursor feature on or off, automatically disabling any active incompatible features.
     * The returned feature keys are the ones that were disabled and can be used to show a localized
     * explanation to the user.
     */
    public static List<String> setFeatureEnabled(SharedPreferences prefs, String feature, boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit();
        List<String> disabled = new ArrayList<>();
        if (enabled) {
            for (String[] pair : INCOMPATIBILITY_TABLE) {
                String other = pair[0].equals(feature) ? pair[1] : pair[1].equals(feature) ? pair[0] : null;
                if (other != null && isFeatureEnabled(prefs, other)) {
                    setFeatureValue(editor, other, false);
                    disabled.add(other);
                }
            }
        }
        setFeatureValue(editor, feature, enabled);
        editor.apply();
        return disabled;
    }

    private static boolean isFeatureEnabled(SharedPreferences prefs, String feature) {
        if (FEATURE_CUSTOM_COLOR.equals(feature)) return !prefs.getBoolean(K_USE_THEME, true);
        return prefs.getBoolean(feature, false);
    }

    private static void setFeatureValue(SharedPreferences.Editor editor, String feature, boolean enabled) {
        if (FEATURE_CUSTOM_COLOR.equals(feature)) editor.putBoolean(K_USE_THEME, !enabled);
        else editor.putBoolean(feature, enabled);
    }

    public static int paddingXPx() {
        return paddingEnabled ? Math.round(paddingX * density) : 0;
    }

    public static int paddingYPx() {
        return paddingEnabled ? Math.round(paddingY * density) : 0;
    }

    static int beginSmoothScroll(Canvas canvas, int topRow, int cellHeight) {
        long now = System.nanoTime();
        if (!smoothScroll) {
            scrollOffset = 0f;
            lastTopRow = topRow;
            lastScrollFrame = now;
            scrollInitialized = true;
            return -1;
        }
        if (scrollInitialized) {
            float dtMs = Math.min((now - lastScrollFrame) / 1_000_000f, 50f);
            float tau = 30f + 120f * scrollSpeed / 100f;
            scrollOffset *= (float) Math.exp(-dtMs / tau);
            int delta = topRow - lastTopRow;
            if (delta != 0) {
                scrollOffset = Math.max(-cellHeight,
                    Math.min(cellHeight, scrollOffset + delta * (float) cellHeight));
            }
        }
        scrollInitialized = true;
        lastTopRow = topRow;
        lastScrollFrame = now;
        if (Math.abs(scrollOffset) < 0.5f) {
            scrollOffset = 0f;
            return -1;
        }
        int save = canvas.save();
        canvas.translate(0f, scrollOffset);
        requestNextFrame();
        return save;
    }

    static void endSmoothScroll(Canvas canvas, int saveCount) {
        if (saveCount >= 0) canvas.restoreToCount(saveCount);
    }

    static void beginTextFrame(TerminalEmulator emulator, int topRow) {
        fadeAnimating = false;
        if (emulator == null) return;
        detectPulseActivity(emulator, topRow);
        if (!fadeEnabled) return;
        TerminalBuffer screen = emulator.getScreen();
        int rows = emulator.mRows;
        int columns = emulator.mColumns;
        frameNow = System.nanoTime();
        boolean reset = previousChars == null || rows != fadeRows || columns != fadeColumns;
        if (reset) {
            previousChars = new char[rows][columns];
            currentChars = new char[rows][columns];
            previousHashes = new int[rows];
            currentHashes = new int[rows];
            bornAt = new long[rows][columns];
            nextBornAt = new long[rows][columns];
        }

        boolean changed = reset;
        for (int row = 0; row < rows; row++) {
            TerminalRow line = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(topRow + row));
            char[] source = line.mText;
            int used = Math.min(Math.min(line.getSpaceUsed(), source.length), columns);
            int hash = 0;
            for (int col = 0; col < columns; col++) {
                char value = col < used ? source[col] : ' ';
                currentChars[row][col] = value;
                hash = 31 * hash + value;
            }
            currentHashes[row] = hash;
            if (reset) {
                System.arraycopy(currentChars[row], 0, previousChars[row], 0, columns);
                previousHashes[row] = hash;
            } else if (hash != previousHashes[row]) {
                changed = true;
            }
        }
        if (reset) {
            fadeRows = rows;
            fadeColumns = columns;
            fadeUntil = 0L;
            return;
        }
        if (changed) {
            int blankHash = 0;
            for (int col = 0; col < columns; col++) blankHash = 31 * blankHash + ' ';
            int bestShift = 0;
            int bestScore = -1;
            for (int distance = 0; distance < rows; distance++) {
                for (int sign = 0; sign < (distance == 0 ? 1 : 2); sign++) {
                    int shift = sign == 0 ? distance : -distance;
                    int score = 0;
                    for (int row = 0; row < rows; row++) {
                        int oldRow = row + shift;
                        if (oldRow >= 0 && oldRow < rows && currentHashes[row] == previousHashes[oldRow]
                            && currentHashes[row] != blankHash) score++;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestShift = shift;
                    }
                }
            }
            int fresh = 0;
            for (int row = 0; row < rows; row++) {
                int oldRow = row + bestShift;
                boolean hasOldRow = oldRow >= 0 && oldRow < rows;
                for (int col = 0; col < columns; col++) {
                    char value = currentChars[row][col];
                    char oldValue = hasOldRow ? previousChars[oldRow][col] : ' ';
                    if (hasOldRow && oldValue == value) {
                        nextBornAt[row][col] = bornAt[oldRow][col];
                    } else if (value == ' ' || oldValue != ' ') {
                        nextBornAt[row][col] = 0L;
                    } else {
                        nextBornAt[row][col] = frameNow;
                        fresh++;
                    }
                }
            }
            if (fresh > rows * columns / 3) {
                for (int row = 0; row < rows; row++)
                    for (int col = 0; col < columns; col++)
                        if (nextBornAt[row][col] == frameNow) nextBornAt[row][col] = 0L;
                fresh = 0;
            }
            if (fresh > 0) fadeUntil = frameNow + fadeMillis * 1_000_000L;
            long[][] oldBorn = bornAt;
            bornAt = nextBornAt;
            nextBornAt = oldBorn;
            char[][] oldChars = previousChars;
            previousChars = currentChars;
            currentChars = oldChars;
            int[] oldHashes = previousHashes;
            previousHashes = currentHashes;
            currentHashes = oldHashes;
        }
        fadeAnimating = frameNow < fadeUntil;
    }

    private static void detectPulseActivity(TerminalEmulator emulator, int topRow) {
        int hash = 1;
        for (int row = 0; row < emulator.mRows; row++) {
            TerminalRow line = emulator.getScreen().allocateFullLineIfNecessary(
                emulator.getScreen().externalToInternalRow(topRow + row));
            char[] text = line.mText;
            int used = Math.min(line.getSpaceUsed(), Math.min(text.length, emulator.mColumns));
            for (int col = 0; col < used; col++) hash = 31 * hash + text[col];
            hash = 31 * hash + 0x7f;
        }
        if (pulseHashInitialized && hash != lastPulseHash) {
            pulseStartNs = System.nanoTime();
            pulseUntilNs = pulseStartNs + 750_000_000L;
        }
        pulseHashInitialized = true;
        lastPulseHash = hash;
    }

    static boolean drawTextWithFade(Canvas canvas, char[] text, int row, int startColumn, int startIndex,
                                    int columns, int chars, float measuredWidth, float cellWidth,
                                    float x, float baseline, Paint paint) {
        if (!fadeEnabled || !fadeAnimating || bornAt == null || row < 0 || row >= fadeRows
            || columns != chars || columns <= 0 || startColumn < 0 || startColumn + columns > fadeColumns
            || Math.abs(measuredWidth / cellWidth - columns) > 0.01f) {
            canvas.drawTextRun(text, startIndex, chars, startIndex, chars, x, baseline, false, paint);
            return false;
        }

        long fadeNs = Math.max(1, fadeMillis) * 1_000_000L;
        int originalAlpha = paint.getAlpha();
        int i = 0;
        while (i < columns) {
            int level = fadeLevel(bornAt[row][startColumn + i], frameNow, fadeNs);
            int j = i + 1;
            while (j < columns && fadeLevel(bornAt[row][startColumn + j], frameNow, fadeNs) == level) j++;
            if (level > 0) {
                paint.setAlpha(originalAlpha * level / 16);
                int width = j - i;
                canvas.drawTextRun(text, startIndex + i, width, startIndex + i, width,
                    x + i * cellWidth, baseline, false, paint);
            }
            i = j;
        }
        paint.setAlpha(originalAlpha);
        if (frameNow < fadeUntil) requestNextFrame();
        return true;
    }

    private static int fadeLevel(long born, long now, long durationNs) {
        if (born == 0L) return 16;
        float t = (now - born) / (float) durationNs;
        if (t >= 1f) return 16;
        if (t < 0f) t = 0f;
        float eased = t * t * (3f - 2f * t);
        return (int) (eased * 16f);
    }

    static void drawCursor(TerminalEmulator emulator, Canvas canvas, int topRow,
                           float cellWidth, int cellHeight, int baselineOffset, boolean visible) {
        cursorEffectAnimating = false;
        if (emulator == null || (!visible && !breathingEnabled && !imageCursorEnabled
            && System.nanoTime() >= pulseUntilNs)) return;
        int col = emulator.getCursorCol();
        int row = emulator.getCursorRow() - topRow;
        float left = col;
        float right = col + 1f;
        float top = row;
        float bottom = row + 1f;
        int terminalStyle = emulator.getCursorStyle();
        int styleKey;
        if (cursorShape == CURSOR_SHAPE_FOLLOW_TERMINAL) {
            styleKey = terminalStyle;
            if (terminalStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) top = row + 0.75f;
            else if (terminalStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right = col + 0.25f;
        } else {
            styleKey = 100 + cursorShape;
            switch (cursorShape) {
                case CURSOR_SHAPE_LINE:
                    right = col + 0.12f;
                    break;
                case CURSOR_SHAPE_UNDERLINE:
                    top = row + 0.86f;
                    break;
                case CURSOR_SHAPE_PILL:
                    left = col + 0.35f;
                    right = col + 0.65f;
                    break;
                default:
                    break;
            }
        }

        float[] targetX = {left, right, right, left};
        float[] targetY = {top, top, bottom, bottom};
        long now = System.nanoTime();
        float dtMs = 0f;
        if (!cursorInitialized || (smoothScroll && topRow != lastCursorTopRow)) {
            for (int i = 0; i < 4; i++) {
                cursorX[i] = targetX[i];
                cursorY[i] = targetY[i];
            }
            cursorInitialized = true;
            lastCursorCol = col;
            lastCursorRow = row;
            lastCursorStyle = styleKey;
        } else if (col != lastCursorCol || row != lastCursorRow || styleKey != lastCursorStyle) {
            retargetCursor(targetX, targetY, cellWidth, cellHeight);
            lastCursorCol = col;
            lastCursorRow = row;
            lastCursorStyle = styleKey;
            dtMs = cursorMoving ? Math.min((now - lastCursorFrame) / 1_000_000f, 50f) : 0f;
        } else {
            dtMs = Math.min((now - lastCursorFrame) / 1_000_000f, 50f);
        }
        lastCursorTopRow = topRow;
        lastCursorFrame = now;

        boolean moving = false;
        for (int i = 0; i < 4; i++) {
            previousCursorX[i] = cursorX[i];
            previousCursorY[i] = cursorY[i];
            float amount = 1f - (float) Math.exp(-dtMs / cursorTau[i]);
            cursorX[i] += (targetX[i] - cursorX[i]) * amount;
            cursorY[i] += (targetY[i] - cursorY[i]) * amount;
            if (Math.abs(targetX[i] - cursorX[i]) < 0.005f) cursorX[i] = targetX[i];
            else moving = true;
            if (Math.abs(targetY[i] - cursorY[i]) < 0.005f) cursorY[i] = targetY[i];
            else moving = true;
        }
        cursorMoving = moving;
        if (imageCursorEnabled && moving) {
            int index = trailCount < trailX.length ? trailCount : trailX.length - 1;
            System.arraycopy(previousCursorX, 0, trailX[index], 0, 4);
            System.arraycopy(previousCursorY, 0, trailY[index], 0, 4);
            if (trailCount < trailX.length) trailCount++;
            else {
                for (int i = 1; i < trailX.length; i++) {
                    System.arraycopy(trailX[i], 0, trailX[i - 1], 0, 4);
                    System.arraycopy(trailY[i], 0, trailY[i - 1], 0, 4);
                    trailTimes[i - 1] = trailTimes[i];
                }
            }
            trailTimes[trailCount - 1] = now;
        }

        cursorPath.reset();
        RectF cursorRect = new RectF();
        for (int i = 0; i < 4; i++) {
            float px = cursorX[i] * cellWidth;
            float py = baselineOffset + cursorY[i] * cellHeight;
            if (i == 0) {
                cursorRect.left = px;
                cursorRect.top = py;
            } else if (i == 1) {
                cursorRect.right = px;
            } else if (i == 2) {
                cursorRect.bottom = py;
            }
        }
        if (cursorShape == CURSOR_SHAPE_ROUNDED_BLOCK || cursorShape == CURSOR_SHAPE_PILL) {
            float radius;
            if (cursorShape == CURSOR_SHAPE_PILL) {
                radius = cursorRect.width() / 2f;
            } else {
                radius = Math.min(cellWidth, cellHeight) * roundedRadius / 100f;
            }
            if (radius > 0f) {
                cursorPath.addRoundRect(cursorRect, radius, radius, Path.Direction.CW);
            } else {
                cursorPath.addRect(cursorRect, Path.Direction.CW);
            }
        } else {
            cursorPath.moveTo(cursorRect.left, cursorRect.top);
            cursorPath.lineTo(cursorRect.right, cursorRect.top);
            cursorPath.lineTo(cursorRect.right, cursorRect.bottom);
            cursorPath.lineTo(cursorRect.left, cursorRect.bottom);
            cursorPath.close();
        }
        float alphaFactor = getCursorAlphaFactor(now);

        if (!imageCursorEnabled && glowCursorEnabled && glowCursorIntensity > 0) {
            int cursorColor = getCursorColor(emulator, now) | 0xFF000000;
            float radius = Math.max(1f, glowCursorRadius * density);
            if (glowBlurRadius != radius) {
                glowBlurRadius = radius;
                glowOuterBlur = new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL);
                glowInnerBlur = new BlurMaskFilter(Math.max(1f, radius * 0.4f), BlurMaskFilter.Blur.NORMAL);
            }
            cursorGlow.setMaskFilter(glowOuterBlur);
            cursorGlow.setColor(cursorColor);
            cursorGlow.setAlpha(Math.round(glowCursorIntensity * 255f / 100f * alphaFactor));
            canvas.drawPath(cursorPath, cursorGlow);
            cursorGlowInner.setMaskFilter(glowInnerBlur);
            cursorGlowInner.setColor(cursorColor);
            cursorGlowInner.setAlpha(Math.round(glowCursorIntensity * 255f / 100f * alphaFactor));
            canvas.drawPath(cursorPath, cursorGlowInner);
        }
        boolean trailAnimating = false;
        if (imageCursorEnabled && cursorImage != null) {
            trailAnimating = drawImageTrail(canvas, now, cellWidth, cellHeight, baselineOffset);
            drawCursorImage(canvas, cellWidth, cellHeight, baselineOffset, alphaFactor);
        } else {
            int cursorColor = getCursorColor(emulator, now) | 0xFF000000;
            cursorFill.setColor(cursorColor);
            cursorFill.setAlpha(Math.round(200f * alphaFactor));
            canvas.drawPath(cursorPath, cursorFill);
        }
        if (outline > 0 && (!imageCursorEnabled || cursorImage == null)) {
            int cursorColor = getCursorColor(emulator, now) | 0xFF000000;
            cursorStroke.setColor(cursorColor);
            cursorStroke.setAlpha(Math.round(255f * alphaFactor));
            cursorStroke.setStrokeWidth(Math.max(1f, cellHeight * 0.12f * outline / 100f));
            canvas.drawPath(cursorPath, cursorStroke);
        }
        if (outline > 0 && imageCursorEnabled && cursorImage != null) {
            cursorStroke.setColor(imageTrailColor);
            cursorStroke.setAlpha(Math.round(255f * alphaFactor));
            cursorStroke.setStrokeWidth(Math.max(1f, cellHeight * 0.12f * outline / 100f));
            canvas.drawPath(cursorPath, cursorStroke);
        }
        cursorEffectAnimating = breathingEnabled || now < pulseUntilNs || trailAnimating
            || (!imageCursorEnabled && (gradientEnabled || rainbowEnabled));
        if (moving || cursorEffectAnimating) requestNextFrame();
    }

    private static void drawCursorImage(Canvas canvas, float cellWidth, int cellHeight,
                                        int baselineOffset, float alphaFactor) {
        if (cursorImage == null) return;
        // Fit the picture to the whole character cell, then clip to the selected figure. This
        // keeps the crop editor consistent for narrow forms such as line, underline, and pill.
        float cellLeft = cursorX[0];
        if (cursorShape == CURSOR_SHAPE_PILL)
            cellLeft = (cursorX[0] + cursorX[1]) / 2f - 0.5f;
        float cellTop = cursorY[0];
        if (cursorShape == CURSOR_SHAPE_UNDERLINE) cellTop -= 0.86f;
        else if (cursorShape == CURSOR_SHAPE_FOLLOW_TERMINAL
            && lastCursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cellTop -= 0.75f;
        RectF bounds = new RectF(cellLeft * cellWidth, baselineOffset + cellTop * cellHeight,
            (cellLeft + 1f) * cellWidth, baselineOffset + (cellTop + 1f) * cellHeight);
        float baseScale = Math.max(bounds.width() / cursorImage.getWidth(),
            bounds.height() / cursorImage.getHeight());
        float scale = baseScale * imageScale;
        float width = cursorImage.getWidth() * scale;
        float height = cursorImage.getHeight() * scale;
        float dx = imageOffsetX * bounds.width();
        float dy = imageOffsetY * bounds.height();
        RectF destination = new RectF(bounds.centerX() - width / 2f + dx,
            bounds.centerY() - height / 2f + dy,
            bounds.centerX() + width / 2f + dx,
            bounds.centerY() + height / 2f + dy);
        int save = canvas.save();
        canvas.clipPath(cursorPath);
        imagePaint.setAlpha(Math.round(255f * alphaFactor));
        canvas.drawBitmap(cursorImage, null, destination, imagePaint);
        imagePaint.setAlpha(255);
        canvas.restoreToCount(save);
    }

    private static boolean drawImageTrail(Canvas canvas, long now, float cellWidth, int cellHeight,
                                          int baselineOffset) {
        if (trail <= 0) {
            trailCount = 0;
            return false;
        }
        int kept = 0;
        boolean animating = false;
        for (int i = 0; i < trailCount; i++) {
            float age = (now - trailTimes[i]) / 220_000_000f;
            if (age >= 1f) continue;
            animating = true;
            float alpha = (1f - age) * (trail / 100f) * 0.35f;
            trailPath.reset();
            addCursorPath(trailPath, trailX[i], trailY[i], cellWidth, cellHeight, baselineOffset);
            trailPaint.setColor(imageTrailColor);
            trailPaint.setAlpha(Math.round(alpha * 255f));
            canvas.drawPath(trailPath, trailPaint);
            if (kept != i) {
                System.arraycopy(trailX[i], 0, trailX[kept], 0, 4);
                System.arraycopy(trailY[i], 0, trailY[kept], 0, 4);
                trailTimes[kept] = trailTimes[i];
            }
            kept++;
        }
        trailCount = kept;
        return animating;
    }

    private static void addCursorPath(Path path, float[] xs, float[] ys, float cellWidth,
                                      int cellHeight, int baselineOffset) {
        float l = Math.min(xs[0], xs[3]) * cellWidth;
        float r = Math.max(xs[1], xs[2]) * cellWidth;
        float t = baselineOffset + Math.min(ys[0], ys[1]) * cellHeight;
        float b = baselineOffset + Math.max(ys[2], ys[3]) * cellHeight;
        RectF rect = new RectF(l, t, r, b);
        if (cursorShape == CURSOR_SHAPE_ROUNDED_BLOCK || cursorShape == CURSOR_SHAPE_PILL) {
            float radius = cursorShape == CURSOR_SHAPE_PILL
                ? rect.width() / 2f : Math.min(cellWidth, cellHeight) * roundedRadius / 100f;
            if (radius > 0f) path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            else path.addRect(rect, Path.Direction.CW);
        } else {
            path.addRect(rect, Path.Direction.CW);
        }
    }

    /** Draw the fullscreen-app row highlight before terminal text is painted. */
    static boolean drawCurrentLineHighlight(TerminalEmulator emulator, Canvas canvas, int topRow,
                                            float fontWidth, int cellHeight, int baselineOffset,
                                            int columns) {
        if (!lineHighlightEnabled || emulator == null || !emulator.isAlternateBufferActive()) {
            highlightInitialized = false;
            return false;
        }
        targetHighlightRow = emulator.getCursorRow() - topRow;
        long now = System.nanoTime();
        if (!highlightInitialized) {
            highlightRow = targetHighlightRow;
            highlightInitialized = true;
        }
        float dt = lastHighlightFrame == 0L ? 0f : Math.min((now - lastHighlightFrame) / 1_000_000f, 50f);
        lastHighlightFrame = now;
        float tau = 300f - 270f * lineHighlightSpeed / 100f;
        float amount = 1f - (float) Math.exp(-dt / Math.max(20f, tau));
        highlightRow += (targetHighlightRow - highlightRow) * amount;
        boolean moving = Math.abs(targetHighlightRow - highlightRow) > 0.01f;
        if (!moving) highlightRow = targetHighlightRow;
        lineHighlightPaint.setColor(lineHighlightColor);
        lineHighlightPaint.setAlpha(Math.round(lineHighlightAlpha * 255f / 100f));
        float top = baselineOffset + highlightRow * cellHeight;
        canvas.drawRect(0f, top, columns * fontWidth, top + cellHeight, lineHighlightPaint);
        if (moving) requestNextFrame();
        return moving;
    }

    private static int getCursorColor(TerminalEmulator emulator, long now) {
        if (rainbowEnabled) {
            float[] hsv = {cyclePhase(now, colorSpeed, colorEffectStartNs) * 360f, 0.9f, 1f};
            return Color.HSVToColor(255, hsv);
        }
        if (gradientEnabled) {
            float phase = cyclePhase(now, colorSpeed, colorEffectStartNs);
            float amount = 0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2d);
            return interpolateColor(gradientColorStart, gradientColorEnd, amount);
        }
        return useTheme ? emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : color;
    }

    private static int interpolateColor(int from, int to, float amount) {
        int red = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
        int green = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
        int blue = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
        return Color.rgb(red, green, blue);
    }

    private static float getCursorAlphaFactor(long now) {
        if (now < pulseUntilNs) {
            float pulsePhase = ((now - pulseStartNs) % 520_000_000L) / 520_000_000f;
            float pulseWave = 0.5f - 0.5f * (float) Math.cos(pulsePhase * Math.PI * 2d);
            return 0.35f + pulseWave * 0.65f;
        }
        if (!breathingEnabled) return 1f;
        float phase = cyclePhase(now, breathingSpeed, breathingCycleStartNs);
        float wave = 0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2d);
        float depth = breathingDepth / 100f;
        float alpha = 1f - depth * 0.85f * (1f - wave);
        float intro = clamp((int) ((now - breathingIntroStartNs) / 1_000_000L), 0, 350) / 350f;
        intro = intro * intro * (3f - 2f * intro);
        return alpha * intro;
    }

    private static float cyclePhase(long now, int speed, long startNs) {
        float periodMs = 8000f - 7000f * speed / 100f;
        float elapsedMs = Math.max(0L, now - startNs) / 1_000_000f;
        float phase = (elapsedMs % periodMs) / periodMs;
        return phase;
    }

    private static void retargetCursor(float[] targetX, float[] targetY, float cellWidth, int cellHeight) {
        float oldX = 0f, oldY = 0f, newX = 0f, newY = 0f;
        for (int i = 0; i < 4; i++) {
            oldX += cursorX[i];
            oldY += cursorY[i];
            newX += targetX[i];
            newY += targetY[i];
        }
        float dx = (newX - oldX) / 4f * cellWidth;
        float dy = (newY - oldY) / 4f * cellHeight;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float strength = trail / 100f;
        float lead = 30f + 50f * strength;
        float tail = lead * (1.3f + 2.2f * strength);
        for (int i = 0; i < 4; i++) {
            float projection = distance > 1e-3f
                ? ((CORNER_X[i] * dx + CORNER_Y[i] * dy) / distance) * 0.70710678f
                : 0f;
            float amount = (1f - projection) / 2f;
            cursorTau[i] = lead + (tail - lead) * amount;
        }
    }

    static boolean shouldGlowText(int backColor, int effect, boolean reverseVideo, int cursor) {
        return glowTextEnabled && glowTextIntensity > 0 && cursor == 0 && !reverseVideo
            && (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE | TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE)) == 0
            && backColor == TextStyle.COLOR_INDEX_BACKGROUND;
    }

    static void setTextGlow(Paint paint, int color, float multiplier) {
        paint.setShadowLayer(Math.max(1f, glowTextRadius * density * multiplier), 0f, 0f,
            ((glowTextIntensity * 255 / 100) << 24) | (color & 0x00FFFFFF));
    }

    static void clearTextGlow(Paint paint) {
        paint.clearShadowLayer();
    }

    static void requestFrameIfNeeded() {
        if (fadeAnimating || cursorMoving || cursorEffectAnimating || Math.abs(scrollOffset) >= 0.5f)
            requestNextFrame();
    }

    private static void requestNextFrame() {
        TerminalView view = viewRef.get();
        if (view == null) return;
        view.postInvalidateOnAnimation();
    }

    /** Pick the highest refresh rate of the current display mode so the animations can run smoothly. */
    public static void applyPreferredRefreshRate(android.app.Activity activity) {
        try {
            if (android.os.Build.VERSION.SDK_INT < 23) return;
            android.view.Display display = activity.getWindowManager().getDefaultDisplay();
            android.view.Display.Mode current = display.getMode();
            android.view.Display.Mode best = current;
            for (android.view.Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                    && mode.getPhysicalHeight() == current.getPhysicalHeight()
                    && mode.getRefreshRate() > best.getRefreshRate()) {
                    best = mode;
                }
            }
            android.view.WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.preferredDisplayModeId = best.getModeId();
            params.preferredRefreshRate = best.getRefreshRate();
            activity.getWindow().setAttributes(params);
        } catch (Throwable ignored) { }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}