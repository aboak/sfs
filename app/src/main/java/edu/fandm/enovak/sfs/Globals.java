package edu.fandm.enovak.sfs;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Created by enovak on 6/12/17.
 */

public class Globals {
    private final static String TAG = Globals.class.getName();

    public final static int PORT = 7700;
    public final static String START_IP = "155.68.178.126";
    public final static String SFS_SERVICE_TYPE = "_sfs._tcp."; // The format of this is very strict

    public final static byte PROTOCOL_REQ_FILE_LIST = 1;
    public final static byte PROTOCOL_FILE_CREATE = 2;
    public final static byte PROTOCOL_FILE_DELETE = 3;
    public final static byte PROTOCOL_REQ_FILE = 4;


    public static String getIMEITail(Context ctx, int len){
        // Length of the number of IMEI digits to use (len = 3 means the final three digits)

        TelephonyManager telephonyManager = (TelephonyManager)ctx.getSystemService(Context.TELEPHONY_SERVICE);
        String id = telephonyManager.getDeviceId();
        if(len > 0) {
            return id.substring(id.length() - len);
        } else {
            return id;
        }
    }


    public static String bytesToString(byte[] data){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < data.length; i++){
            sb.append((char)data[i]);
        }
        return sb.toString();
    }

    public static String acceptStringData(DataInputStream is) throws IOException {
        byte[] dataBytes = acceptData(is);
        String dataString = Globals.bytesToString(dataBytes);
        return dataString;
    }

    public static byte[] acceptData(DataInputStream is) throws IOException {
        // First comes an integer size field, which describes how many
        // bytes are about to come (these subsequent bytes are then converted back to a string
        int size = is.readInt();
        byte[] dataBytes = new byte[size];

        // Next comes the actual data
        is.readFully(dataBytes);
        return dataBytes;
    }

    public static String getLocalWifiIP(Context ctx){
        WifiManager wifiMgr = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        String ipAddress = Formatter.formatIpAddress(ip);

        DhcpInfo dhcpInfo = wifiMgr.getDhcpInfo();
        if(dhcpInfo != null){
            Log.d(TAG, "subnet: " + dhcpInfo.netmask + "  dhcp info: " + dhcpInfo.toString());
        } else {
            Log.d(TAG, "No DHCP info!");
        }
        return ipAddress;
    }

    public static String getServiceName(Context ctx) {
        return "SFS-" + Globals.getIMEITail(ctx, 5);
    }

    public static String getServiceType(){
        // the format of this thing is ridiculously strict
        return SFS_SERVICE_TYPE;
    }

    public static File getLocalRoot(){
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SFS");
    }

    public static File getRemoteRoot() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SFS_REMOTE");
    }

    public static byte[] getFileBytes(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            fis.read(data);
            return data;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
