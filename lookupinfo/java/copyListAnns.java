// Copy the LookupList and the referenced Lookup annotations 
// from the inputAS to the outputAS.
// This step is done so we can evaluate the max recall later,
// based on ALL candidates we initially have, not just the ones
// that remain at a later step.

import gate.*;
import gate.trendminer.lodie.utils.LodieUtils;

@Override
public void execute() {
  LodieUtils.copyListAnns(inputAS, outputAS, "LookupList");
}
