/* TextActivity.java -- 
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
import android.app.ProgressDialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.text.Editable;
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


import java.math.BigInteger;

public class TextActivity extends Activity implements Runnable {
	private EditText mEditText = null;
	private Button mClearButton = null;
	private Button mGenerateButton = null;
	private Button mCopyButton = null;
	private Spinner mSpinner = null;
	private TextView mResultTV = null;
	private ClipboardManager mClipboard = null;
	private String msHash = "";
	private String msToHash = "";
	private String[] mOutputFormats;
	private ProgressDialog mProgressDialog = null;
	private int miItePos = -1;
	private String mSeed = "";

	public static final String PREFS_NAME = "DgpConfig";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.text);

		mEditText = (EditText) findViewById(R.id.edittext);
		mClearButton = (Button) findViewById(R.id.ClearButton);
		mGenerateButton = (Button) findViewById(R.id.GenerateButton);
		mSpinner = (Spinner) findViewById(R.id.spinner);
		mResultTV = (TextView) findViewById(R.id.label_result);
		mCopyButton = (Button) findViewById(R.id.CopyButton);
		mClipboard = (ClipboardManager) getSystemService("clipboard");
		mOutputFormats = getResources().getStringArray(R.array.Output_Formats);

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this, R.array.Output_Formats, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinner.setAdapter(adapter);
		mSpinner.setSelection(0); // alnum by default
		mSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView,
					View selectedItemView, int position, long id) {
				// your code here
				// Hide the copy button
				if (!msHash.equals(""))
					mCopyButton.setVisibility(View.INVISIBLE);
				// Clean the result text view
				if (mResultTV != null)
					mResultTV.setText("");
			}

			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
				// your code here
			}
		});

		mClearButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Perform action on clicks
				mEditText.setText("");
				if (mResultTV != null)
					mResultTV.setText("");
				msHash = "";
				if (mCopyButton != null)
					mCopyButton.setVisibility(View.INVISIBLE);
			}
		});

		mGenerateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Perform action on clicks
				miItePos = mSpinner.getSelectedItemPosition();
				Editable InputEdit = mEditText.getText();
				msToHash = InputEdit.toString();
				ComputeAndDisplayHash();
			}
		});

		mCopyButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Perform action on clicks
				if (mClipboard != null) {
					mClipboard.setText(msHash);
					String sCopied = getString(R.string.copied);
					Toast.makeText(TextActivity.this, sCopied,
							Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	@Override
	public void onResume() {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		mSeed = settings.getString("seed", "test");
		super.onResume();
	}

	private void ComputeAndDisplayHash() {
		String sCalculating = getString(R.string.Calculating);
		mProgressDialog = ProgressDialog.show(TextActivity.this, "",
				sCalculating, true);

		Thread thread = new Thread(this);
		thread.start();
	}

	private static final byte[] hmac(byte[] key, byte[] msg) {
		if (key.length > 64) {
			Sha256 md = new Sha256();
			md.update(key);
			key = md.digest();
		}
		if (key.length < 64) {
			byte[] newkey = new byte[64];
			System.arraycopy(key, 0, newkey, 0, key.length);
			for (int i = key.length; i < 64; i++) {
				newkey[i] = (byte)0;
			}
			key = newkey;
		}
		byte[] ikey = new byte[64];
		byte[] okey = new byte[64];
		for (int i = 0; i < key.length; i++) {
			okey[i] = (byte)(key[i] ^ (byte)0x5c);
			ikey[i] = (byte)(key[i] ^ (byte)0x36);
		}
		Sha256 imd = new Sha256();
		imd.update(ikey);
		imd.update(msg);
		Sha256 omd = new Sha256();
		omd.update(okey);
		omd.update(imd.digest());
		return omd.digest();
	}

	private static final byte[] pbkdf_block(byte[] key, byte[] salt, int iterations, int block) {
		byte[] saltiter = new byte[salt.length + 4];
		System.arraycopy(salt, 0, saltiter, 0, salt.length);
		saltiter[salt.length] = (byte)((block >> 24) & 0xff);
		saltiter[salt.length+1] = (byte)((block >> 16) & 0xff);
		saltiter[salt.length+2] = (byte)((block >> 8) & 0xff);
		saltiter[salt.length+3] = (byte)(block & 0xff);
		byte[] digtmp = hmac(key, saltiter);
		byte[] result = digtmp;
		for (int i = 1; i < iterations; i++) {
			digtmp = hmac(key, digtmp);
			for (int j = 0; j < 32; j++) {
				result[j] = (byte)(result[j] ^ digtmp[j]);
			}
		}
		return result;
	}

	private static final byte[] pbkdf(byte[] key, byte[] salt, int iterations, int outlen) {
		int blocknum = 0;
		int outlen_aligned = 32 * ((outlen + 31) / 32);
		byte[] output = new byte[outlen_aligned];
		int cur_offset = 0;
		while (cur_offset < outlen) {
			blocknum++;
			byte[] block = pbkdf_block(key, salt, iterations, blocknum);
			System.arraycopy(block, 0, output, cur_offset, 32);
			cur_offset += 32;
		}
		byte[] truncated_output = new byte[outlen];
		System.arraycopy(output, 0, truncated_output, 0, outlen);
		return truncated_output;
	}


	public static final BigInteger bytes_to_int(byte[] data) {
		return new BigInteger(1, data);
	}

	public static final String get_base58(BigInteger int_data) {
		final String digits = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
		String res = "";
		while (int_data.signum() == 1) {
			BigInteger[] t = int_data.divideAndRemainder(BigInteger.valueOf(58));
			int_data = t[0];
			int mod = t[1].intValue();
			res = res.concat(digits.substring(mod, mod+1));
		}
		return res;
	}

	private static final boolean is_alnum(String str) {
		boolean has_lower = false;
		boolean has_upper = false;
		boolean has_digit = false;
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if ((c >= '0') && (c <= '9')) has_digit = true;
			if ((c >= 'a') && (c <= 'z')) has_lower = true;
			if ((c >= 'A') && (c <= 'Z')) has_upper = true;
		}
		return has_digit && has_lower && has_upper;
	}

	private static final String grab_alnum(BigInteger int_data, int length) {
		String raw = get_base58(int_data);
		while (raw.length() > length) {
			String res = raw.substring(0, length);
			if (is_alnum(res)) return res;
			raw = raw.substring(1);
		}
		//assert false;
		return "";
	}

	private static final BigInteger gen_large_int(String seed, String name) {
		byte[] bin_data = pbkdf(seed.getBytes(), name.getBytes(), 8192, 32);
		return bytes_to_int(bin_data);
	}

	@Override
	// Call when the thread is started
	public void run() {
		msHash = "";
//		Sha256 md = new Sha256();
//		md.update(msToHash.getBytes());
//		msHash = UtilServices.toString(md.digest());
//		msHash = UtilServices.toString(hmac("key".getBytes(), "The quick brown fox jumps over the lazy dog".getBytes()));
		//msHash = UtilServices.toString(pbkdf("password".getBytes(), "salt".getBytes(), 4096, 20));
		BigInteger int_data = gen_large_int(mSeed, msToHash);
		msHash = grab_alnum(int_data, 8);
//		msHash = get_base58(int_data);

		handler.sendEmptyMessage(0);
	}

	// This method is called when the computation is over
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// Hide the progress dialog
			if (mProgressDialog != null)
				mProgressDialog.dismiss();

			Resources res = getResources();
			String sTextTitle = String.format(res.getString(R.string.Text),
					msToHash);
			String sTextHashTitle = "";
			if (!msHash.equals("")) {
				String OutputFormat = "";
				if (miItePos >= 0)
					OutputFormat = mOutputFormats[miItePos];
				sTextHashTitle = String.format(res.getString(R.string.Hash),
						OutputFormat, msHash);
				// Show the copy button
				if (mCopyButton != null)
					mCopyButton.setVisibility(View.VISIBLE);
			} else {
				sTextHashTitle = String.format(
						res.getString(R.string.unable_to_calculate), msToHash);
				// Hide the copy button
				if (mCopyButton != null)
					mCopyButton.setVisibility(View.INVISIBLE);
			}

			if (mResultTV != null)
				mResultTV.setText(sTextTitle + sTextHashTitle);
		}
	};
}
