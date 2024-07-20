/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import parser.BufferDataBlocks;
import parser.Config;
import parser.DataBlock;
import parser.ZuluMillis;

/**
 * A class to decode the received packets and put them into tracks
 *
 * This is the main entry point for applications
 */
public final class DataBlockParser extends Thread {

    private static final long RATE1 = 30L * 1000L;              // 30 seconds
    private static final long RATE2 = 5L * 1000L;               // 5 seconds
    private static final long RATE3 = 60L * 1000L;         // 1 minute
    //
    private final ConcurrentHashMap<String, Track> targets;
    //
    private final Thread process;
    private final BufferDataBlocks buf;
    private final LatLon receiverLatLon;
    private final PositionManager pm;
    private final NConverter nconverter;
    private final ZuluMillis zulu;
    private DataBlock dbk;
    //
    private Connection db1;
    private Connection db2;
    private Statement query;
    private Statement querytt;
    private final Config config;

    private int radarid;
    private final long targetTimeout;
    private long radarscan;
    //
    private final Timer targetTimer;
    //
    private final TimerTask targetTimeoutTask;
    
    private static boolean EOF;
    private String data;
    private String acid;
    private String callsign;
    private String squawk;
    //
    private boolean isOnGround;
    private boolean emergency;
    private boolean alert;
    private boolean spi;
    private boolean si;
    //
    private long data56;
    private long detectTime;
    //
    private float groundSpeed;
    private float trueHeading;
    private float magneticHeading;
    private float airspeed;
    //
    private int vSpeed;
    private int category;
    private int subtype3;
    private int altitude;
    private int radarIID;
    //
    private final Timer timer1;
    private final Timer timer2;
    private final Timer timer3;
    //
    private final TimerTask task1;
    private final TimerTask task2;
    private final TimerTask task3;

    public DataBlockParser(Config cf, LatLon ll, BufferDataBlocks bd) {
        zulu = new ZuluMillis();
        config = cf;
        receiverLatLon = ll;
        buf = bd;

        radarid = cf.getRadarID();
        radarscan = (long) cf.getRadarScanTime() * 1000L;
        acid = "";
        callsign = "";
        squawk = "";
        EOF = false;        

        String connectionURL = config.getDatabaseURL();

        Properties properties = new Properties();
        properties.setProperty("user", config.getDatabaseLogin());
        properties.setProperty("password", config.getDatabasePassword());
        properties.setProperty("useSSL", "false");
        properties.setProperty("allowPublicKeyRetrieval", "true");
        properties.setProperty("serverTimezone", "UTC");

        /*
         * You need the ODBC MySQL driver library in the same directory you have
         * the executable JAR file of this program, but under a lib directory.
         */
        try {
            db1 = DriverManager.getConnection(connectionURL, properties);
        } catch (SQLException e2) {
            System.err.println("DataBlockParser Fatal: Unable to open database 1 " + connectionURL + " " + e2.getLocalizedMessage());
            System.exit(0);
        }

        try {
            db2 = DriverManager.getConnection(connectionURL, properties);
        } catch (SQLException e3) {
            System.err.println("DataBlockParser Fatal: Unable to open database 2" + connectionURL + " " + e3.getLocalizedMessage());
            System.exit(0);
        }
        
        targets = new ConcurrentHashMap<>();

        pm = new PositionManager(receiverLatLon, this);
        nconverter = new NConverter();

        targetTimeout = config.getDatabaseTargetTimeout() * 60L * 1000L;
        targetTimeoutTask = new TargetTimeoutThread();

        targetTimer = new Timer();
        targetTimer.scheduleAtFixedRate(targetTimeoutTask, 0L, RATE1); // Update targets every 30 seconds

        task1 = new UpdateReports();
        timer1 = new Timer();
        timer1.scheduleAtFixedRate(task1, 0L, RATE1);

        task2 = new UpdateTrackQuality();
        timer2 = new Timer();
        timer2.scheduleAtFixedRate(task2, 10L, RATE2);
        
        task3 = new removeTCASAlerts();
        timer3 = new Timer();
        timer3.scheduleAtFixedRate(task3, 14L, RATE3);

        process = new Thread(this);
        process.setName("DataBlockParser");
        process.setPriority(Thread.NORM_PRIORITY);
    }

    @Override
    public void start() {
        process.start();
    }

    public void close() {
        EOF = true;
        
        try {
            db1.close();
            db2.close();
        } catch (SQLException e) {
            System.out.println("DataBlockParser::close Closing Bug " + e.getMessage());
            System.exit(0);
        }

        timer1.cancel();
        timer2.cancel();
        timer3.cancel();

        pm.close();
    }

    /**
     * TargetTimeoutThread
     *
     * A TimerTask class to move target to history after fade-out,
     */
    private class TargetTimeoutThread extends TimerTask {

        private long time;
        private long timeout;

