/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the vehicle track object
 */
public final class Track implements IConstants {

    private String acid;            // Aircraft ID
    private String registration;    // N-Number if USA registered
    private int mode;               // Track mode
    private int positionMode;
    private int trackQuality;       // 0 - 9 quality value (9 means Firm)
    private int verticalRate;       // fps
    private int verticalTrend;      // -1 = down, 0 = level, 1 = up
    private final int[] trend = new int[10];
    private static int trend_el = 0;
    private float groundSpeed;      // kts
    private float groundTrack;      // deg
    private float groundSpeedComputed;
    private float groundTrackComputed;
    private float latitude;        // aircraft position latitude (- is south)
    private float longitude;       // aircraft position longitude (- is west)
    private float ias;
    private float tas;
    private float heading;
    private String callsign;        // 8 character string
    private String squawk;          // 4 digit octal code
    //
    private int version;
    private int category;
    private int altitudeDF00;
    private int altitudeDF04;
    private int altitudeDF16;
    private int altitudeDF17;
    private int altitudeDF18;
    private int altitudeDF20;
    private int radarIID;                       // IID is a II code (00 - 15)
    private boolean si;                         // IID is a SI code (00 - 63)
    //
    private boolean alert;          // octal code changed bit
    private boolean emergency;      // emergency bit
    private boolean spi;            // ident bit
    private boolean isOnGround;     // aircraft squat switch activated
    private boolean isVirtOnGround; // Virtual onGround for MMS2
    private boolean hijack;
    private boolean comm_out;
    //
    private boolean hadAlert;
    private boolean hadEmergency;
    private boolean hadSPI;
    //
    private long updatedTime;        // zulu time object was updated
    private long updatedPositionTime;// zulu time object lat/lon position was updated
    private boolean updated;        // set on update, cleared on sent
    private boolean updatePosition;
    //
    private boolean isLocal;            // Target is from one of our radars/not remote
    private boolean isRelayed;          // Target has been relayed by a ground site (TIS-B)

    /**
     * A track is the complete data structure of the Aircraft ID (acid).
     * It takes several different target reports to gather all the data,
     * but this is where it is finally stored.
     *
     * @param ac a String representing the ICAO ID of the vehicle
     * @param relayed a boolean representing a target that is relayed (TIS-B)
     */
    public Track(String ac, boolean relayed) {
        isLocal = true;
        isRelayed = relayed;
        acid = ac;
        registration = "";
        version = 0;
        category = 0;
        mode = TRACK_MODE_NORMAL;
        positionMode = POSITION_MODE_UNKNOWN;
        groundSpeed = -999.0f;
        groundTrack = -999.0f;
        heading = -999.0f;
        groundSpeedComputed = -999.0f;
        groundTrackComputed = -999.0f;
        latitude = -999.0f;
        longitude = -999.0f;
        ias = -999.0f;
        tas = -999.0f;
        radarIID = -99;
        verticalRate = -9999;
        verticalTrend = 0;
        altitudeDF00 = -9999;
        altitudeDF04 = -9999;
        altitudeDF16 = -9999;
        altitudeDF17 = -9999;
        altitudeDF18 = -9999;
        altitudeDF20 = -9999;
        squawk = "";
        callsign = "";
        trackQuality = 0;
        updatedPositionTime = 0L;
        updatedTime = 0L;
        alert = emergency = spi = hadAlert
                = hadEmergency = hadSPI = si = hijack = comm_out = false;
        updated = updatePosition = false;
        isOnGround = isVirtOnGround = false;
    }

    /**
     * Method to increment track quality
     */
    public void incrementTrackQuality() {
        if (trackQuality < 9) {
            trackQuality++;
            updated = true;
        }
    }

    /**
     * Method to decrement track quality
     */
    public void decrementTrackQuality() {
        if (trackQuality > 0) {
            trackQuality--;
            updated = true;
        }
    }

    /**
     * Method to set the track quality
     *
     * @param val an integer Representing the track quality [0...9]
     */
    public void setTrackQuality(int val) {
        if (trackQuality != val) {
            trackQuality = val;
            updated = true;
        }
    }

