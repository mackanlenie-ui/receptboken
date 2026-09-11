package se.steffy.receptboken;

import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** Receptboken with the Mat & Fika visual design, while keeping the existing Receptboken data and features. */
public class MatFikaActivity extends BatteryOptimizedActivity {
    static final int CREAM=Color.rgb(248,247,242), GREEN=Color.rgb(35,78,60), INK=Color.rgb(36,46,40), GREY=Color.rgb(102,113,105), PALE=Color.rgb(229,237,226);
    String group="Alla", query="";
    boolean atHome=false, editing=false;
    ScrollView page;
    LinearLayout shell;
    TextView resultCount;
    int columns=1;
    Recipe editingRecipe; boolean editingImported; Recipe visibleRecipe; int visibleAmount;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(state!=null){
            group=state.getString("group","Alla");query=state.getString("query","");favoritesOnly=state.getBoolean("favorites",false);
            if(state.getBoolean("editor",false)){
                Recipe old=findRecipe(state.getString("editingName",""));boolean imported=state.getBoolean("imported",false);
                edit(old,imported);ArrayList<String> values=state.getStringArrayList("fields");int n=0;
                if(values!=null)for(int k=0;k<root.getChildCount();k++)if(root.getChildAt(k) instanceof EditText&&n<values.size())((EditText)root.getChildAt(k)).setText(values.get(n++));
                selectedImage=state.getString("image","");
            }else{Recipe r=findRecipe(state.getString("detail",""));if(r!=null)detail(r,state.getInt("amount",r.amount));else home();}
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putString("group",group);state.putString("query",query);state.putBoolean("favorites",favoritesOnly);state.putBoolean("editor",editing);
        if(editing){state.putString("editingName",editingRecipe==null?"":editingRecipe.name);state.putBoolean("imported",editingImported);state.putString("image",selectedImage);ArrayList<String> values=new ArrayList<>();for(int k=0;k<root.getChildCount();k++)if(root.getChildAt(k) instanceof EditText)values.add(((EditText)root.getChildAt(k)).getText().toString());state.putStringArrayList("fields",values);}
        else if(visibleRecipe!=null){state.putString("detail",visibleRecipe.name);state.putInt("amount",visibleAmount);}
    }

