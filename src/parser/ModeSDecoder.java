/*
 * ModeSDecoder - A Mode-S/ADS-B MySQL Application for Windows
 *
 * This program connects to a Beast Mode-S Receiver via Serial Port.
 *
 * It reads the Serial Port data and sends target data to a MySQL database.
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
        parser = new DataBlockParser(receiverLatLon, bufferData);  // thread to create List of targets from blocks

        recv.start();
        bufferData.start();
        parser.start();

        /*
         * At this point the user is provided with a List of targets These
         * targets are only those that have been updated in some way. To get
         * these targets you call parser.getTargets.
         *
         * import decoder.Target;
         * import java.sql.Timestamp;
         * import java.util.List;
         *
         * Timestamp sqlTime = new Timestamp(0L);
         * List<Target> updated;
         *
         * try {
         *     while (true) {
         *         if (parser.getTargetQueueSize() > 0) {
         *             try {
         *                 updated = parser.getTargets();
         *             } catch (Exception e) {
         *                 continue;
         *             }
         *
         *             for (Target target : updated) {
         *                 sqlTime.setTime(target.getUpdatedTime());
         *                 String timestamp = sqlTime.toString();
         *
         *                 System.out.printf("%s %s %d %.6f %.6f %s\n",
         *                     target.getAcid(),
         *                     target.getCallsign(),
         *                     target.getAltitude(),
         *                     target.getLatitude(),
         *                     target.getLongitude(),
         *                     timestamp);
         *             }
         *
         *             Thread.sleep(1000);
         *         } else {
         *             Thread.sleep(10000);
         *         }
         *     }
         * } catch (Exception e1) {
         * }
         *
         *
         * Sample Output (very poor antenna):
         *
         * A1AC24 N207BG 42475 35.344666 -97.613468 2024-07-06 17:02:29.237
         * A00CE0 RPA4535 5150 35.342010 -97.521114 2024-07-06 17:02:29.237
         * A0FBB7 UAL1098 35000 35.352859 -97.600994 2024-07-06 17:02:30.238
         * A1AC24 N207BG 42500 35.344711 -97.615356 2024-07-06 17:02:30.238
         * A00CE0 RPA4535 5200 35.343297 -97.518798 2024-07-06 17:02:30.238
         * A0FBB7 UAL1098 35000 35.351397 -97.599150 2024-07-06 17:02:31.239
         * A1AC24 N207BG 42500 35.344757 -97.617588 2024-07-06 17:02:31.239
         * A00CE0 RPA4535 5225 35.344025 -97.517509 2024-07-06 17:02:31.239
         * A0FBB7 UAL1098 35000 35.349768 -97.597104 2024-07-06 17:02:32.24
         * A1AC24 N207BG 42500 35.344787 -97.619311 2024-07-06 17:02:32.24
         * A00CE0 RPA4535 5250 35.344740 -97.516285 2024-07-06 17:02:32.24
         * A0FBB7 UAL1098 35000 35.348325 -97.595293 2024-07-06 17:02:33.241
         * A1AC24 N207BG 42525 35.344803 -97.621021 2024-07-06 17:02:33.241
         * A00CE0 RPA4535 5275 35.344740 -97.516285 2024-07-06 17:02:33.241
         * A0FBB7 UAL1098 35000 35.346542 -97.592983 2024-07-06 17:02:34.241
         * A1AC24 N207BG 42525 35.344833 -97.623051 2024-07-06 17:02:34.241
         * A00CE0 RPA4535 5300 35.345811 -97.514298 2024-07-06 17:02:34.241
         * A0FBB7 UAL1098 35000 35.346542 -97.592983 2024-07-06 17:02:35.244
         * A1AC24 N207BG 42525 35.344849 -97.624798 2024-07-06 17:02:35.244
         * A00CE0 RPA4535 5350 35.346542 -97.512932 2024-07-06 17:02:35.244
         * A0FBB7 UAL1098 35000 35.344787 -97.590676 2024-07-06 17:02:36.245
         * A1AC24 N207BG 42550 35.344880 -97.626440 2024-07-06 17:02:36.245
         * A00CE0 RPA4535 5375 35.347533 -97.511142 2024-07-06 17:02:36.245
         * A0FBB7 UAL1098 35000 35.343201 -97.588577 2024-07-06 17:02:37.245
         * A1AC24 N207BG 42550 35.344940 -97.628517 2024-07-06 17:02:37.245
         * A00CE0 RPA4535 5400 35.347916 -97.510471 2024-07-06 17:02:37.245
         * A0FBB7 UAL1098 35000 35.341481 -97.586293 2024-07-06 17:02:38.246
         * A1AC24 N207BG 42550 35.344940 -97.628517 2024-07-06 17:02:38.246
         * A00CE0 RPA4535 5425 35.348883 -97.508688 2024-07-06 17:02:38.246
         * A0FBB7 UAL1098 35000 35.339767 -97.583942 2024-07-06 17:02:39.248
         * A1AC24 N207BG 42575 35.344973 -97.632518 2024-07-06 17:02:39.248
         * A00CE0 RPA4535 5450 35.349609 -97.507381 2024-07-06 17:02:39.248
         * A0FBB7 UAL1098 35000 35.339767 -97.583942 2024-07-06 17:02:40.25
         * A1AC24 N207BG 42575 35.345019 -97.634388 2024-07-06 17:02:40.25
         * A00CE0 RPA4535 5450 35.350326 -97.506058 2024-07-06 17:02:40.25
         * A0FBB7 UAL1098 35000 35.336792 -97.579880 2024-07-06 17:02:41.252
         * A1AC24 N207BG 42575 35.345066 -97.636258 2024-07-06 17:02:41.252
         * A00CE0 RPA4535 5500 35.350326 -97.506058 2024-07-06 17:02:41.252
         * A0FBB7 UAL1098 35000 35.333817 -97.575874 2024-07-06 17:02:42.254
         * A1AC24 N207BG 42575 35.345078 -97.638359 2024-07-06 17:02:42.254
         * A00CE0 RPA4535 5550 35.351164 -97.504597 2024-07-06 17:02:42.254
         * A0FBB7 UAL1098 35000 35.333817 -97.575874 2024-07-06 17:02:43.254
         * A1AC24 N207BG 42600 35.345123 -97.640247 2024-07-06 17:02:43.254
         * A00CE0 RPA4535 5575 35.351898 -97.503204 2024-07-06 17:02:43.254
         * A0FBB7 UAL1098 35000 35.330704 -97.571640 2024-07-06 17:02:44.257
         * A1AC24 N207BG 42600 35.345159 -97.642335 2024-07-06 17:02:44.257
         * A00CE0 RPA4535 5600 35.352768 -97.501717 2024-07-06 17:02:44.257
         * A0FBB7 UAL1098 35000 35.328964 -97.569294 2024-07-06 17:02:45.259
         * A1AC24 N207BG 42600 35.345206 -97.644205 2024-07-06 17:02:45.259
         * A00CE0 RPA4535 5650 35.353492 -97.500448 2024-07-06 17:02:45.259
         *
         *
         * You can also create a Track from the target
         * to exchange with other sites.
         * 
         * Random rand = new Random();
         * TrackNumber trackNumber = new TrackNumber(rand.nextLong());
         *
         * Track track = new Track(trackNumber.getNextTrackNumber(), target);
         */

        Shutdown sh = new Shutdown(port, comm_input, recv, bufferData, parser);
        Runtime.getRuntime().addShutdownHook(sh);
    }
}
