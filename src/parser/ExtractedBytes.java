/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

public class ExtractedBytes {

    private byte[] messageBytes;
    private byte[] mlatBytes;
    private int signalLevel;

    public ExtractedBytes() {
        signalLevel = -1;
    }

    public ExtractedBytes setMessageBytes(byte[] bytes) {
        messageBytes = bytes;
        return this;
    }

    public ExtractedBytes setMlatBytes(byte[] bytes) {
        mlatBytes = bytes;
        return this;
    }

    public ExtractedBytes setSignalLevel(int sl) {
        signalLevel = sl;
        return this;
    }

    public int getSignalLevel() {
        return signalLevel;
    }

    public byte[] getMlatBytes() {
        return mlatBytes;
    }

    public byte[] getMessageBytes() {
        return messageBytes;
    }
}
