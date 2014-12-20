
import gate.trendminer.lodie.utils.LodieUtils;
import gate.*;
import gate.util.GateRuntimeException;
import com.jpetrak.gate.jdbclookup.JdbcLR;
import java.net.URL;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

// Make an attempt to fix the "inst" feature (so far only that one!) of 
// the annotations in InputAS, having type as specified as script parameter "type"
// If the resource1 parameter is set to a Jdbc database LR it is assumed to 
// contain the redirects table to substitute URIs with their redirected values.
// NOTE: only non-empty insta feature values will be processed.
// 

Connection connection = null; // if null, do not try to redirect
String sql = "SELECT touri FROM redirects WHERE fromuri = ?"; 
PreparedStatement pSt;
String type = "";

@Override
public void init() {
  // if we got a resource, try to open a connection
  if(resource1 != null) {
    JdbcLR db = (JdbcLR)resource1;
    connection = db.getConnection(); 
    if(connection == null) {
      throw new GateRuntimeException("Connection to the db is null for "+db);
    } else {
      try {
        pSt = connection.prepareStatement(sql);
      } catch (Exception ex) {
        throw new GateRuntimeException("Problem preparing statement",ex);
      }
    }
  }
  // make sure the type parameter is set and store it
  type = (String)parms.get("type");
  if(type == null) { 
    throw new GateRuntimeException("Script parameter 'type' is missing!");
  }
}

@Override
public void cleanup() {
  if(connection != null) {
    try {
      connection.close();
    } catch(Exception ex) {
      System.err.println("Could not close JDBC connection");
      ex.printStackTrace(System.err);
    }
  }
}

private static final Pattern uriPrefix = Pattern.compile("http://(?:[a-z]+\\.)?dbpedia.org/resource/.*");

@Override
public void execute() {
  // get the annotations to process
  AnnotationSet toProcess = inputAS.get(type);
  Set<Annotation> toRemove = new HashSet<Annotation>();
  for(Annotation ann : toProcess) {
    FeatureMap fm = ann.getFeatures();
    String value = (String)fm.get("inst");
    // only do anything at all if the inst value exists and is non-empty
    if(value != null && !value.isEmpty()) {
      String oldValue = value;
      // sometimes the wrong base URI is used (page instead of resource)
      if(value.startsWith("http://dbpedia.org/page/")) {
        logger.info("Have a ..pagep.. uri, replacing with ..resource..: "+value);
        value = value.replaceAll("http://dbpedia.org/page/","http://dbpedia.org/resource/");        
      }
      if(!uriPrefix.matcher(value).matches()) {
        System.err.println(doc.getName()+"URI does not seem to be a DBPedia URI/IRI, removing annotation: "+ann);
        toRemove.add(ann);
        continue;
      }
      try {
        value = LodieUtils.recodeForDbp38(value);
      } catch (Exception ex) {
        System.err.println("FixUris.java Document "+doc.getName()+" could not recode: "+value);
        ex.printStackTrace(System.err);
      }
      // if we have a connection, try to find the canonical redirect for the URI
      // Since the redirect DB has full URIs, we need to temporarily create a full URI of anything
      // that is not a full URI first, do the redirect and then remove the base URI again
      if(connection != null) {
        String toRedirect = value;
        boolean weAddedBaseUri = false;
        if(!toRedirect.startsWith("http://dbpedia.org/resource/")) {
          toRedirect = "http://dbpedia.org/resource/"+toRedirect;
          weAddedBaseUri = true;
        }
        String redirected = findRedirect(toRedirect);
        if(weAddedBaseUri) {
          // remove the base URI again
          redirected = redirected.substring("http://dbpedia.org/resource/".length());
        }
        value = redirected;
      }
      fm.put("inst",value);
      if(!oldValue.equals(value)) {
        logger.info("Found a replacement for "+oldValue+": "+value);
      }
    }
  }
  inputAS.removeAll(toRemove);
}

private String findRedirect(String what) {
  try {
    pSt.setString(1,what);
    ResultSet rs = pSt.executeQuery();
    if(rs.next()) {
      String redirected = rs.getString("toUri");
      return redirected;
    } else {
      return what;
    }
  } catch(Exception ex) {
    throw new GateRuntimeException("Could not re-direct "+what,ex);
  }
}


