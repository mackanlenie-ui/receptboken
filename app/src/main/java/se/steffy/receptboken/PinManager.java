package se.steffy.receptboken;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class PinManager {
    private static final String PREFS = "settings";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_HASH = "pin_hash";

    private PinManager() {}

    public static boolean hasPin(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.contains(KEY_SALT) && p.contains(KEY_HASH);
    }

    public static boolean isValidFormat(String pin) {
        return pin != null && pin.matches("\\d{4,8}");
    }

    public static void setPin(Context context, String pin) throws Exception {
        if (!isValidFormat(pin)) throw new IllegalArgumentException("PIN-koden måste vara 4–8 siffror");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = hash(salt, pin);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
    }

    public static boolean verify(Context context, String pin) {
        if (pin == null) return false;
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String saltText = p.getString(KEY_SALT, null);
            String hashText = p.getString(KEY_HASH, null);
            if (saltText == null || hashText == null) return false;
            byte[] salt = Base64.decode(saltText, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hashText, Base64.NO_WRAP);
            byte[] actual = hash(salt, pin);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_SALT)
                .remove(KEY_HASH)
                .apply();
    }

    private static byte[] hash(byte[] salt, String pin) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        digest.update(pin.getBytes(StandardCharsets.UTF_8));
        return digest.digest();
    }
}
