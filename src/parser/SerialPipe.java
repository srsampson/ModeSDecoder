/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;

public final class SerialPipe extends Thread {

    private final Thread dataReceive;
    private final InputStream input;
    private final PipedOutputStream output;
    private boolean EOF;

    public SerialPipe(InputStream i, PipedOutputStream o) {
        this.input = i;
        this.output = o;
        this.EOF = false;

        dataReceive = new Thread(this);
        dataReceive.setName("SerialPipe");
        dataReceive.setPriority(Thread.NORM_PRIORITY);
    }

    @Override
    public void start() {
        dataReceive.start();
    }

    public void close() {
        EOF = true;
    }

    /*
     * Thread to read the data from the Beast serial port and put it in the pipe.
     */
    @Override
    public void run() {
        int cnt, val;
        byte[] data;

        while (EOF == false) {
            try {
                while ((cnt = input.available()) > 0) {
                    data = new byte[cnt];

                    /*
                     * This will block if input goes gimpy
                     */
                    val = input.read(data, 0, cnt);

                    /*
                     * Write the data to the pipe
                     */
                    if (val != -1) {
                        output.write(data, 0, data.length);
                    } else {
                        System.out.println("SeialPipe::run Write Pipe Overrun");
                        sleep(10);
                    }
                }

                sleep(0,100);
            } catch (IOException | InterruptedException ex) {
                System.out.println("SerialPipe::run read error " + ex.getMessage());
                break;
            }
        }
    }
}
