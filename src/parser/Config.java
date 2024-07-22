/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/*
 * A Class to store configuration parameters.
 */
public final class Config {

    private int databaseTargetTimeout;
    private String databaseHost;
    private String databaseName;
    private String databasePort;
    private String databaseLogin;
    private String databasePassword;
    private String airportName;
    //
    private int amplitude;
    private int radarscan;
    private int radarid;
    //
    private float latitude;    // degrees
    private float longitude;   // degrees
    private int altitude;
    //
    private Properties Props;
    private String userDir;
    private String fileSeparator;
    private String OSConfPath;
    //
    private String commPort;

    public Config(String val) {
        String temp;

        commPort = "COM4";
        airportName = "";
        radarscan = 3;      // simulates a 20 RPM antenna, 10 would be 6 RPM
        radarid = 0;
        databaseTargetTimeout = 3;    // 3 minutes
        amplitude = 0;
        //
        latitude = 0.0f;
        longitude = 0.0f;
        altitude = 0;
        //
        Props = null;
        //
        userDir = System.getProperty("user.dir");
        fileSeparator = System.getProperty("file.separator");
        OSConfPath = userDir + fileSeparator + val;

        /*
         * Read the config file into the Properties class Note: The file must
         * exist, or we can not proceed.
         */
        try {
            FileInputStream in = new FileInputStream(OSConfPath);
            Props = new Properties();
            Props.load(in);
        } catch (IOException e1) {
            System.out.println("ModeSDecoder::Config Fatal: Unable to read configuration file " + OSConfPath);
            System.exit(-1);
        }

        /*
         * Now check which properties were selected, and take the defaults if
         * none given.
         */
        if (Props != null) {
            temp = Props.getProperty("radar.id");
            if (temp == null) {
                radarid = 0;
                System.out.println("radar.id not set, set to 0");
            } else {
                try {
                    radarid = Integer.parseInt(temp.trim());
                } catch (NumberFormatException e3) {
                    radarid = 0;
                }
            }

            temp = Props.getProperty("radar.scan");
            if (temp == null) {
                radarscan = 3;
                System.out.println("radar.scan not set, set to 3 seconds");
            } else {
                try {
                    radarscan = Integer.parseInt(temp.trim());

                    if (radarscan < 1) {
                        radarscan = 1;
                    } else if (radarscan > 13) {
                        radarscan = 13;
                    }
                } catch (NumberFormatException e4) {
                    radarscan = 3;
                }
            }

            temp = Props.getProperty("comm.port");
            if (temp == null) {
                commPort = "COM4";
                System.out.println("comm.port not set, set to COM4");
            } else {
                commPort = temp.trim();
            }

            temp = Props.getProperty("db.targettimeout");
            if (temp == null) {
                databaseTargetTimeout = 3;
                System.out.println("db.targettimeout not set, set to 3 minutes");
            } else {
                try {
                    databaseTargetTimeout = Integer.parseInt(temp.trim());
                } catch (NumberFormatException e6) {
                    databaseTargetTimeout = 3;
                }
            }

            temp = Props.getProperty("db.host");
            if (temp == null) {
                databaseHost = "127.0.0.1";
                System.out.println("db.host not set, set to 127.0.0.1");
            } else {
                databaseHost = temp.trim();
            }

            temp = Props.getProperty("db.name");
            if (temp == null) {
                databaseName = "modes";
                System.out.println("db.name not set, set to modes");
            } else {
                databaseName = temp.trim();
            }

            temp = Props.getProperty("db.port");
            if (temp == null) {
                databasePort = "3306";
                System.out.println("db.port not set, set to 3306");
            } else {
                databasePort = temp.trim();
            }

            temp = Props.getProperty("db.login");
            if (temp == null) {
                databaseLogin = "adsbrw";
                System.out.println("db.login not set, set to adsbrw");
            } else {
                databaseLogin = temp.trim();
            }

            temp = Props.getProperty("db.password");
            if (temp == null) {
                databasePassword = "secret";
                System.out.println("db.password not set, set to secret");
            } else {
                databasePassword = temp.trim();
            }

            temp = Props.getProperty("server.amplitude");
            if (temp == null) {
                amplitude = 0;
                System.out.println("server.amplitude not set, set to 0");
            } else {
                try {
                    amplitude = Integer.parseInt(temp.trim());
                } catch (NumberFormatException e) {
                    amplitude = 0;
                }
            }

            temp = Props.getProperty("station.latitude");
            if (temp == null) {
                latitude = 35.0f;
                System.out.println("station.latitude not set, set to 35.0");
            } else {
                try {
                    latitude = Float.parseFloat(temp.trim());
                } catch (NumberFormatException e) {
                    latitude = 35.0f;
                }
            }

            temp = Props.getProperty("station.longitude");
            if (temp == null) {
                longitude = -97.0f;
                System.out.println("station.longitude not set, set to -97.0");
            } else {
                try {
                    longitude = Float.parseFloat(temp.trim());
                } catch (NumberFormatException e) {
                    longitude = -97.0f;
                }
            }

            temp = Props.getProperty("station.altitude");
            if (temp == null) {
                altitude = 0;
                System.out.println("station.altitude not set, set to 0");
            } else {
                try {
                    altitude = Integer.parseInt(temp.trim());
                } catch (NumberFormatException e) {
                    altitude = 0;
                }
            }

            temp = Props.getProperty("station.airport");
            if (temp == null) {
                airportName = "KOKC";
                System.out.println("station.airport not set, set to KOKC");
            } else {
                airportName = temp.trim();
            }
        }
    }

    /**
     * Method to return the Beast detector serial Comm Port
     *
     * @return a string Representing the Beast serial port
     */
    public String getCommPort() {
        return commPort;
    }

    /**
     * Getter to return the filename path of the configuration file
     *
     * @return a string Representing the configuration file path
     */
    public String getOSConfPath() {
        return OSConfPath;
    }

    /**
     * Getter to return the database connection URL
     *
     * @return a string Representing the database URL
     */
    public String getDatabaseURL() {
        return "jdbc:mysql://" + databaseHost + ":" + databasePort + "/" + databaseName;
    }

    /**
     * Getter to return the database login name
     *
     * @return a string Representing the database login name
     */
    public String getDatabaseLogin() {
        return databaseLogin;
    }

    /**
     * Getter to return the database login password
     *
     * @return a string Representing the database login password
     */
    public String getDatabasePassword() {
        return databasePassword;
    }

    /**
     * Getter to return the target timeout value
     *
     * @return an int Representing the database timeout for archiving
     */
    public int getDatabaseTargetTimeout() {
        return databaseTargetTimeout;
    }

    public int getAmplitude() {
        return amplitude;
    }

    public float getStationLatitude() {
        return latitude;
    }

    public float getStationLongitude() {
        return longitude;
    }

    public int getStationAltitude() {
        return altitude;
    }

    public String getStationAirport() {
        return airportName;
    }

    /**
     * Getter to provide for multiple detector data in the same database Each
     * network connect should be configured with a different radar ID
     *
     * @return a int Representing a numeric radar ID
     */
    public int getRadarID() {
        return this.radarid;
    }

    public int getRadarScanTime() {
        return this.radarscan;
    }
}
