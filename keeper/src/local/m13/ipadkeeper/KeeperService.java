package local.m13.ipadkeeper;
import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.*;
import java.net.*;
import java.io.*;

/** Independent foreground process. No ADB key, password, root, or arbitrary command interface. */
public final class KeeperService extends Service {
 public static volatile String state="正在检查";
 volatile boolean running;
 static boolean enabled(Context c){return c.getSharedPreferences("keeper",0).getBoolean("enabled",false);}
 static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,1302,new Intent(c,WakeReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT);}
 static void arm(Context c){((AlarmManager)c.getSystemService(ALARM_SERVICE)).setInexactRepeating(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+60000,60000,alarm(c));}
 static void begin(Context c){if(enabled(c)){arm(c);c.startService(new Intent(c,KeeperService.class));}}
 static void setEnabled(Context c,boolean on){
  c.getSharedPreferences("keeper",0).edit().putBoolean("enabled",on).commit();
  if(on)begin(c);else{((AlarmManager)c.getSystemService(ALARM_SERVICE)).cancel(alarm(c));c.stopService(new Intent(c,KeeperService.class));state="守护已暂停";}
 }
 public void onCreate(){
  super.onCreate();running=true;
  Notification n=new Notification.Builder(this).setContentTitle("iPad 面板守护").setContentText("自动检查并恢复桌面连接").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),0)).build();
  startForeground(1302,n);
  new Thread(new Runnable(){public void run(){while(running&&enabled(KeeperService.this)){
   try{check();}catch(Exception e){state="等待恢复："+e.getClass().getSimpleName();}
   for(int n=0;n<30&&running;n++)SystemClock.sleep(500);
  }}},"m13-panel-keeper").start();
 }
 void check()throws Exception{
  ApplicationInfo panel;
  try{panel=getPackageManager().getApplicationInfo("local.m13.ipadremote",0);}catch(PackageManager.NameNotFoundException e){state="面板未安装或被隐藏";return;}
  boolean stopped=(panel.flags&ApplicationInfo.FLAG_STOPPED)!=0;
  if(!stopped&&healthy()){state="面板在线，自动守护中";return;}
  state=stopped?"发现面板被停止，正在恢复":"连接服务离线，正在恢复";
  Intent recover=new Intent();recover.setComponent(new ComponentName("local.m13.ipadremote","local.m13.ipadremote.RecoveryActivity"));
  recover.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION|Intent.FLAG_ACTIVITY_NO_HISTORY);
  startActivity(recover);
 }
 static boolean healthy(){
  HttpURLConnection c=null;
  try{c=(HttpURLConnection)new URL("http://127.0.0.1:8090/health").openConnection();c.setConnectTimeout(1500);c.setReadTimeout(1500);c.setUseCaches(false);
   if(c.getResponseCode()!=200)return false;InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buffer=new byte[512];int count;
   try{while((count=in.read(buffer))!=-1){if(b.size()+count>4096)return false;b.write(buffer,0,count);}}finally{in.close();}
   String body=b.toString("UTF-8");return body.contains("\"service\":\"m13-ipad-remote\"")&&body.contains("\"uid\":2000");
  }catch(Exception e){return false;}finally{if(c!=null)c.disconnect();}
 }
 public int onStartCommand(Intent i,int flags,int id){if(!enabled(this)){stopSelf();return START_NOT_STICKY;}arm(this);return START_STICKY;}
 public android.os.IBinder onBind(Intent i){return null;}
 public void onDestroy(){running=false;super.onDestroy();}
}
