/**************************
 *
 * Copyright (c) 2010, Cormac Flanagan (University of California, Santa Cruz) and Stephen Freund
 * (Williams College)
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 * and the following disclaimer in the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the names of the University of California, Santa Cruz and Williams College nor the names
 * of its contributors may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 **************************/

package tools.velodrome;

import java.util.*;
import java.lang.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileWriter;
import java.util.Scanner;
import acme.util.Assert;
import acme.util.decorations.Decoration;
import acme.util.decorations.DecorationFactory;
import acme.util.decorations.DecorationFactory.Type;
import acme.util.decorations.DefaultValue;
import acme.util.decorations.NullDefault;
import acme.util.Util;
import acme.util.Yikes;
import acme.util.io.XMLWriter;
import acme.util.option.CommandLine;
import rr.RRMain;
import rr.annotations.Abbrev;
import rr.error.ErrorMessage;
import rr.error.ErrorMessages;
import rr.event.AccessEvent;
import rr.event.AccessEvent.Kind;
import rr.event.AcquireEvent;
import rr.event.ArrayAccessEvent;
import rr.event.FieldAccessEvent;
import rr.event.Event;
import rr.event.NewThreadEvent;
import rr.event.ReleaseEvent;
import rr.event.VolatileAccessEvent;
import rr.event.MethodEvent;
import rr.meta.ArrayAccessInfo;
import rr.meta.AccessInfo;
import rr.meta.AcquireInfo;
import rr.meta.FieldInfo;
import rr.meta.MethodInfo;
import rr.simple.LastTool;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.state.ShadowLock;
import rr.tool.RR;
import rr.tool.Tool;
import tools.util.VectorClock;

@Abbrev("VD")
final public class VelodromeTool extends Tool {
    
    public int node_creator = 1;
    public static VelGraph<Integer> graph = new VelGraph<>();
    public static final Object m = new Object();
    public static Set<String> excludeFunSet = new HashSet<>();
    
    public static HashMap<Integer,String> nodeName = new HashMap<Integer,String>();
    public static boolean cycleOnce = false;
    
    public final ErrorMessage<FieldInfo> fieldErrors = ErrorMessages
            .makeFieldErrorMessage("Velodrome");
    public final ErrorMessage<ArrayAccessInfo> arrayErrors = ErrorMessages
            .makeArrayErrorMessage("Velodrome");

    public VelodromeTool(String name, Tool next, CommandLine commandLine) {
      	super(name, next, commandLine);
	if (!(next instanceof LastTool)) {
            fieldErrors.setMax(1);
            arrayErrors.setMax(1);
        }
    }

     
    private static final Decoration<ShadowThread, VelThreadState> velthread = ShadowThread
            .makeDecoration("VD:threadstate", DecorationFactory.Type.MULTIPLE,
                    new NullDefault<ShadowThread, VelThreadState>());
 
    private static final Decoration<ShadowLock, VelLockState> vellock = ShadowLock
    .makeDecoration("VD:lockstate", DecorationFactory.Type.MULTIPLE,
                    new NullDefault<ShadowLock, VelLockState>());
    
    public void excludeFun(){
    	File fObj = new File("exclude.txt");
        
        //Set<String> excludeFunSet = new HashSet<>();  declared in start of tool
       try {        
        Scanner fObjReader = new Scanner(fObj);
        while(fObjReader.hasNextLine()) {
          String line = fObjReader.nextLine();
          line = line.trim();
          excludeFunSet.add(line);
          //System.out.println("\n" + line);
        }
        
        System.out.println("\nExcluded functions are: " + excludeFunSet);
       }
       catch(IOException e) {
         System.out.println("An error occurred.");
         e.printStackTrace();
       }
    
    }
    
    public static void deleteBlameNode(Integer bn){
    	Map<Integer,Set<Integer>> ghm = graph.getGraph();
    	ghm.remove(bn);
    	synchronized(graph){
    	 print("\n\nBefore deletion of blame node\n"+ graph.toString());
    	}
    	synchronized(graph){
    	  for (Integer v : ghm.keySet()) {
            	if(ghm.get(v).contains(bn)) {
                  ghm.get(v).remove(bn);		 
            	}   		 
          }
        }
        
      print("\nGraph after delettion of blame node:"+bn.toString()+"\n"+graph.toString());	
    }
    
