package com.termux.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.view.SmoothCursorEffects;

import java.util.List;
import java.util.ArrayList;
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
    private static final String SECTION_COMMAND_RULES = "command_rules";
    private static final int REQUEST_IMAGE = 7102;

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
    private LinearLayout rulesContainer;
    private ArrayList<SmoothCursorEffects.CommandRule> editingRules;

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
        if (preferences != null)
            showHookResult(SmoothCursorShellHook.sync(this, preferences, shellName));
    }

    private void renderPage() {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(20);
        content.setPadding(horizontal, dp(12), horizontal, dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        String section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null) buildMainMenu();
        else buildSection(section);
    }

    private void buildMainMenu() {
        setTitle(R.string.action_open_smooth_cursor_settings);
        addHeader(R.string.action_open_smooth_cursor_settings);
        addMenuItem(R.string.sc_section_cursor, SECTION_CURSOR);
        addMenuItem(R.string.sc_section_breathing, SECTION_BREATHING);
        addMenuItem(R.string.sc_section_color_effects, SECTION_COLORS);
        addMenuItem(R.string.sc_section_scroll, SECTION_SCROLL);
        addMenuItem(R.string.sc_section_padding, SECTION_PADDING);
        addMenuItem(R.string.sc_section_glow, SECTION_GLOW);
        addMenuItem(R.string.sc_section_fade, SECTION_FADE);
        addMenuItem(R.string.sc_section_image_cursor, SECTION_IMAGE_CURSOR);
        addMenuItem(R.string.sc_section_line_highlight, SECTION_LINE_HIGHLIGHT);
        addMenuItem(R.string.sc_section_command_error, SECTION_COMMAND_ERROR);
        addMenuItem(R.string.sc_section_command_pulse, SECTION_COMMAND_PULSE);
        addMenuItem(R.string.sc_section_command_rules, SECTION_COMMAND_RULES);
        addResetButton();
    }

    private void buildSection(String section) {
        int title = sectionTitle(section);
        setTitle(title);
        Button back = new Button(this);
        back.setText(R.string.sc_back);
        styleActionButton(back, false);
        back.setOnClickListener(v -> finish());
        content.addView(back, matchWrap());
        addHeader(title);

        switch (section) {
            case SECTION_CURSOR:
                buildCursorSettings();
                break;
            case SECTION_BREATHING:
                addSwitch(R.string.sc_breathing_enabled, SmoothCursorEffects.K_BREATHING_ON, false);
                addSeekBar(R.string.sc_breathing_speed, SmoothCursorEffects.K_BREATHING_SPEED,
                    1, 100, 50, R.string.sc_percent);
                addSeekBar(R.string.sc_breathing_depth, SmoothCursorEffects.K_BREATHING_DEPTH,
                    0, 100, 60, R.string.sc_percent);
                break;
            case SECTION_COLORS:
                buildColorSettings();
                break;
            case SECTION_SCROLL:
                addSwitch(R.string.sc_smooth_scroll, SmoothCursorEffects.K_SMOOTH_SCROLL, true);
                addSeekBar(R.string.sc_scroll_speed, SmoothCursorEffects.K_SCROLL_SPEED,
                    0, 100, 50, R.string.sc_percent);
                break;
            case SECTION_PADDING:
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
                addSwitch(R.string.sc_fade_enabled, SmoothCursorEffects.K_FADE_ON, true);
                addSeekBar(R.string.sc_fade_duration, SmoothCursorEffects.K_FADE_MS,
                    50, 1000, 160, R.string.sc_ms);
                break;
            case SECTION_IMAGE_CURSOR:
                buildImageCursorSettings();
                break;
            case SECTION_LINE_HIGHLIGHT:
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
            case SECTION_COMMAND_RULES:
                buildCommandRulesSettings();
                break;
            default:
                buildMainMenu();
                break;
        }
    }

    private void buildCursorSettings() {
        addCursorShapeSelector();
        addSwitch(R.string.sc_use_theme, SmoothCursorEffects.K_USE_THEME, true,
            SmoothCursorEffects.FEATURE_CUSTOM_COLOR);
        addColorEditor(R.string.sc_cursor_color, SmoothCursorEffects.K_COLOR, Color.WHITE);
        addSeekBar(R.string.sc_outline, SmoothCursorEffects.K_OUTLINE, 0, 100, 0, R.string.sc_percent);
        addSeekBar(R.string.sc_trail, SmoothCursorEffects.K_TRAIL, 0, 100, 50, R.string.sc_percent);
    }

    private void buildColorSettings() {
        gradientSwitch = addFeatureSwitch(R.string.sc_gradient_enabled, SmoothCursorEffects.FEATURE_GRADIENT);
        rainbowSwitch = addFeatureSwitch(R.string.sc_rainbow_enabled, SmoothCursorEffects.FEATURE_RAINBOW);
        addColorEditor(R.string.sc_gradient_color_start, SmoothCursorEffects.K_GRADIENT_COLOR_START,
            0xFFFF5A7A);
        addColorEditor(R.string.sc_gradient_color_end, SmoothCursorEffects.K_GRADIENT_COLOR_END,
            0xFF6C63FF);
        addSeekBar(R.string.sc_color_effect_speed, SmoothCursorEffects.K_COLOR_SPEED,
            1, 100, 50, R.string.sc_percent);
    }

    private void buildImageCursorSettings() {
        String imagePath = preferences.getString(SmoothCursorEffects.K_IMAGE_PATH, null);
        boolean hasImage = imagePath != null && new File(imagePath).isFile();
        if (!hasImage && preferences.getBoolean(SmoothCursorEffects.FEATURE_IMAGE_CURSOR, false))
            preferences.edit().putBoolean(SmoothCursorEffects.FEATURE_IMAGE_CURSOR, false).apply();
        imageSwitch = createSwitch(R.string.sc_image_cursor_enabled,
            preferences.getBoolean(SmoothCursorEffects.FEATURE_IMAGE_CURSOR, false) && hasImage);
        imageSwitch.setEnabled(hasImage);
        bindFeatureSwitch(imageSwitch, SmoothCursorEffects.FEATURE_IMAGE_CURSOR);
        content.addView(imageSwitch, matchWrap());

        TextView hint = new TextView(this);
        hint.setText(hasImage ? R.string.sc_image_cursor_ready : R.string.sc_image_cursor_empty);
        hint.setTextSize(14);
        hint.setPadding(0, 0, 0, dp(8));
        content.addView(hint, matchWrap());

        Button choose = new Button(this);
        choose.setText(hasImage ? R.string.sc_image_cursor_change : R.string.sc_image_cursor_choose);
        choose.setAllCaps(false);
        choose.setOnClickListener(v -> openImagePicker());
        content.addView(choose, matchWrap());
    }

    private void buildGlowSettings() {
        LinearLayout sharedBlock = new LinearLayout(this);
        sharedBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout splitBlock = new LinearLayout(this);
        splitBlock.setOrientation(LinearLayout.VERTICAL);

        addSwitch(sharedBlock, R.string.sc_glow_enabled, SmoothCursorEffects.K_GLOW_ON, false);
        addSeekBar(sharedBlock, R.string.sc_glow_radius, SmoothCursorEffects.K_GLOW_RADIUS,
            1, 30, 6, R.string.sc_dp);
        addSeekBar(sharedBlock, R.string.sc_glow_intensity, SmoothCursorEffects.K_GLOW_INTENSITY,
            0, 100, 70, R.string.sc_percent);

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

        boolean split = preferences.getBoolean(SmoothCursorEffects.K_GLOW_SPLIT, false);
        sharedBlock.setVisibility(split ? View.GONE : View.VISIBLE);
        splitBlock.setVisibility(split ? View.VISIBLE : View.GONE);
        Switch splitSwitch = new Switch(this);
        splitSwitch.setText(R.string.sc_glow_split);
        splitSwitch.setTextSize(16);
        splitSwitch.setPadding(0, dp(8), 0, dp(8));
        splitSwitch.setChecked(split);
        splitSwitch.setOnCheckedChangeListener((button, checked) -> {
            sharedBlock.setVisibility(checked ? View.GONE : View.VISIBLE);
            splitBlock.setVisibility(checked ? View.VISIBLE : View.GONE);
            preferences.edit().putBoolean(SmoothCursorEffects.K_GLOW_SPLIT, checked).apply();
        });
        content.addView(splitSwitch, matchWrap());
        content.addView(sharedBlock, matchWrap());
        content.addView(splitBlock, matchWrap());
    }


    private void buildCommandErrorSettings() {
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

    private void addRuleDefaultColorEditor() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText(R.string.sc_command_rules_color);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button color = new Button(this);
        styleActionButton(color, false);
        Runnable update = () -> {
            int value = preferences.getInt(SmoothCursorEffects.K_COMMAND_RULE_DEFAULT_COLOR, 0xFF42A5F5);
            color.setText(String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF));
            color.setBackground(roundRect(value, 14, value));
            color.setTextColor(contrastColor(value));
        };
        update.run();
        color.setEnabled(shellSupported);
        color.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            input.setText(String.format(Locale.ROOT, "#%06X",
                preferences.getInt(SmoothCursorEffects.K_COMMAND_RULE_DEFAULT_COLOR, 0xFF42A5F5) & 0xFFFFFF));
            new AlertDialog.Builder(this).setTitle(R.string.sc_command_rules_color)
                .setView(input).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sc_apply, (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.startsWith("#")) text = "#" + text;
                    try {
                        int newColor = Color.parseColor(text) | 0xFF000000;
                        preferences.edit().putInt(SmoothCursorEffects.K_COMMAND_RULE_DEFAULT_COLOR, newColor).apply();
                        for (int i = 0; i < editingRules.size(); i++) {
                            SmoothCursorEffects.CommandRule current = editingRules.get(i);
                            if (!current.customColor) editingRules.set(i,
                                new SmoothCursorEffects.CommandRule(current.command, newColor, current.pulse, false));
                        }
                        persistRules();
                        update.run();
                        renderRuleRows();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, R.string.sc_invalid_color, Toast.LENGTH_SHORT).show();
                    }
                }).show();
        });
        row.addView(color, new LinearLayout.LayoutParams(dp(136), dp(46)));
        content.addView(row, matchWrap());
    }

    private void buildCommandPulseSettings() {
        addCommandSwitch(R.string.sc_command_pulse_enabled, SmoothCursorEffects.K_COMMAND_PULSE_ON,
            true, false, false);
        addCommandSwitch(R.string.sc_command_vibration_enabled, SmoothCursorEffects.K_COMMAND_VIBRATION_ON,
            true, false, false);
        addSeekBar(R.string.sc_command_vibration_strength,
            SmoothCursorEffects.K_COMMAND_VIBRATION_STRENGTH, 0, 100, 55, R.string.sc_percent);
        addCommandSwitch(R.string.sc_command_notification_enabled,
            SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON, true, false, false);
    }

    private void buildCommandRulesSettings() {
        addCommandSwitch(R.string.sc_command_rules_enabled, SmoothCursorEffects.K_COMMAND_RULES_ON,
            true, false, true);
        addRuleDefaultColorEditor();
        TextView note = new TextView(this);
        note.setText(R.string.sc_command_rules_help);
        note.setTextSize(14);
        note.setAlpha(0.78f);
        note.setPadding(dp(4), dp(4), dp(4), dp(12));
        content.addView(note, matchWrap());
        rulesContainer = new LinearLayout(this);
        rulesContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(rulesContainer, matchWrap());
        editingRules = new ArrayList<>(SmoothCursorEffects.getCommandRules(preferences));
        renderRuleRows();

        Button add = new Button(this);
        add.setText(R.string.sc_command_rule_add);
        styleActionButton(add, true);
        add.setEnabled(shellSupported && preferences.getBoolean(SmoothCursorEffects.K_COMMAND_RULES_ON, true));
        add.setOnClickListener(v -> showAddRuleDialog());
        content.addView(add, matchWrap());
    }

    private void addCommandSwitch(int title, String key, boolean defaultValue,
                                  boolean feature, boolean refreshOnChange) {
        Switch control = createSwitch(title, shellSupported && preferences.getBoolean(key, defaultValue));
        control.setEnabled(shellSupported);
        control.setOnCheckedChangeListener((button, checked) -> {
            if (!shellSupported) return;
            if (feature) {
                List<String> disabled = SmoothCursorEffects.setFeatureEnabled(preferences, key, checked);
                showIncompatibilityToast(disabled, key);
            } else {
                preferences.edit().putBoolean(key, checked).apply();
            }
            showHookResult(SmoothCursorShellHook.sync(this, preferences, shellName));
            if (refreshOnChange) renderPage();
        });
        content.addView(control, matchWrap());
    }

    private void addChoice(int title, String key, int defaultValue, int[] optionIds) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button choice = new Button(this);
        styleActionButton(choice, false);
        Runnable update = () -> {
            int selected = clamp(preferences.getInt(key, defaultValue), 0, optionIds.length - 1);
            choice.setText(getString(optionIds[selected]));
        };
        update.run();
        choice.setEnabled(shellSupported);
        choice.setOnClickListener(v -> {
            String[] labels = new String[optionIds.length];
            for (int i = 0; i < optionIds.length; i++) labels[i] = getString(optionIds[i]);
            int selected = clamp(preferences.getInt(key, defaultValue), 0, optionIds.length - 1);
            new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected,
                (dialog, which) -> {
                    preferences.edit().putInt(key, which).apply();
                    update.run();
                    dialog.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).show();
        });
        row.addView(choice, new LinearLayout.LayoutParams(dp(142), dp(48)));
        content.addView(row, matchWrap());
    }

    private void renderRuleRows() {
        if (rulesContainer == null || editingRules == null) return;
        rulesContainer.removeAllViews();
        boolean rulesActive = shellSupported && preferences.getBoolean(SmoothCursorEffects.K_COMMAND_RULES_ON, true);
        for (int i = 0; i < editingRules.size(); i++) {
            final int index = i;
            SmoothCursorEffects.CommandRule rule = editingRules.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(25, 33, 46));
            background.setCornerRadius(dp(16));
            background.setStroke(dp(1), Color.rgb(53, 68, 88));
            card.setBackground(background);
            LinearLayout.LayoutParams cardParams = matchWrap();
            cardParams.bottomMargin = dp(8);

            EditText command = new EditText(this);
            command.setSingleLine(true);
            command.setTextColor(Color.WHITE);
            command.setHintTextColor(Color.LTGRAY);
            command.setTextSize(15);
            command.setHint(R.string.sc_command_rule_hint);
            command.setText(rule.command);
            card.addView(command, matchWrap());

            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER_VERTICAL);
            Button colorButton = new Button(this);
            colorButton.setText(String.format(Locale.ROOT, "#%06X", rule.color & 0xFFFFFF));
            styleActionButton(colorButton, false);
            colorButton.setBackground(roundRect(rule.color, 14, rule.color));
            colorButton.setTextColor(contrastColor(rule.color));
            colorButton.setOnClickListener(v -> showRuleColorDialog(index));
            controls.addView(colorButton, new LinearLayout.LayoutParams(0, dp(44), 1f));

            Switch pulse = new Switch(this);
            pulse.setText(R.string.sc_command_rule_pulse);
            pulse.setTextSize(13);
            pulse.setTextColor(Color.WHITE);
            pulse.setChecked(rule.pulse);
            pulse.setOnCheckedChangeListener((button, checked) -> {
                editingRules.set(index, new SmoothCursorEffects.CommandRule(
                    editingRules.get(index).command, editingRules.get(index).color, checked,
                    editingRules.get(index).customColor));
                persistRules();
            });
            controls.addView(pulse, new LinearLayout.LayoutParams(0, dp(48), 1.2f));

            TextView up = smallAction("↑", R.string.sc_command_rule_move_up);
            up.setEnabled(index > 0);
            up.setOnClickListener(v -> moveRule(index, index - 1));
            controls.addView(up, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView down = smallAction("↓", R.string.sc_command_rule_move_down);
            down.setEnabled(index + 1 < editingRules.size());
            down.setOnClickListener(v -> moveRule(index, index + 1));
            controls.addView(down, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView delete = smallAction("×", R.string.sc_command_rule_delete);
            delete.setOnClickListener(v -> {
                editingRules.remove(index);
                persistRules();
                renderRuleRows();
            });
            controls.addView(delete, new LinearLayout.LayoutParams(dp(42), dp(42)));
            card.addView(controls, matchWrap());

            Button save = new Button(this);
            save.setText(R.string.sc_command_rule_save);
            styleActionButton(save, true);
            save.setOnClickListener(v -> {
                String phrase = command.getText().toString().trim();
                if (phrase.isEmpty()) {
                    command.setError(getString(R.string.sc_command_rule_required));
                    return;
                }
                SmoothCursorEffects.CommandRule old = editingRules.get(index);
                editingRules.set(index, new SmoothCursorEffects.CommandRule(phrase, old.color, old.pulse, old.customColor));
                persistRules();
                Toast.makeText(this, R.string.sc_command_rule_saved, Toast.LENGTH_SHORT).show();
            });
            card.addView(save, matchWrap());
            setEnabledRecursive(card, rulesActive);
            rulesContainer.addView(card, cardParams);
            card.startAnimation(enterAnimation());
        }
    }

    private void showAddRuleDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.sc_command_rule_hint);
        new AlertDialog.Builder(this).setTitle(R.string.sc_command_rule_add)
            .setView(input).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sc_apply, (dialog, which) -> {
                String phrase = input.getText().toString().trim();
                if (phrase.isEmpty()) {
                    Toast.makeText(this, R.string.sc_command_rule_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                int defaultColor = preferences.getInt(SmoothCursorEffects.K_COMMAND_RULE_DEFAULT_COLOR, 0xFF42A5F5);
                editingRules.add(new SmoothCursorEffects.CommandRule(phrase, defaultColor, false, false));
                persistRules();
                renderRuleRows();
            }).show();
    }

    private void showRuleColorDialog(int index) {
        SmoothCursorEffects.CommandRule rule = editingRules.get(index);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(String.format(Locale.ROOT, "#%06X", rule.color & 0xFFFFFF));
        new AlertDialog.Builder(this).setTitle(R.string.sc_command_rule_color)
            .setView(input).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sc_apply, (dialog, which) -> {
                String value = input.getText().toString().trim();
                if (!value.startsWith("#")) value = "#" + value;
                try {
                    int color = Color.parseColor(value) | 0xFF000000;
                    SmoothCursorEffects.CommandRule current = editingRules.get(index);
                    editingRules.set(index, new SmoothCursorEffects.CommandRule(current.command, color, current.pulse, true));
                    persistRules();
                    renderRuleRows();
                } catch (IllegalArgumentException e) {
                    Toast.makeText(this, R.string.sc_invalid_color, Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    private void moveRule(int from, int to) {
        if (to < 0 || to >= editingRules.size()) return;
        SmoothCursorEffects.CommandRule rule = editingRules.remove(from);
        editingRules.add(to, rule);
        persistRules();
        renderRuleRows();
    }

    private void persistRules() {
        SmoothCursorEffects.saveCommandRules(preferences, editingRules);
        showHookResult(SmoothCursorShellHook.sync(this, preferences, shellName));
    }

    private TextView smallAction(String label, int description) {
        TextView action = new TextView(this);
        action.setText(label);
        action.setTextColor(Color.WHITE);
        action.setTextSize(22);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription(getString(description));
        action.setBackground(roundRect(Color.rgb(44, 58, 77), 14, Color.rgb(73, 91, 115)));
        return action;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int contrastColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color));
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
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
        button.setTextColor(Color.WHITE);
        button.setBackground(roundRect(accent ? Color.rgb(55, 110, 205) : Color.rgb(38, 51, 69),
            14, accent ? Color.rgb(92, 146, 236) : Color.rgb(58, 75, 96)));
        button.setElevation(dp(2));
    }

    private AlphaAnimation enterAnimation() {
        AlphaAnimation animation = new AlphaAnimation(0f, 1f);
        animation.setDuration(180);
        animation.setInterpolator(new DecelerateInterpolator());
        return animation;
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
            case SECTION_COMMAND_RULES: return R.string.sc_section_command_rules;
            default: return R.string.action_open_smooth_cursor_settings;
        }
    }

    private void addMenuItem(int title, String section) {
        Button item = new Button(this);
        item.setText(title);
        item.setAllCaps(false);
        item.setTextSize(16);
        item.setTextColor(Color.WHITE);
        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        item.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable card = new GradientDrawable();
        card.setColor(Color.rgb(28, 37, 51));
        card.setCornerRadius(dp(16));
        card.setStroke(dp(1), Color.rgb(53, 68, 88));
        item.setBackground(card);
        item.setElevation(dp(2));
        item.setOnClickListener(v -> {
            Intent intent = new Intent(this, SmoothCursorSettingsActivity.class);
            intent.putExtra(EXTRA_SECTION, section);
            intent.putExtra(SmoothCursorShellHook.EXTRA_SHELL, shellName);
            startActivity(intent);
        });
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        itemParams.bottomMargin = dp(8);
        content.addView(item, itemParams);
        item.startAnimation(enterAnimation());
    }

    private void addHeader(int title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(24);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(0, dp(8), 0, dp(12));
        content.addView(header, matchWrap());
    }

    private void addSubSection(LinearLayout parent, int title) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(16);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setPadding(0, dp(12), 0, dp(4));
        parent.addView(label, matchWrap());
    }

    private void addSwitch(int title, String key, boolean defaultValue) {
        addSwitch(content, title, key, defaultValue);
    }

    private void addSwitch(int title, String key, boolean defaultValue, String featureKey) {
        Switch control = createSwitch(title, preferences.getBoolean(key, defaultValue));
        control.setOnCheckedChangeListener((button, checked) -> {
            List<String> disabled = SmoothCursorEffects.setFeatureEnabled(preferences,
                featureKey, !checked);
            showIncompatibilityToast(disabled, featureKey);
        });
        content.addView(control, matchWrap());
    }

    private void addSwitch(LinearLayout parent, int title, String key, boolean defaultValue) {
        Switch control = createSwitch(title, preferences.getBoolean(key, defaultValue));
        control.setOnCheckedChangeListener((button, checked) ->
            preferences.edit().putBoolean(key, checked).apply());
        parent.addView(control, matchWrap());
    }

    private Switch addFeatureSwitch(int title, String featureKey) {
        Switch control = createSwitch(title, preferences.getBoolean(featureKey, false));
        bindFeatureSwitch(control, featureKey);
        content.addView(control, matchWrap());
        return control;
    }

    private Switch createSwitch(int title, boolean checked) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setTextSize(16);
        control.setPadding(0, dp(8), 0, dp(8));
        control.setChecked(checked);
        return control;
    }

    private void bindFeatureSwitch(Switch control, String featureKey) {
        control.setOnCheckedChangeListener((button, checked) -> {
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
                getString(featureTitle(key)), getString(featureTitle(enabledFeature))),
                Toast.LENGTH_SHORT).show();
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
        Button selector = new Button(this);
        selector.setAllCaps(false);
        updateCursorShapeLabel(selector);
        content.addView(selector, matchWrap());

        View radius = addSeekBar(R.string.sc_rounded_radius, SmoothCursorEffects.K_ROUNDED_RADIUS,
            0, 50, 24, R.string.sc_percent);
        radius.setVisibility(preferences.getInt(SmoothCursorEffects.K_CURSOR_SHAPE,
            SmoothCursorEffects.CURSOR_SHAPE_FOLLOW_TERMINAL)
            == SmoothCursorEffects.CURSOR_SHAPE_ROUNDED_BLOCK ? View.VISIBLE : View.GONE);
        selector.setOnClickListener(v -> {
            int selected = clamp(preferences.getInt(SmoothCursorEffects.K_CURSOR_SHAPE,
                SmoothCursorEffects.CURSOR_SHAPE_FOLLOW_TERMINAL), 0, cursorShapeLabels.length - 1);
            String[] labels = new String[cursorShapeLabels.length];
            for (int i = 0; i < cursorShapeLabels.length; i++) labels[i] = getString(cursorShapeLabels[i]);
            new AlertDialog.Builder(this)
                .setTitle(R.string.sc_cursor_shape)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    preferences.edit().putInt(SmoothCursorEffects.K_CURSOR_SHAPE, which).apply();
                    updateCursorShapeLabel(selector);
                    radius.setVisibility(which == SmoothCursorEffects.CURSOR_SHAPE_ROUNDED_BLOCK
                        ? View.VISIBLE : View.GONE);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
    }

    private void updateCursorShapeLabel(TextView selector) {
        int shape = preferences.getInt(SmoothCursorEffects.K_CURSOR_SHAPE,
            SmoothCursorEffects.CURSOR_SHAPE_FOLLOW_TERMINAL);
        shape = Math.max(0, Math.min(cursorShapeLabels.length - 1, shape));
        selector.setText(getString(R.string.sc_cursor_shape) + ": " + getString(cursorShapeLabels[shape]));
    }

    private View addSeekBar(int title, String key, int min, int max, int defaultValue, int unit) {
        return addSeekBar(content, title, key, min, max, defaultValue, unit);
    }

    private View addSeekBar(LinearLayout parent, int title, String key, int min, int max,
                            int defaultValue, int unit) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(7), 0, dp(7));

        TextView label = new TextView(this);
        label.setTextSize(15);
        block.addView(label, matchWrap());

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        int initial = clamp(preferences.getInt(key, defaultValue), min, max);
        bar.setProgress(initial - min);
        label.setText(getString(title) + ": " + getString(unit, initial));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                label.setText(getString(title) + ": " + getString(unit, value));
                if (fromUser) preferences.edit().putInt(key, value).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        block.addView(bar, matchWrap());
        parent.addView(block, matchWrap());
        return block;
    }

    private void addColorEditor(int title, String key, int defaultColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));

        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setHint("#FFFFFF");
        hex.setText(String.format(Locale.ROOT, "#%06X",
            preferences.getInt(key, defaultColor) & 0xFFFFFF));
        row.addView(hex, new LinearLayout.LayoutParams(dp(112), dp(48)));

        Button apply = new Button(this);
        apply.setText(R.string.sc_apply);
        apply.setOnClickListener(v -> {
            String value = hex.getText().toString().trim();
            if (!value.startsWith("#")) value = "#" + value;
            try {
                int parsed = Color.parseColor(value) | 0xFF000000;
                preferences.edit().putInt(key, parsed).apply();
                hex.setText(String.format(Locale.ROOT, "#%06X", parsed & 0xFFFFFF));
                Toast.makeText(this, R.string.sc_color_saved, Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                hex.setError(getString(R.string.sc_invalid_color));
            }
        });
        row.addView(apply, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        content.addView(row, matchWrap());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.sc_image_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null)
            return;
        try {
            Bitmap bitmap = decodeSelectedImage(data.getData());
            if (bitmap == null) {
                Toast.makeText(this, R.string.sc_image_open_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            showCropEditor(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, R.string.sc_image_open_failed, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, R.string.sc_image_save_failed, Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, R.string.sc_image_cursor_saved, Toast.LENGTH_SHORT).show();
        return true;
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