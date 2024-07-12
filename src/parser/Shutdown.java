/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import com.fazecast.jSerialComm.SerialPort;
import decoder.DataBlockParser;
import java.io.IOException;
import java.io.InputStream;

public final class Shutdown extends Thread {

    private final BufferDataBlocks bufferdatablock;
    private final DataBlockParser datablockparser;
    private final SerialPipe serialpipe;
    private final InputStream inputstream;
    private final SerialPort serialport;

    public Shutdown(SerialPort port, InputStream stream,  SerialPipe br, BufferDataBlocks bd, DataBlockParser dp) {
        serialpipe = br;
        bufferdatablock = bd;
        datablockparser = dp;
        serialport = port;
        inputstream = stream;
    }

    @Override
    public void run() {
        System.out.println("Shutdown started");
        
        serialpipe.close();
        datablockparser.close();
        
        bufferdatablock.resetQueue(); // empty the list
        bufferdatablock.close();

        try {
            inputstream.close();
        } catch (IOException e) {
            // punt
        }

        serialport.closePort();
    }
}
