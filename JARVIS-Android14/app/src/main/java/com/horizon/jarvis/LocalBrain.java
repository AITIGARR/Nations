package com.horizon.jarvis;
import android.content.Context;
import java.io.*;
final class LocalBrain {
    private static final String NAME="SmolLM2-135M-Instruct-Q4_K_M.gguf";
    private static boolean tried=false, loaded=false;
    static File file(Context ctx){return new File(ctx.getFilesDir(),NAME);}
    static boolean ready(Context ctx){return file(ctx).length()>95000000L;}
    static synchronized void install(Context ctx) throws Exception {
        if(ready(ctx))return;
        File partial=new File(ctx.getFilesDir(),NAME+".partial");
        try(InputStream in=ctx.getAssets().open(NAME);OutputStream out=new FileOutputStream(partial)){
            byte[] b=new byte[65536]; int count;
            while((count=in.read(b))!=-1)out.write(b,0,count);
        }catch(Exception e){partial.delete();throw e;}
        if(partial.length()<95000000L){partial.delete();throw new IOException("Incomplete GGUF");}
        if(file(ctx).exists()&&!file(ctx).delete())throw new IOException("Cannot replace GGUF");
        if(!partial.renameTo(file(ctx)))throw new IOException("Cannot move GGUF");
    }
    private static synchronized void load(){
        if(tried)return;
        tried=true;
        try{System.loadLibrary("jarvis_local");loaded=true;}
        catch(UnsatisfiedLinkError e){loaded=false;}
    }
    static String answer(Context ctx,String prompt){
        if(!ready(ctx))return "Локальная модель не установлена. Откройте JARVIS и нажмите «Подготовить локальный ИИ».";
        load();
        if(!loaded)return "Локальный движок недоступен на этом устройстве.";
        try{
            String query=prompt.length()>700?prompt.substring(0,700):prompt;
            String answer=generate(file(ctx).getAbsolutePath(),query);
            return answer==null||answer.trim().isEmpty()?"Локальная модель не смогла ответить.":answer.trim();
        }catch(Exception e){return "Ошибка локального ИИ: "+e.getClass().getSimpleName();}
    }
    private static native String generate(String path,String prompt);
}
