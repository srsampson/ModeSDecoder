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

/*
 * A class to decode the received data blocks
 * and store them into database tracks.
 */
public final class DataBlockParser extends Thread {

    private static final long RATE1 = 60L * 1000L;              // 60 seconds
    private static final long RATE2 = 5L * 1000L;               // 5 seconds
    //
    private final ConcurrentHashMap<String, Track> tracks;
    private final ConcurrentHashMap<String, DataBlock> shortDetects;
    private final ArrayList<DataBlock> longDetects;
    //
    private final Thread process;
    private final BufferDataBlocks buf;
    private final LatLon receiverLatLon;
    private final PositionManager pm;
    private final NConverter nconverter;
    private final ZuluMillis zulu;
    private final PressureAltitude pa;
    private DataBlock dbk;
    //
    private final Connection db;
    private final Config config;
    //
    
    private final int radar_site;
    private final long trackTimeout;
    private final long radarscan;
    //
    private static boolean EOF;
    private String data;
    private String icao_number;
    private String callsign;
    private String squawk;
    private String mdhash;
    private String airport;
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
    private int elevation;
    //
    private final Timer timer1;
    private final Timer timer2;
    //
    private final TimerTask task1;
    private final TimerTask task2;
    
    public DataBlockParser(Config cf, LatLon ll, BufferDataBlocks bd, Connection dbc, PressureAltitude p) {
        zulu = new ZuluMillis();
        config = cf;
        receiverLatLon = ll;
        buf = bd;
        db = dbc;
        pa = p;

        radarscan = (long) cf.getRadarScanTime() * 1000L;
        radar_site = cf.getRadarSite();

        if (pa == null) {
            airport = "";
            elevation = 0;
        } else {
            airport = pa.getAirportName();            
            elevation = pa.getAirportElevation();
        }

        icao_number = "";
        callsign = "";
        squawk = "";
        mdhash = "";      
        airport = "";   // TODO Add to table
        elevation = 0;

        tracks = new ConcurrentHashMap<>();
        shortDetects = new ConcurrentHashMap<>();
        longDetects = new ArrayList<>();

        pm = new PositionManager(receiverLatLon, this);
        nconverter = new NConverter();

        trackTimeout = config.getDatabaseTrackTimeout() * 60L * 1000L;

        task1 = new UpdateActiveTracksTask();
        task2 = new UpdateTrackQualityTask();

        timer1 = new Timer();
        timer1.scheduleAtFixedRate(task1, 0L, RATE1);
        
        timer2 = new Timer();
        timer2.scheduleAtFixedRate(task2, 10L, RATE2);
        
        process = new Thread(this);
        process.setName("DataBlockParser");
        process.setPriority(Thread.NORM_PRIORITY);
    }

    @Override
    public void start() {
        EOF = false;
        initializeTracks();
        process.start();
    }

    public void close() {
        EOF = true;
        
        try {
            db.close();
        } catch (NullPointerException | SQLException e) {
        }

        timer1.cancel();
        timer2.cancel();

        pm.close();
    }

    /*
     * SQL note to self:
     *
     * mysql> select count(*) from position_echo P INNER JOIN tracks T
     * ON P.icao_number = T.icao_number WHERE active=0;
     * 
     * +----------+
     * | count(*) |
     * +----------+
     * |    10856 |
     * +----------+
     * 1 row in set (0.01 sec)
     * 
     * mysql> select count(*) from position_echo P INNER JOIN tracks T
     * ON P.icao_number = T.icao_number WHERE active=1;
     * 
     * +----------+
     * | count(*) |
     * +----------+
     * |     1415 |
     * +----------+
     * 1 row in set (0.00 sec)
     */

    /*
     * On startup make sure all tracks are set to non-active
     * and reset the quality to 0.
     */
    public void initializeTracks() {
        String queryString = String.format("UPDATE modes.tracks SET active = 0,"
                + "quality = 0");

        try (Statement query = db.createStatement()) {
            query.executeUpdate(queryString);
        } catch (SQLException it1) {
        }
    }

    public boolean hasTrack(String icao) throws NullPointerException {
        synchronized (tracks) {
            return tracks.containsKey(icao);
        }
    }

    public Track getTrack(String icao) throws NullPointerException {
        synchronized (tracks) {
            return tracks.get(icao);
        }
    }

    /**
     * Method to return a collection of all tracks.
     *
     * @return a list Representing all track objects (active and inactive).
     */
    public List<Track> getAllTracks() throws NullPointerException {
        List<Track> result = new ArrayList<>();

        synchronized (tracks) {
            result.addAll(tracks.values());
        }
        
        return result;
    }

