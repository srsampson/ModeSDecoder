/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public interface ITrueHeading {

    /**
     * Calculate true north target heading from the n/s and e/w velocity in knots
     *
     * @param velocityn_s an integer velocity north/south
     * @param velocitye_w an integer velocity east/west
     * @param signlat an integer sign bit for latitude (0 = North, 1 = South)
     * @param signlon an integer sign bit for longitude (0 = East, 1 = West)
     * @return double representing heading in degrees true north
     */
    public double trueHeading(int velocityn_s, int velocitye_w, int signlat, int signlon);
}
