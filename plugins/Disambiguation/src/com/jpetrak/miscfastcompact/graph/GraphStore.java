package com.jpetrak.miscfastcompact.graph;

import com.jpetrak.miscfastcompact.store.StoreOfInts;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Simple Graph store. Just a first attempt to create a graph store, WIP...
 * @author Johann Petrak
 */
public class GraphStore implements Serializable {
  // The store consists of the following parts:
  // = a map from URI-String to Uri-id (an int, sequential 0...(n-1)
  // = an array that maps URI id to the out edge chunk index or -1 if no edge exists (yet)
  // = an array that maps URI id to the in edge chunk index or -1 if no edge exists (yet)
  // = out-edge store and in-edge store: two separate stores where we store,
  //   at position chunk-index, a variable block of edge-data. Edge data
  //   is a table with two integers per row: first the id or count of the edg
  //   second the id of the to/from node

  public static final long serialVersionUID = 15L;
  
  private StoreOfInts outEdges;
  private StoreOfInts inEdges;
  private Object2IntAVLTreeMap<String> uri2idMap;
  //private HashMap<String,Integer> uri2idMap;
  private IntArrayList id2OutEdgeChunk;
  private IntArrayList id2InEdgeChunk;
  private int nextId = 0;
  
  public GraphStore() {
    uri2idMap = new Object2IntAVLTreeMap<String>();
    //uri2idMap = new HashMap<String,Integer>();
    outEdges = new StoreOfInts();
    inEdges = new StoreOfInts();
    id2OutEdgeChunk = new IntArrayList();
    id2InEdgeChunk = new IntArrayList();
  }

  /**
   * Add a node to the node list and return the id (unless it already exists,
   * then just return the id). 
   * @param uri
   * @return 
   */
  public int addNode(String uri) {
    if(uri2idMap.containsKey(uri)) {
      return uri2idMap.get(uri);
    } else {
      int usedId = nextId;
      uri2idMap.put(uri, nextId);
      id2InEdgeChunk.add(-1);
      id2OutEdgeChunk.add(-1);
      nextId++;
      return usedId;
    }
  }
  
  // return the id or -1 if not found
  public int getNodeId(String uri) {
    if(uri2idMap.containsKey(uri)) {
      return uri2idMap.get(uri);
    } else {
      return -1;
    }
  }

  // TO LOAD A GRAPH:
  // First, add all the known nodes: addNode(uri) for all different uris
  // Then for each node that has outgoing edges, add all the outgoing edges
  // at once: addOutEdges(nodeId, listOfEdgeData, listOfNodeIds)
  // Then for each node that has incoming edges, add all the incoming edges
  // at once: addInEdges(nodeId, listOfEdgeData, listOfNodeIds)
  // This could be achieved by reading in three files: one with just the 
  // URIs, one sorted by first uri, one sorted by second uri, and the 
  // loading program gathers all the information for blocks of identical 
  // first or second uris
  
  /**
   * Add all the incoming edges for a node. This assumes that all the nodes
   * have already been added! For each node, this must only be called once!
   * Also, the list MUST already be sorted by increasing Edge.nodeId!
   * Node that all edge data must be != Integer.MIN_VALUE, but this is not 
   * checked!
   * @param nodeId
   * @param edgeData
   * @param nodeIds 
   */
  public void addSortedInEdges(int nodeId, List<Edge> edges) {
    int[] chunk = edgesList2Chunk(edges);
    int chunkIndex = inEdges.addData(chunk);
    id2InEdgeChunk.set(nodeId, chunkIndex);
  }
  public void addInEdges(int nodeId, List<Edge> edges) {
    Collections.sort(edges);
    addSortedInEdges(nodeId,edges);
  }
  public void addSortedOutEdges(int nodeId, List<Edge> edges) {
    int[] chunk = edgesList2Chunk(edges);
    int chunkIndex = outEdges.addData(chunk);
    id2OutEdgeChunk.set(nodeId, chunkIndex);
  }
  public void addOutEdges(int nodeId, List<Edge> edges) {
    Collections.sort(edges);
    addSortedOutEdges(nodeId,edges);
  }
  private int[] edgesList2Chunk(List<Edge> edges) {
    int size = edges.size();
    int[] chunk = new int[size*2];
    for(int i=0; i<size; i++) {
      chunk[2*i] = edges.get(i).nodeId;
      chunk[2*i+1] = edges.get(i).edgeData;
    }
    return chunk;
  }
  
