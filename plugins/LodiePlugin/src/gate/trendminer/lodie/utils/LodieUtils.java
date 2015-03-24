/*
 *  LodieUtils.java
 *
 * Copyright (c) 2000-2012, The University of Sheffield.
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free
 * software, licenced under the GNU Library General Public License,
 * Version 3, 29 June 2007.
 *
 * A copy of this licence is included in the distribution in the file
 * licence.html, and is also available at http://gate.ac.uk/gate/licence.html.
 *
 *  johann, 25/4/2014
 *
 * For details on the configuration options, see the user guide:
 * http://gate.ac.uk/cgi-bin/userguide/sec:creole-model:config
 */

package gate.trendminer.lodie.utils;

import gate.*;
import gate.annotation.ImmutableAnnotationSetImpl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import gate.util.GateRuntimeException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import org.apache.commons.lang.StringEscapeUtils;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.logging.Logger;

/** 
 * Various utility methods that should make it easier to do LODIE-stuff.
 * 
 * One large part of this is support for handling "candidates" more conveniently.
 * Candidates are usually represented in a document as annotations (e.g. Lookup) 
 * which all belong to a single list annotation (e.g. LookupList). The list
 * annotation contains a feature "ids" with the list of annotation ids of the
 * candidate annotations. For handling one or more candidates in Java, it can
 * be more convenient to just handle the feature map of a candidate annotation
 * though. For all the methods here, we use "Candidate" to refer to the
 * featuremap representation while we use "CandidateAnn" to refer to the 
 * annotation representation.
 * 
 * @author Johann Petrak
 */
public class LodieUtils {

  // TODO: eventually use better names for these features, e.g. "gate.listann.ids" and "gate.listanns.llId"
  /**
   * The name of the feature in a list annotation that contains the list of ids of the referenced
   * annotations.
   */
  public static final String IDS = "ids";
  /**
   * Then name of the feature in a referenced annotation that contains the id of the list annotation
   * the references it. 
   * 
   * We deliberately allow each referenced annotation to only belong to exactly one list annotation
   * to avoid all kinds of problems when manipulating lists. In order to have more than one list
   * annotation reference an annotation, both must be copied.
   */
  public static final String LLID = "llId";
  
  
  private final static Logger logger = Logger.getLogger(LodieUtils.class.toString());
  /**
   * Get an annotation set of all candidates for a LookupList
   * 
   * @param fromSet: the annotation set that contains the Lookup annotations
   * @param lookupList: the annotation that contains the list of Lookup ids
   * @return an AnnotationSet of all the Lookup annotations listed 
   */
  public static AnnotationSet getCandidateAnns(AnnotationSet fromSet, Annotation lookupList) {
    List<Integer> ids = getIds(lookupList);
    Set<Annotation> cands = new HashSet<Annotation>();
    for(Integer id : ids) {
      cands.add(fromSet.get(id));
    }
    return new ImmutableAnnotationSetImpl(fromSet.getDocument(), cands);
  }

  /**
   * Get a list of feature maps of all candidates for a LookupList
   * 
   * @param fromSet: the annotation set that contains the Lookup annotations
   * @param lookupList: the annotation that contains the list of Lookup ids
   * @return an List of all the feature maps from the Lookup annotations listed 
   */
  public static List<FeatureMap> getCandidateList(AnnotationSet fromSet, Annotation lookupList) {
    List<Integer> ids = getIds(lookupList);
    List<FeatureMap> cands = new ArrayList<FeatureMap>();
    for(Integer id : ids) {      
      Annotation ann = fromSet.get(id);
      if(ann == null) {
        throw new GateRuntimeException("List annotation refers to non-existing id "+id+": "+lookupList);
      }
      cands.add(ann.getFeatures());
    }
    return cands;
  }


  /** 
   * Destructively remove all candidates from the originalCandidates where the
   * given features does not match any candidate in the keepCandidates.
   * 
   * If no features are given, all features must match. This reduces the 
   * candidates in the origCandidates collection to only those which have
   * a match in the keepCandidates collection. This means the candidates
   * in the origCandidates collection represent the intersection of matching
   * candidates between the two collections.
   * 
   * @param origCandiates: the annotation set that contains the original candidates
   * @param keepCandidates: a collection of FeatureMaps corresponding used for filtering
   * @param features: one or more features to use for matching, at least one required
   */
  public static int keepCandidatesByCollection(Collection<FeatureMap> origCandidates, Collection<FeatureMap> keepCandidates, String... features) {
    Iterator<FeatureMap> it = origCandidates.iterator();
    int nremoved = 0;
    while (it.hasNext()) {
      FeatureMap fm = it.next();
      if (!hasMatchingCandidate(keepCandidates, fm, features)) {
        it.remove();
        nremoved++;
      }
    }
    return nremoved;
  }
  
  /**
   * Return a list of candidates that occur in both collections, according to the given features.
   * 
   * This returns the intersection of both candidate collection, where candidate
   * equality is based on the features given, or on all features if no feature
   * is given. The returned list is a new object.
   * 
   * @param cands1
   * @param cands2
   * @param features
   * @return 
   */
  public static List<FeatureMap> intersectCandidates(Collection<FeatureMap> cands1, Collection<FeatureMap> cands2, String... features) {
    List<FeatureMap> ret = new ArrayList<FeatureMap>();    
    Iterator<FeatureMap> it = cands1.size() < cands2.size() ? cands1.iterator() : cands2.iterator();
    Collection<FeatureMap> other = cands1.size() < cands2.size() ? cands2 : cands1;
    while (it.hasNext()) {
      FeatureMap fm = it.next();
      if (hasMatchingCandidate(other, fm, features)) {
        FeatureMap newfm = Factory.newFeatureMap();
        newfm.putAll(fm);
        ret.add(newfm);
      }
    }
    return ret;
  }

