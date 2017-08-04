package edu.fandm.enovak.sfs;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;

/**
 * Created by enovak on 6/16/17.
 */

public class NSDScanner {
    private final static String TAG = NSDScanner.class.getName();

    private NsdManager mNsdManager;
    private Context ctx;
    private HashSet<String> names;
    private NSDScannerNewHostListener NHListener;


    private myNSDDiscoveryL myNSDDiscoveryLInstance;
    private myNSDRegistrationL myNSDRegistrationLInstance;




    public NSDScanner(Context newCTX){
        ctx = newCTX;
        mNsdManager = (NsdManager)ctx.getSystemService(Context.NSD_SERVICE);
        names = new HashSet<String>();

    }

    public void setNSDScannerNewHostListener(NSDScannerNewHostListener newNHListener){
        NHListener = newNHListener;
    }

    public void startScan(){
        // Search for services offered on other devices
        myNSDDiscoveryLInstance = new myNSDDiscoveryL();
        mNsdManager.discoverServices(Globals.SFS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, myNSDDiscoveryLInstance);
    }

    public void stopScan(){
        mNsdManager.stopServiceDiscovery(myNSDDiscoveryLInstance);
    }

    public void makeVisible(){
        NsdServiceInfo serviceInfo  = new NsdServiceInfo();
        String ownName = Globals.getServiceName(ctx);
        Log.d(TAG, "ownName: " + ownName);
        serviceInfo.setServiceName(ownName);
        names.add(ownName);
        serviceInfo.setServiceType(Globals.getServiceType());
        serviceInfo.setPort(Globals.PORT);

        myNSDRegistrationLInstance = new myNSDRegistrationL();
        mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, myNSDRegistrationLInstance);
    }

    public void makeInvisible(){
        mNsdManager.unregisterService(myNSDRegistrationLInstance);
    }


    public void startScanMakeVisible(){
        startScan();
        makeVisible();
    }

    public void stopScanMakeInvisible(){
        stopScan();
        makeInvisible();
    }


    private class myNSDResolveL implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(NsdServiceInfo info, int errorCode) {
            // Called when the resolve fails.  Use the error code to debug.
            Log.d(TAG, "Resolve failed. code: " + errorCode + "   " + info);
        }

        @Override
        public void onServiceResolved(NsdServiceInfo info) {
            Log.d(TAG, "New service resolved! " + info);

            String correctType = "." + Globals.SFS_SERVICE_TYPE.substring(0, Globals.SFS_SERVICE_TYPE.length() - 1);
            //Log.d(TAG, info.getServiceType() + "  ==  " + correctType);
            if(info.getServiceType().equals(correctType)){
                //Log.d(TAG, "It's the correct type!");

                if(!(names.contains(info.getServiceName()))){
                    //Log.d(TAG, "This is a new peer!");
                    names.add(info.getServiceName());
                    InetSocketAddress insa = new InetSocketAddress(info.getHost(), info.getPort());
                    if(NHListener != null){
                        // Make call to callback
                        NHListener.newHostEvent(insa);
                    }
                } else {
                    Log.d(TAG, "I've already connected to this peer");
                }
            }
        }
    }

    private class myNSDDiscoveryL implements NsdManager.DiscoveryListener  {
        public void onServiceFound(NsdServiceInfo info) {
            //Log.d(TAG, "checking: " + info.getServiceName() + "  ==  " + Globals.getServiceName(ctx));
            if( info.getServiceName().equals(Globals.getServiceName(ctx)) ) {
                // This is me
            } else {
                //Log.d(TAG, "Service Found!  " + info + " attempting to resolve...");
                mNsdManager.resolveService(info, new myNSDResolveL());
            }
        }

        public void onServiceLost(NsdServiceInfo info){
            Log.d(TAG, "Service Lost: " + info);
        }

        public void onDiscoveryStarted(String regType){
            Log.d(TAG, "Discovery started.  Offering service: " + regType);
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.d(TAG, "Discovery stopped");
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.d(TAG, "Discovery failed: Error code:" + errorCode);
            mNsdManager.stopServiceDiscovery(this);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.d(TAG, "Discovery failed: Error code:" + errorCode);
            mNsdManager.stopServiceDiscovery(this);
        }
    };


    private class myNSDRegistrationL implements NsdManager.RegistrationListener {
        @Override
        public void onServiceRegistered(NsdServiceInfo info) {
            // Save the service name.  Android may have changed it in order to
            // resolve a conflict, so update the name you initially requested
            // with the name Android actually used.
            if(!Globals.getServiceName(ctx).equals(info.getServiceName())){
                Log.d(TAG, "New Service Name Provided: " + info.getServiceName());
                throw new IllegalStateException("Name changed by Android!!");
            }
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo info, int code) {
            // Registration failed!  Put debugging code here to determine why.
            Log.d(TAG, "Registration Failed");
            Log.d(TAG, "info: " + info);
            Log.d(TAG, "Error: " + code);
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo arg0) {
            // Service has been unregistered.  This only happens when you call
            // NsdManager.unregisterService() and pass in this listener.
            //Log.d(TAG, "NSD unregistered.");
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Unregistration failed.  Put debugging code here to determine why.
            Log.d(TAG, "NSD unregistration failed!");
        }
    };
}
