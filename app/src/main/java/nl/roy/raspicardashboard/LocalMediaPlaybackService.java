package nl.roy.raspicardashboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background local audio player using only user-selected folders. */
public final class LocalMediaPlaybackService extends Service {
    public static final String ACTION_PLAY_URI = "nl.roy.raspicardashboard.local.PLAY_URI";
    public static final String ACTION_PLAY_PAUSE = "nl.roy.raspicardashboard.local.PLAY_PAUSE";
    public static final String ACTION_NEXT = "nl.roy.raspicardashboard.local.NEXT";
    public static final String ACTION_PREVIOUS = "nl.roy.raspicardashboard.local.PREVIOUS";
    public static final String ACTION_STOP = "nl.roy.raspicardashboard.local.STOP";
    public static final String ACTION_REQUEST_STATE = "nl.roy.raspicardashboard.local.REQUEST_STATE";
    public static final String ACTION_STATE = "nl.roy.raspicardashboard.local.STATE";

    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TREE_URI = "tree_uri";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_HAS_TRACK = "has_track";

    private static final int NOTIFICATION_ID = 4104;
    private static final String CHANNEL_ID = "raspicar_local_media";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<LocalTrack> playlist = new ArrayList<>();

    private MediaPlayer player;
    private MediaSession mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private int currentIndex = -1;
    private Uri currentUri;
    private String currentTitle = "Lokale muziek";
    private String currentArtist = "Kies een muziekmap";
    private long duration;
    private int loadGeneration;

