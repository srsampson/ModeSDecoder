/*
 * ModeSDecoder - A Mode-S/ADS-B Decoder Application for Windows
 *
 * This program connects to a Beast Mode-S Receiver via Serial Port.
 * It reads the Serial Port data and combines data into targets.
 * The targets are then stored and updated in a MySQL Database.
 *
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import com.fazecast.jSerialComm.SerialPort;
import decoder.DataBlockParser;
import decoder.LatLon;
import decoder.PressureAltitude;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

public final class ModeSDecoder {

    private static BufferDataBlocks bufferData;
    private static LatLon receiverLatLon;
    //
    private static String configFile = "modesdecoder.conf";
    private static Config config;
    private static SerialPipe recv;
    private static DataBlockParser parser;
    //
    private static PipedOutputStream beast_output;
    private static PipedInputStream beast_input;
    private static InputStream comm_input;
    private static SerialPort port;
    //
    private static Connection db;

    public static void main(String[] args) {
        /*
         * Check for commandline options
         */
        try {
            if (args[0].equals("-c") || args[0].equals("/c")) {
                configFile = args[1];
            }
        } catch (Exception e) {
        }

        Locale.setDefault(Locale.US);

        config = new Config(configFile);

        PressureAltitude pa = new PressureAltitude(config);   // Start PA data source

        /*
         * Create a pipe between Serial and ProcessData threads
         *
         * I tried using a BufferedReader but couldn't get it to
         * work with the jSerialComm library
         */
        try {
            beast_input = new PipedInputStream(4096);
            beast_output = new PipedOutputStream();
            beast_output.connect(beast_input);
        } catch (IOException p) {
            System.out.println("Fatal: Can't initialize data queue");
            System.exit(0);
        }
        
        /*
         * Connect to the Serial Communications Port
         */
        port = SerialPort.getCommPort(config.getCommPort());

        if (port.openPort() == false) {
            System.err.println("Fatal: Can't initialize Serial Port");
            System.exit(0);
        }

        port.setBaudRate(3000000);   // 3,000,000 Baud
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED);
        port.flushIOBuffers();

        if (port.getBaudRate() != 3000000) {
            System.err.println("Fatal: Unable to set Baud Rate on Serial Port");
            System.exit(0);
        }

        comm_input = port.getInputStream();

        /*
         * Command the Beast switches
         */        
        if (beastSetup() == false) {
            System.err.printf("Fatal: Beast Switch Command Failed\n");
            System.exit(0);
        } else {
            System.out.println("Mode-S Beast Switches Configured");
        }

        /*
         * Open the database
         */
        String connectionURL = config.getDatabaseURL();

        Properties properties = new Properties();
        properties.setProperty("user", config.getDatabaseLogin());
        properties.setProperty("password", config.getDatabasePassword());
        properties.setProperty("useSSL", "false");
        properties.setProperty("allowPublicKeyRetrieval", "true");
        properties.setProperty("serverTimezone", "UTC");

        /*
         * You need the ODBC MySQL driver library in the same directory you have
         * the executable JAR file of this program, but under a lib directory.
         */
        try {
            db = DriverManager.getConnection(connectionURL, properties);
        } catch (SQLException e2) {
            System.err.println("ModeSDecoder Fatal: Unable to open database 1 " + connectionURL + " " + e2.getLocalizedMessage());
            System.exit(0);
        }

        /*
         * The receiver location should be high resolution (6 digits).
         * It is used by the position determining algorithms.
         */
        receiverLatLon = new LatLon(config.getStationLatitude(), config.getStationLongitude());

        recv = new SerialPipe(comm_input, beast_output);   // grab Beast data and buffer between threads
        
        try {
            bufferData = new BufferDataBlocks(beast_input, config);     // queue thread
        } catch (NoSuchAlgorithmException md) {
            System.err.println("ModeSDecoder Fatal: Unable to use SHA-1 hash " + md.getMessage());
            System.exit(0);
        }
        
        parser = new DataBlockParser(config, receiverLatLon, bufferData, db);  // thread to create List of targets from blocks

        Shutdown sh = new Shutdown(port, comm_input, recv, bufferData, parser);
        Runtime.getRuntime().addShutdownHook(sh);

        /*
         * Start me up...
         *      ...and never stop
         */
        recv.start();
        bufferData.start();
        parser.start();
    }

    /*
     * Send the configuration switch settings
     * (Except for Baud Rate)
     *
     * Toward the LEDS = OPEN
     * AWAY from the LEDS = CLOSED
     *
     * SW1, SW2 = OPEN (Normal Baud Rate)
     *                                          Open | Closed
     * SW3  = CLOSED (Binary Format)               c | C*
     * SW4  = OPEN (All DF Decoded)                d*| D
     * SW5  = CLOSED (MLAT Counter Enabled)        e | E*
     * SW6  = OPEN (CRC on DF-11, DF-17, DF-18)    f*| F
     * SW7  = OPEN (DF-0/DF-4/DF-5 filter Off)     g*| G
     * SW8  = CLOSED (Hardware Handshake)          h | H*
     * SW9  = CLOSED (FEC Off)                     i | I*
     * SW10 = OPEN (No Mode-A/C)                   j*| J
     *
     * When binary format is selected, SW5 (MLAT Counter) is ignored (close it anyway).
     */
    private static boolean beastSetup() {
        byte[] optionsmsg = new byte[] {0x1a, 0x31, 0x00}; // Escape, '1', n
        String options = "CdEfgHIj";
        boolean good = true;

        for (int i = 0; i < options.length(); i++) {
            optionsmsg[2] = (byte) options.charAt(i);
            
            if (port.writeBytes(optionsmsg, 3) != 3) {
                good = false;
            }
        }

        return good;
    }
}
