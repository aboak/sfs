package edu.fandm.enovak.sfs;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.FileObserver;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SyncService extends Service {
    private final static String TAG = SyncService.class.getName();

    private volatile boolean running;
    protected Context ctx;
    private File root;

    // Collections.synchronize* gives a thread safe implementation of the given List type.
    public List<SFSFile> localFiles = Collections.synchronizedList(new ArrayList<SFSFile>());

    private SFSNetworkServer servT;
    private IPScanner ipsT;
    private NSDScanner nsds;

    // These might need to be thread safe?
    private HashMap<String, Socket> connections = new HashMap<String, Socket>();


    // This allows clients to "Bind" and then call public methods below (no need for static, yay!)
    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        SyncService getService() {
            // Return this instance of LocalService so clients can call public methods
            return SyncService.this;
        }
    }


    public SyncService() {
        Log.d(TAG, "SERVICE CREATED!!");
        ctx = this;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Getting Binded To!");
        return mBinder;
    }


    public int onStartCommand(Intent i, int flags, int startID){
        Log.d(TAG, "Sync Service Started");
        int pid = android.os.Process.myPid();
        Log.d(TAG, "PID: " + pid);
        running = true;

        // Guarantee that the necessary folders exist
        File tmp = Globals.getLocalRoot();
        tmp.mkdirs();
        tmp = Globals.getRemoteRoot();
        tmp.mkdirs();
        tmp = null;

        // Scan the local folder for contents (these are files the user is sharing)
        root = Globals.getLocalRoot();
        root.mkdirs();
        SFSFileObserver rootFileObs = new SFSFileObserver(root.getAbsolutePath());
        rootFileObs.startWatching();
        fileScan();


        File f = new File(root, "test" + Globals.getIMEITail(ctx, 3) + ".txt");
        try {
            FileOutputStream fos = new FileOutputStream(f);
            String s = "This is some placeholder text.\nThe last three digits of the IMEI\nof the phone that created this file is: " + Globals.getIMEITail(ctx, 3);
            fos.write(s.getBytes());
            fos.close();
        } catch (IOException e){
            e.printStackTrace();
        }


        // Start thread for server
        servT = new SFSNetworkServer();
        servT.start();

        // Do some sort of service scanning / discovery here
        // 1) NSDScanner (does not seem to work on the school's Eduroam WIFI network, works at home)
        // 2) IPScanner (works but seems to crash the WiFi on the phone sometimes?)
        // 3) ???

        // Option 1 NSDScanner
        nsds = new NSDScanner(ctx);
        nsds.setNSDScannerNewHostListener(new NSDScannerNewHostListener() {
            @Override
            public void newHostEvent(InetSocketAddress insa) {
                establishConnection(insa);
            }
        });
        nsds.startScanMakeVisible();



        // Option 2 IPScanner
        // Put myself in the list of connections so I don't connect to myself when scanning
        //connections.put(Globals.getLocalWifiIP(ctx), null);
        //ipsT = new IPScanner();
        //ipsT.start();


        return START_STICKY;
    }

    public void onDestroy(){
        if(servT != null) {
            servT.die();
        }

        // Stop option 1
        if(nsds != null) {
            nsds.stopScanMakeInvisible();
        }

        // Stop option 2
        //ipsT.die();

        // Close all active connections
        for(Socket s : connections.values()){
            try{
                s.close();
            } catch (IOException e){

            }
        }
        connections.clear();


        // Do these two things last.  Setting running = false is just for outsiders to
        // observe the state of the service (via the isRunning method below).  I'm not sure it works.
        running = false;
        Log.d(TAG, "Sync Service Stopped");
    }


    public boolean isRunning(){
        return running;
    }

    public static File getLocalFileFromFID(String FID, List<SFSFile> files)throws FileNotFoundException {
        File dir = Globals.getLocalRoot();
        for(int i = 0; i < files.size(); i++){
            if(files.get(i).FID.equals(FID)){
                File f = new File(dir, files.get(i).name);
                return f;
            }
        }
        throw new FileNotFoundException("Could not find matching FID: " + FID);
    }

    // Feels like maybe I should be binding or something?
    public void getFile(final SFSFile SFSf, final SyncServiceNewFileListener origSSNFListener) {

        // Check cache
        Log.d(TAG, "Checking if file is cached");


        if (SFSf.isCached) {
            // This file is in the cache, just use the local copy!
            Log.d(TAG, "HIT!!");
            if (origSSNFListener != null) {
                File newF = new File(Globals.getRemoteRoot(), SFSf.getCacheName());
                origSSNFListener.newFile(newF);
            }
        } else {
            // This file is not in the cache, time to search the cluster for it!
            Log.d(TAG, "Miss!!");
            FileCaller caller = new FileCaller(ctx, SFSf, origSSNFListener, connections);
        }
    }

    public SFSFile[] listAllFiles(){

        // Remote files
        FileDBHelper fdbHelper = new FileDBHelper(ctx);
        SQLiteDatabase db = fdbHelper.getReadableDatabase();
        List<SFSFile> output = FileDBHelper.getAllFiles(db);
        db.close();

        File localSharedDir = Globals.getLocalRoot();
        File[] localFiles = localSharedDir.listFiles();
        for(int i = 0; i < localFiles.length; i++){
            //Log.d(TAG, "File: " + localFiles[i].getName() + "  hash: " + Globals.hash(localFiles[i]) + "  as string: " + new String(Globals.hash(localFiles[i])));
            output.add(new SFSFile(localFiles[i]));
        }

        SFSFile[] tmp = new SFSFile[output.size()];
        return output.toArray(tmp);
    }




    private void establishConnection(InetSocketAddress insa) {
        Log.d(TAG, "Current connections: " + connections.keySet());
        if( !(connections.containsKey(insa.getAddress().getHostAddress())) ) {
            Log.d(TAG, "New address!  Trying to connect...");
            try {
                Socket s = new Socket();
                connections.put(insa.getAddress().getHostAddress(), s);
                s.connect(insa);
                Log.d(TAG, "Done establishing channel");


                //Log.d(TAG, "Done establishing channel.  Will now exchange files.");

                // Do the initial best practice (get remote file list)
                ClientProtocolThread clientT = new ClientProtocolThread(s, Globals.PROTOCOL_REQ_FILE_LIST, ctx);
                clientT.start();
            } catch (IOException e) {
                Log.d(TAG, "Failed connecting to client: " + insa.getAddress().getHostAddress());
                connections.remove(insa.getAddress().getHostAddress());
            }
        } else {
            Log.d(TAG, "Already have this connection, aborting connection.");
        }
    }


    private class SFSNetworkServer extends Thread {

        private ServerSocket ss = null;

        public void run(){
            try {
                ss = new ServerSocket(Globals.PORT);
                ss.setReuseAddress(true);

                while(true) {
                    try {
                        Socket client = ss.accept();
                        String IP = client.getInetAddress().getHostAddress();
                        Log.d(TAG, "Accepted new connection!  IP: " + IP);

                        // Establish server thread for this connection
                        ServerProtocolThread t = new ServerProtocolThread(client, localFiles);
                        t.start();

                        // Establish a "back channel"
                        // This device is now a server for the other.  But it also needs to
                        // be a client of hte other.
                        // This is a socket to establish this device as a client.
                        Log.d(TAG, "Establishing back channel!");
                        InetSocketAddress insa = new InetSocketAddress(client.getInetAddress(), Globals.PORT);
                        establishConnection(insa);
                    }

                    catch (SocketException e1){ // Should ONLY be caused from a prev call to die()
                        break;
                    } catch (IOException e2) {
                        e2.printStackTrace();
                        Log.d(TAG, "Fix this error!");
                        break;
                    }
                }
            } catch (IOException e){
                e.printStackTrace();
                Log.d(TAG, "Server cannot start listening. Fix this error!");
            }

            Log.d(TAG, "Server Thread Stopped");
        }

        public void die(){
            Log.d(TAG, "Server dieing");
            try {
                ss.close();
            } catch (NullPointerException | IOException e){}
            ss = null;
        }
    }

    private class IPScanner extends Thread {
        private final String TAG = edu.fandm.enovak.sfs.SyncService.IPScanner.class.getName();

        private Socket s = null;
        private long socketTS;
        private int timeout = 1000; // ms

        public IPScanner(){
            socketTS = System.currentTimeMillis(); // Set TS initially
        }

        public void run(){

            String curIP = Globals.START_IP;
            Log.d(TAG, "Starting IP probe at: " + curIP);

            while(true){
                curIP = incrIP(curIP);
                Log.d(TAG, "Probing: " + curIP);

                InetSocketAddress insa = new InetSocketAddress(curIP, Globals.PORT);

                // Wait at least timeout = 1000ms to test an IP regardless
                if( (System.currentTimeMillis() - socketTS) < timeout ){
                    Log.d(TAG, "Waiting...");
                    try {
                        Thread.currentThread().sleep(1000);
                    } catch (InterruptedException e){}
                }

                if(this.isInterrupted()){
                    break;
                }

                socketTS = System.currentTimeMillis();
                try {
                    if (insa.getAddress().isReachable(timeout)) {
                        establishConnection(insa);
                    }
                }

                catch (SocketTimeoutException | java.net.ConnectException | NoRouteToHostException e1) {
                    e1.printStackTrace();
                    // These are variations of "can't connect to this IP"
                    // In any case, the answer is to skip to the next IP
                }

                catch (IOException e){
                    // If this happens, I am pretty sure we have to stop probing, so break.
                    e.printStackTrace();
                    break;
                }

            /*

            } catch (SocketException e3) {
                // Caused from the socket closing; almost certainly die() was called
                e3.printStackTrace();
                Log.d(TAG, "Socket Exception in the IPScanner Thread");
                break;

            } catch (InterruptedException e5){
                e5.printStackTrace();
                Log.d(TAG, "IP Scanner thread interrupted!");
                break;
            }
            */
            }

            Log.d(TAG, "IPScanner Probe Thread Stopped");
        }

        public void die(){
            Log.d(TAG, "IP scanner dieing");
            this.interrupt();
            try {
                s.close();
            } catch (NullPointerException | IOException e){}
            s = null;
        }

        private String incrIP(String IP){
            String[] bytes = IP.split("\\.");

            for(int i = bytes.length - 1; i >= 0; i--){
                int cur = Integer.valueOf(bytes[i]);
                cur = (cur + 1) % 256;
                bytes[i] = String.valueOf(cur);
                if(cur != 0){
                    break;
                }
            }

            // Convert back to one string
            String s = bytes[0] + "." + bytes[1] + "." + bytes[2] + "." + bytes[3];

            // check if we need to skip (recursive)
            if(connections.containsKey(s)){
                s = incrIP(s);
            }

            return s;
        }
    }

    private void fileScan(){
        localFiles = walk(root, new ArrayList<SFSFile>());
        Log.d(TAG, localFiles.size() + " Local Files: " + localFiles);
    }

    private ArrayList<SFSFile> walk(File cur, ArrayList<SFSFile> files){
        String[] contents = cur.list();
        for(int i = 0; i < contents.length; i++){
            File child = new File(cur.getAbsolutePath(), contents[i]);
            SFSFile f = new SFSFile(child);
            Log.d(TAG, "FID:" + f.FID + "   name:"  + f.name);
            files.add(f);

            if(child.isDirectory()){ // recursive
                SFSFileObserver obs = new SFSFileObserver(child.getAbsolutePath());
                obs.startWatching();
                walk(child, files);
            }
        }
        return files;
    }

    private class SFSFileObserver extends FileObserver {
        private final int mask = FileObserver.CREATE | FileObserver.DELETE;

        SFSFileObserver(String pathName){
            super(pathName);
        }

        @Override
        public void onEvent(int event, String path) {
            if ((event & FileObserver.DELETE) != 0) {
                Log.d(TAG, "File deleted.  I must tell the others!1");
                tellTheOthers(path, Globals.PROTOCOL_FILE_DELETE);

            } else if ((event & FileObserver.CREATE) != 0) {
                Log.d(TAG, "New file created.  I must tell the others!!");
                tellTheOthers(path, Globals.PROTOCOL_FILE_CREATE);
            }
        }

        private void tellTheOthers(String filePath, byte MSG_TYPE){
            for(int i = 0; i < connections.size(); i++){
                try {
                    ClientProtocolThread t = new ClientProtocolThread(connections.get(i), MSG_TYPE, ctx);
                    t.setFileName(filePath);
                    t.start();
                } catch (IOException e){
                    Log.d(TAG, "Error informing: " + connections.get(i).getInetAddress().getHostAddress());
                }
            }
        }
    }
}
