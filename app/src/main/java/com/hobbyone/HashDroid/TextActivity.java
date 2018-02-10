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
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.content.ClipboardManager;
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
import java.security.KeyStore;
import java.security.spec.KeySpec;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;

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
    private String[] mOutputFormats;
    private ProgressDialog mProgressDialog = null;
    private int miItePos = -1;
    private String mSeed = "";
    private String mEncSeed = "";
    private String mSeedIV = "";
    private CountDownTimer clear_clipboard_timer = null;
    private CountDownTimer clear_seed_timer = null;

    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;

    public static final String PREFS_NAME = "DgpConfig";
    public static final String HISTORY_PREFS_NAME = "DgpHistory";
    public static final String KEY_NAME = "DgpKey";

    private KeyguardManager mKeyguardManager;

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
        mCheckBox = (CheckBox) findViewById(R.id.SaveHistoryCB);

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
                    if (clear_clipboard_timer != null) {
                        clear_clipboard_timer.cancel();
                        clear_clipboard_timer = null;
                    }
                    clear_clipboard_timer = new CountDownTimer(30000, 1000) {
                        public void onTick(long millisUntilFinished) {}
                        public void onFinish() {
                            mClipboard.setPrimaryClip(ClipData.newPlainText("hash", ""));
                            clear_clipboard_timer = null;
                        }
                    }.start();
                }
            }
        });

        mCheckBox.setChecked(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        /* Check for message from HistoryActivity (so ugly) */
        boolean needs_generate = false;
        MainActivity parent = (MainActivity)TextActivity.super.getParent();
        String s = parent.get_msg_between_tabs();
        parent.set_msg_between_tabs("");
        if (!s.equals("")) {
            run_history_entry(s);
            needs_generate = true;
        }

        /* Restore/Load key (if invalidated or changed) */
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String tmp = settings.getString("seed", "test");
        if (mEncSeed.equals(tmp) && !mSeed.equals("cleared")) {
            if (needs_generate) {
                ComputeAndDisplayHash();
            }
            return;
        }
        mEncSeed = tmp;
        if (mEncSeed.compareTo("test") == 0) {
            mSeed = "test";
        } else {
            mSeedIV = settings.getString("seed_iv", "");
            showAuthenticationScreen();
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

            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(UtilServices.hex_to_bytes(mSeedIV)));
            byte[] result = cipher.doFinal(UtilServices.hex_to_bytes(mEncSeed));
            mSeed = new String(result);
            if (clear_seed_timer != null) {
                clear_seed_timer.cancel();
                clear_seed_timer = null;
            }
            clear_seed_timer = new CountDownTimer(30000, 1000) {
                public void onTick(long millisUntilFinished) {}
                public void onFinish() {
                    mSeed = "cleared";
                    clear_seed_timer = null;
                }
            }.start();
        } catch (KeyPermanentlyInvalidatedException e) {
            // This happens if the lock screen has been disabled or reset after the key was
            // generated after the key was generated.
            Toast.makeText(this, "Keys are invalidated after created. Regenerate\n"
                            + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            mResultTV.setText("Failed to decrypt: " + e.getMessage());
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

    @Override
    // Call when the thread is started
    public void run() {
        msHash = "";

        final int iterations = 42000;

        final int outputKeyLength = 160;

        try {
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec keySpec = new PBEKeySpec(mSeed.toCharArray(), msToHash.getBytes(), iterations, outputKeyLength);
            SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
            msHash = grab_alnum(bytes_to_int(secretKey.getEncoded()), 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            msHash = "AlgoError " + e.getMessage();
        } catch (java.security.spec.InvalidKeySpecException e) {
            msHash = "KeySpecError " + e.getMessage();
        } catch (Exception e) {
            msHash = "Error " + e.getMessage();
        }

        handler.sendEmptyMessage(0);
    }

    private void add_item(String s) {
        SharedPreferences settings = getSharedPreferences(HISTORY_PREFS_NAME, 0);
        Set<String> hist_items = settings.getStringSet("History", null);
        Set<String> output = null;
        if (hist_items == null) {
            output = new HashSet<String>();
        } else {
            output = new HashSet<String>(hist_items);
        }
        output.add(s);
        settings.edit().putStringSet("History", output).apply();
    }

    public void run_history_entry(String s) {
        for (int i = 0; i < mOutputFormats.length; i++) {
            String suffix = " (" + mOutputFormats[i] + ")";
            if (!s.endsWith(suffix)) continue;
            mSpinner.setSelection(i);
            mEditText.setText(s.substring(0, s.lastIndexOf(suffix)));
            return;
        }
        Toast.makeText(this, "Invalid entry.", Toast.LENGTH_SHORT).show();
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
                if (mCheckBox != null) {
                    if (mCheckBox.isChecked()) {
                        add_item(msToHash + " (" + OutputFormat + ")");
                    }
                }
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
