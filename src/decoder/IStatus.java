/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the IStatus Interface
 *
 * Used to provide DF Status information
 */
public interface IStatus {

    public int getFS3();

    public int getDR5();

    public int getUM6();

    public int getIDS2();

    public int getIIS4();
}