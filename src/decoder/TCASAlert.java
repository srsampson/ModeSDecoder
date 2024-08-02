/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * Comm-B Data Selector (BDS) field equal to 0x30
 *
 * The BDS register 3,0 is used to send to the ground any
 * TCAS advisories that are threats to this aircraft.
 *
 * I mostly see these coming from DF20 and DF21
 */
public final class TCASAlert {

    private Altitude alt;
    //
    private int threatIdentityData26;
    private int threatTypeData30;
    //
    private long detectTime;
    /*
     * DF Source of data
     */
    private int dfSource;
    /*
     * Threat Type Indicator (TTI)
     */
    private int tti;
    /*
     * Threat ICAO ID
     *
     * This ID is only filled if TTI = 1
     */
    private String threatIcaoID;
    /*
     * Active Resolution Advisory (RA) register (ARA)
     */
    private int ara6;
    /*
     * Resolution Advisory Active (RAC)
     */
    private int rac4;
    /*
     * Threat Range
     */
    private float threatRange;
    /*
     * Threat Bearing
     */
    private float threatBearing;
    /*
     * Threat Altitude
     */
    private int threatAltitude;
    /*
     * Relative Altitude
     */
    private int threatRelativeAltitude;
    /*
     * Single Threat Indicator
     */
    private boolean singleRA;
    /*
     * Multiple Threat Indicator (MTI)
     */
    private boolean multipleRA;
    /*
     * Active RA Indicator
     */
    boolean activeRA;
    /*
     * Threat Terminated
     */
    private boolean threatTerminated;

    public TCASAlert(long mv56, int df5, long time, int trackAltitude) {
        tti = 0;
        ara6 = 0;
        rac4 = 0;
        threatIcaoID = "";
        threatRange = -999.0f;
        threatBearing = -999.0f;
        threatAltitude = -9999;
        threatRelativeAltitude = -9999;
        detectTime = time;

        dfSource = df5;

        alt = new Altitude();

        // These fit into an Integer in Java
        threatIdentityData26 = (int)(mv56 & 0x3FFFFFFL);    // 26 bits masked off
        threatTypeData30 = (int)(mv56 >>> 26);              // 30 bits left over

        /*
         * TTI Bits:
         * 
         * 00 No identity data in threat identity data
         * 01 Threat identity data contains a Mode~S transponder address
         * 10 Threat identity data contains altitude, range, and bearing
         * 11 Not assigned
         */
        tti = (threatTypeData30 & 0x3);                               // bit 29,30 Threat Type Indicator
        multipleRA = (((threatTypeData30 >>> 2) & 0x1) == 1);         // bit 28
        threatTerminated = (((threatTypeData30 >>> 3) & 0x1) == 1);   // bit 27

        singleRA = (((threatTypeData30 >>> 21) & 0x1) == 1);          // bit 9


        /*
         * Note: singleRA and multipleRA false, means this is only a threat
         * detected, but not to do anything (don't turn, don't climb, don't
         * descend).
         */
        activeRA = ((singleRA == true) || (multipleRA == true));

        switch (tti) {
            case 1:     // ICAO ID bits 31 - 54
                threatIcaoID = Integer.toString((threatIdentityData26 >>> 2), 16).toUpperCase();
                break;
            case 2:     // altitude, range, bearing bits 31 - 56
                /*
                 * Altitude Encoding
                 * Bit              31 32 33 34 35 36 37 38 39 40 41 42 43
                 * Mode C code bit  C1 A1 C2 A2 C4 A4  0 B1 D1 B2 D2 B4 D4
                 */        
                int tmp13 = (threatIdentityData26 >>> 13);   // Mode-C Altitude
                int tmp5 = ((tmp13 & 0x2F) >>> 5) << 4; // Move bit 38 to 39, no D1
                int ac11 = ((tmp13 >> 7) << 5) | (tmp5 & 0x1F); // drop bit 37, 39
                
                threatAltitude = alt.computeAltitude(ac11);

                if ((threatAltitude != -9999) && (trackAltitude != -9999)) {
                    threatRelativeAltitude = trackAltitude - threatAltitude;
                } else {
                    threatRelativeAltitude = -9999;
                }

                int range = ((threatIdentityData26 >>> 6) & 0x7F); // 7 bits 44-50

                if (range > 0 && range < 128) {
                    threatRange = switch (range) {
                        case 127 -> // greater than 12.55 nmi
                            13.0f;
                        case 1 ->   // inside .05 nmi
                            0.05f;
                        case 0 ->
                            0.0f;
                        default ->  // 0.1 to 12.5 nmi
                            (float) (range - 1) / 10.0f;
                    };
                } // else threatRange = -999.0f;

                int bearing = (threatIdentityData26 & 0x3F); // 6 bits 51-56

                if (bearing > 0 && bearing < 61) {
                    if (bearing == 1) {
                        threatBearing = 0.0f;
                    } else {
                        threatBearing = (float) bearing * 6.0f;
                    }
                } // else threatBearing = -999.0f;
            default:
        }

        /*
         * Decode the ARA 14 bits (6 usable, rest are for ACAS III)
         * Bit 9 is used to signify singleRA
         */
        ara6 = ((threatIdentityData26 >>> 8) & 0x3F); // 6 usable bits

        /*
         * Decode the RAC 4 Bits
         */
        rac4 = ((threatIdentityData26 >>> 4) & 0xF);
    }

