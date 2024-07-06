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

    public DataBlock(long time, int signal, String mlat, String data) {
        this.sqlTime = new Timestamp(0L);
        this.time = time;
        this.sqlTime.setTime(time);
        this.timestamp = this.sqlTime.toString();
        this.signalLevel = signal;
        this.mlat = mlat;
        this.data = data;
    }

    public int getSignalLevel() {
        return this.signalLevel;
    }

    public String getData() {
        return this.data;
    }

    public String getMlat() {
        return this.mlat;
    }

    public long getUTCTime() {
        return this.time;
    }

    public String getTimestamp() {
        return this.timestamp;
    }
}
