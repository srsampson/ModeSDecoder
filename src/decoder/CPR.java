/*
 * Copyright (C) 2015 by Oliver Jowett <oliver@mutability.co.uk>
 * Copyright (C) 2012 by Salvatore Sanfilippo <antirez@gmail.com>
 *
 * All rights reserved
 *
 * Algorithms based on dump1090 application.
 */
package decoder;

public class CPR implements IConstants {

    private static final float NL[] = new float[59];       // NL[0..58] Number of Longitude Zones

    /*
     * Initialize the NL Table (Number of Longitude Zones as a function of
     * latitude)
     *
     * This has been verified with a published ICAO fixed table.
     */
    public CPR() {
        float tmp = (1.0f - (float) Math.cos(Math.PI / 30.0));

        NL[0] = 90.0f;

        for (int i = 2; i < 60; i++) {
            NL[i - 1] = (float) Math.toDegrees(Math.acos(Math.sqrt(tmp / (1.0 - Math.cos(TAU / (float) i)))));
        }
    }

    public int cprNLFunction(float lat) {
        int i = 58;

        lat = Math.abs(lat);

        if (Float.compare(lat, 0.0f) == 0) {
            return 59;                  // Equator
        } else if (Float.compare(lat, 87.0f) == 0) {
            return 2;
        } else if (Float.compare(lat, 87.0f) > 0) {
            return 1;                   // Pole
        }

        while (Float.compare(lat, NL[i]) > 0) {
            i--;
        }

        return (i + 1);     // Java is Arabic - starts at zero...
    }

    private int cprNFunction(float lat, boolean fflag) {
        int nl = cprNLFunction(lat) - ((fflag == ODD) ? 1 : 0);

        if (nl < 1) {
            nl = 1;
        }

        return nl;
    }

    /*
     * Always positive MOD operation, used for CPR decoding.
     */
    private int cprModInt(int a, int b) {
        int res = a % b;

        if (res < 0) {
            res += b;
        }

        return res;
    }

    public float cprModFloat(float a, float b) {
        if (Float.compare(b, 0.0f) == 0) {
            return Float.NaN;
        }
        
        float res = (float) Math.IEEEremainder(a, b);

        if (Float.compare(res, 0.0f) < 0) {
            res += b;
        }

        return res;
    }

    private float cprDlonFunction(float lat, boolean fflag, boolean surface) {
        return ((surface == true) ? 90.0f : 360.0f) / cprNFunction(lat, fflag);
    }

    public LatLon decodeCPRairborne(int even_cprlat, int even_cprlon,
            int odd_cprlat, int odd_cprlon, boolean fflag) {
        float AirDlat0 = 360.0f / 60.0f;
        float AirDlat1 = 360.0f / 59.0f;
        float lat0 = even_cprlat;
        float lat1 = odd_cprlat;
        float lon0 = even_cprlon;
        float lon1 = odd_cprlon;

        float rlat, rlon;

        // Compute the Latitude Index "j"
        int j = (int) (float) Math.floor(((59.0 * lat0 - 60.0 * lat1) / 131072.0) + 0.5);
        float rlat0 = AirDlat0 * (cprModInt(j, 60) + lat0 / 131072.0f);
        float rlat1 = AirDlat1 * (cprModInt(j, 59) + lat1 / 131072.0f);

        if (Float.compare(rlat0, 270.0f) >= 0) {
            rlat0 -= 360.0f;
        }

        if (Float.compare(rlat1, 270.0f) >= 0) {
            rlat1 -= 360.0f;
        }

        // Check to see that the latitude is in range: -90 .. +90
        if (Float.compare(rlat0, -90.0f) < 0 || Float.compare(rlat0, 90.0f) > 0 ||
                Float.compare(rlat1, -90.0f) < 0 || Float.compare(rlat1, 90.0f) > 0) {
            return new LatLon(0.0f, 0.0f); // bad data
        }
        
        // Check that both are in the same latitude zone, or abort.
        if (cprNLFunction(rlat0) != cprNLFunction(rlat1)) {
            return new LatLon(0.0f, 0.0f); // positions crossed a latitude zone, try again later
        }
        
        // Compute ni and the Longitude Index "m"
        if (fflag == ODD) { // Use odd packet.
            int ni = cprNFunction(rlat1, ODD);
            int m = (int) (float) Math.floor((((lon0 * (cprNLFunction(rlat1) - 1))
                    - (lon1 * cprNLFunction(rlat1))) / 131072.0) + 0.5);
            rlon = cprDlonFunction(rlat1, ODD, false) * (cprModInt(m, ni) + lon1 / 131072.0f);
            rlat = rlat1;
        } else {     // Use even packet.
            int ni = cprNFunction(rlat0, EVEN);
            int m = (int) (float) Math.floor((((lon0 * (cprNLFunction(rlat0) - 1))
                    - (lon1 * cprNLFunction(rlat0))) / 131072.0) + 0.5);
            rlon = cprDlonFunction(rlat0, EVEN, false) * (cprModInt(m, ni) + lon0 / 131072.0f);
            rlat = rlat0;
        }

        // Renormalize to -180 .. +180
        rlon -= (float) Math.floor((rlon + 180.0) / 360.0) * 360.0f;

        return new LatLon(rlat, rlon);
    }

