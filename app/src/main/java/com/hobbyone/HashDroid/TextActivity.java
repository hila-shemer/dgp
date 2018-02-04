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
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.content.ClipboardManager;
import android.text.Editable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.content.SharedPreferences;


import java.math.BigInteger;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

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
	private String mEncSeed = "";
	private String mSeedIV = "";

	private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;

	public static final String PREFS_NAME = "DgpConfig";
	public static final String KEY_NAME = "DgpKey";

	private KeyguardManager mKeyguardManager;

	private static final int BLOCK_SIZE = 64;
	private static final int DIGEST_SIZE = 32;

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
		mClipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		mOutputFormats = getResources().getStringArray(R.array.Output_Formats);

		mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
		if (!mKeyguardManager.isKeyguardSecure()) {
			// Show a message that the user hasn't set up a lock screen.
			Toast.makeText(this,
					"Secure lock screen hasn't set up.\n"
							+ "Go to 'Settings -> Security -> Screenlock' to set up a lock screen",
					Toast.LENGTH_LONG).show();
		}

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
					mClipboard.setPrimaryClip(ClipData.newPlainText("hash", msHash));
					String sCopied = getString(R.string.copied);
					Toast.makeText(TextActivity.this, sCopied,
							Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		mEncSeed = settings.getString("seed", "test");
		if (mEncSeed.compareTo("test") == 0) {
			mSeed = "test";
		} else {
			mSeedIV = settings.getString("seed_iv", "");
			tryDecrypt();
		}
	}

	/**
	 * Tries to decrypt some data with the generated key in createKey which
	 * only works if the user has just authenticated via device credentials.
	 */
	private void tryDecrypt() {
		try {
			KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
			keyStore.load(null);
			SecretKey secretKey = (SecretKey) keyStore.getKey(TextActivity.KEY_NAME, null);
			Cipher cipher = Cipher.getInstance(
					KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
							+ KeyProperties.ENCRYPTION_PADDING_PKCS7);

			// Try encrypting something, it will only work if the user authenticated within
			// the last AUTHENTICATION_DURATION_SECONDS seconds.
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(hex_to_bytes(mSeedIV)));
			byte[] result = cipher.doFinal(hex_to_bytes(mEncSeed));
			mSeed = new String(result);
		} catch (UserNotAuthenticatedException e) {
			// User is not authenticated, let's authenticate with device credentials.
			mResultTV.setText("Not authenticated");
//			showAuthenticationScreen();
		} catch (KeyPermanentlyInvalidatedException e) {
			// This happens if the lock screen has been disabled or reset after the key was
			// generated after the key was generated.
			Toast.makeText(this, "Keys are invalidated after created. Regenerate\n"
							+ e.getMessage(),
					Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			mResultTV.setText("Failed to encrypt: " + e.getMessage());
		}
	}

	private void showAuthenticationScreen() {
		// Create the Confirm Credentials screen. You can customize the title and description. Or
		// we will provide a generic one for you if you leave it null
		Intent intent = mKeyguardManager.createConfirmDeviceCredentialIntent(null, null);
		if (intent != null) {
			startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
			// Challenge completed, proceed with using cipher
			if (resultCode == RESULT_OK) {
				tryDecrypt();
			} else {
				// The user canceled or didn’t complete the lock screen
				// operation. Go to error/cancellation flow.
				Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void ComputeAndDisplayHash() {
		String sCalculating = getString(R.string.Calculating);
		mProgressDialog = ProgressDialog.show(TextActivity.this, "",
				sCalculating, true);

		Thread thread = new Thread(this);
		thread.start();
	}

	private static byte[] hex_to_bytes(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i+1), 16));
		}
		return data;
	}

	public static String bytes_to_hex(byte[] b) {
		StringBuilder data = new StringBuilder();

		for (byte aB : b) {
			data.append(Integer.toHexString((aB >>> 4) & 0xf));
			data.append(Integer.toHexString(aB & 0xf));
		}
		return data.toString();
	}

	private static byte[] hmac(byte[] key, byte[] msg) {
		if (key.length > BLOCK_SIZE) {
			Sha256 md = new Sha256();
			md.update(key);
			key = md.digest();
		}
		if (key.length < BLOCK_SIZE) {
			byte[] newkey = new byte[BLOCK_SIZE];
			System.arraycopy(key, 0, newkey, 0, key.length);
			for (int i = key.length; i < BLOCK_SIZE; i++) {
				newkey[i] = (byte)0;
			}
			key = newkey;
		}
		byte[] ikey = new byte[BLOCK_SIZE];
		byte[] okey = new byte[BLOCK_SIZE];
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

	private static byte[] pbkdf_block(byte[] key, byte[] salt, int iterations, int block) {
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
			for (int j = 0; j < DIGEST_SIZE; j++) {
				result[j] = (byte)(result[j] ^ digtmp[j]);
			}
		}
		return result;
	}

	private static byte[] pbkdf(byte[] key, byte[] salt, int iterations, int outlen) {
		int blocknum = 0;
		int outlen_aligned = DIGEST_SIZE * ((outlen + (DIGEST_SIZE-1)) / DIGEST_SIZE);
		byte[] output = new byte[outlen_aligned];
		int cur_offset = 0;
		while (cur_offset < outlen) {
			blocknum++;
			byte[] block = pbkdf_block(key, salt, iterations, blocknum);
			System.arraycopy(block, 0, output, cur_offset, DIGEST_SIZE);
			cur_offset += DIGEST_SIZE;
		}
		byte[] truncated_output = new byte[outlen];
		System.arraycopy(output, 0, truncated_output, 0, outlen);
		return truncated_output;
	}


	public static BigInteger bytes_to_int(byte[] data) {
		return new BigInteger(1, data);
	}

	public static String get_base58(BigInteger int_data) {
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

	private static boolean is_alnum(String str) {
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

	private static String grab_alnum(BigInteger int_data, int length) {
		String raw = get_base58(int_data);
		while (raw.length() > length) {
			String res = raw.substring(0, length);
			if (is_alnum(res)) return res;
			raw = raw.substring(1);
		}
		//assert false;
		return "";
	}

	private static BigInteger gen_large_int(String seed, String name) {
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
			String sTextHashTitle;
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
