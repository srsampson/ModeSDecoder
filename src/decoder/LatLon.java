/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import java.text.DecimalFormat;

/**
 * An immutable position class to represent a position on the Earth as a
 * latitude, longitude pair. Provide constructors to create the position from
 * defined latitude, longitude or a bearing, distance from a latitude,
 * longitude.
 *
 * @author David Wheeler
 * @version 0.1
 */
public final class LatLon {

    private double latitude;    // degrees
    private double longitude;   // degrees

    /**
     * Create a new position from the latitude and longitude
     *
     * @param lat latitude of the position in degrees
     * @param lon longitude of the position in degrees
     */
    public LatLon(double lat, double lon) {
        latitude = fix90(lat);
        longitude = fix180(lon);
    }

    /**
     * Create a new position from the bearing and distance from a known position
     *
     * @param lat latitude of the starting position in degrees
     * @param lon longitude of the starting position in degrees
     * @param bearing bearing to the required position in degrees
     * @param distance distance to the required position in meters
     */
    public LatLon(double lat, double lon, double bearing, double distance) {
        latitude = fix90(lat);
        longitude = fix180(lon);

        LatLon newPosition = direct(bearing, distance);

        latitude = newPosition.latitude;
        longitude = newPosition.longitude;
    }

    /**
     * Create a new position at the intersection of a circle and the offset from
     * a bearing line from another position
     *
     * @param extLat latitude of the bearing line starting position
     * @param extLon longitude of the bearing line starting position
     * @param bearing bearing of the bearing line from the starting position
     * @param offset offset distance from the bearing line in meters
     * @param circleLat latitude of the center of the circle
     * @param circleLon longitude of the center of the circle
     * @param radius distance from the center of the circle in meters
     */
    public LatLon(double extLat, double extLon, double bearing, double offset, double circleLat, double circleLon, double radius) {
        LatLon extStart = new LatLon(extLat, extLon);
        LatLon circleCenter = new LatLon(circleLat, circleLon);

        // Calculate the adjustment due to the start position of the bearing line and the center of the circle not being the same
        double adjust = extStart.distance(circleCenter) * Math.sin(Math.toRadians(extStart.bearing(circleCenter) - bearing));

        // Calculate the intersection
        LatLon intersection = circleCenter.direct(bearing - Math.toDegrees(Math.asin((offset + adjust) / radius)), radius);

        latitude = intersection.latitude;
        longitude = intersection.longitude;
    }

    /**
     * Create a new position at an offset from the bearing and distance from a
     * known position
     *
     * @param extLat latitude of the starting position
     * @param extLon longitude of the starting position
     * @param bearing bearing to the offset starting point
     * @param distance distance to the offset starting point
     * @param offset offset from the end of the bearing line at right angles to
     * the bearing line
     */
    public LatLon(double extLat, double extLon, double bearing, double distance, double offset) {
        LatLon newPosition = new LatLon(extLat, extLon, bearing - Math.toDegrees(Math.atan(offset / distance)), Math.sqrt((distance * distance) + (offset * offset)));

        latitude = newPosition.latitude;
        longitude = newPosition.longitude;
    }

    /**
     * Get the latitude of the position
     *
     * @return the latitude of the position in degrees
     */
    public final double getLat() {
        return latitude;
    }

    /**
     * Get the longitude of the position
     *
     * @return the longitude of the position in degrees
     */
    public final double getLon() {
        return longitude;
    }

