/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the DownlinkFormat20 object
 *
 * DF20 is used to reply to ground interrogations with altitude information
 */
public final class DownlinkFormat20 implements IDF20 {

    private final Altitude alt;
    private final CRC crc;
    private final Callsign call;
    private int altitude;
    private int fs3;
    private int dr5;
    private int um6;
    private int ids2;
    private int iis4;
    private String acid;
    private String callsign;
    private long timestamp;
    private final long data56;
    private final int[] dataBytes;
    private final int bds;
    private boolean isOnGround;
    private boolean isAlert;
    private boolean isSPI;
    private boolean isEmergency;

    /**
     * Decode the DF20 packets
     *
     * @param raw112 a string of the raw 112-bit packet in hexadecimal
     * @param time a long representing the UTC time of detection
     */
    public DownlinkFormat20(String raw112, long time) {
        alt = new Altitude();
        crc = new CRC();
        call = new Callsign();
        dataBytes = new int[7];

        timestamp = time;
        altitude = alt.decodeAltitude(raw112.substring(0, 8), true);    // true ==  has Metre bit

        /*
         * By running the first 88 bits into the CRC, and then XOR the last 24
         * bits, you arrive at the true 6 hex digit Aircraft ID
         *
         * This is effected by interference and garbled bits, so you have to
         * validate this ACID with the list of DF11,DF17, and DF18 packets.
         */
        acid = crc.crcCompute(raw112);

        fs3 = Integer.parseInt(raw112.substring(1, 2), 16) & 0x07;
        dr5 = ((Integer.parseInt(raw112.substring(2, 4), 16) & 0xF8) >>> 3) & 0x1F; // DR 5 bits
        um6 = (Integer.parseInt(raw112.substring(3, 5), 16) >> 1) & 0x3F;    // UM 6 bits
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

        int loop = 0;
        for (int j = 8; j < 22; j += 2) {
            dataBytes[loop++] = Integer.parseInt(raw112.substring(j, j + 2), 16);   // MB
        }
        
        data56 = ((long) dataBytes[0] << 48)
                | ((long) dataBytes[1] << 40)
                | ((long) dataBytes[2] << 32)
                | ((long) dataBytes[3] << 24)
                | ((long) dataBytes[4] << 16)
                | ((long) dataBytes[5] << 8)
                | ((long) dataBytes[6]);

        bds = dataBytes[0];

        if (bds == 0x20) {
            callsign = call.callsignDecode(data56);
        }
    }

    @Override
    public String getCallsign() {
        return callsign;
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

    public String getACID() {
        return acid;
    }

    @Override
    public int getAltitude() {
        return altitude;
    }

    @Override
    public long getUpdateTime() {
        return timestamp;
    }
}
