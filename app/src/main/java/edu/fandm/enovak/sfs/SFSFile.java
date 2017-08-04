package edu.fandm.enovak.sfs;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by enovak on 6/29/17.
 */

class SFSFile {
    private final static String TAG = SFSFile.class.getName();

    public String FID;
    public String name;
    public String hostname;
    public boolean isCached = false;
    public boolean isRemote = false;

    SFSFile(File f){
        this(f.getName(), hash(f), null);
    }

    SFSFile(String nName, String nFID, String nHostname){
        name = nName;
        FID = nFID;
        hostname = nHostname;
    }

    @Override
    public String toString(){

        if(isRemote == false){
            return name;
        } else {
            return hostname + ":" + name;
        }
    }


    public static String hash(File f){
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] fBytes = new byte[(int)f.length()];
            fis.read(fBytes);
            String h = hash(fBytes);
            return h;
        } catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    public static String hash(byte[] input){
        try {
            //Log.d(TAG, "hashing now!");
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input);
            byte[] hashOut = md.digest();
            md.reset();
            String hex = new BigInteger(1, hashOut).toString(16);
            return hex;
        } catch (NoSuchAlgorithmException e){

        }
        return null;
    }

    public String getCacheName(){
        int i = this.name.lastIndexOf('.');
        String name = this.FID + this.name.substring(i);
        return name;
    }
}
