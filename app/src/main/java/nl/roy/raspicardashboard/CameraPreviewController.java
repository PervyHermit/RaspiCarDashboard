package nl.roy.raspicardashboard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;
import java.util.Comparator;

/** Lightweight Camera2 preview with preserved aspect ratio and user-selectable fit/fill. */
public final class CameraPreviewController {
    public interface Listener {
        void onCameraStatus(String status, boolean isError);
    }

    public static final String SCALE_FIT = "fit";
    public static final String SCALE_FILL = "fill";
    public static final String ASPECT_AUTO = "auto";
    public static final String ASPECT_4_3 = "4:3";
    public static final String ASPECT_16_9 = "16:9";

    private final Activity activity;
    private final TextureView textureView;
    private final Listener listener;
    private final CameraManager cameraManager;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Surface previewSurface;
    private String pendingCameraId;
    private String scaleMode = SCALE_FIT;
    private String aspectMode = ASPECT_AUTO;
    private boolean mirror;
    private int rotation;
    private volatile boolean requested;
    private volatile boolean openingCamera;
    private volatile int openGeneration;
    private Size previewSize;

    public CameraPreviewController(Activity activity, TextureView textureView, Listener listener) {
        this.activity = activity;
        this.textureView = textureView;
        this.listener = listener;
        this.cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        this.textureView.setSurfaceTextureListener(surfaceListener);
    }

    public void start(String cameraId, boolean mirror, int rotation) {
        start(cameraId, mirror, rotation, SCALE_FIT, ASPECT_AUTO);
    }

    public void start(String cameraId, boolean mirror, int rotation, String scaleMode, String aspectMode) {
        String normalizedId = cameraId == null || cameraId.trim().isEmpty() ? null : cameraId;
        boolean cameraChanged = pendingCameraId != null && normalizedId != null
                && !pendingCameraId.equals(normalizedId);
        requested = true;
        pendingCameraId = normalizedId;
        this.mirror = mirror;
        this.rotation = normalizeRotation(rotation);
        this.scaleMode = SCALE_FILL.equals(scaleMode) ? SCALE_FILL : SCALE_FIT;
        this.aspectMode = ASPECT_4_3.equals(aspectMode) || ASPECT_16_9.equals(aspectMode)
                ? aspectMode : ASPECT_AUTO;
        ensureThread();
        if (cameraChanged && (cameraDevice != null || openingCamera)) {
            openGeneration++;
            openingCamera = false;
            closeCameraAsync(false);
        }
        if (cameraDevice != null) {
            applyTransform();
            return;
        }
        if (textureView.isAvailable()) openSelectedCamera();
        else notifyStatus("Camera voorbereiden…", false);
    }

    public void stop() {
        requested = false;
        openGeneration++;
        openingCamera = false;
        closeCameraAsync(true);
    }

