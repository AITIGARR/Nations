package dev.horizon.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Manages a library of Minecraft Java versions.
 * An imported version is NOT the same as a Rust engine implementation.
 */
final class VersionLibrary {
    static final String ENGINE_VERSION = "26.3";
    private static final String PREFS="horizon_versions";
    private static final String KEY_SELECTED="selected";
    private static final String KEY_LIST="registered";
    private static final Pattern SAFE=Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,59}");

    private VersionLibrary(){}

    static boolean valid(String value){
        return value!=null && SAFE.matcher(value).matches() && !value.contains("..");
    }

    static String selected(Context context){
        String value=context.getSharedPreferences(PREFS,0).getString(KEY_SELECTED,ENGINE_VERSION);
        return valid(value)?value:ENGINE_VERSION;
    }

    static void select(Context context,String value){
        if(!valid(value))throw new IllegalArgumentException("Недопустимое имя версии");
        add(context,value);
        context.getSharedPreferences(PREFS,0).edit().putString(KEY_SELECTED,value).apply();
    }

    static void add(Context context,String value){
        if(!valid(value))throw new IllegalArgumentException("Недопустимое имя версии");
        SharedPreferences p=context.getSharedPreferences(PREFS,0);
        Set<String> saved=new LinkedHashSet<>(p.getStringSet(KEY_LIST,Collections.emptySet()));
        saved.add(value);
        p.edit().putStringSet(KEY_LIST,saved).apply();
    }

    static List<String> versions(Context context){
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        unique.add(ENGINE_VERSION);
        unique.add("1.21.11");
        unique.add("1.20.1");
        unique.add("1.16.5");
        unique.add("1.12.2");
        unique.add("1.8.9");
        unique.addAll(context.getSharedPreferences(PREFS,0).getStringSet(KEY_LIST,Collections.emptySet()));
        File dir=new File(context.getFilesDir(),"horizon/versions");
        File[] entries=dir.listFiles(File::isDirectory);
        if(entries!=null)for(File f:entries)if(valid(f.getName()))unique.add(f.getName());
        String current=selected(context);
        unique.add(current);
        List<String> values=new ArrayList<>(unique);
        values.removeIf(v->!valid(v));
        return values;
    }

    static boolean imported(Context context,String version){
        if(!valid(version))return false;
        File location=new File(context.getFilesDir(),"horizon/versions/"+version);
        if(location.isDirectory()){
            File[] files=location.listFiles();
            if(files!=null && files.length>0)return true;
        }
        return ENGINE_VERSION.equals(version) &&
            new File(context.getFilesDir(),"horizon/resources/horizon-resources.json").isFile();
    }

    static boolean canLaunch(Context context,String version){
        return ENGINE_VERSION.equals(version) &&
            new File(context.getFilesDir(),"horizon/resources/horizon-resources.json").isFile();
    }

    static String description(Context context,String version){
        if(canLaunch(context,version)){
            return "Подготовленные данные совместимы с Rust-движком "+ENGINE_VERSION+".";
        }
        if(ENGINE_VERSION.equals(version)){
            return "Для запуска импортируй подготовленные данные Horizon 26.3.";
        }
        if(imported(context,version)){
            return "Файлы "+version+" импортированы, но движок Horizon пока не поддерживает запуск этой версии.";
        }
        return "Версия "+version+" не импортирована. Её можно добавить через проводник, но запуск на Rust ещё не поддерживается.";
    }
}
