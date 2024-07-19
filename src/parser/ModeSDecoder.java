/*
 * ModeSDecoder - A Mode-S/ADS-B Decoder Application for Windows
 *
 * This program connects to a Beast Mode-S Receiver via Serial Port.
 * It reads the Serial Port data and combines data into targets.
 * The targets are then stored and updated in a MySQL Database.
 *
 * My thanks to Kinetic Avionic UK, who got us all started in
 * the hobby with their SBS-1, which was a fantastic kit
 * with its FPGA decoder.
 *
 * My thanks also to Jetvision and Günter Köllner for his
 * fantastic FPGA based 1090 receiver called the Mode-S Beast.
 *
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import com.fazecast.jSerialComm.SerialPort;
import decoder.DataBlockParser;
import decoder.LatLon;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Locale;

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

    public static void main(String[] args) {
        /*
         * The user may have a commandline option for config file.
         */
        try {
            if (args[0].equals("-c") || args[0].equals("/c")) {
                configFile = args[1];
            }
        } catch (Exception e) {
        }

        Locale.setDefault(Locale.US);

        config = new Config(configFile);

        /*
         * Create a buffer between Serial and ProcessData
         *
         * I tried using a BufferedReader but couldn't get it to
         * work with the jSerialComm library.
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
         * Note: The Switches on the Beast are
         * a real bitch. They are so small that you
         * need a magnifying glass and a sharp stick.
         *
         * But you can also send the switch config settings
         * using the serial port (Except for Baud Rate).
         *
         * Toward the LEDS = OPEN
         * AWAY from the LEDS = CLOSED
         *
         * SW1, SW2 = OPEN (Normal Baud Rate)
         *
         * SW3 = CLOSED (Binary Format)                c,C option
         * SW4 = OPEN (All DF Decoded)                 d,D
         * SW5 = CLOSED (MLAT Counter Enabled)         e,E
         * SW6 = OPEN (CRC on DF-11, DF-17, DF-18)     f,F
         * SW7 = OPEN (DF-0/DF-4/DF-5 filter Off)      g,G
         * SW8 = CLOSED (Hardware Handshake)           h,H
         * SW9 = CLOSED (FEC Off)                      i,I
         * SW10 = OPEN (No Mode-A/C)                   j,J
         *
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

        /*
         * Setup the Beast modes the way I like it (recommended by JetVision)
         */
        beastSetup();

        comm_input = port.getInputStream();

        receiverLatLon = new LatLon(config.getStationLatitude(), config.getStationLongitude());

        recv = new SerialPipe(comm_input, beast_output);   // grab Beast data and buffer between threads
        bufferData = new BufferDataBlocks(beast_input, config);     // thread to queue beast data into blocks
        parser = new DataBlockParser(config, receiverLatLon, bufferData);  // thread to create List of targets from blocks

        Shutdown sh = new Shutdown(port, comm_input, recv, bufferData, parser);
        Runtime.getRuntime().addShutdownHook(sh);

        recv.start();
        bufferData.start();
        parser.start();
    }
    
    /*
     * Amateur Hour here, just git-er-done...
     */
    private static void beastSetup() {
        byte[] optionsmsg = new byte[3];
        boolean error = false;

        optionsmsg[0] = 0x1a;   // Escape
        optionsmsg[1] = 0x31;   // '1'
        optionsmsg[2] = 'C';    // binary mode command

        if (port.writeBytes(optionsmsg, 3) != 3) {
            error = true;
        }

        optionsmsg[2] = 'd';  // Pass All DF
        if (port.writeBytes(optionsmsg, 3) != 3) {
            error = true;
        }
        
        optionsmsg[2] = 'E';  // MLAT enabled
        if (port.writeBytes(optionsmsg, 3) != 3) {
            error = true;
        }
        
        optionsmsg[2] = 'f';  // CRC off
        if (port.writeBytes(optionsmsg, 3) != 3) {
            error = true;
        }
        
        optionsmsg[2] = 'g';  // DF0,4,5 Filter off
        if (port.writeBytes(optionsmsg, 3) != 3) {
            error = true;
        }
        
        optionsmsg[2] = 'H';  // Hardware handshake
        if (port.writeBytes(optionsmsg, 3) != 3) {
            error = true;
        }
        
        optionsmsg[2] = 'I';  // FEC off
        if (port.writeBytes(optionsmsg, 3) != 3) {
            error = true;
        }
        
        optionsmsg[2] = 'J';  // No Mode-A/C
        if (port.writeBytes(optionsmsg, 3) != 3) {
            error = true;
        }
        
        if (error == true) {
            System.out.printf("Beast Switch Command %c Failed\n", (char) optionsmsg[2]);
        } else {
            System.out.println("Mode-S Beast Switches Configured");
        }
    }
}
