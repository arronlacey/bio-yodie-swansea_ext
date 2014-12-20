/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jpetrak.miscfastcompact.graph;

/**
 * A small container for information about an edge.
 * This represents either an outgoing edge to some other node or an
 * incoming edge from some other node. Edges are stored for each node
 * for which that edge is an incoming or outgoing edge.
 * NOTE: the edgeData can represent anything, but only values != Integer.MIN_VALUE
 * can be stored, Integer.MIN_VALUE is reserved to indicate a missing edge
 * or other missing conditions in some methods!
 * @author Johann Petrak
 */
public class Edge implements Comparable<Edge>  {
  public int edgeData;
  public int nodeId;

  public Edge(int edgeData, int nodeId) {
    this.edgeData = edgeData;
    this.nodeId = nodeId;
  }
  
  @Override
  public int compareTo(Edge o) {
    return (o.nodeId == this.nodeId) ? 0 : ((this.nodeId < o.nodeId) ? -1 : 1);
  }
}
