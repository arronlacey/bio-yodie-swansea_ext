2014-12-20 JP: Initial git version copied over from 
  svn+ssh://gateservice2.dcs.shef.ac.uk/data/svn/gate-extras-svnrep/tags/disambiguation/v-trendminer-final/
but without the following directories or files:
  all-legacy.xgapp
  all.xgapp
  bin/
  evaluation/
  legacy/
  misc-apps/
  notes/
  plugins/AlchemyAPI/
  plugins/Alignment/
  plugins/AppDoc/
  plugins/BdbLookup/
  plugins/CorpusQualityAssurance2/
  plugins/dbpedia-spotlight/
  plugins/Disambiguation/ri-spaces/
  plugins/Learning/
  plugins/LuceneGaz/
  plugins/ML_Mallet/  
  plugins/Ontology/
  plugins/Tagger_Aida/
  plugins/Tagger_DBpediaSpotlight/
  plugins/Tagger_Lodie1/
  plugins/Tagger_Lupedia/
  plugins/Tagger_TagMe/
  plugins/Tagger_TextRazor/
  plugins/TwitterSentenceIdentifier/
  plugins/TwitterTruecaser/
  plugins/VirtualCorpusOld/
  preprocess-hi/
  tagger-app/
  wp-all
  wp-classinfo
  wp-linksAndMentions
  wp-prepare
Some of these may be added later or to a different yodie-* repository.
= the tagger-app directory has been moved to yodie-experiments/pipelines
= the tagger plugins have been moved to yodie-experiements/pipelines/plugins
= the wp-* directories have been moved to yodie-preparation/pipelines

Unfortunately we had to copy over the following plugin:
  plugins/LuceneGaz/  required by the Disambiguation plugin
