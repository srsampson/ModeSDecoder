/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to store all target information, and provide access
 * methods to use and update.
 */
public final class Target implements IConstants {

    private final List<TCAS> tcasAlerts;
    //
    private String callsign;
    private final String acid;
    private final String opStatus;
    private int version;                        // Transponder version
    private int mode;
    private int positionMode;
    private String squawk;
    private int verticalRate;
    private int category;
    private int haeDelta;
    private int altitudeHAE;
    private int altitudeDF00;
    private int altitudeDF04;
    private int altitudeDF16;
    private int altitudeDF17;
    private int altitudeDF18;
    private int altitudeDF20;
    private int radarIID;                       // IID is a II code (00 - 15)
    private boolean si;                         // IID is a SI code (00 - 63)
    private boolean updated;
    private boolean alert;
    private boolean emergency;
    private boolean spi;
    private boolean isOnGround;                // Might be on ground but no position
    //
    private double latitude;
    private double longitude;
    private double groundSpeed;
    private double trueHeading;
    private double heading;
    private double ias;
    private double tas;
    //
    private double bearing;
    private double range;
    //
    private long updatedPosTime;
    private long updatedTime;
    //
    private boolean isLocal;            // Target is from one of our radars/not remote
    private boolean isRelayed;          // Target has been relayed by a ground site (TIS-B)
    //
    private int[] signalLevel;
    //
    private long odd_cprtime;
    private int odd_cprlat;
    private int odd_cprlon;
    private int odd_cprnuc;
    //
    private long even_cprtime;
    private int even_cprlat;
    private int even_cprlon;
    private int even_cprnuc;
    //
    private int pos_nuc;        // NUCp of last computed position

    /**
     * A target is the complete data structure of the Aircraft ID (acid). It
     * takes several different target reports to gather all the data, but this
     * is where it is finally stored.
     *
     * @param ac a String representing the ICAO ID of the vehicle
     * @param relayed a boolean representing a target that is relayed (TIS-B)
     */
    public Target(String ac, boolean relayed) {
        acid = ac;
        isLocal = true;
        isRelayed = relayed;
        version = 0;
        opStatus = "";
        callsign = "";
        mode = TRACK_MODE_NORMAL;
        positionMode = POSITION_MODE_UNKNOWN;
        squawk = "0000";
        category = 0;
        latitude = 0.0;
        longitude = 0.0;
        bearing = 0.0;
        range = 0.0;
        altitudeDF00 = 0;
        altitudeDF04 = 0;
        altitudeDF16 = 0;
        altitudeDF17 = 0;
        altitudeDF18 = 0;
        altitudeDF20 = 0;
        verticalRate = 0;
        alert = emergency = spi = si
                = isOnGround = updated = false;        // Might be on ground but no position
        groundSpeed = 0.0;
        trueHeading = 0.0;
        heading = 0.0;
        ias = 0.0;
        tas = 0.0;
        updatedPosTime = 0L;
        updatedTime = 0L;

        tcasAlerts = new ArrayList<>();
    }

    /**
     * Method to return the Aircraft ID
     *
     * @return a String Representing the 6-character ICAO ID
     */
    public String getAcid() {
        return acid;
    }

    public void setLocal(boolean val) {
        isLocal = val;
    }

    public boolean getLocal() {
        return isLocal;
    }

    public void setRelayed(boolean val) {
        isRelayed = val;
    }

    public boolean getRelayed() {
        return isRelayed;
    }

    /*
     * Transponder version detected
     *
     * 0 = Unknown
     * 1 = Version 1
     * 2 = Version 2
     */
    public int getVersion() {
        return version;
    }
    
    public void setVersion(int val) {
        version = val;
    }

    /**
     * Method to determine if there are any TCAS alerts on the queue
     *
     * @return a boolean Representing whether there are TCAS alerts on the queue
     */
    public synchronized boolean hasTCASAlerts() {
        return !tcasAlerts.isEmpty();
    }

    /**
     * Method to return the number of TCAS alerts on the queue
     *
     * @return an integer representing the number of TCAS alerts on the queue
     */
    public synchronized int getNumberOfTCASAlerts() {
        return tcasAlerts.size();
    }

    /*
     * Method to add a new TCAS alert for this target at the tail of the queue
     */
    public synchronized void insertTCAS(long data56, long time) {
        TCAS tcas = new TCAS(data56, time, getAltitude());

        /*
         * Some TCAS are just advisory, no RA generated
         * So we keep these off the table, as they are basically junk.
         * 
         * TCAS class only sets time if RA is active.
         */
        if (tcas.getUpdateTime() == 0L) {
            return;
        }

        try {
            tcasAlerts.add(tcas);
        } catch (UnsupportedOperationException | IllegalArgumentException | ClassCastException | NullPointerException e) {
            System.err.println("Target::insertTCAS Exception during addElement " + e.toString());
        }
    }

