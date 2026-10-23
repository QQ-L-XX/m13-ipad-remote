package local.m13.ipadremote;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Signature-protected, invisible entry; an explicit activity start clears the stopped state. */
public final class RecoveryActivity extends Activity {
 public void onCreate(Bundle state){
  super.onCreate(state);
  if(getSharedPreferences("remote",0).getBoolean("enabled",false))startService(new Intent(this,BridgeService.class));
  finish();
 }
}
