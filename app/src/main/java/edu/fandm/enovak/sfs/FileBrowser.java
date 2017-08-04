package edu.fandm.enovak.sfs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class FileBrowser extends AppCompatActivity {
    private final static String TAG = FileBrowser.class.getName();

    private ArrayAdapter<SFSFile> listViewArrayAdapter;
    private ProgressBar prog;
    private Context ctx;
    private SyncService syncServ;

    private SyncServiceNewFileListener SSNFListener = new SyncServiceNewFileListener() {
        @Override
        public void newFile(File f) {
            runOnUiThread(new TurnOffProgRunnable());

            if(f == null){
                runOnUiThread(new ShowFileLostToast());
            } else {
                Log.d(TAG, "I have the file! " + f.getAbsolutePath());
                openFile(f);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);
        ctx = this;
        prog = (ProgressBar)findViewById(R.id.file_browser_pb_loading);

        listViewArrayAdapter = new ArrayAdapter<SFSFile>(this, android.R.layout.simple_list_item_1);
        final ListView lv = (ListView) findViewById(R.id.file_browser_lv_files);
        lv.setAdapter(listViewArrayAdapter);

        // Short click opens an item
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                prog.setVisibility(View.VISIBLE);

                SFSFile f = listViewArrayAdapter.getItem(position);
                if(f.isRemote) {
                    syncServ.getFile(f, SSNFListener);
                } else {
                    openFile(new File(Globals.getLocalRoot(), f.name));
                    prog.setVisibility(View.GONE);
                }
            }
        });

        Intent intent = new Intent(this, SyncService.class);
        bindService(intent, bindConn, Context.BIND_AUTO_CREATE);
    }

    protected void onResume(){
        super.onResume();
        updateUI();
    }

    protected void onDestroy(){
        super.onDestroy();
        try {
            unbindService(bindConn);
        } catch (IllegalArgumentException e){};
    }

    private void openFile(File f){
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(FileProvider.getUriForFile(ctx, "edu.fandm.enovak.fileprovider", f));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        /*
        PackageManager p = ctx.getPackageManager();
        if(i.resolveActivity(p) != null){
            Toast.makeText(ctx, "No app for this file!", Toast.LENGTH_SHORT).show();
        } else {
            startActivity(i);
        }
        */

        startActivity(i);
    }

    private void updateFileList(){
        listViewArrayAdapter.clear();
        if(syncServ != null) {
            SFSFile[] files = syncServ.listAllFiles();
            listViewArrayAdapter.addAll(files);
        }
    }

    private void updateUI(){
        updateFileList();

        TextView warn = (TextView)findViewById(R.id.file_browser_tv_warning);
        if(syncServ != null && syncServ.isRunning()) {
            warn.setVisibility(View.GONE);
        } else {
            warn.setVisibility(View.VISIBLE);
        }
    }

    private class TurnOffProgRunnable implements Runnable {
        public void run(){
            prog.setVisibility(View.GONE);
        }

    }

    private class ShowFileLostToast implements Runnable {
        public void run() {
            Toast.makeText(ctx, "The device hosting this file is no longer reachable.", Toast.LENGTH_LONG).show();
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection bindConn = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to SyncService, cast the IBinder to the service's binder type
            // and get an instance of the service itself (syncServ)
            SyncService.LocalBinder binder = (SyncService.LocalBinder) service;
            syncServ = binder.getService();
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Binding to service lost!");
            updateUI();
        }
    };
}
