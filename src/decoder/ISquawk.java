/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the ISquawk Interface
 *
 * Used as the decoder of the squawk broadcasts
 */
public interface ISquawk {

    /**
     * Method to decode squawk and return the string value of 4-digit octal code
     *
     * @param raw56 a string representing the raw 56 bits to decode
     * @return squawk a String representing the squawk in 4-digit octal
     */
    public String decodeSquawk(String raw56);
}
