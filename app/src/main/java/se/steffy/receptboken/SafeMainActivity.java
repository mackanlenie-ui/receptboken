package se.steffy.receptboken;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Backup-safe launcher activity.
 *
 * Version 4 backups embed a display-sized copy of each recipe image directly
 * in the JSON file. Old backups are still accepted; inaccessible legacy image
 * URIs are simply ignored instead of making the whole restore fail.
 */
public class SafeMainActivity extends MainActivity {

    private static final int BACKUP_IMAGE_MAX_PX = 1600;
    private static final int BACKUP_JPEG_QUALITY = 88;

    @Override
    JSONObject backupJson() {
        JSONObject root = new JSONObject();
        JSONArray recipeArray = new JSONArray();

        for (Recipe recipe : recipes) {
            JSONObject item = recipe.json();
            if (recipe.image != null && !recipe.image.trim().isEmpty()) {
                try {
                    byte[] imageBytes = makeBackupImage(recipe.image);
                    if (imageBytes != null && imageBytes.length > 0) {
                        item.put("imageData", Base64.encodeToString(imageBytes, Base64.NO_WRAP));
                        item.put("imageMime", "image/jpeg");
                    }
                } catch (Exception | OutOfMemoryError ignored) {
                    // The recipe must still be backed up even if its image cannot be read.
                }
            }
            recipeArray.put(item);
        }

        try {
            root.put("version", 4);
            root.put("recipes", recipeArray);
            root.put("shopping", new JSONArray(shopping));
            JSONObject week = new JSONObject();
            for (String day : DAYS) {
                String name = weekMenu.get(day);
                if (name != null) week.put(day, name);
            }
            root.put("week", week);
        } catch (Exception ignored) {
        }
        return root;
    }

    private byte[] makeBackupImage(String uriString) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = openImageStream(uriString)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > BACKUP_IMAGE_MAX_PX * 2) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap decoded;
        try (InputStream in = openImageStream(uriString)) {
            if (in == null) return null;
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) return null;

        Bitmap output = decoded;
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        int largest = Math.max(width, height);
        if (largest > BACKUP_IMAGE_MAX_PX) {
            float scale = (float) BACKUP_IMAGE_MAX_PX / largest;
            int newWidth = Math.max(1, Math.round(width * scale));
            int newHeight = Math.max(1, Math.round(height * scale));
            output = Bitmap.createScaledBitmap(decoded, newWidth, newHeight, true);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        output.compress(Bitmap.CompressFormat.JPEG, BACKUP_JPEG_QUALITY, bytes);
        if (output != decoded) output.recycle();
        decoded.recycle();
        return bytes.toByteArray();
    }

    private InputStream openImageStream(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) return null;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isEmpty()) return new FileInputStream(new File(value));
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path == null || path.isEmpty()) return null;
            return new FileInputStream(new File(path));
        }
        return getContentResolver().openInputStream(uri);
    }

    @Override
    void readBackup(Uri uri) {
        try {
            byte[] raw;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new Exception("Ingen filström");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
                raw = out.toByteArray();
            }

            JSONObject root = new JSONObject(new String(raw, "UTF-8"));
            JSONArray array = root.optJSONArray("recipes");
            if (array == null) throw new JSONException("recipes saknas");

            ArrayList<Recipe> restoredRecipes = new ArrayList<>();
            int skippedImages = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                Recipe recipe = Recipe.from(item);
                String embedded = item.optString("imageData", "");
                if (!embedded.isEmpty()) {
                    String restoredImage = restoreEmbeddedImage(embedded, i);
                    if (!restoredImage.isEmpty()) recipe.image = restoredImage;
                    else {
                        recipe.image = "";
                        skippedImages++;
                    }
                } else if (recipe.image != null && !recipe.image.isEmpty()) {
                    String safeLegacyUri = validateLegacyImage(recipe.image);
                    if (safeLegacyUri.isEmpty()) skippedImages++;
                    recipe.image = safeLegacyUri;
                }
                restoredRecipes.add(recipe);
            }

            ArrayList<String> restoredShopping = new ArrayList<>();
            JSONArray shoppingArray = root.optJSONArray("shopping");
            if (shoppingArray != null) {
                for (int i = 0; i < shoppingArray.length(); i++) {
                    String value = shoppingArray.optString(i, "");
                    if (!value.isEmpty()) restoredShopping.add(value);
                }
            }

            LinkedHashMap<String, String> restoredWeek = new LinkedHashMap<>();
            JSONObject week = root.optJSONObject("week");
            if (week != null) {
                for (String day : DAYS) {
                    String value = week.optString(day, "");
                    if (!value.isEmpty()) restoredWeek.put(day, value);
                }
            }

            recipes.clear();
            recipes.addAll(restoredRecipes);
            shopping.clear();
            shopping.addAll(restoredShopping);
            weekMenu.clear();
            weekMenu.putAll(restoredWeek);
            save();
            saveShopping();
            saveWeek();

            String message = "Säkerhetskopian är återställd";
            if (skippedImages > 0) {
                message += ". " + skippedImages + (skippedImages == 1 ? " gammal bild kunde inte läsas" : " gamla bilder kunde inte läsas");
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            home();
        } catch (Exception | OutOfMemoryError e) {
            Toast.makeText(this, "Filen kunde inte läsas", Toast.LENGTH_LONG).show();
        }
    }

    private String restoreEmbeddedImage(String base64, int index) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (bytes.length == 0) return "";
            File dir = new File(getFilesDir(), "recipe_images");
            if (!dir.exists() && !dir.mkdirs()) return "";
            File file = new File(dir, "restored_" + System.currentTimeMillis() + "_" + index + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            return Uri.fromFile(file).toString();
        } catch (Exception | OutOfMemoryError e) {
            return "";
        }
    }

    private String validateLegacyImage(String value) {
        try (InputStream in = openImageStream(value)) {
            if (in == null) return "";
            // Reading one byte verifies that the app still has access to the URI/file.
            in.read();
            return value;
        } catch (Exception | OutOfMemoryError e) {
            return "";
        }
    }

    @Override
    ImageView photo(String uriString) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (uriString == null || uriString.isEmpty()) return image;
        try {
            if (!validateLegacyImage(uriString).isEmpty()) image.setImageURI(Uri.parse(uriString));
        } catch (Exception | OutOfMemoryError ignored) {
        }
        return image;
    }
}