    public LatLon decodeCPRsurface(LatLon receiverLatLon,
            int even_cprlat, int even_cprlon,
            int odd_cprlat, int odd_cprlon,
            boolean fflag) {
        float AirDlat0 = 90.0f / 60.0f;
        float AirDlat1 = 90.0f / 59.0f;
        float lat0 = even_cprlat;
        float lat1 = odd_cprlat;
        float lon0 = even_cprlon;
        float lon1 = odd_cprlon;
        float rlon, rlat;

        // Compute the Latitude Index "j"
        int j = (int) (float) Math.floor(((59.0 * lat0 - 60.0 * lat1) / 131072.0) + 0.5);
        float rlat0 = AirDlat0 * (cprModInt(j, 60) + lat0 / 131072.0f);
        float rlat1 = AirDlat1 * (cprModInt(j, 59) + lat1 / 131072.0f);

        /*
         * Pick the quadrant that's closest to the reference location -
         * this is not necessarily the same quadrant that contains the
         * reference location.
         *
         * There are also only two valid quadrants:
         * -90..0 and 0..90;
         * no correct message would try to encoding a latitude in the
         * ranges -180..-90 and 90..180.
         *
         * If the computed latitude is more than 45 degrees north of
         * the reference latitude (using the northern hemisphere
         * solution), then the southern hemisphere solution will be
         * closer to the reference latitude.
         *
         * e.g. reflat=0, rlat=44, use rlat=44
         * reflat=0, rlat=46, use rlat=46
         * -90 = -44
         * reflat=40, rlat=84, use rlat=84
         * reflat=40, rlat=86, use rlat=86
         * -90 = -4
         * reflat=-40, rlat=4, use rlat=4
         * reflat=-40, rlat=6, use rlat=6
         * -90 = -84
         *
         * As a special case, -90, 0 and +90 all encode to zero, so
         * there's a little extra work to do there.
         */
        if (Float.compare(rlat0, 0.0f) == 0) {
            if (Float.compare(receiverLatLon.getLat(), -45.0f) < 0) {
                rlat0 = -90.0f;
            } else if (Float.compare(receiverLatLon.getLat(), 45.0f) > 0) {
                rlat0 = 90.0f;
            }
        } else if (Float.compare((rlat0 - receiverLatLon.getLat()), 45.0f) > 0) {
            rlat0 -= 90.0f;
        }

        if (Float.compare(rlat1, 0.0f) == 0) {
            if (Float.compare(receiverLatLon.getLat(), -45.0f) < 0) {
                rlat1 = -90.0f;
            } else if (Float.compare(receiverLatLon.getLat(), 45.0f) > 0) {
                rlat1 = 90.0f;
            }
        } else if (Float.compare((rlat1 - receiverLatLon.getLat()), 45.0f) > 0) {
            rlat1 -= 90.0f;
        }
        
        // Check to see that the latitude is in range: -90 .. +90
        if (Float.compare(rlat0, -90.0f) < 0 || Float.compare(rlat0, 90.0f) > 0 ||
                Float.compare(rlat1, -90.0f) < 0 || Float.compare(rlat1, 90.0f) > 0) {
            return new LatLon(0.0f, 0.0f); // bad data
        }

        // Check that both are in the same latitude zone, or abort.
        if (cprNLFunction(rlat0) != cprNLFunction(rlat1)) {
            return new LatLon(0.0f, 0.0f); // positions crossed a latitude zone, try again later
        }

        // Compute ni and the Longitude Index "m"
        if (fflag == ODD) { // Use odd packet.
            int ni = cprNFunction(rlat1, ODD);
            int m = (int) (float) Math.floor((((lon0 * (cprNLFunction(rlat1) - 1))
                    - (lon1 * cprNLFunction(rlat1))) / 131072.0f) + 0.5f);
            rlon = cprDlonFunction(rlat1, ODD, true) * (cprModInt(m, ni) + lon1 / 131072.0f);
            rlat = rlat1;
        } else {     // Use even packet.
            int ni = cprNFunction(rlat0, EVEN);
            int m = (int) (float) Math.floor((((lon0 * (cprNLFunction(rlat0) - 1))
                    - (lon1 * cprNLFunction(rlat0))) / 131072.0f) + 0.5f);
            rlon = cprDlonFunction(rlat0, EVEN, true) * (cprModInt(m, ni) + lon0 / 131072.0f);
            rlat = rlat0;
        }

        /*
         * Pick the quadrant that's closest to the reference location -
         * this is not necessarily the same quadrant that contains the
         * reference location. Unlike the latitude case, all four
         * quadrants are valid.
         *
         * if reflon is more than 45 degrees away, move some multiple
         * of 90 degrees towards it.
         */
        rlon += (float) Math.floor((receiverLatLon.getLon() - rlon + 45.0) / 90.0) * 90.0f;  // this might move us outside (-180..+180), we fix this below

        // Renormalize to -180 .. +180
        rlon -= (float) Math.floor((rlon + 180.0) / 360.0) * 360.0f;

        return new LatLon(rlat, rlon);
    }

