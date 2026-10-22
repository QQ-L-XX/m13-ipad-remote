package local.m13.ipadremote;

import android.os.SystemClock;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Short cached, asynchronous observation; never launches or starts a player. */
final class AutoPlayback {
 private volatile String snapshot="{\"known\":false,\"active\":false}";
 private long checkedAt;
 private boolean checking;
 synchronized JSONObject status()throws Exception{
  final long now=SystemClock.elapsedRealtime();
  if(!checking&&(checkedAt==0||now-checkedAt>=1800)){
   checking=true;
   Thread worker=new Thread(new Runnable(){public void run(){try{
    JSONObject p=FilmRelay.playerStatus();boolean active=p.optBoolean("boxLocalProxy")&&p.optInt("videoWidth")>0&&p.optLong("durationMs")>0;
    String identity=p.optString("title")+"|"+p.optString("source")+"|"+p.optLong("durationMs");
    byte[] digest=MessageDigest.getInstance("SHA-256").digest(identity.getBytes("UTF-8"));StringBuilder key=new StringBuilder();for(int i=0;i<12;i++)key.append(String.format(java.util.Locale.US,"%02x",digest[i]&255));
    snapshot=new JSONObject().put("known",true).put("active",active).put("key",active?key.toString():"").put("title",active?p.optString("title"):"").put("positionMs",p.optLong("positionMs")).put("playing",p.optBoolean("playing")).put("durationMs",p.optLong("durationMs")).toString();
   }catch(Exception ignored){snapshot="{\"known\":false,\"active\":false}";}finally{synchronized(AutoPlayback.this){checkedAt=SystemClock.elapsedRealtime();checking=false;}}}},"movie-observation");worker.setDaemon(true);worker.start();
  }
  return new JSONObject(snapshot).put("checking",checking).put("ageMs",checkedAt==0?-1:now-checkedAt);
 }
}
