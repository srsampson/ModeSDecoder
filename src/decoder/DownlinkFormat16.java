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
    private final int bds;
    private final long timestamp;
    private final String icao;
    private final boolean isOnGround;
    private final long mv56;
    private final int[] dataBytes;

    /**
     * Decode the DF16 packets
     *
     * +-------+------+---+------+---+-------+-------+-------+
     * | 10000 | VS:1 | 7 | RI:4 | 2 | AC:13 | MV:56 | AP:24 |
     * +-------+------+---+------+---+-------+-------+-------+
     * 
     * @param raw112 a string of the raw 112-bit packet in hexadecimal
     * @param time a long representing the UTC time of detection
     */
    public DownlinkFormat16(String raw112, long time) {
        crc = new CRC();
        alt = new Altitude();
        dataBytes = new int[7];
        altitude = -9999;
        timestamp = time;

        // Send the first 32 bits/8 nibbles (altitude is the last 13 bits)
        altitude = alt.decodeAltitude(raw112.substring(0, 8), true);    // true == has the Metre Bit
        
        /*
         * By running the first 32 bits into the CRC, and then XOR the last 24
         * bits, you arrive at the true 6 hex digits Aircraft ID
         *
         * This is effected by interference and garbled bits, so you have to
         * validate this ACID with the list of DF11,DF17, and DF18 packets.
         */
        icao = crc.crcCompute(raw112);

        /*
         * The second hex digit is the vs1 0X00
         */
        int tmp = Integer.parseInt(raw112.substring(1, 2), 16);
        isOnGround = ((tmp & 0x04) == 0x04);                      // true if vs1 == 1

        /*
         * The 4th and 5th hex digits contain the ri4 bits 0XXX X000
         *
         * 0000 : No operating ACAS
         * 0010 : ACAS with resolution capability inhibited
         * 0011 : ACAS with vertical-only resolution capability
         * 0111 : ACAS with vertical and horizontal resolution capability
         */
        tmp = Integer.parseInt(raw112.substring(3, 5), 16);
        ri4 = (tmp >>> 3) & 0x0F;

        int loop = 0;
        for (int j = 8; j < 22; j += 2) {
            dataBytes[loop++] = Integer.parseInt(raw112.substring(j, j + 2), 16);   // MV
        }

        mv56 = ((long) dataBytes[0] << 48)
                | ((long) dataBytes[1] << 40)
                | ((long) dataBytes[2] << 32)
                | ((long) dataBytes[3] << 24)
                | ((long) dataBytes[4] << 16)
                | ((long) dataBytes[5] << 8)
                | ((long) dataBytes[6]);
        
        bds = dataBytes[0];
    }

    @Override
    public long getMV() {
        return mv56;
    }

    @Override
    public int getBDS() {
        return bds;
    }

    @Override
    public int getRI4() {
        return ri4;
    }

    @Override
    public boolean getIsOnGround() {
        return isOnGround;
    }

    @Override
    public int getAltitude() {
        return altitude;
    }

    public String getICAO() {
        return icao;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
