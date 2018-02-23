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
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class SetupActivity extends Activity {

    private EditText mSeedText = null;
    private EditText mAccountText = null;
    private Button mSaveButton = null;
    private Button mClearButton = null;
    private Button mGenButton = null;
    private TextView mResultTV = null;
    private String mSeed = "";
    private String mAccount = "";

    /**
     * If the user has unlocked the device Within the last this number of seconds,
     * it can be considered as an authenticator.
     */
    private static final int AUTHENTICATION_DURATION_SECONDS = 30;
    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;

    private KeyguardManager mKeyguardManager;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup);

        mSeedText = (EditText) findViewById(R.id.seed_txt);
        mAccountText = (EditText) findViewById(R.id.account_txt);
        mClearButton = (Button) findViewById(R.id.ClearButton);
        mGenButton = (Button) findViewById(R.id.GenButton);
        mSaveButton = (Button) findViewById(R.id.SaveButton);
        mResultTV = (TextView) findViewById(R.id.label_result);

        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        try {
            if (!mKeyguardManager.isKeyguardSecure()) {
                // Show a message that the user hasn't set up a lock screen.
                Toast.makeText(this,
                        "Secure lock screen hasn't set up.\n"
                                + "Go to 'Settings -> Security -> Screenlock' to set up a lock screen",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this,
                    "Secure lock screen hasn't set up.\n"
                            + "Go to 'Settings -> Security -> Screenlock' to set up a lock screen\n"
                            + "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        mSaveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Editable AccountEdit = mAccountText.getText();
                mAccount = AccountEdit.toString();
                AccountEdit.clear();
                if (!mAccount.equals("")) {
                    SharedPreferences settings = getSharedPreferences(TextActivity.PREFS_NAME, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("account", mAccount);
                    editor.apply();
                }

                Editable SeedEdit = mSeedText.getText();
                mSeed = SeedEdit.toString();
                SeedEdit.clear();
                if (!mSeed.equals("")) {
                    if (mResultTV != null) {
                        mResultTV.setTextColor(Color.RED);
                        mResultTV.setText("Generating key");
                    }
                    createKey();
                    if (mResultTV != null) {
                        mResultTV.setTextColor(Color.RED);
                        mResultTV.setText("Saving");
                    }
                    showAuthenticationScreen();
                }
            }
        });

        mClearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mSeedText.setText("");
                mAccountText.setText("");
                mResultTV.setText("");
            }
        });

        mGenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Perform action on clicks
                SecureRandom random = new SecureRandom();
                byte bytes[] = new byte[40];
                random.nextBytes(bytes);
                try {
                    String[] words = UtilServices.get_words(UtilServices.bytes_to_int(bytes), 24);
                    String tmp = "";
                    for (String word : words) {
                        tmp = tmp.concat(word);
                        tmp = tmp.concat(" ");
                    }
                    tmp = tmp.substring(0, tmp.lastIndexOf(' '));
                    mSeedText.setText(tmp);
                } catch (Exception e) {
                    mSeedText.setText("Error " + e.getMessage());
                }
                mResultTV.setText("");
            }
        });
    }

    /**
     * Tries to encrypt some data with the generated key in {@link #createKey} which
     * only works if the user has just authenticated via device credentials.
     */
    private void tryEncrypt() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(TextActivity.KEY_NAME, null);
            Cipher cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] result = cipher.doFinal(mSeed.getBytes());
            mSeed = "";
            store_seed(result, iv);
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

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with device credentials within the last X seconds.
     */
    private void createKey() {
        // Generate a key to decrypt payment credentials, tokens, etc.
        // This will most likely be a registration step for the user when they are setting up your app.
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            keyGenerator.init(new KeyGenParameterSpec.Builder(TextActivity.KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            keyGenerator.generateKey();
        } catch (Exception e) {
            mResultTV.setText("Failed to create key: " + e.getMessage());
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
                tryEncrypt();
            } else {
                // The user canceled or didn’t complete the lock screen
                // operation. Go to error/cancellation flow.
                Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void store_seed(byte[] seed, byte[] iv) {
        SharedPreferences settings = getSharedPreferences(TextActivity.PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        String encoded = UtilServices.bytes_to_hex(seed);
        editor.putString("seed", encoded);
        encoded = UtilServices.bytes_to_hex(iv);
        editor.putString("seed_iv", encoded);
        editor.apply();
        String sText = getString(R.string.SetupSuccess);
        if (mResultTV != null) {
            mResultTV.setTextColor(Color.GREEN);
            mResultTV.setText(sText);
        }
    }
}
