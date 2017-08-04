package edu.fandm.enovak.sfs;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * Created by enovak on 6/13/17.
 */

class ClientProtocolThread extends Thread {
    private final static String TAG = ClientProtocolThread.class.getName();

    private Socket sock;
    private int MSG_TYPE;
    private Context ctx;

    private DataInputStream is;
    private DataOutputStream os;

    private List<String> localFiles;
    private String curFName;
    private String curFID;
    private SyncServiceNewFileListener ssel;

    ClientProtocolThread(Socket newS, int N_MSG_TYPE, Context newCtx) throws IOException {
        super();
        sock = newS;
        MSG_TYPE = N_MSG_TYPE;
        ctx = newCtx;

        os = new DataOutputStream(sock.getOutputStream());
        is = new DataInputStream(sock.getInputStream());

        Log.d(TAG, "I will send a message to: " + sock.getInetAddress().getHostAddress() + "  msg type: " + MSG_TYPE);
    }


    @Override
    public void run() {
        super.run();
        try {
            // First, tell the server what is happening
            os.writeByte(MSG_TYPE);

            // Then, do that thing.
            switch (MSG_TYPE) {
                case Globals.PROTOCOL_REQ_FILE_LIST:
                    // Parse data and store in Database
                    String fileNames = Globals.acceptStringData(is);
                    Log.d(TAG, "Got file list response");
                    //Log.d(TAG, "fileNameS: " + fileNames);

                    // Format of result pairs from the server is [filename1, filehash1, filename2, filehash2, ...]
                    String[] pairs = fileNames.split("#");
                    //Log.d(TAG, "first pair: " + pairs[0] + " " + pairs[1]);
                    FileDBHelper fileDBOpener = new FileDBHelper(ctx);
                    SQLiteDatabase fileDB = fileDBOpener.getWritableDatabase();
                    for (int i = 0; i < pairs.length / 2; i++) {
                        String name = pairs[i * 2];
                        String fid = pairs[(i * 2) + 1];
                        String hostname = sock.getInetAddress().getHostName();
                        Log.d(TAG, "Inserting into DB: " + name + "  fid: " + fid + "  hostname: " + hostname);
                        FileDBHelper.insert(fileDB, name, fid, hostname);
                    }
                    fileDB.close();
                    //Log.d(TAG, "File DB should now include more files...");
                    //Log.d(TAG, "File DB should now include more files...");
                    break;

                case Globals.PROTOCOL_FILE_CREATE:
                case Globals.PROTOCOL_FILE_DELETE:
                    // The same process!  Just tell the server what was deleted or created.
                    byte[] data = curFName.getBytes();
                    os.writeInt(data.length);
                    os.write(data);
                    break;

                case Globals.PROTOCOL_REQ_FILE:
                    os.writeInt(curFID.length());
                    os.write(curFID.getBytes());

                    File f;
                    byte[] fileBytes = Globals.acceptData(is);
                    Log.d(TAG, "Got bytes: " + fileBytes.length + "   at spot [0]: " + fileBytes[0]);
                    if (fileBytes.length == 1 && fileBytes[0] == 0){
                        f = null;
                    } else {
                        f = new File(Globals.getRemoteRoot(), curFName);
                        FileOutputStream fos = new FileOutputStream(f);
                        fos.write(fileBytes);
                        fos.close();
                    }
                    if(ssel != null){
                        Log.d(TAG, "Client Protocol Thread Calling Callback");
                        ssel.newFile(f);
                    }
                    break;
            }
        } catch (IOException e){
            e.printStackTrace();
        }

        Log.d(TAG, "Client Protocol Thread Finished");
    }

    public void setLocalFiles(List<String> newLocalFiles){
        localFiles = newLocalFiles;
    }

    public void setSyncServiceEventListener(SyncServiceNewFileListener newSSEL){
        ssel = newSSEL;
    }

    public List<String> getLocalFiles(){
        return localFiles;
    }

    public void setFileName(String name){
        curFName = name;
    }

    public String getFileName(){
        return curFName;
    }

    public void setFID(String FID) { curFID = FID; }

    public String getFID(){ return curFID; }

}
