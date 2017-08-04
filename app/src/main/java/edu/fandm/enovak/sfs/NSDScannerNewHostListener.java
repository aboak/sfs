package edu.fandm.enovak.sfs;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Created by enovak on 6/27/17.
 */

public interface NSDScannerNewHostListener {
    void newHostEvent(InetSocketAddress insa);
}
