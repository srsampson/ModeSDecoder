/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the TIS-B object
 */
public final class DownlinkFormat18 implements IDF18 {

    private final Altitude alt;
    private final CRC crc;
    private final TrueHeading thead;
    private final Callsign call;
    private final PositionManager pm;
    private int altitude;
    private int cf3;
    private int imf;
    private int sss2;
    private int alt12;
    private int formatType5;
    private int subType3;
    private int nucp;
    private int categoryNumeric;
    private int vrsource;
    private int vSpeed;
    private int sign;
    private int diff;
    private int baroDiff;
    private int capability;
    //
    private long data56;
    private int lat17;
    private int lon17;
    private final long timestamp;
    //
    private String callsign;
    private String vspeedSource;
    private final String acid;
    //
    private boolean singleAntenna;
    private boolean timeSync;
    private boolean qBit1;
    private boolean isOnGround;
    private boolean isAlert;
    private boolean isSPI;
    private boolean isEmergency;
    private boolean magneticFlag;
    private boolean tasFlag;
    private boolean supersonic;
    private boolean valid;
    private boolean cpr1;
    //
    private float airspeed;
    private float indicatedAirspeed;
    private float trueAirspeed;
    private float groundSpeed;
    private float trueHeading;
    private float magneticHeading;
    //
    private final String crcValue;

    /**
     * Decode the DF18 packets
     *
     * @param raw112 a string of the raw 112-bit packet in hex
     * @param time a long representing the UTC time of detection
     * @param p a PositionManager object
     */
    public DownlinkFormat18(String raw112, long time, PositionManager p) {
        alt = new Altitude();
        crc = new CRC();
        thead = new TrueHeading();
        call = new Callsign();

        pm = p;
        timestamp = time;

        /*
         * By running the first 88 bits into the CRC, and then XOR the last 24
         * bits, you should get 000000.
         *
         * This is effected by interference and garbled bits, so you have to
         * validate this ACID with the ACID in hex digits 2 through 7.
         */
        acid = raw112.substring(2, 8);
        crcValue = crc.crcCompute(raw112);
        valid = false;

        if (crcValue.equals("000000")) {
            valid = true;
            isOnGround = false;
            /*
             * CF Value  ICAO/Mode A Flag (IMF)   Meaning
             *
             * 0         N/A                      ADS-B Message from a non-transponder device,
             *                                    AA field holds 24-bit ICAO aircraft address
             *
             * 1         N/A                      Reserved for ADS-B Message in which the AA field holds
             *                                    anonymous address or ground vehicle address or fixed
             *                                    obstruction address
             *
             * 2         0                        Fine TIS-B Message,
             *                                    AA field contains the 24-bit ICAO aircraft address
             *
             *           1                        Fine TIS-B Message,
             *                                    AA field contains the 12-bit Mode A code followed by a 12-bit track
             *                                    file number
             *
             * 3         0                        Coarse TIS-B Airborne Position and Velocity Message,
             *                                    AA field contains the 24-bit ICAO aircraft address
             *
             *           1                        Coarse TIS-B Airborne Position and Velocity Message,
             *                                    AA field contains the 12-bit Mode A code followed by a 12-bit track
             *                                    file number.
             *
             * 4         N/A                      TIS-B and ADS-R Management Message
             *                                    AA contains TIS-B/ADS-R management information.
             *
             * 5         0                        Fine TIS-B Message
             *                                    AA field contains a non-ICAO 24-bit address
             *
             *           1                        Reserved
             *
             * 6         0                        Rebroadcast of ADS-B Message from an alternate data link.
             *                                    AA field holds 24-bit ICAO aircraft address
             *
             *           1                        Rebroadcast of ADS-B Message from an alternate data link.
             *                                    AA field holds anonymous address or ground vehicle address or
             *                                    fixed obstruction
             *
             * 7         N/A                      Reserved
             */

            cf3 = Integer.parseInt(raw112.substring(1, 2), 16) & 0x07;  // bits 6-8

            /*
             * IMF equal to ZERO (0) shall indicate that the TIS-B data is
             * identified by an 24-bit Aircraft address. IMF equal to ONE (1)
             * indicates that the TIS-B data is identified by a Mode A Code.
             * A Mode A Code of all ZEROs indicates a primary radar target.
             */
            imf = Integer.parseInt(raw112.substring(9, 10), 16) & 0x01; // ME bit 8

            if (cf3 == 1) {
                isOnGround = true;
            }

            /*
             * Decode the ME field based on the decoded CF3/IMF value
             */
            decodeExtended(raw112.substring(8)); // starting at bit 33 (8 x 4 hex)
        }
    }

