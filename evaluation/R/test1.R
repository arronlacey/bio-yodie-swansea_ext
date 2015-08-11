
TestAll <- function() {
  ProcessAll("aida-ee-fix01","/data/johann/gate-home/gate-plugins/Evaluation/")
}

ProcessAll <- function(prefix,directory) {
  print("SPOTTING STAGES")
  file=paste(directory,prefix,"-spots-gazetteer-label.tsv",sep="")
  print(paste("FOR spots gazetteer-label"))
  Load4Tagging(file)
  file=paste(directory,prefix,"-spots-spotting-final.tsv",sep="")
  print(paste("FOR spots spotting-final"))
  Load4Tagging(file)
  file=paste(directory,prefix,"-spots-lookupinfo-final.tsv",sep="")
  print(paste("FOR spots lookupinfo-final"))
  Load4Tagging(file)
  file=paste(directory,prefix,"-spots-filter-prescoring-final.tsv",sep="")
  print(paste("FOR spots filter-prescoring-final"))
  Load4Tagging(file)
  file=paste(directory,prefix,"-spots-final.tsv",sep="")
  print(paste("FOR spots final"))
  Load4Tagging(file)
}

Load4Tagging <- function(filename) {
  ## 1) load the data
  df = read.delim(filename,encoding="UTF8", row.names=NULL)
  ## get the overall P/R/F for all documents, and threshold NaN
  df.tmp = df
  df.tmp = df.tmp[df.tmp$docName=="[doc:all:micro]",]
  df.tmp = df.tmp[df.tmp$evaluationType=="normal"]
  df.tmp = df.tmp[df.tmp$Threshold=="NaN"]
  df.tmp = df.tmp[,c("setName","annotationType","precisionStrict","recallStrict","F1Strict","precisionLenient","recallLenient","F1Lenient")]
  print(df.tmp)
}

Load4TaggingList <- function(filename) {
  df1 = read.delim(filename,encoding="UTF8", row.names=NULL)
  df1.tmp = df1
  df1.tmp = df1.tmp[df1.tmp$docName=="[doc:all:micro]",]
  df1.tmp = df1.tmp[df1.tmp$evaluationType=="list-best",]
  df1.tmp = df1.tmp[,c("setName","annotationType","precisionStrict","recallStrict","F1Strict","precisionLenient","recallLenient","F1Lenient")]
  list.best = df1.tmp

  df1.tmp = df1
  df1.tmp = df1.tmp[df1.tmp$docName=="[doc:all:micro]",]
  df1.tmp = df1.tmp[df1.tmp$evaluationType=="list-rank",]
  df1.tmp = df1.tmp[df1.tmp$Threshold==2147483647,]
  df1.tmp = df1.tmp[,c("setName","annotationType","precisionStrict","recallStrict","F1Strict","precisionLenient","recallLenient","F1Lenient")]
  list.rank = df1.tmp

  filename2 = gsub("\\.tsv","-matches.tsv",filename)
  df2 = read.delim(filename2,encoding="UTF8", row.names=NULL)
  df2.tmp = df2
  df2.tmp = df2.tmp[df2.tmp$scoreAtStrictMatch != -Inf,]  ## remove the entries without a match
  list.matches = df2.tmp
  ret=list(
    filename=filename,
    list.best=list.best,
    list.rank=list.rank,
    list.matches=list.matches)
  class(ret) = "evalTaggingList"
  return(ret)
}

print.evalTaggingList <- function(x, useS4 = FALSE) {
  cat("EvalTaggingList for ",x$filename,"\n")
  invisible(x)
}