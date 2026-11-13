package com.termux.app.terminal;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import com.termux.app.TermuxActivity;
import com.termux.view.SmoothCursorEffects;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manages optional app-private image/video backgrounds behind the terminal text. */
public final class TerminalBackgroundManager implements
    android.content.SharedPreferences.OnSharedPreferenceChangeListener, TextureView.SurfaceTextureListener {

    public static final String PREF_MODE = "terminal_background_mode";
    public static final String PREF_FILE = "terminal_background_file";
    public static final String PREF_DIM = "terminal_background_dim";

    private static final int MODE_OFF = 0;
    private static final int MODE_IMAGE = 1;
    private static final int MODE_VIDEO = 2;

    private final Activity activity;
    private final ImageView imageView;
    private final TextureView videoView;
    private final View propertyOverlay;
    private final View dimOverlay;
    private final android.content.SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    private MediaPlayer mediaPlayer;
    private Surface videoSurface;
    private Bitmap currentBitmap;
    private File currentFile;
    private int currentMode = MODE_OFF;
    private int loadGeneration;
    private int videoWidth;
    private int videoHeight;
    private int backgroundOverlayColor;
    private boolean resumed;
    private boolean released;

    public TerminalBackgroundManager(Activity activity, ImageView imageView, TextureView videoView,
                                     View propertyOverlay, View dimOverlay) {
        this.activity = activity;
        this.imageView = imageView;
        this.videoView = videoView;
        this.propertyOverlay = propertyOverlay;
        this.dimOverlay = dimOverlay;
        this.preferences = SmoothCursorEffects.getPreferences(activity);
        this.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        this.videoView.setOpaque(false);
        this.videoView.setSurfaceTextureListener(this);
        this.preferences.registerOnSharedPreferenceChangeListener(this);
        refreshOverlayColor();
        applyModeFromPreferences();
    }

    public void onResume() {
        resumed = true;
        startPreparedVideo();
        if (currentMode == MODE_VIDEO && mediaPlayer == null) prepareVideo();
    }

    public void onStop() {
        resumed = false;
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try { mediaPlayer.pause(); } catch (IllegalStateException ignored) { }
        }
    }

    public void release() {
        released = true;
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        imageExecutor.shutdownNow();
        releasePlayer();
        clearImage();
    }

    public void refreshOverlayColor() {
        int color = 0;
        if (activity instanceof TermuxActivity && ((TermuxActivity) activity).getProperties() != null)
            color = ((TermuxActivity) activity).getProperties().getBackgroundOverlayColor();
        backgroundOverlayColor = color;
        propertyOverlay.setBackgroundColor(color);
        updatePropertyOverlayVisibility();
    }

    @Override
    public void onSharedPreferenceChanged(android.content.SharedPreferences prefs, String key) {
        if (PREF_MODE.equals(key) || PREF_FILE.equals(key)) {
            applyModeFromPreferences();
        } else if (PREF_DIM.equals(key)) {
            applyDim();
        }
    }

    private void applyModeFromPreferences() {
        if (released) return;
        int generation = ++loadGeneration;
        currentMode = preferences.getInt(PREF_MODE, MODE_OFF);
        String path = preferences.getString(PREF_FILE, null);
        currentFile = path == null ? null : new File(path);

        releasePlayer();
        clearImage();
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.GONE);
        updatePropertyOverlayVisibility();
        applyDim();

        if (currentMode == MODE_OFF) return;
        if ((currentMode != MODE_IMAGE && currentMode != MODE_VIDEO) || currentFile == null || !currentFile.isFile()) {
            disableBackground();
            return;
        }

        if (currentMode == MODE_IMAGE) {
            final File file = currentFile;
            imageExecutor.execute(() -> {
                Bitmap bitmap = decodeImage(file, imageView.getWidth(), imageView.getHeight());
                mainHandler.post(() -> {
                    if (released || generation != loadGeneration || currentMode != MODE_IMAGE) {
                        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                        return;
                    }
                    if (bitmap == null) {
                        disableBackground();
                        return;
                    }
                    currentBitmap = bitmap;
                    imageView.setImageBitmap(bitmap);
                    imageView.setVisibility(View.VISIBLE);
                });
            });
        } else {
            videoView.setVisibility(View.VISIBLE);
            if (videoView.isAvailable()) prepareVideo();
        }
    }

    private Bitmap decodeImage(File file, int viewWidth, int viewHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int targetWidth = viewWidth > 0 ? viewWidth : Math.min(bounds.outWidth, 2048);
        int targetHeight = viewHeight > 0 ? viewHeight : Math.min(bounds.outHeight, 2048);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetWidth &&
            bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try { return BitmapFactory.decodeFile(file.getAbsolutePath(), options); }
        catch (OutOfMemoryError error) { return null; }
    }

    private void prepareVideo() {
        if (released || currentMode != MODE_VIDEO || currentFile == null || !currentFile.isFile()) {
            if (currentMode == MODE_VIDEO) disableBackground();
            return;
        }
        if (mediaPlayer != null) {
            startPreparedVideo();
            return;
        }
        SurfaceTexture texture = videoView.getSurfaceTexture();
        if (texture == null) return;
        final int generation = loadGeneration;
        try {
            videoSurface = new Surface(texture);
            mediaPlayer = new MediaPlayer();
            MediaPlayer player = mediaPlayer;
            player.setSurface(videoSurface);
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.setOnVideoSizeChangedListener((mp, width, height) -> {
                videoWidth = width;
                videoHeight = height;
                updateVideoTransform();
            });
            player.setOnPreparedListener(mp -> {
                if (released || generation != loadGeneration || currentMode != MODE_VIDEO) return;
                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();
                updateVideoTransform();
                startPreparedVideo();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                if (generation == loadGeneration) disableBackground();
                return true;
            });
            player.setDataSource(currentFile.getAbsolutePath());
            player.prepareAsync();
        } catch (Exception e) {
            disableBackground();
        }
    }

    private void startPreparedVideo() {
        if (!resumed || currentMode != MODE_VIDEO || mediaPlayer == null) return;
        try {
            if (!mediaPlayer.isPlaying()) mediaPlayer.start();
        } catch (IllegalStateException ignored) { }
    }

    private void updateVideoTransform() {
        int width = videoView.getWidth();
        int height = videoView.getHeight();
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return;
        float scale = Math.max((float) width / videoWidth, (float) height / videoHeight);
        float scaledWidth = videoWidth * scale;
        float scaledHeight = videoHeight * scale;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((width - scaledWidth) / 2f, (height - scaledHeight) / 2f);
        videoView.setTransform(matrix);
    }

    private void updatePropertyOverlayVisibility() {
        propertyOverlay.setVisibility(currentMode != MODE_OFF &&
            android.graphics.Color.alpha(backgroundOverlayColor) != 0 ? View.VISIBLE : View.GONE);
    }

    private void applyDim() {
        int dim = Math.max(0, Math.min(100, preferences.getInt(PREF_DIM, 0)));
        dimOverlay.setAlpha(dim / 100f);
        dimOverlay.setVisibility(dim == 0 || currentMode == MODE_OFF ? View.GONE : View.VISIBLE);
    }

    private void disableBackground() {
        if (released) return;
        preferences.edit().putInt(PREF_MODE, MODE_OFF)
            .remove(PREF_FILE).apply();
        currentMode = MODE_OFF;
        currentFile = null;
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.GONE);
        propertyOverlay.setVisibility(View.GONE);
        dimOverlay.setVisibility(View.GONE);
        releasePlayer();
        clearImage();
    }

    private void clearImage() {
        imageView.setImageDrawable(null);
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        currentBitmap = null;
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.setOnPreparedListener(null); mediaPlayer.setOnErrorListener(null); mediaPlayer.release(); }
            catch (Exception ignored) { }
            mediaPlayer = null;
        }
        if (videoSurface != null) {
            try { videoSurface.release(); } catch (Exception ignored) { }
            videoSurface = null;
        }
        videoWidth = 0;
        videoHeight = 0;
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        updateVideoTransform();
        if (currentMode == MODE_VIDEO) prepareVideo();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { updateVideoTransform(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { releasePlayer(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
}
