This contains scripts, pipelines, config files and corpora for running a few
simple unit-tests. 
The idea is that the unit tests check if what some of the standard pipelines 
generate is the same as it was before we changed something.

This is done the following way:
  = there are two corpora, one for tweets and one for non-tweets, sampled from some of our 
    training corpora
  = At time X we run each pipeline on the relevant corpora and save the result
    Then, the annotatations in Shef get moved to Ref, and everything else (except the Key set) gets cleaned
  = For the actual unit test, each pipeline is again run on the relevant prepared corpora which have
    the Ref set.
    Then an evaluation script is run which will compare the Ref and Shef sets. If there are any differences,
    they will be logged and an error code is set.  