    public String findBestCameraId() {
        if (cameraManager == null) return null;
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length == 0) return null;
            for (String id : ids) {
                try {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return id;
                } catch (CameraAccessException | RuntimeException ignored) {
                    // Some USB providers expose stale IDs; keep checking the remaining devices.
                }
            }
            for (String id : ids) {
                try {
                    cameraManager.getCameraCharacteristics(id);
                    return id;
                } catch (CameraAccessException | RuntimeException ignored) {
                    // Skip unusable camera IDs instead of failing the whole camera screen.
                }
            }
            return null;
        } catch (CameraAccessException | RuntimeException e) {
            return null;
        }
    }

    private void ensureThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("RaspiCarCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void openSelectedCamera() {
        Handler handler = cameraHandler;
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.post(this::openSelectedCamera);
            return;
        }
        if (!requested || cameraDevice != null || openingCamera) return;
        if (cameraManager == null) {
            notifyStatus("Cameraservice niet beschikbaar", true);
            return;
        }
        if (cameraHandler == null) {
            ensureThread();
            if (cameraHandler == null) {
                notifyStatus("Cameraproces kon niet starten", true);
                return;
            }
        }
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            notifyStatus("Cameratoestemming ontbreekt", true);
            return;
        }
        String cameraId = pendingCameraId;
        if (cameraId == null || cameraId.trim().isEmpty()) cameraId = findBestCameraId();
        if (cameraId == null) {
            notifyStatus("Geen camera gevonden", true);
            return;
        }
        pendingCameraId = cameraId;
        final int generation = ++openGeneration;
        openingCamera = true;
        try {
            notifyStatus("Camera openen…", false);
            cameraManager.openCamera(cameraId, createStateCallback(generation), cameraHandler);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            if (generation == openGeneration) openingCamera = false;
            notifyStatus("Camera kon niet worden geopend", true);
        } catch (RuntimeException e) {
            if (generation == openGeneration) openingCamera = false;
            notifyStatus("Cameraservice gaf een fout", true);
        }
    }

    private void createPreviewSession(int generation) {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            previewSize = choosePreviewSize(pendingCameraId,
                    Math.max(1, textureView.getWidth()), Math.max(1, textureView.getHeight()));
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            applyTransform();
            previewSurface = new Surface(texture);
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            if (supportsContinuousVideoAutoFocus(pendingCameraId)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            }
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (!requested || generation != openGeneration || cameraDevice == null) {
                        try { session.close(); } catch (RuntimeException ignored) { }
                        return;
                    }
                    captureSession = session;
                    try {
                        session.setRepeatingRequest(builder.build(), null, cameraHandler);
                        notifyStatus("", false);
                    } catch (CameraAccessException | RuntimeException e) {
                        if (generation == openGeneration) {
                            notifyStatus("Camerabeeld kon niet starten", true);
                        }
                    }
                }

                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    try { session.close(); } catch (RuntimeException ignored) { }
                    if (generation == openGeneration) notifyStatus("Camera-preview mislukt", true);
                }
            }, cameraHandler);
        } catch (CameraAccessException | RuntimeException e) {
            if (generation == openGeneration) notifyStatus("Camera-preview niet beschikbaar", true);
        }
    }

    private void applyTransform() {
        if (previewSize == null || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed() || !requested) return;
            float viewWidth = textureView.getWidth();
            float viewHeight = textureView.getHeight();
            float bufferWidth = previewSize.getWidth();
            float bufferHeight = previewSize.getHeight();
            if (rotation == 90 || rotation == 270) {
                float swap = bufferWidth;
                bufferWidth = bufferHeight;
                bufferHeight = swap;
            }

            // TextureView normally stretches the buffer to fill. This matrix cancels that
            // non-uniform stretch, then applies either aspect-fit or aspect-fill.
            float defaultScaleX = viewWidth / bufferWidth;
            float defaultScaleY = viewHeight / bufferHeight;
            float uniformScale = SCALE_FILL.equals(scaleMode)
                    ? Math.max(defaultScaleX, defaultScaleY)
                    : Math.min(defaultScaleX, defaultScaleY);
            float correctionX = uniformScale / defaultScaleX;
            float correctionY = uniformScale / defaultScaleY;

            Matrix matrix = new Matrix();
            float centerX = viewWidth / 2f;
            float centerY = viewHeight / 2f;
            matrix.postScale(correctionX, correctionY, centerX, centerY);
            if (mirror) matrix.postScale(-1f, 1f, centerX, centerY);
            if (rotation != 0) matrix.postRotate(rotation, centerX, centerY);
            textureView.setTransform(matrix);
        });
    }

    private boolean supportsContinuousVideoAutoFocus(String cameraId) {
        if (cameraManager == null) return false;
        try {
            int[] modes = cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if (modes == null) return false;
            for (int mode : modes) if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) return true;
        } catch (CameraAccessException | RuntimeException ignored) { }
        return false;
    }

    private Size choosePreviewSize(String cameraId, int targetWidth, int targetHeight) throws CameraAccessException {
        if (cameraManager == null) return new Size(640, 480);
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return new Size(640, 480);
        Size[] choices = map.getOutputSizes(SurfaceTexture.class);
        if (choices == null || choices.length == 0) return new Size(640, 480);
        double targetRatio;
        if (ASPECT_4_3.equals(aspectMode)) targetRatio = 4.0 / 3.0;
        else if (ASPECT_16_9.equals(aspectMode)) targetRatio = 16.0 / 9.0;
        else targetRatio = (double) targetWidth / Math.max(1, targetHeight);
        return Arrays.stream(choices)
                .filter(size -> size.getWidth() <= 1920 && size.getHeight() <= 1080)
                .min(Comparator.comparingDouble(size -> {
                    double ratio = (double) size.getWidth() / size.getHeight();
                    double ratioPenalty = Math.abs(ratio - targetRatio) * 1500.0;
                    double sizePenalty = Math.abs(size.getWidth() - targetWidth)
                            + Math.abs(size.getHeight() - targetHeight);
                    return ratioPenalty + sizePenalty;
                }))
                .orElse(choices[0]);
    }

    private int normalizeRotation(int value) {
        int normalized = ((value % 360) + 360) % 360;
        if (normalized < 45) return 0;
        if (normalized < 135) return 90;
        if (normalized < 225) return 180;
        if (normalized < 315) return 270;
        return 0;
    }

    private void closeCameraAsync(boolean stopThread) {
        CameraCaptureSession session = captureSession;
        CameraDevice device = cameraDevice;
        Surface surface = previewSurface;
        captureSession = null;
        cameraDevice = null;
        previewSurface = null;
        previewSize = null;

        Handler handler = cameraHandler;
        HandlerThread thread = cameraThread;
        if (stopThread) {
            cameraHandler = null;
            cameraThread = null;
        }
        Runnable release = () -> {
            releaseResources(session, device, surface);
            if (stopThread && thread != null) thread.quitSafely();
        };
        if (handler == null || !handler.post(release)) release.run();
    }

    private static void releaseResources(CameraCaptureSession session, CameraDevice device, Surface surface) {
        if (session != null) {
            try { session.close(); } catch (RuntimeException ignored) { }
        }
        if (device != null) {
            try { device.close(); } catch (RuntimeException ignored) { }
        }
        if (surface != null) {
            try { surface.release(); } catch (RuntimeException ignored) { }
        }
    }

    private void notifyStatus(String status, boolean error) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try { listener.onCameraStatus(status, error); }
            catch (RuntimeException ignored) { }
        });
    }

    private CameraDevice.StateCallback createStateCallback(final int generation) {
        return new CameraDevice.StateCallback() {
            @Override public void onOpened(CameraDevice camera) {
                if (generation != openGeneration || !requested) {
                    try { camera.close(); } catch (RuntimeException ignored) { }
                    return;
                }
                openingCamera = false;
                if (generation != openGeneration || !requested) {
                    try { camera.close(); } catch (RuntimeException ignored) { }
                    return;
                }
                cameraDevice = camera;
                createPreviewSession(generation);
            }

            @Override public void onDisconnected(CameraDevice camera) {
                try { camera.close(); } catch (RuntimeException ignored) { }
                if (generation != openGeneration) return;
                openingCamera = false;
                cameraDevice = null;
                notifyStatus("Camera losgekoppeld", true);
            }

            @Override public void onError(CameraDevice camera, int error) {
                try { camera.close(); } catch (RuntimeException ignored) { }
                if (generation != openGeneration) return;
                openingCamera = false;
                cameraDevice = null;
                notifyStatus("Camera is bezet of niet beschikbaar", true);
            }
        };
    }

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (requested) openSelectedCamera();
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            applyTransform();
        }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            openGeneration++;
            openingCamera = false;
            closeCameraAsync(false);
            return true;
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };
}
