package edu.fandm.enovak.sfs;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by enovak on 6/27/17.
 */

public class FileDBHelper extends SQLiteOpenHelper {
    private static final String TAG = FileDBHelper.class.getName();

    private static final int DATABASE_VERSION = 8;
    private static final String DATABASE_NAME = "SFS";
    public static final String TABLE_FILES = "FILES";

    // Schema
    public static final String KEY_FID = "FID";
    public static final String KEY_NAME = "FNAME";
    public static final String KEY_HOSTNAME = "HOSTNAME";
    public static final String KEY_CACHED = "CACHED";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_FILES + " (" +
                    KEY_FID + " TEXT " + " PRIMARY KEY," +
                    KEY_NAME + " TEXT, " +
                    KEY_HOSTNAME + " TEXT, " +
                    KEY_CACHED + " INTEGER);";

    private static final String[] PROJ_ALL =  new String[]{
        FileDBHelper.KEY_FID,
        FileDBHelper.KEY_NAME,
        FileDBHelper.KEY_HOSTNAME,
        FileDBHelper.KEY_CACHED
    };

    FileDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating new database!");
        db.execSQL(TABLE_CREATE);
    }

    public void onOpen(SQLiteDatabase db){
        //Log.d(TAG, "Opening database: " + db.getPath());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // on upgrade drop older tables
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FILES);

        // create new tables
        onCreate(db);
    }

    public static void insert(SQLiteDatabase db, SFSFile f){
        insert(db, f.name, f.FID, f.hostname);
    }

    public static void insert(SQLiteDatabase db, String name, String FID, String hostname){
        ContentValues values = new ContentValues();
        values.put(KEY_NAME, name);
        values.put(KEY_FID, new String(FID));
        values.put(KEY_HOSTNAME, hostname);

        try {
            db.insertOrThrow(TABLE_FILES, null, values);
        } catch (android.database.sqlite.SQLiteConstraintException e){
            Log.d(TAG, "This file is already in the DB!");
        }
    }

    public static void setCached(SQLiteDatabase db, SFSFile target){
        setCached(db, target.FID, target.isCached);
    }

    public static void setCached(SQLiteDatabase db, String FID, boolean newCachedVal){
        ContentValues cv = new ContentValues();
        cv.put(KEY_CACHED, newCachedVal);
        db.update(TABLE_FILES, cv, KEY_FID + "=?", new String[] {FID});
    }

    public static SFSFile getFile(SQLiteDatabase db, String fid){
        // To Do

        throw new UnsupportedOperationException("Not yet implemented!");
    }

    public static List<SFSFile> getAllFiles(SQLiteDatabase db){
        Cursor c = getAll(db);
        ArrayList<SFSFile>output = new ArrayList<SFSFile>(c.getCount());

        String fid;
        String name;
        String hostname;
        boolean isCached;

        SFSFile cur;
        while(c.moveToNext()){
            fid = c.getString(c.getColumnIndexOrThrow(KEY_FID));
            name = c.getString(c.getColumnIndexOrThrow(KEY_NAME));
            hostname = c.getString(c.getColumnIndexOrThrow(KEY_HOSTNAME));
            isCached = c.getInt(c.getColumnIndexOrThrow(KEY_CACHED)) == 1;

            cur = new SFSFile(name, fid, hostname);
            cur.isRemote = true;
            cur.isCached = isCached;
            output.add(cur);
        }
        return output;
    }

    public static Cursor getAll(SQLiteDatabase db){
        Cursor c = db.query(TABLE_FILES, PROJ_ALL, null, null, null, null, null);
        return c;
    }

}
