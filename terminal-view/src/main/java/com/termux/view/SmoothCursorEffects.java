package com.termux.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;

import java.lang.ref.WeakReference;

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
    public static final String K_FADE_ON = "fade_on";
    public static final String K_FADE_MS = "fade_ms";

    private static final Paint cursorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint cursorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint cursorGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint cursorGlowInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path cursorPath = new Path();
    private static WeakReference<TerminalView> viewRef = new WeakReference<>(null);
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private static float density = 1f;

    private static volatile boolean useTheme = true;
    private static volatile int color = 0xFFFFFFFF;
    private static volatile int outline = 0;
    private static volatile int trail = 50;
    private static volatile boolean smoothScroll = true;
    private static volatile int scrollSpeed = 50;
    private static volatile boolean paddingEnabled;
    private static volatile int paddingX = 8;
    private static volatile int paddingY = 8;
    private static volatile boolean glowEnabled;
    private static volatile int glowRadius = 6;
    private static volatile int glowIntensity = 70;
    private static volatile boolean fadeEnabled = true;
    private static volatile int fadeMillis = 160;

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
        useTheme = prefs.getBoolean(K_USE_THEME, true);
        color = prefs.getInt(K_COLOR, 0xFFFFFFFF);
        outline = clamp(prefs.getInt(K_OUTLINE, 0), 0, 100);
        trail = clamp(prefs.getInt(K_TRAIL, 50), 0, 100);
        smoothScroll = prefs.getBoolean(K_SMOOTH_SCROLL, true);
        scrollSpeed = clamp(prefs.getInt(K_SCROLL_SPEED, 50), 0, 100);
        paddingEnabled = prefs.getBoolean(K_PAD_ON, false);
        paddingX = clamp(prefs.getInt(K_PAD_X, 8), 0, 64);
        paddingY = clamp(prefs.getInt(K_PAD_Y, 8), 0, 64);
        glowEnabled = prefs.getBoolean(K_GLOW_ON, false);
        glowRadius = clamp(prefs.getInt(K_GLOW_RADIUS, 6), 1, 30);
        glowIntensity = clamp(prefs.getInt(K_GLOW_INTENSITY, 70), 0, 100);
        fadeEnabled = prefs.getBoolean(K_FADE_ON, true);
        fadeMillis = clamp(prefs.getInt(K_FADE_MS, 160), 50, 1000);
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
        if (!fadeEnabled || emulator == null) return;
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
        if (!visible || emulator == null) return;
        int col = emulator.getCursorCol();
        int row = emulator.getCursorRow() - topRow;
        int style = emulator.getCursorStyle();
        float left = col;
        float right = col + 1f;
        float top = row;
        float bottom = row + 1f;
        if (style == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) top = row + 0.75f;
        else if (style == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right = col + 0.25f;

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
            lastCursorStyle = style;
        } else if (col != lastCursorCol || row != lastCursorRow || style != lastCursorStyle) {
            retargetCursor(targetX, targetY, cellWidth, cellHeight);
            lastCursorCol = col;
            lastCursorRow = row;
            lastCursorStyle = style;
            dtMs = cursorMoving ? Math.min((now - lastCursorFrame) / 1_000_000f, 50f) : 0f;
        } else {
            dtMs = Math.min((now - lastCursorFrame) / 1_000_000f, 50f);
        }
        lastCursorTopRow = topRow;
        lastCursorFrame = now;

        boolean moving = false;
        for (int i = 0; i < 4; i++) {
            float amount = 1f - (float) Math.exp(-dtMs / cursorTau[i]);
            cursorX[i] += (targetX[i] - cursorX[i]) * amount;
            cursorY[i] += (targetY[i] - cursorY[i]) * amount;
            if (Math.abs(targetX[i] - cursorX[i]) < 0.005f) cursorX[i] = targetX[i];
            else moving = true;
            if (Math.abs(targetY[i] - cursorY[i]) < 0.005f) cursorY[i] = targetY[i];
            else moving = true;
        }
        cursorMoving = moving;

        cursorPath.reset();
        for (int i = 0; i < 4; i++) {
            float px = cursorX[i] * cellWidth;
            float py = baselineOffset + cursorY[i] * cellHeight;
            if (i == 0) cursorPath.moveTo(px, py);
            else cursorPath.lineTo(px, py);
        }
        cursorPath.close();
        int cursorColor = useTheme
            ? emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR]
            : color;
        cursorColor |= 0xFF000000;

        if (glowEnabled && glowIntensity > 0) {
            float radius = Math.max(1f, glowRadius * density);
            cursorGlow.setMaskFilter(new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL));
            cursorGlow.setColor(cursorColor);
            cursorGlow.setAlpha(glowIntensity * 255 / 100);
            canvas.drawPath(cursorPath, cursorGlow);
            cursorGlowInner.setMaskFilter(new BlurMaskFilter(Math.max(1f, radius * 0.4f), BlurMaskFilter.Blur.NORMAL));
            cursorGlowInner.setColor(cursorColor);
            cursorGlowInner.setAlpha(glowIntensity * 255 / 100);
            canvas.drawPath(cursorPath, cursorGlowInner);
        }
        cursorFill.setColor(cursorColor);
        cursorFill.setAlpha(200);
        canvas.drawPath(cursorPath, cursorFill);
        if (outline > 0) {
            cursorStroke.setColor(cursorColor);
            cursorStroke.setAlpha(255);
            cursorStroke.setStrokeWidth(Math.max(1f, cellHeight * 0.12f * outline / 100f));
            canvas.drawPath(cursorPath, cursorStroke);
        }
        if (moving) requestNextFrame();
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
        return glowEnabled && cursor == 0 && !reverseVideo
            && (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE | TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE)) == 0
            && backColor == TextStyle.COLOR_INDEX_BACKGROUND;
    }

    static void setTextGlow(Paint paint, int color, float multiplier) {
        paint.setShadowLayer(Math.max(1f, glowRadius * density * multiplier), 0f, 0f,
            ((glowIntensity * 255 / 100) << 24) | (color & 0x00FFFFFF));
    }

    static void clearTextGlow(Paint paint) {
        paint.clearShadowLayer();
    }

    static void requestFrameIfNeeded() {
        if (fadeAnimating || cursorMoving || Math.abs(scrollOffset) >= 0.5f) requestNextFrame();
    }

    private static void requestNextFrame() {
        TerminalView view = viewRef.get();
        if (view != null) view.postInvalidateOnAnimation();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}