    public int getDFSource() {
        return dfSource;
    }

    /**
     * Method to return the time this threat was detected in Zulu time (UTC)
     *
     * @return a long representing time in zulu (UTC) threat detected
     */
    public long getDetectTime() {
        return detectTime;
    }

    /**
     * Method to return the 6 bits of the Active Resolution Advisory (ARA) field
     *
     * <p>
     * These bits have two meanings based on the status of singleRA boolean and
     * the multipleRA boolean.
     *
     * <p>
     * Note: the 6 bits are numbered 0 for LSB, and 5 for MSB
     *
     * <p>
     * If singleRA is true, and multipleRA is false:
     *
     * <p>
     * Bit 5 = 0 RA is preventive<br>
     * Bit 5 = 1 RA is corrective
     *
     * <p>
     * Bit 4 = 0 upward sense RA<br>
     * Bit 4 = 1 downward sense RA
     *
     * <p>
     * Bit 3 = 0 not increased rate<br>
     * Bit 3 = 1 increased rate
     *
     * <p>
     * Bit 2 = 0 RA is not a sense reversal<br>
     * Bit 2 = 1 RA is a sense reversal
     *
     * <p>
     * Bit 1 = 0 not altitude crossing<br>
     * Bit 1 = 1 altitude crossing
     *
     * <p>
     * Bit 0 = 0 RA is vertical speed limit<br>
     * Bit 0 = 1 RA is positive
     *
     * <p>
     * If singleRA is false and multipleRA is true
     *
     * <p>
     * Bit 5 = 0 RA does not require upward correction<br>
     * Bit 5 = 1 RA requires upward correction
     *
     * <p>
     * Bit 4 = 0 RA does not require positive climb<br>
     * Bit 4 = 1 RA requires positive climb
     *
     * <p>
     * Bit 3 = 0 RA does not require downward correction<br>
     * Bit 3 = 1 RA requires downward correction
     *
     * <p>
     * Bit 2 = 0 RA does not require positive descent<br>
     * Bit 2 = 1 RA requires positive descent
     *
     * <p>
     * Bit 1 = 0 RA does not require altitude crossing<br>
     * Bit 1 = 1 RA requires altitude crossing
     *
     * <p>
     * Bit 0 = 0 RA is not a sense reversal<br>
     * Bit 0 = 1 RA is a sense reversal
     *
     * @return an integer representing the 6 bits of the Active Resolution
     * Advisory (ARA) field
     */
    public int getARABits() {
        return ara6;
    }

