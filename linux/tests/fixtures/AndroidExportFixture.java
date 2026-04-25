import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public class AndroidExportFixture {
    private static final byte[] SALT = "dgp-export-v1".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;

    private static byte[] deriveKey(String pin) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(pin.toCharArray(), SALT, ITERATIONS, KEY_BITS);
        return factory.generateSecret(spec).getEncoded();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: AndroidExportFixture <encrypt|decrypt> <pin> <input-file> <output-file>");
            System.exit(1);
        }
        String mode = args[0];
        String pin = args[1];
        String inputFile = args[2];
        String outputFile = args[3];

        byte[] keyBytes = deriveKey(pin);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

        if ("encrypt".equals(mode)) {
            String plaintext = new String(Files.readAllBytes(Paths.get(inputFile)), StandardCharsets.UTF_8);
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ctAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] blob = new byte[IV_LEN + ctAndTag.length];
            System.arraycopy(iv, 0, blob, 0, IV_LEN);
            System.arraycopy(ctAndTag, 0, blob, IV_LEN, ctAndTag.length);

            String encoded = Base64.getEncoder().encodeToString(blob);
            try (PrintWriter pw = new PrintWriter(outputFile, "UTF-8")) {
                pw.print(encoded);
            }
        } else if ("decrypt".equals(mode)) {
            String encoded = new String(Files.readAllBytes(Paths.get(inputFile)), StandardCharsets.UTF_8).trim();
            byte[] blob = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[IV_LEN];
            System.arraycopy(blob, 0, iv, 0, IV_LEN);
            byte[] ctAndTag = new byte[blob.length - IV_LEN];
            System.arraycopy(blob, IV_LEN, ctAndTag, 0, ctAndTag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(ctAndTag);

            try (PrintWriter pw = new PrintWriter(outputFile, "UTF-8")) {
                pw.print(new String(plainBytes, StandardCharsets.UTF_8));
            }
        } else {
            System.err.println("Unknown mode: " + mode);
            System.exit(1);
        }
    }
}
