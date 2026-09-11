package se.steffy.receptboken;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import java.io.*;

public class SmokeTest extends Instrumentation {
    MatFikaActivity a;
    void onMain(Runnable action){final Throwable[] failure={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new RuntimeException(failure[0]);}
    void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle results=new Bundle();try{
        Intent i=new Intent(getTargetContext(),MatFikaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);a=(MatFikaActivity)startActivitySync(i);waitForIdleSync();
        onMain(()->{check(a.recipes.size()==16,"Expected 16 recipes");check(a.resultCount.getText().toString().contains("16"),"Home count");});
        shot("home");
        onMain(()->{a.group="Kakor & bakning";a.home();check(a.resultCount.getText().toString().contains("8"),"Baking filter");a.search.setText("kakao");check(a.resultCount.getText().toString().contains("3"),"Ingredient search");a.query="";a.group="Alla";a.detail(a.findRecipe("Kladdkaka"),8);});waitForIdleSync();shot("recipe");
        onMain(()->{MainActivity.Recipe r=a.findRecipe("Kladdkaka");String scaled=a.scale(r.ingredients,2);check(scaled.contains("200 g smör"),"Scaling");a.smartAddIngredients(scaled);check(a.shopping.size()>0,"Shopping list");r.fav=true;a.save();a.favoritesOnly=true;a.home();check(a.resultCount.getText().toString().contains("1 recept"),"Favorite filter");a.favoritesOnly=false;a.edit(null);});waitForIdleSync();
        onMain(()->{int n=0;for(int k=0;k<a.root.getChildCount();k++)if(a.root.getChildAt(k) instanceof EditText){EditText e=(EditText)a.root.getChildAt(k);if(n==0)e.setText("Testrecept");if(n==5)e.setText("1 dl mjölk\n2 dl mjöl");if(n==6){StringBuilder s=new StringBuilder();for(int j=0;j<80;j++)s.append("Steg ").append(j).append(": blanda noga.\n");e.setText(s.toString());}n++;}check(n==8,"Editor fields");});waitForIdleSync();
        onMain(()->{a.page.fullScroll(View.FOCUS_DOWN);});waitForIdleSync();shot("editor");
        onMain(()->{Button save=null;for(int k=0;k<a.root.getChildCount();k++){View v=a.root.getChildAt(k);if(v instanceof Button&&((Button)v).getText().toString().equals("Spara recept"))save=(Button)v;}check(save!=null,"Save available");save.performClick();check(a.findRecipe("Testrecept")!=null,"Save recipe");check(a.findRecipe("Testrecept").steps.contains("Steg 79"),"Long text retained");try{check(a.backupJson().getJSONArray("recipes").length()==17,"Backup recipes");}catch(Exception e){throw new RuntimeException(e);}a.recipes.remove(a.findRecipe("Testrecept"));a.save();});
        results.putString("stream","All smoke checks passed: seeds, filters, search, portions, favorites, shopping, long editor, save, backup.\n");finish(Activity.RESULT_OK,results);
    }catch(Throwable e){results.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,results);}}
    void shot(String name)throws Exception{waitForIdleSync();Bitmap b=getUiAutomation().takeScreenshot();File dir=new File(getTargetContext().getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream f=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
}
