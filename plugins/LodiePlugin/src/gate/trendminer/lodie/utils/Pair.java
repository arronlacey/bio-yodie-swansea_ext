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
  @Override
  public boolean equals(Object other) {
    if(other == null) { return false; }
    if(!(other instanceof Pair)) { return false; }
    if(other == this) { return true; }
    Pair<?,?> otherPair = (Pair)other;
    return value1.equals(otherPair.value1) && value2.equals(otherPair.value2);
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 37 * hash + (this.value1 != null ? this.value1.hashCode() : 0);
    hash = 37 * hash + (this.value2 != null ? this.value2.hashCode() : 0);
    return hash;
  }
  
  @Override
  public String toString() {
    return "Pair("+value1+","+value2+")";
  }
  
}
