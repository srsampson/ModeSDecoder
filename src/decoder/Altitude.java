/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public final class Altitude {

    private boolean mbit1;
    private boolean qbit1;
    private int ac11;

    public int getAltitude11Bits() {
        return ac11;
    }

    public boolean getMBit() {
        return mbit1;
    }

    public boolean getQBit() {
        return qbit1;
    }

    /*
     * Method to decode altitude in feet
     *
     * @param raw32 a string representing the raw (8 hex) bits to decode
     * @param hasMBit a boolean representing the altitude has the metre bit
     * which is true for DF00, DF04, DF16, and DF20, but false for DF17 and DF18,
     * but only if the altimeter is 25 foot capable.
     * @return an int representing the altitude
     */
    public int decodeAltitude(String raw32, boolean hasMBit) {
        int altitude;
        int altbits;

        try {
            if (hasMBit == true) {
                int acmsb1 = Integer.parseInt(raw32.substring(4, 5), 16) & 0x01;
                int aclsb12 = Integer.parseInt(raw32.substring(5, 8), 16);
                altbits = ((acmsb1 << 12) | aclsb12) & 0x1FFF;  // DF00, DF04, DF20 13 bits
                qbit1 = ((altbits & 0x0010) == 0x10);      // Q-Bit true means 25ft resolution
                mbit1 = ((altbits & 0x0040) == 0x40);      // M-Bit 26 and Q-Bit 28 0 0000 0X0X 0000 m = 0 feet, m = 1 metres
                ac11 = (altbits & 0x000F) | ((altbits & 0x0020) >>> 1) | ((altbits & 0x1F80) >>> 2); // raw 11 bits now
            } else {
                altbits = Integer.parseInt(raw32.substring(2, 5), 16) & 0x0FFF; // DF17, DF18 12 bits 0000 000X 0000
                qbit1 = ((altbits & 0x0010) == 0x10);      // Q-Bit true means 25ft resolution
                mbit1 = false;
                ac11 = ((altbits & 0x0FE0) >>> 1) | (altbits & 0x00F);   // 11 bits now, got rid of q-bit
            }

            altitude = computeAltitude(ac11, qbit1);

            if (mbit1) {
                altitude *= .3048;   // if m-bit then convert metres to feet (Probably a Russian)
            }
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            System.out.printf("Altitude::decodeAltitude exception [%s], %s%n", raw32, e.getMessage());
            return -9999;       // return null
        }

        return altitude;
    }

    /*
     * Method to return altitude given an SSR Mode-C squawk
     * The X-bit has already been removed.
     *
     * This is available on some receivers that pass SSR data in addition to Mode-S
     *
     * 100 foot resolution C1 A1 C2 A2 | C4 A4 B1 D1 | B2 D2 B4 D4
     */
    public int convertModeCtoAltitude(int raw12) {
        int a = ((((raw12 & 0x0100) << 1) | (raw12 & 0x0400) | ((raw12 & 0x0040) << 2)) >>> 8) & 0x07;    // A1 A2 A4
        int b = (((raw12 & 0x0002) >>> 1) | ((raw12 & 0x0008) >>> 2) | ((raw12 & 0x0020) >>> 3)) & 0x07;  // B1 B2 B4
        int c = (((raw12 & 0x0800) >>> 9) | ((raw12 & 0x0200) >>> 8) | ((raw12 & 0x0080) >>> 7)) & 0x07;  // C1 C2 C4
        int d = ((raw12 & 0x0001) | ((raw12 & 0x0004) >>> 1) | ((raw12 & 0x0010) >>> 2)) & 0x07;          // D1 D2 D4

        if (((d & 0x04) == 0x04) || (c == 0) || (c == 5) || (c == 7)) {
            /*
             * Illegal code, must be a Mode-A or garble
             * This will probably be pre-filtered before called
             */

            return -9999;
        }

        return modecDecode(a, b, c, d);
    }

    /*
     * Method to return altitude given the raw coded 11 bits
     *
     * Bit D1 and the X bit are already removed
     *
     * @param ac11 an int representing the coded altitude
     * @param rvsm a boolean representing whether 25 foot resolution is being used
     * @return an int representing the altitude in feet or metres
     */
    private int computeAltitude(int ac11, boolean rvsm) {
        if (rvsm == true) {
            return (ac11 * 25) - 1000;
        } else {
            // 100 foot resolution C1 A1 C2 | A2 C4 A4 B1 | B2 D2 B4 D4

            int a = ((ac11 & 0x0200) >>> 7) | ((ac11 & 0x0080) >>> 6) | ((ac11 & 0x0020) >>> 5);  // A1 A2 A4
            int b = ((ac11 & 0x0010) >>> 2) | ((ac11 & 0x0008) >>> 2) | ((ac11 & 0x0002) >>> 1);  // B1 B2 B4
            int c = ((ac11 & 0x0400) >>> 8) | ((ac11 & 0x0100) >>> 7) | ((ac11 & 0x0040) >>> 6);  // C1 C2 C4
            int d = ((ac11 & 0x0004) >>> 1) | (ac11 & 0x0001);                                    // XX D2 D4

            return modecDecode(a, b, c, d);
        }
    }

    /*
     * Method to convert octal data into an altitude with 100 foot resolution.
     *
     * The aviation industry uses mostly 25 foot resolution, but many legacy
     * transponders are still in use. These will be Mode-S only typically, and
     * not ADS-B that are transmitting position.
     *
     * Invalid codes are C1 C2 C4 bits: 000, 101, 111, or D4 == 1
     *
     * @param a an integer representing the A-bits
     * @param b an integer representing the B-bits
     * @param c an integer representing the C-bits
     * @param d an integer representing the D-bits
     * @return an int representing the altitude in feet with 100 foot resolution
     */
    public int modecDecode(int a, int b, int c, int d) {
        int alt;
        int dab = (grayToBinary((d << 6) + (a << 3) + b) * 500) - 1000;
        boolean i = (dab & 0x01) == 0;

        if (i) {
            switch (c) {
                case 4:
                    alt = dab + 200;
                    break;
                case 6:
                    alt = dab + 100;
                    break;
                case 2:
                    alt = dab;
                    break;
                case 3:
                    alt = dab - 100;
                    break;
                case 1:
                    alt = dab - 200;
                    break;
                default:
                    alt = -9999;            // case 0, 5, 7 illegal value
            }
        } else {
            switch (c) {
                case 4:
                    alt = dab - 200;
                    break;
                case 6:
                    alt = dab - 100;
                    break;
                case 2:
                    alt = dab;
                    break;
                case 3:
                    alt = dab + 100;
                    break;
                case 1:
                    alt = dab + 200;
                    break;
                default:
                    alt = -9999;            // case 0, 5, 7 illegal value
            }
        }

        return alt;
    }

    /*
     * Method to calculate 100 altitude from Mode-C transponder type reply
     * 
     * B[i] = XOR(B[i+1], G[i]) to convert Gray to binary
     *
     * @param g an integer representing a gray code
     * @return an integer representing the binary value of the given gray code
     */
    private int grayToBinary(int g) {
        int val = g & 0x80;

        val = val | ((g & 0x40) ^ ((val & 0x80) >>> 1));
        val = val | ((g & 0x20) ^ ((val & 0x40) >>> 1));
        val = val | ((g & 0x10) ^ ((val & 0x20) >>> 1));
        val = val | ((g & 0x08) ^ ((val & 0x10) >>> 1));
        val = val | ((g & 0x04) ^ ((val & 0x08) >>> 1));
        val = val | ((g & 0x02) ^ ((val & 0x04) >>> 1));
        val = val | ((g & 0x01) ^ ((val & 0x02) >>> 1));

        return val;
    }
}
