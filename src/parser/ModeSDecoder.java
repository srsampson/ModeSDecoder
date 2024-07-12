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
    private static Database db;

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
         * Connect to the Serial Communications Port
         */
        var port = SerialPort.getCommPort(config.getCommPort());

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

        receiverLatLon = new LatLon(config.getStationLatitude(), config.getStationLongitude());

        recv = new SerialPipe(comm_input, beast_output);   // grab Beast data and buffer between threads
        bufferData = new BufferDataBlocks(beast_input, config);     // thread to queue beast data into blocks
        parser = new DataBlockParser(config, receiverLatLon, bufferData);  // thread to create List of targets from blocks
        db = new Database(config, parser);

        Shutdown sh = new Shutdown(db, port, comm_input, recv, bufferData, parser);
        Runtime.getRuntime().addShutdownHook(sh);

        recv.start();
        bufferData.start();
        parser.start();
    }
}
