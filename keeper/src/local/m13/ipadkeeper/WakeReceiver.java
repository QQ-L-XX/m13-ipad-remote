package local.m13.ipadkeeper;
import android.content.*;
public final class WakeReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){KeeperService.begin(c);}
}
