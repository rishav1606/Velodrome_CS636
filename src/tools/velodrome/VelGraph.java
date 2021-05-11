package tools.velodrome;
import java.util.*;

public class VelGraph<T>
{
	private Map<T,Set<T>> hm = new HashMap<>();
	private LinkedList<T> txnHBCycle = new LinkedList<T>();
	
	
	public Map<T,Set<T>> getGraph(){
		return hm;
	}
	public LinkedList<T> getTxnHBCycle() {
		return txnHBCycle;
	}
	
	public void emptyTxnHBCycle() {
		txnHBCycle.clear();
	}
	
	public void addVertex(T s)
	{
		Set<T> mySet = new HashSet<T>();
		hm.put(s, mySet);


	}
	
	public void addEdge(T source, T destination)
	{
		if(!hm.containsKey(source))
			addVertex(source);
		if(!hm.containsKey(destination))
			addVertex(destination);

		hm.get(source).add(destination);
	}
	
	public int getVertexCount(){
         	return hm.keySet().size();
	}
	
    	public int merger(List<T> nodes){
		int flag1=0,flag2=0;
		for(T use: nodes){
		  int i = (Integer)use;
		  if(i!= 0){
		   	flag1=1;
		  }
	  	}
	  
		if(flag1==0)
		  return 0;

		for(T use: nodes){
		      int i = (Integer)use;
		      if(i!= 0){
			int flag3=0;
			for(T use1: nodes){
		         int i1 = (Integer) use1;
			  if(i1 != 0 && i1!=i && hm.get(i1).contains(i)==false){
		            flag3 = 1;
			  }
			}
		   	if(flag3==0){
		         return i;
			}
		      }
		}
		return -1;
	}
	
	public void merger2(List<T> nodes,T new_node){
		for(T use: nodes){
		  int i =(Integer) use;
		  if(i!=0)
		    addEdge(use,new_node);   
		}
	}

	
	public int getEdgeCount(){
	
		int count=0;
		
		for(T v : hm.keySet())
			count += hm.get(v).size();
			
	  	return count/2;		
	}
	
	public boolean isCyclicUtil(T use, boolean[] visited,
                                      boolean[] recStack) 
      	{
          
        	// Mark the current node as visited and
        	// part of recursion stack
		int i = (Integer)use;
        	if (recStack[i]) {
        	  //txnHBCycle.addFirst(use);       // for Txn HB Cycle
            	  return true;
  		} 
  		
        	if (visited[i])
            	  return false;
              
        	visited[i] = true;
  
        	recStack[i] = true;
		Set<T> children = hm.get(use);
		for (T nodes: children){
		    if (isCyclicUtil(nodes, visited, recStack)) {
		     	txnHBCycle.addFirst(nodes);
		        return true;
		    }
		}
		recStack[i] = false;
	  
		return false;
    	}
   
    	public boolean isCyclic() 
    	{
          
        	// Mark all the vertices as not visited and
        	// not part of recursion stack
		int V = getVertexCount();
		boolean[] visited = new boolean[V+10];
		boolean[] recStack = new boolean[V+10];
		  
		  
		// Call the recursive helper function to
		// detect cycle in different DFS trees
		for(T v : hm.keySet())
		    if (isCyclicUtil(v, visited, recStack)) {
		    	txnHBCycle.addFirst(v);
		        return true;
	  	    }
		return false;
    	}
	
    	public String toString()
    	{
        	StringBuilder builder = new StringBuilder();
  
        	for (T v : hm.keySet()) {
            		builder.append(v.toString() + ": ");
            		for (T w : hm.get(v)) {
                		builder.append(w.toString() + " ");
            		}	
            		builder.append("\n");
        	}	
  
        	return (builder.toString());
    	}
}
   
   
    	