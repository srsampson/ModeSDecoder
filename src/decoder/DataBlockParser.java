/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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
    private static final long RATE3 = 10L * 60L * 1000L;        // 10 minutes
    //
    private final ConcurrentHashMap<String, Track> targets;
    private final ConcurrentHashMap<String, DataBlock> shortDetects;
    private final ArrayList<DataBlock> longDetects;
    //
    private final Thread process;
    private final BufferDataBlocks buf;
    private final LatLon receiverLatLon;
    private final PositionManager pm;
    private final NConverter nconverter;
    private final ZuluMillis zulu;
    private DataBlock dbk;
    //
    private final Connection db;
    private Statement query;
    private Statement querytt;
    private final Config config;

    private final int radar_site;
    private final long targetTimeout;
    private final long radarscan;
    //
    private static boolean EOF;
    private String data;
    private String icao_number;
    private String callsign;
    private String squawk;
    private String mdhash;
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
    private int amplitude;
    //
    private final Timer timer1;
    private final Timer timer2;
    private final Timer timer3;
    private final Timer targetTimer;
    //
    private final TimerTask task1;
    private final TimerTask task2;
    private final TimerTask task3;
    private final TimerTask targetTimeoutTask;
    
    public DataBlockParser(Config cf, LatLon ll, BufferDataBlocks bd, Connection dbc) {
        zulu = new ZuluMillis();
        config = cf;
        receiverLatLon = ll;
        buf = bd;
        db = dbc;

        radar_site = cf.getRadarSite();
        radarscan = (long) cf.getRadarScanTime() * 1000L;
        icao_number = "";
        callsign = "";
        squawk = "";
        mdhash = "";
        EOF = false;        

        targets = new ConcurrentHashMap<>();
        shortDetects = new ConcurrentHashMap<>();
        longDetects = new ArrayList<>();

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
//TODO OFF for debugging
//timer3.scheduleAtFixedRate(task3, 14L, RATE3);

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
            db.close();
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
                    "INSERT INTO target_history ("
                    + "flight_id,"
                    + "radar_site,"
                    + "icao_number,"
                    + "utcdetect,"
                    + "utcfadeout,"
                    + "radar_iid,"
                    + "radar_si,"
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
                    + "radar_site,"
                    + "icao_number,"
                    + "FROM_UNIXTIME(utcdetect/1000),"
                    + "FROM_UNIXTIME(utcupdate/1000),"
                    + "radar_iid,"
                    + "radar_si,"
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
                    + " FROM target_table WHERE target_table.utcupdate <= %d",
                    timeout);

            try {
                querytt = db.createStatement();
                querytt.executeUpdate(update);
                querytt.close();
            } catch (SQLException tt1) {
                try {
                    querytt.close();
                } catch (SQLException tt2) {
                }
                System.out.println("TargetTimeoutThread::run targethistory SQL Error: " + update + " " + tt1.getMessage());
            }

            update = String.format("DELETE FROM target_table WHERE utcupdate <= %d", timeout);

            try {
                querytt = db.createStatement();
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

    public boolean hasTarget(String icao) throws NullPointerException {
        synchronized (targets) {
            return targets.containsKey(icao);
        }
    }

    public Track getTarget(String icao) throws NullPointerException {
        synchronized (targets) {
            return targets.get(icao);
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
     * @param icao a String representing the Aircraft ID
     * @param obj an Object representing the Target data
     */
    public void addTarget(String icao, Track obj) throws NullPointerException {
       synchronized (targets) {
           targets.put(icao, obj);
       }
    }

    public void removeTarget(String icao) throws NullPointerException {
       synchronized (targets) {
            if (targets.containsKey(icao) == true) {
                targets.remove(icao);
            }
       }
    }

    /*
     * Method to add a new TCAS alert for this target
     * into the database table
     */
    public void insertTCASAlert(String hexid, long data56, long time) {
        /*
         * See if this is even a valid target
         */
        if (hasTarget(hexid)) {
            Track trk = getTarget(hexid);
            int alt16 = trk.getAltitudeDF16();  // might be -9999 (null)

            TCASAlert tcas = new TCASAlert(data56, time, alt16);

            /*
             * Some TCAS are just advisory, no RA generated
             * Note: TCASAlert class only sets time if RA is active.
             */
            
            String update = String.format("INSERT INTO tcas_alerts ("
                    + "icao_number,"
                    + "utcdetect,"
                    + "ttibits,"
                    + "threat_icao,"
                    + "threat_relative_altitude,"
                    + "threat_altitude,"
                    + "threat_bearing,"
                    + "threat_range,"
                    + "ara_bits,"
                    + "rac_bits,"
                    + "active_ra,"
                    + "single_ra,"
                    + "multiple_ra,"
                    + "multiple_threats,"
                    + "threat_terminated,"
                    + "identity_data_raw,"
                    + "type_data_raw) VALUES ("
                    + "'%s',%d,%d,'%s',NULLIF(%d,-9999),NULLIF(%d,-9999),NULLIF(%.1f,-999.0),NULLIF(%.1f,-999.0),%d,%d,"
                    + "%d,%d,%d,%d,%d,"
                    + "'%s','%s')",
                    icao_number,
                    tcas.getDetectTime(),
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
                    tcas.getThreatTerminated() ? 1 : 0,
                    tcas.getThreatIdentityData(),
                    tcas.getThreatTypeData());

            try {
                querytt = db.createStatement();
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
     * This will look through the TCAS database every 10 minutes and delete
     * entries that are over 30 minutes old. This is so I can view it manually.
     */
    private class removeTCASAlerts extends TimerTask {
        @Override
        public void run() {
            long time = zulu.getUTCTime() - (30L * 60L * 1000L); // subtract 30 minutes
        
            String update = String.format("DELETE FROM tcas_alerts WHERE utcupdate <= %d", time);

            try {
                querytt = db.createStatement();
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
                            removeTarget(id.getAircraftICAO());
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
            String icao;

            try {
                targets = getAllTargets();
            } catch (NullPointerException te) {
                return; // No targets found
            }

            for (Track id : targets) {
                try {
                    if (id.getTrackQuality() > 0) {
                        icao = id.getAircraftICAO();

                        // find the idStatus reports that haven't been position updated in 30 seconds
                        delta = Math.abs(currentTime - id.getUpdatedPositionTime());

                        if (delta >= RATE1) {
                            id.decrementTrackQuality();
                            id.setUpdatedTime(currentTime);
                            addTarget(icao, id);   // overwrite
                        }
                    }
                } catch (NullPointerException e1) {
                    // not likely
                }
            }
        }
    }

    private void updateTargetAmplitude(String hexid, int val, long time) {
        try {
            Track tgt = getTarget(hexid);
            tgt.setAmplitude(val);
            tgt.setUpdatedTime(time);
            addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
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

    /*
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

            /*
             * The compression rate for duplicates is pretty large.
             * For 3000 target reports, about 2000 are duplicates.
             */

            /*
             * Start with a fresh sheet each radar scan
             */
            shortDetects.clear();

            for (int i = 0; i < qsize; i++) {
                try {
                    dbk = buf.popData();

                    amplitude = dbk.getSignalLevel();
                    data = dbk.getData();
                    detectTime = dbk.getUTCTime();
                    mdhash = dbk.getDataHash();

                    if (dbk.getBlockType() == DataBlock.SHORTBLOCK) {
                        /*
                         * This will put the short data blocks on the keyed queue
                         * and duplicates will be overwritten. The duplicates
                         * have to be within the radar.scan number of seconds.
                         */
                        shortDetects.put(mdhash, dbk);
                    } else {
                        longDetects.add(dbk);
                    }
                } catch (IndexOutOfBoundsException dbk3) {
                }
            }

            parseShortDetects();
            parseLongDetects();

            /*
             * We now have targets to process
             */
            try {
                List<Track> table = getAllUpdatedTargets();

                if (table.isEmpty() == false) {
                    for (Track trk : table) {
                        time = trk.getUpdatedTime();
                        trk.setUpdated(false);  // reset the updated boolean

                        icao_number = trk.getAircraftICAO();

                        /*
                         * See if this ICAO exists yet in the target table, and
                         * has our radar ID. If it does, we can do an update, and
                         * if not we will do an insert.
                         */
                        try {
                            queryString = String.format("SELECT count(*) AS TC FROM target_table WHERE icao_number='%s' AND radar_site=%d",
                                    icao_number, radar_site);

                            query = db.createStatement();
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
                            queryString = String.format("UPDATE target_table SET utcupdate=%d,"
                                    + "amplitude=%d,"
                                    + "radar_iid=NULLIF(%d, -99),"
                                    + "radar_si=%d,"
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
                                    + " WHERE icao_number='%s' AND radar_site=%d",
                                    time,
                                    trk.getAmplitude(),
                                    trk.getRadarIID(),
                                    trk.getRadarSI() ? 1 : 0,
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
                                    icao_number,
                                    radar_site);
                        } else {                // target doesn't exist
                            queryString = String.format("INSERT INTO target_table ("
                                    + "icao_number,"
                                    + "radar_site,"
                                    + "utcdetect,"
                                    + "utcupdate,"
                                    + "amplitude,"
                                    + "radar_iid,"
                                    + "radar_si,"
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
                                    + "VALUES ('%s',%d,%d,%d,%d,"
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
                                    icao_number,
                                    radar_site,
                                    time,
                                    time,
                                    trk.getAmplitude(),
                                    trk.getRadarIID(),
                                    trk.getRadarSI() ? 1 : 0,
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
                            query = db.createStatement();
                            query.executeUpdate(queryString);
                            query.close();
                        } catch (SQLException e5) {
                            query.close();
                            System.out.println("DataBlockParser::run insert/update target_table Error: " + queryString + " " + e5.getMessage());
                        }

                        if (trk.getUpdatePosition() == true) {
                            trk.setUpdatePosition(false);

                            // Safety check, we don't want NULL's
                            // TODO: Figure out why we get those
                            if ((trk.getLatitude() != -999.0F) && (trk.getLongitude() != -999.0F)) {
                                queryString = String.format("INSERT INTO target_echo ("
                                        + "flight_id,"
                                        + "radar_site,"
                                        + "icao_number,"
                                        + "utcdetect,"
                                        + "amplitude,"
                                        + "radar_iid,"
                                        + "radar_si,"
                                        + "latitude,"
                                        + "longitude,"
                                        + "altitude,"
                                        + "verticalTrend,"
                                        + "onground"
                                        + ") VALUES ("
                                        + "(SELECT flight_id FROM target_table WHERE icao_number='%s' AND radar_site=%d),"
                                        + "%d,"
                                        + "'%s',"
                                        + "%d,"
                                        + "%d,"     // amplitude
                                        + "NULLIF(%d, -99), %d," // radariid & radarsi
                                        + "%f,"
                                        + "%f,"
                                        + "%d,"
                                        + "%d,"
                                        + "%d)",
                                        icao_number,
                                        radar_site,
                                        radar_site,
                                        icao_number,
                                        time,
                                        trk.getAmplitude(),
                                        trk.getRadarIID(),
                                        trk.getRadarSI() ? 1 : 0,
                                        trk.getLatitude(),
                                        trk.getLongitude(),
                                        trk.getAltitude(),
                                        trk.getVerticalTrend(),
                                        ground);

                                try {
                                    query = db.createStatement();
                                    query.executeUpdate(queryString);
                                    query.close();
                                } catch (SQLException e6) {
                                    query.close();
                                    System.out.println("DataBlockParser::run query target_echo Error: " + queryString + " " + e6.getMessage());
                                }
                            }
                        }

                        if (trk.getRegistration().equals("") == false) {
                            try {

                                queryString = String.format("SELECT count(*) AS RG FROM icao_table"
                                        + " WHERE icao_number='%s'", icao_number);

                                query = db.createStatement();
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
                                System.out.println("DataBlockParser::run query icao_table warn: " + queryString + " " + e7.getMessage());
                                continue;   // skip the following
                            }

                            if (exists > 0) {
                                queryString = String.format("UPDATE icao_table SET registration='%s' WHERE icao_number='%s'",
                                        trk.getRegistration(),
                                        icao_number);

                                query = db.createStatement();
                                query.executeUpdate(queryString);
                                query.close();
                            }
                        }

                        if (trk.getCallsign().equals("") == false) {
                            try {

                                queryString = String.format("SELECT count(*) AS CS FROM callsign_table"
                                        + " WHERE callsign='%s' AND icao_number='%s' AND radar_site=%d",
                                        trk.getCallsign(), icao_number, radar_site);

                                query = db.createStatement();
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
                                System.out.println("DataBlockParser::run query callsign_table warn: " + queryString + " " + e8.getMessage());
                                continue;   // skip the following
                            }

                            if (exists > 0) {
                                queryString = String.format("UPDATE callsign_table SET utcupdate=%d WHERE callsign='%s' AND icao_number='%s' AND radar_site=%d",
                                        time, trk.getCallsign(), icao_number, radar_site);
                            } else {
                                queryString = String.format("INSERT INTO callsign_table (callsign,flight_id,radar_site,icao_number,"
                                        + "utcdetect,utcupdate) VALUES ('%s',"
                                        + "(SELECT flight_id FROM target_table WHERE icao_number='%s' AND radar_site=%d),"
                                        + "%d,'%s',%d,%d)",
                                        trk.getCallsign(),
                                        icao_number,
                                        radar_site,
                                        radar_site,
                                        icao_number,
                                        time,
                                        time);
                            }

                            query = db.createStatement();
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
        } //  end of while
    }

    private void parseShortDetects() {
        Collection<DataBlock> scan = shortDetects.values();
        Iterator<DataBlock> iterator = scan.iterator();

        while (iterator.hasNext()) {

            dbk = iterator.next();
            amplitude = dbk.getSignalLevel();
            data = dbk.getData();
            detectTime = dbk.getUTCTime();

            int df5 = ((Integer.parseInt(data.substring(0, 2), 16)) >>> 3) & 0x1F;

            /*
             * Most decoders pass a lot of garble packets, so we first check if
             * DF11 or DF17/18 have validated the packet by calling hasTarget().
             * If not, then it is garbled.
             *
             * Note: The DF code itself may be garbled
             */
            switch (df5) {
                case 0:
                    /*
                     * Check data for correct length
                     * A short packet needs 56 bits/7 bytes/14 nibbles
                     */
                    if (data.length() < 14) {
                        System.out.println("DF00 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat00 df00 = new DownlinkFormat00(data, detectTime);
                    icao_number = df00.getICAO();

                    try {
                        if (hasTarget(icao_number)) {           // must be valid then
                            altitude = df00.getAltitude();
                            isOnGround = df00.getIsOnGround();      // true if vs1 == 1

                            updateTargetAmplitude(icao_number, amplitude, detectTime);
                            updateTargetAltitudeDF00(icao_number, altitude, detectTime);
                            updateTargetOnGround(icao_number, isOnGround, detectTime);
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np);
                    }
                    break;
                case 4:
                    /*
                     * Check data for correct length
                     * A short packet needs 56 bits/7 bytes/14 nibbles
                     */
                    if (data.length() < 14) {
                        System.out.println("DF04 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat04 df04 = new DownlinkFormat04(data, detectTime);
                    icao_number = df04.getICAO();

                    try {
                        if (hasTarget(icao_number)) {
                            altitude = df04.getAltitude();
                            isOnGround = df04.getIsOnGround();
                            alert = df04.getIsAlert();
                            spi = df04.getIsSPI();
                            emergency = df04.getIsEmergency();

                            updateTargetAmplitude(icao_number, amplitude, detectTime);
                            updateTargetAltitudeDF04(icao_number, altitude, detectTime);
                            updateTargetBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np);
                    }
                    break;
                case 5:
                    /*
                     * Check data for correct length
                     * A short packet needs 56 bits/7 bytes/14 nibbles
                     */
                    if (data.length() < 14) {
                        System.out.println("DF05 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat05 df05 = new DownlinkFormat05(data, detectTime);
                    icao_number = df05.getICAO();

                    try {
                        if (hasTarget(icao_number)) {
                            squawk = df05.getSquawk();
                            isOnGround = df05.getIsOnGround();
                            alert = df05.getIsAlert();
                            spi = df05.getIsSPI();
                            emergency = df05.getIsEmergency();

                            updateTargetAmplitude(icao_number, amplitude, detectTime);
                            updateTargetSquawk(icao_number, squawk, detectTime);
                            updateTargetBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np);
                    }
                    break;
                case 11:
                    /*
                     * Check data for correct length
                     * A short packet needs 56 bits/7 bytes/14 nibbles
                     */
                    if (data.length() < 14) {
                        System.out.println("DF11 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat11 df11 = new DownlinkFormat11(data, detectTime);
                    icao_number = df11.getICAO();

                    if (df11.isValid()) {
                        /*
                         * See if Target already exists
                         */
                        try {
                            if (hasTarget(icao_number) == false) {
                                /*
                                 * New Target
                                 */
                                Track t = new Track(icao_number, false);  // false == not TIS
                                t.setRegistration(nconverter.icao_to_n(icao_number));
                                addTarget(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
                            break;
                        }

                        updateTargetAmplitude(icao_number, amplitude, detectTime);

                        radarIID = df11.getRadarIID();
                        si = df11.getRadarSI();
                        updateTargetRadarID(icao_number, radarIID, si, detectTime);

                        isOnGround = df11.getIsOnGround();
                        updateTargetOnGround(icao_number, isOnGround, detectTime);
                    }
                    break;
            }
        }
    }

    private void parseLongDetects() {
        Iterator<DataBlock> iterator = longDetects.iterator();

        while (iterator.hasNext()) {
            
            dbk = longDetects.remove(0);
            amplitude = dbk.getSignalLevel();
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
                case 16:
                    /*
                     * Check data for correct length
                     * A long packet needs 112 bits/14 bytes/28 nibbles
                     */
                    if (data.length() < 28) {
                        System.out.println("DF16 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat16 df16 = new DownlinkFormat16(data, detectTime);
                    icao_number = df16.getICAO();

                    try {
                        if (hasTarget(icao_number)) {
                            isOnGround = df16.getIsOnGround();
                            altitude = df16.getAltitude();

                            updateTargetAmplitude(icao_number, amplitude, detectTime);
                            updateTargetAltitudeDF16(icao_number, altitude, detectTime);
                            updateTargetOnGround(icao_number, isOnGround, detectTime);

                            if (df16.getBDS() == 0x30) {   // BDS 3,0
                                data56 = df16.getMV();
                                int data30 = (int) (data56 >>> 26);
                                /*
                                 * Some planes send TTI = 0 which means nothing to do
                                 */
                                if ((data30 & 0x3) != 0) {
                                    insertTCASAlert(icao_number, data56, detectTime);
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
                    /*
                     * Check data for correct length
                     * A long packet needs 112 bits/14 bytes/28 nibbles
                     */
                    if (data.length() < 28) {
                        System.out.println("DF17 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat17 df17 = new DownlinkFormat17(data, detectTime, pm);
                    icao_number = df17.getICAO();

                    if (df17.isValid() == true) { // CRC passed
                        /*
                         * See if Target already exists
                         */
                        try {
                            if (hasTarget(icao_number) == false) {
                                /*
                                 * New Target
                                 */
                                Track t = new Track(icao_number, false);  // false == not TIS
                                t.setRegistration(nconverter.icao_to_n(icao_number));
                                addTarget(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                                 * Not likely to occur
                             */
                            System.err.println(np);
                            break;
                        }

                        updateTargetAmplitude(icao_number, amplitude, detectTime);

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

                                updateTargetCallsign(icao_number, callsign, category, detectTime);
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

                                updateTargetBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);
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

                                updateTargetBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);

                                altitude = df17.getAltitude();
                                updateTargetAltitudeDF17(icao_number, altitude, detectTime);

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
                                            updateTargetGroundSpeedTrueHeading(icao_number, groundSpeed, trueHeading, vSpeed, detectTime);
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
                                                updateTargetMagneticHeadingIAS(icao_number, magneticHeading, airspeed, vSpeed, detectTime);
                                            } else {
                                                updateTargetMagneticHeadingTAS(icao_number, magneticHeading, airspeed, vSpeed, detectTime);
                                            }
                                        }
                                }
                        }
                    }
                    break;
                case 18:
                    /*
                     * Check data for correct length
                     * A long packet needs 112 bits/14 bytes/28 nibbles
                     */
                    if (data.length() < 28) {
                        System.out.println("DF18 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat18 df18 = new DownlinkFormat18(data, detectTime, pm);
                    icao_number = df18.getICAO();

                    if (df18.isValid() == true) { // Passed CRC
                        /*
                         * See if Target already exists
                         */
                        try {
                            if (hasTarget(icao_number) == false) {
                                /*
                                     * New Target
                                 */
                                Track t = new Track(icao_number, true);  // true == TIS

                                t.setRegistration(nconverter.icao_to_n(icao_number));
                                addTarget(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                                 * Not likely to occur
                             */
                            System.err.println(np);
                        }

                        updateTargetAmplitude(icao_number, amplitude, detectTime);

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

                                updateTargetCallsign(icao_number, callsign, category, detectTime);
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

                                updateTargetBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);
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

                                updateTargetBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);

                                altitude = df18.getAltitude();
                                updateTargetAltitudeDF18(icao_number, altitude, detectTime);
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
                                            updateTargetGroundSpeedTrueHeading(icao_number, groundSpeed, trueHeading, vSpeed, detectTime);
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
                                                updateTargetMagneticHeadingIAS(icao_number, magneticHeading, airspeed, vSpeed, detectTime);
                                            } else {
                                                updateTargetMagneticHeadingTAS(icao_number, magneticHeading, airspeed, vSpeed, detectTime);
                                            }
                                        }
                                }
                        }
                    }
                    break;
                case 19: // Military Squitter
                    break;
                case 20:
                    /*
                     * Check data for correct length
                     * A long packet needs 112 bits/14 bytes/28 nibbles
                     */
                    if (data.length() < 28) {
                        System.out.println("DF20 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat20 df20 = new DownlinkFormat20(data, detectTime);
                    icao_number = df20.getICAO();

                    try {
                        if (hasTarget(icao_number)) {
                            altitude = df20.getAltitude();
                            isOnGround = df20.getIsOnGround();
                            emergency = df20.getIsEmergency();
                            alert = df20.getIsAlert();
                            spi = df20.getIsSPI();

                            updateTargetAmplitude(icao_number, amplitude, detectTime);
                            updateTargetAltitudeDF20(icao_number, altitude, detectTime);
                            updateTargetBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);

                            int bds = df20.getBDS();
                            data56 = df20.getData56();

                            if (bds == 0x20) {             // BDS 2,0
                                callsign = df20.getCallsign();
                                updateTargetCallsign(icao_number, callsign, detectTime);
                            } else if (bds == 0x30) {      // BDS 3,0
                                int data30 = (int) (data56 >>> 26);
                                /*
                                 * Some planes send TTI = 0 which means nothing to do
                                 */
                                if ((data30 & 0x3) != 0) {
                                    insertTCASAlert(icao_number, data56, detectTime);
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
                    /*
                     * Check data for correct length
                     * A long packet needs 112 bits/14 bytes/28 nibbles
                     */
                    if (data.length() < 28) {
                        System.out.println("DF21 Error " + data.length() + " Hex Length");
                        break;
                    }

                    DownlinkFormat21 df21 = new DownlinkFormat21(data, detectTime);
                    icao_number = df21.getICAO();

                    try {
                        if (hasTarget(icao_number)) {
                            squawk = df21.getSquawk();
                            isOnGround = df21.getIsOnGround();
                            emergency = df21.getIsEmergency();
                            alert = df21.getIsAlert();
                            spi = df21.getIsSPI();

                            updateTargetAmplitude(icao_number, amplitude, detectTime);
                            updateTargetSquawk(icao_number, squawk, detectTime);
                            updateTargetBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);

                            int bds = df21.getBDS();
                            data56 = df21.getData56();

                            switch (bds) {      // Bunch more available for decode
                                case 0x20:      // BDS 2,0 Callsign
                                    callsign = df21.getCallsign();
                                    updateTargetCallsign(icao_number, callsign, detectTime);
                                    break;
                                case 0x30:      // BDS 3,0 TCAS
                                    int data30 = (int) (data56 >>> 26);
                                    /*
                                     * Some planes send TTI = 0 which means nothing to do
                                     */
                                    if ((data30 & 0x3) != 0) {
                                        insertTCASAlert(icao_number, data56, detectTime);
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
        }
    }
}
