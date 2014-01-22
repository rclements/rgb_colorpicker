package com.example.app;

import android.annotation.TargetApi;
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
import android.widget.Toast;

import com.commonsware.cwac.colormixer.ColorMixer;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity {
    private TCPClient mTcpClient;

    float mRed   = 0;
    float mGreen = 0;
    float mBlue  = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

            new connectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            TimerTask task = new TimerTask() {
                public void run(){
                    RGBLedSetter task = new RGBLedSetter();
                    task.execute();
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

            return rootView;
        }
    }

    public class connectTask extends AsyncTask<String,String,TCPClient> {

        @Override
        protected TCPClient doInBackground(String... message) {
            //we create a TCPClient object and
            mTcpClient = new TCPClient(new TCPClient.OnMessageReceived() {
                @Override
                //Toast.makeText(getApplicationContext(), "TCP CLIENT", Toast.LENGTH_SHORT).show();
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                }
            });
            mTcpClient.run();

            return null;
        }
    }

    public class RGBLedSetter extends AsyncTask<Object, Void, String> {
        @Override
        protected String doInBackground(Object... arg0) {
            if(mTcpClient != null){
                String message = getMessage();
                Log.e("RGBLedSetter", message);
                mTcpClient.sendMessage(message);
            }
            return "nope";
        }

        @Override
        protected void onPostExecute(String result) {
            Log.e("RGBLedSetter", "onPostExecute");
        }

        protected String getMessage() {
          return "R" + Float.toString(mRed) + "G" + Float.toString(mGreen) + "B" + Float.toString(mBlue) + "\n";
        }
    }
}
