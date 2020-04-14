package com.example.ha_andriod;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Reminder extends AppCompatActivity {
    JSONArray reminders = new JSONArray();
    int reminderPort = 8081;
    boolean pauseWaitTask = true;
    FloatingActionButton addRoutine;
    TextView noremindertext;
    GridView reminderGrid;
    CustomAdapterReminder adapterReminder;
    int currentApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder);
        setTitle("Reminders");
        currentApi = Build.VERSION.SDK_INT;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        addRoutine = findViewById(R.id.addRoutine);
        noremindertext = findViewById(R.id.noremindertext);
        reminderGrid = findViewById(R.id.remindergrid);
        requestReminderList();
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

    void requestReminderList(){
        String data = "client@app$action@reminderlist$";
        Routines.RequestParameters param = new Routines.RequestParameters(data);
        new Routines.RequestGetOrPost().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, param);
        startWaitTask();
    }

    void startWaitTask(){
        pauseWaitTask = false;
        new WaitForReminderListTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
        requestReminderList();
        Log.d("MSG2", "ResumedRoutines");
    }

    class WaitForReminderListTask extends AsyncTask<Void, Void, Void>{

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
                serverSocket = new ServerSocket(reminderPort);
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
                        reminders = new JSONArray(jsonData);
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
            if(reminders.length() != 0){
                noremindertext.setVisibility(View.GONE);
                reminderGrid.setVisibility(View.VISIBLE);
            }else{
                reminderGrid.setVisibility(View.GONE);
            }
            adapterReminder = new CustomAdapterReminder(Reminder.this, reminders);
            reminderGrid.setAdapter(adapterReminder);
            adapterReminder.notifyDataSetChanged();
        }
    }

    class CustomAdapterReminder extends BaseAdapter {
        JSONArray rArray;
        private Context context;

        CustomAdapterReminder(Context context, JSONArray rArray){
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

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ArrayList<Integer> days = new ArrayList<Integer>();
            for(int i=0; i<7; i++){
                days.add(i, 0);
            }
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View reminderGridItem;
            if(convertView == null){
                reminderGridItem = inflater.inflate(R.layout.remindergriditem, null);
            }else{
                reminderGridItem = convertView;
            }
            TextView head = reminderGridItem.findViewById(R.id.title);
            TextView time = reminderGridItem.findViewById(R.id.time);

            final TextView mon = reminderGridItem.findViewWithTag("monday");
            final TextView tue = reminderGridItem.findViewWithTag("tuesday");
            final TextView wed = reminderGridItem.findViewWithTag("wednesday");
            final TextView thur = reminderGridItem.findViewWithTag("thursday");
            final TextView fri = reminderGridItem.findViewWithTag("friday");
            final TextView sat = reminderGridItem.findViewWithTag("saturday");
            final TextView sun = reminderGridItem.findViewWithTag("sunday");
            final TextView monedit = reminderGridItem.findViewById(R.id.monedit);
            final TextView tueedit = reminderGridItem.findViewById(R.id.tueedit);
            final TextView wededit = reminderGridItem.findViewById(R.id.wededit);
            final TextView thuredit = reminderGridItem.findViewById(R.id.thuredit);
            final TextView friedit = reminderGridItem.findViewById(R.id.friedit);
            final TextView satedit = reminderGridItem.findViewById(R.id.satedit);
            final TextView sunedit = reminderGridItem.findViewById(R.id.sunedit);

            final TimePicker timep2 = reminderGridItem.findViewById(R.id.timep2);
            ImageButton delete = reminderGridItem.findViewById(R.id.deletereminder);
            final LinearLayout routineEdit = reminderGridItem.findViewById(R.id.routineedit);
            routineEdit.setVisibility(View.GONE);
            final EditText titleInput = reminderGridItem.findViewById(R.id.titleedit);
            ImageButton savereminder = reminderGridItem.findViewById(R.id.savereminder);
            titleInput.setText(head.getText());
            /*mon.setBackgroundResource(R.drawable.background);
            tue.setBackgroundResource(R.drawable.background);
            wed.setBackgroundResource(R.drawable.background);
            thur.setBackgroundResource(R.drawable.background);
            fri.setBackgroundResource(R.drawable.background);
            sat.setBackgroundResource(R.drawable.background);
            sun.setBackgroundResource(R.drawable.background);*/

            try {
                final JSONObject current = rArray.getJSONObject(position);
                String freq = current.get("frequency").toString();

                if(Integer.parseInt(String.valueOf(freq.toCharArray()[0])) == 1){
                    mon.setBackgroundResource(R.color.pink);
                }
                if(Integer.parseInt(String.valueOf(freq.toCharArray()[1])) == 1){

                    tue.setBackgroundResource(R.color.pink);
                }
                if(Integer.parseInt(String.valueOf(freq.toCharArray()[2])) == 1){
                    wed.setBackgroundResource(R.color.pink);
                }
                if(Integer.parseInt(String.valueOf(freq.toCharArray()[3])) == 1){
                    thur.setBackgroundResource(R.color.pink);
                }
                if(Integer.parseInt(String.valueOf(freq.toCharArray()[4])) == 1) {
                    fri.setBackgroundResource(R.color.pink);
                }
                if(Integer.parseInt(String.valueOf(freq.toCharArray()[5])) == 1){
                    sat.setBackgroundResource(R.color.pink);
                }
                if(Integer.parseInt(String.valueOf(freq.toCharArray()[6])) == 1){
                    sun.setBackgroundResource(R.color.pink);
                }
                head.setText(current.get("title").toString());
                time.setText(current.get("time").toString());

                String tempTime = current.get("time").toString();

                if(currentApi > Build.VERSION_CODES.LOLLIPOP_MR1){
                    timep2.setHour(Integer.parseInt(String.valueOf(tempTime.charAt(0)))*10 + Integer.parseInt(String.valueOf(tempTime.charAt(1))));
                    timep2.setMinute(Integer.parseInt(String.valueOf(tempTime.charAt(3)))*10 + Integer.parseInt(String.valueOf(tempTime.charAt(4))));
                }else{
                    timep2.setCurrentHour(Integer.parseInt(String.valueOf(tempTime.charAt(0)))*10 + Integer.parseInt(String.valueOf(tempTime.charAt(1))));
                    timep2.setCurrentMinute(Integer.parseInt(String.valueOf(tempTime.charAt(3)))*10 + Integer.parseInt(String.valueOf(tempTime.charAt(4))));
                }

                delete.setOnClickListener(new View.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(View v) {
                        rArray.remove(position);
                        if(rArray.length() == 0){
                            noremindertext.setVisibility(View.VISIBLE);
                            reminderGrid.setVisibility(View.GONE);
                        }
                        adapterReminder.notifyDataSetChanged();
                        //inform RPI

                    }
                });

                head.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(routineEdit.getVisibility() == View.GONE){
                            routineEdit.setVisibility(View.VISIBLE);
                        }else{
                            routineEdit.setVisibility(View.GONE);
                        }
                    }
                });
                monedit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(mon.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            mon.setBackgroundResource(R.color.pink);
                        }else{
                            mon.setBackgroundResource(R.drawable.background);
                        }

                    }
                });
                tueedit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(tueedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            tueedit.setBackgroundResource(R.color.pink);
                        }else{
                            tueedit.setBackgroundResource(R.drawable.background);
                        }

                    }
                });
                wededit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(wededit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            wededit.setBackgroundResource(R.color.pink);
                        }else{
                            wededit.setBackgroundResource(R.drawable.background);
                        }

                    }
                });
                thuredit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(thuredit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            thuredit.setBackgroundResource(R.color.pink);
                        }else{
                            thuredit.setBackgroundResource(R.drawable.background);
                        }

                    }
                });
                friedit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(friedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            friedit.setBackgroundResource(R.color.pink);
                        }else{
                            friedit.setBackgroundResource(R.drawable.background);
                        }

                    }
                });
                satedit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(satedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            satedit.setBackgroundResource(R.color.pink);
                        }else{
                            satedit.setBackgroundResource(R.drawable.background);
                        }

                    }
                });
                sunedit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(sunedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            sunedit.setBackgroundResource(R.color.pink);
                        }else{
                            sunedit.setBackgroundResource(R.drawable.background);
                        }

                    }
                });

                savereminder.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String temp = new String();
                        String newtitle = titleInput.getText().toString();
                        if(monedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            temp = "0";
                        }else{
                            temp = "1";
                        }
                        if(tueedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            temp += "0";
                        }else{
                            temp += "1";
                        }if(wededit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            temp += "0";
                        }else{
                            temp += "1";
                        }if(thuredit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            temp += "0";
                        }else{
                            temp += "1";
                        }if(friedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            temp += "0";
                        }else{
                            temp += "1";
                        }if(satedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            temp += "0";
                        }else{
                            temp += "1";
                        }if(sunedit.getBackground().getConstantState().equals(getResources().getDrawable(R.drawable.background).getConstantState())){
                            temp += "0";
                        }else{
                            temp += "1";
                        }
                        if(temp.equals("0000000") && newtitle.trim().length() == 0){
                            //incomplete info
                        }else{
                            int h, m;
                            if(currentApi > Build.VERSION_CODES.LOLLIPOP_MR1){
                                h = timep2.getHour();
                                m = timep2.getMinute();
                            }else{
                                h = timep2.getCurrentHour();
                                m = timep2.getCurrentMinute();
                            }
                            String hour = String.valueOf(h), min = String.valueOf(m);
                            if(h<0){
                                hour = "0" + hour;
                            }
                            if(m<0){
                                min = "0" + min;
                            }
                            String finalTime = hour + ":" + min;
                            try {
                                current.put("title", newtitle);
                                current.put("frequency", temp);
                                current.put("time", finalTime);
                                if(!current.has("id"))
                                    current.put("id", "0");
                                //send the packet
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }

            return reminderGridItem;
        }
    }
}
