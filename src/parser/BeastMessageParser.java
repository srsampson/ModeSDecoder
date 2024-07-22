/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;

/*
 * This is used to parse the Mode-S Beast binary data.
 *
 * There are only three frame formats in the binary protocol:
 *
 * <esc> "1" : 6 byte MLAT counter, 1 byte signal level, 2 byte Mode-AC
 * <esc> "2" : 6 byte MLAT counter, 1 byte signal level, 7 byte Mode-S short frame
 * <esc> "3" : 6 byte MLAT counter, 1 byte signal level, 14 byte Mode-S long frame
 *
 * (<esc><esc>: true 0x1A
 *
 * <esc> is 0x1A, and "1", "2" and "3" are 0x31, 0x32 and 0x33 ASCII
 */
public final class BeastMessageParser {

    private static final int ESCAPE = 0x1A;
    private static final byte MODEAC = 0x31;
    private static final byte SHORT = 0x32;
    private static final byte LONG = 0x33;
    //
    private static final int MAXBUFFERSIZE = 10000; // some big number

    private final class ExtractResult {

        int signalLevel;
        int startOfPacket;
        int endOfPacket;
        int dataLength;

        ExtractResult(int sl, int sp, int ep, int dl) {
            signalLevel = sl;
            startOfPacket = sp;
            endOfPacket = ep;
            dataLength = dl;

        }
    }

    private byte[] readBuffer;
    private int readBufferLength;
    //
    private byte[] payload;
    private byte[] mlatBytes;
    //
    private boolean sawFirstPacket;

    public BeastMessageParser() {
        sawFirstPacket = false;
    }

    /**
     * Scans a byte array for Mode S beast messages. Returns a collection of
     * extracted Mode S payloads.
     *
     * @param bytes an array of raw bytes
     * @param bytesLength the length of the array of raw bytes
     * @return a linked list of Mode-S data packets
     */
    public List<ExtractedBytes> parse(byte[] bytes, int bytesLength) {
        LinkedList<ExtractedBytes> result = new LinkedList<>();
        int length = readBufferLength + bytesLength;

        if (length > MAXBUFFERSIZE) {
            readBufferLength = 0;
            length = bytesLength;
        }

        if (readBuffer == null || length > readBuffer.length) {
            byte[] newReadBuffer = new byte[length];

            if (readBuffer != null) {
                System.arraycopy(readBuffer, 0, newReadBuffer, 0, readBuffer.length);
            }

            readBuffer = newReadBuffer;
        }

        System.arraycopy(bytes, 0, readBuffer, readBufferLength, bytesLength);
        readBufferLength = length;

        int startOfPacket = findStartIndex(0);

        if ((sawFirstPacket == false) && startOfPacket == 1) {
            startOfPacket = findStartIndex(startOfPacket);
        }

        int firstByteAfterLastValidPacket = -1;

        while (startOfPacket != -1 && startOfPacket < readBufferLength) {
            int dataLength = 0;

            ExtractResult eresult = extractBinaryPayload(startOfPacket, dataLength);

            int endOfPacket = eresult.endOfPacket;

            if (endOfPacket == -1) {
                break;
            }

            dataLength = eresult.dataLength;

            sawFirstPacket = true;
            firstByteAfterLastValidPacket = endOfPacket;

            ExtractedBytes extractedBytes = new ExtractedBytes()
                .setMlatBytes(Arrays.copyOf(mlatBytes, 6))
                .setSignalLevel(eresult.signalLevel)
                .setMessageBytes(Arrays.copyOf(payload, dataLength));
            result.add(extractedBytes);

            startOfPacket = findStartIndex(firstByteAfterLastValidPacket);
        }

        if (firstByteAfterLastValidPacket != -1) {
            int unusedBytesCount = readBufferLength - firstByteAfterLastValidPacket;

            if (unusedBytesCount > 0) {
                if (unusedBytesCount > 1024) {
                    unusedBytesCount = 0;
                } else {
                    for (int si = firstByteAfterLastValidPacket, di = 0; di < unusedBytesCount; ++si, ++di) {
                        readBuffer[di] = readBuffer[si];
                    }
                }
            }

            readBufferLength = unusedBytesCount;
        }

        return result;
    }

    /*
     * Find the <esc> character, and ignore <esc><esc>
     * Return the start index, or -1 if none found
     */
    private int findStartIndex(int start) {
        int result = -1;

        for (int i = start; i < readBufferLength; ++i) {
            byte ch = readBuffer[i];

            if (ch == ESCAPE) {
                if (++i < readBufferLength) {
                    if (readBuffer[i] != ESCAPE) {
                        result = i;
                        break;
                    }
                }
            }
        }

        return result;
    }

    private ExtractResult extractBinaryPayload(int startOfPacket, int dataLength) {
        dataLength = 0;

        switch (readBuffer[startOfPacket++]) {
            case MODEAC -> {  // Mode-A 2-byte Code is Octal in each nibble
                dataLength = 2;
            }
            case SHORT -> {  // Short Mode-S 7 bytes (56 bits)
                dataLength = 7;
            }
            case LONG -> {  // Long Mode-S 14 bytes (112 bits)
                dataLength = 14;
            }
        }

        if (payload == null || payload.length < dataLength) {
            payload = new byte[dataLength];
        }
        
        mlatBytes = new byte[6];
        
        int si = startOfPacket;
        int signalLevel = 0;
        int di;

        /*
         * First we read the MLAT and amplitude bytes
         */
        for (di = 0; si < readBufferLength && di < 7; ++si, ++di) {
            byte ch = readBuffer[si];

            if (ch == ESCAPE && ++si > readBufferLength) {
                break;
            }

            if (di == 6) {
                signalLevel = ch & 0xFF;
            } else {
                mlatBytes[di] = ch; // bytes 0 - 6
            }
        }

        /*
         * Read the payload data associated with the type (Short, Long, AC)
         */
        for (di = 0; si < readBufferLength && di < dataLength; ++si) {
            byte ch = readBuffer[si];

            if (ch == ESCAPE) {
                if (++si >= readBufferLength) {
                    break;
                }

                ch = readBuffer[si];
            }

            payload[di++] = ch;
        }

        int endOfPacket = (di != dataLength) ? -1 : si;

        return new ExtractResult(signalLevel, startOfPacket, endOfPacket, dataLength);
    }
}