    /*
     * This method decodes the 56 bit Message Extended (ME) field
     */
    private void decodeExtended(String raw56) {
        int[] dataBytes = new int[7];

        int loop = 0;
        for (int j = 0; j < 14; j += 2) {
            dataBytes[loop++] = Integer.parseInt(raw56.substring(j, j + 2), 16);
        }

        data56 = ((long) dataBytes[0] << 48)
                | ((long) dataBytes[1] << 40)
                | ((long) dataBytes[2] << 32)
                | ((long) dataBytes[3] << 24)
                | ((long) dataBytes[4] << 16)
                | ((long) dataBytes[5] << 8)
                | ((long) dataBytes[6]);

        formatType5 = (dataBytes[0] >>> 3) & 0x1F;           // 5 bits

        if (cf3 == 6 || cf3 == 2) {
            magneticFlag = false;

            switch (formatType5) {
                case 0:
                    nucp = 0;
                    break;
                case 5:
                case 9:
                case 20:
                    nucp = 9;
                    break;
                case 6:
                case 10:
                case 21:
                    nucp = 8;
                    break;
                case 7:
                case 11:
                    nucp = 7;
                    break;
                case 8:
                case 12:
                    nucp = 6;
                    break;
                case 13:
                    nucp = 5;
                    break;
                case 14:
                    nucp = 4;
                    break;
                case 15:
                    nucp = 3;
                    break;
                case 16:
                    nucp = 2;
                    break;
                case 17:
                    nucp = 1;
                    break;
                case 18:
                    nucp = 0;
            }

            switch (formatType5) {
                case 0:
                    // No position information (may have baro alt)
                    break;
                case 1: // Cat D
                case 2: // Cat C
                case 3: // Cat B
                case 4: // Cat A
                    // Identification and Category Type

                    categoryNumeric = dataBytes[0] & 0x7;
                    callsign = call.callsignDecode(data56);
                    break;
                case 5:
                case 6:
                case 7:
                case 8:
                    // Surface Position

                    isOnGround = true;
                    timeSync = ((dataBytes[2] & 0x08) == 0x08);

                    cpr1 = (((dataBytes[2] >>> 2) & 0x01) == 1);// 1 = ODD (true), 0 = EVEN (false)
                    lat17 = (int) (((data56 & 0x03FFFE0000L) >>> 17) & 0x01FFFFL);
                    lon17 = (int) (data56 & 0x01FFFFL);

                    pm.addNewPosition(acid, lat17, lon17, timestamp, cpr1, true, true); // Surface Position
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                case 14:
                case 15:
                case 16:
                case 17:
                case 18:

                    // Airborne Position with barometric altitude
                    isOnGround = false;
                    altitude = alt.decodeAltitude(raw56.substring(0, 8), false);       // false == no Metre bit
                    qBit1 = alt.getQBit();

                    sss2 = (dataBytes[0] >>> 1) & 0x03;                 // Surveillance Status Subfield
                    isEmergency = (sss2 == 1);
                    isAlert = (sss2 == 2);
                    isSPI = (sss2 == 3);

                    singleAntenna = ((dataBytes[0] & 0x01) == 1);
                    timeSync = ((dataBytes[2] & 0x08) == 0x08); // not used

                    cpr1 = (((dataBytes[2] >>> 2) & 0x01) == 1);   // 1 = ODD (true), 0 = EVEN (false)
                    lat17 = (int) (((data56 & 0x03FFFE0000L) >>> 17) & 0x01FFFFL);
                    lon17 = (int) (data56 & 0x01FFFFL);

                    pm.addNewPosition(acid, lat17, lon17, timestamp, cpr1, false, true);    // Airborne position
                    break;
                case 19:
                    // Airborne Velocity (has SubTypes)

                    subType3 = dataBytes[0] & 0x07;

                    switch (subType3) {
                        case 1:     // gndspeed normal lsb=1knot
                        case 2:     // gndspeed supersonic lsb=4knots

                            int dire_w = ((dataBytes[1] & 0x04) >>> 2); // 0=east, 1=west
                            int velocitye_w = (((dataBytes[1] & 0x03) << 8) | (dataBytes[2])) - 1;

                            int dirn_s = ((dataBytes[3] & 0x80) >>> 7); // 0=north, 1=south
                            int velocityn_s = ((((dataBytes[3] & 0x7f) << 8) | (dataBytes[4] & 0xE0)) >>> 5) - 1;

                            if (velocityn_s == -1 || velocitye_w == -1) {
                                groundSpeed = -1.0f;    // invalid
                                trueHeading = -1.0f;      // invalid
                            } else {
                                trueHeading = thead.trueHeading(velocityn_s, velocitye_w, dirn_s, dire_w);

                                if (subType3 == 2) {
                                    velocitye_w *= 4;
                                    velocityn_s *= 4;
                                }

                                float vX = (float) (velocitye_w * velocitye_w);
                                float vY = (float) (velocityn_s * velocityn_s);

                                groundSpeed = (float) Math.sqrt(vX + vY);
                            }

                            diff = (int) ((data56) & 0x7FL) - 1;
                            vrsource = (int) ((data56 >>> 20) & 0x01);
                            vspeedSource = (vrsource == 1) ? "Baro" : "Geo";
                            vSpeed = (int) ((data56 >>> 10) & 0x1FFL) - 1; //1 = 0, 2 = 64
                            sign = (int) ((data56 >>> 7) & 0x01) == 1 ? -1 : 1;

                            if (diff == -1) {
                                diff = 0;
                            } else if (diff == 126) {
                                diff = 125; // Just make 3125 the max diff
                            }

                            baroDiff = diff * 25 * sign;

                            /*
                             * calculate vertical rate: it's 9 bits
                             */
                            if (vSpeed == -1) {
                                vSpeed = 0;
                            } else if (vSpeed == 510) {
                                vSpeed = 509;     // Just make 32576 the max fpm
                            }

                            sign = (int) ((data56 >>> 19) & 0x01) == 1 ? -1 : 1;
                            vSpeed = vSpeed * 64 * sign;

                            break;
                        case 3: // subsonic
                        case 4: // supersonic

                            // Decode Heading, Velocity over ground is not known
                            if ((dataBytes[1] & 0x04) == 0x04) {
                                magneticFlag = true;
                                magneticHeading = (((dataBytes[1] & 0x03) << 8) | dataBytes[2]) * 360.0f / 1024.0f;
                                tasFlag = ((dataBytes[3] & 0x80) == 0x80);
                                airspeed = (((dataBytes[3] & 0x7f) << 3) | ((dataBytes[4] & 0xE0) >>> 3)) - 1;

                                supersonic = false;

                                if (subType3 == 4) {
                                    airspeed *= 4.0;
                                    supersonic = true;
                                }

                                vrsource = (int) ((data56 >>> 20) & 0x01);
                                vspeedSource = (vrsource == 1) ? "Baro" : "Geo";
                                diff = (int) ((data56) & 0x7FL) - 1;
                                vSpeed = (int) ((data56 >>> 10) & 0x1FFL) - 1; //1 = 0, 2 = 64
                                sign = (int) ((data56 >>> 7) & 0x01) == 1 ? -1 : 1;

                                if (diff == -1) {
                                    diff = 0;
                                } else if (baroDiff == 126) {
                                    diff = 125;
                                }

                                baroDiff = diff * 25 * sign;

                                /*
                                 * calculate vertical rate: it's 9 bits
                                 */
                                if (vSpeed == -1) {
                                    vSpeed = 0;
                                } else if (vSpeed == 510) {
                                    vSpeed = 509;
                                }

                                sign = (int) ((data56 >>> 19) & 0x01) == 1 ? -1 : 1;
                                vSpeed = vSpeed * 64 * sign;
                            }
                    }
            }
        }
    }

    public int getSubType() {
        return subType3;
    }

    public long getData56() {
        return data56;
    }

    public int getNUCP() {
        return nucp;
    }

    public boolean getTasFlag() {
        return tasFlag;
    }

    public boolean getMagneticFlag() {
        return magneticFlag;
    }

    public boolean isValid() {
        return valid;
    }

    public int getFormatType() {
        return formatType5;
    }

    @Override
    public int getControlField() {
        return cf3;
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
    public int getCategory() {
        return categoryNumeric;
    }

    @Override
    public String getCallsign() {
        return callsign;
    }

    @Override
    public int getAltitude() {
        return altitude;
    }

    public float getGroundSpeed() {
        return groundSpeed;
    }

    public float getTrueHeading() {
        return trueHeading;
    }

    public int getVspeed() {
        return vSpeed;
    }

    public float getAirspeed() {
        return airspeed;
    }

    public float getMagneticHeading() {
        return magneticHeading;
    }

    public String getACID() {
        return acid;
    }

    @Override
    public long getUpdateTime() {
        return timestamp;
    }
}
