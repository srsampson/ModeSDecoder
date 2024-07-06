/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the IAlert Interface
 *
 * Used to provide alerting information
 */
public interface IAlert {

    public boolean getIsOnGround();

    public boolean getIsAlert();

    public boolean getIsSPI();

    public boolean getIsEmergency();
}