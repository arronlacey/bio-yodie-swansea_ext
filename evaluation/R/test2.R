## Initial experimental code to test the new GateEvalR R package

## easiest way to use a package without actually installing it.
## Advantage: we always use the correct version of the package.

## library("devtools")
## whereIsGateEvalR = ???
## devtools::load_all(whereIsGateEvalR)
loadAll <- function(prefix,directory=".") {
  suf_spots = c(
    "-spots-gazetteer-label.tsv",
    "-spots-spotting-final.tsv",
    "-spots-lookupinfo-final.tsv",
    "-spots-filter-prescoring-final.tsv",
    "-spots-final.tsv"
    )
  suf_tagging = c(
    "-tags-finalByScore.tsv"
    )
  suf_lists = c(
    "-lists-lookupinfo-final.tsv",
    "-lists-filter-prescoring-filterByType.tsv",
    "-lists-filter-prescoring-afterMisc.tsv",
    "-lists-filter-prescoring-afterOverlapping.tsv",
    "-lists-filter-prescoring-final.tsv",
    "-lists-scoring-structSimCombined.tsv",
    "-lists-scoring-contSimRDFDirect.tsv",
    "-lists-scoring-contSimTFICFSemantic.tsv",
    "-lists-scoring-stringSimBestLabel.tsv",
    "-lists-finalByScore.tsv"
    )

  ########################################################
  suf_lists_matches = sapply(suf_lists,function(x) { sub("\\.tsv","-matches.tsv",x) },USE.NAMES=FALSE)

  if(substr(directory,nchar(directory),nchar(directory)) != "/") {
    directory = paste(directory,"/",sep="")
  }


  files_spots = sapply(suf_spots,function(x) { paste(directory,prefix,x,sep="") },USE.NAMES=FALSE)
  files_tagging = sapply(suf_tagging,function(x) { paste(directory,prefix,x,sep="") },USE.NAMES=FALSE)
  files_lists = sapply(suf_lists,function(x) { paste(directory,prefix,x,sep="") },USE.NAMES=FALSE)
  files_lists_matches = sapply(suf_lists_matches,function(x) { paste(directory,prefix,x,sep="") },USE.NAMES=FALSE)

  spotEvalsNormal = GateEvalList(files_spots,evaluationType="normal")

  listEvalsBest = GateEvalList(files_lists,evaluationType="list-best")
  listEvalsRank = GateEvalList(files_lists,evaluationType="list-rank")
  listEvalsScore = GateEvalList(files_lists,evaluationType="list-score")
  listEvalsDisamb = GateEvalList(files_lists,evaluationType="list-disamb")

  listEvalsMatches = GateEvalList(files_lists_matches,evaluationType="list-matches")

  taggingEvalsNormal = GateEvalList(files_tagging,evaluationType="normal")

  ret = list(
    spotEvalsNormal = spotEvalsNormal,
    listEvalsBest = listEvalsBest,
    listEvalsRank = listEvalsRank,
    listEvalsScore = listEvalsScore,
    listEvalsMatches = listEvalsMatches,
    taggingEvalsNormal = taggingEvalsNormal
    )
  class(ret) = c("YodieExperiment","list")

} ## processAll
