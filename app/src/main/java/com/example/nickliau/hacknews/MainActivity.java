package com.example.nickliau.hacknews;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static String URL_IDS = "https://hacker-news.firebaseio.com/v0/topstories.json";
    private static String URL_ART_START = "https://hacker-news.firebaseio.com/v0/item/";
    private static String URL_ART_END = ".json";
    private static String URL_MAX_ID= "https://hacker-news.firebaseio.com/v0/maxitem.json";

    ListView listview;
    TextView textView;
    ArrayAdapter<String> adapter;
    ArrayList<String> titlelist, URLlist;

    SQLiteDatabase eventsDB;
    static String HACKER_NEWS_TABLE = "hacker_news_table";
    WebView webView;
    int server_max_id, local_max_id;
    Cursor cursor = null;
    boolean firsttime = true;
    boolean downloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        listview = (ListView) findViewById(R.id.listUrl);
        textView = (TextView) findViewById(R.id.ShowDownloadText);
        titlelist = new ArrayList<>();
        URLlist = new ArrayList<>();
        local_max_id = 0;

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, titlelist);
        listview.setAdapter(adapter);
        webView =  (WebView) findViewById(R.id.webView);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    webView.getSettings().setJavaScriptEnabled(true);
                    webView.setWebViewClient(new WebViewClient());
                    webView.loadUrl(URLlist.get(i));
                    webView.setVisibility(View.VISIBLE);
            }
        });

        if (OpenOrCreateDatabase()) {
            Log.e("Nick", "Create or open database Success");
            downloadRepeatedly();
        } else {
            Log.e("Nick", "Create database failed");
        }
    }

    private void downloadRepeatedly() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate( new TimerTask() {
            public void run() {
                Log.d ("Nick", "size of list " + titlelist.size());
                try{
                    new DownloadTask().execute();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 5000000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            //moveTaskToBack(true);
            webView.setVisibility(View.GONE);
            return false;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        eventsDB.close();
    }


    class DownloadTask extends AsyncTask<Void, String, Void> {
        String a , b;
        @Override
        protected Void doInBackground(Void... Params) {
            String resArray = "";
            String max_id = "";
            HttpURLConnection connection = null;
            InputStream in = null;
            InputStreamReader inReader;


            try {
                URL urlid = new URL(URL_IDS);
                URL maxid = new URL(URL_MAX_ID);
                //get max id
                connection = (HttpURLConnection)maxid.openConnection();
                in = connection.getInputStream();
                inReader = new InputStreamReader(in);
                int getid = inReader.read();
                char current;
                while (getid != -1) {
                    current = (char) getid;
                    max_id += current;

                    getid = inReader.read();
                }
                server_max_id = Integer.parseInt(max_id);

                if (server_max_id <= local_max_id) {
                    return null;
                }

                connection = (HttpURLConnection) urlid.openConnection();
                in = connection.getInputStream();
                inReader = new InputStreamReader(in);

                int data = inReader.read();
                while (data != -1) {
                    current = (char) data;
                    resArray += current;

                    data = inReader.read();
                }

                JSONArray jsonArray = new JSONArray(resArray);
                int index = 0;
                if (firsttime) {
                    int titleIndex = cursor.getColumnIndex("title");
                    int urlIndex = cursor.getColumnIndex("URL");

                    downloading = true;
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        a = cursor.getString(titleIndex);
                        b = cursor.getString(urlIndex);
                        titlelist.add("" + titlelist.size() + ". " + a);
                        URLlist.add(b);

                        index++;
                    }
                    downloading = false;
                    firsttime = false;
                }

                for (int i = 0; i < jsonArray.length(); i ++) {
                    String id = jsonArray.getString(i);
                    URL artURL = new URL(URL_ART_START + id + URL_ART_END);
                    connection = (HttpURLConnection) artURL.openConnection();
                    in = connection.getInputStream();
                    inReader = new InputStreamReader(in);
                    String jsonobjectstring = "";

                    data = inReader.read();
                    while (data != -1) {
                        current = (char) data;
                        jsonobjectstring += current;
                        data = inReader.read();
                    }

                    JSONObject jsonObject = new JSONObject(jsonobjectstring);

                    if (jsonObject.has("title") && jsonObject.has("url") ) {
                        a = jsonObject.getString("title");
                        b = jsonObject.getString("url");

                        if (local_max_id < jsonObject.getInt("id"))
                            local_max_id = jsonObject.getInt("id");

                        if (InsertDataToDatabase(jsonObject.getInt("id"), jsonObject.getString("title"),
                                jsonObject.getString("by"), jsonObject.getString("url"))) {
                            Log.d("Nick", "id: " + jsonObject.getInt("id") + " title" +
                                    jsonObject.getString("title") + " by: " + jsonObject.getString("by")
                                    + " url: " + jsonObject.getString("url"));
                            downloading = true;
                            publishProgress("Downloading...: " + index + " articles");
                            index++;
                        } else {
                            downloading = false;
                            if (0 == i % 3) {
                                publishProgress("Check server update.." );
                            } else if (1 == i % 3) {
                                publishProgress("Check server update....");
                            } else {
                                publishProgress("Check server update......");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }

                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            publishProgress("finish");

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
           super.onProgressUpdate(values);

            String update = values[0];
            textView.setText(update);
            textView.setVisibility(View.VISIBLE);

            if (downloading) {
                titlelist.add("" + titlelist.size() + ". " + a);
                URLlist.add(b);
            }
            adapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            adapter.notifyDataSetChanged();
            textView.setVisibility(View.GONE);
            firsttime = false;
        }
    }

    private boolean OpenOrCreateDatabase() {
        try {
            eventsDB = this.openOrCreateDatabase("HackerNews", MODE_PRIVATE, null);
            eventsDB.execSQL("CREATE TABLE IF NOT EXISTS hacker_news_table (id INT(11), title VARCHAR, name VARCHAR, URL VARCHAR)");

            cursor = eventsDB.rawQuery("SELECT * FROM " + HACKER_NEWS_TABLE, null);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean InsertDataToDatabase(int id, String title, String name, String url) {
        if (CheckIsDataAlreadyInDBorNot(id))
            return false;

        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("title", title);
        cv.put("name", name);
        cv.put("URL", url);
        try {
            eventsDB.insert(HACKER_NEWS_TABLE, null, cv);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean CheckIsDataAlreadyInDBorNot(int queryid) {
        String Query = "SELECT * FROM " + HACKER_NEWS_TABLE + " WHERE id=" + queryid;
        Cursor cursor = eventsDB.rawQuery(Query, null);
        if(cursor.getCount() <= 0){
            cursor.close();
            return false;
        }
        cursor.close();
        return true;
    }
}
