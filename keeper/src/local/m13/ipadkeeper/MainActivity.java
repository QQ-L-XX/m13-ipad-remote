package local.m13.ipadkeeper;
import android.app.Activity;
import android.os.*;
import android.view.View;
import android.widget.*;
public final class MainActivity extends Activity {
 Handler handler=new Handler();TextView status;
 public void onCreate(Bundle b){super.onCreate(b);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(50,35,50,25);setContentView(box);
  TextView title=new TextView(this);title.setText("iPad 面板守护");title.setTextSize(28);box.addView(title);
  TextView info=new TextView(this);info.setText("开机、面板更新或被停止后，自动尝试恢复连接。\n守护自身若被强停、卸载或失去系统启动条件，仍可能需要手动打开。\n请先安装同一签名的 UI3 面板；不需要密码或 root。");info.setTextSize(18);box.addView(info);
  status=new TextView(this);status.setTextSize(21);box.addView(status);
  Button start=new Button(this);start.setText("启用守护");box.addView(start);start.setOnClickListener(new View.OnClickListener(){public void onClick(View v){KeeperService.setEnabled(MainActivity.this,true);}});
  Button stop=new Button(this);stop.setText("暂停守护");box.addView(stop);stop.setOnClickListener(new View.OnClickListener(){public void onClick(View v){KeeperService.setEnabled(MainActivity.this,false);}});
  KeeperService.setEnabled(this,true);handler.post(tick);
 }
 Runnable tick=new Runnable(){public void run(){status.setText(KeeperService.state);handler.postDelayed(this,1500);}};
 public void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}
}
