package local.m13.home;

import android.content.Intent;
import android.widget.VideoView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;

/** Called only by the existing DUMP-protected receiver, on the app UI thread. */
public final class SyncPlayback {
 private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 public static String control(Intent intent){
  try{
   Class<?> type=Class.forName("local.m13.home.LibreTvActivity");Field active=type.getDeclaredField("active");active.setAccessible(true);
   Object activity=((WeakReference<?>)active.get(null)).get();if(activity==null)throw new IllegalStateException("No movie");
   VideoView video=(VideoView)field(activity,"video");if(video==null||video.getDuration()<=0||field(activity,"localProxy")==null)throw new IllegalStateException("Movie not ready");
   String identity=String.valueOf(field(activity,"videoTitle"))+"|"+String.valueOf(field(activity,"sourceName"))+"|"+video.getDuration();
   byte[] digest=MessageDigest.getInstance("SHA-256").digest(identity.getBytes("UTF-8"));StringBuilder key=new StringBuilder();for(int i=0;i<12;i++)key.append(String.format(Locale.US,"%02x",digest[i]&255));
   if(!key.toString().equals(intent.getStringExtra("key")))throw new IllegalStateException("Movie changed");
   String operation=intent.getStringExtra("operation");
   if("seek".equals(operation)){long at=intent.getLongExtra("positionMs",-1);if(at<0||at>24*60*60*1000L)throw new IllegalArgumentException("Position");video.seekTo((int)Math.min(at,Math.max(0,video.getDuration()-250)));}
   else if("pause".equals(operation))video.pause();
   else if("play".equals(operation))video.start();
   else throw new IllegalArgumentException("Operation");
   return new JSONObject().put("ok",true).put("controlVersion",1).put("playing",video.isPlaying()).put("positionMs",video.getCurrentPosition()).put("durationMs",video.getDuration()).toString();
  }catch(Exception e){try{return new JSONObject().put("ok",false).put("error","Movie unavailable or changed").toString();}catch(Exception ignored){return "{}";}}
 }
}