  // find the edge data of the first or only edge between two nodes or Integer.MIN_VALUE if
  // no edge was found
  public int getFirstEdgeData(int nodeId1, int nodeId2) {
    // first check the sizes of the two edge chunks and pick the 
    // smaller one for finding the edge!
    int chunkIndex1 = id2OutEdgeChunk.get(nodeId1);
    if(chunkIndex1<0) { return Integer.MIN_VALUE; }
    int chunkIndex2 = id2InEdgeChunk.get(nodeId2);
    if(chunkIndex2<0) { return Integer.MIN_VALUE; }
    int size1 = outEdges.getSize(chunkIndex1);
    int size2 = inEdges.getSize(chunkIndex2);
    //System.out.println("Node1="+nodeId1+", node2="+nodeId2+", size1="+size1+", size2="+size2);
    int ret = Integer.MIN_VALUE;
    int chunk[];
    int index;
    if(size1 < size2) {
      // find in the outgoing edges
      chunk = outEdges.getData(chunkIndex1);
      index = binarySearchInEntries(chunk, nodeId2, 0);
    } else {
      // find in the incoming edges
      chunk = inEdges.getData(chunkIndex2);
      index = binarySearchInEntries(chunk, nodeId1, 0);
    }
    if(index >= 0) {
      ret = chunk[index+1];  // we found the index of the nodeId, the data is behind that
    }
    return ret;
  }
  
  public int getSumEdgeDataSharedParent(int nodeId1, int nodeId2) {
    // = check which edge list is smaller, use that one
    // = for each edge in the smaller list, try to find the edge data 
    //   in the other list. The search routine returns the index if found
    //   or the smallest index for a node id larger than the wanted (negated)
    //   if not found. We use that index as the starting point for the search
    //   in the next iteration.
    int chunkIndex1 = id2InEdgeChunk.get(nodeId1);
    if(chunkIndex1<0) { 
      //System.out.println("DEBUG: no in edges: "+nodeId1);
      return 0; 
    }
    int chunkIndex2 = id2InEdgeChunk.get(nodeId2);
    if(chunkIndex2<0) { 
      //System.out.println("DEBUG: no in edges: "+nodeId1);
      return 0; 
    }
    int size1 = inEdges.getSize(chunkIndex1);
    int size2 = inEdges.getSize(chunkIndex2);
    int sumData = 0;
    int[] chunk1, chunk2;
    int index, startIndex, sizeToSearch;
    if(size1 < size2) {
      chunk1 = inEdges.getData(chunkIndex1);
      chunk2 = inEdges.getData(chunkIndex2);
      sizeToSearch = size2;
    } else {
      chunk1 = inEdges.getData(chunkIndex2);
      chunk2 = inEdges.getData(chunkIndex1);
      sizeToSearch = size1;
    }
    startIndex = 0;
    //System.out.println("DEBUG: size1="+size1+", size2="+size2);
    // go through the edges in chunk1 and get the other node, then 
    // try to find the other node in the other chunk and adjust the 
    // starting search position for the next iteration.
    //System.out.println("DEBUG: dump of chunk1 edges:");
    //debugDumpEdges(chunk1);
    //System.out.println("DEBUG: dump of chunk2 edges:");
    //debugDumpEdges(chunk2);
    for(int i=0; i<chunk1.length/2; i++) {
      int otherNodeId = chunk1[2*i];
      index = binarySearchInEntries(chunk2, otherNodeId, startIndex);
      //System.out.println("DEBUG: checked otherNode="+otherNodeId+" with start="+startIndex+", index="+index);
      // if we found something, we should add the edgeData of both edges to the sum
      // in any case, set the startIndex to point to the node which is greater
      // than the one we just found (because we go through our own nodes in 
      // increasing order)
      if(index >= 0) { // found it
        //System.out.println("DEBUG: found at index="+index+", this count="+chunk1[2*i+1]+" other="+chunk2[index+1]);
        sumData += chunk1[2*i+1] + chunk2[index+1];
        startIndex = index+2;
      } else { // did not find the other Node        
        startIndex = (-index)-2;
        if(startIndex > sizeToSearch) {
          break;
        }
      }
    }
    return sumData;
  }
  