    @Override
    public final String toString() {
        double num;
        int part;

        DecimalFormat dfMins = new DecimalFormat("00");
        DecimalFormat dfSecs = new DecimalFormat("00.0");

        num = latitude;

        StringBuilder lat = new StringBuilder();

        if (Double.compare(num, 0.0) >= 0) {
            lat.append('N');
        } else {
            lat.append('S');
            num *= -1.0;
        }

        part = Double.valueOf(Math.floor(num)).intValue();

        lat.append(part).append('°');

        num = (num - part) * 60;

        part = Double.valueOf(Math.floor(num)).intValue();

        lat.append(dfMins.format(part)).append('\'');

        num = (num - part) * 60;

        lat.append(dfSecs.format(num)).append("\" ");

        num = longitude;

        StringBuffer lon = new StringBuffer();

        if (Double.compare(num, 0.0) >= 0) {
            lat.append('E');
        } else {
            lat.append('W');
            num *= -1.0;
        }

        part = Double.valueOf(Math.floor(num)).intValue();

        lat.append(part).append('°');

        num = (num - part) * 60;

        part = Double.valueOf(Math.floor(num)).intValue();

        lat.append(dfMins.format(part)).append('\'');

        num = (num - part) * 60;

        lat.append(dfSecs.format(num)).append('\"');

        return String.valueOf(lat + ", ") + String.valueOf(lon);
    }