    /**
     * Method to return track quality
     *
     * @return an integer representing the track quality [0...9]
     */
    public int getTrackQuality() {
        return trackQuality;
    }

    /**
     * Method to check if the track has been updated
     *
     * @return boolean which signals if the track has been updated
     */
    public boolean getUpdated() {
        return updated;
    }

    /**
     * Method to flag a track as being updated
     *
     * @param val a boolean which signals the track has been updated
     */
    public void setUpdated(boolean val) {
        updated = val;
    }

    /**
     * Method to check if the track position has been updated
     *
     * @return boolean which signals if the track position has been updated
     */
    public boolean getUpdatePosition() {
        return updatePosition;
    }

    /**
     * Method to flag a track position as being updated or not updated
     *
     * @param val a boolean to set or reset the track position updated status
     */
    public void setUpdatePosition(boolean val) {
        updatePosition = val;
    }

    /**
     * Method to return the Aircraft Mode-S Hex ID
     *
     * @return a string Representing the track Mode-S Hex ID
     */
    public String getAircraftID() {
        return acid;
    }

    /**
     * Method to set the Aircraft Mode-S Hex ID
     *
     * @param val a string Representing the track Mode-S Hex ID
     */
    public void setAircraftID(String val) {
        acid = val;
    }

    /**
     * Method to return the Aircraft N-Number Registration
     *
     * @return a string Representing the track registration
     */
    public String getRegistration() {
        return registration;
    }

    /**
     * Method to set the Aircraft N-Number Registration
     *
     * @param val a string Representing the track registration
     */
    public void setRegistration(String val) {
        registration = val;
    }

    /**
     * Method to return the tracks updated position time in milliseconds
     *
     * @return a long Representing the track updated position time in
     * milliseconds
     */
    public long getUpdatedPositionTime() {
        return updatedPositionTime;
    }

    /**
     * Method to return the tracks updated time in milliseconds
     *
     * @return a long Representing the track updated time in milliseconds
     */
    public long getUpdatedTime() {
        return updatedTime;
    }

    /**
     * Method to set the track updated time in milliseconds
     *
     * @param val a long Representing the track updated time in milliseconds
     */
    public void setUpdatedTime(long val) {
        updatedTime = val;
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
        if (si != val) {
            si = val;
            updated = true;
        }
    }

    public boolean getSI() {
        return si;
    }

    /**
     * Method to return the track vertical rate in feet per second The
     * resolution is +/- 64 fps, with descent being negative
     *
     * @return an integer Representing the track climb or descent rate
     */
    public int getVerticalRate() {
        return verticalRate;
    }

    /**
     * Method to set the track vertical rate in feet per second The resolution
     * is +/- 64 fps, with descent being negative
     *
     * @param val an integer Representing the track climb or descent rate
     */
    public void setVerticalRate(int val) {
        if (verticalRate != val) {
            verticalRate = val;
            updated = true;
        }
        
        int vt = 0;

        if (val > 192) {
            trend[trend_el] = 1;
        } else if (val < -192) {
            trend[trend_el] = -1;
        } else {
            trend[trend_el] = 0;
        }

        trend_el = (trend_el + 1) % 10;

        for (int i = 0; i < 10; i++) {
            vt += trend[i];
        }

        if (vt > 0) {
            verticalTrend = 1;
        } else if (vt < 0) {
            verticalTrend = -1;
        } else {
            verticalTrend = 0;
        }
    }

    public int getVerticalTrend() {
        return verticalTrend;
    }

    public void setGroundSpeed(float val) {
        if (groundSpeed != val) {
            groundSpeed = val;
            updated = true;
        }
    }
    
    /**
     * Method used to return the target ground speed in knots
     *
     * @return target groundspeed in knots
     */
    public float getGroundSpeed() {
        return groundSpeed;
    }

    /**
     * Method used to return the target ground track in degrees true north.
     *
     * @return target ground track in degrees true north
     */
    public float getGroundTrack() {
        return groundTrack;
    }

