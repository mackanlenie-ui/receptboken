package se.steffy.receptboken;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Battery/performance optimized launcher.
 * Keeps all functionality from SafeMainActivity, but avoids keeping the screen
 * awake while merely reading a recipe and loads recipe images as cached,
 * downsampled previews instead of decoding full-resolution images repeatedly.
 */
public class BatteryOptimizedActivity extends SafeMainActivity {

    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(12 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    @Override
    void detail(Recipe r, int amount) {
        cancelTimer();
        base();
        // Important battery change: ordinary recipe reading follows the phone's
        // normal screen timeout. Only the dedicated cooking mode keeps it awake.
        Button back = btn("‹ Tillbaka");
        back.setOnClickListener(v -> home());
        root.addView(back, new LinearLayout.LayoutParams(-2, -2));

        if (!r.image.isEmpty()) {
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, dp(210));
            ip.setMargins(0, dp(12), 0, dp(8));
            root.addView(photo(r.image), ip);
        }

        title(r.name);
        root.addView(txt(r.category + "  •  " + r.time + " min", 15, MUTED));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = btn("−"), plus = btn("＋");
        TextView count = txt(amountLabel(r, amount), 17, TEXT);
        count.setGravity(Gravity.CENTER);
        count.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(minus, new LinearLayout.LayoutParams(dp(64), -2));
        row.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(plus, new LinearLayout.LayoutParams(dp(64), -2));
        fullRow(row, 10);

        LinearLayout actions = new LinearLayout(this);
        Button fav = btn(r.fav ? "★ Favorit" : "☆ Favorit"), edit = btn("Redigera");
        actions.addView(fav, half(0, 5));
        actions.addView(edit, half(5, 0));
        fullRow(actions, 8);

        Button addShop = btn("🛒 Lägg till i inköpslistan");
        Button cook = btn("👨‍🍳 Starta tillagningsläge");
        Button share = btn("↗ Dela recept");
        full(addShop, 8);
        full(cook, 3);
        full(share, 3);

        String scaled = scale(r.ingredients, (double) amount / Math.max(1, r.amount));
        checklistSection("Ingredienser", scaled, null);
        section("Gör så här", r.steps);
        if (!r.tips.trim().isEmpty()) section("Tips & förvaring", r.tips);

        minus.setOnClickListener(v -> detail(r, Math.max(1, amount - 1)));
        plus.setOnClickListener(v -> detail(r, amount + 1));
        fav.setOnClickListener(v -> {
            r.fav = !r.fav;
            save();
            detail(r, amount);
        });
        edit.setOnClickListener(v -> edit(r));
        cook.setOnClickListener(v -> {
            checkedIngredients.clear();
            cook(r, amount, 0);
        });
        share.setOnClickListener(v -> shareRecipe(r, amount));
        addShop.setOnClickListener(v -> {
            smartAddIngredients(scaled);
            saveShopping();
            Toast.makeText(this, "Ingredienserna är tillagda och dubbletter har slagits ihop", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    ImageView photo(String uriString) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(BG);
        if (uriString == null || uriString.trim().isEmpty()) return image;

        image.setTag(uriString);
        Bitmap cached = getCached(uriString);
        if (cached != null && !cached.isRecycled()) {
            image.setImageBitmap(cached);
            return image;
        }

        imageExecutor.execute(() -> {
            Bitmap bitmap = decodePreview(uriString);
            if (bitmap == null) return;
            putCached(uriString, bitmap);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Object tag = image.getTag();
                if (uriString.equals(tag)) image.setImageBitmap(bitmap);
            });
        });
        return image;
    }

    private Bitmap getCached(String key) {
        synchronized (imageCache) {
            return imageCache.get(key);
        }
    }

    private void putCached(String key, Bitmap bitmap) {
        synchronized (imageCache) {
            Bitmap old = imageCache.get(key);
            if (old == null || old.isRecycled()) imageCache.put(key, bitmap);
        }
    }

    private Bitmap decodePreview(String value) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = openPreviewStream(value)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int reqWidth = Math.min(1080, Math.max(720, getResources().getDisplayMetrics().widthPixels));
            int reqHeight = Math.max(600, dp(220));
            int sample = 1;
            while ((bounds.outWidth / sample) > reqWidth * 3 / 2 ||
                   (bounds.outHeight / sample) > reqHeight * 3 / 2) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = openPreviewStream(value)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        }
    }

    private InputStream openPreviewStream(String value) throws Exception {
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
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            synchronized (imageCache) {
                imageCache.evictAll();
            }
        }
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        synchronized (imageCache) {
            imageCache.evictAll();
        }
        super.onDestroy();
    }
}
