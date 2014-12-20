lookupinfo-preproc:

= based on the annotations from preprocessing and on the 
  various gazetteer lookup annotations, build a final 
  set of annotations for which we will try to lookup 
  information. 
  This will create an annotation set which contains Lookup
  annotations which should not overlap unnecessarily and never
  be co-extensive. Each Lookup identifies a location in the 
  text for which to lookup information and the annotation is expected
  to have the feature "string" which contains the key normalized
  in a way suitable for lookup (e.g. lower-case).

