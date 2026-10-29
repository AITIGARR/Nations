package dev.horizon.mobile;

import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import java.util.zip.*;

final class ImportWorker {
    private final Context context;
    private final File base;
    private final String version;
    private long copied;
    private int count;
    private static final long LIMIT=3L*1024*1024*1024;
    private static final int FILE_LIMIT=120000;
    ImportWorker(Context context,String version){
        if(!VersionLibrary.valid(version))throw new IllegalArgumentException("Недопустимая версия");
        this.version=version;
        this.context=context.getApplicationContext();
        base=new File(context.getFilesDir(),"horizon");
    }
    static boolean ready(Context c){return VersionLibrary.canLaunch(c,VersionLibrary.selected(c));}

    String importArchive(Uri uri)throws Exception{
        File tmp=staging();
        boolean success=false;
        try {
            String name=getName(uri).toLowerCase(Locale.ROOT);
            if(name.endsWith(".jar")){
                try(InputStream in=open(uri);OutputStream out=new FileOutputStream(safe(tmp,"minecraft.jar"))){copy(in,out);}
            }else{
                try(InputStream in=open(uri); ZipInputStream zip=new ZipInputStream(new BufferedInputStream(in))){
                    ZipEntry entry;
                    while((entry=zip.getNextEntry())!=null){
                        if(++count>FILE_LIMIT)throw new IOException("Слишком много файлов в архиве");
                        String nameInZip=entry.getName().replace('\\','/');
                        if(nameInZip.startsWith("/")||nameInZip.contains(":")||nameInZip.indexOf('\0')>=0)
                            throw new IOException("Запрещённый путь в архиве");
                        File to=safe(tmp,nameInZip);
                        if(entry.isDirectory()){if(!to.isDirectory()&&!to.mkdirs())throw new IOException("Ошибка каталога");}
                        else{
                            File parent=to.getParentFile();
                            if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Ошибка каталога");
                            try(OutputStream out=new FileOutputStream(to)){copy(zip,out);}
                        }
                        zip.closeEntry();
                    }
                }
            }
            String result=finish(tmp);success=true;return result;
        }finally{if(!success)delete(tmp);}
    }
    String importFolder(Uri uri)throws Exception{
        File tmp=staging();boolean success=false;
        try{
            String rootId=DocumentsContract.getTreeDocumentId(uri);
            copyFolder(uri,rootId,tmp,0);
            String result=finish(tmp);success=true;return result;
        }finally{if(!success)delete(tmp);}
    }
    private void copyFolder(Uri tree,String id,File out,int depth)throws Exception{
        if(depth>35)throw new IOException("Слишком глубокая папка");
        ContentResolver r=context.getContentResolver();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        try(Cursor c=r.query(children,new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(c==null)throw new IOException("Проводник не открыл папку");
            while(c.moveToNext()){
                if(++count>FILE_LIMIT)throw new IOException("Слишком много файлов");
                String childId=c.getString(0), name=c.getString(1),mime=c.getString(2);
                if(name==null||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\"))
                    throw new IOException("Недопустимое имя файла");
                File target=safe(out,name);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){
                    if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Нет прав на каталог");
                    copyFolder(tree,childId,target,depth+1);
                }else{
                    Uri file=DocumentsContract.buildDocumentUriUsingTree(tree,childId);
                    try(InputStream in=open(file);OutputStream stream=new FileOutputStream(target)){copy(in,stream);}
                }
            }
        }
    }
    private String finish(File tmp)throws Exception{
        File marker=findMarker(tmp,4);
        File perVersion=new File(base,"versions/"+version);
        if(!perVersion.isDirectory()&&!perVersion.mkdirs())
            throw new IOException("Нельзя создать папку версии");
        if(marker!=null){
            File src=marker.getParentFile();
            if(VersionLibrary.ENGINE_VERSION.equals(version)){
                String json;
                try(InputStream in=new FileInputStream(marker)){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    copy(in,out);
                    json=out.toString("UTF-8");
                }
                int dataVersion=new org.json.JSONObject(json).optInt("data_version",-1);
                if(dataVersion!=5023)throw new IOException("Ресурсы относятся к другой версии: Horizon 26.3 требует DataVersion 5023");
                File current=new File(base,"resources");
                File backup=new File(base,"resources-backup");
                delete(backup);
                if(current.exists()&&!current.renameTo(backup))
                    throw new IOException("Не удалось сохранить предыдущую установку");
                if(!src.renameTo(current)){
                    if(backup.exists())backup.renameTo(current);
                    throw new IOException("Ошибка установки ресурсов");
                }
                delete(backup);
                delete(tmp);
                VersionLibrary.add(context,version);
                return "Horizon 26.3: подготовленные ресурсы установлены; можно пробовать запуск.";
            }
            File current=new File(perVersion,"resources");
            File backup=new File(perVersion,"resources-backup");
            delete(backup);
            if(current.exists()&&!current.renameTo(backup))
                throw new IOException("Не удалось сохранить предыдущие файлы");
            if(!src.renameTo(current)){
                if(backup.exists())backup.renameTo(current);
                throw new IOException("Ошибка импорта");
            }
            delete(backup);delete(tmp);
            VersionLibrary.add(context,version);
            return "Ресурсы "+version+" сохранены. Rust-движок пока рассчитан только на 26.3.";
        }
        File imports=new File(perVersion,"imports");
        if(!imports.isDirectory()&&!imports.mkdirs())throw new IOException("Нельзя сохранить файлы");
        File target=new File(imports,tmp.getName());
        if(!tmp.renameTo(target))throw new IOException("Не удалось перенести импорт");
        VersionLibrary.add(context,version);
        return "Файлы "+version+" импортированы. Для запуска в Horizon требуются ресурсы и движок этой версии.";
    }
    private File findMarker(File root,int depth){
        if(new File(root,"horizon-resources.json").isFile())return new File(root,"horizon-resources.json");
        if(depth==0)return null;
        File[] children=root.listFiles(File::isDirectory);
        if(children!=null)for(File f:children){File found=findMarker(f,depth-1);if(found!=null)return found;}
        return null;
    }
    private File staging()throws Exception{
        if(!base.isDirectory()&&!base.mkdirs())throw new IOException("Нет каталога приложения");
        File f=new File(base,"import-"+UUID.randomUUID());
        if(!f.mkdirs())throw new IOException("Нельзя создать временный каталог");
        return f;
    }
    private File safe(File root,String name)throws Exception{
        File f=new File(root,name).getCanonicalFile();
        if(!f.getPath().startsWith(root.getCanonicalPath()+File.separator))
            throw new IOException("Путь выходит за пределы каталога");
        return f;
    }
    private InputStream open(Uri uri)throws Exception{
        InputStream s=context.getContentResolver().openInputStream(uri);
        if(s==null)throw new IOException("Проводник не предоставил файл");
        return s;
    }
    private String getName(Uri uri){
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return "minecraft.zip";
    }
    private void copy(InputStream in,OutputStream out)throws IOException{
        byte[] bytes=new byte[65536];int n;
        while((n=in.read(bytes))!=-1){
            copied+=n;if(copied>LIMIT)throw new IOException("Файлы превышают 3 ГБ");
            out.write(bytes,0,n);
        }
    }
    private void delete(File f){
        if(!f.exists())return;
        if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);}
        f.delete();
    }
}
