/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public interface IConstants {

    public static final long MAXTIME = 10L * 1000L;         // 10 Seconds
    //
    public static final float Exp12 = 4096.0f;              // TIS-B Course Position Bins 2^12
    public static final float Exp17 = 131072.0f;            // Airborne Position Bins 2^17
    public static final float Exp19 = 524288.0f;            // Surface Position Bins 2^19
    //
    public static final float NAUTICAL_MILES = 0.000539957f;// conversion from meters
    public static final float TAU = (float) (Math.PI * 2.0);
    //
    public static final float DlatEven = (360.0f / 60.0f);   // Airborne Even
    public static final float DlatOdd = (360.0f / 59.0f);    // Airborne Odd
    public static final float DlatSEven = (90.0f / 60.0f);   // Surface Even
    public static final float DlatSOdd = (90.0f / 59.0f);    // Surface Odd
    //
    public static final boolean EVEN = false;
    public static final boolean ODD = true;
    //
    public static final int TRACK_MODE_STANDBY = 0;
    public static final int TRACK_MODE_NORMAL = 1;
    public static final int TRACK_MODE_IDENT = 2;
    public static final int TRACK_MODE_GLOBAL = 3;
    public static final int TRACK_MODE_COAST = 4;
    //
    public static final int POSITION_MODE_UNKNOWN = 0;
    public static final int POSITION_MODE_GLOBAL_SURFACE = 1;
    public static final int POSITION_MODE_GLOBAL_AIRBORNE = 2;
    public static final int POSITION_MODE_RELATIVE_SURFACE = 3;
    public static final int POSITION_MODE_RELATIVE_AIRBORNE = 4;
}
