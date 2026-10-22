package local.m13.ipadremote;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if((Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))&&c.getSharedPreferences("remote",0).getBoolean("enabled",false))c.startService(new Intent(c,BridgeService.class));}
}
