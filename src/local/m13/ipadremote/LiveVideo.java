package local.m13.ipadremote;

import android.graphics.Rect;
import android.media.*;
import android.os.*;
import android.view.Surface;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.json.JSONObject;

/** On-box AVC encoder, with short in-memory TS segments. No microphone access. */
final class LiveVideo {
 static final int WIDTH=1280,HEIGHT=720,BITRATE=1800000,FPS=30;
 static final class Segment {
  final long number;final double seconds;final byte[] bytes;
  Segment(long n,double s,byte[] b){number=n;seconds=s;bytes=b;}
 }
 private final ArrayList<Segment> segments=new ArrayList<Segment>();
 volatile boolean running;
 volatile String error="",browserState="";
 volatile long lastAccess,frameCount;
 volatile long playlistRequests,segmentRequests,startedAt,lastFrameAt,lastBrowserAt,waitingEvents;
 volatile String browserAgent="";
 private volatile String run="";
 private Thread worker;
 private long nextNumber;
 synchronized void start(){
  touch();if(worker!=null&&worker.isAlive())return;
  segments.clear();error="";frameCount=0;playlistRequests=0;segmentRequests=0;nextNumber=0;startedAt=SystemClock.elapsedRealtime();lastFrameAt=startedAt;run=UUID.randomUUID().toString().replace("-","");running=true;
  worker=new Thread(new Runnable(){public void run(){encode();}},"ipad-live-video");worker.start();
 }
 synchronized void stop(){running=false;}
 void touch(){lastAccess=SystemClock.elapsedRealtime();}
 synchronized JSONObject status()throws Exception{
  JSONObject o=new JSONObject();o.put("running",running);o.put("ready",segments.size()>=2);o.put("segments",segments.size());o.put("frames",frameCount);o.put("error",error);
  o.put("width",WIDTH);o.put("height",HEIGHT);o.put("targetFps",FPS);o.put("bitrate",BITRATE);o.put("measuredFps",Math.round(frameCount*10000.0/Math.max(1,lastFrameAt-startedAt))/10.0);o.put("playlistRequests",playlistRequests);o.put("segmentRequests",segmentRequests);o.put("waitingEvents",waitingEvents);o.put("browserReportAgeMs",lastBrowserAt==0?-1:SystemClock.elapsedRealtime()-lastBrowserAt);o.put("browserAgent",browserAgent);o.put("audioAvailable",false);o.put("audioReason","CAPTURE_AUDIO_OUTPUT is signature|system on this box; shell capture denied");o.put("browserPlayback",browserState);return o;
 }
 synchronized byte[] playlist()throws IOException{
  touch();playlistRequests++;if(segments.size()<2)throw new IOException(error.length()>0?error:"视频正在准备");
  int from=Math.max(0,segments.size()-6);double maximum=1;
  for(int i=from;i<segments.size();i++)maximum=Math.max(maximum,segments.get(i).seconds);
  StringBuilder b=new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-ALLOW-CACHE:NO\n#EXT-X-TARGETDURATION:").append((int)Math.ceil(maximum)).append("\n#EXT-X-MEDIA-SEQUENCE:").append(segments.get(from).number).append('\n');
  for(int i=from;i<segments.size();i++){Segment s=segments.get(i);b.append(String.format(Locale.US,"#EXTINF:%.3f,\n",s.seconds)).append(run).append('-').append(s.number).append(".ts\n");}
  if(!running)b.append("#EXT-X-ENDLIST\n");return b.toString().getBytes("UTF-8");
 }
 synchronized byte[] segment(String name){
  touch();segmentRequests++;for(Segment s:segments)if(name.equals(run+"-"+s.number+".ts"))return s.bytes;return null;
 }
 private synchronized void publish(double seconds,byte[] data){
  if(data.length>3000000)throw new IllegalStateException("Segment size limit");
  segments.add(new Segment(nextNumber++,seconds,data));while(segments.size()>10)segments.remove(0);
 }
 private static byte[] bytes(ByteBuffer buffer){ByteBuffer copy=buffer.duplicate();byte[] b=new byte[copy.remaining()];copy.get(b);return b;}
 private void encode(){
  MediaCodec codec=null;Surface surface=null;Object display=null;Class<?> sc=null;VideoHeartbeat heartbeat=null;
  try{
   sc=Class.forName("android.view.SurfaceControl");
   MediaFormat format=MediaFormat.createVideoFormat("video/avc",WIDTH,HEIGHT);
   format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
   format.setInteger(MediaFormat.KEY_BIT_RATE,BITRATE);format.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
   // This vendor codec rejects explicit profile/level; its default is Baseline.
   format.setLong("repeat-previous-frame-after",100000);
   codec=MediaCodec.createByCodecName("OMX.amlogic.video.encoder.avc");codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
   surface=codec.createInputSurface();codec.start();
   display=sc.getMethod("createDisplay",String.class,boolean.class).invoke(null,"M13 iPad live video",false);
   sc.getMethod("openTransaction").invoke(null);
   try{
    sc.getMethod("setDisplaySurface",IBinder.class,Surface.class).invoke(null,display,surface);
    sc.getMethod("setDisplayProjection",IBinder.class,int.class,Rect.class,Rect.class).invoke(null,display,0,new Rect(0,0,1920,1080),new Rect(0,0,WIDTH,HEIGHT));
    sc.getMethod("setDisplayLayerStack",IBinder.class,int.class).invoke(null,display,0);
   }finally{sc.getMethod("closeTransaction").invoke(null);}
   heartbeat=new VideoHeartbeat();
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();MpegTs ts=new MpegTs();byte[] config=null;long origin=-1,begin=-1,last=-1,tablesAt=-1,requested=SystemClock.elapsedRealtime(),pulsed=0;
   while(running&&SystemClock.elapsedRealtime()-lastAccess<30000){
    long now=SystemClock.elapsedRealtime();if(now-pulsed>25){heartbeat.pulse();pulsed=now;}if(now-requested>1100){Bundle b=new Bundle();b.putInt("request-sync",0);codec.setParameters(b);requested=now;}
    int index=codec.dequeueOutputBuffer(info,8000);
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();ByteArrayOutputStream all=new ByteArrayOutputStream();for(String k:new String[]{"csd-0","csd-1"})if(f.containsKey(k))all.write(bytes(f.getByteBuffer(k)));config=all.toByteArray();continue;}
    if(index<0)continue;
    try{
     if(info.size==0)continue;ByteBuffer b=codec.getOutputBuffer(index);b.position(info.offset);b.limit(info.offset+info.size);byte[] data=bytes(b);
     if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0){config=data;continue;}
     boolean key=(info.flags&MediaCodec.BUFFER_FLAG_SYNC_FRAME)!=0;
     if(origin<0){if(!key)continue;origin=info.presentationTimeUs;}
     long pts=info.presentationTimeUs-origin;
     // Vendor output sometimes timestamps successive reference frames almost equally.
     // Keep every reference frame and align timestamps to a 30 Hz presentation grid,
     // avoiding uneven near-duplicate presentation times on the legacy player.
     long tick=1000000L/FPS;pts=Math.max(((pts+tick/2)/tick)*tick,last<0?0:last+tick);
     if(key&&begin>=0&&pts-begin>=900000){publish((pts-begin)/1000000.0,ts.finish());begin=-1;}
     if(begin<0){if(!key)continue;ts.begin();begin=pts;tablesAt=pts;}
     if(pts-tablesAt>=400000){ts.tables();tablesAt=pts;}
     ByteArrayOutputStream frame=new ByteArrayOutputStream(data.length+128);
     frame.write(new byte[]{0,0,0,1,9,(byte)0xf0});if(key&&config!=null)frame.write(config);frame.write(data);
     ts.video(frame.toByteArray(),pts+1000000,key);last=pts;frameCount++;lastFrameAt=SystemClock.elapsedRealtime();
     if(ts.size()>3000000)throw new IOException("Segment exceeded memory limit");
    }finally{codec.releaseOutputBuffer(index,false);}
   }
  }catch(Throwable e){error=DesktopServer.message(e);System.err.println("live-video "+error);}
  finally{
   if(heartbeat!=null)heartbeat.close();
   if(display!=null)try{sc.getMethod("destroyDisplay",IBinder.class).invoke(null,display);}catch(Exception ignored){}
   if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
   if(surface!=null)surface.release();running=false;
  }
 }
}
