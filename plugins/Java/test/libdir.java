import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

// Demonstration for using a libDir: if libDir is set to a directory that
// contains the H2 jar, this code should compile!

@Override
public void execute() {
  System.out.println("Running for document "+doc.getName()+" started in copy "+duplicationId);
  System.out.println("Running for document "+doc.getName()+" finished in copy "+duplicationId);
}

@Override
public void initPr() {
  System.out.println("Running initPr() started "+this.getClass()+" in copy "+duplicationId);
  try {
    // NOTE: this requires that the H2 is on the classpath!!!
    Class.forName("org.h2.Driver");
  } catch(Exception ex) {
    System.err.println("Could not load the H2 driver");
  }
  System.out.println("Running initPr() finished "+this.getClass()+" in copy "+duplicationId);
}


