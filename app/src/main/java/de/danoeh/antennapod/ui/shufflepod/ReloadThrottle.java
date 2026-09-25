package de.danoeh.antennapod.ui.shufflepod;

import android.os.Handler;
import android.os.Looper;

/**
 * Runs a reload at most once per interval, however many change events arrive. During a refresh of
 * hundreds of feeds, screens otherwise reload once per feed.
 */
public class ReloadThrottle {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long intervalMillis;
    private final Runnable reload;
    private boolean pending = false;
    private final Runnable run = new Runnable() {
        @Override
        public void run() {
            pending = false;
            reload.run();
        }
    };

    public ReloadThrottle(long intervalMillis, Runnable reload) {
        this.intervalMillis = intervalMillis;
        this.reload = reload;
    }

    public void request() {
        if (!pending) {
            pending = true;
            handler.postDelayed(run, intervalMillis);
        }
    }

    public void cancel() {
        handler.removeCallbacks(run);
        pending = false;
    }
}
