package pl.uni91.coach;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private FrameLayout content;
    private TextView headerTitle;
    private TextView headerSubtitle;
    private AnalysisEngine engine;
    private AppStateStore stateStore;
    private JSONObject state;
    private JSONObject draftMatch;
    private List<AnalysisEngine.BehaviorOption> behaviorOptions = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showWelcomeScreen();
    }

    private void showWelcomeScreen() {
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        ImageView welcome = new ImageView(this);
        welcome.setImageResource(R.drawable.splash_coach);
        welcome.setScaleType(ImageView.ScaleType.CENTER_CROP);
        welcome.setContentDescription("Dotknij, aby otworzyć FEA Coach System");
        welcome.setBackgroundColor(getColor(R.color.fea_green_dark));
        setContentView(welcome);
        welcome.setOnClickListener(v -> openSystem());
    }

    private void openSystem() {
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return windowInsets;
        });
        content = findViewById(R.id.content);
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        initializeSystem();
    }

    private void initializeSystem() {
        try {
            AssetRepository repository = new AssetRepository(this);
            repository.validate();
            engine = new AnalysisEngine(repository.loadFea(), repository.loadMotor(), repository.loadBehaviors());
            EngineParityCheck.run(engine);
            behaviorOptions = engine.behaviorOptions();
            stateStore = new AppStateStore(this);
            state = stateStore.load();
            showHome();
        } catch (Exception error) {
            showFatalError(error.getMessage());
        }
    }

    private void showHome() {
        setHeader("FEA COACH SYSTEM", "Co teraz wymaga decyzji trenera?");
        LinearLayout body = page();
        body.addView(title("Mecz → diagnoza → trening"));
        body.addView(paragraph("Opisz najważniejszy problem z meczu. System rozpozna zachowanie i przygotuje pierwszy trening mikrocyklu."));
        Button analysis = primaryButton("ROZPOCZNIJ ANALIZĘ MECZU");
        analysis.setOnClickListener(v -> showMatchAnalysis());
        body.addView(analysis, marginTop(24));
        Button library = secondaryButton("BIBLIOTEKA ŚRODKÓW");
        library.setOnClickListener(v -> showLibrary(false, ""));
        body.addView(library, marginTop(12));
        Button history = secondaryButton("HISTORIA DECYZJI");
        history.setOnClickListener(v -> showHistory());
        body.addView(history, marginTop(8));
        if (state != null && state.optJSONObject("plans") != null && state.optJSONObject("plans").length() > 0) {
            Button resume = secondaryButton("KONTYNUUJ MIKROCYKL");
            resume.setOnClickListener(v -> showMicrocycle());
            body.addView(resume, marginTop(12));
        }
        body.addView(infoCard("Baza gotowa", "40 FEA  •  40 Motoryka  •  26 zachowań  •  80 grafik"), marginTop(20));
        body.addView(infoCard("Workflow", "MECZ → PRIORYTET → PON → SCORE → ŚR → SCORE → PT → WERYFIKACJA"), marginTop(12));
        render(body);
    }

    private void showMatchAnalysis() {
        setHeader("Analiza meczu", "Wskaż jeden najważniejszy problem");
        LinearLayout body = page();
        Button back = secondaryButton("← Strona główna");
        back.setOnClickListener(v -> showHome());
        body.addView(back);

        EditText opponent = field("Przeciwnik");
        EditText result = field("Wynik");
        EditText problem = field("Najważniejszy problem meczowy");
        problem.setMinLines(3); problem.setGravity(android.view.Gravity.TOP);
        EditText observation = field("Opis sytuacji – kiedy problem występował?");
        observation.setMinLines(3); observation.setGravity(android.view.Gravity.TOP);
        Spinner area = spinner(list("PER – percepcja", "DEC – decyzja", "EXE – wykonanie", "MENTAL", "MIX"));

        List<String> correctionLabels = new ArrayList<>();
        correctionLabels.add("AUTOMATYCZNIE Z OPISU");
        for (AnalysisEngine.BehaviorOption option : behaviorOptions) correctionLabels.add(option.name);
        Spinner correction = spinner(correctionLabels);

        body.addView(label("Przeciwnik"), marginTop(20)); body.addView(opponent);
        body.addView(label("Wynik"), marginTop(12)); body.addView(result);
        body.addView(label("Najważniejszy problem meczowy"), marginTop(12)); body.addView(problem);
        body.addView(label("Opis sytuacji"), marginTop(12)); body.addView(observation);
        body.addView(label("Dominujący obszar"), marginTop(12)); body.addView(area);
        body.addView(label("Korekta zachowania – opcjonalnie"), marginTop(12)); body.addView(correction);

        Button analyze = primaryButton("ANALIZUJ I UTWÓRZ PONIEDZIAŁEK");
        analyze.setOnClickListener(v -> {
            String problemText = problem.getText().toString().trim();
            if (problemText.isEmpty()) {
                problem.setError("Opisz najważniejszy problem z meczu"); problem.requestFocus(); return;
            }
            try {
                String override = correction.getSelectedItemPosition() == 0 ? null
                        : behaviorOptions.get(correction.getSelectedItemPosition() - 1).code;
                AnalysisEngine.TrainingPlan plan = engine.buildPlan("Poniedziałek", problemText,
                        "CONTINUE", "R", observation.getText().toString().trim(),
                        String.valueOf(area.getSelectedItem()), Collections.emptySet(), override);
                draftMatch = new JSONObject();
                draftMatch.put("opponent", opponent.getText().toString().trim());
                draftMatch.put("result", result.getText().toString().trim());
                draftMatch.put("problem", problemText);
                draftMatch.put("observation", observation.getText().toString().trim());
                draftMatch.put("area", String.valueOf(area.getSelectedItem()));
                draftMatch.put("behaviorCode", plan.behaviorCode);
                showPlan(draftMatch.optString("opponent"), draftMatch.optString("result"), plan);
            } catch (Exception error) {
                Toast.makeText(this, "Nie udało się utworzyć treningu: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        body.addView(analyze, marginTop(24));
        body.addView(paragraph("System nie zmienia opisów środków. Jeśli automatycznie rozpoznane zachowanie jest niewłaściwe, wybierz ręczną korektę z listy."), marginTop(12));
        render(body);
    }

    private void showPlan(String opponent, String result, AnalysisEngine.TrainingPlan plan) {
        setHeader("Trening • " + plan.day, "Priorytet mikrocyklu");
        LinearLayout body = page();
        Button newAnalysis = secondaryButton("← Popraw analizę");
        newAnalysis.setOnClickListener(v -> showMatchAnalysis());
        body.addView(newAnalysis);
        if (!opponent.isEmpty() || !result.isEmpty()) body.addView(paragraph(opponent + (result.isEmpty() ? "" : "  •  " + result)), marginTop(16));
        body.addView(title(plan.problem), marginTop(12));
        body.addView(infoCard("Rozpoznane zachowanie", plan.behaviorName + "\nPewność: " + plan.confidence + "\nCel: " + plan.targetBehavior), marginTop(16));
        body.addView(infoCard("Diagnoza wejściowa", "Decyzja: " + plan.decision + "  •  obszar: " + plan.weak + "\n" + TextUtils.join(", ", plan.axes)), marginTop(12));
        body.addView(title("Plan treningu"), marginTop(24));
        for (AnalysisEngine.TrainingBlock block : plan.blocks) body.addView(trainingCard(block), marginTop(14));
        Button save = primaryButton("ZAPISZ I PRZEJDŹ DO MIKROCYKLU");
        save.setOnClickListener(v -> saveInitialPlan(plan));
        body.addView(save, marginTop(24));
        render(body);
    }

    private void saveInitialPlan(AnalysisEngine.TrainingPlan plan) {
        try {
            state = new JSONObject();
            state.put("match", draftMatch == null ? new JSONObject() : draftMatch);
            JSONObject plans = new JSONObject(); plans.put("Poniedziałek", PlanCodec.toJson(plan));
            state.put("plans", plans); state.put("scores", new JSONObject()); state.put("history", new JSONArray());
            stateStore.save(state); showMicrocycle();
        } catch (Exception error) {
            Toast.makeText(this, "Błąd zapisu: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showMicrocycle() {
        setHeader("Mikrocykl", "Poniedziałek → Środa → Piątek");
        LinearLayout body = page();
        Button home = secondaryButton("← Strona główna"); home.setOnClickListener(v -> showHome()); body.addView(home);
        JSONObject match = state.optJSONObject("match"); JSONObject plans = state.optJSONObject("plans"); JSONObject scores = state.optJSONObject("scores");
        body.addView(title(match == null ? "Aktualny mikrocykl" : match.optString("problem", "Aktualny mikrocykl")), marginTop(18));
        for (String day : list("Poniedziałek", "Środa", "Piątek")) {
            if (plans != null && plans.has(day)) {
                try {
                    AnalysisEngine.TrainingPlan plan = PlanCodec.fromJson(plans.getJSONObject(day));
                    String status = scores != null && scores.has(day) ? "Training Score zapisany" : "Oczekuje na Training Score";
                    MaterialCardView card = infoCard(day, plan.blocks.size() + " bloków  •  " + status);
                    card.setOnClickListener(v -> showMicrocycleDay(day)); body.addView(card, marginTop(14));
                } catch (Exception error) { body.addView(paragraph(day + ": błąd odczytu planu"), marginTop(14)); }
            } else {
                body.addView(infoCard(day, day.equals("Środa") ? "Powstanie po ocenie poniedziałku" : "Powstanie po ocenie środy"), marginTop(14));
            }
        }
        render(body);
    }

    private void showMicrocycleDay(String day) {
        try {
            AnalysisEngine.TrainingPlan plan = PlanCodec.fromJson(state.getJSONObject("plans").getJSONObject(day));
            setHeader(day, "Plan aktualnego mikrocyklu"); LinearLayout body = page();
            Button back = secondaryButton("← Mikrocykl"); back.setOnClickListener(v -> showMicrocycle()); body.addView(back);
            body.addView(title(plan.problem), marginTop(16));
            for (int i=0;i<plan.blocks.size();i++) {
                AnalysisEngine.TrainingBlock block=plan.blocks.get(i); body.addView(trainingCard(block), marginTop(14));
                Button replace=secondaryButton("PODMIEŃ " + block.id); final int blockIndex=i;
                replace.setOnClickListener(v -> showReplacement(day,plan,blockIndex)); body.addView(replace,marginTop(6));
            }
            Button pdf=secondaryButton("POBIERZ KONSPEKT PDF • 2×A4");
            pdf.setOnClickListener(v->{try{sharePdf(new PdfExporter(this).trainingPlan(plan));}catch(Exception error){Toast.makeText(this,"Błąd PDF: "+error.getMessage(),Toast.LENGTH_LONG).show();}});
            body.addView(pdf,marginTop(18));
            JSONObject savedScore=state.optJSONObject("scores").optJSONObject(day);
            if(savedScore!=null){
                Button scorePdf=secondaryButton("POBIERZ TRAINING SCORE PDF");scorePdf.setOnClickListener(v->{try{sharePdf(new PdfExporter(this).trainingScore(day,plan,savedScore));}catch(Exception error){Toast.makeText(this,"Błąd PDF: "+error.getMessage(),Toast.LENGTH_LONG).show();}});body.addView(scorePdf,marginTop(8));
                Button kpiPdf=secondaryButton("POBIERZ KPI PDF");kpiPdf.setOnClickListener(v->{try{sharePdf(new PdfExporter(this).kpi(day,plan,savedScore));}catch(Exception error){Toast.makeText(this,"Błąd PDF: "+error.getMessage(),Toast.LENGTH_LONG).show();}});body.addView(kpiPdf,marginTop(8));
            }
            Button score = primaryButton("UZUPEŁNIJ TRAINING SCORE"); score.setOnClickListener(v -> showTrainingScore(day, plan));
            body.addView(score, marginTop(22)); render(body);
        } catch (Exception error) { showFatalError(error.getMessage()); }
    }

    private void showTrainingScore(String day, AnalysisEngine.TrainingPlan plan) {
        setHeader("Training Score • " + day, "Oceń efekt, nie atrakcyjność ćwiczenia"); LinearLayout body = page();
        Button back = secondaryButton("← Plan treningu"); back.setOnClickListener(v -> showMicrocycleDay(day)); body.addView(back);
        body.addView(paragraph("1 = bardzo słabo, 5 = zachowanie działa samodzielnie i pod presją."), marginTop(16));
        Spinner r=scoreSpinner(), w=scoreSpinner(), p=scoreSpinner(), g=scoreSpinner();
        body.addView(label("R – rozumienie i percepcja"),marginTop(14)); body.addView(r);
        body.addView(label("W – wykonanie"),marginTop(12)); body.addView(w);
        body.addView(label("P – zachowanie pod presją"),marginTop(12)); body.addView(p);
        body.addView(label("G – transfer do gry"),marginTop(12)); body.addView(g);
        List<Spinner> blockScores=new ArrayList<>(); body.addView(title("Ocena środków"),marginTop(22));
        for(AnalysisEngine.TrainingBlock block:plan.blocks){ Spinner s=scoreSpinner(); blockScores.add(s); body.addView(label(block.id+" • "+block.name),marginTop(10)); body.addView(s); }
        EditText observation=field("Co działało, a co znikało pod presją?"); observation.setMinLines(3); observation.setGravity(android.view.Gravity.TOP);
        body.addView(label("Obserwacja trenera"),marginTop(18)); body.addView(observation);
        Button analyze=primaryButton(day.equals("Piątek")?"ZAPISZ OCENĘ PIĄTKU":"ANALIZUJ I UTWÓRZ KOLEJNY TRENING");
        analyze.setOnClickListener(v -> saveTrainingScore(day,plan,value(r),value(w),value(p),value(g),blockScores,observation.getText().toString().trim()));
        body.addView(analyze,marginTop(22)); render(body);
    }

    private void showLibrary(boolean motorItems, String query) {
        setHeader("Biblioteka", motorItems ? "40 środków motorycznych" : "40 środków FEA");
        LinearLayout body=page(); Button home=secondaryButton("← Strona główna");home.setOnClickListener(v->showHome());body.addView(home);
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button feaButton=secondaryButton("FEA (40)"),motorButton=secondaryButton("MOTORYKA (40)");
        feaButton.setOnClickListener(v->showLibrary(false,""));motorButton.setOnClickListener(v->showLibrary(true,""));
        tabs.addView(feaButton,new LinearLayout.LayoutParams(0,-2,1));tabs.addView(motorButton,new LinearLayout.LayoutParams(0,-2,1));body.addView(tabs,marginTop(14));
        EditText search=field("Szukaj po nazwie, celu lub organizacji");search.setText(query);body.addView(search,marginTop(14));
        Button searchButton=primaryButton("SZUKAJ");searchButton.setOnClickListener(v->showLibrary(motorItems,search.getText().toString().trim()));body.addView(searchButton,marginTop(8));
        try {
            String needle=query.toLowerCase(java.util.Locale.ROOT);int shown=0;
            for(AnalysisEngine.LibraryItem item:engine.libraryItems(motorItems)){
                if(!needle.isEmpty()&&!item.searchableText().contains(needle))continue;
                body.addView(libraryCard(item),marginTop(14));shown++;
            }
            if(shown==0)body.addView(paragraph("Brak środków pasujących do wyszukiwania."),marginTop(20));
        } catch(Exception error){body.addView(paragraph("Błąd biblioteki: "+error.getMessage()),marginTop(16));}
        render(body);
    }

    private MaterialCardView libraryCard(AnalysisEngine.LibraryItem item){
        MaterialCardView card=card();LinearLayout box=vertical(14);box.addView(label(item.id+" • "+item.name));
        try(InputStream input=getAssets().open("images/"+item.id+".png")){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(BitmapFactory.decodeStream(input));box.addView(image,fixedHeight(175,10));}catch(Exception ignored){}
        box.addView(paragraph(item.detail()),marginTop(10));box.addView(paragraph("Status: "+item.status),marginTop(8));card.addView(box);return card;
    }

    private void showReplacement(String day,AnalysisEngine.TrainingPlan plan,int index){
        AnalysisEngine.TrainingBlock old=plan.blocks.get(index);boolean motor=old.role.equals("Motoryka");
        setHeader("Podmiana środka",old.role+" • "+old.id);LinearLayout body=page();
        Button back=secondaryButton("← Wróć bez zmiany");back.setOnClickListener(v->showMicrocycleDay(day));body.addView(back);
        body.addView(infoCard("Aktualny środek",old.id+" • "+old.name),marginTop(16));
        try{
            List<AnalysisEngine.LibraryItem> choices=new ArrayList<>();List<String> labels=new ArrayList<>();
            for(AnalysisEngine.LibraryItem item:engine.libraryItems(motor))if("AKTYWNY".equals(item.status)){choices.add(item);labels.add(item.toString());}
            Spinner selection=spinner(labels);int current=0;for(int i=0;i<choices.size();i++)if(choices.get(i).id.equals(old.id))current=i;selection.setSelection(current);
            body.addView(label("Nowy środek"),marginTop(18));body.addView(selection);
            EditText reason=field("Powód podmiany");reason.setText("Lepsze dopasowanie do aktualnego problemu");body.addView(label("Powód podmiany"),marginTop(14));body.addView(reason);
            Button confirm=primaryButton("ZATWIERDŹ PODMIANĘ");confirm.setOnClickListener(v->{
                try{AnalysisEngine.LibraryItem chosen=choices.get(selection.getSelectedItemPosition());JSONObject match=state.getJSONObject("match");
                    AnalysisEngine.TrainingPlan changed=engine.replaceBlock(plan,index,chosen,match.optString("observation"),match.optString("area","MIX"));
                    state.getJSONObject("plans").put(day,PlanCodec.toJson(changed));JSONObject event=new JSONObject();event.put("day",day);event.put("from",old.id);event.put("to",chosen.id);event.put("reason",reason.getText().toString().trim());event.put("type","manual_change");event.put("time",System.currentTimeMillis());state.getJSONArray("history").put(event);stateStore.save(state);showMicrocycleDay(day);
                }catch(Exception error){Toast.makeText(this,"Błąd podmiany: "+error.getMessage(),Toast.LENGTH_LONG).show();}
            });body.addView(confirm,marginTop(22));
            body.addView(paragraph("Podmiana wstawia oryginalną kartę i grafikę środka. Opis biblioteczny nie jest modyfikowany."),marginTop(12));
        }catch(Exception error){body.addView(paragraph("Błąd listy środków: "+error.getMessage()),marginTop(16));}
        render(body);
    }

    private void showHistory(){
        setHeader("Historia decyzji","Training Score i ręczne podmiany");LinearLayout body=page();
        Button home=secondaryButton("← Strona główna");home.setOnClickListener(v->showHome());body.addView(home);
        JSONArray history=state==null?null:state.optJSONArray("history");
        if(history==null||history.length()==0){body.addView(paragraph("Historia pojawi się po pierwszym Training Score lub ręcznej podmianie."),marginTop(20));render(body);return;}
        for(int i=history.length()-1;i>=0;i--){JSONObject event=history.optJSONObject(i);if(event==null)continue;
            if("manual_change".equals(event.optString("type")))body.addView(infoCard(event.optString("day")+" • podmiana",event.optString("from")+" → "+event.optString("to")+"\n"+event.optString("reason")),marginTop(12));
            else{JSONObject score=event.optJSONObject("score");if(score!=null)body.addView(infoCard(event.optString("day")+" • Training Score","R/W/P/G: "+score.optInt("R")+"/"+score.optInt("W")+"/"+score.optInt("P")+"/"+score.optInt("G")+"\nDecyzja: "+score.optString("decision")+" • słaby obszar: "+score.optString("weak")),marginTop(12));}
        }
        render(body);
    }

    private void sharePdf(File file){
        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");intent.putExtra(Intent.EXTRA_STREAM,uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent,"Zapisz lub udostępnij PDF"));
    }

    private void saveTrainingScore(String day, AnalysisEngine.TrainingPlan plan, int r,int w,int p,int g,
                                   List<Spinner> blockSpinners,String observation) {
        try {
            AnalysisEngine.ScoreProfile profile=engine.analyzeScore(r,w,p,g,plan.problem,observation,state.getJSONObject("match").optString("area","MIX"));
            JSONObject score=new JSONObject(); score.put("R",r);score.put("W",w);score.put("P",p);score.put("G",g);
            score.put("decision",profile.decision);score.put("weak",profile.weak);score.put("average",profile.average);score.put("observation",observation);
            JSONObject blockRatings=new JSONObject(); for(int i=0;i<plan.blocks.size();i++) blockRatings.put(plan.blocks.get(i).id,value(blockSpinners.get(i)));
            score.put("blocks",blockRatings); state.getJSONObject("scores").put(day,score);
            String next=day.equals("Poniedziałek")?"Środa":day.equals("Środa")?"Piątek":null;
            if(next!=null){ Set<String> used=new HashSet<>(); for(AnalysisEngine.TrainingBlock b:plan.blocks) if(b.id.startsWith("FEA-")) used.add(b.id);
                JSONObject match=state.getJSONObject("match");
                AnalysisEngine.TrainingPlan nextPlan=engine.buildPlan(next,plan.problem,profile.decision,profile.weak,observation,match.optString("area","MIX"),used,match.optString("behaviorCode"));
                state.getJSONObject("plans").put(next,PlanCodec.toJson(nextPlan));
            }
            JSONObject historyItem=new JSONObject();historyItem.put("day",day);historyItem.put("score",score);historyItem.put("time",System.currentTimeMillis());state.getJSONArray("history").put(historyItem);
            stateStore.save(state); showScoreResult(day,profile,next);
        } catch(Exception error){Toast.makeText(this,"Błąd analizy: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void showScoreResult(String day, AnalysisEngine.ScoreProfile profile, String next) {
        setHeader("Wynik Training Score",day); LinearLayout body=page();
        body.addView(infoCard("Decyzja: "+profile.decision,"Najsłabszy obszar: "+profile.weak+"  •  średnia: "+String.format(java.util.Locale.forLanguageTag("pl-PL"),"%.2f",profile.average)),marginTop(8));
        body.addView(infoCard("Diagnoza",profile.cause+"\n\nZalecenie: "+profile.action),marginTop(12));
        if(next!=null) body.addView(infoCard("Utworzono: "+next,"Kolejna jednostka została dopasowana do wyniku R/W/P/G."),marginTop(12));
        else body.addView(infoCard("Mikrocykl treningowy zakończony","Kolejnym krokiem będzie weryfikacja zachowania w następnym meczu."),marginTop(12));
        Button cycle=primaryButton("WRÓĆ DO MIKROCYKLU");cycle.setOnClickListener(v->showMicrocycle());body.addView(cycle,marginTop(22));render(body);
    }

    private Spinner scoreSpinner(){Spinner s=spinner(list("1","2","3","4","5"));s.setSelection(2);return s;}
    private int value(Spinner spinner){return Integer.parseInt(String.valueOf(spinner.getSelectedItem()));}

    private MaterialCardView trainingCard(AnalysisEngine.TrainingBlock block) {
        MaterialCardView card = card();
        LinearLayout box = vertical(16);
        TextView role = label(block.role + "  •  " + block.minutes + " min"); role.setTextColor(getColor(R.color.fea_green));
        box.addView(role);
        TextView name = title(block.id + "  " + block.name); name.setTextSize(19); box.addView(name, marginTop(5));
        try (InputStream input = getAssets().open("images/" + block.id + ".png")) {
            ImageView image = new ImageView(this); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(BitmapFactory.decodeStream(input)); box.addView(image, fixedHeight(190, 10));
        } catch (Exception ignored) { }
        box.addView(section("Pole / organizacja", block.field));
        box.addView(section("Cel", block.goal));
        box.addView(section("Przebieg", block.course));
        box.addView(section("Coaching", block.questions));
        box.addView(section("Progresja", block.progression));
        box.addView(section("Dlaczego ten środek", block.selectionReason));
        card.addView(box); return card;
    }

    private void showFatalError(String message) {
        setHeader("FEA Coach", "Błąd uruchomienia");
        LinearLayout body = page(); body.addView(title("Nie można uruchomić systemu"));
        TextView error = paragraph(message == null ? "Nieznany błąd" : message); error.setTextColor(0xFFB3261E); body.addView(error, marginTop(12)); render(body);
    }

    private void setHeader(String title, String subtitle) { headerTitle.setText(title); headerSubtitle.setText(subtitle); }
    private void render(LinearLayout body) { ScrollView scroll=new ScrollView(this); scroll.addView(body); content.removeAllViews(); content.addView(scroll); }
    private LinearLayout page() { LinearLayout v=vertical(20); v.setPadding(dp(20),dp(20),dp(20),dp(36)); return v; }
    private LinearLayout vertical(int padding) { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(padding),dp(padding),dp(padding),dp(padding)); v.setLayoutParams(new ViewGroup.LayoutParams(-1,-2)); return v; }
    private TextView title(String text) { TextView v=new TextView(this); v.setText(text); v.setTextSize(24); v.setTextColor(0xFF071B3A); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v; }
    private TextView label(String text) { TextView v=new TextView(this); v.setText(text); v.setTextSize(15); v.setTextColor(0xFF17355E); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v; }
    private TextView paragraph(String text) { TextView v=new TextView(this); v.setText(text); v.setTextSize(16); v.setTextColor(0xFF42597A); v.setLineSpacing(0,1.15f); return v; }
    private EditText field(String hint) { EditText v=new EditText(this); v.setHint(hint); v.setTextSize(16); v.setPadding(dp(12),dp(12),dp(12),dp(12)); v.setBackgroundColor(Color.WHITE); return v; }
    private Spinner spinner(List<String> values) { Spinner v=new Spinner(this); v.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values)); v.setBackgroundColor(Color.WHITE); v.setPadding(dp(8),dp(8),dp(8),dp(8)); return v; }
    private Button primaryButton(String text) { Button b=new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackgroundColor(getColor(R.color.fea_green)); b.setMinHeight(dp(54)); return b; }
    private Button secondaryButton(String text) { Button b=new Button(this); b.setText(text); b.setTextColor(getColor(R.color.fea_green_dark)); b.setBackgroundColor(Color.WHITE); return b; }
    private MaterialCardView card() { MaterialCardView c=new MaterialCardView(this); c.setRadius(dp(14)); c.setCardElevation(dp(2)); c.setCardBackgroundColor(Color.WHITE); return c; }
    private MaterialCardView infoCard(String heading,String text) { MaterialCardView c=card(); LinearLayout box=vertical(16); box.addView(label(heading)); box.addView(paragraph(text),marginTop(7)); c.addView(box); return c; }
    private TextView section(String heading,String text) { TextView v=paragraph(heading+":\n"+(text==null||text.isEmpty()?"—":text)); v.setPadding(0,dp(10),0,0); return v; }
    private LinearLayout.LayoutParams marginTop(int value) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(value); return p; }
    private LinearLayout.LayoutParams fixedHeight(int height,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height)); p.topMargin=dp(top); return p; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private static List<String> list(String... values) { List<String> result=new ArrayList<>(); Collections.addAll(result,values); return result; }
}
