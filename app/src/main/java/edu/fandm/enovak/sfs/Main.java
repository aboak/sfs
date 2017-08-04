package edu.fandm.enovak.sfs;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.preference.PreferenceActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class Main extends AppCompatActivity {
    private final static String TAG = Main.class.getName();

    public final static String[] PERMS = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE};

    private Context ctx;
    private Intent serviceIntent;
    private SyncService syncServ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx = getApplicationContext();
        serviceIntent = new Intent(ctx, SyncService.class);

        int pid = android.os.Process.myPid();
        Log.d(TAG, "PID: " + pid);

        // Check permissions
        if(!checkPerms()) {
            Toast.makeText(ctx, "All permissions are necessary.  Please grant them and try again.", Toast.LENGTH_LONG).show();
            return;
        }

        // Check if WiFi is running
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (!mWifi.isConnected()) {
            Toast.makeText(ctx, "Connecting without WiFi", Toast.LENGTH_SHORT).show();
        }

        // Start the service!
        // Give some feedback about the IP and port and IMEI for testing / debugging purposes
        String ip = Globals.getLocalWifiIP(ctx);
        TextView tv = (TextView)findViewById(R.id.main_tv_details);
        tv.setText("Listening at: " + ip + "\n IMEI Tail: " + Globals.getIMEITail(ctx, 5));
        startService(serviceIntent);

        // Bind to the service here so we if this activity exists we are bound to service
        bindService(serviceIntent, bindConn, Context.BIND_AUTO_CREATE);
    }

    public void onResume(){
        super.onResume();
        checkPerms();
        setStatusState();
    }

    public void onDestroy(){
        super.onDestroy();;
        try {
            unbindService(bindConn);
        } catch (IllegalArgumentException e){};
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.action_file_browser:
                Intent i = new Intent(this, FileBrowser.class);
                startActivity(i);
                break;

            case R.id.action_stop_service:
                Log.d(TAG, "trying to unbind");
                try {
                    unbindService(bindConn);
                } catch (IllegalArgumentException e){};
                stopService(serviceIntent);
                syncServ = null;
                setStatusState();
                break;
        }
        return true;
    }

    private void setStatusState() {
        // Set button according to service state.
        TextView tv = (TextView)findViewById(R.id.main_tv_status);
        ImageView iv = (ImageView)findViewById(R.id.main_iv_status);

        if(syncServ != null && syncServ.isRunning()){
            tv.setText("SFS Service On!");
            iv.setImageResource(R.mipmap.service_on);
        } else {
            tv.setText("Service Not Running.  Click to Restart!");
            iv.setImageResource(R.mipmap.service_off);
            LinearLayout ll = (LinearLayout)tv.getParent();
            ll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    recreate();
                }
            });
        }
    }


    public void onRequestPermissionsResult(int code, String[] perms, int[] results){
        if(code == 1){
            for(int i = 0; i < results.length; i++){
                if(results[i] == PackageManager.PERMISSION_DENIED){
                    Toast.makeText(ctx, "All permissions are necessary.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    private boolean checkPerms(){
        // check all permissions
        for(int i = 0; i < PERMS.length; i++){
            if(ContextCompat.checkSelfPermission(ctx, PERMS[i]) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, PERMS, 1);
                return false;
            }
        }
        return true;
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection bindConn = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to SyncService, cast the IBinder to the service's binder type
            // and get an instance of the service itself (syncServ)
            SyncService.LocalBinder binder = (SyncService.LocalBinder) service;
            syncServ = binder.getService();

            setStatusState();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Binding to service lost!");
            // Bind to the service here so we if this activity exists we are bound to service
            unbindService(bindConn);
            syncServ = null;
            setStatusState();
        }
    };
}
