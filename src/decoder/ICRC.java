/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public interface ICRC {

    /**
     * A polynomial CRC algorithm for validating mode-s packets
     * 
     * @param raw a 56 or 112 bit raw Mode-S packet
     * @return a string representing the result of the CRC check
     */
    public String crcCompute(String raw);
}
