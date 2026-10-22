package local.m13.ipadremote;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Surface;
import java.lang.reflect.*;

/** A near-transparent 2px corner surface keeps this vendor's static display encoder fed. */
final class VideoHeartbeat {
 private Object session,control;
 private Surface surface;
 private Class<?> sc;
 private int tick;
 VideoHeartbeat()throws Exception{
  Class<?> ss=Class.forName("android.view.SurfaceSession");sc=Class.forName("android.view.SurfaceControl");
  session=ss.getConstructor().newInstance();
  Constructor<?> constructor=sc.getDeclaredConstructor(ss,String.class,int.class,int.class,int.class,int.class);constructor.setAccessible(true);
  control=constructor.newInstance(session,"M13 video heartbeat",2,2,1,4);
  try{
   Constructor<Surface> empty=Surface.class.getDeclaredConstructor();empty.setAccessible(true);surface=empty.newInstance();Surface.class.getDeclaredMethod("copyFrom",sc).invoke(surface,control);
   sc.getMethod("openTransaction").invoke(null);
   try{sc.getMethod("setLayer",int.class).invoke(control,2000000000);sc.getMethod("setPosition",float.class,float.class).invoke(control,1918f,1078f);sc.getMethod("setAlpha",float.class).invoke(control,.01f);sc.getMethod("show").invoke(control);}finally{sc.getMethod("closeTransaction").invoke(null);}
   pulse();
  }catch(Exception e){close();throw e;}
 }
 void pulse()throws Exception{Canvas c=surface.lockCanvas(null);try{c.drawColor((tick++&1)==0?Color.BLACK:Color.WHITE);}finally{surface.unlockCanvasAndPost(c);}}
 void close(){if(surface!=null){surface.release();surface=null;}if(control!=null)try{sc.getMethod("destroy").invoke(control);}catch(Exception ignored){}control=null;if(session!=null)try{session.getClass().getMethod("kill").invoke(session);}catch(Exception ignored){}session=null;}
}
