package com.termux.app.terminal;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;

import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.terminal.TerminalSession;
import com.termux.view.SmoothCursorEffects;

import java.nio.charset.StandardCharsets;

/** Decodes the single shell-hook protocol and applies visual and completion feedback. */
final class SmoothCursorCommandHandler {
    private static final String CHANNEL_ID = "smooth_cursor_command_events";

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
            SmoothCursorEffects.onCommandStarted(context, command);
            return;
        }
        if (!"E".equals(kind)) return;
        int exitCode;
        try { exitCode = Integer.parseInt(fields[1]); }
        catch (NumberFormatException e) { return; }

        SmoothCursorEffects.onCommandFinished(context, exitCode, command);
        if (appVisible) {
            if (prefs.getBoolean(SmoothCursorEffects.K_COMMAND_VIBRATION_ON, true))
                vibrate(context, prefs.getInt(SmoothCursorEffects.K_COMMAND_VIBRATION_STRENGTH, 55));
        } else if (prefs.getBoolean(SmoothCursorEffects.K_COMMAND_NOTIFICATION_ON, true)) {
            notifyFinished(context, session);
        }
    }

    private static void vibrate(Context context, int strength) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        int amount = Math.max(0, Math.min(100, strength));
        if (amount == 0) return;
        long duration = 28L + Math.round(amount * 0.32f);
        if (Build.VERSION.SDK_INT >= 26) {
            int amplitude = amount == 0 ? 1 : 20 + Math.round(amount * 2.35f);
            vibrator.vibrate(VibrationEffect.createOneShot(duration, Math.min(255, amplitude)));
        } else {
            vibrator.vibrate(duration);
        }
    }

    private static void notifyFinished(Context context, TerminalSession session) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.sc_command_notification_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableVibration(false);
            manager.createNotificationChannel(channel);
        }
        int id = session == null ? 45031 : (session.mHandle.hashCode() & 0x7fffffff);
        if (id == 0) id = 45031;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(context.getString(R.string.sc_command_finished))
            .setContentText(context.getString(R.string.sc_command_finished_notification))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(
                context.getString(R.string.sc_command_finished_notification)))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVibrate(new long[0]);
        manager.notify(id, notification.build());
    }
}
