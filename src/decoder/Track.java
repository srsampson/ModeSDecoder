/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public final class Track {

    private final TrackNumber trackNumber;              // The network global track number
    private Target target;                              // The target data object for this track

    public Track(TrackNumber tn, Target tgt) {
        trackNumber = tn;
        target = tgt;
    }

    public Target getTarget() {
        return target;
    }

    public void updateTarget(Target val) {
        target = val;
    }

    public TrackNumber getTrackNumber() {
        return trackNumber;
    }
}