    /**
     * Method to return a list TCAS alerts
     *
     * @return a vector of TCAS alert objects
     */
    public synchronized List<TCAS> getTCASAlerts() {
        List<TCAS> result = new ArrayList<>();

        try {
            for (int i = 0; i < tcasAlerts.size(); i++) {
                if (tcasAlerts.get(i).getThreatTerminated() == false) {
                    result.add(tcasAlerts.get(i));
                }
            }
        } catch (Exception e) {
            System.err.println("Target::getTCASAlerts Exception during addElement " + e.toString());
        }

        return result;
    }

    /*
     * Method to remove expired TCAS alerts from queue
     * 
     * @param t a long representing the current time in milliseconds
     */
    public synchronized void removeTCAS(long t) {
        t -= 60000L;         // subtract 60 seconds

        try {
            for (int i = 0; i < tcasAlerts.size(); i++) {
                if (tcasAlerts.get(i).getUpdateTime() <= t) {
                        tcasAlerts.remove(i);
                }
            }
        } catch (Exception e2) {
            System.err.println("Target::removeTCAS Exception during remove " + e2.toString());
        } 
    }

    public void setRadarIID(int val) {
        if (radarIID != val) {
            radarIID = val;
            updated = true;
        }
    }

    public int getRadarIID() {
        return radarIID;
    }

    public void setSI(boolean val) {
        si = val;
    }

    public boolean getSI() {
        return si;
    }

    public void setHeading(double val) {
        if (heading != val) {
            heading = val;
            updated = true;
        }
    }

    public double getHeading() {
        return heading;
    }

    public void setIAS(double val) {
        if (ias != val) {
            ias = val;
            updated = true;
        }
    }

    public double getIAS() {
        return ias;
    }

    public void setTAS(double val) {
        if (tas != val) {
            tas = val;
            updated = true;
        }
    }

    public double getTAS() {
        return tas;
    }

    public void setCategory(int val) {
        if (category != val) {
            category = val;
            updated = true;
        }
    }

    public int getCategory() {
        return category;
    }

    /**
     * Method to return the time this target was last updated
     *
     * @return a long representing the time in milliseconds
     */
    public long getUpdatedTime() {
        return updatedTime;
    }

    /**
     * Method to store the time this target was last updated
     *
     * @param time a long Representing the time in milliseconds
     */
    public void setUpdatedTime(long time) {
        updatedTime = time;
    }

    public void setUpdated(boolean val) {
        updated = val;
    }

    public boolean getUpdated() {
        return updated;
    }

    public long getUpdatedPosTime() {
        return updatedPosTime;
    }

    /**
     * Method to set the callsign
     *
     * <p>
     * Don't change the callsign to blank if it was a good value
     *
     * @param val a string representing the target callsign
     */
    public void setCallsign(String val) {
        if (!callsign.equals(val)) {
            if (!val.equals("")) {
                callsign = val;
                updated = true;
            }
        }
    }

    public String getCallsign() {
        return callsign;
    }

    public int getMode() {
        return mode;
    }

    /**
     * Method to set the 4-digit octal Mode-3A squawk
     *
     * @param val a String Representing the targets Mode-3A 4-digit octal code
     */
    public void setSquawk(String val) {
        if (!squawk.equals(val)) {
            if (!val.equals("0000")) {      // don't switch from a good code to a 0 code
                squawk = val;
                updated = true;
            }
        }
    }

    /**
     * Method to return the 4-digit octal Mode-3A squawk
     *
     * @return a String Representing the target Mode-3A 4-digit octal code
     */
    public String getSquawk() {
        return squawk;
    }

    /**
     * Method to set the DF00 target altitude in feet
     *
     * @param val an integer representing the target altitude in feet
     */
    public void setAltitudeDF00(int val) {
        altitudeDF00 = val;
    }

    /**
     * Method to set the DF04 target altitude in feet
     *
     * @param val an integer representing the target altitude in feet
     */
    public void setAltitudeDF04(int val) {
        altitudeDF04 = val;
    }

    /**
     * Method to set the DF16 target altitude in feet
     *
     * @param val an integer representing the target altitude in feet
     */
    public void setAltitudeDF16(int val) {
        altitudeDF16 = val;
    }

