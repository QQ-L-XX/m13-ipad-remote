import java.io.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.immutable.reference.*;
import org.jf.dexlib2.builder.*;
import org.jf.dexlib2.builder.instruction.*;
import org.jf.dexlib2.writer.pool.DexPool;
import org.jf.dexlib2.writer.io.FileDataStore;

/** Preserve installed classes and add one guarded receiver dispatch plus one helper. */
public class PatchHomeDex {
 static ImmutableMethodReference method(String owner,String name,List<String>args,String result){return new ImmutableMethodReference(owner,name,args,result);}
 public static void main(String[]args)throws Exception{
  DexFile original=DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forApi(22)),helper=DexFileFactory.loadDexFile(new File(args[1]),Opcodes.forApi(22));
  DexPool pool=new DexPool(Opcodes.forApi(22));int patched=0,added=0;
  for(ClassDef c:original.getClasses()){
   if(c.getType().equals("Llocal/m13/home/SyncPlayback;"))throw new IOException("Already patched");
   if(!c.getType().equals("Llocal/m13/home/LibreTvProvision;")){pool.internClass(c);continue;}
   List<Method> methods=new ArrayList<Method>();
   for(Method m:c.getMethods()){
    if(!m.getName().equals("onReceive")){methods.add(m);continue;}
    MutableMethodImplementation impl=new MutableMethodImplementation(m.getImplementation());int count=impl.getRegisterCount(),receiver=count-3,intent=count-1;
    if(count<5||count>16)throw new IOException("Unexpected receiver registers");
    List<BuilderInstruction> guard=new ArrayList<BuilderInstruction>();
    guard.add(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,1,intent,0,0,0,0,method("Landroid/content/Intent;","getAction",Collections.<String>emptyList(),"Ljava/lang/String;")));
    guard.add(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT,0));
    guard.add(new BuilderInstruction21c(Opcode.CONST_STRING,1,new ImmutableStringReference("local.m13.home.LIBRE_CONTROL")));
    guard.add(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,2,1,0,0,0,0,method("Ljava/lang/String;","equals",Arrays.asList("Ljava/lang/Object;"),"Z")));
    guard.add(new BuilderInstruction11x(Opcode.MOVE_RESULT,0));
    // Insert remaining guard first, then bind the original entry at its new index.
    guard.add(new BuilderInstruction10x(Opcode.NOP));
    guard.add(new BuilderInstruction35c(Opcode.INVOKE_STATIC,1,intent,0,0,0,0,method("Llocal/m13/home/SyncPlayback;","control",Arrays.asList("Landroid/content/Intent;"),"Ljava/lang/String;")));
    guard.add(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT,0));
    guard.add(new BuilderInstruction11n(Opcode.CONST_4,1,-1));
    guard.add(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,2,receiver,1,0,0,0,method("Landroid/content/BroadcastReceiver;","setResultCode",Arrays.asList("I"),"V")));
    guard.add(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,2,receiver,0,0,0,0,method("Landroid/content/BroadcastReceiver;","setResultData",Arrays.asList("Ljava/lang/String;"),"V")));
    guard.add(new BuilderInstruction10x(Opcode.RETURN_VOID));
    for(int i=0;i<guard.size();i++)impl.addInstruction(i,guard.get(i));
    Label originalEntry=impl.newLabelForIndex(guard.size());impl.replaceInstruction(5,new BuilderInstruction21t(Opcode.IF_EQZ,0,originalEntry));
    methods.add(new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl));patched++;
   }
   pool.internClass(new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods));
  }
  for(ClassDef c:helper.getClasses()){if(!c.getType().equals("Llocal/m13/home/SyncPlayback;"))throw new IOException("Unexpected helper class");pool.internClass(c);added++;}
  if(patched!=1||added!=1)throw new IOException("Unexpected patch scope");
  FileDataStore store=new FileDataStore(new File(args[2]));try{pool.writeTo(store);}finally{store.close();}
  System.out.println("Patched receiver methods: "+patched+"; added helpers: "+added+"; all installed class definitions retained.");
 }
}
