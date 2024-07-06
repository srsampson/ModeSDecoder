/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * A Track Number consists of a 64-bit site assigned half, and an incrementing
 * 64-bit half. A global Track Number is thus 128-bits long.
 */
public final class TrackNumber {

    private final long trackNumberHigh;       // a 64-bit fixed ID number for this site
    private static long trackNumberLow;        // a 64-bit sequential number for this site
    private final long startNumber;     // in case a number other than zero is desired

    /**
     * Instantiate starting at zero
     * @param val
     */
    public TrackNumber(long val) {
        trackNumberHigh = val;
        trackNumberLow = 0L;
        startNumber = 0L;
    }

    /**
     * Instantiate starting at non-zero
     *
     * @param val1
     * @param val2 a long representing the new track starting number
     */
    public TrackNumber(long val1, long val2) {
        trackNumberHigh = val1;
        trackNumberLow = val2;
        startNumber = val2;
    }

    /**
     * Method to return the next available track number
     *
     * @return a long representing an available track number
     */
    public synchronized long getNextTrackNumber() {
        trackNumberLow += 1L;

        return trackNumberLow;
    }

    /**
     * Method to reset the track number to the starting number
     */
    public synchronized void resetTrackNumber() {
        trackNumberLow = startNumber;
    }

    /**
     * Method to return the fixed part of the track number
     *
     * @return a long representing the site assigned track number header
     */
    public long getTrackNumberID() {
        return trackNumberHigh;
    }
}
