package local.m13.ipadremote;

import java.io.*;
import java.util.Arrays;

/** Small, bounded single-track AVC MPEG-TS writer for legacy Safari HLS. */
final class MpegTs {
 private final int[] counters=new int[8192];
 private ByteArrayOutputStream output;
 void begin(){output=new ByteArrayOutputStream(300000);tables();}
 byte[] finish(){byte[] b=output.toByteArray();output=null;return b;}
 int size(){return output==null?0:output.size();}
 void tables(){
  section(0,new byte[]{0,(byte)0xb0,13,0,1,(byte)0xc1,0,0,0,1,(byte)0xf0,0});
  section(4096,new byte[]{2,(byte)0xb0,18,0,1,(byte)0xc1,0,0,(byte)0xe1,0,(byte)0xf0,0,0x1b,(byte)0xe1,0,(byte)0xf0,0});
 }
 private void section(int pid,byte[] data){
  int crc=-1;for(byte b:data){crc^=(b&255)<<24;for(int j=0;j<8;j++)crc=(crc<<1)^((crc<0)?0x04c11db7:0);}
  byte[] section=new byte[data.length+5];System.arraycopy(data,0,section,1,data.length);
  for(int j=0;j<4;j++)section[1+data.length+j]=(byte)(crc>>>(24-j*8));
  packetize(pid,section,true,-1,false);
 }
 void video(byte[] data,long timeUs,boolean key){
  long pts=(timeUs*9/100)&0x1ffffffffL;
  byte[] pes=new byte[14+data.length];pes[2]=1;pes[3]=(byte)0xe0;pes[6]=(byte)0x80;pes[7]=(byte)0x80;pes[8]=5;
  pes[9]=(byte)(0x21|((pts>>>29)&14));pes[10]=(byte)(pts>>>22);pes[11]=(byte)(((pts>>>14)&0xfe)|1);pes[12]=(byte)(pts>>>7);pes[13]=(byte)(((pts<<1)&0xfe)|1);
  System.arraycopy(data,0,pes,14,data.length);packetize(256,pes,true,pts,key);
 }
 private void packetize(int pid,byte[] data,boolean first,long pcr,boolean key){
  int offset=0;while(offset<data.length){byte[] packet=new byte[188];Arrays.fill(packet,(byte)0xff);packet[0]=0x47;packet[1]=(byte)((pid>>>8)|(first?0x40:0));packet[2]=(byte)pid;
   boolean clock=first&&pcr>=0;int count=Math.min(clock?176:184,data.length-offset);int adaptation=184-count;int index=4;
   packet[3]=(byte)((adaptation>0?0x30:0x10)|(counters[pid]++&15));
   if(adaptation>0){packet[index++]=(byte)(adaptation-1);if(adaptation>1){packet[index++]=(byte)((clock?0x10:0)|(key&&first?0x40:0));if(clock){packet[index++]=(byte)(pcr>>>25);packet[index++]=(byte)(pcr>>>17);packet[index++]=(byte)(pcr>>>9);packet[index++]=(byte)(pcr>>>1);packet[index++]=(byte)(((pcr&1)<<7)|0x7e);packet[index++]=0;}index=4+adaptation;}}
   System.arraycopy(data,offset,packet,index,count);output.write(packet,0,188);offset+=count;first=false;
  }
 }
}
