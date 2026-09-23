package pl.uni91.coach;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class PdfExporter {
    private static final int WIDTH=842, HEIGHT=595, MARGIN=24;
    private final Context context;
    public PdfExporter(Context context){this.context=context.getApplicationContext();}

    public File trainingPlan(AnalysisEngine.TrainingPlan plan) throws Exception {
        PdfDocument pdf=new PdfDocument();
        for(int pageNo=0;pageNo<2;pageNo++){
            PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,pageNo+1).create());
            Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);header(canvas,"FEA COACH • "+plan.day,plan.problem,pageNo+1,2);
            int start=pageNo==0?0:3,end=pageNo==0?Math.min(3,plan.blocks.size()):plan.blocks.size();
            int y=78;for(int i=start;i<end;i++){drawBlock(canvas,plan.blocks.get(i),y);y+=pageNo==0?164:224;}
            footer(canvas,"Decyzja: "+plan.decision+" • obszar: "+plan.weak+" • zachowanie: "+plan.behaviorName);
            pdf.finishPage(page);
        }
        return write(pdf,"FEA_Konspekt_"+safe(plan.day)+".pdf");
    }

    public File trainingScore(String day, AnalysisEngine.TrainingPlan plan, JSONObject score) throws Exception {
        PdfDocument pdf=new PdfDocument();PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,1).create());
        Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);header(canvas,"TRAINING SCORE • "+day,plan.problem,1,1);
        Paint p=paint(18,true,0xFF071B3A);canvas.drawText("R  "+score.optInt("R")+"     W  "+score.optInt("W")+"     P  "+score.optInt("P")+"     G  "+score.optInt("G"),MARGIN,105,p);
        canvas.drawText("Decyzja: "+score.optString("decision")+"     Najsłabszy obszar: "+score.optString("weak"),MARGIN,134,paint(13,true,Color.DKGRAY));
        drawWrapped(canvas,"Obserwacja trenera: "+score.optString("observation","—"),MARGIN,165,WIDTH-2*MARGIN,12,paint(10,false,Color.DKGRAY));
        int y=222;JSONObject ratings=score.optJSONObject("blocks");
        canvas.drawText("OCENA ŚRODKÓW",MARGIN,y,paint(13,true,0xFF071B3A));y+=24;
        for(AnalysisEngine.TrainingBlock b:plan.blocks){canvas.drawText(b.id+" • "+b.name,MARGIN,y,paint(10,true,Color.DKGRAY));canvas.drawText(String.valueOf(ratings==null?"—":ratings.optInt(b.id)),760,y,paint(12,true,0xFF126BEB));y+=27;}
        footer(canvas,"Training Score • dokument oddzielny od konspektu i KPI");pdf.finishPage(page);
        return write(pdf,"FEA_Training_Score_"+safe(day)+".pdf");
    }

    public File kpi(String day, AnalysisEngine.TrainingPlan plan, JSONObject score) throws Exception {
        PdfDocument pdf=new PdfDocument();PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,1).create());
        Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);header(canvas,"KPI • "+day,plan.problem,1,1);
        int y=94;canvas.drawText("GŁÓWNY WSKAŹNIK SUKCESU",MARGIN,y,paint(14,true,0xFF071B3A));
        y=drawWrapped(canvas,kpiText(score.optString("weak")),MARGIN,y+24,WIDTH-2*MARGIN,5,paint(12,false,Color.DKGRAY))+20;
        canvas.drawText("KRYTERIA DLA ŚRODKÓW",MARGIN,y,paint(14,true,0xFF071B3A));y+=26;
        for(AnalysisEngine.TrainingBlock b:plan.blocks){
            canvas.drawText(b.id+" • "+b.name,MARGIN,y,paint(10,true,Color.DKGRAY));y+=16;
            String criterion=b.role.equals("Motoryka")?"Jakość ruchu nie spada; pełne zaangażowanie bez zbędnych przerw.":
                    b.role.equals("Gra / transfer")?"Zachowanie pojawia się spontanicznie, bez zatrzymywania gry i komendy trenera.":kpiText(score.optString("weak"));
            y=drawWrapped(canvas,criterion,MARGIN+14,y,WIDTH-2*MARGIN-14,2,paint(9,false,Color.DKGRAY))+12;
        }
        footer(canvas,"KPI • osobny dokument kontrolny");pdf.finishPage(page);return write(pdf,"FEA_KPI_"+safe(day)+".pdf");
    }

    private void drawBlock(Canvas c,AnalysisEngine.TrainingBlock b,int y){
        Paint green=paint(12,true,0xFF126BEB);c.drawText(b.role+" • "+b.id+" • "+b.minutes+" min",MARGIN,y,green);
        c.drawText(b.name,190,y,paint(12,true,Color.BLACK));
        try(InputStream in=context.getAssets().open("images/"+b.id+".png")){Bitmap bmp=BitmapFactory.decodeStream(in);c.drawBitmap(bmp,null,new Rect(MARGIN,y+10,174,y+145),null);}catch(Exception ignored){}
        int x=190,ty=y+18,w=WIDTH-x-MARGIN;
        ty=drawWrapped(c,"Pole: "+b.field,x,ty,w,2,paint(8,false,Color.DKGRAY));
        ty=drawWrapped(c,"Cel: "+b.goal,x,ty+2,w,3,paint(8,false,Color.DKGRAY));
        ty=drawWrapped(c,"Organizacja: "+b.organization,x,ty+2,w,4,paint(8,false,Color.DKGRAY));
        ty=drawWrapped(c,"Przebieg: "+b.course,x,ty+2,w,4,paint(8,false,Color.DKGRAY));
        drawWrapped(c,"Coaching: "+b.questions,x,ty+2,w,3,paint(8,false,Color.DKGRAY));
        c.drawLine(MARGIN,y+155,WIDTH-MARGIN,y+155,paint(1,false,0xFFE1E6E2));
    }

    private void header(Canvas c,String title,String subtitle,int page,int total){c.drawRect(0,0,WIDTH,56,paint(1,false,0xFF071B3A));c.drawText(title,MARGIN,25,paint(16,true,Color.WHITE));c.drawText(shorten(subtitle,90),MARGIN,44,paint(9,false,0xFFD8E8FF));c.drawText(page+"/"+total,790,32,paint(10,true,Color.WHITE));}
    private void footer(Canvas c,String text){c.drawText(shorten(text,120),MARGIN,HEIGHT-14,paint(8,false,Color.GRAY));}
    private int drawWrapped(Canvas c,String text,int x,int y,int width,int maxLines,Paint paint){List<String> lines=wrap(text,paint,width);int h=Math.round(paint.getTextSize()*1.25f);for(int i=0;i<Math.min(maxLines,lines.size());i++){String line=lines.get(i);if(i==maxLines-1&&lines.size()>maxLines)line=shorten(line,Math.max(4,line.length()-3))+"…";c.drawText(line,x,y+i*h,paint);}return y+Math.min(maxLines,lines.size())*h;}
    private static List<String> wrap(String text,Paint paint,int width){List<String> out=new ArrayList<>();StringBuilder line=new StringBuilder();for(String word:String.valueOf(text).replace('\n',' ').split("\\s+")){String test=line.length()==0?word:line+" "+word;if(paint.measureText(test)>width&&line.length()>0){out.add(line.toString());line=new StringBuilder(word);}else line=new StringBuilder(test);}if(line.length()>0)out.add(line.toString());return out;}
    private static Paint paint(float size,boolean bold,int color){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(size);p.setColor(color);p.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);return p;}
    private File write(PdfDocument pdf,String name)throws Exception{File dir=new File(context.getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Nie można utworzyć katalogu PDF");File file=new File(dir,name);try(FileOutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}finally{pdf.close();}return file;}
    private static String kpiText(String weak){if("R".equals(weak))return "Zawodnicy wcześniej rozpoznają informację, skanują otoczenie i potrafią wyjaśnić swoją decyzję.";if("W".equals(weak))return "Zachowanie jest wykonywane jakościowo i powtarzalnie w tempie zadania.";if("P".equals(weak))return "Zachowanie utrzymuje się mimo aktywnej presji przeciwnika i ograniczonego czasu.";return "Zachowanie pojawia się spontanicznie w grze bez podpowiedzi trenera.";}
    private static String safe(String s){return s.replaceAll("[^a-zA-Z0-9ąćęłńóśźżĄĆĘŁŃÓŚŹŻ_-]","_");}
    private static String shorten(String s,int n){return s==null?"":s.length()<=n?s:s.substring(0,n-1)+"…";}
}
