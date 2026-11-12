package com.termux.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.view.SmoothCursorEffects;

import java.util.Locale;

/** Settings for the SmoothCursor effects embedded in this Termux APK. */
public final class SmoothCursorSettingsActivity extends Activity {

    private android.content.SharedPreferences preferences;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.action_open_smooth_cursor_settings);
        preferences = SmoothCursorEffects.getPreferences(this);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(20);
        content.setPadding(horizontal, dp(12), horizontal, dp(24));
        TextView header = new TextView(this);
        header.setText(R.string.action_open_smooth_cursor_settings);
        header.setTextSize(24);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(0, dp(8), 0, dp(12));
        content.addView(header, matchWrap());
        scroll.addView(content, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        section(R.string.sc_section_cursor);
        addSwitch(R.string.sc_use_theme, SmoothCursorEffects.K_USE_THEME, true);
        addColorEditor();
        addSeekBar(R.string.sc_outline, SmoothCursorEffects.K_OUTLINE, 0, 100, 0, R.string.sc_percent);
        addSeekBar(R.string.sc_trail, SmoothCursorEffects.K_TRAIL, 0, 100, 50, R.string.sc_percent);

        section(R.string.sc_section_scroll);
        addSwitch(R.string.sc_smooth_scroll, SmoothCursorEffects.K_SMOOTH_SCROLL, true);
        addSeekBar(R.string.sc_scroll_speed, SmoothCursorEffects.K_SCROLL_SPEED, 0, 100, 50, R.string.sc_percent);

        section(R.string.sc_section_padding);
        addSwitch(R.string.sc_padding_enabled, SmoothCursorEffects.K_PAD_ON, false);
        addSeekBar(R.string.sc_padding_horizontal, SmoothCursorEffects.K_PAD_X, 0, 64, 8, R.string.sc_dp);
        addSeekBar(R.string.sc_padding_vertical, SmoothCursorEffects.K_PAD_Y, 0, 64, 8, R.string.sc_dp);

        section(R.string.sc_section_glow);
        addSwitch(R.string.sc_glow_enabled, SmoothCursorEffects.K_GLOW_ON, false);
        addSeekBar(R.string.sc_glow_radius, SmoothCursorEffects.K_GLOW_RADIUS, 1, 30, 6, R.string.sc_dp);
        addSeekBar(R.string.sc_glow_intensity, SmoothCursorEffects.K_GLOW_INTENSITY, 0, 100, 70, R.string.sc_percent);

        section(R.string.sc_section_fade);
        addSwitch(R.string.sc_fade_enabled, SmoothCursorEffects.K_FADE_ON, true);
        addSeekBar(R.string.sc_fade_duration, SmoothCursorEffects.K_FADE_MS, 50, 1000, 160, R.string.sc_ms);

        Button reset = new Button(this);
        reset.setText(R.string.sc_reset);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setMessage(R.string.sc_reset_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                preferences.edit().clear().apply();
                recreate();
            }).show());
        content.addView(reset, matchWrap());
    }

    private void section(int title) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(19);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setPadding(0, dp(20), 0, dp(8));
        content.addView(label, matchWrap());
    }

    private void addSwitch(int title, String key, boolean defaultValue) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setTextSize(16);
        control.setPadding(0, dp(8), 0, dp(8));
        control.setChecked(preferences.getBoolean(key, defaultValue));
        control.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean(key, checked).apply());
        content.addView(control, matchWrap());
    }

    private void addSeekBar(int title, String key, int min, int max, int defaultValue, int unit) {
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
        label.setText(getString(title) + ": " + formatValue(initial, unit));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                label.setText(getString(title) + ": " + formatValue(value, unit));
                if (fromUser) preferences.edit().putInt(key, value).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        block.addView(bar, matchWrap());
        content.addView(block, matchWrap());
    }

    private String formatValue(int value, int unit) {
        return getString(unit, value);
    }

    private void addColorEditor() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView label = new TextView(this);
        label.setText(R.string.sc_cursor_color);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));

        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setHint("#FFFFFF");
        hex.setText(String.format(Locale.ROOT, "#%06X",
            preferences.getInt(SmoothCursorEffects.K_COLOR, Color.WHITE) & 0xFFFFFF));
        row.addView(hex, new LinearLayout.LayoutParams(dp(112), dp(48)));

        Button apply = new Button(this);
        apply.setText(R.string.sc_apply);
        apply.setOnClickListener(v -> {
            String value = hex.getText().toString().trim();
            if (!value.startsWith("#")) value = "#" + value;
            try {
                int parsed = Color.parseColor(value) | 0xFF000000;
                preferences.edit().putInt(SmoothCursorEffects.K_COLOR, parsed).apply();
                hex.setText(String.format(Locale.ROOT, "#%06X", parsed & 0xFFFFFF));
                Toast.makeText(this, R.string.sc_color_saved, Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                hex.setError(getString(R.string.sc_invalid_color));
            }
        });
        row.addView(apply, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        content.addView(row, matchWrap());
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