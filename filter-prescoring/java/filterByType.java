import gate.trendminer.lodie.utils.LodieUtils;
import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collections;

// TODO: we need to do this more intelligently: some types we can really filter
// here, but other types may be useful for filtering later: if some other 
// entities can be linked to something that we do NOT want, it is an indication
// that we do not want that other stuff either, even if it does not have a type!
// EG: we do not want to annotate sports leagues but finding one can help to
// disambiguate a sports person or team! Same for Stadium ...
// or a television episode / movie to disambiguate a television host or actor



// Filter the candidate by removing candidates which do not have a DBPedia
// type we do not want. By default, this leaves candidates which do not have
// any class information untouched. 
// If any of the dbpSpecific/Interesting or airpSpecificInteresting classes
// matches one of the classes which is in the list of "definitely not 
// interesting", we will remove the candidate.
// TODO: this should eventually either be based on ontology inference or 
// on a generated full list of classes pre-calculated with ontology inference.

// CONFIG: the property lodie.filter-prescoring.filterByType.filterNoType
// controls if empty type only candidates should get filtered. Default is false,
// if the property is set to "true" they will get filtered.

HashSet<String> typeUris = new HashSet<String>();
ArrayList<String> features = new ArrayList<String>();

boolean filterNoType = false;

HashSet<String> seenTypes = new HashSet<String>();

@Override
public void init() {
  //typeUris.add("dbpo:AcadenicJournal"); // ??? 
  typeUris.add("dbpo:Activity"); // ??? 
  typeUris.add("dbpo:Aircraft"); // ??? 
  //typeUris.add("dbpo:Agent"); // is top but contains Organisation and Person
  typeUris.add("dbpo:Amphibian");
  typeUris.add("dbpo:Animal");
  typeUris.add("dbpo:AnimangaCharacter");
  typeUris.add("dbpo:Anime");
  typeUris.add("dbpo:Arachnid");
  //typeUris.add("dbpo:ArchitecturalStructure");
  typeUris.add("dbpo:ArtistDiscography");
  //typeUris.add("dbpo:Artwork");
  typeUris.add("dbpo:Asteroid");
  typeUris.add("dbpo:Award");
  typeUris.add("dbpo:Bacteria");
  typeUris.add("dbpo:Biomolecule");
  typeUris.add("dbpo:Bird"); // Species
  typeUris.add("dbpo:Bone");
  typeUris.add("dbpo:Brain");
  //typeUris.add("dbpo:Building");
  typeUris.add("dbpo:Cave");
  typeUris.add("dbpo:CelestialBody"); // is top
  typeUris.add("dbpo:ChemicalCompound");
  typeUris.add("dbpo:Colour");
  //typeUris.add("dbpo:Comics");
  //typeUris.add("dbpo:ComicsCharacter");
  typeUris.add("dbpo:Conifer");
  typeUris.add("dbpo:Convention");
  typeUris.add("dbpo:Crustacean");
  typeUris.add("dbpo:CultivatedVariety");
  typeUris.add("dbpo:Currency");
  //typeUris.add("dbpo:Disease");
  typeUris.add("dbpo:Drug"); // ????????
  //typeUris.add("dbpo:Election");
  typeUris.add("dbpo:Embryology");
  typeUris.add("dbpo:Enzyme");
  typeUris.add("dbpo:EthnicGroup");
  typeUris.add("dbpo:Eukaryote"); // Species
  //typeUris.add("dbpo:Event");
  typeUris.add("dbpo:Fern");
  //typeUris.add("dbpo:FictionalCharacter");
  typeUris.add("dbpo:FilmFestival");
  typeUris.add("dbpo:Fish");
  typeUris.add("dbpo:FloweringPlant");
  typeUris.add("dbpo:Food");
  //typeUris.add("dbpo:FootballMatch");
  typeUris.add("dbpo:Fungus");
  typeUris.add("dbpo:Galaxy");
  //typeUris.add("dbpo:Game");
  typeUris.add("dbpo:GivenName");
  //typeUris.add("dbpo:GrandPrix");
  //typeUris.add("dbpo:Grape"); //Species
  //typeUris.add("dbpo:HistoricBuilding");
  //typeUris.add("dbpo:Holiday"); // is top
  //typeUris.add("dbpo:HollywoodCartoon");
  //typeUris.add("dbpo:Hospital");
  //typeUris.add("dbpo:Hotel");
  //typeUris.add("dbpo:Infrastructure");
  typeUris.add("dbpo:Insect");
  typeUris.add("dbpo:Language"); // is top
  typeUris.add("dbpo:Legislature"); // not sure, is subclass of organisation
  //typeUris.add("dbpo:Library");
  //typeUris.add("dbpo:Lighthouse");
  typeUris.add("dbpo:Locomotive");
  typeUris.add("dbpo:LunarCrater");
  typeUris.add("dbpo:Mammal"); // species
  //typeUris.add("dbpo:MeanOfTransportation");
  //typeUris.add("dbpo:Medician");
  //typeUris.add("dbpo:MilitaryUnit"); // not sure, is subclass of organisation
  typeUris.add("dbpo:MilitaryConflict");
  typeUris.add("dbpo:MilitaryStructure");
  typeUris.add("dbpo:Mineral");
  typeUris.add("dbpo:MixedMartialArtsEvent");
  typeUris.add("dbpo:Mollusca");
  //typeUris.add("dbpo:Monument");
  typeUris.add("dbpo:MusicGenre"); // is in genre is in topical concept
  typeUris.add("dbpo:MusicFestival");
  //typeUris.add("dbpo:Musical");
  //typeUris.add("dbpo:MusicalWork");
  typeUris.add("dbpo:Nerve");
  typeUris.add("dbpo:OlympicResult");
  //typeUris.add("dbpo:Painting");
  //typeUris.add("dbpo:PeriodicalLiterature");
  typeUris.add("dbpo:PersonFunction");
  typeUris.add("dbpo:Planet"); // celestialbody
  typeUris.add("dbpo:Plant"); // Species
  //typeUris.add("dbpo:Play");
  //typeUris.add("dbpo:PowerStation");
  //typeUris.add("dbpo:Prison");
  typeUris.add("dbpo:ProgrammingLanguage");
  typeUris.add("dbpo:Protein");
  typeUris.add("dbpo:PublicTransitSystem");
  typeUris.add("dbpo:Race");
  typeUris.add("dbpo:RaceHorse");
  //typeUris.add("dbpo:RadioProgram");
  //typeUris.add("dbpo:RailwayStation");
  typeUris.add("dbpo:RailwayTunnel");
  //typeUris.add("dbpo:RecordLabel");
  typeUris.add("dbpo:Religious");
  //typeUris.add("dbpo:ReligiousBuilding");
  typeUris.add("dbpo:Reptile");
  //typeUris.add("dbpo:Restaurant");
  //typeUris.add("dbpo:RoadJunction");
  //typeUris.add("dbpo:RoadTunnel");
  typeUris.add("dbpo:Road"); // not sure is part of route of transportation is part of infrastructure is part of place
  typeUris.add("dbpo:Rocket");
  //typeUris.add("dbpo:Saint");
  //typeUris.add("dbpo:School");
  //typeUris.add("dbpo:Scientist");
  typeUris.add("dbpo:Ship");
  //typeUris.add("dbpo:ShoppingMall");
  //typeUris.add("dbpo:Single");
  typeUris.add("dbpo:SiteOfSpecialScientificInterest");
  // typeUris.add("dbpo:Software");
  //typeUris.add("dbpo:Song");
  typeUris.add("dbpo:SpaceMission");
  typeUris.add("dbpo:SpaceShuttle");
  typeUris.add("dbpo:SpaceStation");
  typeUris.add("dbpo:Species");
  //typeUris.add("dbpo:Sport");
  //typeUris.add("dbpo:SportsEvent");
  //typeUris.add("dbpo:SportsLeague");
  //typeUris.add("dbpo:Stadium");
  typeUris.add("dbpo:Star");
  //typeUris.add("dbpo:Station");
  typeUris.add("dbpo:SupremeCourtOfTheUnitedStatesCase");
  typeUris.add("dbpo:Tax");
  //typeUris.add("dbpo:TelevisionEpisode");
  //typeUris.add("dbpo:TelevisionSeason");
  //typeUris.add("dbpo:TelevisionShow");
  //typeUris.add("dbpo:Theatre");
  typeUris.add("dbpo:Train");
  //typeUris.add("dbpo:Tunnel");
  typeUris.add("dbpo:Vein");
  //typeUris.add("dbpo:VideogamesLeague");
  typeUris.add("dbpo:Volcano");
  typeUris.add("dbpo:Weapon");
  // typeUris.add("dbpo:Website"); // not sure subclass of software which is a work
  //typeUris.add("dbpo:Work");
  //typeUris.add("dbpo:WrittenWork");
  typeUris.add("dbpo:Year");
  
  typeUris.add("dbpo:WorldHeritageSite");
  features.add("airpSpecificClass");
  features.add("airpInterestingClass");
  features.add("dbpSpecificClass");
  features.add("dbpInterestingClass");
    
  String confFilterNoType = System.getProperty("lodie.filter-prescoring.filterByType.filterNoType");
  if(confFilterNoType != null && confFilterNoType.toLowerCase().equals("true")) {
    filterNoType = true;
  }
  
}

