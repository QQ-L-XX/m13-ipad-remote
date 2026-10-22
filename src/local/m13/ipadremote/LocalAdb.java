package local.m13.ipadremote;
import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import javax.crypto.Cipher;

/** One service only: local loopback shell. Keys are generated inside this app's private storage. */
public final class LocalAdb implements Closeable {
 static final int CNXN=0x4e584e43,AUTH=0x48545541,OPEN=0x4e45504f,OKAY=0x59414b4f,CLSE=0x45534c43,WRTE=0x45545257;
 Socket socket;InputStream in;OutputStream out;PrivateKey privateKey;RSAPublicKey publicKey;
 LocalAdb(Context context)throws Exception{
  File key=new File(context.getFilesDir(),"adb-private.pk8"),pub=new File(context.getFilesDir(),"adb-public.x509");KeyFactory factory=KeyFactory.getInstance("RSA");
  if(key.exists()&&pub.exists()){privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(readFile(key)));publicKey=(RSAPublicKey)factory.generatePublic(new X509EncodedKeySpec(readFile(pub)));}
  else{KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair pair=g.generateKeyPair();privateKey=pair.getPrivate();publicKey=(RSAPublicKey)pair.getPublic();writeFile(key,privateKey.getEncoded());writeFile(pub,publicKey.getEncoded());}
 }
 void connect()throws Exception{
  socket=new Socket();socket.connect(new InetSocketAddress("127.0.0.1",5555),3000);socket.setSoTimeout(15000);in=socket.getInputStream();out=socket.getOutputStream();send(CNXN,0x01000000,4096,"host::m13-ipad\0".getBytes("UTF-8"));boolean signed=false,sentPublic=false;
  for(int i=0;i<6;i++){Packet p=receive();if(p.command==CNXN)return;if(p.command!=AUTH||p.a!=1)throw new IOException("Unexpected local debug handshake");if(!signed){send(AUTH,2,0,signature(p.data));signed=true;}else if(!sentPublic){send(AUTH,3,0,publicMessage());sentPublic=true;}else throw new IOException("请在电视确认本机调试授权");}
  throw new IOException("本机调试授权未完成");
 }
 String shell(String command)throws Exception{
  send(OPEN,1,0,("shell:"+command+"\0").getBytes("UTF-8"));ByteArrayOutputStream result=new ByteArrayOutputStream();int remote=0;
  for(int i=0;i<100;i++){Packet p=receive();if(p.command==OKAY){remote=p.a;}else if(p.command==WRTE){remote=p.a;if(result.size()+p.data.length>65536)throw new IOException("Debug reply too large");result.write(p.data);send(OKAY,1,remote,new byte[0]);}else if(p.command==CLSE)return new String(result.toByteArray(),"UTF-8");else throw new IOException("Unexpected debug response");}
  throw new IOException("Debug command limit");
 }
 byte[]signature(byte[]token)throws Exception{byte[]prefix=new byte[]{0x30,0x21,0x30,9,6,5,0x2b,0x0e,3,2,0x1a,5,0,4,0x14};ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(prefix);b.write(token);Cipher c=Cipher.getInstance("RSA/ECB/PKCS1Padding");c.init(Cipher.ENCRYPT_MODE,privateKey);return c.doFinal(b.toByteArray());}
 byte[]publicMessage()throws Exception{
  BigInteger n=publicKey.getModulus(),word=BigInteger.ONE.shiftLeft(32);ByteBuffer b=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);b.putInt(64);b.putInt(n.mod(word).modInverse(word).negate().mod(word).intValue());little(b,n,256);little(b,BigInteger.ONE.shiftLeft(4096).mod(n),256);b.putInt(publicKey.getPublicExponent().intValue());return (Base64.encodeToString(b.array(),Base64.NO_WRAP)+" m13-ipad@local\0").getBytes("UTF-8");
 }
 static void little(ByteBuffer b,BigInteger n,int length){for(int i=0;i<length;i++){b.put(n.byteValue());n=n.shiftRight(8);}}
 void send(int command,int a,int b,byte[]data)throws IOException{int checksum=0;for(byte x:data)checksum+=x&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);h.putInt(command).putInt(a).putInt(b).putInt(data.length).putInt(checksum).putInt(command^(-1));out.write(h.array());out.write(data);out.flush();}
 Packet receive()throws IOException{ByteBuffer h=ByteBuffer.wrap(read(in,24)).order(ByteOrder.LITTLE_ENDIAN);Packet p=new Packet();p.command=h.getInt();p.a=h.getInt();p.b=h.getInt();int n=h.getInt(),sum=h.getInt(),magic=h.getInt();if(n<0||n>1048576||magic!=(p.command^(-1)))throw new IOException("Debug header invalid");p.data=read(in,n);int actual=0;for(byte x:p.data)actual+=x&255;if(actual!=sum)throw new IOException("Debug checksum invalid");return p;}
 static byte[]read(InputStream in,int size)throws IOException{byte[]b=new byte[size];int n=0;while(n<size){int k=in.read(b,n,size-n);if(k<0)throw new EOFException();n+=k;}return b;}
 static byte[]readFile(File f)throws IOException{FileInputStream in=new FileInputStream(f);try{return read(in,(int)f.length());}finally{in.close();}}
 static void writeFile(File f,byte[]data)throws IOException{FileOutputStream o=new FileOutputStream(f);try{o.write(data);}finally{o.close();}}
 public void close(){try{if(socket!=null)socket.close();}catch(Exception ignored){}}
 static class Packet{int command,a,b;byte[]data;}
}
