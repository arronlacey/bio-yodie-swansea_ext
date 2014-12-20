#!/usr/bin/perl -w

use strict;
use Heap::Priority;

my $n = 10;
if(defined($ARGV[0])) {
  $n = $ARGV[0];
}

my $h = new Heap::Priority;
while(<STDIN>) {
  next if(!/^StructuralSimilarityPRv2/);
  chomp;
  my (undef,$what,$node1,$node2,$time) = split(/\t/);
  $h->add($_,$time); 
}

$h->highest_first;
print "$n items with highest priority:\n";
for(my $i=0;$i<$n;$i++) {
  my $val = $h->pop;
  if(!defined($val)) {
    print("No more entries in the queue");
    last;
  }
  print $i,": ",$val,"\n";
}

