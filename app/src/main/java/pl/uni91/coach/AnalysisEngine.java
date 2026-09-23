package pl.uni91.coach;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Port stabilnej logiki z aplikacja.py. Nie modyfikuje rekordów biblioteki. */
public final class AnalysisEngine {
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "oraz", "przez", "jest", "gra", "gry", "zawodnik", "zawodnicy", "trening",
            "pod", "nad", "bez", "dla", "się", "nie", "lub", "przy", "po", "do", "na", "w", "i", "z"));

    private static final Map<String, List<String>> BEHAVIOR_KEYWORDS = new HashMap<>();
    static {
        key("A1", "otwarcie gry", "wyprowadzenie", "od bramkarza", "budowanie od bramki", "spod pressingu");
        key("A2", "przeniesienie ciężaru", "zmiana strony", "druga strona", "wolna strona", "szerokość", "ciężar gry");
        key("A3", "tworzenie sytuacji", "przewaga liczebna", "ostatnie podanie", "sytuacja bramkowa");
        key("A4", "finalizacja", "wykończenie", "wykończyć", "nie wykorzystaliśmy", "nie potrafiliśmy wykończyć", "przed bramką", "brak skuteczności", "strzał", "skuteczność", "bramka");
        key("O1", "wysoki pressing", "pressing wysoki", "doskok wysoko", "odbiór wysoko");
        key("O2", "blok 1 5 2 1", "blok 1-5-2-1", "niski blok", "średni blok", "odbudowa bloku", "kompaktowy blok", "kompaktowego bloku", "po minięciu pressingu", "zaciśnięta pięść");
        key("O3", "obrona bramki", "ochrona bramki", "bronienie pola karnego");
        key("T-", "reakcja po stracie", "po stracie", "kontrpressing", "natychmiastowy odbiór");
        key("T+", "pierwsza decyzja po odbiorze", "po odbiorze", "atak po odbiorze");
        key("MEN", "reakcja na błąd", "mental", "zaangażowanie", "pewność siebie", "po straconej bramce", "presja wyniku");
        key("A5", "utrzymanie pod presją", "spokój pod presją", "utrzymanie piłki", "gra pod presją");
        key("A6", "gra między liniami", "między liniami", "za linią pomocy", "w półprzestrzeni");
        key("A7", "trzeci zawodnik", "gra na trzeciego", "podaj i rusz", "trzecia opcja");
        key("A8", "orientacja ciała", "przyjęcie kierunkowe", "ustawienie ciała", "pierwszy kontakt");
        key("A9", "przewaga na boku", "dwa na jeden na boku", "2v1 na boku", "skrzydło");
        key("A10", "zmiana kierunku ataku", "zmiana kierunku", "atak w inną stronę", "odwrócenie gry");
        key("A11", "szerokość w otwarciu", "szeroko w otwarciu", "rozciągnięcie ustawienia", "boczni szeroko");
        key("O4", "pressing zespołowy", "sygnał pressingu", "pressing kierunkowy", "jeden rusza");
        key("O5", "asekuracja", "zabezpiecza partnera", "drugi obrońca", "wsparcie w obronie");
        key("O6", "kompaktowość", "odległości między formacjami", "odległości między zawodnikami", "zamknięcie środka");
        key("O7", "zabezpieczenie po ataku", "zabezpieczenie za piłką", "rest defence", "ochrona przed kontrą");
        key("T-2", "odzyskaj albo zorganizuj blok", "odzysk lub powrót", "pięć sekund", "5 sekund");
        key("T+2", "odbiór i wyjście spod presji", "wyjście po odbiorze", "po odbiorze pod presją");
        key("F1", "finalizacja w przewadze", "przewaga przed bramką", "szybki atak", "kontratak do finalizacji");
        key("F2", "finalizacja po wycofaniu", "podanie wycofujące", "wycofanie spod linii końcowej", "cutback");
        key("PER", "skanowanie", "percepcja przestrzeni", "ruch głową", "nie widzi partnera", "brak informacji", "przed przyjęciem");
    }

    private final JSONArray fea;
    private final JSONArray motor;
    private final JSONArray behaviors;

    public AnalysisEngine(JSONArray fea, JSONArray motor, JSONArray behaviors) {
        this.fea = fea;
        this.motor = motor;
        this.behaviors = behaviors;
    }

    public List<BehaviorOption> behaviorOptions() throws JSONException {
        List<BehaviorOption> result = new ArrayList<>();
        for (int i = 0; i < behaviors.length(); i++) {
            JSONObject item = behaviors.getJSONObject(i);
            result.add(new BehaviorOption(item.optString("Kod"), item.optString("Problem"),
                    item.optString("Zachowanie docelowe")));
        }
        return result;
    }

    public List<LibraryItem> libraryItems(boolean motorItems) throws JSONException {
        JSONArray source = motorItems ? motor : fea;
        String idField = motorItems ? "Kod" : "ID";
        List<LibraryItem> result = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.getJSONObject(i);
            result.add(new LibraryItem(item.optString(idField), item.optString("Nazwa"),
                    item.optString("Status", "AKTYWNY"), motorItems, item));
        }
        return result;
    }

    public TrainingPlan replaceBlock(TrainingPlan plan, int index, LibraryItem replacement,
                                     String observation, String area) throws JSONException {
        if (index < 0 || index >= plan.blocks.size()) throw new JSONException("Nieprawidłowy blok planu");
        TrainingBlock old = plan.blocks.get(index);
        if (old.role.equals("Motoryka") != replacement.motor) throw new JSONException("Niezgodny typ środka");
        List<TrainingBlock> blocks = new ArrayList<>(plan.blocks);
        blocks.set(index, block(old.role, replacement.source, old.minutes, plan.problem,
                plan.decision, plan.weak, observation, area));
        return new TrainingPlan(plan.day, plan.problem, plan.decision, plan.weak,
                plan.behaviorCode, plan.behaviorName, plan.targetBehavior, plan.confidence,
                new ArrayList<>(plan.axes), blocks);
    }

    private static void key(String code, String... words) {
        BEHAVIOR_KEYWORDS.put(code, Arrays.asList(words));
    }

    public ScoreProfile analyzeScore(int r, int w, int p, int g, String problem, String observation, String area) {
        Map<String, Integer> values = new HashMap<>();
        values.put("R", r); values.put("W", w); values.put("P", p); values.put("G", g);
        String weak = "R";
        for (String code : Arrays.asList("R", "W", "P", "G")) {
            if (values.get(code) < values.get(weak)) weak = code;
        }
        double average = (r + w + p + g) / 4.0;
        int minimum = Collections.min(values.values());
        String decision;
        if (minimum <= 1) decision = "CHANGE";
        else if (average >= 4.25 && minimum >= 4) decision = "PROGRESS";
        else if ((weak.equals("P") || weak.equals("G")) && values.get(weak) <= 2) decision = "CONTINUE";
        else if (average >= 3.15) decision = "CONTINUE";
        else if (average >= 2.25) decision = "REGRESS";
        else decision = "CHANGE";

        String cause;
        if (weak.equals("R")) cause = "Zawodnicy nie rozpoznają jeszcze wystarczająco wcześnie informacji potrzebnej do działania.";
        else if (weak.equals("W")) cause = "Zawodnicy rozpoznają rozwiązanie, ale jakość wykonania ogranicza skuteczność.";
        else if (weak.equals("P")) cause = "Zachowanie działa w łatwiejszym środowisku, ale zanika przy presji czasu lub przeciwnika.";
        else cause = "Zachowanie nie przenosi się jeszcze spontanicznie do gry właściwej.";

        Map<String, String> actions = new HashMap<>();
        actions.put("PROGRESS", "zwiększyć presję, tempo lub złożoność bez zmiany głównej zasady");
        actions.put("CONTINUE", "utrzymać priorytet, ale mocniej trafić w najsłabsze ogniwo");
        actions.put("REGRESS", "uprościć środowisko i zwiększyć liczbę czytelnych powtórzeń");
        actions.put("CHANGE", "zmienić bodziec/środek, zachowując problem jako punkt odniesienia");
        return new ScoreProfile(decision, weak, average, classify(problem, observation, area), cause, actions.get(decision));
    }

    public BehaviorMatch inferBehavior(String problem, String observation) throws JSONException {
        String text = lower(problem + " " + observation).replace('–', ' ').replace('—', ' ');
        Set<String> textTokens = tokens(text);
        List<ScoredJson> ranked = new ArrayList<>();
        for (int i = 0; i < behaviors.length(); i++) {
            JSONObject behavior = behaviors.getJSONObject(i);
            String code = behavior.optString("Kod");
            int score = 0;
            for (String phrase : BEHAVIOR_KEYWORDS.getOrDefault(code, Collections.emptyList())) {
                String normalized = lower(phrase);
                if (text.contains(normalized)) score += normalized.contains(" ") ? 8 : 4;
                for (String token : tokens(normalized)) if (similarToken(textTokens, token)) score += 2;
            }
            for (String token : tokens(behavior.optString("Problem"))) if (similarToken(textTokens, token)) score += 2;
            for (String token : tokens(behavior.optString("Zachowanie docelowe"))) if (similarToken(textTokens, token)) score += 1;
            ranked.add(new ScoredJson(score, i, behavior));
        }
        ranked.sort(ScoredJson.DESCENDING);
        ScoredJson best = ranked.isEmpty() ? new ScoredJson(0, 0, new JSONObject()) : ranked.get(0);
        double second = ranked.size() > 1 ? ranked.get(1).score : 0;
        String confidence = best.score >= 12 && best.score >= second + 3 ? "WYSOKA" : best.score >= 6 ? "ŚREDNIA" : "NISKA";
        List<JSONObject> alternatives = new ArrayList<>();
        for (int i = 1; i < Math.min(4, ranked.size()); i++) if (ranked.get(i).score > 0) alternatives.add(ranked.get(i).json);
        return new BehaviorMatch(best.json, best.score, confidence, alternatives);
    }

    public TrainingPlan buildPlan(String day, String problem, String decision, String weak,
                                  String observation, String area, Set<String> previouslyUsed,
                                  String behaviorOverride) throws JSONException {
        BehaviorMatch inferred = inferBehavior(problem, observation);
        JSONObject behavior = behaviorOverride == null || behaviorOverride.isEmpty()
                ? inferred.behavior : behaviorByCode(behaviorOverride);
        List<JSONObject> picks = chooseFea(problem, decision, weak, observation, area,
                previouslyUsed == null ? Collections.emptySet() : previouslyUsed, behavior, 4);
        JSONObject mot = chooseMotor(problem, weak, observation, area, behavior);
        if (picks.size() < 4 || mot.length() == 0) throw new JSONException("Brak środków do zbudowania planu");

        int[] minutes = day.equals("Piątek") ? new int[]{10, 7, 13, 15, 20} : new int[]{12, 10, 20, 18, 22};
        String[] roles = {"Aktywacja", "Motoryka", "Główna I", "Główna II", "Gra / transfer"};
        JSONObject transfer = findBy(fea, "ID", "FEA-20");
        JSONObject[] selected = {picks.get(0), mot, picks.get(1), picks.get(2), transfer.length() > 0 ? transfer : picks.get(3)};
        List<TrainingBlock> blocks = new ArrayList<>();
        for (int i = 0; i < roles.length; i++) blocks.add(block(roles[i], selected[i], minutes[i], problem, decision, weak, observation, area));
        return new TrainingPlan(day, problem, decision, weak, behavior.optString("Kod"),
                behavior.optString("Problem"), behavior.optString("Zachowanie docelowe"),
                inferred.confidence, classify(problem, observation, area), blocks);
    }

    private List<JSONObject> chooseFea(String problem, String decision, String weak, String observation,
                                       String area, Set<String> used, JSONObject behavior, int amount) throws JSONException {
        List<ScoredJson> ranked = new ArrayList<>();
        for (int i = 0; i < fea.length(); i++) {
            JSONObject item = fea.getJSONObject(i);
            if (!"AKTYWNY".equals(item.optString("Status", "AKTYWNY"))) continue;
            ranked.add(new ScoredJson(semanticFit(item, problem, decision, weak, observation, area), i, item));
        }
        ranked.sort(ScoredJson.DESCENDING);
        List<JSONObject> picks = new ArrayList<>();
        for (String field : Arrays.asList("FEA 1", "FEA 2")) {
            String id = behavior.optString(field);
            JSONObject item = findBy(fea, "ID", id);
            if (item.length() > 0 && !((decision.equals("CHANGE") || decision.equals("PROGRESS")) && used.contains(id))) picks.add(item);
        }
        for (ScoredJson candidate : ranked) {
            String id = candidate.json.optString("ID");
            if ((decision.equals("CHANGE") || decision.equals("PROGRESS")) && used.contains(id)) continue;
            if (!containsId(picks, "ID", id)) picks.add(candidate.json);
            if (picks.size() >= amount) break;
        }
        for (ScoredJson candidate : ranked) {
            if (picks.size() >= amount) break;
            if (!containsId(picks, "ID", candidate.json.optString("ID"))) picks.add(candidate.json);
        }
        return picks;
    }

    private JSONObject chooseMotor(String problem, String weak, String observation, String area, JSONObject behavior) throws JSONException {
        String preferred = behavior.optString("Motoryka");
        JSONObject preferredItem = findBy(motor, "Kod", preferred);
        if (preferredItem.length() > 0 && "AKTYWNY".equals(preferredItem.optString("Status", "AKTYWNY"))) return preferredItem;
        ScoredJson best = null;
        for (int i = 0; i < motor.length(); i++) {
            JSONObject item = motor.getJSONObject(i);
            if (!"AKTYWNY".equals(item.optString("Status", "AKTYWNY"))) continue;
            ScoredJson scored = new ScoredJson(relevance(item, problem, weak, observation, area), i, item);
            if (best == null || ScoredJson.DESCENDING.compare(scored, best) < 0) best = scored;
        }
        return best == null ? new JSONObject() : best.json;
    }

    private TrainingBlock block(String role, JSONObject item, int minutes, String problem, String decision,
                                String weak, String observation, String area) throws JSONException {
        String id = item.has("ID") ? item.optString("ID") : item.optString("Kod");
        String goal = first(item, "Cel / percepcja", "Cel/percepcja", "Akcent");
        String reason = id.startsWith("FEA-") ? selectionReason(item, problem, decision, weak, observation, area)
                : "Motoryka dobrana do obszaru " + weak + " i problemu treningowego.";
        return new TrainingBlock(role, id, item.optString("Nazwa"), minutes,
                first(item, "Pole", "Organizacja"), goal, item.optString("Organizacja"),
                first(item, "Przebieg i zasady", "Coaching / bezpieczeństwo"),
                first(item, "Pytania", "Coaching / bezpieczeństwo"),
                first(item, "Progresja", "Objętość"), reason);
    }

    private double semanticFit(JSONObject item, String problem, String decision, String weak, String observation, String area) {
        DrillProfile profile = drillProfile(item);
        TargetProfile target = targetProfile(weak, decision);
        List<String> problemAxes = classify(problem, observation, area);
        int axisScore = profile.axes.contains(target.axis) ? 5 : intersects(profile.axes, problemAxes) ? 3 : 1;
        int pressureScore = Math.max(0, 5 - Math.abs(profile.pressure - target.pressure));
        int transferScore = Math.max(0, 5 - Math.abs(profile.transfer - target.transfer));
        double lexical = Math.min(5, relevance(item, problem, weak, observation, area) / 3.0);
        return axisScore * 3 + pressureScore * 2 + transferScore * 2 + lexical;
    }

    private String selectionReason(JSONObject item, String problem, String decision, String weak, String observation, String area) {
        DrillProfile profile = drillProfile(item); TargetProfile target = targetProfile(weak, decision);
        double score = semanticFit(item, problem, decision, weak, observation, area);
        return String.format(Locale.forLanguageTag("pl-PL"), "%s pasuje do etapu %s: profil %s, presja %d/5 (cel %d/5), transfer %d/5 (cel %d/5). Decyzja: %s. Dopasowanie %.1f pkt.",
                item.optString("ID", item.optString("Kod")), weak, join(profile.axes), profile.pressure,
                target.pressure, profile.transfer, target.transfer, decision, score);
    }

    private DrillProfile drillProfile(JSONObject item) {
        String text = jsonText(item); List<String> axes = new ArrayList<>();
        if (containsAny(text, "percepc", "skan", "zobacz", "woln", "przestrze", "partner", "przeciwnik")) axes.add("PER");
        if (containsAny(text, "decyz", "przewag", "moment", "kierunk", "zmiana", "przenie", "wybór", "wybor")) axes.add("DEC");
        if (containsAny(text, "podanie", "przyję", "strzał", "final", "techn", "prowadzenie", "wykon")) axes.add("EXE");
        if (axes.isEmpty()) axes.add("DEC");
        int pressure = 1 + countPresent(text, "press", "presją", "obrońc", "4v4", "5v5", "6v6", "8v8", "czas", "kontakt");
        int transfer = 1 + countPresent(text, "transfer", "gra właściwa", "mecz", "8v8", "swobod", "br+", "bramk");
        return new DrillProfile(axes, Math.min(5, pressure), Math.min(5, transfer));
    }

    private TargetProfile targetProfile(String weak, String decision) {
        String axis = weak.equals("R") ? "PER" : weak.equals("W") ? "EXE" : "DEC";
        int pressure = weak.equals("R") ? 1 : weak.equals("W") ? 2 : 4;
        int transfer = weak.equals("R") ? 1 : weak.equals("W") ? 2 : weak.equals("P") ? 3 : 5;
        int shift = decision.equals("REGRESS") ? -1 : decision.equals("PROGRESS") ? 1 : 0;
        return new TargetProfile(axis, clamp(pressure + shift), clamp(transfer + shift));
    }

    private int relevance(JSONObject item, String problem, String weak, String observation, String area) {
        String text = jsonText(item); int score = 0;
        for (String word : tokens(problem)) if (text.contains(word)) score += 3;
        List<String> axes = classify(problem, observation, area);
        Map<String, String[]> cues = new HashMap<>();
        cues.put("PER", new String[]{"percepc", "skan", "woln", "przestrze", "partner"});
        cues.put("DEC", new String[]{"decyz", "moment", "przewag", "zmiana centrum", "kierunk"});
        cues.put("EXE", new String[]{"wykon", "final", "podanie", "przyję", "strzał", "techn"});
        cues.put("PRESSURE", new String[]{"press", "presją", "4v4", "5v5", "6v6", "8v8", "obrońc"});
        cues.put("TRANSFER", new String[]{"transfer", "swobod", "8v8", "gra właściwa", "mecz"});
        cues.put("MENTAL", new String[]{"reakcj", "kryzys", "błąd", "zaangaż"});
        for (String axis : axes) for (String cue : cues.getOrDefault(axis, new String[0])) if (text.contains(cue)) score += 2;
        String weakAxis = weak.equals("R") ? "PER" : weak.equals("W") ? "EXE" : weak.equals("P") ? "PRESSURE" : "TRANSFER";
        for (String cue : cues.getOrDefault(weakAxis, new String[0])) if (text.contains(cue)) score += 2;
        return score;
    }

    public List<String> classify(String problem, String observation, String area) {
        String text = lower(problem + " " + observation + " " + area); List<String> axes = new ArrayList<>();
        if (containsAny(text, "skan", "widzi", "zobacz", "przestrze", "ustawienie", "partner", "przeciwnik", "woln")) axes.add("PER");
        if (containsAny(text, "decyz", "moment", "kiedy", "wybór", "wybor", "przenie", "zmiana centrum", "utrzymać", "atakować")) axes.add("DEC");
        if (containsAny(text, "wykon", "podanie", "przyję", "strzał", "final", "techn", "dokład", "prowadzenie")) axes.add("EXE");
        if (containsAny(text, "pres", "press", "nacisk", "tempo", "pod presją", "chaos")) axes.add("PRESSURE");
        if (containsAny(text, "mecz", "transfer", "swobod", "znika", "nie pojawia", "bez pomocy")) axes.add("TRANSFER");
        if (containsAny(text, "odwaga", "pewność", "zaangaż", "reakcja po błędzie", "mental")) axes.add("MENTAL");
        if (axes.isEmpty()) axes.add("DEC"); return axes;
    }

    private JSONObject behaviorByCode(String code) throws JSONException { return findBy(behaviors, "Kod", code); }
    private static JSONObject findBy(JSONArray array, String field, String value) throws JSONException {
        for (int i = 0; i < array.length(); i++) if (value.equals(array.getJSONObject(i).optString(field))) return array.getJSONObject(i);
        return new JSONObject();
    }
    private static boolean containsId(List<JSONObject> list, String key, String id) { for (JSONObject x : list) if (id.equals(x.optString(key))) return true; return false; }
    private static boolean intersects(List<String> a, List<String> b) { for (String x : a) if (b.contains(x)) return true; return false; }
    private static String first(JSONObject item, String... keys) { for (String key : keys) { String value=item.optString(key); if (!value.isEmpty()) return value; } return ""; }
    private static int clamp(int value) { return Math.max(1, Math.min(5, value)); }
    private static int countPresent(String text, String... values) { int n=0; for (String value:values) if(text.contains(value)) n++; return n; }
    private static boolean containsAny(String text, String... values) { for (String value:values) if(text.contains(value)) return true; return false; }
    private static String join(List<String> values) { return String.join(", ", values); }
    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private static String jsonText(JSONObject value) {
        StringBuilder text = new StringBuilder();
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) text.append(' ').append(value.opt(keys.next()));
        return lower(text.toString());
    }
    private static Set<String> tokens(String value) {
        String clean = lower(value).replaceAll("[/–—,.;:()\\[\\]+]", " "); Set<String> result = new LinkedHashSet<>();
        for (String word : clean.split("\\s+")) if (word.length() > 3 && !STOP.contains(word)) result.add(word);
        return result;
    }
    private static boolean similarToken(Set<String> text, String query) {
        for (String token : text) if (token.equals(query) || (token.length() >= 5 && query.length() >= 5 && token.substring(0,5).equals(query.substring(0,5)))) return true;
        return false;
    }

    private static final class ScoredJson {
        static final Comparator<ScoredJson> DESCENDING = (a,b) -> a.score == b.score ? Integer.compare(a.order,b.order) : Double.compare(b.score,a.score);
        final double score; final int order; final JSONObject json;
        ScoredJson(double score, int order, JSONObject json) { this.score=score; this.order=order; this.json=json; }
    }
    private static final class DrillProfile { final List<String> axes; final int pressure,transfer; DrillProfile(List<String>a,int p,int t){axes=a;pressure=p;transfer=t;} }
    private static final class TargetProfile { final String axis; final int pressure,transfer; TargetProfile(String a,int p,int t){axis=a;pressure=p;transfer=t;} }

    public static final class BehaviorMatch {
        public final JSONObject behavior; public final double score; public final String confidence; public final List<JSONObject> alternatives;
        BehaviorMatch(JSONObject b,double s,String c,List<JSONObject>a){behavior=b;score=s;confidence=c;alternatives=a;}
    }
    public static final class BehaviorOption {
        public final String code,name,target;
        BehaviorOption(String c,String n,String t){code=c;name=n;target=t;}
        @Override public String toString(){return name;}
    }
    public static final class LibraryItem {
        public final String id,name,status; public final boolean motor; final JSONObject source;
        LibraryItem(String i,String n,String s,boolean m,JSONObject o){id=i;name=n;status=s;motor=m;source=o;}
        public String searchableText(){return lower(source.toString());}
        public String detail(){
            if(motor) return first(source,"Akcent")+"\n"+first(source,"Organizacja")+"\n"+first(source,"Coaching / bezpieczeństwo");
            return first(source,"Cel / percepcja","Cel/percepcja")+"\n"+first(source,"Organizacja")+"\n"+first(source,"Przebieg i zasady");
        }
        @Override public String toString(){return id+" • "+name;}
    }
    public static final class ScoreProfile {
        public final String decision,weak,cause,action; public final double average; public final List<String> axes;
        ScoreProfile(String d,String w,double a,List<String>x,String c,String ac){decision=d;weak=w;average=a;axes=x;cause=c;action=ac;}
    }
    public static final class TrainingBlock {
        public final String role,id,name,field,goal,organization,course,questions,progression,selectionReason; public final int minutes;
        TrainingBlock(String r,String i,String n,int m,String f,String g,String o,String c,String q,String p,String s){role=r;id=i;name=n;minutes=m;field=f;goal=g;organization=o;course=c;questions=q;progression=p;selectionReason=s;}
    }
    public static final class TrainingPlan {
        public final String day,problem,decision,weak,behaviorCode,behaviorName,targetBehavior,confidence; public final List<String> axes; public final List<TrainingBlock> blocks;
        TrainingPlan(String d,String p,String dc,String w,String bc,String bn,String tb,String cf,List<String>a,List<TrainingBlock>b){day=d;problem=p;decision=dc;weak=w;behaviorCode=bc;behaviorName=bn;targetBehavior=tb;confidence=cf;axes=a;blocks=b;}
    }
}
