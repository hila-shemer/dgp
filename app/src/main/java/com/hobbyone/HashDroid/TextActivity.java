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

public class TextActivity extends Activity implements Runnable {
	private EditText mEditText = null;
	private CheckBox mCheckBox = null;
	private Button mClearButton = null;
	private Button mGenerateButton = null;
	private Button mCopyButton = null;
	private Spinner mSpinner = null;
	private TextView mResultTV = null;
	private ClipboardManager mClipboard = null;
	private String msHash = "";
	private String msToHash = "";
	private String[] mFunctions;
	private HashFunctionOperator mHashOpe = null;
	private ProgressDialog mProgressDialog = null;
	private int miItePos = -1;

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
		mFunctions = getResources().getStringArray(R.array.Algo_Array);
		mCheckBox = (CheckBox) findViewById(R.id.UpperCaseCB);

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this, R.array.Algo_Array, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinner.setAdapter(adapter);
		mSpinner.setSelection(5); // MD5 by default
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

		mCheckBox.setChecked(false); // lower case by default
		mCheckBox.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Perform action on clicks
				if (!msHash.equals("")) {
					// A hash value has already been calculated,
					// just convert it to lower or upper case
					String OldHash = msHash;
					if (mCheckBox.isChecked()) {
						msHash = OldHash.toUpperCase();
					} else {
						msHash = OldHash.toLowerCase();
					}
					if (mResultTV != null) {
						String sResult = mResultTV.getText().toString();
						sResult = sResult.replaceAll(OldHash, msHash);
						mResultTV.setText(sResult);
					}
				}
			}
		});

	}

	private void ComputeAndDisplayHash() {
		if (mHashOpe == null)
			mHashOpe = new HashFunctionOperator();
		String sAlgo = "";
		if (miItePos == 0)
			sAlgo = "Adler-32";
		else if (miItePos == 1)
			sAlgo = "CRC-32";
		else if (miItePos == 2)
			sAlgo = "haval";
		else if (miItePos == 3)
			sAlgo = "md2";
		else if (miItePos == 4)
			sAlgo = "md4";
		else if (miItePos == 5)
			sAlgo = "md5";
		else if (miItePos == 6)
			sAlgo = "ripemd-128";
		else if (miItePos == 7)
			sAlgo = "ripemd-160";
		else if (miItePos == 8)
			sAlgo = "sha-1";
		else if (miItePos == 9)
			sAlgo = "sha-256";
		else if (miItePos == 10)
			sAlgo = "sha-384";
		else if (miItePos == 11)
			sAlgo = "sha-512";
		else if (miItePos == 12)
			sAlgo = "tiger";
		else if (miItePos == 13)
			sAlgo = "whirlpool";
		mHashOpe.SetAlgorithm(sAlgo);

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
			for (int i = 0; i < key.length; i++) {
				newkey[i] = key[i];
			}
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
		for (int i = 0; i < salt.length; i++) {
			saltiter[i] = salt[i];
		}
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
		int size = 0;
		int blocknum = 0;
		int outlen_aligned = 32 * ((outlen + 31) / 32);
		byte[] output = new byte[outlen_aligned];
		int cur_offset = 0;
		while (size < outlen) {
			blocknum++;
			byte[] block = pbkdf_block(key, salt, iterations, blocknum);
			for (int i = 0; i < 32; i++) {
				output[cur_offset] = block[i];
				cur_offset++;
			}
			size += 32;
		}
		byte[] truncated_output = new byte[outlen];
		for (int i = 0; i < outlen; i++) {
			truncated_output[i] = output[i];
		}
		return truncated_output;
	}

	/*
int PKCS5_PBKDF2_HMAC(const char *pass, int passlen,
                      const unsigned char *salt, int saltlen, int iter,
                      const EVP_MD *digest, int keylen, unsigned char *out)
{
    const char *empty = "";
    unsigned char digtmp[EVP_MAX_MD_SIZE], *p, itmp[4];
    int cplen, j, k, tkeylen, mdlen;
    unsigned long i = 1;
    HMAC_CTX *hctx_tpl = NULL, *hctx = NULL;

    mdlen = EVP_MD_size(digest);
    if (mdlen < 0)
        return 0;

    hctx_tpl = HMAC_CTX_new();
    if (hctx_tpl == NULL)
        return 0;
    p = out;
    tkeylen = keylen;
    if (pass == NULL) {
        pass = empty;
        passlen = 0;
    } else if (passlen == -1) {
        passlen = strlen(pass);
    }
    if (!HMAC_Init_ex(hctx_tpl, pass, passlen, digest, NULL)) {
        HMAC_CTX_free(hctx_tpl);
        return 0;
    }
    hctx = HMAC_CTX_new();
    if (hctx == NULL) {
        HMAC_CTX_free(hctx_tpl);
        return 0;
    }
    while (tkeylen) {
        if (tkeylen > mdlen)
            cplen = mdlen;
        else
            cplen = tkeylen;
        /*
         * We are unlikely to ever use more than 256 blocks (5120 bits!) but
         * just in case...
         * /
		itmp[0] = (unsigned char)((i >> 24) & 0xff);
		itmp[1] = (unsigned char)((i >> 16) & 0xff);
		itmp[2] = (unsigned char)((i >> 8) & 0xff);
		itmp[3] = (unsigned char)(i & 0xff);
        if (!HMAC_CTX_copy(hctx, hctx_tpl)) {
			HMAC_CTX_free(hctx);
			HMAC_CTX_free(hctx_tpl);
			return 0;
		}
        if (!HMAC_Update(hctx, salt, saltlen)
            || !HMAC_Update(hctx, itmp, 4)
            || !HMAC_Final(hctx, digtmp, NULL)) {
			HMAC_CTX_free(hctx);
			HMAC_CTX_free(hctx_tpl);
			return 0;
		}
		memcpy(p, digtmp, cplen);
        for (j = 1; j < iter; j++) {
			if (!HMAC_CTX_copy(hctx, hctx_tpl)) {
				HMAC_CTX_free(hctx);
				HMAC_CTX_free(hctx_tpl);
				return 0;
			}
			if (!HMAC_Update(hctx, digtmp, mdlen)
					|| !HMAC_Final(hctx, digtmp, NULL)) {
				HMAC_CTX_free(hctx);
				HMAC_CTX_free(hctx_tpl);
				return 0;
			}
			for (k = 0; k < cplen; k++)
				p[k] ^= digtmp[k];
		}
		tkeylen -= cplen;
		i++;
		p += cplen;
	}
	HMAC_CTX_free(hctx);
	HMAC_CTX_free(hctx_tpl);
	return 1;
}

	 */

	@Override
	// Call when the thread is started
	public void run() {
		msHash = "";
//		Sha256 md = new Sha256();
//		md.update(msToHash.getBytes());
//		msHash = UtilServices.toString(md.digest());
//		msHash = UtilServices.toString(hmac("key".getBytes(), "The quick brown fox jumps over the lazy dog".getBytes()));
		msHash = UtilServices.toString(pbkdf("password".getBytes(), "salt".getBytes(), 4096, 20));

//		if (mHashOpe != null)
//			msHash = mHashOpe.StringToHash(msToHash);
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
				if (mCheckBox != null) {
					if (mCheckBox.isChecked()) {
						msHash = msHash.toUpperCase();
					} else {
						msHash = msHash.toLowerCase();
					}
				}
				String Function = "";
				if (miItePos >= 0)
					Function = mFunctions[miItePos];
				sTextHashTitle = String.format(res.getString(R.string.Hash),
						Function, msHash);
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