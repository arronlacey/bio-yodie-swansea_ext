package gate.creole.disambiguation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.PriorityQueue;


/**
 * Read the debug log output of StructuralSimilarityPRv2 and find slowest
 * queries.
 * 
 * 
 * @author Johann Petrak
 */
public class AnalyzeStructSimLog {
  
  public static void main(String[] args) throws IOException {
    int n = 10;
    if(args.length > 0) {
      n = Integer.parseInt(args[0]);
    }
    PriorityQueue<Info> q = new PriorityQueue<Info>();
    BufferedReader ir = new BufferedReader(new InputStreamReader(System.in));
    String line;
    int linenr = 0;
    while((line = ir.readLine())!=null) {
      linenr++;
      if(line.startsWith("StructuralSimilarityPRv2")) {  
        String[] fields = line.split("\t", -1);
        //System.out.println("Processing line "+linenr+": "+line+" fields is "+fields.length);
        // fields are: 0=PRname, 1=query method, 2=node1, 3=node2, 4=time
        long time = Long.parseLong(fields[4]);
        // we store the elements by time: the head of the queue will always
        // contain the element with the smallest time so far.
        // we peek at the smallest element: if our new element is even smaller,
        // there is no point of adding it, if we already have n elements.      
        Info el = new Info(time,line);
        if(q.size() >= n) {
          Info least = q.peek();
          if(least.compareTo(el) < 0) {
            //System.out.println("Adding: "+el+" least is "+least);
            q.poll();
            q.add(el);
          } else {
            //System.out.println("NOT adding: "+el+" least is "+least);
          }
        } else {
          q.add(el);
        }
      }
    }
    for(int i=0; i<n; i++) {
      String l = q.poll().line;
      System.out.println((n-i)+": "+l);
    }
  }
  
  private static class Info implements Comparable<Info> {
    public String line;
    public long time;

    public Info(long time, String line) {
      this.line = line;
      this.time = time;
    }
    
    @Override
    public int compareTo(Info o) {
      Long tmp = this.time;
      return tmp.compareTo(o.time);
    }
    
    @Override
    public String toString() {
      return line;
    }
    
  }
  
}
