package com.jpetrak.miscfastcompact.store;

import com.jpetrak.miscfastcompact.utils.Utils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.Serializable;


/**
 * 
 * @author Johann Petrak
 *
 */
public class StoreOfInts implements Serializable {
   
  /**
   * 
   */
  private static final long serialVersionUID = 123L;
  
  IntArrayList theList;
  int curIndex = 0;
  
  public StoreOfInts(int capacity) {
    theList = new IntArrayList(capacity);
  }
  public StoreOfInts() {
    theList = new IntArrayList();
  }
  
  
  public int size() { return theList.size(); }
  // ********** VARIABLE LENGTH DATA METHODS **********
  
  /**
   * Add variable length data and get back the index under which we can get it back.
   * 
   * @param the data
   * @return index that can be used to get back the data
   */
  public int addData(int[] data) {
    // remember where we store the data
    int oldIndex = curIndex;
    // first store the length of the data
    addInt(data.length);
    addInts(data);
    // after storing, the new index is now moved by the length of the data
    // plus the two chars where we store the length
    curIndex += data.length+1;
    return oldIndex;
  }
  
  /** 
   * Get variable length data from the given index.
   * @param index
   * @return
   */
  public int[] getData(int index) {
    // retrieve the length 
    int l = theList.get(index);
    // now retrieve the characters for this data block
    int data[] = new int[l];
    for(int i=0; i<l; i++) {
      data[i] = theList.get(index+1+i);
    }
    return data;
  }

  /** 
   * Get the size of the variable data stored at the given index.
   * @param index
   * @return 
   */
  public int getSize(int index) {
    return theList.get(index);
  }
  
  
  // ********** FIXED LENGTH DATA METHODS **********
  
  /**
   * Add fixed length data and get back the index under which we can get it back. This will 
   * add a chunk of data of known length to the store: no length is stored in the 
   * store for this chunk. This chunk can only be retrieved with the getFixedLengthData
   * method.
   * 
   * @param data
   * @return
   */
  public int addFixedLengthData(int[] data) {
    // remember where we store the data
    int oldIndex = curIndex;
    addInts(data);
    // after storing, the new index is now moved by the length of the data
    curIndex += data.length;
    return oldIndex;
  }
  
  /**
   * Replace a block of fixed length data with new data. 
   * The data passed to this method must be of exactly the same length
   * as the data originally stored, otherwise the whole store will get
   * corrupted!
   * 
   * @param index
   * @param data
   * @return 
   */
  public int replaceFixedLengthData(int index, int[] data) {
    for(int i = 0; i<data.length; i++) {
      theList.set(index+i,data[i]);
    }
    return index;
  }
 
  
  /** 
   * Get fixed length data from the given index.
   * The length must be exactly the same as used when storing the fixed
   * length data and the data must have been stored with the addFixedLengthData
   * method.
   * @param index
   * @return
   */
  public int[] getFixedLengthData(int index, int length) {
    int data[] = new int[length];
    for(int i=0; i<length; i++) {
      data[i] = theList.get(index+i);
    }
    return data;
  }
  
  
  private void addInts(int[] is) {
    if((theList.size()+is.length) < 0) {
      throw new RuntimeException("Capacity of store exhausted, adding data would wrap index");
    }
    for(int i : is) {
      theList.add(i);
    }
  }
  
  private void addInt(int i) {
    if((theList.size()+1) < 0) {
      throw new RuntimeException("Capacity of store exhausted, adding data would wrap index");
    }
    theList.add(i);
  }
  
  
  
  
}