/*
@Override
public void cleanup() {
  ArrayList<String> tmp = new ArrayList<String>();
  tmp.addAll(seenTypes);
  Collections.sort(tmp);
  for(String type : tmp) {
    System.out.println("SEEN TYPE: "+type);
  }
}
*/


@Override
public void execute() {
  List<FeatureMap> cands;
  int removed = 0;
  for(Annotation ll : inputAS.get("LookupList")) {
    cands = LodieUtils.getCandidateList(inputAS,ll);
    // remove all the candidates where the uriFreqInWp feature does not exist or is < minUriFreqInWp
    Iterator<FeatureMap> it = cands.iterator();
    while(it.hasNext()) {
      FeatureMap fm = it.next();
      boolean hadOne = false;
      // TODO: for now we go through all features and all types, even after
      // we already removed the candidate, so we can be sure to collect all
      // the types that are there in the seenTypes set.
      boolean isRemoved = false;
      for(String feature : features) {
        String typelist = (String)fm.get(feature);
        if(typelist != null && !typelist.isEmpty()) {
          String[] types = typelist.split("[|]");
          for (String type : types) {
            seenTypes.add(type);
            hadOne = true;
            if(!isRemoved && typeUris.contains(type)) {
              it.remove();
              isRemoved = true;
              // to abort early, but possibly miss some types in the seen set:
              // break;
            } // if typeUris contains type
          } // for type .. types
          // to abort early:
          // if isRemoved break
        } // if typelist not empty 
      } // for feature...
      if(!hadOne && filterNoType) {
        it.remove();
      }
    }
    // finally delete all the annotations which do not match the remaining ones, but 
    // if we have an empty candidate list, just delete everything including the list annotation
    if(cands.size() == 0) {
      removed += LodieUtils.removeListAnns(inputAS,ll);
    } else {
      removed += LodieUtils.keepCandidateAnnsByCollection(inputAS,ll,cands,"inst");
    }
  }
  System.out.println(doc.getName()+": filterByType filtered: "+removed);
}
