package com.example.ha_andriod;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {

    DisplayMetrics metrics;
    static int width;
    static int height;

    ImageButton settings, refresh, wifiStat;
    GridView grid;
    static CustomAdapter adapter;

    int id = -1;
    LocationManager locationManager;
    boolean gps_enabled = false;
    Intent locationIntent, wifiIntent;

    boolean mustStopSSIDCheck = false;

    static boolean connectionSuccess = false;
    boolean pauseWaitTask = false;
    ServerSocket serverSocket = null;
    String getRequest;
    String[] reqParameters = new String[7];
    WaitForConnectionTask waitForConnectionTask;
    static ConnectTask connectTask;

    boolean handshake = false;
    boolean hTaskRunning = false;
    String dataToSend;

    static ArrayList<Node> nodes = new ArrayList<Node>();
    static Iterator iterator;

    static float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        density = this.getResources().getDisplayMetrics().density;
        locationManager = (LocationManager)getApplicationContext().getSystemService(getApplicationContext().LOCATION_SERVICE);
        locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        wifiIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);

        settings = findViewById(R.id.setting);
        wifiStat = findViewById(R.id.wifiStat);
        refresh = findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handshakeTry();
            }
        });

        wifiStat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(wifiIntent);
            }
        });

        getLocationAccess();
        Log.d("MSG", "Location Done");

        grid  = findViewById(R.id.grid);
        adapter = new CustomAdapter(MainActivity.this, nodes);
        grid.setAdapter(adapter);

        metrics = getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;

        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Dialog dialog = new Dialog(MainActivity.this);
                dialog.setContentView(R.layout.settings);
                Button saveBtn = dialog.findViewById(R.id.saveBtn);
                Button discardBtn = dialog.findViewById(R.id.discardBtn);

                saveBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditText temp = dialog.findViewById(R.id.ssid);
                        String ssid = temp.getText().toString();
                        temp = dialog.findViewById(R.id.password);
                        String password = temp.getText().toString();

                        dialog.dismiss();

                        String dataToSend = "client@app$action@apconfig$" + ssid + "$" + password + "$";
                        new ConnectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, dataToSend);
                    }
                });

                discardBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                dialog.getWindow().setLayout(7*(width/8), 5*(height/6));
                dialog.show();
            }
        });

        handshakeTry();
    }

    void getNodeList(){
        nodes.clear();

        Log.d("MSG2", "Nodes Cleared, size: " + nodes.size());
        Node.nodeCount = 0;
        printNodeList();
        adapter.notifyDataSetChanged();
        Log.d("MSG2", "Asking for NODE lIST");
        dataToSend = "client@app$action@getnodelist$0$" + id + "$APP$1$0$";
        new ConnectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, dataToSend);
        Log.d("MSG2", "Connect called");

    }

    void handshakeTry(){
        Log.d("MSG2", "Trying handshake");
        handshake = false;
        hTaskRunning = true;
        pauseWaitTask = true;
        new HandshakeTask().execute();
    }

    class HandshakeTask extends AsyncTask<Void, Void, Void>{

        HttpURLConnection con;
        URL url;
        String data = "client@app$action@config$0$0$APP$0$0$";
        Socket socket = null;
        ServerSocket serverSocket = null;
        private ProgressDialog progressDialog;
        boolean ss = false;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage("Connecting....");
            progressDialog.show();
            Log.d("MSG2", "Dialog started");
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
                    serverSocket = new ServerSocket(8080);
                    ss = true;
                    serverSocket.setSoTimeout(10000);
                    Log.d("MSG2", "Socket Open");
                    socket = serverSocket.accept();
                    Log.d("MSG2", "Connected");
                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    getRequest = br.readLine();
                    PrintStream ps = new PrintStream(socket.getOutputStream());
                    ps.println("HTTP/1.1 200 OK\n");
                    ps.close();
                    br.close();
                    socket.close();
                    serverSocket.close();
                    Log.d("MSG2", "here");
                    int i = getRequest.indexOf("/message?data=");
                    if(i>0){
                        Log.d("MSG2", "here1");
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
                try {
                    if(ss){
                        serverSocket.close();
                        Log.d("MSG2", "Failed open server socket closed");
                    }

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }finally{
                hTaskRunning = false;
                Log.d("MSG2", "HTaskRunning: " + hTaskRunning);
            }
            Log.d("MSG2", "Here");
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            if(!handshake){
                Log.d("MSG2", "Failed handshake");
                wifiStat.setImageResource(R.drawable.wifiof24);
                nodes.clear();
                adapter.notifyDataSetChanged();
            }else{
                wifiStat.setImageResource(R.drawable.wifion24);
                Log.d("MSG2", "Success handshake");
                mustStopSSIDCheck = false;
                //start ssidcheckthread.
                new KeepCheckingSSID().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                waitForConnection();
                //refresh node list
                getNodeList();
            }
        }
    }

    static class ConnectTask extends AsyncTask<String, Void, Void>{

        HttpURLConnection con;
        URL url;
        String data;

        @Override
        protected Void doInBackground(String... strings) {
            Log.d("MSG2", "In Connect");
            data = strings[0];
            connectionSuccess = false;
            while(!connectionSuccess){
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
                    Log.d("MSG2", "Connect E: " + e.getMessage() + " CS: " + connectionSuccess);
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            if(connectionSuccess){
                connectionSuccess = false;
            }
        }
    }

    void waitForConnection(){
        pauseWaitTask = false;
        waitForConnectionTask = new WaitForConnectionTask();
        waitForConnectionTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    class WaitForConnectionTask extends AsyncTask<Void, Void, Void>{

        Socket socket;
        InetAddress inetAddress;
        int port = 8080;
        BufferedReader br;
        PrintStream ps;
        boolean decode = false;

        @Override
        protected Void doInBackground(Void... voids) {
            try{
                inetAddress = InetAddress.getLocalHost();
                serverSocket = new ServerSocket(port);
                serverSocket.setSoTimeout(1000);
                Log.d("MSG2", "Wait Started");
                while(!pauseWaitTask){
                    try{
                        socket = serverSocket.accept();
                        Log.d("MSG2", "Someone Connected");
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
                            decode = true;
                            pauseWaitTask = true;
                        }else{
                            Log.d("MSG2", "Message Body Invalid");
                        }
                    }catch(Exception e){
                       // Log.d("MSG2", "Wait Inner E: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                Log.d("MSG2", "Wait stopped");
                serverSocket.close();
            }catch (Exception e){
                Log.d("MSG2", "Wait E: " + e.getMessage());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            Log.d("MSG2", "Decode, handshake: " + decode + ", " + handshake);
            if(handshake && decode) {
                new DecodeParamaters().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                waitForConnection();
            }

        }
    }

    static void printNodeList(){
        iterator = nodes.iterator();
        Log.d("MSG2", "-----------------------------------------------");
        Log.d("MSG2", "ARRAYList Size: " + nodes.size());
        Log.d("MSG2", "ID Name    Cstat RStat Type Actions");
        while(iterator.hasNext()){
            Node temp = (Node)iterator.next();
            Log.d("MSG2", temp.nodeId + " " + temp.nodeName + " " + temp.conStat + " " + temp.rStat + " " + temp.type);
            if(temp.type == 3){
                for(int i=0; i<temp.irActions.size(); i++){
                    Log.d("MSG2", temp.irActions.get(i));
                }
            }
        }
        Log.d("MSG2", "-----------------------------------------------");
    }

    boolean isPresent(int id){
        iterator = nodes.iterator();
        while(iterator.hasNext()){
            Node temp = (Node)iterator.next();
            if(temp.nodeId == id)
                return true;
        }
        return false;
    }

    public static Node getNodeObject(int id){
        iterator = nodes.iterator();
        while(iterator.hasNext()){
            Node temp = (Node)iterator.next();
            if(temp.nodeId == id)
                return temp;
        }
        return null;
    }

    static void sendNodeStat(Node node){
        int r = 0, c = 0;

        if(node.conStat)
            c = 1;
        if(node.rStat)
            r = 1;
        String name = node.nodeName;
        if(node.type == 3){
            if(node.irActions!=null){
                for(String temp : node.irActions){
                    name += "_" + temp;
                }
                name += "_";
            }
        }
        String dataToSend = "client@app$action@stat$0$" + node.nodeId + "$" + name + "$" + c + "$" + r + "$";
        Log.d("MSG2", "Sendig Node STat");
        connectTask = (ConnectTask) new ConnectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, dataToSend);
    }

    void setNodeStat(Node node){
        for(int i=0; i<Node.nodeCount; i++){
            if(nodes.get(i).nodeId == node.nodeId){
                nodes.set(i, node);
                break;
            }
        }
        printNodeList();
    }



    class DecodeParamaters extends AsyncTask<Void, Void, Void>{
        boolean change = false;
        Node newNode = null;
        @Override
        protected Void doInBackground(Void... voids) {
            Log.d("MSG2", "In Decode Parameters");
            if(reqParameters[1].equals("action@stat")){
                boolean c = false, r = false;
                if(Integer.parseInt(reqParameters[5]) == 1)
                    c = true;
                if(Integer.parseInt(reqParameters[6]) == 1)
                    r = true;
                int type = Integer.parseInt((reqParameters[2]));
                if(type==2){
                    newNode = new Node(reqParameters[4], Integer.parseInt(reqParameters[3]), type, c, r);
                }else if(type==3){
                    String name = reqParameters[4];
                    int i = name.indexOf("_");
                    Log.d("MSG2", "i: " + i);
                    String irNames[] = null;
                    if(i!=-1){
                        Log.d("MSG2", "IN");
                        String temp = name;
                        name = name.substring(0, i);
                        i++;
                        temp = temp.substring(i);
                        Log.d("MSG2", "Temp: " + temp);
                        irNames = temp.split("_");
                    }
                    Log.d("MSG2", "here");
                    newNode = new Node(name, Integer.parseInt(reqParameters[3]), type, c, r, irNames);
                    Log.d("MSG2", "Decode Complete");
                }

                Log.d("MSG2", "incoming Node temp created");
                if(isPresent(Integer.parseInt(reqParameters[3]))){
                    Log.d("MSG2", "Setting stat, node present");
                    if(!c){
                        Log.d("MSG2","Cstat 0, removing node");
                        Node temp = getNodeObject(newNode.nodeId);
                        nodes.remove(temp);
                        Node.nodeCount--;
                    }
                    else{
                        setNodeStat(newNode);
                    }
                }else{
                    if(c){
                        //Node Doesnt Exist
                        Log.d("MSG2", "its a new node");
                        nodes.add(newNode);
                        Log.d("MSG2", "New Node, added in DS");
                        Node.nodeCount++;
                    }

                }
                printNodeList();
                change = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            if(change)
                adapter.notifyDataSetChanged();
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
                        Log.d("MSG2", "SSID Incorrect");
                        mustStopSSIDCheck = true;
                        handshake = false;
                    }else{
                       // Log.d("MSG2", "Correct SSID");
                    }
                }else{
                    Log.d("MSG2", "SSID Incorrect");
                    handshake = false;
                    mustStopSSIDCheck = true;
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            if(!handshake) {
                pauseWaitTask = true;
                //showDialog();
                wifiStat.setImageResource(R.drawable.wifiof24);
                nodes.clear();
                adapter.notifyDataSetChanged();
            }
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


    @Override
    protected void onRestart(){
        super.onRestart();
        Log.d("MSG@", "Restarted");
        getLocationAccess();
    }

    @Override
    protected  void onDestroy(){
        super.onDestroy();
        pauseWaitTask = true;
        mustStopSSIDCheck = true;
        //To stop any ConnectTask if active.
        connectionSuccess = true;
        Log.d("MSG2", "Destroyed");
    }
}

class Node{
    public static int nodeCount;

    String nodeName;
    boolean rStat, conStat;
    int nodeId;
    int type;
    ArrayList<String> irActions;

    Node(String name, int id, int type, boolean conStat, boolean rStat){
        nodeName = name;
        this.nodeId = id;
        this.type = type;
        this.rStat = rStat;
        this.conStat = conStat;
    }

    Node(String name, int id, int type, boolean conStat, boolean rStat, String[] irNames){
        nodeName = name;
        this.nodeId = id;
        this.type = type;
        this.rStat = rStat;
        this.conStat = conStat;

        irActions = new ArrayList<String>();
        Log.d("MSg2", "C1");
        if(irNames!=null){
            for(String temp : irNames){
                irActions.add(temp);
            }
            Log.d("MSG2", "Node Created");
        }

    }

    Node(Node node){
        this.irActions = node.irActions;
        this.conStat = node.conStat;
        this.nodeName = node.nodeName;
        this.nodeId = node.nodeId;
        this.type = node.type;
        this.rStat = node.rStat;
    }
}

class CustomAdapter extends BaseAdapter{
    ArrayList<Node> nodeList;
    private Context context;

    CustomAdapter(Context context, ArrayList<Node> nodeList){
        this.context = context;
        this.nodeList = nodeList;
    }

    @Override
    public int getCount() {
        return nodeList.size();
    }

    @Override
    public Object getItem(int position) {
        return nodeList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View gridItem;
        if(convertView == null){
            gridItem = inflater.inflate(R.layout.item, null);
        }else{
            gridItem = convertView;
        }
        final Node nodeObject = nodeList.get(position);
        gridItem.setTag("Node:" + nodeList.get(position).nodeId);
        gridItem.setBackgroundResource(R.drawable.greyborder);
        LinearLayout tapMe = gridItem.findViewById(R.id.tapMe);
        tapMe.setTag("NodeTapMe:" + nodeList.get(position).nodeId);
        TextView head = tapMe.findViewById(R.id.head);
        head.setText(nodeObject.nodeName);
        head.setBackgroundColor(Color.LTGRAY);
        ImageView image = tapMe.findViewById(R.id.imageStat);
        LinearLayout action = gridItem.findViewById(R.id.actions);
        if(nodeObject.type==2){
            Log.d("MSG2", "NODE Type 2: " + nodeObject.nodeName);
            if(nodeObject.rStat)
                image.setImageResource(R.drawable.on128);
            else
                image.setImageResource(R.drawable.off128);

            tapMe.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                String tag = v.getTag().toString();
                tag = tag.substring(10);
                int id = Integer.parseInt(tag);
                Node temp = MainActivity.getNodeObject(id);
                if(temp.rStat){
                    temp.rStat = false;
                }else{
                    temp.rStat = true;
                }
                notifyDataSetChanged();
                MainActivity.sendNodeStat(temp);
                }
            });
            image.setVisibility(View.VISIBLE);
            action.setVisibility(View.GONE);
        }else if(nodeObject.type == 3){
            Log.d("MSG2", "NODE Type 3: " + nodeObject.nodeName);

            int n = nodeObject.irActions.size();
            int mark = 0;
            while(n>0){
                mark++;
                n -= 2;
            }
            Log.d("MSG2", "mark: " + mark);
            int height = (int)MainActivity.density * (105);
            ViewGroup.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height*mark);
            action.setLayoutParams(params);
            image.setVisibility(View.GONE);
            action.setVisibility(View.VISIBLE);
            CustomAdapterIR adapterIR = new CustomAdapterIR(context, nodeObject.irActions, nodeObject);
            GridView innerGrid = action.findViewById(R.id.innerGrid);
            innerGrid.setAdapter(adapterIR);
        }

        tapMe.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showNodeConfigDialog(v);
                return true;
            }
        });

        return gridItem;
    }

    public void showNodeConfigDialog(View v){
        String tag = v.getTag().toString();
        tag = tag.substring(10);
        int id = Integer.parseInt(tag);
        final Node nodeObject = MainActivity.getNodeObject(id);

        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.nodeconfig);
        dialog.setTitle("Node Configuration");
        final Spinner spinner = dialog.findViewById(R.id.nodeNameSpinner);

        if(nodeObject.nodeName.equals("Light"))
            spinner.setSelection(0);
        else if(nodeObject.nodeName.equals("Fan"))
            spinner.setSelection(1);
        else if(nodeObject.nodeName.equals("TV"))
            spinner.setSelection(2);
        else
            spinner.setSelection(3);

        Button addAction = dialog.findViewById(R.id.addaction);
        if(nodeObject.type == 3){
            final Node temp = new Node(nodeObject);
            TextView irsteps = dialog.findViewById(R.id.irsteps);
            irsteps.setVisibility(View.VISIBLE);
            GridView irActionEditGrid = dialog.findViewById(R.id.editIRGrid);
            final CustomAdapterIREditor adapterIREditor = new CustomAdapterIREditor(context, temp.irActions, temp);
            irActionEditGrid.setAdapter(adapterIREditor);

            addAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String newAction = "(New)";
                    temp.irActions.add(newAction);
                    adapterIREditor.notifyDataSetChanged();
                }
            });
        }else{
            addAction.setVisibility(View.GONE);
        }
        Button saveBtn = dialog.findViewById(R.id.saveBtn);
        Button discardBtn = dialog.findViewById(R.id.discardBtn);

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(spinner.getSelectedItemPosition() == 3)
                    nodeObject.nodeName = "Node";
                else
                    nodeObject.nodeName = spinner.getSelectedItem().toString();
                dialog.dismiss();
                MainActivity.adapter.notifyDataSetChanged();
                MainActivity.sendNodeStat(nodeObject);
            }
        });

        discardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.getWindow().setLayout(7*(MainActivity.width/8), 5*(MainActivity.height/6));
        dialog.show();
    }
}