  public int getSumEdgeDataSharedChild(int nodeId1, int nodeId2) {
    // = check which edge list is smaller, use that one
    // = for each edge in the smaller list, try to find the edge data 
    //   in the other list. The search routine returns the index if found
    //   or the smallest index for a node id larger than the wanted (negated)
    //   if not found. We use that index as the starting point for the search
    //   in the next iteration.
    int chunkIndex1 = id2OutEdgeChunk.get(nodeId1);
    if(chunkIndex1<0) { return 0; }
    int chunkIndex2 = id2OutEdgeChunk.get(nodeId2);
    if(chunkIndex2<0) { return 0; }
    int size1 = outEdges.getSize(chunkIndex1);
    int size2 = outEdges.getSize(chunkIndex2);
    int sumData = 0;
    int[] chunk1, chunk2;
    int index, startIndex;
    if(size1 < size2) {
      chunk1 = outEdges.getData(chunkIndex1);
      chunk2 = outEdges.getData(chunkIndex2);
    } else {
      chunk1 = outEdges.getData(chunkIndex2);
      chunk2 = outEdges.getData(chunkIndex1);
    }
    startIndex = 0;
    // go through the edges in chunk1 and get the other node, then 
    // try to find the other node in the other chunk and adjust the 
    // starting search position for the next iteration.
    for(int i=0; i<chunk1.length/2; i++) {
      int otherNodeId = chunk1[2*i];
      index = binarySearchInEntries(chunk2, otherNodeId, startIndex);
      // if we found something, we should add the edgeData of both edges to the sum
      // in any case, set the startIndex to point to the node which is greater
      // than the one we just found (because we go through our own nodes in 
      // increasing order)
      if(index >= 0) { // found it
        sumData += chunk1[2*i+1] + chunk2[index+1];
        startIndex = index+2;
      } else { // did not find the other Node
        startIndex = (-index)-2;
      }
    }
    return sumData;
  }
  
  public int getSumEdgeDataSequence(int nodeId1, int nodeId2) {
    // = check which edge list is smaller, use that one
    // = for each edge in the smaller list, try to find the edge data 
    //   in the other list. The search routine returns the index if found
    //   or the smallest index for a node id larger than the wanted (negated)
    //   if not found. We use that index as the starting point for the search
    //   in the next iteration.
    int chunkIndex1 = id2OutEdgeChunk.get(nodeId1);
    if(chunkIndex1<0) { return 0; }
    int chunkIndex2 = id2InEdgeChunk.get(nodeId2);
    if(chunkIndex2<0) { return 0; }
    int size1 = outEdges.getSize(chunkIndex1);
    int size2 = inEdges.getSize(chunkIndex2);
    int sumData = 0;
    int[] chunk1, chunk2;
    int index, startIndex;
    if(size1 < size2) {
      chunk1 = outEdges.getData(chunkIndex1);
      chunk2 = inEdges.getData(chunkIndex2);
    } else {
      chunk1 = inEdges.getData(chunkIndex2);
      chunk2 = outEdges.getData(chunkIndex1);
    }
    startIndex = 0;
    // go through the edges in chunk1 and get the other node, then 
    // try to find the other node in the other chunk and adjust the 
    // starting search position for the next iteration.
    for(int i=0; i<chunk1.length/2; i++) {
      int otherNodeId = chunk1[2*i];
      index = binarySearchInEntries(chunk2, otherNodeId, startIndex);
      // if we found something, we should add the edgeData of both edges to the sum
      // in any case, set the startIndex to point to the node which is greater
      // than the one we just found (because we go through our own nodes in 
      // increasing order)
      if(index >= 0) { // found it
        sumData += chunk1[2*i+1] + chunk2[index+1];
        startIndex = index+2;
      } else { // did not find the other Node
        startIndex = (-index)-2;
      }
    }
    return sumData;
  }
  
  
  /**
   * Given an otherNode id and a chunk of edges, find the index of the first
   * edge that matches the node id or return -1 if not found.
   * @param nodeId
   * @param chunk
   * @return 
   */
  public int findFirstNodeIndex(int nodeId, int[] chunk) {
    // we use our own version of binary sort to find the node
    return binarySearchInEntries(chunk, nodeId, 0);
  }
  
