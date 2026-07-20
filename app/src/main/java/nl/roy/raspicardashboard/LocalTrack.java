package nl.roy.raspicardashboard;

import android.net.Uri;

/** A local audio item discovered through Android's Storage Access Framework. */
public final class LocalTrack {
    public final Uri uri;
    public final String displayName;

    public LocalTrack(Uri uri, String displayName) {
        this.uri = uri;
        this.displayName = displayName;
    }
}
