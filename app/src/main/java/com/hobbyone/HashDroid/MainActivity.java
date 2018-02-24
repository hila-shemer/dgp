/* MainActivity.java -- 
   Copyright (C) 2010 Christophe Bouyer (Hobby One)

This file is part of Hash Droid.

Hash Droid is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hash Droid is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hash Droid. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hobbyone.HashDroid;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class MainActivity extends TabActivity implements Runnable {

    private TabHost tabHost = null;
    private String msg_between_tabs = "";
    private ProgressDialog mProgressDialog = null;
    private String test_vectors_result = "N/A";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        tabHost = getTabHost(); // The activity TabHost
        TabHost.TabSpec spec; // Reusable TabSpec for each tab
        Intent intent; // Reusable Intent for each tab

        String sTextTabTitle = getString(R.string.tab_text);
        // Create an Intent to launch an Activity for the tab (to be reused)
        intent = new Intent().setClass(this, TextActivity.class);
        // Initialize a TabSpec for each tab and add it to the TabHost
        spec = tabHost
                .newTabSpec("text")
                .setIndicator(sTextTabTitle)
                .setContent(intent);
        tabHost.addTab(spec);

        // Do the same for the other tabs
        intent = new Intent().setClass(this, SetupActivity.class);
        String sSetupTabTitle = getString(R.string.tab_setup);
        spec = tabHost
                .newTabSpec("setup")
                .setIndicator(sSetupTabTitle)
                .setContent(intent);
        tabHost.addTab(spec);

        intent = new Intent().setClass(this, HistoryActivity.class);
        String sHistTabTitle = getString(R.string.tab_history);
        spec = tabHost
                .newTabSpec("history")
                .setIndicator(sHistTabTitle)
                .setContent(intent);
        tabHost.addTab(spec);

        tabHost.setCurrentTab(0);

        // methods called to get a smoother gradient background on all devices
        //getWindow().setFormat(PixelFormat.RGBA_8888);
        // especially for Donut 1.6
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_DITHER);
    }

    public TabHost get_tab_host() { return tabHost; }
    public String get_msg_between_tabs() { return msg_between_tabs; }
    public void set_msg_between_tabs(String s) { msg_between_tabs = s; }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
        case R.id.menu_help:
            LayoutInflater help_inflater = getLayoutInflater();
            View HelpView = help_inflater.inflate(R.layout.help,
                    (ViewGroup) findViewById(R.id.help_layout_root));

            new AlertDialog.Builder(this)
                    .setIcon(0)
                    .setTitle(getString(R.string.label_menu_help))
                    .setView(HelpView)
                    .setPositiveButton(getString(R.string.Close_but),
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    // TODO Auto-generated method stub
                                }
                            }).show();
            break;
        case R.id.menu_about:
            LayoutInflater about_inflater = getLayoutInflater();
            View AboutView = about_inflater.inflate(R.layout.about,
                    (ViewGroup) findViewById(R.id.about_layout_root));

            TextView vVersion = (TextView) AboutView
                    .findViewById(R.id.about_version);
            String sVersion = vVersion.getText().toString();
            vVersion.setText(sVersion + " " + getSoftwareVersion());

            new AlertDialog.Builder(this)
                    .setIcon(0)
                    .setTitle(getString(R.string.label_menu_about))
                    .setView(AboutView)
                    .setPositiveButton(getString(R.string.Close_but),
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    // TODO Auto-generated method stub
                                }
                            }).show();
            break;
        case R.id.menu_test_vectors:
            ComputeTestVectors();
            break;
        default:
            break;
        }
        return true;
    }

    private String getSoftwareVersion() {
        String sRetString = "";
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                    getPackageName(), 0);
            sRetString = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("AboutActivity", "Package name not found", e);
        }
        return sRetString;
    }

    private void ComputeTestVectors() {
        String sCalculating = getString(R.string.Calculating);
        mProgressDialog = ProgressDialog.show(MainActivity.this, "",
                sCalculating, true);

        Thread thread = new Thread(this);
        thread.start();
    }

    private void run_test_vector(String print, String seed, String account, String name, String format)
    {
        test_vectors_result = test_vectors_result.concat(print + UtilServices.generate_password(seed, account, name, format));
    }

    private void run_test_vector_all(String print, String seed, String account, String name)
    {
        test_vectors_result = test_vectors_result.concat(print);
        String[] formats = {"Hex", "HexLong", "AlNum", "AlNumLong", "Base58", "Base58Long", "XKCD", "XKCD-Long"};
        for (String fmt : formats) {
            run_test_vector("\n" + fmt + ": ", seed, account, name, fmt);
        }
    }

    private void run_test_vector_some(String print, String seed, String account, String name)
    {
        test_vectors_result = test_vectors_result.concat(print);
        String[] formats = {"HexLong", "AlNum", "XKCD-Long"};
        for (String fmt : formats) {
            run_test_vector("\n" + fmt + ": ", seed, account, name, fmt);
        }
    }

    @Override
    // Call when the thread is started
    public void run() {
        test_vectors_result = "";
        run_test_vector("a:aa:alnum: ", "a", "", "aa", "AlNum");
        run_test_vector("\na:aa:base58: ", "a", "", "aa", "Base58");
        run_test_vector("\na:aa:alnumlong: ", "a", "", "aa", "AlNumLong");
        run_test_vector_all("\nP:S:", "passwordPASSWORDpassword", "", "saltSALTsaltSALTsaltSALTsaltSALTsalt");
        run_test_vector_all("\npassword:salt:", "pass", "word", "salt");
        String some_a = "";
        String some_b = "";
        for (int i = 0; i < 64; i++) {
            some_a = some_a.concat("A");
            some_b = some_b.concat("B");
        }
        run_test_vector_some("\nA*64:salt:", some_a, "", "salt");
        String more_a = some_a.concat("A");
        run_test_vector_some("\nA*65:salt:", more_a, "", "salt");
        run_test_vector_some("\nA*64:B*64:", some_a, "", some_b);
        String more_b = some_b.concat("B");
        run_test_vector_some("\nA*64:B*65:", some_a, "", more_b);
        run_test_vector_some("\nA*65:B*64:", more_a, "", some_b);
        run_test_vector_some("\nA*65:B*65:", more_a, "", more_b);

        run_test_vector_some("\nA*64default:salt:", some_a, "default", "salt");
        run_test_vector_some("\nA*65default:salt:", more_a, "default", "salt");
        run_test_vector_some("\nA*64default:B*64:", some_a, "default", some_b);
        run_test_vector_some("\nA*64default:B*65:", some_a, "default", more_b);
        run_test_vector_some("\nA*65default:B*64:", more_a, "default", some_b);
        run_test_vector_some("\nA*65default:B*65:", more_a, "default", more_b);

        run_test_vector_some("\nA*64test:salt:", some_a, "test", "salt");
        run_test_vector_some("\nA*65test:salt:", more_a, "test", "salt");
        run_test_vector_some("\nA*64test:B*64:", some_a, "test", some_b);
        run_test_vector_some("\nA*64test:B*65:", some_a, "test", more_b);
        run_test_vector_some("\nA*65test:B*64:", more_a, "test", some_b);
        run_test_vector_some("\nA*65test:B*65:", more_a, "test", more_b);

        handler.sendEmptyMessage(0);
    }

    //static inner class doesn't hold an implicit reference to the outer class
    private static class MyHandler extends Handler {
        //Using a weak reference means you won't prevent garbage collection
        private final WeakReference<MainActivity> parent_ref;

        public MyHandler(MainActivity parent_instance) {
            parent_ref = new WeakReference<MainActivity>(parent_instance);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity parent = parent_ref.get();
            if (parent != null) {
                // Hide the progress dialog
                if (parent.mProgressDialog != null)
                    parent.mProgressDialog.dismiss();

                // Show the result
                LayoutInflater test_inflater = parent.getLayoutInflater();
                View TestView = test_inflater.inflate(R.layout.test_vectors,
                        (ViewGroup) parent.findViewById(R.id.test_vectors_layout_root));

                TextView TestRes = (TextView) TestView.findViewById(R.id.test_result);
                String res = parent.test_vectors_result;
                TestRes.setText(res);
                new AlertDialog.Builder(parent)
                        .setIcon(0)
                        .setTitle(parent.getString(R.string.label_menu_test_vectors))
                        .setView(TestView)
                        .setPositiveButton(parent.getString(R.string.Close_but),
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        // TODO Auto-generated method stub
                                    }
                                }).show();
            }
        }
    }
    private final MyHandler handler = new MyHandler(this);

}