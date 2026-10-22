package local.m13.ipadremote;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** The device secret stays in app-private preferences; Safari receives only its derived token. */
final class PersistentLogin {
 static final long MAX_AGE_SECONDS=315360000L;
 static String newSecret(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return hex(bytes);}
 static String token(String secret,String password)throws Exception{
  if(secret==null||!secret.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid login secret");
  byte[] bytes=new byte[32];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(secret.substring(i*2,i*2+2),16);
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(bytes,"HmacSHA256"));
  return hex(mac.doFinal(("M13 remembered login v1\n"+password).getBytes("UTF-8")));
 }
 static boolean authorized(String token,String cookies){
  if(cookies==null)return false;
  for(String part:cookies.split(";")){
   String value=part.trim();
   if(value.startsWith("m13=")){
    String supplied=value.substring(4);
    if(supplied.length()==64&&MessageDigest.isEqual(token.getBytes(java.nio.charset.Charset.forName("US-ASCII")),supplied.getBytes(java.nio.charset.Charset.forName("US-ASCII"))))return true;
   }
  }
  return false;
 }
 static String cookie(String token){
  SimpleDateFormat date=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",Locale.US);date.setTimeZone(TimeZone.getTimeZone("GMT"));
  return "Set-Cookie: m13="+token+"; Path=/; HttpOnly; SameSite=Lax; Max-Age="+MAX_AGE_SECONDS+"; Expires="+date.format(new Date(System.currentTimeMillis()+MAX_AGE_SECONDS*1000L))+"\r\n";
 }
 static String hex(byte[] bytes){StringBuilder text=new StringBuilder(bytes.length*2);for(byte value:bytes){int b=value&255;text.append("0123456789abcdef".charAt(b>>>4));text.append("0123456789abcdef".charAt(b&15));}return text.toString();}
}
