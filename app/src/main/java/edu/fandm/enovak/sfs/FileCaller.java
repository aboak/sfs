package edu.fandm.enovak.sfs;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by enovak on 6/30/17.
 */
class FileCaller {
    // Class used by the SyncService to obtain a File from the current pool of known
    // devices in the cluster (connections)


    private final static String TAG = FileCaller.class.getName();

    private volatile boolean found = false;
    private SyncServiceNewFileListener origListener;
    private SFSFile fileTarget;
    private Context ctx;

    private volatile int searchCounter = 0;

    private SyncServiceNewFileListener deepListener = new SyncServiceNewFileListener() {
        @Override
        public void newFile(File f) {
            Log.d(TAG, "Response!  searchCounter before: " + searchCounter);
            searchCounter--;

            if (f != null) {
                if (found == false) {
                    fileTarget.isCached = true;
                    updateDB();
                    found = true;
                    origListener.newFile(f);
                }
            } else if (searchCounter == 0 && !found) {
                // This is the last cluster client responding
                // we haven't found the file yet
                // and this ain't it either.
                fileTarget.isCached = false;
                updateDB();
                found = false;
                origListener.newFile(f = null); // Just return the null file.
            }
        }
    };

    public FileCaller(Context nCtx, SFSFile target, SyncServiceNewFileListener l, Map<String, Socket> connections){
        origListener = l;
        fileTarget = target;
        ctx = nCtx;

        // Nobody to ask!  Just return null;
        if(connections.size() == 0){
            searchCounter++;
            File f = null;
            deepListener.newFile(f);
            return;
        }

        for (Socket curS : connections.values()) {
            if(found){
                break;
            }
            try {
                ClientProtocolThread t = new ClientProtocolThread(curS, Globals.PROTOCOL_REQ_FILE, ctx);
                t.setFID(target.FID);
                t.setFileName(target.getCacheName());
                t.setSyncServiceEventListener(deepListener);
                t.start();
                searchCounter++;
                Log.d(TAG, "Sent req for " + target.name + " to " + curS.getInetAddress().getHostAddress());
            } catch (IOException e){
                Log.d(TAG, "Error calling for file from " + curS.getInetAddress().getHostAddress());
            }
        }
    }

    private void updateDB(){
        FileDBHelper fdb = new FileDBHelper(ctx);
        SQLiteDatabase db = fdb.getWritableDatabase();
        FileDBHelper.setCached(db, fileTarget);
        db.close();
    }
}