    public void setGroundTrack(float val) {
        if (groundTrack != val) {
            groundTrack = val;
            updated = true;
        }
    }

    /**
     * Method used to return the target computed ground speed in knots
     *
     * @return target groundspeed in knots
     */
    public float getComputedGroundSpeed() {
        return groundSpeedComputed;
    }

    /**
     * Method used to return the target computed ground track in degrees true
     * north.
     *
     * @return target ground track in degrees true north
     */
    public float getComputedGroundTrack() {
        return groundTrackComputed;
    }

    public void setComputedGroundSpeed(float val) {
        groundSpeedComputed = val;
    }

    public void setComputedGroundTrack(float val) {
        groundTrackComputed = val;
    }
    
    /**
     * Method to set all three velocities
     *
     * <p>
     * Vertical Rate is set to zero for low values, as the aircraft tend to
     * bobble up and down in turbulence, which generates network traffic. The
     * Vertical Rate is negative for descent.
     *
     * @param val1 Ground Track in degrees
     * @param val2 Ground Speed in knots
     * @param val3 Vertical Rate in feet per second
     */
    public void setVelocityData(float val1, float val2, int val3) {
        boolean changed = false;

        if (groundTrack != val1) {
            groundTrack = val1;
            changed = true;
        }

        if (groundSpeed != val2) {
            groundSpeed = val2;
            changed = true;
        }

        if (verticalRate != val3) {
            setVerticalRate(val3);
            changed = true;
        }

        if (changed == true) {
            updated = true;
        }
    }

    /**
     * Method used to set the DF00 altitude in feet MSL (29.92) The virtual
     * onground status is also set if altitude reads 0 feet.
     *
     * @param val an integer Representing altitude in feet MSL or -9999 for null
     */
    public void setAltitudeDF00(int val) {
        if (altitudeDF00 != val) {
            altitudeDF00 = val;
            isVirtOnGround = (val == 0);
            updated = true;
        }
    }

    public int getAltitudeDF00() {
        return altitudeDF00;
    }

    /**
     * Method used to set the DF04 altitude in feet MSL (29.92) The virtual
     * onground status is also set if altitude reads 0 feet.
     *
     * @param val an integer Representing altitude in feet MSL or -9999 for null
     */
    public void setAltitudeDF04(int val) {
        if (altitudeDF04 != val) {
            altitudeDF04 = val;
            isVirtOnGround = (val == 0);
            updated = true;
        }
    }

    public int getAltitudeDF04() {
        return altitudeDF04;
    }
    
    /**
     * Method used to set the DF16 altitude in feet MSL (29.92) The virtual
     * onground status is also set if altitude reads 0 feet.
     *
     * @param val an integer Representing altitude in feet MSL or -9999 for null
     */
    public void setAltitudeDF16(int val) {
        if (altitudeDF16 != val) {
            altitudeDF16 = val;
            isVirtOnGround = (val == 0);
            updated = true;
        }
    }

    public int getAltitudeDF16() {
        return altitudeDF16;
    }
    
    /**
     * Method used to set the DF17 altitude in feet MSL (29.92) The virtual
     * onground status is also set if altitude reads 0 feet.
     *
     * @param val an integer Representing altitude in feet MSL or -9999 for null
     */
    public void setAltitudeDF17(int val) {
        if (altitudeDF17 != val) {
            altitudeDF17 = val;
            isVirtOnGround = (val == 0);
            updated = true;
        }
    }

    public int getAltitudeDF17() {
        return altitudeDF17;
    }
    
    /**
     * Method used to set the DF18 altitude in feet MSL (29.92) The virtual
     * onground status is also set if altitude reads 0 feet.
     *
     * @param val an integer Representing altitude in feet MSL or -9999 for null
     */
    public void setAltitudeDF18(int val) {
        if (altitudeDF18 != val) {
            altitudeDF18 = val;
            isVirtOnGround = (val == 0);
            updated = true;
        }
    }

    public int getAltitudeDF18() {
        return altitudeDF18;
    }
    
