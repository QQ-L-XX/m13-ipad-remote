package local.m13.ipadremote;
import android.app.*;
import android.content.*;
import android.os.*;
import java.net.*;
import java.io.*;

public class BridgeService extends Service {
 public static volatile String state="尚未启动";
 volatile boolean running;
 public void onCreate(){super.onCreate();running=true;Notification n=new Notification.Builder(this).setContentTitle("iPad 桌面连接").setContentText("本地桌面服务").setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),0)).build();startForeground(1301,n);new Thread(new Runnable(){public void run(){watch();}},"m13-local-bridge").start();}
 public int onStartCommand(Intent i,int f,int id){return START_STICKY;}
 public IBinder onBind(Intent i){return null;}
 public void onDestroy(){running=false;super.onDestroy();}
 void watch(){while(running){LocalAdb adb=null;try{
  boolean ready=healthy();
  if(ready&&getSharedPreferences("remote",0).getBoolean("local_authorized",false)){state="已就绪，iPad 可直接连接";SystemClock.sleep(10000);continue;}
  String pass=getSharedPreferences("remote",0).getString("password",null);if(pass==null)throw new IOException("请先打开本应用完成设置");
  String apk=getApplicationInfo().sourceDir;if(!apk.matches("/[A-Za-z0-9_./-]+"))throw new IOException("Unexpected application path");
  state=ready?"桌面可用；正在确认重启后的本机启动授权":"正在启动；首次请在电视确认本机调试授权";
  adb=new LocalAdb(this);adb.connect();
  String proof=adb.shell("echo M13_IPAD_LOCAL_AUTHORIZED");if(!proof.contains("M13_IPAD_LOCAL_AUTHORIZED"))throw new IOException("Local authorization proof failed");getSharedPreferences("remote",0).edit().putBoolean("local_authorized",true).apply();
  if(!ready){String token=persistentToken(pass);adb.shell("(trap '' HUP; CLASSPATH='"+apk+"' /system/xbin/setsid /system/bin/app_process /system/bin local.m13.ipadremote.DesktopServer '"+pass+"' '"+token+"' > /data/local/tmp/m13-ipad-remote.log 2>&1 < /dev/null &); sleep 2");}
  SystemClock.sleep(2000);state=healthy()?"已就绪，iPad 可直接连接":"服务未就绪，请检查调试授权";
 }catch(Exception e){state=(healthy()?"桌面可用，自动启动授权仍待确认。":"等待连接：")+e.getClass().getSimpleName()+"。请先退出电脑调试连接，再确认电视上的授权";}finally{if(adb!=null)adb.close();}SystemClock.sleep(5000);}}
 String persistentToken(String password)throws Exception{
  SharedPreferences prefs=getSharedPreferences("remote",0);String secret=prefs.getString("persistent_login_secret",null);
  if(secret==null||!secret.matches("[0-9a-f]{64}")){secret=PersistentLogin.newSecret();if(!prefs.edit().putString("persistent_login_secret",secret).commit())throw new IOException("Cannot save remembered login");}
  return PersistentLogin.token(secret,password);
 }
 public static boolean healthy(){try{String s=local("GET","/health",null);return s.contains("\"uid\":2000")&&s.contains("\"service\":\"m13-ipad-remote\"");}catch(Exception e){return false;}}
 public static String local(String method,String path,String body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:8090"+path).openConnection();try{c.setConnectTimeout(1500);c.setReadTimeout(2000);c.setRequestMethod(method);if(body!=null){c.setDoOutput(true);byte[]b=body.getBytes("UTF-8");c.setFixedLengthStreamingMode(b.length);OutputStream o=c.getOutputStream();o.write(b);o.close();}InputStream in=c.getInputStream();try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[]buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1){if(b.size()>8192)throw new IOException("Local reply too large");b.write(buffer,0,n);}return b.toString("UTF-8");}finally{in.close();}}finally{c.disconnect();}}
}