    /**
     * Method to return the four Resolution Advisory Active (RAC) values
     *
     * <p>
     * The RAC bits are commands from the other threat
     *
     * <p>
     * Note: the 4 bits are numbered 0 for LSB, and 3 for MSB
     *
     * <p>
     * Bit 3 = 1 Do not pass below<br>
     * Bit 2 = 1 Do not pass above<br>
     * Bit 1 = 1 Do not turn left<br>
     * Bit 0 = 1 Do not turn right
     *
     * @return an integer representing the four Resolution Advisory Active (RAC)
     * values
     */
    public int getRACBits() {
        return rac4;
    }

    public boolean getActiveRA() {
        return activeRA;
    }

    public boolean getSingleRA() {
        return singleRA;
    }

    public boolean getMultipleRA() {
        return multipleRA;
    }
    
    /**
     * Method to return the Threat Type Indicator (TTI)
     *
     * <p>
     * The Threat Type is specified by the return values:
     *
     * <p>
     * 0 = No identity data in Threat Identity Data (TID) fields<br>
     * 1 = TID contains Mode S ICAO address<br>
     * 2 = TID contains altitude, range, and bearing<br>
     * 3 = Not assigned
     *
     * @return an integer representing the Threat Type Indicator value
     */
    public int getThreatTypeIndicator() {
        return tti;
    }

    /**
     * Method to return the status of the Threat
     *
     * <p>
     * This is the Resolution Advisory Terminated bit (RAT)
     *
     * <p>
     * true = Threat is no longer valid<br>
     * false = Threat is still active and valid
     *
     * @return a boolean representing the threat alert is terminated
     */
    public boolean getThreatTerminated() {
        return threatTerminated;
    }

    /**
     * Method to return the Mode-S ICAO ID of a threat
     *
     * <p>
     * The 24-bit ICAO ID is returned as six hexadecimal digits. The string will
     * be empty if the threat type indicator (TTI) is for altitude, bearing and
     * range.
     *
     * @return a string representing the ICAO ID in hexadecimal
     */
    public String getThreatICAOID() {
        return threatIcaoID;
    }

    /**
     * Method to return the range in nautical miles to a threat track
     *
     * <p>
     * The threat range is converted to floating point (.05 minimum to 13.0
     * nautical miles maximum), from the internal representation:
     *
     * <p>
     * 0 = No range available -999.0 <br>
     * 1 = Range is inside .05 nmi<br>
     * 2...126 = .1 to 12.5 nmi (n - 1) / 10 nmi<br>
     * 127 = Range greater than 12.55 nmi
     * 
     * @return a float representing the range to a threat in nautical miles
     */
    public float getThreatRange() {
        return threatRange;
    }

    /**
     * Method to return the threat bearing in degrees relative to the tracks
     * nose.
     *
     * <p>
     * Examples: 360 degrees is 12 O'clock on the nose<br>
     * 180 degrees is 6 O'clock on the tail.
     *
     * <p>
     * The threat bearing is converted to floating point (6.0 to 360.0 degrees)
     * from the internal representation:
     *
     * <p>
     * 0 = No bearing available -999.0 <br>
     * 1...60 = 0.0 to 360.0 degrees (n * 6.0 degrees)<br>
     * 61...63 = Not used
     *
     * @return a float representing the relative bearing to a threat
     */
    public float getThreatBearing() {
        return threatBearing;
    }

    /**
     * Method to return the threat altitude in feet
     * 
     * -9999 for no altitude received, or null
     *
     * @return an integer representing the threat altitude
     */
    public int getThreatAltitude() {
        return threatAltitude;
    }

    /**
     * Method to return the relative altitude of the threat in feet
     *
     * <p>
     * The threat altitude returned in the TCAS report is subtracted from the
     * tracks reporting altitude, resulting in a +/- relative difference.
     *
     * <p>
     * A negative value indicates the threat is below<br>
     * A positive value indicates the threat is above
     * -9999 for no altitude received, or null
     * 
     * @return an integer representing relative altitude in feet to the threat
     */
    public int getThreatRelativeAltitude() {
        return threatRelativeAltitude;
    }
    
    public String getThreatIdentityData() {
        return Integer.toString(threatIdentityData26, 16).toUpperCase();
    }
    
    public String getThreatTypeData() {
        return Integer.toString(threatTypeData30, 16).toUpperCase();
    }
}
