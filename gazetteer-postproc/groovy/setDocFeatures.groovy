// Set document features from config properties


void beforeCorpus(c) {
  // we expect the system property "lodie.home" to be set to the root of the 
  // application directory
  if(!binding.hasVariable("config")) {    
    config = new Properties()
    String home = System.getProperty("lodie.home")
    if(home == null) {
      throw new GateRuntimeException("System property lodie.home is not set!")
    }
    // now see if there is a config file called "lodie.config.properties" in the lodie.home directory 
    File configFile = new File(new File(home),"lodie.config.properties")
    if(configFile.exists()) {
      // read the config file
      config.load(new FileInputStream(configFile))
    }
    // try to get the property which represents the run-removeLookups feature value
    String parm = config.getProperty("lodie.gazetteer-postproc.run-removeLookups")
    if(parm != null) {
      // doc.getFeatures.put("run-removeLookups",parm)
      featureRunRemoveLookups = parm
    } else {
      println("No config setting lodie.gazetteer-postproc.run-removeLookups not present, using true")
      // doc.getFeatures.put("run-removeLookups","true") // DEFAULT
      featureRunRemoveLookups = "true"
    }
  } // binding config does not exist
} // beforeCorpus

doc.getFeatures().put("run-removeLookups",featureRunRemoveLookups)