    /**
     * Method to set the DF17 target altitude in feet
     *
     * @param val an integer representing the target altitude in feet
     */
    public void setAltitudeDF17(int val) {
        altitudeDF17 = val;
    }

    /**
     * Method to set the DF18 target altitude in feet
     *
     * @param val an integer representing the target altitude in feet
     */
    public void setAltitudeDF18(int val) {
        altitudeDF18 = val;
    }
    
    /**
     * Method to set the DF20 target altitude in feet
     *
     * @param val an integer representing the target altitude in feet
     */
    public void setAltitudeDF20(int val) {
        altitudeDF20 = val;
    }

    /**
     * Method to return the target altitude in feet
     *
     * @return an integer representing the target altitude in feet
     */
    public int getAltitude() {

        /*
         * Return the altitude in this order: DF04, DF00/DF16 (TCAS),
         * DF17/DF18 (ADS-B)
         * 
         * Note: DF20 doesn't change often enough to display it
         * 
         * Note: TCAS DF00 and Mode-S DF04 are short packets, and more apt to be
         * decoded than the long packets
         * 
         * Note: It's a toss really, as I see ADS-B and TCAS leading/lagging each other
         * on climbs and descents.  Both are CRC checked.
         */
        if (altitudeDF04 != 0) {
            return altitudeDF04;
        } else if (altitudeDF00 != 0) {
            return altitudeDF00;
        } else if (altitudeDF16 != 0) {
            return altitudeDF16;
        } else if (altitudeDF17 != 0) {
            return altitudeDF17;
        } else if (altitudeDF18 != 0) {
            return altitudeDF18;
        }
        
        // punt
        return 0;
    }

    public int getVerticalRate() {
        return verticalRate;
    }

    public void setVerticalRate(int val) {
        if (verticalRate != val) {
            verticalRate = val;
            updated = true;
        }
    }

    public boolean getAlert() {
        return alert;
    }

    public void setAlert(boolean val) {
        if (alert != val) {
            if (val == true) {
                mode = TRACK_MODE_IDENT;
            } else {
                mode = TRACK_MODE_NORMAL;
            }

            alert = val;
            updated = true;
        }
    }

    public boolean getEmergency() {
        return emergency;
    }

    public void setEmergency(boolean val) {
        if (emergency != val) {
            if (val == true) {
                mode = TRACK_MODE_IDENT;
            } else {
                mode = TRACK_MODE_NORMAL;
            }

            emergency = val;
            updated = true;
        }
    }

    public boolean getSPI() {
        return spi;
    }

    public void setSPI(boolean val) {
        if (spi != val) {
            if (val == true) {
                mode = TRACK_MODE_IDENT;
            } else {
                mode = TRACK_MODE_NORMAL;
            }

            spi = val;
            updated = true;
        }
    }

    public void setIsOnGround(boolean val) {
        if (isOnGround != val) {
            if (val == true) {
                if (getAltitude() > 10000) {        // I just pulled this number out of a hat
                    // This is suspicious
                    isOnGround = false;
                    mode = TRACK_MODE_NORMAL;
                    updated = true;

                    return;
                } else {
                    mode = TRACK_MODE_STANDBY;
                }
            } else {
                mode = TRACK_MODE_NORMAL;
            }

            isOnGround = val;
            updated = true;
        }
    }

    public boolean getIsOnGround() {
        return isOnGround;
    }

    public void setGroundSpeed(double val) {
        if (groundSpeed != val) {
            groundSpeed = val;
            updated = true;
        }
    }

    public double getGroundSpeed() {
        return groundSpeed;
    }

    public void setTrueHeading(double val) {
        if (trueHeading != val) {
            trueHeading = val;
            updated = true;
        }
    }

    public double getTrueHeading() {
        return trueHeading;
    }

    public void setPosition(LatLon latlon, int mode, long time) {
        /*
         * Don't update with the same position
         */

        if ((Double.compare(longitude, latlon.getLon()) != 0) &&
                Double.compare(latitude, latlon.getLat()) != 0) {
            /*
             * If a good position is followed by a 0.0 then keep the old
             * position
             */
            if ((Double.compare(latlon.getLat(), 0.0) != 0) &&
                    Double.compare(latlon.getLon(), 0.0) != 0) {
                longitude = latlon.getLon();
                latitude = latlon.getLat();
                positionMode = mode;
                updatedPosTime = time;
                updated = true;
            }
        }
    }

    public LatLon getPosition() {
        return new LatLon(latitude, longitude);
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
    
    public int getPositionMode() {
        return positionMode;
    }
}
