package local.m13.ipadremote;

import android.graphics.Bitmap;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Runs as the existing authorized Android shell, never as root. */
public class DesktopServer {
 static final int PORT=8090;
 static volatile byte[] frame;
 static volatile long frameAt,activeAt,sequence,captureMs,lastInputAt,lastInteractionAt;
 static final Object captureWake=new Object();
 static final class Capture {final byte[] bytes;final long began,number;Capture(byte[] b,long t,long n){bytes=b;began=t;number=n;}}
 static volatile Capture captured;
 static volatile String touchGesture="";
 static long touchDownTime,touchLastAt;static float touchX,touchY;
 static volatile String captureError="",inputError="";
 static final long started=SystemClock.elapsedRealtime();
 static String password,session;
 static final String runtimeId=UUID.randomUUID().toString();
 static int physicalWidth=1920,physicalHeight=1080,failures;
 static final class Profile {final String id;final int width,height,quality,interval;Profile(String i,int w,int h,int q,int t){id=i;width=w;height=h;quality=q;interval=t;}}
 static final Profile CLEAR=new Profile("clear",1280,720,85,250),ORIGINAL=new Profile("original",1920,1080,90,600),SMOOTH=new Profile("smooth",960,540,70,160);
 static volatile Profile profile=CLEAR,rendered=CLEAR;
 static final String PROFILE_FILE="/data/local/tmp/m13-ipad-remote.profile";
 static String bootId="unknown";
 static final LiveVideo liveVideo=new LiveVideo();
 static final FilmRelay filmRelay=new FilmRelay();
 static final AutoPlayback autoPlayback=new AutoPlayback();
 static long blockedUntil;
 static Object inputManager;static Method inject,screenshot;
 static final ThreadPoolExecutor inputs=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16));
 static final ThreadPoolExecutor clients=new ThreadPoolExecutor(2,6,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16));

 public static void main(String[]args)throws Exception{
  if(args.length!=2||!args[0].matches("[A-Za-z0-9-]{8,40}")||!args[1].matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Expected local panel password and persisted login token");
  password=args[0];session=args[1];Looper.prepareMainLooper();
  try{Profile saved=lookup(readSmall(PROFILE_FILE));if(saved!=null)profile=saved;}catch(Exception ignored){}
  try{bootId=readSmall("/proc/sys/kernel/random/boot_id");}catch(Exception ignored){}
  Class<?> sc=Class.forName("android.view.SurfaceControl");screenshot=sc.getDeclaredMethod("screenshot",int.class,int.class);screenshot.setAccessible(true);
  Class<?> im=Class.forName("android.hardware.input.InputManager");inputManager=im.getDeclaredMethod("getInstance").invoke(null);inject=im.getDeclaredMethod("injectInputEvent",InputEvent.class,int.class);inject.setAccessible(true);
  ServerSocket server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(PORT),8);
  System.out.println("M13 iPad desktop 0.8; uid="+android.os.Process.myUid()+"; listening on "+PORT);
  new Thread(new Runnable(){public void run(){captureLoop();}},"desktop-capture").start();
  new Thread(new Runnable(){public void run(){while(true){SystemClock.sleep(500);if(touchGesture.length()>0)try{inputs.execute(new Runnable(){public void run(){if(touchGesture.length()>0&&SystemClock.uptimeMillis()-touchLastAt>2200)try{releaseTouch();}catch(Exception e){inputError=message(e);}}});}catch(RejectedExecutionException ignored){}}}},"touch-release-watch").start();
  while(true){final Socket socket=server.accept();try{clients.execute(new Runnable(){public void run(){handle(socket);}});}catch(RejectedExecutionException e){socket.close();}}
 }
 static void captureLoop(){while(true){long began=SystemClock.elapsedRealtime();Profile current=profile;if(frame==null||began-activeAt<15000){Bitmap b=null;try{
   b=(Bitmap)screenshot.invoke(null,current.width,current.height);if(b==null)throw new IOException("Capture returned no bitmap");ByteArrayOutputStream out=new ByteArrayOutputStream(100000);b.compress(Bitmap.CompressFormat.JPEG,current.quality,out);frame=out.toByteArray();rendered=current;frameAt=SystemClock.elapsedRealtime();captureMs=frameAt-began;sequence++;captured=new Capture(frame,began,sequence);captureError="";
  }catch(Throwable e){captureError=message(e);System.err.println("capture "+captureError);}finally{if(b!=null)b.recycle();}}
  int interval=began-lastInteractionAt<1800&&current!=ORIGINAL?100:current.interval;
  try{synchronized(captureWake){captureWake.wait(Math.max(12,interval-(SystemClock.elapsedRealtime()-began)));}}catch(InterruptedException e){return;}
 }}
 static String message(Throwable e){if(e instanceof InvocationTargetException&&((InvocationTargetException)e).getCause()!=null)e=((InvocationTargetException)e).getCause();return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
 static String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();while(b.size()<8192){int c=in.read();if(c<0)throw new EOFException();if(c==10)return new String(b.toByteArray(),"UTF-8").trim();b.write(c);}throw new IOException("Header too long");}
 static Map<String,String> form(String body)throws Exception{Map<String,String> m=new HashMap<String,String>();for(String p:body.split("&")){int i=p.indexOf('=');if(i>0)m.put(URLDecoder.decode(p.substring(0,i),"UTF-8"),URLDecoder.decode(p.substring(i+1),"UTF-8"));}return m;}
 static void send(Socket s,int code,String type,byte[]data,String extra)throws IOException{
  String label=code==200?"OK":code==401?"Unauthorized":code==403?"Forbidden":code==429?"Too Many Requests":code==503?"Service Unavailable":"Bad Request";
  OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 "+code+" "+label+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n"+(extra==null?"":extra)+"\r\n").getBytes("UTF-8"));out.write(data);out.flush();
 }
 static void text(Socket s,int code,String body)throws IOException{send(s,code,"application/json; charset=utf-8",body.getBytes("UTF-8"),null);}
 static boolean authorized(Map<String,String> h){return PersistentLogin.authorized(session,h.get("cookie"));}
 static void handle(Socket s){boolean transferred=false;try{
  s.setSoTimeout(5000);s.setTcpNoDelay(true);if(!s.getInetAddress().isSiteLocalAddress()&&!s.getInetAddress().isLoopbackAddress()){text(s,403,"{\"error\":\"仅限本地网络\"}");return;}
  InputStream in=new BufferedInputStream(s.getInputStream());String[]start=line(in).split(" ");if(start.length!=3)return;String[] target=start[1].split("\\?",2);String method=start[0],path=target[0];Map<String,String> query=form(target.length>1?target[1]:"");Map<String,String> h=new HashMap<String,String>();int count=0;while(true){String l=line(in);if(l.length()==0)break;if(++count>40)throw new IOException("Too many headers");int c=l.indexOf(':');if(c>0)h.put(l.substring(0,c).toLowerCase(Locale.US),l.substring(c+1).trim());}
  String host=h.get("host"),origin=h.get("origin");if(host==null||!host.equals(s.getLocalAddress().getHostAddress()+":"+PORT)||(origin!=null&&!origin.equals("http://"+host))){text(s,403,"{\"error\":\"请求来源不匹配\"}");return;}
  if(!method.equals("GET")&&!method.equals("POST")){text(s,400,"{}");return;}int n=Integer.parseInt(h.containsKey("content-length")?h.get("content-length"):"0");if(n<0||n>2048)throw new IOException("Body limit");byte[]body=new byte[n];int off=0;while(off<n){int k=in.read(body,off,n-off);if(k<0)throw new EOFException();off+=k;}
  if(path.equals("/health")){JSONObject o=new JSONObject();o.put("ok",true);o.put("service","m13-ipad-remote");o.put("version","0.8");o.put("rememberLogin",true);o.put("authVersion",1);o.put("recoveryVersion",1);o.put("runtimeId",runtimeId);o.put("uid",android.os.Process.myUid());o.put("bootId",bootId);o.put("deviceUptimeSeconds",SystemClock.elapsedRealtime()/1000);text(s,200,o.toString());return;}
  if(path.equals("/shutdown")&&method.equals("POST")&&s.getInetAddress().isLoopbackAddress()&&MessageDigest.isEqual(password.getBytes("UTF-8"),body)){text(s,200,"{\"ok\":true}");new Thread(new Runnable(){public void run(){SystemClock.sleep(300);System.exit(0);}}).start();return;}
  if(path.equals("/")){send(s,200,"text/html; charset=utf-8",readAsset("panel.html"),"Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'\r\n");return;}
  if(path.equals("/sync.js")){send(s,200,"application/javascript; charset=utf-8",readAsset("sync.js"),null);return;}
  if(path.equals("/login")&&method.equals("POST")){synchronized(DesktopServer.class){if(SystemClock.elapsedRealtime()<blockedUntil){text(s,429,"{\"error\":\"请稍后再试\"}");return;}if(!MessageDigest.isEqual(password.getBytes("UTF-8"),body)){if(++failures>=6){blockedUntil=SystemClock.elapsedRealtime()+30000;failures=0;}text(s,401,"{\"error\":\"密码错误\"}");return;}failures=0;}send(s,200,"application/json","{\"ok\":true}".getBytes("UTF-8"),PersistentLogin.cookie(session));return;}
  if(!authorized(h)){text(s,401,"{\"error\":\"请先登录\"}");return;}
  if(path.equals("/auto/status")){text(s,200,autoPlayback.status().toString());return;}
  if(path.equals("/film/control")&&method.equals("POST")){if(!"1".equals(h.get("x-m13-control"))){text(s,403,"{}");return;}JSONObject result=filmRelay.control(form(new String(body,"UTF-8")));text(s,result.optBoolean("ok")?200:503,result.toString());return;}
  if(path.equals("/film/prepare")&&method.equals("POST")){if(!"1".equals(h.get("x-m13-control"))){text(s,403,"{}");return;}filmRelay.prepare();text(s,200,filmRelay.status().toString());return;}
  if(path.equals("/film/status")){text(s,200,filmRelay.status().toString());return;}
  if(path.startsWith("/film/")&&method.equals("GET")){transferred=filmRelay.serve(s,path);if(!transferred)text(s,503,"{}");return;}
  if(path.equals("/live/start")&&method.equals("POST")){if(!"1".equals(h.get("x-m13-control"))){text(s,403,"{}");return;}liveVideo.start();text(s,200,liveVideo.status().toString());return;}
  if(path.equals("/live/stop")&&method.equals("POST")){if(!"1".equals(h.get("x-m13-control"))){text(s,403,"{}");return;}liveVideo.stop();text(s,200,"{\"ok\":true}");return;}
  if(path.equals("/live/status")){liveVideo.touch();text(s,200,liveVideo.status().toString());return;}
  if(path.equals("/live/index.m3u8")){try{send(s,200,"application/vnd.apple.mpegurl",liveVideo.playlist(),null);}catch(IOException e){text(s,503,"{\"error\":\"视频正在准备\"}");}return;}
  if(path.startsWith("/live/")&&path.endsWith(".ts")){byte[] segment=liveVideo.segment(path.substring(6));if(segment==null){text(s,410,"{\"error\":\"视频分段已过期，请重新播放\"}");return;}send(s,200,"video/mp2t",segment,null);return;}
  if(path.equals("/playback")&&method.equals("POST")){if(!"1".equals(h.get("x-m13-control"))){text(s,403,"{}");return;}String report=new String(body,"UTF-8");if(report.length()>1000){text(s,400,"{}");return;}liveVideo.browserState=report;liveVideo.lastBrowserAt=SystemClock.elapsedRealtime();String agent=h.get("user-agent");liveVideo.browserAgent=agent==null?"":agent.substring(0,Math.min(agent.length(),180));if(report.startsWith("event=waiting"))liveVideo.waitingEvents++;text(s,200,"{\"ok\":true}");return;}
  if(path.equals("/status")){JSONObject o=new JSONObject();o.put("version","0.8");o.put("sequence",sequence);o.put("captureMs",captureMs);o.put("frameAgeMs",SystemClock.elapsedRealtime()-frameAt);o.put("captureError",captureError);o.put("inputError",inputError);o.put("width",physicalWidth);o.put("height",physicalHeight);o.put("profile",profile.id);o.put("imageWidth",rendered.width);o.put("imageHeight",rendered.height);o.put("jpegQuality",rendered.quality);o.put("bootId",bootId);o.put("deviceUptimeSeconds",SystemClock.elapsedRealtime()/1000);o.put("uptimeSeconds",(SystemClock.elapsedRealtime()-started)/1000);o.put("computerRequired",false);o.put("inputAt",lastInputAt);o.put("frameBegan",captured==null?0:captured.began);o.put("touchActive",touchGesture.length()>0);send(s,200,"application/json; charset=utf-8",o.toString().getBytes("UTF-8"),PersistentLogin.cookie(session));return;}
  if(path.equals("/profile")&&method.equals("POST")){if(!"1".equals(h.get("x-m13-control"))){text(s,403,"{}");return;}Profile selected=lookup(form(new String(body,"UTF-8")).get("profile"));if(selected==null){text(s,400,"{\"error\":\"画质选项无效\"}");return;}File file=new File(PROFILE_FILE);FileOutputStream output=new FileOutputStream(file);try{output.write(selected.id.getBytes("UTF-8"));output.getFD().sync();}finally{output.close();}android.system.Os.chmod(PROFILE_FILE,0600);profile=selected;activeAt=SystemClock.elapsedRealtime();text(s,200,"{\"ok\":true}");return;}
  if(path.equals("/frame.jpg")){activeAt=SystemClock.elapsedRealtime();long required="1".equals(query.get("next"))?Math.max(lastInputAt,activeAt):lastInputAt,until=activeAt+1000;Capture f=captured;while((f==null||f.began<required||SystemClock.elapsedRealtime()-frameAt>1500)&&SystemClock.elapsedRealtime()<until){SystemClock.sleep(10);f=captured;}if(f==null){text(s,503,"{\"error\":\"正在准备画面\"}");return;}send(s,200,"image/jpeg",f.bytes,"X-M13-Frame-Sequence: "+f.number+"\r\nX-M13-Frame-Began: "+f.began+"\r\n");return;}
  if(path.equals("/input")&&method.equals("POST")){if(!"1".equals(h.get("x-m13-control"))){text(s,403,"{}");return;}final Map<String,String> m=form(new String(body,"UTF-8"));final String action=m.get("action");if(!"tap".equals(action)&&!"swipe".equals(action)&&!"key".equals(action)&&!"touch".equals(action)){text(s,400,"{}");return;}
   activeAt=SystemClock.elapsedRealtime();
   if("key".equals(action)){int key=Integer.parseInt(m.get("key"));if(key!=3&&key!=4&&key!=19&&key!=20&&key!=21&&key!=22&&key!=23){text(s,400,"{}");return;}}
   else{coordinate(m,"x");coordinate(m,"y");if("swipe".equals(action)){coordinate(m,"x2");coordinate(m,"y2");}if("touch".equals(action)){String phase=m.get("phase"),gesture=m.get("gesture");if(gesture==null||!gesture.matches("[0-9]{8,20}")||(!"down".equals(phase)&&!"move".equals(phase)&&!"up".equals(phase)&&!"cancel".equals(phase))){text(s,400,"{}");return;}}}
   if(inputs.getQueue().size()>10){text(s,429,"{\"error\":\"操作过快\"}");return;}
   Future<Long> done=inputs.submit(new Callable<Long>(){public Long call()throws Exception{try{perform(m);inputError="";lastInputAt=SystemClock.elapsedRealtime();lastInteractionAt=lastInputAt;activeAt=lastInputAt;synchronized(captureWake){captureWake.notifyAll();}return lastInputAt;}catch(Exception e){inputError=message(e);throw e;}}});try{long completed=done.get(1600,TimeUnit.MILLISECONDS);text(s,200,"{\"ok\":true,\"inputAt\":"+completed+"}");}catch(TimeoutException e){done.cancel(false);text(s,503,"{\"error\":\"操作等待超时\"}");}return;
  }text(s,400,"{\"error\":\"未知请求\"}");
 }catch(Throwable e){try{text(s,400,"{\"error\":\"请求失败\"}");}catch(Exception ignored){}System.err.println(message(e));}finally{if(!transferred)try{s.close();}catch(Exception ignored){}}}
 static float coordinate(Map<String,String> m,String key){float v=Float.parseFloat(m.get(key));if(Float.isNaN(v)||Float.isInfinite(v)||v<0||v>1)throw new IllegalArgumentException("Coordinate");return v;}
 static void motion(long down,int action,float x,float y)throws Exception{MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(0x1002);try{if(!Boolean.TRUE.equals(inject.invoke(inputManager,e,2)))throw new IOException("Touch injection rejected");}finally{e.recycle();}}
 static void perform(Map<String,String> m)throws Exception{
  if("touch".equals(m.get("action"))){String phase=m.get("phase"),gesture=m.get("gesture");float x=coordinate(m,"x")*(physicalWidth-1),y=coordinate(m,"y")*(physicalHeight-1);if("down".equals(phase)){releaseTouch();touchDownTime=SystemClock.uptimeMillis();touchLastAt=touchDownTime;touchX=x;touchY=y;motion(touchDownTime,0,x,y);touchGesture=gesture;}else if(gesture.equals(touchGesture)){touchLastAt=SystemClock.uptimeMillis();touchX=x;touchY=y;if("move".equals(phase))motion(touchDownTime,2,x,y);else{motion(touchDownTime,"up".equals(phase)?1:3,x,y);touchGesture="";}}return;}
  releaseTouch();
  if("key".equals(m.get("action"))){int code=Integer.parseInt(m.get("key"));long t=SystemClock.uptimeMillis();for(int a=0;a<2;a++){KeyEvent k=new KeyEvent(t,SystemClock.uptimeMillis(),a,code,0);if(!Boolean.TRUE.equals(inject.invoke(inputManager,k,2)))throw new IOException("Key injection rejected");}return;}
  float x=coordinate(m,"x")*(physicalWidth-1),y=coordinate(m,"y")*(physicalHeight-1);long t=SystemClock.uptimeMillis();motion(t,0,x,y);
  try{if("swipe".equals(m.get("action"))){float x2=coordinate(m,"x2")*(physicalWidth-1),y2=coordinate(m,"y2")*(physicalHeight-1);for(int i=1;i<=12;i++){Thread.sleep(20);motion(t,2,x+(x2-x)*i/12,y+(y2-y)*i/12);}x=x2;y=y2;}else Thread.sleep(60);}finally{motion(t,1,x,y);}
 }
 static void releaseTouch()throws Exception{if(touchGesture.length()>0){try{motion(touchDownTime,3,touchX,touchY);}finally{touchGesture="";}}}
 static byte[]readAsset(String name)throws IOException{InputStream in=DesktopServer.class.getClassLoader().getResourceAsStream("assets/"+name);if(in==null)throw new IOException("Missing panel asset");try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[]buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return b.toByteArray();}finally{in.close();}}
 static Profile lookup(String id){if("clear".equals(id))return CLEAR;if("original".equals(id))return ORIGINAL;if("smooth".equals(id))return SMOOTH;return null;}
 static String readSmall(String path)throws IOException{InputStream in=new FileInputStream(path);try{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(b.size()>=128)throw new IOException("Settings length");b.write(c);}return b.toString("UTF-8").trim();}finally{in.close();}}
}
