/* HistoryActivity.java -- 
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

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.content.ClipboardManager;
import android.text.Editable;
import android.util.ArraySet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class HistoryActivity extends Activity {
    private EditText mEditText = null;
    private Button mDeleteButton = null;
    private Button mGenerateButton = null;
    private Spinner mSpinner = null;
    private ClipboardManager mClipboard = null;
    private String[] mOutputFormats;
    private int miItePos = -1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history);

        mEditText = (EditText) findViewById(R.id.edittext);
        mDeleteButton = (Button) findViewById(R.id.DeleteButton);
        mGenerateButton = (Button) findViewById(R.id.GenerateButton);
        mSpinner = (Spinner) findViewById(R.id.spinner);
        mClipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        mOutputFormats = getResources().getStringArray(R.array.Output_Formats);

        mSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView,
                    View selectedItemView, int position, long id) {
                // your code here
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }
        });

        mDeleteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                delete_item();
            }
        });

        mGenerateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Perform action on clicks
            //    miItePos = mSpinner.getSelectedItemPosition();
            }
        });
//      SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
//      String tmp = settings.getString("seed", "test");
    }

    private void update_list() {
        SharedPreferences settings = getSharedPreferences(TextActivity.HISTORY_PREFS_NAME, 0);
        Set<String> hist_items = settings.getStringSet("History", null);
        SortedSet<String> sorted_items = new TreeSet<String>(hist_items);
        ArrayList<String> items = new ArrayList<String>();
        if (hist_items != null) {
            for (String s : sorted_items) {
                items.add(s);
            }
        } else {
            items.add("Empty");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String> (this, android.R.layout.simple_spinner_dropdown_item, items);
        mSpinner.setAdapter(adapter);
        mSpinner.setSelection(0);
    }

    private void delete_item() {
        //miItePos = mSpinner.getSelectedItemPosition();
        String s = mSpinner.getSelectedItem().toString();
        Toast.makeText(this,"Chosen: " + s, Toast.LENGTH_LONG).show();
        SharedPreferences settings = getSharedPreferences(TextActivity.HISTORY_PREFS_NAME, 0);
        Set<String> hist_items = settings.getStringSet("History", null);
        if (hist_items == null) return;
        Set<String> output = new HashSet<String>(hist_items);
        output.remove(s);
        settings.edit().putStringSet("History", output).apply();
        update_list();
    }

    @Override
    public void onResume() {
        super.onResume();
        update_list();
    }

}
