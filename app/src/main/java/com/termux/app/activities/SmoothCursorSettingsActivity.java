package com.termux.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.Manifest;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.material.snackbar.Snackbar;

import com.termux.R;
import com.termux.app.terminal.TerminalBackgroundManager;
import com.termux.app.terminal.SmoothCursorCommandHandler;
import com.termux.view.SmoothCursorEffects;

import java.util.List;
import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Settings for the SmoothCursor effects embedded in this Termux APK. */
public final class SmoothCursorSettingsActivity extends Activity {

    private static final String EXTRA_SECTION = "smooth_cursor_section";
    private static final String SECTION_CURSOR = "cursor";
    private static final String SECTION_BREATHING = "breathing";
    private static final String SECTION_COLORS = "colors";
    private static final String SECTION_SCROLL = "scroll";
    private static final String SECTION_PADDING = "padding";
    private static final String SECTION_GLOW = "glow";
    private static final String SECTION_FADE = "fade";
    private static final String SECTION_IMAGE_CURSOR = "image_cursor";
    private static final String SECTION_LINE_HIGHLIGHT = "line_highlight";
    private static final String SECTION_COMMAND_ERROR = "command_error";
    private static final String SECTION_COMMAND_PULSE = "command_pulse";
    private static final String SECTION_BACKGROUND = "background";
    private static final int REQUEST_IMAGE = 7102;
    private static final int REQUEST_BACKGROUND = 7103;
    private static final int REQUEST_NOTIFICATIONS = 7104;
    private int pendingBackgroundMode;
    private LinearLayout activeCard;
    private TextView titleView;
    private LinearLayout backgroundFileCard;
    private View backgroundFileHeader;
    private LinearLayout backgroundModeSegments;
    private CursorPreview previewView;
    private View backgroundDimBlock;
    private View roundedRadiusControl;
    private Switch notificationSwitch;
    private Switch cursorBlinkSwitch;
    private boolean notificationEnablePending;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener backgroundPreferenceListener =
        (prefs, key) -> runOnUiThread(() -> {
            if (TerminalBackgroundManager.PREF_MODE.equals(key) || TerminalBackgroundManager.PREF_FILE.equals(key))
                refreshBackgroundSelection();
            if (SmoothCursorEffects.K_CURSOR_BLINK_ON.equals(key) && cursorBlinkSwitch != null)
                cursorBlinkSwitch.setChecked(prefs.getBoolean(SmoothCursorEffects.K_CURSOR_BLINK_ON, false));
            if (previewView != null) previewView.invalidate();
        });

    private final int[] cursorShapeLabels = {
        R.string.sc_shape_follow_terminal,
        R.string.sc_shape_block,
        R.string.sc_shape_line,
        R.string.sc_shape_underline,
        R.string.sc_shape_rounded_block,
        R.string.sc_shape_pill
    };