    /**
     * Put track on queue after being created or updated
     *
     * @param icao a String representing the Aircraft ID
     * @param obj an Object representing the track data
     */
    public void addTrack(String icao, Track obj) throws NullPointerException {
       synchronized (tracks) {
           tracks.put(icao, obj);
       }
    }

    public void removeTrack(String icao) throws NullPointerException {
       synchronized (tracks) {
            if (tracks.containsKey(icao) == true) {
                tracks.remove(icao);
            }
        }

       /*
        * Assuming it was copied to the database
        *
        * Set the database track inactive, and quality to 0.
        */
        String queryString = String.format("UPDATE modes.tracks SET active = 0,"
                + "quality = 0 WHERE icao_number='%s'", icao);

        try (Statement query = db.createStatement()) {
            query.executeUpdate(queryString);
        } catch (SQLException e77) {
        }
    }

    /*
     * Method to add a new TCAS alert for this track
     * into the database table
     */
    public void insertTCASAlert(String hexid, int df5, long data56, long time) {
        /*
         * See if this is even a valid track
         */
        if (hasTrack(hexid)) {
            Track track = getTrack(hexid);

            TCASAlert tcas = new TCASAlert(data56, df5, time, track.getAltitude());

            /*
             * Some TCAS are just advisory, no RA generated
             */
            String queryString = String.format("INSERT INTO modes.tcas_alerts ("
                    + "icao_number,"
                    + "utcdetect,"
                    + "df_source,"
                    + "tti_bits,"
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
                    + "threat_terminated,"
                    + "identity_data_raw,"
                    + "type_data_raw) "
                    + "VALUES ('%s',%d,%d,%d,'%s',"
                    + "NULLIF(%d,-9999),"
                    + "NULLIF(%d,-9999),"
                    + "NULLIF(%.1f,-999.0),"
                    + "NULLIF(%.1f,-999.0),"
                    + "%d,%d,%d,%d,%d,%d,'%s','%s')",
                    icao_number,
                    tcas.getDetectTime(),
                    tcas.getDFSource(),
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
                    tcas.getThreatTerminated() ? 1 : 0,
                    tcas.getThreatIdentityData(),
                    tcas.getThreatTypeData());

            try (Statement query = db.createStatement()) {
                query.executeUpdate(queryString);
            } catch (SQLException t3) {
                System.out.println("DataBlockParser::insertTCASAlert insert SQL Error: " + queryString + " " + t3.getMessage());
            }
        }
    }

    /*
     * This will look through the Track table every minute and mark
     * inactive entries that are over X minutes old.  In that case the
     * track has probably landed or faded-out from coverage.
     *
     * Note: Only active tracks are affected.
     *
     * First the database entry is marked inactive, and then the local
     * track is removed from the queue.
     */
    private class UpdateActiveTracksTask extends TimerTask {

        private List<Track> tracks;
        private long tracktime;

        @Override
        public void run() {
            long currentTime = zulu.getUTCTime();
            long delta;

            try {
                tracks = getAllTracks();
            } catch (NullPointerException te) {
                return; // No tracks found
            }

            for (Track track : tracks) {
                try {
                    tracktime = track.getUpdatedTime();

                    // find tracks that haven't been updated in X minutes
                    delta = Math.abs(currentTime - tracktime);

                    if (delta >= trackTimeout) {    // default 1 minute
                        try {
                            removeTrack(track.getAircraftICAO());
                        } catch (NullPointerException mt1) {
                            // punt
                        }
                    }
                } catch (NullPointerException e2) {
                    // ignore
                }
            }
        }
    }

    /*
     * Track Position Quality
     *
     * This will look through the Track local table and decrement track quality
     * every 30 seconds that the lat/lon position isn't updated. This timer task
     * is run every 5 seconds.
     *
     * Note: It doesn't run against inactive tracks.
     */
    private class UpdateTrackQualityTask extends TimerTask {

        private List<Track> tracks;
        private long delta;
        private long currentTime;

        @Override
        public void run() {
            currentTime = zulu.getUTCTime();
            delta = 0L;
            String icao;

            try {
                tracks = getAllTracks();
            } catch (NullPointerException te) {
                return; // No tracks found
            }

            for (Track track : tracks) {
                try {
                    if (track.getTrackQuality() > 0) {
                        icao = track.getAircraftICAO();

                        // find the idStatus reports that haven't been position updated in 30 seconds
                        delta = Math.abs(currentTime - track.getUpdatedPositionTime());

                        if (delta >= RATE1) {
                            track.decrementTrackQuality();
                            track.setUpdatedTime(currentTime);
                            addTrack(icao, track);   // overwrite
                        }
                    }
                } catch (NullPointerException e1) {
                    // not likely
                }
            }
        }
    }

