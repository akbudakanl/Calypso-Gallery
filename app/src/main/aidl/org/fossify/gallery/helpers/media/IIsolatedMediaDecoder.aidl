package org.fossify.gallery.helpers.media;

import org.fossify.gallery.helpers.media.IsolatedBoundsResult;
import org.fossify.gallery.helpers.media.IsolatedDecodeRequest;
import org.fossify.gallery.helpers.media.IsolatedDecodeResult;
import org.fossify.gallery.helpers.media.IsolatedGifFrameResult;
import org.fossify.gallery.helpers.media.IsolatedGifOpenResult;
import org.fossify.gallery.helpers.media.IsolatedRegionOpenResult;

interface IIsolatedMediaDecoder {
    IsolatedBoundsResult probeBounds(in IsolatedDecodeRequest request);
    IsolatedDecodeResult decodeStatic(in IsolatedDecodeRequest request);
    IsolatedRegionOpenResult openRegionSession(in IsolatedDecodeRequest request);
    IsolatedDecodeResult decodeRegionTile(
        long sessionId,
        int left,
        int top,
        int right,
        int bottom,
        int sampleSize
    );
    void closeRegionSession(long sessionId);
    IsolatedGifOpenResult openGifSession(in IsolatedDecodeRequest request);
    IsolatedGifFrameResult renderGifFrame(long sessionId, int frameIndex, int targetSlot);
    void closeGifSession(long sessionId);
}
