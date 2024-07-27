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

    private static final long RATE1 = 30L * 1000L;              // 30 seconds
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
    private static PressureAltitude pa;
    private DataBlock dbk;
    //
    private final Connection db;
    private Statement query;
    private Statement querytt;
    private final Config config;

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

        radar_site = cf.getRadarSite();
        radarscan = (long) cf.getRadarScanTime() * 1000L;
        icao_number = "";
        callsign = "";
        squawk = "";
        mdhash = "";      
        airport = "";
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
        timer2 = new Timer();

        process = new Thread(this);
        process.setName("DataBlockParser");
        process.setPriority(Thread.NORM_PRIORITY);
    }

    @Override
    public void start() {
        process.start();

        airport = pa.getAirportName();
        elevation = pa.getAirportElevation();

        EOF = false;
        
        timer1.scheduleAtFixedRate(task1, 0L, RATE1);
        timer2.scheduleAtFixedRate(task2, 10L, RATE2);
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
     * Method to return a collection of all active tracks.
     * (tracks still receiving updates, and not landed or faded).
     *
     * @return a list representing all the tracks that are active
     */
    public List<Track> getAllActiveTracks() throws NullPointerException {
        List<Track> result = new ArrayList<>();
        List<Track> tracklist;
        
        try {
            tracklist = getAllTracks();
        } catch (NullPointerException e) {
            return result;  // empty
        }

        if (tracklist.isEmpty() == false) {
            for (Track track : tracklist) {
                if (track.getActive() == true) {
                    result.add(track);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Probably not used
     * 
     * Method to return a collection of all active updated tracks.
     *
     * @return a list representing all the tracks that have been updated
     */
    public List<Track> getAllActiveUpdatedTracks() throws NullPointerException {
        List<Track> result = new ArrayList<>();
        List<Track> tracklist;
        
        try {
            tracklist = getAllActiveTracks();
        } catch (NullPointerException e) {
            return result;  // empty
        }

        if (tracklist.isEmpty() == false) {
            for (Track track : tracklist) {
                if (track.getUpdated() == true) {
                    result.add(track);
                }
            }
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

    /*
     * Probably no use case
     */
    public void markTrackActive(String icao) throws NullPointerException {
       synchronized (tracks) {
            if (tracks.containsKey(icao) == true) {
                Track track = tracks.get(icao);
                track.setActive(true);
                addTrack(icao, track);
            }
       }
    }
    
    public void markTrackInactive(String icao) throws NullPointerException {
       synchronized (tracks) {
            if (tracks.containsKey(icao) == true) {
                Track track = tracks.get(icao);
                track.setActive(false);
                addTrack(icao, track);
            }
       }
    }

    /*
     * Method to add a new TCAS alert for this track
     * into the database table
     */
    public void insertTCASAlert(String hexid, long data56, long time) {
        /*
         * See if this is even a valid track
         */
        if (hasTrack(hexid)) {
            Track track = getTrack(hexid);
            int alt16 = track.getAltitudeDF16();  // might be -9999 (null)

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
     * This will look through the Track table every 30 seconds and mark
     * inactive entries that are over X minutes old.  In that case the
     * track has probably landed or faded-out from coverage.
     *
     * Note: Only active tracks are affected.
     */
    private class UpdateActiveTracksTask extends TimerTask {

        private List<Track> tracks;
        private long tracktime;

        @Override
        public void run() {
            long currentTime = zulu.getUTCTime();
            long delta;

            try {
                tracks = getAllActiveTracks();
            } catch (NullPointerException te) {
                return; // No tracks found
            }

            for (Track track : tracks) {
                try {
                    tracktime = track.getUpdatedTime();

                    if (tracktime != 0L) {
                        // find tracks that haven't been updated in X minutes
                        delta = Math.abs(currentTime - tracktime);

                        if (delta >= trackTimeout) {
                            markTrackInactive(track.getAircraftICAO());
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
                tracks = getAllActiveTracks();
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
        try {
            Track track = getTrack(hexid);
            track.setPosition(latlon, mode, time);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    public void createTrackLatLon(String hexid, boolean tis, LatLon latlon, int mode, long time) {
        try {
            Track track = new Track(hexid, tis);
            track.setPosition(latlon, mode, time);
            track.setUpdatedTime(time);
            addTrack(hexid, track);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    /*
     * Track detection processing and adding to the Track library.
     *
     * Decode Mode-S Short (56-Bit) and Long (112-Bit) Packets
     */
    @Override
    public void run() {
        ResultSet rs = null;
        String queryString = null;
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
             * We now have active tracks to process
             */
            try {
                List<Track> table = getAllActiveTracks();

                if (table.isEmpty() == false) {
                    for (Track trk : table) {
                        time = trk.getUpdatedTime();
                        trk.setUpdated(false);  // reset the updated boolean

                        icao_number = trk.getAircraftICAO();

                        /*
                         * See if this ICAO exists yet in the track table, and
                         * has our radar ID. If it does, we can do an update, and
                         * if not we will do an insert.
                         */
                        try {
                            queryString = String.format("SELECT count(*) AS TC FROM tracks WHERE icao_number='%s' AND radar_site=%d",
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

                        if (exists > 0) {         // track exists
                            queryString = String.format("UPDATE tracks SET utcupdate=%d,"
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
                                    + "hadSPI=%d,"
                                    + "active=%d"
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
                                    trk.getActive() ? 1 : 0,
                                    icao_number,
                                    radar_site);
                        } else {                // track doesn't exist
                            queryString = String.format("INSERT INTO tracks ("
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
                                    + "hadSPI,"
                                    + "active) "
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
                                    + "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d)",
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
                                    trk.getHadSPI() ? 1 : 0,
                                    trk.getActive() ? 1 : 0);
                        }

                        try {
                            query = db.createStatement();
                            query.executeUpdate(queryString);
                            query.close();
                        } catch (SQLException e5) {
                            query.close();
                            System.out.println("DataBlockParser::run insert/update tracks table Error: " + queryString + " " + e5.getMessage());
                        }

                        if (trk.getUpdatePosition() == true) {
                            trk.setUpdatePosition(false);

                            // Safety check, we don't want NULL's
                            // TODO: Figure out why we get those
                            if ((trk.getLatitude() != -999.0F) && (trk.getLongitude() != -999.0F)) {
                                queryString = String.format("INSERT INTO position_echo ("
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
                                        + "(SELECT flight_id FROM tracks WHERE icao_number='%s' AND radar_site=%d),"
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
                                    System.out.println("DataBlockParser::run query position_echo Error: " + queryString + " " + e6.getMessage());
                                }
                            }
                        }
                    }
                }

                /*
                 * We now have all tracks to process callsigns
                 */
                List<Track> all = getAllTracks();
                
                if (all.isEmpty() == false) {
                    for (Track trk : all) {
                        icao_number = trk.getAircraftICAO();
                        callsign = trk.getCallsign();
                        String registration = trk.getRegistration();

                        if (registration.equals("") == false) {
                            try {
                                queryString = String.format("SELECT count(*) AS RG FROM icao_list"
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
                                System.out.println("DataBlockParser::run query icao_list warn: " + queryString + " " + e7.getMessage());
                                continue;   // skip the following
                            }

                            if (exists > 0) {
                                queryString = String.format("UPDATE icao_list SET registration='%s' WHERE icao_number='%s'",
                                    registration,
                                    icao_number);

                                    query = db.createStatement();
                                    query.executeUpdate(queryString);
                                    query.close();
                            }
                        }

                        /*
                         * Does this track have a callsign?
                         */
                        if (callsign.equals("") == false) {     // false = has callsign
                            try {
                                queryString = String.format("SELECT count(*) AS CS FROM callsign_list"
                                    + " WHERE callsign='%s' AND icao_number='%s'",
                                        callsign,
                                        icao_number);

                                time = zulu.getUTCTime();
                                    
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
                                System.out.println("DataBlockParser::run query callsign_list warn: " + queryString + " " + e8.getMessage());
                                continue;   // skip the following
                            }

                            /*
                             * Does the callsign_list table have a copy of this callsign?
                             */
                            if (exists > 0) {
                                // yes, update the time
                                queryString = String.format("UPDATE callsign_list SET utcupdate=%d WHERE callsign='%s' AND icao_number='%s'",
                                    time, callsign, icao_number);
                            } else {
                                // callsign not in table, so add it
                                queryString = String.format("INSERT INTO callsign_list (callsign,flight_id,icao_number,"
                                    + "utcdetect,utcupdate) VALUES ('%s',"
                                    + "(SELECT flight_id FROM tracks WHERE icao_number='%s'),"
                                    + "'%s',%d,%d)",
                                    callsign,
                                    icao_number,
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
                // No tracks updated
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
                        if (hasTrack(icao_number)) {
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
                        if (hasTrack(icao_number)) {
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
                         * See if Track already exists
                         */
                        try {
                            if (hasTrack(icao_number) == false) {
                                /*
                                 * New Track
                                 */
                                Track t = new Track(icao_number, false);  // false == not TIS
                                t.setRegistration(nconverter.icao_to_n(icao_number));
                                t.setActive(true);
                                addTrack(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
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
                         * See if Track already exists
                         */
                        try {
                            if (hasTrack(icao_number) == false) {
                                /*
                                 * New Track
                                 */
                                Track t = new Track(icao_number, false);  // false == not TIS
                                t.setRegistration(nconverter.icao_to_n(icao_number));
                                t.setActive(true);
                                addTrack(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
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
                                t.setActive(true);
                                addTrack(icao_number, t);
                            }
                        } catch (NullPointerException np) {
                            /*
                             * Not likely to occur
                             */
                            System.err.println(np);
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
                default:    // output CSV
                    System.err.printf("%d,%d,%s%n", df5, amplitude, data);
            }
        }
    }
}
