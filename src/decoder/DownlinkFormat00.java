/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the DownlinkFormat00 object
 *
 * DF00 is used with TCAS/ACAS to exchange altitude information
 */
public final class DownlinkFormat00 implements IDF00 {

    private final CRC crc;
    private final Altitude alt;
    private int altitude;
    private final int ri4;
    private final long timestamp;
    private String icao;
    private boolean crosslinkCapable;
    private final boolean isOnGround;
    private final boolean cc1;

    /**
     * Decode the DF00 packets
     *
     * @param raw56 a string of the raw 14 hex packet
     * @param time a long representing the UTC time of detection
     */
    public DownlinkFormat00(String raw56, long time) {
        crc = new CRC();
        alt = new Altitude();

        timestamp = time;
        icao = "";
        altitude = -9999;

        // remove AP hex
        altitude = alt.decodeAltitude(raw56.substring(0, 8), true);    // true == has the Metre Bit
        
        /*
         * By running the first 32 bits into the CRC, and then XOR the last 24
         * bits, you arrive at the true 6 hex digits Aircraft ID
         *
         * This is effected by interference and garbled bits, so you have to
         * validate this ACID with the list of DF11,DF17, and DF18 packets.
         */
        icao = crc.crcCompute(raw56);

        /*
         * The second hex digit is the vs1 and cc1 bits 0XX0
         */
        int tmp = Integer.parseInt(raw56.substring(1, 2), 16);
        isOnGround = ((tmp & 0x04) == 0x04);             // true if vs1 == 1
        cc1 = ((tmp & 0x02) == 0x02);                    // true if cc1 == 1

        /*
         * The 4th and 5th hex digits contain the ri4 bits 0XXX X000
         */
        tmp = Integer.parseInt(raw56.substring(3, 5), 16);
        ri4 = (tmp >>> 3) & 0x0F;
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

    public String getICAO() {
        return icao;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
