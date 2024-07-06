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

    private static final double NL[] = new double[59];       // NL[0..58] Number of Longitude Zones

    /*
     * Initialize the NL Table (Number of Longitude Zones as a function of
     * latitude)
     *
     * This has been verified with a published ICAO fixed table.
     */
    public CPR() {
        double tmp = (1.0 - Math.cos(Math.PI / 30.0));

        NL[0] = 90.0;

        for (int i = 2; i < 60; i++) {
            NL[i - 1] = Math.toDegrees(Math.acos(Math.sqrt(tmp / (1.0 - Math.cos(TAU / (double) i)))));
        }
    }

    public int cprNLFunction(double lat) {
        int i = 58;

        lat = Math.abs(lat);

        if (Double.compare(lat, 0.0) == 0) {
            return 59;                  // Equator
        } else if (Double.compare(lat, 87.0) == 0) {
            return 2;
        } else if (Double.compare(lat, 87.0) > 0) {
            return 1;                   // Pole
        }

        while (Double.compare(lat, NL[i]) > 0) {
            i--;
        }

        return (i + 1);     // Java is Arabic - starts at zero...
    }

    private int cprNFunction(double lat, boolean fflag) {
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

    public double cprModDouble(double a, double b) {
        if (Double.compare(b, 0.0) == 0) {
            return Double.NaN;
        }
        
        double res = Math.IEEEremainder(a, b);

        if (Double.compare(res, 0.0) < 0) {
            res += b;
        }

        return res;
    }

    private double cprDlonFunction(double lat, boolean fflag, boolean surface) {
        return ((surface == true) ? 90.0 : 360.0) / cprNFunction(lat, fflag);
    }

    public LatLon decodeCPRairborne(int even_cprlat, int even_cprlon,
            int odd_cprlat, int odd_cprlon, boolean fflag) {
        double AirDlat0 = 360.0 / 60.0;
        double AirDlat1 = 360.0 / 59.0;
        double lat0 = even_cprlat;
        double lat1 = odd_cprlat;
        double lon0 = even_cprlon;
        double lon1 = odd_cprlon;

        double rlat, rlon;

        // Compute the Latitude Index "j"
        int j = (int) Math.floor(((59.0 * lat0 - 60.0 * lat1) / 131072.0) + 0.5);
        double rlat0 = AirDlat0 * (cprModInt(j, 60) + lat0 / 131072.0);
        double rlat1 = AirDlat1 * (cprModInt(j, 59) + lat1 / 131072.0);

        if (Double.compare(rlat0, 270.0) >= 0) {
            rlat0 -= 360.0;
        }

        if (Double.compare(rlat1, 270.0) >= 0) {
            rlat1 -= 360.0;
        }

        // Check to see that the latitude is in range: -90 .. +90
        if (Double.compare(rlat0, -90.0) < 0 || Double.compare(rlat0, 90.0) > 0 ||
                Double.compare(rlat1, -90.0) < 0 || Double.compare(rlat1, 90.0) > 0) {
            return new LatLon(0.0, 0.0); // bad data
        }
        
        // Check that both are in the same latitude zone, or abort.
        if (cprNLFunction(rlat0) != cprNLFunction(rlat1)) {
            return new LatLon(0.0, 0.0); // positions crossed a latitude zone, try again later
        }
        
        // Compute ni and the Longitude Index "m"
        if (fflag == ODD) { // Use odd packet.
            int ni = cprNFunction(rlat1, ODD);
            int m = (int) Math.floor((((lon0 * (cprNLFunction(rlat1) - 1))
                    - (lon1 * cprNLFunction(rlat1))) / 131072.0) + 0.5);
            rlon = cprDlonFunction(rlat1, ODD, false) * (cprModInt(m, ni) + lon1 / 131072.0);
            rlat = rlat1;
        } else {     // Use even packet.
            int ni = cprNFunction(rlat0, EVEN);
            int m = (int) Math.floor((((lon0 * (cprNLFunction(rlat0) - 1))
                    - (lon1 * cprNLFunction(rlat0))) / 131072.0) + 0.5);
            rlon = cprDlonFunction(rlat0, EVEN, false) * (cprModInt(m, ni) + lon0 / 131072.0);
            rlat = rlat0;
        }

        // Renormalize to -180 .. +180
        rlon -= Math.floor((rlon + 180.0) / 360.0) * 360.0;

        return new LatLon(rlat, rlon);
    }

    public LatLon decodeCPRsurface(LatLon receiverLatLon,
            int even_cprlat, int even_cprlon,
            int odd_cprlat, int odd_cprlon,
            boolean fflag) {
        double AirDlat0 = 90.0 / 60.0;
        double AirDlat1 = 90.0 / 59.0;
        double lat0 = even_cprlat;
        double lat1 = odd_cprlat;
        double lon0 = even_cprlon;
        double lon1 = odd_cprlon;
        double rlon, rlat;

        // Compute the Latitude Index "j"
        int j = (int) Math.floor(((59.0 * lat0 - 60.0 * lat1) / 131072.0) + 0.5);
        double rlat0 = AirDlat0 * (cprModInt(j, 60) + lat0 / 131072.0);
        double rlat1 = AirDlat1 * (cprModInt(j, 59) + lat1 / 131072.0);

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
        if (Double.compare(rlat0, 0.0) == 0) {
            if (Double.compare(receiverLatLon.getLat(), -45.0) < 0) {
                rlat0 = -90.0;
            } else if (Double.compare(receiverLatLon.getLat(), 45.0) > 0) {
                rlat0 = 90.0;
            }
        } else if (Double.compare((rlat0 - receiverLatLon.getLat()), 45.0) > 0) {
            rlat0 -= 90.0;
        }

        if (Double.compare(rlat1, 0.0) == 0) {
            if (Double.compare(receiverLatLon.getLat(), -45.0) < 0) {
                rlat1 = -90.0;
            } else if (Double.compare(receiverLatLon.getLat(), 45.0) > 0) {
                rlat1 = 90.0;
            }
        } else if (Double.compare((rlat1 - receiverLatLon.getLat()), 45.0) > 0) {
            rlat1 -= 90.0;
        }
        
        // Check to see that the latitude is in range: -90 .. +90
        if (Double.compare(rlat0, -90.0) < 0 || Double.compare(rlat0, 90.0) > 0 ||
                Double.compare(rlat1, -90.0) < 0 || Double.compare(rlat1, 90.0) > 0) {
            return new LatLon(0.0, 0.0); // bad data
        }

        // Check that both are in the same latitude zone, or abort.
        if (cprNLFunction(rlat0) != cprNLFunction(rlat1)) {
            return new LatLon(0.0, 0.0); // positions crossed a latitude zone, try again later
        }

        // Compute ni and the Longitude Index "m"
        if (fflag == ODD) { // Use odd packet.
            int ni = cprNFunction(rlat1, ODD);
            int m = (int) Math.floor((((lon0 * (cprNLFunction(rlat1) - 1))
                    - (lon1 * cprNLFunction(rlat1))) / 131072.0) + 0.5);
            rlon = cprDlonFunction(rlat1, ODD, true) * (cprModInt(m, ni) + lon1 / 131072.0);
            rlat = rlat1;
        } else {     // Use even packet.
            int ni = cprNFunction(rlat0, EVEN);
            int m = (int) Math.floor((((lon0 * (cprNLFunction(rlat0) - 1))
                    - (lon1 * cprNLFunction(rlat0))) / 131072.0) + 0.5);
            rlon = cprDlonFunction(rlat0, EVEN, true) * (cprModInt(m, ni) + lon0 / 131072.0);
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
        rlon += Math.floor((receiverLatLon.getLon() - rlon + 45.0) / 90.0) * 90.0;  // this might move us outside (-180..+180), we fix this below

        // Renormalize to -180 .. +180
        rlon -= Math.floor((rlon + 180.0) / 360.0) * 360.0;

        return new LatLon(rlat, rlon);
    }

    public LatLon decodeCPRrelative(LatLon ref, int cprlat, int cprlon, boolean fflag, boolean surface) {
        double fractional_lat = cprlat / 131072.0;
        double fractional_lon = cprlon / 131072.0;

        double AirDlat = ((surface == true) ? 90.0 : 360.0) / ((fflag == ODD) ? 59.0 : 60.0);

        // Compute the Latitude Index "j"
        int j = (int) (Math.floor(ref.getLat() / AirDlat)
                + Math.floor(0.5 + cprModDouble(ref.getLat(), AirDlat) / AirDlat - fractional_lat));

        double rlat = AirDlat * (j + fractional_lat);

        if (Double.compare(rlat, 270.0) >= 0) {
            rlat -= 360.0;
        }

        // Check to see that the latitude is in range: -90 .. +90
        if (Double.compare(rlat, -90.0) < 0 || Double.compare(rlat, 90.0) > 0) {
            return new LatLon(0.0, 0.0);  // Time to give up - Latitude error
        }

        // Check to see that answer is reasonable - ie no more than 1/2 cell away
        if (Double.compare(Math.abs(rlat - ref.getLat()), (AirDlat / 2.0)) > 0) {
            return new LatLon(0.0, 0.0); // Time to give up - Latitude error
        }

        // Compute the Longitude Index "m"
        double AirDlon = cprDlonFunction(rlat, fflag, surface);

        int m = (int) (Math.floor(ref.getLon() / AirDlon)
                + Math.floor(0.5 + cprModDouble(ref.getLon(), AirDlon) / AirDlon - fractional_lon));

        double rlon = AirDlon * (m + fractional_lon);

        if (Double.compare(rlon, 180.0) > 0) {
            rlon -= 360.0;
        }

        // Check to see that answer is reasonable - ie no more than 1/2 cell away
        if (Double.compare(Math.abs(rlon - ref.getLon()), (AirDlon / 2.0)) > 0) {
            return new LatLon(0.0, 0.0);   // Time to give up - Longitude error
        }

        return new LatLon(rlat, rlon);
    }
}