class CustomAdapterIR extends BaseAdapter{
    ArrayList<String> irActions;
    private Context context;
    Node nodeObject;

    CustomAdapterIR(Context context, ArrayList<String> irActions, Node nodeObject){
        this.context = context;
        this.irActions = irActions;
        this.nodeObject = nodeObject;
    }

    @Override
    public int getCount() {
        return irActions.size();
    }

    @Override
    public Object getItem(int position) {
        return irActions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View IRGridItem;
        if(convertView == null){
            IRGridItem = inflater.inflate(R.layout.iractionlayout, null);
        }else{
            IRGridItem = convertView;
        }
        //IRGridItem.setTag(nodeObject.nodeName + ":" + irActions.get(position));
        TextView textView = IRGridItem.findViewById(R.id.actionText);
        textView.setText(irActions.get(position));
        //To improve look, you can add button instead of textview and apply onclick event on that
        IRGridItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;
                message = "client@app$action@task$0$" + nodeObject.nodeId + "$" + nodeObject.nodeName + "$" + nodeObject.conStat + "$" + irActions.get(position) + "$";
                new MainActivity.ConnectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
            }
        });
        return IRGridItem;
    }
}

class CustomAdapterIREditor extends BaseAdapter{
    ArrayList<String> irActions;
    private Context context;
    Node nodeObject;

