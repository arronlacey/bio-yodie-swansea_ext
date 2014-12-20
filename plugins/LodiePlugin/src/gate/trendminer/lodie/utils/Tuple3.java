/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.trendminer.lodie.utils;

/**
 * Class to make it easy to return a triple (3-tuple) from a method.
 * 
 * @author Johann Petrak
 */
public class Tuple3<T1,T2,T3> {
  public T1 value1;
  public T2 value2;
  public T3 value3;
  private Tuple3() {}
  public Tuple3(T1 f1, T2 f2, T3 f3) {
    value1=f1;
    value2=f2;
    value3=f3;
  }
}
