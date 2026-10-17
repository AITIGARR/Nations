package com.horizon.jarvis;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.InputType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local-only contact lookup. Requires explicit on-screen confirmation before any call. */
public final class CallContactActivity extends Activity {
    static final String EXTRA_TARGET = "contact_query";
    private static final int REQUEST_CONTACTS = 701;
    private static final int REQUEST_CALL = 702;

    private String requested;
    private Contact chosen;
    private TextView status;
    private EditText input;

    private static final class Contact {
        final String name;
        final String number;
        final int score;
        Contact(String name, String number, int score) {
            this.name=name;
            this.number=number;
            this.score=score;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF061C2D);
        getWindow().setNavigationBarColor(0xFF061C2D);
        requested=getIntent().getStringExtra(EXTRA_TARGET);
        if(requested==null) requested="";
        if(requested.length()>70) requested=requested.substring(0,70);

        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(23),dp(24),dp(23),dp(20));
        layout.setBackgroundColor(0xFF061624);
        TextView title=new TextView(this);
        title.setText("☎   JARVIS  /  ЗВОНКИ");
        title.setTextSize(21);
        title.setTextColor(0xFF00DFFF);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);
        status=new TextView(this);
        status.setText("Контакты читаются только на устройстве. Перед звонком JARVIS попросит подтвердить имя и номер.");
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setPadding(0,dp(15),0,dp(16));
        layout.addView(status);

        input=new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF91B3C7);
        input.setHint("Имя контакта");
        input.setText(requested);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        layout.addView(input);
        Button find=new Button(this);
        find.setText("НАЙТИ КОНТАКТ И ПОЗВОНИТЬ");
        find.setAllCaps(false);
        find.setTextColor(Color.WHITE);
        find.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0B5672));
        layout.addView(find);
        find.setOnClickListener(v -> lookup(input.getText().toString().trim()));
        setContentView(layout);

        if(!requested.trim().isEmpty()) lookup(requested.trim());
    }

    private int dp(int dip) {
        return Math.round(getResources().getDisplayMetrics().density*dip);
    }

    private void lookup(String target) {
        if(target==null || target.trim().isEmpty()) {
            status.setText("Введите имя контакта, сэр.");
            return;
        }
        requested=target.trim();
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) {
            status.setText("Нужен доступ к контактам, чтобы найти номер по имени.");
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},REQUEST_CONTACTS);
            return;
        }
        final List<Contact> matches = findContacts(requested);
        if(matches.isEmpty()) {
            status.setText("Не нашёл контакт «"+requested+"». Проверьте имя и разрешение на контакты.");
            return;
        }
        if(matches.size()==1) {confirm(matches.get(0));return;}
        String[] options=new String[matches.size()];
        for(int i=0;i<matches.size();i++) {
            Contact c=matches.get(i);
            options[i]=(i+1)+".  "+c.name+"   •   "+c.number;
        }
        status.setText("Найдено "+matches.size()+" номеров. Выберите нужный.");
        new AlertDialog.Builder(this)
            .setTitle("Кому позвонить?")
            .setItems(options,(d,which)->confirm(matches.get(which)))
            .setNegativeButton("Отмена",(d,which)->{})
            .show();
    }

    private List<Contact> findContacts(String text) {
        List<Contact> found=new ArrayList<>();
        String target=ContactCalling.normalizedName(text);
        Set<String> unique=new HashSet<>();
        String[] projection={
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        // Data is queried only while this activity is visible, and never uploaded or persisted.
        try(Cursor cursor=getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,null,null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")) {
            if(cursor==null) return found;
            int checked=0;
            while(cursor.moveToNext() && checked++<6000) {
                String name=cursor.getString(0);
                String phone=cursor.getString(1);
                if(name==null || phone==null)continue;
                String digits=phone.replaceAll("[^+0-9]","");
                if(digits.replace("+","").length()<3 || digits.replace("+","").length()>20
                    || (digits.contains("+") && !digits.startsWith("+"))) continue;
                String norm=ContactCalling.normalizedName(name);
                int score=matchScore(target,norm);
                if(score>4 || !unique.add(norm+"|"+digits)) continue;
                found.add(new Contact(name,phone,score));
            }
        } catch(SecurityException exception) {
            status.setText("Android запретил чтение контактов. Проверьте разрешения JARVIS.");
            return new ArrayList<>();
        }
        found.sort(Comparator.comparingInt((Contact c)->c.score)
            .thenComparing(c->c.name));
        if(found.size()>12) return new ArrayList<>(found.subList(0,12));
        return found;
    }

    static int matchScore(String query,String name) {
        if(query==null || name==null || query.isEmpty())return 10;
        if(name.equals(query))return 0;
        if(name.startsWith(query+" ") || name.startsWith(query))return 1;
        if(name.contains(" "+query) || name.contains(query))return 2;
        String stem=query.length()>=5?query.substring(0,query.length()-1):query;
        if(stem.length()>=3 && name.startsWith(stem))return 3;
        if(query.length()>=4 && name.startsWith(query.substring(0,3)))return 4;
        return 10;
    }

    private void confirm(Contact c) {
        chosen=c;
        status.setText("Номер найден: "+c.name+". Ожидаю подтверждения.");
        new AlertDialog.Builder(this)
            .setTitle("Подтвердить звонок, сэр?")
            .setMessage(c.name+"\n"+c.number+"\n\nJARVIS позвонит только после вашего подтверждения.")
            .setPositiveButton("Позвонить",(d,which)->placeCall())
            .setNeutralButton("Открыть набор",(d,which)->openDialer())
            .setNegativeButton("Отмена",(d,which)->status.setText("Звонок отменён."))
            .show();
    }

    private Uri selectedNumber() {
        if(chosen==null)return null;
        String raw=chosen.number.replaceAll("[^+0-9]","");
        if(raw.replace("+","").length()<3 || raw.replace("+","").length()>20) return null;
        if(raw.indexOf('+')>0 || raw.indexOf('+',1)>=0) return null;
        return Uri.fromParts("tel",raw,null);
    }

    private void placeCall() {
        if(chosen==null || selectedNumber()==null)return;
        if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED) {
            status.setText("Подтвердите разрешение Android на телефонные звонки.");
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE},REQUEST_CALL);
            return;
        }
        try {
            Intent call=new Intent(Intent.ACTION_CALL,selectedNumber());
            startActivity(call);
            status.setText("Передал звонок в телефонное приложение.");
        } catch(SecurityException | ActivityNotFoundException ex) {
            status.setText("Прямой вызов недоступен. Открываю стандартный набор номера.");
            openDialer();
        }
    }

    private void openDialer() {
        Uri number=selectedNumber();
        if(number==null)return;
        try {
            startActivity(new Intent(Intent.ACTION_DIAL,number));
            status.setText("Номер открыт в звонилке. Нажмите вызов.");
        } catch(ActivityNotFoundException e) {
            status.setText("Не найдено приложение для телефонных звонков.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        boolean granted=grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED;
        if(requestCode==REQUEST_CONTACTS) {
            if(granted)lookup(requested);
            else status.setText("Без разрешения на контакты поиск по имени недоступен.");
        } else if(requestCode==REQUEST_CALL) {
            if(granted)placeCall();
            else {
                status.setText("Разрешение на прямой звонок не выдано. Открываю набор номера.");
                openDialer();
            }
        }
    }
}
