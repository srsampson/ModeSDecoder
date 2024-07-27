/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import parser.Config;
import parser.ZuluMillis;

/*
 * Pressure Altitude and Altitude Correction Class
 * 
 * Used for calculating track altitude correction below transition altitude.
 * The GUI can replace the altitude shown for each aircraft below the transition
 * altitude with the aircraft's sent-value minus the correction calculated here.
 */
public class PressureAltitude {

    private static final String NOAA = "https://tgftp.nws.noaa.gov";
    private static final String URL = "/data/observations/metar/decoded/";
    private static final double STANDARD = 29.920;
    private static final long RATE = 25L * 60L * 1000L; // 25 minutes
    //
    private URL noaa;
    private BufferedReader in;
    private final ZuluMillis zulu;
    //
    private float airportAltimeter;
    private int airportElevation;
    private int pressureAltitude;
    private int altitudeCorrection;
    private String observationUTCTime;     // HHmm
    private final String airportName;
    private long utcUpdateTime;
    private volatile boolean airportDataValid;
    //
    private final Timer timer1;
    private final TimerTask task1;
    private final Config config;

    public PressureAltitude(Config c) {
        zulu = new ZuluMillis();
        config = c;

        airportAltimeter = 0.0f;
        airportElevation = config.getStationAltitude();
        airportName = config.getStationAirport();
        utcUpdateTime = 0L;
        pressureAltitude = 0;
        altitudeCorrection = 0;
        observationUTCTime = "";
        airportDataValid = false;

        task1 = new MetarRefresh();
        timer1 = new Timer();
        timer1.scheduleAtFixedRate(task1, 0L, RATE);
    }

    public String getAirportName() {
        return airportName;
    }

    public int getPressureAltitude() {
        if (airportDataValid == true) {
            return pressureAltitude;
        } else {
            return 0;
        }
    }

    public int getAltitudeCorrection() {
        if (airportDataValid == true) {
            return altitudeCorrection;
        } else {
            return 0;
        }
    }

    public long getMetarUpdateTime() {
        return utcUpdateTime;
    }

    public String getObservationTime() {
        return observationUTCTime;
    }

    public int getAirportElevation() {
        return airportElevation;
    }

    public void setAirportElevation(int val) {
        airportElevation = val;
    }

    private class MetarRefresh extends TimerTask {

        @Override
        public void run() {
            boolean complete;
            String inputLine;

            try {
                noaa = new URI(NOAA + URL + airportName + ".TXT").toURL();
                in = new BufferedReader(new InputStreamReader(noaa.openStream()));
            } catch (IOException | URISyntaxException e1) {
                airportDataValid = false;
                
                try {
                    in.close();
                } catch (IOException e3) {
                }
                
                return; // network down
            }

            do {
                try {
                    inputLine = in.readLine();

                    if (inputLine.startsWith("ob:") == true) {
                        break;
                    }
                } catch (IOException e1) {
                    try {
                        in.close();
                    } catch (IOException e3) {
                    }

                    return;     // Network probably down
                }
            } while (true);

            try {
                in.close();
            } catch (IOException e3) {
            }

            String[] token = inputLine.split(" ", -2);   // Tokenize the data input line

            observationUTCTime = token[2];  // zulu observation Example: 211952Z 
            utcUpdateTime = zulu.getUTCTime();
            complete = false;

            for (int i = 3; (complete == false) && (i < token.length); i++) {
                if (token[i].startsWith("A") == true) {
                    if (token[i].equals("AUTO") == true
                            || token[i].equals("AO2") == true
                            || token[i].equals("AO2A") == true) {// might be AUTO or AO2
                        continue;
                    }

                    airportAltimeter = (float) (Integer.parseInt(token[i].substring(1)) / 100.00f);
                    complete = true; // bail out of for-loop
                }
            }

            if (complete == true) {
                altitudeCorrection = (int) Math.round((STANDARD - airportAltimeter) * 1000.0);
                pressureAltitude = (airportElevation + altitudeCorrection);
                airportDataValid = true;

                System.out.println("Pressure Altitude Correction "
                        + altitudeCorrection
                        + " As of "
                        + observationUTCTime);
            } else {
                airportDataValid = false;
            }
        }
    }
}