        @Override
        public void run() {
            String update;

            time = zulu.getUTCTime();
            timeout = time - targetTimeout;    // timeout in minutes

            /*
             * This also converts the timestamp to SQL format, as the history is
             * probably not going to need any further computations.
             */
            update = String.format(
                    "INSERT INTO targethistory ("
                    + "flight_id,"
                    + "radar_id,"
                    + "acid,"
                    + "utcdetect,"
                    + "utcfadeout,"
                    + "radariid,"
                    + "si,"
                    + "altitude,"
                    + "altitudedf00,"
                    + "altitudedf04,"
                    + "altitudedf16,"
                    + "altitudedf17,"
                    + "altitudedf18,"
                    + "altitudedf20,"
                    + "groundSpeed,"
                    + "groundTrack,"
                    + "gsComputed,"
                    + "gtComputed,"
                    + "callsign,"
                    + "latitude,"
                    + "longitude,"
                    + "verticalRate,"
                    + "verticalTrend,"
                    + "squawk,"
                    + "alert,"
                    + "emergency,"
                    + "spi,"
                    + "onground,"
                    + "hijack,"
                    + "comm_out,"
                    + "hadAlert,"
                    + "hadEmergency,"
                    + "hadSPI"
                    + ") SELECT flight_id,"
                    + "radar_id,"
                    + "acid,"
                    + "FROM_UNIXTIME(utcdetect/1000),"
                    + "FROM_UNIXTIME(utcupdate/1000),"
                    + "radariid,"
                    + "si,"
                    + "altitude,"
                    + "altitudedf00,"
                    + "altitudedf04,"
                    + "altitudedf16,"
                    + "altitudedf17,"
                    + "altitudedf18,"
                    + "altitudedf20,"
                    + "groundSpeed,"
                    + "groundTrack,"
                    + "gsComputed,"
                    + "gtComputed,"
                    + "callsign,"
                    + "latitude,"
                    + "longitude,"
                    + "verticalRate,"
                    + "verticalTrend,"
                    + "squawk,"
                    + "alert,"
                    + "emergency,"
                    + "spi,"
                    + "onground,"
                    + "hijack,"
                    + "comm_out,"
                    + "hadAlert,"
                    + "hadEmergency,"
                    + "hadSPI"
                    + " FROM target WHERE target.utcupdate <= %d",
                    timeout);

            try {
                querytt = db2.createStatement();
                querytt.executeUpdate(update);
                querytt.close();
            } catch (SQLException tt1) {
                try {
                    querytt.close();
                } catch (SQLException tt2) {
                }
                System.out.println("TargetTimeoutThread::run targethistory SQL Error: " + update + " " + tt1.getMessage());
            }

            update = String.format("DELETE FROM target WHERE utcupdate <= %d", timeout);

            try {
                querytt = db2.createStatement();
                querytt.executeUpdate(update);
                querytt.close();
            } catch (SQLException tt3) {
                try {
                    querytt.close();
                } catch (SQLException tt4) {
                }
                System.out.println("DataBlockParser::TargetTimeoutThread delete SQL Error: " + update + " " + tt3.getMessage());
            }
        }
    }

    public boolean hasTarget(String acid) throws NullPointerException {
        synchronized (targets) {
            return targets.containsKey(acid);
        }
    }

    public Track getTarget(String acid) throws NullPointerException {
        synchronized (targets) {
            return targets.get(acid);
        }
    }

    /**
     * Method to return a collection of all targets.
     *
     * @return a vector Representing all target objects.
     */
    public List<Track> getAllTargets() throws NullPointerException {
        List<Track> result = new ArrayList<>();

        synchronized (targets) {
            result.addAll(targets.values());
        }
        
        return result;
    }

    /**
     * Method to return a collection of all updated targets.
     *
     * @return a vector representing all the targets that have been updated
     */
    public List<Track> getAllUpdatedTargets() throws NullPointerException {
        List<Track> result = new ArrayList<>();
        List<Track> targetlist;
        
        try {
            targetlist = getAllTargets();
        } catch (NullPointerException e) {
            return result;  // empty
        }

        if (targetlist.isEmpty() == false) {
            for (Track tgt : targetlist) {
                if (tgt.getUpdated() == true) {
                    result.add(tgt);
                }
            }
        }
        
        return result;
    }

    /**
     * Put target on queue after being created or updated
     *
     * @param acid a String representing the Aircraft ID
     * @param obj an Object representing the Target data
     */
    public void addTarget(String acid, Track obj) throws NullPointerException {
       synchronized (targets) {
           targets.put(acid, obj);
       }
    }

    public void removeTarget(String acid) throws NullPointerException {
       synchronized (targets) {
            if (targets.containsKey(acid) == true) {
                targets.remove(acid);
            }
       }
    }

