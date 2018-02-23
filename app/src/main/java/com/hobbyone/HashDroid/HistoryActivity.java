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
import android.content.Context;
import android.os.Bundle;
import android.content.ClipboardManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

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
    private String mAccount = "";

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
                MainActivity parent = (MainActivity)HistoryActivity.super.getParent();
                parent.set_msg_between_tabs(mSpinner.getSelectedItem().toString());
                parent.get_tab_host().setCurrentTab(0);
            }
        });
    }

    private void update_list() {
        int i = mSpinner.getSelectedItemPosition();
        SharedPreferences account_settings = getSharedPreferences(TextActivity.PREFS_NAME, 0);
        mAccount = account_settings.getString("account", "default");
        SharedPreferences settings = getSharedPreferences(TextActivity.HISTORY_PREFS_NAME, 0);
        String history_key = "History" + mAccount;
        Set<String> hist_items = settings.getStringSet(history_key, null);
        ArrayList<String> items = new ArrayList<String>();
        if (hist_items != null && hist_items.size() != 0) {
            SortedSet<String> sorted_items = new TreeSet<String>(hist_items);
            items.addAll(sorted_items);
            if (i >= items.size()) {
                i = 0;
            }
        } else {
            items.add("Empty");
            i = 0;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String> (this, android.R.layout.simple_spinner_dropdown_item, items);
        mSpinner.setAdapter(adapter);
        mSpinner.setSelection(i);
    }

    private void delete_item() {
        //miItePos = mSpinner.getSelectedItemPosition();
        String s = mSpinner.getSelectedItem().toString();
        SharedPreferences settings = getSharedPreferences(TextActivity.HISTORY_PREFS_NAME, 0);
        Set<String> hist_items = settings.getStringSet("History" + mAccount, null);
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
