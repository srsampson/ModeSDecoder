/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

/*
 * The DataBlock structure is the raw target data detected at the serial port.
 */
public final class DataBlock {

    private final long time;
    private final int signalLevel;
    private final String data;
    private final String mlat;  // not used

    public DataBlock(long t, int s, String m, String d) {
        time = t;
        signalLevel = s;
        mlat = m;
        data = d;
    }

    public int getSignalLevel() {
        return signalLevel;
    }

    public String getData() {
        return data;
    }

    public String getMlat() {
        return mlat;
    }

    public long getUTCTime() {
        return time;
    }
}
