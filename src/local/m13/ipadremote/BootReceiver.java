package local.m13.ipadremote;
import android.content.*;
import android.os.SystemClock;
public class BootReceiver extends BroadcastReceiver {
 public void onReceive(final Context c,Intent i){
  if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))return;
  if(!c.getSharedPreferences("remote",0).getBoolean("enabled",false))return;
  KeeperControl.enabled(c,true);
  if(!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())){c.startService(new Intent(c,BridgeService.class));return;}
  final PendingResult pending=goAsync();
  new Thread(new Runnable(){public void run(){try{
   String password=c.getSharedPreferences("remote",0).getString("password",null);
   if(password!=null)try{BridgeService.local("POST","/shutdown",password);SystemClock.sleep(600);}catch(Exception ignored){}
   c.startService(new Intent(c,BridgeService.class));
  }finally{pending.finish();}}},"m13-update-restart").start();
 }
}