    /**
     * Calculate the geodesic distance (in nm) to a destination position using
     * the Vincenty inverse formula for ellipsoids.
     *
     * @param to latitude of destination position
     * @return the distance to the destination position in meters
     */
    public final double distance(LatLon to) {
        if ((Double.compare(latitude, to.latitude) == 0) &&
                (Double.compare(longitude, to.longitude) == 0)) { // Trap equal positions
            return 0.0;
        }

        //   Use WGS-84 constants
        double a = 6378137.0;
        double b = 6356752.3142;
        double f = 1 / 298.257223563;

        double L = Math.toRadians(to.longitude) - Math.toRadians(longitude);
        double U1 = Math.atan((1.0 - f) * Math.tan(Math.toRadians(latitude)));
        double U2 = Math.atan((1.0 - f) * Math.tan(Math.toRadians(to.latitude)));
        double sinU1 = Math.sin(U1);
        double cosU1 = Math.cos(U1);
        double sinU2 = Math.sin(U2);
        double cosU2 = Math.cos(U2);
        double lambda = L;
        double lambdaP = 2.0 * Math.PI;
        int iterLimit = 20;

        double sinLambda;
        double cosLambda;
        double sinSigma = 0.0;
        double cosSigma = 0.0;
        double sigma = 0.0;
        double sinAlpha;
        double cosSqAlpha = 0.0;
        double cos2SigmaM = 0.0;
        double C;

        while ((Math.abs(lambda - lambdaP) > 1e-12) && (--iterLimit > 0)) {
            sinLambda = Math.sin(lambda);
            cosLambda = Math.cos(lambda);
            sinSigma = Math.sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda));
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha;
            C = f / 16.0 * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
            lambdaP = lambda;
            lambda = L + (1.0 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)));
        }

        if (iterLimit == 0) {
            return Double.NaN; // Formula failed to converge
        }

        double uSq = cosSqAlpha * (a * a - b * b) / (b * b);
        double A = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)));
        double B = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)));
        double deltaSigma = B * sinSigma * (cos2SigmaM + B / 4.0 * (cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM) - B / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)));
        double s = b * A * (sigma - deltaSigma);

        return s * 0.000539957; // metres to nautical miles
    }

    /**
     * Calculate the bearing to a destination position using the Vincenty
     * inverse formula for ellipsoids.
     *
     * @param to latitude of destination position
     * @return the bearing to the destination position in degrees
     */
    public final double bearing(LatLon to) {
        if ((Double.compare(latitude, to.latitude) == 0) &&
                (Double.compare(longitude, to.longitude) == 0)) { // Trap equal positions (ignore altitude)
            return 0.0;
        }

        //   Use WGS-84 constants
        double f = 1 / 298.257223563;

        double L = Math.toRadians(to.longitude) - Math.toRadians(longitude);
        double U1 = Math.atan((1.0 - f) * Math.tan(Math.toRadians(latitude)));
        double U2 = Math.atan((1.0 - f) * Math.tan(Math.toRadians(to.latitude)));
        double sinU1 = Math.sin(U1);
        double cosU1 = Math.cos(U1);
        double sinU2 = Math.sin(U2);
        double cosU2 = Math.cos(U2);
        double lambda = L;
        double lambdaP = 2.0 * Math.PI;
        int iterLimit = 20;

        double sinLambda = 0.0;
        double cosLambda = 0.0;
        double sinSigma;
        double cosSigma;
        double sigma;
        double sinAlpha;
        double cosSqAlpha;
        double cos2SigmaM;
        double C;

        while ((Math.abs(lambda - lambdaP) > 1e-12) && (--iterLimit > 0)) {
            sinLambda = Math.sin(lambda);
            cosLambda = Math.cos(lambda);
            sinSigma = Math.sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda));
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha;
            C = f / 16.0 * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
            lambdaP = lambda;
            lambda = L + (1.0 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)));
        }

        if (iterLimit == 0) {
            return Double.NaN;  // Formula failed to converge
        }

        double bearing = Math.atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);

        return norm360(Math.toDegrees(bearing));
    }

    /**
     * Calculates a new position using a bearing and distance using the Vincenty
     * direct formula for ellipsoids.
     *
     * @param bearing bearing to the new position
     * @param distance distance to the new position in meters
     * @return calculated position
     */
    public final LatLon direct(double bearing, double distance) {
        if (Double.compare(distance, 0.0) == 0) {
            return this;
        }

        double a = 6378137.0;
        double b = 6356752.3142;
        double f = 1 / 298.257223563;

        double radBearing = Math.toRadians(bearing);
        double tanU1 = (1.0 - f) * Math.tan(Math.toRadians(latitude));
        double tanSigma1 = tanU1 / Math.cos(radBearing);
        double u1 = Math.atan(tanU1);
        double sinU1 = Math.sin(u1);
        double cosU1 = Math.cos(u1);
        double sinAlpha = cosU1 * Math.sin(radBearing);
        double cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
        double uSq = cosSqAlpha * (a * a - b * b) / (b * b);
        double A = 1.0 + (uSq / 16384.0) * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175 * uSq)));
        double B = (uSq / 1024.0) * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)));
        double sigma = distance / (b * A);
        double sigma1 = Math.atan(tanSigma1);
        int iterLimit = 20;

        double lastSigma;
        double twoSigmaM;
        double sinSigma;
        double cosSigma;
        double cos2SigmaM;
        double deltaSigma;

        do {
            lastSigma = sigma;
            twoSigmaM = 2.0 * sigma1 + sigma;
            sinSigma = Math.sin(sigma);
            cosSigma = Math.cos(sigma);
            cos2SigmaM = Math.cos(twoSigmaM);
            deltaSigma = B * sinSigma * (cos2SigmaM + B / 4.0 * (cosSigma * (-1.0 + 2.0 * Math.pow(cos2SigmaM, 2.0)) - B / 6.0 * cos2SigmaM * (-3.0 + 4.0 * Math.pow(sinSigma, 2.0)) * (-3.0 + 4.0 * Math.pow(cos2SigmaM, 2.0))));
            sigma = distance / (b * A) + deltaSigma;
        } while ((--iterLimit > 0) && (Math.abs(sigma - lastSigma) >= 1e-12));

        if (iterLimit == 0) {
            return new LatLon(Double.NaN, Double.NaN); // Formula failed to converge
        }

        twoSigmaM = 2.0 * sigma1 + sigma;
        sinSigma = Math.sin(sigma);
        cosSigma = Math.cos(sigma);
        cos2SigmaM = Math.cos(twoSigmaM);

        double lat = Math.atan2(sinU1 * cosSigma + cosU1 * sinSigma * Math.cos(radBearing), (1.0 - f) * Math.sqrt(Math.pow(sinAlpha, 2.0) + Math.pow(sinU1 * sinSigma - cosU1 * cosSigma * Math.cos(radBearing), 2.0)));

        double lambda = Math.atan2(sinSigma * Math.sin(radBearing), cosU1 * cosSigma - sinU1 * sinSigma * Math.cos(radBearing));

        double C = f / 16.0 * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));

        double lambdaP = lambda - (1.0 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)));
        double lon = longitude + Math.toDegrees(lambdaP);

        return new LatLon(Math.toDegrees(lat), lon);
    }

    /**
     * Truncate a number so that it is between -90 and 90. Numbers outside this
     * range are truncated to -90 or 90. This for latitudes
     *
     * @param num the number to fix
     * @return the number fixed to the range -90 to 90
     */
    public double fix90(double num) {
        if (Double.compare(num, -90.0) < 0) {
            return -90.0;
        }

        if (Double.compare(num, 90.0) > 0) {
            return 90.0;
        }

        return num;
    }

    /**
     * Truncate a number so that it is between -180 and 180. Numbers outside
     * this range are truncated to -180 or 180. This for longitudes
     *
     * @param num the number to fix
     * @return the number fixed to the range -180 to 180
     */
    public double fix180(double num) {
        if (Double.compare(num, -180.0) < 0) {
            return -180.0;
        }

        if (Double.compare(num, 180.0) > 0) {
            return 180.0;
        }

        return num;
    }

    /**
     * Adjust a number to the range 0 to 360. If a number is outside this range
     * it moved to be within the range by adding or subtracting multiples of 360
     *
     * @param num the number to fix
     * @return the number adjusted to be in the range 0 to 360
     */
    public double norm360(double num) {
        if (Double.compare(num, 0.0) < 0) {
            return num - Math.floor(num / 360.0) * 360.0;
//          return Math.IEEEremainder(num, 360.0);
        } else if (Double.compare(num, 360.0) > 0) {
            return num - Math.floor(num / 360.0) * 360.0;
//          return Math.IEEEremainder(num, 360.0);
        } else {
            return num;
        }
    }

    /**
     * Convert a string of the format SDDD MM SS or SDDD MM SS.S to a double
     *
     * @param txt a string containing a latitude or longitude
     * @return the latitude or longitude as a double in degrees
     */
    public double fromString(String txt) {
        int txtlen = txt.length();
        int idx = 0;

        char ch = Character.toUpperCase(txt.charAt(0));

        double sgn = 1.0;

        if (ch == 'N' || ch == 'S' || ch == 'E' || ch == 'W') {
            if (ch == 'S' || ch == 'W') {
                sgn = -1.0;
            }

            idx++;
        } else {
            throw new NumberFormatException();
        }

        double numd = 0;

        while ((idx < txtlen) && (Character.isDigit(txt.charAt(idx)))) {
            numd = (numd * 10.0) + (txt.charAt(idx++) - '0');
        }

        if ((idx >= txt.length()) || (txt.charAt(idx++) != ' ')) {
            throw new NumberFormatException();
        }

        double numm = 0;

        while ((idx < txtlen) && (Character.isDigit(txt.charAt(idx)))) {
            numm = (numm * 10.0) + (txt.charAt(idx++) - '0');
        }

        if (Double.compare(numm, 59.0) > 0) {
            throw new NumberFormatException();
        }

        if ((idx >= txt.length()) || (txt.charAt(idx++) != ' ')) {
            throw new NumberFormatException();
        }

        double nums = 0;

        while ((idx < txtlen) && (Character.isDigit(txt.charAt(idx)))) {
            nums = (nums * 10.0) + (txt.charAt(idx++) - '0');
        }

        if (Double.compare(nums, 59.0) > 0) {
            throw new NumberFormatException();
        }

        double numss = 0;

        if (idx < txtlen) {
            if (txt.charAt(idx++) != '.') {
                throw new NumberFormatException();
            }

            if (idx < txtlen) {
                if (Character.isDigit(txt.charAt(idx))) {
                    numss = (txt.charAt(idx++) - '0');
                } else {
                    throw new NumberFormatException();
                }
            } else {
                throw new NumberFormatException();
            }
        }

        double num = sgn * (numd + (numm / 60.0) + ((nums + (numss / 10)) / 3600.0));

        return num;
    }
}
