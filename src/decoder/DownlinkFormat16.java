/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the DownlinkFormat16 object
 *
 * DF16 is used for TCAS
 */
public final class DownlinkFormat16 implements IDF16 {

    private final CRC crc;
    private final Altitude alt;
    private int altitude;
    private final int ri4;
    private final long timestamp;
    private String acid;
    private boolean crosslinkCapable;
    private final boolean isOnGround;
    private final boolean cc1;
    private final long data56;
    private final int[] dataBytes;
    private final int bds;

    /**
     * Decode the DF16 packets
     *
     * @param raw56 a string of the raw 56-bit packet in hexadecimal
     * @param time a long representing the UTC time of detection
     */
    public DownlinkFormat16(String raw56, long time) {
        crc = new CRC();
        alt = new Altitude();
        dataBytes = new int[7];
        acid = "";
        altitude = -9999;

        timestamp = time;
        altitude = alt.decodeAltitude(raw56.substring(0, 8), true);    // true == has the Metre Bit

        /*
         * By running the first 32 bits into the CRC, and then XOR the last 24
         * bits, you arrive at the true 6 hex digits Aircraft ID
         *
         * This is effected by interference and garbled bits, so you have to
         * validate this ACID with the list of DF11,DF17, and DF18 packets.
         */
        acid = crc.crcCompute(raw56);

        /*
         * The second hex digit is the vs1 and cc1 bits 0XX0
         */
        int tmp = Integer.parseInt(raw56.substring(1, 2), 16);
        isOnGround = ((tmp & 0x04) == 0x04);                      // true if vs1 == 1
        cc1 = ((tmp & 0x02) == 0x02);                             // true if cc1 == 1

        /*
         * The 4th and 5th hex digits contain the ri4 bits 0XXX X000
         */
        tmp = Integer.parseInt(raw56.substring(3, 5), 16);
        ri4 = (tmp >>> 3) & 0x0F;

        int loop = 0;
        for (int j = 8; j < 22; j += 2) {
            dataBytes[loop++] = Integer.parseInt(raw56.substring(j, j + 2), 16);   // MV
        }

        data56 = ((long) dataBytes[0] << 48)
                | ((long) dataBytes[1] << 40)
                | ((long) dataBytes[2] << 32)
                | ((long) dataBytes[3] << 24)
                | ((long) dataBytes[4] << 16)
                | ((long) dataBytes[5] << 8)
                | ((long) dataBytes[6]);

        bds = dataBytes[0];
    }

    @Override
    public int getBDS() {
        return bds;
    }
    
    @Override
    public long getData56() {
        return data56;
    }

    @Override
    public boolean getCC1() {
        return cc1;
    }

    @Override
    public int getRI4() {
        return ri4;
    }

    @Override
    public boolean getCrosslinkCapable() {
        return crosslinkCapable;
    }

    @Override
    public boolean getIsOnGround() {
        return isOnGround;
    }

    @Override
    public int getCapability() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getAltitude() {
        return altitude;
    }

    public String getACID() {
        return acid;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