    @Override GradientDrawable round(int color) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(20));return d;}
    @Override TextView txt(String s,int size,int color){TextView t=super.txt(s,size,color==TEXT?INK:color==MUTED?GREY:color);t.setFontFeatureSettings("kern");t.setLineSpacing(dp(3),1);return t;}
    @Override Button btn(String s){Button b=super.btn(s);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x224C765E),round(GREEN),null));b.setMinHeight(dp(50));b.setMinimumWidth(0);b.setStateListAnimator(null);return b;}
    Button quiet(String s){Button b=btn(s);b.setTextColor(GREEN);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x224C765E),round(PALE),null));return b;}
    @Override void title(String s){TextView t=txt(s,32,INK);t.setTypeface(Typeface.create("serif",Typeface.BOLD));t.setPadding(0,dp(18),0,dp(12));root.addView(t);}
    @Override LinearLayout card(){LinearLayout c=super.card();c.setElevation(0);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(round(Color.WHITE));return c;}

    @Override void base(){
        atHome=false;editing=false;visibleRecipe=null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(CREAM);
        page=new ScrollView(this);page.setFillViewport(true);page.setVerticalScrollBarEnabled(false);page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int windowDp=getResources().getConfiguration().screenWidthDp;
        int side=Math.max(dp(22),dp((windowDp-1120)/2));
        columns=windowDp>=1000?3:windowDp>=650?2:1;
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(side,dp(16),side,dp(30));
        root.setFocusableInTouchMode(true);page.addView(root,new ScrollView.LayoutParams(-1,-2));shell.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);
        shell.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int keyboard=insets.getInsets(WindowInsets.Type.ime()).bottom;v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard));}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        shell.requestApplyInsets();
    }

    @Override EditText field(String hint,String value,boolean multi){
        TextView label=txt(hint,14,GREY);label.setPadding(dp(3),dp(16),0,dp(7));root.addView(label);
        EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextColor(INK);e.setTextSize(17);e.setPadding(dp(16),dp(12),dp(16),dp(12));e.setBackground(round(Color.WHITE));
        e.setSingleLine(!multi);e.setHorizontallyScrolling(false);e.setVerticalScrollBarEnabled(false);e.setHorizontalScrollBarEnabled(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (multi?InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        if(hint.equals("Antal")||hint.equals("Tid i minuter"))e.setInputType(InputType.TYPE_CLASS_NUMBER);
        if(multi){e.setMinLines(4);e.setGravity(Gravity.TOP);}
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        root.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    @Override void edit(Recipe old,boolean imported){super.edit(old,imported);editing=true;editingRecipe=old;editingImported=imported;}
    @Override void edit(Recipe old){edit(old,false);}

    @Override void home(){
        cancelTimer();base();atHome=true;
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=txt("◒  RECEPTBOKEN",15,GREEN);logo.setLetterSpacing(.12f);logo.setTypeface(null,Typeface.BOLD);brand.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
        Button add=quiet("＋");add.setContentDescription("Nytt recept");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(48),dp(48));ap.setMargins(0,0,dp(6),0);brand.addView(add,ap);add.setOnClickListener(v->edit(null));
        Button more=quiet("•••");more.setContentDescription("Fler funktioner");brand.addView(more,new LinearLayout.LayoutParams(dp(54),dp(48)));root.addView(brand);
        more.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Din receptbok").setItems(new String[]{"Nytt recept","Importera foto eller PDF","Säkerhetskopiera & återställ"},(d,w)->{if(w==0)edit(null);else if(w==1)chooseRecipeImport();else backup();}).setNegativeButton("Stäng",null).show());
        title("Gott att laga.\nHärligt att baka.");
        TextView sub=txt("Dina favoriter, från vardagsmiddag till fika.",16,GREY);sub.setPadding(0,0,0,dp(20));root.addView(sub);
        LinearLayout hero=card();hero.setOrientation(LinearLayout.HORIZONTAL);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setBackground(round(GREEN));hero.setPadding(dp(20),dp(12),dp(8),dp(12));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);TextView tag=txt("EN STUND I KÖKET",10,0xFFD7E5CD);tag.setLetterSpacing(.12f);words.addView(tag);
        TextView heading=txt("Vad är du\nsugen på?",24,Color.WHITE);heading.setTypeface(Typeface.create("serif",Typeface.BOLD));words.addView(heading);words.addView(txt("Hitta ditt nästa recept ↓",12,0xFFD7E5CD));hero.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        FoodArt art=new FoodArt(false,true);hero.addView(art,new LinearLayout.LayoutParams(dp(116),dp(132)));root.addView(hero);
        search=new EditText(this);search.setSingleLine(true);search.setHint("Sök recept eller ingrediens");search.setContentDescription("Sök recept eller ingrediens");search.setTextSize(16);search.setTextColor(INK);search.setHintTextColor(GREY);search.setBackground(round(Color.WHITE));search.setPadding(dp(18),dp(14),dp(18),dp(14));search.setText(query);fullRow(search,6);
        LinearLayout filters=new LinearLayout(this);String[] groups={"Alla","Mat","Kakor & bakning"};
        for(String s:groups){Button b=s.equals(group)?btn(s):quiet(s);b.setTextSize(13);b.setPadding(dp(8),dp(6),dp(8),dp(6));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,s.equals("Kakor & bakning")?1.65f:1f);p.setMargins(0,0,dp(6),0);filters.addView(b,p);b.setOnClickListener(v->{query=search.getText().toString();group=s;home();});}fullRow(filters,14);
        LinearLayout summary=new LinearLayout(this);summary.setGravity(Gravity.CENTER_VERTICAL);resultCount=txt("",14,GREY);summary.addView(resultCount,new LinearLayout.LayoutParams(0,-2,1));Button sort=quiet("Sortera");sort.setTextSize(12);summary.addView(sort,new LinearLayout.LayoutParams(dp(86),dp(48)));fullRow(summary,14);
        sort.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Sortera recept").setItems(new String[]{"A–Ö","Kortast tid","Senast tillagda"},(d,w)->{sortMode=w==0?"A–Ö":w==1?"Kortast tid":"Senast";render(query);}).show());
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);fullRow(list,10);render(query);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();render(query);}});
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(8),dp(8),dp(8),dp(8));nav.setBackgroundColor(Color.WHITE);
        addNav(nav,"Recept",!favoritesOnly,()->{favoritesOnly=false;home();});addNav(nav,"Favoriter",favoritesOnly,()->{favoritesOnly=true;home();});addNav(nav,"Inköp",false,()->shopping());addNav(nav,"Veckomeny",false,()->weeklyMenu());shell.addView(nav,new LinearLayout.LayoutParams(-1,-2));
    }
    void addNav(LinearLayout nav,String label,boolean active,Runnable action){Button b=active?btn(label):quiet(label);b.setTextSize(11);b.setPadding(dp(3),dp(6),dp(3),dp(6));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(3),0,dp(3),0);nav.addView(b,p);b.setOnClickListener(v->action.run());}
    static boolean isBaking(Recipe r){if(r.category.toLowerCase(Locale.ROOT).startsWith("mat"))return false;String c=(r.category+" "+r.name).toLowerCase(Locale.ROOT);return c.matches(".*(bakning|kakor|kaka|muffin|bulle|bullar|chokladboll|smulpaj|sockerkaka|efterrätt).*");}
    @Override void render(String q){
        if(list==null)return;list.removeAllViews();ArrayList<Recipe> ordered=new ArrayList<>(recipes);
        if(sortMode.equals("A–Ö"))Collections.sort(ordered,(a,b)->java.text.Collator.getInstance(new Locale("sv","SE")).compare(a.name,b.name));
        else if(sortMode.equals("Kortast tid"))Collections.sort(ordered,(a,b)->Integer.compare(a.time,b.time));
        int shown=0;LinearLayout row=null;String needle=q.trim().toLowerCase(Locale.ROOT);
        for(Recipe r:ordered){if(favoritesOnly&&!r.fav)continue;if(group.equals("Mat")&&isBaking(r))continue;if(group.equals("Kakor & bakning")&&!isBaking(r))continue;if(!(r.name+" "+r.ingredients+" "+r.category).toLowerCase(Locale.ROOT).contains(needle))continue;
            if(shown%columns==0){row=new LinearLayout(this);row.setBaselineAligned(false);list.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            LinearLayout c=card();c.setPadding(0,0,0,dp(14));c.setClipToOutline(true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(0,0,dp(10),dp(14));row.addView(c,cp);
            FrameLayout imageBox=new FrameLayout(this);imageBox.setBackgroundColor(isBaking(r)?0xFFF1E3CF:PALE);imageBox.addView(r.image.isEmpty()?new FoodArt(isBaking(r),false):photo(r.image),new FrameLayout.LayoutParams(-1,-1));c.addView(imageBox,new LinearLayout.LayoutParams(-1,dp(columns==1?145:155)));
            Button fav=quiet(r.fav?"♥":"♡");fav.setContentDescription((r.fav?"Ta bort favorit: ":"Favoritmarkera: ")+r.name);fav.setTextSize(22);FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);fp.setMargins(0,dp(9),dp(9),0);imageBox.addView(fav,fp);fav.setOnClickListener(v->{r.fav=!r.fav;save();render(query);});
            TextView cat=txt(r.category.toUpperCase(new Locale("sv","SE")),10,GREEN);cat.setLetterSpacing(.09f);cat.setPadding(dp(17),dp(12),dp(17),0);c.addView(cat);
            TextView name=txt(r.name,21,INK);name.setTypeface(Typeface.create("serif",Typeface.BOLD));name.setPadding(dp(17),dp(5),dp(17),dp(4));c.addView(name);
            TextView meta=txt(r.time+" min   ·   "+amountLabel(r,r.amount),13,GREY);meta.setPadding(dp(17),0,dp(17),0);c.addView(meta);c.setOnClickListener(v->detail(r,r.amount));c.setContentDescription("Öppna recept: "+r.name);shown++;
        }
        if(row!=null&&shown%columns!=0)for(int i=shown%columns;i<columns;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(resultCount!=null)resultCount.setText((favoritesOnly?"Dina favoriter · ":"")+shown+" recept");
        if(shown==0){LinearLayout empty=card();empty.addView(txt(favoritesOnly?"Här samlas dina favoriter":"Inga recept hittades",23,INK));empty.addView(txt(favoritesOnly?"Tryck på hjärtat på ett recept för att spara det här.":"Prova ett annat sökord eller välj Alla.",16,GREY));list.addView(empty);}
    }
    @Override void detail(Recipe r,int amount){
        super.detail(r,amount);visibleRecipe=r;visibleAmount=amount;
        if(r.image.isEmpty()){FoodArt art=new FoodArt(isBaking(r),false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(180));p.setMargins(0,dp(14),0,0);root.addView(art,1,p);}
        if(getResources().getConfiguration().screenWidthDp>=800){
            int begin=-1;for(int i=0;i<root.getChildCount();i++){View v=root.getChildAt(i);if(v instanceof TextView && ((TextView)v).getText().toString().equals("Ingredienser")){begin=i;break;}}
            if(begin>=0&&root.getChildCount()>=begin+4){LinearLayout sides=new LinearLayout(this),left=new LinearLayout(this),right=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);right.setOrientation(LinearLayout.VERTICAL);for(int i=0;i<2;i++){View v=root.getChildAt(begin);root.removeViewAt(begin);left.addView(v);}while(root.getChildCount()>begin){View v=root.getChildAt(begin);root.removeViewAt(begin);right.addView(v);}sides.addView(left,half(0,12));sides.addView(right,half(12,0));root.addView(sides);}
        }
    }
    @Override void readBackup(android.net.Uri uri){new AlertDialog.Builder(this).setTitle("Återställ säkerhetskopia?").setMessage("Recept, favoriter, inköpslista och veckomeny ersätts av innehållet i filen.").setNegativeButton("Avbryt",null).setPositiveButton("Återställ",(d,w)->super.readBackup(uri)).show();}
    @Override public void onBackPressed(){if(editing){new AlertDialog.Builder(this).setTitle("Lämna utan att spara?").setNegativeButton("Fortsätt skriva",null).setPositiveButton("Lämna",(d,w)->home()).show();}else if(atHome)superFinish();else home();}
    void superFinish(){finish();}
    @Override protected void onDestroy(){cancelTimer();super.onDestroy();}

    class FoodArt extends View {
        final boolean cake,hero; final Paint p=new Paint(3);
        FoodArt(boolean cake,boolean hero){super(MatFikaActivity.this);this.cake=cake;this.hero=hero;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void oval(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);c.drawOval(x,y,x+w,y+h,p);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);Canvas c=canvas;if(!hero)c.drawColor(cake?0xFFF1E3CF:PALE);c.save();float scale=Math.min(getWidth()/320f,getHeight()/190f);c.translate((getWidth()-320*scale)/2,(getHeight()-190*scale)/2);c.scale(scale,scale);
            oval(c,62,137,203,24,hero?0xFF173B2B:0x190F3523);oval(c,48,24,224,145,0xFFFDFBF4);oval(c,61,33,198,124,0xFFE4E8DA);oval(c,69,39,182,110,0xFFF8F6ED);
            if(cake){Path path=new Path();path.moveTo(113,65);path.lineTo(206,61);path.lineTo(230,125);path.lineTo(114,132);path.close();p.setColor(0xFF683D2B);c.drawPath(path,p);Path top=new Path();top.moveTo(112,64);top.lineTo(207,48);top.lineTo(228,109);top.lineTo(114,112);top.close();p.setColor(0xFF482D25);c.drawPath(top,p);for(int i=0;i<17;i++)oval(c,121+(i*31)%94,65+(i*17)%36,3,2,0xFFF9E9D0);oval(c,84,92,21,20,0xFFB75849);oval(c,91,111,18,17,0xFFCB6754);}
            else{for(int i=0;i<13;i++){float x=91+(i*37)%130,y=53+(i*19)%61;oval(c,x,y,39,16,i%2==0?0xFF95AD6B:0xFF597F4D);}for(int i=0;i<7;i++){float x=104+(i*43)%101,y=61+(i*23)%48;oval(c,x,y,23,18,0xFFC56B4D);oval(c,x+5,y+4,11,9,0xFFEB9970);}p.setColor(0xFFEBCB85);p.setStrokeWidth(6);p.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<8;i++){float x=100+(i*23)%102,y=63+(i*29)%50;c.drawLine(x,y,x+21,y+9,p);}}
            c.restore();
        }
    }
}
