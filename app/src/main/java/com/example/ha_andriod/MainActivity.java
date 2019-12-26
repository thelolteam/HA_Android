package com.example.ha_andriod;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ThreadPoolExecutor;

public class MainActivity extends AppCompatActivity {

    int id = -1;
    LocationManager locationManager;
    boolean gps_enabled = false;
    Intent locationIntent;

    boolean mustStopSSIDCheck = false;

    boolean connectionSuccess = false;
    boolean pauseTask = false;
    ServerSocket serverSocket = null;
    String getRequest;
    String[] reqParameters = new String[7];
    WaitForConnectionTask waitForConnectionTask;
    String handshakeMessage = "client@app$action@config$0$0$APP$0$0$";

    boolean handshake = false;
    boolean hTaskRunning = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager)getApplicationContext().getSystemService(getApplicationContext().LOCATION_SERVICE);
        locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);

        getLocationAccess();
        Log.d("MSG", "Location Done");

        handshakeTry();
    }

    void handshakeTry(){
        Log.d("MSG2", "Trying handshake");
        handshake = false;
        hTaskRunning = true;
        new HandshakeTask().execute();
        /*while(hTaskRunning){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }*/

    }

    class HandshakeTask extends AsyncTask<Void, Void, Void>{

        HttpURLConnection con;
        URL url;
        String data = "client@app$action@config$0$0$APP$0$0$";
        Socket socket = null;
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage("Connecting....");
            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                url = new URL("http://192.168.1.1:8080/message?data=" + data);
                Log.d("MSG2", "URL: " + url);
                con = (HttpURLConnection)url.openConnection();
                con.setConnectTimeout(7000);
                con.connect();
                int resCode = con.getResponseCode();
                Log.d("MSG2", "Res Code: " + resCode);
                if(resCode == 200){
                    con.disconnect();
                    ServerSocket serverSocket = new ServerSocket(8080);
                    serverSocket.setSoTimeout(10000);
                    socket = serverSocket.accept();
                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    getRequest = br.readLine();
                    PrintStream ps = new PrintStream(socket.getOutputStream());
                    ps.println("HTTP/1.1 200 OK\n");
                    ps.close();
                    br.close();
                    socket.close();

                    int i = getRequest.indexOf("/message?data=");
                    if(i>0){
                        getRequest = getRequest.substring(i);
                        i = getRequest.indexOf("=");
                        i++;
                        getRequest = getRequest.substring(i);
                        i = getRequest.indexOf(" ");
                        getRequest = getRequest.substring(0, i);
                        reqParameters = getRequest.split("\\$", 0);
                        for(i=0; i<7; i++){
                            Log.d("MSG2", "parameter[" + i + "]: " + reqParameters[i]);
                        }
                        id = Integer.parseInt(reqParameters[3]);
                        Log.d("MSG2", "ID: " + id);
                        if(id!=-1)
                            handshake = true;

                    }else{
                        Log.d("MSG2", "Message Body Invalid");
                    }
                }
            } catch (Exception e) {
                Log.d("MSG2", "HandShake E: " + e.getMessage());
                e.printStackTrace();
            }finally{
                hTaskRunning = false;
                Log.d("MSG2", "HTaskRunning: " + hTaskRunning);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            if(!handshake){
                Log.d("MSG2", "Failed handshake");
                showDialog();
            }else{
                Log.d("MSG2", "Success handshake");
                //start ssidcheckthread.
                new KeepCheckingSSID().execute();
                //refresh node list
            }
        }
    }

    class ConnectTask extends AsyncTask<String, Void, Void>{

        HttpURLConnection con;
        URL url;
        String data;

        @Override
        protected Void doInBackground(String... strings) {
            data = strings[0];
            connectionSuccess = false;
            try {
                url = new URL("http://192.168.1.1:8080/message?data=" + data);
                Log.d("MSG2", "URL: " + url);
                con = (HttpURLConnection)url.openConnection();
                con.connect();
                int resCode = con.getResponseCode();
                if(resCode == 200){
                    connectionSuccess = true;
                }
                Log.d("MSG2", "Res Code: " + resCode);
                con.disconnect();
            } catch (Exception e) {
                Log.d("MSG2", "Connect E: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            if(connectionSuccess){
                //reflect
            }
        }
    }

    void waitForConnection(){
        pauseTask = false;
        waitForConnectionTask = new WaitForConnectionTask();
        waitForConnectionTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    class WaitForConnectionTask extends AsyncTask<Void, Void, Void>{

        Socket socket;
        InetAddress inetAddress;
        int port = 8080;
        BufferedReader br;
        PrintStream ps;

        @Override
        protected Void doInBackground(Void... voids) {
            try{
                inetAddress = InetAddress.getLocalHost();
                serverSocket = new ServerSocket(port);
                serverSocket.setSoTimeout(10000);
                while(!pauseTask){
                    socket = serverSocket.accept();
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    getRequest = br.readLine();
                    ps = new PrintStream(socket.getOutputStream());
                    ps.println("HTTP/1.1 200 OK\n");
                    ps.close();
                    br.close();
                    socket.close();

                    int i = getRequest.indexOf("/message?data=");
                    if(i>0){
                        getRequest = getRequest.substring(i);
                        i = getRequest.indexOf("=");
                        i++;
                        getRequest = getRequest.substring(i);
                        i = getRequest.indexOf(" ");
                        getRequest = getRequest.substring(0, i);

                        reqParameters = getRequest.split("\\$", 0);
                        for(i=0; i<7; i++){
                            Log.d("MSG2", "parameter[" + i + "]: " + reqParameters[i]);
                        }

                        pauseTask = true;
                    }else{
                        Log.d("MSG2", "Message Body Invalid");
                    }
                }

                serverSocket.close();

            }catch (Exception e){
                Log.d("MSG2", "Wait E: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            new DecodeParamaters().execute();
            waitForConnection();
        }
    }

    class DecodeParamaters extends AsyncTask<Void, Void, Void>{

        boolean callSSIDCheck = false;

        @Override
        protected Void doInBackground(Void... voids) {
            if(reqParameters[1].equals("action@config")){
                id = Integer.parseInt(reqParameters[3]);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            if(callSSIDCheck){
                new KeepCheckingSSID().execute();
            }
        }
    }

    class KeepCheckingSSID extends AsyncTask<Void, Void, Void>{

        WifiManager wm;
        WifiInfo wi;
        String currssid, ssid;
        @Override
        protected Void doInBackground(Void... voids) {
            wm = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wi = wm.getConnectionInfo();
            if(wi.getSupplicantState() == SupplicantState.COMPLETED){
                ssid = wi.getSSID();
            }

            while(!mustStopSSIDCheck){
                wi = wm.getConnectionInfo();
                if(wi.getSupplicantState() == SupplicantState.COMPLETED){
                    currssid = wi.getSSID();

                    if(!currssid.equals(ssid)){
                        mustStopSSIDCheck = true;
                        handshake = false;
                    }
                }else{
                    handshake = false;
                    mustStopSSIDCheck = true;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            if(!handshake)
                showDialog();
        }
    }

    //Show Location Permission Dialog if not permitted
    protected void getLocationAccess(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Pop Dialog
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        else{
            Toast.makeText(this,"Has Location Access", Toast.LENGTH_SHORT).show();
            turnOnLocation();
        }
    }

    //Making of Location Permission Dialog, action on the click
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Got Location Access", Toast.LENGTH_SHORT).show();
                    Log.d("MSG2", "Got Access");
                    turnOnLocation();
                } else {
                    Toast.makeText(this, "App requires location access to verify connection to MODULE", Toast.LENGTH_SHORT).show();
                    Log.d("MSG2", "Access Denied");
                    getLocationAccess();
                }
                return;
            }
        }
    }

    //Open Location settings if location not ON
    protected void turnOnLocation(){
        try {
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            Log.d("MSG2", "GPS Stat");
        } catch(Exception ex) {}
        if(!gps_enabled){
            //Open Location Settings
            Toast.makeText(this,"Location OFF", Toast.LENGTH_SHORT).show();
            Log.d("MSG2", "Starting gps activity");
            startActivity(locationIntent);
        }

    }

    private void showDialog(){
        AlertDialog.Builder builder;

        builder = new AlertDialog.Builder(this);

        builder.setMessage("Unable to Register To ESP, Check Connection").setCancelable(false).setPositiveButton("Try Again", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.cancel();
                handshakeTry();
            }
        }).setNegativeButton("Exit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                finish();
            }
        });
        AlertDialog alert = builder.create();
        alert.setTitle("Connection Error!");
        alert.show();
    }

    @Override
    protected void onStop(){
        super.onStop();
        Log.d("MSG2", "Stopped");
        try{
            //serverSocket.close();
        }catch(Exception e){
            Log.d("MSG2", "Pause E: " + e.getMessage());
        }
        pauseTask = true;
        mustStopSSIDCheck = true;
    }

    @Override
    protected void onRestart(){
        super.onRestart();
        Log.d("MSG@", "Restarted");
        getLocationAccess();
        mustStopSSIDCheck = false;
        if(handshake)
            new KeepCheckingSSID().execute();
    }
}