    /**
     * Method used to set the DF20 altitude in feet MSL (29.92) The virtual
     * onground status is also set if altitude reads 0 feet.
     *
     * @param val an integer Representing altitude in feet MSL or -9999 for null
     */
    public void setAltitudeDF20(int val) {
        if (altitudeDF20 != val) {
            altitudeDF20 = val;
            isVirtOnGround = (val == 0);
            updated = true;
        }
    }

    public int getAltitudeDF20() {
        return altitudeDF20;
    }
    
    /**
     * Method used to return the target altitude in feet MSL (29.92)
     *
     * @return an integer Representing the target altitude in feet MSL
     */
    public int getAltitude() {

        /*
         * Return the altitude in this order: DF00, DF17 (ADS-B),
         * DF04, DF16 (TCAS), DF18 (TIS-B)
         * 
         * Note: DF20 doesn't change often enough to display it
         * 
         * Note: TCAS DF00 and Mode-S DF04 are short packets, and more apt to be
         * decoded than the long packets
         * 
         * Note: It's a toss really, as I see ADS-B and TCAS leading/lagging each other
         * on climbs and descents.  Both are CRC checked.
         */
        if (altitudeDF00 != -9999) {
            return altitudeDF00;
        } else if (altitudeDF17 != -9999) {
            return altitudeDF17;
        } else if (altitudeDF04 != -9999) {
            return altitudeDF04;
        } else if (altitudeDF16 != -9999) {
            return altitudeDF16;
        } else if (altitudeDF18 != -9999) {
            return altitudeDF18;
        }
        
        // punt
        return -9999;
    }

    public LatLon getPosition() {
        return new LatLon(latitude, longitude);
    }
    
    public int getPositionMode() {
        return positionMode;
    }
    
    /**
     * Method used to return the target latitude in degrees (south is negative)
     *
     * @return a float Representing the target latitude
     */
    public float getLatitude() {
        return latitude;
    }

    /**
     * Method used to return the target longitude in degrees (west is negative)
     *
     * @return a float Representing the target longitude
     */
    public float getLongitude() {
        return longitude;
    }

    /**
     * Method used to set the target 2D position (latitude, longitude) (south
     * and west are negative)
     *
     * @param latlon an object Representing the target lat/lon
     * @param mode an int Representing the target mode
     * @param utc a long timestamp
     */
    public void setPosition(LatLon latlon, int mode, long utc) {
        /*
         * Don't update with the same position
         */

        if ((Float.compare(longitude, latlon.getLon()) != 0) &&
                Float.compare(latitude, latlon.getLat()) != 0) {
            /*
             * If a good position is followed by a 0.0 then keep the old
             * position
             */
            if ((Float.compare(latlon.getLat(), 0.0f) != 0) &&
                    Float.compare(latlon.getLon(), 0.0f) != 0) {
                longitude = latlon.getLon();
                latitude = latlon.getLat();
                
                positionMode = mode;
                incrementTrackQuality();
                updated = updatePosition = true;
                updatedPositionTime = utc;
            }
        }
    }

    /**
     * Method used to return the target callsign
     *
     * @return a string Representing the target callsign
     */
    public String getCallsign() {
        return callsign;
    }

    /**
     * Method used to set the target callsign
     * Don't change the callsign to blank if it was a good value
     * 
     * @param val a string Representing the target callsign
     */
    public void setCallsign(String val) {
        if (val.equals("") == false) {
            if (val.equals(callsign) == false) {
                callsign = val;
                updated = true;
            }
        }
    }

    /**
     * Method used to return the target octal 4-digit squawk
     *
     * @return a String Representing the target octal squawk
     */
    public String getSquawk() {
        return squawk;
    }

    /**
     * Method used to set the target octal 4-digit squawk
     *
     * @param val a String Representing the target octal squawk
     */
    public void setSquawk(String val) {
        if (val.equals("") == false) {
            if (val.equals(squawk) == false) {
                if (val.equals("0000") == false) {      // don't switch from a good to 0 code
                    squawk = val;
                    updated = true;
                    emergency = val.equals("7700");
                    hijack = val.equals("7500");
                    comm_out = val.equals("7600");
                }
            }
        }
    }

