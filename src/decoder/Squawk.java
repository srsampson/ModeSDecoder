/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the squawk object
 *
 * Used as the decoder of the squawk broadcasts
 */
public final class Squawk implements ISquawk {

    @Override
    public String decodeSquawk(String raw32) {
        int acmsb = (Integer.parseInt(raw32.substring(4, 5), 16) & 0x1) << 12;
        int aclsb = Integer.parseInt(raw32.substring(5, 8), 16);
        int ac13 = acmsb | aclsb;                            // 13 bits

        /*
         * Combine all the bits back into octal digits - X-bit already removed
         *
         * C1 A1 C2 | A2 C4 A4 | B1 D1 B2 | D2 B4 D4
         */

        int a = ((ac13 & 0x0800) >>> 11) + ((ac13 & 0x0200) >>> 8) + ((ac13 & 0x0080) >>> 5);
        int b = ((ac13 & 0x0020) >>> 5) + ((ac13 & 0x0008) >>> 2) + ((ac13 & 0x0002) << 1);
        int c = ((ac13 & 0x1000) >>> 12) + ((ac13 & 0x0400) >>> 9) + ((ac13 & 0x0100) >>> 6);
        int d = ((ac13 & 0x0010) >>> 4) + ((ac13 & 0x0004) >>> 1) + ((ac13 & 0x0001) << 2);

        return String.format("%d%d%d%d", a, b, c, d);
    }
}
