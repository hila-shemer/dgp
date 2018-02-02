/* SetupActivity.java --
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
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.security.SecureRandom;

public class SetupActivity extends Activity {

	private EditText mEditText1 = null;
	private Button mSetupButton = null;
	private Button mClearButton = null;
	private Button mGenButton = null;
	private TextView mResultTV = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.setup);

		mEditText1 = (EditText) findViewById(R.id.edit_txt1);
		mClearButton = (Button) findViewById(R.id.ClearButton);
		mGenButton = (Button) findViewById(R.id.GenButton);
		mSetupButton = (Button) findViewById(R.id.SaveButton);
		mResultTV = (TextView) findViewById(R.id.label_result);

		mSetupButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Perform action on clicks
				Editable InputEdit1 = mEditText1.getText();
				String sInputText1 = InputEdit1.toString();
				if (sInputText1 == null) return;
				SharedPreferences settings = getSharedPreferences(TextActivity.PREFS_NAME, 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putString("seed", sInputText1);
				editor.commit();
				String sText = getString(R.string.SetupSuccess);
				mResultTV.setTextColor(Color.GREEN);
				if (mResultTV != null)
					mResultTV.setText(sText);
			}
		});

		mClearButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Perform action on clicks
				mEditText1.setText("");
				mResultTV.setText("");
			}
		});

		mGenButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Perform action on clicks
				SecureRandom random = new SecureRandom();
				byte bytes[] = new byte[20];
				random.nextBytes(bytes);
				mEditText1.setText(TextActivity.get_base58(TextActivity.bytes_to_int(bytes)));
				mResultTV.setText("");
			}
		});
	}
}