    private android.content.SharedPreferences preferences;
    private LinearLayout content;
    private Switch gradientSwitch;
    private Switch rainbowSwitch;
    private Switch imageSwitch;
    private String shellName;
    private boolean shellSupported;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = SmoothCursorEffects.getPreferences(this);
        shellName = getIntent().getStringExtra(SmoothCursorShellHook.EXTRA_SHELL);
        shellSupported = SmoothCursorShellHook.isSupported(shellName);
        showHookResult(SmoothCursorShellHook.sync(this, preferences, shellName));
        renderPage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferences != null) {
            preferences.registerOnSharedPreferenceChangeListener(backgroundPreferenceListener);
            if (notificationEnablePending && hasNotificationPermission()) {
                notificationEnablePending = false;
                preferences.edit().putBoolean(SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON, true).apply();
                if (notificationSwitch != null) notificationSwitch.setChecked(true);
            }
            showHookResult(SmoothCursorShellHook.sync(this, preferences, shellName));
        }
    }

    @Override
    protected void onPause() {
        if (preferences != null)
            preferences.unregisterOnSharedPreferenceChangeListener(backgroundPreferenceListener);
        super.onPause();
    }

    private void renderPage() {
        setContentView(R.layout.activity_smoothcursor_settings);
        content = findViewById(R.id.smoothcursor_content);
        titleView = findViewById(R.id.smoothcursor_title);
        findViewById(R.id.smoothcursor_back).setOnClickListener(v -> finish());
        int maxContent = Math.min(getResources().getDisplayMetrics().widthPixels, dp(600));
        ViewGroup.LayoutParams params = content.getLayoutParams();
        params.width = Math.max(dp(240), maxContent);
        content.setLayoutParams(params);
        String section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null) buildMainMenu();
        else buildSection(section);
    }

    private void setPageTitle(int title) {
        setTitle(title);
        if (titleView != null) titleView.setText(title);
    }

    private void beginGroup(int title) {
        TextView heading = new TextView(this);
        heading.setText(getString(title).toUpperCase(Locale.ROOT));
        heading.setTextColor(color(R.color.smoothcursor_secondary));
        heading.setTextSize(14);
        heading.setLetterSpacing(0.06f);
        heading.setPadding(0, dp(14), 0, dp(8));
        content.addView(heading, matchWrap());

        activeCard = new LinearLayout(this);
        activeCard.setOrientation(LinearLayout.VERTICAL);
        activeCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        activeCard.setBackground(roundRect(color(R.color.smoothcursor_card), 16,
            color(R.color.smoothcursor_card)));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(12);
        content.addView(activeCard, cardParams);
    }

    private LinearLayout targetCard() {
        return activeCard != null ? activeCard : content;
    }

    private void buildMainMenu() {
        setPageTitle(R.string.action_open_smooth_cursor_settings);
        addMenuItem(R.string.sc_section_cursor, R.string.sc_menu_cursor_summary, "▮", SECTION_CURSOR);
        addMenuItem(R.string.sc_section_breathing, R.string.sc_menu_breathing_summary, "◌", SECTION_BREATHING);
        addMenuItem(R.string.sc_section_color_effects, R.string.sc_menu_colors_summary, "◉", SECTION_COLORS);
        addMenuItem(R.string.sc_section_scroll, R.string.sc_menu_scroll_summary, "↕", SECTION_SCROLL);
        addMenuItem(R.string.sc_section_padding, R.string.sc_menu_padding_summary, "↔", SECTION_PADDING);
        addMenuItem(R.string.sc_section_glow, R.string.sc_menu_glow_summary, "✧", SECTION_GLOW);
        addMenuItem(R.string.sc_section_fade, R.string.sc_menu_fade_summary, "⋯", SECTION_FADE);
        addMenuItem(R.string.sc_section_image_cursor, R.string.sc_menu_image_summary, "▧", SECTION_IMAGE_CURSOR);
        addMenuItem(R.string.sc_section_line_highlight, R.string.sc_menu_line_summary, "☰", SECTION_LINE_HIGHLIGHT);
        addMenuItem(R.string.sc_section_command_error, R.string.sc_menu_result_summary, "✓", SECTION_COMMAND_ERROR);
        addMenuItem(R.string.sc_section_command_pulse, R.string.sc_menu_command_summary, "⌁", SECTION_COMMAND_PULSE);
        addMenuItem(R.string.sc_section_background, R.string.sc_menu_background_summary, "▤", SECTION_BACKGROUND);
        addResetButton();
    }

    private void buildSection(String section) {
        int title = sectionTitle(section);
        setPageTitle(title);
        switch (section) {
            case SECTION_CURSOR:
                buildCursorSettings();
                break;
            case SECTION_BREATHING:
                beginGroup(R.string.sc_group_effect);
                Switch breathing = addSwitch(R.string.sc_breathing_enabled, SmoothCursorEffects.K_BREATHING_ON, false);
                View breathingSpeed = addSeekBar(R.string.sc_breathing_speed, SmoothCursorEffects.K_BREATHING_SPEED,
                    1, 100, 50, R.string.sc_percent);
                View breathingDepth = addSeekBar(R.string.sc_breathing_depth, SmoothCursorEffects.K_BREATHING_DEPTH,
                    0, 100, 60, R.string.sc_percent);
                bindDependency(breathing, SmoothCursorEffects.K_BREATHING_ON, breathingSpeed, breathingDepth);
                break;
            case SECTION_COLORS:
                buildColorSettings();
                break;
            case SECTION_SCROLL:
                beginGroup(R.string.sc_group_effect);
                addSwitch(R.string.sc_smooth_scroll, SmoothCursorEffects.K_SMOOTH_SCROLL, false);
                addSeekBar(R.string.sc_scroll_speed, SmoothCursorEffects.K_SCROLL_SPEED,
                    0, 100, 50, R.string.sc_percent);
                break;
            case SECTION_PADDING:
                beginGroup(R.string.sc_group_effect);
                addSwitch(R.string.sc_padding_enabled, SmoothCursorEffects.K_PAD_ON, false);
                addSeekBar(R.string.sc_padding_horizontal, SmoothCursorEffects.K_PAD_X,
                    0, 64, 8, R.string.sc_dp);
                addSeekBar(R.string.sc_padding_vertical, SmoothCursorEffects.K_PAD_Y,
                    0, 64, 8, R.string.sc_dp);
                break;
            case SECTION_GLOW:
                buildGlowSettings();
                break;
            case SECTION_FADE:
                beginGroup(R.string.sc_group_effect);
                Switch fade = addSwitch(R.string.sc_fade_enabled, SmoothCursorEffects.K_FADE_ON, false);
                View fadeDuration = addSeekBar(R.string.sc_fade_duration, SmoothCursorEffects.K_FADE_MS,
                    50, 1000, 160, R.string.sc_ms);
                bindDependency(fade, SmoothCursorEffects.K_FADE_ON, fadeDuration);
                break;
            case SECTION_IMAGE_CURSOR:
                buildImageCursorSettings();
                break;
            case SECTION_LINE_HIGHLIGHT:
                beginGroup(R.string.sc_group_effect);
                addSwitch(R.string.sc_line_highlight_enabled,
                    SmoothCursorEffects.K_LINE_HIGHLIGHT_ON, false);
                addColorEditor(R.string.sc_line_highlight_color,
                    SmoothCursorEffects.K_LINE_HIGHLIGHT_COLOR, 0xFF5A8DEE);
                addSeekBar(R.string.sc_line_highlight_alpha,
                    SmoothCursorEffects.K_LINE_HIGHLIGHT_ALPHA, 0, 100, 24, R.string.sc_percent);
                addSeekBar(R.string.sc_line_highlight_speed,
                    SmoothCursorEffects.K_LINE_HIGHLIGHT_SPEED, 1, 100, 55, R.string.sc_percent);
                break;
            case SECTION_COMMAND_ERROR:
                buildCommandErrorSettings();
                break;
            case SECTION_COMMAND_PULSE:
                buildCommandPulseSettings();
                break;
            case SECTION_BACKGROUND:
                buildBackgroundSettings();
                break;
            default:
                buildMainMenu();
                break;
        }
    }

    private void buildCursorSettings() {
        beginGroup(R.string.sc_group_preview);
        previewView = new CursorPreview(this);
        activeCard.addView(previewView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(96)));

        beginGroup(R.string.sc_group_shape);
        addCursorShapeSelector();
        roundedRadiusControl = addSeekBar(R.string.sc_rounded_radius, SmoothCursorEffects.K_ROUNDED_RADIUS,
            0, 50, 24, R.string.sc_percent);
        roundedRadiusControl.setVisibility(preferences.getInt(SmoothCursorEffects.K_CURSOR_SHAPE,
            SmoothCursorEffects.CURSOR_SHAPE_FOLLOW_TERMINAL)
            == SmoothCursorEffects.CURSOR_SHAPE_ROUNDED_BLOCK ? View.VISIBLE : View.GONE);

        beginGroup(R.string.sc_group_color);
        Switch useTheme = addSwitch(R.string.sc_use_theme, SmoothCursorEffects.K_USE_THEME, true,
            SmoothCursorEffects.FEATURE_CUSTOM_COLOR);
        View colorControl = addColorEditor(R.string.sc_cursor_color, SmoothCursorEffects.K_COLOR, Color.WHITE);
        useTheme.setOnCheckedChangeListener((button, checked) -> {
            List<String> disabled = SmoothCursorEffects.setFeatureEnabled(preferences,
                SmoothCursorEffects.FEATURE_CUSTOM_COLOR, !checked);
            setEnabledRecursive(colorControl, !checked);
            showIncompatibilityToast(disabled, SmoothCursorEffects.FEATURE_CUSTOM_COLOR);
        });
        setEnabledRecursive(colorControl, !useTheme.isChecked());

        beginGroup(R.string.sc_group_effects);
        addSeekBar(R.string.sc_outline, SmoothCursorEffects.K_OUTLINE, 0, 100, 0, R.string.sc_percent);
        addSeekBar(R.string.sc_trail, SmoothCursorEffects.K_TRAIL, 0, 100, 50, R.string.sc_percent);
        cursorBlinkSwitch = addSwitch(R.string.sc_cursor_blink_enabled,
            SmoothCursorEffects.K_CURSOR_BLINK_ON, false);
    }

    private void buildColorSettings() {
        beginGroup(R.string.sc_group_effect);
        gradientSwitch = addFeatureSwitch(R.string.sc_gradient_enabled, SmoothCursorEffects.FEATURE_GRADIENT);
        rainbowSwitch = addFeatureSwitch(R.string.sc_rainbow_enabled, SmoothCursorEffects.FEATURE_RAINBOW);
        beginGroup(R.string.sc_group_color);
        addColorEditor(R.string.sc_gradient_color_start, SmoothCursorEffects.K_GRADIENT_COLOR_START,
            0xFFFF5A7A);
        addColorEditor(R.string.sc_gradient_color_end, SmoothCursorEffects.K_GRADIENT_COLOR_END,
            0xFF6C63FF);
        beginGroup(R.string.sc_group_effects);
        addSeekBar(R.string.sc_color_effect_speed, SmoothCursorEffects.K_COLOR_SPEED,
            1, 100, 50, R.string.sc_percent);
    }

    private void buildImageCursorSettings() {
        beginGroup(R.string.sc_group_effect);
        String imagePath = preferences.getString(SmoothCursorEffects.K_IMAGE_PATH, null);
        boolean hasImage = imagePath != null && new File(imagePath).isFile();
        if (!hasImage && preferences.getBoolean(SmoothCursorEffects.FEATURE_IMAGE_CURSOR, false))
            preferences.edit().putBoolean(SmoothCursorEffects.FEATURE_IMAGE_CURSOR, false).apply();
        imageSwitch = createSwitch(R.string.sc_image_cursor_enabled,
            preferences.getBoolean(SmoothCursorEffects.FEATURE_IMAGE_CURSOR, false) && hasImage);
        bindFeatureSwitch(imageSwitch, SmoothCursorEffects.FEATURE_IMAGE_CURSOR);
        addSwitchRow(imageSwitch, R.string.sc_image_cursor_summary);

        beginGroup(R.string.sc_group_file);
        buildImageCursorFileCard();
    }

    private void buildImageCursorFileCard() {
        if (activeCard == null) return;
        activeCard.removeAllViews();
        String path = preferences.getString(SmoothCursorEffects.K_IMAGE_PATH, null);
        File file = path == null ? null : new File(path);
        if (file == null || !file.isFile()) {
            LinearLayout empty = createFileEmptyCard(R.string.sc_image_cursor_choose, "＋");
            empty.setOnClickListener(v -> openImagePicker());
            activeCard.addView(empty, matchWrap());
            return;
        }
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(roundRect(color(R.color.smoothcursor_screen), 12,
            color(R.color.smoothcursor_screen)));
        preview.setClipToOutline(true);
        preview.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(filePreviewHeightDp()));
        previewParams.bottomMargin = dp(12);
        activeCard.addView(preview, previewParams);
        TextView name = createBodyText(file.getName(), true);
        name.setGravity(Gravity.CENTER);
        activeCard.addView(name, matchWrap());
        TextView size = createBodyText(formatFileSize(file.length()), false);
        size.setGravity(Gravity.CENTER);
        activeCard.addView(size, matchWrap());
        addFileActions(activeCard, R.string.sc_replace, R.string.sc_remove,
            this::openImagePicker, this::removeImageCursor);
    }

    private void buildGlowSettings() {
        beginGroup(R.string.sc_group_effects);
        LinearLayout sharedBlock = new LinearLayout(this);
        sharedBlock.setOrientation(LinearLayout.VERTICAL);
        Switch glow = createSwitch(R.string.sc_glow_enabled,
            preferences.getBoolean(SmoothCursorEffects.K_GLOW_ON, false));
        addSwitchRow(sharedBlock, glow, R.string.sc_setting_summary);
        glow.setOnCheckedChangeListener((button, checked) ->
            preferences.edit().putBoolean(SmoothCursorEffects.K_GLOW_ON, checked).apply());
        addSeekBar(sharedBlock, R.string.sc_glow_radius,
            SmoothCursorEffects.K_GLOW_RADIUS, 1, 30, 6, R.string.sc_dp);
        addSeekBar(sharedBlock, R.string.sc_glow_intensity,
            SmoothCursorEffects.K_GLOW_INTENSITY, 0, 100, 70, R.string.sc_percent);

        Switch split = addSwitch(R.string.sc_glow_split, SmoothCursorEffects.K_GLOW_SPLIT, false);
        LinearLayout splitBlock = new LinearLayout(this);
        splitBlock.setOrientation(LinearLayout.VERTICAL);
        addSubSection(splitBlock, R.string.sc_glow_text);
        addSwitch(splitBlock, R.string.sc_glow_text_enabled, SmoothCursorEffects.K_GLOW_TEXT_ON, false);
        addSeekBar(splitBlock, R.string.sc_glow_radius, SmoothCursorEffects.K_GLOW_TEXT_RADIUS,
            1, 30, 6, R.string.sc_dp);
        addSeekBar(splitBlock, R.string.sc_glow_intensity, SmoothCursorEffects.K_GLOW_TEXT_INTENSITY,
            0, 100, 70, R.string.sc_percent);
        addSubSection(splitBlock, R.string.sc_glow_cursor);
        addSwitch(splitBlock, R.string.sc_glow_cursor_enabled, SmoothCursorEffects.K_GLOW_CURSOR_ON, false);
        addSeekBar(splitBlock, R.string.sc_glow_radius, SmoothCursorEffects.K_GLOW_CURSOR_RADIUS,
            1, 30, 6, R.string.sc_dp);
        addSeekBar(splitBlock, R.string.sc_glow_intensity, SmoothCursorEffects.K_GLOW_CURSOR_INTENSITY,
            0, 100, 70, R.string.sc_percent);
        activeCard.addView(sharedBlock, matchWrap());
        activeCard.addView(splitBlock, matchWrap());
        Runnable update = () -> {
            boolean separate = preferences.getBoolean(SmoothCursorEffects.K_GLOW_SPLIT, false);
            sharedBlock.setVisibility(separate ? View.GONE : View.VISIBLE);
            splitBlock.setVisibility(separate ? View.VISIBLE : View.GONE);
        };
        split.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(SmoothCursorEffects.K_GLOW_SPLIT, checked).apply();
            update.run();
        });
        update.run();
    }

    private void buildCommandErrorSettings() {
        beginGroup(R.string.sc_group_result);
        addCommandSwitch(R.string.sc_error_effect_enabled, SmoothCursorEffects.FEATURE_ERROR_FLASH,
            false, true, false);
        addChoice(R.string.sc_error_effect_type, SmoothCursorEffects.K_ERROR_STYLE, 0,
            new int[]{R.string.sc_error_type_tint, R.string.sc_error_type_flash});
        addColorEditor(R.string.sc_error_effect_color, SmoothCursorEffects.K_ERROR_COLOR, 0xFFFF3B30);
        addColorEditor(R.string.sc_cancel_effect_color, SmoothCursorEffects.K_CANCEL_COLOR, 0xFFFFB020);
        addSeekBar(R.string.sc_error_effect_duration, SmoothCursorEffects.K_ERROR_DURATION,
            150, 5000, 900, R.string.sc_ms);
        addSeekBar(R.string.sc_error_effect_strength, SmoothCursorEffects.K_ERROR_STRENGTH,
            0, 100, 80, R.string.sc_percent);
    }

    private void buildBackgroundSettings() {
        beginGroup(R.string.sc_group_mode);
        int selected = clamp(preferences.getInt(TerminalBackgroundManager.PREF_MODE, 0), 0, 2);
        backgroundModeSegments = addSegmented(new int[]{R.string.sc_background_off,
            R.string.sc_background_image, R.string.sc_background_video}, selected, mode -> {
            if (mode == 0) {
                clearBackground();
            } else {
                pendingBackgroundMode = mode;
                openBackgroundPicker(mode);
            }
        });
        beginGroup(R.string.sc_group_file);
        backgroundFileCard = activeCard;
        backgroundFileHeader = content.getChildAt(content.getChildCount() - 2);
        refreshBackgroundSelection();
        beginGroup(R.string.sc_group_effects);
        backgroundDimBlock = addSeekBar(R.string.sc_background_dim, TerminalBackgroundManager.PREF_DIM,
            0, 100, 0, R.string.sc_percent);
        refreshBackgroundSelection();
    }

    private void refreshBackgroundSelection() {
        if (backgroundFileCard == null || preferences == null) return;
        int mode = clamp(preferences.getInt(TerminalBackgroundManager.PREF_MODE, 0), 0, 2);
        selectSegment(backgroundModeSegments, mode);
        String path = preferences.getString(TerminalBackgroundManager.PREF_FILE, null);
        File file = path == null ? null : new File(path);
        backgroundFileCard.removeAllViews();
        backgroundFileCard.setVisibility(mode == 0 ? View.GONE : View.VISIBLE);
        if (backgroundFileHeader != null) backgroundFileHeader.setVisibility(mode == 0 ? View.GONE : View.VISIBLE);
        if (mode == 0) {
            if (backgroundDimBlock != null) backgroundDimBlock.setVisibility(View.GONE);
            return;
        }
        if (file == null || !file.isFile()) {
            int label = mode == 2 ? R.string.sc_choose_video : R.string.sc_choose_image;
            LinearLayout empty = createFileEmptyCard(label, "＋");
            empty.setOnClickListener(v -> {
                pendingBackgroundMode = mode == 2 ? 2 : 1;
                openBackgroundPicker(pendingBackgroundMode);
            });
            backgroundFileCard.addView(empty, matchWrap());
        } else {
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackground(roundRect(color(R.color.smoothcursor_screen), 12,
                color(R.color.smoothcursor_screen)));
            preview.setClipToOutline(true);
            if (mode == 1) {
                preview.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
            } else {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(file.getAbsolutePath());
                    preview.setImageBitmap(retriever.getFrameAtTime(0));
                } catch (RuntimeException ignored) { }
                finally { try { retriever.release(); } catch (RuntimeException ignored) { } }
            }
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(filePreviewHeightDp()));
            previewParams.bottomMargin = dp(12);
            if (mode == 2) {
                TextView play = new TextView(this);
                play.setText("▶");
                play.setTextColor(color(R.color.smoothcursor_text));
                play.setTextSize(24);
                play.setGravity(Gravity.CENTER);
                FrameLayout frame = new FrameLayout(this);
                frame.setBackground(roundRect(color(R.color.smoothcursor_screen), 12,
                    color(R.color.smoothcursor_screen)));
                frame.setClipToOutline(true);
                frame.addView(preview, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, dp(filePreviewHeightDp())));
                frame.addView(play, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, dp(filePreviewHeightDp())));
                backgroundFileCard.addView(frame, previewParams);
            } else {
                backgroundFileCard.addView(preview, previewParams);
            }
            TextView name = createBodyText(file.getName(), true);
            name.setGravity(Gravity.CENTER);
            backgroundFileCard.addView(name, matchWrap());
            TextView size = createBodyText(formatFileSize(file.length()), false);
            size.setGravity(Gravity.CENTER);
            backgroundFileCard.addView(size, matchWrap());
            addFileActions(backgroundFileCard, R.string.sc_replace, R.string.sc_remove,
                () -> openBackgroundPicker(mode), this::clearBackground);
        }
        if (backgroundDimBlock != null) {
            backgroundDimBlock.setVisibility(mode == 0 ? View.GONE : View.VISIBLE);
            setEnabledRecursive(backgroundDimBlock, mode != 0 && file != null && file.isFile());
        }
    }

    private void clearBackground() {
        String path = preferences.getString(TerminalBackgroundManager.PREF_FILE, null);
        preferences.edit().putInt(TerminalBackgroundManager.PREF_MODE, 0)
            .remove(TerminalBackgroundManager.PREF_FILE).apply();
        if (path != null) new File(path).delete();
        refreshBackgroundSelection();
    }

    private void buildCommandPulseSettings() {
        beginGroup(R.string.sc_group_command);
        addCommandSwitch(R.string.sc_command_pulse_enabled, SmoothCursorEffects.K_COMMAND_PULSE_ON,
            false, false, false);
        Switch vibration = addCommandSwitch(R.string.sc_command_vibration_enabled,
            SmoothCursorEffects.K_COMMAND_VIBRATION_ON, false, false, false);
        View vibrationStrength = addSeekBar(R.string.sc_command_vibration_strength,
            SmoothCursorEffects.K_COMMAND_VIBRATION_STRENGTH, 0, 100, 55, R.string.sc_percent);
        bindDependency(vibration, SmoothCursorEffects.K_COMMAND_VIBRATION_ON, vibrationStrength);
        notificationSwitch = addCommandSwitch(R.string.sc_command_notification_enabled,
            SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON, false, false, false);
        if (!hasNotificationPermission()) {
            TextView note = createBodyText(R.string.sc_notification_permission_hint, false);
            LinearLayout.LayoutParams noteParams = matchWrap();
            noteParams.topMargin = dp(8);
            activeCard.addView(note, noteParams);
            Button settings = createSecondaryButton(R.string.sc_open_notification_settings);
            settings.setOnClickListener(v -> openAppNotificationSettings());
            activeCard.addView(settings, matchWrap());
        }
        beginGroup(R.string.sc_group_background_help);
        TextView batteryNote = createBodyText(R.string.sc_battery_hint, false);
        activeCard.addView(batteryNote, matchWrap());
        Button batterySettings = createSecondaryButton(R.string.sc_open_battery_settings);
        batterySettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.sc_battery_settings_unavailable, Toast.LENGTH_SHORT).show();
            }
        });
        activeCard.addView(batterySettings, matchWrap());
    }

    private Switch addCommandSwitch(int title, String key, boolean defaultValue,
                                    boolean feature, boolean refreshOnChange) {
        boolean initiallyChecked = shellSupported && preferences.getBoolean(key, defaultValue);
        if (SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON.equals(key) && !hasNotificationPermission()) {
            initiallyChecked = false;
            preferences.edit().putBoolean(key, false).apply();
        }
        Switch control = createSwitch(title, initiallyChecked);
        control.setEnabled(shellSupported);
        addSwitchRow(control, R.string.sc_setting_summary);
        control.setOnCheckedChangeListener((button, checked) -> {
            if (!shellSupported) return;
            if (SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON.equals(key) && checked
                && !hasNotificationPermission()) {
                notificationSwitch = control;
                control.setOnCheckedChangeListener(null);
                control.setChecked(false);
                control.setOnCheckedChangeListener((b, value) -> onCommandSwitchChanged(
                    title, key, value, feature, refreshOnChange, control));
                notificationEnablePending = true;
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    SmoothCursorCommandHandler.ensureNotificationChannel(this);
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
                } else {
                    new AlertDialog.Builder(this)
                        .setMessage(R.string.sc_notification_permission_hint)
                        .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> notificationEnablePending = false)
                        .setPositiveButton(R.string.sc_open_notification_settings,
                            (dialog, which) -> openAppNotificationSettings())
                        .show();
                }
                return;
            }
            onCommandSwitchChanged(title, key, checked, feature, refreshOnChange, control);
        });
        return control;
    }

    private void onCommandSwitchChanged(int title, String key, boolean checked,
                                        boolean feature, boolean refreshOnChange, Switch control) {
        if (feature) {
            List<String> disabled = SmoothCursorEffects.setFeatureEnabled(preferences, key, checked);
            showIncompatibilityToast(disabled, key);
        } else {
            preferences.edit().putBoolean(key, checked).apply();
        }
        showHookResult(SmoothCursorShellHook.sync(this, preferences, shellName));
        if (refreshOnChange) renderPage();
    }

    private boolean hasNotificationPermission() {
        boolean runtimeGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return runtimeGranted && NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    private void openAppNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(intent); }
        catch (Exception ignored) { Toast.makeText(this, R.string.sc_notification_settings_unavailable,
            Toast.LENGTH_SHORT).show(); }
    }

    private void openBackgroundPicker(int mode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mode == 1 ? "image/*" : "video/*");
        try {
            startActivityForResult(intent, REQUEST_BACKGROUND);
        } catch (Exception e) {
            preferences.edit().putInt(TerminalBackgroundManager.PREF_MODE, 0)
                .remove(TerminalBackgroundManager.PREF_FILE).apply();
            refreshBackgroundSelection();
            showSnackbar(R.string.sc_background_open_failed);
        }
    }

    private void saveBackgroundFile(Uri uri, int mode) {
        java.io.File destination = null;
        try {
            String mime = getContentResolver().getType(uri);
            String extension = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mime == null ? "" : mime);
            if (extension == null || extension.isEmpty()) extension = mode == 1 ? "img" : "mp4";
            extension = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (extension.isEmpty()) extension = mode == 1 ? "img" : "mp4";
            destination = new File(getFilesDir(), "terminal-background." + extension);
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(destination, false)) {
                if (input == null) throw new java.io.IOException("No input stream");
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
            }
            if (!destination.isFile() || destination.length() == 0)
                throw new java.io.IOException("Empty background file");
            if (mode == 1) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(destination.getAbsolutePath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                    throw new java.io.IOException("Invalid background image");
                int sample = 1;
                while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1024)
                    sample *= 2;
                BitmapFactory.Options decode = new BitmapFactory.Options();
                decode.inSampleSize = sample;
                Bitmap bitmap = BitmapFactory.decodeFile(destination.getAbsolutePath(), decode);
                if (bitmap == null) throw new java.io.IOException("Invalid background image");
                bitmap.recycle();
            } else {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                Bitmap frame;
                try {
                    retriever.setDataSource(destination.getAbsolutePath());
                    frame = retriever.getFrameAtTime(0);
                } finally {
                    try { retriever.release(); } catch (RuntimeException ignored) { }
                }
                if (frame == null) throw new java.io.IOException("Invalid background video");
                frame.recycle();
            }
            preferences.edit().putString(TerminalBackgroundManager.PREF_FILE,
                destination.getAbsolutePath()).putInt(TerminalBackgroundManager.PREF_MODE, mode).apply();
            refreshBackgroundSelection();
        } catch (Exception e) {
            if (destination != null) //noinspection ResultOfMethodCallIgnored
                destination.delete();
            preferences.edit().putInt(TerminalBackgroundManager.PREF_MODE, 0)
                .remove(TerminalBackgroundManager.PREF_FILE).apply();
            refreshBackgroundSelection();
            showSnackbar(R.string.sc_background_open_failed);
        }
    }

    private void addChoice(int title, String key, int defaultValue, int[] optionIds) {
        TextView label = createBodyText(title, true);
        LinearLayout.LayoutParams labelParams = matchWrap();
        labelParams.bottomMargin = dp(8);
        targetCard().addView(label, labelParams);
        int selected = clamp(preferences.getInt(key, defaultValue), 0, optionIds.length - 1);
        addSegmented(optionIds, selected, which -> preferences.edit().putInt(key, which).apply());
    }

    private interface SegmentListener { void onSelected(int index); }

    private LinearLayout addSegmented(int[] labels, int selected, SegmentListener listener) {
        LinearLayout segments = new LinearLayout(this);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        segments.setBackground(roundRect(color(R.color.smoothcursor_divider), 12,
            color(R.color.smoothcursor_divider)));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(48));
            chip.setTextColor(i == selected ? Color.BLACK : color(R.color.smoothcursor_text));
            chip.setBackground(roundRect(i == selected ? color(R.color.smoothcursor_accent)
                : color(R.color.smoothcursor_card), 10, i == selected
                ? color(R.color.smoothcursor_accent) : color(R.color.smoothcursor_card)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            segments.addView(chip, params);
            chip.setOnClickListener(v -> {
                selectSegment(segments, index);
                listener.onSelected(index);
            });
        }
        targetCard().addView(segments, matchWrap());
        return segments;
    }

    private void selectSegment(LinearLayout segments, int selected) {
        if (segments == null) return;
        for (int i = 0; i < segments.getChildCount(); i++) {
            TextView chip = (TextView) segments.getChildAt(i);
            chip.setTextColor(i == selected ? Color.BLACK : color(R.color.smoothcursor_text));
            chip.setBackground(roundRect(i == selected ? color(R.color.smoothcursor_accent)
                : color(R.color.smoothcursor_card), 10, i == selected
                ? color(R.color.smoothcursor_accent) : color(R.color.smoothcursor_card)));
        }
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int color(int id) { return ContextCompat.getColor(this, id); }

    private TextView createBodyText(int text, boolean primary) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color(primary ? R.color.smoothcursor_text : R.color.smoothcursor_secondary));
        view.setTextSize(primary ? 16 : 13);
        return view;
    }

    private TextView createBodyText(String text, boolean primary) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color(primary ? R.color.smoothcursor_text : R.color.smoothcursor_secondary));
        view.setTextSize(primary ? 16 : 13);
        return view;
    }

    private LinearLayout createFileEmptyCard(int label, String glyph) {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setMinimumHeight(dp(112));
        empty.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable border = new GradientDrawable();
        border.setColor(color(R.color.smoothcursor_card));
        border.setCornerRadius(dp(12));
        border.setStroke(dp(1), color(R.color.smoothcursor_secondary), dp(6), dp(4));
        empty.setBackground(border);
        TextView icon = new TextView(this);
        icon.setText(glyph);
        icon.setTextSize(28);
        icon.setTextColor(color(R.color.smoothcursor_accent));
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        TextView caption = createBodyText(label, true);
        caption.setGravity(Gravity.CENTER);
        empty.addView(caption, matchWrap());
        return empty;
    }

    private void addFileActions(LinearLayout parent, int replaceLabel, int removeLabel,
                                Runnable replace, Runnable remove) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(12);
        Button replaceButton = createSecondaryButton(replaceLabel);
        replaceButton.setOnClickListener(v -> replace.run());
        Button removeButton = createSecondaryButton(removeLabel);
        removeButton.setOnClickListener(v -> remove.run());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        buttonParams.setMargins(dp(4), 0, dp(4), 0);
        actions.addView(replaceButton, buttonParams);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        removeParams.setMargins(dp(4), 0, dp(4), 0);
        actions.addView(removeButton, removeParams);
        parent.addView(actions, actionsParams);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024f);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024f * 1024f));
    }

    private int filePreviewHeightDp() {
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels
            / getResources().getDisplayMetrics().density;
        float cardWidthDp = Math.max(180f, Math.min(screenWidthDp, 600f) - 64f);
        return Math.round(cardWidthDp * 9f / 16f);
    }

    private void showSnackbar(int message) {
        View anchor = content == null ? findViewById(R.id.smoothcursor_scroll) : content;
        if (anchor != null) Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
        else Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private Button createSecondaryButton(int text) {
        Button button = new Button(this);
        button.setText(text);
        styleActionButton(button, false);
        return button;
    }

    private void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.45f);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEnabledRecursive(group.getChildAt(i), enabled);
        }
    }

    private void showHookResult(SmoothCursorShellHook.Result result) {
        int message = 0;
        if (result == SmoothCursorShellHook.Result.INSTALLED) message = R.string.sc_hook_installed;
        else if (result == SmoothCursorShellHook.Result.RESTORED) message = R.string.sc_hook_restored;
        else if (result == SmoothCursorShellHook.Result.UNSUPPORTED) message = R.string.sc_hook_unsupported;
        else if (result == SmoothCursorShellHook.Result.FAILED) message = R.string.sc_hook_failed;
        if (message != 0) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void styleActionButton(Button button, boolean accent) {
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(color(R.color.smoothcursor_text));
        button.setBackground(roundRect(color(R.color.smoothcursor_divider), 12,
            color(R.color.smoothcursor_divider)));
        button.setMinHeight(dp(48));
        button.setElevation(0);
    }

    private int sectionTitle(String section) {
        switch (section) {
            case SECTION_CURSOR: return R.string.sc_section_cursor;
            case SECTION_BREATHING: return R.string.sc_section_breathing;
            case SECTION_COLORS: return R.string.sc_section_color_effects;
            case SECTION_SCROLL: return R.string.sc_section_scroll;
            case SECTION_PADDING: return R.string.sc_section_padding;
            case SECTION_GLOW: return R.string.sc_section_glow;
            case SECTION_FADE: return R.string.sc_section_fade;
            case SECTION_IMAGE_CURSOR: return R.string.sc_section_image_cursor;
            case SECTION_LINE_HIGHLIGHT: return R.string.sc_section_line_highlight;
            case SECTION_COMMAND_ERROR: return R.string.sc_section_command_error;
            case SECTION_COMMAND_PULSE: return R.string.sc_section_command_pulse;
            case SECTION_BACKGROUND: return R.string.sc_section_background;
            default: return R.string.action_open_smooth_cursor_settings;
        }
    }

    private void addMenuItem(int title, int summary, String glyph, String section) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(76));
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(roundRect(color(R.color.smoothcursor_card), 16,
            color(R.color.smoothcursor_card)));
        TextView icon = new TextView(this);
        icon.setText(glyph);
        icon.setTextSize(24);
        icon.setTextColor(color(R.color.smoothcursor_accent));
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(48)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        labels.addView(createBodyText(title, true), matchWrap());
        labels.addView(createBodyText(summary, false), matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setTextColor(color(R.color.smoothcursor_secondary));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(48)));
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, SmoothCursorSettingsActivity.class);
            intent.putExtra(EXTRA_SECTION, section);
            intent.putExtra(SmoothCursorShellHook.EXTRA_SHELL, shellName);
            startActivity(intent);
        });
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(12);
        content.addView(row, params);
    }

    private void addSubSection(LinearLayout parent, int title) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(color(R.color.smoothcursor_secondary));
        label.setTextSize(14);
        label.setPadding(0, dp(16), 0, dp(4));
        parent.addView(label, matchWrap());
    }

    private Switch addSwitch(int title, String key, boolean defaultValue) {
        Switch control = createSwitch(title, preferences.getBoolean(key, defaultValue));
        control.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean(key, checked).apply());
        addSwitchRow(control, R.string.sc_setting_summary);
        return control;
    }

    private Switch addSwitch(int title, String key, boolean defaultValue, String featureKey) {
        Switch control = createSwitch(title, preferences.getBoolean(key, defaultValue));
        control.setOnCheckedChangeListener((button, checked) -> {
            List<String> disabled = SmoothCursorEffects.setFeatureEnabled(preferences, featureKey, !checked);
            showIncompatibilityToast(disabled, featureKey);
        });
        addSwitchRow(control, R.string.sc_setting_summary);
        return control;
    }

    private Switch addSwitch(LinearLayout parent, int title, String key, boolean defaultValue) {
        Switch control = createSwitch(title, preferences.getBoolean(key, defaultValue));
        control.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean(key, checked).apply());
        addSwitchRow(parent, control, R.string.sc_setting_summary);
        return control;
    }

    private Switch addFeatureSwitch(int title, String featureKey) {
        Switch control = createSwitch(title, preferences.getBoolean(featureKey, false));
        bindFeatureSwitch(control, featureKey);
        addSwitchRow(control, R.string.sc_setting_summary);
        return control;
    }

    private Switch createSwitch(int title, boolean checked) {
        Switch control = new Switch(this);
        control.setTag(title);
        control.setContentDescription(getString(title));
        control.setChecked(checked);
        if (Build.VERSION.SDK_INT >= 21) {
            control.setThumbTintList(android.content.res.ColorStateList.valueOf(color(R.color.smoothcursor_accent)));
            control.setTrackTintList(android.content.res.ColorStateList.valueOf(color(R.color.smoothcursor_secondary)));
        }
        return control;
    }

    private void addSwitchRow(Switch control, int summary) { addSwitchRow(targetCard(), control, summary); }

    private void addSwitchRow(LinearLayout parent, Switch control, int summary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        TextView title = createBodyText((Integer) control.getTag(), true);
        TextView subtitle = createBodyText(summary, false);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.addView(title, matchWrap());
        labels.addView(subtitle, matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(control, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        row.setOnClickListener(v -> control.setChecked(!control.isChecked()));
        parent.addView(row, matchWrap());
    }

    private void bindDependency(Switch control, String key, View... views) {
        bindDependency(control, key, views, true);
    }

    private void bindDependency(Switch control, String key, View view, boolean enabledWhenChecked) {
        bindDependency(control, key, new View[]{view}, enabledWhenChecked);
    }

    private void bindDependency(Switch control, String key, View[] views, boolean enabledWhenChecked) {
        Runnable update = () -> {
            boolean enabled = control.isChecked() == enabledWhenChecked;
            for (View view : views) setEnabledRecursive(view, enabled);
        };
        control.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(key, checked).apply();
            update.run();
        });
        update.run();
    }

    private void bindFeatureSwitch(Switch control, String featureKey) {
        control.setOnCheckedChangeListener((button, checked) -> {
            if (SmoothCursorEffects.FEATURE_IMAGE_CURSOR.equals(featureKey) && checked) {
                String imagePath = preferences.getString(SmoothCursorEffects.K_IMAGE_PATH, null);
                if (imagePath == null || !new File(imagePath).isFile()) {
                    control.setOnCheckedChangeListener(null);
                    control.setChecked(false);
                    bindFeatureSwitch(control, featureKey);
                    openImagePicker();
                    return;
                }
            }
            List<String> disabled = SmoothCursorEffects.setFeatureEnabled(preferences, featureKey, checked);
            if (!disabled.isEmpty()) {
                for (String key : disabled) {
                    Switch incompatible = getFeatureSwitch(key);
                    if (incompatible != null) {
                        incompatible.setOnCheckedChangeListener(null);
                        incompatible.setChecked(false);
                        bindFeatureSwitch(incompatible, key);
                    }
                }
                showIncompatibilityToast(disabled, featureKey);
            }
        });
    }

    private Switch getFeatureSwitch(String featureKey) {
        if (SmoothCursorEffects.FEATURE_GRADIENT.equals(featureKey)) return gradientSwitch;
        if (SmoothCursorEffects.FEATURE_RAINBOW.equals(featureKey)) return rainbowSwitch;
        if (SmoothCursorEffects.FEATURE_IMAGE_CURSOR.equals(featureKey)) return imageSwitch;
        return null;
    }

    private void showIncompatibilityToast(List<String> disabled, String enabledFeature) {
        for (String key : disabled) {
            Toast.makeText(this, getString(R.string.sc_incompatible_disabled,
                getString(featureTitle(key)), getString(featureTitle(enabledFeature))), Toast.LENGTH_SHORT).show();
        }
    }

    private int featureTitle(String featureKey) {
        if (SmoothCursorEffects.FEATURE_CUSTOM_COLOR.equals(featureKey)) return R.string.sc_feature_custom_color;
        if (SmoothCursorEffects.FEATURE_IMAGE_CURSOR.equals(featureKey)) return R.string.sc_feature_image_cursor;
        if (SmoothCursorEffects.FEATURE_ERROR_FLASH.equals(featureKey)) return R.string.sc_feature_error_flash;
        if (SmoothCursorEffects.FEATURE_GRADIENT.equals(featureKey)) return R.string.sc_feature_gradient;
        if (SmoothCursorEffects.FEATURE_RAINBOW.equals(featureKey)) return R.string.sc_feature_rainbow;
        return R.string.sc_section_cursor;
    }

    private void addCursorShapeSelector() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(2);
        int[] icons = {0, 1, 2, 3, 4, 5};
        String[] glyphs = {"▮", "█", "▏", "▁", "▣", "●"};
        int selected = clamp(preferences.getInt(SmoothCursorEffects.K_CURSOR_SHAPE,
            SmoothCursorEffects.CURSOR_SHAPE_FOLLOW_TERMINAL), 0, cursorShapeLabels.length - 1);
        for (int i = 0; i < cursorShapeLabels.length; i++) {
            final int shape = i;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setMinimumHeight(dp(64));
            TextView icon = new TextView(this);
            icon.setText(glyphs[i]);
            icon.setTextSize(18);
            icon.setGravity(Gravity.CENTER);
            TextView label = createBodyText(cursorShapeLabels[i], true);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            chip.addView(icon, matchWrap());
            chip.addView(label, matchWrap());
            styleShapeChip(chip, i == selected);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(i % 3, 1f);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(chip, params);
            chip.setOnClickListener(v -> {
                preferences.edit().putInt(SmoothCursorEffects.K_CURSOR_SHAPE, shape).apply();
                if (roundedRadiusControl != null)
                    roundedRadiusControl.setVisibility(shape == SmoothCursorEffects.CURSOR_SHAPE_ROUNDED_BLOCK
                        ? View.VISIBLE : View.GONE);
                for (int j = 0; j < grid.getChildCount(); j++) styleShapeChip(grid.getChildAt(j), j == shape);
            });
        }
        targetCard().addView(grid, matchWrap());
    }

    private void styleShapeChip(View view, boolean selected) {
        view.setBackground(roundRect(selected ? color(R.color.smoothcursor_accent)
            : color(R.color.smoothcursor_divider), 12, selected
            ? color(R.color.smoothcursor_accent) : color(R.color.smoothcursor_divider)));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView) ((TextView) child).setTextColor(selected
                    ? Color.BLACK : color(R.color.smoothcursor_text));
            }
        }
    }

    private View addSeekBar(int title, String key, int min, int max, int defaultValue, int unit) {
        return addSeekBar(targetCard(), title, key, min, max, defaultValue, unit);
    }

    private View addSeekBar(LinearLayout parent, int title, String key, int min, int max,
                            int defaultValue, int unit) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(8), 0, dp(4));
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = createBodyText(title, true);
        labelRow.addView(label, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView value = new TextView(this);
        value.setTextColor(color(R.color.smoothcursor_accent));
        value.setTextSize(13);
        value.setGravity(Gravity.CENTER);
        value.setPadding(dp(10), 0, dp(10), 0);
        value.setBackground(roundRect(color(R.color.smoothcursor_divider), 12,
            color(R.color.smoothcursor_divider)));
        labelRow.addView(value, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)));
        block.addView(labelRow, matchWrap());
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        int initial = clamp(preferences.getInt(key, defaultValue), min, max);
        bar.setProgress(initial - min);
        bar.setMinHeight(dp(48));
        GradientDrawable track = new GradientDrawable();
        track.setColor(color(R.color.smoothcursor_divider));
        track.setCornerRadius(dp(2));
        track.setSize(-1, dp(4));
        GradientDrawable progress = new GradientDrawable();
        progress.setColor(color(R.color.smoothcursor_accent));
        progress.setCornerRadius(dp(2));
        progress.setSize(-1, dp(4));
        LayerDrawable layers = new LayerDrawable(new Drawable[]{track,
            new ClipDrawable(progress, Gravity.START, ClipDrawable.HORIZONTAL)});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        bar.setProgressDrawable(layers);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(color(R.color.smoothcursor_accent));
        thumb.setSize(dp(20), dp(20));
        bar.setThumb(thumb);
        value.setText(getString(unit, initial));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int current = min + progress;
                value.setText(getString(unit, current));
                if (fromUser) preferences.edit().putInt(key, current).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        block.addView(bar, matchWrap());
        parent.addView(block, matchWrap());
        return block;
    }

    private View addColorEditor(int title, String key, int defaultColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        TextView label = createBodyText(title, true);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));
        int initial = preferences.getInt(key, defaultColor);
        TextView swatch = new TextView(this);
        swatch.setText(" ");
        swatch.setBackground(roundRect(initial, 24, color(R.color.smoothcursor_secondary)));
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        swatchParams.setMargins(dp(4), 0, dp(8), 0);
        row.addView(swatch, swatchParams);
        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setTextColor(color(R.color.smoothcursor_text));
        hex.setHintTextColor(color(R.color.smoothcursor_secondary));
        hex.setTextSize(14);
        hex.setPadding(dp(8), 0, dp(8), 0);
        hex.setBackground(roundRect(color(R.color.smoothcursor_divider), 8,
            color(R.color.smoothcursor_divider)));
        hex.setText(String.format(Locale.ROOT, "#%06X", initial & 0xFFFFFF));
        row.addView(hex, new LinearLayout.LayoutParams(dp(104), dp(44)));
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                Integer parsed = parseHexColor(value.toString());
                if (parsed == null) {
                    hex.setError(value.length() == 0 ? null : getString(R.string.sc_invalid_color));
                } else {
                    hex.setError(null);
                    preferences.edit().putInt(key, parsed).apply();
                    swatch.setBackground(roundRect(parsed, 24, color(R.color.smoothcursor_secondary)));
                }
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        swatch.setOnClickListener(v -> showColorPalette(title, key, defaultColor, hex, swatch));
        targetCard().addView(row, matchWrap());
        return row;
    }

    private Integer parseHexColor(String text) {
        String value = text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) return null;
        try { return Color.parseColor("#" + value) | 0xFF000000; }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private void showColorPalette(int title, String key, int defaultColor, EditText hex, View swatch) {
        int[] colors = {0xFFEDEDED, 0xFFBDBDBD, 0xFF757575, 0xFF424242, 0xFF000000,
            0xFF90A4AE, 0xFF607D8B, 0xFF546E7A, 0xFF78909C, 0xFFCFD8DC,
            0xFF90CAF9, 0xFF80CBC4, 0xFFA5D6A7, 0xFFFFE082, 0xFFCE93D8,
            0xFFEF9A9A, 0xFFFFCC80, 0xFFB0BEC5, 0xFF81D4FA, 0xFFAED581};
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        for (int sample : colors) {
            View chip = new View(this);
            chip.setBackground(roundRect(sample, 20, color(R.color.smoothcursor_secondary)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(44); params.height = dp(44);
            params.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(chip, params);
            chip.setOnClickListener(v -> {
                preferences.edit().putInt(key, sample).apply();
                hex.setText(String.format(Locale.ROOT, "#%06X", sample & 0xFFFFFF));
                swatch.setBackground(roundRect(sample, 24, color(R.color.smoothcursor_secondary)));
            });
        }
        new AlertDialog.Builder(this).setTitle(title).setView(grid)
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_IMAGE);
        } catch (Exception e) {
            showSnackbar(R.string.sc_image_open_failed);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
            && hasNotificationPermission();
        if (granted) {
            notificationEnablePending = false;
            preferences.edit().putBoolean(SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON, true).apply();
            if (notificationSwitch != null) notificationSwitch.setChecked(true);
            showHookResult(SmoothCursorShellHook.sync(this, preferences, shellName));
        } else {
            notificationEnablePending = false;
            preferences.edit().putBoolean(SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON, false).apply();
            if (notificationSwitch != null) notificationSwitch.setChecked(false);
            new AlertDialog.Builder(this)
                .setMessage(R.string.sc_notification_permission_hint)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sc_open_notification_settings,
                    (dialog, which) -> { notificationEnablePending = true; openAppNotificationSettings(); })
                .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BACKGROUND) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                saveBackgroundFile(data.getData(), pendingBackgroundMode);
            else refreshBackgroundSelection();
            return;
        }
        if (requestCode != REQUEST_IMAGE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Bitmap bitmap = decodeSelectedImage(data.getData());
            if (bitmap == null) {
                removeImageCursor();
                showSnackbar(R.string.sc_image_open_failed);
                return;
            }
            showCropEditor(bitmap);
        } catch (Exception e) {
            removeImageCursor();
            showSnackbar(R.string.sc_image_open_failed);
        }
    }

    private Bitmap decodeSelectedImage(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) return null;
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 2048) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            return stream == null ? null : BitmapFactory.decodeStream(stream, null, options);
        }
    }

    private void showCropEditor(Bitmap bitmap) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(12));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = new Button(this);
        cancel.setText("×");
        cancel.setTextSize(24);
        cancel.setContentDescription(getString(R.string.sc_crop_cancel));
        Button accept = new Button(this);
        accept.setText("✓");
        accept.setTextSize(22);
        accept.setContentDescription(getString(R.string.sc_crop_accept));
        TextView title = new TextView(this);
        title.setText(R.string.sc_crop_title);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        toolbar.addView(cancel, new LinearLayout.LayoutParams(dp(56), dp(52)));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));
        toolbar.addView(accept, new LinearLayout.LayoutParams(dp(56), dp(52)));
        root.addView(toolbar, matchWrap());

        CropEditorView crop = new CropEditorView(this, bitmap,
            preferences.getInt(SmoothCursorEffects.K_CURSOR_SHAPE,
                SmoothCursorEffects.CURSOR_SHAPE_FOLLOW_TERMINAL),
            preferences.getInt(SmoothCursorEffects.K_ROUNDED_RADIUS, 24));
        root.addView(crop, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(300)));
        TextView zoomLabel = new TextView(this);
        zoomLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        zoomLabel.setText(getString(R.string.sc_crop_zoom, "1.0"));
        root.addView(zoomLabel, matchWrap());
        SeekBar zoom = new SeekBar(this);
        zoom.setMax(400);
        zoom.setProgress(0);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float scale = 1f + progress / 100f;
                crop.setScale(scale);
                zoomLabel.setText(getString(R.string.sc_crop_zoom, String.format(Locale.ROOT, "%.1f", scale)));
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        root.addView(zoom, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        accept.setOnClickListener(v -> {
            if (saveImageCursor(bitmap, crop)) dialog.dismiss();
        });
        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(420)),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    private boolean saveImageCursor(Bitmap bitmap, CropEditorView crop) {
        File target = new File(getFilesDir(), "smooth_cursor_image_" + System.currentTimeMillis() + ".png");
        String previousPath = preferences.getString(SmoothCursorEffects.K_IMAGE_PATH, null);
        try (FileOutputStream stream = new FileOutputStream(target)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new Exception("PNG encode failed");
            stream.flush();
        } catch (Exception e) {
            target.delete();
            showSnackbar(R.string.sc_image_save_failed);
            return false;
        }

        int averageColor = averageVisibleColor(bitmap);
        preferences.edit()
            .putString(SmoothCursorEffects.K_IMAGE_PATH, target.getAbsolutePath())
            .putInt(SmoothCursorEffects.K_IMAGE_TRAIL_COLOR, averageColor)
            .putFloat(SmoothCursorEffects.K_IMAGE_SCALE, crop.getScale())
            .putFloat(SmoothCursorEffects.K_IMAGE_OFFSET_X, crop.getNormalizedOffsetX())
            .putFloat(SmoothCursorEffects.K_IMAGE_OFFSET_Y, crop.getNormalizedOffsetY())
            .apply();
        List<String> disabled = SmoothCursorEffects.setFeatureEnabled(preferences,
            SmoothCursorEffects.FEATURE_IMAGE_CURSOR, true);
        if (imageSwitch != null) {
            imageSwitch.setOnCheckedChangeListener(null);
            imageSwitch.setChecked(true);
            imageSwitch.setEnabled(true);
            bindFeatureSwitch(imageSwitch, SmoothCursorEffects.FEATURE_IMAGE_CURSOR);
        }
        if (previousPath != null && !previousPath.equals(target.getAbsolutePath())) new File(previousPath).delete();
        showIncompatibilityToast(disabled, SmoothCursorEffects.FEATURE_IMAGE_CURSOR);
        buildImageCursorFileCard();
        Toast.makeText(this, R.string.sc_image_cursor_saved, Toast.LENGTH_SHORT).show();
        return true;
    }

    private void removeImageCursor() {
        String path = preferences.getString(SmoothCursorEffects.K_IMAGE_PATH, null);
        preferences.edit().remove(SmoothCursorEffects.K_IMAGE_PATH)
            .putBoolean(SmoothCursorEffects.FEATURE_IMAGE_CURSOR, false).apply();
        if (path != null) new File(path).delete();
        if (imageSwitch != null) {
            imageSwitch.setOnCheckedChangeListener(null);
            imageSwitch.setChecked(false);
            bindFeatureSwitch(imageSwitch, SmoothCursorEffects.FEATURE_IMAGE_CURSOR);
        }
        buildImageCursorFileCard();
    }

    private int averageVisibleColor(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        long red = 0L, green = 0L, blue = 0L, weight = 0L;
        for (int pixel : pixels) {
            int alpha = Color.alpha(pixel);
            if (alpha == 0) continue;
            red += (long) Color.red(pixel) * alpha;
            green += (long) Color.green(pixel) * alpha;
            blue += (long) Color.blue(pixel) * alpha;
            weight += alpha;
        }
        if (weight == 0L) return Color.WHITE;
        return Color.rgb((int) (red / weight), (int) (green / weight), (int) (blue / weight));
    }

    private final class CursorPreview extends View {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cursorRect = new RectF();

        CursorPreview(Activity context) { super(context); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            backgroundPaint.setColor(0xFF111111);
            canvas.drawRoundRect(0, 0, width, height, dp(12), dp(12), backgroundPaint);
            textPaint.setColor(0xFF9A9A9A);
            textPaint.setTextSize(dp(14));
            String prompt = "user@termux:~$ ls";
            float x = dp(16), baseline = height / 2f + dp(5);
            canvas.drawText(prompt, x, baseline, textPaint);
            float cursorLeft = x + textPaint.measureText(prompt) + dp(5);
            int shape = clamp(preferences.getInt(SmoothCursorEffects.K_CURSOR_SHAPE,
                SmoothCursorEffects.CURSOR_SHAPE_FOLLOW_TERMINAL), 0, 5);
            int fill = preferences.getBoolean(SmoothCursorEffects.K_USE_THEME, true)
                ? color(R.color.smoothcursor_accent)
                : preferences.getInt(SmoothCursorEffects.K_COLOR, Color.WHITE);
            cursorPaint.setColor(fill);
            boolean blinkOn = preferences.getBoolean(SmoothCursorEffects.K_CURSOR_BLINK_ON, false);
            boolean cursorVisible = !blinkOn || (System.currentTimeMillis() / 500L) % 2L == 0L;
            cursorPaint.setAlpha(cursorVisible ? 255 : 32);
            if (blinkOn) postInvalidateDelayed(500);
            cursorRect.set(cursorLeft, baseline - dp(17), cursorLeft + dp(12), baseline + dp(4));
            if (shape == SmoothCursorEffects.CURSOR_SHAPE_LINE) cursorRect.right = cursorRect.left + dp(3);
            else if (shape == SmoothCursorEffects.CURSOR_SHAPE_UNDERLINE) cursorRect.top = cursorRect.bottom - dp(3);
            else if (shape == SmoothCursorEffects.CURSOR_SHAPE_PILL) {
                cursorRect.inset(dp(3), dp(2));
                canvas.drawRoundRect(cursorRect, cursorRect.height() / 2, cursorRect.height() / 2, cursorPaint);
                return;
            } else if (shape == SmoothCursorEffects.CURSOR_SHAPE_ROUNDED_BLOCK) {
                float radius = dp(preferences.getInt(SmoothCursorEffects.K_ROUNDED_RADIUS, 24) * 12 / 50);
                canvas.drawRoundRect(cursorRect, radius, radius, cursorPaint);
                return;
            }
            canvas.drawRect(cursorRect, cursorPaint);
        }
    }

    private final class CropEditorView extends View {
        private final Bitmap bitmap;
        private final int shape;
        private final int roundedRadius;
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint checkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final RectF frame = new RectF();
        private float scale = 1f;
        private float offsetX;
        private float offsetY;
        private float lastTouchX;
        private float lastTouchY;
        private float lastPinchDistance;

        CropEditorView(Activity context, Bitmap bitmap, int shape, int roundedRadius) {
            super(context);
            this.bitmap = bitmap;
            this.shape = shape;
            this.roundedRadius = roundedRadius;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
            borderPaint.setColor(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            calculateFrame();
            canvas.drawColor(0xFF202124);
            buildClipPath();
            int save = canvas.save();
            canvas.clipPath(clipPath);
            int tile = dp(18);
            boolean alternate = false;
            for (int y = (int) frame.top, row = 0; y < frame.bottom; y += tile, row++) {
                for (int x = (int) frame.left, col = 0; x < frame.right; x += tile, col++) {
                    checkerPaint.setColor(((row + col) & 1) == 0 ? 0xFF4A4A4A : 0xFF666666);
                    canvas.drawRect(x, y, Math.min(x + tile, frame.right),
                        Math.min(y + tile, frame.bottom), checkerPaint);
                }
            }
            float baseScale = Math.max(frame.width() / bitmap.getWidth(), frame.height() / bitmap.getHeight());
            float drawWidth = bitmap.getWidth() * baseScale * scale;
            float drawHeight = bitmap.getHeight() * baseScale * scale;
            clampOffsets(drawWidth, drawHeight);
            RectF destination = new RectF(frame.centerX() - drawWidth / 2f + offsetX,
                frame.centerY() - drawHeight / 2f + offsetY,
                frame.centerX() + drawWidth / 2f + offsetX,
                frame.centerY() + drawHeight / 2f + offsetY);
            canvas.drawBitmap(bitmap, null, destination, imagePaint);
            canvas.restoreToCount(save);
            canvas.drawPath(clipPath, borderPaint);
        }

        private void calculateFrame() {
            float frameWidth = Math.min(getWidth() * 0.82f, (getHeight() - dp(20)) * 0.55f);
            frameWidth = Math.max(dp(72), frameWidth);
            float frameHeight = frameWidth / 0.55f;
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            frame.set(centerX - frameWidth / 2f, centerY - frameHeight / 2f,
                centerX + frameWidth / 2f, centerY + frameHeight / 2f);
        }

        private void buildClipPath() {
            clipPath.reset();
            RectF shapeBounds = new RectF(frame);
            switch (shape) {
                case SmoothCursorEffects.CURSOR_SHAPE_LINE:
                    shapeBounds.inset(frame.width() * 0.44f, 0f);
                    clipPath.addRect(shapeBounds, Path.Direction.CW);
                    break;
                case SmoothCursorEffects.CURSOR_SHAPE_UNDERLINE:
                    shapeBounds.top = shapeBounds.bottom - frame.height() * 0.14f;
                    clipPath.addRect(shapeBounds, Path.Direction.CW);
                    break;
                case SmoothCursorEffects.CURSOR_SHAPE_PILL:
                    shapeBounds.inset(frame.width() * 0.34f, 0f);
                    clipPath.addRoundRect(shapeBounds, shapeBounds.width() / 2f,
                        shapeBounds.width() / 2f, Path.Direction.CW);
                    break;
                case SmoothCursorEffects.CURSOR_SHAPE_ROUNDED_BLOCK:
                    float radius = Math.min(frame.width(), frame.height()) * roundedRadius / 100f;
                    clipPath.addRoundRect(shapeBounds, radius, radius, Path.Direction.CW);
                    break;
                default:
                    clipPath.addRect(shapeBounds, Path.Direction.CW);
                    break;
            }
        }

        private void clampOffsets(float imageWidth, float imageHeight) {
            float maxX = Math.max(0f, (imageWidth - frame.width()) / 2f);
            float maxY = Math.max(0f, (imageHeight - frame.height()) / 2f);
            offsetX = Math.max(-maxX, Math.min(maxX, offsetX));
            offsetY = Math.max(-maxY, Math.min(maxY, offsetY));
        }

        void setScale(float value) {
            scale = Math.max(1f, Math.min(5f, value));
            invalidate();
        }

        float getScale() {
            return scale;
        }

        float getNormalizedOffsetX() {
            calculateFrame();
            return frame.width() == 0f ? 0f : offsetX / frame.width();
        }

        float getNormalizedOffsetY() {
            calculateFrame();
            return frame.height() == 0f ? 0f : offsetY / frame.height();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2)
                        lastPinchDistance = pointerDistance(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() >= 2) {
                        float distance = pointerDistance(event);
                        if (lastPinchDistance > 0f && distance > 0f)
                            setScale(scale * distance / lastPinchDistance);
                        lastPinchDistance = distance;
                    } else {
                        offsetX += event.getX() - lastTouchX;
                        offsetY += event.getY() - lastTouchY;
                        invalidate();
                    }
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    lastPinchDistance = 0f;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    lastPinchDistance = 0f;
                    return true;
                default:
                    return true;
            }
        }

        private float pointerDistance(MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    private void addResetButton() {
        Button reset = new Button(this);
        reset.setText(R.string.sc_reset);
        styleActionButton(reset, false);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setMessage(R.string.sc_reset_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String imagePath = preferences.getString(SmoothCursorEffects.K_IMAGE_PATH, null);
                preferences.edit().clear().apply();
                if (imagePath != null) new File(imagePath).delete();
                recreate();
            }).show());
        content.addView(reset, matchWrap());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}