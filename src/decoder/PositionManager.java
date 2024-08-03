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
    private final DataBlockParser dbp;
    private final Timer timer1;
    private final TimerTask task1;

    public PositionManager(LatLon rxll, DataBlockParser d) {
        pos = new ConcurrentHashMap<>();
        zulu = new ZuluMillis();
        cpr = new CPR();
        dbp = d;
        receiverLatLon = rxll;

        encodeCPR();

        task1 = new DropPosition();
        timer1 = new Timer();
        timer1.scheduleAtFixedRate(task1, 0L, RATE);
    }

    public void close() {
        timer1.cancel();
    }

    /*
     * In case receiver is moving
    */
    public void updateReceiverPosition(LatLon rxll) {
        receiverLatLon = rxll;

        encodeCPR();
    }

    public synchronized int getPositionCount() {
        return pos.size();
    }

    public synchronized boolean hasPosition(String icao) {
        try {
            return pos.containsKey(icao);
        } catch (NullPointerException e) {
            System.err.println("PositionManager::hasPosition Exception during containsKey " + e.toString());
            return false;
        }
    }

    public synchronized void addPosition(String icao, Position position) {
        try {
            pos.put(icao, position);
        } catch (NullPointerException e) {
            System.err.println("PositionManager::addPosition Exception during put " + e.toString());
        }
    }

    private synchronized Position removePosition(String icao) {
        Position position = (Position) null;

        if (pos.containsKey(icao) == true) {
            try {
                position = pos.remove(icao);
            } catch (NullPointerException e) {
                System.err.println("PositionManager::removePosition Exception during remove " + e.toString());
            }
        }

        return position;
    }

    /**
     * Store the lat/lon position on the table keyed by the Aircraft ID
     * 
     * @param icao the ICAO ID
     * @param lat17 the 17-bit latitude
     * @param lon17 the 17-bit longitude
     * @param zulu the time in UTC
     * @param cpr1 the CPR odd/even boolean
     * @param surface the on the surface boolean
     * @param tis the Traffic Information Service boolean
     */
    public void addNewPosition(String icao, int lat17, int lon17, long zulu,
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
             * See if this track is on the table
             */
            if (hasPosition(icao) == true) {

                /*
                 * Yes, it is on the table
                 */
                oldpos = removePosition(icao);      // might become null if it gets deleted in dropPosition

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

                        if (latlon.getLat() != 0.0f && latlon.getLon() != 0.0f) {
                            dbp.updateTrackLatLon(icao, latlon, mode, zulu);
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

                        if (latlon.getLat() != 0.0f && latlon.getLon() != 0.0f) {
                            dbp.updateTrackLatLon(icao, latlon, mode, zulu);
                        }
                    }

                    /*
                     * Replace the position with modified values to table
                     */
                    addPosition(icao, oldpos);
                }
            } else {
                /*
                 * Track wasn't on the table so create a new object
                 */

                addPosition(icao, new Position(icao, zulu, lat17, lon17, cpr1));
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
                    addPosition(pos.getICAO(), pos);            // update table value
                } else if (pos.getUpdateTime() < timeout) {     // delete after a minute
                    removePosition(pos.getICAO());              // remove from table
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
        float yz17Even = (float) Math.floor(Exp17 * cpr.cprModFloat(receiverLatLon.getLat(), DlatEven) / DlatEven + .5);
        float yz17Odd = (float) Math.floor(Exp17 * cpr.cprModFloat(receiverLatLon.getLat(), DlatOdd) / DlatOdd + .5);
        float rlat17Even = DlatEven * ((yz17Even / Exp17) + (float) Math.floor(receiverLatLon.getLat() / DlatEven));
        float rlat17Odd = DlatOdd * ((yz17Odd / Exp17) + (float) Math.floor(receiverLatLon.getLat() / DlatOdd));

        // Surface 19-bits
        float yz19Even = (float) Math.floor(Exp19 * cpr.cprModFloat(receiverLatLon.getLat(), DlatSEven) / DlatSEven + .5);
        float yz19Odd = (float) Math.floor(Exp19 * cpr.cprModFloat(receiverLatLon.getLat(), DlatSOdd) / DlatSOdd + .5);
        float rlat19Even = DlatSEven * ((yz19Even / Exp19) + (float) Math.floor(receiverLatLon.getLat() / DlatSEven));
        float rlat19Odd = DlatSOdd * ((yz19Odd / Exp19) + (float) Math.floor(receiverLatLon.getLat() / DlatSOdd));

        // TIS-B Course 12-bits
        float yz12Even = (float) Math.floor(Exp12 * cpr.cprModFloat(receiverLatLon.getLat(), DlatEven) / DlatEven + .5);
        float yz12Odd = (float) Math.floor(Exp12 * cpr.cprModFloat(receiverLatLon.getLat(), DlatOdd) / DlatOdd + .5);
        float rlat12Even = DlatEven * ((yz12Even / Exp12) + (float) Math.floor(receiverLatLon.getLat() / DlatEven));
        float rlat12Odd = DlatOdd * ((yz12Odd / Exp12) + (float) Math.floor(receiverLatLon.getLat() / DlatOdd));

        int temp = cpr.cprNLFunction(rlat12Odd) - 1;
        temp = (temp == 0) ? 1 : temp;
        
        float dlon12Even = 360.0f / cpr.cprNLFunction(rlat12Even);
        float dlon12Odd = 360.0f / (float) temp;

        temp = cpr.cprNLFunction(rlat17Odd) - 1;
        temp = (temp == 0) ? 1 : temp;
        
        float dlon17Even = 360.0f / cpr.cprNLFunction(rlat17Even);
        float dlon17Odd = 360.0f / (float) temp;

        temp = cpr.cprNLFunction(rlat19Odd) - 1;
        temp = (temp == 0) ? 1 : temp;
        
        float dlon19Even = 90.0f / (float) cpr.cprNLFunction(rlat19Even);
        float dlon19Odd = 90.0f / (float) temp;

        float xz12Even = (float) Math.floor(Exp12 * cpr.cprModFloat(receiverLatLon.getLon(), dlon12Even) / dlon12Even + .5);
        float xz12Odd = (float) Math.floor(Exp12 * cpr.cprModFloat(receiverLatLon.getLon(), dlon12Odd) / dlon12Odd + .5);

        float xz17Even = (float) Math.floor(Exp17 * cpr.cprModFloat(receiverLatLon.getLon(), dlon17Even) / dlon17Even + .5);
        float xz17Odd = (float) Math.floor(Exp17 * cpr.cprModFloat(receiverLatLon.getLon(), dlon17Odd) / dlon17Odd + .5);

        float xz19Even = (float) Math.floor(Exp19 * cpr.cprModFloat(receiverLatLon.getLon(), dlon19Even) / dlon19Even + .5);
        float xz19Odd = (float) Math.floor(Exp19 * cpr.cprModFloat(receiverLatLon.getLon(), dlon19Odd) / dlon19Odd + .5);

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
