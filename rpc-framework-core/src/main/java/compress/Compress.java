package compress;

import extension.SPI;

/**
 * @author cyx
 * @create 2021-03-30 19:41
 */
@SPI
public interface Compress {

    byte[] compress(byte[] bytes);


    byte[] decompress(byte[] bytes);

}
