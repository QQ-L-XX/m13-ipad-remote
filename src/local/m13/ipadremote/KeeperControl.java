package local.m13.ipadremote;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;

final class KeeperControl {
 static void enabled(Context context,boolean enabled){
  Intent i=new Intent("local.m13.ipadremote.KEEPER_ENABLED");
  i.setComponent(new ComponentName("local.m13.ipadkeeper","local.m13.ipadkeeper.ControlReceiver"));
  i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);i.putExtra("enabled",enabled);
  context.sendBroadcast(i,"local.m13.ipadremote.RECOVER");
 }
}
