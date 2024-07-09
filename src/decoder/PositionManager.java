/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import parser.ZuluMillis;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public final class PositionManager implements IConstants {

    private static final long RATE = 30L * 1000L;               // 30 Seconds
    //
    private long receiverLatitudeTisbCourseEven;
    private long receiverLongitudeTisbCourseEven;
    private long receiverLatitudeAirborneEven;
    private long receiverLongitudeAirborneEven;
    private long receiverLatitudeSurfaceEven;
    private long receiverLongitudeSurfaceEven;
    //
    private long receiverLatitudeTisbCourseOdd;
    private long receiverLongitudeTisbCourseOdd;
    private long receiverLatitudeAirborneOdd;
    private long receiverLongitudeAirborneOdd;
    private long receiverLatitudeSurfaceOdd;
    private long receiverLongitudeSurfaceOdd;
    //
    private LatLon receiverLatLon;
    //
    private final ConcurrentHashMap<String, Position> pos;      // Table needs synchronizaton
    //
    private final ZuluMillis zulu;
    private final CPR cpr;
    private final DataBlockParser df;
    private final Timer timer1;
    private final TimerTask task1;

    public PositionManager(LatLon rxll, DataBlockParser d) {
        pos = new ConcurrentHashMap<>();
        zulu = new ZuluMillis();
        cpr = new CPR();
        df = d;
        receiverLatLon = rxll;

        encodeCPR();

        task1 = new DropPosition();
        timer1 = new Timer();
        timer1.scheduleAtFixedRate(task1, 0L, RATE);
    }

    public void close() {
        timer1.cancel();
    }

    public void updateReceiverPosition(LatLon rxll) {
        receiverLatLon = rxll;

        encodeCPR();
    }

    public synchronized int getPositionCount() {
        return pos.size();
    }

    public synchronized boolean hasPosition(String acid) {
        try {
            return pos.containsKey(acid);
        } catch (NullPointerException e) {
            System.err.println("PositionManager::hasPosition Exception during containsKey " + e.toString());
            return false;
        }
    }

    public synchronized void addPosition(String acid, Position position) {
        try {
            pos.put(acid, position);
        } catch (NullPointerException e) {
            System.err.println("PositionManager::addPosition Exception during put " + e.toString());
        }
    }

    private synchronized Position removePosition(String acid) {
        Position position = (Position) null;

        if (pos.containsKey(acid) == true) {
            try {
                position = pos.remove(acid);
            } catch (NullPointerException e) {
                System.err.println("PositionManager::removePosition Exception during remove " + e.toString());
            }
        }

        return position;
    }

    /**
     * Store the lat/lon position on the table keyed by the Aircraft ID
     * 
     * @param acid the aircraft ID
     * @param lat17 the 17-bit latitude
     * @param lon17 the 17-bit longitude
     * @param zulu the time in UTC
     * @param cpr1 the CPR odd/even boolean
     * @param surface the on the surface boolean
     * @param tis the Traffic Information Service boolean
     */
    public void addNewPosition(String acid, int lat17, int lon17, long zulu,
            boolean cpr1, boolean surface, boolean tis) {
        Position oldpos;
        LatLon latlon;
        long time;
        int mode;
        int longEven = 0;
        int longOdd = 0;
        int latEven = 0;
        int latOdd = 0;

        if (lat17 != 0 && lon17 != 0) {
            /*
             * See if this target is on the table
             */
            if (hasPosition(acid) == true) {

                /*
                 * Yes, it is on the table
                 */
                oldpos = removePosition(acid);      // might become null if it gets deleted in dropPosition

                if (oldpos != (Position) null) {    // Timer Thread goo

                    if (cpr1 == ODD) {
                        // odd frame

                        oldpos.setLatLonOdd(lat17, lon17, zulu);

                        if (oldpos.getEvenFrameTime() != 0L) {
                            /*
                             * If the even frame has a value then we can set the ptime
                             */
                            time = Math.abs(oldpos.getOddFrameTime() - oldpos.getEvenFrameTime());
                        } else {
                            /*
                             * The even frame has no time yet, so ptime will be zero for now
                             */
                            time = 0L;
                        }
                    } else {
                        // even frame

                        oldpos.setLatLonEven(lat17, lon17, zulu);

                        if (oldpos.getOddFrameTime() != 0L) {
                            /*
                             * If the odd frame has a value we can set the ptime
                             */

                            time = Math.abs(oldpos.getEvenFrameTime() - oldpos.getOddFrameTime());
                        } else {
                            /*
                             * The odd frame has no time yet, so ptime will be zero for now
                             */

                            time = 0L;
                        }
                    }

                    oldpos.setProcessTime(time);
                    oldpos.setTimedOut(false);    // we have a hit, so mark position not timed out

                    /*
                     * See if the ProcessTime is less than 10 seconds
                     */
                    if ((time > 0L) && (time <= MAXTIME)) {
                        // ProcessTime non-zero means we have an odd and even position
                        longEven = oldpos.getLonEven();
                        longOdd = oldpos.getLonOdd();
                        latEven = oldpos.getLatEven();
                        latOdd = oldpos.getLatOdd();

                        if (surface == true) {
                            mode = POSITION_MODE_GLOBAL_SURFACE;
                            latlon = cpr.decodeCPRsurface(receiverLatLon, latEven, longEven, latOdd, longOdd, cpr1);
                        } else {
                            mode = POSITION_MODE_GLOBAL_AIRBORNE;
                            latlon = cpr.decodeCPRairborne(latEven, longEven, latOdd, longOdd, cpr1);
                        }

                        if (latlon.getLat() != 0.0 && latlon.getLon() != 0.0) {
                            if (df.hasTarget(acid)) {
                                df.updateTargetLatLon(acid, latlon, mode, zulu);
                            } else {
                                df.createTargetLatLon(acid, tis, latlon, mode, zulu);
                            }
                        }
                    } else if (time == 0L) {
                        /*
                         * If time is zero, maybe we can substitute
                         * the receiver position, or the last airborne position.
                         */
                        if (surface == true) {
                            if (cpr1 == EVEN) {
                                latlon = cpr.decodeCPRrelative(receiverLatLon, latEven, longEven,
                                        cpr1, surface);
                            } else {
                                latlon = cpr.decodeCPRrelative(receiverLatLon, latOdd, longOdd,
                                        cpr1, surface);
                            }

                            mode = POSITION_MODE_RELATIVE_SURFACE;
                        } else {
                            if (cpr1 == EVEN) {
                                latlon = cpr.decodeCPRrelative(new LatLon(latOdd, longOdd),
                                        latEven, longEven, cpr1, surface);
                            } else {
                                latlon = cpr.decodeCPRrelative(new LatLon(latEven, longEven),
                                        latOdd, longOdd, cpr1, surface);
                            }

                            mode = POSITION_MODE_RELATIVE_AIRBORNE;
                        }

                        if (latlon.getLat() != 0.0 && latlon.getLon() != 0.0) {
                            if (df.hasTarget(acid)) {
                                df.updateTargetLatLon(acid, latlon, mode, zulu);
                            } else {
                                df.createTargetLatLon(acid, tis, latlon, mode, zulu);
                            }
                        }
                    }

                    /*
                     * Replace the position with modified values to table
                     */
                    addPosition(acid, oldpos);
                }
            } else {
                /*
                 * Target wasn't on the table so create a new object
                 */

                addPosition(acid, new Position(acid, zulu, lat17, lon17, cpr1));
            }
        }
    }
    
    /*
     * This will look through the Position Hashmap and delete the positions
     * as they timeout (Default 60 seconds).
     */
    private final class DropPosition extends TimerTask {

        @Override
        public void run() {
            long timeout = zulu.getUTCTime() - (60L * 1000L);    // subtract 60 seconds
            long pos_timeout = zulu.getUTCTime() - MAXTIME;

            // Get rid of Positions on the table that haven't been updated
            Enumeration list = pos.elements();

            while (list.hasMoreElements()) {
                Position pos = (Position) list.nextElement();

                if (pos.getUpdateTime() <= pos_timeout) {
                    pos.setTimedOut(true);                      // mark timed out after XX sec
                    addPosition(pos.getACID(), pos);            // update table value
                } else if (pos.getUpdateTime() < timeout) {     // delete after a minute
                    removePosition(pos.getACID());              // remove from table
                }
            }
        }
    }

    /*
     * This method encodes our receiver position into longitude and latitude zones.
     * This may be useful, as well, for a moving receiver.
     *
     * A test value given by the FAA is:
     *
     * 43.054N Latitude = 23025 (even), 7349 (odd) I seem to get 7350
     * 76.06W Longitude = 119938 (even), 16559 (odd)
     *
     * For my position in Hooterville, I get:
     *
     * Latitude = 117591 (even), 104709 (odd)
     * Longitude = 121602 (even), 26000 (odd)
     */
    private void encodeCPR() {
        // Airborne and TIS-B fine
        double yz17Even = Math.floor(Exp17 * cpr.cprModDouble(receiverLatLon.getLat(), DlatEven) / DlatEven + .5);
        double yz17Odd = Math.floor(Exp17 * cpr.cprModDouble(receiverLatLon.getLat(), DlatOdd) / DlatOdd + .5);
        double rlat17Even = DlatEven * ((yz17Even / Exp17) + Math.floor(receiverLatLon.getLat() / DlatEven));
        double rlat17Odd = DlatOdd * ((yz17Odd / Exp17) + Math.floor(receiverLatLon.getLat() / DlatOdd));

        // Surface 19-bits
        double yz19Even = Math.floor(Exp19 * cpr.cprModDouble(receiverLatLon.getLat(), DlatSEven) / DlatSEven + .5);
        double yz19Odd = Math.floor(Exp19 * cpr.cprModDouble(receiverLatLon.getLat(), DlatSOdd) / DlatSOdd + .5);
        double rlat19Even = DlatSEven * ((yz19Even / Exp19) + Math.floor(receiverLatLon.getLat() / DlatSEven));
        double rlat19Odd = DlatSOdd * ((yz19Odd / Exp19) + Math.floor(receiverLatLon.getLat() / DlatSOdd));

        // TIS-B Course 12-bits
        double yz12Even = Math.floor(Exp12 * cpr.cprModDouble(receiverLatLon.getLat(), DlatEven) / DlatEven + .5);
        double yz12Odd = Math.floor(Exp12 * cpr.cprModDouble(receiverLatLon.getLat(), DlatOdd) / DlatOdd + .5);
        double rlat12Even = DlatEven * ((yz12Even / Exp12) + Math.floor(receiverLatLon.getLat() / DlatEven));
        double rlat12Odd = DlatOdd * ((yz12Odd / Exp12) + Math.floor(receiverLatLon.getLat() / DlatOdd));

        int temp = cpr.cprNLFunction(rlat12Odd) - 1;
        temp = (temp == 0) ? 1 : temp;
        double dlon12Even = 360.0 / (double) cpr.cprNLFunction(rlat12Even);
        double dlon12Odd = 360.0 / (double) temp;

        temp = cpr.cprNLFunction(rlat17Odd) - 1;
        temp = (temp == 0) ? 1 : temp;
        double dlon17Even = 360.0 / (double) cpr.cprNLFunction(rlat17Even);
        double dlon17Odd = 360.0 / (double) temp;

        temp = cpr.cprNLFunction(rlat19Odd) - 1;
        temp = (temp == 0) ? 1 : temp;
        double dlon19Even = 90.0 / (double) cpr.cprNLFunction(rlat19Even);
        double dlon19Odd = 90.0 / (double) temp;

        double xz12Even = Math.floor(Exp12 * cpr.cprModDouble(receiverLatLon.getLon(), dlon12Even) / dlon12Even + .5);
        double xz12Odd = Math.floor(Exp12 * cpr.cprModDouble(receiverLatLon.getLon(), dlon12Odd) / dlon12Odd + .5);

        double xz17Even = Math.floor(Exp17 * cpr.cprModDouble(receiverLatLon.getLon(), dlon17Even) / dlon17Even + .5);
        double xz17Odd = Math.floor(Exp17 * cpr.cprModDouble(receiverLatLon.getLon(), dlon17Odd) / dlon17Odd + .5);

        double xz19Even = Math.floor(Exp19 * cpr.cprModDouble(receiverLatLon.getLon(), dlon19Even) / dlon19Even + .5);
        double xz19Odd = Math.floor(Exp19 * cpr.cprModDouble(receiverLatLon.getLon(), dlon19Odd) / dlon19Odd + .5);

        receiverLatitudeTisbCourseEven = (long) yz12Even & 0x0FFF;
        receiverLongitudeTisbCourseEven = (long) xz12Even & 0x0FFF;
        receiverLatitudeTisbCourseOdd = (long) yz12Odd & 0x0FFF;
        receiverLongitudeTisbCourseOdd = (long) xz12Odd & 0x0FFF;

        receiverLatitudeAirborneEven = (long) yz17Even & 0x1FFFF;
        receiverLongitudeAirborneEven = (long) xz17Even & 0x1FFFF;
        receiverLatitudeAirborneOdd = (long) yz17Odd & 0x1FFFF;
        receiverLongitudeAirborneOdd = (long) xz17Odd & 0x1FFFF;

        receiverLatitudeSurfaceEven = (long) yz19Even & 0x1FFFF;
        receiverLongitudeSurfaceEven = (long) xz19Even & 0x1FFFF;
        receiverLatitudeSurfaceOdd = (long) yz19Odd & 0x1FFFF;
        receiverLongitudeSurfaceOdd = (long) xz19Odd & 0x1FFFF;
    }
}
