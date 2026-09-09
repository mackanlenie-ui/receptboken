package se.steffy.receptboken;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

public class RecipeSeedProvider extends ContentProvider {
    private static final String PREFS = "recipes";
    private static final String DATA = "data";
    private static final String KLADDMUFFINS = "Kladdmuffins";

    @Override
    public boolean onCreate() {
        if (getContext() == null) return true;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, 0);
        String stored = prefs.getString(DATA, "");

        try {
            JSONArray recipes;
            if (stored == null || stored.trim().isEmpty()) {
                recipes = defaultRecipes();
            } else {
                recipes = new JSONArray(stored);
                if (!containsRecipe(recipes, KLADDMUFFINS)) {
                    recipes.put(kladdmuffins());
                }
            }
            prefs.edit().putString(DATA, recipes.toString()).apply();
        } catch (Exception ignored) {
            // Bevara alltid användarens befintliga recept om filen inte kan läsas.
        }
        return true;
    }

    private JSONArray defaultRecipes() throws Exception {
        JSONArray a = new JSONArray();
        a.put(recipe("Köttfärssås med spaghetti", "Pasta", 35, 4,
                "500 g köttfärs\n1 gul lök\n2 vitlöksklyftor\n400 g krossade tomater\n2 msk tomatpuré\n1 tsk oregano\nSalt och peppar\n400 g spaghetti",
                "1. Hacka lök och vitlök.\n2. Bryn köttfärsen och tillsätt löken.\n3. Rör ner tomater, tomatpuré och oregano.\n4. Låt sjuda 20 minuter.\n5. Koka spaghettin och servera."));
        a.put(recipe("Korvstroganoff", "Husmanskost", 25, 4,
                "500 g falukorv\n1 gul lök\n2 msk tomatpuré\n3 dl grädde\n1 tsk senap\nSalt och peppar",
                "1. Stek korv och lök.\n2. Tillsätt övriga ingredienser.\n3. Sjud 10 minuter."));
        a.put(recipe("Ugnspannkaka", "Husmanskost", 40, 4,
                "4 ägg\n8 dl mjölk\n4 dl vetemjöl\n0,5 tsk salt",
                "1. Sätt ugnen på 225 °C.\n2. Vispa ihop allt.\n3. Grädda 25–30 minuter."));
        a.put(recipe("Krämig kycklinggryta", "Kyckling", 35, 4,
                "600 g kycklingfilé\n1 gul lök\n2 dl crème fraiche\n2 dl grädde\n1 paprika",
                "1. Bryn kycklingen.\n2. Tillsätt resten.\n3. Sjud 15 minuter."));
        a.put(recipe("Kladdkaka", "Efterrätt", 30, 8,
                "100 g smör\n2 ägg\n3 dl strösocker\n1,5 dl vetemjöl\n4 msk kakao",
                "1. Sätt ugnen på 175 °C.\n2. Blanda allt.\n3. Grädda 15–18 minuter."));
        a.put(kladdmuffins());
        return a;
    }

    private JSONObject kladdmuffins() throws Exception {
        return recipe(
                KLADDMUFFINS,
                "Bakning",
                25,
                16,
                "100 g smör\n2 ägg\n2,5 dl strösocker\n2 tsk vaniljsocker\n0,5 tsk bakpulver\n2 krm salt\n4 msk kakao\n2 dl (120 g) vetemjöl",
                "1. Sätt ugnen på 200 °C. Smält smöret och låt det svalna.\n" +
                "2. Ställ ut 16 bakformar på en plåt.\n" +
                "3. Vispa ägg och strösocker ljust och pösigt.\n" +
                "4. Tillsätt vaniljsocker, bakpulver, salt och kakao och vispa till en jämn smet.\n" +
                "5. Tillsätt vetemjöl och det smälta smöret. Vispa snabbt ihop smeten.\n" +
                "6. Fördela smeten jämnt i formarna.\n" +
                "7. Grädda mitt i ugnen i 8–10 minuter. Ta ut dem när de börjar sjunka för att behålla kladdigheten.\n" +
                "8. Låt muffinsen svalna på plåten under en handduk."
        );
    }

    private JSONObject recipe(String name, String category, int time, int portions,
                              String ingredients, String steps) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("category", category);
        o.put("time", time);
        o.put("portions", portions);
        o.put("ingredients", ingredients);
        o.put("steps", steps);
        o.put("fav", false);
        o.put("image", "");
        return o;
    }

    private boolean containsRecipe(JSONArray a, String name) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && name.equalsIgnoreCase(o.optString("name"))) return true;
        }
        return false;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
