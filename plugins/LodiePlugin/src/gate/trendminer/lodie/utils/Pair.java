/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.trendminer.lodie.utils;

/**
 * Class to make it easy to return some Pair of values.
 * 
 * @author Johann Petrak
 */
public class Pair<T1,T2> {
  public T1 value1;
  public T2 value2;
  private Pair() {}
  public Pair(T1 f1, T2 f2) {
    value1=f1;
    value2=f2;
  }
  
}
