package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider;

/**
 * SHUFFLEPOD: a larger audio output buffer (1 to 1.5 s instead of 0.25 to 0.75 s). Speed changes and skip silence
 * are processed on the phone before this buffer, so with the screen off (slower CPU, e.g. on Android Auto) a short
 * delay in that processing no longer runs the buffer dry and makes playback choppy.
 */
@OptIn(markerClass = UnstableApi.class)
class ShufflepodRenderersFactory extends DefaultRenderersFactory {
    private static final int MIN_BUFFER_US = 1_000_000;
    private static final int MAX_BUFFER_US = 1_500_000;

    ShufflepodRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput,
                                       boolean enableAudioTrackPlaybackParams) {
        return new DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioTrackBufferSizeProvider(new DefaultAudioTrackBufferSizeProvider.Builder()
                        .setMinPcmBufferDurationUs(MIN_BUFFER_US)
                        .setMaxPcmBufferDurationUs(MAX_BUFFER_US)
                        .build())
                .build();
    }
}