    CustomAdapterIREditor(Context context, ArrayList<String> irActions, Node nodeObject){
        this.context = context;
        this.irActions = irActions;
        this.nodeObject = nodeObject;
    }

    @Override
    public int getCount() {
        return irActions.size();
    }

    @Override
    public Object getItem(int position) {
        return irActions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        String action = irActions.get(position);
        Log.d("MSG2", "For: " + irActions.get(position));
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View IREditorGridItem;
        if(action.equals("(New)")){
            Log.d("MSG2", "new");
            IREditorGridItem = inflater.inflate(R.layout.iraddaction, null);
            final EditText actionName = IREditorGridItem.findViewById(R.id.actionName);
            actionName.setHint(irActions.get(position));
            Log.d("MSG@", "Edit text ");
            Button record = IREditorGridItem.findViewById(R.id.recordIr);
            Button save = IREditorGridItem.findViewById(R.id.saveIr);
            Log.d("MSG@", "TBN");
            record.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String message = "client@app$action@recordIR$0$" + nodeObject.nodeId + "$" + nodeObject.nodeName + "$" + nodeObject.conStat + "$" + irActions.get(position) + "$";
                    new MainActivity.ConnectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
                }
            });

            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    nodeObject.irActions.set(position, actionName.getText().toString());
                    irActions.set(position, actionName.getText().toString());

                    Log.d("MSG2", "NODE OBJE After");
                    if(nodeObject.type == 3){
                        for(int i=0; i<nodeObject.irActions.size(); i++){
                            Log.d("MSG2", nodeObject.irActions.get(i));
                        }
                    }

                    Node original = MainActivity.getNodeObject(nodeObject.nodeId);
                    original = nodeObject;
                    Log.d("MSG2", "OG OBJE After");
                    if(original.type == 3){
                        for(int i=0; i<original.irActions.size(); i++){
                            Log.d("MSG2", original.irActions.get(i));
                        }
                    }
                    notifyDataSetChanged();
                    String message = "client@app$action@saveIR$0$" + nodeObject.nodeId + "$" + nodeObject.nodeName + "$" + nodeObject.conStat + "$" + irActions.get(position) + "$";
                    new MainActivity.ConnectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
                }
            });
        }else{
            Log.d("MSG2", "old");
            IREditorGridItem = (View) inflater.inflate(R.layout.irshow, null);
            final TextView actionName = IREditorGridItem.findViewById(R.id.actionName);
            actionName.setText(irActions.get(position));
        }
        ImageButton remove = IREditorGridItem.findViewById(R.id.removeAction);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "client@app$action@remove$0$" + nodeObject.nodeId + "$" + nodeObject.nodeName + "$" + nodeObject.conStat + "$" + irActions.get(position) + "$";
                nodeObject.irActions.remove(irActions.get(position));
                Log.d("MSG2", "Removed from node obj, irac:" + irActions.size());
                //irActions.remove(irActions.get(position));

                Node original = MainActivity.getNodeObject(nodeObject.nodeId);
                original = nodeObject;
                notifyDataSetChanged();

                new MainActivity.ConnectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
            }
        });
        Log.d("MSg2", "Temp OBJEct: " + nodeObject.irActions.size());
        return IREditorGridItem;
    }
}

/*private void showDialog(){
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
    }*/