  // A modification of binary search that only looks at the indices 
  // 0, 2, 4, .... in the array
  // This searches the entries array to find the key in one of these positions
  // and returns the index (>= 0) if found or the insertion point as a negative int
  // if not found.
  // NOTE: start must be an even number, i.e. start always must be the index
  // if a nodeId!!
  protected int linearSearchInEntries(int[] entries, int find, int start) {    
    for(int i=start/2;i<entries.length/2;i++) {
      if(entries[2*i]>find) {
        return -(2*i)-2;
      } else if(entries[2*i]==find) {
        return 2*i;
      }
    }
    return -entries.length-4;
  }
  
  
  protected int binarySearchInEntries(int[] entries, int find, int start) {
    if(start > (entries.length-2)) {
      return -start;
    }
    int nrentries = (entries.length) / 2 - start/2;
    int low = start/2;
    int high = low+(nrentries - 1);

    while (low <= high) {
        int mid = (low + high)/2;
        int midVal = entries[mid*2];

        if (midVal < find)
            low = mid + 1;
        else if (midVal > find)
            high = mid - 1;
        else
            return (mid*2); // key found
    }
    return  2*(-low)-2;  // key not found.    
  }
  
  
  public void debugDumpEdges(int[] chunk) {
    for(int i=0;i<chunk.length/2;i++) {
      System.out.println("Edge nodeId="+chunk[2*i]+" edgedata="+chunk[2*i+1]);
    }
  }
  public void debugDumpOutEdges(String node) {
    int nodeId = getNodeId(node);
    int chunkIndex = id2OutEdgeChunk.getInt(nodeId);
    if(chunkIndex < 0) {
      System.out.println("No out Edges for "+node);
    } else {
      int[] chunk = outEdges.getData(chunkIndex);
      debugDumpEdges(chunk);
    }
  }
  public void debugDumpInEdges(String node) {
    int nodeId = getNodeId(node);
    int chunkIndex = id2InEdgeChunk.getInt(nodeId);
    if(chunkIndex < 0) {
      System.out.println("No in edges for "+node);
    } else {
      int[] chunk = inEdges.getData(chunkIndex);
      debugDumpEdges(chunk);
    }
  }
  
  // for debugging mainly
  public void debugPrintEdges(String uri) {
    int id = getNodeId(uri);
    if(id == -1) {
      System.out.println("Node not known: "+uri);
    } else {
      System.out.println("Finding edges for node "+id);
    }
    // get the chunk for the in edges for uri
    int inChunk = id2InEdgeChunk.get(id);
    if(inChunk == -1) {
      System.out.println("No in edges for "+uri);
    } else {
      System.out.println("Chunk index for in edges "+inChunk);
      int[] chunk = inEdges.getData(inChunk);
      int size = chunk.length/2;
      System.out.println("Got in edges "+size);
      for(int i=0; i<size; i++) {
        int relData = chunk[2*i+1];
        int nodeId = chunk[2*i];
        System.out.println("In Edge "+i+": nodeid="+nodeId+", data="+relData);
      }
    }
    int outChunk = id2OutEdgeChunk.get(id);
    if(outChunk == -1) {
      System.out.println("No out edges for "+uri);
    } else {
      System.out.println("Chunk index for out edges "+outChunk);
      int[] chunk = outEdges.getData(outChunk);
      int size = chunk.length/2;
      System.out.println("Got out edges "+size);
      for(int i=0; i<size; i++) {
        int relData = chunk[2*i+1];
        int nodeId = chunk[2*i];
        System.out.println("Out Edge "+i+": nodeid="+nodeId+", data="+relData);
      }
    }
  }
  
