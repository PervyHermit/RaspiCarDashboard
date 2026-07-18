package nl.roy.raspicardashboard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
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
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;
import java.util.Comparator;

/** Lightweight Camera2 preview for the selected built-in or USB camera. */
public final class CameraPreviewController {
    public interface Listener {
        void onCameraStatus(String status, boolean isError);
    }

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
    private boolean requested;

    public CameraPreviewController(Activity activity, TextureView textureView, Listener listener) {
        this.activity = activity;
        this.textureView = textureView;
        this.listener = listener;
        this.cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        this.textureView.setSurfaceTextureListener(surfaceListener);
    }

    public void start(String cameraId, boolean mirror, int rotation) {
        requested = true;
        pendingCameraId = cameraId;
        textureView.setScaleX(mirror ? -1f : 1f);
        textureView.setRotation(rotation);
        ensureThread();
        if (textureView.isAvailable()) openSelectedCamera();
        else notifyStatus("Camera voorbereiden…", false);
    }

    public void stop() {
        requested = false;
        closeCamera();
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try {
                cameraThread.join(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cameraThread = null;
            cameraHandler = null;
        }
    }

    public String findBestCameraId() {
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length == 0) return null;
            for (String id : ids) {
                Integer facing = cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return id;
            }
            return ids[0];
        } catch (CameraAccessException e) {
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
        if (!requested || cameraDevice != null) return;
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
        try {
            notifyStatus("Camera openen…", false);
            cameraManager.openCamera(cameraId, stateCallback, cameraHandler);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            notifyStatus("Camera kon niet worden geopend", true);
        }
    }

    private void createPreviewSession() {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            Size size = choosePreviewSize(pendingCameraId,
                    Math.max(1, textureView.getWidth()), Math.max(1, textureView.getHeight()));
            texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            previewSurface = new Surface(texture);
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            if (supportsContinuousVideoAutoFocus(pendingCameraId)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            }
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        session.setRepeatingRequest(builder.build(), null, cameraHandler);
                        notifyStatus("", false);
                    } catch (CameraAccessException e) {
                        notifyStatus("Camerabeeld kon niet starten", true);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    notifyStatus("Camera-preview mislukt", true);
                }
            }, cameraHandler);
        } catch (CameraAccessException | RuntimeException e) {
            notifyStatus("Camera-preview niet beschikbaar", true);
        }
    }


    private boolean supportsContinuousVideoAutoFocus(String cameraId) {
        try {
            int[] modes = cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if (modes == null) return false;
            for (int mode : modes) {
                if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) return true;
            }
        } catch (CameraAccessException | IllegalArgumentException ignored) { }
        return false;
    }

    private Size choosePreviewSize(String cameraId, int targetWidth, int targetHeight) throws CameraAccessException {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return new Size(640, 480);
        Size[] choices = map.getOutputSizes(SurfaceTexture.class);
        if (choices == null || choices.length == 0) return new Size(640, 480);
        double targetRatio = (double) targetWidth / targetHeight;
        return Arrays.stream(choices)
                .filter(size -> size.getWidth() <= 1920 && size.getHeight() <= 1080)
                .min(Comparator.comparingDouble(size -> {
                    double ratio = (double) size.getWidth() / size.getHeight();
                    double ratioPenalty = Math.abs(ratio - targetRatio) * 1000.0;
                    double sizePenalty = Math.abs(size.getWidth() - targetWidth)
                            + Math.abs(size.getHeight() - targetHeight);
                    return ratioPenalty + sizePenalty;
                }))
                .orElse(choices[0]);
    }

    private void closeCamera() {
        if (captureSession != null) {
            try { captureSession.close(); } catch (RuntimeException ignored) { }
            captureSession = null;
        }
        if (cameraDevice != null) {
            try { cameraDevice.close(); } catch (RuntimeException ignored) { }
            cameraDevice = null;
        }
        if (previewSurface != null) {
            try { previewSurface.release(); } catch (RuntimeException ignored) { }
            previewSurface = null;
        }
    }

    private void notifyStatus(String status, boolean error) {
        activity.runOnUiThread(() -> listener.onCameraStatus(status, error));
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            if (!requested) {
                camera.close();
                return;
            }
            cameraDevice = camera;
            createPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
            notifyStatus("Camera losgekoppeld", true);
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            notifyStatus("Camera is bezet of niet beschikbaar", true);
        }
    };

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (requested) openSelectedCamera();
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            closeCamera();
            return true;
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };
}