  /**
   * Return a list of candidates that occur in both annotation lists, according to the given features.
   * 
   * @param fromSet
   * @param listAnn1
   * @param listAnn2
   * @param features
   * @return 
   */
  public static List<FeatureMap> intersectCandidates(AnnotationSet fromSet, Annotation listAnn1, Annotation listAnn2, String... features) {
    return intersectCandidates(getCandidateList(fromSet,listAnn1),getCandidateList(fromSet,listAnn2),features);
  }

  /**
   * Return a list of candidates that occur in either of two collections, according to the given features.
   * 
   * This returns the union of both candidate collection, where candidate
   * equality is based on the features given, or on all features if no feature
   * is given.
   * 
   * This is done by creating a new list and first adding all elements from the
   * first list to the new list, then adding all elements from the second list
   * which are not in the first list.
   * 
   * @param cands1
   * @param cands2
   * @param features
   * @return 
   */
  public static List<FeatureMap> unionCandidates(Collection<FeatureMap> cands1, Collection<FeatureMap> cands2, String... features) {
    List<FeatureMap> ret = new ArrayList<FeatureMap>();    
    ret.addAll(cands1);
    for(FeatureMap fm : cands2) {
      if (!hasMatchingCandidate(cands1, fm, features)) {
        FeatureMap newfm = Factory.newFeatureMap();
        newfm.putAll(fm);
        ret.add(newfm);
      }
    }
    return ret;
  }

  /**
   * Return a list of candidates that occur in either of two annotation lists, according to the given features.
   * 
   * @param fromSet
   * @param listAnn1
   * @param listAnn2
   * @param features
   * @return 
   */
  public static List<FeatureMap> unionCandidates(AnnotationSet fromSet, Annotation listAnn1, Annotation listAnn2, String... features) {
    return unionCandidates(getCandidateList(fromSet,listAnn1),getCandidateList(fromSet,listAnn2),features);
  }
  
  /**
   * Merge the second list annotation into the first.
   * 
   * This will move the references of all the annotations referenced in the 
   * second annotation to the first annotation, unless that annotation id 
   * is already present there. If the parameter byFeatures is true, then
   * an annotation is not moved if there is already an annotation with the same
   * features and the same offsets. In that case the features specified will
   * be used to check equality, if no features are specified, all features are
   * used to check equality.
   * 
   * This will also merge the features from the second list annotation into the
   * first list annotation, but will not overwrite any feature that is already there.
   * To indicate that the toAnno is the result of a merge, it will set the 
   * feature LodieUtils.isMerged to true.
   */
  public static void mergeListAnns(AnnotationSet set, Annotation toAnn, Annotation fromAnn,
          boolean byFeatures, String... features) {
    // TODO: this does not check if the ids features really is a list
    // and it does not check if it really is a list of Integer 
    @SuppressWarnings("unchecked")
    List<Integer> toIdList = (List<Integer>)toAnn.getFeatures().get(IDS);
    List<FeatureMap> toCandList = getCandidateList(set, toAnn);
    @SuppressWarnings("unchecked")
    List<Integer> fromIdList = (List<Integer>)fromAnn.getFeatures().get(IDS);
    Set<Annotation> toRemove = new HashSet<Annotation>();
    Set<Integer> toRemoveIds = new HashSet<Integer>();
    for(int id : fromIdList) {
      //logger.info("Checking id "+id);
      // if the id is not in the to list, consider it for adding
      if(!toIdList.contains(id)) {
        // if we need to check the features, check those too
        Annotation ann = set.get(id);
        if(byFeatures) {  // we need to check the features too
          if(!hasMatchingCandidate(toCandList, ann.getFeatures(), features)) {
            //logger.info("Not in target and not equal, adding to destination "+id);
            // ok, we are safe to add it
            toIdList.add(id);
          } else {
            // this is an id not in the to annotation but we do not want to 
            // add it, so we need to remove that annotation and remove
            // the id from the list
            toRemove.add(ann);
            toRemoveIds.add(id);
            //logger.info("Not in target but equal, removing: "+id);
          }
        } else {
          // we do not need to check by features and are thus save to merge
          //logger.info("Not in target, not checking for equal, adding to destination: "+id);
          toIdList.add(id);
        }
      } else {
        // this id is present in the to set, so the same annotation is contained
        // in both lists. Since we keep the annotation, just do nothing
        //logger.info("Id is in the target: "+id);
        toRemoveIds.add(id);
      }
      //logger.info("toIdList="+toIdList);
    }
    // actually remove the annotations we want to remove
    set.removeAll(toRemove);
    //logger.info("after remove, toIdList="+toIdList);
    fromIdList.removeAll(toRemoveIds);
    // copy all the annotations from -> to. This will NOT overwrite anything that
    // was already there!!
    FeatureMap toFm = toAnn.getFeatures();
    FeatureMap fromFm = fromAnn.getFeatures();
    for(Object fName : fromFm.keySet()) {
      if(!toFm.containsKey(fName)) {
        toFm.put(fName,fromFm.get(fName));
      }
    }
    toAnn.getFeatures().put("LodieUtils.isMerged",true);
    // and also remove the from list annotation
    set.remove(fromAnn);
    //logger.info("after remove fromAnn, toIdList="+toIdList);
  }
  
