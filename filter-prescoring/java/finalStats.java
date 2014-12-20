

@Override
public void execute() {
  System.out.println(doc.getName()+": filterByAnniePerson filtered:"+
    doc.getFeatures().get("filter-prescoring.nrRemovedByAnniePerson"));
  System.out.println(doc.getName()+": Final remaining total number of Lookups: "+inputAS.get("Lookup").size());
}