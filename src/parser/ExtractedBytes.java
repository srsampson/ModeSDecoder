/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

public final class ExtractedBytes {

    private final byte[] messageBytes;
    private final int signalLevel;

    public ExtractedBytes(int sl, byte[] mb) {
        messageBytes = mb;
        signalLevel = sl;
    }

    public byte[] getMessageBytes() {
        return messageBytes;
    }

    public int getSignalLevel() {
        return signalLevel;
    }
}
