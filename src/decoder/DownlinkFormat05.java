/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the DownlinkFormat05 object
 *
 * DF05 is used to reply to ground interrogations with squawk information
 */
public final class DownlinkFormat05 implements IDF05 {

    private CRC crc;
    private Squawk sqk;
    private String squawk;
    private int fs3;
    private int dr5;
    private int um6;
    private int ids2;
    private int iis4;
    private String acid;
    private long timestamp;
    private boolean isOnGround;
    private boolean isAlert;
    private boolean isSPI;
    private boolean isEmergency;

    /**
     * Decode the DF05 packets
     *
     * @param raw56 a string of the raw 56-bit packet in hexadecimal
     * @param time a long representing the UTC time of detection
     */
    public DownlinkFormat05(String raw56, long time) {
        crc = new CRC();
        sqk = new Squawk();

        timestamp = time;
        squawk = sqk.decodeSquawk(raw56.substring(0, 8));

        /*
         * By running the first 32 bits into the CRC, and then XOR the last 24
         * bits, you arrive at the true 6 hex digits Aircraft ID
         *
         * This is effected by interference and garbled bits, so you have to
         * validate this ACID with the list of DF11,DF17, and DF18 packets.
         */
        acid = crc.crcCompute(raw56);

        fs3 = Integer.parseInt(raw56.substring(1, 2), 16) & 0x07;
        dr5 = ((Integer.parseInt(raw56.substring(2, 4), 16) & 0xF8) >>> 3) & 0x1F; // DR 5 bits
        um6 = (Integer.parseInt(raw56.substring(3, 5), 16) >> 1) & 0x3F;    // UM 6 bits
        
        ids2 = um6 & 0x3;
        iis4 = (um6 >>> 2) & 0xF;

        isOnGround = isAlert = isSPI = isEmergency = false;

        switch (fs3) {
            case 1:
                isOnGround = true;
                break;
            case 2:
                isAlert = true;
                break;
            case 3:
                isAlert = isOnGround = true;
                break;
            case 4:
                isAlert = isSPI = true;
                break;
            case 5:
                isSPI = true;
                break;
            case 0:
            default:
        }
    }

    @Override
    public int getFS3() {
        return fs3;
    }

    @Override
    public int getDR5() {
        return dr5;
    }

    @Override
    public int getUM6() {
        return um6;
    }

    @Override
    public int getIDS2() {
        return ids2;
    }

    @Override
    public int getIIS4() {
        return iis4;
    }

    @Override
    public boolean getIsOnGround() {
        return isOnGround;
    }

    @Override
    public boolean getIsAlert() {
        return isAlert;
    }

    @Override
    public boolean getIsSPI() {
        return isSPI;
    }

    @Override
    public boolean getIsEmergency() {
        return isEmergency;
    }

    @Override
    public String getSquawk() {
        return squawk;
    }

    public String getACID() {
        return acid;
    }

    @Override
    public long getUpdateTime() {
        return timestamp;
    }
}
