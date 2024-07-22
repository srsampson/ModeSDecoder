/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public final class TrueHeading implements ITrueHeading, IConstants {

    @Override
    public float trueHeading(int velocityn_s, int velocitye_w, int signlat, int signlon) {
        // do a sanity check (shouldn't ever be true because we check before call)

        if (velocityn_s == -1 || velocitye_w == -1) {
            return 0.0f;
        }

        // Check for divide by zero

        if (velocityn_s == 0) {
            if (signlon == 0) { // 0 == East
                return 90.0f;
            } else {
                return 270.0f;
            }
        }

        float trueHeading = (float) Math.toDegrees(Math.atan((float) velocitye_w / (float) velocityn_s));

        if (signlat == 1) { // 1 == South
            if (signlon == 0) {    // 0 == East
                trueHeading = 180.0f - trueHeading;
            } else {
                trueHeading = 180.0f + trueHeading;
            }
        } else {
            if (signlon == 1) {  // 1 == West
                trueHeading = 360.0f - trueHeading;
            }
        }

        return trueHeading;
    }
}
