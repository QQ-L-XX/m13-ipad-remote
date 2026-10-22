package local.m13.ipadremote;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
 Handler handler=new Handler();TextView status;String password;
 public void onCreate(Bundle b){super.onCreate(b);final SharedPreferences p=getSharedPreferences("remote",0);password=p.getString("password",null);
  if(password==null){String initial=getIntent().getStringExtra("initial_password");password=initial!=null&&initial.matches("[A-Za-z0-9-]{8,40}")?initial:"M13-"+UUID.randomUUID().toString().replace("-","").substring(0,8);p.edit().putString("password",password).apply();}
  getIntent().removeExtra("initial_password");
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(60,35,60,20);box.setBackgroundColor(0xff17283c);setContentView(box);
  label(box,"iPad 桌面连接 · 试用版 0.2",30);label(box,"iPad 连接同一个 Wi-Fi，用 Safari 打开：",20);label(box,address(),30);label(box,"连接密码："+password,26);
  label(box,"首次连接若弹出调试授权：勾选“始终允许”，再选择允许。\n只显示桌面与回传操作，暂不提供声音和视频播放。",19);status=label(box,"正在检查服务…",19);
  Button start=new Button(this);start.setText("启动／重试连接");box.addView(start);start.setOnClickListener(new View.OnClickListener(){public void onClick(View v){p.edit().putBoolean("enabled",true).apply();startService(new Intent(MainActivity.this,BridgeService.class));}});
  Button stop=new Button(this);stop.setText("停止桌面服务");box.addView(stop);stop.setOnClickListener(new View.OnClickListener(){public void onClick(View v){p.edit().putBoolean("enabled",false).apply();stopService(new Intent(MainActivity.this,BridgeService.class));new Thread(new Runnable(){public void run(){try{BridgeService.local("POST","/shutdown",password);}catch(Exception ignored){}BridgeService.state="已停止";}}).start();}});
  p.edit().putBoolean("enabled",true).apply();startService(new Intent(this,BridgeService.class));handler.post(tick);
 }
 Runnable tick=new Runnable(){public void run(){status.setText(BridgeService.state);handler.postDelayed(this,1500);}};
 public void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}
 TextView label(LinearLayout p,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(0xffedf3ff);t.setTextSize(size);t.setPadding(0,5,0,10);p.addView(t);return t;}
 static String address(){try{Enumeration<NetworkInterface> ns=NetworkInterface.getNetworkInterfaces();while(ns.hasMoreElements()){Enumeration<InetAddress> as=ns.nextElement().getInetAddresses();while(as.hasMoreElements()){InetAddress a=as.nextElement();if(a instanceof Inet4Address&&a.isSiteLocalAddress())return "http://"+a.getHostAddress()+":8090/";}}}catch(Exception ignored){}return "等待 Wi-Fi 连接";}
}
