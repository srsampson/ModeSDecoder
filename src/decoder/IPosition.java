/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public interface IPosition {

    /**
     * Getter for Odd Frame UTC time in milliseconds
     *
     * @return a long representing UTC Odd frame time in milliseconds
     */
    public long getOddFrameTime();

    /**
     * Getter for Even Frame UTC time in milliseconds
     *
     * @return a long representing UTC Even frame time in milliseconds
     */
    public long getEvenFrameTime();

    /**
     * Getter for Frame Processing UTC time in milliseconds
     *
     * The Even and Odd lat/lon packets must be received within MAXTIME seconds
     * of each other, so this is the time delta.
     *
     * @return a long representing odd/even delta time in milliseconds
     */
    public long getProcessTime();

    public void setLatLonOdd(int lat, int lon, long time);

    public void setLatLonEven(int lat, int lon, long time);

    public void setProcessTime(long val);

    public String getACID();

    public long getUpdateTime();

    public int getLatEven();

    public int getLatOdd();

    public int getLonEven();

    public int getLonOdd();
    
    public boolean getTimedOut();

    public void setTimedOut(boolean var);
}
