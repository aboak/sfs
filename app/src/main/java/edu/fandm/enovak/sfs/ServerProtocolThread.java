package edu.fandm.enovak.sfs;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.List;


/**
 * Created by enovak on 6/13/17.
 */

public class ServerProtocolThread extends Thread {
    private final static String TAG = ServerProtocolThread.class.getName();

    private Socket sock;
    public final String IP; // defined below in the constructor
    private DataInputStream is;
    private DataOutputStream os;

    private List<SFSFile> localFiles;

    ServerProtocolThread(Socket newS, List<SFSFile> newLocalFiles){
        super();

        sock = newS;
        IP = sock.getInetAddress().getHostAddress();
        localFiles = newLocalFiles;
    }

    @Override
    public void run(){
        super.run();

        try{
            is = new DataInputStream(sock.getInputStream());
            os = new DataOutputStream(sock.getOutputStream());
        } catch (IOException e){
            e.printStackTrace();
            return;
        }

        while (true) {
            try {
                byte code = is.readByte(); // Protocol always starts (at this end) with a 1 byte MSG_TYPE
                handleCode(code);

            } catch (IOException e) {
                Log.d(TAG, "Protocol violated!!!  in connection with: " + sock.getInetAddress().getHostAddress());
                e.printStackTrace();
                break;
            }

        }


        try {
            sock.close();
        } catch (IOException e){};
        Log.d(TAG, "Server Protocol Thread Stopped");

    }


    private void handleCode(byte code) throws IOException {
        switch(code){

            case Globals.PROTOCOL_REQ_FILE_LIST:
                Log.d(TAG, "Handling request for file list");
                StringBuilder sb = new StringBuilder();
                for(int i = 0; i < localFiles.size(); i++){
                    SFSFile tmp = localFiles.get(i);
                    sb.append(tmp.name + "#" + tmp.FID + "#");
                }

                // Sometimes there are no files, in that case we wil have length = 0 and cannot deleteCharAt
                if(sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1); // Remove the trailing #
                }

                String list = sb.toString();

                os.writeInt(list.length()); // Number of characters = number of bytes
                os.write(list.getBytes()); // send the bytes
                break;


            case Globals.PROTOCOL_REQ_FILE:
                Log.d(TAG, "Handling file get REQ");
                String fid = Globals.acceptStringData(is);
                Log.d(TAG, "Looking for file with FID: " + fid );

                byte[] fileBytes;
                try {
                    File f = SyncService.getLocalFileFromFID(fid, localFiles);
                    fileBytes = Globals.getFileBytes(f);
                    Log.d(TAG, "Found it!  Sending back file bytes.");
                } catch (FileNotFoundException e){
                    Log.d(TAG, "Don't have this file!  Sending back null.");
                    fileBytes = new byte[]{0};
                }


                os.writeInt(fileBytes.length);
                os.write(fileBytes);
                break;


            default:
                Log.d(TAG, "UNKNOWN PROTOCOL TYPE_MSG: " + code + "    Ignoring...");
                return;

        }
    }


}
