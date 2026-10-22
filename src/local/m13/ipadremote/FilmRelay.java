package local.m13.ipadremote;

import android.os.SystemClock;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.json.JSONObject;

/** Authenticated, bounded relay of an already playing LibreTV loopback source. */
final class FilmRelay {
 private static final Pattern LOCAL=Pattern.compile("http://127\\.0\\.0\\.1:[0-9]{1,5}/[a-f0-9-]{36}/[A-Za-z0-9_-]+");
 private static final Pattern URI=Pattern.compile("URI=\"([^\"]+)\"");
 private static final int LIMIT=4*1024*1024;
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(3,3,20,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
 private final Map<String,String> resources=new HashMap<String,String>();
 private volatile boolean preparing,ready;
 private volatile String error="",generation="",prefix="",root="",title="";
 private volatile long positionMs,requests,bytes;

 synchronized void prepare(){
  if(preparing)return;
  preparing=true;error="";
  new Thread(new Runnable(){public void run(){try{discover();}catch(Exception e){error="当前影片暂时无法转发，请先在 LibreTV 播放影片后重试";}finally{preparing=false;}}},"film-discovery").start();
 }
 JSONObject status()throws Exception{return new JSONObject().put("preparing",preparing).put("ready",ready).put("error",error).put("title",title).put("positionMs",positionMs).put("playlist",ready?"/film/"+generation+"/index.m3u8":"").put("requests",requests).put("bytes",bytes).put("systemAudio",false);}
 private static String command(String...args)throws Exception{
  final Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
  Thread timeout=new Thread(new Runnable(){public void run(){SystemClock.sleep(8000);try{p.destroy();}catch(Exception ignored){}}});timeout.setDaemon(true);timeout.start();
  try{return new String(read(p.getInputStream(),3*1024*1024),"UTF-8");}finally{p.destroy();}
 }
 static JSONObject playerStatus()throws Exception{
  String state=command("/system/bin/am","broadcast","-a","local.m13.home.LIBRE_STATUS","-n","local.m13.home/.LibreTvProvision");
  int begin=state.indexOf("data=\"");int end=state.lastIndexOf('"');
  if(begin<0||end<begin+6)throw new IOException("No player");
  return new JSONObject(state.substring(begin+6,end));
 }
 static JSONObject control(Map<String,String> fields)throws Exception{
  String operation=fields.get("operation"),key=fields.get("key");
  if(key==null||!key.matches("[a-f0-9]{24}")||(!"seek".equals(operation)&&!"pause".equals(operation)&&!"play".equals(operation)))throw new IOException("Control fields");
  long position=0;if("seek".equals(operation)){position=Long.parseLong(fields.get("positionMs"));if(position<0||position>86400000)throw new IOException("Position");}
  String result=command("/system/bin/am","broadcast","-a","local.m13.home.LIBRE_CONTROL","-n","local.m13.home/.LibreTvProvision","--es","operation",operation,"--es","key",key,"--el","positionMs",Long.toString(position));
  int begin=result.indexOf("data=\""),end=result.lastIndexOf('"');
  if(begin<0||end<begin+6)throw new IOException("Player control unavailable");
  return new JSONObject(result.substring(begin+6,end));
 }
 private void discover()throws Exception{
  JSONObject player=playerStatus();
  if(!player.optBoolean("boxLocalProxy")||player.optInt("videoWidth")<=0)throw new IOException("Not a local LibreTV movie");
  title=player.optString("title");positionMs=player.optLong("positionMs");
  String log=command("/system/bin/logcat","-d","-t","2400","-v","brief");
  Matcher match=LOCAL.matcher(log);List<String> found=new ArrayList<String>();while(match.find())found.add(match.group());
  LinkedHashSet<String> candidates=new LinkedHashSet<String>();
  for(int i=found.size()-1;i>=0&&candidates.size()<9;i--){
   String local=found.get(i);int slash=local.lastIndexOf('/');String base=local.substring(0,slash+1);
   String decoded;try{decoded=new String(Base64.decode(local.substring(slash+1),Base64.URL_SAFE),"UTF-8");}catch(Exception e){continue;}
   URL source;try{source=new URL(decoded);}catch(Exception e){continue;}
   if(!source.getProtocol().equals("http")&&!source.getProtocol().equals("https"))continue;
   if(source.getPath().endsWith(".m3u8"))candidates.add(local);
   else if(source.getPath().endsWith(".ts")){
    URL directory=new URL(source,".");
    for(int up=0;up<3;up++){URL playlist=new URL(directory,"index.m3u8");candidates.add(base+Base64.encodeToString(playlist.toString().getBytes("UTF-8"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));directory=new URL(directory,"../");}
   }
  }
  for(String candidate:candidates){
   try{
    String content=new String(fetch(candidate,1500,6000),"UTF-8");if(!content.trim().startsWith("#EXTM3U"))continue;
    String candidatePrefix=candidate.substring(0,candidate.lastIndexOf('/')+1);
    // A master playlist is accepted only when its selected media playlist is usable.
    String media=candidate;String mediaText=content;
    if(content.contains("#EXT-X-STREAM-INF")){
     String child=null;for(String row:content.split("\\r?\\n"))if(!row.trim().isEmpty()&&!row.startsWith("#")){child=new URL(new URL(candidate),row.trim()).toString();break;}
     if(child==null||!child.startsWith(candidatePrefix)||!LOCAL.matcher(child).matches())continue;
     media=child;mediaText=new String(fetch(child,1500,6000),"UTF-8");
    }
    if(!mediaText.startsWith("#EXTM3U")||!mediaText.contains("#EXTINF"))continue;
    synchronized(this){if(!media.equals(root)){resources.clear();generation=UUID.randomUUID().toString().replace("-","");prefix=candidatePrefix;root=media;requests=0;bytes=0;}ready=true;}
    return;
   }catch(Exception ignored){}
  }
  throw new IOException("No current playlist");
 }
 private static byte[]read(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[]buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException("Response limit");out.write(buf,0,n);}return out.toByteArray();}
 private static HttpURLConnection open(String url,int connect,int read)throws Exception{
  if(!LOCAL.matcher(url).matches())throw new IOException("Not a LibreTV loopback resource");
  HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection(Proxy.NO_PROXY);h.setInstanceFollowRedirects(false);h.setConnectTimeout(connect);h.setReadTimeout(read);h.setRequestProperty("Accept-Encoding","identity");return h;
 }
 private static byte[]fetch(String url,int connect,int timeout)throws Exception{HttpURLConnection h=open(url,connect,timeout);try{if(h.getResponseCode()!=200)throw new IOException("Source unavailable");return read(h.getInputStream(),LIMIT);}finally{h.disconnect();}}
 private synchronized String mapped(String local,boolean playlist)throws Exception{
  if(!local.startsWith(prefix)||!LOCAL.matcher(local).matches())throw new IOException("Resource outside current player");
  String id=Integer.toHexString(local.hashCode())+"-"+Integer.toHexString(resources.size());
  for(Map.Entry<String,String> e:resources.entrySet())if(e.getValue().equals(local))return "/film/"+generation+"/"+e.getKey();
  if(resources.size()>8192)throw new IOException("Resource limit");
  id+=playlist?".m3u8":".bin";resources.put(id,local);return "/film/"+generation+"/"+id;
 }
 private String rewrite(String text,String original)throws Exception{
  if(!text.trim().startsWith("#EXTM3U"))throw new IOException("Not a playlist");
  StringBuilder out=new StringBuilder();URL base=new URL(original);
  for(String row:text.split("\\r?\\n")){
   if(row.startsWith("#")){Matcher m=URI.matcher(row);StringBuffer replaced=new StringBuffer();while(m.find())m.appendReplacement(replaced,Matcher.quoteReplacement("URI=\""+mapped(new URL(base,m.group(1)).toString(),false)+"\""));m.appendTail(replaced);out.append(replaced);}
   else if(!row.trim().isEmpty())out.append(mapped(new URL(base,row.trim()).toString(),text.contains("#EXT-X-STREAM-INF")));
   out.append('\n');
  }return out.toString();
 }
 boolean serve(final Socket socket,final String path){
  try{workers.execute(new Runnable(){public void run(){relay(socket,path);}});return true;}catch(RejectedExecutionException e){return false;}
 }
 private void relay(Socket socket,String path){HttpURLConnection h=null;boolean sent=false;
  try{
   String local; synchronized(this){String base="/film/"+generation+"/";if(!ready||!path.startsWith(base))throw new IOException("Expired movie");String id=path.substring(base.length());local=id.equals("index.m3u8")?root:resources.get(id);if(local==null)throw new IOException("Unknown resource");}
   h=open(local,2000,15000);int code=h.getResponseCode();if(code!=200)throw new IOException("Source unavailable");
   String type=h.getContentType();if(type==null)type="application/octet-stream";
   requests++;
   if(path.endsWith(".m3u8")||type.toLowerCase(Locale.US).contains("mpegurl")){
    String content=new String(read(h.getInputStream(),LIMIT),"UTF-8");byte[]body;
    synchronized(this){if(!path.startsWith("/film/"+generation+"/"))throw new IOException("Expired movie");body=rewrite(content,local).getBytes("UTF-8");}
    DesktopServer.send(socket,200,"application/vnd.apple.mpegurl",body,null);sent=true;bytes+=body.length;
   }else{
    long length=-1;try{length=Long.parseLong(h.getHeaderField("Content-Length"));}catch(Exception ignored){}
    if(length>16*1024*1024)throw new IOException("Segment too large");
    if(type.startsWith("text/html")||type.contains("javascript"))throw new IOException("Not media");
    OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+type+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n"+(length>=0?"Content-Length: "+length+"\r\n":"")+"\r\n").getBytes("UTF-8"));sent=true;
    InputStream in=h.getInputStream();byte[]buf=new byte[32768];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>16*1024*1024)throw new IOException("Segment limit");out.write(buf,0,n);bytes+=n;}out.flush();
   }
  }catch(Exception e){if(!sent)try{DesktopServer.text(socket,503,"{\"error\":\"影片资源暂时不可用，请重试影片原声\"}");}catch(Exception ignored){}}
  finally{if(h!=null)h.disconnect();try{socket.close();}catch(Exception ignored){}}
 }
}