    /*
     * Method to add a new TCAS alert for this target
     * into the database table
     */
    public void insertTCASAlert(String hexid, long data56, long time) {
        String update;
        int alt16;
        
        /*
         * See if this is even a valid target
         */
        if (hasTarget(hexid)) {
            
            System.out.println("TCAS Entry %s" + " " + hexid);
            
            Track trk = getTarget(hexid);
            alt16 = trk.getAltitudeDF16();  // might be -9999 (null)

            TCASAlert tcas = new TCASAlert(data56, time, alt16);

            /*
             * Some TCAS are just advisory, no RA generated
             * So we keep these off the table, as they are basically junk.
             * 
             * Note: TCAS class only sets time if RA is active.
             */
            if (tcas.getUpdateTime() == 0L) {
                return; // No work -- bye
            }
            
            update = String.format("INSERT INTO tcasalerts ("
                    + "utcupdate,"
                    + "ttibits,"
                    + "threatid,"
                    + "threatrelativealtitude,"
                    + "altitude,"
                    + "bearing,"
                    + "range,"
                    + "arabits,"
                    + "racbits,"
                    + "active_ra,"
                    + "single_ra,"
                    + "multiple_ra,"
                    + "multiplethreats,"
                    + "threatterminated) VALUES ("
                    + "'%s',%d,%d,'%s',%d,%d,%f,%f,%d,%d,%d,%d,%d,%d,%d)",
                    acid,
                    tcas.getUpdateTime(),
                    tcas.getThreatTypeIndicator(),
                    tcas.getThreatICAOID(),
                    tcas.getThreatRelativeAltitude(),
                    tcas.getThreatAltitude(),
                    tcas.getThreatBearing(),
                    tcas.getThreatRange(),
                    tcas.getARABits(),
                    tcas.getRACBits(),
                    tcas.getActiveRA() ? 1 : 0,
                    tcas.getSingleRA() ? 1 : 0,
                    tcas.getMultipleRA() ? 1 : 0,
                    tcas.getHasMultipleThreats() ? 1 : 0,
                    tcas.getThreatTerminated() ? 1 : 0);

            try {
                querytt = db2.createStatement();
                querytt.executeUpdate(update);
                querytt.close();
            } catch (SQLException tt3) {
                try {
                    querytt.close();
                } catch (SQLException tt4) {
                }
                System.out.println("DataBlockParser::insertTCASAlert insert SQL Error: " + update + " " + tt3.getMessage());
            }
        }
    }

    /*
     * This will look through the TCAS database every minute and delete
     * entries that are over 3 minutes old.  In that case the target has
     * probably flown out of threat.
     */
    private class removeTCASAlerts extends TimerTask {
        @Override
        public void run() {
            long time = zulu.getUTCTime() - (3L * 60L * 1000L);         // subtract 3 minutes
        
            String update = String.format("DELETE FROM tcasalerts WHERE utcupdate <= %d", time);

            try {
                querytt = db2.createStatement();
                querytt.executeUpdate(update);
                querytt.close();
            } catch (SQLException tt3) {
                try {
                    querytt.close();
                } catch (SQLException tt4) {
                }
                System.out.println("DataBlockParser::removeTCASAlerts delete SQL Error: " + update + " " + tt3.getMessage());
            }
        }
    }

    /*
     * This will look through the Track table every 30 seconds and delete
     * entries that are over X minutes old.  In that case the target has
     * probably landed or faded-out from coverage.
     */
    private class UpdateReports extends TimerTask {

        private List<Track> targets;
        private long targetTime;

        @Override
        public void run() {
            long currentTime = zulu.getUTCTime();
            long delta;

            try {
                targets = getAllTargets();
            } catch (NullPointerException te) {
                return; // No targets found
            }

            for (Track id : targets) {
                try {
                    targetTime = id.getUpdatedTime();

                    if (targetTime != 0L) {
                        // find the reports that haven't been updated in X minutes
                        delta = Math.abs(currentTime - id.getUpdatedTime());

                        if (delta >= targetTimeout) {
                            removeTarget(id.getAircraftID());
                        }
                    }
                } catch (NullPointerException e2) {
                    // ignore
                }
            }
        }
    }

    /*
     * This will look through the Track local table and decrement track quality
     * every 30 seconds that the lat/lon position isn't updated. This timer task
     * is run every 5 seconds.
     */
    private class UpdateTrackQuality extends TimerTask {

        private List<Track> targets;
        private long delta;
        private long currentTime;

        @Override
        public void run() {
            currentTime = zulu.getUTCTime();
            delta = 0L;
            String acid;

            try {
                targets = getAllTargets();
            } catch (NullPointerException te) {
                return; // No targets found
            }

            for (Track id : targets) {
                try {
                    if (id.getTrackQuality() > 0) {
                        acid = id.getAircraftID();

                        // find the idStatus reports that haven't been position updated in 30 seconds
                        delta = Math.abs(currentTime - id.getUpdatedPositionTime());

                        if (delta >= RATE1) {
                            id.decrementTrackQuality();
                            id.setUpdatedTime(currentTime);
                            addTarget(acid, id);   // overwrite
                        }
                    }
                } catch (NullPointerException e1) {
                    // not likely
                }
            }
        }
    }

