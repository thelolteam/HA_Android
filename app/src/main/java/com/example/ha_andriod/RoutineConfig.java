package com.example.ha_andriod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.Toolbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;


public class RoutineConfig extends AppCompatActivity {

    int currentAPIVersion;
    JSONObject routine = new JSONObject();
    JSONObject actions = new JSONObject();
    JSONArray actionArray = new JSONArray();
    ArrayList<String> jsonKeys = new ArrayList<String>();
    static ArrayList<Node> nodes;
    TimePicker timep;
    EditText voicecommand;
    boolean newRoutine = false;
    GridView actiongrid;
    Button btn1, btn2, addaction;
    //Spinner actionsSpinner, targetSpinner;
    String[] actionList = {"Speak Temperature", "Turn On", "Turn Off", "IR Action"};

    //String[] targetList;
    ArrayList<String> targetList = new ArrayList<String>();
    static Iterator iterator;

    static ActionsCustomAdapter actionsCustomAdapter;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routine_config);
        setTitle("Configure Routine");

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        currentAPIVersion = Build.VERSION.SDK_INT;

        nodes = MainActivity.nodes;
        iterator = nodes.iterator();
        while(iterator.hasNext()){
            Node temp = (Node)iterator.next();
            targetList.add(temp.location + " " + temp.nodeName);
        }

        timep = findViewById(R.id.timepicker);
        voicecommand = findViewById(R.id.voicecommand);
        actiongrid = findViewById(R.id.actiongrid);
        btn1 = findViewById(R.id.btn1);
        btn2 = findViewById(R.id.btn2);
        addaction = findViewById(R.id.addactionbtn);

        actiongrid = findViewById(R.id.actiongrid);

        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    routine.put("type", "voice");
                    btn1.setBackgroundResource(R.drawable.whiteborder);
                    btn2.setBackgroundResource(R.drawable.background);
                    //btn2.setBackgroundColor(Color.WHITE);
                    voicecommand.setVisibility(View.VISIBLE);
                    timep.setVisibility(View.GONE);
                    voicecommand.setText("Voice Command");
                    routine.put("trigger", "X");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    routine.put("type", "schedule");
                    btn2.setBackgroundResource(R.drawable.whiteborder);
                    //btn1.setBackgroundColor(Color.WHITE);
                    btn1.setBackgroundResource(R.drawable.background);
                    voicecommand.setVisibility(View.GONE);
                    timep.setVisibility(View.VISIBLE);
                    final Calendar cal = Calendar.getInstance();
                    if(currentAPIVersion > Build.VERSION_CODES.LOLLIPOP_MR1){
                        timep.setHour(cal.get(Calendar.HOUR_OF_DAY));
                        timep.setMinute(cal.get(Calendar.MINUTE));
                    }else{
                        timep.setCurrentHour(cal.get(Calendar.HOUR_OF_DAY));
                        timep.setCurrentMinute(cal.get(Calendar.MINUTE));
                    }

                    routine.put("trigger", "XX:XX");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });

        addaction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Dialog dialog = new Dialog(RoutineConfig.this);
                dialog.setContentView(R.layout.addactionform);
                dialog.setTitle("Add Action");
                final Spinner actionspinner, targetspinner, irspinner;
                final EditText delayinput;
                Button saveAction;
                final TextView alertForm;
                alertForm = dialog.findViewById(R.id.alertform);
                actionspinner = dialog.findViewById(R.id.actionspinner);
                targetspinner = dialog.findViewById(R.id.targetspinner);
                irspinner = dialog.findViewById(R.id.irspinner);
                delayinput = dialog.findViewById(R.id.delay);
                saveAction = dialog.findViewById(R.id.saveactionbtn);

                final ArrayAdapter forName = new ArrayAdapter(RoutineConfig.this, android.R.layout.simple_spinner_item, actionList);
                forName.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
                actionspinner.setAdapter(forName);

                actionspinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if(position == 0){
                            targetspinner.setVisibility(View.INVISIBLE);
                            irspinner.setVisibility(View.INVISIBLE);
                        }
                        else{
                            ArrayAdapter forTarget = new ArrayAdapter(RoutineConfig.this, android.R.layout.simple_spinner_item, targetList);
                            forName.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
                            targetspinner.setAdapter(forTarget);

                            targetspinner.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        targetspinner.setVisibility(View.INVISIBLE);
                        irspinner.setVisibility(View.INVISIBLE);
                    }
                });

                targetspinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        iterator = nodes.iterator();
                        while(iterator.hasNext()){
                            Node tempNode = (Node)iterator.next();
                            if((tempNode.location + " " + tempNode.nodeName).equals(targetspinner.getSelectedItem().toString())){
                                if(tempNode.type == 2){
                                    irspinner.setVisibility(View.INVISIBLE);
                                }else{
                                    irspinner.setVisibility(View.VISIBLE);
                                    ArrayAdapter forIR = new ArrayAdapter(RoutineConfig.this, android.R.layout.simple_spinner_item, tempNode.irActions);
                                    forIR.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
                                    irspinner.setAdapter(forIR);
                                }
                                break;
                            }
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        irspinner.setVisibility(View.INVISIBLE);
                    }
                });

                saveAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        JSONObject newAction = new JSONObject(), inner = new JSONObject();
                        boolean ir = false;
                        boolean filled = true;
                        String action = new String(), target = new String(), irname, delay;
                        int selectedAction = actionspinner.getSelectedItemPosition();
                        if(selectedAction != -1){

                            if(selectedAction == 0){
                                action = "speakTemperature";
                            }else if(selectedAction == 1){
                                action = "turnon";
                            }else if(selectedAction == 2){
                                action = "turnoff";
                            }else if(selectedAction == 3){
                                action = "iraction";
                            }
                            delay = delayinput.getText().toString();
                            if(delay.trim().length() == 0){
                                delay = "0";
                            }
                            inner = new JSONObject();
                            try {
                                inner.put("delay", delay);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Log.d("MSG2", "Coudnt put delay in json");
                            }

                            if(selectedAction == 1 || selectedAction == 2 || selectedAction == 3){
                                if(targetspinner.getSelectedItemPosition() !=-1){
                                    target = targetspinner.getSelectedItem().toString();

                                    iterator = nodes.iterator();
                                    while(iterator.hasNext()){
                                        Node tempNode = (Node)iterator.next();
                                        if((tempNode.location + " " + tempNode.nodeName).equals(target)){
                                            try {
                                                inner.put("nodeLocation", tempNode.location);
                                                inner.put("nodeName", tempNode.nodeName);
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                                Log.d("MSG2", "Coudnt put name and location in json");
                                            }

                                            if(tempNode.type == 3){
                                                ir = true;

                                                if(irspinner.getSelectedItemPosition() != -1){

                                                    irname = irspinner.getSelectedItem().toString();
                                                    try {
                                                        inner.put("iraction", irname);
                                                    } catch (JSONException e) {
                                                        e.printStackTrace();
                                                        Log.d("MSG2", "Coudnt put iraction in json");
                                                    }
                                                }else{
                                                    filled = false;
                                                }
                                            }
                                            break;
                                        }
                                    }
                                }else{
                                    filled = false;
                                }

                            }
                            try {
                                newAction.put(action, inner);
                                Log.d("MSG2", "New ACTIONS: " + newAction.toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Log.d("MSG2", "Coudnt put Value for action details in json");
                            }
                        }else{
                            filled = false;
                        }

                        if(!filled){
                            alertForm.setText("Incomplete Information");
                            alertForm.setVisibility(View.VISIBLE);
                        }else{
                            alertForm.setVisibility(View.INVISIBLE);
                            //save in json
                            Log.d("MSg2", "FINAL JSON: " + newAction.toString());
                            try {
                                actions.put(action, inner);
                                actionArray.put(inner);
                                jsonKeys.add(action);
                                Log.d("MSG2", "Saved: " + actions.toString());
                                Log.d("MSG2", "KEYS: " + jsonKeys.toString());
                                Log.d("MSG2", "ACTIOn Array: " + actionArray.toString());
                                actiongrid.setVisibility(View.VISIBLE);
                                dialog.dismiss();
                                actionsCustomAdapter.notifyDataSetChanged();

                            } catch (JSONException e) {
                                e.printStackTrace();
                                Log.d("MSG2", "Final Addition Failed of json");
                            }
                        }

                    }

                });
                Log.d("MSG2", String.valueOf(MainActivity.height));
                dialog.getWindow().setLayout(9*(MainActivity.width/10), Math.max((MainActivity.height/3), 700));
                dialog.show();
            }
        });

        timep.setIs24HourView(true);
        String jsonString = getIntent().getStringExtra("routine");
        try {
            routine = new JSONObject(jsonString);

            try{
                actions = routine.getJSONObject("actions");
                Iterator<String> iter = actions.keys();
                while(iter.hasNext()){
                    String t = iter.next();
                    Log.d("MSG2", "Key: "+ t);
                    jsonKeys.add(t);

                    JSONObject temp = actions.getJSONObject(t);
                    actionArray.put(temp);
                }
                JSONObject turnon = actions.getJSONObject("turnon");
                Log.d("MSG2", "TurnON: " + turnon.getClass());
            }catch(Exception e){
                e.printStackTrace();
                Log.d("MSG2", "E: " + e.getMessage());
            }

            Log.d("MSG2", "ROutine: " + routine.toString());
            Log.d("MSG2", "Type: " + routine.get("type").toString());
            if(actions.length() == 0){
                actiongrid.setVisibility(View.GONE);
            }
            int id = Integer.parseInt(routine.get("id").toString());

            if(id==0)
                newRoutine = true;

            if(routine.get("type").equals("voice")){
                voicecommand.setVisibility(View.VISIBLE);
                timep.setVisibility(View.GONE);
                voicecommand.setText(routine.get("trigger").toString());
                btn1.setBackgroundResource(R.drawable.whiteborder);
                //btn2.setBackgroundColor(Color.WHITE);

            }else{
                String timeString = routine.get("trigger").toString();
                voicecommand.setVisibility(View.GONE);
                timep.setVisibility(View.VISIBLE);
                if(currentAPIVersion > Build.VERSION_CODES.LOLLIPOP_MR1){
                    timep.setHour((Integer.parseInt(String.valueOf(timeString.charAt(0))))* 10 + (Integer.parseInt(String.valueOf(timeString.charAt(1)))));
                    timep.setMinute((Integer.parseInt(String.valueOf(timeString.charAt(3))))* 10 + (Integer.parseInt(String.valueOf(timeString.charAt(4)))));
                }else{
                    timep.setCurrentHour((Integer.parseInt(String.valueOf(timeString.charAt(0))))* 10 + (Integer.parseInt(String.valueOf(timeString.charAt(1)))));
                    timep.setCurrentMinute((Integer.parseInt(String.valueOf(timeString.charAt(3))))* 10 + (Integer.parseInt(String.valueOf(timeString.charAt(4)))));
                }
                btn2.setBackgroundResource(R.drawable.whiteborder);
                //btn1.setBackgroundColor(Color.WHITE);
            }

            Log.d("MSG2", "Keys: " + jsonKeys);
            actionsCustomAdapter = new ActionsCustomAdapter(RoutineConfig.this, actionArray, jsonKeys);
            actiongrid.setAdapter(actionsCustomAdapter);

        } catch (JSONException e) {
            e.printStackTrace();
            Log.d("MSG2", "E: " + e.getMessage());
        }
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

            case R.id.saveRoutine:
                if(actions.length() == 0){
                    Toast.makeText(this, "No Actions Added", Toast.LENGTH_SHORT).show();
                }else{
                    new SaveRoutine().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.custom_title_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    class ActionsCustomAdapter extends BaseAdapter{
        Context context;
        JSONArray actions;
        ArrayList<String> keys;

        ActionsCustomAdapter(Context context, JSONArray actions, ArrayList<String> keys){
            this.context = context;
            this.actions = actions;
            this.keys = keys;
        }

        @Override
        public int getCount() {
            return keys.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            Log.d("MSG2", "Keys: " + keys);
            Log.d("MSG2", "Values: " + actions.toString());
            Log.d("MSG2", "Position: " + position);
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View actionitem;

            if(convertView == null){
                actionitem = inflater.inflate(R.layout.actionitem, null);
                Log.d("MSG2", "1: " + actionitem);
            }else{
                actionitem = convertView;
                Log.d("MSG2", "2: " + actionitem);
            }
            try {
                final JSONObject current = (JSONObject)actions.getJSONObject(position);
                final String currentKey = keys.get(position);

                TextView name, target, delay;
                name = actionitem.findViewById(R.id.actionname);

                target = actionitem.findViewById(R.id.actiontarget);
                delay = actionitem.findViewById(R.id.actiondelay);

                if(currentKey.equals("speakTemperature")){
                    name.setText("Speak Temperature");
                }else if(currentKey.equals("turnon")){
                    name.setText("Turn ON");
                }
                else if(currentKey.equals("turnoff")){
                    name.setText("Turn OFF");
                }else if(current.equals("iraction")){
                    name.setText("IR Action");
                }
                try{
                    target.setText(current.get("nodeLocation").toString() + " " + current.get("nodeName").toString());
                }catch(Exception e){
                    target.setText("");
                }

                delay.setText(current.get("delay").toString() + "s");

                Log.d("MSG2", "Cur Key: " + currentKey);
                Log.d("MSG2", "Cur Val;" + current);
                Log.d("MSG2", name.getText() + " " + target.getText() + " " + delay.getText());

                ImageButton up, down, remove;

                up = actionitem.findViewById(R.id.up);
                down = actionitem.findViewById(R.id.down);
                remove = actionitem.findViewById(R.id.remove);

                up.setClickable(true);
                down.setClickable(true);

                /*if(position == 0){
                    up.setClickable(false);
                }
                if(position == getCount()-1){
                    up.setClickable(true);
                }*/

                up.setOnClickListener(new View.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(View v) {
                        try {
                            if(position!=0){
                                JSONObject temp = actions.getJSONObject(position-1);
                                actionArray.put(position-1, current);
                                actionArray.put(position, temp);
                                jsonKeys.add(position-1, currentKey);
                                jsonKeys.remove(position+1);
                                actionsCustomAdapter.notifyDataSetChanged();
                                Log.d("MSG2", "UP ACTIONS: " + actionArray.toString());
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

                down.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            if(position != getCount()-1){
                                JSONObject temp = actions.getJSONObject(position+1);
                                actionArray.put(position+1, current);
                                actionArray.put(position, temp);
                                jsonKeys.add(position, jsonKeys.get(position+1));
                                jsonKeys.remove(position+2);
                                Log.d("MSG2", "Down ACTIONS: " + actionArray.toString());
                                actionsCustomAdapter.notifyDataSetChanged();
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

                remove.setOnClickListener(new View.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(View v) {
                        actions.remove(position);
                        jsonKeys.remove(position);
                        if(actions.length() == 0){
                            actiongrid.setVisibility(View.INVISIBLE);
                        }
                        actionsCustomAdapter.notifyDataSetChanged();
                        Log.d("MSG2", "removed");
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("MSG2", "Ex: " + e.getMessage());
            }

            return actionitem;
        }
    }

    class SaveRoutine extends AsyncTask<Void, Void, Void>{

        boolean success = false;
        int tries = 2;
        String parameter;
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        protected Void doInBackground(Void... voids) {
            parameter = "client@app$action@routine$";
            try {
                String trigger;
                if(routine.get("type").equals("voice"))
                    trigger = voicecommand.getText().toString();
                else{
                    if(currentAPIVersion > Build.VERSION_CODES.LOLLIPOP_MR1){
                        int hour = timep.getHour();
                        int min = timep.getMinute();
                        String h = String.valueOf(hour);
                        String m = String.valueOf(min);
                        if(hour < 10){
                            h = "0" + h;
                        }
                        if(min < 10){
                            m = "0" + m;
                        }
                        trigger = h + ":" + m;
                    }else{
                        int hour = timep.getCurrentHour();
                        int min = timep.getCurrentMinute();
                        String h = String.valueOf(hour);
                        String m = String.valueOf(min);
                        if(hour < 10){
                            h = "0" + h;
                        }
                        if(min < 10){
                            m = "0" + m;
                        }
                        trigger = h + ":" + m;
                    }
                }
                for(int i=0; i<jsonKeys.size(); i++)
                    actions.remove(jsonKeys.get(i));
                for(int i=0; i<actionArray.length(); i++){
                    Log.d("MSG2", "Key: " + jsonKeys.get(i));
                    actions.put(jsonKeys.get(i), actionArray.getJSONObject(i));
                    Log.d("MSG2", "Actions: " + actions.toString());
                }


                routine.put("trigger", trigger);
                routine.put("actions", actions);
                Log.d("MSG2", "ROUTINE: " + routine.toString());
                Log.d("MSG2", "Actions: " + actions.toString());
                while(!success && tries>0){
                    URL url = new URL("http://192.168.4.1:8080/message?data=client@app$action@routine$");
                    Log.d("MSG2", "URL: " + url);
                    HttpURLConnection con = (HttpURLConnection)url.openConnection();
                    con.setRequestMethod("POST");
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setDoOutput(true);
                    //String data = new String(routine.toString());
                    try(OutputStream os = con.getOutputStream()) {
                        byte[] input = routine.toString().getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }
                    int resCode = con.getResponseCode();
                    if(resCode == 200){
                        Log.d("MSG2", "Save Routine RES Code: " + resCode);
                        success = true;
                    }
                    tries--;
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("MSG2", "Save, put issue");
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voids){
            finish();
        }
    }

}

