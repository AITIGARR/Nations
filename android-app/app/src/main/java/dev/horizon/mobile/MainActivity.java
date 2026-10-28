package dev.horizon.mobile;

import android.app.Activity;
import android.app.NativeActivity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int ARCHIVE=1, FOLDER=2;
    private final ExecutorService workers=Executors.newSingleThreadExecutor();
    private TextView message;
    private Button play;
    private ProgressBar progress;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff101729);
        getWindow().setNavigationBarColor(0xff101729);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff0c1120);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p=dp(20);
        box.setPadding(p,dp(34),p,p);
        scroll.addView(box);
        TextView title=text("HORIZON RUST",29,Color.WHITE);
        box.addView(title);
        box.addView(text("Minecraft на нативном Rust-движке",14,0xffc0cade));
        message=text("Проверяем ресурсы...",15,Color.WHITE);
        message.setPadding(0,dp(24),0,dp(18));
        box.addView(message);
        progress=new ProgressBar(this);
        progress.setVisibility(View.GONE);
        box.addView(progress);
        Button archive=button("Выбрать ZIP/JAR через проводник");
        archive.setOnClickListener(v->pickArchive());
        box.addView(archive);
        Button folder=button("Выбрать папку через проводник");
        folder.setOnClickListener(v->pickFolder());
        box.addView(folder);
        play=button("▶ ИГРАТЬ");
        play.setOnClickListener(v->startGame());
        box.addView(play);
        box.addView(text("Zalith нужен только как возможный источник файлов. Приложению требуются подготовленные ресурсы Horizon 26.3, а не просто Java JAR.",12,0xffadb9cc));
        setContentView(scroll);
        refresh(null);
    }
    private void pickArchive(){
        Intent i=new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/java-archive","application/octet-stream"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i,"Выбери файловый менеджер"),ARCHIVE);
    }
    private void pickFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i,"Выбери проводник и папку"),FOLDER);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri selected=data.getData();
        message.setText("Импортируем файлы, не закрывай приложение...");
        progress.setVisibility(View.VISIBLE);
        play.setEnabled(false);
        workers.execute(()->{
            String resultText;
            try{
                ImportWorker worker=new ImportWorker(this);
                resultText=request==FOLDER?worker.importFolder(selected):worker.importArchive(selected);
            }catch(Exception e){resultText="Ошибка: "+e.getMessage();}
            final String text=resultText;
            runOnUiThread(()->{
                progress.setVisibility(View.GONE);
                refresh(text);
            });
        });
    }
    private void startGame(){
        if(!engineReady()||!ImportWorker.ready(this)){refresh("Нет необходимых игровых файлов.");return;}
        startActivity(new Intent(this,NativeActivity.class));
    }
    private void refresh(String info){
        boolean assets=ImportWorker.ready(this), engine=engineReady();
        play.setEnabled(assets&&engine&&progress.getVisibility()!=View.VISIBLE);
        message.setText("Rust-движок: "+(engine?"подключён":"нет")+"\nРесурсы: "+(assets?"готовы":"не подготовлены")+(info==null?"":"\n\n"+info));
    }
    private boolean engineReady(){
        return new File(getApplicationInfo().nativeLibraryDir,"libhorizon_client.so").isFile();
    }
    private Button button(String title){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setTextSize(16);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffd46236));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(60));
        lp.bottomMargin=dp(14);
        b.setLayoutParams(lp);
        return b;
    }
    private TextView text(String value,int size,int color){
        TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);return v;
    }
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+0.5f);}
    @Override protected void onDestroy(){workers.shutdown();super.onDestroy();}
}
