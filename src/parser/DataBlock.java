/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import java.sql.Timestamp;

/*
 * The DataBlock structure is the raw target data detected at the serial port.
 */
public final class DataBlock {

    private final Timestamp sqlTime;
    private final long time;
    private final int signalLevel;
    private final String data;
    private final String mlat;
    private final String timestamp;

    public DataBlock(long t, int s, String m, String d) {
        sqlTime = new Timestamp(0L);
        time = t;
        sqlTime.setTime(t);
        timestamp = sqlTime.toString();
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

    public String getTimestamp() {
        return timestamp;
    }
}