  public int debugGetInEdgesSize() {
    return inEdges.size();
  }
  public int debugGetOutEdgesSize() {
    return outEdges.size();
  }
  public int debugGetInId2ChunkSize() {
    return id2InEdgeChunk.size();
  }
  public int debugGetOutId2ChunkSize() {
    return id2OutEdgeChunk.size();
  }
  
  
  public static void main(String[] args) {
    System.out.println("Running main ...");
    GraphStore gstore = new GraphStore();
    NodeNameStore nstore = new NodeNameStore();
    gstore.addNode("uri1");
    nstore.addNode("uri1");
    gstore.addNode("uri2");
    nstore.addNode("uri2");
    gstore.addNode("uri3");
    nstore.addNode("uri3");
    gstore.addNode("uri4");
    nstore.addNode("uri4");
    gstore.addNode("uri5");
    nstore.addNode("uri5");
    gstore.addNode("uri6");
    nstore.addNode("uri6");
    
    ArrayList<Edge> edges = new ArrayList<Edge>();
    
    edges.add(new Edge(22,gstore.getNodeId("uri2")));
    edges.add(new Edge(23,gstore.getNodeId("uri3")));
    gstore.addOutEdges(gstore.getNodeId("uri1"), edges);

    edges = new ArrayList<Edge>();
    edges.add(new Edge(24,gstore.getNodeId("uri3")));
    edges.add(new Edge(23,gstore.getNodeId("uri4")));
    gstore.addOutEdges(gstore.getNodeId("uri2"), edges);
    
    edges = new ArrayList<Edge>();
    edges.add(new Edge(22,gstore.getNodeId("uri1")));
    gstore.addInEdges(gstore.getNodeId("uri2"), edges);
    
    edges = new ArrayList<Edge>();
    edges.add(new Edge(23,gstore.getNodeId("uri1")));
    edges.add(new Edge(24,gstore.getNodeId("uri2")));
    gstore.addInEdges(gstore.getNodeId("uri3"), edges);
    
    edges = new ArrayList<Edge>();
    edges.add(new Edge(23,gstore.getNodeId("uri2")));
    gstore.addInEdges(gstore.getNodeId("uri4"), edges);
    
    gstore.debugPrintEdges("uri1");
    gstore.debugPrintEdges("uri2");
    gstore.debugPrintEdges("uri3");
    gstore.debugPrintEdges("uri4");
    System.out.println("InEdgesSize="+gstore.debugGetInEdgesSize());
    System.out.println("OutEdgesSize="+gstore.debugGetOutEdgesSize());
    System.out.println("Name for id 1: "+nstore.getNodeName(1));
    System.out.println("Name for id 2: "+nstore.getNodeName(2));
    System.out.println("Edge between uri1 and uri2: "+gstore.getFirstEdgeData(gstore.getNodeId("uri1"), gstore.getNodeId("uri2")));
    System.out.println("Edge between uri1 and uri3: "+gstore.getFirstEdgeData(gstore.getNodeId("uri1"), gstore.getNodeId("uri3")));
    System.out.println("Edge between uri1 and uri4: "+gstore.getFirstEdgeData(gstore.getNodeId("uri1"), gstore.getNodeId("uri4")));
    System.out.println("Parent sum uri3, uri2: "+gstore.getSumEdgeDataSharedParent(gstore.getNodeId("uri3"), gstore.getNodeId("uri2")));
    System.out.println("Finishing main ....");
  }
  
  
  
}
