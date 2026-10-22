package local.m13.ipadremote;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
public final class PersistentLoginTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  String secret="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  String token=PersistentLogin.token(secret,"fake-test-password");
  check(token.matches("[0-9a-f]{64}"),"Token format");
  check(token.equals(PersistentLogin.token(secret,"fake-test-password")),"Stable across loads");
  check(!token.equals(PersistentLogin.token(secret,"changed-test-password")),"Password change revokes old token");
  check(!token.equals(PersistentLogin.token(PersistentLogin.newSecret(),"fake-test-password")),"Device secret change revokes old token");
  check(!PersistentLogin.newSecret().equals(PersistentLogin.newSecret()),"Fresh device entropy");
  check(PersistentLogin.authorized(token,"other=1; m13="+token+"; more=2"),"Cookie among other cookies");
  check(!PersistentLogin.authorized(token,null),"Missing cookie");
  check(!PersistentLogin.authorized(token,"notm13="+token),"Cookie name exact");
  check(!PersistentLogin.authorized(token,"m13="+token.substring(1)),"Truncated cookie");
  check(!PersistentLogin.authorized(token,"m13="+token+"x"),"Appended cookie");
  check(!PersistentLogin.authorized(token,"m13="+new StringBuilder(token).reverse()),"Wrong cookie");
  String header=PersistentLogin.cookie(token);
  check(header.contains("HttpOnly")&&header.contains("SameSite=Lax")&&header.contains("Max-Age=315360000")&&header.endsWith("\r\n"),"Persistent cookie attributes");
  SimpleDateFormat date=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",Locale.US);date.setTimeZone(TimeZone.getTimeZone("GMT"));
  long remaining=date.parse(header.substring(header.indexOf("Expires=")+8).trim()).getTime()-System.currentTimeMillis();
  check(Math.abs(remaining-PersistentLogin.MAX_AGE_SECONDS*1000L)<5000,"Ten-year UTC expiry without integer overflow");
  System.out.println("PASS: stable tokens, password/secret invalidation, cookie parsing and persistent expiry (13 checks)");
 }
}
