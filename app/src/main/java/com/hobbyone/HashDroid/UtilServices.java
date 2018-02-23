/* UtilServices.java -- various utility routines.
   Copyright (C) 2001, 2002, 2003, 2006 Free Software Foundation, Inc.

This file is a part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or (at
your option) any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
USA

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version.  */

package com.hobbyone.HashDroid;

import java.math.BigInteger;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * <p>
 * A collection of utility methods used throughout this project.
 * </p>
 */
public class UtilServices {

    /** Trivial constructor to enforce Singleton pattern. */
    private UtilServices() {
        super();
    }

    public static String bytes_to_hex(byte[] b) {
        StringBuilder data = new StringBuilder();

        for (byte aB : b) {
            data.append(Integer.toHexString((aB >>> 4) & 0xf));
            data.append(Integer.toHexString(aB & 0xf));
        }
        return data.toString();
    }

    public static byte[] hex_to_bytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
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

    public static String grab_alnum(BigInteger int_data, int length) {
        String raw = get_base58(int_data);
        while (raw.length() > length) {
            String res = raw.substring(0, length);
            if (is_alnum(res)) return res;
            raw = raw.substring(1);
        }
        return "FailedGrabAlnum from "+ get_base58(int_data);
    }

    public static String generate_password(String seed, String account, String name, String format)
    {
        final int iterations = 42000;

        final int outputKeyLength = 320;

        try {
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            String seed_with_account = seed + account;
            KeySpec keySpec = new PBEKeySpec(seed_with_account.toCharArray(), name.getBytes(), iterations, outputKeyLength);
            SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
            if (format.equals("AlNum")) {
                return grab_alnum(bytes_to_int(secretKey.getEncoded()), 8);
            } else if (format.equals("AlNumLong")) {
                return grab_alnum(bytes_to_int(secretKey.getEncoded()), 12);
            } else if (format.equals("Hex")) {
                return bytes_to_hex(secretKey.getEncoded()).substring(0, 8);
            } else if (format.equals("HexLong")) {
                return bytes_to_hex(secretKey.getEncoded()).substring(0, 16);
            } else if (format.equals("Base58")) {
                return get_base58(bytes_to_int(secretKey.getEncoded())).substring(0, 8);
            } else if (format.equals("Base58Long")) {
                return get_base58(bytes_to_int(secretKey.getEncoded())).substring(0, 12);
            } else {
                return "UnknownFormat";
            }
        } catch (java.security.NoSuchAlgorithmException e) {
            return "AlgoError " + e.getMessage();
        } catch (java.security.spec.InvalidKeySpecException e) {
            return "KeySpecError " + e.getMessage();
        } catch (Exception e) {
            return "Error " + e.getMessage();
        }
    }

}
