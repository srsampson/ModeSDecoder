/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import java.text.DecimalFormat;

/*
 * An immutable position class to represent a position on the Earth as a
 * latitude, longitude pair. Provide constructors to create the position from
 * defined latitude, longitude or a bearing, distance from a latitude,
 * longitude.
 *
 * @author David Wheeler
 * @version 0.1
 */
public final class LatLon {

    private float latitude;    // degrees
    private float longitude;   // degrees

    /**
     * Create a new position from the latitude and longitude
     *
     * @param lat latitude of the position in degrees
     * @param lon longitude of the position in degrees
     */
    public LatLon(float lat, float lon) {
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
    public LatLon(float lat, float lon, float bearing, float distance) {
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
    public LatLon(float extLat, float extLon, float bearing, float offset, float circleLat, float circleLon, float radius) {
        LatLon extStart = new LatLon(extLat, extLon);
        LatLon circleCenter = new LatLon(circleLat, circleLon);

        // Calculate the adjustment due to the start position of the bearing line and the center of the circle not being the same
        float adjust = extStart.distance(circleCenter) * (float) Math.sin(Math.toRadians(extStart.bearing(circleCenter) - bearing));

        // Calculate the intersection
        LatLon intersection = circleCenter.direct(bearing - (float) Math.toDegrees(Math.asin((offset + adjust) / radius)), radius);

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
    public LatLon(float extLat, float extLon, float bearing, float distance, float offset) {
        LatLon newPosition = new LatLon(extLat, extLon,
                bearing - (float) Math.toDegrees(Math.atan(offset / distance)),
                (float) Math.sqrt((distance * distance) + (offset * offset)));

        latitude = newPosition.latitude;
        longitude = newPosition.longitude;
    }

    /**
     * Get the latitude of the position
     *
     * @return the latitude of the position in degrees
     */
    public final float getLat() {
        return latitude;
    }

    /**
     * Get the longitude of the position
     *
     * @return the longitude of the position in degrees
     */
    public final float getLon() {
        return longitude;
    }

    @Override
    public final String toString() {
        float num;
        int part;

        DecimalFormat dfMins = new DecimalFormat("00");
        DecimalFormat dfSecs = new DecimalFormat("00.0");

        num = latitude;

        StringBuilder lat = new StringBuilder();

        if (Float.compare(num, 0.0f) >= 0) {
            lat.append('N');
        } else {
            lat.append('S');
            num *= -1.0f;
        }

        part = Float.valueOf((float) Math.floor(num)).intValue();

        lat.append(part).append('°');

        num = (num - part) * 60.0f;

        part = Float.valueOf((float) Math.floor(num)).intValue();

        lat.append(dfMins.format(part)).append('\'');

        num = (num - part) * 60.0f;

        lat.append(dfSecs.format(num)).append("\" ");

        num = longitude;

        StringBuffer lon = new StringBuffer();

        if (Float.compare(num, 0.0f) >= 0) {
            lat.append('E');
        } else {
            lat.append('W');
            num *= -1.0f;
        }

        part = Float.valueOf((float) Math.floor(num)).intValue();

        lat.append(part).append('°');

        num = (num - part) * 60.0f;

        part = Float.valueOf((float) Math.floor(num)).intValue();

        lat.append(dfMins.format(part)).append('\'');

        num = (num - part) * 60.0f;

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
    public final float distance(LatLon to) {
        if ((Float.compare(latitude, to.latitude) == 0) &&
                (Float.compare(longitude, to.longitude) == 0)) { // Trap equal positions
            return 0.0f;
        }

        //   Use WGS-84 constants
        float a = 6378137.0f;
        float b = 6356752.3142f;
        float f = 1.0f / 298.257223563f;

        float L = (float) Math.toRadians(to.longitude) - (float) Math.toRadians(longitude);
        float U1 = (float) Math.atan((1.0 - f) * Math.tan(Math.toRadians(latitude)));
        float U2 = (float) Math.atan((1.0 - f) * Math.tan(Math.toRadians(to.latitude)));
        float sinU1 = (float) Math.sin(U1);
        float cosU1 = (float) Math.cos(U1);
        float sinU2 = (float) Math.sin(U2);
        float cosU2 = (float) Math.cos(U2);
        float lambda = L;
        float lambdaP = (float) (2.0 * Math.PI);
        int iterLimit = 20;

        float sinLambda;
        float cosLambda;
        float sinSigma = 0.0f;
        float cosSigma = 0.0f;
        float sigma = 0.0f;
        float sinAlpha;
        float cosSqAlpha = 0.0f;
        float cos2SigmaM = 0.0f;
        float C;

        while ((Math.abs(lambda - lambdaP) > 1e-12f) && (--iterLimit > 0)) {
            sinLambda = (float) Math.sin(lambda);
            cosLambda = (float) Math.cos(lambda);
            sinSigma = (float) Math.sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
                    * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda));
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = (float) Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1.0f - sinAlpha * sinAlpha;
            cos2SigmaM = cosSigma - 2.0f * sinU1 * sinU2 / cosSqAlpha;
            C = f / 16.0f * cosSqAlpha * (4.0f + f * (4.0f - 3.0f * cosSqAlpha));
            lambdaP = lambda;
            lambda = L + (1.0f - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma
                    * (-1.0f + 2.0f * cos2SigmaM * cos2SigmaM)));
        }

        if (iterLimit == 0) {
            return Float.NaN; // Formula failed to converge
        }

        float uSq = cosSqAlpha * (a * a - b * b) / (b * b);
        float A = 1.0f + uSq / 16384.0f * (4096.0f + uSq * (-768.0f + uSq * (320.0f - 175.0f * uSq)));
        float B = uSq / 1024.0f * (256.0f + uSq * (-128.0f + uSq * (74.0f - 47.0f * uSq)));
        float deltaSigma = B * sinSigma * (cos2SigmaM + B / 4.0f
                * (cosSigma * (-1.0f + 2.0f * cos2SigmaM * cos2SigmaM) - B / 6.0f
                * cos2SigmaM * (-3.0f + 4.0f * sinSigma * sinSigma)
                * (-3.0f + 4.0f * cos2SigmaM * cos2SigmaM)));
        float s = b * A * (sigma - deltaSigma);

        return s * 0.000539957f; // metres to nautical miles
    }

    /**
     * Calculate the bearing to a destination position using the Vincenty
     * inverse formula for ellipsoids.
     *
     * @param to latitude of destination position
     * @return the bearing to the destination position in degrees
     */
    public final float bearing(LatLon to) {
        if ((Float.compare(latitude, to.latitude) == 0) &&
                (Float.compare(longitude, to.longitude) == 0)) { // Trap equal positions (ignore altitude)
            return 0.0f;
        }

        //   Use WGS-84 constants
        float f = 1.0f / 298.257223563f;

        float L = (float) Math.toRadians(to.longitude) - (float) Math.toRadians(longitude);
        float U1 = (float) Math.atan((1.0 - f) * Math.tan(Math.toRadians(latitude)));
        float U2 = (float) Math.atan((1.0 - f) * Math.tan(Math.toRadians(to.latitude)));
        float sinU1 = (float) Math.sin(U1);
        float cosU1 = (float) Math.cos(U1);
        float sinU2 = (float) Math.sin(U2);
        float cosU2 = (float) Math.cos(U2);
        float lambda = L;
        float lambdaP = (float) (2.0 * Math.PI);
        int iterLimit = 20;

        float sinLambda = 0.0f;
        float cosLambda = 0.0f;
        float sinSigma;
        float cosSigma;
        float sigma;
        float sinAlpha;
        float cosSqAlpha;
        float cos2SigmaM;
        float C;

        while ((Math.abs(lambda - lambdaP) > 1e-12f) && (--iterLimit > 0)) {
            sinLambda =(float) Math.sin(lambda);
            cosLambda = (float) Math.cos(lambda);
            sinSigma = (float) Math.sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda));
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = (float) Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1.0f - sinAlpha * sinAlpha;
            cos2SigmaM = cosSigma - 2.0f * sinU1 * sinU2 / cosSqAlpha;
            C = f / 16.0f * cosSqAlpha * (4.0f + f * (4.0f - 3.0f * cosSqAlpha));
            lambdaP = lambda;
            lambda = L + (1.0f - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1.0f + 2.0f * cos2SigmaM * cos2SigmaM)));
        }

        if (iterLimit == 0) {
            return Float.NaN;  // Formula failed to converge
        }

        float bearing = (float) Math.atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);

        return norm360((float) Math.toDegrees(bearing));
    }

    /**
     * Calculates a new position using a bearing and distance using the Vincenty
     * direct formula for ellipsoids.
     *
     * @param bearing bearing to the new position
     * @param distance distance to the new position in meters
     * @return calculated position
     */
    public final LatLon direct(float bearing, float distance) {
        if (Float.compare(distance, 0.0f) == 0) {
            return this;
        }

        float a = 6378137.0f;
        float b = 6356752.3142f;
        float f = 1.0f / 298.257223563f;

        float radBearing = (float) Math.toRadians(bearing);
        float tanU1 = (1.0f - f) * (float) Math.tan(Math.toRadians(latitude));
        float tanSigma1 = tanU1 / (float) Math.cos(radBearing);
        float u1 = (float) Math.atan(tanU1);
        float sinU1 = (float) Math.sin(u1);
        float cosU1 = (float) Math.cos(u1);
        float sinAlpha = cosU1 * (float) Math.sin(radBearing);
        float cosSqAlpha = 1.0f - sinAlpha * sinAlpha;
        float uSq = cosSqAlpha * (a * a - b * b) / (b * b);
        float A = 1.0f + (uSq / 16384.0f) * (4096.0f + uSq * (-768.0f + uSq * (320.0f - 175.0f * uSq)));
        float B = (uSq / 1024.0f) * (256.0f + uSq * (-128.0f + uSq * (74.0f - 47.0f * uSq)));
        float sigma = distance / (b * A);
        float sigma1 = (float) Math.atan(tanSigma1);
        int iterLimit = 20;

        float lastSigma;
        float twoSigmaM;
        float sinSigma;
        float cosSigma;
        float cos2SigmaM;
        float deltaSigma;

        do {
            lastSigma = sigma;
            twoSigmaM = 2.0f * sigma1 + sigma;
            sinSigma = (float) Math.sin(sigma);
            cosSigma = (float) Math.cos(sigma);
            cos2SigmaM = (float) Math.cos(twoSigmaM);
            deltaSigma = B * sinSigma * (cos2SigmaM + B / 4.0f * (cosSigma * (-1.0f + 2.0f * (float) Math.pow(cos2SigmaM, 2.0)) - B / 6.0f * cos2SigmaM * (-3.0f + 4.0f * (float) Math.pow(sinSigma, 2.0)) * (-3.0f + 4.0f * (float) Math.pow(cos2SigmaM, 2.0))));
            sigma = distance / (b * A) + deltaSigma;
        } while ((--iterLimit > 0) && (Math.abs(sigma - lastSigma) >= 1e-12));

        if (iterLimit == 0) {
            return new LatLon(Float.NaN, Float.NaN); // Formula failed to converge
        }

        twoSigmaM = 2.0f * sigma1 + sigma;
        sinSigma = (float) Math.sin(sigma);
        cosSigma = (float) Math.cos(sigma);
        cos2SigmaM = (float) Math.cos(twoSigmaM);

        float lat = (float) (Math.atan2(sinU1 * cosSigma + cosU1 * sinSigma * Math.cos(radBearing),
                (1.0 - f) * Math.sqrt(Math.pow(sinAlpha, 2.0) +
                        Math.pow(sinU1 * sinSigma - cosU1 * cosSigma * Math.cos(radBearing), 2.0))));

        float lambda = (float) (Math.atan2(sinSigma * Math.sin(radBearing), cosU1 * cosSigma - sinU1 * sinSigma * Math.cos(radBearing)));

        float C = f / 16.0f * cosSqAlpha * (4.0f + f * (4.0f - 3.0f * cosSqAlpha));

        float lambdaP = lambda - (1.0f - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM +
                C * cosSigma * (-1.0f + 2.0f * cos2SigmaM * cos2SigmaM)));
        
        float lon = longitude + (float) Math.toDegrees(lambdaP);

        return new LatLon((float) Math.toDegrees(lat), lon);
    }

    /**
     * Truncate a number so that it is between -90 and 90. Numbers outside this
     * range are truncated to -90 or 90. This for latitudes
     *
     * @param num the number to fix
     * @return the number fixed to the range -90 to 90
     */
    public float fix90(float num) {
        if (Float.compare(num, -90.0f) < 0) {
            return -90.0f;
        }

        if (Float.compare(num, 90.0f) > 0) {
            return 90.0f;
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
    public float fix180(float num) {
        if (Float.compare(num, -180.0f) < 0) {
            return -180.0f;
        }

        if (Float.compare(num, 180.0f) > 0) {
            return 180.0f;
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
    public float norm360(float num) {
        if (Float.compare(num, 0.0f) < 0) {
            return num - (float) (Math.floor(num / 360.0) * 360.0);
//          return Math.IEEEremainder(num, 360.0);
        } else if (Float.compare(num, 360.0f) > 0) {
            return num - (float) (Math.floor(num / 360.0) * 360.0);
//          return Math.IEEEremainder(num, 360.0);
        } else {
            return num;
        }
    }

    /**
     * Convert a string of the format SDDD MM SS or SDDD MM SS.S to a float
     *
     * @param txt a string containing a latitude or longitude
     * @return the latitude or longitude as a float in degrees
     */
    public float fromString(String txt) throws NumberFormatException {
        int txtlen = txt.length();
        int idx = 0;

        char ch = Character.toUpperCase(txt.charAt(0));

        float sgn = 1.0f;

        if (ch == 'N' || ch == 'S' || ch == 'E' || ch == 'W') {
            if (ch == 'S' || ch == 'W') {
                sgn = -1.0f;
            }

            idx++;
        } else {
            throw new NumberFormatException();
        }

        float numd = 0;

        while ((idx < txtlen) && (Character.isDigit(txt.charAt(idx)))) {
            numd = (numd * 10.0f) + (txt.charAt(idx++) - '0');
        }

        if ((idx >= txt.length()) || (txt.charAt(idx++) != ' ')) {
            throw new NumberFormatException();
        }

        float numm = 0;

        while ((idx < txtlen) && (Character.isDigit(txt.charAt(idx)))) {
            numm = (numm * 10.0f) + (txt.charAt(idx++) - '0');
        }

        if (Float.compare(numm, 59.0f) > 0) {
            throw new NumberFormatException();
        }

        if ((idx >= txt.length()) || (txt.charAt(idx++) != ' ')) {
            throw new NumberFormatException();
        }

        float nums = 0;

        while ((idx < txtlen) && (Character.isDigit(txt.charAt(idx)))) {
            nums = (nums * 10.0f) + (txt.charAt(idx++) - '0');
        }

        if (Float.compare(nums, 59.0f) > 0) {
            throw new NumberFormatException();
        }

        float numss = 0;

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

        return sgn * (numd + (numm / 60.0f) + ((nums + (numss / 10)) / 3600.0f));
    }
}
