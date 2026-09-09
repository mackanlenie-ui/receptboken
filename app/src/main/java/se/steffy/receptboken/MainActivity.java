package se.steffy.receptboken;

import android.app.*;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

public class MainActivity extends Activity {
    final int BG=Color.rgb(255,248,240), CARD=Color.WHITE, ACCENT=Color.rgb(208,91,52), TEXT=Color.rgb(45,39,35), MUTED=Color.rgb(112,103,96), RED=Color.rgb(130,50,45);
    final int PICK_IMAGE=42, EXPORT_BACKUP=43, IMPORT_BACKUP=44;
    LinearLayout root,list;
    EditText search;
    ArrayList<Recipe> recipes=new ArrayList<>();
    ArrayList<String> shopping=new ArrayList<>();
    SharedPreferences prefs;
    boolean favoritesOnly=false;
    String selectedImage="", categoryFilter="Alla", sortMode="Senast";
    final HashSet<Integer> checkedIngredients=new HashSet<>();
    CountDownTimer activeTimer;
    long timerEndsAt=0;
    TextView timerLabel;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("recipes",0);
        load();
        loadShopping();
        home();
    }

    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    int top(){int id=getResources().getIdentifier("status_bar_height","dimen","android");return (id>0?getResources().getDimensionPixelSize(id):dp(28))+dp(12);}
    GradientDrawable round(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(14));return g;}
    TextView txt(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(dp(4),dp(5),dp(4),dp(5));return v;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setBackground(round(ACCENT));return b;}
    LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(18),dp(14),dp(18),dp(14));x.setBackground(round(CARD));x.setElevation(dp(1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));x.setLayoutParams(p);return x;}
    void base(){getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);ScrollView s=new ScrollView(this);s.setFillViewport(true);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),top(),dp(18),dp(28));root.setBackgroundColor(BG);s.addView(root);setContentView(s);}
    void full(Button b,int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(m),0,dp(5));root.addView(b,p);}
    void title(String s){TextView h=txt(s,30,TEXT);h.setTypeface(null,Typeface.BOLD);root.addView(h);}
    void fullRow(View v,int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(m),0,0);root.addView(v,p);}
    LinearLayout.LayoutParams half(int l,int r){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(l),0,dp(r),0);return p;}

    void home(){
        cancelTimer();
        base();
        title("Min receptbok");
        root.addView(txt("Vad vill du laga idag?",16,MUTED));
        Button add=btn("＋ Nytt recept"), shop=btn("🛒 Lista ("+shopping.size()+")"), backup=btn("💾 Säkerhetskopia");
        full(add,12);
        LinearLayout b=new LinearLayout(this);b.addView(shop,half(0,5));b.addView(backup,half(5,0));fullRow(b,6);
        search=new EditText(this);search.setHint("Sök maträtt eller ingrediens…");search.setSingleLine();search.setTextSize(16);search.setPadding(dp(16),dp(12),dp(16),dp(12));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(12),0,dp(6));root.addView(search,sp);
        categories();
        Button sort=btn("Sortera: "+sortMode);full(sort,2);
        CheckBox fav=new CheckBox(this);fav.setText("Visa bara favoriter ★");fav.setChecked(favoritesOnly);root.addView(fav);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        render("");
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){render(s.toString());}public void afterTextChanged(android.text.Editable e){}});
        fav.setOnCheckedChangeListener((v,c)->{favoritesOnly=c;render(search.getText().toString());});
        add.setOnClickListener(v->edit(null));shop.setOnClickListener(v->shopping());backup.setOnClickListener(v->backup());
        sort.setOnClickListener(v->{sortMode=sortMode.equals("Senast")?"A–Ö":sortMode.equals("A–Ö")?"Kortast tid":"Senast";home();});
    }

    void categories(){
        root.addView(txt("Kategorier – svep åt sidan",13,MUTED));
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(true);
        LinearLayout row=new LinearLayout(this);
        LinkedHashSet<String> names=new LinkedHashSet<>();names.add("Alla");for(Recipe r:recipes)if(!r.category.trim().isEmpty())names.add(r.category.trim());
        for(String name:names){Button b=btn(name);b.setTextSize(13);if(name.equalsIgnoreCase(categoryFilter))b.setBackground(round(TEXT));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44));p.setMargins(0,0,dp(7),dp(4));row.addView(b,p);b.setOnClickListener(v->{categoryFilter=name;home();});}
        scroll.addView(row);root.addView(scroll);
    }

    void render(String query){
        list.removeAllViews();String q=query.toLowerCase(Locale.ROOT);ArrayList<Recipe> ordered=new ArrayList<>(recipes);
        if(sortMode.equals("A–Ö"))Collections.sort(ordered,(a,b)->a.name.compareToIgnoreCase(b.name));else if(sortMode.equals("Kortast tid"))Collections.sort(ordered,(a,b)->Integer.compare(a.time,b.time));
        int shown=0;
        for(Recipe r:ordered){
            if(favoritesOnly&&!r.fav)continue;
            if(!categoryFilter.equals("Alla")&&!r.category.equalsIgnoreCase(categoryFilter))continue;
            if(!q.isEmpty()&&!(r.name+" "+r.category+" "+r.ingredients).toLowerCase(Locale.ROOT).contains(q))continue;
            shown++;
            LinearLayout c=card();if(!r.image.isEmpty())c.addView(photo(r.image),new LinearLayout.LayoutParams(-1,dp(150)));
            TextView n=txt((r.fav?"★  ":"")+r.name,21,TEXT);n.setTypeface(null,Typeface.BOLD);c.addView(n);
            c.addView(txt(r.category+"  •  "+r.time+" min  •  "+amountLabel(r,r.amount),14,MUTED));
            c.setOnClickListener(v->detail(r,r.amount));list.addView(c);
        }
        if(shown==0)list.addView(txt("Inga recept hittades.",17,MUTED));
    }

    ImageView photo(String uri){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);try{im.setImageURI(Uri.parse(uri));}catch(Exception ignored){}return im;}

    void detail(Recipe r,int amount){
        cancelTimer();
        base();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Button back=btn("‹ Tillbaka");back.setOnClickListener(v->home());root.addView(back,new LinearLayout.LayoutParams(-2,-2));
        if(!r.image.isEmpty()){LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(210));ip.setMargins(0,dp(12),0,dp(8));root.addView(photo(r.image),ip);}
        title(r.name);root.addView(txt(r.category+"  •  "+r.time+" min",15,MUTED));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);Button minus=btn("−"),plus=btn("＋");TextView count=txt(amountLabel(r,amount),17,TEXT);count.setGravity(Gravity.CENTER);count.setTypeface(null,Typeface.BOLD);row.addView(minus,new LinearLayout.LayoutParams(dp(64),-2));row.addView(count,new LinearLayout.LayoutParams(0,-2,1));row.addView(plus,new LinearLayout.LayoutParams(dp(64),-2));fullRow(row,10);
        LinearLayout actions=new LinearLayout(this);Button fav=btn(r.fav?"★ Favorit":"☆ Favorit"),edit=btn("Redigera");actions.addView(fav,half(0,5));actions.addView(edit,half(5,0));fullRow(actions,8);
        Button addShop=btn("🛒 Lägg till i inköpslistan"),cook=btn("👨‍🍳 Starta tillagningsläge"),share=btn("↗ Dela recept");full(addShop,8);full(cook,3);full(share,3);
        String scaled=scale(r.ingredients,(double)amount/Math.max(1,r.amount));
        checklistSection("Ingredienser",scaled,null);
        section("Gör så här",r.steps);
        if(!r.tips.trim().isEmpty())section("Tips & förvaring",r.tips);
        minus.setOnClickListener(v->detail(r,Math.max(1,amount-1)));plus.setOnClickListener(v->detail(r,amount+1));
        fav.setOnClickListener(v->{r.fav=!r.fav;save();detail(r,amount);});edit.setOnClickListener(v->edit(r));
        cook.setOnClickListener(v->{checkedIngredients.clear();cook(r,amount,0);});
        share.setOnClickListener(v->shareRecipe(r,amount));
        addShop.setOnClickListener(v->{for(String x:scaled.split("\n"))if(!x.trim().isEmpty())shopping.add(x.trim());saveShopping();Toast.makeText(this,"Tillagt i inköpslistan",Toast.LENGTH_SHORT).show();});
    }

    void checklistSection(String h,String body,HashSet<Integer> state){
        TextView t=txt(h,22,TEXT);t.setTypeface(null,Typeface.BOLD);t.setPadding(dp(4),dp(22),dp(4),dp(5));root.addView(t);
        LinearLayout c=card();String[] lines=body.split("\n");
        for(int i=0;i<lines.length;i++){
            if(lines[i].trim().isEmpty())continue;
            final int idx=i;CheckBox cb=new CheckBox(this);cb.setText(lines[i]);cb.setTextSize(17);cb.setTextColor(TEXT);cb.setPadding(dp(4),dp(5),dp(4),dp(5));
            if(state!=null)cb.setChecked(state.contains(idx));
            cb.setOnCheckedChangeListener((v,checked)->{if(state!=null){if(checked)state.add(idx);else state.remove(idx);}});
            c.addView(cb);
        }
        root.addView(c);
    }

    void cook(Recipe r,int amount,int step){
        base();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String[] steps=r.steps.split("\n");if(steps.length==0)return;int index=Math.max(0,Math.min(step,steps.length-1));
        Button close=btn("✕ Avsluta");close.setOnClickListener(v->{cancelTimer();detail(r,amount);});root.addView(close,new LinearLayout.LayoutParams(-2,-2));
        title(r.name);root.addView(txt("Steg "+(index+1)+" av "+steps.length,17,MUTED));
        String scaled=scale(r.ingredients,(double)amount/Math.max(1,r.amount));
        checklistSection("Ingredienser – bocka av",scaled,checkedIngredients);
        LinearLayout c=card();TextView instruction=txt(steps[index],26,TEXT);instruction.setPadding(dp(12),dp(28),dp(12),dp(28));c.addView(instruction);fullRow(c,10);
        int timerMin=findTimerMinutes(steps[index]);
        if(timerMin>0){
            Button timer=btn("⏱ Starta "+timerMin+" min timer");full(timer,4);
            timerLabel=txt(timerEndsAt>System.currentTimeMillis()?remainingText():"Timer ej startad",18,TEXT);timerLabel.setGravity(Gravity.CENTER);root.addView(timerLabel);
            timer.setOnClickListener(v->startTimer(timerMin));
            if(timerEndsAt>System.currentTimeMillis())ensureTimerRunning();
        } else if(timerEndsAt>System.currentTimeMillis()){
            timerLabel=txt("⏱ Timer: "+remainingText(),18,TEXT);timerLabel.setGravity(Gravity.CENTER);root.addView(timerLabel);ensureTimerRunning();
        }
        LinearLayout nav=new LinearLayout(this);Button prev=btn("‹ Föregående"),next=btn(index==steps.length-1?"✓ Klar":"Nästa ›");nav.addView(prev,half(0,5));nav.addView(next,half(5,0));fullRow(nav,12);
        prev.setEnabled(index>0);prev.setOnClickListener(v->cook(r,amount,index-1));next.setOnClickListener(v->{if(index==steps.length-1){cancelTimer();detail(r,amount);}else cook(r,amount,index+1);});
    }

    int findTimerMinutes(String s){
        Matcher range=Pattern.compile("([0-9]+) *[–-] *([0-9]+) *min",Pattern.CASE_INSENSITIVE).matcher(s);if(range.find())return Integer.parseInt(range.group(1));
        Matcher single=Pattern.compile("([0-9]+) *min",Pattern.CASE_INSENSITIVE).matcher(s);if(single.find())return Integer.parseInt(single.group(1));
        return 0;
    }
    void startTimer(int minutes){timerEndsAt=System.currentTimeMillis()+minutes*60000L;ensureTimerRunning();Toast.makeText(this,"Timer startad: "+minutes+" minuter",Toast.LENGTH_SHORT).show();}
    void ensureTimerRunning(){
        if(activeTimer!=null)activeTimer.cancel();long remain=timerEndsAt-System.currentTimeMillis();if(remain<=0){timerEndsAt=0;return;}
        activeTimer=new CountDownTimer(remain,1000){public void onTick(long ms){if(timerLabel!=null)timerLabel.setText("⏱ "+formatMs(ms));}public void onFinish(){timerEndsAt=0;if(timerLabel!=null)timerLabel.setText("⏱ Klart!");Toast.makeText(MainActivity.this,"⏱ Timern är klar!",Toast.LENGTH_LONG).show();}}.start();
    }
    String remainingText(){long ms=Math.max(0,timerEndsAt-System.currentTimeMillis());return formatMs(ms);}
    String formatMs(long ms){long sec=(ms+999)/1000;return String.format(Locale.forLanguageTag("sv-SE"),"%d:%02d",sec/60,sec%60);}
    void cancelTimer(){if(activeTimer!=null){activeTimer.cancel();activeTimer=null;}timerEndsAt=0;timerLabel=null;}

    void shareRecipe(Recipe r,int amount){String body=r.name+"\n"+r.category+" • "+amountLabel(r,amount)+" • "+r.time+" min\n\nINGREDIENSER\n"+scale(r.ingredients,(double)amount/Math.max(1,r.amount))+"\n\nGÖR SÅ HÄR\n"+r.steps+(r.tips.trim().isEmpty()?"":"\n\nTIPS & FÖRVARING\n"+r.tips)+"\n\nDelat från Min receptbok";Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,r.name);i.putExtra(Intent.EXTRA_TEXT,body);startActivity(Intent.createChooser(i,"Dela recept med"));}
    void section(String h,String body){TextView t=txt(h,22,TEXT);t.setTypeface(null,Typeface.BOLD);t.setPadding(dp(4),dp(22),dp(4),dp(5));root.addView(t);LinearLayout c=card();c.addView(txt(body,17,TEXT));root.addView(c);}

    String amountLabel(Recipe r,int amount){String unit=r.unit==null||r.unit.trim().isEmpty()?"portioner":r.unit.trim();if(amount==1){if(unit.equalsIgnoreCase("portioner"))unit="portion";else if(unit.equalsIgnoreCase("bitar"))unit="bit";}return amount+" "+unit;}
    String scale(String src,double f){
        if(Math.abs(f-1)<.001)return src;
        StringBuilder out=new StringBuilder();
        for(String line:src.split("\n",-1)){
            String x=line;
            Matcher lead=Pattern.compile("^([ ]*)([0-9]+(?:[.,][0-9]+)?)(.*)$").matcher(x);
            if(lead.matches())x=lead.group(1)+scaledNumber(lead.group(2),f)+lead.group(3);
            Matcher par=Pattern.compile("[(]([0-9]+(?:[.,][0-9]+)?)([^)]*)[)]").matcher(x);
            StringBuffer sb=new StringBuffer();
            while(par.find())par.appendReplacement(sb,Matcher.quoteReplacement("("+scaledNumber(par.group(1),f)+par.group(2)+")"));
            par.appendTail(sb);out.append(sb).append('\n');
        }
        return out.toString().trim();
    }
    String scaledNumber(String n,double f){double v=Double.parseDouble(n.replace(',','.'))*f;if(Math.abs(v-Math.rint(v))<.001)return ""+(int)Math.rint(v);String x=String.format(Locale.US,"%.2f",v);while(x.endsWith("0"))x=x.substring(0,x.length()-1);if(x.endsWith("."))x=x.substring(0,x.length()-1);return x.replace('.',',');}

    EditText field(String hint,String val,boolean multi){EditText e=new EditText(this);e.setHint(hint);e.setText(val);e.setTextSize(16);e.setPadding(dp(12),dp(10),dp(12),dp(10));if(multi){e.setMinLines(4);e.setGravity(Gravity.TOP);}root.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    void edit(Recipe old){
        cancelTimer();base();selectedImage=old==null?"":old.image;
        Button back=btn("‹ Avbryt");back.setOnClickListener(v->{if(old==null)home();else detail(old,old.amount);});root.addView(back,new LinearLayout.LayoutParams(-2,-2));title(old==null?"Nytt recept":"Redigera recept");
        Button image=btn(selectedImage.isEmpty()?"📷 Välj bild":"📷 Byt bild");full(image,8);
        EditText name=field("Namn på maträtten",old==null?"":old.name,false),cat=field("Kategori",old==null?"":old.category,false),time=field("Tid i minuter",old==null?"":""+old.time,false),amount=field("Antal",old==null?"":""+old.amount,false),unit=field("Enhet, t.ex. portioner, st eller bitar",old==null?"portioner":old.unit,false),ing=field("Ingredienser – en per rad",old==null?"":old.ingredients,true),steps=field("Gör så här – steg för steg",old==null?"":old.steps,true),tips=field("Tips & förvaring (valfritt)",old==null?"":old.tips,true);
        Button saveBtn=btn("Spara recept");full(saveBtn,16);
        if(old!=null){Button del=btn("Ta bort recept");del.setBackground(round(RED));full(del,2);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Ta bort receptet?").setNegativeButton("Avbryt",null).setPositiveButton("Ta bort",(d,w)->{recipes.remove(old);save();home();}).show());}
        image.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_IMAGE);});
        saveBtn.setOnClickListener(v->{if(name.getText().toString().trim().isEmpty()){name.setError("Skriv ett namn");return;}Recipe r=old==null?new Recipe():old;r.name=name.getText().toString().trim();r.category=cat.getText().toString().trim();r.time=num(time,0);r.amount=Math.max(1,num(amount,1));r.unit=unit.getText().toString().trim().isEmpty()?"portioner":unit.getText().toString().trim();r.ingredients=ing.getText().toString().trim();r.steps=steps.getText().toString().trim();r.tips=tips.getText().toString().trim();r.image=selectedImage;if(old==null)recipes.add(0,r);save();detail(r,r.amount);});
    }
    int num(EditText e,int d){try{return Integer.parseInt(e.getText().toString());}catch(Exception x){return d;}}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(req==PICK_IMAGE){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}selectedImage=u.toString();Toast.makeText(this,"Bilden är vald",Toast.LENGTH_SHORT).show();}else if(req==EXPORT_BACKUP)writeBackup(u);else if(req==IMPORT_BACKUP)readBackup(u);}

    void backup(){cancelTimer();base();Button back=btn("‹ Tillbaka");back.setOnClickListener(v->home());root.addView(back,new LinearLayout.LayoutParams(-2,-2));title("Säkerhetskopia");root.addView(txt("Spara alla recept, favoriter och inköpslistan i en fil. Filen kan återställas på den här eller en annan telefon.",16,MUTED));Button export=btn("Exportera säkerhetskopia"),restore=btn("Återställ från fil");full(export,18);full(restore,5);export.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"Receptboken-backup.json");startActivityForResult(i,EXPORT_BACKUP);});restore.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT_BACKUP);});}
    JSONObject backupJson(){JSONObject o=new JSONObject();JSONArray a=new JSONArray();for(Recipe r:recipes)a.put(r.json());try{o.put("version",2);o.put("recipes",a);o.put("shopping",new JSONArray(shopping));}catch(Exception ignored){}return o;}
    void writeBackup(Uri u){try{OutputStream out=getContentResolver().openOutputStream(u);out.write(backupJson().toString(2).getBytes("UTF-8"));out.close();Toast.makeText(this,"Säkerhetskopian är sparad",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Kunde inte spara filen",Toast.LENGTH_LONG).show();}}
    void readBackup(Uri u){try{InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)out.write(buf,0,n);in.close();JSONObject o=new JSONObject(out.toString("UTF-8"));JSONArray a=o.getJSONArray("recipes");ArrayList<Recipe> restored=new ArrayList<>();for(int i=0;i<a.length();i++)restored.add(Recipe.from(a.getJSONObject(i)));recipes.clear();recipes.addAll(restored);shopping.clear();JSONArray s=o.optJSONArray("shopping");if(s!=null)for(int i=0;i<s.length();i++)shopping.add(s.getString(i));save();saveShopping();Toast.makeText(this,"Säkerhetskopian är återställd",Toast.LENGTH_LONG).show();home();}catch(Exception e){Toast.makeText(this,"Filen kunde inte läsas",Toast.LENGTH_LONG).show();}}

    void shopping(){cancelTimer();base();Button back=btn("‹ Tillbaka");back.setOnClickListener(v->home());root.addView(back,new LinearLayout.LayoutParams(-2,-2));title("Inköpslista");if(shopping.isEmpty())root.addView(txt("Listan är tom. Lägg till ingredienser från ett recept.",16,MUTED));for(String item:new ArrayList<>(shopping)){CheckBox cb=new CheckBox(this);cb.setText(item);cb.setTextSize(17);cb.setPadding(dp(8),dp(8),dp(8),dp(8));root.addView(cb);cb.setOnCheckedChangeListener((v,c)->{if(c){shopping.remove(item);saveShopping();root.postDelayed(()->shopping(),250);}});}if(!shopping.isEmpty()){Button clear=btn("Töm inköpslistan");clear.setBackground(round(RED));full(clear,16);clear.setOnClickListener(v->{shopping.clear();saveShopping();shopping();});}}
    void loadShopping(){try{JSONArray a=new JSONArray(prefs.getString("shopping","[]"));for(int i=0;i<a.length();i++)shopping.add(a.getString(i));}catch(Exception ignored){}}
    void saveShopping(){prefs.edit().putString("shopping",new JSONArray(shopping).toString()).apply();}
    void load(){String d=prefs.getString("data","");if(d.isEmpty()){seed();save();return;}try{JSONArray a=new JSONArray(d);for(int i=0;i<a.length();i++)recipes.add(Recipe.from(a.getJSONObject(i)));migrateKnownRecipes();save();}catch(Exception e){seed();save();}}
    void save(){JSONArray a=new JSONArray();for(Recipe r:recipes)a.put(r.json());prefs.edit().putString("data",a.toString()).apply();}
    void migrateKnownRecipes(){for(Recipe r:recipes){if(r.name.equalsIgnoreCase("Kladdmuffins")){r.unit="st";if(r.tips.trim().isEmpty())r.tips="Sikta gärna florsocker över vid servering. Förvara svalt eller frys in när de har svalnat helt. I frysen håller de ungefär 3–6 månader.";}else if(r.name.equalsIgnoreCase("Kladdkaka")&&r.unit.equals("portioner"))r.unit="bitar";}}

    void seed(){
        recipes.clear();
        recipes.add(new Recipe("Köttfärssås med spaghetti","Pasta",35,4,"portioner","500 g köttfärs\n1 gul lök\n2 vitlöksklyftor\n400 g krossade tomater\n2 msk tomatpuré\n1 tsk oregano\nSalt och peppar\n400 g spaghetti","1. Hacka lök och vitlök.\n2. Bryn köttfärsen och tillsätt löken.\n3. Rör ner tomater, tomatpuré och oregano.\n4. Låt sjuda 20 minuter.\n5. Koka spaghettin och servera.",""));
        recipes.add(new Recipe("Korvstroganoff","Husmanskost",25,4,"portioner","500 g falukorv\n1 gul lök\n2 msk tomatpuré\n3 dl grädde\n1 tsk senap\nSalt och peppar","1. Stek korv och lök.\n2. Tillsätt övriga ingredienser.\n3. Sjud 10 minuter.",""));
        recipes.add(new Recipe("Ugnspannkaka","Husmanskost",40,4,"portioner","4 ägg\n8 dl mjölk\n4 dl vetemjöl\n0,5 tsk salt","1. Sätt ugnen på 225 °C.\n2. Vispa ihop allt.\n3. Grädda 25–30 minuter.",""));
        recipes.add(new Recipe("Krämig kycklinggryta","Kyckling",35,4,"portioner","600 g kycklingfilé\n1 gul lök\n2 dl crème fraiche\n2 dl grädde\n1 paprika","1. Bryn kycklingen.\n2. Tillsätt resten.\n3. Sjud 15 minuter.",""));
        recipes.add(new Recipe("Kladdkaka","Efterrätt",30,8,"bitar","100 g smör\n2 ägg\n3 dl strösocker\n1,5 dl vetemjöl\n4 msk kakao","1. Sätt ugnen på 175 °C.\n2. Blanda allt.\n3. Grädda 15–18 minuter.",""));
        recipes.add(new Recipe("Kladdmuffins","Bakning",25,16,"st","100 g smör\n2 ägg\n2,5 dl strösocker\n2 tsk vaniljsocker\n0,5 tsk bakpulver\n2 krm salt\n4 msk kakao\n2 dl (120 g) vetemjöl","1. Sätt ugnen på 200 °C. Smält smöret och låt det svalna.\n2. Ställ ut 16 bakformar på en plåt.\n3. Vispa ägg och strösocker ljust och pösigt.\n4. Tillsätt vaniljsocker, bakpulver, salt och kakao och vispa till en jämn smet.\n5. Tillsätt vetemjöl och det smälta smöret. Vispa snabbt ihop smeten.\n6. Fördela smeten jämnt i formarna.\n7. Grädda mitt i ugnen i 8–10 minuter. Ta ut dem när de börjar sjunka för att behålla kladdigheten.\n8. Låt muffinsen svalna på plåten under en handduk.","Sikta gärna florsocker över vid servering. Förvara svalt eller frys in när de har svalnat helt. I frysen håller de ungefär 3–6 månader."));
    }

    static class Recipe{
        String name="",category="Övrigt",ingredients="",steps="",image="",unit="portioner",tips="";int time=0,amount=1;boolean fav=false;
        Recipe(){}
        Recipe(String n,String c,int t,int a,String u,String i,String s,String tp){name=n;category=c;time=t;amount=a;unit=u;ingredients=i;steps=s;tips=tp;}
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("name",name);o.put("category",category);o.put("time",time);o.put("amount",amount);o.put("portions",amount);o.put("unit",unit);o.put("ingredients",ingredients);o.put("steps",steps);o.put("tips",tips);o.put("fav",fav);o.put("image",image);}catch(Exception ignored){}return o;}
        static Recipe from(JSONObject o){Recipe r=new Recipe();r.name=o.optString("name");r.category=o.optString("category","Övrigt");r.time=o.optInt("time");r.amount=o.has("amount")?o.optInt("amount",1):o.optInt("portions",1);r.unit=o.optString("unit",guessUnit(r.name));r.ingredients=o.optString("ingredients");r.steps=o.optString("steps");r.tips=o.optString("tips","");r.fav=o.optBoolean("fav");r.image=o.optString("image");return r;}
        static String guessUnit(String name){String n=name==null?"":name.toLowerCase(Locale.ROOT);if(n.contains("muffin"))return "st";if(n.contains("kladdkaka"))return "bitar";return "portioner";}
    }

    @Override public void onBackPressed(){home();}
}
