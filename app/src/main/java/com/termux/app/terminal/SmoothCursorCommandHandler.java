package com.termux.app.terminal;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Base64;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.terminal.TerminalSession;
import com.termux.view.SmoothCursorEffects;

import java.nio.charset.StandardCharsets;

/** Decodes the shell-hook protocol and applies enabled completion feedback. */
public final class SmoothCursorCommandHandler {
    private static final String CHANNEL_ID = "smooth_cursor_command_events_v2";

    private SmoothCursorCommandHandler() { }

    static void handle(Context context, TerminalSession session, String event, boolean appVisible) {
        SharedPreferences prefs = SmoothCursorEffects.getPreferences(context);
        String[] fields = event.split(";", 3);
        if (fields.length != 3) return;
        String command;
        try {
            command = new String(Base64.decode(fields[2], Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return;
        }
        String kind = fields[0];
        if ("B".equals(kind)) {
            if (prefs.getBoolean(SmoothCursorEffects.K_COMMAND_PULSE_ON, false))
                SmoothCursorEffects.onCommandStarted(context, command);
            return;
        }
        if (!"E".equals(kind)) return;
        int exitCode;
        try { exitCode = Integer.parseInt(fields[1]); }
        catch (NumberFormatException e) { return; }

        SmoothCursorEffects.onCommandFinished(context, exitCode, command);
        if (appVisible) {
            if (prefs.getBoolean(SmoothCursorEffects.K_COMMAND_VIBRATION_ON, false))
                vibrate(context, prefs.getInt(SmoothCursorEffects.K_COMMAND_VIBRATION_STRENGTH, 55));
        } else if (prefs.getBoolean(SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON, false)) {
            notifyFinished(context, session);
        }
    }

    private static void vibrate(Context context, int strength) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator == null || !vibrator.hasVibrator()) return;
        int amount = Math.max(0, Math.min(100, strength));
        if (amount == 0) return;
        long duration = 28L + Math.round(amount * 0.32f);
        if (Build.VERSION.SDK_INT >= 26) {
            int amplitude = Math.min(255, 20 + Math.round(amount * 2.35f));
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude), attributes);
        } else {
            vibrator.vibrate(duration);
        }
    }

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            context.getString(R.string.sc_command_notification_channel), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    private static void notifyFinished(Context context, TerminalSession session) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureNotificationChannel(context);
        int id = session == null ? 45031 : (session.mHandle.hashCode() & 0x7fffffff);
        if (id == 0) id = 45031;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(context.getString(R.string.sc_command_finished))
            .setContentText(context.getString(R.string.sc_command_finished_notification))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true);
        manager.notify(id, notification.build());
    }
}