    /**
     * Method used to return the Emergency status
     *
     * @return a boolean Representing the Emergency status
     */
    public boolean getEmergency() {
        return emergency;
    }

    /**
     * Method used to return the SPI status
     *
     * @return a boolean Representing the SPI status
     */
    public boolean getSPI() {
        return spi;
    }

    /**
     * Method used to return the Hijack status
     *
     * @return a boolean Representing the Hijack status
     */
    public boolean getHijack() {
        return hijack;
    }

    public boolean getCommOut() {
        return comm_out;
    }

    public boolean getVirtualOnGround() {
        return isVirtOnGround;
    }

    /**
     * Method used to return the OnGround status
     *
     * @return a boolean Representing the OnGround status
     */
    public boolean getOnGround() {
        return isOnGround;
    }

    /**
     * Method to set the OnGround status
     *
     * @param val a boolean Representing the OnGround status
     */
    public void setOnGround(boolean val) {
        if (isOnGround != val) {
            if (val == true) {
                if (getAltitude() > 10000) {        // I just pulled this number out of a hat
                    // This is suspicious
                    isOnGround = false;
                    mode = TRACK_MODE_NORMAL;
                } else {
                    isOnGround = true;
                    mode = TRACK_MODE_STANDBY;
                }
            } else {
                mode = TRACK_MODE_NORMAL;
                isOnGround = val;
            }
            
            updated = true;
        }
    }

    /**
     * Method used to return the Alert status The Alert signals the 4-digit
     * octal squawk has changed
     *
     * @return a boolean Representing the Alert status
     */
    public boolean getAlert() {
        return alert;
    }

    /**
     * Method to set all the boolean bits
     *
     * <p>
     * The Alert bit is set if the 4-digit octal code is changed. The Emergency
     * bit is set if the pilot puts in the emergency code The SPI bit is set if
     * the pilots presses the Ident button.
     *
     * @param val1 a boolean Representing the status of the Alert
     * @param val2 a boolean Representing the status of the Emergency
     * @param val3 a boolean Representing the status of the SPI
     */
    public void setAlert(boolean val1, boolean val2, boolean val3) {
        boolean changed = false;

        if (alert != val1) {
            if (val1 == true) {
                mode = TRACK_MODE_IDENT;
            } else {
                mode = TRACK_MODE_NORMAL;
            }

            alert = val1;

            if (alert == true) {
                hadAlert = true;
            }

            changed = true;
        }

        if (emergency != val2) {
            if (val2 == true) {
                mode = TRACK_MODE_IDENT;
            } else {
                mode = TRACK_MODE_NORMAL;
            }

            emergency = val2;

            if (emergency == true) {
                hadEmergency = true;
            }

            changed = true;
        }

        if (spi != val3) {
            if (val3 == true) {
                mode = TRACK_MODE_IDENT;
            } else {
                mode = TRACK_MODE_NORMAL;
            }

            spi = val3;

            if (spi == true) {
                hadSPI = true;
            }

            changed = true;
        }

        if (changed == true) {
            updated = true;
        }
    }

    public boolean getHadAlert() {
        return hadAlert;
    }

    public boolean getHadEmergency() {
        return hadEmergency;
    }

    public boolean getHadSPI() {
        return hadSPI;
    }

    public int getMode() {
        return mode;
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
        if (version != val) {
            version = val;
            updated = true;
        }
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

    public void setIAS(float val) {
        if (ias != val) {
            ias = val;
            updated = true;
        }
    }

    public float getIAS() {
        return ias;
    }

    public void setTAS(float val) {
        if (tas != val) {
            tas = val;
            updated = true;
        }
    }

    public float getTAS() {
        return tas;
    }

    /*
     * This is some ADS-B bo-jive
     */
    public void setHeading(float val) {
        if (heading != val) {
            heading = val;
            updated = true;
        }
    }

    public float getHeading() {
        return heading;
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
}