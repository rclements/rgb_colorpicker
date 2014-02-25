package com.example.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.AsyncTask;
import android.widget.Button;

import com.commonsware.cwac.colormixer.ColorMixer;
import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangFloat;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.ericsson.otp.erlang.OtpMbox;
import com.ericsson.otp.erlang.OtpNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity {
    float mRed   = 0;
    float mGreen = 0;
    float mBlue  = 0;

    static final String TAG = "RGBColorPicker";
    static final String COOKIE = "test";
    static Context context;
    static OtpNode self;
    static OtpMbox mbox;

    private boolean mIsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MainActivity.context = getApplicationContext();

        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    public float getMapped(int val) {
        float newVal = Float.valueOf(val) / Float.valueOf(255);
        Log.d("UI", "getMapped: " + Float.toString(newVal));
        return newVal;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            ColorMixer colors = (ColorMixer) rootView.findViewById(R.id.mixer);

            TimerTask task = new TimerTask() {
                public void run(){
                    if(mIsReady){
                        RGBLedSetter task = new RGBLedSetter();
                        task.execute();
                    }
                }
            };
            Timer timer = new Timer();
            timer.schedule(task, 0, 100);

            colors.setOnColorChangedListener(new ColorMixer.OnColorChangedListener() {
                @Override
                public void onColorChange(int argb) {
                    int r = Color.red(argb);
                    int g = Color.green(argb);
                    int b = Color.blue(argb);
                    mRed = getMapped(r);
                    mGreen = getMapped(g);
                    mBlue = getMapped(b);
                }
            });

            final Button copyButton = (Button) rootView.findViewById(R.id.copyButton);
            final Button launchErlangButton = (Button) rootView.findViewById(R.id.launchErlangButton);

            copyButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    copyErlangOntoFs();
                    makeExecutable("/erlang/bin/epmd");
                    makeExecutable("/erlang/bin/erl");
                }
            });

            launchErlangButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchEpmd();
                    launchErlangNode();
                    mIsReady = true;
                }
            });

            return rootView;
        }


        public void copyErlangOntoFs() {
            Log.d(TAG, "copyErlangOntoFs start");

            InputStream erlangZipFileInputStream = null;
            try {
                erlangZipFileInputStream = getActivity().getApplicationContext().getAssets().open("erlang_R16B.zip");
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, erlangZipFileInputStream.toString());
            Log.d(TAG, MainActivity.context.getFilesDir().getPath());
            Decompress unzipper = new Decompress(erlangZipFileInputStream, MainActivity.context.getFilesDir().getPath() + "/");
            unzipper.unzip();

            Log.d(TAG, "copyErlangOntoFs done");
        }

        public void makeExecutable(String path) {
            this.doCommand("/system/bin/chmod 777 " + MainActivity.context.getFilesDir().getPath() + path);
        }

        public void doCommand(String command) {
            try {
                // Executes the command.
                Process process = Runtime.getRuntime().exec(command);

                // Reads stdout.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                int read;
                char[] buffer = new char[4096];
                StringBuffer output = new StringBuffer();
                while ((read = reader.read(buffer)) > 0) {
                    output.append(buffer, 0, read);
                }
                reader.close();

                // Waits for the command to finish.
                process.waitFor();

                // send output to the log
                Log.d(TAG, output.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public void launchEpmd() {
            this.doCommand(MainActivity.context.getFilesDir().getPath() + "/erlang/bin/epmd -daemon");
        }

        public void launchErlangNode() {
            String ip = Utils.getIPAddress(true);
            this.doCommand(MainActivity.context.getFilesDir().getPath() + "/erlang/bin/erl -name foo@" + ip + " -setcookie " + COOKIE);
        }

        public void listFiles() {
            Log.d(TAG, MainActivity.context.getFilesDir().getPath());
            this.doCommand("/system/bin/ls -al " + MainActivity.context.getFilesDir().getPath());
        }
    }

    public class RGBLedSetter extends AsyncTask<Object, Void, String> {
        final String remoteNodeName = "server@192.168.1.10";

        @Override
        protected String doInBackground(Object... arg0) {
            prepareNode();
            updateLed();
            return "whatevs...";
        }

        public void prepareNode(){
            if(self == null){
                try {
                    self = new OtpNode("mynode", COOKIE);
                    mbox = self.createMbox("rgbcolorpicker");
                    if (self.ping(remoteNodeName, 2000)) {
                        System.out.println("remote is up");
                    } else {
                        System.out.println("remote is not up");
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected void onPostExecute(String result) {
            //Log.e(TAG, "onPostExecute");
        }

        public void updateLed(){
            castUpdate(mRed, mGreen, mBlue);
            castBlast();
        }

        public void castUpdate(Float red, Float green, Float blue){
            castValueUpdate("red", red);
            castValueUpdate("green", green);
            castValueUpdate("blue", blue);
        }

        public void castValueUpdate(String color, Float value){
            OtpErlangObject[] message = new OtpErlangObject[2];
            message[0] = new OtpErlangAtom(color);
            message[1] = new OtpErlangFloat(value);

            cast(new OtpErlangTuple(message));
        }

        public void castBlast(){
            cast(new OtpErlangAtom("blast"));
        }

        public void cast(OtpErlangObject message){
            //Log.e(TAG, "updateLed");
            OtpErlangObject[] castMsg = new OtpErlangObject[2];
            castMsg[0] = new OtpErlangAtom("$gen_cast");
            castMsg[1] = message;

            mbox.send("rgbled", remoteNodeName, new OtpErlangTuple(castMsg));
            Log.d(TAG, "cast completed");
        }
    }


    public class Pinger extends AsyncTask<Object, Void, String> {

        @Override
        protected String doInBackground(Object... params) {
            String remoteNodeName = (String) params[0];
            prepareNode(remoteNodeName);
            ping(remoteNodeName);
            return "k...";
        }

        public void prepareNode(String remoteNodeName){
            if(self == null){
                try {
                    self = new OtpNode("mynode", COOKIE);
                    mbox = self.createMbox("rgbcolorpicker");
                    if (self.ping(remoteNodeName, 2000)) {
                        System.out.println("remote is up");
                    } else {
                        System.out.println("remote is not up");
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void ping(String remoteNodeName){
            Log.d(TAG, "pinging " + remoteNodeName);

            OtpErlangObject[] msg = new OtpErlangObject[2];
            msg[0] = mbox.self();
            msg[1] = new OtpErlangAtom("ping");
            OtpErlangTuple tuple = new OtpErlangTuple(msg);
            mbox.send("pong", remoteNodeName, tuple);
            Log.d(TAG, "pinging complete");
        }

    }
}