    public static void addMethXList(String mName) {
       
      
      if(!excludeFunSet.contains(mName)) {
       File fObj = new File("exclude.txt");

       try {        
         FileWriter fObjWriter = new FileWriter("exclude.txt",true);
        fObjWriter.write("\n");
         fObjWriter.write(mName);
         fObjWriter.close();
         
         excludeFunSet.add(mName);
         print("\nNewly Excluded function : "+mName);
        }
       catch(IOException e) {
         print("An error occurred.");
         e.printStackTrace();
       }
      }
          
    }
    
    public void setNodeName(Integer n, String name){
      	synchronized(m){
               nodeName.put(n,name);
      	}
    }
    
    public static String getNodeName(Integer n){
      	synchronized(m){
               return nodeName.get(n);
      	}
    }
    
    public int get_node(){
    	int use;
      	synchronized(m){
              use = node_creator;
              node_creator++;
              return use;
      	}
    }

    public static void thread_set(ShadowThread st , VelThreadState vt){
           synchronized(velthread){
              velthread.set(st,vt);
           }
    }
    public static void lock_set(ShadowLock st , VelLockState vt){
           synchronized(vellock){
              vellock.set(st,vt);
           }
    }
    
    public static VelThreadState thread_get(ShadowThread st){
           synchronized(velthread){
               return velthread.get(st);
           }
    }
    
    public static VelLockState lock_get(ShadowLock st){
           synchronized(vellock){
               return vellock.get(st);
           }
    }
    
    // public static String extractMethodName(String mName) {
    // 	String name;
    //     if(mName.contains("__$rr_"))
    //     {
    //         name = mName.substring(6);
    //         Integer i = name.indexOf('$');
    //         name = name.substring(0,i-2);
    //         //print("index is :"+ i.toString());
    //     }
    //     else
    //         name = mName;
    //     return name;
    
    // }
   public static boolean graph_add(int src , int des,String mName){//adding edge and cycle checking
         synchronized(graph){
              graph.addEdge(src,des);
              boolean isCycle = graph.isCyclic();   //graph.isCyclic();
              String name = mName;
              //boolean atLeastOneCycle = isCycle;
               
              if(isCycle) {
               cycleOnce = true;
               LinkedList<Integer> txnHBCList = graph.getTxnHBCycle();
               Integer blameNode = txnHBCList.getLast();
               String nodeName = getNodeName(blameNode);
               //deleteBlameNode(blameNode);
               
               print("\n\nGraph before Blame:\n"+graph.toString());
               print(txnHBCList.toString()+" Blame Node:"+blameNode.toString()+",its Name:"+nodeName);
              	
              	graph.emptyTxnHBCycle();
              	//if(nodeName!=null)
              	  addMethXList(nodeName);
              	//else
              	  //addMethXList(name); 
              	//atLeastOneCycle = graph.isCyclic();
              }
              return  isCycle;                          
        }
    }
    public int graph_merge(List<Integer> nodes){
         synchronized(graph){
            int use = graph.merger(nodes);
            if(use == -1){
               int new_created_node = get_node();
               graph.merger2(nodes,new_created_node);
               use = new_created_node;
               if(graph.isCyclic())
               {
                 print("\n Error: Cycle detected in the graph.");
               }
            }
            return use;
        }
    }
    public static void print(String s){
         synchronized(m){
              System.out.println(s);
        }
    }
    
    @Override
    public void create(NewThreadEvent event) {
        final ShadowThread st = event.getThread();
        
        if(st.getTid()==0)
          excludeFun();
        
        super.create(event);
    }
    
    @Override
    public void enter(final MethodEvent event) {
    
     if(cycleOnce==false) {
     String mName = event.getInfo().toSimpleName();
     if(!excludeFunSet.contains(mName))
     {
        print("\n **Welcome to Method enter Event**"+ mName);
        final ShadowThread st = event.getThread();
        final VelThreadState vs;
        int new_node;
        vs = thread_get(st);
        if(vs == null){
            new_node = get_node();
            setNodeName(new_node,mName);
            
            final VelThreadState vt = new VelThreadState(new_node);
            vt.incCounter();
            thread_set(st,vt);
        }
        else if(vs.get_current()==0){     // nested txn check
             new_node = get_node();
             setNodeName(new_node,mName);
             vs.incCounter();
             vs.set_current(new_node);
             if(vs.get_last() != 0 && vs.get_last()!=new_node){
               if(graph_add(vs.get_last(),new_node,mName))
                 print("\n **ERROR: Detected a Cycle in graph **");
             }
        }
        else
           vs.incCounter();
      }
   //   print("\n\nGraph:\n"+ graph.toString());
     }
     super.enter(event);
    }


