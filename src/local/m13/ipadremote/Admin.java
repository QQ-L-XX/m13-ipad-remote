package local.m13.ipadremote;
public class Admin {
 public static void main(String[]args)throws Exception{if(args.length!=1)throw new IllegalArgumentException();System.out.println(BridgeService.local("POST","/shutdown",args[0]));}
}
