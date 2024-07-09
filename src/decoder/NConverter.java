/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public final class NConverter {

    private final String base9;         // The first digit (after the "N")
                                        // is always one of these.
    private final String base10;        // The possible second and third digits
                                        // are one of these.

    // Note that "I" and "O" are never used as letters,
    // to prevent confusion with "1" and "0"

    private final String base34;
    private final int icaooffset;       // The lowest possible number
    private final int b1;               // basis between N1... and N2...
    private final int b2;               // basis between N10.... and N11....

    public NConverter() {
        base9 = "123456789";
        base10 = "0123456789";
        base34 = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        icaooffset = 0xA00001;
        b1 = 101711;
        b2 = 10111;
    }

    private String suffix(int rem) {
        String suf;
        
        // Produces the alpha(numeric) suffix from a number 0 - 950
        if (rem == 0) {
            suf = "";
        } else if (rem <= 600) {  // Class A suffix -- only letters.
            rem--;
            suf = Character.toString(base34.charAt(rem / 25));

            if (rem % 25 > 0) {
                suf += Character.toString(base34.charAt((rem % 25) - 1)); // second class A letter, if present.
            }
        } else {    // rem > 600 : First digit of suffix is a number.  Second digit may be blank, letter, or number.
            rem -= 601;
            suf = Character.toString(base10.charAt(rem / 35));
            
            if (rem % 35 > 0) {
                suf += Character.toString(base34.charAt((rem % 35) - 1));
            }
        }

        return suf;
    }

    private int enc_suffix(String suf) {
        int r0;
        int r1;

        // Produces a remainder from a 0 - 2 digit suffix.
        // No error checking.  Using illegal strings will have strange results."""

        if (suf.length() == 0) {
            return 0;
        }
        
        r0 = base34.indexOf(suf.charAt(0));
        
        if (suf.length() == 1) {
            r1 = 0;
        } else {
            r1 = base34.indexOf(suf.charAt(1)) + 1;
        }

        if (r0 < 24) {
            return r0 * 25 + r1 + 1;    // first char is a letter, use base 25
        } else {  
            return r0 * 35 + r1 - 239;  // first is a number -- base 35.
        }
    }

    public String icao_to_n(String val) {
        String nnum;
        int icao;
        int d1;
        int d2;
        int d3;
        int r1;
        int r2;
        int r3;
        
        icao = Integer.parseInt(val.toUpperCase(), 16);

        /*
         * N Numbers fit in this range. Other ICAO not decoded.
         */
        if ((icao < 0xA00001) || (icao > 0xADF7C7)) {
            return "";
        }

        icao -= icaooffset;     // A00001
        d1 = icao / b1;
        nnum = "N" + Character.toString(base9.charAt(d1));
        r1 = icao % b1;

        if (r1 < 601) {
            nnum += suffix(r1); // of the form N1ZZ
        } else {
            d2 = (r1 - 601) / b2; // find second digit.
            nnum += Character.toString(base10.charAt(d2));
            r2 = (r1 - 601) % b2;  // and residue after that

            if (r2 < 601) {
                nnum += suffix(r2);   // No third digit.(form N12ZZ
            } else {
                d3 = (r2 - 601) / 951; // Three-digits have extended suffix.
                r3 = (r2 - 601) % 951;
                nnum += Character.toString(base10.charAt(d3)) + suffix(r3);
            }
        }

        return nnum;
    }

    public int n_to_icao(String tail) {
        int d2;
        int d3;
        int icao;
        
        tail = tail.toUpperCase();
        
        if (tail.startsWith("N") == false) {
            return -1;
        }

        icao = icaooffset;
        icao += base9.indexOf(tail.charAt(1)) * b1;

        if (tail.length() == 2) { // simple 'N3' etc.
            return icao;
        }

        d2 = base10.indexOf(tail.charAt(2));

        if (d2 == -1) {
            icao += enc_suffix(tail.substring(2, 4));    // Form N1A
        } else {
            icao += d2 * b2 + 601;    // Form N11... or N111..

            if (tail.length() != 3) { // simple 'N34' etc.
                d3 = base10.indexOf(tail.charAt(3));

                if (d3 > -1) {  // Form N111 Suffix is base 35.
                    icao += d3 * 951 + 601;
                    icao += enc_suffix(tail.substring(4, 6));
                } else {    // Form N11A
                    icao += enc_suffix(tail.substring(3, 5));
                }
            }
        }

        return icao;
    }
}