    @Override
    public void exit(final MethodEvent event) {
     if(cycleOnce==false) {
     String mName = event.getInfo().toSimpleName();
     if(!excludeFunSet.contains(mName))
     {
        
        print("\n **Welcome to Method exit Event**" + mName);
        final ShadowThread st = event.getThread();
        final VelThreadState vs;
        int current_node;
         
        vs = thread_get(st);
        
        vs.decCounter();

        if(vs != null && vs.get_current()!=0 && vs.getCounter()==0){

        current_node = vs.get_current();
        vs.set_current(0);
        vs.set_last(current_node);
     
        }
      }
       
         if(mName.equals("test/Test.main"))       //String mName = event.getInfo().getName();
     print("\n\nGraph:\n"+ graph.toString());
      }
      super.exit(event);
    }


    @Override
    public void acquire(AcquireEvent event) {
       //    print("\n **Welcome to New Acquire Event**");
        if(cycleOnce==false) {
        String mName = event.getInfo().getEnclosing().toSimpleName();
        final ShadowThread st = event.getThread();
        final ShadowLock lock = event.getLock();
        final VelLockState vl;
        final VelThreadState vt;
     
        vl = lock_get(lock);
        vt = thread_get(st);
        if(vt != null && vt.get_current()!=0 && vl != null && vl.get()!=0){
                if(vl.get()!=vt.get_current())
              if(graph_add(vl.get(),vt.get_current(),mName))
                print("\n **ERROR: Detected a Cylce in the graph **");
        }
        else if(vt == null || vt.get_current()==0){
             List<Integer> list=new ArrayList<Integer>();
             if(vt==null)
               list.add(0);
             else
               list.add(vt.get_last());
             if(vl==null)
               list.add(0);
             else
               list.add(vl.get());
               
             int merged_node = graph_merge(list);
	      if(vt == null){
	        final VelThreadState temp = new VelThreadState(0);
	        thread_set(st,temp);
	        final VelThreadState copied = thread_get(st);
	        copied.set_last(merged_node);
	      }
	      else{
		vt.set_last(merged_node);
	      } 
        }
      //  print("\n\nGraph:\n"+ graph.toString());
        }
        super.acquire(event);
    }

    @Override
    public void release(final ReleaseEvent event) {
      //   print("\n **Welcome to New Realese Event**");
      if(cycleOnce==false) {
        final ShadowThread st = event.getThread();
        final ShadowLock lock = event.getLock();
        final VelLockState vl;
        final VelThreadState vt;
     
        vl = lock_get(lock);
        vt = thread_get(st);
        
        if(vt!=null && vt.get_current()!=0){
         if(vl == null){
             final VelLockState temp = new VelLockState(vt.get_current());
             lock_set(lock,temp);
         }
         else{
           vl.set(vt.get_current());
         }
        }
        else if(vt == null || vt.get_current()==0){
             int setnow;
             if(vt==null)
               setnow = 0;
             else
               setnow = vt.get_last();
             if(vl == null){
               final VelLockState temp = new VelLockState(setnow);
               lock_set(lock,temp);
             }
             else{
               vl.set(setnow);
             } 
         }
      //   print("\n\nGraph:\n"+ graph.toString());
       }
       super.release(event);
    }