    private final Runnable progressUpdate = new Runnable() {
        @Override public void run() {
            broadcastState();
            if (player != null && player.isPlaying()) handler.postDelayed(this, 1_000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createNotificationChannel();
        createMediaSession();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_PLAY_URI.equals(action)) {
            String uriText = intent.getStringExtra(EXTRA_URI);
            String treeText = intent.getStringExtra(EXTRA_TREE_URI);
            if (uriText != null) preparePlaylistAndPlay(Uri.parse(uriText), treeText == null ? null : Uri.parse(treeText));
        } else if (ACTION_PLAY_PAUSE.equals(action)) {
            togglePlayback();
        } else if (ACTION_NEXT.equals(action)) {
            playRelative(1);
        } else if (ACTION_PREVIOUS.equals(action)) {
            if (player != null && player.getCurrentPosition() > 5_000) player.seekTo(0);
            else playRelative(-1);
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        } else if (ACTION_REQUEST_STATE.equals(action)) {
            broadcastState();
            if (currentUri == null) stopSelf();
        }
        return START_STICKY;
    }

    private void preparePlaylistAndPlay(Uri requestedUri, Uri treeUri) {
        startForegroundCompat(buildNotification("Lokale muziek voorbereiden…", false, null));
        int generation = ++loadGeneration;
        executor.execute(() -> {
            List<LocalTrack> scanned = treeUri == null ? new ArrayList<>() : LocalMediaLibrary.scan(this, treeUri);
            int selected = -1;
            for (int i = 0; i < scanned.size(); i++) {
                if (requestedUri.equals(scanned.get(i).uri)) { selected = i; break; }
            }
            if (selected < 0) {
                scanned.add(new LocalTrack(requestedUri, requestedUri.getLastPathSegment()));
                selected = scanned.size() - 1;
            }
            final int selectedIndex = selected;
            handler.post(() -> {
                if (generation != loadGeneration) return;
                playlist.clear();
                playlist.addAll(scanned);
                currentIndex = selectedIndex;
                loadCurrentTrack();
            });
        });
    }

    private void loadCurrentTrack() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        LocalTrack track = playlist.get(currentIndex);
        currentUri = track.uri;
        currentTitle = stripExtension(track.displayName);
        currentArtist = "Lokale muziek";
        duration = 0L;
        releasePlayer();
        int generation = ++loadGeneration;
        executor.execute(() -> {
            String title = currentTitle;
            String artist = currentArtist;
            long detectedDuration = 0L;
            byte[] artBytes = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, track.uri);
                String metadataTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String metadataArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String metadataDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (metadataTitle != null && !metadataTitle.isBlank()) title = metadataTitle;
                if (metadataArtist != null && !metadataArtist.isBlank()) artist = metadataArtist;
                if (metadataDuration != null) detectedDuration = Long.parseLong(metadataDuration);
                artBytes = retriever.getEmbeddedPicture();
            } catch (RuntimeException ignored) {
            } finally {
                try { retriever.release(); } catch (Exception ignored) { }
            }
            String finalTitle = title;
            String finalArtist = artist;
            long finalDuration = detectedDuration;
            byte[] finalArtBytes = artBytes;
            handler.post(() -> {
                if (generation != loadGeneration) return;
                currentTitle = finalTitle;
                currentArtist = finalArtist;
                duration = finalDuration;
                createPlayer(track.uri, finalArtBytes);
            });
        });
    }

    private void createPlayer(Uri uri, byte[] artBytes) {
        requestAudioFocus();
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setOnPreparedListener(mp -> {
            duration = mp.getDuration();
            mp.start();
            updateMediaSession();
            startForegroundCompat(buildNotification(currentTitle, true, artBytes));
            scheduleProgress();
            broadcastState();
        });
        player.setOnCompletionListener(mp -> playRelative(1));
        player.setOnErrorListener((mp, what, extra) -> {
            currentArtist = "Bestand kon niet worden afgespeeld";
            broadcastState();
            return true;
        });
        try {
            player.setDataSource(this, uri);
            player.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            currentArtist = "Bestand kon niet worden geopend";
            broadcastState();
            stopForeground(false);
        }
    }

    private void togglePlayback() {
        if (player == null) {
            if (currentIndex >= 0) loadCurrentTrack();
            else broadcastState();
            return;
        }
        if (player.isPlaying()) player.pause();
        else {
            requestAudioFocus();
            player.start();
        }
        updateMediaSession();
        startForegroundCompat(buildNotification(currentTitle, player.isPlaying(), null));
        scheduleProgress();
        broadcastState();
    }

    private void playRelative(int delta) {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex + delta + playlist.size()) % playlist.size();
        loadCurrentTrack();
    }

    private void stopPlayback() {
        ++loadGeneration;
        releasePlayer();
        currentUri = null;
        currentIndex = -1;
        playlist.clear();
        currentTitle = "Lokale muziek";
        currentArtist = "Geen nummer geselecteerd";
        duration = 0L;
        abandonAudioFocus();
        updateMediaSession();
        broadcastState();
        stopForeground(true);
        stopSelf();
    }

    private void scheduleProgress() {
        handler.removeCallbacks(progressUpdate);
        handler.post(progressUpdate);
    }

    private void broadcastState() {
        Intent state = new Intent(ACTION_STATE).setPackage(getPackageName());
        state.putExtra(EXTRA_URI, currentUri == null ? null : currentUri.toString());
        state.putExtra(EXTRA_TITLE, currentTitle);
        state.putExtra(EXTRA_ARTIST, currentArtist);
        state.putExtra(EXTRA_DURATION, duration);
        int position = 0;
        boolean playing = false;
        if (player != null) {
            try {
                position = player.getCurrentPosition();
                playing = player.isPlaying();
            } catch (IllegalStateException ignored) { }
        }
        state.putExtra(EXTRA_POSITION, (long) position);
        state.putExtra(EXTRA_PLAYING, playing);
        state.putExtra(EXTRA_HAS_TRACK, currentUri != null);
        sendBroadcast(state);
    }

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "RaspiCarLocalMedia");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { togglePlayback(); }
            @Override public void onPause() { togglePlayback(); }
            @Override public void onSkipToNext() { playRelative(1); }
            @Override public void onSkipToPrevious() { playRelative(-1); }
            @Override public void onStop() { stopPlayback(); }
        });
        mediaSession.setActive(true);
        updateMediaSession();
    }

    private void updateMediaSession() {
        if (mediaSession == null) return;
        int state = player != null && player.isPlaying()
                ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        long position = player == null ? 0 : safePosition();
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP)
                .setState(currentUri == null ? PlaybackState.STATE_NONE : state, position, 1f)
                .build());
        mediaSession.setMetadata(new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, duration)
                .build());
    }

    private long safePosition() {
        try { return player == null ? 0L : player.getCurrentPosition(); }
        catch (IllegalStateException e) { return 0L; }
    }

    private Notification buildNotification(String title, boolean playing, byte[] artBytes) {
        Intent dashboardIntent = new Intent(this, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent dashboardPending = PendingIntent.getActivity(this, 0, dashboardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent previous = servicePending(ACTION_PREVIOUS, 1);
        PendingIntent toggle = servicePending(ACTION_PLAY_PAUSE, 2);
        PendingIntent next = servicePending(ACTION_NEXT, 3);
        Bitmap art = artBytes == null ? null : BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_music)
                .setContentTitle(title)
                .setContentText(currentArtist)
                .setContentIntent(dashboardPending)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_music), "Vorige", previous).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_music), playing ? "Pauze" : "Afspelen", toggle).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_music), "Volgende", next).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        if (art != null) builder.setLargeIcon(art);
        return builder.build();
    }

    private PendingIntent servicePending(String action, int requestCode) {
        Intent intent = new Intent(this, LocalMediaPlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lokale muziek",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Afspeelbediening voor lokale muziek in RaspiCar");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener(change -> {
                            if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && player != null && player.isPlaying()) {
                                player.pause();
                                updateMediaSession();
                                broadcastState();
                            }
                        }).build();
            }
            audioManager.requestAudioFocus(focusRequest);
        } else {
            //noinspection deprecation
            audioManager.requestAudioFocus(change -> { }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
    }

    private void releasePlayer() {
        handler.removeCallbacks(progressUpdate);
        if (player != null) {
            try { player.stop(); } catch (RuntimeException ignored) { }
            try { player.release(); } catch (RuntimeException ignored) { }
            player = null;
        }
    }

    private String stripExtension(String name) {
        if (name == null || name.isBlank()) return "Onbekend nummer";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override public void onDestroy() {
        ++loadGeneration;
        releasePlayer();
        abandonAudioFocus();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
