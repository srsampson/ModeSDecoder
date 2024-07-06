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
        this.messageBytes = bytes;
        return this;
    }

    public ExtractedBytes setMlatBytes(byte[] bytes) {
        this.mlatBytes = bytes;
        return this;
    }

    public ExtractedBytes setSignalLevel(int signalLevel) {
        this.signalLevel = signalLevel;
        return this;
    }

    public int getSignalLevel() {
        return this.signalLevel;
    }

    public byte[] getMlatBytes() {
        return mlatBytes;
    }

    public byte[] getMessageBytes() {
        return messageBytes;
    }
}
