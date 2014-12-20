This contains several modules for filtering candidates
based on some heuristics ...

1) Filter location "pairs"
==========================

At the moment we try to keep this simple. There are dozens of 
ways how things can overlap, can have or not have matching 
candidates etc.

So for now we do this:
= find separate pairs of LookupList before and after a comma
  For this we create a single temporary annotation over the whole
  span of type LookupListPairTwo which contains the ids of the  
  two LookupLists 
= find a single LookupList that goes over a comma
  For this we create single temporary annotation over the same 
  span of type LookupListPairOne which contains the id of the 
  LookupList

After this we distinguish the following cases:
= There is  just a Two annotation and no One annotation overlaps:
  we then filter the candidates to only those which have a airp or dbp
  interesting class coresponding to Location
  then, we only keep pairs of candidates which are related
  But if we find no related pairs, the original annotations and 
  candidates are kept unchanged.
= There is a Two annotation and it overlaps with a One annotation
  and the overlap is exact.
  Then we limit the candidate list of the first in the pair to the
  candidates which are shared with the One annotations.
  If there are candidates in the second of the pair which 
  are related to the final first list, we restrict to both lists 
  to just those.
  The List annotation corresponding to One is always removed, 
  however the candidate annotations of the pair are only 
  reduced if at least one pair that is related is found, otherwise
  the original candidate lists are kept.

In all other cases we leave everything as it was and warn

Finally we remove the temporary annotations.

2) Filter abbreviations
=======================

a) We find sequences of 2 to 6 word tokens with an initial 
upper case letter, followed by an all upper case word 
enclosed in parentheses which is also a LookupList.
For now we require that the following is true:
= the first letter of the first word must match the first
  letter in the abbreviation
= of the remaining n words (n=1..5), at least 
  all have to have matching first letters too,
  but there may be litters in the abbreviation which
  do not correspond to initials in the words.
  Later we should allow more flexible matching!! 
At the moment, we do not allow optional stopwords 
like "of" be mixed into the word tokens.
Also, at the moment with do not allow spurious words
with non-uppercase letters, other punctuation or hypens
or numbers in the definining word sequence

b) In addition we also find lookup list annotations 
followed by an all-uppercase word in parentheses
and check if the word tokens inside the lookup list
annotation have initial letters which are a subsequence
of the upper case word letters. 

For a) if not b) we then reduce the candidates for the 
abbreviation annotation to just those which have at
least one original label that matches the words.

For b) we reduce both candidate lists to the common URIs,
unless there are no common URIs. 
TODO: what to do if there are no common URIs (for now: warn)

3) Filter by ANNIE Person
=========================

If we find one or more LookupList annotations overlapping
with an ANNIE annotation:

For now extremely simple: remove all LookupList annotations
which are shorter and fully contained in the Person.

4) JobTitle: 
============

NOT YET IMPLEMENTED
= remove all LookupList contained or coextensive with 
  JobTitle. If a Job title is followed by a LookupList,
  and if that list contains person candidates, reduce,
  if there is no person, warn.

5) Tokens
========= 

NOT YET, ALSO: this should not be here but in filter-prelookup?
TODO: this may not be necessary, if we can postprocess the Tokens or prevent
annotations that involve some Tokens much earlier already.

We should avoid to annotate with original gazetteer lookups:
= number only things: already done, we do not include numbers in the gazetteer
= Words which are immediately followed or preceded by a number without
  any whitespace: this is usually a code 
= words which are joined to another word or number before or after  
  by one of the following things in between: "_", "@", "."(?)

6) Known stuff
==============

NOT YET
If we can detect something with good confidence before we do the 
gazetteer lookup, exclude it.
This could be:

ANNIE: Identifier
ANNIE/OWN?: Date/Times that include date names or timezone names (UTC) where
  the time zone could be an abbreviation or the full name.
  TODO: we may want to link to the timezone instance so we would need 
  some list or some way to do this.

7) Filter Lookups where the mention starts with "the". 
======================================================

There are two cases here: the frequent one, like 
"the white house" or "the state department" where we want to 
remove the "the" and the less frequent one like "The Wall" (album)
where we must keep it. 

If we do not know already what the correct class of the mention
is (e.g. album versus location) then it is hard to decide if the 
"the" should get removed from syntax only: a hint may be the 
case of the "The" but even that is ambiguous if this appears at
the start of a sentence. On the other hand, enclosing quotes or
paretheses or commas may be a hint: 
  the album "The Wall" was ...
  when the publiched their first album, The Wall, which ...
Another hint may be the number of common URIs for the two 
candidate lists: if the number os very similar, "the" may
be rendundand, but if not, it may be an important part of
the label (but then, the mention without the the might still be
the one which contains the correct inst in that situation).

Another situation is where the overlap is partial as in
  the State Departoment
Where we get
  [the State] [Department]
      [State Department]
This is a special case of where we have to decide between
overlapping annotations 
  [some annotation]  
       [annotation two]
There are several heuristics how this could be resolved,
but for the "the" problem, we may just want to at least
remove [some annotation] if "some" is not a NNP and
"annotation"  is an NNP?

8) Persons by job or association
================================

NOT YET
(just vague notes for now...)
In many cases we know from DBPedia the profession of a person, e.g.
football player. So if we find something like 
  [Organization] spokesperson [Person]
  the lawyer, [Person] 
we have some indication about if that can be matched or not.
This is especially important to recognize persons mentioned
only by last name:
  "Bush" alone: maybe not, not always
  "president bush": definitely, even if case is wrong!
  "Premier McGuinty" etc.
    "Dalton McGuinty" is in WP, but there is no label for "McGuinty" alone
    so we should generate a last-names gazetteer but restrict matching 
    of last names along based on context.
   Here, the mappingbased properties contains 
      "ontology/office" -> "24th Premier of Ontario" 
      airpclasses contains: "Politician", "OfficeHolder", "President" ... but not "Premier"
       


80) Filter by rank
==================

Implement to filter by uri frequency in wp count: remove all candidates
where the count is null. Remove all candidates where the count is less 
than the minimum count. Remove all but the N highest ranks of candidates.

99) Filter overlaps
===================

TBD: this should be configurable by some document feature or similar.

The idea is to filter overlapping LookupList  annotations so that only
longest/non-overlapping are retained or so that at least not all 
overlaps are retained (depending on the config).

