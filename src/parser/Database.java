/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import decoder.DataBlockParser;
import decoder.Track;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public final class Database extends Thread {

    private final Thread database;
    private DataBlockParser parser;
    //
    private static final long RATE = 30000L;        // 30 Seconds in milliseconds
    //
    private Connection db1;
    private Connection db2;
    private Statement query;
    private Statement querytt;
    //
    private static boolean EOF;
    //
    private Config config;
    private String acid;
    private int radarid;
    private final long targetTimeout;
    private long radarscan;
    //
    private final Timer targetTimer;
    //
    private final TimerTask targetTimeoutTask;
    private final ZuluMillis zulu;

    public Database(Config cf, DataBlockParser p) {
        zulu = new ZuluMillis();
        parser = p;
        config = cf;
        radarid = cf.getRadarID();
        radarscan = (long) cf.getRadarScanTime() * 1000L;
        acid = "";
        EOF = false;
        
        database = new Thread(this);
        database.setName("Database");
        database.setPriority(Thread.NORM_PRIORITY);
        
        String connectionURL = config.getDatabaseURL();

        Properties properties = new Properties();
        properties.setProperty("user", config.getDatabaseLogin());
        properties.setProperty("password", config.getDatabasePassword());
        properties.setProperty("useSSL", "false");                    // added in Jun 2024
        properties.setProperty("allowPublicKeyRetrieval", "true");    // added in July 2024
        properties.setProperty("serverTimezone", "UTC");              // added in Jun 2024

        /*
         * You need the ODBC MySQL driver library in the same directory you have
         * the executable JAR file of this program, but under a lib directory.
         */
        try {
            db1 = DriverManager.getConnection(connectionURL, properties);
        } catch (SQLException e) {
            System.err.println("Database Fatal: Unable to open database 1 " + connectionURL + " " + e.getMessage());
            System.exit(0);
        }

        try {
            db2 = DriverManager.getConnection(connectionURL, properties);
        } catch (SQLException e3) {
            System.err.println("Database Fatal: Unable to open database 2 " + connectionURL + " " + e3.getMessage());
            System.exit(0);
        }

        targetTimeout = config.getDatabaseTargetTimeout();
        targetTimeoutTask = new TargetTimeoutThread(targetTimeout);

        targetTimer = new Timer();
        targetTimer.scheduleAtFixedRate(targetTimeoutTask, 0L, RATE); // Update targets every 30 seconds
    }

    public void startup() {
        database.start();
    }

    public void close() {
        EOF = true;

        try {
            parser.close();
            db1.close();
            db2.close();
        } catch (SQLException e) {
            System.out.println("Database::close Closing Bug " + e.getMessage());
            System.exit(0);
        }
    }

    public Connection getDBConnection() {
        return db2;
    }

    @Override
    public void run() {
        List<Track> table;
        ResultSet rs = null;
        String queryString = "";
        int ground, exists;
        long time;

        try {
            while (EOF == false) {
                try {
                    table = parser.getAllUpdatedTargets();

                    if (table.isEmpty() == false) {
                        for (Track trk : table) {
                            time = trk.getUpdatedTime();
                            trk.setUpdated(false);

                            acid = trk.getAircraftID();

                            /*
                             * See if this ACID exists yet in the target table, and
                             * has our radar ID. If it does, we can do an update, and
                             * if not we will do an insert.
                             */
                            try {
                                queryString = String.format("SELECT count(1) AS TC FROM target WHERE acid='%s' && radar_id=%d",
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
                                        + "altitude=NULLIF(%d, -9999),"
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
                                        + " WHERE acid='%s' && radar_id=%d",
                                        time,
                                        trk.getAltitude(),
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
                                        + "altitude,"
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
                                        trk.getAltitude(),
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
                                System.out.println("Database::run query target Error: " + queryString + " " + e5.getMessage());
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
                                            + "latitude,"
                                            + "longitude,"
                                            + "altitude,"
                                            + "verticalTrend,"
                                            + "onground"
                                            + ") VALUES ("
                                            + "(SELECT flight_id FROM target WHERE acid='%s' && radar_id=%d),"
                                            + "%d,"
                                            + "'%s',"
                                            + "%d,"
                                            + "%f,"
                                            + "%f,"
                                            + "NULLIF(%d, -9999),"
                                            + "%d,"
                                            + "%d)",
                                            acid,
                                            radarid,
                                            radarid,
                                            acid,
                                            time,
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
                                        System.out.println("Database::run query targetecho Error: " + queryString + " " + e6.getMessage());
                                    }
                                }
                            }

                            if (!trk.getRegistration().equals("")) {
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
                                    System.out.println("Database::run query modestable warn: " + queryString + " " + e7.getMessage());
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
                                            + " WHERE callsign='%s' && acid='%s' && radar_id=%d",
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
                                    System.out.println("Database::run query callsign warn: " + queryString + " " + e8.getMessage());
                                    continue;   // skip the following
                                }

                                if (exists > 0) {
                                    queryString = String.format("UPDATE callsign SET utcupdate=%d WHERE callsign='%s' && acid='%s' && radar_id=%d",
                                            time, trk.getCallsign(), acid, radarid);
                                } else {
                                    queryString = String.format("INSERT INTO callsign (callsign,flight_id,radar_id,acid,"
                                            + "utcdetect,utcupdate) VALUES ('%s',"
                                            + "(SELECT flight_id FROM target WHERE acid='%s' && radar_id=%d),"
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
                    }
                } catch (Exception g1) {
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
        } catch (Exception e1) {
            // Probably an I/O Exception
            try {
                query.close();
            } catch (SQLException e2) {
            }
            
            System.out.println("Database::run Fatal Exception " + queryString + " " + e1.getMessage());
            close();
        }
    }

    /**
     * TargetTimeoutThread
     *
     * A TimerTask class to move target to history after fade-out,
     */
    private class TargetTimeoutThread extends TimerTask {

        private long time;
        private long timeout;
        private final long min;

        /**
         * Clean up the target table
         *
         * @param to a long Representing the timeout in minutes
         */
        public TargetTimeoutThread(long to) {
            min = to;
        }

        @Override
        public void run() {
            String update;

            time = zulu.getUTCTime();
            timeout = time - (min * 60L * 1000L);    // timeout in milliseconds

            /*
             * This also converts the timestamp to SQL format, as the history is
             * probably not going to need any further computations.
             */
            update = String.format(
                    "INSERT INTO targethistory (flight_id,radar_id,acid,utcdetect,utcfadeout,altitude,groundSpeed,"
                    + "groundTrack,gsComputed,gtComputed,callsign,latitude,longitude,verticalRate,verticalTrend,squawk,alert,emergency,spi,onground,"
                    + "hijack,comm_out,hadAlert,hadEmergency,hadSPI) SELECT flight_id,radar_id,acid,FROM_UNIXTIME(utcdetect/1000),FROM_UNIXTIME(utcupdate/1000),"
                    + "altitude,groundSpeed,groundTrack,gsComputed,gtComputed,callsign,latitude,longitude,verticalRate,verticalTrend,squawk,alert,"
                    + "emergency,spi,onground,hijack,comm_out,hadAlert,hadEmergency,hadSPI FROM target WHERE target.utcupdate <= %d",
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
                System.out.println("Database::run targethistory SQL Error: " + update + " " + tt1.getMessage());
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
                System.out.println("Database:run delete SQL Error: " + update + " " + tt3.getMessage());
            }
        }
    }
}