    @Override
    public void access(AccessEvent fae) {
      if(cycleOnce==false) {	
	String mName = fae.getAccessInfo().getEnclosing().toSimpleName();
        ShadowThread currentThread = fae.getThread();
        int tid = currentThread.getTid();
        VelThreadState stateObj = thread_get(currentThread);
        //long currNode = stateObj.getCurrTxnNode();
        
        
        if(fae.getKind()==Kind.FIELD) {  //AccessEvent(AE)
          ShadowVar shadow = fae.getShadow();             //Nowhere found getShadow definition
          						    //just abstruct function in AE
          if(shadow instanceof VelVarState) {
            long node = (long)0;
            if(stateObj!=null)
              node = stateObj.get_current();
            if(stateObj != null && node != 0){
              print("\nTarget Field:" + fae.toString());  //MetaDataInfo.java
              VelVarState var = (VelVarState) shadow;
              print("\nBefore update for tid:"+tid);
              var.get();
              if (fae.isWrite()) {
              	
              	Map<Integer,Long> lastReaders = var.getLastReaders();
              	for(int t : lastReaders.keySet()) {
              	  long reader = lastReaders.get(t);
              	  if(node!=reader && reader!=0){  
                   if(graph_add((int)reader,(int)node,mName))
                     print("\n **ERROR: Detected a cycle in the graph **");
                 }
              	}
              	
               long lastWrite = var.getLastWriteNode();
               if(lastWrite!=0 && lastWrite!=node) {
                 if(graph_add((int)lastWrite,(int)node,mName))
                   print("\n **ERROR: Detected a cycle in the graph **");
               }
    
               var.setWrite(node);
              	//VelVarState newVar = new VelVarState();
              	//fae.putShadow(newVar);      // nowhere found putShodow Definition
                //var.set("Change Name","changed course");   
              }
              else {
                var.setRead(tid,node);
               
                long lastWrite = var.getLastWriteNode();
                if(lastWrite!=0 && lastWrite!=node) {
                  if(graph_add((int)lastWrite,(int)node,mName))
                    print("\n **ERROR: Detected a cycle in the graph **");
                }
              }
              print("\nAfter update for tid:"+tid);
              var.get();
            }
            else if(stateObj == null || node == 0){
              print("\nTarget Field:" + fae.toString());  //MetaDataInfo.java
              VelVarState var = (VelVarState) shadow;
              print("\nBefore update for tid:"+tid);
              var.get();
              if (fae.isWrite()) {
                List<Integer> list=new ArrayList<Integer>();
                if(stateObj ==null)
                  list.add(0);
                else
                  list.add(stateObj.get_last());
                list.add((int)var.getLastWriteNode());
            
            	 Map<Integer,Long> lastReaders = var.getLastReaders();
              
                for(int t : lastReaders.keySet()) {
              	   long reader = lastReaders.get(t);
                   list.add((int)reader);
              	 }

                int merged_node = graph_merge(list);
                if(stateObj == null){
                   final VelThreadState temp = new VelThreadState(0);
                   thread_set(currentThread,temp);
                   final VelThreadState copied = thread_get(currentThread);
                   copied.set_last(merged_node);
                }
                else {
                   stateObj.set_last(merged_node);
                }
    
                var.setWrite(merged_node);
              	//VelVarState newVar = new VelVarState();
              	//fae.putShadow(newVar);      // nowhere found putShodow Definition
                //var.set("Change Name","changed course");   
              }
              else {
                 List<Integer> list=new ArrayList<Integer>();
                 if(stateObj==null)
                     list.add(0);
                 else
                     list.add(stateObj.get_last());

                 list.add((int)var.getLastWriteNode());
                 int merged_node = graph_merge(list);

                 var.setRead(tid,merged_node);
               
                 if(stateObj == null) {
                    final VelThreadState temp = new VelThreadState(0);
                    thread_set(currentThread,temp);
                    final VelThreadState copied = thread_get(currentThread);
                    copied.set_last(merged_node);
                 }
                 else{
                    stateObj.set_last(merged_node);
                 }
          
                 print("\nAfter update for tid:"+tid);
                 var.get();   
              }

            }
         }
      }
   //   print("\n\nGraph:\n"+ graph.toString());
     } 
     super.access(fae);
    }
    
     /* Shadow location stores ShadowVar object reference. We create ShadowVar object for keeping
       extra information that we want to attach with each variable specific to algorithm that 
       we are going to implement. In our case 'vvr' object of type VelVarState.
       
       This method doesn't create shadow location. Shadow location for each memory location/
       variable is automatically created and maintained by RoadRunner itself through AccessEvent's
       originalShadow field. This method only stores ShadowVar object reference (VelVarState)*/
       
    @Override
    public ShadowVar makeShadowVar(AccessEvent fae) {   // This function is executed only once
    							  // before firing access event
    	//return vvs;   // this return stores vvs reference in fae.originalShadow field 
        
        VelVarState vs = new VelVarState();
        return vs; 
    }





    /*@Override
    public void access(AccessEvent fae) {

        ShadowThread currentThread = fae.getThread();

        }
    }

    @Override
    public ShadowVar makeShadowVar(AccessEvent fae) {
         
    }

    @Override
    public void create(NewThreadEvent e) {
        ShadowThread td = e.getThread();
        //ts_set_lset(td, LockSet.emptySet());
        super.create(e);

    }*/


}