  /**
   * Return true if the collection contains at least one matching candidate.
   * 
   * This will check all candidates in the collection if any of them matches
   * the candidate given, optionally by just looking at the features given.
   * If no feature is given, all the features in the candidate feature map
   * must match all the features in the map from toBeSearched and there 
   * may be no additional features in either map.
   * 
   * @param toBeSearched
   * @param candidate
   * @param features
   * @return 
   */
  public static boolean hasMatchingCandidate(Collection<FeatureMap> toBeSearched, FeatureMap candidate, String... features) {
    Iterator<FeatureMap> it = toBeSearched.iterator();    
    while(it.hasNext()) {
      if(isMatchingCandidates(it.next(),candidate,features)) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Returns true if the given id is the id of a candidate for that list annotation.
   * @param listAnn
   * @param candidateId
   * @return 
   */
  public static boolean hasCandidateAnn(Annotation listAnn, int candidateId) {
    boolean ret = false;
    List<Integer> ids = getIds(listAnn);
    for(Integer id : ids) {
      if(candidateId == id) {
        ret = true;
        break;
      }
    }
    return ret;
  }
  
  /**
   * Returns true if the given candidate annotation is a member of the list annotation.
   * @param listAnn
   * @param candidateAnn
   * @return 
   */
  public static boolean hasCandidateAnn(Annotation listAnn, Annotation candidateAnn) {
    return hasCandidateAnn(listAnn,candidateAnn.getId());
  }
  
  /**
   * Check if two candidates match.
   * 
   * The candidates match if all the values for the features are equal. If no
   * features are given, they match if both have the same keys and the values
   * for all keys are equal.
   * 
   * @param cand1
   * @param cand2
   * @param features
   * @return 
   */
  public static boolean isMatchingCandidates(FeatureMap cand1, FeatureMap cand2, String... features) {
    if(features.length == 0) {
      Set<Object> k1s = cand1.keySet();
      Set<Object> k2s = cand2.keySet();
      if(k1s.size() != k2s.size()) {
        return false;
      }
      for(Object k1 : k1s) {
        if(!k2s.contains(k1)) { return false; }
        if(!cand1.get(k1).equals(cand2.get(k1))) { return false; }
      }
      return true;
    } else {
      for(String feature : features) {
        if(!cand1.get(feature).equals(cand2.get(feature))) {
          return false;
        }
      }
      return true;
    }
  }
  
  /**
   * Remove a LookupList annotation and all the candidates it references.
   * 
   * Returns the number of candidate annotations removed.
   * @param fromSet
   * @param listAnn 
   */
  public static int removeListAnns(AnnotationSet fromSet, Annotation listAnn) {
    List<Integer> ids = getIds(listAnn);
    int nremoved = 0;
    for(Integer id : ids) {
      fromSet.remove(fromSet.get(id));
      nremoved++;
    }
    fromSet.remove(listAnn);
    return nremoved;
  }
  
  /**
   * Remove an candidate annotation from the fromSet, taking care of updating the id list.
   * 
   * Remove the candidate annotation and also update the list of candidate annotation
   * ids stored in the list annotation to which this candidate annotation belongs.
   * NOTE: this expects the candidate to have a feature "llId" which contains
   * the id of the list annotation, but if that feature is not found, than it
   * will try to find the the list annotation by finding all coextensive LookupList
   * annotations.
   * 
   * @param candidate 
   */
  public static int removeCandidateAnn(AnnotationSet fromSet, Annotation candidate) {
    FeatureMap fm = candidate.getFeatures();
    Integer listId = (Integer)fm.get(LLID);
    Annotation listAnn = null;
    if(listId == null) {
      throw new GateRuntimeException("Candidate annotation does not have a list annotation id");
      /*
      // fallback code
      AnnotationSet lists = Utils.getCoextensiveAnnotations(fromSet, candidate,"LookupList");
      if(lists.size() != 1) {
        throw new GateRuntimeException("Delete candidate: no llId and not a single overlapping LookupList annotation either");
      } else {
        listAnn = lists.get(0);
      }
      */
    } else {
      listAnn = fromSet.get(listId);
    }
    int candId = candidate.getId();
    List<Integer> ids = getIds(listAnn);
    Iterator<Integer> it = ids.iterator();
    boolean removedSomething = false;
    while(it.hasNext()) {
      Integer id = it.next();
      if(id == candId) {
        removedSomething = true;
        it.remove();
        fromSet.remove(candidate);
      }
    }
    if(!removedSomething) {
      throw new GateRuntimeException("Candidate removal did not succeed for candidate "+candidate);
    }
    return 1;
  }
  
  
  
  
  /** 
   * Remove all candidate annotations except those which correspond to the feature maps 
   * in a collection. 
   * 
   * This returns the number of candidates removed.
   * @param fromSet: the annotation set that contains the Lookup annotations
   * @param lookupList: the annotation that contains the list of Lookup ids
   * @param filterCandidates: a collection of FeatureMaps corresponding to candidates
   */
  // TODO check the semantics of FeatureMap.equals()!!!
  public static int keepCandidateAnnsByCollection(AnnotationSet fromSet,
          Annotation lookupList, Collection<FeatureMap> filterCandidates, String... features) {
    List<?> ids = (List<?>) lookupList.getFeatures().get(IDS);
    int nremoved = 0;
    Iterator<?> it = ids.iterator();
    while (it.hasNext()) {
      Object idObj = it.next();
      if (idObj instanceof Integer) {
        int id = (Integer) idObj;
        Annotation theLookup = fromSet.get(id);
        FeatureMap fm = theLookup.getFeatures();
        if (!hasMatchingCandidate(filterCandidates, fm, features)) {
          fromSet.remove(theLookup);
          it.remove();
          nremoved++;
        }
      } else {
        throw new GateRuntimeException("Non-Integer id in the ids feature of " + lookupList);
      }
    }
    return nremoved;
  }

  public static int keepCandidateAnnsByListAnn(AnnotationSet fromSet, 
          Annotation lookupList, Annotation filterAnn, String... features) {
    return keepCandidateAnnsByCollection(fromSet,lookupList,getCandidateList(fromSet,filterAnn),features);
  }
  
  
  /**
   * Return the candidates where the interesting airpedia or interesting dbpedia
   * class match the given class.
   *
   * Since the airpedia and dbpedia interesting classes field could contain
   * several classes separated by a bar, we simply use String.contains to check
   * if there is a match.
   *
   * @param classToMatch
   * @return
   */
  public static List<FeatureMap> filterByInterestingClass(List<FeatureMap> candidates, String classToMatch) {
    List<FeatureMap> filtered = new ArrayList<FeatureMap>();
    for (FeatureMap fm : candidates) {
      String dbpc = (String) fm.get("dbpInterestingClass");
      String airpc = (String) fm.get("airpInterestingClass");
      if ((dbpc != null && dbpc.contains(classToMatch))
              || (airpc != null && airpc.contains(classToMatch))) {
        filtered.add(fm);
      }
    }
    return filtered;
  }

  public static void logListAnn(PrintStream log, String message, AnnotationSet fromSet, Annotation listAnn, String... features) {
    log.println(message);
    Document doc = fromSet.getDocument();
    log.println("  - text="+gate.Utils.cleanStringFor(doc, listAnn));
    FeatureMap fm = listAnn.getFeatures();
    List<?> ids = (List<?>)fm.get(IDS);
    List<FeatureMap> cands = new ArrayList<FeatureMap>();
    int n = 0;
    for(Object idObj : ids) {
      n++;
      if(idObj instanceof Integer) {
        Annotation cand = fromSet.get((Integer)idObj);
        FeatureMap cfm = cand.getFeatures();
        log.println("  - cand."+n+"="+toStringFeatureMap(cfm,features));
      } else {
        throw new GateRuntimeException("Non-Integer id in the ids feature of "+listAnn);
      }
    }
    
  }
  
  /**
   * Create a deep copy of a list annotation at another location.
   * This will create a new list annotation at the same offset and length 
   * as the target annotation and will create for each referenced annotation
   * in the original list annotation a new referenced annotation for the new
   * list annotation. All newly created annotations will be coextensive with
   * the target annotation. 
   * Also, all the feature maps of the new annotations will be clones of 
   * the original feature maps, however the values of the content of these
   * feature maps will not be cloned!
   * NOTE: the annotation type of the new list annotation and the new referenced 
   * annotations will be the same as the existing list and referenced annotation
   * types.
   */
  public static int cloneListAnn(AnnotationSet listSet, Annotation listAnn, 
          AnnotationSet targetSet, Annotation targetAnn) {
    FeatureMap oldListFm = listAnn.getFeatures();
    FeatureMap newListFm = Utils.toFeatureMap(oldListFm);
    List<Integer> newIds = new ArrayList<Integer>();
    newListFm.put(IDS,newIds);
    int newId = Utils.addAnn(targetSet, targetAnn, listAnn.getType(), newListFm);
    // create clones of all the referenced annotations
    @SuppressWarnings("unchecked")
    List<Integer> oldIds = (List<Integer>)oldListFm.get(IDS);
    for(int oldId : oldIds) {
      Annotation oldAnn = listSet.get(oldId);
      FeatureMap newAnnFm = Utils.toFeatureMap(oldAnn.getFeatures());
      int id = Utils.addAnn(targetSet, targetAnn, oldAnn.getType(), newAnnFm);
      newIds.add(id);
    }
    return newId;
  }
  
  
  /**
   * Alternate, improved toString for FeatureMap, sorts by feature name, allows to specify features.
   * 
   * If features are specified, just show them, in the order listed, otherwise
   * use all features found in the feature map, sorted by key.
   * 
   * @param fm
   * @param features 
   */
  public static String toStringFeatureMap(FeatureMap fm, String... features) {
    Set<Object> keyObjs = fm.keySet();
    List<String> keys = null;
    if(features.length > 0) {
      keys = Arrays.asList(features);
    } else {
      keys = new ArrayList<String>();
      for(Object keyObj : keyObjs) {
        if(keyObj instanceof String) {
          keys.add((String)keyObj);
        } else {
          throw new GateRuntimeException("Feature map has a non-String key: "+fm);
        }
      }
      java.util.Collections.sort(keys);
    }
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for(String key : keys) {
      Object val = fm.get(key);
      String value = (val == null) ? "(null)" : val.toString();
      if(!first) {
        sb.append(";");
      } else {
        first = false;
      }
      sb.append(key);
      sb.append("=");
      sb.append(value);
    }
    return sb.toString();
  }
  
  
  // Methods to handle URIs
  
  /** 
   * This will make an attempt to create a DBPedia version 3 dot 9 URI from
   * the resource name, DBP URI or wikipedia title or URL it gets.
   * 
   * See http://wiki.dbpedia.org/URIencoding?show_comments=1 for the documentation
   * about DBPedia 3.9 URIs are encoded.
   * <p>
   * See http://en.wikipedia.org/wiki/Wikipedia:Page_name for the rules on
   * Wikipedia page names
   * <p>
   * See http://wiki.dbpedia.org/Internationalization for internationalization
   * issues. As far as I understand, the dbpedia URIs or some languages 
   * are IRIs while for others (like English) they are URIs. Also other language
   * URIs or IRIs have different, language-specific prefixes. 
   * (Example: for german the prefix is http://de.dbpedia.org/resource/ and
   * IRIs are used) However this is only relevant for the "Localized Datasets"
   * which has information from the language specific Wikipedia which does not
   * have an English equivalent. Things that DO have an English equivalent still
   * have an URI.
   * <p>
   * NOTE: the encoding used for URIs also seems to depend on the format used
   * for downloading: for example the german labels for the localized datasets
   * contains in the example dataset the URI for "Ångström" as
   * http://de.dbpedia.org/resource/Ångström_(Einheit) in TTL format and as
   * http://de.dbpedia.org/resource/\u00C5ngstr\u00F6m_(Einheit) in NT format.
   * Also the labels are encoded differently: "Ångström (Einheit)"@de versus
   * "\u00C5ngstr\u00F6m (Einheit)"@de. This difference also exists for the
   * non-localized datasets, e.g. for http://dbpedia.org/resource/Park_G%C3%BCell 
   * / "Park G\u00FCell"@en in NT format versus Unicode characters in TTL format.
   * NOTE: our Turtle parser will parse the NT format and convert the label
   * to proper Unicode, but will leave the %-encoded characters in the URI!
   * NOTE: characters which are not in the unreserved set (which is essentially
   * ASCII without the special characters and codes) must be %-encoded in URIs
   * but may be included un-encoded in IRIs. That means we are already using
   * IRIs. 
   * <p>
   * Problems: converting from IRI to URI and back need not arrive to the same
   * String, althought the two IRIs should be "semantically equivalent". This is
   * because Unicode can represent e.g. accented characters in different ways.
   * On the other hand both IRIs should get converted to the same URI if done
   * properly: the IRI first needs to get converted using canonical composition 
   * normalization (NFC), then to UTF-8, then each byte percent-encoded.
   * <p>
   * In the preparation stage we use the TTL format, but we need to be able
   * to also deal with URIs that come from other sources and therefore 
   * are in other formats.
   * <p> 
   * This method will first try to identify if the String it gets starts with
   * a known URI or URL prefix and determine from this if it is a DBPedia 
   * resource URI, DBPedia Ontology URI, Wikipedia URL, or just a resoure name
   * or Wikipedia title. 
   * Here is the list if strings currently detected and processed:
   * <ul>
   * <li>It starts with http:// or https:// : in that case it is treated
   * like an URI. First an attempt is made to decode the URI into a string, then
   * the String is re-encoded again using the rules for DBP 3.9. If the URI parsing
   * does not succeed, then an exception is thrown.
   * <li>Otherwise: In this case it is assumed that this
   * string should become the resource identifier of a dbpedia URI. 
   * First any escapes of the form \\uXXXX are resolved. Then we assume that
   * the string is already percent encoded and try to create a URI that 
   * can be later decoded. However if that fails, we assume that we have
   * to skip that part and just percent-encode.
   * </ul>
   * NOTE: we always ignore any userInfo or port parts!
   */
  // NOTE: check if https://code.google.com/p/gdata-java-client/source/browse/trunk/java/src/com/google/gdata/util/common/base/CharEscapers.java
  // is helpful at all.
  // also check http://docs.spring.io/spring/docs/3.0.x/javadoc-api/org/springframework/web/util/UriUtils.html
  public static String recodeForDbp38(String uriString) {
    String ret;
    URI uri = null;
    if(uriString == null || uriString.trim().isEmpty()) {
      return "";
    }
    if(uriString.contains("\\u")) {
      uriString = StringEscapeUtils.unescapeJava(uriString);
    }
    if(uriString.startsWith("http://") || uriString.startsWith("https://")) {
      // First try to parse the string as an URI so that any superfluous 
      // percent-encodings can get decoded later
      // However, we have to already make sure that some characters which
      // would cause and exception are properly percent encoded
      uriString = encodeCharsIn(DBP38Encoded+":",uriString);
      try {
        uri = new URI(uriString);
      } catch(Exception ex) {
        throw new GateRuntimeException("Could not parse URI "+uriString,ex);
      }
      // now use this constructor to-recode only the necessary parts
      try {
        String path = uri.getPath();
        path = path.trim();
        path = path.replaceAll(" +","_");
        uri = new URI(uri.getScheme(),null,uri.getHost(),-1,path,uri.getQuery(),uri.getFragment());
      } catch(Exception ex) {
        throw new GateRuntimeException("Could not re-construct URI: "+uri);
      }
      ret = uri.toString();
    } else {      
      uriString = uriString.trim();
      uriString = uriString.replaceAll(" +", "_");
      String oldUriString = uriString;
      // We need to %-encode colons, otherwise the getPath() method will return
      // null ...
      //uriString = uriString.replaceAll(":","%3A");
      // There seems to be a very odd problem here: we cannot pass on the colon literally,
      // since the uri getPath method will silenty return null, e.g. for 
      // URI("asdf:jkl"). This is not a problem per se, but becomes a problem
      // if the same URI also contains a percent-encoded character: this will
      // not get decoded and thus will be doubly-encoded later.
      // However if we %-encode the colon, there is another problem:
      // for example: 
      try {
        uri = new URI(encodeCharsIn(DBP38Encoded+":",uriString));
        // decode and prepare for minimal percent encoding
        uriString = uri.getPath();
      } catch (URISyntaxException ex) {
        // do nothing: the uriString must already be ready for percent-encoding        
        System.err.println("Problem creating uri from "+uriString);
        ex.printStackTrace(System.err);
      }
      if(uriString == null) {
        System.err.println("Problem: uriString is now null, was "+oldUriString);
        uriString = oldUriString;
      } else {
        uriString = uriString.replaceAll(" +", "_");
      }
      try {
        // the URI constructor is behaving strangely here: if uriString is
        // e.g. "By_7:30" it will throw an exception  "Illegal character in scheme name at index 2"
        // but if we prefix the path with a slash, it will work fine.
        // So we add the slash, only to remove it later again
        uri = new URI(null,null,null,-1,"/"+uriString,null,null);
      } catch(Exception ex) {
        throw new GateRuntimeException("Could not re-construct URI part: "+uriString);
      }
      // remove the slash from the string
      ret = uri.toString().substring(1);
    }
    return ret;
  }
  public static String DBP38Encoded =
          "\"#%<>?[\\]^`{|}";
  public static String DBP37Encoded =
          "!\"#$%'()+;<=>?@[\\]^`{|}~";
  
  private static final String hexChars = "0123456789ABCDEF";
  private static final char[] hex = hexChars.toCharArray();
  
  public static String encodeCharsIn(String charsToReplace, String stringToEncode) {
    StringBuilder sb = new StringBuilder();
    
    char[] chars = stringToEncode.toCharArray();
    for(int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if(charsToReplace.indexOf(c) >= 0) {
        // check if we have a percent character and if it is followed by 
        // at least two hex characters: in that case, do not percent encode
        if(c == '%' && i < (chars.length-2) && 
                hexChars.indexOf(Character.toUpperCase(chars[i+1])) >= 0 && 
                hexChars.indexOf(Character.toUpperCase(chars[i+2])) >= 0) {
          // just append the percent character
          sb.append(c);
        } else {
          sb.append("%");
          if (c <= (char) 0xF) {
            sb.append('0');
            sb.append(hex[c]);
          } else if (c <= (char) 0xFF) {
            sb.append(hex[ c / 16]);
            sb.append(hex[ c % 16]);
          } else {
            throw new GateRuntimeException("This should never happen!");
          }
        }
      } else {
        sb.append(c);
      }
    }
    
    return sb.toString();
  }
  
    
   /// TODO: below this, there are just some not yet implemented 
    /// method declarations for what may be useful to have.
    
    
    /**
     * This will return a sorted list of candidates, sorted by the given feature, 
     * with at most max entries.
     * 
     * This method sorts the candidates by decreasing value of the feature, which
     * is expected to be numeric.  If the feature is not numeric or does not 
     * exist (is null) then the candidate is ignored and guaranteed not to be
     * included in the result.
     * <p>
     * NOTE: this expects all values of the feature to be of one of the types
     * Integer, Long, Double, or Float and it expects all values of the feature
     * for all candidates to be of identical types!
     * 
     * @param fromSet
     * @param lookupList
     * @param featureName 
     * @param maxDifferent: the maximum number of different values of the 
     * feature in the result set. Rather than always taking at most N values,
     * this will make sure that elements which all have the same best value
     * are counted as one. 
     */
    public static List<FeatureMap> sortCandidatesDescOn(List<FeatureMap> candidates, String featureName, int maxDifferent) {
      return sortCandidatesDescOn(candidates,featureName,maxDifferent,false);
    }
    /**
     * This will return a sorted list of candidates, sorted by the given feature, 
     * with at most max entries.
     * 
     * This method sorts the candidates by decreasing value of the feature, which
     * is expected to be numeric.  If the feature is not numeric or does not 
     * exist (is null) then the parameter includeNull determines how the candidate
     * is processed: if the parameter is true, it will treated as if the feature 
     * had a value less than all other values, otherwise the candidate is ignored.
     * <p>
     * NOTE: this expects all values of the feature to be of one of the types
     * Integer, Long, Double, or Float and it expects all values of the feature
     * for all candidates to be of identical types!
     * 
     * 
     * @param fromSet
     * @param lookupList
     * @param featureName 
     * @param maxDifferent: the maximum number of different values of the 
     * feature in the result set. Rather than always taking at most N values,
     * this will make sure that elements which all have the same best value
     * are counted as one. 
     * @param includeNull: if true, keep candidates where the feature is null
     */
    public static List<FeatureMap> sortCandidatesDescOn(List<FeatureMap> candidates, String featureName, int maxDifferent, boolean includeNull) {
      // TODO: it would be more efficient to use a bounded priority queue here 
      // but Java does not have one!
      PriorityQueue<FeatureMap> queue = new PriorityQueue<FeatureMap>(candidates.size(),makeNumDescFeatureComparator(featureName));
      for(FeatureMap fm : candidates) {
        Object f = fm.get(featureName);
        if(!includeNull && f == null) {
          continue;
        }
        if(f == null || f instanceof Number) {
          queue.add(fm);
        }
      }
      List<FeatureMap> ret = new LinkedList<FeatureMap>();
      FeatureMap el = null;
      int ndiff = 0;
      Object lastValue = new Object();
      while(ndiff <= maxDifferent && (el=queue.poll()) != null) {
        Object thisValue = el.get(featureName);
          if((thisValue == null && lastValue != null) || 
             (thisValue != null && !thisValue.equals(lastValue))
            ) {
            ndiff++;
            lastValue = thisValue;
          }
          if(ndiff <= maxDifferent) {
            ret.add(el);
          }
      }
      return ret;
    }
    
    public static Comparator<FeatureMap> makeNumDescFeatureComparator(String featureName) {
      final String feat = featureName;
      return new Comparator<FeatureMap>() {
        @Override
        public int compare(FeatureMap o1, FeatureMap o2) {
          Object f1Obj = o1.get(feat);
          Object f2Obj = o2.get(feat);
          if(f1Obj != null && f2Obj != null) {
            if(f1Obj instanceof Integer) {
              Integer f1 = (Integer)f1Obj;
              Integer f2 = (Integer)f2Obj;
              return f2.compareTo(f1);
            } else if(f1Obj instanceof Long) {
              Long f1 = (Long)f1Obj;
              Long f2 = (Long)f2Obj;
              return f2.compareTo(f1);
            } else if(f1Obj instanceof Float) {
              Float f1 = (Float)f1Obj;
              Float f2 = (Float)f2Obj;
              return f2.compareTo(f1);
            } else if(f1Obj instanceof Double) {
              Double f1 = (Double)f1Obj;
              Double f2 = (Double)f2Obj;
              return f2.compareTo(f1);              
            } else {
              throw new GateRuntimeException("Feature value is nor Integer nor Float: "+f1Obj+"/"+f1Obj.getClass());
            }
          } else if(f1Obj == null && f2Obj != null) {
            return 1;
          } else if(f1Obj != null && f2Obj == null) {
            return -1;
          } else {
            return 0;
          }
        }
      };      
    }

    public static void addRankFeatureFloat(String featureToRank, Annotation lookuplist,
    		AnnotationSet annotationSet, boolean includeAbsoluteRankFeature){
    	AnnotationSet lus = getCandidateAnns(annotationSet, lookuplist);

    	Iterator<Annotation> it = lus.iterator();
    	LinkedList<Annotation> lis = new LinkedList<Annotation>();

		 if(it.hasNext()){
		  Annotation first = it.next();
		  lis.add(first);
		 }

    	 while(it.hasNext()){
    	  Annotation lu = it.next();

    	  //Insert this lookup into the list at the correct point

    	  float feat = 0.0F;
    	  if(lu.getFeatures().get(featureToRank)!=null){
    	   feat = ((Float)lu.getFeatures().get(featureToRank)).floatValue();
    	  }

    	  boolean islessthan = true;
    	  int pos = 0;
    	  while(pos<lis.size() && islessthan==true){
    	   Annotation thislistelement = lis.get(pos);
    	   float thislistfeat = 0.0F;
    	   if(thislistelement.getFeatures().get(featureToRank)!=null){
    	    thislistfeat = ((Float)thislistelement.getFeatures().get(featureToRank)).floatValue();
    	   }
    	   if(feat>thislistfeat){
    	    islessthan = false;
    	   } else {
    	    pos++;
    	   }
    	  }

    	  //So now we have found the element that the current lookup
    	  //needs to precede, so we insert it in that position

    	  lis.add(pos, lu);
    	 }

    	 //Now we have an ordered list. So write the rank feature on.
    	 int listlen = lis.size();

    	 float prevfeat = -1.0F;
    	 float prevrankfeat = -1.0F;
    	 int prevAbsoluteRank = -1;

    	 for(int i=0;i<listlen;i++){
    	  Annotation thislistelement = lis.get(i);

    	  float thisfeat = 0.0F;
    	  if(thislistelement.getFeatures().get(featureToRank)!=null){
    	   thisfeat = ((Float)thislistelement.getFeatures().get(featureToRank)).floatValue();
    	  }

    	  float rankFeat = 0.0F;
    	  int absoluteRank = -1;
    	  if(thisfeat==prevfeat){
    	   rankFeat = prevrankfeat;
    	   absoluteRank = prevAbsoluteRank;
    	  } else if(thisfeat==0){
    	   rankFeat = 0.0F;
    	   absoluteRank = 0;
    	  } else {
    	   rankFeat = ((float)listlen-i)/(float)listlen;
    	   absoluteRank = i+1;
    	  }

    	  lis.get(i).getFeatures().put(featureToRank + "Rank", rankFeat);
    	  if(includeAbsoluteRankFeature){
    		  lis.get(i).getFeatures().put(featureToRank + "AbsoluteRank", absoluteRank);
    	  }
    	  prevrankfeat = rankFeat;
    	  prevfeat = thisfeat;
    	  prevAbsoluteRank = absoluteRank;
    	 }
    }
    
    
    public static void addRankFeatureDouble(String featureToRank, Annotation lookuplist,
    		AnnotationSet annotationSet, boolean includeAbsoluteRankFeature){
    	AnnotationSet lus = getCandidateAnns(annotationSet, lookuplist);

    	Iterator<Annotation> it = lus.iterator();
    	LinkedList<Annotation> lis = new LinkedList<Annotation>();

		 if(it.hasNext()){
		  Annotation first = it.next();
		  lis.add(first);
		 }

    	 while(it.hasNext()){
    	  Annotation lu = it.next();

    	  //Insert this lookup into the list at the correct point

    	  float feat = 0.0F;
    	  if(lu.getFeatures().get(featureToRank)!=null){
    	   feat = ((Double)lu.getFeatures().get(featureToRank)).floatValue();
    	  }

    	  boolean islessthan = true;
    	  int pos = 0;
    	  while(pos<lis.size() && islessthan==true){
    	   Annotation thislistelement = lis.get(pos);
    	   float thislistfeat = 0.0F;
    	   if(thislistelement.getFeatures().get(featureToRank)!=null){
    	    thislistfeat = ((Double)thislistelement.getFeatures().get(featureToRank)).floatValue();
    	   }
    	   if(feat>thislistfeat){
    	    islessthan = false;
    	   } else {
    	    pos++;
    	   }
    	  }

    	  //So now we have found the element that the current lookup
    	  //needs to precede, so we insert it in that position

    	  lis.add(pos, lu);
    	 }

    	 //Now we have an ordered list. So write the rank feature on.
    	 int listlen = lis.size();

    	 float prevfeat = -1.0F;
    	 float prevrankfeat = -1.0F;
    	 int prevAbsoluteRank = -1;

    	 for(int i=0;i<listlen;i++){
    	  Annotation thislistelement = lis.get(i);

    	  float thisfeat = 0.0F;
    	  if(thislistelement.getFeatures().get(featureToRank)!=null){
    	   thisfeat = ((Double)thislistelement.getFeatures().get(featureToRank)).floatValue();
    	  }

    	  float rankFeat = 0.0F;
    	  int absoluteRank = -1;
    	  if(thisfeat==prevfeat){
    	   rankFeat = prevrankfeat;
    	   absoluteRank = prevAbsoluteRank;
    	  } else if(thisfeat==0){
    	   rankFeat = 0.0F;
    	   absoluteRank = 0;
    	  } else {
    	   rankFeat = ((float)listlen-i)/(float)listlen;
    	   absoluteRank = i+1;
    	  }

    	  lis.get(i).getFeatures().put(featureToRank + "Rank", rankFeat);
    	  if(includeAbsoluteRankFeature){
    		  lis.get(i).getFeatures().put(featureToRank + "AbsoluteRank", absoluteRank);
    	  }
    	  prevrankfeat = rankFeat;
    	  prevfeat = thisfeat;
    	  prevAbsoluteRank = absoluteRank;
    	 }
    }
    

    public static void addRankFeatureInt(String featureToRank, Annotation lookuplist,
    		AnnotationSet annotationSet, boolean includeAbsoluteRankFeature){
    	AnnotationSet lus = getCandidateAnns(annotationSet, lookuplist);

    	Iterator<Annotation> it = lus.iterator();
    	LinkedList<Annotation> lis = new LinkedList<Annotation>();

		 if(it.hasNext()){
		  Annotation first = it.next();
		  lis.add(first);
		 }

    	 while(it.hasNext()){
    	  Annotation lu = it.next();

    	  //Insert this lookup into the list at the correct point

    	  int feat = 0;
    	  if(lu.getFeatures().get(featureToRank)!=null){
    	   feat = ((Integer)lu.getFeatures().get(featureToRank)).intValue();
    	  }

    	  boolean islessthan = true;
    	  int pos = 0;
    	  while(pos<lis.size() && islessthan==true){
    	   Annotation thislistelement = lis.get(pos);
    	   int thislistfeat = 0;
    	   if(thislistelement.getFeatures().get(featureToRank)!=null){
    	    thislistfeat = ((Integer)thislistelement.getFeatures().get(featureToRank)).intValue();
    	   }
    	   if(feat>thislistfeat){
    	    islessthan = false;
    	   } else {
    	    pos++;
    	   }
    	  }

    	  //So now we have found the element that the current lookup
    	  //needs to precede, so we insert it in that position

    	  lis.add(pos, lu);
    	 }

    	 //Now we have an ordered list. So write the rank feature on.
    	 int listlen = lis.size();

    	 int prevfeat = -1;
    	 float prevrankfeat = -1.0F;
    	 int prevAbsoluteRank = -1;

    	 for(int i=0;i<listlen;i++){
    	  Annotation thislistelement = lis.get(i);

    	  int thisfeat = 0;
    	  if(thislistelement.getFeatures().get(featureToRank)!=null){
    	   thisfeat = ((Integer)thislistelement.getFeatures().get(featureToRank)).intValue();
    	  }

    	  float rankFeat = 0.0F;
    	  int absoluteRank = -1;
    	  if(thisfeat==prevfeat){
    	   rankFeat = prevrankfeat;
    	   absoluteRank = prevAbsoluteRank;
    	  } else if(thisfeat==0){
    	   rankFeat = 0.0F;
    	   absoluteRank = 0;
    	  } else {
    	   rankFeat = ((float)listlen-i)/(float)listlen;
    	   absoluteRank = i+1;
    	  }

    	  lis.get(i).getFeatures().put(featureToRank + "Rank", rankFeat);
    	  if(includeAbsoluteRankFeature){
    		  lis.get(i).getFeatures().put(featureToRank + "AbsoluteRank", absoluteRank);
    	  }
    	  prevrankfeat = rankFeat;
    	  prevfeat = thisfeat;
    	  prevAbsoluteRank = absoluteRank;
    	 }
    }

    // ********************************************************
    // helper methods
    
    /**
     * Move all list annotations of the given type from one set to another set.
     * 
     * This expects all annotations in the fromSet with the given type to be list annotations
     * and moves those annotations together with their referenced annotations to the given toSet.
     * Both fromSet and toSet must be mutable annotation sets.
     * <p>
     * NOTE: this method will keep the annotation ids of both list and referenced
     * annotations intact.
     * 
     * @param fromSet
     * @param toSet
     * @param type 
     */
    public static void moveListAnns(AnnotationSet fromSet, AnnotationSet toSet, String type) {
      // TODO
    }
    
    /**
     * Move all list annotations contained in the given which set from one set to another.
     * 
     * This expects all annotations in the which set to be list annotations and moves the annotations
     * in the fromSet that correspond to the annotations in the which set to the toSet.
     * <p>
     * NOTE: this method will keep the annotation ids of both list and referenced annotations intact
     * <p>
     * NOTE: this method expects all annotations in the which set to actually be present in the fromSet
     * and also expects both the fromSet and toSet to be mutable.
     * 
     * @param fromSet
     * @param toSet
     * @param which 
     */
    public static void moveListAnns(AnnotationSet fromSet, AnnotationSet toSet, Collection<Annotation> which) {
      // TODO
    }
    
    /**
     * Copy all list annotations of the given type from one set to another set.
     * 
     * This expects all annotations in the fromSet with the given type to be list annotations
     * and copies those annotations together with their referenced annotations to the given toSet.
     * Both fromSet and toSet must be mutable annotation sets.
     * <p>
     * NOTE: this method will change the ids both in the list annotation and the referenced 
     * annotation.
     * 
     * @param fromSet
     * @param toSet
     * @param type 
     */
    public static void copyListAnns(AnnotationSet fromSet, AnnotationSet toSet, String type) {
      AnnotationSet listAnns = fromSet.get(type);
      for(Annotation listAnn : listAnns) {
        List<Integer> ids = getIds(listAnn);
        List<Integer> newIds = new ArrayList<Integer>();
        FeatureMap lfm = Utils.toFeatureMap(listAnn.getFeatures());
        lfm.put(IDS, newIds);
        int newLlId = Utils.addAnn(toSet,listAnn,listAnn.getType(),lfm);
        for(Integer id : ids) {
          Annotation ann = fromSet.get(id);
          FeatureMap fm = Utils.toFeatureMap(ann.getFeatures());
          // override the list annotation id with the one for the copy
          fm.put(LLID,newLlId);
          int newId = Utils.addAnn(toSet,ann,ann.getType(),Utils.toFeatureMap(fm));
          newIds.add(newId);
        } // for ids        
      } // for listAnns
    }
    
    public static void copyListAnns(AnnotationSet fromSet, AnnotationSet toSet, AnnotationSet which) {
      // TODO
    }
    
    public static List<Integer> getIds(Annotation listAnn) {
      Object idsObj = listAnn.getFeatures().get(IDS);
      if(idsObj == null) {
        throw new GateRuntimeException("List annotation does not have an ids feature: "+listAnn);
      }
      if(idsObj instanceof List) {
        @SuppressWarnings("unchecked")
        List<Integer> ret = (List<Integer>)idsObj;
        return ret;
      } else {
        throw new GateRuntimeException("List annotation has ids feature which is not a list: "+listAnn);
      }
    }
    
} // class LodieUtils
