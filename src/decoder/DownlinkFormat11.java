/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the DownlinkFormat11 object
 *
 * DownlinkFormat11 is used as a Beacon AllCall transmission
 */
public final class DownlinkFormat11 implements IDF11 {

    private final CRC crc;
    private int radarIID;
    private int pi7;
    private int cl3;
    private final int ca3;
    private final String raw56;
    private final String acid;
    private final String crcValue;
    private final long timestamp;
    private boolean isSIcode;
    private boolean isOnGround;
    private final boolean valid;

    /**
     * Decode the DF11 Packets
     *
     * @param raw a string of the raw 56-bit packet in hexadecimal
     * @param time a long representing the UTC time of detection
     */
    public DownlinkFormat11(String raw, long time) {
        crc = new CRC();
        raw56 = raw;
        timestamp = time;
        isSIcode = false;

        /*
         * First we determine if this is a broadcast squitter from an aircraft,
         * which has a 000000 hex xor with parity/IID (PI). A ground interrogated
         * reply would have a IID or SI xor, so 00007F hex (IC 4-bits, CL
         * 3-bits),
         *
         * The CL code (bits 50-52) come first and is 3 bits, then comes the IC
         * code (53-56) which is 4 bits.
         */
        acid = raw56.substring(2, 8);
        crcValue = crc.crcCompute(raw56);

        isOnGround = false;
        ca3 = Integer.parseInt(raw56.substring(1, 2), 16) & 0x07;
        
        if (ca3 == 4) {             // bunch of options I am not dealing with
            isOnGround = true;
        }

        /*
         * If the PI parity comes back 000000 this means we probably have a
         * squitter, and the parity CRC is good.
         */
        if (crcValue.equals("000000")) {
            radarIID = 0;
            isSIcode = false;

            valid = true;
        } else if (crcValue.substring(0, 4).equals("0000")) {
            /*
             * IID or SI overlayed returns
             */

            pi7 = Integer.parseInt(crcValue.substring(4), 16);
            cl3 = (pi7 >>> 4) & 0x07;
            radarIID = pi7 & 0x0F;

            if (cl3 == 0) {
                isSIcode = false;
            } else if (cl3 < 5) {
                isSIcode = true;
                radarIID = pi7 - 16;
            } else {
                radarIID = 0;
            }
            
            valid = true;
        } else {
            valid = false;
        }
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public int getRadarIID() {
        return radarIID;
    }

    @Override
    public boolean getSI() {
        return isSIcode;
    }

    @Override
    public int getCapability() {
        return ca3;
    }

    @Override
    public boolean getIsOnGround() {
        return isOnGround;
    }

    public String getACID() {
        return acid;
    }

    @Override
    public long getUpdateTime() {
        return timestamp;
    }
}
