/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import com.fazecast.jSerialComm.SerialPort;
import decoder.DataBlockParser;
import java.io.IOException;
import java.io.InputStream;

public final class Shutdown extends Thread {

    private final BufferDataBlocks bd;
    private final DataBlockParser dp;
    private final SerialPipe br;
    private final InputStream stream;
    private final SerialPort port;

    public Shutdown(SerialPort port, InputStream stream,  SerialPipe br, BufferDataBlocks bd, DataBlockParser dp) {
        this.br = br;
        this.bd = bd;
        this.dp = dp;
        this.port = port;
        this.stream = stream;
    }

    @Override
    public void run() {
        System.out.println("Shutdown started");
        this.br.close();
        this.dp.close();
        
        this.bd.resetQueue(); // empty the list
        this.bd.close();

        try {
            this.stream.close();
        } catch (IOException e) {
            // punt
        }

        this.port.closePort();
    }
}
