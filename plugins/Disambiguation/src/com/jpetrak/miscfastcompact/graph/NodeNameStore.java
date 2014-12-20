/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jpetrak.miscfastcompact.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Container for optionally holding all the original name strings for nodes,
 * by node id.
 * This encapsulates how we store the names for node ids. At the moment we
 * use a List of String but this may change to something more space 
 * efficient later.
 * 
 * @author Johann Petrak
 */
public class NodeNameStore implements Serializable {
  private List<String> namesById = new ArrayList<String>();
  /**
   * Add a node name - this MUST be done exactly at the same time when the
   * node is added to the GraphStore!!
   * 
   * @param nodeName 
   */
  public void addNode(String nodeName) {
    namesById.add(nodeName);
  }
  public String getNodeName(int nodeId) {
    return namesById.get(nodeId);
  }
}
