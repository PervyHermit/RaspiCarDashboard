package nl.roy.raspicardashboard;

import android.service.notification.NotificationListenerService;

/**
 * Android requires an enabled NotificationListenerService before a normal app
 * can inspect active MediaSession controllers from Spotify, VLC, etc.
 */
public final class MediaNotificationListener extends NotificationListenerService {
}
