/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public final class Callsign implements ICallsign {

    private static final char[] Alpha = {
        ' ', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
        'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
        'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W',
        'X', 'Y', 'Z', ' ', ' ', ' ', ' ', ' ',
        ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
        ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', ' ', ' ', ' ', ' ', ' ', ' '
    };

    @Override
    public String callsignDecode(long data56) {
        char c8 = Alpha[(int) (data56 & 0x3FL)];
        char c7 = Alpha[(int) ((data56 >>> 6) & 0x3FL)];
        char c6 = Alpha[(int) ((data56 >>> 12) & 0x3FL)];
        char c5 = Alpha[(int) ((data56 >>> 18) & 0x3FL)];
        char c4 = Alpha[(int) ((data56 >>> 24) & 0x3FL)];
        char c3 = Alpha[(int) ((data56 >>> 30) & 0x3FL)];
        char c2 = Alpha[(int) ((data56 >>> 36) & 0x3FL)];
        char c1 = Alpha[(int) ((data56 >>> 42) & 0x3FL)];

        char[] result = {c1, c2, c3, c4, c5, c6, c7, c8};
        String callsign = new String(result).trim();

        return callsign;
    }
}
