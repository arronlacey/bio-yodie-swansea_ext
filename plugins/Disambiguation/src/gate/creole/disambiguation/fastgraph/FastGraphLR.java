/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.creole.disambiguation.fastgraph;

import com.jpetrak.miscfastcompact.graph.GraphStore;
import gate.Resource;
import gate.creole.AbstractLanguageResource;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.RunTime;
import gate.creole.metadata.Sharable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/**
 * A language resource representing a "fast graph". This LR uses the code
 * copied over from the miscfastcompact library to represent a DAG in 
 * memory and allow to query for direct, sharedParent, sharedChild and 
 * sequence relation counts in a very fast way. This LR automatically 
 * re-uses any existing loaded graph from the same URL or re-uses an
 * existing graph in memory when getting custom-duplicated.
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "FastGraphLR",
        comment = "A LR representing a FastGraph")
public class FastGraphLR extends AbstractLanguageResource {
  
  @CreoleParameter(comment="The URL of the fast graph file to load.")
  public void setGraphFileUrl(URL graphFileUrl) {
    this.graphFileUrl = graphFileUrl;
  }
  public URL getGraphFileUrl() {
    return graphFileUrl;
  }
  private URL graphFileUrl;
  
  @Sharable
  public void setProperty(GraphStore gstore) {
    this.gstore = gstore;
  }
  public GraphStore getProperty() {
    return gstore;
  }
  GraphStore gstore;
  
  @Override
  public Resource init() throws ResourceInstantiationException {
    if(gstore == null) {
      loadGraphStore();
    }
    return this;
  }
  
  public void loadGraphStore() throws ResourceInstantiationException {
    if(getGraphFileUrl() == null) {
      throw new ResourceInstantiationException("Graph Store URL not specified");
    }
    System.err.println("Loading GraphStore from "+getGraphFileUrl());
    long start = System.currentTimeMillis();
    long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    InputStream is = null;
    try {
      is = new GZIPInputStream(getGraphFileUrl().openStream());
    } catch (IOException ex) {
      throw new ResourceInstantiationException("Could not open GraphStore for reading: "+getGraphFileUrl(),ex);
    }
    ObjectInputStream iobjs = null;
    try {
      iobjs = new ObjectInputStream(is);
      Object tmp = iobjs.readObject();
      gstore = (GraphStore)tmp;
    } catch (Exception ex) {
      throw new ResourceInstantiationException("Could not restore GraphStore from URL "+getGraphFileUrl(),ex);
    } finally {
      try {
        if(iobjs != null) {
          iobjs.close();
        }
        if(is != null) {
          is.close();
        }
      } catch (IOException ignore) {
          //
      }
    }
    long end = System.currentTimeMillis();
    long after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    System.err.println("GraphStore finished loading in "+(end-start)/1000+
            " seconds, using about "+(after-before) / (1024 * 1024)+"MB");
  }
  
  public int getDirectRelationCount(String from, String to) {
    int id1 = gstore.getNodeId(from);
    int id2 = gstore.getNodeId(to);
    if(id1 < 0 || id2 < 0) { return 0; }
    int ret = gstore.getFirstEdgeData(id1, id2);
    if(ret < 0) {
      return 0;
    } else {
      return ret;
    }
  }
  public int getSharedParentRelationCount(String from, String to) {
    int id1 = gstore.getNodeId(from);
    int id2 = gstore.getNodeId(to);
    if(id1 < 0 || id2 < 0) { return 0; }
    int ret = gstore.getSumEdgeDataSharedParent(id1, id2);
    if(ret < 0) {
      return 0;
    } else {
      return ret;
    }
  }
  public int getSharedChildRelationCount(String from, String to) {
    int id1 = gstore.getNodeId(from);
    int id2 = gstore.getNodeId(to);
    if(id1 < 0 || id2 < 0) { return 0; }
    int ret = gstore.getSumEdgeDataSharedChild(id1, id2);
    if(ret < 0) {
      return 0;
    } else {
      return ret;
    }
  }
  public int getSequenceRelationCount(String from, String to) {
    int id1 = gstore.getNodeId(from);
    int id2 = gstore.getNodeId(to);
    if(id1 < 0 || id2 < 0) { return 0; }
    int ret = gstore.getSumEdgeDataSequence(id1, id2);
    if(ret < 0) {
      return 0;
    } else {
      return ret;
    }
  }
}
