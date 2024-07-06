/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import java.util.List;
import parser.BufferDataBlocks;
import parser.Config;
import parser.DataBlock;

/**
 * A class to decode the received packets and put them into targets
 *
 * This is the main entry point for applications
 */
public final class DataBlockParser extends Thread {

    private final Thread process;
    private final BufferDataBlocks buf;
    private final ProcessTargets procTarget;
    private final LatLon receiverLatLon;
    private final PositionManager pm;
    private DataBlock dbk;
    private static boolean EOF;
    private String data;
    private String acid;
    private String callsign;
    private String modeA;
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
    private double groundSpeed;
    private double trueHeading;
    private double magneticHeading;
    private double airspeed;
    //
    private int vSpeed;
    private int category;
    private int subtype3;
    private int altitude;
    private int radarIID;

    public DataBlockParser(LatLon ll, BufferDataBlocks bd) {
        this.buf = bd;
        EOF = false;

        receiverLatLon = ll;
        procTarget = new ProcessTargets();
        pm = new PositionManager(receiverLatLon, this, procTarget);

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
        pm.close();
        procTarget.close();
    }

    public int getTargetQueueSize() {
        return procTarget.getQueueSize();
    }

    public List<Target> getTargets() throws Exception {
        try {
            return procTarget.getAllUpdatedTargets();
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    private void updateTargetMagneticHeadingIAS(String hexid, double head, double ias, int vvel, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setHeading(head);
            tgt.setIAS(ias);
            tgt.setVerticalRate(vvel);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetMagneticHeadingTAS(String hexid, double head, double tas, int vvel, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setHeading(head);
            tgt.setTAS(tas);
            tgt.setVerticalRate(vvel);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetCallsign(String hexid, String cs, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setCallsign(cs);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetCallsign(String hexid, String cs, int category, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setCallsign(cs);
            tgt.setCategory(category);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF00(String hexid, int alt, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setAltitudeDF00(alt);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF04(String hexid, int alt, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setAltitudeDF04(alt);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF16(String hexid, int alt, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setAltitudeDF16(alt);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF17(String hexid, int alt, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setAltitudeDF17(alt);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF18(String hexid, int alt, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setAltitudeDF18(alt);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetAltitudeDF20(String hexid, int alt, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setAltitudeDF20(alt);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetGroundSpeedTrueHeading(String hexid, double gs, double th, int vs, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setGroundSpeed(gs);
            tgt.setTrueHeading(th);
            tgt.setVerticalRate(vs);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetRadarID(String hexid, int iid, boolean si, long time) {
        try {
            Target trk = procTarget.getTarget(hexid);
            trk.setRadarIID(iid);
            trk.setSI(si);
            trk.setUpdatedTime(time);
            procTarget.addTarget(hexid, trk);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetSquawk(String hexid, String squawk, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setSquawk(squawk);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetBoolean(String hexid, boolean onground, boolean emergency, boolean alert, boolean spi, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setEmergency(emergency);
            tgt.setAlert(alert);
            tgt.setSPI(spi);
            tgt.setIsOnGround(onground);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetOnGround(String hexid, boolean onground, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setIsOnGround(onground);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    public void updateTargetLatLon(String hexid, LatLon latlon, int mode, long time) {
        try {
            Target tgt = procTarget.getTarget(hexid);
            tgt.setPosition(latlon, mode, time);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    public void createTargetLatLon(String hexid, boolean tis, LatLon latlon, int mode, long time) {
        try {
            Target tgt = new Target(hexid, tis);

            tgt.setPosition(latlon, mode, time);
            tgt.setUpdatedTime(time);
            procTarget.addTarget(hexid, tgt);
        } catch (NullPointerException np) {
            System.err.println(np);
        }
    }

    private void updateTargetTCASAlert(String hexid, long time, long data56) {
        try {
            Target tgt = procTarget.getTarget(hexid);

            if (procTarget.hasTarget(hexid)) {
                tgt.insertTCAS(data56, time);
                procTarget.addTarget(hexid, tgt);
            }
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
        while (EOF == false) {
            while (buf.getQueueSize() > 0) {
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
                            if (procTarget.hasTarget(acid)) {           // must be valid then
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
                            if (procTarget.hasTarget(acid)) {
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
                            if (procTarget.hasTarget(acid)) {
                                modeA = df05.getSquawk();
                                isOnGround = df05.getIsOnGround();
                                alert = df05.getIsAlert();
                                spi = df05.getIsSPI();
                                emergency = df05.getIsEmergency();

                                updateTargetSquawk(acid, modeA, detectTime);
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
                                if (!procTarget.hasTarget(acid)) {
                                    /*
                                     * New Target
                                     */
                                    procTarget.addTarget(acid, new Target(acid, false)); // false == not TIS
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
                            if (procTarget.hasTarget(acid)) {
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
                                        updateTargetTCASAlert(acid, detectTime, data56);
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

                        if (df17.isValid()) {
                            /*
                             * See if Target already exists
                             */
                            try {
                                if (!procTarget.hasTarget(acid)) {
                                    /*
                                     * New Target
                                     */
                                    procTarget.addTarget(acid, new Target(acid, false));    // false == not TIS
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

                        if (df18.isValid()) {
                            /*
                             * See if Target already exists
                             */
                            try {
                                if (!procTarget.hasTarget(acid)) {
                                    /*
                                     * New Target
                                     */
                                    procTarget.addTarget(acid, new Target(acid, true));    // true == TIS
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

                                        if (!(Double.compare(trueHeading, -1.0) == 0)) {
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
                            if (procTarget.hasTarget(acid)) {
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
                                        updateTargetTCASAlert(acid, detectTime, data56);
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
                            if (procTarget.hasTarget(acid)) {
                                modeA = df21.getSquawk();
                                isOnGround = df21.getIsOnGround();
                                emergency = df21.getIsEmergency();
                                alert = df21.getIsAlert();
                                spi = df21.getIsSPI();

                                updateTargetSquawk(acid, modeA, detectTime);
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
                                            updateTargetTCASAlert(acid, detectTime, data56);
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

            try {
                sleep(0, 100);
            } catch (InterruptedException z) {
            }
        }
    }
}