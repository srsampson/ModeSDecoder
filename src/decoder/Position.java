/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public final class Position implements IPosition, IConstants {

    private final String icao;        // ICAO ID
    //
    private long utctime;             // last update time (even or odd)
    private long ptime;               // processing time abs(evenframe - oddframe)
    private long evenframeTime;       // even frame time
    private long oddframeTime;        // odd frame time
    //
    private int latEven;             // 17 bit binary
    private int latOdd;
    private int lonEven;
    private int lonOdd;
    //
    private boolean timedout;

    // Constructor
    public Position(String ac, long time, int lat, int lon, boolean cpr1) {
        timedout = false;
        icao = ac;
        ptime = 0L;
        utctime = time;

        // The new frame will either be even or odd when new object is created
        if (cpr1 == ODD) {
            // odd frame

            latOdd = lat;
            lonOdd = lon;
            evenframeTime = 0;
            oddframeTime = time;
        } else {
            // even frame

            latEven = lat;
            lonEven = lon;
            oddframeTime = 0;
            evenframeTime = time;
        }
    }

    @Override
    public long getOddFrameTime() {
        return oddframeTime;
    }

    @Override
    public long getEvenFrameTime() {
        return evenframeTime;
    }

    @Override
    public void setLatLonOdd(int lat, int lon, long time) {
        latOdd = lat;
        lonOdd = lon;
        utctime = oddframeTime = time;
    }

    @Override
    public void setLatLonEven(int lat, int lon, long time) {
        latEven = lat;
        lonEven = lon;
        utctime = evenframeTime = time;
    }

    @Override
    public long getProcessTime() {
        return ptime;
    }

    @Override
    public void setProcessTime(long val) {
        ptime = val;
    }

    @Override
    public String getICAO() {
        return icao;
    }

    @Override
    public long getUpdateTime() {
        return utctime;
    }

    @Override
    public int getLatEven() {
        return latEven;
    }

    @Override
    public int getLatOdd() {
        return latOdd;
    }

    @Override
    public int getLonEven() {
        return lonEven;
    }

    @Override
    public int getLonOdd() {
        return lonOdd;
    }

    @Override
    public boolean getTimedOut() {
        return timedout;
    }

    @Override
    public void setTimedOut(boolean val) {
        timedout = val;
    }
}
