/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import java.io.IOException;
import java.io.PipedInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/*
 * This class scans the data pipe connected to the binary serial port
 * and queues the serial decoded blocks as buffered List.
 */
public final class BufferDataBlocks extends Thread {

    private final Thread process;
    private final PipedInputStream data_pipe;
    private final ArrayList<DataBlock> recordQueue;
    private final ZuluMillis zulu;
    private final BeastMessageParser bmp;
    private final MessageDigest messageDigest;
    private final char[] hexArray;
    private final int amplitude;
    private boolean EOF;
    //
    private final Config config;

    public BufferDataBlocks(PipedInputStream p, Config cf) throws NoSuchAlgorithmException {
        hexArray = "0123456789ABCDEF".toCharArray();
        zulu = new ZuluMillis();
        bmp = new BeastMessageParser();
        config = cf;
        
        amplitude = config.getAmplitude();
        data_pipe = p;
        recordQueue = new ArrayList<>();
        messageDigest = MessageDigest.getInstance("SHA-1");
        
        process = new Thread(this);
        process.setName("BufferDataBlocks");
        process.setPriority(Thread.NORM_PRIORITY);
    }

    @Override
    public void start() {
        EOF = false;
        process.start();
    }

    /*
     * Method to functionally shutdown the program by setting EOF to true
     */
    public void close() {
        recordQueue.clear();
        EOF = true;
    }

    /*
     * Method to determine how many packets are on the pipe queue
     *
     * @return int a value representing how many packets are on the pipe queue
     */
    public synchronized int getQueueSize() {
        return recordQueue.size();
    }

    /*
     * Push the receive packet onto the queue
     *
     * There is a lot of duplicate data packets from receiver data.
     */
    private synchronized void pushData(int signal, String data) {
        DataBlock block;
        
        switch (data.length()) {
            case 14 -> {
                messageDigest.update(data.getBytes());  // 20 byte 40 hex hash
                String dataHash = bytesToHex(messageDigest.digest());
                block = new DataBlock(DataBlock.SHORTBLOCK, zulu.getUTCTime(), signal, data, dataHash);
            }
            case 28 -> {
                block = new DataBlock(DataBlock.LONGBLOCK, zulu.getUTCTime(), signal, data);
            }
            default -> {
                return;
            }
        }
        
        if (recordQueue.add(block) != true) {
            System.out.println("BufferDataBlocks::pushData could not add DataBlock to queue");
        }
    }

    /*
     * Pop a packet off the queue
     */
    public synchronized DataBlock popData() throws IndexOutOfBoundsException {
        return recordQueue.remove(0);
    }

    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];

        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;

            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

    /*
     * Thread to read and parse the data from the pipe
     */
    @Override
    public void run() {
        List<ExtractedBytes> buffer;
        Iterator<ExtractedBytes> iterator;
        ExtractedBytes packet;
        byte[] modes;
        int available;

        while (EOF == false) {
            try {
                while ((available = data_pipe.available()) != 0) {
                    modes = new byte[available];

                    if (data_pipe.read(modes, 0, available) == -1) {
                        // end of data_pipe, we are dead
                        System.out.println("BufferDataBlocks::run Fatal: Lost serial communication");
                        System.exit(0);
                    }
                    /*
                     * Get the linked list of Mode-S Beast packets
                     */
                    buffer = bmp.parse(modes, available);

                    iterator = buffer.iterator();

                    while (iterator.hasNext()) {
                        packet = iterator.next();

                        int signal = packet.getSignalLevel();
                        String data = bytesToHex(packet.getMessageBytes());

                        // Disregard Mode AC data if enabled
                        // Mode AC is 2 Bytes converted to 4 Hex Char
                        // Disregard low amplitude signals

                        if ((signal > amplitude) && (data.length() > 4)) {
                            pushData(signal, data);
                        }
                    }
                }
                try {
                    sleep(1);
                } catch (InterruptedException z) {
                }
            } catch (IOException e) {
                System.out.println("BufferDataBlocks::run - IO Exception: " + e.toString());
                break;
            }
        }
    }
}