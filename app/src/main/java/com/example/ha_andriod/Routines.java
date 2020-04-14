package com.example.ha_andriod;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Routines extends AppCompatActivity {

    static ArrayList<Node> nodes;
    static JSONArray routinesArray = new JSONArray();
    int routinePort = 8081;
    static boolean pauseWaitTask = false;
    static CustomAdapterRoutines adapterRoutines;
    GridView routineGrid;
    static TextView noroutinestext;
    FloatingActionButton addroutinebtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routines);
        setTitle("Routines");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        nodes = MainActivity.nodes;
        MainActivity.printNodeList();
        noroutinestext = findViewById(R.id.noroutinestext);
        addroutinebtn = findViewById(R.id.addRoutine);

        addroutinebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    String tempString = "{'id':'0', 'type':'voice', 'trigger':'Voice Command', 'actions':'{}'}";
                    JSONObject temp = new JSONObject(tempString);
                    Intent routineConfig = new Intent(Routines.this, RoutineConfig.class);
                    routineConfig.putExtra("routine", temp.toString());
                    startActivity(routineConfig);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        requestRoutineList();
        routineGrid = findViewById(R.id.routinegrid);
        Log.d("MSG2", "Length: " + routinesArray.length());
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    void printRoutineList(){
        Log.d("MSG2", "Length: " + routinesArray.length());
        for(int i=0; i<routinesArray.length(); i++){
            try {
                Log.d("MSG2", "Type: " + routinesArray.getClass());
                //JSONObject temp = (JSONObject)routinesArray.getJSONObject(i);
                //Log.d("MSG2", "Routine: " + temp.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("MSG2", "EP: " + e.getMessage());
            }
        }
    }

    void requestRoutineList(){
        String data = "client@app$action@routinelist$";
        RequestParameters param = new RequestParameters(data);
        new RequestGetOrPost().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, param);
        startWaitTask();
    }

    void startWaitTask(){
        pauseWaitTask = false;
        new WaitForRoutineListTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    static class RequestGetOrPost extends AsyncTask<RequestParameters, Void, Void>{
        HttpURLConnection con;
        URL url;
        String method = "GET";
        boolean connection = false;

        @Override
        protected Void doInBackground(RequestParameters... param) {
            if(param[0].post)
                method = "POST";
            Log.d("MSG2", "Method: " + method);

            while(!connection){
                try {
                    url = new URL("http://192.168.4.1:8080/message?data=" + param[0].data);
                    Log.d("MSG2", "URL: " + url);
                    con = (HttpURLConnection)url.openConnection();
                    con.setRequestMethod(method);
                    if(param[0].post){
                        Log.d("MSG2", "POST Req");
                        con.setDoOutput(true);
                        con.setRequestProperty("Content-Type", "application/json");
                        OutputStream os = con.getOutputStream();
                        byte[] input = param[0].routine.toString().getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }
                    int resCode = con.getResponseCode();
                    Log.d("MSG2", "Res Code For Req Task: " + resCode);
                    if(resCode == 200){
                        connection = true;
                    }
                    con.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    con.disconnect();
                }
            }
            return null;
        }

        @Override
        protected  void onPostExecute(Void voids){
        }
    }

    //-------------------------------------------------------------------------------------------------------------------
    class WaitForRoutineListTask extends AsyncTask<Void, Void, Void>{

        Socket socket;
        ServerSocket serverSocket;
        InetAddress inetAddress;
        BufferedReader br;
        PrintStream ps;
        boolean success = false;

        @Override
        protected Void doInBackground(Void... voids) {
            try{
                inetAddress = InetAddress.getLocalHost();
                serverSocket = new ServerSocket(routinePort);
                serverSocket.setSoTimeout(1000);
                Log.d("MSG2", "Wait Started: " + pauseWaitTask);
                while(!pauseWaitTask){
                    try{
                        Log.d("MSG2", "Started Wait");
                        socket = serverSocket.accept();
                        Log.d("MSG2", "Someone Connected");
                        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String temp, jsonData = new String();
                        while(!(temp = br.readLine()).equals("END")){
                            jsonData += temp;
                            Log.d("MSG2", temp);
                        }
                        int i = jsonData.indexOf("[");
                        jsonData = jsonData.substring(i);
                        routinesArray = new JSONArray(jsonData);
                        ps = new PrintStream(socket.getOutputStream());
                        ps.println("HTTP/1.1 200 OK\n");
                        ps.close();
                        br.close();
                        socket.close();
                        pauseWaitTask = true;
                        success = true;
                    }catch(Exception e){
                        e.printStackTrace();
                        Log.d("MSG2", "Execpeiton e:" + e.getMessage());
                    }
                }
                serverSocket.close();
            }catch (Exception e){
                Log.d("MSG2", "Wait E: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected  void onPostExecute(Void voids){
            //call routine designer;
            //adapter.notifyDataChange
            /*Log.d("MSG2", "Temp Array: " + tempArray.toString());
            Log.d("MSG2", "Length: " + routinesArray.length());
            Log.d("MSG2", "Test: " + adapterRoutines.getCount());
            for(int i=0; i<tempArray.length(); i++){
                try {
                    routinesArray.put(tempArray.getJSONObject(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }*/
            if(routinesArray.length() != 0){
                noroutinestext.setVisibility(View.GONE);
                routineGrid.setVisibility(View.VISIBLE);
            }else{
                routineGrid.setVisibility(View.GONE);
            }
            adapterRoutines = new CustomAdapterRoutines(Routines.this, routinesArray);
            routineGrid.setAdapter(adapterRoutines);
            printRoutineList();
            adapterRoutines.notifyDataSetChanged();
        }
    }
    //-------------------------------------------------------------------------------------------------------------------


    static class RequestParameters{
        String data;
        boolean post;
        JSONObject routine;

        RequestParameters(String data, JSONObject routine, boolean post){
            this.data = data;
            this.routine = routine;
            this.post = post;
        }
        RequestParameters(String data, JSONObject routine){
            this.data = data;
            this.routine = routine;
            this.post = false;
        }
        RequestParameters(String data){
            this.data = data;
            this.post = false;
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        pauseWaitTask = true;
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        pauseWaitTask = true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        requestRoutineList();
        Log.d("MSG2", "ResumedRoutines");
    }

    class CustomAdapterRoutines extends BaseAdapter{
        JSONArray rArray;
        private Context context;

        CustomAdapterRoutines(Context context, JSONArray rArray){
            this.context = context;
            this.rArray = rArray;
        }

        @Override
        public int getCount() {
            return rArray.length();
        }

        @Override
        public Object getItem(int position) {
            try {
                return rArray.getJSONObject(position);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View routineGridItem;
            if(convertView == null){
                routineGridItem = inflater.inflate(R.layout.routinegriditem, null);
            }else{
                routineGridItem = convertView;
            }
            final JSONObject current;
            try {
                Log.d("MSG2", "Pos: "+ position);
                current = (JSONObject)rArray.getJSONObject(position);
                JSONObject tempa = current.getJSONObject("actions");
                Log.d("MSG2", "Current: " + current.toString());
                Log.d("MSG2", "Actions: " + tempa.toString());
                ImageView triggerImage = routineGridItem.findViewById(R.id.triggerImage);
                TextView head = routineGridItem.findViewById(R.id.head);
                ImageButton remove = routineGridItem.findViewById(R.id.deleteBtn);
                try {
                    String h = current.get("trigger").toString();

                    if(current.get("type").equals("voice")){
                        head.setText("\"" + h + "\"");
                        triggerImage.setImageResource(R.drawable.whitemic24);
                    }else{
                        head.setText(h);
                        triggerImage.setImageResource(R.drawable.whiteclock24);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                head.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //launch next activityIn
                        Intent routineConfig = new Intent(Routines.this, RoutineConfig.class);
                        routineConfig.putExtra("routine", current.toString());
                        startActivity(routineConfig);
                    }
                });
                remove.setOnClickListener(new View.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(View v) {
                        //remove Routine and send notification to rpi
                        rArray.remove(position);
                        if(routinesArray.length() == 0){
                            noroutinestext.setVisibility(View.VISIBLE);
                            routineGrid.setVisibility(View.GONE);
                        }
                        adapterRoutines.notifyDataSetChanged();
                        try {
                            String data = "client@app$action@delroutine$" + current.get("id").toString() + "$";
                            RequestParameters param = new RequestParameters(data);
                            new RequestGetOrPost().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, param);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                });
                Log.d("MSG2", "For: " + head.getText().toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("MSG2", "Ex: " + e.getMessage());
            }

            return routineGridItem;
        }
    }

}
