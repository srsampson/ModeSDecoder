/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public interface IConstants {

    public static final long MAXTIME = 10L * 1000L;         // 10 Seconds
    //
    public static final double Exp12 = 4096.0;              // TIS-B Course Position Bins 2^12
    public static final double Exp17 = 131072.0;            // Airborne Position Bins 2^17
    public static final double Exp19 = 524288.0;            // Surface Position Bins 2^19
    //
    public static final double NAUTICAL_MILES = 0.000539957;// conversion from meters
    public static final double TAU = Math.PI * 2.0;
    //
    public static final double DlatEven = (360.0 / 60.0);   // Airborne Even
    public static final double DlatOdd = (360.0 / 59.0);    // Airborne Odd
    public static final double DlatSEven = (90.0 / 60.0);   // Surface Even
    public static final double DlatSOdd = (90.0 / 59.0);    // Surface Odd
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
