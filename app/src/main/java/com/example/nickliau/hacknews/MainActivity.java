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

public class MainActivity extends AppCompatActivity {
    public String URL_IDS = "https://hacker-news.firebaseio.com/v0/topstories.json";
    public String URL_ART_START = "https://hacker-news.firebaseio.com/v0/item/";
    public String URL_ART_END = ".json";

    ListView listview;
    TextView textView;
    ArrayAdapter<String> adapter;
    ArrayList<String> titlelist, URLlist;

    SQLiteDatabase eventsDB;
    static String HACKER_NEWS_TABLE = "hacker_news_table";
    WebView webView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        //Toolbar toolbar = (Toolbar) findViewById(R.id.);

        listview = (ListView) findViewById(R.id.listUrl);
        textView = (TextView) findViewById(R.id.ShowDownloadText);
        titlelist = new ArrayList<>();
        URLlist = new ArrayList<>();

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, titlelist);
        listview.setAdapter(adapter);
        webView =  (WebView) findViewById(R.id.webView);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
//                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(URLlist.get(i)));
//                startActivity(browserIntent);


                webView.getSettings().setJavaScriptEnabled(true);
                webView.setWebViewClient(new WebViewClient());
                webView.loadUrl(URLlist.get(i));
                webView.setVisibility(View.VISIBLE);
            }
        });

        if (OpenOrCreateDatabase()) {
            Log.e("Nick", "Create database Success");
            new DownloadTask().execute();
        } else {
            Log.e("Nick", "Create database failed");
        }
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

//    public void UpdateAdapter (Cursor cursor) {
//        // TODO Auto-generated method stub
//        if (cursor != null && cursor.getCount() > 0) {
//            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
//                    android.R.layout.simple_list_item_2, cursor, new String[] {
//                    "title", "price"}, new int[] { android.R.id.text1, android.R.id.text2 } );
//            }
//        }
//    }

    class DownloadTask extends AsyncTask<Void, String, Void> {

        @Override
        protected Void doInBackground(Void... Params) {
            String resArray = "";
            HttpURLConnection connection = null;
            InputStream in = null;
            InputStreamReader inReader = null;

            try {
                URL urlid = new URL(URL_IDS);
                connection = (HttpURLConnection) urlid.openConnection();
                in = connection.getInputStream();
                inReader = new InputStreamReader(in);

                int data = inReader.read();
                char current;
                while (data != -1) {
                    current = (char) data;
                    resArray += current;

                    data = inReader.read();
                }

                JSONArray jsonArray = new JSONArray(resArray);
                int index = 0;
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

                    if (jsonObject.has("title") && jsonObject.has("url")) {
                        titlelist.add(jsonObject.getString("title"));
                        URLlist.add(jsonObject.getString("url"));

                        Log.d("Nick", "id: " + jsonObject.getInt("id") + " title" +
                                jsonObject.getString("title") + " by: " + jsonObject.getString("by")
                        + " url: " + jsonObject.getString("url"));

                        InsertDataToDatabase(jsonObject.getInt("id"), jsonObject.getString("title"),
                                jsonObject.getString("by"), jsonObject.getString("url"));
                        publishProgress("Downloading...: " + index + " articles");
                        index ++;
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

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
           super.onProgressUpdate(values);

            String update = values[0];
            textView.setText(update);
            textView.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            adapter.notifyDataSetChanged();
            textView.setVisibility(View.GONE);
        }
    }

    private boolean OpenOrCreateDatabase() {
        try {
            eventsDB = this.openOrCreateDatabase("HackerNews", MODE_PRIVATE, null);
            eventsDB.execSQL("CREATE TABLE IF NOT EXISTS hacker_news_table (id INT(11), title VARCHAR, name VARCHAR, URL VARCHAR)");
//            eventsDB.execSQL("INSERT INTO newUsers (name, age) VALUES ('Nick', 20)");
//            eventsDB.execSQL("INSERT INTO newUsers (name, age) VALUES ('Jeff', 30)");
//            Cursor c = eventsDB.rawQuery("SELECT * FROM newUsers", null);
//            int eventIndex = c.getColumnIndex("name");
//            int yearIndex = c.getColumnIndex("age");
//            c.moveToFirst();
//            while (c != null) {
//                Log.i("Results - name", c.getString(eventIndex));
//                c.moveToNext();
//            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean InsertDataToDatabase(int id, String title, String name, String url) {
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
        Log.d("Nick", "Insert data to database success");
        return true;
    }
}
