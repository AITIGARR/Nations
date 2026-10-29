package dev.horizon.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NativeActivity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Version-aware Android launcher; the Rust game itself targets Java 26.3. */
public final class MainActivity extends Activity {
    private static final int ARCHIVE=1;
    private static final int FOLDER=2;
    private final ExecutorService workers=Executors.newSingleThreadExecutor();
    private TextView message;
    private Button play;
    private Button versionPicker;
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
        box.addView(text("HORIZON RUST",29,Color.WHITE));
        box.addView(text("Библиотека версий Minecraft Java Edition",15,0xffc0cade));
        message=text("",15,Color.WHITE);
        message.setPadding(0,dp(24),0,dp(18));
        box.addView(message);

        versionPicker=button("Выбрать версию");
        versionPicker.setOnClickListener(v->chooseVersion());
        box.addView(versionPicker);

        progress=new ProgressBar(this);
        progress.setVisibility(View.GONE);
        box.addView(progress);

        Button archive=button("Импортировать ZIP/JAR через проводник");
        archive.setOnClickListener(v->pickArchive());
        box.addView(archive);
        Button folder=button("Импортировать папку через проводник");
        folder.setOnClickListener(v->pickFolder());
        box.addView(folder);

        play=button("▶ ИГРАТЬ НА RUST");
        play.setOnClickListener(v->startGame());
        box.addView(play);

        TextView warning=text("Важно: файлы любых версий можно хранить и выбирать. Нативный игровой движок пока реализует только Minecraft 26.3. Для остальных версий одного импорта недостаточно.",13,0xffbec7d9);
        warning.setPadding(0,dp(10),0,0);
        box.addView(warning);
        setContentView(scroll);
        refresh(null);
    }

    private void chooseVersion(){
        List<String> versions=VersionLibrary.versions(this);
        String[] labels=new String[versions.size()];
        for(int i=0;i<versions.size();i++){
            String ver=versions.get(i);
            labels[i]=ver + (VersionLibrary.ENGINE_VERSION.equals(ver)?"  · движок Rust":"  · файлы / импорт") +
                (VersionLibrary.imported(this,ver)?"  ✓":"");
        }
        new AlertDialog.Builder(this)
            .setTitle("Выбери версию Minecraft")
            .setItems(labels,(dialog,which)->{
                VersionLibrary.select(this,versions.get(which));
                refresh(null);
            })
            .setNeutralButton("Своя версия",(dialog,which)->customVersion())
            .setNegativeButton("Закрыть",null)
            .show();
    }
    private void customVersion(){
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setText("1.21.11");
        field.setSelectAllOnFocus(true);
        LinearLayout wrap=new LinearLayout(this);
        wrap.setPadding(dp(18),0,dp(18),0);
        wrap.addView(field,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Укажи версию Minecraft Java")
            .setMessage("Например: 1.7.10, 1.12.2, 1.20.1, 1.21.11 или snapshot.")
            .setView(wrap)
            .setPositiveButton("Выбрать",null)
            .setNegativeButton("Отмена",null)
            .create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String candidate=field.getText().toString().trim();
            if(!VersionLibrary.valid(candidate)){
                field.setError("Используй латинские буквы, цифры, точки, дефисы");
                return;
            }
            VersionLibrary.select(this,candidate);
            dialog.dismiss();
            refresh(null);
        }));
        dialog.show();
    }
    private void pickArchive(){
        Intent i=new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
            "application/zip","application/java-archive","application/octet-stream"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i,"Выбери проводник"),ARCHIVE);
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
        final String version=VersionLibrary.selected(this);
        message.setText("Импорт файлов версии "+version+"...");
        progress.setVisibility(View.VISIBLE);
        versionPicker.setEnabled(false);
        play.setEnabled(false);
        workers.execute(()->{
            String resultText;
            try{
                ImportWorker worker=new ImportWorker(this,version);
                resultText=request==FOLDER?worker.importFolder(selected):worker.importArchive(selected);
            }catch(Exception e){resultText="Ошибка импорта "+version+": "+e.getMessage();}
            final String status=resultText;
            runOnUiThread(()->{
                progress.setVisibility(View.GONE);
                versionPicker.setEnabled(true);
                refresh(status);
            });
        });
    }
    private void startGame(){
        String ver=VersionLibrary.selected(this);
        if(!VersionLibrary.ENGINE_VERSION.equals(ver)){
            refresh("Нельзя запустить "+ver+" на движке 26.3: это приведёт к несовместимости форматов.");
            return;
        }
        if(!engineReady()||!VersionLibrary.canLaunch(this,ver)){
            refresh("Не хватает нативной библиотеки или подготовленных ресурсов 26.3.");
            return;
        }
        try{
            startActivity(new Intent(this,NativeActivity.class));
        }catch(Exception e){
            refresh("Ошибка запуска Horizon: "+e.getMessage());
        }
    }
    private void refresh(String info){
        String ver=VersionLibrary.selected(this);
        versionPicker.setText("Версия Minecraft: "+ver+"  ▾");
        boolean canRun=engineReady() && VersionLibrary.canLaunch(this,ver);
        play.setEnabled(canRun && progress.getVisibility()!=View.VISIBLE);
        String state="Движок Horizon ARM64: "+(engineReady()?"установлен":"не найден")+
            "\nВыбрана версия: "+ver+
            "\n"+VersionLibrary.description(this,ver);
        if(info!=null && !info.isEmpty())state+="\n\n"+info;
        message.setText(state);
    }
    private boolean engineReady(){
        return new File(getApplicationInfo().nativeLibraryDir,"libhorizon_client.so").isFile();
    }
    private Button button(String title){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setTextSize(15);
        b.setBackgroundTintList(ColorStateList.valueOf(0xffd46236));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));
        lp.bottomMargin=dp(14);
        b.setLayoutParams(lp);
        return b;
    }
    private TextView text(String value,int size,int color){
        TextView v=new TextView(this);
        v.setText(value);v.setTextColor(color);v.setTextSize(size);
        return v;
    }
    private int dp(int value){
        return (int)(getResources().getDisplayMetrics().density*value+0.5f);
    }
    @Override protected void onDestroy(){
        workers.shutdown();
        super.onDestroy();
    }
}
