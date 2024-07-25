/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

/*
 * The DataBlock structure is the raw target data detected at the serial port
 * that are 56-bit and 112-bit Mode-S Packets.
 *
 * Short Blocks have a hash code, as they are duplicate filtered.
 * Long Blocks do not have a hash code, as they are not duplicate filtered.
 */
public final class DataBlock {

    public static final int SHORTBLOCK = 0;
    public static final int LONGBLOCK = 1;
    //
    private final long time;
    private final int signalLevel;
    private final String dataHash;
    private final String data;
    private final int blockType;

    public DataBlock(int bt, long t, int s, String d) {
        blockType = bt;
        time = t;
        signalLevel = s;
        data = d;
        dataHash = "";
    }

    public DataBlock(int bt, long t, int s, String d, String dh) {
        blockType = bt;
        time = t;
        signalLevel = s;
        data = d;
        dataHash = dh;
    }

    public int getBlockType() {
        return blockType;
    }
    
    public int getSignalLevel() {
        return signalLevel;
    }

    public String getDataHash() {
        return dataHash;
    }
    
    public String getData() {
        return data;
    }

    public long getUTCTime() {
        return time;
    }
}