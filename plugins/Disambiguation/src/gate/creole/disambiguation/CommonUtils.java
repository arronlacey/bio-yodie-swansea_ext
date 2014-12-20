package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.FeatureMap;
import gate.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommonUtils {
  private static String[][] accentedArray = new String[][]{{"á", "a"},
    {"Á", "A"}, {"à", "a"}, {"À", "A"}, {"ă", "a"}, {"Ă", "A"}, {"â", "a"},
    {"Â", "A"}, {"å", "a"}, {"Å", "A"}, {"ã", "a"}, {"Ã", "A"}, {"ą", "a"},
    {"Ą", "A"}, {"ā", "a"}, {"Ā", "A"}, {"ä", "ae"}, {"Ä", "AE"}, {"æ", "ae"},
    {"Æ", "AE"}, {"ḃ", "b"}, {"Ḃ", "B"}, {"ć", "c"}, {"Ć", "C"}, {"ĉ", "c"},
    {"Ĉ", "C"}, {"č", "c"}, {"Č", "C"}, {"ċ", "c"}, {"Ċ", "C"}, {"ç", "c"},
    {"Ç", "C"}, {"ď", "d"}, {"Ď", "D"}, {"ḋ", "d"}, {"Ḋ", "D"}, {"đ", "d"},
    {"Đ", "D"}, {"ð", "dh"}, {"Ð", "Dh"}, {"é", "e"}, {"É", "E"}, {"è", "e"},
    {"È", "E"}, {"ĕ", "e"}, {"Ĕ", "E"}, {"ê", "e"}, {"Ê", "E"}, {"ě", "e"},
    {"Ě", "E"}, {"ë", "e"}, {"Ë", "E"}, {"ė", "e"}, {"Ė", "E"}, {"ę", "e"},
    {"Ę", "E"}, {"ē", "e"}, {"Ē", "E"}, {"ḟ", "f"}, {"Ḟ", "F"}, {"ƒ", "f"},
    {"Ƒ", "F"}, {"ğ", "g"}, {"Ğ", "G"}, {"ĝ", "g"}, {"Ĝ", "G"}, {"ġ", "g"},
    {"Ġ", "G"}, {"ģ", "g"}, {"Ģ", "G"}, {"ĥ", "h"}, {"Ĥ", "H"}, {"ħ", "h"},
    {"Ħ", "H"}, {"í", "i"}, {"Í", "I"}, {"ì", "i"}, {"Ì", "I"}, {"î", "i"},
    {"Î", "I"}, {"ï", "i"}, {"Ï", "I"}, {"ĩ", "i"}, {"Ĩ", "I"}, {"į", "i"},
    {"Į", "I"}, {"ī", "i"}, {"Ī", "I"}, {"ĵ", "j"}, {"Ĵ", "J"}, {"ķ", "k"},
    {"Ķ", "K"}, {"ĺ", "l"}, {"Ĺ", "L"}, {"ľ", "l"}, {"Ľ", "L"}, {"ļ", "l"},
    {"Ļ", "L"}, {"ł", "l"}, {"Ł", "L"}, {"ṁ", "m"}, {"Ṁ", "M"}, {"ń", "n"},
    {"Ń", "N"}, {"ň", "n"}, {"Ň", "N"}, {"ñ", "n"}, {"Ñ", "N"}, {"ņ", "n"},
    {"Ņ", "N"}, {"ó", "o"}, {"Ó", "O"}, {"ò", "o"}, {"Ò", "O"}, {"ô", "o"},
    {"Ô", "O"}, {"ő", "o"}, {"Ő", "O"}, {"õ", "o"}, {"Õ", "O"}, {"ø", "oe"},
    {"Ø", "OE"}, {"ō", "o"}, {"Ō", "O"}, {"ơ", "o"}, {"Ơ", "O"}, {"ö", "oe"},
    {"Ö", "OE"}, {"ṗ", "p"}, {"Ṗ", "P"}, {"ŕ", "r"}, {"Ŕ", "R"}, {"ř", "r"},
    {"Ř", "R"}, {"ŗ", "r"}, {"Ŗ", "R"}, {"ś", "s"}, {"Ś", "S"}, {"ŝ", "s"},
    {"Ŝ", "S"}, {"š", "s"}, {"Š", "S"}, {"ṡ", "s"}, {"Ṡ", "S"}, {"ş", "s"},
    {"Ş", "S"}, {"ș", "s"}, {"Ș", "S"}, {"ß", "SS"}, {"ť", "t"}, {"Ť", "T"},
    {"ṫ", "t"}, {"Ṫ", "T"}, {"ţ", "t"}, {"Ţ", "T"}, {"ț", "t"}, {"Ț", "T"},
    {"ŧ", "t"}, {"Ŧ", "T"}, {"ú", "u"}, {"Ú", "U"}, {"ù", "u"}, {"Ù", "U"},
    {"ŭ", "u"}, {"Ŭ", "U"}, {"û", "u"}, {"Û", "U"}, {"ů", "u"}, {"Ů", "U"},
    {"ű", "u"}, {"Ű", "U"}, {"ũ", "u"}, {"Ũ", "U"}, {"ų", "u"}, {"Ų", "U"},
    {"ū", "u"}, {"Ū", "U"}, {"ư", "u"}, {"Ư", "U"}, {"ü", "ue"}, {"Ü", "UE"},
    {"ẃ", "w"}, {"Ẃ", "W"}, {"ẁ", "w"}, {"Ẁ", "W"}, {"ŵ", "w"}, {"Ŵ", "W"},
    {"ẅ", "w"}, {"Ẅ", "W"}, {"ý", "y"}, {"Ý", "Y"}, {"ỳ", "y"}, {"Ỳ", "Y"},
    {"ŷ", "y"}, {"Ŷ", "Y"}, {"ÿ", "y"}, {"Ÿ", "Y"}, {"ź", "z"}, {"Ź", "Z"},
    {"ž", "z"}, {"Ž", "Z"}, {"ż", "z"}, {"Ż", "Z"}, {"þ", "th"}, {"Þ", "Th"},
    {"µ", "u"}, {"а", "a"}, {"А", "a"}, {"б", "b"}, {"Б", "b"}, {"в", "v"},
    {"В", "v"}, {"г", "g"}, {"Г", "g"}, {"д", "d"}, {"Д", "d"}, {"е", "e"},
    {"Е", "e"}, {"ё", "e"}, {"Ё", "e"}, {"ж", "zh"}, {"Ж", "zh"}, {"з", "z"},
    {"З", "z"}, {"и", "i"}, {"И", "i"}, {"й", "j"}, {"Й", "j"}, {"к", "k"},
    {"К", "k"}, {"л", "l"}, {"Л", "l"}, {"м", "m"}, {"М", "m"}, {"н", "n"},
    {"Н", "n"}, {"о", "o"}, {"О", "o"}, {"п", "p"}, {"П", "p"}, {"р", "r"},
    {"Р", "r"}, {"с", "s"}, {"С", "s"}, {"т", "t"}, {"Т", "t"}, {"у", "u"},
    {"У", "u"}, {"ф", "f"}, {"Ф", "f"}, {"х", "h"}, {"Х", "h"}, {"ц", "c"},
    {"Ц", "c"}, {"ч", "ch"}, {"Ч", "ch"}, {"ш", "sh"}, {"Ш", "sh"},
    {"щ", "sch"}, {"Щ", "sch"}, {"ъ", ""}, {"Ъ", ""}, {"ы", "y"}, {"Ы", "y"},
    {"ь", ""}, {"Ь", ""}, {"э", "e"}, {"Э", "e"}, {"ю", "ju"}, {"Ю", "ju"},
    {"я", "ja"}, {"Я", "ja"}};

  public static int UPPER = 0;
  public static int LOWER = 1;
  public static int UPPERINITIAL = 2;
  public static int OTHER = 3;
  public static int EMPTY = 4;
  
  public static final Map<String, String> nsMap = new HashMap<String, String>();
  static{
	  nsMap.put("","http://dbpedia.org/resource/");
	  nsMap.put("dbpo:","http://dbpedia.org/ontology/");
	  nsMap.put("owl:","http://www.w3.org/2002/07/owl#");
	  nsMap.put("dbpp:","http://dbpedia.org/property/");
	  nsMap.put("foaf:","http://xmlns.com/foaf/0.1/");
	  nsMap.put("rdf:","http://www.w3.org/1999/02/22-rdf-syntax-ns#");
	  nsMap.put("rdfs:","http://www.w3.org/2000/01/rdf-schema#");
	  nsMap.put("skos:","http://www.w3.org/2004/02/skos/core#");
	  nsMap.put("yago:","http://mpii.de/yago/resource/");
  }
  
  /**
   * for each annotation id find the list of context annotations by looking at
   * the sentences that contain coreferenced matches
   * 
   * @return
   */
  public static Map<Integer, List<Annotation>> findExtendedContextForCoreferencedAnnotations(Document document,
                                                                                             AnnotationSet inputAS,
                                                                                             int contextWindowInNumOfTokens) {
    Set<String> annotTypesSet = new HashSet<String>();
    annotTypesSet.add("Person");
    annotTypesSet.add("Location");
    annotTypesSet.add("Organization");
    // to return
    Map<Integer, List<Annotation>> toReturn =
      new HashMap<Integer, List<Annotation>>();
    AnnotationSet nerSet = inputAS.get(annotTypesSet);
    for(Annotation tmpAnn : nerSet) {
      toReturn.put(
        tmpAnn.getId(),
        getContextAnnotations(tmpAnn, inputAS, annotTypesSet,
          contextWindowInNumOfTokens, true));
    }
    return toReturn;
  }

  public static Set<Annotation> getContainedCoveringAnnots(Annotation a,
                                                           AnnotationSet inputAS,
                                                           List<String> annotationTypes) {
    Set<Annotation> toReturn = new HashSet<Annotation>();
    for(String aType : annotationTypes) {
      toReturn.addAll(inputAS.getCovering(aType, Utils.start(a), Utils.end(a)));
      toReturn.addAll(inputAS.getContained(Utils.start(a), Utils.end(a)).get(
        aType));
    }
    if(annotationTypes.contains(a.getType())) toReturn.add(a);
    return toReturn;
  }

  public static String normaliseAccentedChars(String s) {
    if(s == null) return s;
    String toReturn =
      s.toLowerCase().replaceAll("[\\s\\p{Punct}]+", " ").trim();
    for(int i = 0; i < accentedArray.length; i++) {
      toReturn = toReturn.replaceAll(accentedArray[i][0], accentedArray[i][1]);
    }
    return toReturn;
  }

  public static List<Annotation> getContextAnnotations(Annotation ann,
                                                       AnnotationSet inputAS,
                                                       String type,
                                                       int windowsInNumOfTokens,
                                                       boolean removeOverlapping) {
    Set<String> annotationTypes = new HashSet<String>();
    annotationTypes.add(type);
    return getContextAnnotations(ann, inputAS, annotationTypes,
      windowsInNumOfTokens, removeOverlapping);
  }

  /**
   * @param ann
   *          context of what annotation
   * @param inputAS
   *          inputAS
   * @param type
   *          type of the annotations to return in the context
   * @param windowInNumOfTokens
   *          window in number of tokens for the context annotations
   * @return
   */
  public static List<Annotation> getContextAnnotations(Annotation ann,
                                                       AnnotationSet inputAS,
                                                       Set<String> types,
                                                       int windowsInNumOfTokens,
                                                       boolean removeOverlapping) {
    List<Annotation> tokensList = Utils.inDocumentOrder(inputAS.get("Token"));
    List<Annotation> limitedTokenList =
      Utils.inDocumentOrder(inputAS.getContained(Utils.start(ann),
        Utils.end(ann)).get("Token"));
    if(limitedTokenList.size() == 0) {
      limitedTokenList =
        Utils.inDocumentOrder(inputAS.getCovering("Token", Utils.start(ann),
          Utils.end(ann)));
    }
    int firstToken = tokensList.indexOf(limitedTokenList.get(0));
    int lastToken =
      tokensList.indexOf(limitedTokenList.get(limitedTokenList.size() - 1));
    int lcToken = firstToken - windowsInNumOfTokens;
    int rcToken = lastToken + windowsInNumOfTokens;
    if(lcToken < 0) lcToken = 0;
    if(rcToken > tokensList.size() - 1) rcToken = tokensList.size() - 1;
    long startOffset = Utils.start(tokensList.get(lcToken));
    long endOffset = Utils.end(tokensList.get(rcToken));
    List<Annotation> toReturn =
      Utils.inDocumentOrder(inputAS.getContained(startOffset, endOffset).get(
        types));
    if(removeOverlapping) {
      for(int i = 0; i < toReturn.size(); i++) {
        Annotation a = toReturn.get(i);
        if(a.overlaps(ann)) {
          toReturn.remove(i);
          i--;
        }
      }
    }
    return toReturn;
  }

  /**
   * filter annotations
   * 
   * @param annots
   * @param featureName
   * @param featureValues
   * @return
   */
  public static List<Annotation> filterAnnotations(List<Annotation> annots,
                                                   String featureName,
                                                   Set<String> featureValues) {
    List<Annotation> toReturn = new ArrayList<Annotation>();
    for(Annotation a : annots) {
      FeatureMap f = a.getFeatures();
      String val = null;
      if(f.containsKey(featureName)) {
        val = f.get(featureName).toString();
      }
      if(val == null || !featureValues.contains(val)) {
        continue;
      }
      toReturn.add(a);
    }
    return toReturn;
  }

  public static int caseOfString(String str){
	  char[] chars = str.toCharArray();
	  if(chars.length==0){
		  return CommonUtils.EMPTY;
	  } else {
		  boolean firstisupper = Character.isUpperCase(chars[0]);
		  
		  int uppercount = 0;
		  int lowercount = 0;
		  for(int i=1;i<chars.length;i++){
			  if(Character.isLowerCase(chars[i])){
				  lowercount++;
			  } else if(Character.isUpperCase(chars[i])){
				  uppercount++;
			  }
		  }
		  
		  if(firstisupper && lowercount==0){
			  return CommonUtils.UPPER;
		  } else if(firstisupper && uppercount==0){
			  return CommonUtils.UPPERINITIAL;
		  } else if(!firstisupper && uppercount==0){
			  return CommonUtils.LOWER;
		  } else {
			  return CommonUtils.OTHER;
		  }
	  }
  }
  
  public static float match(String toMatch, String toMatchAgainst,
                            boolean normaliseAccentedChars) {
    if(normaliseAccentedChars) {
      toMatch = CommonUtils.normaliseAccentedChars(toMatch);
      toMatchAgainst = CommonUtils.normaliseAccentedChars(toMatchAgainst);
    }
    String[] swords = toMatch.split("[_ \\p{Punct}]+");
    String[] twords = toMatchAgainst.split("[_ \\p{Punct}]+");

    List<String> slist = new ArrayList<String>();
    List<String> tlist = new ArrayList<String>();
    for(String s : swords) {
      if(s.trim().length() == 0) continue;
      slist.add(s.trim());
    }
    for(String s : twords) {
      if(s.trim().length() == 0) continue;
      tlist.add(s.trim());
    }
    swords = new String[slist.size()];
    twords = new String[tlist.size()];
    for(int i = 0; i < slist.size(); i++)
      swords[i] = slist.get(i);
    for(int i = 0; i < tlist.size(); i++)
      twords[i] = tlist.get(i);
    float bestScore = 0.0F;
    if(swords.length == 1 && swords[0].length() == twords.length) {
      int same = 0;
      for(int i = 0; i < swords[0].length(); i++) {
        if(Character.toUpperCase(twords[i].charAt(0)) == Character
          .toUpperCase(swords[0].charAt(i))) {
          same++;
        }
      }
      bestScore = (float)same / (swords[0].length() + twords.length);
    } else if(twords.length == 1 && twords[0].length() == swords.length) {
      int same = 0;
      for(int i = 0; i < twords[0].length(); i++) {
        if(Character.toUpperCase(swords[i].charAt(0)) == Character
          .toUpperCase(twords[0].charAt(i))) {
          same++;
        }
      }
      bestScore = (float)same / (twords[0].length() + swords.length);
    }
    Set<String> sset = new HashSet<String>();
    Set<String> tset = new HashSet<String>();
    for(String s : swords) {
      sset.add(s.length() < 4 ? s : s.substring(0, 4));
    }
    for(String s : twords) {
      tset.add(s.length() < 4 ? s : s.substring(0, 4));
    }
    int ssz = sset.size();
    int tsz = tset.size();
    tset.retainAll(sset);
    float wordScore = 0.0F;
    if(tset.size() == 0)
      wordScore = 0.0F;
    else wordScore = (float)tset.size() / (ssz + tsz);
    if(wordScore > bestScore) {
      bestScore = wordScore;
    }
    return bestScore;
  }
}
