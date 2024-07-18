/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public final class CRC implements ICRC {

    private static final long POLY = 0xFFFA0480;        // Polynomial

    @Override
    public String crcCompute(String raw) {
        int hexid, data, data1, data2;
        
        switch (raw.length()) {
            case 14 -> {
                /*
                * Convert the first 8 hex characters into a 32 bit result
                */
                
                data = Integer.parseInt(raw.substring(0, 8), 16);

                /*
                * Convert the 6 hexid characters into a 32 bit result It is
                * shifted left 8 bits to align
                */
                hexid = Integer.parseInt(raw.substring(8), 16) << 8;

                /*
                * Run the data through the polynomial
                */
                for (int i = 0; i < 32; i++) {
                    if ((data & 0x80000000) != 0) {
                        data ^= POLY;
                    }

                    data <<= 1;
                }
            }
            case 28 -> {
                /*
                * Convert the 22 hex characters frame into a 88 bit result Java
                * barfs if the msb is 1 in a 32-bit number during parse so I use
                * an (int) cast on the msb numbers
                */
                
                data = (int) Long.parseLong(raw.substring(0, 8), 16);           // Bytes 1 - 4
                data1 = (int) Long.parseLong(raw.substring(8, 16), 16);         // Bytes 5 - 8
                data2 = Integer.parseInt(raw.substring(16, 22), 16) << 8;       // Bytes 9 - 11

                /*
                * Convert the 6 hexid characters into a 32 bit result.
                * It is shifted left 8 bits to align
                */
                hexid = Integer.parseInt(raw.substring(22), 16) << 8;

                /*
                * Run the data through the polynomial
                */
                for (int i = 0; i < 88; i++) {
                    if ((data & 0x80000000) != 0) {
                        data ^= POLY;
                    }

                    data <<= 1;

                    if ((data1 & 0x80000000) != 0) {
                        data |= 1;
                    }

                    data1 <<= 1;

                    if ((data2 & 0x80000000) != 0) {
                        data1 = data1 | 1;
                    }

                    data2 <<= 1;
                }
            }
            default -> {
                return "BAD";
            }
        }

        /*
         * The result is shifted 8 bits to the right for final value
         */
        return String.format("%06X", (data ^ hexid) >>> 8);
    }
}