    private void updateTargetMagneticHeadingIAS(String hexid, float head, float ias, int vvel, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setHeading(head);
            tgt.setIAS(ias);
            tgt.setVerticalRate(vvel);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetMagneticHeadingTAS(String hexid, float head, float tas, int vvel, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setHeading(head);
            tgt.setTAS(tas);
            tgt.setVerticalRate(vvel);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetCallsign(String hexid, String cs, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setCallsign(cs);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetCallsign(String hexid, String cs, int category, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setCallsign(cs);
            tgt.setCategory(category);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF00(String hexid, int alt, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setAltitudeDF00(alt);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF04(String hexid, int alt, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setAltitudeDF04(alt);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF16(String hexid, int alt, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setAltitudeDF16(alt);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF17(String hexid, int alt, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setAltitudeDF17(alt);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF18(String hexid, int alt, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setAltitudeDF18(alt);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF20(String hexid, int alt, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setAltitudeDF20(alt);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetGroundSpeedTrueHeading(String hexid, float gs, float th, int vs, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setGroundSpeed(gs);
            tgt.setGroundTrack(th);
            tgt.setVerticalRate(vs);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetRadarID(String hexid, int iid, boolean si, long time) {
        try {
            Track trk = getTarget(hexid);
            trk.setRadarIID(iid);
            trk.setSI(si);
            trk.setUpdatedTime(time);
            addTarget(hexid, trk);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetSquawk(String hexid, String sq, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setSquawk(sq);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetBoolean(String hexid, boolean onground, boolean emergency, boolean alert, boolean spi, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setAlert(alert, emergency, spi);
            tgt.setOnGround(onground);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetOnGround(String hexid, boolean onground, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setOnGround(onground);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    public void updateTargetLatLon(String hexid, LatLon latlon, int mode, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setPosition(latlon, mode, time);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    public void createTargetLatLon(String hexid, boolean tis, LatLon latlon, int mode, long time) {
        try {
            Track tgt = new Track(hexid, tis);

            tgt.setPosition(latlon, mode, time);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    /**
     * Target detection processing and adding to the Target library.
     *
     * Decode Mode-S Short (56-Bit) and Long (112-Bit) Packets
     */
    @Override
    public void run() {
        ResultSet rs = null;
        String queryString;
        int ground, exists;
        long time;

        while (EOF == false) {
            int qsize = buf.getQueueSize();
            
            if (qsize == 0) {
                continue;
            }

            for (int i = 0; i < qsize; i++) {
                dbk = buf.popData();

                data = dbk.getData();
                detectTime = dbk.getUTCTime();

                int df5 = ((Integer.parseInt(data.substring(0, 2), 16)) >>> 3) & 0x1F;


                /*
                 * Most decoders pass a lot of garble packets, so we
                 * first check if DF11 or DF17/18 have validated
                 * the packet by calling hasTarget().  If not, then
                 * it is garbled.
                 *
                 * Note: The DF code itself may be garbled
                 */
                switch (df5) {
                    case 0:
                        DownlinkFormat00 df00 = new DownlinkFormat00(data, detectTime);
                        acid = df00.getACID();

                        try {
                            if (hasTarget(acid)) {           // must be valid then
                                altitude = df00.getAltitude();
                                isOnGround = df00.getIsOnGround();      // true if vs1 == 1

                                updateTargetAltitudeDF00(acid, altitude, detectTime);
                                updateTargetOnGround(acid, isOnGround, detectTime);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
                        }
                        break;
                    case 4:
                        DownlinkFormat04 df04 = new DownlinkFormat04(data, detectTime);
                        acid = df04.getACID();

                        try {
                            if (hasTarget(acid)) {
                                altitude = df04.getAltitude();
                                isOnGround = df04.getIsOnGround();
                                alert = df04.getIsAlert();
                                spi = df04.getIsSPI();
                                emergency = df04.getIsEmergency();

                                updateTargetAltitudeDF04(acid, altitude, detectTime);
                                updateTargetBoolean(acid, isOnGround, emergency, alert, spi, detectTime);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
                        }
                        break;
                    case 5:
                        DownlinkFormat05 df05 = new DownlinkFormat05(data, detectTime);
                        acid = df05.getACID();

                        try {
                            if (hasTarget(acid)) {
                                squawk = df05.getSquawk();
                                isOnGround = df05.getIsOnGround();
                                alert = df05.getIsAlert();
                                spi = df05.getIsSPI();
                                emergency = df05.getIsEmergency();

                                updateTargetSquawk(acid, squawk, detectTime);
                                updateTargetBoolean(acid, isOnGround, emergency, alert, spi, detectTime);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
                        }
                        break;
                    case 11:
                        DownlinkFormat11 df11 = new DownlinkFormat11(data, detectTime);
                        acid = df11.getACID();

                        if (df11.isValid()) {
                            /*
                             * See if Target already exists
                             */
                            try {
                                if (!hasTarget(acid)) {
                                    /*
                                     * New Target
                                     */
                                    Track t = new Track(acid, false);  // false == not TIS

                                    t.setRegistration(nconverter.icao_to_n(acid));
                                    addTarget(acid, t);
                                }
                            } catch (NullPointerException np) {
                                /*
                                 * Not likely to occur
                                 */
                                System.err.println(np);
                                break;
                            }

                            radarIID = df11.getRadarIID();
                            si = df11.getSI();
                            updateTargetRadarID(acid, radarIID, si, detectTime);

                            isOnGround = df11.getIsOnGround();
                            updateTargetOnGround(acid, isOnGround, detectTime);
                        }
                        break;
                    case 16:
                        DownlinkFormat16 df16 = new DownlinkFormat16(data, detectTime);
                        acid = df16.getACID();

                        try {
                            if (hasTarget(acid)) {
                                isOnGround = df16.getIsOnGround();
                                altitude = df16.getAltitude();

                                updateTargetAltitudeDF16(acid, altitude, detectTime);
                                updateTargetOnGround(acid, isOnGround, detectTime);

                                if (df16.getBDS() == 0x30) {   // BDS 3,0
                                    /*
                                     * Some planes send Zero's
                                     */
                                    data56 = df16.getData56();

                                    if (data56 != 30000000000000L) {
                                        insertTCASAlert(acid, detectTime, data56);
                                    }
                                }
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
                        }
                        break;
                    case 17:
                        DownlinkFormat17 df17 = new DownlinkFormat17(data, detectTime, pm);
                        acid = df17.getACID();

                        if (df17.isValid() == true) {
                            /*
                             * See if Target already exists
                             */
                            try {
                                if (hasTarget(acid) == false) {
                                    /*
                                     * New Target
                                     */
                                    Track t = new Track(acid, false);  // false == not TIS

                                    t.setRegistration(nconverter.icao_to_n(acid));
                                    addTarget(acid, t);
                                }
                            } catch (NullPointerException np) {
                                /*
                                 * Not likely to occur
                                 */
                                System.err.println(np);
                                break;
                            }
                        }

                        switch (df17.getFormatType()) {
                            case 0:
                                // No position information (may have baro alt)
                                break;
                            case 1: // Cat D
                            case 2: // Cat C
                            case 3: // Cat B
                            case 4: // Cat A

                                // Identification and Category Type
                                category = df17.getCategory();
                                callsign = df17.getCallsign();

                                updateTargetCallsign(acid, callsign, category, detectTime);
                                break;
                            case 5:
                            case 6:
                            case 7:
                            case 8:
                                // Surface Position

                                isOnGround = df17.getIsOnGround();
                                emergency = df17.getIsEmergency();
                                alert = df17.getIsAlert();
                                spi = df17.getIsSPI();

                                updateTargetBoolean(acid, isOnGround, emergency, alert, spi, detectTime);
                                break;
                            case 9:
                            case 10:
                            case 11:
                            case 12:
                            case 13:
                            case 14:
                            case 15:
                            case 16:
                            case 17:
                            case 18:
                                // Airborne Position with barometric altitude
                                isOnGround = df17.getIsOnGround();
                                emergency = df17.getIsEmergency();
                                alert = df17.getIsAlert();
                                spi = df17.getIsSPI();

                                updateTargetBoolean(acid, isOnGround, emergency, alert, spi, detectTime);

                                altitude = df17.getAltitude();
                                updateTargetAltitudeDF17(acid, altitude, detectTime);

                                break;
                            case 19:
                                subtype3 = df17.getSubType();

                                switch (subtype3) {
                                    case 1:     // gndspeed normal lsb=1knot
                                    case 2:     // gndspeed supersonic lsb=4knots
                                        groundSpeed = df17.getGroundSpeed();
                                        trueHeading = df17.getTrueHeading();
                                        vSpeed = df17.getVspeed();

                                        if (trueHeading != -1.0) {
                                            updateTargetGroundSpeedTrueHeading(acid, groundSpeed, trueHeading, vSpeed, detectTime);
                                        }
                                        break;
                                    case 3: // subsonic
                                    case 4: // supersonic

                                        // Decode Heading and Airspeed, Groundspeed/TrueHeading is not known
                                        if (df17.getMagneticFlag()) {
                                            magneticHeading = df17.getMagneticHeading();
                                            airspeed = df17.getAirspeed();
                                            vSpeed = df17.getVspeed();

                                            if (df17.getTasFlag() == false) {
                                                updateTargetMagneticHeadingIAS(acid, magneticHeading, airspeed, vSpeed, detectTime);
                                            } else {
                                                updateTargetMagneticHeadingTAS(acid, magneticHeading, airspeed, vSpeed, detectTime);
                                            }
                                        }
                                }
                        }
                        break;
                    case 18:
                        DownlinkFormat18 df18 = new DownlinkFormat18(data, detectTime, pm);
                        acid = df18.getACID();

                        if (df18.isValid() == true) {
                            /*
                             * See if Target already exists
                             */
                            try {
                                if (hasTarget(acid) == false) {
                                    /*
                                     * New Target
                                     */
                                    Track t = new Track(acid, true);  // true == TIS

                                    t.setRegistration(nconverter.icao_to_n(acid));
                                    addTarget(acid, t);
                                }
                            } catch (NullPointerException np) {
                                /*
                                 * Not likely to occur
                                 */
                                System.err.println(np);
                            }
                        }

                        switch (df18.getFormatType()) {
                            case 0:
                                // No position information (may have baro alt)
                                break;
                            case 1: // Cat D
                            case 2: // Cat C
                            case 3: // Cat B
                            case 4: // Cat A

                                // Identification and Category Type
                                category = df18.getCategory();
                                callsign = df18.getCallsign();

                                updateTargetCallsign(acid, callsign, category, detectTime);
                                break;
                            case 5:
                            case 6:
                            case 7:
                            case 8:
                                // Surface Position

                                isOnGround = df18.getIsOnGround();
                                emergency = df18.getIsEmergency();
                                alert = df18.getIsAlert();
                                spi = df18.getIsSPI();

                                updateTargetBoolean(acid, isOnGround, emergency, alert, spi, detectTime);
                                break;
                            case 9:
                            case 10:
                            case 11:
                            case 12:
                            case 13:
                            case 14:
                            case 15:
                            case 16:
                            case 17:
                            case 18:
                                // Airborne Position with barometric altitude
                                isOnGround = df18.getIsOnGround();
                                emergency = df18.getIsEmergency();
                                alert = df18.getIsAlert();
                                spi = df18.getIsSPI();

                                updateTargetBoolean(acid, isOnGround, emergency, alert, spi, detectTime);

                                altitude = df18.getAltitude();
                                updateTargetAltitudeDF18(acid, altitude, detectTime);
                                break;
                            case 19:
                                subtype3 = df18.getSubType();

                                switch (subtype3) {
                                    case 1:     // gndspeed normal lsb=1knot
                                    case 2:     // gndspeed supersonic lsb=4knots
                                        groundSpeed = df18.getGroundSpeed();
                                        trueHeading = df18.getTrueHeading();
                                        vSpeed = df18.getVspeed();

                                        if (!(Float.compare(trueHeading, -1.0f) == 0)) {
                                            updateTargetGroundSpeedTrueHeading(acid, groundSpeed, trueHeading, vSpeed, detectTime);
                                        }
                                        break;
                                    case 3: // subsonic
                                    case 4: // supersonic

                                        // Decode Heading and Airspeed, Groundspeed/TrueHeading is not known
                                        if (df18.getMagneticFlag()) {
                                            magneticHeading = df18.getMagneticHeading();
                                            airspeed = df18.getAirspeed();
                                            vSpeed = df18.getVspeed();

                                            if (df18.getTasFlag() == false) {
                                                updateTargetMagneticHeadingIAS(acid, magneticHeading, airspeed, vSpeed, detectTime);
                                            } else {
                                                updateTargetMagneticHeadingTAS(acid, magneticHeading, airspeed, vSpeed, detectTime);
                                            }
                                        }
                                }
                        }
                        break;
                    case 19: // Military Squitter
                        break;
                    case 20:
                        DownlinkFormat20 df20 = new DownlinkFormat20(data, detectTime);
                        acid = df20.getACID();

                        try {
                            if (hasTarget(acid)) {
                                altitude = df20.getAltitude();
                                isOnGround = df20.getIsOnGround();
                                emergency = df20.getIsEmergency();
                                alert = df20.getIsAlert();
                                spi = df20.getIsSPI();

                                updateTargetAltitudeDF20(acid, altitude, detectTime);
                                updateTargetBoolean(acid, isOnGround, emergency, alert, spi, detectTime);

                                int bds = df20.getBDS();
                                data56 = df20.getData56();

                                if (bds == 0x20) {             // BDS 2,0
                                    callsign = df20.getCallsign();
                                    updateTargetCallsign(acid, callsign, detectTime);
                                } else if (bds == 0x30) {      // BDS 3,0
                                    /*
                                     * Some planes send Zero's for some damned reason
                                     */
                                    if (data56 != 30000000000000L) {
                                        insertTCASAlert(acid, detectTime, data56);
                                    }
                                }
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
                        }
                        break;
                    case 21:
                        DownlinkFormat21 df21 = new DownlinkFormat21(data, detectTime);
                        acid = df21.getACID();

                        try {
                            if (hasTarget(acid)) {
                                squawk = df21.getSquawk();
                                isOnGround = df21.getIsOnGround();
                                emergency = df21.getIsEmergency();
                                alert = df21.getIsAlert();
                                spi = df21.getIsSPI();

                                updateTargetSquawk(acid, squawk, detectTime);
                                updateTargetBoolean(acid, isOnGround, emergency, alert, spi, detectTime);

                                int bds = df21.getBDS();
                                data56 = df21.getData56();

                                switch (bds) {      // Bunch more available for decode
                                    case 0x20:      // BDS 2,0 Callsign
                                        callsign = df21.getCallsign();
                                        updateTargetCallsign(acid, callsign, detectTime);
                                        break;
                                    case 0x30:      // BDS 3,0 TCAS
                                        /*
                                         * Some planes send Zero's for some damned reason
                                         */
                                        if ((data56 & 0x0FFFFFFFFFFFFFL) != 0) {    // 52-Bits all zero
                                            insertTCASAlert(acid, detectTime, data56);
                                        }
                                }
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
                        }
                        break;
                    default:
                        System.err.printf("DataBlockParser::decodeData DF: [%d] %s%n", df5, data);
                }
            } //  end of for-loop

            /*
             * We now have targets to process
             */
            try {
                List<Track> table = getAllUpdatedTargets();

                if (table.isEmpty() == false) {
                    for (Track trk : table) {
                        time = trk.getUpdatedTime();
                        trk.setUpdated(false);  // reset the updated boolean

                        acid = trk.getAircraftID();

                        /*
                         * See if this ACID exists yet in the target table, and
                         * has our radar ID. If it does, we can do an update, and
                         * if not we will do an insert.
                         */
                        try {
                            queryString = String.format("SELECT count(1) AS TC FROM target WHERE acid='%s' AND radar_id=%d",
                                    acid, radarid);

                            query = db1.createStatement();
                            rs = query.executeQuery(queryString);

                            if (rs.next() == true) {
                                exists = rs.getInt("TC");
                            } else {
                                exists = 0;
                            }

                            rs.close();
                            query.close();
                        } catch (SQLException e3) {
                            try {
                                if (rs != null) {
                                    rs.close();
                                }
                            } catch (SQLException e4) {
                            }
                            query.close();
                            continue;   // this is not good, so end pass
                        }

                        if ((trk.getOnGround() == true) || (trk.getVirtualOnGround() == true)) {
                            ground = 1;
                        } else {
                            ground = 0;
                        }

                        if (exists > 0) {         // target exists
                            queryString = String.format("UPDATE target SET utcupdate=%d,"
                                    + "radariid=NULLIF(%d, -99),"
                                    + "si=%d,"
                                    + "altitude=NULLIF(%d, -9999),"
                                    + "altitudedf00=NULLIF(%d, -9999),"
                                    + "altitudedf04=NULLIF(%d, -9999),"
                                    + "altitudedf16=NULLIF(%d, -9999),"
                                    + "altitudedf17=NULLIF(%d, -9999),"
                                    + "altitudedf18=NULLIF(%d, -9999),"
                                    + "altitudedf20=NULLIF(%d, -9999),"
                                    + "groundSpeed=NULLIF(%.1f, -999.0),"
                                    + "groundTrack=NULLIF(%.1f, -999.0),"
                                    + "gsComputed=NULLIF(%.1f, -999.0),"
                                    + "gtComputed=NULLIF(%.1f, -999.0),"
                                    + "callsign='%s',"
                                    + "latitude=NULLIF(%f, -999.0),"
                                    + "longitude=NULLIF(%f, -999.0),"
                                    + "verticalRate=NULLIF(%d, -9999),"
                                    + "verticalTrend=%d,"
                                    + "quality=%d,"
                                    + "squawk='%s',"
                                    + "alert=%d,"
                                    + "emergency=%d,"
                                    + "spi=%d,"
                                    + "onground=%d,"
                                    + "hijack=%d,"
                                    + "comm_out=%d,"
                                    + "hadAlert=%d,"
                                    + "hadEmergency=%d,"
                                    + "hadSPI=%d"
                                    + " WHERE acid='%s' AND radar_id=%d",
                                    time,
                                    trk.getRadarIID(),
                                    trk.getSI() ? 1 : 0,
                                    trk.getAltitude(),
                                    trk.getAltitudeDF00(),
                                    trk.getAltitudeDF04(),
                                    trk.getAltitudeDF16(),
                                    trk.getAltitudeDF17(),
                                    trk.getAltitudeDF18(),
                                    trk.getAltitudeDF20(),
                                    trk.getGroundSpeed(),
                                    trk.getGroundTrack(),
                                    trk.getComputedGroundSpeed(),
                                    trk.getComputedGroundTrack(),
                                    trk.getCallsign(),
                                    trk.getLatitude(),
                                    trk.getLongitude(),
                                    trk.getVerticalRate(),
                                    trk.getVerticalTrend(),
                                    trk.getTrackQuality(),
                                    trk.getSquawk(),
                                    trk.getAlert() ? 1 : 0,
                                    trk.getEmergency() ? 1 : 0,
                                    trk.getSPI() ? 1 : 0,
                                    ground,
                                    trk.getHijack() ? 1 : 0,
                                    trk.getCommOut() ? 1 : 0,
                                    trk.getHadAlert() ? 1 : 0,
                                    trk.getHadEmergency() ? 1 : 0,
                                    trk.getHadSPI() ? 1 : 0,
                                    acid,
                                    radarid);
                        } else {                // target doesn't exist
                            queryString = String.format("INSERT INTO target ("
                                    + "acid,"
                                    + "radar_id,"
                                    + "utcdetect,"
                                    + "utcupdate,"
                                    + "radariid,"
                                    + "si,"
                                    + "altitude,"
                                    + "altitudedf00,"
                                    + "altitudedf04,"
                                    + "altitudedf16,"
                                    + "altitudedf17,"
                                    + "altitudedf18,"
                                    + "altitudedf20,"
                                    + "groundSpeed,"
                                    + "groundTrack,"
                                    + "gsComputed,"
                                    + "gtComputed,"
                                    + "callsign,"
                                    + "latitude,"
                                    + "longitude,"
                                    + "verticalRate,"
                                    + "verticalTrend,"
                                    + "quality,"
                                    + "squawk,"
                                    + "alert,"
                                    + "emergency,"
                                    + "spi,"
                                    + "onground,"
                                    + "hijack,"
                                    + "comm_out,"
                                    + "hadAlert,"
                                    + "hadEmergency,"
                                    + "hadSPI) "
                                    + "VALUES ('%s',%d,%d,%d,"
                                    + "NULLIF(%d, -99), %d," // radarIID & SI
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%.1f,-999.0),"
                                    + "NULLIF(%.1f,-999.0),"
                                    + "NULLIF(%.1f,-999.0),"
                                    + "NULLIF(%.1f,-999.0),"
                                    + "'%s',"
                                    + "NULLIF(%f, -999.0),"
                                    + "NULLIF(%f, -999.0),"
                                    + "NULLIF(%d, -9999),"
                                    + "%d,"
                                    + "%d,"
                                    + "'%s',"
                                    + "%d,%d,%d,%d,%d,%d,%d,%d,%d)",
                                    acid,
                                    radarid,
                                    time,
                                    time,
                                    trk.getRadarIID(),
                                    trk.getSI() ? 1 : 0,
                                    trk.getAltitude(),
                                    trk.getAltitudeDF00(),
                                    trk.getAltitudeDF04(),
                                    trk.getAltitudeDF16(),
                                    trk.getAltitudeDF17(),
                                    trk.getAltitudeDF18(),
                                    trk.getAltitudeDF20(),
                                    trk.getGroundSpeed(),
                                    trk.getGroundTrack(),
                                    trk.getComputedGroundSpeed(),
                                    trk.getComputedGroundTrack(),
                                    trk.getCallsign(),
                                    trk.getLatitude(),
                                    trk.getLongitude(),
                                    trk.getVerticalRate(),
                                    trk.getVerticalTrend(),
                                    trk.getTrackQuality(),
                                    trk.getSquawk(),
                                    trk.getAlert() ? 1 : 0,
                                    trk.getEmergency() ? 1 : 0,
                                    trk.getSPI() ? 1 : 0,
                                    ground,
                                    trk.getHijack() ? 1 : 0,
                                    trk.getCommOut() ? 1 : 0,
                                    trk.getHadAlert() ? 1 : 0,
                                    trk.getHadEmergency() ? 1 : 0,
                                    trk.getHadSPI() ? 1 : 0);
                        }

                        try {
                            query = db1.createStatement();
                            query.executeUpdate(queryString);
                            query.close();
                        } catch (SQLException e5) {
                            query.close();
                            System.out.println("DataBlockParser::run query target Error: " + queryString + " " + e5.getMessage());
                        }

                        if (trk.getUpdatePosition() == true) {
                            trk.setUpdatePosition(false);

                            // Safety check, we don't want NULL's
                            // TODO: Figure out why we get those
                            if ((trk.getLatitude() != -999.0F) && (trk.getLongitude() != -999.0F)) {
                                queryString = String.format("INSERT INTO targetecho ("
                                        + "flight_id,"
                                        + "radar_id,"
                                        + "acid,"
                                        + "utcdetect,"
                                        + "radariid,"
                                        + "si,"
                                        + "latitude,"
                                        + "longitude,"
                                        + "altitude,"
                                        + "verticalTrend,"
                                        + "onground"
                                        + ") VALUES ("
                                        + "(SELECT flight_id FROM target WHERE acid='%s' AND radar_id=%d),"
                                        + "%d,"
                                        + "'%s',"
                                        + "%d,"
                                        + "%d, %d," // radariid & si
                                        + "%f,"
                                        + "%f,"
                                        + "%d,"
                                        + "%d,"
                                        + "%d)",
                                        acid,
                                        radarid,
                                        radarid,
                                        acid,
                                        time,
                                        trk.getRadarIID(),
                                        trk.getSI() ? 1 : 0,
                                        trk.getLatitude(),
                                        trk.getLongitude(),
                                        trk.getAltitude(),
                                        trk.getVerticalTrend(),
                                        ground);

                                try {
                                    query = db1.createStatement();
                                    query.executeUpdate(queryString);
                                    query.close();
                                } catch (SQLException e6) {
                                    query.close();
                                    System.out.println("DataBlockParser::run query targetecho Error: " + queryString + " " + e6.getMessage());
                                }
                            }
                        }

                        if (trk.getRegistration().equals("") == false) {
                            try {

                                queryString = String.format("SELECT count(1) AS RG FROM modestable"
                                        + " WHERE acid='%s'", acid);

                                query = db1.createStatement();
                                rs = query.executeQuery(queryString);

                                if (rs.next() == true) {
                                    exists = rs.getInt("RG");
                                } else {
                                    exists = 0;
                                }

                                rs.close();
                                query.close();
                            } catch (SQLException e7) {
                                rs.close();
                                query.close();
                                System.out.println("DataBlockParser::run query modestable warn: " + queryString + " " + e7.getMessage());
                                continue;   // skip the following
                            }

                            if (exists > 0) {
                                queryString = String.format("UPDATE modestable SET acft_reg='%s',utcupdate=%d WHERE acid='%s'",
                                        trk.getRegistration(),
                                        time,
                                        acid);

                                query = db1.createStatement();
                                query.executeUpdate(queryString);
                                query.close();
                            }
                        }

                        if (trk.getCallsign().equals("") == false) {
                            try {

                                queryString = String.format("SELECT count(1) AS CS FROM callsign"
                                        + " WHERE callsign='%s' AND acid='%s' AND radar_id=%d",
                                        trk.getCallsign(), acid, radarid);

                                query = db1.createStatement();
                                rs = query.executeQuery(queryString);

                                if (rs.next() == true) {
                                    exists = rs.getInt("CS");
                                } else {
                                    exists = 0;
                                }

                                rs.close();
                                query.close();
                            } catch (SQLException e8) {
                                rs.close();
                                query.close();
                                System.out.println("DataBlockParser::run query callsign warn: " + queryString + " " + e8.getMessage());
                                continue;   // skip the following
                            }

                            if (exists > 0) {
                                queryString = String.format("UPDATE callsign SET utcupdate=%d WHERE callsign='%s' AND acid='%s' AND radar_id=%d",
                                        time, trk.getCallsign(), acid, radarid);
                            } else {
                                queryString = String.format("INSERT INTO callsign (callsign,flight_id,radar_id,acid,"
                                        + "utcdetect,utcupdate) VALUES ('%s',"
                                        + "(SELECT flight_id FROM target WHERE acid='%s' AND radar_id=%d),"
                                        + "%d,'%s',%d,%d)",
                                        trk.getCallsign(),
                                        acid,
                                        radarid,
                                        radarid,
                                        acid,
                                        time,
                                        time);
                            }

                            query = db1.createStatement();
                            query.executeUpdate(queryString);
                            query.close();
                        }
                    }
                } // table empty
            } catch (NullPointerException | SQLException g1) {
                // No targets updated
            }

            /*
             * Simulate radar RPM
             */
            try {
                Thread.sleep(radarscan);
            } catch (InterruptedException e9) {
            }
        }
    }
}
