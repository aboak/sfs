package edu.fandm.enovak.sfs;

import java.net.Socket;

/**
 * Created by enovak on 6/29/17.
 */

public class SocketDeadException extends Exception {
    public Socket s;

    public SocketDeadException(Socket offendingS){
        super();
        s = offendingS;
    }
}
