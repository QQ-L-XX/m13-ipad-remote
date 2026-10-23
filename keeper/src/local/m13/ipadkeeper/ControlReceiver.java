package local.m13.ipadkeeper;
import android.content.*;
public final class ControlReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if("local.m13.ipadremote.KEEPER_ENABLED".equals(i.getAction())&&i.hasExtra("enabled"))KeeperService.setEnabled(c,i.getBooleanExtra("enabled",false));}
}
