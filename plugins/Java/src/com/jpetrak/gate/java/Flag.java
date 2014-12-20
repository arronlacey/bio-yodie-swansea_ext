package com.jpetrak.gate.java;

/**
 * Flag is like a mutable Boolean. It can be used as a sharable object
 * to signal a state between all duplication copies of a script.
 * @author Johann Petrak
 */
public class Flag {
  protected boolean value = false;
  public Flag(boolean value) { this.value = value; }
  public void set(boolean value) { this.value = value; }
  public boolean get() {return value; }
}