    public LatLon decodeCPRrelative(LatLon ref, int cprlat, int cprlon, boolean fflag, boolean surface) {
        float fractional_lat = cprlat / 131072.0f;
        float fractional_lon = cprlon / 131072.0f;

        float AirDlat = ((surface == true) ? 90.0f : 360.0f) / ((fflag == ODD) ? 59.0f : 60.0f);

        // Compute the Latitude Index "j"
        int j = (int) ((float) Math.floor(ref.getLat() / AirDlat)
                + (float) Math.floor(0.5 + cprModFloat(ref.getLat(), AirDlat) / AirDlat - fractional_lat));

        float rlat = AirDlat * (j + fractional_lat);

        if (Float.compare(rlat, 270.0f) >= 0) {
            rlat -= 360.0f;
        }

        // Check to see that the latitude is in range: -90 .. +90
        if (Float.compare(rlat, -90.0f) < 0 || Float.compare(rlat, 90.0f) > 0) {
            return new LatLon(0.0f, 0.0f);  // Time to give up - Latitude error
        }

        // Check to see that answer is reasonable - ie no more than 1/2 cell away
        if (Float.compare((float) Math.abs(rlat - ref.getLat()), (AirDlat / 2.0f)) > 0) {
            return new LatLon(0.0f, 0.0f); // Time to give up - Latitude error
        }

        // Compute the Longitude Index "m"
        float AirDlon = cprDlonFunction(rlat, fflag, surface);

        int m = (int) (Math.floor(ref.getLon() / AirDlon)
                + Math.floor(0.5 + cprModFloat(ref.getLon(), AirDlon) / AirDlon - fractional_lon));

        float rlon = AirDlon * (m + fractional_lon);

        if (Float.compare(rlon, 180.0f) > 0) {
            rlon -= 360.0f;
        }

        // Check to see that answer is reasonable - ie no more than 1/2 cell away
        if (Float.compare(Math.abs(rlon - ref.getLon()), (AirDlon / 2.0f)) > 0) {
            return new LatLon(0.0f, 0.0f);   // Time to give up - Longitude error
        }

        return new LatLon(rlat, rlon);
    }
}
