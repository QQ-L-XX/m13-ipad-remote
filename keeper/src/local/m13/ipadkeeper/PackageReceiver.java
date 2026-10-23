package local.m13.ipadkeeper;
import android.content.*;
public final class PackageReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if((Intent.ACTION_PACKAGE_REPLACED.equals(i.getAction())||Intent.ACTION_PACKAGE_ADDED.equals(i.getAction()))&&i.getData()!=null&&"local.m13.ipadremote".equals(i.getData().getSchemeSpecificPart()))KeeperService.begin(c);}
}