    private void updateTrackAmplitude(String hexid, int val, long time) {
        try {
            Track track = getTrack(hexid);
            track.setAmplitude(val);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }
    private void updateTrackMagneticHeadingIAS(String hexid, float head, float ias, int vvel, long time) {
        try {
            Track track = getTrack(hexid);
            track.setHeading(head);
            track.setIAS(ias);
            track.setVerticalRate(vvel);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackMagneticHeadingTAS(String hexid, float head, float tas, int vvel, long time) {
        try {
            Track track = getTrack(hexid);
            track.setHeading(head);
            track.setTAS(tas);
            track.setVerticalRate(vvel);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackCallsign(String hexid, String cs, long time) {
        try {
            Track track = getTrack(hexid);
            track.setCallsign(cs);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackCallsign(String hexid, String cs, int category, long time) {
        try {
            Track track = getTrack(hexid);
            track.setCallsign(cs);
            track.setCategory(category);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackAltitudeDF00(String hexid, int alt, long time) {
        try {
            Track track = getTrack(hexid);
            track.setAltitudeDF00(alt);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackAltitudeDF04(String hexid, int alt, long time) {
        try {
            Track track = getTrack(hexid);
            track.setAltitudeDF04(alt);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackAltitudeDF16(String hexid, int alt, long time) {
        try {
            Track track = getTrack(hexid);
            track.setAltitudeDF16(alt);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackAltitudeDF17(String hexid, int alt, long time) {
        try {
            Track track = getTrack(hexid);
            track.setAltitudeDF17(alt);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackAltitudeDF18(String hexid, int alt, long time) {
        try {
            Track track = getTrack(hexid);
            track.setAltitudeDF18(alt);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackAltitudeDF20(String hexid, int alt, long time) {
        try {
            Track track = getTrack(hexid);
            track.setAltitudeDF20(alt);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackGroundSpeedTrueHeading(String hexid, float gs, float th, int vs, long time) {
        try {
            Track track = getTrack(hexid);
            track.setGroundSpeed(gs);
            track.setGroundTrack(th);
            track.setVerticalRate(vs);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackRadarID(String hexid, int iid, boolean si, long time) {
        try {
            Track trk = getTrack(hexid);
            trk.setRadarIID(iid);
            trk.setSI(si);
            trk.setUpdatedTime(time);
            addTrack(hexid, trk);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackSquawk(String hexid, String sq, long time) {
        try {
            Track track = getTrack(hexid);
            track.setSquawk(sq);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackBoolean(String hexid, boolean onground, boolean emergency, boolean alert, boolean spi, long time) {
        try {
            Track track = getTrack(hexid);
            track.setAlert(alert, emergency, spi);
            track.setOnGround(onground);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTrackOnGround(String hexid, boolean onground, long time) {
        try {
            Track track = getTrack(hexid);
            track.setOnGround(onground);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    public void updateTrackLatLon(String hexid, LatLon latlon, int mode, long time) {
        if (hasTrack(hexid) == true) {
            try {
                Track track = getTrack(hexid);
                track.setPosition(latlon, mode, time);
                track.setUpdatedTime(time);
                addTrack(hexid, track);
            } catch (NullPointerException np) {
                System.err.println(np);
            }
        }
    }

    /*
     * Track detection processing and adding to the Track library.
     *
     * Decode Mode-S Short (56-Bit) and Long (112-Bit) Packets
     */
    @Override
    public void run() {
        String queryString;
        String registration;
        int ground, exists;
        long time;

        while (EOF == false) {
            int qsize = buf.getQueueSize();

            if (qsize == 0) {
                continue;
            }

            /*
             * The compression rate for duplicates is pretty large.
             * For 3000 track reports, about 2000 are duplicates.
             *
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
             * We now have tracks to process
             */
            List<Track> table = getAllTracks();

            if (table.isEmpty() == false) {
                for (Track trk : table) {
                    trk.setUpdated(false);  // reset the updated boolean

                    icao_number = trk.getAircraftICAO();
                    time = trk.getUpdatedTime();

                    /*
                     * See if this ICAO exists yet in the track table, and
                     * has our radar ID. If it does, we can do an update, and
                     * if not we will do an insert.
                     */
                    queryString = String.format("SELECT count(*) AS TC FROM modes.tracks WHERE icao_number='%s' AND radar_site=%d",
                            icao_number, radar_site);

                    exists = 0;

                    try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                        if (rs.next() == true) {
                            exists = rs.getInt("TC");
                        }
                    } catch (SQLException e3) {
                    }

                    if ((trk.getOnGround() == true) || (trk.getVirtualOnGround() == true)) {
                        ground = 1;
                    } else {
                        ground = 0;
                    }

                    /*
                     * Not much in tracks anymore
                     */
                    if (exists > 0) {         // track exists
                        queryString = String.format("UPDATE modes.tracks SET "
                                + "utcupdate=%d,"
                                + "quality=%d,"
                                + "active='1'"
                                + " WHERE icao_number='%s' AND radar_site=%d",
                                time,
                                trk.getTrackQuality(),
                                icao_number,
                                radar_site);
                    } else {                // track doesn't exist
                        queryString = String.format("INSERT INTO modes.tracks ("
                                + "icao_number,"
                                + "radar_site,"
                                + "utcdetect,"
                                + "utcupdate,"
                                + "quality,"
                                + "active"
                                + ") VALUES ('%s',%d,%d,%d,%d,'1')",
                                icao_number,
                                radar_site,
                                time,
                                time,
                                trk.getTrackQuality());
                    }

                    try (Statement query = db.createStatement()) {
                        query.executeUpdate(queryString);
                    } catch (SQLException t3) {
                        System.out.println("DataBlockParser::run insert/update tracks table Error: " + queryString + " " + t3.getMessage());
                    }

                    if (trk.getUpdatePosition() == true) {
                        trk.setUpdatePosition(false);

                        if ((trk.getLatitude() != -999.0F) && (trk.getLongitude() != -999.0F)) {
                            queryString = String.format("INSERT INTO modes.position_echo ("
                                    + "icao_number,"
                                    + "radar_site,"
                                    + "utcdetect,"
                                    + "latitude,"
                                    + "longitude,"
                                    + "verticalTrend,"
                                    + "onground"
                                    + ") VALUES ('%s',%d,%d,"
                                    + "NULLIF(%f, -999.0),"
                                    + "NULLIF(%f, -999.0),"
                                    + "%d, %d)",
                                    icao_number,
                                    radar_site,
                                    time,
                                    trk.getLatitude(),
                                    trk.getLongitude(),
                                    trk.getVerticalTrend(),
                                    ground);

                            try (Statement query = db.createStatement()) {
                                query.executeUpdate(queryString);
                            } catch (SQLException e6) {
                                System.out.println("DataBlockParser::run query position_echo Error: " + queryString + " " + e6.getMessage());
                            }
                        }
                    }

                    /*
                     * We now process squawks
                     *
                     * First see if squawk is null, and skip if it is.
                     *
                     * See if this ICAO exists yet in the squawk table, and
                     * only if the squawk is new, drop it in.
                     */
                    squawk = trk.getSquawk();

                    if (squawk.equals("") == false) {
                        queryString = String.format("SELECT count(*) AS SK"
                                + " FROM modes.squawk_list "
                                + "WHERE icao_number='%s' AND squawk='%s'",
                                icao_number, squawk);

                        exists = 0;

                        try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                            if (rs.next() == true) {
                                exists = rs.getInt("SK");
                            }
                        } catch (SQLException e3) {
                        }

                        if (exists == 0) {
                            queryString = String.format("INSERT INTO modes.squawk_list ("
                                    + "icao_number,"
                                    + "utcdetect,"
                                    + "squawk) VALUES ('%s',%d,'%s')",
                                    icao_number,
                                    time,
                                    squawk);

                            try (Statement query = db.createStatement()) {
                                query.executeUpdate(queryString);
                            } catch (SQLException e67) {
                                System.out.println("DataBlockParser::run query squawk Error: " + queryString + " " + e67.getMessage());
                            }
                        }
                    }
                    
                    /*
                     * We now process alerts
                     * Check for duplicates
                     */
                    queryString = String.format("SELECT count(*) AS AK FROM modes.alert_list "
                            + "WHERE icao_number='%s' AND utcdetect=%d",
                            icao_number,
                            time);

                    exists = 0;

                    try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                        if (rs.next() == true) {
                            exists = rs.getInt("AK");
                        }
                    } catch (SQLException e77) {
                        System.out.println("DataBlockParser::run query alert_list warn: " + queryString + " " + e77.getMessage());
                    }

                    if (exists == 0) {
                        boolean aa = trk.getAlert();
                        boolean ea = trk.getEmergency();
                        boolean sa = trk.getSPI();
                        boolean ja = trk.getHijack();
                        boolean ca = trk.getCommOut();

                        if (aa == true || ea == true || sa == true || ja == true || ca == true) {
                            queryString = String.format("INSERT INTO modes.alert_list ("
                                    + "icao_number,"
                                    + "utcdetect,"
                                    + "alert,"
                                    + "emergency,"
                                    + "spi,"
                                    + "hijack,"
                                    + "comm_out"
                                    + ") VALUES ("
                                    + "'%s',"
                                    + "%d,"
                                    + "%d,"
                                    + "%d,"
                                    + "%d,"
                                    + "%d,"
                                    + "%d)",
                                    icao_number,
                                    time,
                                    (aa == true) ? 1 : 0,
                                    (ea == true) ? 1 : 0,
                                    (sa == true) ? 1 : 0,
                                    (ja == true) ? 1 : 0,
                                    (ca == true) ? 1 : 0);

                            try (Statement query = db.createStatement()) {
                                query.executeUpdate(queryString);
                            } catch (SQLException e66) {
                                System.out.println("DataBlockParser::run query alert Error: " + queryString + " " + e66.getMessage());
                            }
                        }
                    }

                    /*
                     * We now process registrations
                     * Check for duplicates
                     */
                    registration = trk.getRegistration();

                    if (registration.equals("") == false) {
                        queryString = String.format("SELECT count(*) AS RG FROM modes.icao_list "
                                + "WHERE icao_number='%s' AND registration='%s'",
                                icao_number,
                                registration);

                        exists = 0;

                        try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                            if (rs.next() == true) {
                                exists = rs.getInt("RG");
                            }
                        } catch (SQLException e7) {
                            System.out.println("DataBlockParser::run query icao_list warn: " + queryString + " " + e7.getMessage());
                        }

                        if (exists == 0) {
                            queryString = String.format("UPDATE modes.icao_list SET "
                                    + "registration = '%s' WHERE icao_number = '%s'",
                                registration,
                                icao_number);

                            try (Statement query = db.createStatement()) {
                                query.executeUpdate(queryString);
                            } catch (SQLException e77) {
                                System.out.println("DataBlockParser::run query icao_list warn: " + queryString + " " + e77.getMessage());
                            }
                        }
                    }

                    /*
                     * We now process callsigns
                     * Check for duplicates
                     */
                    callsign = trk.getCallsign();

                    if (callsign.equals("") == false) {     // false = has callsign
                        queryString = String.format("SELECT count(*) AS CS FROM modes.callsign_list"
                                + " WHERE callsign='%s' AND icao_number='%s'",
                                callsign,
                                icao_number);

                        exists = 0;

                        try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                            if (rs.next() == true) {
                                exists = rs.getInt("CS");
                            }
                        } catch (SQLException e89) {
                        }

                        if (exists == 0) {
                            queryString = String.format("INSERT INTO modes.callsign_list ("
                                    + "callsign,"
                                    + "icao_number,"
                                    + "utcdetect"
                                    + ") VALUES ("
                                    + "'%s',"
                                    + "'%s',"
                                    + "%d)",
                                    callsign,
                                    icao_number,
                                    time);

                            try (Statement query = db.createStatement()) {
                                query.executeUpdate(queryString);
                            } catch (SQLException e90) {
                                System.out.println("DataBlockParser::run query callsign_list warn: " + queryString + " " + e90.getMessage());
                            }
                        }
                    }

                    /*
                     * We now process radar/si
                     */
                    int iid = trk.getRadarIID();
                    int sib = trk.getRadarSI() ? 1 : 0;

                    /*
                     * Don't fill database up with NULL's
                     */
                    if (iid != -99) {
                        queryString = String.format("SELECT count(*) AS RSK FROM modes.radar_list "
                                + "WHERE icao_number='%s' "
                                + "AND radar_site=%d "
                                + "AND radar_iid=%d "
                                + "AND radar_SI=%d "
                                + "AND utcdetect=%d",
                                icao_number,
                                radar_site,
                                iid,
                                sib,
                                time);

                        exists = 0;

                        try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                            if (rs.next() == true) {
                                exists = rs.getInt("RSK");
                            }
                        } catch (SQLException e79) {
                            System.out.println("DataBlockParser::run query radar_list warn: " + queryString + " " + e79.getMessage());
                        }

                        if (exists == 0) {
                            queryString = String.format("INSERT INTO modes.radar_list ("
                                    + "icao_number,"
                                    + "utcdetect,"
                                    + "radar_site,"
                                    + "radar_iid,"
                                    + "radar_si"
                                    + ") VALUES ("
                                    + "'%s',"
                                    + "%d,"
                                    + "%d,"
                                    + "NULLIF(%d, -99),"
                                    + "%d)",
                                    icao_number,
                                    time,
                                    radar_site,
                                    iid,
                                    sib);

                            try (Statement query = db.createStatement()) {
                                query.executeUpdate(queryString);
                            } catch (SQLException e92) {
                                System.out.println("DataBlockParser::run query radar_list warn: " + queryString + " " + e92.getMessage());
                            }
                        }
                    }

                    /*
                     * We now process speed/track
                     *
                     * Limit the rows to one per utcdetect
                     */
                    queryString = String.format("SELECT count(*) AS STS FROM modes.speed_list "
                            + "WHERE icao_number='%s' "
                            + "AND radar_site=%d "
                            + "AND utcdetect=%d",
                            icao_number,
                            radar_site,
                            time);

                    exists = 0;

                    try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                        if (rs.next() == true) {
                            exists = rs.getInt("STS");
                        }
                    } catch (SQLException e799) {
                        System.out.println("DataBlockParser::run query speed_list warn: " + queryString + " " + e799.getMessage());
                    }

                    if (exists == 0) {
                        float spd = trk.getGroundSpeed();
                        float gt = trk.getGroundTrack();
                        float cspd = trk.getComputedGroundSpeed();
                        float cgt = trk.getComputedGroundTrack();

                        /*
                         * If no speed transmitted (null), skip the database write
                         * unless computed values are available
                         */
                        boolean skip = false;
                        
                        if ((spd == -999.0f) && (gt == -999.0f)) {
                            if ((cspd == -999.0f) && (cgt == -999.0f)) {
                                skip = true;
                            }
                        }

                        if (skip == false) {
                            if ((cspd == -999.0f) && (cgt == -999.0f)) {
                                cspd = cgt = 0.0f;   // write 0 rather than null
                            }

                            queryString = String.format("INSERT INTO modes.speed_list ("
                                    + "icao_number,"
                                    + "utcdetect,"
                                    + "radar_site,"
                                    + "groundSpeed,"
                                    + "groundTrack,"
                                    + "gsComputed,"
                                    + "gtComputed"
                                    + ") VALUES ("
                                    + "'%s',"
                                    + "%d,"
                                    + "%d,"
                                    + "NULLIF(%.1f, -999.0),"
                                    + "NULLIF(%.1f, -999.0),"
                                    + "NULLIF(%.1f, -999.0),"
                                    + "NULLIF(%.1f, -999.0))",
                                    icao_number,
                                    time,
                                    radar_site,
                                    spd,
                                    gt,
                                    cspd,
                                    cgt);

                            try (Statement query = db.createStatement()) {
                                query.executeUpdate(queryString);
                            } catch (SQLException e93) {
                                System.out.println("DataBlockParser::run query speed_list warn: " + queryString + " " + e93.getMessage());
                            }
                        }
                    }
                    
                    /*
                     * We now process altitude
                     *
                     * Limit the rows to one per utcdetect
                     */
                    queryString = String.format("SELECT count(*) AS ASK FROM modes.altitude_list "
                            + "WHERE icao_number='%s' "
                            + "AND radar_site=%d "
                            + "AND utcdetect=%d",
                            icao_number,
                            radar_site,
                            time);

                    exists = 0;

                    try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                        if (rs.next() == true) {
                            exists = rs.getInt("ASK");
                        }
                    } catch (SQLException e788) {
                        System.out.println("DataBlockParser::run query altitude_list warn: " + queryString + " " + e788.getMessage());
                    }

                    if (exists == 0) {
                        /*
                         * Don't load the database up with
                         * a bunch of null crap.
                         */
                        int alt = trk.getAltitude();
                        
                        if (alt != -9999) {
                            queryString = String.format("INSERT INTO modes.altitude_list ("
                                    + "icao_number,"
                                    + "utcdetect,"
                                    + "radar_site,"
                                    + "altitude,"
                                    + "altitude_df00,"
                                    + "altitude_df04,"
                                    + "altitude_df16,"
                                    + "altitude_df17,"
                                    + "altitude_df18,"
                                    + "altitude_df20,"
                                    + "verticalRate,"
                                    + "verticalTrend,"
                                    + "onground"
                                    + ") VALUES ("
                                    + "'%s',"
                                    + "%d,"
                                    + "%d,"
                                    + "NULLIF(%d, -9999)," // alt
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999),"
                                    + "NULLIF(%d, -9999)," // vert rate
                                    + "%d,"
                                    + "%d)",
                                    icao_number,
                                    time,
                                    radar_site,
                                    alt,
                                    trk.getAltitudeDF00(),
                                    trk.getAltitudeDF04(),
                                    trk.getAltitudeDF16(),
                                    trk.getAltitudeDF17(),
                                    trk.getAltitudeDF18(),
                                    trk.getAltitudeDF20(),
                                    trk.getVerticalRate(),
                                    trk.getVerticalTrend(),
                                    ground);

                            try (Statement query = db.createStatement()) {
                                query.executeUpdate(queryString);
                            } catch (SQLException e94) {
                                System.out.println("DataBlockParser::run query altitude_list warn: " + queryString + " " + e94.getMessage());
                            }
                        }
                    }
                    
                    /*
                     * We now process amplitude
                     *
                     * Limit the rows to one per utcdetect
                     */
                    queryString = String.format("SELECT count(*) AS AMP FROM modes.amplitude_list "
                            + "WHERE icao_number='%s' "
                            + "AND radar_site=%d "
                            + "AND utcdetect=%d",
                            icao_number,
                            radar_site,
                            time);

                    exists = 0;

                    try (Statement query = db.createStatement(); ResultSet rs = query.executeQuery(queryString)) {
                        if (rs.next() == true) {
                            exists = rs.getInt("AMP");
                        }
                    } catch (SQLException e789) {
                        System.out.println("DataBlockParser::run query amplitude_list warn: " + queryString + " " + e789.getMessage());
                    }

                    if (exists == 0) {
                        queryString = String.format("INSERT INTO modes.amplitude_list ("
                                + "icao_number,"
                                + "utcdetect,"
                                + "radar_site,"
                                + "amplitude"
                                + ") VALUES ("
                                + "'%s',"
                                + "%d,"
                                + "%d,"
                                + "%d)",
                                icao_number,
                                time,
                                radar_site,
                                trk.getAmplitude());

                        try (Statement query = db.createStatement()) {
                            query.executeUpdate(queryString);
                        } catch (SQLException e91) {
                            System.out.println("DataBlockParser::run query callsign_list warn: " + queryString + " " + e91.getMessage());
                        }
                    }
                    
                    /*
                     * Database might get closed
                     * on exit or error, so kill thread
                     */
                    if (EOF == true) {
                        break;
                    }
                } // for loop
            } // if empty

            /*
             * Everything is copied to the database now
             * Simulate radar RPM
             */
            try {
                Thread.sleep(radarscan);
            } catch (InterruptedException e9) {
            }
        } // while
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
             * DF11 or DF17/18 have validated the packet by calling hasTrack().
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
                        if (hasTrack(icao_number)) {           // must be valid then
                            altitude = df00.getAltitude();
                            isOnGround = df00.getIsOnGround();      // true if vs1 == 1

                            updateTrackAmplitude(icao_number, amplitude, detectTime);
                            updateTrackAltitudeDF00(icao_number, altitude, detectTime);
                            updateTrackOnGround(icao_number, isOnGround, detectTime);
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np.getMessage());
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
                        if (hasTrack(icao_number) == true) {
                            altitude = df04.getAltitude();
                            isOnGround = df04.getIsOnGround();
                            alert = df04.getIsAlert();
                            spi = df04.getIsSPI();
                            emergency = df04.getIsEmergency();

                            updateTrackAmplitude(icao_number, amplitude, detectTime);
                            updateTrackAltitudeDF04(icao_number, altitude, detectTime);
                            updateTrackBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np.getMessage());
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
                        if (hasTrack(icao_number) == true) {
                            squawk = df05.getSquawk();
                            isOnGround = df05.getIsOnGround();
                            alert = df05.getIsAlert();
                            spi = df05.getIsSPI();
                            emergency = df05.getIsEmergency();

                            updateTrackAmplitude(icao_number, amplitude, detectTime);
                            updateTrackSquawk(icao_number, squawk, detectTime);
                            updateTrackBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np.getMessage());
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
                         * See if Track already exists
                         */
                        try {
                            if (hasTrack(icao_number) == false) {
                                /*
                                 * New Track
                                 */
                                Track t = new Track(icao_number, false);  // false == not TIS
                                t.setRegistration(nconverter.icao_to_n(icao_number));
                                addTrack(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np.getMessage());
                            break;
                        }

                        updateTrackAmplitude(icao_number, amplitude, detectTime);

                        radarIID = df11.getRadarIID();
                        si = df11.getRadarSI();
                        updateTrackRadarID(icao_number, radarIID, si, detectTime);

                        isOnGround = df11.getIsOnGround();
                        updateTrackOnGround(icao_number, isOnGround, detectTime);
                    }
                    break;
                default:    // output CSV
                    System.err.printf("%d,%d,%s%n", df5, amplitude, data);
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
             * the packet by calling hasTrack().  If not, then
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
                        if (hasTrack(icao_number)) {   // Note: if ICAO is "BAD" it will fail
                            isOnGround = df16.getIsOnGround();
                            altitude = df16.getAltitude();

                            updateTrackAmplitude(icao_number, amplitude, detectTime);
                            updateTrackAltitudeDF16(icao_number, altitude, detectTime);
                            updateTrackOnGround(icao_number, isOnGround, detectTime);

                            if (df16.getBDS() == 0x30) {   // BDS 3,0
                                data56 = df16.getMV();
                                int data30 = (int) (data56 >>> 26);
                                /*
                                 * Some planes send TTI = 0 which means nothing to do
                                 */
                                if ((data30 & 0x3) != 0) {
                                    insertTCASAlert(icao_number, df5, data56, detectTime);
                                }
                            }
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np.getMessage());
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
                         * See if Track already exists
                         */
                        try {
                            if (hasTrack(icao_number) == false) {
                                /*
                                 * New Track
                                 */
                                Track t = new Track(icao_number, false);  // false == not TIS
                                t.setRegistration(nconverter.icao_to_n(icao_number));
                                addTrack(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np.getMessage());
                            break;
                        }

                        updateTrackAmplitude(icao_number, amplitude, detectTime);

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

                                updateTrackCallsign(icao_number, callsign, category, detectTime);
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

                                updateTrackBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);
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

                                updateTrackBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);

                                altitude = df17.getAltitude();
                                updateTrackAltitudeDF17(icao_number, altitude, detectTime);

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
                                            updateTrackGroundSpeedTrueHeading(icao_number, groundSpeed, trueHeading, vSpeed, detectTime);
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
                                                updateTrackMagneticHeadingIAS(icao_number, magneticHeading, airspeed, vSpeed, detectTime);
                                            } else {
                                                updateTrackMagneticHeadingTAS(icao_number, magneticHeading, airspeed, vSpeed, detectTime);
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
                         * See if Track already exists
                         */
                        try {
                            if (hasTrack(icao_number) == false) {
                                /*
                                 * New Track
                                 */
                                Track t = new Track(icao_number, true);  // true == TIS
                                t.setRegistration(nconverter.icao_to_n(icao_number));
                                addTrack(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np.getMessage());
                        }

                        updateTrackAmplitude(icao_number, amplitude, detectTime);

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

                                updateTrackCallsign(icao_number, callsign, category, detectTime);
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

                                updateTrackBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);
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

                                updateTrackBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);

                                altitude = df18.getAltitude();
                                updateTrackAltitudeDF18(icao_number, altitude, detectTime);
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
                                            updateTrackGroundSpeedTrueHeading(icao_number, groundSpeed, trueHeading, vSpeed, detectTime);
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
                                                updateTrackMagneticHeadingIAS(icao_number, magneticHeading, airspeed, vSpeed, detectTime);
                                            } else {
                                                updateTrackMagneticHeadingTAS(icao_number, magneticHeading, airspeed, vSpeed, detectTime);
                                            }
                                        }
                                }
                        }
                    }
                    break;
                case 19:  // Military Squitters
                   break; // Get a lot of these, but no way to decode
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
                        if (hasTrack(icao_number)) {
                            altitude = df20.getAltitude();
                            isOnGround = df20.getIsOnGround();
                            emergency = df20.getIsEmergency();
                            alert = df20.getIsAlert();
                            spi = df20.getIsSPI();

                            updateTrackAmplitude(icao_number, amplitude, detectTime);
                            updateTrackAltitudeDF20(icao_number, altitude, detectTime);
                            updateTrackBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);

                            int bds = df20.getBDS();
                            data56 = df20.getData56();

                            if (bds == 0x20) {             // BDS 2,0
                                callsign = df20.getCallsign();
                                updateTrackCallsign(icao_number, callsign, detectTime);
                            } else if (bds == 0x30) {      // BDS 3,0
                                int data30 = (int) (data56 >>> 26);
                                /*
                                 * Some planes send TTI = 0 which means nothing to do
                                 */
                                if ((data30 & 0x3) != 0) {
                                    insertTCASAlert(icao_number, df5, data56, detectTime);
                                }
                            }
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np.getMessage());
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
                        if (hasTrack(icao_number)) {
                            squawk = df21.getSquawk();
                            isOnGround = df21.getIsOnGround();
                            emergency = df21.getIsEmergency();
                            alert = df21.getIsAlert();
                            spi = df21.getIsSPI();

                            updateTrackAmplitude(icao_number, amplitude, detectTime);
                            updateTrackSquawk(icao_number, squawk, detectTime);
                            updateTrackBoolean(icao_number, isOnGround, emergency, alert, spi, detectTime);

                            int bds = df21.getBDS();
                            data56 = df21.getData56();

                            switch (bds) {      // Bunch more available for decode
                                case 0x20:      // BDS 2,0 Callsign
                                    callsign = df21.getCallsign();
                                    updateTrackCallsign(icao_number, callsign, detectTime);
                                    break;
                                case 0x30:      // BDS 3,0 TCAS
                                    int data30 = (int) (data56 >>> 26);
                                    /*
                                     * Some planes send TTI = 0 which means nothing to do
                                     */
                                    if ((data30 & 0x3) != 0) {
                                        insertTCASAlert(icao_number, df5, data56, detectTime);
                                    }
                            }
                        }
                    } catch (NullPointerException np) {
                        /*
                         * Not likely to occur
                         */
                        System.err.println(np.getMessage());
                    }
                    break;
                default:    // output CSV
                    System.err.printf("%d,%d,%s%n", df5, amplitude, data);
            }
        }
    }
}
